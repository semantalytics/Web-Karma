package edu.isi.karma.controller.command.worksheet;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.isi.karma.controller.command.CommandException;
import edu.isi.karma.controller.command.CommandType;
import edu.isi.karma.controller.command.WorksheetCommand;
import edu.isi.karma.controller.update.ErrorUpdate;
import edu.isi.karma.controller.update.UpdateContainer;
import edu.isi.karma.controller.update.WorksheetDeleteUpdate;
import edu.isi.karma.controller.update.WorksheetListUpdate;
import edu.isi.karma.controller.update.WorksheetUpdateFactory;
import edu.isi.karma.er.helper.CloneTableUtils;
import edu.isi.karma.modeling.alignment.Alignment;
import edu.isi.karma.modeling.alignment.AlignmentManager;
import edu.isi.karma.modeling.semantictypes.SemanticTypeUtil;
import edu.isi.karma.rep.HNode;
import edu.isi.karma.rep.HTable;
import edu.isi.karma.rep.RepFactory;
import edu.isi.karma.rep.Row;
import edu.isi.karma.rep.Table;
import edu.isi.karma.rep.Worksheet;
import edu.isi.karma.rep.Workspace;
import edu.isi.karma.rep.Node;
import edu.isi.karma.rep.metadata.WorksheetProperties.Property;
import edu.isi.karma.util.Util;

public class UnfoldCommand extends WorksheetCommand {

	//add column to this table
	//the id of the new column that was created
	//needed for undo
	private String newWorksheetId;
	private String newHNodeId;
	private String keyHNodeId;
	private String valueHNodeId;
	private String keyName;
	private String valueName;
	private static Logger logger = LoggerFactory
			.getLogger(FoldCommand.class);

	public enum JsonKeys {
		updateType, hNodeId, worksheetId
	}

	protected UnfoldCommand(String id, String worksheetId, String keyHNodeid, String valueHNodeid) {
		super(id, worksheetId);
		newWorksheetId = null;
		newHNodeId = null;
		this.keyHNodeId = keyHNodeid;
		this.valueHNodeId = valueHNodeid;
		addTag(CommandTag.Transformation);
	}

	@Override
	public String getCommandName() {
		// TODO Auto-generated method stub
		return this.getClass().getSimpleName();
	}

	@Override
	public String getTitle() {
		// TODO Auto-generated method stub
		return "Unfold";
	}

	@Override
	public String getDescription() {
		// TODO Auto-generated method stub
		return keyName + " with " + valueName;
	}

	@Override
	public CommandType getCommandType() {
		// TODO Auto-generated method stub
		return CommandType.undoable;
	}

	@Override
	public UpdateContainer doIt(Workspace workspace) throws CommandException {
		RepFactory factory = workspace.getFactory();
		Worksheet oldws = workspace.getWorksheet(
				worksheetId);
		Worksheet newws = null;
		HTable ht = CloneTableUtils.getHTable(oldws.getHeaders(), keyHNodeId);
		if (ht == oldws.getHeaders()) {
			newws = unfoldTopLevel(oldws, keyHNodeId, valueHNodeId, workspace, factory);
			this.newWorksheetId = newws.getId();
		}
		else {
			try {
			unfoldNestedLevel(oldws, ht, keyHNodeId, valueHNodeId, factory);
			}catch(Exception e) {
				e.printStackTrace();
			}
		}
		try{
			UpdateContainer c =  new UpdateContainer();
			c.add(new WorksheetListUpdate());
			if (newws == null)
				c.append(WorksheetUpdateFactory.createRegenerateWorksheetUpdates(oldws.getId()));
			if (newws != null) {
				c.append(WorksheetUpdateFactory.createRegenerateWorksheetUpdates(newws.getId()));
				Alignment alignment = AlignmentManager.Instance().getAlignmentOrCreateIt(workspace.getId(), newws.getId(), workspace.getOntologyManager());
				SemanticTypeUtil.computeSemanticTypesSuggestion(workspace.getWorksheet(newws.getId()), workspace
						.getCrfModelHandler(), workspace.getOntologyManager());
				c.append(WorksheetUpdateFactory.createSemanticTypesAndSVGAlignmentUpdates(newws.getId(), workspace, alignment));
			}
			c.append(computeAlignmentAndSemanticTypesAndCreateUpdates(workspace));
			return c;
		} catch (Exception e) {
			logger.error("Error in UnfoldCommand" + e.toString());
			Util.logException(logger, e);
			return new UpdateContainer(new ErrorUpdate(e.getMessage()));
		}
	}

	@Override
	public UpdateContainer undoIt(Workspace workspace) {
		UpdateContainer c = new UpdateContainer();
		if (this.newWorksheetId != null) {
			workspace.removeWorksheet(newWorksheetId);
			workspace.getFactory().removeWorksheet(newWorksheetId);
			c.add(new WorksheetListUpdate());
			c.add(new WorksheetDeleteUpdate(newWorksheetId));
		}
		if (this.newHNodeId != null) {
			Worksheet worksheet = workspace.getWorksheet(worksheetId);
			HNode ndid = workspace.getFactory().getHNode(newHNodeId);
			HTable currentTable = workspace.getFactory().getHTable(ndid.getHTableId());
			ndid.removeNestedTable();
			//remove the new column
			currentTable.removeHNode(newHNodeId, worksheet);
			c.append(WorksheetUpdateFactory.createRegenerateWorksheetUpdates(worksheetId));
			c.append(computeAlignmentAndSemanticTypesAndCreateUpdates(workspace));
		}
		return c;
	}
	
	public void setValueName(String valueName) {
		this.valueName = valueName;
	}
	
	public void setKeyName(String keyName) {
		this.keyName = keyName;
	}
	
	private void unfoldNestedLevel(Worksheet oldws, HTable ht, String keyHNodeid, String valueHNodeid, RepFactory factory) {
		ArrayList<HNode> topHNodes = new ArrayList<HNode>(ht.getHNodes());
		HTable parentHT = ht.getParentHNode().getHTable(factory);
		List<Table> parentTables = new ArrayList<Table>();
		CloneTableUtils.getDatatable(oldws.getDataTable(), parentHT,parentTables);
		ArrayList<Row> parentRows = new ArrayList<Row>();
		for (Table tmp : parentTables) {
			for (Row row : tmp.getRows(0, tmp.getNumRows())) {
				parentRows.add(row);
			}
		}
		//ArrayList<Row> parentRows = parentTable.getRows(0, parentTable.getNumRows());
		HNode newNode = parentHT.addHNode("Unfold: " + ht.getHNode(keyHNodeid).getColumnName(), oldws, factory);
		this.newHNodeId = newNode.getId();
		HTable newHT = newNode.addNestedTable("Unfold: " + ht.getHNode(keyHNodeid).getColumnName(), oldws, factory);
		HNode key = ht.getHNode(keyHNodeid);
		HNode value = ht.getHNode(valueHNodeid);
		List<HNode> hnodes = new ArrayList<HNode>();
		List<String> hnodeIds = new ArrayList<String>();
		for (HNode h : topHNodes) {
			if (h.getId().compareTo(value.getId()) != 0 && h.getId().compareTo(key.getId()) != 0) {
				hnodes.add(h);
				hnodeIds.add(h.getId());
			}
		}
		CloneTableUtils.cloneHTable(ht, newHT, oldws, factory, hnodes);
		List<Row> resultRows = new ArrayList<Row>();
		for (Row parentRow: parentRows) {
			Table t = null;
			for (Node node : parentRow.getNodes()) {
				if (node.hasNestedTable() && node.getNestedTable().getHTableId().compareTo(ht.getId()) == 0) {
					t = node.getNestedTable();
					break;
				}	
			}
			Map<String, String> keyMapping = new HashMap<String, String>();
			Map<String, String> HNodeidMapping = new HashMap<String, String>();
			ArrayList<Row> rows = t.getRows(0, t.getNumRows());
			for (Row row : rows) {
				Node n = row.getNode(key.getId());
				keyMapping.put(HashValueManager.getHashValue(oldws, n.getId()), n.getValue().asString());
			}
			for (String mapkey : keyMapping.keySet()) {
				HNode hn = newHT.getHNodeFromColumnName(keyMapping.get(mapkey).toLowerCase().replace('/', '_'));
				if (hn == null) {
					HNode n = newHT.addHNode(keyMapping.get(mapkey).toLowerCase().replace('/', '_'), oldws, factory);
					HTable htt = n.addNestedTable("values", oldws, factory);
					htt.addHNode("Values", oldws, factory);
					HNodeidMapping.put(keyMapping.get(mapkey), n.getId());
				}
				else
					HNodeidMapping.put(keyMapping.get(mapkey), hn.getId());
			}
			Map<String, ArrayList<String>> hash = new TreeMap<String, ArrayList<String>>();
			for (Row row : rows) {
				String hashValue = HashValueManager.getHashValue(row, hnodeIds);
				ArrayList<String> ids = hash.get(hashValue);
				if (ids == null)
					ids = new ArrayList<String>();
				ids.add(row.getId());
				hash.put(hashValue, ids);
				//System.out.println("Hash: " + HashValueManager.getHashValue(row, hnodeIDs));
			}

			for (String hashKey : hash.keySet()) {
				ArrayList<String> r = hash.get(hashKey);
				Node node = parentRow.getNeighbor(newNode.getId());
				Row lastRow = CloneTableUtils.cloneDataTable(CloneTableUtils.getRow(t.getRows(0, t.getNumRows()), r.get(0)), node.getNestedTable(), parentHT, newHT, hnodes, factory);
				for (String rowid : r) {
					Row cur = CloneTableUtils.getRow(rows, rowid);
					String newId = HNodeidMapping.get(cur.getNode(key.getId()).getValue().asString());
					Node newnode = lastRow.getNode(newId);
					Node oldnode = cur.getNode(value.getId());
					Row tmprow = newnode.getNestedTable().addRow(factory);
					tmprow.getNeighborByColumnName("Values", factory).setValue(oldnode.getValue().asString(), oldnode.getStatus(), factory);
					//newnode.setValue(oldnode.getValue().asString(), oldnode.getStatus(), factory);
				}
				resultRows.add(lastRow);
			}
		}
		for (Row tmpRow : resultRows) {
			for (Node node : tmpRow.getNodes()) {
				if (node.hasNestedTable()) {
					Table tmpTable = node.getNestedTable();
					if (tmpTable.getNumRows() == 0) {
						tmpTable.addRow(factory);
					}
				}
			}
		}

	}
	private Worksheet unfoldTopLevel(Worksheet oldws, String keyHNodeid, String valueHNodeid, Workspace workspace, RepFactory factory) {
		Worksheet newws = factory.createWorksheet("Unfold: " + oldws.getTitle(), workspace, oldws.getEncoding());
		newws.getMetadataContainer().getWorksheetProperties().setPropertyValue(Property.sourceType, oldws.getMetadataContainer().getWorksheetProperties().getPropertyValue(Property.sourceType));
		ArrayList<HNode> topHNodes = new ArrayList<HNode>(oldws.getHeaders().getHNodes());
		ArrayList<Row> rows = oldws.getDataTable().getRows(0, oldws.getDataTable().getNumRows());
		HNode key = oldws.getHeaders().getHNode(keyHNodeid);
		HNode value = oldws.getHeaders().getHNode(valueHNodeid);
		List<HNode> hnodes = new ArrayList<HNode>();
		List<String> hnodeIds = new ArrayList<String>();
		for (HNode h : topHNodes) {
			if (h.getId().compareTo(value.getId()) != 0 && h.getId().compareTo(key.getId()) != 0) {
				hnodes.add(h);
				hnodeIds.add(h.getId());
			}
		}
		CloneTableUtils.cloneHTable(oldws.getHeaders(), newws.getHeaders(), newws, factory, hnodes);
		Map<String, String> keyMapping = new HashMap<String, String>();
		Map<String, String> HNodeidMapping = new HashMap<String, String>();
		for (Row row : rows) {
			Node n = row.getNode(key.getId());
			keyMapping.put(HashValueManager.getHashValue(oldws, n.getId()), n.getValue().asString());
		}
		for (String mapkey : keyMapping.keySet()) {
			HNode n = newws.getHeaders().addHNode(keyMapping.get(mapkey), newws, factory);
			HTable ht = n.addNestedTable("values", newws, factory);
			ht.addHNode("Values", newws, factory);
			HNodeidMapping.put(keyMapping.get(mapkey), n.getId());
		}

		Map<String, ArrayList<String>> hash = new TreeMap<String, ArrayList<String>>();
		for (Row row : rows) {
			String hashValue = HashValueManager.getHashValue(row, hnodeIds);
			ArrayList<String> ids = hash.get(hashValue);
			if (ids == null)
				ids = new ArrayList<String>();
			ids.add(row.getId());
			hash.put(hashValue, ids);
		}
		List<Row> resultRows = new ArrayList<Row>();
		for (String hashKey : hash.keySet()) {
			ArrayList<String> r = hash.get(hashKey);
			Row lastRow = CloneTableUtils.cloneDataTable(CloneTableUtils.getRow(rows, r.get(0)), newws.getDataTable(), oldws.getHeaders(), newws.getHeaders(), hnodes, factory);
			for (String rowid : r) {
				Row cur = CloneTableUtils.getRow(rows, rowid);
				String newId = HNodeidMapping.get(cur.getNode(key.getId()).getValue().asString());
				Node newnode = lastRow.getNode(newId);
				Node oldnode = cur.getNode(value.getId());
				Row tmprow = newnode.getNestedTable().addRow(factory);
				tmprow.getNeighborByColumnName("Values", factory).setValue(oldnode.getValue().asString(), oldnode.getStatus(), factory);
				//newnode.setValue(oldnode.getValue().asString(), oldnode.getStatus(), factory);
			}
			resultRows.add(lastRow);
		}
		for (Row tmpRow : resultRows) {
			for (Node node : tmpRow.getNodes()) {
				if (node.hasNestedTable()) {
					Table tmpTable = node.getNestedTable();
					if (tmpTable.getNumRows() == 0) {
						tmpTable.addRow(factory);
					}
				}
			}
		}
		return newws;
	}

}

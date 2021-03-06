package edu.isi.karma.controller.command.worksheet;


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.isi.karma.controller.command.Command;
import edu.isi.karma.controller.command.CommandException;
import edu.isi.karma.controller.command.CommandType;
import edu.isi.karma.controller.update.ErrorUpdate;
import edu.isi.karma.controller.update.UpdateContainer;
import edu.isi.karma.rep.*;

public class MergeClusterValuesCommand extends Command {
	private String hNodeId;
	private String worksheetId;
	private Map<String, String> oldRowValueMap = new HashMap<String, String>();
	
	MultipleValueEditColumnCommand edit;
			
	public MergeClusterValuesCommand(String id, String hNodeId,
			String worksheetId) {
		super(id);
		this.hNodeId = hNodeId;
		this.worksheetId = worksheetId;
	}

	@Override
	public String getCommandName() {

		return this.getClass().getName();
	}

	@Override
	public String getTitle() {
		// TODO Auto-generated method stub
		return "Merge Cluster Values";
	}

	@Override
	public String getDescription() {
		// TODO Auto-generated method stub
		return "";
	}

	@Override
	public CommandType getCommandType() {
		// TODO Auto-generated method stub
		return CommandType.undoable;
	}

	@Override
	public UpdateContainer doIt(Workspace workspace) throws CommandException {
		// TODO Auto-generated method stub
		Worksheet worksheet = workspace.getWorksheet(worksheetId);
		
		HNodePath selectedPath = null;
		List<HNodePath> columnPaths = worksheet.getHeaders().getAllPaths();
		for (HNodePath path : columnPaths) {
			if (path.getLeaf().getId().equals(hNodeId)) {
				selectedPath = path;
			}
		}
		Collection<Node> nodes = new ArrayList<Node>();
		workspace.getFactory().getWorksheet(worksheetId).getDataTable()
				.collectNodes(selectedPath, nodes);

		
		try {
			JSONArray requestJsonArray = new JSONArray();	
			for (Node node : nodes) {
				
				String originalVal = node.getValue().asString();
				originalVal = originalVal == null ? "" : originalVal;
		 		requestJsonArray.put(originalVal);
			}
			
			JSONObject json =  new JSONObject(worksheet.getJsonAnnotation().toString());

			String mainId = json.getString("id");
			String mainWorksheetId = json.getString("worksheetId");
			String mainHNodeId = json.getString("hNodeId");
			
			
			Worksheet mainWorksheet = workspace.getWorksheet(mainWorksheetId);

			HNodePath mainSelectedPath = null;
			List<HNodePath> mainColumnPaths = mainWorksheet.getHeaders().getAllPaths();
			for (HNodePath path : mainColumnPaths) {
				if (path.getLeaf().getId().equals(mainHNodeId)) {
					mainSelectedPath = path;
				}
			}
			Collection<Node> mainNodes = new ArrayList<Node>();
			workspace.getFactory().getWorksheet(mainWorksheetId).getDataTable().collectNodes(mainSelectedPath, mainNodes);
			int i = 0;
			Map<String, String> rowValueMap = new TreeMap<String, String>();
			
			UpdateContainer c = new UpdateContainer();
				for (Node node : mainNodes) {

					Row row = node.getBelongsToRow();
					rowValueMap.put(row.getId(), requestJsonArray.get(i).toString());
					//oldValueMap.put(row.getId(),node.getValue().asString());
					i = i+1;
					
				}
				edit = new MultipleValueEditColumnCommand(mainId, mainWorksheetId, mainHNodeId, rowValueMap);
				c.append(edit.doIt(workspace));
				
				
				return c;
			//return null;
		} catch (Exception e) {
			e.printStackTrace();
			return new UpdateContainer(new ErrorUpdate("Error!"));
		}
	}

	@Override
	public UpdateContainer undoIt(Workspace workspace) {
		// TODO Auto-generated method stub
		
		UpdateContainer c = new UpdateContainer();
		c.append(edit.undoIt(workspace));
		
		return c;
	}

}
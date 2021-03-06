package edu.isi.karma.controller.command;

import java.io.File;
import java.io.PrintWriter;

import org.json.JSONObject;

import edu.isi.karma.controller.update.AbstractUpdate;
import edu.isi.karma.controller.update.InfoUpdate;
import edu.isi.karma.controller.update.UpdateContainer;
import edu.isi.karma.rep.Workspace;
import edu.isi.karma.view.VWorkspace;
import edu.isi.karma.webserver.ServletContextParameterMap;
import edu.isi.karma.webserver.ServletContextParameterMap.ContextParameter;

public class SetKarmaHomeCommand extends Command {

	private String directory;
	
	protected SetKarmaHomeCommand(String id, String directory) {
		super(id);
		if(!directory.endsWith(File.separator))
			this.directory = directory + File.separator;
		else
			this.directory = directory;
	}
	
	@Override
	public String getCommandName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public String getTitle() {
	return "Set Karma Home";
	}

	@Override
	public String getDescription() {
		return this.directory;
	}

	@Override
	public CommandType getCommandType() {
		return CommandType.notInHistory;
	}

	@Override
	public UpdateContainer doIt(Workspace workspace) throws CommandException {
		ServletContextParameterMap.setParameterValue(ContextParameter.USER_DIRECTORY_PATH, directory);
		UpdateContainer uc = new UpdateContainer();
		uc.add(new InfoUpdate("Successfully changed Karma Home Directory"));
		uc.add(new AbstractUpdate() {

			@Override
			public void generateJson(String prefix, PrintWriter pw,
					VWorkspace vWorkspace) {
				JSONObject obj = new JSONObject();
				obj.put(GenericJsonKeys.updateType.name(), "ReloadPageUpdate");
				pw.println(obj.toString());
			}
			
		});
		return uc;
	}

	@Override
	public UpdateContainer undoIt(Workspace workspace) {
		// TODO Auto-generated method stub
		return null;
	}

}

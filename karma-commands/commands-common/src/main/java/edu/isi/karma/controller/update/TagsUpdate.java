/*******************************************************************************
 * Copyright 2012 University of Southern California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 	http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code was developed by the Information Integration Group as part 
 * of the Karma project at the Information Sciences Institute of the 
 * University of Southern California.  For more information, publications, 
 * and related projects, please see: http://www.isi.edu/integration
 ******************************************************************************/
package edu.isi.karma.controller.update;

import edu.isi.karma.rep.metadata.Tag;
import edu.isi.karma.view.VWorkspace;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.Set;

public class TagsUpdate extends AbstractUpdate {

	private static Logger logger = LoggerFactory.getLogger(TagsUpdate.class);

	private enum JsonKeys {
		Tags, Label, Color, Nodes
	}

	@Override
	public void generateJson(String prefix, PrintWriter pw,
			VWorkspace vWorkspace) {
		Set<Tag> tags = vWorkspace.getWorkspace().getTagsContainer().getTags();
		try {
			JSONObject topObj = new JSONObject();
			topObj.put(GenericJsonKeys.updateType.name(),
					TagsUpdate.class.getSimpleName());

			JSONArray arr = new JSONArray();
			for (Tag tag : tags) {
				JSONObject tagObj = new JSONObject();
				tagObj.put(JsonKeys.Label.name(), tag.getLabel().name());
				tagObj.put(JsonKeys.Color.name(), tag.getColor().name());

				JSONArray nodeArr = new JSONArray(tag.getNodeIdList());
				tagObj.put(JsonKeys.Nodes.name(), nodeArr);

				arr.put(tagObj);
			}
			topObj.put(JsonKeys.Tags.name(), arr);

			pw.write(topObj.toString(4));
		} catch (JSONException e) {
			logger.error("Error occured while writing to JSON!", e);
		}

	}

}

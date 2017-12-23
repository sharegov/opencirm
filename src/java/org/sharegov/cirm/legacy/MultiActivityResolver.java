/*******************************************************************************
 * Copyright 2017 Miami-Dade County
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.sharegov.cirm.legacy;

import java.util.List;
import java.util.Properties;

import org.sharegov.cirm.utils.SRJsonActivityUtil;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import mjson.Json;

/**
 * Activity resolver that creates multi-line html text related to more than one
 * activity in an SR.
 * 
 * @author Thomas Hilpold
 *
 */
public class MultiActivityResolver implements VariableResolver {

	public static final String SR_ACTIVITIES = "$$SR_ACTIVITIES$$";
	public static final String DATE_PATTERN = "MMMM d, yyyy h:mm aa";

	@Override
	public String resolve(String variableName, Json sr, Properties properties) {
		String result;
		if (SR_ACTIVITIES.equals(variableName)) {
			result = resolveAllActivityDetail(sr);
		} else {
			result = null;
		}
		return result;
	}

	private String resolveAllActivityDetail(Json sr) {
		String result = "";
		if (sr == null)
			return null;
		List<Json> acts = SRJsonActivityUtil.getActivities(sr);
		acts = SRJsonActivityUtil.sortActivitiesByDateTime(acts);
		for (Json act : acts) {
			String oneActStr = "<p>";
			try {
				String type = SRJsonActivityUtil.getActivityTypeLabel(act);
				if (type.contains("StatusChangeActivity")) continue;
				String created = SRJsonActivityUtil.getHasDateCreated(act, DATE_PATTERN);
				String details = SRJsonActivityUtil.getHasDetails(act);
				String outcome = SRJsonActivityUtil.getHasOutcomeLabel(act);
				String createdby = "";
				try {
					createdby = SRJsonActivityUtil.getIsCreatedByName(act);
				} catch (Exception e) {
				}
				oneActStr += "" + created + "&nbsp;" + type + "&nbsp;" + outcome + "&nbsp;" + "&nbsp;" + createdby + "<br>\r\n";
				oneActStr += "&nbsp;&nbsp;&nbsp; Details: " + details + "<br><br>\r\n";
			} catch (Exception e) {
				ThreadLocalStopwatch.error("MultiActivityResolver Error during resolving activity " + act + e);
				e.printStackTrace();
				oneActStr += "N/A";
			} finally {
				oneActStr += "</p>\r\n";
			}
			result += oneActStr;
		}
		return result;
	}

}

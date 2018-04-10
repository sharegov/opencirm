/*******************************************************************************
 * Copyright 2018 Miami-Dade County
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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import mjson.Json;

/**
 * Resolves SR Attachment related variables to html links as unordered lists.
 * 
 * @author Thomas Hilpold
 *
 */
public class AttachmentResolver implements VariableResolver {
	
	public final static String VAR_SR_IMAGES = "$$SR_IMAGES$$";

	@Override
	public String resolve(String variableName, Json sr, Properties properties) {
		String result = null;
		if (VAR_SR_IMAGES.equals(variableName)) {
			result = resolveSrImagesHtml(sr);
		}
		return result;
	}

	/**
	 * Returns a xhtml conform unordered HTML list containing links to all attached png or jpg images labeled SR Image <N>.
	 * @param sr
	 * @return empty string is returned if no attachments.
	 */
	private String resolveSrImagesHtml(Json sr) {
		String result = "";
		int i = 0;
		for (String link : getImages(sr)) {
			i++;
			result += "  <li><a href=\"" + link + "\">SR Image " + i + "</a></li>\r\n";
		}
		if (result.length() > 0) {
			result = "<ul>\r\n" + result;
			result = result + "</ul>\r\n";
		}
		return result;		
	}
	
	private List<String> getImages(Json sr) {
		Json hasAttachment = sr.at("hasAttachment");
		List<String> allAttachments = new ArrayList<>();
		List<String> images = new ArrayList<>();
		if (hasAttachment == null || hasAttachment.isNull()) {
			return images;
		}
		if (hasAttachment.isString()) {
			allAttachments.add(hasAttachment.asString());
		} else if (hasAttachment.isArray()){
			List<Json> hasAttachmentList = hasAttachment.asJsonList();
			for (Json a : hasAttachmentList) {
				if (a.isString()) {
					allAttachments.add(a.asString());
				}
			}
		}
		for (String a : allAttachments) {
			if (isImage(a)) {
				images.add(a);
			}
		}
		return images;
		
	}
	
	private boolean isImage(String attachmentStr) {
		if (attachmentStr.toLowerCase().contains("-jpg-")) {
			return true;
		}
		else if (attachmentStr.toLowerCase().contains("-png-")) {
			return true;
		}
		else {
			return false;
		}
	}
}

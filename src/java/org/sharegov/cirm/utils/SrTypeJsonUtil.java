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
package org.sharegov.cirm.utils;


import mjson.Json;

/**
 * SR type json static utility methods operating on Json types.
 * For Json type format see type individuals in Refs.owlJsonCache.
 * 
 * @author Thomas Hilpold
 *
 */
public class SrTypeJsonUtil {

	public static float getDurationDays(Json type) {
		if (!type.has("hasDurationDays")) {
			return 0;
		} else {
			String durationDays = type.at("hasDurationDays").asString();
			float result = Float.parseFloat(durationDays);
			return result;
		}
	}
	
	public static boolean isDuration5DayBased(Json type) {
		if (!type.has("isDuration5DayBased")) {
			return false;
		} else {
			String ww = type.at("isDuration5DayBased").asString();
			boolean result = Boolean.parseBoolean(ww);
			return result;
		}
	}
	
	/**
	 * Calculates and adds two boolean properties hasCitizenEmailTemplate and hasCitizenSmsTemplate if true.
	 * @param type
	 */
	public static void applyTransientProperties(Json type) {
		boolean hasCitizenEmailTemplate = hasCitizenEmailTemplate(type);
		boolean hasCitizenSmsTemplate = hasCitizenSmsTemplate(type);
		if (hasCitizenEmailTemplate) {
			type.set("transientHasCitizenEmailTemplate", hasCitizenEmailTemplate);
		}
		if (hasCitizenSmsTemplate) {
			type.set("transientHasCitizenSmsTemplate", hasCitizenSmsTemplate);
		}
	}
	
	public static boolean hasCitizenEmailTemplate(Json type) {
		if (!type.has("hasActivity")) return false;
		boolean result = false;
		if (type.at("hasActivity").isArray()) {
			for (Json activity : type.at("hasActivity").asJsonList()) {
				if (activityHasCitizenEmailTemplate(activity)) {
					result = true;
					break;
				}
			}
		} else if (type.at("hasActivity").isObject()) {
			result = activityHasCitizenEmailTemplate(type.at("hasActivity"));
		}
		return result;
	}
	
	public static boolean hasCitizenSmsTemplate(Json type) {
		if (!type.has("hasActivity")) return false;
		boolean result = false;
		if (type.at("hasActivity").isArray()) {
			for (Json activity : type.at("hasActivity").asJsonList()) {
				if (activityHasCitizenSmsTemplate(activity)) {
					result = true;
					break;
				}
			}
		} else if (type.at("hasActivity").isObject()) {
			result = activityHasCitizenSmsTemplate(type.at("hasActivity"));
		}
		return result;
	}
	
	private static boolean activityHasCitizenEmailTemplate(Json activity) {
		if (!activity.isObject() || !activity.has("hasEmailTemplate") || !activity.at("hasEmailTemplate").has("hasTo")) return false;
		boolean result = activity.at("hasEmailTemplate").at("hasTo").asString().contains("CITIZENS_EMAIL");
		return result;
	}

	private static boolean activityHasCitizenSmsTemplate(Json activity) {
		if (!activity.isObject() || !activity.has("hasSmsTemplate") || !activity.at("hasSmsTemplate").has("hasTo")) return false;
		boolean result = activity.at("hasSmsTemplate").at("hasTo").asString().contains("CITIZENS_CELL_PHONE");
		return result;
	}
}

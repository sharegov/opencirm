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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mjson.Json;

/**
 * Utility for new and existing SR json property access.
 * 
 * @author Thomas Hilpold
 *
 */
public class SrJsonUtil {

	/**
	 * Returns the X coordinate for the Sr json which may be a double or string property or not exist.
	 * Expected at: properties.hasXCoordinate
	 * 
	 * @param sr a new or existing sr with properties at root
	 * @return null if not exists or string could not be converted to double
	 */
	public Double gethasXCoordinate(Json sr) {
		return getCoordinate(sr, "hasXCoordinate");
	}

	/**
	 * Returns the Y coordinate for the Sr json which may be a double or string property or not exist.
	 * Expected at: properties.hasYCoordinate
	 * 
	 * @param sr a new or existing sr with properties at root
	 * @return null if not exists or string could not be converted to double
	 */
	public Double gethasYCoordinate(Json sr) {
		return getCoordinate(sr, "hasYCoordinate");
	}
	
	/**
	 * Gets x or y coordinate from an sr json, which be a string, a double, null, or not exist.
	 * @param sr
	 * @param hasXYCoordinate
	 * @return the coordinate or null
	 */
	private Double getCoordinate(Json sr, String hasXYCoordinate) {
		if(!"hasXCoordinate".equals(hasXYCoordinate) && !"hasYCoordinate".equals(hasXYCoordinate)) {
			throw new IllegalArgumentException("only hasXCoordinate or hasYCoordinate allowed");
		}
		Double result;
		if (sr.has("properties") && sr.at("properties").has(hasXYCoordinate)) {
			Json xJ = sr.at("properties").at(hasXYCoordinate);
			if (xJ.isNumber()) {
				result = xJ.asDouble();
			} else if (xJ.isString()) {
				try {
					result = Double.parseDouble(xJ.asString());
				} catch(Exception e) {
					ThreadLocalStopwatch.error("ERROR: "+ hasXYCoordinate + " string not parseable as double, was " + xJ.asString());
					result = null;
				}
			} else if (xJ.isNull()) {
				result = null;
			} else {
			
				throw new RuntimeException(hasXYCoordinate + " illegal json type, was " + xJ);
			}
		} else {
			//no properties or no hasXCoordinate, ok in some cases, return null.
			result = null;
		}
		return result;
	}
	
	/**
	 * Gets the notification preference of the citizen by hasNotificationPreference property.
	 * (if more than one citizen is found in the SR, the first serialized citizen's preference is returned.)
	 * If the first citizen does not have a hasNotificationPreference, MessagingPreference.Undefined is returned.
	 * @param sr
	 * @return null, if no citizen found in SR.
	 */
	public MessagingPreference getCitizenNotificationPreference(Json sr) {
		List<Json> citizens = getCitizenActors(sr);
		if (citizens.isEmpty()) {
			return null;
		} else {
			return getActorNotificationPreference(citizens.get(0));
		}
	}
	
	/**
	 * Gets the actors notification preference or returns Undefined if not available.
	 * @param serviceCaseActor
	 * @return
	 */
	MessagingPreference getActorNotificationPreference(Json serviceCaseActor) {
		if (serviceCaseActor.has("hasNotificationPreference")) {
			String prefValue = serviceCaseActor.at("hasNotificationPreference").asString();
			if (prefValue.isEmpty()) {
				return MessagingPreference.UNDEFINED;
			} else {
				return MessagingPreference.valueOf(prefValue);
			}
		} else {
			return MessagingPreference.UNDEFINED;
		}
	}
	
	public List<Json> getCitizenActors(Json sr) {
		List<Json> result = new ArrayList<>();
		// "hasServiceCaseActor" [] //hasServiceActor obj with iri or string http://www.miamidade.gov/cirm/legacy#CITIZEN
		List<Json> actors = getActors(sr);
		for (Json actor : actors) {
			if (isCitizenActor(actor)) {
				result.add(actor);
			}
		}
		return result;
	}
	
	/**
	 * Determines if the actor is of type CITIZEN.
	 * @param serviceCaseActor
	 * @return
	 */
	boolean isCitizenActor(Json serviceCaseActor) {
		if (!serviceCaseActor.has("hasServiceActor")) throw new IllegalArgumentException("No hasServiceActor property in " + serviceCaseActor);
		boolean result;
		Json hasServiceActor = serviceCaseActor.at("hasServiceActor");
		if (hasServiceActor.isObject()) {
			result = hasServiceActor.at("iri").asString().endsWith("#CITIZEN");
		} else if (hasServiceActor.isString()) {
			result = hasServiceActor.asString().endsWith("#CITIZEN");
		} else {
			throw new IllegalArgumentException("hasServiceActor must be string or ojb with iri " + serviceCaseActor);
		}
		return result;
	}
	
	
	/**
	 * Returns hasServiceCaseActor always as Json List.
	 * @param sr
	 * @return
	 */
	public List<Json> getActors(Json sr) {
		List<Json> result;
		Json root = sr;
		if (root.has("properties")) root = root.at("properties");
		if (root.has("hasServiceCaseActor")) {
			result = (root.at("hasServiceCaseActor").isArray()) ? root.at("hasServiceCaseActor").asJsonList() : Collections.singletonList(root.at("hasServiceCaseActor"));
		} else {
			result = Collections.emptyList();
		}
		return result;
	}
}

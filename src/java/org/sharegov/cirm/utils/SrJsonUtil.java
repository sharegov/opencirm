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
}

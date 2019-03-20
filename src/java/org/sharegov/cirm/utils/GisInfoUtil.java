/*******************************************************************************
 * Copyright 2015 Miami-Dade County
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
 * GisInfoUtil contains read only helper methods for Gis or locationInfo access.
 * 
 * @author Thomas Hilpold
 *
 */
public class GisInfoUtil {

	/**
	 * Returns folio if found or null.
	 * 
	 * @param locationInfo
	 * @return
	 */
	public Long getFolioFromLocationInfo(Json locationInfo) {
		Long result = null;
		Json folioJson = null; 
		try {
			folioJson = locationInfo.at("address").at("propertyInfo").at("parcelFolioNumber");
		} catch (Exception e) {
			ThreadLocalStopwatch.error("WARN: Folio not found in locationInfo at address.propertyInfo.parcelFolioNumber with exc: " + e);
		};		
		if (folioJson != null) {
    		try {
    			if (folioJson.isString()) {
    				result = Long.parseLong(folioJson.asString());
    			} else {
    				result = folioJson.asLong();
    			}
    		} catch (Exception e) {
    			ThreadLocalStopwatch.error("ERROR: Folio value was not integer, was " + folioJson + " exc: "  + e);
    		}
		}
		return result;
	}

}

/*******************************************************************************
 * Copyright 2014 Miami-Dade County
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
package org.sharegov.cirm.gis;

import org.semanticweb.owlapi.model.IRI;
import org.sharegov.cirm.utils.Mapping;

import mjson.Json;

/**
 * <p>
 * This interface connects CiRM to a back-end GIS system. Since we don't 
 * have much experience with different GIS system, the abstraction here 
 * doesn't pretend to be right in any sense whatsoever. A GIS system offers
 * several kinds of services:
 * </p>
 * <ul>
 * <li>Given an address written as a single line of text, parse into 
 * address components and obtain the XY coordinates of the location.</li>
 * <li>
 * Given an XY coordinate, obtain data in the form of layers (essentially
 * grouping of name/value pair associated with regions) pertaining to that
 * location.
 * </li>
 * <li>
 * Given a more complex location description, like an intersection, a corridor
 * or a block delimited by 4 streets, return similar type of information. Except,
 * here for a given attribute, we may several values since the region may
 * be large.
 * </li>
 * </ul>
 * 
 * <p>
 * This interface shouldn't be considered very stable.
 * </p>
 * 
 * <p>
 * GIS information is collected for a particular service case and may sometimes depend
 * on other attributes of the case. To customize collecting GIS information based on a
 * specific case, implement the <code>getInformationForCase</code> method
 * </p>
 * 
 * @author Borislav Iordanov, Thomas Hilpold
 *
 */
public interface GisInterface
{
	/**
	 * Gets layer data from GIS for the given projected location.
	 * 
	 * @param x
	 * @param y
	 * @param layers
	 * @return
	 */
    Json getLocationInfo(double x, double y, String [] layers);
    
    /**
     * Gets layer data from GIS for the given projected location and retries on certain errors considered retriable.
     * 
     * @param x
     * @param y
     * @param layers
     * @param maxAttempts number of attempts, 2 means one retry on retriable error.
     * @param sleepTime in milliseconds between retriable failed attempts
     * @return
     */
    Json getLocationInfo(double x, double y, String [] layers, int maxAttempts, long sleepTimeMs);
    
    /**
     * Gets public works GIS data specific to non-location/property types, which are Area, Corridor, and Intersection.
     *  
     * @param locationType
     * @param street1 must not be null
     * @param street2 must not be null (Intersection or other)
     * @param street3 must not be null for Corridor and Area
     * @param street4 must not be null for Area
     * @return nil() if locationtype unknown or street params don't match locationtype.
     */
    Json getExtendedGisInfo(String locationType, String street1, String street2, String street3, String street4);
    
    /**
     * Gets extendedGisInfo if the given serviceCase is a MD-PWS interface case and has a non Location/Property locationType.
     * For MD-PWS, GIS1 answer will determine locationType and GIS2-6 streets 1-4.
     * 
     * @param serviceCase
     * @return extendedGisInfo or Json.nil() if Non PWS or property location.
     */
    Json getInformationForCase(Json serviceCase);
    
    Mapping<Json, Boolean> makeGisFilter(Json locationInfo, boolean removeUnavailable, String[] layers);
    boolean isAvailable(Json propertyInfo, IRI caseType);    
    boolean testLayerValue(Json locationInfo, 
    					   String layerName, 
    					   String attributeName, 
    					   String valueExpression);
}

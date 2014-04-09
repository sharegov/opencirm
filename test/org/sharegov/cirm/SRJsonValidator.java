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
package org.sharegov.cirm;

import mjson.Json;

import org.junit.Assert;

/**
 * Assertions that should hold for various Json representations of a Cirm ServiceRequest (SR).
 * TODO extend this
 * 
 * @author Thomas Hilpold
 *
 */
public class SRJsonValidator
{
	/**
	 * Asserts that the updateJSON is valid.
	 * Does not look deeper than properties.
	 * Does not assert a properties.atAdress as this is optional now.
	 * Based on DOG_UPDATE.json from 12/12/2012
	 * @param srUpdateJson
	 */
	public static void assertValidUpdateSR(Json srUpdateJson) 
	{
		Assert.assertTrue(srUpdateJson.at("boid").asInteger() >= 0);
		Assert.assertTrue(srUpdateJson.at("type").asString().startsWith("legacy:"));
		Assert.assertTrue(srUpdateJson.at("iri").asString().startsWith("http://www.miamidade.gov/bo/"));
		Assert.assertTrue(srUpdateJson.at("properties").isObject());
		if (srUpdateJson.at("properties").has("hasGisDataId")) 
		{
			Assert.assertTrue(srUpdateJson.at("properties").at("hasGisDataId").isNumber());
		}
		if (srUpdateJson.at("properties").has("isCreatedBy")) 
		{
			Assert.assertTrue(srUpdateJson.at("properties").at("isCreatedBy").isString());
		}
		if (srUpdateJson.at("properties").has("hasYCoordinate")) 
		{
			Assert.assertTrue(srUpdateJson.at("properties").at("hasYCoordinate").isNumber());
			Assert.assertTrue(srUpdateJson.at("properties").at("hasXCoordinate").isNumber());
		}
//		Assert.assertTrue(srUpdateJson.at("properties").at("hasDateCreated").asString().endsWith("-05:00"));
//		Assert.assertTrue(srUpdateJson.at("properties").at("hasImage").isArray());
//		Assert.assertTrue(srUpdateJson.at("properties").at("hasRemovedImage").isArray());
//		Assert.assertTrue(srUpdateJson.at("properties").at("hasDateLastModified").asString().endsWith("-05:00"));
//		Assert.assertTrue(srUpdateJson.at("properties").at("isModifiedBy").isString());
//		Assert.assertTrue(srUpdateJson.at("properties").at("legacy:hasServiceCaseActor").isArray());
//		Assert.assertTrue(srUpdateJson.at("properties").at("legacy:hasServiceAnswer").isArray());
//		Assert.assertTrue(srUpdateJson.at("properties").at("legacy:hasDueDate").asString().endsWith("Z"));
//		Assert.assertTrue(srUpdateJson.at("properties").at("legacy:hasIntakeMethod").isObject());
//		Assert.assertTrue(srUpdateJson.at("properties").at("legacy:hasPriority").isObject());
//		Assert.assertTrue(srUpdateJson.at("properties").at("legacy:hasStatus").isObject());
//		Assert.assertTrue(srUpdateJson.at("properties").at("legacy:hasServiceActivity").isArray());
	}

}

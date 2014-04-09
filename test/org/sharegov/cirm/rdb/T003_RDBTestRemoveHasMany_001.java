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
package org.sharegov.cirm.rdb;

import java.io.File;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import mjson.Json;
import org.junit.*;
import org.sharegov.cirm.OWL;
//import org.sharegov.cirm.SRJsonValidator;
import org.sharegov.cirm.rest.LegacyEmulator;
import org.sharegov.cirm.utils.GenUtils;

public class T003_RDBTestRemoveHasMany_001
{
	
	public static final String TEST_JSON = "BULKYTRA2.json";
	
	static Json json1; 
	static String json1Str; 

	static LegacyEmulator le = new LegacyEmulator();
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		URL buylkytraJSONURL = T003_RDBTestRemoveHasMany_001.class.getResource(TEST_JSON);
		json1Str = GenUtils.readTextFile(new File(buylkytraJSONURL.getFile()));
		json1 = Json.read(json1Str);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception
	{
	}

	/**
	 * Tests as follows:
	 * 1. saves a new SR with X hasMany (legacy:hasServiceCaseActor)
	 * 2. loads same SR, checks number of legacy:hasServiceCaseActor matches X
	 * 3. removes last ServiceCaseActor from loaded (Json)
	 * 4. prefixes loaded modified
	 * 5. validates Json for update
	 * 6. updates SR without last ServiceCaseActor
	 * 7. loads SR check number of ServiceCaseActor is X - 1
	 */
	@Test
	public void testRemoveOneHasManyServiceCaseActor()
	{
		//1. Save Json BO V1
		Assert.assertNotNull("Probe SR JSON file has legacy:hasServiceCaseActor as expected?", json1.at("properties").at("legacy:hasServiceCaseActor"));
		int nrOfServiceCaseActors = json1.at("properties").at("legacy:hasServiceCaseActor").asJsonList().size();
		Assert.assertTrue("Check more than zero ServiceCaseActors", nrOfServiceCaseActors > 0);
		System.out.println("Original SR Json has " + nrOfServiceCaseActors + " hasMany ServiceCaseActors");
		Json bo = le.saveNewServiceRequest(json1Str);
		long boid = bo.at("data").at("boid").asLong();
		Json bo2 = le.lookupServiceCase(boid);
		//Missing the prefixes already:
		int nrOfServiceCaseActorsLoaded = bo2.at("bo").at("properties").at("hasServiceCaseActor").asJsonList().size();
		Assert.assertTrue("Check ServiceCaseActor number loaded == saved", nrOfServiceCaseActorsLoaded == nrOfServiceCaseActors);
		//2. Remove last ServiceCaseActor for bo 
		bo2.at("bo").at("properties").at("hasServiceCaseActor").delAt(nrOfServiceCaseActors - 1);
		//I don't like that i have to do this because the update NPE's otherwise.
		bo2.at("bo").at("properties").set("hasServiceActivity", Json.array());
		Assert.assertTrue("Successfully removed serviceCaseActor (JSON)?", 
				bo2.at("bo").at("properties").at("hasServiceCaseActor")
					.asJsonList().size() == nrOfServiceCaseActors - 1);
		//3. Prepare prefixes as they are needed for update
		Json updateJson = bo2.at("bo");
		Json updateJsonPrefixed = OWL.prefix(updateJson);
		//Don't like to do this either!
		updateJsonPrefixed.set("type", "legacy:" + updateJsonPrefixed.at("type").asString());
		System.out.println(updateJsonPrefixed.toString());
//		SRJsonValidator.assertValidUpdateSR(updateJsonPrefixed);
		//4. Update 
		Json bo3 = le.updateServiceCase(bo2.at("bo").toString());
		System.out.println(bo3);
		//5. Load
		Json loadedSRPostUpdate = le.lookupServiceCase(boid);
		//6. Ensure atAddress not stored anymore
		System.out.println(loadedSRPostUpdate);
		if (loadedSRPostUpdate.at("bo").at("properties").has("hasServiceCaseActor")) 
		{
			if (loadedSRPostUpdate.at("bo").at("properties").at("hasServiceCaseActor").isArray()) 
			{
				Assert.assertTrue("Successfully removed serviceCaseActor through BO update (DB)?", 
						loadedSRPostUpdate.at("bo").at("properties").at("hasServiceCaseActor")
							.asJsonList().size() == nrOfServiceCaseActors - 1);
			} 
			else 
			{
				Assert.assertTrue(loadedSRPostUpdate.at("bo").at("properties").at("hasServiceCaseActor").isObject());
				Assert.assertTrue("Successfully removed serviceCaseActor through BO update (DB)?", 1 == nrOfServiceCaseActors - 1);
			}
		} 
		else 
		{
			Assert.assertTrue("Successfully removed only serviceCaseActor through BO update (DB)?", 0 == nrOfServiceCaseActors - 1);	
		}
	}
}

	

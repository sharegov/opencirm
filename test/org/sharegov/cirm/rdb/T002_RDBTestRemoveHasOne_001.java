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
import mjson.Json;
import org.junit.*;
import org.sharegov.cirm.OWL;
//import org.sharegov.cirm.SRJsonValidator;
import org.sharegov.cirm.rest.LegacyEmulator;
import org.sharegov.cirm.utils.GenUtils;

public class T002_RDBTestRemoveHasOne_001
{
	
	public static final String TEST_JSON = "BULKYTRA2.json";
	
	static Json json1; 
	static String json1Str; 

	static LegacyEmulator le = new LegacyEmulator();
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		URL buylkytraJSONURL = T002_RDBTestRemoveHasOne_001.class.getResource(TEST_JSON);
		json1Str = GenUtils.readTextFile(new File(buylkytraJSONURL.getFile()));
		json1 = Json.read(json1Str);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception
	{
	}

	/**
	 * Tests as follows:
	 * 1. saves a new SR with hasOne (atAdress)
	 * 2. loads same SR, checks atAdress
	 * 3. removes atAddress from loaded (Json)
	 * 4. prefixes loaded modified
	 * 5. validates Json for update
	 * 6. updates SR without atAddress
	 * 7. loads SR checkNull atAddress
	 */
	@Test
	public void testRemoveOneHasOne()
	{
		//1. Save Json BO V1
		Assert.assertNotNull("Probe SR JSON file has atAddress as expected?", json1.at("properties").at("atAddress"));
		Json bo = le.saveNewServiceRequest(json1Str);
		long boid = bo.at("data").at("boid").asLong();
		Json bo2 = le.lookupServiceCase(boid);
		Assert.assertNotNull("Probe SR has atAddress as expected after save/load?", bo2.at("bo").at("properties").at("atAddress"));
		//2. Remove atAddress for bo 
		bo2.at("bo").at("properties").delAt("atAddress");
		//I don't like that i have to do this because the update NPE's otherwise.
		bo2.at("bo").at("properties").set("legacy:hasServiceActivity", Json.array());
		Assert.assertNull("Successfully removed address?", bo2.at("bo").at("properties").at("atAddress"));
		//System.out.println(bo2.toString());
		//3. Prepare prefixes as they are needed for update
		Json updateJson = bo2.at("bo");
		Json updateJsonPrefixed = OWL.prefix(updateJson);
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
		//System.out.println(bo4.at("bo").at("properties").at("atAddress"));
		Assert.assertNull("Successfully removed Address through BO update?",loadedSRPostUpdate.at("bo").at("properties").at("atAddress"));
	}		
}
	

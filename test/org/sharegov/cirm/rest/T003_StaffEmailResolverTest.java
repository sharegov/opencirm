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
package org.sharegov.cirm.rest;

import static org.junit.Assert.*;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import mjson.Json;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.legacy.ServiceRequestResolver;
import org.sharegov.cirm.legacy.StaffEmailResolver;
import org.sharegov.cirm.owl.CachedReasoner;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.JsonUtil;
import org.sharegov.cirm.utils.SRJsonActivityUtil;
//import org.sharegov.cirmx.maintenance.AllowAnySSL;

import com.itextpdf.text.log.SysoLogger;

/**
 * Tests SRJsonActivityUtil AND StaffEmailResolver variables: 
 * "$$SR_CREATED_BY_EMAIL$$";
 * "$$SR_ASSIGNED_STAFF_EMAIL$$"; needs legacycode PERSCNTC & isAssignedTo eckey.
 * 	Activity that's modified:
 * 	{
			"isCreatedBy": "e300371",
			"hasUpdatedDate": "2014-03-20T12:06:04.000-0400",
			"isAssignedTo": "VARIATION HERE: e300371",
			"hasDetails": "Open sr gave number to collection agency and instruction to send letter to ASD.",
			"label": "ServiceActivity28719708",
			"type": "ServiceActivity",
			"hasDateCreated": "2014-03-20T12:06:04.000-0400",
			"iri": "http://www.miamidade.gov/ontology#ServiceActivity28719708",
			"hasActivity": {
				"label": "Personal Contact",
				"iri": "http://www.miamidade.gov/cirm/legacy#ASDEATH_PERSCNTC"
			}

 * @author Thomas Hilpold
 * 
 */
public class T003_StaffEmailResolverTest extends T000_UserServiceTest
{

	public final static String SR_FILE = "SRForResolverTest.json";
	public final static String MD_FILE = "T001_UserServiceTest.md.json";
	public final static String COM_FILE = "T001_UserServiceTest.com.json";
	public final static String [] SR_RESOLVER_VARS = new String[] {"$$SR_CREATED_BY_EMAIL$$", 
		"$$SR_CREATED_BY_ELECTR_ADDR$$", 
		"$$SR_CREATED_BY_NAME$$", 
		"$$SR_CREATED_BY_PHONE$$" };
	
	private static List<Json> data;
	private static Json sr;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		T000_UserServiceTest.setUpBeforeClass();
		CachedReasoner.DBG_CACHE_MISS = false;
		// new AllowAnySSL().installPermissiveTrustmanager();
		// Load both files into Json List of such objects.
		URL f1 = T003_StaffEmailResolverTest.class.getResource(MD_FILE);
		URL f2 = T003_StaffEmailResolverTest.class.getResource(COM_FILE);
		URL f3 = T003_StaffEmailResolverTest.class.getResource(SR_FILE);
		Json j1 = Json.read(GenUtils.readAsStringUTF8(f1));
		Json j2 = Json.read(GenUtils.readAsStringUTF8(f2));
		sr  = Json.read(GenUtils.readAsStringUTF8(f3));
		sr = sr.at("properties");
		data = new LinkedList<Json>();
		data.addAll(j1.asJsonList());
		data.addAll(j2.asJsonList());
		System.out.println("User service test using " + data.size() + " users.");
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception
	{
		T000_UserServiceTest.tearDownAfterClass();
		data = null;
	}

	@Test
	public void testStaffEmailResolverAllVars()
	{
		StaffEmailResolver srr =  new StaffEmailResolver(); 
		Properties properties = new Properties();
		properties.setProperty("LEGACY_CODE","PERSCNTC");
		for (Json testUser : data)
		{
			String ecKey = testUser.at("ecKey").asString();
			String testDepartment = testUser.at("mdcDepartment").asString();
			String testDivision = testUser.at("mdcDivision").asString();
			String testEmail = testUser.at("email").asString();
			
			//Set the user as createdBy & assignedTo at PERSCNTC activity.
			sr.set("isCreatedBy", ecKey);
			System.out.println("testStaffEmailResolverAllVars Testing for User: " + ecKey + " dep " + testDepartment + " div " + testDivision);
			Json activity = SRJsonActivityUtil.getMostRecentActivityByLegacyCode(sr, "PERSCNTC");
			activity.set("isAssignedTo", ecKey); 
			assertTrue("Retrieving test activity failed, check SR and code", activity.at("hasActivity").at("iri").asString().equals("http://www.miamidade.gov/cirm/legacy#ASDEATH_PERSCNTC"));
			String var1AssignedStaffEMAIL = srr.resolve(StaffEmailResolver.VAR_ASSIGNED_STAFF, sr, properties);
			String var2SRCreatedByEMAIL = srr.resolve(StaffEmailResolver.VAR_CREATED_BY, sr, properties);
			assertTrue("VAR_ASSIGNED_STAFF failed for " + ecKey, testEmail.equalsIgnoreCase(var1AssignedStaffEMAIL));
			assertTrue("VAR_CREATED_BY failed for ", testEmail.equalsIgnoreCase(var2SRCreatedByEMAIL));
			System.out.println("Pass.");
		}
	}

	@Test
	public void testSRJsonActivityUtilAllVars()
	{
		for (Json testUser : data)
		{
			String ecKey = testUser.at("ecKey").asString();
			String testDepartment = testUser.at("mdcDepartment").asString();
			String testDivision = testUser.at("mdcDivision").asString();
			String testEmail = testUser.at("email").asString();
			String testFullName = testUser.at("FirstName").asString() + " " + testUser.at("LastName").asString();
			
			//Set the user as createdBy & assignedTo at PERSCNTC activity.
			sr.set("isCreatedBy", ecKey);
			System.out.println("testSRJsonActivityUtilAllVars Testing for User: " + ecKey + " dep " + testDepartment + " div " + testDivision);
			Json activity = SRJsonActivityUtil.getMostRecentActivityByLegacyCode(sr, "PERSCNTC");
			activity.set("isAssignedTo", ecKey); 
			activity.set("isCreatedBy", ecKey);
			assertTrue("Retrieving test activity failed, check SR and code", activity.at("hasActivity").at("iri").asString().equals("http://www.miamidade.gov/cirm/legacy#ASDEATH_PERSCNTC"));
			String var1AssignedStaffName = SRJsonActivityUtil.getAssignedStaffName(activity);
			String var2ActivityCreatedByName = SRJsonActivityUtil.getIsCreatedByName(activity);
			assertTrue("var1AssignedStaffName(activity) failed for " + ecKey + " was " + var1AssignedStaffName, testFullName.equalsIgnoreCase(var1AssignedStaffName));
			assertTrue("getIsCreatedByName(activity) failed for "+ ecKey + " was " + var2ActivityCreatedByName, testFullName.equalsIgnoreCase(var2ActivityCreatedByName));
			System.out.println("Pass.");
		}
	}

}

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

import mjson.Json;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.owl.CachedReasoner;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.JsonUtil;
//import org.sharegov.cirmx.maintenance.AllowAnySSL;

import com.itextpdf.text.log.SysoLogger;

/**
 * 
 * cirm.top.postObject('/users/profile', {"username":"e309888", "groups": true,
 * "access": false} ).profile; Json Input Format: [] of: { "ecKey" : "c0006",
 * "groups" : [ "City_of_Miami", "COM_STAT"], "FirstName" : "Victor", "LastName"
 * : "Ochoa", "email" : "VOCHOA@miamigov.com", "mdcDepartment" : "COM",
 * "mdcDivision" : "140", "comment" : "Data from: Onto, ENET", },
 * 
 * @author Thomas Hilpold
 * 
 */
public class T001_UserServiceTest extends T000_UserServiceTest
{

	public final static String MD_FILE = "T001_UserServiceTest.md.json";
	public final static String COM_FILE = "T001_UserServiceTest.com.json";

	private static List<Json> data;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		T000_UserServiceTest.setUpBeforeClass();
		CachedReasoner.DBG_CACHE_MISS = false;
		// new AllowAnySSL().installPermissiveTrustmanager();
		// Load both files into Json List of such objects.
		URL f1 = T001_UserServiceTest.class.getResource(MD_FILE);
		URL f2 = T001_UserServiceTest.class.getResource(COM_FILE);
		Json j1 = Json.read(GenUtils.readAsStringUTF8(f1));
		Json j2 = Json.read(GenUtils.readAsStringUTF8(f2));
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

	@Before
	public void setUp() throws Exception
	{
	}

	@After
	public void tearDown() throws Exception
	{
	}

	@Test
	public void testUserProfileAndGroups()
	{
		for (Json testUser : data)
		{
			String ecKey = testUser.at("ecKey").asString();
			String testDepartment = testUser.at("mdcDepartment").asString();
			String testDivision = testUser.at("mdcDivision").asString();
			System.out.println("ProfileAndGroups Testing for User: " + ecKey + " dep " + testDepartment + " div " + testDivision);
			// String testEmail = testUser.at("email").asString();
			// String testPhone = testUser.at("phone").asString();
			List<Json> testGroups = testUser.at("groups").asJsonList();
			Json userProfileRequest = Json.object();
			userProfileRequest.set("username", ecKey);
			userProfileRequest.set("password", "anypass");
			// userProfileRequest.set("provider", "enet");
			userProfileRequest.set("groups", true);
			Json userProfile = u.userProfile(userProfileRequest);
			// assert ok true maybe
			userProfile = userProfile.at("profile");
			// System.out.println(userProfile.toString());
			String mdcDepartment = userProfile.at("mdcDepartment").asString();
			String mdcDivision = userProfile.at("mdcDivision").asString();
			GenUtils.ensureArray(userProfile, "groups");
			List<Json> groups = userProfile.at("groups").asJsonList();
			assertTrue("Groups failed for " + ecKey, isMatchingGroups(testGroups, groups));
			assertTrue("mdcDepartment code failed for " + ecKey, testDepartment.equals(mdcDepartment));
			assertTrue("mdcDivision code failed for " + ecKey, testDivision.equals(mdcDivision));
			System.out.println("Pass.");
		}
	}

	boolean isMatchingGroups(List<Json> a, List<Json> b)
	{
		if (a.size() != b.size())
		{
			System.err.println("Group sizes don't match: a : " + a.size() + " b: " + b.size());
			return false;
		}
		for (int i = 0; i < a.size(); i++)
		{
			boolean found = false;
			String gA = a.get(i).asString();
			if (!gA.toUpperCase().startsWith("HTTP"))
			{
				gA = OWL.fullIri(gA).toString();
			}
			int j = 0;
			while (!found && j < b.size())
			{
				String gB = b.get(j).asString();
				if (!gB.toUpperCase().startsWith("HTTP"))
				{
					gB = OWL.fullIri(gB).toString();
				}
				if (gA.equals(gB))
				{
					found = true;
				}
				j++;
			}
			if (!found)
			{
				System.err.println("Could not find matching group for: " + gA);
				return false;
			}
		}
		return true;
	}

//	@Test
//	public void testAccessPolicies()
//	{
//		fail("Not yet implemented");
//	}

	@Test
	public void testSearchUserById()
	{
		fail("Not yet implemented");
	}

	@Test
	public void testGetUserJson()
	{
		fail("Not yet implemented");
	}

	@Test
	public void testGetUserById()
	{
		String errorKeys = "";
		int errorCount = 0;
		for (Json testUser : data)
		{
			String ecKey = testUser.at("ecKey").asString();
			String testDepartment = testUser.at("mdcDepartment").asString();
			String testDivision = testUser.at("mdcDivision").asString();
			System.out.println("GetUserById Testing for User: " + ecKey + " dep " + testDepartment + " div " + testDivision);
			String testEmail = testUser.at("email").asString();
			String testPhone = testUser.at("phone").asString();
			Json userAllProv = u.getUserById(ecKey).at("profile");
			System.out.println(userAllProv.toString());
			Json mdcDepartment = userAllProv.at("enet", Json.object()).at("mdcDepartment");
			Json mdcDivision = userAllProv.at("enet", Json.object()).at("mdcDivision");
			Json email = userAllProv.at("email", "");
			//onto phone mapped to PhoneNember toplevel???
			Json phone;
			if (userAllProv.has("PhoneNumber")) 
			{
				phone = userAllProv.at("PhoneNumber", "");
			}
			else if (userAllProv.has("intranet"))
			{
				phone = userAllProv.at("intranet", Json.object()).at("telephoneNumber", "");
			} 
			else if (userAllProv.has("bluebook"))
			{
				phone = userAllProv.at("bluebook", Json.object()).at("WK_Phone", "");
			}
			else 
			{ 
				phone = null;
				System.err.println("NO PHONE IN ANY PROVIDER");
				errorCount++; errorKeys += ecKey + " ";
			}
			if (!testDepartment.equals(mdcDepartment.asString()))
			{
				System.err.println(mdcDepartment.asString() + " <> " + testDepartment);
				errorCount++; errorKeys += ecKey + " ";
			}
			if (!testDivision.equals(mdcDivision.asString()))
			{
				System.err.println(ecKey + " " + mdcDivision.asString() + " <> " + testDivision);
				errorCount++; errorKeys += ecKey + " ";
			}
			if (!testEmail.toUpperCase().equals(email.asString().toUpperCase()))
			{
				System.err.println(email.asString() + " <> " + testEmail);
				errorCount++; errorKeys += ecKey + " ";
			}
			if (phone != null && !isMatchingPhoneNumbers(testPhone, phone.asString()))
			{
				System.err.println(phone.asString() + " <> " + testPhone);
				errorCount++; errorKeys += ecKey + " ";
			}
			if (phone != null && phone.asString().isEmpty())
			{
				System.err.println("Empty Phone from a provider property for user " + testUser.at("ecKey").asString());
				errorCount++; errorKeys += ecKey + " ";
			}
			if (email.asString().isEmpty())
			{
				System.err.println("No email for user " + testUser.at("ecKey").asString());
				errorCount++; errorKeys += ecKey + " ";
			}
		}
		if (errorCount > 0)
			fail("Errors: " + errorCount + " ecKeys: " + errorKeys);
	}


	@Test
	public void testGetFullName()
	{
		String errorKeys = "";
		int errorCount = 0;
		for (Json testUser : data)
		{
			String ecKey = testUser.at("ecKey").asString();
			String testDepartment = testUser.at("mdcDepartment").asString();
			String testDivision = testUser.at("mdcDivision").asString();
			String testFullName = testUser.at("FirstName").asString() + " " + testUser.at("LastName").asString();
			String fullName = u.getFullName(testUser.at("ecKey").asString());
			System.out.println("GetFullName Testing for User: " + ecKey + " dep " + testDepartment + " div " + testDivision);

			if (!testFullName.equalsIgnoreCase(fullName))
			{
				errorCount++;
				errorKeys += testUser.at("ecKey").asString() + ", ";
				System.err.println("Error: " + fullName + " " + testUser.at("ecKey").asString());
			}
		}
		if (errorCount > 0)
			fail("Errors: " + errorCount + " ecKeys: " + errorKeys);
	}

}

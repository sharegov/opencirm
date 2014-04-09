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
import org.sharegov.cirm.legacy.ServiceRequestResolver;
import org.sharegov.cirm.owl.CachedReasoner;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.JsonUtil;
//import org.sharegov.cirmx.maintenance.AllowAnySSL;

import com.itextpdf.text.log.SysoLogger;

/**
 * 
 *	"$$SR_CREATED_BY_EMAIL$$"; // Email address of SR Creator, SQL refers to ST_PREFERENCES!
 *	"$$SR_CREATED_BY_ELECTR_ADDR$$"; // Email address of SR Creator 
 *	"$$SR_CREATED_BY_NAME$$"; // First Last Name of SR creator (UserService lookup)
 *	"$$SR_CREATED_BY_PHONE$$"; // Phone nr of Created By user
 * 
 * @author Thomas Hilpold
 * 
 */
public class T002_SRResolverTest extends T000_UserServiceTest
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
		URL f1 = T002_SRResolverTest.class.getResource(MD_FILE);
		URL f2 = T002_SRResolverTest.class.getResource(COM_FILE);
		URL f3 = T002_SRResolverTest.class.getResource(SR_FILE);
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
	public void testAllVars()
	{
		ServiceRequestResolver srr =  new ServiceRequestResolver(); 
		for (Json testUser : data)
		{
			String ecKey = testUser.at("ecKey").asString();
			String testDepartment = testUser.at("mdcDepartment").asString();
			String testDivision = testUser.at("mdcDivision").asString();
			String testEmail = testUser.at("email").asString();
			String testPhone = testUser.at("phone").asString();
			String testFullName = testUser.at("FirstName").asString() + " " + testUser.at("LastName").asString();

			//Set the user as createdBy
			sr.set("isCreatedBy", ecKey);
			System.out.println("ServiceRequestResolver Testing for User: " + ecKey + " dep " + testDepartment + " div " + testDivision);
			String var1EMAIL = srr.resolve(ServiceRequestResolver.VAR3_SR_CREATED_BY_EMAIL, sr, null);
			String var2EMAIL = srr.resolve(ServiceRequestResolver.VAR4_SR_CREATED_BY_ELECTR_ADDR, sr, null);
			String var3NAME = srr.resolve(ServiceRequestResolver.VAR5_SR_CREATED_BY_NAME, sr, null);
			String var4PHONE = srr.resolve(ServiceRequestResolver.VAR6_SR_CREATED_BY_PHONE, sr, null);
			assertTrue("VAR3_SR_CREATED_BY_EMAIL failed for " + ecKey, testEmail.equalsIgnoreCase(var1EMAIL));
			assertTrue("VAR4_SR_CREATED_BY_ELECTR_ADDR code failed for ", testEmail.equalsIgnoreCase(var2EMAIL));
			assertTrue("VAR5_SR_CREATED_BY_NAME code failed for " + ecKey, testFullName.equalsIgnoreCase(var3NAME));
			assertTrue("VAR6_SR_CREATED_BY_PHONE code failed for " + ecKey, isMatchingPhoneNumbers(var4PHONE, testPhone));
			System.out.println("Pass.");
		}
	}

}

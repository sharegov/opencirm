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


import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.sharegov.cirm.owl.CachedReasoner;

public class T000_UserServiceTest
{
	protected static UserService u;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		CachedReasoner.DBG_CACHE_MISS = false;
		// new AllowAnySSL().installPermissiveTrustmanager();
		// Load both files into Json List of such objects.
		System.out.println("User service test starting Userservice.");
		if (u == null) u = new UserService();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception
	{
		//u = null;
	}

	boolean isMatchingPhoneNumbers(String a, String b)
	{
		if (a == null) a = "";
		if (b == null) b = "";
		if (a.length() > 1 && a.equals(b))
			return true;
		String digitA = "";
		String digitB = "";
		for (int i = 0; i < a.length(); i++)
		{
			if (Character.isDigit(a.charAt(i)))
				digitA += a.charAt(i);
		}
		for (int i = 0; i < b.length(); i++)
		{
			if (Character.isDigit(b.charAt(i)))
				digitB += b.charAt(i);
		}
		return digitA.equals(digitB);
	}
}

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
package org.sharegov.cirm.legacy;

import java.util.Properties;
import mjson.Json;
import org.sharegov.cirm.rest.UserService;
import org.sharegov.cirm.utils.SRJsonActivityUtil;

public class StaffEmailResolver implements VariableResolver
{
	public static String VAR_CREATED_BY = "$$SR_CREATED_BY_EMAIL$$";
	public static String VAR_ASSIGNED_STAFF = "$$SR_ASSIGNED_STAFF_EMAIL$$";
	
	
	@Override
	public String resolve(String variableName, Json sr, Properties properties)
	{
		String result;
		if(VAR_ASSIGNED_STAFF.equals(variableName))
			result = resolveAssignedStaffEmail(sr, properties);
		else if(VAR_CREATED_BY.equals(variableName))
			result = resolveEmail(sr.at("isCreatedBy").asString());
		else 
			result = null;
		return result;
	}
	
	private String resolveAssignedStaffEmail(Json sr, Properties properties)
	{
		String result = null;
		String legacyCode = properties.getProperty("LEGACY_CODE");
		if(legacyCode != null)
		{
			Json activity = SRJsonActivityUtil.getMostRecentActivityByLegacyCode(sr, legacyCode);
			if (activity != null)
			{
				Json isAssignedTo = activity.at("isAssignedTo"); 
				if(isAssignedTo != null)
				{
					result = resolveEmail(isAssignedTo.asString());
				}
				return result;
			} 
			else 
			{
				System.err.println("StaffEmailResolver.resolveAssignedStaffEmailProblem: Problem with ServiceActivity JSON:");
				System.err.println("Activity JSON was: " + activity);
				System.err.println("SR JSON was: " + sr);
				System.err.println("Legacy code: " + legacyCode);
			}
		}
		else
		{
			System.err.println("StaffEmailResolver: Some Legacy code missing for activity in SR: " + sr);
		}
		return result;
	}
	
	private String resolveEmail(String id)
	{
		String result = null;
		if(id == null)
		{
			return result;
		}
		else if(id.contains("@"))
		{
			result = id;
			return result;
		}
		else if(id.startsWith("e"))
		{
			UserService userService = new UserService();
			Json userData = userService.getUserJson("bluebook", id.substring(1).trim());
			result = userData.at("WK_email").asString();
			return result;
		}
		else if(id.startsWith("c"))
		{
			UserService userService = new UserService();
			Json userData = userService.getUserJson("onto", id.trim());
			result = userData.at("hasEmailAddress").asString();
			return result;
		}
		else
		{
			System.err.println("StaffEmailResolver FAILED resolveEmail for " + id); 
			//new IllegalArgumentException("Could not resolve email address for staff" + id);
			return null;
		}
	}
	
	
	public static void main(String[] args)
	{
		StaffEmailResolver resolver = new StaffEmailResolver();
		System.out.println(resolver.resolveEmail("e160616"));
	}
}

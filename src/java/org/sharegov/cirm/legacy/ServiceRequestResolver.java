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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;

import org.semanticweb.owlapi.model.OWLLiteral;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.rest.UserService;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import mjson.Json;

/**
 * Resolves MessageVariables that are directly ServiceRequest related and cannot be resolved by a simple XPath expression.
 * 
 * @author Thomas Hilpold
 */
public class ServiceRequestResolver implements VariableResolver
{

	public static boolean DBG = true;
	public static final String VAR1_SR_CREATED_DATE = "$$SR_CREATED_DATE$$"; // 'Mon DD, YYYY'
	public static final String VAR2_SR_CREATED_DATE1 = "$$SR_CREATED_DATE1$$"; //  'MON-DD-YYYY HH:MI AM '
	public static final String VAR3_SR_CREATED_BY_EMAIL = "$$SR_CREATED_BY_EMAIL$$"; // Email address of SR Creator, SQL refers to ST_PREFERENCES!
	public static final String VAR4_SR_CREATED_BY_ELECTR_ADDR = "$$SR_CREATED_BY_ELECTR_ADDR$$"; // Email address of SR Creator 
	public static final String VAR5_SR_CREATED_BY_NAME = "$$SR_CREATED_BY_NAME$$"; // First Last Name of SR creator (UserService lookup)
	public static final String VAR6_SR_CREATED_BY_PHONE = "$$SR_CREATED_BY_PHONE$$"; // Phone nr of Created By user
	public static final String VAR7_SR_LOCATION_DETAILS = "$$SR_LOCATION_DETAILS$$"; //FULLADDDRESS + Unit + CITY + STATE_CODE + ZIP
	public static final String VAR8_SR_LOCATION_STRING = "$$SR_LOCATION_STRING$$"; //FULL_ADDRESS + UNIT + FLOOR + BUILDING NAME + CITY + STATE_CODE + ZIP
	public static final String VAR9_SR_DUE_DATE = "$$SR_DUE_DATE$$"; //'Mon DD, YYYY' null if no duration days configured
	public static final String VAR10_SR_REMAINING_DAYS = "$$SR_REMAINING_DAYS$$"; //rounded integer now - created, null if no duration days configured
	public static final String VAR11_SR_DURATION_CALENDAR_DAYS = "$$SR_DURATION_CALENDAR_DAYS$$"; //rounded integer due - created, null if no duration days configured
	
	public static final String[] DATE_FORMAT_PATTERNS = new String[]
			{ 	"MMM d, yyyy", //'Mon DD, YYYY'
				"MM-dd-yyyy h:mm aa"  //'MON-DD-YYYY HH:MI AM '
			};	

	@Override
	public String resolve(String variableName, Json sr, Properties properties)
	{
		String result;
		if(variableName.equals(VAR1_SR_CREATED_DATE))
			result = getSRCreatedDateStr(sr, 0);
		else if (variableName.equals(VAR2_SR_CREATED_DATE1))
			result = getSRCreatedDateStr(sr, 1);
		else if (variableName.equals(VAR3_SR_CREATED_BY_EMAIL) || variableName.equals(VAR4_SR_CREATED_BY_ELECTR_ADDR))
			result = getSRCreatedByEmail(sr);
		else if (variableName.equals(VAR5_SR_CREATED_BY_NAME))
			result = getSRCreatedByName(sr);
		else if (variableName.equals(VAR6_SR_CREATED_BY_PHONE))
			result = getSRCreatedByPhone(sr);
		else if (variableName.equals(VAR7_SR_LOCATION_DETAILS))
			result = getSRLocationDetails(sr);
		else if (variableName.equals(VAR8_SR_LOCATION_STRING))
			result = getSRLocationString(sr);
		else if (variableName.equals(VAR9_SR_DUE_DATE))
			result = getSRDueDateStr(sr, 0);
		else if (variableName.equals(VAR10_SR_REMAINING_DAYS))
			result = getSRRemainingDaysStr(sr);
		else if (variableName.equals(VAR11_SR_DURATION_CALENDAR_DAYS))
			result = getSrDurationCalendarDaysStr(sr);
		else
			result = null;
		ThreadLocalStopwatch.getWatch().time("ServiceRequestResolver: Var: " + variableName + " result: " + result);
		return result;
	}

	/**
	 * DurationCalendarDays rounded by back calculating due - created as String.
	 * @param sr
	 * @return
	 */
	private String getSrDurationCalendarDaysStr(Json sr) {
		Integer duration = getSrDurationCalendarDays(sr);
		if (duration == null) return null;
		return "" + duration;
	}
	
	/**
	 * DurationCalendarDays rounded by back calculating due - created.
	 * @param sr
	 * @return
	 */
	private Integer getSrDurationCalendarDays(Json sr) {
		Date due = getSRDueDate(sr);
		if (due == null) return null;
		Date created = getSRCreatedDate(sr);
		if (created == null) return null;
		long durationMs = due.getTime() - created.getTime();
		double durationDays = durationMs / (24.0 * 60 * 60 * 1000); 
		return (int)Math.round(durationDays);
	}

	/**
	 * Returns the rounded remaining days until the SR is due from this morning as String.
	 * @param sr
	 * @return
	 */
	private String getSRRemainingDaysStr(Json sr) {
		Integer remaining = getSRRemainingDays(sr);
		if (remaining == null) return null;
		return "" + remaining;
	}

	/**
	 * Returns the rounded remaining days until the SR is due from this morning as integer value.
	 * @param sr
	 * @return
	 */
	private Integer getSRRemainingDays(Json sr) {
		Date due = getSRDueDate(sr);
		if (due == null) return null;
		//Today 0:00:000
		Calendar c = Calendar.getInstance();
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		Date today = c.getTime();
		long remainingMs = due.getTime() - today.getTime();
		double remainingDays = remainingMs / (24.0 * 60 * 60 * 1000);
		return (int)Math.round(remainingDays);
	}

	private String getSRDueDateStr(Json sr, int dateFormatIndex) {
		Date hasDueDate = getSRDueDate(sr); 		
		return hasDueDate != null? new SimpleDateFormat(DATE_FORMAT_PATTERNS[dateFormatIndex]).format(hasDueDate) : null;
	}
	
	private Date getSRDueDate(Json sr) {
		Json hasDueDateJ = sr.at("hasDueDate", Json.object());
		Date hasDueDate = null; 
		try {
			hasDueDate = GenUtils.parseDate(hasDueDateJ.asString());
		} catch(Exception e) { ThreadLocalStopwatch.getWatch().time("ServiceRequestResolver: getSRDueDate failed to parse date: " + hasDueDateJ.toString());
		}
		return hasDueDate;
	}

	private String getSRCreatedDateStr(Json sr, int dateFormatIndex)
	{
		Date hasDateCreated = getSRCreatedDate(sr); 		
		return hasDateCreated != null? new SimpleDateFormat(DATE_FORMAT_PATTERNS[dateFormatIndex]).format(hasDateCreated) : null;
	}

	private Date getSRCreatedDate(Json sr) {
		Json hasDateCreatedJ = sr.at("hasDateCreated", Json.object());
		Date hasDateCreated = null; 
		try {
			hasDateCreated = GenUtils.parseDate(hasDateCreatedJ.asString());
		} catch(Exception e) { ThreadLocalStopwatch.getWatch().time("ServiceRequestResolver: getSRCreatedDate failed to parse date: " + hasDateCreatedJ.toString());
		}
		return hasDateCreated;
	}
	
	private String getSRCreatedByEmail(Json sr)
	{
		String result = null;
		Json createdByENumJ = sr.at("isCreatedBy", Json.object());
		if (createdByENumJ.isString()) 
		{
			try {
				String createdByENumStr = createdByENumJ.asString();
				if (!createdByENumStr.isEmpty()) 
				{
					UserService userSvc = new UserService();
					if (createdByENumStr.substring(0, 1).equalsIgnoreCase("c"))
					{
						Json user = userSvc.getUserJson("onto", createdByENumStr);
						if (!user.isNull() && user.has("email"));
						{
							result = user.at("email").asString();
						}
					} 
					else
					{
						Json employee = new UserService().getUserJson("bluebook", createdByENumStr);
						if (!employee.isNull()) 
						{
							result = employee.at("WK_email", "").asString();
						} 
						else 
						{
							employee = new UserService().getUserJson("intranet", createdByENumStr);
							if (!employee.isNull()) 
							{
								result = employee.at("mail", "").asString();
							} 
							else 
							{
								System.err.println("Could not find email address in bluebook or intranet for user: " + createdByENumStr);
							}
						}
					} 
				}
			}
			catch(Exception e)
			{
				ThreadLocalStopwatch.getWatch().time("ServiceRequestResolver: Error resolving getSRCreatedByEmail: " + createdByENumJ.asString());
				e.printStackTrace();
				ThreadLocalStopwatch.getWatch().time("ServiceRequestResolver: Error resolving getSRCreatedByEmail: " + createdByENumJ.asString() + " resuming anyways.");
			}
		}
		return result;
	}

	private String getSRCreatedByName(Json sr)
	{
		String result = null;
		Json createdByENumJ = sr.at("isCreatedBy", Json.object());
		if (createdByENumJ.isString()) 
		{
			String createdByENumStr = createdByENumJ.asString();
			try {
				if (!createdByENumStr.isEmpty()) 
				{
					UserService userSvc = new UserService();
					if (createdByENumStr.substring(0, 1).equalsIgnoreCase("c"))
					{
						Json user = userSvc.getUserJson("onto", createdByENumStr);
						if (user != null);
						{
							result = "" + user.at("FirstName").asString() + " " + user.at("LastName").asString();
						}
					} 
					//If not in onto try cKey and eKey in LDAP 
					if (result == null)
					{
						Json employee = new UserService().getUserJson("bluebook", createdByENumJ.asString());
						if (!employee.isNull())
							result = employee.at("Fname", "").asString() + " " + employee.at("Lname", "").asString();
					}
				}
			} 
			catch(Exception e)
			{
				ThreadLocalStopwatch.getWatch().time("ServiceRequestResolver: Error resolving getSRCreatedByName : " + createdByENumJ.asString());
				e.printStackTrace();
				ThreadLocalStopwatch.getWatch().time("ServiceRequestResolver: Error resolving getSRCreatedByName : " + createdByENumJ.asString() + " resuming anyways.");
			}
		}
		return result;
	}

	/**
	 * @param sr
	 * @return eg. "(305) 375-4802"
	 */
	private String getSRCreatedByPhone(Json sr)
	{
		String result = null;
		Json createdByENumJ = sr.at("isCreatedBy", Json.object());
		if (createdByENumJ.isString()) 
		{
			String createdByENumStr = createdByENumJ.asString();
			try {
				if (!createdByENumStr.isEmpty()) 
				{
					UserService userSvc = new UserService();
					if (createdByENumStr.substring(0, 1).equalsIgnoreCase("c"))
					{
						Json user = userSvc.getUserJson("onto", createdByENumStr);
						if (!user.isNull() && user.has("PhoneNumber"));
						{
							result = "" + user.at("PhoneNumber").asString();
						}
					} 
					else
					{
						Json employee = new UserService().getUserById(createdByENumJ.asString());
						if (employee.is("ok", true)) 
						{
							result =  employee.at("profile").at("intranet", Json.object()).at("telephoneNumber", "").asString();
							if (result.isEmpty() && employee.at("profile").has("bluebook")) 
							{
								result =  employee.at("profile").at("bluebook", Json.object()).at("WK_Phone", "").asString();
							}
						}
					}
				}					
			}
			catch(Exception e)
			{
				ThreadLocalStopwatch.getWatch().time("ServiceRequestResolver: Error resolving getSRCreatedByPhone : " + createdByENumJ.asString());
				e.printStackTrace();
				ThreadLocalStopwatch.getWatch().time("ServiceRequestResolver: Error resolving getSRCreatedByPhone : " + createdByENumJ.asString() + " resuming anyways.");
				result = null;
			}
		}
		return result;
	}

	//FULLADDDRESS + Unit + CITY + STATE_CODE + ZIP
	private String getSRLocationDetails(Json sr)
	{
		try {
			String fullAddress = sr.at("atAddress", Json.object()).at("fullAddress", "").asString();
			if (fullAddress.isEmpty()) return null;
			// Acquire data
			StringBuffer result = new StringBuffer(200);
			String unit = sr.at("atAddress", Json.object()).at("Street_Unit_Number", "").asString();
			Json cityJson = sr.at("atAddress", Json.object()).at("Street_Address_City", "");
			String cityIRIStr = cityJson.isObject()? cityJson.at("iri", "").asString() : cityJson.asString();
			//no: String cityIRIStr = sr.at("atAddress", Json.object()).at("Street_Address_City", Json.object()).at("iri", "").asString();
			String cityStr = null;
			if (!cityIRIStr.isEmpty())
			{
				Iterator<OWLLiteral> litIt = OWL.dataProperties(OWL.individual(cityIRIStr), "mdc:Name").iterator();
				if (litIt.hasNext())
					cityStr = litIt.next().getLiteral();
			}
			Json stateJson = sr.at("atAddress", Json.object()).at("Street_Address_State", "");
			String stateIRIStr = stateJson.isObject()? stateJson.at("iri", "").asString() : stateJson.asString();
			String stateAbbreviationStr = null;
			if (!stateIRIStr.isEmpty())
			{
				Iterator<OWLLiteral> litIt = OWL.dataProperties(OWL.individual(stateIRIStr), "mdc:USPS_Abbreviation").iterator();
				if (litIt.hasNext())
					stateAbbreviationStr = litIt.next().getLiteral();
			}
			String zipStr = sr.at("atAddress", Json.object()).at("Zip_Code", "").asString();
			// Construct formatted string
			result.append(fullAddress);
			if (!unit.isEmpty()) {
				result.append(" unit ");
				result.append(unit);
			}
			result.append(" ");
			result.append(cityStr);
			result.append(" ");
			result.append(stateAbbreviationStr);
			result.append(" ");
			result.append(zipStr);
			return result.toString();
		} 
		catch (Exception e)
		{
			ThreadLocalStopwatch.getWatch().time("ServiceRequestResolver: Error resolving getSRLocationDetails : boid " + sr.at("boid", ""));
			e.printStackTrace();
			ThreadLocalStopwatch.getWatch().time("ServiceRequestResolver: Error resolving getSRLocationDetails : boid " + sr.at("boid", "") + " resuming anyways.");
			return null;
		}
	}

	private String getSRLocationString(Json sr)
	{
		return getSRLocationDetails(sr);
	}
}

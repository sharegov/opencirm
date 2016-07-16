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
package org.sharegov.cirm.utils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import mjson.Json;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.rest.UserService;

/**
 * Utility methods for accessing activities in Json format.
 * 
 * Some Methods access UserService (LDAP!) and or the Cirm OWL environment.
 * 
 * @author Thomas Hilpold
 */
public class SRJsonActivityUtil
{

	/**
	 * Returns a list of Activities.
	 * 
	 * @param sr
	 * @return a list of activity json
	 */
	public static List<Json> getActivities(Json sr)
	{
		List<Json> result = null; 
		if(sr.has("hasServiceActivity"))
		{
			result = (sr.at("hasServiceActivity").isArray()) ? sr.at("hasServiceActivity").asJsonList() : Collections.singletonList(sr.at("hasServiceActivity"));
		}
		return result;
	}

	/**
	 * Returns the most recent activity (highest datetime).
	 * Most recent is defined as (Max(created datetime, updated datetime)).
	 * 
	 * @param sr an SR Json (no prefixes)
	 * @return the max activity by date, null if the sr has no activities.
	 * @throws NullPointerException if one activity of given legacycode is found that has neither created nor updated date.
	 */
	public static Json getMostRecentActivity(Json sr)
	{
		Json result = null; 
		if(sr.has("hasServiceActivity"))
		{
			List<Json> activities = (sr.at("hasServiceActivity").isArray()) ? sr.at("hasServiceActivity").asJsonList() : Collections.singletonList(sr.at("hasServiceActivity"));
			result = getMostRecentActivity(activities);
		}
		return result;
	}

	/**
	 * Returns the most recent activity among those matching legacyCode (highest datetime).
	 * Most recent is defined as (Max(created datetime, updated datetime)).
	 * If more than one activities have equal Max(created, updated), the one with the highest
	 * index in the Json activity list is returned.
	 * 
	 * @param sr an SR Json (no prefixes)
	 * @param legacyCode
	 * @return the max activity by date, null if the sr has no activities.
	 * @throws NullPointerException if one activity of given legacycode is found that has neither created nor updated date.
	 */
	public static Json getMostRecentActivityByLegacyCode(Json sr, String legacyCode)
	{
		Json result = null; 
		if(legacyCode != null && sr.has("hasServiceActivity"))
		{
			List<Json> activities = (sr.at("hasServiceActivity").isArray()) ? sr.at("hasServiceActivity").asJsonList() : Collections.singletonList(sr.at("hasServiceActivity"));
			//Look into dates of act with matching legacyCode
			List<Json> activitiesOneLegacyCode = new LinkedList<Json>();
			for (Json activity : activities)
			{
				String activityIRI;
				if (activity.at("hasActivity").isString())
					activityIRI = activity.at("hasActivity").asString();
				else
					activityIRI = activity.at("hasActivity").at("iri").asString();
				OWLLiteral currentLegacyCode = OWL.dataProperty(OWL.individual(IRI.create(activityIRI)), "legacy:hasLegacyCode");
				if(currentLegacyCode != null  && currentLegacyCode.getLiteral().equals(legacyCode))
					activitiesOneLegacyCode.add(activity);
			}
			result = getMostRecentActivity(activitiesOneLegacyCode);
		}
		return result;
	}

	/**
	 * Returns the most recent activity (highest dateTime).
	 * Most recent is defined as (Max(created datetime, updated datetime)).
	 * If more than one activities have equal Max(created, updated), the one with the highest
	 * index in the Json activity list is returned.
	 * @param activities a list of activity Json (no prefixes)
	 * @return an activity Json
	 * @throws NullPointerException if activities is null
	 */
	public static Json getMostRecentActivity(List<Json> activities)
	{
		if(activities.isEmpty()) 
			return null;
		else 
			return sortActivitiesByDateTime(activities).get(activities.size() - 1);
	}

	/**
	 * Sorts activities by Max(created datetime, updated datetime) ascending
	 * 
	 * @param activities a list of activity Jsons (no prefixes)
	 * @return a new List containing all activities by Max(created, updated) in ascending order.
	 * @throws NullPointerException if an activity's max date could not be determined
	 */
	public static List<Json> sortActivitiesByDateTime(List<Json> activities) 
	{
		if (activities.size() <= 1) return activities;
		List<Json> result;
		//1. determine max dateTime of hasDateCreated and hasUpdatedDate for each
		TreeMap<Date, List<Json>> dateToActMap = new TreeMap<Date, List<Json>>();
		for (Json act : activities) 
		{
			Date d = getActivityMaxDateTime(act);
			if (!dateToActMap.containsKey(d)) 
			{
				dateToActMap.put(d, new LinkedList<Json>(Collections.singletonList(act))); //will throw NPE if date null
			}
			else
			{
				dateToActMap.get(d).add(act); 
			}
		}
		result = new ArrayList<Json>(activities.size());
		for (List<Json> l : dateToActMap.values())
		{
			result.addAll(l);
		}
		return result;
	}
	
	/**
	 * Determines Max(hasDateCreated, hasUpdatedDate) for an activity. 
	 * 
	 * @param activity a Json representation of an activity (no prefixes)
	 * @return the most recent activity's max(updated, created) date or null, if no date could be parsed.
	 * @throws NullPointerException is activity is null
	 * @throws IllegalStateException if activity has neither parseable created nor updated date. 
	 */
	public static Date getActivityMaxDateTime(Json activity)
	{
		Date created = null;
		Date updated = null;
		Date max = null;
		String createdStr = activity.at("hasDateCreated", "").asString();
		String updatedStr = activity.at("hasUpdatedDate", "").asString();
		try 
		{
			if (!createdStr.isEmpty())
			{
				created = GenUtils.parseDate(createdStr);
			}
		} catch (Exception e)
		{
			System.err.println("could not parse date from hasDateCreated " + createdStr + " in activity: " + activity );
		}
		try 
		{
			if (!updatedStr.isEmpty())
			{
				updated = GenUtils.parseDate(updatedStr);
			}
		} catch (Exception e)
		{
			System.err.println("could not parse date from hasUpdatedDate " + createdStr + " in activity: " + activity );
		}
		if (created == null && updated == null) 
		{
			throw new IllegalStateException("Encountered activity with neither a parsable created nor updated date: " + activity.toString());
		}
		if (created == null) max = updated;
		if (updated == null) max = created;
		if (max == null)
		{
			max = created.after(updated)? created : updated;
		}
		return max;
	}
	
	/**
	 * Returns the type label of the activity's type.
	 * If no label property is contained in the activity Json due to earlier 
	 * serialization, the label is retrieved from the ontology.
	 * @param activity a Json representation of an activity (no prefixes)
	 * @return 
	 */
	public static String getActivityTypeLabel(Json activity)
	{
		String result;
		if (activity.at("hasActivity", Json.object()).isObject()) 
		{
			Json label = activity.at("hasActivity", Json.object()).at("label", "");
			result = (label.isString())? label.asString() : null;
		}
		else
		{   //Serialized earlier, holds iri
			String activityIri = activity.at("hasActivity").asString();
			result = OWL.getEntityLabel(OWL.individual(activityIri));
		}
		return result;
	}
	
	/**
	 * Returns the label of the outcome.
	 * 
	 * @param activity a Json representation of an activity (no prefixes)
	 * @return the label or an empty string, if no hasOutcome or hasOutcome has no label.
	 */
	public static String getHasOutcomeLabel(Json activity)
	{
		String result;
		//TODO need to find first serialized outcome? 
		//still true if two or more activities of same type are in SR and
		//IRIs were not fully resolved before calling this method.
		Json outcome = activity.at("hasOutcome", Json.object());
		if (outcome.isObject()) {
			result = outcome.at("label", "").asString();
		} else if (outcome.isString()) {
			String outcomeStr = outcome.asString();
			if (outcomeStr.contains("#")) { 
				result = outcomeStr.split("#")[1];
			} else {
				result = outcomeStr;
			}
		} else {
			throw new RuntimeException("Misconfiguration of outcome detected in Activity: " + activity);
		}
		return result; 
	}

	/**
	 * Returns the details of an activity.
	 * @param activity a Json representation of an activity (no prefixes)
	 * @return
	 */
	public static String getHasDetails(Json activity)
	{
		return activity.at("hasDetails", "").asString();
	}

	/**
	 * Returns the due date of the activity as formatted string.
	 *  
	 * @param activity a Json representation of an activity (no prefixes)
	 * @return null, if the activity has no due date String.
	 */
	public static String getHasDueDate(Json activity, String dateFormatPattern)
	{
		Json dueDateJ = activity.at("hasDueDate", Json.nil());
		if (dueDateJ.isNull()) return null;
		Date dueDate = null; 
		try {
			dueDate = GenUtils.parseDate(dueDateJ.asString());
		} catch(Exception e) { System.err.println("ActivityResolver failed to parse date: " + dueDateJ.toString());
		}
		return dueDate != null? new SimpleDateFormat(dateFormatPattern).format(dueDate) : null;
	}

	/**
	 * Returns the created date of the activity.
	 * @param activity a Json representation of an activity (no prefixes)
	 * @return a DATE_PATTERN formatted date or null
	 */
	public static String getHasDateCreated(Json activity, String dateFormatPattern)
	{
		Json hasDateCreatedJ = activity.at("hasDateCreated", Json.nil());
		if (hasDateCreatedJ.isNull()) return null;
		Date hasDateCreated = null; 
		try {
			hasDateCreated = GenUtils.parseDate(hasDateCreatedJ.asString());
		} catch(Exception e) { System.err.println("ActivityResolver:getActivityDateTime failed to parse date: " + hasDateCreatedJ.toString());
		}
		return hasDateCreated != null? new SimpleDateFormat(dateFormatPattern).format(hasDateCreated) : null;
	}

	/**
	 * Returns the employee name of the creator of the activity.
	 * UserService is used.
	 * 
	 * @param activity a Json representation of an activity (no prefixes)
	 * @return the employee name or null, if isCreatedBy is missing or isCreatedBy is not a string.
	 */
	public static String getIsCreatedByName(Json activity)
	{
		Json createdByENumJ = activity.at("isCreatedBy", Json.nil());
		if (createdByENumJ.isNull()) return null;
		if (createdByENumJ.isString()) 
			return new UserService().getFullName(createdByENumJ.asString());
		else 
			return null;
	}

	/**
	 * Calculates a due date 90 days after hasDateCreated.
	 * 
	 * @param activity a Json representation of an activity (no prefixes)
	 * @return
	 */
	public static String getDueDate90Days(Json activity, String datePattern)
	{
		Json dateCreatedJ = activity.at("hasDateCreated", Json.nil());
		if (dateCreatedJ.isNull()) return null;
		Date dueDate = null; 
		try {
			Date dateCreated = GenUtils.parseDate(dateCreatedJ.asString());
			//add 90 days / See orig SQL
			dueDate = new Date(dateCreated.getTime() + 90L * 24 * 60 * 60 * 1000);
		} catch(Exception e) { System.err.println("ActivityResolver:getCustomDUEDATE_SWR failed to parse date: " + dateCreatedJ);
		}
		return dueDate != null? new SimpleDateFormat(datePattern).format(dueDate) : null;
	}
	
	/**
	 * Returns the name of the assigned staff of the activity by resolving an eKey or cKey.
	 * If the assigned string is not an eKey or cKey, the string itself is returned (e.g. an email address).
	 * For e or c keys, UserService is used. 
	 * @param activity a Json representation of an activity (no prefixes)
	 * @return  
	 */
	public static String getAssignedStaffName(Json activity)
	{
		Json createdByENumJ = activity.at("isAssignedTo", Json.object());
		if (createdByENumJ.isString())
		{
			if  (isUserEorCKey(createdByENumJ.asString())) 
				return new UserService().getFullName(createdByENumJ.asString());
			else // Email address? 
				return createdByENumJ.asString();
		}
		else 
			return null;
	}
	
	/**
	 * Determines if the string is a c or e-key.
	 * 
	 * @param s any string or null
	 * @return false, if s null, s empty, or s not an e or c key.
	 */
	public static boolean isUserEorCKey(String s)	
	{
		return (s != null && !s.isEmpty() && 
				(s.toUpperCase().startsWith("E") || s.toUpperCase().startsWith("C")) 
				&& Character.isDigit(s.charAt(s.length()-1)));
	}

}

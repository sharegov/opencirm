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
package org.sharegov.cirm.user;

import java.util.Map;
import mjson.Json;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.rest.OWLIndividuals;
import org.sharegov.cirm.utils.JsonUtil;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

/**
 * OntoUserProvider retrieves mdc:User individuals from the ontologies. <br>
 * Users are managed in mdc: ontology
 * <br>
 * A mdc:User is modelled as follows:
 * OWLClass: mdc:User or Subclass (==DEFAULT_IRI_BASE)
 * <br>
 * User IRI: mdc:cKey/eKey OR mdc:emailAddress (In cases where no cKey is available, in such case the User is also an mdc:EmailAddress) <br>
 * (The User IRI fragment will be user in the assignedTo field of activities; either cKey/eKey or Email)<br>
 * User Email: mdc:hasEmailAddress mdc:EmailAddress individual mandatory <br>
 * (If no cKey/eKey, User individual A has two Classes: mdc:User and mdc:EmailAddress, the mdc:hasEmailAddress property refers to A) <br>
 * User FirstName: mdc:FirstName (xsd:String) mandatory <br>
 * User LastName: mdc:LastName (xsd:String) mandatory <br>
 * User MiddleName: mdc:MiddleName (xsd:String) OPTIONAL <br>
 * User PhoneNumber: mdc:PhoneNumber (xsd:String) OPTIONAL <br>
 * <br>
 * Find on String attributes is never case sensitive.
 * <br>
 * This object contains an internal cache of users. Do not instantiate this object for each request, but use a Ref instead.<br>
 * If User ontology users change, create one new OntoUserProvider.
 *  
 * @author Thomas Hilpold
 */
public class OntoUserProvider implements UserProvider
{
	/**
	 * This WildCard is allowed only at the end of an attibute value.
	 */
	public static final String SEARCH_STARTS_WITH_WILDCARD = "%"; 

	public static final String ID_ATTRIBUTE = "iri"; 
	public static final OWLDataProperty USER_FIRSTNAME_DP = OWL.dataProperty(OWL.fullIri("mdc:FirstName")); 
	public static final OWLDataProperty USER_LASTNAME_DP = OWL.dataProperty(OWL.fullIri("mdc:LastName")); 
	public static final OWLDataProperty USER_PHONE_NUMBER_DP_OPT= OWL.dataProperty(OWL.fullIri("mdc:PhoneNumber")); 
	public static final OWLDataProperty USER_MIDDLENAME_DP_OPT = OWL.dataProperty(OWL.fullIri("mdc:MiddleName")); 
	public static final OWLObjectProperty USER_HAS_EMAIL_ADDRESS_OP = OWL.objectProperty(OWL.fullIri("mdc:hasEmailAddress")); 
	
	private volatile Json users = null;
	
	public OntoUserProvider() 
	{
		ensureUsers();
	}
	
    public boolean authenticate(String username, String password)
    {
        boolean result = false;
        return result;
    }
	
	@Override
	public Json find(String attribute, String value)
	{
		Json prototype = Json.object();
		prototype.set(attribute, value);
		return find(prototype);
	}

	@Override
	public Json find(Json prototype)
	{		
		return find(prototype, Integer.MAX_VALUE);
	}
	
	public Json findGroups(String id)
	{
	    return Json.array();	    
	}

	/**
	 * Finds all users matching the prototype.
	 * Use wildcard as last char of a search value on string attribute values only.
	 * Current implementation scans over user list to find matches.
	 */
	@Override
	public Json find(Json prototype, int resultLimit)
	{		
		Json result = Json.array();
		int matchCount = 0;
		for (Json candidate : users.asJsonList())
		{
			if (match(candidate, prototype)) 
			{
				result.add(candidate);
				matchCount ++;
			}
			if (matchCount >= resultLimit) break;
		}
		return result;
	}

	/**
	 * Determines if all attribute values in search are found in canditate (not case sensitive, wildcard allowed on String val attributes only).
	 * @param candidate
	 * @param search
	 * @return
	 */
	public boolean match(Json candidate, Json search)
	{
		if (search.asJsonMap().isEmpty()) return true;
		for (Map.Entry<String, Json> searchE : search.asJsonMap().entrySet())
		{
			if (!candidate.asJsonMap().containsKey(searchE.getKey()) || 
			    !matchValue(candidate.at(searchE.getKey()), searchE.getValue()))
					return false;
		}
		return true;
	}
	
	public boolean matchValue(Object candidateValue, Object searchValue)
	{
		boolean result;
		if (!(searchValue instanceof String)) 
		{
			//Might be int, double or other search, no wild card
			result = searchValue.equals(candidateValue);
		}
		else 
		{
			String candVal = candidateValue.toString().trim().toUpperCase();
			String searchVal = searchValue.toString().trim().toUpperCase();
			if (searchVal.lastIndexOf(SEARCH_STARTS_WITH_WILDCARD) == searchVal.length() - 1) 
			{	
				//Remove WildCard
				searchVal = searchVal.substring(0, searchVal.length() - 1);
				result = candVal.startsWith(searchVal);
			} 
			else
				result = candVal.equals(searchVal);
		}
		return result;
	}
	/**
	 * @param id null returns empty map
	 * @return the user or null
	 */
	@Override
	public Json get(String id)
	{
		if (id == null) return null;
		ensureUsers();
		for (Json user: users.asJsonList())
		{
			String compareID = user.at("hasUsername").asString();
			if (id.toUpperCase().equals(compareID.toUpperCase()))
				return user;
		}			
		return Json.nil();
	}

	/**
	 * Gets a map for a user Json with iris for each known attribute value resolved to fragments.
	 * @param user
	 * @return null, if a user has no email (logged)
	 */
	private Json massageUser(Json user)
	{
		String userId = user.at(ID_ATTRIBUTE).asString();		
		Json userEmailMapOrString = user.at(USER_HAS_EMAIL_ADDRESS_OP.getIRI().getFragment());	
		if (userEmailMapOrString == null) 
		{
			ThreadLocalStopwatch.getWatch().time("OntoUserProvider: Ill configured user: " +
			        userId + " Check ontology and make sure it has all mandatory attributes.");
			return null;
		}
		user.set(ID_ATTRIBUTE, IRI.create(userId).getFragment());
		//Email Address
		String userEmailValue;
		if (userEmailMapOrString.isObject()) 
		{
			String userEmailIri = userEmailMapOrString.at("iri").asString();
			userEmailValue = IRI.create(userEmailIri).getFragment();
		} 
		else
		{
			userEmailValue = userEmailMapOrString.asString();
		}
		user.set(USER_HAS_EMAIL_ADDRESS_OP.getIRI().getFragment(), userEmailValue);
		return user.set("hasUsername", user.at(ID_ATTRIBUTE))
		           .set("email", user.at("hasEmailAddress"));
	}

	@Override
	public String getIdAttribute()
	{
		return ID_ATTRIBUTE;
	}
	
	private void ensureUsers()
	{
		if (users == null)
		{
			synchronized (this)
			{
				if (users == null)
					users = retrieveMappedUsers();
			}
		}
	}
	
	/**
	 * Retrieves all Users from the ontologies 
	 * using reasoner first, then OwlEntityCache
	 * @return
	 */
	private Json retrieveUsers()
	{
		Json result;
		ThreadLocalStopwatch.getWatch().time("OntoUserProvider: retrieveUsers");
		result = JsonUtil.ensureArray(new OWLIndividuals().doQueryService("mdc:User"));
		ThreadLocalStopwatch.getWatch().time("OntoUserProvider: retrieveUsers completed: nrOfUsers: " + 
		                                result.asJsonList().size());
		return result;
	}
	
	/**
	 * Retrieves users as Map<String, Object>, prepared for find operations.
	 * All Object property values and iris of known attributes are resolved to their iri fragments. 
	 * @return
	 */
	private Json retrieveMappedUsers() 
	{
		try 
		{
			Json users = retrieveUsers();
			Json result = Json.array();
			for (Json user: users.asJsonList()) 
			{
				user = massageUser(user);
				if (user != null) 
				    result.add(user);
			}
			return result;
		} 
		catch (Exception e) 
		{
			ThreadLocalStopwatch.getWatch().time("OntoUserProvider: Error during retrieveMappedUsers from ontology. Empty user list will now be used. " + e.toString());
			e.printStackTrace();
			return Json.array();
		}
	}
	
    public Json populate(Json user)
    {
    	if (user.has("userid"))
    	{
    		Json found = get(user.at("userid").asString());
    		if (!found.isNull())
    		{
    			user.set("onto", found);    			
    			JsonUtil.setIfMissing(user, "email", found.at("hasEmailAddress"));    			
    			JsonUtil.setIfMissing(user, "FirstName", found.at("FirstName"));    			
    			JsonUtil.setIfMissing(user, "LastName", found.at("LastName"));
    			JsonUtil.setIfMissing(user, "hasUsername", found.at("hasUsername"));
    		}
    	}
    	return user;
    }
}

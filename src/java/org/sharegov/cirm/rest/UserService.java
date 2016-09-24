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

import static org.sharegov.cirm.OWL.dataFactory;

import static org.sharegov.cirm.OWL.fullIri;
import static org.sharegov.cirm.utils.GenUtils.dbg;
import static org.sharegov.cirm.utils.GenUtils.ko;
import static org.sharegov.cirm.utils.GenUtils.ok;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import mjson.Json;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.sharegov.cirm.AutoConfigurable;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.owl.Model;
import org.sharegov.cirm.owl.OWLObjectPropertyCondition;
import org.sharegov.cirm.user.UserProvider;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

/**
 * 
 * <p>
 * Main entry point for user management - authentication, profile retrieval, access policies.
 * </p>
 * 
 * @author Syed Abbas
 * @author Tom Hilpold
 * @author Borislav Iordanov
 *
 */
@Path("users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserService extends RestService implements AutoConfigurable
{	
	public static final IRI DEFAULT_STOP_EXPANSION_CONDITION_IRI1 = Model.upper("Department"); 
	public static final IRI DEFAULT_STOP_EXPANSION_CONDITION_IRI2 = Model.upper("Divison"); 
	public static final IRI DEFAULT_STOP_EXPANSION_CONDITION_IRI3 = Model.upper("hasDivision"); 
	public static final IRI DEFAULT_STOP_EXPANSION_CONDITION_IRI4 = Model.upper("hasObject"); 
	public static final String CIRM_ADMIN = Model.upper("CirmAdmin").toString(); 

	private final OWLObjectPropertyCondition stopExpansionCondition = getStopExpansionCondition();
	
	private OWLObjectPropertyCondition getStopExpansionCondition() 
	{
		Set<OWLObjectProperty> stopExpansionProps = new HashSet<OWLObjectProperty>();
		stopExpansionProps.add(OWL.objectProperty(DEFAULT_STOP_EXPANSION_CONDITION_IRI1));
		stopExpansionProps.add(OWL.objectProperty(DEFAULT_STOP_EXPANSION_CONDITION_IRI2));
		stopExpansionProps.add(OWL.objectProperty(DEFAULT_STOP_EXPANSION_CONDITION_IRI3));
		stopExpansionProps.add(OWL.objectProperty(DEFAULT_STOP_EXPANSION_CONDITION_IRI4));
		return new OWLObjectPropertyCondition(stopExpansionProps);
	}

	private Json desc = Json.object();
	private static volatile Map<String, UserProvider> providerMap = new HashMap<String, UserProvider>();
		
	private List<String> orderedProviders()
	{
	    ArrayList<String> L = new ArrayList<String>(desc.at("hasUserBase", Json.object()).asJsonMap().keySet());
	    Collections.sort(L, new Comparator<String>() {
	       public int compare(String left, String right)
	       {
	           int x = desc.at("hasUserBase").at(left).at("hasOrdinal", Integer.MAX_VALUE).asInteger();
	           int y = desc.at("hasUserBase").at(right).at("hasOrdinal", Integer.MAX_VALUE).asInteger();
	           return x - y;
	       }
	    });
	    return L;
	}
	
	private String authenticateProvider()
	{
	    return desc.at("authenticatesWith").at("hasName").asString();	          
	}

	private UserProvider provider(String name)
	{
	    synchronized (providerMap)
	    {
	        UserProvider provider = providerMap.get(name);
	        if (provider != null)
	            return provider;
	        if (!desc.at("hasUserBase").has(name))
	            return null;	        
	        String classname = desc.at("hasUserBase").at(name).at("hasImplementation").at("iri").asString().split("#")[1];
	        try 
	        {
	            provider = (UserProvider)Class.forName(classname).newInstance();
	            //Autoconfigure is not part of the object initialisation
	            //without synchronization, variables set during autoconfigure might not be readable by other threads.
	            synchronized(provider)
	            {
		            if (provider instanceof AutoConfigurable)
		            	((AutoConfigurable)provider).autoConfigure(desc.at("hasUserBase").at(name));
	            }
	            providerMap.put(name, provider);
	            return provider;
	        }
	        catch (Exception ex)
	        {
	            throw new RuntimeException(ex);
	        }
	    }
	}
		
	private Json getAccessPolicies(Json groups) 
	{
		if (!groups.isArray()) 
			throw new IllegalArgumentException("Expected Array of cirmusergroups. e.g. legacy:311..");
		Json cirmUserGroupsWithAccessPolicies = Json.array();
		for (Json iri : groups.asJsonList())
		{
			OWLIndividual group = dataFactory().getOWLNamedIndividual(fullIri(iri.asString()));
			//Here we need to make sure that the serialization stops at e.g. 
			//individuals that are the objects of an AccessPolicy!		
			Json groupWithAccessPolicies = OWL.toJSON(group, stopExpansionCondition);
			cirmUserGroupsWithAccessPolicies.add(groupWithAccessPolicies);			
		}
		//Array of cirm groups with all access policy information serialized.
		return cirmUserGroupsWithAccessPolicies; //userdata.set("cirmusergroups", cirmUserGroupsWithAccessPolicies);
	}

	private Json prepareReturn(Json user)
	{
		if (user.isArray())
		{
			for (Json u : user.asJsonList())
				prepareReturn(u);
		}
		else
		{
			user.delAt("hasPassword");
			// TODO: can we get rid of this? the fear that somewhere on the client
			// it is being used, but it shouldn't be.
			if (user.has("hasUsername"))
				user.set("username", user.at("hasUsername"));
		}
		return user;
	}
	
    public void autoConfigure(Json config)
    {
        this.desc = config;        
    }
	
	/**
	 * <p>
	 * This is a general method to retrieve information about a particular user.
	 * Because it's expensive to fill out all information we can get about a user,
	 * the request is a more complex object that specifies what is to be 
	 * provided. In this way, a client can request all that is needed and only
	 * that which is needed in a single network round-trip.
	 * </p>
	 * <p>
	 * The basic profile (first name, email etc.) is returned regardless. Here are the 
	 * expected properties of the JSON <code>request</code> parameter that control
	 * what else is returned:
	 * <ul>
	 * <li>username - mandatory...of course</li>
	 * <li>groups - true/false whether to include the list of groups the user belongs to</li>
	 * <li>access - true/false whether to include the access policies for this user</li>
	 * </ul>
	 * </p>
	 * 
	 * @param request
	 * @return
	 */
	@POST
	@Path("/profile")
	public Json userProfile(Json request)
	{	
		try
		{
			if (!request.isObject() || !request.has("username"))
				return ko("bad request.");
	        if (!request.has("provider") || request.is("provider", ""))
	            request.set("provider", desc.at("authenticatesWith").at("hasName"));			
			UserProvider providerImpl = provider(request.at("provider").asString());
			Json profile = providerImpl.get(request.at("username").asString()); 			        
			if (profile.isNull()) return ko("No profile");
				if (request.is("groups", true) || request.is("access", true))
					profile.set("groups", providerImpl.findGroups(request.at("username").asString()));
				if (request.is("access", true))
					profile.set("access", getAccessPolicies(profile.at("groups")));
				return ok().set("profile", prepareReturn(profile));
		}
		catch (Throwable t)
		{
			if (!"unavailable".equals(t.getMessage())) // error would have already been reported in the logs
				t.printStackTrace(System.err);
			return ko(t.getMessage());
		}
	}
			
	/**
	 * <p>
	 * Authenticate within a given realm (user provider). 
	 * </p>
	 * 
	 * @param form
	 * @return
	 */
	@POST
	@Path("/authenticate")
	public Json authenticate(Json form)
	{		
	    if (!form.has("provider") || form.is("provider", ""))
	        form.set("provider", desc.at("authenticatesWith").at("hasName"));
		if (form.is("provider", authenticateProvider()))
		{
			if (!form.has("password") || form.is("password", ""))
				return ko("Please provide a password.");
			Json userdata = userProfile(form);
			if (userdata.is("error", "No profile"))
				return ko("User not found or invalid password.");
			else if (!userdata.is("ok", true))
				return userdata;
			else if (!StartUp.getConfig().is("ignorePasswords", true))
			{			  
				if (!provider(form.at("provider").asString()).authenticate(
				        userdata.at("profile").at("hasUsername").asString(), 
				        form.at("password").asString()))
					return ko("User not found or invalid password.");
			}
			if (dbg())
			{
				String msg = (userdata.at("profile").has("hasUsername"))? userdata.at("profile").at("hasUsername").asString() : "Unknown";
				msg += " | lastname: "  + (userdata.at("profile").at("lastName", " no lastname")).toString();
				msg += "\r\n | groups: "  + (userdata.at("profile").at("groups", " no groups")).toString() + "\r\n";
				ThreadLocalStopwatch.getWatch().time("Auth success: " + msg);
				ThreadLocalStopwatch.dispose();
			}
			return ok().set("user", prepareReturn(userdata.at("profile")));
		}
		// other realms/providers...
		else
			return ko("Unknown realm");
	}
	
	/**
	 * Consumes an array of group names and augments those groups with the corresponding access policies.
	 * @param groups An array of names of groups.
	 * @return
	 */
	@POST
	@Path("/accesspolicies")
	public Json accessPolicies(Json groups)
	{
		groups = getAccessPolicies(groups);
		if (!groups.asList().isEmpty() && groups.at(0).has("hasAccessPolicy"))
			return ok().set("cirmusergroups", groups);
		else 
			return ko("No Access policies are available for user.");
	}
	
	@GET
	@Path("search")
	public Json search(@QueryParam("id") String id, 
					   @QueryParam("name") String searchString,
					   @QueryParam("providers") String providers)
	{
		if(id != null && !id.isEmpty())
		{
			return Json.array().add(searchUserById(id));
		}
		Json resultList = Json.array();
		final int maxResults = 15;
		try
		{
	        if (searchString == null || searchString.length() == 0)
	            return null;
	        else
	        	searchString = searchString.trim();
	        Json user = Json.object();
	        String name = searchString;
	        name = name.trim();
	        int idx;
	        //Parse search string
	        if ( (idx = name.indexOf(',')) > -1)
	        {   //Miller, Bob            
	        	user.set("LastName", name.substring(0, idx).trim());
	        	user.set("FirstName", name.substring(idx+1).trim());
	        }
	        else if ( (idx = name.indexOf(' ')) > -1)
		    {    //Bob Miller
		         user.set("LastName", name.substring(idx+1).trim());
		         user.set("FirstName", name.substring(0, idx).trim());
		    }
	        else
	        {	//Miller
	        	user.set("LastName", name);
	        }
	        if (user.is("FirstName", ""))
	        	user.delAt("FirstName");
	        if (user.is("LastName", ""))
	        	user.delAt("LastName");
	        if (user.asJsonMap().size() > 0)
	        {
		        Collection<String> P = providers != null ? Arrays.asList(providers.split(",")) : orderedProviders();
		        for (String providerName : P)
	        		resultList.with(searchProvider(providerName, user, maxResults));
	        }
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return ko(e);
		}
		return prepareReturn(resultList);
	}
	
	/**
	 * <p>
	 * Searches a user by ID. If multiple realms are configured, each will be tried  
	 * according to their ordinal number configuration. Only the first found is returned.
	 * </p>
	 */
	public Json searchUserById(String id)
	{
        if (id == null || id.length() == 0)
            return Json.array();
		for (String providerName : orderedProviders())
		{
		    UserProvider P = provider(providerName);
		    Json user = P.get(id);
		    if (!user.isNull())
		        return user;
		}
		return Json.nil();
	}
	
	public Json searchProvider(String name, Json prototype, int maxResults)
	{
		UserProvider provider = provider(name);
		if (provider == null)
		    throw new RuntimeException("Unknown user realm " + name);
		return provider.find(prototype, maxResults);
	}
	

	@GET
	@Path("{provider}/{id}")
	@Produces("application/json")
	public Json getUserJson(@PathParam(value = "provider") String provider, @PathParam(value = "id") String id)
	{
	    UserProvider providerImpl = provider(provider);
	    if (providerImpl == null)
	        return ko("Unknown realm " + provider);
		return prepareReturn(providerImpl.get(id));
	}

	/**
	 * <p>
	 * Retrieve full user information given a user id (a.k.a. username). If
	 * there are multiple user backing stores configured, information from each
	 * will be aggregated. The provider with the highest priority will be used
	 * to provide based information, but then each separate provider is added
	 * as a property.  
	 * </p>
	 * <p>
	 * For example, if you have an LDAP provider called "ldap" and a databse provider
	 * called "db", with the ldap provider being the default (high priority), you
	 * would get something that looks like <code>{ "hasUsername":id, "FirstName":"John",
	 * "ldap":{...all LDAP user attributes }, "db":{ all DB user attributes}}</code>
	 * </p>
	 * @param id
	 * @return
	 */
	@GET
	@Path("{id}")
	@Produces("application/json")
	public Json getUserById(@PathParam("id") String id)
	{		
		Json user = Json.object("userid", id);
		List<String> plist = orderedProviders();
		for (String providerName : plist)
		{
			UserProvider P = provider(providerName);
			P.populate(user);
		}
		return ok().set("profile", prepareReturn(user));
	}
	
    public String getFullName(String userid)
    {
        if(userid == null || userid.isEmpty())
            return "";
        Json user = searchUserById(userid);
        if (user.isNull())
        	return "";
        else
        	return user.at("FirstName", "").asString() + " " + user.at("LastName", "").asString();
    }

    public UserService()
    {
        autoConfigure(Refs.owlJsonCache.resolve().individual(OWL.fullIri("UserService")).resolve());
    }
    
}

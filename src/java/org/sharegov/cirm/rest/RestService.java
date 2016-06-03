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


import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.restlet.data.ClientInfo;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.RequestScopeFilter;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.Ref;
import org.sharegov.cirm.utils.RequestScopeRef;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;


/**
 * <p>
 * Top level class for CiRM REST services. 
 * </p>
 * 
 * <p>
 * This class offers facilities for dealing with currently logged in user and their permissions.
 * </p>
 * 
 * @author Borislav Iordanov, Thomas Hilpold
 *
 */
public class RestService
{
	public @Context
	SecurityContext security = null;
	public @Context
	HttpHeaders httpHeaders = null;
	public @Context
	UriInfo uriInfo = null;
	public @Context
	Request request = null;

	public static boolean DBG = true;
	
	public static volatile RequestScopeRef<Boolean> forceClientExempt = new RequestScopeRef<Boolean>(new Ref<Boolean>()
	{
		public Boolean resolve() { return false; }
	});
	
	/**
	 * Value: one or array of exempt client Hostname individuals
	 */
	public static final String EXEMPT_CONFIG_PARAM_KEY = "CirmExemptClientConfig";
	
	private static volatile Map<String, String> exemptClientIpToHost;
	
	// for some reason this is not working, service becomes inaccessible with
	// Restlet framework
	// if this is enabled...
	// public @Context Response response = null;

	public String getUserId()
	{
		if(httpHeaders == null) 
		{
			ThreadLocalStopwatch.getWatch().time("ERROR: RestService.getUserId() httpHeaders were null. this indicates that this object: " + this + " was called without context. "
					+ "\r\n Remove this after all such problems are found and fixed:");
			GenUtils.logStackTrace(Thread.currentThread().getStackTrace(), 10);
			
			return "";
		}
		Cookie cookie = httpHeaders.getCookies().get("username");
		if (cookie != null && cookie.getValue() != null && cookie.getValue().length() > 0)
			return cookie.getValue();
		else
			return "anonymous";
	}
	
	public String [] getUserGroups()
	{
		if(httpHeaders == null) 
		{
			if (DBG) 
			{
				ThreadLocalStopwatch.getWatch().time("ERROR: RestService.getUserGroups() httpHeaders were null. this indicates that this object: " + this + " was called without context. "
						+ "\r\n Remove this after all such problems are found and fixed:");
				GenUtils.logStackTrace(Thread.currentThread().getStackTrace(), 10);
			}
			return new String[0];
		}
		Cookie cookie = httpHeaders.getCookies().get("usergroups");
		if (cookie != null && cookie.getValue() != null && cookie.getValue().length() > 0)
		{
			// ,$Version=1 observed in tests, must be excluded.
			String cookieVal = cookie.getValue();
			int versionIndex = cookieVal.indexOf(",$"); 
			if (versionIndex > 0) 
				cookieVal = cookieVal.substring(0, versionIndex);
			try {
				return URLDecoder.decode(cookieVal, "UTF-8").split(";");
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		}
		else
			return new String[0];		
	}
	
	public Set<OWLNamedIndividual> getUserActors()
	{
		HashSet<OWLNamedIndividual> S = new HashSet<OWLNamedIndividual>();
		for (String groupName : getUserGroups())
			S.add(OWL.individual(groupName));
		return S;
	}
	
	/**
	 * Get User information consisting of UserId and -Groups.
	 * @return
	 */
	public String getUserInfo() 
	{
		StringBuffer result = new StringBuffer(200);
		result.append("UId: " + getUserId() + " GIds: ");
		for (String g : getUserGroups())
		{
			result.append(g);
			result.append(" ");

		}
		return result.toString();
	}
	
	public static Map<String,String> getClientExemptIpToHostMap() 
	{
		if (exemptClientIpToHost == null) 
			synchronized (RestService.class) 
			{
				if (exemptClientIpToHost == null) 
					initClientExemptCache();
			}
		return exemptClientIpToHost;
	}
	
	private static synchronized void initClientExemptCache() 
	{
		Map<String, String> exemptClientIpToHost = new HashMap<String, String>();
		Set<String> hosts = getClientExemptHostsFromConfiguration();
		for (String hostname : hosts)
		{
			try
			{
				InetAddress[] exemptIps = InetAddress.getAllByName(hostname);
				for (InetAddress exemptIp : exemptIps)
				{
					exemptClientIpToHost.put(exemptIp.getHostAddress(), hostname);
					ThreadLocalStopwatch.getWatch().time("RestService: ClientExemptCache intialized with host " + hostname + "(" + exemptIp.getHostAddress() + ").");
				}
			} catch (UnknownHostException e)
			{
				System.err.println("RestService: initClientExemptCache: Unknownhost Exception: Could not resolve hostname to IPs for " + hostname);
			}
		}
		//publish to volatile for all threads
		RestService.exemptClientIpToHost = Collections.unmodifiableMap(exemptClientIpToHost);
	}

	@SuppressWarnings("rawtypes")
	private static Set<String> getClientExemptHostsFromConfiguration()
	{
		Set<String> result = new HashSet<String>();
		Object values = Refs.configSet.resolve().get(EXEMPT_CONFIG_PARAM_KEY);		
		if (values instanceof Set) {
			for (Object value : ((Set)values)) 
			{
				result.add(((OWLNamedIndividual)value).getIRI().getFragment());
			}
		} else 
		{
			result.add(((OWLNamedIndividual)values).getIRI().getFragment());
		}
		return result;
	}

	public static synchronized void clearClientExemptCache() 
	{
		exemptClientIpToHost = null;
	}

	public boolean isClientExempt()
	{
		if (StartUp.config.is("allClientsExempt", true) || Boolean.TRUE.equals(Refs.configSet.resolve().get("areAllClientsExempt")))
		{
			return true;
		}
		else if (forceClientExempt.resolve())
		{
			return true;
		} 
		else if (isClientCirmAdmin())
		{
			return true;
		}
		else
		{
			String exemptHostName = null;
			ClientInfo clientInfo = (ClientInfo)RequestScopeFilter.get("clientInfo");
			if (clientInfo != null) 
			{
				String clientIp = clientInfo.getAddress();
				exemptHostName = getClientExemptIpToHostMap().get(clientIp);
				if (DBG && exemptHostName != null) {
					ThreadLocalStopwatch.getWatch().time("RestService: Granting exempt client access to " + exemptHostName + " (" + clientIp + ")");
				}
			}
			return exemptHostName != null;
		}
	}
	
	public boolean isClientCirmAdmin() {
		return Arrays.asList(getUserGroups()).contains(UserService.CIRM_ADMIN);
	}
}

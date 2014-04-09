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


import java.util.Properties;
import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.ldap.*;

import org.sharegov.cirm.utils.GenUtils;


/**
 * Thread safe, self recovering Ldap client with limited functionality
 *  
 * @author Thomas Hilpold 
 *
 */
public class LDAPClient
{
	final ThreadLocal<LdapContext> ldapContextsByThread = new ThreadLocal<LdapContext>();
	
	private final Properties params;
	private volatile InitialLdapContext ctx;
	
	private static abstract class Operation {
		
		final LdapContext threadCtx;
		
		Operation(final LdapContext threadCtx)
		{
			this.threadCtx = threadCtx;					
		}
		
		final Object exec() throws NamingException 
		{
			return run();			
		}
		
		abstract Object run() throws NamingException;
		
		LdapContext getCtx()
		{
			return threadCtx;
		}
		@Override
		protected void finalize() throws Throwable
		{		
			super.finalize();
			try {
				if (threadCtx != null) 
					threadCtx.close();
			} catch (Exception e) 
			{
				System.err.println("LDAP ERROR ON INITIAL CONTEXT CLOSE " + e);
			}
		}
	}
	
	public LDAPClient(Properties params)
	{
		if (params == null)	throw new RuntimeException("LDAPClient created with null params.");
		this.params = params;
		init();
	}
	
	private LdapContext getContextForThread() 
	{
		LdapContext threadContext = ldapContextsByThread.get();
		if (threadContext == null)
		{
			try
			{
				synchronized(this) 
				{
					if (ctx == null) init();
					if (ctx == null)  throw new RuntimeException("LDAP down, we'll recover once it's back up.");
					System.out.println("LDAP getting newInstance of context for" + Thread.currentThread());
					threadContext = ctx.newInstance(null);
				}
				ldapContextsByThread.set(threadContext);
			} catch (NamingException e)
			{
				System.err.println("LDAP Failed to create context for thread: " + Thread.currentThread());
				ctx = null;
				GenUtils.rethrowRuntime(e);
			}
		}
		return threadContext;
	}
	
	private synchronized void init() 
	{
		try
		{
			ctx = new InitialLdapContext(params, null);
		} catch (NamingException e)
		{
			System.err.println("LDAP Could not initialize initial context with parameters: " + params);
			e.printStackTrace();
			ctx = null;
		}
	}
	
	private Object exec(Operation op)
	{
		boolean reconnected = false;
		try {
			do 
			{
				try
				{
					return op.exec();
				} 
				catch(CommunicationException e)
				{
					if (reconnected) throw e;
					System.out.println("LDAP reconnecting op ctx " + Thread.currentThread().getName());
					op.getCtx().reconnect(null);
					reconnected = true;
				}
			} while (reconnected);
		}
		catch (NamingException e)
		{
			System.err.println("LDAP ERROR :" + e + " on " + Thread.currentThread());
			//clear threads context
			try {
				op.getCtx().close();
			} catch (Exception e2) {
				System.err.println("LDAP close failed " + e2);
			}
			ldapContextsByThread.remove();
			GenUtils.rethrowRuntime(e);
		}
		return null; // unreachable
	}
			
	/**
	 * <p>
	 * Perform a search in LDAP and return the results. Several parameters control the 
	 * results returned. The search is by default recursive and will take as long as needed
	 * to finish (no timeout). All attributes are returned in the results. To select only
	 * certain attributes to be returned, use one of the overloaded methods.
	 * </p>
	 * 
	 * @param baseName The starting DN.
	 * @param ldapFilter The LDAP entry filter.
	 * @param pageSize The number of entries per returned page. Use 0 for no paging.
	 * @param sortBy The attribute by which returned entries are sorted. 
	 * @return an <code>Enumeration</code> of <code>javax.naming.directory.SearchResult</code> 
	 * instances 
	 */
	public NamingEnumeration<?> search(final String baseName, 
							 	    final String ldapFilter, 
								    final String [] sortBy)
	{
		try
		{ 
			if (baseName == null)
				throw new NullPointerException("LDAPClient.search: null base name.");
			final boolean sorting = (sortBy != null && sortBy.length > 0);
			final Control [] requestControls;
			if (sorting) {
				 requestControls = new Control[] {
				       new SortControl(sortBy, Control.NONCRITICAL)
				 };
			} else requestControls = null;
			final String filter = ldapFilter == null ? "" : ldapFilter;
			return (NamingEnumeration<?>)exec(new Operation(getContextForThread())
			{
				public Object run() throws NamingException
				{
					LdapContext threadCtx = getCtx();
					threadCtx.setRequestControls(null);
					if (requestControls != null) {
						threadCtx.setRequestControls(requestControls);
					}
					return threadCtx.search(baseName, 
							filter, 
							new SearchControls(SearchControls.SUBTREE_SCOPE, 
									0, 
									0, 
									null, 
									true, 
									true));
				}
			});
		}
		catch (Throwable t)
		{
			System.err.println("Could not perform LDAP search starting at " + baseName + 
					 " and filter " + ldapFilter);
			GenUtils.rethrowRuntime(t);
			return null; // unreachable
		}
	}
  
	public Object get(final String name)
	{
		try
		{
			return exec(new Operation(getContextForThread()) { public Object run() throws NamingException 
				{ return getCtx().lookup(name); }});			
		}
		catch (Throwable t)
		{
			error("Failed to retrieve name '" + name + "'", t);
			return null;
		}
	}

	private void error(String msg, Throwable t)
	{
		System.err.println("LDAP Access Error: " + msg + "; stack trace may follow.");
		if (t != null)
			t.printStackTrace(System.err);
	}
	
	@Override
	protected void finalize() throws Throwable
	{		
		super.finalize();
		try {
			if (ctx != null) 
				ctx.close();
		} catch (Exception e) 
		{
			System.err.println("LDAP ERROR ON INITIAL CONTEXT CLOSE " + e);
		}
	}

}

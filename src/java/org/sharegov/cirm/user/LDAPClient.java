/*******************************************************************************
 * Copyright 2017 Miami-Dade County
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
 * Thread safe, self recovering Ldap client with limited functionality.
 * Pool strategy is to keep one initial ldap context per client thread open and attempt 3x to recover on any error.
 * Recovery means to remove ctx and try create a new one. 
 * (If thread dies, java Ldap client's own finalize will closed & clean up.)
 * 
 * @author Thomas Hilpold
 *
 */
public class LDAPClient {

	final ThreadLocal<LdapContext> ldapContextsByThread = new ThreadLocal<LdapContext>();

	public final static int MAX_RETRY_CREATE = 3;
	public final static int MAX_WAIT_MS = 300;

	private final Properties params;

	/// Class for short lived operation objects
	private static abstract class Operation {
		abstract Object run(LdapContext threadCtx) throws NamingException;
	}

	public LDAPClient(Properties params) {
		if (params == null)
			throw new RuntimeException("LDAPClient created with null params.");
		this.params = params;
		validateParams();
	}

	private LdapContext getContextForThread() {
		LdapContext threadContextH = ldapContextsByThread.get();
		if (threadContextH == null) {
			synchronized (this) {
				System.out.println("LDAP getting newInstance of context for" + Thread.currentThread()
						+ " for client: " + this.hashCode());
				//Throws runtime on failure after multiple attempts.
				threadContextH = tryCreateLdapCtx();
			}
			ldapContextsByThread.set(threadContextH);
		}
		return threadContextH;
	}

	private synchronized void validateParams() {
		System.out.println("LDAP INIT " + this.hashCode());
		try {
			InitialLdapContext initCtx = tryCreateLdapCtx();
			initCtx.close();
		} catch (NamingException e) {
			System.err.println("LDAP Could not initialize initial context with parameters: " + params);
			e.printStackTrace();
			GenUtils.rethrowRuntime(e);
		}
	}

	private InitialLdapContext tryCreateLdapCtx() {
		InitialLdapContext c ;
		int attempts = 0;
		do {
    		attempts ++;
    		try {
    			c = new InitialLdapContext(params, null);
    		} catch (NamingException e) {
				c = null;
    			if (attempts < MAX_RETRY_CREATE) {    				
    				System.err.println("Failed to create LDAP context " + attempts + "/" + MAX_RETRY_CREATE + " Exc: " + e);
    				try {
						Thread.sleep(attempts * MAX_WAIT_MS / MAX_RETRY_CREATE);
					} catch (InterruptedException e1) {
					}
    			} else {
    				System.err.println("Failed to create LDAP context - giving up ");
    				GenUtils.rethrowRuntime(e);
    			}
    		}
		} while (c == null && attempts <= MAX_RETRY_CREATE);
		return c;
	}
	
	private Object exec(Operation op) {
		Exception exc = null;
		for (int i = 0; i < 2; i ++) {
			LdapContext ctx = getContextForThread();
			Object result = null;
			try {
				result = op.run(ctx);
				//exit on success
				return result;
			} catch (Exception e) {
				exc = e;
				//remove and getCtx will attempt recreate on one retry
				//then throw without ctx on thread to recreate ctx on next user action.  
				try {
					ctx.close();
				} catch (Exception ec) {
					System.err.println("Non critical exception on close LDAP context: " + ec);
				}
				ldapContextsByThread.remove();		
			}
		}
		System.err.println("ERROR on LDAP exec after retry:" + exc + " on " + Thread.currentThread());
		GenUtils.rethrowRuntime(exc);
		return null;
	}

	/**
	 * <p>
	 * Perform a search in LDAP and return the results. Several parameters
	 * control the results returned. The search is by default recursive and will
	 * take as long as needed to finish (no timeout). All attributes are
	 * returned in the results. To select only certain attributes to be
	 * returned, use one of the overloaded methods.
	 * </p>
	 * 
	 * @param baseName
	 *            The starting DN.
	 * @param ldapFilter
	 *            The LDAP entry filter.
	 * @param pageSize
	 *            The number of entries per returned page. Use 0 for no paging.
	 * @param sortBy
	 *            The attribute by which returned entries are sorted.
	 * @return an <code>Enumeration</code> of
	 *         <code>javax.naming.directory.SearchResult</code> instances
	 */
	public NamingEnumeration<?> search(final String baseName, final String ldapFilter, final String[] sortBy) {
		try {
			if (baseName == null)
				throw new NullPointerException("LDAPClient.search: null base name.");
			final boolean sorting = (sortBy != null && sortBy.length > 0);
			final Control[] requestControls;
			if (sorting) {
				requestControls = new Control[] { new SortControl(sortBy, Control.NONCRITICAL) };
			} else
				requestControls = null;
			final String filter = ldapFilter == null ? "" : ldapFilter;
			return (NamingEnumeration<?>) exec(new Operation() {
				public Object run(LdapContext threadCtx) throws NamingException {
					if (requestControls != null) {
						threadCtx.setRequestControls(requestControls);
					} else {
						threadCtx.setRequestControls(null);
					}
					return threadCtx.search(baseName, filter,
							new SearchControls(SearchControls.SUBTREE_SCOPE, 0, 0, null, true, true));
				}
			});
		} catch (Throwable t) {
			System.err.println("Could not perform LDAP search starting at " + baseName + " and filter " + ldapFilter);
			GenUtils.rethrowRuntime(t);
			return null; // unreachable
		}
	}

	public Object get(final String name) {
		try {
			return exec(new Operation() {
				public Object run(LdapContext threadCtx) throws NamingException {
					return threadCtx.lookup(name);
				}
			});
		} catch (Throwable t) {
			error("Failed to retrieve name '" + name + "'", t);
			return null;
		}
	}

	private void error(String msg, Throwable t) {
		System.err.println("LDAP Access Error: " + msg + "; stack trace may follow.");
		if (t != null)
			t.printStackTrace(System.err);
	}
}

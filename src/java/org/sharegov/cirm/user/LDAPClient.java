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

import java.util.Date;
import java.util.Properties;
import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.ldap.*;

import org.sharegov.cirm.utils.GenUtils;

/**
 * Thread safe, self recovering Ldap client with limited functionality.
 * Keeps/pools one connection per caller thread and assumes a need to reconnect every 2 minutes.
 * Does NOT close connections after operations (which leads to undefined behavior if reused).
 * 
 * @author Thomas Hilpold
 *
 */
public class LDAPClient {
	
	final ThreadLocal<LdapContextHolder> ldapContextsByThread = new ThreadLocal<LdapContextHolder>();

	public final static long CONTEXT_TIMEOUT = 120 * 1000L; // Timeout and
															// reconnect after 2
															// minutes;

	private final Properties params;
	private volatile InitialLdapContext initCtx;

	/// Class for short lived operation objects
	private static abstract class Operation {
		abstract Object run(LdapContext threadCtx) throws NamingException;
	}

	public LDAPClient(Properties params) {
		if (params == null)
			throw new RuntimeException("LDAPClient created with null params.");
		this.params = params;
		init();
	}

	private LdapContextHolder getContextForThread() {
		LdapContextHolder threadContextH = ldapContextsByThread.get();
		if (threadContextH == null) {
			try {
				synchronized (this) {
					if (initCtx == null)
						init();
					if (initCtx == null)
						throw new RuntimeException("LDAP down, we'll recover once it's back up.");
					System.out.println("LDAP getting newInstance of context for" + Thread.currentThread());
					LdapContext newCtx = initCtx.newInstance(null);
					newCtx.reconnect(null);
					threadContextH = new LdapContextHolder(newCtx);
				}
				ldapContextsByThread.set(threadContextH);
			} catch (NamingException e) {
				System.err.println("LDAP Failed to create context for thread: " + Thread.currentThread());
				initCtx = null;
				GenUtils.rethrowRuntime(e);
			}
		} else {
			if (threadContextH.needsReconnect()) {
				try {
					threadContextH.getCtx().reconnect(null);
					threadContextH.markConnectedNow();
				} catch (NamingException e) {
					System.err.println("LDAP Failed to reconnect for thread: " + Thread.currentThread());
					GenUtils.rethrowRuntime(e);
				}
			}
		}
		return threadContextH;
	}

	private synchronized void init() {
		try {
			initCtx = new InitialLdapContext(params, null);
		} catch (NamingException e) {
			System.err.println("LDAP Could not initialize initial context with parameters: " + params);
			e.printStackTrace();
			initCtx = null;
		}
	}

	private Object exec(Operation op) {
		LdapContextHolder ctxH = getContextForThread();
		Object result = null;
		try {
			result = op.run(ctxH.getCtx());
			ctxH.markConnectedNow();
		} catch (NamingException e) {
			System.err.println("LDAP ERROR on exec:" + e + " on " + Thread.currentThread());
			ldapContextsByThread.remove();
			GenUtils.rethrowRuntime(e);
		} catch (Throwable e) {
			System.err.println("NON LDAP ERROR on exec:" + e + " on " + Thread.currentThread());
			ldapContextsByThread.remove();
			GenUtils.rethrowRuntime(e);
		}
		return result;
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

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		try {
			if (initCtx != null)
				initCtx.close();
		} catch (Exception e) {
			System.err.println("LDAP ERROR ON INITIAL CONTEXT CLOSE " + e);
		}
	}

	/**
	 * Holds context and last successful connect time by thread. <br>
	 * Not thread safe.
	 * 
	 * @author Thomas Hilpold
	 */
	private static class LdapContextHolder {
		private LdapContext ctx;
		private Date lastUsed;

		LdapContextHolder(LdapContext ctx) {
			if (ctx == null)
				throw new NullPointerException("ctx null not allowed.");
			this.ctx = ctx;
			this.lastUsed = new Date();
		}

		private LdapContext getCtx() {
			return ctx;
		}

		private void markConnectedNow() {
			lastUsed = new Date();
		}

		private boolean needsReconnect() {
			Date now = new Date();
			Date check = new Date(lastUsed.getTime() + CONTEXT_TIMEOUT);
			return now.after(check);
		}
	}
}

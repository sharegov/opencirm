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
package org.sharegov.cirm.rdb;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;

import oracle.jdbc.OracleConnection;

/**
 * This class realizes a thread local connection wrapper that supports a
 * topLevelMethod and a subLevelMethod mode of operation. In sublevel mode, commit,
 * rollback, close and some other methods are quietly disabled. In toplevel
 * mode, all methods are enabled. A close in toplevel mode will end the
 * relationship with the thread. For each thread, two objects will be created.
 * One for the toplevel method and one to be used by sublevel method calls.
 * 
 * To use this class a thread shall call createThreadLocalConnection(Connection) in the toplevelMethod.
 * This will return a fully functional wrapper in topLevelMethod mode and also associate a threadlocal 
 * wrapper in subLevelMethodMode, which has commit, rollback, et.c. disabled with it.
 * The thread can now run through sublevel methods and getThreadLocalConnection() will repeatedly 
 * return this wrapper object in sublevel mode. All commit and close operations in sublevel methods will be ignored.   
 * Once execution returns to the starting toplevel method, the toplevel mode wrapper can be used to commit, rollback, et.c. 
 * the wrapped connection.
 *  
 * 
 * @author Thomas Hilpold
 */
public class ThreadLocalConnection implements Connection
{
	public static boolean DBG = false;

	// Access to this variable does not need to be synchronized.
	private static ThreadLocal<ThreadLocalConnection> threadlocalSubLevelConnections = new ThreadLocal<ThreadLocalConnection>();

	private boolean topLevelMethodMode;
	private Connection wrappedConnection;

	/**
	 * Creates and returns a fully functional toplevel thread local connection wrapper for the given
	 * connection. User needs to make sure, that close() is called by the same
	 * thread that calls this method.
	 * This method will also create another wrapper object in sublevelmode and associate it with the thread.
	 * 
	 * @param conn  a connection to be wrapped.
	 * @return a ThreadLocalConnection with topLevelMethodMode enabled.
	 */
	public static ThreadLocalConnection createThreadLocalConnectionTopLevel(Connection conn)
	{
		if (threadlocalSubLevelConnections.get() != null)
		{
			throw new IllegalStateException("A toplevel connection for thread: " + Thread.currentThread().getName()
					+ " was not closed properly.");
		}
		ThreadLocalConnection topLevelWrappedConnection = new ThreadLocalConnection(conn, true);
		threadlocalSubLevelConnections.set(new ThreadLocalConnection(conn, false));
		if (DBG)
		{
			System.out.println("ThreadLocalConnection toplevel created conn : " + topLevelWrappedConnection
					+ " associated with thread " + Thread.currentThread().toString());
		}
		return topLevelWrappedConnection;
	}

	/**
	 * Returns the sublevel connection wrapper for this thread, if a toplevel wrapper was already created. 
	 * The same sublevel object will be returned for the same thread in subsequent calls.
	 * 
	 * @return null or a ThreadLocalConnection with topLevelMethodMode disables (sublevel).
	 */
	public static ThreadLocalConnection getThreadLocalConnection()
	{
		if (DBG) {
			System.out.println("ThreadLocalConnection sublevel request for : " + Thread.currentThread());
		}
		ThreadLocalConnection localConn = threadlocalSubLevelConnections.get();
		if (DBG && localConn != null) {
			System.out.print("returning sublevel connection : " + localConn.wrappedConnection);
		}
		return localConn;
	}

	/**
	 * Tests, if the calling thread has a threadlocal connection associated with it.
	 * @return
	 */
	public static boolean hasThreadLocalConnection()
	{
		return threadlocalSubLevelConnections.get() != null;
	}

	private ThreadLocalConnection(Connection connection, boolean topLevelMode)
	{
		if (DBG && topLevelMode) {
			System.out.println("Creating toplevel for connection : " + connection + " tlc: " + this);
		}
		if (DBG && !topLevelMode) {
			System.out.println("Creating sublevel for connection : " + connection + " tlc: " + this);
		}
		this.topLevelMethodMode = topLevelMode;
		wrappedConnection = connection;
	}
	
	public void assertTopLevelMode() {
		if (!isTopLevelMode()) {
			throw new IllegalStateException("This threadlocalconnection ignores commit, rollback and close.");
		}
	}

	/**
	 * For testing only. Do not use.
	 * @return
	 */
	public Connection getDirectConnection() {
		return wrappedConnection;
	}
	
	public boolean isTopLevelMode()
	{
		return topLevelMethodMode;
	}

	public void setTopLevelMode(boolean topLevelMode)
	{
		this.topLevelMethodMode = topLevelMode;
	}

	// ------------------------------------------------------------------------
	// MODIFIED METHODS:
	//

	public void setAutoCommit(boolean autoCommit) throws SQLException
	{
		if (topLevelMethodMode)
		{
			wrappedConnection.setAutoCommit(autoCommit);
		}
	}

	public void commit() throws SQLException
	{
		if (topLevelMethodMode)
		{
			wrappedConnection.commit();
		}
	}

	public void rollback() throws SQLException
	{
		if (topLevelMethodMode)
		{
			wrappedConnection.rollback();
		}
	}

	public void closeAndDiscard() throws SQLException {
		if (topLevelMethodMode) { 
			if (wrappedConnection.isWrapperFor(OracleConnection.class)) {
				try {
					OracleConnection oc =wrappedConnection.unwrap(OracleConnection.class);
					oc.close(OracleConnection.INVALID_CONNECTION);
					System.err.println("Successfully Discarded: " + oc);
				} finally {
					// Disassociate this connection from the threadlocal var.
					threadlocalSubLevelConnections.remove();
				}
			} else {
				close();
			}
		}
	}

	public void close() throws SQLException
	{
		if (topLevelMethodMode) 
		{
			try
			{
				if (wrappedConnection.getWarnings() != null) {
					System.out.println("Connection : " + wrappedConnection + " had warnings before close.:");
					System.out.println(wrappedConnection.getWarnings());
				}
				wrappedConnection.close();
			}
			catch (SQLException e)
			{
				throw e;
			}
			finally
			{
				// Disassociate this connection from the threadlocal var.
				threadlocalSubLevelConnections.remove();
				if (DBG)
				{
					System.out.println("ThreadLocalConnection toplevel close on " + this + " by "
							+ Thread.currentThread().toString());
				}
			}
		}
	}

	/**
	 * Inactive if not topLevel.
	 */
	public void setReadOnly(boolean readOnly) throws SQLException
	{
		if (topLevelMethodMode)
		{
			wrappedConnection.setReadOnly(readOnly);
		}
	}

	/**
	 * Effective for ToplevelConnection only, because it needs to be called as
	 * first statement of a transaction.
	 */
	public void setTransactionIsolation(int level) throws SQLException
	{
		if (topLevelMethodMode)
		{
			wrappedConnection.setTransactionIsolation(level);
		}
	}

	public void setCatalog(String catalog) throws SQLException
	{
		wrappedConnection.setCatalog(catalog);
	}

	public void clearWarnings() throws SQLException
	{
		wrappedConnection.clearWarnings();
	}

	public void setHoldability(int holdability) throws SQLException
	{
		wrappedConnection.setHoldability(holdability);
	}

	public void setTypeMap(Map<String, Class<?>> map) throws SQLException
	{
		wrappedConnection.setTypeMap(map);
	}

	// ------------------------------------------------------------------------
	// UNMODIFIED METHODS DIRECTLY DELEGATED:
	//
	public <T> T unwrap(Class<T> iface) throws SQLException
	{
		return wrappedConnection.unwrap(iface);
	}

	public boolean isWrapperFor(Class<?> iface) throws SQLException
	{
		return wrappedConnection.isWrapperFor(iface);
	}

	public Statement createStatement() throws SQLException
	{
		return wrappedConnection.createStatement();
	}

	public PreparedStatement prepareStatement(String sql) throws SQLException
	{
		return wrappedConnection.prepareStatement(sql);
	}

	public CallableStatement prepareCall(String sql) throws SQLException
	{
		return wrappedConnection.prepareCall(sql);
	}

	public String nativeSQL(String sql) throws SQLException
	{
		return wrappedConnection.nativeSQL(sql);
	}

	public boolean getAutoCommit() throws SQLException
	{
		return wrappedConnection.getAutoCommit();
	}

	public boolean isClosed() throws SQLException
	{
		return wrappedConnection.isClosed();
	}

	public DatabaseMetaData getMetaData() throws SQLException
	{
		return wrappedConnection.getMetaData();
	}

	public boolean isReadOnly() throws SQLException
	{
		return wrappedConnection.isReadOnly();
	}

	public String getCatalog() throws SQLException
	{
		return wrappedConnection.getCatalog();
	}

	public int getTransactionIsolation() throws SQLException
	{
		return wrappedConnection.getTransactionIsolation();
	}

	public SQLWarning getWarnings() throws SQLException
	{
		return wrappedConnection.getWarnings();
	}

	public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException
	{
		return wrappedConnection.createStatement(resultSetType, resultSetConcurrency);
	}

	public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
			throws SQLException
	{
		return wrappedConnection.prepareStatement(sql, resultSetType, resultSetConcurrency);
	}

	public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException
	{
		return wrappedConnection.prepareCall(sql, resultSetType, resultSetConcurrency);
	}

	public Map<String, Class<?>> getTypeMap() throws SQLException
	{
		return wrappedConnection.getTypeMap();
	}

	public int getHoldability() throws SQLException
	{
		return wrappedConnection.getHoldability();
	}

	public Savepoint setSavepoint() throws SQLException
	{
		return wrappedConnection.setSavepoint();
	}

	public Savepoint setSavepoint(String name) throws SQLException
	{
		return wrappedConnection.setSavepoint(name);
	}

	public void rollback(Savepoint savepoint) throws SQLException
	{
		wrappedConnection.rollback(savepoint);
	}

	public void releaseSavepoint(Savepoint savepoint) throws SQLException
	{
		wrappedConnection.releaseSavepoint(savepoint);
	}

	public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
			throws SQLException
	{
		return wrappedConnection.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
	}

	public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
			int resultSetHoldability) throws SQLException
	{
		return wrappedConnection.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
	}

	public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
			int resultSetHoldability) throws SQLException
	{
		return wrappedConnection.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
	}

	public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException
	{
		return wrappedConnection.prepareStatement(sql, autoGeneratedKeys);
	}

	public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException
	{
		return wrappedConnection.prepareStatement(sql, columnIndexes);
	}

	public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException
	{
		return wrappedConnection.prepareStatement(sql, columnNames);
	}

	public Clob createClob() throws SQLException
	{
		return wrappedConnection.createClob();
	}

	public Blob createBlob() throws SQLException
	{
		return wrappedConnection.createBlob();
	}

	public NClob createNClob() throws SQLException
	{
		return wrappedConnection.createNClob();
	}

	public SQLXML createSQLXML() throws SQLException
	{
		return wrappedConnection.createSQLXML();
	}

	public boolean isValid(int timeout) throws SQLException
	{
		return wrappedConnection.isValid(timeout);
	}

	public void setClientInfo(String name, String value) throws SQLClientInfoException
	{
		wrappedConnection.setClientInfo(name, value);
	}

	public void setClientInfo(Properties properties) throws SQLClientInfoException
	{
		wrappedConnection.setClientInfo(properties);
	}

	public String getClientInfo(String name) throws SQLException
	{
		return wrappedConnection.getClientInfo(name);
	}

	public Properties getClientInfo() throws SQLException
	{
		return wrappedConnection.getClientInfo();
	}

	public Array createArrayOf(String typeName, Object[] elements) throws SQLException
	{
		return wrappedConnection.createArrayOf(typeName, elements);
	}

	public Struct createStruct(String typeName, Object[] attributes) throws SQLException
	{
		return wrappedConnection.createStruct(typeName, attributes);
	}
}

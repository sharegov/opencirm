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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;
import javax.sql.DataSource;
import mjson.Json;

import org.sharegov.cirm.AutoConfigurable;
import org.sharegov.cirm.utils.JsonUtil;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;
import static org.sharegov.cirm.utils.GenUtils.*;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

/**
 * <p>
 * Access users from a database table.
 * </p>
 * 
 * @author boris, thomas hilpold
 *
 */
public class DBUserProvider implements UserProvider, AutoConfigurable
{
	public static final boolean USE_CONNECTION_POOL = true;
	public static final int POOL_SIZE_INITIAL = 1;
	public static final int POOL_SIZE_MAX = 60;
	public static final int POOL_CONNECTION_REUSE_COUNT_MAX = 1000;

	/**
	 * Following is recommended to be used as pooled datasource acc to MS doc
	 * @see 
	 */
	private String dataSourceClassName;
	private String driver;
	private String url;
	private String user;
	private String pwd;
	private String dsName;
	private String idColumn; // = "UserId";
	private String table; //  = "UserList";	
	private volatile DataSource datasource; //needs to be volatile for double checked locking to work
	private Json config = Json.object();
	
	public void autoConfigure(Json C)
	{
		this.config = C.dup();
		if (!config.has("hasDataSource"))
			throw new RuntimeException("Please configure DB connection with hasDataSource property of " + 
					C.at("iri"));
		Json db = config.at("hasDataSource");
		this.url = db.at("hasUrl", "").asString();
		this.user = db.at("hasUsername", "").asString();
		this.pwd = db.at("hasPassword", "").asString();
		this.dataSourceClassName = db.at("hasDatabaseType").at("hasDataSourceClassName").asString();
		this.driver = db.at("hasDatabaseType").at("hasDriver").asString();
		if (db.has("hasName"))
			this.dsName = db.at("hasName").asString();
		
		if ((this.dsName == null || this.dataSourceClassName == null) && driver != null)
			try { Class.forName(driver); } catch (Exception ex) { throw new RuntimeException(ex); }
		
		this.idColumn = config.at("hasIdName", "ID").asString();
		this.table = config.at("hasTableName", "user").asString();
	}
	
	public Json find(String attribute, String value)
	{
		Json result = Json.array();
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		String query = "select * from "
				+ table + " where " + attribute + " LIKE '?%'";
		try
		{
			conn = getConnection();
			stmt = conn.prepareStatement(query);
			stmt.setString(1, value);
			rs = stmt.executeQuery();
			ResultSetMetaData meta = rs.getMetaData();			
			while (rs.next())
			{
				Json u = Json.object();
				for(int i = 1; i <= meta.getColumnCount(); i++)
				{
					u.set(meta.getColumnName(i), rs.getString(i));
				}
				result.add(u);
			}						
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			try{rs.close();}catch(Exception e){}
			try{stmt.close();}catch(Exception e){}
			try{conn.close();}catch(Exception e){}
		}
		return result;
	}
	
	public Json findGroups(String id)
	{
	    return Json.array();
	}
	
	public Json find(Json prototype)
	{
		return find(prototype, 0);
	}
	
	/**
	 * Finds a list of users based on a set of name value pairs which
	 * represent attributes of the user. A single user is represented
	 * as a Map of name-values.
	 * 
	 *  @param protoype - a list of name value pairs that represent values for a user.
	 *  @param resultLimit - a limit on the amount of results 
	 *  
	 *  @return A List of users represented as maps.
	 */
	public Json find(Json prototype, int resultLimit)
	{
		Json result = Json.array();
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		StringBuilder query = new StringBuilder("select ");
		if(resultLimit > 0 )
			query.append(" TOP " + resultLimit);
		query.append(" * from " + table + " where ");
		TreeMap<String,Json> sortedParameters = new TreeMap<String,Json>(prototype.asJsonMap());
		for(Map.Entry<String, Json> entry: sortedParameters.entrySet())
		{
			String attribute = entry.getKey();
			if (config.has(attribute))
				attribute = config.at(attribute).asString();
			Json value = entry.getValue();
			if(value.isString())
			{
				query.append(attribute).append(" LIKE ? ").append(" AND ");
			}
		}
		query.delete(query.lastIndexOf(" AND "), query.length()-1);
		try
		{
			conn = getConnection();
			stmt = conn.prepareStatement(query.toString());
			int parameterCount = 1;
			for(Map.Entry<String, Json> entry: sortedParameters.entrySet())
			{
				Json value = entry.getValue();
				if(value.isString())
				{
					stmt.setString(parameterCount, value.asString());
					parameterCount++;
				}
			}
			rs = stmt.executeQuery();
			ResultSetMetaData meta = rs.getMetaData();
			while (rs.next())
			{
				Json u = Json.object();
				for(int i = 1; i <= meta.getColumnCount(); i++)
				{
					u.set(meta.getColumnName(i), rs.getString(i));
				}
				result.add(u);
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			try{rs.close();}catch(Exception e){}
			try{stmt.close();}catch(Exception e){}
			try{conn.close();}catch(Exception e){}
		}
		return result;
	}

	public Json get(String id)
	{
		Json u = Json.nil();
		Connection conn = null;
		Statement stmt = null;
		ResultSet rs = null;
		String query = "select * from "
				+ table + " where " + idColumn + " = '" + id + "'";
		try
		{
			conn = getConnection();
			stmt = conn.createStatement();
			rs = stmt.executeQuery(query);
			ResultSetMetaData meta = rs.getMetaData();			
			if (rs.next())
			{				
				u = Json.object();
				for(int i = 1; i <= meta.getColumnCount(); i++)
				{
					u.set(meta.getColumnName(i), rs.getString(i));
				}
			}
		}
		catch (SQLException e)
		{
			throw new RuntimeException(e);
		}
		finally
		{
			try{rs.close();}catch(Exception e){}
			try{stmt.close();}catch(Exception e){}
			try{conn.close();}catch(Exception e){}
		}
		return u;
	}

	
	public String getIdAttribute()
	{
		return idColumn;
	}
	
	public String getDataSourceClassName()
	{
		return dataSourceClassName;
	}

	public void setDataSourceClassName(String dataSourceClassName)
	{
		this.dataSourceClassName = dataSourceClassName;
	}

	public String getUrl()
	{
		return url;
	}

	public void setUrl(String url)
	{
		this.url = url;
	}

	public String getUser()
	{
		return user;
	}

	public void setUser(String user)
	{
		this.user = user;
	}

	public String getPwd()
	{
		return pwd;
	}

	public void setPwd(String pwd)
	{
		this.pwd = pwd;
	}

	public String getDsName()
	{
		return dsName;
	}

	public void setDsName(String dsName)
	{
		this.dsName = dsName;
	}

	public String getIdColumn()
	{
		return idColumn;
	}

	public void setIdColumn(String idColumn)
	{
		this.idColumn = idColumn;
	}

	public String getTable()
	{
		return table;
	}

	public void setTable(String table)
	{
		this.table = table;
	}
	
	public Connection getConnection() 
	{
		Connection conn = null;

		if (dsName == null || dataSourceClassName == null)
		{
			try
			{
				conn = DriverManager.getConnection(url, user, pwd);
			}
			catch (SQLException e)
			{
				System.err.println("DBUserProvider Failed to get a connection:" + e);
				throw new RuntimeException(e);
			}			
		}
		else
		{
			try 
			{
				conn = getDatasource().getConnection();
				
			} 
			catch (SQLException e)
			{
				System.err.println("DBUserProvider Failed to get a connection:" + e);
				throw new RuntimeException(e);
			}
			if (dbg())
			{
				//ThreadLocalStopwatch.getWatch().time("DBUserProvider getConnection: " + conn);
				try {
				if (getDatasource() instanceof PoolDataSource)
				{
					PoolDataSource ods = (PoolDataSource)getDatasource();
					if (ods.getBorrowedConnectionsCount() > POOL_SIZE_MAX) 
					{
						ThreadLocalStopwatch.getWatch().time("Pool borrowed Conns count > 50% Max pool: " + ods.getBorrowedConnectionsCount());
						ThreadLocalStopwatch.dispose();
					}
				}
				}catch(Exception e) {};
			}			
		}
		
		return conn;
	}

	private DataSource getDatasource() throws SQLException
	{
		if (datasource == null) 
		{
			synchronized (this)
			{
				if (datasource == null)
						datasource = createPoolDatasource();
			}
		}
		return datasource;
	}
	/**
	 * Creates a pool datasource.
	 * 
	 * @throws SQLException
	 */
	private PoolDataSource createPoolDatasource() throws SQLException
	{
		PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();
		pds.setConnectionFactoryClassName(dataSourceClassName); 
		pds.setURL(url);
		pds.setUser(user);
		pds.setPassword(pwd);

		pds.setInitialPoolSize(POOL_SIZE_INITIAL);
		pds.setMinPoolSize(1);
		pds.setMaxPoolSize(POOL_SIZE_MAX);
		pds.setMaxConnectionReuseCount(POOL_CONNECTION_REUSE_COUNT_MAX);
		pds.setConnectionPoolName("UCP Pool " + this.getClass().getSimpleName() + "/" + dsName + "/" + hashCode());
		pds.setValidateConnectionOnBorrow(true);
		System.out.println("DBUSERPROVIDER POOL DATA SOURCE : " + pds);
		System.out.println("DB URL : " + url);
		Connection testConn = pds.getConnection();
		testConn.close();
		return pds;
	}

    public boolean authenticate(String username, String password)
    {
        boolean result = false;
        return result;
    }
    
    public Json populate(Json user)
    {
    	if (user.has("userid"))
    	{
    		Json found = get(user.at("userid").asString());
    		if (!found.isNull())
    		{    			
    			user.set(config.at("hasName").asString(), found);
    			if (config.has("email"))
    				JsonUtil.setIfMissing(user, "email", found.at(config.at("email").asString()));
    			if (config.has("FirstName"))
    				JsonUtil.setIfMissing(user, "FirstName", found.at(config.at("FirstName").asString()));
    			if (config.has("LastName"))
    				JsonUtil.setIfMissing(user, "LastName", found.at(config.at("LastName").asString()));
    			if (config.has("hasUsername"))
    				JsonUtil.setIfMissing(user, "hasUsername", found.at(config.at("hasUsername").asString()));
    		}    		
    	}
    	return user;

    }
}

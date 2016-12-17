package org.sharegov.cirm.rdb.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Properties;

import javax.sql.DataSource;
import mjson.Json;

import oracle.jdbc.pool.OracleDataSource;
import oracle.ucp.UniversalConnectionPoolException;
import oracle.ucp.admin.UniversalConnectionPoolManager;
import oracle.ucp.admin.UniversalConnectionPoolManagerImpl;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import static org.sharegov.cirm.rdb.Sql.*;
import org.sharegov.cirm.rdb.DBU;
import org.sharegov.cirm.rdb.DatabaseHook;
import org.sharegov.cirm.utils.DBGUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

public class OracleHook implements DatabaseHook
{
	public static final int POOL_SIZE_INITIAL = 5;
	public static final int POOL_SIZE_MAX = 50; //150 processes limit on server
	public static final int POOL_CONNECTION_REUSE_COUNT_MAX = 1000;
	public static final int POOL_CONNECTION_STATEMENTS_MAX = 40;
	public static final boolean POOL_CONNECTION_VALIDATE_ON_BORROW = true;
	public static final int POOL_CONNECTION_WAIT_TIMEOUT_SECS = 120;
	public static final int POOL_CONNECTION_INACTIVE_TIMEOUT_SECS = 8 * 3600; //before it is removed from pool
	public static final int POOL_CONNECTION_PREFETCH_ROWS = 50; //single db roundtrip
	public static final int POOL_CONNECTION_BATCH_ROWS = 50; //single db roundtrip
	
	public DataSource createDataSource(Json description)
	{
		try
		{
			OracleDataSource ods = new OracleDataSource();
			ods.setURL(description.at("hasUrl").asString());
			ods.setUser(description.at("hasUsername").asString());
			ods.setPassword(description.at("hasPassword").asString());
			//Set connection timeout if configured
			if (description.has("hasTimeoutSecs")) {
				try {
					Properties connectionProperties = new Properties();
					int timeoutSecs = Integer.parseInt(description.at("hasTimeoutSecs").asString());
					connectionProperties.setProperty("oracle.jdbc.ReadTimeout", "" + timeoutSecs * 1000);
					ods.setConnectionProperties(connectionProperties);
				} catch (Exception e) {
					ThreadLocalStopwatch.error("Database connection hasTimeoutSecs not an integer value, using default for data source url" + ods.getURL());
					e.printStackTrace();
				}
			}	
			return ods;
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
	}

	public DataSource createPooledDataSource(Json description)
	{
		String poolName = "Cirm UCP Pool for " + description.at("iri").asString();		
		PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();
		try
		{
			pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
			pds.setURL(description.at("hasUrl").asString());
			pds.setUser(description.at("hasUsername").asString());
			pds.setPassword(description.at("hasPassword").asString());
			pds.setConnectionPoolName(poolName);
			pds.setInitialPoolSize(POOL_SIZE_INITIAL);
			pds.setMinPoolSize(POOL_SIZE_INITIAL);
			pds.setMaxPoolSize(POOL_SIZE_MAX);
			pds.setMaxConnectionReuseCount(POOL_CONNECTION_REUSE_COUNT_MAX);
			//Sets implicit statement cache on all pooled connections
			pds.setMaxStatements(POOL_CONNECTION_STATEMENTS_MAX);
			pds.setValidateConnectionOnBorrow(POOL_CONNECTION_VALIDATE_ON_BORROW);
			//How long to wait if a conn is not available
			pds.setConnectionWaitTimeout(POOL_CONNECTION_WAIT_TIMEOUT_SECS);
			//How many secs to wait until a pooled and unused connection is removed from pool
			// 8 h
			pds.setInactiveConnectionTimeout(POOL_CONNECTION_INACTIVE_TIMEOUT_SECS);
			Properties connectionProperties = new Properties();
			connectionProperties.setProperty("defaultRowPrefetch", "" + POOL_CONNECTION_PREFETCH_ROWS);
			connectionProperties.setProperty("defaultBatchValue", "" + POOL_CONNECTION_BATCH_ROWS);
			//Set connection timeout if configured
			if (description.has("hasTimeoutSecs")) {
				try {
					int timeoutSecs = Integer.parseInt(description.at("hasTimeoutSecs").asString());
					connectionProperties.setProperty("oracle.jdbc.ReadTimeout", "" + timeoutSecs);
				} catch (Exception e) {
					ThreadLocalStopwatch.error("Database connection hasTimeoutSecs not an integer value, using default for pool data source url" + pds.getURL());
					e.printStackTrace();
				}
			}
			pds.setConnectionProperties(connectionProperties);
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
		try 
		{
			Connection testConn = pds.getConnection();
			testConn.close();
		} 
		catch (Exception e)
		{
			ThreadLocalStopwatch.getWatch().time("POOL DATA SOURCE: FAILED TO GET A TEST CONNECTION FROM POOL!\r\n Exception was: ");
			e.printStackTrace();
			System.err.print("Attemting to destroy the failing pool \"" + poolName + "\"...");
			try
			{
				UniversalConnectionPoolManager pm = UniversalConnectionPoolManagerImpl.getUniversalConnectionPoolManager();
				pm.destroyConnectionPool(poolName);
				System.err.println("Succeeded.");
			} 
			catch (UniversalConnectionPoolException e1)
			{
				System.err.println("Failed. Exception on failing to destroy pool was:");
				e1.printStackTrace();
			}
			throw new RuntimeException(e);
		}
		DBGUtils.printPoolDataSourceInfo(pds);
		return pds;
	}
	
	public long nextSequence(Connection conn, String sequenceName)  throws SQLException
	{
        ResultSet rs = null;
        java.sql.Statement stmt = null;
        try
        {
            stmt = conn.createStatement();
            rs = stmt.executeQuery("select " + sequenceName+ ".nextval from dual");
            rs.next();
            long nextId = rs.getLong(1);
            conn.commit();
            return nextId;
        }        
        finally
        {
            DBU.close(null, stmt, rs);
        }	    
	}

    public String nextSequenceClause(String sequenceName)
    {
        return sequenceName + ".NEXTVAL";
    }
	
	public Date timeStamp(Connection conn) throws SQLException
	{
        PreparedStatement stmt = null;
        ResultSet rs = null;
        Date result;
        String select = "SELECT SYSTIMESTAMP FROM DUAL";
        try
        {
            stmt = conn.prepareStatement(select);
            rs = stmt.executeQuery();
            rs.next();
            result = rs.getTimestamp(1);
            conn.commit();
        }
        finally
        {
            DBU.close(null, stmt, rs);
        }
        return result;
	}
	
	public String paginate(String sql, long minValue, long maxValue)
	{
        return SELECT()
                .COLUMN("*")
                .FROM("(" + 
                    SELECT()
                    .COLUMN("a.*")
                    .COLUMN("rownum rnum")
                    .FROM("(" + sql + ") a")
                    .WHERE("rownum")
                    .LESS_THAN_OR_EQUAL("" + maxValue).SQL()
                +")")
                .WHERE("rnum")
                .GREATER_THAN_OR_EQUAL("" + minValue).SQL();	    
	}
	
	public void resetSequence(Connection conn, String sequenceName)  throws SQLException
	{
        ResultSet rs = null;
        java.sql.Statement stmt = null;
        try
        {
            stmt = conn.createStatement();
            stmt.execute("drop sequence " + sequenceName);
            stmt.execute("CREATE SEQUENCE " + sequenceName + " start with 1 minvalue 1 increment by 1 cache 20 order");
            conn.commit();
        }        
        finally
        {
            DBU.close(null, stmt, rs);
        }	    
	}
}
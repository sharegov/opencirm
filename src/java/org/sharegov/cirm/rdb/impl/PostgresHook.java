package org.sharegov.cirm.rdb.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

import javax.sql.DataSource;

import mjson.Json;

import org.sharegov.cirm.rdb.DBU;
import org.sharegov.cirm.rdb.DatabaseHook;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.ds.PGPoolingDataSource;

public class PostgresHook implements DatabaseHook
{
    public DataSource createDataSource(Json description)
    {
        try
        {
            PGSimpleDataSource source = new PGSimpleDataSource();
            source.setUrl(description.at("hasUrl").asString());
            source.setUser(description.at("hasUsername").asString());
            source.setPassword(description.at("hasPassword").asString());               
            return source;
        }
        catch (SQLException ex)
        {
            throw new RuntimeException(ex);
        }
    }

    public DataSource createPooledDataSource(Json description)
    {
        try
        {
            PGPoolingDataSource source = new PGPoolingDataSource();
            source.setDataSourceName("CiRM PostgreSQL Pooling Data Source");
            source.setUrl(description.at("hasUrl").asString());
            source.setUser(description.at("hasUsername").asString());
            source.setPassword(description.at("hasPassword").asString());                       
            source.setMaxConnections(10);        
            return source;
        }
        catch (SQLException ex)
        {
            throw new RuntimeException(ex);
        }
    }
    
    public long nextSequence(Connection conn, String sequenceName)  throws SQLException
    {
        ResultSet rs = null;
        java.sql.Statement stmt = null;
        try
        {
            stmt = conn.createStatement();
            rs = stmt.executeQuery("select nextval('" + sequenceName + "')");
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
        return "nextval('" + sequenceName + "')";
    }
    
    public Date timeStamp(Connection conn) throws SQLException
    {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        Date result;
        String select = "select current_timestamp";
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
        return sql + " offset " + (minValue-1) + " limit  " + (maxValue - minValue + 1);
    }
    
	public void resetSequence(Connection conn, String sequenceName)  throws SQLException
	{
        ResultSet rs = null;
        java.sql.Statement stmt = null;
        try
        {
            stmt = conn.createStatement();
            stmt.execute("alter sequence " + sequenceName + " restart" );
            conn.commit();
        }        
        finally
        {
            DBU.close(null, stmt, rs);
        }	    
	}
}
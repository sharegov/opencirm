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

import java.sql.*;
import java.sql.Statement;

import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.Ref;

import mjson.Json;

/**
 * Static utilities for working with JDBC
 * 
 * @author Borislav Iordanov
 *
 */
public class DBU
{
	public static void close(Connection c, java.sql.Statement s, ResultSet rs)
	{
		if (rs != null) try { rs.close(); } catch (Throwable t) { }
		if (s != null) try { s.close(); } catch (Throwable t) { }
		if (c != null) try { c.close(); } catch (Throwable t) { }
	}

	public static Json rowToJson(ResultSet rs)
	{
		try
		{
			ResultSetMetaData meta = rs.getMetaData();
			Json j = Json.object();
			for (int i = 1; i <= meta.getColumnCount(); i++)
			{
				Json value = Json.nil();
				if (rs.getObject(i) != null) switch (meta.getColumnType(i))
				{
					case Types.DATE: value = Json.make(GenUtils.formatDate(rs.getDate(i))); break;
					case Types.TIMESTAMP: value = Json.make(rs.getTimestamp(i).getTime()); break;
					case Types.VARCHAR: value = Json.make(rs.getString(i)); break;
					case Types.NUMERIC: value = Json.make(rs.getObject(i)); break;
					default: value = Json.make(rs.getObject(i));
				}
				j.set(meta.getColumnName(i), value);
			}
			return j;
		}
		catch (SQLException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	public static Json collectOne(Ref<DataSourceRef> datasource, String query)
	{
		Json A = collect(datasource, query);
		if (A.asJsonList().isEmpty())
			return Json.nil();
		else
			return A.at(0);
	}
	
	public static Json collect(Ref<DataSourceRef> datasource, String query)
	{
		Connection conn = null;
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			conn = datasource.resolve().resolve().getConnection();
			stmt = conn.createStatement();
			rs = stmt.executeQuery(query);
			Json A = Json.array();
			if (rs.next())
				A.add(DBU.rowToJson(rs));
			return A;
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
		finally
		{
			DBU.close(conn, stmt, rs);
		}		
	}
	
	public static Json collect(ResultSet rs)
	{
		Json A = Json.array();
		try
		{
			while (rs.next())
				A.add(rowToJson(rs));
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
		return A;
	}
}

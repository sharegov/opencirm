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
package gov.miamidade.cirm.maintenance;

import gov.miamidade.cirm.GisClient;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import mjson.Json;
import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.gis.GisDAO;

public class GisTableUpdate
{

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		GisClient.DBGSQL =true;
		GisClient.DBG = false;
		GisClient.DBGX = false;
		Connection conn = null;
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			
			conn = Refs.defaultRelationalStoreExt.resolve().getConnection();
			stmt = conn.createStatement();
			rs = stmt.executeQuery("select ID, DATA from CIRM_GIS_INFO");
			while (rs.next())
			{
				long dbId = rs.getLong("ID");
				String s = rs.getString("DATA");
				boolean directMapping = s.contains("\"MUNCODE\"");
				Json existing = Json.read(s);;
				update(dbId, existing, directMapping);
			}
			conn.commit();
			rs.close();
			rs = null;
			stmt.close();
		}catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
		finally
		{
			if (rs != null)
				try { rs.close(); } catch (Throwable t) { } 
			if (stmt != null)
				try { stmt.close(); } catch (Throwable t) { }
			if (conn != null)
				try { conn.close(); } catch (Throwable t) { } 
		
		}		
			

	}
	
	public static boolean update(final long dbId, final Json locationInfo, final boolean directMapping)
	{
		return Refs.defaultRelationalStore.resolve().txn(new CirmTransaction<Boolean>() {
			public Boolean call()
			{
				GisDAO.updateNormalizedColumns(dbId, locationInfo, directMapping);
				return true;
			}
		});
	}
}

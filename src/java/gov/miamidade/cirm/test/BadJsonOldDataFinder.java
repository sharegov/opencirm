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
package gov.miamidade.cirm.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import mjson.Json;

import org.sharegov.cirm.rest.OperationService;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;




public class BadJsonOldDataFinder
{

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
	
		BadJsonOldDataFinder f = new BadJsonOldDataFinder();
		try {
			f.startProcess();
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void startProcess() throws Exception
	{
		
		//String sql = "SELECT * FROM CIRM_DATA_PROPERTY_VIEW WHERE PREDICATE_ID = 107 AND VALUE_CLOB IS NOT NULL ORDER BY SUBJECT_ID";
		//String sql = "SELECT * FROM CIRM_DATA_PROPERTY_VIEW WHERE PREDICATE_ID = 107 AND VALUE_VARCHAR IS NOT NULL ORDER BY SUBJECT_ID";
		String sql = "SELECT * FROM CIRM_DATA_PROPERTY_VIEW WHERE PREDICATE_ID = 107 AND VALUE_VARCHAR_LONG IS NOT NULL ORDER BY SUBJECT_ID";
		
		Connection c = null;
		long boid = -1;
		int i = 0;
		try {
			c = OperationService.getPersister().getStoreExt().getConnection();
			Statement s = c.createStatement();
			System.out.println("Executing: " + sql);
			ResultSet rs = s.executeQuery(sql);
			System.err.println("i;boid;Error;Json;");
			while (rs.next()) 
			{
				i ++;
				boid = rs.getLong("SUBJECT_ID");
				String jsonStr = rs.getString("VALUE_VARCHAR_LONG");
				//Clob clob = rs.getClob("VALUE_CLOB");
				if (!rs.wasNull()) {
					//String jsonStr = clob.getSubString(1L, (int)clob.length());
					try 
					{
						Json j = Json.read(jsonStr);
					} catch (Exception e) 
					{
						System.err.println(i + ";" + boid + ";" + e.getClass().getSimpleName() + " " + e.getMessage() + ";" + jsonStr + ";");
					}
				}			
				if (i % 10000 == 0) 
				{ 
					ThreadLocalStopwatch.getWatch().time("Read: " + i + " hasOldServicaAnswer values");					
				}
			}
			rs.close();
			s.close();
		} finally 
		{
			System.out.println("Last i "+ i + "BOID: " + boid);
			if (c != null) c.close();
		}
	}
	

	public static void prompt() 
	{
		try{
		    BufferedReader bufferRead = new BufferedReader(new InputStreamReader(System.in));
		    String s = bufferRead.readLine();
		}
		catch(IOException e)
		{
			throw new RuntimeException(e);
		}
	}


	

}

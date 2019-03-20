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

import java.io.File;
import java.net.URL;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import mjson.Json;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sharegov.cirm.rest.LegacyEmulator;
import org.sharegov.cirm.utils.GenUtils;

public class StreetInsert
{
	static Json json1; 
	static String json1Str; 

	static LegacyEmulator le = new LegacyEmulator();
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		//URL buylkytraJSONURL = StreetInsert.class.getResource("BULKYTRA.json");
		json1Str = GenUtils.readTextFile(new File("c:/temp/BULKYTRA.json"));
		json1 = Json.read(json1Str);
	}

	@Test
	public void testStringArray() {
		String hash = "9aOAMKiNVJg7M0aG6RhjHeyvSQ4="; 
		String value = "12983612947861384762318472364872364823746283746238476238472342";
		String[] arr1 = new String[] {hash, value};
		String[] arr2 = new String[] {hash, value};
		Map<String, String> m1 = Collections.singletonMap(hash, value);
		Map<String, String> m2 = Collections.singletonMap(hash, value);
		System.out.println("arr1 equals arr2 ?" + arr1.equals(arr2));
		System.out.println("arr1 hash ?" + arr1.hashCode());
		System.out.println("arr2 hash ?" + arr2.hashCode());
		System.out.println("map1 equals map2 ?" + m1.equals(m2));
		System.out.println("map1 hash ?" + m1.hashCode());
		System.out.println("map2 hash ?" + m2.hashCode());
	}

	
	@Test
	public void testSaveLoadBusinessObjectOntology100K()
	{
		RelationalOWLPersister.DBG = false;
		
		System.out.println("TEST testSaveLoadBusinessObjectOntology100K STARTED. " + new Date());
		long startTime = System.currentTimeMillis();
		
		Json bo = le.saveNewServiceRequest(json1Str);
		String saveTimeSecs = getDurationSecs(startTime);
		
		for (int i = 1; i < 100000; i = i + 10) {
			try
			{
				for (int j = i; j < i + 10; j++) {
					startTime = System.currentTimeMillis();
					//bo: need ok.data.boid
					startTime = System.currentTimeMillis();								
					long boid = bo.at("data").at("boid").asLong();
					Json bo2 = le.lookupServiceCase(boid);
//					String loadTimeSecs = getDurationSecs(startTime);
//					System.out.println("Time \t BOID \t SaveNewTimeSecs \t LookupTimeSecs \t BO2size \t Nr ");
//					System.out.print(new Date() + "\t");
//					System.out.print(boid + "\t");
//					System.out.print(saveTimeSecs + "\t");
//					System.out.print(loadTimeSecs + "\t");
//					System.out.print(bo2.toString().length() + "\t");
//					System.out.println(j);
				}
				//Thread.sleep(1 * 1000);
			}
			catch (Throwable e)
			{
				e.printStackTrace();
				if (e instanceof Error) {
					throw (Error)e;
				}
				//and continue
			}
		}
		System.out.println("TEST FINISHED. " + new Date());
	}

		
	private NumberFormat df = DecimalFormat.getNumberInstance();	
		
	public String getDurationSecs(long startTime) {
		long duration = System.currentTimeMillis() - startTime;
		return df.format(duration / 1000.0);
	}
	

	private void printDatabaseMetaData(DatabaseMetaData mt) throws SQLException
	{
		System.out.println("+ Database Meta Data " + mt.getDatabaseProductName() + " " + mt.getDatabaseProductVersion());
		System.out.println("+ Driver: " + mt.getDriverName() + " " + mt.getDriverVersion());
		System.out.println("+ JDBC: " + mt.getJDBCMajorVersion() + "." + mt.getJDBCMinorVersion());
		System.out.println("+ T getMaxStatementLength (chars): " + mt.getMaxStatementLength());
		System.out.println("+ T getMaxStatements (no concurrent): " + mt.getMaxStatements());
	}
}

	

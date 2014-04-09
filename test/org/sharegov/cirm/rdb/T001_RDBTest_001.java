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
import java.util.Date;

import mjson.Json;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sharegov.cirm.rest.LegacyEmulator;
import org.sharegov.cirm.RequestScopeFilter;
import org.sharegov.cirm.rdb.RelationalOWLPersister;
import org.sharegov.cirm.rdb.RelationalStoreImpl;
import org.sharegov.cirm.utils.GenUtils;


public class T001_RDBTest_001
{
	
	public static final String TEST_JSON = "BULKYTRA.json";
	
//static String[] DBCONF = new String[] {
//		"jdbc:oracle:thin:@(DESCRIPTION =(ADDRESS = (PROTOCOL = TCP)(HOST = s0141409.miamidade.gov)(PORT = 1521))(ADDRESS = (PROTOCOL = TCP)(HOST = s0141734.miamidade.gov)(PORT = 1521))(ADDRESS = (PROTOCOL = TCP)(HOST = s0141872.miamidade.gov)(PORT = 1521))(LOAD_BALANCE = yes)(CONNECT_DATA =(SERVER = DEDICATED)(SERVICE_NAME = pcirm.miamidade.gov)))",
//		"oracle.jdbc.OracleDriver",
//		"cirmschm",
//		"pciaocirm" };
	
	static Json json1; 
	static String json1Str; 

	static LegacyEmulator le = new LegacyEmulator();
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		URL buylkytraJSONURL = T001_RDBTest_001.class.getResource(TEST_JSON);
		json1Str = GenUtils.readTextFile(new File(buylkytraJSONURL.getFile()));
		json1 = Json.read(json1Str);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception
	{
	}

	
	@Test
	public void testSaveLoadBusinessObjectOntology100K()
	{
		int nrOfBOsToInsert = 100 * 1000;
		RelationalOWLPersister.DBG = false;
		RelationalStoreImpl.DBG = false;
		
		System.out.println("TEST testSaveLoadBusinessObjectOntology100K STARTED. " + new Date());
		long startTime = System.currentTimeMillis();
		for (int i = 1; i < nrOfBOsToInsert; i = i + 10) {
			try
			{
				for (int j = i; j < i + 10; j++) {
					startTime = System.currentTimeMillis();
					Json bo = le.saveNewServiceRequest(json1Str);
					String saveTimeSecs = getDurationSecs(startTime);
					//bo: need ok.data.boid
					startTime = System.currentTimeMillis();
					long boid = bo.at("data").at("boid").asLong();
					Json bo2 = le.lookupServiceCase(boid);
					String loadTimeSecs = getDurationSecs(startTime);
					System.out.println("Time \t BOID \t SaveNewTimeSecs \t LookupTimeSecs \t BO2size \t Nr ");
					System.out.print(new Date() + "\t");
					System.out.print(boid + "\t");
					System.out.print(saveTimeSecs + "\t");
					System.out.print(loadTimeSecs + "\t");
					System.out.print(bo2.toString().length() + "\t");
					System.out.println(j);
				}
				Thread.sleep(1 * 1000);
				RequestScopeFilter.clear();
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
	
//	String failedSelect = "SELECT ID, IRI, IRI_TYPE FROM CIRM_IRI_VIEW WHERE IRI IN ('http://www.miamidade.gov/ontology#ServiceAnswer54943', 'http://www.miamidade.gov/ontology#ServiceAnswer54944', 'http://www.miamidade.gov/ontology#Street_Address54945', 'http://www.miamidade.gov/ontology#ServiceAnswer54940', 'http://www.miamidade.gov/ontology#ServiceAnswer54941', 'http://www.miamidade.gov/ontology#ServiceAnswer54942', 'http://www.miamidade.gov/ontology#ServiceAnswer54931', 'http://www.miamidade.gov/ontology#ServiceAnswer54930', 'http://www.miamidade.gov/ontology#ServiceAnswer54935', 'http://www.miamidade.gov/ontology#ServiceAnswer54934', 'http://www.miamidade.gov/ontology#ServiceAnswer54933', 'http://www.miamidade.gov/ontology#ServiceAnswer54932', 'http://www.miamidade.gov/ontology#ServiceAnswer54939', 'http://www.miamidade.gov/ontology#ServiceAnswer54938', 'http://www.miamidade.gov/ontology#ServiceAnswer54937', 'http://www.miamidade.gov/ontology#ServiceAnswer54936', 'http://www.miamidade.gov/ontology#ServiceAnswer54926', 'http://www.miamidade.gov/ontology#ServiceAnswer54928', 'http://www.miamidade.gov/ontology#ServiceAnswer54927', 'http://www.miamidade.gov/ontology#ServiceAnswer54929' )";
//	
//	@Test
//	public void testfailedSelectIRI_IN() throws Throwable
//	{
//		OperationService os = new OperationService();
//		Connection conn = os.getPersister().getStore().getConnection();
//		Statement stmt = conn.createStatement();
//		ResultSet rs = stmt.executeQuery(failedSelect);
//		int i = 0;
//		while (rs.next()) {
//			i++;
//			System.out.print(rs.getLong(1) + " ");
//			System.out.print(rs.getString(2) + " ");
//			System.out.println(rs.getString(3));
//		}
//		System.out.println("FOUND: " + i);
//	}
//	
//	int iCounter = -1001;
//	@Test
//	public void testInsertSelectCycle() throws Throwable
//	{
//		//OperationService os = new OperationService();
//		//RelationalStore store = os.getPersister().getStore();
//		RelationalStore store = new RelationalStore(DBCONF[0], DBCONF[1], DBCONF[2], DBCONF[3]);
//		ThreadLocalConnection conn = store.getConnection(); //.getDirectConnection();
//		//conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
//		printDatabaseMetaData(conn.getMetaData());
//		//conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
//		//printDatabaseMetaData(conn.getMetaData());
//		System.out.println("TEST WITH: " + conn);
//		Thread.currentThread().sleep(1000);
//		boolean shouldrepeat = false;
//		int n = 0;
//		do {
//			long startTime = System.currentTimeMillis();
//			if (shouldrepeat) 
//			{
//				//System.out.println("REPEATING AFTER CANNOT SERIALIZE");
//				System.out.print(".");
//				if (++n % 100 == 0)
//					System.out.println();
//			}
//			try {
////				if (conn.isClosed()) {
////					conn = store.getConnection(); //.getDirectConnection();
////					System.out.println("New Connection: " + conn.getDirectConnection());
////				}
//				//iCounter = 0;
//				for (int i = 0; i < 1000; i ++) {
//					List<OWLEntity> toInsert = new LinkedList<OWLEntity>();
//					for (int j = 0; j < 1000; j ++) {
//						OWLEntity newEntity = OWLUtils.individual(fullIri("Street_Address" +  iCounter--));
//						toInsert.add(newEntity);
//					}
//					System.out.println("Inserting # " + i + toInsert.iterator().next());
//					Set<OWLEntity> toInsertSet = new HashSet<OWLEntity>(toInsert);
//					store.insertNewEntities(toInsertSet, conn);
//					//store.insertNewEntitiesNoBatch(toInsertSet, conn);
//					//Thread.sleep(1000);
//					// insert finished					
////					conn.commit(); 
////					conn.close();
////					conn = store.getConnection();
//					Map<OWLEntity, Long> result = store.selectIDsAndEntitiesByIRIs(toInsertSet, conn, true);
//					conn.assertTopLevelMode();
//					//break;
//					//conn.commit();
//					//rollback();
//					//conn.close();
//				}
//				System.out.println("Duration: " + (System.currentTimeMillis() - startTime) / 1000.0 + " secs");
//				shouldrepeat = false;
//			} catch(Exception e ){
//				conn.rollback();
//				//System.out.println("Warning: " + conn.getWarnings());
//				conn.closeAndDiscard();
//				//conn.close();
//				conn = store.getConnection();
//				conn.assertTopLevelMode();
//				//e.printStackTrace();
//				shouldrepeat = store.isCannotSerializeException(e);
//				if (!shouldrepeat) throw e;
//			}
//		} while(shouldrepeat); 
//		//
//		conn.close();
//	}

	private void printDatabaseMetaData(DatabaseMetaData mt) throws SQLException
	{
		System.out.println("+ Database Meta Data " + mt.getDatabaseProductName() + " " + mt.getDatabaseProductVersion());
		System.out.println("+ Driver: " + mt.getDriverName() + " " + mt.getDriverVersion());
		System.out.println("+ JDBC: " + mt.getJDBCMajorVersion() + "." + mt.getJDBCMinorVersion());
		System.out.println("+ T getMaxStatementLength (chars): " + mt.getMaxStatementLength());
		System.out.println("+ T getMaxStatements (no concurrent): " + mt.getMaxStatements());
		//int[] rsType = new int[] {ResultSet.TYPE_FORWARD_ONLY, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.TYPE_SCROLL_SENSITIVE };
//		System.out.println("+ T ResultSet.TYPE_FORWARD_ONLY: ");
//		System.out.println("+ T ownDeletesAreVisible: " + mt.ownDeletesAreVisible(ResultSet.TYPE_FORWARD_ONLY));
//		System.out.println("+ T ownInsertsAreVisible: " + mt.ownInsertsAreVisible(ResultSet.TYPE_FORWARD_ONLY));
//		System.out.println("+ T ownUpdatesAreVisible: " + mt.ownUpdatesAreVisible(ResultSet.TYPE_FORWARD_ONLY));
//		System.out.println("+ T TYPE_SCROLL_INSENSITIVE: ");
//		System.out.println("+ T ownDeletesAreVisible: " + mt.ownDeletesAreVisible(ResultSet.TYPE_SCROLL_INSENSITIVE));
//		System.out.println("+ T ownInsertsAreVisible: " + mt.ownInsertsAreVisible(ResultSet.TYPE_SCROLL_INSENSITIVE));
//		System.out.println("+ T ownUpdatesAreVisible: " + mt.ownUpdatesAreVisible(ResultSet.TYPE_SCROLL_INSENSITIVE));
//		System.out.println("+ T TYPE_SCROLL_SENSITIVE: ");
//		System.out.println("+ T ownDeletesAreVisible: " + mt.ownDeletesAreVisible(ResultSet.TYPE_SCROLL_SENSITIVE));
//		System.out.println("+ T ownInsertsAreVisible: " + mt.ownInsertsAreVisible(ResultSet.TYPE_SCROLL_SENSITIVE));
//		System.out.println("+ T ownUpdatesAreVisible: " + mt.ownUpdatesAreVisible(ResultSet.TYPE_SCROLL_SENSITIVE));
		//mt.supportsResultSetConcurrency(type, ResultSet.)
		
	}
}

	

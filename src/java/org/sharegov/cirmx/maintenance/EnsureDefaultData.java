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
package org.sharegov.cirmx.maintenance;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;

import mjson.Json;

import org.sharegov.cirm.rest.OperationService;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.rdb.RelationalStore;
import org.sharegov.cirm.rdb.RelationalStoreExt;
import org.sharegov.cirm.rdb.RelationalStoreImpl;
import org.sharegov.cirm.rdb.ThreadLocalConnection;

/**
 * Fills default data for AnwerhubMarqueee and PopularSearchesList, and it also inserts and deletes dummy rows in each table of the horizontal and vertical CIRM schema.
 * The DB it goes to is defined in County ontology. 
 * 
 * We call inserting and deleting dummy rows "priming" the database and we suspect the priming to relieve a bug when using serializable transactions with an Oracle database.
 * All inserts will be done with negative ID values and all deleted will be on rows with negative ID values.
 * 
 * Run this class as Java program with one parameter:
 * EnsureDefaultData -data  ... Reads and saves AnswerHubMarqueeList.json and PopularSearchesList.json.
 * EnsureDefaultData -prime ... Inserts and deletes rows in each database table.
 * EnsureDefaultData -both  ... Executes both, prime and data.
 * 
 * To get latest Anwerhub or Popsearches from inside Browser, run: 
 * JSON.stringify(cirm.op.get('/individual/PopularSearchesList')) 
 * JSON.stringify(cirm.op.get('/individual/AnswerHubMarqueeList'))
 * @author Thomas Hilpold
 *
 */
public class EnsureDefaultData
{
	public final static String ResDir = ""; //"org//sharegov//cirmx//maintenance//"; 
	public final static String AnswerHubMarqueeList = ResDir + "AnswerHubMarqueeListV04.json"; 
	public final static String PopularSearchesList = ResDir + "PopularSearchesListV04.json"; 
	public final static int DEFAULT_ROWS_TO_INSERT = 50000; 

	//Vertical: 1 CIRM_IRI_TYPE 2 CIRM_IRI, 3-7 CIRMOWL_DATA_VAL_1-5, 8 CIRM_CLASSIFICATION, 9 CIRM_OBJECT_PROPERTY, 10 CIRM_DATA_PROPERTY 
	public final static String[] TABLESV = new String[] {
		"CIRM_IRI_TYPE",  //0
		"CIRM_IRI", 
		"CIRM_OWL_DATA_VAL_CLOB", 
		"CIRM_OWL_DATA_VAL_DATE", 
		"CIRM_OWL_DATA_VAL_DOUBLE",
		"CIRM_OWL_DATA_VAL_INTEGER", 
		"CIRM_OWL_DATA_VAL_STRING", 
		"CIRM_CLASSIFICATION", 
		"CIRM_OWL_OBJECT_PROPERTY", 
		"CIRM_OWL_DATA_PROPERTY" //9
	};
	
	//Horizontal: H1 CIRM_SERVICE_CALL, H2 CIRM_SERVICE_ACTION, H3 CIRM_MDC_ADDRESS, H4 CIRM_SR_REQUESTS, H5 CIRM_SR_ACTIVITY, H6 CIRM_SR_ACTOR, 
	public final static String[] TABLESH = new String[] {
		"CIRM_SERVICE_CALL", //0
		"CIRM_SERVICE_ACTION",
		"CIRM_MDC_ADDRESS",
		"CIRM_GIS_INFO",
		"CIRM_SR_REQUESTS",
		"CIRM_SR_ACTIVITY",
		"CIRM_SR_ACTOR",
		"CIRM_SRREQ_SRACTOR" //7
	};
	
	public final static String INSERTV_01_CIRM_IRI_TYPE = "INSERT INTO " + TABLESV[0] + " VALUES(?,?) "; 
	public final static String INSERTV_02_CIRM_IRI = "INSERT INTO " + TABLESV[1] + " VALUES(?,?,?) "; 
	public final static String INSERTV_03_CIRM_VAL_CLOB = "INSERT INTO " + TABLESV[2] + " VALUES(?,?,?,?) ";
	public final static String INSERTV_04_CIRM_VAL_DATE = "INSERT INTO " + TABLESV[3] + " VALUES(?,?) "; 
	public final static String INSERTV_05_CIRM_VAL_DOUBLE = "INSERT INTO " + TABLESV[4] + " VALUES(?,?) "; 
	public final static String INSERTV_06_CIRM_VAL_INTEGER = "INSERT INTO " + TABLESV[5] + " VALUES(?,?) "; 
	public final static String INSERTV_07_CIRM_VAL_STRING = "INSERT INTO " + TABLESV[6] + " VALUES(?,?) "; 
	public final static String INSERTV_08_CIRM_CLASSIFICATION = "INSERT INTO " + TABLESV[7] + " VALUES(?,?,?,?) "; 
	public final static String INSERTV_09_CIRM_OBJECT_PROP = "INSERT INTO " + TABLESV[8] + " VALUES(?,?,?,?,?) "; 
	public final static String INSERTV_10_CIRM_DATA_PROP = "INSERT INTO " + TABLESV[9] + " VALUES(?,?,?,?,?,?) "; 

	public final static String INSERTH_01_CIRM_SERVICE_CALL = "INSERT INTO "+ TABLESH[0] + " VALUES(?,?,?,?,?) "; 
	public final static String INSERTH_02_CIRM_SERVICE_ACTION = "INSERT INTO "+ TABLESH[1] + " VALUES(?,?,?,?) "; 
	public final static String INSERTH_03_CIRM_MDC_ADDRESS = "INSERT INTO "+ TABLESH[2] + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) ";
	public final static String INSERTH_04_CIRM_GIS_INFO = "INSERT INTO "+ TABLESH[3] + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "; 
	public final static String INSERTH_05_CIRM_SR_REQUESTS = "INSERT INTO "+ TABLESH[4] + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "; 
	public final static String INSERTH_06_CIRM_SR_ACTIVITY = "INSERT INTO "+ TABLESH[5] + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?) "; 
	public final static String INSERTH_07_CIRM_SR_ACTOR = "INSERT INTO "+ TABLESH[6] + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "; 
	public final static String INSERTH_08_CIRM_SRREQ_SRACTOR = "INSERT INTO "+ TABLESH[7] + " VALUES(?,?) "; 

	public final static String DELETEV_01_CIRM_IRI_TYPE = "DELETE FROM " + TABLESV[0] + " WHERE ID < 0 "; 
	public final static String DELETEV_02_CIRM_IRI = "DELETE FROM " + TABLESV[1] + " WHERE ID < 0 "; 
	public final static String DELETEV_03_CIRM_VAL_CLOB = "DELETE FROM " + TABLESV[2] + " WHERE ID < 0 ";
	public final static String DELETEV_04_CIRM_VAL_DATE = "DELETE FROM " + TABLESV[3] + " WHERE ID < 0 "; 
	public final static String DELETEV_05_CIRM_VAL_DOUBLE = "DELETE FROM " + TABLESV[4] + " WHERE ID < 0 "; 
	public final static String DELETEV_06_CIRM_VAL_INTEGER = "DELETE FROM " + TABLESV[5] + " WHERE ID < 0 "; 
	public final static String DELETEV_07_CIRM_VAL_STRING = "DELETE FROM " + TABLESV[6] + " WHERE ID < 0 "; 
	public final static String DELETEV_08_CIRM_CLASSIFICATION = "DELETE FROM " + TABLESV[7] + " WHERE SUBJECT < 0 "; 
	public final static String DELETEV_09_CIRM_OBJECT_PROP = "DELETE FROM " + TABLESV[8] + " WHERE SUBJECT < 0 "; 
	public final static String DELETEV_10_CIRM_DATA_PROP = "DELETE FROM " + TABLESV[9] + " WHERE SUBJECT < 0 "; 

	public final static String DELETEH_01_CIRM_SERVICE_CALL = "DELETE FROM "+ TABLESH[0] + " WHERE SERVICE_CALL_ID < 0 "; 
	public final static String DELETEH_02_CIRM_SERVICE_ACTION = "DELETE FROM "+ TABLESH[1] + " WHERE SERVICE_CALL_ID < 0 "; 
	public final static String DELETEH_03_CIRM_MDC_ADDRESS = "DELETE FROM "+ TABLESH[2] + " WHERE ADDRESS_ID < 0 ";
	public final static String DELETEH_04_CIRM_GIS_INFO = "DELETE FROM "+ TABLESH[3] + " WHERE ID < 0 "; 
	public final static String DELETEH_05_CIRM_SR_REQUESTS = "DELETE FROM "+ TABLESH[4] + " WHERE SR_REQUEST_ID < 0 "; 
	public final static String DELETEH_06_CIRM_SR_ACTIVITY = "DELETE FROM "+ TABLESH[5] + " WHERE ACTIVITY_ID < 0 "; 
	public final static String DELETEH_07_CIRM_SR_ACTOR = "DELETE FROM "+ TABLESH[6] + " WHERE SR_ACTOR_ID < 0 "; 
	public final static String DELETEH_08_CIRM_SRREQ_SRACTOR = "DELETE FROM "+ TABLESH[7] + " WHERE SR_REQUEST_ID < 0 "; 
	
	private static OperationService operationService;
	
	private static OperationService getOperationService() {
		if (operationService == null) {
			try {
				StartUp.main(new String[0]);
				operationService = new OperationService();
			} catch (Exception e) {
				throw new RuntimeException("Could not start up.", e);
			}
		}
		return operationService;
	}
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception
	{
		if (args.length != 1) help();
		String parameter = args[0];
		if (parameter.equalsIgnoreCase("-data")) {
			ensureDefaultData();
		} else if (parameter.equalsIgnoreCase("-prime")) {
			primeDatabaseTables();
		} else if (parameter.equalsIgnoreCase("-both")) {
			ensureDefaultData();
			primeDatabaseTables();
		} else {
			help();
		}
	}
	
	public static void help() {
		System.out.println("EnsureDefaultData Usage:");
		System.out.println("-data  ... Reads and saves AnswerHubMarqueeList.json and PopularSearchesList.json.");
		System.out.println("-prime ... Inserts and deletes rows in each database table.");
		System.out.println("-both  ... Executes both, prime and data.");
		System.exit(-1);
	}
	
	/**
	 * Inserts and deletes DEFAULT_ROWS_TO_INSERT rows with negative IDs.
	 * 
	 * @throws Exception
	 */
	public static void primeDatabaseTables() throws Exception {
		RelationalStoreImpl.TRANSACTION_ISOLATION_LEVEL = Connection.TRANSACTION_READ_COMMITTED;
		RelationalStoreExt store = Refs.defaultRelationalStoreExt.resolve();
		ThreadLocalConnection conn = store.getConnection();
		conn.assertTopLevelMode();
		deleteInsertedRows(conn);
		insertRowsIntoEachTable(conn, DEFAULT_ROWS_TO_INSERT);
		deleteInsertedRows(conn);
		conn.close();
		RelationalStoreImpl.TRANSACTION_ISOLATION_LEVEL = Connection.TRANSACTION_SERIALIZABLE;
	}
	
	/**
	 * Inserts rows using negative IDs from [-1, 0-nrOfRows] 
	 * @param nrOfRows
	 * @throws Exception
	 */
	public static void insertRowsIntoEachTable(Connection conn, int nrOfRows) throws Exception {
		//Insertion order:
		//Vertical: 1 CIRM_IRI, 2-6 CIRMOWL_DATA_VAL_1-5, 7 CIRM_CLASSIFICATION, 8 CIRM_OBJECT_PROPERTY, 9 CIRM_DATA_PROPERTY, 10 CIRM_IRI_TYPE
		//Horizontal: H1 CIRM_SERVICE_CALL, H2 CIRM_SERVICE_ACTION, H3 CIRM_MDC_ADDRESS, H4 CIRM_SR_REQUESTS, H5 CIRM_SR_ACTIVITY, H6 CIRM_SR_ACTOR, 
		// H7 CIRM_SRREQ_SRACTOR
		//Vertical
		int firstId = -1;
		int lastId = 0 - nrOfRows;
		PreparedStatement ps;
		System.out.println("Inserting rows " + firstId + " to " + lastId + " in each table. Total rows per table: " + nrOfRows);
		System.out.println("Vertical Schema start:");
		// V01
		System.out.print("Table: " + INSERTV_01_CIRM_IRI_TYPE + " ..");
		ps = conn.prepareStatement(INSERTV_01_CIRM_IRI_TYPE);
		for (int id = firstId; id >= lastId; id --) {
			ps.setInt(1, id);
			ps.setString(2, "String" + id);
			ps.addBatch();
		}
		ps.executeBatch();
		conn.commit();
		ps.close();
		System.out.println(".Done.");
		// V02
		System.out.print("Table: " + INSERTV_02_CIRM_IRI + " ..");
		ps = conn.prepareStatement(INSERTV_02_CIRM_IRI);
		for (int id = firstId; id >= lastId; id --) {
			ps.setInt(1, id);
			ps.setString(2, "String" + id);
			ps.setInt(3, id);
			ps.addBatch();
		}
		ps.executeBatch();
		conn.commit();
		ps.close();
		System.out.println(".Done.");
		// V03
		System.out.print("Table: " + INSERTV_03_CIRM_VAL_CLOB + " ..");
		ps = conn.prepareStatement(INSERTV_03_CIRM_VAL_CLOB);
		for (int id = firstId; id >= lastId; id --) {
			ps.setInt(1, id);
			ps.setString(2, "String" + id);
			ps.setString(3, "String" + id);
			ps.setNull(4, Types.CLOB);
			ps.addBatch();
		}
		ps.executeBatch();
		conn.commit();
		ps.close();
		System.out.println(".Done.");
		// V04
		System.out.print("Table: " + INSERTV_04_CIRM_VAL_DATE + " ..");
		ps = conn.prepareStatement(INSERTV_04_CIRM_VAL_DATE);
		for (int id = firstId; id >= lastId; id --) {
			ps.setInt(1, id);
			ps.setTimestamp(2, new Timestamp(new Date().getTime() + id * 100));
			ps.addBatch();
		}
		ps.executeBatch();
		conn.commit();
		ps.close();
		System.out.println(".Done.");
		// V05
		System.out.print("Table: " + INSERTV_05_CIRM_VAL_DOUBLE + " ..");
		ps = conn.prepareStatement(INSERTV_05_CIRM_VAL_DOUBLE);
		for (int id = firstId; id >= lastId; id --) {
			ps.setInt(1, id);
			ps.setDouble(2, id * .99);
			ps.addBatch();
		}
		ps.executeBatch();
		conn.commit();
		ps.close();
		System.out.println(".Done.");
		// V06
		System.out.print("Table: " + INSERTV_06_CIRM_VAL_INTEGER + " ..");
		ps = conn.prepareStatement(INSERTV_06_CIRM_VAL_INTEGER);
		for (int id = firstId; id >= lastId; id --) {
			ps.setInt(1, id);
			ps.setInt(2, id);
			ps.addBatch();
		}
		ps.executeBatch();
		conn.commit();
		ps.close();
		System.out.println(".Done.");
		// V07
		System.out.print("Table: " + INSERTV_07_CIRM_VAL_STRING + " ..");
		ps = conn.prepareStatement(INSERTV_07_CIRM_VAL_STRING);
		for (int id = firstId; id >= lastId; id --) {
			ps.setInt(1, id);
			ps.setString(2, "String" + id);
			ps.addBatch();
		}
		ps.executeBatch();
		conn.commit();
		ps.close();
		System.out.println(".Done.");
		// V08
		System.out.print("Table: " + INSERTV_08_CIRM_CLASSIFICATION + " ..");
		ps = conn.prepareStatement(INSERTV_08_CIRM_CLASSIFICATION);
		for (int id = firstId; id >= lastId; id --) {
			ps.setInt(1, id);
			ps.setInt(2, id);
			ps.setTimestamp(3, new Timestamp(new Date().getTime()));
			ps.setTimestamp(4, new Timestamp(new Date().getTime()));
			ps.addBatch();
		}
		ps.executeBatch();
		conn.commit();
		ps.close();
		System.out.println(".Done.");
		// V09
		System.out.print("Table: " + INSERTV_09_CIRM_OBJECT_PROP + " ..");
		ps = conn.prepareStatement(INSERTV_09_CIRM_OBJECT_PROP);
		for (int id = firstId; id >= lastId; id --) {
			ps.setInt(1, id);
			ps.setInt(2, id);
			ps.setInt(3, id);
			ps.setTimestamp(4, new Timestamp(new Date().getTime()));
			ps.setTimestamp(5, new Timestamp(new Date().getTime()));
			ps.addBatch();
		}
		ps.executeBatch();
		conn.commit();
		ps.close();
		System.out.println(".Done.");
		// V10
		System.out.print("Table: " + INSERTV_10_CIRM_DATA_PROP + " ..");
		ps = conn.prepareStatement(INSERTV_10_CIRM_DATA_PROP);
		for (int id = firstId; id >= lastId; id --) {
			ps.setInt(1, id);
			ps.setInt(2, id);
			ps.setInt(3, id);
			ps.setInt(4, id);
			ps.setTimestamp(5, new Timestamp(new Date().getTime()));
			ps.setTimestamp(6, new Timestamp(new Date().getTime()));
			ps.addBatch();
		}
		ps.executeBatch();
		conn.commit();
		ps.close();
		System.out.println(".Done.");
		System.out.println("Vertical Schema insert finished.");
		System.out.println("--------------------------------------------------------");
		System.out.println("Horizontal Schema insert start.");
		// H1
		System.out.print("Table: " + INSERTH_01_CIRM_SERVICE_CALL + " ..");
		ps = conn.prepareStatement(INSERTH_01_CIRM_SERVICE_CALL);
		for (int id = firstId; id >= lastId; id --) {
			ps.setInt(1, id);
			ps.setString(2, "Agent" + id);
			ps.setTimestamp(3, new Timestamp(new Date().getTime()));
			ps.setTimestamp(4, new Timestamp(new Date().getTime() + 1000));
			ps.setString(5, "0123456789");
			ps.addBatch();
		}
		ps.executeBatch();
		conn.commit();
		ps.close();
		System.out.println(".Done.");
		// H2
		System.out.print("Table: " + INSERTH_02_CIRM_SERVICE_ACTION + " ..");
		ps = conn.prepareStatement(INSERTH_02_CIRM_SERVICE_ACTION);
		for (int id = firstId; id >= lastId; id --) {
			ps.setInt(1, id);
			ps.setString(2, "Name" + id);
			ps.setString(3, "Value" + id);
			ps.setTimestamp(4, new Timestamp(new Date().getTime()));
			ps.addBatch();
		}
		ps.executeBatch();
		conn.commit();
		ps.close();
		System.out.println(".Done.");
		// H3
		System.out.print("Table: " + INSERTH_03_CIRM_MDC_ADDRESS + " ..");
		ps = conn.prepareStatement(INSERTH_03_CIRM_MDC_ADDRESS);
		for (int id = firstId; id >= lastId; id --) {
			ps.setInt(1, id);
			ps.setString(2, "fa" + id);
			ps.setInt(3, id);
			ps.setString(4, "sn" + id);
			ps.setString(5, "snp" + id);
			ps.setString(6, "sns" + id);
			ps.setString(7, "unit" + id);
			ps.setInt(8, id);
			ps.setInt(9, id);
			ps.setInt(10, id);
			ps.setDouble(11, id);
			ps.setDouble(12, id);
			ps.setString(13, "loc" + id);
			ps.addBatch();
		}
		ps.executeBatch();
		conn.commit();
		ps.close();
		System.out.println(".Done.");
		// H4
		System.out.print("Table: " + INSERTH_04_CIRM_GIS_INFO + " ..");
		ps = conn.prepareStatement(INSERTH_04_CIRM_GIS_INFO);
		for (int id = firstId; id >= lastId; id --) {
			ps.setInt(1, id);
			ps.setString(2, "hash" + id);
			ps.setString(3, "{ \"data\":" + id + "}");
			ps.setString(4, "SWR" + id );
			ps.setString(5, "MUNICIP" + id );
			ps.setString(6, "TEAMOFFC" + id );
			ps.setString(7, "MIACODE" + id );
			ps.setDouble(8,  id * .99 );
			ps.setString(9, "MUN" + id );
			ps.setInt(10,  id );
			ps.setInt(11,  id );
			ps.setInt(12,  id );
			ps.setInt(13,  id );
			ps.setString(14, "MIACOM" + id );
			ps.setString(15, "MIAGARB" + id );
			ps.setString(16, "MIAGARB2" + id );
			ps.setString(17, "m" + id );
			ps.setInt(18,  id );
			ps.setString(19, "r" + id );
			ps.setInt(20,  id );
			ps.setString(21, "d" + id );
			ps.setInt(22,  id );
			ps.setInt(23,  id );
			ps.setString(24, "m" + id );
			ps.setString(25, "STLGHT" + id );
			ps.setInt(26,  id );
			ps.addBatch();
		}
		ps.executeBatch();
		conn.commit();
		ps.close();
		System.out.println(".Done.");
		// H5
		System.out.print("Table: " + INSERTH_05_CIRM_SR_REQUESTS + " ..");
		ps = conn.prepareStatement(INSERTH_05_CIRM_SR_REQUESTS);
		for (int id = firstId; id >= lastId; id --) {
			ps.setInt(1, id);
			ps.setString(2, "" + id);
			ps.setString(3, "" + id);
			ps.setString(4, "" + id);
			ps.setString(5, "" + id);
			ps.setInt(6, id);
			ps.setInt(7, id);
			ps.setDouble(8, id * .99);
			ps.setDouble(9, id * .99);
			ps.setString(10, "" + id);
			ps.setString(11, "" + id);
			ps.setTimestamp(12, new Timestamp(new Date().getTime()));
			ps.setString(13, "" + id);
			ps.setTimestamp(14, new Timestamp(new Date().getTime() + 1000));
			ps.setString(15, "" + id);
			ps.setTimestamp(16, new Timestamp(new Date().getTime() + 1000));
			ps.addBatch();
		}
		ps.executeBatch();
		conn.commit();
		ps.close();
		System.out.println(".Done.");
		// H6
		System.out.print("Table: " + INSERTH_06_CIRM_SR_ACTIVITY + " ..");
		ps = conn.prepareStatement(INSERTH_06_CIRM_SR_ACTIVITY);
		for (int id = firstId; id >= lastId; id --) {
			ps.setInt(1, id);
			ps.setInt(2, id);
			ps.setString(3, "" + id);
			ps.setString(4, "" + id);
			ps.setString(5, "" + id);
			ps.setString(6, "" + id);
			ps.setTimestamp(7, new Timestamp(new Date().getTime()));
			ps.setTimestamp(8, new Timestamp(new Date().getTime() + 1000));
			ps.setString(9, "" + id);
			ps.setTimestamp(10, new Timestamp(new Date().getTime() + 1000));
			ps.setString(11, "" + id);
			ps.setTimestamp(12, new Timestamp(new Date().getTime() + 1000));
			ps.addBatch();
		}
		ps.executeBatch();
		conn.commit();
		ps.close();
		System.out.println(".Done.");
		// H7
		System.out.print("Table: " + INSERTH_07_CIRM_SR_ACTOR + " ..");
		ps = conn.prepareStatement(INSERTH_07_CIRM_SR_ACTOR);
		for (int id = firstId; id >= lastId; id --) {
			ps.setInt(1, id);
			ps.setString(2, "" + id);
			ps.setString(3, "" + id);
			ps.setString(4, "" + id);
			ps.setString(5, "" + id);
			ps.setString(6, "" + id);
			ps.setString(7, "" + id);
			ps.setString(8, "" + id);
			ps.setString(9, "" + id);
			ps.setString(10, "" + id);
			ps.setInt(11, id);
			ps.setString(12, "" + id);
			ps.setString(13, "" + id);
			ps.setString(14, "" + id);
			ps.setString(15, "" + id);
			ps.setTimestamp(16, new Timestamp(new Date().getTime()));
			ps.setString(17, "" + id);
			ps.setTimestamp(18, new Timestamp(new Date().getTime() + 1000));
			ps.addBatch();
		}
		ps.executeBatch();
		conn.commit();
		ps.close();
		System.out.println(".Done.");
		// H8
		System.out.print("Table: " + INSERTH_08_CIRM_SRREQ_SRACTOR + " ..");
		ps = conn.prepareStatement(INSERTH_08_CIRM_SRREQ_SRACTOR);
		for (int id = firstId; id >= lastId; id --) {
			ps.setInt(1, id);
			ps.setInt(2, id);
			ps.addBatch();
		}
		ps.executeBatch();
		conn.commit();
		ps.close();
		System.out.println(".Done.");
		//
		System.out.println("Horizontal Schema insert finished.");
		System.out.println("--------------------------------------------------------");
	}
	
	public static void deleteInsertedRows(Connection conn) throws SQLException{
		//Delete from horizontal inverse insert order
		Statement deleteStmt = conn.createStatement();
		System.out.println("Delete all with negative id start.");
		System.out.println("Horizontal Schema delete start.");
		System.out.println(DELETEH_08_CIRM_SRREQ_SRACTOR);
		deleteStmt.executeUpdate(DELETEH_08_CIRM_SRREQ_SRACTOR);
		conn.commit();
		System.out.println(DELETEH_07_CIRM_SR_ACTOR);
		deleteStmt.executeUpdate(DELETEH_07_CIRM_SR_ACTOR);
		conn.commit();
		System.out.println(DELETEH_06_CIRM_SR_ACTIVITY);
		deleteStmt.executeUpdate(DELETEH_06_CIRM_SR_ACTIVITY);
		conn.commit();
		System.out.println(DELETEH_05_CIRM_SR_REQUESTS);
		deleteStmt.executeUpdate(DELETEH_05_CIRM_SR_REQUESTS);
		conn.commit();
		System.out.println(DELETEH_04_CIRM_GIS_INFO);
		deleteStmt.executeUpdate(DELETEH_04_CIRM_GIS_INFO);
		conn.commit();
		System.out.println(DELETEH_03_CIRM_MDC_ADDRESS);
		deleteStmt.executeUpdate(DELETEH_03_CIRM_MDC_ADDRESS);
		conn.commit();
		System.out.println(DELETEH_02_CIRM_SERVICE_ACTION);
		deleteStmt.executeUpdate(DELETEH_02_CIRM_SERVICE_ACTION);
		conn.commit();
		System.out.println(DELETEH_01_CIRM_SERVICE_CALL);
		deleteStmt.executeUpdate(DELETEH_01_CIRM_SERVICE_CALL);
		conn.commit();
		System.out.println("Horizontal Schema delete finished.");
		System.out.println("Vertical Schema delete start.");
		System.out.println(DELETEV_10_CIRM_DATA_PROP);
		deleteStmt.executeUpdate(DELETEV_10_CIRM_DATA_PROP);
		conn.commit();
		System.out.println(DELETEV_09_CIRM_OBJECT_PROP);
		deleteStmt.executeUpdate(DELETEV_09_CIRM_OBJECT_PROP);
		conn.commit();
		System.out.println(DELETEV_08_CIRM_CLASSIFICATION);
		deleteStmt.executeUpdate(DELETEV_08_CIRM_CLASSIFICATION);
		conn.commit();
		System.out.println(DELETEV_07_CIRM_VAL_STRING);
		deleteStmt.executeUpdate(DELETEV_07_CIRM_VAL_STRING);
		conn.commit();
		System.out.println(DELETEV_06_CIRM_VAL_INTEGER);
		deleteStmt.executeUpdate(DELETEV_06_CIRM_VAL_INTEGER);
		conn.commit();
		System.out.println(DELETEV_05_CIRM_VAL_DOUBLE);
		deleteStmt.executeUpdate(DELETEV_05_CIRM_VAL_DOUBLE);
		conn.commit();
		System.out.println(DELETEV_04_CIRM_VAL_DATE);
		deleteStmt.executeUpdate(DELETEV_04_CIRM_VAL_DATE);
		conn.commit();
		System.out.println(DELETEV_03_CIRM_VAL_CLOB);
		deleteStmt.executeUpdate(DELETEV_03_CIRM_VAL_CLOB);
		conn.commit();
		System.out.println(DELETEV_02_CIRM_IRI);
		deleteStmt.executeUpdate(DELETEV_02_CIRM_IRI);
		conn.commit();
		System.out.println(DELETEV_01_CIRM_IRI_TYPE);
		deleteStmt.executeUpdate(DELETEV_01_CIRM_IRI_TYPE);
		conn.commit();
		deleteStmt.close();
		System.out.println("Vertical Schema delete finished.");
		System.out.println("Delete finished.");
		
	}

	public static void ensureDefaultData() throws Exception {
		RelationalStoreImpl.TRANSACTION_ISOLATION_LEVEL = Connection.TRANSACTION_READ_COMMITTED;
		URL answerHubJsonUrl = EnsureDefaultData.class.getResource(AnswerHubMarqueeList);
		URL popularSearchJsonUrl = EnsureDefaultData.class.getResource(PopularSearchesList);
		String answerHubJsonStr = readAsStringUTF8(answerHubJsonUrl);
		String popularSearchesStr = readAsStringUTF8(popularSearchJsonUrl);
		Json answerHubJson = Json.read(answerHubJsonStr);
		Json popularSearchJson = Json.read(popularSearchesStr);
		OperationService opService = getOperationService();
		System.out.println("ENSURING DEFAULT DATA...");
		opService.saveIndividual("AnswerHubMarqueeList", answerHubJson);
		opService.saveIndividual("PopularSearchesList", popularSearchJson);
		System.out.println();
		System.out.println("DONE: DEFAULT DATA ENSURED");
		RelationalStoreImpl.TRANSACTION_ISOLATION_LEVEL = Connection.TRANSACTION_SERIALIZABLE;
	}

	public static String readAsStringUTF8(URL url) {
		StringBuffer str = new StringBuffer(10000);
		String cur;
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"));
			do {
				cur = br.readLine();
				if (cur != null) {
					str.append(cur);
				}
			} while(cur != null);			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return str.toString();
	}
}

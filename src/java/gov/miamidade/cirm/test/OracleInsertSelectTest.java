/**
 * 
 */
package gov.miamidade.cirm.test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import oracle.jdbc.pool.OracleDataSource;

/**
 * This test demonstates a suspected Oracle 11g2 bug as follows:
 * 
 * 
 * @author Thomas Hilpold
 *
 */
public class OracleInsertSelectTest
{
	public static final String CONN_URL = "jdbc:oracle:thin:@localhost:1521:xe";
	public static final String CONN_USER = "cirmschm";
	public static final String CONN_PASS = "cirmschm";
	
	public static final String TABLE = "CIRM_OWL_DATA_VAL_INTEGER";
	public static final String TABLE_INSERT = "INSERT INTO " + TABLE + " VALUES(?,?) ";
	public static final String TABLE_SELECT = "SELECT * FROM " + TABLE + " WHERE ID = ? ";
	public static final String TABLE_DELETE = "DELETE FROM " + TABLE + " ";
	
	public static final String TABLE_DROP = " DROP TABLE " + TABLE + " ";
	public static final String TABLE_CREATE = " create table " + TABLE  + " ( "
												+ " ID number(19,0) NOT NULL, " 
												+ " VALUE_INTEGER INTEGER NOT NULL, "
												+ " primary key (ID), "
												+ " unique(VALUE_INTEGER) "
												//+ " ) ";
												+ " ) SEGMENT CREATION IMMEDIATE PCTFREE 30 PCTUSED 40 INITRANS 100 ROWDEPENDENCIES ";
	
	public enum TestMode {
		TEST_ROLLBACK_AFTER, TEST_COMMIT_AFTER, TEST_ROLLBACK_EACH, TEST_COMMIT_EACH;
	}
		
	private DataSource datasource;
	private int tempID = 0;

	public int getNextTempId() {
		return tempID++;
	}

	public void setNextTempId(int nextId) {
		tempID = nextId;
	}
	
	public DataSource getDataSource() throws SQLException {
		if (datasource == null) {
			OracleDataSource ods = new OracleDataSource();
			ods.setURL(CONN_URL);
			ods.setUser(CONN_USER);
			ods.setPassword(CONN_PASS);
			//ods.setConnectionProperties(arg0)
			datasource = ods;
			Connection conn = ods.getConnection();
			printDatabaseMetaData(conn.getMetaData());
			conn.close();
		} 
		return datasource;
	}

	
	public Connection getConnection() throws SQLException {
		Connection conn = getDataSource().getConnection();
		conn.setAutoCommit(false);
		conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
		return conn;
	}
	
	public OracleInsertSelectTest() {
		try
		{
			Class.forName("oracle.jdbc.driver.OracleDriver").newInstance();
		}
		catch (InstantiationException e)
		{
			e.printStackTrace();
		}
		catch (IllegalAccessException e)
		{
			e.printStackTrace();
		}
		catch (ClassNotFoundException e)
		{
			e.printStackTrace();
		}
	}
	
	public void dropCreateTable() throws SQLException {		
		Connection conn = getConnection();
		try {
			Statement stmt = conn.createStatement();
			stmt.executeUpdate(TABLE_DROP);
			stmt.close();
		} catch (SQLException e ) {
			System.out.println("Drop table failed ignored: " + e);
		}
		Statement stmt = conn.createStatement();
		stmt.executeUpdate(TABLE_CREATE);
		conn.commit();
		stmt.close();
		conn.close();
		System.out.println("Table dropped and re-created.");
	}
	
	/**
	 * Inserts a given number of generated rows in the TABLE and deletes them afterwards.
	 * This is known to prime the table in SERIALIZABLE mode and mysticly avoids subsequent errors.
	 * 
	 * @param number
	 */
	public void insertAndDeleteRows(int number) {
		boolean shouldRepeat = false;
		Connection conn = null;
		do {
			try
			{
				conn = getConnection();
				PreparedStatement insert = conn.prepareStatement(TABLE_INSERT);
				System.out.println("Inserting " + number + " rows with commit.");
				for (int i = 0; i < number; i++) {
					insert.setInt(1, i);
					insert.setInt(2, 2 * i);
					int updatedRows = insert.executeUpdate();
					conn.commit();
				}
				insert.close();
				System.out.println("Deleting " + number + " rows with commit.");
				Statement delete = conn.createStatement();
				delete.executeUpdate(TABLE_DELETE);
				conn.commit();
				delete.close();
				conn.close();
				shouldRepeat = false;
			}
			catch (SQLException e)
			{
				shouldRepeat = isCannotSerializeException(e);
				if (!shouldRepeat) e.printStackTrace();
				try {
					conn.rollback();
				} catch (SQLException e2) {
					e2.printStackTrace();
				}
			} finally {
				try {
					conn.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
			if (shouldRepeat) System.out.println(" - Repeating I&D -");
		} while (shouldRepeat);

	}
	
	/**
	 * Tests inserting and selecting  
	 * @param number
	 * @param testmode
	 */
	public void test(int number, TestMode testmode) {
		boolean shouldRepeat = false;
		Connection conn = null;
		int startTempId = getNextTempId();
		do {
			setNextTempId(startTempId);
			try
			{
				conn = getConnection();
				System.out.print("Testing " + number + " Inserts and Selects mode: "+ testmode + " ...");
				for (int i = 0; i < number; i++) {
					// 1. insert
					int curTempId = getNextTempId();
					PreparedStatement insert = conn.prepareStatement(TABLE_INSERT);
					insert.setInt(1, curTempId);
					insert.setInt(2, 2 * curTempId);
					int updatedRows = insert.executeUpdate();
					insert.close();
					if (updatedRows != 1) throw new SQLException("Insert failed for " + i + " tempId " + curTempId);
					// 2. select 
					PreparedStatement select = conn.prepareStatement(TABLE_SELECT);
					select.setInt(1, curTempId);
					ResultSet rs = select.executeQuery();
					if (!rs.next()) throw new SQLException("No valid row read for number: "  + i + " tempId " + curTempId);
					int readID = rs.getInt(1);
					int readIntegerValue = rs.getInt(2);
					rs.close();
					select.close();
					if (readID != curTempId) throw new SQLException("Bad ID value read for : "  + i  + " was " + readID);
					if (readIntegerValue != 2 * curTempId) throw new SQLException("Bad integer value read.");
					if (testmode == TestMode.TEST_ROLLBACK_EACH) conn.rollback();
					if (testmode == TestMode.TEST_COMMIT_EACH) conn.commit();
				}
				System.out.println("passed.");
				if (testmode == TestMode.TEST_ROLLBACK_AFTER) conn.rollback();
				if (testmode == TestMode.TEST_COMMIT_AFTER) conn.commit();
				conn.close();
				shouldRepeat = false;
			}
			catch (SQLException e)
			{
				shouldRepeat = isCannotSerializeException(e);
				if (!shouldRepeat) e.printStackTrace();
				try {
					conn.rollback();
				} catch (SQLException e2) {
					e2.printStackTrace();
				}
			} finally {
				try {
					conn.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
			if (shouldRepeat) System.out.println(" - Repeating -");
		} while (shouldRepeat);
	}
	
	public static boolean isCannotSerializeException(Exception e) {
		SQLException s;
		if (e instanceof SQLException) {
			s = (SQLException) e;
		} else if (e.getCause() instanceof SQLException) {
			s = (SQLException) e.getCause();
		} else {
			return false;
		}
		return s.getErrorCode() == 8177;
	}

	/**
	 * @param args
	 * @throws SQLException 
	 */
	public static void main(String[] args) throws SQLException
	{
		testOne();
		testTwo();
		testThree();
	}	
	
	public static void testOne() throws SQLException {
		System.out.println("Test one");
		OracleInsertSelectTest t = new OracleInsertSelectTest();
		t.dropCreateTable();
		t.test(1000, TestMode.TEST_ROLLBACK_AFTER);
		t.test(1000, TestMode.TEST_ROLLBACK_AFTER);
		t.dropCreateTable();
		t.test(1000, TestMode.TEST_COMMIT_AFTER);
		t.test(1000, TestMode.TEST_COMMIT_AFTER);
		t.dropCreateTable();
		t.test(1000, TestMode.TEST_ROLLBACK_AFTER);
		t.test(10000, TestMode.TEST_ROLLBACK_AFTER);
	}

	public static void testTwo() throws SQLException {
		System.out.println("Test two - like one with priming");
		OracleInsertSelectTest t = new OracleInsertSelectTest();
		t.dropCreateTable();
		t.insertAndDeleteRows(100000);
		t.test(1000, TestMode.TEST_ROLLBACK_AFTER);
		t.test(1000, TestMode.TEST_ROLLBACK_AFTER);
		t.dropCreateTable();
		t.insertAndDeleteRows(100000);
		t.test(1000, TestMode.TEST_COMMIT_AFTER);
		t.test(1000, TestMode.TEST_COMMIT_AFTER);
		t.dropCreateTable();
		t.insertAndDeleteRows(100000);
		t.test(1000, TestMode.TEST_ROLLBACK_AFTER);
		t.test(10000, TestMode.TEST_ROLLBACK_AFTER);
	}

	public static void testThree() throws SQLException {
		System.out.println("Test three - primed once");
		OracleInsertSelectTest t = new OracleInsertSelectTest();
		t.dropCreateTable();
		t.insertAndDeleteRows(100000);
		t.test(5000, TestMode.TEST_ROLLBACK_AFTER);
		t.test(5000, TestMode.TEST_ROLLBACK_AFTER);
		t.test(5000, TestMode.TEST_COMMIT_AFTER);
		t.test(10000, TestMode.TEST_COMMIT_AFTER);
		t.test(20000, TestMode.TEST_COMMIT_AFTER);
		t.test(40000, TestMode.TEST_COMMIT_AFTER);
		t.test(80000, TestMode.TEST_COMMIT_AFTER);
		t.insertAndDeleteRows(0);
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

package gov.miamidade.cirm.maintenance;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.sharegov.cirm.Refs;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.rdb.RelationalStoreExt;

/**
 * SRFolioUpdater reads FOLIO information from a csv, compares, and applies it to cases in CIRM_SR_REQUESTS table column SR_FOLIO. 
 * <br>
 * A TEST MODE allows a full run without executing update statements against the database.<br>
 * Note that a repeated run with the same csv should not update any SR Folio.<br>
 *<br>
 * To run, you have to temporarily comment out a tx safeguard in RelationalStoreImpl.<br>
 *<br> 
 * CSV FORMAT  (Tab separated) 
 * <Code>
 * #REQUEST_ID	CASE_NUMBER	FULLADDRESS	FOLIO
 * 95115215	17-10281145	14901 SW 11TH ST	3049090020070
 * 
 * </code>
 * 
 * @author Thomas Hilpold
 */
class SRFolioUpdater {
	
	/**
	 * The configuration file containing REQUEST_ID (long)	CASE_NUMBER(Str)	FULLADDRESS(Str)	FOLIO(long).
	 */
	static final String CONFIG_FILE = "SRFolioUpdater.csv";
	
	/**
	 * Test mode runs the whole process, but does not modify any rows in DB.
	 * Disable only after you are satisfied with results. 
	 * Repeated test mode runs will show the exact same results for a given configuration.
	 */
	static final boolean ANALYSIS_ONLY_MODE = false; 
	
	private List<SRFolioCsvRow> csvDataRows;
	
	void start() {
		System.out.println("*****************************************************************************************");
		System.out.println("          SRFolioUpdater starts processing in " + (ANALYSIS_ONLY_MODE? " ANALYSIS ONLY " : " UPDATE DB ") + " MODE");
		if (!ANALYSIS_ONLY_MODE) {
			System.out.println("UPDATE DB MODE: TABLE CIRM_SR_REQUESTS COLUMN SR_FOLIO WILL BE UPDATED TO ENSURE CORRECT SR_FOLIO AS DEFINED in:" + CONFIG_FILE);		
		}
		System.out.println("*****************************************************************************************");
		URL configFile = this.getClass().getResource(CONFIG_FILE);
		csvDataRows = readData(configFile);
		//Initialize CIRM and get two DB connections, txmode readcommitted.
		System.out.println();
		System.out.println("Rowsto analyze/update "  + csvDataRows.size());
		System.out.println("\r\nCirm Startup Config is: " + StartUp.DEFAULT_CONFIG.at("ontologyConfigSet").asString());
		if (!ANALYSIS_ONLY_MODE) { 
			System.out.println("THIS WILL MODIFY DB TABLE CIRM_SR_REQUESTS SR_FOLIO COLUMNS.");
		} else {
			System.out.println("THIS IS ANALYSIS ONLY MODE: NO UPDATES WILL BE EXECUTED.");
		}
		System.out.print("ENTER 'EXIT' OR CONFIRM BY ENTERING 'CONTINUE':");
		Scanner s = new Scanner(System.in);
		if (!s.next().trim().equalsIgnoreCase("CONTINUE")) {
			System.out.println("EXITING...");
			s.close();
			return;
		}
		s.close();
		int i = 1;
		for (SRFolioCsvRow row : csvDataRows) {
			System.out.println("Start Processing " + i + "/" + csvDataRows.size() );
			processEntry(row);
			System.out.println("Completed Processing " + i + "/" + csvDataRows.size() );
			i++;
		}
		System.out.println("*****************************************************************************************");
		System.out.println("          COMPLETED  processing in " + (ANALYSIS_ONLY_MODE? " ANALYSIS ONLY " : " UPDATE DB ") + " MODE");
		System.out.println("*****************************************************************************************");		
	}

	/**
	 * Processes one config line or DueDateFixEntry.
	 *
	 * @param folioCsvRow
	 */
	private void processEntry(SRFolioCsvRow folioCsvRow) {
		System.out.println("Processing " + folioCsvRow);
		//on first call this will initialize owldb/reasoner
		RelationalStoreExt store = Refs.defaultRelationalStoreExt.resolve();
		Connection con = store.getConnection();
		DbSrEntry selectedSr = null;
		try {
			con.setAutoCommit(true);
			//Overwriting serializable to avoid retry handling for this simple process.
			con.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
			selectedSr = selectOne(folioCsvRow, con);
			if(selectedSr == null) {
				throw new IllegalStateException("SR not found for " + folioCsvRow);
			} else {
				checkAndUpdateFolio(selectedSr, folioCsvRow, con);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			try {
				con.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Completed Processing " + folioCsvRow);		
	}
	
	private void checkAndUpdateFolio(DbSrEntry selectedSr, SRFolioCsvRow folioCsvRow, Connection con) {
		long correctFolio = folioCsvRow.getFOLIO(); 
		long existingFolio = selectedSr.getSR_FOLIO();
		if (correctFolio != existingFolio) {
			if (folioCsvRow.getCASE_NUMBER().equals(selectedSr.getCASE_NUMBER())) {
				if (folioCsvRow.getFULL_ADDRESS().equals(selectedSr.getFULL_ADDRESS())) {
					if (!ANALYSIS_ONLY_MODE) {
						dbUpdateFolio(selectedSr, correctFolio, con);
					}
					System.out.println("FOLIO UPDATED, WAS: " + existingFolio + " " + folioCsvRow);
				} else {
					System.out.println("FULL ADDRESS NOT MATCHED: " + folioCsvRow);
				}
			} else {
				System.out.println("CASE NUMBER NOT MATCHED: " + folioCsvRow);				
			}
		} else {
			System.out.println("EXISTING FOLIO IS OK " + folioCsvRow);
		}
	}

	/**
	 * Select all SRs to check for one config line or DueDateFixEntry.
	 * @param ddfe
	 * @param con
	 * @return
	 */
	private DbSrEntry selectOne(SRFolioCsvRow folioCsvRow, Connection con)  {
		DbSrEntry result = null;
		try {
			System.out.println("Selecting SR for " + folioCsvRow);
			PreparedStatement s = con.prepareStatement(DB_SELECT);
			//param 1 SR_ID, 2 CASE_NUMBER
			s.setLong(1, folioCsvRow.getREQUEST_ID());
			s.setString(2, folioCsvRow.getCASE_NUMBER());
			ResultSet rs = s.executeQuery();
			while (rs.next()) {
				result = new DbSrEntry(rs);
			}
			s.close();
			//may return null == not found
			return result;
		} catch (SQLException e) {
			throw new RuntimeException("Fatal SQL exc during select " + folioCsvRow, e);
		}
	}
	
	private void dbUpdateFolio(DbSrEntry sre, long newFolio, Connection con)  {
		try {
			PreparedStatement s = con.prepareStatement(DB_UPDATE);
			//param 1 new SR_FOLIO 2  SR_REQUEST_ID 3 FULL_ADDRESS
			s.setLong(1, newFolio);
			s.setLong(2, sre.getSR_REQUEST_ID());
			s.setString(3, sre.getCASE_NUMBER());
			int updatedRows = s.executeUpdate();
			if (updatedRows != 1) throw new IllegalStateException("Updated != 1 rows, but " + updatedRows + " :failed for " + sre + " newFolio "  + newFolio);
			s.close();
		} catch (SQLException e) {
			throw new RuntimeException("Fatal SQL exc during update " + sre, e);
		}	
	}

	/**
	 * Read csv file.
	 * @param configFile
	 * @return
	 */
	private List<SRFolioCsvRow> readData(URL csvFile) {
		List<SRFolioCsvRow> result = new ArrayList<SRFolioCsvRow>(); 
		System.out.print("Reading data from " + csvFile + "...");
		try {
			BufferedReader br = new BufferedReader(new FileReader(csvFile.getFile()));
			while (br.ready()) {
				String l = br.readLine();
				if (l.startsWith("#") || l.trim().isEmpty()) continue;
				result.add(new SRFolioCsvRow(l));
			}
			br.close();
			System.out.println("Complete: " + result.size() + " lines");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return result;
	}
	
	/**
	 * Runs the process - will ask for user confirmation before proceeding with db changes.
	 * @param args
	 */
	public static void main(String[] args) {		
		SRFolioUpdater d = new SRFolioUpdater();
		d.start();
	}

	/**
	 * DbSrEntry represents columns or one SR row loaded from DB.
	 *
	 * @author Thomas Hilpold
	 *
	 */
	private static class DbSrEntry {
		private long SR_REQUEST_ID;
		private String CASE_NUMBER;
		private String FULL_ADDRESS;
		private long SR_FOLIO;
		
		public DbSrEntry(ResultSet rs) throws SQLException {
			SR_REQUEST_ID = rs.getLong("SR_REQUEST_ID");
			CASE_NUMBER = rs.getString("CASE_NUMBER");
			FULL_ADDRESS = rs.getString("FULL_ADDRESS");
			SR_FOLIO = rs.getLong("SR_FOLIO");
		}
		
		public String toString() {
			return getSR_REQUEST_ID() + ", " + getCASE_NUMBER() +  ", " 
					+ getFULL_ADDRESS() + ", " + getSR_FOLIO();
		}

		
	
	long getSR_REQUEST_ID() {
			return SR_REQUEST_ID;
		}

		String getCASE_NUMBER() {
			return CASE_NUMBER;
		}

		String getFULL_ADDRESS() {
			return FULL_ADDRESS;
		}

		long getSR_FOLIO() {
			return SR_FOLIO;
		}
	}

	//REQUEST_ID (long)	CASE_NUMBER(Str)	FULL_ADDRESS(Str)	FOLIO(long).
	private static class SRFolioCsvRow {
		private long REQUEST_ID; //inclusive >=
		private String CASE_NUMBER; //exclusive <
		private String FULL_ADDRESS;
		private long FOLIO;
	
		SRFolioCsvRow(String line) {
			try {
				Scanner s = new Scanner(line);
				s.useDelimiter("\t");
				REQUEST_ID = Long.parseLong(s.next().trim());
				CASE_NUMBER = s.next().trim();
				FULL_ADDRESS = s.next().trim();
				FOLIO = Long.parseLong(s.next().trim());
				s.close();
				//Ignore remaining line.
			} catch (Exception e) {
				throw new RuntimeException("Error parsing line: " + line, e);
			}
		}
		
		public String toString() {
			return REQUEST_ID + ", " + CASE_NUMBER + ", " + FULL_ADDRESS + ", " + FOLIO;
		}

		long getREQUEST_ID() {
			return REQUEST_ID;
		}

		String getCASE_NUMBER() {
			return CASE_NUMBER;
		}

		String getFULL_ADDRESS() {
			return FULL_ADDRESS;
		}

		long getFOLIO() {
			return FOLIO;
		}
	}
	
	//DB SQL Queries;
	public static final String DB_SELECT = 
			"select SR_REQUEST_ID, CASE_NUMBER, CIRM_MDC_ADDRESS.FULL_ADDRESS, SR_FOLIO FROM CIRM_SR_REQUESTS \r\n"
			+ "inner join CIRM_MDC_ADDRESS on (CIRM_SR_REQUESTS.SR_REQUEST_ADDRESS = CIRM_MDC_ADDRESS.ADDRESS_ID) \r\n"
			+ " Where   \r\n"
			+ " CIRM_SR_REQUESTS.SR_REQUEST_ID = ? \r\n"
			+ " AND CIRM_SR_REQUESTS.CASE_NUMBER = ? \r\n";
			//1 ID long, 2 CASENUM String
	
	public static final String DB_UPDATE = 
			"UPDATE CIRM_SR_REQUESTS \r\n"
			+ " SET  CIRM_SR_REQUESTS.SR_FOLIO = ? \r\n"
			+ " WHERE \r\n" 
			+ " CIRM_SR_REQUESTS.SR_REQUEST_ID = ? \r\n"
			+ " AND CIRM_SR_REQUESTS.CASE_NUMBER = ? \r\n"; //--Safety
			//1 SR_FOLIO long, 2 SRID long, 3 CASENUM String
}
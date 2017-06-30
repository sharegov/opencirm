package gov.miamidade.cirm.maintenance;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.rdb.RelationalStoreExt;

/**
 * SRDueDateFixer corrects SR due dates in CIRM_SR_REQUESTS table rows that are more than 1 day off 
 * based on a configuration set in DueDateFixerConf.csv and
 * a recalculation of the correct due date using OWL.addDaysToDate.<br>
 * <br>
 * A TEST MODE allows a full run without executing update statements against the database.<br>
 * Note that a repeated run with the same configuration should not update any SR due date.<br>
 *<br>
 * To run, you have to temporarily comment out a tx safeguard in RelationalStoreImpl.<br>
 *<br> 
 *
 * @author Thomas Hilpold
 *
 */
class SRDueDateFixer {
	
	/**
	 * The configuration file, setting created date range, srType, intake Method and goal days per row.
	 */
	static final String CONFIG_FILE = "SRDueDateFixerConf.csv";
	
	/**
	 * Test mode runs the whole process, but does not modify any rows in DB.
	 * Disable only after you are satisfied with results. 
	 * Repeated test mode runs will show the exact same results for a given configuration.
	 */
	static final boolean ANALYSIS_ONLY_MODE = true; 
	
	private List<DueDateFixEntry> fixEntryList;
	
	/**
	 * Starts the SRDueDateFixer process and fixes all SRs as configured in csv.
	 */
	void start() {
		System.out.println("*****************************************************************************************");
		System.out.println("          SRDueDateFixer starts processing in " + (ANALYSIS_ONLY_MODE? " ANALYSIS ONLY " : " UPDATE DB ") + " MODE");
		if (!ANALYSIS_ONLY_MODE) {
			System.out.println("UPDATE DB MODE: TABLE CIRM_SR_REQUESTS COLUMN DUE_DATE WILL BE UPDATED TO ENSURE CORRECT DUE_DATES AS CONFIGURED in:" + CONFIG_FILE);		
		}
		System.out.println("*****************************************************************************************");
		URL configFile = this.getClass().getResource(CONFIG_FILE);
		fixEntryList = readConfig(configFile);
		//Initialize CIRM and get two DB connections, txmode readcommitted.
		System.out.println();
		System.out.println("Please confirm the following configuration that is about to execute:\r\n");
		System.out.println("CREATED_FROM_DATE, CREATED_TO_DATE, SR_TYPE, INTAKE_METHOD_CODE, DURATION_DAYS, isDuration5DayWorkweek");
		for (DueDateFixEntry e : fixEntryList) {
			System.out.println(e);
		}
		System.out.println("\r\nCirm Startup Config is: " + StartUp.DEFAULT_CONFIG.at("ontologyConfigSet").asString());
		if (!ANALYSIS_ONLY_MODE) { 
			System.out.println("THIS WILL MODIFY DB TABLE CIRM_SR_REQUESTS DUE DATE COLUMNS.");
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
		for (DueDateFixEntry e : fixEntryList) {
			System.out.println("Start Processing " + i + "/" + fixEntryList.size() );
			processEntry(e);
			System.out.println("Completed Processing " + i + "/" + fixEntryList.size() );
			i++;
		}
		System.out.println("*****************************************************************************************");
		System.out.println("          COMPLETED SRDueDateFixer processing in " + (ANALYSIS_ONLY_MODE? " ANALYSIS ONLY " : " UPDATE DB ") + " MODE");
		System.out.println("*****************************************************************************************");		
	}

	/**
	 * Processes one config line or DueDateFixEntry.
	 *
	 * @param ddfe
	 */
	private void processEntry(DueDateFixEntry ddfe) {
		System.out.println("Processing " + ddfe);
		//on first call this will initialize owldb/reasoner
		RelationalStoreExt store = Refs.defaultRelationalStoreExt.resolve();
		Connection con = store.getConnection();
		List<DbSrEntry> selectedSrs = null;
		try {
			con.setAutoCommit(true);
			//Overwriting serializable to avoid retry handling for this simple process.
			con.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
			selectedSrs = select(ddfe, con);
			System.out.println("Selected " + selectedSrs.size() + " total SRs for due date checking.");
			checkAndUpdateBadDueDates(selectedSrs, ddfe, con);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			try {
				con.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Completed Processing " + ddfe);		
	}
	
	/**
	 * Checks all selectedSrs for a config line and updates those db SR rows where the due date is null or more than 1 day off.
	 * 
	 * @param selectedSrs
	 * @param ddfe
	 * @param con
	 */
	private void checkAndUpdateBadDueDates(List<DbSrEntry> selectedSrs, DueDateFixEntry ddfe, Connection con) {
		int updatedSrs = 0;
		int correctSrs = 0;
		for (DbSrEntry sr : selectedSrs) {
			Date correctDueDate; 
			correctDueDate = OWL.addDaysToDate(sr.getCREATED_DATE(), ddfe.getDurationDays(), ddfe.isUse5DayWorkWeek());
			Date existingDueDate = sr.getDUE_DATE();
			if (correctDueDate.after(sr.getCREATED_DATE())) {
				if (existingDueDate == null || differenceInHours(correctDueDate, existingDueDate) > 0.0d) {
					updatedSrs ++;
					System.out.println(updatedSrs + ", Updating, " + sr + ", with due date, " + correctDueDate);
					if (!ANALYSIS_ONLY_MODE) {
						dbUpdateDueDate(sr, correctDueDate, con);
					}
				} else {
					correctSrs ++;
				}
			} else {
				throw new IllegalStateException("Correct Due date not after created date or 0 duration days set in csv.");
			}			
		}
		System.out.println("Updates complete for: "  + ddfe);
		System.out.println("Total updated SRs: "  + updatedSrs);
		System.out.println("Total correct SRs: "  + correctSrs);
	}
	
	double differenceInHours(Date a, Date b) {
		return Math.abs(a.getTime() - b.getTime()) / 1000.0d / 60.0d / 60.0d;
	}

	/**
	 * Select all SRs to check for one config line or DueDateFixEntry.
	 * @param ddfe
	 * @param con
	 * @return
	 */
	private List<DbSrEntry> select(DueDateFixEntry ddfe, Connection con)  {
		List<DbSrEntry> result = new ArrayList<>(50000);
		try {
			System.out.println("Selecting SRs for " + ddfe);
			PreparedStatement s = con.prepareStatement(DB_SELECT);
			//param 1 SR Type fullIRI 2 CREATED_DATE min 3 CREATED_DATE max
			s.setString(1, "http://www.miamidade.gov/cirm/legacy#" + ddfe.getSrTypeFragment());
			//old version s.setString(2, ddfe.getIntakeMethodFragment());
			s.setTimestamp(2, new Timestamp(ddfe.getFrom().getTime()));
			s.setTimestamp(3, new Timestamp(ddfe.getTo().getTime()));
			ResultSet rs = s.executeQuery();
			while (rs.next()) {
				result.add(new DbSrEntry(rs));
			}
			s.close();
			System.out.println("Selected " + result.size() + " SRs");
			return result;
		} catch (SQLException e) {
			throw new RuntimeException("Fatal SQL exc during select " + ddfe, e);
		}
	}
	
	/**
	 * Updates the due date of one SR.
	 * 
	 * @param sre
	 * @param newDueDate
	 * @param con
	 */
	private void dbUpdateDueDate(DbSrEntry sre, Date newDueDate, Connection con)  {
		try {
			PreparedStatement s = con.prepareStatement(DB_UPDATE);
			//param 1 new DUE_DATE 2  SR_REQUEST_ID 3 SR_INTAKE_METHOD
			s.setTimestamp(1, new Timestamp(newDueDate.getTime()));
			s.setLong(2, sre.getSR_REQUEST_ID());
			s.setString(3, sre.getSR_INTAKE_METHOD());
			int updatedRows = s.executeUpdate();
			if (updatedRows != 1) throw new IllegalStateException("Updated != 1 rows, but " + updatedRows + " :failed for " + sre + " newDue "  + newDueDate);
			s.close();
		} catch (SQLException e) {
			throw new RuntimeException("Fatal SQL exc durint update " + sre, e);
		}	
	}

	/**
	 * Read csv config file.
	 * @param configFile
	 * @return
	 */
	private List<DueDateFixEntry> readConfig(URL configFile) {
		List<DueDateFixEntry> result = new ArrayList<DueDateFixEntry>(); 
		System.out.print("Reading config from " + configFile + "...");
		try {
			BufferedReader br = new BufferedReader(new FileReader(configFile.getFile()));
			while (br.ready()) {
				String l = br.readLine();
				if (l.startsWith("#") || l.trim().isEmpty()) continue;
				result.add(new DueDateFixEntry(l));
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
		SRDueDateFixer d = new SRDueDateFixer();
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
		private Date CREATED_DATE;
		private Date DUE_DATE;
		private String SR_INTAKE_METHOD;
		private String SR_TYPE_FRAGMENT;
		
		public DbSrEntry(ResultSet rs) throws SQLException {
			SR_REQUEST_ID = rs.getLong("SR_REQUEST_ID");
			CASE_NUMBER = rs.getString("CASE_NUMBER");
			CREATED_DATE = rs.getTimestamp("CREATED_DATE");
			DUE_DATE = rs.getTimestamp("DUE_DATE");
			SR_INTAKE_METHOD = rs.getString("SR_INTAKE_METHOD");
			SR_TYPE_FRAGMENT = rs.getString("IRI").substring("http://www.miamidade.gov/cirm/legacy#".length());
		}
		
		public String toString() {
			return getSR_REQUEST_ID() + ", " + getCASE_NUMBER() + ", " + getCREATED_DATE() + ",  " 
					+ getDUE_DATE() + ",  " + getSR_INTAKE_METHOD() + ",  " + getSR_TYPE_FRAGMENT();
		}

		long getSR_REQUEST_ID() {
			return SR_REQUEST_ID;
		}

		String getCASE_NUMBER() {
			return CASE_NUMBER;
		}

		Date getCREATED_DATE() {
			return CREATED_DATE;
		}

		Date getDUE_DATE() {
			return DUE_DATE;
		}

		String getSR_INTAKE_METHOD() {
			return SR_INTAKE_METHOD;
		}

		String getSR_TYPE_FRAGMENT() {
			return SR_TYPE_FRAGMENT;
		}
	}
	
	/**
	 * DueDateFixEntry represents one configuration line in config csv.
	 *
	 * @author Thomas Hilpold
	 *
	 */
	private static class DueDateFixEntry {
		static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		private Date from; //inclusive >=
		private Date to; //exclusive <
		private String srTypeFragment;
		private float durationDays;
		private boolean use5DayWorkWeek;
	
		DueDateFixEntry(String line) {
			try {
				Scanner s = new Scanner(line);
				s.useDelimiter(",");
				from = df.parse(s.next().trim());
				to = df.parse(s.next().trim());
				srTypeFragment = s.next().trim();
				durationDays = Float.parseFloat(s.next().trim());
				use5DayWorkWeek = Boolean.parseBoolean(s.next());	
				s.close();
				//Ignore remaining line.
			} catch (Exception e) {
				throw new RuntimeException("Error parsing line: " + line, e);
			}
		}
		
		public String toString() {
			return from + ", " + to + ", " + srTypeFragment + ", " + durationDays + ", " + use5DayWorkWeek;
		}

		Date getFrom() {
			return from;
		}

		Date getTo() {
			return to;
		}

		String getSrTypeFragment() {
			return srTypeFragment;
		}

		float getDurationDays() {
			return durationDays;
		}

		boolean isUse5DayWorkWeek() {
			return use5DayWorkWeek;
		}
	}
	
	//DB SQL Queries;
	public static final String DB_SELECT = 
			"select SR_REQUEST_ID, CASE_NUMBER, SR_INTAKE_METHOD, CREATED_DATE, DUE_DATE, CIRM_IRI.IRI FROM CIRM_SR_REQUESTS \r\n"
			+ " inner join CIRM_CLASSIFICATION on (CIRM_SR_REQUESTS.SR_REQUEST_ID = CIRM_CLASSIFICATION.SUBJECT AND CIRM_CLASSIFICATION.TO_DATE is null) \r\n"
			+ " inner join CIRM_IRI on (CIRM_CLASSIFICATION.OWLCLASS = CIRM_IRI.ID AND CIRM_IRI.IRI = ?) \r\n"
			+ " Where   \r\n"
			+ " CIRM_SR_REQUESTS.CREATED_DATE >= ? \r\n"
			+ " AND CIRM_SR_REQUESTS.CREATED_DATE < ? \r\n"
			+ " order by SR_REQUEST_ID desc \r\n";
			//IRI = 'http://www.miamidade.gov/cirm/legacy#MOSQINSP'
			//param 1 SR Type fullIRI 2 CREATED_DATE min 3 CREATED_DATE max
	
	public static final String DB_UPDATE = 
			"UPDATE CIRM_SR_REQUESTS \r\n"
			+ " SET  CIRM_SR_REQUESTS.DUE_DATE = ? \r\n"
			+ " WHERE \r\n" 
			+ " CIRM_SR_REQUESTS.SR_REQUEST_ID = ? \r\n"
			+ " AND CIRM_SR_REQUESTS.SR_INTAKE_METHOD = ? \r\n"; //--Safety
			//param 1 new DUE_DATE 2  SR_REQUEST_ID 3 SR_INTAKE_METHOD
}
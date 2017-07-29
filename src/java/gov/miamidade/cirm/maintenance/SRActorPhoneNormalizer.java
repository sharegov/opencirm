package gov.miamidade.cirm.maintenance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import org.sharegov.cirm.Refs;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.rdb.RelationalStoreExt;

/**
 * SRActorPhoneNormalizer normalizes all actor phone numbers currently stored in a CIRM_SR_ACTOR phone number fields.
 *
 * Normalized format is numbers only, no country prefix. Multiple phone numbers separated by ",", Extension added by "#"
 * 
 * @author Thomas Hilpold
 */
class SRActorPhoneNormalizer {
	
	/**
	 * The minimum actor id to process.
	 */
	static final long MIN_SR_ACTOR_ID_TO_UPDATE = 0;
	
	/**
	 * The maximum actor id to process.
	 */	
	static final long MAX_SR_ACTOR_ID_TO_UPDATE = Long.MAX_VALUE; //Does not exist
	
	/**
	 * Test mode runs the whole process, but does not modify any rows in DB.
	 * Disable only after you are satisfied with results. 
	 * Repeated test mode runs will show the exact same results for a given configuration.
	 */
	static final boolean ANALYSIS_ONLY_MODE = false; 
	
	void start() {
		System.out.println("*****************************************************************************************");
		System.out.println("          SRFolioUpdater starts processing in " + (ANALYSIS_ONLY_MODE? " ANALYSIS ONLY " : " UPDATE DB ") + " MODE");
		if (!ANALYSIS_ONLY_MODE) {
			System.out.println("UPDATE DB MODE: TABLE CIRM_SR_ACTOR COLUMNS SR_ACTOR_CELL_PHONE_NO, SR_ACTOR_FAX_PHONE_NO, \r\n"
					+ "SR_ACTOR_PHONE_NUMBER, SR_ACTOR_WORK_PHONE_NO WILL BE UPDATED TO ENSURE NORMALIZED PHONE NUMBERS");		
		}
		System.out.println("*****************************************************************************************");
		//Initialize CIRM and get two DB connections, txmode readcommitted.
		List<DbSrActorEntry>  dbActors = selectAll(MIN_SR_ACTOR_ID_TO_UPDATE, MAX_SR_ACTOR_ID_TO_UPDATE);
		System.out.println();
		System.out.println("Total actors this execution "  + dbActors.size());
		System.out.println("\r\nCirm Startup Config is: " + StartUp.DEFAULT_CONFIG.at("ontologyConfigSet").asString());
		if (!ANALYSIS_ONLY_MODE) { 
			System.out.println("THIS WILL MODIFY DB TABLE CIRM_SR_ACTOR 4 phone related COLUMNS.");
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
		System.out.println("Starting update " + new Date());
		processAll(dbActors);
		System.out.println("*****************************************************************************************");
		System.out.println("          COMPLETED  processing in " + (ANALYSIS_ONLY_MODE? " ANALYSIS ONLY " : " UPDATE DB ") + " MODE");
		System.out.println("*****************************************************************************************");	
		System.out.println("" + new Date());
	}

	private void processAll(List<DbSrActorEntry> dbActors) {
		RelationalStoreExt store = Refs.defaultRelationalStoreExt.resolve();
		Connection con = store.getConnection();
		try {
			con.setAutoCommit(true);
			//Overwriting serializable to avoid retry handling for this simple process.
			con.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
			int i = 1;
			for (DbSrActorEntry row : dbActors) {
				System.out.println( i + "/" + dbActors.size());
				processEntry(row, con);
				i++;
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
	}

	/**
	 * Processes one DbSrActorEntry.
	 *
	 * @param folioCsvRow
	 */
	private void processEntry(DbSrActorEntry oldActorDbEntry, Connection con) {
		//on first call this will initialize owldb/reasoner
		String oldActorP = oldActorDbEntry.toString();
		DbSrActorEntry newActorDbEntry = normalizeActorPhones(oldActorDbEntry);
		String newActorP = newActorDbEntry.toString();
		boolean changed = !(oldActorP.equals(newActorP));
		if (changed) {
			System.out.println("O: " + oldActorP);
			System.out.println("N: " + newActorP);			
    		if (!ANALYSIS_ONLY_MODE) {
    			dbUpdateActorPhones(newActorDbEntry, con);
    		}
		}
	}
	
	/**
	 * Normalizes all 4 phone numbers in the given oldActor and returns a new Actor object with the normalized numbers.
	 * 
	 * @param oldActor
	 * @return a new actor object with (equal) or fixed phone numbers
	 */
	private DbSrActorEntry normalizeActorPhones(final DbSrActorEntry oldActor) {
		DbSrActorEntry newActor = new DbSrActorEntry(oldActor);
		String cell = oldActor.getSR_ACTOR_CELL_PHONE_NO();
		String fax = oldActor.getSR_ACTOR_FAX_PHONE_NO();
		String phone = oldActor.getSR_ACTOR_PHONE_NUMBER();
		String work = oldActor.getSR_ACTOR_WORK_PHONE_NO();
		cell = normalizePhoneData(cell);
		fax = normalizePhoneData(fax);
		phone = normalizePhoneData(phone);
		work = normalizePhoneData(work);
		newActor.updatePhoneNumbers(cell, fax, phone, work);
		return newActor;
	}

	String normalizePhoneData(String data) {
		String result;
		if (data == null || data.isEmpty()) return data;		
		String[] phoneNums = data.split(",");
		for (int i = 0; i < phoneNums.length; i++) {
			phoneNums[i] = normalizeOnePhoneNumber(phoneNums[i]);
		}
		//Ensure only unique non emplty phone numbers are returned
		List<String> phoneNumsList = new LinkedList<>();
		for (int i = 0; i < phoneNums.length; i++) {
			String curNum = phoneNums[i];
			if(!phoneNumsList.contains(curNum)) {
				if (curNum != null && !curNum.isEmpty()) {
					phoneNumsList.add(curNum);
				} else {
				//do nothing
				}
			} else { //already contained
				System.out.println("Duplicate: " + curNum);
			}
		}
		result = "";
		for (int i = 0; i < phoneNumsList.size(); i++) {
			result += phoneNumsList.get(i);
			if (i != phoneNumsList.size()-1) {
				result += ",";
			}
		}
		return result;
	}
	
	String normalizeOnePhoneNumber(String oneNum) {
		if (oneNum == null || oneNum.isEmpty()) return oneNum;
		String result;
		String[] numExt = oneNum.split("#");
		String number = numExt[0];
		String ext = null;
		if (numExt.length > 1) {
			ext = onlyDigits(numExt[1]);
		}
		if (numExt.length > 2) {
			System.err.println("Error weird number: " + oneNum);
		}
		number = onlyDigits(number);
		//Remove 1 at start (country code usa)
		if (number.startsWith("1") && number.length() > 10) number = number.substring(1);
		result = number;
		if (number.length() != 10) {
			System.err.println("Numer != 10 " + number);
		}
		if (ext != null && !ext.isEmpty()) {
			result += "#" + ext;
		}
		return result;
	}
	
	String onlyDigits(String any) {
		String result = "";
		for (char c : any.toCharArray()) {
			if (Character.isDigit(c)) {
				result += c;
			}
		}
		return result;
	}

	private List<DbSrActorEntry> selectAll(long minActorId, long maxInclusiveActorId)  {
		List<DbSrActorEntry> result = null;
    	RelationalStoreExt store = Refs.defaultRelationalStoreExt.resolve();
    	Connection con = store.getConnection();
    	try {
    		con.setAutoCommit(true);
    		con.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    		result = selectAll(minActorId, maxInclusiveActorId, con);
    	} catch (Exception e) {
    		throw new RuntimeException(e);
    	} finally {
    		try {
    			con.close();
    		} catch (SQLException e) {
    			e.printStackTrace();
    		}
    	}
    	return result;
	}
	
	/**
	 * Select all SRs to check for one config line or DueDateFixEntry.
	 * @param ddfe
	 * @param con
	 * @return
	 */
	private List<DbSrActorEntry> selectAll(long minActorId, long maxInclusiveActorId, Connection con)  {
		List<DbSrActorEntry> result = new ArrayList<>(1000 * 1000);
		try {
            //Params 1 Max actor id long inclusive, 2 Min actor id long exclusive 
			PreparedStatement s = con.prepareStatement(DB_SELECT);
			s.setLong(1, maxInclusiveActorId);
			s.setLong(2, minActorId);
			ResultSet rs = s.executeQuery();
			while (rs.next()) {
				result.add(new DbSrActorEntry(rs));
			}
			s.close();
			//may return null == not found
			return result;
		} catch (SQLException e) {
			throw new RuntimeException("Fatal SQL exc during select ", e);
		}
	}
	
	private void dbUpdateActorPhones(DbSrActorEntry ae, Connection con)  {
		try {
			// 1 SR_ACTOR_CELL_PHONE_NO val varchar2(255)
			// 2 SR_ACTOR_FAX_PHONE_NO val varchar2(255)
			// 3 SR_ACTOR_PHONE_NUMBER val varchar2(255)
			// 4 SR_ACTOR_WORK_PHONE_NO val varchar2(255)
			// 5 SR_ACTOR_ID long 
			// 6 SR_ACTOR_TYPE string varchar2(255)
			PreparedStatement s = con.prepareStatement(DB_UPDATE);
			s.setString(1, ae.getSR_ACTOR_CELL_PHONE_NO());
			s.setString(2, ae.getSR_ACTOR_FAX_PHONE_NO());
			s.setString(3, ae.getSR_ACTOR_PHONE_NUMBER());
			s.setString(4, ae.getSR_ACTOR_WORK_PHONE_NO());
			s.setLong(5, ae.getSR_ACTOR_ID());
			s.setString(6, ae.getSR_ACTOR_TYPE());
			int updatedRows = s.executeUpdate();
			if (updatedRows != 1) throw new IllegalStateException("Updated != 1 rows, but " + updatedRows + " :failed for " + ae);
			s.close();
		} catch (SQLException e) {
			throw new RuntimeException("Fatal SQL exc during update " + ae, e);
		}	
	}

	
	/**
	 * Runs the process - will ask for user confirmation before proceeding with db changes.
	 * @param args
	 */
	public static void main(String[] args) {		
		SRActorPhoneNormalizer d = new SRActorPhoneNormalizer();
		d.start();
	}

	/**
	 * DbSrEntry represents columns or one SR row loaded from DB.
	 *
	 * @author Thomas Hilpold
	 *
	 */
	private static class DbSrActorEntry {
		private long SR_ACTOR_ID; //inclusive >=
		private String SR_ACTOR_TYPE; //exclusive <
		private String SR_ACTOR_PHONE_NUMBER;
		private String SR_ACTOR_WORK_PHONE_NO;
		private String SR_ACTOR_CELL_PHONE_NO;
		private String SR_ACTOR_FAX_PHONE_NO;

		
		public DbSrActorEntry(DbSrActorEntry orig) {
			SR_ACTOR_ID = orig.getSR_ACTOR_ID();
			SR_ACTOR_TYPE = orig.getSR_ACTOR_TYPE();
			SR_ACTOR_CELL_PHONE_NO = orig.getSR_ACTOR_CELL_PHONE_NO();
			SR_ACTOR_FAX_PHONE_NO = orig.getSR_ACTOR_FAX_PHONE_NO();
			SR_ACTOR_PHONE_NUMBER = orig.getSR_ACTOR_PHONE_NUMBER();
			SR_ACTOR_WORK_PHONE_NO = orig.getSR_ACTOR_WORK_PHONE_NO();
			if (SR_ACTOR_ID == 0) throw new IllegalStateException("SR_ACTOR_ID zero");
		}

		public DbSrActorEntry(ResultSet rs) throws SQLException {
			SR_ACTOR_ID = rs.getLong("SR_ACTOR_ID");
			SR_ACTOR_TYPE = rs.getString("SR_ACTOR_TYPE");
			SR_ACTOR_CELL_PHONE_NO = rs.getString("SR_ACTOR_CELL_PHONE_NO");
			SR_ACTOR_FAX_PHONE_NO = rs.getString("SR_ACTOR_FAX_PHONE_NO");
			SR_ACTOR_PHONE_NUMBER = rs.getString("SR_ACTOR_PHONE_NUMBER");
			SR_ACTOR_WORK_PHONE_NO = rs.getString("SR_ACTOR_WORK_PHONE_NO");
			if (SR_ACTOR_ID == 0) throw new IllegalStateException("SR_ACTOR_ID zero");
		}
		
		//Do NOT modify toString without checking it's use
		public String toString() {
			return getSR_ACTOR_ID() + "\t " + getSR_ACTOR_TYPE() 
					+  "\t C: " + getSR_ACTOR_CELL_PHONE_NO() 
					+ "\t F: " + getSR_ACTOR_FAX_PHONE_NO()
					+ "\t P: " + getSR_ACTOR_PHONE_NUMBER()
					+ "\t W: " + getSR_ACTOR_WORK_PHONE_NO();
		}
		
//		public boolean equals(Object o) {
//			if (o == null || !(o instanceof DbSrActorEntry)) return false;
//			DbSrActorEntry other = (DbSrActorEntry) o;
//			boolean idEqual = this.getSR_ACTOR_ID() == other.getSR_ACTOR_ID() && 
//					this.getSR_ACTOR_TYPE().equals(other.getSR_ACTOR_TYPE());
//			//complex with null possible for each phone
//		}
		
		/**
		 * Sets new phone numbers for this object. 
		 * Make sure to print before and after.
		 * @param cell anything is allowed.
		 * @param fax
		 * @param phone
		 * @param work
		 */
		void updatePhoneNumbers(String cell, String fax, String phone, String work) {
			SR_ACTOR_CELL_PHONE_NO = cell;
			SR_ACTOR_FAX_PHONE_NO = fax;
			SR_ACTOR_PHONE_NUMBER = phone;
			SR_ACTOR_WORK_PHONE_NO = work;
		}

		long getSR_ACTOR_ID() {
			return SR_ACTOR_ID;
		}

		String getSR_ACTOR_TYPE() {
			return SR_ACTOR_TYPE;
		}

		String getSR_ACTOR_CELL_PHONE_NO() {
			return SR_ACTOR_CELL_PHONE_NO;
		}

		String getSR_ACTOR_FAX_PHONE_NO() {
			return SR_ACTOR_FAX_PHONE_NO;
		}

		String getSR_ACTOR_PHONE_NUMBER() {
			return SR_ACTOR_PHONE_NUMBER;
		}

		String getSR_ACTOR_WORK_PHONE_NO() {
			return SR_ACTOR_WORK_PHONE_NO;
		}
	}

	
	//DB SQL Queries;
	public static final String DB_SELECT = 
			"select SR_ACTOR_ID, SR_ACTOR_TYPE, SR_ACTOR_PHONE_NUMBER, SR_ACTOR_WORK_PHONE_NO, SR_ACTOR_CELL_PHONE_NO, SR_ACTOR_FAX_PHONE_NO from CIRM_SR_ACTOR \r\n" 
			+"where SR_ACTOR_ID <= ? \r\n"
			+"and  SR_ACTOR_ID  > ? \r\n"
			+"and  ( SR_ACTOR_CELL_PHONE_NO is not null \r\n"
			+"or   SR_ACTOR_FAX_PHONE_NO is not null \r\n"
			+"or   SR_ACTOR_PHONE_NUMBER is not null \r\n"
			+"or   SR_ACTOR_WORK_PHONE_NO is not null ) \r\n"
			+"order by SR_ACTOR_ID desc \r\n";
            //Params 1 Max actor id long inclusive, 2 Min actor id long exclusive 
			//	CREATE TABLE CIRM_SR_ACTOR
            //	(
            //	   SR_ACTOR_ID decimal(19,0) PRIMARY KEY NOT NULL,
            //	   SR_ACTOR_NAME varchar2(255),
            //	   SR_ACTOR_LNAME varchar2(255),
            //	   SR_ACTOR_INITIALS varchar2(255),
            //	   SR_ACTOR_TITLE varchar2(255),
            //	   SR_ACTOR_SUFFIX varchar2(255),
            //	   SR_ACTOR_PHONE_NUMBER varchar2(255),
            //	   SR_ACTOR_EMAIL varchar2(255),
            //	   SR_ACTOR_CONTACT_METHOD varchar2(255),
            //	   SR_ACTOR_TYPE varchar2(255),
            //	   SR_ACTOR_ADDRESS decimal(19,0),
            //	   SR_ACTOR_WORK_PHONE_NO varchar2(255),
            //	   SR_ACTOR_CELL_PHONE_NO varchar2(255),
            //	   SR_ACTOR_FAX_PHONE_NO varchar2(255),
            //	   CREATED_BY varchar2(255),
            //	   CREATED_DATE timestamp,
            //	   UPDATED_BY varchar2(255),
            //	   UPDATED_DATE timestamp
            //	);
            //	CREATE INDEX CIRM_IDX_SR_ACTOR_ADDFK ON CIRM_SR_ACTOR(SR_ACTOR_ADDRESS);
            //	CREATE UNIQUE INDEX CIRM_SR_ACTOR_PK ON CIRM_SR_ACTOR(SR_ACTOR_ID);	
	
	public static final String DB_UPDATE = 
			"UPDATE CIRM_SR_ACTOR \r\n"
			+ " SET  CIRM_SR_ACTOR.SR_ACTOR_CELL_PHONE_NO = ?, \r\n" 
			+ " CIRM_SR_ACTOR.SR_ACTOR_FAX_PHONE_NO = ?, \r\n" 
			+ " CIRM_SR_ACTOR.SR_ACTOR_PHONE_NUMBER = ?, \r\n"
			+ " CIRM_SR_ACTOR.SR_ACTOR_WORK_PHONE_NO = ? \r\n"
			+ " WHERE \r\n" 
			+ " CIRM_SR_ACTOR.SR_ACTOR_ID = ? \r\n"
			+ " AND CIRM_SR_ACTOR.SR_ACTOR_TYPE = ? \r\n"; //--Safety
			// 1 SR_ACTOR_CELL_PHONE_NO val varchar2(255)
			// 2 SR_ACTOR_FAX_PHONE_NO val varchar2(255)
			// 3 SR_ACTOR_PHONE_NUMBER val varchar2(255)
			// 4 SR_ACTOR_WORK_PHONE_NO val varchar2(255)
			// 5 SR_ACTOR_ID long 
			// 6 SR_ACTOR_TYPE string varchar2(255)
}
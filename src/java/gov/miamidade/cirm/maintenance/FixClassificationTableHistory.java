package gov.miamidade.cirm.maintenance;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.sharegov.cirm.Refs;
import org.sharegov.cirm.rdb.RelationalStoreExt;

/**
 * Garbage collects the CIRM_CLASSIFICATION table after Syed's fix.
 * 
 * @author Thomas Hilpold
 *
 */
public class FixClassificationTableHistory
{
	static final String DEFAULT_LOG_FILE_NAME = "C:\\temp\\FixClassificationTableHistory.log";

	final static int SLEEP_TIME_BETWEEN_INDIVIDUALS = 70; //ms
	final static int MAX_HISTORY_SUBJECTS = 100000;
	//final static int MAX_HISTORY_LENGTH = 1; //per class - not 100% correct, but will do.
	final static String SELECT_MAX_CLASS_HIST = 
			"select A.*, CIRM_IRI.IRI, CIRM_IRI.IRI_TYPE_ID FROM " 
			+" ( "
			+" select COUNT(*) as COUNT, SUBJECT  from CIRM_CLASSIFICATION " 
			+" where SUBJECT in "
			+"( "
			+" select SUBJECT FROM CIRM_CLASSIFICATION "
			+" group by SUBJECT, OWLCLASS "
			+" having count(*) > 1 "
			+" ) "
			+" group by SUBJECT "
			+" ) A, CIRM_IRI "
			+" where A.SUBJECT = CIRM_IRI.ID " 
			+" order by A.COUNT desc, subject asc ";
//
//			" select A.*, CIRM_IRI.IRI, CIRM_IRI.IRI_TYPE_ID FROM " 
//			+" ( "
//			+"		select COUNT(*) as COUNT, SUBJECT, OWLCLASS  from CIRM_CLASSIFICATION " 
//			+"		group by SUBJECT, OWLCLASS "
//			+"		) A, CIRM_IRI "
//			+"		where A.SUBJECT = CIRM_IRI.ID "
//			+"		AND A.COUNT > " + MAX_HISTORY_LENGTH + " "
//			+"		order by A.COUNT desc, A.SUBJECT asc ";

	final static String SELECT_ALL_HISTORY_ORDERED_FROM_DESC = "select SUBJECT, OWLCLASS, FROM_DATE, TO_DATE from CIRM_CLASSIFICATION where SUBJECT = ? ORDER BY FROM_DATE DESC";
	//final static Strin
	
	private FileWriter logWriter;
	private int totalSubjectsFixed = 0;
	private int totalDeletedRows = 0;
	private int totalupdatedRows = 0;
	
	public void start() 
	{
		try
		{
			logWriter =  new FileWriter(DEFAULT_LOG_FILE_NAME, true);
		} catch (IOException e1)
		{
			throw new RuntimeException(e1);
		}
		log("FixClassificationTableHistory started at " + new Date());
		List<BigHistorySubject> bigHistorySubjectList = null;
		RelationalStoreExt store = Refs.defaultRelationalStoreExt.resolve();
		int lastQueryResultSize = 0;
		do {
			try {
				log("Query for bigHistorySubjectList");
				bigHistorySubjectList = getCurrentMaxHistorySubjects(store);
				lastQueryResultSize = bigHistorySubjectList.size();
				log("bigHistorySubjectList size is " + bigHistorySubjectList.size());
				for(BigHistorySubject bigHistorySubject : bigHistorySubjectList)
				{
					fixClassificationHistory(bigHistorySubject, store);
					totalSubjectsFixed ++;
					log("Subjects: " + totalSubjectsFixed + " deletedRows: " + totalDeletedRows  + " updatedRows " + totalupdatedRows);
					try
					{
						Thread.sleep(SLEEP_TIME_BETWEEN_INDIVIDUALS);
					} catch (InterruptedException e)
					{
						e.printStackTrace();
					}
				}
			} catch (SQLException e) 
			{
				throw new RuntimeException(e);
			}
		} while (lastQueryResultSize == MAX_HISTORY_SUBJECTS);
		try
		{
			logWriter.close();
		} catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void fixClassificationHistory(BigHistorySubject bigHistorySubject, RelationalStoreExt store )
	{
		if(!(bigHistorySubject.subject > 0 ))
			throw new IllegalArgumentException("Invalid subject id of " +bigHistorySubject.subject );
		
		Connection con = null;
		PreparedStatement s = null;
		ResultSet rs = null;
		try {			
			con = store.getConnection();			
			s = con.prepareStatement(SELECT_ALL_HISTORY_ORDERED_FROM_DESC, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
			s.setLong(1, bigHistorySubject.subject);
			rs = s.executeQuery();	
			log("" + totalSubjectsFixed + "| Compressing subject history starting: " + bigHistorySubject.subject + " " + bigHistorySubject.iri + " Cur history size: " + bigHistorySubject.count + " ( type id: " + bigHistorySubject.iri_type_id + ")");
			long segmentOwlClass = -1;
			Timestamp minimumFrom = null;
			Timestamp maximumFrom = null;
			long subject = -1;
			long owlclass = -1;
			Timestamp from = null;
			Timestamp to = null;	
			int rowsInClassSegmentCount = 0;
			int segmentCount = 0;
			while (rs.next())
			{				
				subject = rs.getLong("SUBJECT");
				if(subject != bigHistorySubject.subject )
					throw new IllegalStateException("subjects not equal");
				owlclass = rs.getLong("OWLCLASS");
				from = rs.getTimestamp("FROM_DATE");
				to = rs.getTimestamp("TO_DATE");
				if(owlclass != segmentOwlClass)
				{					
					if (segmentOwlClass > 0 && rowsInClassSegmentCount > 1) 
					{
						//	lastowlClass Segment finished, update row to retain with max/min found before cur row
						updateMaximumFrom(subject, segmentOwlClass, maximumFrom, minimumFrom,store);					
					}
					if (segmentOwlClass > 0) 
						log("Nr of rows in segment for owlclass: " + segmentOwlClass + "determined: " + rowsInClassSegmentCount);
					//initialize next segment with cur row
					maximumFrom = from;
					minimumFrom = from;
					segmentOwlClass = owlclass;
					log("Row to retain for new segment " + segmentCount + " determined " + bigHistorySubject.subject + " OldFrom " + maximumFrom + " To: " + to + " segmentOwlclass: " + segmentOwlClass); 	
					rowsInClassSegmentCount = 1;
					segmentCount ++;
				}
				else
				{
					//work
					if(from.before(minimumFrom))
					{
						minimumFrom = from;
					}
					rs.deleteRow();
					totalDeletedRows ++;
					rowsInClassSegmentCount ++;
				}
			} //loop over full history of subject order from desc
			//update last owlclass segment from it's min from date.
			if (segmentOwlClass > 0) 
				log("Nr of rows in segment for owlclass: " + segmentOwlClass + "determined: " + rowsInClassSegmentCount);
			if (rowsInClassSegmentCount > 1) {
				updateMaximumFrom(subject, segmentOwlClass, maximumFrom, minimumFrom,store);
			}
			con.commit();
			log("Compressing subject history completed: " + bigHistorySubject.subject + " " + bigHistorySubject.iri + " type id: " + bigHistorySubject.iri_type_id);
		}catch (SQLException e) 
		{
			try
			{
				log("Compressing subject history failed: " + bigHistorySubject.subject + " " + bigHistorySubject.iri + " type id: " + bigHistorySubject.iri_type_id);
				con.rollback();
			} catch (SQLException e1)
			{
				e1.printStackTrace();
			}
			throw new RuntimeException(e);
		}
		finally {
			try {
				if (rs != null) rs.close();
				if (s != null) s.close();
				if (con != null) con.close();
			} catch (SQLException e) 
			{
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * Updates the FROM_DATE value from the maximum value to the minimumfrom of a classification history.
	 * Only this one row should remain instead of one row per save.
	 * 
	 * @param subject
	 * @param owlclass
	 * @param maximumFrom
	 * @param minimumFrom
	 * @param store
	 */
	private void updateMaximumFrom(long subject, long owlclass, Timestamp maximumFrom, Timestamp minimumFrom, RelationalStoreExt store )
	{
		if (subject < 1) throw new IllegalArgumentException();
		if (owlclass < 1) throw new IllegalArgumentException();
		if (maximumFrom == null) throw new IllegalArgumentException();
		if (minimumFrom == null) throw new IllegalArgumentException();
		if (minimumFrom.after(maximumFrom)) throw new IllegalArgumentException();
		if (store == null) throw new IllegalArgumentException();
		Connection con = null;
		PreparedStatement s = null;
		ResultSet rs = null;
		try {
			log("Updating classification row to retain: Setting oldFrom: " + maximumFrom + " to min from in segement " + minimumFrom);
			con = store.getConnection();
			s = con.prepareStatement("UPDATE CIRM_CLASSIFICATION SET FROM_DATE = ? where SUBJECT = ? and OWLCLASS = ? and FROM_DATE = ? "  );
			s.setTimestamp(1,minimumFrom);
			s.setLong(2,subject);
			s.setLong(3,owlclass);
			s.setTimestamp(4,maximumFrom);
			int updateRowCount =  s.executeUpdate();
			if(updateRowCount != 1)
			{
				System.out.println("Error Updated rows != 1 ---updated count ( " + updateRowCount + " ) for subject :" + subject);
			}
			con.commit();
			totalupdatedRows++;		
		}catch (Throwable e)
		{ 
			try
			{
				con.rollback();
			}catch(SQLException sqle)
			{ 
				System.err.println("Error on rollback: " + e);
				e.printStackTrace();
			}
		}
		finally {
			try {
				if (rs != null) rs.close();
				if (s != null) s.close();
				if (con != null) con.close();
			} catch (SQLException e) 
			{
				throw new RuntimeException(e);
			}
		}
		
	}

	/**
	 * Returns a list of SUBJECT to OWLCLASS entries.
	 * @param store
	 * @return
	 */
	public List<BigHistorySubject> getCurrentMaxHistorySubjects(RelationalStoreExt store) throws SQLException 
	{
		List<BigHistorySubject> result = new LinkedList<FixClassificationTableHistory.BigHistorySubject>();
		Connection con = null;
		Statement s = null;
		ResultSet rs = null;
		try {
			con = store.getConnection();
			s = con.createStatement();
			rs = s.executeQuery(SELECT_MAX_CLASS_HIST);
			int count = 0;
			while (rs.next() && count < MAX_HISTORY_SUBJECTS)
			{
				BigHistorySubject cur = new BigHistorySubject();
				cur.count = rs.getInt("COUNT");
				cur.subject = rs.getLong("SUBJECT");
				//cur.owlclass = rs.getLong("OWLCLASS");
				cur.iri = rs.getString("IRI");
				cur.iri_type_id = rs.getInt("IRI_TYPE_ID");
				result.add(cur);
				count ++;
			}
		}
		finally {
			if (rs != null) rs.close();
			if (s != null) s.close();
			if (con != null) con.close();
		}
		return result;
	}
	
	DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
	public void log(String s) 
	{
		String logS = df.format(new Date()) + "| " + s;
		System.out.println(logS);
		try
		{
			logWriter.append(logS + "\r\n");
			logWriter.flush();
		} catch (IOException e)
		{
			throw new RuntimeException(e);
		}
		
	}
	
	public static void main(String[] argv) 
	{
		FixClassificationTableHistory x = new FixClassificationTableHistory();
		x.start();
	}
	
	static class BigHistorySubject 
	{
		long subject;
		//long owlclass;
		int count;
		String iri;
		int iri_type_id;
	}

}

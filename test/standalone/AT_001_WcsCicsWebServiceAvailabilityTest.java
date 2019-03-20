package standalone;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.sharegov.cirm.StartupUtils;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import mjson.Json;

/**
 * Continuously simulates a couple users querying WCS in WCS tab, while printing results every minute.<br>
 * This calls 6 WCS CICS web service endpoints with the exact same data each time.<br>
 * <br>
 * Warning: Intentionally does not terminate!<br>
 * <br>
 * Rationale for test:<br> 
 * Call center repeatedly reported problems in Jan 2016 with the WCS tab and we say higher than usual error responses from the<br> 
 * WCS CICS web services. <br>
 * <br>
 * @author Thomas Hilpold
 */
public class AT_001_WcsCicsWebServiceAvailabilityTest {
	public static final int NR_OF_USERS_SIMULATED = 1;

	@Test
	public void test() {
		StartupUtils.disableCertificateValidation();
		List<Thread> simulatedUsers = new ArrayList<>();
		for (int i = 0; i < NR_OF_USERS_SIMULATED; i++) {
			Thread t = new Thread(new WcsAccountQueryCaller());
			simulatedUsers.add(t);
			t.start();
		}		
		for(;;) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				System.out.println("Test exiting.");
			}
		}
	}

	/**
	 * Runnable that simulates a user that continuously calls all Query URLS and keeps statistics (across users) on success/error.
	 * 
	 * 
	 * @author Thomas Hilpold
	 *
	 */
	public static class WcsAccountQueryCaller implements Runnable {
		public static final long SLEEP_MS = 1000;

		//Must sync formats
		private static final DateFormat DFORMAT = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);

		
		private static List<String[]> errors = new LinkedList<>();
		private static volatile AtomicInteger successCount = new AtomicInteger(0);
		private static final String[] QUERY_URLS = getTestUrls(); 
		
		@Override
		public void run() {
			int runCount = 1;
			while (true) {
				runAllQueries(runCount);
				try {
					Thread.sleep(SLEEP_MS);
				} catch (InterruptedException e) {};
				runCount++;
			}			
		}
		
		void runAllQueries(int runCount) {
			for (int i = 0; i < QUERY_URLS.length; i++) {
				runQuery(QUERY_URLS[i], i, runCount);
			}
		}
		 
		void runQuery(String queryUrl, int idx, int runCount) {
			try {
				Json result = GenUtils.httpGetJson(queryUrl);
				if (result.toString().contains("error")) throw new RuntimeException("error in result");
				successCount.incrementAndGet();
			} catch (Exception e) {
				String thread = ThreadLocalStopwatch.getThreadName();
				addError(new String[] {thread, "" +runCount, now(), "" + idx, "" + e.getMessage()});
				printResultsNow();
			}
		}
		
		// Thread, run, time, endpointIdx, error
		public synchronized static List<String[]> getErrors() {
			return errors;
		}
		
		synchronized static void addError(String[] error) {
			errors.add(error);
		}
		
		static synchronized String now() {
			return DFORMAT.format(new Date());
		}

		/**
		 * Prints results so far including all error occurances in all threads/users.
		 * Thread safe.
		 */
		public synchronized static void printResultsNow() {
			List<String[]> e = getErrors();
			NumberFormat df = DecimalFormat.getPercentInstance();
			int totalExecutions = successCount.get() + e.size();
			double errorPercent = e.size() / (double) totalExecutions;
			ThreadLocalStopwatch.now("Printing Results until, " + now() 
				+ " Error rate (Error/Total %): " + df.format(errorPercent) 
				+ " Success: " + successCount.get() 
				+ " Errors: " + e.size()
				+ " Total: " + totalExecutions);
			System.out.println("Thread\tRun\ttime\tendpoint\terror");
			for (String[] row : e) {
				System.out.println(row[0] + "\t" + row[1] + "\t" + row[2] + "\t" + row[3] + "\t" + row[4]);
			}
			ThreadLocalStopwatch.now("Printing Results completed.");
		}
		
		/**
		 * These are the test endpoints with test data included. 
		 * @return
		 */
		public static String[] getTestUrls() {
			String endpoint1 = "https://localhost:8183/legacy/ws/WCSAccountQueryByFolio?arg=3078110060150&_=1453258848334";
			//String result1 = "{"result":{"WCS":{"RETURNMSG":"INVALID"}},"server":"77","ok":true}";
			String endpoint2 = "https://localhost:8183/legacy/ws/WCSAccountQueryByAddress?arg=18911%20SW%20312TH%20ST&_=1453258848573";
			// {"result":{"WCS":{"RETURNMSG":"MULTIPLE ADDRESSES","Accounts":[{"Account":{"AccountCode":"UH","AccountAddress":"18911 SW 312 ST","FolioNumber":3078110060150,"AccountNumber":12671668,"AccountName":"JOEL A LOBO"}},{"Account":{"AccountCode":"OR","AccountAddress":"18911 SW 312 ST","FolioNumber":3078110060150,"AccountNumber":40099549,"AccountName":"ANTONIO L BARRIENTOS & BARBARA"}}]}},"server":"77","ok":true} 
			String endpoint3 = "https://localhost:8183/legacy/ws/WCSAccountQueryByAccount?arg=12671668&_=1453259120062";
			//{"result":{"WCS":{"RETURNMSG":"QUERY SUCCESSFUL","Accounts":{"Account":{"TotalDue":0.0,"CurrentFee":0.0,"GrandTotalDue":0.0,"MailZip":"33030-3842","AccountAddress":"18911 SW 312 ST","MonthlyDeliquent":0.0,"AccountStatus":null,"MailState":"FL","Book":23,"AccountCode":"UH","BadCheckFee":0.0,"Trips":2,"FolioNumber":3078110060150,"MiscMail":null,"Route":7208,"Handicap":"N","BillStatus":"T","WasteUnit":1,"CreatedDate":"03/26/1997","CanRollCode":null,"AccountName":"JOEL A LOBO","OwnerName":"JOEL A LOBO","Paid":0.0,"BillingDate":"10/01/1997","AccountCodeDescription":"GARB,TRASH,TRC,RECYCLE","MailCo":null,"PriorYearAmount":0.0,"RegFeeCredit":0.0,"Phone":"000000-0000","MailCity":"MIAMI","OutServ":"N","CurrentServTax":0.0,"Bulky":0.0,"SrvStart":"SERVICE DATE","MailAddress":"18911 SW 312 ST","Delinquent":0.0,"Pickup":"TU & FR","Dist":8,"SrvDate":"03/26/1997","AccountType":"HOUSEHOLD","MailPhone":"000000-0000","Penalty":"00/0000      0.00","AccountNumber":12671668,"MailApt":null,"PreviousPenalty":"00/0000      0.00","Judgement":null,"Reg":7,"Cluc":"SINGLE","PriorYearTax":0.0,"OCL":0,"TaxUnit":1,"PenCode":"Y"}}}},"server":"77","ok":true}
			String endpoint4 = "https://localhost:8183/legacy/ws/WCSBulkyQueryByAccount?arg=12671668&_=1453259120320";
			//1 {"result":{"WCS":{"WorkOrders":[{"WorkOrder":{"SchActDate":"10/2012","OrderStatus":"FR","BulkyFee":0,"BulkyFeePaid":0,"WorkOrderNumber":55389200,"EstAct":5,"TotalFeeDue":0,"OrderDate":"10/12/2012"}},{"WorkOrder":{"SchActDate":"06/2013","OrderStatus":"FR","BulkyFee":0,"BulkyFeePaid":0,"WorkOrderNumber":60662900,"EstAct":6,"TotalFeeDue":0,"OrderDate":"06/18/2013"}},{"WorkOrder":{"SchActDate":"01/2015","OrderStatus":"FR","BulkyFee":0,"BulkyFeePaid":0,"WorkOrderNumber":75335400,"EstAct":5,"TotalFeeDue":0,"OrderDate":"01/08/2015"}},{"WorkOrder":{"SchActDate":"05/2015","OrderStatus":"FR","BulkyFee":0,"BulkyFeePaid":0,"WorkOrderNumber":78756900,"EstAct":8,"TotalFeeDue":0,"OrderDate":"05/19/2015"}},{"WorkOrder":{"SchActDate":"01/2016","OrderStatus":"PP","BulkyFee":0,"BulkyFeePaid":0,"WorkOrderNumber":85760700,"EstAct":0,"TotalFeeDue":0,"OrderDate":"01/19/2016"}}],"Address":"18911 SW 312 ST","RETURNMSG":"MULTIPLE BULKIES","Name":"JOEL A LOBO"}},"server":"77","ok":true}
			//4 {"result":{"WCS":{"RETURNMSG":"EMPTY"}},"server":"77","ok":true}
			String endpoint5 = "https://localhost:8183/legacy/ws/WCSPublicComplaintQueryByAccount?arg=12671668&_=1453259120485";
			//1 {"result":{"WCS":{"RETURNMSG":"EMPTY"}},"server":"77","ok":true}
			//4 {result: {WCS: {RETURNMSG: "EMPTY"}}, server: "77", ok: true}
			String endpoint6 = "https://localhost:8183/legacy/ws/WCSEnforcementComplaintQueryByAccount?arg=12671668&_=1453259120643";
			//1 {"result":{"WCS":{"EnforcementComplaints":[{"EnforcementComplaint":{"Status":"BC","CDate":" 1/ 7/2015","StatusDescription":"BULKY CLOSE","ComplaintNumber":10687191,"CDetails1":"T/C, M/T","BulkyOrderNumber":"753354 00"}},{"EnforcementComplaint":{"Status":"CO","CDate":" 4/ 8/2014","StatusDescription":"CLOSED","ComplaintNumber":10670225,"CDetails1":"FURN ITEMS, MISC.","BulkyOrderNumber":"000000 00"}},{"EnforcementComplaint":{"Status":"CO","CDate":" 1/28/2014","StatusDescription":"CLOSED","ComplaintNumber":10665376,"CDetails1":"W/C","BulkyOrderNumber":"000000 00"}}],"RETURNMSG":"MULTIPLE ENFORCEMENT COMPLAINTS"}},"server":"77","ok":true}
			//4 {"result":{"WCS":{"RETURNMSG":"EMPTY"}},"server":"77","ok":true}
			return new String[] { endpoint1, endpoint2, endpoint3, endpoint4, endpoint5, endpoint6 };
		}		
	}
}

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
package gov.miamidade.cirm.reports;

import static org.sharegov.cirm.rest.OperationService.getPersister;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import mjson.Json;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFPalette;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.util.CellRangeAddress;
import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.legacy.MessageManager;
import org.sharegov.cirm.rdb.Query;
import org.sharegov.cirm.rdb.QueryTranslator;
import org.sharegov.cirm.rdb.RelationalStore;
import org.sharegov.cirm.rest.LegacyEmulator;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ServiceRequestReportUtil;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;



/**
 * Report: RECYCLING SERVICE COMPLAINT - MD
 * 
 * Header:
 * Miami-Dade Department of Solid Waste Management 															
 * Recycling Complaints  															
 * 5/22/2013 (Date of report)															
 * 
 * LReport, LZone, LGis, LComplaint	
 * 																												VENDOR'S RESPONSE
 * ZONE		SR NUMBER 		ADDRESS		ZIP_		PHONE NUMBER		CREATED_DATE			ACTION TAKEN                         ACTION DATE 		
 * LZ1: <ZoneNr/>
 * LG1: <GIS_SWRECWK/>
 * LC1: <What is the type of Recycling problem experienced?>
 * LC2: 11A02	13-00127143			19410 NE 19TH PL      		33179		(305) 933-9006x2343		5/21/2013  11:07:40 AM
 *	   (Firstletter = zone)			(SR_ADDRESS)
 * LC3: COMPLAINT DETAIL: SR_DETAIL / <Please provide details associated to your concern:> (Merge 4h, 2v)
 * LC3:				
 * LZ2: Zone Total:	 <nrOfZone>
 * LR1: Grand Total: 
 * 
 * Usage:
 * "/reportRecyclingComplaints/create"...creates but does not send the reports.
 * "/reportRecyclingComplaints/createAndSend"...creates and sends the reports.
 * 
 * 
 * EMAILS WILL BE SENT TO:
 * The Recycling Complaints Z1, Z2 and Z4 go to:
 * TO:
 * mdcrecycling@worldwasteservices.com
 * CC:
 * Customer Service Unit (PWWM);Jeanmarie Massa;
 * swmcsu@miamidade.gov;massaj@miamidade.gov;
 *
 *
 * The Recycling Complaints Z3 go to:
 * TO:
 * CS-MIAMIDADE-RECYCLI@WASTESERVICESINC.COM;  
 * Sametha.Lovett@progressivewaste.com;
 * Gerardo.Cardona@progressivewaste.com; 
 * Jacqueson.Bernard@progressivewaste.com;
 * Kimberly.Diljohn@progressivewaste.com; 
 * Joe.Ruiz@progressivewaste.com;
 * Maria.Suarez@progressivewaste.com
 * CC: same
 * 
 * To email to cirmtest@miamidade.gov, set MessageManager Test mode or set EMAIL_TESTMODE = true;
 * 
 * @author Thomas Hilpold
 */
@Path("reportRecyclingComplaints") 
public class ReportRecyclingComplaintsXls
{
	public static final boolean EMAIL_TEST_MODE = false; //true overrides a MessageManager.isTestMode()==false, false respects MM setting.
	public static final String EMAIL_TO1 = "mdcrecycling@worldwasteservices.com";
	public static final String EMAIL_TO2 = "CS-MIAMIDADE-RECYCLI@WASTESERVICESINC.COM;"  
						+ "Sametha.Lovett@progressivewaste.com;" 
						+ "Gerardo.Cardona@progressivewaste.com;" 
						+ "Jacqueson.Bernard@progressivewaste.com;" 
						+ "Kimberly.Diljohn@progressivewaste.com;" 
						+ "Joe.Ruiz@progressivewaste.com;"
						+ "Maria.Suarez@progressivewaste.com";
	public static final String EMAIL_CC12 = "swmcsu@miamidade.gov;massaj@miamidade.gov";
	public static final String EMAIL_BCC12 = "cirmtest@miamidade.gov";
	public static final String EMAIL_FROM12 = "cirm@miamidade.gov";
	public static final String EMAIL_TEST_TO12 = "cirmtest@miamidade.gov";
	public static final String EMAIL_TEST_FROM12 = "cirmtest@miamidade.gov";
	public static final String EMAIL_SUBJECT1 = "MDC Dept of Solid Waste - Recycling Complaints Report Zone 1,2,4";
	public static final String EMAIL_SUBJECT2 = "MDC Dept of Solid Waste - Recycling Complaints Report Zone 3";
	public static final String EMAIL_TEXT = "Dear Sir or Madam, \r\n\r\n\r\n"
			+ "Please find attached the Recycling Complaints Report covering $$REPORT_DETAIL$$.\r\n" //zone three for the time range Friday through Sunday."
			+ "We hope it meets your expectations.\r\n\r\n"
			+ "This email and the report were automatically generated. \r\n\r\n"
			+ "\r\n"
			+ "Kind regards,\r\n\r\n\r\n"
			+ "Your Miami-Dade 311 Hub Team\r\n\r\n\r\n"
			+ "In case of error please respond to swmcsu@miamidade.gov (Customer Service Unit (PWWM))";

	public static final String EMAIL_VAR_REPORT_DETAIL = "$$REPORT_DETAIL$$";
		
	public static final String REPORTZ1Z2Z4_FILENAME = "RecyclingComplaints_Z1_Z2_Z4.xls";
	public static final String REPORTZ3_FILENAME = "RecyclingComplaints_Z3.xls";	
	public static final boolean DBG_USE_90DAY_PERIOD = false;
	public static final boolean DBG = true;
	
	public static final String HEADER_1 = "Miami-Dade Department of Solid Waste Management";
	public static final String HEADER_2 = "Recycling Complaints";
	// 3 = Date 5/22/2013
	public static final String HEADER_4 = "prepared by 311Hub";

	public static final String HEADER_COLS1 = "VENDOR'S RESPONSE";
	public static final String[] HEADER_COLS2 = new String[] { "ZONE", "SR NUMBER", "ADDRESS", "ZIP", "PHONE NUMBER", "CREATED DATE", "ACTION TAKEN", "ACTION DATE" };
	public static final int[] COL_WIDTHS = new int[] 	     {  95,				 135,	 200, 	65, 	155,		 	135, 			230,  			230 };
	public static final float COL_WIDTH_FACTOR = 31f;
	public static final int NR_OF_COLUMNS = 8;

	public static final int STYLE_DEFAULT = 1;
	public static final int STYLE_BOLD = 2;
	public static final int STYLE_CENTERED = 3;
	public static final int STYLE_RBOUND = 4;
	public static final int STYLE_HEADER = 5; //Bold 14, Centered
	public static final int STYLE_COL_HEADER = 6; //Bold 14, Centered
	public static final int STYLE_ZONE_NR = 7;
	public static final int STYLE_10_X = 8;
	public static final int STYLE_VENDOR = 9;
	public static final int STYLE_COMPLAINT = 10;

	private Map<Integer, CellStyle> stylesMap;
	
	private File directoryPath;
	
	private LegacyEmulator legacyEmulator;
	private HSSFSheet sheet;
	private HSSFWorkbook workbook;
	private int rowIdx = 0;
	private ListIterator<Json> curReportResultArrayIt = null; // [{ "RNUM": "34", "GIS_SWRECWK": "21B10", "SR_REQUEST_ID": "3025860" }]
	private String emailVarReportDetailZ124 = "zones one, two, and four for ";
	private String emailVarReportDetailZ3 = "zone three for ";
	
    /**
     * Creates today's recycling complaint reports, saves them as files in io.temp dir and sends them to recipients.
     * To Send test emails, have MessageManager in Test Mode or set DBG_TEST
     * A Monday report will include open cases of Fri, Sat and Sun. All others contain open cases of the previous day.
     * @return
     */
    @GET
    @Path("createAndSend")
    public synchronized Json createAndSend()
    {
		Json result =  Refs.defaultRelationalStore.resolve().txn(new CirmTransaction<Json>() {
		public synchronized Json call()
		{			
	    	//Date reportDate = new Date(new Date().getTime() - 30L * 24*60*60*1000); DBG for 30 days earlier.
	    	Date reportDate = new Date(new Date().getTime());
	    	String tempDir = System.getProperty("java.io.tmpdir");
	    	setDirectoryPath(new File(tempDir));
	    	File[] twoReports = createReportsZ124AndZ3(reportDate);
	    	//So if sth retriable happens before this line, no emails will have been sent. If we get here, retriable exceptions are over.
	    	if (twoReports == null || twoReports.length != 2 || twoReports[0] == null || twoReports[1] == null)
	    		return GenUtils.ko("Report generation failed. Check logs! " + new Date());
	    	//send reports
	    	else
	    	{
		    	try {
		    		sendReports(twoReports);
			    	return GenUtils.ok();
		    	} catch(Exception e) 
		    	{
		    		System.err.println(e);
		    		e.printStackTrace(System.err);
		    		return GenUtils.ko("Report generation ok, but sending reports by email failed. Check logs! " + new Date());	
		    	}
	    	}
	    		
		}});
		return result;
    }

    /**
     * Creates today's recycling complaint reports and saves them as files in io.temp dir
     * A Monday report will include open cases of Fri, Sat and Sun. All others contain open cases of the previous day.
     * @return
     */
    @GET
    @Path("create")
    public synchronized Json create()
    {
		Json result =  Refs.defaultRelationalStore.resolve().txn(new CirmTransaction<Json>() {
		public Json call()
		{			
	    	//Date reportDate = new Date(new Date().getTime() - 30L * 24*60*60*1000); DBG for 30 days earlier.
	    	Date reportDate = new Date(new Date().getTime());
	    	String tempDir = System.getProperty("java.io.tmpdir");
	    	setDirectoryPath(new File(tempDir));
	    	File[] twoReports = createReportsZ124AndZ3(reportDate);
	    	if (twoReports != null && twoReports.length == 2 && twoReports[0] != null && twoReports[1] != null)
	    		return GenUtils.ok();
	    	else 
	    		return GenUtils.ko("Report generation failed. Check logs! " + new Date());
		}});
		return result;
    }
    
    // Email
    protected synchronized void sendReports(File[] reportFiles) throws MessagingException 
    {
    	ThreadLocalStopwatch.getWatch().time("START SENDING RECYCLING REPORT EMAILS (two emails).");
    	MessageManager mm = MessageManager.get(); 
    	boolean testMode = mm.isTestMode() || EMAIL_TEST_MODE;
    	//Email for Report 1
    	String emailText1 = EMAIL_TEXT.replace(EMAIL_VAR_REPORT_DETAIL, emailVarReportDetailZ124); 
        Multipart multiPart1 = new MimeMultipart();  
 
        MimeBodyPart messageText1 = new MimeBodyPart();  
        messageText1.setContent(emailText1, "text/plain");  
        multiPart1.addBodyPart(messageText1);  
        //Attach Report File 1
        MimeBodyPart rarAttachment1 = new MimeBodyPart();  
        FileDataSource rarFile1 = new FileDataSource(reportFiles[0]);  
        rarAttachment1.setDataHandler(new DataHandler(rarFile1));  
        rarAttachment1.setFileName(rarFile1.getName());  
        multiPart1.addBodyPart(rarAttachment1);
        
        //Email for Report 2        
    	String emailText2 = EMAIL_TEXT.replace(EMAIL_VAR_REPORT_DETAIL, emailVarReportDetailZ3); 
        Multipart multiPart2 = new MimeMultipart();  
 
        MimeBodyPart messageText2 = new MimeBodyPart();  
        messageText2.setContent(emailText2, "text/plain");  
        multiPart2.addBodyPart(messageText2);  
        //Report File 1
        MimeBodyPart rarAttachment2 = new MimeBodyPart();  
        FileDataSource rarFile2 = new FileDataSource(reportFiles[1]);  
        rarAttachment2.setDataHandler(new DataHandler(rarFile2));  
        rarAttachment2.setFileName(rarFile2.getName());  
        multiPart2.addBodyPart(rarAttachment2);
        int successSend = 0;
    	try {
    		if (testMode) 
    		{
    			mm.sendEmail(EMAIL_TEST_FROM12, EMAIL_TEST_TO12, null, null, EMAIL_SUBJECT1, multiPart1);
    			successSend++;
    			mm.sendEmail(EMAIL_TEST_FROM12, EMAIL_TEST_TO12, null, null, EMAIL_SUBJECT2, multiPart2);
    			successSend++;
    		} else
    		{
    			mm.sendEmail(EMAIL_FROM12, EMAIL_TO1, EMAIL_CC12, EMAIL_BCC12, EMAIL_SUBJECT1, multiPart1);
    			successSend++;
    			mm.sendEmail(EMAIL_FROM12, EMAIL_TO2, EMAIL_CC12, EMAIL_BCC12, EMAIL_SUBJECT2, multiPart2);
    			successSend++;
    		}
    	}catch (Exception e) 
    	{	
    		ThreadLocalStopwatch.getWatch().time("FAILED SENDING RECYCLING REPORT EMAILS failed with: " + e);
    		System.err.println("ReportRecyclingComplaintsXls:sendReports mm.sendEmail " + successSend + " of 2 emails were sucessfully sent" );
    		System.err.println("ReportRecyclingComplaintsXls:sendReports Subject 1: " + EMAIL_SUBJECT1);
    		System.err.println("ReportRecyclingComplaintsXls:sendReports Subject 2: " + EMAIL_SUBJECT2);
    		throw new RuntimeException(e);
    	}
    	ThreadLocalStopwatch.getWatch().time("END SENDING RECYCLING REPORT EMAILS succeeded. (two emails sent).");
    }
    
    
    //-------------------------------------- PUBLIC INTERFACE ---------------------------------------------
    
	/**
     * Requirements (Assia):
     * The reports come in Monday, Tuesday, Wednesday, Thursday and Friday.     
	 *  The calls or emails that come in on Friday, Saturday and Sunday show up on the Monday report.  
     * @param date
     * @return
     */
    public Date[] getFromToDatesForReportOn(Date date) 
    {
    	Date from;
    	Date to;
    	Calendar cal = Calendar.getInstance(); //server time zone!
    	cal.setTime(date);
    	cal.set(Calendar.HOUR_OF_DAY, 0);
    	cal.set(Calendar.MINUTE, 0);
    	cal.set(Calendar.SECOND, 0);
    	cal.set(Calendar.MILLISECOND, 0);
		to = cal.getTime();
		int reportDays;
    	if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) 
    	{
    		//From = FriAlpha, to = SunOmega
    		reportDays = DBG_USE_90DAY_PERIOD? 90 : 3;
   			cal.add(Calendar.DATE, - reportDays);
    		from = cal.getTime();
    	}
    	else
    	{
    		reportDays = DBG_USE_90DAY_PERIOD? 90 : 1;
    		cal.add(Calendar.DATE, - reportDays);
    		from = cal.getTime();
    	}
    	//Set to to last Millisecond of prev day
    	cal = Calendar.getInstance();
    	cal.setTime(to);
    	cal.add(Calendar.MILLISECOND, -1);
    	to = cal.getTime();
    	return new Date[] {from, to};
    }
    
	/**
	 * Creates a query finding all SRs for a given time range and zone.
	 * 
	 * :{"hasGeoPropertySet":{"type":"GeoPropertySet","GIS_SWRECWK":"like(3%)"},
	 * "sortBy":"boid","sortDirection":"desc",
	 * "currentPage":2,"type":"legacy:SWMRECIS","itemsPerPage":15,
	 * "hasDateCreated":"between(2013-04-25T00:00:00.000,2013-05-31T23:59:59.999)"}
	 *
	 * @param from
	 * @param to
	 * @param zones
	 * @return
	 */
    public Query createQuery(Date from, Date to, int zone, RelationalStore store)
    {
    	if (from == null || to == null || zone < 1 || to.before(from)) 
    		throw new IllegalArgumentException("from " + from + " before to ?" + to + " zone > 0 ?" + zone);
    	String fromStr = GenUtils.formatDate(from);
    	String toStr = GenUtils.formatDate(to);    	
    	Json pattern = Json.object();
    	pattern.set("caseSensitive", false);
    	pattern.set("currentPage", 1);
    	pattern.set("itemsPerPage", 1000);
    	pattern.set("sortBy","GIS_SWRECWK");
    	pattern.set("sortDirection","asc");
    	//Do not change the type of Service Request. As the report is specific to this type.
    	//pattern.set("type", Json.array("legacy:SWMRECIS"));
    	pattern.set("type", Json.array("legacy:SWMRECIS"));
    	//pattern.set("hasStatus", "O-OPEN");
    	pattern.set("hasDateCreated", "between("+ fromStr + "," + toStr + ")");
    	Json geoSet = Json.object();
    	geoSet.set("type", "GeoPropertySet");
    	geoSet.set("GIS_SWRECWK", "like ("+ zone + "%)");
    	pattern.set("hasGeoPropertySet", geoSet);
    	QueryTranslator qt = new QueryTranslator();
    	Query q = qt.translate(pattern, store);
    	q.getStatement().getSql().COLUMN("GIS_SWRECWK");
    	//q.getStatement().getSql().ORDER_BY("GIS_SWRECWK");
    	//q.getStatement().getSql().ORDER_DIRECTION("asc");
    	if (DBG) System.out.println("ReportQuery: " + q.getStatement().getSql().SQL());
    	return q;
    }
	       
    /**
     * To be called within a serializable transaction!
     * 
     * TODO maybe a no zone report for failed GIS.
     * @return 2 files, one for Z1, Z2 and 4, one for Z3
     */
    public synchronized File[] createReportsZ124AndZ3(Date reportDate) 
    {
		File [] result = null;
    	ThreadLocalStopwatch.getWatch().time("START Create RecyclingComplaintReports");
		RelationalStore store = getPersister().getStore();
		//OWLDataFactory df = Refs.tempOntoManager.resolve().getOWLDataFactory();
		Json boidsOrderedR1Z12Z4;
		Json boidsOrderedR2Z3;
		Query qZ1, qZ2, qZ4, qZ3;
		// Determine Dates
    	// Report file 1 zones 1&2&4
    	Date[] fromTo = getFromToDatesForReportOn(reportDate);
    	SimpleDateFormat weekDayDf = new SimpleDateFormat("EEEEEE");
    	String fromStr = weekDayDf.format(fromTo[0]);
    	String toStr = weekDayDf.format(fromTo[1]);
    	String weekDayRange = (fromStr.equals(toStr))? fromStr : fromStr + " to " + toStr; 
    	emailVarReportDetailZ124 += weekDayRange;
    	emailVarReportDetailZ3 += weekDayRange;
		qZ1 = createQuery(fromTo[0], fromTo[1], 1, store);
		qZ2 = createQuery(fromTo[0], fromTo[1], 2, store);
		qZ4 = createQuery(fromTo[0], fromTo[1], 4, store);
		try
		{
			boidsOrderedR1Z12Z4 = store.customSearch(qZ1);
			boidsOrderedR1Z12Z4.with(store.customSearch(qZ2));
			boidsOrderedR1Z12Z4.with(store.customSearch(qZ4));

			// Report file 2 Zone 3
			qZ3 = createQuery(fromTo[0], fromTo[1], 3, store);
			boidsOrderedR2Z3 = store.customSearch(qZ3);
			ThreadLocalStopwatch.getWatch().time("Create RecyclingComplaintReports queries finished found Z1Z24 : " 
					+ boidsOrderedR1Z12Z4.asJsonList().size() +  " Z3 " + boidsOrderedR2Z3.asJsonList().size());
			//Create Report Z1Z2Z4
			File r1 = createReport(boidsOrderedR1Z12Z4, reportDate, REPORTZ1Z2Z4_FILENAME);
			//Create Report Z3
			File r2 = createReport(boidsOrderedR2Z3, reportDate, REPORTZ3_FILENAME);
			ThreadLocalStopwatch.getWatch().time("END Create RecyclingComplaintReports for Z124 and Z3");
			result = new File[] {r1, r2};
		} catch (SQLException e)
		{
			ThreadLocalStopwatch.getWatch().time("FAILED Create RecyclingComplaintReports for Z124 and Z3");
			e.printStackTrace();
		}
    	return result;
    }
    
    
    /**
     * Creates one report for the given ordered SR_REQUEST_ID/GIS_SWRECWK array.
     * It must be orderd by GIS_SWRECWK ascending.
     * RNUM should be ignored. 
     * To be called within a serializable transaction!
     * @param boidsOrdered arr of { "RNUM": "32", "GIS_SWRECWK": "21B03", "SR_REQUEST_ID": "3024810" }
     * @param reportDate
     * @return
     */
    public synchronized File createReport(Json boidsOrdered, Date reportDate, String fileName) 
    {
    	addReportRows(reportDate, boidsOrdered);    
    	//TODO save file, return filename.
    	File rf = null;
    	try {
    		rf = new File(getDirectoryPath().getAbsolutePath() +  File.separatorChar + fileName);
    		FileOutputStream fileOut = new FileOutputStream(rf);
    		workbook.write(fileOut);
    		fileOut.close();
    	} catch (Exception e)
    	{
    		rf = null;
    		System.err.println("ReportRecyclingComplaints:createReport: " +e);
    	}
    	if (DBG) System.out.println("ReportRecyclingComplaints: created " + (rf == null? "NULL!!!": rf.getAbsolutePath()));
    	return rf;
    }

    //----------------------------- PROTECTED METHODS ------------------------------
	protected String getZone(Json resultRow)
	{
		return "" + resultRow.at("GIS_SWRECWK", "0").asString().charAt(0);
	}

	protected String getGIS_SWRECWK(Json resultRow)
	{
		return resultRow.at("GIS_SWRECWK", "").asString();
	}

	protected long getSRBoid(Json resultRow)
	{
		return resultRow.at("SR_REQUEST_ID", "-1").asLong();
	}

	protected boolean hasNextRs() 
	{
		return curReportResultArrayIt.hasNext();
	}
	/**
	 * Returns the next result row and moves iterator forward.
	 * @return
	 */
	protected Json nextRs() 
	{
		return hasNextRs()? curReportResultArrayIt.next(): Json.object();
	}

	/**
	 * Returns the previous result row and moves iterator back.
	 * @return
	 */
	protected Json prevRs() 
	{
		return curReportResultArrayIt.previous();
	}
	
	protected Json readReport(long boid)
	{
		return legacyEmulator.findServiceCaseOntology(boid).toJSON();
	}

	public synchronized void setDirectoryPath(File directoryPath) 
	{
		if (!directoryPath.isDirectory() || ! directoryPath.canWrite())
			throw new IllegalArgumentException("Not acceptable as report directory (no dir or no write): " + directoryPath);
	
		this.directoryPath = directoryPath;
	}
	
	public synchronized File getDirectoryPath() 
	{
		return directoryPath;
	}
	
	protected void initReport(Json resultArray) 
	{
		legacyEmulator = new LegacyEmulator();
		stylesMap = null;
		rowIdx = 0;
		workbook = new HSSFWorkbook();
		sheet = workbook.createSheet();
		if (resultArray == null || !resultArray.isArray()) 
		{
			resultArray = Json.array();
			System.err.println("ExcelReportRecyclingComplaints: search result was not a JSON array, was : " + resultArray);
		}
		curReportResultArrayIt = resultArray.asJsonList().listIterator();
		// Customize color
		HSSFPalette palette = workbook.getCustomPalette();

	    //replacing the standard red with freebsd.org red
	    palette.setColorAtIndex(HSSFColor.LIGHT_BLUE.index,
	            (byte) 200,  //RGB red (0-255)
	            (byte) 200,    //RGB green
	            (byte) 255     //RGB blue
	    );
	}
	
	/**
	 * Create Workbook
	 */
	protected void addReportRows(Date reportDate, Json boidsOrdered)
	{
		initReport(boidsOrdered);
		//Header
		addHeaderRows(reportDate);
//	    boldStyle.setWrapText(true);
		addZoneSections();
		for (int i = 0; i < NR_OF_COLUMNS; i++)
		{
			sheet.setColumnWidth(i, getColWidth(i));
		}
		HSSFRow hr = sheet.createRow(rowIdx);
		HSSFCell hc = hr.createCell(0);
		hc.setCellStyle(getStyle(STYLE_ZONE_NR));
		hc.setCellValue("All Total:");
		hc = hr.createCell(1);
		hc.setCellStyle(getStyle(STYLE_BOLD));
		hc.setCellValue(curReportResultArrayIt.nextIndex());		
	}
	
	/**
	 * 6 rows.
	 * @param reportDate
	 */
	protected void addHeaderRows(Date reportDate)
	{
		HSSFCell hc;
		int firstRowIdx = rowIdx;
		//0
		hc = sheet.createRow(rowIdx++).createCell(0);
		hc.setCellStyle(getStyle(STYLE_HEADER));
		hc.setCellValue(HEADER_1);
		sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, NR_OF_COLUMNS - 1));
		//1
		hc = sheet.createRow(rowIdx++).createCell(0);
		hc.setCellStyle(getStyle(STYLE_HEADER));
		hc.setCellValue(HEADER_2);
		sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, NR_OF_COLUMNS - 1));
		//2
		hc = sheet.createRow(rowIdx++).createCell(0);
		hc.setCellStyle(getStyle(STYLE_HEADER));
		SimpleDateFormat df = new SimpleDateFormat("EEE, MM/dd/yy");
		hc.setCellValue(df.format(reportDate));
		sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, NR_OF_COLUMNS - 1));
		//3 empty
		hc = sheet.createRow(rowIdx++).createCell(0);
		hc.setCellStyle(getStyle(STYLE_RBOUND));
		hc.setCellValue(HEADER_4);
		sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, NR_OF_COLUMNS - 1));
		//4 Vendors response
		hc = sheet.createRow(rowIdx++).createCell(6);
		hc.setCellStyle(getStyle(STYLE_CENTERED));
		hc.setCellValue(HEADER_COLS1);
		sheet.addMergedRegion(new CellRangeAddress(4, 4, 6, NR_OF_COLUMNS - 1));
		//5 all 7 column headers
		HSSFRow hr = sheet.createRow(rowIdx++);
		for (int i = 0; i < NR_OF_COLUMNS; i++)
		{
			hc = hr.createCell(i);
			hc.setCellStyle(getStyle(STYLE_COL_HEADER));
			hc.setCellValue(HEADER_COLS2[i]);			
			sheet.setColumnWidth(i, getColWidth(i));
		}
		//Set row height
		for (int i = firstRowIdx; i < rowIdx; i++)
		{
			sheet.getRow(i).setHeightInPoints(15);
		}
		//assert(rowIdx == 6);
	}
	
	protected CellStyle getStyle(int STYLE)
	{
		if (stylesMap == null)
		{
			stylesMap = new HashMap<Integer, CellStyle>();
			//Default
			CellStyle style = workbook.createCellStyle();
			stylesMap.put(STYLE_DEFAULT, style);
			//Centerd
			style = workbook.createCellStyle();
			style.setAlignment(CellStyle.ALIGN_CENTER);
			stylesMap.put(STYLE_CENTERED, style);
			//RBOUND
			style = workbook.createCellStyle();
			style.setAlignment(CellStyle.ALIGN_RIGHT);
			stylesMap.put(STYLE_RBOUND, style);
			//Bold
			style = workbook.createCellStyle();
			Font f = workbook.createFont();
			f.setFontHeightInPoints((short)10);
			f.setBoldweight(Font.BOLDWEIGHT_BOLD);
			style.setFont(f);
			stylesMap.put(STYLE_BOLD, style);
			//Merged Header Cells
			style = workbook.createCellStyle();
			f = workbook.createFont();
			f.setFontHeightInPoints((short)12);
			f.setBoldweight(Font.BOLDWEIGHT_BOLD);
			style.setFont(f);
			style.setAlignment(CellStyle.ALIGN_CENTER);
			stylesMap.put(STYLE_HEADER, style);
			//COL HEADER
			style = workbook.createCellStyle();
			f = workbook.createFont();
			f.setFontHeightInPoints((short)10);
			f.setUnderline(Font.U_SINGLE);
			style.setFont(f);
			style.setAlignment(CellStyle.ALIGN_LEFT);
			stylesMap.put(STYLE_COL_HEADER, style);
			//STYLE_ZONE_NR
			style = workbook.createCellStyle();
			f = workbook.createFont();
			f.setFontHeightInPoints((short)10);
			f.setBoldweight(Font.BOLDWEIGHT_BOLD);
			style.setFont(f);
			style.setAlignment(CellStyle.ALIGN_CENTER);
			style.setFillForegroundColor(HSSFColor.GREY_25_PERCENT.index);
			style.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);
			stylesMap.put(STYLE_ZONE_NR, style);
			//STYLE_10_X
			style = workbook.createCellStyle();
			f = workbook.createFont();
			f.setFontHeightInPoints((short)10);
			f.setBoldweight(Font.BOLDWEIGHT_BOLD);
			style.setFont(f);
			style.setAlignment(CellStyle.ALIGN_LEFT);
			style.setFillForegroundColor(HSSFColor.GREY_40_PERCENT.index);
			style.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);
			stylesMap.put(STYLE_10_X, style);
			//STYLE_VENDOR
			style = workbook.createCellStyle();
			style.setFillForegroundColor(HSSFColor.LIGHT_BLUE.index);
			style.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);
			stylesMap.put(STYLE_VENDOR, style);
			//STYLE_COMPLAINT
			style = workbook.createCellStyle();
			f = workbook.createFont();
			f.setFontHeightInPoints((short)10);
			style.setWrapText(true);
			style.setFont(f);
			style.setAlignment(CellStyle.ALIGN_LEFT);
			style.setVerticalAlignment(CellStyle.VERTICAL_TOP);
			stylesMap.put(STYLE_COMPLAINT, style);
		}
		CellStyle result = stylesMap.get(STYLE);
		if (result == null) {
			System.err.println("ExcelReportRecyclingComplaints: STYLE NOT FOUND, using default. Was: " + STYLE);
			return stylesMap.get(STYLE_DEFAULT);
		}
		return result;
	}
	
	public int getColWidth(int colIdx)
	{
		return (int)(COL_WIDTHS[colIdx] * COL_WIDTH_FACTOR);
	}

	
	protected void addZoneSections()
	{
		while (hasNextRs())
		{
			addZoneSection();
		}
	}
	
	protected String addZoneSection()
	{
		Json firstInZoneRs = nextRs();
		prevRs();
		String zone = getZone(firstInZoneRs);
		HSSFCell hc;
		hc = sheet.createRow(rowIdx++).createCell(0);
		hc.setCellStyle(getStyle(STYLE_ZONE_NR));
		hc.setCellValue(zone);
		int inZoneSrStartIdx = curReportResultArrayIt.nextIndex();
		//
		// Add GisCodeRows RESULT
		addGisCodeSections(zone);
		//
		HSSFRow hr = sheet.createRow(rowIdx++);
		hc = hr.createCell(0);
		hc.setCellStyle(getStyle(STYLE_ZONE_NR));
		hc.setCellValue("Zone Total:");
		hc = hr.createCell(1);
		hc.setCellStyle(getStyle(STYLE_BOLD));
		hc.setCellValue(curReportResultArrayIt.nextIndex() - inZoneSrStartIdx);
		for (int i = 0; i < 1; i++)
			sheet.createRow(rowIdx++);
		return zone;
	}
	

	protected void addGisCodeSections(String zone)
	{
		boolean sameZone = true;
		while (hasNextRs() && sameZone) 
		{
			addGisCodeSection();
			if (hasNextRs())
			{
				Json next = nextRs();			
				prevRs();
				sameZone = zone.equals(getZone(next));
			}
		} 
	}

	/**
	 * E.g. 11A02
	 * return the section
	 */
	protected String addGisCodeSection()
	{
		Json firstInCodeRs = nextRs();
		prevRs();
		String gisCode = getGIS_SWRECWK(firstInCodeRs);
		HSSFCell hc;
		hc = sheet.createRow(rowIdx++).createCell(0);
		hc.setCellStyle(getStyle(STYLE_BOLD));
		hc.setCellValue(gisCode);
		//
		// 	Add GisCodeRows RESULT
		addServiceCaseSections(gisCode);
		//
		for (int i = 0; i < 1; i++)
			sheet.createRow(rowIdx++);
		return gisCode;
	}
	
	protected void addServiceCaseSections(String gisCode)
	{
		boolean nextSameGisCode = true;
		while(hasNextRs() && nextSameGisCode)
		{
			Json resultRow = nextRs();
			Json serviceCase = readReport(getSRBoid(resultRow));
			addServiceCaseSection(gisCode, resultRow, serviceCase);
			if (hasNextRs())
			{
				Json next = nextRs();
				prevRs();
				nextSameGisCode = gisCode.equals(getGIS_SWRECWK(next));
			}
		}
	}
	
	protected void addServiceCaseSection(String gisCode, Json resultRow, Json serviceCase)
	{
		if (serviceCase.isNull())
		{
			addErrorRow(resultRow, "Could not read ServiceCase from DB");
			return;
		}
		List<Json> allAnswers = ServiceRequestReportUtil.getAllServiceAnswers(serviceCase);
		HSSFCell hc;
		//Row 0 10-X 
		//"What is the type of Recycling problem experienced?"
		String SA10_X = getServiceAnswer("SWMRECIS_RCIQ2", allAnswers);
		hc = sheet.createRow(rowIdx++).createCell(0);
		sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 1));
		hc.setCellStyle(getStyle(STYLE_10_X));
		hc.setCellValue(SA10_X);
		
		//Row 1 22B04	13-00127734			11610 SW 32ND ST      		33165		(305) 244-3784		5/21/2013  6:08:36 PM					
		HSSFRow hr = sheet.createRow(rowIdx++);
		List<String> colsValues = new ArrayList<String>();
		colsValues.add(gisCode);
		colsValues.add(serviceCase.at("properties", Json.object()).at("hasCaseNumber", "N/A").asString());
		colsValues.add(serviceCase.at("properties", Json.object()).at("atAddress", Json.object()).at("fullAddress", "N/A").asString());
		colsValues.add(serviceCase.at("properties", Json.object()).at("atAddress", Json.object()).at("Zip_Code", "N/A").asString());
		String phoneNumber = getServiceAnswer("SWMRECIS_RCIQ9", allAnswers);
		String extension = getServiceAnswer("SWMRECIS_RCIQ11", allAnswers);
		if (!extension.isEmpty())
			phoneNumber = phoneNumber + "x" + extension;
		colsValues.add(phoneNumber); //Phone num
		//SWMRECIS_RCIQ11 Phone Extension
		String dateCreatedStr = serviceCase.at("properties", Json.object()).at("hasDateCreated", "").asString();
		if (DBG) System.out.println(dateCreatedStr);
		if (dateCreatedStr.contains("T")) 
			dateCreatedStr = ServiceRequestReportUtil.formatRecyclingDate(dateCreatedStr);
		colsValues.add(dateCreatedStr); //col 0 - 6
		int i = 0;
		for (;i < 6; i++)
		{
			hc = hr.createCell(i);
			hc.setCellStyle(getStyle(STYLE_DEFAULT));
			hc.setCellValue(colsValues.get(i));
		}
		for (;i < 8; i++) 
		{
			hc = hr.createCell(i);
			hc.setCellStyle(getStyle(STYLE_VENDOR));
		}
		
		//ROW 2
		//COMPLAINT DETAIL:				 / CALLER SAYS THAT THE RECYCLING WAS MISSED TODAY											
		hr = sheet.createRow(rowIdx++);
		//Col 0-1
		hc = hr.createCell(0);
		hc.setCellStyle(getStyle(STYLE_BOLD));
		hc.setCellValue("COMPLAINT DETAIL:");
		sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1 , 0, 1));
		//ROW 2-3
		//Col 2-5
		hc = hr.createCell(2);
		hc.setCellStyle(getStyle(STYLE_COMPLAINT));
		hc.setCellValue(getComplaintDetail(serviceCase, allAnswers));
		sheet.createRow(rowIdx++);
		sheet.createRow(rowIdx++);
		sheet.addMergedRegion(new CellRangeAddress(rowIdx - 3, rowIdx - 1, 2, 5));
		//Add two empty rows
	}
	
	protected void addErrorRow(Json resultRow, String message)
	{
		HSSFCell hc = sheet.createRow(rowIdx++).createCell(0);
		hc.setCellStyle(getStyle(STYLE_ZONE_NR));
		sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx -1, 0, NR_OF_COLUMNS - 1));
		hc.setCellValue("Error: " + message + " while processing " + resultRow.toString());
	}
	
	protected String getComplaintDetail(Json serviceCase, List<Json> allAnswers)
	{
		String qaDetail = getServiceAnswer("SWMRECIS_RCIQ8", allAnswers);
		String description = serviceCase.at("properties", Json.object()).at("hasDetails", "").asString();
		return description + " / " + qaDetail;  
	}
	
	protected String getServiceAnswer(String serviceFieldFragment, List<Json> allAnswers)
	{
		String fullField = "http://www.miamidade.gov/cirm/legacy#" + serviceFieldFragment;
		for (Json tempObj : allAnswers)
		{
			String candidate = tempObj.at("hasServiceField", Json.object()).at("iri", "").asString();
			if (fullField.equals(candidate)) {
				String hasAnswerValueOrObjectLabel = tempObj.at("hasAnswerObject", Json.object()).at("label", "").asString();
				if (hasAnswerValueOrObjectLabel.isEmpty())
					hasAnswerValueOrObjectLabel = tempObj.at("hasAnswerValue", Json.object()).at("literal", "").asString();
				return hasAnswerValueOrObjectLabel;
			}
		} 
		return "";
	}    
    
    public static void main(String[] a) {
    	System.out.println(new ReportRecyclingComplaintsXls().create());
    }
}

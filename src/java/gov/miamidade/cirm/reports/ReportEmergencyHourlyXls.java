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
import java.util.Set;

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
import org.apache.poi.hssf.usermodel.HSSFPalette;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.util.CellRangeAddress;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.OWL;
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
 * Report: EMERGENY - MD
 * 
 * No Header, no Footer.
 * 
 * FileNamePattern: 311Calls_8_27_2012_1400.xlsx -> 311Calls_M_D_YYYY_HHHH (24h) 
 * 
 * EID	SR_NUMBER	CREATED_DATE	GROUP_CODE	TYPE_CODE	TYPE_DESC	SR_ADDRESS	ZIP_CODE	DISTRICT	X_COORDINATE	Y_COORDINATE
 * 1246785810	12-00250962	 08/26/2012	MDGIC02	311UNDEB	311 ACTIVATION   FLOODING COMPLAINTS	 3640  YACHT CLUB DR	33180	4	942669.440000000	596930.94000000
 * 
 * Usage:
 * cirm.top.get('/reportEmergency/createAndSend/')
 * 
 * The time machine should call createAndSend after every hour during Hurricanes.
 * e.g. 11:02, to make sure the 1100 report get's generated, which includes all relevant SRs from 10:00:00.000-10:59:59:999
 * 
 * "/reportEmergency/create"...creates but does not send the reports.
 * "/reportEmergency/createAndSend"...creates and sends the reports.
 * 
 * 
 * For down situations:
 * "/reportEmergency/create{from,to}"...creates but does not send the reports for a given timeframe.
 * 
 * To email to cirmtest@miamidade.gov, set MessageManager Test mode or set EMAIL_TESTMODE = true;
 * 
 * @author Thomas Hilpold
 */
@Path("reportEmergency") 
public class ReportEmergencyHourlyXls
{
	public static final boolean DBG_USE_24h_PERIOD = false;
	public static final boolean DBG = true;

	// Based on ITD  Andelo, Sandra email to hilpold received Tue 8/6/2013 8:40 AM
	// Subj: RE: Hurricane hourly OEM report request
	public static final Json REPORT_SR_TYPES_MD = Json.array(
			"legacy:311UNDEB", "legacy:311BELST", "legacy:311CMGPL","legacy:311FPLLN",
			"legacy:311MOBHM","legacy:311RFDMG","legacy:311STRUC",
			"legacy:311VOLDN","legacy:PW6065","legacy:PW441","legacy:PW399",
			"legacy:PW86","legacy:PW465","legacy:PW449","legacy:PW339","legacy:PW321",
			"legacy:PW437","legacy:PW433","legacy:PW341","legacy:PW424",
			"legacy:PW422","legacy:PW82","legacy:PW423","legacy:PW60",
			"legacy:RAAM4","legacy:RAAM3","legacy:RAAM5"
			); //27 Types 

	/**
	 * Report will contain all REPORT_SR_TYPES_MD with HAS_PRIORITY_OBJ priority
	 */
	public static final String HAS_PRIORITY_OBJ = "legacy:HURRACT";
	
	public static final boolean EMAIL_TEST_MODE = false; //true overrides a MessageManager.isTestMode()==false, false respects MM setting.
	
	public static final String EMAIL_TO = "ajabss@miamidade.gov";
	public static final String EMAIL_CC = "hilpold@miamidade.gov";
	public static final String EMAIL_BCC = "cirmtest@miamidade.gov;hilpold@miamidade.gov";
	public static final String EMAIL_FROM = "cirm@miamidade.gov";
	public static final String EMAIL_TEST_TO = "cirmtest@miamidade.gov;hilpold@miamidade.gov";
	public static final String EMAIL_TEST_FROM = "cirmtest@miamidade.gov";
	public static final String EMAIL_SUBJECT = "MDC CIAO 311HUB - Emergency Service Request Report";
	public static final String EMAIL_TEXT = "Dear Sir or Madam, \r\n\r\n\r\n"
			+ "Please find attached the Emergeny Report covering $$REPORT_DETAIL$$.\r\n" 
			+ "We hope it meets your expectations.\r\n\r\n"
			+ "This email and the report were automatically generated. \r\n\r\n"
			+ "\r\n"
			+ "Kind regards,\r\n\r\n\r\n"
			+ "Your Miami-Dade 311 Hub Team\r\n\r\n\r\n";
			//+ "In case of error please respond to ...@miamidade.gov (... Unit (....))";

	public static final String EMAIL_VAR_REPORT_DETAIL = "$$REPORT_DETAIL$$";
		
	public static final String REPORT_FILENAME_PREFIX = "311Calls_"; // date to follow
	public static final String REPORT_FILENAME_SUFFIX = ".xls";
	//"EID", "SR_NUMBER",	"CREATED_DATE",  "GROUP_CODE", "TYPE_CODE", "TYPE_DESC", "SR_ADDRESS", "ZIP_CODE", "DISTRICT", "X_COORDINATE", "Y_COORDINATE"
	public static final String[] HEADER_COLS = new String[] { "EID", "SR_NUMBER", "CREATED_DATE", "GROUP_CODE", "TYPE_CODE", "TYPE_DESC", "SR_ADDRESS", "ZIP_CODE", "DISTRICT", "X_COORDINATE", "Y_COORDINATE"};
	public static final int[] COL_WIDTHS = new int[] 	     { 80,    120,	 	   150,            110,  	     100, 		  460,			200,  		 80, 		 80, 		 100, 			 100 };
	public static final float COL_WIDTH_FACTOR = 31f;
	public static final int NR_OF_COLUMNS = 11;

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
	private ListIterator<Json> curReportResultArrayIt = null; // [{ "RNUM": "34", "GIS_COMDIST": "11", "SR_REQUEST_ID": "3025860" }] GIS_COMDIST.. MD Commissioner district
	private String emailVarReportDetail = "";
	
	/**
	 * Creates and sends an emergency report for the last full hour before the call. E.g. call at 11:01 (server time) for a report from 10:00-10:59:59.999
	 *  
     * To Send test emails, have MessageManager in Test Mode or set DBG_TEST
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
	    	File report = createReport(reportDate);
	    	//So if sth retriable happens before this line, no emails will have been sent. If we get here, retriable exceptions are over.
	    	if (report == null)
	    		return GenUtils.ko("Report generation failed. Check logs! " + new Date());
	    	//send reports
	    	else
	    	{
		    	try {
		    		sendReport(report);
			    	return GenUtils.ok();
		    	} catch(Exception e) 
		    	{
		    		System.err.println(e);
		    		e.printStackTrace(System.err);
		    		return GenUtils.ko("Report generation ok, but sending report by email failed. Check logs! " + new Date());	
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
	    	Date reportDate = new Date(new Date().getTime());
	    	String tempDir = System.getProperty("java.io.tmpdir");
	    	setDirectoryPath(new File(tempDir));
	    	File report = createReport(reportDate);
	    	if (report != null)
	    		return GenUtils.ok();
	    	else 
	    		return GenUtils.ko("Report generation failed. Check logs! " + new Date());
		}});
		return result;
    }

    // Email
    protected synchronized void sendReport(File reportFile) throws MessagingException 
    {
    	ThreadLocalStopwatch.getWatch().time("START SENDING RECYCLING REPORT EMAIL.");
    	MessageManager mm = MessageManager.get(); 
    	boolean testMode = mm.isTestMode() || EMAIL_TEST_MODE;
    	//Email for Report 1
    	String emailText = EMAIL_TEXT.replace(EMAIL_VAR_REPORT_DETAIL, emailVarReportDetail); 
        Multipart multiPart1 = new MimeMultipart();  
 
        MimeBodyPart messageText1 = new MimeBodyPart();  
        messageText1.setContent(emailText, "text/plain");  
        multiPart1.addBodyPart(messageText1);  
        //Attach Report File 1
        MimeBodyPart rarAttachment1 = new MimeBodyPart();  
        FileDataSource rarFile1 = new FileDataSource(reportFile);  
        rarAttachment1.setDataHandler(new DataHandler(rarFile1));  
        rarAttachment1.setFileName(rarFile1.getName());  
        multiPart1.addBodyPart(rarAttachment1);
        
        int successSend = 0;
    	try {
    		if (testMode) 
    		{
    			mm.sendEmail(EMAIL_TEST_FROM, EMAIL_TEST_TO, null, null, EMAIL_SUBJECT, multiPart1);
    			successSend++;
    		} else
    		{
    			mm.sendEmail(EMAIL_FROM, EMAIL_TO, EMAIL_CC, EMAIL_BCC, EMAIL_SUBJECT, multiPart1);
    			successSend++;
    		}
    	}catch (Exception e) 
    	{	
    		ThreadLocalStopwatch.getWatch().time("FAILED SENDING EMERGENCY REPORT EMAIL failed with: " + e);
    		System.err.println("ReportEmergencyHourlyXls:sendReports mm.sendEmail " + successSend + " of 1 emails were sucessfully sent" );
    		System.err.println("ReportEmergencyHourlyXls:sendReports Subject: " + EMAIL_SUBJECT);
    		throw new RuntimeException(e);
    	}
    	ThreadLocalStopwatch.getWatch().time("END SENDING EMERGENCY REPORT EMAIL succeeded. (one email sent).");
    }
    
    //-------------------------------------- PUBLIC INTERFACE ---------------------------------------------
    
	/**
     * Requirements (Assia):
     * Hourly report. e.g. 12:01 -> 11:00.00.000 to 11:59.59.999    
     * 
     * @param date
     * @return
     */
    public Date[] getFromToDatesForReportOn(Date date) 
    {
    	Date from;
    	Date to;
    	Calendar cal = Calendar.getInstance(); //server time zone!
    	cal.setTime(date);
    	cal.set(Calendar.MINUTE, 0);
    	cal.set(Calendar.SECOND, 0);
    	cal.set(Calendar.MILLISECOND, 0);
		to = cal.getTime();
   		int reportHours = DBG_USE_24h_PERIOD? 24 : 1;
   		cal.add(Calendar.HOUR_OF_DAY, - reportHours);
   		from = cal.getTime();
    	//Set to to last Millisecond of prev hour
    	cal = Calendar.getInstance();
    	cal.setTime(to);
    	cal.add(Calendar.MILLISECOND, -1);
    	to = cal.getTime();
    	return new Date[] {from, to};
    }
    
    public Query createQuery(Date from, Date to,  RelationalStore store)
    {
    	if (from == null || to == null || to.before(from)) 
    		throw new IllegalArgumentException("from " + from + " before to ?" + to);
    	String fromStr = GenUtils.formatDate(from);
    	String toStr = GenUtils.formatDate(to);    	
    	Json pattern = Json.object();
    	pattern.set("caseSensitive", false);
    	pattern.set("currentPage", 1);
    	pattern.set("itemsPerPage", 1000);
    	pattern.set("sortBy","type");
    	pattern.set("sortDirection","asc");
    	pattern.set("type", REPORT_SR_TYPES_MD);
    	//"legacy:hasPriority":{"type":"legacy:Priority","iri":"http://www.miamidade.gov/cirm/legacy#HURRACT"}
    	Json priorityObj = Json.object();
    	priorityObj.set("type", "legacy:Priority");
    	priorityObj.set("iri", OWL.fullIri(HAS_PRIORITY_OBJ).toString());
    	pattern.set("legacy:hasPriority", priorityObj);
    	pattern.set("hasDateCreated", "between("+ fromStr + "," + toStr + ")");
    	Json geoSet = Json.object();
    	geoSet.set("type", "GeoPropertySet");
    	geoSet.set("GIS_COMDIST", "like (%)");
    	pattern.set("hasGeoPropertySet", geoSet);
    	Json atAddress = Json.object();
    	atAddress.set("type", "Street_Address");
    	atAddress.set("sortBy", "Zip_Code");
    	atAddress.set("sortDirection" , "asc");
    	pattern.set("atAddress", atAddress);
    	QueryTranslator qt = new QueryTranslator();
    	Query q = qt.translate(pattern, store);
    	q.getStatement().getSql().COLUMN("GIS_COMDIST"); //MD commissioner district
    	if (DBG) System.out.println("ReportQuery: " + q.getStatement().getSql().SQL());
    	return q;
    }

    public synchronized File createReport(Date reportDate)
    {
    	Date[] fromTo = getFromToDatesForReportOn(reportDate);
    	return createReport(reportDate, fromTo[0], fromTo[1]);
    }
	       
    /**
     * To be called within a serializable transaction!
     * 
     * @return report file
     */
    public synchronized File createReport(Date reportDate, Date from, Date to) 
    {
    	if (!to.after(from) || from.equals(to)) throw new IllegalArgumentException("to" + to.toString() + " must be after or equal from " + from.toString());
		File result = null;
    	ThreadLocalStopwatch.getWatch().time("START Create Emergency Report");
		RelationalStore store = getPersister().getStore();
		Json boidsOrdered;
		Query q;
		// Determine Dates
    	String fromStr = GenUtils.formatDate(from);
    	String toStr = GenUtils.formatDate(to);
    	String range = (fromStr.equals(toStr))? fromStr : fromStr + " to " + toStr; 
    	emailVarReportDetail += range;
    	
    	q = createQuery(from, to, store);
		try
		{
			boidsOrdered = store.customSearch(q);
			ThreadLocalStopwatch.getWatch().time("Create Emergency Report query finished found : " 
					+ boidsOrdered.asJsonList().size());
			//TODO include time in filename between pre anf suffix  
			result = createReport(boidsOrdered, reportDate, REPORT_FILENAME_PREFIX + formatDateForFileName(reportDate) + REPORT_FILENAME_SUFFIX);
			ThreadLocalStopwatch.getWatch().time("END Create Emergency Report ");
		} catch (SQLException e)
		{
			ThreadLocalStopwatch.getWatch().time("FAILED Create Emergency Report");
			e.printStackTrace();
		}
    	return result;
    }
    
    public String formatDateForFileName(Date d)
    {
    	SimpleDateFormat df = new SimpleDateFormat("M_d_yyyy_HH00");
    	return df.format(d);
    }
    
    /**
     * To be called within a serializable transaction!
     * @param boidsOrdered arr of { "RNUM": "32", "COM_DIST": "1", "SR_REQUEST_ID": "3024810" }
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
    		System.err.println("ReportEmergencyHourlyXls: createReport: " + e);
    	}
    	if (DBG) System.out.println("ReportEmergencyHourlyXls: created " + (rf == null? "NULL!!!": rf.getAbsolutePath()));
    	return rf;
    	//TODO delete reports older than some time. ask requirement.
    }

    //----------------------------- PROTECTED METHODS ------------------------------
	protected long getSRBoid(Json resultRow)
	{
		return resultRow.at("SR_REQUEST_ID", "-1").asLong();
	}

	protected int getGIS_COMDIST(Json resultRow)
	{
		return resultRow.at("GIS_COMDIST", "-1").asInteger();
	}

	protected String getCSR_GroupCode(Json sr)
	{
		Set<OWLNamedIndividual> orgs = OWL.queryIndividuals("owl:Thing and inverse providedBy value " + "legacy:" + sr.at("type", "N/A").asString());
		if (orgs.isEmpty()) return "N/A";
		OWLNamedIndividual provider = orgs.iterator().next();
		Set<OWLLiteral> groupCodes = provider.getDataPropertyValues(OWL.dataProperty("legacy:CSR_GroupCode"), OWL.ontology());
		if (groupCodes.isEmpty()) 
			return "N/A";
		else
			return "" + groupCodes.iterator().next().getLiteral();
		//legacy:providedBy
		//legacy:CSR_GroupCode
	}
	
	protected String getSRTypeLabel(Json sr) 
	{
		OWLNamedIndividual type = OWL.individual("legacy:" + sr.at("type", "").asString());
		return OWL.getEntityLabel(type);
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
			System.err.println("ReportEmergencyHourlyXls: search result was not a JSON array, was : " + resultArray);
		}
		curReportResultArrayIt = resultArray.asJsonList().listIterator();
		// Customize color
		HSSFPalette palette = workbook.getCustomPalette();
	    palette.setColorAtIndex(HSSFColor.LIGHT_BLUE.index,
	            (byte) 200,  //RGB red
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
		addServiceCaseRows();
		for (int i = 0; i < NR_OF_COLUMNS; i++)
		{
			sheet.setColumnWidth(i, getColWidth(i));
		}
	}

	/**
	 * 1 row
	 * @param reportDate
	 */
	protected void addHeaderRows(Date reportDate)
	{
		HSSFCell hc;
		int firstRowIdx = rowIdx;
		HSSFRow hr = sheet.createRow(rowIdx++);
		for (int i = 0; i < NR_OF_COLUMNS; i++)
		{
			hc = hr.createCell(i);
			hc.setCellStyle(getStyle(STYLE_COL_HEADER));
			hc.setCellValue(HEADER_COLS[i]);			
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

		}
		CellStyle result = stylesMap.get(STYLE);
		if (result == null) {
			System.err.println("ReportEmergencyHourlyXls: STYLE NOT FOUND, using default. Was: " + STYLE);
			return stylesMap.get(STYLE_DEFAULT);
		}
		return result;
	}
	
	public int getColWidth(int colIdx)
	{
		return (int)(COL_WIDTHS[colIdx] * COL_WIDTH_FACTOR);
	}


	protected void addServiceCaseRows()
	{
		while(hasNextRs())
		{
			Json resultRow = nextRs();
			Json serviceCase = readReport(getSRBoid(resultRow));
			addServiceCaseRow(resultRow, serviceCase);
		}
	}
	
	protected void addServiceCaseRow(Json resultRow, Json serviceCase)
	{
		if (serviceCase.isNull())
		{
			addErrorRow(resultRow, "Could not read ServiceCase from DB");
			return;
		}
		//List<Json> allAnswers = ServiceRequestReportUtil.getAllServiceAnswers(serviceCase);
		HSSFCell hc;
		//Row n sr.EID, sr.created_date, sr.group_code, st.code_code, st.description, FULLADDRESS, sr.city,sr.zip_code, x_coordinate, y_coordinate, cd
		HSSFRow hr = sheet.createRow(rowIdx++);
		List<String> colsValues = new ArrayList<String>();
		//0
		colsValues.add(serviceCase.at("boid", "N/A").asString());
		colsValues.add(serviceCase.at("properties", Json.object()).at("hasCaseNumber", "N/A").asString());
		String dateCreatedStr = serviceCase.at("properties", Json.object()).at("hasDateCreated", "").asString();
		//if (DBG) System.out.println(dateCreatedStr);
		if (dateCreatedStr.contains("T")) 
			dateCreatedStr = ServiceRequestReportUtil.formatRecyclingDate(dateCreatedStr);
		colsValues.add(dateCreatedStr);
		colsValues.add(getCSR_GroupCode(serviceCase)); //Type GROUP CODE; provided by?
		colsValues.add(serviceCase.at("type", "N/A").asString());
		//5
		colsValues.add(getSRTypeLabel(serviceCase)); //TYPE label!
		colsValues.add(serviceCase.at("properties", Json.object()).at("atAddress", Json.object()).at("fullAddress", "N/A").asString());
		colsValues.add(serviceCase.at("properties", Json.object()).at("atAddress", Json.object()).at("Zip_Code", "N/A").asString());
		colsValues.add("" + getGIS_COMDIST(resultRow));
		colsValues.add(serviceCase.at("properties", Json.object()).at("hasXCoordinate", "N/A").asString());
		//10
		colsValues.add(serviceCase.at("properties", Json.object()).at("hasYCoordinate", "N/A").asString());
		for (int i = 0; i < colsValues.size(); i++)
		{
			hc = hr.createCell(i);
			hc.setCellStyle(getStyle(STYLE_DEFAULT));
			hc.setCellValue(colsValues.get(i));
		}
	}
	
	protected void addErrorRow(Json resultRow, String message)
	{
		HSSFCell hc = sheet.createRow(rowIdx++).createCell(0);
		hc.setCellStyle(getStyle(STYLE_ZONE_NR));
		sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx -1, 0, NR_OF_COLUMNS - 1));
		hc.setCellValue("Error: " + message + " while processing " + resultRow.toString());
	}
	
    public static void main(String[] a) {
    	System.out.println(new ReportEmergencyHourlyXls().create());
    }
}

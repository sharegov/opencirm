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

import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
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
 * Directly returns csv file with the following content: 
 * (see http://www.rfc-editor.org/rfc/rfc4180.txt)
 * 
 * EID	SR_NUMBER	CREATED_DATE	GROUP_CODE	TYPE_CODE	TYPE_DESC	SR_ADDRESS	ZIP_CODE	DISTRICT	X_COORDINATE	Y_COORDINATE
 * 1246785810	12-00250962	 08/26/2012	MDGIC02	311UNDEB	311 ACTIVATION   FLOODING COMPLAINTS	 3640  YACHT CLUB DR	33180	4	942669.440000000	596930.94000000
 * 
 * Usage:
 * cirm.top.get('/reportEmergency/create/')
 * 
 * The time machine should call createAndSend after every hour during Hurricanes.
 * e.g. 11:02, to make sure the 1100 report get's generated, which includes all relevant SRs from 10:00:00.000-10:59:59:999
 * 
 * "/reportEmergency/create"...creates but does not send the reports.
 * 
 * For down situations:
 * "/reportEmergency/create{from,to}"...creates but does not send the reports for a given timeframe.
 * 
 * To email to cirmtest@miamidade.gov, set MessageManager Test mode or set EMAIL_TESTMODE = true;
 * 
 * @author Thomas Hilpold
 */
@Path("reportEmergencyCSV") 
public class ReportEmergencyHourlyCSV
{
	public static final boolean DBG = true;

	public static final char CSV_SEPARATOR = ',';

	public static final String PARAM_DATE_FORMAT = "yyMMdd";
	
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

	//"EID", "SR_NUMBER",	"CREATED_DATE",  "GROUP_CODE", "TYPE_CODE", "TYPE_DESC", "SR_ADDRESS", "ZIP_CODE", "DISTRICT", "X_COORDINATE", "Y_COORDINATE"
	public static final String[] HEADER_COLS = new String[] { "EID", "SR_NUMBER", "CREATED_DATE", "GROUP_CODE", "TYPE_CODE", "TYPE_DESC", "SR_ADDRESS", "ZIP_CODE", "DISTRICT", "X_COORDINATE", "Y_COORDINATE"};
	public static final int NR_OF_COLUMNS = 11;

	//private volatile List<String> csv = null; //The list of Strings to be returned as text/csv

	private LegacyEmulator legacyEmulator;
	private ListIterator<Json> curReportResultArrayIt = null; // [{ "RNUM": "34", "GIS_COMDIST": "11", "SR_REQUEST_ID": "3025860" }] GIS_COMDIST.. MD Commissioner district
	

    @GET
    @Path("create")
    @Produces("text/csv")
    public synchronized String create()
    {
    	return create(null);
    }

    @GET
    @Path("create")
    @Produces("text/csv")
    public synchronized String create(@QueryParam("from") String fromDateYYMMDD)
    {
    	return create(fromDateYYMMDD, null);
    }

    /**
 	 * Creates an emergency report in csv format
 	 *   
	 * @param fromDateYYMMDD if null, the beginning of the day is assumed. 
	 * @param toDateYYMMMDD if null, now will be used.
	 * @return
	 */
    @GET
    @Path("create")
    @Produces("text/csv")
    public synchronized String create(@QueryParam("from") String fromDateYYMMDD, @QueryParam("to") String toDateYYMMDD)
    {
    	//Repeated calls with equal params should cache for 30 mins. 
    	SimpleDateFormat paramDf = new SimpleDateFormat(PARAM_DATE_FORMAT);
    	Date from, to;
    	if (fromDateYYMMDD == null) {
    		from = new Date();
    	} else {
	    	try {
	    		from = paramDf.parse(fromDateYYMMDD);
	    	} catch (ParseException e)
	    	{
	    		return "Error parsing from date parameter: Format YYMMDD " + fromDateYYMMDD;
	    	}
    	}
    	if (toDateYYMMDD == null) {
    		to = new Date();
    	} else {
	    	try {
	    		to = paramDf.parse(toDateYYMMDD);
	    	} catch (ParseException e)
	    	{
	    		return "Error parsing to date parameter: Format YYMMDD " + fromDateYYMMDD;
	    	}
    	}
    	final Date fFrom = getStartOfDayTimeStamp(from);
    	final Date fTo = getEndOfDayTimeStamp(to);
		List<String> result =  Refs.defaultRelationalStore.resolve().txn(new CirmTransaction<List<String>>() {
			public List<String> call()
			{			
				List<String> report = createReport(fFrom, fTo);
				return report;
			}});
		//Convert List of Strings to String
		StringBuffer b = new StringBuffer((1+result.size()) * 120);
		for (String line : result) 
		{
			b.append(line);
			b.append("\r\n");
		}
		return b.toString();
    }

    //-------------------------------------- PUBLIC INTERFACE ---------------------------------------------
    
    /**
     * Get start of day in Server time zone for a timestamp.
     * End of day is defined here as the start of the first millisecond of the day.
     *  
     * @param date a date of which only the year, month and day are used.
     * @return
     */
    public Date getStartOfDayTimeStamp(Date date) 
    {
    	Calendar cal = Calendar.getInstance(); //server time zone!
    	cal.setTime(date);
    	cal.set(Calendar.HOUR_OF_DAY, 0);
    	cal.set(Calendar.MINUTE, 0);
    	cal.set(Calendar.SECOND, 0);
    	cal.set(Calendar.MILLISECOND, 0);
		return cal.getTime();
    }

    /**
     * Get end of day in Server time zone for a timestamp.
     * End of day is defined here as the last millisecond of the day.
     *  
     * @param date a date of which only the year, month and day are used.
     * @return
     */
    public Date getEndOfDayTimeStamp(Date date) 
    {
    	Calendar cal = Calendar.getInstance(); //server time zone!
    	cal.setTime(date);
    	cal.set(Calendar.HOUR_OF_DAY, 23);
    	cal.set(Calendar.MINUTE, 59);
    	cal.set(Calendar.SECOND, 59);
    	cal.set(Calendar.MILLISECOND, 999);
		return cal.getTime();
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

    /**
     * To be called within a serializable transaction!
     * 
     * @return report file or null, on error
     */
    public synchronized List<String> createReport(Date from, Date to) 
    {
    	List<String> report = null;
    	if (!to.after(from) || from.equals(to)) throw new IllegalArgumentException("to" + to.toString() + " must be after or equal from " + from.toString());
    	ThreadLocalStopwatch.getWatch().time("START Create Emergency Report csv");
		RelationalStore store = getPersister().getStore();
		Json boidsOrdered;
		Query q;
		// Determine Dates
//    	String fromStr = GenUtils.formatDate(from);
//    	String toStr = GenUtils.formatDate(to);
//    	String range = (fromStr.equals(toStr))? fromStr : fromStr + " to " + toStr; 
    	q = createQuery(from, to, store);
		try
		{
			boidsOrdered = store.customSearch(q);
			ThreadLocalStopwatch.getWatch().time("Create Emergency Report query finished found : " 
					+ boidsOrdered.asJsonList().size());
			report = createReport(boidsOrdered);
			ThreadLocalStopwatch.getWatch().time("END Create Emergency Report csv");
		} catch (SQLException e)
		{
			ThreadLocalStopwatch.getWatch().time("FAILED Create Emergency Report csv");
			e.printStackTrace();
		}
		return report;
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
    public synchronized List<String> createReport(Json boidsOrdered) 
    {
    	List<String> report = new ArrayList<String>(300);
    	addReportRows(boidsOrdered, report);    
    	//TODO delete reports older than some time. ask requirement.
    	return report;
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

	
	protected void initReport(Json resultArray) 
	{
		legacyEmulator = new LegacyEmulator();
		if (resultArray == null || !resultArray.isArray()) 
		{
			resultArray = Json.array();
			System.err.println("ReportEmergencyHourlyXls: search result was not a JSON array, was : " + resultArray);
		}
		curReportResultArrayIt = resultArray.asJsonList().listIterator();
	}
	
	/**
	 * Create Workbook
	 */
	protected void addReportRows(Json boidsOrdered, List<String> csv)
	{
		initReport(boidsOrdered);
		//Header
		csv.add(getHeaderRow());
		//Content 
		while(hasNextRs())
		{
			Json resultRow = nextRs();
			Json serviceCase = readReport(getSRBoid(resultRow));
			csv.add(getServiceCaseRow(resultRow, serviceCase));
		}
	}

	/**
	 * 1 row
	 */
	protected String getHeaderRow()
	{
		String header = "";
		for (int i = 0; i < NR_OF_COLUMNS; i++)
		{
			header += HEADER_COLS[i] + CSV_SEPARATOR;			
		}
		return header;
	}

	protected String getServiceCaseRow(Json resultRow, Json serviceCase)
	{
		String result = "";
		if (serviceCase.isNull())
		{
			return getErrorRow(resultRow, "Could not read ServiceCase from DB");
		}
		//Row n sr.EID, sr.created_date, sr.group_code, st.code_code, st.description, FULLADDRESS, sr.city,sr.zip_code, x_coordinate, y_coordinate, cd
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
			result+= colsValues.get(i) + CSV_SEPARATOR;
		}
		return result;
	}
	
	protected String getErrorRow(Json resultRow, String message)
	{
		String error = message + CSV_SEPARATOR;
		error = resultRow.toString() + CSV_SEPARATOR;
		for (int i = 2; i < NR_OF_COLUMNS; i++) {
			error += CSV_SEPARATOR; 
		}
		return error;
	}
	
    public static void main(String[] a) {
    	System.out.println(new ReportEmergencyHourlyCSV().create());
    }
}

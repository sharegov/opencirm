package gov.miamidade.cirm.maintenance.http;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import org.sharegov.cirm.OWL;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.StartupUtils;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import gov.miamidade.cirm.other.ServiceCaseJsonHelper;
import mjson.Json;

/**
 * BulkyCaseCloseHttpClient console application closes bulky trash request SRs after a csv file was provided by WCS team.<br>
 * It sets the SR closed date to the file provided pickup date and uses http to find, load, and (historically)update each case.<br>
 * <br>
 * Warning: Using this program without proper review and thorough testing can destroy production service requests.
 * Also: This does not yet consider all special cases, such as Status changes after provided closed date; so it should be improved 
 * before the next run.

 * <br>
 * How to use: <br>
 * 1. Create a csv in similar format as the test file provided with test data (only WO# and Pickup Date will be used by program)<br>
 * 2. Start local Cirm in Test mode.<br>
 * 3. Modify MODIFIED_BY to reflect your e/c key.<br>
 * 4. Start BulkyCaseCloseHttpClient as java application.<br>
 * 5. Monitor output and validate test SRs thoroughly.<br>
 * 6. Modify csv file and local Cirm config for production data, run, and validate cases manually.<br>
 * <br>
 * The program fails/stops with exception if any expectation does not hold to avoid risk.<br>
 * (e.g a bad date in the file, row column issues, et.c.)<br.
 * <br>
 * Running this program again with the same csv rows is safe, because once a case has been closed it will not be modified again.<br>
 * <br>
 * In the future http methods should be extracted for reuse in a more generic class. <br>
 * <br>
 * @author Thomas Hilpold
 *
 */
public class BulkyCaseCloseHttpClient {

	public final static String FILENAME = "BulkyCaseClose_Test_Data.csv"; 
	public final static int COLUMN_COUNT = 7; 
	public final static boolean HAS_HEADER_LINE = true; 
	public final static String MODIFIED_BY = "e309888";
	public final static String CIRM_URL = "https://127.0.0.1:8183";
	
	public final static DateFormat DATE_FORMAT = new SimpleDateFormat("M/d/yyyy"); 

	public final static String SEARCH_URL = CIRM_URL + "/legacy/advSearch";
	public final static String LOADCASE_URL = CIRM_URL + "/legacy/caseNumberSearch";
	public final static String UPDATECASE_HISTORIC_URL = CIRM_URL + "/legacy/updateHistoric";
	public final static String UPDATECASE_HISTORIC_PATHPARAM = "updatedDate"; //Value: Iso date using Genutils
	public final static String CIRMADMIN_COOKIE_VALUE = "usergroups=http%3A%2F%2Fwww.miamidade.gov%2Fontology%23CirmAdmin";

	public final static Json SEARCH_JSON = Json.read("{\"type\":\"legacy:BULKYTRA\","
			+ "\"http://www.miamidade.gov/cirm/legacy#BULKYTRA_BULKYWOR\":{\"literal\":\"12345678\",\"datatype\":\"http://www.w3.org/2001/XMLSchema#string\"}," 
			+ "\"caseSensitive\":false,\"currentPage\":1,\"itemsPerPage\":1,\"sortBy\":\"legacy:hasCaseNumber\",\"sortDirection\":\"desc\"}");; 
									
	public final static Json LOADCASE_JSON = Json.read("{\"type\":\"legacy:ServiceCase\",\"legacy:hasCaseNumber\":\"16-10001234\"}");
	
	/**
	 * Main Method
	 * @param args are ignored
	 */
	public static void main(String[] args) {
		StartupUtils.disableCertificateValidation();
		StartUp.getConfig().set("metaDatabaseLocation", "c:/temp/testontodbclient");
		//Force init of ontologies early; this is slow; you may disable this for quick file parsing tests.
		OWL.reasoner();
		BulkyCaseCloseHttpClient c = new BulkyCaseCloseHttpClient();		
		c.processAll();
	}
	
	/**
	 * Processes all lines in the file (except header if given).
	 */
	void processAll() {
		try {
			List<String> lines = loadFile();
			int i = 0;
			for (String line : lines) {
				i++;
				ThreadLocalStopwatch.startTop("Processing line " + i + " / " + lines.size());
				processLine(line);
			}
		} catch(Exception e) {
			ThreadLocalStopwatch.error("" + e);
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Loads the file into memory.<br>
	 * Validates the column count, integer at col 1, and date at col 3 for each row.
	 * 
	 * @return a list of rows as Strings without header
	 * @throws URISyntaxException
	 * @throws IllegalStateException
	 * @throws IOException
	 */
	List<String> loadFile() throws URISyntaxException, IOException {
		ArrayList<String> result = new ArrayList<>();
		URL file = BulkyCaseCloseHttpClient.class.getResource(FILENAME);
		@SuppressWarnings("resource")
		BufferedReader reader = new BufferedReader(new FileReader(new File(file.toURI())));
		while (reader.ready()) {
			String line = reader.readLine();
			try {
				validateRow(line, HAS_HEADER_LINE && result.isEmpty());
			} catch (Exception e) {
				ThreadLocalStopwatch.error("Validation exception in row " + (result.size() + 1) + " File " + FILENAME);
				throw new RuntimeException("File Validation failed");
			}
			result.add(line);
		}
		reader.close();
		if (HAS_HEADER_LINE) result.remove(0);
		ThreadLocalStopwatch.now("Loaded lines: " + result.size());
		return result;
	};
	
	void validateRow(String row, boolean isHeaderLine) throws ParseException, NumberFormatException {
		StringTokenizer rowTok = new StringTokenizer(row, ",");
		if (rowTok.countTokens() != COLUMN_COUNT) throw new IllegalStateException("Token error in line " + row);
		if (!isHeaderLine) {
			String intStr = rowTok.nextToken();			
			try { 
				Integer.parseInt(intStr);
			} catch (NumberFormatException e) {
				ThreadLocalStopwatch.error("Column 1 (WO#) is not an integer, was " + intStr);
				throw e;
			}
			rowTok.nextToken();
			String dateStr = rowTok.nextToken();
			try {
				DATE_FORMAT.parse(dateStr);
			} catch (ParseException e) {
				ThreadLocalStopwatch.error("Column 3 (Pickup Date) is not a date, was " + dateStr);
				throw e;
			}
		}		
	}
	
	/**
	 * Process one line in the file.
	 * @param line
	 * @throws ParseException
	 */
	void processLine(String line) throws ParseException {
		StringTokenizer lineTok = new StringTokenizer(line, ",");
		int workOrderNumber = Integer.parseInt(lineTok.nextToken());
		lineTok.nextToken();
		Date closedDate = DATE_FORMAT.parse(lineTok.nextToken());
		ThreadLocalStopwatch.now("Closing case with WO: " + workOrderNumber + " at " + DATE_FORMAT.format(closedDate));
		String caseNumber = httpFindBulkytraCaseNumberByWorkorder(workOrderNumber);
		Json sr = httpLoadCase(caseNumber);
		//ThreadLocalStopwatch.now("Loaded: " + sr.toString());
		if (!isCaseClosed(sr)) {
			closeCase(sr, closedDate);
			ThreadLocalStopwatch.now("Saving: " + sr.toString());
			boolean updateOk = httpSaveUpdatedCase(sr, closedDate);
			if (!updateOk) {
				ThreadLocalStopwatch.error("Update Failed. Continuing.");
			}
		} else {
			ThreadLocalStopwatch.now("Case was closed " + caseNumber + " , not updating.");
		}
	}
	
	/**
	 * Find BULKYTRA SR by work order number.
	 * @param workOrderNumber 
	 * @return
	 */
	String httpFindBulkytraCaseNumberByWorkorder(int workOrderNumber) {
		String result = null;
		Json search = SEARCH_JSON.dup();
		search.at("http://www.miamidade.gov/cirm/legacy#BULKYTRA_BULKYWOR").set("literal", "" + workOrderNumber);
		ThreadLocalStopwatch.now("Http Find by Work order number " + workOrderNumber + "...");
		Json searchresult = Json.read(GenUtils.httpPost(SEARCH_URL, search.toString(), "Cookie", CIRMADMIN_COOKIE_VALUE));
		result = searchresult.at("resultsArray").asJsonList().get(0).at("hasCaseNumber").asString();
		if (searchresult.at("resultsArray").asJsonList().size() > 1) ThreadLocalStopwatch.error("Found more than one SR for WO. Ignored.");
		ThreadLocalStopwatch.now("Done. Found " + result);
		return result;
	}

	/**
	 * Loads a case by case number with IRIs assigned and prefixed.
	 * Empty arrays for actorEmails and hasRemovedAttachment are added as well, similar to the browser clients.
	 * <br>
	 * @param caseNumber eg. 16-10028942
	 * @return an SR json similar to what a browser client sends to an update endpoint.
	 */
	Json httpLoadCase(String caseNumber) {
		Json result = null;
		Json sr = null;
		Json load = LOADCASE_JSON.dup();
		load.set("legacy:hasCaseNumber", caseNumber);
		ThreadLocalStopwatch.now("Http Load case...");
		result = Json.read(GenUtils.httpPost(LOADCASE_URL, load.toString(), "Cookie", CIRMADMIN_COOKIE_VALUE));
		sr= result.at("bo");
		int size = sr.toString().length();
		ThreadLocalStopwatch.now("Done. Server: " + result.at("server") + " Size(bo) " + size + " bytes");
		ThreadLocalStopwatch.now("Assigning Iris...");
		ServiceCaseJsonHelper.cleanUpProperties(sr);
		ServiceCaseJsonHelper.assignIris(sr);
		sr.at("properties").delAt("transient$protected");
		sr.at("properties").set("actorEmails", Json.array());
		sr.at("properties").set("hasRemovedAttachment", Json.array());
		//maybe: if !has then sr.at("properties").set("hasAttachment", Json.array());		
		ThreadLocalStopwatch.now("Done.");
		return sr;
	}

	/**
	 * Calls the server to historically update the case at the updateDate.
	 * 
	 * @param sr a prefixed IRI SR json
	 * @param updatedDate the date for the status change activity
	 * @return true if saved successfully
	 * @throws UnsupportedEncodingException 
	 */
	boolean httpSaveUpdatedCase(Json sr, Date updatedDate) {
		ThreadLocalStopwatch.now("Http Save Update Historic...");
		Json result;
		String updatedDateStr = GenUtils.formatDate(updatedDate);
		String updateUrlWithDate;
		try {
			updateUrlWithDate = UPDATECASE_HISTORIC_URL + "?" + UPDATECASE_HISTORIC_PATHPARAM + "=" + URLEncoder.encode(updatedDateStr, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		result = Json.read(GenUtils.httpPost(updateUrlWithDate, sr.toString(), "Cookie", CIRMADMIN_COOKIE_VALUE));
		boolean ok = result.at("ok").asBoolean();
		if(!ok) ThreadLocalStopwatch.error("Http Save Update Historic failed with: " + result.toString());
		ThreadLocalStopwatch.now("Done.");
		return ok;
	}
	
	/**
	 * Determines if case is already closed.
	 * 
	 * @param sr
	 * @return true if closed.
	 */
	boolean isCaseClosed(Json sr) {
		if (!sr.at("type").asString().equals("BULKYTRA")) throw new IllegalStateException("Case not BULKYTRA, was " + sr.at("type").asString());
		String status = sr.at("properties").at("legacy:hasStatus").at("iri").asString();
		return (status.contains("C-"));
	}
	
	/**
	 * Modifies the service request to update status to C-CLOSED.
	 * If there are no updates already after the closedDate or no updates at all, the status change user, SR isModifiedBy, 
	 * and hadDateLastModified will be set to the closedDate.
	 * 
	 * @param sr a prefixed iri SR json.
	 */
	void closeCase(Json sr, Date closedDate) {
		boolean updateModified = false;
		//update
		sr.at("properties").at("legacy:hasStatus").set("iri", "http://www.miamidade.gov/cirm/legacy#C-CLOSED");
		if (sr.at("properties").has("hasDateLastModified")) {
			Date oldMod = GenUtils.parseDate(sr.at("properties").at("hasDateLastModified").asString());
			updateModified = oldMod.before(closedDate);
		}
		if (updateModified || !sr.at("properties").has("hasDateLastModified")) {
			sr.at("properties").set("hasDateLastModified", GenUtils.formatDate(closedDate));
			sr.at("properties").set("isModifiedBy", MODIFIED_BY);
		}
	}
}

package gov.miamidade.cirm.maintenance.http;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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
 * Abstract http client that simplifies implementation of batch case update tools against the mdCirm http API.<br>
 * <br>
 * Warning: Using this program without proper review and thorough testing can destroy production service requests.
 * <br>
 * How to use: <br>
 * Create a subclassof this class implementing only the abstract methods.<br>
 * Call processAll() to execute. <br>
 * <br>
 * The program fails/stops with exception if any expectation does not hold to avoid risk.<br>
 * (e.g a bad date in the file, row column issues, et.c.)<br>
 * A completion estimate is printed after the first line and every 100 lines (COMPLETION_ESTIMATE_EVERY).<br>
 * <br>
 * Running this program again with the same csv lines should be safe, if the subclass implements caseNeedsupdate correctly.<br>
 * <br>
 * @author Thomas Hilpold
 *
 */
public abstract class AbstractCaseUpdateHttpClient {

	public static boolean DBG = true;
	
	public final static String CIRM_URL = "https://127.0.0.1:8183";
	public final static int COMPLETION_ESTIMATE_EVERY = 100;
			
	public final static int MIN_COLUMN_COUNT = 3; //
	public final static boolean HAS_HEADER_LINE = true; 
	public final static String COLUMN_SEPARATOR = ","; 
	public final static DateFormat COLUMN_2_DATE_FORMAT = new SimpleDateFormat("M/d/yyyy"); 

	public final static String SEARCH_URL = CIRM_URL + "/legacy/advSearch";
	public final static String LOADCASE_URL = CIRM_URL + "/legacy/caseNumberSearch";
	public final static Json LOADCASE_JSON = Json.read("{\"type\":\"legacy:ServiceCase\",\"legacy:hasCaseNumber\":\"16-10001234\"}");
	public final static String UPDATECASE_HISTORIC_URL = CIRM_URL + "/legacy/updateHistoric";
	public final static String UPDATECASE_HISTORIC_PATHPARAM = "updatedDate"; //Value: Iso date using Genutils
	public final static String CIRMADMIN_COOKIE_VALUE = "usergroups=http%3A%2F%2Fwww.miamidade.gov%2Fontology%23CirmAdmin";
									
	private final static NumberFormat DEC_FORMAT = new DecimalFormat("00.00");
	private final static DateFormat COMPLETION_FORMAT = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);

	/**
	 * The meta database location used for the Http client. 
	 * Delete this directory if fresh ontologies are needed.
	 */
	private final static String META_DATABASE_LOCATION = "c:/temp/prodontodbclient";
	
	/**
	 * Constructor which should always be called as it initializes this mdcirm instance for processing.
	 * @param args are ignored
	 */
	public AbstractCaseUpdateHttpClient() {
		StartupUtils.disableCertificateValidation();
		StartUp.config.set("metaDatabaseLocation", META_DATABASE_LOCATION);
		//Force init of ontologies early; this is slow; you may disable this for quick file parsing tests.
		OWL.reasoner();
	}
	
	/**
	 * Processes all lines in the file (except header if given).
	 */
	protected void processAll() {
		long targetProcesssingTime = getTargetProcessingTimePerCaseMs();
		double avgDuration10PercentMs = 1000;
		try {
			List<String> lines = loadFile(getFileUri());
			int i = 0;
			for (String line : lines) {
				long startTime = System.currentTimeMillis();
				i++;
				ThreadLocalStopwatch.startTop("Processing line " + i + " / " + lines.size() + "\t" + line);
				processLine(line);
				long duration = System.currentTimeMillis() - startTime;
				if (duration < targetProcesssingTime) {
					try {
						Thread.sleep(targetProcesssingTime - duration);
					} catch(InterruptedException e) { 
						System.err.println("Interrupted " + e);
					};
				}
				long actualDuration = System.currentTimeMillis() - startTime;
				if (i == 1) {
					avgDuration10PercentMs = actualDuration;
				} else {
					avgDuration10PercentMs = avgDuration10PercentMs + 0.1 * (actualDuration - avgDuration10PercentMs);
				}
				if ((i - 1) % COMPLETION_ESTIMATE_EVERY == 0){
					printCompletionEstimate(avgDuration10PercentMs, lines.size() - i);
				}
			}
		} catch(Exception e) {
			ThreadLocalStopwatch.error("" + e);
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Prints a completion estimate time based on known average processing time.
	 * @param averageDuration
	 * @param nrOfRemaining
	 */
	void printCompletionEstimate(double averageDuration, int nrOfRemaining) {
		Calendar c = Calendar.getInstance();
		int minutesToCompletion = (int)(averageDuration * ((nrOfRemaining) / 1000.0 / 60.0));
		c.add(Calendar.MINUTE,  minutesToCompletion);
		ThreadLocalStopwatch.now("Estiated completion at " + COMPLETION_FORMAT.format(c.getTime()) 
				+ " in " + DEC_FORMAT.format(minutesToCompletion / 60.0) + " hours" );
	}
	
	/**
	 * Loads the file into memory.<br>
	 * Validates (basic) the column count and columns 1-3 for each row.
	 * 
	 * @return a list of rows as Strings without header
	 * @throws IllegalStateException
	 * @throws IOException
	 */
	List<String> loadFile(URI fileUri) throws URISyntaxException, IOException {
		ArrayList<String> result = new ArrayList<>();
		@SuppressWarnings("resource")
		BufferedReader reader = new BufferedReader(new FileReader(new File(fileUri)));
		while (reader.ready()) {
			String line = reader.readLine();
			try {
				validateLine(line, HAS_HEADER_LINE && result.isEmpty());
			} catch (Exception e) {
				ThreadLocalStopwatch.error("Validation exception in row " + (result.size() + 1) + " File " + getFileUri());
				throw new RuntimeException("File Validation failed");
			}
			result.add(line);
		}
		reader.close();
		if (HAS_HEADER_LINE) result.remove(0);
		ThreadLocalStopwatch.now("Loaded lines: " + result.size());
		return result;
	};
	
	/**
	 * Basic vadildation of a line/row.
	 * @param line
	 * @param isHeaderLine
	 * @throws ParseException
	 * @throws NumberFormatException
	 */
	protected void validateLine(String line, boolean isHeaderLine) throws ParseException, NumberFormatException {
		StringTokenizer lineTok = new StringTokenizer(line, COLUMN_SEPARATOR);
		if (lineTok.countTokens() < MIN_COLUMN_COUNT) throw new IllegalStateException("Less columns than required: " + line);
		if (!isHeaderLine) {
			String col1Str = lineTok.nextToken();
			if (col1Str.length() < 10) {
				throw new IllegalStateException("Column 1 (Case identifier) less than 10 characters: " + col1Str);
			}
			for (char c : col1Str.toCharArray()) {
				if (Character.isWhitespace(c)) {
					throw new IllegalStateException("Column 1 (Case identifier) contains whitespace: " + col1Str);
				}
			}
			String col2Str = lineTok.nextToken();
			try {
				COLUMN_2_DATE_FORMAT.parse(col2Str);
			} catch (ParseException e) {
				ThreadLocalStopwatch.error("Column 2 (Update Date) is not a date, was " + col2Str);
				throw e;
			}
			String col3Str = lineTok.nextToken();
			if (col3Str.length() < 7) {
				throw new IllegalStateException("Column 3 (Case type) less than 7 characters: " + col3Str);
			}
			for (char c : col3Str.toCharArray()) {
				if (Character.isWhitespace(c) || Character.isLowerCase(c)) {
					throw new IllegalStateException("Column 3 (Case type) contains whitespace or lowercase: " + col3Str);
				}
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
		String column1CaseIdentifier = lineTok.nextToken();
		Date column2UpdateDate = COLUMN_2_DATE_FORMAT.parse(lineTok.nextToken());
		String column3CaseType = lineTok.nextToken();
		if (DBG) ThreadLocalStopwatch.now("Updating case with Identifier: " + column1CaseIdentifier + " at " + COLUMN_2_DATE_FORMAT.format(column2UpdateDate));
		String caseNumber = findCaseNumber(column1CaseIdentifier);
		Json sr = httpLoadCaseForUpdate(caseNumber);
		validateType(sr, column3CaseType);
		if (caseNeedsUpdate(sr)) {
			if (updateCase(sr, column2UpdateDate)) {
				//ThreadLocalStopwatch.now("Saving: " + sr.toString());
				boolean updateOk = httpSaveUpdatedCase(sr, column2UpdateDate);
				if (!updateOk) {
					ThreadLocalStopwatch.error("Saving the updated case failed: \r\n " + sr.toString());
					throw new IllegalStateException("Saving the update failed. Check server logs.");
				}
			} else {
				ThreadLocalStopwatch.error("Modifying the json failed in updateCase Method. Json after method call: \r\n" + sr.toString());				
			}
		} else {
			ThreadLocalStopwatch.now("Case did not need updating: " + caseNumber + ", column1CaseIdentifier: " + column1CaseIdentifier);
		}
	}
	
	/**
	 * Gets a file uri pointing to a file with at least 2 columns (col1: caseIdentifier, col2:UpdateDate (M\d\yyyy), col3: caseType).
	 * caseIdentifier typically is caseNumber or biod, however, you can implement your own case search.
	 * casetype should be given in fragment only format. (e.g. BULKYTRA)
	 * 
	 * @return
	 * @throws URISyntaxException 
	 */
	protected abstract URI getFileUri() throws URISyntaxException;

	/**
	 * To avoid undue load on the cirm database, specify a target processing time in milliseconds.
	 * e.g. If processing one line only takes 3 seconds, but you determine 10 seconds as target,
	 * the processing thread will sleep for 7 seconds after processing to reduce system load.
	 * Return zero for fast as possible processing. 
	 * 
	 * @return
	 */
	protected abstract long getTargetProcessingTimePerCaseMs();

	/**
	 * Finds a case number (16-12345678) for the identifier given in column1.
	 * (which may already be the case number)
	 * 
	 * @param column1CaseIdentifierCurRow
	 * @return
	 */
	protected abstract String findCaseNumber(String column1CaseIdentifierCurRow);
	
	/**
	 * Determines if this case should be updated.
	 * 
	 * @param serviceRequest an sr in save format.
	 * @return false if was already processed or does not need update.
	 */
	protected abstract boolean caseNeedsUpdate(Json serviceRequest);

	/**
	 * Manipulate the serviceRequest json in this method for the update date given in column2.
	 * 
	 * @param serviceRequest an sr in save format.
	 * @param column2UpdateDate date of update as provided in column2
	 * @return false if the modification failed.
	 */
	protected abstract boolean updateCase(Json serviceRequest, Date column2UpdateDate);
	
	
	/**
	 * Determines if case is already closed.
	 * 
	 * @param sr
	 * @return true if closed.
	 */
	void validateType(Json sr, String column3SrType) {
		if (!sr.at("type").asString().equals("legacy:" + column3SrType)) {
			throw new IllegalStateException("CaseType not " + column3SrType + ", was " + sr.at("type").asString());
		}
		//String status = sr.at("properties").at("legacy:hasStatus").at("iri").asString();
		//return (status.contains("C-"));
	}

	/**
	 * Loads a case by case number with IRIs assigned and prefixed.
	 * Empty arrays for actorEmails and hasRemovedAttachment are added as well, similar to the browser clients.
	 * <br>
	 * @param caseNumber eg. 16-10028942
	 * @return an SR json similar to what a browser client sends to an update endpoint.
	 */
	Json httpLoadCaseForUpdate(String caseNumber) {
		Json result = null;
		Json sr = null;
		Json load = LOADCASE_JSON.dup();
		load.set("legacy:hasCaseNumber", caseNumber);
		if (DBG) ThreadLocalStopwatch.now("Http Load case...");
		result = Json.read(GenUtils.httpPost(LOADCASE_URL, load.toString(), "Cookie", CIRMADMIN_COOKIE_VALUE));
		sr= result.at("bo");
		int size = sr.toString().length();
		if (DBG)ThreadLocalStopwatch.now("Loaded. Server: " + result.at("server") + " Size(bo) " + size + " bytes");
		ServiceCaseJsonHelper.cleanUpProperties(sr);
		ServiceCaseJsonHelper.assignIris(sr);
		sr.at("properties").delAt("transient$protected");
		sr.at("properties").set("actorEmails", Json.array());
		sr.at("properties").set("hasRemovedAttachment", Json.array());
		//maybe: if !has then sr.at("properties").set("hasAttachment", Json.array());		
		if (DBG)ThreadLocalStopwatch.now("Iris assigned.");
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
		if (DBG) ThreadLocalStopwatch.now("Http Save Update Historic...");
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
		if (DBG) ThreadLocalStopwatch.now("Done.");
		return ok;
	}
	

	
//	/**
//	 * Modifies the service request to update status to C-CLOSED.
//	 * If there are no updates already after the closedDate or no updates at all, the status change user, SR isModifiedBy, 
//	 * and hadDateLastModified will be set to the closedDate.
//	 * 
//	 * @param sr a prefixed iri SR json.
//	 */
//	void closeCase(Json sr, Date closedDate) {
//		boolean updateModified = false;
//		//update
//		sr.at("properties").at("legacy:hasStatus").set("iri", "http://www.miamidade.gov/cirm/legacy#C-CLOSED");
//		if (sr.at("properties").has("hasDateLastModified")) {
//			Date oldMod = GenUtils.parseDate(sr.at("properties").at("hasDateLastModified").asString());
//			updateModified = oldMod.before(closedDate);
//		}
//		if (updateModified || !sr.at("properties").has("hasDateLastModified")) {
//			sr.at("properties").set("hasDateLastModified", GenUtils.formatDate(closedDate));
//			sr.at("properties").set("isModifiedBy", MODIFIED_BY);
//		}
//	}
}

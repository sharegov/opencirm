/**
 * 
 */
package gov.miamidade.cirm.maintenance.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

import org.sharegov.cirm.utils.GenUtils;

import mjson.Json;

/**
 * ComSwrolCloseCaseHttpClient closes COMSWROL cases specified by data file and adds:
 * "Closed automatically because the Permit expired" to each case's description field.
 * 
 * @author Thomas Hilpold
 *
 */
public class ComSwrolCloseCaseHttpClient extends AbstractCaseUpdateHttpClient {

	public static final String DATA_FILE_NAME = "ComSwrolCloseCaseData.csv";
	public static final String DETAILS_APPEND = "Closed automatically because the Permit expired.\r\n";
	public static final long TARGET_DURATION_PER_LINE = 0;
	
	public static void main(String[] args) {
		AbstractCaseUpdateHttpClient.DBG = false;
		ComSwrolCloseCaseHttpClient c = new ComSwrolCloseCaseHttpClient();
		c.processAll();
	}
	
	public ComSwrolCloseCaseHttpClient() {
		super();
	}

	/* (non-Javadoc)
	 * @see gov.miamidade.cirm.maintenance.http.AbstractCaseUpdateHttpClient#getFileUri()
	 */
	@Override
	protected URI getFileUri() throws URISyntaxException {
		return this.getClass().getResource(DATA_FILE_NAME).toURI();
	}

	/* (non-Javadoc)
	 * @see gov.miamidade.cirm.maintenance.http.AbstractCaseUpdateHttpClient#getTargetProcessingTimePerCaseMs()
	 */
	@Override
	protected long getTargetProcessingTimePerCaseMs() {
		return TARGET_DURATION_PER_LINE;
	}

	/* (non-Javadoc)
	 * @see gov.miamidade.cirm.maintenance.http.AbstractCaseUpdateHttpClient#findCaseNumber(java.lang.String)
	 */
	@Override
	protected String findCaseNumber(String column1CaseIdentifierCurRow) {
		if (!column1CaseIdentifierCurRow.contains("-")) throw new IllegalArgumentException(column1CaseIdentifierCurRow);
		return column1CaseIdentifierCurRow;
	}

	/* (non-Javadoc)
	 * @see gov.miamidade.cirm.maintenance.http.AbstractCaseUpdateHttpClient#caseNeedsUpdate(mjson.Json)
	 */
	@Override
	protected boolean caseNeedsUpdate(Json serviceRequest) {
		String status = serviceRequest.at("properties").at("legacy:hasStatus").at("iri").asString();
		return (status.endsWith("O-OPEN") || status.endsWith("O-DUP"));
	}

	/* (non-Javadoc)
	 * @see gov.miamidade.cirm.maintenance.http.AbstractCaseUpdateHttpClient#updateCase(mjson.Json, java.util.Date)
	 */
	@Override
	protected boolean updateCase(Json sr, Date closedDate) {
		boolean updateModified = false;
		//update
		sr.at("properties").at("legacy:hasStatus").set("iri", "http://www.miamidade.gov/cirm/legacy#C-CLOSED");
		String hasDetails = "";
		if (sr.at("properties").has("legacy:hasDetails")) {
			hasDetails = sr.at("properties").at("legacy:hasDetails").asString();
			if (hasDetails.length() > 0) {
				hasDetails += "\r\n";
			}
		}
		hasDetails += DETAILS_APPEND;
		sr.at("properties").set("legacy:hasDetails", hasDetails);		
		
		if (sr.at("properties").has("hasDateLastModified")) {
			Date oldMod = GenUtils.parseDate(sr.at("properties").at("hasDateLastModified").asString());
			updateModified = oldMod.before(closedDate);
		}
		if (updateModified || !sr.at("properties").has("hasDateLastModified")) {
			sr.at("properties").set("hasDateLastModified", GenUtils.formatDate(closedDate));
			sr.at("properties").set("isModifiedBy", "e309888");
		}
		return true;
	}
}

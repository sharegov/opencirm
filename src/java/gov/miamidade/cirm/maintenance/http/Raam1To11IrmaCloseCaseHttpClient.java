/**
 * 
 */
package gov.miamidade.cirm.maintenance.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.List;


import org.sharegov.cirm.utils.GenUtils;

import mjson.Json;

/**
 * Raam1To11IrmaCloseCaseHttpClient closes  RAAM1 ... RAAM11 cases specified by data file and adds an 
 * activity RAAM<1-11>_RAAMSU with no outcome,
 * and act details "Hurricane Irma impacted all of Miami-Dade County on September 10, 2017<br> 
 *   which generated large amounts of service requests for vegetation removal, hanging branches,<br> 
 *   tree trimming, tree removal, stump tree removal, visual obstructions,<br> 
 *   clearing and RAAM contractor complaints. Effective May 04, 2018,<br> 
 *   all service request received up to May 4, 2018 were completed as per the Miami-Dade County's<br> 
 *   assigned clean-up management firm Tetra Tech.".<br>
 * <br>
 * Requires to be run against a local 311hub server with emails and time machine calls disabled in ActivityManager.<br>
 *<br>
 * 
 * @author Thomas Hilpold
 *
 */
public class Raam1To11IrmaCloseCaseHttpClient extends AbstractCaseUpdateHttpClient {

	public static final String DATA_FILE_NAME = "Raam1To11IrmaCloseCaseData.csv";
	public static final String ADD_ACTIVITY_DETAILS = "Hurricane Irma impacted all of Miami-Dade County on September 10, 2017 which generated large amounts of service requests for vegetation removal, "
				+"hanging branches, tree trimming, tree removal, stump tree removal, visual obstructions, clearing, and RAAM contractor complaints."
				+" Effective May 04, 2018, all service request received up to May 4, 2018 were completed as per "
				+"the Miami-Dade County's assigned clean-up management firm Tetra Tech.\r\n";
	public static final String ADD_ACTIVITY_FRAGMENT_PART2 = "_RAAMSU";
	
	public static final String USER_EKEY = "e309888";
	public static final long TARGET_DURATION_PER_LINE = 0;
	
	public static void main(String[] args) {
		AbstractCaseUpdateHttpClient.DBG = true;
		Raam1To11IrmaCloseCaseHttpClient c = new Raam1To11IrmaCloseCaseHttpClient();
		c.processAll();
	}
	
	public Raam1To11IrmaCloseCaseHttpClient() {
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
		return (status.contains("#O-OPEN") || status.contains("#O-WIP"));
	}

	/* (non-Javadoc)
	 * @see gov.miamidade.cirm.maintenance.http.AbstractCaseUpdateHttpClient#updateCase(mjson.Json, java.util.Date)
	 */
	@Override
	protected boolean updateCase(Json sr, Date closedDate) {
		boolean updateModified = false;
		System.out.println("PRE UPDATE");
		System.out.println(sr);
		String srType = sr.at("type").asString();
		if (srType.startsWith("legacy:")) {
			srType = srType.substring("legacy:".length());
		}
		//update
		sr.at("properties").at("legacy:hasStatus").set("iri", "http://www.miamidade.gov/cirm/legacy#C-CLOSED");
		//Create and add new activity
		Json newActObj = createNewActivity(srType, closedDate);
		if (sr.at("properties").has("legacy:hasServiceActivity")) {
			if (sr.at("properties").at("legacy:hasServiceActivity").isArray()) {
				List<Json> existingActArr = sr.at("properties").at("legacy:hasServiceActivity").asJsonList();
				existingActArr.add(newActObj);				
			} else if (sr.at("properties").at("legacy:hasServiceActivity").isObject()) {
				Json existingActObj = sr.at("properties").at("legacy:hasServiceActivity");
				Json arr = Json.array(existingActObj, newActObj);
				sr.at("properties").set("legacy:hasServiceActivity", arr);
			} else {
				throw new IllegalStateException("existing legacy:hasServiceActivity neither object nor array: " 
						+ sr.at("properties").at("legacy:hasServiceActivity"));
			}
		} else {
			if (sr.at("properties").has("hasServiceActivity")) throw new IllegalStateException("serviceact without prefix detected.");
			Json arr = Json.array(newActObj);
			sr.at("properties").set("legacy:hasServiceActivity", arr);
		}
		if (sr.at("properties").has("hasDateLastModified")) {
			Date oldMod = GenUtils.parseDate(sr.at("properties").at("hasDateLastModified").asString());
			updateModified = oldMod.before(closedDate);
		}
		if (updateModified || !sr.at("properties").has("hasDateLastModified")) {
			sr.at("properties").set("hasDateLastModified", GenUtils.formatDate(closedDate));
			sr.at("properties").set("isModifiedBy", USER_EKEY);
		}
		System.out.println("POST UPDATE");
		System.out.println(sr);
		return true;
	}
	
	protected Json createNewActivity(String type, Date createComplDate) {
		Json act = Json.object();
		act.set("type", "legacy:ServiceActivity");
		act.set("hasDateCreated", GenUtils.formatDate(createComplDate));
		act.set("isCreatedBy", USER_EKEY);
		//act.set("legacy:isAccepted", true);
		act.set("legacy:hasCompletedTimestamp", GenUtils.formatDate(createComplDate)); //2017-11-26T22:12:18.000-0000
		act.set("legacy:hasDetails", ADD_ACTIVITY_DETAILS);
		Json actType = Json.object();
		actType.set("iri", "http://www.miamidade.gov/cirm/legacy#" + type + ADD_ACTIVITY_FRAGMENT_PART2);
		actType.set("type", "legacy:Activity");
		act.set("legacy:hasActivity", actType);
//		Json outcome = Json.object();		
//		outcome.set("iri", "http://www.miamidade.gov/cirm/legacy#" + ADD_ACTIVITY_OUTCOME_FRAGMENT);
//		outcome.set("type", "legacy:Outcome");
//		act.set("legacy:hasOutcome", outcome);
		return act;
	}
}

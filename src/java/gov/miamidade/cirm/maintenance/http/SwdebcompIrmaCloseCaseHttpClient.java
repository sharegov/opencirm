/**
 * 
 */
package gov.miamidade.cirm.maintenance.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


import org.sharegov.cirm.utils.GenUtils;

import mjson.Json;

/**
 * SwdebcompIrmaCloseCaseHttpClient closes  SWDEBCOMP SRs specified by data file with choice value answer dependent activity details. <br> 
 * 
 * and SWDEBCOMP_SWMNOTES act details for 10-8 SWDEBCOMP_TYOFCOON_CVL_CV_PRDA <br>
 *   "The SR was forwarded to ISD's Risk Management Division. Risk Management initiated the property damage claim process."<br>
 *   <br>
 * and SWDEBCOMP_SWMNOTES act details for all others (NOT SWDEBCOMP_TYOFCOON_CVL_CV_PRDA) <br>
 *    "The Hurricane Debris Program closed on December 16th, 2017. Outstanding debris issues were addressed through the Department's Bulky Operations."<br>
 * <br>
 * WARNING! Requires to be run against a local 311hub server with emails and time machine calls disabled in ActivityManager.<br>
 *<br>
 * 
 * @author Thomas Hilpold
 *
 */
public class SwdebcompIrmaCloseCaseHttpClient extends AbstractCaseUpdateHttpClient {

	public static final String DATA_FILE_NAME = "SwdebcompIrmaCloseCaseData.csv";
	//Type of complaint on the collection of the debris pile:
	public static final String DETECT_SERVICE_FIELD_FRAGMENT = "SWDEBCOMP_SQ_TYOFCOON";
	//Property Damage 10-8 
	public static final String DETECT_ANSWER_OBJECT_FRAGMENT_8 = "SWDEBCOMP_TYOFCOON_CVL_CV_PRDA-COD";
	public static final String ADD_ACTIVITY_DETAILS_8 = "The SR was forwarded to ISD's Risk Management Division. Risk Management initiated the property damage claim process.\r\n";
	public static final String ADD_ACTIVITY_DETAILS_NOT_8 = "The Hurricane Debris Program closed on December 16th, 2017. Outstanding debris issues were addressed through the Department's Bulky Operations.\r\n";
	public static final String ADD_ACTIVITY_FRAGMENT = "SWDEBCOMP_SWMNOTES";
	public static final String ADD_ACTIVITY_OUTCOME_FRAGMENT = "OUTCOME_COMPLETE";
	
	public static final String USER_EKEY = "e309888";
	public static final long TARGET_DURATION_PER_LINE = 0;
	
	public static void main(String[] args) {
		AbstractCaseUpdateHttpClient.DBG = true;
		SwdebcompIrmaCloseCaseHttpClient c = new SwdebcompIrmaCloseCaseHttpClient();
		c.processAll();
	}
	
	public SwdebcompIrmaCloseCaseHttpClient() {
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
		return (status.contains("#O-OPEN") || status.contains("#O-WIP") || status.contains("#O-DUP"));
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
		boolean is10_8case = is10_8PropertyCase(sr); 
		//update
		sr.at("properties").at("legacy:hasStatus").set("iri", "http://www.miamidade.gov/cirm/legacy#C-CLOSED");
		//Create and add new activity
		Json newActObj = createNewActivity(closedDate, is10_8case);
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
	
	protected Json createNewActivity(Date createComplDate, boolean is10_8_PropertyCase) {
		Json act = Json.object();
		act.set("type", "legacy:ServiceActivity");
		act.set("hasDateCreated", GenUtils.formatDate(createComplDate));
		act.set("isCreatedBy", USER_EKEY);
		act.set("legacy:isAccepted", true);
		act.set("legacy:hasCompletedTimestamp", GenUtils.formatDate(createComplDate)); //2017-11-26T22:12:18.000-0000
		if (is10_8_PropertyCase) {
			act.set("legacy:hasDetails", ADD_ACTIVITY_DETAILS_8);
		} else  {
			act.set("legacy:hasDetails", ADD_ACTIVITY_DETAILS_NOT_8);
		}
		Json actType = Json.object();
		actType.set("iri", "http://www.miamidade.gov/cirm/legacy#" + ADD_ACTIVITY_FRAGMENT);
		actType.set("type", "legacy:Activity");
		act.set("legacy:hasActivity", actType);
		Json outcome = Json.object();		
		outcome.set("iri", "http://www.miamidade.gov/cirm/legacy#" + ADD_ACTIVITY_OUTCOME_FRAGMENT);
		outcome.set("type", "legacy:Outcome");
		act.set("legacy:hasOutcome", outcome);
		return act;
	}
	
	/**
	 * Determines if this SR has an answer to DETECT_SERVICE_FIELD CHARLIST and the answer value object matches DETECT_ANSWER_OBJECT. 
	 * @param sr
	 * @return true if 10-8 property case, false if not or could not be determined because answer missing.
	 */
	protected boolean is10_8PropertyCase(Json sr) {
		//properties / legacy:hasServiceAnswer Obj | Arr
		Json properties = sr.at("properties");
		Json l_hasServiceAnswer = properties.at("legacy:hasServiceAnswer");
		List<Json> hasServiceAnswerArr;
		if (l_hasServiceAnswer.isObject()) {
			hasServiceAnswerArr = new ArrayList<>(1);
			hasServiceAnswerArr.add(l_hasServiceAnswer);
		} else if (l_hasServiceAnswer.isArray()) {
			hasServiceAnswerArr = l_hasServiceAnswer.asJsonList();
		} else {
			System.err.println("Error: This case has no answer object or array.");
			return false;
		}
		for (Json answer : hasServiceAnswerArr) {
			//All and any answers will be checked
			if (is10_8PropertyAnswer(answer)) {
				System.out.println("This is a 10-8 case");
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Detects if the answer is for single select question SWDEBCOMP_SQ_TYOFCOON and the selected answer is Property 10-8
	 * @param answer accepts all answers to all questions
	 * @return
	 */
	protected boolean is10_8PropertyAnswer(Json answerObj) {
		boolean result = false;
		//legacy:hasServiceField /iri == http://www.miamidade.gov/cirm/legacy#SWDEBCOMP_SQ_TYOFCOON
		//legacy:hasAnswerObject /iri == http://www.miamidade.gov/cirm/legacy#SWDEBCOMP_TYOFCOON_CVL_CV_PRDA-COD
		String hasServiceField_iri = answerObj.at("legacy:hasServiceField").at("iri").asString();
		if (hasServiceField_iri.endsWith(DETECT_SERVICE_FIELD_FRAGMENT)) {
			System.out.println(DETECT_SERVICE_FIELD_FRAGMENT + " found.");
			String hasAnswerObject_iri = answerObj.at("legacy:hasAnswerObject").at("iri").asString();
			result = hasAnswerObject_iri.endsWith(DETECT_ANSWER_OBJECT_FRAGMENT_8);
		}
		return result;	
	}
}

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
package gov.miamidade.cirm.other;

import static org.sharegov.cirm.OWL.fullIri;

import static org.sharegov.cirm.utils.GenUtils.ko;
import static org.sharegov.cirm.utils.GenUtils.ok;
import gov.miamidade.cirm.MDRefs;

import java.text.SimpleDateFormat;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import mjson.Json;

import org.restlet.data.ClientInfo;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.RequestScopeFilter;
import org.sharegov.cirm.gis.GisException;
import org.sharegov.cirm.legacy.MessageManager;
import org.sharegov.cirm.rest.LegacyEmulator;
import org.sharegov.cirm.rest.RestService;
import org.sharegov.cirm.stats.CirmStatistics;
import org.sharegov.cirm.stats.CirmStatisticsFactory;
import org.sharegov.cirm.stats.SRCirmStatsDataReporter;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;
import org.sharegov.cirm.utils.XMLU;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * <p>
 * City of Miami, a.k.a. COM, CityView interface client. All integration with COM is implemented in here.
 * </p>
 * Supported Operations: <br>
 * 1. Retrieve Activities from COM and update CiRM SRs with them.<br>
 * 2. Send new cases to COM<br>
 * <br>
 * 2015.11.22 hilpold<br>
 * Case updates received from COM for cases that could not be found AND the case was created prior to CASE_NOT_FOUND_CUTOFF_YEAR (2009),
 * will be acknowledged as if the update was applied succesfully (Y) to prevent resource consuming infinite loops with CitiView.<br>
 * A special tag "Historic Data" will be sent with our response, so COM staff can report on these updates in their system.<br>
 * 2016.10.22 hilpold<br>
 * Redesigned to use cirm transactions with external calls correctly under high load (sendNewCaseToCity, applyUpdatFromCity and all response processing). <br>s
 * 
 * @author boris, Thomas Hilpold
 */
@Path("/other/cityofmiami")
@Produces("application/json")
@Consumes("application/json")
public class CityOfMiamiClient extends RestService
{
	public static final int CASE_NOT_FOUND_CUTOFF_YEAR = 2009;
	public static final String CASE_NOT_FOUND_TAG = "Case prior " + CASE_NOT_FOUND_CUTOFF_YEAR + " was not available";

	Json serviceDescription = OWL.toJSON((OWLNamedIndividual)Refs.configSet.resolve().get("COMWebService"));	
	LegacyEmulator emulator = new LegacyEmulator();
	
	private SRCirmStatsDataReporter srStatsReporter = 
			CirmStatisticsFactory.createServiceRequestStatsReporter(MDRefs.mdStats.resolve(), "CityOfMiamiClient"); 
	
	private String replace(String src, String oldPattern, String newPattern)   
	{
		return src.replace(oldPattern, newPattern);
	}
	
	private String encode(String src) 
	{
		src = replace(src, "&", "&amp;");
		src = replace(src, "\"", "&quot;");
		src = replace(src, "'", "&apos;");
		src = replace(src, "<", "&lt;");
		src = replace(src, ">", "&gt;");
		return src;
	}
	
	/**
	 * Updates a CiRM SR in CiRM with COM response information after sending it as new case to COM.
	 * Existence of a processMessage means COM rejected the SR. In this case, the SR status is X-ERROR.
	 * cityCaseNumber will be set as answer <SRYPE>_CASENUM in the CiRM SR, if exists.
	 * 
	 * @param serviceCase
	 * @param cityResponse with cityCaseNumber and processMessage from the COM response
	 * @return
	 */
	public Json updateServiceCaseWithCityResponse(Json serviceCase, CitySendNewCaseResponse cityResponse)
	{
		ThreadLocalStopwatch.start("START CityOfMiamiClient saveCOMSubmitResult");
		String cityCaseNumber = cityResponse.getCityCaseNumber();
		String cityProcessMessage = cityResponse.getCityProcessMessage();
		// Update SR with passed back information
		if (cityCaseNumber != null)
			serviceCase.at("properties").at("hasServiceAnswer").add(
				Json.object("hasAnswerValue", Json.object("type", "http://www.w3.org/2001/XMLSchema#string", "literal", cityCaseNumber), 
						    "hasServiceField", Json.object("iri", "http://www.miamidade.gov/cirm/legacy#" + serviceCase.at("type").asString() + "_CASENUM")));
		if (cityProcessMessage != null)
		{
			serviceCase.at("properties").set("hasDepartmentError", cityProcessMessage);
			serviceCase.at("properties").at("hasStatus").set("iri", fullIri("legacy:X-ERROR").toString());
		}
		else
		{
			serviceCase.at("properties").delAt("hasDepartmentError");			
			serviceCase.at("properties").at("hasStatus").set("iri", fullIri("legacy:O-LOCKED").toString());
		}
		Json result = emulator.updateServiceCase(serviceCase, "cirmuser");
		ThreadLocalStopwatch.stop("END CityOfMiamiClient saveCOMSubmitResult");
		return result;
	}
	
	/**
	 * Sends a CiRM existing SR to COM
	 * @param serviceCase
	 * @return
	 */
	public CitySendNewCaseResponse sendNewCaseToCityHttpPost(Json serviceCase)
	{
		ThreadLocalStopwatch.start("START CityOfMiamiClient sendNewCase");
		String SOAP_HEADER = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<soap:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soap:Body>"
                + "<strAddServiceRequest xmlns=\"http://citynet.com/\">"
                + "<Authentication>EI1($R</Authentication>" + "<strCSR>";
		String SOAP_FOOTER = "</strCSR></strAddServiceRequest></soap:Body></soap:Envelope>";

		Json locationInfo = null;
		if (serviceCase.at("properties").has("hasXCoordinate"))
		{
			locationInfo = Refs.gisClient.resolve().getLocationInfo(serviceCase.at("properties").at("hasXCoordinate").asDouble(), 
														  serviceCase.at("properties").at("hasYCoordinate").asDouble(), 
														  null);
			if (locationInfo != null) 
			{
				serviceCase.at("properties").set("hasGeoAreaLayer", locationInfo);
			}
		}
		GenUtils.ensureArray(serviceCase.at("properties"), "hasServiceCaseActor");
		GenUtils.ensureArray(serviceCase.at("properties"), "hasServiceAnswer");
		GenUtils.ensureArray(serviceCase.at("properties"), "hasServiceActivity");
		String request = SOAP_HEADER + "<![CDATA[" + makeNewCaseBody(serviceCase) + "]]>" + SOAP_FOOTER;
		serviceCase.at("properties").delAt("hasGeoAreaLayer");
		//
		// Post new case to COM endpoint and retrieve response.
		//
		String response = GenUtils.httpPost(serviceDescription.at("hasEndpoint").asString(), request, new String[] {
		    "Content-type", "text/xml; charset=utf-8",
		    "content-length", Integer.toString(request.length()),
		    "soapaction", serviceDescription.at("hasSOAPAction").asString()
		});

		Document topdoc = XMLU.parse(response);
		Node item = topdoc.getElementsByTagName("strAddServiceRequestResult").item(0);
		String responseContent = item.getFirstChild().getTextContent();
		Document rdoc = XMLU.parse(responseContent);
		// COM interface response contains a CaseNumber (comNumber) and a processMessage
		String cityCaseNumber = XMLU.content(rdoc, "CaseNumber");
//		String inspector = XMLU.content(rdoc, "Inspector");
		String cityProcessMessage = XMLU.content(rdoc, "ProcessMessage");
		
		CitySendNewCaseResponse cityResponse = new CitySendNewCaseResponse(cityCaseNumber, cityProcessMessage); 
		return cityResponse;
	}
	
	/**
	 * Responds to COM after an update from com was retrieved.
	 * 
	 * @param update
	 * @param YN
	 * @param msg
	 * @return
	 */
	public Json respondToCityAfterUpdateHttpPost(Json update, String YN, String msg)
	{
		String header = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" 
			+ "<soap:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
			+ "<soap:Body>"
		    + "<strUpdateCVCasesProcessStatus xmlns=\"http://citynet.com/\">"
			+ "<Authentication>EI1($R</Authentication>"
			+ "<strCSR>";
		String footer = "</strCSR></strUpdateCVCasesProcessStatus></soap:Body></soap:Envelope>";
		String cirmNumber = update.has("CaseNumber") && !update.at("CaseNumber").isNull() ? update.at("CaseNumber").asString():"";
		String body = "<NewDataSet>"
		 + "<xs:schema id=\"NewDataSet\" xmlns=\"\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:msdata=\"urn:schemas-microsoft-com:xml-msdata\">"
		 + "<xs:element name=\"NewDataSet\" msdata:IsDataSet=\"true\" msdata:UseCurrentLocale=\"true\">"
		 + "<xs:complexType>"
		 + "<xs:choice minOccurs=\"0\" maxOccurs=\"unbounded\">"
		 + "<xs:element name=\"tblCECases\">"
		 + "<xs:complexType>"
		 + "<xs:sequence>"
		 + "<xs:element name=\"TrackingNumber\"    type=\"xs:string\" minOccurs=\"0\" />"
		 + "<xs:element name=\"flagCSRready\"      type=\"xs:string\" minOccurs=\"0\" />"
		 + "<xs:element name=\"flagCSRProcessOK\"  type=\"xs:string\" minOccurs=\"0\" />"
		 + "<xs:element name=\"CSRProcessMessage\" type=\"xs:string\" minOccurs=\"0\" />"
		 + "</xs:sequence>"                                                                                                                                       
		 + "</xs:complexType>"
		 + "</xs:element>"
		 + "</xs:choice>"
		 + "</xs:complexType>"
		 + "</xs:element>"
		 + "</xs:schema>"
		 + "<tblCECases>"
		 + "<TrackingNumber>" + cirmNumber + "</TrackingNumber>"
		 + "<flagCSRready>N</flagCSRready>"
		 + "<flagCSRProcessOK>" + YN + "</flagCSRProcessOK>"                                                            
		 + "<CSRProcessMessage>" + msg+ "</CSRProcessMessage>"
		 + "</tblCECases>"
		 + "</NewDataSet>";

		String request = header + encode(body) + footer;
		
		String response = GenUtils.httpPost(serviceDescription.at("hasEndpoint").asString(), request, new String[] {
		    "Content-type", "text/xml; charset=utf-8",
		    "content-length", Integer.toString(request.length()),
		    "soapaction", "http://citynet.com/strUpdateCVCasesProcessStatus"
		});
	
		Document topdoc = XMLU.parse(response);
		
		Node item = topdoc.getElementsByTagName("strUpdateCVCasesProcessStatusResult").item(0);
		String responseContent = item.getFirstChild().getTextContent();
		Document rdoc = XMLU.parse(responseContent);
		String processMessage = XMLU.content(rdoc, "ProcessMessage");
		
		if (processMessage == null || "Process OK".equals(processMessage))
			return ok();
		else
			return ko(processMessage);
	}
	
	/**
	 * Applies one update received by COM to an existing CiRM SR, by adding a maximum of 3 activities.
	 * This method also responds to COM.
	 * No 
	 *  
	 * @param update
	 * @return
	 */
	public Json applyUpdateFromCity(final Json update)
	{
		Json updateResult = null;
		try {
			updateResult = Refs.defaultRelationalStore.resolve().txn(new CirmTransaction<Json>() {
    		public Json call()
    		{								
    //			System.out.println("COM update:" + update);
    			Json existingSR = emulator.lookupServiceCase(Json.object("legacy:hasCaseNumber", update.at("CaseNumber"), "type", "legacy:ServiceCase"));
    			if (existingSR.is("ok", false)) 
    			{
    				srStatsReporter.failed("CirmUpdateAfterComRXUpdate", CirmStatistics.UNKNOWN, 
    						"" + update.at("CaseNumber"), 
    						"Case to update was not found in CiRM", "" + update);
    				return existingSR;
    			}
    			existingSR = existingSR.at("bo");
    			String typeCode = existingSR.at("type").asString();
    			existingSR.at("properties").delAt("ServiceField21643565");
    			Json newActivities = Json.array();
    			try {
        			if (update.at("code1", "").asString().length() > 0 && update.at("value1").asString().length() > 0)
        			{
        				newActivities.add(Json.object("hasActivity", Json.object("type", "Activity", "iri", "legacy:" + typeCode + "_" + update.at("code1").asString()),
        								 "hasOutcome", Json.object("type", "Outcome", "iri", "legacy:OUTCOME_" + update.at("value1", "").asString()),
        								 "hasDateCreated", GenUtils.formatDate(new java.util.Date()),
        								 "hasCompletedTimestamp", GenUtils.formatDate(new java.util.Date())));
        			}
        			if (update.at("code2", "").asString().length() > 0 && update.at("value2", "").asString().length() > 0)
        			{
        				newActivities.add(Json.object("hasActivity", Json.object("type", "Activity", "iri", "legacy:" + typeCode + "_" + update.at("code2").asString()),
        								 "hasOutcome", Json.object("type", "Outcome", "iri", "legacy:OUTCOME_" + update.at("value2", "").asString()),
        								 "hasDateCreated", GenUtils.formatDate(new java.util.Date()),
        								 "hasCompletedTimestamp", GenUtils.formatDate(new java.util.Date())));
        			}
        			if (update.at("code3", "").asString().length() > 0 && update.at("value3", "").asString().length() > 0)
        			{
        				newActivities.add(Json.object("hasActivity", Json.object("type", "Activity", "iri", "legacy:" + typeCode + "_" + update.at("code3").asString()),
        								 "hasOutcome", Json.object("type", "Outcome", "iri", "legacy:OUTCOME_" + update.at("value3", "").asString()),
        								 "hasDateCreated", GenUtils.formatDate(new java.util.Date()),
        								 "hasCompletedTimestamp", GenUtils.formatDate(new java.util.Date())));
        			}
        			existingSR.at("properties").at("hasServiceActivity", Json.array()).with(newActivities);
        			Json updateResultInt = emulator.updateServiceCase(OWL.resolveIris(OWL.prefix(existingSR), null), "department");
       				srStatsReporter.succeeded("CirmUpdateAfterComRXUpdate", existingSR);
        			return updateResultInt;
    			} catch (Throwable t) {
    				srStatsReporter.failed("CirmUpdateAfterComRXUpdate", existingSR, t.toString(),
    						"Update sr with " + newActivities.asJsonList().size() + "new activities received by COM failed ");
    				throw t;
    			}
    		}});
		} catch (Throwable t) {
			//Transaction with potential retries is over, now we have an exception that we need to consider in our
			//acknowledgment to the city.
			ThreadLocalStopwatch.error("FAIL COM UPDATE " + update + " with " + t);
			updateResult =  GenUtils.ko(t);
		}
		//Respond to city after applying city update to sr in cirm
		Json ackResult = Json.nil();
		boolean retryNeeded;
		long startTime = System.currentTimeMillis();
		do {
			retryNeeded = false;
			try {
    			if (!updateResult.is("ok", true)) {
        			ackResult = respondToCityAfterUpdateHttpPost(update, "N", encode(updateResult.at("error").asString()));
        		} else {
        			ackResult = respondToCityAfterUpdateHttpPost(update, "Y", "");
        		}
			} catch(Throwable t) {				
				retryNeeded = isExceptionWorthRetrying(t);
				if (!retryNeeded) {
					throw new RuntimeException("RespondToCityAfterUpdate to CityView interface failed with : " + t, t);
				} else {
    				try {
    					Thread.sleep(1000);
    				} catch (InterruptedException e) {
    				}
				}
			}
		} while (retryNeeded && (System.currentTimeMillis() - startTime < 30000));
		if (retryNeeded) {
			throw new RuntimeException("RespondToCityAfterUpdate to CityView interface failed for over 30 seconds with retriable exception");
		}
		return ackResult;
	}
	
	/**
	 * <p>
	 * Get the multiple updates from COM to apply them to existing SRs in CiRM.
	 * COM updates lead to up to 3 NEW ACTIVITIES for an SR in CiRM
	 * 
	 * Possible failures:
	 * An update node does not contain a case number
	 * 
	 * </p>
	 *   
	 */
	@GET
	@Path("/retrieveUpdates")
	public Json retrieveUpdatesFromCityHttpPost()
	{		
		ThreadLocalStopwatch.startTop("START CityOfMiamiClient /retrieveUpdates");
		forceClientExempt.set(true);
	    String uprequest = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<soap:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soap:Body>"
                + "<strGetServiceRequestReady xmlns=\"http://citynet.com/\">"
                + "<Authentication>EI1($R</Authentication>" 
                + "</strGetServiceRequestReady></soap:Body></soap:Envelope>";

	    try
	    {
			String response = GenUtils.httpPost(serviceDescription.at("hasEndpoint").asString(), uprequest, new String[] {
			    "Content-type", "text/xml; charset=utf-8",
			    "content-length", Integer.toString(uprequest.length()),
			    "soapaction", "http://citynet.com/strGetServiceRequestReady"
			});
	
			Document topdoc = XMLU.parse(response); 
			Document rdoc = XMLU.parse(topdoc.getElementsByTagName("strGetServiceRequestReadyResult").item(0).getTextContent());
			NodeList updateNodes = rdoc.getElementsByTagName("tblCECases");
			Json result = Json.array();
			ThreadLocalStopwatch.now("NOW CityOfMiamiClient /retrieveUpdates updateNodes.length() = " + updateNodes.getLength());			
			for (int i = 0; i < updateNodes.getLength(); i++)
			{
				Element n = (Element)updateNodes.item(i);
				final Json update = Json.object(
					"CaseNumber", XMLU.content(n, "TrackingNumber"),
					"code1", XMLU.content(n, "Activity1"),
					"value1", XMLU.content(n, "AssignedTo", ""),
					"code2", XMLU.content(n, "Activity2"),
					"value2", XMLU.content(n, "Outcome2", ""),
					"code3", XMLU.content(n, "Activity3"),
					"value3", XMLU.content(n, "Outcome3", "")
				);
				if (update.at("CaseNumber").isNull())
				{
					respondToCityAfterUpdateHttpPost(update, "N", "CiRM tracking number is null.");
					continue;
				}
				try
				{
					Json updateResult = applyUpdateFromCity(update);
					// TODO : if the update failed somehow, we need to log it somewhere for auditing purpose
					// nowhere else for now, but stdout
					if (updateResult.is("ok", false))
					{
						if (isCityUpdateForPreCutoffCase(update)) {
							ThreadLocalStopwatch.error("COM UPDATE FOR PRE " 
									+ CASE_NOT_FOUND_CUTOFF_YEAR 
									+ "  case " + i 
									+ " FAILED: " + update.at("CaseNumber") + " result: " + updateResult 
									+ ", responding Y " + CASE_NOT_FOUND_TAG);
							//respond as if update was applied, but provide special message.
							respondToCityAfterUpdateHttpPost(update, "Y", CASE_NOT_FOUND_TAG);							
						} else {
							//do not acknowledge, CitiView will resend
							ThreadLocalStopwatch.error("ERROR: COM UPDATE " + i + " FAILED: " + update + "\nCOM UPDATED RESULT: " + updateResult);
						}
					}
					result.add(updateResult);	
				}
				catch (Throwable t)
				{
					ThreadLocalStopwatch.error("ERROR: COM UPDATE " + i + " FAILED unexpectedly: " + update + "\n exception " + t);
					t.printStackTrace();
					result.add(ko(t));
				}
			} //end for
			ThreadLocalStopwatch.stop("END CityOfMiamiClient /retrieveUpdates returning ok, maybe with errors");
			return ok().set("data", result);
	    }
	    catch (Throwable t)
	    {
			ThreadLocalStopwatch.fail("FAILED CityOfMiamiClient /retrieveUpdates with" + t);
	    	return ko(t);
	    }
	}
	
	/**
	 * Checks if casenumber year in an update received from COM is pre cutoff.
	 * @param update
	 * @return false, if could not determine or not.
	 */
	private boolean isCityUpdateForPreCutoffCase(Json update) {
		if (ServiceCaseJsonHelper.isCaseNumberString(update.at("CaseNumber"))) {
			String caseNum = update.at("CaseNumber").asString().trim();
			int updateCaseYear4 = ServiceCaseJsonHelper.getCaseNumberYear(caseNum);
			return updateCaseYear4 < CASE_NOT_FOUND_CUTOFF_YEAR;
		} else {
			return false;
		}
	}
	
	private String makeNewCaseBody(Json sr)
	{
		Json p = sr.at("properties");
		Json addr = p.at("atAddress").dup();
		Json actor = Json.object();
		if (p.has("hasServiceCaseActor"))
			for (Json ac  : p.at("hasServiceCaseActor").asJsonList())
				if (ac.has("Name")) { actor = ac.dup(); break; }
		// TODO: the email address is an object for some reason - a modeling problem,which needs
		// to be fixed. So we converted it here.
		if (actor.has("hasEmailAddress") && actor.at("hasEmailAddress").isObject())
			actor.set("hasEmailAddress", actor.at("hasEmailAddress").at("iri"));
		SimpleDateFormat comDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		String comDateReceived = comDateFormat.format(GenUtils.parseDate(p.at("hasDateCreated").asString()));
		
		// TMP - until all street types get assigned a label from their USPS_Suffix in the ontology.
		String streetType = "";
		if (addr.has("hasStreetType"))
		{
			Json stobj = OWL.toJSON(OWL.individual(addr.at("hasStreetType").at("iri").asString()));
			streetType = stobj.at("USPS_Suffix", "").asString();
		}
		String actorType = "";
		if (actor.has("hasServiceActor"))
		{
			Json akind = actor.at("hasServiceActor");
			if (akind.has("hasLegacyCode"))
				actorType = akind.at("hasLegacyCode").asString();
			else
				actorType = akind.at("iri").asString().split("#")[1];
		}
		String zone = "";
		if (!p.at("hasGeoAreaLayer", Json.object()).at("Code_Enforcement_Zones", Json.object()).isNull())
			zone = p.at("hasGeoAreaLayer").at("Code_Enforcement_Zones").at("CEZNID", "").asString();
		String BODY = "<NewDataSet><tblCECases>" + "<TrackingNumber>"
				+ p.at("hasCaseNumber").asString()
				+ "</TrackingNumber>"
				+ "<RequestCategory>"
				+ sr.at("type").asString()
				+ "</RequestCategory>"
				+ "<Description>"
				+ encode(getProblemDescription(p))
				+ "</Description>"
				+ "<DateReceived>"
				+ comDateReceived
				+ "</DateReceived>"
				+ "<STNUMBER>"
				+ addr.at("Street_Number", "").asString()
				+ "</STNUMBER>"
				+ "<STDIRECTION>"
				+ addr.at("Street_Direction", Json.object()).at("label", "").asString()
				+ "</STDIRECTION>"
				+ "<STNAME>"
				+ addr.at("Street_Name", "").asString()
				+ "</STNAME>"
				+ "<STTYPE>"
				+ streetType // addr.at("hasStreetType", Json.object()).at("USPS_Suffix", "").asString()
				+ "</STTYPE>"
				+ "<STUNIT xml:space=\"preserve\">"
				+ addr.at("Street_Unit_Number", "").asString()
				+ "</STUNIT>"
				+ "<Zone>"
				+ zone
				+ "</Zone>"
				+ // MIAZONE
				"<ContactType>"
				+ actorType
				+ "</ContactType>"
				+ "<ContactName>"
				+ actor.at("Name", "").asString() + "  " + actor.at("LastName", "").asString()
				+ "</ContactName>"
				+ "<ContactBusinessName xml:space=\"preserve\"></ContactBusinessName>"
				+ // <-- find out what value goes here
				"<ContactAddress1>" + actor.at("atAddress", Json.object()).at("fullAddress", "").asString() + "</ContactAddress1>"
				+ "<ContactAddress2>" + "" + "</ContactAddress2>"
				+ "<ContactCity>" + actor.at("atAddress", Json.object()).at("Street_Address_City", Json.object()).at("Name", "").asString() + "</ContactCity>" + "<ContactZip>"
				+ actor.at("atAddress", Json.object()).at("Zip_Code", "").asString() + "</ContactZip>" 
				+ "<ContactEmail>" + actor.at("hasEmailAddress", "").asString()
				+ "</ContactEmail>" + "<ContactPhone>" + actor.at("HomePhoneNumber", "").asString()
				+ "</ContactPhone>" + "<ContactFax>" + actor.at("FaxNumber", "").asString() + "</ContactFax>"
				+ "</tblCECases></NewDataSet>";
		return BODY;
	}
	
	private String getProblemDescription(Json sr)
	{
		StringBuffer desc = new StringBuffer();
		for (Json ans : sr.at("hasServiceAnswer").asJsonList())
		{
			String answer = "";
			if (ans.has("hasAnswerValue"))
				if (ans.at("hasAnswerValue").isObject())
					answer = ans.at("hasAnswerValue").at("literal").asString();
				else
					answer = ans.at("hasAnswerValue").asString();
			else if (ans.has("hasAnswerObject"))
				answer = ans.at("hasAnswerObject").at("label").asString();
			desc.append(ans.at("hasServiceField").at("label").asString() + ": " + answer + "\n");
		}
		return desc.toString();
	}	
	
	/**
	 * Sends a ServiceCase to COM 
	 * tx1: find/load case,
	 * NoTx: send to com, store result (retry http)
	 * tx2: process result, lock or xerror case.
	 * @param data
	 * @return
	 */
	@POST
	@Path("/sendnew")
	public Json sendCaseToCity(Json data)
	{
		if (!data.has("caseNumber")) {
			ThreadLocalStopwatch.fail("FAIL CityOfMiamiClient /sendnew Case number property missing");
			return ko("Case number property missing from JSON object.");
		}
		forceClientExempt.set(true);
		//1 Find and load case tx
		final String cirmCaseNumber = data.at("caseNumber").asString();
		ThreadLocalStopwatch.start("START CityOfMiamiClient /sendnew " + cirmCaseNumber);
		try
		{
    		Json sr = Refs.defaultRelationalStore.resolve().txn(new CirmTransaction<Json>() {
                public Json call()
                {       
            		long boid = emulator.toServiceCaseId(cirmCaseNumber);
            		Json sr = emulator.lookupServiceCase(boid);
            		return sr;
                }});              		
    		if (!sr.is("ok", true) || 
            		!sr.at("bo").at("properties").at("hasStatus").at("iri").asString().contains("O-OPEN"))
            {
            	ThreadLocalStopwatch.fail("END CityOfMiamiClient /sendnew lookup sr not found or not O-OPEN - not sending " + cirmCaseNumber);
            	return sr;
            }
    		sr = sr.at("bo");
    		OWL.resolveIris(sr, null);
    		
    		//2 Send case to city via sync webservice call, retrieve response
    		//This fails with exception if city is not avail
    		final CitySendNewCaseResponse cityReponse = sendNewCaseToCityHttpPost(sr);    		
    		
    		//3 Send email if process message == failure
    		if (cityReponse.getCityProcessMessage() != null) 
    		{
    			ThreadLocalStopwatch.error("CityOfMiamiClient [COM CASE REJECTED] department error email sent for " 
    			+ cirmCaseNumber 
    			+ " message " 
    			+ cityReponse.getCityProcessMessage());
    			MessageManager.get().sendEmail("cirm@miamidade.gov", 
					"hilpold@miamidade.gov;ioliva@miamigov.com;VOchoa@miamigov.com;angel.martin@miamidade.gov;silval@miamidade.gov;imarp@miamidade.gov", 
					"[COM CASE REJECTED] " + cirmCaseNumber, cityReponse.getCityProcessMessage());
    		}

    		//4 Reload and Update (Lock or X-ERR) 311Hub case tx with response
    		Json updateResult = Refs.defaultRelationalStore.resolve().txn(new CirmTransaction<Json>() {
                public Json call()
                {                      	
            		ThreadLocalStopwatch.now("NOW CityOfMiamiClient /sendnew update (Lock/Xerr) case with city response. City case number: " + cityReponse.getCityCaseNumber());            		
            		long boid = emulator.toServiceCaseId(cirmCaseNumber);
            		Json sr = emulator.lookupServiceCase(boid);
            		sr = sr.at("bo");
            		//Update CiRM SR with this information	
            		OWL.resolveIris(sr, null);
            		Json updateResult = updateServiceCaseWithCityResponse(sr, cityReponse);
            		srStatsReporter.succeeded("sendCaseToCOM rest /sendnew", sr.at("bo"));
            		//return ok().set("updateResult", updateResult);
            		return updateResult; //ok with bo property
                }});
    		ThreadLocalStopwatch.stop("END CityOfMiamiClient /sendnew case sent " + cirmCaseNumber);
    		return updateResult;
		}
		catch (Throwable ex)
		{
			srStatsReporter.failed("FAIL sendCaseToCOM rest /sendnew", data, "" + GenUtils.getRootCause(ex), "" + GenUtils.getRootCause(ex).getMessage());
			if (isExceptionWorthRetrying(ex))
			{
				ThreadLocalStopwatch.stop("FAIL CityOfMiamiClient /sendnew with " + ex + " but will retry automatically in 30 minutes.");
				return scheduleSendNewRetry(ex, "/other/cityofmiami/sendnew", 30, data);
			}
			else 
			{
				ThreadLocalStopwatch.stop("FAIL FATAL CityOfMiamiClient /sendnew with " + ex + ". Sending Fatal email, no retry.");
				GenUtils.reportFatal("While sending COM case, caseNumber was " + data.at("caseNumber"), ex.toString(), ex);
				ex.printStackTrace();
				return ko(ex);
			}
		}		
	}
	
	@POST
	@Path("/testperms")
	public Json testPerms(Json data)
	{
		forceClientExempt.set(true);		
		ClientInfo clientInfo = (ClientInfo)RequestScopeFilter.get("clientInfo");
		String clientIp = clientInfo.getAddress();
		String exemptHostName = getClientExemptIpToHostMap().get(clientIp);		
		return ok().set("client-exempt", isClientExempt()).set("clientIp", clientIp).set("exemptHost", exemptHostName);
	}
	
	/**
	 * Submits a time machine task to retry 
	 * @param ex
	 * @param path callback path
	 * @param minutes 
	 * @param data
	 * @return
	 */
	Json scheduleSendNewRetry(Throwable ex, String path, int minutes, Json data)
	{
		ThreadLocalStopwatch.now("CityOfMiamiClient retry by time machine: /other/cityofmiami/sendnew in 30 mins");
		long initiatedAt = data.at("initiatedAt", 0).asLong();		
		if (initiatedAt + 24*60*60*1000 < System.currentTimeMillis())
		{
			srStatsReporter.failed("scheduleretry", data, "Giving up after 24 hours", "" + ex);
			GenUtils.reportFatal("While sending (after 24 hour long attempts: " + initiatedAt + " - " + System.currentTimeMillis() +  
					") " + data.at("caseNumber").asString(), ex.toString(), ex);
			return ko(ex);
		}
		else
		{
			if (initiatedAt + 60*60*1000 > System.currentTimeMillis() && GenUtils.getRootCause(ex) instanceof java.net.SocketException)
				// TODO : remove hardcoded email
		    	MessageManager.get().sendEmail("cirm@miamidade.gov", 
						"ioliva@miamigov.com;hilpold@miamidade.gov;rajiv@miamidade.gov", 
						"CityView web service seems down", "We've trying to contact web service at  " + serviceDescription.at("hasEndPoint") + 
						" for an hour and connection fails repeatedly.");			
				
			Json j = GenUtils.timeTask(30, "/other/cityofmiami/sendnew", data);
			if (j.is("ok", false))
				GenUtils.reportFatal("While retrying new case task " + data.at("caseNumber").asString(), j.toString(), ex);
			return ko("Failed with " + ex.toString() + ", resubmission success=" + j.is("ok", true));
		}										
	}
	
	/**
	 * @return true if cause is a Socket or GisException.
	 */
	boolean isExceptionWorthRetrying(Throwable t)
	{
		Throwable root = GenUtils.getRootCause(t);
		return root instanceof java.net.SocketException ||
			   root instanceof GisException;
	}
	
	private static class CitySendNewCaseResponse {

		private String cityCaseNumber;
		private String cityProcessMessage;
		
		CitySendNewCaseResponse(String cityCaseNumber, String cityProcessMessage) {
			this.cityCaseNumber = cityCaseNumber;
			this.cityProcessMessage = cityProcessMessage;
		}

		public String getCityCaseNumber() {
			return cityCaseNumber;
		}

		public String getCityProcessMessage() {
			return cityProcessMessage;
		}
	}
}

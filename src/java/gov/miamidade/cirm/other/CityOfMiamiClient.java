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
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;
import org.sharegov.cirm.utils.XMLU;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * <p>
 * City of Miami, a.k.a. COM, interface. All integration with COM is implemented in here:
 * </p>
 * 
 * <ol>
 * <li></li>
 * <li></li>
 * <li></li>
 * <li></li> 
 * </ol>
 * 
 * @author boris
 */
@Path("/other/cityofmiami")
@Produces("application/json")
@Consumes("application/json")
public class CityOfMiamiClient extends RestService
{
	Json serviceDescription = OWL.toJSON((OWLNamedIndividual)Refs.configSet.resolve().get("COMWebService"));	
	LegacyEmulator emulator = new LegacyEmulator();
	
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
	
	public Json saveCOMSubmitResult(Json serviceCase, String comNumber, String processMessage)
	{
		ThreadLocalStopwatch.start("START CityOfMiamiClient saveCOMSubmitResult");
		// Update SR with passed back information
		if (comNumber != null)
			serviceCase.at("properties").at("hasServiceAnswer").add(
				Json.object("hasAnswerValue", Json.object("type", "http://www.w3.org/2001/XMLSchema#string", "literal", comNumber), 
						    "hasServiceField", Json.object("iri", "http://www.miamidade.gov/cirm/legacy#" + serviceCase.at("type").asString() + "_CASENUM")));
		if (processMessage != null)
		{
			serviceCase.at("properties").set("hasDepartmentError", processMessage);
			serviceCase.at("properties").at("hasStatus").set("iri", fullIri("legacy:X-ERROR").toString());
			// TODO, remove hard emails from here...also, may be X-ERROR status should trigger emails
			// for all SR types, not just COM, configurable somehow...
			ThreadLocalStopwatch.error("CityOfMiamiClient [COM CASE REJECTED] department error email sent ");
	    	MessageManager.get().sendEmail("cirm@miamidade.gov", 
					"boris@miamidade.gov;ioliva@miamigov.com;VOchoa@miamigov.com;angel.martin@miamidade.gov;silval@miamidade.gov", 
					"[COM CASE REJECTED] " + serviceCase.at("properties").at("hasCaseNumber"), processMessage);			
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
	
	public Json sendNewCase(Json serviceCase)
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
			locationInfo = Refs.gisClient.resolve().getLocationInfo(serviceCase.at("properties").at("hasXCoordinate").asDouble(), 
														  serviceCase.at("properties").at("hasYCoordinate").asDouble(), 
														  null);
		
		if (locationInfo != null)
			serviceCase.at("properties").set("hasGeoAreaLayer", locationInfo);
		GenUtils.ensureArray(serviceCase.at("properties"), "hasServiceCaseActor");
		GenUtils.ensureArray(serviceCase.at("properties"), "hasServiceAnswer");
		GenUtils.ensureArray(serviceCase.at("properties"), "hasServiceActivity");
		String request = SOAP_HEADER + "<![CDATA[" + makeNewCaseBody(serviceCase) + "]]>" + SOAP_FOOTER;
		serviceCase.at("properties").delAt("hasGeoAreaLayer");
		String response = GenUtils.httpPost(serviceDescription.at("hasEndpoint").asString(), request, new String[] {
		    "Content-type", "text/xml; charset=utf-8",
		    "content-length", Integer.toString(request.length()),
		    "soapaction", serviceDescription.at("hasSOAPAction").asString()
		});

		Document topdoc = XMLU.parse(response);
		Node item = topdoc.getElementsByTagName("strAddServiceRequestResult").item(0);
		String responseContent = item.getFirstChild().getTextContent();
		Document rdoc = XMLU.parse(responseContent);
		String comNumber = XMLU.content(rdoc, "CaseNumber");
//		String inspector = XMLU.content(rdoc, "Inspector");
		String processMessage = XMLU.content(rdoc, "ProcessMessage");
		Json upr = this.saveCOMSubmitResult(serviceCase, comNumber, processMessage);
		ThreadLocalStopwatch.stop("END CityOfMiamiClient sendNewCase");
		return ok().set("updateResult", upr);
	}
	
	public Json acknowledgeUpdate(Json update, String YN, String msg)
	{
//		if (1 > 0)
//			return ok();
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
	
	public Json applyUpdate(final Json update)
	{
		return Refs.defaultRelationalStore.resolve().txn(new CirmTransaction<Json>() {
		public Json call()
		{								
//			System.out.println("COM update:" + update);
			Json existing = emulator.lookupServiceCase(Json.object("legacy:hasCaseNumber", update.at("CaseNumber"), "type", "legacy:ServiceCase"));
			if (existing.is("ok", false))
				return existing;
			existing = existing.at("bo");
			String typeCode = existing.at("type").asString();
			existing.at("properties").delAt("ServiceField21643565");
			Json newActivities = Json.array();
			if (update.at("code1", "").asString().length() > 0 && update.at("value1").asString().length() > 0)
				newActivities.add(Json.object("hasActivity", Json.object("type", "Activity", "iri", "legacy:" + typeCode + "_" + update.at("code1").asString()),
								 "hasOutcome", Json.object("type", "Outcome", "iri", "legacy:OUTCOME_" + update.at("value1", "").asString()),
								 "hasDateCreated", GenUtils.formatDate(new java.util.Date()),
								 "hasCompletedTimestamp", GenUtils.formatDate(new java.util.Date())));
			if (update.at("code2", "").asString().length() > 0 && update.at("value2", "").asString().length() > 0)
				newActivities.add(Json.object("hasActivity", Json.object("type", "Activity", "iri", "legacy:" + typeCode + "_" + update.at("code2").asString()),
								 "hasOutcome", Json.object("type", "Outcome", "iri", "legacy:OUTCOME_" + update.at("value2", "").asString()),
								 "hasDateCreated", GenUtils.formatDate(new java.util.Date()),
								 "hasCompletedTimestamp", GenUtils.formatDate(new java.util.Date())));
			if (update.at("code3", "").asString().length() > 0 && update.at("value3", "").asString().length() > 0)
				newActivities.add(Json.object("hasActivity", Json.object("type", "Activity", "iri", "legacy:" + typeCode + "_" + update.at("code3").asString()),
								 "hasOutcome", Json.object("type", "Outcome", "iri", "legacy:OUTCOME_" + update.at("value3", "").asString()),
								 "hasDateCreated", GenUtils.formatDate(new java.util.Date()),
								 "hasCompletedTimestamp", GenUtils.formatDate(new java.util.Date())));			       
			existing.at("properties").at("hasServiceActivity", Json.array()).with(newActivities);
			Json updateResult = emulator.updateServiceCase(OWL.resolveIris(OWL.prefix(existing), null), "department");
			Json ackResult = Json.nil();
			
			// Doing the acknowledgment inside the transaction implies the following potential irregularities:
			// (a) the transaction can be retried several times until it succeeds, so the acknowledgment will be sent several times
			// (b) the transaction may fail permanently in which case the acknoweledgment will be wrong. However, such a failure
			// during the commit would mean a bug that needs to be addressed. So we only need to make sure the error is propagated
			if (!updateResult.is("ok", true))
				ackResult = acknowledgeUpdate(update, "N", encode(updateResult.at("error").asString()));
			else
				ackResult = acknowledgeUpdate(update, "Y", "");
			return ackResult;
		}});
	}
	
	/**
	 * <p>
	 * Get the latest updates from COM and apply them locally.
	 * </p>
	 *   
	 */
	@GET
	@Path("/retrieveUpdates")
	public Json retrieveUpdates()
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
			ThreadLocalStopwatch.now("CityOfMiamiClient /retrieveUpdates updateNodes.length() " + updateNodes.getLength());			
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
					acknowledgeUpdate(update, "N", "CiRM tracking number is null.");
					continue;
				}
				try
				{
					Json upResult = applyUpdate(update);
					// TODO : if the update failed somehow, we need to log it somewhere for auditing purpose
					// nowhere else for now, but stdout
					if (upResult.is("ok", false))
					{
						ThreadLocalStopwatch.error("COM UPDATE " + i + " FAILED: " + update + "\nCOM UPDATED RESULT: " + upResult);
					}
					result.add(upResult);	
				}
				catch (Throwable t)
				{
					ThreadLocalStopwatch.error("COM UPDATE " + i + " FAILED unexpectedly: " + update + "\n exception " + t);
					result.add(ko(t));
				}
			}
			ThreadLocalStopwatch.stop("END CityOfMiamiClient /retrieveUpdates returning ok, maybe with errors");
			return ok().set("data", result);
	    }
	    catch (Throwable t)
	    {
			ThreadLocalStopwatch.fail("FAILED CityOfMiamiClient /retrieveUpdates with" + t);
	    	return ko(t);
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
	
	@POST
	@Path("/sendnew")
	public Json sendCaseToCOM(Json data)
	{
		ThreadLocalStopwatch.start("START CityOfMiamiClient /sendnew");
		forceClientExempt.set(true);
		if (!data.has("caseNumber")) {
			ThreadLocalStopwatch.fail("FAIL CityOfMiamiClient /sendnew Case number property missing");
			return ko("Case number property missing from JSON object.");
		}
		String casenumber = data.at("caseNumber").asString();
		long boid = emulator.toServiceCaseId(casenumber);
		try
		{
			Json sr = emulator.lookupServiceCase(boid);
			OWL.resolveIris(sr, null);
			if (!sr.is("ok", true) || 
				!sr.at("bo").at("properties").at("hasStatus").at("iri").asString().contains("O-OPEN"))
			{
				ThreadLocalStopwatch.fail("END CityOfMiamiClient /sendnew lookup sr not ok or not O-OPEN - not sending");
				return sr;
			}
			Json sendResult = sendNewCase(sr.at("bo"));
			ThreadLocalStopwatch.stop("END CityOfMiamiClient /sendnew case sent ");
			return sendResult;
		}
		catch (Throwable ex)
		{
			ThreadLocalStopwatch.stop("FAIL CityOfMiamiClient /sendnew with " + ex);
			if (!isWorthRetrying(ex))
			{
				GenUtils.reportFatal("While sending COM case " + data.at("caseNumber").asLong(), ex.toString(), ex);
				return ko(ex);
			}
			else
				return retry(ex, "/other/cityofmiami/sendnew", 30, data);
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
	
	Json retry(Throwable ex, String path, int minutes, Json data)
	{
		ThreadLocalStopwatch.now("CityOfMiamiClient retry by time machine: /other/cityofmiami/sendnew in 30 mins");
		long initiatedAt = data.at("initiatedAt", 0).asLong();		
		if (initiatedAt + 24*60*60*1000 < System.currentTimeMillis())
		{
			GenUtils.reportFatal("While sending (after 24 hour long attempts: " + initiatedAt + " - " + System.currentTimeMillis() +  
					") " + data.at("caseNumber").asString(), ex.toString(), ex);
			return ko(ex);
		}
		else
		{
			if (initiatedAt + 60*60*1000 > System.currentTimeMillis() && GenUtils.getRootCause(ex) instanceof java.net.SocketException)
				// TODO : remove hardcoded email
		    	MessageManager.get().sendEmail("cirm@miamidade.gov", 
						"ioliva@miamigov.com", 
						"CityView web service seems down", "We've trying to contact web service at  " + serviceDescription.at("hasEndPoint") + 
						" for an hour and connection fails repeatedly.");			
				
			Json j = GenUtils.timeTask(30, "/other/cityofmiami/sendnew", data);
			if (j.is("ok", false))
				GenUtils.reportFatal("While retrying new case task " + data.at("caseNumber").asString(), j.toString(), ex);
			return ko("Failed with " + ex.toString() + ", resubmission success=" + j.is("ok", true));
		}										
	}
	
	boolean isWorthRetrying(Throwable t)
	{
		Throwable root = GenUtils.getRootCause(t);
		return root instanceof java.net.SocketException ||
			   root instanceof GisException;
	}
}

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
package gov.miamidade.cirm.rest;


import static org.sharegov.cirm.OWL.fullIri;
import static org.sharegov.cirm.OWL.getEntityLabel;
import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.objectProperty;
import static org.sharegov.cirm.rest.OperationService.getPersister;
import static org.sharegov.cirm.utils.GenUtils.containsWhiteSpace;
import static org.sharegov.cirm.utils.GenUtils.ko;
import static org.sharegov.cirm.utils.GenUtils.ok;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.getAddressPropertyValue;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.blankField;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.convertToTimestamp;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.emptyArray;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.getAllServiceAnswers;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.getLegacyCode;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.getYesterday830PM;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.formatDate;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.formatRecyclingDate;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.getCity;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.saveTextFileLocally;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.toList;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.toTilde;

import gov.miamidade.cirm.GisClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.POST;
import javax.ws.rs.Path;

import mjson.Json;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.gis.GisDAO;
import org.sharegov.cirm.legacy.MessageManager;
import org.sharegov.cirm.rdb.Query;
import org.sharegov.cirm.rdb.QueryTranslator;
import org.sharegov.cirm.rdb.RelationalStore;
import org.sharegov.cirm.rest.LegacyEmulator;

/**
 * Creates a report that contains all Blue Cart Issues requests (legacy:SWMRESVC) that were 
 * created (&status open), 
 * updated(&status open), 
 * closed 
 * or voided after either yesterday 8:30PM or the previous time machine fire time. 
 * The report file will be saved to an FTP location.
 * An email will be sent on success or failure to CIAO-CIRMTT@miamidade.gov after each report generation.
 * 
 * Previous fire time will be determined from the json data passed into POST /reports/bluecartReport.
 * 
 * @author Phani Upadrasta
 *
 */
@Path("reports")
public class ServiceRequestReports
{
	
	public static final int FTP_MAX_ON_ERROR_RETRIES = 5;
	
	private Map<String, Integer> blueCartColumnOrder = Collections.emptyMap();
	private QueryTranslator qt = null;
	private RelationalStore store = null;
	private static boolean ftp = true;
	private static boolean saveLocally = true;
	private static boolean email = true;
	private static String emailFrom ="cirm@miamidade.gov";
	private static String emailTo = "CIAO-CIRMTT@miamidade.gov";
	private static String recyclingCartsFileName = "RecyclingCarts.txt";
	
	private Map<Integer, String> addBlueCartRow(Json data, Json gisAddrData) {
		Map<Integer, String> eachRow = new HashMap<Integer, String>();
		Json atAddress = data.at("properties").at("atAddress");
		Json props = data.at("properties");
		List<Json> activities = toList(props, "hasServiceActivity");
		List<Json> customers = toList(props, "hasServiceCaseActor");
		addAddressFields(eachRow, atAddress);
		String[] fulladdr = atAddress.has("fullAddress") ? atAddress.at("fullAddress").asString().split(" ") : null;
		//TODO: 5th element is not always the direction suffix. Sometimes, the street name spans more than 1 element
		if(fulladdr != null && fulladdr.length == 5)
			eachRow.put(blueCartColumnOrder.get("hasStreetTypeSuffix"), fulladdr[4]);
		eachRow.put(blueCartColumnOrder.get("Street_Unit_Number"), 
				atAddress.has("Street_Unit_Number") ? atAddress.at("Street_Unit_Number").asString() : blankField);
		eachRow.put(blueCartColumnOrder.get("Street_Address_City"), 
				gisAddrData != null ? !gisAddrData.at("municipality").isNull() ? 
						gisAddrData.at("municipality").asString() : getCity(atAddress) : getCity(atAddress));
		eachRow.put(blueCartColumnOrder.get("Street_Address_State"), 
				getAddressPropertyValue(atAddress, "Street_Address_State", "USPS_Abbreviation"));
		eachRow.put(blueCartColumnOrder.get("Zip_Code"), 
				atAddress.has("Zip_Code") ? atAddress.at("Zip_Code").asString() : blankField);
		eachRow.put(blueCartColumnOrder.get("hasCaseNumber"), 
				props.has("hasCaseNumber") ? props.at("hasCaseNumber").asString() : blankField);
		eachRow.put(blueCartColumnOrder.get("hasDateCreated"), 
				formatRecyclingDate(props.at("hasDateCreated").asString()));
		eachRow.put(blueCartColumnOrder.get("type"), data.at("type").asString());
		eachRow.put(blueCartColumnOrder.get("recyclingRoute"),
				gisAddrData != null ? !gisAddrData.at("recyclingRoute").isNull() ? gisAddrData
						.at("recyclingRoute").asString() : blankField : blankField);
		eachRow.put(blueCartColumnOrder.get("hasDetails"),
				props.has("hasDetails") ? props.at("hasDetails").asString() : blankField);
		eachRow.put(blueCartColumnOrder.get("hasStatus"), getLegacyCode(props, "hasStatus"));
		eachRow.put(blueCartColumnOrder.get("isCreatedBy"),
				props.has("isCreatedBy") ? props.at("isCreatedBy").asString() : blankField);
		eachRow.put(blueCartColumnOrder.get("hasIntakeMethod"), getLegacyCode(props, "hasIntakeMethod"));
		eachRow.put(blueCartColumnOrder.get("hasPriority"), getLegacyCode(props, "hasPriority"));
		eachRow.put(blueCartColumnOrder.get("garbagePickupRoute"),
				gisAddrData != null ? !gisAddrData.at("garbagePickupRoute").isNull() ? 
						gisAddrData.at("garbagePickupRoute").asString() : blankField : blankField);
		eachRow.put(blueCartColumnOrder.get("districtNumber"),
				gisAddrData != null ? !gisAddrData.at("districtNumber").isNull() ? gisAddrData
						.at("districtNumber").asString() : blankField : blankField);

		boolean actorPhonesAdded = false;
		for(Json customer : customers)
		{
			OWLNamedIndividual actor = individual(customer, "hasServiceActor");
			if(blueCartColumnOrder.containsKey(actor.getIRI().getFragment()+".Phone.0"))
			{
				String[] homeNo = customer.has("HomePhoneNumber") ? 
						customer.at("HomePhoneNumber").asString().split(",") : emptyArray;
				String[] cellNo = customer.has("CellPhoneNumber") ?
						customer.at("CellPhoneNumber").asString().split(",") : emptyArray;
				String[] businessNo = customer.has("BusinessPhoneNumber") ?
						customer.at("BusinessPhoneNumber").asString().split(",") : emptyArray;
				String[] faxNo = customer.has("FaxNumber") ?
						customer.at("FaxNumber").asString().split(",") : emptyArray;
				String[] otherNo = customer.has("OtherPhoneNumber") ?
						customer.at("OtherPhoneNumber").asString().split(",") : emptyArray;
				
				List<String> contactNos = new ArrayList<String>(Arrays.asList(homeNo));
				contactNos.addAll(new ArrayList<String>(Arrays.asList(cellNo)));
				contactNos.addAll(new ArrayList<String>(Arrays.asList(businessNo)));
				contactNos.addAll(new ArrayList<String>(Arrays.asList(faxNo)));
				contactNos.addAll(new ArrayList<String>(Arrays.asList(otherNo)));

				if(contactNos.isEmpty())
				{
					contactNos.add(blankField);
					contactNos.add(blankField);
				}
				else if(contactNos.size() == 1)
					contactNos.add(blankField);
				for(int i=0; i<2; i++) {
					String contactNo = contactNos.get(i);
					eachRow.put(blueCartColumnOrder.get(actor.getIRI().getFragment()+".Phone."+i), 
							contactNo.startsWith("000") ? blankField : contactNo);
				}
				actorPhonesAdded = true;
			}
		}
		if(!actorPhonesAdded)
		{
			eachRow.put(blueCartColumnOrder.get("CITIZEN.Phone.0"), blankField);
			eachRow.put(blueCartColumnOrder.get("CITIZEN.Phone.1"), blankField);
		}
		
		eachRow.put(blueCartColumnOrder.get("hasDateLastModified"), 
				props.has("hasDateLastModified") ? 
						formatRecyclingDate(props.at("hasDateLastModified").asString()) : blankField);
		
		for(Json activity : activities)
		{
			OWLNamedIndividual act = individual(activity, "hasActivity");
			if(blueCartColumnOrder.containsKey(act.getIRI().getFragment()+".hasDetails"))
				eachRow.put(blueCartColumnOrder.get(act.getIRI().getFragment()+".hasDetails"), 
						activity.has("hasDetails") ? activity.at("hasDetails").asString() : blankField);
		}
		eachRow.put(blueCartColumnOrder.get("isModifiedBy"), 
				props.has("isModifiedBy") ? props.at("isModifiedBy").asString() : blankField);

		OWLNamedIndividual typeInd = individual("legacy:"+data.at("type").asString());
		List<Json> allServiceFields = new ArrayList<Json>();
		LegacyEmulator.getAllServiceFields(typeInd, allServiceFields, true);
		List<Json> allServiceAnswers = getAllServiceAnswers(data);
		
		for(int k = 0; k < allServiceFields.size(); k++)
		{
			Json sf = allServiceFields.get(k);
			int counter = 0;
			for(Json sa : allServiceAnswers)
			{
				OWLNamedIndividual sfInd = individual(sf.at("hasServiceField").at("iri").asString());
				OWLNamedIndividual saInd = individual(sa.at("hasServiceField").at("iri").asString());
				if(sa.at("hasServiceField").at("iri").asString().
						equals(sf.at("hasServiceField").at("iri").asString()))
				{
					if(blueCartColumnOrder.containsKey(saInd.getIRI().getFragment()))
					{
						if(sa.has("hasAnswerValue"))
						{
							StringBuilder answerValue = new StringBuilder("");
							if(sa.at("hasAnswerValue").at("literal").isArray())
							{
								List<Json> literalList = sa.at("hasAnswerValue").at("literal").asJsonList();
								for(int i=0; i<literalList.size(); i++)
								{
									answerValue.append(
											modifyAnswerValue(sfInd, literalList.get(i).asString()))
									.append(",");
								}
								answerValue.deleteCharAt(answerValue.length() - 1);
								eachRow.put(blueCartColumnOrder.get(sfInd.getIRI().getFragment()), answerValue.toString());
							}
							else
							{
								answerValue.append(modifyAnswerValue(sfInd, 
										sa.at("hasAnswerValue").at("literal").asString()));
								if(sf.at("hasDataType").asString().equals("DATE"))
									eachRow.put(blueCartColumnOrder.get(sfInd.getIRI().getFragment()), 
											formatDate(answerValue.toString(), false, true));
								if(sf.at("hasDataType").asString().equals("NUMBER"))
									eachRow.put(blueCartColumnOrder.get(sfInd.getIRI().getFragment()), 
											Long.toString(new BigDecimal(answerValue.toString()).longValue()));
								else
									eachRow.put(blueCartColumnOrder.get(sfInd.getIRI().getFragment()), answerValue.toString());
							}
						}
						else if(sa.has("hasAnswerObject"))
						{
							OWLIndividual qtn = individual(sa.at("hasServiceField").at("iri").asString());
							OWLObjectProperty cvl = objectProperty("legacy:"+"hasChoiceValueList");
							if(sa.at("hasAnswerObject").isArray())
							{
								List<Json> answerObjectList = sa.at("hasAnswerObject").asJsonList();
								StringBuilder answerObject = new StringBuilder("");
								for(int i=0; i<answerObjectList.size(); i++)
								{
									Json eachAnswerObject = answerObjectList.get(i);
									String eachAnsObj = eachAnswerObject.at("iri").asString();
									if(!OWL.collectObjectProperties(qtn, cvl).isEmpty() && 
											!containsWhiteSpace(eachAnsObj))
									{
										answerObject.append(getEntityLabel(individual(eachAnsObj))).append(",");
									}
								}
								answerObject.deleteCharAt(answerObject.length() - 1);
								eachRow.put(blueCartColumnOrder.get(sfInd.getIRI().getFragment()), answerObject.toString());
							}
							else 
							{
								String ans = sa.at("hasAnswerObject").at("iri").asString();
								if(!OWL.collectObjectProperties(qtn, cvl).isEmpty() && 
										!containsWhiteSpace(ans))
								{	
									eachRow.put(blueCartColumnOrder.get(sfInd.getIRI().getFragment()), 
											getEntityLabel(individual(ans)));
								}
								else
								{
									eachRow.put(blueCartColumnOrder.get(sfInd.getIRI().getFragment()), ans);
								}
							}
						}
						++counter;
					}
					else
						continue;
				}
				if(counter == 0)
					eachRow.put(blueCartColumnOrder.get(sfInd.getIRI().getFragment()), blankField);
			}
		}
		
		if(eachRow.containsKey(null))
			eachRow.remove(null);
		return eachRow;
	}

	private String modifyAnswerValue(OWLNamedIndividual ind, String value)
	{
		if(ind.getIRI().getFragment().equals("SWMRESVC_RCIQ1A"))
		{
			//SWMRESVC_RCIQ1A : hold the folio number as the answer value.
			//North Miami Beach folio's start with a zero 
			//and as its an integer the leading zero is discarded during the save in db 
			value = value.length() == 12 ? "0"+value : value;
			return value;
		}
		else
			return value;
	}	
	
	private void addAddressFields(Map<Integer, String> eachRow, Json atAddress)
	{
		eachRow.put(blueCartColumnOrder.get("Street_Number"), 
				atAddress.has("Street_Number") ? atAddress.at("Street_Number").asString() : blankField);
		eachRow.put(blueCartColumnOrder.get("Street_Name"), 
				atAddress.has("Street_Name") ? atAddress.at("Street_Name").asString() : blankField);
		eachRow.put(blueCartColumnOrder.get("Street_Direction"), 
				getAddressPropertyValue(atAddress, "Street_Direction", "USPS_Abbreviation"));
		eachRow.put(blueCartColumnOrder.get("hasStreetType"), 
				getAddressPropertyValue(atAddress, "hasStreetType", "USPS_Suffix"));
		// We dont store street suffix in a seperate column only in fullAddress.
		eachRow.put(blueCartColumnOrder.get("hasStreetTypeSuffix"), blankField);
	}

	private void setBlueCartColumnOrder() {
		if(blueCartColumnOrder.isEmpty())
		{
			blueCartColumnOrder = new HashMap<String, Integer>();
			blueCartColumnOrder.put("SWMRESVC_RCIQ1A", 0);
			blueCartColumnOrder.put("Street_Number", 1);
			blueCartColumnOrder.put("Street_Direction", 2);
			blueCartColumnOrder.put("Street_Name", 3);
			blueCartColumnOrder.put("hasStreetType", 4);
			blueCartColumnOrder.put("hasStreetTypeSuffix", 5);
			blueCartColumnOrder.put("Street_Unit_Number", 6);
			blueCartColumnOrder.put("Street_Address_City", 7);
			blueCartColumnOrder.put("Street_Address_State", 8);
			blueCartColumnOrder.put("Zip_Code", 9);
			blueCartColumnOrder.put("hasCaseNumber", 10);
			blueCartColumnOrder.put("hasDateCreated", 11);
			blueCartColumnOrder.put("type", 12);
			blueCartColumnOrder.put("recyclingRoute", 13);
			blueCartColumnOrder.put("hasDetails", 14);
			blueCartColumnOrder.put("hasStatus", 15);
			blueCartColumnOrder.put("isCreatedBy", 16);
			blueCartColumnOrder.put("hasIntakeMethod", 17);
			blueCartColumnOrder.put("hasPriority", 18);
			blueCartColumnOrder.put("garbagePickupRoute", 19);
			blueCartColumnOrder.put("districtNumber", 20);
			blueCartColumnOrder.put("SWMRESVC_RICQ7", 21);
			blueCartColumnOrder.put("SWMRESVC_RICQ6", 22);
			blueCartColumnOrder.put("SWMRESVC_RICQ5", 23);
			blueCartColumnOrder.put("SWMRESVC_RCPROP", 24);
			blueCartColumnOrder.put("SWMRESVC_SELECTTY", 25);
			blueCartColumnOrder.put("SWMRESVC_RCIQ2", 26); 
			blueCartColumnOrder.put("SWMRESVC_IF1212CI", 27);
			blueCartColumnOrder.put("SWMRESVC_IF1214CA", 28);
			blueCartColumnOrder.put("SWMRESVC_RCIQ4", 29);
			blueCartColumnOrder.put("SWMRESVC_IFYOULIV", 30);
			blueCartColumnOrder.put("CITIZEN.Phone.0", 31);
			blueCartColumnOrder.put("CITIZEN.Phone.1", 32);
			blueCartColumnOrder.put("hasDateLastModified", 33);
			blueCartColumnOrder.put("SWMRESVC_SWRECA1.hasDetails", 34);
			blueCartColumnOrder.put("isModifiedBy", 35);
			blueCartColumnOrder.put("SWMRESVC_RCIQ21", 36);
			blueCartColumnOrder.put("SWMRESVC_RCIQ22", 37);
			//Is there account NO ? 
			//blueCartColumnOrder.put("ACCOUNT_NO", 38);
		}
	}
	
	private Json getGisAddress(Json data)
	{
		Json result = null;
		if(data.at("properties").has("hasXCoordinate") 
				&& data.at("properties").has("hasYCoordinate"))
		{
			Json address = GisClient.getAddressFromCoordinates(
				data.at("properties").at("hasXCoordinate").asDouble(),
				data.at("properties").at("hasYCoordinate").asDouble());
			if(!address.isNull())
				result = address;
		}
		if(result == null || (data.at("properties").has("atAddress") && 
				data.at("properties").at("atAddress").has("fullAddress")))
		{
			Json candidates =  GisClient.findCandidates(
				data.at("properties").at("atAddress").at("fullAddress").asString(), 
				data.at("properties").at("atAddress").has("Zip_Code") ? 
					data.at("properties").at("atAddress").at("Zip_Code").asString() : null,
			null);
			if(candidates.asJsonList().size() == 1)
				result = candidates.at(0);
		}
		return result;
	}

//	private Json getGisAddrDataFromServiceLayers(Json data)
//	{
//		//Fix : Addresses with missing GIS Info are not returning 
//		//the complete GIS data on hitting the ServiceLayers.
//		//So, hitting the candidates first and then the ServiceLayers, 
//		//so that the ServiceLayers returns the full data.
//		if(data.at("properties").at("atAddress").has("fullAddress"))
//		{
//			GisClient.findCandidates(
//				data.at("properties").at("atAddress").at("fullAddress").asString(), 
//				data.at("properties").at("atAddress").has("Zip_Code") ? 
//					data.at("properties").at("atAddress").at("Zip_Code").asString() : null,
//				null);
//		}
//		Json serviceLayersInfo = null;
//		if(data.at("properties").has("hasXCoordinate") 
//				&& data.at("properties").has("hasYCoordinate"))
//		{
//			serviceLayersInfo = Refs.gisClient.resolve().getLocationInfo(
//				data.at("properties").at("hasXCoordinate").asDouble(), 
//				data.at("properties").at("hasYCoordinate").asDouble(), 
//				null);
//		}
//		if(serviceLayersInfo != null)
//			return serviceLayersInfo.has("address") ? serviceLayersInfo.at("address") : null;
//		else
//			return serviceLayersInfo;
//	}

	private Json getGisAddrData(Json data)
	{
		if(data.at("properties").has("hasGisDataId"))
		{
			Json layers = GisDAO.getGisData(
					data.at("properties").at("hasGisDataId").asString());
			if(layers != null && !layers.isNull() && layers.has("address")
					&& layers.at("address").has("recyclingRoute")
						&& !layers.at("address").at("recyclingRoute").isNull())
				return layers.at("address");
			else
				return null;
		}
		else
			return null;
	}

	private String addBlueCartContent(Set<Long> boids, Set<Long> missingGisCases) {
    	try {
    		LegacyEmulator le = new LegacyEmulator();
        	List<Map<Integer, String>> allRows = new ArrayList<Map<Integer, String>>();
    		setBlueCartColumnOrder();
    		if(blueCartColumnOrder.isEmpty())
    			throw new RuntimeException("RecyclingBlueCartIssues Column order Map empty. Cannot create the daily report.");
        	for(Long boid : boids)
        	{
    			Json data = le.findServiceCaseOntology(boid).toJSON();
    			Json gisAddrData = getGisAddrData(data);
   				if(gisAddrData == null)
   				{
   					gisAddrData = getGisAddress(data);
   					missingGisCases.add(boid);
   				}
				allRows.add(addBlueCartRow(data, gisAddrData));
        	}
        	//String result = toCSV(allRows);
        	String result = toTilde(allRows);
        	return result;
    	}
    	catch(Exception e)
    	{
    		e.printStackTrace();
    		throw new RuntimeException(e);
    	}
	}
	
	@POST
	@Path("bluecartReportManual")
	public Json recyclingBlueCartIssuesReportManual() {
		Json data = Json.object().set("schedule", 
				Json.object().set("scheduleData", 
						Json.object().set("previousFireTime", Json.nil()))
		);
		return recyclingBlueCartIssuesReport(data);
	}
	
	@POST
	@Path("bluecartReport")
	public Json recyclingBlueCartIssuesReport(Json data) {
    	StringBuilder emailBody = new StringBuilder();
		try {
			Set<Long> boids = getBlueCartServiceRequests(data);
	    	Set<Long> missingGisCases = new HashSet<Long>();
			System.out.println("Total boids : "+boids.size() + " : They are : "+boids);
			StringBuilder result = new StringBuilder("");
    		if(boids.size() > 0)
    			result.append(addBlueCartContent(boids, missingGisCases));
			else {
				System.out.println("No Recycling Blue Cart Issues Service Requests present for given search criteria.");
				System.out.println("data passed into the recyclingBlueCartIssuesReport method : " + data.toString());
			}
			if(ftp)
				sendReportToFTPWithOnErrorRetry(result.toString());
			if(saveLocally)
				saveTextFileLocally(result.toString());
			System.out.println("Successfully created the Recycling Blue Cart Issues report.");
	    	if(missingGisCases.size() > 0)
	    	{
	    		System.out.println("*****Cases which needed a GIS ServiceLayers lookup : " 
	    				+missingGisCases.size() + " : They are : "+missingGisCases);
	    	}
		    if(email)
		    {
	    		String emailSubject = "SUCCESS : Recycling BlueCart Report Generation SUCCESSFUL";
		    	emailBody.append("Successfully generated the Recyling Blue Cart Report.").append("<br><br>");
		    	if(missingGisCases.size() > 0)
		    	{
		    		emailBody.append("The cases with missing GIS info and needed a lookup are :")
		    				.append("<br>")
		    				.append(missingGisCases);
		    	}
		    	MessageManager.get().sendEmail(emailFrom, emailTo, emailSubject, emailBody.toString());
		    }
			return ok();
		}
		catch(Exception e)
		{
			System.out.println("******Unable to create Recycling Blue Cart Issues report ******");
			System.out.println("data passed into the recyclingBlueCartIssuesReport method : " + data.toString());
			e.printStackTrace();
	    	if(email)
	    	{
				String emailSubject = "FAIL : Recycling BlueCart Report Generation FAILED";
		    	emailBody.append("Failed to generate the Recyling BlueCart Report.")
		    			.append("<br><br>")
		    			.append("Error message:")
		    			.append("<br>")
		    			.append(e.getMessage());
		    	MessageManager.get().sendEmail(emailFrom, emailTo, emailSubject, emailBody.toString());
	    	}
		    return ko(e.getMessage());
		}
	}

	/**
	 * FTPs the report to the PROD/TEST location with up to FTP_MAX_ON_ERROR_RETRIES retries on any error.
	 * 
	 * @param fileStr
	 */
	private void sendReportToFTPWithOnErrorRetry(String reportContent) {
		int attemptCount = 0;
		boolean success;
		do {
			try {
				attemptCount++;
				sendReportToFTP(reportContent);
				success = true;
			} catch (Exception e) {
				success = false;
				System.err.println("Blue Cart Report failed to send to FTP on " + attemptCount + ". of "
						+ FTP_MAX_ON_ERROR_RETRIES + "attempts."
						+ "Error was: " + e);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ie) 
				{ /* do nothing */ }
			}
		} while (!success && attemptCount <= FTP_MAX_ON_ERROR_RETRIES); 
	}

	/**
	 * FTPs the report to the PROD/TEST location
	 * 
	 * @param reportContent the full report which will be used as file content.
	 */
	private void sendReportToFTP(String reportContent)
	{
		OWLNamedIndividual ftpConfig = Refs.configSet.resolve().get("FTPServerConfig");
		OWLLiteral hasUserName = OWL.dataProperty(ftpConfig, "hasUsername");
		OWLLiteral hasPassword = OWL.dataProperty(ftpConfig, "hasPassword");
		OWLLiteral hasPath = OWL.dataProperty(ftpConfig, "hasPath");
		OWLLiteral hasURL = OWL.dataProperty(ftpConfig, "hasUrl");

		if(hasURL == null)
			throw new RuntimeException("No hasURL defined for FTPServerConfig in the Ontology");
		String username = (hasUserName == null) ? "" : hasUserName.getLiteral();
		String password = (hasPassword == null) ? "" : hasPassword.getLiteral();
		
		FTPClient client = new FTPClient();
		try {
			client.connect(hasURL.getLiteral()); 
			boolean login = client.login(username, password);
			if(!login)
				throw new RuntimeException("Unable to connect to FTP Server with the provided Username/Password credentials");

			if(hasPath != null)
			{
				String destinationDir = hasPath == null ? "" : hasPath.getLiteral();
				boolean workingDir = client.changeWorkingDirectory(destinationDir);
				if(!workingDir)
					throw new RuntimeException("Unable to change the working directory to "
							+ destinationDir + " on FTP Server");
			}
			FTPFile[] listFiles = client.listFiles(recyclingCartsFileName);
			for(FTPFile file : listFiles)
			{
				if(file.getName().equalsIgnoreCase(recyclingCartsFileName))
				{
					boolean isFileRenamed = client.rename(recyclingCartsFileName, 
							recyclingCartsFileName+"_"+formatDate(new Date()));
					if(!isFileRenamed)
						throw new RuntimeException("Unable to rename the existing file on FTP Server");
				}
			}

			boolean storeFile = client.storeFile(recyclingCartsFileName, 
					new ByteArrayInputStream(reportContent.getBytes()));
			if(!storeFile)
				throw new RuntimeException("Unable to store the file on FTP Server");
			boolean logout = client.logout();
			if(!logout)
				throw new RuntimeException("Unable to logout from FTP Server");
		}
		catch(IOException e)
		{
			throw new RuntimeException(e);
		}
		catch(Exception e)
		{
			throw new RuntimeException(e);
		}
		finally
		{
			try {
				client.disconnect();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
    private Json commonSearchCriteria()
    {
    	Json pattern = Json.object();
    	pattern.set("caseSensitive", false);
    	pattern.set("currentPage", 1);
    	pattern.set("itemsPerPage", 1000);
    	pattern.set("sortBy","boid");
    	pattern.set("sortDirection","desc");
    	return pattern;
    }
    
    /**
     * Takes the json coming in from TimeMachine as a parameter 
     * and extracts the previous run time from it.
     * Returns a Set of Service Request IDs which satisfy the 
     * given search criteria
     * @param data
     * @return     */
    private Set<Long> getBlueCartServiceRequests(Json data) {
		StringBuilder lastRunDate = new StringBuilder(">=");
		Json previousFireTime = (data.isNull()) ? null : 
				data.at("schedule").at("scheduleData").at("previousFireTime");
		if(previousFireTime != null && !previousFireTime.isNull())
			lastRunDate.append(convertToTimestamp(previousFireTime.asString()));
		else
			lastRunDate.append(getYesterday830PM());
		return getBlueCartServiceRequests(lastRunDate.toString());
//		return getBlueCartServiceRequests();
    }
    
    private Set<Long> getBlueCartServiceRequests(String lastRunDate)
    {
		if (qt == null) 
			qt = new QueryTranslator();
		if (store == null)
			store = getPersister().getStore();
		Set<Long> boids = new HashSet<Long>();
    	boids.addAll(createdAfterLastRunDate(lastRunDate.toString()));
    	boids.addAll(updatedAfterLastRunDate(lastRunDate.toString()));
    	boids.addAll(voidAfterLastRunDate(lastRunDate.toString()));
    	boids.addAll(closedAfterLastRunDate(lastRunDate.toString()));
    	return boids;
    }
    
    //Used for fetching specific Service Requests from a file or manually add them to the Set.
//    private Set<Long> getBlueCartServiceRequests()
//    {
//		if (qt == null) 
//			qt = new QueryTranslator();
//		if (store == null)
//			store = getPersister().getStore();
//		Set<Long> boids = new HashSet<Long>();
////		boids.add(new Long(9495625));
//		boids.addAll(ServiceRequestReportUtil.getSRIDListFromFile());
//    	return boids;
//    }

    private Set<Long> createdAfterLastRunDate(String lastRunDate)
    {
		Json pattern = commonSearchCriteria();
    	pattern.set("type", "legacy:SWMRESVC");
		pattern.set("hasDateCreated", lastRunDate);
    	pattern.set("legacy:hasStatus", 
    			Json.object().set("iri", fullIri("O-OPEN").toString()));
		Query q = qt.translate(pattern, store);
		Set<Long> boids = store.query(q, 
				Refs.tempOntoManager.resolve().getOWLDataFactory());
		System.out.println("Total createdAfterLastRunDate : "+boids.size() + " : They are : "+boids);
		return boids;
    }
    
    private Set<Long> updatedAfterLastRunDate(String lastRunDate)
    {
		Json pattern = commonSearchCriteria();
    	pattern.set("type", "legacy:SWMRESVC");
		pattern.set("hasDateLastModified", lastRunDate);
    	pattern.set("legacy:hasStatus", 
    			Json.object().set("iri", fullIri("O-OPEN").toString()));
		Query q = qt.translate(pattern, store);
		Set<Long> boids = store.query(q, 
				Refs.tempOntoManager.resolve().getOWLDataFactory());
		System.out.println("Total updatedAfterLastRunDate : "+boids.size() + " : They are : "+boids);
		return boids;
    }

    private Set<Long> voidAfterLastRunDate(String lastRunDate)
    {
		Json pattern = commonSearchCriteria();
    	pattern.set("type", "legacy:SWMRESVC");
		pattern.set("hasDateCreated", lastRunDate);
    	pattern.set("legacy:hasStatus", 
    			Json.object().set("iri", fullIri("C-VOID").toString()));
		Query q = qt.translate(pattern, store);
		Set<Long> boids = store.query(q, 
				Refs.tempOntoManager.resolve().getOWLDataFactory());
		System.out.println("Total voidAfterLastRunDate : "+boids.size() + " : They are : "+boids);
		return boids;
    }

    private Set<Long> closedAfterLastRunDate(String lastRunDate)
    {
		Json pattern = commonSearchCriteria();
    	pattern.set("type", "legacy:SWMRESVC");
		pattern.set("hasDateLastModified", lastRunDate);
    	pattern.set("legacy:hasStatus", 
    			Json.object().set("iri", fullIri("C-CLOSED").toString()));
    	pattern.set("legacy:hasServiceActivity", Json.object()
				.set("type","legacy:ServiceActivity")
				.set("legacy:hasActivity", Json.object()
					.set("iri", fullIri("legacy:SWMRESVC_SWRECA1").toString()))
				.set("legacy:hasOutcome", Json.object()
					.set("iri", fullIri("legacy:OUTCOME_COMPLCLS").toString())));

		Query q = qt.translate(pattern, store);
		Set<Long> boids = store.query(q, 
				Refs.tempOntoManager.resolve().getOWLDataFactory());
		System.out.println("Total closedAfterLastRunDate : "+boids.size() + " : They are : "+boids);
		return boids;
    }

	public static void main(String args[]) {
		try {
			ServiceRequestReports bci = new ServiceRequestReports();
			bci.recyclingBlueCartIssuesReportManual();
//			Json data = Json.object().set("schedule", Json.object()
//					.set("scheduleData", Json.object()
//							.set("previousFireTime", Json.nil())));
//			Json taskSpec = Json.object().set("group", "CIAO").set("name", "phani");
//			RESTClient.post("http://10.9.25.131:9192/timemachine-0.1/testtask", taskSpec);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

}

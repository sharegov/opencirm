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


import static org.sharegov.cirm.OWL.and;
import static org.sharegov.cirm.OWL.fullIri;
import static org.sharegov.cirm.OWL.has;
import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.objectProperty;
import static org.sharegov.cirm.OWL.owlClass;
import static org.sharegov.cirm.OWL.reasoner;

import gov.miamidade.cirm.AddressSearchService;
import gov.miamidade.cirm.GisClient;
import gov.miamidade.cirm.MDRefs;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueReceiver;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;

import mjson.Json;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.RequestScopeFilter;
import org.sharegov.cirm.legacy.CirmMessage;
import org.sharegov.cirm.rest.LegacyEmulator;
import org.sharegov.cirm.rest.RestService;
import org.sharegov.cirm.stats.CirmStatisticsFactory;
import org.sharegov.cirm.stats.SRCirmStatsDataReporter;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.SendEmailOnTxSuccessListener;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import gov.miamidade.cirm.other.JMSClient;
import gov.miamidade.cirm.other.LegacyMessageValidator.MessageValidationResult;

import org.sharegov.cirm.utils.TraceUtils;


/**
 * <p>
 * Listens to JMS events coming from mdceai with cirmservices as the destination. Because
 * there might be potentially other, non-ServiceDirect clients, attention is taken to consider
 * only event destined to us.
 * </p>
 * 
 * <p>
 * This listener should be started as a background thread upon server startup. 
 * </p>
 * 
 * @author Borislav Iordanov, Thomas Hilpold
 *
 */
public class LegacyJMSListener extends Thread
{	

	public static final String JMS_CLIENT_ID = "311HUB_JMS_LEGACYCLIENT";
	
	private SRCirmStatsDataReporter srStatsReporter = 
			CirmStatisticsFactory.createServiceRequestStatsReporter(MDRefs.mdStats.resolve(), "LegacyJMSListener"); 

	private Json config = null;
	private JMSConfig jmsConfig;
	
	
//	private volatile boolean trace;
//	private long timeout;
	
	private Throwable fatality = null; // the fatal exception that cause the thread to exit.	
	private volatile boolean quit = false;		
	private PrintStream out = System.out;	
	// JMS connection stuff
	private QueueConnectionFactory factory = null;
	private QueueConnection connection = null;
	private QueueSession session = null;
	private Queue queue = null;
	private QueueReceiver receiver = null;
	
	public LegacyJMSListener(Json config, PrintStream out)
	{
		this.config = config;
		this.out = out;
	}
	
	static SimpleDateFormat logdateformat = new SimpleDateFormat("yy/MM/dd HH:mm:ss");
	
	private String logdate()
	{
		return "[" + logdateformat.format(new java.util.Date()) + "] - "; 
	}
	
	private void trace(String msg)
	{
		if (config.is("trace", true))
		{
			out.println(logdate() + "LegacyJMSListener: " + msg);
		}
	}
	
	private void error(String msg)
	{
		out.println(logdate() + "LegacyJMSListener: " + msg);		
	}
	
	private void error(String msg, Throwable t)
	{
		if (msg != null)
		{
			out.print(logdate() + "LegacyJMSListener: " + msg);
			if (t != null)
				out.println(", stack trace follows...");
			else 
				out.println();
		}
		if (t !=  null)
			t.printStackTrace(out);
	}
	
	private boolean init()
	{
		trace("Initializing JMS Connector with configuration: " );
		try
		{
			try
			{
				if (jmsConfig == null) {
					jmsConfig = JMSClient.createJMSConfigFromOntology();
					ThreadLocalStopwatch.now("LegacyJMSListener initialized with config " + jmsConfig);
				}
				if (factory == null)
					factory = (QueueConnectionFactory)Class.forName(jmsConfig.getFactoryClassName()).newInstance();
				// Dependency on MQ Series API here... this avoids us the need to configure a JNDI resource.
				// Also, the County's messaging system has been standardized to MQ Series so there is very
				// little chance of a JMS implementation change in the future.
				com.ibm.mq.jms.MQQueueConnectionFactory mqFactory  = (com.ibm.mq.jms.MQQueueConnectionFactory)factory;
				mqFactory.setTransportType(com.ibm.mq.jms.JMSC.MQJMS_TP_CLIENT_MQ_TCPIP);
				mqFactory.setQueueManager(jmsConfig.getQueueManager());
				mqFactory.setHostName(jmsConfig.getHostName());
				mqFactory.setPort(jmsConfig.getPort());
				mqFactory.setClientId(JMS_CLIENT_ID);
				//2015.06.01 set channel was always missing
				mqFactory.setChannel(jmsConfig.getChannel());
				if(jmsConfig.isAuthenticate()) {
					connection = factory.createQueueConnection(jmsConfig.getUser(),jmsConfig.getPwd());
				} else {
					connection = factory.createQueueConnection();
				}
				connection.start();
				session = connection.createQueueSession(false, Session.CLIENT_ACKNOWLEDGE);
				queue = session.createQueue(jmsConfig.getInQueueName());
				receiver = session.createReceiver(queue);
				trace("JMS Connector initialized successfully.");
				return true;
			}
			catch (Throwable t)
			{
				error("Failed to initialize JMS connection.", t);
				return false;
			}
		}
		catch (Throwable t)
		{
			error("Failed to initialize JMS connection.", t);
			return false;
		}
	}

	public void cleanup()
	{
		trace("Cleanup JMS Connector.");
		try { if (receiver != null) receiver.close(); }
		catch (Throwable t) { t.printStackTrace(out); }
		finally { receiver = null; }
		
		try { if (session != null) session.close(); }
		catch (Throwable t) { t.printStackTrace(out); }
		finally { session = null; }
		
		try { if (connection != null) connection.close(); }
		catch (Throwable t) { t.printStackTrace(out); }
		finally { connection = null; }	
	}
	
    protected void finalize()
    {
    	cleanup();
    }
    
    /**
     * Recursively time stamps each Json Object by applying a hasDateCreated property with value NOW,
     * only if such object does not yet have a hasDateCreated Property.
     * 
     * @param anyJson any Json or null.
     */
    public void timeStamp(Json anyJson) 
    {
    	timeStamp(anyJson, new Date());
    }
    
    /**
     * Recursively time stamps each Json Object by applying a hasDateCreated property with value timeStampDate,
     * only if such object does not yet have a hasDateCreated Property.<br>
     * Only Objects passed in directly or found in recursive Arrays are time stamped.<br>
     * Objects or arrays found as value of properties are not time stamped or traversed.<br>
     * <br>
     * Typically used for activities received from departmental interfaces.<br>
     * <br>
     * @param anyJson any Json or null;
     * @param timeStampDate the date to be used for time stamping Json Objects
     */
    public void timeStamp(Json anyJson, Date timeStampDate)
    {
    	if (timeStampDate == null) {
    		throw new IllegalArgumentException("timeStampDate was null error, timestamp json: " + anyJson);
    	}
    	if (anyJson == null || anyJson.isPrimitive()) {
    		return;
    	} else if (anyJson.isObject() && !anyJson.has("hasDateCreated")) {
    		anyJson.set("hasDateCreated",  GenUtils.formatDate(timeStampDate));
    	} else if (anyJson.isArray()) {
    		for (Json x : anyJson.asJsonList()) {
    			timeStamp(x, timeStampDate);
    		}
    	}
    }
    	
	private void gisToAtAddress(Json gis, Json addr)
	{
		Json newAddr = ServiceCaseJsonHelper.makeCirmAddress(gis, false);
		if (newAddr == null || newAddr.isNull()) {
			return;
		}
		addr.with(newAddr);
		
//		if (!gis.has("parsedAddress"))
//			return;
//		Json parsed = gis.at("parsedAddress");		
//		Set<OWLNamedIndividual> S = OWL.queryIndividuals("Place and (Name value \"" + 
//				gis.at("municipality").asString() +
//				"\" or Alias value \"" + gis.at("municipality").asString() + "\")");
//		if (S.isEmpty())
//			throw new IllegalArgumentException("Cannot find municipality in ontology " + gis.at("municipality"));
//		
//		String streetAddress = gis.at("address").asString().split(",")[0];
//		addr.set("Street_Number", parsed.at("House"))
//			.set("Zip_Code", parsed.at("zip"))
//			.set("Street_Address_State", 
//				  Json.object("iri", "http://www.miamidade.gov/ontology#Florida"))
//			.set("Street_Address_City", Json.object("iri", S.iterator().next().getIRI().toString()))
//			.set("Street_Name", parsed.at("StreetName"))
//			.set("fullAddress", streetAddress);
//		if (parsed.has("PreDir") && !parsed.is("PreDir", ""))
//			addr.set("Street_Direction", Json.object("USPS_Abbreviation", parsed.at("PreDir").asString()));
//		if (parsed.has("SufType") && !parsed.is("SufType", ""))
//			addr.set("hasStreetType", Json.object("USPS_Suffix", parsed.at("SufType").asString()));		
	}
	
	/**
	 * Uses GisClient to find an address from XY and sets it to the scase.
	 * @param scase
	 * @param xcoord number
	 * @param ycoord number
	 */
	public void updateAddressFromXY(Json scase, Json xcoord, Json ycoord)
	{
		Json gis = Json.object();
		try
		{
			gis = GisClient.getAddressFromCoordinates(xcoord.asDouble(), ycoord.asDouble(), 3, 1000*30); // 
		}
		catch (RuntimeException ex)
		{
			error("During GIS XY lookup for " + xcoord + ", " + ycoord, ex);
			srStatsReporter.failed("updateAddressFromXY", scase,"" + GenUtils.getRootCause(ex), "" + GenUtils.getRootCause(ex).getMessage());
		}
		if (gis.isArray())
			gis = gis.at(0);
		else if (gis.isNull() || gis.asJsonMap().isEmpty())
		{
			srStatsReporter.failed("updateAddressFromXY", scase,"GisClient returned null", "");
			return;
		}
		Json addr =  scase.at("atAddress", Json.object());
		gisToAtAddress(gis, addr);
		scase.set("hasXCoordinate", xcoord);
		scase.set("hasYCoordinate", ycoord);
//		scase.set("hasGisDataId", GisClient.getGisDBId(scase.at("hasXCoordinate").asDouble(), 
//				   scase.at("hasYCoordinate").asDouble()));		
		srStatsReporter.succeeded("updateAddressFromXY", scase);
	}		
	
	/**
	 * Update the 'atAddress' of 'scase' with 'newAddress'. First make sure 'newAddress' is geo-located
	 * with a unique match. Also update the x,y coordinates of the case.
	 */
	public void updateAddress(Json scase, Json newAddress)
	{
		String streetAddress = newAddress.at("Street_Number").asString() + " " + 
					(newAddress.has("Street_Direction") ? 
					  newAddress.at("Street_Direction").at("USPS_Abbreviation").asString():"") + 
					" " + newAddress.at("Street_Name").asString() + " " + 
					(newAddress.has("hasStreetType") ? newAddress.at("hasStreetType").at("USPS_Suffix").asString() : "");
		Json candidates = GisClient.findCandidates(streetAddress, 
												   newAddress.has("Zip_Code") ? newAddress.at("Zip_Code").asString() : "",
												   newAddress.has("Street_Address_City") ?
												     newAddress.at("Street_Address_City").at("Name").asString() :
													 null);
		if (candidates.asJsonList().isEmpty())
			throw new IllegalArgumentException("Unable to GEO-locate address " + newAddress);
		else if (candidates.asJsonList().size() == 1)
			gisToAtAddress(candidates.at(0), scase.at("atAddress"));
		else
			throw new IllegalArgumentException("Ambiguous address (more than 1 matches) " + newAddress);
		scase.set("hasXCoordinate", candidates.at(0).at("location").at("x"));
		scase.set("hasYCoordinate", candidates.at(0).at("location").at("y"));
//		scase.set("hasGisDataId", GisClient.getGisDBId(scase.at("hasXCoordinate").asDouble(), 
//				  			 						   scase.at("hasYCoordinate").asDouble()));		
	}
	
	public void validateAddresses(Json top)
	{
		if (top.isArray())
			for (Json x : top.asJsonList())
				validateAddresses(x);
		else if (top.isObject())
		{
			if (top.has("atAddress"))
			{
				Json a = top.at("atAddress");
				a.set("type", "Street_Address");
				if (a.has("Street_Address_City") && a.at("Street_Address_City").is("Name", ""))
					a.delAt("Street_Address_City");
				if (!a.has("Street_Address_City"))
				{
					String street = a.has("Street_Number") ? a.at("Street_Number").asString() : "";
					if (a.has("Street_Direction"))
					{
						if (a.at("Street_Direction").has("USPS_Abbreviation"))
							street += " " + a.at("Street_Direction").at("USPS_Abbreviation").asString();
						else
							a.delAt("Street_Direction");
					}						
					if (a.has("Street_Name"))
						street += " " + a.at("Street_Name").asString();
					if (a.has("hasStreetType"))
					{
						// Sometimes we get garbage in the USPS_Suffix fields from departments (particularly PWS)
						// the 'assignIris' method will delete the USPS_Suffix property if it detects it's garbage
						// and here we delete the whole hasStreetType so the DB layer doesn't choke looking up
						// the IRI in the ontology. Same goes for Street_Direction above: both those properties
						// should be standard values.
						if (a.at("hasStreetType").has("USPS_Suffix"))
							street += " " + a.at("hasStreetType").at("USPS_Suffix").asString();
						else 
							a.delAt("hasStreetType");
					}
					if (a.has("Street_Unit_Number"))
						street += " " + a.at("Street_Unit_Number").asString();
					String zip = "";
					if (a.has("Zip_Code"))
						zip = a.at("Zip_Code").asString();
					Json propinfo = new AddressSearchService().findPropertyInfo(street, zip);
					if (propinfo != null)
						a.set("Street_Address_City", 
								Json.object("iri", AddressSearchService.citiesToAgency.get(propinfo.at("municipality").asString()).toString()));
				}
			}
			for (Map.Entry<String, Json> e : top.asJsonMap().entrySet())
			{
				validateAddresses(e.getValue());
			}
		}
	}

	private Json newCaseTxn(LegacyEmulator emulator, Json jmsg) throws JMSException
	{
		Json data = Json.object("properties", jmsg.at("data").dup());
		Json props = data.at("properties");
		data.set("type", props.atDel("type"));
		//2016.02.19 hilpold all interface messages changed to never have empty hasDetails (mdcirm 2547)
		if (!props.has("hasDetails")) 
		{
			props.set("hasDetails", "");
		}
		timeStamp(props.at("hasServiceCaseActor"));
		timeStamp(props.at("hasServiceActivity"));
		try
		{
			if (props.has("hasXCoordinate"))
				this.updateAddressFromXY(props, 
										 props.at("hasXCoordinate"), 
										 props.at("hasYCoordinate"));
			else if (props.has("atAddress"))
				this.updateAddress(props, props.at("atAddress"));				
		}
		catch (Throwable t)
		{
			// maybe spit out a warning...
		}
		ServiceCaseJsonHelper.insertIriFromCode(props.at("hasServiceCaseActor"), "hasServiceActor", "");
		ServiceCaseJsonHelper.insertIriFromCode(props.at("hasServiceAnswer"), "hasServiceField", data.at("type").asString() + "_");
		ServiceCaseJsonHelper.insertIriFromCode(props.at("hasServiceActivity"), "hasActivity", data.at("type").asString() + "_");
		ServiceCaseJsonHelper.insertIriFromCode(props.at("hasServiceActivity"), "hasOutcome", "OUTCOME_");				
		ServiceCaseJsonHelper.insertIriFromCode(props.at("hasStatus"), "");
		props.delAt("updateDate"); // MDCEAI wrongly sends an updateDate even on new SRs (that's likely CMS's fault that started sending those)
		ServiceCaseJsonHelper.cleanUpProperties(data);
		ServiceCaseJsonHelper.assignIris(data);
		ServiceCaseJsonHelper.replaceAnswerLabelsWithValues(props.at("legacy:hasServiceAnswer"));

		// remove properties that should be ignore or have been taken care of above already
		
		validateAddresses(data); // we still need to go through actors' address etc.
		
		// Insert CiRM required default values for missing properties:
		if (!props.has("hasPriority"))
			props.set("legacy:hasPriority", 
					Json.object("iri", OWL.fullIri("legacy:STANDARD").toString()));
		if (!props.has("hasIntakeMethod"))
			props.set("legacy:hasIntakeMethod", 
					Json.object("iri", OWL.fullIri("legacy:XTERFACE").toString()));
					
		trace("Inserting new case with DATA:" + data.toString() + "\n\n");
		
		Json result = emulator.saveNewCaseTransaction(data);
		
		if (result.is("ok", true))
		{
			Set<OWLNamedIndividual> interfaces = 
				    reasoner().getInstances(
						and(owlClass("legacy:LegacyInterface"), 
						    has(objectProperty("legacy:hasAllowableEvent"), individual("legacy:NEWSR")),
						    has(objectProperty("legacy:isLegacyInterface"), individual(data.at("type").asString()))), false).getFlattened();
			
			if (!interfaces.isEmpty())
			{
				OWLNamedIndividual LI = interfaces.iterator().next();					
				result.at("data").set("hasLegacyInterface", Json.object("hasLegacyCode", 
											OWL.dataProperty(LI, "legacy:hasLegacyCode").getLiteral()));				
			}
			srStatsReporter.succeeded("newCaseTxn", result);
		} else
		{
			srStatsReporter.failed("newCaseTxn", result, "saveNewCaseTransaction ko", "" + result.at("error"));
		}
		JMSClient.connectAndRespond(jmsg, result);	
		return result;
	}
	/**
	 * A new activity was received by Cirm from an interface, is added to the existing sr and updated in the db.
	 * @param emulator
	 * @param sr
	 * @param activity
	 * @return
	 * @throws JMSException
	 */
	private Json newActivityTxn(LegacyEmulator emulator, Json sr, Json activity) throws JMSException
	{
		Json result;
		Json original = sr.dup();
		boolean createdDateProvided = false;
		Date updateDate = new Date();
		//1. if sr was already modified, we use that date as updatedDate
		if (original.has("properties") && original.at("properties").has("hasDateLastModified")) {	
			try {
				updateDate = GenUtils.parseDate(original.at("properties").at("hasDateLastModified").asString());				
			} catch(Exception e) {
				ThreadLocalStopwatch.error("Failed to parse sr last modified date in newActivityTxn for SR " + original);
			}
		}
		//2. use create activity date as update date if after last sr modification.
		if (activity.has("hasDateCreated")) {
			createdDateProvided = true;
			try {
    			Date actCreated = GenUtils.parseDate(activity.at("hasDateCreated").asString());
    			if (actCreated.after(updateDate)) {
    				updateDate = actCreated;
    				sr.at("properties").set("isModifiedBy", "department");
    			}
			} catch (Exception e) {
				ThreadLocalStopwatch.error("Failed to parse new activity hasDateCreated in newActivityTxn for act " + activity);
			}
		}
		//3. use activity calculated act created =~ act completed date as update date if after sr modification 
		//Timestamp activity if needed to set created date at completed date
		if (activity.has("hasCompletedTimestamp")) {
			try {
				Date completedDate = GenUtils.parseDate(activity.at("hasCompletedTimestamp").asString());
				//Add one minute for each existing activity with same completed date to retain order
				//in case multiple activities are received for the same day and hasCompletedTimestamp from interface has zero hours.
				Date calcCreatedDate = ServiceCaseJsonHelper.calculateNextActivityCreatedDate(original, completedDate);
				if (!createdDateProvided) {
					timeStamp(activity, calcCreatedDate);
				} 
				if (calcCreatedDate.after(updateDate)) {
					updateDate = calcCreatedDate;
					sr.at("properties").set("isModifiedBy", "department");
				}
			} catch(Exception e) {
				ThreadLocalStopwatch.error("Failed to parse sr last modified date in newActivityTxn for SR " + original);
			}
		}
		//4. act created by "department" if not provided by interface.
		if (!activity.has("isCreatedBy")) {
			activity.set("isCreatedBy", "department");
		}
		
		ServiceCaseJsonHelper.insertIriFromCode(activity.at("hasActivity"), sr.at("type").asString() + "_");
		ServiceCaseJsonHelper.insertIriFromCode(activity.at("hasOutcome"), "OUTCOME_");		
		sr.at("properties").at("hasServiceActivity").add(activity);
		ServiceCaseJsonHelper.cleanUpProperties(sr);
		ServiceCaseJsonHelper.assignIris(sr);
		OWL.resolveIris(sr.at("properties"), null);
		result = emulator.updateServiceCaseTransaction(sr, original, updateDate, new ArrayList<CirmMessage>(), "department");
		if (result.has("ok") && result.is("ok", true)) 
		{
			srStatsReporter.succeeded("newActivityTxn", result);
		} else
		{
			srStatsReporter.failed("newActivityTxn", result, "updateServiceCaseTransaction ko", "" + result.at("error"));
		}
		return result;
	}
	
	/**
	 * 
	 * @param emulator
	 * @param existing
	 * @param newdata
	 * @return
	 * @throws JMSException
	 */
	private Json updateTxn(LegacyEmulator emulator, Json existing, Json newdata) throws JMSException
	{
		Json result;
		Json preUpdateSr = existing.dup();				
		Json postUpdateSr = existing;
		Json updateDateJson = newdata.atDel("updateDate");
		Date updateDate = null;
		if (updateDateJson != null) 
		{
			updateDate = GenUtils.parseDate(updateDateJson.asString());
		}
		//Note that postUpdateSr and newdata will be modified in next line.
		updateExistingCase(postUpdateSr, newdata, updateDate);
		result = emulator.updateServiceCaseTransaction(postUpdateSr, 
													 preUpdateSr,
													 updateDate,
													 new ArrayList<CirmMessage>(),
													 "department");
		if (result.has("ok") && result.is("ok", true)) 
		{
			srStatsReporter.succeeded("updateCaseTxn", result);
		} else
		{
			srStatsReporter.failed("updateCaseTxn", result, "updateServiceCaseTransaction ko", "" + result.at("error"));
		}
		return result;
	}
	
	/**
	 * Updates an existing case json by applying new data received from an interface update message to the passed in existing case json.
	 * Existing and newData will be modified by this method.
	 * 
	 * To be used inside of a transaction.
	 * @param existing case SR loaded from the DB.
	 * @param newdata data received from an interface.
	 */
	 void updateExistingCase(Json existing, Json newdata, Date updateDate) {
		 if (updateDate == null) {
			 updateDate = new Date();
		 }
		Json props = existing.at("properties");
		if (newdata.has("hasXCoordinate") && (
					  !props.has("hasXCoordinate") || !newdata.is("hasXCoordinate", props.at("hasXCoordinate")))
			||  
			newdata.has("hasYCoordinate") && (
							  !props.has("hasYCoordinate") || !newdata.is("hasYCoordinate", props.at("hasYCoordinate"))))
		{
			try
			{
				this.updateAddressFromXY(existing.at("properties"), 
										 newdata.at("hasXCoordinate"), 
										 newdata.at("hasYCoordinate"));
				newdata.delAt("hasXCoordinate").delAt("hasYCoordinate").delAt("atAddress");
			}
			catch (Throwable t) { /* maybe log a warning...if it turns out to be useful */ }
		}
		else if (newdata.has("atAddress") && 
				// this is relying on a deep hashmap comparison which strictly
				// speaking must ignore "label" and other annotation properties,
				// but if it's the same address, no harm should be done anyway
				 !newdata.is("atAddress", existing.at("properties").at("atAddress")))
		{
			try
			{
				this.updateAddress(existing.at("properties"), newdata.at("atAddress"));
				newdata.delAt("hasXCoordinate").delAt("hasYCoordinate").delAt("atAddress");
			}
			catch (Throwable t) { /* maybe log a warning...if it turns out to be useful */ }
		}
		if (newdata.has("type") && !newdata.is("type", existing.at("type")))
		{
			existing.set("type", newdata.atDel("type"));
			// We are expecting fresh new flex questions if the type changes
			// it may be possible to do an intelligent merge of information
			// in this case, but it doesn't make much. Any external system
			// capable of changin the SR type, should be capable of sending
			// the flex questions for that new type as well.
			existing.at("properties").delAt("hasServiceAnswer");
		}
		if (!newdata.has("isModifiedBy")) {
			newdata.set("isModifiedBy", "department");
		}
		timeStamp(newdata.at("hasServiceCaseActor", Json.array()), updateDate);
		Date activiyCreatedDate = ServiceCaseJsonHelper.calculateNextActivityCreatedDate(existing, updateDate);
		timeStamp(newdata.at("hasServiceActivity", Json.array()), activiyCreatedDate);
		ServiceCaseJsonHelper.insertIriFromCode(newdata.at("hasServiceCaseActor", Json.array()), "hasServiceActor", "");
		ServiceCaseJsonHelper.insertIriFromCode(newdata.at("hasServiceAnswer",Json.array()), "hasServiceField", existing.at("type").asString() + "_");
		ServiceCaseJsonHelper.insertIriFromCode(newdata.at("hasServiceActivity"), "hasActivity", existing.at("type").asString() + "_");
		ServiceCaseJsonHelper.insertIriFromCode(newdata.at("hasServiceActivity"), "hasOutcome", "OUTCOME_");
		ServiceCaseJsonHelper.insertIriFromCode(newdata.at("hasStatus"), "");
		ServiceCaseJsonHelper.cleanUpProperties(newdata);
		ServiceCaseJsonHelper.assignIris(newdata);
		ServiceCaseJsonHelper.cleanUpProperties(existing);
		ServiceCaseJsonHelper.assignIris(existing);
		ServiceCaseJsonHelper.replaceAnswerLabelsWithValues(newdata.at("legacy:hasServiceAnswer"));
		OWL.resolveIris(existing.at("properties"), null);		
		// hilpold: After resolveIRIs, the json implementation will point hasOutcome and HasStatus to the same status {"iri", "....O-LOCKED"} object.
		// Therefore during the merge, where the value of "iri" in the status object is updated to C-CLOSED, both references are updated.
		// Pointing two property values at the same json object first happens in Owl.resolveIRIs, where the gathering knows only the {"iri","...O-LOCKED} object 
		// and that same object will be referred to from all occurances of a "O-LOCKED" string iri as value.
		// Resolution: change merge to never update a possibly shared object.
		ServiceCaseJsonHelper.mergeInto(newdata, existing.at("properties"));			
		trace("Merged: " + existing + "\n\n");
	}

	/**
	 * A response was received from an interface - Only if was due to a newCase Cirm sent, we set the case to O-LOCKED or X-ERROR.
	 * TODO hilpold What if it's closed? Are we sending closed cases as newCase sometimes? Shouldn't it remain closed?
	 * If the response is not due to a newCase, no case update whatsoever is performed and ok() is returned.
	 * 
	 * @param emulator
	 * @param jmsg
	 * @param emailsToSend
	 * @return
	 * @throws JMSException
	 */
	private Json responseTxn(LegacyEmulator emulator, Json jmsg, ArrayList<CirmMessage> emailsToSend) throws JMSException
	{
		Json orig = jmsg.at("originalMessage");
		// Since cases are not modifiable in CiRM, the only response for an 
		// actual business action we are getting is for new cases.
		//
		// Add if-logic if there are other types of responses that need processing
		// (activities?)
		if (!orig.is("messageType", "NewCase"))
		{
			srStatsReporter.succeeded("responseTxn-nonNewCase-do nothing", jmsg);
			return GenUtils.ok();
		}
		Json scase = emulator.lookupServiceCase(orig.at("data").at("boid"));
		if (!scase.is("ok", true))
		{
			srStatsReporter.failed("responseTxn", jmsg, "Could not find case to update after a newCase response from an interface", "");
			String errmsg = "While processing departmental response, cannot load service case " + 
					orig.at("data").at("boid") + " :" + scase.at("error");
			TraceUtils.error(new Exception(errmsg));
			return GenUtils.ko(errmsg);					
		}
		else 
		{
			scase = scase.at("bo");
		}
		Json preUpdateSr = scase.dup();
		//Update scase
		OWL.resolveIris(scase, null);		
		ServiceCaseJsonHelper.cleanUpProperties(scase);
		ServiceCaseJsonHelper.assignIris(scase);
		if (jmsg.at("response").is("ok", true))
		{
			if (orig.is("messageType", "NewCase")) 
			{
				ServiceCaseJsonHelper.ensureExclusivePropertyValueObject(scase.at("properties"), "legacy:hasStatus");
				scase.at("properties").at("legacy:hasStatus").set("iri", 
						fullIri("legacy:O-LOCKED").toString());
			}
			if (jmsg.at("response").has("hasLegacyId")) 
			{
				scase.at("properties").set("legacy:hasLegacyId", jmsg.at("response").at("hasLegacyId"));
			}	
			if (jmsg.at("response").has("data"))
			{
				Json data = jmsg.at("response").at("data");
				ServiceCaseJsonHelper.cleanUpProperties(data);
				ServiceCaseJsonHelper.assignIris(data);				
				ServiceCaseJsonHelper.mergeInto(data, scase.at("properties"));
			}
		}
		else
		{
			ServiceCaseJsonHelper.ensureExclusivePropertyValueObject(scase.at("properties"), "legacy:hasStatus");
			scase.at("properties").at("legacy:hasStatus").set("iri", fullIri("legacy:X-ERROR").toString());
			scase.at("properties").set("legacy:hasDepartmentError", jmsg.at("response").at("error"));				
		}				
		// The following is to remove the "temp" IRIs which intefering with newly added answers,
		// the end up having duplicate temp IRIs. 
		for (Json x : scase.at("properties").at("legacy:hasServiceAnswer").asJsonList())
			x.delAt("iri");				
		OWL.resolveIris(scase.at("properties").at("legacy:hasServiceCaseActor"), null);
		OWL.resolveIris(scase.at("properties").at("legacy:hasServiceActivity"), null);								
		trace("Saving " + scase + "\n\n");
		Json result = emulator.updateServiceCaseTransaction(scase, 
														    preUpdateSr, 
														    null, 
														    emailsToSend,
														    "department");
		if (!result.is("ok", true))
		{
			srStatsReporter.failed("responseTxn", result, "emu.updateServiceCaseTransaction failed", "" + result.at("error"));
			// If we fail to save, this is bug, it must be reported, but we shouldn't be generating
			// a response, and we should still consume the message.
			Exception ex = new Exception("Failed to save case with error '" + result.at("error") + 
					"\n\n from legacy message response:\n\n" + jmsg);
			TraceUtils.severe(ex);
		} 
		else
		{
			srStatsReporter.succeeded("responseTxn", result);
		}
		return result;
	}
	
	private void process(final Json jmsg) throws JMSException
	{		
		trace("JMS receive:" + jmsg + "\n\n");
		final LegacyEmulator emulator = new LegacyEmulator();
		final LegacyMessageType messageType = LegacyMessageType.valueOf(jmsg.at("messageType").asString());
		//long transactionId = jmsg.at("transactionId").asLong();
		//Respond to any message prior to 2009 with OK Historic Data.
		final LegacyMessageValidator validator = new LegacyMessageValidator();
		switch (messageType)
		{
			case NewCase:
			{
				ThreadLocalStopwatch.now("LegacyJMSListener.process NewCase ");
				Refs.defaultRelationalStore.resolve().txn(new CirmTransaction<Json>() {
				public Json call() throws JMSException
				{				
					Json jmsgForTx = jmsg.dup();
					MessageValidationResult validationResult = validator.validateNewCase(jmsgForTx);
					Json R;
					if (validationResult.isValid()) 
					{
						R = newCaseTxn(emulator, jmsgForTx);
						if (!R.is("ok", true)) 
						{
							ThreadLocalStopwatch.error("LegacyJMSListener.process NewCase txn failed");
							System.err.println(R.at("error") + " for " + jmsgForTx);
						}
						srStatsReporter.succeeded("process-NewCase", R);
					} else
					{
						if (validationResult.isAllowRetry()) 
						{  //respond ko to allow retry by interface 
							R = GenUtils.ko(validationResult.getResponseMessage());
						} else 
						{	//respond ok, despite error to not allow interface retry 
							R = Json.object("ok", true, "error", validationResult.getResponseMessage());
						}
						srStatsReporter.failed("process-NewCase", jmsgForTx, "invalid message", "" + validationResult.getResponseMessage());
					}
					return R;
				}});
				break;
			}
			case NewActivity:
			case CaseUpdate:				
			{
				Refs.defaultRelationalStore.resolve().txn(new CirmTransaction<Json>() {
				public Json call() throws JMSException
				{							
					ThreadLocalStopwatch.now("LegacyJMSListener.process lookup case for update or new activity");
					Json jmsgForTx = jmsg.dup();
					MessageValidationResult validationResult;
					validationResult = (messageType == LegacyMessageType.NewActivity)? 
							validator.validateNewActivity(jmsgForTx) : validator.validateCaseUpdate(jmsgForTx);
					Json R;
					if (validationResult.isValid()) 
					{
						Json origSr = validationResult.getExistingSR();
						Json origSrForTxn = origSr.at("bo").dup(); //Dup needed for idempotent retries!
						// This should be done already by the framework, but we are not there
						// yet...not clear on strategy, maybe "all non-functional properties
						// should be turned into arrays".
						origSrForTxn.at("properties").delAt("extendedTypes");
						origSrForTxn.at("properties").delAt("gisAddressData");
						trace("Existing: " + origSrForTxn + "\n\n");
						if (messageType == LegacyMessageType.NewActivity) 
						{
							ThreadLocalStopwatch.now("LegacyJMSListener.process newActivityTxn ");					
							R = newActivityTxn(emulator, origSrForTxn, jmsgForTx.at("data").at("hasServiceActivity").at(0));
						}
						else
						{ //update service case
							ThreadLocalStopwatch.now("LegacyJMSListener.process updateTxn ");					
							R = updateTxn(emulator, origSrForTxn, jmsgForTx.at("data").dup().delAt("boid"));
						}
						if (R.is("ok", false)) 
						{
							ThreadLocalStopwatch.error("ERROR: LegacyJMSListener.process newActivityTxn or updateTxn failed, responding");
							System.err.println(R.at("error") + " for " + jmsgForTx);
						}
					}
					else 
					{
						if (validationResult.isAllowRetry()) 
						{  //respond ko to allow retry by interface 
							R = GenUtils.ko(validationResult.getResponseMessage());
						} else 
						{	//respond ok, despite error to not allow interface retry 
							R = Json.object("ok", true, "error", validationResult.getResponseMessage());
						}
						srStatsReporter.failed("process-NewActivityOrCaseUpdate", jmsgForTx, "invalid message ", "" + validationResult.getResponseMessage());
						ThreadLocalStopwatch.error("ERROR: LegacyJMSListener.process lookup for update or new activity: case not found, responding that");							
					}
					try
					{
						JMSClient.connectAndRespond(jmsgForTx, R);
					}
					// TODO: we should re-implement this to ask the TimeServer to call back and retry sending the response
					catch (Exception ex) { throw new RuntimeException(ex); }					
					return R;					
				}});
				break;
			}
			case Response:		
			{
				//Issue #705 : X-Error Email Notification. Emails were not getting triggered while response was received from the interface.
				ThreadLocalStopwatch.now("LegacyJMSListener.process responseTxn & sending emails if any");					
				Refs.defaultRelationalStore.resolve().txn(new CirmTransaction<Json>() {
				public Json call() throws JMSException
				{												
					ArrayList<CirmMessage> emailsToSend = new ArrayList<CirmMessage>();
					CirmTransaction.get().addTopLevelEventListener(new SendEmailOnTxSuccessListener(emailsToSend));					
					Json R = responseTxn(emulator, jmsg, emailsToSend);				
					if (R.is("ok", false))
					{
						ThreadLocalStopwatch.error("LegacyJMSListener.process responseTxn failed with ");					
						System.err.println("LegacyJMSListener " + R.at("error") + " for " + jmsg);
					}
					return R;
				}});
				break;
			}
			default:
			{
				srStatsReporter.failed("process", jmsg, "Message type unknown", "" + jmsg);
			}
		}
		//throw new RuntimeException();		
	}
	
	public void run()
	{
		int loopCount = 0;
		// successCount is increased after process() returns.
		int successCount = 0;
		ThreadLocalStopwatch.startTop("START LegacyJMSListener.run ");					
		trace("In Legacy JMS run method");
		if (!init()) 
		{
			ThreadLocalStopwatch.fail("FAIL LegacyJMSListener.run init()");					
			return;
		}
		fatality = null;
		try
		{
		for (quit = false; !quit; )
		{
			loopCount ++;
			ThreadLocalStopwatch.startTop("START LegacyJMSListener loop execution loopCount " + loopCount + " successCount " + successCount);					
			TextMessage msg = null;
			String msgText = null;
			try
			{
				trace("Before read message.");
				msg = (TextMessage)receiver.receive(config.at("timeout").asLong());
				trace("After read message." + msg);
				if (msg == null) 
				{
					ThreadLocalStopwatch.fail("LegacyJMSListener.run received msg was null loop " + loopCount);										
					continue;
				}
				if (msg.getText() == null || msg.getText().equals(""))
				{
					ThreadLocalStopwatch.fail("LegacyJMSListener.run received msg, but txt was null or empty loop " + loopCount);										
					error("Null or empty text in message.");
					msg.acknowledge();
					continue;
				}
				else 
				{
					msgText = msg.getText();
					msgText=msgText.replace("TMOUPD", "COMPLETE");
					Json jmsg = Json.read(msgText);
					try
					{
						RestService.forceClientExempt.set(true);					
						process(jmsg);					
						successCount ++;
					}
					catch (JMSException ex)
					{
						// Failed to send response, we have to exit the thread to avoid
						// multiple such failures until the problem was fixed. The
						// response itself should have been rescheduled through the 
						// time server.
						srStatsReporter.failed("run", "Message", "", "" + ex, "exiting JMS Listener thread > STOPPED!!");
						throw new Exception("Wrap JMS exception -exiting JMS Listener thread", ex);
					}
					catch (Throwable t)
					{
						srStatsReporter.failed("run", "Message", "", "" + t, t.getMessage());					
						error("While processing " + msg, t);
						JMSClient.connectAndRespond(jmsg, Json.object("ok", false, "error", t.toString()));
						// throw t;
					}
					finally
					{			
						// This is to clear the temp ontology manager that caching
						// BO ontologies, and potentially other thread-bound 
						// structures that were intended to be used within a single
						// request transaction.
						RequestScopeFilter.clear();
						out.flush();
					}
					msg.acknowledge();
				} //else
			}
			catch (JMSException ex)
			{
				error("Failed to receive JMS message with config " + 
							config.at("jms") + ", stack trace follows...", ex);
				cleanup();
				do
				{
					ThreadLocalStopwatch.error("FAIL LegacyJMSListener.run re-initialize JMS connection after " + ex);														
					trace("Attempting to re-initialize JMS connection after " + (double)
							config.at("timeout").asLong()/1000.0 + " seconds.");
					try { Thread.sleep(config.at("timeout").asLong()); } catch (InterruptedException ie) { }
				} while (!init() && !quit);
			}
			catch (Throwable t)
			{
				ThreadLocalStopwatch.fail("FAIL LegacyJMSListener.run with unexpected: " + t);									
				fatality = t; // capture the exception for possible later examination...
				// for now, we don't allow continuation of the main loop if any other exception occurs.
				// if more tolerance is needed, it will have to be explicitely added on a case by case basis
				error("An unexpected exception occured, exiting listener loop...", t);
				error("Legacy JMS Listener crashing due to an unacceptable exception." + config.at("jms"), t);				
				throw new RuntimeException(t);
			}
		} // for
		} 
		finally
		{
			cleanup();
		}
		ThreadLocalStopwatch.stop("END LegacyJMSListener.run processing queue messages stopped");
	}

	
	
	/**
	 * <p>Return the fatal error that caused the thread to exit.</p>
	 */
	public Throwable getFatality()
	{
		return fatality;
	}
	
	public String getStatus()
	{
		if (isAlive())
			return "running";
		else if (quit)
			return "stopped by user";
		else if (fatality != null)
			return "stopped due to fatal error: " + fatality.toString();
		else
			return "never started";
	}
		
	public void quit()
	{
		ThreadLocalStopwatch.now("LegacyJMSListener.quit() processing will stop (initiated by user)");
		quit = true;
	}
	
//	private Json getConfig()
//	{
//		OWLNamedObject x = Refs.configSet.resolve().get("OperationsQueue");
//		OWLNamedIndividual info = OWL.individual(x.getIRI()); // OWL.individual("CIRMTestQueueConnection"); 
//		OWLNamedIndividual queueType = OWL.objectProperty(info, "hasQueueType");
//		Json config = Json.object(
//			"factoryClassName", OWL.dataProperty(queueType, "hasFactoryClassName").getLiteral(),
//			"url", OWL.dataProperty(info, "hasUrl").getLiteral(),
//			"channel", OWL.dataProperty(info, "hasChannel").getLiteral(),
//			"queueManager", OWL.dataProperty(info, "hasQueueManager").getLiteral(),
//			"outQueueName", OWL.dataProperty(info, "hasOutQueueName").getLiteral(),
//			"inQueueName", OWL.dataProperty(info, "hasInQueueName").getLiteral(),
//			"port", OWL.dataProperty(info, "hasPort").getLiteral()
//		);
//		
//		return config;
//	}
 
//	public static void main(String args[])
//	{
//		System.out.println("Starting JMS Listener");
//		if( (args.length > 0) )
//			StartUp.getConfig() = Json.read(GenUtils.readTextFile(new File(args[0])));
//		StartupUtils.disableCertificateValidation();
//		System.out.println("Using config " + StartUp.getConfig().toString());
//		DepartmentListenerController ctrl = new DepartmentListenerController();
//		ctrl.traceMore();
//		try
//		{
//			ctrl.start();
//		}
//		catch (Exception e)
//		{
//			e.printStackTrace();
//		}
//	}
}

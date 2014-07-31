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

import static mjson.Json.object;
import static org.sharegov.cirm.OWL.and;
import static org.sharegov.cirm.OWL.has;
import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.objectProperty;
import static org.sharegov.cirm.OWL.owlClass;
import static org.sharegov.cirm.OWL.reasoner;
import static org.sharegov.cirm.utils.GenUtils.ko;
import static org.sharegov.cirm.utils.GenUtils.ok;

import java.util.Calendar;
import java.util.Set;
import java.util.logging.Level;

import javax.jms.JMSException;
import javax.ws.rs.Consumes;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import mjson.Json;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.StartUp.CirmServerResource;
import org.sharegov.cirm.gis.GisException;
import org.sharegov.cirm.legacy.MessageManager;
import org.sharegov.cirm.legacy.Permissions;
import org.sharegov.cirm.rdb.DBIDFactory;
import org.sharegov.cirm.rest.LegacyEmulator;
import org.sharegov.cirm.rest.RestService;
import org.sharegov.cirm.stats.CirmStatistics;
import org.sharegov.cirm.stats.CirmStatisticsFactory;
import org.sharegov.cirm.stats.SRCirmStatsDataReporter;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import gov.miamidade.cirm.MDRefs;
import gov.miamidade.cirm.other.JMSClient;
import org.sharegov.cirm.utils.JsonUtil;

/**
 * <p>
 * Methods dealing with departmental integration. 
 * </p>
 * 
 * @author boris
 *
 */
@Path("legacy/departments")
@Produces("application/json")
@Consumes("application/json")
public class DepartmentIntegration extends RestService
{
	public static final int RETRY_INTERVAL_SHORT_MINS = 60;
	public static final int RETRY_INTERVAL_LONG_MINS = 12 * 60;
	
	public static final int MAX_RETRY_SHORT = 2; //twice every hour
	public static final int MAX_RETRY_LONG = 21 * 2; // 21days every 12 hours
	
    private LegacyEmulator emulator = new LegacyEmulator();
    
	private SRCirmStatsDataReporter srStatsReporter = 
			CirmStatisticsFactory.createServiceRequestStatsReporter(MDRefs.mdStats.resolve(), "DepartmentIntegration"); 

    // The following is to support start/stop of the JMS queue listener for departmental events
    // it should only be started on one machine.
    @GET
    @Path("/status")
    public Json getDepartmentsListenerStatus()
    {
        return ok().set("status", DepartmentListenerController.ref.resolve().status());
    }

    @GET
    @Path("/config")
    public Json getDepartmentsListenerConfig()
    {
        return ok().set("config", DepartmentListenerController.ref.resolve().getConfig());
    }

    @POST
    @Path("/config")
    @Consumes("application/json")
    public Json setDepartmentsListenerConfig(Json newconfig)
    {
    	DepartmentListenerController.ref.resolve().getConfig().with(newconfig);       
        return ok();
    }

    @POST
    @Path("/start")
    public Json getDepartmentsListenerStart()
    {
        try
        {
        	DepartmentListenerController.ref.resolve().start();       
            return this.getDepartmentsListenerStatus();     
        }
        catch (Throwable t)
        {
            return ko(t);
        }
    }

    @POST
    @Path("/stop")
    public Json getDepartmentsListenerStop()
    {
        try
        {
        	DepartmentListenerController.ref.resolve().stop(60000);
            return this.getDepartmentsListenerStatus();
        }
        catch (Throwable t)
        {
            return ko(t);
        }
    }

    /**
     * <p>Return
     */
    @GET
    @Path("/openReport/{numberOfDays}")
    public Json getDepartmentsOpenReport(@PathParam("numberOfDays") int numberOfDays)
    {
        Json R =  this.getOpenCasesReport(new LegacyEmulator(), numberOfDays);
        if (R.is("ok", true))
        {
            MessageManager.get().emailAsAttachment("cirm@miamidade.gov", "hilpold@miamidade.gov;sabbas@miamidade.gov;jorgefi@miamidade.gov",
                    "CIRM Open Cases Report " + R.at("from").asString() + " to " +
                    R.at("to").asString(), JsonUtil.csvTable(R.at("results"), 
                            "hasCaseNumber,type,hasStatus,hasDateCreated,hasLegacyInterface", 
                            "Case Number,SR Type,Status,Created On,Department"),
                            "application/csv",
                            "openCases.csv");
            return ok();
        }
        else
            return R; 
    }
    
    /**
     * <p>
     * Retrieve the individual representing the departmental interface with which the given case type
     * is associated. 
     * </p>
     * 
     * @param caseType The IRI (short or long form) of the case type individual.
     */
    @GET
    @Path("/interfaceCode/{caseType}")    
	public Json getDepartmentInterfaceCode(@PathParam("caseType") String caseType)
	{
		if (caseType.contains("#"))
			caseType=caseType.substring((caseType.indexOf("#") + 1));
		if (!caseType.startsWith("legacy:"))
			caseType="legacy:" +caseType;
		Set<OWLNamedIndividual> interfaces = reasoner().getInstances(
				and(owlClass("legacy:LegacyInterface"),
						has(objectProperty("legacy:hasAllowableEvent"),
								individual("legacy:NEWSR")),
						has(objectProperty("legacy:isLegacyInterface"),
								individual(caseType))), false).getFlattened();
		return interfaces.isEmpty() ? Json.nil() : OWL.toJSON(interfaces.iterator().next());		
	}
	
    /**
     * <p>
     * Invoked by the scheduling service (Time Server) in order to send a
     * message to a departmental message. Normally MQ Series is stable and
     * when a new message or a response is sent, it goes through right away. However, there
     * are rare cases where sending the message fails because the JMS queue is full
     * or otherwise unavailable. When that happens, the next options is for CiRM
     * to queue the message by some other means and that is the Time Server.
     * 
     * </p>
     * <p>
     * This method is the callback the Time Server invokes to re-send a previously failed
     * message to the departmental queue.
     * </p>
     * 
     * @param msg
     * @return
     */
    @POST
    @Path("/sendMessage")
    public Json sendMessageToDepartment(Json msg)
    {
    	ThreadLocalStopwatch.startTop("STARTTop /sendMessage to department ");
        try
        {
            JMSClient.connectAndSend(msg);
        	ThreadLocalStopwatch.stop("END /sendMessage to department ");        	
            return ok();
        }
        catch (JMSException ex)
        {
            // We ignore because the connectAndSend method will already re-schedule another attempt
            // in case of a JMS error.
        	ThreadLocalStopwatch.stop("END with time machine retry /sendMessage to department ");
            return ok();
        }
        catch (Throwable t)
        {
        	ThreadLocalStopwatch.fail("END /sendMessage to department ");
            return ko(t);
        }
    }
    
    @POST
    @Path("/sendnew")
    @Produces("application/json")
    @Consumes("application/json")
    public Json caseToDepartment(Json data)
    {
		ThreadLocalStopwatch.getWatch().time("START DepartmentIntegration /sendnew ");
		forceClientExempt.set(true);
        long initiatedAt = data.at("initiatedAt", 0).asLong();
        if (!data.has("caseNumber")) 
        {
        	ThreadLocalStopwatch.getWatch().time("FAIL DepartmentIntegration /sendnew case number missing ");
    		return ko("Case number property missing from JSON object.");
        }
        String casenumber = data.at("caseNumber").asString();
        long boid = emulator.toServiceCaseId(casenumber);
        try
        {
            BOntology bo = emulator.findServiceCaseOntology(boid);
            if (bo == null)
                bo = emulator.findServiceCaseOntology(boid);
            sendToDepartment(bo, null, null);
        	ThreadLocalStopwatch.getWatch().time("SUCCEEDED DepartmentIntegration /sendnew  " + casenumber);
            return ok();
        }
        catch (GisException gisex)
        {
            if (initiatedAt + 24*60*60*1000 < System.currentTimeMillis())
            {
                GenUtils.reportFatal("While sending (after 24 hour long attempts: " + initiatedAt + " - " + System.currentTimeMillis() +  
                            ") " + casenumber, gisex.toString(), gisex);
                return ko(gisex);
            }
            else
            {
            	ThreadLocalStopwatch.getWatch().time("RETRY DepartmentIntegration /sendnew  by time machine in 30 mins " + casenumber);
                Json j = GenUtils.timeTask(30, "/legacy/departments/sendnew", data);
                if (j.is("ok", false))
                    GenUtils.reportFatal("While retrying new case task " + casenumber, j.toString(), gisex);
                return ko("Failed with " + gisex.toString() + ", resubmission success=" + j.is("ok", true));
            }                   
        }
        catch (Throwable t)
        {
            GenUtils.reportFatal("While sending " + casenumber, t.toString(), t);           
            return ko(t);
        }       
    }
    
    /**
     * 
     * @deprecated
     */ 
    @GET
    @Path("/sendnew/{casenumber}")
    @Produces("application/json")
    public Json caseToDepartment(@PathParam("casenumber") String casenumber)
    {
        long boid = -1; 
        try { boid = Long.parseLong(casenumber); }
        catch (Throwable t)
        {
            try
            {
                boid = emulator.lookupServiceCaseId(Json.object("legacy:hasCaseNumber", casenumber, 
                                                        "type", "legacy:ServiceCase"));
            }
            catch (Throwable t2)
            {
                GenUtils.reportFatal("While retreiving " + casenumber, t2.toString(), t2);
                return ko(t2);
            }
        }
        if (boid < 0)
            return ko("Case number " + casenumber + " not found.");
        try
        {
            BOntology bo = emulator.findServiceCaseOntology(boid);
            sendToDepartment(bo, null, null);
            return ok();
        }
        catch (GisException ex)
        {
            return ko(ex).set("retryAfter", 30);
        }
        catch (Throwable t)
        {
            GenUtils.reportFatal("While sending " + casenumber, t.toString(), t);           
            return ko(t);
        }
    }
    
    @GET
    @Path("/testtm/{casenumber}")
    @Produces("application/json")
    public Json testtm(@PathParam("casenumber") String casenumber)
    {
        if (casenumber.startsWith("2"))
            return ok();
        else
            return ko("not found");
    }

    @POST
    @Path("/testtmPost")
    @Produces("application/json")
    @Consumes("application/json")
    public Json testtmPost(Json data)
    {
        System.out.println("testtmPost: " + data);
        return ok();
    }
    
    /**
     * Schedules a time machine task to send a case to a department respecting hasAnswerUpdateTimeout.
     * TM task name will be: sendCase_" + serviceCase.at("boid") + "_ToDepartment.
     * 
     * @param serviceCase
     * @param locationInfo
     * @param minutes
     * @return
     */
    public Json delaySendToDepartment(Json serviceCase, Json locationInfo, int minutes)
    {
        String relativePath = "/legacy/departments/sendnew";
        if (serviceCase.at("hasLegacyInterface").is("hasLegacyCode", "COM-CITY"))
        {
            relativePath = "/other/cityofmiami/sendnew";
//          minutes = 1;
        }
        Json type = OWL.toJSON(OWL.individual("legacy:" + serviceCase.at("type").asString()));
        if (!type.has("hasAnswerUpdateTimeout"))
            type = OWL.toJSON(OWL.individual("legacy:ServiceCase"));
        if (!type.has("hasAnswerUpdateTimeout"))
        {
            this.sendToDepartment(serviceCase, locationInfo);
            srStatsReporter.succeeded("delaySendToDepartment", serviceCase);
            return ok();
        }       
        Json timeMachine = OWL.toJSON((OWLIndividual)Refs.configSet.resolve().get("TimeMachineConfig"));
        Json thisService = OWL.toJSON((OWLIndividual)Refs.configSet.resolve().get("OperationsRestService"));
        Calendar cal = Calendar.getInstance();          
        cal.add(Calendar.MINUTE, minutes > -1 ? minutes : type.at("hasAnswerUpdateTimeout").at("hasValue").asInteger());
        if (minutes == 0)
        {
            this.sendToDepartment(serviceCase, locationInfo);
            return ok();            
        }
        Json taskSpec = object(); 
        Json restCall = object("url",  
                 thisService.at("hasUrl").asString()  + relativePath,
                               "method", "POST",
                               "content", object("caseNumber", serviceCase.at("boid"),
                                                 "initiatedAt", System.currentTimeMillis()));
        taskSpec.set("restCall",restCall)
                .set("group", "cirm_service_hub")
                .set("name", "sendCase_" + serviceCase.at("boid") + "_ToDepartment")
                .set("state", "NORMAL")
                .set("description", "Send new service case with BOID " + serviceCase.at("boid") + " to departments for further processing.")
                .set("scheduleType", "SIMPLE")
                .set("startTime", object()
                    .set("day_of_month", cal.get(Calendar.DATE))
                    .set("month", cal.get(Calendar.MONTH) + 1)
                    .set("year", cal.get(Calendar.YEAR))
                    .set("hour", cal.get(Calendar.HOUR_OF_DAY))
                    .set("minute", cal.get(Calendar.MINUTE))
                    .set("second", cal.get(Calendar.SECOND))
        );
        Json tmResult;
        tmResult = GenUtils.httpPostJson(timeMachine.at("hasUrl").asString() +  "/task", taskSpec);
        return ok().set("task", tmResult);
    }
    
    public Json formatNewCaseForDepartments(Json data, Json locationInfo)
    {
        if (!locationInfo.isNull())
        {            
            if (locationInfo.at("address", Json.object()).has("propertyInfo") &&
                    !locationInfo.at("address").at("propertyInfo").isNull() &&
                    locationInfo.at("address").at("propertyInfo").has("parcelFolioNumber"))
                data.set("folio", locationInfo.at("address").at("propertyInfo").at("parcelFolioNumber", "").asString());
        }
        data.set("hasGeoAreaLayer", locationInfo);
        
        // Maybe we need to ensure the other elements are arrays as well. But,
        // the real
        // solution to this is for generic JSON serialization to create arrays
        // for
        // non-functional properties, always.
        GenUtils.ensureArray(data, "hasServiceCaseActor");
        GenUtils.ensureArray(data, "hasServiceAnswer");
        GenUtils.ensureArray(data, "hasServiceActivity");                                                       
        LegacyEmulator.replaceAnswerValuesWithLabels(data);
        return data;
    }
    
    /**
     * Formats case & locationinfo for a department and uSes JMSClient to send it there.
     *  
     * @param caseData
     * @param locationInfo
     */
    public void sendToDepartment(Json caseData, Json locationInfo)
    {
        Json data = formatNewCaseForDepartments(caseData, locationInfo);
        try
        {
            JMSClient.connectAndSend(LegacyMessageType.NewCase, 
                ((DBIDFactory) Refs.idFactory.resolve()).generateSequenceNumber(), data);
        }
        catch (JMSException ex)
        {
            srStatsReporter.failed("sendToDepartment", caseData, "" + ex, "JMSCLIENT failed" + ex.getMessage());
        	// We do need to report the error, but we also have to retry at a later time.
            Refs.logger.resolve().log(Level.SEVERE, "While sending case " + data.at("hasServiceCase") + " to departments.", ex);
//              this.delaySendToDepartment(bontology, deptInterface, 60);
        }        
    }
    
    
    /**
     * <p>
     * Send a new service case for departments to process.
     * </p>
     * 
     * @param bontology The BO ontology.
     * @param data Should be the same as <code>bontology.toJSON()</code> if available, if not
     * available just pass <code>null</code>. This parameter is only used to save time if
     * the ontology was already json-inified.
     * @param deptInterface This is <code>OWL.toJSON(I)</code> where <code>I</code> is
     * the individual representing the departmental interface. As with the <code>data</code>
     * parameter, if you don't have this available just pass null. 
     */
    public void sendToDepartment(BOntology bontology, Json deptInterface, Json locationInfo)    
    {
        BOntology vontology;
        try
        {
            vontology = emulator.addMetaDataAxioms(bontology); 
        }
        catch (OWLOntologyCreationException e)
        {
            throw new RuntimeException(e);
        }
        IRI objectIRI = bontology.getObjectIRI();
        OWLDataFactory boFactory = bontology.getOntology().getOWLOntologyManager().getOWLDataFactory();     
        if (deptInterface == null)
            deptInterface = getDepartmentInterfaceCode("legacy:" + bontology.getTypeIRI().getFragment());
        Json data = OWL.toJSON(vontology.getOntology(), boFactory.getOWLNamedIndividual(objectIRI));        
        if (locationInfo == null)
            locationInfo = emulator.populateGisData(Json.object("properties", data), bontology);
        sendToDepartment(data.set("hasLegacyInterface", deptInterface), locationInfo);
        srStatsReporter.succeeded("sendToDepartment-BO", data);
    }
    
    @GET
    @Path("/sendnewactivity/{boid}/{timestamp}/{minutes}")
    public Json sendActivityService(@PathParam("boid") long boid, @PathParam("timestamp") long timestamp, @PathParam("minutes") int minutes)
    {
		ThreadLocalStopwatch.startTop("START DepartmentIntegration /sendnewactivity/" + boid + "/activity timestamp: " + timestamp);
    	forceClientExempt.set(true);
        Json sr = emulator.lookupServiceCase(boid);
        if (!sr.is("ok", true))
        {
        	srStatsReporter.failed("/sendnewactivity/{boid}/{timestamp}/{minutes}", 
        			CirmStatistics.UNKNOWN, "" + boid, "lookup for boid failed, not found", "");
    		ThreadLocalStopwatch.fail("FAIL /sendnewactivity/ case not found ");
        	return sr;
        }
        else
        {
            sr = sr.at("bo");
        }
        Json activity = Json.nil();
        sr=OWL.prefix(sr);
        if (!sr.has("properties")) 
        {
        	srStatsReporter.failed("/sendnewactivity/{boid}/{timestamp}/{minutes}", sr, "no properties in sr json", "");
        	ThreadLocalStopwatch.fail("FAIL /sendnewactivity/ SR has no 'properties' property ");
        	return ko("Bad JSON  - SR has no 'properties' property.");
        }
        if (!sr.at("properties").has("legacy:hasServiceActivity"))
        {
        	srStatsReporter.failed("/sendnewactivity/{boid}/{timestamp}/{minutes}", sr, "no legacy:hasServiceActivity in sr json", "");
        	ThreadLocalStopwatch.fail("FAIL /sendnewactivity/ no activities in case");
            return ko("No activities found in case " + sr.at("boid"));
        }
        if (!sr.at("properties").at("legacy:hasServiceActivity").isArray())
             sr.at("properties").set("legacy:hasServiceActivity", Json.array(sr.at("properties").at("legacy:hasServiceActivity")));
        OWL.resolveIris(sr.at("properties").at("legacy:hasServiceActivity"), null); 
        for (Json a : sr.at("properties").at("legacy:hasServiceActivity").asJsonList())
            if (timestamp == GenUtils.parseDate(a.at("hasDateCreated").asString()).getTime())
            {
                activity = a;
                break;
            }
        if (activity.isNull()) 
        {
        	srStatsReporter.failed("/sendnewactivity/{boid}/{timestamp}/{minutes}", sr, "serviceActivity not found by createdDate", "");
        	ThreadLocalStopwatch.fail("FAIL /sendnewactivity/ activity with timestamp " + timestamp + " not found ");
            return ko("Activity with timestamp " + timestamp + " not found in " + sr.at("boid"));
        }
        try
        {
            Json actSendResult =  tryActivitySend(sr, activity, minutes);
    		ThreadLocalStopwatch.stop("END /sendnewactivity/ timestamp " + timestamp + "");
    		srStatsReporter.succeeded("/sendnewactivity/{boid}/{timestamp}/{minutes}", sr);
    		return actSendResult;
        }
        catch (Throwable t)
        {
        	srStatsReporter.failed("/sendnewactivity/{boid}/{timestamp}/{minutes}", sr, "" + t, t.getMessage());
        	ThreadLocalStopwatch.fail("FAIL /sendnewactivity/ unexpected with " + t);
            return ko(t);
        }
    }
            
    /**
     * <p>
     * Update a case and send to departments as a new case. This is indended to be
     * used after correcting an error. 
     * </p>
     * 
     * @param data
     * @return
     */
    @POST
    @Encoded
    @Path("/send")
    @Produces("application/json")
    @Consumes("application/json")
    public Json resendNewCase(final Json newcase)
    {
        if (!isClientExempt()
                && !Permissions.check(individual("BO_Update"),
                        individual(newcase.at("type").asString()),
                        getUserActors()))
            return ko("Permission denied.");
        
        newcase.at("properties").set("legacy:hasStatus", Json.object("iri", "legacy:O-OPEN"));
        newcase.at("properties").delAt("legacy:hasDepartmentError");
        return Refs.defaultRelationalStore.resolve().txn(new CirmTransaction<Json>() {
        public Json call()
        {       
            Json data = emulator.updateServiceCase(newcase, "cirmuser");
            if (!data.is("ok", true)) 
            {
                return data;
            }
            data = data.at("bo");
            String type = "legacy:" + data.at("type").asString();
            Json deptInterface = getDepartmentInterfaceCode(type);
            if (deptInterface.isNull()) 
                return ko("The type " + type + " is not configured to an external interface.");
            try
            {
                BOntology bo = BOntology.makeRuntimeBOntology(data);
                if (deptInterface.is("hasLegacyCode", "COM-CITY"))
                {
                    new gov.miamidade.cirm.other.CityOfMiamiClient().sendCaseToCOM(
                            Json.object("caseNumber", data.at("properties").at("legacy:hasCaseNumber")));
                    //delaySendToDepartment(bo, deptInterface, 1);
                    return ok().set("bo", bo.toJSON());
                }
                
                // First, check the GIS is ok, this is extra work because we don't
                // have easy means to obtain the return values from the service calls upstream
                // during the update.
                Json locationInfo = emulator.populateGisData(data.dup().at("properties").set("hasLegacyInterface", deptInterface).up(), bo);
                if (locationInfo.has("extendedInfo") && locationInfo.at("extendedInfo").has("error"))
                {
                    GenUtils.reportPWGisProblem(data.at("properties").at("legacy:hasCaseNumber").asString(),
                            locationInfo.at("extendedInfo").at("error"));
                    return ko("GIS extended validation failed: " + locationInfo.at("extendedInfo").at("error"));
                }
                sendToDepartment(bo, deptInterface, null);
                srStatsReporter.succeeded("resendNewCase", data);
                return ok().set("bo", bo.toJSON()); 
            }
            catch (Throwable t)
            {
                srStatsReporter.failed("resendNewCase", data, t.toString(), t.getMessage());
            	// this will print out the "can't serialize exeption", which we don't want 'cause it's normal
//              t.printStackTrace(System.err);
                return ko(t); 
            }
        }});
    }
    
	/**
	 * <p>
	 * Return a list of cases with OPEN status and that have
	 * been created within the last <code>numberOfDays</code>.
	 * </p>
	 * 
	 * <p>
	 * The return JSON array contains objects with the following
	 * form:
	 * </p>
	 * <p>
	 * { "hasCaseNumber" : SR Number, 
	 *   "type": the SR type,
	 *   "hasStatus": the SR status
	 *   "hasLegacyInterface": The interface code of the dept.,
	 *   "hasDateCreated": the date it was created
	 *  }
	 * </p>
	 * @param numberOfDays
	 * @return
	 */
	public Json getOpenCasesReport(LegacyEmulator e, int numberOfDays)
	{
		Calendar cal = Calendar.getInstance();
		String to = GenUtils.formatDate(cal.getTime());
		cal.add(Calendar.DATE, -numberOfDays);
		String from = GenUtils.formatDate(cal.getTime());
		Json crit = Json.object()
			.set("sortBy","boid")
			.set("legacy:hasStatus", Json.object(
					"type", "legacy:Status",
					"iri", "http://www.miamidade.gov/cirm/legacy#O-OPEN"))
			.set("caseSensitive", false)
			.set("sortDirection", "desc")
			.set("currentPage", 1)
			.set("type", "legacy:ServiceCase")
			.set("itemsPerPage", 500)
			.set("hasDateCreated", "between(" + from + "," + to + ")");
		Json R = e.lookupAdvancedSearch(crit);
		if (R.is("ok", false))
			return R;
		crit.at("legacy:hasStatus").set("iri", "http://www.miamidade.gov/cirm/legacy#X-ERROR");
		Json R2 = e.lookupAdvancedSearch(crit);
		if (R2.is("ok", false))
			return R2;
		R.at("resultsArray").with(R2.at("resultsArray"));
		Json A = Json.array();		
		for (Json sr : R.at("resultsArray").asJsonList())
		{
			Json x = Json.object("type", sr.at("type"), 
							     "hasCaseNumber", sr.at("hasCaseNumber"),
							     "hasDateCreated", sr.at("hasDateCreated"),
							     "hasStatus", sr.at("hasStatus"),
							     "hasLegacyInterface", null);
			for (OWLNamedIndividual external: OWL.objectProperties(
					OWL.individual("legacy:" + sr.at("type").asString()), "legacy:hasLegacyInterface"))
			{
			    if (OWL.objectProperties(external, "legacy:hasAllowableEvent").contains(OWL.individual("legacy:NEWSR")))
			    	x.set("hasLegacyInterface", external.getIRI().getFragment());
			}	
			if (!x.is("hasLegacyInterface", null))
				A.add(x);
		}
		return ok().set("results", A).set("from", from).set("to", to);
	}
	
	/**
	 * Checks if a particular SR is present in the WCS system or not by examining 
	 * the flex question for a WCS complaint or enforcement number.  
	 * @param serviceCase
	 * @return
	 */
	public boolean ensureCaseAlreadyAtDepartment(Json serviceCase, Json departmentInterfaceCode)
	{
		
		// the following should apply only to WCS SR types, and all WCS SR types
		Json deptinterface = departmentInterfaceCode; 
				//
		//System.out.println("serviceCase: "+serviceCase);
		Json statusJson = serviceCase.at("properties").at("legacy:hasStatus");
		//2014.05.02 hilpold status failed if string
		String status = statusJson.isObject()? statusJson.at("iri").asString() : statusJson.asString();
		if (deptinterface != null && 
			deptinterface.isObject() && 
			((deptinterface.is("hasLegacyCode", "MD-WCS")||
				 deptinterface.is("hasLegacyCode", "MD-WCSL"))))
			{
				Json ServiceCaseAnswers = serviceCase.at("properties").at("legacy:hasServiceAnswer");
				for (Json serviceCaseAnswersVal : ServiceCaseAnswers.asJsonList())
				{
					String serviceCaseAnswer = serviceCaseAnswersVal.at("legacy:hasServiceField").at("iri").asString();
					if (serviceCaseAnswer.endsWith("BULKYTRA_COMPLAIN")	|| 
						serviceCaseAnswer.endsWith("BULKYTRA_BULKYWOR")||
						serviceCaseAnswer.endsWith("MISSEDBU_PUBCMPLN")||
						serviceCaseAnswer.endsWith("EZGO_PUBCMPLN")||
						serviceCaseAnswer.endsWith("GARBAGEM_PUBCMPLN"))
					{
						if(!serviceCaseAnswersVal.at("legacy:hasAnswerValue").at("literal").asString().equalsIgnoreCase(""))
							return true;
					}
				}
				if (status.endsWith("O-LOCKED"))
					return false;
			}
		
		return status.endsWith("O-LOCKED");	
	}
	
	public Json sendNewActivity(Json serviceCase, Json activity)
	{
		Json caseNumber = serviceCase.at("properties").has("hasCaseNumber") ?
				serviceCase.at("properties").at("hasCaseNumber") :
					serviceCase.at("properties").at("legacy:hasCaseNumber");
		Json data = Json.object("boid", serviceCase.at("boid"),
								"type", serviceCase.at("type"),
								"iri", serviceCase.at("iri"),
//								"properties", Json.object(
									"hasCaseNumber", caseNumber,
									"hasServiceActivity", Json.array(activity)
//								)
								);
		OWL.unprefix(data);
		System.out.println("Send new activity: " + data);
		String type = "legacy:" + data.at("type").asString();
		Set<OWLNamedIndividual> interfaces = reasoner().getInstances(
				and(owlClass("legacy:LegacyInterface"),
						has(objectProperty("legacy:hasAllowableEvent"),
								individual("legacy:NEWSR")),
						has(objectProperty("legacy:isLegacyInterface"),
								individual(type))), false).getFlattened();
		if (interfaces.isEmpty())
		{
			srStatsReporter.failed("sendNewActivity", serviceCase, "No interface found for type", "not sending, responding ok().");
			return ok();
		}
		data.set("hasLegacyInterface", OWL.toJSON(interfaces.iterator().next()));
		try
		{
			ThreadLocalStopwatch.now("DepartmentIntegration.sendNewActivity actually sending activity through JMS");
			JMSClient.connectAndSend(LegacyMessageType.NewActivity, 
					((DBIDFactory) Refs.idFactory.resolve()).generateSequenceNumber(), data);
			srStatsReporter.succeeded("sendNewActivity", serviceCase);
			return ok();
		}
		catch (JMSException ex)
		{
			srStatsReporter.failed("sendNewActivity", serviceCase, ex.toString(), ex.getMessage());
			Refs.logger.resolve().log(Level.SEVERE, "While sending activity for case " + 
					serviceCase.at("properties").at("hasServiceCase") + " to departments.", ex);
			return ko(ex.toString());
//				this.delaySendNewActivity(serviceCase, activity, 60);				
		}
	}
	
	/**
	 * Schedules a time machine task to send an activity to an interface later.
	 * @param serviceCase
	 * @param activity
	 * @param minutes
	 * @return
	 */
	public Json delaySendNewActivity(Json serviceCase, Json activity, int minutes)
	{
		Json type = OWL.toJSON(OWL.individual(serviceCase.at("type").asString()));
		Json deptinterface = this.getDepartmentInterfaceCode(serviceCase.at("type").asString());
		Json statusJson = serviceCase.at("properties").at("legacy:hasStatus");
		String status = statusJson.isObject()? statusJson.at("iri").asString() : statusJson.asString();
		// Activities are not sent separately, at least for PWS, until the case has made it 
		// there already. This is because a "personal contact" activity can be added right
		// after case creation and before the delay when it is sent to the PWS system.
		// 
		if (deptinterface != null && 
			deptinterface.isObject() && 
			(deptinterface.is("hasLegacyCode", "MD-PWS") &&	status.contains("O-") 
					|| deptinterface.is("hasLegacyCode", "COM-CITY")))
			return ok();
		if (!type.has("hasAnswerUpdateTimeout"))
			type = OWL.toJSON(OWL.individual("legacy:ServiceCase"));
		//TODO hilpold ?? of ServiceCase individual???
		if (!type.has("hasAnswerUpdateTimeout"))
		{
			this.sendNewActivity(serviceCase, activity);
			return ok();
		}		
		int delayInMinutes = (minutes > 0 ? minutes : type.at("hasAnswerUpdateTimeout").at("hasValue").asInteger()) + 5;
		String taskUrl = "/legacy/departments/sendnewactivity/" + serviceCase.at("boid").asString() + 
  							"/" + GenUtils.parseDate(activity.at("hasDateCreated").asString()).getTime()+
  							"/" + delayInMinutes;
		return ok().set("task", GenUtils.timeTask(delayInMinutes, taskUrl, null));
	}
	
	
	/**
	 * Sends a new activity, created in cirm to a department.
	 * If the case is not there yet, delaySendNewActivity will schedule a time machine callback as follows:
	 * 1. 2 retries every 1 hour (60, 61, 62) minutes ((6)1 = first retry, (6)2 = second retry
	 * nrOfRetries = curMin - 60; 
	 * 2. 42 retries every 12 hours covering 21 days (3 weeks time to resolve X-Error issue)
	 * (720, 721, ..., 761 (=41st), 762 (=42nd))
	 * nrOfRetries = curMin - 720
	 * @param serviceCase
	 * @return
	 */
	public Json tryActivitySend(Json serviceCase, Json activity, int minutes)
	{
		Json result;
		String caseType = serviceCase.at("type").asString();
		Json departmentInterfaceCode = getDepartmentInterfaceCode(caseType);
		//hilpold check if case type is interface type
		if (departmentInterfaceCode == null || departmentInterfaceCode.isNull()) 
		{
			ThreadLocalStopwatch.now("tryActivitySend returning ko: activity is not of an interface type: " + caseType + " act: " + activity);
			return ko("activity is not of an interface type: " + caseType + " act: " + activity);			
		}
		ThreadLocalStopwatch.start("START tryActivitySend");
		if (ensureCaseAlreadyAtDepartment(serviceCase, departmentInterfaceCode)) 
		{
			ThreadLocalStopwatch.now("case is at department -> sendNewActivity");
			result = sendNewActivity(serviceCase, activity);
			if(result.is("ok", true)) {
				srStatsReporter.succeeded("tryActivitySend-case at department", serviceCase);
			} else
			{
				srStatsReporter.failed("tryActivitySend-case at department", serviceCase, "" + result.at("error"), "sendNewActivity failed");
			}
			ThreadLocalStopwatch.stop("END tryActivitySend");
			return result;
		}
		Json statusJson = serviceCase.at("properties").at("legacy:hasStatus");
		//2014.05.02 hilpold status failed if string
		String status = statusJson.isObject()? statusJson.at("iri").asString() : statusJson.asString();
		//bad: String status = serviceCase.at("properties").at("legacy:hasStatus").at("iri").asString();
		
		// If the case should eventually make into the departmental system
		if (status.endsWith("O-OPEN") ||
			status.endsWith("X-ERROR")||
			status.endsWith("O-LOCKED"))		
		{
			return scheduleNextSendNewActivityRetry(serviceCase, activity, minutes);
		}
		else // case is not in department and will never make it there
		{
			srStatsReporter.failed("tryActivitySend-case", serviceCase, "Will never make it to department", "no delaySendNewActivity called");
			// do nothing
			ThreadLocalStopwatch.fail("FAIL tryActivitySend case is not at department and will never make it there, returning ok");
		}
		return ok();
	}
	
	public Json scheduleNextSendNewActivityRetry(Json serviceCase, Json activity, int curMinutes) {
		// then delay send of activity
		Json result;
		int nextMinutes;
		if (curMinutes >= -1 && curMinutes < 60)
			nextMinutes = 60;
		else if (curMinutes >= 60 && curMinutes < 180)
			nextMinutes = 3*60;
		else if (curMinutes >= 180 && curMinutes <4320)
			nextMinutes = 72*60;
		else // (curMinutes>4320)
		{
			//TODO hilpold retry more often e.g. every 12 hours for 21 days- X-ERROR might need more time for fixing 
			//eventually the retry is stopped after 3 unsuccessful attempts.
			srStatsReporter.failed("tryActivitySend-case", serviceCase, "Giving up retry after ", "sendNewActivity failed");
			ThreadLocalStopwatch.fail("FAIL tryActivitySend retry is stopped after 3 unsuccessful attempts, returning ko");
			return ko("tryActivitySend failed 3 times, giving up.");
		}			
		result = delaySendNewActivity(serviceCase, activity, nextMinutes);
		ThreadLocalStopwatch.stop("End tryActivitySend delaySendNewActivity callback requested in " + nextMinutes);
		return result;
	}
	
//	public boolean areShortIntervalRetriesExhausted(int minutes) {
//		
//	}
//
//	public boolean areLongIntervalRetriesExhausted(int minutes) {
//		
//	}
//
//	/**
//	 * Calculates the total number of retries encoded in the minutes value.
//	 *
//	 * 0..for any minute value <= RETRY_INTERVAL_SHORT
//	 * 0..for any minute value == RETRY_INTERVAL_LONG
//	 * N..(minutes >= RETRY_INTERVAL_LONG)? minutes - RETRY_INTERVAL_LONG
//	 *  
//	 * @param minutes
//	 * @return
//	 */
//	public int decodeNrOfRetries(int minutes) {
//		
//	}
}

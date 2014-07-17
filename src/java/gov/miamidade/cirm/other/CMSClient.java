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

import gov.miamidade.cirm.MDRefs;

import java.sql.CallableStatement;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import mjson.Json;
import static org.sharegov.cirm.utils.GenUtils.*;

import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.gis.GisException;
import org.sharegov.cirm.rdb.DBU;
import org.sharegov.cirm.rdb.DataSourceRef;
import org.sharegov.cirm.rest.LegacyEmulator;
import org.sharegov.cirm.rest.RestService;
import org.sharegov.cirm.stats.CirmStatisticsFactory;
import org.sharegov.cirm.stats.SRCirmStatsDataReporter;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.Ref;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

/**
 * Interface client for CMS/RER
 * Supported actions:
 * 1. Receive new case
 * 2. Receive new activity
 * 3. Receive data update
 * 4. Respond to receive
 * 
 * @author boris, Thomas Hilpold
 *
 */
@Path("other/cms")
@Produces("application/json")
@Consumes("application/json")
public class CMSClient extends RestService
{
	final static String RESPONSE_INPROGRESS_OK = "OK"; //always first response before OK CIRM or REJECTED
	final static String RESPONSE_OK_CIRM = "OK CIRM";
	final static String RESPONSE_REJECTED = "REJECTED";
	
	final static AtomicInteger nrOfSuccessNewCase = new AtomicInteger();
	final static AtomicInteger nrOfSuccessUpdateActivity = new AtomicInteger();
	final static AtomicInteger nrOfSuccessUpdateCase = new AtomicInteger();
	
	final static ReentrantLock STATIC_LOCK = new ReentrantLock();
	
	private SRCirmStatsDataReporter srStatsReporter = 
			CirmStatisticsFactory.createServiceRequestStatsReporter(MDRefs.mdStats.resolve(), "CMSClient"); 
	
	static final String CMSDATEFORMAT = "yyyy-MM-dd hh:mm:ss";
	
	boolean dryrun = false;	
	private LegacyEmulator emulator = new LegacyEmulator();
	
	static Ref<DataSourceRef> cms = new Ref<DataSourceRef> () {
		public DataSourceRef resolve()
		{
			OWLNamedIndividual cmsDS = Refs.configSet.resolve().get("CMSDataSource");
			return new DataSourceRef(cmsDS.getIRI());
		}
	};
		
	Json getEventRecord(int eventid)
	{
		Connection conn = null;
		Statement stmt = null;
		ResultSet rs = null;
		Json result; 
		try
		{
			conn = cms.resolve().resolve().getConnection();
			stmt = conn.createStatement();
			rs = stmt.executeQuery("select * from tmmt_csrout where cms_event_eid=" + eventid);
			if (rs.next()) {
				result = DBU.rowToJson(rs);
				srStatsReporter.succeeded("getEventRecord", "CMS-EVENT", "" + eventid);
			}
			else 
			{
				srStatsReporter.failed("getEventRecord", "CMS-EVENT", "" + eventid, "not found in CMS db", "");
				result = Json.nil();
			}
		}
		catch (Exception ex)
		{
			srStatsReporter.failed("getEventRecord from CMS", "CMS-EVENT", "" + eventid, "" + ex, ex.getMessage());
			throw new RuntimeException(ex);
		}
		finally
		{
			DBU.close(conn, stmt, rs);
		}
		return result;
	}
	
	Json getReadyEvents(int max)
	{
		Connection conn = null;
		CallableStatement cs = null;
		ResultSet rs = null;
		try
		{
			conn = cms.resolve().resolve().getConnection();
			cs = conn.prepareCall("{ call tmmpk_cirmout.csr_send( ? ) }");
			cs.registerOutParameter(1, oracle.jdbc.OracleTypes.CURSOR);
			cs.executeQuery();
			rs = (ResultSet) cs.getObject(1); // <- casts
			Json A = Json.array();
			if (rs != null) 
			{
				while (rs.next() && max-- > 0)
				{
					A.add(DBU.rowToJson(rs));
				}
			}
			return A;
		}
		catch (SQLException sqle)
		{
			if (sqle.getErrorCode() == 20200 || sqle.toString().contains("Cursor is closed."))
				return Json.array();
			else
				throw new RuntimeException(sqle);
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
		finally
		{
			DBU.close(conn, cs, rs);
		}
	}

	/**
	 * Marks an event as processed in CMS.
	 * @param eventid
	 * @param caseNumber
	 * @param status
	 * @param error
	 * @return
	 */
	Json markProcessed(int eventid, String caseNumber, String status, String error)
	{
		if (dryrun)
		{
			System.out.println("MARK PROCESSED: " +eventid + "," + status + ", " + error);
			return ok();
		}
		if (!RESPONSE_INPROGRESS_OK.equals(status))
			markProcessed(eventid, caseNumber, RESPONSE_INPROGRESS_OK, "");
		Connection conn = null;
		CallableStatement stmt = null;
		try
		{
			conn = cms.resolve().resolve().getConnection();
			stmt = conn.prepareCall("{ call tmmpk_cirmout.csr_send_response(?, ?, ?, ?) }");
			stmt.setInt(1, eventid);
			stmt.setString(2, caseNumber);		
			stmt.setString(3, status);		
			stmt.registerOutParameter(3, java.sql.Types.VARCHAR); 
			stmt.setString(4, error);
			stmt.registerOutParameter(4, java.sql.Types.VARCHAR); 	
			stmt.executeQuery();
			status = stmt.getString(3).toUpperCase();
			error = stmt.getString(4);
			if ("OK".equalsIgnoreCase(status))
			{
				ThreadLocalStopwatch.now("CMSClient mark processed ok: " + "eventId:" + eventid + " Case: " + caseNumber + " " + status);
				return ok();
			}
			else
			{
				ThreadLocalStopwatch.error("FAIL: CMSClient mark processed ko: " + "eventId:" + eventid + " Case: " + caseNumber + " " + status + " error " + error);
				return ko(error);
			}
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
		finally
		{
			DBU.close(conn, stmt, null);
		}
	}
	
	/**
	 * Applies event to CiRM, persisting a new SR, adding one new activity or updating SR data
	 * @param event
	 * @return
	 */
	Json processInCiRM(final Json event)
	{
		return Refs.defaultRelationalStore.resolve().txn(new CirmTransaction<Json>() {
		public Json call()
		{						
			String caseNumber = "";
			Json result = ok();			
			if (event.is("CSR_ACTION", "XNEWSR"))
			{
				result = cirmHandleXNewSrCMSEvent(event);
				if (result.is("ok", true))
					caseNumber = result.at("data").at("hasCaseNumber").asString();
			}
			else
			{   //
				String srnum = event.at("SR_NUM").asString();
				if (!srnum.contains("-"))
				{
					Json srtype = DBU.collectOne(cms, "select * from tmmt_csrout where case_num=" +
							srnum + " and sr_type is not null");
					if (srtype.isNull())
						return ko("Could not find case type.");
					Json queryByCaseNum = Json.object()
					      .set("type", "legacy:" + srtype.at("SR_TYPE").asString())
					      .set("http://www.miamidade.gov/cirm/legacy#" + srtype.at("SR_TYPE").asString() + "_CASENUM", Json.object()
					        .set("datatype", "http://www.w3.org/2001/XMLSchema#string")
					        .set("literal", "\"" + srnum + "\""))
					    ;
					 Json R = emulator.lookupAdvancedSearch(queryByCaseNum);    
					 if (R.at("totalRecords").asLong() == 1l)
					 {
						 srnum = R.at("resultsArray").at(0).at("hasCaseNumber").asString();
					 }
					 else
					 {	
						 return ko("Could not find SR number by CMS case: " + srnum);
					 }
				}
				Json sr = emulator.lookupByCaseNumber(srnum); 
				if (sr.isNull())
					result = ko("Case not found.");
				else if (sr.is("ok", false))
					result = sr;
				else
				{
					sr = sr.at("bo");
					caseNumber = sr.at("properties").at("hasCaseNumber").asString();
					if (event.is("CSR_ACTION", "ACTIVITY"))
						result = cirmHandleActivityCMSEvent(sr, event);			
					else if (event.is("CSR_ACTION", "UPDATE"))
						result = cirmHandleUpdateCMSEvent(sr, event);					
				}
			}
			// So we have an issue here, even though we can repeat the markProcessed
			// success action as many times as we like in case of a serialization failure
			// that eventually succeeds. The problem is timing: our CiRM transaction may
			// end up failing while we mark it as success at CMS. The CiRM tx eventually
			// succeeds so the success status is ok, however in the meantime we may get 
			// other CMS updates about this particular SR that fail because it is not visible
			// yet in CiRM. To prevent this type of situation, we have to have the general
			// distributed tx solution. Or have some CMS specific, ad hoc synchronization....
			result.set("caseNumber", caseNumber);
			return result;
		}});		
	}
	
	Json markProcessed(Json event, Json result)
	{
		if (result.is("ok", false)) 
		{
//			return markProcessed(event.at("CMS_EVENT_EID").asInteger(), 
//					result.at("caseNumber").asString(), "FAILED", result.at("error").asString());
			return markProcessed(event.at("CMS_EVENT_EID").asInteger(), 
					result.at("caseNumber").asString(), RESPONSE_REJECTED, result.at("error").asString());
		}
		else
		{
			return markProcessed(event.at("CMS_EVENT_EID").asInteger(), 
					result.at("caseNumber").asString(), 
								 RESPONSE_OK_CIRM, 
								 "");
		}
	}
	
	Json processEvent(final Json event)
	{
		try
		{
			Json result = processInCiRM(event); //if this returns ko, we still report it as success in stats; response REJECTED			
			return markProcessed(event, result);			
		}
		catch (Exception ex)
		{
			boolean retry = false;
			for (Throwable e = ex; e != null; e = e.getCause())
				if (e instanceof GisException)
					retry = true;
			// We have to retry, so we don't mark that event as processed and it will be picked up again!
			if (retry)
				return ko(ex.toString()).set("retry", true);
			else 
			{
				ex.printStackTrace(System.err);
//				return markProcessed(event.at("CMS_EVENT_EID").asInteger(), 
//						"", 
//						 "ERROR", 
//						 ex.toString());
				return markProcessed(event.at("CMS_EVENT_EID").asInteger(), 
						"", 
						 RESPONSE_REJECTED, 
						 ex.toString());
			}
		}
	}
	
	public Json cirmHandleActivityCMSEvent(Json sr, Json event)
	{
		Json current = sr.dup();		
		Json a = Json.object("type", "ServiceActivity");
		a.set("hasActivity", Json.object("type", "Activity", 
				"hasLegacyCode", event.at("ACTV_CODE").asString()));
		a.set("hasDateCreated", GenUtils.formatDate(
				new java.util.Date(Long.parseLong(event.at("ACTV_CREATED_DATE").asString()))));
		if (!event.at("ACTV_DATE").isNull())
		{
			a.set("hasCompletedTimestamp",GenUtils.formatDate(
				new java.util.Date(Long.parseLong(event.at("ACTV_DATE").asString())))); 
			a.set("hasOutcome", Json.object("type", "Outcome", 
											"hasLegacyCode", "OUTCOME_COMPLETE"));
		}
		if (!event.at("ACTV_OUTCOME").isNull())
			a.set("hasOutcome", Json.object("type", "Outcome", 
											"hasLegacyCode", event.at("ACTV_OUTCOME").asString()));		
		if (!event.at("ACTV_DETAILS").isNull())
			a.set("hasDetails", event.at("ACTV_DETAILS").asString());
		ServiceCaseJsonHelper.insertIriFromCode(a.at("hasActivity"), sr.at("type").asString() + "_");
		ServiceCaseJsonHelper.insertIriFromCode(a.at("hasOutcome"), "");		
		sr.at("properties")
		   .set("isModifiedBy", "department")
		   .at("hasServiceActivity").add(a);	
		if (dryrun)
		{
			System.out.println("add activity " + a + " to case " + sr);
			return ok();
		}		
		ServiceCaseJsonHelper.assignIris(sr);		
		OWL.resolveIris(sr.at("properties"), null);		
		Json result = emulator.updateServiceCaseTransaction(sr, 
													 current, 
													 new java.util.Date(), 
													 null, 
													 "department");
		nrOfSuccessUpdateActivity.incrementAndGet();
		if (result.is("ok", true)) 
		{
			srStatsReporter.succeeded("updateActivity", sr);
		}
		else
		{
			srStatsReporter.failed("updateActivity", sr, "updateServiceCaseTransaction failed", "");
		}
		return result;
	}

	void populateCaseFromEvent(Json sr, Json event)
	{
		Json props = sr.at("properties");
		if (!event.at("SR_PRIORITY").isNull())
			props.set("hasPriority", Json.object("iri", OWL.fullIri("legacy:" + 
				(event.at("SR_PRIORITY").isNull() ? "STANDARD" : event.at("SR_PRIORITY").asString())).toString()));
		if (!event.at("SR_DETAILS").isNull())		
			props.set("hasDetails", event.at("SR_DETAILS"));		
		if (!event.at("SR_STATUS").isNull())
			props.set("hasStatus", 
				Json.object("iri", OWL.fullIri("legacy:" + event.at("SR_STATUS").asString()).toString()));		
		if (!event.at("SR_X_COORD").isNull() &&			
			!event.at("SR_Y_COORD").isNull() && 
			(!props.has("hasXCoordinate") || 
			 event.at("SR_X_COORD").asDouble() != props.at("hasXCoordinate").asDouble()||
			 !props.has("hasYCoordinate") ||
			 event.at("SR_Y_COORD").asDouble() != props.at("hasYCoordinate").asDouble()))
		{
			props.set("hasXCoordinate", event.at("SR_X_COORD").asDouble()); 
			props.set("hasYCoordinate", event.at("SR_Y_COORD").asDouble());
			Json address = ServiceCaseJsonHelper.reverseGeoCode(event.at("SR_X_COORD").asDouble(), event.at("SR_Y_COORD").asDouble());
			if (!address.isNull())
			{
				props.set("atAddress", address.set("type", "Street_Address"));
			}
		}
		if (!event.at("FQ_STRING").isNull())
		{
			Json answers = props.at("hasServiceAnswer", Json.array());			
			StringTokenizer attributes = new StringTokenizer(event.at("FQ_STRING").asString(), "|");
			while (attributes.hasMoreTokens())
			{
				String att = attributes.nextToken();
				String code = att.substring(0, att.indexOf("="));
				if (code.length() == 0) continue; 
				code = code.trim();
				String fieldIri = OWL.fullIri("legacy:" + sr.at("type").asString() + "_" + code).toString();
				String value = att.substring(att.indexOf("=") + 1);
				if (value.isEmpty() || "NA".equals(value) || "$ET2NULL".equals(value)) 
				{
					//2014.07.08 Issue 1010  CMS cannot collapse problem onto transformer fix
					ServiceCaseJsonHelper.removeAnswerFieldByIri(answers, fieldIri);
				}
				else 
				{
					Json ans = Json.object()
							.set("type", "legacy:ServiceAnswer")
							.set("legacy:hasServiceField", Json.object("iri", fieldIri))
							.set("legacy:hasAnswerValue", value);
					int existing = ServiceCaseJsonHelper.findAnswerByField(answers, fieldIri);
					if (existing > -1)
						answers.delAt(existing);
					answers.add(ans);					
				}
			}
			ServiceCaseJsonHelper.replaceAnswerLabelsWithValues(answers);
		}
		if (!event.at("PART_TYPE").isNull())
		{
			Json actor = Json.object()
					.set("type", "ServiceCaseActor") 
					.set("hasServiceActor", Json.object("hasLegacyCode", event.at("PART_TYPE")));
			if (!event.at("PART_FIRST_NAME").isNull())
				actor.set("Name", event.at("PART_FIRST_NAME"));
			if (!event.at("PART_LAST_NAME").isNull())			
				actor.set("LastName", event.at("PART_LAST_NAME"));
			if (!event.at("PART_PHONE").isNull())			
				actor.set("HomePhoneNumber", event.at("PART_PHONE").asString() + 
						(event.at("PART_EXT").isNull() ? "" : "#" + event.at("PART_EXT").asString()));
			if (!event.at("PART_FAX").isNull())			
				actor.set("FaxNumber", event.at("PART_FAX"));
			if (!event.at("PART_EMAIL").isNull())			
				actor.set("hasEmailAddress", 
					Json.object("iri", "mailto:" + event.at("PART_EMAIL").asString(),
							    "label", event.at("PART_EMAIL").asString(),
							    "type", "EmailAddress"));
			Json aaddr = Json.object();
			if (!event.at("PART_ST_STATE").isNull())
			{
				Json stateobject = ServiceCaseJsonHelper.findUSStateObject(event.at("PART_ST_STATE").asString());
				if (!stateobject.isNull())
					aaddr.set("Street_Address_State", stateobject).set("type", "Street_Address");
			}
			if (!event.at("PART_ST_NAME").isNull())
				aaddr.set("fullAddress", event.at("PART_ST_NAME")).set("type", "Street_Address");
			if (!event.at("PART_ST_ZIP").isNull())
				aaddr.set("Zip_Code", event.at("PART_ST_ZIP")).set("type", "Street_Address");
			if (!event.at("PART_ST_CITY").isNull())
			{
				Json cityobject = ServiceCaseJsonHelper.findCityObject(event.at("PART_ST_CITY").asString());
				if (!cityobject.isNull())
					aaddr.set("Street_Address_City", cityobject).set("type", "Street_Address");
			}
			if (aaddr.has("type"))
				actor.set("atAddress", aaddr);
			ServiceCaseJsonHelper.insertIriFromCode(actor.at("hasServiceActor"), "");			
			props.set("hasServiceCaseActor", Json.array(actor));	
		}		
	}
	
	public Json cirmHandleUpdateCMSEvent(Json sr, Json event)
	{
		Json current = sr.dup();
		populateCaseFromEvent(sr, event);
		java.util.Date updatedDate = event.at("STATUS_DATE").isNull() ? 
			new java.util.Date() : new java.util.Date(Long.parseLong(event.at("STATUS_DATE").asString()));
		ServiceCaseJsonHelper.cleanUpProperties(sr);			
		ServiceCaseJsonHelper.assignIris(sr);		
		OWL.resolveIris(sr.at("properties"), null);		
		System.out.println("update case " + sr + ", updatedDate=" + updatedDate);
		if (dryrun)
		{
			return ok();
		}
		Json result = emulator.updateServiceCaseTransaction(sr, 
													 current, 
													 updatedDate, 
													 null, 
													 "department");
		nrOfSuccessUpdateCase.incrementAndGet();
		if (result.is("ok", true)) 
		{
			srStatsReporter.succeeded("updateData", sr);
		}
		else
		{
			srStatsReporter.failed("updateData", sr, "updateSR failed", "");
		}
		return result;
	}
	
	/**
	 * Inserts a new case retrieved by CMS into CiRM. 
	 * @param event
	 * @return
	 */
	public Json cirmHandleXNewSrCMSEvent(Json event)
	{
		final Json sr = Json.object("type", event.at("SR_TYPE").asString(), "properties", Json.object());
		Json props = sr.at("properties");
		props.set("legacy:hasIntakeMethod", Json.object("iri", OWL.fullIri("legacy:" + 
				(event.at("SR_METHOD_RECEIVED").isNull() ? "XTERFACE" : event.at("SR_METHOD_RECEIVED").asString())).toString()));		
		props.set("hasDateCreated", event.at("SR_CREATED_DATE").isNull() ? 
				GenUtils.formatDate(new java.util.Date()) : 
				GenUtils.formatDate(new java.util.Date(Long.parseLong(event.at("SR_CREATED_DATE").asString()))));  
		props.set("legacy:hasStatus", Json.object("iri", OWL.fullIri("legacy:" + 
				(event.at("SR_STATUS").isNull() ? "O-LOCKED" : event.at("SR_STATUS").asString())).toString()));
		if (!event.at("STATUS_DATE").isNull())
			props.set("hasDateLastModified", 
				GenUtils.formatDate(new java.util.Date(Long.parseLong(event.at("STATUS_DATE").asString()))));
		populateCaseFromEvent(sr, event);
		ServiceCaseJsonHelper.assignIris(sr);
		System.out.println("insert new case " + sr);		
		if (dryrun)
		{
			return ok();
		}		
		Json result =  emulator.saveNewCaseTransaction(sr);
		nrOfSuccessNewCase.incrementAndGet();
		if (result.is("ok", true)) 
		{
			srStatsReporter.succeeded("insertCase", sr);
		}
		else
		{
			srStatsReporter.failed("insertCase", sr, "updateSR failed", "" + sr);
		}
		return result;		
	}
	
	@GET
	@Path("/event/{eventid}")
	public Json getEvent(@PathParam("eventid") int eventid)
	{
		forceClientExempt.set(true);
		return ok().set("event", getEventRecord(eventid));
	}
	
	@POST
	@Path("/event/process/{eventid}")
	public Json process(@PathParam("eventid") int eventid)
	{
		forceClientExempt.set(true);
		Json event = getEventRecord(eventid); //TODO VALIDATE INPUT FROM CMS
		if (event.isNull())
			return ko("Event not found");
		return processEvent(event);
	}
	
	@POST
	@Path("/event/processBatch/{count}")
	public synchronized Json processBatchSynced(@PathParam("count") int batchSize) 
	{
		boolean noOtherThreadExecuting = STATIC_LOCK.tryLock();
		if (noOtherThreadExecuting) {
			//I'm the only thread processingBatch in any CMSClient object in this vm right now.
			try {
				return processBatch(batchSize);
			}
			finally
			{
				STATIC_LOCK.unlock();
			}
		} else
		{
			ThreadLocalStopwatch.fail("CMSClient: processBatchSynced problem: another thread is still executing.");
			return ko("Another thread was still batch processing.");
		}
	}
	
	//@POST
	//@Path("/event/processBatch/{count}")
	public synchronized Json processBatch(@PathParam("count") int batchSize)
	{
		ThreadLocalStopwatch.startTop("START CMSClient /event/processBatch/ batchSize: " + batchSize);
		long startMs = System.currentTimeMillis();
		forceClientExempt.set(true);
		Json events = getReadyEvents(batchSize);
		int successCount = 0;
		List<Json> eventList = events.asJsonList();
		int totalCount = eventList.size();
		for (Json event : eventList)
		{
			Json r = processEvent(event); 
			for (int retryCount = 1; retryCount < 5 && r.is("ok", false) && r.is("retry", true); retryCount++)
			{
				GenUtils.sleep(5000); // TODO: magic number
				r = processEvent(event);
			}
			if (r.is("ok", false)) {
				srStatsReporter.failed("/event/processBatch/", "CMS-EVENT", "" + event, "processEvent", "" + r.at("error"));
				ThreadLocalStopwatch.fail("FAIL CMSClient processAllEvents total: " + totalCount + " success: " + successCount);
				return ko("Error " + r.at("error") + 
					" while processing event " + event).set("successCount", successCount);
			}
			else
			{
				successCount++;
				srStatsReporter.succeeded("/event/processBatch/", "CMS-EVENT", "" + event);
			}
		}
		double durationSecs = (((System.currentTimeMillis() - startMs) / 100) / 10.0); //10th of sec precision
		printCounts();
		ThreadLocalStopwatch.stop("END CMSClient processAllEvents total: " + totalCount + " success: " + successCount);
		return ok().set("successCount", successCount).set("durationSecs", durationSecs);
	}
	
	private void printCounts() 
	{
		ThreadLocalStopwatch.now("CMSClient stats Newcase: " + nrOfSuccessNewCase.get() + " UpdateCase: " + nrOfSuccessUpdateCase.get() + " UpdateActivity: " + nrOfSuccessUpdateActivity.get());
	}
	
	public static void main(String argv[])
	{
		OWLReasoner r = OWL.reasoner();
		System.out.println(r.getInstances(OWL.owlClass("State__U.S._"), false));
		//System.out.println(r.getInstances(OWL.and(OWL.owlClass("State__U.S._"),OWL.has(OWL.dataProperty("USPS_Abbrevation"), OWL.literal("FL"))), false));
	}
}

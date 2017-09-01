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

import static mjson.Json.array;
import static mjson.Json.object;
import static org.sharegov.cirm.OWL.fullIri;
import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.owlClass;
import static org.sharegov.cirm.rest.OperationService.getPersister;
import static org.sharegov.cirm.utils.GenUtils.ko;
import static org.sharegov.cirm.utils.GenUtils.ok;
import static org.sharegov.cirm.utils.GenUtils.trim;
import static org.sharegov.cirm.utils.GenUtils.formatDate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

import javax.ws.rs.Consumes;
import javax.ws.rs.Encoded;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import mjson.Json;

import org.restlet.Response;
import org.restlet.representation.Representation;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.legacy.ActivityManager;
import org.sharegov.cirm.legacy.CirmMessage;
import org.sharegov.cirm.legacy.MessageManager;
import org.sharegov.cirm.legacy.Permissions;
import org.sharegov.cirm.rdb.GenericStore;
import org.sharegov.cirm.rdb.Query;
import org.sharegov.cirm.rdb.QueryTranslator;
import org.sharegov.cirm.rdb.RelationalStore;
import org.sharegov.cirm.rest.LegacyEmulator;
import org.sharegov.cirm.rest.RestService;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

@Path("asd")
@Produces("application/json")
public class ASDService extends RestService
{

	@GET
	@Path("/query")
	@Produces("application/json")
	public Json getASDInfo(@QueryParam("arg") String search)
	{

		Json searchCriteria = Json.read(search);
		StringBuilder criteria = new StringBuilder(" where ");
		for (Entry<String, Object> entry : searchCriteria.asMap().entrySet())
		{
			if (entry.getValue() == null
					|| entry.getValue().toString().isEmpty())
				continue;
			if (entry.getKey().startsWith("tag"))
				criteria.append("t." + entry.getKey());
			else if (entry.getKey().startsWith("animal"))
				criteria.append("a." + entry.getKey());
			else
				criteria.append("p." + entry.getKey());
			criteria.append(" = ");
			criteria.append("'" + entry.getValue().toString() + "'");
			criteria.append(" and ");
		}

		Connection conn = null;
		ResultSet rs = null;
		Json result = object();
		StringBuilder query = null;

		try
		{
			conn = GenericStore.getASDInstance().getASDSQLPooledConnection();
			query = new StringBuilder();
			query.append("select p.first_name, p.last_name, p.phone_area_code, p.phone_number, ");
			query.append("p.street_no, p.street_dir, p.street_name, p.street_type, p.city, p.state, p.zip_code, ");
			query.append("t.tag_no, t.tag_exp, t.vaccine, t.vac_date, t.vac_exp, ");
			query.append("a.animal_name, a.animal_type, a.primary_breed, t.tag_stat ");
			query.append("from sysadm.person p, sysadm.tag t, sysadm.animal a ");
			query.append(criteria.toString());
			query.append("p.person_id = t.person_id and t.animal_id = a.animal_id ");
			query.append("order by t.tag_exp desc");

			rs = conn.createStatement().executeQuery(query.toString());
			Json recordsArray = array();
			while (rs.next())
			{
				Json x = object();
				x.set("Name",
						trim(rs.getString(1)) + " " + trim(rs.getString(2)));
				x.set("Phone", trim(rs.getString(3)) + trim(rs.getString(4)));
				x.set("Address", object()
								.set("Street_Number", rs.getInt(5))
								.set("Street_Direction", trim(rs.getString(6)))
								.set("Street_Name", trim(rs.getString(7)))
								.set("hasStreetType", trim(rs.getString(8)))
								.set("Street_Address_City", trim(rs.getString(9)))
								.set("Street_Address_State", trim(rs.getString(10)))
								.set("Zip_Code", trim(rs.getString(11)))
				);
				x.set("Tag", trim(rs.getString(12)));
				x.set("TagExpires", formatDate(rs.getTimestamp(13), "MM/dd/yyyy"));
				x.set("Vaccine", trim(rs.getString(14)));
				x.set("VaccineDate", formatDate(rs.getTimestamp(15), "MM/dd/yyyy"));
				x.set("VaccineExpires", formatDate(rs.getTimestamp(16), "MM/dd/yyyy"));
				x.set("AnimalName", trim(rs.getString(17)));
				x.set("AnimalType", trim(rs.getString(18)));
				x.set("Breed", trim(rs.getString(19)));
				x.set("TagStatus", trim(rs.getString(20)));
				recordsArray.add(x);
			}
			result.set("records", recordsArray);
			return ok().set("result", result);
		}
		catch (Exception e)
		{
			System.out.println("arg passed into getASDInfo : "+search);
			e.printStackTrace();
			return ko(e.getMessage());
		}
	}

	@POST
	@Encoded
	@Path("dispatchUpdate")
	@Produces("application/json")
	@Consumes("application/json")
	public Json asdDispatchUpdateActivity(Json data)
	{
		ThreadLocalStopwatch.startTop("START asdDispatchUpdateActivity");
		try
		{
			LegacyEmulator le = new LegacyEmulator();
			Response current = Response.getCurrent();
			long boid = Long.valueOf(data.at("SR_ID").asString());
			BOntology bo = le.findServiceCaseOntology(boid);
			if (bo == null)
				return ko("Case not found.");				
			//permission check
			if (!(isClientExempt() || Permissions.check(
					individual("BO_View"), individual(bo.getTypeIRI("legacy")),
					getUserActors())))
				return ko("Permission denied.");
			Json sr = bo.toJSON();
			sr.at("properties").set("hasDateLastModified", 
					GenUtils.formatDate(new java.util.Date()));
			bo = BOntology.makeRuntimeBOntology(sr);
			sr = bo.toJSON();
			ActivityManager mngr = new ActivityManager();
			//TODO hilpold whole method should be inside a Tx and SendEmailOnTxSuccess used.
			List<CirmMessage> msgsToSend = new ArrayList<CirmMessage>();			
			Json dbActs = array();
			if(sr.at("properties").at("hasServiceActivity").isArray())
				dbActs = sr.at("properties").at("hasServiceActivity");
			else
				dbActs.add(sr.at("properties").at("hasServiceActivity"));
			for(Json dbAct : dbActs.asJsonList())
			{
				String activity = data.at("ACTIVITY_IRI").asString();
				String activityType = data.at("ACTIVITY").asString();
				if(dbAct.at("iri").asString().equals(activity))
				{
					String modifiedBy = data.at("isModifiedBy").asString();
					String details = data.at("DETAILS").isNull() ? 
							null : data.at("DETAILS").asString();
					String assignedTo = data.at("ASSIGNED_TO").isNull() ? 
							null : data.at("ASSIGNED_TO").asString();
					String outcome = data.at("OUTCOME").isNull() ? 
							null : data.at("OUTCOME").asString();
					boolean isAccepted = data.at("isAccepted").isNull() ? 
							false : data.at("isAccepted").toString().equals("true");
					//If outcome already set, then dont set it again.
					if(dbAct.has("hasOutcome"))
						mngr.updateActivity(activityType, activity, null, details, 
								assignedTo, modifiedBy, false, bo, msgsToSend);
					else
						mngr.updateActivity(activityType, activity, outcome, details, 
								assignedTo, modifiedBy, isAccepted, bo, msgsToSend);
				}
			}
			Response.setCurrent(current);
			Json result = le.updateServiceCase(bo);
			MessageManager.get().sendMessages(msgsToSend);
			ThreadLocalStopwatch.stop("END asdDispatchUpdateActivity");
			return result;
		}
		catch (Exception e)
		{
			ThreadLocalStopwatch.fail("FAIL asdDispatchUpdateActivity with " + e);
			e.printStackTrace();
			return ko(e.getMessage());
		}
	}

	@POST
	@Path("dispatch")
	@Produces("application/json")
	@Consumes("application/json")
	public Json asdDispatchLookup(Json data)
	{
		if (DBG)
			ThreadLocalStopwatch.startTop("START asdDispatchLookup");
		try
		{
			RelationalStore store = getPersister().getStore();
			if(!isClientExempt())
			{
				if(data.at("type").asString().equals("legacy:ServiceCase"))
				{
					Set<OWLNamedIndividual> permittedTypes = Permissions.
							getAllowedObjectsOfClass(
								Permissions.BO_VIEW, 
								owlClass("legacy:ServiceCase"), 
								getUserActors());
					List<String> permittedTypeList = new ArrayList<String>();
					for(OWLNamedIndividual each : permittedTypes)
						permittedTypeList.add(each.getIRI().toString());
					data.set("type", permittedTypeList);
				}
				else
				{
					if (!Permissions.check(individual("BO_View"), 
							individual(data.at("type").asString()),
							getUserActors()))
						return ko("Permission denied.");
				}
			}
			//REMOVE NOT PERMITTED VIEW TYPES
			Set<String> asdDispatchTypes = getASDDispatchTypesAsStrings();
			if(data.at("type").isString())
			{
				if (data.at("type").asString().equals("legacy:ServiceCase"))
					data.set("type", asdDispatchTypes);
				else if (!asdDispatchTypes.contains(fullIri(data.at("type").asString()).toString()))
						return ko("Selected Service Request Type is not permitted in ASD Dispatch view");
				//else allowed type
			}
			else if (data.at("type").isArray())
			{
				List<Json> types = data.at("type").asJsonList();
				Iterator<Json> it = types.iterator();
				// Remove all types that are not asd view types from permitted types
				while (it.hasNext())
				{
					String cur = it.next().asString();
					if (!asdDispatchTypes.contains(cur)) 
					{
						it.remove();
					}
				}
			}
			else 
				throw new IllegalArgumentException("asdDispatchLookup: data.at(\"type\") neither String nor Array but: " + data.at("type"));
			
			QueryTranslator qt = new QueryTranslator();
			Query query = qt.translateASDDispatch(data, store);

			Json viewResults = store.customSearch(query);

			//Do not display StatusChangeActivity Activities
			Json filteredResults = array();
			for(Json j : viewResults.asJsonList())
			{
				if(!j.at("ACTIVITY").isNull() && 
						!j.at("ACTIVITY").asString().equals("StatusChangeActivity"))
					filteredResults.add(j);
			}

			for (int i = 0; i < filteredResults.asJsonList().size(); i++)
			{
				Json j = filteredResults.at(i);
				if (!j.at("ACTIVITY").isNull())
					j.set("ACTIVITY",
							fullIri("legacy:" + j.at("ACTIVITY").asString())
									.toString());
				if (!j.at("OUTCOME").isNull())
					j.set("OUTCOME",
							fullIri("legacy:" + j.at("OUTCOME").asString())
									.toString());
				if (!j.at("PRIORITY").isNull())
					j.set("PRIORITY",
							fullIri("legacy:" + j.at("PRIORITY").asString())
									.toString());
				j.set("PRIORITY_LABEL", "");
				j.set("ACTIVITY_LABEL", "");
				j.set("OUTCOME_LABEL", "");
				j.set("RNUM", i);
				j.set("isAccepted", "");
			}
			return ok().set("records", filteredResults);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return ko(e.getMessage());
		} 
		finally
		{
			if (DBG)
				ThreadLocalStopwatch.stop("END asdDispatchLookup");
		}
	}

	@POST
	@Path("dispatchPDF")
	@Produces("application/pdf")
	public Representation asdServiceRequests(
			@FormParam("formData") String formData)
	{
		Set<Long> results = new HashSet<Long>();
		try
		{
			Json queryData = Json.read(formData);
			RelationalStore store = getPersister().getStore();
			QueryTranslator qt = new QueryTranslator();
			Query query = qt.translateASDDispatch(queryData, store);
			Json viewResults = store.customSearch(query);
			for(Json sr : viewResults.asJsonList())
				results.add(sr.at("SR_ID").asLong());
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		LegacyEmulator le = new LegacyEmulator();
		return le.makePDFCaseReports(results);
	}

	private Set<String> getASDDispatchTypesAsStrings() {
		Set<OWLNamedIndividual> types = getASDDispatchTypes();
		Set<String> result = new HashSet<String>(30);
		for (OWLNamedIndividual i : types)
		{
			result.add(i.getIRI().toString());
		}
		return result;
	}
	
	private Set<OWLNamedIndividual> getASDDispatchTypes() {
		return OWL.queryIndividuals("legacy:ServiceCase and inverse hasObject value legacy:ASD_SR_ACCESS");
	}
	
}

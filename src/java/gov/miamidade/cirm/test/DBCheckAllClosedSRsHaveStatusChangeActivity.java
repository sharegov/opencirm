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
package gov.miamidade.cirm.test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;

import mjson.Json;

import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.owl.CachedReasoner;
import org.sharegov.cirm.rdb.RelationalStore;
import org.sharegov.cirm.rdb.RelationalStoreExt;
import org.sharegov.cirm.rest.LegacyEmulator;
import org.sharegov.cirm.rest.OperationService;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

/**
 * Checks if all closed SRs have a proper StatusChangeActivity with outcome "C-"
 * 
 *  Modify modulo field below!!!
 *  
 * @author Thomas Hilpold
 */
public class DBCheckAllClosedSRsHaveStatusChangeActivity
{
	public int modulo = 100; //only every 100th SR will be loaded and checked. Change to 1 if you want all checked.
	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		DBCheckAllClosedSRsHaveStatusChangeActivity op = new DBCheckAllClosedSRsHaveStatusChangeActivity();
		op.startCheck();
	}
	
	/**
	 * Don't run this against PROD during the day!
	 */
	public void startCheck() {
		OWL.manager();
		LegacyEmulator le = new LegacyEmulator();
		RelationalStoreExt store =  OperationService.getPersister().getStoreExt();
		ThreadLocalStopwatch.getWatch().time("DBCheckAllClosedSRsHaveStatusChangeActivity");
		ThreadLocalStopwatch.getWatch().time("Checking if all closed SRs have a proper StatusChangeActivity ");
		ThreadLocalStopwatch.getWatch().time("Every " + modulo + " SR will be loaded and checked" );
		LinkedHashSet<Long> allClosedSRsbyBoid = getAllClosedSRsbyBoid(store); 
		int i = 1; 
		int failed = 0; 
		int passed = 0; 
		int errors = 0; 
		int total = allClosedSRsbyBoid.size();
		CachedReasoner.DBG_CACHE_MISS = false;
		Json sr = null;
		for (long boid : allClosedSRsbyBoid) {
			//Don't check all boids, if modulo is set to > 1;
			if (i % modulo == 0)
				try {
					sr = null;
					BOntology bo = le.findServiceCaseOntology(boid);
					sr = bo.toJSON();
					boolean pass = hasProperStatusChangeActivity(sr, boid);
					if (pass) 
						passed ++; 
					else 
						failed ++;
					System.out.println("Check " + i + "/" + total + " Boid: " + boid + " Passed? " + pass);
					if (i % 1000 == 0)
						ThreadLocalStopwatch.getWatch().time("Checked so far: " + i + "/" + total + " Failed: " + failed + " Passed: " + passed + " errors: " + errors);
				} catch (Exception e) {
					System.err.println("FAILED Check " + i + "/" + total + " Boid: " + boid + e);
					if (sr != null) System.err.println(sr.toString());
					e.printStackTrace();
					errors ++;
				}
			i++;
		}
		ThreadLocalStopwatch.getWatch().time("Finished: Checked " + i + " Failed: " + failed + " Passed: " + passed + " Errors: " + errors);
		ThreadLocalStopwatch.getWatch().time("Every " + modulo + " SR was loaded and checked" );
	}

	public boolean hasProperStatusChangeActivity(Json sr, long boid)  {
			//System.out.println(sr.toString());
			Json properties = sr.at("properties", null);
			Date lastModifiedDate = GenUtils.parseDate(properties.at("hasDateLastModified").asString());
			Date createdDate = GenUtils.parseDate(properties.at("hasDateCreated").asString());
			if (properties.at("hasServiceActivity", null) == null) 
			{
				System.err.println(boid + " NO hasServiceActivity");
				return false;
			}
			if (lastModifiedDate.before(createdDate))
			{
				System.err.println(boid + " SR LAST MODIFIED DATE BEFORE CREATED DATE");
				return false;
			}
			List<Json> activities = (properties.at("hasServiceActivity").isArray()) ? properties.at("hasServiceActivity").asJsonList() : Collections.singletonList(properties.at("hasServiceActivity"));
			List<Json> statusChangedActivities = new ArrayList<Json>();
			for(Json activity : activities)
			{
				String activityTypeIRI = activity.at("hasActivity").isObject()? activity.at("hasActivity").at("iri").asString() : activity.at("hasActivity").asString();
				if (activityTypeIRI.contains("StatusChangeActivity"))
					statusChangedActivities.add(activity);				
			}
			if (statusChangedActivities.isEmpty())
			{
				System.err.println(boid + " NO STATUS CHANGED ACTIVITIES FOUND ");
				return false;
			}
			boolean closeSRActivityFound = false;
			for (Json statusChangedAct : statusChangedActivities)
			{
				String outcomeIRI = statusChangedAct.at("hasOutcome").isObject()? statusChangedAct.at("hasOutcome").at("iri").asString() : statusChangedAct.at("hasOutcome").asString();  
				if (outcomeIRI.startsWith("http://www.miamidade.gov/cirm/legacy#C-"))
				{
					closeSRActivityFound = true;
					Date actClosedTimeStamp = GenUtils.parseDate(statusChangedAct.at("hasCompletedTimestamp").asString());
					if (actClosedTimeStamp.before(createdDate)) 
					{
						System.err.println(boid + " STATUS CHANGE ACT COMPLETED TIME STAMP BEFORE SR CREATED DATE");
						return false;
					}
				}
				//Check all StatusChangeActs for 
				if (!statusChangedAct.at("type", "").asString().equals("ServiceActivity")) 
				{
					System.err.println(boid + " STATUS CHANGE ACT TYPE NOT ServiceActivity" + statusChangedAct.toString());
					return false;
				}
			}
			if (!closeSRActivityFound)
			{
				System.err.println(boid + " STATUS CHANGE WITH C- OUTCOME NOT FOUND FOR CLOSED SR");
				return false;
			}
			return true;
	}
	
	
	public LinkedHashSet<Long> getAllClosedSRsbyBoid(RelationalStoreExt store)  {
		try 
		{
			return getAllClosedSRsbyBoidInt(store);
		} catch (Exception e) 
		{
			throw new RuntimeException(e);
		}
	}

	public LinkedHashSet<Long> getAllClosedSRsbyBoidInt(RelationalStoreExt store) throws SQLException {
			PreparedStatement stmt = null;			
			Connection conn = null;
			ResultSet rs = null;
			LinkedHashSet<Long> result = new LinkedHashSet<Long>(300);
			try
			{
				conn = store.getConnection();
				String sql = "SELECT SR_REQUEST_ID, SR_STATUS FROM CIRM_SR_REQUESTS WHERE SR_STATUS like 'C-%' ORDER BY SR_REQUEST_ID DESC ";
				System.out.println(sql);
				stmt = conn.prepareStatement(sql);
				rs = stmt.executeQuery();
				while (rs.next())
				{
					result.add(rs.getLong("SR_REQUEST_ID"));
				}
				conn.commit();
			} 
			catch (SQLException e)
			{
				if (conn != null) conn.rollback();
				throw new RuntimeException(e);
			} finally
			{
				rs.close();
				stmt.close();
				conn.close();
			}
			System.out.println("GET ALL CLOSED WHERE SR_STATUS like 'C-%' FOUND : " + result.size());
			return result;
	}
}

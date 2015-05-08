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

import gov.miamidade.cirm.MDRefs;
import gov.miamidade.cirm.other.LiveReportingSender;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.rest.LegacyEmulator;
import org.sharegov.cirm.rest.RestService;
import org.sharegov.cirm.utils.GenUtils;

/**
 * Service for Live Reporting which sends all new Service Cases including those originated at an interface 
 * to the configured live reporting queue.
 *  
 * @author Thomas Hilpold
 */
@Path("liveReporting")
@Produces("application/json")
public class LiveReportingService extends RestService
{
	
    @GET
    @Path("/status")
	public Json status()
	{
		return GenUtils.ok().with(Json.object("liveReportingEnabled", isLiveReportingEnabled()));
	}
	
    /**
     * Starts sending all new SRs from 311HUb UI, interfaces, and open311 to reporting queue for live reporting.
     * 
     * @return
     */
	@POST
    @Path("/start")
	public synchronized Json start()
	{
		if (!isLiveReportingEnabled()) 
		{
			MDRefs.liveReportingStatus.resolve().setEnabled(true);
			return status();
		} else {
			return GenUtils.ko("Live reporting already enabled on this server");
		}
	}
		
    /**
     * Stops sending new SRs from 311HUb UI, interfaces, and open311 to reporting queue for live reporting.
     * 
     * @return
     */
	@POST
    @Path("/stop")
	public synchronized Json stop()
	{
		if (isLiveReportingEnabled()) 
		{
			MDRefs.liveReportingStatus.resolve().setEnabled(false);
			return status();
		} else {
			return GenUtils.ko("Live reporting not enabled on this server");
		}
	}
	
	/**
	 * Sends an SR that already exists combined with meta data from the ontologies to live reporting.
	 * User must ensure that SR has not yet been sent. 
	 * If SR already exists in live reporting, receiving logic should consider the event an update and not ignore it.
	 * The message will be tagged as BO_NEW.
	 * 
	 * @param caseId a boid of an SR that exists in CiRM to be sent to live reporting.
	 * @return ko if not found or error during meta data adding.
	 */
	@POST
    @Path("/sendAsNewSR")
	public synchronized Json sendAsNewSR(@QueryParam("caseId") long caseId)
	{
		BOntology bontology, bontologyVerbose; 
		LegacyEmulator le = new LegacyEmulator();
		LiveReportingSender sender = new LiveReportingSender();
		//not needed LegacyEmulator.forceClientExempt.set(true), because find does not check for permissions.
		//findServiceCaseOntology may be called outside of a cirm transaction as long as we don't delete SRs.
		bontology = le.findServiceCaseOntology(caseId);
		if (bontology != null) {
			try
			{ 
				bontologyVerbose = le.addMetaDataAxioms(bontology);
				Json bontologyVerboseJson = OWL.toJSON(bontologyVerbose.getOntology(), bontology.getBusinessObject()); 
				bontologyVerboseJson.set("boid", bontology.getObjectId());
				sender.sendNewServiceRequestToReporting(Json.object("case", bontologyVerboseJson));
				return GenUtils.ok().set("status", "Sr with caseId " + caseId + " sucessfully sent to reporting as new SR");
			} catch (OWLOntologyCreationException e)			
			{
				e.printStackTrace();
				return GenUtils.ko("Sr found, but error during adding meta data axioms. See OWLOntologyCreationException stacktrace in server log.");
			} 
		} else {
			return GenUtils.ko("Sr with case number " + caseId + " no found. Cannot send sr.");
		}
	}
	
	
	//Just a shortcut
	private synchronized boolean isLiveReportingEnabled() {
		return MDRefs.liveReportingStatus.resolve().isEnabled();
	}
	
}

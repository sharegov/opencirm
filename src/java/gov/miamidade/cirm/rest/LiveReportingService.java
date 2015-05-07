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

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import mjson.Json;

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
	
	//Just a shortcut
	private synchronized boolean isLiveReportingEnabled() {
		return MDRefs.liveReportingStatus.resolve().isEnabled();
	}
	
}

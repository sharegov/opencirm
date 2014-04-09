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
package org.sharegov.cirm.rest;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import org.restlet.ext.jaxrs.JaxRsApplication;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.event.EventDispatcher;


import mjson.Json;
import static org.sharegov.cirm.utils.GenUtils.*;

@Path("manage")
@Produces("application/json")
public class RestServiceAdmin extends RestService
{
	/**
	 * <p>Return general information about the server and JVM the platform is running on.</p>
	 */
	@GET
	@Path("/sysinfo")
	public Json sysInfo()
	{
		System.out.println("in RestServiceAdmin.syinfo, remove this trace");
		try
		{
			Json info = Json.object();
			info.set("host", java.net.InetAddress.getLocalHost().getHostName());
			info.set("config", StartUp.config);
			// add whatever else may be needed...JVM sys properties, memory data, 
			// other stats collected somewhere (in RESTlet filters or whatever)
			return info;
		}
		catch (Throwable t)
		{
			return ko(t.toString());
		}
	}
	
	@POST
	@Path("/suicide")
	public Json suicide()
	{
		for (StackTraceElement[] t : Thread.getAllStackTraces().values()) 
		{
			for (StackTraceElement el : t)
				System.out.println(el);
			System.out.println("\n-------------------------------------------------\n");			
		}
		System.exit(3);
		return ok();
	}
	
	@POST
	@Path("/reload")
	public Json reload()
	{
		Json x = unload();
		return x.is("ok", true) ? load() : x;
	}
	
	@POST
	@Path("/unload")
	public Json unload()
	{
		try
		{
			JaxRsApplication app = (JaxRsApplication)StartUp.server.getDefaultHost().getApplication(); 
			StartUp.server.getDefaultHost().detach(StartUp.jsonpFilter);
			StartUp.server.getDefaultHost().detach(app);
			return ok();
		}
		catch (Throwable t)
		{
			t.printStackTrace(System.err);
			return ko(t.toString());
		}
	}
	
	@POST
	@Path("/load")
	public Json load()
	{
		StartUp.createRestServicesApp();
		return ok();
	}
	
	/**
	 * Creates an application update event and sends it to each currently connected client.
	 * 
	 * @return 
	 */
	@POST
	@Path("/applicationUpdated")
	public Json applicationUpdated()
	{
		// create app update event
		// put in a queue
		// can we determine how many clients get the event?
		EventDispatcher.get().dispatch(OWL.individual("CIRMApplication"), OWL.owlClass("Software_Application"), null, OWL.individual("BO_Update"));
		return ok();
	}	
}

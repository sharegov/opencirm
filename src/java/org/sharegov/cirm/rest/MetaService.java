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

import static org.sharegov.cirm.OWL.fullIri;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import mjson.Json;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.SWRLRule;
import org.sharegov.cirm.rules.RulesManager;
import org.sharegov.cirm.utils.EvalUtils;
import org.sharegov.cirm.workflows.JsonSWRLSerializer;
import org.sharegov.cirm.workflows.Workflow;
import org.sharegov.cirm.workflows.WorkflowManager;

@Path("meta")
public class MetaService extends RestService
{
	@GET
	@Path("/workflow/{type}")
	@Produces("application/json")
	public String getWorkflowDescription(@PathParam("type") String type)
	{
		Workflow workflow = WorkflowManager.getInstance().getWorkflowForType(fullIri(type));
		return workflow.toJSON().toString();
	}
	
	public String handleRestError(Throwable t)
	{
		StringWriter sw = new StringWriter();
		t.printStackTrace(new PrintWriter(sw));
		return "{\"errorStackTrace\": \"" + sw.toString() + "\"}";
	}
	
	@GET
	@Path("/rules")
	@Produces("application/json")
	public String getRules(@QueryParam("fragment") boolean fragment)
	{
		Json result = Json.array();
		Set<IRI> l = RulesManager.getInstance().getAllRules();
		for(IRI i : l)
		{
			if(fragment)
			{
				String s = i.toString();
				result.add(s.substring(s.lastIndexOf("/") + 1));
			}
			else
				result.add(i.toQuotedString());
		}
		return result.toString();
	}
	
	@GET
	@Path("/rules/{type}")
	@Produces("application/json")
	public String getRules(@PathParam("type") String type)
	{
		Json array = Json.array();
		for(SWRLRule rule : RulesManager.getInstance().getRules(type))
		{
			array.add((Json)EvalUtils.dispatch(new JsonSWRLSerializer() , rule));
		}
		return array.toString();
	}
	
	@POST
	@Path("/rules/{type}")
	@Consumes("application/json")
	public void setRules(@PathParam("type") String type, Json json)
	{
		RulesManager manager = RulesManager.getInstance();
		Set<SWRLRule> rules = manager.fromJSON(json);
		if(!rules.isEmpty())
		{
			try
			{
				manager.setRules(type, rules);
			}catch(OWLOntologyCreationException e)
			{
				handleRestError(e);
			}
			catch (OWLOntologyStorageException e)
			{
				handleRestError(e);
			}
			catch (IOException e)
			{
				handleRestError(e);
			}
		}
	}
	
	@DELETE
	@Path("/rules/{type}")
	public void deleteRules(@PathParam("type") String type)
	{
		RulesManager manager = RulesManager.getInstance();
		manager.deleteRules(type);
	}
}
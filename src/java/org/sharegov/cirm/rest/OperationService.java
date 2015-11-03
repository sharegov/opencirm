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

import static org.sharegov.cirm.OWL.businessObjectId;

import static org.sharegov.cirm.OWL.dataProperty;
import static org.sharegov.cirm.OWL.fullIri;
import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.isBusinessObject;
import static org.sharegov.cirm.OWL.owlClass;
import static org.sharegov.cirm.utils.GenUtils.ko;
import static org.sharegov.cirm.utils.GenUtils.ok;

import java.io.File;
import java.net.URLDecoder;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import mjson.Json;

import org.hypergraphdb.HGHandle;
import org.semanticweb.owlapi.io.OWLFunctionalSyntaxOntologyFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.OWLObjectToJson;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.event.EventDispatcher;
import org.sharegov.cirm.rdb.RelationalOWLPersister;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;
import org.sharegov.cirm.workflows.UserInputRequest;
import org.sharegov.cirm.workflows.Workflow;
import org.sharegov.cirm.workflows.WorkflowDone;
import org.sharegov.cirm.workflows.WorkflowExecutionContext;
import org.sharegov.cirm.workflows.WorkflowManager;
import org.sharegov.cirm.workflows.WorkflowRequestStep;
import org.sharegov.cirm.workflows.WorkflowStep;

@Path("op")
@Produces("application/json")
public class OperationService
{		
	public static boolean DBG = true;
	
	public static RelationalOWLPersister getPersister()
    {
		OWLNamedObject x = Refs.configSet.resolve().get("OperationsDatabaseConfig");
		return RelationalOWLPersister.getInstance(x.getIRI());
    }
	
	private String handleException(Throwable e)
	{
		e.printStackTrace();
		return "{\"error\": \"" + e.toString() + "\"}";
	}
	
	void saveOntology(OWLOntologyManager manager, OWLOntology ont) throws OWLOntologyStorageException
	{
		String filename = ont.getOntologyID().getOntologyIRI().toString();
		filename = filename.substring("http://www.miamidade.gov/".length()).replace('/', '_') + ".owl";
		File dir = new File(StartUp.config.at("workingDir").asString() + "/src/ontology");
		File f = new File(dir, filename);		
		manager.saveOntology(ont, new OWLFunctionalSyntaxOntologyFormat(), IRI.create(f));
	}
	
	public BOntology createBusinessObject(OWLClass type)
	{
		try
		{		
			return BOntology.makeNewBusinessObject(type);
			
			/*
			String boid = OWLRefs.idFactory.resolve().newId("");
			OWLOntologyManager manager = MetaService.get().getManager();		
			IRI ontologyIRI = IRI.create("http://www.miamidade.gov/bo/" + 
					type.getIRI().getFragment() + "/" + boid);
			// The business object is created in its own ontology.
			OWLOntology boOntology;
				boOntology = manager.createOntology(ontologyIRI);
			manager.applyChange(new AddImport(boOntology, manager.getOWLDataFactory().getOWLImportsDeclaration(
										ontology().getOntologyID().getOntologyIRI())));
			
			OWLNamedIndividual businessObject = businessObject(boOntology);
			OWLAxiom axiom = manager.getOWLDataFactory().getOWLClassAssertionAxiom(type, businessObject); 
			manager.applyChange(new AddAxiom(boOntology, axiom));
			getPersister().saveBusinessObjectOntology(boOntology);
			return boOntology;
			*/			
		}
		catch (OWLOntologyCreationException e)
		{
			throw new RuntimeException(e);		
		}		
	}

	@POST
	@Path("/create/{classname}")
	@Consumes("application/json")
	public Json newBusinessObject(@PathParam("classname") String classname, Json object)
	{
		if (DBG) ThreadLocalStopwatch.getWatch().time("START newBusinessObject " + classname);
		try
		{
			OWLClass theclass = owlClass(classname);
			BOntology bontology = createBusinessObject(theclass);
			bontology.setProperties(object);
			getPersister().saveBusinessObjectOntology(bontology.getOntology());	
			return ok().set("bo", bontology.toJSON());
		}
		catch (Throwable t)
		{
			t.printStackTrace(System.err);
			return ko(t);
		}
		finally
		{ 
			if (DBG) ThreadLocalStopwatch.getWatch().time("END newBusinessObject " + classname);
			ThreadLocalStopwatch.dispose();
		}
	}
		
	@GET
	@Path("/new/{classname}")
	@Produces("application/json")
	public String newBusinessObject(@PathParam("classname") String classname) 
	{
		try
		{
			OWLClass theclass = owlClass(classname);
			BOntology bontology = createBusinessObject(theclass);
			return bontology.toJSON().toString();
		}
		catch (Throwable ex)
		{
			ex.printStackTrace(System.err);
			throw new RuntimeException(ex);
		}
	}
	
	Json stepBack(WorkflowExecutionContext context)
	{
		Json result = null;		
		while (result == null && !context.getHistory().isEmpty())
		{
			context.backtrack();
//			if (context.getCurrentStep() instanceof PromptUserTask)
//			{				
//				Object request = ((WorkflowRequestStep)context.getCurrentStep().perform(context)).getRequest();
//				if (request instanceof UserInputRequest)
//				{
//					result = ((UserInputRequest)request).getUispec();
//				}
//				else
//					result = Json.object().set("error", "unknown workflow request type " + request);
//			}			
		}
		return result;
	}
	
	Json stepForward(WorkflowExecutionContext context)
	{
		Json result = null;
		while (result == null)
		{
			WorkflowStep nextStep = context.getCurrentStep().perform(context);			
			if (nextStep instanceof WorkflowRequestStep)
			{
				Object request = ((WorkflowRequestStep)nextStep).getRequest();
				if (request instanceof UserInputRequest)
				{
					result = ((UserInputRequest)request).getUispec();
				}
				else
					result = Json.object().set("error", "unknown workflow request type " + request);
			}
			else if (nextStep instanceof WorkflowDone)
			{
				WorkflowDone done = (WorkflowDone)nextStep;
				Json x = Json.object();
				x.set("done", done.isSuccess());
				if (done.getInfo() != null)
					x.set("info", done.getInfo().toString());
				else
				{
					Set<OWLLiteral> S = context.getBusinessObject().getDataPropertyValues(
							dataProperty("hasExplanation"), 
							context.getBusinessObjectOntology().getOntology());
					if (!S.isEmpty())
						x.set("info", S.iterator().next().getLiteral());
				}
				result = x;
			}
			else
			{
				context.getHistory().push(context.getCurrentStep());			
				context.setCurrentStep(nextStep);
			}
		}
		return result;
	}
	
	@GET
	@Path("/workflow/{type}/{id}")
	@Produces("application/json")
	public String getWorkflow(@PathParam("type") String classname, @PathParam("id") String id)
	{
		try
		{
			// First, determine if the business object already has an associated workflow
			// and if not, instantiate one, based on the business rules defined for its type.
			WorkflowExecutionContext context = WorkflowManager.getInstance().getWorkflowContext(businessObjectId(classname, id));
			if (context == null)
			{
				BOntology bontology = getBusinessObjectOntology(OWL.businessObjectId(classname, id));
				context = WorkflowManager.getInstance().startWorkflow(bontology);
			}
			return stepForward(context).toString();
		}
		catch (Throwable e)
		{
			throw new RuntimeException(e);
		}
	}
		
	/**
	 * Submit a workflow action: either perform next step in workflow (from a user's
	 * perspective, or go back).
	 * 
	 * @param classname
	 * @param id
	 * @param command
	 * @param formDataParam
	 * @return
	 */
	@POST
	@Path("/workflow/{type}/{id}/{command}")
	@Produces("application/json")	
	public String workflowStep(@PathParam("type") String classname, 
							   @PathParam("id") String id,
							   @PathParam("command") String command,
							   @FormParam("data") String formDataParam)
	{
		if (DBG) ThreadLocalStopwatch.getWatch().time("START workflowStep " + id);
		try
		{
			Json formData = Json.read(formDataParam);			
			WorkflowExecutionContext context = WorkflowManager.getInstance().getWorkflowContext(businessObjectId(classname, id));
			if (context == null)
				throw new Exception("No workflow started for object " + classname + "#" + id);

			Json result = null;
			
			if ("continue".equals(command))
			{
				WorkflowRequestStep step = (WorkflowRequestStep)context.getCurrentStep().perform(context);
				step.setResponse(formData);
				WorkflowStep next = step.perform(context);
				context.moveTo(next);
				result = stepForward(context);
			}
			else if ("back".equals(command))
			{
				result = stepBack(context);
			}
			getPersister().saveBusinessObjectOntology(context.getBusinessObjectOntology().getOntology());
			return result.toString();			
		}
		catch (Throwable e)
		{
			return handleException(e);
		} 
		finally 
		{
			if (DBG) ThreadLocalStopwatch.getWatch().time("END workflowStep " + id);
			ThreadLocalStopwatch.dispose();
		}
	}

	@POST
	@Path("/workflow/create")
	public Json createWorkflow(@FormParam("data") String boAsData)
	{
		Json data = Json.read(boAsData);
		BOntology bontology = BOntology.makeRuntimeBOntology(data);
		WorkflowExecutionContext context = WorkflowManager.getInstance().startWorkflow(bontology);
		return ok().set("context", context.toJSON());		
	}
	
	@GET
	@Path("/workflow/create/{type}/{id}")
	public Json createWorkflow(@PathParam("type") String classname, @PathParam("id") String id)
	{
		try
		{
			IRI classIri = fullIri(classname);
			
			// First, determine if the business object already has an associated workflow
			// and if not, instantiate one, based on the business rules defined for its type.
			WorkflowExecutionContext context = WorkflowManager.getInstance().getWorkflowContext(businessObjectId(classname, id));
			if (context == null)
			{
				BOntology bontology = getBusinessObjectOntology(OWL.businessObjectId(classname, id));
				context = WorkflowManager.getInstance().startWorkflow(bontology);
				return ok().set("context", context.toJSON());
			}
			else
				return ko("workflow-exists").set("context", context.toJSON());
		}
		catch (Throwable e)
		{
			throw new RuntimeException(e);
		}
	}
	
	@GET
	@Path("/workflow/state/{type}/{id}")
	@Produces("application/json")	
	public Json workflowState(@PathParam("type") String classname, 
						      @PathParam("id") String id)
	{
		try
		{
			// First, determine if the business object already has an associated workflow
			// and if not, instantiate one, based on the business rules defined for its type.
			WorkflowExecutionContext context = WorkflowManager.getInstance().getWorkflowContext(businessObjectId(classname, id));
			if (context == null)
			{
				return ko("no-workflow");
			}
			// ? what else besides current task we want to send here? variables?
			return ok().set("next", 
							Workflow.toJSON(context.getCurrentStep()));
		}
		catch (Throwable e)
		{
			throw new RuntimeException(e);
		}		
	}
	
	@POST
	@Path("/workflow/step/{type}/{id}")
	@Produces("application/json")	
	public Json workflowStep(@PathParam("type") String classname, 
						     @PathParam("id") String id,
						     @FormParam("data") String dataParam)
	{
		Json boAsJson = Json.read(dataParam);
		WorkflowExecutionContext context = WorkflowManager.getInstance().getWorkflowContext(businessObjectId(classname, id));
		if (context == null)
			return ko("No workflow started for object " + classname + "#" + id);
		WorkflowStep currentStep = context.getCurrentStep();
		WorkflowStep nextStep = context.step(); 
		return ok().set("next", Workflow.toJSON(nextStep))
				   .set("prev", Workflow.toJSON(currentStep))
				   .set("outputVariables", context.getStepOutput(currentStep));
	}

	private WorkflowExecutionContext getContextFromClientData(Json data)
	{
		if (!data.has("context"))
			return null;
		String classname = data.at("context").at("bo").at("type").asString();		
		WorkflowExecutionContext  context = WorkflowManager.getInstance().makeRuntimeContext(OWL.fullIri(classname));
		context.fromJSON(data.at("context"));
		return context;
	}
	
	/**
	 * Evaluate the workflow until the next step loops back into itself or until a 
	 * condition passed into the data parameter is met. The condition can be a 
	 * 'stopCondition' on the value of an output variable name. Variable values
	 * are simply compared as strings here. 
	 *  
	 * @param classname
	 * @param id
	 * @return
	 */
	@POST
	@Path("/workflow/forward")
	@Produces("application/json")	
	public Json workflowForward(@FormParam("data") String dataParam)
	{	
		Json data = Json.read(dataParam);
		System.out.println(dataParam);
		WorkflowExecutionContext context = getContextFromClientData(data);
		if (context == null)
			return ko("Missing workflow context.");
		WorkflowStep lastStep = context.getHistory().isEmpty() ? null:context.getHistory().lastElement();
		while (context.getCurrentStep() != null)
		{ 
			if (lastStep != null && data.has("stopCondition"))
			{
				String varname = data.at("stopCondition").at("variable").asString();
				String value = data.at("stopCondition").at("value").asString();
				Object varvalue = context.getStepOutput(lastStep).get(IRI.create(varname));
				if (varvalue != null && value.equals(varvalue.toString()))
					break;
			}
			WorkflowStep currentStep = context.getCurrentStep();
			WorkflowStep nextStep = context.step(); 
			if (nextStep == currentStep)
				break;
			else 
				lastStep = currentStep;
		}
		return ok().set("context", context.toJSON());
	}

	@POST
	@Path("/workflow/back")
	@Produces("application/json")	
	public Json workflowBack(@FormParam("count") int stepCount, @FormParam("data") String dataParam) 
	{
		Json data = Json.read(dataParam);
		WorkflowExecutionContext context = getContextFromClientData(data);
		if (context == null)
			return ko("Missing workflow context.");
		while (stepCount-- > 0 && !context.getHistory().isEmpty())
		{
			context.backtrack();
		}		
		return ok().set("context", context.toJSON());
	}

	@POST
	@Path("/workflow/backto")
	@Produces("application/json")	
	public Json workflowBackTo(@FormParam("data") String latestDataParam,
							   @FormParam("tostep") String toStepId) 
	{
		HGHandle toStep = WorkflowManager.getInstance().getGraph().getHandleFactory().makeHandle(toStepId);
		Json data = Json.read(latestDataParam);
		WorkflowExecutionContext context = getContextFromClientData(data);
		if (context == null)
			return ko("Missing workflow context.");
		while (context.getHistory().isEmpty())
		{
			if (context.getCurrentStep().getAtomHandle().equals(toStep))
				break;
			context.backtrack();
		}		
		return ok().set("context", context.toJSON()); 
	}
	
	@POST
	@Path("/workflow/back/{type}/{id}")
	@Produces("application/json")	
	public Json workflowBack(@PathParam("type") String classname, 
						     @PathParam("id") String id)
	{
		IRI classIri = fullIri(classname);		
		WorkflowExecutionContext context = WorkflowManager.getInstance().getWorkflowContext(businessObjectId(classname, id));
		if (context == null)
			return ko("No workflow started for object " + classname + "#" + id);
		
		if (!context.getHistory().isEmpty())
			return ko("Already at beginning.");
		
		WorkflowStep top = context.getHistory().pop();
		context.setCurrentStep(top);
		return ok().set("next", Workflow.toJSON(context.getCurrentStep()));
	}
	
	public BOntology getBusinessObjectOntology(Long boId)
	{
		return new BOntology(getPersister().getBusinessObjectOntology(boId));
	}

	public BOntology getBusinessObjectOntology(IRI boIri)
	{
		return new BOntology(getPersister().getBusinessObjectOntology(boIri));
	}
	
	@GET
	@Path("/get/{type}/{id}")
	@Produces("application/json")
	public Json getBusinessObject(@PathParam("type") String classname, 
			   					  @PathParam("id") String id)
	{
		try
		{			
			IRI boIri = businessObjectId(classname,  id);
			BOntology boOntology = getBusinessObjectOntology(boIri); 
	//			MetaService.get().getOntologyLoader().getBusinessObjectOntology(classIri, id);
			OWLObjectToJson mapper = new OWLObjectToJson();
			mapper.setIncludeTypeInfo(true);
			Json j = mapper.map(boOntology.getOntology(), boOntology.getBusinessObject(), null);
			System.out.println(j.toString());
			return j;
		}
		catch (Throwable e)
		{
			return Json.object("ko", true, "error", e.toString());
		}
	}
	
	@GET
	@Path("/list")	
	public Json list(@QueryParam("q") String queryAsString)
	{
		try
		{
			Json result = Json.array();
			Json query = Json.read(queryAsString);
			List<OWLEntity> L = getPersister().query(query);
			//List<OWLObject> L = new ArrayList<OWLObject>();
			for (OWLEntity x : L)			
			{
				if (x instanceof OWLNamedIndividual && 
					isBusinessObject(x.asOWLNamedIndividual().getIRI())) 
				{
					BOntology on = this.getBusinessObjectOntology(((OWLNamedIndividual) x).getIRI());
					result.add(OWL.toJSON(on.getOntology(), x.asOWLNamedIndividual()));
				}
				else
					result.add(OWL.toJSON(x));
			}
			System.out.println(result);
			return result;
		}
		catch (Throwable e)
		{
			e.printStackTrace(System.err);
			return Json.object("error", e.toString());
		}
	}

	@POST
	@Path("/update/{type}/{id}")
	public Json updateBusinessObject(@PathParam("type") String classname, 
		    					     @PathParam("id") String id,
		    					     @FormParam("data") String formDataParam)
	{
		if (DBG) ThreadLocalStopwatch.getWatch().time("START updateBusinessObject " + id);
		try {
			Json data = Json.read(formDataParam);
			WorkflowExecutionContext context = getContextFromClientData(data);
			if (context == null)
				context = WorkflowManager.getInstance().getWorkflowContext(businessObjectId(classname, id));
			IRI boid = businessObjectId(classname, id);
			BOntology o = getBusinessObjectOntology(boid);
			o.setProperties(data);					
			if (context != null)
				context.setBusinessObjectOntology(o);
			getPersister().saveBusinessObjectOntology(o.getOntology());
			if (context != null)
				context.processEventRules();
			return ok();
		} 
		finally 
		{ 
			if (DBG) ThreadLocalStopwatch.getWatch().time("END updateBusinessObject " + id);
			ThreadLocalStopwatch.dispose();
		}
	}
	
	@DELETE
	@Path("/delete/{type}/{id}")
	public Json removeBusinessObject(@PathParam("type") String classname, 
		    					     @PathParam("id") String id)
	{
		IRI boid = businessObjectId(classname, id);
		BOntology boOntology = getBusinessObjectOntology(boid);
		getPersister().deleteBusinessObjectOntologyWithHistory(boOntology.getOntology());
		return ok();
	}
	
	@GET
	@Path("/actions/{type}/{id}")
	public Json getActionsInContext(@PathParam("type") String classname,
								    @PathParam("id") String id,
									@QueryParam("context") String context)
	{
		Json actions = Json.array();
		actions.add(OWL.toJSON(individual("BO_Update")));
		actions.add(OWL.toJSON(individual("BO_Delete")));		
		return actions;
	}
	
	@GET
	@Path("/successpath/{type}/{id}")
	public Json getShortestSuccessPath(@PathParam("type") String classname, 
		     						   @PathParam("id") String id)
	{
		WorkflowExecutionContext context = 
			WorkflowManager.getInstance().getWorkflowContext(businessObjectId(classname, id));
		// TODO...
		return ok();
	}
	
	@SuppressWarnings("deprecation")
	@GET
	@Path("/eventrule")
	public Json fireEventRule(@QueryParam("boiri") String boiri, @QueryParam("id") String ruleId)
	{
		try
		{
			System.out.println("Fire rule " + ruleId + " on " + URLDecoder.decode(boiri, "UTF-8"));
			WorkflowExecutionContext context = WorkflowManager.getInstance().getWorkflowContext(IRI.create(boiri));
			context.fireEventRule(ruleId);			
		}
		catch (Throwable t)
		{
			return ko(t);
		}
		return ok();
	}

	@DELETE
	@Path("/individual/{iri}")
	public Json deleteIndividual(@PathParam("iri") String iri)
	{
		try
		{
			OWLOntology O = Refs.tempOntoManager.resolve().createOntology();
			OWLNamedIndividual individual = individual(iri);
			getPersister().readIndividualData(O, individual);
			getPersister().deleteBusinessObjectOntologyWithHistory(O);
			return ok();
		}
		catch (Throwable ex)
		{
			ex.printStackTrace(System.err);
			return ko(ex);
		} 
		finally
		{
			ThreadLocalStopwatch.dispose();
		}
	}

	@GET
	@Path("/individual/{iri}")
	public Json getIndividual(@PathParam("iri") String iri)
	{
		try
		{
			OWLOntology O = Refs.tempOntoManager.resolve().createOntology();
			OWLNamedIndividual individual = individual(iri);
			getPersister().readIndividualData(O, individual);			
			return O.getAxioms().size() > 0 ? 
			        ok().set("data", OWL.toJSON(O, individual)) :
			        ko("non-found");
		}
		catch (Throwable ex)
		{
			ex.printStackTrace(System.err);
			return ko(ex);
		} 
		finally
		{
			ThreadLocalStopwatch.dispose();
		}
	}
	
	@POST
	@Path("/individual/{iri}")
	@Consumes("application/json")
	public Json saveIndividual(@PathParam("iri") String iri, Json object)
	{
		if (DBG) ThreadLocalStopwatch.getWatch().time("START saveIndividual " + iri);
		try
		{
			OWLOntology O = Refs.tempOntoManager.resolve().createOntology();
			OWLNamedIndividual individual = individual(iri);
			BOntology bo = new BOntology(O);
			bo.setPropertiesFor(individual, object);
			getPersister().saveBusinessObjectOntology(O);// .readIndividualData(O, individual);
			OWLObjectToJson mapper = new OWLObjectToJson();
			Json j = mapper.map(O, individual, null);
			EventDispatcher.get().dispatch(individual, 
						owlClass(object.at("type").asString()), 
						object,
						individual("BO_Update"));
			return j;
		}
		catch (Throwable ex)
		{
			ex.printStackTrace(System.err);
			return ko(ex);
		} 
		finally 
		{ 
			if (DBG) ThreadLocalStopwatch.getWatch().time("END saveIndividual " + iri);
			ThreadLocalStopwatch.dispose();
		}
	}
	
	@GET
	@Path("/event/{since}")
	public Json getEvent(@PathParam("since") long since)
	{
		try
		{
			return ok().set("events", Refs.clientPushQueue.resolve().getAfter(since));
		}
		catch (Throwable t)
		{
			return ko(t);
		}
	}

	/**
	 * Get's the current time in milliseconds.
	 * Current implementation retrieves the time from the RelationalStore.
	 * @return Json containing time with millisecond accuracy.
	 */
	@GET
	@Path("/time")
	public Json getTime()
	{
		try
		{
			return ok().set("time", getPersister().getStore().getStoreTime().getTime());
		}
		catch (Throwable t)
		{
			return ko(t);
		}
	}

	// This is not a main program, just used for quick&dirty tests
	public static void main(String [] argv)
	{
		OperationService op = new OperationService();
	}
}

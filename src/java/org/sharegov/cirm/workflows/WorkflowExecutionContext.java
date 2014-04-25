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
package org.sharegov.cirm.workflows;

import java.util.HashMap;


import java.util.Calendar;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.hypergraphdb.HGGraphHolder;
import org.hypergraphdb.HGHandle;
import org.hypergraphdb.HyperGraph;
import org.hypergraphdb.annotation.AtomReference;
import org.hypergraphdb.util.Pair;
import org.hypergraphdb.util.RefResolver;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.SWRLRule;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.utils.EvalUtils;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.JsonSerializable;

import static org.sharegov.cirm.OWL.*;
import mjson.Json;

/**
 * 
 * <p>
 * The workflow execution context represents state information for a particular
 * execution of a particular workflow. The state can be persistent and 
 * it is managed entirely by the various steps being executed during the
 * workflow's instance lifetime as well the {@link ExecutionEngine} driving
 * the process.  
 * </p>
 *
 * @author Borislav Iordanov
 *
 */
public class WorkflowExecutionContext implements HGGraphHolder, JsonSerializable
{
	private HyperGraph graph;
	@AtomReference("symbolic")
	private Workflow workflow;
	@AtomReference("symbolic")
	private WorkflowStep currentStep;
	private Stack<WorkflowStep> history = new Stack<WorkflowStep>();
	// We store runtime information about event rules (which one is active, history
	// of firing etc. as Json structures mapped to the rule HGDB handles).
	private Map<HGHandle, Json> eventRules = new HashMap<HGHandle, Json>();
	private BOntology bontology;
	
	// The output variables per each step are stored globally here. The key is a step handle + the
	// variable id. For global variables, the step handle is the HGHandleFactory.anyHandle().
	private Map<Pair<HGHandle, IRI>, Object> variables = new HashMap<Pair<HGHandle, IRI>, Object>();
	
	// A global varresolver for the whole context is needed for "global" variables that are available
	// in all workflow steps. The RulesToWorkflow.boVar (i.e. '?bo' inside a rule) is the only one
	// such variable currently. When creating the workflow, this variable is resolved either to a prototypical
	// object of the OWLClass or (if a workflow is being re-generated for a specific business object) to 
	// an actual business object with some data in it, but unlike other variables that can be resolved during
	// workflow creation, it remains a variable and doesn't get instantiated. It is supposed to be 
	// implicitly defined in all workflow steps. The logic of how to deal with this variable is kind of
	// scattered in RulesToWorkflow and this class and perhaps some other classes. It is really treated
	// as a special case burried inside "if" conditions. It's ugly. The whole point is to be able to generate
	// a workflow for a prototypical business object of the type of interest, but use a real/operational business
	// object when executing the workflow.  
	//
	//Another way to deal with this and possible other globals is to have
	// a special "WorkflowStart" step that has all globals as "output variables" that go to all other steps as 
	// "input variables", but that doesn't seem to elegant either, it just kind of masks what's implicitly
	// global into making it explicitly local everywhere. 
	private RefResolver<SWRLVariable, OWLObject> varResolver = new RefResolver<SWRLVariable, OWLObject>(){
		public OWLObject resolve(SWRLVariable var)
		{
			if (var.getIRI().equals(RulesToWorkflow.boVar))
				return bontology.getBusinessObject();
			else if (EvalUtils.isVarGlobal(var.getIRI()))
				return (OWLObject)getGlobalVariable(var.getIRI());
				//variables.get(new Pair<HGHandle, IRI>(graph.getHandleFactory().anyHandle(), var.getIRI()));
			else
				return null;
		}
	};
	
	public WorkflowExecutionContext()
	{		
	}
		
	public void setHyperGraph(HyperGraph graph) { this.graph = graph; }
	
	public RefResolver<SWRLVariable, OWLObject> getVarResolver() { return varResolver; }
	
	public void processEventRules()
	{
		for (Map.Entry<HGHandle, Json> e : this.eventRules.entrySet())
		{
			EventRule rule = new EventRule((SWRLRule)graph.get(e.getKey()), e.getValue());
			if (rule.isActive(this) && 
				(Calendar.getInstance().after(rule.getFireTime())) || !rule.isViable(this))
				rule.deactivate(this);
			else if (rule.isViable(this) && Calendar.getInstance().before(rule.getFireTime()))
				rule.activate(this);
		}
	}
	
	public void fireEventRule(String ruleId)
	{
		HGHandle ruleHandle = graph.getHandleFactory().makeHandle(ruleId);
		EventRule evRule = new EventRule((SWRLRule)WorkflowManager.getInstance().getGraph().get(ruleHandle), 
							eventRules.get(ruleHandle));
		evRule.fire(this);		
	}
	
	public void backtrack()
	{
		if (history.isEmpty())
			throw new WorkflowException("Can't backtrack on an empty history.");
		WorkflowStep top = history.pop();
		while (top instanceof Branch && !history.isEmpty())
			top = history.pop();
		if (history.isEmpty()) // unlikely, but theoretically possible that first steps were only branches
			currentStep = workflow.getStartingStep();
		if (top instanceof WorkflowUndoableStep)
		{
//			if (top instanceof PromptUserTask)
//			{
//				WorkflowUndoableStep promptRequest = (WorkflowUndoableStep)top.perform(this);
//				promptRequest.backtrack(this);
//			}
			((WorkflowUndoableStep) top).backtrack(this);
			currentStep = top;
		}
		else
			throw new WorkflowException("Previous workflow step " + top + " cannot be undone.");
	}
	
	public void moveTo(WorkflowStep step)
	{
		history.push(currentStep);
		currentStep = step;
	}
	
	/**
	 * Return a map of all output variables of the passed in workflow step.
	 */
	public Map<IRI, Object> getStepOutput(WorkflowStep step)
	{
		Map<IRI, Object> m = new HashMap<IRI, Object>();
		if (! (step instanceof Task))
			return m;
		Set<IRI> varnames = new HashSet<IRI>();
		Task t = (Task)step;
		varnames.addAll(t.getOutputVariables());
		if (t instanceof AtomEvalTask)
			varnames.add(AtomEvalTask.evalResultVar);
		for (IRI name : varnames)
		{
			Object value = variables.get(new Pair<HGHandle, IRI>(t.getPrototypeId(), name));
			if (value != null)
				m.put(name, value);
		}
		return m;
	}
		
	public WorkflowStep step()
	{		
		WorkflowStep next = currentStep.perform(this);
		if (next != currentStep)
			this.moveTo(next);
		return next;
	}
	
	public void setBusinessObjectOntology(BOntology bontology)
	{
		this.bontology = bontology;
	}
	
	public BOntology getBusinessObjectOntology()
	{
		return bontology;
	}
	
	public OWLNamedIndividual getBusinessObject()
	{
		return OWL.individual(bontology.getOntology().getOntologyID().getOntologyIRI().resolve("#bo"));
	}
	
	public Workflow getWorkflow()
	{
		return workflow;
	}

	public void setWorkflow(Workflow workflow)
	{
		this.workflow = workflow;
	}

	public WorkflowStep getCurrentStep()
	{
		return currentStep;
	}
	
	public void setCurrentStep(WorkflowStep step)
	{
		this.currentStep = step;
	}
	
	public IRI getBusinessObjectTypeIri()
	{
		return bontology.getTypeIRI();
	}

	public String getBusinessObjectId()
	{
		return bontology.getObjectId();
	}

//	public Map<Pair<HGHandle, IRI>, Object> getVariables()
//	{
//		return variables;
//	}

	public void removeVariable(HGHandle taskId, IRI varname)
	{
		variables.remove(new Pair<HGHandle, IRI>(taskId, varname));
	}
	
	public void setVariable(HGHandle taskId, IRI varname, Object value)
	{
		if (EvalUtils.isVarGlobal(varname))
			setGlobalVariable(varname, value);
		else		
			variables.put(new Pair<HGHandle, IRI>(taskId, varname), value);
	}

	public Object getVariable(HGHandle taskId, IRI varname)
	{
		return variables.get(new Pair<HGHandle, IRI>(taskId, varname));
	}
	
	public Object getGlobalVariable(IRI varname)
	{
		return variables.get(new Pair<HGHandle, IRI>(graph.getHandleFactory().anyHandle(), varname));
	}
	
	public void setGlobalVariable(IRI varname, Object value)
	{
		variables.put(new Pair<HGHandle, IRI>(graph.getHandleFactory().anyHandle(), varname), value);
	}
	
//	public void setVariables(Map<Pair<HGHandle, IRI>, Object> variables)
//	{
//		this.variables = variables;
//	}
	
	public Stack<WorkflowStep> getHistory()
	{
		return history;
	}

	public void setHistory(Stack<WorkflowStep> history)
	{
		this.history = history;
	}

	@SuppressWarnings("unchecked")
	public <T> T valueOf(IRI varIri)
	{
		Object x = variables.get(varIri);
		return (T)x;
	}

	private Json varValueToJson(Object x)
	{
		Json j = Json.object();
		if (x instanceof OWLNamedObject)
		{
			if (x instanceof OWLIndividual)
				return j.set("individual", ((OWLNamedObject)x).getIRI().toString());
			else if (x instanceof OWLClass)
				return j.set("class", ((OWLNamedObject)x).getIRI().toString());
			else if (x instanceof OWLDataProperty)
				return j.set("dataproperty", ((OWLNamedObject)x).getIRI().toString());
			else if (x instanceof OWLObjectProperty)
				return j.set("objectproperty", ((OWLNamedObject)x).getIRI().toString());
			else
				throw new IllegalArgumentException("Unable to json-ify: " + x);
		}
		else if (x instanceof OWLLiteral)
		{
			return j.set("literal", ((OWLLiteral)x).getLiteral());
		}
		else
			try 
			{ 
				return j.set("json", Json.make(x)); 
			}
			catch (Exception ex) 
			{ 
				return j.set("serialized", GenUtils.serializeAsString(x)); 
			}
	}
	
	private Object varValueFromJson(Json j)
	{
		if (j.has("individual"))
			return individual(j.at("individual").asString());
		else if (j.has("class"))
			return owlClass(j.at("class").asString());
		else if (j.has("dataproperty"))
			return dataProperty(j.at("dataproperty").asString());
		else if (j.has("objectproperty"))
			return dataProperty(j.at("objectproperty").asString());
		else if (j.has("literal"))
			return OWL.dataFactory().getOWLLiteral(
					j.at("literal").asString());
		else if (j.has("json"))
			return j.at("json").getValue();
		else if (j.has("serialized"))
			return GenUtils.deserializeFromString(j.at("serialized").asString());
		else
			throw new IllegalArgumentException("Can't deserialize var value from " + j);
	}
	
	public Json toJSON()
	{
		Json jhist = Json.array();
		for (WorkflowStep step : this.history)
		{
			Json vars = Json.object();
			vars.set(RulesToWorkflow.boVar.toString(), varValueToJson(getBusinessObject()));
			if (step instanceof Task)
			{
				Task t = (Task)step;				
				for (IRI iri : t.getInputVariables().keySet())
				{
					Object value = this.variables.get(t.getInputVariables().get(iri));
					if (value != null)
						vars.set(iri.toString(), varValueToJson(value));
				}
			}				
			jhist.add(Workflow.toJSON(step).set("variables", vars));
		}
		Json vars = Json.array();
		for (Map.Entry<Pair<HGHandle, IRI>, Object> e : variables.entrySet())
		{
			vars.add(Json.object("step-handle", e.getKey().getFirst().getPersistent().toString(),
							     "iri", e.getKey().getSecond().toString(),
							     "value", varValueToJson(e.getValue())));
		}
		return Json.object()
					.set("variables", vars)
					.set("history", jhist)
					.set("currentStep", Workflow.toJSON(currentStep))
					.set("bo", bontology.toJSON());
	}
	
	public void fromJSON(Json json)
	{
		variables.clear();
		for (Json v : json.at("variables").asJsonList())
		{
			Pair<HGHandle, IRI> p = new Pair<HGHandle, IRI>(
					graph.getHandleFactory().makeHandle(v.at("step-handle").asString()),
					IRI.create(v.at("iri").asString()));
			variables.put(p, varValueFromJson(v.at("value")));
		}
		history.clear();
		for (Json h : json.at("history").asJsonList())
		{
			HGHandle handle = graph.getHandleFactory().makeHandle(h.at("id").asString());
			WorkflowStep step = graph.get(handle);
			history.add(step);
		}
		HGHandle currentStepHandle = graph.getHandleFactory().makeHandle(json.at("currentStep").at("id").asString());
		currentStep = graph.get(currentStepHandle);
		bontology = BOntology.makeRuntimeBOntology(json.at("bo"));
	}
}

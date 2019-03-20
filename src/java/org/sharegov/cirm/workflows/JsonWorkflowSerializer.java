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


import java.util.Map;
import static mjson.Json.*;
import mjson.Json;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.semanticweb.owlapi.model.OWLObject;
import org.sharegov.cirm.utils.EvalUtils;

public class JsonWorkflowSerializer
{
	private boolean includeFollowUps = false;
	
	public JsonWorkflowSerializer(boolean includeFollowUps)
	{
		this.includeFollowUps = includeFollowUps;
	}
	
	public Json apply(BranchOnAtomValue branch)
	{
		Json j = object()
			.set("id", branch.getAtomHandle().getPersistent().toString())
			.set("type", "branch-on-atom");
		if (this.includeFollowUps)
		{
			if (branch.getOnFalse() != null)
				j.set("onFalse", EvalUtils.dispatch(this, branch.getOnFalse()));
			if (branch.getOnTrue() != null)
				j.set("onTrue", EvalUtils.dispatch(this, branch.getOnTrue()));
			if (branch.getOnUnknown() != null)
				j.set("onUnknown", EvalUtils.dispatch(this, branch.getOnTrue()));
		}
		j.set("variable", branch.getSourceVariable().toString());
		return j;		
	}
	
	public Json apply(BranchOnBoolean branch)
	{
		Json j = Json.object()
			.set("id", branch.getAtomHandle().getPersistent().toString())
			.set("type", "branch-on-boolean");
		if (this.includeFollowUps)
		{
			if (branch.getOnFalse() != null)
				j.set("onFalse", EvalUtils.dispatch(this, branch.getOnFalse()));
			if (branch.getOnTrue() != null)
				j.set("onTrue", EvalUtils.dispatch(this, branch.getOnTrue()));
		}
		j.set("variable", branch.getSourceVariable().toString());
		return j;		
	}

	public Json apply(BranchOnPropertyValue branch)
	{
		Json j = Json.object()
			.set("id", branch.getAtomHandle().getPersistent().toString())
			.set("type", "branch-on-property")
			.set("property", branch.getPropertyId().toString())
			.set("individual", branch.getIndividualId().toString());
		if (this.includeFollowUps)
		{
			Json map = Json.object();		
			for (Map.Entry<OWLObject, WorkflowStep> e : branch.getBranchMap().entrySet())
			{
				if (e.getKey() instanceof OWLLiteral)
					map.set(((OWLLiteral)e.getKey()).getLiteral(), EvalUtils.dispatch(this, e.getValue()));
				else
					map.set(((OWLNamedObject)e.getKey()).getIRI().toString(), EvalUtils.dispatch(this, e.getValue()));
			}
			j.set("map", map);
		}
		return j;		
	}

	public Json apply(AtomEvalTask task)
	{
		Json j = Json.object()
			.set("id", task.getAtomHandle().getPersistent().toString())
			.set("type", "atom-eval-task")
			.set("atom", EvalUtils.dispatch(new JsonSWRLSerializer(), task.getAtom()));
		if (task.getNext() != null && this.includeFollowUps)
			j.set("next", EvalUtils.dispatch(this, task.getNext()));
		return j;		
	}
	
	public Json apply(AssertAtomTask task)
	{
		Json j = Json.object()
			.set("id", task.getAtomHandle().getPersistent().toString())
			.set("type", "assert-atom")
			.set("atom", EvalUtils.dispatch(new JsonSWRLSerializer(), task.getAtom()));			
		if (task.getNext() != null && this.includeFollowUps)
			j.set("next", EvalUtils.dispatch(this, task.getNext()));				
		return j;		
	}

//	public Json apply(PromptUserBuiltIn task)
//	{
//		Json j = Json.object()
//			.set("id", task.getAtomHandle().getPersistent().toString())
//			.set("type", "prompt-user")
//			.set("property", task.getPropertyId().toString());
//		if (task.getNext() != null && this.includeFollowUps)
//			j.set("next", EvalUtils.dispatch(this, task.getNext()));		
//		IRI objectId = task.getSubjectVarId();
//		if (objectId == null)
//			objectId = ((OWLNamedIndividual)task.getIndividual(null)).getIRI();
//		j.set("individual", objectId.toString());
//		return j;
//	}
	
//	public Json apply(PromptUserTask task)
//	{
//		Json j = Json.object()
//			.set("id", task.getAtomHandle().getPersistent().toString())
//			.set("type", "prompt-user")
//			.set("property", task.getPropertyId().toString());
//		if (task.getNext() != null && this.includeFollowUps)
//			j.set("next", EvalUtils.dispatch(this, task.getNext()));		
//		IRI objectId = task.getSubjectVarId();
//		if (objectId == null)
//			objectId = ((OWLNamedIndividual)task.getIndividual(null)).getIRI();
//		j.set("individual", objectId.toString());
//		return j;
//	}
	
	public Json apply(BuiltInAtomTask task)
	{
		Json j = Json.object()
			.set("id", task.getAtomHandle().getPersistent().toString())
			.set("type", task.getAtom().getAllArguments().iterator().next().toString());		
		if (task.getNext() != null && this.includeFollowUps)
			j.set("next", EvalUtils.dispatch(this, task.getNext()));
		return j;
	}

	public Json apply(WorkflowDone step)
	{
		return Json.object()
				.set("id", step.getAtomHandle().getPersistent().toString())
				.set("type", "end")
				.set("outcome", step.isSuccess());		
	}
	
	public Json apply(WorkflowStep step)
	{
		return object()
				.set("id", step.getAtomHandle().getPersistent().toString())
				.set("type", step.getClass().getSimpleName());
	}	
	
	public Json apply(WaitStep step)
	{
		return object()
			.set("id", step.getAtomHandle().getPersistent().toString())
			.set("type", "wait");
	}
}

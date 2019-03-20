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

import java.util.HashSet;
import java.util.Set;

import org.hypergraphdb.HGHandle;
import org.hypergraphdb.annotation.AtomReference;
import org.hypergraphdb.util.Pair;
import org.semanticweb.owlapi.model.IRI;

public class BranchOnAtomValue extends Branch
{
	@AtomReference("symbolic")
	private Task sourceTask;	
	private IRI sourceVariable;
	
	@AtomReference("symbolic")	
	private WorkflowStep onTrue;
	@AtomReference("symbolic")
	private WorkflowStep onFalse;
	@AtomReference("symbolic")
	private WorkflowStep onUnknown;

	public Set<WorkflowStep> getAlternatives()
	{
		HashSet<WorkflowStep> S = new HashSet<WorkflowStep>();
		S.add(onTrue);
		S.add(onFalse);
		S.add(onUnknown);
		return S;
	}
	
	public WorkflowStep perform(WorkflowExecutionContext context)
	{
//		Pair<HGHandle, IRI> varRef = new Pair<HGHandle, IRI>(sourceTask.getPrototypeId(), 
//															 AtomEvalTask.evalResultVar);
		AtomValue atomValue = (AtomValue)context.getVariable(sourceTask.getPrototypeId(), 
														AtomEvalTask.evalResultVar);
		//context.getVariables().get(varRef);
		if (atomValue == AtomValue.True)
			return onTrue;
		else if (atomValue == AtomValue.False)
			return onFalse;
		else
			return onUnknown;
	}

	public Task getSourceTask()
	{
		return sourceTask;
	}


	public void setSourceTask(Task sourceTask)
	{
		this.sourceTask = sourceTask;
	}


	public IRI getSourceVariable()
	{
		return sourceVariable;
	}


	public void setSourceVariable(IRI sourceVariable)
	{
		this.sourceVariable = sourceVariable;
	}


	public WorkflowStep getOnTrue()
	{
		return onTrue;
	}


	public void setOnTrue(WorkflowStep onTrue)
	{
		this.onTrue = onTrue;
	}


	public WorkflowStep getOnFalse()
	{
		return onFalse;
	}


	public void setOnFalse(WorkflowStep onFalse)
	{
		this.onFalse = onFalse;
	}


	public WorkflowStep getOnUnknown()
	{
		return onUnknown;
	}


	public void setOnUnknown(WorkflowStep onUnknown)
	{
		this.onUnknown = onUnknown;
	}
}

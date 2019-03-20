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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.semanticweb.owlapi.model.SWRLAtom;
import org.sharegov.cirm.utils.EvalUtils;
import org.sharegov.cirm.utils.JsonSerializable;

import mjson.Json;
import static mjson.Json.*;

public class Workflow implements JsonSerializable
{
	private WorkflowStep startingStep = null;
	private Set<SWRLAtom> endgoals = new HashSet<SWRLAtom>();
	private Set<String> eventRules = new HashSet<String>(); 
	
	public WorkflowStep getStartingStep()
	{
		return startingStep;
	}

	public void setStartingStep(WorkflowStep startingStep)
	{
		this.startingStep = startingStep;
	}

	public Set<SWRLAtom> getEndgoals()
	{
		return endgoals;
	}

	public void setEndgoals(Set<SWRLAtom> endgoals)
	{
		this.endgoals = endgoals;
	}	
	
	public Set<String> getEventRules()
	{
		return eventRules;
	}

	public void setEventRules(Set<String> eventRules)
	{
		this.eventRules = eventRules;
	}

	void stepToString(StringBuilder sb, WorkflowStep step, String indent)
	{
//		sb.append(indent);
		if (step == null)
		{
			sb.append("NULL workflow step\n");
			return;
		}
		String s = step.toString();
		sb.append(s);
		sb.append('\n');
		if (step instanceof WorkflowDone)
			return;
		char [] moreIndent = new char[s.length()];
		Arrays.fill(moreIndent, ' ');
		indent += "  ";//new String(moreIndent);
		sb.append(indent);		
		WorkflowStep next = ((Task)step).getNext();
		if (next instanceof BranchOnBoolean)
		{
			
			BranchOnBoolean b = (BranchOnBoolean)((Task)step).getNext(); 
			sb.append("yes -> ");
			stepToString(sb, b.getOnTrue(), indent + "       ");
			sb.append('\n');
			sb.append(indent);
			sb.append("no  -> ");
			stepToString(sb, b.getOnFalse(), indent + "       ");			
		}
		else if (next instanceof BranchOnAtomValue)
		{
			
			BranchOnAtomValue b = (BranchOnAtomValue)((Task)step).getNext(); 
			sb.append("yes -> ");
			stepToString(sb, b.getOnTrue(), indent + "       ");
			sb.append('\n');
			sb.append(indent);
			sb.append("no  -> ");
			stepToString(sb, b.getOnFalse(), indent + "       ");			
		}
		else
		{
			sb.append("->");
			stepToString(sb, next, indent + "  ");
		}
	}
	
	public String toString()
	{
		StringBuilder sb = new StringBuilder();		
		stepToString(sb, startingStep, "");
		return sb.toString();
	}
	
	public Json toJSON()
	{
		Json result = object().set("start", 
				(Json)EvalUtils.dispatch(
						new JsonWorkflowSerializer(true), this.startingStep));
		return result;
	}
	
	public static Json toJSON(WorkflowStep step) 
	{
		return (Json)EvalUtils.dispatch(
				new JsonWorkflowSerializer(false), step);		
	}
}

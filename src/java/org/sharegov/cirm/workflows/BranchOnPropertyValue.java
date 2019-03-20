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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLObject;

/**
 * 
 * <p>
 * Branch depending on the value of a property of a given individual.
 * </p>
 *
 * @author Borislav Iordanov
 *
 */
public class BranchOnPropertyValue extends Branch
{
	private IRI propertyId;
	private IRI individualId;
	private Map<OWLObject, WorkflowStep> branchMap = new HashMap<OWLObject, WorkflowStep>();
	
	public Set<WorkflowStep> getAlternatives()
	{
		HashSet<WorkflowStep> S = new HashSet<WorkflowStep>();
		S.addAll(branchMap.values());
		return S;
	}

	public WorkflowStep perform(WorkflowExecutionContext ctx)
	{
		WorkflowStep next = null;
		return next;
	}
	
	public IRI getPropertyId()
	{
		return propertyId;
	}
	public void setPropertyId(IRI propertyId)
	{
		this.propertyId = propertyId;
	}
	public IRI getIndividualId()
	{
		return individualId;
	}
	public void setIndividualId(IRI individualId)
	{
		this.individualId = individualId;
	}
	public Map<OWLObject, WorkflowStep> getBranchMap()
	{
		return branchMap;
	}
	public void setBranchMap(Map<OWLObject, WorkflowStep> branchMap)
	{
		this.branchMap = branchMap;
	}	
}

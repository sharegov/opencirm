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

import org.hypergraphdb.util.CloneMe;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.SWRLAtom;

/**
 * 
 * <p>
 * Represents a logical derivation path where premises are ordered according
 * to their dependencies (dependents come later in the path) and cost of satisfying
 * them so that the least costly are evaluated first and if they fail the more
 * costly won't have to be evaluated. The last element in a derivation path is always
 * a goal atom.
 * </p>
 * 
 * <p>
 * The <code>GoalSet</code> as a set itself holds the top-level independent atoms that
 * can be evaluated in any other. The logical path is represented by the graph of 
 * WorkflowPathElements (each has a set of dependents and a set of dependencies).
 * </p>
 * 
 * @author Borislav Iordanov
 *
 */
public class GoalSet extends HashSet<SWRLAtom> implements CloneMe
{
	private static final long serialVersionUID = -9199830701769643453L;
	Map<AtomPartialKey, WorkflowPathElement> pathElements = new HashMap<AtomPartialKey, WorkflowPathElement>();
	
	void putPathElements(Map<AtomPartialKey, WorkflowPathElement> elements)
	{
    	// first, clone workflow nodes without dependencies
    	for (Map.Entry<AtomPartialKey, WorkflowPathElement> e : elements.entrySet())
    	{
    		WorkflowPathElement el = new WorkflowPathElement(e.getValue().getAtom(), e.getValue().getOutputVar());
    		pathElements.put(e.getKey(), el);    		
    	}
    	// now that we have node copies,clone the dependencies
    	for (Map.Entry<AtomPartialKey, WorkflowPathElement> e : elements.entrySet())
    	{
    		WorkflowPathElement el = getWorkflowElement(e.getValue().getAtom());
    		for (Map.Entry<WorkflowPathElement, IRI> de : e.getValue().getDependents().entrySet())
    		{
    			el.getDependents().put(getWorkflowElement(de.getKey().getAtom()), de.getValue());
    		}
    		for (Map.Entry<WorkflowPathElement, IRI> de : e.getValue().getDependencies().entrySet())
    		{
    			el.getDependencies().put(getWorkflowElement(de.getKey().getAtom()), de.getValue());
    		}
    	}	
	}
	
	public GoalSet(Map<AtomPartialKey, WorkflowPathElement> globalWorkflowElements)
	{
		putPathElements(globalWorkflowElements);
	}
	
	public WorkflowPathElement getWorkflowElement(AtomPartialKey key)
	{
		return pathElements.get(key);
	}
	
	public WorkflowPathElement getWorkflowElement(SWRLAtom atom)
	{
		AtomPartialKey key = new AtomPartialKey(atom);
		WorkflowPathElement el = pathElements.get(key);
		if (el == null)
		{
			el = new WorkflowPathElement(atom);
			pathElements.put(key, el);
		}
		return el;
	}
	
    @SuppressWarnings("unchecked")
	public GoalSet duplicate()
    {
    	GoalSet S = new GoalSet(this.pathElements);
    	S.addAll(this);
    	return S;
    }
}

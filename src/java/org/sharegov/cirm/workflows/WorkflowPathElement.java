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
import java.util.Map;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.SWRLArgument;
import org.semanticweb.owlapi.model.SWRLAtom;
import org.semanticweb.owlapi.model.SWRLVariable;

/**
 * <p>
 * An auxiliary class used in the construction of workflows from SWRL rule sets. It represents a SWRLAtom
 * as a workflow step and holds data structures to represent dependencies with other such workflow path 
 * elements. 
 * </p>
 *
 * @author Borislav Iordanov
 *
 */
public class WorkflowPathElement
{
	private SWRLAtom atom;
	
	// For now at least, only one output variable per atom is supported, and this is
	// generally the variable at the 'object' position of a property predicate or the
	// last (result) variable in a builtin predicate.
	private IRI outputVar;
	
	// The following two maps manage dependencies between atoms's variables across different rules.
	// Since variables are local to rules, we may have different names in different instances of
	// logically the same atom. 
	
	// associate other elements that depend on this one with their variable that must be linked to this 'outputVar' 
	private Map<WorkflowPathElement, IRI> dependents = new HashMap<WorkflowPathElement, IRI>();
	
	// associate other element that this one depends on to the variable (in this element's atom) that 
	// must be linked to the dependency's 'outputVar'
	private Map<WorkflowPathElement, IRI> dependencies = new HashMap<WorkflowPathElement, IRI>();
	
	public WorkflowPathElement(SWRLAtom atom)
	{
		this.atom = atom;
	}
	
	public WorkflowPathElement(SWRLAtom atom, IRI outputVar)
	{
		this.atom = atom;
		this.outputVar = outputVar;
	}
	
	public boolean hasVar(IRI var)
	{
		for (SWRLArgument arg : atom.getAllArguments())
		{
			if (arg instanceof SWRLVariable && ((SWRLVariable)arg).getIRI().equals(var))
				return true;
		}
		return false;
	}
	public SWRLAtom getAtom()
	{
		return atom;
	}
	
	public void setOutputVar(IRI outputVar)
	{
		this.outputVar = outputVar;
	}
	
	public IRI getOutputVar()
	{
		return outputVar;
	}
	
	public Map<WorkflowPathElement, IRI> getDependents()
	{
		return dependents;
	}
	
	public Map<WorkflowPathElement, IRI> getDependencies()
	{
		return dependencies;
	}
	
	public boolean equals(Object x)
	{
		if (! (x instanceof WorkflowPathElement))
			return false;
		return AtomPartialKey.equals(atom, ((WorkflowPathElement)x).atom);
	}

	public int hashCode()
	{
		return AtomPartialKey.hashCode(atom);
	}	
	
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Work[atom:");
		sb.append(atom);
		sb.append("]");
		for (Map.Entry<WorkflowPathElement, IRI> e : dependents.entrySet())
		{
			sb.append("\n\t -> ");			
			sb.append(e.getValue().getFragment());
			sb.append(":");
			sb.append(e.getKey().getAtom());
		}
		for (Map.Entry<WorkflowPathElement, IRI> e : dependencies.entrySet())
		{
			sb.append("\n\t <- ");			
			sb.append(e.getValue().getFragment());
			sb.append(":");
			sb.append(e.getKey().getAtom());
		}
		return sb.toString();
	}
}

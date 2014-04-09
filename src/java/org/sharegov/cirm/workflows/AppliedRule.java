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

import org.hypergraphdb.app.owl.model.swrl.SWRLVariableHGDB;
import org.hypergraphdb.util.RefResolver;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.sharegov.cirm.utils.EvalUtils;


import java.util.*;

/**
 * 
 * <p>
 * An AppliedRule contains evaluation information for a single SWRLRule. The information
 * consists of the following maps:
 * 
 *  variables: contains the value of each variable we were able to resolve.
 *  truthValues: contains the result (true, false, unknown) of evaluating each atom in the rule's body
 *  instantiated: contains an instantiated version (i.e. with known variables replaced by their values) 
 *  				of each atom in the rule's head
 *  dependents/dependsOn: an atom A in the rule's body depends on another atom B if A contains a variable
 *  which was instantiated  from A and the same variable also occurs in B. The dependents map associates
 *  atoms with set of dependents and the dependsOn map is just the reverse relationship. Dependencies
 *  are used to order atoms in an execution workflow.
 * </p>
 *
 * @author Borislav Iordanov
 *
 */
public class AppliedRule
{	
	OWLOntologyManager manager;
	SWRLRule rule;
	Map<SWRLVariable, OWLObject> variables = new HashMap<SWRLVariable, OWLObject>();
	Map<SWRLAtom, AtomValue> truthValues = new HashMap<SWRLAtom, AtomValue>();
	Map<SWRLAtom, Set<SWRLAtom>> dependents = new HashMap<SWRLAtom, Set<SWRLAtom>>();
	Map<SWRLAtom, Set<SWRLAtom>> dependsOn = new HashMap<SWRLAtom, Set<SWRLAtom>>();
	Map<SWRLAtom, SWRLAtom> instantiated = new HashMap<SWRLAtom, SWRLAtom>();
	
	SWRLVariable getDependableVar(SWRLAtom atom, Set<SWRLAtom> depset)
	{
		System.out.println("Get DEP VAR in " + atom);
		Set<SWRLAtom> reverse = dependsOn.get(atom);
		Set<SWRLVariable> knownVars = new HashSet<SWRLVariable>();
		knownVars.add(new SWRLVariableHGDB(RulesToWorkflow.boVar));
		if (reverse != null) for (SWRLAtom dep : reverse)
		{
			if (depset.contains(dep))
				throw new RuntimeException("Circular dependency in " + depset);
			depset.add(dep);			
			SWRLVariable var = getDependableVar(dep, depset);
			if (var != null)
				knownVars.add(var);
		}
		return EvalUtils.getDependableVar(substituteVars(atom), knownVars); 		
	}
	
	SWRLVariable getDependableVar(SWRLAtom atom)
	{
		return getDependableVar(atom, new HashSet<SWRLAtom>(Collections.singleton(atom)));
	}
	
	void computeDependents()
	{
		if (rule.getBody().size() != truthValues.size())
			throw new RuntimeException("rule not evaluated yet");
		for (boolean done = false; !done; )
		{
			done = true;
			for (SWRLAtom atom : rule.getBody())
			{
				// the form we are expecting is (Predicate KnownSubject Variable) for
				// dependable atoms
				SWRLVariable var = getDependableVar(atom);
				if (var == null)
					continue;
				Set<SWRLAtom> S = dependents.get(atom);
				if (S == null)
				{
					S = new HashSet<SWRLAtom>();
					dependents.put(atom, S);
				}
				for (SWRLAtom other : rule.getBody())
				{
					if (other.equals(atom) || S.contains(other))
						continue;
					for (SWRLArgument arg : other.getAllArguments())
						if (arg instanceof SWRLVariable && var.equals(arg))
						{
							S.add(other);
							Set<SWRLAtom> reverseS = dependsOn.get(other);
							if (reverseS == null) 
							{ 
								reverseS = new HashSet<SWRLAtom>(); 
								dependsOn.put(other, reverseS); 
							}
							reverseS.add(atom);
							done = false;
							break;
						}
				}
			}
		}
		System.out.println("Dependson:" + dependsOn);
	}
	
	public SWRLRule getRule() { return rule; }
	
	public Map<SWRLVariable, OWLObject> getVariables() { return variables; }
	
	public SWRLAtom substituteVars(SWRLAtom a)
	{
		return instantiated.containsKey(a) ? instantiated.get(a) : a;
	}
	
	/**
	 * Does evaluation of this atom depend on variable instantiation by some
	 * other atom before it?
	 * 
	 * @param atom
	 * @return
	 */
	boolean isIndependent(SWRLAtom atom)
	{
		Set<SWRLAtom> S = dependsOn.get(atom);
		return S == null || S.isEmpty();
	}
	
	public Set<SWRLAtom> getUnknowns()
	{
		if (rule.getBody().size() != truthValues.size())
			throw new RuntimeException("rule not evaluated yet");
		HashSet<SWRLAtom> S = new HashSet<SWRLAtom>();
		for (Map.Entry<SWRLAtom, AtomValue> e : truthValues.entrySet())
			if (e.getValue() == AtomValue.Unknown)
				S.add(e.getKey());
		return S;
	}

	public AtomValue isSatisfied()
	{
		if (rule.getBody().size() != truthValues.size())
			throw new RuntimeException("rule not evaluated yet");
		AtomValue result = AtomValue.True;
		for (AtomValue x : truthValues.values())
			if (x == AtomValue.False)
				return AtomValue.False;
			else if (x == AtomValue.Unknown)
				result = AtomValue.Unknown;
		return result;
	}
	
	public void assign(SWRLVariable v, OWLObject x)
	{
		variables.put(v, x);
	}
	
	@SuppressWarnings("unchecked")
	public <T extends OWLObject> T valueOf(SWRLVariable v)
	{
		return (T)variables.get(v);
	}

	public RefResolver<SWRLVariable, OWLObject> getVarResolver()
	{
		return new RefResolver<SWRLVariable, OWLObject>()
		{
			public OWLObject resolve(SWRLVariable v) { return valueOf(v); }
		};
	}
	
	public int hashCode() { return rule.hashCode(); }
	public boolean equals(Object x) { return rule.equals(((AppliedRule)x).rule); }
}

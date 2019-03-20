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

import org.hypergraphdb.util.Mapping;
import org.hypergraphdb.util.Pair;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.SWRLArgument;
import org.semanticweb.owlapi.model.SWRLAtom;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.sharegov.cirm.utils.EvalUtils;

public class GlobalVarDependencyMap
{
	// The map is:
	// global var -> [set of atom that can instantiate it, set of atoms that use it]
	// 
	Map<IRI, Pair<Set<AtomPartialKey>, Set<AtomPartialKey>>> M = 
		new HashMap<IRI, Pair<Set<AtomPartialKey>, Set<AtomPartialKey>>>();
	
	private Pair<Set<AtomPartialKey>, Set<AtomPartialKey>> get(IRI var)
	{
		Pair<Set<AtomPartialKey>, Set<AtomPartialKey>> p = M.get(var);
		if (p == null)
		{
			p = new Pair<Set<AtomPartialKey>, Set<AtomPartialKey>>(
					new HashSet<AtomPartialKey>(), new HashSet<AtomPartialKey>());
			M.put(var, p);
		}
		return p;
	}
	
	public void addInstantiatingAtom(IRI var, SWRLAtom atom)
	{
		get(var).getFirst().add(new AtomPartialKey(atom));
	}
	
	public void addDependentAtom(IRI var, SWRLAtom atom)
	{
		get(var).getSecond().add(new AtomPartialKey(atom));
	}
	
	public Set<Pair<IRI,AtomPartialKey>> getDependencies(SWRLAtom atom,
			Mapping<Set<AtomPartialKey>, AtomPartialKey> selector)
	{
		// For each global variable in the atom, collect the atoms 
		// that instantiate it.
		Set<Pair<IRI,AtomPartialKey>> S = new HashSet<Pair<IRI,AtomPartialKey>>();
		AtomPartialKey key = new AtomPartialKey(atom);		
		for (SWRLArgument arg : atom.getAllArguments())
		{
			if (! (arg instanceof SWRLVariable)) continue;
			SWRLVariable var = (SWRLVariable)arg;
			if (!EvalUtils.isVarGlobal(var.getIRI())) continue;
			Pair<Set<AtomPartialKey>, Set<AtomPartialKey>> p = get(var.getIRI());
			if (!p.getSecond().contains(key))
				continue;
			AtomPartialKey selected = selector.eval(p.getFirst());
			if (selected != null)
				S.add(new Pair<IRI,AtomPartialKey>(var.getIRI(), selected));
		}
		return S;
	}
}

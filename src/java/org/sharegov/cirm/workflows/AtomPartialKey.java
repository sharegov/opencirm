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

import java.util.Iterator;

import org.semanticweb.owlapi.model.SWRLArgument;
import org.semanticweb.owlapi.model.SWRLAtom;
import org.semanticweb.owlapi.model.SWRLVariable;

/**
 * 
 * <p>
 * Represents a SWRL atom identity modulo the variables appearing in the 
 * atom. That is, when comparing atoms, the predicate and all arguments must
 * match. Two arguments match either if they are both variables (possibly with
 * different names) or if they are the same individual or data value.
 * </p>
 *
 * @author Borislav Iordanov
 *
 */
public class AtomPartialKey
{
	private SWRLAtom atom;
	
	public AtomPartialKey(SWRLAtom atom)
	{
		this.atom = atom;
	}
	
	public int hashCode()
	{
		return hashCode(atom);
	}
	
	public static int hashCode(SWRLAtom atom)
	{
		int hash = atom.getPredicate().hashCode();
		for (SWRLArgument arg : atom.getAllArguments())
			if (arg instanceof SWRLVariable)
				continue;
			else
				hash = 37 * hash + arg.hashCode();
		return hash;
	}
	
	public boolean equals(Object other)
	{
		if (! (other instanceof AtomPartialKey))
			return false;		
		return equals(atom, ((AtomPartialKey)other).atom);
	}
	
	public static boolean equals(SWRLAtom atom, SWRLAtom x)
	{
		if (!x.getPredicate().equals(atom.getPredicate()))
			return false;
		if (x.getAllArguments().size() != atom.getAllArguments().size())
			return false;
		Iterator<SWRLArgument> i1 = x.getAllArguments().iterator();
		Iterator<SWRLArgument> i2 = atom.getAllArguments().iterator();
		while (i1.hasNext())
		{
			SWRLArgument l = i1.next();
			SWRLArgument r = i2.next();
			if (l instanceof SWRLVariable && r instanceof SWRLVariable)
				continue;
			else if (!l.equals(r))
				return false;
		}
		return true;
	}
	
	public String toString()
	{
		return atom.toString();
	}
}

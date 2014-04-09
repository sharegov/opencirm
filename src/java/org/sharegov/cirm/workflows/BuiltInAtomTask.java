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
import org.hypergraphdb.HGHandle;
import org.hypergraphdb.util.Pair;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.SWRLBuiltInAtom;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.sharegov.cirm.rules.SWRLBuiltinImplementation;
import org.sharegov.cirm.utils.EvalUtils;

import static org.sharegov.cirm.OWL.*;

public class BuiltInAtomTask extends AtomEvalTask
{
	public BuiltInAtomTask()
	{		
	}
	
	public BuiltInAtomTask(SWRLBuiltInAtom atom)
	{
		super(atom);
	}
	
	protected AtomValue eval(final WorkflowExecutionContext ctx)
	{
		IRI builtinIri = ((SWRLBuiltInAtom)getAtom()).getPredicate(); 
		
		// Web Service case
		
//		if (builtInIri.startsWith(..."/webserivce"))
//		{
//		WebServiceImplementation = ...builtinIri.
//		
//		ca;; web serbce....
//		}
//		else
//		{
		SWRLBuiltinImplementation impl = findImplementation(SWRLBuiltinImplementation.class, 
															builtinIri);
		if (impl == null)
			throw new RuntimeException("Missing 'hasImplementation' property for builtin individual " + 
			   builtinIri);				
		Map<SWRLVariable, OWLObject> m = impl.eval(
				(SWRLBuiltInAtom)getAtom(), 
				ctx.getBusinessObjectOntology().getOntology(), 
				this.getVarResolver(ctx));
		if (m == null)
			return AtomValue.False;
		else
		{
			if (!m.isEmpty())
			{
				// In the rules-workflow engine that we have, only one output variable
				// is allowed per SWRL atom.
				SWRLVariable var = m.keySet().iterator().next();
				ctx.setVariable(getPrototypeId(), var.getIRI(), m.get(var));
//					ctx.getVariables().put(new Pair<HGHandle, IRI>(this.getPrototypeId(), var.getIRI()), 
//						m.get(var));
			}
			return AtomValue.True;
		}
	}
}

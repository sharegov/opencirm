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
package org.sharegov.cirm.rules;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hypergraphdb.util.RefResolver;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.SWRLBuiltInAtom;
import org.semanticweb.owlapi.model.SWRLDArgument;
import org.semanticweb.owlapi.model.SWRLLiteralArgument;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.sharegov.cirm.owl.SynchronizedOWLManager;

public class Merge implements SWRLBuiltinImplementation
{

	@Override
	public Map<SWRLVariable, OWLObject> eval(SWRLBuiltInAtom atom, OWLOntology ontology,
			RefResolver<SWRLVariable, OWLObject> varResolver)
	{
		Map<SWRLVariable, OWLObject> M = null;
		List<SWRLDArgument> arguments = atom.getArguments();
		if (arguments ==  null || arguments.size() < 1)
			return M;
		SWRLDArgument arg0 = arguments.get(0);
		OWLOntology o = resolveToOntology(arg0, varResolver);
		if( o == null )
			return M;
		List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
		for (OWLAxiom axiom: o.getAxioms())
		{
			changes.add(new AddAxiom(ontology, axiom));
		}
		if(!changes.isEmpty())
			ontology.getOWLOntologyManager().applyChanges(changes);
		M = new HashMap<SWRLVariable, OWLObject>();
		if( arguments.size() == 2)
		{
			SWRLDArgument arg1 = arguments.get(1);
			if(arg1 instanceof SWRLVariable)
			{
				M.put((SWRLVariable) arg1, ontology.getOWLOntologyManager().getOWLDataFactory().getOWLLiteral(true));
			}
		}
		return M;
	}
	
	private OWLOntology resolveToOntology(SWRLDArgument a, RefResolver<SWRLVariable, OWLObject> varResolver)
	{
		OWLOntologyManager manager = SynchronizedOWLManager.createOWLOntologyManager();
		OWLOntology result = null;
		try
		{
			if (a instanceof SWRLVariable)
			{
			OWLObject o = varResolver.resolve((SWRLVariable)a); 
			if(o instanceof OWLLiteral)
				result = manager.loadOntologyFromOntologyDocument(new ByteArrayInputStream(((OWLLiteral)o).toString().getBytes())); 
			else if (o instanceof OWLOntology)
				result = (OWLOntology)o;
			}
			else if(a instanceof SWRLLiteralArgument)
				result = manager.loadOntologyFromOntologyDocument(new ByteArrayInputStream((((SWRLLiteralArgument)a).getLiteral()).toString().getBytes()));
			
		}catch(Exception e)
		{
			System.out.println("Cannot resolve argument to an ontology.");
			e.printStackTrace();
		}
		return result;
	}

}

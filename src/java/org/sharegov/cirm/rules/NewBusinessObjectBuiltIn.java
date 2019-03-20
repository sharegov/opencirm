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


import java.util.HashMap;
import java.util.Map;
import static org.sharegov.cirm.OWL.*;
import org.hypergraphdb.util.RefResolver;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.SWRLBuiltInAtom;
import org.semanticweb.owlapi.model.SWRLDArgument;
import org.semanticweb.owlapi.model.SWRLLiteralArgument;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.sharegov.cirm.rest.OperationService;


public class NewBusinessObjectBuiltIn implements SWRLBuiltinImplementation
{
	public Map<SWRLVariable, OWLObject> eval(
					 SWRLBuiltInAtom atom,
					 OWLOntology ontology,					 
					 RefResolver<SWRLVariable, OWLObject> varResolver)
	{
		HashMap<SWRLVariable, OWLObject> M = new HashMap<SWRLVariable, OWLObject>(); 
		String typeName = null;
		SWRLDArgument typeArg = atom.getArguments().get(0);
		if (typeArg instanceof SWRLVariable)
		{
			OWLLiteral literal = (OWLLiteral)varResolver.resolve((SWRLVariable)typeArg);
			typeName = literal.getLiteral();
		}
		else
		{
			typeName = ((SWRLLiteralArgument)typeArg).getLiteral().getLiteral();
		}
		OWLClass type = owlClass(typeName);
		OWLOntology boOntology = new OperationService().createBusinessObject(type).getOntology();
		M.put((SWRLVariable)atom.getArguments().get(1), businessObject(boOntology));
		return M;
	}
}

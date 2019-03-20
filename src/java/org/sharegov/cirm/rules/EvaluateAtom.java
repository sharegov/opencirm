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

import org.hypergraphdb.util.RefResolver;

import org.semanticweb.owlapi.model.*;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.workflows.AtomValue;

public class EvaluateAtom
{
	RefResolver<SWRLVariable, OWLObject> varResolver = null;
	OWLOntology ontology;
	
	public EvaluateAtom(RefResolver<SWRLVariable, OWLObject> varResolver, OWLOntology ontology)
	{
		this.varResolver = varResolver;
		this.ontology = ontology;
	}
	
	public AtomValue apply(SWRLAtom atom)		
	{
		return AtomValue.Unknown;
	}
	
	public AtomValue apply(SWRLSameIndividualAtom atom)
	{
		OWLIndividual ind1 = null; 
		if (atom.getFirstArgument() instanceof SWRLIndividualArgument)
			ind1 = ((SWRLIndividualArgument)atom.getFirstArgument()).getIndividual();
		else 
			ind1 = (OWLIndividual)varResolver.resolve((SWRLVariable)atom.getFirstArgument());

		OWLIndividual ind2 = null;
		if (atom.getSecondArgument() instanceof SWRLIndividualArgument)
			ind2 = ((SWRLIndividualArgument)atom.getSecondArgument()).getIndividual();
		else 
			ind2 = (OWLIndividual)varResolver.resolve((SWRLVariable)atom.getSecondArgument());
		
		if (ind1 == null || ind2 == null)
			return AtomValue.Unknown;
		return ind1.equals(ind2) ? AtomValue.True : AtomValue.False;
	}
	
	public AtomValue apply(SWRLDataPropertyAtom atom)
	{
		OWLIndividual ind = null; 
		if (atom.getFirstArgument() instanceof SWRLIndividualArgument)
			ind = ((SWRLIndividualArgument)atom.getFirstArgument()).getIndividual();
		else 
			ind = (OWLIndividual)varResolver.resolve((SWRLVariable)atom.getFirstArgument());
		
		OWLLiteral literal = null;
		if (atom.getSecondArgument() instanceof SWRLLiteralArgument)
			literal = ((SWRLLiteralArgument)atom.getSecondArgument()).getLiteral();
		else
			literal = (OWLLiteral)varResolver.resolve(((SWRLVariable)atom.getSecondArgument()));
		
		if (ind == null || literal == null)
			return AtomValue.Unknown;
		
		for (OWLDataPropertyAssertionAxiom axiom : ontology.getDataPropertyAssertionAxioms(ind))
		{
			if (axiom.getProperty().equals(atom.getPredicate()) && axiom.getSubject().equals(ind))
				return axiom.getObject().equals(literal) ? AtomValue.True : AtomValue.False;
		}		
		for (OWLDataPropertyAssertionAxiom axiom :OWL.ontology().getDataPropertyAssertionAxioms(ind))
		{
			if (axiom.getProperty().equals(atom.getPredicate()) && axiom.getSubject().equals(ind))
				return axiom.getObject().equals(literal) ? AtomValue.True : AtomValue.False;
		}		
		return AtomValue.Unknown;
	}
	
	public AtomValue apply(SWRLObjectPropertyAtom atom)
	{
		OWLIndividual subject = null; 
		if (atom.getFirstArgument() instanceof SWRLIndividualArgument)
			subject = ((SWRLIndividualArgument)atom.getFirstArgument()).getIndividual();
		else 
			subject = (OWLIndividual)varResolver.resolve((SWRLVariable)atom.getFirstArgument());
		
		OWLIndividual object = null; 
		if (atom.getSecondArgument() instanceof SWRLIndividualArgument)
			object = ((SWRLIndividualArgument)atom.getSecondArgument()).getIndividual();
		else 
			object = (OWLIndividual)varResolver.resolve((SWRLVariable)atom.getSecondArgument());
		
		if (subject == null || object == null)
			return AtomValue.Unknown;
		
		for (OWLObjectPropertyAssertionAxiom axiom : ontology.getObjectPropertyAssertionAxioms(subject))
		{
			if (!axiom.getProperty().equals(atom.getPredicate()))
				continue;
			if (subject.equals(axiom.getSubject()))
				return object.equals(axiom.getObject()) ? AtomValue.True : AtomValue.False;
		}
		
		return AtomValue.Unknown;
	}		
}

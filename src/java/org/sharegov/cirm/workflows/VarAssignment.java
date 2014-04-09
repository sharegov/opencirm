/**
 * 
 */
package org.sharegov.cirm.workflows;

import org.hypergraphdb.util.Pair;

import org.hypergraphdb.util.RefResolver;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.SWRLDataPropertyAtom;
import org.semanticweb.owlapi.model.SWRLIndividualArgument;
import org.semanticweb.owlapi.model.SWRLObjectPropertyAtom;
import org.semanticweb.owlapi.model.SWRLSameIndividualAtom;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.sharegov.cirm.OWL;

public class VarAssignment 
	{
//		AppliedRule rule;
		RefResolver<SWRLVariable, OWLObject> varResolver;
		OWLOntology ontology;
		
		VarAssignment(OWLOntology ontology, RefResolver<SWRLVariable, OWLObject> varResolver)
		{
			this.ontology = ontology;
			this.varResolver = varResolver;
		}
		
		public Pair<SWRLVariable, OWLObject> apply(Object x)
		{
			return null;
		}
		
		public Pair<SWRLVariable, OWLObject> apply(SWRLSameIndividualAtom atom)
		{
			SWRLSameIndividualAtom x = (SWRLSameIndividualAtom)atom;
			SWRLVariable vararg = (x.getFirstArgument() instanceof SWRLVariable) ?
					(SWRLVariable)x.getFirstArgument() :
						(x.getSecondArgument() instanceof SWRLVariable) ?
								(SWRLVariable)x.getSecondArgument() : null;
			OWLIndividual indarg = (x.getFirstArgument() instanceof SWRLIndividualArgument) ?
					((SWRLIndividualArgument)x.getFirstArgument()).getIndividual() :
						(x.getSecondArgument() instanceof SWRLIndividualArgument) ?
								((SWRLIndividualArgument)x.getSecondArgument()).getIndividual() : null;
			if (vararg != null && indarg != null)
				return new Pair<SWRLVariable, OWLObject>(vararg, indarg); 
			else
				return null;
		}
		
		public Pair<SWRLVariable, OWLObject> apply(SWRLDataPropertyAtom atom)
		{
			if (! (atom.getSecondArgument() instanceof SWRLVariable))
				return null;
			OWLIndividual ind = null; 
			if (atom.getFirstArgument() instanceof OWLNamedIndividual)
				ind = (OWLIndividual)atom.getFirstArgument();
			else if (atom.getFirstArgument() instanceof SWRLIndividualArgument)
				ind = ((SWRLIndividualArgument)atom.getFirstArgument()).getIndividual();
			else 
				ind = (OWLIndividual)varResolver.resolve((SWRLVariable)atom.getFirstArgument());
			if (ind == null)
				return null;			
			for (OWLDataPropertyAssertionAxiom axiom : ontology.getDataPropertyAssertionAxioms(ind))
			{
				if (axiom.getProperty().equals(atom.getPredicate()) && axiom.getSubject().equals(ind))
					return new Pair<SWRLVariable, OWLObject>((SWRLVariable)atom.getSecondArgument(), 
															  axiom.getObject());
			}			
			for (OWLDataPropertyAssertionAxiom axiom : OWL.ontology().getDataPropertyAssertionAxioms(ind))
			{
				if (axiom.getProperty().equals(atom.getPredicate()) && axiom.getSubject().equals(ind))
					return new Pair<SWRLVariable, OWLObject>((SWRLVariable)atom.getSecondArgument(), 
															  axiom.getObject());
			}						
			return null;
		}
		
		public Pair<SWRLVariable, OWLObject> apply(SWRLObjectPropertyAtom atom)
		{
			if (! (atom.getSecondArgument() instanceof SWRLVariable))
				return null;
			OWLIndividual ind = null; 
			if (atom.getFirstArgument() instanceof OWLNamedIndividual)
				ind = (OWLIndividual)atom.getFirstArgument();
			else if (atom.getFirstArgument() instanceof SWRLIndividualArgument)
				ind = ((SWRLIndividualArgument)atom.getFirstArgument()).getIndividual();			
			else 
				ind = (OWLIndividual)varResolver.resolve((SWRLVariable)atom.getFirstArgument());
			if (ind == null)
				return null;
			for (OWLObjectPropertyAssertionAxiom axiom : ontology.getObjectPropertyAssertionAxioms(ind))
			{
				if (!axiom.getProperty().equals(atom.getPredicate()))
					continue;
				if (ind.equals(axiom.getSubject()))
					return new Pair<SWRLVariable, OWLObject>((SWRLVariable)atom.getSecondArgument(),axiom.getObject());
			}
			return null;
		}
	}
/**
 * 
 */
package org.sharegov.cirm.workflows;

import org.hypergraphdb.app.owl.model.OWLLiteralHGDB;
import org.hypergraphdb.app.owl.model.swrl.SWRLBuiltInAtomHGDB;
import org.hypergraphdb.app.owl.model.swrl.SWRLDataPropertyAtomHGDB;
import org.hypergraphdb.app.owl.model.swrl.SWRLIndividualArgumentHGDB;
import org.hypergraphdb.app.owl.model.swrl.SWRLLiteralArgumentHGDB;
import org.hypergraphdb.app.owl.model.swrl.SWRLObjectPropertyAtomHGDB;
import org.hypergraphdb.app.owl.model.swrl.SWRLSameIndividualAtomHGDB;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.SWRLArgument;
import org.semanticweb.owlapi.model.SWRLAtom;
import org.semanticweb.owlapi.model.SWRLBuiltInAtom;
import org.semanticweb.owlapi.model.SWRLDArgument;
import org.semanticweb.owlapi.model.SWRLDataPropertyAtom;
import org.semanticweb.owlapi.model.SWRLIArgument;
import org.semanticweb.owlapi.model.SWRLObjectPropertyAtom;
import org.semanticweb.owlapi.model.SWRLSameIndividualAtom;
import org.semanticweb.owlapi.model.SWRLVariable;

public class AtomInstantiation
{
	AppliedRule rule;

	AtomInstantiation(AppliedRule rule)
	{
		this.rule = rule;
	}

	SWRLArgument instantiate(SWRLArgument arg)
	{
		if (!(arg instanceof SWRLVariable))
			return arg;
		// We don't want to instantiate the variable pointing to the business
		// object
		// because it may be just a prototypical object. We want to keep that
		// variable
		// in the workflow atoms so it gets dynamically bound when the workflow
		// is executed.
		// if (((SWRLVariable)arg).getIRI().equals(boVar))
		// return arg;
		OWLObject x = rule.valueOf((SWRLVariable) arg);
		if (x == null)
			return arg;
		else if (x instanceof OWLIndividual)
			return rule.manager.getOWLDataFactory().getSWRLIndividualArgument((OWLIndividual) x);
		else
			return new SWRLLiteralArgumentHGDB((OWLLiteralHGDB) x);
	}

	public SWRLAtom apply(SWRLAtom atom)
	{
		return atom;
	}

	public SWRLAtom apply(SWRLBuiltInAtom atom)
	{
		SWRLBuiltInAtomHGDB instance = new SWRLBuiltInAtomHGDB(atom.getPredicate());
		for (SWRLDArgument arg : atom.getArguments())
			instance.getArguments().add((SWRLDArgument) instantiate(arg));
		return instance;
	}

	public SWRLAtom apply(SWRLSameIndividualAtom atom)
	{
		SWRLAtom instance = new SWRLSameIndividualAtomHGDB((SWRLIArgument) instantiate(atom.getFirstArgument()),
				(SWRLIArgument) instantiate(atom.getSecondArgument()));
		return instance;
	}

	public SWRLAtom apply(SWRLDataPropertyAtom atom)
	{
		SWRLAtom instance = new SWRLDataPropertyAtomHGDB(atom.getPredicate(), (SWRLIArgument) instantiate(atom
				.getFirstArgument()), (SWRLDArgument) instantiate(atom.getSecondArgument()));
		return instance;
	}

	public SWRLAtom apply(SWRLObjectPropertyAtom atom)
	{
		SWRLAtom instance = new SWRLObjectPropertyAtomHGDB(atom.getPredicate(), (SWRLIArgument) instantiate(atom
				.getFirstArgument()), (SWRLIArgument) instantiate(atom.getSecondArgument()));
		return instance;
	}
}
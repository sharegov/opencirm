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

import org.semanticweb.owlapi.model.*;
import org.sharegov.cirm.OWL;

/**
 * 
 * <p>
 * This task executes an assertion of an ontology fact (an OWLAxiom) into
 * the business object ontology associated with the current workflow execution
 * context. The assertion is represented by a SWRLAtom. When that atom has variables
 * in it, an attempt is made to obtain them from the context. If they are free (i.e.
 * with no values), there will be exceptions...
 * </p>
 *
 * @author Borislav Iordanov
 *
 */
public class AssertAtomTask extends AtomBasedTask implements WorkflowUndoableStep
{
	private OWLAxiom makeAxiom(WorkflowExecutionContext ctx, OWLDataFactory df)
	{
		if (atom instanceof SWRLDataPropertyAtom)
		{
			SWRLDataPropertyAtom datom = (SWRLDataPropertyAtom) atom;
			OWLIndividual ind = getIndividual(ctx, datom.getFirstArgument());
			if (ind == null)
				throw new RuntimeException("No subject to assert in atom " + datom);
			OWLLiteral literal = getLiteral(ctx, datom.getSecondArgument());
			if (literal == null)
				throw new RuntimeException("No data to assert in atom " + datom);
			return df.getOWLDataPropertyAssertionAxiom(datom.getPredicate(),
					ind, literal);
		}
		else if (atom instanceof SWRLObjectPropertyAtom)
		{
			SWRLObjectPropertyAtom datom = (SWRLObjectPropertyAtom) atom;
			OWLIndividual subject = getIndividual(ctx, datom.getFirstArgument());
			if (subject == null)
				throw new RuntimeException("No subject to assert in atom " + datom);
			OWLIndividual object = getIndividual(ctx, datom.getSecondArgument());
			if (object == null)
				 throw new RuntimeException("No object to assert in atom " + datom);
			return df.getOWLObjectPropertyAssertionAxiom(datom.getPredicate(),
					subject, object);
		}
		return null;
	}
	
	public AssertAtomTask()
	{
	}

	public AssertAtomTask(SWRLAtom atom)
	{
		this.atom = atom;
	}
	
	public SWRLAtom getAtom()
	{
		return atom;
	}

	public void setAtom(SWRLAtom atom)
	{
		this.atom = atom;
	}

	public void backtrack(WorkflowExecutionContext ctx)
	{
		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory df = manager.getOWLDataFactory();
		OWLAxiom axiom = makeAxiom(ctx, df);
		if (axiom == null)
			throw new WorkflowException("Could not create ontology axiom from SWRLAtom : " + atom);
		manager.applyChange(new RemoveAxiom(ctx.getBusinessObjectOntology().getOntology(),
											axiom));		
	}
	
	public WorkflowStep perform(WorkflowExecutionContext ctx)
	{
		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory df = manager.getOWLDataFactory();
		OWLAxiom axiom = makeAxiom(ctx, df);
		if (axiom == null)
			throw new WorkflowException("Could not create ontology axiom from SWRLAtom : " + atom);
		manager.applyChange(new AddAxiom(ctx.getBusinessObjectOntology().getOntology(),
						axiom));
		return getNext();
	}
	
	public String toString()
	{
		String pred = atom.getPredicate().toString();
		pred = pred.substring(1, pred.length() - 1);
		pred = IRI.create(pred).getFragment();
		return "Assert:" + pred;
	}

	@Override
	public int hashCode()
	{
		return atom.hashCode();
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (getClass() != obj.getClass())
			return false;
		AssertAtomTask other = (AssertAtomTask) obj;
//		if (Double.doubleToLongBits(assignedScore) != Double
//				.doubleToLongBits(other.assignedScore))
//			return false;
		if (!other.atom.equals(atom))
			return false;
		return true;
//		return super.equals(obj);
	}
}

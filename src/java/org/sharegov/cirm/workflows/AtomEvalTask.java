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

import org.hypergraphdb.annotation.AtomReference;
import org.hypergraphdb.util.Pair;
import org.hypergraphdb.util.RefResolver;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.SWRLAtom;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.sharegov.cirm.rules.EvaluateAtom;
import org.sharegov.cirm.utils.EvalUtils;

/**
 * 
 * <p>
 * An <code>AtomEvalTask</code> evaluates a given {@link SWRLAtom} against
 * the business object ontology of a workflow and puts the result in a predefined
 * output variable of the task with IRI <em>http://www.miamiade.gov/swrl#atomEvalResult</em>.
 * </p>
 *
 * @author Borislav Iordanov
 *
 */
public class AtomEvalTask extends AtomBasedTask implements WorkflowUndoableStep
{
	public static final IRI evalResultVar = IRI.create("http://www.miamiade.gov/swrl#atomEvalResult");
		
	protected AtomValue eval(final WorkflowExecutionContext ctx)
	{ 
		final RefResolver<SWRLVariable, OWLObject> ctxResolver = getVarResolver(ctx);
		
		// The evaluation of an atom may have the side-effect of assigning a variable 
		// if that variable is at the 'object' position of a property assertion. If there's
		// such an assignment, it becomes an output variable from this task to tasks downstream.
		// If the variable is global, we want to record it in the global execution context.
		final Pair<SWRLVariable, OWLObject> assignment = EvalUtils.dispatch(
				new VarAssignment(ctx.getBusinessObjectOntology().getOntology(), ctxResolver), atom);
		
		if (assignment != null)
		{
			IRI variri = assignment.getFirst().getIRI(); 
			if (this.getOutputVariables().contains(variri))
				ctx.setVariable(getPrototypeId(), variri, assignment.getSecond());
//				ctx.getVariables().put(new Pair<HGHandle, IRI>(getPrototypeId(), variri), 
//						assignment.getSecond());
		}
		RefResolver<SWRLVariable, OWLObject> resolver = new RefResolver<SWRLVariable, OWLObject>()
		{
			public OWLObject resolve(SWRLVariable v)
			{
				if (assignment != null && assignment.getFirst().equals(v))
					return assignment.getSecond();
				else
					return ctxResolver.resolve(v);
			}
		};
		
		AtomValue val = EvalUtils.dispatch(new EvaluateAtom(resolver, 
															ctx.getBusinessObjectOntology().getOntology()), 
															atom);
		return val;// == AtomValue.True ? true : false;
	}
	
	public AtomEvalTask()
	{		
	}
	
	public AtomEvalTask(SWRLAtom atom)
	{
		this.atom = atom;
	}

	public void backtrack(WorkflowExecutionContext ctx)
	{
		//Pair<HGHandle, IRI> varRef = new Pair<HGHandle, IRI>(getPrototypeId(), evalResultVar);
		ctx.removeVariable(getPrototypeId(), evalResultVar);// .getVariables().remove(varRef);
		for (IRI iri : this.getOutputVariables())
		{
		//	varRef = new Pair<HGHandle, IRI>(getPrototypeId(), iri);
			//ctx.getVariables().remove(varRef);
			ctx.removeVariable(getPrototypeId(), iri);
		}
	}
	
	public WorkflowStep perform(WorkflowExecutionContext ctx)
	{
		//Pair<HGHandle, IRI> varRef = new Pair<HGHandle, IRI>(getPrototypeId(), evalResultVar);
		Object result = ctx.getVariable(getPrototypeId(), evalResultVar);// .getVariables().get(varRef);
		if (result == null)
		{
			result = eval(ctx);
			ctx.setVariable(getPrototypeId(), evalResultVar, result);
		}
		return getNext();
	}
	
	public boolean equals(Object x)
	{
		if (! (x instanceof AtomEvalTask))
			return false;
		return AtomPartialKey.equals(atom, ((AtomEvalTask)x).atom);
	}

	public int hashCode()
	{
		return AtomPartialKey.hashCode(atom);
	}
	
	public String toString()
	{
		String pred = atom.getPredicate().toString();
		pred = pred.substring(1, pred.length() - 1);
		pred = IRI.create(pred).getFragment();
		return "Task:" + pred;
	}
}

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
import org.sharegov.cirm.Refs;

import static org.sharegov.cirm.OWL.*;

/**
 * 
 * <p>
 * Converts a SWRLAtom into a workflow task. The results is always an instance of
 * {@link AtomEvalTask}. The RulesToWorkflow translator won't creates tasks for atoms that can be
 * evaluated right away, so this class always assumes that something must be "done"
 * to evaluate the atom. 
 * </p>
 * @author Borislav Iordanov
 *
 */
public class StepFactory
{
	public StepFactory()
	{
	}
	
	public WorkflowStep createStep(SWRLAtom atom)
	{
		if (atom instanceof SWRLBuiltInAtom)
		{
			IRI builtinIri = ((SWRLBuiltInAtom)atom).getPredicate();
			WorkflowStep stepImpl = findImplementation(WorkflowStep.class, builtinIri);
			if (stepImpl != null)
			{
				if (stepImpl instanceof AtomEvalTask)
				{
					((AtomEvalTask)stepImpl).setAtom(atom);
				}
				return stepImpl;
			}
			else // assume a SWRLBuiltinImplementation available for evaluation
				return new BuiltInAtomTask((SWRLBuiltInAtom)atom);
		}
		/*
		if (! (atom.getPredicate() instanceof OWLProperty))
		{
			return new AtomEvalTask(atom);
		}
		OWLProperty prop = (OWLProperty)atom.getPredicate();
		OWLNamedIndividual punnedProp = individual(prop.getIRI());
		OWLNamedIndividual propertyResolver = objectProperty(punnedProp, 
															 OWLRefs.hasPropertyResolver);
//		System.out.println("Property resolver for property " + punnedProp + " is " + propertyResolver);
		if (propertyResolver != null)
		{
			String classname = propertyResolver.getIRI().getFragment();
			try
			{
				AtomEvalTask task = (AtomEvalTask)Class.forName(classname).newInstance();
				task.setAtom(atom);
				return task;
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		}
		*/
		return new AtomEvalTask(atom);
	}
}

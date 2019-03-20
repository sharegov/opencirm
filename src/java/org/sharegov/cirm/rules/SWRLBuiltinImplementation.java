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

import java.util.Map;

import org.hypergraphdb.util.RefResolver;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.SWRLBuiltInAtom;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.sharegov.cirm.workflows.WorkflowExecutionContext;

/**
 * 
 * <p>
 * Implementations of SWRL built-ins for the purpose of workflow execution. 
 * The implementations operate on {@link WorkflowExecutionContext}
 * </p>
 *
 * @author Borislav Iordanov
 *
 */
public interface SWRLBuiltinImplementation
{
	/**
	 * <p>
	 * The evaluation of a builtin can have side-effect in modifying the ontology. The returned
	 * map contains all instantiated variable arguments of the built-in. If that return value is
	 * null, the result of the builtin evaluation should be considered false (fail).
	 * </p>
	 * 
	 * @param atom The {@link SWRLBuiltInAtom} to evaluate.
	 * @param ontology The ontology against which the evaluation happens.
	 * @param varResolver Variable resolver of any variable appearing in the <code>atom</code>
	 * parameter. The implementation cannot overwrite the value of a variable (in the return map)
	 * for which this varResolver does not return null.
	 */
	Map<SWRLVariable, OWLObject> eval(
			 SWRLBuiltInAtom atom,
			 OWLOntology ontology,					 
			 RefResolver<SWRLVariable, OWLObject> varResolver);
}

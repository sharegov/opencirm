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
import java.util.List;
import java.util.Map;

import org.hypergraphdb.util.RefResolver;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.SWRLBuiltInAtom;
import org.semanticweb.owlapi.model.SWRLDArgument;
import org.semanticweb.owlapi.model.SWRLLiteralArgument;
import org.semanticweb.owlapi.model.SWRLVariable;

public class StringConcat implements SWRLBuiltinImplementation
{

	@Override
	public Map<SWRLVariable, OWLObject> eval(SWRLBuiltInAtom atom, OWLOntology ontology,
			RefResolver<SWRLVariable, OWLObject> varResolver)
	{
		Map<SWRLVariable, OWLObject> M = null;
		List<SWRLDArgument> arguments = atom.getArguments();
		//arguments
		//1-(n-1). a sequence of literal to concat
		//(n-1). the variable to put the result concat.
		if (arguments ==  null || arguments.size() < 2)
			return M;
		StringBuffer concat = new StringBuffer();
		for (int i = 0; i < arguments.size() - 2; i ++)
		{
			SWRLDArgument arg = arguments.get(0);
			OWLLiteral l = resolveToLiteral(arg, varResolver);
			if(l == null)
				continue;
			concat.append(l.getLiteral());
		}
		OWLLiteral result = ontology.getOWLOntologyManager().getOWLDataFactory().getOWLLiteral(concat.toString());
		SWRLDArgument a = arguments.get(arguments.size()-1);
		if(a instanceof SWRLVariable)
		{
			M = new HashMap<SWRLVariable, OWLObject>();
			M.put((SWRLVariable)a,result);
		}
		return M;
	}
	private OWLLiteral resolveToLiteral(SWRLDArgument a, RefResolver<SWRLVariable, OWLObject> varResolver)
	{
		OWLLiteral result = null;
		if (a instanceof SWRLVariable)
		{
			OWLObject o = varResolver.resolve((SWRLVariable)a); 
			if(o instanceof OWLLiteral)
				result =(OWLLiteral)o; 
		}
		else if(a instanceof SWRLLiteralArgument)
			result = ((SWRLLiteralArgument)a).getLiteral();
		return result;
	}

}

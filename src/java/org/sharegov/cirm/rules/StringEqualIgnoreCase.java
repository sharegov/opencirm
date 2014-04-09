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

import javax.xml.xpath.XPathExpressionException;

import org.hypergraphdb.util.RefResolver;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.SWRLBuiltInAtom;
import org.semanticweb.owlapi.model.SWRLDArgument;
import org.semanticweb.owlapi.model.SWRLLiteralArgument;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.semanticweb.owlapi.util.DefaultPrefixManager;

public class StringEqualIgnoreCase implements SWRLBuiltinImplementation
{

	@Override
	public Map<SWRLVariable, OWLObject> eval(SWRLBuiltInAtom atom, OWLOntology ontology,
			RefResolver<SWRLVariable, OWLObject> varResolver)
	{
		Map<SWRLVariable, OWLObject> M = null;
		List<SWRLDArgument> arguments = atom.getArguments();
		//arguments
		//1. the first string
		//2. the second string
		if (arguments ==  null || arguments.size() < 2)
			return M;
		SWRLDArgument arg0 = arguments.get(0);
		SWRLDArgument arg1 = arguments.get(1);
		OWLLiteral string0 = resolveToLiteral(arg0, varResolver);
		OWLLiteral string1 = resolveToLiteral(arg1, varResolver);
		if(string0 == null || string1 == null)
			return M;
		if(string0.getLiteral().equalsIgnoreCase(string1.getLiteral()))
			M = new HashMap<SWRLVariable, OWLObject>();
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

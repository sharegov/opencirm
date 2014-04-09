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

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.hypergraphdb.util.RefResolver;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.SWRLBuiltInAtom;
import org.semanticweb.owlapi.model.SWRLDArgument;
import org.semanticweb.owlapi.model.SWRLLiteralArgument;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.OWL;

public class XSLTransform implements SWRLBuiltinImplementation
{

	@Override
	public Map<SWRLVariable, OWLObject> eval(SWRLBuiltInAtom atom, OWLOntology ontology,
			RefResolver<SWRLVariable, OWLObject> varResolver)
	{
		HashMap<SWRLVariable, OWLObject> M = null; 
		List<SWRLDArgument> arguments = atom.getArguments();
		Map<String,Object> defaultParameters = new HashMap<String,Object>();
		//arguments
		//1. the document to be transformed
		//2. its stylesheet as an iri fragment.
		//3-(n-1)...optional arguments.
		//n. a variable used to store the transformation.
		if (arguments ==  null || arguments.size() < 3)
			return M;
		SWRLDArgument arg0 = arguments.get(0);
		SWRLDArgument arg1 = arguments.get(1);
		SWRLDArgument argN = arguments.get(arguments.size()-1);
		OWLDataFactory factory = ontology.getOWLOntologyManager().getOWLDataFactory();
		OWLLiteral item = resolveToLiteral(arg0, varResolver);
		defaultParameters.put("ontologyIRI", ontology.getOntologyID().getOntologyIRI().toString());
		defaultParameters.put("boIRI", ontology.getOntologyID().getOntologyIRI().resolve("#bo").toString());
		if(item == null)
			return M;
		OWLLiteral stylesheet = OWL
								.dataProperty(
										OWL.individual(resolveToLiteral(arg1,  varResolver).getLiteral()),"hasContents");
		if(stylesheet == null)
			return M;
		SWRLVariable v = null;
		if(argN instanceof SWRLVariable && varResolver.resolve((SWRLVariable)argN) == null)
			v = (SWRLVariable) argN;
		if(v == null)
			return M;
		String s = transform
		(
				new StreamSource(new StringReader(item.getLiteral())),
				stylesheet.getLiteral(),
				varResolver,
				defaultParameters,
				(arguments.size()>3)? arguments.subList(2, arguments.size()-1):null
		);
		if(s == null)
			return M;
		OWLLiteral l = factory
							.getOWLLiteral(s, OWL2Datatype.RDF_XML_LITERAL);
		M = new HashMap<SWRLVariable, OWLObject>();
		M.put((SWRLVariable)v, l);
		return M;
	}
	
	private String transform(Source source, String stylesheet, RefResolver<SWRLVariable, 
			OWLObject> varResolver, Map<String,Object> defaultParameters, List<SWRLDArgument> parameters)
	{
		StringWriter writer = new StringWriter();
		StreamResult literal = new StreamResult(writer);
		TransformerFactory factory = TransformerFactory.newInstance();
		try
		{
			Transformer transformer = factory.newTransformer(new StreamSource(new StringReader(stylesheet)));
			for (Map.Entry<String, Object> entry: defaultParameters.entrySet())
			{
				transformer.setParameter(entry.getKey(),entry.getValue());
			}
			if (parameters != null)
			    for (int i = 0; i < parameters.size(); i++ )
				{
					SWRLDArgument arg = parameters.get(i);
					if(arg instanceof SWRLLiteralArgument)
						transformer.setParameter("p"+(i+1),((SWRLLiteralArgument)arg).getLiteral().getLiteral());
					else if(arg instanceof SWRLVariable && varResolver.resolve((SWRLVariable)arg) != null )
					{
						OWLObject o = varResolver.resolve((SWRLVariable)arg);
						if(o instanceof OWLLiteral)
							transformer.setParameter("p"+(i+1),((OWLLiteral)o).getLiteral());
					}
				}
			transformer.transform(source, literal);
		}
		catch (TransformerConfigurationException e)
		{
			e.printStackTrace();
		}
		catch (TransformerException e)
		{
			e.printStackTrace();
		}
		return writer.getBuffer().toString();
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

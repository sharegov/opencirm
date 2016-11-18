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

import static org.sharegov.cirm.OWL.dataProperty;


import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.hypergraphdb.util.RefResolver;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.SWRLBuiltInAtom;
import org.semanticweb.owlapi.model.SWRLDArgument;
import org.semanticweb.owlapi.model.SWRLLiteralArgument;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.rules.SWRLBuiltinImplementation;
import org.sharegov.cirm.utils.WSClient;
import org.w3c.dom.Document;

public class WebServiceCallTask implements SWRLBuiltinImplementation
{
	@Override
	public Map<SWRLVariable, OWLObject> eval(SWRLBuiltInAtom atom,
											 OWLOntology ontology,
											 RefResolver<SWRLVariable, OWLObject> varResolver)
	{
		
		HashMap<SWRLVariable, OWLObject> M = null; 
		Map<String,Object> defaultParameters = new HashMap<String,Object>();
		List<SWRLDArgument> arguments = atom.getArguments();
		if (arguments ==  null || arguments.isEmpty())
			return M;
		SWRLDArgument arg = arguments.get(0);
		if(arg instanceof SWRLLiteralArgument)
		{
			defaultParameters.put("ontologyIRI", ontology.getOntologyID().getOntologyIRI().toString());
			defaultParameters.put("boIRI", ontology.getOntologyID().getOntologyIRI().resolve("#bo").toString());
			OWLNamedIndividual webService = OWL.individual(((SWRLLiteralArgument)arg).getLiteral().getLiteral());
			if(dataProperty(webService, "hasEndpoint") == null)
				return M;
			String requestStylesheet = OWL.unescape(dataProperty(webService, "hasRequestStylesheet")); 
			System.out.println("webService");
			System.out.println(webService);
			System.out.println("requestStylesheet");
			System.out.println(requestStylesheet);
			OWLLiteral responseStylesheet = dataProperty(webService, "hasResponseStylesheet");
			WSClient client = getClient(webService);
			Document request = newDocument(client.getNamespace());
			Document response = client.invoke(
					transform
					(
							new DOMSource(request),
							requestStylesheet,
							varResolver,
							defaultParameters,
							arguments.subList(1, arguments.size() - 1)
					));
			if(responseStylesheet != null)
			{
				response = transform(
							new DOMSource
								( 
								 response
								)
							,
							OWL.unescape(responseStylesheet),
							varResolver,
							defaultParameters
						);
			}
			M = new HashMap<SWRLVariable, OWLObject>();
			//Assign the Document as OWLLiteral to the first null argument variable.
			for(SWRLDArgument var : arguments)
			{
				if(var instanceof SWRLVariable && varResolver.resolve((SWRLVariable)var) == null)
				{
					StringWriter writer = new StringWriter();
					StreamResult literal = new StreamResult(writer);
					TransformerFactory factory = TransformerFactory.newInstance();
					try
					{
						Transformer transformer = factory.newTransformer();
						transformer.transform(new DOMSource(response), literal);
						OWLLiteral l = OWL.dataFactory()
											.getOWLLiteral(writer.getBuffer().toString(), OWL2Datatype.RDF_XML_LITERAL);
						M.put((SWRLVariable)var, l);
					}
					catch (TransformerConfigurationException e)
					{
						e.printStackTrace();
					}
					catch (TransformerException e)
					{
						e.printStackTrace();
					}
				}
			}
		}
		return M;
	}
	
	private WSClient getClient(OWLNamedIndividual webService)
	{
		
		return
		new WSClient(
		 dataProperty(webService, "hasWsdlUrl").getLiteral(),
		 dataProperty(webService, "hasPortName").getLiteral(),
		 dataProperty(webService, "hasNamespace").getLiteral(),
		 dataProperty(webService, "hasServiceName").getLiteral(),
		 dataProperty(webService, "hasEndpoint").getLiteral(),
		 dataProperty(webService, "hasSOAPAction").getLiteral(),
		 dataProperty(webService, "hasUsername").getLiteral(),
		 dataProperty(webService, "hasPassword").getLiteral());
	}
	
	private Document transform(Source source, String stylesheet, RefResolver<SWRLVariable, 
			OWLObject> varResolver, Map<String,Object> defaultParameters, List<SWRLDArgument> parameters)
	{
		DOMResult result = new DOMResult();
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
			transformer.transform(source, result);
		}
		catch (TransformerConfigurationException e)
		{
			e.printStackTrace();
		}
		catch (TransformerException e)
		{
			e.printStackTrace();
		}
		return (Document)result.getNode();
	}
	
	private Document transform(Source source, String stylesheet,
			RefResolver<SWRLVariable, OWLObject> varResolver, Map<String,Object> parameters)
	{
		return transform(source, stylesheet, varResolver, parameters, null);
	}
	
	private Document newDocument(String namespace)
	{
		Document doc = null;
		try
		{
			DocumentBuilderFactory domFactory = DocumentBuilderFactory
			.newInstance();
			domFactory.setNamespaceAware(true);
			DocumentBuilder builder = domFactory.newDocumentBuilder();
			doc = builder.newDocument();
			doc.setDocumentURI(namespace);
		}
		catch (ParserConfigurationException e)
		{
			e.printStackTrace();
		}
		return doc;
	}
}

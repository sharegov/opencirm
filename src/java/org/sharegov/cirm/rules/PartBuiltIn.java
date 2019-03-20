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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.hypergraphdb.util.RefResolver;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataOneOf;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.SWRLBuiltInAtom;
import org.semanticweb.owlapi.model.SWRLDArgument;
import org.semanticweb.owlapi.model.SWRLLiteralArgument;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Gets a object/literal part of a larger object/literal.
 *  
 * @author SABBAS
 *
 */
public class PartBuiltIn implements SWRLBuiltinImplementation
{

	@Override
	public Map<SWRLVariable, OWLObject> eval(SWRLBuiltInAtom atom, OWLOntology ontology,
			RefResolver<SWRLVariable, OWLObject> varResolver)
	{
		Map<SWRLVariable, OWLObject> M = null;
		List<SWRLDArgument> arguments = atom.getArguments();
		//arguments
		//1. the item to be evaluated.
		//2. an expression used to evaluate
		//3. the datatype of the part  
		//4. optional boolean literal to indicate whether result should be treated as a data range.
		//5-(n-1)...optional arguments.
		//n. a variable used to store the part
		if (arguments ==  null || arguments.size() < 4)
			return M;
		SWRLDArgument arg0 = arguments.get(0);
		SWRLDArgument arg1 = arguments.get(1);
		SWRLDArgument arg2 = arguments.get(2);
		SWRLDArgument arg3 = arguments.get(3);
		SWRLDArgument argN = arguments.get(arguments.size()-1);
		OWLDataFactory factory = ontology.getOWLOntologyManager().getOWLDataFactory();
		OWLLiteral item = resolveToLiteral(arg0, varResolver);
		Boolean asRange = false;
		if(item == null)
			return M;
		OWLLiteral expression = resolveToLiteral(arg1, varResolver);
		OWLDatatype type = factory.getOWLDatatype(resolveToLiteral(arg2, varResolver).getLiteral(), new DefaultPrefixManager());
		SWRLVariable v = null;
		if(argN instanceof SWRLVariable && varResolver.resolve((SWRLVariable)argN) == null)
			v = (SWRLVariable) argN;
		if(v == null)
			return M;
		if(arg3 != null)
		{
			OWLLiteral  l = resolveToLiteral(arg3, varResolver); 
			if(l.isBoolean())
				asRange = l.parseBoolean();
		}
		
		M = new HashMap<SWRLVariable, OWLObject>();
		switch (item.getDatatype().getBuiltInDatatype())
		{
			case RDF_XML_LITERAL:
				try
				{
					OWLObject o = xmlPart(item.getLiteral(), expression.getLiteral(), type, asRange, factory, (arguments.size()>5)?arguments.subList(4, arguments.size()-1):null, varResolver);
					M.put(v, o);
				}
				catch (XPathExpressionException e)
				{
					e.printStackTrace();
				}
				break;
			default:
		}
		return M;
	}
	
	private OWLObject xmlPart(String doc, String xpath, OWLDatatype type, boolean asRange, OWLDataFactory factory, List<SWRLDArgument> arguments, RefResolver<SWRLVariable, OWLObject> varResolver) throws XPathExpressionException 
	{
		OWLObject result = null;
		SWRLDArgument arg0 = arguments.get(0);
		OWLLiteral namespace = null;
		if(arg0 != null)
			namespace = resolveToLiteral(arg0, varResolver);
		XPathFactory xPathFactory = XPathFactory.newInstance();
		XPath xPath = xPathFactory.newXPath();
		if (namespace != null)
			xPath.setNamespaceContext(defaultContext(namespace.getLiteral()));
		try
		{
		Object o = xPath.evaluate(xpath, new InputSource(new StringReader(doc)),
					(asRange)?XPathConstants.NODESET:XPathConstants.STRING);
		if(o instanceof String)
			result = factory.getOWLLiteral((String)o, type);
		if(o instanceof NodeList)
		{	
			OWLDataOneOf oneOf = factory.getOWLDataOneOf(factory.getOWLLiteral(""));
			for( int i =0; i < ((NodeList)o).getLength(); i++ )
			{
				Node n = ((NodeList)o).item(i);
				System.out.println(n.getTextContent());
				result = oneOf;
			}
		}		
		}catch(Exception e)
		{
			result = factory.getOWLLiteral("");
		}
		return result;
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
	
	private NamespaceContext defaultContext(final String namespace)
	{
		return
		new NamespaceContext(){
			@Override
			public String getNamespaceURI(String prefix){return namespace;}
			@Override
			public String getPrefix(String namespaceURI){return "";}
			@Override
			public Iterator<String> getPrefixes(String namespaceURI){return Arrays.asList(new String[]{""}).iterator();}
		};
	}
	
	

}

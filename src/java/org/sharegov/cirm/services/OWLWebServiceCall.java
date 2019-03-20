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
package org.sharegov.cirm.services;

import static org.sharegov.cirm.OWL.dataProperty;
import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.objectProperties;
import static org.sharegov.cirm.OWL.objectProperty;
import static org.sharegov.cirm.OWL.ontology;
import static org.sharegov.cirm.OWL.owlClass;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.Set;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.soap.SOAPBinding;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathFactory;

import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.sharegov.cirm.OWL;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * 
 * Class to call a web service based on ontology configuration.
 * 
 * @author SABBAS
 * 
 */
public class OWLWebServiceCall
{

	private OWLNamedIndividual service;
	private OWLLiteral wsdl;
	private OWLLiteral portName;
	private OWLLiteral namespace;
	private OWLLiteral serviceName;
	private OWLLiteral endpoint;
	private OWLLiteral soapAction;
	private OWLLiteral username;
	private OWLLiteral password;
	private ArgumentMap argumentMap;

	public OWLWebServiceCall(OWLNamedIndividual service)
	{
		this.service = service;
		this.wsdl = dataProperty(service, "Wsdl");
		this.portName = dataProperty(service, "PortName");
		this.namespace = dataProperty(service, "Namespace");
		this.serviceName = dataProperty(service, "ServiceName");
		this.endpoint = dataProperty(service, "Endpoint");
		this.soapAction = dataProperty(service, "SOAPAction");
		this.username = dataProperty(service, "hasUsername");
		this.password = dataProperty(service, "hasPassword");
		
		this.argumentMap = new ArgumentMap(getArgumentMapping());

	}

	public OWLLiteral[] execute(OWLLiteral[] values) throws Exception
	{
		Set<OWLNamedIndividual> inputs = objectProperties(service, "hasInput");
		Set<OWLNamedIndividual> outputs = objectProperties(service, "hasOutput");
		argumentMap.setValuesArray(values);
		// if (values.length != argumentMap.size())
		// throw new Exception(
		// "Pass a values array equal to the size of the configured arguments. See ontology argument mapping.");
		invoke(wsdl.getLiteral(), namespace.getLiteral(), serviceName
				.getLiteral(), portName.getLiteral(), endpoint.getLiteral(),
				soapAction.getLiteral(), inputs, outputs, argumentMap);
		return argumentMap.getValuesArray();
	}

	private void invoke(String wsdlLocation, String namespace,
			String serviceName, String portName, String endpoint,
			String soapAction, Set<OWLNamedIndividual> inputs,
			Set<OWLNamedIndividual> outputs, ArgumentMap argMap) throws Exception
	{
		QName svc = new QName(namespace, serviceName);
		QName port = new QName(namespace, portName);
		Service service = Service.create(new URL(wsdlLocation), svc);
		Dispatch<SOAPMessage> dispatch = service.createDispatch(port,
				SOAPMessage.class, Service.Mode.MESSAGE);
		BindingProvider bp = (BindingProvider) dispatch;
		if (endpoint != null)
			bp.getRequestContext().put(
					BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpoint);
		if(soapAction != null)
		{
			bp.getRequestContext().put(BindingProvider.SOAPACTION_USE_PROPERTY, Boolean.TRUE);
			bp.getRequestContext().put(BindingProvider.SOAPACTION_URI_PROPERTY, soapAction);
		}
		if(username != null && password !=null)
		{
			bp.getRequestContext().put(BindingProvider.USERNAME_PROPERTY, username.getLiteral());
			bp.getRequestContext().put(BindingProvider.PASSWORD_PROPERTY, password.getLiteral());
		}
		MessageFactory factory = ((SOAPBinding) bp.getBinding())
				.getMessageFactory();
		SOAPMessage request = factory.createMessage();
		SOAPHeader header = request.getSOAPHeader();
		SOAPBody body = request.getSOAPBody();
		Document req = createRequest(namespace);
		addInputs(req, inputs, argMap);
		body.addDocument(req);
		write(req);
		SOAPMessage reply = null;
		try
		{
			reply = dispatch.invoke(request);
		}
		catch (WebServiceException e)
		{
			throw e;
		}
		try
		{
			body = reply.getSOAPBody();
			Document resp = body.extractContentAsDocument();
			// write(resp);
			getOutputs(resp, outputs, argMap);
		}
		catch (SOAPException e)
		{
			throw e;
		}
	}

	private void write(Document resp)
	{
		TransformerFactory tFactory = TransformerFactory.newInstance();
		try
		{
			Transformer transformer = tFactory.newTransformer();
			DOMSource source = new DOMSource(resp);
			StreamResult result = new StreamResult(System.out);
			transformer.transform(source, result);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private Document createRequest(String namespace)
			throws ParserConfigurationException, IOException, SAXException
	{
		DocumentBuilderFactory domFactory = DocumentBuilderFactory
				.newInstance();
		domFactory.setNamespaceAware(true);
		DocumentBuilder builder = domFactory.newDocumentBuilder();
		Document doc = builder.newDocument();
		doc.setDocumentURI(namespace);
		return doc;
	}

	private void addInputs(Document req, Set<OWLNamedIndividual> inputs,
			ArgumentMap argMap)
	{
		for (OWLNamedIndividual input : inputs)
		{
			putInputNode(req, dataProperty(input, "XPathExpression")
					.getLiteral(), argMap.get(input).getLiteral());
		}
	}

	private void getOutputs(Document req, Set<OWLNamedIndividual> outputs,
			ArgumentMap argMap)
	{
		for (OWLNamedIndividual output : outputs)
		{
			try
			{
				Node node = getOutputNode(req, dataProperty(output,
						"XPathExpression").getLiteral());
				String value = "";
				if (node.getNodeType() == Node.ATTRIBUTE_NODE)
					value = node.getNodeValue();
				else if (node.getNodeType() == Node.ENTITY_NODE)
					value = node.getTextContent();
				else
					value = node.getTextContent();
				argMap.put(output, value);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	private Document addElement(Document doc, String xpath, String text)
	{
		Node parentNode = doc.getFirstChild();
		if (doc != null && xpath != null && xpath.length() > 0)
		{
			String[] tokens = xpath.split("/");
			String exp = "";
			for (int i = 0; i < tokens.length; i++)
			{
				if (tokens[i] == null || tokens[i].isEmpty())
					continue;
				exp += "/" + tokens[i];
				Node childNode = null;
				try
				{
					childNode = findNode(doc, exp);
				}
				catch (XPathException xpe)
				{
				}
				if (childNode == null)
					childNode = doc.createElementNS(doc.getDocumentURI(),
							tokens[i]);
				if (parentNode == null)
				{
					doc.appendChild(childNode);
				}
				else
				{
					parentNode.appendChild(childNode);
				}
				parentNode = childNode;
			}
		}
		parentNode.setTextContent(text);
		return doc;
	}

	private Node findNode(final Document doc, String xpath) throws XPathException
	{
		NamespaceContext defaultDocContext = new NamespaceContext(){
			@Override
			public String getNamespaceURI(String prefix)
			{
				String namespace = doc.getNamespaceURI();
				if (namespace == null)
					namespace = doc.getFirstChild().getNamespaceURI();
				return namespace;
			}
			@Override
			public String getPrefix(String namespaceURI){return "";}
			@Override
			public Iterator getPrefixes(String namespaceURI){return null;}
		};
		XPathFactory xPathFactory = XPathFactory.newInstance();
		XPath xPath = xPathFactory.newXPath();
		xPath.setNamespaceContext(defaultDocContext);
		return (Node) xPath.evaluate(xpath, doc, XPathConstants.NODE);
	}

	private void putInputNode(Document request, String xpath, String value)
	{
		addElement(request, xpath, value);
	}

	private Node getOutputNode(Document response, String xpath)
			throws XPathException
	{
		return findNode(response, xpath);
	}

	private Set<OWLNamedIndividual> getArgumentMapping()
	{
		OWLDataFactory df = OWL.dataFactory();
		OWLClassExpression paramsQuery = df.getOWLObjectIntersectionOf(
				owlClass("WebArgumentMapping"), df.getOWLObjectHasValue(
						objectProperty("forWebService"), service));
		NodeSet<OWLNamedIndividual> S = OWL.reasoner(
				ontology()).getInstances(paramsQuery, false);
		OWLNamedIndividual argumentMapping = (OWLNamedIndividual) S.iterator().next()
				.getEntities().iterator().next();
		Set<OWLNamedIndividual> arguments = objectProperties(argumentMapping,
				"hasArgument");
		return arguments;
	}
	
	/**
	 * Class that maps a set of owl WebArgument(s) to an array of arg values.
	 * 
	 * @author SABBAS
	 * 
	 */
	class ArgumentMap
	{

		private Set<OWLNamedIndividual> argMap;
		private OWLLiteral[] valuesArray;

		public ArgumentMap(Set<OWLNamedIndividual> argMap)
		{
			this.argMap = argMap;
			this.valuesArray = makeValuesArray();
		}

		public void setValuesArray(OWLLiteral[] valuesArray)
		{
			for (int i = 0; i < valuesArray.length; i++)
			{
				try
				{
					this.valuesArray[i] = valuesArray[i];
				}
				catch (ArrayIndexOutOfBoundsException e)
				{
				}
			}
		}

		public Set<OWLNamedIndividual> getArgMap()
		{
			return argMap;
		}

		public OWLLiteral[] getValuesArray()
		{
			return valuesArray;
		}

		public OWLLiteral get(OWLNamedIndividual parameter)
		{
			try
			{
				OWLNamedIndividual argument = mappingForParameter(parameter);
				int index = dataProperty(argument, "ArgumentIndex")
						.parseInteger();
				return valuesArray[index];
			}
			catch (Exception e)
			{
				return null;
			}
		}

		public void put(OWLNamedIndividual parameter, String value)
		{
			try
			{
				OWLNamedIndividual argument = mappingForParameter(parameter);
				int index = dataProperty(argument, "ArgumentIndex")
						.parseInteger();
				valuesArray[index] = OWL.dataFactory().getOWLLiteral(value);
			}
			catch (Exception e)
			{

			}
		}

		public int size()
		{
			return argMap != null ? argMap.size() : 0;
		}

		private OWLNamedIndividual mappingForParameter(OWLNamedIndividual parameter)
		{
			for (OWLNamedIndividual a : argMap)
			{
				if (objectProperty(a, "parameter").equals(parameter))
					return a;
			}
			return null;
		}

		private OWLLiteral[] makeValuesArray()
		{
			OWLLiteral[] result = new OWLLiteral[argMap.size()];
			int i = 0;
			for (OWLNamedIndividual a : argMap)
			{
				OWLLiteral dataType = dataProperty(objectProperty(a,
						"parameter").asOWLNamedIndividual(), "DataType");
				if ("xsd:string".equalsIgnoreCase(dataType.getLiteral()))
				{
					result[i] = OWL.dataFactory().getOWLLiteral("");
				}
				i++;
			}
			return result;
		}

	}

	public static void main(String[] args) throws Exception
	{
		OWLWebServiceCall call = new OWLWebServiceCall(
				individual("GarbageTruckRouteCheckService"));
		DocumentBuilderFactory domFactory = DocumentBuilderFactory
				.newInstance();
		domFactory.setNamespaceAware(true);
		DocumentBuilder builder = domFactory.newDocumentBuilder();
		//Document doc = builder.parse(new ByteArrayInputStream(
		//		"<hasPassed xmlns=\"sw\">no</hasPassed>".getBytes()));
		//System.out
		//		.println(call.findNode(doc, "/sw:hasPassed").getTextContent());
		Document doc = builder.parse(new ByteArrayInputStream(
				"<XYAddressResponse xmlns=\"http://intra.miamidade.gov/GISAddress\"><XYAddressResult><Count>1</Count><XY><ArrXY><X>911075.00007005</X><Y>514594.87501394</Y><Zip_Code>33145</Zip_Code><Munic_Code>1</Munic_Code></ArrXY></XY></XYAddressResult></XYAddressResponse>".getBytes()));
		//doc.s
		Node node = call.findNode(doc, "/:XYAddressResponse/:XYAddressResult/:XY/:ArrXY[1]/:X");
		System.out
				.println(node.getTextContent());
	

	}

}

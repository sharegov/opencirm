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
package org.sharegov.cirm.utils;


import java.net.MalformedURLException;

import java.net.URL;
import java.util.Collections;

import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.soap.SOAPBinding;

import org.w3c.dom.Document;




/**
 * 
 * A simple client to invoke soap style web services.
 * 
 * @author SABBAS
 *
 */
public class WSClient
{
	
	private String wsdlUrl;
	private String portName;
	private String namespace;
	private String serviceName;
	private String endpoint;
	private String soapAction;
	private String username;
	private String password;
	
	public WSClient()
	{
		
	}
	
	
	public WSClient(String wsdlUrl, String portName, String namespace, String serviceName, String endpoint,
			String soapAction, String username, String password)
	{
		super();
		this.wsdlUrl = wsdlUrl;
		this.portName = portName;
		this.namespace = namespace;
		this.serviceName = serviceName;
		this.endpoint = endpoint;
		this.soapAction = soapAction;
		this.username = username;
		this.password = password;
	}

	public Document invoke(Document request)
	{
		Document response = null;
		QName svc = new QName(namespace, serviceName);
		QName port = new QName(namespace, portName);
		try
		{
			Service service = Service.create(wsdlUrl.startsWith("/") ? GenUtils.makeLocalURL(wsdlUrl) : new URL(wsdlUrl), svc);
			Dispatch<SOAPMessage> dispatch = service.createDispatch(port, SOAPMessage.class, Service.Mode.MESSAGE);
			BindingProvider bp = (BindingProvider) dispatch;
			if (endpoint != null)
				bp.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpoint);
			if(soapAction != null)
			{
				bp.getRequestContext().put(BindingProvider.SOAPACTION_USE_PROPERTY, Boolean.TRUE);
				bp.getRequestContext().put(BindingProvider.SOAPACTION_URI_PROPERTY, soapAction);
			}
			if(username != null && password !=null)
			{
				bp.getRequestContext().put(BindingProvider.USERNAME_PROPERTY, username);
				bp.getRequestContext().put(BindingProvider.PASSWORD_PROPERTY, password);
			}
			MessageFactory factory = ((SOAPBinding) bp.getBinding()).getMessageFactory();
			//write(request);
			SOAPMessage reply = null;
			SOAPMessage message = factory.createMessage();
			//SOAPHeader header = message.getSOAPHeader();
			SOAPBody body = message.getSOAPBody();
			body.addDocument(request);
			reply = dispatch.invoke(message);
			body = reply.getSOAPBody();
			response =  body.extractContentAsDocument();
			//write(response);
		}
		catch (MalformedURLException e)
		{
			e.printStackTrace();
		}
		catch (WebServiceException e)
		{
			e.printStackTrace();
		}
		catch (SOAPException e)
		{
			e.printStackTrace();
		}
		return response;
	}

	private void write(Document resp)
	{
		TransformerFactory factory = TransformerFactory.newInstance();
		try
		{
			Transformer transformer = factory.newTransformer();
			DOMSource source = new DOMSource(resp);
			StreamResult result = new StreamResult(System.out);
			transformer.transform(source, result);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public String getWsdlUrl()
	{
		return wsdlUrl;
	}


	public void setWsdlUrl(String wsdlUrl)
	{
		this.wsdlUrl = wsdlUrl;
	}


	public String getPortName()
	{
		return portName;
	}


	public void setPortName(String portName)
	{
		this.portName = portName;
	}


	public String getNamespace()
	{
		return namespace;
	}


	public void setNamespace(String namespace)
	{
		this.namespace = namespace;
	}


	public String getServiceName()
	{
		return serviceName;
	}


	public void setServiceName(String serviceName)
	{
		this.serviceName = serviceName;
	}


	public String getEndpoint()
	{
		return endpoint;
	}


	public void setEndpoint(String endpoint)
	{
		this.endpoint = endpoint;
	}


	public String getSoapAction()
	{
		return soapAction;
	}


	public void setSoapAction(String soapAction)
	{
		this.soapAction = soapAction;
	}


	public String getUsername()
	{
		return username;
	}


	public void setUsername(String username)
	{
		this.username = username;
	}


	public String getPassword()
	{
		return password;
	}


	public void setPassword(String password)
	{
		this.password = password;
	}
	
}

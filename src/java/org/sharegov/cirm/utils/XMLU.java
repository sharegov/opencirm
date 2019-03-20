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


import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * <p>
 * Utilities methods for dealing with XML.
 * </p>
 * 
 * @author boris
 *
 */
public class XMLU
{
	/**
	 * 
	 * @param xml
	 * @return
	 */
	public static Document parse(String xml)
	{
		Document doc = null;
		if (xml == null || xml.isEmpty())
			return doc;
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance(); 
	    DocumentBuilder builder;   
	    try  
	    {   
	        builder = factory.newDocumentBuilder();   
	        return builder.parse(new InputSource(new StringReader(xml)));   

	    } 
	    catch (Exception e)   
	    {   
	    	throw new RuntimeException(e);
	    }   		
	}
	
	public static String stringify(Document doc)
	{
		try
		{
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			StringWriter writer = new StringWriter();
			transformer.transform(new DOMSource(doc), new StreamResult(writer));
			return writer.getBuffer().toString().replaceAll("\n|\r", "");		
	    } 
	    catch (Exception e)   
	    {   
	    	throw new RuntimeException(e);
	    }   		
	}

	public static String stringify(Element doc)
	{
		try
		{
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			StringWriter writer = new StringWriter();
			transformer.transform(new DOMSource(doc), new StreamResult(writer));
			return writer.getBuffer().toString().replaceAll("\n|\r", "");		
	    } 
	    catch (Exception e)   
	    {   
	    	throw new RuntimeException(e);
	    }   		
	}
	
	public static String content(Document doc, String tagName, String def)
	{
		return content(doc.getDocumentElement(), tagName, def);
	}
	
	public static String content(Document doc, String tagName)
	{		
		return content(doc, tagName, null);
	}
	
	public static String content(Element el, String tagName, String def)
	{
		NodeList L = el.getElementsByTagName(tagName);
		if (L.getLength() == 0)
			return def;
		else
			return L.item(0).getTextContent();		
	}
	
	public static String content(Element el, String tagName)
	{
		return content(el, tagName, null);
	}
}

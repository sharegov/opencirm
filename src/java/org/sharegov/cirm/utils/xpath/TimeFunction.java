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
package org.sharegov.cirm.utils.xpath;

import java.util.List;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.xpath.XPathFunction;
import javax.xml.xpath.XPathFunctionException;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
/**
 * 
 * @author SABBAS
 *
 */
public class TimeFunction implements XPathFunction
{

	/**
	 * This function attempts to return the lexical time part of a 
	 * xsdDateTime representation. 
	 * @see  http://www.w3.org/TR/2004/PER-xmlschema-2-20040318/#time
	 * 'The lexical representation for time is the left truncated lexical
	 *  representation for dateTime: hh:mm:ss.sss with optional following time zone indicator.'
	 */
	@Override
	public Object evaluate(List args) throws XPathFunctionException
	{
		
		if(args != null && !args.isEmpty())
		{
			Object o = args.get(0);
			String xsdDateTime;
			if( o instanceof NodeList)
			{
				Node node = ((NodeList)o).item(0);
				xsdDateTime = node.getTextContent();
			}
			else if ( o instanceof Node)
			{
				xsdDateTime = ((Node)o).getTextContent();
			}
			else if ( o instanceof String)
			{
				xsdDateTime = (String)o;
			} else
			{
				xsdDateTime = null;
				
			}
			if(xsdDateTime == null)
				throw new XPathFunctionException("Cannot interpret the argument supplied" +  o);
			
			String parsed;
			try
			{
				XMLGregorianCalendar xmlCal = DatatypeFactory.newInstance().newXMLGregorianCalendar(xsdDateTime);
				parsed = xmlCal.toXMLFormat();
			}
			catch (DatatypeConfigurationException e)
			{
				throw new XPathFunctionException(e);
			}
			int tzoneIndicator = parsed.indexOf("T");
			if(tzoneIndicator != -1)
				return parsed.substring(tzoneIndicator + 1);
			else if(parsed.contains(":"))
				return parsed.substring(parsed.indexOf(":")-2);
			else 
				throw new XPathFunctionException("Could not resolve time part of dateTime: " +  xsdDateTime);
		}
		
		
		// TODO Auto-generated method stub
		return null;
	}

}

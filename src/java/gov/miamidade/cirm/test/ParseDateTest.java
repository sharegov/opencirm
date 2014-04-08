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
package gov.miamidade.cirm.test;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

import org.sharegov.cirm.utils.GenUtils;

/**
 * We're using two Dateformats in OWL: Standard XML and a custom, defined in Genutils by means of SimpleDateFormat.
 * 
 * Checks if either date format used can be parse by either parser used.
 * @author Thomas Hilpold
 */
public class ParseDateTest
{

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		String genDateStr = getGenutilDateNow();
		String xmlDateStr = getXMLDateNow();
		tryParseWithBoth(genDateStr);
		tryParseWithBoth(xmlDateStr);
	}
	
	public static void tryParseWithBoth(String s)
	{
		DatatypeFactory xmlDatatypeFactory = null;
		try
		{
			xmlDatatypeFactory = DatatypeFactory.newInstance();
		} catch (DatatypeConfigurationException e)
		{
			e.printStackTrace();
		}
		try {
			Date d2 = xmlDatatypeFactory.newXMLGregorianCalendar(s).toGregorianCalendar().getTime();
			System.out.println("XML successfully parsed: " + s + " into " + d2.toString());
		} catch (Exception e) {
			System.err.println("XML failed to parse Genutils format: " + s + " with " + e.toString());
		}
		//
		try {
			Date d2 = GenUtils.parseDate(s);
			System.out.println("Genutils sucessfully parsed: " + d2.toString());
		} catch (Exception e) {
			System.err.println("Genutils failed to parse XML format: " + s + " whith " + e.toString());
		}
	}

	public static String getGenutilDateNow() 
	{
		Date d = new Date();
		System.out.println("" + d);
		String r1 = GenUtils.formatDate(d);
		System.out.println("Genutils format: " + r1);
		return r1;
		//"2013-04-23T15:54:43.000-0000"
	}

	public static String getXMLDateNow() 
	{
		Date d = new Date();
		System.out.println("" + d);
		DatatypeFactory xmlDatatypeFactory = null;
		try
		{
			xmlDatatypeFactory = DatatypeFactory.newInstance();
		} catch (DatatypeConfigurationException e)
		{
			e.printStackTrace();
		}
		GregorianCalendar cal = (GregorianCalendar) Calendar.getInstance();
		cal.setTime(d);
		String r1 = xmlDatatypeFactory.newXMLGregorianCalendar(cal).toXMLFormat();
		System.out.println("Format XML: " + r1);
		return r1;
	}
}

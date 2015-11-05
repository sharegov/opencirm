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

import static org.sharegov.cirm.OWL.individual;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.StartUp;

import mjson.Json;

public class ServiceRequestReportUtil
{
	public static final String legacyPrefix = "legacy:";
	public static final String emptyField = "----";
	public static final String blankField = "";
	public static final String[] emptyArray = {};
	
	/**
	 * Merges all the old and new Service Answers associated with the provided Service Request
	 * and returns the Service Answers as a List of Json objects. 
	 * @param data : Business Ontology in Json format
	 * @return
	 */
	public static List<Json> getAllServiceAnswers(Json data) {

		List<Json> saList = Collections.emptyList();
		if(data.at("properties").has("hasServiceAnswer"))
		{
			if(!data.at("properties").at("hasServiceAnswer").isArray())
			{
				data.at("properties").set("hasServiceAnswer", 
						Json.array().add(data.at("properties").at("hasServiceAnswer")));
			}
			saList = data.at("properties").at("hasServiceAnswer").asJsonList();
		}
		return replaceWithOldServiceAnswer(fetchOldServiceAnswers(data), saList);
	}

	private static Json fetchOldServiceAnswers(Json data) {
		Json hasOldData = Json.object();
		Json hasOldServiceAnswers = Json.array();
		if(data.at("properties").has("hasOldData"))
		{	String oldDataStr = data.at("properties").at("hasOldData").asString(); //.replace("\\\"", "\"");
			//System.out.println(oldDataStr);
			hasOldData = Json.read(oldDataStr);
			if(hasOldData.has("hasServiceAnswer"))
				if(hasOldData.at("hasServiceAnswer").isArray())
					hasOldServiceAnswers = hasOldData.at("hasServiceAnswer");
				else
					hasOldServiceAnswers.add(hasOldData.at("hasServiceAnswer"));
		}
		return hasOldServiceAnswers;
	}

	private static List<Json> replaceWithOldServiceAnswer(Json hasOldServiceAnswers, List<Json> saList)
	{
		if(hasOldServiceAnswers == null || hasOldServiceAnswers.asJsonList().isEmpty())
			return saList;
		if(saList.isEmpty())
			return hasOldServiceAnswers.asJsonList();
		else {
			List<Json> newSAList = new ArrayList<Json>();
			for(Json hasServiceAnswer : saList)
			{
				boolean hasAnswer = false;
				for(Json oldServiceAnswer : hasOldServiceAnswers.asJsonList())
				{
					if(oldServiceAnswer.at("hasServiceField").at("iri").asString().equals(
							hasServiceAnswer.at("hasServiceField").at("iri").asString()))
					{
						if(hasServiceAnswer.has("hasAnswerValue") && 
								!hasServiceAnswer.at("hasAnswerValue").at("literal").asString().equalsIgnoreCase(
										oldServiceAnswer.at("hasAnswerValue").at("literal").asString()))
						{
							hasServiceAnswer.set("hasAnswerValue", oldServiceAnswer.at("hasAnswerValue"));
						}
						else if(hasServiceAnswer.has("hasAnswerObject"))
						{
							hasServiceAnswer.set("hasAnswerValue", oldServiceAnswer.at("hasAnswerValue"));
							hasServiceAnswer.atDel("hasAnswerObject");
						}
						hasAnswer = true;
						newSAList.add(hasServiceAnswer);
					}
				}
				if(!hasAnswer)
					newSAList.add(hasServiceAnswer);
			}
			for(Json oldServiceAnswer : hasOldServiceAnswers.asJsonList())
			{
				boolean hasAnswer = false;
				for(Json hasServiceAnswer : saList)
				{
					if(oldServiceAnswer.at("hasServiceField").at("iri").asString().equals(
							hasServiceAnswer.at("hasServiceField").at("iri").asString()))
					{
						hasAnswer = true;
					}
				}
				if(!hasAnswer)
					newSAList.add(oldServiceAnswer);
			}
			return newSAList;
		}
	}
	
	public static String getCity(Json address)
	{
		OWLNamedIndividual ind = individual(address, "Street_Address_City");
		if(ind != null)
		{
			OWLLiteral literal = OWL.dataProperty(ind, "Name");
			if(literal != null)
				return literal.getLiteral();
			else
			{
				literal = OWL.dataProperty(ind, "Alias");
				if(literal != null)
					return literal.getLiteral();
				else
					return blankField;
			}
		}
		else
			return blankField;
	}

	/**
	 * Returns the literal value of the data property present in the Ontology.
	 * If no literal value present, then returns an empty String
	 * @param address : the parent address JSON object in which the address property is present
	 * @param addrProp : the address property
	 * @param dataProp : the dataProperty name whose value has to be retrieved from the Ontology
	 * @return
	 */
	public static String getAddressPropertyValue(Json address, String addrProp, String dataProp)
	{
		OWLNamedIndividual ind= individual(address, addrProp);
		if(ind != null)
		{
			OWLLiteral literal = OWL.dataProperty(ind, dataProp);
			if(literal != null)
				return literal.getLiteral();
			else
				return blankField;
		}
		else
			return blankField;
	}
	
	/**
	 * Takes a Json object and checks if it has a property named as the second parameter type
	 * if true : if type property is singular converts to an array and returns a List<Json>
	 * if false : returns an empty list
	 * @param object
	 * @param type
	 * @return
	 */
	public static List<Json> toList(Json object, String type) {
		List<Json> result = Collections.emptyList();
		if(object.has(type))
		{
			if(!object.at(type).isArray())
				object.set(type, Json.array(object.at(type)));
			result = object.at(type).asJsonList();
		}
		return result;
	}
	
	/**
	 * Converts the given list of maps into a comma separated single string.
	 * Each inner maps key corresponds to the position in the resultant string and 
	 * the Map values are respective values to be placed in the resultant string.
	 * Hence after iterating each complete inner list a line break is added.  
	 * @param valueList
	 * @return
	 */
	public static String toCSV(List<Map<Integer, String>> valueList)
	{
		StringBuilder result = new StringBuilder("");
		try {
			StringBuilder csvRow;
			for(Map<Integer, String> row : valueList)
			{
				csvRow = new StringBuilder("");
				for(int i=0; i<row.size(); i++)
				{
					csvRow.append("\"").append(row.get(i).replaceAll(",", "\\,")).append("\"").append(",");
				}
				csvRow.deleteCharAt(csvRow.length() - 1);
				csvRow.append("\r\n");
				result.append(csvRow.toString());
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		return result.toString();
	}
	
	/**
	 * Converts the given list of maps into a ~ separated single string.
	 * Each inner maps key corresponds to the position in the resultant string and 
	 * the Map values are respective values to be placed in the resultant string.
	 * Hence after iterating each complete inner list a line break is added.  
	 * @param valueList
	 * @return
	 */
	public static String toTilde(List<Map<Integer, String>> valueList)
	{
		StringBuilder result = new StringBuilder("");
		try {
			StringBuilder sbRow;
			for(Map<Integer, String> row : valueList)
			{
				sbRow = new StringBuilder("~");
				for(int i=0; i<row.size(); i++)
				{
					String str = row.get(i).replaceAll("\n", "").trim();
					sbRow.append(str).append("~");
				}
				sbRow.append("\r\n");
				result.append(sbRow.toString());
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		return result.toString();
	}

	public static String formatRecyclingDate(String strDate)
	{
		try {
			SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
			if(strDate.trim().isEmpty())
				return blankField;
			else
			{
				Date dt = input.parse(strDate);
				SimpleDateFormat output = new SimpleDateFormat("MM/dd/yy HH:mm");
				if(dt != null)
					return output.format(dt);
				else
					return blankField;
			}
		}
		catch (ParseException e)
		{
			e.printStackTrace();
			return blankField;
		}
	}

	public static String formatDate(String strDate, boolean time, boolean csv)
	{
		SimpleDateFormat sdf = null;
		if(time)
			sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
		else if(strDate.contains("/"))
			sdf = new SimpleDateFormat("MM/dd/yyyy");
		else if(strDate.contains(","))
			sdf = new SimpleDateFormat("MMM dd, yyyy");
		else 
			sdf = new SimpleDateFormat("yyyy-MM-dd");
		try
		{
			if(!strDate.trim().isEmpty())
				return formatDate(sdf.parse(strDate), time, csv);
			else
				return blankField;
		}
		catch (ParseException e)
		{
			e.printStackTrace();
			return blankField;
		}
	}
	
	/**
	 * returns the date in string format.
	 * @param java.util.Date date: If null, then returns an empty string.
	 * @param boolean time: If true, returns date timestamp and only date if false
	 * @return
	 */
	public static String formatDate(Date date, boolean time, boolean csv)
	{
		StringBuilder sb = new StringBuilder("");
		if(time)
		{
			if(csv)
				sb.append("MMM dd yyyy hh:mm a");
			else
				sb.append("MMM dd, yyyy hh:mm a");
		}
		else{
			if(csv)
				sb.append("MMM dd yyyy");
			else
				sb.append("MMM dd, yyyy");
		}
		SimpleDateFormat sdf = new SimpleDateFormat(sb.toString());
		if(date != null)
			return sdf.format(date);
		else 
			return emptyField;
	}
	
	/**
	 * returns the date in String MMddyyyy format. 
	 * if date is null then returns an empty string.
	 * @param date
	 * @return
	 */
	public static String formatDate(Date date)
	{
		SimpleDateFormat sdf = null;
		if(date != null)
		{
			sdf = new SimpleDateFormat("MMddyyyy");
			return sdf.format(date);
		}
		else
			return emptyField;
	}
	
	public static String getLegacyCode(Json json, String prop)
	{
		OWLNamedIndividual iri = individual(json, prop);
		if(iri == null)
			return blankField;
		OWLLiteral hasLegacyCode = OWL.dataProperty(iri, "legacy:hasLegacyCode");
		if(hasLegacyCode != null)
			return hasLegacyCode.getLiteral();
		else
			return blankField;
	}
	public static String convertToTimestamp(String date) {
		try {
			SimpleDateFormat incomingFormat = new SimpleDateFormat("yyyy/MM/dd' 'HH:mm:ss");
			SimpleDateFormat outgoingFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
			return outgoingFormat.format(incomingFormat.parse(date));
		}
		catch(ParseException e)
		{
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
	public static String getYesterday6AM() {
		Calendar today = Calendar.getInstance();
		today.add(Calendar.DATE, -1);
		today.set(Calendar.HOUR, 6);
		today.set(Calendar.AM_PM, Calendar.AM);
		today.set(Calendar.MINUTE, 02);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		SimpleDateFormat outgoingFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
		return outgoingFormat.format(today.getTime());
	}
	
	public static String getYesterday830PM()
	{
		Calendar today = Calendar.getInstance();
		today.add(Calendar.DATE, -1);
		today.set(Calendar.HOUR, 8);
		today.set(Calendar.AM_PM, Calendar.PM);
		today.set(Calendar.MINUTE, 30);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		SimpleDateFormat outgoingFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
		return outgoingFormat.format(today.getTime());
	}
	
	public static void saveTextFileLocally(String content) {
		FileOutputStream fos = null;
		BufferedOutputStream bos = null;
		try {
			String fileName = "RecyclingCarts"+formatDate(new Date())+".txt";
//			File f = new File("C:\\"+fileName);
	        File f = new File(StartUp.config.at("workingDir").asString() + "/RecyclingReports", fileName);
	    	fos = new FileOutputStream(f);
			bos = new BufferedOutputStream(fos);
			bos.write(content.getBytes());
		}
		catch(Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		finally {
			try
			{
				bos.close();
				fos.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
	}
	
	public static Set<Long> getSRIDListFromFile()
    {
		Set<Long> boids = new HashSet<Long>();
    	try {
	    	FileInputStream fis = new FileInputStream("C:\\boidList.txt");
	    	DataInputStream dis = new DataInputStream(fis);
	    	BufferedReader br = new BufferedReader(new InputStreamReader(dis));
	    	String str;
	    	while((str = br.readLine()) != null) {
	    		boids.add(new Long(str));
	    	}
	    	br.close();
    	}
    	catch(Exception e)
    	{
    		e.printStackTrace();
    		throw new RuntimeException(e);
    	}
	    return boids;
    }
}

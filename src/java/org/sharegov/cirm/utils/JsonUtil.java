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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import mjson.Json;
/**
 *  
 * This class is thread safe (non blocking).
 * 
 * @author Thomas Hilpold
 *
 */
public class JsonUtil
{
	public static class RemoveProperty implements Mapping<Json, Json>
	{
		String prop;
		public RemoveProperty(String prop) { this.prop = prop; }
		public Json eval(Json x)
		{
			if (x.isObject())
				x.delAt(prop);
			return x;
		}
	}
	
	// This method looks a bit weird to me: the result of Mapping.eval is ignored
	// so the Mapping shouldn't be probably Json to Json. [Boris]
	public static Json apply(Json j, Mapping<Json, Json> map, Map<Json, Boolean> ignore)
	{
		Boolean b = ignore.get(j);
		if (b != null && b.booleanValue())
			return j;
		map.eval(j);
		ignore.put(j, true);		
		if (j.isArray())
			for (Json x : j.asJsonList())
				apply(x, map, ignore);
		else if (j.isObject())
			for (Json x : j.asJsonMap().values())
				apply(x, map, ignore);
		return j;
	}
	
	public static Json apply(Json j, Mapping<Json, Json> map)
	{
		return apply(j, map, new IdentityHashMap<Json, Boolean>());
	}
	

	/**
	 * Converts any json object structure into a flat sorted String to String map.
	 * The keys of the map are either the property names, index numbers (array) or 
	 * a dotted path of those. (e.g. a.b.2.r.5)
	 * The returned map is safe to manipulate.
	 * 
	 * For primitives a singleton map ("value", o.toString) is returned.
	 * 
	 * @param o a json.
	 * @return
	 */
	public static Map<String, String> flatmap(Json o)
	{
		Map<String,String> m;
		List<String> path = new ArrayList<String>();
		if (o.isPrimitive())
			m = Collections.singletonMap("value", o.getValue().toString());
		else
			m = flattenImpl(o, path);
		return m;
	}

	/**
	 * Reursively traverse non primitive values using a path structure.
	 * 
	 * @param o
	 * @param path
	 * @return
	 */
	private static SortedMap<String, String> flattenImpl(Json o, List<String> path)
	{
		SortedMap<String, String> result = new TreeMap<String, String>();
		String curPathPrefix = getPathPrefix(path);
		if (o.isObject()) 
		{
			Map<String, Json> ojmap = o.asJsonMap();
			for (Map.Entry<String, Json> e : ojmap.entrySet()) 
			{
				if (e.getValue().isPrimitive()) 
					if (e.getValue().isBoolean())
						result.put(curPathPrefix + e.getKey(), "" + e.getValue().asBoolean());
					else
						result.put(curPathPrefix + e.getKey(), e.getValue().asString());
						
				else 
				{
					path.add(e.getKey());
					// Recurse
					Map<String, String> valueMap = flattenImpl(e.getValue(), path);
					path.remove(path.size() -1);
					result.putAll(valueMap);
				}
			}
		} 
		else if (o.isArray())
		{
			List<Json> l = o.asJsonList();
			int i = 0;
			for (Json arrValue : l) 
			{
				if (arrValue.isPrimitive()) 
					result.put(curPathPrefix + i, arrValue.asString());
				else 
				{
					path.add("" + i);
					// Recurse
					Map<String, String> valueMap = flattenImpl(arrValue, path);
					path.remove(path.size() -1);
					result.putAll(valueMap);
				}
				i++;
			}
		} else
			throw new IllegalStateException("Json at path " 
						+ getPathPrefix(path) 
						+ " is neither array nor object" + o.toString());
		return result;
	}

	/**
	 * Generates a dotted path with a trailing dot.
	 * e.g. "a.b.c.d"
	 * @param path
	 * @return
	 */
	private static String getPathPrefix(List<String> path) 
	{
		String result = "";
		for (String s : path) 
		{
			result += (s + ".");
		}		
		return result;
	}
	
	/**
	 * Formats a json into an indented multi line string.
	 * The Json must not contain string literals containing any of { '{', '}', ',', ':' };
	 * TODO Improve String literal handling ("..inliteral..").
	 * @param j
	 * @return
	 */
	public static String formatFlattened(Json j)
	{
		StringBuffer result = new StringBuffer(1000);
		Map<String,String> flatMap = flatmap(j);
		for (Entry<String, String> flatE : flatMap.entrySet()) 
			result.append(flatE.getKey() + " : " + flatE.getValue() + "\r\n");
		return result.toString();
	}

	/**
	 * Formats a json into an indented multi line string.
	 * The Json must not contain string literals containing the quote char '"'; 
	 * TODO Improve String literal handling ("..inliteral..").
	 * @param j
	 * @return
	 */
	public static String format(Json j)
	{
		char[] in = j.toString().toCharArray();
		StringBuffer out = new StringBuffer(in.length * 2);
		int d = 0;
		boolean inliteral = false;
		for (int i = 0; i < in.length; i++) 
		{
			if (in[i] == '"' || inliteral)
			{
				out.append(in[i]);
				if(in[i] == '"')
					inliteral = !inliteral;
			}
			else if (in[i] == '{')
			{
				out.append(in[i]);
				out.append("\r\n");
				d ++;
				for (int curD = d; curD > 0; curD--) 
					out.append("    ");
			}
			else if (in[i] == '}')
			{
				d --;
				out.append("\r\n");
				for (int curD = d; curD > 0; curD--) 
					out.append("    ");
				out.append(in[i]);
			}
			else if (in[i] == ',')
			{
				out.append(in[i]);
				out.append("\r\n");
				for (int curD = d; curD > 0; curD--) 
					out.append("    ");
			}
			else if (in[i] == ':')
			{
				out.append(" ");
				out.append(in[i]);
				out.append(" ");
			}
			else
				out.append(in[i]);
		}
		return out.toString();
	}
	/**
	 * Always returns a Json.ArrayJson instance
	 * @param j
	 * @return
	 */
	public static Json ensureArray(Json j) 
	{
		if (j.isArray()) return j;
		else
			return Json.array(j);
	}
	
	/**
	 * <p>
	 * Presents a set of JSON objects as a CSV table. The objects must
	 * all have the same form. The properties to use as well as the 
	 * order of appearance is specified in the columns parameter. 
	 * </p>
	 * @param arrayOfObjects A Json array (possible empty) containing
	 * JSON objects.
	 * @param properties A comma separate list of properties that make up
	 * the columns in the order specified.
	 * @param headers If not null, the table will contain a header row
	 * with the titles specified as a comma separated list in this argument.
	 * @return The generated CSV. 
	 */
	public static String csvTable(Json arrayOfObjects, String properties, String headers)
	{
		StringBuilder sb = new StringBuilder();
		if (headers != null)
		{
			String [] titles = headers.split(",");
			for (int i = 0; i < titles.length - 1; i++)
				sb.append(titles[i] + ",");
			sb.append(titles[titles.length-1] + "\n");
		}
		String [] at = properties.split(",");
		for (Json row : arrayOfObjects.asJsonList())
		{
			for (int i = 0; i < at.length - 1; i++)
			{
				Json x = row.at(at[i]);
				sb.append(x.isNull() ? "null" : x.getValue().toString() + ",");
			}
			sb.append(row.at(at[at.length-1]) + "\n");			
		}
		return sb.toString();
	}
	
	public static Json setIfMissing(Json object, String name, Object value)
	{
		if (!object.has(name) && value != null)
			object.set(name, value);
		return object;
	}
	
	public static Json readFromFile(String path) throws IOException {
	    BufferedReader reader = new BufferedReader(new FileReader(path));
	    String         line = null;
	    StringBuilder  stringBuilder = new StringBuilder();
	    String         ls = System.getProperty("line.separator");

	    try {
	        while((line = reader.readLine()) != null) {
	            stringBuilder.append(line);
	            stringBuilder.append(ls);
	        }

	        String jsonStr = stringBuilder.toString();
	        
	       return Json.read(jsonStr);
	       
	    } finally {
	        reader.close();
	    }
	}
	
	public static boolean writeToFile(Json obj, String path)  throws IOException {	
		BufferedWriter writer = null;
		
		writer = new BufferedWriter(new FileWriter(path));
		writer.write(obj.toString());
		
		writer.close( );
		
		return true;
	}
}

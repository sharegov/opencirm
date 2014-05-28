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
package gov.miamidade.cirm.search;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mjson.Json;

import org.sharegov.cirm.StartUp;

public class GisServiceMapping
{
	static GisServiceMapping instance = null;
	
	HashMap<String, GisServiceRule> srtype2rule = new HashMap<String, GisServiceRule>();
	
	private void load(File f)
	{
		final Pattern pattern = Pattern.compile("\"([^\"]*)\"|(?<=,|^)([^,]*)(?=,|$)");
		BufferedReader reader = null;
		try 
		{
			reader = new BufferedReader(new FileReader(f));
			reader.readLine(); // skip title line
			for (String line = reader.readLine(); line != null; line = reader.readLine())
			{
				ArrayList<String> cols = new ArrayList<String>();
				Matcher m =	pattern.matcher(line);
				while (m.find())
					cols.add(line.substring(m.start(), m.end()));
				if (cols.size() < 9)
					continue;
				if (cols.get(6).length() == 0)
					continue;
				if (cols.get(5).length() == 0) //6-26-2013 sabbas - If REQD column is empty/null, no rule.
					continue;
				if (!cols.get(5).trim().equalsIgnoreCase("Y")) //6-26-2013 sabbas - If REQD is not Y, no rule.
					continue;
					
				srtype2rule.put(cols.get(0), GisServiceRule.make(cols.get(6), cols.get(7), cols.get(8)));
			}
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
		finally
		{
			try { reader.close(); } catch (Exception ex) {}
		}
	}
	
	public Boolean isAvailable(String typeCode, Json gisInfo)
	{
		GisServiceRule rule = srtype2rule.get(typeCode);
		if (rule == null)
			return true;
		Json svc = gisInfo.at(rule.getServiceName(), Json.object());
		if (svc == null)
			throw new RuntimeException("No information for service " +  rule.getServiceName());
		else if (svc.isNull())
			return false;
		else if (!svc.isArray())
		{
			Json value = svc.at(rule.getFieldName());
			return rule.eval(value == null ? null : value.getValue());			
		}
		for (Json data : svc.asJsonList())
		{
			Json value = data.at(rule.getFieldName());
			if (rule.eval(value == null ? null : value.getValue()))
				return true;
		}
		return false;
	}
	
	public static synchronized GisServiceMapping get()
	{
		if (instance == null)
		{
			instance  = new GisServiceMapping();
			instance.load(new File(StartUp.config.at("workingDir").asString() + "/src/resources/mdcirm/srgeoinfo.csv"));
		}
		return instance;
	}
}

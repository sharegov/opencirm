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
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * This class finds all regex pattern matches in a file and outputs them as match;count table.
 * In the match;count table, each match is unique and count is defined as number of occurrences in the file.
 * 
 * @author Thomas Hilpold
 *
 */
public class UniqueRegexpFileSearch
{
	//public static String REGEXP = "\"([0-9]+)\"";
	//"C:\_CiRM\3. Working\T67 Email Lists\missingAutoAssignmentAxioms\csr_COM.log"
	public static String FILE = "C:/work/cirmservices/src/ontology/csr.owl";
	public static String REGEXP = "(CM[0-9]{3,8})";
	public static void main(String[] argv) {
		Pattern pattern;
		File f;
		if (argv.length > 0)
		{
			f = new File(argv[0]);
		} else
			f = new File(FILE);		
		if (!f.canRead()) throw new IllegalStateException("Cannot read: " + f.getAbsolutePath());
		if (argv.length > 1)
		{
			pattern = Pattern.compile(argv[1]);
		} else 
			pattern = Pattern.compile(REGEXP);
		UniqueRegexpFileSearch us = new UniqueRegexpFileSearch();
		System.out.println("UniqueRegexpFileSearch for all unique matches for " + REGEXP + " in file " + f.getAbsolutePath());
		SortedMap<String, Integer> results  = us.find(pattern, f);
		//System.out.println("Total matches in file: " + total);
		System.out.println("Match\tCount");
		for(Map.Entry<String, Integer> result : results.entrySet()) 
		{
			System.out.print(result.getKey() + "\t");
			System.out.println(result.getValue());
		}
	} 
	
	/**
	 * Finds all matches for regexp pattern in file and counts them.
	 * 
	 * @param pattern
	 * @param file
	 * @return a sorted map from match (sorted) to count of match
	 * @Throw RuntimeException if problems during file read
	 * @Throw IllegalStateException if file not canRead
	 * 
	 */
	public SortedMap<String, Integer> find(Pattern pattern, File file)
	{
		if (!file.canRead()) throw new IllegalArgumentException("Cannot read: " + file);
		TreeMap<String, Integer> results  = new TreeMap<String, Integer>();
		int total = 0;
		try
		{
			FileReader fr = new FileReader(file);
			BufferedReader r = new BufferedReader(fr);
			String line = null;
			do 
			{
					line = r.readLine();
				if (line != null) 
				{
					Matcher matcher = pattern.matcher(line);
					while (matcher.find()) 
					{
						String match = matcher.group();
						total ++;
						Integer count = results.get(match);
						if (count == null) 
							count = 1;
						else
							count++;
						results.put(match, count);
					}
				}
			} while (line != null);
			r.close();
			return results;
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}
}

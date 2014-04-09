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

import java.io.PrintStream;
import java.util.*;

import org.semanticweb.owlapi.model.SWRLAtom;
import org.sharegov.cirm.workflows.AppliedRule;


public class TraceUtils
{
	public static void error(Throwable t)
	{
		t.printStackTrace(System.err);
	}
	
	public static void warn(String msg)
	{
		System.err.println("[WARN] - " + msg);
	}
	
	public static void severe(Throwable t)
	{
		t.printStackTrace(System.err);
	}
	
	public static void trace(PrintStream out, AppliedRule rule, boolean indent)
	{
		// Body
		out.print("[");
		if (indent) out.println();
		for (Iterator<SWRLAtom> I = rule.getRule().getBody().iterator(); I.hasNext(); )
		{
			SWRLAtom a = I.next();
			out.print(rule.substituteVars(a));
			if (I.hasNext())
				out.print(indent ? "\n" : ",");
		}
		if (indent) out.println();
		out.print("] => ");

		// Head
		out.print("[");
		if (indent) out.println();
		for (Iterator<SWRLAtom> I = rule.getRule().getHead().iterator(); I.hasNext(); )
		{
			SWRLAtom a = I.next();
			out.print(rule.substituteVars(a));
			if (I.hasNext())
				out.print(indent ? "\n" : ",");
		}
		if (indent) out.println();
		out.println("]");		
	}
	
	@SuppressWarnings("unchecked")
	public static void trace(PrintStream out, Map map, boolean oneEntryPerLine, boolean valuesFirst)
	{
		out.print("Map{");
		for (Iterator<Map.Entry> i = map.entrySet().iterator(); i.hasNext(); )
		{
			Map.Entry<?,?> e = i.next();
			if (valuesFirst)
				out.print(e.getValue() + "<-" + e.getKey());
			else
				out.print(e.getKey() + "->" + e.getValue());
			if (oneEntryPerLine)
				out.println();
			else if (i.hasNext())
				out.print(",");
		}
		out.println("}");
	}
}

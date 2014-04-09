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
package org.sharegov.cirm.workflows;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MyGraph
{
	Map<Object, Set<Object>> incidenceMap = new HashMap<Object, Set<Object>>();
	Map<Object, Set<Object>> targetMap = new HashMap<Object, Set<Object>>();
	
	public Set<Object> getIncident(Object x)
	{
		Set<Object> I = incidenceMap.get(x);
		if (I == null)
		{
			I = new HashSet<Object>();
			incidenceMap.put(x, I);
		}
		return I;
	}
	
	public Set<Object> getTargets(Object x)
	{
		return targetMap.get(x);
	}

	public void addLink(Object x, Object...targets)
	{
		Set<Object> S = new HashSet<Object>();
		for (Object t : targets)
		{
			getIncident(t).add(x);  
			S.add(t);
		}
		targetMap.put(x, S);
	}
}

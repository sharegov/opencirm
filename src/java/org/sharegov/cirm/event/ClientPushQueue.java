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
package org.sharegov.cirm.event;

import java.util.LinkedList;
import org.hypergraphdb.util.Pair;
import mjson.Json;

public class ClientPushQueue
{
	LinkedList<Pair<Json, Long>> thequeue = new LinkedList<Pair<Json, Long>>(); 
	long purgeTimeout = 10*60*60*1000; // 10 hours

	void purge()
	{
		while (!thequeue.isEmpty() && 
				thequeue.getFirst().getSecond() + purgeTimeout > System.currentTimeMillis())
			thequeue.removeFirst();
	}
	
	public synchronized ClientPushQueue enqueue(Json event)
	{
		purge();
		thequeue.add(new Pair<Json, Long>(event, System.currentTimeMillis()));
		return this;
	}	
	
	public Json getAfter(Long timestamp)
	{
		Json A = Json.array();
		for (Pair<Json, Long> p : thequeue)
			if (p.getSecond() > timestamp)
				A.add(Json.object("timestamp", p.getSecond(), "event", p.getFirst()));
		return A;
	}
}

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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.sharegov.cirm.CirmTransaction;

public class CirmTransactionUtil
{

	/**
	 * Gets a map of all thread/transactions whose current retry or current execution takes longer than a specified duration.
	 * Do not expect threads to still exist when iterating over the result map. Transactions also may have completed.
	 * @param seconds
	 * @return
	 */
	public static synchronized Map<Long, CirmTransaction<?>> getExecutingTransactionsLongerThan(int durationSecs)
	{
		Map<Long, CirmTransaction<?>> m =  new HashMap<Long, CirmTransaction<?>>(101);
		for (Map.Entry<Long, CirmTransaction<?>> e : CirmTransaction.getExecutingTransactions().entrySet()) 
		{
			CirmTransaction<?> tr = e.getValue();
			Long threadId = e.getKey();
			if (tr.isExecuting() &&  tr.getCurrentExecutionTimeSecs() > durationSecs) 
			{
				m.put(threadId, tr);
			}			
		}
		return m;
	}

	/**
	 * Interrupts all threads by a given Id, if they are still alive at the time of the interrupt.
	 */
	public static synchronized void interruptExecutingThreads(Set<Long> threadIds)
	{
		Set<Thread> allThreads = new HashSet<Thread>(Thread.getAllStackTraces().keySet());
		for (Thread th : allThreads)
		{
			if (th.isAlive() && threadIds.contains(th.getId())) {
				th.interrupt();
				System.out.println("Interrupted Thread: " + th.getName());
			}
		}		
	}

	/**
	 * Interrupts all threads by a given Id, if they are still alive at the time of the interrupt.
	 */
	public static synchronized void stopExecutingThreads(Set<Long> threadIds)
	{
		Set<Thread> allThreads = new HashSet<Thread>(Thread.getAllStackTraces().keySet());
		for (Thread th : allThreads)
		{
			if (th.isAlive() && threadIds.contains(th.getId())) {
				th.stop();
				System.out.println("Stopped Thread (unsafe): " + th.getName());
			}
		}		
	}

}

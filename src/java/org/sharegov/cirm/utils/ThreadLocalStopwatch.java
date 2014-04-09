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

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Date;

public class ThreadLocalStopwatch
{
	private static ThreadLocal<ThreadLocalStopwatch> stopwatches = new ThreadLocal<ThreadLocalStopwatch>();
	private static ThreadLocal<String> threadNames = new ThreadLocal<String>();
	private static int threadID = 1;
	private static DecimalFormat df = new DecimalFormat("##,#00");

	private long startTime;
	
	private DecimalFormat decF = new DecimalFormat("#,##0.000 sec");
	private DateFormat dateF = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
	
	
	/**
	 * Returns a thread local stopwatch.
	 * @return
	 */
	public static ThreadLocalStopwatch getWatch() 
	{
		ThreadLocalStopwatch watch = stopwatches.get(); 
		if (watch == null) {
			watch = new ThreadLocalStopwatch();
			stopwatches.set(watch);
			System.out.println("New Thread: " + getThreadName() + " Orig: " + Thread.currentThread().getName());
		}
		return watch;
	}
	
	public static String getThreadName() 
	{
		String threadName = threadNames.get();
		if (threadName == null)
		{
			synchronized (ThreadLocalStopwatch.class)
			{
				threadName = "T" + df.format(threadID); 
				threadID++;
			}
			threadNames.set(threadName);
		}
		return threadName;
	}

	public static void dispose() 
	{
		stopwatches.remove();
	}

	public ThreadLocalStopwatch() 
	{
		startTime = 0; 
	}
	
	public void reset() 
	{
		reset(null);
	}

	public void reset(String txt) 
	{
		startTime = System.currentTimeMillis();
		System.out.print(getThreadName() + "-");
		if (txt != null) 
		{
			System.out.println(txt + " " + dateF.format(new Date(startTime)));
		} 
		else 
		{
			System.out.println("WATCH  " + dateF.format(new Date(startTime)));
		}
	}
	
	public void time() {
		time(null);
	}

	public void time(String txt) 
	{
		if (startTime == 0) 
		{
			reset(txt);
		} 
		else 
		{
			long totalTime = System.currentTimeMillis() - startTime;
			System.out.print(getThreadName() + "-");
			if (txt != null) 
			{
				System.out.print(txt + " ");
			}
			System.out.println(decF.format(totalTime / 1000.0));
		}
	}

//	public void time(long startTime) {
//		long totalTime = System.currentTimeMillis() - startTime;
//		System.out.println(decF.format(totalTime / 1000.0));
//	}
}

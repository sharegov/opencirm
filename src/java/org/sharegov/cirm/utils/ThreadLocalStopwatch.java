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
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Date;

/**
 * ThreadLocalStopwatch, a thread local logging tool, printing threadId, time and a given string to System.out or .err.
 * 
 *  Usage:
 *  ThreadLocalStopwatch.start("START myclass.mymethod") OR ThreadLocalStopwatch.startTop("START myclass.mymethod") (if method is never called by another with a start)
 *  ThreadLocalStopwatch.now("myclass.mymethod did this")
 *  ThreadLocalStopwatch.nowError("myclass.mymethod had a problem here")
 *  ThreadLocalStopwatch.stop("END myclass.mymethod") OR ThreadLocalStopwatch.fail("FAILED myclass.mymethod with " + exception) //fail only if method exits. 
 *  
 *  One start should match one (stop|fail) call on every level, so the stop or fail calls should be at all exit points of a method.
 * 
 * @author Thomas Hilpold
 *
 */
public class ThreadLocalStopwatch
{
	private static ThreadLocal<ThreadLocalStopwatch> stopwatches = new ThreadLocal<ThreadLocalStopwatch>();
	private static ThreadLocal<String> threadNames = new ThreadLocal<String>();
	private static int threadID = 1;
	private static DecimalFormat df = new DecimalFormat("##,#00");

	private long startTime;
	private int level; //start calls - stop calls. stop call on 0 disposes watch.
	
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

	/**
	 * Use if a method is known to only(!) be used as toplevel method.
	 * Forces a reset of the watch.
	 * @param txt
	 */
	public static void startTop(String txt)
	{
		ThreadLocalStopwatch w = getWatch();
		w.reset(txt);
		w.increaseDepthCount();
	}

	/**
	 * Prints the time + txt and disposes the current threads watch.
	 * Keeps track of the number of start - (stop|fail) calls (level).
	 * @param txt
	 */
	public static void start(String txt) 
	{
		ThreadLocalStopwatch w = getWatch();		
		w.time(txt);
		w.increaseDepthCount();
	}

	/**
	 * Prints threadId, time and txt to system.out.
	 * Does not change level.
	 * @param txt
	 */
	public static void now(String txt)
	{
		getWatch().time(txt);
	}
	
	/**
	 * Error and method continues.  Prints threadId, time and txt to system.err.
	 * Does not change level.
	 * @param txt
	 */
	public static void error(String txt)
	{
		getWatch().timeErr(txt);
	}

	/**
	 * Stop and method exits. Prints threadId, time and txt to system.out and disposes the current thread's watch, if top level.
	 * Only use this in all methods where one start precedes the call, no error occured and the method exits.
	 * @param txt
	 */
	public static void stop(String txt)
	{
		ThreadLocalStopwatch w = getWatch();		
		w.time(txt);
		if (w.decreaseDepthCount() == 0) dispose();
	}

	/**
	 * Fail and method exits. Prints threadId, time and txt to system.err and disposes the current thread's watch, if top level.
	 * Only use this in all methods where one start precedes the call, an error occurs and the method exits.
	 * @param txt
	 */
	public static void fail(String txt)
	{
		ThreadLocalStopwatch w = getWatch();		
		w.timeErr(txt);
		if (w.decreaseDepthCount() == 0) dispose();
	}

	
	public static void dispose() 
	{
		stopwatches.remove();
	}

	public ThreadLocalStopwatch() 
	{
		startTime = 0; 
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
	
	public void time(String txt) {
		timeInt(txt, false);
	}

	private void timeErr(String txt) {
		timeInt(txt, true);
	}

	private void timeInt(String txt, boolean error) 
	{
		if (startTime == 0) 
		{
			reset(txt);
		} 
		else 
		{
			long totalTime = System.currentTimeMillis() - startTime;
			@SuppressWarnings("resource")
			PrintStream stream = error? System.err : System.out;
			stream.print(getThreadName() + "-");
			if (txt != null) 
			{
				stream.print(txt + " ");
			}
			stream.println(decF.format(totalTime / 1000.0));
		}
	}
	
	private int increaseDepthCount() 
	{
		level++;
		return level;
	}

	private int decreaseDepthCount() 
	{
		if (level > 0) level--;
		else time("Watch problem: more stops than starts.");
		return level;
	}

//	public void time(long startTime) {
//		long totalTime = System.currentTimeMillis() - startTime;
//		System.out.println(decF.format(totalTime / 1000.0));
//	}
}

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
package gov.miamidade.cirm.other;

import java.io.File;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.utils.Ref;
import org.sharegov.cirm.utils.SingletonRef;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import mjson.Json;

/**
 * This is just used to instantiate, start, stop etc. the departmental queue
 * listener. Because the listener is a java.lang.Thread, once stopped, it can't
 * be restarted, a new one needs to be created. Hence the need for a controller
 * the contains and manages the currently active JMS listener, if any.
 * 
 * @author boris
 *
 */
public class DepartmentListenerController
{
	public static final Ref<DepartmentListenerController> ref =
			new SingletonRef<DepartmentListenerController>(new DepartmentListenerController());			
			
	private Json config = Json.object(
			"trace", true,
			"timeout", 30000,
			"logFile", "departmentalJMSListener"
	);
	
	private volatile LegacyJMSListener listener = null;
	private volatile PrintStream out = null;
	
	private synchronized PrintStream getLogStream()
	{
		if (out != null)
			return out;
		File outfile = new File (StartUp.config.at("workingDir").asString() + "/logs");
		outfile.mkdir();
		String timestamp = new SimpleDateFormat("yyMMdd-HHmmss").format(new java.util.Date());
		outfile = new File(outfile, config.at("logFile").asString() + "_" + timestamp + ".log");
		try
		{
			out = new PrintStream(new FileOutputStream(outfile));
			ThreadLocalStopwatch.now("DepartmentListenerController using logfile " + outfile.getAbsolutePath());
		}
		catch (FileNotFoundException e)
		{
			out = System.out;
			e.printStackTrace(out);
		} 
		return out;
	}
	
	public void traceMore()
	{
		config.set("trace", true);
	}

	public void traceLess()
	{
		config.set("trace", false);
	}
	
	public boolean isRunning()
	{
		return listener != null && listener.isAlive();
	}
	 
	public String status()
	{
		return (listener == null) ? "never started" : listener.getStatus();
	}
	
	public Json getConfig()
	{
		return config;
	}
	
	public synchronized void start()
	{
		if (isRunning())
			throw new RuntimeException("Departmental listener already running.");
		listener = new LegacyJMSListener(config, getLogStream());
		listener.start();
	}
		
	public synchronized void stop(long waittime)
	{
		if (!isRunning())
			throw new RuntimeException("Departmental listener is not currently running.");
		listener.quit();
		try 
		{
			if (waittime > 0)
				listener.join(waittime);
		}
		catch (InterruptedException ex)
		{
			throw new RuntimeException("Could not finish waiting for departmental queue listener to stop.");
		}
		if (!isRunning() && out != null)
		{
			out.close();
			out = null;
		}
	}
}
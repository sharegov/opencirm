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

import static mjson.Json.object;

import java.io.FileWriter;
import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import mjson.Json;

import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.routing.Filter;

/**
 * Filter that monitors cpu utilization and memory allocation 
 * of the running JVM process.
 * @author SABBAS
 *
 */
public class ServerMonitorFilter extends Filter {


	public static Boolean ON = false;

	private Json A = Json.array(); 
	
	public ServerMonitorFilter() { }
	public ServerMonitorFilter(Context context)	{ super(context);	}
	
	protected synchronized void afterHandle(Request request, Response response)
    {
		if(ON)
		{
			Json monitorData = (Json)request.getAttributes().get("monitorData");
			Long threadCpuStartTime = monitorData.at("threadCpuStartTime").asLong();
			//Long processCpuStartTime = monitorData.at("processCpuStartTime").asLong();
			Long sysStartTime = monitorData.at("sysStartTime").asLong();
			Long threadCpuEndTime = getThreadCpuTime();
			Long processCpuEndTime = getProcessCpuTime();
			Long sysEndTime = System.nanoTime();
			Long cpuPercentageLoad = 0l; 
			if(sysEndTime > sysStartTime)
				cpuPercentageLoad = ((threadCpuEndTime - threadCpuStartTime) * 100l) /  (sysEndTime - sysStartTime);
			monitorData.set("threadCpuEndTime", threadCpuEndTime);
			monitorData.set("processCpuEndTime", processCpuEndTime);
			monitorData.set("cpuPercentageLoad", cpuPercentageLoad);
			monitorData.set("sysEndTime", sysEndTime);
			monitorData.set("freeMemoryMB", Runtime.getRuntime().freeMemory()/(1024*1024));
			monitorData.set("totalMemoryMB", Runtime.getRuntime().totalMemory()/(1024*1024));
			monitorData.set("call", object("url", request.getOriginalRef().toString(), 
			 		   "responseCode", response.getStatus().getCode()));
			A.add(monitorData);
		}
    }
    
    protected synchronized int beforeHandle(Request request, Response response)
    {
     	if(ON)
    	{
     		if (request.getOriginalRef().toString().contains("/monitor/stop"))
	    	{
				try
	    		{
	    			FileWriter out = new FileWriter("c:/temp/cirmmonitor.json");
	    			out.write(A.toString());
	    			out.close();
	    			A = Json.array();
	    		}
	    		catch (Throwable t)
	    		{
	    			t.printStackTrace(System.err);
	    		}
	    	}else
	    	{
	     		Json monitorData = Json.object()
	     				.set("sysStartTime", System.nanoTime())
	    				.set("threadCpuStartTime", getThreadCpuTime())
	    				.set("processCpuStartTime" ,  getProcessCpuTime())
	    				.set("timestamp", System.currentTimeMillis());
	    		request.getAttributes().put( "monitorData", monitorData );
	    	}
    	}
    	return Filter.CONTINUE;
    } 
    
    private Long getProcessCpuTime()
    {
    	try
    	{
	    	MBeanServer managementServer = ManagementFactory.getPlatformMBeanServer();
		   	ObjectName osname = new ObjectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
		   	return (Long) managementServer.getAttribute(osname, "ProcessCpuTime");
    	}
    	catch (Exception e)
    	{
    		e.printStackTrace(System.err);
    	}
    	return -1l;
    }
    
    private Long getThreadCpuTime()
    {
    	try
    	{
	    	MBeanServer managementServer = ManagementFactory.getPlatformMBeanServer();
		   	ObjectName tname = new ObjectName(ManagementFactory.THREAD_MXBEAN_NAME);
		   	return (Long) managementServer.getAttribute(tname, "CurrentThreadCpuTime");
    	}
    	catch (Exception e)
    	{
    		e.printStackTrace(System.err);
    	}
    	return -1l;
    }
}

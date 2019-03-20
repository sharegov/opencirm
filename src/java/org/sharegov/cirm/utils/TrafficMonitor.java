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

import java.io.FileWriter;

import mjson.Json;
import static mjson.Json.*;

import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.routing.Filter;

public class TrafficMonitor extends Filter
{
	private Json A = Json.array(); 
	
	public TrafficMonitor() { }
	public TrafficMonitor(Context context)	{ super(context);	}
	
    protected synchronized void afterHandle(Request request, Response response)
    {
//    	System.out.println("Request: " + request.getOriginalRef().toString());
//    	System.out.println("Response: " + response.getStatus().getCode());
//    	if (request.getOriginalRef().toString().contains("savetraffic"))
//    	{
//    		try
//    		{
//    			FileWriter out = new FileWriter("c:/temp/cirmtraffic.json");
//    			out.write(A.toString());
//    			out.close();
//    		}
//    		catch (Throwable t)
//    		{
//    			t.printStackTrace(System.err);
//    		}
//    	}
//    	else
//    		A.add(object("url", request.getOriginalRef().toString(), 
//    					 "responseCode", response.getStatus().getCode()));
    }
    
    protected synchronized int beforeHandle(Request request, Response response)
    {
//    	System.out.println("Request: " + request.getOriginalRef().toString());
//    	System.out.println("Response: " + response.getStatus().getCode());
    	if (request.getOriginalRef().toString().contains("savetraffic"))
    	{
    		try
    		{
    			FileWriter out = new FileWriter("c:/temp/cirmtraffic.json");
    			out.write(A.toString());
    			out.close();
    		}
    		catch (Throwable t)
    		{
    			t.printStackTrace(System.err);
    		}
    	}
    	else if (!request.getOriginalRef().toString().contains("favicon.ico"))
    	{
    		Json call = object("url", request.getOriginalRef().toString(), 
					 		   "responseCode", response.getStatus().getCode());
    		if (request.getMethod().equals(Method.POST))
    		{
    			if (request.getEntity().getMediaType().equals(MediaType.APPLICATION_JSON))
	    		{
	    	    	String text = request.getEntityAsText();
	    	    	request.setEntity(text, request.getEntity().getMediaType());    			
	    			call.set("post", Json.read(text));
	    			A.add(call);
	    		}
    		}
    		else if (request.getMethod().equals(Method.GET))
    			A.add(call);
    		
    	}
    	return Filter.CONTINUE;
    }    
}

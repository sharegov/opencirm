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

import java.io.IOException;

import java.net.URI;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.Map;

import org.restlet.Client;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Protocol;
import org.restlet.data.Reference;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.ClientResource;

import mjson.Json;
import static mjson.Json.*;

public class RESTClient
{
	private String url;
	private Json queryParams;
	private Object postData;

	@SuppressWarnings("deprecation")
	private URI makeURI()
	{
		try
		{
			StringBuilder queryString = new StringBuilder();
			if (queryParams != null)
			{
				Iterator<Map.Entry<String, Json>> I = queryParams.asJsonMap().entrySet().iterator();
				if (I.hasNext())
					queryString.append("?");
				while (I.hasNext())
				{
					Map.Entry<String, Json> e = I.next();
					queryString.append(e.getKey());
					queryString.append(URLEncoder.encode(e.getValue().getValue().toString()));
					if (I.hasNext())
						queryString.append("&");
				}
			}
			return new URI(url + queryString.toString());
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
	}
	
	@SuppressWarnings("unchecked")
	private <T> T returnResult(Representation rep)
	{
		try
		{
			if (rep.getMediaType() == MediaType.APPLICATION_JSON)
				return (T)Json.read(rep.getText());
			else
				return (T)rep.getText();
		}
		catch (IOException ex)
		{
			throw new RuntimeException(ex);
		}
	}
	
	public String getUrl()
	{
		return url;
	}

	public void setUrl(String url)
	{
		this.url = url;
	}

	public Json getQueryParams()
	{
		return queryParams;
	}

	public void setQueryParams(Json queryParams)
	{
		this.queryParams = queryParams;
	}

	public Object getPostData()
	{
		return postData;
	}

	public void setPostData(Object postData)
	{
		this.postData = postData;
	}

	public <T> T get()
	{
		ClientResource res = new ClientResource(makeURI());
		return (T)returnResult(res.get());
	}
	
	public <T> T post()
	{
		Client client = new Client(Protocol.HTTP);		
		Representation rep = null;
		if (postData != null)
			rep = postData instanceof Json ? 
				new JsonRepresentation((Json)postData) : new StringRepresentation(postData.toString());	
		Response resp = client.handle(new Request(Method.POST, 
												  new Reference(makeURI()),												  
												  rep));
		return (T)returnResult(resp.getEntity());
	}
	
	public <T> T put()
	{
		Client client = new Client(Protocol.HTTP);		
		Representation rep = null;
		if (postData != null)
			rep = postData instanceof Json ? 
				new JsonRepresentation((Json)postData) : new StringRepresentation(postData.toString());	
		Response resp = client.handle(new Request(Method.PUT, 
												  new Reference(makeURI()),												  
												  rep));
		return (T)returnResult(resp.getEntity());
	}
	
	public <T> T del()
	{
		ClientResource res = new ClientResource(makeURI());
		return (T)returnResult(res.delete());
	}
	
	public String update()
	{
		Client client = new Client(Protocol.HTTP);
		Representation rep = null;
		if (rep != null)
			rep = postData instanceof Json ? 
				new JsonRepresentation((Json)postData) : new StringRepresentation(postData.toString());	
		Response resp = client.handle(new Request(Method.PUT, 
												  new Reference(makeURI()),												  
												  rep));
		return (String)returnResult(resp.getEntity());
	}
	
	public static <T> T get(String url)
	{
		RESTClient cl = new RESTClient();
		cl.url = url;
		return (T)cl.get();
	}
	
	public static <T> T post(String url, Object data)
	{
		RESTClient cl = new RESTClient();
		cl.url = url;
		cl.postData = data;
		return (T)cl.post();		
	}
	
	public static <T> T put(String url, Object data)
	{
		RESTClient cl = new RESTClient();
		cl.url = url;
		cl.postData = data;
		return (T)cl.put();		
	}
	
	public static <T> T del(String url)
	{
		RESTClient cl = new RESTClient();
		cl.url = url;
		return (T)cl.del();
	}
	
	public static void main(String [] argv)
	{
		//String url = "http://10.9.25.14:8080/timemachine-0.1/scheduler";
		String url = "http://localhost:9192/timemachine-0.1/task";
		Json data = object();
		data.set("myurl", "http://olsportaldev:8182/op/eventrule")
			.set("time", object()
					.set("day", "13")
					.set("month", "12")
					.set("year", "2011")
					.set("hour", "15")
					.set("minute", "37")
					.set("second", "0")
		);		
		try
		{
			System.out.println(data.toString());
			System.out.println(post(url, data));
		}
		catch (Throwable t)
		{
			t.printStackTrace(System.err);
		}
	}
}

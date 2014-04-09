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
package org.sharegov.cirm;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import mjson.Json;

import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.RESTClient;

public class PlayTraffic
{

	public static class RestCall implements Runnable
	{
		Json traffic;
		public RestCall(Json traffic) { this.traffic = traffic; }
		
		public void run()
		{
//			if (1==1)
//			{
//				System.out.println(RESTClient.get("http://localhost:8182/individuals?q=Street_Type"));
//				return;
//			}
			for (Json call : traffic.asJsonList())
			{
				if (call.at("url").asString().contains("favicon.ico"))
					continue;
				try
				{
					Object result = null;
					if (call.has("post"))
						result = RESTClient.post(call.at("url").asString(),
									call.at("post"));
					else
						result = RESTClient.get(call.at("url").asString());
				}
				catch (Throwable t)
				{
					System.out.println("Failed on " + call.at("url"));
					t.printStackTrace(System.err);
				}
				break;
			}
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		try
		{
			int nthreads = 10;
			Json traffic = Json.read(GenUtils.readTextFile(new File("c:/temp/cirmtraffic.json")));
			while (true)
			{		
				ExecutorService eservice = Executors.newFixedThreadPool(nthreads);
				for (int i = 0; i < nthreads; i++)
					eservice.execute(new RestCall(traffic));
				eservice.shutdown();
				try
				{
					eservice.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
				}
				catch (InterruptedException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				//break;
			} 

		}
		catch (Throwable t)
		{
			t.printStackTrace(System.err);
		}
	}

}

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
package org.sharegov.cirm.rest;

import static org.sharegov.cirm.utils.GenUtils.ok;

import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import mjson.Json;

import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.utils.CirmTransactionUtil;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ServerMonitorFilter;

@Path("monitor")
@Produces("application/json")
public class SystemMonitorService extends RestService
{
	@GET
	@Path("/start")
	public Json start()
	{
		ServerMonitorFilter.ON = true;
		return ok();
	}
	
	@GET
	@Path("/stop")
	public Json stop()
	{
		ServerMonitorFilter.ON = false;
		return ok();
	}

	/**
	 * Gets transaction info on currently executing transactions, as well as total and failed (since server up).
	 * @return
	 */
	@GET
	@Path("/transactionInfo")
	public Json getTransactionInfo()
	{
		Json result = Json.object().set("NrOfTotalTransactions", CirmTransaction.getNrOfTotalTransactions())
				.set("NrOfFailedTransactions", CirmTransaction.getNrOfFailedTransactions())
				.set("NrOfLiveTransactions", CirmTransaction.getNrOfExecutingTransactions());
		return ok().set("transactionInfo", result);
	}
	
	/**
	 * Blocks until live transaction count is zero.
	 * @return
	 */
	@GET
	@Path("/transactionWaitForZero")
	public Json transactionWaitForZero()
	{
		do {
			try
			{
				Thread.sleep(100);
			} catch (InterruptedException e)
			{
			}
		} while (CirmTransaction.getNrOfExecutingTransactions() > 0);
		return ok().set("transactionWaitForZero", "completed");
	}

	
	/**
	 * Gets all transactions whose execute already takes longer than 2 minutesa.
	 * @return ok.[{threadId=a, executionCount=b, executionTimeSecs=c, currentExecutionTimeSecs=d, toString=transaction.toString() e }]
	 */
	@GET
	@Path("/transactionsOver/get/{durationSecs}")
	public Json transactionsOver(@PathParam("durationSecs") Integer durationSecs)
	{
		if (durationSecs == null || durationSecs <= 0) return GenUtils.ko("durationSecs must be > 0");
		Json result = Json.array();
		for (Map.Entry<Long, CirmTransaction<?>> tt : CirmTransactionUtil.getExecutingTransactionsLongerThan(durationSecs).entrySet())
		{
			Json cur = Json.object();
			cur.set("threadId", tt.getKey());
			cur.set("executionCount", tt.getValue().getExecutionCount());
			cur.set("executionTimeSecs", tt.getValue().getExecutionTimeSecs());
			cur.set("currentExecutionTimeSecs", tt.getValue().getCurrentExecutionTimeSecs());
			cur.set("totalExecutionTimeSecs", tt.getValue().getTotalExecutionTimeSecs());
			cur.set("state", tt.getValue().getState().name());
			cur.set("toString", tt.getValue().toString());	
			result.add(cur);
		}
		return ok().set("transactionsOver" + durationSecs.toString(), result);
	}

	/**
	 * Gets all transactions whose execute already takes longer than 2 minutesa.
	 * @return ok.[{threadId=a, executionCount=b, executionTimeSecs=c, currentExecutionTimeSecs=d, toString=transaction.toString() e }]
	 */
	@POST
	@Path("/transactionsOver/interrupt/{durationSecs}")
	public Json transactionsOverInterrupt(@PathParam("durationSecs") Integer durationSecs)
	{
		if (durationSecs == null || durationSecs <= 0) return GenUtils.ko("durationSecs must be > 0");
		CirmTransactionUtil.interruptExecutingThreads(CirmTransactionUtil.getExecutingTransactionsLongerThan(durationSecs).keySet());
		return ok();
	}
	
	/**
	 * Gets all transactions whose execute already takes longer than 2 minutesa.
	 * @return ok.[{threadId=a, executionCount=b, executionTimeSecs=c, currentExecutionTimeSecs=d, toString=transaction.toString() e }]
	 */
	@POST
	@Path("/transactionsOver/stop/{durationSecs}")
	public Json transactionsOverStop(@PathParam("durationSecs") Integer durationSecs)
	{
		if (durationSecs == null || durationSecs <= 0) return GenUtils.ko("durationSecs must be > 0");
		CirmTransactionUtil.stopExecutingThreads(CirmTransactionUtil.getExecutingTransactionsLongerThan(durationSecs).keySet());
		return ok();
	}
}

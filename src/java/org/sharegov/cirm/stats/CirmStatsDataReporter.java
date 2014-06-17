package org.sharegov.cirm.stats;

import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.CirmTransactionEvent;
import org.sharegov.cirm.CirmTransactionListener;
import org.sharegov.cirm.stats.CirmStatistics.StatsValue;

/**
 * CirmDataProvider allows for simplified and minimum blocking data reporting to a CirmStatistics object (e.g. from Refs or MDRefs).
 * Calls inside CirmTransactions are automatically routed in a way, that data only is inserted into stats only on transaction success or failure,
 * thereby ignoring retries. 
 * 
 * THREAD SAFE and fast (but mostly used in one thread only. e.g worker thread).
 *
 * @author Thomas Hilpold
 *
 */
public class CirmStatsDataReporter
{
	private final CirmStatistics stats;
	private final String component;
	
	public CirmStatsDataReporter(CirmStatistics stats, String component)
	{
		if (stats == null) throw new NullPointerException("stats");
		if (component == null) throw new NullPointerException("component");
		this.stats = stats;
		this.component = component;
	}
	
	/**
	 * Reports success to CirmStatistics, even inside of a CirmTransaction.
	 * In a CirmTransaction, the report will be delayed until CirmTransaction fails or succeeds.
	 *
	 * @param action
	 * @param type
	 * @param id
	 */
	public void succeeded(final String action, final String type, final String id)
	{
		final StatsValue val = stats.getEntry(component, action, type);
		if (CirmTransaction.isExecutingOnThisThread())
		{
			//inside transaction, delay delivery to cirmStatistics until all potential retries are over and the 
			//transaction either succeeded or failed.
			CirmTransactionListener componentDataInTransactionProvider = new CirmTransactionListener()
			{				
				@Override
				public void transactionStateChanged(CirmTransactionEvent e)
				{
					if (e.isSucceeded())
					{
						val.addSuccess(id);
					}
					else
					{
						val.addFailure(id, "component reported success,  but transaction failed", "CirmTransaction failure");
					}			
				}
			};
			CirmTransaction.get().addTopLevelEventListener(componentDataInTransactionProvider);
		}
		else
		{
			//no top level transaction
			val.addSuccess(id);
		}
	}
	
	/**
	 * Reports a failure to CirmStatistics, even inside of a CirmTransaction.
	 * In a CirmTransaction, the report will be delayed until CirmTransaction fails or succeeds.
	 * 
	 * @param action
	 * @param type
	 * @param id
	 * @param exception
	 * @param failureMessage
	 */
	public void failed(final String action, final String type, final String id, final String exception, final String failureMessage) 
	{
		final StatsValue val = stats.getEntry(component, action, type);
		if (CirmTransaction.isExecutingOnThisThread())
		{
			//inside transaction, delay delivery to cirmStatistics until all potential retries are over and the 
			//transaction either succeeded or failed.
			CirmTransactionListener componentDataInTransactionProvider = new CirmTransactionListener()
			{				
				@Override
				public void transactionStateChanged(CirmTransactionEvent e)
				{
					if (e.isSucceeded())
					{
						val.addFailure(id, exception, failureMessage);
					}
					else
					{
						val.addFailure(id, exception, "CirmTransaction failed and component reported failure: " + failureMessage);
					}			
				}
			};
			CirmTransaction.get().addTopLevelEventListener(componentDataInTransactionProvider);
		}
		else
		{
			//no top level transaction
			val.addFailure(id, exception, failureMessage);
		}
	
	}
}

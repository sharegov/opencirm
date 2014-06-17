package org.sharegov.cirm.stats;

import mjson.Json;

/**
 * SRCirmStatsDataReporter is a CirmDataProvider that can evaluate a serviceRequest Json for type and caseNumber (id).
 * Calls inside CirmTransactions are automatically routed in a way, that data only is inserted into stats only on transaction success or failure,
 * thereby ignoring retries. 
 * 
 * THREAD SAFE and fast (but mostly used in one thread only. e.g worker thread).
 *
 * @author Thomas Hilpold
 *
 */
public class SRCirmStatsDataReporter extends CirmStatsDataReporter
{	
	public SRCirmStatsDataReporter(CirmStatistics stats, String component)
	{
		super(stats, component);
	}
	
	/**
	 * Reports success to CirmStatistics, even inside of a CirmTransaction.
	 * In a CirmTransaction, the report will be delayed until CirmTransaction fails or succeeds.
	 *
	 * @param action
	 * @param serviceCaseJson with String properties "type" and "properties"."legacy:hasCaseNumber" 
	 */
	public void succeeded(final String action, final Json serviceCaseJson)
	{
		String type = safeDetermineType(serviceCaseJson);
		String id = safeDetermineHasCaseNumber(serviceCaseJson);
		super.succeeded(action, type, id);
	}
	
	/**
	 * Reports a failure to CirmStatistics, even inside of a CirmTransaction.
	 * In a CirmTransaction, the report will be delayed until CirmTransaction fails or succeeds.
	 * 
	 * @param action
	 * @param serviceCaseJson with String properties "type" and "properties"."legacy:hasCaseNumber" 
	 * @param exception
	 * @param failureMessage
	 */
	public void failed(final String action, final Json serviceCaseJson, final String exception, final String failureMessage) 
	{
		String type = safeDetermineType(serviceCaseJson);
		String id = safeDetermineHasCaseNumber(serviceCaseJson);
		super.failed(action, type, id, exception, failureMessage);
	}
	
	/**
	 * Returns the type property or CirmStatistics.UNKNOWN.
	 * @param serviceCaseJson
	 * @throws never an exception.
	 * @return
	 */
	public static String safeDetermineType(final Json serviceCaseJson) 
	{
		try
		{
			return serviceCaseJson.at("type").asString();
		} catch (Exception e)
		{
			System.err.println("SRCirmStatsDataReporter was unable to determine type as String from " + serviceCaseJson);
			return CirmStatistics.UNKNOWN;
		}		
	}
	
	/**
	 * Returns the ("properties").at("legacy:hasCaseNumber") property or CirmStatistics.UNKNOWN.
	 * @param serviceCaseJson
	 * @throws never an exception.
	 * @return
	 */
	public static String safeDetermineHasCaseNumber(final Json serviceCaseJson) 
	{
		try
		{
			return serviceCaseJson.at("properties").at("legacy:hasCaseNumber").asString();
		} catch (Exception e)
		{
			System.err.println("SRCirmStatsDataReporter was unable to determine " 
					+ "properties.legacy:hasCaseNumber as String from " + serviceCaseJson);
			return CirmStatistics.UNKNOWN;
		}		
	}
	
}

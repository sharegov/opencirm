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
	 * if type and/or casenum cannot be found, UNKNOWN will be set but no exception will be thrown.
	 * @param action
	 * @param serviceCaseJson with String properties (x|x."type"|x."bo")."type" and (x|x."type"|x."bo")."properties"."legacy:hasCaseNumber" 
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
	 * @param serviceCaseJson with String properties (x|x."type"|x."bo")."type" and (x|x."type"|x."bo")."properties"."legacy:hasCaseNumber" 
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
		Json caseRoot;
		try
		{
			caseRoot = findCaseRoot(serviceCaseJson);
			return caseRoot.at("type").asString();
		} catch (Exception e)
		{
			System.err.println("SRCirmStatsDataReporter was unable to determine type as String from " + serviceCaseJson);
			return CirmStatistics.UNKNOWN;
		}		
	}
	
	/**
	 * Returns the ("properties").at("legacy:hasCaseNumber") property or CirmStatistics.UNKNOWN.
	 * jmsg.at("response").at("data")
	 * @param serviceCaseJson
	 * @throws never an exception.
	 * @return
	 */
	public static String safeDetermineHasCaseNumber(final Json serviceCaseJson) 
	{
		Json caseRoot;
		try
		{
			caseRoot = findCaseRoot(serviceCaseJson);			
			if (caseRoot.has("properties")) {
				caseRoot = caseRoot.at("properties");
			}
			if (caseRoot.has("legacy:hasCaseNumber")) {
				return caseRoot.at("legacy:hasCaseNumber").asString();
			} else 
			{
				return caseRoot.at("hasCaseNumber").asString();
			}
		} catch (Exception e)
		{
			System.err.println("SRCirmStatsDataReporter was unable to determine " 
					+ "properties.legacy:hasCaseNumber as String from " + serviceCaseJson);
			return CirmStatistics.UNKNOWN;
		}		
	}
	
	/**
	 * Tries to return the case root (data or BO) for a serviceCaseJson, LE response or response JMSMessage.
	 * @param serviceCaseOrJmsg
	 * @throws Exception if param null or non object
	 */
	static Json findCaseRoot(final Json serviceCaseOrJmsg) 
	{
		Json caseRoot = serviceCaseOrJmsg;
		if (serviceCaseOrJmsg.has("response") && serviceCaseOrJmsg.at("response").at("response").has("data")) 
		{
			caseRoot = serviceCaseOrJmsg.at("response").at("data");
		} else if (serviceCaseOrJmsg.has("bo")) caseRoot = serviceCaseOrJmsg.at("bo");
		else if (serviceCaseOrJmsg.has("data")) caseRoot = serviceCaseOrJmsg.at("data");
		//else could not yet find caseRoot
		
		//we might have case, data.case or response.data.case, so we'll investigate that:
		if (serviceCaseOrJmsg.has("case")) caseRoot = serviceCaseOrJmsg.at("case");
		return caseRoot;
	}
}

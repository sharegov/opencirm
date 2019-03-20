package org.sharegov.cirm.stats;

public class CirmStatisticsFactory
{
	
	/**
	 * Creates a lightweight CirmStatsDataReporter, which inserts data into the CirmStatistics object for a given component. 
	 * @param stats a stats object (e.g. from Refs or MDRefs) - null not allowed
	 * @param component null not allowed
	 * @return
	 */
	public static CirmStatsDataReporter createStatsReporter(CirmStatistics stats, String component) 
	{
		return new CirmStatsDataReporter(stats, component);
	}
	
	/**
	 * Creates a lightweight SRCirmStatsDataReporter, which inserts data into the CirmStatistics object for a given component. 
	 * @param stats a stats object (e.g. from Refs or MDRefs) - null not allowed
	 * @param component null not allowed
	 * @return
	 */
	public static SRCirmStatsDataReporter createServiceRequestStatsReporter(CirmStatistics stats, String component) 
	{
		return new SRCirmStatsDataReporter(stats, component);
	}

	/**
	 * Creates a thread safe CirmStatistics object, holding runtime stats of the CiRM system.
	 * @return
	 */
	public static CirmStatistics createStats() 
	{
		return new CirmStatistics();
	}

}

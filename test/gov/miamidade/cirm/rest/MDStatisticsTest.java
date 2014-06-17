package gov.miamidade.cirm.rest;

import java.util.Date;

import gov.miamidade.cirm.MDRefs;
import gov.miamidade.cirm.rest.MDStatisticsService;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sharegov.cirm.stats.CirmStatistics;
import org.sharegov.cirm.stats.CirmStatisticsFactory;
import org.sharegov.cirm.stats.SRCirmStatsDataReporter;
import org.sharegov.cirm.utils.JsonUtil;

public class MDStatisticsTest
{

	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		insertTestData();
	}

	@After
	public void tearDown() throws Exception
	{
	}

	public static void insertTestData() 
	{		
		System.out.println("Insert data start " + new Date());
		CirmStatistics s = MDRefs.mdStats.resolve();
		SRCirmStatsDataReporter sd = CirmStatisticsFactory.createServiceRequestStatsReporter(s, "PWS");
		SRCirmStatsDataReporter sd2 = CirmStatisticsFactory.createServiceRequestStatsReporter(s, "LE");
		for (int i = 0; i < 5; i++) //30 total
		{
			for (int j = 0; j < 1E6; j++)
			{
				sd.succeeded("RX_NEW_ACTIVITY", "PW44" + i, "14-1000" + i);
				sd.failed("RX_NEW_ACTIVITY", "PW44" + i,"14-1100" + i, "NPE" + i, "RX MultilineMessgage \r\n M2ndline \r\n 3rdline");
//				try
//				{
//					Thread.sleep(10);
//				} catch (InterruptedException e)
//				{
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
				sd.succeeded("RX_NEW_ACTIVITY", "PW44" + i, "14-1200" + i);
				sd.failed("TX_NEW_ACTIVITY", "PW44" + i, "14-1300" + i, "NPEX" + i, "TX XXMultilineMessgage \r\n M2ndline \r\n 3rdline");
//				try
//				{
//					Thread.sleep(10);
//				} catch (InterruptedException e)
//				{
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
				sd2.succeeded("RX_NEW_ACTIVITY", "BULKYTRA", "14-1400" + i);
				sd2.failed("TX_NEW_ACTIVITY", "ASSPA", "14-1500" + i, "NPE" + i, "TX MultilineMessgage \r\n M2ndline");
			}
		}
		System.out.println("Insert data end " + new Date());
	}
	
	@Test
	public void testAll()
	{
		MDStatisticsService s = new MDStatisticsService();
		System.out.println(JsonUtil.format(s.all()));
	}

	@Test
	public void testSum()
	{
		MDStatisticsService s = new MDStatisticsService();
		System.out.println(JsonUtil.format(s.sum()));
	}

	@Test
	public void testQuery1()
	{
		MDStatisticsService s = new MDStatisticsService();
		System.out.println(JsonUtil.format(s.query(CirmStatistics.ALL, "RX_NEW_ACTIVITY", "PW441")));
	}

	@Test
	public void testQuery2()
	{
		MDStatisticsService s = new MDStatisticsService();
		System.out.println(JsonUtil.format(s.query(CirmStatistics.ALL, "RX_NEW_ACTIVITY", "PW441")));
	}

	@Test
	public void testQuery3()
	{
		long start = new Date().getTime();
		System.out.println("Query start: ");
		MDStatisticsService s = new MDStatisticsService();
		System.out.println(JsonUtil.format(s.query(CirmStatistics.ALL, CirmStatistics.ALL, CirmStatistics.EACH)));
		System.out.println("Query duration: " + (new Date().getTime() - start) + " ms");
	}

}

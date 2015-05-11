package gov.miamidade.cirm;


import gov.miamidade.cirm.other.LiveReportingStatus;

import org.sharegov.cirm.stats.CirmStatistics;
import org.sharegov.cirm.stats.CirmStatisticsFactory;
import org.sharegov.cirm.utils.Ref;
import org.sharegov.cirm.utils.SingletonRef;

public class MDRefs
{
	public static final Ref<CirmStatistics> mdStats = new SingletonRef<CirmStatistics>(CirmStatisticsFactory.createStats());
	public static final Ref<LiveReportingStatus> liveReportingStatus = new SingletonRef<LiveReportingStatus>(new LiveReportingStatus());
}

package org.sharegov.cirm.rest;

import static org.sharegov.cirm.utils.GenUtils.ko;
import static org.sharegov.cirm.utils.GenUtils.ok;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.sharegov.cirm.Refs;
import org.sharegov.cirm.stats.CirmStatistics;
import org.sharegov.cirm.stats.CirmStatisticsFactory;
import org.sharegov.cirm.stats.CirmStatsDataReporter;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import mjson.Json;

/**
 * AnalyticsService allows tracking/logging UI activity for analysis purposes.
 * 
 * Each analytics call must have strings category and tag and may have info.
 * 
 * Data is logged and available in {@link CirmStatistics} under key
 * "AnalyticsService".
 * 
 * Simple and fast.
 * 
 * @author Thomas Hilpold
 *
 */
@Path("analytics")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AnalyticsService {

	private final String STATS_COMPONENT_KEY = "AnalyticsService";

	private volatile CirmStatsDataReporter statsReporter;

	/**
	 * Tracks an analytics event from a cirm client, eg UI, logs it and collects
	 * stats. Each event must have category and tag strings and may have an info
	 * string.
	 * 
	 * @param event
	 * @return
	 */
	@POST
	@Path("/trackEvent")
	@Consumes("application/json")
	public Json trackEvent(Json event) {
		CirmStatsDataReporter statsR = getStatsReporter();
		try {
			String category = event.at("category").asString();
			String tag = event.at("tag").asString();
			String info = "";
			if (event.has("info")) {
				info = event.at("info").asString();
			}
			ThreadLocalStopwatch.startTop("Analytics/trackEvent " + category + "-" + tag + (info.isEmpty() ? "" : "-" + info));
			statsR.succeeded(category, tag, info);
			return ok();
		} catch (Throwable t) {
			statsR.failed("unknown", "unknown", "N/A", "" + t, "" + event);
			return ko(t);
		} finally {
			ThreadLocalStopwatch.dispose();
		}
	}

	private CirmStatsDataReporter getStatsReporter() {
		if (statsReporter == null) {
			CirmStatistics cirmStats = Refs.stats.resolve();
			statsReporter = CirmStatisticsFactory.createServiceRequestStatsReporter(cirmStats, STATS_COMPONENT_KEY);
		}
		return statsReporter;
	}
}

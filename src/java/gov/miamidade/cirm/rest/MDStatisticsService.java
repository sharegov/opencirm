package gov.miamidade.cirm.rest;

import gov.miamidade.cirm.MDRefs;

import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.sharegov.cirm.rest.StatisticsService;
import org.sharegov.cirm.stats.CirmStatistics;

/**
 * A StatisticsService for Miami-Dade specific stats, which are collected in MDRefs.mdStats.
 * @see StatisticsService for rest endpoints. 
 * 
 * @author Thomas Hilpold
 *
 */
@Path("mdstatistics")
@Produces("application/json")
@Consumes("application/json")
public class MDStatisticsService extends StatisticsService
{

	@Override
	protected CirmStatistics getStats()
	{	
		return MDRefs.mdStats.resolve();
	}
	
}
	

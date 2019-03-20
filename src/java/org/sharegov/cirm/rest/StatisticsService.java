package org.sharegov.cirm.rest;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import mjson.Json;

import org.sharegov.cirm.Refs;
import org.sharegov.cirm.stats.CirmStatistics;
import org.sharegov.cirm.stats.CirmStatistics.StatsKey;
import org.sharegov.cirm.stats.CirmStatistics.StatsValue;
import org.sharegov.cirm.utils.GenUtils;

@Path("statistics")
@Produces("application/json")
public class StatisticsService extends RestService
{

	@POST
	@Path("/clear")
	@Produces(MediaType.APPLICATION_JSON)
	public Json clear() 
	{
		CirmStatistics stats = getStats();
		stats.clear();
		return GenUtils.ok().set("info", "Statstics cleared.");
	}

	@GET
	@Path("/all")
	@Produces(MediaType.APPLICATION_JSON)
	public Json all()
	{
		CirmStatistics stats = getStats();
		TreeMap<StatsKey, StatsValue> statsTree = stats.getStatistics();
		List<Json> result = new LinkedList<Json>();
		for (Map.Entry<StatsKey, StatsValue> entry : statsTree.entrySet()) 
		{
			Json cur = Json.object(entry.getKey().toJson().asString(), entry.getValue().toJson());
			result.add(cur);
		}
		return GenUtils.ok().set("stats-all", result);
	}
	
	@GET
	@Path("/sum")
	@Produces(MediaType.APPLICATION_JSON)
	public Json sum() 
	{
		CirmStatistics stats = getStats();
		TreeMap<StatsKey, StatsValue> statsTree = stats.getAggregatedStatisticsFor(CirmStatistics.ALL, CirmStatistics.ALL, CirmStatistics.ALL);
		List<Json> result = new LinkedList<Json>();
		for (Map.Entry<StatsKey, StatsValue> entry : statsTree.entrySet()) 
		{
			Json cur = Json.object(entry.getKey().toJson().asString(), entry.getValue().toJson());
			result.add(cur);
		}
		return GenUtils.ok().set("stats-sum", result);
	}

	@GET
	@Path("/query")
	@Produces(MediaType.APPLICATION_JSON)
	public Json query(@QueryParam("component") String component, @QueryParam("action") String action, @QueryParam("type") String type) 
	{
		if (component == null) return GenUtils.ko("component parameter null not allowed, use ALL or EACH for aggregated views");
		if (action == null) return GenUtils.ko("action parameter null not allowed, use ALL or EACH  for aggregated views");
		if (type == null) return GenUtils.ko("type parameter null not allowed, use ALL or EACH  for aggreagated views");		
		CirmStatistics stats = getStats();
		TreeMap<StatsKey, StatsValue> statsTree = stats.getAggregatedStatisticsFor(component, action, type);
		List<Json> result = new LinkedList<Json>();
		for (Map.Entry<StatsKey, StatsValue> entry : statsTree.entrySet()) 
		{
			Json cur = Json.object(entry.getKey().toJson().asString(), entry.getValue().toJson());
			result.add(cur);
		}
		return GenUtils.ok().set("stats-" + component + "-" + action + "-" + type, result);
	}
	
	protected CirmStatistics getStats() 
	{
		return Refs.stats.resolve();
	}
}
	

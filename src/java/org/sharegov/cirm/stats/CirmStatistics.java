package org.sharegov.cirm.stats;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.sharegov.cirm.utils.GenUtils;


import mjson.Json;

/**
 * CiRMStatistics class, keeping track of success, failure, lastSuccess, lastFailure and SR-Numbers for each interface and action including CIRMCLIENT.
 * Allows for aggregated sorted views.
 * 
 * Example Usage:
 * To collect stats:
 * 1. StatsEntry se = Refs.stats().getEntry(CIRMCLIENT, RX_NEW_SR, type | UNKNOWN)
 * 2. se.addSuccess(Case_NUmber) | se.addFailure(...)
 * ----
 * To retrieve stats:
 * A) getEntry for specific stats
 * B) getStatistics for all stats
 * C1) getAggregatedStatisticsFor(ALL, ALL, ALL) = all failures/succeess + the very last success, failure time, SR and kind.
 * C2) getAggregatedStatisticsFor(PWS, ALL, ALL) = all failures/succeess for PWS
 * D1) getAggregatedStatisticsFor(EACH, EACH, EACH) is equivalent to getStatistics() (but slower)
 * D2) getAggregatedStatisticsFor(EACH, ALL, ALL) = aggregated failures/succeess by each component (e.g. interface) + the last success, failure time, SR and kind for each component.
 * D3) getAggregatedStatisticsFor(ALL, ALL, EACH) = aggregated failures/succeess by each type (e.g. sr type) + the last success, failure time, SR and kind for each type.
 * 
 * @author Thomas Hilpold
 *
 */
public class CirmStatistics
{
	/* 
	 * Example interface names
	 * CIRMCLIENT, PWS, WCS, COM, CMS, UNKNOWN, ALL
	 * CIRMCLIENT = createNew or update and originator user
	 */
	
	/* 
	 * Exemple interface actions: 
	 *
		TX_NEW_SR,  //Cirm sends a new SR to an interface 
		TX_UPDATE_SR, 
		TX_NEW_ACTIVITY, 
		TX_RESPONSE_NEW_SR, //Cirm sends the response after receiving a new SR from an interface to the interface 
		TX_RESPONSE_UPDATE_SR, 
		TX_RESPONSE_NEW_ACTIVITY,
		RX_NEW_SR,  //Web also
		RX_UPDATE_SR, //Web also
		RX_NEW_ACTIVITY, //never Web
		RX_RESPONSE_NEW_SR, 
		RX_RESPONSE_UPDATE_SR, 
		RX_RESPONSE_NEW_ACTIVITY,   //12 interface actions total
		UNKNOWN,
		ALL
	 */
	
	public final static String UNKNOWN = "UNKNOWN";
	public final static String ALL = "ALL";
	public final static String EACH = "EACH";
	
	private volatile ConcurrentHashMap<StatsKey, StatsValue> statsEntryMap = new ConcurrentHashMap<StatsKey, CirmStatistics.StatsValue>(1001);
	
	/**
	 * Clears all results collected so far.
	 * Thread safe.
	 */
	public void clear() 
	{
		statsEntryMap.clear();
	}
	/**
	 * Retrieve a stats entry for adding success or failure now or later.
	 * Stats entry access is blocking.
	 * 
	 * THIS IS THREAD SAFE and non blocking.
	 * @param name null safe, but should be avoided -> unknown is set
	 * @param action null safe, but should be avoided -> unknown is set
	 * @param type null safe, but should be avoided -> unknown is set
	 * @return
	 */
	StatsValue getEntry(String component, String action, String type) 
	{
		if (component == null) component = UNKNOWN;
		if (action == null) action = UNKNOWN;
		if (type == null) type = UNKNOWN;
		StatsKey findKey = new StatsKey(component, action, type);
		StatsValue entry = statsEntryMap.get(findKey);
		if (entry == null) 
		{
				entry = new StatsValue();
				statsEntryMap.put(findKey, entry);
		}
		return entry;
	}
	
	/**
	 * Returns the a copy of the statistic entry map as sorted treemap - Expensive!.
	 * Entries may change while iterating over the map.
	 * Statistic Entry map may change after this method returns.
	 * The Treemap will not be modified after this methos returns.
	 * Sort order is defined in Key class compareTo method.
	 * @return
	 */
	public TreeMap<StatsKey, StatsValue> getStatistics() 
	{
		TreeMap<StatsKey, StatsValue> treeMap = new TreeMap<StatsKey, CirmStatistics.StatsValue>();
		//weakly consistent but thread and concurrent modification safe traversal
		treeMap.putAll(statsEntryMap);
		return treeMap;
	}
	
	/**
	 * Returns the a copy of the statistic entry map as sorted treemap, applying aggregation as defined by the parameters - Expensive!.
	 * For example: if the comparator defines all as 0, the resulting treemap will contain only one key/entry pair, which aggregates all statsEntries.
	 * (In this case the Key will be (UNKNOWN, UNKNOWN, UNKNOWN)
	 * If the comparator defines all types as 0, the tree will contain as many keys as different Modules/Actions are contained, but all type specific entries will be aggregated.
	 * A typical key would read (WEB, RX_UPDATE, UNKNOWN).
	 * 
	 * This method is NOT thread safe, use external synchronisation.
	 * 
	 * @param component null NOT allowed, use constants ALL or -UNKNOWN
	 * @param action null NOT allowed, use constants ALL or -UNKNOWN 
	 * @param type null NOT allowed, use constants SR_TYPE_ALL or -UNKNOWN
	 * @return
	 */
	public TreeMap<StatsKey, StatsValue> getAggregatedStatisticsFor(String component, String action, String type) 
	{
		if (component == null) throw new NullPointerException("name");
		if (action == null) throw new NullPointerException("action");
		if (type == null) throw new NullPointerException("type");
		StatsKey filterKey = new StatsKey(component, action, type);
		TreeMap<StatsKey, StatsValue> aggregateStatsTreeMap = new TreeMap<StatsKey, CirmStatistics.StatsValue>();
		//weakly consistent but thread and concurrent modification safe traversal (see ConcurrentHashMap)
		//Iterate over statsEntryMap to find matches to filterKey and aggregate the values
		StatsKey curAggregateKey = filterKey;
		for (Map.Entry<StatsKey, StatsValue> statsKeyValue : statsEntryMap.entrySet())
		{
			//Determine, if Entry shall be considered for aggregation
			StatsKey curStatsKey = statsKeyValue.getKey();
			if (isKeyMatched(filterKey, curStatsKey))
			{
				curAggregateKey = getAggregateKeyFor(filterKey, curAggregateKey, curStatsKey);
				StatsValue aggregateEntry = aggregateStatsTreeMap.get(curAggregateKey);
				if (aggregateEntry == null) {
					aggregateEntry = new StatsValue();
					//insert first new value as basis for aggregation
					aggregateStatsTreeMap.put(curAggregateKey, aggregateEntry);
				} 
				//once it's there just add to it
				aggregateEntry.aggregate(statsKeyValue.getValue());
			}
			// else skip entry
		}
		return aggregateStatsTreeMap;
	}
	
	/**
	 * Returns an aggregate key necessary during EACH queries or either component, action or type.
	 * If no parameter in the filterKey has value EACH, the given curKey is returned.
	 * 
	 * @param filterKey
	 * @param curAggregateKey
	 * @param statsKey
	 * @return
	 */
	private StatsKey getAggregateKeyFor(StatsKey filterKey, StatsKey curAggregateKey, StatsKey statsKey)
	{
		StatsKey result = curAggregateKey;
		boolean newComponentEach = (EACH.equals(filterKey.getComponent()) && !curAggregateKey.getComponent().equals(statsKey.getComponent()));
		boolean newActionEach = (EACH.equals(filterKey.getAction()) && !curAggregateKey.getAction().equals(statsKey.getAction()));
		boolean newTypeEach = (EACH.equals(filterKey.gettype()) && !curAggregateKey.gettype().equals(statsKey.gettype()));
		if (newComponentEach || newActionEach || newTypeEach) 
		{
			result = new StatsKey(
					newComponentEach? statsKey.getComponent() : filterKey.getComponent()
					,newActionEach? statsKey.getAction() : filterKey.getAction()
				    ,newTypeEach? statsKey.gettype() : filterKey.gettype());
		}
		return result;
	}

	/**
	 * Determines if an existing key matches a filter given as key.
	 * @param filterKey
	 * @param existingKey
	 * @return
	 */
	private boolean isKeyMatched(StatsKey filterKey, StatsKey existingKey) 
	{
		boolean result = false;
		boolean nameMatch = ALL.equals(filterKey.getComponent()) 
				|| EACH.equals(filterKey.getComponent()) 
				|| filterKey.getComponent().equals(existingKey.getComponent());
		if (nameMatch) 
		{
			boolean actionMatch = ALL.equals(filterKey.getAction()) 
					|| EACH.equals(filterKey.getAction()) 
					|| filterKey.getAction().equals(existingKey.getAction());
			if (actionMatch) 
			{
				result = ALL.equals(filterKey.gettype())
						|| EACH.equals(filterKey.gettype()) 
						|| filterKey.gettype().equals(existingKey.gettype());				
			}
		}
		return result;
	}
	
	/**
	 * Sortable Key for interface entries ordered by interfaceName, -action and type.
	 * Not hashable, no equals overwrite.
	 * 
	 * Thread safe.
	 * 
	 * @author Thomas Hilpold
	 *
	 */
	public static class StatsKey implements Comparable<StatsKey>
	{
		private final String component;
		private final String action;
		private final String type;
		private final int hashCode;
		
		/**
		 * Creates a sortable key for stats entries.
		 * @param component if null, UNKNOWN will be set
		 * @param action if null, UNKNOWN will be set
		 * @param type, if null, UNKNOWN will be set
		 */
		StatsKey(String component, String action, String type) {
			//defensive creation to prevent exception during stats operations
			//users can find trouble spots in the code later by reading the results which should never
			//contain any UNKNOW category.
			if (component == null) component = UNKNOWN;
			if (action == null) action = UNKNOWN;
			if (type == null) type = UNKNOWN;
			//assert no null to be set to avoid compareTo having to deal with null order
			this.component = component;
			this.action = action;
			this.type = type;
			//all final, determine constant hashcode
			this.hashCode = 5 * component.hashCode() + 3 * action.hashCode() + type.hashCode();
		}

		/**
		 * @return a string json "/" delimited
		 */
		public Json toJson() 
		{
			String result = getComponent().toString() + "/" + getAction().toString() + "/" + type; 
			return Json.make(result);
		}

		public final String getComponent()
		{
			return component;
		}

		public final String getAction()
		{
			return action;
		}

		public final String gettype()
		{
			return type;
		}

		public boolean equals(Object other) 
		{
			if (other == null || !(other instanceof StatsKey)) 
				return false;
			else 
			{
				StatsKey otherKey = (StatsKey)other;
				return getComponent().equals(otherKey.getComponent()) 
						&& getAction().equals(otherKey.getAction())
						&& gettype().equals(otherKey.gettype());
			}
		}			
		
		@Override
		public int hashCode()
		{
			return this.hashCode;
		}

		@Override
		public int compareTo(StatsKey o)
		{
			int result;
			if (o == null) 
				{
					//nulls come first, if any
					result = -1;
				}
			else 
			{
				int interfaceNameOrder = this.getComponent().compareTo(o.getComponent());
				if (interfaceNameOrder != 0) 
				{
					result = interfaceNameOrder; 
				}
				else 
				{
					int interfaceActionOrder = this.getAction().compareTo(o.getAction());
					if (interfaceActionOrder != 0) 
					{
						result = interfaceActionOrder;
					}
					else 
					{						
						int typeKey = this.gettype().compareTo(o.gettype());
						result = typeKey;
					}
				}
			}			
			return result;
		}
	}

	/**
	 * Statistics entry for success or failure of interface operations by type.
	 * 
	 * THIS CLASS IS THREAD SAFE AND BLOCKING.
	 * 
	 * It is safe for any thread to call any method on an object of this class. 
	 * 
	 * @author Thomas Hilpold
	 *
	 */
	public static class StatsValue {
		//
		private Date firstEntryTime; //time at which first success or failure is determined
		private long successCount;
		private Date lastSuccessTime;
		private String lastSuccessId;

		private long failureCount;
		private Date lastFailureTime;
		private String lastFailureId;
		private String lastFailureException;
		private String lastFailureMessage;
		
		/**
		 * Reports success for this id and increases successCount.
		 * @param id
		 */
		public synchronized void addSuccess(String id) {
			Date now = new Date();
			if (getFirstEntryTime() == null) setFirstEntryTime(now);
			setLastSuccessId(id);
			setLastSuccessTime(now);
			setSuccessCount(getSuccessCount() + 1);
		}

		/**
		 * Reports failure for this id and increases failureCount.
		 * @param id
		 * @param exception
		 * @param failureMessage
		 */
		public synchronized void addFailure(String id, String exception, String failureMessage)
		{
			Date now = new Date();	
			if (getFirstEntryTime() == null) setFirstEntryTime(now);
			setLastFailureId(id);
			setLastFailureTime(now);
			setLastFailureException(exception);
			setLastFailureMessage(failureMessage);
			setFailureCount(getFailureCount() + 1);			
		}
		
		/**
		 * For building grouped stats by various criteria.
		 * Time durations get expanded by setting earlier first and later last time.
		 * @param other
		 */
		public synchronized void aggregate(StatsValue other) {
			setSuccessCount(getSuccessCount() + other.getSuccessCount());
			setFailureCount(getFailureCount() + other.getFailureCount());
			if (other.getFirstEntryTime() != null) 
			{
				if (getFirstEntryTime() == null || other.getFirstEntryTime().before(getFirstEntryTime()))
				{
					setFirstEntryTime(other.getFirstEntryTime());
				}
			}
			if (other.getLastSuccessTime() != null) 
			{
				if (getLastSuccessTime() == null || other.getLastSuccessTime().after(getLastSuccessTime())) 
				{
					setLastSuccessTime(other.getLastSuccessTime());
					setLastSuccessId(other.getLastSuccessId());
				}
			}
			if (other.getLastFailureTime() != null)
			{
				if (getLastFailureTime() == null || other.getLastFailureTime().after(getLastFailureTime())) 
				{
					setLastFailureTime(other.getLastFailureTime());
					setLastFailureId(other.getLastFailureId());
					setLastFailureException(other.getLastFailureException());
					setLastFailureMessage(other.getLastFailureMessage());
				}
			}
		}

		/**
		 * Returns a json representation of this StatsValue as a json object.
		 * @return
		 */
		public synchronized Json toJson() 
		{
			Json result = Json.object();
			result.set("firstEntryTime", GenUtils.formatDate(getFirstEntryTime()));
			result.set("successCount", getSuccessCount());
			result.set("lastSuccessTime", getLastSuccessTime() != null ? GenUtils.formatDate(getLastSuccessTime()) : null);
			result.set("lastSuccessId", getLastSuccessId());
			result.set("failureCount", getFailureCount());
			result.set("lastFailureTime", getLastFailureTime() != null ? GenUtils.formatDate(getLastFailureTime()) : null);
			result.set("lastFailureId", getLastFailureId());
			result.set("lastFailureException", getLastFailureException());
			result.set("lastFailureMessage", getLastFailureMessage());
			return result;
		}

		public synchronized final Date getFirstEntryTime()
		{
			return firstEntryTime;
		}

		public synchronized final void setFirstEntryTime(Date firstEntryTime)
		{
			this.firstEntryTime = firstEntryTime;
		}

		public synchronized final long getSuccessCount()
		{
			return successCount;
		}

		public synchronized final void setSuccessCount(long successCount)
		{
			this.successCount = successCount;
		}

		public synchronized final Date getLastSuccessTime()
		{
			return lastSuccessTime;
		}

		public synchronized final void setLastSuccessTime(Date lastSuccessTime)
		{
			this.lastSuccessTime = lastSuccessTime;
		}

		public synchronized final String getLastSuccessId()
		{
			return lastSuccessId;
		}

		public synchronized final void setLastSuccessId(String lastSuccessId)
		{
			this.lastSuccessId = lastSuccessId;
		}

		public synchronized final long getFailureCount()
		{
			return failureCount;
		}

		public synchronized final void setFailureCount(long failureCount)
		{
			this.failureCount = failureCount;
		}

		public synchronized final Date getLastFailureTime()
		{
			return lastFailureTime;
		}

		public synchronized final void setLastFailureTime(Date lastFailureTime)
		{
			this.lastFailureTime = lastFailureTime;
		}

		public synchronized final String getLastFailureId()
		{
			return lastFailureId;
		}

		public synchronized final void setLastFailureId(String lastFailureId)
		{
			this.lastFailureId = lastFailureId;
		}

		public synchronized final String getLastFailureException()
		{
			return lastFailureException;
		}

		public synchronized final void setLastFailureException(String lastFailureException)
		{
			this.lastFailureException = lastFailureException;
		}

		public synchronized final String getLastFailureMessage()
		{
			return lastFailureMessage;
		}

		public synchronized final void setLastFailureMessage(String lastFailureMessage)
		{
			this.lastFailureMessage = lastFailureMessage;
		}

	}

}

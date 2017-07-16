package gov.miamidade.cirm.cache;

import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import mjson.Json;

/**
 * LocationInfoCache, a simple time-based cache implementation that evicts to a evictionTargetRatio.<br>
 * <br>
 * <b>put<b> will insert a new (never null) value into the cache and mark it for expiration now + entryExpireDurationMs.<br>
 * Modifying the layer array or the result Json after put is explicitly allowed, bcause both keys and values are immutable in the cache after put.<br> 
 * (If maxCacheSize is exceeded after the put eviction occurs.)<br>
 * <br>
 * <br>
 * <b>get<b> will return a non expired valueStr if available.
 * (Entries are stored as immutable strings and are parsed and returned as Json)<br>
 * <br>
 * <br>
 * Eviction strategy if size exceeded:<br>
 * Goal is to reduce cache by evictionTargetRatio (e.g. to 70%)<br>
 * This is achieved by:<br>
 * 1. Find and Remove all expired entries<br>
 * 2. If evictionTargetRatio not reached, evict additional entries brute force.
 * <br>
 * <br>
 * All operations are fully thread safe.
 * <br>
 * 
 * @author Thomas Hilpold
 *
 */
public class LocationInfoCache {
	
	private static boolean DBG = true;
	
	
	public final static double DEFAULT_EVICTION_TARGET_RATIO = 0.3;
	/**
	 * The duration after which a newly put valueStr will not be returned but removed on get.
	 */
	private final long entryExpireDurationMs;
	
	/**
	 * Maximum nr of entries in the cache, before eviction is triggered.
	 */
	private final int maxCacheSize;
	
	/**
	 * If cache entries need to be removed the target ratio for how much space should be available after evition.
	 * E.g. 0.3 means reduce from 100% to 70%.
	 */
	private final double evictionTargetRatio; //70 left after cleanup

	/**
	 * The cache.
	 */
	private final ConcurrentHashMap<LocInfoKey, ExpiringJsonValue> cache;
	
	/**
	 * Creates cache with DEFAULT_EVICTION_TARGET_RATIO 0.3.
	 * @param entryExpireDurationMs
	 * @param maxCacheSize
	 */
	public LocationInfoCache(long entryExpireDuration, int maxSize) {
		this(entryExpireDuration, maxSize, DEFAULT_EVICTION_TARGET_RATIO);
	}
	
	/**
	 * Created a cache with maxCacheSize, where each valueStr expires in entryExpireDurationMs milliseconds and if full cache will be reduced by evictionTargetRatio.
	 * @param entryExpireDurationMs >= 0
	 * @param maxCacheSize >0
	 * @param evictionTargetRatio between 0 and 0.99
	 */
	public LocationInfoCache(long entryExpireDuration, int maxSize, double evictionTargetRatio) {
		if (maxSize < 1) throw new IllegalArgumentException("MaxSize >= 1 required");
		if (entryExpireDuration < 0) throw new IllegalArgumentException("entryExpireDurationMs >= 0 ms required");
		if (evictionTargetRatio < 0) throw new IllegalArgumentException("evictionTargetRatio >= 0 required");
		if (evictionTargetRatio > 0.99) throw new IllegalArgumentException("evictionTargetRatio <= 0.99 required");
		this.entryExpireDurationMs = entryExpireDuration;
		this.maxCacheSize = maxSize;
		this.evictionTargetRatio = evictionTargetRatio;
		cache = new ConcurrentHashMap<>(maxSize * 2);
		ThreadLocalStopwatch.now("LocationInfoCache initialized entryExpireDuration: " + (entryExpireDuration / 1000) + " secs, maxCacheSize " + maxSize);
	}
	
	/**
	 * Gets modifiable valueStr if exists and not expired. Evicts expired valueStr. 
	 * @param x
	 * @param y
	 * @param layers
	 * @return
	 */
	public Json get(double x, double y, String[] layers) {
		Json result;
		LocInfoKey findKey = new LocInfoKey(x, y, layers);
		ExpiringJsonValue entry = cache.get(findKey);
		if (entry != null) {
			if (entry.expirationTime >= System.currentTimeMillis()) {
				//Not expired
				result = entry.getValueAsJson();
				if (DBG) ThreadLocalStopwatch.now("LocationInfoCache cache hit " + findKey);
			} else {
				//Expired
				cache.remove(findKey);
				result = null;
				if (DBG) ThreadLocalStopwatch.now("LocationInfoCache cache hit, but expired " + new Date(entry.expirationTime));
			}
		} else {
			//not found
			result = null;
		}
		return result;
	}
	
	/**
	 * Puts into cache, overwrites existing. May block to run eviction if cache full after put.
	 * @param x
	 * @param y
	 * @param layers
	 * @param result
	 * @return
	 */
	public void put(double x, double y, String[] layers, Json result) {
		if (result == null) throw new IllegalArgumentException("null result not allowed to be put");
		LocInfoKey key = new LocInfoKey(x, y, layers);
		if (DBG) ThreadLocalStopwatch.now("LocationInfoCache cache put " + key + " size " + cache.size());
		long expirationTime = System.currentTimeMillis() + getEntryExpireDurationMs();
		ExpiringJsonValue value = new ExpiringJsonValue(result, expirationTime);
		//Always put and possibly overwrite, expiration will be newer.
		cache.put(key, value);
		performEvictionIfNeeded();
	}
	
	/**
	 * Performs eviction if size exceeded.
	 * Will be called automatically on each put.
	 * Thread safe and blocking. Double checking if multiple threads to ensure eviction only occurs for the first entering thread.
	 */
	public void performEvictionIfNeeded() {
		if (cache.size() > getMaxCacheSize()) {
			synchronized(cache) {
				if (cache.size() > getMaxCacheSize()) {
					ThreadLocalStopwatch.now("START LocationInfoCache eviction cache size > max " + getMaxCacheSize());
					performEviction();
					ThreadLocalStopwatch.now("END LocationInfoCache eviction cache size after: " + cache.size());
				}
			}
		}		
	}
	
	/**
	 * Evicts all expired and if target not reached by that, evicts some more.
	 */
	private void performEviction() {
		ThreadLocalStopwatch.now("START LocationInfoCache evictAllExpired ");
		int removeCount = evictAllExpired();
		double actualCleanupRatio = removeCount / (double)getMaxCacheSize();
		ThreadLocalStopwatch.now("End LocationInfoCache evictAllExpired, removed: " + removeCount + " Ratio was: " + actualCleanupRatio + " Target: " + getEvictionTargetRatio());
		if (actualCleanupRatio < getEvictionTargetRatio()) {
			int nrToPurge = (int)Math.round((getEvictionTargetRatio() - actualCleanupRatio) * getMaxCacheSize());
			ThreadLocalStopwatch.now("START LocationInfoCache evictSome " + nrToPurge + " ");
			int purged = evictSome(nrToPurge);
			ThreadLocalStopwatch.now("END LocationInfoCache evictSome: " + purged);
		}
	}
	
	/**
	 * Evicts all expired entries
	 * @return nr of removed entries
	 */
	private int evictAllExpired() {
		long now = System.currentTimeMillis();
		Iterator<Entry<LocInfoKey, ExpiringJsonValue>> it = cache.entrySet().iterator();
		int removeCount = 0;
		while (it.hasNext()) {
			Entry<LocInfoKey, ExpiringJsonValue> e = it.next();
			if (e.getValue().getExpirationTime() <= now) {
				it.remove();
				removeCount ++;
			}
		}
		return removeCount;
	}
	
	/**
	 * Evicts a fixed number of entries by deleting in entrySet iterator order.
	 * @param nrToPurge
	 * @return nr of actually purged.
	 */
	private int evictSome(int nrToPurge) {
		if (nrToPurge == 0) return 0;
		Iterator<Entry<LocInfoKey, ExpiringJsonValue>> it = cache.entrySet().iterator();
		int removeCount = 0;
		while (it.hasNext() && removeCount < nrToPurge) {
				it.next();
				it.remove();
				removeCount++;
			}
		return removeCount;
	}
	
	/**
	 * The duration any new entry is considered valid from the time of put.
	 * @return
	 */
	public long getEntryExpireDurationMs() {
		return entryExpireDurationMs;
	}
	
	/**
	 * The max number of allowed cache entries before eviction occurs.
	 * @return
	 */
	public int getMaxCacheSize() {
		return maxCacheSize;
	}
	
	/**
	 * The ration by which cache must be reduced on eviction.
	 * @return
	 */
	public double getEvictionTargetRatio() {
		return evictionTargetRatio;
	}
	
	/**
	 * LocInfoKey Immutable Cache key with hashCode and equals implementation for hashing.<br>
	 * ToString is also provided.<br>
	 *
	 * @author Thomas Hilpold
	 *
	 */
	private class LocInfoKey {
		private final double x;
		private final double y;
		private final String[] layers;
		private final int hash;

		private LocInfoKey(double x, double y, String[] layers) {
			this.x = x;
			this.y = y;
			if (layers != null) {
				this.layers = layers.clone();
			} else {
				this.layers = null;
			}
			//Set hashcode for immutable LocInfoKey
			this.hash = calcHashCode();
		}
		
		private int calcHashCode() {
			return (int) x * (int) y * (layers != null? layers.length : 1); 
		}
		
		@Override		
		public int hashCode() {
			return hash;
		}
		
		
		@Override
		public boolean equals(Object obj) {
			if (obj != null && obj instanceof LocInfoKey) {
				LocInfoKey other = (LocInfoKey)obj;
				boolean xyMatch = x == other.x 
								&& y == other.y;
				boolean layerMatch = false;
				if (xyMatch) {
    				if (other.layers == null) {
    					if (layers == null) {
    						layerMatch = true;
    					}
    				} else {
    					if (layers != null) {
    						layerMatch = Arrays.equals(layers, other.layers);
    					}
    				}
				}
				return xyMatch && layerMatch;						
			} else {
				return false;
			}
		}

		@Override
		public String toString() {
			return "" + x + ", " + y + " Layers: " + (layers == null? layers : layers.length);
		}				
	}
	
		
	/**
	 * ExpiringJsonValue stores Json as immutable string and parses on access.
	 *
	 * @author Thomas Hilpold
	 *
	 */
	private class ExpiringJsonValue {
		
		private final String valueStr; //Immutable String reprentation of the Json

		private final long expirationTime;
		
		private ExpiringJsonValue(Json value, long expirationTime) {
			if (value == null) throw new IllegalArgumentException("Entry null");  
			if (expirationTime <= 0) throw new IllegalArgumentException("Expiration time <=0");  
			this.valueStr = value.toString();
			//System.out.println("Value size: " + valueStr.length() / 512.0 + " kBytes");
			this.expirationTime = expirationTime;
		}
		
		private long getExpirationTime() {
			return expirationTime;
		}

		/**
		 * Gets the Entry as Json object which is allowed to be modified by client (parses immutable string).
		 * @return
		 */
		private Json getValueAsJson() {
			return Json.read(valueStr);
		}		
	}
}

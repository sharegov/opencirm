package org.sharegov.cirm.utils;

import java.util.HashMap;
import java.util.Map;

import mjson.Json;

public class ServiceCaseManagerCache {
	private static ServiceCaseManagerCache instance = null;
	private Map<String, Json> cache;
	
	private ServiceCaseManagerCache (){
		cache = new HashMap<String, Json>();
	}
	
	public static ServiceCaseManagerCache getInstance(){
		if (instance == null){
			instance = new ServiceCaseManagerCache ();
		}
		return instance;
	}
	
	public Json getElement (String aKey){
		if (instance == null) return Json.nil();
		
		if (cache.containsKey(aKey)){
			return cache.get(aKey);
		} else {
			return Json.nil();
		}
	}
	
	public boolean setElement (String aKey, Json aValue){
		if (instance == null) return false;
		
		cache.put(aKey, aValue);
		
		return true;
	}

}

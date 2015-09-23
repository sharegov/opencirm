package org.sharegov.cirm.utils;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import mjson.Json;

public class JsonSchemaHandler {
	
	private static JsonSchemaHandler instance; 
	private Map<String, Json> cache;
	
	/**
	 * private to defeat multiple instantiation
	 * 
	 */
	private JsonSchemaHandler() {
		cache = new ConcurrentHashMap<String, Json>();
	}

	/**
	 * Singleton instance getter. Synchronized to defeat multiple instantiation when instance == null
	 *  
	 * @return the same unique instance of the class 
	 */
	public synchronized static JsonSchemaHandler getInstance(){
		if (instance == null){
			instance = new JsonSchemaHandler ();
		}
		return instance;
	}
	
	public boolean validate (String schemaUri, Json o){
		Json s = getFullSchema(schemaUri);
		
		return crawl (s, o);
	}
	
	private boolean crawl (Json s, Json o){
		if (checkType(s, o)){			
			if (o.isArray()){ 
				if (checkArray(s, o)) {
					return true;
				}
			} else if (o.isObject()){
				if (checkObject(s, o)){
					return true;
				}
			} else if (checkLiteral(s, o)){
				return true;
			}
			
		}
		
		return false;
	}
	
	private boolean checkType (Json s, Json o){
		if (s.has("type")){
			String sType = s.at("type").asString();
			if (sType.compareToIgnoreCase("array") == 0 && o.isArray()) return true; 
			if (sType.compareToIgnoreCase("object") == 0 && o.isObject()) return true; 
			if (sType.compareToIgnoreCase("string") == 0 && o.isString()) return true;
			if (sType.compareToIgnoreCase("number") == 0 && o.isNumber()) return true;
			if (sType.compareToIgnoreCase("boolean") == 0 && o.isBoolean()) return true;
		}
		
		return false;
	}
	
	private boolean checkArray (Json s, Json o){
		if (s.has("minItems")){
			if (o.asJsonList().size() <= s.at("minItems").asInteger()) return false;
		}
		
		if (s.has("items")){
			if (!s.at("items").has("type")) return false;
			
			for(Json e: o.asJsonList())
				if (!crawl(s.at("items"), e)) return false;
		}
		
		// TO-DO: unique elements
		
		return true;
	}
	
	private boolean checkObject (Json s, Json o){
		if (s.has("properties")){
			if (s.has("oneOf")) return false;
			else if (!checkProperties(s, o)) return false;
		}
		
		if (s.has("required")){
			if (s.has("oneOf")) return false;
			else if (!checkRequiredProperties(s, o)) return false;
		}
		
		if (s.has("additionalProperties")){
			if (s.has("oneOf")) return false;
			else if (!checkAdditionalProperties(s, o)) return false;
		}
		
		if (s.has("oneOf")){
			if (s.has("properties")||s.has("required")||s.has("additionalProperties")) return false;
			else if (!checkObjectOneOf(s, o)) return false;
		}
		
		return true;
	}
	
	private boolean checkProperties(Json s, Json o){
		Map<String,Json> schemaProperties = s.at("properties").asJsonMap();
		for (Map.Entry<String, Json> sKeyValue : schemaProperties.entrySet()) {
			if (!o.has(sKeyValue.getKey())) return false;
			else if (!crawl(sKeyValue.getValue(), o.at(sKeyValue.getKey()))) return false;				
		}
		
		return true;
	}
	
	private boolean checkRequiredProperties(Json s, Json o){		
		if (!s.at("required").isArray()) return false;
		
		for (Json p: s.at("required").asJsonList())
			if (!o.has(p.asString())) return false;
		
		return true;
	}
	
	private boolean checkAdditionalProperties(Json s, Json o){
		Map<String,Json> objectProperties = o.asJsonMap();
		for (Map.Entry<String, Json> oKeyValue : objectProperties.entrySet()) {	
			if (!s.at("properties").has(oKeyValue.getKey())) return false;
		}
		
		return true;
	}
	
	private boolean checkObjectOneOf(Json s, Json o){
		if (checkType(s, o) && s.has("oneOf")){
			for (Json e: s.at("oneOf").asJsonList()){
				if (crawl(e, o)) return true;
			}
		}
		
		return false;
	}

	private boolean checkLiteral (Json s, Json o){
		if (s.has("value")){
			if (!checkLiteralValue(s, o)) return false;
		}
		
		if (s.has("oneOf")){
			if (!checkLiteralOneOf(s, o)) return false;
		}
		
		return true;
	}
	
	private boolean checkLiteralValue (Json s, Json o){
		if (o.isString() && s.at("value").asString().compareTo(o.asString()) != 0) return false;
		if (o.isBoolean() && s.at("value").asBoolean() != o.asBoolean()) return false;
		if (o.isNumber() && s.at("value").asDouble() != o.asDouble()) return false;
		
		return true;
	}
	
	private boolean checkLiteralOneOf (Json s, Json o){
		for (Json e : s.at("oneOf").asJsonList()){
			Json p = Json.object().set("value", e);
			if (checkType(e, o) && checkLiteralValue(p, o)) return true;
		}
		
		return false;
	}

	public Json getFullSchema (String schemaUri){
		try {
			URL url = new URL(schemaUri);	
			
			String host = url.getProtocol() + "://" + url.getHost() + ":" + Integer.toString(url.getPort());
			String path = url.getPath();

			return buildFullSchema (host , path);
			
		} catch (Exception e) {
			System.out.println("Malformed JSON Schema URI:" + schemaUri);
			e.printStackTrace();
		}
		
		return Json.object();
	}
	
	private Json buildFullSchema (String host, String path){
		Json content = cache.get(host + path);
		if (content == null) {
			content = GenUtils.httpGetJson(host + path);
			cache.put(host + path, content);
		}
		
		Json result = parseSchema (content, host);
		cache.put(host + path, result);
		return result;
	}
	
	private Json parseSchema (Json o, String host){		
		if (o.isArray()){
			int i = 0;
			for(Json e: o.asJsonList()){
				String reference = getReference(e);
				if (reference != null){
					o.asJsonList().set(i, buildFullSchema(host, reference));
				} else {
					o.asJsonList().set(i, parseSchema(e, host));
				}
				i++;
			}			
		} else if (o.isObject()){
			Map<String,Json> properties = o.asJsonMap();
			for (Map.Entry<String, Json> propKeyValue : properties.entrySet()) {
				Json e = propKeyValue.getValue();
				String reference = getReference(e);
				if (reference != null){
					o.set(propKeyValue.getKey(), buildFullSchema(host, reference));
				} else {
					o.set(propKeyValue.getKey(), parseSchema(e, host));
				}
			}
		}
		
		return o;		
	}
	
	private boolean isReference (Json e){
		return e.isObject() && e.has("$ref");
	}
	
	private String getReference (Json e){
		return e.isObject() && e.has("$ref") ? e.at("$ref").asString() : "";
	}
}

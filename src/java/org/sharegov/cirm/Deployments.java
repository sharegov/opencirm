package org.sharegov.cirm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.sharegov.cirm.utils.JsonUtil;

import mjson.Json;

public class Deployments {
	private static String deploymentsFile = "c:/cirmservices/persistent/deployment_list.json";
	
	private static Deployments instance;
	private Map <Long, Deployment> deploymentMap;	
	
	/**
	 * private to defeat multiple instantiation
	 * 
	 */
	private Deployments() {		
		deploymentMap = new ConcurrentHashMap<Long, Deployment>();
		
		try {
			
			Json persisted = JsonUtil.readFromFile(deploymentsFile);
			
			fromJson(persisted);
			
			cleanup();
			
			} catch (Exception e){
				System.out.println("Cannot recover saved deployments!");
				System.out.println(e.getMessage());
				e.printStackTrace();
				
				deploymentMap.clear();
			}
	}
	
	/**
	 * Singleton instance getter. Synchronized to defeat multiple instantiation when instance == null
	 *  
	 * @return the same unique instance of the class 
	 */
	public synchronized static Deployments getInstance(){
		if (instance == null){
			instance = new Deployments ();
		}
		return instance;
	}
	
	public Deployment newDeployment (Deployment dx){
		deploymentMap.put(dx.getId(), dx);
		
		return dx;
	}
	
	public Deployment newDeployment (long date, int revision, boolean restart, boolean enabled){
		return newDeployment(date, revision, restart, enabled, true);		
	}
	
	private Deployment newDeployment (long date, int revision, boolean restart, boolean enabled, boolean persist){
		Deployment dx = null;
		
		boolean collision = true;
		long time = System.currentTimeMillis();
		
		do {
			time++;
			if (deploymentMap.containsKey(time)){
				continue;
			} else {
				dx = new Deployment (time, date, revision, restart, enabled);
				deploymentMap.put(time, dx);
				collision = false;
			}
		} while (collision );
		
		if (persist){
			persist();
		}
		
		return dx;		
	}
	
	public Json updateDeployment (Json obj){
		if (obj.isObject() && obj.has("id") && obj.has("revision") && obj.has("date") && obj.has("restart") && obj.has("enabled")){
			return updateDeployment(obj.at("id").asLong(), obj.at("date").asLong(), obj.at("revision").asInteger(), obj.at("restart").asBoolean(), obj.at("enabled").asBoolean()).toJson();
		} else {
			throw new RuntimeException("Invalid Deployment structure.");
		}					
	}
	
	public Deployment updateDeployment (long deploymentID, long date, int revision, boolean restart, boolean enabled){
		Deployment dx = deploymentMap.get(deploymentID);
		
		if (dx != null){
			dx.updateAll(date, revision, restart, enabled);
			
			persist();
		} 
		
		return dx;
	}
	
	public Deployment deleteDeployment (long deploymentID){
		Deployment dx = deploymentMap.remove(deploymentID);
		
		persist();
		
		return dx;
	}
	
	public Deployment getDeployment (long deploymentID){
		return deploymentMap.get(deploymentID);
	}
	
	public Json toJson (){
		Json result = Json.array();
		
		for (Map.Entry<Long, Deployment> e: deploymentMap.entrySet()){
			Json node = e.getValue().toJson();
			
			result.add(node);
		}
		
		return result;
		
		
	}
	
	public void persist(){
		synchronized (instance) {
			try {
				JsonUtil.writeToFile(toJson(), deploymentsFile);
			} catch (Exception e){
				System.out.println("Cannot save deployment list to file!");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
		}		
	}
	
	private void fromJson (Json obj){
		if (obj.isNull()) return;
		
		deploymentMap.clear();
		
		if (obj.isArray()){
			for (Json e: obj.asJsonList()){
				if (e.isObject() && e.has("id") && e.has("revision") && e.has("date") && e.has("restart") && e.has("enabled")){
					Deployment dx = new Deployment(e.at("id").asLong(), e.at("date").asLong(), e.at("revision").asInteger(), e.at("restart").asBoolean(), e.at("enabled").asBoolean());
					
					deploymentMap.put(e.at("id").asLong(), dx);							
				} else {
					throw new RuntimeException("Invalid Node structure.");
				}
			}
		} else  {
			throw new RuntimeException("Input is not a list Nodes.");
		}
	}
	
	private void cleanup (){
		List<Long> keyToDelete = new ArrayList<>();
		
		for (Map.Entry<Long, Deployment> e: deploymentMap.entrySet()){
			if (e.getValue().getDate() < System.currentTimeMillis()){
				keyToDelete.add(e.getKey());
			}
		}
		
		for (Long keyx: keyToDelete){
			deploymentMap.remove(keyx);
		}
		
		if (keyToDelete.size() > 0){
			persist();
		}
	}
	
}

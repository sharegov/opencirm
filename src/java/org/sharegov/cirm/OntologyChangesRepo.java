package org.sharegov.cirm;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.sharegov.cirm.utils.JsonUtil;
import org.sharegov.cirm.utils.OntologyCommit;

import mjson.Json;

public class OntologyChangesRepo {
	
	private static String repositoryFile = "c:/cirmservices/persistent/changes_repository.json";
	
	private static OntologyChangesRepo instance;
	private Map<String, Map<Integer, OntologyCommit>> ontoChangesMap;	
	
	/**
	 * private to defeat multiple instantiation
	 * 
	 */
	private OntologyChangesRepo() {		
		ontoChangesMap = new ConcurrentHashMap<String, Map<Integer, OntologyCommit>>();
		
		try {
		
		Json persisted = JsonUtil.readFromFile(repositoryFile);
		
		fromJson(persisted);
		
		} catch (Exception e){
			System.out.println("Cannot recover saved repository!");
			System.out.println(e.getMessage());
			e.printStackTrace();
			
			ontoChangesMap.clear();
		}
	}
	
	/**
	 * Singleton instance getter. Synchronized to defeat multiple instantiation when instance == null
	 *  
	 * @return the same unique instance of the class 
	 */
	public synchronized static OntologyChangesRepo getInstance(){
		if (instance == null){
			instance = new OntologyChangesRepo ();
		}
		return instance;
	}
	
	/**
	 * Getter for the changes map
	 * 	
	 * @return
	 */
	public Map<Integer, OntologyCommit> getAllRevisionChangesForOnto (String onto){
		return ontoChangesMap.get(onto);
	}
	
	public  OntologyCommit getOntoRevisionChanges(String onto, int revision) {		
		if (ontoChangesMap.get(onto) == null){
			return null;
		} else if (ontoChangesMap.get(onto).get(revision) == null){
			return null;
		} else return ontoChangesMap.get(onto).get(revision);
	}
	
	public void setOntoRevisionChanges(String onto, int revision, String userName, String comment, List <OWLOntologyChange> changes, long timeStamp) {
		if (ontoChangesMap.get(onto) == null){
			ontoChangesMap.put(onto, new ConcurrentHashMap<Integer, OntologyCommit>());
		}
		ontoChangesMap.get(onto).put(revision, new OntologyCommit(userName, comment, changes, timeStamp, revision));
		
		persist ();
	}
	
	public void setOntoRevisionChanges(String onto, int revision, OntologyCommit commit) {
		setOntoRevisionChanges(onto, revision, commit, true);
	}
	
	private void setOntoRevisionChanges(String onto, int revision, OntologyCommit commit, boolean persist) {
		if (ontoChangesMap.get(onto) == null){
			ontoChangesMap.put(onto, new ConcurrentHashMap<Integer, OntologyCommit>());
		}
		ontoChangesMap.get(onto).put(revision, commit);	
		
		if (persist){
			persist ();
		}
	}
	
	public void deleteOntoRevisionChanges(String onto, int revision){
		if (ontoChangesMap.get(onto) == null) return;
		
		ontoChangesMap.get(onto).remove(revision);
		
		persist ();
	}
	
	public void clearAll (){
		ontoChangesMap.clear();
		
		persist ();
	}
	
	public void persist(){
		synchronized (instance) {
			try {
				JsonUtil.writeToFile(toJson(), repositoryFile);
			} catch (Exception e){
				System.out.println("Cannot save repository to file!");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
		}		
	}
	
	public Json toJson(){
		Json result = Json.array();
	
		for (Map.Entry<String, Map<Integer, OntologyCommit>> e : ontoChangesMap.entrySet()){
			Json node = Json.object().set("ontology", e.getKey());
			
			node.set("revisions", serializeRevisions(e.getValue()));
			
			result.add(node.dup());
		}		
	
	
		return result;
	}
	
	private Json serializeRevisions (Map<Integer, OntologyCommit> revisions){
		Json result = Json.array();
		
		for (Map.Entry<Integer, OntologyCommit> e : revisions.entrySet()){
			Json node = Json.object().set("revisionNumber", e.getKey());
			
			node.set("commit",e.getValue().toJson());
			
			result.add(node.dup());
		}		
	
	
		return result;
	}
	
	private void fromJson (Json obj){
		if (obj.isNull()) return;
		
		ontoChangesMap.clear();
		
		if (obj.isArray()){
			for (Json e: obj.asJsonList()){
				if (e.isObject() && e.has("ontology") && e.has("revisions") && e.at("revisions").isArray()){
					
					for (Json rx: e.at("revisions").asJsonList()){
						if (rx.isObject() && rx.has("revisionNumber") && rx.has("commit") && rx.at("commit").isObject()){
							OntologyCommit commitx = new OntologyCommit(rx.at("commit"));
							
							setOntoRevisionChanges(e.at("ontology").asString(), rx.at("revisionNumber").asInteger(), commitx, false);
							
						} else {
							throw new RuntimeException("Invalid Revision structure.");
						}
					}
					
				} else {
					throw new RuntimeException("Invalid Node structure.");
				}
			}
		} else  {
			throw new RuntimeException("Input is not a list Nodes.");
		}
	}
}

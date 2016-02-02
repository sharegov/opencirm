package org.sharegov.cirm;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.sharegov.cirm.utils.OntologyCommit;

public class OntologyChangesRepo {
	public class OntoChangesReference {
		private String onto;
		private int revision;
		private OntologyCommit value;
		
		public OntoChangesReference (String onto, int revision, OntologyCommit value){
			this.onto = onto;
			this.revision = revision;
			this.value = value;
		}
		
		public String getOnto() {
			return onto;
		}
		public void setOnto(String onto) {
			this.onto = onto;
		}
		public int getRevision() {
			return revision;
		}
		public void setRevision(int revision) {
			this.revision = revision;
		}
		public OntologyCommit getValue() {
			return value;
		}
		public void setValue(OntologyCommit value) {
			this.value = value;
		}
	}
	
	private static OntologyChangesRepo instance;
	private Map<String, Map<Integer, OntologyCommit>> ontoChangesMap;	
	
	/**
	 * private to defeat multiple instantiation
	 * 
	 */
	private OntologyChangesRepo() {
		ontoChangesMap = new ConcurrentHashMap<String, Map<Integer, OntologyCommit>>();
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
	
	public void setOntoRevisionChanges(String onto, int revision, String userName, String comment, List <OWLOntologyChange> changes) {
		if (ontoChangesMap.get(onto) == null){
			ontoChangesMap.put(onto, new ConcurrentHashMap<Integer, OntologyCommit>());
		}
		ontoChangesMap.get(onto).put(revision, new OntologyCommit(userName, comment, changes));		
	}
	
	public void setOntoRevisionChanges(String onto, int revision, OntologyCommit commit) {
		if (ontoChangesMap.get(onto) == null){
			ontoChangesMap.put(onto, new ConcurrentHashMap<Integer, OntologyCommit>());
		}
		ontoChangesMap.get(onto).put(revision, commit);		
	}
	
	public void deleteOntoRevisionChanges(String onto, int revision){
		if (ontoChangesMap.get(onto) == null) return;
		
		ontoChangesMap.get(onto).remove(revision);
	}
	
	public void clearAll (){
		ontoChangesMap.clear();
	}
}

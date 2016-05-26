package org.sharegov.cirm.utils;

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

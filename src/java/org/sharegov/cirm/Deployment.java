package org.sharegov.cirm;

import mjson.Json;

public class Deployment {
	private long date;
	private int revision;
	private boolean restart;
	private boolean enabled;
	private long id;
	
	public long getId() {
		return id;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public long getDate() {
		return date;
	}

	public void setDate(long date) {
		this.date = date;
	}

	public int getRevision() {
		return revision;
	}

	public void setRevision(int revision) {
		this.revision = revision;
	}

	public boolean isRestart() {
		return restart;
	}

	public void setRestart(boolean restart) {
		this.restart = restart;
	}
	
	public Deployment (long id, long date, int revision, boolean restart, boolean enabled){			
		this.id = id;
		this.date = date;
		this.revision = revision;
		this.restart = restart;
		this.enabled = enabled;
		
	}
	
	public void updateAll (long date, int revision, boolean restart, boolean enabled){		
		this.date = date;
		this.revision = revision;
		this.restart = restart;
		this.enabled = enabled;
	}
	
	public Json toJson (){
		return Json.object()
			      .set("id", id)
			      .set("date", date)
			      .set("revision", revision)
			      .set("enabled", enabled)
			      .set("restart", restart);				      
	}

}

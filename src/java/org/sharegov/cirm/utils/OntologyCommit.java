package org.sharegov.cirm.utils;

import java.util.List;

import org.semanticweb.owlapi.model.OWLOntologyChange;

import mjson.Json;
	
	public class OntologyCommit {
		private boolean approved;
		
		public boolean isApproved() {
			return approved;
		}

		public void setApproved(boolean approved) {
			this.approved = approved;
		}

		private long timeStamp;
		
		public long getTimeStamp() {
			return timeStamp;
		}

		public void setTimeStamp(long timeStamp) {
			this.timeStamp = timeStamp;
		}

		private String userName, comment;
		
		public String getUserName() {
			return userName;
		}

		public void setUserName(String userName) {
			this.userName = userName;
		}

		public String getComment() {
			return comment;
		}

		public void setComment(String comment) {
			this.comment = comment;
		}

		public List<OWLOntologyChange> getChanges() {
			return changes;
		}

		public void setChanges(List<OWLOntologyChange> changes) {
			this.changes = changes;
		}

		private List <OWLOntologyChange> changes;
		
		public OntologyCommit (String userName, String comment, List <OWLOntologyChange> changes, long timeStamp){
			this.userName = userName;
			this.comment = comment;
			this.changes = changes;
			this.timeStamp = timeStamp;
			this.approved = false;
		}
		
		public Json toJson (){
			Json result = Json.object()
					      .set("userName", userName)
					      .set("comment", comment)
					      .set("timeStamp", timeStamp)
					      .set("approved", approved)
					      .set("changes", Json.array());
			
			for (OWLOntologyChange chx: changes){
				result.at("changes").add(serializeChange(chx));
			}
			
			return result;
		}
		
		private Json serializeChange(OWLOntologyChange change){
			Json result = Json.object();
			
			if (change.getClass().toString().toLowerCase().contains("remove")){
				result.set("type", "remove");
			} else {
				result.set("type", "add");
			}
			
			result.set("axiom", change.getAxiom().toString());
			
			return result;
		}
	}

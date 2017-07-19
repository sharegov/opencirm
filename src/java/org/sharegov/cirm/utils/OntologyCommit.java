package org.sharegov.cirm.utils;

import static org.sharegov.cirm.OWL.owlClass;

import java.util.ArrayList;
import java.util.List;

import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.MetaOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;

import mjson.Json;
	
	public class OntologyCommit {
		private int revision;
		
		public int getRevision() {
			return revision;
		}

		public void setRevision(int revision) {
			this.revision = revision;
		}

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
		
		public OntologyCommit (String userName, String comment, List <OWLOntologyChange> changes, long timeStamp, int revision){
			this.userName = userName;
			this.comment = comment;
			this.changes = changes;
			this.timeStamp = timeStamp;
			this.approved = false;
			this.revision = revision;
		}
		
		public OntologyCommit (Json data){
			if (data.has("userName") && data.has("comment")  &&
				data.has("timeStamp")&&	data.has("approved") &&
				data.has("revision") &&	data.has("changes")  && data.at("changes").isArray()){
				
				userName = data.at("userName").asString();
				comment = data.at("comment").asString();
				timeStamp = data.at("timeStamp").asLong();
				approved = data.at("approved").asBoolean();
				revision = data.at("revision").asInteger();
				
				changes = new ArrayList<>();
				
				OWLOntology O = OWL.ontology();
				String ontologyIri = Refs.defaultOntologyIRI.resolve();

				if (O == null) {
					throw new RuntimeException("Ontology not found: " + ontologyIri);
				}

				OWLOntologyManager manager = OWL.manager();
				OWLDataFactory factory = manager.getOWLDataFactory();
								
				for (Json chx: data.at("changes").asJsonList()){
					changes.add(buildChange(chx, O, factory));
				}
				
			} else {
				throw new RuntimeException("Invalid Ontology Revision structure.");
			}
		}
		
		public Json toJson (){
			Json result = Json.object()
					      .set("userName", userName)
					      .set("comment", comment)
					      .set("timeStamp", timeStamp)
					      .set("approved", approved)
					      .set("revision", revision)
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
			
			result.set("axiom", serializeAxiom(change.getAxiom()));
			
			return result;
		}
		
		private Json serializeAxiom(OWLAxiom ax){	
			try {
			String fullAxiom = ax.toString();
			String axiomType = ax.getAxiomType().toString();
			String signature = fullAxiom.replace(axiomType, "");
			signature = signature.substring(1, signature.length()-1);			
			
			
			Json result = decomposeAxiom(axiomType, signature);
			
			result.set("fullAxiom", fullAxiom);
			
			return result;
			
			} catch (Exception e){
				System.out.println("Error while serializing: " + ax.toString());
				e.printStackTrace();
				return Json.object().set("error", e.getMessage());
			}
		}
		
		private Json decomposeAxiom(String axiomType, String signature){
			Json result = Json.object();
			result.set("type",axiomType);
			
			switch (axiomType){
				case "DataPropertyAssertion": decomposeDataPropertyAssertionAxiom(result, signature);
					break;
				case "ObjectPropertyAssertion": decomposeObjectPropertyAssertionAxiom(result, signature);
					break;
				case "AnnotationAssertion": decomposeAnnotationAssertionAxiom(result, signature);
					break;
				case "ClassAssertion": decomposeClassAssertionAxiom(result, signature);
					break;
				default: throw new RuntimeException("Unknown Axiom Type: " + axiomType);
			}
			
			return result;
		}
		
		private void decomposeDataPropertyAssertionAxiom (Json obj, String signature){
			String placeholder = "str_value_" + String.valueOf(System.currentTimeMillis());
			String strValue = "";
			int svStarts = signature.indexOf('"') + 1;
			if (svStarts > 0){
				int svEnds  = signature.lastIndexOf('"');
				strValue = signature.substring(svStarts, svEnds);
				signature = signature.replace('"' + strValue + '"', '"' + placeholder + '"');
			}
			signature = signature.replaceAll("> ", ">%%");
			String[] signatureComponents = signature.split("%%");
			obj.set("property", signatureComponents[0].substring(1, signatureComponents[0].length()-1));
			obj.set("individual", signatureComponents[1].substring(1, signatureComponents[1].length()-1));
			String[] valueComponents = signatureComponents[2].split("\\^\\^");
			String value =  valueComponents[0].substring(1, valueComponents[0].length()-1);
			if (value.contains(placeholder)){
				value = strValue;
			}
			obj.set("value", value);
			obj.set("xsdType", valueComponents[1]);
		}
		
		private void decomposeObjectPropertyAssertionAxiom (Json obj, String signature){
			signature = signature.replaceAll("> ", ">%%");
			String[] signatureComponents = signature.split("%%");
			obj.set("property", signatureComponents[0].substring(1, signatureComponents[0].length()-1));
			obj.set("individual", signatureComponents[1].substring(1, signatureComponents[1].length()-1));
			obj.set("value", signatureComponents[2].substring(1, signatureComponents[2].length()-1));
		}
		
		private void decomposeAnnotationAssertionAxiom (Json obj, String signature){
			signature = signature.replace("rdfs:label", "<rdfs:label>");
			signature = signature.replace("rdfs:comment", "<rdfs:comment>");
			signature = signature.replaceAll("> ", ">%%");
			String[] signatureComponents = signature.split("%%");
			obj.set("property", signatureComponents[0].substring(1, signatureComponents[0].length()-1));
			obj.set("individual", signatureComponents[1].substring(1, signatureComponents[1].length()-1));
			String[] valueComponents = signatureComponents[2].split("\\^\\^");
			obj.set("value", valueComponents[0].substring(1, valueComponents[0].length()-1));
			obj.set("xsdType", valueComponents[1]);
		}
		
		private void decomposeClassAssertionAxiom (Json obj, String signature){
			signature = signature.replaceAll("> ", ">%%");
			String[] signatureComponents = signature.split("%%");
			obj.set("class", signatureComponents[0].substring(1, signatureComponents[0].length()-1));
			obj.set("individual", signatureComponents[1].substring(1, signatureComponents[1].length()-1));
		}
		
		private OWLOntologyChange buildChange(Json chx, OWLOntology O, OWLDataFactory factory){
			if (chx.has("type")&&chx.has("axiom")&&chx.at("axiom").isObject()){
				OWLAxiom ax = buildAxiom(chx.at("axiom"), O, factory);
				return chx.at("type").asString().toLowerCase().contains("add") ? new AddAxiom(O, ax): new RemoveAxiom(O, ax);
			} else {
				throw new RuntimeException("Invalid Ontology Change structure.");
			}		
		}
		
		private OWLAxiom buildAxiom(Json ax, OWLOntology O,  OWLDataFactory factory){
			if (ax.has("type")){
				switch (ax.at("type").asString()){
					case "DataPropertyAssertion": return buildDataPropertyAssertionAxiom(ax, O, factory);
					case "ObjectPropertyAssertion": return buildObjectPropertyAssertionAxiom(ax, O, factory);
					case "AnnotationAssertion": return buildAnnotationAssertionAxiom(ax, O, factory);
					case "ClassAssertion": return buildClassAssertionAxiom(ax, O, factory);
					default: throw new RuntimeException("Unknown Axiom Type: " + ax.at("type").asString());
				}
			} else {
				throw new RuntimeException("Invalid Axiom structure.");
			}	
			
		}
		
		private OWLAxiom buildDataPropertyAssertionAxiom(Json ax, OWLOntology O, OWLDataFactory factory){
			if (ax.has("property") && ax.has("individual") && ax.has("value") && ax.has("xsdType")){
				OWL2Datatype xsdType = OWL2Datatype.getDatatype(OWL.fullIri(ax.at("xsdType").asString()));
				OWLDataProperty property = OWL.dataFactory().getOWLDataProperty(IRI.create(ax.at("property").asString()));
				OWLLiteral literal = MetaOntology.toLiteral(factory, property, ax.at("value").asString(), xsdType);
				OWLIndividual individual = OWL.individual(IRI.create(ax.at("individual").asString()));
				
				return factory.getOWLDataPropertyAssertionAxiom(property, individual, literal);
				
			}else {
				throw new RuntimeException("Invalid Ontology Change structure.");
			}	
		}
		
		private OWLAxiom buildObjectPropertyAssertionAxiom(Json ax, OWLOntology O, OWLDataFactory factory){
			if (ax.has("property") && ax.has("individual") && ax.has("value")){
				OWLObjectProperty property = OWL.dataFactory().getOWLObjectProperty(IRI.create(ax.at("property").asString()));
				OWLIndividual individual = OWL.individual(IRI.create(ax.at("individual").asString()));
				OWLNamedIndividual object = factory.getOWLNamedIndividual(IRI.create(ax.at("value").asString()));
				
				return factory.getOWLObjectPropertyAssertionAxiom(property, individual, object);
				
			} else {
				throw new RuntimeException("Invalid Ontology Change structure.");
			}	
			
		}
		
		private OWLAxiom buildAnnotationAssertionAxiom(Json ax, OWLOntology O, OWLDataFactory factory){
			if (ax.has("property") && ax.has("individual") && ax.has("value") && ax.has("xsdType")){
				if (ax.at("property").asString() == "rdfs:label"){
					ax.set("property", "http://www.w3.org/2000/01/rdf-schema#label");
				}
				if (ax.at("property").asString() == "rdfs:comment"){
					ax.set("property", "http://www.w3.org/2000/01/rdf-schema#comment");
				}
				
			    return factory.getOWLAnnotationAssertionAxiom(IRI.create(ax.at("individual").asString()), 
														      factory.getOWLAnnotation(OWL.annotationProperty(ax.at("property").asString()), 
														      factory.getOWLLiteral(ax.at("value").asString())));
			} else {
				throw new RuntimeException("Invalid Ontology Change structure.");
			}	
		}
		
		private OWLAxiom buildClassAssertionAxiom(Json ax, OWLOntology O, OWLDataFactory factory){
			if (ax.has("class") && ax.has("individual")){
				OWLIndividual individual = OWL.individual(IRI.create(ax.at("individual").asString()));
				
				return factory.getOWLClassAssertionAxiom(owlClass(IRI.create(ax.at("class").asString())),individual);
				
			} else {
				throw new RuntimeException("Invalid Ontology Change structure.");
			}	
		}
	}

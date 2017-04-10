/*******************************************************************************
 * Copyright 2014 Miami-Dade County
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.sharegov.cirm;

import static org.sharegov.cirm.OWL.owlClass;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import mjson.Json;

import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataRange;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.reasoner.BufferingMode;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.BidirectionalShortFormProviderAdapter;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.util.ShortFormProvider;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.event.ClearOWLEntityCacheForSrTypeModification;
import org.sharegov.cirm.rest.OWLIndividuals;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;


/**
 *  
 * @author chirino, hilpold
 * 
 * Utility wrapper over Meta Ontology operations
 * 
 */
public class MetaOntology 
{

	private static String PREFIX = "legacy:";
	private static volatile ShortFormProvider prefixShortFormProvider; 
	
	
	public static final int RESOLVE_ALL_IRI_MAX_DEPTH = 5;
	
	public static synchronized ShortFormProvider getPrefixShortFormProvider(){
			if (prefixShortFormProvider == null)
			{
				DefaultPrefixManager pm = new DefaultPrefixManager();
				if (StartUp.getConfig().has("ontologyPrefixes"))
					for (Map.Entry<String, Json> e : StartUp.getConfig().at("ontologyPrefixes", Json.object()).asJsonMap().entrySet())
						if (!e.getKey().equals(":"))
							pm.setPrefix(e.getKey(), e.getValue().asString());
				prefixShortFormProvider = pm;
			}
			return prefixShortFormProvider;
	}
	
	public static List<OWLOntologyChange> getReplaceObjectAnnotationChanges (String individualID, String newAnnotationContent){
		OWLOntology O = OWL.ontology();
		//get the individual
		OWLEntity entity = OWL.dataFactory().getOWLNamedIndividual(MetaOntology.fullIri(individualID));
		String existingLabel = OWL.getEntityLabel(entity);
		//create existing annotation
		OWLAnnotationAssertionAxiom toRemove = OWL.dataFactory().getOWLAnnotationAssertionAxiom(
				entity.getIRI(), OWL.dataFactory().getOWLAnnotation(OWL.annotationProperty(fullIri("rdfs:label")), OWL.dataFactory().getOWLLiteral(existingLabel)));
		//create new annotation
		OWLAnnotationAssertionAxiom toAdd = OWL.dataFactory().getOWLAnnotationAssertionAxiom(
				entity.getIRI(), OWL.dataFactory().getOWLAnnotation(OWL.annotationProperty(fullIri("rdfs:label")), OWL.dataFactory().getOWLLiteral(newAnnotationContent)));		
		
		RemoveAxiom removeAxiom = new RemoveAxiom(O, toRemove);			
		AddAxiom addAxiom = new AddAxiom(O, toAdd); 
		
		List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
		changes.add(removeAxiom);
		changes.add(addAxiom);

		return changes;			
	}
		
	public static <Type> OWLLiteral createTypedLiteral (Type value){
		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory factory = manager.getOWLDataFactory();
		
		if (value instanceof Boolean) {
			return factory.getOWLLiteral((Boolean) value);
		} else if (value instanceof String){
			return factory.getOWLLiteral((String) value);
		} else if (value instanceof Integer){
			return factory.getOWLLiteral((Integer) value);
		} else if (value instanceof Float){
			return factory.getOWLLiteral((Float) value);
		} else if (value instanceof Double){
			return factory.getOWLLiteral((Double) value);
		} else {
			throw new RuntimeException("Invalid parameter tyoe: " + value.getClass().toString());
		}
	}

	public static <Type> List<OWLOntologyChange> getReplaceIndividualLiteralPropertyChanges (String individualID, String propertyID, Type newValue) {
		List<OWLOntologyChange> changes = getRemoveIndividualPropertyChanges(individualID, propertyID);
		
		changes.add(getIndividualLiteralAddAxiom (individualID, propertyID, newValue));

		return changes;
	}
	
	public static <Type> AddAxiom getIndividualLiteralAddAxiom (String individualID, String propertyID, Type newValue){
		OWLOntology O = OWL.ontology();
		String ontologyIri = Refs.defaultOntologyIRI.resolve();

		if (O == null) {
			throw new RuntimeException("Ontology not found: " + ontologyIri);
		}

		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory factory = manager.getOWLDataFactory();

		OWLNamedIndividual individual = factory.getOWLNamedIndividual(MetaOntology.fullIri(individualID));
		OWLDataProperty property = factory.getOWLDataProperty(MetaOntology.fullIri(propertyID));
		
		OWLLiteral newLiteralValue = createTypedLiteral(newValue);		 
		
		OWLDataPropertyAssertionAxiom addAssertion = factory.getOWLDataPropertyAssertionAxiom(property, individual, newLiteralValue);
		
		return new AddAxiom(O, addAssertion);
	}
	
	public static List <RemoveAxiom> getIndividualLiteralRemoveAxioms (String individualID, String propertyID){
		OWLOntology O = OWL.ontology();
		String ontologyIri = Refs.defaultOntologyIRI.resolve();
		
		
		if (O == null) {
			throw new RuntimeException("Ontology not found: " + ontologyIri);
		}

		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory factory = manager.getOWLDataFactory();

		OWLNamedIndividual individual = factory.getOWLNamedIndividual(MetaOntology.fullIri(individualID));
		OWLDataProperty property = factory.getOWLDataProperty(MetaOntology.fullIri(propertyID));
		
		Set<OWLLiteral> propValues = OWL.reasoner().getDataPropertyValues(individual, property);
		
		List<RemoveAxiom> result = new ArrayList<RemoveAxiom>();
		
		if (propValues.isEmpty()){
			// Axioms not found 
			return result;
		}						
		
		for (OWLLiteral value : propValues) {
			OWLDataPropertyAssertionAxiom removeAssertion = factory.getOWLDataPropertyAssertionAxiom(property, individual, value);
			result.add(new RemoveAxiom(O, removeAssertion));
		}				
		
		return result;
	}
	
	public static List<OWLOntologyChange> getRemoveIndividualPropertyChanges (String individualID, String propertyID){
		List<RemoveAxiom> axioms = getIndividualLiteralRemoveAxioms (individualID, propertyID);
		
		List<OWLOntologyChange> result = new ArrayList<OWLOntologyChange>();
		
		if (axioms.isEmpty()){
			return result;
		}
		
		for (RemoveAxiom axiom : axioms) {
			result.add(axiom);
		}
		
		return result;
	}
	
	public static List <RemoveAxiom> getIndividualObjectPropertyRemoveAxioms (String individualID, String propertyID){
		OWLOntology O = OWL.ontology();
		String ontologyIri = Refs.defaultOntologyIRI.resolve();

		if (O == null) {
			throw new RuntimeException("Ontology not found: " + ontologyIri);
		}

		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory factory = manager.getOWLDataFactory();

		OWLNamedIndividual individual = factory.getOWLNamedIndividual(MetaOntology.fullIri(individualID));
		OWLObjectPropertyExpression property = factory.getOWLObjectProperty(MetaOntology.fullIri(propertyID));
		
		Set <OWLNamedIndividual> propObjects = OWL.reasoner().getObjectPropertyValues(individual, property).getFlattened();
		
		List<RemoveAxiom> result = new ArrayList<RemoveAxiom>();
		
		if (propObjects.isEmpty()){
			// Axioms not found 
			return result;
		}						
		
		for (OWLNamedIndividual value : propObjects) {
			OWLObjectPropertyAssertionAxiom removeAssertion = factory.getOWLObjectPropertyAssertionAxiom(property, individual, value);
			result.add(new RemoveAxiom(O, removeAssertion));
		}				
		
		return result;
	}
	
	public static List<OWLOntologyChange> getRemoveIndividualObjectPropertyChanges (String individualID, String propertyID){
		List<RemoveAxiom> axioms = getIndividualObjectPropertyRemoveAxioms (individualID, propertyID);
		
		List<OWLOntologyChange> result = new ArrayList<OWLOntologyChange>();
		
		if (axioms.isEmpty()){
			return result;
		}
		
		for (RemoveAxiom axiom : axioms) {
			result.add(axiom);
		}
		
		return result;
	}
	
	public static List<OWLOntologyChange> getRemoveIndividualObjectPropertyChanges (String individualID){
		OWLOntology O = OWL.ontology();
		String ontologyIri = Refs.defaultOntologyIRI.resolve();

		if (O == null) {
			throw new RuntimeException("Ontology not found: " + ontologyIri);
		}
		
		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory factory = manager.getOWLDataFactory();
		OWLIndividual individual = factory.getOWLNamedIndividual(MetaOntology.fullIri(individualID)); 
		
		return getRemoveOnlyPropertiesIndividualChanges (individual);		
	}
	
	public static List<OWLOntologyChange> getRemoveIndividualObjectPropertyReferenceChanges (String parentID, String propertyID, String individualID){
		OWLOntology O = OWL.ontology();
		String ontologyIri = Refs.defaultOntologyIRI.resolve();

		if (O == null) {
			throw new RuntimeException("Ontology not found: " + ontologyIri);
		}
		
		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory factory = manager.getOWLDataFactory();
		
		OWLNamedIndividual newInd = OWL.individual(MetaOntology.fullIri(individualID));		
		OWLIndividual parent = factory.getOWLNamedIndividual(MetaOntology.fullIri(parentID));
		OWLObjectProperty property =  factory.getOWLObjectProperty(MetaOntology.fullIri(propertyID));
		
		List<OWLOntologyChange> result = new ArrayList<OWLOntologyChange>();
		
		result.add(new RemoveAxiom(O, factory.getOWLObjectPropertyAssertionAxiom(property, parent, newInd)));	
		
		return result;
	}
	
	/*
	 * create a list of changes to be commited in order to create a new new individual
	 * 
	 */
	public static List<OWLOntologyChange> getCreateIndividualObjectFromJsonChanges (Json data){
		String ontologyIri = Refs.defaultOntologyIRI.resolve();
		OWLOntology O = OWL.ontology(ontologyIri);		

		if (O == null) {
			throw new RuntimeException("Ontology not found: " + ontologyIri);
		}		
		
		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory factory = manager.getOWLDataFactory();
		
		List<OWLOntologyChange> result = new ArrayList<OWLOntologyChange>();
		
		if (data.isObject()){
			if (!data.has("iri")){
				throw new RuntimeException("root iri not found : " + data.toString());
			}
			String iri = getIdFromUri(data.at("iri").asString());
			String prefix = correctedPrefix (iri);
			
			OWLNamedIndividual newInd = OWL.individual(MetaOntology.fullIri(prefix + iri));
						
			result.addAll(makeObjectIndividual (newInd, data, O, manager, factory));
		}
		
		if (data.isArray()){
			for (Json e: data.asJsonList()){
				if (e.isObject()){
					if (!e.has("iri")){
						throw new IllegalArgumentException("Cannot find iri property for question: "+ e.asString());
					}  
				} else {
					throw new IllegalArgumentException("element is not an object: "+ e.asString());
				}
				
				String iri = getIdFromUri(e.at("iri").asString());
				String prefix = correctedPrefix (iri);
				
				OWLNamedIndividual newInd = OWL.individual(MetaOntology.fullIri(prefix + iri));
							
				result.addAll(makeObjectIndividual (newInd, e, O, manager, factory));
			}
		}
		
		return result;
	}
	
	
	/*
	 * function creates a new named individual using properties described on the json structure and attach it to the parent on property described by propertyID.
	 * 
	 */
	public static List<OWLOntologyChange> getAddIndividualObjectFromJsonChanges (String parentID, String propertyID,  Json data){
		String ontologyIri = Refs.defaultOntologyIRI.resolve();
		OWLOntology O = OWL.ontology(ontologyIri);		

		if (O == null) {
			throw new RuntimeException("Ontology not found: " + ontologyIri);
		}		
		
		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory factory = manager.getOWLDataFactory();
		
		List<OWLOntologyChange> result = new ArrayList<OWLOntologyChange>();
		
		if (data.isObject()){
			if (!data.has("iri")){
				throw new RuntimeException("root iri not found : " + data.toString());
			}
			String iri = getIdFromUri(data.at("iri").asString());
			String prefix = correctedPrefix (iri);
			
			OWLNamedIndividual newInd = OWL.individual(MetaOntology.fullIri(prefix + iri));
						
			result.addAll(makeObjectIndividual (newInd, data, O, manager, factory));
			
			OWLIndividual parent = factory.getOWLNamedIndividual(MetaOntology.fullIri(parentID));
			OWLObjectProperty property =  factory.getOWLObjectProperty(MetaOntology.fullIri(propertyID));
			
			result.add(new AddAxiom(O, factory.getOWLObjectPropertyAssertionAxiom(property, parent, newInd)));
		}
		
		if (data.isArray()){
			for (Json e: data.asJsonList()){
				if (e.isObject()){
					if (!e.has("iri")){
						throw new IllegalArgumentException("Cannot find iri property for question: "+ e.asString());
					}  
				} else {
					throw new IllegalArgumentException("element is not an object: "+ e.asString());
				}
				
				String iri = getIdFromUri(e.at("iri").asString());
				String prefix = correctedPrefix (iri);
				
				OWLNamedIndividual newInd = OWL.individual(MetaOntology.fullIri(prefix + iri));
							
				result.addAll(makeObjectIndividual (newInd, e, O, manager, factory));
				
				OWLIndividual parent = factory.getOWLNamedIndividual(MetaOntology.fullIri(parentID));
				OWLObjectProperty property =  factory.getOWLObjectProperty(MetaOntology.fullIri(propertyID));
				
				result.add(new AddAxiom(O, factory.getOWLObjectPropertyAssertionAxiom(property, parent, newInd)));
			}
		}	
		
		return result;
	}
	
	public static List<OWLOntologyChange> getAddIndividualObjectProperty (String parentID, String propertyID,  String  propertyValue){
		String ontologyIri = Refs.defaultOntologyIRI.resolve();
		OWLOntology O = OWL.ontology(ontologyIri);	
		List<OWLOntologyChange> result = new ArrayList<OWLOntologyChange>();
		result.add(new AddAxiom(O, getObjectPropertyAxiom( O,  parentID,  propertyID,  propertyValue) ));
		return result;
	}
	
	public static List<OWLOntologyChange> getRemoveIndividualObjectProperty (String parentID, String propertyID, String  propertyValue){
		String ontologyIri = Refs.defaultOntologyIRI.resolve();
		OWLOntology O = OWL.ontology(ontologyIri);	
		List<OWLOntologyChange> result = new ArrayList<OWLOntologyChange>();
		result.add(new RemoveAxiom(O, getObjectPropertyAxiom( O,  parentID,  propertyID,  propertyValue) ));
		return result;
	}
	
	public static OWLNamedIndividual getMetaIndividual (String individualID){		
		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory factory = manager.getOWLDataFactory();
		return  factory.getOWLNamedIndividual(MetaOntology.fullIri(individualID)); 
	}

	
	public static OWLAxiom getObjectPropertyAxiom(OWLOntology O, String parentID, String propertyID, String  objectPropertyValue){
		
		String ontologyIri = Refs.defaultOntologyIRI.resolve();
		if (O == null) {
			throw new RuntimeException("Ontology not found: " + ontologyIri);
		}		
		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory factory = manager.getOWLDataFactory();
		OWLIndividual existingInd = factory.getOWLNamedIndividual(MetaOntology.fullIri(objectPropertyValue)); 
		OWLIndividual parent = factory.getOWLNamedIndividual(MetaOntology.fullIri(parentID));
		OWLObjectProperty property =  factory.getOWLObjectProperty(MetaOntology.fullIri(propertyID));
		return factory.getOWLObjectPropertyAssertionAxiom(property, parent, existingInd);
	}
	
	/*
	 * function creates a new named idividual using properties described on the newData json structure and replace it to the parent on property described by propertyID by removing the object represented by oldData.
	 * 
	 */
	public static List<OWLOntologyChange> getAddReplaceIndividualObjectPropertyFromJsonChanges (String parentID, String propertyID,  Json newData, Json oldData){
		OWLOntology O = OWL.ontology();
		String ontologyIri = Refs.defaultOntologyIRI.resolve();

		if (O == null) {
			throw new RuntimeException("Ontology not found: " + ontologyIri);
		}
		
		if (!newData.has("iri")){
			throw new RuntimeException("root iri not found : " + newData.toString());
		}

		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory factory = manager.getOWLDataFactory();
		OWLIndividual newInd = factory.getOWLNamedIndividual(MetaOntology.fullIri(PREFIX + newData.at("iri").asString())); 
		OWLIndividual oldInd = factory.getOWLNamedIndividual(MetaOntology.fullIri(PREFIX + oldData.at("iri").asString())); 
		
		List<OWLOntologyChange> result = getRemoveAllPropertiesIndividualChanges (oldInd);
		
		result.addAll(makeObjectIndividual (newInd, newData, O, manager, factory));
		
		OWLIndividual parent = factory.getOWLNamedIndividual(fullIri(PREFIX + parentID));
		OWLObjectProperty property =  factory.getOWLObjectProperty(fullIri(PREFIX + propertyID));
		
		result.add(new AddAxiom(O, factory.getOWLObjectPropertyAssertionAxiom(property, parent, newInd)));
		
		return result;
	}
	
	private static boolean isIdenticalOppositeAxion (OWLOntologyChange a, OWLOntologyChange b){
		return (a.getAxiom().equals(b.getAxiom()) && a.getClass() != b.getClass());			
	}
	
	private static boolean isIdenticalAxion (OWLOntologyChange a, OWLOntologyChange b){
		return (a.getAxiom().equals(b.getAxiom()) && a.getClass() == b.getClass());			
	}
	
	public static List<OWLOntologyChange> clearChanges (List<OWLOntologyChange> changes){
		// Mark duplicates
		List<Integer> toRemove = new ArrayList<>();
		
		int lim = changes.size();
		for (int i=0; i<lim; i++){
			if (!toRemove.contains(i)){
				for (int j=i+1;j<lim; j++){
					if (!toRemove.contains(j)){
						if (isIdenticalAxion(changes.get(i), changes.get(j))){
							toRemove.add(j);
						}
					}
				}
			}			
		}
		
		// Filter the results
		for (int i=lim-1; i>-1; i--){
			if (toRemove.contains(i)){
				changes.remove(i);
			}
		}
		

		lim = changes.size();
		toRemove.clear();
		for (int i=0; i<lim; i++){
			if (!toRemove.contains(i)){
				for (int j=0;j<lim; j++)
					if (i!=j){
						if (!toRemove.contains(j)){
							if (isIdenticalOppositeAxion(changes.get(i), changes.get(j))){
							toRemove.add(i);
							toRemove.add(j);
							continue;
						}
					}
				}
			}			
		}
		
		for (int i=lim-1; i>-1; i--){
			if (toRemove.contains(i)){
				changes.remove(i);
			}
		}
		
		lim = changes.size();
		toRemove.clear();
		for (int i=0; i<lim; i++){
			OWLOntologyChange changex = changes.get(i);
			if (changex.getClass().toString().contains("Add")){
				for (OWLOntology o : OWL.ontologies()) {
					if (o.containsAxiom(changex.getAxiom())) {
						toRemove.add(i);
					}
				}
			}
		}
		
		for (int i=lim-1; i>-1; i--){
			if (toRemove.contains(i)){
				changes.remove(i);
			}
		}
		
		return changes;
	}
	
	public static List<OWLOntologyChange> getRemoveAllPropertiesIndividualChanges (OWLIndividual individual){
		List<OWLOntologyChange> L = new ArrayList<OWLOntologyChange>();
		
		for (OWLOntology O: OWL.ontologies()){		
			for (OWLAxiom a : O.getDeclarationAxioms((OWLEntity)individual)) L.add(new RemoveAxiom(O, a));
			for (OWLAxiom a : O.getReferencingAxioms((OWLEntity)individual)) L.add(new RemoveAxiom(O, a));			
			for (OWLAxiom a : O.getDataPropertyAssertionAxioms(individual)) L.add(new RemoveAxiom(O, a));
			for (OWLAxiom a : O.getObjectPropertyAssertionAxioms(individual)) L.add(new RemoveAxiom(O, a));
			for (OWLAxiom a : O.getAnnotationAssertionAxioms(((OWLEntity) individual).getIRI())) L.add(new RemoveAxiom(O, a));
			for (OWLAxiom a : O.getClassAssertionAxioms(individual)) L.add(new RemoveAxiom(O, a));
		}
		
		return L;
	}
	
	public static List<OWLOntologyChange> getRemoveOnlyPropertiesIndividualChanges (OWLIndividual individual){
		List<OWLOntologyChange> L = new ArrayList<OWLOntologyChange>();
		
		for (OWLOntology O: OWL.ontologies()){				
			for (OWLAxiom a : O.getDeclarationAxioms((OWLEntity)individual)) L.add(new RemoveAxiom(O, a));	
			for (OWLAxiom a : O.getDataPropertyAssertionAxioms(individual)) L.add(new RemoveAxiom(O, a));
			for (OWLAxiom a : O.getObjectPropertyAssertionAxioms(individual)) L.add(new RemoveAxiom(O, a));
			for (OWLAxiom a : O.getAnnotationAssertionAxioms(((OWLEntity) individual).getIRI())) L.add(new RemoveAxiom(O, a));
			for (OWLAxiom a : O.getClassAssertionAxioms(individual)) L.add(new RemoveAxiom(O, a));
		}
		
		return L;
	}
	
	protected static PropertyDescriptor findPropertyIri(String iriFragment) {
		PropertyDescriptor result = new PropertyDescriptor();
		Json prefixes = Json.array().add("legacy:").add("mdc:").add("legacy:");
		for (Json prefix : prefixes.asJsonList()) {
			IRI propIri = fullIri(prefix.asString() + iriFragment);
			OWLOntology o = OWL.ontology();
			if (o.containsObjectPropertyInSignature(propIri, true)) {
				result.setIri(propIri);
				result.setType(PropertyType.OBJECT);
				return result;
			} else if (o.containsDataPropertyInSignature(propIri, true)) {
				result.setIri(propIri);
				result.setType(PropertyType.DATA);
				return result;
			} else if (o.containsAnnotationPropertyInSignature(propIri, true)) {
				result.setIri(propIri);
				result.setType(PropertyType.ANNOTATION);
				return result;
			} // else try other prefix -
			// FIX
		}
		return null;
	}
	
	protected static String getClassPrefix (String iriFragment){
		Json prefixes = Json.array().add("mdc:").add(":").add("legacy:");
		for (Json prefix : prefixes.asJsonList()) {
			IRI propIri = fullIri(prefix.asString() + iriFragment);
			for (OWLOntology o : OWL.ontologies()) {
				if (o.containsClassInSignature(propIri)) {
					return prefix.asString();
				}
			}
		}
		return null;
	}
	
	protected static String detectIndividualPrefix (String iriFragment){
		Json prefixes = Json.array().add("mdc:").add(":").add("legacy:");
		for (Json prefix : prefixes.asJsonList()) {
			IRI propIri = fullIri(prefix.asString() + iriFragment);
			for (OWLOntology o : OWL.ontologies()) {
				if (o.containsIndividualInSignature(propIri)) {
					return prefix.asString();
				}
			}
		}
		return null;
	}
	
	protected static String correctedPrefix (String iriFragment){
		String detected = detectIndividualPrefix(iriFragment);
		
		if (detected == null){
			return "legacy:";
		} else {
			return detected;
		}
		
	}
	
	protected static void handleClassesArray (Json result, Json e){
		if (!result.isArray()) result = Json.array();
		
		if (e.isArray()){
			for (Json v: e.asJsonList()){	
				handleClassesArray(result, v);
			}
		}
		else if (e.isString()){
			result.add(e.asString());
		}
	}
	
	public static List<OWLOntologyChange> addNewClassAssertion (OWLIndividual parent, Json e, OWLOntology O, OWLDataFactory factory){
		List<OWLOntologyChange> result = new ArrayList<OWLOntologyChange>();
		
		Json classes = Json.array();
		
		handleClassesArray (classes, e);
		
		for (Json v: classes.asJsonList()){		
			String classPrefix = getClassPrefix(v.asString());
			
			if (classPrefix == null) throw new RuntimeException("Undeclared OWL class: " + v.asString());
			
			result.add(new AddAxiom(O, factory.getOWLClassAssertionAxiom(owlClass(fullIri(classPrefix + v.asString())),parent)));
		}
		
		return result;
	}
	
	/*
	 * Refactored code coming from BOntology class 
	 * 
	 */	
	
	protected static List<OWLOntologyChange> makeObjectIndividual (OWLIndividual parent, Json properties, OWLOntology O, OWLOntologyManager manager, OWLDataFactory factory){
		List<OWLOntologyChange> result = new ArrayList<OWLOntologyChange>();
		
		if (properties.asJsonMap().containsKey("reference")) {
			return result;
		}
		
		result.addAll(getRemoveOnlyPropertiesIndividualChanges(parent));			
		
		for (Map.Entry<String, Json> e : properties.asJsonMap().entrySet())
		{
			String key = e.getKey();
			
			Json value = e.getValue();
			if (key.equals("label") || key.equals("iri") || key.equals("type") || key.equals("extendedTypes") || key.equals("transient$protected") || key.equals("comment"))
			{
				if (key.equals("type") || key.equals("extendedTypes")){
					result.addAll(addNewClassAssertion (parent, e.getValue(), O, factory));
					
				} else if (key.equals("label")){
					result.add(new  AddAxiom(O,factory.getOWLAnnotationAssertionAxiom(((OWLEntity) parent).getIRI(), 
																					  factory.getOWLAnnotation(OWL.annotationProperty("http://www.w3.org/2000/01/rdf-schema#label"), 
																				      factory.getOWLLiteral(value.asString())))));
				} else if (key.equals("comment")){
					result.add(new  AddAxiom(O,factory.getOWLAnnotationAssertionAxiom(((OWLEntity) parent).getIRI(),
                            factory.getOWLAnnotation(OWL.annotationProperty("http://www.w3.org/2000/01/rdf-schema#comment"),
                            factory.getOWLLiteral(value.asString())))));
				}
				continue;
			}
			
			PropertyDescriptor pd = findPropertyIri(key);
			
			if (pd == null) throw new RuntimeException("Unknown OWL property or annotation for key: " + key);
			
			if (pd.getType() == PropertyType.UNKNOWN) throw new RuntimeException("Undeclared OWL property or annotation: " + pd.getIri());
			
			IRI propIri = pd.getIri();
			if (pd.getType() == PropertyType.OBJECT)
			{
				if (value.isArray())
				{
					for (int i = 0; i < e.getValue().asList().size(); i++)
					{
						result.addAll(addObjectProperty(parent, OWL.dataFactory().getOWLObjectProperty(propIri), value.at(i), O, manager, factory));
					}
				}
				else
					result.addAll(addObjectProperty(parent, OWL.dataFactory().getOWLObjectProperty(propIri), value, O, manager, factory));
			}
			else if (pd.getType() == PropertyType.DATA)
			{
				if (value.isArray())
				{
					for (int i = 0; i < e.getValue().asList().size(); i++)
					{
						result.addAll(addDataProperty(parent, OWL.dataFactory().getOWLDataProperty(propIri), value.at(i), O, manager, factory));
					}
				}
				else
					result.addAll(addDataProperty(parent, OWL.dataFactory().getOWLDataProperty(propIri), value, O, manager, factory));
			}
			else if (pd.getType() == PropertyType.ANNOTATION){
				// TO-DO
			}
				
		}
		return result;
	}
	
	protected static List<OWLOntologyChange> addObjectProperty(OWLIndividual ind, OWLObjectProperty prop, Json value, OWLOntology O, OWLOntologyManager manager, OWLDataFactory factory)
	{
		List<OWLOntologyChange> result = new ArrayList<OWLOntologyChange>();
		
		if (value.isObject())
		{			
			if (value.has("iri")){
				String iri = value.at("iri").asString();
				if (iri.contains("#")){
					iri = getIdFromUri(iri);
				}
				String prefix = correctedPrefix(iri);
				OWLNamedIndividual object = factory.getOWLNamedIndividual(fullIri(prefix + iri));
				result.add(new AddAxiom(O, factory.getOWLObjectPropertyAssertionAxiom(prop, ind, object)));
				result.addAll(setPropertiesFor(object, value, O, manager, factory));
			}
			else
			{				
				throw new RuntimeException("Missing iri on Object Property: " + value.asString());
			}
		}
		else{
			String iri = value.asString();
			if (iri.contains("#")){
				iri = getIdFromUri(iri);
			}
			String prefix = correctedPrefix(iri);
			OWLNamedIndividual object = factory.getOWLNamedIndividual(fullIri(prefix + iri));			
			result.add(new AddAxiom(O, factory.getOWLObjectPropertyAssertionAxiom(prop, ind, object)));
		}
		
		return result;
	}
	
	protected static List<OWLOntologyChange> setPropertiesFor(OWLNamedIndividual individual, Json properties, OWLOntology O, OWLOntologyManager manager, OWLDataFactory factory)
	{
		// Remove all data and object properties currently declared on the ontology.
		List<OWLOntologyChange> L = new ArrayList<OWLOntologyChange>();
//		L.addAll(getRemoveOnlyPropertiesIndividualChanges(individual));		
		L.addAll(makeObjectIndividual(individual, properties, O, manager, factory));
		
		return L;
	}
	
	protected static OWL2Datatype getDataPropertyType (OWLIndividual individual, OWLDataProperty property){
		for (OWLOntology O: OWL.ontologies()){					
			for (OWLAxiom a : O.getDataPropertyAssertionAxioms(individual)) {
				Set <OWLDataProperty> p = a.getDataPropertiesInSignature();
				if (p.contains(property)){
					Set <OWLDatatype> s = a.getDatatypesInSignature();
					for (OWLDatatype type: s){
						return type.getBuiltInDatatype();
					}
				}
			}
		}
		
		return null;
	}
	
	protected static List<OWLOntologyChange> addDataProperty(OWLIndividual ind, OWLDataProperty prop, Json value, OWLOntology O, OWLOntologyManager manager, OWLDataFactory factory)
	{
		List<OWLOntologyChange> result = new ArrayList<OWLOntologyChange>();
		OWLLiteral literal;
		String valueStr;
		OWL2Datatype xsdType;
		if (value.isObject())
		{
			String typeStr = value.at("type").asString();
			if (value.at("literal").isArray())
				throw new RuntimeException(
						"The value of the dataProperty cannot be an Array, individual is: "
								+ ind.asOWLNamedIndividual().getIRI()
								+ " property is: " + prop.getIRI()
								+ " value is: " + value.at("literal"));
			valueStr = value.at("literal").asString();
			xsdType = OWL2Datatype.getDatatype(OWL.fullIri(PREFIX + typeStr));// IRI.create(typeStr));
			if (xsdType == null)
				throw new IllegalArgumentException(
						"Unable to read type for Json value." + value);
		}
		else
		{
			xsdType = getDataPropertyType(ind, prop);
			if (!value.isNull())
			{
				//TODO: add asString() in BooleanJson function and remove this if condition
				if(value.isBoolean())
					valueStr = value.toString();
				else
					valueStr = value.asString();
			}
			else
			{
				valueStr = null;
			}
		}
		if (valueStr == null || valueStr.isEmpty())
		{
			System.err.println("Empty or null string JSON detected. No BO axiom created. Individual was: "
							+ ind + " dataproperty was: " + prop);
			return result;
		}
		
		literal = toLiteral(factory, prop, valueStr, xsdType);
		if (literal == null)
			literal = factory.getOWLLiteral(valueStr);
		
		OWLAxiom axiom = factory.getOWLDataPropertyAssertionAxiom(prop, ind, literal);
		result.add(new AddAxiom(O, axiom));
		
		return result;
	}
	
	private static OWLLiteral toLiteral(OWLDataFactory factory, OWLDataProperty prop, String value, OWL2Datatype builtinDatatype)
	{		
		// Parse out if the value is an ISO date and convert it to the format XML datafactory accepts.
		if (builtinDatatype == null || builtinDatatype.equals(OWL2Datatype.XSD_DATE_TIME_STAMP))
		{
			try  { return dateLiteral(factory, GenUtils.parseDate(value), OWL2Datatype.XSD_DATE_TIME_STAMP); }
			catch (Throwable t) { }
		}
		
		//TODO we could validate here, if the value string matches the builtinDatatype.
		if (builtinDatatype != null){
			return factory.getOWLLiteral(value, builtinDatatype);
		}
		
		Set<OWLDataRange> ranges = prop.getRanges(OWL.ontologies());
		for (OWLDataRange range : ranges)
		{
			if (range instanceof OWLDatatype) 
			{
					return factory.getOWLLiteral(value, (OWLDatatype)range);
			}
		}
		return null;
	}
	
	private static OWLLiteral dateLiteral(OWLDataFactory factory, Date date, OWL2Datatype d)
	{
		OWLLiteral result = factory.getOWLLiteral("", d);
		if(date == null)
			return result;
		try
		{
			//see:
			//http://download.oracle.com/javase/6/docs/api/javax/xml/datatype/XMLGregorianCalendar.html#getXMLSchemaType()
			Calendar c = Calendar.getInstance();
			c.setTime(date);
			XMLGregorianCalendar x = DatatypeFactory.newInstance().newXMLGregorianCalendar();
			if(c instanceof GregorianCalendar)
				if(DatatypeConstants.DATE.getNamespaceURI().equals(d.getIRI().toString()))
				{
					x.setYear(c.get(Calendar.YEAR));
					x.setMonth(c.get(Calendar.MONTH));
					x.setDay(c.get(Calendar.DAY_OF_MONTH));
					result = factory.getOWLLiteral(x.toXMLFormat(),d);
				}
				else
				   result = factory.getOWLLiteral(DatatypeFactory.newInstance().newXMLGregorianCalendar((GregorianCalendar)c).toXMLFormat(), d);
				
		}
		catch(Exception e)
		{
			String msg = "Exception occured while attempting to extract a xsd date value";
			throw new RuntimeException(msg);
		}
		return result;
	}
	
	/*
	 * Utility functions
	 * 
	 */	
	
	public static String getOntologyFromUri(String uri){		
		return uri.substring(uri.lastIndexOf("/")+1,uri.indexOf("#"));
	}
	
	public static String getOntologyFromIdentifier(String uri){		
		return uri.replace("http:", "").substring(uri.lastIndexOf("/")+1,uri.indexOf(":"));
	}
	
	public static String getIndividualOntologyPrefix(String id){
		if (id.replace("http:", "").contains(":")) return getOntologyFromIdentifier(id);
		else if (id.contains("#")) return getOntologyFromUri(id);
		
		return id;
	}
		
	public static String getIdFromUri(String uri)
	{
		return uri.substring(uri.indexOf("#")+1, uri.length());
	}
	
	public static String getIdFromIdentifier(String uri)
	{
		return uri.replace("http:", "").substring(uri.indexOf(":")+1, uri.length());
	}
		
	public static String getIndividualIdentifier(String id){
		if (id.replace("http:", "").contains(":")) return getIdFromIdentifier(id);
		else if (id.contains("#")) return getIdFromUri(id);
		
		return id;
	}
	
	/**
	 * Clears caches, synchronizes the reasoner and always tests reasoner consistency.
	 * Synchronization will be conducted only, if the reasoner is in buffering mode.
	 * 
	 * 
	 * TODO hilpold find the right class for this method.
	 * 
	 */	
	public static void clearCacheAndSynchronizeReasoner() {
		synchronized (OWL.reasoner()) {
			ThreadLocalStopwatch.startTop("START clearCache");
			clearCache();
			ThreadLocalStopwatch.now("END clearCache");
			ThreadLocalStopwatch.now("START syncReasoner");
			synchronizeReasoner();
			ThreadLocalStopwatch.now("END syncReasoner");
		}		
	}
	
	/**
	 * Clears all underlying cache.
	 */
	public static void clearCache() {
		synchronized (OWL.reasoner()) {
			ClearOWLEntityCacheForSrTypeModification c = new ClearOWLEntityCacheForSrTypeModification();
			c.apply(null, null, null);
		}		
	}
	
	/**
	 * Synchronizes the reasoner.
	 * Applies all changes since reasoner initialization to the reasoner.
	 */
	public static void synchronizeReasoner() {
		synchronized (OWL.reasoner()) {
			OWLReasoner reasoner = OWL.reasoner();
			if (reasoner.getBufferingMode().equals(BufferingMode.BUFFERING)) {
				reasoner.flush();
			}
			if (!reasoner.isConsistent()) throw new IllegalStateException("Reasoner is not consistent. Axioms leading to inconsistency must have been applied since server start or the last call of this method.");
		}
		
	}
		
	/**
	 * Simple method to check if a Json is a fullIRI.
	 * TODO This needs improvement.
	 * @param iriCandidate
	 * @return
	 */
	public static boolean isFullIriString(Json iriCandidate) {
		return iriCandidate.isString() 
				&& iriCandidate.asString().contains("#")
			    && iriCandidate.asString().startsWith("http://");
	}
	
	public static boolean isTargetObject (Json candidate, String targetOntology){
		if (candidate.isArray()) return true;
		
		Json iriCandidate = null;
		
		if (candidate.isObject() && candidate.has("iri")) {
			iriCandidate = candidate.at("iri");
		} else {
			iriCandidate = candidate;
		}
		
		return isFullIriString(iriCandidate) && iriCandidate.isString()	&& iriCandidate.asString().contains(targetOntology);
	}
		
	public static Json getSerializedOntologyObject (String fullIRI){			
		String individualID = getIndividualIdentifier(fullIRI);
		String individualOntology = correctedPrefix(individualID);
		OWLIndividuals q = new OWLIndividuals();
		Json S = Json.nil();
		
		try {
			S = q.serialize(q.doQuery("{" + individualOntology + individualID + "}"), getPrefixShortFormProvider());			
			
		} catch (Exception e) {
			System.out.println("Error while querying the Ontology for "+ individualOntology + individualID);
			System.out.println("Querying individual's endpoint instead...");
			try {
				
				S = q.getOWLIndividualByName(individualOntology + individualID);		
				System.out.println("Resolved: "+ individualOntology + individualID);
				
			} catch (Exception ex){
				System.out.println("Unable to resolve Object: " + individualOntology + individualID);
				ex.printStackTrace();
			}					
		}
		
		if (!S.isNull()){
			if (S.isArray()){
				for (Json ind: S.asJsonList()){
					return ind;
				}
			} else{
				return S;
			}
		} 
		
		return S;
	}

	public static Json resolveIRIs(Json j, String targetOntology){
		if (targetOntology == null) targetOntology = "#";
		
		Map<String, Json> objectMap = new ConcurrentHashMap<String, Json>();
		Map<String, Boolean> resolutionMap  = new ConcurrentHashMap<String, Boolean>();
		
		Json result = j.dup();
		
		mapJsonObject(result, objectMap, targetOntology, 1);
		
		resolveEmptyObjects(objectMap, targetOntology);
		
		String error = "";
		
		if (!checkAllObjects(objectMap, error, targetOntology)){
			throw new IllegalArgumentException("Unable to resolve object: " + error);
		}
				
		expandJson(result, objectMap, resolutionMap, targetOntology, 1);
		
		return result;
	}
	
	private static void mapJsonObject(Json j, Map<String, Json> map, String targetOntology, int depth){
		if (j.isObject()) {
			Map<String,Json> properties = j.asJsonMap();
			for (Map.Entry<String, Json> propKeyValue : properties.entrySet()) {
				Json value = propKeyValue.getValue();
				
				if (propKeyValue.getKey().equals("iri")){	
					if (!map.containsKey(value.asString()) || (map.containsKey(value.asString()) && map.get(value.asString()) == Json.nil())){
						map.put(value.asString(), j.dup());
					}
				} else if (isFullIriString(value)) {
					if (!map.containsKey(value.asString())){
						if (isTargetObject(value, targetOntology)) {
							map.put(value.asString(), Json.nil());
						} else {
							map.put(value.asString(), value);
						}
					}
				} 
				if (isTargetObject(value, targetOntology)) {
					mapJsonObject(value, map, targetOntology, depth+1);
				}
				
			}
		} else if (j.isArray()) {
			ListIterator<Json> arrayIt = j.asJsonList().listIterator();
			while(arrayIt.hasNext()) {
				Json elem = arrayIt.next();
				if (isFullIriString(elem)) {
					if (!map.containsKey(elem.asString())){
						if (isTargetObject(elem, targetOntology)) {
							map.put(elem.asString(), Json.nil());
						} else {
							map.put(elem.asString(), elem);
						}
					}
				} 
				if (isTargetObject(elem, targetOntology)) {
					mapJsonObject(elem, map, targetOntology, depth+1);
				}
			}
		} else {
			// nothing to do for primitives
		}  	
	}
	
	private static void resolveEmptyObjects(Map<String, Json> map, String targetOntology){
		boolean newObjects;
		do {
			newObjects = false;
			for (Map.Entry<String, Json> propKeyValue : map.entrySet()) {
				if (propKeyValue.getValue().isNull()){
					Json value = getSerializedOntologyObject(propKeyValue.getKey());
					if (value.isNull()){
						throw new IllegalArgumentException("Unable to serialize invividual legacy: " + propKeyValue.getKey());
					}
					propKeyValue.setValue(value);
					if (isTargetObject(value, targetOntology)) {
						mapJsonObject(propKeyValue.getValue(), map, targetOntology, 1);
						newObjects = true;
					}
				}
			}
		}while(newObjects);
	}
	
	private static boolean checkAllObjects(Map<String, Json> map, String failedIRI, String targetOntology){
		for (Map.Entry<String, Json> propKeyValue : map.entrySet()) {
			if (isTargetObject(Json.object().set("iri", propKeyValue.getKey()), targetOntology))
			if (propKeyValue.getValue().isNull()||!propKeyValue.getValue().isObject()){
				failedIRI = propKeyValue.getKey();
				return false;
			}
		}
		return true;
	}
	
	private static void expandJson(Json j, Map<String, Json> objectMap, Map<String, Boolean> resolutionMap, String targetOntology, int depth){	
		if (j.isObject() && isTargetObject(j, targetOntology)) {
			if (!j.has("iri")){ 
				throw new IllegalArgumentException("Object missing IRI property: " + j.asString());
			}
			if (resolutionMap.containsKey(j.at("iri").asString())){
				if (resolutionMap.get(j.at("iri").asString())){
//					would like to empty j before adding the extended object. 
//					the line bellow clears the object but null point exception is triggered when trying to use the object after.
//					j.asJsonMap().clear();
					j.with(objectMap.get(j.at("iri").asString()).dup());
					return;
				} else {
					j.set("reference", true).set("type", "ObjectReference");
					//System.out.println("Recursive Definition detected for individual: " + j.at("iri").asString());
					return;
				}
			} else {
				resolutionMap.put(j.at("iri").asString(), false);
			}
			Map<String,Json> properties = j.asJsonMap();
			for (Map.Entry<String, Json> propKeyValue : properties.entrySet()) {
				if (!propKeyValue.getKey().equals("iri")&&isFullIriString(propKeyValue.getValue())&&isTargetObject(propKeyValue.getValue(), targetOntology)) {
					if (!isObjectOnMap(propKeyValue.getValue().asString(), objectMap)){
						throw new IllegalArgumentException("Object missing on the map: " + propKeyValue.getValue().asString());
					}
					propKeyValue.setValue(objectMap.get(propKeyValue.getValue().asString()).dup());
				} 
				expandJson(propKeyValue.getValue(), objectMap, resolutionMap, targetOntology, depth+1);				
			}
			objectMap.put(j.at("iri").asString(), j.dup());
			resolutionMap.put(j.at("iri").asString(), true);
		} else if (j.isArray()) {
			ListIterator<Json> arrayIt = j.asJsonList().listIterator();
			while(arrayIt.hasNext()) {
				Json elem = arrayIt.next();
				if (isFullIriString(elem)&&isTargetObject(elem, targetOntology)) {
					if (!isObjectOnMap(elem.asString(), objectMap)){
						throw new IllegalArgumentException("Object missing on the map: " + elem.asString());
					}
					elem = objectMap.get(elem.asString()).dup();
				}
				expandJson(elem, objectMap, resolutionMap, targetOntology, depth+1); 
				arrayIt.set(elem);
			}
		} else {
			// nothing to do for primitives
		}  	
	}
	
	private static boolean isObjectOnMap(String objectIRI, Map<String, Json> map){
		return map.containsKey(objectIRI);
	}
	
	protected static boolean individualExists (String individualID){
		Json prefixes = Json.array().add("mdc:").add(":").add("legacy:");
		for (Json prefix : prefixes.asJsonList()) {
			IRI propIri = fullIri(prefix.asString() + individualID);
			for (OWLOntology o : OWL.ontologies()) {
				if (o.containsIndividualInSignature(propIri)) {
					return true;
				}
			}
		}
		return false;
	}
	
	protected static Json expandRealtedObjects (String id, Json obj, List<String> expanded){
		if (obj.isArray()) {
			return expandArray(id, obj, expanded);
		} else if (obj.isObject()){
			return expandObject(id, obj, expanded);
		}
		
		return obj;
	}
	
	private static Json expandArray(String id, Json arr, List<String> expanded){
		ListIterator<Json> arrayIt = arr.asJsonList().listIterator();
		while(arrayIt.hasNext()) {
			Json elem = arrayIt.next();
			if (isFullIriString(elem) && elem.asString().contains("#" + id)) {
				elem = getSerializedOntologyObject(elem.asString());
			}
			elem = expandRealtedObjects(id, elem, expanded); 
			arrayIt.set(elem);
		}
		return arr;
	}
	
	private static Json expandObject(String id, Json obj, List<String> expanded){
		if (obj.has("iri")){
			if (expanded.contains(obj.at("iri").asString())){
				return obj;
			} else {
				expanded.add(obj.at("iri").asString());
			}
		} else {
			throw new IllegalArgumentException("Object missing on the iri property: ");
		}
		
		Map<String,Json> properties = obj.asJsonMap();
		for (Map.Entry<String, Json> propKeyValue : properties.entrySet()) {
			if (!propKeyValue.getKey().equals("iri") && isFullIriString(propKeyValue.getValue()) && propKeyValue.getValue().asString().contains("#" + id)) {
				propKeyValue.setValue(getSerializedOntologyObject(propKeyValue.getValue().asString()));
			} 
			propKeyValue.setValue(expandRealtedObjects(id, propKeyValue.getValue(), expanded));				
		}
		
		expanded.remove(obj.at("iri").asString());
		
		return obj;
	}
	
	public static Json cloneSerializedIndividual(String donor, String dolly){
		if (individualExists(dolly)) {
			throw new IllegalArgumentException(dolly + " is already present on the Ontology.");
		}
		

		List<String> expanded = new CopyOnWriteArrayList<>();
		String donorPrefix = correctedPrefix(donor);
		Json serializedDonor = expandRealtedObjects (donor, getSerializedOntologyObject(donorPrefix + donor), expanded);
		String strDonor = serializedDonor.toString();
		
		strDonor = strDonor.replaceAll(donor, dolly);
			        
        return Json.read(strDonor);              
	}
		
	
	public static IRI fullIri (String prefixedForm){
		BidirectionalShortFormProviderAdapter adapter = new BidirectionalShortFormProviderAdapter(getPrefixShortFormProvider());
		return adapter.getEntity(prefixedForm).getIRI();
	}
}

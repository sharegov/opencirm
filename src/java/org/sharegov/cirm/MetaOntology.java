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

import static org.sharegov.cirm.OWL.fullIri;
import static org.sharegov.cirm.OWL.owlClass;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

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
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.event.ClearOWLEntityCache;
import org.sharegov.cirm.event.ClearOWLEntityCacheForSrTypeModification;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import com.clarkparsia.sparqlowl.parser.antlr.SparqlOwlParser.booleanLiteral_return;


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
	public static final int RESOLVE_ALL_IRI_MAX_DEPTH = 5;
	/*
	 * Generic Ontology handling functions.
	 */
	
	public static void setPrefix(String p){
		PREFIX = p;
	}
	
	public static List<OWLOntologyChange> getReplaceObjectAnnotationChanges (String individualID, String newAnnotationContent){
		OWLOntology O = OWL.ontology();
		//get the individual
		OWLEntity entity = OWL.dataFactory().getOWLNamedIndividual(OWL.fullIri(PREFIX + individualID));
		String existingLabel = OWL.getEntityLabel(entity);
		//create existing annotation
		OWLAnnotationAssertionAxiom toRemove = OWL.dataFactory().getOWLAnnotationAssertionAxiom(
				entity.getIRI(), OWL.dataFactory().getOWLAnnotation(OWL.annotationProperty("http://www.w3.org/2000/01/rdf-schema#label"), OWL.dataFactory().getOWLLiteral(existingLabel)));
		//create new annotation
		OWLAnnotationAssertionAxiom toAdd = OWL.dataFactory().getOWLAnnotationAssertionAxiom(
				entity.getIRI(), OWL.dataFactory().getOWLAnnotation(OWL.annotationProperty("http://www.w3.org/2000/01/rdf-schema#label"), OWL.dataFactory().getOWLLiteral(newAnnotationContent)));		
		
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

		OWLNamedIndividual individual = factory.getOWLNamedIndividual(OWL.fullIri(PREFIX + individualID));
		OWLDataProperty property = factory.getOWLDataProperty(OWL.fullIri(PREFIX + propertyID));
		
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

		OWLNamedIndividual individual = factory.getOWLNamedIndividual(OWL.fullIri(PREFIX + individualID));
		OWLDataProperty property = factory.getOWLDataProperty(OWL.fullIri(PREFIX + propertyID));
		
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

		OWLNamedIndividual individual = factory.getOWLNamedIndividual(OWL.fullIri(PREFIX + individualID));
		OWLObjectPropertyExpression property = factory.getOWLObjectProperty(OWL.fullIri(PREFIX + propertyID));
		
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
		OWLIndividual individual = factory.getOWLNamedIndividual(fullIri(PREFIX + individualID)); 
		
		return getRemoveAllPropertiesIndividualChanges (individual);		
	}
	
	/*
	 * function creates a new named idividual using properties described on the json structure and attach it to the parent on property described by propertyID.
	 * 
	 */
	public static List<OWLOntologyChange> getAddIndividualObjectFromJsonChanges (String parentID, String propertyID,  Json data){
		OWLOntology O = OWL.ontology();
		String ontologyIri = Refs.defaultOntologyIRI.resolve();

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
			OWLIndividual newInd = factory.getOWLNamedIndividual(fullIri(PREFIX + data.at("iri").asString())); 
						
			result.addAll(makeObjectIndividual (newInd, data, O, manager, factory));
			
			OWLIndividual parent = factory.getOWLNamedIndividual(fullIri(PREFIX + parentID));
			OWLObjectProperty property =  factory.getOWLObjectProperty(fullIri(PREFIX + propertyID));
			
			result.add(new AddAxiom(O, factory.getOWLObjectPropertyAssertionAxiom(property, parent, newInd)));
		}
		
		if (data.isArray()){
			for (Json e: data.asJsonList()){
				String iri = "";
				if (e.isObject())
					if (e.has("iri")){
						iri = e.at("iri").asString();
					} else throw new IllegalArgumentException("Cannot find iri property for question: "+ e.asString());
				else throw new IllegalArgumentException("element is not an object: "+ e.asString());
				
				iri = getIdFromUri(iri);
				
				OWLNamedIndividual newInd = OWL.individual(PREFIX + iri);
							
				result.addAll(makeObjectIndividual (newInd, e, O, manager, factory));
				
				OWLIndividual parent = factory.getOWLNamedIndividual(fullIri(PREFIX + parentID));
				OWLObjectProperty property =  factory.getOWLObjectProperty(fullIri(PREFIX + propertyID));
				
				result.add(new AddAxiom(O, factory.getOWLObjectPropertyAssertionAxiom(property, parent, newInd)));
			}
		}	
		
		return result;
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
		OWLIndividual newInd = factory.getOWLNamedIndividual(fullIri(PREFIX + newData.at("iri").asString())); 
		OWLIndividual oldInd = factory.getOWLNamedIndividual(fullIri(PREFIX + oldData.at("iri").asString())); 
		
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
	
	public static List<OWLOntologyChange> clearChanges (List<OWLOntologyChange> changes){
		List<OWLOntologyChange> result = new ArrayList<>();
		
		int lim = changes.size();
		for (int i=0; i<lim; i++){
			boolean found = false;
			for (int j=0;j<lim; j++)
				if (i!=j){
					if (isIdenticalOppositeAxion(changes.get(i), changes.get(j))){
					found = true;
					continue;
				}
			}
			
			if (!found){
				result.add(changes.get(i));
			}
			
		}
		
		return result;
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
			for (OWLAxiom a : O.getDataPropertyAssertionAxioms(individual)) L.add(new RemoveAxiom(O, a));
			for (OWLAxiom a : O.getObjectPropertyAssertionAxioms(individual)) L.add(new RemoveAxiom(O, a));
			for (OWLAxiom a : O.getAnnotationAssertionAxioms(((OWLEntity) individual).getIRI())) L.add(new RemoveAxiom(O, a));
			for (OWLAxiom a : O.getClassAssertionAxioms(individual)) L.add(new RemoveAxiom(O, a));
		}
		
		return L;
	}
	
	protected static PropertyDescriptor findPropertyIri(String irifragment) {
		PropertyDescriptor result = new PropertyDescriptor();
		Json prefixes = Json.array().add("legacy:").add("mdc:");
		for (Json prefix : prefixes.asJsonList()) {
			IRI propIri = fullIri(prefix.asString() + irifragment);
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
	
	/*
	 * Refactored code coming from BOntology class 
	 * 
	 */	
	
	protected static List<OWLOntologyChange> makeObjectIndividual (OWLIndividual parent, Json properties, OWLOntology O, OWLOntologyManager manager, OWLDataFactory factory){
		List<OWLOntologyChange> result = new ArrayList<OWLOntologyChange>();
		for (Map.Entry<String, Json> e : properties.asJsonMap().entrySet())
		{
			String key = e.getKey();
			Json value = e.getValue();
			if (key.equals("label") || key.equals("iri") || key.equals("type"))
			{
				if (key.equals("type"))
					result.add(new AddAxiom(O, factory.getOWLClassAssertionAxiom(owlClass(fullIri(e.getValue().asString())),parent)));
				else if (key.equals("label"))
					result.add(new  AddAxiom(O,factory.getOWLAnnotationAssertionAxiom(((OWLEntity) parent).getIRI(), 
																					  factory.getOWLAnnotation(OWL.annotationProperty("http://www.w3.org/2000/01/rdf-schema#label"), 
																				      factory.getOWLLiteral(value.asString())))));
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
				if (value.at("iri").asString().contains("#")) value.set("iri", getIdFromUri(value.at("iri").asString()));
				OWLNamedIndividual object = factory.getOWLNamedIndividual(fullIri(PREFIX + value.at("iri").asString()));
				result.add(new AddAxiom(O, factory.getOWLObjectPropertyAssertionAxiom(prop, ind, object)));
				result.addAll(setPropertiesFor(object, value, O, manager, factory));
			}
			else
			{				
				throw new RuntimeException("Missing iri on Object Property: " + value.asString());
			}
		}
		else{
			OWLNamedIndividual object;
			if (!value.asString().contains("#")) object = factory.getOWLNamedIndividual(fullIri(PREFIX + value.asString()));
			else object = factory.getOWLNamedIndividual(fullIri(PREFIX + getIdFromUri(value.asString())));			
			result.add(new AddAxiom(O, factory.getOWLObjectPropertyAssertionAxiom(prop, ind, object)));
		}
		
		return result;
	}
	
	protected static List<OWLOntologyChange> setPropertiesFor(OWLNamedIndividual individual, Json properties, OWLOntology O, OWLOntologyManager manager, OWLDataFactory factory)
	{
		// Remove all data and object properties currently declared on the ontology.
		List<OWLOntologyChange> L = new ArrayList<OWLOntologyChange>();
		L.addAll(getRemoveOnlyPropertiesIndividualChanges(individual));		
		L.addAll(makeObjectIndividual(individual, properties, O, manager, factory));
		
		return L;
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
			xsdType = null;
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
		Set<OWLDataRange> ranges = prop.getRanges(OWL.ontologies());
		if (ranges.isEmpty() && builtinDatatype != null) {
			return factory.getOWLLiteral(value, builtinDatatype);
		}
		for (OWLDataRange range : ranges)
		{
			if ((builtinDatatype == null && range instanceof OWLDatatype)
					|| (builtinDatatype != null && range.equals(builtinDatatype))) 
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
		String token = tokenizeUri(uri, "/", 5, 4); 
		return tokenizeUri(token, "#", 2, 0);
	}
		
	public static String getIdFromUri(String uri)
	{
		return tokenizeUri(uri, "#", 2, 1);
	}
	
	public static String getIdFromIdentifier(String uri)
	{
		return tokenizeUri(uri, ":", 2, 1);
	}
		
	private static String tokenizeUri(String uri, String del, int expectedLength, int returnPosition){
			
			String tokens[]  = uri.split(del);
			
			if(tokens.length != expectedLength)
				throw new IllegalArgumentException("Invalid uri");
			
			return tokens[returnPosition];
	}
	
	public static String getIndividualIdentifier(String id){
		if (id.contains(":")) return getIdFromIdentifier(id);
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
	 * Resolves all fulliri strings in the owlSerialized json 
	 * and modifies the json by replacing all fulliri strings with the resolved json objects.
	 */
	public static Json resolveAllIris(Json owlSerialized) {
		resolveAllIris(owlSerialized, owlSerialized, RESOLVE_ALL_IRI_MAX_DEPTH);
		
		return owlSerialized;
	}


	/**
	 * Resolves all fulliri strings starting at root in the context of owlSerialized  
	 * and modifies the owlSerialized by replacing all fulliri strings with the resolved json objects.
	 * Root must be within owlSerialized.
	 */
	public static void resolveAllIris(Json owlSerialized, Json root, int maxDepth) {
		if (maxDepth == 0) return;
		if (root.isObject()) {
			Map<String,Json> properties = root.asJsonMap();
			for (Map.Entry<String, Json> propKeyValue : properties.entrySet()) {
				if (!propKeyValue.getKey().equals("iri") 
						&& isFullIriString(propKeyValue.getValue())) {
					//modify Json key to fully resolved json object
					Json serializedOwlEntity = findByIri(owlSerialized, propKeyValue.getValue().asString());
					if (serializedOwlEntity.isNull()) {
						//ignore
						//throw new IllegalArgumentException("parameter contains IRI for which no serialized object could be found: " + propKeyValue.getValue());
					} else {
						//replace key with full object instead of string IRI.
						propKeyValue.setValue(serializedOwlEntity.dup());	
					}
				} 
				resolveAllIris(owlSerialized, propKeyValue.getValue(), maxDepth - 1);
			}
		} else if (root.isArray()) {
			ListIterator<Json> arrayIt = root.asJsonList().listIterator();
			while(arrayIt.hasNext()) {
				Json elem = arrayIt.next();
				if (isFullIriString(elem)) {
					Json serializedOwlEntity = findByIri(owlSerialized, elem.asString());
					if (serializedOwlEntity.isNull()) {
						//ignore now
					} else {
						//replace cur elem.
						arrayIt.set(serializedOwlEntity.dup());
						//needed so recursion continues with object not iri string.
						elem = serializedOwlEntity;
					}
				} 
				resolveAllIris(owlSerialized, elem, maxDepth - 1);
			}
		} else {
			// nothing to do for primitives
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
	
	/**
	 * Finds any object with an IRI attribute that's fully serialized.
	 * Ignores fullIris that are Strings and not objects.
	 * 
	 * @param fullIRI
	 * @return
	 */
	public static Json findByIri(Json owlSerialized, String fullIRI) {
		if (owlSerialized.isObject()) {
			if (owlSerialized.has("iri") && fullIRI.equals(owlSerialized.at("iri").asString()))
				return owlSerialized;
			else {
				Map<String,Json> properties = owlSerialized.asJsonMap();
				for (Json propValue : properties.values()) {
					Json result = findByIri(propValue, fullIRI);
					if (!result.isNull()) {
						return result;
					}
				}
				return Json.nil();
			}
		} else if (owlSerialized.isArray()) {
			List<Json> array = owlSerialized.asJsonList();
			for (Json elem : array) {
				Json result = findByIri(elem, fullIRI);
				if (!result.isNull()) {
					return result;
				}
			}
			return Json.nil();
		} else return Json.nil();				
	}

	
}

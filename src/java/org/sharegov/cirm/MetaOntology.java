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
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.utils.GenUtils;


/**
 *  
 * @author Chirino
 * 
 * Utility wrapper over Meta Ontology operations
 * 
 */
public class MetaOntology 
{

	private static final String PREFIX = "legacy:";
	/*
	 * Generic Ontology handling functions.
	 */
	
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
		
		if (!data.has("iri")){
			throw new RuntimeException("root iri not found : " + data.toString());
		}

		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory factory = manager.getOWLDataFactory();
		OWLIndividual newInd = factory.getOWLNamedIndividual(fullIri(PREFIX + data.at("iri").asString())); 
		
		List<OWLOntologyChange> result = getRemoveAllPropertiesIndividualChanges (O, newInd);
		
		result.addAll(makeObjectIndividual (newInd, data, O, manager, factory));
		
		OWLIndividual parent = factory.getOWLNamedIndividual(fullIri(PREFIX + parentID));
		OWLObjectProperty property =  factory.getOWLObjectProperty(fullIri(PREFIX + propertyID));
		
		result.add(new AddAxiom(O, factory.getOWLObjectPropertyAssertionAxiom(property, parent, newInd)));
		
		return result;
	}
	
	protected static List<OWLOntologyChange> getRemoveAllPropertiesIndividualChanges (OWLOntology O, OWLIndividual individual){
		List<OWLOntologyChange> L = new ArrayList<OWLOntologyChange>();
		
		for (OWLAxiom a : O.getDataPropertyAssertionAxioms(individual)) L.add(new RemoveAxiom(O, a));
		for (OWLAxiom a : O.getObjectPropertyAssertionAxioms(individual)) L.add(new RemoveAxiom(O, a));
		for (OWLAxiom a : O.getAnnotationAssertionAxioms(((OWLEntity) individual).getIRI())) L.add(new RemoveAxiom(O, a));
		for (OWLAxiom a : O.getClassAssertionAxioms(individual)) L.add(new RemoveAxiom(O, a));
		
		return L;
	}
	
	protected static PropertyDescriptor findPropertyIri (String irifragment){
		PropertyDescriptor result = new PropertyDescriptor();
		Json prefixes = Json.array().add("legacy:").add("mdc:").add(":");
		
		for (Json prefix :  prefixes.asJsonList()){
			IRI propIri = fullIri(prefix.asString() + irifragment);
			PropertyType type = OWL.getPropertyType(propIri);
			if (type != PropertyType.UNKNOWN){
				result.setIri(propIri);
				result.setType(type);
				return result;
			}
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
			OWLNamedIndividual object = factory.getOWLNamedIndividual(fullIri(PREFIX + value.asString()));
			result.add(new AddAxiom(O, factory.getOWLObjectPropertyAssertionAxiom(prop, ind, object)));
		}
		
		return result;
	}
	
	protected static List<OWLOntologyChange> setPropertiesFor(OWLNamedIndividual individual, Json properties, OWLOntology O, OWLOntologyManager manager, OWLDataFactory factory)
	{
		// Remove all data and object properties currently declared on the ontology.
		List<OWLOntologyChange> L = new ArrayList<OWLOntologyChange>();
		L.addAll(getRemoveAllPropertiesIndividualChanges(O, individual));		
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
	
	
}

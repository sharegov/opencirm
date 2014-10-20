package org.sharegov.cirm.owl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import mjson.Json;

import org.semanticweb.owlapi.model.EntityType;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.utils.GenUtils;

/**
 * <p>
 * Convert the Json representation of an OWL object to a set of OWL axioms. This is very similar
 * to the work done in the BOntology class, so some of code was copied from there. The B
 * </p>
 * 
 * @author Borislav Iordanov
 *
 */
public class JsonToOWL
{
	private OWLOntology O;
	private OWLDataFactory factory;
	private OWLReferenceContext referenceContext; 
	
	public JsonToOWL(OWLOntology ontology)
	{
		this.O = ontology;
		this.factory = OWL.dataFactory();
		referenceContext = DeclarationReferenceContext.make(O);
	}
	
	private void addDataPropertyAxioms(OWLNamedIndividual ind, Json value, OWLDataProperty prop, Set<OWLAxiom> axioms)
	{
		if (value.isArray())
		{
			for (Json x : value.asJsonList())
				addDataPropertyAxioms(ind, x, prop, axioms);
			return;
		}
		OWLLiteral literal = null;
		if (value.isObject())
		{
			if (!value.has("type") || !value.has("literal"))
				throw new IllegalArgumentException("Bad OWL data property JSON value " + value + " of " + ind);
			OWL2Datatype xsdType = OWL2Datatype.getDatatype(OWL.fullIri(value.at("type").asString()));
			if (xsdType == null)
				throw new IllegalArgumentException("Unrecognized OWL2 primitive data type " + value.at("type") + " for " + ind);
			String literalString = value.at("literal").getValue().toString();
			if (xsdType.equals(OWL2Datatype.XSD_DATE_TIME_STAMP))
				literal = OWL.literal(GenUtils.parseDate(literalString));
			else
				literal = factory.getOWLLiteral(literalString, xsdType);			
		}
		else if (!value.isNull())
			literal = factory.getOWLLiteral(value.getValue().toString());
		if (literal != null)
			axioms.add(factory.getOWLDataPropertyAssertionAxiom(prop, ind, literal));			
	}

	private void addObjectPropertyAxioms(OWLNamedIndividual ind, Json value, OWLObjectProperty prop, Set<OWLAxiom> axioms)
	{
		if (value.isArray())
		{
			for (Json x : value.asJsonList())
				addObjectPropertyAxioms(ind, x, prop, axioms);
			return;
		}
		OWLNamedIndividual object = referenceContext.entity(value.at("iri").asString(), EntityType.NAMED_INDIVIDUAL);
		if (object == null)
		{
			object = com.clarkparsia.owlapiv3.OWL.Individual(referenceContext.fullIri(value.at("iri").asString()));
			axioms.add(factory.getOWLDeclarationAxiom(object));
		}
			
		axioms.add(factory.getOWLObjectPropertyAssertionAxiom(prop, ind, object));
		toOWL(value, axioms);
	}

	private void addAnnotationPropertyAxioms(OWLNamedIndividual ind, Json value, OWLAnnotationProperty prop, Set<OWLAxiom> axioms)
	{
		if (value.isArray())
		{
			for (Json x : value.asJsonList())
				addAnnotationPropertyAxioms(ind, x, prop, axioms);
			return;
		}
		axioms.add(factory.getOWLAnnotationAssertionAxiom(prop, ind.getIRI(), OWL.literal(value.getValue().toString())));
	}
	
	public Set<OWLAxiom> toOWL(Json object, Set<OWLAxiom> axioms)
	{
		if (!object.isObject())
			throw new IllegalArgumentException("JSON is not an object " + object);
		if (!object.has("iri"))
			throw new IllegalArgumentException("Missing IRI for object " + object);
		OWLNamedIndividual ind = referenceContext.entity(object.at("iri").asString(), EntityType.NAMED_INDIVIDUAL);
		if (ind == null)
		{
			ind = com.clarkparsia.owlapiv3.OWL.Individual(referenceContext.fullIri(object.at("iri").asString()));
			axioms.add(factory.getOWLDeclarationAxiom(ind));
		}
		if (object.has("type"))
		{
			OWLClass typeClass = referenceContext.entity(object.at("type").asString(), EntityType.CLASS);
			if (typeClass == null)
				throw new IllegalArgumentException("Type not found : " + object.at("type").asString());
			axioms.add(factory.getOWLClassAssertionAxiom(typeClass, ind));
		}
		// TODO: what about extendedTypes?
		for (Map.Entry<String, Json> p : object.asJsonMap().entrySet())
		{
			if ("type".equals(p.getKey()) || "iri".equals(p.getKey()) || p.getKey().startsWith("transient$"))
				continue; 
			// "representative" value (in case of array) to figure what type of property
			// we are dealing with
			Json rep = p.getValue(); 
			if (p.getValue().isArray())
				if (p.getValue().asJsonList().isEmpty())
					continue;
				else
					rep = p.getValue().at(0);
			if (rep.isArray())
				throw new IllegalArgumentException("Invalid property value: array within array.");
			if (rep.isObject() && rep.has("iri"))
			{
				OWLObjectProperty oprop = referenceContext.entity(p.getKey(), EntityType.OBJECT_PROPERTY);
				if (oprop == null)
					throw new IllegalArgumentException("No object property with name " + p.getKey());
				addObjectPropertyAxioms(ind, p.getValue(), oprop, axioms);
			}
			else if (rep.isPrimitive() || rep.asJsonMap().keySet().equals(GenUtils.set("type", "value")))
			{
				OWLDataProperty dprop = referenceContext.entity(p.getKey(), EntityType.DATA_PROPERTY);
				if (dprop == null)
				{
					if (!rep.isPrimitive())
						throw new IllegalArgumentException("No data property with name " + p.getKey());
					OWLObjectProperty oprop = referenceContext.entity(p.getKey(), EntityType.OBJECT_PROPERTY);
					if (oprop != null && rep.isString() && rep.asString().startsWith("http://"))
					{
						addObjectPropertyAxioms(ind, Json.object("iri", rep), oprop, axioms);
						continue;
					}
					OWLAnnotationProperty aprop = null;
					if ("label".equals(p.getKey()))
						aprop = OWL.annotationProperty("rdfs:label");  
					else if ("comment".equals(p.getKey()))
						aprop = OWL.annotationProperty("rdfs:comment");
					else
						aprop = referenceContext.entity(p.getKey(), EntityType.ANNOTATION_PROPERTY);
					if (aprop == null)
						throw new IllegalArgumentException("No data or annotation property with name " + p.getKey());
					else
						addAnnotationPropertyAxioms(ind, p.getValue(), aprop, axioms);
				}
				else
					addDataPropertyAxioms(ind, p.getValue(), dprop, axioms);
			}
		}
		return axioms;
	}
	
	public Set<OWLAxiom> toOWLAxioms(Json object)
	{
		return toOWL(object, new HashSet<OWLAxiom>());		
	}
	
	public OWLOntology toOWLOntology(Json object)
	{
		// TODO: maybe move the BOntology logic here if the mapping is the same for
		// business objects and meta data.	
		return null;
	}	
}

package org.sharegov.cirm.owl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import mjson.Json;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
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
	
	public JsonToOWL(OWLOntology ontology)
	{
		this.O = ontology;
		this.factory = OWL.dataFactory();
	}
	
	private boolean looksPrimitive(Json x)
	{
		return x.isPrimitive() || 
			   (x.isObject() && x.asJsonMap().keySet().equals(GenUtils.set("type", "value"))) ||
			   (x.isArray() && x.asJsonList().size() > 0 && looksPrimitive(x.at(0)));
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
		OWLNamedIndividual object = OWL.individual(value.at("iri").asString());
		axioms.add(factory.getOWLObjectPropertyAssertionAxiom(prop, ind, object));
		toOWL(value, axioms);
	}
	
	public Set<OWLAxiom> toOWL(Json object, Set<OWLAxiom> axioms)
	{
		if (!object.has("iri"))
			throw new IllegalArgumentException("Missing IRI for object " + object);
		OWLNamedIndividual ind = OWL.individual(object.at("iri").asString());
		if (object.has("type"))
			axioms.add(factory.getOWLClassAssertionAxiom(
					OWL.owlClass(object.at("type").asString()), ind));
		for (Map.Entry<String, Json> p : object.asJsonMap().entrySet())
		{
			if ("type".equals(p.getKey()) || "iri".equals(p.getKey()))
				continue;
			IRI piri = OWL.fullIri(p.getKey()); 
			boolean isDeclaredData = OWL.isDataProperty(piri);
			boolean isDeclaredObject = OWL.isObjectProperty(piri);
			if (isDeclaredObject)
			{
				if (isDeclaredData && looksPrimitive(p.getValue()))
					addDataPropertyAxioms(ind, p.getValue(), OWL.dataProperty(piri), axioms);
				else if (p.getValue().isPrimitive())
					throw new IllegalArgumentException("Property " + 
								piri + " is an object, but value " + p.getValue() + " of " + p.getKey() + " is not.");
				else
					addObjectPropertyAxioms(ind, p.getValue(), OWL.objectProperty(piri), axioms);
			}
			else if (isDeclaredData)
				addDataPropertyAxioms(ind, p.getValue(), OWL.dataProperty(piri), axioms);
			else
				throw new IllegalArgumentException("Unknown property " + piri + " with value " + p.getValue());					
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

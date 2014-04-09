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

import static org.sharegov.cirm.OWL.businessObject;
import static org.sharegov.cirm.OWL.dataProperty;
import static org.sharegov.cirm.OWL.fullIri;
//import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.objectProperty;
import static org.sharegov.cirm.OWL.owlClass;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import mjson.Json;

import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataRange;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLProperty;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.JsonSerializable;
import org.sharegov.cirm.utils.JsonUtil;
import org.sharegov.cirm.utils.Mapping;

/**
 * 
 * <p>
 * Represents a business object ontology. Essentially wrap a
 * <code>OWLOntology</code> and provide utility methods to manipulate it.
 * </p>
 * 
 * <p>
 * This is a very lightweight object, a thin wrapper so that many be created
 * frequently and on demand.
 * </p>
 * 
 * @author Borislav Iordanov
 * 
 */
public class BOntology implements JsonSerializable
{
	private OWLOntology O;
	private volatile int tempIndividualCounter = 0;

	/**
	 * On the client-side, we need those temporary individuals. But when the ontology is saved,
	 * the temporary individuals are removed by the ontology transformer. When loading form
	 * the store, the temporary individuals get generated  for the UI JSON representation.
	 * @return
	 */
	IRI getTempIRI()
	{
		return fullIri("temp" + tempIndividualCounter++);		
	}
	
	boolean isTempIRI(String iri)
	{
		return iri.startsWith(fullIri("temp").toString());
	}
	
	public BOntology(OWLOntology O)
	{
		this.O = O;
	}

	/**
	 * Checks if the passed in IRI is a valid BOntology or not
	 * @param entityIRI 
	 * @return true/false
	 */
	public static boolean isValidBO(IRI entityIRI)
	{
		if(entityIRI != null && entityIRI.toString().startsWith(Refs.BO_PREFIX))
			return true;
		else
			return false;
	}

	public static BOntology makeNewBusinessObject(OWLClass type)
			throws OWLOntologyCreationException
	{
		OWLOntologyManager manager = Refs.tempOntoManager.resolve();
		IRI iriPrefix = IRI.create(Refs.BO_PREFIX + "/"
				+ type.getIRI().getFragment());
		IRI ontologyIRI = iriPrefix.resolve(Refs.idFactory.resolve().newId(
				type.getIRI().getFragment()));
		OWLOntology o = manager.createOntology(IRI.create(ontologyIRI
				.getStart().substring(0, ontologyIRI.getStart().length() - 1)));
		OWLNamedIndividual businessObject = businessObject(o);
		OWLAxiom axiom = manager.getOWLDataFactory().getOWLClassAssertionAxiom(
				type, businessObject);
		manager.applyChange(new AddAxiom(o, axiom));
		return new BOntology(o);
	}

	/**
	 * Creates an in-memory BOntology based on the Json data representation.
	 * This ontology will reside in its own OWLOntologyManager and its intended
	 * to be transient, perhaps living during the lifetime of a single client
	 * request.
	 * 
	 * @param data
	 * @return
	 * @throws OWLOntologyCreationException
	 */
	public static BOntology makeRuntimeBOntology(Json data)
	{
		try
		{
			OWLOntologyManager manager = Refs.tempOntoManager.resolve();
			IRI ontologyIRI = IRI.create("http://www.miamidade.gov/bo/"
					+ data.at("type").asString().replace("legacy:", "") + "/"
					+ data.at("boid").asString());
			OWLOntology o = manager.getOntology(ontologyIRI);
			if (o != null)
				manager.removeOntology(o);
			o = manager.createOntology(ontologyIRI);
			BOntology result = new BOntology(o);
			result.prefix(data, new IdentityHashMap<Json, Boolean>());
			result.setProperties(data.at("properties"));
			OWLNamedIndividual businessObject = businessObject(o);
			OWLAxiom axiom = manager.getOWLDataFactory()
					.getOWLClassAssertionAxiom(owlClass(data.at("type").asString()), businessObject);
			manager.applyChange(new AddAxiom(o, axiom));
			return result;
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
	}

	public OWLOntology getOntology()
	{
		return O;
	}

	public String getObjectId()
	{
		String A[] = O.getOntologyID().getOntologyIRI().toString().split("/");
		return A[A.length - 1];
	}

	public IRI getTypeIRI()
	{
		return OWL.fullIri(getObjectIRI().toURI().getPath().split("/")[2]);
	}

	public IRI getTypeIRI(String prefix)
	{
		return OWL.fullIri(prefix + ":"
				+ getObjectIRI().toURI().getPath().split("/")[2]);
	}

	public IRI getObjectIRI()
	{
		return O.getOntologyID().getOntologyIRI().resolve("#bo");
	}

	public OWLNamedIndividual getBusinessObject()
	{
		return O.getOWLOntologyManager().getOWLDataFactory().getOWLNamedIndividual(getObjectIRI());
	}

	public OWLLiteral getDataProperty(String name)
	{
		OWLNamedIndividual object = getBusinessObject();
		Set<OWLLiteral> S = object.getDataPropertyValues(dataProperty(name), O);
		return S.isEmpty() ? null : S.iterator().next();
	}

	public OWLNamedIndividual getObjectProperty(String name)
	{
		OWLNamedIndividual object = getBusinessObject();
		Set<OWLIndividual> S = object.getObjectPropertyValues(objectProperty(name), O);
		return S.isEmpty() ? null : S.iterator().next().asOWLNamedIndividual();
	}
	
	public Set<OWLIndividual> getObjectProperties(String name)
	{
		OWLNamedIndividual object = getBusinessObject();
		Set<OWLIndividual> S = object.getObjectPropertyValues(objectProperty(name), O);
		return S;
	}

	/**
	 * @param indIri
	 *            - if null a "temp" individual will be returned. As soon as
	 *            toJson spports anonymous ind, OWLAnonymousIndividual should be
	 *            returned;
	 * @param properties
	 *            - the properties to create axioms for.
	 * @return
	 */
	protected OWLIndividual makeObjectIndividual(IRI indIri, Json properties)
	{
		OWLOntologyManager manager = O.getOWLOntologyManager();
		OWLDataFactory df = manager.getOWLDataFactory();
		OWLIndividual result = df.getOWLNamedIndividual(indIri == null ? getTempIRI() : indIri);
		for (Map.Entry<String, Json> e : properties.asJsonMap().entrySet())
		{
			if (e.getKey().equals("label") || e.getKey().equals("iri") || e.getKey().equals("type"))
			{
				if (e.getKey().equals("type"))
					manager.addAxiom(O, df.getOWLClassAssertionAxiom(
							owlClass(fullIri(e.getValue().asString())),
							result));
				continue;
			}
			IRI propIri = fullIri(e.getKey());
			if (OWL.isObjectProperty(propIri))
			{
				if (e.getValue().isArray())
				{
					for (int i = 0; i < e.getValue().asList().size(); i++)
					{
						addObjectProperty(result, OWL.dataFactory().getOWLObjectProperty(propIri), e.getValue().at(i));
					}
				}
				else
					addObjectProperty(result, OWL.dataFactory().getOWLObjectProperty(propIri), e.getValue());
			}
			else if (OWL.isDataProperty(propIri))
			{
				if (e.getValue().isArray())
				{
					for (int i = 0; i < e.getValue().asList().size(); i++)
					{
						addDataProperty(result, OWL.dataFactory().getOWLDataProperty(propIri), e.getValue().at(i));
					}
				}
				else
					addDataProperty(result, OWL.dataFactory().getOWLDataProperty(propIri), e.getValue());
			}
			else if (!OWL.isAnnotation(propIri))
				throw new RuntimeException("Undeclared OWL property or annotation: " + propIri);
		}
		return result;
	}

	/**
	 * <p>
	 * First remove all properties of the this individual, then assign new properties
	 * from the <code>properties</code> parameter. 
	 * </p>
	 * 
	 * @param ind
	 * @param properties
	 * @return The <code>ind</code> parameter.
	 */
	public OWLNamedIndividual setPropertiesFor(OWLNamedIndividual ind, Json properties)
	{
		// Remove all data and object properties currently declared in the
		// ontology.
		ArrayList<OWLOntologyChange> L = new ArrayList<OWLOntologyChange>();
		for (OWLAxiom a : O.getDataPropertyAssertionAxioms(ind))
			L.add(new RemoveAxiom(O, a));
		for (OWLAxiom a : O.getObjectPropertyAssertionAxioms(ind))
			L.add(new RemoveAxiom(O, a));
		O.getOWLOntologyManager().applyChanges(L);
		makeObjectIndividual(ind.getIRI(), properties);
		return ind;
	}

	public void setProperties(Json properties)
	{
		OWLNamedIndividual boInd = businessObject(O);
		setPropertiesFor(boInd, properties);
	}

	public OWLLiteral addDataProperty(OWLIndividual ind, OWLDataProperty prop, Json value)
	{
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
			xsdType = OWL2Datatype.getDatatype(OWL.fullIri(typeStr));// IRI.create(typeStr));
			if (xsdType == null)
				throw new IllegalArgumentException(
						"Unable to read type for Jason value." + value);
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
			return null;
		}
		OWLOntologyManager manager = O.getOWLOntologyManager();
		OWLDataFactory df = manager.getOWLDataFactory();
		literal = toLiteral(df, prop, valueStr, xsdType);
		if (literal == null)
			literal = df.getOWLLiteral(valueStr);
		OWLAxiom axiom = df.getOWLDataPropertyAssertionAxiom(prop, ind, literal);
		manager.applyChange(new AddAxiom(O, axiom));
		return literal;
	}

	private OWLClass getValueType(OWLObjectProperty prop, Json value)
	{
		if (value.has("type"))
			return owlClass(value.at("type").asString());
		else
			for (OWLClassExpression range : prop.getRanges(OWL.ontologies()))
				if (range instanceof OWLClass)
					return (OWLClass) range;
		return null;
		// // TODO: we need to somehow pass the actual type of the property
		// // value
		// // to the client (hidden form field perhaps) and back to the server
		// // this is a hack because PromptUserTask currently uses the first
		// // range that has a form
		// if (new UiService().getForm(range) != null)
		// {
		// objectType = range;
		// break;
		// }
		//
	}
	
	public OWLIndividual addObjectProperty(OWLIndividual ind, String prop, Json value)
	{
		return addObjectProperty(ind, O.getOWLOntologyManager().getOWLDataFactory().getOWLObjectProperty(fullIri(prop)), value);
	}

	public OWLIndividual addObjectProperty(OWLIndividual ind, OWLObjectProperty prop, Json value)
	{
		OWLOntologyManager manager = O.getOWLOntologyManager();
		OWLDataFactory df = manager.getOWLDataFactory();
		OWLIndividual object = null;

		if (value.isObject())
		{
			if (value.has("iri"))
				object = setPropertiesFor(df.getOWLNamedIndividual(fullIri(value.at("iri").asString())), value);
			else
			{
				IRI indIri = null;
				if (!Refs.ontologyTransformer.resolve().isTransformProperty(prop))
				{
					OWLClass objectType = getValueType(prop, value);
					if (objectType == null)
						throw new RuntimeException(
								"Couldn't figure out the type of " + value
										+ " as object property " + prop);
					String type = objectType.asOWLClass().getIRI().getFragment();
					indIri = fullIri(type + Refs.idFactory.resolve().newId(null));
				}
				if (indIri == null)
					indIri = getTempIRI();
				object = setPropertiesFor(df.getOWLNamedIndividual(indIri), value);
			}
		}
		else
			object = df.getOWLNamedIndividual(fullIri(value.asString()));

		manager.applyChange(new AddAxiom(O, 
				df.getOWLObjectPropertyAssertionAxiom(prop, ind, object)));

		return object;
	}

	public void deleteObjectProperty(OWLIndividual ind, String prop)
	{
		OWLOntologyManager manager = O.getOWLOntologyManager();
		OWLDataFactory df = manager.getOWLDataFactory();
		OWLObjectProperty property = df.getOWLObjectProperty(fullIri(prop));
		Set<OWLIndividual> all = ind.getObjectPropertyValues(property, O);
		for (OWLIndividual x : all)
		{
			OWLAxiom axiom = df.getOWLObjectPropertyAssertionAxiom(property, ind, x);
			manager.applyChange(new RemoveAxiom(O, axiom));
		}
	}

	public void deleteDataProperty(OWLIndividual ind, String prop)
	{
		OWLOntologyManager manager = O.getOWLOntologyManager();
		OWLDataFactory df = manager.getOWLDataFactory();
		OWLDataProperty property = df.getOWLDataProperty(fullIri(prop));
		Set<OWLLiteral> all = ind.getDataPropertyValues(property, O);
		for (OWLLiteral x : all)
		{
			OWLAxiom axiom = df.getOWLDataPropertyAssertionAxiom(property, ind, x);
			manager.applyChange(new RemoveAxiom(O, axiom));
		}
	}
	
	public void deleteObjectProperty(OWLIndividual ind, OWLObjectProperty prop)
	{
		OWLOntologyManager manager = O.getOWLOntologyManager();
		OWLDataFactory df = manager.getOWLDataFactory();
		Set<OWLIndividual> all = ind.getObjectPropertyValues(prop, O);
		for (OWLIndividual x : all)
		{
			OWLAxiom axiom = df.getOWLObjectPropertyAssertionAxiom(prop, ind, x);
			manager.applyChange(new RemoveAxiom(O, axiom));
		}
	}

	public void deleteDataProperty(OWLIndividual ind, OWLDataProperty prop)
	{
		OWLOntologyManager manager = O.getOWLOntologyManager();
		OWLDataFactory df = manager.getOWLDataFactory();
		Set<OWLLiteral> all = ind.getDataPropertyValues(prop, O);
		for (OWLLiteral x : all)
		{
			OWLAxiom axiom = df.getOWLDataPropertyAssertionAxiom(prop, ind, x);
			manager.applyChange(new RemoveAxiom(O, axiom));
		}
	}

	public void deleteProperty(OWLIndividual ind,
			@SuppressWarnings("rawtypes") OWLProperty prop)
	{
		if (prop instanceof OWLDataProperty)
			deleteDataProperty(ind, (OWLDataProperty) prop);
		else
			deleteObjectProperty(ind, (OWLObjectProperty) prop);
	}

	private static final Set<String> toprefix = GenUtils.set(
			"hasServiceAnswer", "hasAnswerValue", "hasServiceField", 
			"hasServiceCaseActor", "ServiceAnswer", "hasServiceActor", 
			"ServiceCaseActor", "hasServiceActivity", "hasActivity", 
			"hasCompletedTimestamp", "hasUpdatedDate", "hasDetails", 
			"hasDueDate", "isAssignedTo", "hasOutcome", "Outcome", 
			"ServiceActivity", "hasOldData", "isAccepted", "hasStatus", 
			"hasPriority", "hasIntakeMethod", "hasAnswerObject", 
			"hasCaseNumber", "hasParentCaseNumber", "hasGisDataId", "hasLocationDetails",
			"hasDepartmentError", "hasLegacyEvent");

	private static final Set<String> toignore = GenUtils.set("hasLegacyCode",
			"hasLegacyId", "addressType", "label", "hasChoiceValueList",
    		"hasDataType", "hasOrderBy", "hasAnswerUpdateTimeout","description", 
	        "description2", "description3", "description4", "description5", 
	        "description6", "comment", "isOldData", "participantEntityTable",
			"hasBusinessCodes", "hasAllowableModules", "folio", "isDisabled",
			"transient$protected", "isAlwaysPublic", "isHighlighted", 
			"fromDiffSRType", "hasLegacyInterface", "hasLegacyEvent");

	private static final Set<String> toValueprefix = GenUtils.set("type");

	private static final Set<String> toValueExcludePrefix = GenUtils.set("Street_Address");

	private void prefix(Json j, IdentityHashMap<Json, Boolean> done)
	{
		if (done.containsKey(j))
			return;
		else
			done.put(j, true);
		if (j.isArray())
			for (Json el : j.asJsonList())
				prefix(el, done);
		else if (j.isObject())
		{
			for (String s : toignore)
				j.asJsonMap().remove(s);
			for (String s : toValueprefix)
				if (j.has(s) && j.at(s).isString()
						&& !j.at(s).asString().matches("\\w+:\\w+|http://.+")
						&& !toValueExcludePrefix.contains(j.at(s).asString()))
					j.set(s, "legacy:" + j.at(s).asString());
			for (String s : toprefix)
				if (j.has(s))
					j.set("legacy:" + s, j.atDel(s));
			for (Map.Entry<String, Json> e : j.asJsonMap().entrySet())
				prefix(e.getValue(), done);
		}
	}

	public Json toJSON()
	{
		// Json j = MetaService.get().toJSON(O, this.getBusinessObject());
		OWLObjectToJson mapper = new OWLObjectToJson();
		mapper.setIncludeTypeInfo(true);
		mapper.getPropertiesToTypeDecorate().add("hasAnswerValue");
		Json j = mapper.map(this.getOntology(), this.getBusinessObject(), null);
		Json result = Json.object().set("boid", this.getObjectId());
		result.set("type", j.atDel("type"));
		result.set("iri", j.atDel("iri"));
		result.set("properties", j);
		result = JsonUtil.apply(result, new Mapping<Json, Json>() {
			public Json eval(Json in)
			{
				if (in.isObject() && in.has("iri") && isTempIRI(in.at("iri").asString()))
					in.delAt("iri");
				return in;
			}
		});
		return result;
	}
	
	
	/**
	 * Convert a Json value to an OWL literal using the first range of the 
	 * the data property. If the dataproperty has no ranges defined, the given OWL2Datatype is used.
	 * If a OWL2Datatype is given and the dataproperty has ranges defined a match is enforced.
	 * If no OWL2Datatype is given, the first range that is an OWLDatatype will be used for the literal.
	 * (see @link #toLiteral(OWLDataRange, Json) for details on how the mapping
	 * is performed).
	 * @param prop
	 * @param value
	 * @param builtinDatatype if null, first range will become literal datatype; if given it must match range.
	 * @return an OWlLiteral or null, if the range does not match the given datatype or no OWLDatatype was found in the range.
	 */
	private OWLLiteral toLiteral(OWLDataFactory factory, OWLDataProperty prop, String value, OWL2Datatype builtinDatatype)
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
	
	private OWLLiteral dateLiteral(OWLDataFactory factory, Date date, OWL2Datatype d)
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

    public OWLLiteral getDataProperty(OWLNamedIndividual individual, String name)
    {
           Set<OWLLiteral> S = individual.getDataPropertyValues(dataProperty(name), O);
           return S.isEmpty() ? null : S.iterator().next();
    }
    
    public OWLNamedIndividual getObjectProperty(OWLNamedIndividual individual, String name)
    {
           Set<OWLIndividual> S = individual.getObjectPropertyValues(objectProperty(name), O);
           return S.isEmpty() ? null : S.iterator().next().asOWLNamedIndividual();
    }
	
}

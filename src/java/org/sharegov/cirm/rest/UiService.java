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
package org.sharegov.cirm.rest;

import java.util.Set;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import static org.sharegov.cirm.OWL.*;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;

import mjson.Json;

@Path("ui")
@Produces("application/json")
public class UiService extends RestService
{
	Json prepareEditor(Json editor)
	{
		editor.delAt("isEditorOf");
		Json fieldsOrder = editor.at("hasFieldsOrdering");
		if (fieldsOrder != null)
		{
			editor.delAt("hasFieldsOrdering");
			Json expanded = Json.array();
			for (int i = 0; i < fieldsOrder.asJsonList().size(); i++)
			{
				Json el = fieldsOrder.at(i);
				Json newel = getField(el.at("iri").asString());
				if (newel != null)
					expanded.add(newel);
				else
					expanded.add(el);
			}
			editor.set("fieldSequence", expanded);
		}	
		return editor;
	}
	
	public Json getForm(OWLDataProperty prop)
	{
		OWLIndividual ind = objectProperty(individual(prop.getIRI()), "hasEditor");
		Json result = null;
		if (ind != null)
			result = prepareEditor(OWL.toJSON(ind));
		else for (OWLDataRange range : prop.getRanges(ontologies()))//prop.getRanges(ontology()))
		{			
			switch (range.asOWLDatatype().getBuiltInDatatype())
			{
				case XSD_BOOLEAN:
				{
					result = OWL.toJSON(individual("DefaultCheckBox"));
					break;
				}
				case XSD_STRING:
				default:
				{
					result = OWL.toJSON(individual("DefaultInputBox"));
				}
			}
		}
		if (result != null)
		{
			Json attributes = result.at("attributes", Json.object());
			attributes.set("name", prop.getIRI().getFragment());
			if (result.has("type"))
				result.set("head", result.atDel("type"));
			return annotate(prop, result);
		}
		else
			return result;
	}
	
	public Json getForm(OWLObjectProperty prop)
	{
		OWLIndividual ind = objectProperty(individual(prop.getIRI()), "hasEditor");
		Json result = null;
		if (ind != null)
			result = prepareEditor(OWL.toJSON(ind));
		else for (OWLClassExpression range : prop.getRanges(ontologies())) //prop.getRanges(ontology()))
		{
			result = getForm(range);
			if (result != null)
				break;
		}		
		if (result != null)
		{
			Json attributes = result.at("attributes", Json.object());
			attributes.set("name", prop.getIRI().getFragment());			
			if (result.has("type"))
				result.set("head", result.atDel("type"));			
			return annotate(prop, result);
		}
		else
			return null;
	}
	
	public Json getForm(OWLClassExpression expr)
	{
		switch (expr.getClassExpressionType())
		{
			case OWL_CLASS:
				return getForm((OWLClass)expr);
			case OBJECT_ONE_OF:
				return getForm((OWLObjectOneOf)expr);
			default:
				return null;
		}
	}

	Json getForm(OWLObjectOneOf en)
	{
		Json result = Json.object();
		result.set("head", "UIDropDown");
		Json options = Json.array();
		for (OWLIndividual ind : en.getIndividuals())
		{			
			options.add(Json.object()
				.set("value", ind.toStringID())
				.set("label", ind.isNamed() ? getEntityLabel((OWLEntity)ind) : ind.toString()));
		}
		result.set("options", options);
		return result;
	}
	
	public Json getForm(OWLClass cl)
	{
		Json result = null;
		// First see if that class has already an associated editor.
		OWLIndividual editor = objectProperty(individual(cl.getIRI()), "hasEditor");
		if (editor != null)
		{
			return prepareEditor(OWL.toJSON(ontology(), editor));
//			Json jsonEditor = prepareEditor(app.toJSON(ontology(), editor));
//			result = Json.object().with(jsonEditor);
//			return result;
		}
		
		for (OWLClassExpression expr : cl.getEquivalentClasses(ontologies())) //ontology()
		{
			result = getForm(expr);
			if (result != null)
				return result;
		}
		
		result = Json.object();
		
		// Next, get all properties that have the class in their domain.
		//Set<OWLProperty<?, ?>> properties = app.getClassProperties(ontologies(), owlClass(cl.getIRI()));
		Set<OWLProperty> properties = (Set)OWL.getClassProperties(ontology(), owlClass(cl.getIRI()));
		
		// The form will be simply a JSON array of editors, one for each property.
		Json A  = Json.array();
		
		for (OWLProperty prop : properties)
		{
			Json el = null;
			if (prop instanceof OWLDataProperty)
				el = getForm((OWLDataProperty)prop);
			else if (prop instanceof OWLObjectProperty)
				el = getForm((OWLObjectProperty)prop);
			if (el != null)
				A.add(el);
		}
		result.set("fieldSet", A);
		return result;
	}	
	
	Json getField(String propertyName)
	{
		IRI iri = fullIri(propertyName);
		OWLDataProperty asDataProp = dataProperty(iri);
		if (ontology().getDataPropertiesInSignature(true).contains(asDataProp))
		//if (ontology().getDataPropertiesInSignature().contains(asDataProp))
			return getForm(asDataProp);
		OWLObjectProperty asObjectProp = objectProperty(iri);
		if (ontology().getObjectPropertiesInSignature(true).contains(asObjectProp))
		//if (ontology().getObjectPropertiesInSignature().contains(asObjectProp))
			return getForm(objectProperty(iri));
		return null;
	}

	public Json makeActionButton(String label, String postback)
	{
		Json result = OWL.toJSON(individual("DefaultActionButton"))
			.set("content", label)
			.set("postTo", postback)
			.set("attributes", Json.object().set("type", "button"));
		if (result.has("type"))
			result.set("head", result.atDel("type"));		
		return result;
	}
	
	@GET
	@Path("/new/{classname}")	
	public String getForm(@PathParam("classname") String classname) 
	{
		return getForm(owlClass(fullIri(classname, Refs.topOntologyIRI.resolve())))
					.set("head", "UIForm").toString();	
	}
	
	@GET
	@Path("/editor")
	public Json makeEditor(@QueryParam("individual") String individualParam)
	{
		IRI iri = fullIri(individualParam);
		if (isBusinessObject(iri))
		{
			OWLClass type = businessObjectType(iri);
			return getForm(type).set("head", "UIForm");
		}
		// TODO ...
		return Json.object();
	}

	@GET
	@Path("/propertyEditor")
	public  Json makePropertyEditor(@QueryParam("property") String propertyId)
	{
		OWLObjectProperty prop = objectProperty(propertyId);
		Json result = getForm(prop);
//		for (OWLClassExpression range : prop.getRanges(ontology()))
//			if ((result = getForm(range)) != null)
//				break;		
		if (result == null)
		{
			OWLDataProperty dprop = dataProperty(propertyId);
			Json fieldForm = getForm(dprop);
			Json A = Json.array();
			if (fieldForm != null)
				A.add(fieldForm);
			result = Json.object().set("fieldSequence", A).set("isStructured", false);
		}
		else
		{
			if (!result.has("fieldSequence"))
			{
				Json A = Json.array().add(result);
				result = Json.object().set("fieldSequence", A);
			}
			result.set("isStructured", result.at("fieldSequence").asJsonList().size() > 1);
		}				
		result.set("head", "UIForm");
		return result;		
	}
	
	public Json findTemplate(IRI iri)
	{
		OWLObjectProperty isTemplateOf = objectProperty("isTemplateOf");
		OWLReasoner reasoner = reasoner(ontology());
		Set<OWLNamedIndividual> S = reasoner.getInstances(
				has(isTemplateOf, individual(iri)), true).getFlattened();
		if (!S.isEmpty())
			return OWL.toJSON(S.iterator().next());
		OWLClass cl = owlClass(iri);
		for (OWLClassExpression expr : cl.getSuperClasses(ontology()))
		{
			if (! (expr instanceof OWLClass))
				continue;			
			S = reasoner.getInstances(has(isTemplateOf, 
				individual(((OWLClass)expr).getIRI())), true).getFlattened();
			if (!S.isEmpty())
				return OWL.toJSON(S.iterator().next());
		}
		
		// If everything fails, we just return the generic template for 'Thing' or 
		// simple an empty string.
		S = reasoner.getInstances(
				has(isTemplateOf, individual(iri)), true).getFlattened();
		if (!S.isEmpty())
			return OWL.toJSON(S.iterator().next());
		else
		{
			return Json.object().set("hasContents", "[Object]")
				   				.set("type", "HtmlTemplate");
		}
	}
	
	/**
	 * <p>
	 * Return a display template for an individual. 
	 * </p>
	 * @param iriAsString The IRI of the individual. It could be a business object
	 * IRI, in which case the business ontology is loaded to get the full individual, 
	 * or a generic IRI refering to an individual or a class in the main ontology.
	 * @return
	 */
	@GET
	@Path("/template/{iri}")
	@Produces("application/json")
	public String getTemplate(@PathParam("iri") String iriAsString, @QueryParam("type") String typeName)
	{
		Json result = null;
		
		IRI iri = null; // the IRI of the individual whose template we'll be searching for.
		
		// First check if this points to a business object, just be the format of the IRI
		// in which case we are looking for a template defined for the class of the object.
		
		if (iriAsString.startsWith(Refs.BO_PREFIX))
		{
			String className = iriAsString.substring(Refs.BO_PREFIX.length()).split("/")[1];
			iri = OWL.fullIri(className);
		}
		else 
			iri = fullIri(iriAsString);
	
		// If it's not a class IRI, we need to loop through all its declared classes
		if (ontology().getAxioms(owlClass(iri)).isEmpty())
		{
			IRI typeIri = null;
			if (typeName != null && typeName.length() > 0)
				typeIri = fullIri(typeName);
			else for (OWLClassExpression type : individual(iri).getTypes(ontology()))
				if (type instanceof OWLClass) // && (result = findTemplate(((OWLClass)type).getIRI())) != null)
				{
					typeIri = ((OWLClass)type).getIRI();
					break;
				}
			if (typeIri != null)
				iri = typeIri;
		}
		
		result = findTemplate(iri);
		
		return (result != null) ? result.toString() : "{}";
	}
}

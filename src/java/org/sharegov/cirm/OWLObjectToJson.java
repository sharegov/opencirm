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


import static org.sharegov.cirm.OWL.allProperties;
import static org.sharegov.cirm.OWL.annotate;
import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.ontology;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLProperty;
import org.semanticweb.owlapi.model.OWLPropertyExpression;
import org.semanticweb.owlapi.util.ShortFormProvider;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.owl.OWLObjectPropertyCondition;
import org.sharegov.cirm.owl.OWLProtectedClassCache;
import org.sharegov.cirm.utils.GenUtils;
import static org.sharegov.cirm.utils.GenUtils.*;

/**
 * Converts OWL to JSON recursively.
 * Version 2 provides more comprehensive and thread safe use of a ShortFormProvider.
 * 
 * @author Thomas Hilpold
 *
 */
public class OWLObjectToJson implements OWLObjectMapper<Json>
{	
	public final static boolean STRICT_TYPES = false;
	private boolean  includeTypeInfo = false;
	private OWLObjectPropertyCondition stopExpansionCondition; //Must be thread safe
	
	private Set<String> includeTypeFor = new HashSet<String>();
	
	public boolean isIncludeTypeInfo()
	{
		return includeTypeInfo;
	}

	public void setIncludeTypeInfo(boolean includeTypeInfo)
	{
		this.includeTypeInfo = includeTypeInfo;
	}
	
	public OWLObjectToJson() {}
	
	public OWLObjectToJson(OWLObjectPropertyCondition stopExpansionCondition) 
	{
		if (stopExpansionCondition == null) throw new IllegalArgumentException();
		this.stopExpansionCondition = stopExpansionCondition;
	}
	
	/**
	 * If false only the iri and type information of an indivdual, which is a member of the Protected class will be serialized.
	 * In this case, all properties and annotations of a protected individual and individuals reachable only through a protected individuals object properties will be omitted.
	 * Default is true.
	 * @return
	 */
	public Set<String> getPropertiesToTypeDecorate() { return includeTypeFor; }
	
	
	@Override
	public Json map(OWLOntology ontology, OWLObject object, ShortFormProvider shortFormProvider) 
	{
		if (shortFormProvider == null) shortFormProvider = OWLObjectMapper.DEFAULT_SHORTFORM_PROVIDER;
		return toJSON(ontology, object, true, new HashSet<OWLObject>(), shortFormProvider);
	}

	@SuppressWarnings("unchecked")
	private OWLObjectMapper<Json> findObjectMapper(ShortFormProvider shortFormProvider, 
	                                                OWLOntology ontology, 
	                                                OWLNamedObject object, 
	                                                String mapPropertyName)
	{
		OWLNamedIndividual asIndividual = individual(object.getIRI());
		OWLNamedIndividual mapper = OWL.objectProperty(asIndividual, mapPropertyName); 
		if (mapper == null)
			return null;
		String classname = shortFormProvider.getShortForm(mapper);
		try
		{
			return (OWLObjectMapper<Json>)Class.forName(classname).newInstance();
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
	}

    private OWLPropertyMapper<Json> findPropertyMapper(OWLProperty prop)
    {
        OWLNamedIndividual asIndividual = individual(prop.getIRI());
        OWLNamedIndividual mapper = OWL.objectProperty(asIndividual, Refs.hasJsonMapper); 
        if (mapper == null)
            return null;
        String classname = mapper.getIRI().getFragment();
 
        try
        {
            @SuppressWarnings("unchecked")
            OWLPropertyMapper<Json> M = (OWLPropertyMapper<Json>)Class.forName(classname).newInstance();
            M.configure(OWL.toJSON(asIndividual));
            return M;
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }
	
	
	private Json toJSON(OWLOntology ontology, OWLProperty prop, OWLObject x, Set<OWLObject> done, ShortFormProvider shortFormProvider)
	{
		boolean expandIndividual =  (prop instanceof OWLObjectProperty)? 
				(stopExpansionCondition == null || !stopExpansionCondition.isMet(prop)) 
				: true;
		Json value = toJSON(ontology, x, expandIndividual, done, shortFormProvider);
		if (includeTypeInfo && this.includeTypeFor.contains(shortFormProvider.getShortForm(prop)))
		{
			if (prop instanceof OWLObjectProperty)
			{
				OWLClass type = OWL.getPropertyType(ontology, (OWLObjectProperty)prop);
				value = Json.object("type", type.getIRI().toString(), "literal", value);
			}
			else
			{
				OWLLiteral l = (OWLLiteral)x;	
				if (l.getDatatype().getIRI().equals(OWL2Datatype.XSD_DATE_TIME_STAMP.getIRI()))
					value = Json.make(GenUtils.formatDate(OWL.parseDate(l)));
				value = Json.object("type", l.getDatatype().getIRI().toString(), "literal", value);							
			}
		}
		else if (prop instanceof OWLDataProperty) 
		{
			OWLLiteral l = (OWLLiteral)x;	
			if (l.getDatatype().getIRI().equals(OWL2Datatype.XSD_DATE_TIME_STAMP.getIRI()))
				value = Json.make(GenUtils.formatDate(OWL.parseDate(l)));			
		}
		return value;
	}
	
	@SuppressWarnings("rawtypes")
	private Json toJSON(OWLOntology ontology, 
						OWLObject object, 
						boolean expandIfIndividual, 
						Set<OWLObject> done, 
						ShortFormProvider shortFormProvider)
	{
		if (object == null)
		{
			return Json.nil();
		}
		else if(done.contains(object))
		{
			 if (object instanceof OWLNamedIndividual) 
				 return Json.make(((OWLIndividual) object).asOWLNamedIndividual().getIRI().toString());
			 else if (object instanceof OWLLiteral) 
				 return Json.make(((OWLLiteral)object).getLiteral());			 
		}
		done.add(object);
		if (object instanceof OWLNamedIndividual)
		{
			OWLNamedIndividual ind = (OWLNamedIndividual)object;
			// 1) Check for a custom mapper to use for the individual
			Set<OWLClassExpression> classes = ind.getTypes(ontology.getImportsClosure());
			for (OWLClassExpression expr : classes)
			{
				if (! (expr instanceof OWLClass))
					continue;
				OWLClass type = (OWLClass)expr;
				OWLObjectMapper<Json> jsonMapper = findObjectMapper(shortFormProvider, ontology(), type, Refs.hasJsonMapper);
				if (jsonMapper != null)
					return jsonMapper.map(ontology, object, shortFormProvider);
			}
			Json result = Json.object();
			
			// 2) Find All Classes of the individual the type property will have the first one.
			boolean isProtectedIndividual = false;
			 //reasoner.getTypes(ind, true).getFlattened();
			if (!classes.isEmpty()) 
			{
				OWLProtectedClassCache protectedCache = Refs.protectedClassCache.resolve();
				// 2013.01.08 hilpold added multiple type support w protection
				Iterator<OWLClassExpression> it = classes.iterator();
				OWLClass firstClass = it.next().asOWLClass();
				String classIriFragment = shortFormProvider.getShortForm(firstClass);
				result.set("type", classIriFragment);
				isProtectedIndividual = protectedCache.isProtectedClass(firstClass);
				if (it.hasNext()) 
				{
					List<String> extendedClassesFragments = new LinkedList<String>();
					while (it.hasNext()) 
					{
						OWLClass curClass = it.next().asOWLClass();
						extendedClassesFragments.add(shortFormProvider.getShortForm(curClass));
						if (!isProtectedIndividual)
							isProtectedIndividual = protectedCache.isProtectedClass(curClass);
					}
					result.set("extendedTypes", Json.array(extendedClassesFragments));
					{
						//TODO hilpold think about what types, main type, et.c. in ontology, prefixes...
						String msg = "Encountered object with " + classes.size() + " types. First set as Json >type< property: " 
								+ firstClass 
								+ " \r\n of individual " + ind;
						//for (OWLClassExpression classExp : classes) {
						//msg += ("\r\n Classes:" + classExp.asOWLClass().getIRI().getFragment());
						//}
						if (STRICT_TYPES) 
							throw new IllegalArgumentException(msg);
						else if (dbg())
							System.err.println(this.getClass() + " " + msg + "\r\n Type " + 
							        shortFormProvider.getShortForm(firstClass) + "was added to JSON ");
					}
				}
				if (dbg()) System.out.println("Type: " + classIriFragment + " for " + ind);
				//3) Mark the Individual as protected, if it is.
				if (isProtectedIndividual)
				{
					result.set("transient$protected", Boolean.TRUE);
					if (dbg()) System.out.println("transient$protected: " + shortFormProvider.getShortForm(ind));
				}
			}
			result.set("iri", ind.getIRI().toString());	
			// Add the individuals Annotations 
			try
			{
				annotate(ind, result, shortFormProvider);
			}
			catch (Throwable t) 
			{ 
				System.err.println("Failed to annotate " +
					ind.getIRI() + " " + t.toString());
				t.printStackTrace(System.err);
			}
			// 3) All properties for the individual (recursion), if it's not protected and expandProtectedIndividuals false
			Json properties = Json.object();
			Map<OWLPropertyExpression<?,?>, Set<OWLObject>> props = allProperties(ind, ontology, true, true);
			for (Map.Entry<OWLPropertyExpression<?,?>, Set<OWLObject>> e : props.entrySet())
			{
				if (e.getKey() instanceof OWLProperty)
				{
					OWLProperty prop = (OWLProperty) e.getKey();
					Json value;
					OWLPropertyMapper<Json> jsonMapper = findPropertyMapper(prop);
					if (jsonMapper != null)
					    value = jsonMapper.map(ontology, ind, prop, e.getValue());
					else
					{
					    value = Json.array();
    					for (OWLObject x : e.getValue())
    					{
    						if (expandIfIndividual || prop instanceof OWLDataProperty)
    						{
    							value.add(toJSON(ontology, prop, x, done, shortFormProvider));
    						}
    						else
    						{
    							//iri only, but don't add to done set, it might be needed later; maybe do...
    							//done.add(x);
    							if (x instanceof OWLNamedIndividual) 
    								value.add(Json.make(((OWLIndividual) x).asOWLNamedIndividual().getIRI().toString()));
    						}
    					}
    					if (value.asJsonList().size() == 1)
    						value = value.at(0);
					}
					properties.set(shortFormProvider.getShortForm(prop), value);
				}
			}
			result.with(properties);
			if (!expandIfIndividual && dbg()) 
			{
				System.out.println("Not expanding object properties' objects for individual: " + ind);
			}
			return result;
		}
		else if (object instanceof OWLClass)
		{
			OWLClass cl = (OWLClass)object;
			Json result = Json.object().set("iri", cl.getIRI().toString());
			annotate(cl, result, shortFormProvider);
			return result;
		}
		else if (object instanceof OWLLiteral)
		{
			return Json.make(((OWLLiteral)object).getLiteral());
		} 
		else 
		{
			return new DefaultToJSONMapper().map(ontology, object, shortFormProvider);
		}
	}
}

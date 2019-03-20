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

import javax.ws.rs.*;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.sharegov.cirm.OWL;

import mjson.Json;
import static org.sharegov.cirm.OWL.*;

@Path("classes")
@Produces("application/json")
public class OWLTypes
{
	/**
	 * <p>
	 * 
	 * Retrieve all subclasses of the given parent class. The sub-classes
	 * are obtained from the top-level global ontology reasoner.
	 * </p>
	 *  
	 * @param parentId The name of the parent class, either full IRI or prefixed short
	 * form.
	 * @param direct Whether to include classes that are direct sub-classes only (true)
	 * or all possible infered sub-classes (false). 
	 * @return A JSON object with 3 properties: parentClass and direct (returning back the call parameters)
	 * and classes (an array of all sub-classes, JSON-serialized).
	 */
	@GET
	@Path("/sub/{parentClass}")
	public Json getClassList(@PathParam("parentClass") String parentId,
							 @QueryParam("direct") boolean direct) throws OWLException
	{
		Json result = Json.object();
		OWLClass parent = null;
		if(parentId.equals("Thing"))
			parent = OWL.owlClass(OWLRDFVocabulary.OWL_THING.getIRI());
		else
			parent = owlClass(fullIri(parentId));
		Set<OWLClass> S = reasoner(ontology()).getSubClasses(parent, direct).getFlattened();
		Json A = Json.array();
		for (OWLClass cl : S)
		{
			if (cl.equals(OWL.owlClass(OWLRDFVocabulary.OWL_NOTHING.getIRI())))
				continue;
			A.add(OWL.toJSON(cl));
		}
		result.set("parentClass", parentId)
			  .set("direct", direct)
			  .set("classes", A);
		
		return result;
	}
	
	/**
	 * Retrieve a complete description of a given class, including its domains, ranges
	 * etc.
	 * @param classname The name of the class, full IRI or a prefixed short form.
	 */
	@GET
	@Path("/describe/{classname}")
	public Json getOWLClass(@PathParam("classname") String classname) throws OWLException
	{
		Json top = Json.object();
		OWLOntology ontology = ontology();
		OWLClass cl = owlClass(fullIri(classname));
		for (OWLProperty<?,?> prop : OWL.getClassProperties(ontology, cl))
		{
			Json ranges = Json.array();
			for (OWLPropertyRange range : prop.getRanges(ontology))
			{
				if (range instanceof OWLEntity)
				{
					OWLEntity entity = (OWLEntity)range;
					if (entity.isOWLDatatype())
						ranges.add(entity.asOWLDatatype().toString());
					else if (entity.isOWLClass())
						ranges.add(entity.asOWLClass().toString());
				}else if(range instanceof OWLDataOneOf)
				{
					OWLDataOneOf oneOf = (OWLDataOneOf)range;
					Json options = Json.array();
					for(OWLLiteral c : oneOf.getValues())
					{
						options.add(c.getLiteral());
					}
					ranges.add(options);
				}
				else if(range instanceof OWLObjectOneOf)
				{
					OWLObjectOneOf oneOf = (OWLObjectOneOf)range;
					Json options = Json.array();
					for(OWLIndividual i : oneOf.getIndividuals())
					{
						options.add(i.asOWLNamedIndividual().getIRI().toQuotedString());
					}
					ranges.add(options);
				}
			}
			if (ranges.asJsonList().size() == 1)
				top.set(prop.getIRI().toString(), ranges.at(0));
			else if (ranges.asJsonList().size() > 1)
				top.set(prop.getIRI().toString(), ranges);
		}
		
		return top;
	}
}

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


import org.semanticweb.owlapi.model.OWLClass;

import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.util.ShortFormProvider;
import org.sharegov.cirm.owl.FragmentShortFormProvider;

import static org.sharegov.cirm.OWL.*;

import mjson.Json;

public class SubClassListToJSONMapper implements OWLObjectMapper<Json>
{

	@Override
	public Json map(OWLOntology ontology, OWLObject object, ShortFormProvider shortFormProvider) 
	{
		if (shortFormProvider == null) shortFormProvider = OWLObjectMapper.DEFAULT_SHORTFORM_PROVIDER;
		Json result = Json.array();
		OWLNamedIndividual ind = (OWLNamedIndividual)object;
		OWLNamedIndividual parentClass =  (OWLNamedIndividual)objectProperty(ind, Refs.hasParentClass);
		if (parentClass != null)
		{
			OWLClass cl = owlClass(parentClass.getIRI());
			for (OWLClassExpression expr : cl.getSubClasses(ontology()))
			{
				Json el;
				el = OWL.toJSON(object, shortFormProvider);
				if (el != null)
					result.add(el);
			}
		}
		return result;
	}
}

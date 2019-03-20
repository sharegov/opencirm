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


import java.util.Set;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.ShortFormProvider;
import org.sharegov.cirm.owl.FragmentShortFormProvider;

import static org.sharegov.cirm.OWL.*;
import mjson.Json;

public class ListToJSONMapper implements OWLObjectMapper<Json>
{

	@Override
	public Json map(OWLOntology ontology, OWLObject object, ShortFormProvider shortFormProvider) 
	{
		if (shortFormProvider == null) shortFormProvider = OWLObjectMapper.DEFAULT_SHORTFORM_PROVIDER;
		OWLReasoner reasoner = reasoner(ontology);
		Json A = Json.array();
		OWLObjectProperty hasContents = objectProperty(Refs.hasContents);
		OWLObjectProperty hasNext = objectProperty(Refs.hasNext);
		OWLNamedIndividual emptyList = individual(Refs.EmptyList);
		for (OWLNamedIndividual current = (OWLNamedIndividual)object; !emptyList.equals(current); )
		{
			Set<OWLNamedIndividual> S = reasoner.getObjectPropertyValues(current, hasContents).getFlattened();
			if (S.isEmpty())
				A.add(Json.nil());
			else
			{
				OWLIndividual single = S.iterator().next();
				A.add(OWL.toJSON(ontology, single, shortFormProvider));
			}
			S = reasoner.getObjectPropertyValues(current, hasNext).getFlattened();
			current = S.isEmpty() ? emptyList : S.iterator().next();
		}
		return A;
	}
}

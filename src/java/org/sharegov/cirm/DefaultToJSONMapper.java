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

import mjson.Json;

import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.util.ShortFormProvider;
import org.sharegov.cirm.owl.FragmentShortFormProvider;

public class DefaultToJSONMapper implements OWLObjectMapper<Json>
{

	@Override
	public Json map(OWLOntology ontology, OWLObject object, ShortFormProvider shortFormProvider) 
	{
		if (shortFormProvider == null) shortFormProvider = OWLObjectMapper.DEFAULT_SHORTFORM_PROVIDER;
		if (object instanceof OWLEntity)
			//TODO Entity vs NamedObject
			return Json.make(shortFormProvider.getShortForm((OWLEntity)object));
		else
			return Json.make(object.toString());
	}
}

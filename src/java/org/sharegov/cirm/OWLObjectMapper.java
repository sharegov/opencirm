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

import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.util.ShortFormProvider;
import org.sharegov.cirm.owl.FragmentShortFormProvider;

public interface OWLObjectMapper<T>
{
	public static final ShortFormProvider DEFAULT_SHORTFORM_PROVIDER = new FragmentShortFormProvider(); 
	
	/**
	 * Maps an object of the given ontology to T using the given shortformprovider for IRIs.
	 * @param ontology
	 * @param object
	 * @param shortFormProvider If <code>null</code> the default shortformprovider providing IRI fragments will be used.
	 * @return
	 */
	T map(OWLOntology ontology, OWLObject object, ShortFormProvider shortFormProvider);
}

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
package org.sharegov.cirm.event;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.owl.CachedReasoner;
import org.sharegov.cirm.rdb.RelationalOWLMapper;

public class ClearOWLEntityCache implements EventTrigger
{
	public void apply(OWLNamedIndividual entity, OWLNamedIndividual changeType, Json data)
	{
		synchronized(OWL.reasoner()) 
		{
			if (OWL.reasoner() instanceof CachedReasoner) 
			{
				CachedReasoner cr = (CachedReasoner) OWL.reasoner();
				//Thread safe call
				cr.clearCache();
				//TODO maybe other cached reasoners need to be cleared
				// those created by OWL.reasoner(bo) are expected to be collected with their temp manager
				// after a request.
				// Only one ref to OWL.reasoner(county) was found in case importer which should not cause 
				// problems.
			}
			Refs.owlJsonCache.resolve().clearAll();
			RelationalOWLMapper.getInstance().clearCache();
			Refs.ontologyTransformer.resolve().clearPredicateCache();
		}
	}
}

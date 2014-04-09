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
package org.sharegov.cirm.owl;

import java.util.Set;

import org.semanticweb.owlapi.model.OWLEntity;

public interface OWLEntityCondition {

	
	/**
	 * Checks, if the condition is satisfied by the given entity.
	 * 
	 * @param e
	 * @return
	 */
	boolean isMet(OWLEntity e);

	/**
	 * Checks, if the condition is satisfied by ALL given entities.
	 * 
	 * @param es
	 * @return
	 */
	boolean isMetByAll(Set<? extends OWLEntity> es);

	/**
	 * Checks, if the condition is satisfied by NONE of the given entities.
	 * 
	 * @param es
	 * @return
	 */
	boolean isMetByNone(Set<? extends OWLEntity> es);
}

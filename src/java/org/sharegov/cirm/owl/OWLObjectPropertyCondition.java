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

import java.util.Collections;

import java.util.Set;

import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLObjectProperty;

/**
 * Condition is met, if passed in entity is an OWLObjectyProperty that was used
 * during construction of this class.
 * 
 * This class is Thread Safe (Immutable, fast and no synchronization)
 * 
 * @author Thomas Hilpold
 * 
 */
public class OWLObjectPropertyCondition implements OWLEntityCondition
{

	private Set<OWLObjectProperty> entities;

	public OWLObjectPropertyCondition(OWLObjectProperty op)
	{
		if (op == null)
			throw new IllegalArgumentException();
		entities = Collections.<OWLObjectProperty> singleton(op);
	}

	/**
	 * 
	 * @param ops
	 *            set that should not be modified after passing it as parameter.
	 */
	public OWLObjectPropertyCondition(Set<OWLObjectProperty> ops)
	{
		if (ops == null)
			throw new IllegalArgumentException();
		entities = ops;
	}

	@Override
	public boolean isMet(OWLEntity e)
	{
		if (!(e instanceof OWLObjectProperty))
			return false;
		return entities.contains(e);
	}

	@Override
	public boolean isMetByAll(Set<? extends OWLEntity> es)
	{
		if (!(es.isEmpty())
				&& (!(es.iterator().next() instanceof OWLObjectProperty)))
			return false;
		return entities.containsAll(es);
	}

	@Override
	public boolean isMetByNone(Set<? extends OWLEntity> es)
	{
		for (OWLEntity cur : es)
		{
			if (isMet(cur))
				return false;
		}
		return true;
	}
}

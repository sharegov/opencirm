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
package org.sharegov.cirmx.maintenance;

import java.util.Set;

import org.eclipse.jetty.util.ConcurrentHashSet;
import org.semanticweb.owlapi.model.OWLEntity;
import org.sharegov.cirm.owl.OWLEntityCondition;

/**
 * Thread safe implementation of OWLEntityCondition
 * 
 * @author Thomas Hilpold
 *
 */
public class OWLEntityConditionImpl extends Object implements
		OWLEntityCondition
{

	final ConcurrentHashSet<OWLEntity> entities;
	
	public OWLEntityConditionImpl() {
		entities = new ConcurrentHashSet<OWLEntity>();
	}

	public OWLEntityConditionImpl(final Set<OWLEntity> entities) {
		this.entities = new ConcurrentHashSet<OWLEntity>();
		this.entities.addAll(entities);
	}
	
	@Override
	public boolean isMet(OWLEntity e)
	{
		return entities.contains(e);
	}

	@Override
	public boolean isMetByAll(Set<? extends OWLEntity> es)
	{
		for (OWLEntity e : es)
			if(!entities.contains(e)) return false;
		return true;
	}

	@Override
	public boolean isMetByNone(Set<? extends OWLEntity> es)
	{
		for (OWLEntity e : es)
			if(entities.contains(e)) return false;
		return true;
	}
	
	public Set<OWLEntity> getEntities()
	{
		return entities;
	}
}

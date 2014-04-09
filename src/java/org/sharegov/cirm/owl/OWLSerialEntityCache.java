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
import java.util.concurrent.ConcurrentHashMap;
import mjson.Json;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.utils.Ref;

/**
 * This caches everything because it is serving an application with close-ended set of
 * queries/individuals that it asks for. We are not caching operational data that grows
 * and grows, we are caching ontological data that kind of grows but not necessarily so much
 * and it may even shrink from time to time.
 * 
 * In case this turns out to be a memory hog, we have to implement an eviction policy of
 * course. 
 * 
 * @author boris
 *
 */
public class OWLSerialEntityCache
{
	ConcurrentHashMap<IRI, Ref<Json>> entityRefs = new ConcurrentHashMap<IRI, Ref<Json>>();
	ConcurrentHashMap<String, Ref<Json>> setRefs = new ConcurrentHashMap<String, Ref<Json>>();

	@SuppressWarnings("unchecked")
	abstract static class CachedRef<T> implements Ref<T>
	{
		static final Object nil = new Object();
		private T value = (T)nil;
		
		protected abstract T compute();
		
		public void clear() 
		{ 
			value = (T)nil; 
		}
		public T resolve() 
		{ 
			if (value == nil) 
			{
				value = compute();
			}
			return value; 
		}
	}
	
	class IndividualRef extends CachedRef<Json>
	{
		private IRI iri;
		public IndividualRef(IRI iri) { this.iri = iri; }
		public synchronized Json compute()
		{
			return OWL.toJSON(OWL.individual(iri));
		}
	}
	
	class SetRef extends CachedRef<Json>
	{
		String dlExpression;
		public SetRef(String dlExpression) { this.dlExpression = dlExpression; }		
		public synchronized Json compute()
		{
			Json json = Json.array();
			for (OWLNamedIndividual ind : OWL.queryIndividuals(dlExpression))
				json.add(OWL.toJSON(ind));
			return json;
		}
	}
	
	/**
	 * Obtain a set of individuals as described by a DL expression.
	 * 
	 * @param dlExpression
	 * @return
	 */
	public Ref<Json> set(String dlExpression)
	{
		Ref<Json> result = setRefs.get(dlExpression);
		if (result == null)
		{
			result = new SetRef(dlExpression);
			Ref<Json> x = setRefs.putIfAbsent(dlExpression, result);
			if (x != null)
				result = x;
		}
		return result;
	}
	
	/**
	 * Obtain the JSON serialized form of a given individual in the ontology.
	 * 
	 * @param iri
	 * @return
	 */
	public Ref<Json> individual(IRI iri)
	{
		Ref<Json> result = entityRefs.get(iri);
		if (result == null)
		{
			result = new IndividualRef(iri);
			Ref<Json> x = entityRefs.putIfAbsent(iri, result);
			if (x != null)
				result = x;
		}
		return result;
	}
	
	public void notify(Set<OWLOntologyChange> changes)
	{
		clearAll();
	}
	
	public void clearAll()
	{
		entityRefs.clear();
		setRefs.clear();
	}
}

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

import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hypergraphdb.util.Pair;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.sharegov.cirm.OWL;
import mjson.Json;
import static org.sharegov.cirm.OWL.*;

/**
 * <p>
 * A singleton that manages dispatching of events defined in the ontology. Events are dispatched
 * according to the type of entity they are about and the type of change. Each (entity type, change
 * type) combination pair can have a set of event triggers invoked to process the event.
 * </p>
 * 
 * <p>
 * A bit tired to think this through properly, but: the requirements of always having an entity
 * and a change type is a bit too restrictive for a general event dispatcher. Even if we do
 * have them, defining them in the ontology to do the proper (entity, change)->handler dispatch
 * is a bit cumbersome. Sometimes you just want to say: here fire this event and pass in that 
 * Json. So I've added a means to do that with the eventTriggers map and an overloaded dispatch
 * method that still takes an entity and change type as arguments because the EventTrigger interface
 * is defined that way.
 * </p>
 * @author boris
 *
 */
public class EventDispatcher
{
	static EventDispatcher instance = null;
	
	public synchronized static EventDispatcher get() 
	{
		if (instance != null)
			return instance;
		instance = new EventDispatcher();
		//OWLOntology O = OWL.ontology(Refs.topOntologyIRI.resolve());
		OWLReasoner R = reasoner();
		Set<OWLNamedIndividual> eventSet = R.getInstances(owlClass("EntityChangeEvent"), 
														  false).getFlattened();
		for (OWLNamedIndividual e : eventSet)
		{			
			// Each EntityChangeEvent is configured with the entity types that it should be
			// triggered for as a 'hasQueryExpression' data property, a list of implementation
			// singletons that should be instantiated and invoked as event triggers and a list
			// of changes that should trigger the event.
			Set<OWLClass> types = new HashSet<OWLClass>();
			for (OWLLiteral expr : reasoner().getDataPropertyValues(e, dataProperty("hasQueryExpression")))
			{
				String typeQuery = expr.getLiteral();
				types.addAll(querySubsumedClasses(typeQuery, ontology()));
			}
			if (types.isEmpty())
				types.add(dataFactory().getOWLThing());
			types.remove(dataFactory().getOWLNothing());
			Set<OWLNamedIndividual> triggers = OWL.objectProperties(e, "hasImplementation");
			for (OWLClass cl : types)
				for (OWLNamedIndividual chtype : OWL.objectProperties(e, "hasChangeType"))
					for (OWLNamedIndividual trigger : triggers)						
						instance.addTrigger(instance.changeTriggers, 
										new Pair<OWLClass, OWLNamedIndividual>(cl, chtype), trigger);
			for (OWLNamedIndividual trigger : triggers)
				instance.addTrigger(instance.eventTriggers, e, trigger);
		}
		return instance;
	}
	
	Map<Pair<OWLClass, OWLNamedIndividual>, List<EventTrigger>> changeTriggers = 
			new HashMap<Pair<OWLClass, OWLNamedIndividual>, List<EventTrigger>>();
	
	Map<OWLNamedIndividual, List<EventTrigger>> eventTriggers = 
			new HashMap<OWLNamedIndividual, List<EventTrigger>>();
	
	public <KeyType> EventDispatcher addTrigger(Map<KeyType, List<EventTrigger>> map,
												KeyType key,
												OWLNamedIndividual trigger)
	{
		List<EventTrigger> L = map.get(key);
		if (L == null)
		{
			L = new ArrayList<EventTrigger>();
			map.put(key, L);
		}
		try
		{
			EventTrigger t = (EventTrigger)Class.forName(trigger.getIRI().getFragment()).newInstance();
			L.add(t);
		}
		catch (Throwable t)
		{
			throw new RuntimeException(t);
		}
		return this;
	}

	/**
	 * <p>
	 * Dispatch a change event about a given individual calling only {@link EventTrigger}s for
	 * the specified type. 
	 * </p>
	 * 
	 * @param entity The individual that changed.
	 * @param entityType The type of interest - only event triggers associated with this type will be
	 * invoked even if the individual is also classified to be of other types.
	 * @param data An arbitrary piece of data to pass to the trigger.
	 * @param changeType The type of change.
	 * @return <code>this</code>
	 */
	public EventDispatcher dispatch(OWLNamedIndividual entity, 
									OWLClass entityType,
									Json data,
									OWLNamedIndividual changeType)
	{
		List<EventTrigger> L = changeTriggers.get(new Pair<OWLClass, OWLNamedIndividual>(entityType, changeType));
		if (L != null) for (EventTrigger trigger : L)
			trigger.apply(entity, changeType, data);
		return this;
	}

	public EventDispatcher dispatch(OWLNamedIndividual event, 
									OWLNamedIndividual entity, 
									OWLNamedIndividual changeType,
									Json data)
	{
		List<EventTrigger> L = eventTriggers.get(event);
		if (L != null) for (EventTrigger trigger : L)
			trigger.apply(entity, changeType, data);
		return this;
	}
	
	/**
	 * <p>
	 * Iteratively call <code>dispatch(entity, type, null, changeType)</code> for 
	 * each <code>type</code> the given <code>entity</code> is classified under.
	 * </p>
	 * @return <code>this</code>
	 */
	public EventDispatcher dispatch(OWLNamedIndividual entity, OWLNamedIndividual changeType)
	{
		//OWLOntology O = MetaService.get().getMetaOntology();
		OWLReasoner R = reasoner();
		for (OWLClass type : R.getTypes(entity, false).getFlattened())
		{
			dispatch(entity, type, null, changeType);
		}
		return this;
	}
}

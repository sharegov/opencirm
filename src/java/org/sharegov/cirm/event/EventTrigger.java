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

/**
 * <p>
 * An <code>EventTrigger</code> is something that processes an event dispatched to it
 * by the {@link EventDispatcher}. When an event is described in the ontology, one
 * of the properties is the <code>hasImplementation</code> property that points
 * to an <code>EventTrigger</code> implementing class. 
 * </p>
 * 
 * <p>
 * So "trigger" may seem a bit confusing in the name of this interface since the 
 * original trigger is whatever called the dispatcher. We probably should change
 * that name. It probably comes from the "historical" fact that the framework's
 * first kind of event was the client-push event where the trigger does indeed
 * push to the client. 
 * </p>
 * 
 * @author Borislav Iordanov
 *
 */
public interface EventTrigger
{
	void apply(OWLNamedIndividual entity, OWLNamedIndividual changeType, Json data);
}

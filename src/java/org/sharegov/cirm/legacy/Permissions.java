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
package org.sharegov.cirm.legacy;

import java.util.Collections;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.sharegov.cirm.OWL;
import static org.sharegov.cirm.OWL.*;

public class Permissions
{
	//This wont work: having a datafactory is a miracle at that point.
	public static final String PROTECTED_CLASS = "mdc:Protected";
	public static final OWLNamedIndividual BO_NEW = individual(fullIri("BO_New"));
	public static final OWLNamedIndividual BO_VIEW = individual(fullIri("BO_View"));
	public static final OWLNamedIndividual BO_UPDATE = individual(fullIri("BO_Update"));
	public static final OWLNamedIndividual BO_DELETE = individual(fullIri("BO_Delete"));
	
	public static boolean check(OWLNamedIndividual action, 
						 OWLNamedIndividual entity,
						 Set<OWLNamedIndividual> groups)
	{
		return getAllowedActions(action, entity, groups).contains(action);
	}

	public static Set<OWLNamedIndividual> getAllowedActions(OWLNamedIndividual action, OWLNamedIndividual entity, Set<OWLNamedIndividual> groups)
	{
		// Here's how the DL expression looks like:
		// inverse hasAction some (AccessPolicy and hasActor value
		// Animal_Services and hasObject value ASBITE)
		if (groups.isEmpty()) return Collections.<OWLNamedIndividual>emptySet();
		Set<OWLClassExpression> S = new HashSet<OWLClassExpression>();
		if (!groups.isEmpty())
		{
			for (OWLNamedIndividual group : groups)
				S.add(hasObject("hasActor", group));
			OWLClassExpression query = some(inverse("hasAction"),
					and(owlClass("AccessPolicy"), or(S.toArray(new OWLClassExpression[0])),
							hasObject("hasObject", entity))); 
			//System.out.println(query);
			Set<OWLNamedIndividual> allowed = reasoner().getInstances(query, false).getFlattened();
			return allowed;
		}
		else 
		{
			return Collections.<OWLNamedIndividual>emptySet();
		}
	}	

	/**
	 * e.g. inverse hasObject some (AccessPolicy and (hasActor value X or hasActor value Y) ) and ServiceCase
	 * @param action
	 * @param objectClass
	 * @param groups
	 * @return
	 */
	public static Set<OWLNamedIndividual> getAllowedObjectsOfClassOld(OWLNamedIndividual action, OWLClass objectClass, Set<OWLNamedIndividual> actors)
	{
		if (actors.isEmpty()) return Collections.<OWLNamedIndividual>emptySet();
		String dlQuery = constrainClause(action, actors);
		dlQuery += " and " + OWL.prefixedIRI(objectClass.getIRI());
		return OWL.queryIndividuals(dlQuery);
	}	

	/**
	 * Gets all individuals that are objects in any AccessPolicy for any given actors
	 * in the fastest known way (by elimitating adding a constrain clause to the reasoner query)
	 * @param action parameter is currently ignored
	 * @param objectClass
	 * @param actors
	 * @return
	 */
	public static Set<OWLNamedIndividual> getAllowedObjectsOfClass(OWLNamedIndividual action, OWLClass objectClass, Set<OWLNamedIndividual> actors)
	{
		//action param currently ignored
		if (objectClass == null) throw new NullPointerException();
		if (actors.isEmpty()) return Collections.<OWLNamedIndividual>emptySet();
		OWLOntology ontology = ontology();
		OWLReasoner reasoner = reasoner(ontology);
		Set<OWLNamedIndividual> allAllowed = null;
		if (reasoner.getSuperClasses(objectClass, false).containsEntity(owlClass("Protected")))
		{
			allAllowed = new HashSet<OWLNamedIndividual>();
			Set<OWLNamedIndividual> policies = policiesForActors(actors);
			for (OWLNamedIndividual x : policies)
			{
				Set<OWLNamedIndividual> allowedActions = OWL.objectProperties(x, "hasAction");
				if (allowedActions.contains(action)) {
					Set<OWLNamedIndividual> policyObjects = OWL.objectProperties(x, "hasObject");					
					allAllowed.addAll(policyObjects);
				}
			}
		}
		// The following is not a 100% test for protection. To be 100%, we'd have
		// to check that getInstances returns an empty set rather than getSubClasses, but
		// this will work in practice and it's a hopefully faster test
		else if (!reasoner.getSubClasses(and(objectClass, owlClass("Protected")), false).isBottomSingleton())
		{
			System.err.println("Permissions:getAllowedObjectsOfClass Unsafe Query for " + objectClass + " - Returning empty set.");
			return Collections.<OWLNamedIndividual>emptySet();
		}
		Set<OWLNamedIndividual> result = new HashSet<OWLNamedIndividual>();
		for (OWLNamedIndividual ind : reasoner.getInstances(objectClass, false).getFlattened())
		{
			if (allAllowed == null || allAllowed.contains(ind))
				result.add(ind);
		}
		return result;
	}

	/**
	 * Constrain for protected individuals. All non protected should be allowed.
	 * @param action
	 * @param actors
	 * @return
	 */
	public static OWLClassExpression constrain(OWLNamedIndividual action, Set<OWLNamedIndividual> actors)
	{
		Set<OWLClassExpression> S = new HashSet<OWLClassExpression>();
		if (!actors.isEmpty())
		{
			for (OWLNamedIndividual actor : actors)
				S.add(hasObject("hasActor", actor));
		}
		else
		{
			S.add(OWL.dataFactory().getOWLNothing());
		}
		return some(inverse("hasObject"),
					and(owlClass("AccessPolicy"), 
				         or(S.toArray(new OWLClassExpression[0]))));
	}
	
	/**
	 * Same as constrain but returns it as a String so it can be added to a Manchester
	 * syntax DL expression.
	 * 
	 * @param action
	 * @param actors
	 * @return
	 */
	public static String constrainClause(OWLNamedIndividual action, Set<OWLNamedIndividual> actors)
	{
		//Set<OWLClassExpression> S = new HashSet<OWLClassExpression>();
		StringBuilder or = new StringBuilder();
		if (!actors.isEmpty()) 
		{
			for (Iterator<OWLNamedIndividual> i = actors.iterator(); i.hasNext(); )
			{
				or.append("hasActor value " +  OWL.prefixedIRI(i.next().getIRI()));
				if (i.hasNext())
					or.append(" or ");
			}
		}
		else 
		{
			or.append("Nothing");
		}
		
		return " inverse hasObject some (AccessPolicy and (" + or + "))";
	}
	
	public static Set<OWLNamedIndividual> policiesForActors(Set<OWLNamedIndividual> actors)
	{
		StringBuilder or = new StringBuilder();
		if (!actors.isEmpty()) 
		{
			for (Iterator<OWLNamedIndividual> i = actors.iterator(); i.hasNext(); )
			{
				or.append("hasActor value " +  OWL.prefixedIRI(i.next().getIRI()));
				if (i.hasNext())
					or.append(" or ");
			}
		}
		else 
		{
			or.append("Nothing");
		}
		return OWL.queryIndividuals(or.toString());
	}
}

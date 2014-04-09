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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.owl.OWLEntityCondition;


/**
 * OntologyGraphSearch finds all axioms connected to a non-builtin entity (e.g. an SR) by depth first search from subject to object. 
 * No reasoner involved.
 * 
 * Connected means starting from the given non-builtin entity, all axioms are returned that can be reached by DFS.
 * Builtin Entities such as OWLDatatypes are not explored!
 * 
 * StopExpansionCondition may contain either NamedIndiviuals that should not be expanded, or ObjectProperties, whose objects should not be expanded. 
 * reverseTraversalObjectProperties will expand from object to subject.
 * 
 * @author Thomas Hilpold (CIAO/Miami-Dade County)
 * @created Oct 5, 2012
 */
public class OntologyGraphSearch {

	Map<OWLEntity, Set<OWLAxiom>> entityToAxioms;
	

	public List<OWLAxiom> findAllConnectedDFS(final OWLOntology onto, final OWLEntity e) {
		return findAllConnectedDFS(onto, e, new OWLEntityCondition()
		{
			@Override
			public boolean isMetByNone(Set<? extends OWLEntity> es)
			{
				return true;
			}
			
			@Override
			public boolean isMetByAll(Set<? extends OWLEntity> es)
			{
				return false;
			}
			
			@Override
			public boolean isMet(OWLEntity e)
			{
				return false;
			}
		}, new HashSet<OWLObjectProperty>());
	}
	
	/**
	 * Stops at Datatypes.
	 * @param onto
	 * @param i
	 * @return a list with unique axioms that are connected to e. (builtin entities ignored)
	 */
	public List<OWLAxiom> findAllConnectedDFS(final OWLOntology onto, final OWLEntity e, final OWLEntityCondition stopExpansionCondition, final Set<OWLObjectProperty> reverseTraversalObjectProperties) {
		prepareGraph(onto);
		Set<OWLEntity> exploredE = new HashSet<OWLEntity>(entityToAxioms.size());
		List<OWLAxiom> orderedResult = new ArrayList<OWLAxiom>(entityToAxioms.size()/2);
		findAllConnectedDFS(e, stopExpansionCondition, reverseTraversalObjectProperties, exploredE, orderedResult);
		return orderedResult;
	}

	
	protected void findAllConnectedDFS(final OWLEntity e, final OWLEntityCondition stopExpansionCondition, final Set<OWLObjectProperty> reverseTraversalObjectProperties, Set<OWLEntity> exploredE, List<OWLAxiom> result) {
		if (!shouldExplore(e, stopExpansionCondition)) return;
		exploredE.add(e);
		Set<OWLAxiom> eAxioms = entityToAxioms.get(e);
		if (eAxioms == null) 
			{
				System.err.println("findAllConnectedDFS: No axioms by entity: " + e.toStringID());
				return;
			}
		for (OWLAxiom ax : eAxioms) {
			if (ax.getAxiomType().equals(AxiomType.OBJECT_PROPERTY_ASSERTION))
			{
				OWLObjectPropertyAssertionAxiom oax = (OWLObjectPropertyAssertionAxiom)ax;
				//Expecting all named:
				OWLObjectProperty op = oax.getProperty().isAnonymous()? null : oax.getProperty().asOWLObjectProperty(); 
				if (oax.getSubject().equals(e))
				{
					if (!result.contains(ax)) result.add(ax);
					//else don't add OPA where e is object
					//Check if we shall traverse down this OPA to expand the object.
					OWLNamedIndividual object = (OWLNamedIndividual)((OWLObjectPropertyAssertionAxiom)ax).getObject();
					if (!stopExpansionCondition.isMet(object) && !stopExpansionCondition.isMet(op))
					{
						if (!exploredE.contains(object)) {
							findAllConnectedDFS(object, stopExpansionCondition, reverseTraversalObjectProperties, exploredE, result);
						}
					}
				} 
				else if (op != null && oax.getObject().equals(e) && reverseTraversalObjectProperties.contains(op))
				{
					OWLNamedIndividual subject = (OWLNamedIndividual)((OWLObjectPropertyAssertionAxiom)ax).getSubject();
					if (!exploredE.contains(subject)) {
						findAllConnectedDFS(subject, stopExpansionCondition, reverseTraversalObjectProperties, exploredE, result);
					}
				}
			}
			else
			{
				// add class assertions, data prop, et.c. for e.
				if (!result.contains(ax)) result.add(ax);					
			}					
		}
	}

	/**
	 * @param e
	 * @return
	 */
	public boolean shouldExplore(final OWLEntity e, final OWLEntityCondition stopExpansionCondition) {
		return !e.isBuiltIn() && e.isOWLNamedIndividual() 
				&& !stopExpansionCondition.isMet(e);
	}

	private void prepareGraph(OWLOntology onto) {
		Set<OWLAxiom> allAxioms = onto.getAxioms();
		entityToAxioms = new HashMap<OWLEntity, Set<OWLAxiom>>(allAxioms.size() * 2);
		for (OWLAxiom ax : allAxioms) {
			Set<OWLEntity> sig = ax.getSignature();
			for (OWLEntity e : sig) {
				Set<OWLAnnotationAssertionAxiom> annotations = e.getAnnotationAssertionAxioms(onto);
				Set<OWLAxiom> eAxioms = entityToAxioms.get(e);
				if (eAxioms == null) {
					eAxioms = new HashSet<OWLAxiom>();
					entityToAxioms.put(e, eAxioms);
				}
				eAxioms.add(ax);
				eAxioms.addAll(annotations);
			}
		}
	}
	public static Set<OWLIndividual> findSubjectsOfObject(OWLNamedIndividual ind, OWLOntology O)
	{
	    Set<OWLIndividual> result = new HashSet<OWLIndividual>();
	    for (OWLAxiom ax:ind.getReferencingAxioms(O))
	    {
	        if (! (ax instanceof OWLObjectPropertyAssertionAxiom)) continue;
	        OWLObjectPropertyAssertionAxiom oax = (OWLObjectPropertyAssertionAxiom)ax;
	        if (oax.getObject().equals(ind))
	            result.add(oax.getSubject());
	    }
	    return result;
	}
	
	public static Set<OWLIndividual> findObjectsOfSubject(OWLNamedIndividual ind, OWLOntology O)
	{
	    Set<OWLIndividual> result = new HashSet<OWLIndividual>();
	    for (OWLAxiom ax:ind.getReferencingAxioms(O))
	    {
	        if (! (ax instanceof OWLObjectPropertyAssertionAxiom)) continue;
	        OWLObjectPropertyAssertionAxiom oax = (OWLObjectPropertyAssertionAxiom)ax;
	        if (oax.getSubject().equals(ind))
	            result.add(oax.getObject());
	    }
	    return result;
	}
	
	public static void breadthFirstObjectGraph(OWLNamedIndividual ind, OWLOntology O)
	{
		Queue<OWLIndividual> toexplore = new LinkedList<OWLIndividual>();
		Set<OWLIndividual> examined = new HashSet<OWLIndividual>();
		toexplore.add(ind);
		    
	    while (!toexplore.isEmpty())
	    {
	        OWLIndividual next = toexplore.remove();
	        examined.add(next);
	        for (OWLIndividual x : findObjectsOfSubject((OWLNamedIndividual)next, O))
	        {
	            if (!examined.contains(x))
	            {
	                toexplore.add(x);
	            }
	        }
	    }		    
	    System.out.println(examined);
	}
	
	public static void main(String []argv)
	{
		StartUp.config.set("metaDatabaseLocation", "c:/temp/testontodb_clone");		
		breadthFirstObjectGraph(OWL.individual("legacy:COMPWPH"), OWL.ontology());
	}
}

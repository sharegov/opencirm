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
package org.sharegov.cirm.rdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hypergraphdb.app.owl.util.StopWatch;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLMutableOntology;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLPropertyExpression;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.owl.Model;
import org.sharegov.cirm.utils.DBGUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import uk.ac.manchester.cs.owl.owlapi.OWLOntologyImpl;

/**
 * Transforms an ontology for generic storage optimization purposes.
 * 
 * Currently only hasServiceAnswer AI1 -> hasServiceField, hasAnswerValue | hasAnswerObject is supported.
 * 
 * @author Thomas Hilpold, Abbas Syed
 */
public class OntologyTransformer
{
	/**
	 * Triggers output of axiom numbers before and after transform/inverse transform.
	 */
	public static boolean DBG = false;

	/**
	 * Triggers full functional printout of original and transformed (save)/inverse transformed (load) ontologies
	 */
	public static boolean DBGX = false;

	/**
	 * This will print a trace whenever a class assertion is removed during transform (e.g. for a temp0..N individual.)
	 */
	public static boolean DBGX_CLASS_ASSERTION_REMOVE = true;

	public static OWLObjectProperty objectPropertyToCollapse = OWL.objectProperty(Model.legacy("hasServiceAnswer"));  
	public static OWLObjectProperty objectSubPropertyForOptimizedPredicate = OWL.objectProperty(Model.legacy("hasServiceField"));  
	public static OWLObjectProperty objectSubPropertyForOptimizedObject = OWL.objectProperty(Model.legacy("hasAnswerObject"));  
	public static OWLDataProperty dataSubPropertyForOptimizedValue = OWL.dataProperty(Model.legacy("hasAnswerValue"));  
	public static OWLClass optimizedPredicateClassification = OWL.owlClass(Model.legacy("ServiceField"));
	
	private volatile Set<OWLNamedIndividual> optimizedPredicates;
	
	public OntologyTransformer()
	{
		initOptimizedPredicates();
	}
	
	private synchronized void initOptimizedPredicates() 
	{
		StopWatch s = new StopWatch();
		s.start();
		System.out.print("Start caching all " + optimizedPredicateClassification);
		optimizedPredicates = OWL.reasoner().getInstances(optimizedPredicateClassification, false).getFlattened();
		s.stop("Done in ");
		System.out.println("Storage optimized predicates: " + optimizedPredicates.size());
	}
	
	/**
	 * Clears the meta dependent predicate cache.
	 */
	public synchronized void clearPredicateCache() 
	{
		optimizedPredicates = null;
	}
	
	/**
	 * Refreshes the meta dependent predicate cache.
	 */
	protected synchronized void refreshPredicateCache() 
	{
		if (optimizedPredicates != null) 
		{
			clearPredicateCache();
		}
		initOptimizedPredicates();
	}
	
	public synchronized boolean isOptimizedPredicate(OWLNamedIndividual predicateInd) 
	{
		return optimizedPredicates.contains(predicateInd);
	}

	/**
	 * Transforms 3 axioms into 1 for objectPropertyToCollapse.
	 * indX ax1 indY, indY ax2 indZ1, indY dataPropertySubAx3 litZ2 -> X dataPropertyAxIRIZ1 litZ2
	 * indX ax1 indY, indY ax2 indZ1, indY objectPropertySubAx3 indZ3 -> X objectPropertyAxIRIZ1 indZ3
	 * 
	 * This will modify the given ontology directly, without going through the OwlOntologyManager.
	 * @return an optimized copy of o for storage purposes only.
	 */
	public OWLOntology transform(OWLOntology o) 
	{
		if (optimizedPredicates == null) refreshPredicateCache();
		int oldAxiomNr = o.getAxiomCount();
		OWLDataFactory factory = o.getOWLOntologyManager().getOWLDataFactory();
		OWLMutableOntology newMo = copyOntology(o);
		if (o.getAxiomCount() != oldAxiomNr) throw new IllegalArgumentException("Wrong copy");
		// e.g. BO hasServiceAnswer temp0, 
		Set<OWLObjectPropertyAssertionAxiom> collapsibleOPAxioms = getCollapsibleObjectPropertiesAxioms(o);
		for (OWLObjectPropertyAssertionAxiom oax : collapsibleOPAxioms) 
		{ //TODO hilpold: do NOT use OWL datafactory below, use the one the onto provides.
			List<OWLOntologyChange> transformationChanges = new ArrayList<OWLOntologyChange>();
			OWLIndividual newSubject = oax.getSubject();
			//e.g. serviceAnswer1 hasServiceField serviceQuestion1 -> (serviceAnswer1 hasAnswerObject ind1, serviceAnswer1 hasAnswerObject ind2, ...) 
			Map<OWLObjectPropertyAssertionAxiom, Set<OWLObjectPropertyAssertionAxiom>> axForNewPredicateToSetofSubObjectAx = getObjectSubPropertyAxiomForOptimizedPredicate(o, oax.getObject());
			OWLObjectPropertyAssertionAxiom axForNewPredicate = axForNewPredicateToSetofSubObjectAx.keySet().iterator().next();
			IRI newPredicateIRI = axForNewPredicate.getObject().asOWLNamedIndividual().getIRI();
			OWLObjectProperty newPredicateObjectProperty = factory.getOWLObjectProperty(newPredicateIRI);
			//Create one collapsed Object Property assertion ax per OWLObjectPropertyAssertionAxiom.
			Set<OWLObjectPropertyAssertionAxiom> objectSubPropertyAxSet = axForNewPredicateToSetofSubObjectAx.values().iterator().next();  
			for (OWLObjectPropertyAssertionAxiom oldOPAx: objectSubPropertyAxSet) 
			{
				transformationChanges.add(new AddAxiom(newMo, factory.getOWLObjectPropertyAssertionAxiom(newPredicateObjectProperty, newSubject, oldOPAx.getObject())));
				transformationChanges.add(new RemoveAxiom(newMo, oldOPAx));
				
			}
			//Create one collapsed Data Property assertion ax per Literal.
			Set<OWLDataPropertyAssertionAxiom> dataSubPropertyAxSet = getDataSubPropertyAxiomForOptimizedPredicate(o, oax.getObject());  
			OWLDataProperty newPredicateDataProperty = factory.getOWLDataProperty(newPredicateIRI);
			for (OWLDataPropertyAssertionAxiom oldDPAx: dataSubPropertyAxSet)
			{
				transformationChanges.add(new AddAxiom(newMo, factory.getOWLDataPropertyAssertionAxiom(newPredicateDataProperty, newSubject, oldDPAx.getObject())));
				transformationChanges.add(new RemoveAxiom(newMo, oldDPAx));
			}
			if (dataSubPropertyAxSet.isEmpty() && objectSubPropertyAxSet.isEmpty()) 
			{
				throw new IllegalArgumentException("Cannot collapse " + objectPropertyToCollapse + " for " + axForNewPredicate + " because no subaxioms were found.");
			}
			transformationChanges.add(new RemoveAxiom(newMo, axForNewPredicate)); //e.g.
			transformationChanges.add(new RemoveAxiom(newMo, oax));
			//hilpold 2014.04.08 do not let store save unnecessary class assertions for individuals we won't save (temp 0..n)
			//Remove class assertions for oax objects (temp0, ..., tempN)
			OWLIndividual removableObject = oax.getObject();
			for (OWLAxiom removableClassAssertion : newMo.getClassAssertionAxioms(removableObject)) 
			{
				transformationChanges.add(new RemoveAxiom(newMo, removableClassAssertion)); //e.g. temp0 isClass ServiceAnswer
				if(DBGX_CLASS_ASSERTION_REMOVE) 
				{
					String ontoId = (!o.isAnonymous() && o.getOntologyID() != null)? "" + o.getOntologyID().getOntologyIRI() : "anonymous";
					ThreadLocalStopwatch.getWatch().time("OntologyTransformer removed a class assertion: " + removableClassAssertion.toString() + " from " + ontoId);
				}
			}
			//we do that here to make the ontology smaller while we transform.			
			newMo.applyChanges(transformationChanges);
		}
		if (DBG) 
		{ 
			int newAxiomNr = newMo.getAxiomCount();
			ThreadLocalStopwatch.getWatch().time("Transforming ontology from " + oldAxiomNr + " to " + newAxiomNr + " (" + (oldAxiomNr - newAxiomNr) + " removed Ax)");
		}
		if (DBGX) 
		{
			DBGUtils.printOntologyFunctional(o);
			DBGUtils.printOntologyFunctional(newMo);
		}
		if(oldAxiomNr != o.getAxiomCount())
			throw new IllegalStateException("Modified original ontology.");
		return newMo;
	}
	
	/**
	 * Created a new Ontology with all axioms of the given o and an equal ID.
	 * The new Axioms will be added without using an owlontologymanager. (Reasoner not notified).
	 * @param o
	 * @return an ontology with o's manager.
	 */
	protected OWLMutableOntology copyOntology(OWLOntology o) 
	{
		String copiedIri = o.isAnonymous() ? 
				o.getOntologyID().toString() : o.getOntologyID().getOntologyIRI().toString();
		copiedIri += "-TRANSFORMED";
		IRI copiedOntoIRI = IRI.create(copiedIri);
		OWLOntologyID copyOntoID = new OWLOntologyID(copiedOntoIRI);
		OWLMutableOntology newMo = new OWLOntologyImpl(o.getOWLOntologyManager(), copyOntoID);
		Set<OWLAxiom> oAxioms = o.getAxioms();
		List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
		//Copy all axioms to new mo
		for (OWLAxiom ax : oAxioms) 
		{
			changes.add(new AddAxiom(newMo, ax));
		}
		newMo.applyChanges(changes);
		return newMo;
	}
	
	/**
	 * returns all referencing axioms of the objectPropertyToCollapse (e.g. hasServiceAnswer)
	 * e.g. BO hasServiceAnswer temp0, BO hasServiceAnswer temp1, et.c.
	 * @param o
	 * @return
	 */
	protected Set<OWLObjectPropertyAssertionAxiom> getCollapsibleObjectPropertiesAxioms(OWLOntology o) 
	{
		Set<OWLAxiom> original = o.getReferencingAxioms(objectPropertyToCollapse);
		Set<OWLObjectPropertyAssertionAxiom> filtered = new HashSet<OWLObjectPropertyAssertionAxiom>();
		for (OWLAxiom ax : original) 
		{
			if (ax instanceof OWLObjectPropertyAssertionAxiom) 
			{
				filtered.add((OWLObjectPropertyAssertionAxiom)ax);
			}
		}
		return filtered;
	}

	protected Map<OWLObjectPropertyAssertionAxiom, Set<OWLObjectPropertyAssertionAxiom>> getObjectSubPropertyAxiomForOptimizedPredicate(OWLOntology o, OWLIndividual subject) 
	{
		//e.g. 1 serviceAnswerX hasServiceField serviceQuestion  AND 0..* serviceAnswerX hasAnswerObject choiceValueYN
		OWLObjectPropertyAssertionAxiom objectSubPropertyForOptimizedPredicateAxiom = null;
		Set<OWLObjectPropertyAssertionAxiom> subjectAxioms = o.getObjectPropertyAssertionAxioms(subject);
		Iterator<OWLObjectPropertyAssertionAxiom> axIt = subjectAxioms.iterator();
		//modify subjectAxioms set to only contain objectSubPropertyForOptimizedObject axioms (e.g. hasAnswerObject), if any.
		while (axIt.hasNext()) 
		{
			OWLObjectPropertyAssertionAxiom curAx = axIt.next();
			if (curAx.getProperty().equals(objectSubPropertyForOptimizedPredicate)) 
			{
				objectSubPropertyForOptimizedPredicateAxiom = curAx;
				axIt.remove();
			} else if (!curAx.getProperty().equals(objectSubPropertyForOptimizedObject)) 
			{
				axIt.remove();
			}
		}
		if (objectSubPropertyForOptimizedPredicateAxiom == null) 
		{
			throw new IllegalStateException("objectSubPropertyForOptimizedPredicateAxiom not found for subject " + subject);
		}
		return Collections.singletonMap(objectSubPropertyForOptimizedPredicateAxiom, subjectAxioms);
	}

	protected Set<OWLDataPropertyAssertionAxiom> getDataSubPropertyAxiomForOptimizedPredicate(OWLOntology o, OWLIndividual subject) 
	{
		//e.g. serviceAnswerX hasAnswerValue literalYN
		Set<OWLDataPropertyAssertionAxiom> subjectAxioms = o.getDataPropertyAssertionAxioms(subject);
		Iterator<OWLDataPropertyAssertionAxiom> axIt = subjectAxioms.iterator();
		//modify subjectAxioms set to only contain objectSubPropertyForOptimizedObject axioms (e.g. hasAnswerObject), if any.
		while (axIt.hasNext()) 
		{
			OWLDataPropertyAssertionAxiom curAx = axIt.next();
			if (!curAx.getProperty().equals(dataSubPropertyForOptimizedValue)) 
			{
				axIt.remove();
			}
		}
		return subjectAxioms;
	}
	
//	protected OWLNamedIndividual getObjectSubPropertyForOptimizedPredicate(OWLOntology o, OWLIndividual subject) 
//	{
//		Set<OWLIndividual> predicateSet = subject.getObjectPropertyValues(objectSubPropertyForOptimizedPredicate, o);
//		if (predicateSet.size() != 1) {
//			throw new IllegalStateException("The ontology " + o +  "is expected to have exactly one " 
//					+ objectSubPropertyForOptimizedPredicate + " asserted for " + subject + ", was: " + predicateSet.size());
//		}
//		return  (OWLNamedIndividual) predicateSet.iterator().next();
//	}

//	/**
//	 * If ObjectProperty (CharMult, CharOpt questions)
//	 * @param o
//	 * @param subject
//	 * @return
//	 */
//	protected Set<OWLNamedIndividual> getAnswerObjectIndividuals(OWLOntology o, OWLIndividual subject) 
//	{
//		Set<OWLIndividual> objectInds = subject.getObjectPropertyValues(objectSubPropertyForOptimizedObject, o);
//		Set<OWLNamedIndividual> objectNamedInds = new HashSet<OWLNamedIndividual>(objectInds.size() * 2);
//		for (OWLIndividual objectInd : objectInds) {
//			if (!(objectInd instanceof OWLNamedIndividual)) {
//				throw new IllegalStateException("Found an unexpected anonymous individual. " + objectInd + " for " + objectSubPropertyForOptimizedObject + " and " + subject);
//		 	} else {
//		 		objectNamedInds.add((OWLNamedIndividual) objectInd);
//		 	}
//		}
//		return objectNamedInds;
//	}
	
	/**
	 * Reversing an optimized ontology. Changes applied throug manager to notify reasoner.
	 * 
	 * Transforms 1 axiom into 3 for objectPropertyToCollapse.
	 * X dataPropertyAxZ1 Z2 -> indX ax1 indY ax2 indZ1, indY dataPropertySubAx3 indZ2
	 * X objectPropertyAxZ1 Z2 -> indX ax1 indY ax2 indZ1, indY objectPropertySubAx3 litZ2 
	 */
	public synchronized void reverseTransform(OWLOntology o) 
	{
		if (DBGX) DBGUtils.printOntologyFunctional(o);
		if (optimizedPredicates == null) refreshPredicateCache();
		int newAxiomNr = o.getAxiomCount();
		int tempIndividualCounter = 0;
		List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
		OWLOntologyManager man = o.getOWLOntologyManager();
		OWLDataFactory factory = man.getOWLDataFactory();
		OWLObjectProperty objectPropertyToCollapseMem = objectPropertyToCollapse;
		OWLObjectProperty objectSubPropertyForOptimizedPredicateMem = objectSubPropertyForOptimizedPredicate;
		OWLDataProperty dataSubPropertyForOptimizedValueMem = dataSubPropertyForOptimizedValue;
		OWLObjectProperty objectSubPropertyForOptimizedObjectMem = objectSubPropertyForOptimizedObject;
		List<OWLPropertyAssertionAxiom<? extends OWLPropertyExpression<?,?>, ?>> propertyAssertionAxioms;
		Map<OWLNamedIndividual, OWLNamedIndividual> predicateToAnonymousIndividual = new HashMap<OWLNamedIndividual, OWLNamedIndividual>(newAxiomNr * 2);
		Set<OWLDataPropertyAssertionAxiom> dpaSet = o.getAxioms(AxiomType.DATA_PROPERTY_ASSERTION);
		Set<OWLObjectPropertyAssertionAxiom> opaSet = o.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION);
		propertyAssertionAxioms = new ArrayList<OWLPropertyAssertionAxiom<? extends OWLPropertyExpression<?,?>,?>>(dpaSet.size() + opaSet.size());
		propertyAssertionAxioms.addAll(dpaSet);
		propertyAssertionAxioms.addAll(opaSet);
		for(OWLPropertyAssertionAxiom<? extends OWLPropertyExpression<?,?>, ?> pa: propertyAssertionAxioms)
		{
			//Example: predicateToCompare will be a ServiceQuestion individual used as a OWLDataProperty when collapsed.
			OWLNamedIndividual predicateToCompare = factory.getOWLNamedIndividual(((OWLNamedObject)pa.getProperty()).getIRI()); 
			if(optimizedPredicates.contains(predicateToCompare))
			{
				 //TODO one Anonymous for all choicevalues!! or no?
				OWLIndividual sr = pa.getSubject();
				OWLNamedIndividual serviceAnswer = predicateToAnonymousIndividual.get(predicateToCompare);
				if (serviceAnswer == null) 
				{
					serviceAnswer = factory.getOWLNamedIndividual(OWL.fullIri("temp" + tempIndividualCounter++));			
					//axiom #1
					OWLObjectPropertyAssertionAxiom hasServiceAnswer = factory.getOWLObjectPropertyAssertionAxiom(objectPropertyToCollapseMem, sr, serviceAnswer);
					//axiom #2
					OWLObjectPropertyAssertionAxiom hasServiceField = factory.getOWLObjectPropertyAssertionAxiom(objectSubPropertyForOptimizedPredicateMem, serviceAnswer, predicateToCompare);
					changes.add(new AddAxiom(o, hasServiceAnswer));
					changes.add(new AddAxiom(o, hasServiceField));
					predicateToAnonymousIndividual.put(predicateToCompare, serviceAnswer);
				}
				OWLPropertyAssertionAxiom<? extends OWLPropertyExpression<?,?>, ?> hasAnswer;
				if (pa instanceof OWLDataPropertyAssertionAxiom) 
				{
					//axiom #3 for Data Property Assertion
					OWLLiteral literal = (OWLLiteral)pa.getObject();
					hasAnswer = factory.getOWLDataPropertyAssertionAxiom(dataSubPropertyForOptimizedValueMem, serviceAnswer, literal);
				} 
				else 
				{
					//axiom #3 for Object Property Assertion
					OWLNamedIndividual individual = (OWLNamedIndividual)pa.getObject();
					hasAnswer = factory.getOWLObjectPropertyAssertionAxiom(objectSubPropertyForOptimizedObjectMem, serviceAnswer, individual);
				}
				changes.add(new AddAxiom(o, hasAnswer));
				changes.add(new RemoveAxiom(o, pa));
			}
		}
		man.applyChanges(changes);
		if (DBG) 
		{ 
			int reversedAxiomNr = o.getAxiomCount();
			System.out.println("Reverse Transforming ontology from " + newAxiomNr + " to " + reversedAxiomNr + " (" + (reversedAxiomNr - newAxiomNr) + " added Ax)");
		}
		if (DBGX) DBGUtils.printOntologyFunctional(o);
	}
	
	public boolean isTransformProperty(OWLObjectProperty property)
	{
		return objectPropertyToCollapse.equals(property);
	}
}
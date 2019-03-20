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

import java.util.List;
import java.util.Set;

import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.reasoner.AxiomNotInProfileException;
import org.semanticweb.owlapi.reasoner.BufferingMode;
import org.semanticweb.owlapi.reasoner.ClassExpressionNotInProfileException;
import org.semanticweb.owlapi.reasoner.FreshEntitiesException;
import org.semanticweb.owlapi.reasoner.FreshEntityPolicy;
import org.semanticweb.owlapi.reasoner.InconsistentOntologyException;
import org.semanticweb.owlapi.reasoner.IndividualNodeSetPolicy;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.ReasonerInterruptedException;
import org.semanticweb.owlapi.reasoner.TimeOutException;
import org.semanticweb.owlapi.reasoner.UnsupportedEntailmentTypeException;
import org.semanticweb.owlapi.util.Version;

/**
 * A Thread safe but slow Reasoner wrapper synchronizing all method calls.
 * 
 * @author Thomas Hilpold
 * 
 */
public class SynchronizedReasoner implements OWLReasoner, Wrapper<OWLReasoner>
{
	private OWLReasoner reasoner;

	public static SynchronizedReasoner synchronizedReasoner(OWLReasoner reasoner)
	{
		if (reasoner instanceof SynchronizedReasoner)
		{
			throw new IllegalStateException(
					"sychronized Reasoner called with already synchronized reasoner: "
							+ reasoner);
		}
		return new SynchronizedReasoner(reasoner);
	}

	SynchronizedReasoner(OWLReasoner reasoner)
	{
		this.reasoner = reasoner;
	}

	public synchronized String getReasonerName()
	{
		return reasoner.getReasonerName();
	}

	public synchronized Version getReasonerVersion()
	{
		return reasoner.getReasonerVersion();
	}

	public synchronized BufferingMode getBufferingMode()
	{
		return reasoner.getBufferingMode();
	}

	public synchronized void flush()
	{
		reasoner.flush();
	}

	public synchronized List<OWLOntologyChange> getPendingChanges()
	{
		return reasoner.getPendingChanges();
	}

	public synchronized Set<OWLAxiom> getPendingAxiomAdditions()
	{
		return reasoner.getPendingAxiomAdditions();
	}

	public synchronized Set<OWLAxiom> getPendingAxiomRemovals()
	{
		return reasoner.getPendingAxiomRemovals();
	}

	public synchronized OWLOntology getRootOntology()
	{
		return reasoner.getRootOntology();
	}

	public synchronized void interrupt()
	{
		reasoner.interrupt();
	}

	public synchronized void precomputeInferences(
			InferenceType... inferenceTypes)
			throws ReasonerInterruptedException, TimeOutException,
			InconsistentOntologyException
	{
		reasoner.precomputeInferences(inferenceTypes);
	}

	public synchronized boolean isPrecomputed(InferenceType inferenceType)
	{
		return reasoner.isPrecomputed(inferenceType);
	}

	public synchronized Set<InferenceType> getPrecomputableInferenceTypes()
	{
		return reasoner.getPrecomputableInferenceTypes();
	}

	public synchronized boolean isConsistent()
			throws ReasonerInterruptedException, TimeOutException
	{
		return reasoner.isConsistent();
	}

	public synchronized boolean isSatisfiable(OWLClassExpression classExpression)
			throws ReasonerInterruptedException, TimeOutException,
			ClassExpressionNotInProfileException, FreshEntitiesException,
			InconsistentOntologyException
	{
		return reasoner.isSatisfiable(classExpression);
	}

	public synchronized Node<OWLClass> getUnsatisfiableClasses()
			throws ReasonerInterruptedException, TimeOutException,
			InconsistentOntologyException
	{
		return reasoner.getUnsatisfiableClasses();
	}

	public synchronized boolean isEntailed(OWLAxiom axiom)
			throws ReasonerInterruptedException,
			UnsupportedEntailmentTypeException, TimeOutException,
			AxiomNotInProfileException, FreshEntitiesException,
			InconsistentOntologyException
	{
		return reasoner.isEntailed(axiom);
	}

	public synchronized boolean isEntailed(Set<? extends OWLAxiom> axioms)
			throws ReasonerInterruptedException,
			UnsupportedEntailmentTypeException, TimeOutException,
			AxiomNotInProfileException, FreshEntitiesException,
			InconsistentOntologyException
	{
		return reasoner.isEntailed(axioms);
	}

	public synchronized boolean isEntailmentCheckingSupported(
			AxiomType<?> axiomType)
	{
		return reasoner.isEntailmentCheckingSupported(axiomType);
	}

	public synchronized Node<OWLClass> getTopClassNode()
	{
		return reasoner.getTopClassNode();
	}

	public synchronized Node<OWLClass> getBottomClassNode()
	{
		return reasoner.getBottomClassNode();
	}

	public synchronized NodeSet<OWLClass> getSubClasses(OWLClassExpression ce,
			boolean direct) throws ReasonerInterruptedException,
			TimeOutException, FreshEntitiesException,
			InconsistentOntologyException, ClassExpressionNotInProfileException
	{
		return reasoner.getSubClasses(ce, direct);
	}

	public synchronized NodeSet<OWLClass> getSuperClasses(
			OWLClassExpression ce, boolean direct)
			throws InconsistentOntologyException,
			ClassExpressionNotInProfileException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		return reasoner.getSuperClasses(ce, direct);
	}

	public synchronized Node<OWLClass> getEquivalentClasses(
			OWLClassExpression ce) throws InconsistentOntologyException,
			ClassExpressionNotInProfileException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		return reasoner.getEquivalentClasses(ce);
	}

	public synchronized NodeSet<OWLClass> getDisjointClasses(
			OWLClassExpression ce) throws ReasonerInterruptedException,
			TimeOutException, FreshEntitiesException,
			InconsistentOntologyException
	{
		return reasoner.getDisjointClasses(ce);
	}

	public synchronized Node<OWLObjectPropertyExpression> getTopObjectPropertyNode()
	{
		return reasoner.getTopObjectPropertyNode();
	}

	public synchronized Node<OWLObjectPropertyExpression> getBottomObjectPropertyNode()
	{
		return reasoner.getBottomObjectPropertyNode();
	}

	public synchronized NodeSet<OWLObjectPropertyExpression> getSubObjectProperties(
			OWLObjectPropertyExpression pe, boolean direct)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		return reasoner.getSubObjectProperties(pe, direct);
	}

	public synchronized NodeSet<OWLObjectPropertyExpression> getSuperObjectProperties(
			OWLObjectPropertyExpression pe, boolean direct)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		return reasoner.getSuperObjectProperties(pe, direct);
	}

	public synchronized Node<OWLObjectPropertyExpression> getEquivalentObjectProperties(
			OWLObjectPropertyExpression pe)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		return reasoner.getEquivalentObjectProperties(pe);
	}

	public synchronized NodeSet<OWLObjectPropertyExpression> getDisjointObjectProperties(
			OWLObjectPropertyExpression pe)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		return reasoner.getDisjointObjectProperties(pe);
	}

	public synchronized Node<OWLObjectPropertyExpression> getInverseObjectProperties(
			OWLObjectPropertyExpression pe)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		return reasoner.getInverseObjectProperties(pe);
	}

	public synchronized NodeSet<OWLClass> getObjectPropertyDomains(
			OWLObjectPropertyExpression pe, boolean direct)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		return reasoner.getObjectPropertyDomains(pe, direct);
	}

	public synchronized NodeSet<OWLClass> getObjectPropertyRanges(
			OWLObjectPropertyExpression pe, boolean direct)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		return reasoner.getObjectPropertyRanges(pe, direct);
	}

	public synchronized Node<OWLDataProperty> getTopDataPropertyNode()
	{
		return reasoner.getTopDataPropertyNode();
	}

	public synchronized Node<OWLDataProperty> getBottomDataPropertyNode()
	{
		return reasoner.getBottomDataPropertyNode();
	}

	public synchronized NodeSet<OWLDataProperty> getSubDataProperties(
			OWLDataProperty pe, boolean direct)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		return reasoner.getSubDataProperties(pe, direct);
	}

	public synchronized NodeSet<OWLDataProperty> getSuperDataProperties(
			OWLDataProperty pe, boolean direct)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		return reasoner.getSuperDataProperties(pe, direct);
	}

	public synchronized Node<OWLDataProperty> getEquivalentDataProperties(
			OWLDataProperty pe) throws InconsistentOntologyException,
			FreshEntitiesException, ReasonerInterruptedException,
			TimeOutException
	{
		return reasoner.getEquivalentDataProperties(pe);
	}

	public synchronized NodeSet<OWLDataProperty> getDisjointDataProperties(
			OWLDataPropertyExpression pe) throws InconsistentOntologyException,
			FreshEntitiesException, ReasonerInterruptedException,
			TimeOutException
	{
		return reasoner.getDisjointDataProperties(pe);
	}

	public synchronized NodeSet<OWLClass> getDataPropertyDomains(
			OWLDataProperty pe, boolean direct)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		return reasoner.getDataPropertyDomains(pe, direct);
	}

	public synchronized NodeSet<OWLClass> getTypes(OWLNamedIndividual ind,
			boolean direct) throws InconsistentOntologyException,
			FreshEntitiesException, ReasonerInterruptedException,
			TimeOutException
	{
		return reasoner.getTypes(ind, direct);
	}

	public synchronized NodeSet<OWLNamedIndividual> getInstances(
			OWLClassExpression ce, boolean direct)
			throws InconsistentOntologyException,
			ClassExpressionNotInProfileException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		return reasoner.getInstances(ce, direct);
	}

	public synchronized NodeSet<OWLNamedIndividual> getObjectPropertyValues(
			OWLNamedIndividual ind, OWLObjectPropertyExpression pe)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		return reasoner.getObjectPropertyValues(ind, pe);
	}

	public synchronized Set<OWLLiteral> getDataPropertyValues(
			OWLNamedIndividual ind, OWLDataProperty pe)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		return reasoner.getDataPropertyValues(ind, pe);
	}

	public synchronized Node<OWLNamedIndividual> getSameIndividuals(
			OWLNamedIndividual ind) throws InconsistentOntologyException,
			FreshEntitiesException, ReasonerInterruptedException,
			TimeOutException
	{
		return reasoner.getSameIndividuals(ind);
	}

	public synchronized NodeSet<OWLNamedIndividual> getDifferentIndividuals(
			OWLNamedIndividual ind) throws InconsistentOntologyException,
			FreshEntitiesException, ReasonerInterruptedException,
			TimeOutException
	{
		return reasoner.getDifferentIndividuals(ind);
	}

	public synchronized long getTimeOut()
	{
		return reasoner.getTimeOut();
	}

	public synchronized FreshEntityPolicy getFreshEntityPolicy()
	{
		return reasoner.getFreshEntityPolicy();
	}

	public synchronized IndividualNodeSetPolicy getIndividualNodeSetPolicy()
	{
		return reasoner.getIndividualNodeSetPolicy();
	}

	public synchronized void dispose()
	{
		reasoner.dispose();
	}

	@Override
	public OWLReasoner unwrap() {
		return reasoner;
	}

	/**
	 * This method exposes the wrapped reasoner for situations where
	 * implementation specific reasoner functionality is needed. It's use is
	 * highly discouraged. If you do, always synchronize using the synchronized
	 * reasoner object as lock, if you have to call reasoner methods directly.
	 * 
	 * @return
	 */
	@SuppressWarnings("unchecked")
	@Override
	public OWLReasoner unwrapAll() {
		if (reasoner instanceof Wrapper<?>) 
		{
			return ((Wrapper<OWLReasoner>) reasoner).unwrapAll();
		}
		else
		{
			return reasoner;
		}
	}
}

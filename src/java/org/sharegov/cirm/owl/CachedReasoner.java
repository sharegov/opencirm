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
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import mjson.Json;

import org.hypergraphdb.util.Pair;
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
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.util.Version;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.utils.DLQueryParser;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;


import uk.ac.manchester.cs.owl.owlapi.mansyntaxrenderer.ManchesterOWLSyntaxOWLObjectRendererImpl;

/**
 * A non blocking thread safe cache as wrapper for reasoner queries. Caches
 * results of most commonly used reasoner methods in the Cirm system and
 * provides them in a non blocking way.
 * 
 * A Read/Write lock is used for each cached method.
 * 
 * Not all methods are cached.
 * 
 * @author Thomas Hilpold
 */
public class CachedReasoner implements OWLReasoner, Wrapper<OWLReasoner>
{

	public static volatile boolean DBG_CACHE_MISS = false;
	public static final int INITIAL_CAPACITY_PER_CACHE = 1000;
	public static final float LOAD_FACTOR_PER_CACHE = 0.5f;
	public static final int CONCURRENCY_PER_CACHE = 50;

	
	private OWLReasoner reasoner;
	
	private volatile String getReasonerNameCache = null;
	private volatile Version getReasonerVersionChache = null;
	private volatile BufferingMode getBufferingModeCache = null;
	private volatile OWLOntology getRootOntologyCache = null;
	
	private volatile Node<OWLClass> getTopClassNodeCache = null;

	private volatile Node<OWLClass> getBottomClassNodeCache = null;
	
	private ConcurrentHashMap<Pair<Boolean, OWLClassExpression>, NodeSet<OWLClass>> getSubClassesCache = new ConcurrentHashMap<Pair<Boolean, OWLClassExpression>, NodeSet<OWLClass>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);

	private ConcurrentHashMap<Pair<Boolean, OWLClassExpression>, NodeSet<OWLClass>> getSuperClassesCache = new ConcurrentHashMap<Pair<Boolean, OWLClassExpression>, NodeSet<OWLClass>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);

	private ConcurrentHashMap<OWLClassExpression, Node<OWLClass>> getEquivalentClassesCache = new ConcurrentHashMap<OWLClassExpression, Node<OWLClass>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);

	private ConcurrentHashMap<OWLClassExpression, NodeSet<OWLClass>> getDisjointClassesCache = new ConcurrentHashMap<OWLClassExpression, NodeSet<OWLClass>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);

	private volatile Node<OWLObjectPropertyExpression> getTopObjectPropertyNodeCache = null;

	private volatile Node<OWLObjectPropertyExpression> getBottomObjectPropertyNodeCache = null;


	private ConcurrentHashMap<Pair<Boolean, OWLObjectPropertyExpression>, NodeSet<OWLObjectPropertyExpression>> getSubObjectPropertiesCache = new ConcurrentHashMap<Pair<Boolean, OWLObjectPropertyExpression>, NodeSet<OWLObjectPropertyExpression>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);

	private ConcurrentHashMap<Pair<Boolean, OWLObjectPropertyExpression>, NodeSet<OWLObjectPropertyExpression>> getSuperObjectPropertiesCache = new ConcurrentHashMap<Pair<Boolean, OWLObjectPropertyExpression>, NodeSet<OWLObjectPropertyExpression>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);


	private ConcurrentHashMap<OWLObjectPropertyExpression, Node<OWLObjectPropertyExpression>> getEquivalentObjectPropertiesCache = new ConcurrentHashMap<OWLObjectPropertyExpression, Node<OWLObjectPropertyExpression>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);

	private ConcurrentHashMap<OWLObjectPropertyExpression, NodeSet<OWLObjectPropertyExpression>> getDisjointObjectPropertiesCache = new ConcurrentHashMap<OWLObjectPropertyExpression, NodeSet<OWLObjectPropertyExpression>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);

	private ConcurrentHashMap<OWLObjectPropertyExpression, Node<OWLObjectPropertyExpression>> getInverseObjectPropertiesCache = new ConcurrentHashMap<OWLObjectPropertyExpression, Node<OWLObjectPropertyExpression>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);


	private ConcurrentHashMap<Pair<Boolean, OWLObjectPropertyExpression>, NodeSet<OWLClass>> getObjectPropertyDomainsCache = new ConcurrentHashMap<Pair<Boolean, OWLObjectPropertyExpression>, NodeSet<OWLClass>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);


	private ConcurrentHashMap<Pair<Boolean, OWLObjectPropertyExpression>, NodeSet<OWLClass>> getObjectPropertyRangesCache = new ConcurrentHashMap<Pair<Boolean, OWLObjectPropertyExpression>, NodeSet<OWLClass>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);

	private volatile Node<OWLDataProperty> getTopDataPropertyNodeCache = null;
	
	private volatile Node<OWLDataProperty> getBottomDataPropertyNodeCache = null;
	
	private ConcurrentHashMap<Pair<Boolean, OWLDataProperty>, NodeSet<OWLDataProperty>> getSubDataPropertiesCache = new ConcurrentHashMap<Pair<Boolean, OWLDataProperty>, NodeSet<OWLDataProperty>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);


	private ConcurrentHashMap<Pair<Boolean, OWLDataProperty>, NodeSet<OWLDataProperty>> getSuperDataPropertiesCache = new ConcurrentHashMap<Pair<Boolean, OWLDataProperty>, NodeSet<OWLDataProperty>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);

	private ConcurrentHashMap<OWLDataProperty, Node<OWLDataProperty>> getEquivalentDataPropertiesCache = new ConcurrentHashMap<OWLDataProperty, Node<OWLDataProperty>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);

	
	private ConcurrentHashMap<OWLDataPropertyExpression, NodeSet<OWLDataProperty>> getDisjointDataPropertiesCache = new ConcurrentHashMap<OWLDataPropertyExpression, NodeSet<OWLDataProperty>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);

	
	private ConcurrentHashMap<Pair<Boolean, OWLDataProperty>, NodeSet<OWLClass>> getDataPropertyDomainsCache = new ConcurrentHashMap<Pair<Boolean, OWLDataProperty>, NodeSet<OWLClass>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);
	
	private ConcurrentHashMap<Pair<Boolean, OWLNamedIndividual>, NodeSet<OWLClass>> getTypesCache = new ConcurrentHashMap<Pair<Boolean, OWLNamedIndividual>, NodeSet<OWLClass>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);

	private ConcurrentHashMap<Pair<Boolean, OWLClassExpression>, NodeSet<OWLNamedIndividual>> getInstancesCache = new ConcurrentHashMap<Pair<Boolean, OWLClassExpression>, NodeSet<OWLNamedIndividual>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);

	private ConcurrentHashMap<Pair<OWLNamedIndividual, OWLObjectPropertyExpression>, NodeSet<OWLNamedIndividual>> getObjectPropertyValuesCache = new ConcurrentHashMap<Pair<OWLNamedIndividual, OWLObjectPropertyExpression>, NodeSet<OWLNamedIndividual>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);

	private ConcurrentHashMap<Pair<OWLNamedIndividual, OWLDataProperty>, Set<OWLLiteral>> getDataPropertyValuesCache = new ConcurrentHashMap<Pair<OWLNamedIndividual, OWLDataProperty>, Set<OWLLiteral>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);


	private ConcurrentHashMap<OWLNamedIndividual, Node<OWLNamedIndividual>> getSameIndividualsCache = new ConcurrentHashMap<OWLNamedIndividual, Node<OWLNamedIndividual>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);

	private ConcurrentHashMap<OWLNamedIndividual, NodeSet<OWLNamedIndividual>> getDifferentIndividualsCache = new ConcurrentHashMap<OWLNamedIndividual, NodeSet<OWLNamedIndividual>>(
			INITIAL_CAPACITY_PER_CACHE, LOAD_FACTOR_PER_CACHE,
			CONCURRENCY_PER_CACHE);
	
	private volatile Long getTimeOutCache = null;
	
	private volatile FreshEntityPolicy getFreshEntityPolicyCache = null;
	
	private volatile IndividualNodeSetPolicy getIndividualNodeSetPolicyCache = null;
	
	
	/**
	 * Creates a new cached reasoner wrapping the synchronized reasoner.
	 * 
	 * @param reasoner
	 * @return
	 */
	public static CachedReasoner cachedReasoner(SynchronizedReasoner reasoner)
	{
		return new CachedReasoner(reasoner);
	}

	/**
	 * @param reasoner
	 *            a synchronized reasoner
	 */
	private CachedReasoner(SynchronizedReasoner reasoner)
	{
		this.reasoner = reasoner;
	}

	public String getReasonerName()
	{
		if (getReasonerNameCache == null)
			synchronized (reasoner)
			{
				if (getReasonerNameCache == null)
					getReasonerNameCache = reasoner.getReasonerName();
			}
		return getReasonerNameCache;
	}

	public Version getReasonerVersion()
	{
		if (getReasonerVersionChache == null)
			synchronized (reasoner)
			{
				if (getReasonerVersionChache == null)
					getReasonerVersionChache = reasoner.getReasonerVersion();
			}
		return getReasonerVersionChache;
	}

	public BufferingMode getBufferingMode()
	{
		if (getBufferingModeCache == null)
			synchronized (reasoner)
			{
				if (getBufferingModeCache == null)
					getBufferingModeCache = reasoner.getBufferingMode();
			}
		return getBufferingModeCache;
	}

	public void flush()
	{
		reasoner.flush();
	}

	public List<OWLOntologyChange> getPendingChanges()
	{
		return reasoner.getPendingChanges();
	}

	public Set<OWLAxiom> getPendingAxiomAdditions()
	{
		return reasoner.getPendingAxiomAdditions();
	}

	public Set<OWLAxiom> getPendingAxiomRemovals()
	{
		return reasoner.getPendingAxiomRemovals();
	}

	public OWLOntology getRootOntology()
	{
		if (getRootOntologyCache == null)
			synchronized (reasoner)
			{
				if (getRootOntologyCache == null)
					getRootOntologyCache = reasoner.getRootOntology();
			}
		return getRootOntologyCache;
	}

	public void interrupt()
	{
		reasoner.interrupt();
	}

	public void precomputeInferences(InferenceType... inferenceTypes)
			throws ReasonerInterruptedException, TimeOutException,
			InconsistentOntologyException
	{
		reasoner.precomputeInferences(inferenceTypes);
	}

	public boolean isPrecomputed(InferenceType inferenceType)
	{
		return reasoner.isPrecomputed(inferenceType);
	}

	public Set<InferenceType> getPrecomputableInferenceTypes()
	{
		return reasoner.getPrecomputableInferenceTypes();
	}

	public boolean isConsistent() throws ReasonerInterruptedException,
			TimeOutException
	{
		return reasoner.isConsistent();
	}

	public boolean isSatisfiable(OWLClassExpression classExpression)
			throws ReasonerInterruptedException, TimeOutException,
			ClassExpressionNotInProfileException, FreshEntitiesException,
			InconsistentOntologyException
	{
		return reasoner.isSatisfiable(classExpression);
	}

	public Node<OWLClass> getUnsatisfiableClasses()
			throws ReasonerInterruptedException, TimeOutException,
			InconsistentOntologyException
	{
		return reasoner.getUnsatisfiableClasses();
	}

	public boolean isEntailed(OWLAxiom axiom)
			throws ReasonerInterruptedException,
			UnsupportedEntailmentTypeException, TimeOutException,
			AxiomNotInProfileException, FreshEntitiesException,
			InconsistentOntologyException
	{
		return reasoner.isEntailed(axiom);
	}

	public boolean isEntailed(Set<? extends OWLAxiom> axioms)
			throws ReasonerInterruptedException,
			UnsupportedEntailmentTypeException, TimeOutException,
			AxiomNotInProfileException, FreshEntitiesException,
			InconsistentOntologyException
	{
		return reasoner.isEntailed(axioms);
	}

	public boolean isEntailmentCheckingSupported(AxiomType<?> axiomType)
	{
		return reasoner.isEntailmentCheckingSupported(axiomType);
	}

	public Node<OWLClass> getTopClassNode()
	{
		if (getTopClassNodeCache == null)
		{
			synchronized (reasoner)
			{
				if (getTopClassNodeCache == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getTopClassNode ");
					getTopClassNodeCache = reasoner.getTopClassNode();
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getTopClassNode Done.");
				}
			}
		}
		return getTopClassNodeCache;
	}

	public Node<OWLClass> getBottomClassNode()
	{
		Node<OWLClass> result = getBottomClassNodeCache;
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getBottomClassNodeCache;
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getBottomClassNode ");
					result = reasoner.getBottomClassNode();
					getBottomClassNodeCache = result;
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getBottomClassNode Done.");
				}
			}
		}
		return result;
	}

	public NodeSet<OWLClass> getSubClasses(OWLClassExpression ce, boolean direct)
			throws ReasonerInterruptedException, TimeOutException,
			FreshEntitiesException, InconsistentOntologyException,
			ClassExpressionNotInProfileException
	{
		Pair<Boolean, OWLClassExpression> candidate = new Pair<Boolean, OWLClassExpression>(
				direct, ce);
		NodeSet<OWLClass> result = getSubClassesCache.get(candidate);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getSubClassesCache.get(candidate);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getSubClasses " + ce);
					result = reasoner.getSubClasses(ce, direct);
					getSubClassesCache.putIfAbsent(candidate, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getSubClasses Done.");
				}
			}
		}
		return result;
	}

	public NodeSet<OWLClass> getSuperClasses(OWLClassExpression ce,
			boolean direct) throws InconsistentOntologyException,
			ClassExpressionNotInProfileException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		Pair<Boolean, OWLClassExpression> candidate = new Pair<Boolean, OWLClassExpression>(
				direct, ce);
		NodeSet<OWLClass> result = getSuperClassesCache.get(candidate);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getSuperClassesCache.get(candidate);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getSuperClasses " + ce);
					result = reasoner.getSuperClasses(ce, direct);
					getSuperClassesCache.putIfAbsent(candidate, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getSuperClasses Done.");
				}
			}
		}
		return result;
	}

	public Node<OWLClass> getEquivalentClasses(OWLClassExpression ce)
			throws InconsistentOntologyException,
			ClassExpressionNotInProfileException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		Node<OWLClass> result = getEquivalentClassesCache.get(ce);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getEquivalentClassesCache.get(ce);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getEquivalentClasses " + ce);
					result = reasoner.getEquivalentClasses(ce);
					getEquivalentClassesCache.putIfAbsent(ce, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getEquivalentClasses Done.");
				}
			}
		}
		return result;
	}

	public NodeSet<OWLClass> getDisjointClasses(OWLClassExpression ce)
			throws ReasonerInterruptedException, TimeOutException,
			FreshEntitiesException, InconsistentOntologyException
	{
		NodeSet<OWLClass> result = getDisjointClassesCache.get(ce);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getDisjointClassesCache.get(ce);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getDisjointClasses " + ce);
					result = reasoner.getDisjointClasses(ce);
					getDisjointClassesCache.putIfAbsent(ce, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getDisjointClasses Done.");
				}
			}
		}
		return result;
	}

	public Node<OWLObjectPropertyExpression> getTopObjectPropertyNode()
	{
		Node<OWLObjectPropertyExpression> result = getTopObjectPropertyNodeCache;
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getTopObjectPropertyNodeCache;
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getTopObjectPropertyNode ");
					result = reasoner.getTopObjectPropertyNode();
					getTopObjectPropertyNodeCache = result;
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getTopObjectPropertyNode Done.");
				}
			}
		}
		return result;
	}

	public Node<OWLObjectPropertyExpression> getBottomObjectPropertyNode()
	{
		Node<OWLObjectPropertyExpression> result = getBottomObjectPropertyNodeCache;
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getBottomObjectPropertyNodeCache;
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getBottomObjectPropertyNode ");
					result = reasoner.getBottomObjectPropertyNode();
					getBottomObjectPropertyNodeCache = result;
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getBottomObjectPropertyNode Done.");
				}
			}
		}
		return result;
	}

	public NodeSet<OWLObjectPropertyExpression> getSubObjectProperties(
			OWLObjectPropertyExpression pe, boolean direct)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		Pair<Boolean, OWLObjectPropertyExpression> candidate = new Pair<Boolean, OWLObjectPropertyExpression>(
				direct, pe);
		NodeSet<OWLObjectPropertyExpression> result = getSubObjectPropertiesCache.get(candidate);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getSubObjectPropertiesCache.get(candidate);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getSubObjectProperties " + direct + " " + pe);
					result = reasoner.getSubObjectProperties(pe, direct);
					getSubObjectPropertiesCache.putIfAbsent(candidate, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getSubObjectProperties Done.");
				}
			}
		}
		return result;
	}

	public NodeSet<OWLObjectPropertyExpression> getSuperObjectProperties(
			OWLObjectPropertyExpression pe, boolean direct)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		Pair<Boolean, OWLObjectPropertyExpression> candidate = new Pair<Boolean, OWLObjectPropertyExpression>(
				direct, pe);
		NodeSet<OWLObjectPropertyExpression> result = getSuperObjectPropertiesCache.get(candidate);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getSuperObjectPropertiesCache.get(candidate);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getSuperObjectProperties " + direct + " " + pe);
					result = reasoner.getSuperObjectProperties(pe, direct);
					getSuperObjectPropertiesCache.putIfAbsent(candidate, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getSuperObjectProperties Done.");
				}
			}
		}
		return result;
	}

	public Node<OWLObjectPropertyExpression> getEquivalentObjectProperties(
			OWLObjectPropertyExpression pe)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		Node<OWLObjectPropertyExpression> result = getEquivalentObjectPropertiesCache.get(pe);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getEquivalentObjectPropertiesCache.get(pe);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getEquivalentObjectProperties " + pe);
					result = reasoner.getEquivalentObjectProperties(pe);
					getEquivalentObjectPropertiesCache.putIfAbsent(pe, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getEquivalentObjectProperties Done.");
				}
			}
		}
		return result;
	}

	public NodeSet<OWLObjectPropertyExpression> getDisjointObjectProperties(
			OWLObjectPropertyExpression pe)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		NodeSet<OWLObjectPropertyExpression> result = getDisjointObjectPropertiesCache.get(pe);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getDisjointObjectPropertiesCache.get(pe);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getDisjointObjectProperties " + pe);
					result = reasoner.getDisjointObjectProperties(pe);
					getDisjointObjectPropertiesCache.putIfAbsent(pe, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getDisjointObjectProperties Done.");
				}
			}
		}
		return result;
	}

	public Node<OWLObjectPropertyExpression> getInverseObjectProperties(
			OWLObjectPropertyExpression pe)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		Node<OWLObjectPropertyExpression> result = getInverseObjectPropertiesCache.get(pe);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getInverseObjectPropertiesCache.get(pe);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getInverseObjectProperties " + pe);
					result = reasoner.getInverseObjectProperties(pe);
					getInverseObjectPropertiesCache.putIfAbsent(pe, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getInverseObjectProperties Done.");
				}
			}
		}
		return result;
	}

	public NodeSet<OWLClass> getObjectPropertyDomains(
			OWLObjectPropertyExpression pe, boolean direct)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		Pair<Boolean, OWLObjectPropertyExpression> candidate = new Pair<Boolean, OWLObjectPropertyExpression>(
				direct, pe);
		NodeSet<OWLClass> result = getObjectPropertyDomainsCache.get(candidate);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getObjectPropertyDomainsCache.get(candidate);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getObjectPropertyDomains " + direct + " " + pe);
					result = reasoner.getObjectPropertyDomains(pe, direct);
					getObjectPropertyDomainsCache.putIfAbsent(candidate, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getObjectPropertyDomains Done.");
				}
			}
		}
		return result;
	}

	public NodeSet<OWLClass> getObjectPropertyRanges(
			OWLObjectPropertyExpression pe, boolean direct)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		Pair<Boolean, OWLObjectPropertyExpression> candidate = new Pair<Boolean, OWLObjectPropertyExpression>(
				direct, pe);
		NodeSet<OWLClass> result = getObjectPropertyRangesCache.get(candidate);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getObjectPropertyRangesCache.get(candidate);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getObjectPropertyRanges " + direct + " " + pe);
					result = reasoner.getObjectPropertyRanges(pe, direct);
					getObjectPropertyRangesCache.putIfAbsent(candidate, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getObjectPropertyRanges Done.");
				}
			}
		}
		return result;
	}

	public Node<OWLDataProperty> getTopDataPropertyNode()
	{
		if (getTopDataPropertyNodeCache == null)
			synchronized (reasoner)
			{
				if (getTopDataPropertyNodeCache == null)
					getTopDataPropertyNodeCache = reasoner.getTopDataPropertyNode();
			}
		return getTopDataPropertyNodeCache;
	}

	public Node<OWLDataProperty> getBottomDataPropertyNode()
	{
		if (getBottomDataPropertyNodeCache == null)
			synchronized (reasoner)
			{
				if (getBottomDataPropertyNodeCache == null)
					getBottomDataPropertyNodeCache = reasoner.getBottomDataPropertyNode();
			}
		return getBottomDataPropertyNodeCache;
	}

	public NodeSet<OWLDataProperty> getSubDataProperties(OWLDataProperty pe,
			boolean direct) throws InconsistentOntologyException,
			FreshEntitiesException, ReasonerInterruptedException,
			TimeOutException
	{
		Pair<Boolean, OWLDataProperty> candidate = new Pair<Boolean, OWLDataProperty>(
				direct, pe);
		NodeSet<OWLDataProperty> result = getSubDataPropertiesCache.get(candidate);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getSubDataPropertiesCache.get(candidate);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getSubDataProperties " + pe);
					result = reasoner.getSubDataProperties(pe, direct);
					getSubDataPropertiesCache.putIfAbsent(candidate, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getSubDataProperties Done.");
				}
			}
		}
		return result;
	}

	public NodeSet<OWLDataProperty> getSuperDataProperties(OWLDataProperty pe,
			boolean direct) throws InconsistentOntologyException,
			FreshEntitiesException, ReasonerInterruptedException,
			TimeOutException
	{
		Pair<Boolean, OWLDataProperty> candidate = new Pair<Boolean, OWLDataProperty>(
				direct, pe);
		NodeSet<OWLDataProperty> result = getSuperDataPropertiesCache.get(candidate);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getSuperDataPropertiesCache.get(candidate);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getSuperDataProperties " + pe);
					result = reasoner.getSuperDataProperties(pe, direct);
					getSuperDataPropertiesCache.putIfAbsent(candidate, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getSuperDataProperties Done.");
				}
			}
		}
		return result;
	}

	public Node<OWLDataProperty> getEquivalentDataProperties(OWLDataProperty pe)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		Node<OWLDataProperty> result = getEquivalentDataPropertiesCache.get(pe);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getEquivalentDataPropertiesCache.get(pe);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getEquivalentDataProperties " + pe);
					result = reasoner.getEquivalentDataProperties(pe);
					getEquivalentDataPropertiesCache.putIfAbsent(pe, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getEquivalentDataProperties Done.");
				}
			}
		}
		return result;
	}

	public NodeSet<OWLDataProperty> getDisjointDataProperties(
			OWLDataPropertyExpression pe) throws InconsistentOntologyException,
			FreshEntitiesException, ReasonerInterruptedException,
			TimeOutException
	{
		NodeSet<OWLDataProperty> result = getDisjointDataPropertiesCache.get(pe);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getDisjointDataPropertiesCache.get(pe);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getDisjointDataProperties " + pe);
					result = reasoner.getDisjointDataProperties(pe);
					getDisjointDataPropertiesCache.putIfAbsent(pe, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getDisjointDataProperties Done.");
				}
			}
		}
		return result;
	}

	public NodeSet<OWLClass> getDataPropertyDomains(OWLDataProperty pe,
			boolean direct) throws InconsistentOntologyException,
			FreshEntitiesException, ReasonerInterruptedException,
			TimeOutException
	{
		Pair<Boolean, OWLDataProperty> candidate = new Pair<Boolean, OWLDataProperty>(
				direct, pe);
		NodeSet<OWLClass> result = getDataPropertyDomainsCache.get(candidate);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getDataPropertyDomainsCache.get(candidate);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getDataPropertyDomains " + pe);
					result = reasoner.getDataPropertyDomains(pe, direct);
					getDataPropertyDomainsCache.putIfAbsent(candidate, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getDataPropertyDomains Done.");
				}
			}
		}
		return result;
	}

	/**
	 * Cached.
	 */
	public NodeSet<OWLClass> getTypes(OWLNamedIndividual ind, boolean direct)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		Pair<Boolean, OWLNamedIndividual> candidate = new Pair<Boolean, OWLNamedIndividual>(
				direct, ind);
		NodeSet<OWLClass> result = getTypesCache.get(candidate);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getTypesCache.get(candidate);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getTypes " + ind);
					result = reasoner.getTypes(ind, direct);
					getTypesCache.putIfAbsent(candidate, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getTypes Done.");
				}
			}
		}
		return result;
	}

	/**
	 * Cached.
	 */
	public NodeSet<OWLNamedIndividual> getInstances(OWLClassExpression ce,
			boolean direct) throws InconsistentOntologyException,
			ClassExpressionNotInProfileException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		Pair<Boolean, OWLClassExpression> candidate = new Pair<Boolean, OWLClassExpression>(
				direct, ce);
		NodeSet<OWLNamedIndividual> result = getInstancesCache.get(candidate);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getInstancesCache.get(candidate);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getInstances " + ce);
					result = reasoner.getInstances(ce, direct);
					getInstancesCache.putIfAbsent(candidate, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getInstances Done.");
				}
			}
		}
		return result;
	}

	/**
	 * Cached
	 */
	public NodeSet<OWLNamedIndividual> getObjectPropertyValues(
			OWLNamedIndividual ind, OWLObjectPropertyExpression pe)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		Pair<OWLNamedIndividual, OWLObjectPropertyExpression> candidate = new Pair<OWLNamedIndividual, OWLObjectPropertyExpression>(
				ind, pe);
		NodeSet<OWLNamedIndividual> result = getObjectPropertyValuesCache
				.get(candidate);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getObjectPropertyValuesCache.get(candidate);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getObjectPropertyValues " + ind + " " + pe);
					result = reasoner.getObjectPropertyValues(ind, pe);
					getObjectPropertyValuesCache.putIfAbsent(candidate, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getObjectPropertyValues Done.");
				}
			}
		}
		return result;
	}

	/**
	 * Cached
	 */
	public Set<OWLLiteral> getDataPropertyValues(OWLNamedIndividual ind,
			OWLDataProperty pe) throws InconsistentOntologyException,
			FreshEntitiesException, ReasonerInterruptedException,
			TimeOutException
	{
		Pair<OWLNamedIndividual, OWLDataProperty> candidate = new Pair<OWLNamedIndividual, OWLDataProperty>(
				ind, pe);
		Set<OWLLiteral> result = getDataPropertyValuesCache.get(candidate);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getDataPropertyValuesCache.get(candidate);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getDataPropertyValues " + ind + " " + pe);
					result = reasoner.getDataPropertyValues(ind, pe);
					getDataPropertyValuesCache.putIfAbsent(candidate, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getDataPropertyValues Done.");
				}
			}
		}
		return result;
	}

	public Node<OWLNamedIndividual> getSameIndividuals(OWLNamedIndividual ind)
			throws InconsistentOntologyException, FreshEntitiesException,
			ReasonerInterruptedException, TimeOutException
	{
		Node<OWLNamedIndividual> result = getSameIndividualsCache.get(ind);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getSameIndividualsCache.get(ind);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getSameIndividuals " + ind);
					result = reasoner.getSameIndividuals(ind);
					getSameIndividualsCache.putIfAbsent(ind, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getSameIndividuals Done.");
				}
			}
		}
		return result;
	}

	public NodeSet<OWLNamedIndividual> getDifferentIndividuals(
			OWLNamedIndividual ind) throws InconsistentOntologyException,
			FreshEntitiesException, ReasonerInterruptedException,
			TimeOutException
	{
		NodeSet<OWLNamedIndividual> result = getDifferentIndividualsCache.get(ind);
		if (result == null)
		{
			synchronized (reasoner)
			{
				result = getDifferentIndividualsCache.get(ind);
				if (result == null)
				{
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getDifferentIndividuals " + ind);
					result = reasoner.getDifferentIndividuals(ind);
					getDifferentIndividualsCache.putIfAbsent(ind, result);
					if (DBG_CACHE_MISS)	ThreadLocalStopwatch.getWatch().time("CR.getDifferentIndividuals Done.");
				}
			}
		}
		return result;
	}

	public long getTimeOut()
	{
		if (getTimeOutCache == null)
			synchronized (reasoner)
			{
				if (getTimeOutCache == null)
					getTimeOutCache = reasoner.getTimeOut();
			}
		return getTimeOutCache;
	}

	public FreshEntityPolicy getFreshEntityPolicy()
	{
		if (getFreshEntityPolicyCache == null)
			synchronized (reasoner)
			{
				if (getFreshEntityPolicyCache == null)
					getFreshEntityPolicyCache = reasoner.getFreshEntityPolicy();
			}
		return getFreshEntityPolicyCache;
	}

	public IndividualNodeSetPolicy getIndividualNodeSetPolicy()
	{
		if (getIndividualNodeSetPolicyCache == null)
			synchronized (reasoner)
			{
				if (getIndividualNodeSetPolicyCache == null)
					getIndividualNodeSetPolicyCache = reasoner.getIndividualNodeSetPolicy();
			}
		return getIndividualNodeSetPolicyCache;
	}

	public void dispose()
	{
		reasoner.dispose();
	}

	/**
	 * Prints the current size of each cache.
	 */
	public void printCacheStatus()
	{
		System.out.println("CachedResoner Cache sizes: ");
		System.out.println(getCacheStatus());
	}

	/**
	 * Gets the total number of entries in the reasoner cache.
	 * Only HashTables are included in the total.
	 * e.g. not getReasonerVersionChache, getTopClassNodeCache, ...
	 * 
	 * @return
	 */
	public long getTotalCacheEntryCount() {
		return getSubClassesCache.size()
		+ getSuperClassesCache.size()
		+ getEquivalentClassesCache.size()
		+ getDisjointClassesCache.size()
		+ getSubObjectPropertiesCache.size()
		+ getSuperObjectPropertiesCache.size()
		+ getEquivalentObjectPropertiesCache.size()
		+ getDisjointObjectPropertiesCache.size()
		+ getInverseObjectPropertiesCache.size()
		+ getObjectPropertyDomainsCache.size()
		+ getObjectPropertyRangesCache.size()
		+ getSubDataPropertiesCache.size()
		+ getSuperDataPropertiesCache.size()
		+ getEquivalentDataPropertiesCache.size()
		+ getDisjointDataPropertiesCache.size()
		+ getDataPropertyDomainsCache.size()
		+ getTypesCache.size()
		+ getInstancesCache.size()
		+ getObjectPropertyValuesCache.size()
		+ getDataPropertyValuesCache.size()
		+ getSameIndividualsCache.size()
		+ getDifferentIndividualsCache.size();		
	}

	/**
	 * Returns a message containing concurrent cache map names and their current
	 * size. Some are just single value caches that may report as null or the value.
	 * 
	 * @return
	 */
	public String getCacheStatus()
	{
		String msg = 
		"getReasonerNameCache " + getReasonerNameCache
		+ "\r\n getReasonerVersionChache " + getReasonerVersionChache 
		+ "\r\n getBufferingModeCache  " + getBufferingModeCache
		+ "\r\n getRootOntologyCache  " + getRootOntologyCache 
		+ "\r\n getTopClassNodeCache  " + getTopClassNodeCache
		+ "\r\n getBottomClassNodeCache  " + getBottomClassNodeCache
		+ "\r\n getSubClassesCache " + getSubClassesCache.size()
		+ "\r\n getSuperClassesCache " + getSuperClassesCache.size()
		+ "\r\n getEquivalentClassesCache " + getEquivalentClassesCache.size()
		+ "\r\n getDisjointClassesCache " + getDisjointClassesCache.size()
		+ "\r\n getTopObjectPropertyNodeCache  " + getTopObjectPropertyNodeCache
		+ "\r\n getBottomObjectPropertyNodeCache  " + getBottomObjectPropertyNodeCache
		+ "\r\n getSubObjectPropertiesCache " + getSubObjectPropertiesCache.size()
		+ "\r\n getSuperObjectPropertiesCache " + getSuperObjectPropertiesCache.size()
		+ "\r\n getEquivalentObjectPropertiesCache " + getEquivalentObjectPropertiesCache.size()
		+ "\r\n getDisjointObjectPropertiesCache " + getDisjointObjectPropertiesCache.size()
		+ "\r\n getInverseObjectPropertiesCache " + getInverseObjectPropertiesCache.size()
		+ "\r\n getObjectPropertyDomainsCache " + getObjectPropertyDomainsCache.size()
		+ "\r\n getObjectPropertyRangesCache " + getObjectPropertyRangesCache.size()
		+ "\r\n getTopDataPropertyNodeCache  " + getTopDataPropertyNodeCache
		+ "\r\n getBottomDataPropertyNodeCache  " + getBottomDataPropertyNodeCache
		+ "\r\n getSubDataPropertiesCache " + getSubDataPropertiesCache.size()
		+ "\r\n getSuperDataPropertiesCache " + getSuperDataPropertiesCache.size()
		+ "\r\n getEquivalentDataPropertiesCache " + getEquivalentDataPropertiesCache.size()
		+ "\r\n getDisjointDataPropertiesCache " + getDisjointDataPropertiesCache.size()
		+ "\r\n getDataPropertyDomainsCache " + getDataPropertyDomainsCache.size()
		+ "\r\n getTypesCache " + getTypesCache.size()
		+ "\r\n getInstancesCache " + getInstancesCache.size()
		+ "\r\n getObjectPropertyValuesCache " + getObjectPropertyValuesCache.size()
		+ "\r\n getDataPropertyValuesCache " + getDataPropertyValuesCache.size()
		+ "\r\n getSameIndividualsCache " + getSameIndividualsCache.size()
		+ "\r\n getDifferentIndividualsCache " + getDifferentIndividualsCache.size()
		+ "\r\n getTimeOutCache  " + getTimeOutCache
		+ "\r\n getFreshEntityPolicyCache  " + getFreshEntityPolicyCache
		+ "\r\n getIndividualNodeSetPolicyCache  " + getIndividualNodeSetPolicyCache
		;
		return msg;
	}
	
	/**
	 * @return a json array of objects, direct:, classExpression:
	 */
	public Json getInstancesCacheRequests()
	{
		ManchesterOWLSyntaxOWLObjectRendererImpl renderer = new ManchesterOWLSyntaxOWLObjectRendererImpl();
		renderer.setShortFormProvider((DefaultPrefixManager)OWL.prefixManager());
		Json result = Json.array();
		for (Entry<Pair<Boolean, OWLClassExpression>, NodeSet<OWLNamedIndividual>> e : getInstancesCache.entrySet()) 
		{
			Pair<Boolean, OWLClassExpression> query = e.getKey();
			Json queryJ = Json.object();
			queryJ.set("direct", query.getFirst());
			queryJ.set("classExpression", renderer.render(query.getSecond()));
			result.add(queryJ);
		}
		return result;
	}

	/**
	 * Array of objects e.g. "direct":false, "classExpression":"mdc:City_Organization\n and (mdc:hasParentAgency value mdc:City_of_South_Miami)"; 
	 * @param queries
	 * @return
	 */
	public Json populateGetInstancesCache(Json queries)
	{
		if (!queries.isArray()) throw new IllegalArgumentException("Needs to be array");
		List<Json> l = queries.asJsonList();
		int i = 0;
		ThreadLocalStopwatch.getWatch().time("CR.populateGetInstancesCache: nrOfQueries: " + l.size());
		for (Json query : l) 
		{
			System.out.print("Q " + i + " ");
			//ThreadLocalStopwatch.getWatch().reset();
			try {
				boolean direct = query.at("direct").asBoolean();
				String dlQuery = query.at("classExpression").asString();
				DLQueryParser p = DLQueryParser.getParser(getRootOntology(),(DefaultPrefixManager)OWL.prefixManager());
				OWLClassExpression cx = p.parseClassExpression(dlQuery);
				getInstances(cx, direct);
			} catch (Exception e) 
			{
				e.printStackTrace();
				ThreadLocalStopwatch.getWatch().time("CR.populateGetInstancesCache: FAILED.");
				return GenUtils.ko("populateGetInstancesCache failed at query nr" + i + "(" + query.toString() +") with " + e.toString());
			}
			i++;
		}
		ThreadLocalStopwatch.getWatch().time("CR.populateGetInstancesCache: done.");
		return GenUtils.ok().with(Json.object("populateGetInstancesCache nrOfProcessed", i));
	}

	

	/**

	/**
	 * Clears the cache
	 */
	public void clearCache()
	{
		getReasonerNameCache = null;
		getReasonerVersionChache = null; 
		getBufferingModeCache = null;
		getRootOntologyCache = null; 
		getTopClassNodeCache = null;
		getBottomClassNodeCache = null;
		getSubClassesCache.clear();
		getSuperClassesCache.clear();
		getEquivalentClassesCache.clear();
		getDisjointClassesCache.clear();
		getTopObjectPropertyNodeCache = null;
		getBottomObjectPropertyNodeCache = null;
		getSubObjectPropertiesCache.clear();
		getSuperObjectPropertiesCache.clear();
		getEquivalentObjectPropertiesCache.clear();
		getDisjointObjectPropertiesCache.clear();
		getInverseObjectPropertiesCache.clear();
		getObjectPropertyDomainsCache.clear();
		getObjectPropertyRangesCache.clear();
		getTopDataPropertyNodeCache = null;
		getBottomDataPropertyNodeCache = null;
		getSubDataPropertiesCache.clear();
		getSuperDataPropertiesCache.clear();
		getEquivalentDataPropertiesCache.clear();
		getDisjointDataPropertiesCache.clear();
		getDataPropertyDomainsCache.clear();
		getTypesCache.clear();
		getInstancesCache.clear();
		getObjectPropertyValuesCache.clear();
		getDataPropertyValuesCache.clear();
		getSameIndividualsCache.clear();
		getDifferentIndividualsCache.clear();
		getTimeOutCache = null;
		getFreshEntityPolicyCache = null;
		getIndividualNodeSetPolicyCache = null;
		ThreadLocalStopwatch.getWatch().time("-> CACHED_REASONER CACHE CLEARED! <-");
	}

	@Override
	public OWLReasoner unwrap()
	{
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
	public OWLReasoner unwrapAll()
	{
		if (reasoner instanceof Wrapper<?>)
		{
			return ((Wrapper<OWLReasoner>) reasoner).unwrapAll();
		} else
		{
			return reasoner;
		}
	}
}

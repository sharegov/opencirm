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
package org.sharegov.cirm.workflows;


import java.io.File;


import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.sharegov.cirm.Refs;
import static org.sharegov.cirm.OWL.*;

import org.sharegov.cirm.owl.SynchronizedOWLManager;
import org.sharegov.cirm.rules.EvaluateAtom;
//import org.sharegov.cirm.rules.PromptUserBuiltIn;
import org.sharegov.cirm.utils.EvalUtils;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.TraceUtils;
import org.hypergraphdb.HGEnvironment;
import org.hypergraphdb.HGHandle;
import org.hypergraphdb.HGPersistentHandle;
import org.hypergraphdb.HyperGraph;
import org.hypergraphdb.HGQuery.hg;
import org.hypergraphdb.algorithms.DefaultALGenerator;
import org.hypergraphdb.algorithms.HGDepthFirstTraversal;
import org.hypergraphdb.algorithms.HGTraversal;
import org.hypergraphdb.algorithms.HyperTraversal;
import org.hypergraphdb.app.management.HGManagement;
import org.hypergraphdb.app.owl.*;
import org.hypergraphdb.app.owl.model.OWLDatatypeHGDB;
import org.hypergraphdb.app.owl.model.OWLLiteralHGDB;
import org.hypergraphdb.app.owl.model.OWLNamedIndividualHGDB;
import org.hypergraphdb.app.owl.model.OWLObjectPropertyHGDB;
import org.hypergraphdb.app.owl.model.swrl.SWRLAtomHGDB;
import org.hypergraphdb.app.owl.model.swrl.SWRLBody;
import org.hypergraphdb.app.owl.model.swrl.SWRLBuiltInAtomHGDB;
import org.hypergraphdb.app.owl.model.swrl.SWRLClassAtomHGDB;
import org.hypergraphdb.app.owl.model.swrl.SWRLDataPropertyAtomHGDB;
import org.hypergraphdb.app.owl.model.swrl.SWRLDataRangeAtomHGDB;
import org.hypergraphdb.app.owl.model.swrl.SWRLDifferentIndividualsAtomHGDB;
import org.hypergraphdb.app.owl.model.swrl.SWRLHead;
import org.hypergraphdb.app.owl.model.swrl.SWRLIndividualArgumentHGDB;
import org.hypergraphdb.app.owl.model.swrl.SWRLLiteralArgumentHGDB;
import org.hypergraphdb.app.owl.model.swrl.SWRLObjectPropertyAtomHGDB;
import org.hypergraphdb.app.owl.model.swrl.SWRLRuleHGDB;
import org.hypergraphdb.app.owl.model.swrl.SWRLSameIndividualAtomHGDB;
import org.hypergraphdb.app.owl.model.swrl.SWRLVariableHGDB;
import org.hypergraphdb.query.HGQueryCondition;
import org.hypergraphdb.type.TypeUtils;
import org.hypergraphdb.util.HGUtils;
import org.hypergraphdb.util.Mapping;
import org.hypergraphdb.util.Pair;
import org.hypergraphdb.util.RefResolver;


public class RulesToWorkflow
{
	static IRI hasInquiryStatus = IRI
			.create("http://www.miamidade.gov/ontology#hasInquiryStatus");
	static IRI inquiryResolved = IRI
			.create("http://www.miamidade.gov/ontology#InquiryResolved");

//	public final static IRI impliesVar = IRI
//			.create("http://www.miamidade.gov/ontology/variable#implies"); 
//	public final static IRI impliedVar = IRI
//			.create("http://www.miamidade.gov/ontology/variable#implied");

	public final static IRI closesVar = IRI
		.create("http://www.miamidade.gov/ontology/variable#closes");

	public final static IRI closedByVar = IRI
		.create("http://www.miamidade.gov/ontology/variable#closedBy");

	public final static IRI boVar = IRI.create(Refs.SWRL_PREFIX + "#bo");
	
	public final static IRI nowVar = IRI.create(Refs.SWRL_PREFIX + "#now");
	public final static IRI timeElapsed = IRI.create(Refs.MDC_PREFIX + "#timeElapsed");
	
	HyperGraph graph;
	OWLOntologyManager manager;
	OWLOntology boOntology;
	OWLNamedIndividual bo;
	OWLOntology rulesOntology;
	Map<SWRLRule, AppliedRule> appliedRules = new HashMap<SWRLRule, AppliedRule>();
	Set<AppliedRule> unresolvedRules = new HashSet<AppliedRule>();
	Set<AppliedRule> resolvedRules = new HashSet<AppliedRule>();
	Map<AtomPartialKey, Integer> atomOccurrenceCounts = new HashMap<AtomPartialKey, Integer>();
	Map<WorkflowPathElement, Task> tasks = new HashMap<WorkflowPathElement, Task>();
	Map<SWRLAtom, SWRLAtom> instantiatedFrom = new HashMap<SWRLAtom, SWRLAtom>();
	
	// The varBindings map manages variable dependencies between tasks. For each task, we
	// hold a set of associations between a destination variable in the task (a receiver of a value)
	// and a source variable at another task (a sender of a value). 
	Map<Task, Map<IRI, Pair<Task, IRI>>> varBindings = new HashMap<Task, Map<IRI, Pair<Task, IRI>>>();
	GlobalVarDependencyMap globalVarMap = new GlobalVarDependencyMap();
	Map<AtomPartialKey, WorkflowPathElement> globalWorkflowElements = new HashMap<AtomPartialKey, WorkflowPathElement>();
	
	void setVarDependencies(Task dependent, IRI dependentVar, Task dependency, IRI dependencyVar)
	{
//		System.out.println("Dependency " + dependent + " at " + dependentVar + " on " + dependency + " at " + dependencyVar);
		Map<IRI, Pair<Task, IRI>> depMap = varBindings.get(dependent);
		if (depMap == null)
		{
			depMap = new HashMap<IRI, Pair<Task, IRI>>();
			varBindings.put(dependent, depMap);
		}
		depMap.put(dependentVar, new Pair<Task, IRI>(dependency, dependencyVar));
	}
	
	public class AssertAtom
	{
		AppliedRule rule;
		AssertAtom(AppliedRule rule) { this.rule = rule; }
		
		public boolean apply(SWRLDataPropertyAtom atom)
		{
			OWLNamedIndividual ind = null; 
			if (atom.getFirstArgument() instanceof OWLNamedIndividual)
				ind = (OWLNamedIndividual)atom.getFirstArgument();
			else 
				ind = rule.valueOf((SWRLVariable)atom.getFirstArgument());
			if (ind == null)
				return false;
			OWLLiteral literal = null;
			if (atom.getSecondArgument() instanceof SWRLLiteralArgument)
				literal = ((SWRLLiteralArgument)atom.getSecondArgument()).getLiteral();
			else
				literal = rule.valueOf(((SWRLVariable)atom.getSecondArgument()));
			if (literal == null)
				return false;
			OWLDataPropertyAssertionAxiom axiom = 
				manager.getOWLDataFactory().getOWLDataPropertyAssertionAxiom(atom.getPredicate(), ind, literal);
			manager.addAxiom(boOntology, axiom);
			return true;
		}
		
		public boolean apply(SWRLObjectPropertyAtom atom)
		{
			OWLNamedIndividual subject = null; 
			if (atom.getFirstArgument() instanceof OWLIndividual)
				subject = (OWLNamedIndividual)atom.getFirstArgument();
			else 
				subject = rule.valueOf((SWRLVariable)atom.getFirstArgument());
			if (subject == null)
				return false;
			OWLNamedIndividual object = null; 
			if (atom.getFirstArgument() instanceof OWLIndividual)
				object = (OWLNamedIndividual)atom.getFirstArgument();
			else 
				object = rule.valueOf((SWRLVariable)atom.getFirstArgument());
			if (object == null)
				return false;
			OWLObjectPropertyAssertionAxiom axiom = 
				manager.getOWLDataFactory().getOWLObjectPropertyAssertionAxiom(atom.getPredicate(), subject, object);
			manager.addAxiom(boOntology, axiom);
			return true;
		}
	}
	
	SWRLAtom instantiate(AppliedRule rule, SWRLAtom atom)
	{
		SWRLAtom instance = EvalUtils.dispatch(new AtomInstantiation(rule), atom);
		rule.instantiated.put(atom, instance);
		instantiatedFrom.put(instance, atom);	
		return instance;
	}
	
	AtomValue evalAndInstantiate(final AppliedRule rule, final SWRLAtom atom)
	{
		RefResolver<SWRLVariable, OWLObject> varresolver = new RefResolver<SWRLVariable, OWLObject>()
		{
			public OWLObject resolve(SWRLVariable var) { return rule.valueOf(var); }
		};
		AtomValue result = EvalUtils.dispatch(new EvaluateAtom(varresolver, boOntology), atom);
		instantiate(rule, atom);
		return result;
	}
	
	/**
	 * Evaluate/apply a single rule and return the result as an AppliedRule.
	 * The evaluation consists of the following steps:
	 * 
	 *  1) Perform variable assignment for each atom in the rule's body: for object
	 *  and data property axioms, if the first argument is a known individual and the
	 *  second argument is variable assign any known such property of that individual
	 *  to that variable. For SameIndividual axioms, if one argument is known and the 
	 *  other a variable, assign to that variable.
	 *  
	 *  2) Evaluate the truth value of each atom in the body by using the variable
	 *  assignments found in (1). The truth value with True, False or Unknown (enum AtomValue).
	 *  Store the result of the evaluation in the returned AppliedRule instance.
	 *  
	 *  3) Instantiate each atom in the head of the rule from the variables found
	 *  in (1).
	 *  
	 * @param rule
	 * @return
	 */
	AppliedRule eval(SWRLRule rule)
	{
		AppliedRule applied = new AppliedRule();
		applied.manager = manager;
		applied.rule = rule;
//		applied.assign(new SWRLVariableHGDB(boVar), bo);
		for (boolean again = true; again; )		
		{
			again = false;
			for (SWRLAtom atom : rule.getBody())
			{			
				Pair<SWRLVariable, OWLObject> assignment = 
					EvalUtils.dispatch(new VarAssignment(boOntology, applied.getVarResolver()), atom);
				if (assignment != null && applied.valueOf(assignment.getFirst()) == null)
				{
					again = true;
					applied.assign(assignment.getFirst(), assignment.getSecond());
				}
			}
		}
		
		for (SWRLAtom atom : rule.getBody())
		{
			AtomValue value = evalAndInstantiate(applied, atom);
			applied.truthValues.put(atom, value);
		}
		
		for (SWRLAtom atom : rule.getHead())
		{
			instantiate(applied, atom);			 
		}
		
		return applied;
	}
	
	boolean isEndGoal(SWRLAtom atom)
	{
		if (!(atom instanceof SWRLBinaryAtom<?, ?>))
			return false;
		SWRLBinaryAtom bin = (SWRLBinaryAtom) atom;
		SWRLArgument snd = bin.getSecondArgument();
		if (!(bin.getSecondArgument() instanceof SWRLIndividualArgument))
			return false;
		SWRLPredicate predicate = bin.getPredicate();
		if (!(predicate instanceof OWLObjectProperty))
			return false;
		return ((OWLObjectProperty) predicate).getIRI()
				.equals(hasInquiryStatus)
				&& ((SWRLIndividualArgument) snd).getIndividual()
						.asOWLNamedIndividual().getIRI()
						.equals(inquiryResolved);
	}

	OWLObjectPropertyExpression toObjectPropertyExpressionHGDB(
			OWLObjectPropertyExpression expr)
	{
		OWLObjectPropertyExpression result = null;
		if (expr instanceof OWLObjectProperty)
			result = new OWLObjectPropertyHGDB(((OWLObjectProperty) expr).getIRI());
		else
			throw new RuntimeException(
					"can't translate OWLObjectPropertyExpression " + expr);
		return result;
	}

	OWLIndividual toIndividualHGDB(OWLIndividual ind)
	{
		if (ind instanceof OWLNamedIndividual)
			return new OWLNamedIndividualHGDB(((OWLNamedIndividual) ind).getIRI());
		else 
			throw new RuntimeException("can't translate OWLIndividual " + ind);
	}
	
	OWLLiteral toLiteralHGDB(OWLLiteral literal)
	{
		return manager.getOWLDataFactory().getOWLLiteral(literal.getLiteral(), 
				manager.getOWLDataFactory().getOWLDatatype(literal.getDatatype().getIRI()));
	}
	
	@SuppressWarnings("unchecked")
	<T extends SWRLArgument> T toArgumentHGDB(SWRLArgument arg)
	{
		if (arg instanceof SWRLIndividualArgument)
		{
			return (T)manager.getOWLDataFactory().getSWRLIndividualArgument(toIndividualHGDB(((SWRLIndividualArgument) arg).getIndividual()));
//			return (T) new SWRLIndividualArgumentHGDB(
//					toIndividualHGDB(((SWRLIndividualArgument) arg).getIndividual()));
		}
		else if (arg instanceof SWRLLiteralArgument)
		{
			return (T) new SWRLLiteralArgumentHGDB(toLiteralHGDB(((SWRLLiteralArgument) arg)
					.getLiteral()));
		}
		else if (arg instanceof SWRLVariable)
		{
			return (T) new SWRLVariableHGDB(((SWRLVariable) arg).getIRI());
		}
		else
			throw new RuntimeException("can't translate SWRLArgument " + arg);
	}

	HGHandle toHGDBAtom(SWRLAtom atom)
	{
		SWRLAtomHGDB swrlAtom = null;

		if (atom == null)
			throw new NullPointerException("SWRLAtom is null.");
		else if (atom instanceof SWRLBuiltInAtom)
		{
			SWRLBuiltInAtomHGDB x = new SWRLBuiltInAtomHGDB();
			x.setPredicate(((SWRLBuiltInAtom) atom).getPredicate());
			List<SWRLDArgument> L = new ArrayList<SWRLDArgument>();
			for (SWRLDArgument a : ((SWRLBuiltInAtom) atom).getArguments())
				L.add((SWRLDArgument)toArgumentHGDB(a));
			x.setArguments(L);
			swrlAtom = x;
		}
		else if (atom instanceof SWRLClassAtom)
		{
			swrlAtom = new SWRLClassAtomHGDB((OWLClassExpression) atom
					.getPredicate(), ((SWRLClassAtom) atom).getArgument());
		}
		else if (atom instanceof SWRLDataPropertyAtom)
		{
			swrlAtom = new SWRLDataPropertyAtomHGDB(
					(OWLDataPropertyExpression) atom.getPredicate(),
					(SWRLIArgument)toArgumentHGDB(((SWRLDataPropertyAtom) atom).getFirstArgument()),
					(SWRLDArgument)toArgumentHGDB(((SWRLDataPropertyAtom) atom).getSecondArgument()));
		}
		else if (atom instanceof SWRLDataRangeAtom)
		{
			swrlAtom = new SWRLDataRangeAtomHGDB((OWLDataRange) atom
					.getPredicate(), ((SWRLDataRangeAtom) atom).getArgument());
		}
		else if (atom instanceof SWRLDifferentIndividualsAtom)
		{
			swrlAtom = new SWRLDifferentIndividualsAtomHGDB(
					((SWRLDifferentIndividualsAtom) atom).getFirstArgument(),
					((SWRLDifferentIndividualsAtom) atom).getSecondArgument());
		}
		else if (atom instanceof SWRLObjectPropertyAtom)
		{
			OWLObjectPropertyExpression predicate = toObjectPropertyExpressionHGDB((OWLObjectPropertyExpression) atom
					.getPredicate());
			swrlAtom = new SWRLObjectPropertyAtomHGDB(
					predicate,
					(SWRLIArgument) toArgumentHGDB(((SWRLObjectPropertyAtom) atom)
							.getFirstArgument()),
					(SWRLIArgument) toArgumentHGDB(((SWRLObjectPropertyAtom) atom)
							.getSecondArgument()));
		}
		else if (atom instanceof SWRLSameIndividualAtom)
		{
			swrlAtom = new SWRLSameIndividualAtomHGDB(
					(SWRLIArgument)toArgumentHGDB(((SWRLSameIndividualAtom) atom).getFirstArgument()),
					(SWRLIArgument)toArgumentHGDB(((SWRLSameIndividualAtom) atom).getSecondArgument()));
		}
		else
		{
			throw new RuntimeException("Don't know what to do with SWRLAtom: "
					+ atom + ":" + atom.getClass());
		}
		try
		{
			return hg.assertAtom(graph, swrlAtom);
		}
		catch (RuntimeException ex)
		{
			System.err.println("Failed on " + atom);
			throw ex;
		}
	}

	HGHandle ruleToHGDB(SWRLRule rule)
	{
		ArrayList<HGHandle> conjunction = new ArrayList<HGHandle>();
		for (SWRLAtom atom : rule.getHead())
			conjunction.add(toHGDBAtom(atom));
		HGHandle head = graph.add(new SWRLHead(conjunction.toArray(new HGHandle[0])));
		conjunction.clear();
		for (SWRLAtom atom : rule.getBody())
			conjunction.add(toHGDBAtom(atom));
		HGHandle body = graph.add(new SWRLBody(conjunction.toArray(new HGHandle[0])));
		return graph.add(new SWRLRuleHGDB(body, head));
	}

	public OWLIndividual getBusinessObjectIndividual()
	{
		IRI iri = boOntology.getOntologyID().getOntologyIRI().resolve("#bo");
		return new OWLNamedIndividualHGDB(iri);
	}
	
	public void augmentRuleWithImplicitClauses(SWRLRule rule)
	{
		IRI varIri = rulesOntology.getOntologyID().getOntologyIRI().resolve("inquiry");
		SWRLAtom atom = manager.getOWLDataFactory().getSWRLSameIndividualAtom(
				manager.getOWLDataFactory().getSWRLIndividualArgument(getBusinessObjectIndividual()), 
				manager.getOWLDataFactory().getSWRLVariable(varIri));
//		SWRLAtom atom = new SWRLSameIndividualAtomHGDB(
//									new SWRLIndividualArgumentHGDB(getBusinessObjectIndividual()),
//									new SWRLVariableHGDB(varIri));
		rule.getBody().add(atom);
	}

	/**
	 * Evaluate a set of rules and return true if at least one of them has 
	 * its premises satisfied and contains an endgoal in its head. The evaluation
	 * creates an AppliedRule for each SWRLRule and stores it in the appliedRules 
	 * map. It also populates the 'resolvedRules' and 'unresolvedRules' sets which
	 * partition the original rule into rules whose premises are all true or contain
	 * at least one false one (resolved) and all other rules (unresolved). Only
	 * the unresolved rules will be used in the workflow construction.
	 *  
	 * If a resolved rule is satisfied (i.e. only true premises) and it contains
	 * and end goal in its head, the method returns true. Otherwise it returns false.
	 * 
	 * @param rules
	 * @return
	 */
	boolean evalRules(Set<SWRLRule> rules)
	{
		unresolvedRules.clear();
		resolvedRules.clear();
		for (boolean again = true; again ; )
		{
			again = false;
			for (SWRLRule rule : rules)
			{				
				AppliedRule applied = appliedRules.get(rule);
				if (applied == null)
					applied = eval(rule);
				else if (resolvedRules.contains(applied))
					continue;
				else
				{
					unresolvedRules.remove(applied);
					applied = eval(rule);
				}
				appliedRules.put(rule, applied);
				switch (applied.isSatisfied())
				{
					case True:
					{
						// assert the atoms in the head globally
						for (SWRLAtom atom : rule.getHead())
						{
							EvalUtils.dispatch(new AssertAtom(applied), atom);
							if (isEndGoal(atom))
								return true;
						}					
						resolvedRules.add(applied);
						unresolvedRules.remove(applied);
						again = true;						
					}
					case False:
					{
						resolvedRules.add(applied);
						unresolvedRules.remove(applied);						
					}
					default:
					{
						unresolvedRules.add(applied);
					}
				}
			}
		}
		return false;
	}
	/*
	class AtomComparator implements Comparator<SWRLAtom>
	{
		public int compare(SWRLAtom o1, SWRLAtom o2)
		{
			int o1_count = getOccurrenceCount(o1);
			int o2_count = getOccurrenceCount(o2);
			return o1_count < o2_count ? 1 : 
					o1_count > o2_count ? -1 : 
						o1.toString().compareTo(o2.toString());  
		}		
	}

	class AtomPathComparator implements Comparator<SortedSet<SWRLAtom>>
	{
		public int compare(SortedSet<SWRLAtom> o1, SortedSet<SWRLAtom> o2)
		{
			double o1_score = 0, o2_score = 0;
			for (SWRLAtom a : o1)
			{
				o1_score += getOccurrenceCount(a);
			}
			for (SWRLAtom a : o2)
			{
				o2_score += getOccurrenceCount(a);
			}
			return o1_score < o2_score ? 1 : 
					o1_score > o2_score ? -1 :
						o1.toString().compareTo(o2.toString());
		}		
	}
	*/
	class TaskComparator implements Comparator<Task>
	{
		public int compare(Task o1, Task o2)
		{
			double o1_score = scoreTask(o1);
			double o2_score = scoreTask(o2);
			return o1_score < o2_score ? -1 : 
				o1_score > o2_score ? 1 :
					o1.toString().compareTo(o2.toString());
		}
	}
	
	class TaskPathComparator implements Comparator<SortedSet<Task>>
	{
		public int compare(SortedSet<Task> o1, SortedSet<Task> o2)
		{
			double o1_score = 0, o2_score = 0;
			for (Task t : o1)
			{
				o1_score += scoreTask(t);
			}
			for (Task t : o2)
			{
				o2_score += scoreTask(t);
			}
			return o1_score < o2_score ? -1 : 
					o1_score > o2_score ? 1 :
						o1.toString().compareTo(o2.toString());			
		}
	}	

	public void computeGlobalVariableDependencies(Set<AppliedRule> rules)
	{
		for (AppliedRule r : rules)
		{
			for (SWRLAtom a : r.getUnknowns())
			{
				SWRLVariable var = r.getDependableVar(a);
				if (var != null && EvalUtils.isVarGlobal(var.getIRI()))
				{
					this.globalVarMap.addInstantiatingAtom(var.getIRI(), a);
					WorkflowPathElement pathEl = new WorkflowPathElement(r.substituteVars(a));
					this.globalWorkflowElements.put(new AtomPartialKey(a), pathEl);
				}
				for (SWRLArgument arg : a.getAllArguments())
				{
					if (! (arg instanceof SWRLVariable)) continue;
					SWRLVariable varg = (SWRLVariable)arg;
					if (!varg.equals(var) && EvalUtils.isVarGlobal(varg.getIRI()))
						this.globalVarMap.addDependentAtom(varg.getIRI(), a);
				}
			}
		}
	}
	
	Iterator<GoalSet> enumerateDerivationPaths(final AppliedRule goalRule)
	{
		return enumerateDerivationPaths(goalRule, new GoalSet(globalWorkflowElements), null);
	}
	
	Iterator<GoalSet> enumerateDerivationPaths(final AppliedRule goalRule, 
											   final GoalSet base,
											   final SWRLAtom subgoal)
	{
		return new Iterator<GoalSet>()
		{
			List<Iterator<GoalSet>> successor = new ArrayList<Iterator<GoalSet>>();
			GoalSet S = null;
			boolean initialized = false;
			void init()
			{
				if (initialized) return;
				
				S = base;
				
				// This contains all atoms that must be replaced by backward-chain evaluations. Each
				// such atom appears in one or more rule heads, each rule head offering a potentially
				// distinct possibility to satisfy the goal. We process this successor map after the
				// loop below where we first want to collect all independent atoms (atom that are not
				// conclusions of other rules) in the set 'S'.
				Map<SWRLAtom, List<HGHandle>> successorHeads = new HashMap<SWRLAtom, List<HGHandle>>();
				
				for (SWRLAtom a : goalRule.getUnknowns())
				{
					SWRLAtom instantiated = goalRule.substituteVars(a);
					WorkflowPathElement pathEl = S.getWorkflowElement(instantiated);
										
					// A variable that this atom can instantiate for other atoms to "use"
					SWRLVariable var = goalRule.getDependableVar(a);
//					System.out.println("Dependable var: " + var + " for " + instantiated);
					if (var != null)
					{
						if (EvalUtils.isVarGlobal(var.getIRI()))
							globalWorkflowElements.put(new AtomPartialKey(pathEl.getAtom()), pathEl);
						pathEl.setOutputVar(var.getIRI());
						for (SWRLAtom d : goalRule.dependents.get(a))
						{
							WorkflowPathElement depEl = S.getWorkflowElement(goalRule.substituteVars(d));
							// Dependents coming from the local rule will bind to the same variable name.
							pathEl.getDependents().put(depEl, var.getIRI());
							depEl.getDependencies().put(pathEl, var.getIRI());
						}
					}
					
					// If this atom in some other rule's head, recursively, add atoms from that rule.
					List<HGHandle> heads = hg.findAll(graph, hg.and(hg.incident(graph.getHandle(a)), 
									 								hg.type(SWRLHead.class)));
					if (!heads.isEmpty())
					{
						successorHeads.put(instantiated, heads);
					}
					else if (goalRule.isIndependent(a) && pathEl.getDependencies().isEmpty())
					{						
						S.add(instantiated);
					}
					
					if (subgoal != null)
					{						
						// The subgoal depends (logically) on the current atom
						WorkflowPathElement subgoalEl = S.getWorkflowElement(subgoal);
						if (var != null && subgoalEl.hasVar(var.getIRI()))
						{
							pathEl.getDependents().put(subgoalEl, var.getIRI());
							subgoalEl.getDependencies().put(pathEl, var.getIRI());							
//							System.out.println("Subgoal " + subgoalEl + " depends " + var + " on " + pathEl);
						}
						else
						{
							IRI implication = EvalUtils.newImplicationVar();
							pathEl.getDependents().put(subgoalEl, implication);
							subgoalEl.getDependencies().put(pathEl, implication);
						}
					}					
				} // for all atoms...
				
				// Here we generate the set of possible successors from atoms in this rule's premises that
				// must be treated as sub-goals (conclusions from other rules).
				for (SWRLAtom instantiated : successorHeads.keySet())
				{
					for (HGHandle head : successorHeads.get(instantiated))
					{
						List<SWRLRule> subrules = hg.getAll(graph, 
															hg.and(hg.incident(head), 
																   hg.type(SWRLRuleHGDB.class)));
						for (SWRLRule subrule : subrules)
						{
							AppliedRule appliedSubrule = appliedRules.get(subrule);
							if (appliedSubrule == null)
							{
								System.err.println("Error: no applied rule for " + subrule);
							}								
							Iterator<GoalSet> I = enumerateDerivationPaths(appliedSubrule, 
																		   S.duplicate(), 
																		   instantiated);
							successor.add(I);
						}
					}
				}						
				
				// if we have subgoal to satisfy, we won't return this GoalSet but augmented
				// clones of it.
				if (!successor.isEmpty())
				{
//					System.out.println("Continue with subgoals " + subgoals);
					S = null; 
				}
//				else
//					System.out.println("Path completed at " + S);
				initialized = true;				
			}
			public void remove() { throw new UnsupportedOperationException(); }
			public boolean hasNext()
			{
				init();
				if (successor.isEmpty()) return S != null;
				else for (Iterator<GoalSet> I : successor) if (I.hasNext()) return true;
				return false;
			}
			public GoalSet next()
			{
				init(); 
				if (successor.isEmpty() && S != null)		
				{
					GoalSet x = S;
					S = null;
					return x;
				}
				else for (Iterator<GoalSet> I : successor)
					if (I.hasNext())
						return I.next();
				throw new NoSuchElementException();
			}
		};
	}

	void addAssertionTasks(SortedSet<Task> taskPath)
	{
//		System.out.println("Add assertions for " + taskPath);
		Set<Task> newTasks = new HashSet<Task>();
		Set<HGHandle> headSet = new HashSet<HGHandle>();
		for (Task t : taskPath)
		{
			if (! (t instanceof AtomEvalTask))
				continue;
			SWRLAtom atom = ((AtomEvalTask)t).getAtom();
			if (instantiatedFrom.containsKey(atom))
				atom = instantiatedFrom.get(atom);
			HGHandle atomHandle = graph.getHandle(atom);
			headSet.add(atomHandle);
			List<SWRLBody> bodies = hg.getAll(graph, hg.and(hg.type(SWRLBody.class),
															 hg.incident(atomHandle)));
			
			// Find all bodies to which this atom belongs and for each such
			// body that is completely satisfied (all its atoms are true), add
			// all its conclusions to the resulting task path. 
			Set<SWRLAtom> toInsert = new HashSet<SWRLAtom>(); 
			for (SWRLBody body : bodies)
			{
				boolean satisfied = true;
				for (HGHandle h : body.asCollection())
					// TODO: shouldn't we also check whether the atom was true to begin with?
					// here we're just verifying if it's a previous task (so, coming from an
					// originally unknown atom) or a default premise
					if (!headSet.contains(h))
					{
						satisfied = false;
						break;
					}
				if (!satisfied)
					continue;
				SWRLRuleHGDB therule = hg.getOne(graph, 
											hg.and(hg.type(SWRLRuleHGDB.class), 
												   hg.incident(graph.getHandle(body)))); 

				for (HGHandle h: therule.getHeadAtom().asCollection())
				{
					if (headSet.contains(h))
						continue;
					SWRLAtom rawAtom = (SWRLAtom)graph.get(h); 
					SWRLAtom instantiatedAtom = appliedRules.get(therule).substituteVars(rawAtom);
//					System.out.println("Raw " + rawAtom + " --- " + instantiatedAtom);
					toInsert.add(instantiatedAtom);
				}
			}
			for (SWRLAtom a : toInsert)
			{
				AtomBasedTask newtask = (a instanceof SWRLBuiltInAtom) ?
						new BuiltInAtomTask((SWRLBuiltInAtom)a) :
						new AssertAtomTask(a);
				//System.out.println("Assert for " + t + " -- " + newtask);				
				Map<IRI, Pair<Task, IRI>> m = varBindings.get(new AtomEvalTask(a));				
				if (m != null)
					varBindings.put(newtask, m);
				System.out.println("AtomTask to assert " + newtask  + " "  + varBindings.get(newtask));				
				newtask.setAssignedScore(scoreTask(t) + 0.0001);
				newTasks.add(newtask);
			}
		}
		taskPath.addAll(newTasks);
//		System.out.println("Assertions added  " + taskPath);
	}
	
	Task getTaskForElement(WorkflowPathElement el)
	{
		Task task = tasks.get(el);
		if (task == null)
		{
			task = (Task)new StepFactory().createStep(el.getAtom());
			if (el.getOutputVar() != null)
				task.getOutputVariables().add(el.getOutputVar());
			tasks.put(el, task);
		}
		return task;
	}
	
	/**
	 * <p>
	 * Transform an intermediary {@link WorkflowPathElement} into a workflow
	 * tasks. If the task has already been created for this element and it's
	 * already in the path, nothing is done. Otherwise, a task is created
	 * (based on the {@link StepFactory}) and added to the path <strong>after</strong>
	 * all its dependencies have been recursively "expanded" as well.
	 * </p>
	 * 
	 * @param el
	 * @param taskPath The path currently being constructed.
	 * @return The task corresponding to the 'el' argument.
	 */
	Task expandPathElement(WorkflowPathElement el , SortedSet<Task> taskPath)
	{
//		System.out.println("Expanding: " + el + " on " + taskPath);
		Task task = getTaskForElement(el);
		if (taskPath.contains(task))
			return tasks.get(el);
		for (WorkflowPathElement d : el.getDependencies().keySet())
		{
			Task dtask = expandPathElement(d, taskPath);
			if (d.getOutputVar() != null)
				setVarDependencies(task, el.getDependencies().get(d), dtask, d.getOutputVar());
			else
				setVarDependencies(task, el.getDependencies().get(d), dtask, d.getDependents().get(el));
		}
		taskPath.add(task);
		for (WorkflowPathElement d : el.getDependents().keySet())
			expandPathElement(d, taskPath);	
		return task;
	}	
	
	SortedSet<Task> injectGlobalDependencies(final SortedSet<Task> taskPath, final GoalSet S)
	{
		final SortedSet<Task> newPath = new TreeSet<Task>(new TaskComparator());
		for (final Task t : taskPath)
		{
			if (! (t instanceof AtomBasedTask))
			{
				newPath.add(t);
				continue;
			}
			SWRLAtom atom = ((AtomBasedTask)t).getAtom();
			Mapping<Set<AtomPartialKey>, AtomPartialKey> selector = 
				new Mapping<Set<AtomPartialKey>, AtomPartialKey>() {
			public AtomPartialKey eval(Set<AtomPartialKey> candidates)
			{
				// Here is given the keys to a set of atoms (and hence, workflow path elements
				// that are "capable" of instantiating a global variable referred in the current 
				// task. We need to select which one is suitable for this derivation path. A suitable
				// element would be one that doesn't create a circular dependency 
				for (AtomPartialKey c : candidates)
				{
					WorkflowPathElement el = S.getWorkflowElement(c);
					Task tel = getTaskForElement(el);
					if (t.equals(tel) || newPath.contains(tel)) // we already have this dependency upstream in the path 
						return null;
					else if (taskPath.contains(tel)) // we'd have a circular dependency if we use this one
					{
						// Should we maybe throw an exception after the loop if the only
						// candidates that we find lead to circularity?
						// In the grand schema of things, a plausible course of action is also
						// to drop that derivation path from the set of possible ways to reach
						// the goal.
						System.err.println("[RULE2WORKFLOW WARNING] : circular dependence in path " + 
								taskPath + " from global dependence on " + tel);
						continue;
					}
					else
						return c;
				}
				return null;	
			}
			};
			Set<Pair<IRI,AtomPartialKey>> deps = globalVarMap.getDependencies(atom, selector);
			for (Pair<IRI,AtomPartialKey> dep : deps)
			{
				WorkflowPathElement globalElement = S.getWorkflowElement(dep.getSecond()); 
				Task globalTask = expandPathElement(globalElement, newPath);
				setVarDependencies(t, dep.getFirst(), globalTask, dep.getFirst());
			}
			newPath.add(t);
		}
		return newPath;
	}

	int getTaskCost(Task t)
	{
//		if (t instanceof PromptUserTask || t instanceof PromptUserBuiltIn)
//			return 10;
		
		// Check the weight of a property
		if (t instanceof AtomEvalTask)
		{
			SWRLPredicate pred = ((AtomEvalTask)t).getAtom().getPredicate(); 
			if (pred instanceof OWLProperty<?,?>)
			{
				Set<OWLLiteral> costs = dataProperties(individual(((OWLProperty<?,?>)pred).getIRI()), 
						 					"hasCost");
				int cost = Integer.MAX_VALUE;
				for (OWLLiteral l : costs)
				{
					try { cost = Math.min(cost, Integer.parseInt(l.getLiteral())); }
					catch (Throwable ex) { ex.printStackTrace(); }
				}
				if (cost < Integer.MAX_VALUE)
					return cost;
			}
		}
		else if (t instanceof BuiltInAtomTask)
		{
			// We need to find the cost of the implementation
			SWRLBuiltInAtom atom = (SWRLBuiltInAtom)((BuiltInAtomTask)t).getAtom();
			OWLLiteral literal = dataProperty(individual(atom.getPredicate()), "hasCost");
			if (literal == null)
				return 1;
			else
				return Integer.parseInt(literal.getLiteral());
		}
		return 1;
	}
	
	/**
	 * <p>
	 * Score the importance of a task, which determines its priority of execution. The lower the score,
	 * the earlier in the workflow the task will be attempted. So this is more like an "inverse" priority.
	 * </p>
	 * 
	 * <p>
	 * The calculation is based on (1) the cost of the task execution (the higher the cost the higher 
	 * the score) (2) the number of occurrence
	 * of the task's atom (the higher the number the lower the score) and (3) the tasks that it depends upon
	 * (sums of their scores added the this task's score - this makes sure that if task A depends on task B,
	 * we have score(A) > score(B)) 
	 * </p>
	 * 
	 * @param t
	 * @return
	 */
	double scoreTask(Task t)
	{
		// the score of a task depends on the SWRLAtom's "importance count", on the
		// "cost" to actually execute it and on its dependencies on other tasks
		// that must be executed before it
		int atomCount = 1;
		if (t.getAssignedScore() >= 0)
			return t.getAssignedScore();
		else if (t instanceof AtomEvalTask)
		{
			atomCount = getOccurrenceCount(((AtomEvalTask)t).getAtom());
		}
		int taskCost = getTaskCost(t);
		
		double score = (double)taskCost / (double)atomCount;
		Map<IRI, Pair<Task, IRI>> m = varBindings.get(t);
		if (m != null) for (Pair<Task, IRI> p : m.values())
		{
			score += scoreTask(p.getFirst());
		}
		return score;
	}
	
	double scoreTaskPath(SortedSet<Task> path)
	{		
		double score = 0;
		for (Task t : path)
		{
			score += scoreTask(t);
		}
		return score;
	}	
	
	void incOccurrenceCount(SWRLAtom a)
	{
		AtomPartialKey key = new AtomPartialKey(a);
		Integer cnt = atomOccurrenceCounts.get(key);
		if (cnt == null)
			atomOccurrenceCounts.put(key, 1);
		else
			atomOccurrenceCounts.put(key, cnt + 1);
	}
	
	int getOccurrenceCount(SWRLAtom a)
	{
		Integer cnt = atomOccurrenceCounts.get(new AtomPartialKey(a));
		if (cnt == null)
		{
			atomOccurrenceCounts.get(new AtomPartialKey(a));
			throw new NullPointerException("No occcurrence count for: " + a);
		}
		return cnt == null ? 0 : cnt;
	}
	
	public Workflow createWorkflow()
	{
		return createWorkflow(rulesOntology.getAxioms(AxiomType.SWRL_RULE));
	}
	
	public Workflow createWorkflow(Set<SWRLRule> rules)
	{
//		this.checkRulesDBConsistency();
		final Workflow workflow = new Workflow();
		HashSet<SWRLRule> allRules = new HashSet<SWRLRule>(); 
		HashSet<SWRLRule> goalRules = new HashSet<SWRLRule>();
		bo = new OWLNamedIndividualHGDB(boOntology.getOntologyID().getOntologyIRI().resolve("#bo"));
		
		for (SWRLRule rule : rules)
		{
			HGHandle ruleHandle = ruleToHGDB(rule);
			rule = graph.get(ruleHandle);
//			System.out.println(rule);
			if (EventRule.isEventRule(rule))
				workflow.getEventRules().add(ruleHandle.getPersistent().toString());
			else
				allRules.add(rule);
			for (SWRLAtom a : rule.getHead())
				if (isEndGoal(a))
				{
					workflow.getEndgoals().add(a);
					goalRules.add(rule);
				}
		}

		if (workflow.getEndgoals().isEmpty())
		{
			throw new RuntimeException("No end goals identified in rule set. Can't create a workflow.");
		}
		System.out.println("End goals: " + workflow.getEndgoals());
		
//		this.checkRulesDBConsistency();
		
		if (evalRules(allRules))
		{
//			System.out.println("End goal satisfied already, no workflow to be created.");
			return null;
		}
		
//		this.checkRulesDBConsistency();
		
		for (AppliedRule rule : this.unresolvedRules)
		{
			for (SWRLAtom a : rule.rule.getBody())
			{
				a = rule.substituteVars(a);
				incOccurrenceCount(a);
//				HGHandle h = graph.getHandle(a);
//				if (h == null)
//				{
//					System.err.println("No handle for "  +a);
//				}
//				this.atomOccurrenceCounts.put(a, 
//											  (int)hg.count(graph, hg.and(hg.incident(h), 
//													  					  hg.type(SWRLBody.class))));
			}			
			rule.computeDependents();
		}
		
		computeGlobalVariableDependencies(unresolvedRules);
		
//		this.checkRulesDBConsistency();
		
//		TraceUtils.trace(System.out, atomOccurrenceCounts, true, true);
//		TraceUtils.trace(System.out, atomOccurrenceCounts, true, false);
		
//		for (SWRLRule rule : goalRules)
//			TraceUtils.trace(System.out, appliedRules.get(rule), true);
		
		List<GoalSet> allPaths = new ArrayList<GoalSet>();
		for (SWRLRule rule : goalRules)
		{
//			TreeSet<SWRLAtom> S = new TreeSet<SWRLAtom>(new AtomComparator());
			AppliedRule applied = appliedRules.get(rule);
//			System.out.println("Calculating paths from " + rule);			
			for (Iterator<GoalSet> I  = enumerateDerivationPaths(applied); I.hasNext(); )
			{
				GoalSet newPath = I.next();
				if (newPath.isEmpty())
					continue;
				allPaths.add(newPath);
				System.out.println("Path calculated: " + newPath);				
			}
		}
		
//		this.checkRulesDBConsistency();
		
		SortedSet<SortedSet<Task>> taskPaths = new TreeSet<SortedSet<Task>>(new TaskPathComparator());		
		for (GoalSet S : allPaths)
		{
//			System.out.println("Path:" + S);
//			for (SWRLAtom a : S) System.out.println(pathElements.get(new AtomPartialKey(a)));
			
			SortedSet<Task> taskPath = new TreeSet<Task>(new TaskComparator());			
			for (SWRLAtom atom : S)
				expandPathElement(S.getWorkflowElement(atom), taskPath);
			
//			for (Task t : taskPath)
//				System.out.println("score(" + t + ") = " + scoreTask(t));
			
			taskPath = injectGlobalDependencies(taskPath, S);
			
			System.out.println("Task Path before assertions: " + taskPath);
			// Propage all inferences until the top goal...loop until 
			// the list of tasks stops changing.
			for (int size = taskPath.size(); ;size = taskPath.size())
			{
//				System.out.println("Before assertions: " + taskPath);				
				addAssertionTasks(taskPath);
//				System.out.println("After assertions: " + taskPath);
				if (size == taskPath.size()) break;
			}
			for (Task t : taskPath)
				System.out.println(t + "->" + scoreTask(t));			
			
//			System.out.println("----------------------------");
			
			System.out.println("Task path ready( " + scoreTaskPath(taskPath) + "): " + taskPath);
			taskPaths.add(taskPath);			
		}

//		this.checkRulesDBConsistency();
		
		
		// The following two variables work in tandem to lazily manage unique identifiers (prototype ids) for task
		// clones
		final HashMap<Task, HGPersistentHandle> prototypeMap = new HashMap<Task, HGPersistentHandle>();
		final RefResolver<Task, HGPersistentHandle> prototypeResolver = new RefResolver<Task, HGPersistentHandle>() {
			public HGPersistentHandle resolve(Task key) 
			{
				HGPersistentHandle value = prototypeMap.get(key);
				if (value == null)
				{
//					if (key.toString().contains("atAddress"))
//					{
//						System.out.println("No equiv found for " + key + " " + key.hashCode());
//					}
					value = graph.getHandleFactory().makeHandle();
					prototypeMap.put(key, value);
				}
				return value;
			}
		};
		
		// The following two variables work in tandem to lazily manage separate task clones for each derivation
		// path (as identified by an integer id)
		final HashMap<Pair<Integer, Task>, Task> map = new HashMap<Pair<Integer, Task>, Task>();
		RefResolver<Pair<Integer, Task>, Task> workflowTask = new RefResolver<Pair<Integer, Task>, Task>() {
			public Task resolve(Pair<Integer, Task> key) 
			{
				// A given task may be cloned several times for several different
				// derivation paths. However we want to maintain variable dependencies
				// with a single "original" task so that even if a different path is
				// taken after a task is executed, the variables that it instantiated
				// are properly resolved. To do that, we store a 'prototypeId' with each
				// clone and that's what identifies the variable. This id must be unique
				// within the workflow, we are using HGDB handles just because it's a convenient
				// ID generation mechanism.
				if (key.getSecond().getPrototypeId() == null)
				{
//					System.out.println("Generating new proto for : " + key);
					key.getSecond().setPrototypeId(prototypeResolver.resolve(key.getSecond()));
				}
				
				Task value = map.get(key);
				if (value == null)
				{
					value = GenUtils.cloneBean(key.getSecond());
					graph.add(value);
					map.put(key, value);
				}
				return value;
			}
		};
		
		WorkflowStep successStep = new WorkflowDone(true, null);		
		WorkflowStep failureStep = new WorkflowDone(false, "Tried everything, but goal unreachable");
		WorkflowStep waitStep = new WaitStep();
		
		graph.add(successStep);
		graph.add(failureStep);
				
		//System.out.println(taskPaths);
		ArrayList<SortedSet<Task>> pathList = new ArrayList<SortedSet<Task>>();
		pathList.addAll(taskPaths);
		for (int i = 0; i < pathList.size(); i++)
		{
			SortedSet<Task> path = pathList.get(i);
			SortedSet<Task> headSet = new TreeSet<Task>(new TaskComparator());
			Iterator<Task> iter = path.iterator();
			Task next = iter.next();
			do
			{		
				Task curr = next;
				Task wt = workflowTask.resolve(new Pair<Integer, Task>(i, curr));
				if (workflow.getStartingStep() == null) workflow.setStartingStep(wt);
				headSet.add(curr);				
				next = iter.hasNext() ? iter.next() : null;
				
				// The following tasks don't need branching because their evaluation result is always true.
				if (wt instanceof AssertAtomTask  || wt instanceof BuiltInAtomTask)
				{
//					if (next instanceof AtomEvalTask && wt instanceof AssertAtomTask && 
//							((AssertAtomTask)wt).getAtom().equals(
//							((AtomEvalTask)next).getAtom()))
//						next = iter.hasNext() ? iter.next() : null;					
					if (next == null)
					{
						wt.setNext(successStep);
						break;
					}
					else
					{						
						wt.setNext(workflowTask.resolve(new Pair<Integer, Task>(i, next)));
						continue;
					}
				}
					
				BranchOnAtomValue branch = new BranchOnAtomValue();
				branch.setSourceTask(wt);				
				branch.setSourceVariable(AtomEvalTask.evalResultVar);
								
				if (next == null)
					branch.setOnTrue(successStep);
				else
					branch.setOnTrue(workflowTask.resolve(new Pair<Integer, Task>(i, next)));
				branch.setOnFalse(failureStep);
				branch.setOnUnknown(waitStep);
				
				graph.add(branch);			
				
				for (int j = i + 1; j < pathList.size() && branch.getOnFalse() == failureStep; j++)
				{
					SortedSet<Task> nextPath = pathList.get(j);
					if (nextPath.contains(curr))
						continue;
					for (Task onFalse : nextPath)
						if (!headSet.contains(onFalse))
						{
							branch.setOnFalse(workflowTask.resolve(new Pair<Integer, Task>(j, onFalse)));
							break;
						}
				}
				wt.setNext(branch);
			} while (next != null);
		}
		
//		System.out.println("With end goals " + workflow.getEndgoals());
		saveWorkflow(workflow);
	
		System.out.println(varBindings);
		
		visit(workflow.getStartingStep(), new Mapping<WorkflowStep, Boolean>(){
			public Boolean eval(final WorkflowStep step)
			{
//				System.out.println("Step " + step + "->" + step.getAtomHandle());
//				if (step instanceof Task)
//					System.out.println("Proto: " + ((Task)step).getPrototypeId());
				if (step instanceof Task)
				{
					final Task wt = (Task)step;
					final Map<IRI, Pair<Task, IRI>> bindings = varBindings.get((Task)step);
					System.out.println("Bindings for "  + step + " -- " + bindings);
					if (bindings == null)
						return true;
					visit(workflow.getStartingStep(), new Mapping<WorkflowStep, Boolean>() {
						public Boolean eval(final WorkflowStep dstep)
						{
							if (! (dstep instanceof Task))
								return true;
							Task dt = (Task)dstep;
							for (IRI iri : bindings.keySet())
							{
								Pair<Task, IRI> p = bindings.get(iri);
								//Task dt = workflowTask.resolve(p.getFirst());
								if (!dt.equals(p.getFirst()))
									continue;
//								System.out.println("bind " + iri + " to " + p.getFirst() + ":" + 
//										p.getSecond() + " " + dt.getPrototypeId());							
								wt.getInputVariables().put(iri, new Pair<HGHandle, IRI>(
										dt.getPrototypeId(), p.getSecond()
										));								
							}
							return true;
						}
					}, new HashSet());
				}
				return true;
			}
		}, new HashSet());
		
//		for (Task t : varBindings.keySet())
//		{
//			Task wt = workflowTask.resolve(t);
//			Map<IRI, Pair<Task, IRI>> bindings = varBindings.get(t);
////			System.out.println("bind vars for " + t);
//			if (bindings != null)
//			{
//				for (IRI iri : bindings.keySet())
//				{
//					Pair<Task, IRI> p = bindings.get(iri);
//					Task dt = workflowTask.resolve(p.getFirst());
////					System.out.println("bind " + iri + " to " + p.getFirst() + ":" + 
////							p.getSecond() + " " + dt.getPrototypeId());							
//					wt.getInputVariables().put(iri, new Pair<HGHandle, IRI>(
//							dt.getPrototypeId(), p.getSecond()
//							));
//				}
////				graph.update(wt);
//			}
////			else
////				System.out.println("no bindings found");
//			
//		}
		System.out.println(workflow);
		return workflow;
	}

	private Boolean visit(WorkflowStep step, Mapping<WorkflowStep, Boolean> map, HashSet<WorkflowStep> visited)
	{
		if (step == null || visited.contains(step))
			return true;
		visited.add(step);
		Boolean b = map.eval(step);
		if (!b)
			return b;
		if (step instanceof Task)
			return visit(((Task)step).getNext(), map, visited);
		else if (step instanceof Branch)
		{
			for (WorkflowStep alternative : ((Branch)step).getAlternatives())
			{
				if (!visit(alternative, map, visited))
					return false;
			}
		}
		return Boolean.TRUE;
	}
	
	private void saveWorkflow(Workflow workflow)
	{
		graph.add(workflow);
	}

	private void checkRulesDBConsistency()
	{
		List<HGHandle> atoms = hg.findAll(graph,hg.typePlus(SWRLAtom.class));
		for (HGHandle atom : atoms)
		{
			List<HGHandle> bodies = hg.findAll(graph, hg.and(hg.type(SWRLBody.class), hg.incident(atom)));
			for (HGHandle b : bodies)
			{
				HGHandle rule = hg.findOne(graph, 
                        hg.and(hg.type(SWRLRuleHGDB.class), 
                                hg.incident(b)));
				if (rule == null)
					throw new RuntimeException("No rule found for body: " + b);
				else if (graph.get(rule) == null)
					throw new RuntimeException("Null rule found for body: " + b);
			}
			List<HGHandle> heads = hg.findAll(graph, hg.and(hg.type(SWRLHead.class), hg.incident(atom)));
			for (HGHandle h : heads)
			{
				HGHandle rule = hg.findOne(graph, 
                        hg.and(hg.type(SWRLRuleHGDB.class), 
                                hg.incident(h)));
				if (rule == null)
					throw new RuntimeException("No rule found for head: " + h);
				else if (graph.get(rule) == null)
					throw new RuntimeException("Null rule found for body: " + h);
			}
		}
	}
	
	public RulesToWorkflow()
	{		
	}
	
	public RulesToWorkflow(String graphLocation, 
						   OWLOntologyManager manager, 
						   OWLOntology rulesOntology, 
						   OWLOntology boOntology)
	{
		this.manager = manager;
		this.rulesOntology = rulesOntology;
		this.boOntology = boOntology;
		if (!HGEnvironment.isOpen(graphLocation))
		{
			HGUtils.dropHyperGraphInstance(graphLocation);
			graph = HGEnvironment.get(graphLocation);			
		}
		else
			graph = HGEnvironment.get(graphLocation);
		List<HGHandle> atoms = hg.findAll(graph, hg.typePlus(SWRLAtom.class));
		for (HGHandle h : atoms)
			graph.remove(h, false);
//		TypeUtils.deleteInstances(graph, 
//								  graph.getTypeSystem().getTypeHandle(SWRLRuleHGDB.class));
	}
	
	public static void main(String[] argv)
	{
		RulesToWorkflow r2w = new RulesToWorkflow();
		HGUtils.dropHyperGraphInstance("c:/temp/r2w");
		HyperGraph graph = r2w.graph = HGEnvironment.get("c:/temp/r2w");
		HGDBApplication.getInstance().install(r2w.graph);
		try
		{
			r2w.manager = SynchronizedOWLManager.createOWLOntologyManager();
			r2w.manager.loadOntologyFromOntologyDocument(new File(
					"c:/work/ontology/County_Working.owl"));
			r2w.rulesOntology = r2w.manager.loadOntologyFromOntologyDocument(new File(
					"c:/work/ontology/Garbage_Missed_Inquiry.swrl"));
			r2w.boOntology = r2w.manager.loadOntologyFromOntologyDocument(new File(
										"c:/work/ontology/Inquiry1.owl"));
			Set<SWRLRule> rules = r2w.rulesOntology.getAxioms(AxiomType.SWRL_RULE); 
			Workflow workflow = r2w.createWorkflow(rules);
			System.out.println(workflow);
//			SWRLAtom endgoal = workflow.getEndgoals().iterator().next();
//			HGHandle goalHandle = graph.getHandle(endgoal);
//			List<HGHandle> heads = hg.findAll(graph, hg.and(hg.type(SWRLHead.class), hg.incident(goalHandle)));
//			for (HGHandle head : heads)
//			{
//				System.out.println("---------------------------------------------");
//				HGTraversal traversal = new HGDepthFirstTraversal(head, 
//						new DefaultALGenerator(graph, hg.type(SWRLRuleHGDB.class), null, true, false, false));
//				traversal = new HyperTraversal(graph, traversal);
//				while (traversal.hasNext())
//				{
//					Pair<HGHandle, HGHandle> curr = traversal.next();
//					System.out.println("-> " + graph.get(curr.getFirst()) + " -> " + graph.get(curr.getSecond()));
//				}
//			}
//			List<SWRLRule> L = hg.getAll(r2w.graph, hg.typePlus(SWRLRule.class));
//			for (SWRLRule x : L)
//				System.out.println(x);
		}
		catch (Throwable t)
		{
			t.printStackTrace(System.err);
			System.exit(-1);
		}
	}
}

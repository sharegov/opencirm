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

import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.Calendar;
import java.util.Set;

import mjson.Json;
import static mjson.Json.object;
import static org.sharegov.cirm.OWL.*;
import org.hypergraphdb.util.Pair;
import org.hypergraphdb.util.RefResolver;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.SWRLAtom;
import org.semanticweb.owlapi.model.SWRLBuiltInAtom;
import org.semanticweb.owlapi.model.SWRLDArgument;
import org.semanticweb.owlapi.model.SWRLLiteralArgument;
import org.semanticweb.owlapi.model.SWRLRule;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.sharegov.cirm.rules.EvaluateAtom;
import org.sharegov.cirm.utils.EvalUtils;
import org.sharegov.cirm.utils.RESTClient;

/**
 * 
 * <p>
 * Represents an asynchronous event rule. This looks like a normal SWRL rule, except it's 
 * interpreted differently: it is not included in the goal-directed workflow. Rather, it
 * creates an event to be fired in the future and possibly changing the direction of the 
 * normal workflow. 
 * </p>
 *
 * <p>
 * This class is just a wrapper that manages the behavior of a SWRL event rule. An event rule is 
 * detected as such if any of the following conditions are met:
 * 
 * <ul>
 * <li>
 * A <code>http://www.miamidade.gov/ontology#timeElapsed</code> built-in is invoked in the
 * rule's premises.
 * </li>
 * <li>
 * Maybe some other way in the future....
 * </li>
 * </ul>
 * 
 * </p>
 * 
 * @author Borislav Iordanov
 *
 */
public class EventRule
{
	static SimpleDateFormat ISO8601Local = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	 
	private SWRLRule rule;
	private Json data;
	private AppliedRule applied;
	
	private synchronized AppliedRule evaluate(WorkflowExecutionContext ctx)
	{
		if (this.applied != null)
			return this.applied;
		
		final AppliedRule applied = new AppliedRule();
		applied.rule = rule;
		for (boolean again = true; again; )		
		{
			again = false;
			for (SWRLAtom atom : rule.getBody())
			{			
				Pair<SWRLVariable, OWLObject> assignment = 
					EvalUtils.dispatch(new VarAssignment(ctx.getBusinessObjectOntology().getOntology(), 
							applied.getVarResolver()), atom);
				if (assignment != null && applied.valueOf(assignment.getFirst()) == null)
				{
					again = true;
					applied.assign(assignment.getFirst(), assignment.getSecond());
				}
			}
		}
		for (SWRLAtom atom : rule.getBody())
		{
			RefResolver<SWRLVariable, OWLObject> varresolver = new RefResolver<SWRLVariable, OWLObject>()
			{
				public OWLObject resolve(SWRLVariable var) { return applied.valueOf(var); }
			};
			AtomValue value = EvalUtils.dispatch(new EvaluateAtom(varresolver, 
					ctx.getBusinessObjectOntology().getOntology()), atom);
			SWRLAtom instantiated = EvalUtils.dispatch(new AtomInstantiation(applied), atom);
			applied.instantiated.put(atom, instantiated);
			applied.truthValues.put(atom, value);
		}
		return this.applied = applied;
	}
	
	public EventRule(SWRLRule rule, Json data)
	{
		this.rule = rule;
		this.data = data;
	}
	
	private static SWRLBuiltInAtom findBuiltIn(Set<SWRLAtom> S, IRI builtinName)
	{
		for (SWRLAtom atom : S)
		{
			if (! (atom instanceof SWRLBuiltInAtom))
				continue;
			if (((SWRLBuiltInAtom)atom).getPredicate().equals(builtinName))
				return (SWRLBuiltInAtom)atom;
		}
		return null;		
	}
	 
	/**
	 * @param rule
	 * @return
	 */
	public static boolean isEventRule(SWRLRule rule)
	{
		return findBuiltIn(rule.getBody(), RulesToWorkflow.timeElapsed) != null;
	}
	
	public boolean isActive(WorkflowExecutionContext ctx)
	{
		return data.is("active", true);	
	}
	
	public boolean isViable(WorkflowExecutionContext ctx)
	{
		evaluate(ctx);
		return applied.isSatisfied() != AtomValue.False;
	}
	
	public Calendar getFireTime()
	{
		SWRLBuiltInAtom bin = findBuiltIn(rule.getBody(), RulesToWorkflow.timeElapsed);
		bin = (SWRLBuiltInAtom)applied.instantiated.get(bin);
		SWRLDArgument arg1 = bin.getArguments().get(0);
		SWRLDArgument arg2 = bin.getArguments().get(1);
		if (arg1 instanceof SWRLVariable || arg2 instanceof SWRLVariable)
			return null; // can't activate, don't have the value of the base date yet
		String baseDate = ((SWRLLiteralArgument)arg1).getLiteral().getLiteral();
		String offsetPeriod = ((SWRLLiteralArgument)arg2).getLiteral().getLiteral();
		
		Calendar cal = Calendar.getInstance();
		try
		{
			cal.setTime(ISO8601Local.parse(baseDate));
		}
		catch (ParseException e)
		{
			throw new RuntimeException(e);
		}
		char periodType = offsetPeriod.charAt(offsetPeriod.length()-1);
		int periodValue = Integer.parseInt(offsetPeriod.substring(0, offsetPeriod.length()-1));
		switch (periodType)
		{
			case 'd':cal.add(Calendar.DATE, periodValue); break;
			case 'h':cal.add(Calendar.HOUR, periodValue); break;
			case 'm':cal.add(Calendar.MINUTE, periodValue); break;
			default:throw new RuntimeException("Invalid period format : " + offsetPeriod);
		}
		return cal;
	}
	
	@SuppressWarnings("deprecation")
	public void activate(WorkflowExecutionContext ctx)
	{
		if (isActive(ctx))
			throw new IllegalStateException("Event rule trigger already active.");
		else if (!isViable(ctx))
			throw new IllegalStateException("Event rule is not viable and won't be activated.");
		
		evaluate(ctx);
		try
		{
			Calendar cal = getFireTime();
			if (cal == null)
				return;
			String callbackUrl = null;
			OWLLiteral thisUrl = dataProperty(individual("CiRMOperationService"), "hasUrl");
			if (thisUrl == null)
				throw new RuntimeException("Missing CiRMOperationService information from ontology.");
			callbackUrl = thisUrl.getLiteral() + "/eventrule?boiri=" + 
				URLEncoder.encode(ctx.getBusinessObject().getIRI().toString() + "&id=" + 
						data.at("hghandle"));
			// Call Time Machine REST Service to submit the timer
			OWLLiteral tmUrl = dataProperty(individual("TimeMachineService"), "hasUrl");
			if (tmUrl == null)
				throw new RuntimeException("Missing TimeMachine information.");
			Json taskSpec = object();
			taskSpec.set("myurl", callbackUrl)
					.set("time", object()
						.set("day", cal.get(Calendar.DATE))
						.set("month", cal.get(Calendar.MONTH + 1))
						.set("year", cal.get(Calendar.YEAR))
						.set("hour", cal.get(Calendar.HOUR_OF_DAY))
						.set("minute", cal.get(Calendar.MINUTE))
						.set("second", cal.get(Calendar.SECOND))
			);					
			Json result = RESTClient.post(tmUrl.getLiteral(), taskSpec);
			if (!result.is("ok", true))
				throw new RuntimeException("Unable to schedule task: " + result.at("error"));
			else {
				data.set("taskid", result.at("taskid"));
				data.set("active", true);
				data.set("time", cal.getTimeInMillis());
			}
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
	}
	
	public void deactivate(WorkflowExecutionContext ctx)
	{
		if (!isActive(ctx))
			throw new IllegalStateException("Event rule trigger is not currently active.");
		OWLLiteral tmUrl = dataProperty(individual("TimeMachineService"), "hasUrl");
		if (tmUrl == null)
			throw new RuntimeException("Missing TimeMachine information.");		
		Json result = RESTClient.del(tmUrl + "/task/" + data.at("taskid"));
		if (!result.is("ok", true))
			throw new RuntimeException("Unable to delete task " + data.at("taskid") + ": " + result.at("error"));		
		else
		{
			data.set("active", false);
		}
	}
	
	public void fire(WorkflowExecutionContext ctx)
	{
		evaluate(ctx);
		if (applied.isSatisfied() == AtomValue.True)
		{
			for (SWRLAtom a : rule.getHead())
			{
				// We eval built-ins and assert everything else.
				if (a instanceof SWRLBuiltInAtom)
					new BuiltInAtomTask((SWRLBuiltInAtom)a).perform(ctx);
				else
					new AssertAtomTask(a).perform(ctx);
			}
		}
	}
}

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

import static org.sharegov.cirm.OWL.fullIri;


import static org.sharegov.cirm.OWL.ontology;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hypergraphdb.HGHandle;
import org.hypergraphdb.HGPersistentHandle;
import org.hypergraphdb.annotation.AtomReference;
import org.hypergraphdb.util.Pair;
import org.hypergraphdb.util.RefResolver;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataRange;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.model.SWRLDArgument;
import org.semanticweb.owlapi.model.SWRLIArgument;
import org.semanticweb.owlapi.model.SWRLIndividualArgument;
import org.semanticweb.owlapi.model.SWRLLiteralArgument;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
// TODO: the following dependency should be removed (refact the getForm code somewhere else)
import org.sharegov.cirm.rest.UiService;

import mjson.Json;

public abstract class Task implements WorkflowStep
{
	@AtomReference("symbolic")
	private WorkflowStep next;
	private Set<IRI> outputVariables = new HashSet<IRI>();
	protected HGHandle handle;
	protected HGPersistentHandle prototypeId;
	private double assignedScore = -1.0;

	// The following represents variable bindings from upstream (where values are output)
	// steps to downstream steps (where values are received) 
	// local input var <-  pair(upstream task, upstream output var)  
	private Map<IRI, Pair<HGHandle, IRI>> inputVariables = new HashMap<IRI, Pair<HGHandle, IRI>>();

	protected RefResolver<SWRLVariable, OWLObject> getVarResolver(final WorkflowExecutionContext ctx)
	{	
		return new RefResolver<SWRLVariable, OWLObject>(){
			public OWLObject resolve(SWRLVariable var)
			{
				Pair<HGHandle, IRI> globalId = getInputVariables().get(var.getIRI());
				if (globalId == null)
					globalId = new Pair<HGHandle, IRI>(getPrototypeId(), var.getIRI());
				OWLObject x = (OWLObject)ctx.getVariable(globalId == null ? getPrototypeId() : globalId.getFirst(), 
											  globalId == null ? var.getIRI() : globalId.getSecond()); 
					//(OWLObject)ctx.getVariables().get(globalId);
				return (x == null) ? ctx.getVarResolver().resolve(var) : x;
			}
		};
	}

	protected void deleteObjectProperty(final WorkflowExecutionContext ctx,
										OWLObjectProperty prop, 
										OWLIndividual ind, 
										Json value)
	{
		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory df = manager.getOWLDataFactory();		
		Set<OWLIndividual> all = ind.getObjectPropertyValues(prop, ctx.getBusinessObjectOntology().getOntology());
		for (OWLIndividual x : all)
		{
			OWLAxiom axiom = df.getOWLObjectPropertyAssertionAxiom(prop, ind, x);
			manager.applyChange(new RemoveAxiom(ctx.getBusinessObjectOntology().getOntology(), axiom));				
		}		
	}
	
	protected OWLIndividual assignObjectProperty(final WorkflowExecutionContext ctx,
												 OWLObjectProperty prop, 
												 OWLIndividual ind, 
												 Json value)
	{
		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory df = manager.getOWLDataFactory();
		OWLIndividual object = null;
		
		OWLClassExpression objectType = null;
		for (OWLClassExpression range : prop.getRanges(ontology()))
			// TODO: we need to somehow pass the actual type of the property value
			// to the client (hidden form field perhaps) and back to the server
			// this is a hack because PromptUserTask currently uses the first range that has a form
			if (new UiService().getForm(range) != null)
			{
				objectType = range;
				break;
			}
		
		if (value.isObject())
			object = makeObjectIndividual(ctx,
										  fullIri(objectType.asOWLClass().getIRI().getFragment() + 
												  Refs.idFactory.resolve().newId()),
									      value);
		else 
			object = df.getOWLNamedIndividual(IRI.create(value.asString()));
		
		ArrayList<OWLOntologyChange> L = new ArrayList<OWLOntologyChange>();
		L.add(new AddAxiom(ctx.getBusinessObjectOntology().getOntology(),
				   df.getOWLClassAssertionAxiom(objectType, object)));		
		L.add(new AddAxiom(ctx.getBusinessObjectOntology().getOntology(), 
						   df.getOWLObjectPropertyAssertionAxiom(prop, ind, object)));
		manager.applyChanges(L);
		
//		for (OWLAxiom ax : ctx.getBusinessObjectOntology().getObjectPropertyAssertionAxioms(ctx.getBusinessObject()))
//			System.out.println("ax : " + ax);
		return object;
	}

	protected OWLLiteral toOWLLiteral(OWLDataFactory df, OWLDataRange range, Json value)
	{
		if (range instanceof OWLDatatype)
		{
			String v = value.asString();
			OWLDatatype type = (OWLDatatype)range;
			if (type.isBoolean())
			{
				if (v.equalsIgnoreCase("on") || v.equalsIgnoreCase("yes") ||
						v.equalsIgnoreCase("t") ||
						v.equalsIgnoreCase("true"))
					v = "true";
				else
					v = "false";
			}
			return df.getOWLLiteral(v, (OWLDatatype) range);
		}
		else
			return null;
	}

	protected void deleteDataProperty(final WorkflowExecutionContext ctx,
								      OWLDataProperty prop, 
									  OWLIndividual ind, 
									  Json value)
	{
		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory df = manager.getOWLDataFactory();		
		Set<OWLLiteral> all = ind.getDataPropertyValues(prop, ctx.getBusinessObjectOntology().getOntology());
		for (OWLLiteral x : all)
		{
			OWLAxiom axiom = df.getOWLDataPropertyAssertionAxiom(prop, ind, x);
			manager.applyChange(new RemoveAxiom(ctx.getBusinessObjectOntology().getOntology(), axiom));				
		}		
	}
	
	protected OWLLiteral assignDataProperty(final WorkflowExecutionContext ctx,
											OWLDataProperty prop, 
											OWLIndividual ind, 
											Json value)
	{
		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory df = manager.getOWLDataFactory();
		OWLOntology mainOntology = ontology();
		OWLLiteral literal = null;
		for (OWLDataRange range : prop.getRanges(mainOntology))
		{
			literal = toOWLLiteral(df, range, value);
			if (literal != null)
				break;
		}
		if (literal == null)
			literal = df.getOWLLiteral(value.toString());
		OWLAxiom axiom = df.getOWLDataPropertyAssertionAxiom(prop, ind, literal);
		manager.applyChange(new AddAxiom(ctx.getBusinessObjectOntology().getOntology(), 
										 axiom));
		return literal;
	}

	protected OWLIndividual makeObjectIndividual(final WorkflowExecutionContext ctx, 
												 IRI indIri,
												 Json properties)
	{
		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory df = manager.getOWLDataFactory();
		OWLOntology mainOntology = ontology();
		OWLIndividual result = indIri == null ? df.getOWLAnonymousIndividual() 
											   : df.getOWLNamedIndividual(indIri);
		for (Map.Entry<String, Json> e : properties.asJsonMap().entrySet())
		{
			IRI propIri = fullIri(e.getKey());
			try
			{
				if (mainOntology.getAxioms(df.getOWLDataProperty(propIri)).isEmpty())
					assignObjectProperty(ctx, df.getOWLObjectProperty(propIri),
										result, e.getValue());
				else
					assignDataProperty(ctx, df.getOWLDataProperty(propIri),
							result, e.getValue());
			}
			catch (RuntimeException ex)
			{
				System.out.println("Failed to assign property " + propIri);
				ex.printStackTrace(System.err);
			}
		}
		return result;
	}

	protected OWLObject assignProperty(final WorkflowExecutionContext ctx, 
									   OWLIndividual ind,
									   IRI propIri,
									   Json value)
	{
		OWLDataFactory df = OWL.dataFactory();
		OWLOntology mainOntology = ontology();
		try
		{
			if (mainOntology.getAxioms(df.getOWLDataProperty(propIri)).isEmpty())
				return assignObjectProperty(ctx, df.getOWLObjectProperty(propIri), ind, value);
			else			
			{
				return assignDataProperty(ctx, 
										  df.getOWLDataProperty(propIri), 
										  ind, 
										  value.at(propIri.getFragment()));
			}
		}
		catch (RuntimeException ex)
		{
			System.out.println("Failed to assign property " + propIri);
			throw ex;
		}
	}

	protected void deleteProperty(final WorkflowExecutionContext ctx, 
			   					  OWLIndividual ind,
			   					  IRI propIri,
								  Json value)
	{
		OWLDataFactory df = OWL.dataFactory();
		OWLOntology mainOntology = ontology();
		try
		{
			if (mainOntology.getAxioms(df.getOWLDataProperty(propIri)).isEmpty())
				deleteObjectProperty(ctx, df.getOWLObjectProperty(propIri), ind, value);
			else			
			{
				deleteDataProperty(ctx, 
								   df.getOWLDataProperty(propIri), 
								   ind, 
								   value != null ? value.at(propIri.getFragment()) : null);
			}
		}
		catch (RuntimeException ex)
		{
			System.out.println("Failed to assign property " + propIri);
			throw ex;
		}		
	}
	
	protected OWLIndividual getIndividual(WorkflowExecutionContext ctx, SWRLIArgument indarg)
	{
		if (indarg instanceof SWRLIndividualArgument)
			return ((SWRLIndividualArgument)indarg).getIndividual();
		else
		{
			SWRLVariable var = (SWRLVariable)indarg;
			return (OWLIndividual)getVarResolver(ctx).resolve(var);
		}
	}

	protected OWLLiteral getLiteral(WorkflowExecutionContext ctx, SWRLDArgument darg)
	{
		if (darg instanceof SWRLLiteralArgument)
			return ((SWRLLiteralArgument)darg).getLiteral();
		else
		{
			SWRLVariable var = (SWRLVariable)darg;
			return (OWLLiteral)getVarResolver(ctx).resolve(var);
		}
	}
	
	public HGPersistentHandle getPrototypeId()
	{
		return prototypeId;
	}

	public void setPrototypeId(HGPersistentHandle prototypeId)
	{
		this.prototypeId = prototypeId;
	}

	public HGHandle getAtomHandle()
	{
		return handle;
	}

	public void setAtomHandle(HGHandle handle)
	{
		this.handle = handle;
	}

	public void setOutputVariables(Set<IRI> outputVariables)
	{
		this.outputVariables = outputVariables;
	}

	public void setInputVariables(Map<IRI, Pair<HGHandle, IRI>> inputVariables)
	{
		this.inputVariables = inputVariables;
	}

	public Set<IRI> getOutputVariables()
	{
		return outputVariables;
	}
	
	public Map<IRI, Pair<HGHandle, IRI>> getInputVariables()
	{
		return inputVariables;
	}
	
	public WorkflowStep getNext()
	{
		return next;
	}

	public void setNext(WorkflowStep next)
	{
		this.next = next;
	}

	public double getAssignedScore()
	{
		return assignedScore;
	}

	public void setAssignedScore(double assignedScore)
	{
		this.assignedScore = assignedScore;
	}	
}

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

import static org.sharegov.cirm.OWL.ontology;


import java.util.HashMap;

import org.hypergraphdb.HGEnvironment;
import org.hypergraphdb.HyperGraph;
import org.hypergraphdb.app.management.HGManagement;
import org.hypergraphdb.app.owl.HGDBApplication;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.OntologyLoader;
import org.sharegov.cirm.utils.DirectRef;
import org.sharegov.cirm.utils.ObjectRef;
import org.sharegov.cirm.utils.SingletonRef;

/**
 * 
 * <p>
 * Entry point for managing runtime workflow instances.
 * </p>
 *
 * @author Borislav Iordanov
 *
 */
public class WorkflowManager
{
	// TMP
	HashMap<IRI, WorkflowExecutionContext> workflowContexts = new HashMap<IRI, WorkflowExecutionContext>();
	HashMap<IRI, Workflow> workflows = new HashMap<IRI, Workflow>();
	
	public HyperGraph getGraph()
	{
		HyperGraph graph = HGEnvironment.get("c:/temp/r2w");
		HGManagement.ensureInstalled(graph, HGDBApplication.getInstance());
		return graph;
	}
	
	public static SingletonRef<WorkflowManager> ref = 
		new SingletonRef<WorkflowManager>(new ObjectRef<WorkflowManager>(DirectRef.make(WorkflowManager.class)));
	
	public static WorkflowManager getInstance() { return ref.resolve(); }
	
	public WorkflowManager()
	{
	}
	
	public WorkflowExecutionContext getWorkflowContext(IRI businessObjectId)
	{
		return workflowContexts.get(businessObjectId);
	}
		
	/**
	 * <p>
	 * </p>
	 * @param businessObjectType
	 * @return
	 */
	public WorkflowExecutionContext makeRuntimeContext(IRI businessObjectType)
	{
		WorkflowExecutionContext context = new WorkflowExecutionContext();
		context.setWorkflow(this.getWorkflowForType(businessObjectType));
		context.setHyperGraph(getGraph());
		return context;
	}

	public WorkflowExecutionContext startWorkflow(BOntology bontology)
	{
		Workflow workflow = getWorkflowForType(bontology.getTypeIRI());
		WorkflowExecutionContext context = new WorkflowExecutionContext();
		context.setWorkflow(workflow);
		context.setCurrentStep(context.getWorkflow().getStartingStep());
		context.setBusinessObjectOntology(bontology);
		return context;
	}
	
	public Workflow getWorkflowForType(IRI boType)
	{
		Workflow result = workflows.get(boType);
		if (result != null && false)
			return result;
		OWLOntologyManager manager = Refs.tempOntoManager.resolve();		
		OntologyLoader loader = OWL.loader();
		OWLOntology rulesOntology = loader.get(Refs.SWRL_PREFIX + "/" + boType.getFragment());
		
		IRI ontologyIRI = IRI.create("http://www.miamidade.gov/bo/" + 
									  boType.getFragment() + "/prototype");
		// The business object is created in its own ontology.
		OWLOntology boOntology;
		try
		{
			boOntology = manager.getOntology(ontologyIRI);
			if (boOntology == null)
			{
				boOntology = manager.createOntology(ontologyIRI);
				manager.applyChange(new AddImport(boOntology, manager.getOWLDataFactory().getOWLImportsDeclaration(
											ontology().getOntologyID().getOntologyIRI())));
				
				OWLNamedIndividual businessObject = manager.getOWLDataFactory().getOWLNamedIndividual(ontologyIRI.resolve("#bo"));
				OWLAxiom axiom = manager.getOWLDataFactory().getOWLClassAssertionAxiom(
						manager.getOWLDataFactory().getOWLClass(boType), 
						businessObject); 
				manager.applyChange(new AddAxiom(boOntology, axiom));
			}			
			
			RulesToWorkflow r2w = new RulesToWorkflow(getGraph().getLocation(), 
													  OWL.manager(),
													  rulesOntology, 
													  boOntology);
			result = r2w.createWorkflow();
			workflows.put(boType, result);
			return result;
		}
		catch (OWLOntologyCreationException e)
		{
			throw new RuntimeException(e);
		}		
	}
}

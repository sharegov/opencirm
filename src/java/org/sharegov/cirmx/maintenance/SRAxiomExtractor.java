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
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hypergraphdb.app.owl.util.AxiomComparator;
import org.hypergraphdb.app.owl.util.OntologyComparator;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.FileDocumentTarget;
import org.semanticweb.owlapi.io.OWLXMLOntologyFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.owl.OWLEntityCondition;

/**
 * Finds exactly those axioms that relate to a ServiceCase individual in a manner that matches Syed's per SR exports.
 * So it does not include any axioms, assertions or annotations about the objects (!) of:
 * 			legacy:hasDefaultIntakeMethod
 *			legacy:hasDefaultPriority
 *			legacy:hasLegacyInterface
 *			legacy:hasServiceActor
 *			legacy:hasAutoServiceActor
 *			legacy:hasStatus
 *			legacy:hasDefaultStatus
 *
 * @author Thomas Hilpold
 *
 */
public class SRAxiomExtractor
{
	
		/**
		 * Ontology, String namedIndividualIRI
		 * @param argv
		 */
		public static void main(String[] argv) {
			if (argv == null || argv.length != 2) {
				System.err.println("Usage: SRAxiomExtractor ontologyFile individualIRI");
				System.err.println("individualIRI may be abbreviated.");
				System.exit(-1);
			}
			String individual = argv[1];
			File ontologyFile = new File (argv[0]);
			if (!ontologyFile.exists()) {
				System.err.println("File does not exist: " + ontologyFile.getAbsolutePath());
				System.exit(-1);
			}
			SRAxiomExtractor axex = new SRAxiomExtractor();
			try {
				axex.extractSRAxioms(individual, ontologyFile);
			} catch (Exception e) 
			{
				e.printStackTrace(System.err);
				System.exit(-1);
			}
		}
		
		public void extractSRAxioms(String serviceCaseIndividual, File ontologyFile) throws Exception
		{
			OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
			manager.setSilentMissingImportsHandling(true);
			System.out.println(" Service case axiom extraction ");
			System.out.println("Loading from Ontology : " + ontologyFile.toURI());
			OWLOntology ontology = manager.loadOntologyFromOntologyDocument(ontologyFile);
			OWLNamedIndividual ind = manager.getOWLDataFactory().getOWLNamedIndividual(IRI.create(serviceCaseIndividual));
			String srFragment = ind.getIRI().getFragment();
			// find all SR related axioms
			List<OWLAxiom> found = getRelatedAxioms(ind, ontology);
			System.out.println(" AXIOMS THAT ARE RELATED TO " + ind + " :" + found.size());
			System.out.println(" ADDED AXIOMS THAT ARE NOT RELATED TO " + ind + " :" + (ontology.getAxiomCount() - found.size()));
			OntologyComparator.saveAxioms(new File(ontologyFile.getAbsolutePath() + "_" + srFragment + "_related_DFS.owl"), found);
			Collections.sort(found, new AxiomComparator());
			OntologyComparator.saveAxioms(new File(ontologyFile.getAbsolutePath() + "_" + srFragment + "_related_sorted.owl"), found);
			OWLOntology related = manager.createOntology(new HashSet<OWLAxiom>(found), IRI.create("RELATED"));
			manager.saveOntology(related, new OWLXMLOntologyFormat(), new FileDocumentTarget(new File(ontologyFile.getAbsolutePath() + "_" + srFragment + "_related.owlxml.owl")));
		}
		
		
		public List<OWLAxiom> getRelatedAxioms(OWLNamedIndividual serviceCaseInd, OWLOntology o)
		{
			OntologyGraphSearch gs = new OntologyGraphSearch();
			return gs.findAllConnectedDFS(o, serviceCaseInd, getSRStopExpansionCondition(), getReverseTraversalProperties());
		}
		
		
		public OWLEntityCondition getSRStopExpansionCondition()
		{
			Set<OWLEntity> stopExpandEntities = new HashSet<OWLEntity>();
			stopExpandEntities.add(OWL.objectProperty("legacy:hasDefaultIntakeMethod")); // don't expand (== include any axioms about) Phone
			stopExpandEntities.add(OWL.objectProperty("legacy:hasDefaultPriority")); // don't expand any Priority
			stopExpandEntities.add(OWL.objectProperty("legacy:hasLegacyInterface"));
			stopExpandEntities.add(OWL.objectProperty("legacy:hasServiceActor"));
			stopExpandEntities.add(OWL.objectProperty("legacy:hasAutoServiceActor"));
			stopExpandEntities.add(OWL.objectProperty("legacy:hasStatus"));
			stopExpandEntities.add(OWL.objectProperty("mdc:hasStatus"));
			stopExpandEntities.add(OWL.objectProperty("legacy:hasDefaultStatus"));
			stopExpandEntities.add(OWL.objectProperty("legacy:providedBy"));
			//stopExpandEntities.add(OWL.objectProperty("legacy:hasDefaultOutcome"));
			//stopExpandEntities.add(OWL.objectProperty("legacy:hasAllowableOutcome"));
			//stopExpandEntities.add(OWL.objectProperty("legacy:hasOutcome"));
			stopExpandEntities.add(OWL.individual("legacy:MDC_DEPARTMENTS"));			
			return new OWLEntityConditionImpl(stopExpandEntities);
		}

		
		/**
		 * Normal traversal goes from Subject via OP to object, but we sometimes need to traverse 
		 * object OP subject to make sure we visit the following:
		 * 
		 * ActivityTriggers TRIGGER legacy:hasActivity ACT 
		 * 
		 * ServicecaseOutcomeTriggers SCOT legacy:hasServiceCase SC
		 * 
		 * QuestionTriggers NNNNNN legacy:hasAnswerObject ChoiceValue
		 * @return
		 */
		public Set<OWLObjectProperty> getReverseTraversalProperties() 
		{
			Set<OWLObjectProperty> s = new HashSet<OWLObjectProperty>();
			s.add(OWL.objectProperty("legacy:hasActivity"));
			s.add(OWL.objectProperty("legacy:hasServiceCase"));
			s.add(OWL.objectProperty("legacy:hasAnswerObject"));
			return s;
		}

}

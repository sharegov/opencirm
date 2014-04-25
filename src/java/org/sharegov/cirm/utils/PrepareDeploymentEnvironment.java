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
package org.sharegov.cirm.utils;


import java.io.File;

import mjson.Json;

import org.hypergraphdb.app.owl.HGDBOWLManager;
import org.hypergraphdb.app.owl.HGDBOntologyFormat;
import org.hypergraphdb.app.owl.HGDBOntologyManager;
import org.hypergraphdb.app.owl.HGDBOntologyOutputTarget;
import org.hypergraphdb.app.owl.HGDBOntologyRepository;
import org.hypergraphdb.app.owl.versioning.distributed.activity.BrowseRepositoryActivity;
import org.hypergraphdb.app.owl.versioning.distributed.activity.PullActivity;
import org.hypergraphdb.peer.workflow.ActivityResult;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.OntologyLoader;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.owl.Model;
import org.sharegov.cirm.owl.OwlRepo;
import org.sharegov.cirm.owl.SynchronizedOWLManager;

import com.clarkparsia.pellet.owlapiv3.PelletReasonerFactory;

public class PrepareDeploymentEnvironment
{
	private static HGDBOntologyManager manager;
	private static HGDBOntologyRepository repository;
	
	static IRI getHGDBIRI(IRI ontologyIRI)
	{
    	String iriNoScheme = ontologyIRI.toString();
    	String scheme = ontologyIRI.getScheme();
    	iriNoScheme = iriNoScheme.substring(scheme.length());
    	IRI docIRI = IRI.create("hgdb" + iriNoScheme);  
    	return docIRI;
	}
	
	static void importOntology() throws Exception
	{
		//Initialisation of repository location before everything else:
		HGDBOntologyRepository.setHypergraphDBLocation(StartUp.config.at("metaDatabaseLocation").asString());
		//This will also create the repository.
		HGDBOntologyManager manager = HGDBOWLManager.createOWLOntologyManager();
		new OntologyLoader(manager); // add file->IRI mappings to manager
		//Manager tries all parsers to load from file:

		OWLOntology onto = manager.loadOntologyFromOntologyDocument(
				IRI.create(Refs.defaultOntologyIRI.resolve()));
		
//		OWLOntology onto = manager.loadOntologyFromOntologyDocument(
//				IRI.create(new File("c:/work/cirmservices/src/ontology/County_Working.owl")));
//		
//		onto = manager.loadOntologyFromOntologyDocument(
//				IRI.create(new File("c:/work/cirmservices/src/ontology/csr.owl")));
//				//IRI.create(StartUp.config.at("defaultOntologyIRI").asString()));
		
		//Define a repository document IRI for our ontology
		IRI targetIRI = getHGDBIRI(onto.getOntologyID().getOntologyIRI());
		HGDBOntologyOutputTarget target = new HGDBOntologyOutputTarget(targetIRI);
		//Manager will find our HGDBStorer based on the format and 
		// import the ontology into our repo. Same for other Formats.
		manager.saveOntology(onto, new HGDBOntologyFormat(), target);

		// To use the ontology: load from Repository:
		// Make sure targetIRI starts with hgdb:// 
		OWLOntology dbOnto= manager.loadOntologyFromOntologyDocument(targetIRI);
	}
	
	public static void registerRepoToResolveImports() {
		manager.addIRIMapper(new OWLOntologyIRIMapper() {			
			@Override
		    public IRI getDocumentIRI(IRI ontologyIRI) {
		    	IRI docIRI = getHGDBIRI(ontologyIRI);  
	    		System.out.println("HGDBIRIMapper: " + ontologyIRI + " -> " + docIRI);
		    	if (repository.existsOntologyByDocumentIRI(docIRI)) {
		    		return docIRI;
		    	} else {
		    		return null;
		    	}
		    }
		});
		new OntologyLoader(manager); // add file->IRI mappings to manager		
	}
	
	public static void importOntology(File ontologyFile) {
		// 1) Load in Memory
		OWLOntology loadedOntology = null;
		try {
			System.out.print("Loading Ontology from file: " + ontologyFile.getAbsolutePath() + " ...");
			 loadedOntology =  manager.loadOntologyFromOntologyDocument(ontologyFile);
			System.out.println("Done.");
		} catch (OWLOntologyCreationException ocex) {
			System.err.println("Error loading ontology from: " + ontologyFile.getAbsolutePath());
			ocex.printStackTrace();
			System.exit(-1);
		}
		// 2) Change Format, create repo url with hgdb://
		//Define a repository document IRI for our ontology
		IRI targetIRI = getHGDBIRI(loadedOntology.getOntologyID().getOntologyIRI());
		HGDBOntologyOutputTarget target = new HGDBOntologyOutputTarget(targetIRI);
		//Manager will find our HGDBStorer based on the format and 
		// import the ontology into our repo. Same for other Formats.
		try {
			System.out.print("Importing: " + loadedOntology.getOntologyID().getOntologyIRI() + " -> " + target + " ...");
			manager.saveOntology(loadedOntology , new HGDBOntologyFormat(), targetIRI);
			System.out.print("Done.");
		} catch (OWLOntologyStorageException e) {
			System.err.println("Error saving ontology: " + ontologyFile.getAbsolutePath());
			e.printStackTrace();
		}
	}

	public static void initDatabase()
	{
		try
		{
			OWL.manager();
			OwlRepo repo = Refs.owlRepo.resolve();
			repo.ensurePeerStarted();
			BrowseRepositoryActivity browseAc = new BrowseRepositoryActivity(repo.repo().getPeer(), 
					repo.getDefaultPeer()); 				
			repo.repo().getPeer().getActivityManager().initiateActivity(browseAc).get();
			for (BrowseRepositoryActivity.BrowseEntry e : browseAc.getRepositoryBrowseEntries())
			{
				System.out.println(e.getOwlOntologyIRI() + " --> " + e.getUuid());
				if (e.getOwlOntologyIRI().equals(Refs.topOntologyIRI.resolve()) ||
					e.getOwlOntologyIRI().equals(Refs.defaultOntologyIRI.resolve()))
				{
					PullActivity pull = new PullActivity(repo.repo().getPeer(), 
													     e.getUuid(),
													     repo.getDefaultPeer());
					ActivityResult r = repo.repo().getPeer().getActivityManager().initiateActivity(pull).get();
					if (r.getException() != null)
						r.getException().printStackTrace();
				}
			}
		}
		catch (Exception t)
		{
			t.printStackTrace();
			throw new RuntimeException(t);
		}		
	}
	
	public static void populateDatabase()
	{
		importOntology(new File("c:/work/cirmservices/src/ontology/County_Working.owl"));
		importOntology(new File("c:/work/cirmservices/src/ontology/csr.owl"));					
	}
	
	public static void testWithFiles() throws Exception
	{
		String dir = "c:/work/cirmservices/src/ontology";
		File f1 = new File(dir + "/County_Working.owl"), f2 = new File(dir + "/csr.owl"); 	
		OWLOntologyManager manager = SynchronizedOWLManager.createOWLOntologyManager();
		OWLOntology o1 = manager.loadOntologyFromOntologyDocument(f1),
				o2 = manager.loadOntologyFromOntologyDocument(f2);
		
		OWLReasonerFactory reasonerFactory = PelletReasonerFactory.getInstance();
		OWLReasoner reasoner = reasonerFactory.createReasoner(o1);
	}
	
	public static void main(String [] args)
	{
		try
		{
			if( (args.length > 0) )
				StartUp.config = Json.read(GenUtils.readTextFile(new File(args[0])));
			initDatabase(); 
			// populateDatabase();
			//testWithFiles();
			Refs.owlRepo.resolve().repo().getPeer().stop();
		}
		catch (Throwable t)
		{
			t.printStackTrace(System.err);
		}
	}
}

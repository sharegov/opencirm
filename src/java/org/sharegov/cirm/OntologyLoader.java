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
package org.sharegov.cirm;

import java.io.File;
import java.io.FilenameFilter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.hypergraphdb.app.owl.HGDBOntologyManagerImpl;
import org.hypergraphdb.app.owl.HGDBOntologyRepository;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.sharegov.cirm.owl.CachedReasoner;
import org.sharegov.cirm.owl.SynchronizedOWLOntologyManager;
import org.sharegov.cirm.owl.SynchronizedReasoner;

import com.clarkparsia.pellet.owlapiv3.PelletReasonerFactory;

public class OntologyLoader
{
	public static boolean THREAD_SAFE_REASONERS = true;
	public static boolean CACHED_REASONERS = true;
	
	private OWLOntologyManager manager;
	private Map<IRI, File> locations = new ConcurrentHashMap<IRI, File>();
	private Map<IRI, OWLOntology> loaded = new ConcurrentHashMap<IRI, OWLOntology>(); 
	private Map<IRI, Long> lastLoaded = new ConcurrentHashMap<IRI, Long>();
	private OWLReasonerFactory reasonerFactory =  // new Reasoner.ReasonerFactory();
			 PelletReasonerFactory.getInstance(); 	
	private ConcurrentHashMap<IRI, OWLReasoner> reasoners = new ConcurrentHashMap<IRI, OWLReasoner>();	
	private OWLOntologyIRIMapper iriMapper = null;
	
	static IRI getHGDBIRI(IRI ontologyIRI)
	{
    	String iriNoScheme = ontologyIRI.toString();
    	String scheme = ontologyIRI.getScheme();
    	iriNoScheme = iriNoScheme.substring(scheme.length());
    	IRI docIRI = IRI.create("hgdb" + iriNoScheme);  
    	return docIRI;
	}
	
	public OntologyLoader(OWLOntologyManager manager)
	{
		// TODO: all this should be in customIRIMappings file only..
		locations.put(IRI.create("http://www.miamidade.gov/cirm/legacy"), 
				  new File(StartUp.config.at("workingDir").asString() + "/src/ontology/legacy.owl"));		
		locations.put(IRI.create("http://www.miamidade.gov/cirm/legacy/exported"),
//				new File("c:/temp/csrnew.owl"));
				  new File(StartUp.config.at("workingDir").asString() + "/src/ontology/csr.owl"));		
		locations.put(IRI.create("http://www.miamidade.gov/ontology"), 
			      new File(StartUp.config.at("workingDir").asString() + "/src/ontology/County_Working.owl"));
		locations.put(IRI.create("http://www.miamidade.gov/ontology/pkbi"), 
			      new File(StartUp.config.at("workingDir").asString() + "/src/County_Working_PKBI.owl"));			
		locations.put(IRI.create("http://www.miamidade.gov/users/enet"), 
				  new File(StartUp.config.at("workingDir").asString() + "/src/ontology/enet.owl"));
		
		this.manager = manager;
		boolean hgdbManager = false;
		if (manager instanceof SynchronizedOWLOntologyManager) {
			hgdbManager = ((SynchronizedOWLOntologyManager)manager).getWrappedOWLOntologyManager() instanceof HGDBOntologyManagerImpl;
		} else {
			System.err.println("OWLOntologyManager in use is NOT THREAD SAFE :" + manager);
			hgdbManager = manager instanceof HGDBOntologyManagerImpl;
		}
		if (hgdbManager)
			this.iriMapper = new OWLOntologyIRIMapper() {			
			@Override
		    public IRI getDocumentIRI(IRI ontologyIRI) {
		    	IRI docIRI = getHGDBIRI(ontologyIRI);  
	    		//System.out.println("HGDBIRIMapper: " + ontologyIRI + " -> " + docIRI);
		    	if (HGDBOntologyRepository.getInstance().existsOntologyByDocumentIRI(docIRI)) {
		    		return docIRI;
		    	} else {
					File f = locations.get(ontologyIRI);				
					return f != null ? IRI.create(f) : null;
		    	}
		    }
			};
		else
		{			
			this.iriMapper = new OWLOntologyIRIMapper() {
	
				public IRI getDocumentIRI(IRI paramIRI)
				{
					File f = locations.get(paramIRI);				
					return f != null ? IRI.create(f) : null;
				}			
			};
		}
		manager.addIRIMapper(iriMapper);
	}
	
	public File findFile(String iriString)
	{
		File f = locations.get(IRI.create(iriString));
		if (f != null)
			return f;
		String prefix = Refs.nameBase.resolve() + "/swrl/";
		if (iriString.startsWith(prefix))
			return new File(new File(StartUp.config.at("workingDir").asString() + "/src/ontology"), 
					iriString.substring(prefix.length()) + ".swrl");
		f = new File(iriString);
		return f.exists() ? f : null;
	}
	
	
	public synchronized void unload(IRI iri)
	{
		OWLOntology o = loaded.get(iri);
		if (o != null) 
			manager.removeOntology(o);	
	}
	
	public synchronized OWLOntology get(String iriString)
	{ 
		IRI iri = IRI.create(iriString);
		if (StartUp.config.has("metaDatabaseLocation"))
		{
			try
			{
				OWLOntology o = manager.getOntology(iri);
				return o == null ? manager.loadOntology(iri) : o;
			}
			catch (Exception ex) 
			{
				throw new RuntimeException(ex);
			}
		}
		OWLOntology o = loaded.get(iri);
		if (o == null) {
			try
			{
				o = manager.getOntology(iri);
				if (o == null)
				{
					o = manager.loadOntology(iri);
					
					lastLoaded.put(iri, System.currentTimeMillis());
				}
			}
			catch (OWLOntologyCreationException e)
			{
				throw new RuntimeException(e);
			}
		}
		Long o_at = lastLoaded.get(iri);
		// TODO: this expensive and called every time we call OWL.ontology()!
		File f = findFile(iriString);
		if (o == null || f != null && (o_at == null || f.lastModified() > o_at))
		{
			if (o != null)
			{
				manager.removeOntology(o);
				reasoners.remove(iri);
			}
			try
			{
				if (f != null && f.exists())
					o = manager.loadOntologyFromOntologyDocument(f);
				else
					o = manager.loadOntology(iri);
			}
			catch (OWLOntologyCreationException ex) 
			{ 
				ex.printStackTrace(System.err); 
				throw new RuntimeException(ex); 
			}
			lastLoaded.put(iri, System.currentTimeMillis());
			loaded.put(iri, o);
			return o;
		}
		else 
			return o;
	}

	/**
	 * synchronize to avoid concurrent reasoner initialization
	 * @param ontology
	 * @return
	 */
	public synchronized OWLReasoner getReasoner(OWLOntology ontology)
	{
		IRI iri = ontology.getOntologyID().getOntologyIRI();
		if (OWL.isBusinessObjectOntology(ontology)) // for temporary "object" ontologies...
		{
			OWLReasoner reasoner = reasonerFactory.createReasoner(ontology);
			if (THREAD_SAFE_REASONERS)
			{
				reasoner = SynchronizedReasoner.synchronizedReasoner(reasoner);
				if (CACHED_REASONERS) 
				{
					reasoner = CachedReasoner.cachedReasoner((SynchronizedReasoner)reasoner);
				}
			}
			return reasoner;
		}
		OWLReasoner reasoner = reasoners.get(iri);
		if (reasoner == null)
		{
			reasoner =  reasonerFactory.createReasoner(ontology);
			if (THREAD_SAFE_REASONERS)
			{
				reasoner = SynchronizedReasoner.synchronizedReasoner(reasoner);
				if (CACHED_REASONERS) 
				{
					reasoner = CachedReasoner.cachedReasoner((SynchronizedReasoner)reasoner);
				}
			}
				
			OWLReasoner existing = reasoners.putIfAbsent(iri, reasoner);
			if (existing != null)
				reasoner = existing;
		}
		return reasoner;
	}
	
	public Set<IRI> allSWRL()
	{
		
		Set<IRI> result = new HashSet<IRI>();
		String dir = StartUp.config.at("workingDir").asString() + "/src/ontology";
		String[] files = new File(dir).list(new FilenameFilter()
		{
			@Override
			public boolean accept(File dir, String name)
			{
				if(name.endsWith(".swrl"))
					return true;
				else
					return false;
			}
		});
		for(String file: files)
		{
			IRI iri = IRI.create(Refs.nameBase.resolve() + "/swrl/" + 
							file.substring(0, file.length()-5));
			result.add(iri);
		}
		return result;
	}
	
	public int getNrOfCachedReasoners()
	{
		return reasoners.size();
	}

}

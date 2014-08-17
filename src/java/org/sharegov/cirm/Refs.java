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

import java.util.logging.Logger;

import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.sharegov.cirm.event.ClientPushQueue;
import org.sharegov.cirm.gis.GisInterface;
import org.sharegov.cirm.owl.Model;
import org.sharegov.cirm.owl.OWLProtectedClassCache;
import org.sharegov.cirm.owl.OWLSerialEntityCache;
import org.sharegov.cirm.owl.OwlRepo;
import org.sharegov.cirm.owl.SynchronizedOWLManager;
import org.sharegov.cirm.rdb.OntologyTransformer;
import org.sharegov.cirm.rdb.RelationalOWLPersister;
import org.sharegov.cirm.rdb.RelationalStore;
import org.sharegov.cirm.rdb.RelationalStoreExt;
import org.sharegov.cirm.search.SearchEngine;
import org.sharegov.cirm.stats.CirmStatistics;
import org.sharegov.cirm.stats.CirmStatisticsFactory;
import org.sharegov.cirm.utils.CIRMIDFactory;
import org.sharegov.cirm.utils.ClassRef;
import org.sharegov.cirm.utils.ConfigRef;
import org.sharegov.cirm.utils.DescribedRef;
import org.sharegov.cirm.utils.DirectRef;
import org.sharegov.cirm.utils.ObjectRef;
import org.sharegov.cirm.utils.Ref;
import org.sharegov.cirm.utils.RequestScopeRef;
import org.sharegov.cirm.utils.SingletonRef;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

public class Refs
{
	public static final String hasContents = Model.upper("hasContents").toString();
	public static final String hasNext = Model.upper("hasNext").toString();
	public static final String EmptyList = Model.upper("EmptyList").toString();
	public static final String isJsonMapper = Model.upper("isJsonMapper").toString();
	public static final String hasJsonMapper = Model.upper("hasJsonMapper").toString();
	public static final String hasParentClass = Model.upper("hasParentClass").toString();
	public static final String hasPropertyResolver = Model.upper("hasPropertyResolver").toString();
	public static final String OWLClass = Model.upper("OWLClass").toString();
	public static final String OWLProperty = Model.upper("OWLProperty").toString();
	public static final String OWLDataProperty = Model.upper("OWLDataProperty").toString();
	public static final String OWLObjectProperty = Model.upper("OWLObjectProperty").toString();
	public static final String hasQueryExpression = Model.upper("hasQueryExpression").toString();
	
	public static final Ref<String> nameBase = new Ref<String>() {
		public String resolve() { return StartUp.config.at("nameBase").asString(); }
	};
	
	public static final Ref<OWLOntology> topOntology = new Ref<OWLOntology>() {
		public OWLOntology resolve()
		{
			return OWL.ontology(topOntologyIRI.resolve());
		}
	};

	public static final Ref<String> topOntologyIRI = new Ref<String>() {
		public String resolve()
		{
			return "http://www.miamidade.gov/ontology";
		}
	};
	
	public static final Ref<String> defaultOntologyIRI = new Ref<String>() {
		public String resolve()
		{
			return StartUp.config.at("defaultOntologyIRI").asString();
		}
	};
	
	public static final Ref<OWLOntology> defaultOntology = new Ref<OWLOntology>() {
		public OWLOntology resolve()
		{
			return OWL.ontology(defaultOntologyIRI.resolve());
		}
	};
	
	public static final Ref<Logger> logger =  
			new SingletonRef<Logger>(Logger.getLogger("org.sharegov.cirm"));
	
	public static final Ref<CIRMIDFactory> idFactory =  
		new SingletonRef<CIRMIDFactory>(
				new ObjectRef<CIRMIDFactory>(new ClassRef<CIRMIDFactory>(new ConfigRef<OWLNamedIndividual>("CIRMIDFactory"))));
	public static final Ref<ConfigSet> configSet = new Ref<ConfigSet>() {
		public ConfigSet resolve() { return ConfigSet.getInstance(); }
	};
	public static final Ref<ClientPushQueue> clientPushQueue =
			new SingletonRef<ClientPushQueue>(new ObjectRef<ClientPushQueue>(new DirectRef<Class<ClientPushQueue>>(ClientPushQueue.class)));

	/**
	 * All methods will be transparently transactional.
	 */
	public static final Ref<RelationalStore> defaultRelationalStore = new Ref<RelationalStore>() {
		public RelationalStore resolve()
		{
			OWLNamedObject x = configSet.resolve().get("OperationsDatabaseConfig");
			return RelationalOWLPersister.getInstance(x.getIRI()).getStore();
		}		
	};

	/**
	 * try to use defaultRelationaslStore whenever possible.
	 * Use extended store methods always inside a CirmTransaction.
	 */
	public static final Ref<RelationalStoreExt> defaultRelationalStoreExt = new Ref<RelationalStoreExt>() {
		public RelationalStoreExt resolve()
		{
			OWLNamedObject x = configSet.resolve().get("OperationsDatabaseConfig");
			return RelationalOWLPersister.getInstance(x.getIRI()).getStoreExt();
		}		
	};

	public static final Ref<RelationalOWLPersister> defaultPersister = new Ref<RelationalOWLPersister>() {
	  public RelationalOWLPersister resolve()
	  {
	      OWLNamedObject x = configSet.resolve().get("OperationsDatabaseConfig");
	      return RelationalOWLPersister.getInstance(x.getIRI());
	  }
	};

	/**
	 * The temporary ontology manager is used to load and unload business object ontologies that should
	 * remain separate from the main/big metadata.
	 */
	public static final Ref<OWLOntologyManager> tempOntoManager = new RequestScopeRef<OWLOntologyManager>(
			// We provide a factory to be used on each request
			new Ref<OWLOntologyManager>() {
				public OWLOntologyManager resolve() {
					return SynchronizedOWLManager.createOWLOntologyManager();
				}});
	public static final Ref<OWLSerialEntityCache> owlJsonCache = 
			new SingletonRef<OWLSerialEntityCache>(new OWLSerialEntityCache());
	
	public static final Ref<OwlRepo> owlRepo = new SingletonRef<OwlRepo>(
			StartUp.config.has("metaDatabaseLocation") ?
			OwlRepo.getInstance() : null);
	
	public static final Ref<OntologyTransformer> ontologyTransformer =
			new SingletonRef<OntologyTransformer>(new ObjectRef<OntologyTransformer>(
			        new DirectRef<Class<OntologyTransformer>>(OntologyTransformer.class)));

	public static final Ref<OWLProtectedClassCache> protectedClassCache = new Ref<OWLProtectedClassCache>()
	{
		public OWLProtectedClassCache resolve()
		{
			return OWLProtectedClassCache.getInstance();
		}
	};
	
	public static final Ref<GisInterface> gisClient = 
	        new SingletonRef<GisInterface>(new DescribedRef<GisInterface>(GisInterface.class.getName()));

	public static final Ref<SearchEngine> searchEgnine = 
			new SingletonRef<SearchEngine>(new DescribedRef<SearchEngine>(SearchEngine.class.getName()));
	
	public static final Ref<String> boIriPrefix = new Ref<String>() {
		public String resolve() 
		{
			return ((OWLLiteral)configSet.resolve().get("businessObjectIRIPrefix")).getLiteral();
		}
	}; 
	

	
	public static final Ref<CirmStatistics> stats = new SingletonRef<CirmStatistics>(CirmStatisticsFactory.createStats());

}
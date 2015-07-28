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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import mjson.Json;

import org.hypergraphdb.app.owl.HGDBOWLManager;
import org.hypergraphdb.app.owl.versioning.distributed.VDHGDBOntologyRepository;
import org.hypergraphdb.app.owl.versioning.distributed.activity.BrowseRepositoryActivity;
import org.hypergraphdb.app.owl.versioning.distributed.activity.PullActivity;
import org.hypergraphdb.app.owl.versioning.distributed.activity.BrowseRepositoryActivity.BrowseEntry;
import org.hypergraphdb.peer.HGPeerIdentity;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.sharegov.cirm.StartUp;

/**
 * Encapsulate the OWL repository and networking services with some
 * convenience operations needed by the application;
 * 
 * @author boris
 *
 */
public class OwlRepo
{
	private static final OwlRepo instance = new OwlRepo();
	
	/**
	 * Prefer Refs.owlRepo.resolve() as the correct method to get a reference to the repo.
	 */
	public static OwlRepo getInstance() { return instance; }
	
	VDHGDBOntologyRepository repo; //= VDHGDBOntologyRepository.getInstance();
	HGPeerIdentity bff = null;
	
	private void findBFF()
	{
		String bffname = StartUp.config.at("network").at("bff").asString();
		for (HGPeerIdentity id : repo.getPeer().getConnectedPeers())
		{
			String name = (String)repo.getPeer().getNetworkTarget(id);
			if (name.startsWith(bffname))
			{
				bff = id;
				return;
			}
		}
	}
	
	private void ensureRepository() {
		if (repo == null) {
			repo = VDHGDBOntologyRepository.getInstance();
			if (repo.getOntologyManager() == null) {
				repo.setOntologyManager(HGDBOWLManager.createOWLOntologyManager());
			}
		}
	}
	
	public HGPeerIdentity getDefaultPeer()
	{
		return bff;
	}
	
	public VDHGDBOntologyRepository repo() 
	{ 
		ensureRepository();
		return repo; 
	}
	
	public boolean ensurePeerStarted()
	{
		ensureRepository();
		if (repo.getPeer() != null && repo.getPeer().getPeerInterface().isConnected() && bff != null)
			return true;
		synchronized (repo)
		{
			if (repo.getPeer() == null || !repo.getPeer().getPeerInterface().isConnected())
			{
				Json config = StartUp.config.at("network");
				if (config == null)
					throw new RuntimeException("No network configured.");
				repo.startNetworking(config.at("user").asString(), 
								   	 config.at("password").asString(), 
								   	 config.at("serverUrl").asString());
			}
			if (bff == null)
			{
				// Now, we need to wait for peers to connect
				for (int i = 0; i < 5; i++)
				{
					findBFF();
					try { Thread.sleep(1000); }
					catch (Throwable t) { }
				}
			}
			if (bff == null)
			{
				repo.getPeer().stop();
				throw new RuntimeException("Best friend forever " + 
						StartUp.config.at("network").at("bff").asString() +
						" is offline, try again later.");
			}
			return true;
		}
	}	
	
	/**
	 * Creates a local repository at the given location and downloads (pulls) ontologies from the default peer.
	 * @param dbLocation
	 * @param ontologyIRIs
	 */
	public void createRepositoryFromDefaultPeer(String dbLocation, Set<IRI> ontologyIRIs) 
	{
		if (repo != null) throw new IllegalStateException("A repository was already created. This method must be called before.");
		File f = new File(dbLocation);
		if (!f.exists())
			f.mkdir();
		VDHGDBOntologyRepository.setHypergraphDBLocation(dbLocation);
		ensureRepository();
		pullNewFromDefaultPeer(ontologyIRIs);
	}

	/**
	 * Pulls (downloads) all versioned ontologies specified by the ontologyIRIs and imports them into the local repository.
	 * None of the ontologies may exist locally and all ontologies must exist remotely before calling this method.
	 * Transactional. 
	 *  
	 * @param ontologyIRIs
	 * @throws IllegalStateException if any ontology already exists locally or does not exist remotely.
	 */
	public void pullNewFromDefaultPeer(final Set<IRI> ontologyIRIs) {
		repo.getHyperGraph().getTransactionManager().ensureTransaction(new Callable<Object>()
		{
			public Object call() {
				for(IRI ontoIri : ontologyIRIs) {
					OWLOntologyID ontoId = new OWLOntologyID(ontoIri);
					if (repo.existsOntology(ontoId)) {
						throw new IllegalStateException("Cannot create new ontology in local repository with IRI " + ontoIri +" because it exists locally. \r\n ");
					}
				}
				ensurePeerStarted();
				System.out.println("My best friend forever is " + getDefaultPeer());
				BrowseRepositoryActivity browseAct = repo.browseRemote(bff);
				try
				{
					browseAct.getFuture().get(60, TimeUnit.SECONDS);
					List<BrowseEntry> remoteEntries = findRemoteEntriesFor(ontologyIRIs, browseAct.getRepositoryBrowseEntries());
					for(BrowseEntry remoteEntry : remoteEntries) {
						if (repo.getHyperGraph().get(remoteEntry.getUuid()) != null) {
							throw new IllegalStateException("Cannot create ontology " + remoteEntry.getOwlOntologyIRI() + "in local repository with UUID " + remoteEntry.getUuid()+ " because it (the uuid) exists locally. \r\n ");
						}
					}
					for(BrowseEntry remoteEntry : remoteEntries) {
						System.out.println("Pulling new ontology from remote: " + remoteEntry.getOwlOntologyIRI() + " (" + remoteEntry.getUuid() + ")" + " Mode: " + remoteEntry.getDistributionMode());
						PullActivity a = repo.pullNew(remoteEntry.getUuid(), bff);
						a.getFuture().get(160, TimeUnit.SECONDS);
						System.out.println("Pulling new completed: " + a.getCompletedMessage());
					}					
				}
				catch (Exception e)
				{
					throw new RuntimeException(e);
				}
				return null;
			}
		});
	}

	public void pushWorkingToDefaultPEer(final IRI ontologyIRI)
	{
		// TODO..
	}
	
	/**
	 * Gets a list of BrowseEntries for all iris by searching in remoteEntries.
	 * @param iris
	 * @param remoteEntries
	 * @return
	 * @throws IllegalStateException if at least one IRI was not found. 
	 */
	private List<BrowseEntry>  findRemoteEntriesFor(Set<IRI> ontologyIRIs, List<BrowseEntry> remoteEntries) {
		List<BrowseEntry> l = new ArrayList<BrowseEntry>();
		BrowseEntry found = null;
		for (IRI iri : ontologyIRIs) {
			for (BrowseEntry remote : remoteEntries) {
				if (remote.getOwlOntologyIRI().equals(iri.toString())) {
					found = remote;
					break;
				}
			}
			if (found != null) {
				l.add(found);
				found = null;
			} else {
				throw new IllegalStateException("OntologyIRI not found in remote entries: " + iri);
			}
		}
		return l;
	}
	
}
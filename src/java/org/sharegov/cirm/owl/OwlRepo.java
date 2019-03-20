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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import mjson.Json;

import org.hypergraphdb.app.owl.HGDBOWLManager;
import org.hypergraphdb.app.owl.versioning.distributed.VDHGDBOntologyRepository;
import org.hypergraphdb.app.owl.versioning.distributed.activity.BrowseRepositoryActivity;
import org.hypergraphdb.app.owl.versioning.distributed.activity.PullActivity;
import org.hypergraphdb.app.owl.versioning.distributed.activity.BrowseRepositoryActivity.BrowseEntry;
import org.hypergraphdb.peer.HGPeerIdentity;
import org.hypergraphdb.peer.workflow.ActivityResult;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

/**
 * Encapsulate the OWL repository and networking services with some
 * convenience operations needed by the application;
 * 
 * @author boris, Thomas Hilpold
 *
 */
public class OwlRepo
{
	private static final OwlRepo instance = new OwlRepo();
	
	/**
	 * The timeout to use for activities that interact with the ontology server.
	 */
	public static final int TIMEOUT_BROWSE_SECS = 180; //3 min timeout (prev was 1 min)
	public static final int TIMEOUT_PULL_SECS = 600; //10 min timeout (prev was 3 min)

	public static final int FIND_ONTO_SERVER_MAX_ATTEMPTS = 10; //10 attampts with 1 sec pause

	/**
	 * Prefer Refs.owlRepo.resolve() as the correct method to get a reference to the repo.
	 */
	public static OwlRepo getInstance() { return instance; }
	
	private volatile VDHGDBOntologyRepository repo;
	private volatile HGPeerIdentity ontoServer = null;
	
	/**
	 * Attempts to find the ontoServer as connected XMPP peer. 
	 * @return HGPeerIdentity if ontoServer found, or null if not found yet.
	 */
	private HGPeerIdentity findOntoServer()
	{
		String ontoServerName = StartUp.getConfig().at("network").at("ontoServer").asString();
		for (HGPeerIdentity id : repo.getPeer().getConnectedPeers())
		{
			String name = (String)repo.getPeer().getNetworkTarget(id);
			if (name.startsWith(ontoServerName))
			{
				return id;
			}
		}
		return null; //ontoServer not found
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
		return ontoServer;
	}
	
	public VDHGDBOntologyRepository repo() 
	{ 
		ensureRepository();
		return repo; 
	}
	
	/**
	 * Starts XMPP networking if needed by connecting this instance to the network and also attempting to find the ontology server.
	 * The XMPP chat server must be available, and the configured ontology server must be on the roster of this instance's XMPP user for this method to return. 
	 * 
	 * @return always true, fails with various RuntimeExceptions if any problem is detected.
	 */
	public boolean ensurePeerStarted()
	{
		ensureRepository();
		if (repo.getPeer() != null && repo.getPeer().getPeerInterface().isConnected() && ontoServer != null)
			return true;
		synchronized (repo)
		{
			if (repo.getPeer() == null || !repo.getPeer().getPeerInterface().isConnected())
			{
				Json config = StartUp.getConfig().at("network");
				if (config == null)
					throw new RuntimeException("No network configured.");
				repo.startNetworking(config.at("user").asString(), 
								   	 config.at("password").asString(), 
								   	 config.at("serverUrl").asString());
			}
			if (ontoServer == null)
			{
				int attempts = 0;
				do 
				{
					attempts ++;
					ontoServer = findOntoServer();
					try { Thread.sleep(1000); }
					catch (Throwable t) { }
				} while (ontoServer == null && attempts < FIND_ONTO_SERVER_MAX_ATTEMPTS);
			}
			if (ontoServer == null)
			{
				repo.getPeer().stop();
				throw new RuntimeException("Ontology Server " + 
						StartUp.getConfig().at("network").at("ontoServer").asString() +
						" is offline, please ensure server is started and try again.");
			} 
			else 
			{
				ThreadLocalStopwatch.now("Found ontoServer for OwlRepo: "  + ontoServer.getHostname() + " (" + ontoServer.getIpAddress() + ")");
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
	 * @throws RuntimeException on Timeouts, or if any processing error occurred.
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
				ThreadLocalStopwatch.now("Connected to Ontology Server " + getDefaultPeer());
				BrowseRepositoryActivity browseAct = repo.browseRemote(ontoServer);
				ActivityResult actResult;
				try
				{
					Future<ActivityResult> browseActFuture = browseAct.getFuture();  
					actResult = browseActFuture.get(TIMEOUT_BROWSE_SECS, TimeUnit.SECONDS);
					if (!browseActFuture.isDone()) {
						throw new TimeoutException("browsing for ontologies at the ontology server timed out after " + TIMEOUT_BROWSE_SECS + " secs");
					} else if (actResult.getException() != null) {
						throw new RuntimeException("browsing for ontologies at the ontology server failed", actResult.getException());
					}
					List<BrowseEntry> remoteEntries = findRemoteEntriesFor(ontologyIRIs, browseAct.getRepositoryBrowseEntries());
					for(BrowseEntry remoteEntry : remoteEntries) {
						if (repo.getHyperGraph().get(remoteEntry.getUuid()) != null) {
							throw new IllegalStateException("Cannot create ontology " + remoteEntry.getOwlOntologyIRI() + "in local repository with UUID " + remoteEntry.getUuid()+ " because it (the uuid) exists locally. \r\n ");
						}
					}
					for(BrowseEntry remoteEntry : remoteEntries) {
						ThreadLocalStopwatch.now("Pulling new ontology from remote: " + remoteEntry.getOwlOntologyIRI() + " (" + remoteEntry.getUuid() + ")" + " Mode: " + remoteEntry.getDistributionMode());
						PullActivity pullNewAct = repo.pullNew(remoteEntry.getUuid(), ontoServer);
						Future<ActivityResult> pullNewActFuture = pullNewAct.getFuture();  
						actResult = pullNewActFuture.get(TIMEOUT_PULL_SECS, TimeUnit.SECONDS);
						if (!pullNewActFuture.isDone()) {
							throw new TimeoutException("PullActivity for new ontology " + remoteEntry.getOwlOntologyIRI() + " timed out after " + TIMEOUT_PULL_SECS + " secs");
						} else if (actResult.getException() != null) {
							throw new RuntimeException("PullActivity for new ontology  " + remoteEntry.getOwlOntologyIRI() + " failed", actResult.getException());
						}
						ThreadLocalStopwatch.now("Pulling new completed: " + pullNewAct.getCompletedMessage());
					}					
				}
				catch (Exception e)
				{
					throw new RuntimeException("Exception in pullNewFromDefaultPeer. Please kill the process manually.", e);
				}
				return null;
			}
		});
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

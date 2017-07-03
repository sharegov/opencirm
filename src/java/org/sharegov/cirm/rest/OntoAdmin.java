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
package org.sharegov.cirm.rest;

import static mjson.Json.array;
import static mjson.Json.object;
import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.utils.GenUtils.ko;
import static org.sharegov.cirm.utils.GenUtils.ok;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.hypergraphdb.app.owl.HGDBOntology;
import org.hypergraphdb.app.owl.versioning.Revision;
import org.hypergraphdb.app.owl.versioning.RevisionID;
import org.hypergraphdb.app.owl.versioning.VersionedOntology;
import org.hypergraphdb.app.owl.versioning.VersionedOntologyComparator.RevisionCompareOutcome;
import org.hypergraphdb.app.owl.versioning.VersionedOntologyComparator.RevisionComparisonResult;
import org.hypergraphdb.app.owl.versioning.VersionedOntologyComparator.VersionedOntologyComparisonResult;
import org.hypergraphdb.app.owl.versioning.distributed.ClientCentralizedOntology;
import org.hypergraphdb.app.owl.versioning.distributed.DistributedOntology;
import org.hypergraphdb.app.owl.versioning.distributed.VDHGDBOntologyRepository;
import org.hypergraphdb.app.owl.versioning.distributed.activity.PullActivity;
import org.hypergraphdb.app.owl.versioning.distributed.activity.PushActivity;
import org.hypergraphdb.peer.HGPeerIdentity;
import org.hypergraphdb.peer.workflow.ActivityResult;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.OntologyChangesRepo;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.event.EventDispatcher;
import org.sharegov.cirm.owl.CachedReasoner;
import org.sharegov.cirm.owl.SynchronizedOWLOntologyManager;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.OntoChangesReference;
import org.sharegov.cirm.utils.OntologyCommit;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import mjson.Json;
import uk.ac.manchester.cs.owl.owlapi.OWLOntologyManagerImpl;

//import com.clarkparsia.pellet.owlapiv3.PelletReasoner;

@Path("ontadmin")
@Produces("application/json")
public class OntoAdmin extends RestService
{
	public enum REPOACTION {PULL, REVERT, NOTHING};
	
	public final String CACHED_REASONER_RESDIR = "/src/resources/cachedReasoner/";
	public final String CACHED_REASONER_POPULATE_GET_INSTANCES_CACHE_FILE = CACHED_REASONER_RESDIR + "populateGetInstancesCache.json";
	public static int ACTIVITY_TIMEOUT_SECS = 180;
	
	public final String[] SERIAL_PRECACHE_IND_QUERIES = new String[] {
			"legacy:Status",
			"legacy:Priority",
			"legacy:IntakeMethod",
			"legacy:IntakeMethodList",
			"State__U.S._",
			"Miami_Dade_City or County",
			"Direction",
			"Street_Type",
			"GeoLayerAttribute",
			"legacy:ServiceCase"
	};
	
	protected VDHGDBOntologyRepository repo()
	{
		return Refs.owlRepo.resolve().repo();
	}
	
	private void notifyOntoChange(String ontologyIri)
	{
		Set<OWLNamedIndividual> S = 
			OWL.reasoner().getInstances(OWL.hasData("hasOntologyIRI", OWL.literal(ontologyIri)), false).getFlattened();
		if (!S.isEmpty())
			EventDispatcher.get().dispatch(S.iterator().next(), individual("BO_Update"));		
	}
	
	@GET
	@Path("/list")
	public Json listOntologies()
	{
		VDHGDBOntologyRepository repo = repo();
		Json A = array();
		for (HGDBOntology O : repo.getOntologies())
		{
			Json x = object()
				.set("iri", O.getOntologyID().getOntologyIRI().toString())
				.set("versionIRI", O.getOntologyID().getVersionIRI() == null ?
						null : O.getOntologyID().getVersionIRI().toString())
				.set("documentIRI", O.getDocumentIRI() == null ?
						null : O.getDocumentIRI().toString())
				.set("imports", array());
			for (OWLOntology io : O.getImports())
				x.at("imports").add(io.getOntologyID().getOntologyIRI().toString());
			A.add(x);
		}
		return A;
	}
	
	@GET
	@Path("/listAll")
	public Json listAllOntologies()
	{
		Json A = array();
		for (OWLOntology O : OWL.manager().getOntologies())
		{
			Json x = object()
				.set("iri", O.getOntologyID().getOntologyIRI().toString())
				.set("versionIRI", O.getOntologyID().getVersionIRI() == null ?
						null : O.getOntologyID().getVersionIRI().toString())
				.set("documentIRI", OWL.manager().getOntologyDocumentIRI(O).toString())
				.set("imports", array());
			for (OWLOntology io : O.getImports())
				x.at("imports").add(io.getOntologyID().getOntologyIRI().toString());
			A.add(x);
		}
		return A;
	}

	@GET
	@Path("/currentVersion/{iri}")
	public Json getCurrentVersion(@PathParam("iri") String iri)
	{
		VDHGDBOntologyRepository repo = repo();
		try
		{		
			OWLOntology O = OWL.manager().getOntology(IRI.create(iri)); 
			if (O == null)
				return ko("Ontology not found: " + iri);
			//repo.printAllOntologies();
			VersionedOntology vo = repo.getVersionControlledOntology(O);
			return ok().set("version", vo.getHeadRevision().getRevision())
					   .set("comment", vo.getHeadRevision().getRevisionComment())
					   .set("timestamp", vo.getHeadRevision().getTimeStamp().getTime())
					   .set("user", vo.getHeadRevision().getUser());
		}
		catch (Throwable t)
		{
			t.printStackTrace(System.err);
			return ko(t.toString());
		}
	}

	@POST
	@Path("/revert/{iri}")
	public Json revert(@PathParam("iri") String iri)
	{
		VDHGDBOntologyRepository repo = repo();
		try
		{
			Refs.owlRepo.resolve().ensurePeerStarted();
			OWLOntology O = OWL.manager().getOntology(IRI.create(iri)); 
			if (O == null)
				return ko("Ontology not found: " + iri);
			VersionedOntology vo = repo.getVersionControlledOntology(O);
			if (vo.getNrOfRevisions() <= 1)
				return ko("Already at first revision.");
			vo.revertHeadTo(vo.getRevisions().get(vo.getNrOfRevisions() - 2));
			OWL.reasoner().flush();
			notifyOntoChange(iri);
			return ok();
		}
		catch (Throwable t)
		{
			t.printStackTrace(System.err);
			return ko(t.toString());
		}
	}

	@POST
	@Path("/synchTo/{iri}/{version}")
	public Json pullTo(@PathParam("iri") String iri, @PathParam("version") String version)
	{
		try
		{
			Refs.owlRepo.resolve().ensurePeerStarted();
			OWLOntology O = OWL.manager().getOntology(IRI.create(iri)); 
			if (O == null)
				return ko("Ontology not found: " + iri);			
			OWL.reasoner().flush();
			notifyOntoChange(iri);			
			return ko("Operation not supported yet.");
		}
		catch (Throwable t)
		{
			t.printStackTrace(System.err);
			return ko(t.toString());
		}		
	}
	
	@POST
	@Path("/synchToLatest/{iri}")
	public Json pull(@PathParam("iri") String iri)
	{
		VDHGDBOntologyRepository repo = repo();
		try
		{
			Refs.owlRepo.resolve().ensurePeerStarted();
			OWLOntology O = OWL.manager().getOntology(IRI.create(iri)); 
			if (O == null)
				return ko("Ontology not found: " + iri);
			DistributedOntology vo = repo.getDistributedOntology(O); 
			PullActivity pull = repo.pull(vo, Refs.owlRepo.resolve().getDefaultPeer());
			pull.getFuture().get();
			OWL.reasoner().flush();
			notifyOntoChange(iri);		
			return ok().set("message", pull.getCompletedMessage());
		}
		catch (Throwable t)
		{
			t.printStackTrace(System.err);
			return ko(t.toString());
		}		
	}
	
	@POST
	@Path("/pushLatest/{iri}")
	public Json push(@PathParam("iri") String iri)
	{
		VDHGDBOntologyRepository repo = repo();
		try
		{
			Refs.owlRepo.resolve().ensurePeerStarted();
			OWLOntology O = OWL.manager().getOntology(IRI.create(iri)); 
			if (O == null)
				return ko("Ontology not found: " + iri);
			DistributedOntology vo = repo.getDistributedOntology(O); 
			PullActivity pull = repo.pull(vo, Refs.owlRepo.resolve().getDefaultPeer());
			pull.getFuture().get();
			OWL.reasoner().flush();
			notifyOntoChange(iri);		
			return ok().set("message", pull.getCompletedMessage());
		}
		catch (Throwable t)
		{
			t.printStackTrace(System.err);
			return ko(t.toString());
		}				
	}
	
	public Json revertReApply(){
		VDHGDBOntologyRepository repo = repo();
		try
		{ 
			Refs.owlRepo.resolve().ensurePeerStarted();
			
			for (OWLOntology o : OWL.ontologies()) {
				VersionedOntology vo = repo.getVersionControlledOntology(o);
				DistributedOntology dOnto = repo.getDistributedOntology(o);
				HGPeerIdentity server = Refs.owlRepo.resolve().getDefaultPeer();
				
				System.out.println("We must revert...");
				int lastMatchingRevision = revertToLastMatch (vo, dOnto, server);
				System.out.println("Done reverting.");
				System.out.println("Pulling last revision...");
				pullFromServer(dOnto, server);
				System.out.println("Done pulling.");
				System.out.println("Re-Applying previuos changes...");
				applyChangesSinceRevision (o, lastMatchingRevision);
				System.out.println("Done re-applying changes.");
			}
			
			return ok().set("message", "success!"); 
			
		} 
		catch (Throwable t)
		{
			t.printStackTrace(System.err);
			return ko(t.toString());
		}
	}
	
	public Json pushALL()
	{
		return pushALL(true);
	}
	
	public Json pushALL(boolean clearRepo)
	{
		VDHGDBOntologyRepository repo = repo();
		try
		{ 
			Refs.owlRepo.resolve().ensurePeerStarted();
			
			String messages = "";
			System.out.println("Checking for Conflicts/Updates before commiting.");
			for (OWLOntology o : OWL.ontologies()) {
				VersionedOntology vo = repo.getVersionControlledOntology(o);
				DistributedOntology dOnto = repo.getDistributedOntology(o);
				HGPeerIdentity server = Refs.owlRepo.resolve().getDefaultPeer();
				
				switch (getBeforeCommitPushAction(dOnto, server)) {
					case REVERT:
						System.out.println("Server revision conflicts with local.");
						System.out.println("We must revert...");
						int lastMatchingRevision = revertToLastMatch (vo, dOnto, server);
						System.out.println("Done reverting.");
						System.out.println("Pulling last revision...");
						pullFromServer(dOnto, server);
						System.out.println("Done pulling.");
						System.out.println("Re-Applying previuos changes...");
						applyChangesSinceRevision (o, lastMatchingRevision);
						System.out.println("Done re-applying changes.");
						break;
					case PULL:
						System.out.println("Server revision is newer.");
						System.out.println("Pulling last revision...");
						pullFromServer(dOnto, server);
						System.out.println("Done pulling.");						
						break;
					case NOTHING:
						System.out.println("Server revision is identical, Nothing to do.");
						break;

					default:
						throw new RuntimeException ("Cannot commit this time.");
				}			
				
				PushActivity push = repo.push(dOnto, server);
				push.getFuture().get();
				messages += push.getCompletedMessage() + ", ";				
			}
			if (clearRepo){
				OntologyChangesRepo.getInstance().clearAll();
			}
			return ok().set("message", messages); 
		}
		catch (Throwable t)
		{
			t.printStackTrace(System.err);
			return ko(t.toString());
		}		
	}
	
	protected boolean commit(String userName, String comment, List <OWLOntologyChange> changes) throws RuntimeException
	{		
		long commitTimeStamp = new Date().getTime();
		
		VDHGDBOntologyRepository repo = repo();
		try
		{ 
			Refs.owlRepo.resolve().ensurePeerStarted();
			
			OWLOntologyManager manager = OWL.manager();				

			HGPeerIdentity server = Refs.owlRepo.resolve().getDefaultPeer();			
			
			// resolve conflicts before commit
			for (OWLOntology o : OWL.ontologies()) {
				VersionedOntology vo = repo.getVersionControlledOntology(o);
				DistributedOntology dOnto = repo.getDistributedOntology(o); 
				
				if (vo == null || dOnto == null) {
					throw new RuntimeException ("Ontology found, but not versioned or distributed: " + o.getOntologyID());
				}
				
				switch (getBeforeCommitPushAction(dOnto, server)) {
					case REVERT:
						System.out.println("Server revision conflicts with local.");
						System.out.println("We must revert...");
						int lastMatchingRevision = revertToLastMatch (vo, dOnto, server);
						System.out.println("Done reverting.");
						System.out.println("Pulling last revision...");
						pullFromServer(dOnto, server);
						System.out.println("Done pulling.");
						System.out.println("Re-Applying previuos changes...");
						applyChangesSinceRevision (o, lastMatchingRevision);
						System.out.println("Done re-applying changes.");
						break;
					case PULL:
						System.out.println("Server revision is newer.");
						System.out.println("Pulling last revision...");
						pullFromServer(dOnto, server);
						System.out.println("Done pulling.");						
						break;
					case NOTHING:
						System.out.println("Server revision is identical, Nothing to do.");
						break;
	
					default:
						throw new RuntimeException ("Cannot commit this time.");
				}								
			}
			
			manager.applyChanges(changes);			

			int committedOntologyCount = 0;
			for (OWLOntology o : OWL.ontologies()) {
				VersionedOntology vo = repo.getVersionControlledOntology(o);
								
				if (vo == null) {
					throw new RuntimeException ("Ontology found, but not versioned: " + o.getOntologyID());
				}
				
				int nrOfCommittableChanges = vo.getNrOfCommittableChanges(); 
				if (nrOfCommittableChanges == 0) {
					int conflicts = vo.getWorkingSetConflicts().size(); 
					if (conflicts > 0) {
						throw new RuntimeException ("All " + conflicts + " pending changes in Ontology " + o.getOntologyID() + " are conflicts, " 
								+ "which will be removed automatically on commit, so there is no single change to commit..");			
					} else {
						//do nothing
					}
				} else {									
					vo.commit(userName, comment);
					committedOntologyCount++;
					int revision = vo.getHeadRevision().getRevision();
					OntologyChangesRepo.getInstance().setOntoRevisionChanges(o.getOntologyID().toString(), revision, userName, comment, changes, commitTimeStamp);
				}
			}
			
			return committedOntologyCount > 0;
		}
		catch (Throwable t)
		{
			t.printStackTrace(System.err);
			throw new RuntimeException(t.toString());
		}				
	}
	
	private int revertToRevision (int revisionNumber, VersionedOntology vo){
		RevisionID rID = new RevisionID(repo().getOntologyUUID(vo.getWorkingSetData()), revisionNumber);		
		vo.revertHeadTo(rID, true);
		
		OWL.reasoner().flush();
		
		return revisionNumber;
	}
	
	private boolean isPossibleToRollBack (List<Integer> revisions){
		if (revisions.size() < 1) return false;
		
		for (int rnx: revisions){
			boolean rf = false;
			for (OWLOntology o : OWL.ontologies()){
				if (OntologyChangesRepo.getInstance().getOntoRevisionChanges(o.getOntologyID().toString(), rnx) != null) rf = true;
			}
			
			if (!rf) return false;
		}
		
		return true;
	}
	
	private List<Long> resolveCommitTimeStamps (List<Integer> revisions){
		List <Long> result = new ArrayList<>();
		for (OWLOntology o : OWL.ontologies()){
			for (int rvx: revisions){
				OntologyCommit ocx = OntologyChangesRepo.getInstance().getOntoRevisionChanges(o.getOntologyID().toString(), rvx);
				
				if (ocx != null) result.add(ocx.getTimeStamp());
			}
		}
		return result;
	}
	
	
	protected List<OntoChangesReference> rollBackRevisions (List<Integer> revisions){				
		if (!isPossibleToRollBack (revisions)) return new ArrayList<>();
		
		System.out.println("Roll Back Started.");

		HGPeerIdentity server = Refs.owlRepo.resolve().getDefaultPeer();		

		List <Long> timeStamps = resolveCommitTimeStamps(revisions);
		
		try {			
			List<OntoChangesReference> toDelete = new ArrayList<>();
			List<OntoChangesReference> toAdd = new ArrayList<>();			
			
			for (OWLOntology o : OWL.ontologies()){		
				System.out.println("Working on: " + o.getOntologyID().toString());	
				VersionedOntology vo = repo().getVersionControlledOntology(o);
				DistributedOntology dOnto = repo().getDistributedOntology(o); 
				
				if (vo == null || dOnto == null) {
					throw new RuntimeException ("Ontology found, but not versioned or distributed: " + o.getOntologyID());
				}
				
				System.out.println("Revert to last common revision number");
				int baseRevision = revertToLastMatch(vo, dOnto, server);
				System.out.println("Done reverting.");	
				System.out.println("Pulling last revision...");
				pullFromServer(dOnto, server);
				System.out.println("Done pulling.");
				System.out.println("Re-Applying changes skipping selected revisions...");
				applyChangesExcludingRevisions (o, baseRevision, revisions, timeStamps, toDelete, toAdd); 
				System.out.println("Done re-applying changes.");						
			}
			
			System.out.println("Updating changes repo...");
			updateOntologyChangesRepo (toDelete, toAdd);
			System.out.println("Done updating changes repo.");	
			
			System.out.println("Revisions roll back was successfull.");	

			return toDelete;
			
		} catch (Throwable t){
			System.out.println("Error found while performing ontology roll back.");
			System.out.println("Revisions roll back failed.");	
			System.out.println(t.getMessage());
			System.out.println("Re-Applying all removed changes.");
			
			for (OWLOntology o : OWL.ontologies()){
				System.out.println("Working on: " + o.getOntologyID().toString());
				VersionedOntology vo = repo().getVersionControlledOntology(o);
				DistributedOntology dOnto = repo().getDistributedOntology(o); 
				
				if (vo == null || dOnto == null) {
					throw new RuntimeException ("Ontology found, but not versioned or distributed: " + o.getOntologyID());
				}
				
				System.out.println("Revert to last common revision number");
				int baseRevision = revertToLastMatch(vo, dOnto, server);
				System.out.println("Done reverting.");
				System.out.println("Re-Applying previuos changes...");
				applyChangesSinceRevision (o, baseRevision);
				System.out.println("Done re-applying changes.");
				System.out.println("Done with: " + o.getOntologyID().toString());
			}
			

			System.out.println("Ontology successfully set to pre-rollback state.");
			
			OWL.reasoner().flush();
			
			return new ArrayList<>();			
		}
	}
	
	private void updateOntologyChangesRepo (List<OntoChangesReference> toDelete, List<OntoChangesReference> toAdd){
		for (int i = 0; i < toDelete.size(); i++){
			OntologyChangesRepo.getInstance().deleteOntoRevisionChanges(toDelete.get(i).getOnto(), toDelete.get(i).getRevision());
		}
		for (int i = 0; i < toAdd.size(); i++){
			OntologyChangesRepo.getInstance().setOntoRevisionChanges(toAdd.get(i).getOnto(), toAdd.get(i).getRevision(), toAdd.get(i).getValue());
		}
	}
	
	private void applyChangesExcludingRevisions (OWLOntology o, 
												 int baseRevision, 
												 List<Integer> excludedRevisions,
												 List<Long> excludedTimeStamps,
												 List<OntoChangesReference> toDelete, 
												 List<OntoChangesReference> toAdd){
		
		Map<Integer, OntologyCommit> changes = OntologyChangesRepo.getInstance().getAllRevisionChangesForOnto(o.getOntologyID().toString());
		
		if (changes != null && !changes.isEmpty()) {
		
			VersionedOntology vo = repo().getVersionControlledOntology(o);
			
			if (vo == null) {
				throw new RuntimeException ("Ontology found, but not versioned: " + o.getOntologyID());
			}
			
			for (Map.Entry<Integer, OntologyCommit> rx: changes.entrySet()){
				int key = rx.getKey();
				OntologyCommit cx = rx.getValue();
				if (key > baseRevision && 
				    excludedRevisions.indexOf(key) < 0 && 
				    excludedTimeStamps.indexOf(cx.getTimeStamp()) < 0){					
						OWL.manager().applyChanges(cx.getChanges());
						vo.commit(cx.getUserName(), cx.getComment());
						int newRevision = vo.getHeadRevision().getRevision();
						toDelete.add(new OntoChangesReference(o.getOntologyID().toString(), key, cx));
						toAdd.add(new OntoChangesReference(o.getOntologyID().toString(), newRevision, cx));		
						
				} else {
					toDelete.add(new OntoChangesReference(o.getOntologyID().toString(), key, cx));
				}
			}
		} else System.out.println("No previous changes saved for: " + o.getOntologyID().toString());
	}
	
	protected int lastMatch(OWLOntology o){
		VersionedOntology vo = repo().getVersionControlledOntology(o);
		
		if (vo == null) {
			throw new RuntimeException ("Ontology found, but not versioned: " + o.getOntologyID());
		}
		
		HGPeerIdentity server = Refs.owlRepo.resolve().getDefaultPeer();
		DistributedOntology dOnto = repo().getDistributedOntology(o); 
		
		VersionedOntologyComparisonResult compare = null;
		try {
			compare = repo().compareOntologyToRemote(dOnto, server, ACTIVITY_TIMEOUT_SECS);
		} catch (Throwable t) {
			throw new RuntimeException("System error while comparing to remote");
		}
		
		return compare.getRevisionResults().get(compare.getLastMatchingRevisionIndex()).getTarget().getRevision();
	}
	
	private int revertToLastMatch (VersionedOntology vo, DistributedOntology dOnto, HGPeerIdentity server){
		VersionedOntologyComparisonResult compare = null;
		try {
			compare = repo().compareOntologyToRemote(dOnto, server, ACTIVITY_TIMEOUT_SECS);
		} catch (Throwable t) {
			throw new RuntimeException("System error while comparing to remote");
		}
		
		int result = compare.getRevisionResults().get(compare.getLastMatchingRevisionIndex()).getTarget().getRevision();		
		RevisionID rID = new RevisionID(repo().getOntologyUUID(vo.getWorkingSetData()), result);		
		vo.revertHeadTo(rID, true);
		
		OWL.reasoner().flush();
		
		return result;
	}
	
	private void applyChangesSinceRevision (OWLOntology o, int revision){
		Map<Integer, OntologyCommit> changes = OntologyChangesRepo.getInstance().getAllRevisionChangesForOnto(o.getOntologyID().toString());
		
		if (changes != null && !changes.isEmpty()) {
		
			VersionedOntology vo = repo().getVersionControlledOntology(o);
			
			if (vo == null) {
				throw new RuntimeException ("Ontology found, but not versioned: " + o.getOntologyID());
			}
			
			for (Map.Entry<Integer, OntologyCommit> rx: changes.entrySet()){
				if (rx.getKey().intValue() > revision){
					OWL.manager().applyChanges(rx.getValue().getChanges());
					vo.commit(rx.getValue().getUserName(), rx.getValue().getComment());
					int newRevision = vo.getHeadRevision().getRevision();
					OntologyChangesRepo.getInstance().deleteOntoRevisionChanges(o.getOntologyID().toString(), rx.getKey().intValue());
					OntologyChangesRepo.getInstance().setOntoRevisionChanges(o.getOntologyID().toString(), newRevision, rx.getValue());
				}
			}
		} else System.out.println("No previous changes saved for: " + o.getOntologyID().toString());
	}

	
	@POST
	@Path("/reloadFileBased/{iri}")
	public Json reload(@PathParam("iri") String iri)
	{
		try
		{
			OWLOntologyManager manager = OWL.manager(); 
			OWLOntology ont = manager.getOntology(IRI.create(iri)); 
			if (ont == null)
				return ko("Ontology not found: " + iri);
			IRI ontologyDocumentIRI = OWL.manager().getOntologyDocumentIRI(ont);
//
			System.out.print("loading " + iri + " from " + ontologyDocumentIRI + "...");
			synchronized(OWL.reasoner()) 
			{
		        manager.removeOntology(ont);
		        try {
		            ont = manager.loadOntologyFromOntologyDocument(ontologyDocumentIRI);
					System.out.println("done.");
					System.out.print("flushing reasoner" + OWL.reasoner() + "...");
					//TODO maybe check OWL.reasoner().getBufferingMode(), so no flush might be needed.
					OWL.reasoner().flush();
					System.out.println("done.");
				}
		        catch (Throwable t) {
		        	if (manager instanceof SynchronizedOWLOntologyManager) 
		        	{
		        		manager = ((SynchronizedOWLOntologyManager)manager).getWrappedOWLOntologyManager(); 
		        	}
		            ((OWLOntologyManagerImpl) manager).ontologyCreated(ont);  // put it back - a hack but it works
		            manager.setOntologyDocumentIRI(ont, ontologyDocumentIRI);
		            String msg = "reload of " + iri + " from " + ontologyDocumentIRI + " failed with exception " + t.toString();
		            System.err.println(msg);
		            return ko(msg);
		        }			
			}
			//
			notifyOntoChange(iri);			
			return ok().set("message", "reload successful for " + iri + " from " + ontologyDocumentIRI);
		}
		catch (Throwable t)
		{
			t.printStackTrace(System.err);
			return ko(t.toString());
		}		
	}

	/**
	 * Gets the total number of cache entries in the cached reasoner. 
	 * @return a json containing a long value; -1 if not a cached reasoner.
	 */
	@GET
	@Path("/cachedReasonerTotalEntryCount")
	public Json cachedReasonerTotalEntryCount()
	{
		OWLReasoner r = OWL.reasoner();
		if (r instanceof CachedReasoner) 
		{
			CachedReasoner cr = (CachedReasoner)r;
			return Json.make(cr.getTotalCacheEntryCount());
		}
		else
			return Json.make(-1);
	}

	@GET
	@Path("/cachedReasonerStats")
	public Json cachedReasonerStats()
	{
		OWLReasoner r = OWL.reasoner();
		if (r instanceof CachedReasoner) 
		{
			CachedReasoner cr = (CachedReasoner)r;
			return Json.make(cr.getCacheStatus());
		}
		else
			return Json.make("Reasoner is not a CachedReasoner instance.");
	}
	
	@GET
	@Path("/nrOfCachedReasoners")
	public Json nrOfCachedReasoners()
	{
		return Json.object("nrOfCachedReasoners", OWL.loader().getNrOfCachedReasoners());
	}
	

	@GET
	@Path("/cachedReasonerQ1")
	public Json cachedReasonerQ1()
	{
		OWLReasoner r = OWL.reasoner();
		if (r instanceof CachedReasoner) 
		{
			CachedReasoner cr = (CachedReasoner)r;
			return cr.getInstancesCacheRequests();
		}
		else
			return Json.make("Reasoner is not a CachedReasoner instance.");
	}

	@GET
	@Path("/cachedReasonerQ1Populate")
	public Json cachedReasonerQ1Populate()
	{
		String fileStr = StartUp.getConfig().at("workingDir").asString() + CACHED_REASONER_POPULATE_GET_INSTANCES_CACHE_FILE;
		File f = new File(fileStr);
		if (!f.exists()) 
			return GenUtils.ko("Cannot populate: no file at :" + CACHED_REASONER_POPULATE_GET_INSTANCES_CACHE_FILE);
		OWLReasoner r = OWL.reasoner();
		if (r instanceof CachedReasoner) 
		{
			Json queries = Json.read(GenUtils.readTextFile(f));
			CachedReasoner cr = (CachedReasoner)r;
			return cr.populateGetInstancesCache(queries);
		}
		else
			return Json.make("Reasoner is not a CachedReasoner instance.");
	}
	
	public synchronized Json compare(String ontologyName){
		Refs.owlRepo.resolve().ensurePeerStarted();
		VDHGDBOntologyRepository repo = repo();
	//			VDHGDBOntologyRepository repo = owlRepo.repo();

		Json json = Json.array(); 
	    String iri = null;
		
	
		iri = "hgdb://www.miamidade.gov/cirm/" + ontologyName; 
		
		HGDBOntology activeOnto = repo().getOntologyByDocumentIRI(IRI.create(iri));
		//HGDBOntology activeOnto = repo.getOntologyByDocumentIRI(OWL.fullIri("http://www.miamidade.gov/cirm/legacy"));
					
		DistributedOntology distributedOnto = repo.getDistributedOntology(activeOnto); 
		
		
		HGPeerIdentity server;  
		ClientCentralizedOntology centralO = (ClientCentralizedOntology)distributedOnto; 
		
		
		server = centralO.getServerPeer(); 
		
		//if(server == null || distributedOnto == null || repo == null)
		//	return json;
		
		VersionedOntologyComparisonResult result = repo.compareOntologyToRemote(distributedOnto, server, 180); 
		
		Revision source = null; 
		Revision target = null;
		
		for(RevisionComparisonResult r : result.getRevisionResults()){
			
			RevisionCompareOutcome revisionOutcome = r.getOutcome();
		    
			source = r.getSource(); 
			target = r.getTarget(); 
			
			if(!revisionOutcome.name().equals("MATCH"))
			{
			Json outcome = Json.object().set("name", revisionOutcome.name());
		    
			Json sourceJson = Json.object();
			if(source != null)
			{
			
				sourceJson.set("revision", source.getRevision())
			    		.set("comment", source.getRevisionComment())
			    		.set("date", source.getTimeStamp().getTime())
			    		.set("user", source.getUser());	
			}
			else
			sourceJson = Json.nil();
		  
			Json targetJson = Json.object();
		    if(target != null){
		    
		    	targetJson.set("revision", target.getRevision())
			    		.set("comment", target.getRevisionComment())
			    		.set("date", target.getTimeStamp().getTime())
			    		.set("user", target.getUser());	
		    }
		    else
		    	targetJson = Json.nil();
			
			
			Json obj = Json.object().set("outcome", outcome)
					.set("source", sourceJson)
					.set("target", targetJson);			
			
			
			
			json.add(obj);
			}
		}		
		
		return json;
	}
	
	
	/**
	 * Higher level cache population of the serial entity cache by running common login queries and
	 * caching fully resolved Json objects. e.g. for each SR.
	 * This leads to ~7x faster login performance after a server restart.
	 * 
	 */
	public void populateIndividualSerialEntityCache() 
	{
		RestService.forceClientExempt.set(true);
		ThreadLocalStopwatch.start("START populateIndividualSerialEntityCache");
		OWLIndividuals oind = new OWLIndividuals();
		for (int i = 0; i < SERIAL_PRECACHE_IND_QUERIES.length; i++) {
			ThreadLocalStopwatch.start("START query " + i + " of " + SERIAL_PRECACHE_IND_QUERIES.length 
					+ " q=" + SERIAL_PRECACHE_IND_QUERIES[i]);
			oind.doQuery(SERIAL_PRECACHE_IND_QUERIES[i]);
			ThreadLocalStopwatch.start("DONE query");
		}
		ThreadLocalStopwatch.start("END populateIndividualSerialEntityCache");		
	}
	
	public static void main(String[]args)
	{
		OntoAdmin admin = new OntoAdmin();		
	
		try
		{
			Refs.owlRepo.resolve().ensurePeerStarted();
			Thread.sleep(10000);
			for (HGPeerIdentity id : admin.repo().getPeer().getConnectedPeers())
			{
				System.out.println(id.toString() + " -- " + admin.repo().getPeer().getNetworkTarget(id));
				System.out.println("dev:" + admin.repo().getPeer().getIdentity("cirmdevelopmentontology"));
			}
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
		finally
		{
			admin.repo().getPeer().stop();
		}
	}
	
	/**
	 * Checks if local pending changes may be committed and sent to the server based on a comparison.
	 * Will open explanatory dialogs if a reason is found that would prevent a commit.
	 * 
	 * @param dOnto
	 * @param server
	 * @return
	 */
	public REPOACTION getBeforeCommitPushAction(DistributedOntology dOnto, HGPeerIdentity server) {
		VersionedOntologyComparisonResult result = null;
		try {
			result = repo().compareOntologyToRemote(dOnto, server, ACTIVITY_TIMEOUT_SECS);
		} catch (Throwable t) {
			throw new RuntimeException("System error while comparing to remote");
		}
		if (result != null) {
			if (result.isConflict()) {
				return REPOACTION.REVERT;
			} else if (result.isTargetNewer()) {
				return REPOACTION.PULL;							
			} else {
				return REPOACTION.NOTHING;
			}
		} else {
			throw new RuntimeException("Cannot commit: There was a problem comparing the local history to the server's ontology. This might mean that the server was not available or a timeout occured. ");
		}
	}
	
	/**
	 * pull from repo
	 * 
	 */
	
	public boolean pullFromServer (DistributedOntology dOnto, HGPeerIdentity server){
		PullActivity pa = repo().pull(dOnto, server);
		try {
			ActivityResult paa = pa.getFuture().get(ACTIVITY_TIMEOUT_SECS, TimeUnit.SECONDS);
			if (paa.getException() != null) {						
				throw new RuntimeException(paa.getException().getMessage());
			}
		} catch (Throwable e) {
			throw new RuntimeException("Transaction timed out while pulling from the server."); 	
		}
		OWL.reasoner().flush();
		return true;
	}

}

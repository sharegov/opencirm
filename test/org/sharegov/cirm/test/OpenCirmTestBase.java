package org.sharegov.cirm.test;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hypergraphdb.app.owl.versioning.RevisionID;
import org.hypergraphdb.app.owl.versioning.VersionedOntology;
import org.hypergraphdb.app.owl.versioning.distributed.VDHGDBOntologyRepository;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.semanticweb.owlapi.model.IRI;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.StartUp;

/**
 * Abstract OpenCirm test base class, from which all opencirm test suites and all opencirm tests should inherit.
 * This ensures, that independently of which class starts a junit test or suite, opencirm is only started once and the server
 * remains running throughout the test, testsuite or master suite of suites.
 * 
 * <br>
 * Usage:
 * @see package org.sharegov.cirm.test.demo 
 * 
 * @author Thomas Hilpold
 *
 */
public abstract class OpenCirmTestBase
{
	static Map<VersionedOntology, RevisionID> vontos2headRevisionID = new HashMap<VersionedOntology, RevisionID>();
	
	
	
	
	
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		if (!StartUp.isServerStarted()) {
			System.out.println("Starting openCirm...");
			StartUp.main(new String[]{});
			if (!StartUp.isServerStarted()) {
				throw new IllegalStateException("Server did not start up");
			} else {
				System.out.println("Starting openCirm...COMPLETED.");
			}
		}
		
		
		rollbackAllOntoPendingChanges();
		saveAllOntoHeadRevisions();
	}
	
	
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		System.out.println("ServiceCaseManagerTest class completed.");
		revertAllOntosToSavedHeadRevisions();
	}
	
	protected static void rollbackAllOntoPendingChanges() {
		VDHGDBOntologyRepository repo = Refs.owlRepo.resolve().repo();
		for (VersionedOntology vo : repo.getVersionControlledOntologies()) {
			if (!vo.getWorkingSetChanges().isEmpty()) {
				IRI ontologyIri = vo.getRevisionData(vo.getHeadRevision()).getOntologyID().getOntologyIRI();
				int nrOfPendingChanges = vo.getWorkingSetChanges().size();
				System.out.println("Rolling back " + nrOfPendingChanges + " pending changes for ontology " + ontologyIri);
				vo.rollback();
			}
		}		
	}
	/**
	 * Saves the current ontology head revision id for each versioned ontology in the repository.
	 */
	protected static void saveAllOntoHeadRevisions() {
		VDHGDBOntologyRepository repo = Refs.owlRepo.resolve().repo();
		List<VersionedOntology> vontos = repo.getVersionControlledOntologies();
		for (VersionedOntology vo : vontos) {
			RevisionID originalHeadRevisionID = new RevisionID(vo.getHeadRevision().getOntologyUUID(),
					vo.getHeadRevision().getRevision());
			IRI ontologyIri = vo.getRevisionData(vo.getHeadRevision()).getOntologyID().getOntologyIRI();
			System.out.println("Saving Versioned Ontology " + ontologyIri 
					+ " Head Revision is " + originalHeadRevisionID.getRevision());
			vontos2headRevisionID.put(vo, originalHeadRevisionID);
		}
	}
	
	protected static void revertAllOntosToSavedHeadRevisions() {
		for (Map.Entry<VersionedOntology, RevisionID> vonto2HeadRevisionIDEntry : vontos2headRevisionID.entrySet()) {
			VersionedOntology vo = vonto2HeadRevisionIDEntry.getKey();
			RevisionID rId = vonto2HeadRevisionIDEntry.getValue();
			int currentHeadRevision = vo.getHeadRevision().getRevision();
			//Check if we need to revert this versioned ontology to a previous revision
			if (currentHeadRevision > rId.getRevision()) {
				IRI ontologyIri = vo.getRevisionData(vo.getHeadRevision()).getOntologyID().getOntologyIRI();
				System.out.println("Reverting Versioned Ontology " + ontologyIri + " from " + currentHeadRevision + " to " + rId.getRevision());
				vo.revertHeadTo(rId);
			}
		}
	}
	
}

package org.sharegov.cirm.rest;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.hypergraphdb.app.owl.versioning.distributed.VDHGDBOntologyRepository;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import static org.sharegov.cirm.utils.GenUtils.ok;
import static org.sharegov.cirm.utils.GenUtils.ko;

import mjson.Json;

@Path("sradmin")
@Produces("application/json")
public class ServiceCaseAdmin extends OntoAdmin {
	
	private static final String PREFIX = "legacy:";

	@POST
	@Path("/disable/")
	public Json disable(Json srData)
	{
		try
		{ 
			Refs.owlRepo.resolve().ensurePeerStarted();
			OWLOntology O = OWL.ontology(); 
			String ontologyIri = Refs.defaultOntologyIRI.resolve();
			
			if (O == null) {
				throw new RuntimeException ("Ontology not found: " + ontologyIri);
			}
			//hard coded for now
			//srData = Json.object().set("userName", "camilo").set("srtype","BULKTRA");
			String userName = srData.at("userName").asString();
			String srType = srData.at("srtype").asString();
			String comment = "Disable Service Request "+PREFIX+srType;
			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");			
	
			OWLOntologyManager manager = OWL.manager();
			OWLDataFactory factory = manager.getOWLDataFactory();
			OWLIndividual sr = factory.getOWLNamedIndividual(OWL.fullIri(PREFIX + srType));		
			OWLDataProperty property = factory.getOWLDataProperty(OWL.fullIri(PREFIX + "isDisabledCreate"));
			OWLLiteral value =	factory.getOWLLiteral(true);
			OWLDataPropertyAssertionAxiom assertion = factory.getOWLDataPropertyAssertionAxiom(property, sr, value);
			//factory.get
			//manager.
			AddAxiom addAxiom = new AddAxiom(O, assertion);
			//RemoveAxiom re
			List <OWLOntologyChange> changes = new ArrayList <OWLOntologyChange>();
			changes.add(addAxiom);
			
			commit(ontologyIri, userName, comment, changes);
			
			return ok();
		}
		catch(RuntimeException ex){
			ex.printStackTrace(System.err);
			return ko(ex.toString());
		}
		catch (Throwable t)
		{
			t.printStackTrace(System.err);
			return ko(t.toString());
		}
		
	}

}

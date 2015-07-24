package org.sharegov.cirm.rest;

import static org.sharegov.cirm.OWL.owlClass;
import static org.sharegov.cirm.OWL.reasoner;
import static org.sharegov.cirm.utils.GenUtils.ko;
import static org.sharegov.cirm.utils.GenUtils.ok;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import mjson.Json;

import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.legacy.Permissions;

@Path("sradmin")
@Produces("application/json")
public class ServiceCaseAdmin extends OntoAdmin {
	
	private static final String PREFIX = "legacy:";
	/**
	 * 
	 *
	 */
	
	@GET
	@Path("/serviceCases/enabled")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getEnabledServiceCases() {
		try
		{
			OWLReasoner reasoner = reasoner();
			OWLClass serviceCase = owlClass(PREFIX + "ServiceCase");
			//TODO: Permission check
			//permissionCheck(serviceCase)
			Set<OWLNamedIndividual> S = reasoner.getInstances(serviceCase, false).getFlattened();
			Json A = Json.array();
			for (OWLNamedIndividual ind : S)
			{	
				Set<OWLLiteral> values = OWL.dataProperties(ind, PREFIX+ "isDisabledCreate");
				if(!values.contains(OWL.dataFactory().getOWLLiteral(true))){
					A.add(Json.object().set("iri",ind.getIRI().toString()).set("code", ind.getIRI().getFragment()).set("label",OWL.getEntityLabel(ind)));
				}
			}
			return Response.ok(A, MediaType.APPLICATION_JSON).build();
		} catch (Exception e) {
			return Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.type(MediaType.APPLICATION_JSON)
					.entity(Json.object().set("error", e.getClass().getName())
							.set("message", e.getMessage())).build();
		}
	}
	
	@POST
	@Path("/disable")
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
	
	private void permissionCheck(OWLClassExpression expr){
		//TODO enable security
//		if (!isClientExempt() && reasoner().getSuperClasses(expr, false).containsEntity(owlClass("Protected")))
//			expr = OWL.and(expr, Permissions.constrain(OWL.individual("BO_View"), getUserActors()));
//		else if (!isClientExempt() && !reasoner().getSubClasses(OWL.and(expr, owlClass("Protected")), false).isBottomSingleton())
//		{
//			return ko("Access denied - protected resources could be returned, please split the query.");
//		}
		
	}

}

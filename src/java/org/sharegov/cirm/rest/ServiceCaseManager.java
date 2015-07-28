package org.sharegov.cirm.rest;

import static org.sharegov.cirm.OWL.owlClass;
import static org.sharegov.cirm.OWL.reasoner;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import mjson.Json;

import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.OWLClass;
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

public class ServiceCaseManager extends OntoAdmin {

	private static final String PREFIX = "legacy:";

	public Json getEnabled() {
		return getServiceCasesByStatus(true);
	}

	public Json getDisabled() {
		return getServiceCasesByStatus(false);
	}

	// returns a list of enabled/disabled service cases
	// parameter isGetEnabled describes whether the function returns all enabled
	// or all disabled SRs
	// if isGetEnabled == true, returns all enabled
	// if isGetEnabled == false, returns all disabled
	private Json getServiceCasesByStatus(boolean isGetEnabled) {
		OWLReasoner reasoner = reasoner();
		OWLClass serviceCase = owlClass(PREFIX + "ServiceCase");
		// TODO: Permission check
		// permissionCheck(serviceCase)
		Set<OWLNamedIndividual> S = reasoner.getInstances(serviceCase, false)
				.getFlattened();
		Json A = Json.array();
		for (OWLNamedIndividual ind : S) {
			Set<OWLLiteral> values = OWL.dataProperties(ind, PREFIX
					+ "isDisabledCreate");
			if ((!isGetEnabled && values.contains(OWL.dataFactory()
					.getOWLLiteral(true)))
					|| (isGetEnabled && !values.contains(OWL.dataFactory()
							.getOWLLiteral(true)))) {
				A.add(Json.object().set("iri", ind.getIRI().toString())
						.set("code", ind.getIRI().getFragment())
						.set("label", OWL.getEntityLabel(ind)));
			}
		}

		return A;
	}

	public Json disable(String srType, String userName, String comment) {
		Refs.owlRepo.resolve().ensurePeerStarted();
		OWLOntology O = OWL.ontology();
		String ontologyIri = Refs.defaultOntologyIRI.resolve();

		if (O == null) {
			throw new RuntimeException("Ontology not found: " + ontologyIri);
		}

		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory factory = manager.getOWLDataFactory();
		OWLIndividual sr = factory.getOWLNamedIndividual(OWL.fullIri(PREFIX
				+ srType));
		OWLDataProperty property = factory.getOWLDataProperty(OWL
				.fullIri(PREFIX + "isDisabledCreate"));
		OWLLiteral value = factory.getOWLLiteral(true);
		OWLDataPropertyAssertionAxiom assertion = factory
				.getOWLDataPropertyAssertionAxiom(property, sr, value);
		AddAxiom addAxiom = new AddAxiom(O, assertion);
		List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
		changes.add(addAxiom);

		commit(ontologyIri, userName, comment, changes);

		return Json.object().set("success", true);
	}
}

package org.sharegov.cirm.legacy;

import static org.sharegov.cirm.OWL.owlClass;
import static org.sharegov.cirm.OWL.reasoner;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import mjson.Json;

import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.owl.OwlRepo;
import org.sharegov.cirm.rest.OntoAdmin;
import org.sharegov.cirm.utils.GenUtils;

public class ServiceCaseManager extends OntoAdmin {

	private static final String PREFIX = "legacy:";

	private OwlRepo getRepo() {
		return Refs.owlRepo.resolve();
	}

	public Json getEnabled() {
		return getServiceCasesByStatus(true);
	}

	public Json getDisabled() {
		return getServiceCasesByStatus(false);
	}

	public Json getAll() {
		Set<OWLNamedIndividual> S = getAllIndividuals();
		Json A = Json.array();
		for (OWLNamedIndividual ind : S) {
			A.add(Json.object().set("iri", ind.getIRI().toString())
					.set("code", ind.getIRI().getFragment())
					.set("label", OWL.getEntityLabel(ind)));
		}

		return A;
	}

	private Set<OWLNamedIndividual> getAllIndividuals() {
		OWLReasoner reasoner = reasoner();
		OWLClass serviceCase = owlClass(PREFIX + "ServiceCase");
		// TODO: Permission check
		// permissionCheck(serviceCase)
		return reasoner.getInstances(serviceCase, false).getFlattened();
	}

	// returns a list of enabled/disabled service cases
	// parameter isGetEnabled describes whether the function returns all enabled
	// or all disabled SRs
	// if isGetEnabled == true, returns all enabled
	// if isGetEnabled == false, returns all disabled
	private Json getServiceCasesByStatus(boolean isGetEnabled) {
		Set<OWLNamedIndividual> S = getAllIndividuals();
		Json A = Json.array();
		for (OWLNamedIndividual ind : S) {
			boolean isSrDisabledOrDisabledCreate = isSrDisabledOrDisabledCreate(ind);
			boolean shouldAddServiceCase = (!isGetEnabled && isSrDisabledOrDisabledCreate)
					|| (isGetEnabled && !isSrDisabledOrDisabledCreate);
			if (shouldAddServiceCase) {
				A.add(Json.object().set("iri", ind.getIRI().toString())
						.set("code", ind.getIRI().getFragment())
						.set("label", OWL.getEntityLabel(ind)));
			}
		}

		return A;
	}

	/**
	 * Checks if an Sr has isDisabledCreate true or isDisabled true
	 * 
	 * @param srTypeIndividual
	 * @return false if either no property
	 */
	private boolean isSrDisabledOrDisabledCreate(
			OWLNamedIndividual srTypeIndividual) {
		Set<OWLLiteral> values = OWL.dataProperties(srTypeIndividual, PREFIX
				+ "isDisabledCreate");
		boolean isDisabledCreate = values.contains(OWL.dataFactory()
				.getOWLLiteral(true));
		values = OWL.dataProperties(srTypeIndividual, PREFIX + "isDisabled");
		return isDisabledCreate
				|| values.contains(OWL.dataFactory().getOWLLiteral(true));
	}

	public Json disable(String srType, String userName, String comment) {
		OwlRepo repo = getRepo();

		synchronized (repo) {
			repo.ensurePeerStarted();
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

			return Json.object().set("success",
					commit(ontologyIri, userName, comment, changes));
		}
	}

	public Json enable(String srType, String userName, String comment) {
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();
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

			OWLLiteral disableValue = factory.getOWLLiteral(true);
			OWLDataPropertyAssertionAxiom removeAssertion = factory
					.getOWLDataPropertyAssertionAxiom(property, sr,
							disableValue);
			RemoveAxiom removeAxiom = new RemoveAxiom(O, removeAssertion);

			OWLLiteral enableValue = factory.getOWLLiteral(false);
			OWLDataPropertyAssertionAxiom addAssertion = factory
					.getOWLDataPropertyAssertionAxiom(property, sr, enableValue);
			AddAxiom addAxiom = new AddAxiom(O, addAssertion);

			List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
			changes.add(removeAxiom);
			changes.add(addAxiom);

			return Json.object().set("success",
					commit(ontologyIri, userName, comment, changes));
		}
	}

	public Json push() {
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();
			OWLOntology O = OWL.ontology();
			String ontologyIri = Refs.defaultOntologyIRI.resolve();

			if (O == null) {
				throw new RuntimeException("Ontology not found: " + ontologyIri);
			}

			return push(ontologyIri);
		}
	}

	public Json changeQuestionLabel(String questionUri, String oldLabel, String newLabel, String userName, String comment) {
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();
			OWLOntology O = OWL.ontology();
			String ontologyIri = Refs.defaultOntologyIRI.resolve();

			if (O == null) {
				throw new RuntimeException("Ontology not found: " + ontologyIri);
			}

			OWLOntologyManager manager = OWL.manager();
			OWLDataFactory factory = manager.getOWLDataFactory();

			OWLNamedIndividual ind = factory.getOWLNamedIndividual(OWL
					.fullIri(PREFIX + questionUri));
			OWLDataProperty property = factory.getOWLDataProperty(OWL
					.fullIri(PREFIX + "label"));

			OWLLiteral oldValue = factory.getOWLLiteral(oldLabel);
			OWLDataPropertyAssertionAxiom removeAssertion = factory
					.getOWLDataPropertyAssertionAxiom(property, ind, oldValue);
			RemoveAxiom removeAxiom = new RemoveAxiom(O, removeAssertion);

			OWLLiteral newValue = factory.getOWLLiteral(newLabel);
			OWLDataPropertyAssertionAxiom addAssertion = factory
					.getOWLDataPropertyAssertionAxiom(property, ind, newValue);
			AddAxiom addAxiom = new AddAxiom(O, addAssertion);

			List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
			changes.add(removeAxiom);
			changes.add(addAxiom);

			return Json.object().set("success",
					commit(ontologyIri, userName, comment, changes));

		}
	}

	private boolean replaceObjectLiteralProperty(String individualID, String propertyID, String newValue) {
		List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
		
		RemoveAxiom removeAxiom = createIndividualRemoveAxiom (individualID, propertyID);
		
		if (removeAxiom == null ) return false;
		
		changes.add(removeAxiom);
		changes.add(createIndividualAddAxiom (individualID, propertyID, newValue));

		return true;
	}
	
	private AddAxiom createIndividualAddAxiom (String individualID, String propertyID, String newValue){
		OWLOntology O = OWL.ontology();
		String ontologyIri = Refs.defaultOntologyIRI.resolve();

		if (O == null) {
			throw new RuntimeException("Ontology not found: " + ontologyIri);
		}

		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory factory = manager.getOWLDataFactory();

		OWLNamedIndividual individual = factory.getOWLNamedIndividual(OWL.fullIri(PREFIX + individualID));
		OWLDataProperty property = factory.getOWLDataProperty(OWL.fullIri(PREFIX + propertyID));
		
		OWLLiteral newLiteralValue = factory.getOWLLiteral(newValue);
		OWLDataPropertyAssertionAxiom addAssertion = factory.getOWLDataPropertyAssertionAxiom(property, individual, newLiteralValue);
		
		return new AddAxiom(O, addAssertion);
	}
	
	private RemoveAxiom createIndividualRemoveAxiom (String individualID, String propertyID){
		OWLOntology O = OWL.ontology();
		String ontologyIri = Refs.defaultOntologyIRI.resolve();

		if (O == null) {
			throw new RuntimeException("Ontology not found: " + ontologyIri);
		}

		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory factory = manager.getOWLDataFactory();

		OWLNamedIndividual individual = factory.getOWLNamedIndividual(OWL.fullIri(PREFIX + individualID));
		OWLDataProperty property = factory.getOWLDataProperty(OWL.fullIri(PREFIX + propertyID));
		
		Set<OWLLiteral> propValues = OWL.reasoner().getDataPropertyValues(OWL.individual(PREFIX + individualID),	OWL.dataProperty(PREFIX + propertyID));
		
		if (propValues.isEmpty()){
			// Axiom not found 
			return null;
		}
		
		String existingValue = "";		
		for (OWLLiteral v : propValues) {
			existingValue = v.getLiteral().toString();
		}
		
		OWLLiteral oldLiteralValue = factory.getOWLLiteral(existingValue);
		OWLDataPropertyAssertionAxiom removeAssertion = factory.getOWLDataPropertyAssertionAxiom(property, individual, oldLiteralValue);
		
		return new RemoveAxiom(O, removeAssertion);
	}
	
	public Json replaceObjectAnnotation(String individualID, String newAnnotationContent, String userName, String comment){
		//Init repo
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();
			OWLOntology O = OWL.ontology();
			String ontologyIri = Refs.defaultOntologyIRI.resolve();
			//get the individual
			OWLEntity entity = OWL.dataFactory().getOWLNamedIndividual(OWL.fullIri(PREFIX + individualID));
			String existingLabel = OWL.getEntityLabel(entity);
			//create existing annotation
			OWLAnnotationAssertionAxiom toRemove = OWL.dataFactory().getOWLAnnotationAssertionAxiom(
					entity.getIRI(), OWL.dataFactory().getOWLAnnotation(OWL.annotationProperty("http://www.w3.org/2000/01/rdf-schema#label"), OWL.dataFactory().getOWLLiteral(existingLabel)));
			//create new annotation
			OWLAnnotationAssertionAxiom toAdd = OWL.dataFactory().getOWLAnnotationAssertionAxiom(
					entity.getIRI(), OWL.dataFactory().getOWLAnnotation(OWL.annotationProperty("http://www.w3.org/2000/01/rdf-schema#label"), OWL.dataFactory().getOWLLiteral(newAnnotationContent)));		
			//create remove axiom for existing annotation
			RemoveAxiom removeAxiom = new RemoveAxiom(O, toRemove);
			//create add axiom for new annotation
			AddAxiom addAxiom = new AddAxiom(O, toAdd); 
			//add changes 
			List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
			changes.add(removeAxiom);
			changes.add(addAxiom);
			//TODO:
			//commit to database			
			return Json.object().set("success", commit(ontologyIri, userName, comment, changes));
		}
	}

	public Json refreshOnto() {
		// String jenkingsEndpointFullDeploy =
		// "https://api.miamidade.gov/jenkins/job/CIRM-ADMIN-TEST-CI-JOB-OPENCIRM/build?token=7ef54dc3a604a1514368e8707d8415";
		String jenkingsEndpointRefreshOntosOnly = "https://api.miamidade.gov/jenkins/job/CIRM-ADMIN-TEST-REFRESH-ONTOS/build?token=1a85a585ef7c424191c7c58ee3c4a97d556eec91";

		return GenUtils.httpPostWithBasicAuth(jenkingsEndpointRefreshOntosOnly, "cirm", "admin", "");
	}

		

	public void testAxiom() {
		OwlRepo repo = getRepo();
		repo.ensurePeerStarted();
		// OWLOntology O = OWL.ontology();
		Set<OWLLiteral> propValues = OWL.reasoner()
				.getDataPropertyValues(OWL.individual("legacy:311OTHER_Q3"),
						OWL.dataProperty("label"));
		System.out.println("isEmpty" + propValues.isEmpty());
		for (OWLLiteral v : propValues) {
			System.out.println(v.getLiteral().toString());
		}

		Set<OWLLiteral> propValues1 = OWL.reasoner().getDataPropertyValues(
				OWL.individual("legacy:311OTHER_Q3"),
				OWL.dataProperty("legacy:label"));
		System.out.println("isEmpty(legacy)" + propValues.isEmpty());
		for (OWLLiteral v : propValues1) {
			System.out.println("legacy:" + v.getLiteral().toString());
		}

		String annotation = OWL.getEntityLabel(OWL
				.individual("legacy:311OTHER_Q3"));
		System.out.println("annotation:" + annotation);

	}
}

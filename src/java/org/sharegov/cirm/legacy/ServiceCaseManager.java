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
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
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
						
			List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
			changes.addAll(createRemoveIndividualPropertyChanges(srType, "isDisabledCreate"));	
			changes.addAll(createIndividualLiteralRemoveAxioms(srType, "isDisabled"));			

			AddAxiom isDisabledCreateAddAxiom = createIndividualLiteralAddAxiom(srType, "isDisabledCreate", true);
			
			changes.add(isDisabledCreateAddAxiom);

			return Json.object().set("success", commit(Refs.defaultOntologyIRI.resolve(), userName, comment, changes));
		}
	}

	public Json enable(String srType, String userName, String comment) {
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();
			
			List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
			changes.addAll(createRemoveIndividualPropertyChanges(srType, "isDisabledCreate"));	
			changes.addAll(createRemoveIndividualPropertyChanges(srType, "isDisabled"));			

			AddAxiom isDisabledCreateAddAxiom = createIndividualLiteralAddAxiom(srType, "isDisabledCreate", false);
			
			changes.add(isDisabledCreateAddAxiom);

			return Json.object().set("success", commit(Refs.defaultOntologyIRI.resolve(), userName, comment, changes));
		}
	}

	public Json push() {
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();
			
			String ontologyIri = Refs.defaultOntologyIRI.resolve();

			return push(ontologyIri);
		}
	}
	
	public Json replaceObjectAnnotation(String individualID, String newAnnotationContent, String userName, String comment){
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();		 
			
			List<OWLOntologyChange> changes = createReplaceObjectAnnotationChanges (individualID, newAnnotationContent);			

			String ontologyIri = Refs.defaultOntologyIRI.resolve();	
			
			return Json.object().set("success", commit(ontologyIri, userName, comment, changes));
		}
	}
	
	public Json refreshOnto() {
		// String jenkingsEndpointFullDeploy = "https://api.miamidade.gov/jenkins/job/CIRM-ADMIN-TEST-CI-JOB-OPENCIRM/build?token=7ef54dc3a604a1514368e8707d8415";
		String jenkingsEndpointRefreshOntosOnly = "https://api.miamidade.gov/jenkins/job/CIRM-ADMIN-TEST-REFRESH-ONTOS/build?token=1a85a585ef7c424191c7c58ee3c4a97d556eec91";

		return GenUtils.httpPostWithBasicAuth(jenkingsEndpointRefreshOntosOnly, "cirm", "admin", "");
	}
	
	/*
	 * Generic Ontology handling functions, maybe better on other class/package.
	 */
	
	public List<OWLOntologyChange> createReplaceObjectAnnotationChanges (String individualID, String newAnnotationContent){
		OWLOntology O = OWL.ontology();
		//get the individual
		OWLEntity entity = OWL.dataFactory().getOWLNamedIndividual(OWL.fullIri(PREFIX + individualID));
		String existingLabel = OWL.getEntityLabel(entity);
		//create existing annotation
		OWLAnnotationAssertionAxiom toRemove = OWL.dataFactory().getOWLAnnotationAssertionAxiom(
				entity.getIRI(), OWL.dataFactory().getOWLAnnotation(OWL.annotationProperty("http://www.w3.org/2000/01/rdf-schema#label"), OWL.dataFactory().getOWLLiteral(existingLabel)));
		//create new annotation
		OWLAnnotationAssertionAxiom toAdd = OWL.dataFactory().getOWLAnnotationAssertionAxiom(
				entity.getIRI(), OWL.dataFactory().getOWLAnnotation(OWL.annotationProperty("http://www.w3.org/2000/01/rdf-schema#label"), OWL.dataFactory().getOWLLiteral(newAnnotationContent)));		
		
		RemoveAxiom removeAxiom = new RemoveAxiom(O, toRemove);			
		AddAxiom addAxiom = new AddAxiom(O, toAdd); 
		
		List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
		changes.add(removeAxiom);
		changes.add(addAxiom);

		return changes;			
	}
		
	private <Type> OWLLiteral createTypedLiteral (Type value){
		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory factory = manager.getOWLDataFactory();
		
		if (value instanceof Boolean) {
			return factory.getOWLLiteral((Boolean) value);
		} else if (value instanceof String){
			return factory.getOWLLiteral((String) value);
		} else if (value instanceof Integer){
			return factory.getOWLLiteral((Integer) value);
		} else if (value instanceof Float){
			return factory.getOWLLiteral((Float) value);
		} else if (value instanceof Double){
			return factory.getOWLLiteral((Double) value);
		} else {
			throw new RuntimeException("Invalid parameter tyoe: " + value.getClass().toString());
		}
	}

	private <Type> List<OWLOntologyChange> createReplaceIndividualLiteralPropertyChanges (String individualID, String propertyID, Type newValue) {
		List<OWLOntologyChange> changes = createRemoveIndividualPropertyChanges(individualID, propertyID);
		
		changes.add(createIndividualLiteralAddAxiom (individualID, propertyID, newValue));

		return changes;
	}
	
	private <Type> AddAxiom createIndividualLiteralAddAxiom (String individualID, String propertyID, Type newValue){
		OWLOntology O = OWL.ontology();
		String ontologyIri = Refs.defaultOntologyIRI.resolve();

		if (O == null) {
			throw new RuntimeException("Ontology not found: " + ontologyIri);
		}

		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory factory = manager.getOWLDataFactory();

		OWLNamedIndividual individual = factory.getOWLNamedIndividual(OWL.fullIri(PREFIX + individualID));
		OWLDataProperty property = factory.getOWLDataProperty(OWL.fullIri(PREFIX + propertyID));
		
		OWLLiteral newLiteralValue = createTypedLiteral(newValue);		 
		
		OWLDataPropertyAssertionAxiom addAssertion = factory.getOWLDataPropertyAssertionAxiom(property, individual, newLiteralValue);
		
		return new AddAxiom(O, addAssertion);
	}
	
	private List <RemoveAxiom> createIndividualLiteralRemoveAxioms (String individualID, String propertyID){
		OWLOntology O = OWL.ontology();
		String ontologyIri = Refs.defaultOntologyIRI.resolve();

		if (O == null) {
			throw new RuntimeException("Ontology not found: " + ontologyIri);
		}

		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory factory = manager.getOWLDataFactory();

		OWLNamedIndividual individual = factory.getOWLNamedIndividual(OWL.fullIri(PREFIX + individualID));
		OWLDataProperty property = factory.getOWLDataProperty(OWL.fullIri(PREFIX + propertyID));
		
		Set<OWLLiteral> propValues = OWL.reasoner().getDataPropertyValues(individual, property);
		
		List<RemoveAxiom> result = new ArrayList<RemoveAxiom>();
		
		if (propValues.isEmpty()){
			// Axioms not found 
			return result;
		}						
		
		for (OWLLiteral value : propValues) {
			OWLDataPropertyAssertionAxiom removeAssertion = factory.getOWLDataPropertyAssertionAxiom(property, individual, value);
			result.add(new RemoveAxiom(O, removeAssertion));
		}				
		
		return result;
	}
	
	private List<OWLOntologyChange> createRemoveIndividualPropertyChanges (String individualID, String propertyID){
		List<RemoveAxiom> axioms = createIndividualLiteralRemoveAxioms (individualID, propertyID);
		
		List<OWLOntologyChange> result = new ArrayList<OWLOntologyChange>();
		
		if (axioms.isEmpty()){
			return result;
		}
		
		for (RemoveAxiom axiom : axioms) {
			result.add(axiom);
		}
		
		return result;
	}
	
	private List <RemoveAxiom> createIndividualObjectPropertyRemoveAxioms (String individualID, String propertyID){
		OWLOntology O = OWL.ontology();
		String ontologyIri = Refs.defaultOntologyIRI.resolve();

		if (O == null) {
			throw new RuntimeException("Ontology not found: " + ontologyIri);
		}

		OWLOntologyManager manager = OWL.manager();
		OWLDataFactory factory = manager.getOWLDataFactory();

		OWLNamedIndividual individual = factory.getOWLNamedIndividual(OWL.fullIri(PREFIX + individualID));
		OWLObjectPropertyExpression property = factory.getOWLObjectProperty(OWL.fullIri(PREFIX + propertyID));
		
		Set <OWLNamedIndividual> propObjects = OWL.reasoner().getObjectPropertyValues(individual, property).getFlattened();
		
		List<RemoveAxiom> result = new ArrayList<RemoveAxiom>();
		
		if (propObjects.isEmpty()){
			// Axioms not found 
			return result;
		}						
		
		for (OWLNamedIndividual value : propObjects) {
			OWLObjectPropertyAssertionAxiom removeAssertion = factory.getOWLObjectPropertyAssertionAxiom(property, individual, value);
			result.add(new RemoveAxiom(O, removeAssertion));
		}				
		
		return result;
	}
	
	private List<OWLOntologyChange> createRemoveIndividualObjectPropertyChanges (String individualID, String propertyID){
		List<RemoveAxiom> axioms = createIndividualObjectPropertyRemoveAxioms (individualID, propertyID);
		
		List<OWLOntologyChange> result = new ArrayList<OWLOntologyChange>();
		
		if (axioms.isEmpty()){
			return result;
		}
		
		for (RemoveAxiom axiom : axioms) {
			result.add(axiom);
		}
		
		return result;
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

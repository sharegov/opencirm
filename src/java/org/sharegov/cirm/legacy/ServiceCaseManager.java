package org.sharegov.cirm.legacy;

import static org.sharegov.cirm.OWL.owlClass;
import static org.sharegov.cirm.OWL.reasoner;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import mjson.Json;

import org.jdom.IllegalDataException;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.sharegov.cirm.MetaOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.owl.OwlRepo;
import org.sharegov.cirm.rest.OWLIndividuals;
import org.sharegov.cirm.rest.OntoAdmin;
import org.sharegov.cirm.utils.GenUtils;

/*
 * @author Sabbas, Hilpold, Chirino
 * 
 * 
 */

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
	
	private String getParentAgencyName (Json p){
		if (p.has("hasParentAgency"))  {
			String parentIri;
			if (p.at("hasParentAgency").isObject()){
				if (p.at("hasParentAgency").has("iri")){
					parentIri = (p.at("hasParentAgency").at("iri").asString());
				} else throw new IllegalDataException("Cannot find IRI property for Individual: " + p.asString());
			} else parentIri = p.at("hasParentAgency").asString();
			
			OWLNamedIndividual ind = OWL.individual(parentIri);
			
			Json np = getSerializedIndividual(ind.getIRI().getFragment(), ind.getIRI().getScheme());
			
			return getParentAgencyName (np);
		}
		
		return p.at("Name").asString();
	}
	
	private String findJusrisdiction (Json srType){
		if (srType.has("providedBy")) return getParentAgencyName(srType.at("providedBy"));
		
		return "";
	}
	
	private Json resolveDepartment (Json p){	
		try {		
			if (p.has("hasParentAgency")) p = p.at("hasParentAgency");
			else 
				if (p.has("Department")) p = p.at("Department");
				else throw new IllegalDataException("Division: " + p.at("iri").asString() + " have no Parent Agency or Department assigned.");
			
			
			String iri;
			if (p.isObject()){
				if (p.has("iri")){
					iri = (p.at("iri").asString());
				} else throw new IllegalDataException("Cannot find IRI property for Individual: " + p.asString());
			} else iri = p.at("iri").asString();
			
	
			OWLNamedIndividual ind = OWL.individual(iri);
			
			Json np = getSerializedIndividual(ind.getIRI().getFragment(), ind.getIRI().getScheme());
			
			if (np.has("type") && np.at("type").asString().toLowerCase().compareTo("division_county") != 0){
				return Json.object().set("Name", np.at("Name").asString()).set("Type", np.at("type").asString());
			} else {
				return resolveDepartment(np);
			}
		}catch (Exception e) {
			System.out.println("Error while trying to resolve Department for " + p.at("iri").asString());
			e.printStackTrace();
		}
		
		return Json.object();
		
	}
	
	private Json resolveDepartmentDivision (Json p){
		Json result = Json.object();
		
		try {		
			if (p.has("iri")){
				OWLNamedIndividual ind = OWL.individual(p.at("iri").asString());
				
				Json np = getSerializedIndividual(ind.getIRI().getFragment(), ind.getIRI().getStart()); 
				
				if (np.has("type") && np.at("type").asString().toLowerCase().compareTo("division_county") == 0){
					result.set("division",  Json.object().set("Name", np.at("Name").asString()).set("Division_Code", np.at("Division_Code").asString()));
					result.set("department", resolveDepartment (np));					
				} else {
					result.set("division", Json.object().set("Name", "N/A").set("Division_Code", "N/A"));
					result.set("department", Json.object().set("Name", np.has("name") ? np.at("Name").asString(): "N/A").set("Type", np.has("type") ? np.at("type").asString(): "N/A"));
				}
			} else throw new IllegalDataException("Cannot find IRI property for Individual: " + p.asString());
		} catch (Exception e) {
			System.out.println("Error while trying to resolve Department/Division for " + p.at("iri").asString());
			e.printStackTrace();
		}
		
		return result;
	}
	
	private Json findDepartmentDivision (Json srType){
		Json result = Json.object();
		
		if (srType.has("providedBy")) return resolveDepartmentDivision (srType.at("providedBy"));		
		
		return result;
	}
	
	private Json getRequiredData (OWLNamedIndividual individual){
		Json result = Json.object().set("iri", individual.getIRI().toString())
								   .set("code", individual.getIRI().getFragment())
								   .set("label", OWL.getEntityLabel(individual))
								   .set("disabled", isSrDisabledOrDisabledCreate(individual));
		
		Json jIndividual = getMetaIndividual(individual.getIRI().getFragment());
		
		String jurisdiction;		
		if (jIndividual.has("hasJurisdictionDescription")){
			jurisdiction = jIndividual.at("hasJurisdictionDescription").asString();
		} else {
			jurisdiction = findJusrisdiction(jIndividual);
			if (jurisdiction == null || jurisdiction.isEmpty()) throw new IllegalDataException("Individual legacy:" +  individual.getIRI().getFragment() + " have no jurisdiction associated.");
			
		}		
		result.set("jurisdiction", jIndividual.at("hasJurisdictionDescription").asString());
		
		Json depdiv = findDepartmentDivision(jIndividual);
		
		if (!depdiv.has("department")) throw new IllegalDataException("Individual legacy:" +  individual.getIRI().getFragment() + " have no provider/owner associated.");
		if (!depdiv.has("division")) throw new IllegalDataException("Cannot resolve division for Individual legacy:" +  individual.getIRI().getFragment());
		
		result.with(depdiv);
		
		return result;
   }

	public Json getAll() {
		Set<OWLNamedIndividual> S = getAllIndividuals();
		Json A = Json.array();
		for (OWLNamedIndividual ind : S) {
			A = getRequiredData(ind);
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
			boolean shouldAddServiceCase = (!isGetEnabled && isSrDisabledOrDisabledCreate) || (isGetEnabled && !isSrDisabledOrDisabledCreate);
			if (shouldAddServiceCase) {
				A = getRequiredData(ind);
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
	private boolean isSrDisabledOrDisabledCreate(OWLNamedIndividual srTypeIndividual) {
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
			changes.addAll(MetaOntology.getRemoveIndividualPropertyChanges(srType, "isDisabledCreate"));	
			changes.addAll(MetaOntology.getRemoveIndividualPropertyChanges(srType, "isDisabled"));			

			AddAxiom isDisabledCreateAddAxiom = MetaOntology.getIndividualLiteralAddAxiom(srType, "isDisabledCreate", true);
			
			changes.add(isDisabledCreateAddAxiom);

			return Json.object().set("success", commit(Refs.defaultOntologyIRI.resolve(), userName, comment, changes));
		}
	}

	public Json enable(String srType, String userName, String comment) {
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();
			
			List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
			changes.addAll(MetaOntology.getRemoveIndividualPropertyChanges(srType, "isDisabledCreate"));	
			changes.addAll(MetaOntology.getRemoveIndividualPropertyChanges(srType, "isDisabled"));			

			AddAxiom isDisabledCreateAddAxiom = MetaOntology.getIndividualLiteralAddAxiom(srType, "isDisabledCreate", false);
			
			changes.add(isDisabledCreateAddAxiom);

			return Json.object().set("success", commit(Refs.defaultOntologyIRI.resolve(), userName, comment, changes));
		}
	}
	
	private Json getSerializedIndividual (String individualID, String ontologyID){
		try {
	//		if (ontologyID.toLowerCase().contains("ontology")) ontologyID = "mdc";
	//		else
	//			if (ontologyID.toLowerCase().contains("legacy")) ontologyID = "legacy";
	//			else throw new IllegalDataException("Connot find ontology prefix for: " + ontologyID + "#" + individualID);
			
			if (ontologyID.toLowerCase().contains("legacy")) ontologyID = "legacy";
			else ontologyID = "mdc";
			
			OWLIndividuals q = new OWLIndividuals();
			Json S = q.doInternalQuery("{" + ontologyID + ":" + individualID + "}");
			
			for (Json ind : S.asJsonList()){
				return ind;
			}
		} catch (Exception e) {
			System.out.println("Error while querying the Ontology for " + ontologyID + ":" + individualID);
			e.printStackTrace();		
		}
		
		return Json.object();
	}
	
	public Json getMetaIndividual (String individualID){
		return getSerializedIndividual(individualID, "legacy");						
	}
	
	public Json getServiceCaseAlert (String srType){
		
		Json sr = getMetaIndividual(srType);		
		
		if (sr.has("hasServiceCaseAlert")){			
			return sr.at("hasServiceCaseAlert");
		} 
	
		return null;
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
			
			List<OWLOntologyChange> changes = MetaOntology.getReplaceObjectAnnotationChanges(individualID, newAnnotationContent);			

			String ontologyIri = Refs.defaultOntologyIRI.resolve();	
			
			return Json.object().set("success", commit(ontologyIri, userName, comment, changes));
		}
	}
	
	public Json addIndividualObjectPropertyToIndividual(String individualID, String propertyID, Json data, String userName, String comment){
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();		 
			
			List<OWLOntologyChange> changes = MetaOntology.getAddIndividualObjectFromJsonChanges(individualID, propertyID, data);		

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
	 * Test by Syed
	 * 
	 */
		
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

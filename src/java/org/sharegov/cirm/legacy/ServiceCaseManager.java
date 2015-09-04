package org.sharegov.cirm.legacy;

import static org.sharegov.cirm.OWL.owlClass;
import static org.sharegov.cirm.OWL.reasoner;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import mjson.Json;

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
import org.sharegov.cirm.utils.ServiceCaseManagerCache;
import org.sharegov.cirm.utils.ServiceCaseManagerCacheInitializer;

import com.hp.hpl.jena.reasoner.IllegalParameterException;

/*
 * @author Sabbas, Hilpold, Chirino
 * 
 * 
 */

public class ServiceCaseManager extends OntoAdmin {		

	private static final String PREFIX = "legacy:";
	private static ServiceCaseManager instance = null; 

	private ServiceCaseManager() {
		ServiceCaseManagerCacheInitializer.startCaching(this);
	}
	
	public static ServiceCaseManager getInstance(){
		if (instance == null){
			instance = new ServiceCaseManager ();
		}
		return instance;
	}
	
	private OwlRepo getRepo() {
		return Refs.owlRepo.resolve();
	}
	
	private synchronized void clearCache(){
		ServiceCaseManagerCacheInitializer.forceRestartCaching(this,true);
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
				} else throw new IllegalParameterException("Cannot find IRI property for Individual: " + p.asString());
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
		if (p.has("hasParentAgency")) p = p.at("hasParentAgency");
		else 
			if (p.has("Department")) p = p.at("Department");
			else throw new IllegalParameterException("Division: " + p.at("iri").asString() + " have no Parent Agency or Department assigned.");
		
		
		String iri;
		if (p.isObject()){
			if (p.has("iri")){
				iri = (p.at("iri").asString());
			} else throw new IllegalParameterException("Cannot find IRI property for Individual: " + p.asString());
		} else iri = p.asString();
		

		OWLNamedIndividual ind = OWL.individual(iri);
		
		Json np = (p.isObject()&&p.has("type")&&p.has("Name")) ? p : getSerializedIndividual(ind.getIRI().getFragment(), ind.getIRI().getScheme());
		
		if (np.has("type") && np.at("type").asString().toLowerCase().compareTo("division_county") != 0){
			return Json.object().set("Name", np.at("Name").asString()).set("Type", np.at("type").asString());
		} else {
			return resolveDepartment(np);
		}
		
	}
	
	private Json resolveDepartmentDivision (Json p){
		Json result = Json.object();
		
		if (p.has("iri")){
			OWLNamedIndividual ind = OWL.individual(p.at("iri").asString());
			
			Json np = (p.has("type")&&p.has("Name")) ? p : getSerializedIndividual(ind.getIRI().getFragment(), ind.getIRI().getStart()); 
			
			if (np.has("type") && np.at("type").asString().toLowerCase().compareTo("division_county") == 0){
				result.set("division",  Json.object().set("Name", np.at("Name").asString()).set("Division_Code", np.at("Division_Code").asString()));
				
				if (!np.has("hasParentAgency")&&!np.has("Department")) np = getSerializedIndividual(ind.getIRI().getFragment(), ind.getIRI().getStart());
				
				result.set("department", resolveDepartment (np));					
			} else {
				result.set("division", Json.object().set("Name", Json.nil()).set("Division_Code", Json.nil()));
				result.set("department", Json.object().set("Name", np.has("Name") ? np.at("Name").asString(): Json.nil()).set("Type", np.has("type") ? np.at("type").asString(): Json.nil()));
			}
		} else throw new IllegalParameterException("Cannot find IRI property for Individual: " + p.asString());
		
		
		return result;
	}
	
	private Json findDepartmentDivision (Json srType){
		if (srType.has("providedBy")) return resolveDepartmentDivision (srType.at("providedBy"));
		else throw new IllegalParameterException("Cannot find providedBy property for SR type: " +srType.at("iri").asString());
	}
	
	private Json getRequiredData (OWLNamedIndividual individual){
		Json el = ServiceCaseManagerCache.getInstance().getElement(individual.getIRI().getFragment() + "-Admin");
		
		if (el != Json.nil()) return el;
		
		Json result = Json.object().set("iri", individual.getIRI().toString())
								   .set("code", individual.getIRI().getFragment())
								   .set("label", OWL.getEntityLabel(individual))
								   .set("disabled", isSrDisabledOrDisabledCreate(individual));
		
		try {		
			Json jIndividual = getMetaIndividual(individual.getIRI().getFragment());
			
			String jurisdiction;		
			if (jIndividual.has("hasJurisdictionDescription")){
				jurisdiction = jIndividual.at("hasJurisdictionDescription").asString();
			} else {
				jurisdiction = findJusrisdiction(jIndividual);
				if (jurisdiction == null || jurisdiction.isEmpty()) throw new IllegalParameterException("Individual legacy:" +  individual.getIRI().getFragment() + " have no jurisdiction associated.");
				
			}		
			result.set("jurisdiction", jIndividual.at("hasJurisdictionDescription").asString());
			
			Json depdiv = findDepartmentDivision(jIndividual);
			
			if (!depdiv.has("department")) throw new IllegalParameterException("Individual legacy:" +  individual.getIRI().getFragment() + " have no provider/owner associated.");
			if (!depdiv.has("division")) throw new IllegalParameterException("Cannot resolve division for Individual legacy:" +  individual.getIRI().getFragment());
			
			result.with(depdiv);
		} catch (Exception e) {
			System.out.println("Error while trying to resolve data for legacy:" + individual.getIRI().getFragment());
			
			if (!result.has("jurisdiction")) result.set("jurisdiction", Json.nil());
			if (!result.has("department")) result.set("department", Json.nil());
			if (!result.has("division")) result.set("division", Json.nil());
		}
		
		ServiceCaseManagerCache.getInstance().setElement(individual.getIRI().getFragment() + "-Admin", result);
		
		return result;
   }
	
	public synchronized Json getSRTypes (boolean isAll, boolean isGetEnabled){
		if (isAll) return getAll();
		else return getServiceCasesByStatus (isGetEnabled);
	}

	private Json getAll() {
		Set<OWLNamedIndividual> S = getAllIndividuals();
		Json A = Json.array();
		for (OWLNamedIndividual ind : S) {			
			A.add(getRequiredData(ind));
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
				A.add(getRequiredData(ind));
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
			
			boolean r = commit(userName, comment, changes);
			
			if (r) clearCache();
			
			return Json.object().set("success", r);
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
			
			boolean r = commit(userName, comment, changes);
			
			if (r) clearCache();
			
			return Json.object().set("success", r);
		}
	}
	
	private Json getSerializedIndividual (String individualID, String ontologyID){
		try {			
			if (ontologyID.toLowerCase().contains("legacy")) ontologyID = "legacy";
			else ontologyID = "mdc";
			
			String cacheKey = ontologyID + ":" + individualID;
			
			Json el = ServiceCaseManagerCache.getInstance().getElement(cacheKey);
			
			if (el != Json.nil()) return el;
					
//			OWLNamedIndividual ind = individual(individualID);
//			Json jInd = OWL.toJSON(ontology(), ind);
			OWLIndividuals q = new OWLIndividuals();
			
			Json S = q.doInternalQuery("{" + cacheKey + "}");
			
			for (Json ind: S.asJsonList()){
				ServiceCaseManagerCache.getInstance().setElement(cacheKey, ind);
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
			OWLNamedIndividual ind = OWL.individual(sr.at("hasServiceCaseAlert").at("iri").asString());
			sr.at("hasServiceCaseAlert").set("iri",ind.getIRI().getFragment());
			return sr.at("hasServiceCaseAlert");
		} 
	
		return Json.nil();
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
			
			boolean r = commit(userName, comment, changes);
			
			if (r) clearCache();
			
			return Json.object().set("success", r);
		}
	}
	
	public Json addIndividualObjectPropertyToIndividual(String individualID, String propertyID, Json data, String userName, String comment){
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();		 
			
			List<OWLOntologyChange> changes = MetaOntology.getAddIndividualObjectFromJsonChanges(individualID, propertyID, data);	
			
			boolean r = commit(userName, comment, changes);
			
			if (r) clearCache();
			
			return Json.object().set("success", r);
		}
	}
	
	public Json refreshOnto() {
		// String jenkingsEndpointFullDeploy = "https://api.miamidade.gov/jenkins/job/CIRM-ADMIN-TEST-CI-JOB-OPENCIRM/build?token=7ef54dc3a604a1514368e8707d8415";
		String jenkingsEndpointRefreshOntosOnly = "https://api.miamidade.gov/jenkins/job/CIRM-ADMIN-TEST-REFRESH-ONTOS/build?token=1a85a585ef7c424191c7c58ee3c4a97d556eec91";

		return GenUtils.httpPostWithBasicAuth(jenkingsEndpointRefreshOntosOnly, "cirm", "admin", "");
	}
	
	public Json addNewAlertServiceCase (String individualID, Json data, String userName, String comment){
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();			
			
			String propertyID = "hasServiceCaseAlert";
			
			if(data.at("iri").isNull())
			{
				
				Date now = new Date(); 
				
				String newIri = individualID + "_" + Long.toString(now.getTime());
				data.set("iri", newIri); 
			}
			
			
			Json oldAlert = getServiceCaseAlert(individualID);
			
			List<OWLOntologyChange> changes;
			
			if (oldAlert != Json.nil()){
				OWLNamedIndividual ind = OWL.individual(oldAlert.at("iri").asString());
				oldAlert.set("iri", ind.getIRI().getFragment());
				changes = MetaOntology.getAddReplaceIndividualObjectFromJsonChanges(individualID, propertyID, data, oldAlert);
			} else {
				changes = MetaOntology.getAddIndividualObjectFromJsonChanges(individualID, propertyID, data);
			}
			
			boolean r = commit(userName, comment, changes);
			
			if (r) clearCache();
			
			return Json.object().set("success", r);
				
		}
	}
	
	public Json deleteAlertServiceCase (String individualID, String userName, String comment){
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();						
			
			Json oldAlert = getServiceCaseAlert(individualID);
			
			List<OWLOntologyChange> changes;
			
			if (oldAlert != Json.nil()){
				OWLNamedIndividual ind = OWL.individual(oldAlert.at("iri").asString());
				changes = MetaOntology.getRemoveAllPropertiesIndividualChanges(ind);
			} else throw new IllegalParameterException("No alert for individual " + PREFIX + individualID);
			
			boolean r = commit(userName, comment, changes);
			
			if (r) clearCache();
			
			return Json.object().set("success", r);
				
		}
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

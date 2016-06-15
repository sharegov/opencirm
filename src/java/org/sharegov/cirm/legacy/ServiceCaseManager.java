package org.sharegov.cirm.legacy;

import static org.sharegov.cirm.OWL.fullIri;
import static org.sharegov.cirm.OWL.owlClass;
import static org.sharegov.cirm.OWL.reasoner;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.sharegov.cirm.MetaOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.owl.OwlRepo;
import org.sharegov.cirm.rest.OWLIndividuals;
import org.sharegov.cirm.rest.OntoAdmin;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import mjson.Json;

/**
 * Handles the User Cases for CIRM Admin Interface
 * 
 * @author chirino, hilpold, dwong, sabbas
 */
public class ServiceCaseManager extends OntoAdmin {		

	private static final String PREFIX = "legacy:";
	private static String jenkingsEndpointRefreshOntosOnlyTest = "https://api.miamidade.gov/jenkins/job/CIRM-ADMIN-TEST-REFRESH-ONTOS/build?token=1a85a585ef7c424191c7c58ee3c4a97d556eec91";
	private static String jenkingsEndpointRefreshOntosOnlyProduction = "https://api.miamidade.gov/jenkins/job/CIRM-ADMIN-PRODUCTION-REFRESH-ONTOS/build?token=1a85a585ef7c424191c7c58ee3c4a97d556ffc91";
	private static ServiceCaseManager instance = null; 
	private Map<String, Json> cache;
	private Map<String, Long> changes;
	
	/**
	 * private to defeat multiple instantiation
	 * 
	 */
	private ServiceCaseManager() {
		cache = new ConcurrentHashMap<String, Json>();
		changes = new ConcurrentHashMap<String, Long>();
		
		ThreadLocalStopwatch.startTop("Started Service Case Admin Cache.");
		getAll();
		//getOne(OWL.individual("legacy:TM100"));
		ThreadLocalStopwatch.now("End Service Case Admin Cache.");
	}

	/**
	 * Singleton instance getter. Synchronized to defeat multiple instantiation when instance == null
	 *  
	 * @return the same unique instance of the class 
	 */
	public synchronized static ServiceCaseManager getInstance(){
		if (instance == null){
			instance = new ServiceCaseManager ();
		}
		return instance;
	}
	
	/**
	 * Getter for the OWL repository
	 * 	
	 * @return
	 */
	private OwlRepo getRepo() {
		return Refs.owlRepo.resolve();
	}
	
	/**
	 * Takes Just the Code of what was changed and saves the date on the list
	 * 
	 */
	private void registerChange (String srType){
		changes.put(PREFIX + srType, System.currentTimeMillis());
	}
	
	/**
	 * Determines if a local change for the given individual exists that was committed after the given date.
	 * TODO currently only works for SR type individuals
	 * @param individualID prefixed IRI (e.g. legacy:311DUMP)
	 * @param date timestamp in milliseconds
	 * @return false, if no change after date or no change found since server startup.
	 */
	public boolean isInvididualModifiedAfter (String individualID, long date){
		Long lastChanged = changes.get(individualID);
		
		if (lastChanged != null) {
			return lastChanged > date;
		} else {		
			return false;
		}
	}
	
	/**
	 * Removes object defined by aKey from the cache
	 * 
	 * @param aKey null not allowed
	 */
	private synchronized void clearCache (String aKey){
		cache.remove(aKey);
		cache.remove(PREFIX + aKey);	
		
		MetaOntology.clearCacheAndSynchronizeReasoner();
	}
	
	/**
	 * Removes a list of objects identified by their keys from the cache	 * 
	 * 
	 * @param keys a list of keys to remove from the cache.
	 */
	
	private synchronized void clearCache(List<String> keys){
		for (String key: keys){
			cache.remove(key);
			cache.remove(PREFIX + key);
		}
		MetaOntology.clearCacheAndSynchronizeReasoner();
	}
	
	/**
	 * 
	 * @return a formated list of enabled Service Case Types 
	 */

	public Json getEnabled() {
		return getServiceCasesByStatus(true);
	}
	
	/**
	 * 
	 * @return a formated list of disabled Service Case Types
	 */

	public Json getDisabled() {
		return getServiceCasesByStatus(false);
	}
	
	/**
	 * Search a parent Agency for the individual p within the ontology
	 * 
	 * @param p a serialized individual
	 * @return the parent agency name as string 
	 */
	
	private String getParentAgencyName (Json p){
		if (p.has("hasParentAgency"))  {
			String parentIri;
			if (p.at("hasParentAgency").isObject()){
				if (p.at("hasParentAgency").has("iri")){
					parentIri = (p.at("hasParentAgency").at("iri").asString());
				} else throw new IllegalArgumentException("Cannot find IRI property for Individual: " + p.asString());
			} else parentIri = p.at("hasParentAgency").asString();
			
			OWLNamedIndividual ind = OWL.individual(parentIri);
			
			Json np = getSerializedIndividual(ind.getIRI().getFragment(), ind.getIRI().getScheme());
			
			return getParentAgencyName (np);
		}
		
		return p.has("Name")?p.at("Name").asString():p.at("label").asString();
	}
	
	/** 
	 * 
	 * @param srType a serialized individual
	 * @return the name of the jurisdiction to whom the individual belongs
	 */
	
	private String findJusrisdiction (Json srType){
		if (srType.has("providedBy")) return getParentAgencyName(srType.at("providedBy"));
		
		return "";
	}
	
	/**
	 * Recursive search of the department/office to whom the individual p belongs on the ontology  
	 * 
	 * @param p a serialized individual
	 * @return Json representation of the attributes of the department/office 
	 */
	
	private Json resolveDepartment (Json p){			
		if (p.has("hasParentAgency")) p = p.at("hasParentAgency");
		else 
			if (p.has("Department")) p = p.at("Department");
			else throw new IllegalArgumentException("Division: " + p.at("iri").asString() + " have no Parent Agency or Department assigned.");
		
		
		String iri;
		if (p.isObject()){
			if (p.has("iri")){
				iri = (p.at("iri").asString());
			} else throw new IllegalArgumentException("Cannot find IRI property for Individual: " + p.asString());
		} else iri = p.asString();
		

		OWLNamedIndividual ind = OWL.individual(iri);
		
		Json np = (p.isObject() && p.has("type") && (p.has("Name") || p.has("label"))) ? p : getSerializedIndividual(ind.getIRI().getFragment(), ind.getIRI().getScheme());
		
		if (np.has("type") && np.at("type").asString().toLowerCase().compareTo("division_county") != 0){
			return Json.object().set("Name", np.has("Name")?np.at("Name").asString():np.at("label").asString()).set("Type", np.at("type").asString());
		} else {
			return resolveDepartment(np);
		}
		
	}
	
	private Json getDepartmentDivisionSerializedIndividual (Json p){
		OWLNamedIndividual ind;
		if (p.isObject()){
			if (p.has("iri")){
				ind = OWL.individual(p.at("iri").asString());	
			} else {
				throw new IllegalArgumentException("Cannot find IRI property for Individual: " + p.asString());
			}			
		} else {
			try {
				ind = OWL.individual(p.asString());				
			} catch (Exception e) {
				throw new IllegalArgumentException("Cannot build Individual: " + p.asString());				
			}
		}
		
		 return getSerializedIndividual(ind.getIRI().getFragment(), ind.getIRI().getStart()); 
	}
	
	/**
	 * Returns the Division and Department to whom the individual belongs
	 * 
	 * @param p a serialized individual
	 * @return a Json representation of the attributes of the department and division
	 */
	
	private Json resolveDepartmentDivision (Json p){
		Json result = Json.object();
		Json np = (p.isObject() && p.has("type") && (p.has("Name")||p.has("label"))&&p.has("hasParentAgency")&&p.has("Department")) ? p: getDepartmentDivisionSerializedIndividual (p);
		
		if (np.has("type") && np.at("type").asString().toLowerCase().compareTo("division_county") == 0){
			result.set("division",  Json.object().set("Name", np.has("Name")?np.at("Name").asString():np.at("label").asString()).set("Division_Code", np.at("Division_Code").asString()));
			
			result.set("department", resolveDepartment (np));					
		} else {
			result.set("division", Json.object().set("Name", Json.nil()).set("Division_Code", Json.nil()));
			result.set("department", Json.object().set("Name", np.has("Name") ? np.at("Name").asString(): np.at("label").asString()).set("Type", np.has("type") ? np.at("type").asString(): Json.nil()));
		}		
		
		return result;
	}
	
	/**
	 * Entry point for the search of the department/division  
	 * 
	 * @param srType
	 * @return a Json representation of the attributes of the department and division
	 */
	
	private Json findDepartmentDivision (Json srType){
		if (srType.has("providedBy")) return resolveDepartmentDivision (srType.at("providedBy"));
		else throw new IllegalArgumentException("Cannot find providedBy property for SR type: " +srType.at("iri").asString());
	}
	
	/**
	 * 
	 * @param srType
	 * @return a Json structure that contains the contracted SR Type data for the user interface  
	 */
	
	private Json getServiceCaseFormated (String srType){
		OWLNamedIndividual ind = OWL.individual(PREFIX + srType);
		
		return getRequiredData(ind);
	}
	
	/**
	 * retrieves the contracted information of a Service Case Type from the ontology
	 * 
	 * @param individual assumes is type Service Case
	 * @return Json representation of the contracted data required for the user interface
	 */
	
	private Json getRequiredData (OWLNamedIndividual individual){
		Json el = cache.get(individual.getIRI().getFragment());
		
		if (el != null && !el.isNull()) return el;
		
		String iri = individual.getIRI().toString();
		
		Json result = Json.object().set("iri", MetaOntology.getOntologyFromUri(iri) + ":" + individual.getIRI().getFragment())
//								   .set("code", individual.getIRI().getFragment())
								   .set("label", OWL.getEntityLabel(individual))
								   .set("disabled", isSrDisabledOrDisabledCreate(individual));
		
		try {		
			Json jIndividual = getMetaIndividual(individual.getIRI().getFragment());
			
			String jurisdiction;		
			if (jIndividual.has("hasJurisdictionDescription")){
				jurisdiction = jIndividual.at("hasJurisdictionDescription").asString();
			} else {
				jurisdiction = findJusrisdiction(jIndividual);
				if (jurisdiction == null || jurisdiction.isEmpty()) throw new IllegalArgumentException("Individual legacy:" +  individual.getIRI().getFragment() + " have no jurisdiction associated.");
				
			}		
			result.set("jurisdiction", jurisdiction);
			Json depdiv = findDepartmentDivision(jIndividual);
			if (!depdiv.has("department")) throw new IllegalArgumentException("Individual legacy:" +  individual.getIRI().getFragment() + " have no provider/owner associated.");
			if (!depdiv.has("division")) throw new IllegalArgumentException("Cannot resolve division for Individual legacy:" +  individual.getIRI().getFragment());
			
			result.with(depdiv);
		} catch (Exception e) {
			System.out.println("Error while trying to resolve data for legacy:" + individual.getIRI().getFragment());
			if (e.getMessage() != null ){
				System.out.println(e.getMessage());
			} else {
				e.printStackTrace();
			}
			
			if (!result.has("jurisdiction")) result.set("jurisdiction", Json.nil());
			if (!result.has("department")) result.set("department", Json.nil());
			if (!result.has("division")) result.set("division", Json.nil());
		}
		
		cache.put(individual.getIRI().getFragment(), result);
		
		return result;
		
	}	
	
	/**
	 * 
	 * @return a list of Service Case Types that contains the required data for the user interface
	 */
	
	public Json getAll() {
		Set<OWLNamedIndividual> S = getAllIndividuals();
		Json A = Json.array();
		for (OWLNamedIndividual ind : S) {			
			A.add(getOne(ind));
		}

		return A;
	}
	
	
	/**
	 * 
	 * @return a Service Case Type that contains the required data for the user interface
	 */
	
	public Json getOne(OWLNamedIndividual individual){
		return getRequiredData(individual);
	}
	
	/**
	 * Removes all objects on cache except the service cases 
	 * 
	 * @return 
	 */
	
	public void clearAllCachedButServiceCase() {
		Set<OWLNamedIndividual> S = getAllIndividuals();
		Map <String, Json> tmpcache = new ConcurrentHashMap<String, Json>();
		for (OWLNamedIndividual ind : S) {			
			Json el = cache.get(ind.getIRI().getFragment());
			
			if (el != null && !el.isNull()) tmpcache.put(ind.getIRI().getFragment(), el);
			else {
				System.out.println("Named Individual not in cache: " + ind.getIRI().getFragment());
				System.out.println("Resolving: " + ind.getIRI().getFragment());
				el = getRequiredData(ind);
				System.out.println("Done resolving: " + ind.getIRI().getFragment());
				
				if (el != null && !el.isNull()) tmpcache.put(ind.getIRI().getFragment(), el);
				else System.out.println("Error: Cannot resolve individual: " + ind.getIRI().getFragment());
			}
		}
		
		cache.clear();
		cache.putAll(tmpcache);
	}
	
	/**
	 * 
	 * @return a list of individuals that belong to the class ServiceCase
	 */

	private Set<OWLNamedIndividual> getAllIndividuals() {
		OWLReasoner reasoner = reasoner();
		OWLClass serviceCase = owlClass(PREFIX + "ServiceCase");
		// TODO: Permission check
		// permissionCheck(serviceCase)
		return reasoner.getInstances(serviceCase, false).getFlattened();
	}
	
	/**
	 *  
	 * @param isGetEnabled describes whether the function returns all enabled or all disabled SRs
	 * if isGetEnabled == true, returns all enabled
	 * if isGetEnabled == false, returns all disabled
	 * 
	 * @return  a list of enabled/disabled service cases
	 */
	
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
	
	private String getIndividualLabel (String srType){
		OWLNamedIndividual individual = OWL.individual(fullIri(PREFIX + srType));
		return OWL.getEntityLabel(individual);
	}
	
	/**
	 * Disables a Service Case Type
	 * 
	 * @param srType individual identifier 
	 * @param userName who commits the action
	 * @return commit success true or false
	 */

	public Json disable(String srType, String userName) {

		srType = MetaOntology.getIndividualIdentifier(srType);
		
		OwlRepo repo = getRepo();

		synchronized (repo) {
			repo.ensurePeerStarted();
						
			List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
			changes.addAll(MetaOntology.getRemoveIndividualPropertyChanges(srType, "isDisabledCreate"));	
			changes.addAll(MetaOntology.getRemoveIndividualPropertyChanges(srType, "isDisabled"));			

			AddAxiom isDisabledCreateAddAxiom = MetaOntology.getIndividualLiteralAddAxiom(srType, "isDisabledCreate", true);
			
			changes.add(isDisabledCreateAddAxiom);
			
			String comment = "Disable Service Request "+PREFIX+srType + " - " + getIndividualLabel(srType);
			
			boolean r = commit(userName, comment, changes);
			
			if (r) {
				registerChange(srType);
				clearCache(srType);
				return getServiceCaseFormated(srType);
			} else throw new IllegalArgumentException("Unable to disable Service Case Type "+ PREFIX + srType);
		}
	}
	
	/**
	 * Enables a Service Case Type
	 * 
	 * @param srType individual identifier 
	 * @param userName who commits the action
	 * @return commit success true or false
	 */
	

	public Json enable(String srType, String userName) {

		srType = MetaOntology.getIndividualIdentifier(srType);
		
		OwlRepo repo = getRepo();
		
		synchronized (repo) {
			repo.ensurePeerStarted();
			
			List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
			changes.addAll(MetaOntology.getRemoveIndividualPropertyChanges(srType, "isDisabledCreate"));	
			changes.addAll(MetaOntology.getRemoveIndividualPropertyChanges(srType, "isDisabled"));			

			AddAxiom isDisabledCreateAddAxiom = MetaOntology.getIndividualLiteralAddAxiom(srType, "isDisabledCreate", false);
			
			changes.add(isDisabledCreateAddAxiom);
			
			String comment = "Enable Service Request "+PREFIX+srType + " - " + getIndividualLabel(srType);
			
			boolean r = commit(userName, comment, changes);
			
			if (r) {
				registerChange(srType);
				clearCache(srType);
				return getServiceCaseFormated(srType);
			} else throw new IllegalArgumentException("Unable to disable Service Case Type "+ PREFIX + srType);
		}
	}
	
	/**
	 * Queries the reasoner for a serialized individual
	 * 
	 * @param individualID identifier of the individual
	 * @param ontologyID ontology prefix
	 * @return a Json representation of the individual
	 */
	
	private Json getSerializedIndividual (String individualID, String ontologyID){
		try {			
			if (ontologyID.toLowerCase().contains("legacy")) ontologyID = "legacy";
			else ontologyID = "mdc";
			
			String cacheKey = ontologyID + ":" + individualID;
			
			Json el = cache.get(cacheKey);
			
			if (el != null && !el.isNull()) return el;
					
//			OWLNamedIndividual ind = individual(individualID);
//			Json jInd = OWL.toJSON(ontology(), ind);
			OWLIndividuals q = new OWLIndividuals();
			
			Json S = q.doInternalQuery("{" + cacheKey + "}");
			for (Json ind: S.asJsonList()){
				cache.put(cacheKey, ind);
				return ind;
			}
			
		} catch (Exception e) {
			System.out.println("Error while querying the Ontology for " + ontologyID + ":" + individualID);
			e.printStackTrace();		
		}
		
		return Json.object();
	}
	
	/**
	 * getter for serialized individuals from the legacy ontology
	 * 
	 * @param individualID individual identifier
	 * @return a Json representation of the individual
	 */
	
	public Json getMetaIndividual (String individualID){
		return getSerializedIndividual(individualID, "legacy");						
	}
	
	public Json getMetaIndividualFormatedIri (String individualID){
		Json result = getSerializedIndividual(individualID, "legacy");
		
		result.set("iri", PREFIX + MetaOntology.getIdFromUri(result.at("iri").asString()));
		
		return result;
	}
	
	public Json getServiceCaseAlert (String srType){		

		srType = MetaOntology.getIndividualIdentifier(srType);
		
		Json sr = getMetaIndividual(srType);		
		
		if (sr.has("hasServiceCaseAlert") && sr.at("hasServiceCaseAlert").isObject()){
			String iri = sr.at("hasServiceCaseAlert").at("iri").asString();
			OWLNamedIndividual ind = OWL.individual(iri);
			sr.at("hasServiceCaseAlert").set("iri", MetaOntology.getOntologyFromUri(ind.getIRI().toString()) + ":" + ind.getIRI().getFragment());
			return sr.at("hasServiceCaseAlert");
		} else return Json.nil();
	
	}
	
	/**
	 * push committed changes from local ontology to the central repository 
	 * 
	 * @return whether the changes were successfully pushed or not 
	 */

	public Json pushToRepo(String Username) {
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();
			return pushALL();
		}
	}
		
	public Json addIndividualObjectPropertyToIndividual(String individualID, String propertyID, Json data, String userName, String comment){

		individualID = MetaOntology.getIndividualIdentifier(individualID);
		
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();		 
			
			List<OWLOntologyChange> changes = MetaOntology.getAddIndividualObjectFromJsonChanges(individualID, propertyID, data);	
			
			boolean r = commit(userName, comment, changes);
			
			if (r) clearCache(individualID);
			
			return Json.object().set("success", r);
		}
	}
	
	/**
	 * Sends Jenkins the signal to start the job that restart servers with fresh ontologies
	 *  
	 * @return whether Jenkins or Time Machines acknowledge the signal or not
	 */
	
	@SuppressWarnings("deprecation")
	public Json refreshOnto(String target, String date, String key) {
		switch (target) {
			case "production":
				// add this post call to the time machine.
				if (date == "0") return GenUtils.httpPostWithBasicAuth(jenkingsEndpointRefreshOntosOnlyProduction, "cirm", "admin", "");
				else {
					String host = getHostIpAddress();
					Json jsonContent = Json.object().set("key", key).set("timeStamp", System.currentTimeMillis());
					Json jsonRestCall = Json.object().set("url", host + "/sradmin/deploy/production")
													 .set("method", "POST")
													 .set("content", jsonContent);
					
					Date time = new java.util.Date(Long.parseLong(date));
					
					Json jsonDate = Json.object().set("second", "0")
							                     .set("minute", time.getMinutes())
							                     .set("hour", time.getHours())
							                     .set("day_of_month", time.getDate())
							                     .set("month", time.getMonth() + 1)
							                     .set("year", 1900 + time.getYear());
					
					Json tmJson = Json.object().set("name", "CIRM Admin Deployment")
											   .set("group", "cirm_admin_tasks")
											   .set("scheduleType", "SIMPLE")
											   .set("scheduleData", Json.object())
											   .set("startTime", jsonDate)
											   .set("endTime", "")
											   .set("state", "NORMAL")
											   .set("description", "Delayed CIRM production ontology only deployment")
											   .set("restCall", jsonRestCall);
					
					return GenUtils.httpPostJson("http://s0141670:9192/timemachine-0.1/task", tmJson);
				}
				
			case "test":
				return GenUtils.httpPostWithBasicAuth(jenkingsEndpointRefreshOntosOnlyTest, "cirm", "admin", "");
		}
		
		throw new IllegalArgumentException("Not a valid target value was passed to the refresh ontologies method.");		
	}
	
	/**
	 * 
	 * @param individualID
	 * @param newLabelContent
	 * @param userName
	 * @param comment
	 * @return
	 */
	
	public Json replaceAlertLabel(String srIndividualID, String alertIndividualID, String newLabelContent, String userName){

		srIndividualID = MetaOntology.getIndividualIdentifier(srIndividualID);
		alertIndividualID = MetaOntology.getIndividualIdentifier(alertIndividualID);
		
		List<String> evictionList = new ArrayList<String>();
		evictionList.add(srIndividualID);
		evictionList.add(alertIndividualID);
		
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();		 
			
			List<OWLOntologyChange> changes = MetaOntology.getReplaceObjectAnnotationChanges(alertIndividualID, newLabelContent);	
            
			String comment = "Replace Alert Message for SR type: " + PREFIX + srIndividualID + " - " + getIndividualLabel(srIndividualID); 
			
			boolean r = commit(userName, comment, changes);
			
			if (r){
				registerChange(srIndividualID);
				clearCache(evictionList);
				return getMetaIndividualFormatedIri(alertIndividualID);
			} else throw new IllegalArgumentException("Cannot update alert label to Service Case Type "+ PREFIX +  srIndividualID);
			
		}
	}
	
	public Json replaceAlertServiceCase (String individualID,  String alertIndividualID, String newLabelContent, String userName){

		individualID = MetaOntology.getIndividualIdentifier(individualID);
		
		List<String> evictionList = new ArrayList<String>();
		evictionList.add(individualID);
		
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();			
			
			String propertyID = "hasServiceCaseAlert";
					
			Date now = new Date();
			String newIri = individualID + "_ALERT_" + Long.toString(now.getTime());
			
			Json data = Json.object().set("iri", newIri)
									 .set("label", newLabelContent)
									 .set("type", "ServiceCaseAlert");
					
			Json oldAlert = getServiceCaseAlert(individualID);
			
			List<OWLOntologyChange> changes;
			
			if (oldAlert.isObject() && oldAlert.has("iri")){
				OWLNamedIndividual ind = OWL.individual(oldAlert.at("iri").asString());
				oldAlert.set("iri", ind.getIRI().getFragment());
				evictionList.add(ind.getIRI().getFragment());
				changes = MetaOntology.getAddReplaceIndividualObjectPropertyFromJsonChanges(individualID, propertyID, data, oldAlert);
			} else {
				changes = MetaOntology.getAddIndividualObjectFromJsonChanges(individualID, propertyID, data);
			}		

			String comment = "Replace Alert Message for SR "+ PREFIX + individualID + " - " + getIndividualLabel(individualID);	
			
			boolean r = commit(userName, comment, changes);
			
			if (r){
				registerChange(individualID);
				clearCache(evictionList);
				return getMetaIndividualFormatedIri(data.at("iri").asString());
			} throw new IllegalArgumentException("Cannot update label to Service Case Type "+ PREFIX +  individualID);
							
		}
	}
	
	/**
	 * Creates or Replace the alert message of a Service Case Type
	 * 
	 * @param individualID the identifier of the Service Case Type
	 * @param data the Json representation of the Service Case Alert 
	 * @param userName that performs the commit
	 * @return
	 */
	
	public Json addNewAlertServiceCase (String individualID, Json data, String userName){

		individualID = MetaOntology.getIndividualIdentifier(individualID);
		
		List<String> evictionList = new ArrayList<String>();
		evictionList.add(individualID);
		
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();			
			
			String propertyID = "hasServiceCaseAlert";
			
			if(data.at("iri").isNull())
			{
				
				Date now = new Date(); 
				
				String newIri = individualID + "_ALERT_" + Long.toString(now.getTime());
				data.set("iri", newIri); 
			}
			
			
			Json oldAlert = getServiceCaseAlert(individualID);
			
			List<OWLOntologyChange> changes;
			
			if (oldAlert.isObject() && oldAlert.has("iri")){
				OWLNamedIndividual ind = OWL.individual(oldAlert.at("iri").asString());
				oldAlert.set("iri", ind.getIRI().getFragment());
				evictionList.add(ind.getIRI().getFragment());
				changes = MetaOntology.getAddReplaceIndividualObjectPropertyFromJsonChanges(individualID, propertyID, data, oldAlert);
			} else {
				changes = MetaOntology.getAddIndividualObjectFromJsonChanges(individualID, propertyID, data);
			}		

			String comment = "Create new Alert Message for SR "+ PREFIX + individualID + " - " + getIndividualLabel(individualID);	
			
			boolean r = commit(userName, comment, changes);
			
			if (r){
				registerChange(individualID);
				clearCache(evictionList);
				return getMetaIndividualFormatedIri(data.at("iri").asString());
			} throw new IllegalArgumentException("Cannot update label to Service Case Type "+ PREFIX +  individualID);
							
		}
	}
	
	/**
	 * Delete the current alert message of a Service Case Type
	 * 
	 * @param individualID the identifier of the Service Case Type
	 * @param userName that performs the commit
	 * @return
	 */
	
	public Json deleteAlertServiceCase (String individualID, String userName){

		individualID = MetaOntology.getIndividualIdentifier(individualID);
		
		List<String> evictionList = new ArrayList<String>();
		evictionList.add(individualID);
		
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();						
			
			Json oldAlert = getServiceCaseAlert(individualID);
			
			List<OWLOntologyChange> changes;
			
			if (oldAlert.isObject() && oldAlert.has("iri")){
				OWLNamedIndividual ind = OWL.individual(oldAlert.at("iri").asString());
				evictionList.add(ind.getIRI().getFragment());
				changes = MetaOntology.getRemoveAllPropertiesIndividualChanges(ind);
			} else throw new IllegalArgumentException("No alert for individual " + PREFIX + individualID);
			
			String comment = "Delete Alert Message for SR "+ PREFIX + individualID + " - " + getIndividualLabel(individualID);	
			
			boolean r = commit(userName, comment, changes);
			
			if (r){
				registerChange(individualID);
				clearCache(evictionList);
			}
			
			return Json.object().set("success", r);
				
		}
	}
	
	/**
	 * 
	 * 
	 */
	public Json getServiceCaseQuestions (String srType){		

		srType = MetaOntology.getIndividualIdentifier(srType);
		
		Json sr = getMetaIndividual(srType);			
		
		// temporary --- remember to remove before production push
		cache.remove(srType);
		
		//Syed's solution
//		sr = OWL.resolveIris(sr, null);
//		
//		if (sr.has("hasServiceField")){
//			
//			return  sr.at("hasServiceField");
//			
//		} else return Json.nil();
		
		//Old solution		
		if (sr.has("hasServiceField")){
			
			return MetaOntology.resolveAllIris( sr.at("hasServiceField"));
			
		} else return Json.nil();
	
	}
	
	public boolean doRollBack (List<Integer> revisionNumbers){		
		boolean result = rollBackRevisions(revisionNumbers);
		
		if (result){
			clearAllCachedButServiceCase();
			MetaOntology.clearCacheAndSynchronizeReasoner();
		}
		
		return result;
	}
	
	private String getHostIpAddress (){
		String host = "",
			   protocol = "",
			   port = "";
		try {
			host = java.net.InetAddress.getLocalHost().getHostName();
			protocol = StartUp.config.at("ssl").asBoolean() ? "https://": "http://";
			port =  StartUp.config.at("ssl").asBoolean() ? StartUp.config.at("ssl-port").asInteger() != 443 ? ":" + StartUp.config.at("ssl-port").asString(): "": 
														   StartUp.config.at("port").asInteger() != 80 ? ":" + StartUp.config.at("port").asString(): "";
		} catch (Exception e) {
			System.out.println("Cannot retreive IP address for localhost");
			e.printStackTrace();
		}
		return protocol + host + port;
	}
	
	public Json addQuestionsServiceCase (String individualID, Json data, String userName){
		String host = getHostIpAddress();		
		
		if (!host.isEmpty() && validateJson(host + "/javascript/schemas/service_field_compact.json", data)){

			individualID = MetaOntology.getIndividualIdentifier(individualID);
			
			List<String> evictionList = new ArrayList<String>();
			evictionList.add(individualID);
			
			OwlRepo repo = getRepo();
			synchronized (repo) {
				repo.ensurePeerStarted();			
				
				String propertyID = "hasServiceField";
				
				// TO-DO: Create IRIs				
				
				Json oldQuestions = getServiceCaseQuestions(individualID);
				
				List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
				
				ThreadLocalStopwatch.now("---- Started Removing Old Questions.");
				
				if (oldQuestions.isArray()){
					for (Json qx: oldQuestions.asJsonList()){
						String iri = "";
						if (qx.isObject())
							if (qx.has("iri")){
								iri = qx.at("iri").asString();
							} else throw new IllegalArgumentException("Cannot find iri property for question: "+ qx.asString());
						else {
							iri = qx.asString();
						}
						
						iri = MetaOntology.getIdFromUri(iri);
						
						OWLNamedIndividual ind = OWL.individual(PREFIX + iri);
						evictionList.add(ind.getIRI().getFragment());
						changes.addAll(MetaOntology.getRemoveAllPropertiesIndividualChanges(ind));
					}
				} else {
					// not so sure ask thomas
					
				}
				ThreadLocalStopwatch.now("---- Ended Removing Old Questions.");
				ThreadLocalStopwatch.now("---- Start Creating New Questions.");
				
				changes.addAll(MetaOntology.getAddIndividualObjectFromJsonChanges(individualID, propertyID, data));
				
				changes = MetaOntology.clearChanges(changes);
				
				ThreadLocalStopwatch.now("---- Ended Creating New Questions.");
	
				String comment = "Create/Replace questions for SR "+ PREFIX + individualID + " - " + getIndividualLabel(individualID);	
				ThreadLocalStopwatch.now("---- Start Commiting Changes.");
				
				boolean r = commit(userName, comment, changes);
				
				ThreadLocalStopwatch.now("---- Ended Commiting Changes.");
				
				if (r){
					registerChange(individualID);
					clearCache(evictionList);
					return getServiceCaseQuestions(individualID);
				} throw new IllegalArgumentException("Cannot update questions to Service Case Type "+ PREFIX +  individualID);
								
			}
		} else throw new IllegalArgumentException("Json object does not match questions schema: " + data.asString()); 
	}
	
	public boolean validateJson (String schemaUri, Json o){	
		try {
			Json.Schema schema = Json.schema(new URI(schemaUri));		
			Json errors = schema.validate(o);
			
			if (errors.has("errors"))	{	
				for (Json error : errors.at("errors").asJsonList())  System.out.println("Validation error " + error.asString());
				return false;
			}
		
		} catch (Exception e) {
			System.out.println("Error ocurred while validating JSON using Schema: " + schemaUri);
			e.printStackTrace();
			return false;
		}		
		
		return true;		
	}
	
	public Json getSchema (String schemaUri){
		try {
			URL url = new URL(schemaUri);	
			
			String host = url.getProtocol() + "://" + url.getHost() + ":" + Integer.toString(url.getPort());
			String path = url.getPath();
			
			return GenUtils.httpGetJson(host + path);
			
		} catch (Exception e) {
			System.out.println("Malformed JSON Schema URI:" + schemaUri);
			e.printStackTrace();
		}
		
		return Json.object();
	}
	
	public Json getFullSchema (String schemaUri){
		try {
			URL url = new URL(schemaUri);	
			
			String host = url.getProtocol() + "://" + url.getHost() + ":" + Integer.toString(url.getPort());
			String path = url.getPath();

			return cleanSchema (buildFullSchema (host , path, Json.nil()));
			
		} catch (Exception e) {
			System.out.println("Malformed JSON Schema URI:" + schemaUri);
			e.printStackTrace();
		}
		
		return Json.object();
	}
	
	private Json buildFullSchema (String host, String path, Json fullSchema){
		Json content = Json.nil();

		if (!path.contains("#")) content = GenUtils.httpGetJson(host + path);
		else content = getFromDefinitions(path, fullSchema);
		
		Json result = parseSchema (content, host, fullSchema.isNull() ? content: fullSchema);

		return result;
	}
	
	private Json parseSchema (Json o, String host, Json fullSchema){		
		if (o.isArray()){
			int i = 0;
			for(Json e: o.asJsonList()){
				String reference = getReference(e);
				if (reference != null){
					o.asJsonList().set(i, buildFullSchema(host, reference, fullSchema));
				} else {
					o.asJsonList().set(i, parseSchema(e, host, fullSchema));
				}
				i++;
			}			
		} else if (o.isObject()){
			Map<String,Json> properties = o.asJsonMap();
			for (Map.Entry<String, Json> propKeyValue : properties.entrySet()) {
				if (propKeyValue.getKey().compareTo("definitions") != 0){
					Json e = propKeyValue.getValue();
					String reference = getReference(e);
					if (reference != null){
						o.set(propKeyValue.getKey(), buildFullSchema(host, reference, fullSchema));
					} else {
						o.set(propKeyValue.getKey(), parseSchema(e, host, fullSchema));
					}
				} 
			}
		}
		
		return o;		
	}
	
	private Json cleanSchema (Json o){		
		if (o.isArray()){
			for(Json e: o.asJsonList()) cleanSchema(e);			
		} else if (o.isObject()){
			if (o.has("definitions")) o.delAt("definitions");
			
		}
		
		return o;		
	}
	
	private String getReference (Json e){
		return e.isObject() && e.has("$ref") ? e.at("$ref").asString() : null;
	}
	
	private Json getFromDefinitions (String path, Json o){
		path = path.replace("#", "");
		String[] l = path.split("/");
		
		Json result = o;
		for (String nodeName: l){
			if (result.has(nodeName)) result = result.at(nodeName);
			else if (!nodeName.isEmpty()) throw new IllegalArgumentException("Cannot find property for question: "+ nodeName + " on schema.");
		}
		
		return result;
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

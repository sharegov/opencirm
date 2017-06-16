package org.sharegov.cirm.legacy;

import static org.sharegov.cirm.OWL.fullIri;
import static org.sharegov.cirm.OWL.owlClass;
import static org.sharegov.cirm.OWL.reasoner;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.hypergraphdb.app.owl.versioning.VersionedOntology;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.sharegov.cirm.MetaOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.OntologyChangesRepo;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.owl.OwlRepo;
import org.sharegov.cirm.rest.OWLIndividuals;
import org.sharegov.cirm.rest.OntoAdmin;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.OntoChangesReference;
import org.sharegov.cirm.utils.OntologyCommit;
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
	private static String jenkingsEndpointRefreshOntosOnlySandbox = "https://api.miamidade.gov/jenkins/job/CRIM-ADMIN-TEST-RESTART-SANDBOX/build?token=x1a85a585ef7c424191c7c58ee3c4a97d556eec91x";
	
	public static final String FROM_EMAIL = "cirm@miamidade.gov";
	public static final String FROM_DISPLAY = "CiRM ADMIN";
	public static final String SMTP_HOST = "smtp.miamidade.gov";
	
	private static ServiceCaseManager instance = null; 
	private Map<String, Json> cache;
	private Map<String, Long> changes;
	private Map<String, Set <String>> srActivities;
	private Map<String, Set <String>> dptActivities;
	private Map<String, Set <String>> dptOutcomes;
	private Map<String, Json> activities;
	private Map<String, Json> outcomes;
	private static HashSet<String> knownTypes = new HashSet<String>(Arrays.asList("legacy:ServiceNote", 
																				  "legacy:ServiceQuestion", 
																				  "legacy:QuestionTrigger", 
																				  "legacy:ChoiceValueList",
																				  "legacy:ChoiceValue",
//																				  "legacy:IntakeMethod",
																				  "legacy:ServiceFieldAlert",
																				  "legacy:ClearServiceField",
																				  "legacy:MarkServiceFieldRequired",
																				  "legacy:MarkServiceFieldRequired",
																				  "legacy:MarkServiceFieldDisabled",
																				  "legacy:ActivityAssignment",
																				  "legacy:StatusChange",
//																				  "legacy:Status",
																				  "legacy:Activity",
//																				  "legacy:AssignActivityToUserRule",
//																				  "legacy:CaseActivityAssignmentRule",
//																				  "legacy:AssignActivityFromGeoAttribute",
																				  "legacy:Outcome",
																				  "legacy:ActivityTrigger",
																				  "legacy:QuestionTrigger",
//																				  "mdc:EventBasedDataSource",
//																				  "mdc:ClientSideEventType",
//																				  "legacy:ServiceAnswerUpdateTimeout",
																				  "legacy:ServiceAnswerConstraint",
																				  "legacy:MessageTemplate",
																				  "Department_County",
																				  "Commission_County"
																				  )) ;
	
	/**
	 * private to defeat multiple instantiation
	 * 
	 */
	private ServiceCaseManager() {
		cache = new ConcurrentHashMap<String, Json>();
		changes = new ConcurrentHashMap<String, Long>();
		srActivities = new ConcurrentHashMap<String, Set <String>>();
		dptActivities = new ConcurrentHashMap<String, Set <String>>();
		dptOutcomes = new ConcurrentHashMap<String, Set <String>>();
		activities = new ConcurrentHashMap<String, Json>();
		outcomes = new ConcurrentHashMap<String, Json>();
		
		ThreadLocalStopwatch.startTop("Started Service Case Admin Cache.");
		getAll();
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
		if (!srType.contains("legacy:")){
			srType = PREFIX + srType;
		}
			
		changes.put(srType, System.currentTimeMillis());
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
		cache.remove("http://www.miamidade.gov/cirm/legacy#" + aKey);
		activities.remove(aKey);
		activities.remove(PREFIX + aKey);
		activities.remove("http://www.miamidade.gov/cirm/legacy#" + aKey);
		outcomes.remove(aKey);
		outcomes.remove(PREFIX + aKey);
		outcomes.remove("http://www.miamidade.gov/cirm/legacy#" + aKey);	
		
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
			cache.remove("http://www.miamidade.gov/cirm/legacy#" + key);
			activities.remove(key);
			activities.remove(PREFIX + key);
			activities.remove("http://www.miamidade.gov/cirm/legacy#" + key);
			outcomes.remove(key);
			outcomes.remove(PREFIX + key);
			outcomes.remove("http://www.miamidade.gov/cirm/legacy#" + key);
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
		if (p.has("mdc:hasParentAgency"))  {
			String parentIri;
			if (p.at("mdc:hasParentAgency").isObject()){
				if (p.at("mdc:hasParentAgency").has("iri")){
					parentIri = (p.at("mdc:hasParentAgency").at("iri").asString());
				} else throw new IllegalArgumentException("Cannot find IRI property for Individual: " + p.asString());
			} else parentIri = p.at("mdc:hasParentAgency").asString();
			
			OWLNamedIndividual ind = OWL.individual(parentIri);
			
			Json np = getSerializedIndividual(ind.getIRI().getFragment(), ind.getIRI().getScheme());
			
			return getParentAgencyName (np);
		}
		
		return p.has("mdc:Name")?p.at("mdc:Name").asString():p.at("rdfs:label").asString();
	}
	
	/** 
	 * 
	 * @param srType a serialized individual
	 * @return the name of the jurisdiction to whom the individual belongs
	 */
	
	private String findJusrisdiction (Json srType){
		if (srType.has("legacy:providedBy")) return getParentAgencyName(srType.at("legacy:providedBy"));
		
		return "";
	}
	
	/**
	 * Recursive search of the department/office to whom the individual p belongs on the ontology  
	 * 
	 * @param p a serialized individual
	 * @return Json representation of the attributes of the department/office 
	 */
	
	private Json resolveDepartment (Json p){			
		if (p.has("mdc:hasParentAgency")) p = p.at("mdc:hasParentAgency");
		else 
			if (p.has("mdc:Department")) p = p.at("mdc:Department");
			else throw new IllegalArgumentException("mdc:Division: " + p.at("iri").asString() + " have no Parent Agency or Department assigned.");
		
		
		String iri;
		if (p.isObject()){
			if (p.has("iri")){
				iri = (p.at("iri").asString());
			} else throw new IllegalArgumentException("Cannot find IRI property for Individual: " + p.asString());
		} else iri = p.asString();
		

		OWLNamedIndividual ind = OWL.individual(iri);
		
		Json np = (p.isObject() && p.has("type") && (p.has("mdc:Name") || p.has("rdfs:label"))) ? p : getSerializedIndividual(ind.getIRI().getFragment(), ind.getIRI().getScheme());
		
		if (np.has("type") && np.at("type").asString().toLowerCase().compareTo("mdc:division_county") != 0){
			return Json.object().set("mdc:Name", np.has("mdc:Name")?np.at("mdc:Name").asString():np.at("rdfs:label").asString()).set("Type", np.at("type").asString()).set("fragment", MetaOntology.getIdFromUri((np.at("iri").asString())));
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
		Json np = (p.isObject() && p.has("type") && (p.has("mdc:Name")||p.has("rdfs:label"))&&p.has("mdc:hasParentAgency")&&p.has("mdc:Department")) ? p: getDepartmentDivisionSerializedIndividual (p);
		
		if (np.has("type") && np.at("type").asString().toLowerCase().compareTo("mdc:division_county") == 0){
			result.set("division",  Json.object().set("mdc:Name", np.has("mdc:Name")?np.at("mdc:Name").asString():np.at("rdfs:label").asString()).set("mdc:Division_Code", np.at("mdc:Division_Code").asString()).set("fragment", MetaOntology.getIdFromUri(np.at("iri").asString())));
			
			result.set("department", resolveDepartment (np));					
		} else {
			result.set("division", Json.object().set("mdc:Name", Json.nil()).set("mdc:Division_Code", Json.nil()).set("fragment", Json.nil()));
			result.set("department", Json.object().set("mdc:Name", np.has("mdc:Name") ? np.at("mdc:Name").asString(): np.at("rdfs:label").asString()).set("Type", np.has("type") ? np.at("type").asString(): Json.nil()).set("fragment", MetaOntology.getIdFromUri(np.at("iri").asString())));
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
		if (srType.has("legacy:providedBy")) return resolveDepartmentDivision (srType.at("legacy:providedBy"));
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
								   .set("rdfs:label", OWL.getEntityLabel(individual))
								   .set("disabled", isSrDisabledOrDisabledCreate(individual));
		
		try {
			
			Json jIndividual = getSerializedMetaIndividual(individual.getIRI().getFragment());			
			
			String jurisdiction;		
			if (jIndividual.has("legacy:hasJurisdictionDescription")){
				jurisdiction = jIndividual.at("legacy:hasJurisdictionDescription").asString();
			} else {
				jurisdiction = findJusrisdiction(jIndividual);
				if (jurisdiction == null || jurisdiction.isEmpty()) throw new IllegalArgumentException("Individual legacy:" +  individual.getIRI().getFragment() + " have no jurisdiction associated.");
				
			}		
			result.set("jurisdiction", jurisdiction);
			Json depdiv = findDepartmentDivision(jIndividual);
			if (!depdiv.has("department")) throw new IllegalArgumentException("Individual legacy:" +  individual.getIRI().getFragment() + " have no provider/owner associated.");
			if (!depdiv.has("division")) throw new IllegalArgumentException("Cannot resolve division for Individual legacy:" +  individual.getIRI().getFragment());
			
			result.with(depdiv);
			
			//caching activities by department
			addActitivitesByDepartment(individual.getIRI().getFragment(), jIndividual, depdiv.at("department").at("fragment").asString());
			
			Json isInterfaceSR = Json.object().set("interface_sr", false);
			if (jIndividual.has("legacy:hasLegacyInterface")){
//				jIndividual.set("hasLegacyInterface", MetaOntology.resolveAllIris(jIndividual.at("hasLegacyInterface ")));
				
				if (jIndividual.at("legacy:hasLegacyInterface").isArray()){
					List<Json> array = jIndividual.at("legacy:hasLegacyInterface").asJsonList();
					for (Json elem : array) {
						if (isInterfaceSR(elem)){
							isInterfaceSR.set("interface_sr", true);
							break;
						}
					}
					
				} else if (jIndividual.at("legacy:hasLegacyInterface").isObject()){
					if (isInterfaceSR(jIndividual.at("legacy:hasLegacyInterface"))){
						isInterfaceSR.set("interface_sr", true);
					}					
				} else {
					throw new IllegalArgumentException("Illegal value of property hasLegacyInterface on legacy:" +  individual.getIRI().getFragment());
				}
			}
			
			result.with(isInterfaceSR);
			
			//caching activities by department
			addActitivitesByDepartment(individual.getIRI().getFragment(), jIndividual, depdiv.at("department").at("fragment").asString());
			
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
	private void addActitivitesByDepartment(String srType, Json serializedSrType, String departmentIriFragment){
		if (serializedSrType.has("legacy:hasActivity")){
			
			Json srTypeActivities = serializedSrType.at("legacy:hasActivity");
			
			Set <String> S = new HashSet<>();
						
			if (srTypeActivities.isArray()){
				for (Json atx : srTypeActivities.asJsonList()){
					if (!atx.isObject()){
						atx = getSerializedIndividual(MetaOntology.getIdFromUri(atx.asString()), MetaOntology.getOntologyFromUri(atx.asString()));
					}
					
					addToCache(atx, S, activities);
					addOutcomesByDepartment (srType, atx, departmentIriFragment);
				}				
			} else {
				if (!srTypeActivities.isObject()){
					srTypeActivities = getSerializedIndividual(MetaOntology.getIdFromUri(srTypeActivities.asString()), MetaOntology.getOntologyFromUri(srTypeActivities.asString()));
				}
				
				addToCache(srTypeActivities, S, activities);
				addOutcomesByDepartment (srType, srTypeActivities, departmentIriFragment);
			}
			
			if (srActivities.containsKey(srType)){
				srActivities.get(srType).addAll(S);
			} else {
				srActivities.put(srType, S);
			}
			
			if (dptActivities.containsKey(departmentIriFragment)){
				dptActivities.get(departmentIriFragment).addAll(S);
			} else {
				dptActivities.put(departmentIriFragment, S);
			}
		}
	}
	
	private void addOutcomesByDepartment (String srType, Json serializedActivity, String departmentIriFragment){
		if (serializedActivity.has("legacy:hasAllowableOutcome")){
			Set <String> S = new HashSet<>();
			
			Json activityOutcomes = serializedActivity.at("legacy:hasAllowableOutcome");
			
			if (activityOutcomes.isArray()){
				for (Json outcx : activityOutcomes.asJsonList()){
					if (!outcx.isObject()){
						outcx = getSerializedIndividual(MetaOntology.getIdFromUri(outcx.asString()), MetaOntology.getOntologyFromUri(outcx.asString()));
					}
					
					addToCache(outcx, S, outcomes);
				}				
			} else {
				if (!activityOutcomes.isObject()){
					activityOutcomes = getSerializedIndividual(MetaOntology.getIdFromUri(activityOutcomes.asString()), MetaOntology.getOntologyFromUri(activityOutcomes.asString()));
				}
				
				addToCache(activityOutcomes, S, outcomes);
			}
			
			if (dptOutcomes.containsKey(departmentIriFragment)){
				dptOutcomes.get(departmentIriFragment).addAll(S);
			} else {
				dptOutcomes.put(departmentIriFragment, S);
			}
		}
	}
	
	private void addToCache (Json atx, Set<String> S, Map <String, Json> cachePtr){
		if (atx.has("iri")){
			String iri = atx.at("iri").asString();
			if (!cachePtr.containsKey(iri)){
				cachePtr.put(iri, atx);
			}
			S.add(iri);
		}
	}
	
	private boolean isInterfaceSR(Json interfaceDescription){
		return (interfaceDescription.isObject() && interfaceDescription.has("legacy:isExternal") && interfaceDescription.at("legacy:isExternal").asString().compareToIgnoreCase("true")==0);
	}
	
	/**
	 * 
	 * Get Activities by department, if department == ALL return all activities on cache
	 * 
	 * Empty array is returned if no results found.
	 * 
	 */
	public Json getActivities (String departmentFragment){
		Json result = Json.array();
		
		if (departmentFragment.compareToIgnoreCase("all") == 0){
						
			Set<OWLNamedIndividual> all = getAllActivityIndividuals();
			
			for (OWLNamedIndividual indx: all){
				String iri = indx.getIRI().toString();
				if (!activities.containsKey(iri)){
					Json atx = MetaOntology.resolveIRIs(getSerializedIndividual(MetaOntology.getIdFromUri(iri), MetaOntology.getOntologyFromUri(iri)), knownTypes);
					if (atx.has("iri")){
						activities.put(iri, atx);
					}
				}
			}
			
			for (Json atx: activities.values()){
				result.add(atx);
			}
		} else {			
			if (dptActivities.containsKey(departmentFragment)){
				for (String iri : dptActivities.get(departmentFragment)) {			
					result.add(activities.get(iri));
				}
			}
		}
		
		return result;
	}
	
	/**
	 * 
	 * Get Outcomes by department, if department == ALL return all outcomes on cache
	 * 
	 * Empty array is returned if no results found.
	 * 
	 */
	public Json getOutcomes (String departmentFragment){
		Json result = Json.array();
		
		if (departmentFragment.compareToIgnoreCase("all") == 0){
						
			Set<OWLNamedIndividual> all = getAllOutcomeIndividuals();
			
			for (OWLNamedIndividual indx: all){
				String iri = indx.getIRI().toString();
				if (!outcomes.containsKey(iri)){
					Json atx = getSerializedIndividual(MetaOntology.getIdFromUri(iri), MetaOntology.getOntologyFromUri(iri));
					if (atx.has("iri")){
						outcomes.put(iri, atx);
					}
				}
			}
			
			for (Json atx: outcomes.values()){
				result.add(atx);
			}
		} else {			
			if (dptOutcomes.containsKey(departmentFragment)){
				for (String iri : dptOutcomes.get(departmentFragment)) {			
					result.add(outcomes.get(iri));
				}
			}
		}
		
		return result;
	}
	
	
	/**
	 * 
	 * Get Outcomes by department
	 * 
	 * Empty array is returned if no results found.
	 * 
	 */
	public Json getOutcomes (String... departments){
		Set<String> allIris = new HashSet<>();		
		for(String departmentFragment : departments)
		{
			if (dptOutcomes.containsKey(departmentFragment)){
				for (String iri : dptOutcomes.get(departmentFragment)) {
					allIris.add(iri);
				}
			}
		}
		
		Json result = Json.array();
		for (String iri: allIris){
			result.add(outcomes.get(iri));			
		}
		return result;
	}	
	
	
	/**
	 * 
	 * @return a list of Service Case Types that contains the required data for the user interface
	 */
	
	public Json getAll() {
		Set<OWLNamedIndividual> S = getAllIndividuals();
		Json A = Json.array();
		long percent = -1;
		int count = 0;
		int total = S.size();
		
		for (OWLNamedIndividual ind : S) {
			A.add(getOne(ind));
			
			count++;
			float newPercent = (float)count/total*100;
			if (newPercent > percent + 1){
				percent = (long) newPercent;
				System.out.print("Creating Service Case Admin Cache "+ String.valueOf(percent) + "% \r");
			}
		}
		
		System.out.println("Creating Service Case Admin Cache 100%");
		
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

	public Json disable(String srType, String userName, String comment) {

		srType = MetaOntology.getIndividualIdentifier(srType);
		
		OwlRepo repo = getRepo();

		synchronized (repo) {
			repo.ensurePeerStarted();
						
			List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
			changes.addAll(MetaOntology.getRemoveIndividualPropertyChanges(srType, "legacy:isDisabledCreate"));	
			changes.addAll(MetaOntology.getRemoveIndividualPropertyChanges(srType, "legacy:isDisabled"));			

			AddAxiom isDisabledCreateAddAxiom = MetaOntology.getIndividualLiteralAddAxiom(srType, "legacy:isDisabledCreate", true);
			
			changes.add(isDisabledCreateAddAxiom);
			
			comment = (comment==null)?"Disable Service Request "+PREFIX+srType + " - " + getIndividualLabel(srType):comment;
			
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
	

	public Json enable(String srType, String userName, String comment) {

		srType = MetaOntology.getIndividualIdentifier(srType);
		
		OwlRepo repo = getRepo();
		
		synchronized (repo) {
			repo.ensurePeerStarted();
			
			List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
			changes.addAll(MetaOntology.getRemoveIndividualPropertyChanges(srType, "legacy:isDisabledCreate"));	
			changes.addAll(MetaOntology.getRemoveIndividualPropertyChanges(srType, "legacy:isDisabled"));			

			AddAxiom isDisabledCreateAddAxiom = MetaOntology.getIndividualLiteralAddAxiom(srType, "legacy:isDisabledCreate", false);
			
			changes.add(isDisabledCreateAddAxiom);
			
			comment = (comment==null)?"Enable Service Request "+PREFIX+srType + " - " + getIndividualLabel(srType):comment;
			
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

			OWLIndividuals q = new OWLIndividuals();
			
			Json S = Json.nil();
			
			try {
				S = q.serialize(q.doQuery("{" + cacheKey + "}"), MetaOntology.getPrefixShortFormProvider());
			} catch (Exception e) {
				System.out.println("Error while querying the Ontology for "+ cacheKey);
				System.out.println("Querying individual's endpoint instead...");
				try {
					
					S = q.getOWLIndividualByName(cacheKey, MetaOntology.getPrefixShortFormProvider());		
					System.out.println("Resolved: "+ cacheKey);
					
				} catch (Exception ex){
					System.out.println("Unable to resolve Object: " + cacheKey);
					ex.printStackTrace();
				}					
			}
			
			if (!S.isNull()){
				if (S.isArray()){
					for (Json ind: S.asJsonList()){
						cache.put(cacheKey, ind);
						return ind;
					}
				} else{
					cache.put(cacheKey, S);
					return S;
				}
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
	
	public Json getSerializedMetaIndividual (String individualID){
		return getSerializedIndividual(individualID, "legacy");						
	}
	
	public Json getSerializedMetaIndividualFormatedIri (String individualID){
		Json result = getSerializedIndividual(individualID, "legacy");
		
		result.set("iri", PREFIX + MetaOntology.getIdFromUri(result.at("iri").asString()));
		
		return result;
	}
	
	public Json getServiceCaseAlert (String srType){		

		srType = MetaOntology.getIndividualIdentifier(srType);
		
		Json sr = getSerializedMetaIndividual(srType);		
		
		if (sr.has("legacy:hasServiceCaseAlert") && sr.at("legacy:hasServiceCaseAlert").isObject()){
			String iri = sr.at("legacy:hasServiceCaseAlert").at("iri").asString();
			OWLNamedIndividual ind = OWL.individual(iri);
			sr.at("legacy:hasServiceCaseAlert").set("iri", MetaOntology.getOntologyFromUri(ind.getIRI().toString()) + ":" + ind.getIRI().getFragment());
			return sr.at("legacy:hasServiceCaseAlert");
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
	
	private void notifyApproval (int lastRevision, Json date) {
		try {
		Properties properties = System.getProperties();
        properties.setProperty("mail.smtp.host", SMTP_HOST);
        properties.setProperty("mail.smtp.starttls.enable", "true");
        Session session = Session.getDefaultInstance(properties);
        MimeMessage message = new MimeMessage(session);
        try {
        	message.setFrom(new InternetAddress(FROM_EMAIL, ""));
        } catch (UnsupportedEncodingException e) {
        	throw new RuntimeException(e);
        }
        
        String cirmAdminServer = getHostIpAddress();
        String link = cirmAdminServer + "/html/cirmadmin/app/index.html#!/approve?rev=" + String.valueOf(lastRevision);
        String dateStr = date.at("month").asString() + "/" + date.at("day_of_month").asString() + "/" + date.at("year").asString() + 
        		         " at " + date.at("hour").asString() + ":" + date.at("minute").asString() + " EST";
        String htmlMessage = "A new CIRM Production deployment has been scheduled for: " + dateStr + "</br>" +
        		             "Follow the <a href='"+ link + "'>link</a> to review.</br>" + 
        		             "Review and approval must occur previous to the deployment date otherwise deployment will not be executed.</br>" +
        		             "Best of luck.</br>CIRM Admin Team.";
        
        message.addRecipients(Message.RecipientType.TO, "ITD-CIRMTT@miamidade.gov");
//        message.addRecipients(Message.RecipientType.TO, "chirino@miamidade.gov");
        message.setSubject("CIRM Deployment Scheduled");
        message.setText(htmlMessage, "UTF-8", "html");
        Transport.send(message);
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	private void notifySimulatedDeploy() {
		try {
		Properties properties = System.getProperties();
        properties.setProperty("mail.smtp.host", SMTP_HOST);
        properties.setProperty("mail.smtp.starttls.enable", "true");
        Session session = Session.getDefaultInstance(properties);
        MimeMessage message = new MimeMessage(session);
        try {
        	message.setFrom(new InternetAddress(FROM_EMAIL, ""));
        } catch (UnsupportedEncodingException e) {
        	throw new RuntimeException(e);
        }
        
        String htmlMessage = "CIRM deployment simulation completed.</br>" +
        		             "Best of luck.</br>CIRM Admin Team.";
        
        message.addRecipients(Message.RecipientType.TO, "ITD-CIRMTT@miamidade.gov");
//        message.addRecipients(Message.RecipientType.TO, "chirino@miamidade.gov");
        message.setSubject("CIRM Deployment Simulation Completed");
        message.setText(htmlMessage, "UTF-8", "html");
        Transport.send(message);
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	private void notifyDeploymentStarted() {
		try {
		Properties properties = System.getProperties();
        properties.setProperty("mail.smtp.host", SMTP_HOST);
        properties.setProperty("mail.smtp.starttls.enable", "true");
        Session session = Session.getDefaultInstance(properties);
        MimeMessage message = new MimeMessage(session);
        try {
        	message.setFrom(new InternetAddress(FROM_EMAIL, ""));
        } catch (UnsupportedEncodingException e) {
        	throw new RuntimeException(e);
        }
        
        String htmlMessage = "Automated CIRM deployment have started as scheduled.</br>" +
        		             "Best of luck.</br>CIRM Admin Team.";
        
        message.addRecipients(Message.RecipientType.TO, "ITD-CIRMTT@miamidade.gov");
//        message.addRecipients(Message.RecipientType.TO, "chirino@miamidade.gov");
        message.setSubject("CIRM Deployment Started");
        message.setText(htmlMessage, "UTF-8", "html");
        Transport.send(message);
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	private void notifyErrorDeploy(String error) {
		try {
		Properties properties = System.getProperties();
        properties.setProperty("mail.smtp.host", SMTP_HOST);
        properties.setProperty("mail.smtp.starttls.enable", "true");
        Session session = Session.getDefaultInstance(properties);
        MimeMessage message = new MimeMessage(session);
        try {
        	message.setFrom(new InternetAddress(FROM_EMAIL, ""));
        } catch (UnsupportedEncodingException e) {
        	throw new RuntimeException(e);
        }
        
        String htmlMessage = "Automated CIRM deployment failed with following error:</br>" + error + "</br>" +
        		             "Best of luck.</br>CIRM Admin Team.";
        
        message.addRecipients(Message.RecipientType.TO, "ITD-CIRMTT@miamidade.gov");
//        message.addRecipients(Message.RecipientType.TO, "chirino@miamidade.gov");
        message.setSubject("CIRM Deployment Failed");
        message.setText(htmlMessage, "UTF-8", "html");
        Transport.send(message);
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	public boolean pushUpToRevision (int lastRevision){
		Set <Integer> toRevert = new HashSet<>();
		
		for (OWLOntology o: OWL.ontologies()){
			
			Map<Integer, OntologyCommit> changes = OntologyChangesRepo.getInstance().getAllRevisionChangesForOnto(o.getOntologyID().toString());
			
			if (changes == null) continue;
			
			for (Map.Entry<Integer, OntologyCommit> e : changes.entrySet()){
				int key = e.getKey();
				if (key <= lastRevision){
					if (!e.getValue().isApproved()){
						toRevert.add(key);
					}
				} else {
					toRevert.add(key);
				}
			}
		}
			
		List<OntoChangesReference> deleted = new ArrayList<>();
		
		if (toRevert.size() > 0){			
			deleted = rollBackRevisions(new ArrayList<>(toRevert));			
		}		
		
		Json r = pushALL();
		
		if (deleted.size() > 0){
			for (OntoChangesReference rx : deleted){
				if (toRevert.contains(rx.getRevision())){
					OntologyCommit cx = rx.getValue();					
					commit( cx.getUserName(), cx.getComment(), cx.getChanges());
				}
			}
		}
		
		return r.has("message") ? (r.at("message").asString().toLowerCase().contains("all changes were applied to target") ? true: false) : false;
	}
	
	private int getCurrentRevision(){
		return getCurrentRevision (OWL.ontology());
	}
	
	private int getCurrentRevision(OWLOntology O){		
		if (O == null){
			throw new IllegalArgumentException("Cannot instanciate default ontology.");
		}
		VersionedOntology vo =  Refs.owlRepo.resolve().repo().getVersionControlledOntology(O);
		return  vo.getHeadRevision().getRevision();
	}
	
	/**
	 * Sends Jenkins the signal to start the job that restart servers with fresh ontologies
	 *  
	 * @return whether Jenkins or Time Machines acknowledge the signal or not
	 */
	
	@SuppressWarnings("deprecation")
	public Json refreshOnto(String target, String date, String key, int revision) {
		switch (target) {
			case "production":
				System.out.println("Deployment endpoint executed:");
				//figure out current revision
				int currentRevision = getCurrentRevision();
				// date = 0 means run this now
				if (date == "0"){
					System.out.println("This is a time-machine scheduled request.");
					if (revision > 0){
						if (revision > currentRevision){
							System.out.println("Deployment not executed: Target revision greater than current.");
							return Json.object().set("success", false);
						}
						
						if (pushUpToRevision(revision)){							
							if (StartUp.getConfig().at("network").at("ontoServer").asString().toLowerCase() == "ontology_server_production"){	
								notifyDeploymentStarted();
								System.out.println("Deployment started!!!");
								return GenUtils.httpPostWithBasicAuth(jenkingsEndpointRefreshOntosOnlyProduction, "cirm", "admin", "");
							} else {
								notifySimulatedDeploy();
								System.out.println("Deployment is simulation, this is a success message.");
								return Json.object().set("success", true);
							}
						} else {
							notifyErrorDeploy("An error was found while trying to configure approved revisions.");
							System.out.println("Deployment not executed: an error was found while trying to configure approved revisions.");
							return Json.object().set("success", false);
						}
					} else {
						notifyErrorDeploy("Invalid revision number.");
						System.out.println("Deployment not executed: Invalid revision number.");
						return Json.object().set("success", false);
					}
				} else {					
					System.out.println("Schedule deployment using time-machine.");
					// add this post call to the time machine.
					String host = getHostIpAddress();
					
					Json jsonContent = Json.object()
							           .set("key", key)
							           .set("timeStamp", System.currentTimeMillis())
							           .set("revision", currentRevision);
					
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
					
					System.out.println("Notify Admin team about this deployment.");
					//notify for approval
					notifyApproval(currentRevision, jsonDate);
					
					Json tmJson = Json.object().set("name", "CIRM Admin Deployment")
											   .set("group", "cirm_admin_tasks")
											   .set("scheduleType", "SIMPLE")
											   .set("scheduleData", Json.object())
											   .set("startTime", jsonDate)
											   .set("endTime", "")
											   .set("state", "NORMAL")
											   .set("description", "Delayed CIRM production ontology only deployment")
											   .set("restCall", jsonRestCall);
					final Json timeMachine = OWL.toJSON((OWLIndividual)Refs.configSet.resolve().get("TimeMachineConfig"));	
					System.out.println("Time Machine url:");
					System.out.println(timeMachine.at("hasUrl").asString());
					System.out.println("Sending request to time-machine...");
					Json r =  GenUtils.httpPostJson(timeMachine.at("hasUrl").asString() + "/task", tmJson);
					System.out.println("Request sent... expect a call back from  time-machine.");
					return r;
				}
				
			case "test":
				return GenUtils.httpPostWithBasicAuth(jenkingsEndpointRefreshOntosOnlyTest, "cirm", "admin", "");
			case "sandbox":
				return GenUtils.httpPostWithBasicAuth(jenkingsEndpointRefreshOntosOnlySandbox, "cirm", "admin", "");
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
				return getSerializedMetaIndividualFormatedIri(alertIndividualID);
			} else throw new IllegalArgumentException("Cannot update alert label to Service Case Type "+ PREFIX +  srIndividualID);
			
		}
	}
	
	public Json replaceAlertServiceCase (String individualID,  String alertIndividualID, String newLabelContent, String userName, String comment){

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
									 .set("rdfs:label", newLabelContent)
									 .set("type", "legacy:ServiceCaseAlert");
					
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

			comment = (comment==null)?"Replace Alert Message for SR "+ PREFIX + individualID + " - " + getIndividualLabel(individualID):comment;	
			
			boolean r = commit(userName, comment, changes);
			
			if (r){
				registerChange(individualID);
				clearCache(evictionList);
				return getSerializedMetaIndividualFormatedIri(data.at("iri").asString());
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
	
	public Json addNewAlertServiceCase (String individualID, Json data, String userName, String comment){

		individualID = MetaOntology.getIndividualIdentifier(individualID);
		
		List<String> evictionList = new ArrayList<String>();
		evictionList.add(individualID);
		
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();			
			
			String propertyID = "legacy:hasServiceCaseAlert";
			
			if(data.at("iri").isNull())
			{
				
				Date now = new Date(); 
				
				String newIri = "http://www.miamidade.gov/cirm/legacy#" + individualID + "_ALERT_" + Long.toString(now.getTime());
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

			comment = (comment==null)?"Create new Alert Message for SR "+ PREFIX + individualID + " - " + getIndividualLabel(individualID):comment;	
			
			boolean r = commit(userName, comment, changes);
			
			if (r){
				registerChange(individualID);
				clearCache(evictionList);
				return getSerializedMetaIndividualFormatedIri(MetaOntology.getIdFromUri(data.at("iri").asString()));
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
		
		Json sr = getSerializedMetaIndividual(srType);			
				
		if (sr.has("legacy:hasServiceField")){
			Json questions = MetaOntology.resolveIRIs(sr.at("legacy:hasServiceField"), knownTypes);
			
			if (!questions.isArray()){
				return Json.array().add(questions);						
			} else {
				return questions;
			}
			
		} else return Json.nil();
	
	}
	
	/**
	 * 
	 * 
	 */
	public Json getAdminSerializedIndividual (String individualID){	
		String id = MetaOntology.getIndividualIdentifier(individualID);
		String onto = MetaOntology.getOntologyFromIdentifier(individualID);
		
		return MetaOntology.resolveIRIs(MetaOntology.getSerializedOntologyObject(id, onto), knownTypes);	
	}
	
	/**
	 * 
	 * 
	 */
	public Json getServiceCaseActivities (String srType){		

		srType = MetaOntology.getIndividualIdentifier(srType);
		
		Json sr = MetaOntology.getSerializedOntologyObject(srType, "legacy");			
				
		if (sr.has("legacy:hasActivity")){
			
			Json activities = sr.at("legacy:hasActivity");
			
			if (!activities.isArray()){
				activities = Json.array().add(activities);						
			} 
			
			Json result = Json.array();
			
			for (Json elem: activities.asJsonList()){
				if (elem.isObject()){
					result.add(MetaOntology.resolveIRIs(elem, knownTypes));
				} else if (MetaOntology.isFullIriString(elem)){
					result.add(MetaOntology.resolveIRIs(MetaOntology.getSerializedOntologyObject(elem.asString()), knownTypes));
				} else throw new IllegalArgumentException("Invalid Activity Json object: " + elem.asString());
			}
			
			return result;
			
		} else return Json.nil();
	
	}
	
	public boolean doRollBack (List<Integer> revisionNumbers){		
		List<OntoChangesReference> result = rollBackRevisions(revisionNumbers);
		
		if (result.size() > 0){
			clearAllCachedButServiceCase();
			MetaOntology.clearCacheAndSynchronizeReasoner();
		}
		
		return result.size() > 0;
	}
	
	private String getHostIpAddress (){
		String host = "",
			   protocol = "",
			   port = "";
		try {
			host = java.net.InetAddress.getLocalHost().getHostName();
			protocol = StartUp.getConfig().at("ssl").asBoolean() ? "https://": "http://";
			port =  StartUp.getConfig().at("ssl").asBoolean() ? StartUp.getConfig().at("ssl-port").asInteger() != 443 ? ":" + StartUp.getConfig().at("ssl-port").asString(): "": 
														   StartUp.getConfig().at("port").asInteger() != 80 ? ":" + StartUp.getConfig().at("port").asString(): "";
		} catch (Exception e) {
			System.out.println("Cannot retreive IP address for localhost");
			e.printStackTrace();
		}
		return protocol + host + port;
	}
	
	public Json addQuestionsServiceCase (String individualID, Json data, String userName, String comment){
		String host = getHostIpAddress();		
		
		if (!host.isEmpty() && validateJson(host + "/javascript/schemas/service_field_list_compact.json", data)){
			
			List<String> evictionList = new ArrayList<String>();
			evictionList.add(individualID);
			String propertyID = "legacy:hasServiceField";
			comment = (comment==null)?"Create/Replace Questions for SR "+ PREFIX + individualID + " - " + getIndividualLabel(individualID):comment;	
			
			Json oldQuestions = getServiceCaseQuestions(individualID);		
			
			if (addObjectsToIndividualProperty (individualID, propertyID, data, userName, comment, evictionList, oldQuestions)){
				registerChange(individualID);
				clearCache(evictionList);
				return getServiceCaseQuestions(individualID);
			} throw new IllegalArgumentException("Cannot update Questions to Service Case Type "+ PREFIX +  individualID);	

		} else throw new IllegalArgumentException("Json object does not match questions schema: " + data.asString()); 	
	}
	
	public Json addActivitesServiceCase (String individualID, Json data, String userName, String comment){	
		String host = getHostIpAddress();		
		
		if (!host.isEmpty() && validateJson(host + "/javascript/schemas/activity_list_compact.json", data)){
			
			List<String> evictionList = new ArrayList<String>();
			evictionList.add(individualID);
			String propertyID = "legacy:hasActivity";
			comment = (comment==null)?"Create/Replace Activities for SR "+ PREFIX + individualID + " - " + getIndividualLabel(individualID):comment;	
			
			Json oldActivities = getServiceCaseActivities(individualID);			
			
			if (addObjectsToIndividualProperty (individualID, propertyID, data, userName, comment, evictionList, oldActivities)){
				registerChange(individualID);
				clearCache(evictionList);
				return getServiceCaseActivities(individualID);
			} throw new IllegalArgumentException("Cannot update Activities to Service Case Type "+ PREFIX +  individualID);	

		} else throw new IllegalArgumentException("Json object does not match activity schema: " + data.asString()); 	
	}
	
	public Json addObjectOnto (String individualIRI, Json data, String userName, String comment){	
		String individualID = MetaOntology.getIndividualIdentifier(individualIRI);
		
		List<String> evictionList = new ArrayList<String>();
		evictionList.add(individualID);		
		
		boolean r = false;
		
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();		
			
			List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
						
			ThreadLocalStopwatch.now("---- Start Creating New Objects for individual: " + individualID);
			
			changes.addAll(MetaOntology.getCreateIndividualObjectFromJsonChanges(data));
			
			changes = MetaOntology.clearChanges(changes);
			
			ThreadLocalStopwatch.now("---- Ended Creating New Objects for individual: " + individualID);
			
			ThreadLocalStopwatch.now("---- Start Commiting Changes.");
			
			r = commit(userName, comment, changes);
			
			ThreadLocalStopwatch.now("---- Ended Commiting Changes.");	
							
		}
		
		if (r){
			registerChange(individualID);
			clearCache(evictionList);
			return MetaOntology.getSerializedOntologyObject(individualIRI);
		} throw new IllegalArgumentException("Cannot add new object: "+ PREFIX +  individualID + " to the ontology.");	
 	
	}
	
	public Json cloneObjectOnto (String sourceID, String newID, String userName, String comment){	
		Json data = MetaOntology.cloneSerializedIndividual(sourceID, newID);
		
		boolean r = false;
		
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();		
			
			List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
						
			ThreadLocalStopwatch.now("---- Start Creating New Objects for individual: " + newID);
			
			changes.addAll(MetaOntology.getCreateIndividualObjectFromJsonChanges(data));
			
			changes = MetaOntology.clearChanges(changes);
			
			ThreadLocalStopwatch.now("---- Ended Creating New Objects for individual: " + newID);

			
			ThreadLocalStopwatch.now("---- Start Commiting Changes.");
			
			r = commit(userName, comment, changes);
			
			ThreadLocalStopwatch.now("---- Ended Commiting Changes.");	
							
		}
		
		if (r){
			registerChange(newID);
			MetaOntology.clearCacheAndSynchronizeReasoner();
			return MetaOntology.getSerializedOntologyObject(data.at("iri").asString());
		} throw new IllegalArgumentException("Cannot add new object: "+ PREFIX +  newID + " to the ontology.");	
 	
	}

	public boolean addObjectsToIndividualProperty (String individualID, String propertyID, Json data, String userName, String comment, List<String> evictionList, Json toRemove){
		individualID = MetaOntology.getIndividualIdentifier(individualID);						
		
		OwlRepo repo = getRepo();
		synchronized (repo) {
			repo.ensurePeerStarted();		
			
			List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
			
			ThreadLocalStopwatch.now("---- Started Removing Old Objects from individual: " + individualID + " property: " + propertyID);
			
			if (toRemove.isArray()){
				for (Json ox: toRemove.asJsonList()){
					String iri = removeObjectOnto (ox, evictionList);
					changes.addAll(MetaOntology.getRemoveIndividualObjectPropertyReferenceChanges(individualID, propertyID, iri));
				}
			} else {
				String iri = removeObjectOnto (toRemove, evictionList);
				changes.addAll(MetaOntology.getRemoveIndividualObjectPropertyReferenceChanges(individualID, propertyID, iri));
				
			}
			ThreadLocalStopwatch.now("---- Ended Removing Old Objects from individual: " + individualID + " property: " + propertyID);
			ThreadLocalStopwatch.now("---- Start Creating New Objects for individual: " + individualID + " property: " + propertyID);
			
			changes.addAll(MetaOntology.getAddIndividualObjectFromJsonChanges(individualID, propertyID, data));
			
			changes = MetaOntology.clearChanges(changes);
			
			ThreadLocalStopwatch.now("---- Ended Creating New Objects for individual: " + individualID + " property: " + propertyID);

			
			ThreadLocalStopwatch.now("---- Start Commiting Changes.");
			
			boolean r = commit(userName, comment, changes);
			
			ThreadLocalStopwatch.now("---- Ended Commiting Changes.");	
			
			return r;
							
		}
	}
	
	/**
	 * Adds a single (existing) activity to a service case. No state changes are assumed for either the activity o
	 * 
	 * @param individualID
	 * @param activityID
	 * @param userName
	 * @return
	 */
	public Json addActivityToServiceCase (String individualID, String activityID, String userName, String comment){
		
			individualID = MetaOntology.getIndividualIdentifier(individualID);	
			activityID = MetaOntology.getIndividualIdentifier(activityID);
			
			List<String> evictionList = new ArrayList<String>();
			evictionList.add(individualID);
			String propertyID = "hasActivity";
			comment = (comment==null)?"Add Activity: " + activityID + " to SR "+ PREFIX + individualID + " - " + getIndividualLabel(individualID):comment;	
			
			if (commit(userName, comment, MetaOntology.getAddIndividualObjectProperty (individualID, propertyID, activityID))){
				registerChange(individualID);
				clearCache(evictionList);
				return getServiceCaseActivities(individualID);
			} throw new IllegalArgumentException("Cannot update Activities to Service Case Type "+ PREFIX +  individualID);	
	}
	
	/**
	 * Remove a single (existing) activity to a service case. No state changes are assumed for either the activity o
	 * 
	 * @param individualID
	 * @param activityID
	 * @param userName
	 * @return
	 */
	public Json removeActivityFromServiceCase (String individualID, String activityID, String userName, String comment){	
		
			individualID = MetaOntology.getIndividualIdentifier(individualID);
			activityID = MetaOntology.getIndividualIdentifier(activityID);
		
			List<String> evictionList = new ArrayList<String>();
			evictionList.add(individualID);
			String propertyID = "hasActivity";
			comment = (comment==null)? "Remove Activity: " + activityID + " to SR "+ PREFIX + individualID + " - " + getIndividualLabel(individualID):comment;	
			
			if (commit(userName, comment, MetaOntology.getRemoveIndividualObjectProperty (individualID, propertyID, activityID))){
				registerChange(individualID);
				clearCache(evictionList);
				return getServiceCaseActivities(individualID);
			} throw new IllegalArgumentException("Cannot update Activities to Service Case Type "+ PREFIX +  individualID);	
	}
	
	private String getIriFromIdividualSerialization (Json serialization){
		String iri = "";
		
		if (serialization.isObject())
			if (serialization.has("iri")){
				iri = serialization.at("iri").asString();
			} else throw new IllegalArgumentException("Cannot find iri property for object: "+ serialization.asString());
		else {
			iri = serialization.asString();
		}
		
		return iri;
	}
	
	private String removeObjectOnto (Json ox, List<String> evictionList){	
		String iri = MetaOntology.getIdFromUri(getIriFromIdividualSerialization(ox));
		
		OWLNamedIndividual ind = OWL.individual(PREFIX + iri);
		evictionList.add(ind.getIRI().getFragment());
		
		return iri;
	}
	
	public boolean validateJson (String schemaUri, Json o){	
//		try {
//			Json.Schema schema = Json.schema(new URI(schemaUri));
//			Json errors = schema.validate(o);
//			
//			if (errors.has("errors"))	{	
//				for (Json error : errors.at("errors").asJsonList())  System.out.println("Validation error " + error.asString());
//				return false;
//			}
//		
//		} catch (Exception e) {
//			System.out.println("Error ocurred while validating JSON using Schema: " + schemaUri);
//			e.printStackTrace();
//			return false;
//		}		
		
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
	
	/**
	 * 
	 * @return a list of individuals that belong to the class Activity
	 */

	public Set<OWLNamedIndividual> getAllActivityIndividuals() {
		return getAllIndividualsOfClass("Activity");
	}
	
	/**
	 * 
	 * @return a list of individuals that belong to the class Outcome
	 */

	private Set<OWLNamedIndividual> getAllOutcomeIndividuals() {
		return getAllIndividualsOfClass("Outcome");
	}
	
	/**
	 * 
	 * @return a list of individuals that belong to the class aClass
	 */

	private Set<OWLNamedIndividual> getAllIndividualsOfClass(String aClass) {
		OWLReasoner reasoner = reasoner();
		OWLClass activity = owlClass(PREFIX + aClass);
		return reasoner.getInstances(activity, false).getFlattened();
	}
	
	
	public Json getServiceCasesByActivityFromCache(String activityIRI){
		
		try {			
			
			Json S = Json.array();
			OWLNamedIndividual activityIndividual = OWL.individual(activityIRI);
			
			for(Json sr: getAll().asJsonList()){
				Json serviceCase = getSerializedMetaIndividual(sr.at("iri").toString().replace("\"", "").split(":")[1]);
				if(serviceCase.has("legacy:hasActivity") && serviceCase.at("legacy:hasActivity").isArray())
				{
					for(Json activity : serviceCase.at("legacy:hasActivity").asJsonList()){
						String iri = null;
						if(activity.isObject()){
							iri = activity.at("iri").asString();
						}else{
							iri = activity.asString();
						}
						if( iri != null && iri.equals(activityIndividual.getIRI().toString()))
						{
							Json o = getOne(OWL.individual(serviceCase.at("iri").asString()));
							if(!S.asJsonList().contains(o))
								S.add(o);
						}
					}
				}
			}
			return S;
		} catch (Exception e) {
			System.out
					.println("Error while querying the cache for SRs with activity: "
							+ activityIRI);
			throw e;	
		}
	}
	/**
	 *  
	 * @param activityIRI the short prefixed for of the iri.
	 * 
	 * @return  a list of service cases who have this activity assigned.
	 */
	
	public Json getServiceCasesByActivity(String activityIRI){
		try {			
			
			Json S = Json.array();
			Set<OWLNamedIndividual> serviceCases = getServiceCaseIndividualsByActivity(activityIRI);
			for(OWLNamedIndividual serviceCase : serviceCases) {
				S.add(getOne(serviceCase));
			}
			return S;
		} catch (Exception e) {
			System.out
					.println("Error while querying the Ontology for SRs with activity: "
							+ activityIRI);
			throw e;	
		}
	}

	private Set<OWLNamedIndividual> getServiceCaseIndividualsByActivity(
			String activityIRI) {
		Set<OWLNamedIndividual> serviceCases = OWL
				.reasoner()
				.getInstances(
						OWL.and(OWL.owlClass("legacy:ServiceCase"),
								OWL.has(OWL
										.objectProperty("legacy:hasActivity"),
										OWL.individual(activityIRI))), true)
				.getFlattened();
		return serviceCases;
	}
	
	/**
	 *  Rename a Service Case
	 * 
	 * @param srType individual identifier
	 * @param newName The new name/label for the Service Case
	 * @param userName who commits the action
	 * @return commit success true or false
	 */
	public Json rename(String srType, String newName, String userName, String comment) {

		srType = MetaOntology.getIndividualIdentifier(srType);
		
		OwlRepo repo = getRepo();
		
		synchronized (repo) {
			repo.ensurePeerStarted();
			List<OWLOntologyChange> changes = MetaOntology.getReplaceObjectAnnotationChanges(srType, newName);
			comment = (comment==null)?"Rename Service Case "+PREFIX+srType + " - " + getIndividualLabel(srType):comment;
			
			boolean r = commit(userName, comment, changes);
			
			if (r) {
				registerChange(srType);
				clearCache(srType);
				return getServiceCaseFormated(srType);
			} else throw new IllegalArgumentException("Unable to rename Service Case Type "+ PREFIX + srType);
		}
	}
	
	
	/**
	 *  Classify individuals as an Activity
	 * 
	 * @param unclassifiedActivities individuals identifier
	 * @param userName who commits the action
	 * @return commit success true or false
	 */
	public Json classifyAsActivity(Set<OWLNamedIndividual> unclassifiedActivities, String userName, String comment) {

		OwlRepo repo = getRepo();
		
		synchronized (repo) {
			repo.ensurePeerStarted();
			OWLOntology o = OWL.ontology();
			OWLDataFactory factory = o.getOWLOntologyManager().getOWLDataFactory();
			List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
			for(OWLNamedIndividual activity: unclassifiedActivities) {
				List<OWLOntologyChange> thisChange = MetaOntology.addNewClassAssertion(activity, Json.array().add("Activity"), o, factory);
				changes.addAll(thisChange);
			}
			comment = (comment==null)?"Classifying a total of " + unclassifiedActivities.size() + " Activity (ies), which were unclassified":comment;
			
			boolean r = commit(userName, comment, changes);
			
			if (!r) throw new IllegalArgumentException("Unable to classify activities " + unclassifiedActivities.toArray().toString());
			return Json.object();
		}
	}
	
	/**
	 *  Classify individuals as an QuestionTrigger
	 * 
	 * @param unclassifiedQuestionTriggers individuals identifier
	 * @param userName who commits the action
	 * @return commit success true or false
	 */
	public Json classifyAsQuestionTrigger(Set<OWLNamedIndividual> unclassifiedQuestionTriggers, String userName, String comment) {

		OwlRepo repo = getRepo();
		
		synchronized (repo) {
			repo.ensurePeerStarted();
			OWLOntology o = OWL.ontology();
			OWLDataFactory factory = o.getOWLOntologyManager().getOWLDataFactory();
			List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
			for(OWLNamedIndividual activity: unclassifiedQuestionTriggers) {
				List<OWLOntologyChange> thisChange = MetaOntology.addNewClassAssertion(activity, Json.array().add("QuestionTrigger"), o, factory);
				changes.addAll(thisChange);
			}
			comment = (comment==null)?"Classifying a total of " + unclassifiedQuestionTriggers.size() + " QuestionTriggers, which were unclassified":comment;
			
			boolean r = commit(userName, comment, changes);
			
			if (!r) throw new IllegalArgumentException("Unable to classify Question triggers " + unclassifiedQuestionTriggers.toArray().toString());
			return Json.object();
		}
	}
	
	/**
	 * 
	 * @return a list of individuals that belong to the class LegacyTrigger.
	 */

	public Set<OWLNamedIndividual> getAllLegacyTriggerIndividuals() {
		return getAllIndividualsOfClass("LegacyTrigger");
	}
	
	
	/**
	 * Disables an Activity
	 * 
	 * @param activity individual identifier 
	 * @param userName who commits the action
	 * @return commit success true or false
	 */

	public Json disableActivity(String activity, String userName, String comment) {

		activity = MetaOntology.getIndividualIdentifier(activity);
		
		OwlRepo repo = getRepo();

		synchronized (repo) {
			repo.ensurePeerStarted();
						
			List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
	
			changes.addAll(MetaOntology.getRemoveIndividualPropertyChanges(activity, "legacy:isDisabled"));			

			AddAxiom isDisabledCreateAddAxiom = MetaOntology.getIndividualLiteralAddAxiom(activity, "legacy:isDisabled", true);
			
			changes.add(isDisabledCreateAddAxiom);
			
			comment = (comment==null)?"Disable Activity "+PREFIX+activity + " - " + getIndividualLabel(activity):comment;
			
			boolean r = commit(userName, comment, changes);
			
			if (r) {
				registerChange(activity);
				clearCache(activity);
				return getSerializedMetaIndividual(activity);
			} else throw new IllegalArgumentException("Unable to disable Activity Type "+ PREFIX + activity);
		}
	}
	
	/**
	 * Enables an Activity
	 * 
	 * @param activity individual identifier 
	 * @param userName who commits the action
	 * @return commit success true or false
	 */
	

	public Json enableActivity(String activity, String userName, String comment) {

		activity = MetaOntology.getIndividualIdentifier(activity);
		
		OwlRepo repo = getRepo();
		
		synchronized (repo) {
			repo.ensurePeerStarted();
			
			List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
				
			changes.addAll(MetaOntology.getRemoveIndividualPropertyChanges(activity, "legacy:isDisabled"));			

			AddAxiom isDisabledCreateAddAxiom = MetaOntology.getIndividualLiteralAddAxiom(activity, "legacy:isDisabled", false);
			
			changes.add(isDisabledCreateAddAxiom);
			
			comment = (comment==null)?"Enable Activity "+PREFIX+activity + " - " + getIndividualLabel(activity):comment;
			
			boolean r = commit(userName, comment, changes);
			
			if (r) {
				registerChange(activity);
				clearCache(activity);
				return  getSerializedMetaIndividual(activity);
			} else throw new IllegalArgumentException("Unable to enable Activity Type "+ PREFIX + activity);
		}
	}
	
	public Json getActivityDBStatus (String activityID){
		return Json.object().set("can_delete", true);
	}
	
	public Json getOutcomeDBStatus (String outcomeID){
		return Json.object().set("can_delete", false);
	}

	public Json getRelatedActiviesByActivity(String activityIRI) {
		Set <String> relatedActivities = new HashSet<>();
		for (String srx: srActivities.keySet()){
			if (srActivities.get(srx).contains(OWL.fullIri(activityIRI).toString())){
				if (relatedActivities.isEmpty()){
					relatedActivities.addAll(srActivities.get(srx));
				} else {
					relatedActivities.retainAll(srActivities.get(srx));
				}
			}
		}

		Json result = Json.array();
		if(relatedActivities != null)
		{
			for (String indx: relatedActivities){
				if (!activities.containsKey(indx)){
					Json atx = MetaOntology.resolveIRIs(getSerializedIndividual(MetaOntology.getIdFromUri(indx), MetaOntology.getOntologyFromUri(indx)), knownTypes);
					if (atx.has("iri")){
						activities.put(indx, atx);
					}
				}
				result.add(activities.get(indx));
			}
		}
		return result;
	}
	
	public Json getChangeSets(String top){
		int limit = Integer.MAX_VALUE;
		
		try {
			limit = Integer.valueOf(top);
		} catch (Exception e){
			
		}
		
		Json result = Json.array();
		Set <Integer> revisions = new HashSet<>();
		
		for (OWLOntology o: OWL.ontologies()){
		
			Map<Integer, OntologyCommit> changes = OntologyChangesRepo.getInstance().getAllRevisionChangesForOnto(o.getOntologyID().toString());
			
			if (changes == null) continue;
			
			int lastCommonRevision = lastMatch(o);
			
			for (Map.Entry<Integer, OntologyCommit> e : changes.entrySet()){
				int key = e.getKey();
				if (revisions.contains(key)) continue;
				if (key <= limit && key > lastCommonRevision){
					revisions.add(key);
					Json commit = e.getValue().toJson();
					result.add(commit);
				}
			}
		}
		
		return result;
	}
	
	public void setChangeSetStatus(int revisionNumber, boolean approved){
		for (OWLOntology o: OWL.ontologies()){
			OntologyCommit cx = OntologyChangesRepo.getInstance().getOntoRevisionChanges(o.getOntologyID().toString(), revisionNumber);
			
			if (cx != null) cx.setApproved(approved);
		}
	}
	
	
	
}

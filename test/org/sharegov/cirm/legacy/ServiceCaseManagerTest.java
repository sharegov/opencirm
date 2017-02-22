package org.sharegov.cirm.legacy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.test.OpenCirmTestBase;
import org.sharegov.cirmx.maintenance.ScriptAddClassificationToIndividual;

import mjson.Json;

/**	
 * Used to test Service Case Manager
 * @author dawong, Syed Abbas
 *
 */
public class ServiceCaseManagerTest extends OpenCirmTestBase {

	private static Logger logger = Logger.getLogger(ServiceCaseManagerTest.class.getName());
	private static ServiceCaseManager serviceCaseManager;
	
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		serviceCaseManager = ServiceCaseManager.getInstance();	
		ConsoleHandler handler = new ConsoleHandler();
		handler.setFormatter(new SimpleFormatter());
		logger.addHandler(handler);
	} 
	
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		System.out.println("ServiceCaseManagerTest class completed.");
		
	}
	
	
	
	
	
	

	

	/***
	 * Testing disabling an SR Type by setting isDisabled to true with a valid SR Type
	 */
	@Test
	public void testDisable() {
		
		String srType = "legacy:PW16";
		String userName = "automated test";
		String comment = "test";
        //make sure the test is disabled
		serviceCaseManager.enable(srType, userName, comment);
		assertTrue(isSRDisabled("legacy:PW16",false));
	
		Json result = serviceCaseManager.disable(srType, userName, comment);
		System.out.println("Hello from test2");
		assertTrue(isSRDisabled("legacy:PW16",true));
		assertTrue(result.at("disabled").asBoolean());
	}
	/***
	 * Testing disabling an SR Type by setting isDisabled to true with an invalid SR Type
	 */
	@Test
	public void testDisable2() {
		
		String srType = "zzzz";
		String userName = "automated test";
		String comment = "test";
		Json result = serviceCaseManager.disable(srType, userName, comment);
		
		assertFalse(result.isNull());
	}
	/***
	 * Testing disabling an SR Type by setting isDisabled to true with an invalid SR Type
	 */
	@Test(expected = NullPointerException.class)
	public void testDisable3() {
		
		String srType = null;
		String userName = "automated test";
		String comment = "test";
		Json result = serviceCaseManager.disable(srType, userName, null);
		
		
	}
	/***
	 * Testing disabling an SR Type by setting isDisabled to false with an invalid SR Type
	 */
	@Test
	public void testEnable() {
		
		String srType = "legacy:PW16";
		String userName = "automated test";
		String comment = "test";
		
		serviceCaseManager.disable(srType, userName, null);
		assertTrue(isSRDisabled("legacy:PW16",true));
	
		Json result = serviceCaseManager.enable(srType, userName, comment);
		
		
		assertTrue(isSRDisabled("legacy:PW16", false));
		assertFalse(result.at("disabled").asBoolean() == false);
		
	}
	/***
	 * Testing serialization with valid service request
	 */
	@Test
	public void testSerialization(){
		Json json = serviceCaseManager.getSerializedMetaIndividual("PW16"); 
		String comment = json.at("comment").asString(); 
		assertTrue((!comment.isEmpty() && comment !=null));
	}
	
	
	/***
	 * Testing serialization with invalid parameter
	 */
	@Test(expected = RuntimeException.class)
	
	public void testSerialization2(){
		Json json = serviceCaseManager.getSerializedMetaIndividual("zzzzzz"); 
	}
	

	/***
	 * Testing for valid service case with alert
	 */
    @Test
    public void testGetServiceCaseAlert(){
    	 String individual = "legacy:PW97";
    	 Json json = serviceCaseManager.getServiceCaseAlert(individual); 
    	assertTrue(json.isNull() == false);	
    }
    
    /**
     * Get the alert for an SR that doesn't have one
     */
    @Test
    public void testGetServiceCaseAlert2(){
    	 String individual = "legacy:PW131"; 
    	 
    	serviceCaseManager.deleteAlertServiceCase(individual, "junit");
    	 Json json = serviceCaseManager.getServiceCaseAlert(individual); 
         System.out.println("get service case alert json " + json.toString());
    	assertTrue(json.isNull());	
    }

    
    /***
     * Testing replacing an existing service case alert
     */
    @Test
    @Ignore
    public void testReplaceAlertLabel()
    {
    	String srType = "legacy:PW16";
    	String individual = "legacy:49173741"; 
    	
    	String newLabel = "test from junit"; 
    	String user = "junit";
    	String existingAnnotation = getAnnotationLabel(individual); 
    	
    	Json json = serviceCaseManager.replaceAlertLabel(srType, individual, newLabel, user); 
    	
    	
    	assertTrue(newLabel.equals(json.at("label")));
    	
    }
    
    
    /***
     * Test deleting a service case alert
     */
    @Test
    public void testDeleteAlertServiceCase(){
    	
    	String individual = "legacy:PW16";
    	String newLabel = "test from junit"; 
    	String user = "junit"; 
    	
    	assertTrue(annotationExist("legacy:49173741"));
    	Json json = serviceCaseManager.deleteAlertServiceCase(individual, user); 
       
    	assertTrue(!annotationExist("legacy:49173741")); 
    
    }
    
    
    /***
     * Test all service cases have a dept with Name
     */
    @Test
    public void testServiceCaseDepartmentNotNull(){
    	Json json = serviceCaseManager.getAll(); 
    	
    	for(Json serviceCase: json.asJsonList())
    	{
    		try {
    			if(serviceCase.has("department") && !serviceCase.at("department").isNull())
    			{
    				assertTrue(serviceCase.at("department").at("Name").asString() != null);
    			}
			} catch (Exception e) {
				assertTrue(serviceCase.at("iri") + " has no dept with Name property " + serviceCase.toString(), false);
			}
    		
    	}
    }
    
    
    /***
     * Test all service cases have a dept with Name
     */
    @Test
    public void testServiceCasesByActivity(){
    	Json json = serviceCaseManager.getServiceCasesByActivity("legacy:ASANCRFU_ASCITATI"); 
    	
    	assertTrue(json.isArray());
    	assertTrue(json.asJsonList().size()>0);
    }
    
    public static boolean annotationExist(String iri){
    	OWLEntity entity = OWL.dataFactory().getOWLNamedIndividual(OWL.fullIri(iri));
        //System.out.println("entity ...." + entity.toStringID());
    	return (entity != null);
    }
	
    public static String getAnnotationLabel(String iri){
    	OWLEntity entity = OWL.dataFactory().getOWLNamedIndividual(OWL.fullIri(iri)); 
    	String label = OWL.getEntityLabel(entity);
    	
    	return label;
    }
    
	public static boolean isSRDisabled(String iri, boolean expected) {

	
		OWLNamedIndividual srTypeInd = OWL.individual(iri);
		Set<OWLLiteral> values = OWL.dataProperties(srTypeInd, "legacy:isDisabledCreate");
		
		for(OWLLiteral value : values)
			System.out.println(iri + " has value " + value.parseBoolean());
		
		return values.contains(OWL.dataFactory().getOWLLiteral(expected));

	}
	
	@Test
	public void testAllActivitiesIndividualsHaveClassAssertionAxioms() {
		ScriptAddClassificationToIndividual.main(null);
//		Set<OWLNamedIndividual> activityIndividualsMissingClassDeclarations = new HashSet<OWLNamedIndividual>();
//		for(OWLNamedIndividual activity: serviceCaseManager.getAllActivityIndividuals())
//		{
//			//Set<OWLClassAssertionAxiom> classAssertion = OWL.ontology().getClassAssertionAxioms(activity);
//			
//			Set<OWLClassExpression> classes = activity.getTypes(OWL.ontology().getImportsClosure());
//
//				if(!classes.contains(OWL.owlClass("legacy:Activity")))
//				{
//					activityIndividualsMissingClassDeclarations.add(activity);
//				}else
//				{
//					if(activityIndividualsMissingClassDeclarations.contains(activity))
//						activityIndividualsMissingClassDeclarations.remove(activity);
//			}
//		}
//		
//	
//		if(activityIndividualsMissingClassDeclarations.size() > 0 )
//		{
//			logger.log(Level.WARNING, "The following activities have no class assertions", activityIndividualsMissingClassDeclarations);
//			for(OWLNamedIndividual i : activityIndividualsMissingClassDeclarations)
//			{
//				System.out.println(i.getIRI().toString());
//			}
//			System.out.println(activityIndividualsMissingClassDeclarations.size());
//			fail("There are activities defined in the ontology with no class assertion.");
//		}
//		
	}

}

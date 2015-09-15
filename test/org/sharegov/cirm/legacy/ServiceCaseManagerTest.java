package org.sharegov.cirm.legacy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import mjson.Json;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.sharegov.cirm.MetaOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.owl.OwlRepo;
import org.sharegov.cirm.test.OpenCirmTestBase;

/**
 * Used to test Service Case Manager
 * @author dawong
 *
 */
public class ServiceCaseManagerTest extends OpenCirmTestBase {

	static ServiceCaseManager serviceCaseManager; 
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		serviceCaseManager = ServiceCaseManager.getInstance();
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
		serviceCaseManager.enable(srType, userName);
		assertTrue(isSRDisabled("legacy:PW16",false));
	
		Json result = serviceCaseManager.disable(srType, userName);
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
		Json result = serviceCaseManager.disable(srType, userName);
		
		assertFalse(result.isNull());
	}
	/***
	 * Testing disabling an SR Type by setting isDisabled to true with an invalid SR Type
	 */
	@Test
	(expected = NullPointerException.class)
	public void testDisable3() {
		
		String srType = null;
		String userName = "automated test";
		String comment = "test";
		Json result = serviceCaseManager.disable(srType, userName);
		
		
	}
	/***
	 * Testing disabling an SR Type by setting isDisabled to false with an invalid SR Type
	 */
	@Test
	
	public void testEnable() {
		
		String srType = "legacy:PW16";
		String userName = "automated test";
		
		serviceCaseManager.disable(srType, userName);
		assertTrue(isSRDisabled("legacy:PW16",true));
	
		Json result = serviceCaseManager.enable(srType, userName);
		
		
		assertTrue(isSRDisabled("legacy:PW16", false));
		assertFalse(result.at("disabled").asBoolean() == false);
		
	}
	/***
	 * Testing serialization with valid service request
	 */
	@Test
	
	public void testSerialization(){
		Json json = serviceCaseManager.getMetaIndividual("PW16"); 
		String comment = json.at("comment").asString(); 
		assertTrue((!comment.isEmpty() && comment !=null));
	}
	
	
	/***
	 * Testing serialization with invalid parameter
	 */
	@Test(expected = RuntimeException.class)
	@Ignore
	public void testSerialization2(){
		Json json = serviceCaseManager.getMetaIndividual("zzzzzz"); 
	}
	

	/***
	 * Testing for valid service case with alert
	 */
    @Test
   
    public void testGetServiceCaseAlert(){
    	 Json json = serviceCaseManager.getServiceCaseAlert("legacy:PW16"); 
         System.out.println("get service case alert json " + json.toString());
    	assertTrue(json.isNull() == false);	
    }
    
    /**
     * Get the alert for an SR that doesn't have one
     */
    @Test
    public void testGetServiceCaseAlert2(){
    	 String individual = "legacy:PW16"; 
    	 
    	serviceCaseManager.deleteAlertServiceCase(individual, "junit");
    	 Json json = serviceCaseManager.getServiceCaseAlert(individual); 
         System.out.println("get service case alert json " + json.toString());
    	assertTrue(json.isNull());	
    }

    
    /***
     * Testing replacing an existing service case alert
     */
    @Test
    public void testReplaceAlertLabel()
    {
    	String individual = "legacy:49173741"; 
    	
    	String newLabel = "test from junit"; 
    	String user = "junit";
    	String existingAnnotation = getAnnotationLabel(individual); 
    	//System.out.println("existing annotation " + existingAnnotation); 
    	Json json = serviceCaseManager.replaceAlertLabel(individual, newLabel, user); 
    	//System.out.println("Hello from test");
    	//System.out.println("json from replace " + json.toString());
    	
    	assertTrue(newLabel.equals(json.at("label")));
    	
    }
    
    
    /***
     * Test deleting a service case alert
     */
    @Test
    @Ignore
    public void testDeleteAlertServiceCase(){
    	
    	String individual = "legacy:PW16";
    	String newLabel = "test from junit"; 
    	String user = "junit"; 
    	
    	Json json = serviceCaseManager.deleteAlertServiceCase(individual, user); 
    	String iri= ""; 
    	assertTrue(annotationExist(iri));
    	
    	//Add the alert again
    	//Json newAlert = Json.object().set("label", "This is a test from junit").set("iri", individual + "ALERT");
    	//serviceCaseManager.addNewAlertServiceCase(individual, newAlert, user); 
    	
    }
    
    /***
     * Test deleting a none existing alert
     */
    @Test(expected = IllegalArgumentException.class)
    public void testDeleteAlertServiceCase2(){
    	//String individual = "legacy:49173741";
    	String individual = "legacy:PW16";
    	String newLabel = "test from junit"; 
    	String user = "junit"; 
    	//Delete
    	Json json = serviceCaseManager.deleteAlertServiceCase(individual, user);
    	//Then Delete again
    	serviceCaseManager.deleteAlertServiceCase(individual, user);
    	
    }
    
    
    @Test
    @Ignore
    public void testDeleteAlertServiceCase3(){
    	
    	String individual = "zzzz";
    	String newLabel = "test from junit"; 
    	String user = "junit"; 
    	
    	Json json = serviceCaseManager.deleteAlertServiceCase(individual, user); 
    	
    	assertTrue(true);
    }
    
   


    
    
    public static boolean annotationExist(String iri){
    	OWLEntity entity = OWL.dataFactory().getOWLNamedIndividual(OWL.fullIri(iri));
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
	
		
	
	

}

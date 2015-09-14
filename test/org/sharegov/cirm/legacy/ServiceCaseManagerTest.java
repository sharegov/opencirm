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
import org.sharegov.cirm.OWL;
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
		
		String srType = "PW16";
		String userName = "automated test";
		String comment = "test";
        //make sure the test is disabled
		serviceCaseManager.enable(srType, userName);
		assertTrue(isSRDisabled("legacy:PW16",false));
	
		Json result = serviceCaseManager.disable(srType, userName);
		assertTrue(isSRDisabled("legacy:PW16",true));
		assertTrue(result.at("success").asBoolean());
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
		assertFalse(result.at("success").asBoolean());
	}
	/***
	 * Testing disabling an SR Type by setting isDisabled to true with an invalid SR Type
	 */
	@Test
	public void testDisable3() {
		
		String srType = null;
		String userName = "automated test";
		String comment = "test";
		Json result = serviceCaseManager.disable(srType, userName);
		assertFalse(result.at("success").asBoolean());
		
	}
	/***
	 * Testing disabling an SR Type by setting isDisabled to false with an invalid SR Type
	 */
	@Test
	
	public void testEnable() {
		
		String srType = null;
		String userName = "automated test";
		String comment = "test";
		Json result = serviceCaseManager.enable(srType, userName);
		
		
		assertTrue(isSRDisabled("legacy:PW16", false));
		assertFalse(result.at("success").asBoolean());
		
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
	
	public void testSerialization2(){
		Json json = serviceCaseManager.getMetaIndividual("zzzzzz"); 
	}
	

	/***
	 * Testing for valid service case with alert
	 */
    @Test
    public void testGetServiceCaseAlert(){
    	Json json = serviceCaseManager.getServiceCaseAlert("PW16"); 
    	assertTrue((json.at("iri").asString() != null && !json.at("iri").asString().isEmpty()));	
    }

    /***
     * Testing for a valid service case without alert
     */
    @Test(expected = IllegalArgumentException.class)
    @Ignore 
    public void testGetServiceCaseAlert2(){
    	serviceCaseManager.getServiceCaseAlert("PW16"); 
    	
    	//assertTrue((json.at("iri").asString() != null && !json.at("iri").asString().isEmpty()));	
    }
    
    /***
     * Testing the replacement of and annotation
     */
    
    /*
     * public static List<OWLOntologyChange> getReplaceObjectAnnotationChanges (String individualID, String newAnnotationContent){
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
     */
    
    @Test
    //@Ignore
    public void testReplaceAnnotation(){
    //String iri = "legacy:49173741";
    //String content = "Hello from junit";
   
    //Json result = serviceCaseManager.replaceObjectAnnotation(iri,content, "junit", "junit annotation test"); 	
    //String label = getAnnotationLabel(iri); 
    //System.out.println("this is the label " + label); 
    
    //assertTrue(label.equals(content));
    }
	
	
	
    public static String getAnnotationLabel(String iri){
    	OWLEntity entity = OWL.dataFactory().getOWLNamedIndividual(OWL.fullIri(iri)); 
    	String label = OWL.getEntityLabel(entity);
    	
    	return label;
    }
    
	public static boolean isSRDisabled(String iri, boolean expected) {

	
		OWLNamedIndividual srTypeInd = OWL.individual(iri);
		Set<OWLLiteral> values = OWL.dataProperties(srTypeInd, "legacy:isDisabledCreate");
		
		return values.contains(OWL.dataFactory().getOWLLiteral(expected));

	}
	
	

}

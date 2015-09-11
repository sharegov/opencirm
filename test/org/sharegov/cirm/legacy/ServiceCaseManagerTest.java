package org.sharegov.cirm.legacy;

import static org.junit.Assert.*;

import java.util.Set;

import mjson.Json;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.StartUp;

public class ServiceCaseManagerTest {

	
	
	static ServiceCaseManager serviceCaseManager; 
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		StartUp.main(new String[]{});
		serviceCaseManager = ServiceCaseManager.getInstance();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Test
	public void testDisable() {
		
		String srType = "PW16";
		String userName = "automated test";
		String comment = "test";
		Json result = serviceCaseManager.disable(srType, userName, comment);
		
		//We can't check against the reasoner for now
		//assertTrue(isSRDisabled("legacy:PW16"));
		assertTrue(result.at("success").asBoolean());
		
	}
	
	@Test
	public void testDisable2() {
		
		String srType = "zzzz";
		String userName = "automated test";
		String comment = "test";
		Json result = serviceCaseManager.disable(srType, userName, comment);
		
		//We can't check against the reasoner for now
		//assertTrue(isSRDisabled("legacy:PW16"));
		assertFalse(result.at("success").asBoolean());
		
	}
	
	@Test
	public void testDisable3() {
		
		String srType = null;
		String userName = "automated test";
		String comment = "test";
		Json result = serviceCaseManager.disable(srType, userName, comment);
		
		//We can't check against the reasoner for now
		//assertTrue(isSRDisabled("legacy:PW16"));
		assertFalse(result.at("success").asBoolean());
		
	}
	
	@Test
	public void testEnable() {
		
		String srType = null;
		String userName = "automated test";
		String comment = "test";
		Json result = serviceCaseManager.enable(srType, userName, comment);
		
		//We can't check against the reasoner for now
		//assertTrue(isSRDisabled("legacy:PW16"));
		assertFalse(result.at("success").asBoolean());
		
	}
	
	@Test
	public void testSerialization(){
		Json json = serviceCaseManager.getMetaIndividual("PW16"); 
		String comment = json.at("comment").asString(); 
		
		assertTrue((!comment.isEmpty() && comment !=null));
		
		
		
	}
	
	@Test(expected = RuntimeException.class)
	public void testSerialization2(){
		Json json = serviceCaseManager.getMetaIndividual("zzzzzz"); 
	}		
		
	public static boolean isSRDisabled(String iri) {
		OWLNamedIndividual srTypeInd = OWL.individual(iri);
		Set<OWLLiteral> values = OWL.dataProperties(srTypeInd, "legacy:isDisabled");
		return values.contains(OWL.dataFactory().getOWLLiteral(true));

	}

}

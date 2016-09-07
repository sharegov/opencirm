package org.sharegov.cirm;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.semanticweb.owlapi.model.OWLClass;
import org.sharegov.cirm.owl.Model;
import org.sharegov.cirm.test.OpenCirmTestBase;

public class ModelTest extends OpenCirmTestBase {

	@Test
	public void testPrefixFor()
	{
		try{
		assertTrue(Model.prefixFor("ServiceQuestion", OWLClass.class).equals("legacy:"));
		assertTrue(Model.prefixFor("EventBasedDataSource", OWLClass.class).equals("mdc:"));
		assertTrue(Model.prefixFor("ClientSideEventType", OWLClass.class).equals("mdc:"));
		assertTrue(Model.prefixFor("ServiceField", OWLClass.class).equals("legacy:"));
		}catch (Exception e)
		{
			
		}
		assertTrue(Model.prefixFor("ServiceQuestion", OWLClass.class, "someDefaultPrefix:").equals("legacy:"));
		assertTrue(Model.prefixFor("EventBasedDataSource", OWLClass.class,"someDefaultPrefix:").equals("mdc:"));
		assertTrue(Model.prefixFor("ClientSideEventType", OWLClass.class,"someDefaultPrefix:").equals("mdc:"));
		assertTrue(Model.prefixFor("ServiceField", OWLClass.class, "someDefaultPrefix:").equals("legacy:"));
		assertTrue(Model.prefixFor("AnUnkownClassThatIsUndefined", OWLClass.class, "someDefaultPrefix:").equals("someDefaultPrefix:"));
		
	}

}

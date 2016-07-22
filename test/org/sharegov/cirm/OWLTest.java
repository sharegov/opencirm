package org.sharegov.cirm;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.semanticweb.owlapi.model.OWLClass;
import org.sharegov.cirm.test.OpenCirmTestBase;

public class OWLTest extends OpenCirmTestBase {

	@Test
	public void testPrefixFor()
	{
		try{
		assertTrue(OWL.prefixFor("ServiceQuestion", OWLClass.class).equals("legacy:"));
		assertTrue(OWL.prefixFor("EventBasedDataSource", OWLClass.class).equals("mdc:"));
		assertTrue(OWL.prefixFor("ClientSideEventType", OWLClass.class).equals("mdc:"));
		assertTrue(OWL.prefixFor("ServiceField", OWLClass.class).equals("legacy:"));
		}catch (Exception e)
		{
			
		}
		assertTrue(OWL.prefixFor("ServiceQuestion", OWLClass.class, "someDefaultPrefix:").equals("legacy:"));
		assertTrue(OWL.prefixFor("EventBasedDataSource", OWLClass.class,"someDefaultPrefix:").equals("mdc:"));
		assertTrue(OWL.prefixFor("ClientSideEventType", OWLClass.class,"someDefaultPrefix:").equals("mdc:"));
		assertTrue(OWL.prefixFor("ServiceField", OWLClass.class, "someDefaultPrefix:").equals("legacy:"));
		assertTrue(OWL.prefixFor("AnUnkownClassThatIsUndefined", OWLClass.class, "someDefaultPrefix:").equals("someDefaultPrefix:"));
		
	}

}

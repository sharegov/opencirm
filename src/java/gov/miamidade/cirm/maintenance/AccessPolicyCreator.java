/*******************************************************************************
 * Copyright 2014 Miami-Dade County
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package gov.miamidade.cirm.maintenance;

import java.util.List;
import java.util.Set;

import org.semanticweb.owlapi.io.OWLXMLOntologyFormat;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;

/**
 * 
 * ADD AXIOM: DLSafeRule( Body(ClassAtom(<http://www.miamidade.gov/cirm/legacy#ServiceCase> Variable(<urn:swrl#x>)) ClassAtom(<http://www.miamidade.gov/ontology#AccessPolicy> <http://www.miamidade.gov/cirm/legacy#311_SV_SR_ACCESS>)) Head(ObjectPropertyAtom(<http://www.miamidade.gov/ontology#hasObject> <http://www.miamidade.gov/cirm/legacy#311_SV_SR_ACCESS> Variable(<urn:swrl#x>))) )
 * ADD AXIOM: DLSafeRule( Body(ClassAtom(<http://www.miamidade.gov/cirm/legacy#ServiceCase> Variable(<urn:swrl#x>)) ClassAtom(<http://www.miamidade.gov/ontology#AccessPolicy> <http://www.miamidade.gov/cirm/legacy#311_SR_ACCESS>)) Head(ObjectPropertyAtom(<http://www.miamidade.gov/ontology#hasObject> <http://www.miamidade.gov/cirm/legacy#311_SR_ACCESS> Variable(<urn:swrl#x>))) )
 * @author Thomas Hilpold
 *
 */
public class AccessPolicyCreator {

	public static String BASE_ONTOLOGY_IRI = "http://www.miamidade.gov/ontology/temp#APCreator";
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		AccessPolicyCreator apc = new AccessPolicyCreator();
		OWLNamedIndividual ap1 = OWL.individual("legacy:311_SR_ACCESS");
		OWLNamedIndividual ap2 = OWL.individual("legacy:311_SV_SR_ACCESS");
		try {
			OWLOntology o1 = apc.createOntologyWithAllSRAccessPolicy(ap1);
			OWLOntology o2 = apc.createOntologyWithAllSRAccessPolicy(ap2);
			
//			File of1 = new File("C:\\accesspolicyonto1.owl");
//			File of2 = new File("C:\\accesspolicyonto2.owl");
//			of1.createNewFile();
//			of2.createNewFile();
//			FileOutputStream ofw1 = new FileOutputStream(of1);
//			FileOutputStream ofw2 = new FileOutputStream(of2);
			o1.getOWLOntologyManager().saveOntology(o1, new OWLXMLOntologyFormat(), System.out);
			o1.getOWLOntologyManager().saveOntology(o2, new OWLXMLOntologyFormat(), System.out);
		} catch (Exception e) 
		{
			throw new RuntimeException(e);
		}
	}

	public AccessPolicyCreator() 
	{
		init();
	}
	
	public void init() 
	{
		OWL.manager();
	}
	/**
	 * Creates an Ontology that contains one <accesspolicy hasObject ServiceREquestn> ObjectPropertyAssertion axiom, 
	 * for each ServiceRequest in the legacy import closure.
	 * 
	 * @param accesspolicy
	 * @return
	 */
	public OWLOntology createOntologyWithAllSRAccessPolicy(OWLNamedIndividual accesspolicy) throws Exception
	{
		Set<OWLNamedIndividual> scs = OWL.reasoner().getInstances(OWL.owlClass("legacy:ServiceCase"), false).getFlattened();
		OWLOntologyManager tempm = Refs.tempOntoManager.resolve();
		OWLOntology apOnto = tempm.createOntology(IRI.create(BASE_ONTOLOGY_IRI + accesspolicy.getIRI().getFragment()));
		OWLDataFactory apDF = tempm.getOWLDataFactory();
		OWLObjectProperty apOP = OWL.objectProperty("hasObject");
		System.out.println("Reasoner found " + scs.size() + " legacy:ServiceCase individuals");
		for (OWLNamedIndividual scInd : scs) 
		{
			OWLAxiom apAx = apDF.getOWLObjectPropertyAssertionAxiom(apOP, accesspolicy, scInd);
			AddAxiom apAddAx = new AddAxiom(apOnto, apAx);
			List<OWLOntologyChange> appliedChanges = tempm.applyChange(apAddAx);
			if (appliedChanges.size() != 1)
			{
				throw new IllegalStateException("Expected one change applied, was : " + appliedChanges.size() + " for axiom " + apAx);
			}
		}
		System.out.println("Created access policy onto : " 
				+ apOnto.getOntologyID().getOntologyIRI() + 
				" contains " + apOnto.getAxiomCount() + " Axioms ");
		return apOnto;
	}
}

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
 * @author Thomas Hilpold
 *
 */
public class AccessPolicyCreatorDLQ {

	private static final String DL_QUERY_HASOBJECT_INDIVIDUAL = "legacy:hasLegacyInterface value legacy:MD-CMS";
	private static final String ACCESS_POLICY = "legacy:RER_SR_ACCESS";
	private static final String ONTOLOGY_IRI = "http://www.miamidade.gov/cirm/legacy/generated";
	
	
	private static final String HAS_OBJECT = "mdc:hasObject";
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		AccessPolicyCreatorDLQ apc = new AccessPolicyCreatorDLQ();
		try {
			OWLOntology o1 = apc.createAccessPolicyAxiomsBasedOnDLQ();
//			File of1 = new File("C:\\accesspolicyonto1.owl");
//			of1.createNewFile();
//			FileOutputStream ofw1 = new FileOutputStream(of1);
			o1.getOWLOntologyManager().saveOntology(o1, new OWLXMLOntologyFormat(), System.out);
		} catch (Exception e) 
		{
			throw new RuntimeException(e);
		}
	}

	public AccessPolicyCreatorDLQ() 
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
	public OWLOntology createAccessPolicyAxiomsBasedOnDLQ() throws Exception
	{
		OWLOntologyManager tempm = Refs.tempOntoManager.resolve();
		OWLOntology apOnto = tempm.createOntology(IRI.create(ONTOLOGY_IRI));
		OWLDataFactory apDF = tempm.getOWLDataFactory();
		//
		OWLNamedIndividual ap = apDF.getOWLNamedIndividual(OWL.fullIri(ACCESS_POLICY));
		OWLObjectProperty apOP = OWL.objectProperty(HAS_OBJECT);
		Set<OWLNamedIndividual> scs = OWL.queryIndividuals(DL_QUERY_HASOBJECT_INDIVIDUAL);
		System.out.println("Reasoner found " + scs.size() + " for DLQuery: " + DL_QUERY_HASOBJECT_INDIVIDUAL);
		for (OWLNamedIndividual scInd : scs) 
		{
			OWLAxiom apAx = apDF.getOWLObjectPropertyAssertionAxiom(apOP, ap, scInd);
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

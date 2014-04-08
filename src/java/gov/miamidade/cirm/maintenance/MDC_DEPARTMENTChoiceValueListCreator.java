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
public class MDC_DEPARTMENTChoiceValueListCreator {

	public static String BASE_ONTOLOGY_IRI = "http://www.miamidade.gov/ontology/temp#MDC_COUNTYCreator";
	public static String DEPT_DLQUERY = "Department_County and hasParentAgency value Miami-Dade_County";
	public static String MDC_CHOICEVALUELIST = "legacy:MDC_DEPARTMENTS";
	public static String HAS_CHOICE_VALUE = "legacy:hasChoiceValue";

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		MDC_DEPARTMENTChoiceValueListCreator apc = new MDC_DEPARTMENTChoiceValueListCreator();
		try {
			OWLOntology o1 = apc.createOntologyWithMDC_DEPARTMENT_ChoiceValueList();
			o1.getOWLOntologyManager().saveOntology(o1, new OWLXMLOntologyFormat(), System.out);
		} catch (Exception e) 
		{
			throw new RuntimeException(e);
		}
	}

	public MDC_DEPARTMENTChoiceValueListCreator() 
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
	public OWLOntology createOntologyWithMDC_DEPARTMENT_ChoiceValueList() throws Exception
	{
		
		Set<OWLNamedIndividual> departments = OWL.queryIndividuals(DEPT_DLQUERY); 
		OWLOntologyManager tempm = Refs.tempOntoManager.resolve();
		OWLOntology apOnto = tempm.createOntology(IRI.create(BASE_ONTOLOGY_IRI));
		OWLNamedIndividual choiceValueList = OWL.individual(MDC_CHOICEVALUELIST);
		OWLDataFactory tempDf = tempm.getOWLDataFactory();
		OWLObjectProperty hasChoiceValue = OWL.objectProperty(HAS_CHOICE_VALUE);
		System.out.println("Reasoner found " + departments.size() + " department individuals");
		for (OWLNamedIndividual department : departments) 
		{
			OWLAxiom hasChoiceValueOPA = tempDf.getOWLObjectPropertyAssertionAxiom(hasChoiceValue, choiceValueList, department);
			AddAxiom hasChoiceValueOPAAddAx = new AddAxiom(apOnto, hasChoiceValueOPA);
			List<OWLOntologyChange> appliedChanges = tempm.applyChange(hasChoiceValueOPAAddAx);
			if (appliedChanges.size() != 1)
			{
				throw new IllegalStateException("Expected one change applied, was : " + appliedChanges.size() + " for axiom " + hasChoiceValueOPA);
			}
		}
		System.out.println("Created choice value list onto : " 
				+ apOnto.getOntologyID().getOntologyIRI() + 
				" contains " + apOnto.getAxiomCount() + " Axioms ");
		return apOnto;
	}
}

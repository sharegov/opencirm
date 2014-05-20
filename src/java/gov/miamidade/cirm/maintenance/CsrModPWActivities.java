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

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;

public class CsrModPWActivities
{

	public static final String PWSTATUS_OLD_ACT_REGEX = "^PW.+_PWSTATUS";

	public static final String PWSTATUS_QUAL_NAME = "legacy:PW_PWSTATUS";
	
	public static final String EXPORTED_IRI = "http://www.miamidade.gov/cirm/legacy/exported";

	public static String OUTPUT_FILE = "C:\\temp\\CsrModPWActivities.owl";

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		CsrModPWActivities a = new CsrModPWActivities();
		a.transformCSROntoPW_STATUS();
	}
	
	public void transformCSROntoPW_STATUS()
	{
		OWL.loader().CACHED_REASONERS = false;
		OWLOntology onto2Mod = OWL.loader().get(EXPORTED_IRI);
		OWLOntologyManager man = OWL.manager();
		System.out.println("About to modify: " + onto2Mod);
		//1. ensure PW_STATUS
		System.out.println("1. Ensuring individual " + PWSTATUS_QUAL_NAME + " and its axioms.");
		ensurePWSTATUSActivity(onto2Mod);
		Set<OWLNamedIndividual> pwActivities = OWL.queryIndividuals("legacy:Activity", PWSTATUS_OLD_ACT_REGEX, onto2Mod);
		Set<OWLAxiom> oldActivityAxioms = new HashSet<OWLAxiom>(201);
		Set<OWLNamedIndividual> pwSRTypes = new HashSet<OWLNamedIndividual>(201);
		//2. remove each activity's dpas and opas, and the sr hasActivity Axiom; 
		//remember the SR type
		System.out.println("2. Removing all referencing axioms: to all activities matching " + PWSTATUS_OLD_ACT_REGEX + "(a dpa, a opa and hasActivity a in onto: " + onto2Mod);
		int activityCount = 0;
		for (OWLNamedIndividual i : pwActivities)
		{
			OWLNamedIndividual srtypeFound = null;
			for (OWLAxiom a: onto2Mod.getReferencingAxioms(i)) 
			{
				System.out.println("Ref: " + a);
				oldActivityAxioms.add(a);
				if (a.isOfType(AxiomType.OBJECT_PROPERTY_ASSERTION))
				{
					OWLObjectPropertyAssertionAxiom opa = (OWLObjectPropertyAssertionAxiom)a;
					if (!opa.getProperty().isAnonymous() && opa.getProperty().asOWLObjectProperty().getIRI().getFragment().equals("hasActivity"))
					{
						srtypeFound = opa.getSubject().asOWLNamedIndividual();
						pwSRTypes.add(srtypeFound);
					}
				}
			}
			if (srtypeFound == null) {
				System.err.println("DID NOT FIND SR TYPE INDIVIDUAL: ABORT! " + i.getIRI());
				System.exit(-1);
			} else
				System.out.println("Sr type: " + srtypeFound.getIRI().getFragment());
			activityCount ++;
		}
		System.out.println("ActivityCount: " + activityCount);
		System.out.println("Please review and then press enter to remove above axioms:");
		QuickQueryCsrMod.prompt();
		int nrOfRemovals = man.removeAxioms(onto2Mod, oldActivityAxioms).size();
		System.out.println("Removed: oldActivityAxioms " + oldActivityAxioms.size() + " (applied: " + nrOfRemovals + ")");
		//3. ensure PW_STATUS is Activity of all PW SRs that have a PWSTATUS_OLD_ACT_REGEX activity
		System.out.println("3. Ensuring all found SR types hasActivity PW_STATUS ");
		Set<OWLObjectPropertyAssertionAxiom> newHasActivityPW_STATUSAxioms = getSRTypeHasActivityPW_StatusAssertions(pwSRTypes);
		int nrOfAdditions = man.addAxioms(onto2Mod, newHasActivityPW_STATUSAxioms).size();
		PrintStream out = System.out;
		if (newHasActivityPW_STATUSAxioms.size() != nrOfAdditions)	
			out = System.err;
		out.println("ensurePWSTATUSActivity: added Axioms: " + nrOfAdditions + " target: " + newHasActivityPW_STATUSAxioms.size());
		//NOT NEEDED??//4. remove all PWSTATUS_OLD_ACT_REGEX
		//5. save ontology
		out.println("Saving ontology to " + OUTPUT_FILE);
		OWL.saveOntology(onto2Mod, new File(OUTPUT_FILE));
	}
	
	public Set<OWLObjectPropertyAssertionAxiom> getSRTypeHasActivityPW_StatusAssertions(Set<OWLNamedIndividual> srTypes)
	{
		Set<OWLObjectPropertyAssertionAxiom> result = new HashSet<OWLObjectPropertyAssertionAxiom>(srTypes.size());
		OWLDataFactory df = OWL.dataFactory();
		for (OWLNamedIndividual srType : srTypes) {
			OWLObjectPropertyAssertionAxiom x = df.getOWLObjectPropertyAssertionAxiom(OWL.objectProperty("legacy:hasActivity"), srType, OWL.individual(PWSTATUS_QUAL_NAME));
			System.out.println("+ " + x);
			result.add(x);
		}
		System.out.println("NrOfHasActivityAssertions: " + result.size());
		return result;
	}

	
	/**
	 * Add single PW_STATUS activity if none exists.
	 * @param o
	 */
	public void ensurePWSTATUSActivity(OWLOntology o)
	{
		if (!o.containsEntityInSignature(OWL.fullIri(PWSTATUS_QUAL_NAME)))
		{
			OWLOntologyManager m = OWL.manager();
			Set<OWLAxiom> axiomsToAdd = getPWStatusAxioms();
			int nrOfAdditions = m.addAxioms(o, getPWStatusAxioms()).size();
			PrintStream out = System.out;
			if (axiomsToAdd.size() != nrOfAdditions)	
				out = System.err;
			out.println("ensurePWSTATUSActivity: ADDED Axioms: " + nrOfAdditions + " target: " + axiomsToAdd.size());
		} else 
			System.err.println("ALREADY THERE!" + PWSTATUS_QUAL_NAME);
	}
	
	public Set<OWLAxiom> getPWStatusAxioms() 
	{
		OWLDataFactory df = OWL.dataFactory();
		Set<OWLAxiom> s = new HashSet<OWLAxiom>();
		OWLNamedIndividual pwstatusInd = df.getOWLNamedIndividual(OWL.fullIri(PWSTATUS_QUAL_NAME));
		//
		s.add(df.getOWLDeclarationAxiom(pwstatusInd));
		s.add(df.getOWLClassAssertionAxiom(df.getOWLClass(OWL.fullIri("legacy:Activity")), pwstatusInd));
//		DataPropertyAssertion(<http://www.miamidade.gov/cirm/legacy#hasSuspenseDays> <http://www.miamidade.gov/cirm/legacy#PW355_PWSTATUS> "0.0"^^xsd:float)
//		DataPropertyAssertion(<http://www.miamidade.gov/cirm/legacy#hasOccurDays> <http://www.miamidade.gov/cirm/legacy#PW355_PWSTATUS> "0.0"^^xsd:float)
//		DataPropertyAssertion(<http://www.miamidade.gov/cirm/legacy#hasLegacyCode> <http://www.miamidade.gov/cirm/legacy#PW355_PWSTATUS> "PWSTATUS"^^xsd:string)
//		DataPropertyAssertion(<http://www.miamidade.gov/cirm/legacy#hasOrderBy> <http://www.miamidade.gov/cirm/legacy#PW355_PWSTATUS> "1.0"^^xsd:float)
//		DataPropertyAssertion(<http://www.miamidade.gov/cirm/legacy#isAutoCreate> <http://www.miamidade.gov/cirm/legacy#PW355_PWSTATUS> "N"^^xsd:string)
//		ObjectPropertyAssertion(<http://www.miamidade.gov/cirm/legacy#hasAllowableOutcome> <http://www.miamidade.gov/cirm/legacy#PW355_PWSTATUS> <http://www.miamidade.gov/cirm/legacy#OUTCOME_COMPLETE>)
		s.add(df.getOWLDataPropertyAssertionAxiom(OWL.dataProperty("legacy:hasSuspenseDays"), pwstatusInd, 0f));
		s.add(df.getOWLDataPropertyAssertionAxiom(OWL.dataProperty("legacy:hasOccurDays"), pwstatusInd, 0f));
		s.add(df.getOWLDataPropertyAssertionAxiom(OWL.dataProperty("legacy:hasLegacyCode"), pwstatusInd, "PWSTATUS"));
		s.add(df.getOWLDataPropertyAssertionAxiom(OWL.dataProperty("legacy:hasOrderBy"), pwstatusInd, 1.0f));
		s.add(df.getOWLDataPropertyAssertionAxiom(OWL.dataProperty("legacy:isAutoCreate"), pwstatusInd, "N"));
		s.add(df.getOWLObjectPropertyAssertionAxiom(OWL.objectProperty("legacy:hasAllowableOutcome"), pwstatusInd, OWL.individual("legacy:OUTCOME_COMPLETE")));
		return s;
	}
}

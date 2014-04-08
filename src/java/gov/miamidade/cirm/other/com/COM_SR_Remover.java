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
package gov.miamidade.cirm.other.com;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Set;

import org.semanticweb.owlapi.io.OWLXMLOntologyFormat;
import org.semanticweb.owlapi.io.WriterDocumentTarget;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyFormat;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.sharegov.cirm.OWL;
//import org.sharegov.cirmx.maintenance.QuickQueryCsrMod;
import org.sharegov.cirmx.maintenance.SRAxiomExtractor;

/**
 * This class removes all axioms that are related to any SR with jurisdiction code COM completely from the csr.owl ontology by
 * loading the file /src/ontology/csr.owl, performing a graph based axiom remove and saving the ontology in a csr.noCOM.owl.
 * 
 * @author Thomas Hilpold
 */
public class COM_SR_Remover
{
	public final static String DLQ_COM_SR_INDIVIUALS = "legacy:ServiceCase and legacy:hasJurisdictionCode value \"COM\"";

	public final static String OUTPUT_CSR_FILE = "C:/work/csr.noCom.owl";
	public final static OWLOntologyFormat OUTPUT_FORMAT = new OWLXMLOntologyFormat();

	private SRAxiomExtractor srAxiomExtractor = new SRAxiomExtractor(); 
	
	public void removeALL_COM_SR_Axioms()
	{
		System.out.println("Removing all COM SR axioms from exported (csr.owl) using OWL environment.");
		System.out.print("Finding all COM SRs types...");
		Set<OWLNamedIndividual> comSRs = OWL.queryIndividuals(DLQ_COM_SR_INDIVIUALS);
		System.out.print("...done. " + comSRs.size() + " found. Press Enter: ");
		prompt();
		OWLOntology exported = OWL.ontology("http://www.miamidade.gov/cirm/legacy/exported");
		OWLOntologyManager manager = OWL.manager();
		int i = 1;
		for(OWLNamedIndividual comSR : comSRs)
		{
			System.out.println("*** " + i + "/" + comSRs.size() + " " + comSR.getIRI().getFragment());
			System.out.print("Finding all related axioms...");
			List<OWLAxiom> comSRAxioms = srAxiomExtractor.getRelatedAxioms(comSR, exported);
			System.out.print(comSRAxioms.size() + " axioms found. Press enter to print.");
			//prompt();
			for (OWLAxiom comSRAxiom : comSRAxioms) 
			{
				System.out.println(comSRAxiom);
			}
			System.out.println("Found axioms: " + comSRAxioms.size());
			System.out.println("Press Enter to remove the axioms above for " + comSR.getIRI().getFragment() +  " n to skip.");
			if (!"n".equals(prompt())) {
				for (OWLAxiom comSRAxiom : comSRAxioms) 
				{
					List<OWLOntologyChange> result = manager.removeAxiom(exported, comSRAxiom);
					if (result.isEmpty()) System.err.println("Axiom not found: " + comSRAxiom);
				}
				System.out.println("Axioms removed of " + comSR.getIRI().getFragment());
				//prompt();
			}
			i++;
		}
		System.out.println("Press enter so save exported in " + OUTPUT_CSR_FILE);
		prompt();
		File outFile = new File(OUTPUT_CSR_FILE);
		try
		{
			OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(outFile) ,Charset.forName("UTF-8"));
			manager.saveOntology(exported, OUTPUT_FORMAT, new WriterDocumentTarget(out));
			System.out.println("Save completed.");
		} catch (OWLOntologyStorageException e)
		{
			e.printStackTrace();
		} catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}
	}
	
	public String prompt()
	{
		return "";
		//return QuickQueryCsrMod.prompt();
	}
	
	public static void main(String[] argv)
	{
		COM_SR_Remover r = new COM_SR_Remover();
		r.removeALL_COM_SR_Axioms();
	}
}

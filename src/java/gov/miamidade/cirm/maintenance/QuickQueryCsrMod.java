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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import mjson.Json;

import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.sharegov.cirm.OWL;


/**
 * Queries the reasoner and prints properties of resulting objects in a CSV format that's easy to process.
 * Just set DLQUERY, PROPS (dotted notation ok!) and VAR(if you use $$VAR$$ in DLQuery).
 * 	 
 * @author Thomas Hilpold
 *
 */
public class QuickQueryCsrMod {
	//public static String DLQUERY = "legacy:hasLegacyInterface value legacy:MD-CMS";
	
	//1 
	public static String DLQUERY = "legacy:MessageTemplate and inverse legacy:hasEmailTemplate some (legacy:Activity and inverse legacy:hasActivity value legacy:$$VAR$$)";

	public static String[] VAR1 = new String[]
	{
		"311DUMP",
		"BULKYTRA",
		"EZGO",
		"GARBAGEM",
		"MISSEDBU",
		"PCNOACC",
		"PERSONEL",
		"SOLIDWAS",
		"SWBUNOAC",
		"SWECLO",
		"SWESHCR",
		"SWMADRCP",
		"SWMCOMM",
		"SWMEIDS",
		"SWMERECC",
		"SWMERECM",
		"SWMFACI",
		"SWMOVCL", //below 4 were added May 15th.
		"SWMRECIS",
		"SWMRESVC",
		"SWMTOUR",
		"SWMOVCTI"
	};
	
	public static String[] VAR = VAR1;
 
	public static String ONTO ="http://www.miamidade.gov/cirm/legacy/exported";

	public static String DATAPROP ="legacy:hasSubject";
	//A string literal
	public static String NEWLITERALVALUE ="SR# $$SR_NUMBER$$ - $$SR_FULLADDRESS$$ - $$SR_ACTIVITY_TYPE$$";
	
	public static String SAVE_PATH = "C:\\temp\\csr_owl.owl";

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String curQuery; 
		boolean done = true;
		int index = 0;
		OWL.reasoner();
		//Print header
		System.out.println(DLQUERY);
		System.out.println(VAR.toString());
		OWLOntology o = OWL.manager().getOntology(IRI.create(ONTO));
		OWLDataProperty prop = OWL.dataProperty(DATAPROP);
		OWLLiteral newLiteral = OWL.dataFactory().getOWLLiteral(NEWLITERALVALUE);
		//Let's go!
		do {
			if (DLQUERY.contains("$$VAR$$"))
			{
				curQuery = DLQUERY.replace("$$VAR$$", VAR[index]);
				done = (index >= VAR.length - 1); 
			} else
				curQuery = DLQUERY;
			Set<OWLNamedIndividual> rs = OWL.queryIndividuals(curQuery);
			int ix = 1;
			for(OWLNamedIndividual i : rs)
			{
				System.out.println("" + ix + " of " + rs.size() + " for " + VAR[index]);
				modifyDataProperty(i, prop, newLiteral, o);
				ix++;
			}
			index ++;
		}
		while (!done);
		System.out.println("About to save ontology to: " + SAVE_PATH);
		prompt();
		OWL.saveOntology(o, new File(SAVE_PATH));
		System.out.println("Finished!");
	}

	
	
	private static void modifyDataProperty(OWLNamedIndividual i,
			OWLDataProperty prop, OWLLiteral newLiteral, OWLOntology o)
	{
		OWLOntologyManager man = o.getOWLOntologyManager();
		OWLDataFactory f = man.getOWLDataFactory();
		Set<OWLDataPropertyAssertionAxiom> dpas = o.getDataPropertyAssertionAxioms(i);
		for (OWLDataPropertyAssertionAxiom a :dpas) 
		{
			if (a.getProperty().equals(prop)) {
				System.out.println("OLD;" + i.getIRI() + ";" + prop.getIRI() + ";" + a.getObject());
				System.out.println("Replace with " + newLiteral  + "?");
				prompt();
				RemoveAxiom ra = new RemoveAxiom(o, a);
				OWLDataPropertyAssertionAxiom newA = f.getOWLDataPropertyAssertionAxiom(prop, i, newLiteral);
				System.out.println("NEW;" + i.getIRI() + ";" + prop.getIRI() + ";" + newLiteral);
				AddAxiom aa = new AddAxiom(o, newA);
				List<OWLOntologyChange> l = new ArrayList<OWLOntologyChange>();
				l.add(ra);
				l.add(aa);
				man.applyChanges(l);
			}
		}
		
	}

	public static String prompt() 
	{
		try{
		    BufferedReader bufferRead = new BufferedReader(new InputStreamReader(System.in));
		    return bufferRead.readLine();
		}
		catch(IOException e)
		{
			throw new RuntimeException(e);
		}
	}


	/**
	 * Prints an individual
	 * @param i
	 * @param props
	 */
	public static void printIndividual(Json i, String[] props)
	{
		for (String prop : props)
		{
			String value = getPropertyValue(i, prop);
			if (prop.endsWith("iri") && value.lastIndexOf('#') > 0)
			{
				value = value.split("#")[1];
			}
			System.out.print(value + ";");
		}
		System.out.println();
	}
	
	public static String getPropertyValue(Json i, String prop) 
	{
		String[] path = prop.split("\\.");
		if (path.length == 0) 
		{
			path = new String[] {prop};
		}
		for (String pathElem : path)
		{
			if (!i.has(pathElem)) {
				return "N/A";
			} 
			i = i.at(pathElem);			
		}
		return i.toString();		
	}
}

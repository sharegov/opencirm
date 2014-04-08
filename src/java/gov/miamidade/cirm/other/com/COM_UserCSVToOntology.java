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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.semanticweb.owlapi.io.OWLXMLOntologyFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.utils.GenUtils;

/**
 * Creates an ontology file for merge from a csv list of COM users as follows:
 * Input:
 * 1 Header line skipped
 * All others, columns:
 * 1. Last Name (No null, No space allowed)
 * 2. First Name (No null, no Space allowed for Middle initial)
 * 3. cKey (No null, unique identified, no space allowed)
 * 4. Email (No null, unique, no space allowed)
 * 5. Phone (Null ok, no space allowed)
 * 
 * Output:
 * For each line:
 * ind mdc:cKey
 * ind mdc:cKey member mdc:User
 * mdc:cKey mdc:LastName UPPER(L)
 * mdc:cKey mdc:FirstName UPPER(F)
 * mdc:cKey mdc:PhoneNumber trim(P)
 * ind mdc:UPPER(emailAdd)@LOWER(domain)
 * mdc:UPPER(emailAdd)@LOWER(domain) type mdc:EmailAddress
 * mdc:cKey mdc:hasEmailAddress ind mdc:UPPER(emailAdd)@LOWER(domain)
 * 
 * @author Thomas Hilpold
 *
 */
public class COM_UserCSVToOntology
{

	public static final String OUTPUT_FILE = "ImportCOMUsers.owlxml.owl";
	public static final String OUTPUT_ONTO = "http://www.miamidade.gov/ontology/ImportCOMUsers";
	public static final String MDC = "mdc:";
	
	/**
	 * @param args [0] a "," separated input file (xls cvs export)
	 */
	public static void main(String[] args)
	{
		File in = new File(args[0]);
		File out = new File(in.getParent() + File.separator + OUTPUT_FILE);
		System.out.println("Output file: " +  out.getAbsolutePath());
		try {
			processFile(new File(args[0]), out);
		} catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	public static void processFile(File in, File out) throws OWLOntologyCreationException, IOException, OWLOntologyStorageException
	{
		OWLOntologyManager m = OWL.manager();
		OWLOntology o = m.createOntology(IRI.create(OUTPUT_ONTO));
		OWLDataFactory df = m.getOWLDataFactory();
		BufferedReader bf = new BufferedReader(new FileReader(in));
		String line;
		int lineCount = 0;
		while ((line = bf.readLine()) != null)
		{
			System.out.println("" + lineCount + " Processing: " + line);
					
			if (lineCount != 0 && !line.isEmpty()) 
				m.addAxioms(o, createAxioms(line.split(","), m, df));
			else 
				System.out.println("Skipping first line as header");
			lineCount ++;
		}
		bf.close();
		// export ontology
		FileOutputStream outS = new FileOutputStream(out);
		System.out.println("Saving Output file: " +  out.getAbsolutePath());
		m.saveOntology(o, new OWLXMLOntologyFormat(), outS);
		outS.close();
	}	
	/**
	 * 
	 * @param userLineTokens
	 * @param m
	 * @param df
	 * @return
	 */
	public static Set<OWLAxiom> createAxioms(String[] userLineTokens, OWLOntologyManager m, OWLDataFactory df) 
	{
		// groom line tokens 0...3 (or 4)
		if (userLineTokens.length < 4) throw new IllegalArgumentException();
		String lastName = userLineTokens[0].trim();
		String firstName = userLineTokens[1].trim();
		String cKey = userLineTokens[2].trim();
		String email = userLineTokens[3].trim();
		String phone = null;
		if (userLineTokens.length > 4)
		{
			phone = userLineTokens[4].trim();
			if (phone.isEmpty()) phone = null;
		}
		if (lastName.isEmpty() || lastName.contains(" ")) throw new IllegalArgumentException("lastname");
		if (firstName.isEmpty()) throw new IllegalArgumentException("firstName");
		if (cKey.isEmpty() || cKey.contains(" ")) throw new IllegalArgumentException("cKey");
		if (email.isEmpty() || !email.contains("@")) throw new IllegalArgumentException("email");
		if (phone != null && (phone.length() < 9 || phone.contains(" "))) throw new IllegalArgumentException("phone");
		int atIndex = email.indexOf("@");
		String part1 = email.substring(0,atIndex).toUpperCase();
		String part2 = email.substring(atIndex).toLowerCase();
		email = part1 + part2;
		if (GenUtils.findEmailIn(email) == null || email.contains(" ")) throw new IllegalArgumentException("email regexp" + email);
		//Used mdc classes/properties
		OWLClass userClass = df.getOWLClass(OWL.fullIri(MDC + "User"));
		OWLDataProperty lastNameDP = df.getOWLDataProperty(OWL.fullIri(MDC + "LastName"));
		OWLDataProperty firstNameDP = df.getOWLDataProperty(OWL.fullIri(MDC + "FirstName"));
		OWLClass emailClass = df.getOWLClass(OWL.fullIri(MDC + "EmailAddress"));
		OWLObjectProperty emailOP = df.getOWLObjectProperty(OWL.fullIri(MDC + "hasEmailAddress"));
		OWLDataProperty phoneDP = df.getOWLDataProperty(OWL.fullIri(MDC + "PhoneNumber"));
		// 7 or 8 per user axioms
		Set<OWLAxiom> axioms = new HashSet<OWLAxiom>();
		OWLNamedIndividual cKeyInd = df.getOWLNamedIndividual(OWL.fullIri(MDC + cKey));
		axioms.add(df.getOWLDeclarationAxiom(cKeyInd));
		axioms.add(df.getOWLClassAssertionAxiom(userClass, cKeyInd));
		axioms.add(df.getOWLDataPropertyAssertionAxiom(lastNameDP, cKeyInd, lastName));
		axioms.add(df.getOWLDataPropertyAssertionAxiom(firstNameDP, cKeyInd, firstName));
		//TODO ERROR in OWL.fullIRI (prefixpattern does not allow @, using topOntology (mdc:) 
		//OWLNamedIndividual emailInd = df.getOWLNamedIndividual(OWL.fullIri(MDC + email));
		OWLNamedIndividual emailInd = df.getOWLNamedIndividual(OWL.fullIri(email));
		axioms.add(df.getOWLDeclarationAxiom(emailInd));
		axioms.add(df.getOWLClassAssertionAxiom(emailClass, emailInd));
		// hasEmailAddress
		axioms.add(df.getOWLObjectPropertyAssertionAxiom(emailOP, cKeyInd, emailInd));
		if (phone != null) 
		{
			axioms.add(df.getOWLDataPropertyAssertionAxiom(phoneDP, cKeyInd, phone));
		}
		if (axioms.size() != (phone == null? 7 : 8)) throw new IllegalStateException("Number of axioms invalid! Target 7 (or 8 with phone), actual " + axioms.size()); 
		return axioms; 
	}
	
}

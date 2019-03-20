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
package org.sharegov.cirm.rdb;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.sharegov.cirm.OWL.dataProperty;
import static org.sharegov.cirm.OWL.fullIri;
import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.objectProperty;
import static org.sharegov.cirm.OWL.owlClass;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import mjson.Json;

import org.junit.Test;
import org.mindswap.pellet.PelletOptions;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.rest.OWLIndividuals;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.rest.OperationService;


public class RelationalOWLMapperTest
{

	private static Logger logger = Logger.getLogger("org.sharegov.cirm.rdb");
	
	private OWLOntology o;
	private OWLNamedIndividual ind;
	
	public void createBULKYTRA() throws OWLOntologyCreationException
	{
		
		 OperationService op = new OperationService();
		 BOntology bontology = op.createBusinessObject(owlClass("legacy:BULYTRA"));
		 o = bontology.getOntology();
		 ind  = bontology.getBusinessObject();
		 OWLOntologyManager manager = o.getOWLOntologyManager();
		 OWLDataFactory df = manager.getOWLDataFactory();
		 OWLClassAssertionAxiom BULKYTRA = df.getOWLClassAssertionAxiom(owlClass("legacy:BULKYTRA"), ind);
		 //OWLDataProperty hasServiceRequestNumber =  dataProperty("hasServiceRequestNumber");
		 //OWLDataPropertyAssertionAxiom a = df.getOWLDataPropertyAssertionAxiom(hasServiceRequestNumber, ind, df.getOWLLiteral(1));
		 OWLObjectProperty hasStatus = objectProperty("legacy:hasStatus");
		 OWLIndividual OPEN = individual("legacy:O-OPEN");
		 OWLObjectPropertyAssertionAxiom b = df.getOWLObjectPropertyAssertionAxiom(hasStatus, ind, OPEN);
		 OWLIndividual addressInd = individual( fullIri(owlClass("Street_Address").asOWLClass().getIRI().getFragment() +  Refs.idFactory.resolve().newId()));
		 OWLClassAssertionAxiom address = df.getOWLClassAssertionAxiom(owlClass("Street_Address"), addressInd);
		 OWLDataPropertyAssertionAxiom c = df.getOWLDataPropertyAssertionAxiom(dataProperty("Street_Number"), addressInd, 101);
		 OWLObjectPropertyAssertionAxiom d = df.getOWLObjectPropertyAssertionAxiom(objectProperty("Street_Direction"), addressInd, individual("North_West"));
		 OWLDataPropertyAssertionAxiom e = df.getOWLDataPropertyAssertionAxiom(dataProperty("Street_Name"), addressInd, df.getOWLLiteral("1ST"));
		 OWLObjectPropertyAssertionAxiom f = df.getOWLObjectPropertyAssertionAxiom(objectProperty("Street_Type"), addressInd, individual("Street_Type_Street"));
		 OWLObjectPropertyAssertionAxiom g = df.getOWLObjectPropertyAssertionAxiom(objectProperty("Street_Address_City"), addressInd, individual("Miami"));
		 OWLObjectPropertyAssertionAxiom h = df.getOWLObjectPropertyAssertionAxiom(objectProperty("Street_Address_State"), addressInd, individual("FL"));
		 OWLObjectPropertyAssertionAxiom i = df.getOWLObjectPropertyAssertionAxiom(objectProperty("atAddress"), ind, addressInd);
		 OWLDataPropertyAssertionAxiom j = df.getOWLDataPropertyAssertionAxiom(dataProperty("Zip_Code"), addressInd, df.getOWLLiteral("33128"));
		 OWLDataPropertyAssertionAxiom k = df.getOWLDataPropertyAssertionAxiom(dataProperty("hasDateCreated"), ind, df.getOWLLiteral("06/14/2011"));
		 OWLDataPropertyAssertionAxiom l = df.getOWLDataPropertyAssertionAxiom(dataProperty("hasDateLastModified"), ind, df.getOWLLiteral("06/14/2011"));
		 
		 OWLIndividual actor1 = individual( fullIri(owlClass("legacy:ServiceCaseActor").asOWLClass().getIRI().getFragment() +  Refs.idFactory.resolve().newId()));
		 OWLIndividual actor2 = individual( fullIri(owlClass("legacy:ServiceCaseActor").asOWLClass().getIRI().getFragment() +  Refs.idFactory.resolve().newId()));
		 OWLClassAssertionAxiom p1 = df.getOWLClassAssertionAxiom(owlClass("legacy:ServiceCaseActor"), actor1);
		 OWLClassAssertionAxiom p2 = df.getOWLClassAssertionAxiom(owlClass("legacy:ServiceCaseActor"), actor2);
		 OWLLiteral literal = df.getOWLLiteral("Cassius Clay", OWL2Datatype.XSD_STRING);
		 System.out.println(literal.getDatatype().getIRI().toString());
		 OWLDataPropertyAssertionAxiom m = df.getOWLDataPropertyAssertionAxiom(dataProperty("Name"), actor1, df.getOWLLiteral("Cassius Clay"));
		 OWLDataPropertyAssertionAxiom n = df.getOWLDataPropertyAssertionAxiom(dataProperty("Name"), actor2, df.getOWLLiteral("Joe Frazier"));
		 
		 OWLObjectPropertyAssertionAxiom p = df.getOWLObjectPropertyAssertionAxiom(objectProperty("legacy:hasServiceCaseActor"), ind, actor1);
		 OWLObjectPropertyAssertionAxiom q = df.getOWLObjectPropertyAssertionAxiom(objectProperty("legacy:hasServiceCaseActor"), ind, actor2);
		 
		 //AddAxiom change1 = new AddAxiom(o, a);
		 AddAxiom change2 = new AddAxiom(o, b);
		 AddAxiom change3 = new AddAxiom(o, BULKYTRA);
		 AddAxiom change4 = new AddAxiom(o, address);
		 AddAxiom change5 = new AddAxiom(o, c);
		 AddAxiom change6 = new AddAxiom(o, d);
		 AddAxiom change7 = new AddAxiom(o, e);
		 AddAxiom change8 = new AddAxiom(o, f);
		 AddAxiom change9 = new AddAxiom(o, g);
		 AddAxiom change10 = new AddAxiom(o, h);
		 AddAxiom change11 = new AddAxiom(o, i);
		 AddAxiom change12 = new AddAxiom(o, j);
		 AddAxiom change13 = new AddAxiom(o, k);
		 AddAxiom change14 = new AddAxiom(o, l);
		 AddAxiom change15 = new AddAxiom(o, p1);
		 AddAxiom change16 = new AddAxiom(o, p2);
		 AddAxiom change17 = new AddAxiom(o, m);
		 AddAxiom change18 = new AddAxiom(o, n);
		 AddAxiom change19 = new AddAxiom(o, p);
		 AddAxiom change20 = new AddAxiom(o, q);
		 
		 //manager.applyChange(change1);
		 manager.applyChange(change2);
		 manager.applyChange(change3);
		 manager.applyChange(change4);
		 manager.applyChange(change5);
		 manager.applyChange(change6);
		 manager.applyChange(change7);
		 manager.applyChange(change8);
		 manager.applyChange(change9);
		 manager.applyChange(change10);
		 manager.applyChange(change11);
		 manager.applyChange(change12);
		 manager.applyChange(change13);
		 manager.applyChange(change14);
		 manager.applyChange(change15);
		 manager.applyChange(change16);
		 manager.applyChange(change17);
		 manager.applyChange(change18);
		 manager.applyChange(change19);
		 manager.applyChange(change20);
	}
	
	@Test
	public void testRelationalMappings()
	{
		testReasonerConfigurationPerformance();
		assertNotNull(RelationalOWLMapper.table(owlClass("legacy:BULKYTRA")).toString());
		logger.info("Assert table mapping true");
		assertNotNull(RelationalOWLMapper.columnMapping(owlClass("legacy:BULKYTRA")));
		logger.info("Assert column mapping true");
		assertTrue(RelationalOWLMapper.join(individual("CIRM_SR_REQUESTS"), individual("CIRM_SR_PARTICIPANT")).equals(individual("CIRM_SRREQ_PARTDATA")));
		OWLNamedIndividual manyTable = individual("CIRM_SR_REQUESTS");
		OWLNamedIndividual joinTable = RelationalOWLMapper.join(individual("CIRM_MDC_ADDRESS"), manyTable);
		assertTrue(joinTable.equals(manyTable));
		logger.info("Assert joins true");
	}
	
	@Test
	public void testPersistMappedObject() throws OWLOntologyCreationException
	{
		createBULKYTRA();
		OperationService op = new OperationService();
		op.getPersister().saveBusinessObjectOntology(o);
		long x = System.currentTimeMillis();
		createBULKYTRA();
		op.getPersister().saveBusinessObjectOntology(o);
		logger.info("Time to save bo " + (System.currentTimeMillis() - x )/1000 + " sec.");
		
	}
	
	@Test
	public void testReasonerConfigurationPerformance()
	{
		long x = 36; //avg seconds with default options.
		//long x = simpleReasonerQuery();
//		Properties options = defaultPelletOptions();
//		options.setProperty("USE_UNIQUE_NAME_ASSUMPTION", "true");
//		PelletOptions.setOptions(options);
//		RelationalOWLMapper.get();
//		
		//tryOption("USE_UNIQUE_NAME_ASSUMPTION", "true", x);
		//tryOption("USE_PSEUDO_NOMINALS ", "true", x);
		//tryOption("FULL_SIZE_ESTIMATE", "true", x);
		//tryOption("REALIZE_INDIVIDUAL_AT_A_TIME", "true",x);
		//tryOption("MAX_ANONYMOUS_CACHE", "9999999",x);
		//tryOption("TRACK_BRANCH_EFFECTS", "true", x);
		//tryOption("CACHE_RETRIEVAL", "true",x);
	}
	
	private void tryOption(String option, String optionValue, long defaultQueryTime)
	{
		Properties options = defaultPelletOptions();
		options.setProperty(option, optionValue);
		PelletOptions.setOptions(options);
		try{
			long y  = simpleReasonerQuery();
			long performance = defaultQueryTime - y;
			boolean gain = (performance >= 0); 
			logger.log(Level.INFO, "Option " + option + " = " + optionValue + " resulted in performance " + ((gain)?"gain":"loss")+ " of " + performance + " sec.");
		}catch(Exception e)
		{
			logger.log(Level.SEVERE, "Option " + option + " = " + optionValue + "resulted in an Exception", e);
		}
	}
	
	private long simpleReasonerQuery()
	{
		OWLIndividuals i = new OWLIndividuals();
		long x = System.currentTimeMillis();
		//i.doQuery("Miami_Dade_City or County");
		System.out.println(i.doQuery("OWLClass and hasTableMapping some DBTable").toString());
		long t =(System.currentTimeMillis() - x)/1000;
		logger.info("query time:" + t );
		return t;
	}
	
	private Properties defaultPelletOptions()
	{
		Properties options = new Properties();
		try
		{
			URL url = PelletOptions.class.getClassLoader().getResource( "pellet.properties" );
			InputStream is = url.openStream();
			options.load(is);
			is.close();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return options;
		
	}
	
	
}

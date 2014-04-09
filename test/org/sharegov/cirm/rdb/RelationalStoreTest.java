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

import static org.sharegov.cirm.OWL.dataProperty;

import static org.sharegov.cirm.OWL.fullIri;
import static org.sharegov.cirm.OWL.hash;
import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.objectProperty;
import static org.sharegov.cirm.OWL.ontology;
import static org.sharegov.cirm.OWL.owlClass;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLAxiomChange;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.rest.OperationService;
import org.sharegov.cirm.utils.DBGUtils;

public class RelationalStoreTest
{

// hilpold temporarily disabled until core RDB stable.
//	@Test
//	public void testCreateData()
//	{ 
//		
//		 AppContextResolver.app = new MetaService();
//		 MetaService.get();
//		 OWLNamedIndividual ind = individual("Garbage_Complaint-1");
//		 Map<OWLDataPropertyExpression, Set<OWLLiteral>> dprops =ind.getDataPropertyValues(ontology());
//		 RelationalStore store = new RelationalStore();
//		 store.insertDataProperties(ind, dprops, null);
//		 
//	}
//
//	@Test
//	public void testCreateObjects()
//	{
//		 AppContextResolver.app = new MetaService();
//		 MetaService.get();
//		 OWLNamedIndividual ind = individual("Garbage_Complaint-1");
//		 Map<OWLObjectPropertyExpression, Set<OWLIndividual>> oprops =ind.getObjectPropertyValues(ontology());
//		 RelationalStore store = new RelationalStore();
//		 store.insertObjectProperties(ind, oprops, null);
//	}
//	
//	@Test
//	public void testUpdateObjects()
//	{
//		 AppContextResolver.app = new MetaService();
//		 MetaService.get();
//		 OWLNamedIndividual ind = individual("Garbage_Complaint-1");
//		 Map<OWLObjectPropertyExpression, Set<OWLIndividual>> oprops =ind.getObjectPropertyValues(ontology());
//		 RelationalStore store = new RelationalStore();
//		 System.out.println(store.updateObjectProperties(ind, oprops,null));
//	}
//	
//	@Test
//	public void testUpdateData()
//	{
//		 AppContextResolver.app = new MetaService();
//		 MetaService.get();
//		 OWLNamedIndividual ind = individual("Garbage_Complaint-1");
//		 Map<OWLDataPropertyExpression, Set<OWLLiteral>> dprops = ind.getDataPropertyValues(ontology());
//		 RelationalStore store = new RelationalStore();
//		 store.updateDataProperties(ind, dprops,null);
//	}
//	
//	
//	@Test
//	public void testReadProperties()
//	{
//		 AppContextResolver.app = new MetaService();
//		 MetaService.get();
//		 OWLNamedIndividual ind = individual("Garbage_Complaint-1");
//		 RelationalStore store = new RelationalStore();
//		 System.out.println("Data Props=" + store.selectDataProperties(ind).size());
//		 System.out.println("Object Props=" + store.selectDataProperties(ind).size());
//		 
//	}
//	@Test
//	public void testCreateClassification()
//	{
//		 AppContextResolver.app = new MetaService();
//		 MetaService.get();
//		 OWLNamedIndividual ind = individual("Garbage_Complaint-1");
//		 RelationalStore store = new RelationalStore();
//		 HashSet<OWLClass> classes = new HashSet<OWLClass>();
//		 classes.add(OWLUtils.owlClass("Service_Type"));
//		 store.update(ind, classes);
//	}
//	
//	@Test
//	public void testReadClassification()
//	{
//		 AppContextResolver.app = new MetaService();
//		 MetaService.get();
//		 OWLNamedIndividual ind = individual("Garbage_Complaint-1");
//		 RelationalStore store = new RelationalStore();
//		 System.out.println("Classification=" + store.selectClass(ind, new Date()).size());
//		 
//	}
	
	@Test
	public void testSelectInsert()
	{
		 System.out.println(new Date());
		 RelationalStoreExt store = new RelationalStoreExt();
		 System.out.println(ontology().getClassesInSignature().size() + ontology().getIndividualsInSignature().size() 
				 + ontology().getDataPropertiesInSignature().size());
		 Map<OWLEntity, DbId> identifiers = store.selectInsertIDsAndEntitiesByIRIs(ontology().getClassesInSignature(), true);
		 identifiers.putAll(store.selectInsertIDsAndEntitiesByIRIs(ontology().getIndividualsInSignature(), true));
		 identifiers.putAll(store.selectInsertIDsAndEntitiesByIRIs(ontology().getDataPropertiesInSignature(), true));
		 for(Map.Entry<OWLEntity, DbId> entry : identifiers.entrySet())
		 {
			 System.out.println(entry.getKey() + " = " + entry.getValue());
		 }
		 System.out.println(new Date());
		 System.out.println(identifiers.size());
	}
	
	@Test
	public void testHash()
	{
		 //RelationalStore store = new RelationalStore();
		 System.out.println(hash("password").getBytes().length);
		 System.out.println(hash("password").getBytes().length);
		 System.out.println(hash(fileToString("C:/work/cirmservices/src/ontology/County_Working.owl")).getBytes().length);
		 System.out.println(hash(fileToString("C:/work/cirmservices/src/ontology/County_Working.owl")).getBytes().length);
	}
	
	
	public String fileToString(String fileName) 
    { 
      try{ 
    	byte[] buffer = new byte[(int) new File(fileName).length()];
	    BufferedInputStream f = new BufferedInputStream(new FileInputStream(fileName));
	    f.read(buffer);
	    return new String(buffer);
      } 
      catch (Exception e) { 
        e.printStackTrace(); 
      } 
        return null; 
    }
	
	@Test
	public void calculateSize()
	{
		//calculate the byte padding size once base 64 encoding is applied.
		int bytes = 20;
		int size = (bytes + 2 - ((bytes + 2) % 3)) / 3 * 4;
		System.out.println(size);
	}
	
	@Test
	public void testCreateLiterals()
	{
		OWLDataFactory factory = OWL.dataFactory();
//		OWLNamedIndividual info = OWL.individual(fullIri("GICDWTestDatabase"));
//		OWLNamedIndividual dbType = OWL.objectProperty(info, "hasDatabaseType");
//		String driverClassName = OWL.dataProperty(dbType, "hasDriver").getLiteral();
//		String url = OWL.dataProperty(info, "hasUrl").getLiteral();
//		String username = OWL.dataProperty(info, "hasUsername").getLiteral();
//		String password = OWL.dataProperty(info, "hasPassword").getLiteral();
		RelationalStoreExt store =  new RelationalStoreExt(new DataSourceRef(fullIri("GICDWTestDatabase")));
		Map<String,OWLLiteral> literals = new HashMap<String,OWLLiteral>();
		OWLLiteral l1 = factory.getOWLLiteral(63.85);
		OWLLiteral l2 = factory.getOWLLiteral("Hello World!");
		OWLLiteral l3 = factory.getOWLLiteral("Hello World!", OWL2Datatype.RDF_PLAIN_LITERAL);
		OWLLiteral l4 = factory.getOWLLiteral("Yes");
		OWLLiteral l5 = factory.getOWLLiteral("No");
		literals.put(hash(l1),l1);
		literals.put(hash(l1),l1);
		literals.put(hash(l2),l2);
		literals.put(hash(l3),l3);
		literals.put(hash(l4),l4);
		literals.put(hash(l5),l5);
		//store.selectLiteralValueIDs(literals, true);
		throw new RuntimeException("NOT YET IMPLEMENTED FOR NEW RDB SCHEMA");
		//Assert.assertTrue(store.select(literals, true).size() == 4);
	}

	@Test
	public void testRemoveAxioms() throws OWLOntologyCreationException
	{
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = manager.getOWLDataFactory();
		OWLOntology o = manager.createOntology();
		Set<RemoveAxiom> hset = new HashSet<RemoveAxiom>();

		OWLDataProperty odp_zip = dataProperty("Zip_Code");
		OWLDataProperty odp_stNo = dataProperty("Street_Number");
		OWLNamedIndividual oni_address2 = individual("Address-2");
		OWLNamedIndividual oni_inq1 = individual("Inquiry-1");
		OWLNamedIndividual oni_part3 = individual("Participant-3");
		OWLLiteral ol_zip = df.getOWLLiteral("33128");
		OWLObjectProperty oop_part = objectProperty("hasParticipant");
		OWLClass oc_stAddr = owlClass("Street_Address");
		
		OWLAxiom daAxiom = df.getOWLDataPropertyAssertionAxiom(odp_zip, oni_address2, ol_zip);
		RemoveAxiom ra = new RemoveAxiom(o, daAxiom);
		hset.add(ra);
		daAxiom = df.getOWLDataPropertyAssertionAxiom(odp_stNo, oni_address2, 101);
		ra = new RemoveAxiom(o, daAxiom);
		hset.add(ra);

		OWLAxiom oaAxiom = df.getOWLObjectPropertyAssertionAxiom(oop_part, oni_inq1, oni_part3);
		ra = new RemoveAxiom(o, oaAxiom);
		hset.add(ra);
		
		OWLAxiom caAxiom = df.getOWLClassAssertionAxiom(oc_stAddr, oni_address2);
		ra = new RemoveAxiom(o, caAxiom);
		hset.add(ra);

//		OntologyChanger oc = new OntologyChanger();
//		boolean removed = oc.removeAxioms(hset);
//		if(removed)
//			System.out.println("All the Axioms have been removed successfully");
//		else 
//			System.out.println("None of the Axioms are removed.");
	}

	@Test
	public void testAddAxioms() throws OWLOntologyCreationException
	{
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = manager.getOWLDataFactory();
		OWLOntology o = manager.createOntology();
		Set<AddAxiom> hset = new HashSet<AddAxiom>();

		OWLDataProperty odp_zip = dataProperty("Zip_Code");
		OWLDataProperty odp_stNo = dataProperty("Street_Number");
		OWLNamedIndividual oni_address2 = individual("Address-2");
		OWLNamedIndividual oni_inq1 = individual("Inquiry-1");
		OWLNamedIndividual oni_part3 = individual("Participant-3");
		OWLNamedIndividual oni_part4 = individual("Participant-4");
		OWLLiteral ol_zip = df.getOWLLiteral("33130");
		OWLObjectProperty oop_part = objectProperty("hasParticipant");
		OWLClass oc_stAddr = owlClass("Street_Address");

		OWLAxiom daAxiom = df.getOWLDataPropertyAssertionAxiom(odp_zip, oni_address2, ol_zip);
		AddAxiom ra = new AddAxiom(o, daAxiom);
		hset.add(ra);
		daAxiom = df.getOWLDataPropertyAssertionAxiom(odp_stNo, oni_address2, 101);
		ra = new AddAxiom(o, daAxiom);
		hset.add(ra);

		OWLAxiom oaAxiom = df.getOWLObjectPropertyAssertionAxiom(oop_part, oni_inq1, oni_part4);
		ra = new AddAxiom(o, oaAxiom);
		hset.add(ra);

		OWLAxiom caAxiom = df.getOWLClassAssertionAxiom(oc_stAddr, oni_address2);
		ra = new AddAxiom(o, caAxiom);
		hset.add(ra);
//
//		OntologyChanger oc = new OntologyChanger();
//		boolean added = oc.addAxioms(hset);
//		if(added)
//			System.out.println("All the Axioms have been added successfully");
//		else 
//			System.out.println("None of the Axioms are added.");
	}
	
	@Test
	public void testAxiomChangers() throws OWLOntologyCreationException {
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = manager.getOWLDataFactory();
		OWLOntology o = manager.createOntology();
		Set<OWLAxiomChange> hset = new HashSet<OWLAxiomChange>();

		OWLDataProperty odp_zip = dataProperty("Zip_Code");
		OWLDataProperty odp_stNo = dataProperty("Street_Number");
		OWLNamedIndividual oni_address2 = individual("Address-2");
		OWLNamedIndividual oni_inq1 = individual("Inquiry-1");
		OWLNamedIndividual oni_part3 = individual("Participant-3");
		OWLNamedIndividual oni_part4 = individual("Participant-4");
		OWLLiteral ol_zip = df.getOWLLiteral("33130");
		OWLObjectProperty oop_part = objectProperty("hasParticipant");
		OWLClass oc_stAddr = owlClass("Street_Address");

		OWLAxiom daAxiom = df.getOWLDataPropertyAssertionAxiom(odp_zip, oni_address2, ol_zip);
		OWLAxiomChange ra = new AddAxiom(o, daAxiom);
		hset.add(ra);
		daAxiom = df.getOWLDataPropertyAssertionAxiom(odp_stNo, oni_address2, 101);
		ra = new RemoveAxiom(o, daAxiom);
		hset.add(ra);


//		OntologyChanger oc = new OntologyChanger();
//		boolean added = oc.axiomChanger(hset);
//		if(added)
//			System.out.println("All the Axioms have been added successfully");
//		else 
//			System.out.println("None of the Axioms are added.");
	}
	
	@Test
	public void testDeltaAxioms() throws OWLOntologyCreationException {
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = manager.getOWLDataFactory();
		OWLOntology o = manager.createOntology();
		OWLNamedIndividual ind = individual("Inquiry-2");
		//Fetch individual from backend. TODO - from cache 
		RelationalOWLPersister.getInstance(fullIri("GICDWTestDatabase")).readIndividualData(o, ind);

		//Changeset from Form Submission
		Set<OWLAxiom> changeset = new HashSet<OWLAxiom>();
//		OWLAxiom ox = df.getOWLDataPropertyAssertionAxiom(dataProperty("hasDateLastModified"), individual("Inquiry-1"), "09/07/2011");
		//OWLAxiom ox = df.getOWLDataPropertyAssertionAxiom(dataProperty("hasDateLastModified"), ind, df.getOWLLiteral("2011-09-19T12:25:54.000-04:00", OWL2Datatype.XSD_DATE_TIME));
		//OWLAxiom ox = df.getOWLDataPropertyAssertionAxiom(dataProperty("hasDateCreated"), ind, df.getOWLLiteral("2011-09-07T06:15:05.000-04:00", OWL2Datatype.XSD_DATE_TIME));
		//changeset.add(ox);
		//ox = df.getOWLDataPropertyAssertionAxiom(dataProperty("hasPrice"), individual("Inquiry-1"), 60);
		//changeset.add(ox);
		OWLAxiom ox = df.getOWLObjectPropertyAssertionAxiom(objectProperty("atAddress"), individual("Inquiry-2"), individual("Address-3"));
		changeset.add(ox);
		
//		OntologyChanger ioChanger = new OntologyChanger();
//		boolean success = ioChanger.deltaAxioms(o, changeset, ind);
//		System.out.println("Successfully persisted: "+success);
	}
	
	@Test
	public void testFetchFromDates() throws OWLOntologyCreationException {
		RelationalOWLPersister persister = RelationalOWLPersister.getInstance(fullIri("GICDWTestDatabase"));
		OWLNamedIndividual ind = individual("Inquiry-1");

		//Fetches list of unique Dates for a given Individual
		List<Date> dates = persister.readIndividualHistory(ind);

		OWLOntologyManager manager = OWL.manager();
		OWLOntology on = manager.createOntology();
		System.out.println("Axiom count before fetching Ontology: "+on.getAxiomCount());
		
		if(dates != null) {
			Date version = dates.get(3);
			System.out.println("Date for which the Ontology is being fetched: "+version.toString());
			//For a given date, fetch an Individual from db.
			persister.readIndividualData(on, ind, version);
		}
		System.out.println("Axiom count after fetching Ontology: "+on.getAxiomCount());
	}
	
	@Test
	public void testPersistUnmappedObject()
	{
		 OWLOntology o;
		 OWLNamedIndividual ind;
		 OperationService op = new OperationService();
		 BOntology bontology = op.createBusinessObject(owlClass("Person"));
		 o = bontology.getOntology();
		 ind  = bontology.getBusinessObject();
		 OWLOntologyManager manager = o.getOWLOntologyManager();
		 OWLDataFactory df = manager.getOWLDataFactory();
		 OWLClassAssertionAxiom Person = df.getOWLClassAssertionAxiom(owlClass("Person"), ind);
		 OWLDataPropertyAssertionAxiom m = df.getOWLDataPropertyAssertionAxiom(dataProperty("Name"), ind, df.getOWLLiteral("Cassius Clay"));
//		 OWLIndividual addressInd = individual( fullIri(owlClass("Street_Address").asOWLClass().getIRI().getFragment() +  OWLRefs.idFactory.resolve().newId(null)));
//		 OWLClassAssertionAxiom address = df.getOWLClassAssertionAxiom(owlClass("Street_Address"), addressInd);
//		 OWLDataPropertyAssertionAxiom c = df.getOWLDataPropertyAssertionAxiom(dataProperty("Street_Number"), addressInd, 101);
//		 OWLObjectPropertyAssertionAxiom d = df.getOWLObjectPropertyAssertionAxiom(objectProperty("Street_Direction"), addressInd, individual("North_West"));
//		 OWLDataPropertyAssertionAxiom e = df.getOWLDataPropertyAssertionAxiom(dataProperty("Street_Name"), addressInd, df.getOWLLiteral("1ST"));
//		 OWLObjectPropertyAssertionAxiom f = df.getOWLObjectPropertyAssertionAxiom(objectProperty("Street_Type"), addressInd, individual("Street_Type_Street"));
//		 OWLObjectPropertyAssertionAxiom g = df.getOWLObjectPropertyAssertionAxiom(objectProperty("Street_Address_City"), addressInd, individual("Miami"));
//		 OWLObjectPropertyAssertionAxiom h = df.getOWLObjectPropertyAssertionAxiom(objectProperty("Street_Address_State"), addressInd, individual("FL"));
//		 OWLObjectPropertyAssertionAxiom i = df.getOWLObjectPropertyAssertionAxiom(objectProperty("Address"), ind, addressInd);
//		 OWLDataPropertyAssertionAxiom j = df.getOWLDataPropertyAssertionAxiom(dataProperty("Zip_Code"), addressInd, df.getOWLLiteral("33128"));
		 //AddAxiom change1 = new AddAxiom(o, a);
		 
		 OWLIndividual miamiDadeCounty = individual("Miami-Dade_County");
		 OWLObjectPropertyAssertionAxiom f = df.getOWLObjectPropertyAssertionAxiom(objectProperty("isMemberOf"), miamiDadeCounty, ind);
		 AddAxiom change2 = new AddAxiom(o, Person);
		 AddAxiom change3 = new AddAxiom(o, m);
		 AddAxiom change4 = new AddAxiom(o, f);
//		 AddAxiom change5 = new AddAxiom(o, c);
//		 AddAxiom change6 = new AddAxiom(o, d);
//		 AddAxiom change7 = new AddAxiom(o, e);
//		 AddAxiom change8 = new AddAxiom(o, f);
//		 AddAxiom change9 = new AddAxiom(o, g);
//		 AddAxiom change10 = new AddAxiom(o, h);
//		 AddAxiom change11 = new AddAxiom(o, i);
//		 AddAxiom change12 = new AddAxiom(o, j);
		 //manager.applyChange(change1);
		 manager.applyChange(change2);
		 manager.applyChange(change3);
		 manager.applyChange(change4);
//		 manager.applyChange(change5);
//		 manager.applyChange(change6);
//		 manager.applyChange(change7);
//		 manager.applyChange(change8);
//		 manager.applyChange(change9);
//		 manager.applyChange(change10);
//		 manager.applyChange(change11);
//		 manager.applyChange(change12);

		 op.getPersister().saveBusinessObjectOntology(o);
		
		
	}
	
	@Test
	public void testElections() throws ClassNotFoundException {
		
		OWLNamedIndividual info = OWL.individual("ElectionsDatabase");
		OWLNamedIndividual dbType = OWL.objectProperty(info, "hasDatabaseType");

		String driverClassName = OWL.dataProperty(dbType, "hasDriver").getLiteral();
		String url = OWL.dataProperty(info, "hasUrl").getLiteral();
		String username = OWL.dataProperty(info, "hasUsername").getLiteral();
		String password = OWL.dataProperty(info, "hasPassword").getLiteral();
		Connection conn = null;
		ResultSet rs = null;
		try
		{
			Class.forName(driverClassName);
			conn = DriverManager.getConnection(url, username, password);

			String query = "select FVRSIDNUM, Name, BIRTHDATE, RESIDENCE_ADDRESS from VOTER where FVRSIDNUM = 112991327";
			rs = conn.createStatement().executeQuery(query);
			while(rs.next()) {
				System.out.println("FVRSIDNUM : " +rs.getString(1));
				System.out.println("Name : " +rs.getString(2));
				System.out.println("BIRTHDATE : " +rs.getTimestamp(3));
				System.out.println("RESIDENCE_ADDRESS : " +rs.getString(4));
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		finally {
			if (rs != null)
				try { rs.close(); }
				catch (Throwable e) {}
			if (conn != null)
				try { conn.close(); }
				catch (Throwable e){}
		}
	}
	
	@Test
	public void testReadMappedServiceCallOnto() throws OWLOntologyCreationException{
		boolean wemadeit = false;
		while (!wemadeit) {
			try {
				OperationService op = new OperationService();
				RelationalOWLPersister persister = op.getPersister();
				OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
				OWLOntology o = manager.createOntology();
				OWLNamedIndividual serviceCall = manager.getOWLDataFactory().getOWLNamedIndividual(IRI.create("http://www.miamidade.gov/bo/ServiceCall/3554#bo"));
				persister.readIndividualData(o, serviceCall);
				DBGUtils.printOntologyFunctional(o);
				wemadeit = true;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
}

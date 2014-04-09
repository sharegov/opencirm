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

import static org.sharegov.cirm.OWL.and;

import static org.sharegov.cirm.OWL.dataProperty;
import static org.sharegov.cirm.OWL.fullIri;
import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.objectProperty;
import static org.sharegov.cirm.OWL.owlClass;
import static org.sharegov.cirm.OWL.some;
import static org.sharegov.cirm.rdb.Sql.SELECT;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
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
import org.semanticweb.owlapi.model.OWLProperty;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.util.OWLEntityCollector;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.OWL;

public class MappingTest
{
	private OWLOntology o;
	private OWLNamedIndividual ind;
	
	@Before
	public void init() throws OWLOntologyCreationException
	{
		createInquiry();
		//createGarbageCompliant();
	}
	

	public void createGarbageCompliant() throws OWLOntologyCreationException
	{
		 OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		 OWLDataFactory df = manager.getOWLDataFactory();
		 o = manager.createOntology();
		 ind  = individual("Garbage_Complaint-1");
		 OWLClassAssertionAxiom garbageComplaint = df.getOWLClassAssertionAxiom(owlClass("Garbage_Missed_Complaint"), ind);
		 //OWLDataProperty hasServiceRequestNumber =  dataProperty("hasServiceRequestNumber");
		 //OWLDataPropertyAssertionAxiom a = df.getOWLDataPropertyAssertionAxiom(hasServiceRequestNumber, ind, df.getOWLLiteral(1));
		 OWLObjectProperty hasServiceRequestStatus = objectProperty("hasServiceRequestStatus");
		 OWLIndividual serviceRequestCompleted = individual("ServiceRequestCompleted");
		 OWLObjectPropertyAssertionAxiom b = df.getOWLObjectPropertyAssertionAxiom(hasServiceRequestStatus, ind, serviceRequestCompleted);
		 OWLIndividual addressInd = individual("Address-1");
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
		 
		 OWLIndividual participant1 = individual("Participant-1");
		 OWLIndividual participant2 = individual("Participant-2");
		 OWLClassAssertionAxiom p1 = df.getOWLClassAssertionAxiom(owlClass("Participant"), participant1);
		 OWLClassAssertionAxiom p2 = df.getOWLClassAssertionAxiom(owlClass("Participant"), participant2);
		 OWLLiteral literal = df.getOWLLiteral("Cassius Clay", OWL2Datatype.XSD_STRING);
		 System.out.println(literal.getDatatype().getIRI().toString());
		 OWLDataPropertyAssertionAxiom m = df.getOWLDataPropertyAssertionAxiom(dataProperty("Name"), participant1, df.getOWLLiteral("Cassius Clay"));
		 OWLDataPropertyAssertionAxiom n = df.getOWLDataPropertyAssertionAxiom(dataProperty("Name"), participant2, df.getOWLLiteral("Joe Frazier"));
		 
		 OWLObjectPropertyAssertionAxiom p = df.getOWLObjectPropertyAssertionAxiom(objectProperty("hasParticipant"), ind, participant1);
		 OWLObjectPropertyAssertionAxiom q = df.getOWLObjectPropertyAssertionAxiom(objectProperty("hasParticipant"), ind, participant2);
		 
		 //AddAxiom change1 = new AddAxiom(o, a);
		 AddAxiom change2 = new AddAxiom(o, b);
		 AddAxiom change3 = new AddAxiom(o, garbageComplaint);
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
	
	public void createInquiry() throws OWLOntologyCreationException
	{
		 OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		 OWLDataFactory df = manager.getOWLDataFactory();
		 o = manager.createOntology();
		 ind  = individual("Inquiry-1");
		 OWLClassAssertionAxiom garbageComplaint = df.getOWLClassAssertionAxiom(owlClass("Inquiry"), ind);
		 //OWLDataProperty hasServiceRequestNumber =  dataProperty("hasServiceRequestNumber");
		 //OWLDataPropertyAssertionAxiom a = df.getOWLDataPropertyAssertionAxiom(hasServiceRequestNumber, ind, df.getOWLLiteral(1));
		 OWLObjectProperty hasServiceRequestStatus = objectProperty("hasServiceRequestStatus");
		 OWLIndividual serviceRequestCompleted = individual("ServiceRequestCompleted");
		 OWLObjectPropertyAssertionAxiom b = df.getOWLObjectPropertyAssertionAxiom(hasServiceRequestStatus, ind, serviceRequestCompleted);
		 OWLIndividual addressInd = individual("Address-2");
		 OWLClassAssertionAxiom address = df.getOWLClassAssertionAxiom(owlClass("Street_Address"), addressInd);
		 OWLDataPropertyAssertionAxiom c = df.getOWLDataPropertyAssertionAxiom(dataProperty("Street_Number"), addressInd, 101);
		 
		 OWLObjectPropertyAssertionAxiom d = df.getOWLObjectPropertyAssertionAxiom(objectProperty("Street_Direction"), addressInd, individual("North_West"));
		 OWLDataPropertyAssertionAxiom e = df.getOWLDataPropertyAssertionAxiom(dataProperty("Street_Name"), addressInd, df.getOWLLiteral("1ST"));
		 OWLObjectPropertyAssertionAxiom f = df.getOWLObjectPropertyAssertionAxiom(objectProperty("Street_Type"), addressInd, individual("Street_Type_Street"));
		 OWLObjectPropertyAssertionAxiom g = df.getOWLObjectPropertyAssertionAxiom(objectProperty("Street_Address_City"), addressInd, individual("Miami"));
		 OWLObjectPropertyAssertionAxiom h = df.getOWLObjectPropertyAssertionAxiom(objectProperty("Street_Address_State"), addressInd, individual("FL"));
		 OWLObjectPropertyAssertionAxiom i = df.getOWLObjectPropertyAssertionAxiom(objectProperty("atAddress"), ind, addressInd);
		 OWLDataPropertyAssertionAxiom j = df.getOWLDataPropertyAssertionAxiom(dataProperty("Zip_Code"), addressInd, df.getOWLLiteral("33128"));
		 OWLDataPropertyAssertionAxiom k = df.getOWLDataPropertyAssertionAxiom(dataProperty("hasDateCreated"), ind, df.getOWLLiteral("2011-09-25T10:05:55.000-04:00", OWL2Datatype.XSD_DATE_TIME));
		 OWLDataPropertyAssertionAxiom l = df.getOWLDataPropertyAssertionAxiom(dataProperty("hasDateLastModified"), ind, df.getOWLLiteral("2011-09-29T15:35:42.000-04:00", OWL2Datatype.XSD_DATE_TIME));
		 
		 OWLIndividual participant1 = individual("Participant-3");
		 OWLIndividual participant2 = individual("Participant-4");
		 OWLIndividual participant3 = individual(IRI.create("http://www.miamidade.gov/users/intranet#%5C3f%5C3f%5C3f%5C00%5C3f%5C3f%5C10%5C41%5C3f%5C26%5C3f%5C60%5C3e%5C3f%5C5f%5C3f"));
		 OWLClassAssertionAxiom p1 = df.getOWLClassAssertionAxiom(owlClass("Participant"), participant1);
		 OWLClassAssertionAxiom p2 = df.getOWLClassAssertionAxiom(owlClass("Participant"), participant2);
		 OWLClassAssertionAxiom p3 = df.getOWLClassAssertionAxiom(owlClass("Participant"), participant3);
		 
		 OWLDataPropertyAssertionAxiom m = df.getOWLDataPropertyAssertionAxiom(dataProperty("Name"), participant1, df.getOWLLiteral("Cassius Clay"));
		 OWLDataPropertyAssertionAxiom n = df.getOWLDataPropertyAssertionAxiom(dataProperty("Name"), participant2, df.getOWLLiteral("Joe Frazier"));
		 OWLDataPropertyAssertionAxiom r = df.getOWLDataPropertyAssertionAxiom(dataProperty("Name"), participant3, df.getOWLLiteral("Syed Abbas"));
		 
		 OWLObjectPropertyAssertionAxiom p = df.getOWLObjectPropertyAssertionAxiom(objectProperty("hasParticipant"), ind, participant1);
		 OWLObjectPropertyAssertionAxiom q = df.getOWLObjectPropertyAssertionAxiom(objectProperty("hasParticipant"), ind, participant2);
		 OWLObjectPropertyAssertionAxiom s = df.getOWLObjectPropertyAssertionAxiom(objectProperty("hasParticipant"), ind, participant3);
		 
		 //AddAxiom change1 = new AddAxiom(o, a);
		 AddAxiom change2 = new AddAxiom(o, b);
		 AddAxiom change3 = new AddAxiom(o, garbageComplaint);
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
		 AddAxiom change23 = new AddAxiom(o, p3);
		 AddAxiom change17 = new AddAxiom(o, m);
		 AddAxiom change18 = new AddAxiom(o, n);
		 AddAxiom change19 = new AddAxiom(o, p);
		 AddAxiom change20 = new AddAxiom(o, q);
		 AddAxiom change21 = new AddAxiom(o, r);
		 AddAxiom change22 = new AddAxiom(o, s);
		 
		 
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
		 manager.applyChange(change21);
		 manager.applyChange(change22);
		 manager.applyChange(change23);
	}
	@Test
	public void testMapping()
	{
//		OWLNamedIndividual info = OWL.individual(fullIri("GICDWTestDatabase"));
//		OWLNamedIndividual dbType = OWL.objectProperty(info, "hasDatabaseType");
//		String driverClassName = OWL.dataProperty(dbType, "hasDriver").getLiteral();
//		String url = OWL.dataProperty(info, "hasUrl").getLiteral();
//		String username = OWL.dataProperty(info, "hasUsername").getLiteral();
//		String password = OWL.dataProperty(info, "hasPassword").getLiteral();
		RelationalStoreExt store =  new RelationalStoreExt(new DataSourceRef(fullIri("GICDWTestDatabase")));
		Map<OWLEntity, DbId> identifiers = new HashMap<OWLEntity, DbId>();
		System.out.println("Identifiers started " + new Date().toString());
		identifiers.putAll(store.selectInsertIDsAndEntitiesByIRIs(o.getClassesInSignature(), true));
		identifiers.putAll(store.selectInsertIDsAndEntitiesByIRIs(o.getIndividualsInSignature(), true));
		identifiers.putAll(store.selectInsertIDsAndEntitiesByIRIs(o.getDataPropertiesInSignature(), true));
		identifiers.putAll(store.selectInsertIDsAndEntitiesByIRIs(o.getObjectPropertiesInSignature(), true));
		identifiers.putAll(store.selectInsertIDsAndEntitiesByIRIs(o.getDatatypesInSignature(true), true));
		System.out.println("Identifiers completed " + new Date().toString());
		Map<OWLClass, OWLNamedIndividual> tableMapping = Mapping.tableMapping(o.getClassesInSignature());
		System.out.println("Table Mappings" + tableMapping.size());
		Map<OWLNamedIndividual, Map<OWLProperty<?, ?>, OWLNamedIndividual>> columnMapping = Mapping.columnMapping(tableMapping);
		System.out.println("Column Mappings" + columnMapping.size());
		System.out.println(new Date().toString());
		Set<OWLNamedIndividual> individuals = o.getIndividualsInSignature();
		Set<OWLNamedIndividual> done = new HashSet<OWLNamedIndividual>();
		System.out.println(columnMapping);
		for(OWLNamedIndividual i : individuals)
		{
			
			OWLNamedIndividual table = Mapping.table(i.getTypes(o), tableMapping);
			if(table != null)
			{
			System.out.println("Table" + table.getIRI());
			System.out.println("Individual" + i);
			}
//			if(!done.contains(i))
//			{
//				System.out.println("merging " + i);
//				done.addAll(store.merge(o, i, identifiers, tableMapping, columnMapping));
//				System.out.println("completed" + done.size());
//			}
		}
		System.out.println("Complete " + new Date().toString());
	}
	
	
	@Test
	public void testMappingWithPersister()
	{
		RelationalOWLPersister persister = RelationalOWLPersister.getInstance(fullIri("GICDWTestDatabase"));
		persister.saveBusinessObjectOntology(o);
	}
	
// hilpold temporarily disabled until core RDB stable.
//	@Test
//	public void testDelete()
//	{
//		OWLNamedIndividual info = OWLUtils.individual(fullIri("GICDWTestDatabase"));
//		OWLNamedIndividual dbType = OWLUtils.objectProperty(info, "hasDatabaseType");
//		String driverClassName = OWLUtils.dataProperty(dbType, "hasDriver").getLiteral();
//		String url = OWLUtils.dataProperty(info, "hasUrl").getLiteral();
//		String username = OWLUtils.dataProperty(info, "hasUsername").getLiteral();
//		String password = OWLUtils.dataProperty(info, "hasPassword").getLiteral();
//		RelationalStore store =  new RelationalStore(url, driverClassName, username, password);
//		Map<OWLEntity, Long> identifiers = new HashMap<OWLEntity, Long>();
//		identifiers.putAll(store.selectIDsAndEntitiesByIRIs(o.getClassesInSignature(), true));
//		identifiers.putAll(store.selectIDsAndEntitiesByIRIs(o.getIndividualsInSignature(), true));
//		identifiers.putAll(store.selectIDsAndEntitiesByIRIs(o.getDataPropertiesInSignature(), true));
//		identifiers.putAll(store.selectIDsAndEntitiesByIRIs(o.getObjectPropertiesInSignature(), true));
//		identifiers.putAll(store.selectIDsAndEntitiesByIRIs(o.getDatatypesInSignature(true), true));
//		Map<OWLClass, OWLNamedIndividual> tableMapping = Mapping.tableMapping(o.getClassesInSignature());
//		Map<OWLNamedIndividual, Map<OWLProperty<?, ?>, OWLNamedIndividual>> columnMapping = Mapping.columnMapping(tableMapping);
//		store.delete(o, ind, identifiers, tableMapping, columnMapping);
//	}
//	
	@Test
	public void testSQL()
	{
		String QUERY  = 
			 SELECT()
			.COLUMN("COLUMN1")
			.FROM("TABLEA")
			.JOIN("TABLEB").ON("COLUMN1", "COLUMN2")
			.WHERE("COLUMN1").GREATER_THAN("?")
			.AND()
			.WHERE("COLUMN2").EQUALS("?")
			.OR()
			.WHERE("COLUMN1 = 1 OR 1=0")
			.SQL();
		System.out.println(QUERY);
	
	}
	
	@Test
	public void testToJson()
	{
		System.out.println(OWL.toJSON(o,ind));
	}
	
	@Test
	public void testMappingToSQL()
	{
		String SELECT  = 
			 SELECT()
			.COLUMN("COLUMN1").AS("ALIAS1")
			.FROM("TABLEA").AS("A")
			.SQL();
		System.out.println(SELECT);
	}
	
	@Test
	public void testMappingHasMany()
	{
		Map<OWLObjectProperty, Map<OWLClass, OWLNamedIndividual>> hasMany = Mapping.hasMany(ind.getObjectPropertyValues(o), o, null,Mapping.tableMapping(o.getClassesInSignature()));
		for (Entry<OWLObjectProperty, Map<OWLClass, OWLNamedIndividual>> entry : hasMany.entrySet())
		{
			System.out.println(entry.getKey());
			System.out.println(entry.getValue());
		}
	}
	
	@Test
	public void testMappingJoin()
	{
		
		OWLNamedIndividual manyTable = individual("CIRM_SR_REQUESTS");
		OWLNamedIndividual joinTable = Mapping.join(individual("CIRM_MDC_ADDRESS"), manyTable);
		System.out.println(joinTable.equals(manyTable));
	}
	
	@Test
	public void testEntityCollector()
	{
		
		OWLEntityCollector collector = new OWLEntityCollector();
		collector.setCollectClasses(true);
		collector.setCollectDatatypes(false);
		collector.setCollectDataProperties(false);
		collector.setCollectIndividuals(false);
		collector.visit(OWL.ontology());
	
		Set<OWLEntity> entities = collector.getObjects();
		for(OWLEntity e : entities)
		{
			System.out.println(e.getIRI());
		}
		
	}
	
	@Test
	public void testIndividuals() throws OWLOntologyCreationException{
		String datatype = "http://www.w3.org/2001/XMLSchema#string";
		OWL2Datatype[] dt = OWL2Datatype.values();
		OWL2Datatype d = OWL2Datatype.valueOf(datatype);
	}
	
	@Test
	public void testIRI() throws OWLOntologyCreationException{
		Set<OWLIndividual> objectPropertyValues = ind.getObjectPropertyValues(objectProperty("hasParticipant"),o);
		for(OWLIndividual participant : objectPropertyValues)
		{
			System.out.println(participant.asOWLNamedIndividual().getIRI().getFragment());
		}
		
	}
	
	@Test
	public void testLiteralCompare()
	{
		OWLLiteral a = OWL.dataFactory().getOWLLiteral(new BigDecimal("1.0").toPlainString(), OWL2Datatype.XSD_FLOAT);
		OWLLiteral b = OWL.dataFactory().getOWLLiteral("1", OWL2Datatype.XSD_FLOAT);
		System.out.println(a.compareTo(b));
		//System.out.println(a.equals(b));
		//System.out.println(new BigDecimal("1").compareTo(new BigDecimal("1.0")));
	}
	
	@Test
	public void testReadIndividual()
	{
		RelationalOWLPersister persister = RelationalOWLPersister.getInstance(fullIri("GICDWTestDatabase"));
		IRI i = IRI.create("http://www.miamidade.gov/bo/PW441/16510#bo");
		OWLOntology o = persister.getBusinessObjectOntology(i);
		System.out.println(OWL.toJSON(o, OWL.individual(i)));
	}
	
	@Test
	public void testEntailment()
	{
		OWLClass type = owlClass(IRI.create("http://www.miamidade.gov/cirm/legacy#BULKYTRA2"));
		OWLClassExpression q = and(owlClass(Refs.OWLClass), some(objectProperty(Concepts.hasTableMapping),owlClass(Concepts.DBTable)));
		NodeSet<OWLNamedIndividual> S = OWL.reasoner().getInstances(q, false);
		for (OWLNamedIndividual i : S.getFlattened())
		{
			System.out.println(i);
			OWLClass mapped = owlClass(i.getIRI());
			System.out.println(type.getIRI());
			System.out.println((type.compareTo(mapped) == 0));
			System.out.println(
					//OWLUtils.reasoner().isEntailmentCheckingSupported(AxiomType.SUBCLASS_OF)
					OWL.reasoner().isEntailed(OWL.dataFactory().getOWLSubClassOfAxiom(type,mapped)
					)
					);
				
					
			
			
		}
	}
	
	
	@Test
	public void testHasOne()
	{
		Map<Map<OWLObjectProperty, OWLNamedIndividual>, OWLNamedIndividual> hasOne = Mapping.hasOne();
		for(Entry entry: hasOne.entrySet())
		{
			System.out.println(entry.getKey());
			System.out.println(entry.getValue());
		}
	}
	
	
	@Test
	public void testSingleton()
	{
		Mapping mapping = Mapping.getInstance();
		for(Entry<?, ?> entry: mapping.getTableMapping(Collections.singleton(owlClass("legacy:ServiceCase"))).entrySet())
		{
			System.out.println(entry);
			for(Entry<?, ?> column :mapping.getColumnMapping().get(entry.getValue()).entrySet())
			{
				System.out.println(column);
			}
		}
	}
	
	
	public static void main(String args[])
	{
		MappingTest t = new MappingTest();
		//t.testEntailment();
		try
		{
			t.init();
			//t.testMappingWithPersister();
			System.out.println(new Date().toString());
			t.testSingleton();
			System.out.println(new Date().toString());
			System.out.println("Again");
			System.out.println(new Date().toString());
			t.testSingleton();
			System.out.println(new Date().toString());
		
		}
		catch (OWLOntologyCreationException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		
//		for(Entry<OWLClass,OWLNamedIndividual> map : Mapping.tableMappings().entrySet())
//		{
//			System.out.println(map.getKey().getIRI());
//			System.out.println(map.getValue().getIRI());
//		}
	}
	
}

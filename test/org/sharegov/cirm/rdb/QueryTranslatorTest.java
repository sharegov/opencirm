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

import static org.sharegov.cirm.OWL.fullIri;


import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

import mjson.Json;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.EntityType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.rest.OperationService;
import org.sharegov.cirm.utils.GenUtils;


public class QueryTranslatorTest
{

	@Test
	public void testPattern()
	{
		Json json = Json.object();
		json.set("type", "Garbage_Missed_Complaint");
		//json.set("hasDateCreated", "between(\"2011-06-15T19:18:06.552Z\",\"2011-06-15T19:18:06.552Z\")");
		json.set("hasDateCreated", "=\"2011-09-19T12:25:54.000-04:00\"");
		json.set("hasDateLastModified", "=\"2011-09-19T12:25:54.000-04:00\"");
		json.set("currentPage",2);
		json.set("itemsPerPage",10);
		
		System.out.println(json);
		OperationService op = new OperationService();
		RelationalStore store = op.getPersister().getStore();
		//RelationalStore store =  RelationalOWLPersister.getInstance(fullIri("GICDWTestDatabase")).getStore();
		Query q = new QueryTranslator().translate(json, store);
		System.out.println(q.getStatement().getSql().SQL());
	}
	
	@Test
	public void testingWithNewRDB() throws SQLException {
		Json json = Json.object();
		//json.set("hasDateCreated", ">= 2012-06-27");
		json.set("type", "legacy:ServiceCase");
		json.set("currentPage",1);
		json.set("itemsPerPage",10);
		json.set("sortBy", "type");
		json.set("sortDirection", "desc");
		
		//json.set("legacy:hasGisDataId", 528603);
/*
		json.set("legacy:hasServiceActivity", 
				Json.object()
				.set("type","legacy:ServiceActivity")
				.set("legacy:isAssignedTo", "=\"boris\"")
		);
*/		
/*
		json.set("hasGeoPropertySet", 
				Json.object()
				.set("type", "GeoPropertySet")
				//.set("GIS_CMAINT", "=\"CO\"")
				.set("GIS_STLGHT", "isNotNull(\"\")")
				//"between(\"2011-06-15T19:18:06.552Z\";
		);
*/

/*
		json.set("legacy:hasServiceCaseActor", 
			Json.object()
			.set("type", "legacy:ServiceCaseActor")
			.set("atAddress", 
				Json.object()
				.set("type", "Street_Address")
				.set("fullAddress", "11399 SW 66TH ST")
			)
		);
*/
		
//		json.set("legacy:hasLegacyId", "11-00315072");

		json.set("atAddress", 
			Json.object()
					.set("type", "Street_Address")
					.set("sortBy", "Zip_Code")
					.set("sortDirection", "desc")
					.set("Zip_Code", 33165)
//					.set("Street_Name", "like(\"1ST\")")
//					.set("fullAddress", "8720 SW 41ST ST")
//					.set("Street_Address_City", Json.object().set("iri", "http://www.miamidade.gov/ontology#Miami_Dade_County"))
		);
		OperationService op = new OperationService();
		RelationalStore store = op.getPersister().getStore();
		try {
			Query q = new QueryTranslator().translate(json, store);
			System.out.println(json.toString());
			System.out.println("********************");
			System.out.println(q.getStatement().getSql().SQL());
			System.out.println("********************");
			System.out.println(q.getStatement().getParameters());
			System.out.println("********************");
			//System.out.println(store.query(q, Refs.tempOntoManager.resolve().getOWLDataFactory()));
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	@Test
	public void testingDispatchLookupASD() throws SQLException {
		Json json = Json.object();
		//json.set("hasDateCreated", ">= 2012-06-27");
		json.set("type", "legacy:ASCRUELT");

		/*		
		json.set("legacy:hasServiceActivity", Json.object()
				.set("type","legacy:ServiceActivity")
				.set("legacy:hasActivity", Json.object().set("iri","http://www.miamidade.gov/cirm/legacy#ASCRUELT_ASANDISP"))
				.set("legacy:hasOutcome", Json.object().set("iri","http://www.miamidade.gov/cirm/legacy#OUTCOME_ASDPUID1"))
		);
		json.set("legacy:hasServiceActivity", Json.object()
				.set("type","legacy:ServiceActivity")
				.set("legacy:hasActivity", Json.object().set("iri","http://www.miamidade.gov/cirm/legacy#ASCRUELT_ASANDISP"))
		);
*/
		json.set("legacy:hasServiceActivity", Json.object()
				.set("type","legacy:ServiceActivity")
				.set("legacy:isAssignedTo", "e132216")
		);
		OperationService op = new OperationService();
		RelationalStore store = op.getPersister().getStore();
		try {
			System.out.println("json is :"+json.toString());
			System.out.println("********************");
			Query q = new QueryTranslator().translate(json, store);
			System.out.println(q.getStatement().getSql().SQL());
			System.out.println("********************");
			System.out.println(q.getStatement().getParameters());
			System.out.println("********************");
			System.out.println(store.query(q, Refs.tempOntoManager.resolve().getOWLDataFactory()));
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void testingFQWithNewRDB() throws SQLException {
		//TOM testing Service Questions in Basic Search 
		Json json = Json.object();
		json.set("type", "legacy:ServiceCase");
		
		json.set("legacy:hasServiceAnswer", 
				Json.object()
					.set("type", "legacy:ServiceAnswer")
					.set("legacy:hasServiceField", 
							Json.object()
							.set("type", "legacy:ServiceField")
							.set("iri", "http://www.miamidade.gov/cirm/legacy#TM74_CASENUM")
						)
					.set("legacy:hasAnswerValue", "=\"201212000035\""
							//Json.object()
							//.set("type", "http://www.w3.org/2001/XMLSchema#string")
							//.set("literal", "=\"201212000035")
					)
		);
		
		System.out.println(json.toString());
		OperationService op = new OperationService();
		RelationalStore store = op.getPersister().getStore();
		Query q = new QueryTranslator().translate(json, store);
		System.out.println(q.getStatement().getSql().SQL());
		System.out.println("***************************");
		System.out.println(store.query(q, Refs.tempOntoManager.resolve().getOWLDataFactory()));
	}

	@Test
	public void testingFQWithOntoTranslatorJson() throws SQLException {
		//TOM testing Service Questions in Basic Search 
		String question;
		Json json = Json.object();
		json.set("type", "legacy:ServiceCase");
		json.set("http://www.miamidade.gov/cirm/legacy#TM74_CASENUM", 
				Json.object()
				.set("datatype", "http://www.w3.org/2001/XMLSchema#integer")
				.set("literal", "=\"201212000035\"")
				);
		
		System.out.println(json.toString());
		OperationService op = new OperationService();
		RelationalStore store = op.getPersister().getStore();
		Query q = new QueryTranslator().translate(json, store);
		System.out.println("***************************");
		System.out.println(json);
		System.out.println("***************************");
		System.out.println(q.getStatement().getSql().SQL());
		System.out.println("***************************");
		//System.out.println(store.query(q));
	}

	@Test
	public void testPattern1()
	{
		Json json = Json.object();
		json.set("type", "AnswerHubMarquee");
		System.out.println(json);
		OperationService op = new OperationService();
		RelationalStore store = op.getPersister().getStore();
		//RelationalStore store =  RelationalOWLPersister.getInstance(fullIri("CIRMTestDatabase")).getStore();
		Query q = new QueryTranslator().translate(json, store);
		System.out.println(q.getStatement().getSql().SQL());
	}
	
	@Test
	public void testTranslate() throws SQLException
	{
		Json json = Json.object();
		json.set("type", "Inquiry");
//		json.set("hasServiceRequestStatus", "ServiceRequestCompleted");
//		json.set("hasServiceRequestStatus", Json.array("ServiceRequestCompleted", "ServiceRequestInProgress"));
//		json.set("hasDateCreated", "=\"2011-09-30T12:25:54.000-04:00\"");
//		json.set("sortBy","hasDateLastModified");
//		json.set("hasDateLastModified", "between(\"2011-09-18T01:01:01.000-04:00\",\"2011-10-20T23:59:55.000-04:00\")");
//		json.set("hasDateLastModified", 
//			Json.array("<\"2011-09-30T12:25:54.000-04:00\"", "=\"2011-09-19T12:25:54.000-04:00\""));
		json.set("atAddress",
				Json.object()
				.set("type", "Street_Address")
				.set("Street_Address_City", "Miami") 
//				.set("Street_Address_City", Json.array("Miami", "Orlando")) 
				.set("Street_Name", "like(\"1ST\")")
//				.set("Street_Name", Json.array("=\"1ST\"", "=\"10TH\""))
				);
//		json.set("hasParticipant", Json.object().set("type", "Participant").set("Name", "=\"Carl Lewis\""));
		//ObjectProperty inside another objectProperty
/*		json.set("hasParticipant", 
					Json.object()
					.set("type", "Participant")
					.set("Name", "=\"Carl Lewis\"")
					.set("atAddress",
						Json.object()
							.set("type", "Street_Address")
							.set("Street_Address_City", "Miami") 
						)
				);
*/
		System.out.println(json);
		OperationService op = new OperationService();
		RelationalStore store = op.getPersister().getStore();
		//RelationalStore store =  RelationalOWLPersister.getInstance(fullIri("GICDWTestDatabase")).getStore();
		Query q = new QueryTranslator().translate(json, store);
		//System.out.println(q.getStatement().getSql().SQL());
		System.out.println(store.queryGetEntities(q, Refs.tempOntoManager.resolve().getOWLDataFactory()).values());
	}
	
	@Test
	public void testMappingHasOne() throws OWLOntologyCreationException
	{
		Map<OWLObjectProperty,OWLNamedIndividual> hasOne = Mapping.hasOne(OWL.individual("CIRM_SR_PARTICIPANT"), OWL.individual("atAddress"));
		System.out.println(hasOne.size());
	}
	
	@Test
	public void testMappingHasMany() throws OWLOntologyCreationException
	{
		Set<OWLObjectProperty> hasMany = Mapping.hasMany(OWL.individual("hasParticipant"));
		System.out.println(hasMany.size());
		for (@SuppressWarnings("rawtypes")
		Iterator iterator = hasMany.iterator(); iterator.hasNext();)
		{
			OWLObjectProperty owlObjectProperty = (OWLObjectProperty) iterator.next();
			System.out.println(owlObjectProperty.getIRI().getFragment());
		}
	}

	@Test
	public void testTime() throws DatatypeConfigurationException
	{
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = manager.getOWLDataFactory();
		OWLLiteral literal = df.getOWLLiteral("2011-09-19T12:25:54.000-04:00", OWL2Datatype.XSD_DATE_TIME);
		System.out.println(literal.toString());
		Date d = DatatypeFactory.newInstance().newXMLGregorianCalendar(literal.getLiteral()).toGregorianCalendar().getTime();
		Timestamp t = new Timestamp(d.getTime());
		System.out.println(t.toString());
	}

	@Test
	public void testTranslation()
	{
			
		Json json = Json.object();
		json.set("type", "Garbage_Missed_Complaint");
		json.set("hasDateCreated", "between(\"2011-06-15T19:18:06.552Z\",\"2011-06-15T19:18:06.552Z\")");
		//json.set("hasDateCreated", "greaterThan(\"2011-06-15T19:18:06.552Z\")");
		//json.set("hasDateCreated", ">= 1");
		System.out.println(json.toString());
		
		RelationalStore store =  RelationalOWLPersister.getInstance(fullIri("GICDWTestDatabase")).getStore();
		QueryTranslator translator = new QueryTranslator();
		Query query = translator.translate(json, store);
		System.out.println(query.getStatement().getSql().SQL());
		
		System.out.println(OWLNamedIndividual.class.getName().toString());
		System.out.println(OWLClass.class.getName().toString());
		System.out.println(OWLDataProperty.class.getName().toString());
		System.out.println(OWLObjectProperty.class.getName().toString());
		System.out.println(OWLDatatype.class.getName().toString());
		System.out.println(IRI.class.getName().toString());
		
		System.out.println(EntityType.ANNOTATION_PROPERTY);
	}
	
	@Test
	public void testQuery() throws SQLException
	{
			
		Json json = Json.object();
		json.set("type", "Garbage_Missed_Complaint");
		json.set("hasDateCreated", "between('12-Jun-2011','18-Jun-2011')");
		//json.set("hasDateCreated", ">= \"12-Jun-2011\"");
		System.out.println(json.toString());
		RelationalStore store = RelationalOWLPersister.getInstance(fullIri("GICDWTestDatabase")).getStore();
		QueryTranslator translator = new QueryTranslator();
		Query query = translator.translate(json, store);
		System.out.println(store.queryGetEntities(query, Refs.tempOntoManager.resolve().getOWLDataFactory()).values());
	}
	
	@Test
	public void testBorisQuery() throws SQLException
	{
		Json json = Json.object();
		json.set("type","Inquiry");
		json.set("sortBy","hasDateLastModified");
		json.set("isCreatedBy","guest");
		json.set("sortDirection","desc");
		json.set("currentPage",1);
		json.set("itemsPerPage",20);
		json.set("hasStatus","*");
		System.out.println(json.toString());
		RelationalStore store =  RelationalOWLPersister.getInstance(fullIri("GICDWTestDatabase")).getStore();
		QueryTranslator translator = new QueryTranslator();
		Query query = translator.translate(json, store);
		System.out.println(query.getStatement().getSql().SQL());
		System.out.println(store.queryGetEntities(query, Refs.tempOntoManager.resolve().getOWLDataFactory()).values());
	}
	
	@Test
	public void testComparable()
	{
			
		IRI iri = fullIri("Service_Request");
		OWLNamedObject o = OWL.dataFactory().getOWLClass(iri);
		System.out.println(o.equals(iri));
	}
	
	@Test
	public void testOntologyItems()
	{
		
		System.out.println(OWL.ontology().containsDataPropertyInSignature(fullIri("Street_Name1")));
	}
	
	@Test
	public void testNoMappingTranslate() throws SQLException
	{
		RelationalStore store = RelationalOWLPersister.getInstance(fullIri("GICDWTestDatabase")).getStore();
		Json json = Json.object();
		json.set("type","Garbage_Missed_Complaint");
		json.set("sortBy","hasDateLastModified");
		//json.set("isCreatedBy","guest");
		json.set("sortDirection","desc");
		json.set("currentPage",1);
		json.set("itemsPerPage",20);
		json.set("hasServiceRequestStatus","*");
		json.set("hasDateCreated", "06/14/2011");
		Json atAddress = Json.object();
		atAddress.set("type", "Street_Address");
		atAddress.set("Street_Name", "1ST");
		atAddress.set("Zip_Code", "33128");
		json.set("atAddress", atAddress);
		System.out.println(json.toString());
		QueryTranslator translator = new QueryTranslator();
		Query query = translator.translate(json, store);
		System.out.println(query.getStatement().getSql().SQL());
		System.out.println(store.queryGetEntities(query, Refs.tempOntoManager.resolve().getOWLDataFactory()).values());
	}
	
	@Test
	public void testNoMappingTranslate2Properties() throws SQLException
	{
		RelationalStore store = RelationalOWLPersister.getInstance(fullIri("GICDWTestDatabase")).getStore();
		Json atAddress = Json.object();
		atAddress.set("type", "Street_Address");
		atAddress.set("Street_Name", "1ST");
		atAddress.set("Zip_Code", "33128");
		System.out.println(atAddress.toString());
		QueryTranslator translator = new QueryTranslator();
		Query query = translator.translate(atAddress, store);
		System.out.println(query.getStatement().getSql().SQL());
		System.out.println(store.queryGetEntities(query, Refs.tempOntoManager.resolve().getOWLDataFactory()).values());
	}
	
	@Test
	public void testPagination()
	{
		Json paginationJson = Json.object();
		Json paginationCriteria = Json.object().set("currentPage", 5).set("itemsPerPage", 20);
		GenUtils.pagination(paginationJson, paginationCriteria);
		System.out.println(paginationJson);
	}
	
	@Test
	public void testSRLookup() throws SQLException
	{
		String typeInd = "ServiceCase";
		int SRID = 8772; 
		//IRI iri = IRI.create("http://www.miamidade.gov/bo/"+typeInd+"/"+SRid+"#bo");
		
		Json json = Json.object();
		json.set("type", "legacy:"+typeInd);
		json.set("boid", SRID);
		System.out.println(json.toString());
		RelationalOWLPersister persister = RelationalOWLPersister.getInstance(fullIri("GICDWTestDatabase"));
		RelationalStore store =  persister.getStore();
		Query q = new QueryTranslator().translate(json, store);
		OWLEntity e = store.queryGetEntities(q, Refs.tempOntoManager.resolve().getOWLDataFactory()).values().iterator().next();
		System.out.println(e.toString());
		
		OWLOntology o = persister.getBusinessObjectOntology(e.getIRI());
		System.out.println("Total No. of axioms : "+o.getAxiomCount());
		
		BOntology bo = new BOntology(o);
		Json finalJson = bo.toJSON();
		System.out.println(finalJson.toString());
	}
	
	
	@Test
	public void testJson()
	{
		
		Json json = Json.read("{\"hasServiceCaseActor\":[{\"Name\":\"Pw Neat Crew\",\"hasUpdatedDate\":\"2012-01-06T05:16:48.000-05:00\",\"hasOrderBy\":\"1.0\",\"hasLegacyId\":\"1132735711\",\"label\":\"bo\",\"hasServiceActor\":{\"participantEntityTable\":\"PERSONS\",\"label\":\"Citizen\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#CITIZEN\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceCaseActor/15159#bo\",\"hasCreatedDate\":\"2012-01-06T05:16:48.000-05:00\"},{\"hasUpdatedDate\":\"2012-01-06T05:16:48.000-05:00\",\"hasOrderBy\":\"3.0\",\"hasLegacyId\":\"1132735771\",\"label\":\"bo\",\"hasServiceActor\":{\"participantEntityTable\":\"PERSONS\",\"label\":\"Commissioner' s Office\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#COMMISH\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceCaseActor/15160#bo\",\"hasCreatedDate\":\"2012-01-06T05:16:48.000-05:00\"},{\"Name\":\"PUBLIC AT LARGE\",\"hasUpdatedDate\":\"2012-01-06T05:16:48.000-05:00\",\"hasOrderBy\":\"2.0\",\"hasLegacyId\":\"1132735708\",\"label\":\"bo\",\"hasServiceActor\":{\"participantEntityTable\":\"PERSONS\",\"label\":\"Originator\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#ORIGIN\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceCaseActor/15167#bo\",\"hasCreatedDate\":\"2012-01-06T05:16:48.000-05:00\"},{\"hasUpdatedDate\":\"2012-01-06T05:16:48.000-05:00\",\"hasOrderBy\":\"4.0\",\"hasLegacyId\":\"1132735774\",\"label\":\"bo\",\"hasServiceActor\":{\"participantEntityTable\":\"PERSONS\",\"label\":\"Employee\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#EMPLOYEE\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceCaseActor/15168#bo\",\"hasCreatedDate\":\"2012-01-06T05:16:48.000-05:00\"}],\"hasServiceAnswer\":[{\"hasAnswerValue\":\"\",\"label\":\"bo\",\"hasServiceField\":{\"label\":\"If Intersection, enter streets in Street 1 and Street 2.\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#PW441_PWNOTE2\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceAnswer/15118#bo\"},{\"hasAnswerValue\":\"\",\"label\":\"bo\",\"hasServiceField\":{\"label\":\"Corridor is a street between 2 cross streets. Enter in Streets 1,2,3.\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#PW441_PWNOTE3\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceAnswer/15119#bo\"},{\"hasAnswerValue\":\"Intersection\",\"label\":\"bo\",\"hasServiceField\":{\"label\":\"Location Type\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#PW441_GIS1\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceAnswer/15120#bo\"},{\"hasAnswerValue\":\"\",\"label\":\"bo\",\"hasServiceField\":{\"label\":\"An Area is surrounded by 4 streets (a block). Use all 4 Street fields.\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#PW441_PWNOTE4\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceAnswer/15150#bo\"},{\"hasAnswerValue\":\"SW 122ND AVE\",\"label\":\"bo\",\"hasServiceField\":{\"label\":\"Street 2\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#PW441_GIS3\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceAnswer/15151#bo\"},{\"hasAnswerValue\":\"\",\"label\":\"bo\",\"hasServiceField\":{\"label\":\"Street 3\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#PW441_GIS4\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceAnswer/15152#bo\"},{\"hasAnswerValue\":\"\",\"label\":\"bo\",\"hasServiceField\":{\"label\":\"Street 4\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#PW441_GIS5\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceAnswer/15153#bo\"},{\"hasAnswerValue\":\"\",\"label\":\"bo\",\"hasServiceField\":{\"label\":\"Corridor Primary Street\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#PW441_GIS6\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceAnswer/15154#bo\"},{\"hasAnswerValue\":\"\",\"label\":\"bo\",\"hasServiceField\":{\"label\":\"Does this road meet any of the following criteria?\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#PW441_DOESTHIS\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceAnswer/15155#bo\"},{\"hasAnswerValue\":\"\",\"label\":\"bo\",\"hasServiceField\":{\"label\":\"Is the Debris Vegetation or Tree?\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#PW441_Q11\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceAnswer/15156#bo\"},{\"hasAnswerValue\":\"\",\"label\":\"bo\",\"hasServiceField\":{\"label\":\"What type of debris? (Ex: glass, rock, furniture, etc.)\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#PW441_Q1\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceAnswer/15157#bo\"},{\"hasAnswerValue\":\"\",\"label\":\"bo\",\"hasServiceField\":{\"label\":\"Do you know how it got there?\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#PW441_Q3\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceAnswer/15158#bo\"},{\"hasAnswerValue\":\"SW 46TH ST\",\"label\":\"bo\",\"hasServiceField\":{\"label\":\"Street 1\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#PW441_GIS2\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceAnswer/15161#bo\"},{\"hasAnswerValue\":\"\",\"label\":\"bo\",\"hasServiceField\":{\"label\":\"Is it interfering with the roadway and / or sidewalk?\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#PW441_Q2\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceAnswer/15162#bo\"},{\"hasAnswerValue\":\"\",\"label\":\"bo\",\"hasServiceField\":{\"label\":\"How much debris is there?\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#PW441_Q4\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceAnswer/15163#bo\"},{\"hasAnswerValue\":\"\",\"label\":\"bo\",\"hasServiceField\":{\"label\":\"Public Works Contact Name (Required}:\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#PW441_CNQ10\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceAnswer/15164#bo\"},{\"hasAnswerValue\":\"\",\"label\":\"bo\",\"hasServiceField\":{\"label\":\"This SR should be addressed in 2 business days.\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#PW441_THISSRSH\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceAnswer/15165#bo\"},{\"hasAnswerValue\":\"\",\"label\":\"bo\",\"hasServiceField\":{\"label\":\"You may call for an update in 2 days.\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#PW441_YOUMAYCA\"},\"iri\":\"http://www.miamidade.gov/bo/ServiceAnswer/15166#bo\"}],\"hasLegacyCode\":\"PW441\",\"hasLegacyId\":\"12-00004030\",\"label\":\"bo\",\"type\":\"PW441\",\"iri\":\"http://www.miamidade.gov/bo/PW441/15117#bo\",\"hasCreatedDate\":\"2012-01-04T10:56:00.000-05:00\",\"hasYCoordinate\":\"506615.03125\",\"hasXCoordinate\":\"856512.375\",\"hasUpdatedDate\":\"2012-01-06T05:45:09.000-05:00\",\"hasServiceActivity\":{\"hasUpdatedDate\":\"2012-01-06T05:40:06.000-05:00\",\"hasOrderBy\":\"1.0\",\"hasCompletedDate\":\"2012-01-04T00:00:00.000-05:00\",\"hasDetails\":\"Miscellaneous debris removed by Neat Crew\",\"hasLegacyId\":\"1132752116\",\"label\":\"bo\",\"iri\":\"http://www.miamidade.gov/bo/ServiceActivity/15181#bo\",\"hasOutcome\":{\"label\":\"OUTCOME_16000916\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#OUTCOME_16000916\"},\"hasActivity\":{\"label\":\"PW-Status Update\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#PW441_PWSTATUS\"},\"hasCreatedDate\":\"2012-01-06T05:40:06.000-05:00\"},\"atAddress\":{\"Zip_Code\":\"33175\",\"Street_Address_State\":{\"label\":\"Florida\",\"iri\":\"http://www.miamidade.gov/ontology#Florida\"},\"Street_Address_City\":{\"label\":\"Miami_Dade_County\",\"iri\":\"http://www.miamidade.gov/ontology#Miami_Dade_County\"},\"Street_Direction\":{\"label\":\"South_West\",\"iri\":\"http://www.miamidade.gov/ontology#South_West\"},\"Street_Name\":\"122ND\",\"Street_Number\":\"4501\",\"hasStreetType\":{\"label\":\"Street_Type_Avenue\",\"iri\":\"http://www.miamidade.gov/ontology#Street_Type_Avenue\"},\"label\":\"bo\",\"iri\":\"http://www.miamidade.gov/bo/Street_Address/15182#bo\"},\"hasStatus\":{\"description4\":\"CLOSED\",\"description6\":\"CSR\",\"label\":\"C-CLOSED\",\"comment\":\"Closed\",\"iri\":\"http://www.miamidade.gov/cirm/legacy#C-CLOSED\"}}");
		//getting scalar properties
		System.out.println("json is object " +json.isObject());
		System.out.println("json is array " + json.isArray());
		System.out.println(json.at("hasLegacyId").asString());
		System.out.println(json.at("hasCreatedDate").asString()); 
		System.out.println(json.at("iri").asString());
		//getting an array within the json
		for(Json participant : json.at("hasServiceCaseActor").asJsonList())
		{
			System.out.println(participant.at("hasLegacyId"));
			System.out.println(participant.at("Name"));
		}
		
	}
	
}

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
package org.sharegov.cirm.rest;

import static mjson.Json.array;
import static mjson.Json.object;
import static mjson.Json.read;
import static org.sharegov.cirm.OWL.dataProperty;
import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.objectProperties;
import static org.sharegov.cirm.OWL.objectProperty;
import static org.sharegov.cirm.OWL.owlClass;
import static org.sharegov.cirm.OWL.reasoner;
import static org.sharegov.cirm.Refs.topOntology;
import static org.sharegov.cirm.rdb.Sql.SELECT;
import static org.sharegov.cirm.rest.OperationService.getPersister;
import static org.sharegov.cirm.utils.GenUtils.ko;
import static org.sharegov.cirm.utils.GenUtils.ok;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import mjson.Json;

import org.hypergraphdb.util.RefResolver;
import org.restlet.Response;
import org.restlet.data.MediaType;
import org.restlet.representation.OutputRepresentation;
import org.restlet.representation.Representation;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLIndividualAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.SWRLBuiltInAtom;
import org.semanticweb.owlapi.model.SWRLDArgument;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.CirmTransactionEvent;
import org.sharegov.cirm.CirmTransactionListener;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.event.EventDispatcher;
import org.sharegov.cirm.gis.GisDAO;
import org.sharegov.cirm.legacy.ActivityManager;
import org.sharegov.cirm.legacy.CirmMessage;
import org.sharegov.cirm.legacy.MessageManager;
import org.sharegov.cirm.legacy.Permissions;
import org.sharegov.cirm.owl.Model;
import org.sharegov.cirm.process.AddTxnListenerForNewSR;
import org.sharegov.cirm.process.ApprovalProcess;
import org.sharegov.cirm.process.AttachSendEmailListener;
import org.sharegov.cirm.process.CreateDefaultActivities;
import org.sharegov.cirm.process.CreateNewSREmail;
import org.sharegov.cirm.process.PopulateGisData;
import org.sharegov.cirm.process.SaveOntology;
import org.sharegov.cirm.rdb.Concepts;
import org.sharegov.cirm.rdb.DBIDFactory;
import org.sharegov.cirm.rdb.Query;
import org.sharegov.cirm.rdb.QueryTranslator;
import org.sharegov.cirm.rdb.RelationalOWLPersister;
import org.sharegov.cirm.rdb.RelationalStore;
import org.sharegov.cirm.rdb.Sql;
import org.sharegov.cirm.rdb.Statement;
import org.sharegov.cirm.stats.CirmStatistics;
import org.sharegov.cirm.stats.CirmStatisticsFactory;
import org.sharegov.cirm.stats.SRCirmStatsDataReporter;
import org.sharegov.cirm.utils.ConcurrentLockedToOpenException;
import org.sharegov.cirm.utils.DueDateUtil;
import org.sharegov.cirm.utils.ExcelExportUtil;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.GisInfoUtil;
import org.sharegov.cirm.utils.JsonUtil;
import org.sharegov.cirm.utils.PDFExportUtil;
import org.sharegov.cirm.utils.PDFViewReport;
import org.sharegov.cirm.utils.RemoveAttachmentsOnTxSuccessListener;
import org.sharegov.cirm.utils.SendEmailOnTxSuccessListener;
import org.sharegov.cirm.utils.SrJsonUtil;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;
import org.sharegov.cirm.workflows.WebServiceCallTask;


@Path("legacy")
@Produces("application/json")
public class LegacyEmulator extends RestService
{
	public static boolean DBG = true;
	public static boolean DBGSQL = false;

	public static final int MAX_CALLWS_ATTEMPTS = 3;
	
	private static Map<String, IRI> hasTypeMappingToXSD;

	private final SRCirmStatsDataReporter srStatsReporter = CirmStatisticsFactory.createServiceRequestStatsReporter(Refs.stats.resolve(), "LegacyEmulator");
	
	private DueDateUtil dueDateUtil = new DueDateUtil();
	private GisInfoUtil gisInfoUtil = new GisInfoUtil();
	private SrJsonUtil srJsonUtil = new SrJsonUtil();
	
	public LegacyEmulator()
	{
	}
	
	/**
	 * Gets a type mapping from question type to xsd type.
	 * Tread Safe double checked.
	 * @author hilpold
	 * @return
	 */
	public Map<String, IRI> getHasTypeMappingToXSD()
	{
		if (hasTypeMappingToXSD == null)
		{
			synchronized (LegacyEmulator.class)
			{
				if (hasTypeMappingToXSD == null) 
					initializeHasTypeMappingToXSD();
			}
		}
		return hasTypeMappingToXSD;
	}

	private static void initializeHasTypeMappingToXSD()
	{
		HashMap<String, IRI> newTypeMappingToXSD = new HashMap<String, IRI>();
		newTypeMappingToXSD.put(LegacyEmulatorConstants.QUESTION_TYPE_CHAR,
				OWL2Datatype.XSD_STRING.getIRI());
		newTypeMappingToXSD.put(LegacyEmulatorConstants.QUESTION_TYPE_CHARLIST,
				OWL2Datatype.XSD_STRING.getIRI());
		newTypeMappingToXSD.put(LegacyEmulatorConstants.QUESTION_TYPE_CHARMULT,
				OWL2Datatype.XSD_STRING.getIRI());
		newTypeMappingToXSD.put(LegacyEmulatorConstants.QUESTION_TYPE_CHAROPT,
				OWL2Datatype.XSD_STRING.getIRI());
		newTypeMappingToXSD.put(LegacyEmulatorConstants.QUESTION_TYPE_DATE,
				OWL2Datatype.XSD_DATE_TIME_STAMP.getIRI());
		newTypeMappingToXSD.put(LegacyEmulatorConstants.QUESTION_TYPE_NUMBER,
				OWL2Datatype.XSD_INTEGER.getIRI());
		newTypeMappingToXSD.put(LegacyEmulatorConstants.QUESTION_TYPE_DOUBLE,
				OWL2Datatype.XSD_DOUBLE.getIRI());
		newTypeMappingToXSD.put(LegacyEmulatorConstants.QUESTION_TYPE_PHONENUM,
				OWL2Datatype.XSD_STRING.getIRI());
		newTypeMappingToXSD.put(LegacyEmulatorConstants.QUESTION_TYPE_TIME,
				OWL2Datatype.XSD_STRING.getIRI());
		// hilpold Thread safety: Set static var as last operation to prevent publishing it before map is fully initialized
		hasTypeMappingToXSD = newTypeMappingToXSD;
	}

	//static String lastqas;
	//static Json srtypelist;

	private static void sortTheFields(List<Json> allFields)
	{
		Collections.sort(allFields, new Comparator<Json>()
		{
			public int compare(Json left, Json right)
			{
				Float f = (left.at("hasOrderBy").asFloat() - right.at(
						"hasOrderBy").asFloat());
				if (f > 0)
					return 1;
				else if (f < 0)
					return -1;
				else
					return 0;
			}
		});
	}

	private Json ontoToJson(BOntology srontology)
	{
		Json result = srontology.toJSON();
		GenUtils.ensureArray(result.at("properties"), "hasServiceActivity");
		GenUtils.ensureArray(result.at("properties"), "hasServiceAnswer");
		GenUtils.ensureArray(result.at("properties"), "hasServiceCaseActor");			
		return result;
	}
	
	private Json getAgencies(OWLClass cl)
	{
		Json result = object();
		// OWLOntology O = MetaService.get().getMetaOntology();
		Set<OWLNamedIndividual> S = reasoner().getInstances(cl, true)
				.getFlattened();
		for (OWLNamedIndividual ind : S)
		{
			Json x = result.at(ind.getIRI().toString(),
					object().set("label", OWL.getEntityLabel(ind)));
			// City_Organization and (hasParentAgency value City_of_Miami)
			Set<OWLNamedIndividual> subs = reasoner()
					.getInstances(
							OWL.and(cl, OWL.has(
									objectProperty("hasParentAgency"), ind)),
							false).getFlattened();
			// reasoner(O).getInstances(cl, false).getFlattened();
			Json A = x.at("agencies", array());
			for (OWLNamedIndividual sind : subs)
			{
				A.add(object("iri", sind.getIRI().toString(), "label",
						OWL.getEntityLabel(sind)));
			}
		}
		return result;
	}

	@GET
	@Path("/getHasTypeMappingToXSD")
	@Produces("application/json")
	public Json getHasTypeMappingToXSDMap()
	{
		Json result = Json.object();
		for (Map.Entry<String, IRI> typeToXSD : getHasTypeMappingToXSD()
				.entrySet())
		{
			result.set(typeToXSD.getKey(), typeToXSD.getValue().toString());
		}
		return result;
	}

	@GET
	@Path("/searchAgencyMap")
	@Produces("application/json")
	public Json getSearchAgencyMap()
	{
		// we want at top-level: the county, all cities, state gov,fed gov and
		// private enterprise (non-gov)
		// i.e. all direct instances of county-gov, city_organization
		return object().with(getAgencies(owlClass("City_Organization")))
				.with(getAgencies(owlClass("County_Organization")))
				.with(getAgencies(owlClass("State_Organization")))
				.with(getAgencies(owlClass("Federal_Organization")));
	}

	// TODO: this could potentially go, it's used in the PDFViewReport, but
	// seems like the use
	// there is opportunistic and not necessary.
	public static void getAllServiceFields(OWLNamedIndividual type,
			List<Json> allServiceAnswers, boolean sort)
	{
		for (OWLNamedIndividual field : objectProperties(type,
				"legacy:hasServiceField"))
		{
			String iri = field.getIRI().toString();
			Json hasServiceAnswer = object("hasAnswerValue",
					object("literal", "", "type", ""), "hasServiceField",
					object("iri", iri));
			String label = OWL.getEntityLabel(field);
			if (label != null)
				hasServiceAnswer.at("hasServiceField").set("label", label);
			OWLLiteral dprop = dataProperty(field, "legacy:hasOrderBy");
			if (dprop != null)
				hasServiceAnswer.set("hasOrderBy",
						Float.parseFloat(dprop.getLiteral()));
			dprop = dataProperty(field, "legacy:hasDataType");
			if (dprop != null)
				hasServiceAnswer.set("hasDataType", dprop.getLiteral());
			dprop = dataProperty(field, "legacy:hasBusinessCodes");
			if (dprop != null)
				hasServiceAnswer.set("hasBusinessCodes", dprop.getLiteral());
			allServiceAnswers.add(hasServiceAnswer);
		}

		if (sort)
			sortTheFields(allServiceAnswers);
	}

	@GET
	@Path("hitme")
	public Json hitme()
	{
		return ok().set("msg", "BOOM!");
	}

	/**
	 * Returns the 'boid' (business object ID) of a service case. Use this method
	 * when you have a string that identifies a service case somehow and you need
	 * the numeric DB identifier.
	 * 
	 * This method will try to do the smart thing depending on its input. If the input is
	 * already a number, that'll be the result. If the looks like a case number (e.g. "13-10004345"),
	 * then a database lookup is performed to find its boid. If it looks like an IRI, then the boid
	 * is extracted from the IRI following the naming convention.
	 * 
	 * @param s A string representation of a long, a case number or an IRI.
	 * @return
	 */
	public long toServiceCaseId(String s)
	{
		try { return Long.parseLong(s); }
		catch (Throwable t) { }
		try 
		{
			IRI iri = IRI.create(s);
			OWL.parseIDFromBusinessOntologyIRI(iri);
		}
		catch (Throwable t) { }
		return lookupServiceCaseId(Json.object("legacy:hasCaseNumber", s, "type", "legacy:ServiceCase"));
	}
	
	/**
	 * <p>
	 * Find a case by its "user friendly" case number. Return <code>Json.nil()</code> if case is not found.
	 * </p>
	 * @param caseNumber Formatted as YY-xxxxxxxx
	 */
	public Json lookupByCaseNumber(String caseNumber)
	{
		QueryTranslator qt = new QueryTranslator();
		RelationalOWLPersister persister = getPersister();
		RelationalStore store = persister.getStore();
		Query q = qt.translate(Json.object("legacy:hasCaseNumber", 
				 caseNumber, "type", "legacy:ServiceCase"), store);
		Set<Long> results = store.query(q, Refs.tempOntoManager.resolve().getOWLDataFactory());
		if (results.size() == 0)
			return Json.nil();
		else
			return lookupServiceCase(results.iterator().next());		
	}
	
	public BOntology lookupServiceCase(String caseID)
	{
		try
		{
			RelationalOWLPersister persister = getPersister();
			OWLEntity entity = persister.getStore().selectEntityByID(
					Long.valueOf(caseID), Refs.tempOntoManager.resolve().getOWLDataFactory());
			if (entity != null)
			{
				BOntology bo = new BOntology(
						persister.getBusinessObjectOntology(entity.getIRI()));
				return bo;
			}
			else
				return null;
		}
		catch (Throwable e)
		{
			e.printStackTrace();
			return null;
		}
	}

	// TODO: the gisAddressData should not be part of the properties, it should be directly 
	// under the object, at the same level as the boid and type and other meta information. The
	// properties should really be only data that is part of the objects (the hasGisDataId is, but not
	// the full gisAddressData thingy). 
	// -- Boris
	public void addAddressData(Json srJson)
	{
		Json props = srJson.at("properties");
		//2017.07.14 Tom: 
		// new case will have hasGisDataId here, so condition never met for newSr.
		// However, performance wise this is better and UI seems ok without it. 
		// If gisAddressData is needed by client,
		// it should be used from in memory locationInfo
		if(props.has("legacy:hasGisDataId") && 
				!props.has("gisAddressData"))
		{
			Json serviceLayersInfo = GisDAO.getGisData(
					props.at("legacy:hasGisDataId").asString());
			if(serviceLayersInfo.has("address"))
				props.set("gisAddressData", serviceLayersInfo.at("address"));
		}
	}

	public BOntology findServiceCaseOntology(long caseid)
	{
			// System.out.println(Arrays.asList(this.getUserGroups()));
		RelationalOWLPersister persister = OperationService.getPersister();
		OWLEntity entity = persister.getStore().selectEntityByID(caseid, Refs.tempOntoManager.resolve().getOWLDataFactory());
		return entity == null ? null : 
			BOntology.isValidBO(entity.getIRI()) ? 
				new BOntology(persister.getBusinessObjectOntology(entity.getIRI())) : null;
	}
	
	@GET
	@Path("/search")
	public Json lookupServiceCase(@QueryParam("id") long caseid)
	{
		try
		{
			BOntology bo = findServiceCaseOntology(caseid);
			if (bo == null)
				return ko("Case not found.");				
			Json result = ontoToJson(bo);			
			addAddressData(result);
			if (isClientExempt() || 
					Permissions.check(individual("BO_View"),
									  individual(bo.getTypeIRI("legacy")),
									  getUserActors()))
				return ok().set("bo", result);
			else
			{
				ThreadLocalStopwatch.error("LE: lookupServiceCase Permission denied to " + caseid + " " + getUserInfo());
				return ko("Permission denied.");
			}
		}
		catch (Throwable e)
		{
			e.printStackTrace();
			return ko(e);
		}
	}

	public long lookupServiceCaseId(Json queryData)
	{
		QueryTranslator qt = new QueryTranslator();
		RelationalOWLPersister persister = getPersister();
		RelationalStore store = persister.getStore();
		Query q = qt.translate(queryData, store);
		Set<Long> results = store.query(q, Refs.tempOntoManager.resolve().getOWLDataFactory());
		if (results.size() == 0)
			return -1;
		else
			return results.iterator().next();		
	}
	
	/**
	 * <p>
	 * Retrieve a single service case by specifying some way to identify it in
	 * the <code>query</code> parameter. 
	 * </p>
	 * 
	 * @param query : Specifies some way to identify the case. If this is a number, 
	 * it is taken to be the <code>boid</code> of the case already. If it is a string,
	 * we still look at how it's format and if we can parse it as a number, we assume it's 
	 * the boid as well. If it doesn't look like a number, it is taken to be the "user friendly" 
	 * case number stored in the <code>hasCaseNumber</code>
	 * property. If the JSON is an object, it is taken to be a query that is executed through the RDBMs 
	 * <code>QueryTranslator</code>. Finally, if the JSON is an array, each element is interpreted
	 * as a separate query and an array result is return for each of them.
	 * 
	 * @return : If found, a Service Request in Json format and 
	 * if not found, a Case not found message.
	 */
	@POST
	@Path("/caseNumberSearch")
	@Produces("application/json")
	@Consumes("application/json")
	public Json lookupServiceCase(Json query)
	{
	    if (query.isBoolean() || query.isNull())
			return ko("Invalid query.");
		Json notfound = ko("Case not found.");
		if (query.isArray())
		{
			Json A = Json.array();
			for (Json x : query.asJsonList())
				A.add(lookupServiceCase(x));
			return A;				
		}
		long caseid = -1;
		if (query.isNumber())
			caseid = -1;
		if (query.isString())
		{
			try { caseid = Long.parseLong(query.asString()); } catch (Throwable t) {}
			if (caseid == -1)
				query = Json.object("legacy:hasCaseNumber", query.asString(), "type", "legacy:ServiceCase");
		}
		if (query.isObject())
		{
			try
			{
				caseid = lookupServiceCaseId(query);
			}
			catch (Exception e)
			{
				e.printStackTrace();
				return ko(e.getMessage());
			}
		}
		return caseid == -1 ? notfound : lookupServiceCase(caseid);		
	}

	private Json getExportData(final Json formData)
	{
		Json metaData = Json.object().set("boid", "SR ID")
				.set("type", "SR Type").set("fullAddress", "Address")
				.set("city", "City").set("zip", "Zip")
				.set("hasStatus", "Status")
				.set("lastActivityUpdatedDate", "Last Activity Date")
				.set("createdDate", "Created Date").set("columns", 8);

		if (formData.has("gisColumnName"))
		{
			metaData.set("gisColumn", formData.at("gisColumnName").asString());
			metaData.set("columns", metaData.at("columns").asInteger() + 1);
		}
		Json data = lookupAdvancedSearch(formData).at("resultsArray");

		if(formData.has("atAddress"))
		{
			if(formData.at("atAddress").has("sortBy"))
				formData.set("sortBy", 
					formData.at("atAddress").at("sortBy").asString());
			if(formData.at("atAddress").has("sortDirection"))
				formData.set("sortDirection",
					formData.at("atAddress").at("sortDirection").asString());
		}
		if (formData.has("sortBy") 
				&& !formData.at("sortBy").asString().equals("")) 
		{
			Collections.sort(data.asJsonList(), new Comparator<Json>()
			{
				public int compare(Json left, Json right)
				{
					String orderBy = formData.at("sortBy").asString();
					if (orderBy.equals("boid") || orderBy.equals("zip"))
					{
						int a = left.at(orderBy).asString().equals("") ? 0
								: left.at(orderBy).asInteger();
						int b = right.at(orderBy).asString().equals("") ? 0
								: right.at(orderBy).asInteger();
						return a - b;
					}
					else if (orderBy.equals("createdDate")
							|| orderBy.equals("lastActivityUpdatedDate"))
					{
						try
						{
							if (left.at(orderBy).asString().equals("")
									&& right.at(orderBy).asString().equals(""))
								return 0;
							else if (left.at(orderBy).asString().equals("")
									|| right.at(orderBy).asString().equals(""))
								return (left.at(orderBy).asString().equals("")) ? -1
										: 1;
							else
							{
								DateFormat df = new SimpleDateFormat(
										"MM/dd/yyyy");
								return df.parse(left.at(orderBy).asString())
										.compareTo(
												df.parse(right.at(orderBy)
														.asString()));
							}
						}
						//Swallowing the exception, if date not parseable, returning 0.
						catch (ParseException e)
						{
							e.printStackTrace();
							return 0;
						}
					}
					else
					{
						if(left.has(orderBy) && right.has(orderBy))
							return left.at(orderBy).asString()
									.compareTo(right.at(orderBy).asString());
						else if(orderBy.contains("legacy:") && left.has(orderBy.split(":")[1]))
						{
							return left.at(orderBy.split(":")[1]).asString()
									.compareTo(right.at(orderBy.split(":")[1]).asString());
						}
						else
							return 0;
					}
//					return 0;
				}
			});
			// Dsc order
			if (formData.has("sortDirection") && 
					formData.at("sortDirection").asString().equals("desc"))
				Collections.reverse(data.asJsonList());
		}
		Json allData = Json.object().set("data", data)
				.set("metaData", metaData);
		return allData;
	}

	@POST
	@Path("/exportToExcel/")
	@Produces("application/vnd.ms-excel")
	public Representation exportToExcel(@FormParam("formData") String formData)
	{
		final Json allData = getExportData(Json.read(formData))
				.set("searchCriteria", Json.read(formData));
		OutputRepresentation or = new OutputRepresentation(
				MediaType.APPLICATION_EXCEL)
		{
			@Override
			public void write(OutputStream out) throws IOException
			{
				try
				{
					ExcelExportUtil e = new ExcelExportUtil();
					e.exportData(out, allData);
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		};
		return or;
	}

	@POST
	@Path("exportToPDF")
	@Produces("application/pdf")
	public Representation exportToPDF(@FormParam("formData") String formData)
	{
		Json form = Json.read(formData);
		final Json allData = getExportData(form);
		OutputRepresentation or = new OutputRepresentation(
				MediaType.APPLICATION_PDF)
		{
			@Override
			public void write(OutputStream out) throws IOException
			{
				try
				{
					PDFExportUtil pdf = new PDFExportUtil();
					pdf.exportData(out, allData);
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		};
		return or;
	}

	/**
	 * Prints the Service Request report in pdf format
	 * @param boid : The boid of service request.
	 * @return
	 */
	@POST
	@Path("printView")
	@Produces("application/pdf")
	public Representation printServiceRequest(@FormParam("boid") String boid)
	{
		Set<Long> boids = new HashSet<Long>();
		boids.add(Long.parseLong(boid));
		try
		{
			ThreadLocalStopwatch.startTop("START LE.printServiceRequest " + boid);
			Representation report = makePDFCaseReports(boids);
			ThreadLocalStopwatch.stop("END LE.printServiceRequest");
			srStatsReporter.succeeded("printServiceRequest", CirmStatistics.UNKNOWN, boid);
			return report;
		}
		catch (Exception e)
		{
			ThreadLocalStopwatch.fail("FAIL LE.printServiceRequest " + boid);
			throw new RuntimeException(e);
		}
	}

	/**
	 * Prints the list of Service Request reports in pdf format.
	 * 
	 * @param formData : The Search Criteria entered in Basic Search Tab
	 * @return
	 */
	@POST
	@Path("srView")
	@Produces("application/pdf")
	public Representation viewServiceRequests(
			@FormParam("formData") String formData)
	{
		Json pattern = Json.read(formData);
		if(!isClientExempt())
		{
			GenUtils.ensureArray(pattern, "type");
			List<Json> searchTypes = pattern.at("type").asJsonList();
			boolean searchAllAllowed = "legacy:ServiceCase".equals(searchTypes.get(0).asString()); 
			if(searchAllAllowed || searchTypes.size() > 1)
			{
				Set<OWLNamedIndividual> permittedTypes = Permissions.
						getAllowedObjectsOfClass(
							Permissions.BO_VIEW, 
							owlClass("legacy:ServiceCase"), 
							getUserActors());
				List<String> permittedTypeList = new ArrayList<String>();
				for(OWLNamedIndividual ind : permittedTypes)
				{	
					String indIRIStr = ind.getIRI().toString();
					if (searchAllAllowed || searchTypes.contains(Json.make(indIRIStr)))
					{
						permittedTypeList.add(indIRIStr);
					}
				}
				pattern.set("type", permittedTypeList);
				if(permittedTypeList.isEmpty())
					throw new RuntimeException("Permission denied to all Service Request Types specified in search parameters");
			}
			else if (searchTypes.size() == 1)
			{
				if (!Permissions.check(individual("BO_View"), 
						individual(searchTypes.get(0).asString()),
						getUserActors()))
					throw new RuntimeException("Permission denied to Service Request Type specified in search parameters.");
			}
			else 
			{
				throw new RuntimeException("No type indication was provided by Basic Search. Please contact tech team.");					
			}
		}
		Set<Long> results = null;
		try
		{
			if (DBG)
			{
				//Trace for 100% CPU issue.
				System.out.println("formData passed into viewServiceRequests : "+formData);
				ThreadLocalStopwatch.getWatch().time("START viewServiceRequestsPDF");
			}
			QueryTranslator qt = new QueryTranslator();
			RelationalStore store = getPersister().getStore();
			Query q = qt.translate(pattern, store);
			results = store.query(q, Refs.tempOntoManager.resolve().getOWLDataFactory());
			//TODO: too many result? goodbye!
			return makePDFCaseReports(results);
		}
		catch (Exception e)
		{
			System.out.println("formData passed into viewServiceRequests : "+formData);
			throw new RuntimeException(e);
		}
		finally 
		{
			if (DBG)
				ThreadLocalStopwatch.getWatch().time("END viewServiceRequestsPDF");
			ThreadLocalStopwatch.dispose();
		}
	}
	
	/**
	 * Creates PDF reports
	 * @param srIDs : Set of Service Request boids whose reports need to be built
	 * @return
	 */
	public Representation makePDFCaseReports(Set<Long> srIDs)
	{
		final List<Long> boids = new ArrayList<Long>(srIDs);
		if(boids.size() > 1)
			Collections.sort(boids);
		OutputRepresentation or = new OutputRepresentation(
				MediaType.APPLICATION_PDF)
		{
			@Override
			public void write(OutputStream out) throws IOException
			{
				ByteArrayOutputStream baOut;
				if(boids.size() > 1)
					baOut = new ByteArrayOutputStream(1000000);
				else
					baOut = new ByteArrayOutputStream(10000);
				PDFViewReport pvr = new PDFViewReport();
				try
				{
					pvr.generateReport(baOut, boids);
				}
				catch (Exception e)
				{
					e.printStackTrace();
					baOut = new ByteArrayOutputStream(10000);
					pvr.errorReport(baOut);
					StringBuilder emailBody = new StringBuilder("");
		    		String emailSubject = "Failed to generate Service Request PDF Report";
			    	emailBody.append("An error occured while trying to generate the PDF report " +
			    			"for one of the following Service Requests:")
		    				 .append("<br>").append(boids).append("<br>");
			    	emailBody.append("The error details are :").append("<br><br>");
			    	
			    	for(StackTraceElement element : e.getStackTrace())
			    	{
						emailBody.append("at ").append(element.getClassName())
							.append(".").append(element.getMethodName())
							.append("(").append(element.getFileName())
							.append(":").append(element.getLineNumber())
							.append(")").append("<br>");
			    	}
			    	
			    	MessageManager.get().sendEmail(
			    			"cirm@miamidade.gov", 
			    			"CIAO-CIRMTT@miamidade.gov", 
			    			emailSubject, emailBody.toString());
				}
				baOut.writeTo(out);
				baOut.close();
			}
		};
		return or;
	}
	
	@POST
	@Path("hetRebateLetter")
	@Produces("application/pdf")
	public Representation getHETRebateLetter(
			@FormParam("applicantInfo") String applicant,
			@FormParam("hasCaseNumber") final String hasCaseNumber,
			@FormParam("isEnglish") String isEnglish) {
		final boolean isEng = Json.read(isEnglish).asBoolean();
		final Json applicantActor = Json.read(applicant);
		OutputRepresentation or = new OutputRepresentation(
				MediaType.APPLICATION_PDF)
		{
			public void write(OutputStream out) throws IOException
			{
				ByteArrayOutputStream baOut = new ByteArrayOutputStream(10000);
				PDFViewReport pvr = new PDFViewReport();
				try
				{
					pvr.generateHETRebateLetter(baOut, applicantActor, hasCaseNumber, isEng);
				}
				catch (Exception e)
				{
					e.printStackTrace();
					baOut = new ByteArrayOutputStream(10000);
					//TODO : pvr.printErrorPage(baOut);
				}
				baOut.writeTo(out);
				baOut.close();
			}
		};
		return or;
	}

	// Remove the pagination and replace the select columns with count(*) for
	// the total count
	private long getSearchResultCount(Query q, RelationalStore store)
			throws SQLException
	{
		q.getStatement().getSql().CLEAR_PAGINATION();
		q.getStatement().getSql().CLEAR_COLUMNS();
		q.getStatement().getSql().COLUMN("count(*)").AS("RecordCount");
		Set<Long> count = store.query(q, Refs.tempOntoManager.resolve().getOWLDataFactory());
		if (count.size() == 1)
			return count.iterator().next();
		else
			return 0;
	}

	/**
	 * Fetches Service Requests from the db which satisfy the given search criteria
	 * @param data - Search Criteria 
	 * @return Search Results
	 */
	// PROTECT
	@POST
	@Path("advSearch")
	@Produces("application/json")
	@Consumes("application/json")
	public Json lookupAdvancedSearch(Json data)
	{
		try
		{
			if (DBG) ThreadLocalStopwatch.startTop("START lookupAdvancedSearch");
			QueryTranslator qt = new QueryTranslator();
			RelationalStore store = getPersister().getStore();
			Json resultsArray = Json.array();
			Query q = null;

			//If Gis Layer/Area is part of the Search criteria then the users
			//want to see the searched column as part of the result as well.
			Json gisSearch = object("isPresent", false);
			if (data.has("gisColumnName"))
			{
				gisSearch.set("isPresent", true).set("gisColumnName",
						data.at("gisColumnName").asString());
				data.delAt("gisColumnName");
			}
			
			if(!isClientExempt())
			{
				GenUtils.ensureArray(data, "type");
				List<Json> searchTypes = data.at("type").asJsonList();
				boolean searchAllAllowed = "legacy:ServiceCase".equals(searchTypes.get(0).asString()); 
				if(searchAllAllowed || searchTypes.size() > 1)
				{
					Set<OWLNamedIndividual> permittedTypes = Permissions.
							getAllowedObjectsOfClass(
								Permissions.BO_VIEW, 
								owlClass("legacy:ServiceCase"), 
								getUserActors());
					List<String> permittedTypeList = new ArrayList<String>();
					for(OWLNamedIndividual ind : permittedTypes)
					{	
						String indIRIStr = ind.getIRI().toString();
						if (searchAllAllowed || searchTypes.contains(Json.make(indIRIStr)))
						{
							permittedTypeList.add(indIRIStr);
						}
					}
					data.set("type", permittedTypeList);
					if(permittedTypeList.isEmpty())
						return ko("Permission denied to all Service Request Types specified in search parameters");
				}
				else if (searchTypes.size() == 1)
				{
					if (!Permissions.check(individual("BO_View"), 
							individual(searchTypes.get(0).asString()),
							getUserActors()))
						return ko("Permission denied to Service Request Type specified in search parameters.");
				}
				else 
				{
					return ko("No type indication was provided by Basic Search. Please contact tech team.");					
				}
					
			}

			
			q = qt.translate(data, store);
		
			
			Set<Long> results = store.query(q, 
					Refs.tempOntoManager.resolve().getOWLDataFactory());
			if (results.size() > 0)
			{
				Sql select = SELECT();
				Statement statement = new Statement();
				Query query = new Query();
				statement.setSql(select);
				query.setStatement(statement);

				select
					.COLUMN("a.SR_REQUEST_ID").AS("SR_REQUEST_ID")
					.COLUMN("i1.IRI").AS("TYPE")
					.COLUMN("addrV.FULL_ADDRESS").AS("FULL_ADDRESS")
					.COLUMN("addrV.ZIP").AS("ZIP")
					.COLUMN("addrV.CITY_SHORT").AS("CITY")
					.COLUMN("a.SR_STATUS").AS("STATUS")
					.COLUMN("acts.COMPLETE_DATE").AS("COMPLETE_DATE")
					.COLUMN("a.CREATED_DATE").AS("CREATED_DATE")
					.COLUMN("a.CASE_NUMBER").AS("CASE_NUMBER")
					.COLUMN("addrV.UNIT").AS("UNIT");
				if (gisSearch.at("isPresent").asBoolean() == true)
					select.COLUMN(
							"CIRM_GIS_INFO."
									+ gisSearch.at("gisColumnName").asString())
							.AS("gisColumn");
				select.FROM("CIRM_SR_REQUESTS a");
				String innerQuery = "(SELECT DISTINCT a1.SR_REQUEST_ID, " +
						"MAX(a1.COMPLETE_DATE) AS COMPLETE_DATE FROM " +
						"CIRM_SR_ACTIVITY a1 GROUP BY a1.SR_REQUEST_ID) acts ";
				select.LEFT_OUTER_JOIN(innerQuery).ON("a.SR_REQUEST_ID",
						"acts.SR_REQUEST_ID");
				select.LEFT_OUTER_JOIN("CIRM_MDC_ADDRESS_VIEW addrV").ON(
						"a.SR_REQUEST_ADDRESS", "addrV.ADDRESS_ID");
				select.LEFT_OUTER_JOIN("CIRM_CLASSIFICATION cl").ON(
						"cl.SUBJECT", "a.SR_REQUEST_ID");
				select.LEFT_OUTER_JOIN("CIRM_IRI i1")
						.ON("cl.OWLCLASS", "i1.ID");
				select.LEFT_OUTER_JOIN("CIRM_GIS_INFO").ON("a.GIS_INFO_ID",
						"CIRM_GIS_INFO.ID");
				select.WHERE("cl.TO_DATE IS NULL");
				select.AND();
				select.WHERE("a.SR_REQUEST_ID");

				Set<String> boids = new HashSet<String>();
				for (Long boid : results)
					boids.add(boid.toString());
				select.IN(boids.toArray(new String[boids.size()]));
				Json viewResults = store.advancedSearch(query);
				
				for (Json j : viewResults.asJsonList())
				{
					OWLNamedIndividual ind = individual("legacy:" + j.at("type").asString());
					j.set("label", OWL.getEntityLabel(ind));
					ind = individual(j.at("Street_Address_City").asString());
					Set<OWLLiteral> dpSet = ind.getDataPropertyValues(
							dataProperty("Name"), topOntology.resolve());
					if (!dpSet.isEmpty())
						j.set("Street_Address_City", dpSet.iterator().next()
								.getLiteral());
					else
					{
						dpSet = ind.getDataPropertyValues(
								dataProperty("Alias"), topOntology.resolve());
						if (!dpSet.isEmpty())
							j.set("Street_Address_City", dpSet.iterator()
									.next().getLiteral());
					}
					resultsArray.add(j);
				}
			}
			return ok().set("resultsArray", resultsArray).set("totalRecords",
					getSearchResultCount(q, store));
		}
		catch (Exception e)
		{
			ThreadLocalStopwatch.fail("FAILED lookupAdvancedSearch with " + e);
			e.printStackTrace();
			return ko(e.getMessage());
		} finally {
			if (DBG) ThreadLocalStopwatch.stop("END lookupAdvancedSearch");
		}
	}

	public Json updateServiceCase(BOntology bontology)
	{
		// if(DBG) DBGUtils.printOntologyFunctional(bontology.getOntology());
//		try
//		{
			getPersister().saveBusinessObjectOntology(bontology.getOntology());
			return ok();
//		}
// Ticket #368 do not swallow exception here!
//		catch (Exception e)
//		{
//			e.printStackTrace(System.err);
//			return ko(e.getMessage());
//		}
	}

	/**
	 * UpdateHistoric allows updating a serviceCase in the past for exempt clients.
	 * e.g. close a locked case at a date provided by a department.
	 * 
	 * @param updatedServiceCase a prefixed service case json with updates applied.
	 * @param updatedDateStr a date in ISO (Genutils) standard.
	 * @return
	 */
	@POST
	@Path("updateHistoric")
	@Consumes("application/json")
	@Produces("application/json")
	public Json updateServiceCaseHistoric(Json updatedServiceCase, @QueryParam("updatedDate") final String updatedDateStr)
	{
		if (!isClientCirmAdmin()) {
			return ko("Permission denied for non CirmAdmin client.");
		}
		ThreadLocalStopwatch.startTop("START updateServiceCaseHistoric");
		System.out.println(updatedServiceCase.toString());
		Date updatedDate = GenUtils.parseDate(updatedDateStr);
		Json result = updateServiceCase(updatedServiceCase, updatedDate, "department");
		if (result.is("ok", true)) {
			srStatsReporter.succeeded("updateServiceCaseHistoric restcall", updatedServiceCase);
		} else {
			srStatsReporter.failed("updateServiceCaseHistoric restcall", updatedServiceCase, result.at("error").toString(), result.at("stackTrace").toString());
		}
		ThreadLocalStopwatch.stop("END updateServiceCaseHistoric");
		return result;
	}
	
	@POST
	//@Encoded 2372 Java8 hilpold
	@Path("update")
	@Produces("application/json")
	public Json updateServiceCase(@FormParam("data") final String formData)
	{
		ThreadLocalStopwatch.startTop("START updateServiceCase data");
		Json form = read(formData);
		
		// Not sure if this is needed anymore (Boris)
		if (form.at("properties").has("legacy:hasDepartmentError"))
			form.at("properties").delAt("legacy:hasDepartmentError");
		
		if (!isClientExempt()
				&& !Permissions.check(individual("BO_Update"),
						individual(form.at("type").asString()),
						getUserActors())) {
			ThreadLocalStopwatch.stop("DENIED updateServiceCase data");
			return ko("Permission denied.");
		}
		else
		{
			Json result = updateServiceCase(form, "cirmuser");
			if (result.is("ok", true)) 
			{
				ThreadLocalStopwatch.stop("END updateServiceCase data");
				srStatsReporter.succeeded("updateServiceCase restcall", form);
			}
			else
			{
				ThreadLocalStopwatch.fail("FAIL updateServiceCase data");
				srStatsReporter.failed("updateServiceCase restcall", form, result.at("error").toString(), result.at("stackTrace").toString());
			}
			return result;
		}
	}
	
	
	private Json findField(Json fields, String code)
	{
		if (fields.isArray()) {
			for (Json f : fields.asJsonList()) {
				if (f.at("hasServiceField").is("hasLegacyCode", code)) {
					return f;
				}
			}
			//not found return nil
		} else if (fields.isObject()) {
			if (fields.at("hasServiceField").is("hasLegacyCode", code)) {
				return fields;
			} else {
				//not found return nil
			}
		} else {
			//primitive return nil
		}
		//
		return Json.nil();
	}
	
	private boolean compareFieldValues(Json leftFields, String leftCode, Json rightFields, String rightCode)
	{
		Json left = findField(leftFields, leftCode);
		Json right = findField(rightFields, rightCode);
		if (left.equals(right))
			return true;
		else
			return left.isObject() && right.isObject() && left.is("hasAnswerValue", right.at("hasAnswerValue"));
	}
	
	/**
	 * Have x or y coordinates changed by more than 0.01, did a coord value change to null or a null coord change to a value.
	 * 
	 * @param existingSr
	 * @param newSr
	 * @return
	 */
	public boolean hasCoordinatesUpdated(Json existingSr, Json newSr) {
		Double existingX = srJsonUtil.gethasXCoordinate(existingSr);
		Double existingY = srJsonUtil.gethasYCoordinate(existingSr);
		Double newX = srJsonUtil.gethasXCoordinate(newSr);
		Double newY = srJsonUtil.gethasYCoordinate(newSr);
		//X updated?
		boolean updatedX = false;
		if (existingX != null) {
			if (newX != null) {
				updatedX = Math.abs(newX - existingX) > 0.01;
			} else {
				//old not null, new null -> bad case
				updatedX = true;
			}
		} else {
			//old null, new not null or null
			updatedX = newX != null;
		}
		//Y updated?
		boolean updatedY = false;
		if (existingY != null) {
			if (newY != null) {
				updatedY = Math.abs(newY - existingY) > 0.01;
			} else {
				//old not null, new null -> bad case
				updatedY = true;
			}
		} else {
			//old null, new not null or null
			updatedY = newY != null;
		}
		return (updatedX || updatedY);
	}
	
	public boolean hasAddressUpdated(Json existing, Json newdata)
	{	
		if (hasCoordinatesUpdated(existing, newdata)) {
			return true;
		}
		// FIXME: this address field by field comparison is not accurate because
		// sometimes the state will include the label, sometimes not so non-essential
		// differences are picked up.
		if (existing.at("properties").has("atAddress"))
		{
			if (newdata.at("properties").has("atAddress"))
			{
				Json x = OWL.resolveIris(existing, null).at("properties").at("atAddress").dup().delAt("iri");
				Json y = OWL.resolveIris(newdata, null).at("properties").at("atAddress").dup().delAt("iri");
				x = JsonUtil.apply(x, new JsonUtil.RemoveProperty("label"));
				y = JsonUtil.apply(y, new JsonUtil.RemoveProperty("label"));
				if (!x.equals(y))
					return true;
			}
		}
		else if (newdata.at("properties").has("atAddress")) {
			return true;
		}
		Json lans = existing.at("properties").at("hasServiceAnswer");
		Json rans = newdata.at("properties").at("hasServiceAnswer");
		if (lans != null && rans != null)
		{
			if (!compareFieldValues(lans, "GIS2", rans, "GIS2") ||
				!compareFieldValues(lans, "GIS3", rans, "GIS3") ||
				!compareFieldValues(lans, "GIS4", rans, "GIS4") ||
				!compareFieldValues(lans, "GIS5", rans, "GIS5"))
				return true;
		}
		return false;
	}
	
	public Json updateServiceCaseTransaction(final Json newValue, 
											 final Json existing,
											 Date updatedDate,
											 final List<CirmMessage> emailsToSend,
											 final String originator)
	{
		final Json serviceCase = newValue.dup();
		Json dbActs = array();
		Json uiActs = array();
		Json newActivities = array();
		if (updatedDate == null)
			updatedDate = getPersister().getStore().getStoreTime();
		serviceCase.at("properties").set("hasDateLastModified", GenUtils.formatDate(updatedDate));
		final Json actorEmails = serviceCase.at("properties").atDel("actorEmails");
		final Json hasRemovedAttachment = serviceCase.at("properties").atDel("hasRemovedAttachment");
		Long boid = serviceCase.at("boid").asLong();
		if (serviceCase.at("properties").has("legacy:hasServiceActivity"))
			for (Json a : serviceCase.at("properties").atDel("legacy:hasServiceActivity").asJsonList())
		{
			if (a.has("iri"))
				uiActs.add(a);
			else if (!a.has("iri"))
				newActivities.add(a);
		}
		serviceCase.at("properties").set("legacy:hasServiceActivity", uiActs);
		// delete any removed Images
		tryDeleteAttachments(hasRemovedAttachment);
		//fetch DB Activities to compare/update against UI Activities
		//T2
		if(uiActs.asJsonList().size() > 0)
		{
			 if(existing.at("properties").at("hasServiceActivity").isArray())
				 dbActs = existing.at("properties").at("hasServiceActivity");
			 else
				 dbActs.add(existing.at("properties").at("hasServiceActivity"));
		}
		final BOntology bontology = BOntology.makeRuntimeBOntology(serviceCase);
		ActivityManager mngr = new ActivityManager();
		OWLNamedIndividual currentStatus = individual(existing.at("properties"),"hasStatus");
		OWLNamedIndividual newStatus = individual(serviceCase.at("properties"),"legacy:hasStatus");
		OWLLiteral srModifiedBy = bontology.getDataProperty("isModifiedBy");
		
		///T2 END
		//06-20-2013 syed - Check for a status change.
		if(currentStatus != null && 
				!currentStatus.equals(newStatus)) 
		{
			if ("cirmuser".equals(originator) 
				&& currentStatus.equals(individual("legacy:O-LOCKED"))
				&& newStatus.equals(individual("legacy:O-OPEN"))) 
			{
				//2016.07.08 hilpold mdcirm 2639 
				// We prevent a very specific concurrent SR modification problem:
				// only if a user saves an SR in OPEN status, which is already locked, we prevent 
				// an overwrite of interface changes.
				throw new ConcurrentLockedToOpenException();
			} else {
				mngr.changeStatus(currentStatus, newStatus, updatedDate, (srModifiedBy != null)?srModifiedBy.getLiteral():null, bontology, emailsToSend);
				if (individual("legacy:O-LOCKED").equals(newStatus)) {
					mngr.createAutoOnLockedActivities(bontology, new Date(), emailsToSend);
				}
			}
		}

		//Update those existing Activities for which Outcome is set in current request
		if(uiActs.asJsonList().size() > 0)
			updateExistingActivities(uiActs, dbActs, mngr, bontology, boid, emailsToSend);

		
		String srModifiedByStr = srModifiedBy == null? null : srModifiedBy.getLiteral();
		//06-20-2013 syed - set the createdBy to the SR modifier.
		for (final Json eachActivity : newActivities.asJsonList())
		{
			final OWLNamedIndividual activity = individual(eachActivity.at("legacy:hasActivity")
																 .at("iri").asString());
			String details = eachActivity.has("legacy:hasDetails") ? eachActivity
					.at("legacy:hasDetails").asString() : null;
			String assignedTo = eachActivity.has("legacy:isAssignedTo") ? eachActivity
					.at("legacy:isAssignedTo").asString() : null;
			String actCreatedBy = eachActivity.has("isCreatedBy") ? eachActivity
							.at("isCreatedBy").asString() : srModifiedByStr;					
			Json hasOutcome = eachActivity.has("legacy:hasOutcome") ? eachActivity
					.at("legacy:hasOutcome") : null;
			java.util.Date createdDate = eachActivity.has("hasDateCreated") ? 
							GenUtils.parseDate(eachActivity.at("hasDateCreated").asString()) : null; 
			java.util.Date completedDate = eachActivity.has("legacy:hasCompletedTimestamp") ? 
						GenUtils.parseDate(eachActivity.at("legacy:hasCompletedTimestamp").asString()) : null; 
			if (hasOutcome == null && completedDate == null)
				mngr.createActivity(activity, 
								    details, 
								    assignedTo, 
								    bontology,
								    createdDate, 
								    actCreatedBy,
								    emailsToSend);
			else
			{
				OWLNamedIndividual outcome = hasOutcome != null ? individual(hasOutcome.at("iri").asString()) : null;
				mngr.createActivity(activity, 
									outcome, 
									details, 
									assignedTo, 
									bontology,
									createdDate,
									completedDate,
									actCreatedBy,
									emailsToSend);
			}
			if (!eachActivity.has("legacy:hasOutcome") && eachActivity.is("legacy:isAccepted", true))
			{
				OWLNamedIndividual activityTypeInd = individual(eachActivity.at("legacy:hasActivity").at("iri").asString());
				Set<OWLNamedIndividual> outcomes = reasoner().getObjectPropertyValues(
						activityTypeInd, objectProperty("legacy:hasDefaultOutcome")).getFlattened();
				if(outcomes.size() > 0)
				{
					eachActivity.set("hasOutcome", OWL.toJSON(outcomes.iterator().next()));
				}
			}
			CirmTransaction.get().addTopLevelEventListener(new CirmTransactionListener() {
			    public void transactionStateChanged(final CirmTransactionEvent e)
			    {
			    	if (e.isSucceeded())
						EventDispatcher.get().dispatch(
								OWL.individual("legacy:ServiceCaseNewActivityEvent"),
	                            activity,			                 
	                            OWL.individual("BO_New"),
                                Json.object("serviceCase", serviceCase,
                                            "activity", eachActivity,
                                            "originator", originator));			
			    }
			});
		}		
		if (hasAddressUpdated(existing, newValue)) {
			populateGisData(serviceCase, bontology);
		}
		Json updateResult = updateServiceCase(bontology);
		if (updateResult.is("ok", true))
		{
			Json result = bontology.toJSON();
			ThreadLocalStopwatch.now("START Adding address data");			
			addAddressData(result);
			ThreadLocalStopwatch.now("END Adding address data");			
			//Register Top Level Tx fire Update event
			CirmTransaction.get().addTopLevelEventListener(new CirmTransactionListener() {
			    public void transactionStateChanged(final CirmTransactionEvent e)
			    {
			    	if (e.isSucceeded())
			    	{
			    		try
			    		{
			    			EventDispatcher.get().dispatch(
			    					OWL.individual("legacy:ServiceCaseUpdateEvent"), 
						            bontology.getBusinessObject(), 
						            OWL.individual("BO_Update"),
						            Json.object("case", result.dup()));
			    		}
			    		catch (Exception ex)
			    		{
							ThreadLocalStopwatch.error("Error updateServiceCaseTransaction - Failed to dispatch update event for " + bontology.getObjectId());
							ex.printStackTrace();
			    		}
			    	}
			    }
			});
			//
			//START : Emails to customers
			if(actorEmails != null)
			{
				sendEmailToCustomers(bontology, 
						actorEmails.asJsonList(), emailsToSend, 
						result.at("properties").at("hasCaseNumber").asString(),
						OWL.getEntityLabel(individual("legacy:"+result.at("type").asString()))
				);
			}
			//END : Emails to customers
			return ok().set("bo", result);
		}
		else
			return updateResult;
	}
	
	public Json updateServiceCase(final Json serviceCaseParam, final String originator) {
		return updateServiceCase(serviceCaseParam, null, originator);
	}
	
	public Json updateServiceCase(final Json serviceCaseParam, final Date updateDate, final String originator)
	{
		if (originator == null) throw new NullPointerException("originator");
		//wrap the entire update in a transaction block.
		try
		{
			ThreadLocalStopwatch.start("START updateServiceCase (str)");
			final List<CirmMessage> emailsToSend = new ArrayList<CirmMessage>();
			Json result =  Refs.defaultRelationalStore.resolve().txn(new CirmTransaction<Json>() {
			public Json call()
			{
				emailsToSend.clear();
				CirmTransaction.get().addTopLevelEventListener(new SendEmailOnTxSuccessListener(emailsToSend));
				//Saving the current context as we are losing when 
				//using another get/post internally 
				Response current = Response.getCurrent();
				Long boid = serviceCaseParam.at("boid").asLong();
				Json bo = findServiceCaseOntology(boid).toJSON();
				Json result = updateServiceCaseTransaction(serviceCaseParam, bo, updateDate, emailsToSend, originator);
				Response.setCurrent(current);
				return result;
			}});			
			ThreadLocalStopwatch.stop("END updateServiceCase (str)");
			return result;	
		}
		catch (Throwable e)
		{
			if (CirmTransaction.isExecutingOnThisThread()) {
				//We're still inside a higher level transaction and must not hide/catch a potentially retriable exception.
				throw e;
			} else {
				//Top level transaction completed, all possible retries completed, return error json. 
    			ThreadLocalStopwatch.fail("FAIL updateServiceCase (str)");
    			System.out.println("formData passed into updateServiceCase: "+ serviceCaseParam.toString());
    			e.printStackTrace();
    			return ko(e);
			}
		}
	}
	
    /**
     * Augment a business object ontology with metadata axioms.
     * 
     */
    public BOntology addMetaDataAxioms(BOntology bo) throws OWLOntologyCreationException
    {
        OWLOntology ontology = bo.getOntology();
    	IRI verboseiri = IRI.create(ontology.getOntologyID().getOntologyIRI().toString() + "/verbose");        
        OWLOntologyManager manager = Refs.tempOntoManager.resolve();
        OWLOntology result = manager.getOntology(verboseiri);
        if (result != null)
        	manager.removeOntology(result);
        result = manager.createOntology(
            IRI.create(ontology.getOntologyID().getOntologyIRI().toString() + "/verbose"), 
            Collections.singleton(ontology));
        OWLDataFactory factory = manager.getOWLDataFactory();
        Set<OWLNamedIndividual> individuals = result.getIndividualsInSignature();
        List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
        for (OWLNamedIndividual ind : individuals)
        {
            // Idk if this is the best way to check if an individual is declared
            // in meta.
            // OWLReasoner doesn't provide facilities to retrieve axioms for an
            // individual.
            // and for now we are only interested in adding legacy axioms.
            
            // If the individual lives in the CiRM namespace, we add all information about it.
            if (OWL.ontology().containsEntityInSignature(ind, true))
            {
            	ind = OWL.individual(ind.getIRI());            	
            	for (OWLOntology O : OWL.ontologies())
            	{
            		for (OWLIndividualAxiom axiom : O.getDataPropertyAssertionAxioms(ind))
            			changes.add(new AddAxiom(result, axiom));
            		for (OWLObjectPropertyAssertionAxiom axiom : O.getObjectPropertyAssertionAxioms(ind))
            			// I'm not sure why we are skipping those two properties. Perhaps they are not needed
            			// but are they harmful? That logic takes away the generality of the method.
            			if (!axiom.getProperty().equals(objectProperty("legacy:hasLegacyInterface")) &&
            				!axiom.getProperty().equals(objectProperty("legacy:hasAllowableEvent")))
            			changes.add(new AddAxiom(result, axiom));
            	}
           }
            else
            {
                // add boid to businessObject in the BOntology
                OWLDataProperty boid = factory.getOWLDataProperty(Model.legacy("boid"));
                if (ind.getDataPropertyValues(boid, result).isEmpty())
                {
                    Long id = Long.valueOf(bo.getObjectId()); // identifiers.get(ind);
                    // 1-15-2013 save the round trip to the DB and grab the id
                    // from the onto.
                    if (id != null)
                        changes.add(new AddAxiom(
                                result,
                                factory.getOWLDataPropertyAssertionAxiom(
                                        boid,
                                        ind,
                                        factory.getOWLLiteral(
                                                id.toString(),
                                                factory.getOWLDatatype(OWL2Datatype.XSD_INT
                                                        .getIRI())))));
                }
            }
        }
        if (changes.size() > 0)
            manager.applyChanges(changes);
        BOntology newBO = new BOntology(result);
        return newBO;
    }
	
	/**
	 * If an outcome is set for any EXISTING ACTIVITY, then have to
	 * update that particular Activity via ActivityManager.updateActivity
	 * 
	 * EXISTING ACTIVITY : An Activity which is already present when the 
	 * user loads the Service Request in the UI.
	 * 
	 * @param uiActivities : List of all existing Activities from UI
	 * @param dbActivities : List of all existing Activities from DB
	 * @param manager : ActivityManager 
	 * @param bontology : The Service Request's business ontology
	 * @param boid : Unique identifier of the Service Request
	 */
	private void updateExistingActivities(
			Json uiActivities,
			Json dbActivities,
			ActivityManager manager, 
			BOntology bontology, 
			Long boid, List<CirmMessage> messages)
	{
		for(Json dbAct : dbActivities.asJsonList())
		{
			String dbActIRI = dbAct.at("iri").asString();
			String dbOutcomeIRI = dbAct.has("hasOutcome") ? 
					dbAct.at("hasOutcome").isObject() ? 
							dbAct.at("hasOutcome").at("iri").asString() :  
								dbAct.at("hasOutcome").asString() : null;
			for(Json uiAct : uiActivities.asJsonList())
			{
				String uiActIRI = uiAct.at("iri").asString();
				String activityType;
				if (uiAct.at("legacy:hasActivity").isObject())
				{
					activityType = uiAct.at("legacy:hasActivity").at("iri").asString(); 
				}
				else 
				{
					activityType = uiAct.at("legacy:hasActivity").asString(); 
				}
				String modifiedBy = uiAct.has("isModifiedBy") ? 
									uiAct.at("isModifiedBy").asString() : null;
				if(dbActIRI.equals(uiActIRI))
				{
					String uiOutcomeIRI;
					if (uiAct.has("legacy:hasOutcome"))
					{
						if (uiAct.at("legacy:hasOutcome").isObject()) 
						{
							uiOutcomeIRI = uiAct.at("legacy:hasOutcome").at("iri").asString();
						}
						else 
						{
							uiOutcomeIRI = uiAct.at("legacy:hasOutcome").asString();
						}
					}
					else
					{
						uiOutcomeIRI = null;
					}
					String details = uiAct.has("legacy:hasDetails") ?
						uiAct.at("legacy:hasDetails").asString() : null;
					String assignedTo = uiAct.has("legacy:isAssignedTo") ?
						uiAct.at("legacy:isAssignedTo").asString() : null;
					boolean isAccepted = uiAct.has("legacy:isAccepted") ? 
						uiAct.at("legacy:isAccepted").toString().equals("true") : false;
					if(dbOutcomeIRI == null)
					{
						if((uiOutcomeIRI == null && isAccepted) || uiOutcomeIRI != null)
						{
							manager.updateActivity(activityType, uiActIRI, uiOutcomeIRI, 
									details, assignedTo, modifiedBy, isAccepted, bontology, messages);
						}
					}
					else
						continue;
				}
				else
					continue;
			}
		}
	}

	/**
	 * Try to delete attachments (files and images) if serviceCase json has a hasRemovedAttachment property.<br>
	 * Always called  inside of a transaction.
	 * @param serviceCase
	 */
	public void tryDeleteAttachments(Json hasRemovedAttachment)
	{	
		if(hasRemovedAttachment != null && hasRemovedAttachment.isArray()) {
			List<Json> hasRemovedAttachmentList = hasRemovedAttachment.asJsonList();
			if (hasRemovedAttachmentList.size() >  0) {
				CirmTransaction.get().addTopLevelEventListener(new RemoveAttachmentsOnTxSuccessListener(hasRemovedAttachmentList));
			}
		} else {
			//programming error
		}
	}
	
	/**
	 * Updates bOntology by replacing or adding legacy:hasGisDataId long and hasFolio long, stores in GIS_INFO table,
	 * returns full locationInfo. 
	 * Prefer to use outside transaction, because a http get is issued to MDCGIS.
	 * 
	 * @param legacyForm
	 * @param bontology
	 * @return
	 */
	public Json populateGisData(Json legacyForm, BOntology bontology)
	{
		ThreadLocalStopwatch.getWatch().time("START populateGisData");
		Json result;
		if (legacyForm.at("properties").has("hasXCoordinate")
				&& legacyForm.at("properties").has("hasYCoordinate")) {
			double x = legacyForm.at("properties").at("hasXCoordinate").asDouble();
			double y = legacyForm.at("properties").at("hasYCoordinate").asDouble();
			//Retry up to 2x 500 ms pause:
			Json locationInfo = Refs.gisClient.resolve().getLocationInfo(x, y, null, 3, 500);
			result = populateGisDataInternal(legacyForm, bontology, locationInfo);
		} else {
			result = Json.nil();
		}
		return result;
	}
	
	/**
	 * Updates bOntology by replacing or adding legacy:hasGisDataId long and hasFolio long, stores in GIS_INFO table,
	 * returns modified locationInfo for MD-PWS cases if Intersection, Corridor or Area location type.
	 * Prefer to use inside transaction, but requires locationInfo, best acquired before tx start to avoid repeated http calls on retry.
	 * 
	 * @param legacyForm
	 * @param bontology
	 * @param locationInfo
	 * @return
	 */
	private Json populateGisDataInternal(Json legacyForm, BOntology bontology, Json locationInfo)
	{
		// Checking for x and y properties before adding as Address it not
		// mandatory anymore
		if (locationInfo != null && locationInfo != Json.nil())
		{
			ThreadLocalStopwatch.getWatch().time("START populateGisDataInternal");
			//1. MD-PWS only: Extended info for Intersection/Corridor/Area location types
			if (isMdPwsInterfaceCase(legacyForm)) {
					Json scase = ontoToJson(bontology);
					if (legacyForm.at("properties").has("hasLegacyInterface"))
    				scase.at("properties").set("hasLegacyInterface", 
    										   legacyForm.at("properties").at("hasLegacyInterface"));
    			Json extendedInfo = Json.nil();
    			try
    			{
    				extendedInfo = Refs.gisClient.resolve().getInformationForCase(scase);
    			}
    			catch (Throwable t)
    			{
    				extendedInfo = Json.object().set("error", t.toString());
    			}
    			if (!extendedInfo.isNull())
    				locationInfo.set("extendedInfo", extendedInfo);
    			else
    				locationInfo.delAt("extendedInfo");
			} else {
				locationInfo.delAt("extendedInfo");
			}
			//2. DBCreate and set/update legacy:hasGisDataId to business ontology
			Json gisLiteral = Json.make(GisDAO.getGisDBId(locationInfo, false));
			OWLLiteral owlLiteral = bontology.getDataProperty(
					bontology.getBusinessObject(), "legacy:hasGisDataId");
			if(owlLiteral != null)
			{    
				bontology.deleteDataProperty(
						bontology.getBusinessObject(), 
						dataProperty("legacy:hasGisDataId"));
			}
			bontology.addDataProperty(
							bontology.getBusinessObject(),
							dataProperty("legacy:hasGisDataId"),
							Json.object()
									.set("literal", gisLiteral.asLong())
									.set("type",
											"http://www.w3.org/2001/XMLSchema#integer"));
			
			//3. get folio from locationInfo and set/update legacy:hasGisDataId to business ontology as long value
			OWLLiteral folioLiteral = bontology.getDataProperty(
					bontology.getBusinessObject(), "hasFolio");
			if(folioLiteral != null)
			{    
				bontology.deleteDataProperty(
						bontology.getBusinessObject(), 
						dataProperty("hasFolio"));
			}
			Long folioVal = gisInfoUtil.getFolioFromLocationInfo(locationInfo);
			if (folioVal != null) {
				bontology.addDataProperty(
						bontology.getBusinessObject(),
						dataProperty("hasFolio"),
						Json.object()
								.set("literal", folioVal.longValue())
								.set("type",
										"http://www.w3.org/2001/XMLSchema#long"));
				ThreadLocalStopwatch.now("Folio set: " + folioVal);
			}
			if (DBG)
				ThreadLocalStopwatch.getWatch().time("END populateGisDataInternal");
			//4. Completed
			return locationInfo;
		}
		return Json.nil();
	}
	
	private boolean isMdPwsInterfaceCase(Json srJson) {
		Json props = srJson.at("properties");
		boolean isPwsInterfaceCase = props.isObject() && props.has("hasLegacyInterface") 
				&& (props.is("hasLegacyInterface", "MD-PWS") || props.at("hasLegacyInterface").is("hasLegacyCode", "MD-PWS")
					);
		return isPwsInterfaceCase;
	}

	/**
	 * Endpoint that creates a case number (Exempt client only)
	 * @return json object with property hasCaseNumber : String
	 */
	@POST
	@Path("createNewCaseNumber")
	@Produces("application/json")
	public synchronized Json createNewCaseNumber() {
		if (!isClientExempt()) {
			return GenUtils.ko("Not authorized.");
		}
		String newCaseNumber = getPersister().getStore().txn(new CirmTransaction<String> () {
			@Override
			public String call() throws Exception
			{
				DBIDFactory idFactory = (DBIDFactory) Refs.idFactory.resolve(); 
				long seq = idFactory.generateUserFriendlySequence();
				return GenUtils.makeCaseNumber(seq);
			}
		});
		return GenUtils.ok().set("newCaseNumber", newCaseNumber);
	}
	
	/**
	 * Adds the Case Number as a dataProperty in the bo
	 * @param legacyForm : bo in Json format
	 */
	private void createCaseNumber(Json legacyForm) {
		long seq = ((DBIDFactory) Refs.idFactory.resolve())
						.generateUserFriendlySequence();
		legacyForm.at("properties").set("legacy:hasCaseNumber", GenUtils.makeCaseNumber(seq));
	}
	
	/**
	 * Creates a new Service Request coming in from the UI.
	 * 
	 * @param formData : The Service Request data stringified json
	 * @return : returns the Business Ontology which is persisted to db in Json format
	 */
	@POST
	//@Encoded 2372 Java8 hilpold
	@Path("kosubmit")
	@Produces("application/json")
	public Json createNewKOSR(@FormParam("data") final String formData)
	{
		ThreadLocalStopwatch.startTop("START createNewKOSR");
		//
		// Pre transaction processing
		//
		//
		final Json newSrJson = read(formData);		
		//1. Determine SR basics
		final boolean hasCaseNumber = newSrJson.has("properties")
				&& newSrJson.at("properties").has("legacy:hasCaseNumber") 
				&& newSrJson.at("properties").at("legacy:hasCaseNumber").isString();
		final boolean hasCoordinates = newSrJson.has("properties") 
				&& newSrJson.at("properties").has("hasXCoordinate")
				&& newSrJson.at("properties").has("hasYCoordinate");
		
		final Json locationInfo;
		try
		{
			//2. Set/Overwrite Due date for SR.
			dueDateUtil.setDueDateNewSr(newSrJson);
			//3. Get actor emails, removed attachments, and type.
			final Json actorEmails = newSrJson.at("properties").atDel("actorEmails");
			final Json hasRemovedAttachment = newSrJson.at("properties").atDel("hasRemovedAttachment");
			final String type = newSrJson.at("type").asString();
			//4. Permission enforcement
			if (!isClientExempt()
					&& !Permissions.check(individual("BO_New"),
							individual(type), getUserActors())) {
				return ko("Permission denied.");
			}
			
			//5. Slow GIS http call for LocationInfo, if sr has coordinates 
			if (hasCoordinates) {
				double xCoordinate = newSrJson.at("properties").at("hasXCoordinate").asDouble();
				double yCoordinate = newSrJson.at("properties").at("hasYCoordinate").asDouble();
				String[] layers = null;				
				locationInfo = Refs.gisClient.resolve().getLocationInfo(xCoordinate, yCoordinate, layers, 3, 500);
				//locationinfo will be used inside tx call. see below.
			} else {
				locationInfo = Json.object();
			}
			
			//
			// DB Transaction A - Create boid and caseNumber 
			// (Open311 cases will have case number already)  
			//
			// Sequence only, rollback no effect on sequences.
			getPersister().getStore().txn(new CirmTransaction<Object> () {
				@Override
				public Object call() throws Exception
				{
					newSrJson.set("boid", Refs.idFactory.resolve().newId(null));
					if (!hasCaseNumber) {
						createCaseNumber(newSrJson);
					}
					return null;
				}
			}
			);
			final List<CirmMessage> emailsToSend = new ArrayList<CirmMessage>();

			//
			// DB Transaction B - Main processing
			//
			Json result = getPersister().getStore().txn(new CirmTransaction<Json> () {
				@Override
				public Json call() throws Exception
				{
					tryDeleteAttachments(hasRemovedAttachment);	
					final BOntology bontology = BOntology.makeRuntimeBOntology(newSrJson);
					
					//Saving the current context as we are losing when 
					//using another get/post internally. 
					Response current = Response.getCurrent();
					ThreadLocalStopwatch.now("START createDefaultActivities");
					ActivityManager am = new ActivityManager();
					//TODO hilpold move this to where emails are created
					emailsToSend.clear();
					CirmTransaction.get().addTopLevelEventListener(new SendEmailOnTxSuccessListener(emailsToSend));
					am.createDefaultActivities(owlClass(type), bontology, GenUtils.parseDate(newSrJson.at("properties").at("hasDateCreated").asString()), emailsToSend);
					Response.setCurrent(current);
					ThreadLocalStopwatch.now("END createDefaultActivities");
					Json locationInfoTmp = populateGisDataInternal(newSrJson, bontology, locationInfo);
					if (!locationInfoTmp.isNull()) {
						locationInfo.with(locationInfoTmp);
					}
					//DB
					getPersister().saveBusinessObjectOntology(bontology.getOntology());			
					// delete any removed Images, if save succeeds only																	
					OWLNamedIndividual emailTemplate = objectProperty(individual(type), "legacy:hasEmailTemplate");
					// It's an array only because it has to be a final variable, so we can insert or not.
					final ArrayList<BOntology> withMetadata = new ArrayList<BOntology>();					
					if (emailTemplate != null)
					{
						try
						{
							BOntology withMeta = addMetaDataAxioms(bontology);
							withMetadata.add(withMeta);
							CirmMessage msg = MessageManager.get().createMessageFromTemplate(
									withMeta,
									dataProperty(individual(type),
											"legacy:hasLegacyCode"), emailTemplate);
							msg.addExplanation("createNewKOSR SR template " + emailTemplate.getIRI().getFragment());
							emailsToSend.add(msg);
						}
						catch (Throwable t)
						{
							ThreadLocalStopwatch.error("Error createNewKOSR - Failed to create email for " + bontology.getObjectId());
						}
					}
                    final Json result = bontology.toJSON();
                    addAddressData(result);
                    //Fire legacy:NewServiceCaseEvent on later tx success
					CirmTransaction.get().addTopLevelEventListener(new CirmTransactionListener() {
					    public void transactionStateChanged(final CirmTransactionEvent e)
					    {
					    	if (e.isSucceeded())
					    	{
					    		try
					    		{
					    			BOntology withMeta = withMetadata.isEmpty() ?
						    			    LegacyEmulator.this.addMetaDataAxioms(bontology):withMetadata.get(0);
								        EventDispatcher.get().dispatch(
								                OWL.individual("legacy:NewServiceCaseEvent"), 
								                bontology.getBusinessObject(), 
								                OWL.individual("BO_New"),
								                Json.object("case", OWL.toJSON(withMeta.getOntology(), bontology.getBusinessObject()),
								                            "locationInfo", locationInfo));
					    		}
					    		catch (Exception ex)
					    		{
					    			GenUtils.reportFatal("Failed to send case " + bontology.getObjectId() + " to department", "", ex);
									ThreadLocalStopwatch.error("Error createNewKOSR - Failed to send to dept " + bontology.getObjectId());
					    		}
					    	}
					    }
					});
					//START : Emails to customers
					if(actorEmails != null)
					{
						sendEmailToCustomers(bontology, 
								actorEmails.asJsonList(), emailsToSend, 
								result.at("properties").at("hasCaseNumber").asString(),
								OWL.getEntityLabel(individual("legacy:"+result.at("type").asString()))
						);
					}
					//END : Emails to customers					
					return ok().set("bo", result);
				}
			});

			//
			// Post Transaction processing
			//
			if (locationInfo.has("extendedInfo") && locationInfo.at("extendedInfo").has("error"))
			{
				GenUtils.reportPWGisProblem(result.at("bo").at("properties").at("hasCaseNumber").asString(),
						locationInfo.at("extendedInfo").at("error"));
				ThreadLocalStopwatch.error("Error createNewKOSR - Gis problem " + result.at("bo").at("properties").at("hasCaseNumber").asString());
			}
			//MessageManager.get().sendEmails(emailsToSend);
			srStatsReporter.succeeded("create", newSrJson);
			ThreadLocalStopwatch.stop("END createNewKOSR");
			return result;					
		}
		catch (Throwable e)
		{
			Throwable t = GenUtils.getRootCause(e);
			srStatsReporter.failed("create", newSrJson, e.toString() + "Rootcause: " + t.toString(), e.getMessage() + "Root Msg: " + t.getMessage());
			ThreadLocalStopwatch.fail("FAIL createNewKOSR");
			System.out.println("formData passed into createNewKOSR : "+formData);
			e.printStackTrace();
			return ko(e.getMessage());
		}
	}

	private void sendEmailToCustomers(BOntology bo, 
				List<Json> customers, 
				List<CirmMessage> emailsToSend, 
				String caseNumber, String srType)
		{
			for(Json customer : customers)
			{
				CirmMessage msg = MessageManager.get().createMessageFromTemplate(
						bo, individual("legacy:SERVICEHUB_EMAIL_CUSTOMERS"), 
						srType + " " + caseNumber, 
						customer.at("email").asString(), 
						null, null, null);
	//					"Dear "+customer.at("name").asString()
	//					+", \n We successfully processed the Service Request.");
				emailsToSend.add(msg);
			}
		}

	/**
	 * Saves a new non Cirm originated (e.g. PW, CMS Interfaces, Open311) or referral service case into the cirm database.
	 * If the SR is saved in pending state autoOnPending activities may be created and emails be sent.
	 * 
	 * Due date will be set based on srType (if not already provided in newSrJson).
	 * 
	 * Must be called from within a CirmTransaction.
	 * @param legacyform
	 * @return
	 */
	public Json saveNewCaseTransaction(Json newSRJson)
	{
		boolean hasCaseNumber = newSRJson.has("properties")
				&& newSRJson.at("properties").has("legacy:hasCaseNumber") 
				&& newSRJson.at("properties").at("legacy:hasCaseNumber").isString();
		String type = newSRJson.at("type").asString();
		// 1 Set due date if not provided by external system or in referral case, ignore errors.
		try {
			if (!dueDateUtil.hasDueDateNewSr(newSRJson)) {
				dueDateUtil.setDueDateNewSr(newSRJson);
			} else {
				ThreadLocalStopwatch.now("saveNewCaseTransaction: Received Sr with due date, type: " + type);
			}
		} catch (Exception e) {
			ThreadLocalStopwatch.error("saveNewCaseTransaction: Ignored Exception during Due Date checking or setting for a new SR, type: " + type + " Exception: ");
			e.printStackTrace();
		}
		OperationService op = new OperationService();
		// 2 Determine SR type and create a new case number
		int i = type.indexOf("legacy:");
		if (i == -1)
			type = "legacy:" + type;
		BOntology bontology = op.createBusinessObject(owlClass(type));
		newSRJson.set("boid", bontology.getObjectId());
		if (!hasCaseNumber) {
			createCaseNumber(newSRJson);
		}
		// 3 Parse all legacyForm data into a business ontology
		bontology = BOntology.makeRuntimeBOntology(newSRJson);
		populateGisData(newSRJson, bontology);	
		// 4 Check pending and create autoOnPending activities
		if (OWL.individual("legacy:O-PENDNG").equals(bontology.getObjectProperty("legacy:hasStatus"))) {
			List<CirmMessage> messages = new ArrayList<>();
			ActivityManager amgr = new ActivityManager();
			amgr.createAutoOnPendingActivities(bontology, new Date(), messages);
			CirmTransaction.get().addTopLevelEventListener(new SendEmailOnTxSuccessListener(messages));
		}
		// 5 Save BO		
		getPersister().saveBusinessObjectOntology(bontology.getOntology());
		// 6 Extend BO by adding meta data axioms		
		final BOntology bontologyVerbose;
		try
		{
			bontologyVerbose = addMetaDataAxioms(bontology);
		}
		catch (OWLOntologyCreationException e)
		{
			throw new RuntimeException(e);
		}
		final Json bontologyVerboseJson = OWL.toJSON(bontologyVerbose.getOntology(), bontology.getBusinessObject()); 
		bontologyVerboseJson.set("boid", bontology.getObjectId());
		// 7 Fire a legacy:NewServiceCaseNonCirmEvent only if the overall transaction finishes successfully.
		// This ignores retries (we're inside a repeatable transaction!), so the event is guaranteed to fire only once.
		// Assumes access to final variables bontologyVerboseJson, bontologyVerbose during event processing,
		// so we must not change the json bontologyVerboseJson points to after returning it.
		CirmTransaction.get().addTopLevelEventListener(new CirmTransactionListener() {
		    public void transactionStateChanged(final CirmTransactionEvent e)
		    {
		    	if (e.isSucceeded())
		    	{
		    		try
		    		{
					        EventDispatcher.get().dispatch(
					                OWL.individual("legacy:NewServiceCaseNonCirmEvent"), 
					                bontologyVerbose.getBusinessObject(), 
					                OWL.individual("BO_New"),
					                Json.object("case", bontologyVerboseJson));
					                // maybe add "locationInfo", locationInfo
		    		}
		    		catch (Exception ex)
		    		{
		    			String errmsg ="Failure during the dispatch of a legacy:NewServiceCaseExternalEvent for " + bontologyVerbose.getObjectId(); 
		    			ThreadLocalStopwatch.error(errmsg);
		    		}
		    	}
		    }
		});
		return ok().set("data", bontologyVerboseJson);		
	}
	
	/**
	 * Internal method (no permissions check) to save a new service request.
	 * 
	 * @param formData
	 * @return
	 */
	public Json saveNewServiceRequest(final String formData)
	{
		ThreadLocalStopwatch.start("START saveNewServiceRequest (referral?)");
		try
		{
			return Refs.defaultRelationalStore.resolve().txn(new CirmTransaction<Json>() {
			public Json call()
			{				
				Json result = saveNewCaseTransaction(Json.read(formData)); 
				ThreadLocalStopwatch.stop("END saveNewServiceRequest (referral?)");
				return result;
			}});
		}
		catch (Exception e)
		{
			ThreadLocalStopwatch.fail("FAIL saveNewServiceRequest (referral?) with " + e);
			e.printStackTrace();
			return ko(e);
		}
	}

	private Set<OWLNamedIndividual> fetchIndividualSet(String csrTypeIRI)
	{
		if (csrTypeIRI != null && !csrTypeIRI.contains("legacy:"))
			csrTypeIRI = "legacy:" + csrTypeIRI;

		String classExpr = "legacy:QuestionTrigger and legacy:hasServiceField some "
				+ "(inverse legacy:hasServiceField value " + csrTypeIRI + ")";

		return OWL.queryIndividuals(classExpr);
	}

	/**
	 * Emails the Service Request to the listed recipients  
	 * @param formData : A JSON object with email subject and recipients
	 * eg : formData = {
	 * 	"subject":"Test email", 
	 *  "to":"abc@abc.com;xyz@xyz.net", 
	 *  "cc":"abc@abc.gov;xyz@xyz.net", 
	 *  "bcc":"abc@abc.net"
	 *  "boid": 12345678
	 * }
	 * @return : a status message if successful or not.
	 */
	@POST
	//@Encoded  2372 Java8 hilpold
	@Path("emailSR")
	@Produces("application/json")
	public Json emailServiceRequestTo(@FormParam("data") final String formData)
	{
		if (DBG)
			ThreadLocalStopwatch.getWatch().time("START emailServiceRequestTo");
		try
		{
			Json data = Json.read(formData);
			Long boid = data.at("boid").asLong();
			String subject = data.has("subject") ? 
					data.at("subject").asString() : null;
			String to = data.has("to") ? data.at("to").asString() : null;
			String cc = data.has("cc") ? data.at("cc").asString() : null;
			String bcc = data.has("bcc") ? data.at("bcc").asString() : null;
			String comments = data.has("comments")? data.at("comments").asString() : null;

			BOntology bo = findServiceCaseOntology(boid);
			final List<CirmMessage> emailsToSend = new ArrayList<CirmMessage>();
			OWLNamedIndividual emailTemplate = individual("legacy:SERVICEHUB_EMAIL");
			CirmMessage msg = MessageManager.get().createMessageFromTemplate(
					bo, emailTemplate, subject, to, cc, bcc,comments);
			emailsToSend.add(msg);
			MessageManager.get().sendEmails(emailsToSend);
			return ok();
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return ko(e.getMessage());
		}
		finally
		{
			if (DBG)
				ThreadLocalStopwatch.getWatch().time(
						"END emailServiceRequestTo");
			ThreadLocalStopwatch.dispose();
		}
	}

	@GET
	@Path("/alerts/{individual}")
	@Produces("application/json")
	public Json getAlerts(@PathParam("individual") String csrTypeIRI)
	{
		if (!isClientExempt()
				&& !Permissions.check(individual("BO_View"),
						individual("legacy:" + csrTypeIRI), getUserActors()))
			return ko("Permission denied.");

		Json alerts = object();
		Set<OWLNamedIndividual> indSet = fetchIndividualSet(csrTypeIRI);
		if (indSet == null)
			return ko("null");
		else
		{
			for (OWLNamedIndividual ind : indSet)
			{
				String outerKey = null;
				String innerKey = dataProperty(ind, "legacy:hasAnswerValue")
						.getLiteral();
				OWLNamedIndividual propInd = objectProperty(ind, "legacy:hasServiceField");
				if (propInd != null)
					outerKey = propInd.getIRI().toString();
				propInd = objectProperty(ind, "legacy:hasLegacyEvent");
				if (propInd != null)
				{
				    String label = OWL.getEntityLabel(propInd);
    				if (label != null)
    				{
    					if (alerts.has(outerKey))
    						alerts.at(outerKey).set(innerKey, label);
    					else
    						alerts.set(outerKey, object().set(innerKey, label));
    				}
				}
			}
			Json hasAlerts = object().set("hasAlerts", alerts);
			return hasAlerts;
		}
	}

	@GET
	@Path("/bo/{boid}/dom")
	@Produces("application/json")
	public Json getServiceCaseAsDom(@PathParam("boid") Long boid)
	{
		try
		{
			OperationService op = new OperationService();
			BOntology bo = op.getBusinessObjectOntology(boid);
			if (bo != null && bo.getBusinessObject() != null)
			{
				if (!isClientExempt()
						&& !Permissions.check(individual("BO_View"),
								individual(bo.getTypeIRI("legacy")),
								getUserActors()))
					return ko("Permission denied.");
				MessageManager manager = new MessageManager();
				BOntology verbose = addMetaDataAxioms(bo);
				Json sr = OWL.toJSON(verbose.getOntology(), verbose.getBusinessObject());
				return ok().set("data", manager.toDOMString(sr));
			}
			else
			{
				return ko("Could not retrieve the ontology for id:" + boid);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return ko(e);
		}
	}

	@GET
	@Path("/bo/{boid}/activities")
	@Produces("application/json")
	public Json getActivities(@PathParam("boid") Long boid)
	{
		try
		{
			OperationService op = new OperationService();
			BOntology bo = op.getBusinessObjectOntology(boid);
			if (bo != null && bo.getBusinessObject() != null)
			{
				if (!isClientExempt()
						&& !Permissions.check(individual("BO_View"),
								individual(bo.getTypeIRI("legacy")),
								getUserActors()))
					return ko("Permission denied.");
				OWLOntology o = bo.getOntology();
				Json array = Json.array();
				for (OWLNamedIndividual activity : OWL
						.reasoner(o)
						.getObjectPropertyValues(bo.getBusinessObject(),
								objectProperty("legacy:hasServiceActivity"))
						.getFlattened())
				{
					array.add(OWL.toJSON(o, activity));
				}
				return array;
			}
			return ko("No object found with id " + boid);
		}
		catch (Throwable e)
		{
			e.printStackTrace();
			return ko(e);
		}

	}

	/**
	 * Creates an activity now, ignoring possible occur day settings.
	 * @param boid
	 * @param activityCode
	 * @return
	 */
	@GET
	@Path("/bo/{boid}/activities/create/{activityCode}")
	@Produces("application/json")
	public Json createActivityNow(@PathParam("boid") Long boid,
			@PathParam("activityCode") String activityCode)
	{
		try
		{
			ThreadLocalStopwatch.startTop("START /bo/{boid}/activities/create/{activityCode} " + boid + " " + activityCode);
			OperationService op = new OperationService();
			RelationalOWLPersister persister = getPersister();
			BOntology bo = op.getBusinessObjectOntology(boid);
			if (bo != null && bo.getBusinessObject() != null)
			{
				if (!isClientExempt()
						&& !Permissions.check(individual("BO_Update"),
								individual(bo.getTypeIRI("legacy")),
								getUserActors()))
				{
					ThreadLocalStopwatch.fail("Fail /bo/{boid}/activities/create/{activityCode} permission denied " + boid + " " + activityCode);
					return ko("Permission denied.");
				}
				// OWLOntology o = bo.getOntology();
				OWLNamedIndividual status = bo
						.getObjectProperty("legacy:hasStatus");
				if (status == null
						|| status.getIRI().getFragment().startsWith("C-"))
					return ko("Can only create an activity on an open SR.");
				ActivityManager manager = new ActivityManager();
				OWLNamedIndividual activity = individual("legacy:" + activityCode);
				//hilpold whole algorithm should be inside a transaction and SendEmailOnTxSuccessListener used
				List<CirmMessage> emailsToSend = new ArrayList<CirmMessage>();
				//Create the activity ignoring occur day settings on TM callback.
				manager.createActivityOccurNow(activity, bo, emailsToSend);
				persister.saveBusinessObjectOntology(bo.getOntology());
				for (CirmMessage m : emailsToSend) 
				{
					m.addExplanation("LE.createActivity " + activityCode);
				}
				MessageManager.get().sendEmails(emailsToSend);
				ThreadLocalStopwatch.stop("END /bo/{boid}/activities/create/{activityCode} success " + boid + " " + activityCode);
				return ok();
			} else 
			{
				//Case not found
				ThreadLocalStopwatch.fail("FAIL /bo/{boid}/activities/create/{activityCode} case not found, but ok() returned " + boid + " " + activityCode);
				return ok();
			}
		}
		catch (Throwable e)
		{
			ThreadLocalStopwatch.fail("FAIL /bo/{boid}/activities/create/{activityCode} " + boid + " " + activityCode + " with " +  e);
			e.printStackTrace();
			return ko(e);
		} 
	}

	@GET
	@Path("/bo/{boid}/activity/{activityFragment}")
	@Produces("application/json")
	public Json getActivity(@PathParam("boid") Long boid,
			@PathParam("activityFragment") String activityFragment)
	{
		try
		{
			OperationService op = new OperationService();
			BOntology bo = op.getBusinessObjectOntology(boid);
			if (bo != null && bo.getBusinessObject() != null)
			{
				if (!isClientExempt()
						&& !Permissions.check(individual("BO_View"),
								individual(bo.getTypeIRI("legacy")),
								getUserActors()))
					return ko("Permission denied.");
				OWLOntology o = bo.getOntology();
				Json object = null;
				for (OWLNamedIndividual activity : OWL
						.reasoner(o)
						.getInstances(OWL.oneOf(individual(activityFragment)),
								true).getFlattened())
				{
					object = OWL.toJSON(o, activity);
					return object;
				}
			}
			return ko("No object found with id " + boid);
		}
		catch (Throwable e)
		{
			return ko(e);
		}

	}
	
	@GET
    @Path("/bo/{boid}/activity/{activityFragment}/overdue/create/{activityCode}")
    @Produces("application/json")
    public Json createActivityWhenOverdue(@PathParam("boid") Long boid,
                  @PathParam("activityFragment") String activityFragment,
                  @PathParam("activityCode") String overdueActivity)
    {	
		   String callInfo = "/bo/{boid}/activity/{activityFragment}/overdue/create/{activityCode} " + boid + " " + activityFragment + " " + overdueActivity;
           ThreadLocalStopwatch.startTop("START " + callInfo);
           try
           {
                  OperationService op = new OperationService();
                  RelationalOWLPersister persister = getPersister();
                  BOntology bo = op.getBusinessObjectOntology(boid);
                  if (bo != null && bo.getBusinessObject() != null)
                  {
                        if (!isClientExempt()
                                      && !Permissions.check(individual("BO_Update"),
                                                    individual(bo.getTypeIRI("legacy")),
                                                    getUserActors()))
                        {
                            ThreadLocalStopwatch.fail("FAIL permission denied " + callInfo);
                        	return ko("Permission denied.");
                        }
                        OWLOntology o = bo.getOntology();
                        
                        OWLNamedIndividual activityToCheck = o.getOWLOntologyManager().getOWLDataFactory().getOWLNamedIndividual(OWL.fullIri(activityFragment)); 
                        if (o.getIndividualsInSignature(true).contains(activityToCheck))
                        {
                               OWLNamedIndividual status = bo.getObjectProperty("legacy:hasStatus");  
                               // if status is closed do nothing.
                               if (status.getIRI().getFragment().startsWith("C-")
                            		   || (bo.getDataProperty(activityToCheck, "legacy:hasCompletedDate") != null) 
                            	   	   || (bo.getDataProperty(activityToCheck, "legacy:hasCompletedTimestamp") != null)) 
                            	   return ok();
                               OWLLiteral dueDate = bo.getDataProperty(activityToCheck, "legacy:hasDueDate");
                               if (dueDate != null )
                               {
                            	   Date due = OWL.parseDate(dueDate);
                                   Date now = new Date();
                                   if (now.after(due))
                                   {
                                	   ActivityManager manager = new ActivityManager();
                                	   //TODO hilpold full method should be inside a transaction and SendEmailOnTxSuccessListener used
                                	   List<CirmMessage> emailsToSend = new ArrayList<CirmMessage>();
                                	   manager.createActivity(individual("legacy:"	+ overdueActivity), null, null, bo, null, null, emailsToSend);
                                	   manager.updateActivityIfAutoDefaultOutcome(activityToCheck, bo, emailsToSend);
                                	   persister.saveBusinessObjectOntology(bo.getOntology());
                                	   for (CirmMessage m : emailsToSend) 
                                		   m.addExplanation("LE.createWhenOverDue boid " + boid + " ACt: " + activityFragment);
                                	   MessageManager.get().sendEmails(emailsToSend);
                                    }
                               }
                               ThreadLocalStopwatch.stop("END " + callInfo);
                               return ok();
                        }
                        ThreadLocalStopwatch.fail("FAIL orig activity not found " + callInfo);
                        return ko("Could not create overdue activity, original activity not found.");
                  }
                  ThreadLocalStopwatch.fail("FAIL bo not found " + callInfo);
                  return ko("No business object found with id " + boid);
           }
           catch (Throwable e)
           {
               ThreadLocalStopwatch.fail("FAIL with " + e + " " + callInfo);
               e.printStackTrace();
               return ko(e);
           }
    }

	/**
	 * Calls a web service (retries up to MAX_CALLWS_ATTEMPS == 3)
	 * @param type the type of web service to call (see ontologies)
	 * @param arguments string array of arguments
	 * @return ok or ko
	 */
	@GET
	@Path("/ws/{type}")
	@Produces("application/json")
	public Json callWS(@PathParam("type") String type, @QueryParam("arg") String[] arguments) {
		boolean failed;
		Json result; 
		int attempt = 0;
		ThreadLocalStopwatch.startTop("START CallWS: " + type);		
		do {
			attempt ++;
			failed = false;
			result = callWsImpl(type, arguments);
			if (result.is("ok", false)) {
				//Any Exception is fully caught and printed in callWsImpl
				failed = true;
				try {
					// sleep min 0.1, avg 0.3, max 0.5s secs
					Thread.sleep(((long)(100 + Math.random() * 400)));
				} catch (InterruptedException ie) {}
			}
		} while (failed && attempt < MAX_CALLWS_ATTEMPTS);
		if (result.is("ok", true)) {
			ThreadLocalStopwatch.stop("END CallWS: Success after " + attempt + " attempt(s).");
		} else {
			ThreadLocalStopwatch.fail("ERROR CallWS: All 3 attempts failed, responding ko");
		}
		return result;
	}
	
	public Json callWsImpl(String type, String[] arguments)
	{
		ThreadLocalStopwatch.start("START CallWsImpl: " + type);		
		try
		{
			OWLNamedIndividual typeInd = (OWLNamedIndividual) Refs.configSet.resolve().get(type);

			OWLDataFactory factory = OWL.dataFactory();
			List<SWRLDArgument> args = new ArrayList<SWRLDArgument>();
			args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral(typeInd.getIRI().getFragment())));
			// args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral(type)));
			for (String arg : arguments)
			{
				SWRLDArgument argSwrld = factory.getSWRLLiteralArgument(factory.getOWLLiteral(arg)); 
				args.add(argSwrld);
				ThreadLocalStopwatch.now("NOW CallWsImpl arg: " + arg + " swrld: " + argSwrld);
			}

			args.add(factory.getSWRLVariable(OWL.fullIri("document")));
			SWRLBuiltInAtom atom = factory.getSWRLBuiltInAtom(OWL.fullIri("webServiceCall"), args);
			WebServiceCallTask accountQuery = new WebServiceCallTask();

			Map<SWRLVariable, OWLObject> eval = accountQuery.eval(atom,
					OWL.ontology(), new RefResolver<SWRLVariable, OWLObject>()
					{
						public OWLObject resolve(SWRLVariable v)
						{
							return null;
						}
					});

			OWLObject obj = eval.entrySet().iterator().next().getValue();
			OWLLiteral l = (OWLLiteral) obj;

			StreamSource xml = new StreamSource(
					new StringReader(l.getLiteral()));
			StreamSource xsl = new StreamSource(new File(StartUp.getConfig().at(
					"workingDir").asString()
					+ "/src/resources/xml-2-json.xsl"));
			TransformerFactory tFactory = TransformerFactory.newInstance();
			Transformer transformer = tFactory.newTransformer(xsl);
			StringWriter writer = new StringWriter();
			StreamResult literal = new StreamResult(writer);
			try {
				transformer.transform(xml, literal);
			} catch (Exception e) {
				throw e;
			}
			System.out.println(literal.getWriter().toString());
			Json result = Json.read(literal.getWriter().toString());
			ThreadLocalStopwatch.stop("END CallWsImpl: " + type + " Success.");		
			return ok().set("result", result);
		}
		catch (Throwable e)
		{
			e.printStackTrace(System.err);
			ThreadLocalStopwatch.error("ERROR CallWsImpl: " + type + " " + e);		
			return ko(e.getMessage());
		}
	}

	public static Json replaceAnswerValuesWithLabels(Json data)
	{
		if (!data.has("hasServiceAnswer"))
			return data;
		if (data.at("hasServiceAnswer").isArray())
		{
			for (Json ans : data.at("hasServiceAnswer").asJsonList())
			{
				replaceEachAnswerValuesWithLabels(ans);
			}
		}
		else
			replaceEachAnswerValuesWithLabels(data.at("hasServiceAnswer"));
		return data;
	}

	public static void replaceEachAnswerValuesWithLabels(Json ans)
	{
		if (ans.has("hasAnswerValue"))
		{
			if (ans.has("hasServiceField")
					&& ans.at("hasServiceField").has("hasChoiceValueList"))
			{
				if (!ans.at("hasAnswerValue").isArray())
				{
					if (!GenUtils.containsWhiteSpace(ans.at("hasAnswerValue")
							.asString()))
					{
						OWLNamedIndividual ansIndividual = OWL.individual(ans
								.at("hasAnswerValue").asString());
						String label = OWL.getEntityLabel(ansIndividual);
						if (label != null)
							ans.set("hasAnswerValue", label);
					}
				}
				else if (ans.at("hasAnswerValue").isArray())
				{
					Json answerValueArray = array();
					for (Json eachAnswerValue : ans.at("hasAnswerValue")
							.asJsonList())
					{
						if (!GenUtils.containsWhiteSpace(eachAnswerValue
								.asString()))
						{
							OWLNamedIndividual ansIndividual = OWL
									.individual(eachAnswerValue.asString());
							String label = OWL.getEntityLabel(ansIndividual);
							if (label != null)
								answerValueArray.add(label);
						}
					}
					ans.set("hasAnswerValue", answerValueArray);
				}
			}
		}
	}

	@GET
	@Path("/validate")
	@Produces("application/json")
	public Json validateServiceOnXY(@QueryParam("type") String typeCode,
			@QueryParam("x") String x, @QueryParam("y") String y)
	{
		ThreadLocalStopwatch.startTop("START validate " + typeCode + " at " + x + ", " + y);
		Json result; 
		try
		{
			Json propertyInfo = Json.object();
			propertyInfo.set(
					"coordinates",
					Json.object().set("x", new BigDecimal(x))
							.set("y", new BigDecimal(y)));
			
			boolean isValidXyForType = Refs.gisClient.resolve().isAvailable(propertyInfo, OWL.fullIri("legacy:" + typeCode));
			result = Json.object().set("isAvailable", isValidXyForType);
		}
		catch (Exception e)
		{
			result = Json.object().set("isAvailable", false);
			ThreadLocalStopwatch.error("ERROR validate " + e);
			e.printStackTrace();
		}
		ThreadLocalStopwatch.stop("END validate " + result);
		return result;
	}

	@POST
	@Path("/duplicateCheck")
	@Produces("application/json")
	public Json duplicateCheckService(@FormParam("data") String formData)
	{
		if (DBG) ThreadLocalStopwatch.startTop("START DuplicateCheck " + formData);
		List<Map<String, Object>> duplicates;
		Json result = ok();
		Json json = Json.read(formData);
		String srType = json.at("type").asString();
		Json address = json.at("address");
		String createdDate = json.has("createdDate") ? 
				json.at("createdDate").asString() : null;
		String boid = json.has("boid") ? json.at("boid").asString() : null;
		String hasCaseNumber = json.has("hasCaseNumber") ? 
				json.at("hasCaseNumber").asString() : null;
		try
		{
			String addrType = address.at("addressType").isNull() ? "" : 
					address.at("addressType").asString();
			String fullAddr = address.at("fullAddress").isNull() ? "" : 
					address.at("fullAddress").asString();
			String city = address.at("Street_Address_City").isNull() ? "" :
					address.at("Street_Address_City").at("iri").isNull() ? "" : 
					address.at("Street_Address_City").at("iri").asString();
			String state = address.at("Street_Address_State").at("iri")
					.asString();
			Long zip = address.at("Zip_Code").isNull() ? 0 : 
					address.at("Zip_Code").asLong();
			String unit = address.has("Street_Unit_Number") ? address.at(
					"Street_Unit_Number").asString() : null;
			String locationName = address.has("hasLocationName") ? address.at(
					"hasLocationName").asString() : null;

			if (addrType.equals("PointAddress") || addrType.equals("StreetAddress") || addrType.equals("Address"))
			{
				Long streetNumber = address.at("Street_Number").asLong();
				String streetName = address.at("Street_Name").asString();
				String streetPrfx = address.at("Street_Direction").at("iri")
						.asString();
				String streetSufx = address.at("hasStreetType").at("iri")
						.asString();

				duplicates = duplicateCheck(owlClass(srType), streetNumber,
						streetName, streetPrfx, streetSufx, unit, locationName,
						city, state, zip, fullAddr, createdDate, false);
			}
			else
			{
				duplicates = duplicateCheck(owlClass(srType), (long) 0, null,
						null, null, unit, locationName, city, state, zip,
						fullAddr, createdDate, true);
			}
			if (DBG) ThreadLocalStopwatch.now("DUPLICATE Query completed");
			if (duplicates == null)
			{
				result.set("count", 0).set("message",
						"No duplicate check rule configured for this type.");
				result.set("details", Json.array());
			}
			else
			{
				Json details = Json.array();
				Set<String> boids = new HashSet<String>();
				for (Map<String, Object> duplicate : duplicates)
				{
					Json dup = Json.make(duplicate);
					boids.add(dup.at("boid").asString());
					details.add(dup);
				}
				if(!boids.isEmpty()) {
    				for (Map<String, Object> rc : getAllRelatedCases(boids, boid, hasCaseNumber))
    				{
    					details.add(rc);
    				}
				}
				result.set("count", details.asJsonList().size());
				result.set("details", details).set("message", "Success");
			}
			return result;
		}
		catch (Exception e)
		{
			if (DBG) ThreadLocalStopwatch.fail("FAIL DuplicateCheck ");
			System.out.println("formData passed into duplicateCheckService : "+formData);
			e.printStackTrace();
			return ko(e);
		}
		finally
		{
			if (DBG) ThreadLocalStopwatch.stop("END DuplicateCheck");
		}

	}

	/**
	 * Checks for SR duplicates in the RelationStore based on the
	 * duplicateCheckRule configuration in the ontology for the given srType.
	 * The algorithm is based on what is described in the CSR Configuration
	 * manual under: Setting Duplicate Check Method {@link http
	 * ://olsserver:8080/
	 * wiki/attach/Docs/Configuration_Mgr_Guide_%28Combined%29_PDF_format.pdf}
	 * 
	 * 
	 * @param srType
	 * @param streetNumber
	 * @param streetName
	 * @param streetPrefix
	 * @param streetSuffix
	 * @param unit
	 * @param locationName
	 * @param city
	 * @param state
	 * @param zip
	 * @return
	 * @author Syed
	 */
	private List<Map<String, Object>> duplicateCheck(OWLClass srType,
			Long streetNumber, String streetName, String streetPrefix,
			String streetSuffix, String unit, String locationName, String city,
			String state, Long zip, String fullAddress, String createdDate, 
			boolean Isintersection)
			throws Exception
	{
		long lo = ((long) streetNumber / 100) * 100;
		long hi = lo + 99;
		long thresholdDays = -1; // Search only SRs created on (today's date -
									// thresholdDays) when determining
									// duplicates.
		long addressBuffer = 0; // Address buffer is the range of streetNumbers
								// to check relative to the supplied
								// streetNumber, default is 0-99 (whole block)
		boolean useAddressBuffer = false;
		OWLClass statusLimit;
		OWLNamedIndividual duplicateCheckRule = objectProperty(
				individual(srType.getIRI().toString()), Model.legacy("hasDuplicateCheckRule").toString());
		Set<OWLClass> S = null;
		if (duplicateCheckRule != null)
			S = reasoner().getTypes(duplicateCheckRule, true).getFlattened();
		if (S == null || S.isEmpty())
			return null;
		OWLClass type = S.iterator().next();
		if (type.getIRI().equals(Model.legacy("StreetAddressOnlyCheckRule")))
		{
			if (Isintersection == false)
			{
				useAddressBuffer = true; // use and address buffer only for
											// StreetAddressOnlyRule.
				OWLLiteral addressBufferLiteral = dataProperty(
						duplicateCheckRule, Model.legacy("hasAddressBuffer").toString());
				if (addressBufferLiteral != null)
				{
					addressBuffer = addressBufferLiteral.parseInteger();
					if (addressBuffer > 0)
					{
						if (addressBuffer > streetNumber)
							lo = 0;
						else
							lo = streetNumber - addressBuffer;
						hi = streetNumber + addressBuffer;
					}
				}
			}

		}
		else if (type.getIRI().equals(Model.legacy("FullAddressCheckRule")))
		{
			/**
			 * The default settings check the all address fields so nothing
			 * extra to do here, except recognizing the rule itself in the else
			 * condition.
			 */
		}
		else
		{
			throw new IllegalStateException("Unknown duplicate check rule:"
					+ type.getIRI().toString());
		}

		OWLLiteral thresholdDaysLiteral = dataProperty(duplicateCheckRule,
				Model.legacy("hasThresholdDays").toString());
		if (thresholdDaysLiteral != null)
		{
			thresholdDays = thresholdDaysLiteral.parseInteger();
		}

		OWLNamedIndividual statusLimitIndividual = objectProperty(
				duplicateCheckRule, Model.legacy("hasStatusLimit").toString());
		if (statusLimitIndividual != null)
		{
			statusLimit = owlClass(statusLimitIndividual.getIRI());
		}
		else
		{
			statusLimit = null;
		}

		Statement statement = new Statement();
		List<Object> parameters = new ArrayList<Object>();
		List<OWLNamedIndividual> parameterTypes = new ArrayList<OWLNamedIndividual>();
		Sql select = SELECT();
		select.FROM("CIRM_SR_REQUESTS_VIEW")
				.COLUMN("SR_REQUEST_ID").AS("\"boid\"")
				.COLUMN("FULL_ADDRESS").AS("\"fullAddress\"")
				.COLUMN("UNIT").AS("\"Street_Unit_Number\"")
				.COLUMN("SR_STATUS").AS("\"hasStatus\"")
				.COLUMN("CREATED_DATE").AS("\"hasDateCreated\"")
				.COLUMN("CASE_NUMBER").AS("\"hasCaseNumber\"");
		select.WHERE("SR_TYPE").EQUALS("?").AND().WHERE("CITY").EQUALS("?")
				.AND().WHERE("ZIP").EQUALS("?");
		parameters.add(srType);
		parameterTypes.add(individual(Concepts.INTEGER));
		OWLNamedIndividual cityIndividual = individual(city);
		parameters.add(cityIndividual);
		parameterTypes.add(individual(Concepts.INTEGER));
		parameters.add(zip);
		parameterTypes.add(individual(Concepts.INTEGER));
		// conditional
		if (thresholdDays > -1)
		{
//			select.AND().WHERE("CREATED_DATE").GREATER_THAN("SYSDATE - ?");
//			parameters.add(thresholdDays);
//			parameterTypes.add(individual(Concepts.INTEGER));
			select.AND().WHERE("CREATED_DATE");
			if(createdDate == null)
			{
				select.GREATER_THAN("SYSDATE - ?");
				parameters.add(thresholdDays);
				parameterTypes.add(individual(Concepts.INTEGER));
			}
			else
			{
				select.GREATER_THAN("? - ?");
				parameters.add(createdDate);
				parameterTypes.add(individual(Concepts.TIMESTAMP));
				parameters.add(thresholdDays);
				parameterTypes.add(individual(Concepts.INTEGER));
			}
		}
		if (useAddressBuffer)
		{
			select.AND().WHERE("STREET_NUMBER").BETWEEN("?", "?");
			parameters.add(lo);
			parameterTypes.add(individual(Concepts.INTEGER));
			parameters.add(hi);
			parameterTypes.add(individual(Concepts.INTEGER));

			if (streetPrefix != null && !"".equals(streetPrefix))
			{
				select.AND().WHERE("STREET_NAME_PREFIX").EQUALS("?");
				OWLNamedIndividual streetPrefixIndividual = individual(streetPrefix);
				parameters.add(streetPrefixIndividual.getIRI().getFragment());
				parameterTypes.add(individual(Concepts.VARCHAR));
			}
			if (streetSuffix != null && !"".equals(streetSuffix))
			{
				select.AND().WHERE("STREET_NAME_SUFFIX").EQUALS("?");
				OWLNamedIndividual streetSuffixIndividual = individual(streetSuffix);
				parameters.add(streetSuffixIndividual.getIRI().getFragment());
				parameterTypes.add(individual(Concepts.VARCHAR));
			}
			if (streetName != null && !"".equals(streetName))
			{
				select.AND().WHERE("STREET_NAME").EQUALS("?");
				parameters.add(streetName);
				parameterTypes.add(individual(Concepts.VARCHAR));
			}
		}
		else
		{
			select.AND().WHERE("FULL_ADDRESS").EQUALS("?");
			parameters.add(fullAddress);
			parameterTypes.add(individual(Concepts.VARCHAR));
		}

		if (unit != null && !unit.equals(""))
		{
			select.AND().WHERE("UNIT").EQUALS("?");
			parameters.add(unit);
			parameterTypes.add(individual(Concepts.VARCHAR));
		}
		else
		{
			select.AND().WHERE("UNIT IS NULL");
		}
		if (locationName != null && !locationName.equals(""))
		{
			select.AND().WHERE("LOCATION_NAME");
			parameters.add(locationName);
			parameterTypes.add(individual(Concepts.VARCHAR));
		}
		else
		{
			select.AND().WHERE("LOCATION_NAME IS NULL");
		}
		if (statusLimit != null)
		{
			Set<OWLNamedIndividual> statuses = reasoner().getInstances(
					statusLimit, false).getFlattened();
			if (!statuses.isEmpty())
			{
				List<String> fragments = new ArrayList<String>(statuses.size());
				for (OWLNamedIndividual status : statuses)
				{
					fragments.add("'" + status.getIRI().getFragment() + "'");
				}
				select.AND().WHERE("SR_STATUS")
						.IN(fragments.toArray(new String[fragments.size()]));
			}
		}
		statement.setSql(select);
		statement.setParameters(parameters);
		statement.setTypes(parameterTypes);
		RelationalStore store = getPersister().getStore();
		ThreadLocalStopwatch.now("DUPLICATE Query start");
		if (DBGSQL) {			
			ThreadLocalStopwatch.now("Query: \r\n" + select.SQL());
			System.out.println(Arrays.toString(parameters.toArray()));
			System.out.println(Arrays.toString(parameterTypes.toArray()));
		}
		return store.query(statement, Refs.tempOntoManager.resolve().getOWLDataFactory());		
	}

	@POST
	@Path("/relatedCases")
	@Produces("application/json")
	public Json relatedCasesCheckService(@FormParam("data") String formData)
	{
		if(DBG) ThreadLocalStopwatch.startTop("START relatedCases " + formData);
		Json json = Json.read(formData);
		String boid = json.has("boid") ? json.at("boid").asString() : null;
		String hasCaseNumber = json.has("hasCaseNumber") ? 
				json.at("hasCaseNumber").asString() : null;
		Json result = ok();
		Set<Map<String, Object>> relatedCases = null;
		try {
			Set<String> boids = new HashSet<String>();
			for(Json j : json.at("boid").asJsonList())
				boids.add(j.asString());
			if(!boids.isEmpty())
				relatedCases = getAllRelatedCases(boids, boid, hasCaseNumber);
			if (relatedCases == null)
			{
				result.set("count", 0)
						.set("message", "no relatedCases")
						.set("details", Json.array());
			}
			else
			{
				result.set("count", relatedCases.size());
				Json details = Json.array();
				for (Map<String, Object> rc : relatedCases)
				{
					details.add(Json.make(rc));
				}
				result.set("details", details).set("message", "Success");
			}
			return result;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return ko(e);
		}
		finally
		{
			if (DBG) ThreadLocalStopwatch.getWatch().time("END relatedCases");
			ThreadLocalStopwatch.dispose();
		}
	}
	
	private Set<Map<String, Object>> getAllRelatedCases(Set<String> boids, 
			String boid, String hasCaseNumber) throws Exception
	{
		Set<Map<String, Object>> results = new HashSet<Map<String, Object>>();
		results.addAll(getParentCases(boids));
		if(boid != null && hasCaseNumber != null)
			results.addAll(getChildCases(boid, hasCaseNumber));
		return results;
	}
	
	private List<Map<String, Object>> getParentCases(Set<String> boids) 
			throws Exception
	{
		Statement statement = new Statement();
		List<Object> parameters = new ArrayList<Object>();
		List<OWLNamedIndividual> parameterTypes = new 
				ArrayList<OWLNamedIndividual>();
		Sql select = SELECT();
		select
			.FROM("CIRM_SR_REQUESTS_VIEW srv, " +
					"CIRM_DATA_PROPERTY_VIEW dpv, CIRM_IRI iri")
			.COLUMN("srv.SR_REQUEST_ID").AS("\"boid\"")
			.COLUMN("iri.IRI").AS("\"type\"")
			.COLUMN("srv.FULL_ADDRESS").AS("\"fullAddress\"")
			.COLUMN("srv.UNIT").AS("\"Street_Unit_Number\"")
			.COLUMN("srv.SR_STATUS").AS("\"hasStatus\"")
			.COLUMN("srv.CREATED_DATE").AS("\"hasDateCreated\"")
			.COLUMN("srv.CASE_NUMBER").AS("\"hasCaseNumber\"")
			.WHERE("dpv.PREDICATE_ID").EQUALS("?")
			.AND().WHERE("dpv.TO_DATE is null")
			.AND().WHERE("dpv.SUBJECT_ID").IN(
					boids.toArray(new String[boids.size()]))
			.AND().WHERE("srv.SR_TYPE").EQUALS("iri.ID")
			.AND().WHERE("TO_CHAR(SRV.SR_REQUEST_ID)").EQUALS("dpv.VALUE_VARCHAR")
			.OR_ARRAY().WHERE("SRV.CASE_NUMBER").EQUALS("dpv.VALUE_VARCHAR");
		
		parameters.add(dataProperty("legacy:hasParentCaseNumber"));
		parameterTypes.add(individual(Concepts.INTEGER));

		statement.setSql(select);
		statement.setParameters(parameters);
		statement.setTypes(parameterTypes);
		RelationalStore store = getPersister().getStore();
		if (DBG)
			System.out.println("ParentCases");
		if (DBGSQL)
			System.out.println("" + select.SQL());
		return store.query(statement, 
				Refs.tempOntoManager.resolve().getOWLDataFactory());
	}

	private List<Map<String, Object>> getChildCases(String boid, 
			String hasCaseNumber) throws Exception 
	{
		Statement statement = new Statement();
		List<Object> parameters = new ArrayList<Object>();
		List<OWLNamedIndividual> parameterTypes = new 
				ArrayList<OWLNamedIndividual>();
		Sql select = SELECT();
		select
			.FROM("CIRM_SR_REQUESTS_VIEW srv, CIRM_DATA_PROPERTY_VIEW dpv, " +
					"CIRM_IRI iri")
			.COLUMN("srv.SR_REQUEST_ID").AS("\"boid\"")
			.COLUMN("iri.IRI").AS("\"type\"")
			.COLUMN("srv.FULL_ADDRESS").AS("\"fullAddress\"")
			.COLUMN("srv.UNIT").AS("\"Street_Unit_Number\"")
			.COLUMN("srv.SR_STATUS").AS("\"hasStatus\"")
			.COLUMN("srv.CREATED_DATE").AS("\"hasDateCreated\"")
			.COLUMN("srv.CASE_NUMBER").AS("\"hasCaseNumber\"")
			.WHERE("dpv.PREDICATE_ID").EQUALS("?")
			.AND().WHERE("dpv.TO_DATE is null")
			.AND().WHERE("dpv.SUBJECT_ID").EQUALS("srv.SR_REQUEST_ID")
			.AND().WHERE("srv.SR_TYPE").EQUALS("iri.ID")
			.AND().WHERE("dpv.VALUE_VARCHAR").EQUALS("?")
			.OR_ARRAY().WHERE("dpv.VALUE_VARCHAR").EQUALS("?");
		
		parameters.add(dataProperty("legacy:hasParentCaseNumber"));
		parameterTypes.add(individual(Concepts.INTEGER));
		parameters.add(boid);
		parameterTypes.add(individual(Concepts.VARCHAR));
		parameters.add(hasCaseNumber);
		parameterTypes.add(individual(Concepts.VARCHAR));
		
		statement.setSql(select);
		statement.setParameters(parameters);
		statement.setTypes(parameterTypes);
		RelationalStore store = getPersister().getStore();
		if (DBG)
			System.out.println("ChildCases");
		if (DBGSQL)
			System.out.println("" + select.SQL());
		return store.query(statement, 
				Refs.tempOntoManager.resolve().getOWLDataFactory());
	}
	

	/**
	 * Creates a new Service Request.
	 * 
	 * 04/07/2015 -Sabbas - Annotated as a REST endpoint to provide access to a clean SR save with no SideEffects, 
	 * except that due date will be set, if not provided.
	 *  
	 * @param sr : The Service Request data as json
	 * @return : returns the SR
	 * 
	 */
	@POST
	@Path("sr")
	@Produces("application/json")
	@Consumes("application/json")
	public Json saveNewServiceRequestEndpoint(Json sr)
	{ 
		ThreadLocalStopwatch.startTop("START saveNewServiceRequestEndpoint");
		Json result = saveNewServiceRequest(sr.toString());
		ThreadLocalStopwatch.stop("END saveNewServiceRequestEndpoint");
		return result;
	}
	
	/**
	 * Get the current Approval State of the SR. 
	 *
	 * 
	 * @param caseNumber : The Service Request data stringified json
	 * @return : returns the Business Ontology which is persisted to db in Json format
	 * 
	 */
	
	@GET
	@Path("sr/{caseNumber}/approvalState")
	@Produces("application/json")
	public Json getApprovalState(@PathParam("caseNumber") String caseNumber)
	{	Json result;
		ThreadLocalStopwatch.startTop("START approvalState " + caseNumber);
		try {
			Json sr = OWL.prefix(lookupByCaseNumber(caseNumber));
			ApprovalProcess approvalProcess = new ApprovalProcess();
			approvalProcess.setSr(sr);
			result = Json.object().set("caseNumber", caseNumber)
					.set("approvalState", approvalProcess.getApprovalState().toString());
			ThreadLocalStopwatch.stop("END approvalState");
		} catch (Exception e) {
			result = ko(e);
			ThreadLocalStopwatch.stop("FAIL approvalState");
		}
		return result;
	}
	
	/**
	 * Approves an existing service request.
	 * 
	 * @param formData : The Service Request data stringified json
	 * @return : returns the Business Ontology which is persisted to db in Json format
	 * 
	 */
	@POST
	@Path("sr/approve")
	@Produces("application/json")
	@Consumes("application/json")
	public Json approveCase(final Json legacyform)
	{
		ThreadLocalStopwatch.startTop("START approveCase");
		//We check if another user approved the case already after this user loaded the case to ensure
		//the case is only approved once. This must be done in the same transaction as the approval.
		//Status history of the case would need to be checked, because certain users can set a case from Locked to pending,
		//but we allow this for now until we are certain that this would never be needed.
		//For non-interface cases it may not be a problem and certain interface situations are thinkable,
		//where a repeated approval would make sense.
		try
		{
			return Refs.defaultRelationalStore.resolve().txn(new CirmTransaction<Json>() {
			public Json call()
			{				
				Json result = null; 
				Json bo = legacyform.has("bo")? legacyform.at("bo") : legacyform;
				long boid = bo.at("boid").asLong();
				//Check if another user has approved case in the meantime.
				Json currentSR = lookupServiceCase(boid);
				String currentStatus = currentSR.at("bo").at("properties").at("hasStatus").at("iri").asString();
				if (!currentStatus.contains("O-PENDNG")) {
					return ko("The status of the SR you tried to approve was modified by another user and is not Pending anymore.\n Please reload SR.");
				} else {
					//Still pending, approve case
					ThreadLocalStopwatch.stop("NOW SR is still in pending, approving with side effects");
    				ApprovalProcess approvalProcess = new ApprovalProcess();
    				approvalProcess.setSr(legacyform);
    				approvalProcess.getSideEffects().add(new AttachSendEmailListener());
    				approvalProcess.getSideEffects().add(new CreateDefaultActivities());
    				approvalProcess.getSideEffects().add(new PopulateGisData());
    				approvalProcess.getSideEffects().add(new SaveOntology());
    				approvalProcess.getSideEffects().add(new CreateNewSREmail());
    				approvalProcess.getSideEffects().add(new AddTxnListenerForNewSR());
    				try {
    					approvalProcess.approve();
    				} catch (Exception e) {
    					throw new RuntimeException("Exception during approve SR", e);
    				}
    				ThreadLocalStopwatch.stop("END approveCase");
    				result = ok().set("bo", approvalProcess.getBOntology().toJSON());
    				return result;
				}
			}});			
		}catch(Exception e)
		{
			ThreadLocalStopwatch.fail("FAIL approveCase");
			return ko(e);
		}
			
	}
}

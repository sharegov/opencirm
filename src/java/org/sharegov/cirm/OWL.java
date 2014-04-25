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
package org.sharegov.cirm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import mjson.Json;

import org.hypergraphdb.HGEnvironment;
import org.hypergraphdb.app.owl.HGDBOWLManager;
import org.hypergraphdb.app.owl.HGDBOntologyFactory;
import org.hypergraphdb.app.owl.core.OWLDataFactoryHGDB;
import org.hypergraphdb.app.owl.core.OWLDataFactoryInternalsHGDB;
import org.hypergraphdb.app.owl.core.OWLObjectHGDB;
import org.hypergraphdb.app.owl.versioning.distributed.VDHGDBOntologyRepository;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataAllValuesFrom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataHasValue;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLDataPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLDataRange;
import org.semanticweb.owlapi.model.OWLDataSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLDatatypeRestriction;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectAllValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectComplementOf;
import org.semanticweb.owlapi.model.OWLObjectHasValue;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectInverseOf;
import org.semanticweb.owlapi.model.OWLObjectOneOf;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectUnionOf;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLProperty;
import org.semanticweb.owlapi.model.OWLPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLPropertyAssertionObject;
import org.semanticweb.owlapi.model.OWLPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLPropertyExpression;
import org.semanticweb.owlapi.model.OWLSubObjectPropertyOfAxiom;
import org.semanticweb.owlapi.model.PrefixManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.util.OWLEntityRemover;
import org.semanticweb.owlapi.util.ShortFormProvider;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.owl.Model;
import org.sharegov.cirm.owl.OWLObjectPropertyCondition;
import org.sharegov.cirm.owl.OwlRepo;
import org.sharegov.cirm.owl.SynchronizedOWLManager;
import org.sharegov.cirm.owl.SynchronizedOWLOntologyManager;
import org.sharegov.cirm.rest.OntoAdmin;
import org.sharegov.cirm.utils.Base64;
import org.sharegov.cirm.utils.CustomOWLOntologyIRIMapper;
import org.sharegov.cirm.utils.DLQueryParser;
import org.sharegov.cirm.utils.GenUtils;

/**
 * <p>
 * Represents the OWL environment used by the applications.
 * </p>
 * 
 * <p>
 *  Also contains static utility methods for working with OWL.
 * </p>
 *
 * @author Borislav Iordanov
 *
 */
public class OWL
{	
	public static boolean DBG_HGDB_ENTITY_CREATION = false;
			
	private static volatile OWLObjectPropertyCondition DEFAULT_STOP_EXPANSION_CONDITION; 
	public static final IRI DEFAULT_STOP_EXPANSION_CONDITION_IRI = Model.legacy("providedBy"); 
	public static final IRI DEFAULT_STOP_EXPANSION_CONDITION_IRI2 = Model.legacy("hasChoiceValue"); 

	private static volatile OWLOntologyManager manager;
	private static volatile OWLDataFactory factory;
	private static volatile OntologyLoader loader;
	private static volatile PrefixManager prefixManager = null;
	private static volatile boolean initialized = false;
	
	private static void init()
	{
		if (!initialized) {
			initChecked();
		}
	}
	
	private static synchronized void initChecked() 
	{
		if (!initialized) 
		{
			if (StartUp.config.has("metaDatabaseLocation"))
			{
				if (!VDHGDBOntologyRepository.hasInstance())
				{
					//Initialisation of repository location before everything else:
					if (HGEnvironment.exists(StartUp.config.at("metaDatabaseLocation").asString()))
					{
						VDHGDBOntologyRepository.setHypergraphDBLocation(StartUp.config.at("metaDatabaseLocation").asString());
					}
					else
					{
						//Refs clinit will trigger a call to owl.init
						OwlRepo repo = Refs.owlRepo.resolve();
						if (!VDHGDBOntologyRepository.hasInstance())
						{
							// TODO: hard-coded list, we need to have this in bootstrap JSON config.
							repo.createRepositoryFromDefaultPeer(
								StartUp.config.at("metaDatabaseLocation").asString(), 
								new HashSet<IRI>(Arrays.asList(
										IRI.create("http://www.miamidade.gov/ontology"),
										IRI.create("http://www.miamidade.gov/cirm/legacy"))));
						}
					}
					VDHGDBOntologyRepository.getInstance();
				}
				manager = SynchronizedOWLOntologyManager.synchronizedManager(HGDBOWLManager.createOWLOntologyManager());
				if (DBG_HGDB_ENTITY_CREATION) 
					OWLDataFactoryInternalsHGDB.DBG = true;
			}
			else
				manager = SynchronizedOWLManager.createOWLOntologyManager();
			manager.setSilentMissingImportsHandling(false);
			//factory = SynchronizedOWLDataFactory.synchronizedFactory(manager.getOWLDataFactory());
			factory = manager.getOWLDataFactory();
			//Assert Factory is SynchronizedOWLDataFactory or HGDB and threadsafe.
			OWLDataFactoryHGDB.getInstance().ignoreOntologyScope(true);
			loader = new OntologyLoader(manager);	
			if (StartUp.config.has("customIRIMappingFile"))
			{
				String customIRIMappingFile = StartUp.config.at("customIRIMappingFile").asString();
				manager.addIRIMapper(CustomOWLOntologyIRIMapper.createFrom(new File(customIRIMappingFile)));
			}		
			DEFAULT_STOP_EXPANSION_CONDITION = getInitializedDefaultStopExpansionCondition(factory); 
			initialized = true;
		}
	}
	
	private static OWLObjectPropertyCondition getInitializedDefaultStopExpansionCondition(OWLDataFactory factory) 
	{
		Set<OWLObjectProperty> stopExpansionProps = new HashSet<OWLObjectProperty>();
		stopExpansionProps.add(factory.getOWLObjectProperty(DEFAULT_STOP_EXPANSION_CONDITION_IRI));
		stopExpansionProps.add(factory.getOWLObjectProperty(DEFAULT_STOP_EXPANSION_CONDITION_IRI2));
		return new OWLObjectPropertyCondition(stopExpansionProps);
	}
	
	public static OWLOntologyManager manager() { init(); return manager; }
	public static OntologyLoader loader() { init(); return loader; }
	public static PrefixManager prefixManager()
	{
		init();
		// We don't care about 
		if (prefixManager == null)
		{
			
			DefaultPrefixManager pm = new DefaultPrefixManager(manager.getOntologyFormat(ontology()).asPrefixOWLOntologyFormat());
			if (StartUp.config.has("ontologyPrefixes"))
				for (Map.Entry<String, Json> e : StartUp.config.at("ontologyPrefixes", Json.object()).asJsonMap().entrySet())
					if (!e.getKey().equals(":") && pm.getPrefix(e.getKey()) != null && !e.getValue().asString().equals(pm.getPrefix(e.getKey())))
						throw new RuntimeException("Prefix clash between default ontology and startup configuration: " + e.getKey());
					else
						pm.setPrefix(e.getKey(), e.getValue().asString());
			prefixManager = pm;
//			if (!StartUp.config.has("metaDatabaseLocation") && manager.getOntologyFormat(ontology()) != null && 
//					manager.getOntologyFormat(ontology()).isPrefixOWLOntologyFormat())
//				prefixManager = new DefaultPrefixManager(manager.getOntologyFormat(ontology()).asPrefixOWLOntologyFormat());
//			else
//			{
//				DefaultPrefixManager pm = new DefaultPrefixManager();
//				for (Map.Entry<String, Json> e : StartUp.config.at("ontologyPrefixes", Json.object()).asJsonMap().entrySet())
//					pm.setPrefix(e.getKey(), e.getValue().asString());
//				prefixManager = pm;
//			}
		}
		return prefixManager;
	}
	
	public static void saveOntology(OWLOntology ontology, File destination)
	{
		FileOutputStream out = null;
		try
		{
			out = new FileOutputStream(destination);
			ontology.getOWLOntologyManager().saveOntology(ontology, out);			
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
		finally
		{
			if (out != null) { try { out.close(); } catch (Throwable t) { } }
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T findImplementation(Class<T> clazz, IRI indIri)
	{
		OWLNamedIndividual I  = individual(indIri);
		for (OWLIndividual x : I.getObjectPropertyValues(objectProperty("hasImplementation"), ontology()))
		{
			OWLNamedIndividual impl = (OWLNamedIndividual)x;
			Class<?> cl;
			try
			{
				cl = Class.forName(impl.getIRI().getFragment());
				if (!clazz.isAssignableFrom(cl))
					continue;
				return (T)cl.newInstance();		
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}				
		}
		return null;
	}
		
	@SuppressWarnings("unchecked")
	public static <T> T getImplementation(IRI indIri)
	{
		OWLNamedIndividual ind = individual(indIri);
		OWLNamedIndividual implIndividual = (OWLNamedIndividual)
							objectProperty(ind, "hasImplementation");
		if (implIndividual == null)
			throw new RuntimeException("Missing 'hasImplementation' property for individual " + 
									   indIri);		
		Class<?> cl;
		try
		{
			cl = Class.forName(implIndividual.getIRI().getFragment());
			return (T)cl.newInstance();		
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}				
	}
	
	public static OWLDataFactory dataFactory()
	{
		init();
		return factory;
	}
	
	private static Pattern prefixedPattern = Pattern.compile("\\w+\\:[A-Za-z0-9\\%\\-\\._~]+");
	
	public static IRI fullIri(String s)
	{
		if (prefixedPattern.matcher(s).matches() && !s.startsWith("mailto:"))
		{
			return prefixManager().getIRI(s);
		}
		return fullIri(s, StartUp.config.at("nameBase").asString());
	}
	
	public static IRI fullIri(String s, String ontologyIri)
	{
		IRI oi = IRI.create(ontologyIri);
		if (s == null || s.length() == 0)
			return oi;
		else if (s.charAt(0) == '/' || s.charAt(0) == '#')
			return oi.resolve(s);
		else if (s.startsWith("mailto:"))
			return IRI.create(s);
		else if (!s.startsWith("http://"))
			return oi.resolve("#" + s);
		else
			return IRI.create(s);
	}
	
	public static IRI businessObjectId(String type, String id)
	{
		return IRI.create(Refs.boIriPrefix.resolve() + type + "/" + id + "#bo");		
	}
	
	public static String businessObjectId(OWLOntology bonto)
	{
		String A [] = bonto.getOntologyID().getOntologyIRI().toString().split("/");
		return A[A.length - 1];
	}
	
	/**
	 * Checks, if the IRI's fragment is "bo".
	 * @param iri
	 * @return
	 */
	public static boolean isBusinessObject(IRI iri)
	{
		return "bo".equals(iri.getFragment());
	}

	public static boolean isBusinessObjectOntology(OWLOntology O)
	{
		String iri = O.getOntologyID().getOntologyIRI().toString();
		String [] parts = iri.split("/");
		return iri.indexOf("miamidade.gov/bo") > -1 && parts.length > 0 && 
				parts[parts.length - 1].matches("\\d+");
	}
	
	public static boolean isBusinessObject(OWLIndividual ind)
	{
		return ind instanceof OWLNamedIndividual &&
			   isBusinessObject(((OWLNamedIndividual)ind).getIRI());
	}
	
	/**
	 * Parses the id value as long value from a business ontology IRI that ends with /ANYLONGNUMBER#bo.
	 * @param boIri
	 * @return an id as long value parsed between "/" and "#bo".
	 * @throws NumberFormatException, IndexOutOfBounds if 
	 */
	public static long parseIDFromBusinessOntologyIRI(IRI boIri) {
		String boIriStr = boIri.toString();
		int poundSignIndex = boIriStr.lastIndexOf("#bo");
		int lastSlashIndex = boIriStr.lastIndexOf('/');
		String idStr = boIriStr.substring(lastSlashIndex + 1, poundSignIndex);
		long id = Long.parseLong(idStr);
		return id;
	}
	
	public static OWLNamedIndividual businessObject(OWLOntology boOntology)
	{
		//System.out.println(boOntology.getOntologyID().getOntologyIRI().resolve("#bo"));
		//System.out.println(dataFactory());
		return boOntology.getOWLOntologyManager().getOWLDataFactory().getOWLNamedIndividual(boOntology.getOntologyID().getOntologyIRI().resolve("#bo"));	
	}
	/**
	 * Will return an OWLClass with an iri based on iriBase
	 * 
	 * @param boIri
	 * @return
	 */
	public static OWLClass businessObjectType(IRI boIri)
	{
		// boIri.toURI().getPath() should give something like "/bo/CLASSNAME/BOID"
		return owlClass(boIri.toURI().getPath().split("/")[2]);
	}
	
	public static OWLReasoner reasoner()
	{
		return reasoner(ontology());
	}
	
	public static OWLReasoner reasoner(OWLOntology ontology)
	{
		return loader().getReasoner(ontology);
	}
	
	public static OWLOntology ontology(String iri)
	{
		return OWL.loader().get(iri);
	}
	
	public static OWLOntology ontology()
	{
		return loader().get(Refs.defaultOntologyIRI.resolve());		
	}	
	
	public static Set<OWLOntology> ontologies()
	{
		return ontology().getImportsClosure();
	}
	
	/**
	 * Returns the NamedIndividual for a given IRI fragment by assuming the fragment is unique for
	 * legacy and county ontology. The import closure of meta will be scanned.
	 * The method will retrurn the first matched named individual and ignore if more than one exists.
	 * 
	 * @throws IllegalStateException, if no named individual could be found.
	 * @param iRIfragment an fragment of an IRI. Without pound sign.
	 * @param factory
	 * @return the found NamedIndividual or NULL if not found.
	 */
	public static OWLNamedIndividual findNamedIndividualByFragment(String iRIfragment) {
		Map<String, Object> ontoPrefixes = StartUp.config.at("ontologyPrefixes").asMap();
		for (Object prefixIri : ontoPrefixes.values()) {
			IRI indIRI = IRI.create("" + prefixIri + iRIfragment);
			if (ontology().containsIndividualInSignature(indIRI, true)) {
				return individual(indIRI);
			}
		}
		System.err.println("getNamedIndividualFromFragment: No individual in any loaded ontologies could be found for iriFragment " + iRIfragment + " returning null.");
		return null;
		//throw new IllegalStateException("No individual in any loaded ontologies could be found for iriFragment " + iRIfragment);
	}
	/**
	 * <p>Return the main ontology or this business object's own ontology
	 * if this is a business object indeed (i.e. its IRI follows the naming
	 * convention for business objects.)
	 * </p>
	 */
/*	public static OWLOntology ontology(OWLNamedIndividual object)
	{
		if (isBusinessObject(object.getIRI()))
			return new OperationService().getBusinessObjectOntology(object.getIRI()).getOntology();
		else
			return ontology();
	} */
	
	public static OWLNamedIndividual individual(IRI id) 
	{
		if (id == null) throw new NullPointerException("IRI id was null");
		return dataFactory().getOWLNamedIndividual(id);
	}

	public static boolean isObjectProperty(IRI id)
	{
		OWLObjectProperty prop = dataFactory().getOWLObjectProperty(id);
		for (OWLOntology o : ontologies())
		{
			if (o.isDeclared(prop))
				return true;
		}
		return false;		
	}
	
	public static boolean isDataProperty(IRI id)
	{
		OWLDataProperty prop = dataFactory().getOWLDataProperty(id);
		for (OWLOntology o : ontologies())
		{
			if (o.isDeclared(prop))
				return true;
		}
		return false;		
	}
	
	public static boolean isAnnotation(IRI id)
	{
		OWLAnnotationProperty prop = dataFactory().getOWLAnnotationProperty(id);
		for (OWLOntology o : ontologies())
		{
			if (o.isDeclared(prop))
				return true;
		}
		return false;
	}
	
	public static OWLObjectProperty objectProperty(IRI id)
	{
		return dataFactory().getOWLObjectProperty(id);
	}
	
	public static OWLDataProperty dataProperty(IRI id)
	{
		return dataFactory().getOWLDataProperty(id);
	}	

	public static OWLNamedIndividual objectProperty(OWLNamedIndividual ind, 
													String id,
													OWLOntology O)
	{
		Set<OWLIndividual> S = ind.getObjectPropertyValues(objectProperty(id), O);
		return S.isEmpty() ? null : S.iterator().next().asOWLNamedIndividual();
	}
	
	/**
	 * 
	 * @param ind
	 * @param id
	 * @return the first objectPropertyValue the reasoner returns for the given ind and objectProperty name (id).
	 */
	public static OWLNamedIndividual objectProperty(OWLNamedIndividual ind, String id)
	{
		OWLObjectProperty prop = objectProperty(id);
		if (prop == null)
			throw new NullPointerException("No object property with ID '" + id + "'");
		Set<OWLNamedIndividual> S = reasoner().getObjectPropertyValues(ind, prop).getFlattened();
		if (S.isEmpty())
			return null;
		else
			return S.iterator().next();
	}
	
	public static Set<OWLAnnotation> annotations(OWLNamedIndividual ind)
	{
	    Set<OWLAnnotation> S = new HashSet<OWLAnnotation>();
	    for (OWLOntology O : ontology().getImportsClosure())
	        S.addAll(ind.getAnnotations(O));
	    return S;
	}

	public static Map<OWLPropertyExpression<?,?>, Set<OWLObject>> allProperties(OWLIndividual ind)
	{
		return allProperties(ind, ontology(), true, true);
	}

	public static Map<OWLPropertyExpression<?,?>, Set<OWLObject>> allProperties(OWLIndividual ind, OWLOntology ontology)
	{
		return allProperties(ind, ontology, true, true);
	}
	
	@SuppressWarnings("unchecked")
	public static Map<OWLPropertyExpression<?,?>, Set<OWLObject>> allProperties(OWLIndividual ind, 
																		   OWLOntology ontology, 
																		   boolean collectDataProperties,
																		   boolean collectObjectProperties)
	{
		HashMap<OWLPropertyExpression<?,?>, Set<OWLObject>> M = 
			new HashMap<OWLPropertyExpression<?,?>, Set<OWLObject>>();
		for (OWLOntology o : ontology.getImportsClosure())
		{
			// Need to add one by one and make sure entries of one ontology don't overwrite entries 
			// another in the import chain.
			if (collectObjectProperties) for (Map.Entry<OWLObjectPropertyExpression, Set<OWLIndividual>> e : ind.getObjectPropertyValues(o).entrySet())
			{
				Set<OWLObject> S = M.get(e.getKey());
				if (S == null)
					M.put(e.getKey(), (Set<OWLObject>)(Set<?>)e.getValue());
				else
					S.addAll(e.getValue());
			}
			if (collectDataProperties) for (Map.Entry<OWLDataPropertyExpression, Set<OWLLiteral>> e : ind.getDataPropertyValues(o).entrySet())
			{
				Set<OWLObject> S = M.get(e.getKey());
				if (S == null)
					M.put(e.getKey(), (Set<OWLObject>)(Set<?>)e.getValue());
				else
					S.addAll(e.getValue());
			}
		}
		return M;
	}
	
	@SuppressWarnings("unchecked")
	public static Set<OWLLiteral> dataProperties(OWLNamedIndividual ind, String id)
	{
		OWLDataProperty prop = dataProperty(id);
		if (prop == null)
			throw new NullPointerException("No data property with ID '" + id + "'");
		Map<OWLPropertyExpression<?, ?>, Set<OWLObject>> props = allProperties(ind, ontology(), true, false);
		Set<OWLLiteral> S = (Set<OWLLiteral>)(Set<?>)props.get(prop);
		return S == null ? Collections.EMPTY_SET : S;
		//return reasoner().getDataPropertyValues(ind, prop);
	}
	
	public static OWLLiteral dataProperty(OWLNamedIndividual ind, String id)
	{
		Set<OWLLiteral> S = dataProperties(ind, id);
		if (S.isEmpty())
			return null;
		else
			return S.iterator().next();
	}
	
	public static OWLClass owlClass(IRI id)
	{
		return dataFactory().getOWLClass(id);
	}
	
	public static OWLNamedIndividual individual(String id) 
	{
		return dataFactory().getOWLNamedIndividual(fullIri(id));
	}
	
	public static OWLLiteral literal(String literal) 
	{
		return dataFactory().getOWLLiteral(literal);
	}

	public static OWLObjectProperty objectProperty(String id)
	{
		return dataFactory().getOWLObjectProperty(fullIri(id));
	}
	
	public static OWLDataProperty dataProperty(String id)
	{
		return dataFactory().getOWLDataProperty(fullIri(id));
	}	
	
	public static OWLClass owlClass(String id)
	{
		return owlClass(fullIri(id));
	}
		
	public static OWLAnnotationProperty annotationProperty(String id)
	{
		return annotationProperty(fullIri(id));
	}
	
	public static OWLAnnotationProperty annotationProperty(IRI id)
	{
		return dataFactory().getOWLAnnotationProperty(id);
	}
	
	public synchronized static String getAnnotation(OWLEntity entity, OWLAnnotationProperty aprop)
	{
		for (OWLOntology o : ontologies())
		{
			Set<OWLAnnotation> anns = entity.getAnnotations(o, aprop);
			if (!anns.isEmpty())
				return ((OWLLiteral)anns.iterator().next().getValue()).getLiteral();
		}
		return null;
	}

	public static synchronized String getEntityLabel(OWLOntology O, OWLEntity entity)
	{
		Set<OWLAnnotation> anns = entity.getAnnotations(O, 
				annotationProperty("http://www.w3.org/2000/01/rdf-schema#label"));
		if (!anns.isEmpty())
			return ((OWLLiteral)anns.iterator().next().getValue()).getLiteral();
		else
			return entity.getIRI().getFragment();
		
	}
	
	public static synchronized String getEntityLabel(OWLEntity entity)
	{
		for (OWLOntology o : ontologies())
		{
			Set<OWLAnnotation> anns = entity.getAnnotations(o, 
					annotationProperty("http://www.w3.org/2000/01/rdf-schema#label"));
			if (!anns.isEmpty())
				return ((OWLLiteral)anns.iterator().next().getValue()).getLiteral();
		}
		return entity.getIRI().getFragment();
	}
	
	public static Json annotate(OWLEntity object, Json x)
	{
		for (OWLOntology o : ontologies())
		{
			x = annotate(o, object, x, null);
		}
		return x;
	}

	public static Json annotate(OWLEntity object, Json x, ShortFormProvider sp)
	{
		for (OWLOntology o : ontologies())
		{
			x = annotate(o, object, x, sp);
		}
		return x;
	}

	public static synchronized Json annotate(OWLOntology ontology, OWLEntity object, Json x)
	{
		return annotate(ontology, object, x, null);
	}

	public static synchronized Json annotate(OWLOntology ontology, OWLEntity object, Json x, ShortFormProvider sp)
	{
		if (sp == null) sp = OWLObjectMapper.DEFAULT_SHORTFORM_PROVIDER;
		Set<OWLAnnotation> annotations = object.getAnnotations(ontology);
		for (OWLAnnotation ann : annotations)
		{
			if (ann.getProperty() == null || ann.getValue() == null)
				continue;
			String annName = sp.getShortForm(ann.getProperty()); //was .getIRI().getFragment();
			if (annName == null)
			{
				String [] A = ann.getProperty().getIRI().toString().split("/");
				annName = A[A.length - 1];
			}
			String annValue = ((OWLLiteral)ann.getValue()).getLiteral();
			x.set(annName, annValue);
		}
		if (!x.has("label"))
			x.set("label", sp.getShortForm(object));  // was object.getIRI().getFragment());
		return x;
	}


	/**
	 * Convert a Json value to an OWL literal using the first range of the 
	 * the data property. If the dataproperty has no ranges defined, the given OWL2Datatype is used.
	 * If a OWL2Datatype is given and the dataproperty has ranges defined a match is enforced.
	 * If no OWL2Datatype is given, the first range that is an OWLDatatype will be used for the literal.
	 * (see @link #toLiteral(OWLDataRange, Json) for details on how the mapping
	 * is performed).
	 * @param prop
	 * @param value
	 * @param builtinDatatype if null, first range will become literal datatype; if given it must match range.
	 * @return an OWlLiteral or null, if the range does not match the given datatype or no OWLDatatype was found in the range.
	 */
	public static OWLLiteral toLiteral(OWLDataProperty prop, String value, OWL2Datatype builtinDatatype)
	{		
		// Parse out if the value is an ISO date and convert it to the format XML datafactory accepts.
		if (builtinDatatype == null)
		{
			try  { return OWL.dateLiteral(GenUtils.parseDate(value), OWL2Datatype.XSD_DATE_TIME_STAMP); }
			catch (Throwable t) { }
		}
		
		//TODO we could validate here, if the value string matches the builtinDatatype.
		Set<OWLDataRange> ranges = prop.getRanges(ontologies());
		if (ranges.isEmpty() && builtinDatatype != null) {
			return dataFactory().getOWLLiteral(value, builtinDatatype);
		}
		for (OWLDataRange range : ranges)
		{
			if ((builtinDatatype == null && range instanceof OWLDatatype)
					|| (builtinDatatype != null && range.equals(builtinDatatype))) 
			{
					return dataFactory().getOWLLiteral(value, (OWLDatatype)range);
			}
		}
		return null;
	}
	
//	public static OWLLiteral toLiteral(OWL2Datatype builtinDatatype, Json value)
//	{
//		if (range instanceof OWLDatatype)
//		{
//			String v = value.asString();
////			OWLDatatype type = (OWLDatatype)range;
////			if (type.isBoolean())
////			{
////				if (v.equalsIgnoreCase("on") || v.equalsIgnoreCase("yes") ||
////						v.equalsIgnoreCase("t") ||
////						v.equalsIgnoreCase("true"))
////					v = "true";
////				else
////					v = "false";
////			}
//			return dataFactory().getOWLLiteral(v, builtinDatatype);
//		}
//		else
//			return null;
//	} 
	
	// forming class expressions
	public static OWLObjectSomeValuesFrom some(OWLObjectPropertyExpression prop, OWLClassExpression clexpr)
	{
		checkImplementation(prop, clexpr);
		return dataFactory().getOWLObjectSomeValuesFrom(prop, clexpr);
	}

	public static OWLObjectSomeValuesFrom someObject(String propName, OWLClassExpression clexpr)
	{
		checkImplementation(clexpr);
		return some(objectProperty(propName), clexpr);
	}
	
	public static OWLDataSomeValuesFrom some(OWLDataPropertyExpression prop, OWLDataRange range)
	{
		checkImplementation(prop, range);
		return dataFactory().getOWLDataSomeValuesFrom(prop, range);
	}

	public static OWLDataSomeValuesFrom someData(String prop, OWLDataRange range)
	{
		checkImplementation(range);
		return some(dataProperty(prop), range);
	}
	
	public static OWLDataHasValue has(OWLDataPropertyExpression prop, OWLLiteral literal)
	{
		checkImplementation(prop, literal);
		return dataFactory().getOWLDataHasValue(prop, literal);
	}
	
	public static OWLDataHasValue hasData(String prop, OWLLiteral literal)
	{
		checkImplementation(literal);
		return has(dataProperty(prop), literal);
	}
	
	public static OWLObjectHasValue has(OWLObjectPropertyExpression prop, OWLIndividual individual)
	{
		checkImplementation(prop, individual);
		return dataFactory().getOWLObjectHasValue(prop, individual);
	}
	
	public static OWLObjectHasValue hasObject(String prop, OWLIndividual individual)
	{
		checkImplementation(individual);
		return has(objectProperty(prop), individual);
	}
	
	public static OWLObjectAllValuesFrom only(OWLObjectPropertyExpression prop, OWLClassExpression cl)
	{
		checkImplementation(prop, cl);
		return dataFactory().getOWLObjectAllValuesFrom(prop, cl);
	}

	public static OWLDataAllValuesFrom only(OWLDataPropertyExpression prop, OWLDataRange range)
	{
		checkImplementation(prop, range);
		return dataFactory().getOWLDataAllValuesFrom(prop, range);
	}
	
	public static OWLObjectComplementOf not(OWLClassExpression cl)
	{
		checkImplementation(cl);
		return dataFactory().getOWLObjectComplementOf(cl);
	}
	
	public static OWLObjectIntersectionOf and(OWLClassExpression...classExpressions)
	{
		checkImplementation(classExpressions);
		return dataFactory().getOWLObjectIntersectionOf(classExpressions);
	}
	
	public static OWLObjectUnionOf or(OWLClassExpression...classExpressions)
	{
		checkImplementation(classExpressions);
		return dataFactory().getOWLObjectUnionOf(classExpressions);
	}
	
	public static OWLObjectOneOf oneOf(OWLIndividual...individuals)
	{
		checkImplementation(individuals);
		return dataFactory().getOWLObjectOneOf(individuals);
	} 
	
	/**
	 * <p>
	 * Using the main ontology imports closure, collect all object property values. This
	 * is normally performed by the reasoner (see {@link #objectProperties(OWLNamedIndividual, String)}
	 * method), but because of performance issues, when we know there aren't any properties inferred
	 * we can use this method.
	 * 
	 * </p>
	 * @param ind
	 * @param prop
	 * @return
	 */
    public static Set<OWLIndividual> collectObjectProperties(OWLIndividual ind, OWLObjectProperty prop)
    {
        Set<OWLIndividual> S = new HashSet<OWLIndividual>();
        for (OWLOntology O : ontology().getImportsClosure())
            S.addAll(ind.getObjectPropertyValues(prop, O));
        return S;
    }

	public static Set<OWLNamedIndividual> objectProperties(OWLNamedIndividual ind, String id)
	{
		 return reasoner().getObjectPropertyValues(ind, objectProperty(id)).getFlattened();
	}
	
	public static OWLObjectInverseOf inverse(OWLObjectPropertyExpression expr)
	{
		checkImplementation(expr);
		return dataFactory().getOWLObjectInverseOf(expr);
	}
	
	public static OWLObjectInverseOf inverse(String prop)
	{
		return inverse(objectProperty(prop));
	}
	
	public static String hash(String s)
	{
		try
		{
			MessageDigest algorithm = MessageDigest.getInstance("SHA-1");
			algorithm.reset();
			algorithm.update(s.getBytes());
			byte[] digest = algorithm.digest();
			// System.out.println("Digest size:" + digest.length);
			return Base64.encode(digest, false);
		}
		catch (NoSuchAlgorithmException e)
		{
			System.out.println("Cannot SHA-1 hash, algorithm not found.");
			e.printStackTrace();
		}
		return s;
	}
	
	public static String hash(OWLLiteral literal)
	{
		return hash(literal.getLiteral());
	}

	public static Map<String, OWLLiteral> hash(Map<OWLDataPropertyExpression, Set<OWLLiteral>> data)
	{
		Map<String, OWLLiteral> result = new HashMap<String, OWLLiteral>();
		for(Set<OWLLiteral> set : data.values())
		{
			for(OWLLiteral literal: set)
			{
				String hash = hash(literal);
				if(result.containsKey(hash) && !result.get(hash).equals(literal))
					System.err.println("Hash collision occured!!!Hash=" + hash + " value= "+ literal.getLiteral());
				else
					result.put(hash, literal);
			}
		}
		return result;
	}

	public static OWLPropertyAssertionAxiom<?, ?> assertProperty(OWLIndividual i, OWLProperty p, OWLPropertyAssertionObject value)
	{
		checkImplementation(i, p, value);
		if (p instanceof OWLDataProperty && value instanceof OWLLiteral)
			return (OWLPropertyAssertionAxiom<OWLDataPropertyExpression,OWLLiteral>) dataFactory().getOWLDataPropertyAssertionAxiom((OWLDataProperty) p, i ,(OWLLiteral) value);
		else if (p instanceof OWLObjectProperty && value instanceof OWLIndividual) 
			return  (OWLPropertyAssertionAxiom<OWLObjectPropertyExpression,OWLIndividual>) dataFactory().getOWLObjectPropertyAssertionAxiom((OWLObjectProperty) p, i ,(OWLIndividual) value);
		else return null;
	}
	
	public static OWLClassAssertionAxiom assertClass(OWLIndividual i, OWLClassExpression e)
	{
		checkImplementation(i, e);
		return dataFactory().getOWLClassAssertionAxiom(e, i);
	}
	
	public static Date add(Date start, float days, boolean useWorkWeek)
	{
		Date result = null;
		int seconds = (int) (86400 * days);
		Calendar c = Calendar.getInstance();
		Set<OWLLiteral> holidays = new HashSet<OWLLiteral>();
		
		for(OWLNamedIndividual holiday: reasoner()
				.getInstances(owlClass("Observed_County_Holiday"), false).getFlattened())
		{
				for(OWLLiteral date: reasoner().getDataPropertyValues(holiday, dataProperty("hasDate")))
				{
					if (date != null)
						holidays.add(date);
				}
		}
		c.setTime(start);
		if (!useWorkWeek)
		{
			c.add(Calendar.SECOND, seconds);
			result = c.getTime();
		}
		else
		{
			int diff = seconds % 86400; 
			for(int workSeconds = 0; workSeconds < seconds-diff;)
			{
				c.add(Calendar.SECOND, 86400);
				int dow = c.get(Calendar.DAY_OF_WEEK);
				if(!( dow == Calendar.SATURDAY 
						|| dow == Calendar.SUNDAY))
				{
					OWLLiteral literal = dateLiteral(c.getTime());
					if(!holidays.contains(literal))
						workSeconds = workSeconds + 86400;
				}
				
			}
			c.add(Calendar.SECOND, diff);
			result = c.getTime();
		}
		return result;
	}
	
	public static Date parseDate(OWLLiteral value)
	{
		return parseDate(value.getLiteral());
	}

	public static Date parseDate(String value)
	{
		try
		{
			// parse ISO 8601 date
			return DatatypeFactory.newInstance().newXMLGregorianCalendar(value).toGregorianCalendar().getTime();
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
	
	public static OWLLiteral dateLiteral(Date date, OWL2Datatype d)
	{
		OWLDataFactory factory = dataFactory();
		OWLLiteral result = factory.getOWLLiteral("", d);
		if(date == null)
			return result;
		try
		{
			//see:
			//http://download.oracle.com/javase/6/docs/api/javax/xml/datatype/XMLGregorianCalendar.html#getXMLSchemaType()
			Calendar c = Calendar.getInstance();
			c.setTime(date);
			XMLGregorianCalendar x = DatatypeFactory.newInstance().newXMLGregorianCalendar();
			if(c instanceof GregorianCalendar)
				if(DatatypeConstants.DATE.getNamespaceURI().equals(d.getIRI().toString()))
				{
					x.setYear(c.get(Calendar.YEAR));
					x.setMonth(c.get(Calendar.MONTH));
					x.setDay(c.get(Calendar.DAY_OF_MONTH));
					result = factory.getOWLLiteral(x.toXMLFormat(),d);
				}
				else
				   result = factory.getOWLLiteral(DatatypeFactory.newInstance().newXMLGregorianCalendar((GregorianCalendar)c).toXMLFormat(), d);
				
		}catch(Exception e)
		{
			System.out.println("Exception occured while attempting to extract a xsd date value");
			e.printStackTrace(System.err);
		}
		
		return result;
	}
	
	public static OWLLiteral dateLiteral(Date date)
	{
		OWLDataFactory factory = dataFactory();
		OWLLiteral result = factory.getOWLLiteral("", OWL2Datatype.XSD_DATE_TIME);
		if(date == null)
			return result;
		try
		{
			DateFormat f = new SimpleDateFormat("yyyy-MM-dd'T00:00:00'");
			result = factory.getOWLLiteral(f.format(date), OWL2Datatype.XSD_DATE_TIME);
		}catch(Exception e)
		{
			System.out.println("Exception occured while attempting to extract a xsd date value");
			e.printStackTrace(System.err);
		}
		return result;
	}
	
	public static String unescape(OWLLiteral literal){
		String str = literal.getLiteral();
		StringWriter out = new StringWriter(str.length() * 2);
		if (out == null) {
            throw new IllegalArgumentException("The Writer must not be null");
        }
        if (str == null) {
            return str;
        }
        int sz = str.length();
        StringBuffer unicode = new StringBuffer(4);
        boolean hadSlash = false;
        boolean inUnicode = false;
        for (int i = 0; i < sz; i++) {
            char ch = str.charAt(i);
            if (inUnicode) {
                // if in unicode, then we're reading unicode
                // values in somehow
                unicode.append(ch);
                if (unicode.length() == 4) {
                    // unicode now contains the four hex digits
                    // which represents our unicode character
                    try {
                        int value = Integer.parseInt(unicode.toString(), 16);
                        out.write((char) value);
                        unicode.setLength(0);
                        inUnicode = false;
                        hadSlash = false;
                    } catch (NumberFormatException nfe) {
                    	throw nfe;
                    }
                }
                continue;
            }
            if (hadSlash) {
                // handle an escaped value
                hadSlash = false;
                switch (ch) {
                    case '\\':
                        out.write('\\');
                        break;
                    case '\'':
                        out.write('\'');
                        break;
                    case '\"':
                        out.write('"');
                        break;
                    case 'r':
                        out.write('\r');
                        break;
                    case 'f':
                        out.write('\f');
                        break;
                    case 't':
                        out.write('\t');
                        break;
                    case 'n':
                        out.write('\n');
                        break;
                    case 'b':
                        out.write('\b');
                        break;
                    case 'u':
                        {
                            // uh-oh, we're in unicode country....
                            inUnicode = true;
                            break;
                        }
                    default :
                        out.write(ch);
                        break;
                }
                continue;
            } else if (ch == '\\') {
                hadSlash = true;
                continue;
            }
            out.write(ch);
        }
        if (hadSlash) {
            // then we're in the weird case of a \ at the end of the
            // string, let's output it anyway.
            out.write('\\');
        }
		return out.toString();
    }

	public static void deleteIndividuals(OWLOntology o, OWLClass cl)
	{
		OWLOntologyManager manager = o.getOWLOntologyManager();//manager();
		
		Set<OWLIndividual> S = cl.getIndividuals(o);
		for (OWLIndividual i : S)
		{
			System.out.println("Removing " + i);
			OWLEntityRemover remover = new OWLEntityRemover(manager, Collections.singleton(o));
			remover.visit(i.asOWLNamedIndividual());
			manager.applyChanges(remover.getChanges());
		}
	}
	
	public static void copyIndividuals(Set<OWLNamedIndividual> S, OWLOntology src, OWLOntology dest)
	{
		OWLOntologyManager manager = src.getOWLOntologyManager();//manager();
		List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
		for (OWLNamedIndividual i : S)
		{
			System.out.println("Adding " + i);
			for (OWLAxiom a : src.getAxioms(i))
				changes.add(new AddAxiom(dest, a));
			for (OWLAxiom ann : i.asOWLNamedIndividual().getAnnotationAssertionAxioms(src))
				changes.add(new AddAxiom(dest, ann));
			manager.applyChanges(changes);
		}
	}
	
	public static Json resolveIris(Json x, Map<String, Json> jsonMap)
	{
		if(jsonMap == null)
			jsonMap = gatherIris(x, null);
		//System.out.println("resolve`");
		if(x.isObject())
			for(Map.Entry<String, Json> entry: x.asJsonMap().entrySet())
			{
				if(entry.getValue().isString() && 
					!entry.getKey().equals("iri")
					&& entry.getValue().asString().startsWith(Refs.nameBase.resolve()) && 
					entry.getValue() != null )
				{
					try
					{
					x.set(entry.getKey(), jsonMap.get(entry.getValue().asString()));
					}
					catch (Throwable t)
					{
						System.out.println("here");
					}
				}
				else if(entry.getValue().isArray() || entry.getValue().isObject())
					resolveIris(entry.getValue(), jsonMap);
			}
		else if(x.isArray())
		{
			for (int i = 0; i < x.asJsonList().size(); i++)
			{
				Json el = x.at(i);
				if (el.isString() && 
					(el.asString().contains(Refs.nameBase.resolve())||
						el.asString().contains(Refs.defaultOntologyIRI.toString())) &&
						jsonMap.containsKey(el.asString()))
					x.asJsonList().set(i, jsonMap.get(el.asString()));
				else
					resolveIris(el, jsonMap);
			}
		}
		return x;
	}
	
	
	public static Map<String, Json> gatherIris(Json x, Map<String,Json> jsonMap)
	{
		if(jsonMap == null)
			jsonMap = new HashMap<String, Json>();
		if(x.isObject())
			for(Map.Entry<String, Json> entry: x.asJsonMap().entrySet())
			{
				if(entry.getKey().equals("iri"))
				{
					jsonMap.put(entry.getValue().asString(), x);
				}
				else if(entry.getValue().isArray() || entry.getValue().isObject())
				{
					gatherIris(entry.getValue(), jsonMap);
				}
				
			}
		else if(x.isArray())
			for(Json j : x.asJsonList())
			{
				gatherIris(j, jsonMap);
			}
		
		return jsonMap;
	}
	
	public static Json toBoJson(Json metaJson)
	{
		Json boJson = Json.object().set("type",metaJson.at("type")).set("boid",metaJson.at("boid")).set("iri",metaJson.at("iri"));
		Json properties = Json.object();
		if(metaJson.isObject())
		{
			for(Map.Entry<String, Json> entry: metaJson.asJsonMap().entrySet())
			{
				if(entry.getKey().equals("type") 
						|| entry.getKey().equals("boid") 
						|| entry.getKey().equals("label")
						|| entry.getKey().equals("iri"))
					continue;
				else 
					properties.set(entry.getKey(), entry.getValue());
			}
		}
		boJson.set("properties", properties);
		return boJson;
	}
	private static String unprefix(String s)
	{
		if (s.startsWith("http://") || s.startsWith("https://"))
			return s;
		int idx = s.indexOf(':');
		if (idx > 0)
			return s.substring(idx + 1);
		else
			return s;
	}
	
	public static Json unprefix(Json json)
	{
		if (json.isArray())
			for (Json x : json.asJsonList())
				unprefix(x);
		else if (json.isObject())
		{			
			for (String propname : json.dup().asJsonMap().keySet())
			{
				Json val = json.at(propname);
				boolean isdate = false;
				try { GenUtils.parseDate(val.asString()); isdate = true; } catch (Throwable t) {}
				if (val.isString() && !isdate)
					val = Json.make(unprefix(val.asString()));
				else if (val.isObject() || val.isArray())
					val = unprefix(val);
				json.delAt(propname).set(unprefix(propname), val);
			}
		}
		return json;
	}
	
	public static Json prefix(Json json)
	{
		Set<OWLProperty<?,?>> properties = new HashSet<OWLProperty<?,?>>();
		properties.addAll(ontology().getDataPropertiesInSignature(true));
		properties.addAll(ontology().getObjectPropertiesInSignature(true));
		return prefix(json, properties);
	}

	/**
	 * Converts an IRI to a prefixed form, using the prefixmanager.
	 * @param iri
	 * @return e.g. "legacy:hasAnswerObject", "mdc:County"
	 */
	public static String prefixedIRI(IRI iri) {
		String prefixIRI = prefixManager().getPrefixIRI(iri);
		if (prefixIRI == null) throw new IllegalStateException("IRI not prefixable: " + iri);
		return prefixIRI;
	}
	
	public static Json prefix(Json json, Set<OWLProperty<?,?>> properties)
	{
		// All prefixes that are equivalent to the default prefix are ignored because
		// obviously they are not needed since the resulting IRI will be the same. This
		// is not some sort of optimization. Those properties are referred to in Java
		// code and that code is refers to property names without the prefixes so we have
		// to make sure we don't put them.
		String defaultPrefixValue = "";
		for(Map.Entry<String,String> prefixes : prefixManager().getPrefixName2PrefixMap().entrySet())
			if (prefixes.getKey().equals(":")) { defaultPrefixValue = prefixes.getValue(); break; }
		
		if(json.isObject())
		{
			Iterator<Map.Entry<String, Json>> iterator = json.asJsonMap().entrySet().iterator();
			Map<String,String> rename = new HashMap<String,String>();
			while(iterator.hasNext())
			{
				Map.Entry<String, Json> entry = iterator.next();
				for(Map.Entry<String,String> prefixes : prefixManager().getPrefixName2PrefixMap().entrySet())
				{
					if(!prefixes.getKey().equals(":") && 
					   !prefixes.getValue().equals(defaultPrefixValue)
							&& (properties.contains(dataProperty(prefixes.getValue() + entry.getKey()))
							|| properties.contains(objectProperty(prefixes.getValue() + entry.getKey()))))
					{
						rename.put(entry.getKey(),prefixes.getKey()+ entry.getKey());
						break;
					}
				}
				if(entry.getValue().isArray() || entry.getValue().isObject())
				{
					prefix(entry.getValue(), properties);
				}
				
			}
			for(Map.Entry<String,String> r: rename.entrySet())
			{
				json.set(r.getValue(),json.atDel(r.getKey()));
			}
		
		}
		else if(json.isArray())
			for(Json j : json.asJsonList())
			{
				prefix(j, properties);
			}
	
		return json;
	}
	
	public static OWLClassExpression parseDL(String expression, OWLOntology ontology)
	{
		DLQueryParser parser = DLQueryParser.getParser(ontology, 
				(DefaultPrefixManager)prefixManager()); 	
		try
		{
			return parser.parseClassExpression(expression);
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
		finally
		{
			if (isBusinessObjectOntology(ontology)) {
				System.out.println("Disposing DL parser for BO " + ontology);
				DLQueryParser.disposeCachedParser(ontology);
			}
		}		
	}
	
	/**
	 * Return the set of sub and equivalent classes.
	 * @param expression
	 * @param ontology
	 * @return
	 */
	public static Set<OWLClass> querySubsumedClasses(String expression, OWLOntology ontology)
	{
		OWLReasoner reasoner = reasoner(ontology);
		DLQueryParser parser = DLQueryParser.getParser(ontology, 
				(DefaultPrefixManager)prefixManager()); 	
		try
		{
			synchronized (reasoner) {
				// sync to prevent onto changes to occur during parsing and getSubclasses
				OWLClassExpression clexpr = parser.parseClassExpression(expression);
				HashSet<OWLClass> S = new HashSet<OWLClass>();
				S.addAll(reasoner.getEquivalentClasses(clexpr).getEntities());
				S.addAll(reasoner.getSubClasses(clexpr, false).getFlattened());
				return S;
			}
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
		finally
		{
			if (isBusinessObjectOntology(ontology)) {
				System.out.println("Disposing DL parser for BO " + ontology);
				DLQueryParser.disposeCachedParser(ontology);
			}
		}		
	}
	
	public static Set<OWLNamedIndividual> queryIndividuals(String expression)
	{
		return queryIndividuals(expression, ontology());		
	}
	
	public static Set<OWLNamedIndividual> queryIndividuals(String expression, String fragmentRegexp, OWLOntology ontology) 
	{
		try {
			Pattern.compile(fragmentRegexp);
		} catch (Exception e ) { throw new RuntimeException(e); };
		Set<OWLNamedIndividual> s = queryIndividuals(expression, ontology);
		return filterIndividuals(fragmentRegexp, s);
	}
	
	public static Set<OWLNamedIndividual> filterIndividuals(String fragmentRegexp, Set<OWLNamedIndividual> s)
	{
		Set<OWLNamedIndividual> result = new HashSet<OWLNamedIndividual>((s.size() + 11) / 2);
		Pattern p;
		try {
			p = Pattern.compile(fragmentRegexp);
		} catch (Exception e ) { throw new RuntimeException(e); };
		for (OWLNamedIndividual i : s) 
		{
			if (p.matcher(i.getIRI().getFragment()).matches())
				result.add(i);
		}
		return result;
	}

	public static Set<OWLNamedIndividual> queryIndividuals(String expression, OWLOntology ontology)
	{
		OWLReasoner reasoner = reasoner(ontology);
		DLQueryParser parser = DLQueryParser.getParser(ontology, 
				(DefaultPrefixManager)prefixManager()); 	
		try
		{
			OWLClassExpression clexpr = parser.parseClassExpression(expression);
			return reasoner.getInstances(clexpr, false).getFlattened();
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
		finally
		{
			if (isBusinessObjectOntology(ontology)) {
				System.out.println("Disposing DL parser for BO " + ontology);
				DLQueryParser.disposeCachedParser(ontology);
			}
		}		
	}
	
	public static <T> Set<T> set(T...args)
	{
		HashSet<T> S = new HashSet<T>();
		for (T x : args) S.add(x);
		return S;
	}
	
	public static Json toJSON(OWLObject object)
	{
		OWLObjectToJson o2j = new OWLObjectToJson(DEFAULT_STOP_EXPANSION_CONDITION);
		return o2j.map(ontology(), object, null);
	}

	public static Json toJSON(OWLObject object, ShortFormProvider sp)
	{
		OWLObjectToJson o2j = new OWLObjectToJson(DEFAULT_STOP_EXPANSION_CONDITION);
		return o2j.map(ontology(), object, sp);
	}

	/**
	 * @param object
	 * @param expandProtectedIndividuals if false, protected individuals will be included but not expanded. Use for Group/AccessPolicy.
	 * @return
	 */
	public static Json toJSON(OWLObject object, OWLObjectPropertyCondition stopExpansionCondition)
	{
		OWLObjectToJson o2j = new OWLObjectToJson(stopExpansionCondition);
		return o2j.map(ontology(), object, null);
	}

	public static Json toJSON(OWLOntology ontology, OWLObject object, ShortFormProvider sp)
	{
		OWLObjectToJson o2j = new OWLObjectToJson(DEFAULT_STOP_EXPANSION_CONDITION);
		return o2j.map(ontology, object, sp);
	}	


	public static Json toJSON(OWLOntology ontology, OWLObject object)
	{
		OWLObjectToJson o2j = new OWLObjectToJson(DEFAULT_STOP_EXPANSION_CONDITION);
		return o2j.map(ontology, object, null);
	}	

	public static Json toJSON(OWLOntology ontology, OWLObject object, OWLObjectPropertyCondition stopExpansionCondition)
	{
		OWLObjectToJson o2j = new OWLObjectToJson(stopExpansionCondition);
		return o2j.map(ontology, object, null);
	}	

	public static OWLClass getPropertyType(Set<OWLOntology> ontologies, OWLObjectProperty prop)
	{
		for(OWLOntology ontology : ontologies) {
			for (OWLObjectPropertyRangeAxiom ax : ontology.getObjectPropertyRangeAxioms(prop))
			{
				OWLClassExpression range = ax.getRange();
				if (range instanceof OWLClass)
					return (OWLClass)range;
			}
		}
		// String is the default data type for any data property. 
		return OWL.dataFactory().getOWLThing();		
	}
	
	public static OWLClass getPropertyType(OWLOntology ontology, OWLObjectProperty prop)
	{
		for (OWLObjectPropertyRangeAxiom ax : ontology.getObjectPropertyRangeAxioms(prop))
		{
			OWLClassExpression range = ax.getRange();
			if (range instanceof OWLClass)
				return (OWLClass)range;
		}
		// String is the default data type for any data property. 
		return OWL.dataFactory().getOWLThing();		
	}

	public static OWLDatatype getPropertyType(Set<OWLOntology> ontologies, OWLDataProperty prop)
	{
		for(OWLOntology ontology : ontologies) {
			for (OWLDataPropertyRangeAxiom ax : ontology.getDataPropertyRangeAxioms(prop))
			{
				OWLDataRange range = ax.getRange();
				switch (range.getDataRangeType())
				{
					case DATATYPE:
						return (OWLDatatype)range;
					case DATATYPE_RESTRICTION:
						return ((OWLDatatypeRestriction)range).getDatatype();
				}
			}
		}
		// String is the default data type for any data property. 
		return OWL.dataFactory().getOWLDatatype(OWL2Datatype.XSD_STRING.getIRI());
	}
	
	public static OWLDatatype getPropertyType(OWLOntology ontology, OWLDataProperty prop)
	{
		for (OWLDataPropertyRangeAxiom ax : ontology.getDataPropertyRangeAxioms(prop))
		{
			OWLDataRange range = ax.getRange();
			switch (range.getDataRangeType())
			{
				case DATATYPE:
					return (OWLDatatype)range;
				case DATATYPE_RESTRICTION:
					return ((OWLDatatypeRestriction)range).getDatatype();
			}
		}
		// String is the default data type for any data property. 
		return OWL.dataFactory().getOWLDatatype(OWL2Datatype.XSD_STRING.getIRI());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static boolean isPropertyInDomain(OWLOntology ontology, OWLProperty prop, OWLClass domain)
	{
	    String id = domain.toStringID();
	    OWLReasoner reasoner = OWL.reasoner(ontology);
	    Set<?> S = (prop instanceof OWLDataProperty) ?
	    		  ontology.getDataPropertyDomainAxioms((OWLDataProperty)prop) :
	    	      ontology.getObjectPropertyDomainAxioms((OWLObjectProperty)prop);
	    for (OWLPropertyDomainAxiom ax : (Set<OWLPropertyDomainAxiom>)S)
	    {
	    	if (ax.getDomain().equals(domain) ||
		    	reasoner.getSubClasses(ax.getDomain(), false).getFlattened().contains(domain))
		    	return true;
	    }
	    return false;
	}

	public static Set<OWLProperty<?,?>> getClassProperties(Set<OWLOntology> ontologies, OWLClass cl)
	{
		Set<OWLProperty<?,?>> result = new HashSet<OWLProperty<?,?>>();
	    for(OWLOntology ontology : ontologies)
	    {
		    for (OWLObjectProperty prop : ontology.getObjectPropertiesInSignature())
		        if (isPropertyInDomain(ontology, prop, cl)) 
		            result.add(prop);
		    for (OWLDataProperty prop : ontology.getDataPropertiesInSignature())
		        if (isPropertyInDomain(ontology, prop, cl)) 
		            result.add(prop);
	    }
	    return result;
	}

	public static Set<OWLProperty<?,?>> getClassProperties(OWLOntology ontology, OWLClass cl)
	{
	    Set<OWLProperty<?,?>> result = new HashSet<OWLProperty<?,?>>();
	    for (OWLObjectProperty prop : ontology.getObjectPropertiesInSignature())
	        if (isPropertyInDomain(ontology, prop, cl)) 
	            result.add(prop);
	    for (OWLDataProperty prop : ontology.getDataPropertiesInSignature())
	        if (isPropertyInDomain(ontology, prop, cl)) 
	            result.add(prop);
	    return result;
	}	
	
	/**
	 * Checks if owlobjects are HGDB, iff the datafactory is a HGDB factory. 
	 * @param e
	 */
	private static void checkImplementation(OWLObject... e) 
	{
		if (factory instanceof HGDBOntologyFactory)
		{
			for (OWLObject owlObject : e) 
			{
				if (!(owlObject instanceof OWLObjectHGDB))
				{
					System.err.println("Ignoring that " + e + " is not a OWLObjectHGDB. Check trace to change implementation and use the BO factory: ");
					new Exception().printStackTrace(System.err);
				}
			}
		}
	}
	/**
	 * Utility method to coerce a json prop into an individual.
	 * OWLIndividuals are represented as a String containing its iri
	 * when the individual is contained more than once in the ontology in
	 * order to avoid infinite recursion when representing the ontology as json. 
	 * @param j - the json containing the property to be checked.
	 * @param propName - the name of the property
	 * @return the individual represented in the value of the property.
	 */
		public static OWLNamedIndividual individual(Json j, String propName) 
	{
		OWLNamedIndividual result = null;
		if(j.isNull())
			return result;
		else if(j.has(propName) && j.at(propName).isString())
			result = OWL.individual(j.at(propName).asString());
		else if(j.has(propName) && j.at(propName).isObject())
			result = OWL.individual(j.at(propName).at("iri").asString());
		return result;
	}
		
	// hasLegacyInterface(?t, MD-PWS) -> hasDataConstraint(?t, hasDetailsMax500)
//	private static SWRLRule addrule()
//	{
//		Set<SWRLAtom> body = new HashSet<SWRLAtom>(), head = new HashSet<SWRLAtom>();
//		body.add(dataFactory().getSWRLObjectPropertyAtom(OWL.objectProperty("legacy:hasLegacyInterface"), 
//				dataFactory().getSWRLVariable(fullIri("legacy:t")), dataFactory().getSWRLIndividualArgument(OWL.individual("legacy:MD-PWS"))));
//		head.add(dataFactory().getSWRLObjectPropertyAtom(OWL.objectProperty("hasDataConstraint"), 
//				dataFactory().getSWRLVariable(fullIri("legacy:t")), dataFactory().getSWRLIndividualArgument(OWL.individual("legacy:hasDetailsMax500"))));
//		return dataFactory().getSWRLRule(body, head);
//	}
	
	public static void main(String [] args)
	{
	//	if( (args.length > 0) )
//			StartUp.config = Json.read(GenUtils.readTextFile(new File("c:/work/cirmservices/conf/devconfig.json")));
//		System.out.println("Using config " + StartUp.config.toString());
//		System.out.println(OWL.queryIndividuals("hasLegacyInterface value MDC-CMS"));
		OWL.init();
		OWLOntology O = ontology();
		Set<OWLNamedIndividual> pwtypes = queryIndividuals("legacy:hasLegacyInterface value legacy:MD-PWS", O);
		for (OWLNamedIndividual ind  : pwtypes)
		{
			System.out.println("ADd to " + ind);
			manager().applyChange(new AddAxiom(O, dataFactory().getOWLObjectPropertyAssertionAxiom(objectProperty("hasDataConstraint"), 
					ind, individual("legacy:hasDetailsMax500"))));
		}
		if (pwtypes.isEmpty())
			System.out.println("No types found!");
		else
		{			
			VDHGDBOntologyRepository.getInstance().getVersionControlledOntology(O).commit("boris", "Apply hasDataConstraint on hasDetails rule for all PW cases.");
			OntoAdmin oadmin = new OntoAdmin();
			oadmin.push(O.getOntologyID().getOntologyIRI().toString());
		}
		OWLSubObjectPropertyOfAxiom axiom = null;
//		Set<OWLClassExpression> S = new HashSet<OWLClassExpression>();
//		for (OWLClassExpression ind:expr.getOperands())
//			S.add(R(ind));
		
	}
}

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
import static org.sharegov.cirm.OWL.has;
import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.objectProperty;
import static org.sharegov.cirm.OWL.oneOf;
import static org.sharegov.cirm.OWL.or;
import static org.sharegov.cirm.OWL.owlClass;
import static org.sharegov.cirm.OWL.reasoner;
import static org.sharegov.cirm.OWL.some;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.mindswap.pellet.KnowledgeBase;
import org.mindswap.pellet.jena.PelletInfGraph;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLProperty;
import org.semanticweb.owlapi.reasoner.BufferingMode;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.owl.Wrapper;

import com.clarkparsia.pellet.owlapiv3.PelletReasoner;
import com.clarkparsia.pellet.sparqldl.jena.SparqlDLExecutionFactory;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.InfModel;
import com.hp.hpl.jena.rdf.model.ModelFactory;

/**
 * @author SABBAS
 *
 */
public class RelationalOWLMapper
{
	public static boolean DBG = false;

	private static Logger logger = Logger.getLogger("org.sharegov.cirm.rdb");
	private static volatile RelationalOWLMapper instance = null;
	
	private static InfModel jenaInfModel;

	public static boolean USE_JENA = false;
	
	public static final boolean COMPARE_OWL_JENA = false;
	
	private String SPARQL_HAS_COLUMN_MAPPING_DATA_PROPERTY;
	private String SPARQL_HAS_COLUMN_MAPPING_OBJECT_PROPERTY;
	private String SPARQL_HAS_TABLE_MAPPING;
	private String SPARQL_PRIMARY_KEYS_PER_TABLE_NO_IRI;
	private String SPARQL_PRIMARY_KEYS_PER_TABLE_IRI;
	private String SPARQL_COLUMNS_BY_TABLE;
	
	/**
	 * maps Owl Datatypes to one of Concepts VARCHAR, INTEGER, DOUBLE, CLOB or DATE
	 */
	private volatile Map<OWL2Datatype, OWLNamedIndividual> datatypeTypeMapping; 
	
	/**
	 * mapped OWLClass to mapped table.
	 */
	private Map<OWLClass, OWLNamedIndividual> tableMapping;

	private Map<OWLNamedIndividual, Set<OWLClass>> classMapping;

	/**
	 * Table to (Data Or Object)Property->Column.
	 */
	private Map<OWLNamedIndividual, Map<OWLProperty<?, ?>, OWLNamedIndividual>> columnMapping; 
	
	/**
	 * Table to columnIRIPKs.
	 * DBPrimaryKey and IRIKey and hasTable some Thing
	 * This matches as of May 11 2012: 
	 * CIRM_SR_ACTOR.SR_ACTOR_ID, CIRM_SERVICE_CALL.SERVICE_CALL_ID, CIRM_SR_ACTIVITY.ACTIVITY_ID, CIRM_MDC_ADDRESS.ADDRESS_ID,
	 * CIRM_SR_REQUESTS.SR_REQUEST_ID
	 */
	private Map<OWLNamedIndividual,Set<OWLNamedIndividual>> columnIRIPK; 

	/**
	 * ObjectProperty->foreignKeyTable to foreignKeyColumn
	 * OWLProperty and hasOne some OWLClass and hasColumnMapping some DBForeignKey
	 * This matches as of May 11 2012: atAddress (Range Street_Address, Domain Service_Activity)
	 */
	private Map<Map<OWLObjectProperty,OWLNamedIndividual>,OWLNamedIndividual> hasOne;

	/**
	 * table t to set of property->t.column 
	 */
	private Map<OWLNamedIndividual, Set<Map<OWLObjectProperty,OWLNamedIndividual>>> hasOneByTable;

	/**
	 * property p to two tables participating in hasOne RelationShip. 
	 */
	private Map<OWLObjectProperty, Set<OWLNamedIndividual>> hasOne2TablesByProperty;
	
	/**
	 * Property To 
	 * Base query:
	 * OWLProperty and hasMany some Thing
	 * Matches: 
	 * hasParticipant, hasMember; legacy: hasServiceCaseActor, hasServiceActivity
	 */
	private Map<OWLObjectProperty, OWLClass> hasMany;
	private Map<OWLClass, OWLObjectProperty> hasManyByClass;
	private Map<OWLClass,Map<OWLObjectProperty,OWLClass>> hasManyPropertyAndDomainByRangeClass;

	/**
	 * Contains either: 
	 * Map from OneTable>ManyTable to JoinTable (1: may => manyTable == JoinTable)
	 * Map from OneTable->ManyTable to JoinTable and ManyTable>OneTable to JoinTable (*:* => manyTable!=JoinTable)
	 * Base query for TABLE: isJoinedWithTable some DBTable
	 * This matches as of May 11 2012: CIRM_SR_ACTIVITY, CIRM_SERVICE_ACTION, CIRM_SR_REQUESTS, CIRM_SRREQ_SRACTOR, CIRM_SR_ACTOR
	 * 
	 * joinsByTable:
	 * OneTable -> (ManyTable -> JoinTable) OR OneTable -> (OtherOneTable -> JoinTable)
	 */
	private Map<Map<OWLNamedIndividual,OWLNamedIndividual>,OWLNamedIndividual> joins;
	private Map<OWLNamedIndividual,Set<Map<OWLNamedIndividual,OWLNamedIndividual>>> joinsByTable;
	
	/**
	 * JoinColumn>JoinTable to DBForeignKey
	 * Base query: DBForeignKey and hasTable some DBTable and hasJoinColumn some DBColumn
	 * This matches as of May 11 2012: CIRM_SRREQ_SRACTOR.SR_REQUEST_ID, CIRM_SRREQ_SRACTOR.SR_ACTOR_ID, 
	 * CIRM_SR_ACTIVITY.SR_REQUEST_ID, CIRM_SERVICE_ACTION.SERVICE_CALL_ID, CIRM_SR_ACTOR.SR_ACTOR_ADDRESS, 
	 * CIRM_SR_REQUESTS.SR_REQUEST_ADDRESS.
	 * 
	 * Example: (REQUEST.SR_REQUEST_ID, SR_ACTIVITY)->SR_ACTIVITY.SR_REQUEST_ID
	 * JoinColumn == PK.
	 */
	private Map<Map<OWLNamedIndividual,OWLNamedIndividual>,OWLNamedIndividual>  foreignKeyByJoinColumnAndJoinTable;
	
	/**
	 * Table To PKColumns.  
	 * DBPrimaryKey and not IRIKey and hasTable Table.
	 * This matches as of May 11 2012: CIRM_SERVICE_ACTION.SERVICE_CALL_ID, CIRM_SERVICE_ACTION.AT_TIME
	 */
	private Map<OWLNamedIndividual,Set<OWLNamedIndividual>> columnPK;
	
	/**
	 * Table To PKColumns.  
	 * DBPrimaryKey and not IRIKey and hasTable Table.
	 * This matches as of May 11 2012: CIRM_SERVICE_ACTION.SERVICE_CALL_ID, CIRM_SERVICE_ACTION.AT_TIME
	 */
	private Map<OWLNamedIndividual,Set<OWLNamedIndividual>> tableColumns;
	
	private RelationalOWLMapper() 
	{
		SPARQL_HAS_COLUMN_MAPPING_DATA_PROPERTY = StartUp.config.at("workingDir").asString() + "/src/resources/" + "rdb/hasColumnMappingDP.sparql";
		SPARQL_HAS_COLUMN_MAPPING_OBJECT_PROPERTY = StartUp.config.at("workingDir").asString() + "/src/resources/" + "rdb/hasColumnMappingOP.sparql";
		SPARQL_HAS_TABLE_MAPPING = StartUp.config.at("workingDir").asString() + "/src/resources/" + "rdb/hasTableMapping.sparql";
		SPARQL_PRIMARY_KEYS_PER_TABLE_NO_IRI = StartUp.config.at("workingDir").asString() + "/src/resources/" + "rdb/primaryKeysNoIRI.sparql";
		SPARQL_PRIMARY_KEYS_PER_TABLE_IRI = StartUp.config.at("workingDir").asString() + "/src/resources/" + "rdb/primaryKeysIRI.sparql";
		SPARQL_COLUMNS_BY_TABLE = StartUp.config.at("workingDir").asString() + "/src/resources/" + "rdb/tableColumns.sparql";
	}
	
	public static synchronized RelationalOWLMapper getInstance()
	{
		init();
		return instance;
	}
	
	private static synchronized void init() 
	{
		if(instance == null)
		{
			RelationalOWLMapper init = new RelationalOWLMapper();
			System.out.print("Reasoner: " + reasoner().getReasonerName() + " " 
					+ reasoner().getReasonerVersion().getMajor() + 
					"." + reasoner().getReasonerVersion().getMinor() 
					+ " build: " + reasoner().getReasonerVersion().getBuild()
					+ " patch: " + reasoner().getReasonerVersion().getPatch());
			if (reasoner().getBufferingMode() == BufferingMode.BUFFERING) 
			{
				System.out.println(" is buffering.");
			} 
			else 
			{
				System.out.println(" is NOT buffering.");
			}
			instance = init;
		}
		if (instance.isClearedCache()) 
		{
			instance.refreshCache();
		}
	}
	
	/**
	 * Clears all meta ontology dependent cached data.
	 * Instantly releases all cache collections for garbage collection.
	 */
	public synchronized void clearCache() 
	{
		datatypeTypeMapping = null; 
		tableMapping = null;
		classMapping = null;
		columnMapping = null; 
		columnIRIPK = null; 
		hasOne = null; 
		hasOneByTable = null; 
		hasOne2TablesByProperty = null; 
		hasMany = null;
		hasManyByClass = null; 
		joins = null;
		joinsByTable = null; 
		foreignKeyByJoinColumnAndJoinTable = null; 
		columnPK = null; 
		tableColumns = null;
		// clear the jena inf model
		jenaInfModel = null;
	}
	
	protected boolean isClearedCache() 
	{
		return datatypeTypeMapping == null;
	}
	
	/**
	 * Refreshes the meta ontology dependent cache.
	 * This is an expensive operation.
	 * A clear will be called if the cache has not been cleared.
	 */
	protected synchronized void refreshCache() 
	{
		if (!isClearedCache()) clearCache();
		synchronized(reasoner())
		{
			if (COMPARE_OWL_JENA) 
			{
				cacheRelationalMappings();
				initJenaModel();
				compareToJena();
			} 
			else 
			{
				if (USE_JENA) 
				{
					initJenaModel();
					cacheRelationalMappingsJena();
				} 
				else 
				{
					cacheRelationalMappings();
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void initJenaModel() 
	{
		OWLReasoner reasoner = reasoner();
		if(reasoner instanceof Wrapper<?>) {
			synchronized(reasoner) {
				KnowledgeBase kb = ((PelletReasoner) ((Wrapper<OWLReasoner>)reasoner).unwrapAll()).getKB();
				PelletInfGraph graph = new org.mindswap.pellet.jena.PelletReasoner().bind( kb );
				jenaInfModel = ModelFactory.createInfModel( graph );
			}
		} else {
			KnowledgeBase kb = ((PelletReasoner) reasoner).getKB();
			PelletInfGraph graph = new org.mindswap.pellet.jena.PelletReasoner().bind( kb );
			jenaInfModel = ModelFactory.createInfModel( graph );
		}
	}
	
	/**
	 * Determines if any of the given types is mapped.
	 * 
	 * @param types
	 * @return
	 */
	public static boolean isMapped(Set<? extends OWLClassExpression> types)
	{
		boolean mapped = false;
		if(types.size() == 1)
		{
			OWLClassExpression cle = types.iterator().next();
			if(! (cle instanceof OWLClass))
				mapped = false;
			else
				mapped = isMapped(cle.asOWLClass());
		}
		else
		{
			for(OWLClassExpression type: types)
			{
				if(! (type instanceof OWLClass))
					continue;
				mapped = isMapped(type.asOWLClass());
				if(mapped)
					break;
			}
		}
		return mapped;
	}

	/**
	 * Finds a sql typeMapping to a datatype. 
	 * This will return null, if there is no mapping for the datatype in the meta ontology.
	 * @param datatype
	 * @return one of VARCHAR, CLOB, DATE, DOUBLE, INTEGER as they are defined in Concepts class.
	 */
	public static OWLNamedIndividual hasTypeMapping(OWL2Datatype datatype)
	{
		return getInstance().datatypeTypeMapping.get(datatype);
	}
	
	public static boolean isMapped(OWLClass c)
	{
		return getInstance().tableMapping.get(c) != null;
	}
	
	/**
	 * Determines a table mapping for the first of the given types, for whom a table mapping exists.
	 * 
	 * @param types
	 * @return null, or an individual representing the table.
	 */
	public static OWLNamedIndividual table(Set<? extends OWLClassExpression> types)
	{
		OWLNamedIndividual table = null;
//		if(types.size() == 1)
//		{
//			OWLClassExpression cle = types.iterator().next();
//			if(! (cle instanceof OWLClass))
//				return table;
//			else
//				table = table(cle.asOWLClass());
//		}
//		else
//		{
			for(OWLClassExpression type: types)
			{
				if(! (type instanceof OWLClass))
					continue;
				table = table(type.asOWLClass());
				if(table != null)
					break;
			}
//		}
		return table;
	}
	
	/**
	 * Determines the mapping table for the given class.
	 *   
	 * @param c
	 * @return null or the mapping table.
	 */
	public static OWLNamedIndividual table(OWLClass c)
	{
		return getInstance().tableMapping.get(c);
	}

	/**
	 * Determines all mapped classes (usually one) for one mapping table.
	 *   
	 * @param table a mapping table
	 * @return null or a non empty set of classes.
	 */
	public static Set<OWLClass> classesByTable(OWLNamedIndividual table)
	{
		return getInstance().classMapping.get(table);
	}

	public static Map<OWLProperty<?, ?>, OWLNamedIndividual> columnMapping(OWLClass c)
	{
		return getInstance().columnMapping.get(table(c));
	}
	
	/**
	 * Returns all property -> column entries for the table.
	 * @param table
	 * @return
	 */
	public static Map<OWLProperty<?, ?>, OWLNamedIndividual> columnMapping(OWLNamedIndividual table)
	{
		return getInstance().columnMapping.get(table);
	}
	
	/**
	 * Returns the cached IRIkey column that is a DBPrimaryKey.
	 * 
	 * DBPrimaryKey and IRIKey
	 * @param table
	 * @return the IRI
	 */
	public static Set<OWLNamedIndividual> columnIriPK(OWLNamedIndividual table)
	{
		return getInstance().columnIRIPK.get(table);
	}
	
	/**
	 * Returns the cached foreignKeyColumn for a given property and foreignTable.
	 * Cache contains properties matching: <pre>
	 * OWLProperty and hasOne some OWLClass and hasColumnMapping some DBForeignKey
	 * </pre>
	 * 
	 * @param property
	 * @param foreignTable
	 * @return foreignKeyColumn
	 */
	public static OWLNamedIndividual hasOne(OWLObjectProperty property, OWLNamedIndividual foreignTable)
	{
		return getInstance().hasOne.get(Collections.singletonMap(property, foreignTable));
	}

	/**
	 * Finds a set of hasOne property->t.column to a table t.
	 * 
	 * hilpold
	 * @param property
	 * @param table a table for the own property and foreignKeyColumn 
	 * @return a singleton map from property to the column in the given table or null. 
	 */
	public static Set<Map<OWLObjectProperty, OWLNamedIndividual>> hasOneByTable(OWLNamedIndividual table)
	{
		return getInstance().hasOneByTable.get(table);
	}

	/**
	 * Finds a set of two tables to a hasOne property participating in the relationship. 
	 * 
	 * hilpold
	 * @param property
	 * @param table a table for the own property and foreignKeyColumn 
	 * @return a singleton map from property to the column in the given table or null. 
	 */
	public static Set<OWLNamedIndividual> hasOne2TablesByProperty(OWLObjectProperty property)
	{
		//2012.05.24 hilpold
		//return hasOne2TablesByProperty(property);
		return getInstance().hasOne2TablesByProperty.get(property);
	}

	/**
	 * Returns all cached Concepts.DBPrimaryKey (but not Concepts.IRIKey) for a given table.
	 * No PrimaryKeys that are IRIKeys will be returned.
	 * @param table
	 * @return
	 */
	public static Set<OWLNamedIndividual> columnPK(OWLNamedIndividual table)
	{
		return getInstance().columnPK.get(table);
	}
	
	/**
	 * 
	 * @param properties
	 * @return
	 */
	public static Map<OWLObjectProperty, OWLClass> hasMany(Set<OWLObjectPropertyExpression> properties)
	{
		Map<OWLObjectProperty, OWLClass> result = new HashMap<OWLObjectProperty, OWLClass>();
		for(OWLObjectPropertyExpression property: properties)
		{
			if(! (property instanceof OWLObjectProperty))
				continue;
			OWLClass val = getInstance().hasMany.get(property.asOWLObjectProperty()); 
			if(val != null)
				result.put(property.asOWLObjectProperty(), val);
		}
		return result;
	}
	/**
	 * 
	 * @param properties
	 * @return
	 */
	public static OWLClass hasMany(OWLObjectProperty property)
	{
		return getInstance().hasMany.get(property);
	}
	
	/**
	 * e.g. returns hasServiceActivity for ServiceActivity
	 * @param clazz
	 * @return
	 */
	public static OWLObjectProperty hasManyByClass(OWLClass clazz)
	{
		return getInstance().hasManyByClass.get(clazz);
	}
	
	/**
	 * 
	 * e.g. returns hasServiceActivity for ServiceActivity
	 * @param clazz
	 * @return
	 */
	public static Map<OWLObjectProperty,OWLClass> hasManyByRange(OWLClass rangeClass)
	{
		return getInstance().hasManyPropertyAndDomainByRangeClass.get(rangeClass);
	}

	/**
	 * Returns the join table for a given hasMany relationShip between given tableA and tableB.
	 * If joinTable equals tableB it is a 1 (tableA) :*(tableB) relationship; else many to many. 
	 * 
	 * @param tableA
	 * @param tableB
	 * @return
	 */
	public static OWLNamedIndividual join(OWLNamedIndividual tableA, OWLNamedIndividual tableB)
	{
		return getInstance().joins.get(Collections.singletonMap(tableA, tableB));
	}

	/**
	 * 
	 * 
	 * e.g. for joinByTable(SR_Request) == (SR_Activity, SR_Activity)
	 * @param tableA
	 * @return
	 */
	public static Set<Map<OWLNamedIndividual, OWLNamedIndividual>> joinsByTable(OWLNamedIndividual tableA)
	{
		return getInstance().joinsByTable.get(tableA);
	}

	/**
	 * Return s a cached DBForeignkey instance for a given joinColumn and joinTable.
	 * DBForeignKey and hasTable some DBTable and hasJoinColumn some DBColumn
	 * @param joinColumn is PK on other side of Relationship than joinTable
	 * @param joinTable
	 * @return
	 */
	public static OWLNamedIndividual foreignKeyByjoinColumnAndTable(OWLNamedIndividual joinColumn, OWLNamedIndividual joinTable)
	{
		return getInstance().foreignKeyByJoinColumnAndJoinTable.get(Collections.singletonMap(joinColumn, joinTable));
	}
	
	/**
	 * Determines for a mapped objectProperty, if the value shall be mapped as a fragment or a full IRI. 
	 * @param prop
	 * @return
	 */
	public static boolean isStoreFragment(OWLObjectProperty prop) {
		OWLNamedIndividual propertyAsIndividual = individual(prop.getIRI());
		OWLLiteral storeFragmentLiteral = OWL.dataProperty(propertyAsIndividual, Concepts.storeFragment);
		return storeFragmentLiteral != null && storeFragmentLiteral.parseBoolean();
	}
	
	/**
	 * Returns all column individuals for a given table
	 * @param prop
	 * @return
	 */
	public static Set<OWLNamedIndividual> columns(OWLNamedIndividual table) {
		return getInstance().tableColumns.get(table);
	}
	
	/**
	 * Caches all RDB mapping described in ontology()
	 * into member variables of this class.
	 */
	private void cacheRelationalMappings()
	{
		long x = System.currentTimeMillis();
		logger.info("Caching relational mappings using OWL reasoning");
		cacheDatatypeTypeMappings();
		cacheTableMappings();
		cacheColumnMappings();
		validateColumnMappings();
		cacheColumnIRIPKs();
		cacheHasOne();
		cacheHasMany();
		cacheJoinTables();
		cacheForeignKeyColumns();
		cacheColumnPKs();
		cacheTableColumns();
		logger.info("Relational cache complete in " + ((System.currentTimeMillis() - x)/1000) + " sec.");
	}
	private void compareToJena() {
		Map<OWLClass, OWLNamedIndividual> tableMappingOrig = tableMapping;
		Map<OWLNamedIndividual, Set<OWLClass>> classMappingOrig = classMapping;
		cacheTableMappingsJena();
		System.out.println("TABLEMAPPING JENA EQUAL TO OWL? " + tableMappingOrig.equals(tableMapping));
		System.out.println("CLASSMAPPING JENA EQUAL TO OWL? " + classMappingOrig.equals(classMapping));
		Map<OWLNamedIndividual, Map<OWLProperty<?, ?>, OWLNamedIndividual>> columnMappingOrig = columnMapping;
		cacheColumnMappingsJena();
		System.out.println("COLUMNMAPPING JENA EQUAL TO OWL? " + columnMappingOrig.equals(columnMapping));
		Map<OWLNamedIndividual,Set<OWLNamedIndividual>> columnPKOrig = columnPK;
		cacheColumnPKsJena();
		System.out.println("COLUMNPK JENA EQUAL TO OWL? " + columnPKOrig.equals(columnPK));
		Map<OWLNamedIndividual,Set<OWLNamedIndividual>> columnIRIPKOrig = columnIRIPK;
		cacheColumnIRIPKsJena();
		System.out.println("COLUMN IRI PK JENA EQUAL TO OWL? " + columnIRIPKOrig.equals(columnIRIPK));
	}
	
	private void cacheRelationalMappingsJena() {
		long x = System.currentTimeMillis();
		long total = System.currentTimeMillis();
		logger.info("Caching relational mappings partially using JENA and SPARQL");
		cacheDatatypeTypeMappings();
		logger.info("Non Sparql cacheDatatypeTypeMappings complete in " + ((System.currentTimeMillis() - x)/1000) + " sec.");
		x = System.currentTimeMillis();
		cacheColumnPKsJena();
		logger.info("Sparql cacheColumnPKs complete in " + ((System.currentTimeMillis() - x)/1000) + " sec.");
		x = System.currentTimeMillis();
		cacheTableMappingsJena();
		logger.info("Sparql Table mappings complete in " + ((System.currentTimeMillis() - x)/1000) + " sec.");
		x = System.currentTimeMillis();
		cacheColumnMappingsJena();
		logger.info("Validating Column Mappings.");
		validateColumnMappings();
		logger.info("Sparql Column mappings complete in " + ((System.currentTimeMillis() - x)/1000) + " sec.");
		x = System.currentTimeMillis();
		cacheColumnIRIPKsJena();
		logger.info("Sparql cacheColumnIRIPKsJena complete in " + ((System.currentTimeMillis() - x)/1000) + " sec.");
		x = System.currentTimeMillis();
		cacheHasOne();
		logger.info("Non Sparql cacheHasOne complete in " + ((System.currentTimeMillis() - x)/1000) + " sec.");
		x = System.currentTimeMillis();
		cacheHasMany();
		logger.info("Non Sparql cacheHasMany complete in " + ((System.currentTimeMillis() - x)/1000) + " sec.");
		x = System.currentTimeMillis();
		cacheJoinTables();
		logger.info("Non Sparql cacheJoinTables complete in " + ((System.currentTimeMillis() - x)/1000) + " sec.");
		x = System.currentTimeMillis();
		cacheForeignKeyColumns();
		logger.info("Non Sparql cacheForeignKeyColumns complete in " + ((System.currentTimeMillis() - x)/1000) + " sec.");
		x = System.currentTimeMillis();
		cacheTableColumnsJena();
		logger.info("Sparql Table columns complete in " + ((System.currentTimeMillis() - x)/1000) + " sec.");
		x = System.currentTimeMillis();
		logger.info("Relational cache complete in " + ((System.currentTimeMillis() - total)/1000) + " sec.");
	}

	
	private void cacheDatatypeTypeMappings()
	{
		datatypeTypeMapping = new HashMap<OWL2Datatype, OWLNamedIndividual>(50);
		for (OWL2Datatype curDatatype : OWL2Datatype.values()) {
			OWLNamedIndividual sqlType = objectProperty(individual(curDatatype.getIRI().getFragment()), "hasTypeMapping");
			if (sqlType == null) {
				System.err.println("Ignoring that no typeMapping is defined in Meta for OWL2Datatype: " + curDatatype.getIRI());
			}
			//sqlType will be null for all those not in meta.
			datatypeTypeMapping.put(curDatatype, sqlType);
		}
	}
	
	private void cacheTableMappings()
	{
		tableMapping = new LinkedHashMap<OWLClass, OWLNamedIndividual>();
		classMapping = new LinkedHashMap<OWLNamedIndividual, Set<OWLClass>>();
		Map<OWLClass, OWLNamedIndividual> mapping = new LinkedHashMap<OWLClass, OWLNamedIndividual>();
		OWLClassExpression q = and(owlClass(Refs.OWLClass), some(objectProperty(Concepts.hasTableMapping),owlClass(Concepts.DBTable)));
		Set<OWLNamedIndividual> S = reasoner().getInstances(q, false).getFlattened();
		int x = S.size();
		logger.info(x + " direct table mappings found.");
		for (OWLNamedIndividual i : S)
		{
			OWLClass mappedClass = owlClass(i.getIRI());
			OWLNamedIndividual table = objectProperty(i, Concepts.hasTableMapping);
			mapping.put(mappedClass, table);
			Set<OWLClass> tableClasses = classMapping.get(table);
			if (tableClasses == null) {
				tableClasses = new HashSet<OWLClass>();
				classMapping.put(table, tableClasses);	
			}
			tableClasses.add(mappedClass);					
			for(OWLClass sub : reasoner().getSubClasses(mappedClass, false).getFlattened())
			{
				if(!sub.isOWLNothing() && objectProperty(individual(sub.getIRI()),Concepts.hasTableMapping ) == null)
				{
					mapping.put(sub, table);
//					Set<OWLClass> tableClasses = classMapping.get(table);
//					if (tableClasses == null) {
//						tableClasses = new HashSet<OWLClass>();
//						classMapping.put(table, tableClasses);	
//					}
					tableClasses.add(sub);					
				}
			}
		}
		logger.info(mapping.size() - x + " additional table mappings inferred.");
		tableMapping.putAll(mapping);
	}
	
	private void cacheTableMappingsJena() 
	{
		tableMapping = new LinkedHashMap<OWLClass, OWLNamedIndividual>();
		classMapping = new LinkedHashMap<OWLNamedIndividual, Set<OWLClass>>();		
		Query query;
		query = QueryFactory.read(SPARQL_HAS_TABLE_MAPPING);
		   QueryExecution queryExecution = SparqlDLExecutionFactory.create(query, jenaInfModel);
		   ResultSet rs = queryExecution.execSelect();
			while (rs.hasNext()) {
				QuerySolution solution = rs.next();
				OWLNamedIndividual table = individual(fullIri(solution.get("table").asResource().getURI()));
				OWLClass mappedClass = owlClass(fullIri(solution.get("class").asResource().getURI()));
				Set<OWLClass> tableClasses;
				if(classMapping.containsKey(table))
				{
					tableClasses = classMapping.get(table);
				}
				else 
				{
					tableClasses = new HashSet<OWLClass>();
					classMapping.put(table, tableClasses);
				}
				tableClasses.add(mappedClass);
				logger.info("Mapping " + mappedClass + " to table " + table);
				tableMapping.put(mappedClass, table);
			}
			logger.info(tableMapping.size() + " table mappings.");
	}

	
	private void cacheColumnMappings()
	{
		columnMapping = new LinkedHashMap<OWLNamedIndividual, Map<OWLProperty<?, ?>, OWLNamedIndividual>>();

		OWLClassExpression qData = and(owlClass(Refs.OWLDataProperty), some(objectProperty(Concepts.hasColumnMapping), and(
				or(owlClass(Concepts.DBPrimaryKey), owlClass(Concepts.DBNoKey) , owlClass(Concepts.DBForeignKey)), 
				some(objectProperty(Concepts.hasTable), oneOf(tableMapping.values().toArray(new OWLIndividual[tableMapping.values().size()]))))));

		OWLClassExpression qObject = and(owlClass(Refs.OWLObjectProperty), some(objectProperty(Concepts.hasColumnMapping), and(
				or(owlClass(Concepts.DBPrimaryKey), owlClass(Concepts.DBNoKey)), 
				some(objectProperty(Concepts.hasTable), oneOf(tableMapping.values().toArray(new OWLIndividual[tableMapping.values().size()]))))));
		
		Set<OWLNamedIndividual> S = reasoner().getInstances(qData, false).getFlattened();
		S.addAll(reasoner().getInstances(qObject, false).getFlattened());

		for (OWLNamedIndividual punnedPropertyWithSomeMapping : S)
		{
			for(OWLNamedIndividual column : reasoner().getObjectPropertyValues(punnedPropertyWithSomeMapping, objectProperty(Concepts.hasColumnMapping)).getFlattened())
			{
				OWLNamedIndividual table = reasoner().getObjectPropertyValues(column,objectProperty(Concepts.hasTable)).getFlattened().iterator().next();
				Map<OWLProperty<?, ?>, OWLNamedIndividual> columns;  
				if(columnMapping.containsKey(table))
				{
					columns = columnMapping.get(table);
				}
				else 
				{
					columns = new LinkedHashMap<OWLProperty<?, ?>, OWLNamedIndividual>();
					columnMapping.put(table, columns);
				}
				if(reasoner().getTypes(punnedPropertyWithSomeMapping, false).getFlattened().contains(owlClass(Refs.OWLDataProperty)))
				{
					columns.put(dataProperty(punnedPropertyWithSomeMapping.getIRI()),column);
				}else
				{
					columns.put(objectProperty(punnedPropertyWithSomeMapping.getIRI()),column);

				}
			}
		}
	}
	
	private void cacheColumnMappingsJena() 
	{
		columnMapping = new LinkedHashMap<OWLNamedIndividual, Map<OWLProperty<?, ?>, OWLNamedIndividual>>();
		cacheColumnMappingsJena(true);
		cacheColumnMappingsJena(false);
	}

	private void cacheColumnMappingsJena(boolean dataproperties) 
	{
		   Query query;
		   if (dataproperties) 
		   {
			   query = QueryFactory.read(SPARQL_HAS_COLUMN_MAPPING_DATA_PROPERTY);
		   } 
		   else 
		   {
			   query = QueryFactory.read(SPARQL_HAS_COLUMN_MAPPING_OBJECT_PROPERTY);
		   }
		   QueryExecution queryExecution = SparqlDLExecutionFactory.create(query, jenaInfModel);
		   ResultSet rs = queryExecution.execSelect();
			while (rs.hasNext()) 
			{
				QuerySolution solution = rs.next();
				OWLNamedIndividual table = individual(fullIri(solution.get("table").asResource().getURI()));
				OWLNamedIndividual column = individual(fullIri(solution.get("column").asResource().getURI()));
				Map<OWLProperty<?, ?>, OWLNamedIndividual> columns;  
				if(columnMapping.containsKey(table))
				{
					columns = columnMapping.get(table);
				}
				else 
				{
					columns = new LinkedHashMap<OWLProperty<?, ?>, OWLNamedIndividual>();
					columnMapping.put(table, columns);
				}
				OWLProperty<?,?> property;
				if (dataproperties) {
					property = dataProperty(fullIri(solution.get("property").asResource().getURI()));
				} else {
					property = objectProperty(fullIri(solution.get("property").asResource().getURI()));
				}
				columns.put(property, column);
			}
			if(DBG)
			{
				for(Map.Entry<OWLNamedIndividual,Map<OWLProperty<?,?>, OWLNamedIndividual>> entry:  columnMapping.entrySet())
				{
					OWLNamedIndividual key =  entry.getKey();
					System.out.println("column key : " + key);
					for(Map.Entry<OWLProperty<?,?>, OWLNamedIndividual> value : entry.getValue().entrySet())
					{
						System.out.println("column map entry property : " + value.getKey() + " = " + value.getValue());
					}
				}
			}
	}

	private void validateColumnMappings() 
	{
		int errorsFound = 0;
		for (Map.Entry<OWLNamedIndividual, Map<OWLProperty<?, ?>, OWLNamedIndividual>> e : columnMapping.entrySet()) 
		{
			Map<OWLProperty<?, ?>, OWLNamedIndividual> colMappings = e.getValue();
			for (Map.Entry<OWLProperty<?, ?>, OWLNamedIndividual> mapping : colMappings.entrySet()) 
			{
				int occurences = Collections.frequency(colMappings.values(), mapping.getValue());
				if (occurences > 1) 
				{
					errorsFound ++; 
					logger.warning(" RDB Mapping - Validation Error "+ errorsFound + " : " 
							+ occurences + " mappings to column : " + mapping.getValue() + " found. "
							+ " Property: " + mapping.getKey() + " Table was: " + e.getKey());
				}
			}
		}
		if (errorsFound > 0) logger.severe("Errors found during Validation of Column Mappings. See Log, Fix Ontology.");
	}

	private void cacheColumnIRIPKs()
	{		
		columnIRIPK = new LinkedHashMap<OWLNamedIndividual,Set<OWLNamedIndividual>>();
		Set<OWLNamedIndividual> uniqueTables =  new HashSet<OWLNamedIndividual>();
		uniqueTables.addAll(tableMapping.values());
		for(OWLNamedIndividual table : uniqueTables)
		{
			Set<OWLNamedIndividual> columns = new HashSet<OWLNamedIndividual>();
			OWLClassExpression q = 
				and(
					owlClass(Concepts.DBPrimaryKey),
					and(owlClass(Concepts.IRIKey)),
					has(objectProperty(Concepts.hasTable), table));
			NodeSet<OWLNamedIndividual> S = reasoner().getInstances(q, false);
			for (OWLNamedIndividual i : S.getFlattened())
				columns.add(i);
			columnIRIPK.put(table, columns);
		}
	}
	
	
	private void cacheColumnIRIPKsJena() {
		columnIRIPK = new LinkedHashMap<OWLNamedIndividual,Set<OWLNamedIndividual>>();
		Query query;
		query = QueryFactory.read(SPARQL_PRIMARY_KEYS_PER_TABLE_IRI);
		QueryExecution queryExecution = SparqlDLExecutionFactory.create(query, jenaInfModel);
		ResultSet rs = queryExecution.execSelect();
		while (rs.hasNext()) 
		{
				QuerySolution solution = rs.next();
				OWLNamedIndividual table = individual(fullIri(solution.get("table").asResource().getURI()));
				OWLNamedIndividual iRIpkColumn = individual(fullIri(solution.get("primaryKeyColumnIRI").asResource().getURI()));
				if (iRIpkColumn.getIRI().toString().length() > 1) 
				{
					Set<OWLNamedIndividual> pkColumnsOneTable;
					if(columnIRIPK.containsKey(table))
					{
						pkColumnsOneTable = columnIRIPK.get(table);
					}
					else 
					{
						pkColumnsOneTable = new HashSet<OWLNamedIndividual>();
						columnIRIPK.put(table, pkColumnsOneTable);
					}
					pkColumnsOneTable.add(iRIpkColumn);
					System.out.println("IRIPK: " + iRIpkColumn + " for table " + table);
				}
		}
		logger.info(columnIRIPK.size() + " tables with IRI PKs.");	
	}
//	
//	private void cacheColumnIRINoPKs()
//	{
//		Set<OWLNamedIndividual> uniqueTables =  new HashSet<OWLNamedIndividual>();
//		uniqueTables.addAll(tableMapping.values());
//		for(OWLNamedIndividual table : uniqueTables)
//		{
//			Set<OWLNamedIndividual> columns = new HashSet<OWLNamedIndividual>();
//			OWLClassExpression q = 
//				and(
//					//or(owlClass(Concepts.DBPrimaryKey), owlClass(Concepts.DBNoKey)),
//					owlClass(Concepts.DBNoKey),
//					and(owlClass(Concepts.IRIKey)),
//					has(objectProperty(Concepts.hasTable), table));
//			NodeSet<OWLNamedIndividual> S = reasoner().getInstances(q, false);
//			for (OWLNamedIndividual i : S.getFlattened())
//				columns.add(i);
//			columnIRI.put(table, columns);
//		}
//	}
//	
	private void cacheHasOne()
	{
		hasOne = new LinkedHashMap<Map<OWLObjectProperty,OWLNamedIndividual>,OWLNamedIndividual> ();
		hasOneByTable = new LinkedHashMap<OWLNamedIndividual, Set<Map<OWLObjectProperty,OWLNamedIndividual>>> ();
		hasOne2TablesByProperty = new LinkedHashMap<OWLObjectProperty, Set<OWLNamedIndividual>>();
		OWLClassExpression q =
		and(
			owlClass(Refs.OWLProperty),
			some(objectProperty(Concepts.hasOne),owlClass(Refs.OWLClass)),
			//2012.04.12 BUG?? some(objectProperty(Concepts.hasColumnMapping), owlClass("DBForeignKey")
			some(objectProperty(Concepts.hasColumnMapping), owlClass(Concepts.DBForeignKey)
			)
		);
		NodeSet<OWLNamedIndividual> S = reasoner().getInstances(q, false);
		//e.g. atAddress
		for(OWLNamedIndividual i : S.getFlattened())
		{
			OWLObjectProperty property = objectProperty(i.getIRI());
			Set<OWLNamedIndividual> foreignKeyColumns = reasoner().getObjectPropertyValues(i,objectProperty(Concepts.hasColumnMapping)).getFlattened();
			//e.g CIRM_SR_REQUESTS.SR_REQUEST_ADDRESS
			for(OWLNamedIndividual foreignKeyColumn : foreignKeyColumns)
			{
				Set<OWLNamedIndividual> foreignKeyTables = reasoner().getObjectPropertyValues(foreignKeyColumn,objectProperty(Concepts.hasTable)).getFlattened();
				//e.g 
				for(OWLNamedIndividual foreignKeyTable : foreignKeyTables)
				{
					//<http://www.miamidade.gov/ontology#atAddress>,  <http://www.miamidade.gov/ontology#CIRM_SR_ACTOR>; <http://www.miamidade.gov/ontology#CIRM_SR_ACTOR.SR_ACTOR_ADDRESS>
					hasOne.put(Collections.singletonMap(property, foreignKeyTable), foreignKeyColumn);
					//by table
					Set<Map<OWLObjectProperty, OWLNamedIndividual>> propToColumn;
					propToColumn = hasOneByTable.get(foreignKeyTable);
					if (propToColumn == null) {
						propToColumn = new HashSet<Map<OWLObjectProperty,OWLNamedIndividual>>();
						hasOneByTable.put(foreignKeyTable, propToColumn);
					}
					//<http://www.miamidade.gov/ontology#atAddress>, <http://www.miamidade.gov/ontology#CIRM_SR_ACTOR.SR_ACTOR_ADDRESS>
					propToColumn.add(Collections.singletonMap(property, foreignKeyColumn));
					// by property
					Set<OWLNamedIndividual> tables = hasOne2TablesByProperty.get(property);
					if (tables == null) {
						tables = new HashSet<OWLNamedIndividual>();
						hasOne2TablesByProperty.put(property, tables);
					}
					tables.add(foreignKeyTable);
				}
			}
		}
	}
	//OWLProperty and hasMany some OWLClass
	private void cacheHasMany()
	{
		hasMany = new LinkedHashMap<OWLObjectProperty, OWLClass>();
		hasManyByClass = new LinkedHashMap<OWLClass, OWLObjectProperty>();
		hasManyPropertyAndDomainByRangeClass = new LinkedHashMap<OWLClass, Map<OWLObjectProperty,OWLClass>>();
		Map<OWLObjectProperty, OWLClass> result = new LinkedHashMap<OWLObjectProperty, OWLClass>();
		OWLClassExpression q =
			and(
			owlClass(Refs.OWLProperty)
			,
			some(objectProperty(Concepts.hasMany),owlClass(Refs.OWLClass))
			);
		NodeSet<OWLNamedIndividual> S = reasoner().getInstances(q, false);
		for(OWLNamedIndividual propertyAsIndividual : S.getFlattened())
		{
			OWLObjectProperty property = objectProperty(propertyAsIndividual.getIRI());
			Set<OWLNamedIndividual> classes = reasoner().getObjectPropertyValues(propertyAsIndividual,objectProperty(Concepts.hasMany)).getFlattened();
			Set<OWLNamedIndividual> domainClasses = reasoner().getObjectPropertyValues(propertyAsIndividual,objectProperty(Concepts.toOne)).getFlattened();
			//one class per prop, but maybe many prop per class
			if(DBG)
			{
				System.out.print("Property:" + property);
				System.out.print("hasMany:" + classes);
				System.out.println("toOne:" +domainClasses);
			}
			OWLClass cle = owlClass(classes.iterator().next().getIRI());	
			OWLClass dcle = null;
			if(!domainClasses.isEmpty())
			{
				dcle = owlClass(domainClasses.iterator().next().getIRI());
				hasManyPropertyAndDomainByRangeClass.put(cle, Collections.singletonMap(property, dcle));
			}
			result.put(property, cle.asOWLClass());
			hasManyByClass.put(cle.asOWLClass(), property);
			
		 }	
		hasMany.putAll(result);
	}
	
	private void cacheJoinTables()
	{
		joins = new LinkedHashMap<Map<OWLNamedIndividual,OWLNamedIndividual>,OWLNamedIndividual>();
		joinsByTable = new LinkedHashMap<OWLNamedIndividual, Set<Map<OWLNamedIndividual,OWLNamedIndividual>>>();
		OWLClassExpression q =	some(objectProperty(Concepts.isJoinedWithTable), owlClass(Concepts.DBTable));
		NodeSet<OWLNamedIndividual> S = reasoner().getInstances(q, false);
		for (OWLNamedIndividual joinTable : S.getFlattened())
		{
			Set<OWLNamedIndividual> objectProperties = OWL.objectProperties(joinTable, Concepts.isJoinedWithTable);
			if(objectProperties.size() == 1)
			{
				//1 : 1 or 1 : *
				OWLNamedIndividual oneTable = objectProperties.iterator().next();
				// in the 1:* case the *table (manyTable) == joinTable
				joins.put(Collections.singletonMap(oneTable, joinTable),joinTable);
				Set<Map<OWLNamedIndividual, OWLNamedIndividual>> joinTableMaps = joinsByTable.get(oneTable);
				if (joinTableMaps == null) {
					joinTableMaps = new HashSet<Map<OWLNamedIndividual,OWLNamedIndividual>>();
					joinsByTable.put(oneTable, joinTableMaps);
				}
				joinTableMaps.add(Collections.singletonMap(joinTable, joinTable));
			}
			else if ( objectProperties.size() == 2) 
			{
				// *:* 
				OWLNamedIndividual manyTableA = null;
				OWLNamedIndividual manyTableB = null;
				Iterator<OWLNamedIndividual> obIt = objectProperties.iterator();  
				manyTableA = obIt.next();
				manyTableB = obIt.next();
//				for(OWLNamedIndividual j : objectProperties)
//				{
//					if(manyTableA == null)
//						manyTableA = j;
//					else if( manyTableB == null)
//						manyTableB = j;
//					else
//						break;
//				}
				joins.put(Collections.singletonMap(manyTableA, manyTableB), joinTable);
				joins.put(Collections.singletonMap(manyTableB, manyTableA), joinTable);
				Set<Map<OWLNamedIndividual, OWLNamedIndividual>> joinTableMapsForA = joinsByTable.get(manyTableA);
				Set<Map<OWLNamedIndividual, OWLNamedIndividual>> joinTableMapsForB = joinsByTable.get(manyTableB);
				if (joinTableMapsForA == null) 
				{
					joinTableMapsForA = new HashSet<Map<OWLNamedIndividual,OWLNamedIndividual>>();
					joinsByTable.put(manyTableA, joinTableMapsForA);
				}
				if (joinTableMapsForB == null) 
				{
					joinTableMapsForB = new HashSet<Map<OWLNamedIndividual,OWLNamedIndividual>>();
					joinsByTable.put(manyTableB, joinTableMapsForB);
				}
				joinTableMapsForA.add(Collections.singletonMap(joinTable, manyTableB));
				//joinsByTable.put(manyTableA, Collections.singletonMap(joinTable, manyTableB));
				joinTableMapsForB.add(Collections.singletonMap(joinTable, manyTableA));
				//joinsByTable.put(manyTableB, Collections.singletonMap(joinTable, manyTableA));
			}
			else 
			{
				throw new IllegalStateException("Expected 1 or 2, had : " + objectProperties.size());
			}
		}
	}
	
	private void cacheForeignKeyColumns()
	{
		foreignKeyByJoinColumnAndJoinTable = new LinkedHashMap<Map<OWLNamedIndividual,OWLNamedIndividual>,OWLNamedIndividual>();
		OWLClassExpression q =
			and(
			owlClass(Concepts.DBForeignKey)
			,
			some(objectProperty(Concepts.hasTable), owlClass(Concepts.DBTable))
			,
			some(objectProperty(Concepts.hasJoinColumn), owlClass(Concepts.DBColumn))
			);
		NodeSet<OWLNamedIndividual> S = reasoner().getInstances(q, false);
		for (OWLNamedIndividual foreignKey : S.getFlattened())
		{
			OWLNamedIndividual joinColumn = objectProperty(foreignKey,Concepts.hasJoinColumn );
			OWLNamedIndividual joinTable = objectProperty(foreignKey, Concepts.hasTable);
			foreignKeyByJoinColumnAndJoinTable.put(Collections.singletonMap(joinColumn, joinTable),foreignKey);
		}
	}
	
	private void cacheColumnPKs()
	{
		columnPK = new LinkedHashMap<OWLNamedIndividual,Set<OWLNamedIndividual>>();
		Set<OWLNamedIndividual> uniqueTables =  new HashSet<OWLNamedIndividual>();
		uniqueTables.addAll(tableMapping.values());
		for(OWLNamedIndividual table : uniqueTables)
		{
			Set<OWLNamedIndividual> columns = new HashSet<OWLNamedIndividual>();
			OWLClassExpression q = 
				and(
					owlClass(Concepts.DBPrimaryKey)
					,OWL.not(owlClass(Concepts.IRIKey))
					,has(objectProperty(Concepts.hasTable), table));
			NodeSet<OWLNamedIndividual> S = reasoner().getInstances(q, false);
			for (OWLNamedIndividual i : S.getFlattened())
				columns.add(i);
			if(columns.size()> 0)
				columnPK.put(table, columns);
		}
	}
	
	private void cacheColumnPKsJena()
	{
	   columnPK = new LinkedHashMap<OWLNamedIndividual,Set<OWLNamedIndividual>>();
	   Query query;
	   query = QueryFactory.read(SPARQL_PRIMARY_KEYS_PER_TABLE_NO_IRI);
	   QueryExecution queryExecution = SparqlDLExecutionFactory.create(query, jenaInfModel);
	   ResultSet rs = queryExecution.execSelect();
		while (rs.hasNext()) {
			QuerySolution solution = rs.next();
			OWLNamedIndividual table = individual(fullIri(solution.get("table").asResource().getURI()));
			OWLNamedIndividual pkColumn = individual(fullIri(solution.get("primaryKeyColumn").asResource().getURI()));
			Set<OWLNamedIndividual> pkColumnsOneTable;
			if(columnPK.containsKey(table))
			{
				pkColumnsOneTable = columnPK.get(table);
			}
			else 
			{
				pkColumnsOneTable = new HashSet<OWLNamedIndividual>();
				columnPK.put(table, pkColumnsOneTable);
			}
			pkColumnsOneTable.add(pkColumn);
			System.out.println("PK: " + pkColumn + " for table " + table);
		}
		logger.info(columnPK.size() + " tables with PKs.");	
	}
	
	private void cacheTableColumns()
	{		
		tableColumns = new LinkedHashMap<OWLNamedIndividual, Set<OWLNamedIndividual>>();
		Set<OWLNamedIndividual> columns = null;
		OWLClassExpression q = 
			and(
				owlClass(Concepts.DBColumn),
				some(objectProperty(Concepts.hasTable), owlClass(Concepts.DBTable)));
		for(OWLNamedIndividual column : reasoner().getInstances(q, false).getFlattened())
		{
			OWLNamedIndividual table = objectProperty(column, "hasTable"); 
			columns = tableColumns.get(table);
			if(columns == null)
			{
				columns = new HashSet<OWLNamedIndividual>();
				tableColumns.put(table, columns);
			}
			columns.add(column);
		}
	}
	
	private void cacheTableColumnsJena() {
		tableColumns = new LinkedHashMap<OWLNamedIndividual, Set<OWLNamedIndividual>>();
		Query query;
		query = QueryFactory.read(SPARQL_COLUMNS_BY_TABLE);
		QueryExecution queryExecution = SparqlDLExecutionFactory.create(query, jenaInfModel);
		ResultSet rs = queryExecution.execSelect();
		while (rs.hasNext()) 
		{
				QuerySolution solution = rs.next();
				OWLNamedIndividual table = individual(fullIri(solution.get("table").asResource().getURI()));
				OWLNamedIndividual column = individual(fullIri(solution.get("column").asResource().getURI()));
				if (column.getIRI().toString().length() > 1) 
				{
					Set<OWLNamedIndividual> columns;
					if(tableColumns.containsKey(table))
					{
						columns = tableColumns.get(table);
					}
					else 
					{
						columns = new HashSet<OWLNamedIndividual>();
						tableColumns.put(table, columns);
					}
					columns.add(column);
					System.out.println("Column: " + column + " for table " + table);
				}
		}
		logger.info(tableColumns.size() + " tables with columns.");	
	}

	public void printTableMappings() {
		System.out.println("TABLE MAPPINGS");
		for (Map.Entry<OWLClass, OWLNamedIndividual> mapping: tableMapping.entrySet()) {
			System.out.println("Class: " + mapping.getKey() 
					+ " -> Table: " + mapping.getValue());
		}
	}

	public void printColumnMappings() {
		System.out.println("COLUMN MAPPINGS");
		for (Map.Entry<OWLNamedIndividual, Map<OWLProperty<?, ?>, OWLNamedIndividual>> mapping: columnMapping.entrySet()) {
			System.out.println("Table: " + mapping.getKey());
			for (Map.Entry<OWLProperty<?, ?>, OWLNamedIndividual> column2Prop : mapping.getValue().entrySet()) {
				System.out.println(column2Prop.getKey() + " -> " + column2Prop.getValue());
			}
		}
	}

	public void printHasManyMappings() {
		System.out.println("HasMany MAPPINGS (ObjectProperty -> Class) ");
		for (Map.Entry<OWLObjectProperty, OWLClass> mapping: hasMany.entrySet()) {
			System.out.println("OWLObjectProperty: " + mapping.getKey() 
					+ " <-> OWLClass: " + mapping.getValue());
		}
	}

	public static void main(String[] args)
	{
		RelationalOWLMapper m = RelationalOWLMapper.getInstance();
		m.printTableMappings();
		m.printColumnMappings();
		m.printHasManyMappings();
		m.clearCache();
		m.refreshCache();
	}
}

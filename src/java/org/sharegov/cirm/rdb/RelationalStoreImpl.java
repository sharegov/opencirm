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
import static org.sharegov.cirm.OWL.hash;
import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.objectProperty;
import static org.sharegov.cirm.OWL.ontology;
import static org.sharegov.cirm.OWL.reasoner;
import static org.sharegov.cirm.rdb.Concepts.CLOB;
import static org.sharegov.cirm.rdb.Concepts.DOUBLE;
import static org.sharegov.cirm.rdb.Concepts.INTEGER;
import static org.sharegov.cirm.rdb.Concepts.TIMESTAMP;
import static org.sharegov.cirm.rdb.Concepts.VARCHAR;
import static org.sharegov.cirm.rdb.Sql.DELETE_FROM;
import static org.sharegov.cirm.rdb.Sql.INSERT_INTO;
import static org.sharegov.cirm.rdb.Sql.UPDATE;
import static org.sharegov.cirm.utils.GenUtils.dbg;

import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

import mjson.Json;
import oracle.net.aso.a;

import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.EntityType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLDataPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLDataRange;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLProperty;
import org.semanticweb.owlapi.model.OWLPropertyAssertionAxiom;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;



/**
 * RelationalStore(Base) provides methods to query and persist Business
 * Ontologies in a SQL Database.
 * 
 * There are two schemas: GENERIC and MAPPED. The GENERIC schema (aka vertical
 * schema) consists of CIRM_IRI CIRM_CLASSIFICATION CIRM_OWL_OBJECT_PROPERTY
 * CIRM_OWL_DATA_PROPERTY CIRM_OWL_DATA_VALUE
 * 
 * For some owlclasses class specific tables exist, the MAPPED schema (aka
 * horizontal schema). These classes are precisely defined in the county
 * ontology (http://www.miamidade.goc/ontology) under:
 * /Tangible/Software_Object/DBObject and subclasses. As of 2012.04.18 the
 * schema has the following tables: CIRM_SR_ACTIVITY, CIRM_SR_REQUESTS,
 * CIRM_SRREQ_SRACTOR, CIRM_SR_ACTOR, CIRM_MDC_ADDRESS, CIRM_SERVICE_ACTION,
 * CIRM_SERVICE_CALL
 * 
 * 
 * 
 * TODO Change CIRM_SR_REQUESTS to singular CIRM_SR_REQUEST !!
 * 
 * 
 * @see org.sharegov.cirm.rdb.RelationalOWLMapper
 * 
 *      Search for hilpoldQ to find questions tom has.
 * 
 * @author Syed Abbas, Thomas Hilpold
 */
public class RelationalStoreImpl implements RelationalStore
{
	public static boolean DBG = true;
	public static boolean DBGX = false;
	public static boolean DBGLOCK = true;
	public static boolean DBG_ALL_TRANSACTIONS_LOCK = false;
	public static boolean DBG_PRE_RETRY_SLEEP = true;
	public static boolean DBG_NO_CLASSIFICATION = true;
	
	/**
	 * Causes the toplevel transaction to retry at least twice each time.
	 * Only for testing txn. 
	 * Must never be true in production!
	 */
	public static boolean TEST_TXN_ALWAYS_RETRY_TWICE = false;

	public static boolean TXN_CHECK_CONNECTION = true;

	/**
	 * Absolute time limit on retries for one transaction independent of the
	 * number of attempted retries. 
	 */
	public final static int TXN_MAX_RETRY_MINUTES = 60;

	/**
	 * Pre retry sleep is calculated after 3 unsuccessful executions (2 retries)
	 * based on many factors before the 3rd and later retries.
	 * (total concurrency, execution time, et.c).
	 * This sets a maximum limit, imposed after calculation.
	 */
	public final static int TXN_MAX_PRE_RETRY_SLEEP_SECS = 10;


	private static final boolean USE_SEQUENCE_FOR_IDS = true;

	// private static final int AVG_WAIT_BEFORE_RETRY_MS = 500;

	public static final int POOL_SIZE_INITIAL = 5;
	public static final int POOL_SIZE_MAX = 50; //150 processes limit on server
	public static final int POOL_CONNECTION_REUSE_COUNT_MAX = 1000;
	public static final int POOL_CONNECTION_STATEMENTS_MAX = 40;
	public static final boolean POOL_CONNECTION_VALIDATE_ON_BORROW = true;
	public static final int POOL_CONNECTION_WAIT_TIMEOUT_SECS = 120;
	public static final int POOL_CONNECTION_INACTIVE_TIMEOUT_SECS = 8 * 3600; //before it is removed from pool
	public static final int POOL_CONNECTION_PREFETCH_ROWS = 50; //single db roundtrip
	public static final int POOL_CONNECTION_BATCH_ROWS = 50; //single db roundtrip

	/**
	 * Oracle supports snaphot isolation when set to serializable mode.
	 */
	public static int TRANSACTION_ISOLATION_LEVEL = Connection.TRANSACTION_SERIALIZABLE;

	private static Logger logger = Logger.getLogger("org.sharegov.cirm.rdb");

	public static final String TABLE_IRI = "CIRM_IRI";
	public static final String TABLE_IRI_TYPE = "CIRM_IRI_TYPE";
	public static final String VIEW_IRI = "CIRM_IRI_VIEW"; // includes IRI_TYPE
	public static final String TABLE_CLASSIFICATION = "CIRM_CLASSIFICATION";
	public static final String SEQUENCE = "CIRM_SEQUENCE";
	public static final String USER_FRIENDLY_SEQUENCE = "CIRM_USER_FRIENDLY_SEQUENCE";
	public static final String TABLE_OBJECT_PROPERTY = "CIRM_OWL_OBJECT_PROPERTY";
	public static final String TABLE_DATA_PROPERTY = "CIRM_OWL_DATA_PROPERTY";
	public static final String TABLE_DATA_VALUE_CLOB = "CIRM_OWL_DATA_VAL_CLOB";
	public static final String TABLE_DATA_VALUE_DATE = "CIRM_OWL_DATA_VAL_DATE";
	public static final String TABLE_DATA_VALUE_DOUBLE = "CIRM_OWL_DATA_VAL_DOUBLE";
	public static final String TABLE_DATA_VALUE_INTEGER = "CIRM_OWL_DATA_VAL_INTEGER";
	public static final String TABLE_DATA_VALUE_STRING = "CIRM_OWL_DATA_VAL_STRING";
	public static final String VIEW_DATA_PROPERTY = "CIRM_DATA_PROPERTY_VIEW";
	public static final String VIEW_DATA_PROPERTY_VALUE = "CIRM_DATA_PROP_VALUE_VIEW";
	public static final int MAX_VARCHAR_SIZE = 4000;
	public static final int VALUE_VARCHAR_SIZE = 255;
	// public static final int MAX_INCLAUSE_SIZE = 1000; // oracle limits sql
	// in() list to 1000 entries, see ORA-01795, so paging technique used.

//	private String url;
//	private String username;
//	private String password;

	private DataSourceRef dataSourceRef = null;
	
	//private volatile DataSource dataSource;
	
	private volatile DBLockStrategy lockingStrategy;
	
	private DatatypeFactory xmlDatatypeFactory;
	// private NumberFormat doubleFormat = NumberFormat.getNumberInstance();
	// private NumberFormat integerFormat = NumberFormat.getIntegerInstance();
	private Map<String, Long> iriTypes = null;

	private int currentLocalID = 1000; // Used if USE_SEQUENCE IS OFF
	
	public RelationalStoreImpl()
	{
//		this("jdbc:oracle:thin:@10.9.25.27:1521:xe",
//				"oracle.jdbc.OracleDriver", "cirmschm", "cirmschm");
		try
		{
			xmlDatatypeFactory = DatatypeFactory.newInstance();
		}
		catch (DatatypeConfigurationException e)
		{
			throw new RuntimeException("Failed to create xmlDataFactory", e);
		}
		lockingStrategy = new SimpleDBLockStrategy();
	}

//	public RelationalStoreImpl(String url, String driverClassName, String username,
//			String password)
//	{
//		this.url = url;
//		this.username = username;
//		this.password = password;
//	}

	public RelationalStoreImpl(DataSourceRef dataSourceRef)
	{
		this();
		this.dataSourceRef = dataSourceRef;
	}
	
	public Connection createNewDatabaseConnection()
	{
		try
		{
			return dataSourceRef.resolve().getConnection();			
//			if (dataSource == null)
//			{
//				synchronized (RelationalStoreImpl.class)
//				{
//					if (dataSource == null)
//					{
//						if (USE_CONNECTION_POOL)
//						{
//							dataSource = createPoolDatasource();
//						} else
//						{
//							dataSource = createDatasource();
//						}
//						System.out
//								.println("Connection transaction isolation level: "
//										+ TRANSACTION_ISOLATION_LEVEL);
//					}
//	
//				}
//			}
//			return dataSource.getConnection();
		}
		catch (SQLException ex)
		{
			throw new RuntimeException(ex);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.sharegov.cirm.rdb.RelationalStore#getConnection()
	 */
	//@Override
	public ThreadLocalConnection getConnection()
	{
		if(CirmTransaction.get() == null) 
		{
			System.err.println("RelationalStore: Error: getConnection was called outside of any transaction !");
			//TODO throw this after detecting all problems in existing code.
			new Exception().printStackTrace();
		}
		ThreadLocalConnection conn = ThreadLocalConnection.getThreadLocalConnection();
		if (conn == null)
		{
			try
			{
//				if (dataSource == null)
//				{
//					synchronized (this)
//					{
//						if (dataSource == null)
//						{
//							if (USE_CONNECTION_POOL)
//							{
//								dataSource = createPoolDatasource();
//								System.out.println("Data source acquired: " + dataSource);
//							} else
//							{
//								dataSource = createDatasource();
//							}
//							System.out
//									.println("Connection transaction isolation level: "
//											+ TRANSACTION_ISOLATION_LEVEL);
//						}
//
//					}
//				}
				Connection toBeWrapped = dataSourceRef.resolve().getConnection();
//				if (dataSource instanceof PoolDataSource) 
//				{
//					PoolDataSource pds = (PoolDataSource) dataSource;
//					if ((POOL_SIZE_MAX - pds.getBorrowedConnectionsCount()) < 5)
//					{
//						System.err.println("PoolDataSource - LESS THAN 5 CONNECTIONS AVAILABLE");
//						//DBGUtils.printPoolDataSourceInfo(pds);
//					}
//				}
				if (toBeWrapped.getHoldability() == ResultSet.HOLD_CURSORS_OVER_COMMIT)
				{
					// System.out.println("HOLD CURSORS ON COMMIT WAS DEFAULT. CHANGING TO CLOSE ON COMMIT.");
					toBeWrapped.setHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT);
				}
				// Snapshot Isolation MVCC for Oracle?
				if (toBeWrapped.getTransactionIsolation() != TRANSACTION_ISOLATION_LEVEL)
				{
					toBeWrapped.setTransactionIsolation(TRANSACTION_ISOLATION_LEVEL);
				}
				if (toBeWrapped.getAutoCommit())
				{ // TODO THIS COMMITS THE TRANSACTION !!!!! THINK OF THE POOL!
					toBeWrapped.setAutoCommit(false);
				}
				// create a full functioning war
				conn = ThreadLocalConnection.createThreadLocalConnectionTopLevel(toBeWrapped);
			} catch (SQLException sex)
			{
				System.err.println(sex.toString());
				sex.printStackTrace();
				throw new RuntimeException(sex);
			}
		}
		try
		{
			if (conn.isClosed())
				throw new IllegalStateException();
		} catch (SQLException e)
		{			
			e.printStackTrace();
		}
		return conn;
	}

	/**
	 * 
	 * @throws SQLException
	 */
//	private PoolDataSource createPoolDatasource() throws SQLException
//	{		
//		String poolName = "Cirm UCP Pool for " + this.getClass().getSimpleName();
//		PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();
//		pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
//		pds.setURL(url);
//		pds.setUser(username);
//		pds.setPassword(password);
//		pds.setConnectionPoolName(poolName);
//		pds.setInitialPoolSize(POOL_SIZE_INITIAL);
//		pds.setMinPoolSize(POOL_SIZE_INITIAL);
//		pds.setMaxPoolSize(POOL_SIZE_MAX);
//		pds.setMaxConnectionReuseCount(POOL_CONNECTION_REUSE_COUNT_MAX);
//		//Sets implicit statement cache on all pooled connections
//		pds.setMaxStatements(POOL_CONNECTION_STATEMENTS_MAX);
//		pds.setValidateConnectionOnBorrow(POOL_CONNECTION_VALIDATE_ON_BORROW);
//		//How long to wait if a conn is not available
//		pds.setConnectionWaitTimeout(POOL_CONNECTION_WAIT_TIMEOUT_SECS);
//		//How many secs to wait until a pooled and unused connection is removed from pool
//		// 8 h
//		pds.setInactiveConnectionTimeout(POOL_CONNECTION_INACTIVE_TIMEOUT_SECS);
//		Properties connectionProperties = new Properties();
//		connectionProperties.setProperty("defaultRowPrefetch", "" + POOL_CONNECTION_PREFETCH_ROWS);
//		connectionProperties.setProperty("defaultBatchValue", "" + POOL_CONNECTION_BATCH_ROWS);
//		pds.setConnectionProperties(connectionProperties);
//		System.out.println("ORACLE POOL DATA SOURCE : ");		
//		System.out.println("DB URL : " + url);
//		try {
//			Connection testConn = pds.getConnection();
//			testConn.close();
//		} catch (Exception e)
//		{
//			ThreadLocalStopwatch.getWatch().time("POOL DATA SOURCE: FAILED TO GET A TEST CONNECTION FROM POOL!\r\n Exception was: ");
//			e.printStackTrace();
//			System.err.print("Attemting to destroy the failing pool \"" + poolName + "\"...");
//			try
//			{
//				UniversalConnectionPoolManager pm = UniversalConnectionPoolManagerImpl.getUniversalConnectionPoolManager();
//				pm.destroyConnectionPool(poolName);
//				System.err.println("Succeeded.");
//			} catch (UniversalConnectionPoolException e1)
//			{
//				System.err.println("Failed. Exception on failing to destroy pool was:");
//				e1.printStackTrace();
//			}
//			throw new RuntimeException(e);
//		}
//		DBGUtils.printPoolDataSourceInfo(pds);
//		return pds;
//	}
//
//	/**
//	 * Gets a pooled connection with auto commit disabled.
//	 * 
//	 * @return
//	 * @throws SQLException
//	 */
//	private OracleDataSource createDatasource() throws SQLException
//	{
//		OracleDataSource ods = new OracleDataSource();
//		ods.setURL(url);
//		ods.setUser(username);
//		ods.setPassword(password);
//		// FOR DEBUGGING DB ods.setLogWriter(new PrintWriter(System.out));
//		// hilpold maybe use: ods.setConnectionCachingEnabled(arg0);
//		// ods.setExplicitCachingEnabled(arg0);
//		// ods.setConnectionCacheProperties(arg0);;
//		// ods.setImplicitCachingEnabled(arg0);
//		// ods.setConnectionProperties(arg0);
//		System.out.println("Oracle Datasource created : ");
//		System.out.println("ConnectionCachingEnabled  : "
//				+ ods.getConnectionCachingEnabled());
//		System.out.println("ConnectionCacheProperties : "
//				+ ods.getConnectionCacheProperties());
//		System.out.println("ImplicitCachingEnabled    : "
//				+ ods.getImplicitCachingEnabled());
//		System.out.println("ExplicitCachingEnabled    : "
//				+ ods.getExplicitCachingEnabled());
//		System.out.println("MaxStatements             : "
//				+ ods.getMaxStatements());
//		return ods;
//	}

	/* (non-Javadoc)
	 * @see org.sharegov.cirm.rdb.RelationalStore#nextSequenceNumber()
	 */
	//@Override
	public long nextSequenceNumber()
	{
		return txn(new CirmTransaction<Long>() {
		public Long call() 
		{
			Connection conn = getConnection();
			try
			{
				long nextId = dataSourceRef.getHook().nextSequence(conn, SEQUENCE);
				conn.commit();
				return nextId;
			} 
			catch (SQLException e)
			{
				rollback(conn);
				e.printStackTrace();
				throw new RuntimeException(e);
			} 
			finally
			{
				DBU.close(conn, null, null);
			}			
		}});
	}

	/* (non-Javadoc)
	 * @see org.sharegov.cirm.rdb.RelationalStore#nextUserFriendlySequenceNumber()
	 */
	//@Override
	public long nextUserFriendlySequenceNumber()
	{
		Connection conn = getConnection();
		try
		{
			 long nextId = dataSourceRef.getHook().nextSequence(conn, USER_FRIENDLY_SEQUENCE);
			conn.commit();
			return nextId;
		} 
		catch (SQLException e)
		{
			rollback(conn);
			e.printStackTrace();
			throw new RuntimeException(e);
		} 
		finally
		{
			DBU.close(conn, null, null);
		}
	}

	// -------------------------------------------------------------------------
	// VERTICAL SCHEMA TIP METHODS
	//

	/**
	 * Returns all IDs and relevant Entities from the given ontology. This
	 * includes: A) Classes, Individuals, ObjectProperties, DataProperties in
	 * the Signature of the given ontology. B) Datatypes in the signature or
	 * importclosure of the ontology.
	 * 
	 * @throws a
	 *             RuntimeException, with any occuring SQLException as cause.
	 */
	public Map<OWLEntity, DbId> selectIDsAndEntitiesByIRIs(OWLOntology ontology, 
	                                                       DbId boObj, 
	                                                       boolean insertIfMissing)
	{
		Map<OWLEntity, DbId> result;
		Set<OWLEntity> entities = new HashSet<OWLEntity>();
		entities.addAll(ontology.getClassesInSignature());
		// Do not try to load id or insert already known boObj
		Set<OWLNamedIndividual> namedIndividuals = ontology.getIndividualsInSignature();
		if (boObj != null) 
			namedIndividuals.remove(boObj.getSecond());
		entities.addAll(namedIndividuals);
		entities.addAll(ontology.getObjectPropertiesInSignature());
		entities.addAll(ontology.getDataPropertiesInSignature());
		// 2013.01.24 hilpold OLD:
		// entities.addAll(ontology.getDatatypesInSignature(true));
		entities.addAll(ontology.getDatatypesInSignature(false));
		result = selectInsertIDsAndEntitiesByIRIs(entities, insertIfMissing);
		// Add already known and guaranteed to be inserted boObj
		if (boObj != null)
			result.put(boObj.getSecond(), boObj);
		return result;
	}

	/**
	 * @throws a
	 *             RuntimeException, with any occuring SQLException as cause.
	 */
	public Map<OWLEntity, DbId> selectInsertIDsAndEntitiesByIRIs(Set<? extends OWLEntity> objects, boolean insertIfMissing)
	{
		Connection conn = getConnection();
		Map<OWLEntity, DbId> result;
		// long iriCount = selectIRICount(conn);
		try
		{
			if (dbg())
				ThreadLocalStopwatch.getWatch().reset("selectInsertIDsAndEntitiesByIRIs() - start");
			result = selectIDsAndEntitiesByIRIs(objects, conn, false);
			if (dbg())
				ThreadLocalStopwatch.getWatch().time("selectInsertIDsAndEntitiesByIRIs() - DB select to find existing IRIs");
			if ((result.size() < objects.size() /* || !result.keySet().equals(objects) */) && insertIfMissing)
			{
				Set<OWLEntity> entitiesToInsert = new HashSet<OWLEntity>(objects);
				// o.addAll(objects);
				entitiesToInsert.removeAll(result.keySet());
				Map<OWLEntity, DbId> insertedIDsNew = insertNewEntities(entitiesToInsert, conn);
				if (dbg())
					ThreadLocalStopwatch.getWatch().time("selectInsertIDsAndEntitiesByIRIs() - DB insert new IRIs");
				// long iriCountPostInsert = selectIRICount(conn);
				// if (iriCount + entitiesToInsert.size() != iriCountPostInsert)
				// {
				// System.err.println("Oracle bug detected in selectIDsAndEntitiesByIRIs: After Inserting : "
				// + iriCount + entitiesToInsert.size() +
				// " a select only returned: " + iriCountPostInsert);
				// throw new RuntimeException(new
				// SQLException("Oracle Select after Insert bug detected. Emulating cannot serialize to cause retry of whole transaction.",
				// "Should close connection and retry", 8177));
				// }
				if (entitiesToInsert.size() != insertedIDsNew.size())
				{
					System.err.println("selectIDsAndEntitiesByIRIs: entitiesToInsert.size() != insertedIDsNew.size()"
									+ entitiesToInsert.size()
									+ " inserted(NEW): "
									+ insertedIDsNew.size());
					throw new IllegalStateException("Deep trouble. See log.");
				}
				if (dbg())
					ThreadLocalStopwatch
							.getWatch()
							.time("selectInsertIDsAndEntitiesByIRIs() - DB select to find all IRIs (newly inserted + existing) IRIs");
				// Error handling:
				if (DBGX)
				{
					Map<OWLEntity, DbId> insertedIDs = selectIDsAndEntitiesByIRIs(entitiesToInsert, conn, true);
					if (entitiesToInsert.size() != insertedIDs.size())
					{
						System.err
								.println("selectIDsAndEntitiesByIRIs: After insert query must match size of entitiesToInsert; insert failed otherwise. To insert: "
										+ entitiesToInsert.size()
										+ " inserted: " + insertedIDs.size());
						entitiesToInsert.removeAll(insertedIDs.keySet());
						for (OWLEntity e : entitiesToInsert)
						{
							if (insertedIDs.keySet().contains(e))
							{
								System.err.print("FOUND    : ");
							} else
							{
								System.err.print("NOT FOUND: ");
							}
							System.err.println(" IRI: " + e.getIRI().toString()
									+ " type: " + e.getEntityType());
						}
					}
				}
				result.putAll(insertedIDsNew);
			}
			conn.commit();
			return result;
		} catch (SQLException e)
		{
			rollback(conn);
			throw new RuntimeException(e);
		} finally
		{
			close(conn);
		}
	}

	// /**
	// * This method acquires and closes a db connection. In most situations one
	// * wants to pass the connection as parameter.
	// *
	// * @param entitiesWithIRIs
	// * @return
	// */
	// @Deprecated
	// public Map<OWLEntity, Long> selectIDsAndEntitiesByIRIs(Set<? extends
	// OWLEntity> entitiesWithIRIs)
	// {
	// Connection conn = getConnection();
	// try
	// {
	// return selectIDsAndEntitiesByIRIs(entitiesWithIRIs, conn);
	// }
	// catch (SQLException e)
	// {
	// throw new RuntimeException(e);
	// }
	// finally
	// {
	// close(conn);
	// }
	// }

	public Map<OWLEntity, DbId> selectIDsAndEntitiesByIRIs(OWLEntity entityWithIRI)
	{
		return selectIDsAndEntitiesByIRIs(Collections.singleton(entityWithIRI));
	}

	public Map<OWLEntity, DbId> selectIDsAndEntitiesByIRIs(final Set<? extends OWLEntity> entitiesWithIRIs)
	{
		return txn(new CirmTransaction<Map<OWLEntity, DbId>>()
		{
			@Override
			public Map<OWLEntity, DbId> call() throws Exception
			{
				return selectIDsAndEntitiesByIRIsInt(entitiesWithIRIs);
			}
		});
	}
	public Map<OWLEntity, DbId> selectIDsAndEntitiesByIRIsInt(Set<? extends OWLEntity> entitiesWithIRIs)
	{
		Connection conn = getConnection();
		try
		{
			Map<OWLEntity, DbId> ret = selectIDsAndEntitiesByIRIs(entitiesWithIRIs, conn);
			try
			{
				conn.commit();
			}
			catch (SQLException e)
			{
				rollback(conn);
				throw new RuntimeException(e);
			}
			return ret;
		} finally
		{
			close(conn);
		}
	}

	public Map<OWLEntity, DbId> selectIDsAndEntitiesByIRIs(
			Set<? extends OWLEntity> entitiesWithIRIs, Connection conn)
	{
		return selectIDsAndEntitiesByIRIs(entitiesWithIRIs, conn, false);
	}

	public Map<OWLEntity, DbId> selectIDsAndEntitiesByIRIs(Set<? extends OWLEntity> entitiesWithIRIs, Connection conn, boolean shouldFindAll)
	{
		Map<OWLEntity, DbId> result = new HashMap<OWLEntity, DbId>(entitiesWithIRIs.size() * 2 + 1);
		StringBuilder select = new StringBuilder();
		select.append("SELECT ID FROM ").append(TABLE_IRI).append(" WHERE ")
				.append("IRI = ? AND IRI_TYPE_ID = ? ");
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = conn.prepareStatement(select.toString());
			for (OWLEntity e : entitiesWithIRIs)
			{
				long entityTypeID = selectIDByEntityType(e.getEntityType());
				String iriStr = e.getIRI().toString();
				pstmt.setString(1, iriStr);
				pstmt.setLong(2, entityTypeID);
				rs = pstmt.executeQuery();
				if (rs.next())
				{
					result.put(e, new DbId(rs.getLong(1), e, true));
				}
				rs.close();
			}
		} 
		catch (SQLException e)
		{
			if (!canRetrySQL(e))
			{
				System.err.println("selectIDsAndEntitiesByIRIs exception " + e);
			}
			throw new RuntimeException(e);
		} 
		finally
		{
			close(rs, pstmt);
		}
		if (shouldFindAll && entitiesWithIRIs.size() != result.size())
			System.err.println("Not all entities found - missing were " + (entitiesWithIRIs.size() - result.size()) + " of " + entitiesWithIRIs.size());
		return result;
	}

	// /**
	// * This will find all entities of all types that match the given entities'
	// IRIs.
	// * e.g. if one individual x is given, it would find individual x,
	// dataproperty x, class x, if it's in the Db and the IRI matches.
	// * @param entitiesWithIRIs
	// * @param conn
	// * @param shouldFindAll
	// * @return
	// * @deprecated
	// */
	// private Map<OWLEntity, Long> selectIDsAndEntitiesByIRIsOld(Set<? extends
	// OWLEntity> entitiesWithIRIs, Connection conn, boolean shouldFindAll,
	// OWLDataFactory df)
	// {
	// Map<OWLEntity, Long> result = new LinkedHashMap<OWLEntity, Long>();
	// java.sql.Statement stmt = null;
	// ResultSet rs = null;
	// StringBuilder select = new StringBuilder();
	// //OWLDataFactory dataFactory = OWL.dataFactory();
	// if (entitiesWithIRIs == null || entitiesWithIRIs.size() == 0)
	// return result;
	// int pageSize = 1000; // oracle limits sql in() list to 1000 entries, see
	// // ORA-01795, so paging technique used.
	// int pageCount = 1;
	// if (entitiesWithIRIs.size() > 1000)
	// pageCount = (entitiesWithIRIs.size() + pageSize - 1) / pageSize;
	// try
	// {
	// List<OWLEntity> set = new ArrayList<OWLEntity>(entitiesWithIRIs);
	// for (int g = 0; g < pageCount; g++)
	// {
	// select.delete(0, select.length());
	// //TODO query by IRI AANNDD type
	// select.append("SELECT ID, IRI, IRI_TYPE FROM ").append(VIEW_IRI).append(" WHERE ").append("IRI IN (");
	// //for (int i = (g * pageSize); i < (g + 1) * pageSize && i <
	// entitiesWithIRIs.size(); i++)
	// // select.append("?,");
	// for (int i = (g * pageSize); i < (g + 1) * pageSize && i <
	// entitiesWithIRIs.size(); i++)
	// select.append("'" + set.get(i).getIRI().toString() + "',");
	// select.deleteCharAt(select.lastIndexOf(",")).append(")");
	// stmt = conn.createStatement(); // prepareStatement(select.toString());
	// if(DBG)
	// {
	// System.out.println(select.toString());
	// }
	// rs = stmt.executeQuery(select.toString());
	// if (stmt.getWarnings() != null) {
	// throw new IllegalStateException("" + stmt.getWarnings());
	// }
	// int i = 0;
	// while (rs.next())
	// {
	// i++;
	// //String iriStr =
	// OWLEntity o = df.getOWLEntity(typeOf(rs.getString("IRI_TYPE")),
	// IRI.create(rs.getString("IRI")));
	//
	// long id = rs.getLong("ID");
	// if (rs.wasNull()) throw new IllegalStateException("ID was null!");
	// //2012.11.08 commenting out check, because after optimization, we have 2
	// with same IRI and different types:
	// //if (!entitiesWithIRIs.contains(o)) {
	// // throw new
	// IllegalArgumentException("selectIDsAndEntitiesByIRIs: created element that's not contained in entitiesWithIRIs : "
	// + o);
	// //}
	// //assert end
	// if (result.put(o, id) != null) {
	// throw new
	// IllegalArgumentException("selectIDsAndEntitiesByIRIs: Inserted already existing entity into result map. Entity: "
	// + o + " Id: " + id);
	// }
	// if (rs.getWarnings() != null) {
	// System.err.println("RS warnings: " + rs.getWarnings());
	// }
	// }
	// //2012.05.03 hilpold rs and statement need to be closed here!!
	// close(rs, stmt);
	// }
	// if (shouldFindAll
	// && entitiesWithIRIs.size() > result.size() ) {
	// System.err.print("Oracle Bug detected. selectIDsAndEntitiesByIRIs NOT ALL FOUND: ");
	// //System.err.println("Statement was: " + select.toString());
	// System.err.println("entitiesWithIRIs.size() was: " +
	// entitiesWithIRIs.size() + " result.size() was " + result.size());
	// throw new
	// SQLException("Oracle Select after Insert bug detected. Emulating cannot serialize exc to cause retry of whole transaction.",
	// "Should close connection and retry", 8177);
	// //System.err.println("Resultset had Rows: " + i);
	// }
	// }
	// catch (SQLException e)
	// {
	// if (!isCannotSerializeException(e)) {
	// System.err.println("selectIDsAndEntitiesByIRIs exception " + e);
	// }
	// throw new RuntimeException(e);
	// } finally {
	// close(rs, stmt);
	// }
	// return result;
	// }

	// /**
	// * Inserts entities (IRI and IRIType) into IRI table generating a new ID
	// for
	// * each. No duplicate check is performed in this method. Caller needs to
	// * make sure that entities don't have an ID assigned yet.
	// *
	// * Commit the connection after calling this method.
	// *
	// * @param entities
	// * @return
	// * @throws SQLException
	// */
	// public Map<OWLEntity, Long> insertNewEntitiesNoBatch(Set<? extends
	// OWLEntity> entities, Connection conn) throws SQLException
	// {
	// PreparedStatement stmt = null;
	// Map<OWLEntity, Long> result = new HashMap<OWLEntity,
	// Long>(entities.size() * 2);
	// // Connection conn = null;
	// // int[] result = {};
	// StringBuffer insert = new StringBuffer();
	// //
	// insert.append("INSERT INTO ").append(TABLE_IRI).append("(").append("ID").append(",IRI").append(", IRI_TYPE_ID")
	// //
	// .append(")").append("VALUES").append("(").append(SEQUENCE).append(".NEXTVAL").append(",?,?")
	// // .append(")");
	// insert.append("INSERT INTO ").append(TABLE_IRI).append("(").append("ID").append(",IRI").append(", IRI_TYPE_ID")
	// .append(")").append("VALUES").append("(?,?,?)");
	// OWLEntity curEntity = null;
	// try
	// {
	// // conn = getConnection();
	// // conn.setAutoCommit(false);
	// stmt = conn.prepareStatement(insert.toString());
	// Map<String,Long> iriTypes = selectIriTypesCached();
	// for (OWLEntity entity : entities)
	// {
	// curEntity = entity;
	// long entityId;
	// if (USE_SEQUENCE_FOR_IDS) {
	// entityId = nextSequenceNumber();
	// } else {
	// entityId = getNextLocalID();
	// }
	// stmt.setLong(1, entityId);
	// String entityIRIStr = entity.getIRI().toString();
	// if (entityIRIStr.isEmpty()) throw new
	// IllegalArgumentException("Empty IRI String");
	// stmt.setString(2, entityIRIStr);
	// //This will throw an NPE if iriType not found.
	// long iriTypeId = iriTypes.get(entity.getEntityType().getName());
	// stmt.setLong(3, iriTypeId);
	// int insertedRows = stmt.executeUpdate();
	// if (insertedRows != 1) throw new
	// IllegalStateException("insertedRows != 1");
	// stmt.clearParameters();
	// if (result.put(entity, entityId) != null) {
	// throw new
	// IllegalStateException("Inserting an entity that already existed.");
	// }
	// }
	// //int[] batchResult = stmt.executeBatch();
	// // for (int i = 0; i < batchResult.length; i ++) {
	// // if (batchResult[i] == java.sql.Statement.EXECUTE_FAILED) {
	// // throw new SQLException("Execution of batch update no: " + i + " of :"
	// + batchResult.length + " failed.");
	// // }
	// // }
	// return result;
	// }
	// catch (SQLException e)
	// {
	// // System.err.println("SQLException on executing: " + insert.toString() +
	// " for entity: param 1 "
	// // + curEntity.getIRI() + " param 2 " + curEntity.getEntityType());
	// //e.printStackTrace();
	// throw e;
	// // try
	// // {
	// // conn.rollback();
	// // }
	// // catch (SQLException f)
	// // {
	// // f.printStackTrace();
	// // }
	// }
	// finally
	// {
	// close(stmt);
	// }
	// }

	/**
	 * Inserts entities (IRI and IRIType) into IRI table generating a new ID for
	 * each. No duplicate check is performed in this method. Caller needs to
	 * make sure that entities don't have an ID assigned yet.
	 * 
	 * Commit the connection after calling this method.
	 * 
	 * @param entities
	 * @return
	 * @throws SQLException
	 */
	public Map<OWLEntity, DbId> insertNewEntities(Set<? extends OWLEntity> entities, Connection conn)
			throws SQLException
	{
		PreparedStatement stmt = null;
		Map<OWLEntity, DbId> result = new HashMap<OWLEntity, DbId>(entities.size() * 2);
		StringBuffer insert = new StringBuffer();
		insert.append("INSERT INTO ").append(TABLE_IRI).append("(")
				.append("ID").append(",IRI").append(", IRI_TYPE_ID")
				.append(")").append("VALUES").append("(?,?,?)");
		OWLEntity curEntity = null;
		try
		{
			// conn = getConnection();
			// conn.setAutoCommit(false);
			stmt = conn.prepareStatement(insert.toString());
			Map<String, Long> iriTypes = selectIriTypesCached();
			for (OWLEntity entity : entities)
			{
				curEntity = entity;
				long entityId;
				if (USE_SEQUENCE_FOR_IDS)
				{
					entityId = nextSequenceNumber();
				} 
				else
				{
					entityId = getNextLocalID();
				}
				stmt.setLong(1, entityId);
				String entityIRIStr = entity.getIRI().toString();
				if (entityIRIStr.isEmpty())
					throw new IllegalArgumentException("Empty IRI String");
				stmt.setString(2, entityIRIStr);
				// This will throw an NPE if iriType not found.
				long iriTypeId = iriTypes.get(entity.getEntityType().getName());
				stmt.setLong(3, iriTypeId);
				stmt.addBatch();
				if (result.put(entity, new DbId(entityId, entity, false)) != null)
				{
					throw new IllegalStateException(
							"Inserting an entity that already existed.");
				}
			}
			int[] batchResult = stmt.executeBatch();
			for (int i = 0; i < batchResult.length; i++)
			{
				if (batchResult[i] == java.sql.Statement.EXECUTE_FAILED)
				{
					throw new SQLException("Execution of batch update no: " + i
							+ " of :" + batchResult.length + " failed.");
				}
			}
			return result;
		} 
		catch (SQLException e)
		{
			if (!canRetrySQL(e))
			{
				System.err
						.println("insertNewEntities: SQLException on executing: "
								+ insert.toString()
								+ " for entity: param 1 "
								+ curEntity.getIRI()
								+ " param 2 "
								+ curEntity.getEntityType());
				e.printStackTrace();
				//hilpold check for NPE here:
				e.getNextException().printStackTrace();
			}
			throw e;
		} finally
		{
			close(stmt);
		}
	}

	
	public Map<OWLEntity, Long> insertNewEntitiesBatch(
			Set<? extends OWLEntity> entities, Connection conn)
			throws SQLException
	{
		PreparedStatement stmt = null;
		Map<OWLEntity, Long> result = new HashMap<OWLEntity, Long>(
				entities.size() * 2);
		StringBuffer insert = new StringBuffer();
		insert.append("INSERT INTO ").append(TABLE_IRI).append("(")
				.append("ID").append(",IRI").append(", IRI_TYPE_ID")
				.append(")").append("VALUES").append("(?,?,?)");
		OWLEntity curEntity = null;
		try
		{
			// conn = getConnection();
			// conn.setAutoCommit(false);
			stmt = conn.prepareStatement(insert.toString());
			Map<String, Long> iriTypes = selectIriTypesCached();
			for (OWLEntity entity : entities)
			{
				curEntity = entity;
				long entityId;
				if (USE_SEQUENCE_FOR_IDS)
				{
					entityId = nextSequenceNumber();
				} else
				{
					entityId = getNextLocalID();
				}
				stmt.setLong(1, entityId);
				String entityIRIStr = entity.getIRI().toString();
				if (entityIRIStr.isEmpty())
					throw new IllegalArgumentException("Empty IRI String");
				stmt.setString(2, entityIRIStr);
				// This will throw an NPE if iriType not found.
				long iriTypeId = iriTypes.get(entity.getEntityType().getName());
				stmt.setLong(3, iriTypeId);
				stmt.addBatch();
				if (result.put(entity, entityId) != null)
				{
					throw new IllegalStateException(
							"Inserting an entity that already existed.");
				}
			}
			int[] batchResult = stmt.executeBatch();
			for (int i = 0; i < batchResult.length; i++)
			{
				if (batchResult[i] == java.sql.Statement.EXECUTE_FAILED)
				{
					throw new SQLException("Execution of batch update no: " + i
							+ " of :" + batchResult.length + " failed.");
				}
			}
			return result;
		} catch (SQLException e)
		{
			if (!canRetrySQL(e))
			{
				System.err
						.println("insertNewEntities: SQLException on executing: "
								+ insert.toString()
								+ " for entity: param 1 "
								+ curEntity.getIRI()
								+ " param 2 "
								+ curEntity.getEntityType());
				e.printStackTrace();
			}
			throw e;
		} finally
		{
			close(stmt);
		}
	}

	private long getNextLocalID()
	{
		return currentLocalID++;
	}

	/**
	 * 
	 * @param subject
	 * @param identifiers
	 * @return
	 */
	public Map<OWLObjectPropertyExpression, Set<OWLIndividual>> selectObjectProperties(
			OWLIndividual subject, Map<OWLEntity, DbId> identifiers,
			OWLDataFactory df)
	{
		PreparedStatement stmt = null;
		Connection conn = null;
		ResultSet rs = null;
		Map<OWLObjectPropertyExpression, Set<OWLIndividual>> result = null;
		StringBuilder select = new StringBuilder();
		select.append("SELECT  B.IRI as PREDICATE, C.IRI as OBJECT FROM ")
				.append(TABLE_OBJECT_PROPERTY)
				.append(" A,")
				.append(TABLE_IRI)
				.append(" B,")
				.append(TABLE_IRI)
				.append(" C")
				.append(" WHERE ")
				.append("SUBJECT =")
				.append("? AND A.PREDICATE = B.ID AND A.OBJECT = C.ID AND TO_DATE IS NULL ORDER BY A.PREDICATE");
		DbId s = identifiers.get(subject.asOWLNamedIndividual());
		if (s == null)
		{
			throw new IllegalArgumentException(
					"Subject not contained in identifiers' map : " + subject);
		}
		conn = getConnection();
		try
		{
			stmt = conn.prepareStatement(select.toString());
			stmt.setLong(1, s.getFirst());
			rs = stmt.executeQuery();
			// OWLDataFactory factory = OWL.dataFactory();
			result = new HashMap<OWLObjectPropertyExpression, Set<OWLIndividual>>();
			while (rs.next())
			{
				OWLObjectProperty predicate = df.getOWLObjectProperty(IRI
						.create(rs.getString("PREDICATE")));
				OWLIndividual object = df.getOWLNamedIndividual(IRI.create(rs
						.getString("OBJECT")));
				if (result.containsKey(predicate))
					result.get(predicate).add(object);
				else
				{
					Set<OWLIndividual> v = new HashSet<OWLIndividual>();
					v.add(object);
					result.put(predicate, v);
				}
			}
			conn.commit();
		} catch (SQLException e)
		{
			rollback(conn);
			throw new RuntimeException(e);
		} finally
		{
			close(rs, stmt, conn);
		}
		return result;
	}

	// //Used by MessageManager only
	// public Map<OWLDataPropertyExpression, Set<OWLLiteral>>
	// selectDataProperties(OWLIndividual subject)
	// {
	// Set<OWLEntity> s = new HashSet<OWLEntity>();
	// s.add(subject.asOWLNamedIndividual());
	// Map<OWLEntity, Long> identifiers = selectIDsAndEntitiesByIRIs(s, true);
	// return selectDataProperties(subject, identifiers);
	// }

	public Map<OWLDataPropertyExpression, Set<OWLLiteral>> selectDataProperties(
			OWLIndividual subject, 
			Map<OWLEntity, DbId> identifiers,
			OWLDataFactory df)
	{
		return selectDataProperties(subject, null, identifiers, df);
	}

	/**
	 * 
	 * @param subject
	 * @param version
	 *            may be null, would return most current.
	 * @param identifiers
	 * @return
	 */
	public Map<OWLDataPropertyExpression, Set<OWLLiteral>> selectDataProperties(
			OWLIndividual subject, Date version,
			Map<OWLEntity, DbId> identifiers, OWLDataFactory df)
	{
		PreparedStatement stmt = null;
		Connection conn = null;
		ResultSet rs = null;
		Map<OWLDataPropertyExpression, Set<OWLLiteral>> result = null;
		StringBuffer select = new StringBuffer();
		select.append("SELECT PREDICATE_IRI, DATATYPE_IRI")
				.append(",VALUE_VARCHAR").append(",VALUE_CLOB")
				.append(",VALUE_DATE").append(",VALUE_DOUBLE")
				.append(",VALUE_INTEGER").append(",VALUE_VARCHAR_LONG")
				.append(" FROM ").append("").append(VIEW_DATA_PROPERTY_VALUE)
				.append(" WHERE ").append(" SUBJECT =").append("? "); // / ORDER
																		// BY
																		// A.PREDICATE;
		if (version != null)
		{
			select.append(" AND FROM_DATE <= ? ").append(
					"AND (TO_DATE >= ? or TO_DATE is null) ");
		} 
		else
		{
			select.append("AND TO_DATE is null");
		}
		try
		{
			Long s = identifiers.get(subject.asOWLNamedIndividual()).getFirst();
			if (s == null)
			{
				throw new IllegalArgumentException(
						"Subject not contained in identifiers: " + subject);
			}
			conn = getConnection();
			stmt = conn.prepareStatement(select.toString());
			// System.out.println(select.toString());
			stmt.setLong(1, s);
			if (version != null)
			{
				Timestamp t = new Timestamp(version.getTime());
				stmt.setTimestamp(2, t);
				stmt.setTimestamp(3, t);
			}
			rs = stmt.executeQuery();
			// OWLDataFactory factory = OWL.dataFactory();
			result = new HashMap<OWLDataPropertyExpression, Set<OWLLiteral>>();
			while (rs.next())
			{
				OWLDataProperty predicate = df.getOWLDataProperty(IRI.create(rs
						.getString(1)));
				String datatypeIRI = rs.getString("DATATYPE_IRI");
				// String lang = rs.getString("LANG");
				OWL2Datatype datatype = OWL2Datatype.getDatatype(IRI
						.create(datatypeIRI));
				OWLLiteral literal = literal(df, rs, datatype);
				Set<OWLLiteral> predicateLiterals = result.get(predicate);
				if (predicateLiterals == null)
				{
					predicateLiterals = new HashSet<OWLLiteral>();
					result.put(predicate, predicateLiterals);
				}
				predicateLiterals.add(literal);
			}
			conn.commit();
		} 
		catch (SQLException e)
		{
			rollback(conn);
			throw new RuntimeException(e);
		} 
		finally
		{
			close(rs, stmt, conn);
		}
		return result;
	}

	public Set<OWLClass> selectClass(OWLIndividual subject,
			                         Map<OWLEntity, DbId> identifiers, 
			                         OWLDataFactory df)
	{
		PreparedStatement stmt = null;
		Connection conn = null;
		ResultSet rs = null;
		Set<OWLClass> result = null;
		StringBuffer select = new StringBuffer();
		select.append("SELECT  B.IRI as CLASSIRI FROM ")
				.append(TABLE_CLASSIFICATION).append(" A,").append(TABLE_IRI)
				.append(" B").append(" WHERE ").append("SUBJECT =")
				// .append("? AND A.OWLCLASS = B.ID AND TO_DATE IS NULL ORDER BY A.OWLCLASS");
				.append("? AND A.OWLCLASS = B.ID AND TO_DATE IS NULL ");
		try
		{
			Long s = identifiers.get(subject.asOWLNamedIndividual()).getFirst();
			if (s == null)
			{
				if (DBG_NO_CLASSIFICATION) ThreadLocalStopwatch.getWatch().time("RelationalStoreImpl::selectClass null identifier for " + subject + " empty set returned");
				return Collections.emptySet();
			}
			conn = getConnection();
			stmt = conn.prepareStatement(select.toString());
			stmt.setLong(1, s);
			rs = stmt.executeQuery();
			// OWLDataFactory factory = OWL.dataFactory();
			result = new HashSet<OWLClass>();
			while (rs.next())
			{
				OWLClass cl = df.getOWLClass(IRI.create(rs
						.getString("CLASSIRI")));
				result.add(cl);
			}
			conn.commit();
		} 
		catch (SQLException e)
		{
			rollback(conn);
			throw new RuntimeException(e);
		} 
		finally
		{
			close(rs, stmt, conn);
		}
		return result;
	}

	// -------------------------------------------------------------------------
	// SELECT FROM VERTICAL SCHEMA HISTORY METHODS
	//

	public Map<OWLObjectPropertyExpression, Set<OWLIndividual>> selectObjectProperties(
			OWLIndividual subject, Date version,
			Map<OWLEntity, DbId> identifiers, OWLDataFactory df)
	{
		PreparedStatement stmt = null;
		Connection conn = null;
		ResultSet rs = null;
		Map<OWLObjectPropertyExpression, Set<OWLIndividual>> result = null;
		StringBuffer select = new StringBuffer();
		select.append("SELECT  B.IRI as PREDICATE, C.IRI as OBJECT FROM ")
				.append(TABLE_OBJECT_PROPERTY).append(" A, ").append(TABLE_IRI)
				.append(" B, ").append(TABLE_IRI).append(" C ")
				.append(" WHERE ")
				.append("SUBJECT = ? ")
				.append("AND A.FROM_DATE <= ? ")
				// hilpoldQ could this return two rows? if [t1 <= t <= t2]
				.append("AND (A.TO_DATE >= ? or A.TO_DATE is null) ")
				.append("AND A.PREDICATE = B.ID ")
				.append("AND A.OBJECT = C.ID");
		try
		{
			Long s = identifiers.get(subject.asOWLNamedIndividual()).getFirst();
			conn = getConnection();
			stmt = conn.prepareStatement(select.toString());
			stmt.setLong(1, s);
			Timestamp t = new Timestamp(version.getTime());
			stmt.setTimestamp(2, t);
			stmt.setTimestamp(3, t);
			rs = stmt.executeQuery();
			// OWLDataFactory factory = OWL.dataFactory();
			result = new HashMap<OWLObjectPropertyExpression, Set<OWLIndividual>>();
			while (rs.next())
			{
				OWLObjectProperty predicate = df.getOWLObjectProperty(IRI
						.create(rs.getString("PREDICATE")));
				OWLIndividual object = df.getOWLNamedIndividual(IRI.create(rs
						.getString("OBJECT")));
				if (result.containsKey(predicate))
					result.get(predicate).add(object);
				else
				{
					Set<OWLIndividual> v = new HashSet<OWLIndividual>();
					v.add(object);
					result.put(predicate, v);
				}
			}
			conn.commit();
		} catch (SQLException e)
		{
			rollback(conn);
			throw new RuntimeException(e);
		} finally
		{
			close(rs, stmt, conn);
		}
		return result;
	}

	public Set<OWLClass> selectClass(OWLIndividual subject, Date version,
			Map<OWLEntity, DbId> identifiers, OWLDataFactory df)
	{
		PreparedStatement stmt = null;
		Connection conn = null;
		ResultSet rs = null;
		Set<OWLClass> result = null;
		StringBuffer select = new StringBuffer();
		select.append("SELECT B.IRI as CLASSIRI ").append("FROM ")
				.append(TABLE_CLASSIFICATION).append(" A, ").append(TABLE_IRI)
				.append(" B ").append("WHERE ").append("SUBJECT = ? ")
				.append("AND A.FROM_DATE <= ? ")
				.append("AND (A.TO_DATE >= ? or A.TO_DATE is null) ")
				.append("AND A.OWLCLASS = B.ID");
		try
		{
			Long s = identifiers.get(subject.asOWLNamedIndividual()).getFirst();
			if (s == null)
			{
				if (DBG_NO_CLASSIFICATION) ThreadLocalStopwatch.getWatch().time("RelationalStoreImpl::selectClassV null identifier for " + subject + " empty set returned");
				return Collections.emptySet();
			}
			conn = getConnection();
			stmt = conn.prepareStatement(select.toString());
			stmt.setLong(1, s);
			Timestamp t = new Timestamp(version.getTime());
			stmt.setTimestamp(2, t);
			stmt.setTimestamp(3, t);
			rs = stmt.executeQuery();
			// OWLDataFactory factory = OWL.dataFactory();
			result = new HashSet<OWLClass>();
			while (rs.next())
			{
				OWLClass cl = df.getOWLClass(IRI.create(rs
						.getString("CLASSIRI")));
				result.add(cl);
			}
			conn.commit();
		} catch (SQLException e)
		{
			rollback(conn);
			throw new RuntimeException(e);
		} finally
		{
			close(rs, stmt, conn);
		}
		return result;
	}

	/**
	 * Searches all 3 tables - CIRM_OWL_DATA_PROPERTY, CIRM_OWL_OBJECT_PROPERTY,
	 * CIRM_CLASSIFICATION and returns the list of unique FROM_DATEs for a given
	 * individual.
	 */
	public List<Date> selectIndividualHistory(long subject)
	{
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;

		StringBuilder select = new StringBuilder();
		select.append("select distinct(FROM_DATE) from CIRM_OWL_DATA_PROPERTY where SUBJECT = ? union "
				+ "select distinct(FROM_DATE) from CIRM_OWL_OBJECT_PROPERTY where SUBJECT = ? union "
				+ "select distinct(FROM_DATE) from CIRM_CLASSIFICATION where SUBJECT = ? ");
		List<Date> dates = new ArrayList<Date>();
		try
		{
			conn = getConnection();
			stmt = conn.prepareStatement(select.toString());
			stmt.setLong(1, subject);
			stmt.setLong(2, subject);
			stmt.setLong(3, subject);
			rs = stmt.executeQuery();
			while (rs.next())
			{
				dates.add(rs.getTimestamp(1));
			}
			conn.commit();
		} catch (SQLException e)
		{
			rollback(conn);
			throw new RuntimeException(e);
		} finally
		{
			close(rs, stmt, conn);
		}
		return dates;
	}

	public List<Long> getServiceRequestList()
	{
		List<Long> result = new ArrayList<Long>();
		PreparedStatement stmt = null;
		Connection conn = null;
		ResultSet rs = null;
		StringBuilder select = new StringBuilder(
				"SELECT SR_REQUEST_ID FROM CIRM_SR_REQUESTS");
		try
		{
			conn = getConnection();
			stmt = conn.prepareStatement(select.toString());
			rs = stmt.executeQuery();
			while (rs.next())
			{
				result.add(rs.getLong(1));
			}
			conn.commit();
		} catch (Exception e)
		{
			rollback(conn);
			throw new RuntimeException(e);
		} finally
		{
			close(rs, stmt, conn);
		}
		return result;
	}

	// -------------------------------------------------------------------------
	// DELETE FROM VERTICAL SCHEMA
	//
	/**
	 * Expires the given OWLClassAssertionAxioms without removing their history.
	 * 
	 * @param set
	 * @param individuals
	 * @param identifiers
	 * @throws SQLException
	 */
	public void deleteClassification(Set<OWLClassAssertionAxiom> set,
			Set<OWLNamedIndividual> individuals,
			Map<OWLEntity, DbId> identifiers) throws SQLException
	{
		// TODO hilpold: set is not used????
		PreparedStatement stmt = null;
		Connection conn = null;
		ResultSet rs = null;
		StringBuffer update = new StringBuffer();
		update.append("UPDATE ").append(TABLE_CLASSIFICATION)
				.append(" SET TO_DATE= ? ")
				.append("WHERE SUBJECT = ? AND TO_DATE IS NULL");
		try
		{
			conn = getConnection();
			Timestamp now = new Timestamp(getStoreTimeInt().getTime());
			stmt = conn.prepareStatement(update.toString());
			for (OWLNamedIndividual i : individuals)
			{
				Long s = identifiers.get(i).getFirst();
				stmt.setTimestamp(1, now);
				stmt.setLong(2, s);
				stmt.addBatch();
			}
			stmt.executeBatch();
			conn.commit();
		} catch (SQLException e)
		{
			rollback(conn);
			throw e;
		} finally
		{
			close(rs, stmt, conn);
		}
	}

	public void deleteClassificationWithHistory(
			Set<OWLClassAssertionAxiom> axioms, Map<OWLEntity, Long> identifiers)
			throws SQLException
	{
		PreparedStatement stmt = null;
		Connection conn = null;
		ResultSet rs = null;
		StringBuffer deleteH = new StringBuffer();
		deleteH.append("DELETE FROM ").append(TABLE_CLASSIFICATION)
				.append(" WHERE SUBJECT = ? ");
		try
		{
			conn = getConnection();
			stmt = conn.prepareStatement(deleteH.toString());
			for (OWLClassAssertionAxiom axiom : axioms)
			{
				OWLNamedIndividual subject = axiom.getIndividual()
						.asOWLNamedIndividual();
				Long subjectId = identifiers.get(subject);
				if (subjectId == null)
					throw new IllegalArgumentException(
							"Subject from ClassAssertionAxiom " + axiom
									+ " was not found in identifiers (size: "
									+ identifiers.size());
				stmt.setLong(1, subjectId);
				stmt.addBatch();
			}
			stmt.executeBatch();
			conn.commit();
		} catch (SQLException e)
		{
			rollback(conn);
			throw e;
		} finally
		{
			close(rs, stmt, conn);
		}
	}

	/**
	 * Expires the given OWLDataPropertyAssertionAxiom without removing their
	 * history.
	 * 
	 * @param axioms
	 * @param individuals
	 * @param identifiers
	 * @throws SQLException
	 */
	public void deleteDataProperties(Set<OWLDataPropertyAssertionAxiom> axioms,
			Set<OWLNamedIndividual> individuals,
			Map<OWLEntity, DbId> identifiers) throws SQLException
	{
		PreparedStatement stmt = null;
		Connection conn = null;
		StringBuffer update = new StringBuffer();
		update.append("UPDATE ").append(TABLE_DATA_PROPERTY)
				.append(" SET TO_DATE= ? ")
				.append("WHERE SUBJECT = ? AND TO_DATE IS NULL");
		try
		{
			conn = getConnection();
			Timestamp now = new Timestamp(getStoreTimeInt().getTime());
			stmt = conn.prepareStatement(update.toString());
			for (OWLNamedIndividual i : individuals)
			{
				Long s = identifiers.get(i).getFirst();
				stmt.setTimestamp(1, now);
				stmt.setLong(2, s);
				stmt.addBatch();
			}
			stmt.executeBatch();
			conn.commit();
		} catch (SQLException e)
		{
			rollback(conn);
			throw e;
		} finally
		{
			close(stmt, conn);
		}
	}

	public void deleteDataPropertiesWithHistory(
			Set<OWLDataPropertyAssertionAxiom> axioms,
			Map<OWLEntity, Long> identifiers) throws SQLException
	{
		PreparedStatement stmt = null;
		Connection conn = null;
		ResultSet rs = null;
		StringBuffer deleteH = new StringBuffer();
		deleteH.append("DELETE FROM ").append(TABLE_DATA_PROPERTY)
				.append(" WHERE SUBJECT = ? ");
		try
		{
			conn = getConnection();
			stmt = conn.prepareStatement(deleteH.toString());
			for (OWLDataPropertyAssertionAxiom axiom : axioms)
			{
				OWLNamedIndividual subject = axiom.getSubject()
						.asOWLNamedIndividual();
				// get allrows for subject
				Long subjectId = identifiers.get(subject);
				if (subjectId == null)
					throw new IllegalArgumentException(
							"Subject from OWLDataPropertyAssertionAxiom "
									+ axiom
									+ " was not found in identifiers (size: "
									+ identifiers.size());
				stmt.setLong(1, subjectId);
				stmt.addBatch();
			}
			stmt.executeBatch();
			conn.commit();
		} catch (SQLException e)
		{
			rollback(conn);
			throw e;
		} finally
		{
			close(rs, stmt, conn);
		}
	}

	/**
	 * Expires the given OWLObjectPropertyAssertionAxiom without removing their
	 * history.
	 * 
	 * @param axioms
	 * @param individuals
	 * @param identifiers
	 * @throws SQLException
	 */
	public void deleteObjectProperties(
			Set<OWLObjectPropertyAssertionAxiom> axioms,
			Set<OWLNamedIndividual> individuals,
			Map<OWLEntity, DbId> identifiers) throws SQLException
	{
		PreparedStatement stmt = null;
		ResultSet rs = null;
		Connection conn = getConnection();
		// sets all active object properties as expired, including those outside
		// the map.
		StringBuffer update = new StringBuffer();
		update.append("UPDATE ").append(TABLE_OBJECT_PROPERTY)
				.append(" SET TO_DATE= ? ")
				.append("WHERE SUBJECT = ? AND TO_DATE IS NULL");
		try
		{
			Timestamp now = new Timestamp(getStoreTimeInt().getTime());
			stmt = conn.prepareStatement(update.toString());
			for (OWLNamedIndividual i : individuals)
			{
				Long s = identifiers.get(i).getFirst();
				stmt.setTimestamp(1, now);
				stmt.setLong(2, s);
				stmt.addBatch();
			}
			stmt.executeBatch();
		} 
		catch (SQLException e)
		{
			rollback(conn);
			throw e;
		} 
		finally
		{
			close(rs, stmt, conn);
		}
	}

	public void deleteObjectPropertiesWithHistory(
			Set<OWLObjectPropertyAssertionAxiom> axioms,
			Map<OWLEntity, Long> identifiers) throws SQLException
	{
		PreparedStatement stmt = null;
		Connection conn = null;
		ResultSet rs = null;
		StringBuffer deleteH = new StringBuffer();
		deleteH.append("DELETE FROM ").append(TABLE_OBJECT_PROPERTY)
				.append(" WHERE SUBJECT = ? ");
		try
		{
			conn = getConnection();
			stmt = conn.prepareStatement(deleteH.toString());
			for (OWLObjectPropertyAssertionAxiom axiom : axioms)
			{
				OWLNamedIndividual subject = axiom.getSubject()
						.asOWLNamedIndividual();
				// get allrows for subject
				Long subjectId = identifiers.get(subject);
				if (subjectId == null)
					throw new IllegalArgumentException(
							"Subject from OWLObjectPropertyAssertionAxiom "
									+ axiom
									+ " was not found in identifiers (size: "
									+ identifiers.size());
				stmt.setLong(1, subjectId);
				stmt.addBatch();
			}
			stmt.executeBatch();
			conn.commit();
		} catch (SQLException e)
		{
			rollback(conn);
			throw e;
		} finally
		{
			close(rs, stmt, conn);
		}

	}

	// @Deprecated
	// public Set<OWLNamedIndividual>
	// deleteMappedOntologyIndividuals(OWLOntology ontology,
	// Map<OWLEntity, Long> identifiers)
	// {
	// Connection conn = getConnection();
	// try
	// {
	// Set<OWLNamedIndividual> result =
	// deleteMappedOntologyIndividuals(ontology, identifiers, conn);
	// conn.commit();
	// return result;
	// }
	// catch (SQLException e)
	// {
	// try
	// {
	// conn.rollback();
	// }
	// catch (SQLException e1)
	// {
	// e1.printStackTrace();
	// }
	// throw new RuntimeException(e);
	// }
	// finally
	// {
	// close(conn);
	// }
	// }

	/**
	 * DELETE MAPPED. This is currently not working because of foreign key
	 * constraints. The individuals need at least be ordered by type.
	 * 
	 * @param ontology
	 * @param identifiers
	 * @return
	 * @throws SQLException
	 */
	public Set<OWLNamedIndividual> deleteMappedOntologyIndividuals(
			OWLOntology ontology, Map<OWLEntity, DbId> identifiers)
			throws SQLException
	{
		Connection conn = getConnection();
		Set<OWLNamedIndividual> done = new HashSet<OWLNamedIndividual>();
		List<Statement> statements = new ArrayList<Statement>(
				ontology.getAxiomCount());
		for (OWLNamedIndividual i : ontology.getIndividualsInSignature())
		{
			if (!done.contains(i)
					&& RelationalOWLMapper.isMapped(i.getTypes(ontology)))
			{
				addDeleteStatementsForMappedIndividualRecursive(statements,
						ontology, i, identifiers, done);
			}
		}
		try
		{
			execute(statements, identifiers, conn);
			conn.commit();
		} catch (SQLException e)
		{
			rollback(conn);
			throw e;
		} finally
		{
			close(conn);
		}
		return done;
	}

	/**
	 * Will be called recursively.
	 * 
	 * @param statements
	 *            a list to add delete statements to.
	 * @param o
	 * @param ind
	 * @param identifiers
	 * @param done
	 */
	private void addDeleteStatementsForMappedIndividualRecursive(
			List<Statement> statements, OWLOntology o, OWLIndividual ind,
			Map<OWLEntity, DbId> identifiers, Set<OWLNamedIndividual> done)
	{
		Statement s = new Statement();
		OWLNamedIndividual table = RelationalOWLMapper.table(ind.getTypes(o));
		if (table != null)
		{
			Sql sql = DELETE_FROM(table.getIRI().getFragment());
			// add mapped properties
			Set<OWLNamedIndividual> columnIRI = RelationalOWLMapper
					.columnIriPK(table);
			// add iri value
			OWLNamedIndividual column = null;
			if (!columnIRI.isEmpty())
			{
				column = columnIRI.iterator().next();
				sql.WHERE(column.getIRI().getFragment()).EQUALS("?");
				s.getParameters().add(identifiers.get(ind));
				Set<OWLIndividual> columnType = column.getObjectPropertyValues(
						objectProperty(fullIri(Concepts.hasColumnType)),
						ontology());
				s.getTypes().add(
						columnType.iterator().next().asOWLNamedIndividual());
			}
			Map<OWLObjectPropertyExpression, Set<OWLIndividual>> objectPropertyValues = ind
					.getObjectPropertyValues(o);
			// hasMany
			Map<OWLObjectProperty, OWLClass> hasMany = RelationalOWLMapper
					.hasMany(objectPropertyValues.keySet());
			for (OWLObjectProperty mappedProperty : hasMany.keySet())
			{
				OWLClass hasManyMappedClass = hasMany.get(mappedProperty);
				boolean deleted = false;
				for (OWLIndividual prop : objectPropertyValues
						.get(mappedProperty))
				{

					OWLNamedIndividual manyTable = RelationalOWLMapper
							.table(hasManyMappedClass);
					OWLNamedIndividual joinTable = RelationalOWLMapper.join(
							table, manyTable);
					OWLNamedIndividual manyObject = prop.asOWLNamedIndividual();
					if (joinTable == null)
						continue;
					boolean manyToMany = !joinTable.equals(manyTable);
					if (manyToMany)
					{
						OWLNamedIndividual joinColumnIRI = RelationalOWLMapper
								.foreignKeyByjoinColumnAndTable(column,
										joinTable);
						// delete from junction table
						if (!deleted)
						{
							Statement delete = new Statement();
							delete.setSql(DELETE_FROM(
									joinTable.getIRI().getFragment()).WHERE(
									joinColumnIRI.getIRI().getFragment())
									.EQUALS("?"));
							delete.getParameters().add(identifiers.get(ind));
							Set<OWLIndividual> columnType = column
									.getObjectPropertyValues(
											objectProperty(fullIri(Concepts.hasColumnType)),
											ontology());
							delete.getTypes().add(
									columnType.iterator().next()
											.asOWLNamedIndividual());
							statements.add(delete);
							deleted = true;
						}
						// Recursive!
						addDeleteStatementsForMappedIndividualRecursive(
								statements, o, manyObject, identifiers, done);
					} else
					{
						// TODO : many-to-one
					}
				}
			}
			s.setSql(sql);
			statements.add(s);
			for (Map.Entry<OWLObjectPropertyExpression, Set<OWLIndividual>> mappedProperty : objectPropertyValues
					.entrySet())
			{
				OWLObjectPropertyExpression ex = mappedProperty.getKey();
				if (ex instanceof OWLObjectProperty)
				{
					OWLObjectProperty prop = (OWLObjectProperty) ex;
					Set<OWLIndividual> set = objectPropertyValues.get(prop);
					if (set.size() > 0)
					{
						OWLNamedIndividual entity = set.iterator().next()
								.asOWLNamedIndividual();
						addDeleteStatementsForMappedIndividualRecursive(
								statements, o, entity, identifiers, done);
					}
				}
			}
		}
	}

	//
	// Helper for delete only.
	//
	protected void execute(List<Statement> statements,
	                       Map<OWLEntity, DbId> identifiers, 
	                       Connection conn)
			throws SQLException
	{
		PreparedStatement stmt = null;
		for (Statement s : statements)
		{
			stmt = prepareStatement(conn, s, identifiers);
			try
			{
				stmt.executeUpdate();
			} 
			catch (SQLException e)
			{
				if (!canRetrySQL(e)) 
				{
					System.err.println("Error executing update: " + e.toString());
					printStatement(s);
					System.err.println("Other Statements: ");
					int i = 0;
					for (Statement x : statements)
					{
						System.err.print("Statements: " + i);
						if (x.equals(s)) 
						{
							System.err.print("This is the failing statement: " + i);
						}
						printStatement(x);
						i ++;
					}
				}
				throw e;
			} 
			finally
			{
				close(stmt);
			}
		}
	}
	
	void printStatement(Statement s) 
	{
		String sql = s.getSql().SQL();
		System.out.println(sql);
		int i = 0;
		for (Object param : s.getParameters())
		{
			System.out.print("" + i + ": " + param + "|");
			i++;
		}
		System.out.println();
		i = 0;
		for (OWLNamedIndividual type : s.getTypes())
		{
			System.out.print("" + i + ": " + type.getIRI().getFragment() + "|");
			i++;
		}
		System.out.println();
	}

	//
	// public long selectIRICount(Connection conn) {
	// ResultSet rs = null;
	// java.sql.Statement stmt = null;
	// try {
	// stmt = conn.createStatement();
	// rs = stmt.executeQuery("SELECT COUNT(*) FROM " + VIEW_IRI);
	// rs.next();
	// return rs.getLong(1);
	// } catch (SQLException e) {
	// throw new RuntimeException(e);
	// } finally {
	// close(rs, stmt);
	// }
	//
	//
	// }

	/* (non-Javadoc)
	 * @see org.sharegov.cirm.rdb.RelationalStore#insertIri(long, java.lang.String, java.lang.String, java.sql.Connection)
	 */
	//@Override
	public void insertIri(long n, String iri, String type, Connection conn)
			throws SQLException
	{
		PreparedStatement stmt = null;
		// Connection conn = null;

		StringBuffer insert = new StringBuffer();
		insert.append("INSERT INTO ").append(TABLE_IRI).append("(")
				.append("ID").append(",IRI").append(", IRI_TYPE_ID")
				.append(")").append("VALUES").append("(").append("?,?,?")
				.append(")");
		try
		{
			Map<String, Long> iriTypes = selectIriTypesCached();
			stmt = conn.prepareStatement(insert.toString());
			stmt.setLong(1, n);
			stmt.setString(2, iri);
			stmt.setLong(3, iriTypes.get(type));
			stmt.execute();
		} catch (SQLException e)
		{
			throw e;
		} finally
		{
			close(stmt);
		}
	}

	/* (non-Javadoc)
	 * @see org.sharegov.cirm.rdb.RelationalStore#updateIri(long, java.lang.String, java.sql.Connection)
	 */
	//Override
	public void updateIri(long id, String iri, Connection conn)
			throws SQLException
	{
		PreparedStatement stmt = null;
		if (iri == null)
			throw new NullPointerException("new iri null for id: " + id);
		StringBuffer update = new StringBuffer();
		update.append("UPDATE ").append(TABLE_IRI).append(" SET IRI = ?")
				.append(" WHERE ID = ?");
		try
		{
			stmt = conn.prepareStatement(update.toString());
			stmt.setString(1, iri);
			stmt.setLong(2, id);
			stmt.execute();
		} catch (SQLException e)
		{
			throw e;
		} finally
		{
			close(stmt);
		}
	}

	public Long selectIDByEntityType(EntityType<? extends OWLEntity> et)
	{
		return selectIriTypesCached().get(et.getName());
	}

	/**
	 * Select the iri type from instance variable, if null looks up iri types
	 * from RDB.
	 * 
	 * @return
	 */
	public Map<String, Long> selectIriTypesCached()
	{
		if (iriTypes == null)
		{
			synchronized (this)
			{
				if (iriTypes == null)
					iriTypes = selectIriTypes();
			}
		}
		return iriTypes;
	}

	public synchronized Map<String, Long> selectIriTypes()
	{
		boolean shouldRepeat = false;
		Map<String, Long> result = null;
		do
		{
			Connection conn = getConnection();
			try
			{
				result = selectIriTypesInternal(conn);
				if (result.size() < EntityType.values().size())
				{
					insertIriTypes(conn);
					conn.commit();
					shouldRepeat = false;
				}
				result = selectIriTypesInternal(conn);
				if (result.size() < EntityType.values().size())
				{
					throw new IllegalStateException(
							"Failed to insert Iri types into RDB.");
				}
				conn.commit();
			} catch (SQLException e)
			{
				rollback(conn);
				if (canRetrySQL(e))
					shouldRepeat = true;
				else
					throw new RuntimeException(e);
			} finally
			{
				close(conn);
			}
		} while (shouldRepeat);
		return result;
	}

	private Map<String, Long> selectIriTypesInternal(Connection conn)
			throws SQLException
	{

		Map<String, Long> result = new ConcurrentHashMap<String, Long>();
		PreparedStatement stmt = null;
		ResultSet rs = null;
		StringBuffer select = new StringBuffer(100);
		select.append("SELECT ID, IRI FROM ").append(TABLE_IRI_TYPE);
		try
		{
			stmt = conn.prepareStatement(select.toString());
			rs = stmt.executeQuery();
			while (rs.next())
			{
				result.put(rs.getString("IRI"), rs.getLong("ID"));
			}
		} catch (SQLException e)
		{
			throw e;
		} finally
		{
			close(rs, stmt);
		}
		return result;
	}

	private void insertIriTypes(Connection conn) throws SQLException
	{
		PreparedStatement stmt = null;
		StringBuffer insert = new StringBuffer();
		insert.append("INSERT INTO ").append(TABLE_IRI_TYPE).append("(")
				.append("ID").append(",IRI").append(")").append("VALUES")
				.append("(").append("?,?").append(")");

		try
		{
			stmt = conn.prepareStatement(insert.toString());
			stmt.setLong(1, 1);
			stmt.setString(2, EntityType.CLASS.getName());
			stmt.addBatch();
			stmt.setLong(1, 2);
			stmt.setString(2, EntityType.DATA_PROPERTY.getName());
			stmt.addBatch();
			stmt.setLong(1, 3);
			stmt.setString(2, EntityType.DATATYPE.getName());
			stmt.addBatch();
			stmt.setLong(1, 4);
			stmt.setString(2, EntityType.NAMED_INDIVIDUAL.getName());
			stmt.addBatch();
			stmt.setLong(1, 5);
			stmt.setString(2, EntityType.OBJECT_PROPERTY.getName());
			stmt.addBatch();
			stmt.setLong(1, 6);
			stmt.setString(2, EntityType.ANNOTATION_PROPERTY.getName());
			stmt.addBatch();
			stmt.executeBatch();
		} catch (SQLException e)
		{
			throw e;
		} finally
		{
			close(stmt);
		}

	}

	// -------------------------------------------------------------------------
	// VERTICAL SCHEMA AND MAPPED SCHEMA METHODS
	//

	/**
	 * Returns a set with all individuals for whom a mapping exists. The
	 * ontology is used. This method should be part of RelationalOWLMapper.
	 */
	private Set<OWLNamedIndividual> getMappedIndividualsFrom(
			final OWLOntology ontology,
			final Set<OWLNamedIndividual> individuals)
	{
		Set<OWLNamedIndividual> result = new HashSet<OWLNamedIndividual>(
				individuals.size());
		for (OWLNamedIndividual curIndividual : individuals)
		{
			if (RelationalOWLMapper.isMapped(curIndividual.getTypes(ontology)))
			{
				result.add(curIndividual);
			}
		}
		return result;
	}

	/**
	 * Merge ontology into DB.
	 * 
	 * @param o
	 * @param ind
	 * @param identifiers
	 */
	public void merge(OWLOntology ontology, DbId boObj)
	{
		if (dbg())
		{
			ThreadLocalStopwatch.getWatch().reset(
					"Start merge ontology "
							+ ontology.getOntologyID().getOntologyIRI());
			// System.out.println("MERGING ONTOLOGY: " + ontology);
			// DBGUtils.printOntologyFunctional(ontology);
		}
		// 0. Save time
		Timestamp time = new Timestamp(getStoreTimeInt().getTime());
		// 1. Get all IDs, insert if missing
		Map<OWLEntity, DbId> identifiers = selectIDsAndEntitiesByIRIs(ontology, boObj, true);
		if (dbg())
			ThreadLocalStopwatch.getWatch().time("Done get IRIs ");
		// 2. Determine mapped and unmapped individuals
		Set<OWLNamedIndividual> allIndividuals = ontology.getIndividualsInSignature();
		Set<OWLNamedIndividual> mappedIndividuals = getMappedIndividualsFrom(ontology, allIndividuals);
		Set<OWLNamedIndividual> unmappedIndividuals = new HashSet<OWLNamedIndividual>(allIndividuals);
		unmappedIndividuals.removeAll(mappedIndividuals);
		// 3. Get and use only axioms, that are not mapped in the mapped schema.
		Set<OWLDataPropertyAssertionAxiom> notMappedDataPropertyAssertionAxioms = getNotMappedDataPropertyAxioms(ontology);
		Set<OWLObjectPropertyAssertionAxiom> notMappedObjectPropertyAssertionAxioms = getNotMappedObjectPropertyAxioms(ontology);
		// 4. All class assertions will be stored (including for mapped classes)
		Set<OWLClassAssertionAxiom> allClassAssertionAxioms = ontology.getAxioms(AxiomType.CLASS_ASSERTION);
		// ensure all non mapped literals inserted
		// Map<String, Long> literalHashToIDsNotMapped =
		// selectLiterals(notMappedDataPropertyAssertionAxioms, true);
		Map<Object, Long> literalValueToIDsNotMapped = selectLiteralValueIDsFromAxioms(notMappedDataPropertyAssertionAxioms, true);// Literals(notMappedDataPropertyAssertionAxioms,
															// true);
		if (dbg())
			ThreadLocalStopwatch.getWatch().time("Done get Literal IDs ");
		// TABLE: This affexets CIRM_OWL_DATA_VALU
		// System.out.println(new BOntology(ontology).toJSON());
		Connection conn = null;
		try
		{
			conn = getConnection();
			// conn.setAutoCommit(false);
			// MERGE VERTICAL SCHEMA (Should be unmapped individuals, object and
			// dataproperties only)
			Map<List<Statement>, List<Statement>> mergeStatements = new LinkedHashMap<List<Statement>, List<Statement>>();
			mergeStatements.putAll(mergeClassAssertions(allClassAssertionAxioms, allIndividuals, identifiers, time));
			// mergeStatements.putAll(mergeClassAssertions(ontology.getAxioms(AxiomType.CLASS_ASSERTION),
			// allIndividuals, identifiers));
			mergeStatements.putAll(mergeDataProperties(notMappedDataPropertyAssertionAxioms, 
			                                           allIndividuals,
					                                   identifiers, 
					                                   literalValueToIDsNotMapped, 
					                                   time));
			mergeStatements.putAll(mergeObjectProperties(notMappedObjectPropertyAssertionAxioms, 
			                                             allIndividuals,
			                                             identifiers, 
			                                             time));
			// triple store merge
			for (Map.Entry<List<Statement>, List<Statement>> merge : mergeStatements.entrySet())
			{

				List<Statement> updates = merge.getKey();
				List<Statement> inserts = merge.getValue();
				if (!updates.isEmpty())
				{
					executeBatch(updates, identifiers, conn);
				}
				if (!inserts.isEmpty())
				{
					executeBatch(inserts, identifiers, conn);
				}
			}
			if (dbg())
				ThreadLocalStopwatch.getWatch().time("Done batch statements ");
			//
			// mapped tables merge
			// Merge mapped will use only axioms, for which a column mapping
			// exists.
			// the class assertion axioms for mapped individuals don't need to
			// be stores,
			// as classes for mapped individuals can be derived from table and
			// county ontology.
			for (Map.Entry<OWLNamedIndividual, List<Statement>> entry : mergeMappedIndividuals(
					ontology, identifiers).entrySet())
			{
				if (dbg())
					logger.info("executing Statements for individual:"
							+ entry.getKey());
				execute(entry.getValue(), identifiers, conn);
			}
			if (dbg())
				ThreadLocalStopwatch.getWatch().time("Done mapped statements ");
			conn.commit();

		} catch (SQLException e)
		{
			rollback(conn);
			throw new RuntimeException(e);
		} finally
		{
			close(conn);
			if (dbg())
			{
				ThreadLocalStopwatch.getWatch().time(
						"End merge "
								+ ontology.getOntologyID().getOntologyIRI());
			}
		}
	}

	/**
	 * Merge of all mapped individuals. hilpold this is the only method
	 * preparing mapped merge.
	 * 
	 * @param o
	 * @param identifiers
	 */
	private Map<OWLNamedIndividual, List<Statement>> mergeMappedIndividuals(
			OWLOntology ontology, Map<OWLEntity, DbId> identifiers)
	{

		Map<OWLNamedIndividual, List<Statement>> done = new LinkedHashMap<OWLNamedIndividual, List<Statement>>();
		for (OWLNamedIndividual curIndividual : ontology.getIndividualsInSignature())
		{
			if (!done.containsKey(curIndividual) && 
			    RelationalOWLMapper.isMapped(curIndividual.getTypes(ontology)))
			{
				mergeMappedIndividual(ontology, curIndividual, identifiers, null, done);
			}
		}
		return done;
	}

	// -------------------------------------------------------------------------
	// READ MAPPED INDIVIDUAL AND ALL REFERRED INDIVIDUALS
	//
	// -------------------------------------------------------------------------

	/**
	 * Reads an individual's and it's by objectproperty related individuals'
	 * data by reading the unmapped and -if mapped- the mapped schema and create
	 * axioms. It keeps track of already visited individuals for both schemas.
	 * 
	 * Call this within a transaction only.
	 */
	void readIndividualDataRecursive(OWLOntology on, OWLNamedIndividual ind)
	{
		Set<OWLNamedIndividual> doneNotMapped = new HashSet<OWLNamedIndividual>();
		Set<OWLNamedIndividual> doneMapped = new HashSet<OWLNamedIndividual>();
		readIndividualDataNotMappedRecursive(on, ind, doneNotMapped);
		readIndividualDataMappedRecursive(on, ind, doneMapped);
		// Now visit those who were found during notMapped, but not during
		// mapped and vice versa.
		boolean hasSymmetricDifference;
		do
		{
			Set<OWLNamedIndividual> doneNotMappedMinusMapped = new HashSet<OWLNamedIndividual>(
					doneNotMapped);
			Set<OWLNamedIndividual> doneMappedMinusNonMapped = new HashSet<OWLNamedIndividual>(
					doneMapped);
			doneNotMappedMinusMapped.removeAll(doneMapped);
			doneMappedMinusNonMapped.removeAll(doneNotMapped);
			hasSymmetricDifference = !(doneNotMappedMinusMapped.isEmpty() && doneMappedMinusNonMapped
					.isEmpty());
			if (dbg())
				System.out.println("DoneNotMappedMinusMapped = "
						+ doneNotMappedMinusMapped.size()
						+ "; DoneMappedMinusNonMapped = "
						+ doneMappedMinusNonMapped.size());
			// read all axioms for individuals found during mapped, but missed
			// during not mapped. (some properties might not be mapped)
			for (OWLNamedIndividual stillNeedsNotMapped : doneMappedMinusNonMapped)
			{
				if (!doneNotMapped.contains(stillNeedsNotMapped))
				{
					readIndividualDataNotMappedRecursive(on,
							stillNeedsNotMapped, doneNotMapped);
				}
			}
			// read all axioms for individuals found during notMapped, but
			// missed during mapped. (some properties might be mapped)
			for (OWLNamedIndividual stillNeedsMapped : doneNotMappedMinusMapped)
			{
				if (!doneMapped.contains(stillNeedsMapped))
				{
					readIndividualDataMappedRecursive(on, stillNeedsMapped,
							doneMapped);
				}
			}
		} while (hasSymmetricDifference);
	}

	/**
	 * Recursively reads all axioms from the not mapped schema for all
	 * individuals that can be reached starting with and including the given
	 * individiual. doneMapped will contain every individual that was visited or
	 * referenced during NON mapped traversal.
	 * 
	 * @param on
	 *            an ontology to add axioms too.
	 * @param ind
	 *            the start individual
	 * @param doneNotMapped
	 *            will contain all individuals that were visited or referenced
	 *            (!) after this method returns.
	 */
	void readIndividualDataNotMappedRecursive(OWLOntology on,
			OWLNamedIndividual ind, Set<OWLNamedIndividual> doneNotMapped)
	{
		Set<OWLNamedIndividual> referencedIndividuals = Collections.emptySet();
		if (!doneNotMapped.contains(ind))
		{
			doneNotMapped.add(ind);
			referencedIndividuals = readIndividualDataNotMapped(on, ind);
			for (OWLNamedIndividual needed : referencedIndividuals)
			{
				readIndividualDataNotMappedRecursive(on, needed, doneNotMapped);
			}
		}
	}

	/**
	 * Recursively reads all axioms from the mapped schema for all individuals
	 * that can be reached starting with and including the given individiual.
	 * 1:1, 1:*, *:* relationships will be resolved. doneMapped will contain
	 * every individual that was visited or referenced during mapped traversal.
	 * 
	 * @param on
	 * @param ind
	 * @param doneMapped
	 *            will contain all individuals that were visited or referenced
	 *            (!) after this method returns.
	 */
	void readIndividualDataMappedRecursive(OWLOntology on,
			OWLNamedIndividual ind, Set<OWLNamedIndividual> doneMapped)
	{
		Set<OWLNamedIndividual> referencedIndividuals = Collections.emptySet();
		if (!doneMapped.contains(ind))
		{
			doneMapped.add(ind);
			referencedIndividuals = readIndividualDataMapped(on, ind,
					doneMapped);
			for (OWLNamedIndividual needed : referencedIndividuals)
			{
				readIndividualDataMappedRecursive(on, needed, doneMapped);
			}
		}
	}

	/**
	 * Searches for all entities by the IRI of the given individual and adds
	 * OWLObjectPropertyAssertionAxioms, OWLDataPropertyAssertionAxioms,
	 * OWLClassAssertionAxiom the ontology with
	 * 
	 * @param on
	 *            an ontology to add axioms too.
	 * @param ind
	 * @return a set of dependent referenced individuals, which should be loaded
	 *         next or the empty set.
	 */
	Set<OWLNamedIndividual> readIndividualDataNotMapped(OWLOntology on, OWLNamedIndividual ind)
	{
		OWLDataFactory df = on.getOWLOntologyManager().getOWLDataFactory();
		// TODO hilpold THREAD SAFE DATA FACTORY needed if not onto exclusive
		// and not hgdb
		OWLOntologyManager manager = on.getOWLOntologyManager(); // OWL.manager();
		ArrayList<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
		Set<OWLNamedIndividual> referencedIndividuals = Collections.emptySet();
		// TRANSACTION START
		ThreadLocalConnection conn = getConnection();
		Set<OWLNamedIndividual> entities = Collections.singleton(ind);
		try
		{
			Map<OWLEntity, DbId> identifiers = selectIDsAndEntitiesByIRIs(entities);

			Map<OWLObjectPropertyExpression, Set<OWLIndividual>> objProps = selectObjectProperties(
					ind, identifiers, df);
			for (Map.Entry<OWLObjectPropertyExpression, Set<OWLIndividual>> e : objProps
					.entrySet())
			{
				for (OWLIndividual propValue : e.getValue())
				{
					changes.add(new AddAxiom(on, manager.getOWLDataFactory()
							.getOWLObjectPropertyAssertionAxiom(e.getKey(),
									ind, propValue)));
					if (referencedIndividuals.isEmpty())
					{
						referencedIndividuals = new HashSet<OWLNamedIndividual>();
					}
					referencedIndividuals.add((OWLNamedIndividual) propValue);
				}
			}

			Map<OWLDataPropertyExpression, Set<OWLLiteral>> dataProps = selectDataProperties(
					ind, identifiers, df);
			for (Map.Entry<OWLDataPropertyExpression, Set<OWLLiteral>> e : dataProps
					.entrySet())
			{
				for (OWLLiteral propValue : e.getValue())
					changes.add(new AddAxiom(on, manager.getOWLDataFactory()
							.getOWLDataPropertyAssertionAxiom(e.getKey(), ind,
									propValue)));
			}

			Set<OWLClass> classes = selectClass(ind, identifiers, df);
			for (OWLClass c : classes)
			{
				changes.add(new AddAxiom(on, manager.getOWLDataFactory()
						.getOWLClassAssertionAxiom(c, ind)));
			}
			manager.applyChanges(changes);
			conn.commit();
			return referencedIndividuals;
		} catch (Exception e)
		{
			rollback(conn);
			throw new RuntimeException(e);
		} finally
		{
			close(conn);
		}
	}

	/**
	 * Reads the given mapped named individual and resolves all 1:1, 1:* and *:*
	 * relationships in which the named individual takes part.
	 * 
	 * @param on
	 * @param ind
	 * @return a set of referenced individuals that have not been loaded yet or
	 *         the empty set.
	 */
	public Set<OWLNamedIndividual> readIndividualDataMapped(OWLOntology onto,
			OWLNamedIndividual ind, Set<OWLNamedIndividual> doneMapped)
	{
		Set<OWLNamedIndividual> referencedIndividuals = Collections.emptySet();
		OWLClass classFromIRI = findClassInLoadedOntologiesFor(ind);
		if (!RelationalOWLMapper.isMapped(classFromIRI))
		{
			if (dbg())
			{
				System.out
						.println("readIndividualData called with non mapped individual: "
								+ ind + "");
			}
			doneMapped.add(ind);
			return referencedIndividuals;
		}
		// Assert class here? No, already done in loadunmapped.
		// OWLClassAssertionAxiom clsAssAxiom =
		// factory.getOWLClassAssertionAxiom(classFromIRI, ind);
		// manager.applyChange(new AddAxiom(onto, clsAssAxiom));

		// Find Table and read as much as possible from row
		OWLNamedIndividual indTable = RelationalOWLMapper.table(classFromIRI);
		Set<OWLNamedIndividual> columnIRIPKs = RelationalOWLMapper
				.columnIriPK(indTable);
		OWLNamedIndividual primaryKeyColumn = columnIRIPKs.iterator().next();
		Map<OWLEntity, DbId> identifiers = selectIDsAndEntitiesByIRIs(ind);
		long primaryKeyValue = identifiers.get(ind).getFirst();
		Set<OWLNamedIndividual> referenced = readMappedIndividualSql(onto, ind,
				primaryKeyValue, classFromIRI, indTable, primaryKeyColumn);
		if (!referenced.isEmpty())
		{
			if (referencedIndividuals.isEmpty())
			{
				referencedIndividuals = new HashSet<OWLNamedIndividual>();
			}
			referencedIndividuals.addAll(referenced);
		}
		// onto now has all directly read axioms. referencedIndividuals need to
		// be dealt with by caller, who needs to make sure
		// that unmapped schema was already tried for those individuals.

		// TRAVERESE RELATIONSHIPS 1:1, 1:*, *:*.
		Set<Map<OWLNamedIndividual, OWLNamedIndividual>> joinTableToOtherTableMaps = RelationalOWLMapper
				.joinsByTable(indTable);
		if (joinTableToOtherTableMaps == null)
			joinTableToOtherTableMaps = Collections.emptySet();
		// for SR_Request: SR_ACTIVITY->SR_ACTIVITY, SRREQ_SRACTOR->SRACTOR, ??
		for (Map<OWLNamedIndividual, OWLNamedIndividual> joinTableToOtherTable : joinTableToOtherTableMaps)
		{
			// Assert map exactly one entry.
			Map.Entry<OWLNamedIndividual, OWLNamedIndividual> joinTableToOtherTableEntry = joinTableToOtherTable
					.entrySet().iterator().next();
			// 1) Get objectProperty for current relationship
			// 2) Distinguish 1:1, 1:* or *:*
			// 3) 1:1 -> load one row
			// 3b) 1:* -> ind is one, load many (property, ind, manyTable,
			// onto,)
			// 3c) *:* -> SELECT * (-<JOIN>.LEFT) FROM <JOIN>, <MANY> WHERE
			// <JOIN>.LEFT = indKey and
			// <JOIN>.RIGHT = <MANY>.PK
			// with those rows, call readMappedIndividualRow(Resultset)
			OWLNamedIndividual joinTable = joinTableToOtherTableEntry.getKey();
			OWLNamedIndividual otherTable = joinTableToOtherTableEntry
					.getValue();
			// e.g. hasServiceActivity -> (ServiceActivity, SR_ACTIVITY)
			// e.g. hasServiceCaseActor-> (ServiceCaseActor, SR_ACTOR)
			Set<OWLClass> classesForTable = RelationalOWLMapper
					.classesByTable(otherTable);
			int manyPropertiesFound = 0;
			for (OWLClass otherTableMappedClass : classesForTable)
			{
				// 2013.02.03 abbas domain and range restriction on hasMany
				// OWLObjectProperty manyProperty =
				// RelationalOWLMapper.hasManyByClass(otherTableMappedClass);
				Map<OWLObjectProperty, OWLClass> propertyAndDomain = RelationalOWLMapper
						.hasManyByRange(otherTableMappedClass);
				if (propertyAndDomain != null)
				{
					if (propertyAndDomain.size() > 1)
						throw new IllegalStateException(
								"Exception hasMany property and domain size exception. Only one property and domain can be defined for a hasMany relationship.");
					Map.Entry<OWLObjectProperty, OWLClass> entry = propertyAndDomain
							.entrySet().iterator().next();
					OWLObjectProperty manyProperty = entry.getKey();
					OWLClass domainClass = entry.getValue();
					// 1:1 or 1:* or *:*
					// add domainClass check by way of its mapped table
					if (manyProperty != null
							&& indTable.equals(RelationalOWLMapper
									.table(domainClass)))
					{
						manyPropertiesFound++;
						Set<OWLNamedIndividual> curReferenced = readMappedRelationshipSql(
								onto, ind, primaryKeyValue, primaryKeyColumn,
								manyProperty, joinTable, otherTable,
								otherTableMappedClass, doneMapped);
						if (referencedIndividuals.isEmpty()
								&& !curReferenced.isEmpty())
						{
							referencedIndividuals = new HashSet<OWLNamedIndividual>();
						}
						referencedIndividuals.addAll(curReferenced);
					}
				}
			}
			if (dbg())
			{
				System.out.println("Many Properties found for ind: " + indTable
						+ " joinT " + joinTable + " other " + otherTable
						+ " Classes " + manyPropertiesFound);
			}
		}
		return referencedIndividuals;
	}

	/**
	 * Adds all axioms to the ontology by loading individuals from all rows on
	 * the right hand side of a 1:1, 1:* or *:* relationship.
	 * 
	 * @param onto
	 * @param mappedIndividual
	 *            the left hand side of the relationship.
	 * @param primaryKeyValue
	 *            of the mappedIndividual's table
	 * @param primaryKeyColumn
	 *            of the mappedIndividual's table
	 * @param manyProperty
	 * @param joinTable
	 *            the jointable in a many to many relationship or equal to
	 *            othertable in 1:1, 1:*, never null
	 * @param otherTable
	 *            the right hand side table of the relationship, never null
	 * @param otherTableMappedClass
	 * @return a set of individuals that were referenced but not yet read.
	 */
	private Set<OWLNamedIndividual> readMappedRelationshipSql(OWLOntology onto,
			OWLNamedIndividual mappedIndividual, long primaryKeyValue,
			OWLNamedIndividual primaryKeyColumn,
			OWLObjectProperty manyProperty, OWLNamedIndividual joinTable,
			OWLNamedIndividual otherTable, OWLClass otherTableMappedClass,
			Set<OWLNamedIndividual> doneMapped)
	{
		OWLDataFactory df = onto.getOWLOntologyManager().getOWLDataFactory();
		Set<OWLNamedIndividual> referencedIndividuals = Collections.emptySet();
		Connection conn = getConnection();
		// Determinne foreign key based on PK and otherTable.
		OWLNamedIndividual primaryKeyColOtherTable;
		Set<OWLNamedIndividual> primaryKeyCols = RelationalOWLMapper
				.columnIriPK(otherTable);
		boolean nonIRIPrimaryKeyColOtherTable = primaryKeyCols == null
				|| primaryKeyCols.isEmpty();
		if (!nonIRIPrimaryKeyColOtherTable)
		{
			primaryKeyColOtherTable = primaryKeyCols.iterator().next();
		} else
		{
			primaryKeyColOtherTable = null;
		}
		OWLNamedIndividual foreignKeyColAJoinTable = RelationalOWLMapper
				.foreignKeyByjoinColumnAndTable(primaryKeyColumn, joinTable);
		String foreignKeyColAJoinTableStr = getColumnNameSQL(foreignKeyColAJoinTable);
		String sql;
		if (otherTable.equals(joinTable))
		{
			// assert joinTable equals otherTable
			// 1:1 or 1:*
			// in this case the jointable equals the othertable, therefore we
			// can use the foreignKeyColAJoinTableStr
			sql = "SELECT * FROM " + otherTable.getIRI().getFragment()
					+ " WHERE " + foreignKeyColAJoinTableStr + " = "
					+ primaryKeyValue;
		} else
		{
			// many to many, not implemented for nonIRIPK yet.
			String primaryKeyColOtherTableStr = getColumnNameSQL(primaryKeyColOtherTable);
			OWLNamedIndividual foreignKeyColBJoinTable = RelationalOWLMapper
					.foreignKeyByjoinColumnAndTable(primaryKeyColOtherTable,
							joinTable);
			String foreignKeyColBJoinTableStr = getColumnNameSQL(foreignKeyColBJoinTable);
			sql = "SELECT * FROM " + joinTable.getIRI().getFragment() + " A, "
					+ otherTable.getIRI().getFragment() + " B WHERE " + "A."
					+ foreignKeyColAJoinTableStr + " = " + primaryKeyValue
					+ " AND A." + foreignKeyColBJoinTableStr + " = B."
					+ primaryKeyColOtherTableStr;
		}
		java.sql.Statement stmt = null;
		ResultSet rs = null;
		try
		{
			stmt = conn.createStatement();
			rs = stmt.executeQuery(sql);
			int anonId = 1;
			while (rs.next())
			{
				// OWLNamedIndividual primaryKeyColumnOther =
				// RelationalOWLMapper.columnIriPK(otherTable).iterator().next();
				OWLIndividual otherIndividual;
				if (!nonIRIPrimaryKeyColOtherTable)
				{
					// Named Individual
					otherIndividual = (OWLNamedIndividual) selectEntityByID(
							rs.getLong(getColumnNameSQL(primaryKeyColOtherTable)),
							df);
				} else
				{
					// Anonymous individual for nonIRIPrimaryKeyColOtherTable
					// (e.g. ServiceAction)
					// TODO THREAD SAFE OWL DATA FACTORY NEEDED if not onto
					// exclusive
					otherIndividual = onto.getOWLOntologyManager()
							.getOWLDataFactory()
							.getOWLAnonymousIndividual("Anon:" + anonId);
					anonId++;
				}
				if (!doneMapped.contains(otherIndividual) /*
														 * || otherIndividual
														 * instanceof
														 * OWLAnonymousIndividual
														 */)
				{
					if (otherIndividual instanceof OWLNamedIndividual)
					{
						doneMapped.add((OWLNamedIndividual) otherIndividual);
					}
					Set<OWLNamedIndividual> referenced = readMappedIndividualRow(
							onto, otherIndividual, otherTable, rs);
					if (!referenced.isEmpty())
					{
						if (referencedIndividuals.isEmpty())
						{
							referencedIndividuals = new HashSet<OWLNamedIndividual>();
						}
						referencedIndividuals.addAll(referenced);
					}
				}
				// TODO shall we always add axioms here, even though we already
				// visited otherIndividual -> yes, but
				// we need to make sure that we don't traverse the relationship
				// twice e.g. from both ends, if we want to avoid
				// duplicate axiom creation.
				// add axiom (objectprop(manyProperty, mappedInd, subject)
				OWLOntologyManager man = onto.getOWLOntologyManager();
				// TODO THREAD SAFE OWL DATA FACTORY NEEDED if not onto
				// exclusive
				OWLDataFactory factory = man.getOWLDataFactory();
				// we could look at the actual changes here
				man.addAxiom(onto, factory.getOWLObjectPropertyAssertionAxiom(
						manyProperty, mappedIndividual, otherIndividual));
				man.addAxiom(onto, factory.getOWLClassAssertionAxiom(
						otherTableMappedClass, otherIndividual));
			}
			conn.commit();
			return referencedIndividuals;
		} catch (SQLException e)
		{
			rollback(conn);
			throw new RuntimeException(e);
		} finally
		{
			close(rs, stmt, conn);
		}
	}

	/**
	 * Submits a sql statement to read one mapped individual.
	 * 
	 * @param onto
	 * @param individualAndID
	 *            the individual to read. Guaranteed to be convertible to ID.
	 * @return a set of individuals that were referenced but not yet read.
	 */
	public Set<OWLNamedIndividual> readMappedIndividualSql(OWLOntology onto,
			OWLNamedIndividual mappedIndividual, long primaryKeyValue,
			OWLClass mappedIndividualClass, OWLNamedIndividual table,
			OWLNamedIndividual primaryKeyColumn)
	{

		Connection conn = getConnection();
		String primaryKeyColumnStr = getColumnNameSQL(primaryKeyColumn);
		String sql = "SELECT * FROM " + table.getIRI().getFragment()
				+ " WHERE " + primaryKeyColumnStr + " = " + primaryKeyValue;
		Set<OWLNamedIndividual> ret;
		java.sql.Statement stmt = null;
		ResultSet rs = null;
		try
		{
			stmt = conn.createStatement();
			rs = stmt.executeQuery(sql);
			if (!rs.next())
			{
				throw new IllegalStateException("Expected to find "
						+ mappedIndividual + " in table " + table
						+ " using pk col " + primaryKeyColumn
						+ " and pk value " + primaryKeyValue
						+ " but no row was returned.");
			}
			ret = readMappedIndividualRow(onto, mappedIndividual, table, rs);
			conn.commit();
			return ret;
		} catch (SQLException e)
		{
			rollback(conn);
			throw new RuntimeException(e);
		} finally
		{
			close(rs, stmt, conn);
		}
	}

	/**
	 * Reads all mapped properties from a given resultset row that represents
	 * the mappedIndiviual and creates axioms.
	 * 
	 * @param onto
	 * @param individualAndID
	 *            the individual to read. Guaranteed to be convertible to ID.
	 * @param mappedIndividual
	 *            may be a named or anonymous individual (anonymous see
	 *            Cirm_Service_Action)
	 * @param rsAtCurRow
	 *            a resultset with a cursor positioned at the row that
	 *            represents the mappedIndividual
	 * @return a set of foreign key values ordered by name of foreign key
	 *         column. TODO should also return the foreign key values that were
	 *         read.
	 */
	public Set<OWLNamedIndividual> readMappedIndividualRow(OWLOntology onto,
			OWLIndividual mappedIndividual, OWLNamedIndividual table,
			ResultSet rsAtCurRow) throws SQLException
	{
		Set<OWLNamedIndividual> referencedIndividuals = new HashSet<OWLNamedIndividual>();
		// OWLNamedIndividual table =
		// RelationalOWLMapper.table(mappedIndividualClass);
		// Set<OWLNamedIndividual> columnIRIPKs =
		// RelationalOWLMapper.columnIriPK(table);
		Map<OWLProperty<?, ?>, OWLNamedIndividual> propertyToColumn = RelationalOWLMapper
				.columnMapping(table);
		Set<Map<OWLObjectProperty, OWLNamedIndividual>> hasOnePropertyToFKColumn = RelationalOWLMapper
				.hasOneByTable(table);
		// Set<OWLNamedIndividual> columnPKs =
		// RelationalOWLMapper.columnPK(table);
		// READ mapped properties for the individual
		for (Map.Entry<OWLProperty<?, ?>, OWLNamedIndividual> mappedProperty : propertyToColumn
				.entrySet())
		{
			// String columnSQLName = getColumnNameSQL();
            //System.out.println("Reading " + mappedProperty.getKey());
			OWLNamedIndividual referenced = readOWLPropertyFromMappedColumn(
					onto, mappedIndividual, mappedProperty.getKey(),
					mappedProperty.getValue(), false, rsAtCurRow);
			if (referenced != null)
			{
				referencedIndividuals.add(referenced);
			}
		}
		// Read FK values, create axioms and recursively load referred
		// individuals
		// 1:1
		if (hasOnePropertyToFKColumn != null)
		{
			for (Map<OWLObjectProperty, OWLNamedIndividual> hasOneMap : hasOnePropertyToFKColumn)
			{
				// Assuming Singletons
				Map.Entry<OWLObjectProperty, OWLNamedIndividual> hasOneEntry = hasOneMap
						.entrySet().iterator().next();
				OWLNamedIndividual referenced = readOWLPropertyFromMappedColumn(
						onto, mappedIndividual, hasOneEntry.getKey(),
						hasOneEntry.getValue(), true, rsAtCurRow);
				if (referenced != null)
				{
					referencedIndividuals.add(referenced);
				}
			}
		}
		return referencedIndividuals;
	}

	/**
	 * Reads a Data- or ObjectProperty from the Resultset and adds one axiom to
	 * the given ontology.
	 * 
	 * @return null or an individual that was referenced by an object property
	 *         and should be visited.
	 */
	@SuppressWarnings("rawtypes")
	private OWLNamedIndividual readOWLPropertyFromMappedColumn(
			OWLOntology onto, OWLIndividual mappedIndividual,
			OWLProperty property, OWLNamedIndividual column,
			boolean isFKColumn, ResultSet rs) throws SQLException
	{
		OWLNamedIndividual referenced = null;
		// TODO THREAD SAFE OWL DATA FACTORY NEEDED if not onto exclusive
		OWLOntologyManager manager = onto.getOWLOntologyManager();
		OWLDataFactory df = manager.getOWLDataFactory();
		String columnNameSql = getColumnNameSQL(column);
		OWLPropertyAssertionAxiom<?, ?> newAxiom;
		// e.g. VARCHAR,
		OWLNamedIndividual typeSQL = getColumnType(column);
		if (property instanceof OWLDataProperty)
		{
			OWLDataProperty dataProp = (OWLDataProperty) property;
			OWLDatatype typeOWL = getPropertyRangeAsDatatype(dataProp);
			Object value;
			try
			{
				value = rs.getObject(columnNameSql);
			} catch (SQLException e)
			{
				System.err
						.println("SqlException during accessing column: "
								+ columnNameSql + " for individual "
								+ mappedIndividual);
				throw e;
			}
			if (value instanceof oracle.sql.Datum)
			{
				value = getJDBCValueFromOracleValue((oracle.sql.Datum) value);
			}
			// hilpold 2012.08.14 if (value != null || (typeOWL != null &&
			// typeOWL.isString())) {
			if (value != null)
			{
				OWLLiteral literal = createLiteral(df, typeOWL, typeSQL, value);
				newAxiom = df.getOWLDataPropertyAssertionAxiom(dataProp, mappedIndividual, literal);
				manager.addAxiom(onto, newAxiom);
			} 
			else
			{
				if (dbg())
					System.out
							.println("Info: Null value for non xsd:String mapped Column "
									+ columnNameSql
									+ " in "
									+ mappedIndividual
									+ " ignored.");
			}
		} else if (property instanceof OWLObjectProperty)
		{
			if (isFKColumn)
			{
				// Resolving 1:1 or *:1 on * side. Refers to owlnamedobject in
				// the business ontology that needs to be loaded also.
				OWLObjectProperty objectProp = (OWLObjectProperty) property;
				long value = rs.getLong(columnNameSql);
				if (!rs.wasNull())
				{
					OWLNamedIndividual object = (OWLNamedIndividual) selectEntityByID(
							value, df);
					if (object != null)
					{
						newAxiom = df.getOWLObjectPropertyAssertionAxiom(
								objectProp, mappedIndividual, object);
						manager.addAxiom(onto, newAxiom);
						// rs holdability might be an issue, if we would use
						// recursion here.
						// 2012.05.29 hilpold: invalid, ind could be partially
						// loaded nonmapped: if
						// (!onto.containsEntityInSignature(object, false)) {
						referenced = object;
					} else
					{
						if (dbg())
							System.out
									.println("Info: Entity for id not found. Id was:  "
											+ value
											+ " objprop was "
											+ objectProp
											+ " in "
											+ mappedIndividual + " ignored.");
						// throw new
						// IllegalStateException("Entity for id not found. Id was: "
						// + value);
					}
				} // else ignore null col value
			} else
			{
				// A storeFragment -> create an IRI, assuming fragment unique
				// or B long -> refers to entity (e.g. see emailAddress)
				// or C other -> is a full IRI, create named Individual
				OWLIndividual object;
				String value = rs.getString(columnNameSql);
				// NEED To add emailAddress Case.
				// normal property
				// This property will refer to legacy or county ontology.
				OWLObjectProperty objectProp = (OWLObjectProperty) property;
				if (value != null)
				{
					if (RelationalOWLMapper.isStoreFragment(objectProp))
					{
						object = OWL
								.findNamedIndividualByFragment((String) value);
					} else
					{
						// probe for long
						Long valueAsLong = null;
						if (value.length() > 0
								&& Character.isDigit(value.charAt(value
										.length() - 1)))
						{
							try
							{
								valueAsLong = Long.parseLong(value);
							} catch (NumberFormatException e)
							{
								valueAsLong = null;
							}
						}
						if (valueAsLong != null)
						{
							// we have an ID, refers to a named individual in
							// IRI table.
							referenced = (OWLNamedIndividual) selectEntityByID(
									valueAsLong, df);
							// we report it as referenced, there might be more
							// axioms for it that need to be loaded later.
							object = referenced;
						} else
						{
							object = df.getOWLNamedIndividual(IRI
									.create((String) value));
						}
					}
					newAxiom = df.getOWLObjectPropertyAssertionAxiom(
							objectProp, mappedIndividual, object);
					try
					{
						manager.addAxiom(onto, newAxiom);
					}
					catch (Exception ex)
					{
						System.out.println("oops!");
					}
				} // else no axiom created for a null value
			}
		} else
		{
			throw new IllegalArgumentException("property type unknown "
					+ property + " class: " + property.getClass());
		}
		return referenced;
	}

	/**
	 * Converts an oracle specific value from a resultset to a Java value. This
	 * first became necessary when we received a oracle.sql.TimeStamp instead of
	 * a java.sql.TimeStamp from a rs.
	 * 
	 * @param valueORA
	 * @return
	 * @throws IllegalArgumentException
	 *             if conversion fails with SQL problem.
	 * @throws IllegalStateException
	 *             if conversion fails due to ora implementation.
	 */
	public Object getJDBCValueFromOracleValue(oracle.sql.Datum valueORA)
	{
		try
		{
			Object nonORAValue = valueORA.toJdbc();
			if (nonORAValue instanceof oracle.sql.Datum)
			{
				throw new IllegalStateException(
						"Conversion resulted in another ORACLE specific value");
			}
			return nonORAValue;
		} catch (SQLException e)
		{
			throw new IllegalArgumentException("Conversion of value "
					+ valueORA + " failed with SQLException.", e);
		}
	}

	public OWLEntity selectEntityByID(final long id, final OWLDataFactory df)
	{
		return txn(new CirmTransaction<OWLEntity>()
		{
			@Override
			public OWLEntity call() throws Exception
			{
				return selectEntityByIDInt(id, df);
			}
		});
		
	}
	/**
	 * Selects one entity by id.
	 * 
	 * @param id
	 * @return the entity or null, if id not found in IRI table.
	 */
	public OWLEntity selectEntityByIDInt(long id, OWLDataFactory df)
	{
		Map<Long, OWLEntity> result = selectEntitiesByIDs(
				Collections.singleton(id), df);
		return (result == null || result.isEmpty()) ? null : result.entrySet()
				.iterator().next().getValue();
	}

	/**
	 * Creates a literal for the given value. Allows null for Strings, but for
	 * no other types. For Timestamp conversion to lexical value, system has to
	 * operate in the same timezone!
	 * 
	 * If typeSQL is VarChar and typeOwl is null, a String literal will be
	 * created.
	 * 
	 * @param factory
	 * @param typeOWL
	 * @param typeSQL
	 * @param value
	 * @return
	 */
	private OWLLiteral createLiteral(OWLDataFactory factory,
	                                 OWLDatatype typeOWL, 
	                                 OWLNamedIndividual typeSQL, 
	                                 Object value)
	{
		OWLLiteral result;
		String typeSQLAsStr = typeSQL.getIRI().toString();
		if (typeSQLAsStr.equals(Concepts.VARCHAR))
		{
			if (typeOWL == null)
			{
				// Assuming string on VARCHAR; this should be defined in the
				// ontology as datatype range.
				typeOWL = factory.getOWLDatatype(OWL2Datatype.XSD_STRING
						.getIRI());
			}
			if (value == null && typeOWL.isString())
			{
				value = "";
			}
			result = factory.getOWLLiteral((String) value, typeOWL);
		} 
		else if (typeSQLAsStr.equals(Concepts.INTEGER))
		{
			// this will throw NPE if value null.
			int valueInt;
			if (value instanceof BigInteger)
			{
				valueInt = ((BigInteger) value).intValue();
			} 
			else if (value instanceof BigDecimal)
			{
				valueInt = ((BigDecimal) value).intValue();
			} 
			else if (value != null)
			{
				valueInt = ((Number) value).intValue();
			} 
			else
			{
				System.err
						.println("Creating empty string literal for null value in INTEGER SQL column. This indicates a mapping problem from Integer SQL to String Onto.");
				return factory.getOWLLiteral("");
			}
			if (value != null && typeOWL.isBuiltIn()
					&& typeOWL.getBuiltInDatatype() == OWL2Datatype.XSD_BOOLEAN)
			{
				boolean valueBool = valueInt == 0 ? false : true;
				result = factory.getOWLLiteral(valueBool);
			} else
			{
				result = factory.getOWLLiteral(valueInt);
			}
		} 
		else if (typeSQLAsStr.equals(Concepts.DOUBLE))
		{
			if (value instanceof BigDecimal)
			{
				BigDecimal valueBigDec = (BigDecimal) value;
				result = factory.getOWLLiteral(valueBigDec.toPlainString(),
						typeOWL);
			} else if (value != null)
			{
				double valueDouble = (Double) value;
				result = factory.getOWLLiteral(valueDouble);
			} else
			{
				System.err
						.println("Creating empty string literal for null value in DOUBLE SQL column. This indicates a mapping problem from DOUBLE SQL to String Onto.");
				result = factory.getOWLLiteral("");
			}
		} 
		else if (typeSQLAsStr.equals(Concepts.TIMESTAMP))
		{
			String lexicalValue;
			Timestamp valueST;
			// hilpold unfortunately we have to deal with a potential oracle
			// timstamp here.
			if (value instanceof oracle.sql.TIMESTAMP)
			{
				oracle.sql.TIMESTAMP valueAsOrclTimeStamp = (oracle.sql.TIMESTAMP) value;
				try
				{
					valueST = (Timestamp) valueAsOrclTimeStamp.timestampValue();
				} catch (SQLException e)
				{
					throw new RuntimeException(
							"Could not convert oracle specific timestamp to java.sql.Timestamp.",
							e);
				}
			} else
			{
				valueST = (Timestamp) value;
			}
			// TODO default (!!!) timezone, we should use only one timezone to
			// interpret timestamps during conversion lexi -> long, long ->
			// lexi.
			GregorianCalendar cal = new GregorianCalendar();
			cal.setTime(valueST);
			// lexical ISO 8601 date
			synchronized (xmlDatatypeFactory)
			{
				lexicalValue = xmlDatatypeFactory.newXMLGregorianCalendar(cal)
						.toXMLFormat();
			}
			if (typeOWL == null)
			{
				System.out.println("Type was null for timestamp: "
						+ lexicalValue + " assuming "
						+ OWL2Datatype.XSD_DATE_TIME_STAMP.getIRI());
				typeOWL = factory
						.getOWLDatatype(OWL2Datatype.XSD_DATE_TIME_STAMP
								.getIRI());
			}
			result = factory.getOWLLiteral(lexicalValue, typeOWL);
		} 
		else if (typeSQLAsStr.equals(Concepts.CLOB))
		{
			Clob valueClob = (Clob) value;
			try
			{
				result = factory.getOWLLiteral(valueClob.getSubString(0L,
						(int) valueClob.length()));
			} catch (SQLException e)
			{
				throw new RuntimeException("Could not read Clob: " + value
						+ " for " + typeOWL, e);
			}
		} 
		else
		{
			throw new IllegalArgumentException("TypeSQL not recognized : "
					+ typeSQL + " asStr: " + typeSQLAsStr);
		}

		return result;
	}

	/**
	 * Checks hasColumnType ObjectProperty to get the type of the column.
	 * 
	 * @param column
	 * @return
	 */
	private OWLNamedIndividual getColumnType(OWLNamedIndividual column)
	{
		Set<OWLNamedIndividual> columnTypes = reasoner()
				.getObjectPropertyValues(column,
						objectProperty(fullIri(Concepts.hasColumnType)))
				.getFlattened();
		if (columnTypes.size() > 1)
		{
			System.err.println("More than one columntype for col: " + column
					+ ". using first.");
			for (OWLNamedIndividual coltype : columnTypes)
			{
				System.err.println("type: " + coltype);
			}
		}
		return columnTypes.iterator().next();
	}

	/**
	 * Finds an OWLDataType, which is a range for the given property by
	 * iterating over the OWLDataPropertyRangeAxioms.
	 * 
	 * @param property
	 * @return the DataType or null, if non found.
	 */
	private OWLDatatype getPropertyRangeAsDatatype(OWLDataProperty property)
	{
		Set<OWLOntology> ontos = OWL.ontologies();// MetaService.get().
													// ontology().getImportsClosure()
		Set<OWLDataPropertyRangeAxiom> rangeAxioms = new HashSet<OWLDataPropertyRangeAxiom>();
		for (OWLOntology onto : ontos)
		{
			rangeAxioms.addAll(onto.getDataPropertyRangeAxioms(property));
		}
		// find first range that is a datatype
		for (OWLDataPropertyRangeAxiom rax : rangeAxioms)
		{
			OWLDataRange range = rax.getRange();
			if (range instanceof OWLDatatype)
			{
				return (OWLDatatype) range;
			}
		}
		// System.err.println("getPropertyRange: No datatype Range found, returning null for data property: "
		// + property);
		return null;
	}

	/**
	 * Returns the COLUMN from
	 * <TABLE>
	 * .<COLUMN>.
	 * 
	 * @param column
	 * @return
	 */
	public String getColumnNameSQL(OWLNamedIndividual column)
	{
		return column.getIRI().getFragment().split("\\.")[1];
	}

	/**
	 * Finds an OWLClass (with fully qualified IRI) to a mapped individuals IRI,
	 * by extracting a short class name from the individual's IRI and searching
	 * in all classes of all loaded Ontologies.
	 * 
	 * The algorithm assumes that IRI-contained short classnames are unique
	 * within the loaded Ontologies, but does not check this condition.
	 * 
	 * @see getMainClassNameFromIRI
	 * @param mappedInd
	 * @return an OWLClass with a fully qualified IRI.
	 */
	public OWLClass findClassInLoadedOntologiesFor(
			OWLNamedIndividual mappedIndividual)
	{
		String className = getMainClassNameFromIRI(mappedIndividual.getIRI());
		if (className != null)
		{
			for (OWLOntology o : OWL.ontologies())
			{
				for (OWLClass c : o.getClassesInSignature())
				{
					if (c.getIRI().getFragment().equals(className))
					{
						return c;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Reads the classname from IRIs given in two formats:
	 * http://www.miamidade.gov/bo/{Main_Type_IRI_Short}/67#bo <br/>
	 * or <br/>
	 * http://www.miamidade.gov/ontology#Street_Address2972 <br/>
	 * 
	 * 
	 * @param individualIRI
	 *            an iri representing a bo or another individual.
	 * @return a short string representation of the class without a base IRI.
	 *         e.g. BULKYTRA, Street_Address or NULL.
	 */
	public String getMainClassNameFromIRI(IRI individualIRI)
	{
		String className = null;
		if (OWL.isBusinessObject(individualIRI))
		{
			className = individualIRI.toURI().getPath().split("/")[2];
		} else
		{
			String fragment = individualIRI.getFragment();
			// Split off the tailing number consisting of more than one digit.
			if (fragment != null
					&& fragment.length() > 0
					&& Character
							.isDigit(fragment.charAt(fragment.length() - 1)))
			{
				String[] split = fragment.split("\\d+");
				if (split.length == 1 && split[0].length() > 0)
				{
					// we found valid className
					className = split[0];
				}
			}
		}
		return className;
	}

	/**
	 * 
	 * Builds a SQL Merge statement for an individual.
	 * 
	 * @param o
	 * @param ind
	 * @param tableMapping
	 * @param columnMapping
	 * @param identifiers
	 * @param statements
	 * @param done
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void mergeMappedIndividual(OWLOntology o, OWLNamedIndividual ind,
			Map<OWLEntity, DbId> identifiers,
			Map<OWLNamedIndividual, DbId> foreignKeys,
			Map<OWLNamedIndividual, List<Statement>> done)
	{
		// logger.info("Merging individual" + ind.getIRI());
		if (done.containsKey(ind))
			return;
		if (dbg())
		{
			System.out.println("Merging mapped ind: " + ind.getIRI()
					+ " Foreign keys: " + foreignKeys);
		}
		boolean put = true;
		List<Statement> statements = new ArrayList<Statement>();
		Statement statement = new Statement();
		OWLNamedIndividual table = RelationalOWLMapper.table(ind.getTypes(o));
		if (table == null)
		{
			System.err.println("No table for individual: " + ind + " types were: ");
			int i = 0;
			for (OWLClassExpression cle : ind.getTypes(o))
			{
				System.err.println("Class: " + i + " is " + cle);
				i++;
			}
			throw new IllegalStateException("No table for individual " + ind + " mapped. Cannot continue.");
		}
		Map<OWLProperty, OWLNamedIndividual> columns = (Map) RelationalOWLMapper.columnMapping(table);
		Sql update = UPDATE(table.getIRI().getFragment());// + " as A");
		List<OWLNamedIndividual> updateTypes = new ArrayList<OWLNamedIndividual>();
		List<Object> updateParameters = new ArrayList<Object>();
		Sql insert = INSERT_INTO(table.getIRI().getFragment());
		List<OWLNamedIndividual> insertTypes = new ArrayList<OWLNamedIndividual>();
		List<Object> insertParameters = new ArrayList<Object>();

		// add mapped properties
		for (Map.Entry<OWLProperty, OWLNamedIndividual> mappedProperty2Col : columns.entrySet())
		{
			OWLNamedIndividual column = mappedProperty2Col.getValue();
			String columnName = mappedProperty2Col.getValue().getIRI().getFragment();
			Set<OWLNamedIndividual> columnType = 
			   reasoner().getObjectPropertyValues(column,objectProperty(fullIri(Concepts.hasColumnType))).getFlattened();
			if (mappedProperty2Col.getKey() instanceof OWLDataProperty)
			{
				OWLLiteral value;
				Iterator<OWLLiteral> valueIterator = ind.getDataPropertyValues(
						mappedProperty2Col.getKey().asOWLDataProperty(), o).iterator();
				value = valueIterator.hasNext() ? valueIterator.next() : null;
				OWLNamedIndividual type = columnType.iterator().next().asOWLNamedIndividual();
				insert.VALUES(columnName.replace(table.getIRI().getFragment() + ".", ""), "?");
				insertParameters.add(value);
				insertTypes.add(type);
				Set<OWLNamedIndividual> pk = RelationalOWLMapper.columnPK(table);
				//if the column is not a primary key then add it to the update stmt. 
				if (pk == null || !pk.contains(column))
				{
					update.SET(columnName.replace(table.getIRI().getFragment() + ".", ""), "?");
					updateParameters.add(value);
					updateTypes.add(type);
				}
			} 
			else if (mappedProperty2Col.getKey() instanceof OWLObjectProperty)
			{
				OWLObjectProperty property = mappedProperty2Col.getKey().asOWLObjectProperty();
				Iterator<OWLIndividual> valueIterator = ind.getObjectPropertyValues(property, o).iterator();
				OWLNamedIndividual value;
				value = valueIterator.hasNext() ? valueIterator.next().asOWLNamedIndividual() : null;
				// store value, check if fragment only
				OWLNamedIndividual type = columnType.iterator().next().asOWLNamedIndividual();
				update.SET(columnName.replace(table.getIRI().getFragment() + ".", ""), "?");
				updateTypes.add(type);
				insert.VALUES(columnName.replace(table.getIRI().getFragment() + ".", ""), "?");
				insertTypes.add(type);
				if (value == null)
				{
					updateParameters.add(null);
					insertParameters.add(null);
				} 
				else if (RelationalOWLMapper.isStoreFragment(property))
				{
					// check if IRI is in one of managed ontologies.
					IRI valueAsIRI = value.getIRI();
					if (ontology().containsIndividualInSignature(valueAsIRI,
							true))
					{
						updateParameters.add(valueAsIRI.getFragment());
						insertParameters.add(valueAsIRI.getFragment());
					} 
					else
					{
						updateParameters.add(valueAsIRI.getFragment());
						insertParameters.add(valueAsIRI.getFragment());
						// 2013.02.11 Exception restored after room discussion
						String msg = "Tried to store value: "
								+ valueAsIRI
								+ " for property: "
								+ property
								+ ". The value represents an individual that is not contained in the import closure of meta ontology. \r\n"
								+ "StoreFragment must only be used for properties that are defined in meta.";
						throw new IllegalStateException(msg);
					}
				} 
				else
				{
					updateParameters.add(identifiers.get(value));
					insertParameters.add(identifiers.get(value));
				}
			} 
			else
			{
				throw new IllegalArgumentException(
						"property neither Data- nor ObjectProperty:  "
								+ mappedProperty2Col);
			}
		} // end foreach
			// MAP FOREIGNKEYS START
		if (foreignKeys != null && !foreignKeys.isEmpty())
		{
			Set<OWLNamedIndividual> pk = RelationalOWLMapper.columnPK(table);
			for (Map.Entry<OWLNamedIndividual, DbId> foreignKey : foreignKeys.entrySet())
			{

				String foreignKeyColumn = foreignKey.getKey().getIRI().getFragment();
				// TODO hilpoldQ Can RelationalOWLMapper be used here?
				Set<OWLNamedIndividual> fkType = reasoner()
						.getObjectPropertyValues(foreignKey.getKey(),objectProperty(fullIri(Concepts.hasColumnType)))
						.getFlattened();
				OWLNamedIndividual type = fkType.iterator().next().asOWLNamedIndividual();
				insert.VALUES(foreignKeyColumn.replace(table.getIRI().getFragment() + ".", ""), "?");
				insertTypes.add(type);
				insertParameters.add(foreignKey.getValue());
				//if the column is not a primary key then add it to the update stmt.
				if (pk == null || !pk.contains(foreignKey.getKey()))
				{
					update.SET(foreignKeyColumn.replace(table.getIRI().getFragment() + ".", ""), "?");
					updateTypes.add(type);
					updateParameters.add(foreignKey.getValue());
				}
			}
		}
		// hasOne
		Map<OWLObjectPropertyExpression, Set<OWLIndividual>> objectPropertyValues = ind.getObjectPropertyValues(o);
		for (Map.Entry<OWLObjectPropertyExpression, Set<OWLIndividual>> mappedProperty : objectPropertyValues.entrySet())
		{
			OWLObjectPropertyExpression ex = mappedProperty.getKey();
			if (ex instanceof OWLObjectProperty)
			{
				OWLObjectProperty prop = (OWLObjectProperty) ex;
				OWLNamedIndividual column = RelationalOWLMapper.hasOne(prop,table);
				if (column != null)
				{
					Set<OWLIndividual> set = objectPropertyValues.get(prop);
					if (set.size() > 0)
					{
						OWLNamedIndividual entity = set.iterator().next().asOWLNamedIndividual();
						Set<OWLNamedIndividual> columnType = 
						    reasoner().getObjectPropertyValues(column, 
						            objectProperty(fullIri(Concepts.hasColumnType))).getFlattened();
						String columnName = column.getIRI().getFragment();
						// RECURSION ALONG HASONE RELATIONSHIP
						mergeMappedIndividual(o, entity, identifiers, null, done);
						OWLNamedIndividual type = columnType.iterator().next().asOWLNamedIndividual();
						update.SET(columnName.replace(table.getIRI().getFragment() + ".", ""), "?");
						updateTypes.add(type);
						updateParameters.add(identifiers.get(entity));
						insert.VALUES(columnName.replace(table.getIRI().getFragment() + ".", ""), "?");
						insertTypes.add(type);
						insertParameters.add(identifiers.get(entity));
					}
				}
			}
		}
		// remove relationship hasOne properties by setting the foregin key
		// column to null when the property is not provided.
		Set<Map<OWLObjectProperty, OWLNamedIndividual>> hasOnePropsByTable = RelationalOWLMapper.hasOneByTable(table);
		if (hasOnePropsByTable != null)
		{
			for (Map<OWLObjectProperty, OWLNamedIndividual> prop2Columns : hasOnePropsByTable)
			{
				for (Map.Entry<OWLObjectProperty, OWLNamedIndividual> prop2Column : prop2Columns.entrySet())
				{
					OWLObjectProperty prop = prop2Column.getKey();
					OWLNamedIndividual column = prop2Column.getValue();
					String columnName = column.getIRI().getFragment();
					String columnAlias = columnName.replace(table.getIRI().getFragment() + ".", "");
					// If the hasOne column has already been provided
					// then do nothing.
					if (insert.COLUMNS().contains(columnAlias) || update.COLUMNS().contains(columnAlias))
					{
						continue;
					}
					if (!objectPropertyValues.containsKey(prop))
					{
						Set<OWLNamedIndividual> columnType = reasoner()
								.getObjectPropertyValues(
										column,
										objectProperty(fullIri(Concepts.hasColumnType)))
								.getFlattened();
						OWLNamedIndividual type = columnType.iterator().next().asOWLNamedIndividual();
						update.SET(columnAlias, "?");
						updateTypes.add(type);
						updateParameters.add(null);
						insert.VALUES(columnAlias, "?");
						insertTypes.add(type);
						insertParameters.add(null);
					}

				}
			}
		}
		// end hasOne
		Set<OWLNamedIndividual> columnIRI = RelationalOWLMapper.columnIriPK(table);
		OWLNamedIndividual column = null;
		// add iri value
		// THIS IS THE PRIMARY KEY FOR SELECT
		if (columnIRI != null && !columnIRI.isEmpty())
		{
			column = columnIRI.iterator().next();
			OWLNamedIndividual columnType = reasoner()
					.getObjectPropertyValues(column, objectProperty(fullIri(Concepts.hasColumnType))).getFlattened().iterator().next();
			update.WHERE(column.getIRI().getFragment().replace(table.getIRI().getFragment() + ".", "")).EQUALS("?");
			updateParameters.add(identifiers.get(ind));
			updateTypes.add(columnType);
			insert.VALUES(column.getIRI().getFragment().replace(table.getIRI().getFragment() + ".", ""), "?");
			insertParameters.add(identifiers.get(ind));
			insertTypes.add(columnType);
		}

		if (identifiers.get(ind).isExisting())
		{
		    statement.setSql(update);
		    statement.getParameters().addAll(updateParameters);
		    statement.getTypes().addAll(updateTypes);
		}
		else
		{
		    statement.setSql(insert);
		    statement.getParameters().addAll(insertParameters);
		    statement.getTypes().addAll(insertTypes);
		}
		statements.add(statement);
		// At this point the statement to add a row has been constructed. Now
		// resolve
		// hasMany RelationShips
		// hasMany
		Map<OWLObjectProperty, OWLClass> hasMany = RelationalOWLMapper.hasMany(objectPropertyValues.keySet());
		for (OWLObjectProperty mappedProperty : hasMany.keySet())
		{
			OWLClass hasManyMappedClass = hasMany.get(mappedProperty);
			OWLNamedIndividual manyTable = RelationalOWLMapper.table(hasManyMappedClass);
			OWLNamedIndividual joinTable = RelationalOWLMapper.join(table, manyTable);
			if (joinTable == null)
				continue;
			boolean manyToMany = !joinTable.equals(manyTable);
			OWLNamedIndividual joinColumnIRI = RelationalOWLMapper.foreignKeyByjoinColumnAndTable(column, joinTable);
			Set<OWLNamedIndividual> manyColumnIRI = RelationalOWLMapper.columnIriPK(manyTable);
			String joinTableFragment = joinTable.getIRI().getFragment();
			String joinColumnIRIFragment = joinColumnIRI.getIRI().getFragment().replace(joinTableFragment + ".", "");
			OWLNamedIndividual joinColumnIRIType = 
			    reasoner().getObjectPropertyValues(joinColumnIRI,objectProperty(fullIri(Concepts.hasColumnType))).getFlattened().iterator().next();
			OWLNamedIndividual foreignKeyColumnManyToMany = null;
			OWLNamedIndividual foreignKeyColumnManyToManyType = null;
			// Create delete statement first, but only if its an existing entity.
			if (identifiers.get(ind).isExisting())
			{
				Statement delete = null;
				if(manyToMany)
				{
					delete = buildManyToManyDelete(ind, identifiers, joinTableFragment, joinColumnIRIFragment, joinColumnIRIType);
				}
				else
				{
					delete = buildOneToManyDelete(ind, identifiers, objectPropertyValues, mappedProperty, manyColumnIRI, 
							joinTableFragment, joinColumnIRIFragment, joinColumnIRIType);
				}
				statements.add(delete);
			}
			//end create delete statement
			// Loop through all manyObjects merging one by one.
			for (OWLIndividual propValue : objectPropertyValues.get(mappedProperty))
			{
				OWLNamedIndividual manyObject = propValue.asOWLNamedIndividual();
				if (manyToMany)
				{
					OWLNamedIndividual manyColumn = manyColumnIRI.iterator().next();
					if (manyColumnIRI != null)
					{
						// RECURSION
						mergeMappedIndividual(o, manyObject, identifiers, null, done);
						if (foreignKeyColumnManyToMany == null)
							foreignKeyColumnManyToMany = 
							    RelationalOWLMapper.foreignKeyByjoinColumnAndTable(manyColumn, joinTable);
						if (foreignKeyColumnManyToManyType == null)
							foreignKeyColumnManyToManyType = reasoner()
									.getObjectPropertyValues(
											foreignKeyColumnManyToMany,
											objectProperty(fullIri(Concepts.hasColumnType)))
									.getFlattened().iterator().next();
						String foreignColumn = foreignKeyColumnManyToMany.getIRI().getFragment().replace(joinTableFragment + ".", "");
						Statement ins = new Statement();
						ins.setSql(INSERT_INTO(joinTableFragment).VALUES(joinColumnIRIFragment, "?").VALUES(foreignColumn, "?"));
						ins.getParameters().add(identifiers.get(ind));
						ins.getParameters().add(identifiers.get(manyObject));
						ins.getTypes().add(joinColumnIRIType);
						ins.getTypes().add(foreignKeyColumnManyToManyType);
						statements.add(ins);
					}
				} 
				else
				{
					// ONE TO MANY
					if (done.containsKey(manyObject))
					{
						//logger.info("removing and re-merging many-to-one: "
						//		+ manyObject.getIRI());
						done.remove(manyObject);
						//logger.info("join Object" + ind.getIRI().toString());
					}
					done.put(ind, statements);
					put = false;
					mergeMappedIndividual(
							o,
							manyObject,
							identifiers,
							Collections.singletonMap(joinColumnIRI,
									identifiers.get(ind)), done);
				}
			}
			
		}
		if (put)
			done.put(ind, statements);

	}

	private Statement buildOneToManyDelete(
			OWLNamedIndividual ind,
			Map<OWLEntity, DbId> identifiers,
			Map<OWLObjectPropertyExpression, Set<OWLIndividual>> objectPropertyValues,
			OWLObjectProperty mappedProperty,
			Set<OWLNamedIndividual> manyColumnIRI, String joinTableFragment,
			String joinColumnIRIFragment, OWLNamedIndividual joinColumnIRIType)
	{
        if (ind == null) throw new NullPointerException("ind param was null");
        //next line expensive - maybe do check later in the code
        if (identifiers.get(ind) == null) throw new IllegalStateException("no identifier found for ind " + ind);
        if (objectPropertyValues == null) throw new NullPointerException("null objectPropertyValues for ind " + ind);
        if (mappedProperty == null) throw new NullPointerException("null mappedProperty for ind " + ind);
        if (manyColumnIRI == null) throw new NullPointerException("null manyColumnIRI for ind " + ind);
        if (joinTableFragment == null) throw new NullPointerException("null joinTableFragment for ind " + ind);
        if (joinColumnIRIFragment == null) throw new NullPointerException("null joinColumnIRIFragment for ind " + ind);
        if (joinColumnIRIType == null) throw new NullPointerException("null joinColumnIRIType for ind " + ind);

		Statement delete = new Statement();
		Sql deleteSql = DELETE_FROM(joinTableFragment).WHERE(joinColumnIRIFragment).EQUALS("?");
		delete.getParameters().add(identifiers.get(ind));
		delete.getTypes().add(joinColumnIRIType);
		if(!objectPropertyValues.get(mappedProperty).isEmpty())
		{
				OWLNamedIndividual manyColumnInd = manyColumnIRI.iterator().next();
				String manyColumn = manyColumnInd.getIRI().getFragment().replace(joinTableFragment + ".", "");
				OWLNamedIndividual manyColumnIRIType = 
					    reasoner().getObjectPropertyValues(manyColumnInd,objectProperty(fullIri(Concepts.hasColumnType))).getFlattened().iterator().next();
				deleteSql.AND().WHERE(manyColumn);
				List<String> values = new ArrayList<String>(objectPropertyValues.get(mappedProperty).size());
				for (OWLIndividual propValue : objectPropertyValues.get(mappedProperty))
				{
					OWLNamedIndividual manyObject = propValue.asOWLNamedIndividual();
					values.add("?");
					delete.getParameters().add(identifiers.get(manyObject));
					delete.getTypes().add(manyColumnIRIType);
				}
				deleteSql.NOT_IN(values.toArray(new String[values.size()]));
		}
		delete.setSql(deleteSql);
		if(DBGX)
		{
			printStatement(delete);
		}
		return delete;
	}
	
	private Statement buildManyToManyDelete(
			OWLNamedIndividual ind,
			Map<OWLEntity, DbId> identifiers,
			String joinTableFragment,
			String joinColumnIRIFragment, OWLNamedIndividual joinColumnIRIType	)
	{
        if (ind == null) throw new NullPointerException("ind param was null");
        //next line expensive - maybe do check later in the code
        if (identifiers.get(ind) == null) throw new IllegalStateException("no identifier found for ind " + ind);
        if (joinTableFragment == null) throw new NullPointerException("null joinTableFragment for ind " + ind);
        if (joinColumnIRIFragment == null) throw new NullPointerException("null joinColumnIRIFragment for ind " + ind);
        if (joinColumnIRIType == null) throw new NullPointerException("null joinColumnIRIType for ind " + ind);

		Statement delete = new Statement();
		Sql deleteSql = DELETE_FROM(joinTableFragment).WHERE(joinColumnIRIFragment).EQUALS("?");
		delete.getParameters().add(identifiers.get(ind));
		delete.getTypes().add(joinColumnIRIType);
		delete.setSql(deleteSql);
		if(DBGX)
		{
			printStatement(delete);
		}
		return delete;
	}

	/**
	 * Creates update and insert statements for CIRM_CLASSIFICATION
	 * 
	 * @param set
	 * @param individuals
	 * @param identifiers
	 * @return a singleton map containing as key the list of updates and as
	 *         value the list of inserts.
	 */
	private Map<List<Statement>, List<Statement>> mergeClassAssertions(
			Set<OWLClassAssertionAxiom> set,
			Set<OWLNamedIndividual> individuals,
			Map<OWLEntity, DbId> identifiers, Timestamp time)
	{
		List<Statement> updates = new ArrayList<Statement>();
		List<Statement> inserts = new ArrayList<Statement>();
		Sql update = UPDATE(TABLE_CLASSIFICATION).SET("TO_DATE", "?")
				.WHERE("SUBJECT").EQUALS("?").AND().WHERE("TO_DATE IS NULL");
		// Timestamp now = new Timestamp(getStoreTime().getTime());
		List<OWLNamedIndividual> updateParamTypes = new ArrayList<OWLNamedIndividual>();
		updateParamTypes.add(individual(Concepts.TIMESTAMP));
		updateParamTypes.add(individual(Concepts.INTEGER));
		Sql insert = INSERT_INTO(TABLE_CLASSIFICATION).VALUES("SUBJECT", "?")
				.VALUES("OWLCLASS", "?").VALUES("FROM_DATE", "?");
		List<OWLNamedIndividual> insertParamTypes = new ArrayList<OWLNamedIndividual>();
		insertParamTypes.add(individual(Concepts.INTEGER));
		insertParamTypes.add(individual(Concepts.INTEGER));
		insertParamTypes.add(individual(Concepts.TIMESTAMP));
		for (OWLNamedIndividual i : individuals)
		{
			List<Object> parameters = new ArrayList<Object>();
			parameters.add(time);
			Long individualID = identifiers.get(i).getFirst();
			if (individualID == null)
			{
				throw new IllegalArgumentException(
						"Individual ID not found in identifiers for: " + i);
			}
			parameters.add(individualID);
			Statement statement = new Statement();
			statement.setSql(update);
			statement.setParameters(parameters);
			statement.setTypes(updateParamTypes);
			updates.add(statement);
		}
		for (OWLClassAssertionAxiom axiom : set)
		{
			OWLClassExpression expr = axiom.getClassExpression();
			if (expr instanceof OWLClass)
			{
				List<Object> parameters = new ArrayList<Object>();
				Long individualID = identifiers.get(axiom.getIndividual()).getFirst();
				if (individualID == null)
				{
					throw new IllegalArgumentException(
							"Individual ID not found in identifiers for: "
									+ axiom.getIndividual() + " in axiom : "
									+ axiom);
				}
				parameters.add(individualID);
				//
				// Long s = identifiers.get(axiom.getIndividual());
				// parameters.add(s);
				Long c = identifiers.get((OWLClass) expr).getFirst();
				parameters.add(c);
				parameters.add(time);
				Statement statement = new Statement();
				statement.setSql(insert);
				statement.setParameters(parameters);
				statement.setTypes(insertParamTypes);
				inserts.add(statement);
			}
		}
		return Collections.singletonMap(updates, inserts);
	}

	private Map<List<Statement>, List<Statement>> mergeDataProperties(
			Set<OWLDataPropertyAssertionAxiom> axioms,
			Set<OWLNamedIndividual> individuals,
			Map<OWLEntity, DbId> identifiers,
			Map<Object, Long> literalValueIdentifiers, Timestamp time)
	{
		List<Statement> updates = new ArrayList<Statement>();
		List<Statement> inserts = new ArrayList<Statement>();
		// 1 : set all TO_DATES for all Properties of the Subject to end their
		// history
		Sql update = UPDATE(TABLE_DATA_PROPERTY).SET("TO_DATE", "?")
				.WHERE("SUBJECT").EQUALS("?").AND().WHERE("TO_DATE IS NULL");
		// Timestamp now = new Timestamp(getStoreTime().getTime());
		List<OWLNamedIndividual> types = new ArrayList<OWLNamedIndividual>();
		types.add(individual(Concepts.TIMESTAMP));
		types.add(individual(Concepts.INTEGER));
		// 2: insert all new
		Sql insert = INSERT_INTO(TABLE_DATA_PROPERTY).VALUES("SUBJECT", "?")
				.VALUES("PREDICATE", "?").VALUES("DATATYPE_ID", "?")
				// .VALUES("LANG", "?")
				.VALUES("VALUE_ID", "?").VALUES("FROM_DATE", "?");
		// .VALUES("VALUE_HASH", "?");
		List<OWLNamedIndividual> types2 = new ArrayList<OWLNamedIndividual>();
		types2.add(individual(Concepts.INTEGER));
		types2.add(individual(Concepts.INTEGER));
		types2.add(individual(Concepts.INTEGER));
		types2.add(individual(Concepts.INTEGER));
		types2.add(individual(Concepts.TIMESTAMP));
		for (OWLNamedIndividual i : individuals)
		{
			Long s = identifiers.get(i).getFirst();
			List<Object> parameters = new ArrayList<Object>();
			parameters.add(time);
			parameters.add(s);
			Statement statement = new Statement();
			statement.setSql(update);
			statement.setParameters(parameters);
			statement.setTypes(types);
			updates.add(statement);

		}
		for (OWLDataPropertyAssertionAxiom axiom : axioms)
		{
			Long subjectID = identifiers.get(axiom.getSubject()).getFirst();
			OWLDataProperty dataproperty = (OWLDataProperty) axiom
					.getProperty();
			Long predicateID = identifiers.get(dataproperty).getFirst();
			OWLLiteral literal = axiom.getObject();
			Long datatypeID = identifiers.get(literal.getDatatype()).getFirst();
			Object value = getLiteralSqlValue(literal);
			// System.out.println(literal);
			Long valueID = literalValueIdentifiers.get(value);
			if (subjectID == null)
				throw new IllegalStateException(
						"Subject's Id not found in identifiers. Subject was: "
								+ axiom.getSubject() + " Axiom was: " + axiom);
			if (predicateID == null)
				throw new IllegalStateException(
						"Predicate's Id/Property not found in identifiers. Property was: "
								+ axiom.getProperty() + " Axiom was: " + axiom);
			if (datatypeID == null)
				throw new IllegalStateException(
						"Dataype's Id not found in identifiers. Datatype was: "
								+ literal.getDatatype() + " Literal was: "
								+ literal + " Axiom was: " + axiom);
			if (valueID == null)
				throw new IllegalStateException(
						"Value's Id not found in identifiers. Value was: "
								+ value + " Literal was: " + literal
								+ " Axiom was: " + axiom);
			// String hash = hash(value);
			List<Object> parameters = new ArrayList<Object>();
			parameters.add(subjectID);
			parameters.add(predicateID);
			parameters.add(datatypeID);
			parameters.add(valueID);
			parameters.add(time);
			Statement statement = new Statement();
			statement.setSql(insert);
			statement.setParameters(parameters);
			statement.setTypes(types2);
			inserts.add(statement);
		}
		return Collections.singletonMap(updates, inserts);
	}

	private Map<List<Statement>, List<Statement>> mergeObjectProperties(
			Set<OWLObjectPropertyAssertionAxiom> axioms,
			Set<OWLNamedIndividual> individuals,
			Map<OWLEntity, DbId> identifiers, 
			Timestamp time)
	{
		List<Statement> updates = new ArrayList<Statement>();
		List<Statement> inserts = new ArrayList<Statement>();
		Sql update = UPDATE(TABLE_OBJECT_PROPERTY).SET("TO_DATE", "?")
				.WHERE("SUBJECT").EQUALS("?").AND().WHERE("TO_DATE IS NULL");
		// Timestamp now = new Timestamp(getStoreTime().getTime());
		List<OWLNamedIndividual> types = new ArrayList<OWLNamedIndividual>();
		types.add(individual(Concepts.TIMESTAMP));
		types.add(individual(Concepts.INTEGER));
		Sql insert = INSERT_INTO(TABLE_OBJECT_PROPERTY).VALUES("SUBJECT", "?")
				.VALUES("PREDICATE", "?").VALUES("OBJECT", "?")
				.VALUES("FROM_DATE", "?");
		List<OWLNamedIndividual> types2 = new ArrayList<OWLNamedIndividual>();
		types2.add(individual(Concepts.INTEGER));
		types2.add(individual(Concepts.INTEGER));
		types2.add(individual(Concepts.INTEGER));
		types2.add(individual(Concepts.TIMESTAMP));
		for (OWLNamedIndividual i : individuals)
		{
			Long s = identifiers.get(i).getFirst();
			List<Object> parameters = new ArrayList<Object>();
			parameters.add(time);
			parameters.add(s);
			Statement statement = new Statement();
			statement.setSql(update);
			statement.setParameters(parameters);
			statement.setTypes(types);
			updates.add(statement);
		}
		for (OWLObjectPropertyAssertionAxiom axiom : axioms)
		{
			Long s = identifiers.get(axiom.getSubject()).getFirst();
			OWLObjectPropertyExpression expr = axiom.getProperty();
			if (expr instanceof OWLObjectProperty)
			{
				Long p = identifiers.get((OWLObjectProperty) expr).getFirst();
				Long o = identifiers.get(axiom.getObject()).getFirst();
				List<Object> parameters = new ArrayList<Object>();
				parameters.add(s);
				parameters.add(p);
				parameters.add(o);
				parameters.add(time);
				Statement statement = new Statement();
				statement.setSql(insert);
				statement.setParameters(parameters);
				statement.setTypes(types2);
				inserts.add(statement);
			}
		}
		return Collections.singletonMap(updates, inserts);
	}

	// -------------------------------------------------------------------------
	// HELPERS FOR MERGE ONLY
	//

	/**
	 * Returns all DataPropertyAssertionAxioms, who do no have a mapped column
	 * in the MAPPED schema for their literal objects. This is determined, by
	 * first looking, if the subject is mapped and if it does, checking for a
	 * column mapping. TODO this method should be moved to another class.
	 */
	public Set<OWLDataPropertyAssertionAxiom> getNotMappedDataPropertyAxioms(
			OWLOntology ontology)
	{
		Set<OWLDataPropertyAssertionAxiom> allAxioms = new HashSet<OWLDataPropertyAssertionAxiom>(
				ontology.getAxioms(AxiomType.DATA_PROPERTY_ASSERTION));
		Iterator<OWLDataPropertyAssertionAxiom> it = allAxioms.iterator();
		while (it.hasNext())
		{
			OWLDataPropertyAssertionAxiom curAxiom = it.next();
			OWLNamedIndividual subject = (OWLNamedIndividual) curAxiom
					.getSubject();
			OWLNamedIndividual table = RelationalOWLMapper.table(subject
					.getTypes(ontology));
			if (table != null)
			{
				Map<OWLProperty<?, ?>, OWLNamedIndividual> columnMapping = RelationalOWLMapper
						.columnMapping(table);
				if (columnMapping.containsKey(curAxiom.getProperty()))
				{
					it.remove();
				} else
				{
					OWLLiteral axiomLiteral = curAxiom.getObject();
					if (axiomLiteral.getLiteral() == null
							|| axiomLiteral.getLiteral().isEmpty())
					{
						System.err
								.println("getNotMappedDataPropertyAxioms: Ignoring not mapped axiom with null or empty literal: "
										+ curAxiom);
						it.remove();
					}
				}

			}
		}
		return allAxioms;
	}

	/**
	 * Returns all ObjectPropertyAxioms, for whose ObjectProperty no
	 * columnMapping, and no hasOne and no hasMany exists.
	 * 
	 * @param ontology
	 * @return
	 */
	public Set<OWLObjectPropertyAssertionAxiom> getNotMappedObjectPropertyAxioms(
			OWLOntology ontology)
	{
		Set<OWLObjectPropertyAssertionAxiom> allAxioms = new HashSet<OWLObjectPropertyAssertionAxiom>(
				ontology.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION));
		Iterator<OWLObjectPropertyAssertionAxiom> it = allAxioms.iterator();
		while (it.hasNext())
		{
			OWLObjectPropertyAssertionAxiom curAxiom = it.next();
			OWLNamedIndividual subject = (OWLNamedIndividual) curAxiom
					.getSubject();
			OWLObjectPropertyExpression propertyExpression = curAxiom
					.getProperty();
			Set<OWLClassExpression> subjectClasses = subject.getTypes(ontology);
			OWLNamedIndividual table = RelationalOWLMapper
					.table(subjectClasses);
			if (table != null)
			{
				Map<OWLProperty<?, ?>, OWLNamedIndividual> columnMapping = RelationalOWLMapper
						.columnMapping(table);
				Set<OWLNamedIndividual> hasOneTables = null;
				OWLClass hasManyMapping = null;
				if (propertyExpression instanceof OWLObjectProperty)
				{
					OWLObjectProperty property = propertyExpression
							.asOWLObjectProperty();
					hasOneTables = RelationalOWLMapper
							.hasOne2TablesByProperty(property);
					hasManyMapping = RelationalOWLMapper.hasMany(property);
				}
				if (columnMapping.containsKey(curAxiom.getProperty())
						|| hasOneTables != null || hasManyMapping != null)
				{
					it.remove();
				}
			}
		}
		return allAxioms;
	}

	// /**
	// * Returns all ClassAssertionAxioms, for whose individuals no tableMapping
	// exists.
	// *
	// * @param ontology
	// * @return
	// */
	// public Set<OWLClassAssertionAxiom>
	// getNotMappedClassAssertionAxioms(OWLOntology ontology)
	// {
	// Set<OWLClassAssertionAxiom> allAxioms =
	// ontology.getAxioms(AxiomType.CLASS_ASSERTION);
	// Iterator<OWLClassAssertionAxiom> it = allAxioms.iterator();
	// while (it.hasNext()) {
	// OWLClassAssertionAxiom curAxiom = it.next();
	// OWLNamedIndividual individual =
	// (OWLNamedIndividual)curAxiom.getIndividual();
	// OWLNamedIndividual table =
	// RelationalOWLMapper.table(individual.getTypes(ontology));
	// if (table != null) {
	// it.remove();
	// }
	// }
	// return allAxioms;
	// }

	/**
	 * Returns a map of literal-->ID entries for all given DataPropertyAxiom.
	 * 
	 * @param literalAxioms
	 * @param insertIfMissing
	 * @return A map of OWLLiteral to value ID (Datatype ID is selected earlier)
	 */
	public Map<Object, Long> selectLiteralValueIDsFromAxioms(
			Set<OWLDataPropertyAssertionAxiom> literalAxioms,
			boolean insertIfMissing)
	{
		Set<OWLLiteral> literals = new HashSet<OWLLiteral>();
		for (OWLDataPropertyAssertionAxiom axiom : literalAxioms)
		{
			OWLLiteral literal = axiom.getObject();
			if (literal.getLiteral() == null || literal.getLiteral().isEmpty())
			{
				System.err
						.println("selectLiteralValueIDsFromAxioms: Ignoring null or empty literal in axiom: "
								+ axiom);
				continue;
			}
			literals.add(literal);
		}
		return selectLiteralValueIDs(literals, insertIfMissing);
	}

	public Map<Object, Long> selectLiteralValueIDs(Set<OWLLiteral> literals,
			boolean insertIfMissing)
	{
		Map<Object, Long> resultAllTables = new HashMap<Object, Long>();
		Map<String, Set<Object>> tableToLiteralValues = classifyLiteralValuesToTables(literals);
		for (Map.Entry<String, Set<Object>> tableToLiteralEntry : tableToLiteralValues
				.entrySet())
		{
			String table = tableToLiteralEntry.getKey();
			Set<Object> values = tableToLiteralEntry.getValue();
			Map<Object, Long> resultOneTable = selectLiteralValueIDsInternal(
					table, values);
			resultAllTables.putAll(resultOneTable);
			if (resultOneTable.size() < values.size() && insertIfMissing)
			{
				Set<Object> valuesToInsert = new HashSet<Object>(values);
				valuesToInsert.removeAll(resultOneTable.keySet());
				insertLiteralValues(table, valuesToInsert);
				Map<Object, Long> selectInserted = selectLiteralValueIDsInternal(
						table, valuesToInsert);
				if (selectInserted.size() != valuesToInsert.size())
				{
					System.err
							.println("Oracle bug inconsistency in selectLiteralValueIDs. Will retry. Inserted "
									+ valuesToInsert.size()
									+ " literal values. "
									+ " But on select only "
									+ selectInserted.size() + " were found. ");
					throw new RuntimeException(
							new SQLException(
									"Oracle Select after Insert bug detected. Emulating cannot serialize to cause retry of whole transaction.",
									"Should close connection and retry", 8177));
				}
				resultAllTables.putAll(selectInserted);
			}
		}
		return resultAllTables;
	}

	/**
	 * Returns the data value table (e.g. DATA_VAL_CLOB) for a sql type.
	 * 
	 * @param type
	 *            as defined in Concepts.
	 * @return
	 */
	private String getTableForSqlType(OWLNamedIndividual type)
	{
		String table;
		if (type.getIRI().toString().equals(Concepts.CLOB))
		{
			table = TABLE_DATA_VALUE_CLOB;
		} else if (type.getIRI().toString().equals(Concepts.TIMESTAMP))
		{
			table = TABLE_DATA_VALUE_DATE;
		} else if (type.getIRI().toString().equals(Concepts.DOUBLE))
		{
			table = TABLE_DATA_VALUE_DOUBLE;
		} else if (type.getIRI().toString().equals(Concepts.INTEGER))
		{
			table = TABLE_DATA_VALUE_INTEGER;
		} else if (type.getIRI().toString().equals(Concepts.VARCHAR))
		{
			table = TABLE_DATA_VALUE_STRING;
		} else
		{
			throw new IllegalArgumentException("Not recognized: " + type);
		}
		return table;
	}

	/**
	 * Maps Indiviual sql values of literals to tables.
	 * 
	 * @param literals
	 * @return
	 */
	private Map<String, Set<Object>> classifyLiteralValuesToTables(
			Set<OWLLiteral> literals)
	{
		Map<String, Set<Object>> tablesToLiteralValues = new HashMap<String, Set<Object>>();
		for (OWLLiteral curLiteral : literals)
		{
			String table = getTableForLiteral(curLiteral);
			Set<Object> literalValuesByTableSet = tablesToLiteralValues
					.get(table);
			if (literalValuesByTableSet == null)
			{
				literalValuesByTableSet = new HashSet<Object>(
						literals.size() * 2);
				tablesToLiteralValues.put(table, literalValuesByTableSet);
			}
			if (curLiteral.getLiteral() == null
					|| curLiteral.getLiteral().isEmpty())
			{
				System.err
						.println("classifyLiteralValuesToTables: ignoring null or empty literal "
								+ curLiteral + " table was: " + table);
			} else
			{
				literalValuesByTableSet.add(getLiteralSqlValue(table,
						curLiteral));
			}
		}
		return tablesToLiteralValues;
	}

	/**
	 * returns the table as mapped by looking at the literals datatype and in
	 * case of a String, the length of the String.
	 * 
	 * @param literal
	 * @return
	 */
	private String getTableForLiteral(OWLLiteral literal)
	{
		OWL2Datatype o2dt = literal.getDatatype().getBuiltInDatatype();
		OWLNamedIndividual type = RelationalOWLMapper.hasTypeMapping(o2dt);
		if (type == null)
		{
			throw new IllegalArgumentException(
					"No TypeMapping found in Meta for datatype: "
							+ literal.getDatatype() + " Literal was: "
							+ literal);
		}
		if (type.getIRI().toString().equals(Concepts.VARCHAR)
				&& literal.getLiteral().length() >= VALUE_VARCHAR_SIZE)
		{
			type = Concepts.CLOB_Individual;
		}
		return getTableForSqlType(type);
	}

	// private Map<Object, Long> selectLiteralValueIDsInternal(Map<String,
	// Set<Object>> tableToValues)
	// {
	// Map<Object, Long> result = new HashMap<Object, Long>(100);
	// for (Map.Entry<String, Set<Object>> tableEntry: tableToValues.entrySet())
	// {
	// String table = tableEntry.getKey();
	// Set<Object> values = tableEntry.getValue();
	// result.putAll(selectLiteralValueIDsInternal(table,values));
	// }
	// return result;
	// }
	//
	// /**
	// * Creates a Map categorizing all literals of equal value.
	// * @param table
	// * @param literals
	// * @return a map from value -> set of literals with equal value
	// */
	// private Map<Object, Set<OWLLiteral>> getLiteralsByValue(String table,
	// Set<OWLLiteral> literals) {
	// Map<Object, Set<OWLLiteral>> valueToLiterals = new HashMap<Object,
	// Set<OWLLiteral>>(literals.size() + 13);
	// for (OWLLiteral literal : literals) {
	// Object value = getLiteralSqlValue(table, literal);
	// Set<OWLLiteral> literalsEqualValue = valueToLiterals.get(value);
	// if (literalsEqualValue == null) {
	// literalsEqualValue = new HashSet<OWLLiteral>();
	// valueToLiterals.put(value, literalsEqualValue);
	// }
	// literalsEqualValue.add(literal);
	// }
	// return valueToLiterals;
	// }

	/**
	 * returns the value that is needed to find a literal in the database. This
	 * is equal to the storage value, except for CLOBs, where a hash value is
	 * returned.
	 * 
	 * @param literal
	 * @return
	 */
	private Object getLiteralSqlValue(OWLLiteral literal)
	{
		String table = getTableForLiteral(literal);
		return getLiteralSqlValue(table, literal);
	}

	/**
	 * Here the allowed java value TYPES are defined: {String[2], String,
	 * sql.TimeStamp, Double, Integer (includes Boolean 1 or 0) }
	 * 
	 * @param table
	 * @param literal
	 * @return a String[2] {hashcode, String} for CLOB table, a String, a
	 *         TimeStamp a Double or an Integer Object.
	 */
	private Object getLiteralSqlValue(String table, OWLLiteral literal)
	{
		if (table.equals(TABLE_DATA_VALUE_CLOB))
		{
			return Collections
					.singletonMap(hash(literal), literal.getLiteral());
		} else if (table.equals(TABLE_DATA_VALUE_STRING))
		{
			return literal.getLiteral();
		} else if (table.equals(TABLE_DATA_VALUE_DATE))
		{
			return new Timestamp(parseDate(literal).getTime());
		} else if (table.equals(TABLE_DATA_VALUE_DOUBLE))
		{
			// TODO float might be needed too.
			return literal.parseDouble();
		} else if (table.equals(TABLE_DATA_VALUE_INTEGER))
		{
			long intValue;
			if (literal.isBoolean())
			{
				intValue = literal.parseBoolean() ? 1 : 0;
			} else
			{
				intValue = Long.parseLong(literal.getLiteral());
			}
			return intValue;
		} else
		{
			throw new IllegalArgumentException("Table not recognized: " + table);
		}
	}

	/**
	 * Loads
	 * 
	 * @param table
	 * @param valueToLiterals
	 *            a map from individual values to literals.
	 * @return
	 */
	private Map<Object, Long> selectLiteralValueIDsInternal(String table,
			Set<Object> values)
	{
		if (values.size() == 0)
			return Collections.emptyMap();
		Map<Object, Long> result = new HashMap<Object, Long>(
				values.size() * 2 + 1);
		PreparedStatement stmt = null;
		Connection conn = null;
		ResultSet rs = null;
		StringBuilder select = new StringBuilder(250);
		select.append("SELECT ID");
		if (table.equals(TABLE_DATA_VALUE_CLOB))
		{
			// select.append(",VALUE_HASH AS CVALUE");
			select.append(" FROM ").append(table)
					.append(" WHERE VALUE_HASH = ? ");
		} else if (table.equals(TABLE_DATA_VALUE_STRING))
		{
			// select.append(",VALUE_VARCHAR AS CVALUE");
			select.append(" FROM ").append(table)
					.append(" WHERE VALUE_VARCHAR = ?");
		} else if (table.equals(TABLE_DATA_VALUE_DATE))
		{
			// select.append(",VALUE_DATE AS CVALUE");
			select.append(" FROM ").append(table)
					.append(" WHERE VALUE_DATE = ? ");
		} else if (table.equals(TABLE_DATA_VALUE_DOUBLE))
		{
			// select.append(",VALUE_DOUBLE AS CVALUE");
			select.append(" FROM ").append(table)
					.append(" WHERE VALUE_DOUBLE = ? ");
		} else if (table.equals(TABLE_DATA_VALUE_INTEGER))
		{
			// select.append(",VALUE_INTEGER AS CVALUE");
			select.append(" FROM ").append(table)
					.append(" WHERE VALUE_INTEGER = ? ");
		} else
		{
			throw new IllegalArgumentException("Table " + table
					+ " not a valid data value table");
		}
		// 1. Prepare Statement Parameters for current page
		StringBuilder sql = new StringBuilder(select);
		// whereClause.deleteCharAt(select.lastIndexOf(",")).append(")");
		try
		{
			conn = getConnection();
			stmt = conn.prepareStatement(sql.toString());
			for (Object value : values)
			{
				if (value instanceof Map)
				{
					// CLOB case, hash value is index 0
					@SuppressWarnings("unchecked")
					Map.Entry<String, String> hashToValueStr = ((Map<String, String>) value)
							.entrySet().iterator().next();
					stmt.setObject(1, (hashToValueStr.getKey()));
				} else
				{
					stmt.setObject(1, value);
				}
				rs = stmt.executeQuery();
				// 2. Execute Statement and load value IDs.
				if (rs.next())
					result.put(value, rs.getLong(1));
				if (rs.next())
					System.err
							.println(" new IllegalStateException(Found more than one IDs for Value: "
									+ value
									+ " one id is: "
									+ rs.getLong(1)
									+ " table was: " + table);
			}
			close(rs, stmt);
			conn.commit();
		} catch (SQLException e)
		{
			rollback(conn);
			throw new RuntimeException(e);
		} finally
		{
			close(rs, stmt, conn);
		}
		return result;
	}

	// /**
	// *
	// * @param table
	// * @param values
	// * @return
	// * @deprecated
	// */
	// private Map<Object, Long> selectLiteralValueIDsInternalOlD(String table,
	// Set<Object> values)
	// {
	// if (values.size() == 0)
	// return Collections.emptyMap();
	// Map<Object, Long> result = new HashMap<Object, Long>((100));
	// PreparedStatement stmt = null;
	// Connection conn = null;
	// ResultSet rs = null;
	// StringBuilder select = new StringBuilder(100);
	// //OWLDataFactory factory = MetaService.get().getDataFactory();
	// //int pageSize = 1000;
	// //Map<Object, Set<OWLLiteral>> valueToLiterals =
	// getLiteralsByValue(table, literals);
	// int pageCount = 1;
	// if (values.size() > MAX_INCLAUSE_SIZE)
	// pageCount = (values.size() + MAX_INCLAUSE_SIZE - 1) / MAX_INCLAUSE_SIZE;
	// try
	// {
	// conn = getConnection();
	// select.append("SELECT ID");
	// if(table.equals(TABLE_DATA_VALUE_CLOB)){
	// select.append(",VALUE_HASH AS CVALUE");
	// select.append(" FROM ").append(table).append(" WHERE VALUE_HASH IN (");
	// }else if(table.equals(TABLE_DATA_VALUE_STRING)){
	// select.append(",VALUE_VARCHAR AS CVALUE");
	// select.append(" FROM ").append(table).append(" WHERE VALUE_VARCHAR IN (");
	// }else if(table.equals(TABLE_DATA_VALUE_DATE)){
	// select.append(",VALUE_DATE AS CVALUE");
	// select.append(" FROM ").append(table).append(" WHERE VALUE_DATE IN (");
	// }else if(table.equals(TABLE_DATA_VALUE_DOUBLE)){
	// select.append(",VALUE_DOUBLE AS CVALUE");
	// select.append(" FROM ").append(table).append(" WHERE VALUE_DOUBLE IN (");
	// }else if(table.equals(TABLE_DATA_VALUE_INTEGER)){
	// select.append(",VALUE_INTEGER AS CVALUE");
	// select.append(" FROM ").append(table).append(" WHERE VALUE_INTEGER IN (");
	// }else{
	// throw new IllegalArgumentException("Table " + table +
	// " not a valid data value table");
	// }
	// Iterator<Object> valueIterator = values.iterator();
	// for (int g = 0; g < pageCount; g++)
	// {
	// // 1. Prepare Statement Parameters for current page
	// StringBuilder sql = new StringBuilder(select);
	// for (int i = (g * MAX_INCLAUSE_SIZE); i < ((g + 1) * MAX_INCLAUSE_SIZE) -
	// 1 && i < values.size() - 1; i++)
	// sql.append("?,");
	// sql.append("?)");
	// //whereClause.deleteCharAt(select.lastIndexOf(",")).append(")");
	// stmt = conn.prepareStatement(sql.toString());
	// int j = 1;
	// for (int i = (g * MAX_INCLAUSE_SIZE); i < (g + 1) * MAX_INCLAUSE_SIZE &&
	// i < values.size(); i++)
	// {
	// Object value = valueIterator.next();
	// if (value instanceof Map) {
	// //CLOB case, hash value is index 0
	// Map.Entry<String,String> hashToValueStr = ((Map<String,
	// String>)value).entrySet().iterator().next();
	// stmt.setObject(j, (hashToValueStr.getKey()));
	// } else {
	// //TODO hilpold WITH SYED: make sure, that a value date will be found:
	// //TODO test precision, et.c. we seem to have problems with not finding,
	// but running into constraint violations on insert!
	// //TODO test targetSqlType, et.c. we seem to have problems with not
	// finding timestamps, but running into constraint violations on insert!
	// stmt.setObject(j, value);
	// }
	// j++;
	// }
	// // 2. Execute Statement and load value IDs.
	// rs = stmt.executeQuery();
	// while (rs.next())
	// {
	// readLiteralValueIDInto(table, rs, result, values);
	// }
	// close(rs, stmt);
	// conn.commit();
	// }
	// }
	// catch (SQLException e)
	// {
	// rollback(conn);
	// throw new RuntimeException(e);
	// }
	// finally
	// {
	// close(rs, stmt, conn);
	// }
	// return result;
	// }

	// /**
	// *
	// * @param table
	// * @param rs
	// * @param result
	// * @param values
	// * @throws SQLException
	// * @deprecated
	// */
	// private void readLiteralValueIDInto(String table, ResultSet rs,
	// Map<Object, Long> result, Set<Object> values) throws SQLException
	// {
	// long id = rs.getLong("ID");
	// Object value;
	// if(table.equals(TABLE_DATA_VALUE_CLOB)){
	// String hashValue = rs.getString("CVALUE");
	// Map<String,String> clobStringValueMap = findClobStringMap(values,
	// hashValue);
	// // Insert the lookup String[] consisting of hashCode and original already
	// in memory string
	// // instead of the loaded value. We need the String[] comparison later to
	// determine, which clob values
	// // needs to be inserted. See callers of this method.
	// if (clobStringValueMap == null) {
	// throw new IllegalStateException("Could not find a CLOB value for hash: "
	// + hashValue + " in given values. Size: " + values.size());
	// }
	// value = clobStringValueMap;
	// }else if(table.equals(TABLE_DATA_VALUE_STRING)){
	// value = rs.getString("CVALUE");
	// }else if(table.equals(TABLE_DATA_VALUE_DATE)){
	// value = rs.getTimestamp("CVALUE");
	// }else if(table.equals(TABLE_DATA_VALUE_DOUBLE)){
	// value = rs.getDouble("CVALUE");
	// }else if(table.equals(TABLE_DATA_VALUE_INTEGER)){
	// value = rs.getLong("CVALUE");
	// } else {
	// throw new IllegalArgumentException("Table not recognized: " + table);
	// }
	// if (rs.wasNull()) {
	// throw new
	// IllegalStateException("Resultset returned a null value for a Literal Value. Value ID: "
	// + id + " Table: " + table);
	// }
	// result.put(value, id);
	// }

	/**
	 * Assumes the values to contain String[2] (clobValues) elements and looks
	 * for a match on value[0].equals(hashCode).
	 * 
	 * @param values
	 * @param
	 * @return a singleton Map{hashCode -> value} or null if not found
	 */
	public Map<String, String> findClobStringMap(Set<Object> values,
			String hashCode)
	{
		for (Object value : values)
		{
			@SuppressWarnings("unchecked")
			Map<String, String> map = (Map<String, String>) value;
			Map.Entry<String, String> clobValue = map.entrySet().iterator()
					.next();
			if (clobValue.getKey() == null
					|| clobValue.getValue().length() <= VALUE_VARCHAR_SIZE)
			{
				throw new IllegalStateException(
						"Found bad clobValue array in values: " + clobValue);
			}
			if (clobValue.getKey().equals(hashCode))
			{
				return map;
			}
		}
		return null;
	}

	/**
	 * 
	 * @param table
	 * @param values
	 *            if table = CLOB, String[2] are expected
	 * @return
	 */
	public int[] insertLiteralValues(String table, Set<Object> values)
	{
		PreparedStatement stmt = null;
		Connection conn = null;
		int[] result = {};
		StringBuffer insert = new StringBuffer();
		insert.append("INSERT INTO ").append(table).append("(").append("ID");
		if (table.equals(TABLE_DATA_VALUE_CLOB))
		{
			insert.append(",VALUE_HASH, VALUE_VARCHAR, VALUE_CLOB ");
		} else if (table.equals(TABLE_DATA_VALUE_STRING))
		{
			insert.append(",VALUE_VARCHAR ");
		} else if (table.equals(TABLE_DATA_VALUE_DATE))
		{
			insert.append(",VALUE_DATE ");
		} else if (table.equals(TABLE_DATA_VALUE_DOUBLE))
		{
			insert.append(",VALUE_DOUBLE ");
		} else if (table.equals(TABLE_DATA_VALUE_INTEGER))
		{
			insert.append(",VALUE_INTEGER ");
		} else
		{
			throw new IllegalArgumentException("Table " + table
					+ " not a valid data value table");
		}
		insert.append(") VALUES ( ").append(dataSourceRef.getHook().nextSequenceClause(SEQUENCE)).append(","); 
		if (table.equals(TABLE_DATA_VALUE_CLOB))
		{
			// for _VARCHAR AND _CLOB
			insert.append("?,?,?)");
		} else
		{
			insert.append("?)");
		}
		try
		{
			conn = getConnection();
			stmt = conn.prepareStatement(insert.toString());
			for (Object value : values)
			{
				if (value == null
						|| (value instanceof String && ((String) value)
								.isEmpty()))
				{
					System.err
							.println("insertLiteralValues: Not inserting illegal null value or empty string for table "
									+ table);
					continue;
				}
				if (table.equals(TABLE_DATA_VALUE_CLOB))
				{
					@SuppressWarnings("unchecked")
					Map<String, String> clobValueMap = (Map<String, String>) value;
					Map.Entry<String, String> clobValueMapEntry = clobValueMap
							.entrySet().iterator().next();
					String hashValue = clobValueMapEntry.getKey(); //TODO check if hash exists!!! and how hash is calculated -> has collision is normal.					
					String longStringValue = clobValueMapEntry.getValue();
					stmt.setString(1, hashValue);
					if (longStringValue.length() > MAX_VARCHAR_SIZE)
					{
						// Clob case
						stmt.setNull(2, Types.VARCHAR);
						stmt.setClob(3, new StringReader(longStringValue));
					} else
					{
						stmt.setString(2, longStringValue);//TODO check if select method is correct for string
						stmt.setNull(3, Types.CLOB);
					}
				} else if (table.equals(TABLE_DATA_VALUE_STRING))
				{
					stmt.setString(1, (String) value); //TODO check if select method is correct for string
				} else if (table.equals(TABLE_DATA_VALUE_DATE))
				{
					stmt.setTimestamp(1, (Timestamp) value);
				} else if (table.equals(TABLE_DATA_VALUE_DOUBLE))
				{
					stmt.setDouble(1, (Double) value);
				} else if (table.equals(TABLE_DATA_VALUE_INTEGER))
				{
					stmt.setLong(1, (Long) value);
				} else
				{
					throw new IllegalArgumentException("Table " + table
							+ " not a valid data value table");
				}
				stmt.addBatch();
			}
			result = stmt.executeBatch();
			conn.commit();
		} catch (Exception e)
		{
			rollback(conn); //TODO print values!
			System.err.println("Error: RelationalStoreImpl::insertLiteralValues table was " + table + " values: ");
			print(values);
			throw new RuntimeException(e);
		} finally
		{
			close(stmt, conn);
		}
		return result;

	}

	private void print(Set<Object> objects) 
	{
		if (objects == null) 
		{
			System.err.println("objects set was null");
			return;
		}
		int i = 0;
		for (Object o : objects) 
		{
			System.err.print("" + i + " : ");
			if (o == null) 
			{
				System.err.println("null");
			} else 
			{
				System.err.println("Class: " + o.getClass().getSimpleName() + " Object: " + "'" + o.toString() + "'");
			}
			i++;
		}
	}
	/**
	 * Deletes objects from CIRM_IRI table including full history. Make sure, no
	 * other foreign keys refer to it before calling this method.
	 * 
	 * @param objects
	 * @return
	 */
	public int[] deleteIRIsWithHistory(Map<OWLEntity, Long> objects)
	{
		PreparedStatement stmt = null;
		Connection conn = null;

		int[] result = {};
		StringBuffer insert = new StringBuffer();
		insert.append("DELETE FROM ").append(TABLE_IRI).append(" WHERE ID = ?");
		try
		{
			conn = getConnection();
			conn.setAutoCommit(false);
			stmt = conn.prepareStatement(insert.toString());
			for (Map.Entry<OWLEntity, Long> object : objects.entrySet())
			{
				stmt.setLong(1, object.getValue());
				stmt.addBatch();
			}
			result = stmt.executeBatch();
			conn.commit();
		} catch (SQLException e)
		{
			rollback(conn);
			throw new RuntimeException(e);
		} finally
		{
			close(stmt, conn);
		}
		return result;
	}

	//
	// DB HELPERS MERGE ONLY
	//
	private void executeBatch(List<Statement> statements,
	                          Map<OWLEntity, DbId> identifiers, 
	                          Connection conn)
			throws SQLException
	{
		PreparedStatement stmt = null;
		String lastSqlStr = null;
		int lastParameterSize = -1;
		try
		{
			if (dbg())
			{
				System.out.println("executeBatch()");
				System.out.println(statements.get(0).getSql().SQL());
			}
			for (Statement s : statements)
			{
				if (stmt == null)
				{
					// will add parameters to first statement.
					stmt = prepareStatement(conn, s, identifiers);
				} 
				else
				{
					for (int i = 0; i < s.getParameters().size(); i++)
					{
						addParameter(stmt, s.getParameters().get(i), s
								.getTypes().get(i), i + 1, identifiers);
					}
				}
				stmt.addBatch();
				// Checking Assertions:
				String curSql = s.getSql().toString();
				if (lastSqlStr == null)
				{
					lastSqlStr = curSql;
				} else
				{
					if (!curSql.equals(lastSqlStr))
					{
						throw new IllegalStateException(
								"SQL in batch statement list does not match: last SQL: "
										+ lastSqlStr + " current SQL: "
										+ curSql);
					}
				}
				int curParameterSize = s.getParameters().size();
				if (lastParameterSize == -1)
				{
					lastParameterSize = curParameterSize;
				} else
				{
					if (curParameterSize != lastParameterSize)
					{
						throw new IllegalStateException(
								"Cur Parameter size in statement is different from last: last #: "
										+ lastParameterSize + " current #: "
										+ curParameterSize);
					}
				}
				if (curParameterSize != s.getTypes().size())
				{
					throw new IllegalStateException(
							"SQL Parameter size did not match types size: param #: "
									+ curParameterSize + " types #: "
									+ s.getTypes().size());
				}
			}
			stmt.executeBatch();
		} catch (SQLException e)
		{
			if (!canRetrySQL(e))
			{
				System.err.println("executeBatch Exception " + e.toString()
						+ "executing following statements: ");
				int i = 0;
				for (Statement s : statements)
				{
					System.err.println("" + i + " " + s.getSql().SQL());
					int j = 1;
					for (Object param : s.getParameters())
					{
						System.err.print("(" + j + "=" + param + ")");
						j++;
					}
					System.err.println();
					i++;
				}
			}
			throw e;
		} finally
		{
			// 2012.07.25 close was missing!!!
			close(stmt);
		}
	}

	// private void execute(List<Statement> statements, Map<OWLEntity, Long>
	// identifiers, Connection conn) throws SQLException
	// {
	// PreparedStatement stmt = null;
	// for (Statement s : statements)
	// {
	// stmt = prepareStatement(conn, s, identifiers);
	// stmt.executeUpdate();
	// }
	// }

	public Date getStoreTime()
	{
		return txn(new CirmTransaction<Date>()
		{
			@Override
			public Date call() throws Exception
			{
				return getStoreTimeInt();
			}
		});
	}
	/* (non-Javadoc)
	 * @see org.sharegov.cirm.rdb.RelationalStore#getStoreTime()
	 */
	//@Override
	Date getStoreTimeInt()
	{
		Connection conn = null;
		Date result;
		try
		{
			conn = getConnection();
			result = dataSourceRef.getHook().timeStamp(conn);
			conn.commit();
		} 
		catch (SQLException e)
		{
			rollback(conn);
			throw new RuntimeException(e);
		} 
		finally
		{
			DBU.close(conn, null, null);
		}
		return result;
	}

	//
	// GENERIC QUERY AND DEPENDENT METHODS
	//

	public Map<Long, OWLEntity> queryGetEntities(final Query query, final OWLDataFactory df)
			throws SQLException
	{
		return txn(new CirmTransaction<Map<Long, OWLEntity>>()
		{
			@Override
			public Map<Long, OWLEntity> call() throws Exception
			{
				return queryGetEntitiesInt(query, df);
			}
		});
	}

	public Map<Long, OWLEntity> queryGetEntitiesInt(Query query, OWLDataFactory df)
			throws SQLException
	{
		Connection conn = getConnection();
		Map<Long, OWLEntity> result;
		try
		{
			result = selectEntitiesByIDs(query(query, df), df);
			conn.commit();
			return result;
		} catch (SQLException e)
		{
			rollback(conn);
			throw e;
		} finally
		{
			close(conn);
		}
	}

	@Override
	public Json customSearch(final Query query) throws SQLException
	{
		return txn(new CirmTransaction<Json>()
		{
			@Override
			public Json call() throws Exception
			{
				return customSearchInt(query);
			}
		});
	}
	
	public Json customSearchInt(Query query) throws SQLException
	{
		PreparedStatement stmt = null;
		Connection conn = null;
		ResultSet rs = null;
		Json results = Json.array();
		try
		{
			conn = getConnection();
			stmt = prepareStatement(conn, query.getStatement(), null);
			rs = stmt.executeQuery();
			DateFormat df = new SimpleDateFormat("MM/dd/yyyy");

			ResultSetMetaData rsmd = rs.getMetaData();
			Json metaInfo = Json.object();
			for (int i = 1; i <= rsmd.getColumnCount(); i++)
			{
				metaInfo.set(rsmd.getColumnName(i), rsmd.getColumnType(i));
			}

			while (rs.next())
			{
				Json eachRow = Json.object();
				for (Entry<String, Json> property : metaInfo.asJsonMap()
						.entrySet())
				{
					String columnName = property.getKey();
					int columnType = property.getValue().asInteger();

					if (columnType == Types.VARCHAR)
						eachRow.set(columnName, rs.getString(columnName));
					if (columnType == Types.LONGVARCHAR)
						eachRow.set(columnName, rs.getString(columnName));
					if (columnType == Types.CLOB)
						eachRow.set(columnName, rs.getString(columnName));
					if (columnType == Types.NUMERIC)
					{
						int i = rs.getInt(columnName);
						eachRow.set(columnName, i != 0 ? Integer.toString(i) : "");
					}
					if (columnType == Types.TIMESTAMP)
					{
						Timestamp dts = rs.getTimestamp(columnName);
						eachRow.set(columnName, (dts != null) ? df.format(dts)
								: "");
					}
				}
				results.add(eachRow);
			}
			conn.commit();
		} catch (SQLException e)
		{
			rollback(conn);
			throw e;
		} finally
		{
			close(rs, stmt, conn);
		}
		return results;

	}

	@Override
	public Json advancedSearch(final Query query) throws SQLException
	{
		return txn(new CirmTransaction<Json>()
		{
			@Override
			public Json call() throws Exception
			{
				return advancedSearchInt(query);
			}
		});
	}

	public Json advancedSearchInt(Query query) throws SQLException
	{
		PreparedStatement stmt = null;
		Connection conn = null;
		ResultSet rs = null;
		Json results = Json.array();
		try
		{
			conn = getConnection();
			stmt = conn.prepareStatement(query.getStatement().getSql().SQL());
			rs = stmt.executeQuery();
			DateFormat df = new SimpleDateFormat("MM/dd/yyyy");

			ResultSetMetaData rsmd = rs.getMetaData();
			while (rs.next())
			{
				int boid = rs.getInt(1);
				String type = rs.getString(2);
				String fullAddress = rs.getString(3);
				int zip = rs.getInt(4);
				String city = rs.getString(5);
				String hasStatus = rs.getString(6);
				Timestamp lastActDate = rs.getTimestamp(7);
				Timestamp createdDate = rs.getTimestamp(8);
				//String hasUserFriendlyID = rs.getString(9);
				String hasCaseNumber = rs.getString(9);
				String unit = rs.getString(10);
				String gisColumn = null;
				if (rsmd.getColumnCount() == 11)
					gisColumn = rs.getString(11);
				Json single = Json.object();
				single.set("boid", boid);
				single.set("type", (type != null) ? fullIri(type).getFragment()
						: "");
				single.set("fullAddress", (fullAddress != null) ? fullAddress
						: "");
				if(unit != null)
					single.set("fullAddress", 
							single.at("fullAddress").asString()+" #"+unit);
				single.set("Zip_Code", (zip != 0) ? zip : "");
				single.set("Street_Address_City", (city != null) ? city : "");
				single.set("hasStatus", (hasStatus != null) ? hasStatus : "");
				single.set("lastActivityUpdatedDate",
						(lastActDate != null) ? df.format(lastActDate) : "");
				single.set("hasDateCreated",
						(createdDate != null) ? df.format(createdDate) : "");
				//single.set("hasUserFriendlyID",
						//(hasUserFriendlyID != null) ? hasUserFriendlyID : "");
				single.set("hasCaseNumber",
						(hasCaseNumber != null) ? hasCaseNumber : "");
				single.set("gisColumn", (gisColumn != null) ? gisColumn : "");
				results.add(single);

			}
			conn.commit();
		} catch (SQLException e)
		{
			rollback(conn);
			throw e;
		} finally
		{
			close(rs, stmt, conn);
		}
		return results;
	}

	@Override
	public LinkedHashSet<Long> query(final Query query, final OWLDataFactory df)
	{
		return txn(new CirmTransaction<LinkedHashSet<Long>>()
		{
			@Override
			public LinkedHashSet<Long>call() throws Exception
			{
				return queryInt(query, df);
			}
		});
	}

	public LinkedHashSet<Long> queryInt(Query query, OWLDataFactory df)
	{
		PreparedStatement stmt = null;
		Connection conn = null;
		ResultSet rs = null;
		LinkedHashSet<Long> result = new LinkedHashSet<Long>(300);
		try
		{
			conn = getConnection();
			Set<? extends OWLEntity> queryEntities = query.getEntities();
			if (queryEntities == null)
			{
				queryEntities = Collections.emptySet();
			}
			stmt = prepareStatement(conn, query.getStatement(),	selectIDsAndEntitiesByIRIs(queryEntities));
			rs = stmt.executeQuery();
			while (rs.next())
			{
				result.add(rs.getLong(1));
			}
			conn.commit();
		} 
		catch (SQLException e)
		{
			rollback(conn);
			throw new RuntimeException(e);
		} finally
		{
			close(rs, stmt, conn);
		}
		return result;
	}

	/**
	 * Selects all OWLEntities
	 * 
	 * @param ids
	 * @return
	 */
	public Map<Long, OWLEntity> selectEntitiesByIDs(Set<Long> ids,
			OWLDataFactory df)
	{
		Map<Long, OWLEntity> result = new LinkedHashMap<Long, OWLEntity>();
		PreparedStatement stmt = null;
		Connection conn = null;
		ResultSet rs = null;
		StringBuilder select = new StringBuilder();
		// OWLDataFactory dataFactory = OWL.dataFactory();
		if (ids == null || ids.size() == 0)
			return result;
		int pageSize = 1000; // oracle limits sql in() list to 1000 entries, see
								// ORA-01795, so paging technique used.
		int pageCount = 1;
		List<Long> idsAsList = new ArrayList<Long>(ids);
		// We expect sorting the ids to improve query processing by db.
		Collections.sort(idsAsList);
		if (idsAsList.size() > 1000)
			pageCount = (idsAsList.size() + pageSize - 1) / pageSize;
		try
		{
			conn = getConnection();
			for (int g = 0; g < pageCount; g++)
			{
				select.delete(0, select.length());
				// TODO maybe use IRI TABLE!!! FASTER?
				select.append("SELECT ID, IRI, IRI_TYPE FROM ")
						.append(VIEW_IRI).append(" WHERE ").append("ID IN (");
				for (int i = (g * pageSize); i < (g + 1) * pageSize
						&& i < idsAsList.size(); i++)
					select.append("?,");
				select.deleteCharAt(select.lastIndexOf(",")).append(")");
				stmt = conn.prepareStatement(select.toString());
				int j = 1;
				for (int i = (g * pageSize); i < (g + 1) * pageSize
						&& i < idsAsList.size(); i++)
					stmt.setLong(j++, idsAsList.get(i));
				rs = stmt.executeQuery();
				while (rs.next())
				{
					OWLEntity o = df.getOWLEntity(
							typeOf(rs.getString("IRI_TYPE")),
							IRI.create(rs.getString("IRI")));
					result.put(rs.getLong("ID"), o);
				}
			}
			conn.commit();
		} catch (SQLException e)
		{
			rollback(conn);
			throw new RuntimeException(e);
		} finally
		{
			close(rs, stmt, conn);
		}
		// >1 here just to avoid a log entry for the SR BO check.
		if (result.size() != ids.size() && ids.size() > 1)
		{
			System.err
					.println("selectEntitiesByIDs did not find all ids.size: "
							+ ids.size() + " result.size: " + result.size());
		}
		return result;
	}

	protected PreparedStatement prepareStatement(Connection conn, 
	                                             Statement s,
	                                             Map<OWLEntity, DbId> identifiers) throws SQLException
	{
		if (dbg())
			System.out.println(s.getSql().SQL());
		PreparedStatement ps = conn.prepareStatement(s.getSql().SQL());
		for (int i = 0; i < s.getParameters().size(); i++)
		{
			addParameter(ps, s.getParameters().get(i), s.getTypes().get(i), i + 1, identifiers);
		}
		if (dbg())
			System.out.println();
		return ps;
	}

	protected void addParameter(PreparedStatement ps, 
	                            Object value,
	                            OWLNamedIndividual type, 
	                            int index, 
	                            Map<OWLEntity, DbId> identifiers)
			throws SQLException
	{
		String t = type.getIRI().toString();
		Long id = null;
		if (value == null)
		{
			// This might not work for all database systems.
			ps.setObject(index, null);
		}
		else if (value instanceof DbId)
		{
		    Long lvalue = ((DbId)value).getFirst();
            if (t.equals(Concepts.VARCHAR))
                ps.setString(index, lvalue.toString());
            else if (t.equals(Concepts.INTEGER))
                ps.setLong(index, lvalue);		    
		}
		else if (value instanceof OWLLiteral)
		{
			// Needed for mapped schema only
			if (t.equals(Concepts.VARCHAR))
				ps.setString(index, ((OWLLiteral) value).getLiteral());
			else if (t.equals(Concepts.INTEGER))
			{
				OWLLiteral literal = (OWLLiteral) value;
				if (literal.isBoolean())
					ps.setLong(index, literal.parseBoolean() ? 1 : 0);
				else
					ps.setLong(index, literal.parseInteger());
			} else if (t.equals(Concepts.DOUBLE))
				ps.setDouble(index, ((OWLLiteral) value).parseDouble());
			else if (t.equals(Concepts.TIMESTAMP))
				ps.setTimestamp(index,
						new Timestamp(parseDate((OWLLiteral) value).getTime()));
			else if (t.equals(Concepts.CLOB))
				ps.setClob(index,
						new StringReader(((OWLLiteral) value).getLiteral()));
		} 
		else if (value instanceof Long)
		{
			if (t.equals(Concepts.VARCHAR))
				ps.setString(index, ((Long) value).toString());
			else if (t.equals(Concepts.INTEGER))
				ps.setLong(index, (Long) value);
		} 
		else if (value instanceof String)
		{
			if (t.equals(Concepts.VARCHAR))
				ps.setString(index, (String) value);
			else if (t.equals(Concepts.CLOB))
				ps.setClob(index, new StringReader((String) value));
			else if (t.equals(Concepts.TIMESTAMP))
				ps.setTimestamp(index, new Timestamp(parseDate((String) value)
						.getTime()));
			else if (t.equals(Concepts.DOUBLE))
				// TODO test this: ps.setBigDecimal(index, new
				// BigDecimal(((String) value)));
				ps.setDouble(index, Double.parseDouble((String) value));
			else if (t.equals(Concepts.INTEGER))
				ps.setLong(index, Long.parseLong((String) value));
		} else if (value instanceof Timestamp)
		{
			ps.setTimestamp(index, (Timestamp) value);
		} else if (value instanceof OWLEntity)
		{
			if (t.equals(Concepts.INTEGER))
			{
				id = identifiers.get(value).getFirst();
				ps.setLong(index, id);
			}
		} else
		{
			throw new IllegalArgumentException("Value Type not recognized"
					+ value + " class: " + value.getClass());
		}
		if (dbg())
		{
			System.out.println("[" + index + " = "
					+ ((id == null) ? value : id + "(" + value + ")") + "] ");
		}
	}

	// protected int getParameterSQLType(OWLNamedIndividual t) {
	// if (t.equals(Concepts.VARCHAR))
	// ps.setString(index, ((OWLLiteral) value).getLiteral());
	// else if (t.equals(Concepts.INTEGER))
	// {
	// OWLLiteral literal = (OWLLiteral) value;
	// if (literal.isBoolean())
	// ps.setLong(index, literal.parseBoolean() ? 1 : 0);
	// else
	// ps.setLong(index, literal.parseInteger());
	// }
	// else if (t.equals(Concepts.DOUBLE))
	// ps.setBigDecimal(index, new BigDecimal(((OWLLiteral)
	// value).getLiteral()));
	// else if (t.equals(Concepts.TIMESTAMP))
	// ps.setTimestamp(index, new Timestamp(parseDate((OWLLiteral)
	// value).getTime()));
	// else if (t.equals(Concepts.CLOB))
	// ps.setClob(index, new StringReader(((OWLLiteral) value).getLiteral()));
	// }

	private Date parseDate(OWLLiteral value)
	{
		return parseDate(value.getLiteral());
	}

	private Date parseDate(String value)
	{
		Date result = new Date();
		try
		{
			// parse ISO 8601 date
			synchronized (xmlDatatypeFactory)
			{
				try
				{
					result = xmlDatatypeFactory.newXMLGregorianCalendar(value)
							.toGregorianCalendar().getTime();
				} catch (IllegalArgumentException t)
				{
					result = GenUtils.parseDate(value);
				}
			}
		} catch (Exception e)
		{
			logger.info("Could not parse date " + value + " as ISO 8601");
			throw new RuntimeException(e);
		}
		return result;
	}

	// -------------------------------------------------------------------------
	// OWL RELATED HELPERS
	//

	protected EntityType<?> typeOf(String s)
	{
		for (EntityType<?> type : EntityType.values())
			if (type.toString().equals(s))
				return type;
		throw new IllegalArgumentException("EntityType not recognized: " + s);
	}

	protected OWLLiteral literal(OWLDataFactory factory, ResultSet rs,
			OWL2Datatype d) throws SQLException
	{
		// TODO
		// here we could have the rs on a "0" varchar, a "0"integer or a "0"
		// double,
		// even though d == Boolean. This would break the algorithm.
		// There might be other cases, such as d == integer, rs on varchar or
		// double or others.
		//
		OWLNamedIndividual type = objectProperty(individual(d.getIRI()
				.getFragment()), "hasTypeMapping");
		if (type == null)
		{
			throw new IllegalStateException(
					"Exception while attempting to extract literal from ResultSet. Check if there is a hasTypeMapping for owl type "
							+ d.getIRI());
		}
		OWLLiteral result;
		if (VARCHAR.equals(type.getIRI().toString()))
		{
			String varcharValue = rs.getString("VALUE_VARCHAR");
			if (varcharValue == null)
			{
				varcharValue = rs.getString("VALUE_VARCHAR_LONG");
				if (varcharValue == null)
				{
					Clob clob = rs.getClob("VALUE_CLOB");
					if (rs.wasNull())
						throw new IllegalStateException(
								"Could read neither varchar or clob from the resultset for datatype "
										+ d);
					result = clobLiteral(factory, clob, d);
				} else
				{
					result = factory.getOWLLiteral(varcharValue, d);
				}
			} else
			{
				result = factory.getOWLLiteral(varcharValue, d);
			}
		} else if (CLOB.equals(type.getIRI().toString()))
		{
			Clob clob = rs.getClob("VALUE_CLOB");
			if (rs.wasNull())
				throw new IllegalStateException(
						"Could not read clob from the resultset for datatype "
								+ d);
			result = clobLiteral(factory, clob, d);
		} else if (TIMESTAMP.equals(type.getIRI().toString()))
		{
			result = dateLiteral(factory, rs.getTimestamp("VALUE_DATE"), d);
		} else if (DOUBLE.equals(type.getIRI().toString()))
		{
			double valueDouble = rs.getDouble("VALUE_DOUBLE");
			if (rs.wasNull())
				throw new IllegalStateException(
						"Null decimal from the resultset for datatype " + d);
			result = factory.getOWLLiteral("" + valueDouble, d);

		} else if (INTEGER.equals(type.getIRI().toString()))
		{
			long l = rs.getLong("VALUE_INTEGER");
			if (rs.wasNull())
				throw new IllegalStateException(
						"Null integer from the resultset for datatype " + d);
			if (d.equals(OWL2Datatype.XSD_BOOLEAN))
			{
				boolean value = (l == 1);
				if (!value && l != 0)
					throw new IllegalArgumentException(
							"Expected 0 for false, but read " + l
									+ "  from the resultset. Datatype was: "
									+ d);
				result = factory.getOWLLiteral(value);
			} else
				result = factory.getOWLLiteral("" + l, d);
		} else
		{
			// logger.warning("New type found but not yet implemented: OWL2Datatype "
			// + d + " type: " + type);
			throw new IllegalStateException(
					"New type found but not yet implemented: OWL2Datatype " + d
							+ " type: " + type);
		}
		return result;
	}

	// protected OWLLiteral literalTheOldWay(OWLDataFactory factory, ResultSet
	// rs, OWL2Datatype d)
	// {
	// //TODO
	// // here we could have the rs on a "0" varchar, a "0"integer or a "0"
	// double,
	// // even though d == Boolean. This would break the algorithm.
	// // There might be other cases, such as d == integer, rs on varchar or
	// double or others.
	// //
	// OWLNamedIndividual type =
	// objectProperty(individual(d.getIRI().getFragment()), "hasTypeMapping");
	// OWLLiteral result = null;
	// try
	// {
	// if (VARCHAR.equals(type.getIRI().toString()))
	// {
	// if (rs.getString("VALUE_AS_VARCHAR") == null)
	// result = clobLiteral(factory, rs.getClob("VALUE_AS_CLOB"), d);
	// else
	// result = factory.getOWLLiteral(rs.getString("VALUE_AS_VARCHAR"), d);
	// }
	// else if (CLOB.equals(type.getIRI().toString()))
	// {
	// result = clobLiteral(factory, rs.getClob("VALUE_AS_CLOB"), d);
	// }
	// else if (TIMESTAMP.equals(type.getIRI().toString()))
	// {
	// result = dateLiteral(factory, rs.getTimestamp("VALUE_AS_DATE"), d);
	// }
	// else if (DOUBLE.equals(type.getIRI().toString()))
	// {
	// String s = "";
	// if (rs.getBigDecimal("VALUE_AS_DOUBLE") != null)
	// s = rs.getBigDecimal("VALUE_AS_DOUBLE").toPlainString();
	// result = factory.getOWLLiteral(s, d);
	// }
	// else if (INTEGER.equals(type.getIRI().toString()))
	// {
	// long l = rs.getLong("VALUE_AS_INTEGER");
	// if (rs.wasNull()) {/* BIG TROUBLE */ };
	// if (d.equals(OWL2Datatype.XSD_BOOLEAN))
	// result = factory.getOWLLiteral(l == 1 ? true : false);
	// else
	// result = factory.getOWLLiteral(new Long(l).toString(), d);
	// }
	// else
	// {
	// result = factory.getOWLLiteral(
	// rs.getString("VALUE_AS_VARCHAR") == null ? "" :
	// rs.getString("VALUE_AS_VARCHAR"), d);
	// }
	// }
	// catch (Exception e)
	// {
	// e.printStackTrace();
	// System.out
	// .println("Exception while attempting to extract literal from ResultSet. Check if there is a hasTypeMapping for owl type "
	// + d.getIRI());
	// }
	// return result;
	// }

	private OWLLiteral clobLiteral(OWLDataFactory factory, Clob clob,
			OWL2Datatype d) throws SQLException
	{
		OWLLiteral result = factory.getOWLLiteral("", d);
		long len = clob.length();
		if (len < 1)
			throw new IllegalArgumentException(
					"Empty Clob read, database should not contain empty clob. Dataype was: "
							+ d);
		result = factory.getOWLLiteral(clob.getSubString(1, (int) len), d);
		return result;
	}

	/**
	 * Creates a literal for OWL2 XSD type DATE_TIME or DATE_TIME_STAMP
	 * 
	 * @param factory
	 * @param timestamp
	 *            must not be null/
	 * @param d
	 * @return
	 * @throws IllegalArgumentException
	 *             if timestamp
	 */
	protected OWLLiteral dateLiteral(OWLDataFactory factory,
			Timestamp timestamp, OWL2Datatype d)
	{
		OWLLiteral result;
		if (timestamp == null)
			throw new IllegalArgumentException(
					"Timestamp was null for datatype " + d);

		synchronized (xmlDatatypeFactory)
		{
			GregorianCalendar cal = (GregorianCalendar) Calendar.getInstance();
			cal.setTime(timestamp);
			result = factory.getOWLLiteral(xmlDatatypeFactory
					.newXMLGregorianCalendar(cal).toXMLFormat(), d);
		}
		return result;
	}

	// -------------------------------------------------------------------------
	// JDBC RELATED HELPERS
	//
	/**
	 * Tries to rollback the given connection. Null allowed. Exception printed.
	 */
	protected void rollback(Connection conn)
	{
		if (conn != null)
		{
			try
			{
				conn.rollback();
			} catch (SQLException e)
			{
				System.err.println("Exception while rolling back connection : "
						+ conn);
				e.printStackTrace(System.err);
			}
		}
	}

	protected void close(java.sql.Statement stmt)
	{
		close(null, stmt, null);
	}

	protected void close(Connection conn)
	{
		close(null, null, conn);
	}

	/**
	 * Tries to call close on each given database object.
	 */
	protected void close(java.sql.Statement stmt, Connection conn)
	{
		close(null, stmt, conn);
	}

	/**
	 * Tries to call close on each given database object.
	 */
	protected void close(ResultSet rs, java.sql.Statement stmt)
	{
		close(rs, stmt, null);
	}

	/**
	 * Tries to call close on each given database object. Prints a stacktrace,
	 * if a throwable is thrown, but continues closing others.
	 * 
	 * @param rs
	 *            null allowed.
	 * @param stmt
	 *            null allowed.
	 * @param conn
	 *            null allowed.
	 */
	protected void close(ResultSet rs, java.sql.Statement stmt, Connection conn)
	{
		if (rs != null)
			try
			{
				rs.close();
			} catch (Throwable t)
			{
				t.printStackTrace();
			}
		if (stmt != null)
			try
			{
				stmt.close();
			} catch (Throwable t)
			{
				t.printStackTrace();
			}
		if (conn != null)
			try
			{
				conn.close();
			} catch (Throwable t)
			{
				t.printStackTrace();
			}
	}

	/**
	 * Checks if a retry was already detected (RetryDetectedException) or
	 * if the exception is a retriable SQL exception. 
	 * @param t
	 * @return
	 */
	static boolean shouldRetry(Throwable t)
	{
		if (RetryDetectedException.isRetryDetectedOrCausedBy(t)) 
			return true;
		else
			return canRetrySQL(t);
	}

	/**
	 * Checks if a throwable is or contains a retriable SQL exception
	 *  
	 * @param t
	 * @return
	 */
	static boolean canRetrySQL(Throwable t)
	{
    	do 
    	{
    		if (t instanceof SQLException)
    		{
    			SQLException sqe = (SQLException)t;
    			do 
    			{
	    			int c = sqe.getErrorCode(); 
	    			if (c == 8177 || c == 8006)
	    				return true;
	    			sqe = sqe.getNextException();
    			} while (sqe != null);
    		}
    		t = t.getCause();
    	} while (t != null);
		return false;
	}
	
	/**
	 * Checks for ORACLE 8177 error.
	 * "ORA-08177: can't serialize access for this transaction"
	 * 
	 * @param e
	 * @return
	 */
//	private static boolean isCannotSerializeException(Throwable t)
//	{
//    	do 
//    	{
//    		if (t instanceof SQLException)
//    		{
//    			if (((SQLException) t).getErrorCode() == 8177)
//    				return true;
//    		}
//    		t = t.getCause();
//    	} while (t != null);
//		return false;
//	}


	// //
	// // Latest incoming changes from Phani and Syed
	// //
	// private boolean isDateDatatype(OWL2Datatype builtInDatatype)
	// {
	// return (builtInDatatype.equals(OWL2Datatype.XSD_DATE_TIME)
	// || builtInDatatype.equals(OWL2Datatype.XSD_DATE_TIME_STAMP));
	// }


	public List<Map<String, Object>> query(final Statement statement, final OWLDataFactory df) throws Exception // ,TODO:Create decorator/or
												// renderer argument//)
	{
		return txn(new CirmTransaction<List<Map<String, Object>>>()
		{
			@Override
			public List<Map<String, Object>> call() throws Exception
			{
				return queryInt(statement, df);
			}
		});
	}
	
	public List<Map<String, Object>> queryInt(Statement statement,
			OWLDataFactory df) throws Exception // ,TODO:Create decorator/or
												// renderer argument//)
	{
		PreparedStatement stmt = null;
		Connection conn = null;
		ResultSet rs = null;
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		Set<OWLEntity> entities = new HashSet<OWLEntity>();
		try
		{
			for (Object parameter : statement.getParameters())
			{
				if (parameter instanceof OWLEntity)
					entities.add((OWLEntity) parameter);
			}
			conn = getConnection();
			stmt = prepareStatement(conn, 
			                        statement,
			                        selectInsertIDsAndEntitiesByIRIs(entities, true));
			rs = stmt.executeQuery();
			while (rs.next())
			{
				Map<String, Object> columns = new HashMap<String, Object>();
				ResultSetMetaData rsmd = rs.getMetaData();
				for (int i = 1; i <= rsmd.getColumnCount(); i++)
				{
					Object obj = null;
					if (rsmd.getColumnType(i) == Types.TIMESTAMP)
					{
						Timestamp ts = rs.getTimestamp(i);
						if (ts != null)
							obj = ts.getTime();
					} else
						obj = rs.getObject(i);

					columns.put(rsmd.getColumnLabel(i), obj);
				}
				result.add(columns);
			}
			conn.commit();
		} catch (SQLException e)
		{
			rollback(conn);
			throw e;
		} finally
		{
			close(rs, stmt, conn);
		}
		return result;
	}
	
	/* (non-Javadoc)
	 * @see org.sharegov.cirm.rdb.RelationalStore#executeStatement(org.sharegov.cirm.rdb.Statement)
	 */
	//@Override
	public int executeStatement(final Statement statement) throws Exception // ,TODO:Create decorator/or
												// renderer argument//)
	{
		
		final Set<OWLEntity> entities = new HashSet<OWLEntity>();
		for (Object parameter : statement.getParameters())
		{
			if (parameter instanceof OWLEntity)
				entities.add((OWLEntity) parameter);
		}
		return txn(new CirmTransaction<Integer>() {
	            public Integer call() throws Exception
	            {
	            	PreparedStatement stmt = null;
            		Connection conn = null;
            		ResultSet rs = null;
	            	try
	        		{
	            		
	        					conn = getConnection();
								stmt = prepareStatement(conn, statement,
										selectInsertIDsAndEntitiesByIRIs(entities, true));
								//int i = 0;
								int i = stmt.executeUpdate();
								conn.commit();
								return i;
				            } catch (SQLException e)
				    		{
				    			rollback(conn);
				    			throw e;
				    		} finally
				    		{
				    			close(rs, stmt, conn);
				    		}
				}
	            
	        });
			
	}
		

	/* (non-Javadoc)
	 * @see org.sharegov.cirm.rdb.RelationalStore#txn(org.sharegov.cirm.CirmTransaction)
	 */
	@Override
	public <T> T txn(CirmTransaction<T> transaction)
	{
		//ThreadLocalConnection.hasThreadLocalConnection()
		T result;
		if (CirmTransaction.isExecutingOnThisThread()) 
		{	// toplevel txn already executing
			// check if the connection for the call is sublevel, if not a close was missed on a toplevel:
			if (TXN_CHECK_CONNECTION) checkNextConnectionToBeSublevel();
			CirmTransaction<?> toplevel = CirmTransaction.get();
			try
			{
				//Another top level transaction exist, use call() (and not execute()) to inherit blocking behaviour from top level 
				// and not replace the toplevel trans bound to the thread.
				// Check retry flag before and after execution of the subtransaction.
				// This will throw a RetryDetectedException to cease subtransaction execution up the stack
				// if it's already set.
				toplevel.checkRetryRequested();
				result = (T) transaction.call();
				toplevel.checkRetryRequested();
				return result;
			} catch (Exception e)
			{
				if (shouldRetry(e) && !toplevel.isRequestingRetry())
				{
					toplevel.requestRetry();
				}
				throw new RuntimeException(e);
			}
		}
		else
		{
			return (T) txnPrivate(transaction);
		}
	}

	private <T> T txnPrivate(CirmTransaction<T> transaction)
	{
		T result = null;
		int retryCount = 0;
		transaction.begin();
		try {
			do
			{
				ThreadLocalConnection conn = getConnection();
				if (!conn.isTopLevelMode()) {
					throw new RuntimeException("txnPrivate: Connection must be in toplevel mode. Commit/Close will fail on" + conn.getDirectConnection());
				}
				//This has to be a top level connection
				try
				{
					if (lockingStrategy.isLockRequired(transaction)	|| DBG_ALL_TRANSACTIONS_LOCK)
					{
						transaction.setAllowDBLock(true);
						if(DBGLOCK) ThreadLocalStopwatch.getWatch().time("LOCKS USED FOR " + transaction);
					}
					result = transaction.execute();
					if (TEST_TXN_ALWAYS_RETRY_TWICE && retryCount < 2)
						throw new RetryDetectedException("Test retry: " + retryCount);
					conn.commit();
					transaction.end(true);
				} 
				catch (Exception e)
				{
					rollback(conn);
					if (!shouldRetry(e))
					{	
						transaction.end(false);
						throw new RuntimeException(e);
					}
				} 
				finally
				{
					transaction.setAllowDBLock(false);
					close(conn);
				}
				if (!transaction.isSucceeded())
				{
					retryCount ++;
					ThreadLocalStopwatch.getWatch().time("RETRY Transaction " + retryCount + ". time");
					ThreadLocalStopwatch.getWatch().time("Transaction info: " 
							+ "Live(Concurrently executing): " + CirmTransaction.getNrOfExecutingTransactions()
							+ " Total: " + CirmTransaction.getNrOfTotalTransactions()
							+ " Failed: " + CirmTransaction.getNrOfFailedTransactions()
							+ " Cur executions: " + transaction.getExecutionCount());
					if (transaction.getTotalExecutionTimeSecs() > TXN_MAX_RETRY_MINUTES * 60) 
						throw new RuntimeException("TXN: Maximum retry time reached for transaction "
							+ "(" + TXN_MAX_RETRY_MINUTES + " minutes). Giving up after " + retryCount + " retries. ");
					//Sleep to avoid next collision
					preRetrySleep(transaction);
				}
			} 
			while (!transaction.isSucceeded());
			//Fire top level transaction event while transaction is still associated with thread.		
			transaction.getTransactionEventSupport().fireEvent(transaction);	
			return result;
		}
		finally 
		{
			if (!transaction.isEnded()) 
			{
					//Retry giving up -> FAIL
					transaction.end(false);				
			}
		}
	}
	
	/**
	 * Calculates a randomized sleeptime from 0-10sec dependend on factors in the transaction framework after 2 retries.
	 *  
	 * @param t
	 * @return
	 */
	public long preRetrySleep(CirmTransaction<?> t)
	{
		int executions = t.getExecutionCount();
		if (executions <= 3) return 0;
		//Time of current execution until failure
		double exSecs = t.getCurrentExecutionTimeSecs();
		if (exSecs == -1) exSecs = 0.01;
		//How many transacionts are currently executing in parallel?
		int concurrency = CirmTransaction.getNrOfExecutingTransactions();
		double random = Math.random();
		long sleepTimeMs = (long) (1000 * exSecs * concurrency * (executions - 3.0) * random / 2.0);
		if (sleepTimeMs > TXN_MAX_PRE_RETRY_SLEEP_SECS * 1000)
			sleepTimeMs = 1000 * (TXN_MAX_PRE_RETRY_SLEEP_SECS - 1) + (long)(2000 * random);
		try 
		{
			Thread.sleep(sleepTimeMs);
		} catch (InterruptedException e) {}
		if (DBG_PRE_RETRY_SLEEP)
		{
			System.out.println("Pre Retry Sleep " + sleepTimeMs + " ms");
		}
		return sleepTimeMs;
	}
	
	/**
	 * Tests the next connection for this thread is an inactive sublevel connection and fixes a stale toplevel connection by committing and closing it if necessary.
	 * If it is not, the toplevel connection will be committed and closed.
	 */
	private boolean checkNextConnectionToBeSublevel()
	{
		boolean isTopLevel;
		//Ensure connection not in toplevel mode:
		ThreadLocalConnection testConn = getConnection();
		isTopLevel = testConn.isTopLevelMode();
		if (isTopLevel)
		{
			System.err.println("TXN Problem: A pooled TL connection had a threadlocal and is toplevel. FIX codebase soon to close the connection properly.");
			System.err.println("We'll commit and close it now as a temporary fix.");
			System.err.println("FIX codebase soon! (We're committing unknown changes and repeatable exceptions will not be repeated.");
			try
			{
				testConn.commit();
			} catch (SQLException e)
			{
				e.printStackTrace();
			}
		}
		try
		{
			testConn.close();
		} catch (SQLException e1)
		{
			System.err.println("testConnectionToBeSublevel: ignored problem on close" + e1);
			//throw new RuntimeException(e1);
		}
		return !isTopLevel;
	}

}

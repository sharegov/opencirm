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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import mjson.Json;

import org.hypergraphdb.util.Pair;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.utils.DBGUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

public class RelationalOWLPersister
{
	public static boolean DBG = true;
	public static boolean DBG_AXIOMS = false;

	private IRI connectionInfo;
	private volatile RelationalStoreExt store = null;
	private static volatile RelationalOWLPersister instance = null;

	private volatile OntologyTransformer transformer = null;

	private final ConcurrentLinkedQueue<RDBListener> rdbListeners = new ConcurrentLinkedQueue<RDBListener>(); 
	private DataSourceRef dataSource = null;
	
	private void createStore()
	{
		if (connectionInfo == null)
			throw new RuntimeException(
					"No connection info meta data specified.");
//		OWLNamedIndividual info = OWL.individual(connectionInfo);
//		OWLNamedIndividual dbType = OWL.objectProperty(info, "hasDatabaseType");
//		String driverClassName = OWL.dataProperty(dbType, "hasDriver")
//				.getLiteral();
//		String url = OWL.dataProperty(info, "hasUrl").getLiteral();
//		String username = OWL.dataProperty(info, "hasUsername").getLiteral();
//		String password = OWL.dataProperty(info, "hasPassword").getLiteral();
		this.dataSource = new DataSourceRef(connectionInfo);
		store = new RelationalStoreExt(dataSource); // url, driverClassName, username, password);
	}

	public RelationalOWLPersister()
	{

	}

	/**
	 * ConnectionInfo is ignored after the instance is created.
	 * 
	 * @param connectionInfo
	 * @return
	 */
	public static RelationalOWLPersister getInstance(IRI connectionInfo)
	{
		if (instance == null)
		{
			synchronized (RelationalOWLPersister.class)
			{
				if (instance == null)
					instance = new RelationalOWLPersister(connectionInfo);
			}
		}
		return instance;
	}

	public DataSourceRef getDataSourceRef()
	{
	    return dataSource;
	}
	
	/**
	 * This publicly makes the store available. Maybe we should avoid this by
	 * extending the persister interface. The method is heavily used in this
	 * class also, I suspect, because of lazy initialization pattern
	 * (constructor does not create store) or because it was not clear when to
	 * create it. In support of this argument is the fact that only two store
	 * methods are extensively used publicly: selectIDsByIRI and
	 * selectEntitiesByIDs {@see LegacyEmulator, ActivityManager, DBIDFactory
	 * and some test classes: QueryTranslatorTest, OntologyChanger.} hilpoldQ
	 * 
	 * @return
	 */
	public RelationalStore getStore()
	{
		return getStoreExt();
	}
	
	public RelationalStoreExt getStoreExt()
	{
		if (store == null)
			synchronized (this) {
			if (store == null)
				createStore();
			}
		return store;
	}

	public RelationalOWLPersister(IRI connectionInfo)
	{
		this.connectionInfo = connectionInfo;
		createStore();
		transformer = Refs.ontologyTransformer.resolve();
	}

	public IRI getConnectionInfo()
	{
		return connectionInfo;
	}

	public void setConnectionInfo(IRI connectionInfo)
	{
		this.connectionInfo = connectionInfo;
	}

	// -------------------------------------------------------------------------
	// INDIVIDUAL METHODS
	//

	/**
	 * Reads an individual and all dependent individuals into the given ontology
	 * within a transaction.
	 * 
	 * @param on
	 *            an (empty) OWLntology with a manager set.
	 * @param ind
	 *            the named indiviual to read.
	 */
	public void readIndividualData(final OWLOntology on, final OWLNamedIndividual ind)
	{
		ThreadLocalStopwatch stopwatch = ThreadLocalStopwatch.getWatch();
		if (DBG)
			stopwatch.time("START readIndividualData(" + ind + ")");
		store.txn(new CirmTransaction<Object>()
		{
			@Override
			public Object call() throws Exception
			{
				store.readIndividualDataRecursive(on, ind);
				return null;
			}
		});
		transformer.reverseTransform(on);
		if (DBG)
			stopwatch.time("END readIndividualData(" + ind + ")");
		if (DBG_AXIOMS)
		{
			System.out.println(on);
			DBGUtils.printOntologyFunctional(on);
		}
	}

	// /**
	// * Searches for all entities by the IRI of the given individual and
	// * adds OWLObjectPropertyAssertionAxioms, OWLDataPropertyAssertionAxioms,
	// OWLClassAssertionAxiom the ontology with
	// * @param on an ontology to add axioms too.
	// * @param ind
	// * @return a set of dependent referenced individuals, which should be
	// loaded next or the empty set.
	// */
	// public Set<OWLNamedIndividual> readIndividualDataNotMapped(OWLOntology
	// on, OWLNamedIndividual ind)
	// {
	// OWLOntologyManager manager = MetaService.get().getManager();
	// ArrayList<OWLOntologyChange> changes = new
	// ArrayList<OWLOntologyChange>();
	// Set<OWLNamedIndividual> referencedIndividuals = Collections.emptySet();
	// //TRANSACTION START
	// ThreadLocalConnection conn = store.getConnection();
	// Set<OWLNamedIndividual> entities = Collections.singleton(ind);
	// try
	// {
	// Map<OWLEntity, Long> identifiers =
	// store.selectIDsAndEntitiesByIRIs(entities);
	//
	// Map<OWLObjectPropertyExpression, Set<OWLIndividual>> objProps =
	// store.selectObjectProperties(ind, identifiers);
	// for (Map.Entry<OWLObjectPropertyExpression, Set<OWLIndividual>> e :
	// objProps.entrySet())
	// {
	// for (OWLIndividual propValue : e.getValue())
	// {
	// changes.add(new AddAxiom(on,
	// manager.getOWLDataFactory().getOWLObjectPropertyAssertionAxiom(e.getKey(),
	// ind, propValue)));
	// if (referencedIndividuals.isEmpty()) {
	// referencedIndividuals = new HashSet<OWLNamedIndividual>();
	// }
	// referencedIndividuals.add((OWLNamedIndividual)propValue);
	// }
	// }
	//
	// Map<OWLDataPropertyExpression, Set<OWLLiteral>> dataProps =
	// getStore().selectDataProperties(ind, identifiers);
	// for (Map.Entry<OWLDataPropertyExpression, Set<OWLLiteral>> e :
	// dataProps.entrySet())
	// {
	// for (OWLLiteral propValue : e.getValue())
	// changes.add(new AddAxiom(on,
	// manager.getOWLDataFactory().getOWLDataPropertyAssertionAxiom(e.getKey(),
	// ind, propValue)));
	// }
	//
	// Set<OWLClass> classes = getStore().selectClass(ind, identifiers);
	// for (OWLClass c : classes)
	// {
	// changes.add(new AddAxiom(on,
	// manager.getOWLDataFactory().getOWLClassAssertionAxiom(c, ind)));
	// }
	// manager.applyChanges(changes);
	// return referencedIndividuals;
	// }
	// catch (RuntimeException e)
	// {
	// store.rollback(conn);
	// throw e;
	// }
	// finally
	// {
	// store.close(conn);
	// }
	// }

	// /**
	// *
	// * @param on
	// * @param ind
	// */
	// public void readIndividualDataMapped(OWLOntology onto, OWLNamedIndividual
	// ind) {
	// store.readIndividualDataMapped(onto, ind);
	// }

	/**
	 * Searches for all entities by the IRI of the given individual and adds
	 * OWLObjectPropertyAssertionAxioms, OWLDataPropertyAssertionAxioms,
	 * OWLClassAssertionAxiom the ontology that are valid at a given point in
	 * time.
	 * 
	 * @param on
	 *            the ontology to add the individual data to.
	 * @param individual
	 *            the named individual
	 * @param version
	 */
	public void readIndividualData(OWLOntology on,
			OWLNamedIndividual individual, Date version)
	{
		// TODO hilpold use THREAD SAFE MANAGER AND DATA FACTORY
		// Populates a given Ontology with the given version from database.
		OWLOntologyManager manager = on.getOWLOntologyManager(); // OWL.manager();
		OWLDataFactory df = manager.getOWLDataFactory();
		ArrayList<OWLOntologyChange> L = new ArrayList<OWLOntologyChange>();
		Set<OWLNamedIndividual> entities = new HashSet<OWLNamedIndividual>();
		entities.add(individual.asOWLNamedIndividual());
		ThreadLocalConnection conn = store.getConnection();
		try
		{
			Map<OWLEntity, DbId> identifiers = getStoreExt().selectIDsAndEntitiesByIRIs(entities);

			Map<OWLObjectPropertyExpression, Set<OWLIndividual>> objProps = getStoreExt()
					.selectObjectProperties(individual, version, identifiers, df);
			for (Map.Entry<OWLObjectPropertyExpression, Set<OWLIndividual>> e : objProps
					.entrySet())
			{
				for (OWLIndividual propValue : e.getValue())
				{
					L.add(new AddAxiom(on, manager.getOWLDataFactory()
							.getOWLObjectPropertyAssertionAxiom(e.getKey(),
									individual, propValue)));
					// 2012.04.12 hilpold was this a BUG? old was:
					// readIndividualData(on, propValue);
					readIndividualData(on, (OWLNamedIndividual) propValue,
							version);
				}
			}
			Map<OWLDataPropertyExpression, Set<OWLLiteral>> dataProps = getStoreExt()
					.selectDataProperties(individual, version, identifiers, df);
			for (Map.Entry<OWLDataPropertyExpression, Set<OWLLiteral>> e : dataProps
					.entrySet())
			{
				for (OWLLiteral propValue : e.getValue())
					L.add(new AddAxiom(on, manager.getOWLDataFactory()
							.getOWLDataPropertyAssertionAxiom(e.getKey(),
									individual, propValue)));
			}
			Set<OWLClass> classes = getStoreExt().selectClass(individual, version,
					identifiers, df);
			for (OWLClass c : classes)
			{
				L.add(new AddAxiom(on, manager.getOWLDataFactory()
						.getOWLClassAssertionAxiom(c, individual)));
			}
			manager.applyChanges(L);
			conn.commit();
		} 
		catch (Exception e)
		{
			store.rollback(conn);
			throw new RuntimeException(e);
		} finally
		{
			store.close(conn);
		}
		transformer.reverseTransform(on);
	}

	/**
	 * 
	 * @param ind
	 * @return
	 */
	public List<Date> readIndividualHistory(OWLIndividual ind)
	{
		Set<OWLEntity> entities = new HashSet<OWLEntity>();
		entities.add(ind.asOWLNamedIndividual());
		ThreadLocalConnection conn = store.getConnection();
		try
		{
			Map<OWLEntity, DbId> identifiers = getStoreExt().selectInsertIDsAndEntitiesByIRIs(entities, false);
			long ind_iri = identifiers.get(ind).getFirst();
			List<Date> dates = getStoreExt().selectIndividualHistory(ind_iri);
			conn.commit();
			return dates;
		} catch (Exception e)
		{
			store.rollback(conn);
			throw new RuntimeException(e);
		} finally
		{
			store.close(conn);
		}
	}

	// -------------------------------------------------------------------------
	// BUSINESS OBJECT ONTOLOGY METHODS
	//
	public void saveBusinessObjectOntology(final OWLOntology ontology)
	{
		ThreadLocalStopwatch stopwatch = ThreadLocalStopwatch.getWatch();
		if (DBG)
		{
			stopwatch.time("START saveBusinessObjectOntology("
					+ ontology.getOntologyID() + ") ");
		}
		final OWLOntology optimizedOntology = transformer.transform(ontology);
		store.txn(new CirmTransaction<Object>()
		{
			@Override
			public Object call() throws Exception
			{
				ThreadLocalConnection conn = store.getConnection();
				DbId boObj;
				OWLOntologyID id = ontology.getOntologyID();
				if (!id.isAnonymous())
				{
					boObj = ensureBusinessOntologyIndividual(ontology.getOntologyID(), conn);
				}
				else
					boObj = null;
				fireRDBSave(ontology);
				//TODO revmoc
				getStoreExt().merge(optimizedOntology, boObj);
				// Commit, rollback, close txn implicit
				return null;
			}
		});
		if (DBG)
		{
			stopwatch.time("END saveBusinessObjectOntology ");
		}
	}

	private DbId ensureBusinessOntologyIndividual(OWLOntologyID ontologyId, Connection conn) throws SQLException
	{
		OWLDataFactory df = Refs.tempOntoManager.resolve().getOWLDataFactory();
		IRI boIRI = ontologyId.getOntologyIRI().resolve("#bo");
		long id = OWL.parseIDFromBusinessOntologyIRI(boIRI);
		if (id < 0)
			throw new IllegalArgumentException(" parsed < 0 id from bo iri"
					+ ontologyId.getOntologyIRI());
		OWLEntity e = getStoreExt().selectEntityByID(id, df);
		if (e == null)
		{
			getStoreExt().insertIri(id, boIRI.toString(), "NamedIndividual", conn);
			return new DbId(id, df.getOWLNamedIndividual(boIRI), false);
		} 
		else
		{
			// 2013.02.25 hilpold supporting type change of a BO
			if (!e.getIRI().equals(boIRI))
				getStoreExt().updateIri(id, boIRI.toString(), conn);
			return new DbId(id, df.getOWLNamedIndividual(boIRI), true);
		}
	}

	public OWLOntology getBusinessObjectOntology(final Long boID)
	{
		return store.txn(new CirmTransaction<OWLOntology>() {
			public OWLOntology call()
			{
				OWLDataFactory df = Refs.tempOntoManager.resolve().getOWLDataFactory();
				Connection conn = getStoreExt().getConnection();
				OWLOntology result;
				try
				{
					OWLEntity entity = getStoreExt().selectEntityByID(boID, df);
					if (entity != null)
					{
						result = getBusinessObjectOntology(entity.getIRI());
					} else
					{
						result = null;
					}
					conn.commit();
					return result;
				} 
				catch (Exception e)
				{
					store.rollback(conn);
					throw new RuntimeException(e);
				} 
				finally
				{
					getStoreExt().close(conn);
				}
				
			}			
		});
	}

	/**
	 * 
	 * @param boIRI
	 *            an IRI ending #bo.
	 * @return
	 * @throws OWLOntologyCreationException
	 */
	public OWLOntology getBusinessObjectOntology(IRI boIRI)
	{
		try
		{
			OWLOntologyManager manager = Refs.tempOntoManager.resolve();
			OWLOntology on;
			synchronized (manager)
			{
				IRI ontologyIRI = IRI.create(boIRI.getStart().substring(0,
						boIRI.getStart().length() - 1));
				on = manager.getOntology(ontologyIRI);
				if (on != null)
					manager.removeOntology(on);
				on = manager.createOntology(ontologyIRI);
			}
			readIndividualData(on, OWL.individual(boIRI));
			return on;
		} catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
	}

	// Mark the business object is invalid as of now (today's date)
	public void shelveBusinessObjectOntology(OWLOntology ontology)
	{
		OWLOntology optimizedOntology = transformer.transform(ontology);
		Map<OWLEntity, DbId> identifiers; // = new HashMap<OWLEntity, Long>();
		Set<OWLEntity> entities = new HashSet<OWLEntity>();
		entities.addAll(optimizedOntology.getClassesInSignature());
		entities.addAll(optimizedOntology.getIndividualsInSignature());
		entities.addAll(optimizedOntology.getObjectPropertiesInSignature());
		entities.addAll(optimizedOntology.getDataPropertiesInSignature());
		entities.addAll(optimizedOntology.getDatatypesInSignature(true));
		ThreadLocalConnection conn = getStoreExt().getConnection();
		try
		{
			identifiers = store.selectIDsAndEntitiesByIRIs(entities);
			Set<OWLNamedIndividual> individuals = optimizedOntology.getIndividualsInSignature();
			getStoreExt().deleteClassification(
				optimizedOntology.getAxioms(AxiomType.CLASS_ASSERTION),
				individuals, identifiers);
			getStoreExt().deleteDataProperties(
				optimizedOntology.getAxioms(AxiomType.DATA_PROPERTY_ASSERTION),
				individuals, identifiers);
			getStoreExt().deleteObjectProperties(
				optimizedOntology.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION),
				individuals, identifiers);
			conn.commit();
		} catch (SQLException e)
		{
			store.rollback(conn);
			throw new RuntimeException(e);
		} finally
		{
			store.close(conn);
		}
	}

	// Delete the BO ontology from the database completely.
	/**
	 * Deletes all stored axioms of the given ontology including their history.
	 * Currently, this only deletes from the vertical schema and NOT(!) from the
	 * mapped schema. (Because of foreign key constraint exceptions)
	 * 
	 * @param ontology
	 * @deprecated
	 */
	public void deleteBusinessObjectOntologyWithHistory(OWLOntology ontology)
	{
		OWLOntology optimizedOntology = transformer.transform(ontology);
		ThreadLocalConnection conn = store.getConnection();
		try
		{
			Map<OWLEntity, Long> identifiers = null; //store.selectIDsAndEntitiesByIRIs(optimizedOntology, false);
			// Set<OWLNamedIndividual> individuals =
			// ontology.getIndividualsInSignature();
			getStoreExt().deleteClassificationWithHistory(
					optimizedOntology.getAxioms(AxiomType.CLASS_ASSERTION),
					identifiers);
			getStoreExt()
					.deleteDataPropertiesWithHistory(
							optimizedOntology
									.getAxioms(AxiomType.DATA_PROPERTY_ASSERTION),
							identifiers);
			getStoreExt()
					.deleteObjectPropertiesWithHistory(
							optimizedOntology
									.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION),
							identifiers);
			System.err
					.println("Current implementation does not delete from mapped schema!");
			// getStoreImpl().deleteMappedOntologyIndividuals(ontology,
			// identifiers);
			conn.commit();
		} catch (Exception e)
		{
			store.rollback(conn);
			throw new RuntimeException(e);
		} finally
		{
			store.close(conn);
		}
	}

	// -------------------------------------------------------------------------
	// QUERY METHODS
	//
	public List<OWLEntity> query(final Json pattern)
	{
		return store.txn(new CirmTransaction<List<OWLEntity>>()
		{
			@Override
			public List<OWLEntity> call() throws Exception
			{
				return queryTxn(pattern);
			}
		});		
	}
	
	private List<OWLEntity> queryTxn(Json pattern)
	{
		// TODO: this obviously really temporary. I actually have the feeling
		// that this class
		// should try to avoid accessing the SQL API and just implement higher
		// level operations.
		// For example, here, it should basically translate the query pattern
		// into something more
		// primitive that the RelationalStore will work with. But for now I just
		// want a list of
		// business objects so I can work on the UI side with templating and
		// stuff....
		OWLDataFactory df = Refs.tempOntoManager.resolve().getOWLDataFactory();
		ThreadLocalStopwatch stopwatch = ThreadLocalStopwatch.getWatch();
		QueryTranslator translator = new QueryTranslator();
		ThreadLocalConnection conn = store.getConnection();
		if (DBG)
		{
			stopwatch.time("START Query ");
		}
		try
		{
			Query query = translator.translate(pattern, getStoreExt());
			ArrayList<OWLEntity> L = new ArrayList<OWLEntity>();
			L.addAll(getStoreExt().queryGetEntities(query, df).values());
			conn.commit();
			return L;
		} catch (Exception e)
		{
			store.rollback(conn);
			throw new RuntimeException(e);
		} finally
		{
			store.close(conn);
			stopwatch.time("END Query ");
		}
	}
	
	public void addRDBListener(RDBListener l)
	{
		rdbListeners.add(l);
	}
	
	public void removeRDBListener(RDBListener l)
	{
		rdbListeners.remove(l);
	}

	public int getNrOfRDBListeners() 
	{
		return rdbListeners.size();
	}
	
	private void fireRDBSave(OWLOntology ontology)
	{
		RDBEvent e = new RDBEvent(ontology.getOntologyID());
		for (RDBListener l : rdbListeners)
			l.saveExecuting(e);
	}
}

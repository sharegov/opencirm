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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLNamedIndividual;


public class RelationalStoreExt extends RelationalStoreImpl
{

	private static Logger logger = Logger.getLogger("org.sharegov.cirm.rdb");
	
	//private String url, driverClassName, username, password;
	//private DataSource dataSource;
	//private int maxVarcharSize = 4000;


	public RelationalStoreExt()
	{
		super();
		//url = "jdbc:oracle:thin:@10.9.25.27:1521:xe";
		//driverClassName = "oracle.jdbc.OracleDriver";
		//username = "cirmschm";
		//password = "cirmschm";
	}

//	public RelationalStoreExt(String url, String driverClassName, String username, String password)
//	{
//		super(url, driverClassName, username, password);
//		//this.url = url;
//		//this.driverClassName = driverClassName;
//		//this.username = username;
//		//this.password = password;
//	}

	public RelationalStoreExt(DataSourceRef dataSourceRef)
	{
		super(dataSourceRef);
	}

//
//	public Map<OWLEntity, OWL2Datatype> selectDatatypes(Set<? extends OWLEntity> objects) {
//		Map<OWLEntity, OWL2Datatype> result = new LinkedHashMap<OWLEntity, OWL2Datatype>();
//		PreparedStatement stmt = null;
//		Connection conn = null;
//		
//		ResultSet rs = null;
//		StringBuilder select = new StringBuilder();
//		OWLDataFactory dataFactory = MetaService.get().getDataFactory();
//		if (objects == null || objects.size() == 0)
//			return result;
//		int pageSize = 1000; // oracle limits sql in() list to 1000 entries, see
//								// ORA-01795, so paging technique used.
//		int pageCount = 1;
//		if (objects.size() > 1000)
//			pageCount = (objects.size() + pageSize - 1) / pageSize;
//		try {
//			conn = getConnection();
//			List<OWLEntity> set = new ArrayList<OWLEntity>(objects);
//			for (int g = 0; g < pageCount; g++)
//			{
//				select.delete(0, select.length());
//				select
//					.append("SELECT IRI_TYPE, IRI, DATATYPE FROM ")
//					.append(TABLE_DATA_PROPERTY)
//					.append(" JOIN ")
//					.append(TABLE_IRI)
//					.append(" ON ")
//					.append(TABLE_IRI+".ID ")
//					.append(" = ")
//					.append(TABLE_DATA_PROPERTY+".PREDICATE ")
//					.append(" WHERE ")
//					.append(TABLE_DATA_PROPERTY+".TO_DATE is null ")
//					.append(" and ")
//					.append(TABLE_IRI)
//					.append(".IRI IN (");
//				for (int i = (g * pageSize); i < (g + 1) * pageSize && i < objects.size(); i++)
//					select.append("?,");
//				select.deleteCharAt(select.lastIndexOf(",")).append(")");
//				stmt = conn.prepareStatement(select.toString());
//				int j = 1;
//				for (int i = (g * pageSize); i < (g + 1) * pageSize && i < objects.size(); i++)
//					stmt.setString(j++, set.get(i).getIRI().toString());
//				rs = stmt.executeQuery();
//				while (rs.next())
//				{
//					OWLEntity o = dataFactory.getOWLEntity(typeOf(rs.getString("IRI_TYPE")), IRI.create(rs
//							.getString("IRI")));
//					String datatype = rs.getString("DATATYPE");
//					OWL2Datatype d = OWL2Datatype.getDatatype(IRI.create(datatype));
//					result.put(o, d);
//				}
//			}
//		}
//		catch (SQLException e) {
//			e.printStackTrace();
//		}
//		finally {
//			close(rs, stmt, conn);
//		}
//		return result;
//	}
//
//
//	
//
//	
//	
//
////	public OWLNamedIndividual selectOne(OWLOntology o, OWLIndividual ind,
////			Map<OWLClass, OWLNamedIndividual> tableMapping, Map<OWLEntity, Long> identifiers)
////	{
////		OWLNamedIndividual result = null;
////		PreparedStatement stmt = null;
////		Connection conn = null;
////		ResultSet rs = null;
////		Statement s = new Statement();
////		OWLNamedIndividual table = Mapping.table(ind.getTypes(o),tableMapping);
////		if (table != null)
////		{
////			
////			Sql sql = SELECT();
////			Set<OWLNamedIndividual> columnIRI = Mapping.columnIRI(table);
////			if (!columnIRI.isEmpty())
////			{
////				OWLNamedIndividual c = columnIRI.iterator().next();
////				sql.COLUMN(c.getIRI().getFragment());
////				sql.WHERE(c.getIRI().getFragment()).EQUALS("?");
////				s.getParameters().add(identifiers.get(ind));
////				Set<OWLNamedIndividual> columnType = reasoner().getObjectPropertyValues(c, 
////						objectProperty(fullIri(Concepts.hasColumnType))).getFlattened(); 
////						
//////						c.getObjectPropertyValues(
//////						objectProperty(fullIri(Concepts.hasColumnType)), ontology());
////				s.getTypes().add(columnType.iterator().next().asOWLNamedIndividual());
////				sql.FROM(table.getIRI().getFragment());
////				s.setSql(sql);
////				try
////				{
////					conn = getConnection();
////					stmt = prepareStatement(conn, s, identifiers);
////					rs = stmt.executeQuery();
////					if (rs.next())
////					{
////						Long id = rs.getLong(1);
////						for (Map.Entry<OWLEntity, Long> entry : identifiers.entrySet())
////						{
////							if (entry.getValue().equals(id))
////							{
////								result = entry.getKey().asOWLNamedIndividual();
////								return result;
////							}
////						}
////					}
////				}
////				catch (SQLException e)
////				{
////					e.printStackTrace();
////				}
////				finally
////				{
////					if (rs != null)
////						try
////						{
////							rs.close();
////						}
////						catch (Throwable e)
////						{
////						}
////					if (stmt != null)
////						try
////						{
////							stmt.close();
////						}
////						catch (Throwable e)
////						{
////						}
////					if (conn != null)
////						try
////						{
////							conn.close();
////						}
////						catch (Throwable e)
////						{
////						}
////				}
////			}
////		}
////		return result;
////	}
//
//	public Set<OWLClass> selectClass(OWLIndividual subject, Date version)
//	{
//		Set<OWLEntity> s = new HashSet<OWLEntity>();
//		s.add(subject.asOWLNamedIndividual());
//		Map<OWLEntity, Long> identifiers = selectIDsAndEntitiesByIRIs(s);
//		return selectClass(subject, version, identifiers);
//	}
//
//	public Set<OWLClass> selectClass(OWLIndividual subject)
//	{
//		Set<OWLEntity> s = new HashSet<OWLEntity>();
//		s.add(subject.asOWLNamedIndividual());
//		Map<OWLEntity, Long> identifiers = selectIDsAndEntitiesByIRIs(s);
//		return selectClass(subject, identifiers);
//	}
//
//
//
//	public Map<OWLObjectPropertyExpression, Set<OWLIndividual>> selectObjectProperties(OWLIndividual subject)
//	{
//		Set<OWLEntity> s = new HashSet<OWLEntity>();
//		s.add(subject.asOWLNamedIndividual());
//		Map<OWLEntity, Long> identifiers = selectIDsAndEntitiesByIRIs(s, true);
//		return selectObjectProperties(subject, identifiers);
//	}
//
//	public Map<OWLObjectPropertyExpression, Set<OWLIndividual>> selectObjectProperties(OWLIndividual subject,
//			Date version)
//	{
//		Set<OWLEntity> s = new HashSet<OWLEntity>();
//		s.add(subject.asOWLNamedIndividual());
//		Map<OWLEntity, Long> identifiers = selectIDsAndEntitiesByIRIs(s, true);
//		return selectObjectProperties(subject, version, identifiers);
//	}
//
//
//
//
//	public Map<OWLDataPropertyExpression, Set<OWLLiteral>> selectDataProperties(OWLIndividual subject, Date version)
//	{
//		Set<OWLEntity> s = new HashSet<OWLEntity>();
//		s.add(subject.asOWLNamedIndividual());
//		Map<OWLEntity, Long> identifiers = selectIDsAndEntitiesByIRIs(s, true);
//		return selectDataProperties(subject, version, identifiers);
//	}
//
//	/**
//	 * Fetch all the data properties for a given Individual on a given time
//	 */
//	public void fetchDataPropertyAxioms(Connection conn, java.sql.Timestamp t1, long subject, OWLNamedIndividual ind, Set<OWLAxiom> axioms) {
//		PreparedStatement stmtData = null;
//		ResultSet rsData = null;
//		StringBuilder selectData = new StringBuilder();
//		selectData
//			.append("SELECT a.DATATYPE, c.IRI, ")
//			.append("b.VALUE_AS_VARCHAR, b.VALUE_AS_CLOB, b.VALUE_AS_DATE, b.VALUE_AS_DOUBLE, b.VALUE_AS_INTEGER ")
//			.append("FROM ")
//			.append(TABLE_DATA_PROPERTY).append(" a, ").append(TABLE_DATA_VALUE).append(" b, ").append(TABLE_IRI).append(" c ")
//			.append("WHERE ")
//			.append("(a.PREDICATE, a.FROM_DATE) in ")
//				.append("(SELECT PREDICATE, MAX(FROM_DATE) FROM ")
//				.append(TABLE_DATA_PROPERTY)
//				.append(" WHERE ")
//				.append("SUBJECT = ? ")
//				.append("AND ")
//				.append("FROM_DATE <= ? ")
//				.append("GROUP BY PREDICATE) ")
//			.append("AND ")
//			.append("SUBJECT = ? ")
//			.append("AND ")
//			.append("a.PREDICATE = c.ID ")
//			.append("AND ")
//			.append("a.VALUE_HASH = b.VALUE_HASH ")
//			.append("AND ")
//			.append("a.VALUE_ID = b.ID");
//		try	{
//			OWLDataFactory factory = MetaService.get().getDataFactory();
//			stmtData = conn.prepareStatement(selectData.toString());
//			stmtData.setLong(1, subject);
//			stmtData.setTimestamp(2, t1);
//			stmtData.setLong(3, subject);
//			rsData = stmtData.executeQuery();
//			while (rsData.next())
//			{
//				OWLLiteral literal = literal(factory, rsData, OWL2Datatype.getDatatype(IRI.create(rsData.getString(1))));
//				System.out.print(literal.toString());
//				System.out.print("    ");
//				System.out.print(rsData.getString(2));
//				System.out.println("    ");
//				OWLAxiom dataAxiom = factory.getOWLDataPropertyAssertionAxiom(dataProperty(rsData.getString(2)), ind, literal.toString());
//				axioms.add(dataAxiom);
//			}
//		}
//		catch (SQLException e) {
//			e.printStackTrace();
//		}
//		finally {
//			close(rsData, stmtData);
//		}
//	}
//
//	/**
//	 * Fetch all the Object Properties for a given Individual on a given time
//	 */
//	public void fetchObjectPropertyAxioms(Connection conn, java.sql.Timestamp t1, long subject, OWLNamedIndividual ind, Set<OWLAxiom> axioms) {
//		PreparedStatement stmtObj = null;
//		ResultSet rsObject = null;
//		StringBuilder selectObj = new StringBuilder();
//		selectObj
//			.append("SELECT b.IRI, c.IRI ")
//			.append("FROM ")
//			.append(TABLE_OBJECT_PROPERTY).append(" a, ").append(TABLE_IRI).append(" b, ").append(TABLE_IRI).append(" c ")
//			.append("WHERE ")
//			.append("(a.PREDICATE, a.FROM_DATE) in ")
//				.append("(SELECT PREDICATE, MAX(FROM_DATE) ")
//				.append("FROM ")
//				.append(TABLE_OBJECT_PROPERTY)
//				.append(" WHERE ")
//				.append("SUBJECT = ? ")
//				.append("AND ")
//				.append("FROM_DATE <= ? ")
//				.append("GROUP BY PREDICATE) ")
//			.append("AND ")
//			.append("SUBJECT = ? ")
//			.append("AND ")
//			.append("a.PREDICATE = b.ID ")
//			.append("AND ")
//			.append("a.OBJECT = c.ID");
//		try	{
//			OWLDataFactory factory = MetaService.get().getDataFactory();
//			stmtObj = conn.prepareStatement(selectObj.toString());
//			stmtObj.setLong(1, subject);
//			stmtObj.setTimestamp(2, t1);
//			stmtObj.setLong(3, subject);
//			rsObject = stmtObj.executeQuery();
//			while(rsObject.next()) {
//				System.out.print(rsObject.getString(1));
//				System.out.print("    ");
//				System.out.print(rsObject.getString(2));
//				System.out.println("    ");
//				OWLAxiom objAxiom = factory.getOWLObjectPropertyAssertionAxiom(objectProperty(rsObject.getString(1)), ind, individual(rsObject.getString(2)));
//				axioms.add(objAxiom);
//			}
//		}
//		catch (SQLException e) {
//			e.printStackTrace();
//		}
//		finally {
//			close(rsObject, stmtObj);
//		}
//	}
//
//	/**
//	 * Fetch all the Class properties for a given Individual on a given time
//	 */
//	public void fetchClassAxioms(Connection conn, java.sql.Timestamp t1, long subject, OWLNamedIndividual ind, Set<OWLAxiom> axioms) {
//		PreparedStatement stmtClass = null;
//		ResultSet rsClass = null;
//		StringBuilder selectClass = new StringBuilder();
//		selectClass
//			.append("SELECT b.IRI ")
//			.append("FROM ")
//			.append(TABLE_CLASSIFICATION).append(" a, ").append(TABLE_IRI).append(" b ")
//			.append("WHERE ")
//			.append("(a.OWLCLASS, a.FROM_DATE) IN ")
//				.append("(SELECT OWLCLASS, MAX(FROM_DATE) ")
//				.append("FROM ")
//				.append(TABLE_CLASSIFICATION)
//				.append(" WHERE ")
//				.append("SUBJECT = ? ")
//				.append("AND ")
//				.append("FROM_DATE <= ? ")
//				.append("GROUP BY OWLCLASS) ")
//			.append("AND ")
//			.append("SUBJECT = ? ")
//			.append("AND ")
//			.append("a.OWLCLASS = b.ID");
//		try	{
//			OWLDataFactory factory = MetaService.get().getDataFactory();
//			stmtClass = conn.prepareStatement(selectClass.toString());
//			stmtClass.setLong(1, subject);
//			stmtClass.setTimestamp(2, t1);
//			stmtClass.setLong(3, subject);
//			rsClass = stmtClass.executeQuery();
//			while(rsClass.next()) {
//				System.out.print(rsClass.getString(1));
//				System.out.println("    ");
//				OWLAxiom caAxiom = factory.getOWLClassAssertionAxiom(owlClass(rsClass.getString(1)), ind);
//				axioms.add(caAxiom);
//			}
//		}
//		catch (SQLException e) {
//			e.printStackTrace();
//		}
//		finally {
//			close(rsClass, stmtClass);
//		}
//	}
//
//	/**
//	 * Fetches all the DataPropertyAssertions, ObjectPropertyAssertions, ClassAssertions from the db
//	 * and populates a Set of OWLAxioms and returns the same to the caller.
//	 */
//	public Set<OWLAxiom> getIndividualFromHistory(java.sql.Timestamp t1, long subject, OWLNamedIndividual ind) {
//		Connection conn = null;
//		Set<OWLAxiom> axioms = new HashSet<OWLAxiom>();
//		try	{
//			conn = getConnection();
//			fetchDataPropertyAxioms(conn, t1, subject, ind, axioms);
//			fetchObjectPropertyAxioms(conn, t1, subject, ind, axioms);
//			fetchClassAxioms(conn, t1, subject, ind, axioms);
//		}
//		catch (Exception e) {
//			e.printStackTrace();
//		}
//		finally {
//			if (conn != null)
//				try { conn.close(); }
//				catch (Throwable e) { }
//		}
//		return axioms;
//	}
//	
//	
//
//	
//
//	@Deprecated
//	public int[] insert(OWLIndividual subject, Set<OWLClass> classes, Map<OWLEntity, Long> identifiers)
//	{
//		PreparedStatement stmt = null;
//		Connection conn = null;
//		ResultSet rs = null;
//		int[] result = new int[] {};
//
//		StringBuffer insert = new StringBuffer();
//		insert.append("INSERT INTO ").append(TABLE_CLASSIFICATION).append("(").append("SUBJECT").append(",OWLCLASS").append(
//				",FROM_DATE").append(")").append("VALUES").append("(?,?,?)");
//		try
//		{
//			conn = getConnection();
//			conn.setAutoCommit(false);
//			stmt = conn.prepareStatement(insert.toString());
//			Timestamp now = new Timestamp(getStoreTime().getTime());
//			Long s = identifiers.get(subject);
//			for (OWLClass cl : classes)
//			{
//				Long c = identifiers.get(cl);
//				stmt.setLong(1, s);
//				stmt.setLong(2, c);
//				stmt.setTimestamp(3, now);
//				stmt.addBatch();
//			}
//			result = stmt.executeBatch();
//			conn.commit();
//		}
//		catch (SQLException e)
//		{
//			e.printStackTrace();
//			try
//			{
//				conn.rollback();
//			}
//			catch (Throwable f)
//			{
//
//			}
//		}
//		finally
//		{
//			close(rs, stmt, conn);
//		}
//		return result;
//	}
//	
//	@Deprecated
//	public int[] insert(OWLIndividual subject, Set<OWLClass> classes)
//	{
//		Set<OWLEntity> s = new HashSet<OWLEntity>();
//		s.add(subject.asOWLNamedIndividual());
//		s.addAll(classes);
//		Map<OWLEntity, Long> identifiers = selectIDsAndEntitiesByIRIs(s, true);
//		return insert(subject, classes, identifiers);
//	}
//
//
//
//
//	public int[] insertDataProperties(OWLIndividual subject, Map<OWLDataPropertyExpression, Set<OWLLiteral>> data,
//			Map<OWLEntity, Long> identifiers)
//	{
//		return insertDataProperties(subject, data, identifiers, null);
//	}
//	
//	public int[] insertObjectProperties(OWLIndividual subject,
//			Map<OWLObjectPropertyExpression, Set<OWLIndividual>> objects, Map<OWLEntity, Long> identifiers)
//	{
//		PreparedStatement stmt = null;
//		Connection conn = null;
//		ResultSet rs = null;
//		int[] result = new int[] {};
//
//		StringBuffer insert = new StringBuffer();
//		insert.append("INSERT INTO ").append(TABLE_OBJECT_PROPERTY).append("(").append("SUBJECT").append(",PREDICATE").append(
//				",OBJECT").append(",FROM_DATE").append(")").append("VALUES").append("(").append("?,?,?,?").append(")");
//		try
//		{
//			conn = getConnection();
//			conn.setAutoCommit(false);
//			Timestamp now = new Timestamp(getStoreTime().getTime());
//			stmt = conn.prepareStatement(insert.toString());
//			Long s = identifiers.get(subject.asOWLNamedIndividual());
//			for (Map.Entry<OWLObjectPropertyExpression, Set<OWLIndividual>> e : objects.entrySet())
//			{
//				if (e.getKey() instanceof OWLObjectProperty)
//				{
//					Long p = identifiers.get((OWLObjectProperty) e.getKey());
//					for (OWLIndividual ind : e.getValue())
//					{
//						Long o = identifiers.get(ind.asOWLNamedIndividual());
//						stmt.setLong(1, s);
//						stmt.setLong(2, p);
//						stmt.setLong(3, o);
//						stmt.setTimestamp(4, now);
//						stmt.addBatch();
//					}
//				}
//			}
//			result = stmt.executeBatch();
//			conn.commit();
//		}
//		catch (SQLException e)
//		{
//			e.printStackTrace();
//			try
//			{
//				conn.rollback();
//			}
//			catch (Throwable f)
//			{
//
//			}
//		}
//		finally
//		{
//			close(rs, stmt, conn);
//		}
//		return result;
//	}
//
////	public int[] update(OWLIndividual subject, Set<OWLClass> classes, Map<OWLEntity, Long> identifiers)
////	{
////		PreparedStatement stmt = null;
////		Connection conn = null;
////		ResultSet rs = null;
////		int[] result = new int[] {};
////
////		StringBuffer update = new StringBuffer();
////		update.append("UPDATE ").append(TABLE_CLASSIFICATION).append(" SET TO_DATE= ? ").append(
////				"WHERE SUBJECT = ? AND TO_DATE IS NULL");
////
////		try
////		{
////			conn = getConnection();
////			conn.setAutoCommit(false);
////			Timestamp now = new Timestamp(globalTime().getTime());
////			stmt = conn.prepareStatement(update.toString());
////			Long s = identifiers.get(subject.asOWLNamedIndividual());
////			stmt.setTimestamp(1, now);
////			stmt.setLong(2, s);
////			stmt.executeUpdate();
////			conn.commit();
////			result = insert(subject, classes);
////		}
////		catch (SQLException e)
////		{
////			e.printStackTrace();
////			try
////			{
////				conn.rollback();
////			}
////			catch (Throwable f)
////			{
////
////			}
////		}
////		finally
////		{
////			if (rs != null)
////				try
////				{
////					rs.close();
////				}
////				catch (Throwable e)
////				{
////				}
////			if (stmt != null)
////				try
////				{
////					stmt.close();
////				}
////				catch (Throwable e)
////				{
////				}
////			if (conn != null)
////				try
////				{
////					conn.close();
////				}
////				catch (Throwable e)
////				{
////				}
////		}
////		return result;
////	}
////
//	
//	public int[] update(OWLIndividual subject, Set<OWLClass> classes)
//	{
//		Set<OWLEntity> s = new HashSet<OWLEntity>();
//		s.add(subject.asOWLNamedIndividual());
//		s.addAll(classes);
//		Map<OWLEntity, Long> identifiers = selectIDsAndEntitiesByIRIs(s, true);
//		return insert(subject, classes, identifiers);
//	}

//	public int[] updateDataProperties(OWLIndividual subject, Map<OWLDataPropertyExpression, Set<OWLLiteral>> data,
//			Map<OWLEntity, Long> identifiers)
//	{
//		return updateDataProperties(subject, data, identifiers, null);
//	}

//	public int[] updateObjectProperties(OWLIndividual subject,
//			Map<OWLObjectPropertyExpression, Set<OWLIndividual>> objects, Map<OWLEntity, Long> identifiers)
//	{
//		PreparedStatement stmt = null;
//		Connection conn = null;
//		ResultSet rs = null;
//		int[] result = new int[] {};
//		// sets all active object properties as expired, including those outside
//		// the map.
//		StringBuffer update = new StringBuffer();
//		update.append("UPDATE ").append(TABLE_OBJECT_PROPERTY).append(" SET TO_DATE= ? ").append(
//				"WHERE SUBJECT = ? AND TO_DATE IS NULL");
//		try
//		{
//			conn = getConnection();
//			conn.setAutoCommit(false);
//			Timestamp now = new Timestamp(getStoreTime().getTime());
//			stmt = conn.prepareStatement(update.toString());
//			Long s = identifiers.get(subject.asOWLNamedIndividual());
//			stmt.setTimestamp(1, now);
//			stmt.setLong(2, s);
//			stmt.executeUpdate();
//			conn.commit();
//			result = insertObjectProperties(subject, objects, identifiers);
//		}
//		catch (SQLException e)
//		{
//			e.printStackTrace();
//			try
//			{
//				conn.rollback();
//			}
//			catch (Throwable f)
//			{
//
//			}
//		}
//		finally
//		{
//			close(rs, stmt, conn);
//		}
//		return result;
//	}
//
//	public void delete(OWLIndividual subject, Set<OWLClass> classes, Map<OWLEntity, Long> identifiers)
//	{
//		PreparedStatement stmt = null;
//		Connection conn = null;
//
//		StringBuffer update = new StringBuffer();
//		update.append("UPDATE ").append(TABLE_CLASSIFICATION).append(" SET TO_DATE= ? ").append(
//				"WHERE SUBJECT = ? AND TO_DATE IS NULL");
//
//		try
//		{
//			conn = getConnection();
//			conn.setAutoCommit(false);
//			Timestamp now = new Timestamp(getStoreTime().getTime());
//			stmt = conn.prepareStatement(update.toString());
//			Long s = identifiers.get(subject.asOWLNamedIndividual());
//			stmt.setTimestamp(1, now);
//			stmt.setLong(2, s);
//			stmt.executeUpdate();
//			conn.commit();
//		}
//		catch (SQLException e)
//		{
//			e.printStackTrace();
//			try
//			{
//				conn.rollback();
//			}
//			catch (Throwable f)
//			{
//
//			}
//		}
//		finally
//		{
//			close(stmt, conn);
//		}
//	}
//	
//// ------------------------------------------------------------------------------------------------------------------------------
//// USED ONLY IN ONTOLOGYCHANGER... 
////
//	
//	@Deprecated
//	public boolean insertAxioms(Set<AddAxiom> axioms, Set<OWLEntity> entities, Map<String, OWLLiteral>literals) {
//		Map<OWLEntity, Long> identifiers = new HashMap<OWLEntity, Long>();
//			identifiers.putAll(selectIDsAndEntitiesByIRIs(entities, true));
//		Map<String, Long> hashes = new HashMap<String, Long>();
//			hashes.putAll(selectLiteralIDs(literals, true));
//		return insertAxioms(axioms, identifiers, hashes);
//	}
//	
//	@Deprecated
//	public boolean insertAxioms(Set<AddAxiom> axioms, Map<OWLEntity, Long> identifiers, Map<String, Long> hashes) {
//		Connection conn = null;
//		PreparedStatement stmt_dpa = null;
//		PreparedStatement stmt_opa = null;
//		PreparedStatement stmt_ca = null;
//		boolean checkCommit = true;
//		
//		try {
//			conn = getConnection();
//			conn.setAutoCommit(false);
//			Timestamp now = new Timestamp(getStoreTime().getTime());
//			
//			StringBuilder insert1 = new StringBuilder();
//			insert1.append("INSERT INTO ").append(TABLE_DATA_PROPERTY).append("(").append("SUBJECT").append(",PREDICATE").append(
//					",DATATYPE").append(",LANG").append(",FROM_DATE").append(",VALUE_ID").append(",VALUE_HASH")
//					.append(") ").append(" VALUES ").append("(?,?,?,?,?,?,?)");
//			stmt_dpa = conn.prepareStatement(insert1.toString());
//
//			StringBuilder insert2 = new StringBuilder();
//			insert2.append("INSERT INTO ").append(TABLE_OBJECT_PROPERTY).append("(").append("SUBJECT").append(",PREDICATE").append(
//					",OBJECT").append(",FROM_DATE").append(")").append("VALUES").append("(").append("?,?,?,?").append(")");
//			stmt_opa = conn.prepareStatement(insert2.toString());
//			
//			StringBuilder insert3 = new StringBuilder();
//			insert3.append("INSERT INTO ").append(TABLE_CLASSIFICATION).append("(").append("SUBJECT").append(",OWLCLASS").append(
//					",FROM_DATE").append(")").append("VALUES").append("(").append("?,?,?").append(")");
//			stmt_ca = conn.prepareStatement(insert3.toString());
//
//			for (AddAxiom aa : axioms)
//			{
//				if(aa.getAxiom().isOfType(AxiomType.DATA_PROPERTY_ASSERTION)) {
//					OWLDataPropertyAssertionAxiom dataAxiom = (OWLDataPropertyAssertionAxiom) aa.getAxiom();
//					String dataHash = hash(dataAxiom.getObject().getLiteral());
//					stmt_dpa.setLong(1, identifiers.get(dataAxiom.getSubject()));
//					stmt_dpa.setLong(2, identifiers.get(dataAxiom.getProperty()));
//					stmt_dpa.setString(3, dataAxiom.getObject().getDatatype().getIRI().toString());
//					stmt_dpa.setString(4, dataAxiom.getObject().getLang());
//					stmt_dpa.setTimestamp(5, now);
//					stmt_dpa.setLong(6, hashes.get(dataHash));
//					stmt_dpa.setString(7, dataHash);
//					stmt_dpa.addBatch();
//				} else if(aa.getAxiom().isOfType(AxiomType.OBJECT_PROPERTY_ASSERTION)) {
//					OWLObjectPropertyAssertionAxiom objAxiom = (OWLObjectPropertyAssertionAxiom) aa.getAxiom();
//					stmt_opa.setLong(1, identifiers.get(objAxiom.getSubject()));
//					stmt_opa.setLong(2, identifiers.get(objAxiom.getProperty()));
//					stmt_opa.setLong(3, identifiers.get(objAxiom.getObject()));
//					stmt_opa.setTimestamp(4, now);
//					stmt_opa.addBatch();
//				} else if(aa.getAxiom().isOfType(AxiomType.CLASS_ASSERTION)) {
//					OWLClassAssertionAxiom classAxiom = (OWLClassAssertionAxiom) aa.getAxiom();
//					stmt_ca.setLong(1, identifiers.get(classAxiom.getIndividual()));
//					stmt_ca.setLong(2, identifiers.get(classAxiom.getClassExpression()));
//					stmt_ca.setTimestamp(3, now);
//					stmt_ca.addBatch();
//				} else {
//					throw new IllegalArgumentException("Unknown axiom: " + aa.getAxiom() + " Class:" + aa.getAxiom().getClass());
//				}
//			}
//			int dpa[] = stmt_dpa.executeBatch();
//			int opa[]= stmt_opa.executeBatch();
//			int ca[]= stmt_ca.executeBatch();
//			//Check for any command failures in each batch.
//			if(checkCommit == true) {
//				for (int j = 0; j < dpa.length; j++) {
//					if(dpa[j] == java.sql.Statement.EXECUTE_FAILED)
//						checkCommit = false;
//				}
//			}
//			if(checkCommit == true) {
//				for (int j = 0; j < opa.length; j++) {
//					if(opa[j] == java.sql.Statement.EXECUTE_FAILED)
//						checkCommit = false;
//				}
//			}
//			if(checkCommit == true) {
//				for (int j = 0; j < ca.length; j++) {
//					if(ca[j] == java.sql.Statement.EXECUTE_FAILED)
//						checkCommit = false;
//				}
//			}
//			//Commit or rollback
//			if(checkCommit == true)
//				conn.commit();
//			else
//				conn.rollback();
//
//		}
//		catch (SQLException e) {
//			e.printStackTrace();
//			try {
//				conn.rollback();
//				checkCommit = false;
//			}
//			catch (Throwable f) { }
//		}
//		finally
//		{
//			if (stmt_dpa != null)
//				try { stmt_dpa.close(); }
//				catch (Throwable e){}
//			if (stmt_opa != null)
//				try { stmt_opa.close(); }
//				catch (Throwable e){}
//			if (stmt_ca != null)
//				try { stmt_ca.close(); }
//				catch (Throwable e){}
//			if (conn != null)
//				try { conn.close(); }
//				catch (Throwable e) {}
//		}
//		return checkCommit;
//	}
//	
//	@Deprecated
//	public boolean deleteAxioms(Set<RemoveAxiom> axioms, Set<OWLEntity> entities) {
//		
//		Map<OWLEntity, Long> identifiers = new HashMap<OWLEntity, Long>();
//		identifiers.putAll(selectIDsAndEntitiesByIRIs(entities, false));
//		return deleteAxioms(axioms, identifiers);
//	}
//
//	@Deprecated
//	public boolean deleteAxioms(Set<RemoveAxiom> hset, Map<OWLEntity, Long> identifiers)
//	{
//		Connection conn = null;
//		PreparedStatement stmt_dpa = null;
//		PreparedStatement stmt_opa = null;
//		PreparedStatement stmt_ca = null;
//		boolean checkCommit = true;
//		
//		try {
//			conn = getConnection();
//			conn.setAutoCommit(false);
//
//			Timestamp now = new Timestamp(getStoreTime().getTime());
//
//			StringBuilder update1 = new StringBuilder();
//			update1.append("UPDATE ").append(TABLE_DATA_PROPERTY).append(" SET TO_DATE = ? ").append(
//					"WHERE SUBJECT = ? and PREDICATE = ? AND VALUE_HASH = ? AND TO_DATE IS NULL");
//			stmt_dpa = conn.prepareStatement(update1.toString());
//
//			StringBuilder update2 = new StringBuilder();
//			update2.append("UPDATE ").append(TABLE_OBJECT_PROPERTY).append(" SET TO_DATE = ? ").append(
//					"WHERE SUBJECT = ? AND PREDICATE = ? AND OBJECT = ? AND TO_DATE IS NULL");
//			stmt_opa = conn.prepareStatement(update2.toString());
//
//			StringBuilder update3 = new StringBuilder();
//			update3.append("UPDATE ").append(TABLE_CLASSIFICATION).append(" SET TO_DATE = ? ").append(
//					"WHERE SUBJECT = ? AND OWLCLASS = ? AND TO_DATE IS NULL");
//			stmt_ca = conn.prepareStatement(update3.toString());
//			
//			for (RemoveAxiom rx : hset)
//			{
//				if(rx.getAxiom().isOfType(AxiomType.DATA_PROPERTY_ASSERTION)) {
//					OWLDataPropertyAssertionAxiom daAxiom = (OWLDataPropertyAssertionAxiom) rx.getAxiom();
//					stmt_dpa.setTimestamp(1, now);
//					stmt_dpa.setLong(2, identifiers.get(daAxiom.getSubject()));
//					stmt_dpa.setLong(3, identifiers.get(daAxiom.getProperty()));
//					String hash = hash(daAxiom.getObject().getLiteral());
//					stmt_dpa.setString(4, hash);
//					stmt_dpa.addBatch();
//				}
//				else if(rx.getAxiom().isOfType(AxiomType.OBJECT_PROPERTY_ASSERTION)) {
//					OWLObjectPropertyAssertionAxiom objAxiom = (OWLObjectPropertyAssertionAxiom) rx.getAxiom();
//					stmt_opa.setTimestamp(1, now);
//					stmt_opa.setLong(2, identifiers.get(objAxiom.getSubject()));
//					stmt_opa.setLong(3, identifiers.get(objAxiom.getProperty()));
//					stmt_opa.setLong(4, identifiers.get(objAxiom.getObject()));
//					stmt_opa.addBatch();
//				}
//				else if(rx.getAxiom().isOfType(AxiomType.CLASS_ASSERTION)) {
//					OWLClassAssertionAxiom classAxiom = (OWLClassAssertionAxiom) rx.getAxiom();
//					stmt_ca.setTimestamp(1, now);
//					stmt_ca.setLong(2, identifiers.get(classAxiom.getIndividual()));
//					stmt_ca.setLong(3, identifiers.get(classAxiom.getClassExpression()));
//					stmt_ca.addBatch();
//				} else {
//					throw new IllegalArgumentException("Unknown axiom: " + rx.getAxiom() + " Class:" + rx.getAxiom().getClass());
//				}
//			}
//			int dpa[]= stmt_dpa.executeBatch();
//			int opa[]= stmt_opa.executeBatch();
//			int ca[]= stmt_ca.executeBatch();
//			
//			//Check for any command failures in each batch.
//			if(checkCommit == true) {
//				for (int j = 0; j < dpa.length; j++) {
//					if(dpa[j] == java.sql.Statement.EXECUTE_FAILED)
//						checkCommit = false;
//				}
//			}
//			if(checkCommit == true) {
//				for (int j = 0; j < opa.length; j++) {
//					if(opa[j] == java.sql.Statement.EXECUTE_FAILED)
//						checkCommit = false;
//				}
//			}
//			if(checkCommit == true) {
//				for (int j = 0; j < ca.length; j++) {
//					if(ca[j] == java.sql.Statement.EXECUTE_FAILED)
//						checkCommit = false;
//				}
//			}
//			if(checkCommit == true)
//				conn.commit();
//			else
//				conn.rollback();
//		}
//		catch (SQLException e) {
//			e.printStackTrace();
//			try {
//				conn.rollback();
//				checkCommit = false;
//			}
//			catch (Throwable f) { }
//		}
//		finally
//		{
//			if (stmt_dpa != null)
//				try { stmt_dpa.close(); }
//				catch (Throwable e){}
//			if (stmt_opa != null)
//				try { stmt_opa.close(); }
//				catch (Throwable e){}
//			if (stmt_ca != null)
//				try { stmt_ca.close(); }
//				catch (Throwable e){}
//			if (conn != null)
//				try { conn.close(); }
//				catch (Throwable e) {}
//		}
//		return checkCommit;
//	}
//
//	//
//	//
//	// ------------------------------------------------------------------------------------------------------------------------------
//	
//	public void deleteDataProperties(OWLIndividual subject, Map<OWLEntity, Long> identifiers)
//	{
//		PreparedStatement stmt = null;
//		Connection conn = null;
//
//		StringBuffer update = new StringBuffer();
//		update.append("UPDATE ").append(TABLE_DATA_PROPERTY).append(" SET TO_DATE= ? ").append(
//				"WHERE SUBJECT = ? AND TO_DATE IS NULL");
//
//		try
//		{
//			conn = getConnection();
//			conn.setAutoCommit(false);
//			Timestamp now = new Timestamp(getStoreTime().getTime());
//			stmt = conn.prepareStatement(update.toString());
//			Long s = identifiers.get(subject.asOWLNamedIndividual());
//			stmt.setTimestamp(1, now);
//			stmt.setLong(2, s);
//			stmt.executeUpdate();
//			conn.commit();
//		}
//		catch (SQLException e)
//		{
//			e.printStackTrace();
//			try
//			{
//				conn.rollback();
//			}
//			catch (Throwable f)
//			{
//				f.printStackTrace();
//			}
//		}
//		finally
//		{
//			close(stmt, conn);
//		}
//	}
//
//	public void deleteObjectProperties(OWLIndividual subject, Map<OWLEntity, Long> identifiers)
//	{
//		PreparedStatement stmt = null;
//		Connection conn = null;
//
//		StringBuffer update = new StringBuffer();
//		update.append("UPDATE ").append(TABLE_OBJECT_PROPERTY).append(" SET TO_DATE= ? ").append(
//				"WHERE SUBJECT = ? AND TO_DATE IS NULL");
//		try
//		{
//			conn = getConnection();
//			conn.setAutoCommit(false);
//			Timestamp now = new Timestamp(getStoreTime().getTime());
//			stmt = conn.prepareStatement(update.toString());
//			Long s = identifiers.get(subject.asOWLNamedIndividual());
//			stmt.setTimestamp(1, now);
//			stmt.setLong(2, s);
//			stmt.executeUpdate();
//			conn.commit();
//
//		}
//		catch (SQLException e)
//		{
//			e.printStackTrace();
//			try
//			{
//				conn.rollback();
//			}
//			catch (Throwable f)
//			{
//
//			}
//		}
//		finally
//		{
//			close(stmt, conn);
//		}
//	}
//
//	public Set<OWLNamedIndividual> delete(OWLOntology o, OWLNamedIndividual ind,
//			Map<OWLEntity, Long> identifiers,
//			Map<OWLClass, OWLNamedIndividual> tableMapping,
//			Map<OWLNamedIndividual, Map<OWLProperty<?, ?>, OWLNamedIndividual>> columnMapping
//			)
//	{
//	
//		List<Statement> statements = new ArrayList<Statement>();
//		Set<OWLNamedIndividual> done = new HashSet<OWLNamedIndividual>();
//		delete(o, ind, tableMapping, columnMapping, identifiers, statements, done);
//		Connection conn = getConnection();
//		try {
//			execute(statements, identifiers, conn);
//			conn.commit();
//		} catch (SQLException e) {
//			try	{
//				conn.rollback();
//			} catch (SQLException e1) {
//				e1.printStackTrace();
//			}
//			throw new RuntimeException(e);
//		}
//		return done;
//	}
//
//
//	public void delete(OWLOntology o, OWLIndividual ind, Map<OWLClass, OWLNamedIndividual> tableMapping,
//			Map<OWLNamedIndividual, Map<OWLProperty<?, ?>, OWLNamedIndividual>> columnMapping,
//			Map<OWLEntity, Long> identifiers, List<Statement> statements, Set<OWLNamedIndividual> done)
//	{
//		Statement s = new Statement();
//		OWLNamedIndividual table = Mapping.table(ind.getTypes(o),tableMapping);
//		if (table != null)
//		{	
//			Sql sql = DELETE_FROM(table.getIRI().getFragment());
//			// add mapped properties
//			Set<OWLNamedIndividual> columnIRI = Mapping.columnIRI(table);
//			// add iri value
//			OWLNamedIndividual column = null;
//			if (!columnIRI.isEmpty())
//			{
//				column = columnIRI.iterator().next();
//				sql.WHERE(column.getIRI().getFragment()).EQUALS("?");
//				s.getParameters().add(identifiers.get(ind));
//				Set<OWLIndividual> columnType = column.getObjectPropertyValues(
//						objectProperty(fullIri(Concepts.hasColumnType)), ontology());
//				s.getTypes().add(columnType.iterator().next().asOWLNamedIndividual());
//			}
//			Map<OWLObjectPropertyExpression, Set<OWLIndividual>> objectPropertyValues = ind.getObjectPropertyValues(o);
//			// hasMany
//			Map<OWLObjectProperty, Map<OWLClass, OWLNamedIndividual>> hasMany = Mapping.hasMany(objectPropertyValues,
//					o, table, tableMapping);
//			for (OWLObjectProperty mappedProperty : hasMany.keySet())
//			{
//				Map<OWLClass, OWLNamedIndividual> hasManyMapping = hasMany.get(mappedProperty);
//				boolean deleted = false;
//				for (OWLIndividual prop : objectPropertyValues.get(mappedProperty))
//				{
//
//					OWLNamedIndividual manyTable = hasManyMapping.values().iterator().next();
//					OWLNamedIndividual joinTable = Mapping.join(table, manyTable);
//					OWLNamedIndividual manyObject = prop.asOWLNamedIndividual();
//					if (joinTable == null)
//						continue;
//					boolean manyToMany = !joinTable.equals(manyTable);
//					if (manyToMany)
//					{
//						OWLNamedIndividual joinColumnIRI = Mapping.joinColumnIRI(column, joinTable);
//						// delete from junction table
//						if (!deleted)
//						{
//							Statement delete = new Statement();
//							delete.setSql(DELETE_FROM(joinTable.getIRI().getFragment()).WHERE(
//									joinColumnIRI.getIRI().getFragment()).EQUALS("?"));
//							delete.getParameters().add(identifiers.get(ind));
//							Set<OWLIndividual> columnType = column.getObjectPropertyValues(
//									objectProperty(fullIri(Concepts.hasColumnType)), ontology());
//							delete.getTypes().add(columnType.iterator().next().asOWLNamedIndividual());
//							statements.add(delete);
//							deleted = true;
//						}
//						delete(o, manyObject, hasManyMapping, columnMapping, identifiers, statements, done);
//					}
//					else
//					{
//						// TODO: many-to-one
//					}
//				}
//			}
//			s.setSql(sql);
//			statements.add(s);
//			for (Map.Entry<OWLObjectPropertyExpression, Set<OWLIndividual>> mappedProperty : objectPropertyValues.entrySet())
//			{
//				OWLObjectPropertyExpression ex = mappedProperty.getKey();
//				if(ex instanceof OWLObjectProperty)
//				{
//					final OWLObjectProperty prop = (OWLObjectProperty)ex;
//					Set<OWLIndividual> set = objectPropertyValues.get(prop);
//					if(set.size() > 0)
//					{
//						OWLNamedIndividual entity = set.iterator().next().asOWLNamedIndividual();
//						Map<OWLClass, OWLNamedIndividual> hasOneMapping = Mapping.getInstance().getTableMapping(entity.getTypes(o));
//						delete(o, entity, hasOneMapping, columnMapping, identifiers, statements, done);
//					}
//				}
//			}
//		}
//	}
//
//	
//
//
//
//
//
//
////	public int getMaxVarcharSize()
////	{
////		return maxVarcharSize;
////	}
////
////	public void setMaxVarcharSize(int maxVarcharSize)
////	{
////		this.maxVarcharSize = maxVarcharSize;
////	}
//	
//	
//	public int[] insertDataProperties(OWLIndividual subject, Map<OWLDataPropertyExpression, Set<OWLLiteral>> data,
//			Map<OWLEntity, Long> identifiers,Map<String, Long> literalIdentifiers)
//	{
//		//throw new RuntimeException("THIS WONT WORK DUE TO NEW SCHEMA");
//		//TODO FIX THIS SOON!!
//		PreparedStatement stmt = null;
//		Connection conn = null;
//		ResultSet rs = null;
//		int[] result = new int[] {};
//		
//		Map<String, Long> values = literalIdentifiers;
//		if(values == null)
//			values = null; //TODO selectLiteralIDs(hash(data), true);
//		
//		StringBuffer insert = new StringBuffer();
//		insert.append("INSERT INTO ").append(TABLE_DATA_PROPERTY).append("(")
//				.append("SUBJECT")
//				.append(",PREDICATE")
//				.append(",DATATYPE")
//				.append(",LANG")
//				.append(",FROM_DATE")
//				.append(",VALUE_ID")
//				.append(",VALUE_HASH")
//				.append(") ")
//				.append(" VALUES ")
//				.append("(?,?,?,?,?,?,?)");
//		try
//		{
//			conn = getConnection();
//			conn.setAutoCommit(false);
//			stmt = conn.prepareStatement(insert.toString());
//			Timestamp now = new Timestamp(getStoreTime().getTime());
//			Long s = identifiers.get(subject.asOWLNamedIndividual());
//			for (Map.Entry<OWLDataPropertyExpression, Set<OWLLiteral>> e : data.entrySet())
//			{
//				if (e.getKey() instanceof OWLDataProperty)
//				{
//					Long p = identifiers.get(((OWLDataProperty) e.getKey()));
//					for (OWLLiteral value : e.getValue())
//					{
//						String hash = hash(value);
//						stmt.setLong(1, s);
//						stmt.setLong(2, p);
//						stmt.setString(3, value.getDatatype().getIRI().toString());
//						stmt.setString(4, value.getLang());
//						stmt.setTimestamp(5, now);
//						stmt.setLong(6, values.get(hash));
//						stmt.setString(7, hash);
//						stmt.addBatch();
//					}
//				}
//			}
//			result = stmt.executeBatch();
//			conn.commit();
//		}
//		catch (SQLException e)
//		{
//			e.printStackTrace();
//			try
//			{
//				conn.rollback();
//			}
//			catch (Throwable f)
//			{
//
//			}
//		}
//		finally
//		{
//			close(rs, stmt, conn);
//		}
//		return result;
//	}
	
//	public int[] updateDataProperties(OWLIndividual subject, Map<OWLDataPropertyExpression, Set<OWLLiteral>> data,
//			Map<OWLEntity, Long> identifiers, Map<String,Long> literalIdentifiers)
//	{
//		PreparedStatement stmt = null;
//		Connection conn = null;
//		ResultSet rs = null;
//		int[] result = new int[] {};
//
//		StringBuffer update = new StringBuffer();
//		update.append("UPDATE ").append(TABLE_DATA_PROPERTY).append(" SET TO_DATE= ? ").append(
//				"WHERE SUBJECT = ? AND TO_DATE IS NULL");
//
//		try
//		{
//			conn = getConnection();
//			conn.setAutoCommit(false);
//			Timestamp now = new Timestamp(getStoreTime().getTime());
//			stmt = conn.prepareStatement(update.toString());
//			Long s = identifiers.get(subject.asOWLNamedIndividual());
//			stmt.setTimestamp(1, now);
//			stmt.setLong(2, s);
//			stmt.executeUpdate();
//			conn.commit();
//			result = insertDataProperties(subject, data, identifiers, literalIdentifiers);
//		}
//		catch (SQLException e)
//		{
//			e.printStackTrace();
//			try
//			{
//				conn.rollback();
//			}
//			catch (Throwable f)
//			{
//
//			}
//		}
//		finally
//		{
//			close(rs, stmt, conn);
//		}
//		return result;
//	}

	
	public int[] update(Set<OWLClassAssertionAxiom> set, Set<OWLNamedIndividual> individuals, Map<OWLEntity, Long> identifiers)
	{
		PreparedStatement stmt = null;
		Connection conn = null;
		ResultSet rs = null;
		int[] result = new int[] {};

		StringBuffer update = new StringBuffer();
		update.append("UPDATE ").append(TABLE_CLASSIFICATION).append(" SET TO_DATE= ? ").append(
				"WHERE SUBJECT = ? AND TO_DATE IS NULL");

		try
		{
			conn = getConnection();
			conn.setAutoCommit(false);
			Timestamp now = new Timestamp(getStoreTimeInt().getTime());
			stmt = conn.prepareStatement(update.toString());
			
			for (OWLNamedIndividual i : individuals)
			{
				Long s = identifiers.get(i);
				stmt.setTimestamp(1, now);
				stmt.setLong(2, s);
				stmt.addBatch();
			}
			result = stmt.executeBatch();
			conn.commit();
			result = insert(set, identifiers);
		}
		catch (SQLException e)
		{
			e.printStackTrace();
			try
			{
				conn.rollback();
			}
			catch (Throwable f)
			{

			}
		}
		finally
		{
			close(rs, stmt, conn);
		}
		return result;
	}
//
	private int[] insert(Set<OWLClassAssertionAxiom> set, Map<OWLEntity, Long> identifiers)
	{
		PreparedStatement stmt = null;
		Connection conn = null;
		ResultSet rs = null;
		int[] result = new int[] {};

		StringBuffer insert = new StringBuffer();
		insert.append("INSERT INTO ").append(TABLE_CLASSIFICATION).append("(").append("SUBJECT").append(",OWLCLASS").append(
				",FROM_DATE").append(")").append("VALUES").append("(?,?,?)");
		try
		{
			conn = getConnection();
			conn.setAutoCommit(false);
			stmt = conn.prepareStatement(insert.toString());
			Timestamp now = new Timestamp(getStoreTimeInt().getTime());
			
			for(OWLClassAssertionAxiom axiom: set)
			{
				Long s = identifiers.get(axiom.getIndividual());
				OWLClassExpression expr = axiom.getClassExpression();
				if (expr instanceof OWLClass)
				{
					Long c = identifiers.get((OWLClass)expr);
					stmt.setLong(1, s);
					stmt.setLong(2, c);
					stmt.setTimestamp(3, now);
					stmt.addBatch();
				}
			}
			result = stmt.executeBatch();
			conn.commit();
		}
		catch (SQLException e)
		{
			e.printStackTrace();
			try
			{
				conn.rollback();
			}
			catch (Throwable f)
			{

			}
		}
		finally
		{
			close(rs, stmt, conn);
		}
		return result;
	}
//	
//	// NOT USED NOT TESTED
//	@Deprecated
//	public int[] updateDataProperties(Set<OWLDataPropertyAssertionAxiom> axioms, Set<OWLNamedIndividual> individuals,
//			Map<OWLEntity, Long> identifiers, Map<String, Long> literalIdentifiers)
//	{
//		PreparedStatement stmt = null;
//		Connection conn = null;
//		ResultSet rs = null;
//		int[] result = new int[] {};
//
//		StringBuffer update = new StringBuffer();
//		update.append("UPDATE ").append(TABLE_DATA_PROPERTY).append(" SET TO_DATE= ? ").append(
//				"WHERE SUBJECT = ? AND TO_DATE IS NULL");
//
//		try
//		{
//			conn = getConnection();
//			conn.setAutoCommit(false);
//			Timestamp now = new Timestamp(getStoreTime().getTime());
//			stmt = conn.prepareStatement(update.toString());
//			for (OWLNamedIndividual i : individuals)
//			{
//				Long s = identifiers.get(i);
//				stmt.setTimestamp(1, now);
//				stmt.setLong(2, s);
//				stmt.addBatch();
//			}
//			
//			stmt.executeBatch();
//			conn.commit();
//			result = insertDataProperties(axioms, identifiers, literalIdentifiers);
//		}
//		catch (SQLException e)
//		{
//			e.printStackTrace();
//			try
//			{
//				conn.rollback();
//			}
//			catch (Throwable f)
//			{
//
//			}
//		}
//		finally
//		{
//			close(rs, stmt, conn);
//		}
//		return result;
//		
//	}
//
//	public int[] insertDataProperties(Set<OWLDataPropertyAssertionAxiom> axioms, Map<OWLEntity, Long> identifiers,
//			Map<String, Long> literalIdentifiers)
//	{
//		PreparedStatement stmt = null;
//		Connection conn = null;
//		ResultSet rs = null;
//		int[] result = new int[] {};
//		
//		Map<String, Long> values = literalIdentifiers;
//		
//		StringBuffer insert = new StringBuffer();
//		insert.append("INSERT INTO ").append(TABLE_DATA_PROPERTY).append("(")
//				.append("SUBJECT")
//				.append(",PREDICATE")
//				.append(",DATATYPE")
//				.append(",LANG")
//				.append(",FROM_DATE")
//				.append(",VALUE_ID")
//				.append(",VALUE_HASH")
//				.append(") ")
//				.append(" VALUES ")
//				.append("(?,?,?,?,?,?,?)");
//		try
//		{
//			conn = getConnection();
//			conn.setAutoCommit(false);
//			stmt = conn.prepareStatement(insert.toString());
//			Timestamp now = new Timestamp(getStoreTime().getTime());
//			for(OWLDataPropertyAssertionAxiom axiom : axioms)
//			{
//				Long s = identifiers.get(axiom.getSubject());
//				OWLDataPropertyExpression expr = axiom.getProperty();
//				if (expr instanceof OWLDataProperty)
//				{
//					Long p = identifiers.get((OWLDataProperty)expr);
//					OWLLiteral value = axiom.getObject();
//					if(value != null)
//					{	try
//						{
//							String hash = hash(value);
//							stmt.setLong(1, s);
//							stmt.setLong(2, p);
//							stmt.setString(3, value.getDatatype().getIRI().toString());
//							stmt.setString(4, value.getLang());
//							stmt.setTimestamp(5, now);
//							stmt.setLong(6, values.get(hash));
//							stmt.setString(7, hash);
//							stmt.addBatch();	
//						}catch(NullPointerException e)
//						{
//							System.out.println("error no hash value for" + value);
//						}
//					}
//				}
//			}
//			result = stmt.executeBatch();
//			conn.commit();
//		}
//		catch (SQLException e)
//		{
//			e.printStackTrace();
//			try
//			{
//				conn.rollback();
//			}
//			catch (Throwable f)
//			{
//
//			}
//		} 
//		finally
//		{
//			close(rs, stmt, conn);
//		}
//		return result;
//	}
//	@java.lang.Deprecated
//	public int[] updateObjectProperties(Set<OWLObjectPropertyAssertionAxiom> axioms,
//			Set<OWLNamedIndividual> individuals, Map<OWLEntity, Long> identifiers)
//	{
//		PreparedStatement stmt = null;
//		Connection conn = null;
//		ResultSet rs = null;
//		int[] result = new int[] {};
//		// sets all active object properties as expired, including those outside
//		// the map.
//		StringBuffer update = new StringBuffer();
//		update.append("UPDATE ").append(TABLE_OBJECT_PROPERTY).append(" SET TO_DATE= ? ").append(
//				"WHERE SUBJECT = ? AND TO_DATE IS NULL");
//		try
//		{
//			conn = getConnection();
//			conn.setAutoCommit(false);
//			Timestamp now = new Timestamp(getStoreTime().getTime());
//			stmt = conn.prepareStatement(update.toString());
//			for (OWLNamedIndividual i : individuals)
//			{
//				Long s = identifiers.get(i);
//				stmt.setTimestamp(1, now);
//				stmt.setLong(2, s);
//				stmt.addBatch();
//			}
//			stmt.executeBatch();
//			conn.commit();
//			result = insertObjectProperties(axioms, identifiers);
//		}
//		catch (SQLException e)
//		{
//			e.printStackTrace();
//			try
//			{
//				conn.rollback();
//			}
//			catch (Throwable f)
//			{
//
//			}
//		}
//		finally
//		{
//			close(rs, stmt, conn);
//		}
//		return result;
//		
//	}
//
//	public int[] insertObjectProperties(Set<OWLObjectPropertyAssertionAxiom> axioms, Map<OWLEntity, Long> identifiers)
//	{
//		PreparedStatement stmt = null;
//		Connection conn = null;
//		ResultSet rs = null;
//		int[] result = new int[] {};
//
//		StringBuffer insert = new StringBuffer();
//		insert.append("INSERT INTO ").append(TABLE_OBJECT_PROPERTY).append("(").append("SUBJECT").append(",PREDICATE").append(
//				",OBJECT").append(",FROM_DATE").append(")").append("VALUES").append("(").append("?,?,?,?").append(")");
//		try
//		{
//			conn = getConnection();
//			conn.setAutoCommit(false);
//			Timestamp now = new Timestamp(getStoreTime().getTime());
//			stmt = conn.prepareStatement(insert.toString());
//			for (OWLObjectPropertyAssertionAxiom axiom: axioms)
//			{
//				Long s = identifiers.get(axiom.getSubject());
//				OWLObjectPropertyExpression expr = axiom.getProperty();
//				if (expr instanceof OWLObjectProperty)
//				{
//					Long p = identifiers.get((OWLObjectProperty) expr);
//					Long o = identifiers.get(axiom.getObject());
//					stmt.setLong(1, s);
//					stmt.setLong(2, p);
//					stmt.setLong(3, o);
//					stmt.setTimestamp(4, now);
//					stmt.addBatch();
//				}
//			}
//			result = stmt.executeBatch();
//			conn.commit();
//		}
//		catch (SQLException e)
//		{
//			e.printStackTrace();
//			try
//			{
//				conn.rollback();
//			}
//			catch (Throwable f)
//			{
//
//			}
//		}
//		finally
//		{
//			close(rs, stmt, conn);
//		}
//		return result;
//	}
//	

	
	
		
}

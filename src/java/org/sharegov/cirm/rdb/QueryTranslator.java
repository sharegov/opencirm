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

import static org.sharegov.cirm.OWL.*;

import static org.sharegov.cirm.rdb.RelationalOWLMapper.join;
import static org.sharegov.cirm.rdb.RelationalOWLMapper.table;
import static org.sharegov.cirm.rdb.RelationalOWLMapper.hasOne;
import static org.sharegov.cirm.rdb.RelationalOWLMapper.hasMany;
import static org.sharegov.cirm.rdb.RelationalOWLMapper.columnMapping;
import static org.sharegov.cirm.rdb.RelationalOWLMapper.columnIriPK;
import static org.sharegov.cirm.rdb.RelationalOWLMapper.foreignKeyByjoinColumnAndTable;
import static org.sharegov.cirm.rdb.Sql.SELECT;
import static org.sharegov.cirm.rdb.RelationalStoreImpl.VIEW_DATA_PROPERTY;
import static org.sharegov.cirm.rdb.RelationalStoreImpl.TABLE_OBJECT_PROPERTY;
import static org.sharegov.cirm.rdb.RelationalStoreImpl.TABLE_CLASSIFICATION;
import static org.sharegov.cirm.rdb.RelationalStoreImpl.TABLE_IRI;
import static org.sharegov.cirm.utils.GenUtils.pagination;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mjson.Json;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLProperty;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;

/**
 * Translates an JSON ontology query specification into a SQL statement against the DB
 * using both, mapped (horizontal) and generic (vertical) schema 
 * definitions in the meta ontology.
 * 
 * It also takes the OntologyTransformer for optimized generic schema storage into account.
 * 
 * @see legacy.js to learn about the JSON format or set DBG to true and submit search queryies.
 *   
 * @author SABBAS, PhaniUpadrasta, hilpold (answer search)
 */
public class QueryTranslator
{

	/**
	 * Triggers output of incoming JSON, resulting SQL and actual parameters.
	 */
	public static boolean DBG = false;
	public static Pattern FUNCTIONS_PATTERN;
	public static Pattern OPERATORS_PATTERN;

	public static final Map<Object, Operation> TRANSLATIONS = new HashMap<Object, Operation>()
	{
		private static final long serialVersionUID = 5203625794001774803L;
		{
			this.put(Function.greaterThan, Operation.SQL_GREATER_THAN);
			this.put(Function.lessThan, Operation.SQL_LESS_THAN);
			this.put(Function.contains, Operation.SQL_CONTAINS);
			this.put(Function.between, Operation.SQL_BETWEEN);
			this.put(Function.isNotNull, Operation.SQL_IS_NOT_NULL);
			this.put(Function.like, Operation.SQL_LIKE);
			this.put(Function.notLike, Operation.SQL_NOT_LIKE);
			this.put(Function.startsWith, Operation.SQL_STARTS_WITH);
			this.put(Function.in, Operation.SQL_IN);
			this.put(Operator.equals, Operation.SQL_EQUALS);
			this.put(Operator.greaterThan, Operation.SQL_GREATER_THAN);
			this.put(Operator.greaterThanOrEqual, Operation.SQL_GREATER_THAN_OR_EQUAL);
			this.put(Operator.lessThan, Operation.SQL_LESS_THAN);
			this.put(Operator.lessThanOrEqual, Operation.SQL_LESS_THAN_OR_EQUAL);
			this.put(Literal.Number, Operation.SQL_EQUALS);
			this.put(Literal.String, Operation.SQL_EQUALS);
			this.put(Literal.Null, Operation.SQL_NO_OPERATION);
			this.put(Keyword.itemsPerPage, Operation.SQL_PAGESIZE);
			this.put(Keyword.currentPage, Operation.SQL_CURRENTPAGE);
			this.put(Keyword.sortBy, Operation.SQL_ORDER_BY);
			this.put(Keyword.sortDirection, Operation.SQL_ORDER_DIRECTION);
		}
	};
	
	public static final String COLUMN_HASH = "VALUE_HASH";
	public static final String COLUMN_CLOB = "VALUE_CLOB";
	public static final String COLUMN_VARCHAR_LONG = "VALUE_VARCHAR_LONG";
	
	public static final Map<String, Operator> OPERATORS = new HashMap<String, Operator>()
	{
		private static final long serialVersionUID = 4705502271036265390L;
		{
			this.put("=", Operator.equals);
			this.put(">", Operator.greaterThan);
			this.put(">=",Operator.greaterThanOrEqual);
			this.put("<", Operator.lessThan);
			this.put("<=",Operator.lessThanOrEqual);
		}
	};

	//translate method for ASD Dispatch
	
	public Query translateASDDispatch(Json pattern, RelationalStore store)
	{
		if (DBG) System.out.println("translate \r\n:" + pattern);
		boolean caseSensitive = true; //Default case sensitive set to true;
		if(pattern.has("caseSensitive"))
			caseSensitive = pattern.atDel("caseSensitive").asBoolean();

		OWLOntology ont = ontology();
		Sql select = SELECT();
		Statement statement = new Statement();
		Query query = new Query();

		statement.setSql(select);
		query.setStatement(statement);
		query.setPattern(pattern);

		Map<OWLEntity, DbId> identifiers = new HashMap<OWLEntity, DbId>();
		Map<OWLEntity, OWL2Datatype> datatypes = new HashMap<OWLEntity, OWL2Datatype>();
		Json paginationJson = Json.object();
		Json paginationCriteria = Json.object();

		identifiers.putAll(store.selectIDsAndEntitiesByIRIs(collectDatatypeEntities(ont, pattern, datatypes, store)));

		OWLNamedIndividual reqTable = individual("CIRM_SR_REQUESTS");
		Set<OWLNamedIndividual> reqColumnIRISet = columnIriPK(reqTable);
		OWLNamedIndividual reqColumnIRI = reqColumnIRISet.iterator().next();
		Map<OWLProperty<?, ?>, OWLNamedIndividual> reqColumns = columnMapping(reqTable);
		OWLNamedIndividual addrTable = individual("CIRM_MDC_ADDRESS");
		OWLNamedIndividual addrColumnIRI = columnIriPK(addrTable).iterator().next();
		OWLNamedIndividual actTable = individual("CIRM_SR_ACTIVITY");

		select
			.COLUMN("CIRM_SR_REQUESTS.SR_REQUEST_ID").AS("SR_ID")
			.COLUMN("CIRM_SR_REQUESTS.CREATED_DATE").AS("CREATED_DATE")
			.COLUMN("CIRM_MDC_ADDRESS.FULL_ADDRESS").AS("ADDRESS")
			.COLUMN("CIRM_MDC_ADDRESS.ZIP").AS("ZIP")
			.COLUMN("CIRM_SR_ACTIVITY.ACTIVITY_CODE").AS("ACTIVITY")
			.COLUMN(TABLE_IRI+"_2"+".IRI").AS("ACTIVITY_IRI")
			.COLUMN("CIRM_SR_REQUESTS.SR_PRIORITY").AS("PRIORITY")
			.COLUMN("CIRM_SR_ACTIVITY.STAFF_ASSIGNED").AS("ASSIGNED_TO")
			.COLUMN("CIRM_SR_ACTIVITY.OUTCOME_CODE").AS("OUTCOME")
			.COLUMN("CIRM_SR_ACTIVITY.DETAILS").AS("DETAILS")
			.COLUMN("CIRM_SR_ACTIVITY.CREATED_DATE").AS("ACT_CREATED_DATE")
			.COLUMN("CIRM_SR_ACTIVITY.COMPLETE_DATE").AS("ACT_COMPLETED_DATE")
			.COLUMN("CIRM_SR_REQUESTS.SR_STATUS").AS("STATUS")
			.COLUMN(TABLE_IRI+"_1"+".IRI").AS("SR_TYPE")
			.COLUMN("CIRM_SR_REQUESTS.CASE_NUMBER").AS("CASE_NUMBER");
		//START : USER_FRIENDLY_ID
		//select.COLUMN("a.VALUE_VARCHAR").AS("USER_FRIENDLY_ID");
		//END : USER_FRIENDLY_ID
		select.FROM(reqTable.getIRI().getFragment());
		select.LEFT_OUTER_JOIN(addrTable.getIRI().getFragment())
			.ON("CIRM_SR_REQUESTS.SR_REQUEST_ADDRESS", addrColumnIRI.getIRI().getFragment());
		select.JOIN(actTable.getIRI().getFragment())
			.ON(reqColumnIRI.getIRI().getFragment(), "CIRM_SR_ACTIVITY.SR_REQUEST_ID");
		select.JOIN(TABLE_CLASSIFICATION)
			.ON(TABLE_CLASSIFICATION+".SUBJECT", reqColumnIRI.getIRI().getFragment());
		select.JOIN(TABLE_IRI +" "+TABLE_IRI+"_1")
			.ON(TABLE_CLASSIFICATION+".OWLCLASS", TABLE_IRI+"_1"+".ID");
		select.JOIN(TABLE_IRI +" "+TABLE_IRI+"_2")
		.ON("CIRM_SR_ACTIVITY.ACTIVITY_ID", TABLE_IRI+"_2"+".ID");
		select.AND();
		select.WHERE(TABLE_CLASSIFICATION+".TO_DATE IS NULL");
		List<String> owlClassTypeList = new LinkedList<String>();
		Json type = pattern.at("type");
		if(type.isArray())
		{
			for(Json j : type.asJsonList())
				owlClassTypeList.add(j.asString());
		}
		else
		{
			owlClassTypeList.add(type.asString());
		}
		if (!owlClassTypeList.contains("legacy:ServiceRequestType")) 
		{
			select.AND();
			if(owlClassTypeList.size() == 1)
			{
				select.WHERE(TABLE_CLASSIFICATION+".OWLCLASS").EQUALS("?");
				statement.getParameters().add(identifiers.get(owlClass(fullIri(type.asString()))));
				statement.getTypes().add(individual(fullIri(Concepts.INTEGER)));
			}
			else if(owlClassTypeList.size() > 1)
			{
				Set<String> longSet = new HashSet<String>();
				for(String owlClassStr : owlClassTypeList)
				{
					longSet.add(String.valueOf(identifiers.get(owlClass(owlClassStr))));
				}
					
				select.WHERE(TABLE_CLASSIFICATION+".OWLCLASS")
					.IN(longSet.toArray(new String[longSet.size()]));
			}
		}
		// END NEW TYPE HANDLING FOR PERMISSIONS 
		
		for(Entry<String,Json> property : pattern.asJsonMap().entrySet())
		{
			String key = property.getKey();
			Json value = property.getValue();
			
			if(key.equals("type"))
				continue;
			if(value.isNull())
				continue;

			if(ont.isDeclared(objectProperty(fullIri(key)), true))
			{
				if(value.isObject() && value.has("iri"))
				{
					OWLNamedIndividual datatypeInd = null;
					select.AND();
					if(reqColumns.containsKey(objectProperty(fullIri(key))))
					{
						select.WHERE(reqColumns.get(objectProperty(fullIri(key))).getIRI().getFragment());
						datatypeInd = objectProperty(reqColumns.get(objectProperty(fullIri(key))), "hasColumnType");
					}
					select.EQUALS("?");
					IRI tempValue = fullIri(value.at("iri").asString());
					statement.getParameters().add(toParameterString(false, tempValue.getFragment(), true));
					statement.getTypes().add(individual(datatypeInd.getIRI()));
				}
				else
				{
					buildASDDispatchInnerQuery(select, statement, ont, key, value, identifiers, datatypes, caseSensitive);
				}
			}
			else if(ont.isDeclared(dataProperty(fullIri(key)), true))
			{
				String column = null;
				if(reqColumns.containsKey(dataProperty(fullIri(key))))
				{
					column = reqColumns.get(dataProperty(fullIri(key))).getIRI().getFragment();
				}
				OWL2Datatype datatype = datatypes.get(dataProperty(fullIri(key)));
				addClause(select, statement, column, datatype, value, 0, true, true, false);
			}
			else 
			{
				Integer i = 0;
				miscQuery(select, statement, property, reqColumnIRISet, reqColumns, paginationCriteria, paginationJson, caseSensitive, i);
			}
		}
		
		if(paginationJson.has("minValue") && paginationJson.has("maxValue")) 
		{
			applyOperation(select, statement, Operation.SQL_PAGINATION, "minValue", null, false, true, caseSensitive, 0, paginationJson.at("minValue").asString());
			applyOperation(select, statement, Operation.SQL_PAGINATION, "maxValue", null, false, true, caseSensitive, 0, paginationJson.at("maxValue").asString());
		}

		if (DBG) System.out.println("Result: \r\n " + query.getStatement().getSql().SQL());
		if (DBG) System.out.println("parameters: \r\n " + query.getStatement().getParameters());
		return query;
	}
	
	public void buildASDDispatchInnerQuery(Sql select, Statement statement, 
			OWLOntology ont, String key, Json value, 
			Map<OWLEntity, DbId> identifiers,
			Map<OWLEntity, OWL2Datatype> datatypes, boolean caseSensitive) {
		
		Set<OWLClassExpression> types = new HashSet<OWLClassExpression>();
		OWLClass rootClass = owlClass(fullIri(value.at("type").asString()));
		types.add(rootClass);
		OWLNamedIndividual table = table(types);
		Set<OWLNamedIndividual> columnIRISet = columnIriPK(table);
		Map<OWLProperty<?, ?>, OWLNamedIndividual> columns = null;
		if(table != null)
			columns = columnMapping(table);
		for(Entry<String,Json> property : value.asJsonMap().entrySet())
		{
			if(property.getKey().equals("type"))
				continue;
			if(property.getValue().isNull())
				continue;
			
			IRI propKey = fullIri(property.getKey());
			Json propValue = property.getValue();

			if(ont.isDeclared(objectProperty(propKey), true))
			{
				select.AND();
				if(columns.containsKey(objectProperty(propKey)))
				{
					select.WHERE(columns.get(objectProperty(propKey)).getIRI().getFragment());
					OWLNamedIndividual datatype = objectProperty(columns.get(objectProperty(propKey)), "hasColumnType");
					select.EQUALS("?");
					IRI tempValue = null;
					if(propValue.isObject() && propValue.has("iri"))
						tempValue = fullIri(propValue.at("iri").asString());
					statement.getParameters().add(toParameterString(false, tempValue.getFragment(), true));
					statement.getTypes().add(individual(datatype.getIRI()));
				}
			}
			else if(ont.isDeclared(dataProperty(propKey), true))
			{
				String column = null;
				if(columns.containsKey(dataProperty(propKey)))
				{
					column = columns.get(dataProperty(propKey)).getIRI().getFragment();
				}
				OWL2Datatype datatype = datatypes.get(dataProperty(propKey));
				addClause(select, statement, column, datatype, propValue, 0, true, true, false);
			}
			else 
			{
				Integer i = 0;
				miscQuery(select, statement, property, columnIRISet, columns, null, null, caseSensitive, i);
			}
		}
	}
	
	//Both mapping and noMapping query translate 
	public Query translate(Json pattern, RelationalStore store) 
	{
		if (DBG) System.out.println("translate \r\n:" + pattern);
		boolean caseSensitive = true; //Default case sensitive set to true;
		if(pattern.has("caseSensitive"))
			caseSensitive = pattern.atDel("caseSensitive").asBoolean();
		OWLOntology ont = ontology();
		Sql select = SELECT().DB(Refs.defaultPersister.resolve().getDataSourceRef().getHook());
		Statement statement = new Statement();
		Query query = new Query();

		statement.setSql(select);
		query.setStatement(statement);
		query.setPattern(pattern);

		//type can be string or Array or strings
		Json type = null;
		if(pattern.has("type"))
			type = pattern.at("type");
		//if(type == null)
			//throw new IllegalArgumentException("Missing \"type\" property in pattern.");
		Map<OWLEntity, DbId> identifiers = new HashMap<OWLEntity, DbId>();
		Map<OWLEntity, OWL2Datatype> datatypes = new HashMap<OWLEntity, OWL2Datatype>();
		Json paginationJson = Json.object();
		Json paginationCriteria = Json.object();
		Json boid = null;
		if(pattern.has("boid"))
			boid = pattern.at("boid");
		if(boid == null)
			identifiers.putAll(store.selectIDsAndEntitiesByIRIs(collectDatatypeEntities(ont, pattern, datatypes, store)));
		Set<OWLClass> types = new HashSet<OWLClass>();
		OWLClass rootClass = null;
		if(type != null) {
			if(type.isArray())
			{
				List<Object> typeStrs = type.asList();
				if (!typeStrs.isEmpty())
					rootClass = owlClass(fullIri((String)typeStrs.get(0)));
				else
					rootClass = OWL.dataFactory().getOWLNothing();
			}
			else
				rootClass = owlClass(fullIri(type.asString()));
			query.setRootClass(rootClass);
			types.add(rootClass);
		}
		identifiers.putAll(store.selectIDsAndEntitiesByIRIs(types));
		
		OWLNamedIndividual table = table(types);
		Set<OWLNamedIndividual> columnIRI = null;
		IRI column = null;
		Map<OWLProperty<?, ?>, OWLNamedIndividual> columns = null;
		
		if(table == null)
		{
			if(boid == null)
			{
				select.COLUMN(TABLE_CLASSIFICATION+".SUBJECT");
				select.FROM(TABLE_CLASSIFICATION);
				if(rootClass != null)
				{
					select.WHERE(TABLE_CLASSIFICATION+".OWLCLASS").EQUALS("?");
					statement.getParameters().add(identifiers.get(rootClass));
					statement.getTypes().add(individual(fullIri(Concepts.INTEGER)));
					select.AND();
				}
				select.WHERE(TABLE_CLASSIFICATION+".TO_DATE IS NULL");
			}
			else if(boid != null)
			{
				select.COLUMN(TABLE_IRI+".ID");
				select.FROM(TABLE_IRI);
				select.WHERE(TABLE_IRI+".ID").EQUALS(boid.getValue().toString());
			}
		}
		else if(table != null)
		{
			columnIRI = columnIriPK(table);
			columns = columnMapping(table);
			if(!columnIRI.isEmpty()) 
			{
				column = columnIRI.iterator().next().getIRI();
				select.COLUMN(column.getFragment());
				if(boid != null)
					select.WHERE(column.getFragment()).EQUALS(boid.getValue().toString());
			}
			select.FROM(table.getIRI().getFragment());
			
			List<String> owlClassTypeList = new LinkedList<String>();
			if(type.isArray())
			{
				for(Json j : type.asJsonList())
					owlClassTypeList.add(j.asString());
			}
			else
			{
				owlClassTypeList.add(type.asString());
			}
			//Join on CIRM_CLASSIFICATION table if type is present. 
			if(!owlClassTypeList.contains("legacy:ServiceRequestType") && column != null) 
			{
				select.JOIN(TABLE_CLASSIFICATION).ON(TABLE_CLASSIFICATION+".SUBJECT", column.getFragment());
				if(owlClassTypeList.size() == 1)
				{
					select.WHERE(TABLE_CLASSIFICATION+".OWLCLASS").EQUALS("?");
					String typeStr = type.isArray()? type.at(0).asString() : type.asString();
					statement.getParameters().add(identifiers.get(owlClass(fullIri(typeStr))));
					statement.getTypes().add(individual(fullIri(Concepts.INTEGER)));
				}
				else if(owlClassTypeList.size() > 1)
				{
					Set<String> longSet = new HashSet<String>();
					for(String owlClassStr : owlClassTypeList)
					{
						longSet.add(String.valueOf(identifiers.get(owlClass(owlClassStr))));
					}
						
					select.WHERE(TABLE_CLASSIFICATION+".OWLCLASS")
						//.IN(owlClassTypeList.toArray(new String[owlClassTypeList.size()]));
						.IN(longSet.toArray(new String[longSet.size()]));
				}
				select.AND();
				select.WHERE(TABLE_CLASSIFICATION+".TO_DATE IS NULL");
			}
		}
		
		Integer i = 0;
		if(boid == null)
		{
			for(Entry<String,Json> property : pattern.asJsonMap().entrySet())
			{
				if(property.getKey().equals("type"))
					continue;
				if(property.getValue().isNull())
					continue;
				if(ont.isDeclared(objectProperty(fullIri(property.getKey())), true))
				{
					if(columns != null && columns.containsKey(objectProperty(fullIri(property.getKey()))))
					{
						if(property.getValue().isArray())
						{
							for(int j=0; j<property.getValue().asList().size(); j++)
							{
								if(j==0)
									select.AND();
								if(j!=0)
									select.OR_ARRAY();
								buildMappedObjectQuery(select, statement, columns, property.getKey(), 
										property.getValue().at(j), identifiers);
							}
						}
						else 
						{
							select.AND();
							buildMappedObjectQuery(select, statement, columns, property.getKey(), 
									property.getValue(), identifiers);
						}
					}
					else 
					{
						if(!property.getValue().isObject())
							buildNotMappedObjectQuery(select, statement, identifiers, property.getKey(), 
									property.getValue(), ++i);
						else if(property.getValue().isObject())
							buildInnerObjectQuery(ont, select, statement, property.getKey(), 
									property.getValue(), identifiers, datatypes, table, caseSensitive, ++i);
					}
				}
				else if(ont.isDeclared(dataProperty(fullIri(property.getKey())), true))
				{
					if(columns != null && columns.containsKey(dataProperty(fullIri(property.getKey())))) 
					{
						if(property.getValue().isArray()) 
						{
							for(int j=0; j<property.getValue().asList().size(); j++) 
							{
								if(j==0)
									select.AND();
								if(j!=0)
									select.OR_ARRAY();
								buildMappedDataQuery(select, statement, columns, property.getKey(), 
										property.getValue().at(j), datatypes, caseSensitive, i);
							}
						}
						else 
						{
							select.AND();
							buildMappedDataQuery(select, statement, columns, property.getKey(), 
									property.getValue(), datatypes, caseSensitive, i);
						}
					}
					else 
					{
						++i;
						//select.JOIN(TABLE_DATA_PROPERTY+" "+TABLE_DATA_PROPERTY+i).ON(TABLE_CLASSIFICATION+".SUBJECT", TABLE_DATA_PROPERTY+i+".SUBJECT");
						select.JOIN(VIEW_DATA_PROPERTY+" "+VIEW_DATA_PROPERTY+i);
						if(table != null && column != null)
							select.ON(column.getFragment(), VIEW_DATA_PROPERTY+i+".SUBJECT_ID");
						else
							select.ON(TABLE_CLASSIFICATION+".SUBJECT", VIEW_DATA_PROPERTY+i+".SUBJECT_ID");
						buildNotMappedDataQuery(select, statement, property.getKey(), property.getValue(), 
								identifiers, datatypes, false, caseSensitive, i);
					}
				}
				else if (ont.containsIndividualInSignature(fullIri(property.getKey()), true)) {
					//2012.11.19 hilpold:
					//Query against transformed ontology where dataproperty or objectproperty name is taken from a declared individual (e.g. from a ServiceField)
					//This will always result in a non mapped query.
					//To Phani: After hasAnswerObject is implemented, if then the json value is an IRI we should assume it to be stored as object property.
					//Currently, as choicevalues are stored as data property values, we always need to add a clause for the Data property view.
					++i;
					if (property.getValue().isObject()) 
					{
						//Data property query {literal: datatype:}
						select.JOIN(VIEW_DATA_PROPERTY+" "+VIEW_DATA_PROPERTY+i).ON(TABLE_CLASSIFICATION+".SUBJECT", VIEW_DATA_PROPERTY+i+".SUBJECT_ID");
						buildNotMappedTransformedOntoDataQuery(select, statement, property.getKey(), property.getValue(), identifiers, caseSensitive, i);
					} 
					else 
					{
						//Assuming key is a punned objectProperty that's not defined in OWL.ontology()
						buildNotMappedObjectQuery(select, statement, identifiers, property.getKey(), property.getValue(), i);
					}
				}
				else 
				{
					System.out.println(statement.getSql().SQL());
					//This is where transformed ontology query goes.
					try
					{
						miscQuery(select, statement, property, columnIRI, columns, paginationCriteria, paginationJson, caseSensitive, i);
					}catch(Exception e)
					{
						e.printStackTrace();
					}
				}
			} //for
		}
		else
		{
			for(Map.Entry<String,Json> property : pattern.asJsonMap().entrySet())
			{
				if(property.getKey().equals("boid") || 
						property.getKey().equals("type") || 
						property.getValue().isNull())
					continue;
				if(ont.isDeclared(objectProperty(fullIri(property.getKey())), true)
					|| 
					ont.isDeclared(dataProperty(fullIri(property.getKey())), true))
					continue;
				else
					miscQuery(select, statement, property, columnIRI, columns, paginationCriteria, paginationJson, caseSensitive, i);
			}
		}
		if(paginationJson.has("minValue") && paginationJson.has("maxValue")) 
		{
			applyOperation(select, statement, Operation.SQL_PAGINATION, "minValue", null, false, true, caseSensitive, i, paginationJson.at("minValue").asString());
			applyOperation(select, statement, Operation.SQL_PAGINATION, "maxValue", null, false, true, caseSensitive, i, paginationJson.at("maxValue").asString());
		}

		if (DBG) System.out.println("Result: \r\n " + query.getStatement().getSql().SQL());
		if (DBG) System.out.println("parameters: \r\n " + query.getStatement().getParameters());
		return query;
	}

	private void miscQuery(Sql select, Statement statement, 
			Entry<String,Json> property, Set<OWLNamedIndividual> columnIRI, 
			Map<OWLProperty<?, ?>, OWLNamedIndividual> columns, Json paginationCriteria, Json paginationJson, 
			boolean caseSensitive, Integer i)
	{
		if (Keyword.valueOf(property.getKey()) != null) {
			if(Operation.SQL_ORDER_BY.equals(TRANSLATIONS.get(Keyword.valueOf(property.getKey()))))
			{
				if(property.getValue().asString().equals("boid")) {
					String cc = columnIRI.iterator().next().getIRI().getFragment();
					applyOperation(select, statement, Operation.SQL_ORDER_BY, cc, null, false, true, caseSensitive, i, cc);
				}
				//sort by SR Type
				else if(property.getValue().asString().equals("type"))
				{
					if(columnIRI != null)
					{
						select.JOIN(TABLE_CLASSIFICATION+" "+TABLE_CLASSIFICATION+"Type");
						select.ON(TABLE_CLASSIFICATION+"Type"+".SUBJECT", 
								columnIRI.iterator().next().getIRI().getFragment());
						select.JOIN(TABLE_IRI+" "+TABLE_IRI+"Type");
						select.ON(TABLE_IRI+"Type"+".ID", TABLE_CLASSIFICATION+"Type"+".OWLCLASS");
						select.AND().WHERE(TABLE_CLASSIFICATION+"Type"+".TO_DATE IS NULL");
					}
					else {
						select.JOIN(TABLE_IRI+" "+TABLE_IRI+"Type");
						select.ON(TABLE_IRI+"Type"+".ID", TABLE_CLASSIFICATION+".OWLCLASS");
					}
					String column = TABLE_IRI+"Type"+".IRI";
					applyOperation(select, statement, Operation.SQL_ORDER_BY, column, null, false, true, caseSensitive, i, column);
				}
				else if(columns != null) {
					OWLNamedIndividual c = columns.get(dataProperty(fullIri(property.getValue().asString())));
					if(c == null)
						c = columns.get(objectProperty(fullIri(property.getValue().asString())));
					if(c != null) {
						String cc = c.getIRI().getFragment();
						applyOperation(select, statement, Operation.SQL_ORDER_BY, cc, null, false, true, caseSensitive, i, cc);
					} else 
					{
						//Assuming GEO_Column or other valid column.
						String column = property.getValue().asString();
						applyOperation(select, statement, Operation.SQL_ORDER_BY, column, null, false, true, caseSensitive, i, column);
					}
				}
			}
			if(Operation.SQL_ORDER_DIRECTION.equals(TRANSLATIONS.get(Keyword.valueOf(property.getKey()))))
			{
				select.ORDER_DIRECTION(property.getValue().asString());
			}
			if(Operation.SQL_PAGESIZE.equals(TRANSLATIONS.get(Keyword.valueOf(property.getKey()))))
			{ 
				if(property.getValue() != null)
					paginationCriteria.set("itemsPerPage", property.getValue().asInteger());
				if(paginationCriteria.has("currentPage") && paginationCriteria.has("itemsPerPage"))
					pagination(paginationJson, paginationCriteria);
			}
			if(Operation.SQL_CURRENTPAGE.equals(TRANSLATIONS.get(Keyword.valueOf(property.getKey()))))
			{
				if(property.getValue() != null)
					paginationCriteria.set("currentPage", property.getValue().asInteger());
				if(paginationCriteria.has("currentPage") && paginationCriteria.has("itemsPerPage"))
					pagination(paginationJson, paginationCriteria);
			}
		}
	}

	private Set<OWLEntity> collectDatatypeEntities(OWLOntology ont, Json pattern, 
									Map<OWLEntity, OWL2Datatype> datatypes, 
									RelationalStore store) 
	{
		Set<OWLEntity> ent = new HashSet<OWLEntity>();
		for(Map.Entry<String,Json> property : pattern.asJsonMap().entrySet()) 
		{
			IRI prop = fullIri(property.getKey());
			if(property.getKey().equals("type"))
			{
				if(property.getValue().isArray())
				{
					for(Json j : property.getValue().asJsonList())
						ent.add(owlClass(j.asString()));
				}
				else
					ent.add(owlClass(property.getValue().asString()));
			}
			else if(ont.isDeclared(dataProperty(prop), true))
			{
				OWLDataProperty p = dataProperty(prop);
				ent.add(p);
				datatypes.put(p, OWL.getPropertyType(ontologies(), p).getBuiltInDatatype());
			}
			else if(ont.isDeclared(objectProperty(prop), true))
			{
				ent.add(objectProperty(prop));
				if(property.getValue().isObject())
					innerObject(ont, property, ent, datatypes);
				else if(!property.getValue().isObject()) 
				{
					if(property.getValue().isArray()) 
					{
						for(int j=0; j<property.getValue().asList().size(); j++) 
						{
							Json obj = property.getValue().at(j);
							if (obj.isObject())
							{
								ent.add(individual(obj.at("iri").asString()));
							}
							else
							{
								ent.add(individual(obj.getValue().toString()));
							}
						}
					}
					else
						ent.add(individual(fullIri(property.getValue().getValue().toString())));
				}
			}
			else if(ont.containsIndividualInSignature(prop, true))
			{
				//Test for transformed property
				OWLDataFactory df = Refs.tempOntoManager.resolve().getOWLDataFactory();
				OWLNamedIndividual candidateInd = df.getOWLNamedIndividual(prop); 
				if (Refs.ontologyTransformer.resolve().isOptimizedPredicate(candidateInd))
				{
					if (property.getValue().isObject())
						// for transformed query DataProp expects iri individual
						ent.add(df.getOWLDataProperty(prop));
					else 
					{
						ent.add(df.getOWLObjectProperty(prop));
						// for transformed query Object Prop expects iri individual
						String valueIRI = property.getValue().asString();
						if (valueIRI.startsWith("http://")) {
							ent.add(individual(valueIRI));
						} 
						else
						{
							throw new IllegalArgumentException("Transformed " + property.getKey() 
									+ " needs an IRI value; was: " + property.getValue().toString());
						}
					}
				}
				else //normal individual
				{
					ent.add(individual(prop));
				}	
			}
			else if(property.getKey().equals("sortBy")) 
			{
				IRI value = fullIri(property.getValue().getValue().toString());
				if(ont.isDeclared(dataProperty(value), true))
				{
					OWLDataProperty p = dataProperty(value);
					ent.add(p);
					datatypes.put(p, OWL.getPropertyType(ontologies(), p).getBuiltInDatatype());
				}
				else if(ont.isDeclared(objectProperty(value), true))
					ent.add(objectProperty(value));
			}
		}
		return ent;
	}

	
	private void buildMappedObjectQuery(Sql select, Statement statement, 
										Map<OWLProperty<?, ?>, OWLNamedIndividual> columns,
										String key, Json value, Map<OWLEntity, DbId> identifiers) 
	{
		String column = columns.get(objectProperty(fullIri(key))).getIRI().getFragment();
		OWLNamedIndividual datatype = objectProperty(columns.get(objectProperty(fullIri(key))), "hasColumnType");
		
		IRI tempValue = null;
		if(value.isObject() && value.has("iri"))
			tempValue = fullIri(value.at("iri").asString());

		select.WHERE(column);
		select.EQUALS("?");

		Set<OWLLiteral> literals = reasoner().getDataPropertyValues(individual(key), dataProperty("storeFragment"));
		
		if(!literals.isEmpty() && literals.iterator().next().getLiteral().equals("true"))
			statement.getParameters().add(toParameterString(false, tempValue.getFragment(), true));
		else
			statement.getParameters().add(identifiers.get(individual(tempValue)));
		statement.getTypes().add(individual(datatype.getIRI()));
	}
	
	private void buildMappedDataQuery(Sql select, Statement statement, 
										Map<OWLProperty<?, ?>, OWLNamedIndividual> columns,
										String key, Json value,
										Map<OWLEntity, OWL2Datatype> datatypes,
										boolean caseSensitive, Integer i) 
	{
		String column = columns.get(dataProperty(fullIri(key))).getIRI().getFragment();
		OWL2Datatype datatype = datatypes.get(dataProperty(fullIri(key)));
		if(value.isString())
			if(value.equals("*"))
				applyOperation(select, statement, Operation.SQL_LIKE, column, datatype, false, true, caseSensitive, i, "'%'");
			else
				addClause(select, statement, column, datatype, value, i, true, true, caseSensitive);
		else 
			applyOperation(select, statement, Operation.SQL_EQUALS, column, datatype, true, true, caseSensitive, i, value.toString());
	}
		
	private void buildNotMappedDataQuery(Sql select, Statement statement, String key, Json value, 
			Map<OWLEntity, DbId> identifiers, Map<OWLEntity, OWL2Datatype> datatypes, 
			boolean mapping, boolean caseSensitive, Integer i)
	{
		select.WHERE(VIEW_DATA_PROPERTY+i+".TO_DATE IS NULL");
		select.AND();
		select.WHERE(VIEW_DATA_PROPERTY+i+".PREDICATE_ID").EQUALS("?");
		statement.getParameters().add(identifiers.get(dataProperty(fullIri(key))));
		statement.getTypes().add(individual(fullIri(Concepts.INTEGER)));
		select.AND();
		OWL2Datatype datatype = datatypes.get(dataProperty(key));
		if(!value.isArray()) {
			if(value.isString())	
				if(value.equals("*"))
					applyOperation(select, statement, Operation.SQL_LIKE, null, datatype, 
							false, mapping, caseSensitive, i, "'%'");
				else
					addClause(select, statement, null, datatype, value, i, true, mapping, caseSensitive);
			else
				applyOperation(select, statement, Operation.SQL_EQUALS, null, datatype, 
						true, mapping, caseSensitive, i, value.toString());
		}
		else if(value.isArray()) {
			for(int j=0; j<value.asList().size(); j++) 
			{
				if(j!= 0)
					select.OR_ARRAY();
				if(value.at(j).isString())
					if(value.at(j).equals("*"))
						applyOperation(select, statement, Operation.SQL_LIKE, null, datatype, 
								false, mapping, caseSensitive, i, "'%'");
					else
						addClause(select, statement, null, datatype, value.at(j), i, true, mapping, caseSensitive);
				else
					applyOperation(select, statement, Operation.SQL_EQUALS, null, datatype, 
							true, mapping, caseSensitive, i, value.at(j).getValue().toString());
			}
		}
	}

	/**
	 * Adds where clauses and parameters to the current SQL statement for a data property considering ontology transformation as defined in OntologyTransformer.
	 * @param select
	 * @param statement
	 * @param key the name of the transformed (punned) data property e.g. a servicequestion IRI
	 * @param literalQueryObject {datatype: "<a full XSD datatype IRI>", literal:"<an operation with parameters e.g between>"};
	 * @param identifiers a map containing the storage id for a key based dataproperty
	 * @param datatypes 
	 * @param mapping
	 * @param i a number to be appended to the SQL name of VIEW_DATA_PROPERTY for uniqueness in the SQL context.
	 */
	private void buildNotMappedTransformedOntoDataQuery(Sql select, Statement statement, String key, Json literalQueryObject, 
			Map<OWLEntity, DbId> identifiers, boolean caseSensitive, Integer i)
	{
		select.WHERE(VIEW_DATA_PROPERTY+i+".TO_DATE IS NULL");
		select.AND();
		select.WHERE(VIEW_DATA_PROPERTY+i+".PREDICATE_ID").EQUALS("?");
		//can we get an id for the dataproperty, if not
		Long datapropertyId = identifiers.get(dataProperty(fullIri(key))).getFirst();
		statement.getParameters().add(datapropertyId);
		statement.getTypes().add(individual(fullIri(Concepts.INTEGER)));
		select.AND();
		// Retrieve the datatype from the JSON, must be builtin
		String datatypeIRI = literalQueryObject.at("datatype").asString();
		Json literalQueryJson = literalQueryObject.at("literal");
		String literalQueryString = literalQueryJson.asString();
		OWL2Datatype datatype = OWL2Datatype.getDatatype(IRI.create(datatypeIRI));
		if(literalQueryString.equals("*")) 
		{
			applyOperation(select, statement, Operation.SQL_LIKE, null, datatype, false, false, caseSensitive, i, "'%'");
		}
		else
		{
			addClause(select, statement, null, datatype, literalQueryJson, i, true, false, caseSensitive);			
		}
	}
	
	private void buildNotMappedObjectQuery(Sql select, Statement statement, 
											Map<OWLEntity, DbId> identifiers, 
											String key, Json value, 
											Integer i) 
	{
		select.JOIN(TABLE_OBJECT_PROPERTY +" "+TABLE_OBJECT_PROPERTY+i).ON(TABLE_CLASSIFICATION+".SUBJECT", TABLE_OBJECT_PROPERTY+i+".SUBJECT");
		select.AND();
		select.WHERE(TABLE_OBJECT_PROPERTY+i+".TO_DATE IS NULL");
		select.AND();
		select.WHERE(TABLE_OBJECT_PROPERTY+i+".PREDICATE").EQUALS("?");
		statement.getParameters().add(identifiers.get(objectProperty(fullIri(key))));
		statement.getTypes().add(individual(fullIri(Concepts.INTEGER)));
		select.AND();
		if(value.isArray()) 
		{
			Set<String> longSet = new HashSet<String>();
			for(int j=0; j<value.asList().size(); j++) 
				longSet.add(String.valueOf(identifiers.get(individual(fullIri(value.at(j).getValue().toString())))));
			select.WHERE(TABLE_OBJECT_PROPERTY+i+".OBJECT").IN(longSet.toArray(new String[longSet.size()]));
		}
		else if(!value.isArray()) 
		{
			select.WHERE(TABLE_OBJECT_PROPERTY+i+".OBJECT").EQUALS("?");
			statement.getParameters().add(identifiers.get(individual(fullIri(value.getValue().toString()))));
			statement.getTypes().add(individual(fullIri(Concepts.INTEGER)));
		}
	}

	private void buildInnerObjectQuery(OWLOntology ont, Sql select, Statement statement, 
			String key, Json value, Map<OWLEntity, DbId> identifiers, 
			Map<OWLEntity, OWL2Datatype> datatypes, OWLNamedIndividual mappedTable,
			boolean caseSensitive, Integer i) 
	{
		boolean mapping = false;
		Json type = null;
		if (!value.isArray() && value.has("type"))
			type = value.at("type");
		Set<OWLClassExpression> types = new HashSet<OWLClassExpression>();
		if(type != null)
			types.add(owlClass(fullIri(type.asString())));
		
		OWLNamedIndividual table = table(types);
		Set<OWLNamedIndividual> columnIRI = null;
		Map<OWLProperty<?, ?>, OWLNamedIndividual> columns = null;

		if(table != null) {
			mapping = true;
			columnIRI = columnIriPK(table);
			columns = columnMapping(table);
		}
		if(value.isObject())
		{
			if(mapping)
			{

				String innerColumnPK = columnIRI.iterator().next().getIRI().getFragment();
				String innerTable = table.getIRI().getFragment();
				
				if(mappedTable != null) {
					//hasOne, hasMany
					OWLNamedIndividual hasOne = hasOne(objectProperty(fullIri(key)), mappedTable);
					OWLClass hasMany = hasMany(objectProperty(fullIri(key)));
					//select.AND();
					if(hasMany != null)
					{
						OWLNamedIndividual centerTable = join(mappedTable, table);
						String mappedColumn = columnIriPK(mappedTable).iterator().next().getIRI().getFragment();
						OWLNamedIndividual mappedColumnFK = foreignKeyByjoinColumnAndTable(
																individual(mappedColumn), centerTable);
						OWLNamedIndividual innerColumnFK = foreignKeyByjoinColumnAndTable(
																individual(innerColumnPK), centerTable);
						if(mappedColumnFK!= null)
							select.JOIN(centerTable.getIRI().getFragment()).ON(mappedColumn, mappedColumnFK.getIRI().getFragment());
						if(innerColumnFK!= null)
							select.JOIN(innerTable).ON(innerColumnFK.getIRI().getFragment(), innerColumnPK);
					}
					else if(hasOne != null)
						select.JOIN(innerTable).ON(hasOne.getIRI().getFragment(), innerColumnPK);
				}
				
				for(Map.Entry<String,Json> objproperty : value.asJsonMap().entrySet())
				{
					if(objproperty.getKey().equals("type"))
						continue;
					else if(ont.isDeclared(objectProperty(fullIri(objproperty.getKey())), true))
					{
						if(objproperty.getValue().isObject()) {
							if(objproperty.getValue().at("type") != null) 
							{
								buildInnerObjectQuery(ont, select, statement, objproperty.getKey(), 
										objproperty.getValue(), identifiers, datatypes, table, caseSensitive, ++i);
								continue;
							}
							else if(objproperty.getValue().has("iri")) {
								select.AND();
								buildMappedObjectQuery(select, statement, columns, objproperty.getKey(), objproperty.getValue(), identifiers);
							}
						}
						else if(!objproperty.getValue().isObject()) 
						{
							if(!objproperty.getValue().isArray()) 
							{
								select.AND();
								buildMappedObjectQuery(select, statement, columns, objproperty.getKey(), objproperty.getValue(), identifiers);
							}
							else if(objproperty.getValue().isArray()) 
							{
								for(int j=0; j<objproperty.getValue().asList().size(); j++) 
								{
									if(j==0)
										select.AND();
									if(j!=0)
										select.OR_ARRAY();
									buildMappedObjectQuery(select, statement, columns, objproperty.getKey(), objproperty.getValue().at(j), identifiers);
								}
							}
						}
					}
					else if(ont.isDeclared(dataProperty(fullIri(objproperty.getKey())), true))
					{
						if(!objproperty.getValue().isArray()) 
						{
							select.AND();
							buildMappedDataQuery(select, statement, columns, objproperty.getKey(), 
									objproperty.getValue(), datatypes, caseSensitive, i);
						}
						else if(objproperty.getValue().isArray()) 
						{
							for(int j=0; j<objproperty.getValue().asList().size(); j++) 
							{
								if(j==0)
									select.AND();
								if(j!=0)
									select.OR_ARRAY();
								buildMappedDataQuery(select, statement, columns, objproperty.getKey(), 
										objproperty.getValue().at(j), datatypes, caseSensitive, i);
							}
						}
					}
					else 
					{
						miscQuery(select, statement, objproperty, columnIRI, columns, null, null, caseSensitive, i);
					}
				}

			}
			else if(!mapping) 
			{
				for(Map.Entry<String,Json> objproperty : value.asJsonMap().entrySet()) 
				{
					if(objproperty.getKey().equals("type"))
						continue;
					else if(ont.isDeclared(objectProperty(fullIri(objproperty.getKey())), true))
					{
						buildNotMappedInnerQuery(select, statement, identifiers, key, mappedTable, ++i);
						subQueryObjectProperty(select, statement, identifiers, objproperty);
					}
					else if(ont.isDeclared(dataProperty(fullIri(objproperty.getKey())), true))
					{
						buildNotMappedInnerQuery(select, statement, identifiers, key, mappedTable, ++i);
						subQueryDataProperty(select, statement, identifiers, objproperty, datatypes, mapping, caseSensitive, i);
					}
					else 
					{
						miscQuery(select, statement, objproperty, columnIRI, columns, null, null, caseSensitive, i);
					}
				}
			}
		}
	}
	
	private void buildNotMappedInnerQuery(Sql select, Statement statement, 
			Map<OWLEntity, DbId> identifiers, String key,
			OWLNamedIndividual mappedTable, Integer i) 
	{
		if(mappedTable != null) 
		{
			String mappedColumn = columnIriPK(mappedTable).iterator().next().getIRI().getFragment();
			select.JOIN(TABLE_OBJECT_PROPERTY +" "+TABLE_OBJECT_PROPERTY+i).ON(mappedColumn, TABLE_OBJECT_PROPERTY+i+".SUBJECT");
		}
		else 
			select.JOIN(TABLE_OBJECT_PROPERTY +" "+TABLE_OBJECT_PROPERTY+i).ON(TABLE_CLASSIFICATION+".SUBJECT", TABLE_OBJECT_PROPERTY+i+".SUBJECT");

		select.AND();
		select.WHERE(TABLE_OBJECT_PROPERTY+i+".TO_DATE IS NULL");
		select.AND();
		select.WHERE(TABLE_OBJECT_PROPERTY+i+".PREDICATE").EQUALS("?");
		statement.getParameters().add(identifiers.get(objectProperty(fullIri(key))));
		statement.getTypes().add(individual(fullIri(Concepts.INTEGER)));
		select.AND();
		select.WHERE(TABLE_OBJECT_PROPERTY+i+".OBJECT");
	}

	private void subQueryDataProperty(Sql sql, Statement statement, 
			Map<OWLEntity, DbId> identifiers, Map.Entry<String,Json> property,
			Map<OWLEntity, OWL2Datatype> datatypes, boolean mapping, boolean caseSensitive, Integer i) 
	{
		Integer j = i+90;
		Sql select = SELECT();
		select.COLUMN(VIEW_DATA_PROPERTY+j+".SUBJECT_ID");
		select.FROM(VIEW_DATA_PROPERTY +" "+VIEW_DATA_PROPERTY+j);
		buildNotMappedDataQuery(select, statement, property.getKey(), 
				property.getValue(), identifiers, datatypes, mapping, caseSensitive, j);
		sql.IN(select);
	}
	
	private void subQueryObjectProperty(Sql selectOriginal, Statement statement, 
										Map<OWLEntity, DbId> identifiers, 
										Map.Entry<String,Json> property) 
	{
		Sql select = SELECT();
		select.COLUMN(TABLE_OBJECT_PROPERTY+".SUBJECT");
		select.FROM(TABLE_OBJECT_PROPERTY);
		select.WHERE(TABLE_OBJECT_PROPERTY+".TO_DATE IS NULL");
		select.AND();
		select.WHERE(TABLE_OBJECT_PROPERTY+".PREDICATE").EQUALS("?");
		statement.getParameters().add(identifiers.get(objectProperty(fullIri(property.getKey()))));
		statement.getTypes().add(individual(fullIri(Concepts.INTEGER)));
		select.AND();
		if(property.getValue().isObject()) 
		{
			for(Map.Entry<String,Json> objproperty : property.getValue().asJsonMap().entrySet()) 
			{
				if(objproperty.getKey().equals("type"))
					continue;
				else if(objproperty.getKey().equals("iri")) 
				{
					select.WHERE(TABLE_OBJECT_PROPERTY+".OBJECT").EQUALS("?");
					statement.getParameters().add(identifiers.get(individual(objproperty.getValue().getValue().toString())));
					statement.getTypes().add(individual(fullIri(Concepts.INTEGER)));
				}
			}
		}
		else if(!property.getValue().isObject()) 
		{
			if(property.getValue().isArray()) 
			{
				Set<String> longSet = new HashSet<String>();
				for(int j=0; j<property.getValue().asList().size(); j++) 
				{
					String val = property.getValue().at(j).getValue().toString();
					Long l = identifiers.get(individual(fullIri(val))).getFirst();
					if(l != null)
						longSet.add(String.valueOf(l));
				}
				select.WHERE(TABLE_OBJECT_PROPERTY+".OBJECT").IN(longSet.toArray(new String[longSet.size()]));
			}
			else if(!property.getValue().isArray()) 
			{
				select.WHERE(TABLE_OBJECT_PROPERTY+".OBJECT").EQUALS("?");
				statement.getParameters().add(identifiers.get(individual(property.getValue().getValue().toString())));
				statement.getTypes().add(individual(fullIri(Concepts.INTEGER)));
			}
		}
		selectOriginal.IN(select);
	}

	private void innerObject(OWLOntology ont, Map.Entry<String,Json> property, Set<OWLEntity> ent, Map<OWLEntity, OWL2Datatype> datatypes) 
	{
		for(Map.Entry<String,Json> objproperty : property.getValue().asJsonMap().entrySet()) 
		{
			IRI objprop = fullIri(objproperty.getKey());
			if(objproperty.getKey().equals("type"))
				ent.add(owlClass(objproperty.getValue().getValue().toString()));
			else if(objproperty.getKey().equals("iri"))
			{
				if(ont.isDeclared(individual(objproperty.getValue().asString()), true))
					ent.add(individual(objproperty.getValue().asString()));
				else if(ont.containsIndividualInSignature(fullIri(objproperty.getValue().asString()), true))
					ent.add(individual(objproperty.getValue().asString()));
			}
			else if(ont.isDeclared(dataProperty(objprop), true))
			{
				OWLDataProperty p = dataProperty(objprop);
				ent.add(p);
				datatypes.put(p, OWL.getPropertyType(ontologies(), p).getBuiltInDatatype());
			}
			else if(ont.isDeclared(objectProperty(objprop), true))
			{
				ent.add(objectProperty(objprop));
				if(objproperty.getValue().isObject())
					innerObject(ont, objproperty, ent, datatypes);
				else if(!objproperty.getValue().isObject()) 
					if(!objproperty.getValue().isArray())
						ent.add(individual(fullIri(objproperty.getValue().getValue().toString())));
					else if(objproperty.getValue().isArray())
						for(int j=0; j<objproperty.getValue().asList().size(); j++)
							ent.add(individual(objproperty.getValue().at(j).getValue().toString()));
			}
		}
	}

	private void addClause(Sql sql, Statement statement, 
							String column, OWL2Datatype datatype, 
							Json value, Integer i, 
							boolean parse, boolean mapping, boolean caseSensitive) 
	{
		Matcher matcher = FUNCTIONS_PATTERN.matcher(value.asString());
		if(matcher.matches())
		{
			Operation o = TRANSLATIONS.get(Function.valueOf(matcher.group(1)));
			String[] values = matcher.group(2).split(",");
				applyOperation(sql, statement, o, column, datatype, parse, mapping, caseSensitive, i, values);
			return;
		}
		matcher = OPERATORS_PATTERN.matcher(value.asString());
		if(matcher.matches())
		{
			Operation o = TRANSLATIONS.get(OPERATORS.get(matcher.group(1)));
				applyOperation(sql, statement, o, column, datatype, parse, mapping, caseSensitive, i, matcher.group(2));
			return;
		}
		if(mapping) 
		{
			try
			{
				Operation keywordOperation = TRANSLATIONS.get(Keyword.valueOf(column));
				applyOperation(sql, statement, keywordOperation, column, datatype, parse, mapping, caseSensitive, i, value.asString());
			}catch(IllegalArgumentException e)
			{
				applyOperation(sql, statement, Operation.SQL_EQUALS, column, datatype, parse, mapping, caseSensitive, i, value.asString());
			}
		}
		else
			applyOperation(sql, statement, Operation.SQL_EQUALS, column, datatype, parse, mapping, caseSensitive, i, value.asString());
	}

	private void applyOperation(Sql sql, Statement stmt, 
								Operation o, String column, OWL2Datatype datatype, 
								boolean parse, boolean mapping, boolean caseSensitive, 
								Integer i, String... values) 
	{
		if(values == null || values.length < 1)
			return;
		switch(o) {
			case SQL_EQUALS: 
			{
				if(mapping) 
				{
					if(caseSensitive == false)
					{
						sql.WHERE("UPPER("+column+")");
						sql.EQUALS("UPPER(?)");
					}
					else 
					{
						sql.WHERE(column);
						sql.EQUALS("?");
					}
					stmt.getParameters().add(toParameterString(parse, values[0], true));
					stmt.getTypes().add(individual(fullIri(TYPES.get(datatype))));
				}
				else 
				{
					if ((!OWL2Datatype.XSD_STRING.equals(datatype))
							|| (OWL2Datatype.XSD_STRING.equals(datatype) 
									&& values[0].length() <= RelationalStoreImpl.VALUE_VARCHAR_SIZE) 
								) {
						if(caseSensitive == false)
							sql.WHERE("UPPER("+getColumn(datatype, i)+")");
						else
							sql.WHERE(getColumn(datatype, i));
					} else {
						// String > 255
						if(caseSensitive == false)
							sql.WHERE("UPPER("+getColumnForCLOB(true, i)+")");
						else
							sql.WHERE(getColumnForCLOB(true, i));
						if (values[0].length() > RelationalStoreImpl.MAX_VARCHAR_SIZE) 
						{
							throw new IllegalArgumentException("CLOB SEARCH NOT YET IMPLEMENTED. Search Parameter too long: " + values[0]);
						}
					}
					if(caseSensitive == false)
						sql.EQUALS("UPPER(?)");
					else
						sql.EQUALS("?");
					stmt.getParameters().add(toParameterString(parse, values[0], true));
					stmt.getTypes().add(individual(fullIri(TYPES.get(datatype))));
				}
				break;
			}
			case SQL_LESS_THAN: 
			{
				if(mapping)
					sql.WHERE(column);
				else 
					sql.WHERE(getColumn(datatype, i));

				sql.LESS_THAN("?");
				stmt.getParameters().add(toParameterString(parse, values[0], true));
				stmt.getTypes().add(individual(fullIri(TYPES.get(datatype))));
				break;
			}
			case SQL_LESS_THAN_OR_EQUAL: 
			{
				if(mapping)
					sql.WHERE(column);
				else
					sql.WHERE(getColumn(datatype, i));

				sql.LESS_THAN_OR_EQUAL("?");
				stmt.getParameters().add(toParameterString(parse, values[0], true));
				stmt.getTypes().add(individual(fullIri(TYPES.get(datatype))));
				break;
			}
			case SQL_GREATER_THAN:
			{
				if(mapping)
					sql.WHERE(column);
				else
					sql.WHERE(getColumn(datatype, i));

				sql.GREATER_THAN("?");
				stmt.getParameters().add(toParameterString(parse, values[0], true));
				stmt.getTypes().add(individual(fullIri(TYPES.get(datatype))));
				break;
			}
			case SQL_GREATER_THAN_OR_EQUAL:
			{
				if(mapping)
					sql.WHERE(column);
				else
					sql.WHERE(getColumn(datatype, i));

				sql.GREATER_THAN_OR_EQUAL("?");
				stmt.getParameters().add(toParameterString(parse, values[0], true));
				stmt.getTypes().add(individual(fullIri(TYPES.get(datatype))));
				break;
			}
			case SQL_LIKE:
			{
				if(datatype.equals(OWL2Datatype.XSD_STRING))
				{
					String columnToUse = (mapping)? column : getColumn(datatype, i);
					// Check VALUE_VARCHAR and VALUE_VARCHAR_LONG values
					if(caseSensitive == false)
					{
						sql.WHERE("UPPER(" + columnToUse + ")");
						sql.LIKE("UPPER(?)");
					}
					else
					{
						sql.WHERE(columnToUse);
						sql.LIKE("?");
					}
					//stmt.getParameters().add(toParameterString(parse, "%" + values[0] + "%", true));
					stmt.getParameters().add(toParameterString(parse, values[0], true));
					stmt.getTypes().add(individual(fullIri(TYPES.get(datatype))));
					//sql.OR();
					//sql.COLUMN(getColumnForCLOB(true, i));
				}
				else 
				{
					if(mapping)
						sql.WHERE(column);
					else
						sql.WHERE(getColumn(datatype, i));
					sql.LIKE("?");
					stmt.getParameters().add(toParameterString(parse, values[0], true));
					stmt.getTypes().add(individual(fullIri(TYPES.get(datatype))));
				}
				break;
			}
			case SQL_BETWEEN:
			{
				if(values.length >= 2)
				{   
					if(mapping)
						sql.WHERE(column);
					else
						sql.WHERE(getColumn(datatype, i));

					sql.BETWEEN("?", "?");
					stmt.getParameters().add(toParameterString(parse, values[0], true));
					stmt.getTypes().add(individual(fullIri(TYPES.get(datatype))));
					stmt.getParameters().add(toParameterString(parse, values[1], true));
					stmt.getTypes().add(individual(fullIri(TYPES.get(datatype))));
				}
				break;
			}
			case SQL_IS_NOT_NULL:
			{
				if(mapping)
					sql.WHERE(column + " IS NOT NULL");
				else
					sql.WHERE(getColumn(datatype, i) + " IS NOT NULL");
				break;
			}
			case SQL_ORDER_BY:
			{
				sql.ORDER_BY(toParameterString(false, values[0], true));
				break;
			}
			case SQL_PAGINATION:
			{
				if(mapping)
					sql.PAGINATION(column, values[0]);
				else
					sql.PAGINATION(getColumn(datatype, i), values[0]);
				break;
			}
		}
	}
	
	private String getColumn(OWL2Datatype datatype, Integer i) {
		return VIEW_DATA_PROPERTY+i+"."+COLUMNS.get(datatype);
	}

	/**
	 * Returns a String for the column prefixed by VIEW_DATA_PROPERTY. longstring? VALUE_VARCHAR_LONG: VALUE_CLOB)
	 * @param longString if true
	 * @param i
	 * @return
	 */
	private String getColumnForCLOB(boolean longString, Integer i) {
		if (longString)
			return VIEW_DATA_PROPERTY+i+"."+ COLUMN_VARCHAR_LONG;
		else 
			return VIEW_DATA_PROPERTY+i+"."+ COLUMN_CLOB;
	}
	
	public static final Map<OWL2Datatype, String> COLUMNS = new HashMap<OWL2Datatype, String>()
	{
		private static final long serialVersionUID = 5700825482702684108L;
		{
			this.put(OWL2Datatype.XSD_INTEGER, "VALUE_INTEGER");
			this.put(OWL2Datatype.XSD_DATE_TIME, "VALUE_DATE");
			this.put(OWL2Datatype.XSD_DATE_TIME_STAMP, "VALUE_DATE");
			this.put(OWL2Datatype.XSD_DOUBLE, "VALUE_DOUBLE");
			this.put(OWL2Datatype.XSD_STRING, "VALUE_VARCHAR");
			//TODO hilpold 2012.07.24 Might need redesign; we have for String also VALUE_VARCHAR_LONG and VALUE_CLOB
		}
	};

	public static final Map<OWL2Datatype, String> TYPES = new HashMap<OWL2Datatype, String>()
	{
		private static final long serialVersionUID = 4395632485206058251L;
		{
			this.put(OWL2Datatype.XSD_INT, Concepts.INTEGER);
			this.put(OWL2Datatype.XSD_INTEGER, Concepts.INTEGER);
			this.put(OWL2Datatype.XSD_DATE_TIME, Concepts.TIMESTAMP);
			this.put(OWL2Datatype.XSD_DATE_TIME_STAMP, Concepts.TIMESTAMP);
			this.put(OWL2Datatype.XSD_DOUBLE, Concepts.DOUBLE);
			this.put(OWL2Datatype.XSD_STRING, Concepts.VARCHAR);
			//TODO hilpold 2012.07.24 Might need redesign; we have for String also Concept.CLOB
		}
	};

	private String toParameterString(boolean parse, String s, boolean noQuotes)//, char wildcardPrefix, char wildcardPostfix
	{
		String result = "";
		if(!parse)
			if(noQuotes)
				return s; 
			else
				return "'" + s + "'";
		Json j;
		if(s.matches("(\\b[0-9]+\\.([0-9]+\\b)?|\\.[0-9]+\\b)") || (s.charAt(0) == '"' && s.charAt(s.length()-1) == '"'))
			j = Json.read("{\"v\":" + s + "}").at("v");	
		else
			j = Json.read("{\"v\":\"" + s + "\"}").at("v");
		  
		if(j.isNull())
			result = " IS NULL ";
		if(j.isNumber())
			result = j.asString();
		if(j.isString())
			if(noQuotes)
				result = j.asString();
			else
				result = "'" + j.asString() + "'";
		return result;	
		//parsing an xsddatetime, ISO 8601 compliant.
		//Don't yet know if we need this to parse Json Dates
		//System.out.println(DatatypeFactory.newInstance().newXMLGregorianCalendar(arg));
	}
	
	static
	{
		StringBuilder pattern = new StringBuilder("\\s*+(");
		for (int i = 0; i < Function.values().length; i++)
		{
			pattern.append(Function.values()[i].getFunctionString());
			if (i < Function.values().length - 1)
				pattern.append("|");
		}
		pattern.append(")\\s*+\\(\\s*(.+)\\s*+\\)");
		FUNCTIONS_PATTERN = Pattern.compile(pattern.toString());
		
		//pattern.delete(0, pattern.length()-1);
		pattern = new StringBuilder("\\s*+(");
		for (int i = 0; i < Operator.values().length; i++)
		{
			pattern.append(Operator.values()[i].getOperatorString());
			if (i < Operator.values().length - 1)
				pattern.append("|");
		}
		pattern.append(")\\s*+(.+)");
		OPERATORS_PATTERN = Pattern.compile(pattern.toString());
	}
}

enum Function
{
	greaterThan("greaterThan"),
	lessThan("lessThan"),
	contains("contains"),
	between("between"),
	isNotNull("isNotNull"),
	like("like"),
	notLike("notLike"),
	startsWith("startsWith"),
	in("in");

	Function(String functionString)
	{
		this.functionString = functionString;
	}
	private String functionString;

	public String getFunctionString()
	{
		return functionString;
	}

	public void setFunctionString(String functionString)
	{
		this.functionString = functionString;
	}

}

enum Operator
{
	greaterThanOrEqual(">="), lessThanOrEqual("<="), equals("="), greaterThan(">"), lessThan("<");

	Operator(String operatorString)
	{
		this.operatorString = operatorString;
	}

	private String operatorString;

	public String getOperatorString()
	{
		return operatorString;
	}

	public void setOperatorString(String operatorString)
	{
		this.operatorString = operatorString;
	}

	@Override
	public String toString()
	{
		return operatorString;
	}
}

enum Conjunction
{
	and("and"), or("or");

	Conjunction(String conjunctionString)
	{
		this.conjunctionString = conjunctionString;
	}
	private String conjunctionString;

	public String getConjunctionString()
	{
		return conjunctionString;
	}

	public void setConjunctionString(String conjunctionString)
	{
		this.conjunctionString = conjunctionString;
	}

}

enum Literal
{
	Number, String, Null;

}

enum Keyword
{
	sortBy("sortBy"),
	sortDirection("sortDirection"),
	currentPage("currentPage"),
	itemsPerPage("itemsPerPage");

	Keyword(String keyword)
	{
		this.keyword = keyword;
	}
	
	private String keyword;

	public String getKeyword()
	{
		return keyword;
	}

	public void setKeyword(String keyword)
	{
		this.keyword = keyword;
	}

}

enum Operation
{
	SQL_EQUALS,
	SQL_NOT_LIKE,
	SQL_LIKE,
	SQL_LESS_THAN,
	SQL_GREATER_THAN,
	SQL_LESS_THAN_OR_EQUAL,
	SQL_GREATER_THAN_OR_EQUAL,
	SQL_BETWEEN,
	SQL_IS_NOT_NULL,
	SQL_STARTS_WITH,
	SQL_CONTAINS,
	SQL_IN,
	SQL_PAGESIZE,
	SQL_CURRENTPAGE,
	SQL_PAGINATION,
	SQL_ORDER_BY,
	SQL_ORDER_DIRECTION,
	SQL_NO_OPERATION;
}

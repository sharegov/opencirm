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

import java.sql.SQLException;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.sharegov.cirm.CirmTransaction;

/**
 * The basic ReationalStore interface with read only methods and txn for general use.
 * 
 * All methods in this interface are safe to call outside a CirmTransaction,
 * as transactions will be created transparently by the store for these methods.
 * 
 * RelationalStoreImpl has a wider interface and should only be used if more functionality is needed.
 * In such case, users need to make sure to create a CirmTransaction.
 * 
 * @author Thomas Hilpold
 *
 */
public interface RelationalStore
{

	/**
	 * Retrieves the current time at the relational store. A database query will
	 * be executed. Precision: Milliseconds.
	 * 
	 * @return a timezone neutral date object representing date and time at the
	 *         store.
	 */
	Date getStoreTime();
 
	/**
	 * @param query
	 * @return a Json array (ordered) with one Json object per resulting row containing properties for each column named. 
	 * @throws SQLException
	 */
	Json customSearch(Query query) throws SQLException;

	Json advancedSearch(Query query) throws SQLException;

	/**
	 * Returns a set of boids, ordered as specified in te query.
	 * 
	 * @param query
	 * @param df
	 * @return
	 */
	LinkedHashSet<Long> query(Query query, OWLDataFactory df);

	List<Map<String, Object>> query(Statement statement, OWLDataFactory df) throws Exception;
	
	Map<Long, OWLEntity> queryGetEntities(Query query, OWLDataFactory df) throws SQLException;

	<T> T txn(CirmTransaction<T> transaction);

	Map<OWLEntity, DbId> selectIDsAndEntitiesByIRIs(Set<? extends OWLEntity> entitiesWithIRIs);

	OWLEntity selectEntityByID(long id,	OWLDataFactory owlDataFactory);

}

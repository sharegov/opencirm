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

import static org.sharegov.cirm.OWL.individual;

import org.semanticweb.owlapi.model.OWLNamedIndividual;

/**
 * IRI Constants for RDB related Concepts that are also in our county ontology.
 * 
 * @author Syed Abbas, Thomas Hilpold
 */
public class Concepts
{
	// Classes Software_Object/DBObject 
	//public static final String BASE 			= "http://www.miamidade.gov/ontology#";
	public static final String BASE 			= "http://opencirm.org#";
	public static final String DBObject 		= BASE + "DBObject";
	public static final String DBSchema 		= BASE + "DBSchema";
	public static final String DBTable 			= BASE + "DBTable";
	public static final String DBColumn 		= BASE + "DBColumn";
	public static final String DBPrimaryKey 	= BASE + "DBPrimaryKey";
	public static final String DBForeignKey 	= BASE + "DBForeignKey";
	public static final String DBNoKey 			= BASE + "DBNoKey";
	
	// hilpoldQ shouldn't those have a common super property in county onto? 
	// OWL Object Properties
	public static final String hasTable 		= BASE + "hasTable";
	public static final String hasColumn 		= BASE + "hasColumn";
	public static final String hasTableMapping 	= BASE + "hasTableMapping";
	public static final String hasColumnMapping = BASE + "hasColumnMapping";
	public static final String hasColumnType 	= BASE + "hasColumnType";
	// RelationShips, OWL Object Properties
	public static final String hasJoinTable 	= BASE + "hasJoinTable";
	public static final String hasJoinColumn 	= BASE + "hasJoinColumn";
	public static final String isJoinedWithTable = BASE + "isJoinedWithTable";
	public static final String hasOne 			= BASE + "hasOne";
	public static final String hasMany 			= BASE + "hasMany";
	//2013.02.03 abbas added toOne property to restrict domain.
	public static final String toOne 			= BASE + "toOne";
	// OWL Data Properties
	public static final String storeFragment 	= BASE + "storeFragment"; //true^^string
	public static final String storeIRI_asString = BASE + "storeIRI_asString";
	// OWL Object Property 
	public static final String storeIRIOf 		= BASE + "storeIRIOf";
	// OWL Classes
	public static final String CLOB 			= BASE + "CLOB";
	//2012.04.10 hilpold abbas public static final String DATE = BASE + "DATE";
	public static final String TIMESTAMP 		= BASE + "TIMESTAMP";
	public static final String DOUBLE 			= BASE + "DOUBLE";
	public static final String INTEGER 			= BASE + "INTEGER";
	public static final String VARCHAR 			= BASE + "VARCHAR";
	public static final String IRI 				= BASE + "IRI";
	public static final String IRIKey 			= BASE + "IRIKey";
	
	public static final OWLNamedIndividual CLOB_Individual = individual(Concepts.CLOB); 

}

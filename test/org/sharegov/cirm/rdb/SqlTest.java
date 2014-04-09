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

import static org.sharegov.cirm.rdb.Sql.INSERT_INTO;
import static org.sharegov.cirm.rdb.Sql.MERGE_INTO;
import static org.sharegov.cirm.rdb.Sql.SELECT;
import static org.sharegov.cirm.rdb.Sql.UPDATE;

import mjson.Json;

import org.junit.Test;

public class SqlTest
{

	
	
	
	@Test
	public void testSELECT()
	{
		
		 String SELECT  = SELECT()
		 					.COLUMN("COLUMN1")
		 					.COLUMN("COLUMN2")
		 					.FROM("TABLEA")
		 					.SQL();
		
	}
	
	
	
	@Test
	public void testUPDATE()
	{
		String QUERY  = 
			 SELECT()
			.COLUMN("COLUMN1")
			.FROM("TABLEA")
			.JOIN("TABLEB").ON("COLUMN1", "COLUMN2")
			.WHERE("COLUMN1").GREATER_THAN("?")
			.AND()
			.WHERE("COLUMN2").EQUALS("?")
			.OR()
			.WHERE("COLUMN1 = 1 OR 1=0")
			.SQL();
		
		System.out.println(QUERY);
		
		//QUERY = INSERT_INTO("TABLEA").
		//		.VALUE
	}
	
	@Test
	public void testINSERT()
	{
		String SELECT  = 
			 SELECT()
			 
		
			.COLUMN("COLUMN1").AS("ALIAS1")
			.FROM("TABLEA").AS("A")
			.SQL();
	}
	
	@Test
	public void testAdvancedSearchQuery()
	{
		Sql select = SELECT();
		select
		.COLUMN("a.SR_REQUEST_ID").AS("SR_REQUEST_ID")
		.COLUMN("i2.IRI").AS("TYPE")
		.COLUMN("addr.FULL_ADDRESS").AS("FULL_ADDRESS")
		.COLUMN("addr.ZIP").AS("ZIP")
		.COLUMN("i1.IRI").AS("CITY")
		.COLUMN("a.SR_STATUS").AS("STATUS")
		.COLUMN("acts.COMPLETE_DATE").AS("COMPLETE_DATE")
		.COLUMN("a.CREATED_DATE").AS("CREATED_DATE")
		.COLUMN("CIRM_GIS_INFO.GIS_CMAINT").AS("GIS_CMAINT");
		select.FROM("CIRM_SR_REQUESTS").AS("a");
		String innerQuery = "(SELECT b.SR_REQUEST_ID, MAX(b.COMPLETE_DATE) lastActivityDate FROM CIRM_SR_ACTIVITY b GROUP BY b.SR_REQUEST_ID ORDER BY b.SR_REQUEST_ID ) tempSRActivity ";
		select.LEFT_OUTER_JOIN(innerQuery).ON("a.SR_REQUEST_ID", "tempSRActivity.SR_REQUEST_ID");
		select.LEFT_OUTER_JOIN("CIRM_SR_ACTIVITY acts").ON("tempSRActivity.lastActivityDate", "acts.COMPLETE_DATE");
		select.LEFT_OUTER_JOIN("CIRM_MDC_ADDRESS addr").ON("a.SR_REQUEST_ADDRESS", "addr.ADDRESS_ID");
		select.LEFT_OUTER_JOIN("CIRM_IRI i1").ON("addr.CITY", "i1.ID");
		select.LEFT_OUTER_JOIN("CIRM_CLASSIFICATION cl").ON("cl.SUBJECT","a.SR_REQUEST_ID");
		select.LEFT_OUTER_JOIN("CIRM_IRI i2").ON("cl.OWLCLASS", "i2.ID");
		select.LEFT_OUTER_JOIN("CIRM_GIS_INFO").ON("a.GIS_INFO_ID","CIRM_GIS_INFO.ID");
		select.WHERE("cl.TO_DATE IS NULL");

		System.out.println(select.SQL());
	}
	
	@Test
	public void testMERGE()
	{
		String table = "CIRM_SR_REQUESTS";
		String columnIRI = "CIRM_SR_REQUESTS.SR_REQUEST_ID";
		Sql MERGE  = 
			 MERGE_INTO("CIRM_SR_REQUESTS", "A")
			 .USING(SELECT().COLUMN("SR_REQUEST_ID").FROM("CIRM_SR_REQUESTS").WHERE("SR_REQUEST_ID").EQUALS("75036"), "B")
			 .ON(columnIRI.replace(table, "A"), columnIRI.replace(table, "B"))
			 .WHEN_MATCHED_THEN(UPDATE(table)
					 .SET("CIRM_SR_REQUESTS.SR_STATUS".replace(table, "A"), "'CLOSE'")
					 .WHERE(columnIRI.replace(table, "A")).EQUALS("75036"))
			 .WHEN_NOT_MATCHED_THEN(INSERT_INTO(table).VALUES(columnIRI.replace(table, "A"), "75036"));
		System.out.print(MERGE.SQL());
	}
	
	@Test
	public void testMERGEAlias()
	{
		String table = "CIRM_SR_REQUESTS";
		String columnIRI = "CIRM_SR_REQUESTS.SR_REQUEST_ID";
		Sql MERGE  = 
			 MERGE_INTO("CIRM_SR_REQUESTS", "A")
			 .USING(SELECT().COLUMN("?").AS("SR_REQUEST_ID").COLUMN("?").AS("SR_REQUEST_TYPE").FROM("CIRM_SR_REQUESTS").WHERE("SR_REQUEST_ID").EQUALS("75036"), "B")
			 .ON(columnIRI.replace(table, "A"), columnIRI.replace(table, "B"))
			 .WHEN_MATCHED_THEN(UPDATE(table)
					 .SET("CIRM_SR_REQUESTS.SR_STATUS".replace(table, "A"), "'CLOSE'")
					 .WHERE(columnIRI.replace(table, "A")).EQUALS("75036"))
			 .WHEN_NOT_MATCHED_THEN(INSERT_INTO(table).VALUES(columnIRI.replace(table, "A"), "75036"));
		System.out.print(MERGE.SQL());
	}

}

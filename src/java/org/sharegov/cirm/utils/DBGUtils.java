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
package org.sharegov.cirm.utils;

import java.sql.SQLException;
import java.text.DecimalFormat;

import oracle.ucp.jdbc.PoolDataSource;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;

public class DBGUtils
{

	public static DecimalFormat decF = new DecimalFormat("#00");
	
	public static void printOntologyFunctional(OWLOntology o) {
		System.out.println("Prefix(xsd:=<http://www.w3.org/2001/XMLSchema#>)");
		System.out.println("Ontology(" + o.getOntologyID());
		for (OWLAxiom ax : o.getAxioms()) {
			System.out.println(ax.toString());
		}
		System.out.println(")");
		System.out.println("Axiom count: " + o.getAxiomCount());
	}
	
	public static void printPoolDataSourceInfo(PoolDataSource pds)
	{
		try
		{
		System.out.println("POOL DATA SOURCE INFO FOR: " + pds);
		System.out.println("ConnectionPoolName : " + pds.getConnectionPoolName());
		System.out.println("DataSourceName : " + pds.getDataSourceName());
		System.out.println("DatabaseName : " + pds.getDatabaseName());
		System.out.println("InitialPoolSize : " + pds.getInitialPoolSize());
		System.out.println("MinPoolSize : " + pds.getMinPoolSize());
		System.out.println("MaxPoolSize : " + pds.getMaxPoolSize());
		System.out.println("ValidateConnectionOnBorrow : " + pds.getValidateConnectionOnBorrow());
		System.out.println("MaxConnectionReuseCount : " + pds.getMaxConnectionReuseCount());
		System.out.println("ConnectionWaitTimeout : " + pds.getConnectionWaitTimeout());
		System.out.println("InactiveConnectionTimeout : " + pds.getInactiveConnectionTimeout());
		//System.out.println("FastConnectionFailoverEnabled : " + pds.getFastConnectionFailoverEnabled());
		System.out.println("MaxConnectionReuseTime : " + pds.getMaxConnectionReuseTime());
		//System.out.println("SQLForValidateConnection : " + pds.getSQLForValidateConnection());
		System.out.println("ConnectionProperties: " + pds.getConnectionProperties().toString());
		System.out.println("AvailableConnectionsCount : " + pds.getAvailableConnectionsCount());
		System.out.println("BorrowedConnectionsCount : " + pds.getBorrowedConnectionsCount());
		} catch (SQLException e)
		{
			e.printStackTrace();
		}
	}
	
}

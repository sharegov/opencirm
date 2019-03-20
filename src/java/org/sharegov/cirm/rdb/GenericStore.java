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
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;

public class GenericStore
{

	private static volatile GenericStore instance = null;
	private static GenericStore electionInstance = null;
	private static GenericStore asdInstance = null;

	private static OWLNamedIndividual electionInfo = null;
	private static String electionDriver = null;
	private static String electionURL = null;
	private static String electionUsername = null;
	private static String electionPassword = null;

	private static OWLNamedIndividual asdInfo = null;
	private static String asdURL = null;
	private static String asdUsername = null;
	private static String asdPassword = null;

	private GenericStore()
	{
	}

	public static GenericStore getInstance()
	{
		if (instance == null)
		{
			instance = new GenericStore();
		}
		return instance;
	}

	public static GenericStore getElectionInstance()
	{
		if (electionInstance == null)
		{			
			electionInfo = Refs.configSet.resolve().get("ElectionsConfig"); // OWL.individual("ElectionsDatabase");
			OWLNamedIndividual hasDatabaseType = OWL.objectProperty(electionInfo, "hasDatabaseType");
			electionDriver = OWL.dataProperty(hasDatabaseType, "hasDriver").getLiteral();
			electionURL = OWL.dataProperty(electionInfo, "hasUrl").getLiteral();
			electionUsername = OWL.dataProperty(electionInfo, "hasUsername").getLiteral();
			electionPassword = OWL.dataProperty(electionInfo, "hasPassword").getLiteral();
			electionInstance = new GenericStore();
		}
		return electionInstance;
	}

	public static GenericStore getASDInstance()
	{
		if (asdInstance == null)
		{
			asdInfo = OWL.individual("ChameleonDatabase");
			asdURL = OWL.dataProperty(asdInfo, "hasUrl").getLiteral();
			asdUsername = OWL.dataProperty(asdInfo, "hasUsername").getLiteral();
			asdPassword = OWL.dataProperty(asdInfo, "hasPassword").getLiteral();
			asdInstance = new GenericStore();
		}
		return asdInstance;
	}

	public Connection getConnection(String driverClassName, String url, String username, String password)
	{
		try
		{
			Class.forName(driverClassName);
			Connection conn = DriverManager.getConnection(url, username, password);
			return conn;
		}
		catch (Throwable e)
		{
			e.getMessage();
			return null;
		}
	}

	public Connection getSQLPooledConnection(String url, String username, String password) throws Exception
	{
		SQLServerDataSource sqlds = new SQLServerDataSource();
		sqlds.setUser(username);
		sqlds.setPassword(password);
		sqlds.setURL(url);
		return sqlds.getConnection();
	}

	public Connection getElectionConnection()
	{
		Connection conn = null;
		try
		{
			return getElectionSQLPooledConnection();
		}
		catch (Exception e)
		{
			System.out.println("Pooled connection exception, using direct access.");
			e.printStackTrace();
		}
		try
		{
			Class.forName(electionDriver);
			conn = DriverManager.getConnection(electionURL, electionUsername, electionPassword);
		}
		catch (Exception ex)
		{
			ex.printStackTrace(System.out);
		}
		return conn;
	}

	public Connection getElectionSQLPooledConnection() throws Exception
	{
		SQLServerDataSource sqlds = new SQLServerDataSource();
		sqlds.setUser(electionUsername);
		sqlds.setPassword(electionPassword);
		sqlds.setURL(electionURL);
		return sqlds.getConnection();
	}

	public Connection getASDSQLPooledConnection() throws Exception
	{
		SQLServerDataSource sqlds = new SQLServerDataSource();
		sqlds.setUser(asdUsername);
		sqlds.setPassword(asdPassword);
		sqlds.setURL(asdURL);
		return sqlds.getConnection();
	}

}

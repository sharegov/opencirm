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
package gov.miamidade.cirm.maintenance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import oracle.jdbc.pool.OracleDataSource;

import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;

/**
 * Class to check providedBy property 
 * for ServiceCases. 
 * 
 * @author Syed
 *
 */
public class ProvidedByChecker
{
	
	private String url;
	private String username;
	private String password;

	private DataSource dataSource;
	/**
	 * Return a map of ServiceCase type who its providedBy
	 * if no providedBy is defined an empty list is returned.
	 * the provided is a map of individual to common properties
	 * such as OWL class, csr group code, iri, label, etc (properties
	 * that may be relevant).
	 * @return
	 */
	public Map<OWLNamedIndividual,Map<OWLNamedIndividual, List<Object>>> getServiceCasesWithProvidedBy(boolean onlyThoseWithAccessPolicy)
	{
		Map<OWLNamedIndividual,Map<OWLNamedIndividual, List<Object>>> result = new HashMap<OWLNamedIndividual,Map<OWLNamedIndividual, List<Object>>>();
		String dlQuery = "legacy:ServiceCase";
		if(onlyThoseWithAccessPolicy)
			dlQuery =  dlQuery + " and legacy:isObjectOf some AccessPolicy";
		Set<OWLNamedIndividual> rs = OWL.queryIndividuals(dlQuery);
		for (final OWLNamedIndividual ind : rs)
		{
			final OWLNamedIndividual providedBy = OWL.objectProperty(ind, "legacy:providedBy");
			Map<OWLNamedIndividual, List<Object>> providedByProps = null;
			if(providedBy != null)
			{
				providedByProps = new HashMap<OWLNamedIndividual, List<Object>>();
				providedByProps.put(providedBy, new ArrayList<Object>(){
					/**
					 * 
					 */
					private static final long serialVersionUID = 1924086007913509018L;
					{
						add(OWL.reasoner().getTypes(providedBy, true).getFlattened());
						add(OWL.dataProperty(providedBy, "Name"));
					} 
				});
			}
			else
			{
				providedByProps = Collections.emptyMap();
			}
			result.put(ind, providedByProps);
		}
		return result;
	}
	
	public Map<String,String> getCSRGroup(OWLEntity serviceCase)
	{
		return getCSRGroup(serviceCase.getIRI().getFragment());
	}
	
	public Map<String,String> getCSRGroup(String serviceCase)
	{
		Map<String,String> csrProperties = new HashMap<String,String>();
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		String query = new StringBuilder ("select def.service_request_type_code, code.description")
					 .append(" ,grouptasks.getjurisdictioncode( code.owner_code ) jurisdiction_code")
					 .append(" ,StandardTasks.GetDescription('GROUP',GroupTasks.GetJurisdictionCode( code.OWNER_CODE )) jurisdiction_desc")
					 .append(" ,code.owner_code as csr_group_code") 
					 .append(" ,StandardTasks.GetDescription('GROUP', code.OWNER_CODE )  AS  CSR_GROUP_DESC") 
					 .append(" from sr_definitions def, st_codes code")
					 .append(" where def.SERVICE_REQUEST_TYPE_CODE=code.CODE_CODE")
					 .append(" and code.TYPE_CODE='SRSRTYPE'")
					 .append(" and GroupTasks.GetJurisdictionCode( code.OWNER_CODE ) in ('MD','COM') and def.service_request_type_code = ?")
					 .append(" order by def.service_request_type_code, code.description").toString();
		try
		{
			conn = getConnection();
			stmt = conn.prepareStatement(query);
			stmt.setString(1, serviceCase);
			rs = stmt.executeQuery();
			ResultSetMetaData meta = rs.getMetaData();			
			if (rs.next())
			{
				for(int i = 1; i <= meta.getColumnCount(); i++)
				{
					csrProperties.put(meta.getColumnName(i), rs.getString(i));
				}
			}						
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			try{rs.close();}catch(Exception e){}
			try{stmt.close();}catch(Exception e){}
			try{conn.close();}catch(Exception e){}
		}
		
		return csrProperties;
		
	}
	
	
	private Connection getConnection() throws SQLException
	{
		if(dataSource == null)
		{
			OWLNamedIndividual info = Refs.configSet.resolve().get("CSRConfig");
			url = OWL.dataProperty(info, "hasUrl").getLiteral();
			username = OWL.dataProperty(info, "hasUsername").getLiteral();
			password = OWL.dataProperty(info, "hasPassword").getLiteral();
			dataSource = createDatasource();
		}
		return dataSource.getConnection();
	}
	
	private OracleDataSource createDatasource() throws SQLException
	{
		OracleDataSource ods = new OracleDataSource();
		ods.setURL(url);
		ods.setUser(username);
		ods.setPassword(password);
		// FOR DEBUGGING DB ods.setLogWriter(new PrintWriter(System.out));
		// hilpold maybe use: ods.setConnectionCachingEnabled(arg0);
		// ods.setExplicitCachingEnabled(arg0);
		// ods.setConnectionCacheProperties(arg0);;
		// ods.setImplicitCachingEnabled(arg0);
		// ods.setConnectionProperties(arg0);
		System.out.println("Oracle Datasource created : ");
		System.out.println("ConnectionCachingEnabled  : "
				+ ods.getConnectionCachingEnabled());
		System.out.println("ConnectionCacheProperties : "
				+ ods.getConnectionCacheProperties());
		System.out.println("ImplicitCachingEnabled    : "
				+ ods.getImplicitCachingEnabled());
		System.out.println("ExplicitCachingEnabled    : "
				+ ods.getExplicitCachingEnabled());
		System.out.println("MaxStatements             : "
				+ ods.getMaxStatements());
		return ods;
	}
	
	public static void main(String[] args)
	{
//		ProvidedByChecker checker = new ProvidedByChecker();
//		Map<OWLNamedIndividual,Map<OWLNamedIndividual, List<Object>>> serviceCases = checker.getServiceCasesWithProvidedBy(true);
//		for(OWLNamedIndividual serviceCase : serviceCases.keySet())
//		{
//			System.out.print(serviceCase.getIRI().getFragment());
//			Map<OWLNamedIndividual, List<Object>> providedByWithProps = serviceCases.get(serviceCase);
//			if(providedByWithProps.equals(Collections.emptyMap()))
//			{
//				System.out.print("\t--No providedBy--");
//			}
//			else
//			{
//				System.out.print("\t" + providedByWithProps.entrySet());
//			}
//			for(Map.Entry<String, String> csrProps : checker.getCSRGroup(serviceCase.getIRI().getFragment()).entrySet())
//			{
//				System.out.print("\t" + csrProps);
//			}
//			System.out.println();
//		}
		
		
			
	}
	
	
	
	
	
	
}

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
import static org.sharegov.cirm.OWL.fullIri;
import static org.sharegov.cirm.OWL.or;
import static org.sharegov.cirm.rest.OperationService.getPersister;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import mjson.Json;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.PrefixManager;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.rdb.DbId;
import org.sharegov.cirm.rdb.RelationalStore;
import org.sharegov.cirm.rdb.RelationalStoreExt;
import org.sharegov.cirm.rdb.RelationalStoreImpl;
import org.sharegov.cirm.rdb.ThreadLocalConnection;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import com.ibm.mq.mqjd.Connection;


public class AddressSave
{
	private static Json directions;
	private static Json cities;
	private static Json suffixes;
	private static Json states;
	
	
	private PrefixManager getPrefixManager()
	{
		String legacy = "http://www.miamidade.gov/cirm/legacy";
		String mdc = "http://www.miamidade.gov/ontology";
		DefaultPrefixManager pm = new DefaultPrefixManager("legacy");
		pm.setPrefix("mdc:", mdc+"#");
		pm.setPrefix("legacy:", legacy+"#");
		return pm;
	}
	
	public long saveAddress(String fullAddress,	
			long streetNumber,
	String streetName ,
	String streetPrefixAlias ,
	String streetSuffixAlias ,
	String unit ,
	String cityAlias, 
	String stateAlias,
	long zip,
	double xcoord,
	double ycoord,
	String location)
	{
		
		RelationalStoreExt  store =	getPersister().getStoreExt();
		OWLDataFactory df = Refs.tempOntoManager.resolve().getOWLDataFactory();
		OWLNamedIndividual address = df.getOWLNamedIndividual(fullIri("Street_Address" + Refs.idFactory.resolve().newId(null)));
		OWLNamedIndividual streetPrefix = getDirection(streetPrefixAlias, df);
		OWLNamedIndividual streetSuffix = getSuffix(streetSuffixAlias, df);
		OWLNamedIndividual city = getCity(cityAlias, df);
		OWLNamedIndividual state = getState(stateAlias, df, getPrefixManager());
		OWLClass streetAddress = OWL.owlClass("http://www.miamidade.gov/ontology#Street_Address");
		Set<OWLEntity> entities = new HashSet<OWLEntity>();
		entities.add(address);
		entities.add(city);
		entities.add(state);
		entities.add(streetAddress);
		Map<OWLEntity,DbId> entitiesAndIds = store.selectInsertIDsAndEntitiesByIRIs(entities, true);
		long id = entitiesAndIds.get(address).getFirst();
		StringBuilder addressSql = new StringBuilder();
		addressSql.append("Insert into CIRM_MDC_ADDRESS (ADDRESS_ID,FULL_ADDRESS,STREET_NUMBER" +
				",STREET_NAME,STREET_NAME_PREFIX,STREET_NAME_SUFFIX," +
				"UNIT,CITY,STATE,ZIP,XCOORDINATE,YCOORDINATE,LOCATION_NAME) values (?,?,?,?,?,?,?,?,?,?,?,?,?)");
		StringBuilder classificationSql = new StringBuilder();
		classificationSql.append("Insert into CIRM_CLASSIFICATION(SUBJECT,OWLCLASS, FROM_DATE)" +
				" values (?,?,SYSTIMESTAMP)");
		ThreadLocalConnection conn = store.getConnection();
		PreparedStatement addressStatement = null;
		PreparedStatement classificationStatement = null;
		try
		{
		addressStatement = conn.prepareStatement(addressSql.toString());
		addressStatement.setLong(1, id);
		addressStatement.setString(2, fullAddress);
		addressStatement.setLong(3, streetNumber);
		addressStatement.setString(4, streetName);
		addressStatement.setString(5, (streetPrefix != null) ?streetPrefix.getIRI().getFragment(): null );
		addressStatement.setString(6, (streetSuffix != null) ?streetSuffix.getIRI().getFragment(): null );
		addressStatement.setString(7, unit);
		addressStatement.setLong(8, entitiesAndIds.get(city).getFirst());
		addressStatement.setLong(9, entitiesAndIds.get(state).getFirst()); 
		addressStatement.setLong(10, zip);
		addressStatement.setDouble(11, xcoord);
		addressStatement.setDouble(12, ycoord);
		addressStatement.setString(13, location);
		addressStatement.executeUpdate();
		classificationStatement = conn.prepareStatement(classificationSql.toString());
		classificationStatement.setLong(1, id);
		classificationStatement.setLong(2, entitiesAndIds.get(streetAddress).getFirst());
		classificationStatement.executeUpdate();
		conn.commit();
		return id;
		} catch(SQLException e)
		{
			throw new RuntimeException(e);
		}
		finally
		{
		
			if (addressStatement != null)
				try { addressStatement.close(); } catch (Throwable t) { } 	
			if (classificationStatement != null)
				try { classificationStatement.close(); } catch (Throwable t) { } 
			if (conn != null)
				try { conn.close(); } catch (Throwable t) { } 
		}
		
		
	}
	
	
	private static OWLNamedIndividual getSuffix(String suffixStr,
			OWLDataFactory factory) {
		OWLNamedIndividual result = null;
		if(suffixes == null)
			cacheSuffixes();
		for(Json suffix: suffixes.asJsonList())
		{
			if(suffix.has("Name") && suffix.at("Name").asString().equals(suffixStr))
			{
				result = factory.getOWLNamedIndividual(IRI.create(suffix.at("iri").asString()));
				return result;
			}
			if(suffix.has("USPS_Suffix")) 
				if(suffix.at("USPS_Suffix").isArray())
				{
					for(Json literal : suffix.at("USPS_Suffix").asJsonList())
					{
						if(literal.asString().equals(suffixStr))
						{
							result = factory.getOWLNamedIndividual(IRI.create(suffix.at("iri").asString()));
							return result;
						}
						
					}
				}else
				{
						if(suffix.at("USPS_Suffix").asString().equals(suffixStr))
						{
							result = factory.getOWLNamedIndividual(IRI.create(suffix.at("iri").asString()));
							return result;
						}
				}
			if(suffix.has("Alias")) 
				if(suffix.at("Alias").isArray())
				{
					for(Json literal : suffix.at("Alias").asJsonList())
					{
						if(literal.asString().equals(suffixStr))
						{
							result = factory.getOWLNamedIndividual(IRI.create(suffix.at("iri").asString()));
							return result;
						}
						
					}
				}else
				{
						if(suffix.at("Alias").asString().equals(suffixStr))
						{
							result = factory.getOWLNamedIndividual(IRI.create(suffix.at("iri").asString()));
							return result;
						}
				}
		}
		
		return result;
	}

	private static OWLNamedIndividual getDirection(String prefix,
			OWLDataFactory factory) {
		OWLNamedIndividual result = null;
		if(directions == null)
			cacheDirections();
		for(Json dir: directions.asJsonList())
		{
			
			if(dir.has("USPS_Abbreviation")) 
				if(dir.at("USPS_Abbreviation").isArray())
				{
					for(Json literal : dir.at("USPS_Abbreviation").asJsonList())
					{
						if(literal.asString().equals(prefix))
						{
							result = factory.getOWLNamedIndividual(IRI.create(dir.at("iri").asString()));
							return result;
						}
						else 
							continue;
					}
				}else
				{
						if(dir.at("USPS_Abbreviation").asString().equals(prefix))
						{
							result = factory.getOWLNamedIndividual(IRI.create(dir.at("iri").asString()));
							return result;
						}
						else
							continue;
				}
		}
		
		return result;
	}
	
	private static OWLNamedIndividual getState(String s, OWLDataFactory factory, PrefixManager pm)
	{
		OWLNamedIndividual state = null;
		if(s != null)
		{
		
			if(s.equals("FL"))
			{
				state = factory.getOWLNamedIndividual("mdc:Florida",pm);
				return state;
			}
			
			if(states == null)
				cacheStates();
			for(Json st: states.asJsonList())
			{
				if(st.has("USPS_Abbreviation"))
					if(st.at("USPS_Abbreviation").isArray())
					{
						for(Json literal : st.at("USPS_Abbreviation").asJsonList())
						{
							if(literal.asString().equals(s))
							{
								state = factory.getOWLNamedIndividual(IRI.create(st.at("iri").asString()));
								return state;
							}
							else 
								continue;
						}
					}else
					{
							if(st.at("USPS_Abbreviation").asString().equals(s))
							{
								state = factory.getOWLNamedIndividual(IRI.create(st.at("iri").asString()));
								return state;
							}
							else
								continue;
					}
			}
		}
		return state;
	}
	
	private static OWLNamedIndividual getCity(String cityStr,
			 OWLDataFactory factory) {
		OWLNamedIndividual result = null;
		if(cities == null)
			cacheCities();
		for(Json city: cities.asJsonList())
		{
			
			if(city.has("Name") && city.at("Name").asString().equals(cityStr))
			{
				result = factory.getOWLNamedIndividual(IRI.create(city.at("iri").asString()));
				return result;
			}
			
			if(city.has("Alias"))
				if( city.at("Alias").isArray())
				{
					for(Json literal : city.at("Alias").asJsonList())
					{
						if(literal.asString().equals(cityStr))
						{
							result = factory.getOWLNamedIndividual(IRI.create(city.at("iri").asString()));
							return result;
						}
						else 
							continue;
					}
				}else
				{
						if(city.at("Alias").asString().equals(cityStr))
						{
							result = factory.getOWLNamedIndividual(IRI.create(city.at("iri").asString()));
							return result;
						}
						else
							continue;
				}
		}
		
		return result;
	}
	
	
	private static String toFullAddress(String streetNumber, String prefix, String streetName, String suffix)
	{
		StringBuffer buffer = new StringBuffer(streetNumber);
		if(prefix != null && prefix.trim().length() > 0)
			buffer.append(" ").append(prefix.trim());
		if(streetName != null && streetName.trim().length() > 0)
			buffer.append(" ").append(streetName.trim());
		if(suffix != null && suffix.trim().length() > 0)
			buffer.append(" ").append(suffix.trim());
		return buffer.toString();
	}
	
	private static void cacheDirections()
	{
		if(directions == null)
		{
			Set<OWLNamedIndividual> set =OWL.reasoner(). 
					getInstances(
							OWL.dataFactory().getOWLClass(fullIri("Direction"))
							, false).getFlattened();
			directions = Json.array();
			for(OWLNamedIndividual direction: set)
			{
				directions.add(OWL.toJSON(direction));
				
			}
		}
	}
	
	private static void cacheStates()
	{
		if(states == null)
		{
			Set<OWLNamedIndividual> set =OWL.reasoner(). 
					getInstances(
							OWL.dataFactory().getOWLClass(fullIri("State__U.S._"))
							, false).getFlattened();
			states = Json.array();
			for(OWLNamedIndividual state: set)
			{
				states.add(OWL.toJSON(state));
				
			}
		}
	}
	
	private static void cacheCities()
	{
		if(cities == null)
		{
			Set<OWLNamedIndividual> set =OWL.reasoner(). 
											getInstances(
													or (OWL.dataFactory().getOWLClass(fullIri("City"))
															,OWL.dataFactory().getOWLClass(fullIri("County"))), false).getFlattened();
			cities = Json.array();
			for(OWLNamedIndividual city: set)
			{
				cities.add(OWL.toJSON(city));
				
			}
		}
	}
	
	private static void cacheSuffixes()
	{
		if(suffixes == null)
		{
			Set<OWLNamedIndividual> set = OWL.reasoner(OWL.ontology(Refs.MDC_PREFIX)). 
			getInstances(OWL.dataFactory().getOWLClass(fullIri("Street_Type")), false).getFlattened();
			suffixes = Json.array();
			for(OWLNamedIndividual suffix: set)
			{
				suffixes.add(OWL.toJSON(suffix));
				
			}
		}
	}
	
	public static void main(String[] args)
	{
		AddressSave save = new AddressSave();
		//System.out.println(save.saveAddress("111 NW 1ST ST", 111l, "1ST", "NW", "", "", "MIAMI", "FL", 33128l, 102.235, 102.254, ""));
		//System.out.println(save.saveAddress("9550 SW 29TH TER", 9550l, "29TH", "SW", "", "", "UNINCORPORATED MIAMI-DADE", "FL", 33165l, 102.235, 102.254, ""));
		System.out.println(save.saveAddress("7937 WEST DR", 7937l, "WEST", "", "DR", "", "NORTH BAY VILLAGE", "FL", 33141l, 102.235, 102.254, ""));
	}

}

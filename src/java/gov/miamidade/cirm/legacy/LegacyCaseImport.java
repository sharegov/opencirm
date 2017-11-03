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
package gov.miamidade.cirm.legacy;

import static org.sharegov.cirm.OWL.fullIri;

import static org.sharegov.cirm.OWL.or;
import static org.sharegov.cirm.OWL.owlClass;

import gov.miamidade.cirm.GisClient;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.sql.DataSource;
import javax.xml.datatype.DatatypeFactory;

import mjson.Json;
import oracle.ucp.UniversalConnectionPoolException;
import oracle.ucp.admin.UniversalConnectionPoolManager;
import oracle.ucp.admin.UniversalConnectionPoolManagerImpl;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.PrefixManager;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.gis.GisDAO;
import org.sharegov.cirm.rdb.RelationalOWLPersister;
import org.sharegov.cirm.rdb.RelationalStoreExt;
import org.sharegov.cirm.rdb.RelationalStoreImpl;
import org.sharegov.cirm.utils.DBGUtils;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;
import gov.miamidade.cirm.maintenance.CirmMonitor;

public class LegacyCaseImport
{
	
	public static boolean DBG = false;
	public static boolean SAVE_RECORDS = true;
	public static boolean WRITE_RECORDS_TO_DISK = false;
	public static boolean SLEEP_DURING_WORK_HOURS= false;
	private static final boolean USE_CONNECTION_POOL = true;
	public static final int POOL_SIZE_INITIAL = 5;
	public static final int POOL_SIZE_MAX = 50; //150 processes limit on server
	public static final int POOL_CONNECTION_REUSE_COUNT_MAX = 1000;
	public static final int POOL_CONNECTION_STATEMENTS_MAX = 40;
	public static final boolean POOL_CONNECTION_VALIDATE_ON_BORROW = true;
	public static final int POOL_CONNECTION_WAIT_TIMEOUT_SECS = 120;
	public static final int POOL_CONNECTION_INACTIVE_TIMEOUT_SECS = 8 * 3600; //before it is removed from pool
	public static final int POOL_CONNECTION_PREFETCH_ROWS = 1500; //single db roundtrip
	public static final int POOL_CONNECTION_BATCH_ROWS = 50; //single db roundtrip
	public static final Map<String, OWLNamedIndividual> EXPIRED_ANSWER_TO_INDIVIDUAL_MAP = new HashMap<String, OWLNamedIndividual>(){
		private static final long serialVersionUID = -2099676196830821256L;
		{
			this.put("$000.00 (First time without Violation-No Fee)", OWL.individual("legacy:COMCESTL_ENTERREG_REGIFEE_1STFREE"));
			this.put("$000.00 (First time-No Fee)", OWL.individual("legacy:COMCESTL_ENTERREG_REGIFEE_1STFREE"));
		}
	};
	
	
	private static Map<String,String> personnelIDs = new HashMap<String,String>();
	private static Map<String,String> csrUsernames = new HashMap<String, String>();
	private static Map<String,String> emails = new HashMap<String, String>();
	private static Map<String,Long> gisDataCache = new HashMap<String, Long>(5000);
	private static Json directions;
	private static Json cities;
	private static Json suffixes;
	private static Json states;
	private static Set<String> notImportedSRs;
	private static DataSource dataSource;
	
	public static void main(String[] args) throws OWLOntologyCreationException
	{
		if(args.length == 1)
		{
			try
			{
				File log = new File(args[0]);
				PrintStream p = new PrintStream(log);
				System.setOut(p);
				System.setErr(p);
			}
			catch (FileNotFoundException e)
			{
				System.out.println("Could not log to file" + args[0]);
			}
		}
		
		Properties props = getDefaultProperties();
		launchMonitor();	
		importServiceRequests(props);
	}
	
	public static Properties getDefaultProperties()
	{
		Properties props = new Properties();
		try
		{
			InputStream is = LegacyCaseImport.class.getClassLoader().
			getResourceAsStream("gov/miamidade/cirm/legacy/import.properties");
			props.load(is);
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
			throw new RuntimeException(ex);
		}
		return props; 
	}

	private static void launchMonitor()
	{
		
		try
		{
			String separator = System.getProperty("file.separator");
			String classpath = System.getProperty("java.class.path");
			String path = System.getProperty("java.home")
		                + separator + "bin" + separator + "javaw";
			ProcessBuilder processBuilder = 
		                new ProcessBuilder(path, "-cp", 
		                classpath, 
		                CirmMonitor.class.getName());
			Process process = processBuilder.start();
		//process.waitFor();
		}catch(Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	private static void loadPersonnelIDsAndUsernames(Connection conn)
	{
		
		Statement stmt = null;
    	ResultSet rs = null;
    	try
    	{
    		String sql = "select a.PERSON_EID || '' as PERSON_EID, a.PERSONNEL_ID, b.E_ADDRESS, a.USERNAME from st_user_information a, ELECTRONIC_ADDRESSES b where a.PERSON_EID = b.OWNER_EID";
            stmt = conn.createStatement();            
            rs = stmt.executeQuery(sql);
            while (rs.next())
            {
            	String personnelID = rs.getString("PERSONNEL_ID");
            	String email =  rs.getString(3);
            	if(personnelID != null)
            		personnelIDs.put(rs.getString(1), personnelID);
            	else if(email != null && !email.equalsIgnoreCase("UNKNOWN"))
            		emails.put(rs.getString(1), email);
            	else
            		csrUsernames.put(rs.getString(1), "USERNAME");
            }   	 		
    	}
    	catch (Exception ex)
    	{
    		throw new RuntimeException(ex);
    	}
    	finally
    	{
        	if (rs != null)
        		try { rs.close(); } catch (Throwable t) { }        	
        	if (stmt != null)
        		try { stmt.close(); } catch (Throwable t) { }
    	}
	}
	
	private static String getUsername(String eid)
	{
		String username = null;
		if (eid.length() > 0)
		{
			// getPersonnelID(eid);
			String personnelID = personnelIDs.get(eid);
			String email = emails.get(eid);
			if (personnelID != null)
			{	
				 if (Character.isDigit(personnelID.charAt(0)))
				 {
					 username = "e" + personnelID;
					 //System.out.println("personnel id that can be assigned: " + username);
					 //set as employee eKey
				 } 
				 else if (personnelID.startsWith("CM"))
				 {
					 if(email != null)
					 {
						 username = email;
					 }
					 else
					 {
						 username = personnelID;
					 }
				 }
				 else
				 {
					 return personnelID;
				 }
			}
			else if (email != null)
			{
				username = email;
			}
			else
			{
				return null;
			}
			
		}
		return username;
	}
	
	public static void importServiceRequests(Properties config)
	{
		
		
		RelationalStoreImpl.TRANSACTION_ISOLATION_LEVEL = Connection.TRANSACTION_READ_COMMITTED;
		GenUtils.dbg(false);
		RelationalStoreImpl.DBGX = false;
		//RelationalOWLPersister persister = RelationalOWLPersister.getInstance(fullIri("GICDWTestDatabase"));
		Connection csrJdbcConn = null;
		Statement stmt = null;
		OWLNamedObject persistentInd = Refs.configSet.resolve().get("OperationsDatabaseConfig");
		final RelationalOWLPersister persister = RelationalOWLPersister.getInstance(persistentInd.getIRI());
		System.out.println("Exporting cases to " + persister.getConnectionInfo() +  ", check ontology for connection details.");
		String serviceRequestNumber = config.getProperty("case-number");
		try
		{
			Class.forName(config.getProperty("db-driver-name"));
			if(dataSource == null)
				dataSource = createPoolDatasource(config.getProperty("db-url"), config.getProperty("db-username"), config.getProperty("db-password"));
            csrJdbcConn = dataSource.getConnection();
			loadPersonnelIDsAndUsernames(csrJdbcConn);
            StringBuffer sql2 = new StringBuffer();
			sql2.append("select a.EID || '' AS EID,a.SERVICE_REQUEST_NUM,a.TYPE_CODE,"); 
			sql2.append("	a.GROUP_CODE,a.GROUP_DESC,a.PRIORITY_CODE,REPLACE(a.STATUS_CODE, 'C_LIEN', 'C-LIEN') as STATUS_CODE,");
			sql2.append("   a.STATUS_DATE,a.ORIG_SERVICE_REQUEST_EID,");
			sql2.append("   a.CREATION_REASON_CODE,a.RELATED_REASON_CODE,");
			sql2.append("	a.METHOD_RECEIVED_CODE,a.GEO_STREET_NAME_EID,");
			sql2.append("	a.VALID_SEGMENT_FLAG,a.LOCATION_RECORD_TYPE_CODE,");
			sql2.append("	cast(a.STREET_NUMBER as INT) STREET_NUMBER,a.STREET_NAME_PREFIX,");
			sql2.append("	a.STREET_NAME,a.STREET_NAME_SUFFIX,a.STREET_SUFFIX_DIRECTION,");
			sql2.append("	a.CITY,a.STATE_CODE,a.COUNTY,a.ZIP_CODE,a.UNIT_NUMBER,");
			sql2.append("	a.FLOOR,a.BUILDING_NAME,a.LOCATION_DETAILS,");
			sql2.append("	a.WDA_1_VALUE,a.WDA_2_VALUE,a.WDA_3_VALUE,");
			sql2.append("	a.X_COORDINATE,a.Y_COORDINATE,a.DETAILS,");
			sql2.append("	a.BEGIN_RESOLUTION_DATE,a.CREATED_DATE,");
			sql2.append("	a.CREATED_BY_EID,a.UPDATED_DATE,a.UPDATED_BY_EID,");
			sql2.append("	a.GEO_AREA_CODE,a.GEO_AREA_VALUE, b.SERVICE_REQUEST_NUM AS PARENT_SERVICE_REQUEST_NUM");
			sql2.append(" 	from CSR.SERVICE_REQUESTS a left outer join CSR.SERVICE_REQUESTS b on a.ORIG_SERVICE_REQUEST_EID = b.EID");
			StringBuffer countSql = new StringBuffer();
			countSql.append("select count(*)");
			countSql.append(" 	from CSR.SERVICE_REQUESTS a left outer join CSR.SERVICE_REQUESTS b on a.ORIG_SERVICE_REQUEST_EID = b.EID");
			StringBuffer whereClause;
			if(serviceRequestNumber != null)
			{
				whereClause = new StringBuffer(" WHERE a.SERVICE_REQUEST_NUM "+ serviceRequestNumber +"");
				sql2.append(whereClause);
				countSql.append(whereClause);
			}
			else
			{
				
				whereClause = new StringBuffer();
				String statusExpression = config.getProperty("status-expression", "='C-CLOSED'");
				whereClause.append(" where (a.STATUS_CODE " + statusExpression + ") ");
				String types = config.getProperty("types","");
				if(!"".equals(types))
				{
					whereClause.append(" and a.TYPE_CODE in (" +types +")");
				}
				String dateRangeExpression =config.getProperty("date-range-expression"); 
				whereClause.append(" and a.CREATED_DATE " + dateRangeExpression);
				if(config.getProperty("restart", "false").trim().equals("true"))
				{
					whereClause.append(" and a.SERVICE_REQUEST_NUM >  '" + getLastImported(persister.getStoreExt()) + "'");
				}
				whereClause.append(" order by a.SERVICE_REQUEST_NUM, a.CREATED_DATE");
				sql2.append(whereClause);
			    countSql.append(whereClause);
				
				//last two years open or closed lien status 
				//sql2.append(" WHERE (a.STATUS_CODE like 'O%' or a.STATUS_CODE like 'C-LIEN') and a.CREATED_DATE > SYSDATE - 365 * 2  AND a.EID  > 1302768576 order by a.EID");
				//clause 
				//sql2.append(" 	WHERE (a.STATUS_CODE like 'O%' or a.STATUS_CODE like 'C-LIEN') ");
				//sql2.append(" WHERE a.CREATED_DATE BETWEEN '01-Nov-2012' and '01-Dec-2012' order by a.EID");
				//sql2.append(" WHERE a.SERVICE_REQUEST_NUM IN ('12-00346400','12-00346403','12-00346504','12-00346638','12-00348290','12-00348292','12-00348387','12-00348764','12-00349774','12-00349795','12-00349799','12-00349808','12-00350045','12-00350105','12-00350410','12-00350523','12-00350940')");
				
				//animalservices types
				//PROD //sql2.append(" WHERE a.TYPE_CODE in ( select def.SERVICE_REQUEST_TYPE_CODE from sr_definitions def, st_codes code where def.SERVICE_REQUEST_TYPE_CODE=code.CODE_CODE and code.OWNER_CODE = 'MDMPDAS') and a.EID >= (select eid from SERVICE_REQUESTS where SERVICE_REQUEST_NUM = '13-00056699') order by a.EID");
				//sql2.append(" WHERE a.TYPE_CODE in ( select def.SERVICE_REQUEST_TYPE_CODE from sr_definitions def, st_codes code where def.SERVICE_REQUEST_TYPE_CODE=code.CODE_CODE and code.OWNER_CODE = 'MDMPDAS') and a.EID >= (select eid from SERVICE_REQUESTS where SERVICE_REQUEST_NUM = '11-00304197') order by a.EID");
				//sql2.append(" WHERE a.TYPE_CODE in ( select def.SERVICE_REQUEST_TYPE_CODE from sr_definitions def, st_codes code where def.SERVICE_REQUEST_TYPE_CODE=code.CODE_CODE and code.OWNER_CODE = 'MDMPDAS') and a.CREATED_DATE BETWEEN '01-JAN-2009' AND '01-OCT-2012' order by a.SERVICE_REQUEST_NUM, a.CREATED_DATE");
				//~end animal services
				
				//RER(a.k.a CMS) types
				//sql2.append(" WHERE a.SERVICE_REQUEST_NUM IN ('12-00042798','13-00061431','13-00065686','13-00091545','13-00091553','13-00096087','13-00105533','13-00033960','13-00043001','13-00048130','13-00049791')");

				//sql2.append(" WHERE a.SERVICE_REQUEST_NUM IN ('10-00133369', '10-00133368' ,'09-00297051','10-00076009',	'10-00133372','10-00076020',	'09-00387055','10-00133365','10-00133367','10-00133315',	'10-00021620')");
				//sql2.append(" WHERE a.TYPE_CODE in ( select def.SERVICE_REQUEST_TYPE_CODE from sr_definitions def, st_codes code where def.SERVICE_REQUEST_TYPE_CODE=code.CODE_CODE and def.ALLOWABLE_CREATE_MODULE_CODES LIKE '%MD-CMS%' and code.STOP_DATE is null) and a.CREATED_DATE > SYSDATE - 3 and rownum < 10 order by a.SERVICE_REQUEST_NUM, a.CREATED_DATE");
				//by legacy code
				//sql2.append(" WHERE a.TYPE_CODE in ( select def.SERVICE_REQUEST_TYPE_CODE from sr_definitions def, st_codes code where def.SERVICE_REQUEST_TYPE_CODE=code.CODE_CODE and def.ALLOWABLE_CREATE_MODULE_CODES LIKE '%MD-CMS%' and code.STOP_DATE is null) and a.CREATED_DATE BETWEEN '01-JAN-2009' AND '01-JAN-2014' order by a.SERVICE_REQUEST_NUM, a.CREATED_DATE");
				// by spreadsheet sr types
				//sql2.append(" WHERE a.STATUS_CODE = 'C-CLOSED' and a.TYPE_CODE in (" +getRERSRTypes()+") and a.CREATED_DATE BETWEEN '01-JAN-2009' AND '01-JAN-2010' order by a.SERVICE_REQUEST_NUM, a.CREATED_DATE");
			}
			
			stmt = csrJdbcConn.createStatement();
			if(DBG)
			{
				System.out.println("Using SQL: " + sql2.toString());
				System.out.println("Count SQL: " + countSql.toString());
			}
			ResultSet rs = stmt.executeQuery(sql2.toString());	
			String legacy = "http://www.miamidade.gov/cirm/legacy";
			String mdc = "http://www.miamidade.gov/ontology";
			final DefaultPrefixManager pm = new DefaultPrefixManager("legacy");
			pm.setPrefix("mdc:", mdc+"#");
			pm.setPrefix("legacy:", legacy+"#");
			int srToImport = getCountToImport(countSql.toString(), csrJdbcConn);
			int srImportedCount = 0;
			int srNotImported = 0;
		
			while (rs.next())
			{
				
				//slow down the import during normal business hours.
				if(SLEEP_DURING_WORK_HOURS && isWithinNormalBusinessHours())
					Thread.sleep(1000 * 3 * 1);
				
				
				final OWLClass srType = owlClass(IRI.create(pm.getPrefix("legacy:") + rs.getString("TYPE_CODE")));
				if(!OWL.ontology().containsIndividualInSignature(srType.getIRI(), true))
              	{
              		System.err.println("SR Type" + srType.getIRI() + " not defined in meta. Cannot import");
              		srNotImported++;
              		if(notImportedSRs == null)
              			notImportedSRs = new HashSet<String>();
              		notImportedSRs.add(serviceRequestNumber);
              		continue;
              	}
				final BigDecimal streetNumber = rs.getBigDecimal("STREET_NUMBER"); 
//				if(rs.wasNull())
//				{
//					//System.out.println("Sr:" + rs.getString("SERVICE_REQUEST_NUM") + " cannot be imported with an empty address.");
//					//continue;
//				}
				
				ThreadLocalStopwatch.getWatch().time("importServiceRequests()"+ rs.getString("SERVICE_REQUEST_NUM") +" case started.");
				final Connection csrJdbcConnFinal = csrJdbcConn;
				final ResultSet rsFinal = rs;
				final String srNumber = serviceRequestNumber;
				persister.getStore().txn( new CirmTransaction<Object>()
				{
					@Override
					public Object call() throws Exception
					{
						saveRecordToCIRMDB(persister, csrJdbcConnFinal,  rsFinal,  pm,  srType,  streetNumber, srNumber );
						return null;
					}
				});
				
				srImportedCount++;
				ThreadLocalStopwatch.getWatch().time( srImportedCount + " ServiceCase(s) imported in this run thus far of " + srToImport + " total to import. Have ignored " +srNotImported + " ServiceCase(s) due to not being found in meta.");
				ThreadLocalStopwatch.getWatch().time("importServiceRequests()"+ rs.getString("SERVICE_REQUEST_NUM") +" case finished.");
				ThreadLocalStopwatch.startTop( ((((srImportedCount + srNotImported)*100 )/srToImport)) + " % complete");
			}
			System.out.println("Import complete. Not imported:");
			if(notImportedSRs != null)
			{
				for(String s : notImportedSRs)
				{
					System.out.println(s);
				}
			}
			rs.close(); 
			rs = null;
			stmt.close(); 
			stmt = null;
			System.exit(0);
		}
		catch (RuntimeException ex)
		{
			ex.printStackTrace();
			throw ex;
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
			//throw new RuntimeException(ex);
		}
		finally
		{
			if (stmt != null) try { stmt.close(); } catch (Throwable t) {}
			if (csrJdbcConn != null) try { csrJdbcConn.close(); } catch (Throwable t) {}
			RelationalStoreImpl.TRANSACTION_ISOLATION_LEVEL = Connection.TRANSACTION_SERIALIZABLE;
		}
	}

	private static void saveRecordToCIRMDB(RelationalOWLPersister persister, Connection csrJdbcConn, ResultSet rs, DefaultPrefixManager pm, OWLClass srType, BigDecimal streetNumber, String srNumber ) throws SQLException, OWLOntologyCreationException
	{
		
		boolean alreadyImported = isAlreadyImported(rs.getString("SERVICE_REQUEST_NUM"), persister.getStoreExt());
		if(alreadyImported && !WRITE_RECORDS_TO_DISK)
		{
			String msg = "Sr:" + rs.getString("SERVICE_REQUEST_NUM") + " has already been imported";
			ThreadLocalStopwatch.getWatch().startTop(msg);
			if(srNumber != null)
			{	
				//throw new RuntimeException(msg);
			}
			else
				System.out.println(msg);
			return;
		}
			
		BOntology o = BOntology.makeNewBusinessObject(srType);
		OWLOntologyManager m = o.getOntology().getOWLOntologyManager();
		OWLDataFactory factory = m.getOWLDataFactory();
		OWLDataFactory metaFactory = OWL.dataFactory();
		System.out.println(o.getBusinessObject().getIRI());
		OWLClass addressType = factory.getOWLClass("mdc:Street_Address", pm);
		String eid = rs.getString("EID");
		Json oldData = Json.object();
		getServiceAnswers(csrJdbcConn, o, eid , pm, oldData);
		getServiceActors(csrJdbcConn, o, eid, pm, metaFactory);
		getActivities(csrJdbcConn, o, eid, pm, oldData);
		OWLOntology ont = o.getOntology();
		if(oldData.has("hasServiceAnswer") || oldData.has("hasServiceActivity"))
		{
			
			m.addAxiom(ont,
					factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty("legacy:hasOldData", pm)
								 ,o.getBusinessObject(), 
								 oldData.toString()
					));
			
//			o.addDataProperty(o.getBusinessObject(),
//								metaFactory.getOWLDataProperty("legacy:hasOldData", pm), 
//								Json.object()
//									.set("type", "http://www.w3.org/2001/XMLSchema#string")
//									.set("literal", oldData.toString()));
		}
		
		//m.addAxiom(ont, factory.getOWLDeclarationAxiom(addressType));
		//OWLImportsDeclaration imp = factory.getOWLImportsDeclaration(IRI.create(legacy));
		//ont.getI
		//m.addAxiom(ont, );
		if(DBG)
			ThreadLocalStopwatch.getWatch().time("sr gis data started.");
		int gisDataId = (int) getGisData(csrJdbcConn, o, eid , pm);
		m.addAxiom(ont, 
						factory.getOWLDataPropertyAssertionAxiom(
								factory.getOWLDataProperty("legacy:hasGisDataId", pm)
								,o.getBusinessObject()
								,gisDataId)
					);
		if(DBG)
			ThreadLocalStopwatch.getWatch().time("sr gis data finished.");
		m.addAxiom(ont,
				factory.getOWLObjectPropertyAssertionAxiom(
					factory.getOWLObjectProperty("legacy:hasStatus", pm),o.getBusinessObject(), 
					factory.getOWLNamedIndividual("legacy:" + rs.getString("STATUS_CODE"), pm)
				));
		m.addAxiom(ont,
				factory.getOWLObjectPropertyAssertionAxiom(
					factory.getOWLObjectProperty("legacy:hasIntakeMethod", pm),o.getBusinessObject(), 
					factory.getOWLNamedIndividual("legacy:" + rs.getString("METHOD_RECEIVED_CODE"), pm)
				));
		m.addAxiom(ont,
				factory.getOWLObjectPropertyAssertionAxiom(
					factory.getOWLObjectProperty("legacy:hasPriority", pm),o.getBusinessObject(), 
					factory.getOWLNamedIndividual("legacy:" + rs.getString("PRIORITY_CODE"), pm)
				));
		if( streetNumber != null)
		{
		setCaseAddress(rs, pm, streetNumber, o, m, factory, metaFactory,
				addressType, ont);
		}
		if(rs.getBigDecimal("X_COORDINATE") != null)
		m.addAxiom(ont,
				factory.getOWLDataPropertyAssertionAxiom(
					factory.getOWLDataProperty("mdc:hasXCoordinate", pm),o.getBusinessObject(), 
					factory.getOWLLiteral(rs.getBigDecimal("X_COORDINATE").toPlainString(),OWL2Datatype.XSD_DOUBLE)
				));
		if(rs.getBigDecimal("Y_COORDINATE") != null)
		m.addAxiom(ont,
				factory.getOWLDataPropertyAssertionAxiom(
					factory.getOWLDataProperty("mdc:hasYCoordinate", pm),o.getBusinessObject(), 
					factory.getOWLLiteral(rs.getBigDecimal("Y_COORDINATE").toPlainString(),OWL2Datatype.XSD_DOUBLE)
				));
		m.addAxiom(ont,
				factory.getOWLDataPropertyAssertionAxiom(
					factory.getOWLDataProperty("legacy:hasCaseNumber", pm),o.getBusinessObject(), 
					factory.getOWLLiteral(rs.getString("SERVICE_REQUEST_NUM"))
				));
		
		String details = rs.getString("DETAILS");
		if(details != null)
		{
			m.addAxiom(ont,
					factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty("legacy:hasDetails", pm),o.getBusinessObject(), 
						factory.getOWLLiteral(details)
					));
			
		}
		String locationDetails = rs.getString("LOCATION_DETAILS");
		if(locationDetails != null)
		{
			m.addAxiom(ont,
					factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty("legacy:hasLocationDetails", pm),o.getBusinessObject(), 
						factory.getOWLLiteral(locationDetails)
					));
			
		}
		String parentId = rs.getString("PARENT_SERVICE_REQUEST_NUM");
		if(parentId != null)
		{
		
			m.addAxiom(ont,
					factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty("legacy:hasParentCaseNumber", pm),o.getBusinessObject(), 
						factory.getOWLLiteral(parentId)
					));
		}
		Date createdDate = rs.getTimestamp("CREATED_DATE");
		if(createdDate != null)
		{
			try
			{
			Calendar c = Calendar.getInstance();
			c.setTime(createdDate);
			OWLLiteral xmlDate = factory.getOWLLiteral(DatatypeFactory.newInstance().newXMLGregorianCalendar((GregorianCalendar)c).toXMLFormat(), OWL2Datatype.XSD_DATE_TIME_STAMP);
			m.addAxiom(ont,
					factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty("mdc:hasDateCreated", pm),o.getBusinessObject(), 
						xmlDate
					));
			}catch (Exception e) {
				System.out.println("Error parsing date for " + createdDate.toString());
				
			}
		}
		
		String createdBy = getUsername(rs.getString("CREATED_BY_EID"));
		if(createdBy != null)
		{
			m.addAxiom(ont,
					factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty("mdc:isCreatedBy", pm),o.getBusinessObject(), 
						factory.getOWLLiteral(createdBy)
					));
		}
		
		Date updatedDate = rs.getTimestamp("UPDATED_DATE");
		if(updatedDate != null)
		{
			try
			{
			Calendar c = Calendar.getInstance();
			c.setTime(updatedDate);
			OWLLiteral xmlDate = factory.getOWLLiteral(DatatypeFactory.newInstance().newXMLGregorianCalendar((GregorianCalendar)c).toXMLFormat(), OWL2Datatype.XSD_DATE_TIME_STAMP);
			m.addAxiom(ont,
					factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty("mdc:hasDateLastModified", pm),o.getBusinessObject(), 
						xmlDate
					));
			}catch (Exception e) {
				System.out.println("Error parsing date for " + updatedDate.toString());
				
			}
		}
		
		String modifiedBy = getUsername(rs.getString("UPDATED_BY_EID"));
		if(modifiedBy != null)
		{
			m.addAxiom(ont,
					factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty("mdc:isModifiedBy", pm),o.getBusinessObject(), 
						factory.getOWLLiteral(modifiedBy)
					));
		}
		//System.out.println(o.toJSON());
		
		
		if(DBG)
		{
			ThreadLocalStopwatch.getWatch().time("SR axioms created");
			DBGUtils.printOntologyFunctional(o.getOntology());
			System.out.println(o.toJSON());
		}
		if(!alreadyImported && SAVE_RECORDS)
			persister.saveBusinessObjectOntology(ont);
		if(WRITE_RECORDS_TO_DISK)
		{
			try
			{
				FileOutputStream fileOut = new FileOutputStream("C:/Work/cirm_case_import/" + (alreadyImported?"UPDATE":"INSERT") +"-"+ rs.getString("SERVICE_REQUEST_NUM") + ".json");
				fileOut.write(o.toJSON().toString().getBytes());
				fileOut.close();
				
			}catch(Exception e)
			{
				
			}
			
			
		}
		if(DBG)
			System.out.println("Managed ontologies:" + m.getOntologies().size());
		m.removeOntology(ont);
		
	}

	private static void setCaseAddress(ResultSet rs, DefaultPrefixManager pm,
			BigDecimal streetNumber, BOntology o, OWLOntologyManager m,
			OWLDataFactory factory, OWLDataFactory metaFactory,
			OWLClass addressType, OWLOntology ont) throws SQLException
	{
		if( streetNumber != null)
		{
			OWLNamedIndividual address = factory.getOWLNamedIndividual(fullIri(addressType.getIRI().getFragment() + Refs.idFactory.resolve().newId(null)));
			m.addAxiom(ont, 
						factory.getOWLDataPropertyAssertionAxiom(
								factory.getOWLDataProperty("legacy:hasLegacyCode", pm)
								, o.getBusinessObject(), rs.getString("TYPE_CODE"))
					);
			
			m.addAxiom(ont, factory.getOWLClassAssertionAxiom(addressType, address));
			m.addAxiom(ont,
					factory.getOWLObjectPropertyAssertionAxiom(
						factory.getOWLObjectProperty("mdc:atAddress", pm),o.getBusinessObject(), 
						address
					));
			m.addAxiom(ont, factory.getOWLDataPropertyAssertionAxiom(
					factory.getOWLDataProperty("mdc:Street_Number", pm), 
					address, 
					streetNumber.toPlainString()));
			
			String streetName = rs.getString("STREET_NAME");
			if( streetName != null)
			m.addAxiom(ont, factory.getOWLDataPropertyAssertionAxiom(
					factory.getOWLDataProperty("mdc:Street_Name", pm), 
					address, 
					rs.getString("STREET_NAME")));
			String unitNumber = rs.getString("UNIT_NUMBER");
			if(unitNumber != null)
				m.addAxiom(ont, factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty("mdc:Street_Unit_Number", pm), 
						address, 
						unitNumber));
			if(rs.getString("ZIP_CODE") != null)
			{
				String zip = (rs.getString("ZIP_CODE").length() > 5)?rs.getString("ZIP_CODE").substring(0,5):rs.getString("ZIP_CODE");
				try
				{
				m.addAxiom(ont, factory.getOWLDataPropertyAssertionAxiom(
					factory.getOWLDataProperty("mdc:Zip_Code", pm), 
					address, 
					factory.getOWLLiteral(Integer.parseInt(zip))));
				}catch(NumberFormatException e)
				{
					System.out.println("Cannot parse " + zip + " to an integer");
				}
			}
			String prefix = rs.getString("STREET_NAME_PREFIX");
			if(prefix != null)
			{
				OWLNamedIndividual direction = getDirection(prefix, factory);
				if(direction != null)
					m.addAxiom(ont, factory.getOWLObjectPropertyAssertionAxiom(
							factory.getOWLObjectProperty("mdc:Street_Direction", pm), 
							address, 
							direction));
			}
			String cityStr = rs.getString("CITY");
			if(cityStr != null)
			{
			
				OWLNamedIndividual city = getCity(cityStr, factory);
				if(city != null)
					m.addAxiom(ont, factory.getOWLObjectPropertyAssertionAxiom(
							factory.getOWLObjectProperty("mdc:Street_Address_City", pm), 
							address, 
							city));
				else
				{
					System.out.println("No city for: " + o.getBusinessObject().getIRI()+ ", city alias '" + cityStr + "' not recognized");
					System.out.println("Warning: No city individual with Name or Alias '" + cityStr+ "' found in ontology.");
				}
			}
			String suffix = rs.getString("STREET_NAME_SUFFIX");
			if(suffix != null)
			{
			
				OWLNamedIndividual suf = getSuffix(suffix, factory);
				if(suf != null)
					m.addAxiom(ont, factory.getOWLObjectPropertyAssertionAxiom(
							factory.getOWLObjectProperty("mdc:hasStreetType", pm), 
							address, 
							suf));
				else
				{
					System.out.println("No suffix for: " + o.getBusinessObject().getIRI()+ ", suffix '" + suffix + "' not recognized");
					System.out.println("Warning: No suffix individual with Name or Alias '" + suffix+ "' found in ontology.");
				}
				
				
			}
			String stateStr = rs.getString("STATE_CODE");
			if(stateStr != null)
			{
			
					OWLNamedIndividual state = getState(stateStr, metaFactory, pm);
					if(state != null)
						m.addAxiom(ont, factory.getOWLObjectPropertyAssertionAxiom(
								factory.getOWLObjectProperty("mdc:Street_Address_State", pm), 
								address, 
								state));
					else
					{
						System.out.println("No state for: " + o.getBusinessObject().getIRI().toString()  +  ", state alias '" + stateStr + "' not recognized");
						System.out.println("Warning: No state individual with Name or Alias '" + stateStr+ "' found in ontology.");
					}
			}
			
			m.addAxiom(ont, factory.getOWLDataPropertyAssertionAxiom(
					factory.getOWLDataProperty("mdc:fullAddress", pm), 
					address, 
					toFullAddress(streetNumber.toPlainString(), prefix, streetName, suffix)));
			
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
			Set<OWLNamedIndividual> set = OWL.reasoner(Refs.topOntology.resolve()). 
			getInstances(OWL.dataFactory().getOWLClass(fullIri("Street_Type")), false).getFlattened();
			suffixes = Json.array();
			for(OWLNamedIndividual suffix: set)
			{
				suffixes.add(OWL.toJSON(suffix));
				
			}
		}
	}

	private static long getGisData(Connection conn, BOntology bo, String eid, DefaultPrefixManager pm)
	{
		
		if(DBG)
			ThreadLocalStopwatch.getWatch().reset("gis data start");
		Statement stmt = null;
    	ResultSet rs = null;
    	try
        {
            StringBuffer sql = new StringBuffer();
            sql.append("select GEO_AREA_CODE, ");
            sql.append("        case WHEN GEO_AREA_CODE = 'BULKSERV' THEN '999' " +
            		"			WHEN (GEO_AREA_CODE = 'MIATDAY' OR GEO_AREA_CODE ='MIARECY')  AND LENGTH(GEO_AREA_VALUE) > 1 THEN (case WHEN  GEO_AREA_VALUE = 'MONDAY' THEN 'M' WHEN  GEO_AREA_VALUE = 'TUESDAY' THEN 'T' WHEN  GEO_AREA_VALUE = 'WEDNESDAY' THEN 'W' WHEN  GEO_AREA_VALUE = 'THURSDAY' THEN 'R' WHEN  GEO_AREA_VALUE = 'FRIDAY' THEN 'F'  ELSE GEO_AREA_VALUE end )" +
            		" ELSE GEO_AREA_VALUE end as GEO_AREA_VALUE  ");
            sql.append("  FROM GEO_AREA_INFORMATION ");
            sql.append(" WHERE OWNER_EID = " + eid + " ");
            sql.append("ORDER BY GEO_AREA_CODE ");
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql.toString());
            Json gisData = Json.object();
            while (rs.next())
            {
            	gisData.set(rs.getString("GEO_AREA_CODE"), rs.getString("GEO_AREA_VALUE"));
            }
            String hash = OWL.hash(GenUtils.normalizeAsString(gisData));
            Long id = gisDataCache.get(hash);
            if(id == null)
            {
            	id = GisDAO.getGisDBId(gisData, true);
            	gisDataCache.put(hash, id);
            }
            if(gisDataCache.size() > 5000)
            	gisDataCache.clear();
            System.out.println("getGisData id = " + id);
            return id;
        }
        catch(Exception e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
        	if (rs != null)
        		try { rs.close(); } catch (Throwable t) { }        	
        	if (stmt != null)
        		try { stmt.close(); } catch (Throwable t) { }
        	if(DBG)
        		ThreadLocalStopwatch.getWatch().time("gis data end");
        }
    	
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

	private static boolean isAlreadyImported(String legacyId, RelationalStoreExt store) throws SQLException
	{
		if(DBG)
			ThreadLocalStopwatch.getWatch().time("isAlreadyImported started.");
		if(legacyId == null)
			throw new IllegalArgumentException("LegacyId cannot be null");
		Connection conn = store.getConnection();
		ResultSet rs = null;
		Statement stmt = null;
		try
		{
			stmt = conn.createStatement();
			rs = stmt.executeQuery("SELECT SR_REQUEST_ID FROM CIRM_SR_REQUESTS WHERE UPPER(CASE_NUMBER) = '" +  legacyId +"'");
			return rs.next();
			
		}
		catch(SQLException e)
		{
			System.err.println("An error occurred while trying to check if sr with legacyid " + legacyId + " has already been imported.");
			throw e;
		}finally
		{
			if(DBG)
				ThreadLocalStopwatch.getWatch().time("isAlreadyImported ended.");
			conn.commit();
			if(rs != null)
				rs.close();
			if(stmt != null)
				stmt.close();
			if(conn != null)
				conn.close();
			
		}
	}
	
	
	private static String getLastImported(final RelationalStoreExt store) throws Exception
	{
		return store.txn( new CirmTransaction<String>()
				{
					@Override
					public String call() throws Exception
					{
						Connection conn = store.getConnection();
						ResultSet rs = null;
						Statement stmt = null;
						try
						{
							stmt = conn.createStatement();
							rs = stmt.executeQuery("select CASE_NUMBER from CIRM_SR_REQUESTS where SR_REQUEST_ID = (select MAX(SR_REQUEST_ID) from CIRM_SR_REQUESTS where CASE_NUMBER LIKE '%-0%')");
							if(rs.next())
							{
								return rs.getString(1);
							}else
							{
								throw new RuntimeException("Could not get last import case id");
							}
							
						}
						catch(Exception e)
						{
							System.err.println("An error occurred while trying to retrieve last imported legacy case.");
							throw e;
						}finally
						{
							conn.commit();
							if(rs != null)
								rs.close();
							if(stmt != null)
								stmt.close();
							if(conn != null)
								conn.close();
							
						}
					}
				});
	}
	
	private static int getCountToImport(String sql, Connection conn) throws Exception
	{
		
		ResultSet rs = null;
		Statement stmt = null;
		try
		{
			stmt = conn.createStatement();
			rs = stmt.executeQuery(sql);
			if(rs.next())
			{
				return rs.getInt(1);
			}else
			{
				throw new RuntimeException("Could not get last import case id");
			}
			
		}
		catch(Exception e)
		{
			System.err.println("An error occurred while trying to retrieve last imported legacy case.");
			throw e;
		}finally
		{
			if (rs != null)
        		try { rs.close(); } catch (Throwable t) { }        	
        	if (stmt != null)
        		try { stmt.close(); } catch (Throwable t) { }
		}
	}


	public static void getServiceAnswers(Connection conn, BOntology bo, String eid, DefaultPrefixManager pm, Json oldData)
	{
		if(DBG)
			ThreadLocalStopwatch.getWatch().time("sr answers started.");
		Statement stmt = null;
    	ResultSet rs = null;
    	try
        {
            StringBuffer sql = new StringBuffer();
            sql.append("select a.TYPE_CODE, ");
            sql.append("       a.ATTRIBUTE_LABEL_CODE, ");
            sql.append("       a.ATTRIBUTE_VALUE, ");
            sql.append("       a.ATTRIBUTE_NUMBER_VALUE, ");
            sql.append("       a.DATA_TYPE_CODE, ");
            sql.append("       a.CREATED_DATE, a.UPDATED_DATE, b.ATTRIBUTE_LABEL_DESCRIPTION ");
            sql.append("  FROM CUSTOM_ATTRIBUTES a, CUSTOM_ATTRIBUTES_TEMPLATES b ");
            sql.append(" WHERE a.TYPE_CODE = b.TYPE_CODE and a.ATTRIBUTE_LABEL_CODE = b.ATTRIBUTE_LABEL_CODE and b.STOP_DATE is null and a.OWNER_EID = " + eid + " ");
            sql.append("ORDER BY a.ORDER_BY ");
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql.toString()); 
            OWLOntology o = bo.getOntology();
            OWLOntologyManager m = bo.getOntology().getOWLOntologyManager();
            OWLDataFactory factory = m.getOWLDataFactory();
            OWLClass serviceAnswerType = factory.getOWLClass("legacy:ServiceAnswer", pm);
            int tempIndividualCounter = 0;
            while (rs.next())
            {
            	if(rs.getString("ATTRIBUTE_VALUE") ==  null)
            	{
            		//no axioms asserted for blank answers
            		continue;
            	}
            	String serviceCode = rs.getString("TYPE_CODE");
            	String fieldCode =  rs.getString("ATTRIBUTE_LABEL_CODE");
            	OWLNamedIndividual serviceField = factory.getOWLNamedIndividual(IRI.create(pm.getPrefix("legacy:") + URLEncoder.encode(serviceCode + "_" + fieldCode,"UTF-8")));
            	
            	String value = rs.getString("ATTRIBUTE_VALUE");
            	if(value == null || "NUMBER".equals(rs.getString("data_type_code")))
            		value = rs.getBigDecimal("ATTRIBUTE_NUMBER_VALUE")==null?"":rs.getBigDecimal("ATTRIBUTE_NUMBER_VALUE").toString();
            	String dataTypeCode = rs.getString("DATA_TYPE_CODE");
            	if(!OWL.ontology().containsIndividualInSignature(serviceField.getIRI(), true))
              	{
            		System.out.println("SR EID with old answers:" + eid);
            		Json serviceAnswers = oldData.at("hasServiceAnswer", Json.array());
              		Json serviceAnswer = Json.object();
              		serviceAnswer.set("type", "legacy:ServiceAnswer").set("iri", factory.getOWLNamedIndividual(fullIri("ServiceAnswerTemp" +tempIndividualCounter++)).getIRI().toString());
              		serviceAnswer.set("hasServiceField", 
								Json.object().set("label", rs.getString("ATTRIBUTE_LABEL_DESCRIPTION"))
											 .set("iri", serviceField.getIRI().toString())
											 .set("hasDataType", Json.object().set("type", "http://www.w3.org/2001/XMLSchema#string").set("literal", rs.getString("data_type_code"))));
              		serviceAnswer.set("hasAnswerValue", Json.object().set("type", "http://www.w3.org/2001/XMLSchema#string").set("literal",value));
              		serviceAnswers.asJsonList().add(serviceAnswer);
              		continue;
              	}
        		if(value != null && !value.isEmpty())
        		{
        			OWLNamedIndividual answer = null;
        			if((dataTypeCode.equals("CHARLIST") || dataTypeCode.equals("CHARMULT") || dataTypeCode.equals("CHAROPT")))
	        		{
	        			String[] values = null;
	        			if(dataTypeCode.equalsIgnoreCase("CHARMULT"))
	                	{
	                		if(value != null)
	                		{
	                			values =  value.split(",");
	                			
	                		}
	                	}
	        			else 
	        			{
	        				values = new String[]{value};
	        			}
	        			Json jsonAnswerObject = null;
	        			Json jsonAnswerValue = null;
	        			for(String v : values)
	        			{
			    			if(v != null)
			    			{
			    				OWLNamedIndividual answerObject = getAnswerObjectIndividual(serviceField, v);
			    				if(answerObject != null)
				    			{
			    					if(!OWL.ontology().containsIndividualInSignature(answerObject.getIRI(), true))
			    					{
			    						if(dataTypeCode.equals("CHAROPT"))
			    	              		{
			    	              			setAnswerValue(bo, pm, o, m, factory,
													serviceAnswerType,
													serviceField, v,
													dataTypeCode, answer);
			    	              			continue;
			    	              		}
			    	              		else
			    	              		{
			    	              			if(jsonAnswerObject == null)
			    	              			{
			    	              				jsonAnswerObject = Json.array();
			    	              			}
			    	              			jsonAnswerObject.add(Json.object().set("label", v).set("iri",answerObject.getIRI()));
			    	              				
			    	              		}
			    	              	}
			    					if(answer == null)
			    					{
			    						answer =  factory.getOWLNamedIndividual(fullIri("ServiceAnswerTemp" +tempIndividualCounter++));
			    	        			m.addAxiom(o, factory.getOWLClassAssertionAxiom(serviceAnswerType, answer));
			    	                	m.addAxiom(o, factory.getOWLObjectPropertyAssertionAxiom
			    	                			(factory.getOWLObjectProperty("legacy:hasServiceAnswer",pm), bo.getBusinessObject(), answer));
			    					}
			    					m.addAxiom(o,factory.getOWLObjectPropertyAssertionAxiom
				                			(factory.getOWLObjectProperty("legacy:hasAnswerObject", pm),answer, answerObject));
				                	m.addAxiom(o, factory.getOWLObjectPropertyAssertionAxiom
				                			(factory.getOWLObjectProperty("legacy:hasServiceField",pm),answer,serviceField));
				    			}
				    			else
				    			{
				    				if(dataTypeCode.equals("CHAROPT"))
				    				{
				    					if(answer == null)
				    					{
				    						answer =  factory.getOWLNamedIndividual(fullIri("ServiceAnswerTemp" +tempIndividualCounter++));
				    					}
				    					setAnswerValue(bo, pm, o, m, factory,
												serviceAnswerType,
												serviceField, v,
												dataTypeCode, answer);
				    					
				    				}else
				    				{
				    					if(jsonAnswerValue == null)
				    					{
				    						jsonAnswerValue = Json.object().set("type", "http://www.w3.org/2001/XMLSchema#string").set("literal",value);
				    					}
				    					System.err.println("Answer object could not be resolved for field " + serviceField + " adding to old data" );
				    				}
				    			}
	        				}
			    		}
	        			if(jsonAnswerObject != null || jsonAnswerValue != null)
	        			{
	        				System.out.println("SR EID with old answers:" + eid);
	        				Json serviceAnswers = oldData.at("hasServiceAnswer", Json.array());
	                  		Json serviceAnswer = Json.object();
	                  		serviceAnswer.set("type", "legacy:ServiceAnswer").set("iri", factory.getOWLNamedIndividual(fullIri("ServiceAnswerTemp" +tempIndividualCounter++)).getIRI().toString());
	                  		serviceAnswer.set("hasServiceField", 
	    								Json.object().set("label", rs.getString("ATTRIBUTE_LABEL_DESCRIPTION"))
	    											 .set("iri", serviceField.getIRI().toString())
	    											 .set("hasDataType", Json.object().set("type", "http://www.w3.org/2001/XMLSchema#string").set("literal", rs.getString("data_type_code"))));
	                  		if(jsonAnswerObject != null)
	                  			serviceAnswer.set("hasAnswerObject", (jsonAnswerObject.asJsonList().size() == 1)?jsonAnswerObject.asJsonList().get(0):jsonAnswerObject);
	                  		else
	                  			serviceAnswer.set("hasAnswerValue", jsonAnswerValue);
	                  		serviceAnswers.asJsonList().add(serviceAnswer);
	        			}
			    	}else
		        	{
			    		if(answer == null)
						{
							answer =  factory.getOWLNamedIndividual(fullIri("ServiceAnswerTemp" +tempIndividualCounter++));
						}
			    		setAnswerValue(bo, pm, o, m, factory,
								serviceAnswerType, serviceField, value,
								dataTypeCode, answer);
	        		}
        		}
            	
            }
        }
        catch(Exception e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
        	if (rs != null)
        		try { rs.close(); } catch (Throwable t) { }        	
        	if (stmt != null)
        		try { stmt.close(); } catch (Throwable t) { }
        }
    	if(DBG)
    		ThreadLocalStopwatch.getWatch().time("sr answers finished.");
	}

	private static void setAnswerValue(BOntology bo, DefaultPrefixManager pm,
			OWLOntology o, OWLOntologyManager m, OWLDataFactory factory,
			OWLClass serviceAnswerType, OWLNamedIndividual serviceField,
			String value, String dataTypeCode, OWLNamedIndividual answer) {
		OWLLiteral literal;
		if(answer == null)
		{
			answer = factory.getOWLNamedIndividual(fullIri(serviceAnswerType.getIRI().getFragment() + Refs.idFactory.resolve().newId(null)));
			m.addAxiom(o, factory.getOWLClassAssertionAxiom(serviceAnswerType, answer));
		}
		m.addAxiom(o, factory.getOWLObjectPropertyAssertionAxiom
				(factory.getOWLObjectProperty("legacy:hasServiceAnswer",pm), bo.getBusinessObject(), answer));
		if(dataTypeCode.equals("NUMBER"))
			literal = factory.getOWLLiteral(Float.parseFloat(value));
		else
			literal = factory.getOWLLiteral(value);
		m.addAxiom(o,factory.getOWLDataPropertyAssertionAxiom
				(factory.getOWLDataProperty("legacy:hasAnswerValue", pm),answer, literal));
		m.addAxiom(o, factory.getOWLObjectPropertyAssertionAxiom
				(factory.getOWLObjectProperty("legacy:hasServiceField",pm),answer,serviceField));
	}

	public static void getActivities(Connection conn, BOntology bo, String eid, DefaultPrefixManager pm, Json oldData)
	{
		if(DBG)
			ThreadLocalStopwatch.getWatch().time("sr activities started.");
		Statement stmt = null;
    	ResultSet rs = null;
    	try
    	{
            StringBuffer sql = new StringBuffer();
            sql.append("select ");
            sql.append("		EID || '' as EID,");
            sql.append("		SERVICE_REQUEST_EID,");
            sql.append("		ACTIVITY_CODE,");
            sql.append("		DUE_DATE,");
			sql.append("		COMPLETE_DATE,");
			sql.append("		ASSIGNED_STAFF_EID,");
			sql.append("		OUTCOME_CODE,");
			sql.append("		SYSTEM_PRINT_JOB_EID,");
			sql.append("		LINK_SERVICE_REQUEST_EID,");
			sql.append("		DETAILS,");
			sql.append("		BUSINESS_CODES,");
			sql.append("		ORDER_BY,");
			sql.append("		CREATED_DATE,");
			sql.append("		CREATED_BY_EID,");
			sql.append("		UPDATED_DATE,");
			sql.append("		UPDATED_BY_EID,");
			sql.append("		PRECEDED_BY_EID,");
			sql.append("		SERVICE_REQUEST_TYPE_CODE,");
			sql.append("		COALESCE(COMPLETED_DATE_TIMESTAMP,COMPLETE_DATE) as COMPLETED_DATE_TIMESTAMP");
			sql.append("		from SR_ACTIVITIES");
			sql.append("		WHERE ");
			sql.append("  		SERVICE_REQUEST_EID = ").append(eid).append("");
			
			stmt = conn.createStatement();            
            rs = stmt.executeQuery(sql.toString());
            OWLOntologyManager manager = bo.getOntology().getOWLOntologyManager();
            OWLOntology o = bo.getOntology();
            OWLDataFactory factory = manager.getOWLDataFactory();
            OWLClass serviceActivityType = factory.getOWLClass("legacy:ServiceActivity", pm);
            while (rs.next())
            {
            	//String activityId =  rs.getString("EID");
            	String serviceCode =  rs.getString("SERVICE_REQUEST_TYPE_CODE");
            	String activityCode =  rs.getString("ACTIVITY_CODE");
            	OWLNamedIndividual serviceActivity = factory.getOWLNamedIndividual(fullIri(serviceActivityType.getIRI().getFragment() + Refs.idFactory.resolve().newId(null)));
            	OWLNamedIndividual activity = factory.getOWLNamedIndividual("legacy:" + serviceCode + "_" + activityCode, pm);
            	OWLNamedIndividual outcomeIndividual = null;
            	String outcome = rs.getString("OUTCOME_CODE");
            	String details = rs.getString("DETAILS");
            	if(activityCode.equals("PWSTATUS"))
            	{
            		activity = factory.getOWLNamedIndividual("legacy:" + "PW_" + activityCode, pm);
            		outcomeIndividual = factory.getOWLNamedIndividual("legacy:OUTCOME_PW" + outcome,pm);
            	}
            	else
            	{	
            		if(outcome != null)
            		{
            			outcomeIndividual = factory.getOWLNamedIndividual("legacy:OUTCOME_" + outcome,pm);
            		}
            	}
            	if(!OWL.ontology().containsIndividualInSignature(activity.getIRI(), true) || (outcomeIndividual != null && !OWL.ontology().containsIndividualInSignature(outcomeIndividual.getIRI(), true) ))
              	{
              		System.err.println("Activity" + activity + " or Outcome "+ outcome +" not defined in meta. Creating old data...");
              		Json serviceActivities = oldData.at("hasServiceActivity", Json.array());
              		Json jsonServiceActivity = Json.object();
              		jsonServiceActivity.set("iri", serviceActivity.getIRI().toString()).set("type", "legacy:ServiceActivity");
              		jsonServiceActivity.set("hasActivity", 
								Json.object().set("label", activityCode)
											 .set("iri", activity.getIRI().toString()));
              		if(outcomeIndividual != null)
              		{
              			jsonServiceActivity.set("hasOutcome", 
								Json.object().set("label", outcome)
											 .set("iri", outcomeIndividual.getIRI().toString()));
              		}
              		 if ( details != null)
     	            	manager.addAxiom(o,factory.getOWLDataPropertyAssertionAxiom(
     							factory.getOWLDataProperty("legacy:hasDetails",pm)
     							, serviceActivity, details));
              		jsonServiceActivity.set("hasDetails", details);
              		setDateInJson(jsonServiceActivity, "hasDateCreated", rs.getTimestamp("CREATED_DATE"));
              		setDateInJson(jsonServiceActivity, "hasUpdatedDate", rs.getTimestamp("UPDATED_DATE"));
              		setDateInJson(jsonServiceActivity, "hasCompletedTimestamp", rs.getTimestamp("COMPLETED_DATE_TIMESTAMP"));
              		setDateInJson(jsonServiceActivity, "hasDueDate", rs.getTimestamp("DUE_DATE"));
              		serviceActivities.asJsonList().add(jsonServiceActivity);
              		continue; 
              	}
            	manager.addAxiom(o, factory.getOWLClassAssertionAxiom(serviceActivityType, serviceActivity));
            	manager.addAxiom(o,factory.getOWLObjectPropertyAssertionAxiom(
							factory.getOWLObjectProperty("legacy:hasActivity",pm)
							, serviceActivity, activity));
                if ( outcomeIndividual != null)
 	            	manager.addAxiom(o,factory.getOWLObjectPropertyAssertionAxiom(
 							factory.getOWLObjectProperty("legacy:hasOutcome",pm)
 							, serviceActivity, outcomeIndividual ));
 	            if ( details != null)
	            	manager.addAxiom(o,factory.getOWLDataPropertyAssertionAxiom(
							factory.getOWLDataProperty("legacy:hasDetails",pm)
							, serviceActivity, details));
 	            if(rs.getString("ASSIGNED_STAFF_EID") != null)
 	            {
	 	            String assignedStaff = getUsername(rs.getString("ASSIGNED_STAFF_EID"));
		 	  		if(assignedStaff != null)
		 	  		{
		 	  			manager.addAxiom(o,
		 	  					factory.getOWLDataPropertyAssertionAxiom(
		 	  						factory.getOWLDataProperty("legacy:isAssignedTo", pm),
		 	  						serviceActivity, assignedStaff
		 	  					));
		 	  		}
 	            }
 	            setDate(serviceActivity, "mdc:hasDateCreated", rs.getTimestamp("CREATED_DATE"), pm, manager, o, factory);
 	            setDate(serviceActivity, "legacy:hasUpdatedDate", rs.getTimestamp("UPDATED_DATE"), pm, manager, o, factory);
 	            setDate(serviceActivity, "legacy:hasCompletedTimestamp", rs.getTimestamp("COMPLETED_DATE_TIMESTAMP"), pm, manager, o, factory);
 	            setDate(serviceActivity, "legacy:hasDueDate", rs.getTimestamp("DUE_DATE"), pm, manager, o, factory);
 	        	manager.addAxiom(o, factory.getOWLObjectPropertyAssertionAxiom(
  						factory.getOWLObjectProperty("legacy:hasServiceActivity",pm)
  						, bo.getBusinessObject(), serviceActivity));
 	            
            }
            
    		}catch (Exception ex)
        	{
        		throw new RuntimeException(ex);
        	}
        	finally
        	{
            	if (rs != null)
            		try { rs.close(); } catch (Throwable t) { }        	
            	if (stmt != null)
            		try { stmt.close(); } catch (Throwable t) { }
        	}
    	if(DBG)	
    		ThreadLocalStopwatch.getWatch().time("sr activities finished.");
	}

	private static void setDate(OWLNamedIndividual individual,String owlProperty, Date dateValue, DefaultPrefixManager pm,
			OWLOntologyManager manager, OWLOntology o, OWLDataFactory factory) {
		if(dateValue != null)
		{
			try
			{
			Calendar c = Calendar.getInstance();
			c.setTime(dateValue);
			OWLLiteral xmlDate = factory.getOWLLiteral(DatatypeFactory.newInstance().newXMLGregorianCalendar((GregorianCalendar)c).toXMLFormat(), OWL2Datatype.XSD_DATE_TIME_STAMP);
			manager.addAxiom(o,
					factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty(owlProperty, pm),individual, 
						xmlDate
					));
			}catch (Exception e) {
				System.out.println("Error parsing date for " + dateValue.toString());
				
			}
		}
	}
	
	private static void setDateInJson(Json json,String jsonProperty, Date dateValue) {
		if(dateValue != null)
		{
			try
			{
			Calendar c = Calendar.getInstance();
			c.setTime(dateValue);
			json.set(jsonProperty,
					//Json.object().set("type", "http://www.w3.org/2001/XMLSchema#dateTimeStamp").set("literal",
					DatatypeFactory.newInstance().newXMLGregorianCalendar((GregorianCalendar)c).toXMLFormat());
					//);
			}catch (Exception e) {
				System.out.println("Error parsing date for " + dateValue.toString());
			}
		}
	}
	
	public static void getServiceActors(Connection conn, BOntology bo, String eid, DefaultPrefixManager pm, OWLDataFactory metaFactory)
	{
		if(DBG)
			ThreadLocalStopwatch.getWatch().time("sr actors started.");
		Statement stmt = null;
    	ResultSet rs = null;
    	try
    	{
            StringBuffer sql = new StringBuffer();
            sql.append("select ");
            sql.append("		EID || '' as EID,");
            sql.append("		SERVICE_REQUEST_EID,");
            sql.append("		ENTITY_EID,");
            sql.append("		ENTITY_TABLE,");
            sql.append("		IS_PART_THE_SOURCE_OF_SR_IND,");
            sql.append("		TYPE_CODE,");
            sql.append("		NAME1,");
            sql.append("		NAME2,");
            sql.append("		NAME3,");
            sql.append("		ADDRESS_TYPE_CODE,");
            sql.append("		STREET_NUMBER,");
            sql.append("		STREET_NAME_PREFIX,");
            sql.append("		STREET_NAME,");
            sql.append("		STREET_NAME_SUFFIX,");
            sql.append("		STREET_SUFFIX_DIRECTION,");
            sql.append("		STREET_LINE2,");
            sql.append("		CITY,");
			sql.append("		STATE_CODE,");
			sql.append("		ZIP_CODE,");
			sql.append("		PHONE_NUM,");
			sql.append("		EXTENSION,");
			sql.append("		FAX_NUM,");
			sql.append("		E_ADDRESS,");
			sql.append("		BUSINESS_CODES,");
			sql.append("		ORDER_BY,");
			sql.append("		CREATED_DATE,");
			sql.append("		CREATED_BY_EID,");
			sql.append("		UPDATED_DATE,");
			sql.append("		UPDATED_BY_EID,");
			sql.append("		SERVICE_REQUEST_TYPE_CODE");
			sql.append("		from SR_PARTICIPANTS");
			sql.append("		WHERE ");
			sql.append("  		SERVICE_REQUEST_EID = ").append(eid).append("");
			
			stmt = conn.createStatement();            
            rs = stmt.executeQuery(sql.toString());
            OWLOntologyManager manager = bo.getOntology().getOWLOntologyManager();
            OWLOntology o = bo.getOntology();
            OWLDataFactory factory = manager.getOWLDataFactory();
            OWLClass serviceCaseActorType = factory.getOWLClass("legacy:ServiceCaseActor", pm);
            OWLClass addressType = factory.getOWLClass("mdc:Street_Address", pm);
            while (rs.next())
            {
            	String name1 = rs.getString("NAME1");
            	if(name1 != null)
            	{
            	//String actorId =  rs.getString("EID");
            	String actorCode =  rs.getString("TYPE_CODE");
            	OWLNamedIndividual serviceCaseActor = factory.getOWLNamedIndividual(fullIri(serviceCaseActorType.getIRI().getFragment() + Refs.idFactory.resolve().newId(null)));
            	OWLNamedIndividual serviceActor = factory.getOWLNamedIndividual("legacy:" + actorCode, pm);
            	manager.addAxiom(o, factory.getOWLClassAssertionAxiom(serviceCaseActorType, serviceCaseActor));
//            	manager.addAxiom(o,
//						factory.getOWLDataPropertyAssertionAxiom(
//							factory.getOWLDataProperty("legacy:hasLegacyId", pm),serviceCaseActor, 
//							factory.getOWLLiteral(actorId)
//						));
            	manager.addAxiom(o,factory.getOWLObjectPropertyAssertionAxiom(
							factory.getOWLObjectProperty("legacy:hasServiceActor",pm)
							, serviceCaseActor, serviceActor));
            	String email = rs.getString("E_ADDRESS");
 	            if ( email != null)
 	            {
 	            	OWLNamedIndividual emailInd = factory.getOWLNamedIndividual(IRI.create("mailto:" + email));
 	            	manager.addAxiom(o,factory.getOWLObjectPropertyAssertionAxiom(
 							factory.getOWLObjectProperty("mdc:hasEmailAddress",pm)
 							, serviceCaseActor,emailInd));
 	            	manager.addAxiom(o, factory.getOWLClassAssertionAxiom(factory.getOWLClass("mdc:EmailAddress", pm), emailInd));
 	            }
// 	            String businessCodes = rs.getString("BUSINESS_CODES");
//				if(businessCodes != null)
//					manager.addAxiom(o, factory.getOWLDataPropertyAssertionAxiom(
//							factory.getOWLDataProperty("legacy:hasBusinessCodes", pm), 
//							serviceCaseActor, 
//							businessCodes));
				String phoneNumber = rs.getString("PHONE_NUM");
				if(phoneNumber != null)
					manager.addAxiom(o, factory.getOWLDataPropertyAssertionAxiom(
							factory.getOWLDataProperty("mdc:PhoneNumber", pm), 
							serviceCaseActor, 
							phoneNumber));
				String faxNumber = rs.getString("FAX_NUM");
				if(faxNumber != null)
					manager.addAxiom(o, factory.getOWLDataPropertyAssertionAxiom(
							factory.getOWLDataProperty("mdc:FaxNumber", pm), 
							serviceCaseActor, 
							faxNumber));
 	            String extension = rs.getString("EXTENSION");
				if(extension != null)
					manager.addAxiom(o, factory.getOWLDataPropertyAssertionAxiom(
							factory.getOWLDataProperty("mdc:PhoneExtension", pm), 
							serviceCaseActor, 
							extension));
            	
            	String name2 = rs.getString("NAME2");
            	String name3 = rs.getString("NAME3");
        		if ( name2 == null)
	            {
            		   		manager.addAxiom(o,factory.getOWLDataPropertyAssertionAxiom(
	            			factory.getOWLDataProperty("mdc:Name",pm)
							, serviceCaseActor, name1));
	            }else
	            {
	            	manager.addAxiom(o,factory.getOWLDataPropertyAssertionAxiom(
	            			factory.getOWLDataProperty("mdc:LastName",pm)
							, serviceCaseActor, name1));
	            	manager.addAxiom(o,factory.getOWLDataPropertyAssertionAxiom(
	            			factory.getOWLDataProperty("mdc:Name",pm)
							, serviceCaseActor, name2));
		            if ( name3 != null)
			            manager.addAxiom(o,factory.getOWLDataPropertyAssertionAxiom(
									factory.getOWLDataProperty("mdc:MiddleName",pm)
									, serviceCaseActor, name3));
	            }
	            
        		BigDecimal streetNumber = rs.getBigDecimal("STREET_NUMBER");
            	if(streetNumber != null  && rs.getString("ZIP_CODE") != null)
            	{
			        	OWLNamedIndividual address = factory.getOWLNamedIndividual(fullIri(addressType.getIRI().getFragment() + Refs.idFactory.resolve().newId(null)));
			            manager.addAxiom(o, factory.getOWLClassAssertionAxiom(addressType, address));
			        	manager.addAxiom(o,
								factory.getOWLObjectPropertyAssertionAxiom(
									factory.getOWLObjectProperty("mdc:atAddress", pm),serviceCaseActor, 
									address
								));
			            manager.addAxiom(o, factory.getOWLDataPropertyAssertionAxiom(
								factory.getOWLDataProperty("mdc:Street_Number", pm), 
								address, 
								streetNumber.toPlainString()));
			            String streetName = rs.getString("STREET_NAME");
			            if(streetName != null)
				            manager.addAxiom(o, factory.getOWLDataPropertyAssertionAxiom(
									factory.getOWLDataProperty("mdc:Street_Name", pm), 
									address, 
									streetName));
						String unitNumber = rs.getString("STREET_LINE2");
						if(unitNumber != null)
							manager.addAxiom(o, factory.getOWLDataPropertyAssertionAxiom(
									factory.getOWLDataProperty("mdc:Street_Unit_Number", pm), 
									address, 
									unitNumber));
						String zip = (rs.getString("ZIP_CODE").length() > 5)?rs.getString("ZIP_CODE").substring(0,5):rs.getString("ZIP_CODE");
						try{
							manager.addAxiom(o, factory.getOWLDataPropertyAssertionAxiom(
							factory.getOWLDataProperty("mdc:Zip_Code", pm), 
							address, 
							factory.getOWLLiteral(Integer.parseInt(zip))));
						}catch(NumberFormatException e)
						{
							System.err.print(zip + " is not a valid zip code, could assert the axiom.");
						}
						String prefix = rs.getString("STREET_NAME_PREFIX");
						if(prefix != null)
						{
							OWLNamedIndividual direction = getDirection(prefix, factory);
							if(direction != null)
								manager.addAxiom(o, factory.getOWLObjectPropertyAssertionAxiom(
										factory.getOWLObjectProperty("mdc:Street_Direction", pm), 
										address, 
										direction));
						}
						String cityStr = rs.getString("CITY");
						if(cityStr != null)
						{
						
							OWLNamedIndividual city = getCity(cityStr, factory);
							if(city != null)
								manager.addAxiom(o, factory.getOWLObjectPropertyAssertionAxiom(
										factory.getOWLObjectProperty("mdc:Street_Address_City", pm), 
										address, 
										city));
							else
							{
								System.out.println("No city for: " + serviceCaseActor+ ", city alias '" + cityStr + "' not recognized");
								System.out.println("Warning: No city individual with Name or Alias '" + cityStr+ "' found in ontology.");
							}
						}
						String stateStr = rs.getString("STATE_CODE");
						if(stateStr != null)
						{
						
							OWLNamedIndividual state = getState(stateStr, metaFactory, pm);
							if(state != null)
							{
								manager.addAxiom(o, factory.getOWLObjectPropertyAssertionAxiom(
										factory.getOWLObjectProperty("mdc:Street_Address_State", pm), 
										address, 
										state));
							}
							else
							{
								System.out.println("No state for: " + serviceCaseActor.getIRI().toString()  +  ", state alias '" + stateStr + "' not recognized");
								System.out.println("Warning: No state individual with Name or Alias '" + stateStr+ "' found in ontology.");
							}
						}
						
						String suffix = rs.getString("STREET_NAME_SUFFIX");
						if(suffix != null)
						{
						
							OWLNamedIndividual suf = getSuffix(suffix, factory);
							if(suf != null)
								manager.addAxiom(o, factory.getOWLObjectPropertyAssertionAxiom(
										factory.getOWLObjectProperty("mdc:hasStreetType", pm), 
										address, 
										suf));
							else
							{
								System.out.println("No suffix for: " + serviceCaseActor.getIRI().toString() + ", suffix '" + suffix + "' not recognized");
								System.out.println("Warning: No suffix individual with Name or Alias '" + suffix+ "' found in ontology.");
							}
						}
						manager.addAxiom(o, factory.getOWLDataPropertyAssertionAxiom(
								factory.getOWLDataProperty("mdc:fullAddress", pm), 
								address, 
								toFullAddress(streetNumber.toPlainString(), prefix, streetName, suffix)));
            	}
				setDate(serviceCaseActor, "mdc:hasDateCreated", rs.getTimestamp("CREATED_DATE"),pm, manager, o, factory);
				setDate(serviceCaseActor, "legacy:hasUpdatedDate", rs.getTimestamp("UPDATED_DATE"),pm, manager, o, factory);
				manager.addAxiom(o, factory.getOWLObjectPropertyAssertionAxiom(
  						factory.getOWLObjectProperty("legacy:hasServiceCaseActor",pm)
  						, bo.getBusinessObject(), serviceCaseActor));
            	}
            }
            
    		}catch (Exception ex)
        	{
        		throw new RuntimeException(ex);
        	}
        	finally
        	{
            	if (rs != null)
            		try { rs.close(); } catch (Throwable t) { }        	
            	if (stmt != null)
            		try { stmt.close(); } catch (Throwable t) { }
        	}
    	if(DBG)	
    		ThreadLocalStopwatch.getWatch().time("sr actors finished.");
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
	
	public static OWLNamedIndividual getAnswerObjectIndividual(OWLNamedIndividual serviceField, String legacyValue )
	{
			OWLDataFactory factory = OWL.dataFactory();
			for( OWLNamedIndividual choiceValueList : OWL.objectProperties(serviceField, "legacy:hasChoiceValueList"))
			{
				for(OWLNamedIndividual choiceValue : OWL.objectProperties(choiceValueList, "legacy:hasChoiceValue"))
				{
						Set<OWLLiteral> legacyCodes = OWL.dataProperties(choiceValue, ":hasLegacyCode");
						if(!legacyCodes.isEmpty() && legacyCodes.contains(factory.getOWLLiteral(legacyValue)))
							return choiceValue;
						else if(legacyValue != null &&	legacyValue.equalsIgnoreCase(OWL.getEntityLabel(choiceValue)))
							return choiceValue;
						else if(EXPIRED_ANSWER_TO_INDIVIDUAL_MAP.containsKey(OWL.getEntityLabel(choiceValue)))
							return EXPIRED_ANSWER_TO_INDIVIDUAL_MAP.get(OWL.getEntityLabel(choiceValue));
				}
			}
			return null;
		
	}
	
	public static boolean isWithinNormalBusinessHours()
	{
		
		Calendar c = Calendar.getInstance();
		int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
		int hour = c.get(Calendar.HOUR_OF_DAY);
		if( dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)
			return false;
		else if ( hour < 7   ||  hour > 17)
			return false;
		else
			return true;
	}
	
	private static PoolDataSource createPoolDatasource(String url,String username, String password) throws SQLException
	{
		
		String poolName = "Cirm UCP Pool for " + LegacyCaseImport.class.getSimpleName();
		PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();
		pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
		pds.setURL(url);
		pds.setUser(username);
		pds.setPassword(password);
		pds.setConnectionPoolName(poolName);
		pds.setInitialPoolSize(POOL_SIZE_INITIAL);
		pds.setMinPoolSize(POOL_SIZE_INITIAL);
		pds.setMaxPoolSize(POOL_SIZE_MAX);
		pds.setMaxConnectionReuseCount(POOL_CONNECTION_REUSE_COUNT_MAX);
		//Sets implicit statement cache on all pooled connections
		pds.setMaxStatements(POOL_CONNECTION_STATEMENTS_MAX);
		pds.setValidateConnectionOnBorrow(POOL_CONNECTION_VALIDATE_ON_BORROW);
		//How long to wait if a conn is not available
		pds.setConnectionWaitTimeout(POOL_CONNECTION_WAIT_TIMEOUT_SECS);
		//How many secs to wait until a pooled and unused connection is removed from pool
		// 8 h
		pds.setInactiveConnectionTimeout(POOL_CONNECTION_INACTIVE_TIMEOUT_SECS);
		Properties connectionProperties = new Properties();
		connectionProperties.setProperty("defaultRowPrefetch", "" + POOL_CONNECTION_PREFETCH_ROWS);
		connectionProperties.setProperty("defaultBatchValue", "" + POOL_CONNECTION_BATCH_ROWS);
		pds.setConnectionProperties(connectionProperties);
		System.out.println("ORACLE POOL DATA SOURCE : ");		
		System.out.println("DB URL : " + url);
		try {
			Connection testConn = pds.getConnection();
			testConn.close();
		} catch (Exception e)
		{
			ThreadLocalStopwatch.getWatch().time("POOL DATA SOURCE: FAILED TO GET A TEST CONNECTION FROM POOL!\r\n Exception was: ");
			e.printStackTrace();
			System.err.print("Attemting to destroy the failing pool \"" + poolName + "\"...");
			try
			{
				UniversalConnectionPoolManager pm = UniversalConnectionPoolManagerImpl.getUniversalConnectionPoolManager();
				pm.destroyConnectionPool(poolName);
				System.err.println("Succeeded.");
			} catch (UniversalConnectionPoolException e1)
			{
				System.err.println("Failed. Exception on failing to destroy pool was:");
				e1.printStackTrace();
			}
			throw new RuntimeException(e);
		}
		DBGUtils.printPoolDataSourceInfo(pds);
		return pds;
	}
	
	private static String getRERSRTypes()
	{
		StringBuilder RERsrTypes = new StringBuilder();
		RERsrTypes.append("'BCCOCLNN'")
		.append(",'CONTRACT'")
		//.append(",'DERMWTR'")
		.append(",'TM100'")
		.append(",'TM13'")
		.append(",'TM14'")
		.append(",'TM15'")
		.append(",'TM16'")
		.append(",'TM24'")
		.append(",'TM451'")
		.append(",'TM50'")
		.append(",'TM500'")
		.append(",'TM501'")
		.append(",'TM501B'")
		.append(",'TM501B2'")
		.append(",'TM502'")
		.append(",'TM505'")
		.append(",'TM51'")
		.append(",'TM510'")
		.append(",'TM511'")
		.append(",'TM515'")
		.append(",'TM520'")
		.append(",'TM525'")
		.append(",'TM53'")
		.append(",'TM535'")
		.append(",'TM54'")
		.append(",'TM540'")
		.append(",'TM55'")
		.append(",'TM550'")
		.append(",'TM552'")
		.append(",'TM555'")
		.append(",'TM560'")
		.append(",'TM567'")
		.append(",'TM57'")
		.append(",'TM570'")
		.append(",'TM575'")
		.append(",'TM58'")
		.append(",'TM590'")
		.append(",'TM600'")
		.append(",'TM605'")
		.append(",'TM610'")
		.append(",'TM615'")
		.append(",'TM625'")
		.append(",'TM636'")
		.append(",'TM637'")
		.append(",'TM638'")
		.append(",'TM640'")
		.append(",'TM645'")
		.append(",'TM650'")
		.append(",'TM655'")
		.append(",'TM660'")
		.append(",'TM665'")
		.append(",'TM670'")
		.append(",'TM675'")
		.append(",'TM74'")
		.append(",'TM75'")
		.append(",'TMDOGLIC'")
		.append(",'TMMEMBER'");
		return RERsrTypes.toString();
	}
}

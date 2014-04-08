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

import static org.sharegov.cirm.OWL.dataProperty;
import static org.sharegov.cirm.OWL.reasoner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import mjson.Json;

import org.semanticweb.owlapi.io.OWLXMLOntologyFormat;
import org.semanticweb.owlapi.io.WriterDocumentTarget;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.PrefixManager;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.semanticweb.owlapi.vocab.PrefixOWLOntologyFormat;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.owl.SynchronizedOWLManager;


public class LegacyOntologyExporter
{
	
	 private static Map<String,String> personnelIDs = new HashMap<String,String>();
	 private static Map<String,String> csrUsernames = new HashMap<String, String>();
	 private static Map<String,String> emails = new HashMap<String, String>();
	 private static Map<String,List<Map<String,String>>> userGeoAssignment = new HashMap<String, List<Map<String,String>>>();
	 private static Map<String,Map<OWLNamedIndividual,String>> geaArea2LayerMapping = new HashMap<String, Map<OWLNamedIndividual,String>>();
	 private static Map<String,String> groupEmails = new HashMap<String, String>();
	 
	 
	 
	 private static final boolean SINGLE_EXPORT_MODE = true;
	 private static final boolean SET_DISABLED_CREATE = true;
	 private static final boolean DBG = false;
	
	 private static void loadGeaArea2LayerMapping(OWLOntologyManager manager, PrefixManager pm)
	 {
			
		 	OWLDataFactory factory = manager.getOWLDataFactory();
		 	geaArea2LayerMapping.put("COMDIST1", Collections.singletonMap(factory.getOWLNamedIndividual("mdc:CommissionDistrictLayer", pm), "ID"));
//			geaArea2LayerMapping.put("METRO", value);
//			geaArea2LayerMapping.put("MIACINS", value);
			geaArea2LayerMapping.put("MIACODE", Collections.singletonMap(factory.getOWLNamedIndividual("mdc:CodeEnforcementZonesLayer", pm), "CESECTNAME"));
			geaArea2LayerMapping.put("MIACOMD", Collections.singletonMap(factory.getOWLNamedIndividual("mdc:MiamiCommissionDistrictLayer", pm), "COMDISTID"));
			geaArea2LayerMapping.put("MUNICIP", Collections.singletonMap(factory.getOWLNamedIndividual("mdc:MunicipalityPolyLayer", pm), "NAME"));
			geaArea2LayerMapping.put("NETA", Collections.singletonMap(factory.getOWLNamedIndividual("mdc:NetAreasLayer", pm), "NETID"));
			geaArea2LayerMapping.put("OUTREACH", Collections.singletonMap(factory.getOWLNamedIndividual("mdc:OutreachLayer", pm), "NAME")); 
			geaArea2LayerMapping.put("PAINTNO", Collections.singletonMap(factory.getOWLNamedIndividual("mdc:PainterTerritoryLayer", pm), "ID"));
			geaArea2LayerMapping.put("SWENFID", Collections.singletonMap(factory.getOWLNamedIndividual("mdc:SolidWasteEnforcementZoneLayer", pm), "ENFZONEID"));
			geaArea2LayerMapping.put("SWENFMUN", Collections.singletonMap(factory.getOWLNamedIndividual("mdc:EnforcementMunicipalitiesLayer", pm), "ENFORCEMEN"));
			geaArea2LayerMapping.put("SWRCENF", Collections.singletonMap(factory.getOWLNamedIndividual("mdc:RecyclingEnforcementZonesLayer", pm), "ENFZONEID"));
			geaArea2LayerMapping.put("SWRECWK", Collections.singletonMap(factory.getOWLNamedIndividual("mdc:RecyclingRouteLayer", pm), "ROUTE"));
			geaArea2LayerMapping.put("TEAMOFFC", Collections.singletonMap(factory.getOWLNamedIndividual("mdc:TeamMetroBoundaryLayer", pm), "NAME"));
//			geaArea2LayerMapping.put("TMOUTRCH", value);
		 
	 }
	 
	 public static void getServiceFields(Connection conn, OWLOntology csr, OWLOntologyManager manager, PrefixManager pm, String serviceCode)
	 {
    	Statement stmt = null;
    	Statement stmt2 = null;
    	ResultSet rs = null;
    	ResultSet rs2 = null;
        try
        {
        	
            StringBuffer sql = new StringBuffer();
            sql.append("select type_code, sub_type_code, ");
            sql.append("       attribute_label_code, attribute_label_description, ");
            sql.append("       lov_type_code, data_type_code, ");
            sql.append("       is_attribute_mandatory_ind, order_by, ");
            sql.append("       lower_limit, upper_limit, ");
            sql.append("       force_upper_ind, business_codes, allowable_module_codes");
            sql.append("  from custom_attributes_templates ");
            sql.append(" where type_code = '" + serviceCode + "' ");
            sql.append("   and automatically_create_ind = 'Y' ");
            sql.append("   and stop_date is null ");
            sql.append("order by order_by ");
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql.toString()); 
            OWLDataFactory factory = manager.getOWLDataFactory();
            OWLNamedIndividual type = factory.getOWLNamedIndividual(":"+ serviceCode, pm);
            OWLClass serviceFieldType = factory.getOWLClass(":ServiceField", pm);
            OWLClass serviceQuestionType = factory.getOWLClass(":ServiceQuestion", pm);
            OWLClass serviceNoteType = factory.getOWLClass(":ServiceNote", pm);
            manager.addAxiom(csr,factory.getOWLSubClassOfAxiom(serviceQuestionType, serviceFieldType));
            manager.addAxiom(csr,factory.getOWLSubClassOfAxiom(serviceNoteType, serviceFieldType));
            while (rs.next())
            {
            	String fieldCode =  rs.getString("ATTRIBUTE_LABEL_CODE");
            	OWLNamedIndividual field = factory.getOWLNamedIndividual(":" + URLEncoder.encode( serviceCode + "_" + fieldCode,"UTF-8"), pm);      
            	String bCodes = rs.getString("BUSINESS_CODES");
  	           	if(bCodes != null)
  	           	{
  	           	manager.addAxiom(csr,factory.getOWLDataPropertyAssertionAxiom(
  						factory.getOWLDataProperty(":hasBusinessCodes",pm)
  						, field, bCodes));
  	           	}
  	           	if(bCodes != null && bCodes.contains("NOUPDATE"))
  	           	{
  	           		manager.addAxiom(csr,factory.getOWLClassAssertionAxiom(serviceNoteType, field));
  	           	}else
  	           	{
  	           		manager.addAxiom(csr,factory.getOWLClassAssertionAxiom(serviceQuestionType, field));
  	           	}
  	            String allowableModuleCodes = rs.getString("ALLOWABLE_MODULE_CODES");
	           	if(allowableModuleCodes != null)
	           	{
	           	manager.addAxiom(csr,factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty(":hasAllowableModules",pm)
						, field, allowableModuleCodes));
	           	}
  	           		manager.addAxiom(csr,factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty(":hasLegacyCode",pm)
						, field, factory.getOWLLiteral(fieldCode)));
  	           	manager.addAxiom(csr,factory.getOWLAnnotationAssertionAxiom(
	            		field.getIRI(),
						factory.getOWLAnnotation(
								factory.getRDFSLabel()
								,factory.getOWLLiteral(rs.getString("ATTRIBUTE_LABEL_DESCRIPTION")))));
	            String dataTypeCode = rs.getString("DATA_TYPE_CODE");
	            String lovType = rs.getString("LOV_TYPE_CODE");
	            manager.addAxiom(csr,factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty(":hasDataType",pm)
						, field, dataTypeCode));
	            manager.addAxiom(csr,factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty(":hasOrderBy",pm)
						, field, rs.getFloat("ORDER_BY")));
              
	            manager.addAxiom(csr,factory.getOWLObjectPropertyAssertionAxiom(factory.getOWLObjectProperty(":hasServiceField",pm), type, field));
	         
               
	            if(dataTypeCode == null || (!dataTypeCode.equals("CHARLIST") && !dataTypeCode.equals("CHARMULT") && !dataTypeCode.equals("CHAROPT") ))
                    continue;
                stmt2 = conn.createStatement();
                String sql2 = "select * from all_codes where type_code = '" + 
                			  lovType + 
                			  "'" + "   and stop_date is null order by order_by";
                rs2 = stmt2.executeQuery(sql2);
                OWLClass lovClass = factory.getOWLClass(":ChoiceValueList", pm);
                OWLClass vClass = factory.getOWLClass(":ChoiceValue", pm);
                OWLNamedIndividual lov = factory.getOWLNamedIndividual(":" + URLEncoder.encode(serviceCode + "_" + fieldCode + "_" + lovType, "UTF-8"),pm);
                manager.addAxiom(csr,factory.getOWLClassAssertionAxiom(lovClass, lov));
                Set<OWLAxiom> hasOrderByAxioms = new HashSet<OWLAxiom>(); 
                Set<Float> hasOrderByNumbers = new HashSet<Float>();
                while (rs2.next())
                {
                	
                	OWLNamedIndividual v = factory.getOWLNamedIndividual(":" + URLEncoder.encode(serviceCode + "_" + fieldCode + "_" + lovType + "_" + rs2.getString("CODE_CODE"), "UTF-8"),pm);
                	manager.addAxiom(csr,factory.getOWLClassAssertionAxiom(vClass, v));
                	manager.addAxiom(csr,factory.getOWLAnnotationAssertionAxiom(
    	            		v.getIRI(),
    						factory.getOWLAnnotation(
    								factory.getRDFSLabel()
    								,factory.getOWLLiteral(rs2.getString("DESCRIPTION")))));
                	manager.addAxiom(csr,factory.getOWLDataPropertyAssertionAxiom(
    						factory.getOWLDataProperty(":hasLegacyCode",pm)
    						, v, rs2.getString("CODE_CODE")));
                	manager.addAxiom(csr,factory.getOWLObjectPropertyAssertionAxiom(
    						factory.getOWLObjectProperty(":hasChoiceValue",pm)
    						, lov, v));
                	manager.addAxiom(csr,factory.getOWLObjectPropertyAssertionAxiom(factory.getOWLObjectProperty(":hasChoiceValueList",pm), field, lov));
                	
                	//create only when set of choice values
                	//has order by that are no equal.
                	float orderBy = rs2.getFloat("order_by");
                	if(!rs2.wasNull())
                	{
                		hasOrderByNumbers.add(orderBy);
                		hasOrderByAxioms.add(factory.getOWLDataPropertyAssertionAxiom(
                				factory.getOWLDataProperty(":hasOrderBy",pm)
                				, v, orderBy));
                	}
                }
                
                if(hasOrderByNumbers.size() >= 2)
                {
                	for(OWLAxiom orderByAxiom: hasOrderByAxioms)
                	{
                		manager.addAxiom(csr, orderByAxiom);
                	}
                }//else we either have no hasOrderByAxioms or all of their order values are equal and therefore meaningless.
                rs2.close();
                stmt2.close();
            }

        }
        catch(Exception e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
        	if (rs2 != null)
        		try { rs2.close(); } catch (Throwable t) { }        	
        	if (rs != null)
        		try { rs.close(); } catch (Throwable t) { }        	
        	if (stmt2 != null)
        		try { stmt2.close(); } catch (Throwable t) { }
        	if (stmt != null)
        		try { stmt.close(); } catch (Throwable t) { }
        }
    }
	 
	public static void getPredecessors(Connection conn, OWLOntology csr, OWLOntologyManager manager, PrefixManager pm, String serviceCode)
	{
		getFieldTriggers(conn, csr, manager, pm, serviceCode);
		getActivityTriggers(conn, csr, manager, pm, serviceCode);
	}
	
	private static void getFieldTriggers(Connection conn, OWLOntology csr, OWLOntologyManager manager, PrefixManager pm, String serviceCode)
	{
		
		Statement stmt = null;
    	ResultSet rs = null;
    	try
    	{	
            StringBuffer sql = new StringBuffer();
            sql.append("select ");
            sql.append("		b.EID || + '' AS EID,");
            sql.append("		a.TYPE_CODE,");
            sql.append("		a.ATTRIBUTE_LABEL_CODE,");
            sql.append("		a.DATA_TYPE_CODE,");
			sql.append("		b.TRIGGER_VALUE,");
			sql.append("		b.OWNER_TABLE,");
			sql.append("		b.OWNER_EID || + '' AS OWNER_EID");
			sql.append("		from CUSTOM_ATTRIBUTES_TEMPLATES a, ST_PREDECESSOR_DEFINITIONS b");
			sql.append("		WHERE ");
			sql.append("  		a.TYPE_CODE = '").append(serviceCode).append("'");
			sql.append("		and a.EID = b.OWNER_EID");
			sql.append(" 		and b.OWNER_TABLE = 'CUSTOM_ATTRIBUTES_TEMPLATES'");  
			sql.append("		and a.STOP_DATE is null and b.STOP_DATE is null ");
			
			stmt = conn.createStatement();            
            rs = stmt.executeQuery(sql.toString());
            OWLDataFactory factory = manager.getOWLDataFactory();
            OWLClass legacyTrigger = factory.getOWLClass(":LegacyTrigger", pm);
            OWLClass fieldTrigger = factory.getOWLClass(":QuestionTrigger", pm);
            manager.addAxiom(csr, factory.getOWLSubClassOfAxiom(fieldTrigger, legacyTrigger));
            while (rs.next())
            {
            	String fieldCode =  rs.getString("ATTRIBUTE_LABEL_CODE");
            	String eid =  rs.getString("EID");
            	OWLNamedIndividual predecessor = factory.getOWLNamedIndividual(":" + eid, pm);   
            	OWLNamedIndividual serviceField = factory.getOWLNamedIndividual(":" + URLEncoder.encode(serviceCode + "_" + fieldCode,"UTF-8"),pm);
            	
            	String triggerValue = rs.getString("TRIGGER_VALUE");
            	if(triggerValue != null)
            	{
            		String dataTypeCode = rs.getString("DATA_TYPE_CODE");
            		OWLLiteral literal = null;
            		if((dataTypeCode.equals("CHARLIST") || dataTypeCode.equals("CHARMULT") || dataTypeCode.equals("CHAROPT")))
            		{
            			//TODO: hasAnswerObject
            			OWLNamedIndividual answerObject = getAnswerObjectIndividual(serviceField, triggerValue, csr, manager, pm);
            			if(answerObject != null)
            			{
            				manager.addAxiom(csr, factory.getOWLClassAssertionAxiom(fieldTrigger,predecessor));
            				//circular reference
            				//TODO:
//            				manager.addAxiom(csr, factory.getOWLObjectPropertyAssertionAxiom(
//                        			factory.getOWLObjectProperty(":hasServiceField", pm), predecessor, serviceField ));
//                        
            				manager.addAxiom(csr, factory.getOWLObjectPropertyAssertionAxiom(
            						factory.getOWLObjectProperty(":hasAnswerObject",pm)
            						, predecessor,answerObject));
            				
            			}
            			else
            			{
            				if(DBG)
            					System.err.println("Answer object could not be resolved for field " + serviceField );
            				continue;
            			}
            		}
            		else
            		{
            			if(dataTypeCode.equals("NUMBER"))
            				literal = factory.getOWLLiteral(Float.parseFloat(triggerValue));
            			else
            				literal = factory.getOWLLiteral(triggerValue);
            			manager.addAxiom(csr, factory.getOWLClassAssertionAxiom(fieldTrigger,predecessor));
                    	//circular reference
            			//TODO: remove
//            			manager.addAxiom(csr, factory.getOWLObjectPropertyAssertionAxiom(
//                    			factory.getOWLObjectProperty(":hasServiceField", pm), predecessor, serviceField ));
                    	manager.addAxiom(csr, factory.getOWLDataPropertyAssertionAxiom(
        						factory.getOWLDataProperty(":hasAnswerValue",pm)
        						, predecessor,literal));
            		}
            		
            	}
            	getSuccessors(conn, csr, manager, pm, eid, serviceField);
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
		
	}
	
	private static OWLNamedIndividual getAnswerObjectIndividual(OWLNamedIndividual serviceField, String legacyValue, OWLOntology csr, OWLOntologyManager manager, PrefixManager pm )
	{
			OWLDataFactory factory = manager.getOWLDataFactory();
			for( OWLIndividual choiceValueList : serviceField.getObjectPropertyValues(factory.getOWLObjectProperty(":hasChoiceValueList", pm), csr))
			{
				for(OWLIndividual choiceValue : choiceValueList.getObjectPropertyValues(factory.getOWLObjectProperty(":hasChoiceValue", pm), csr))
				{
						Set<OWLLiteral> legacyCodes = choiceValue.getDataPropertyValues(factory.getOWLDataProperty(":hasLegacyCode", pm), csr);
						if(!legacyCodes.isEmpty() && legacyCodes.contains(factory.getOWLLiteral(legacyValue)))
							return choiceValue.asOWLNamedIndividual();
				}
			}
			return null;
		
	}

	private static void getActivityTriggers(Connection conn, OWLOntology csr, OWLOntologyManager manager, PrefixManager pm, String serviceCode)
	{
		Statement stmt = null;
    	ResultSet rs = null;
    	try
    	{
            StringBuffer sql = new StringBuffer();
            sql.append("select ");
            sql.append("		b.EID || '' as EID,");
            sql.append("		a.SERVICE_REQUEST_TYPE_CODE,");
            sql.append("		a.ACTIVITY_CODE,");
            sql.append("		b.TRIGGER_VALUE,");
			sql.append("		b.OWNER_TABLE,");
			sql.append("		b.OWNER_EID || '' as OWNER_EID");
			sql.append("		from SR_ACTIVITIES_DEFINITIONS a, ST_PREDECESSOR_DEFINITIONS b");
			sql.append("		WHERE ");
			sql.append("  		a.SERVICE_REQUEST_TYPE_CODE = '").append(serviceCode).append("'");
			sql.append("		and a.EID = b.OWNER_EID");
			sql.append(" 		and b.OWNER_TABLE = 'SR_ACTIVITIES_DEFINITIONS'");  
			sql.append("		and a.CANCEL_DATE is null and b.STOP_DATE is null ");
			
			stmt = conn.createStatement();            
            rs = stmt.executeQuery(sql.toString());
            OWLDataFactory factory = manager.getOWLDataFactory();
            OWLClass legacyTrigger = factory.getOWLClass(":LegacyTrigger", pm);
            OWLClass activityTrigger = factory.getOWLClass(":ActivityTrigger", pm);
            manager.addAxiom(csr, factory.getOWLSubClassOfAxiom(activityTrigger, legacyTrigger));
            while (rs.next())
            {
            	String eid =  rs.getString("EID");
            	OWLNamedIndividual predecessor = factory.getOWLNamedIndividual(":" + eid, pm);
            	String activityCode =  rs.getString("ACTIVITY_CODE");
            	OWLNamedIndividual activity = factory.getOWLNamedIndividual(":" + serviceCode + "_" + activityCode, pm); 
            	String triggerValue = rs.getString("TRIGGER_VALUE");
            	if(triggerValue != null)
            	{
            		manager.addAxiom(csr, factory.getOWLClassAssertionAxiom(activityTrigger,predecessor));
            		manager.addAxiom(csr,factory.getOWLObjectPropertyAssertionAxiom(
    						factory.getOWLObjectProperty(":hasActivity",pm)
    						, predecessor,activity));
            		manager.addAxiom(csr,factory.getOWLObjectPropertyAssertionAxiom(
    						factory.getOWLObjectProperty(":hasOutcome",pm)
    						, predecessor,factory.getOWLNamedIndividual(":OUTCOME_" + triggerValue, pm)));
            	}
            	getSuccessors(conn, csr, manager, pm, eid, activity);
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
 	}
	
	public static void getSuccessors(Connection conn, OWLOntology csr, OWLOntologyManager manager, PrefixManager pm, String predecessor, OWLNamedIndividual subject)
	{
		getFieldAlerts(conn, csr, manager, pm, predecessor, subject);
		getActivityAssignments(conn, csr, manager, pm, predecessor, subject);
	}
	
	public static void getFieldAlerts(Connection conn, OWLOntology csr, OWLOntologyManager manager, PrefixManager pm, String predecessor, OWLNamedIndividual subject)
	{
		Statement stmt = null;
    	ResultSet rs = null;
    	try
    	{
            StringBuffer sql = new StringBuffer();
            sql.append("select ");
            sql.append("		EID || '' as EID,");
            sql.append("		RESULT_VALUE,");
			sql.append("		OWNER_TABLE");
			sql.append("		from ST_SUCCESSOR_DEFINITIONS");
			sql.append("		WHERE ");
			sql.append("  		ST_PREDECESSOR_DEFINITION_EID = ").append(predecessor).append("");
			sql.append("		and OWNER_TABLE = 'NO_TABLE'");
			sql.append("		and STOP_DATE is null ");
			
			stmt = conn.createStatement();            
            rs = stmt.executeQuery(sql.toString());
            OWLDataFactory factory = manager.getOWLDataFactory();
            OWLClass legacyEvent = factory.getOWLClass(":LegacyEvent", pm);
            OWLClass fieldAlert = factory.getOWLClass(":ServiceFieldAlert", pm);
            manager.addAxiom(csr, factory.getOWLSubClassOfAxiom(fieldAlert, legacyEvent));
            while (rs.next())
            {
            	String eid =  rs.getString("EID");
            	OWLNamedIndividual successor = factory.getOWLNamedIndividual(":" + eid, pm);
            	String resultValue = rs.getString("RESULT_VALUE");
            	if(resultValue != null)
            	{
            		
//            		System.out.println("getFieldAlerts:");
//            		System.out.println("Subject " + subject);
//            		System.out.println("Successor " + successor);
//            		System.out.println("Predecessor " + predecessor);
            		manager.addAxiom(csr, factory.getOWLClassAssertionAxiom(fieldAlert,successor));
            		manager.addAxiom(csr,factory.getOWLObjectPropertyAssertionAxiom(
    						factory.getOWLObjectProperty(":hasLegacyEvent",pm)
    						, factory.getOWLNamedIndividual(":" + predecessor  ,pm),successor));
            		manager.addAxiom(csr,factory.getOWLAnnotationAssertionAxiom(
    	            		successor.getIRI(),
    						factory.getOWLAnnotation(
    								factory.getRDFSLabel()
    								,factory.getOWLLiteral(resultValue))));
            		manager.addAxiom(csr, factory.getOWLObjectPropertyAssertionAxiom(
                			factory.getOWLObjectProperty(":hasServiceFieldAlert", pm), subject, factory.getOWLNamedIndividual(":" + predecessor  ,pm)));
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
	}
	
	public static void getActivityAssignments(Connection conn, OWLOntology csr, OWLOntologyManager manager, PrefixManager pm, String predecessor, OWLNamedIndividual subject)
	{
		Statement stmt = null;
    	ResultSet rs = null;
    	try
    	{
            StringBuffer sql = new StringBuffer();
            sql.append("select ");
            sql.append("		a.EID || '' as EID,");
            sql.append("		a.SERVICE_REQUEST_TYPE_CODE,");
            sql.append("		a.ACTIVITY_CODE,");
            sql.append("		b.RESULT_VALUE,");
			sql.append("		b.OWNER_TABLE");
			sql.append("		from SR_ACTIVITIES_DEFINITIONS a, ST_SUCCESSOR_DEFINITIONS b");
			sql.append("		WHERE ");
			sql.append("  		b.ST_PREDECESSOR_DEFINITION_EID = ").append(predecessor).append("");
			sql.append("		and a.EID = b.OWNER_EID");
			sql.append(" 		and b.OWNER_TABLE = 'SR_ACTIVITIES_DEFINITIONS'");  
			sql.append("		and a.CANCEL_DATE is null and b.STOP_DATE is null ");
			
			stmt = conn.createStatement();            
            rs = stmt.executeQuery(sql.toString());
            OWLDataFactory factory = manager.getOWLDataFactory();
            OWLClass legacyEvent = factory.getOWLClass(":LegacyEvent", pm);
            OWLClass activityAssignment = factory.getOWLClass(":ActivityAssignment", pm);
            manager.addAxiom(csr, factory.getOWLSubClassOfAxiom(activityAssignment, legacyEvent));
            while (rs.next())
            {
            	String eid =  rs.getString("EID");
            	OWLNamedIndividual successor = factory.getOWLNamedIndividual(":" + eid, pm);
            	String serviceCode =  rs.getString("SERVICE_REQUEST_TYPE_CODE");
            	String activityCode =  rs.getString("ACTIVITY_CODE");
            	OWLNamedIndividual activity = factory.getOWLNamedIndividual(":" + serviceCode + "_" + activityCode, pm); 
            	manager.addAxiom(csr, factory.getOWLClassAssertionAxiom(activityAssignment, successor));
            	manager.addAxiom(csr,factory.getOWLObjectPropertyAssertionAxiom(
    						factory.getOWLObjectProperty(":hasActivity",pm)
    						, successor,activity));
            	String resultValue = rs.getString("RESULT_VALUE");
            	if(resultValue != null)
	        	{
            			manager.addAxiom(csr,factory.getOWLObjectPropertyAssertionAxiom(
        						factory.getOWLObjectProperty(":hasOutcome",pm)
        						, successor,factory.getOWLNamedIndividual(":OUTCOME_" + resultValue, pm)));
	        	}
            	manager.addAxiom(csr,factory.getOWLObjectPropertyAssertionAxiom(
						factory.getOWLObjectProperty(":hasLegacyEvent",pm)
						, factory.getOWLNamedIndividual(":" + predecessor  ,pm),successor));
            	manager.addAxiom(csr, factory.getOWLObjectPropertyAssertionAxiom(
            			factory.getOWLObjectProperty(":hasActivityAssignment", pm), subject, factory.getOWLNamedIndividual(":" + predecessor  ,pm)));
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
	}
	
	public static void exportOntology(OutputStreamWriter out, Properties dbConfig, String jurisdiction, OWLOntology complement, String stopDatedSRList)
	{
		Connection conn = null;
		Statement stmt = null;
		try
		{
			Class.forName(dbConfig.getProperty("db-driver-name"));
            conn = DriverManager.getConnection(dbConfig.getProperty("db-url"), dbConfig.getProperty("db-username"), dbConfig.getProperty("db-password"));
            loadPersonnelIDsAndUsernames(conn);
            loadGroupEmails(conn);
            loadUserGeoAssignment(conn);
            StringBuffer sql2 = new StringBuffer();
			sql2.append("select def.SERVICE_REQUEST_TYPE_CODE, code.DESCRIPTION,"); 
			sql2.append(" def.EXTERNAL_DESCRIPTION, def.LOCATION_INTAKE_OPTION_CODE");
			sql2.append(" ,def.VALID_LOCATION_IS_REQD_IND, GroupTasks.GetJurisdictionCode( code.OWNER_CODE ) ");
			sql2.append(" ,StandardTasks.GetDescription('GROUP',GroupTasks.GetJurisdictionCode( code.OWNER_CODE )) ");
			sql2.append(" ,def.DEFAULT_GEO_AREA_CODE, GEO_AREA_IS_REQD_IND,DEFAULT_INTAKE_STATUS_CODE,ALLOWABLE_CREATE_MODULE_CODES,DEFAULT_PRIORITY_CODE,DEFAULT_METHOD_RECEIVED_CODE ");
			sql2.append(" ,code.OWNER_CODE AS CSR_GROUP_CODE ");
			sql2.append(" ,def.DURATIONS_DAYS ");
			sql2.append(" ,def.DUPE_CHECK_METHOD_CODE, def.DUPE_STATUS_LIMIT_CODE, def.DUPE_USE_WHOLE_BLOCK_IND, def.DUPE_ADDRESS_BUFFER, def.DUPE_THRESHOLD_DAYS, code.stop_date ");
			sql2.append(" from sr_definitions def, st_codes code");
			sql2.append(" where def.SERVICE_REQUEST_TYPE_CODE=code.CODE_CODE");
			sql2.append(" and code.TYPE_CODE='SRSRTYPE'");
			sql2.append(" and GroupTasks.GetJurisdictionCode( code.OWNER_CODE ) in (" + jurisdiction + ")");
			//sql2.append(" and (code.stop_date is null or def.SERVICE_REQUEST_TYPE_CODE in("+ stopDatedSRList +") or code.stop_date = '15-Mar-2013')"); //");
			sql2.append(" and def.SERVICE_REQUEST_TYPE_CODE in("+ stopDatedSRList +")"); //");
			sql2.append(" and def.stop_date is null  ");
			sql2.append(" order by def.SERVICE_REQUEST_TYPE_CODE, code.DESCRIPTION");
			stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(sql2.toString());	
			String mdc = "http://www.miamidade.gov/ontology";
			String base = "http://www.miamidade.gov/cirm/legacy";
			String exported = "http://www.miamidade.gov/cirm/legacy/exported";
			DefaultPrefixManager pm = new DefaultPrefixManager(base+ "#");
			pm.setPrefix("mdc:",mdc+"#");
			pm.setPrefix("legacy:",base+"#");
			OWLOntologyManager manager = SynchronizedOWLManager.createOWLOntologyManager();
			OWLDataFactory factory = manager.getOWLDataFactory();
			OWLOntology csr = manager.createOntology(IRI.create(exported));
			manager.applyChange(new AddImport(csr,factory.getOWLImportsDeclaration(IRI.create(mdc))));
			if(!SINGLE_EXPORT_MODE)
			{
				loadGeaArea2LayerMapping(manager, pm);
				getStatuses(conn, csr, manager, pm);
				getIntakeMethods(conn, csr, manager, pm);
				getPriorities(conn, csr, manager, pm);
				getDomainAndRanges(csr, manager, pm);
				getLegacyInterfaces(conn, csr, manager, pm);
				getInterfaceEvents(conn, csr, manager, pm);
				getOutcomes(conn, csr, manager, pm);
			}
			OWLClass serviceCaseClass = factory.getOWLClass(":ServiceCase", pm);
			List<String> allTypes = new ArrayList<String>();
			Set<OWLAxiom> axioms = new HashSet<OWLAxiom>();
			Map<String, List<OWLNamedIndividual>> srGCMap = new HashMap<String, List<OWLNamedIndividual>>();
			OWLClass StreetAddressOnlyCheckRule = factory.getOWLClass(":StreetAddressOnlyCheckRule",pm);
			OWLClass FullAddressCheckRule = factory.getOWLClass(":FullAddressCheckRule",pm);
			while (rs.next())
			{
				OWLNamedIndividual serviceCase = factory.getOWLNamedIndividual(":" + rs.getString(1) ,pm);
				OWLClass asClass = factory.getOWLClass(serviceCase.getIRI());
				allTypes.add(rs.getString(1));
				axioms.add(factory.getOWLSubClassOfAxiom(asClass, serviceCaseClass));
				axioms.add(factory.getOWLClassAssertionAxiom(serviceCaseClass,serviceCase));
				axioms.add(factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty(":hasLegacyCode",pm)
						, serviceCase, factory.getOWLLiteral(rs.getString(1))));
				axioms.add(factory.getOWLAnnotationAssertionAxiom(
						serviceCase.getIRI(),
						factory.getOWLAnnotation(
								factory.getRDFSLabel()
								,factory.getOWLLiteral(rs.getString(2)))));
				if(SET_DISABLED_CREATE)
				{
					axioms.add(factory.getOWLDataPropertyAssertionAxiom(
							factory.getOWLDataProperty(":isDisabledCreate",pm)
							, serviceCase, factory.getOWLLiteral(true)));
				}
//				if(rs.getString("VALID_LOCATION_IS_REQD_IND").equals("N"))
//				{
//					axioms.add(factory.getOWLDataPropertyAssertionAxiom(
//							factory.getOWLDataProperty(":isNoLocationAllowed",pm)
//							, serviceCase, factory.getOWLLiteral(true)));
//				}
//			
				
					
				//duplicate check rules
				String duplicateCheckMethod = rs.getString("DUPE_CHECK_METHOD_CODE");
				if(!duplicateCheckMethod.equals("NODUPECH"))
				{
					OWLNamedIndividual rule = factory.getOWLNamedIndividual(":" + serviceCase.getIRI().getFragment() + "_DUPLICATE_CHECK_RULE", pm);
					axioms.add(factory.getOWLObjectPropertyAssertionAxiom(
							factory.getOWLObjectProperty(":hasDuplicateCheckRule",pm)
							, serviceCase, rule));
				
					if(duplicateCheckMethod.equals("STRTONLY"))
					{
						axioms.add(factory.getOWLClassAssertionAxiom(
								StreetAddressOnlyCheckRule
								, rule));
					}else if(duplicateCheckMethod.equals("STRTADDL"))
					{
						axioms.add(factory.getOWLClassAssertionAxiom(
								FullAddressCheckRule
								, rule));
					}
					String statusLimit = rs.getString("DUPE_STATUS_LIMIT_CODE");
					if(statusLimit != null)
					{
						if(statusLimit.equals("ONOWIP"))
						{
							axioms.add(factory.getOWLObjectPropertyAssertionAxiom(
									factory.getOWLObjectProperty(":hasStatusLimit",pm)
									, rule, factory.getOWLNamedIndividual(":Open", pm)));

						}else if(statusLimit.equals("ALLSTAT"))
						{
							axioms.add(factory.getOWLObjectPropertyAssertionAxiom(
									factory.getOWLObjectProperty(":hasStatusLimit",pm)
									, rule, factory.getOWLNamedIndividual(":AnyStatus", pm)));
						}
					}
					
					String useWholeBlock = rs.getString("DUPE_USE_WHOLE_BLOCK_IND");
					if(!duplicateCheckMethod.equals("STRTADDL") && useWholeBlock != null && useWholeBlock.equals("N")) //if not use whole block, then hasAddressBuffer
					{
						long addressBuffer = rs.getLong("DUPE_ADDRESS_BUFFER");
						axioms.add(factory.getOWLDataPropertyAssertionAxiom(
								factory.getOWLDataProperty(":hasAddressBuffer",pm)
								, rule, factory.getOWLLiteral(addressBuffer + "", OWL2Datatype.XSD_INT)));
						
					}
					
					long thresholdDays = rs.getLong("DUPE_THRESHOLD_DAYS");
					if(!rs.wasNull()) //if not use whole block, then hasAddressBuffer
					{
						
						axioms.add(factory.getOWLDataPropertyAssertionAxiom(
								factory.getOWLDataProperty(":hasThresholdDays",pm)
								, rule, factory.getOWLLiteral(thresholdDays + "", OWL2Datatype.XSD_INT)));
						
					}
					
					
				}
				String desc = rs.getString(3);
				if(desc != null)
					axioms.add(factory.getOWLAnnotationAssertionAxiom(
							serviceCase.getIRI(),
							factory.getOWLAnnotation(
									factory.getRDFSComment()
									,factory.getOWLLiteral(desc))));
				axioms.add(factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty(":hasShowLocation",pm)
						, serviceCase, factory.getOWLLiteral(rs.getString(4))));
				String locationNotRequired = rs.getString(5);
				if("N".equals(locationNotRequired))
				{
					
				axioms.add(factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty(":hasValidLocationNotRequired",pm)
						, serviceCase, factory.getOWLLiteral(true)));
				}
				axioms.add(factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty(":hasJurisdictionCode",pm)
						, serviceCase, factory.getOWLLiteral(rs.getString(6))));
				axioms.add(factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty(":hasJurisdictionDescription",pm)
						, serviceCase, factory.getOWLLiteral(rs.getString(7))));
				float duration =  rs.getFloat("DURATIONS_DAYS");
				if(!rs.wasNull() && duration > 0)
				{
					axioms.add(factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty(":hasDurationDays", pm),serviceCase, 
						factory.getOWLLiteral(duration)
						));
				}
				String geoAreaCode = rs.getString(8);
				if (geoAreaCode != null)
					axioms.add(factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty(":hasGeoAreaCode",pm)
						, serviceCase, factory.getOWLLiteral(geoAreaCode)));
				String geoAreaRequired = rs.getString(9);
				if (geoAreaRequired == null)
					geoAreaRequired = "N";
				axioms.add(factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty(":hasGeoAreaRequired",pm)
						, serviceCase, factory.getOWLLiteral(geoAreaRequired)));
				axioms.add(factory.getOWLObjectPropertyAssertionAxiom(
						factory.getOWLObjectProperty(":hasDefaultStatus",pm)
						, serviceCase, factory.getOWLNamedIndividual(":" + rs.getString("DEFAULT_INTAKE_STATUS_CODE") ,pm)));
				axioms.add(factory.getOWLObjectPropertyAssertionAxiom(
						factory.getOWLObjectProperty(":hasDefaultPriority",pm)
						, serviceCase, factory.getOWLNamedIndividual(":" + rs.getString("DEFAULT_PRIORITY_CODE") ,pm)));
				axioms.add(factory.getOWLObjectPropertyAssertionAxiom(
						factory.getOWLObjectProperty(":hasDefaultIntakeMethod",pm)
						, serviceCase, factory.getOWLNamedIndividual(":" + rs.getString("DEFAULT_METHOD_RECEIVED_CODE") ,pm)));
				if(rs.getString("ALLOWABLE_CREATE_MODULE_CODES") == null)
        			continue;
        		StringBuffer legacyInterface =  new StringBuffer(rs.getString("ALLOWABLE_CREATE_MODULE_CODES"));
        		legacyInterface.deleteCharAt(0).deleteCharAt(legacyInterface.length()-1);
        		for(String e :legacyInterface.toString().split(","))
        		{
        			OWLNamedIndividual i = factory.getOWLNamedIndividual(":" + e , pm);
        			if(csr.containsIndividualInSignature(i.getIRI()))
        				manager.addAxiom(csr, 
            				factory.getOWLObjectPropertyAssertionAxiom(
            						factory.getOWLObjectProperty(":hasLegacyInterface",pm)
            						,serviceCase ,i));
        			String fragment = i.getIRI().getFragment();
        			if(!(fragment.equals("SVCDIR") || fragment.equals("PRIMAPP") || fragment.equals("CSRMOBIL") || fragment.equals("WEBINTAK")))
        				if(DBG)
        					System.out.println(serviceCase.getIRI().getFragment() +"\t"+ i.getIRI().getFragment());
        		}
        		if(!srGCMap.containsKey(rs.getString(14)))
        				srGCMap.put(rs.getString(14), new ArrayList<OWLNamedIndividual>());
        		srGCMap.get(rs.getString(14)).add(serviceCase);
			}
			if(!axioms.isEmpty())
				manager.addAxioms(csr, axioms);
			rs.close(); 
			rs = null;
			stmt.close(); 
			stmt = null;
			for (String i : allTypes )
			{
				getServiceFields(conn, csr, manager, pm, i);
				getActors(conn, csr, manager, pm, i);
				getActivities(conn, csr, manager, pm, i);
				getPredecessors(conn, csr, manager, pm, i);
				getCaseAlerts(conn, csr, manager, pm, i);
				getReferrals(conn, csr, manager, pm, i);
			}
			getProvidedBy(srGCMap, csr, pm);
			getMessageTemplates(conn, csr, manager, pm);
			if(!SINGLE_EXPORT_MODE)
			{
				getMessageVariables(conn, csr, manager, pm);
			}
			//add complement axioms created manually.
			PrefixOWLOntologyFormat format = new OWLXMLOntologyFormat();
			//PrefixOWLOntologyFormat format  =  new OWLFunctionalSyntaxOntologyFormat();
			//format.setDefaultPrefix(pm.getDefaultPrefix());
			format.copyPrefixesFrom(pm);
			manager.saveOntology(csr, format, new WriterDocumentTarget(out));
			System.out.println("Ontology exported. " + csr.getAxiomCount() + " axioms written to disk.");
			//TODO: remove, because legacy ontology will be maintained manually, not by the export tool.
//			OWLOntology legacy = manager.createOntology(IRI.create(base));
//			if(complement != null)
//			{
//				
//				manager.applyChange(new AddImport(legacy,factory.getOWLImportsDeclaration(IRI.create(exported))));
//				outer:
//				for(OWLAxiom axiom: complement.getAxioms())
//				{
//					Set<OWLEntity> entities = axiom.getSignature();
//				
////					if(axiom.getAxiomType().equals(AxiomType.DATA_PROPERTY_DOMAIN)
////						|| axiom.getAxiomType().equals(AxiomType.DATA_PROPERTY_RANGE)
////						|| axiom.getAxiomType().equals(AxiomType.OBJECT_PROPERTY_DOMAIN)
////						||  axiom.getAxiomType().equals(AxiomType.OBJECT_PROPERTY_RANGE))
////					{
////						manager.addAxiom(legacy, axiom);
////						System.out.println("Added complement axiom " + axiom);
////						continue;
////					}
//					if(entities.contains(factory.getOWLObjectProperty(":providedBy",pm)))
//						continue;
//					
//					for(OWLEntity i : entities)
//					{
//						if(i.getIRI().toString().contains(mdc))
//						{
//							manager.addAxiom(legacy, axiom);
//							System.out.println("Added complement axiom " + axiom);
//							continue outer;
//						}
//					}
//				}
//			}
//			File file = new File("c:\\work\\cirmservices\\legacy.owl");
//			OutputStreamWriter out2 = new OutputStreamWriter(new FileOutputStream(file) ,Charset.forName("UTF-8"));
//			manager.saveOntology(legacy, format, new WriterDocumentTarget(out2));
		}
		catch (RuntimeException ex)
		{
			throw ex;
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
		finally
		{
			if (stmt != null) try { stmt.close(); } catch (Throwable t) {}
			if (conn != null) try { conn.close(); } catch (Throwable t) {}
		}
	}
	
	private static void getOutcomes(Connection conn, OWLOntology csr,
			OWLOntologyManager manager, DefaultPrefixManager pm)
	{
		 OWLDataFactory factory = manager.getOWLDataFactory();
		 OWLNamedIndividual closeServiceCase = factory.getOWLNamedIndividual(":CloseServiceCase", pm);
		 Statement stmt = null;
		 ResultSet rs = null;
		 try
		 {
			 stmt = conn.createStatement();
	         String sql2 = "select * from all_codes where type_code = 'SROUTCOM'" 
	         			  + " and stop_date is null";
	         rs = stmt.executeQuery(sql2);
	         OWLClass outcomeClass = factory.getOWLClass(":Outcome", pm);
	         while (rs.next())
	         {
	           	OWLNamedIndividual outcome = factory.getOWLNamedIndividual(":" + URLEncoder.encode( "OUTCOME_" +  rs.getString("CODE_CODE"),"UTF-8") ,pm);
	           	OWLClassAssertionAxiom a = factory.getOWLClassAssertionAxiom(outcomeClass, outcome);
	           	if(!csr.containsAxiom(a))
	          	{
	          		manager.addAxiom(csr, a);
	          		manager.addAxiom(csr, 
	              			factory.getOWLDataPropertyAssertionAxiom(
	              					factory.getOWLDataProperty(":hasLegacyCode",pm), 
	              					outcome, factory.getOWLLiteral(rs.getString("CODE_CODE"))));
	          		manager.addAxiom(csr,factory.getOWLAnnotationAssertionAxiom(
	          				outcome.getIRI(),
	   						factory.getOWLAnnotation(
	   								factory.getRDFSLabel()
	   								,factory.getOWLLiteral(rs.getString("DESCRIPTION")))));
	          		String desc2 = rs.getString("DESCRIPTION2");
	          		if (desc2 != null && desc2.equals("CLOSE"))
	          			manager.addAxiom(csr,      				
	          					factory.getOWLObjectPropertyAssertionAxiom(
	          							factory.getOWLObjectProperty(":hasLegacyEvent",pm), outcome, closeServiceCase));
	          	}
	          }
	          rs.close();
	          stmt.close();
		  } catch(Exception e)
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
	}

	public static void getStatuses(Connection conn, OWLOntology csr, OWLOntologyManager manager, PrefixManager pm)
	{
		Statement stmt = null;
    	ResultSet rs = null;
    	try
    	{
            StringBuffer sql = new StringBuffer();
            sql.append("select code_code, always_public_ind, description, description2, description3, ");
            sql.append(" description4, description5, description6, description7, description8, short_description, order_by ");
            sql.append("       from st_codes def");
            sql.append("       where def.type_code = 'SRSRSTAT' and def.STOP_DATE is null");
            sql.append("       order by def.ORDER_BY");
            stmt = conn.createStatement();            
            rs = stmt.executeQuery(sql.toString());
            OWLDataFactory factory = manager.getOWLDataFactory();
            OWLClass openClass = factory.getOWLClass(":Open", pm);
            OWLClass statusClass = factory.getOWLClass(":Status", pm);
            while (rs.next())
            {
            	String statusCode = rs.getString(1);
            	OWLNamedIndividual status = factory.getOWLNamedIndividual(":" + statusCode , pm); 
            	String desc5 = rs.getString(7);
            	OWLClassAssertionAxiom a = (desc5 != null && "OPENED".equals(desc5))?factory.getOWLClassAssertionAxiom(openClass, status):factory.getOWLClassAssertionAxiom(statusClass, status);
            	manager.addAxiom(csr, a);
            	manager.addAxiom(csr, 
				factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty(":hasLegacyCode",pm)
						, status,factory.getOWLLiteral(statusCode)));
        		manager.addAxiom(csr, 
            			factory.getOWLDataPropertyAssertionAxiom(
            					factory.getOWLDataProperty(":isAlwaysPublic",pm), 
            					status, factory.getOWLLiteral(rs.getString(2))));
        		manager.addAxiom(csr, factory.getOWLAnnotationAssertionAxiom(
						status.getIRI(),
						factory.getOWLAnnotation(
								factory.getRDFSLabel()
								,factory.getOWLLiteral(rs.getString(3)))));
        		String allowableStatuses = rs.getString(4);
        		if(allowableStatuses != null)
            		manager.addAxiom(csr, 
                			factory.getOWLDataPropertyAssertionAxiom(
                					factory.getOWLDataProperty(":hasAllowableStatuses",pm), 
                					status, allowableStatuses));
        		String desc3 = rs.getString(5);
        		if(desc3 != null)
        		manager.addAxiom(csr, 
        				factory.getOWLAnnotationAssertionAxiom(status.getIRI()
        				,
        				factory.getOWLAnnotation(
            					factory.getOWLAnnotationProperty(":description3",pm), 
            					factory.getOWLLiteral(desc3))));
        		String desc4 = rs.getString(6);
        		if(desc4 != null)
    			manager.addAxiom(csr, 
        				factory.getOWLAnnotationAssertionAxiom(status.getIRI()
        				,
        				factory.getOWLAnnotation(
            					factory.getOWLAnnotationProperty(":description4",pm), 
            					factory.getOWLLiteral(desc4))));
        		if(desc5 != null)
    			manager.addAxiom(csr, 
        				factory.getOWLAnnotationAssertionAxiom(status.getIRI()
        				,
        				factory.getOWLAnnotation(
            					factory.getOWLAnnotationProperty(":description5",pm), 
            					factory.getOWLLiteral(desc5))));
        		String desc6 = rs.getString(8);
        		if(desc6 != null)
    			manager.addAxiom(csr, 
        				factory.getOWLAnnotationAssertionAxiom(status.getIRI()
        				,
        				factory.getOWLAnnotation(
            					factory.getOWLAnnotationProperty(":description6",pm), 
            					factory.getOWLLiteral(desc6))));	
        		manager.addAxiom(csr, 
            			factory.getOWLDataPropertyAssertionAxiom(
            					factory.getOWLDataProperty(":hasOrderBy",pm), 
            					status, factory.getOWLLiteral(rs.getFloat("ORDER_BY"))));
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

	public static void getActivities(Connection conn, OWLOntology csr, OWLOntologyManager manager, PrefixManager pm,
			String serviceCode)
	{
		Statement stmt = null;
    	Statement stmt2 = null;
    	ResultSet rs = null;
    	ResultSet rs2 = null;
        try
        {
            StringBuffer sql = new StringBuffer();
            sql.append("select service_request_type_code, activity_code, ");
            sql.append("       stcodestasks.getdescription('SRACTVTY', ACTIVITY_CODE) as activity_description, ");
            sql.append("       default_outcome_code, suspense_period_days, ");
            sql.append("       default_occur_days, automatically_create_ind, order_by, ");
            sql.append("       business_codes, allowable_outcome_codes,follow_on_activity_code, ");
            sql.append("       eids_that_can_be_assigned, auto_assign_code ");
            sql.append("  from sr_activities_definitions ");
            sql.append(" where service_request_type_code = '" + serviceCode + "' ");
            sql.append("   and cancel_date is null ");
            sql.append("order by order_by ");
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql.toString()); 
            OWLDataFactory factory = manager.getOWLDataFactory();
            Set<OWLAxiom> axioms = new HashSet<OWLAxiom>();
            OWLNamedIndividual type = factory.getOWLNamedIndividual(":"+ serviceCode, pm);
            OWLClass activityType = factory.getOWLClass(":Activity", pm);
            OWLClass legacyEvent = factory.getOWLClass(":LegacyEvent", pm);
            OWLClass statusChange = factory.getOWLClass(":StatusChange", pm);
            OWLNamedIndividual closeServiceCase = factory.getOWLNamedIndividual(":CloseServiceCase", pm);
            manager.addAxiom(csr, factory.getOWLSubClassOfAxiom(statusChange, legacyEvent));
            manager.addAxiom(csr, factory.getOWLClassAssertionAxiom(statusChange, closeServiceCase));
            manager.addAxiom(csr, factory.getOWLObjectPropertyAssertionAxiom(
            		factory.getOWLObjectProperty(":hasStatus", pm), closeServiceCase, factory.getOWLNamedIndividual(":C-CLOSED", pm)));
            
            while (rs.next())
            {
            	String activityCode =  rs.getString("ACTIVITY_CODE");
            	String allowableOutcomes =  rs.getString("ALLOWABLE_OUTCOME_CODES");
            	OWLNamedIndividual activity = factory.getOWLNamedIndividual(":" + serviceCode + "_" + activityCode, pm); 
            	String overdueActivityCode =  rs.getString("FOLLOW_ON_ACTIVITY_CODE");
            	if(overdueActivityCode != null)
            	{
            		OWLNamedIndividual overdueActivity = factory.getOWLNamedIndividual(":" + serviceCode + "_" + overdueActivityCode, pm);
            		axioms.add(factory.getOWLObjectPropertyAssertionAxiom(factory.getOWLObjectProperty(":hasOverdueActivity",pm), activity, overdueActivity));
            	}
            	axioms.add(factory.getOWLClassAssertionAxiom(activityType, activity));
	            axioms.add(factory.getOWLDataPropertyAssertionAxiom(
        						factory.getOWLDataProperty(":hasLegacyCode",pm)
        						, activity,factory.getOWLLiteral(activityCode)));
	            axioms.add(factory.getOWLAnnotationAssertionAxiom(
	            		activity.getIRI(),
						factory.getOWLAnnotation(
								factory.getRDFSLabel()
								,factory.getOWLLiteral(rs.getString("ACTIVITY_DESCRIPTION")))));
	            String defaultOutcome = rs.getString("DEFAULT_OUTCOME_CODE");
	            if ( defaultOutcome != null)
		            axioms.add(factory.getOWLObjectPropertyAssertionAxiom(
							factory.getOWLObjectProperty(":hasDefaultOutcome",pm)
							, activity, factory.getOWLNamedIndividual(":OUTCOME_" + defaultOutcome,pm)));
	            axioms.add(factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty(":hasSuspenseDays",pm)
						, activity, rs.getFloat("SUSPENSE_PERIOD_DAYS")));
	            axioms.add(factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty(":hasOccurDays",pm)
						, activity, rs.getFloat("DEFAULT_OCCUR_DAYS")));
	            axioms.add(factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty(":isAutoCreate",pm)
						, activity, rs.getString("AUTOMATICALLY_CREATE_IND")));
	            axioms.add(factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty(":hasOrderBy",pm)
						, activity, rs.getFloat("ORDER_BY")));
               String bCodes = rs.getString("BUSINESS_CODES");
	           if(bCodes != null)
	           axioms.add(factory.getOWLDataPropertyAssertionAxiom(
						factory.getOWLDataProperty(":hasBusinessCodes",pm)
						, activity, bCodes));
               
	           axioms.add(factory.getOWLObjectPropertyAssertionAxiom(factory.getOWLObjectProperty(":hasActivity",pm), type, activity));
	           if(!axioms.isEmpty())
               		manager.addAxioms(csr, axioms);
	           if(allowableOutcomes == null)
	           {
	        	   
	        	   OWLNamedIndividual outcomeComplete = factory.getOWLNamedIndividual(":OUTCOME_COMPLETE"  ,pm);
	        	   manager.addAxiom(csr, factory.getOWLObjectPropertyAssertionAxiom(factory.getOWLObjectProperty(":hasAllowableOutcome",pm), activity, outcomeComplete));
	        	   continue;
	        	   
	           }
	           StringBuffer inClause = new StringBuffer(allowableOutcomes);
               inClause.deleteCharAt(0).deleteCharAt(inClause.length()-1);
               stmt2 = conn.createStatement();
               String sql2 = "select * from all_codes where type_code = 'SROUTCOM'" 
               			  + " and code_code in('" +inClause.toString().replaceAll(",", "','")+"')   and stop_date is null";
               rs2 = stmt2.executeQuery(sql2);
               OWLClass outcomeClass = factory.getOWLClass(":Outcome", pm);
               while (rs2.next())
               {
                 	OWLNamedIndividual outcome = factory.getOWLNamedIndividual(":OUTCOME_" +  rs2.getString("CODE_CODE") ,pm);
                 	OWLClassAssertionAxiom a = factory.getOWLClassAssertionAxiom(outcomeClass, outcome);
                 	if(!csr.containsAxiom(a))
                	{
                		manager.addAxiom(csr, a);
                		manager.addAxiom(csr, 
                    			factory.getOWLDataPropertyAssertionAxiom(
                    					factory.getOWLDataProperty(":hasLegacyCode",pm), 
                    					outcome, factory.getOWLLiteral(rs2.getString("CODE_CODE"))));
                		manager.addAxiom(csr,factory.getOWLAnnotationAssertionAxiom(
                				outcome.getIRI(),
         						factory.getOWLAnnotation(
         								factory.getRDFSLabel()
         								,factory.getOWLLiteral(rs2.getString("DESCRIPTION")))));
                		String desc2 = rs2.getString("DESCRIPTION2");
                		if (desc2 != null && desc2.equals("CLOSE"))
                			manager.addAxiom(csr,      				
                					factory.getOWLObjectPropertyAssertionAxiom(
                							factory.getOWLObjectProperty(":hasLegacyEvent",pm), outcome, closeServiceCase));
                	}
                	manager.addAxiom(csr, factory.getOWLObjectPropertyAssertionAxiom(factory.getOWLObjectProperty(":hasAllowableOutcome",pm), activity, outcome));
                }
                rs2.close();
                stmt2.close();
              String autoAssign =  rs.getString("auto_assign_code");
              if(autoAssign != null)
              {
            	  axioms.add(factory.getOWLDataPropertyAssertionAxiom(
  						factory.getOWLDataProperty(":isAutoAssign",pm)
  						, activity, true));
	              setActivityAutoAssignRule(csr, manager, pm ,rs, type, activity, autoAssign);
              } 
            }
        }
        catch(Exception e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
        	if (rs2 != null)
        		try { rs2.close(); } catch (Throwable t) { }        	
        	if (rs != null)
        		try { rs.close(); } catch (Throwable t) { }        	
        	if (stmt2 != null)
        		try { stmt2.close(); } catch (Throwable t) { }
        	if (stmt != null)
        		try { stmt.close(); } catch (Throwable t) { }
        }
	}

	private static void setActivityAutoAssignRule(OWLOntology csr, OWLOntologyManager manager, PrefixManager pm, ResultSet rs, OWLNamedIndividual type, OWLNamedIndividual activity,
			String autoAssign) throws SQLException
	{
		//"legacy:hasAssignmentRule"
		//"#AssignActivityToCaseCreator"
		// "#AssignActivityFromGeoAttribute")
		//"#AssignActivityToUserRule"
		/**
		 * direct assignment to user 
		 */
		OWLDataFactory df = manager.getOWLDataFactory();
		String activityCode =  activity.getIRI().getFragment();
		if (autoAssign.equals("USERAUTO"))
		{
			
			if (rs.getString("eids_that_can_be_assigned") != null
					&& rs.getString("eids_that_can_be_assigned").length() > 1)
			{
				String[] eids = rs.getString("eids_that_can_be_assigned").split(",");
				int i = 0;
				for (String eid : eids)
				{
					if(eid.equals(""))
						continue;
					String username = getUsername(eid);
					OWLNamedIndividual assignmentRule = df.getOWLNamedIndividual( "legacy:" + activityCode + "_USERASSIGNMENT" + i, pm);
					OWLClassAssertionAxiom owlClassAssertionAxiom = df.getOWLClassAssertionAxiom(df.getOWLClass("legacy:AssignActivityToUserRule", pm), assignmentRule);
					OWLDataPropertyAssertionAxiom owlDataPropertyAssertionAxiom = df.getOWLDataPropertyAssertionAxiom(df.getOWLDataProperty("mdc:hasUsername", pm),
							assignmentRule, 
							username==null?eid:username);
					OWLObjectPropertyAssertionAxiom owlObjectPropertyAssertionAxiom = df.getOWLObjectPropertyAssertionAxiom( 
							df.getOWLObjectProperty("legacy:hasAssignmentRule", pm), activity, assignmentRule);
					if(username != null)
					{
						manager.addAxiom(csr, owlClassAssertionAxiom);
						manager.addAxiom(csr,
								owlDataPropertyAssertionAxiom);
						manager.addAxiom(csr, owlObjectPropertyAssertionAxiom);
					}else
					{
						
						if(DBG)
							{
								System.out.println("Cannot resolve user with eid: " + eid + " for assignment to activity " + activity );
								System.out.println("WARNING -- Cannot resolve user with eid: " + eid + " for assignment to activity " + activity);
								System.out.println("Would be axioms are below with eid instead of a known username");
							}
		        		System.out.println("---------------------------------------------------------------");
		        		System.out.println(owlClassAssertionAxiom);
		        		if(groupEmails.containsKey(eid))
		        		{
		        			System.out.println(df.getOWLDataPropertyAssertionAxiom(df.getOWLDataProperty("mdc:hasUsername", pm),
									assignmentRule, 
									groupEmails.get(eid)));
		        		}else
		        		{
		        			System.out.println(owlDataPropertyAssertionAxiom);
		        		}
		        		System.out.println(owlObjectPropertyAssertionAxiom);
		        		System.out.println("---------------------------------------------------------------");
					}
					i++;
				}
			}
		} 
		else if(autoAssign.equals("PGEOAREA") || autoAssign.equals("STAFFGEO"))
		{
			setGeoBasedAssignmentRule(csr, manager, pm, type, activity, autoAssign);
		}
		else if(autoAssign.equals("SRCREATR"))
		{
			OWLNamedIndividual assignmentRule = df.getOWLNamedIndividual( "legacy:AssignActivityToCaseCreator", pm);
			manager.addAxiom(csr, df.getOWLObjectPropertyAssertionAxiom( 
					df.getOWLObjectProperty("legacy:hasAssignmentRule", pm), activity, assignmentRule));
		}
	}
	
	private static void setGeoBasedAssignmentRule(OWLOntology csr, OWLOntologyManager manager, PrefixManager pm, OWLNamedIndividual type, OWLNamedIndividual activity, String autoAssign)
	{
		String typeCode = type.getIRI().getFragment();
		OWLDataFactory df = manager.getOWLDataFactory();
		if(!userGeoAssignment.containsKey(typeCode))
		{
			String activityFragment = activity.getIRI().getFragment();
			typeCode = activityFragment.substring(activityFragment.indexOf('_') + 1);
		}
		if(!userGeoAssignment.containsKey(typeCode))
		{
			if(DBG)
				System.out.println("Type code " + type + " / " + activity + " not defined in GEO ASSIGNMENTS");
			return;
		}
		OWLNamedIndividual assignmentSpec = df.getOWLNamedIndividual("legacy:" + typeCode + "_GEOASSIGNMENT", pm);
		manager.addAxiom(csr, df.getOWLClassAssertionAxiom(df.getOWLClass("legacy:AssignActivityFromGeoAttribute", pm), assignmentSpec));
		int i = 0;
		for(Map<String, String> props : userGeoAssignment.get(typeCode))
		{
		
        	String eid = props.get("EID");
        	String geoArea = props.get("GEO_AREA");
        	String geoValues = props.get("GEO_VALUES");
        	String username = getUsername(eid);
        	String expression = null;
        	        	Map<OWLNamedIndividual, String> layerMapping = geaArea2LayerMapping.get(geoArea);
        	if( layerMapping == null )
        	{
        		if(DBG)
        			System.out.println("No layer Mapping for geoArea:"  + geoArea + " cannot create geo based assignment for" + type + " / " + activity + ". Add mapping to export tool.");
        		continue;
        	}
        	if (geoValues == null)
        	{
        		expression = "NOT NULL";
        	}else
        	{
        		StringBuffer values = new StringBuffer(geoValues);
        		values.deleteCharAt(0).deleteCharAt(values.length()-1);
        		Json v = Json.array();
        		for(String e :values.toString().split(","))
        		{
        			v.add(e);
        		}
        		expression = Json.object().set("oneOf", v).toString();
        	}
        	OWLNamedIndividual assignmentRule = df.getOWLNamedIndividual("legacy:" + typeCode + "_GEORULE" + i, pm);
        	OWLClassAssertionAxiom owlClassAssertionAxiom = df.getOWLClassAssertionAxiom(df.getOWLClass("legacy:GeoBasedUserAssignment", pm), assignmentRule);
			OWLNamedIndividual layer = layerMapping.keySet().iterator().next();
			OWLObjectPropertyAssertionAxiom owlObjectPropertyAssertionAxiom = df.getOWLObjectPropertyAssertionAxiom(df.getOWLObjectProperty("mdc:hasGisLayer", pm),
					assignmentRule, 
					layer);
			OWLDataPropertyAssertionAxiom owlDataPropertyAssertionAxiom = df.getOWLDataPropertyAssertionAxiom(df.getOWLDataProperty("mdc:hasValue", pm),
					assignmentRule, 
					expression);
			OWLDataPropertyAssertionAxiom owlDataPropertyAssertionAxiom2 = df.getOWLDataPropertyAssertionAxiom(df.getOWLDataProperty("mdc:hasName", pm),
					assignmentRule, 
					layerMapping.get(layer));
			OWLDataPropertyAssertionAxiom owlDataPropertyAssertionAxiom3 = df.getOWLDataPropertyAssertionAxiom(df.getOWLDataProperty("mdc:hasUsername", pm),
					assignmentRule, 
					username==null?eid:username);
			OWLObjectPropertyAssertionAxiom owlObjectPropertyAssertionAxiom2 = df.getOWLObjectPropertyAssertionAxiom( 
					df.getOWLObjectProperty("legacy:hasAssignmentRule", pm), assignmentSpec, assignmentRule);
			if(username == null)
        	{
        		if(DBG)
        			{
        				System.out.println("WARNING -- Cannot create geo based assignment for type:"  + typeCode + " eid: " + eid+ " cannote be resolve to a username");
        			    System.out.println("Would be axioms are below with eid instead of a known username");
        			}
        		System.out.println("---------------------------------------------------------------");
        		System.out.println(owlClassAssertionAxiom);
        		System.out.println(owlDataPropertyAssertionAxiom);
        		System.out.println(owlDataPropertyAssertionAxiom2);
        		if(groupEmails.containsKey(eid))
        		{
        			System.out.println(df.getOWLDataPropertyAssertionAxiom(df.getOWLDataProperty("mdc:hasUsername", pm),
							assignmentRule, 
							groupEmails.get(eid)));
        		}else
        		{
        			System.out.println(owlDataPropertyAssertionAxiom3);
        		}
        		System.out.println(owlObjectPropertyAssertionAxiom);
        		System.out.println(owlObjectPropertyAssertionAxiom2);
        		System.out.println("---------------------------------------------------------------");
        		continue;
        	}
			manager.addAxiom(csr, owlClassAssertionAxiom);
			manager.addAxiom(csr,
					owlObjectPropertyAssertionAxiom);
			
			manager.addAxiom(csr,
					owlDataPropertyAssertionAxiom);
			
			manager.addAxiom(csr,
					owlDataPropertyAssertionAxiom2);
	    	
			manager.addAxiom(csr,
						owlDataPropertyAssertionAxiom3);
			manager.addAxiom(csr, owlObjectPropertyAssertionAxiom2);
			
           	i++;
		}
		
		manager.addAxiom(csr, df.getOWLObjectPropertyAssertionAxiom( 
				df.getOWLObjectProperty("legacy:hasAssignmentRule", pm), activity, assignmentSpec));
		
		
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

	public static void getActors(Connection conn, OWLOntology csr, OWLOntologyManager manager, PrefixManager pm,
			String serviceCode)
	{
		Statement stmt = null;
    	ResultSet rs = null;
    	try
    	{
            StringBuffer sql = new StringBuffer();
            sql.append("select def.participant_type_code, stcodestasks.getdescription('SRPARTIC', def.participant_type_code) as PARTICIPANT_DESCRIPTION, def.entity_table, def.business_codes,def.AUTOMATICALLY_CREATE_IND, def.ORDER_BY ");
            sql.append("       from sr_participant_definitions def");
            sql.append("       where def.SERVICE_REQUEST_TYPE_CODE='" + serviceCode + "'");
            sql.append("       and def.STOP_DATE is null");
            sql.append("       order by def.ORDER_BY");
            stmt = conn.createStatement();            
            rs = stmt.executeQuery(sql.toString());
            OWLDataFactory factory = manager.getOWLDataFactory();
            OWLClass actorClass = factory.getOWLClass(":ServiceActor", pm);
            OWLNamedIndividual type = factory.getOWLNamedIndividual(":"+ serviceCode, pm);
            while (rs.next())
            {
            	String aTypeCode = rs.getString(1);
            	OWLNamedIndividual actor = factory.getOWLNamedIndividual(":" + aTypeCode , pm); 
            	OWLClassAssertionAxiom a = factory.getOWLClassAssertionAxiom(actorClass, actor);
            	if(!csr.containsAxiom(a))
            	{
            		manager.addAxiom(csr, a);
            		manager.addAxiom(csr, 
                			factory.getOWLDataPropertyAssertionAxiom(
                					factory.getOWLDataProperty(":hasLegacyCode",pm), 
                					actor, factory.getOWLLiteral(aTypeCode)));
            		manager.addAxiom(csr,factory.getOWLAnnotationAssertionAxiom(
            				actor.getIRI(),
     						factory.getOWLAnnotation(
     								factory.getRDFSLabel()
     								,factory.getOWLLiteral(rs.getString("PARTICIPANT_DESCRIPTION")))));
            		manager.addAxiom(csr, 
                				factory.getOWLAnnotationAssertionAxiom(actor.getIRI()
                				,
                				factory.getOWLAnnotation(
                    					factory.getOWLAnnotationProperty(":participantEntityTable",pm), 
                    					factory.getOWLLiteral(rs.getString(3)))));
//            		manager.addAxiom(csr, factory.getOWLDataPropertyAssertionAxiom(
//       						factory.getOWLDataProperty(":isAutoCreate",pm)
//       						, actor, rs.getString("AUTOMATICALLY_CREATE_IND")));
//            		manager.addAxiom(csr, factory.getOWLDataPropertyAssertionAxiom(
//      						factory.getOWLDataProperty(":hasOrderBy",pm)
//      						, actor, rs.getFloat("ORDER_BY")));
//            		String codes = rs.getString(4);
//            		if(codes != null)
//            		manager.addAxiom(csr, 
//                			factory.getOWLDataPropertyAssertionAxiom(
//                					factory.getOWLDataProperty(":hasBusinessCodes",pm), 
//                					actor, factory.getOWLLiteral(codes)));
            	}
            	manager.addAxiom(csr, factory.getOWLObjectPropertyAssertionAxiom(factory.getOWLObjectProperty(":hasServiceActor",pm), type, actor));
            	String autoCreate = rs.getString("AUTOMATICALLY_CREATE_IND");
            	if( autoCreate != null && "Y".equals(autoCreate))
            	{
            		manager.addAxiom(csr, factory.getOWLObjectPropertyAssertionAxiom(factory.getOWLObjectProperty(":hasAutoServiceActor",pm), type, actor));
            	}
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

	public static void getDomainAndRanges(OWLOntology csr, OWLOntologyManager manager, PrefixManager pm)
	{
		OWLDataFactory factory = manager.getOWLDataFactory();
		OWLDataProperty isDisabled = factory.getOWLDataProperty(":isDisabled",pm);
		OWLDataProperty isDisabledCreate = factory.getOWLDataProperty(":isDisabledCreate",pm);
		//OWLDataProperty isNoLocationAllowed = factory.getOWLDataProperty(":isNoLocationAllowed",pm);
		OWLDataProperty hasVariableName = factory.getOWLDataProperty(":hasVariableName",pm);
		OWLObjectProperty hasServiceCaseAlert = factory.getOWLObjectProperty(":hasServiceCaseAlert",pm);
		OWLObjectProperty hasServiceAnswer = factory.getOWLObjectProperty(":hasServiceAnswer",pm);
		OWLObjectProperty hasDefaultPriority = factory.getOWLObjectProperty(":hasDefaultPriority",pm);
		OWLObjectProperty hasActivity = factory.getOWLObjectProperty(":hasActivity",pm);
		OWLObjectProperty hasOutcome = factory.getOWLObjectProperty(":hasOutcome",pm);
		OWLObjectProperty hasDefaultIntakeMethod = factory.getOWLObjectProperty(":hasDefaultIntakeMethod",pm);
		OWLObjectProperty hasPriority = factory.getOWLObjectProperty(":hasPriority",pm);
		OWLObjectProperty hasIntakeMethod = factory.getOWLObjectProperty(":hasIntakeMethod",pm);
		OWLObjectProperty hasServiceActivity = factory.getOWLObjectProperty(":hasServiceActivity",pm);
		OWLDataProperty hasLegacyCode = factory.getOWLDataProperty(":hasLegacyCode",pm);
		OWLDataProperty hasAnswerValue = factory.getOWLDataProperty(":hasAnswerValue",pm);
		OWLObjectProperty hasAnswerObject = factory.getOWLObjectProperty(":hasAnswerObject",pm);
		OWLDataProperty isExternal = factory.getOWLDataProperty(":isExternal",pm);
		OWLDataProperty hasBody = factory.getOWLDataProperty(":hasBody",pm);
		OWLDataProperty hasLegacyBody = factory.getOWLDataProperty(":hasLegacyBody",pm);
		OWLObjectProperty hasServiceField = factory.getOWLObjectProperty(":hasServiceField",pm);
		OWLObjectProperty hasOverdueActivity = factory.getOWLObjectProperty(":hasOverdueActivity",pm);
		OWLObjectProperty hasDuplicateCheckRule = factory.getOWLObjectProperty(":hasDuplicateCheckRule",pm);
		OWLObjectProperty hasServiceFieldAlert = factory.getOWLObjectProperty(":hasServiceFieldAlert",pm);
		OWLObjectProperty hasActivityAssignment = factory.getOWLObjectProperty(":hasActivityAssignment",pm);
		OWLClass StatusChange = factory.getOWLClass(":StatusChange",pm);
		OWLClass InterfaceEvent = factory.getOWLClass(":InterfaceEvent",pm);
		OWLClass LegacyInterface = factory.getOWLClass(":LegacyInterface",pm);
		OWLClass ServiceCase = factory.getOWLClass(":ServiceCase",pm);
		OWLClass ServiceQuestion = factory.getOWLClass(":ServiceQuestion",pm);
		OWLClass Activity = factory.getOWLClass(":Activity",pm);
		OWLClass Outcome = factory.getOWLClass(":Outcome",pm);
		OWLClass ServiceActivity = factory.getOWLClass(":ServiceActivity",pm);
		OWLClass ServiceNote = factory.getOWLClass(":ServiceNote",pm);
		OWLClass ServiceActor = factory.getOWLClass(":ServiceActor",pm);
		OWLClass ServiceAnswer = factory.getOWLClass(":ServiceAnswer",pm);
		OWLClass ChoiceValueList = factory.getOWLClass(":ChoiceValueList",pm);
		OWLClass ChoiceValue = factory.getOWLClass(":ChoiceValue",pm);
		OWLClass QuestionTrigger = factory.getOWLClass(":QuestionTrigger",pm);
		OWLClass ActivityTrigger = factory.getOWLClass(":ActivityTrigger",pm);
		OWLClass ServiceField = factory.getOWLClass(":ServiceField",pm);
		OWLClass ServiceCaseActor = factory.getOWLClass(":ServiceCaseActor",pm);
		OWLClass Status = factory.getOWLClass(":Status",pm);
		OWLClass AnyStatus = factory.getOWLClass(":AnyStatus",pm);
		OWLClass Open = factory.getOWLClass(":Open",pm);
		OWLClass MessageVariable = factory.getOWLClass(":MessageVariable",pm);
		OWLClass MessageTemplate = factory.getOWLClass(":MessageTemplate",pm);
		OWLClass StreetAddressOnlyCheckRule = factory.getOWLClass(":StreetAddressOnlyCheckRule",pm);
		OWLClass FullAddressCheckRule = factory.getOWLClass(":FullAddressCheckRule",pm);
		OWLDataProperty hasDataType = factory.getOWLDataProperty(":hasDataType",pm);
		OWLDataProperty hasShowLocation = factory.getOWLDataProperty(":hasShowLocation",pm);
		OWLDataProperty hasValidLocationNotRequired = factory.getOWLDataProperty(":hasValidLocationNotRequired",pm);
		OWLDataProperty hasJurisdictionCode = factory.getOWLDataProperty(":hasJurisdictionCode",pm);
		OWLDataProperty hasJurisdictionDescription = factory.getOWLDataProperty(":hasJurisdictionDescription",pm);
		OWLDataProperty hasGeoAreaCode = factory.getOWLDataProperty(":hasGeoAreaCode",pm);
		OWLObjectProperty hasDefaultStatus = factory.getOWLObjectProperty(":hasDefaultStatus",pm);
		OWLObjectProperty hasServiceCaseActor = factory.getOWLObjectProperty(":hasServiceCaseActor",pm);
		OWLDataProperty hasGeoAreaRequired = factory.getOWLDataProperty(":hasGeoAreaRequired",pm);
		OWLDataProperty hasOrderBy = factory.getOWLDataProperty(":hasOrderBy",pm);
		OWLObjectProperty hasStatus = factory.getOWLObjectProperty(":hasStatus",pm);
		OWLDataProperty hasDueDate = factory.getOWLDataProperty(":hasDueDate",pm);
		OWLDataProperty hasUpdatedDate = factory.getOWLDataProperty(":hasUpdatedDate",pm);
		OWLObjectProperty hasChoiceValue = factory.getOWLObjectProperty(":hasChoiceValue",pm);
		OWLObjectProperty hasServiceActor = factory.getOWLObjectProperty(":hasServiceActor",pm);
		OWLObjectProperty hasAutoServiceActor = factory.getOWLObjectProperty(":hasAutoServiceActor",pm);
		OWLDataProperty hasCompletedDate = factory.getOWLDataProperty(":hasCompletedDate",pm);
		OWLDataProperty hasCompletedTimestamp = factory.getOWLDataProperty(":hasCompletedTimestamp",pm);
		OWLDataProperty hasDetails = factory.getOWLDataProperty(":hasDetails",pm);
		OWLDataProperty isAssignedTo = factory.getOWLDataProperty(":isAssignedTo",pm);
		OWLObjectProperty hasLegacyInterface = factory.getOWLObjectProperty("hasLegacyInterface" ,pm);
		OWLObjectProperty hasAllowableEvent = factory.getOWLObjectProperty("hasAllowableEvent" ,pm);
		OWLClass Priority = factory.getOWLClass(":Priority", pm);
		OWLClass IntakeMethod = factory.getOWLClass(":IntakeMethod", pm);
		OWLClass ServiceCaseAlert = factory.getOWLClass(":ServiceCaseAlert", pm);
		OWLClass ServiceFieldAlert = factory.getOWLClass(":ServiceFieldAlert", pm);
		OWLDataProperty hasAddressBuffer = factory.getOWLDataProperty(":hasAddressBuffer",pm);
		OWLDataProperty hasThresholdDays = factory.getOWLDataProperty(":hasThresholdDays",pm);
		OWLObjectProperty hasStatusLimit = factory.getOWLObjectProperty(":hasStatusLimit",pm);
		OWLObjectProperty hasServiceCase = factory.getOWLObjectProperty(":hasServiceCase",pm);
		OWLClass ServiceCaseOutcomeTrigger = factory.getOWLClass(":ServiceCaseOutcomeTrigger",pm);
		OWLClass CreateServiceCase = factory.getOWLClass(":CreateServiceCase",pm);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(isDisabledCreate)
		);
//		manager.addAxiom(
//				csr, factory.getOWLDeclarationAxiom(isNoLocationAllowed)
//		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasServiceCase)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(ServiceCaseOutcomeTrigger)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(CreateServiceCase)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(ActivityTrigger)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(ServiceFieldAlert)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(StatusChange)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasServiceFieldAlert)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasActivityAssignment)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasBody)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasLegacyBody)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasAddressBuffer)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasThresholdDays)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasStatusLimit)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasDuplicateCheckRule)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(StreetAddressOnlyCheckRule)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(FullAddressCheckRule)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(Open)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasAutoServiceActor)
		);
		manager.addAxiom(
				csr, factory.getOWLSubObjectPropertyOfAxiom(hasAutoServiceActor, hasServiceActor)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(AnyStatus)
		);
		manager.addAxiom(
				csr, factory.getOWLSubClassOfAxiom(StreetAddressOnlyCheckRule, factory.getOWLClass("mdc:SoftwareRule", pm))
		);
		manager.addAxiom(
				csr, factory.getOWLSubClassOfAxiom(FullAddressCheckRule, factory.getOWLClass("mdc:SoftwareRule", pm))
		);
		manager.addAxiom(
				csr, factory.getOWLSubClassOfAxiom(Open, Status)
		);
		manager.addAxiom(
				csr, factory.getOWLClassAssertionAxiom(factory.getOWLClass("mdc:OWLClass", pm), factory.getOWLNamedIndividual(Open.getIRI()))
		);
		manager.addAxiom(
				csr, factory.getOWLClassAssertionAxiom(factory.getOWLClass("mdc:OWLClass", pm), factory.getOWLNamedIndividual(Status.getIRI()))
		);
		manager.addAxiom(
				csr, factory.getOWLClassAssertionAxiom(factory.getOWLClass("mdc:OWLClass", pm), factory.getOWLNamedIndividual(AnyStatus.getIRI()))
		);
		manager.addAxiom(
				csr, factory.getOWLSubClassOfAxiom(Open, Status)
		);
		manager.addAxiom(
				csr, factory.getOWLEquivalentClassesAxiom(Status, AnyStatus)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(isDisabled)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasActivity)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasOutcome)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(MessageTemplate)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(MessageVariable)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(ServiceCaseAlert)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasServiceCaseAlert)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasDefaultPriority)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasDefaultIntakeMethod)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasPriority)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasIntakeMethod)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(isExternal)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasAllowableEvent)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasLegacyInterface)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasDetails)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(isAssignedTo)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasCompletedDate)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasCompletedTimestamp)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasDueDate)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasOverdueActivity)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(ChoiceValue)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(ChoiceValueList)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasChoiceValue)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(ServiceActivity)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(ServiceCaseActor)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasServiceActivity)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasServiceCaseActor)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasServiceActor)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasDefaultStatus)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasUpdatedDate)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasGeoAreaRequired)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasOrderBy)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasStatus)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasServiceAnswer)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasLegacyCode)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(ServiceCase)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(ServiceQuestion)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(Activity)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(Outcome)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(ServiceActor)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasAnswerValue)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasAnswerObject)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(QuestionTrigger)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(ServiceAnswer)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasServiceField)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(ServiceField)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasDataType)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasShowLocation)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasValidLocationNotRequired)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasJurisdictionCode)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasJurisdictionDescription)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(hasGeoAreaCode)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(Status)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(InterfaceEvent)
		);
		manager.addAxiom(
				csr, factory.getOWLDeclarationAxiom(LegacyInterface)
		);
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyDomainAxiom(hasLegacyCode
						, factory.getOWLObjectUnionOf(ServiceCase
								,ServiceQuestion
								,ServiceNote
								,Activity
								,Outcome
								,ServiceActor
								,ChoiceValue
								,InterfaceEvent
								,LegacyInterface
								,MessageVariable
								,MessageTemplate
								)));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyRangeAxiom(hasLegacyCode
						, factory.getOWLDatatype(OWL2Datatype.XSD_STRING.getIRI())));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyDomainAxiom(hasServiceAnswer
						, ServiceCase));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyRangeAxiom(hasServiceAnswer
						, ServiceAnswer));
//		manager.addAxiom(csr, 
//				factory.getOWLObjectPropertyDomainAxiom(hasServiceActivity
//						, ?));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyRangeAxiom(hasServiceActivity
						, ServiceActivity));
		
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyDomainAxiom(hasAnswerValue
						, factory.getOWLObjectUnionOf(
								 ServiceAnswer
								,QuestionTrigger
								)));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyDomainAxiom(hasAnswerObject
						, factory.getOWLObjectUnionOf(
								 ServiceAnswer
								,QuestionTrigger
								)));
		
//		manager.addAxiom(csr, 
//				factory.getOWLDataPropertyRangeAxiom(hasAnswerValue
//						, factory.getOWLDatatype(OWL2Datatype.XSD_STRING.getIRI())));
		
//		manager.addAxiom(csr, 
//				factory.getOWLDataPropertyDomainAxiom(isDisabled
//						, ServiceField));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyRangeAxiom(isDisabled
						, factory.getOWLDatatype(OWL2Datatype.XSD_BOOLEAN.getIRI())));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyRangeAxiom(isDisabledCreate
						, factory.getOWLDatatype(OWL2Datatype.XSD_BOOLEAN.getIRI())));
//		manager.addAxiom(csr, 
//				factory.getOWLDataPropertyRangeAxiom(isNoLocationAllowed
//						, factory.getOWLDatatype(OWL2Datatype.XSD_BOOLEAN.getIRI())));
//TODO: causing a stack overflow with a bad recursicve call in UiService.getForm when domain and range are equal.
//		manager.addAxiom(csr, 
//				factory.getOWLObjectPropertyDomainAxiom(hasOverdueActivity
//						, ServiceActivity
//							));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyRangeAxiom(hasOverdueActivity
						, ServiceActivity));
	
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyRangeAxiom(hasLegacyInterface
						, LegacyInterface));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyDomainAxiom(hasLegacyInterface
						, ServiceCase
									  
								));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyRangeAxiom(hasAllowableEvent
						, InterfaceEvent));
//		manager.addAxiom(csr, 
//				factory.getOWLObjectPropertyDomainAxiom(hasAllowableEvent
//						, ServiceCase
//									  
//								));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyDomainAxiom(hasServiceFieldAlert
						
						, ServiceField));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyRangeAxiom(hasServiceFieldAlert
						, QuestionTrigger
									  
								));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyDomainAxiom(hasActivityAssignment
						, factory.getOWLObjectUnionOf(ServiceField, ServiceActivity)));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyRangeAxiom(hasActivityAssignment
						, factory.getOWLObjectUnionOf(QuestionTrigger, ActivityTrigger)
									  
								));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyDomainAxiom(hasServiceField
						, factory.getOWLObjectUnionOf(ServiceCase
									  ,ServiceAnswer
									  ,QuestionTrigger
								)));

		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyRangeAxiom(hasServiceField
						, ServiceField));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyRangeAxiom(hasServiceCase
						, ServiceCase));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyDomainAxiom(hasDataType
						, ServiceField));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyRangeAxiom(hasDataType
						, factory.getOWLDatatype(OWL2Datatype.XSD_STRING.getIRI())));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyDomainAxiom(hasShowLocation
						, ServiceCase));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyRangeAxiom(hasShowLocation
						, factory.getOWLDatatype(OWL2Datatype.XSD_STRING.getIRI())));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyDomainAxiom(isExternal
						, LegacyInterface));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyRangeAxiom(isExternal
						, factory.getOWLDatatype(OWL2Datatype.XSD_BOOLEAN.getIRI())));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyDomainAxiom(hasValidLocationNotRequired
						, ServiceCase));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyRangeAxiom(hasValidLocationNotRequired
						, factory.getOWLDatatype(OWL2Datatype.XSD_BOOLEAN.getIRI())));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyDomainAxiom(hasJurisdictionCode
						, ServiceCase));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyRangeAxiom(hasJurisdictionCode
						, factory.getOWLDatatype(OWL2Datatype.XSD_STRING.getIRI())));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyDomainAxiom(hasJurisdictionDescription
						, ServiceCase));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyRangeAxiom(hasJurisdictionDescription
						, factory.getOWLDatatype(OWL2Datatype.XSD_STRING.getIRI())));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyDomainAxiom(hasGeoAreaCode
						, ServiceCase));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyRangeAxiom(hasGeoAreaCode
						, factory.getOWLDatatype(OWL2Datatype.XSD_STRING.getIRI())));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyDomainAxiom(hasGeoAreaRequired
						, ServiceCase));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyRangeAxiom(hasGeoAreaRequired
						, factory.getOWLDatatype(OWL2Datatype.XSD_STRING.getIRI())));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyDomainAxiom(hasDefaultStatus
						, ServiceCase));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyRangeAxiom(hasDefaultStatus
						, Status));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyRangeAxiom(hasStatus
						, Status));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyDomainAxiom(hasDefaultPriority
						, ServiceCase));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyRangeAxiom(hasDefaultPriority
						, Priority));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyDomainAxiom(hasPriority
						, ServiceCase));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyRangeAxiom(hasPriority
						, Priority));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyDomainAxiom(hasDefaultIntakeMethod
						, ServiceCase));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyRangeAxiom(hasDefaultIntakeMethod
						, IntakeMethod));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyDomainAxiom(hasIntakeMethod
						, ServiceCase));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyRangeAxiom(hasIntakeMethod
						, IntakeMethod));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyDomainAxiom(hasActivity
						, ServiceActivity));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyRangeAxiom(hasActivity
						, Activity));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyDomainAxiom(hasOutcome
						, ServiceActivity));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyRangeAxiom(hasActivity
						, Outcome));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyDomainAxiom(hasOrderBy
						, factory.getOWLObjectUnionOf(
								ServiceField
								,Status
								,Activity
								,ServiceActor
								)));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyRangeAxiom(hasOrderBy
						, factory.getOWLDatatype(OWL2Datatype.XSD_FLOAT.getIRI())));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyDomainAxiom(hasDetails
						, ServiceActivity));
		
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyRangeAxiom(hasDetails
						, factory.getOWLDatatype(OWL2Datatype.XSD_STRING.getIRI())));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyDomainAxiom(isAssignedTo
						, ServiceActivity));
		
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyRangeAxiom(isAssignedTo
						, factory.getOWLDatatype(OWL2Datatype.XSD_STRING.getIRI())));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyDomainAxiom(hasCompletedDate
						, ServiceActivity));
		
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyRangeAxiom(hasCompletedDate
						, factory.getOWLDatatype(OWL2Datatype.XSD_DATE_TIME_STAMP.getIRI())));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyDomainAxiom(hasCompletedTimestamp
						, ServiceActivity));
		
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyRangeAxiom(hasCompletedTimestamp
						, factory.getOWLDatatype(OWL2Datatype.XSD_DATE_TIME_STAMP.getIRI())));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyDomainAxiom(hasDueDate
						, ServiceActivity));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyRangeAxiom(hasDueDate
						, factory.getOWLDatatype(OWL2Datatype.XSD_DATE_TIME_STAMP.getIRI())));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyDomainAxiom(hasUpdatedDate
						, factory.getOWLObjectUnionOf(ServiceCase,ServiceActivity,ServiceCaseActor)));
		manager.addAxiom(csr, 
				factory.getOWLDataPropertyRangeAxiom(hasUpdatedDate
						, factory.getOWLDatatype(OWL2Datatype.XSD_DATE_TIME_STAMP.getIRI())));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyRangeAxiom(hasServiceActor
						, ServiceActor));
		manager.addAxiom(csr, 
				factory.getOWLObjectPropertyRangeAxiom(hasServiceCaseActor
						, ServiceCaseActor));
	}
	
	public static void main(String[] args) throws OWLOntologyCreationException
	{
		OWLOntology complement = null;
		if (args.length < 2)
		{
			System.err.println("Please specify a property file with db settings and a destination OWL file.");
			System.exit(-1);
		}
		
		Properties props = new Properties();
		try
		{
			props.load(new FileInputStream(args[0]));
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
			System.exit(-1);
		}
		
		if(args.length == 3)
		{
			try
			{
				File o = new File(args[2]);
				OWLOntologyManager m = OWL.manager(); 
				complement = m.loadOntologyFromOntologyDocument(o);
			}
			catch (Exception e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.out.println("Could not load ontology file " + args[2]);
			}
		}
		
		if(args.length == 4)
		{
			try
			{
				File log = new File(args[3]);
				PrintStream p = new PrintStream(log);
				System.setOut(p);
				System.setErr(p);
			}
			catch (FileNotFoundException e)
			{
				// TODO Auto-generated catch block
				System.out.println("Could not log to file " + args[3]);
			}
		}
		
		
		OutputStreamWriter out = null;
		try
		{
			
			File file = new File(args[1]);
			System.out.println("Exporting legacy ontology to file " + file.getAbsolutePath() );
			System.out.println("Please wait..");
			out = new OutputStreamWriter(new FileOutputStream(file) ,Charset.forName("UTF-8"));
			
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
			System.exit(-1);
		}
		
		exportOntology(out, props, props.getProperty("csr-jurisdiction"), complement, props.getProperty("stop-dated-SRs"));
		
		try
		{
			out.close();
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
			System.exit(-1);
		}
		
	}
	
	public static void getIntakeMethods(Connection conn, OWLOntology csr, OWLOntologyManager manager, PrefixManager pm)
	{
		Statement stmt = null;
    	ResultSet rs = null;
    	try
    	{
            StringBuffer sql = new StringBuffer();
            sql.append("select code_code, always_public_ind, description, description2, description3, ");
            sql.append(" description4, description5, description6, description7, description8, short_description, order_by ");
            sql.append("       from st_codes def");
            sql.append("       where def.type_code = 'SRMETHOD' and def.STOP_DATE is null");
            sql.append("       order by def.ORDER_BY");
            stmt = conn.createStatement();            
            rs = stmt.executeQuery(sql.toString());
            OWLDataFactory factory = manager.getOWLDataFactory();
            OWLClass intakeClass = factory.getOWLClass(":IntakeMethod", pm);
            while (rs.next())
            {
            	String intakeCode = rs.getString(1);
            	OWLNamedIndividual intake = factory.getOWLNamedIndividual(":" + intakeCode , pm); 
            	OWLClassAssertionAxiom a = factory.getOWLClassAssertionAxiom(intakeClass, intake);
            	if(!csr.containsAxiom(a))
            	{
            		manager.addAxiom(csr, a);
            		manager.addAxiom(csr, 
            				factory.getOWLDataPropertyAssertionAxiom(
            						factory.getOWLDataProperty(":hasLegacyCode",pm)
            						, intake,factory.getOWLLiteral(intakeCode)));
            		manager.addAxiom(csr, 
                			factory.getOWLDataPropertyAssertionAxiom(
                					factory.getOWLDataProperty(":isAlwaysPublic",pm), 
                					intake, factory.getOWLLiteral(rs.getString(2))));
            		manager.addAxiom(csr, factory.getOWLAnnotationAssertionAxiom(
            				intake.getIRI(),
							factory.getOWLAnnotation(
									factory.getRDFSLabel()
									,factory.getOWLLiteral(rs.getString(3)))));
            		manager.addAxiom(csr, 
                			factory.getOWLDataPropertyAssertionAxiom(
                					factory.getOWLDataProperty(":hasOrderBy",pm), 
                					intake, factory.getOWLLiteral(rs.getFloat("ORDER_BY"))));
            	}
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
	
	public static void getPriorities(Connection conn, OWLOntology csr, OWLOntologyManager manager, PrefixManager pm)
	{
		Statement stmt = null;
    	ResultSet rs = null;
    	try
    	{
            StringBuffer sql = new StringBuffer();
            sql.append("select code_code, always_public_ind, description, description2, description3, ");
            sql.append(" description4, description5, description6, description7, description8, short_description, order_by ");
            sql.append("       from st_codes def");
            sql.append("       where def.type_code = 'SRPRIOR' and def.STOP_DATE is null");
            sql.append("       order by def.ORDER_BY");
            stmt = conn.createStatement();            
            rs = stmt.executeQuery(sql.toString());
            OWLDataFactory factory = manager.getOWLDataFactory();
            OWLClass priorityClass = factory.getOWLClass(":Priority", pm);
            while (rs.next())
            {
            	String priorityCode = rs.getString(1);
            	OWLNamedIndividual priority = factory.getOWLNamedIndividual(":" + priorityCode , pm); 
            	OWLClassAssertionAxiom a = factory.getOWLClassAssertionAxiom(priorityClass, priority);
            	if(!csr.containsAxiom(a))
            	{
            		manager.addAxiom(csr, a);
            		manager.addAxiom(csr, 
            				factory.getOWLDataPropertyAssertionAxiom(
            						factory.getOWLDataProperty(":hasLegacyCode",pm)
            						, priority,factory.getOWLLiteral(priorityCode)));
            		manager.addAxiom(csr, 
                			factory.getOWLDataPropertyAssertionAxiom(
                					factory.getOWLDataProperty(":isAlwaysPublic",pm), 
                					priority, factory.getOWLLiteral(rs.getString(2))));
            		manager.addAxiom(csr, factory.getOWLAnnotationAssertionAxiom(
            				priority.getIRI(),
							factory.getOWLAnnotation(
									factory.getRDFSLabel()
									,factory.getOWLLiteral(rs.getString(3)))));
            		manager.addAxiom(csr, 
                			factory.getOWLDataPropertyAssertionAxiom(
                					factory.getOWLDataProperty(":hasOrderBy",pm), 
                					priority, factory.getOWLLiteral(rs.getFloat("ORDER_BY"))));
            	}
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
	
	public static void getLegacyInterfaces(Connection conn, OWLOntology csr, OWLOntologyManager manager, PrefixManager pm)
	{
		Statement stmt = null;
    	ResultSet rs = null;
    	try
    	{
            StringBuffer sql = new StringBuffer();
            sql.append("select code_code, always_public_ind, description, description2, description3, ");
            sql.append(" description4, description5, description6, description7, description8, short_description, order_by ");
            sql.append("       from st_codes def");
            sql.append("       where def.type_code = 'SRCRMODL' and def.STOP_DATE is null");
            sql.append("       order by def.ORDER_BY");
            stmt = conn.createStatement();            
            rs = stmt.executeQuery(sql.toString());
            OWLDataFactory factory = manager.getOWLDataFactory();
            OWLClass interfaceClass = factory.getOWLClass(":LegacyInterface", pm);
            while (rs.next())
            {
            	String interCode = rs.getString(1);
            	OWLNamedIndividual inter = factory.getOWLNamedIndividual(":" + interCode , pm); 
            	OWLClassAssertionAxiom a = factory.getOWLClassAssertionAxiom(interfaceClass, inter);
            	if(!csr.containsAxiom(a))
            	{
            		manager.addAxiom(csr, a);
            		manager.addAxiom(csr, 
            				factory.getOWLDataPropertyAssertionAxiom(
            						factory.getOWLDataProperty(":hasLegacyCode",pm)
            						, inter,factory.getOWLLiteral(interCode)));
            		manager.addAxiom(csr, 
                			factory.getOWLDataPropertyAssertionAxiom(
                					factory.getOWLDataProperty(":isAlwaysPublic",pm), 
                					inter, factory.getOWLLiteral(rs.getString(2))));
            		manager.addAxiom(csr, factory.getOWLAnnotationAssertionAxiom(
            				inter.getIRI(),
							factory.getOWLAnnotation(
									factory.getRDFSLabel()
									,factory.getOWLLiteral(rs.getString(3)))));
            		manager.addAxiom(csr, 
                			factory.getOWLDataPropertyAssertionAxiom(
                					factory.getOWLDataProperty(":hasOrderBy",pm), 
                					inter, factory.getOWLLiteral(rs.getFloat("ORDER_BY"))));
            		if(rs.getString("description3")!=null && rs.getString("description3").equals("EXTERNAL"))
            			manager.addAxiom(csr, 
                			factory.getOWLDataPropertyAssertionAxiom(
                					factory.getOWLDataProperty(":isExternal",pm), 
                					inter, factory.getOWLLiteral(true)));
            		
            		if(rs.getString("description4") == null)
            			continue;
            		StringBuffer allowableEvents =  new StringBuffer(rs.getString("description4"));
            		allowableEvents.deleteCharAt(0).deleteCharAt(allowableEvents.length()-1);
            		for(String e :allowableEvents.toString().split(","))
            		{
            			manager.addAxiom(csr, 
                				factory.getOWLObjectPropertyAssertionAxiom(
                						factory.getOWLObjectProperty(":hasAllowableEvent",pm)
                						, inter,factory.getOWLNamedIndividual(":" + e , pm)));
            		}
            	}
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
	
	public static void getInterfaceEvents(Connection conn, OWLOntology csr, OWLOntologyManager manager, PrefixManager pm)
	{
		Statement stmt = null;
    	ResultSet rs = null;
    	try
    	{
            StringBuffer sql = new StringBuffer();
            sql.append("select code_code, always_public_ind, description, description2, description3, ");
            sql.append(" description4, description5, description6, description7, description8, short_description, order_by ");
            sql.append("       from st_codes def");
            sql.append("       where def.type_code = 'EAIEVENT' and def.STOP_DATE is null");
            sql.append("       order by def.ORDER_BY");
            stmt = conn.createStatement();            
            rs = stmt.executeQuery(sql.toString());
            OWLDataFactory factory = manager.getOWLDataFactory();
            OWLClass eventClass = factory.getOWLClass(":InterfaceEvent", pm);
            OWLClass legacyEvent = factory.getOWLClass(":LegacyEvent", pm);
            manager.addAxiom(csr, factory.getOWLSubClassOfAxiom(eventClass, legacyEvent));
            while (rs.next())
            {
            	String eventCode = rs.getString(1);
            	OWLNamedIndividual event = factory.getOWLNamedIndividual(":" + eventCode , pm); 
            	OWLClassAssertionAxiom a = factory.getOWLClassAssertionAxiom(eventClass, event);
            	if(!csr.containsAxiom(a))
            	{
            		manager.addAxiom(csr, a);
            		manager.addAxiom(csr, 
            				factory.getOWLDataPropertyAssertionAxiom(
            						factory.getOWLDataProperty(":hasLegacyCode",pm)
            						, event,factory.getOWLLiteral(eventCode)));
            		manager.addAxiom(csr, 
                			factory.getOWLDataPropertyAssertionAxiom(
                					factory.getOWLDataProperty(":isAlwaysPublic",pm), 
                					event, factory.getOWLLiteral(rs.getString(2))));
            		manager.addAxiom(csr, factory.getOWLAnnotationAssertionAxiom(
            				event.getIRI(),
							factory.getOWLAnnotation(
									factory.getRDFSLabel()
									,factory.getOWLLiteral(rs.getString(3)))));
            		manager.addAxiom(csr, 
                			factory.getOWLDataPropertyAssertionAxiom(
                					factory.getOWLDataProperty(":hasOrderBy",pm), 
                					event, factory.getOWLLiteral(rs.getFloat("ORDER_BY"))));
            	}
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
	public static void getCaseAlerts(Connection conn, OWLOntology csr, OWLOntologyManager manager, PrefixManager pm, String serviceCode)
	{
		Statement stmt = null;
    	ResultSet rs = null;
    	try
    	{
            StringBuffer sql = new StringBuffer();
            sql.append("select ");
            sql.append("		EID || '' as EID,");
            sql.append("		CODE_1,");
			sql.append("		CODE_2,");
			sql.append("		CODE_3,");
			sql.append("		CODE_4,");
			sql.append("		MESSAGE_TYPE_CODE,");
			sql.append("		DEVICE_EID,");
			sql.append("		TO_ADDRESS,");
			sql.append("		CC_ADDRESS,");
			sql.append("		SUBJECT,");
			sql.append("		BODY,");
			sql.append("		SUBJECT,");
			sql.append("		ATTACHMENT_CODES,");
			sql.append("		START_DATE,");
			sql.append("		STOP_DATE");
			sql.append("		from ST_MESSAGE_DEFINITIONS");
			sql.append("		WHERE CODE_1 = 'SRSRTYPE'  AND MESSAGE_TYPE_CODE = 'SRSRMESG'");
			sql.append("  		AND CODE_2 = '").append(serviceCode).append("'");
			sql.append("		and STOP_DATE is null ");
			
			stmt = conn.createStatement();            
            rs = stmt.executeQuery(sql.toString());
            OWLDataFactory factory = manager.getOWLDataFactory();
            OWLClass legacyEvent = factory.getOWLClass(":LegacyEvent", pm);
            OWLClass caseAlert = factory.getOWLClass(":ServiceCaseAlert", pm);
            manager.addAxiom(csr, factory.getOWLSubClassOfAxiom(caseAlert, legacyEvent));
            while (rs.next())
            {
            	String eid =  rs.getString("EID");
            	OWLNamedIndividual alert = factory.getOWLNamedIndividual(":" + eid, pm);
            	String label = rs.getString("BODY");
            	if(label != null)
            	{
            		manager.addAxiom(csr, factory.getOWLClassAssertionAxiom(caseAlert,alert));
            		manager.addAxiom(csr,factory.getOWLObjectPropertyAssertionAxiom(
    						factory.getOWLObjectProperty(":hasServiceCaseAlert",pm)
    						, factory.getOWLNamedIndividual(":" + serviceCode  ,pm),alert));
            		manager.addAxiom(csr,factory.getOWLAnnotationAssertionAxiom(
            				alert.getIRI(),
    						factory.getOWLAnnotation(
    								factory.getRDFSLabel()
    								,factory.getOWLLiteral(label))));
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
	}
	
	
	public static void getReferrals(Connection conn, OWLOntology csr, OWLOntologyManager manager, PrefixManager pm, String serviceCode)
	{
		Statement stmt = null;
    	ResultSet rs = null;
    	try
    	{
            StringBuffer sql = new StringBuffer();
            sql.append("select ");
            sql.append("		EID || '' as EID,");
            sql.append("		REFERRED_SR_TYPE_CODE,");
			sql.append("		REFERRED_SR_STATUS_CODE,");
			sql.append("		ASSOCIATED_OUTCOME_CODE");
			sql.append("		from SR_REFERRAL_DEFINITIONS");
			sql.append("		WHERE CREATE_REASON_CODE = 'FOLLOW_O'"); //only referral SRs based on activity outcome are supported
			sql.append("  		AND SERVICE_REQUEST_TYPE_CODE = '").append(serviceCode).append("'");
			
			stmt = conn.createStatement();            
            rs = stmt.executeQuery(sql.toString());
            OWLDataFactory factory = manager.getOWLDataFactory();
            OWLClass ServiceCaseOutcomeTrigger = factory.getOWLClass(":ServiceCaseOutcomeTrigger", pm);
            OWLClass LegacyTrigger  = factory.getOWLClass(":LegacyTrigger", pm);
            OWLClass CreateServiceCase  = factory.getOWLClass(":CreateServiceCase", pm);
            OWLClass LegacyEvent  = factory.getOWLClass(":LegacyEvent", pm);
            manager.addAxiom(csr, factory.getOWLSubClassOfAxiom(CreateServiceCase, LegacyEvent));
            manager.addAxiom(csr, factory.getOWLSubClassOfAxiom(ServiceCaseOutcomeTrigger, LegacyTrigger));
            OWLNamedIndividual srType = factory.getOWLNamedIndividual(":" +serviceCode, pm);
            while (rs.next())
            {
            	String eid =  rs.getString("EID");
            	OWLNamedIndividual referralTrigger = factory.getOWLNamedIndividual(":" + eid, pm);
            	OWLNamedIndividual referralEvent = factory.getOWLNamedIndividual(":" + eid + "_CREATESR", pm);
            	OWLNamedIndividual referredType = factory.getOWLNamedIndividual(":" +rs.getString("REFERRED_SR_TYPE_CODE"), pm);
            	OWLNamedIndividual status = factory.getOWLNamedIndividual(":" +rs.getString("REFERRED_SR_STATUS_CODE"), pm);
            	OWLNamedIndividual outcome = factory.getOWLNamedIndividual(":OUTCOME_" + rs.getString("ASSOCIATED_OUTCOME_CODE"), pm);
            	
            	manager.addAxiom(csr, factory.getOWLClassAssertionAxiom(ServiceCaseOutcomeTrigger,referralTrigger));
            	manager.addAxiom(csr, factory.getOWLClassAssertionAxiom(CreateServiceCase,referralEvent));
            	manager.addAxiom(csr,factory.getOWLObjectPropertyAssertionAxiom(
    					factory.getOWLObjectProperty(":hasLegacyEvent",pm)
    					, referralTrigger, referralEvent));
            	manager.addAxiom(csr,factory.getOWLObjectPropertyAssertionAxiom(
    					factory.getOWLObjectProperty(":hasServiceCase",pm)
    					, referralTrigger, srType));
            	manager.addAxiom(csr,factory.getOWLObjectPropertyAssertionAxiom(
    					factory.getOWLObjectProperty(":hasOutcome",pm)
    					, referralTrigger, outcome));
            	manager.addAxiom(csr,factory.getOWLObjectPropertyAssertionAxiom(
    					factory.getOWLObjectProperty(":hasServiceCase",pm)
    					, referralEvent, referredType));
            	manager.addAxiom(csr,factory.getOWLObjectPropertyAssertionAxiom(
    					factory.getOWLObjectProperty(":hasStatus",pm)
    					, referralEvent, status));
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
	}
	public static void getProvidedBy(Map<String, List<OWLNamedIndividual>> srGCMap, OWLOntology csr, DefaultPrefixManager pm) {
		OWLOntologyManager manager = csr.getOWLOntologyManager();
		OWLDataFactory metaFactory = OWL.manager().getOWLDataFactory();
		OWLDataFactory factory = manager.getOWLDataFactory();
		
		
		OWLClassExpression q = OWL.some(dataProperty("CSR_GroupCode"), metaFactory.getOWLDatatype(OWL2Datatype.XSD_STRING.getIRI()));
		Set<OWLNamedIndividual> indSet = reasoner().getInstances(q, false).getFlattened();

		OWLObjectProperty providedBy = factory.getOWLObjectProperty(":providedBy",pm);
		
		for(OWLNamedIndividual ind : indSet) {
			String gc = ind.getDataPropertyValues(dataProperty("CSR_GroupCode"), OWL.ontology(Refs.topOntologyIRI.resolve())).iterator().next().getLiteral();
			if(srGCMap.containsKey(gc)) {
				List<OWLNamedIndividual> srTypeList = srGCMap.remove(gc);
				for(OWLNamedIndividual srType : srTypeList)
					manager.addAxiom(csr, factory.getOWLObjectPropertyAssertionAxiom(providedBy, srType, ind));
			}
		}
		for(Entry<String, List<OWLNamedIndividual>> entry : srGCMap.entrySet())
			for(OWLNamedIndividual eachSR : entry.getValue())
				System.out.println(entry.getKey() +"  :  "+eachSR.getIRI());
	}
	
	public static void getMessageVariables(Connection conn, OWLOntology csr, OWLOntologyManager manager, PrefixManager pm)
	{
		Statement stmt = null;
    	ResultSet rs = null;
    	try
    	{
            StringBuffer sql = new StringBuffer();
            sql.append("select OWNER_CODE, EID, DESCRIPTION, LIMIT_VALUE1 from st_dynamic_sql");
            stmt = conn.createStatement();            
            rs = stmt.executeQuery(sql.toString());
            OWLDataFactory factory = manager.getOWLDataFactory();
            OWLClass messageClass = factory.getOWLClass(":MessageVariable", pm);
            while (rs.next())
            {
            	String varName = rs.getString("LIMIT_VALUE1");
            	OWLNamedIndividual variable = factory.getOWLNamedIndividual(":" + URLEncoder.encode(varName.replaceAll("\\$\\$", ""),"UTF-8"), pm); 
            	OWLClassAssertionAxiom a = factory.getOWLClassAssertionAxiom(messageClass, variable);
            	if(!csr.containsAxiom(a))
            	{
            		manager.addAxiom(csr, a);
            		manager.addAxiom(csr,factory.getOWLAnnotationAssertionAxiom(
    	            		variable.getIRI(),
    						factory.getOWLAnnotation(
    								factory.getRDFSLabel()
    								,factory.getOWLLiteral(rs.getString("DESCRIPTION")))));
            	}
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
	
	public static void getMessageTemplates(Connection conn, OWLOntology csr, OWLOntologyManager manager, PrefixManager pm)
	{
		Statement stmt = null;
    	ResultSet rs = null;
    	try
    	{
            StringBuffer sql = new StringBuffer();
            sql.append("select EID, ")
			.append("CODE_1, ")
			.append("CODE_2, ")
			.append("CODE_3, ")
			.append("CODE_4, ")
			.append("MESSAGE_TYPE_CODE, ")
			.append("DEVICE_EID, ")
			.append("TO_ADDRESS, ")
			.append("CC_ADDRESS, ")
			.append("SUBJECT, ")
			.append("BODY, ")
			.append("ATTACHMENT_CODES, ")
			.append("START_DATE, ")
			.append("STOP_DATE ")
			.append("from ST_MESSAGE_DEFINITIONS WHERE MESSAGE_TYPE_CODE <> 'SRSRMESG' AND STOP_DATE IS NULL AND (TO_ADDRESS IS NOT NULL OR CC_ADDRESS IS NOT NULL)" );
            stmt = conn.createStatement();            
            rs = stmt.executeQuery(sql.toString());
            OWLDataFactory factory = manager.getOWLDataFactory();
            OWLClass messageClass = factory.getOWLClass(":MessageTemplate", pm);
            while (rs.next())
            {
            	boolean forSR = rs.getString("CODE_1").equalsIgnoreCase("SRSRTYPE");
            	String messageTemplateCode = null;
            	OWLNamedIndividual templateFor = null;
            	if(forSR)
            	{
            		messageTemplateCode = rs.getString("CODE_2") + "_" + rs.getString("MESSAGE_TYPE_CODE");
            		templateFor = factory.getOWLNamedIndividual(":" + rs.getString("CODE_2") , pm);
            	}
            	else
            	{
            		messageTemplateCode = rs.getString("CODE_2") + "_" + rs.getString("CODE_3") + "_" +rs.getString("MESSAGE_TYPE_CODE");
            		templateFor = factory.getOWLNamedIndividual(":" + rs.getString("CODE_2") + "_" + rs.getString("CODE_3") , pm);
            	}
            	
            	if(!csr.containsIndividualInSignature(templateFor.getIRI()))
            		continue;
            	OWLNamedIndividual messageTemplate = factory.getOWLNamedIndividual(":" + messageTemplateCode , pm); 
            	OWLClassAssertionAxiom a = factory.getOWLClassAssertionAxiom(messageClass, messageTemplate);
            	if(!csr.containsAxiom(a))
            	{
            		manager.addAxiom(csr, a);
//            		manager.addAxiom(csr, 
//            				factory.getOWLDataPropertyAssertionAxiom(
//            						factory.getOWLDataProperty(":hasLegacyCode",pm)
//            						, messageTemplate,factory.getOWLLiteral(rs.getString("MESSAGE_TYPE_CODE"))));
            		String to = rs.getString("TO_ADDRESS");
            		if(to != null)
            			manager.addAxiom(csr, 
            				factory.getOWLDataPropertyAssertionAxiom(
            						factory.getOWLDataProperty(":hasTo",pm)
            						, messageTemplate,factory.getOWLLiteral(to)));
            		String cc = rs.getString("CC_ADDRESS");
            		if(cc != null)
            			manager.addAxiom(csr, 
            				factory.getOWLDataPropertyAssertionAxiom(
            						factory.getOWLDataProperty(":hasCc",pm)
            						, messageTemplate,factory.getOWLLiteral(cc)));
            		String subject = rs.getString("SUBJECT");
            		if(subject != null)
            			manager.addAxiom(csr, 
            				factory.getOWLDataPropertyAssertionAxiom(
            						factory.getOWLDataProperty(":hasSubject",pm)
            						, messageTemplate,factory.getOWLLiteral(subject)));
            		String body = rs.getString("BODY");
            		if(body != null)
            		{
            			manager.addAxiom(csr, 
            				factory.getOWLDataPropertyAssertionAxiom(
            						factory.getOWLDataProperty(":hasBody",pm)
            						, messageTemplate,factory.getOWLLiteral(body)));
//            			if(forSR)
//            				manager.addAxiom(csr, 
//                    				factory.getOWLDataPropertyAssertionAxiom(
//                    						factory.getOWLDataProperty(":hasBody",pm)
//                    						, messageTemplate,factory.getOWLLiteral("$$GLOBAL_SR_TEMPLATE$$")));
//            			else
//            				manager.addAxiom(csr, 
//                    				factory.getOWLDataPropertyAssertionAxiom(
//                    						factory.getOWLDataProperty(":hasBody",pm)
//                    						, messageTemplate,factory.getOWLLiteral("$$GLOBAL_SR_TEMPLATE$$")));
                    			
            		}
            		String attachCodes = rs.getString("ATTACHMENT_CODES");
            		if(attachCodes != null)
            			manager.addAxiom(csr, 
            				factory.getOWLDataPropertyAssertionAxiom(
            						factory.getOWLDataProperty(":hasAttachmentCodes",pm)
            						, messageTemplate,factory.getOWLLiteral(attachCodes)));
            		manager.addAxiom(csr, 
            				factory.getOWLObjectPropertyAssertionAxiom(
            						factory.getOWLObjectProperty(":hasEmailTemplate",pm)
            						, templateFor,messageTemplate));
 
            	}
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
	
	private static void loadGroupEmails(Connection conn)
	{
		
		Statement stmt = null;
    	ResultSet rs = null;
    	try
    	{
    		String sql = "select SAS.eid  || '' as EID, PREFERENCE_2 from SR_ADDITIONAL_ASSIGNED_STAFF SAS, ST_PREFERENCES STP, ST_CODES ST where st.type_code = 'GROUP' and ST.CODE_CODE = SAS.GROUP_CODE and SAS.EID = STP.OWNER_EID and STP.CODE_1 = 'DEVICE' and STP.CODE_2 = 'PAGER'";
            stmt = conn.createStatement();            
            rs = stmt.executeQuery(sql);
            while (rs.next())
            {
            	String personnelID = rs.getString("EID");
            	String email =  rs.getString(2);
           		groupEmails.put(personnelID, email==null?personnelID:email.replaceAll(" ", ""));
            	
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
	
	private static void loadUserGeoAssignment(Connection conn)
	{
		
		Statement stmt = null;
    	ResultSet rs = null;
    	try
    	{
    		String sql = "select  OWNER_EID || '' as OWNER_EID, CODE_2 as TYPE, CODE_3 as GEO_AREA, PREFERENCE_1 as GEO_VALUES from ST_PREFERENCES where CODE_1 = 'USERGEO'";
            stmt = conn.createStatement();            
            rs = stmt.executeQuery(sql);
            while (rs.next())
            {
            	if(userGeoAssignment.get(rs.getString("TYPE")) == null)
            	{
            		userGeoAssignment.put(rs.getString("TYPE"), new ArrayList<Map<String,String>>());
            	}
            	Map<String,String> props = new HashMap<String, String>();
            	props.put("EID", rs.getString("OWNER_EID"));
            	props.put("GEO_AREA", rs.getString("GEO_AREA"));
            	props.put("GEO_VALUES", rs.getString("GEO_VALUES"));
            	userGeoAssignment.get(rs.getString("TYPE")).add(props);
            }   	 		
            System.out.println(userGeoAssignment.size() + " user geo assignments read from db.");
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
	
	
	/**EMERGNCY
		GENASSIG
	 SRINSERT
*/
	/*
	 * select EID
CODE_1,
CODE_2, --SRTYPE
CODE_3,
CODE_4,
MESSAGE_TYPE_CODE,
DEVICE_EID,
TO_ADDRESS,
CC_ADDRESS,
SUBJECT,
BODY,
ATTACHMENT_CODES,
START_DATE,
STOP_DATE
from ST_MESSAGE_DEFINITIONS WHERE CODE_1 = 'SRSRTYPE' and CODE_2 = 'BULKYTRA' and MESSAGE_TYPE_CODE = 'SRSRMESG'order by code_2
*/
}

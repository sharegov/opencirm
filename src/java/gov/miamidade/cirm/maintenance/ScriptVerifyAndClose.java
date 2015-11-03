package gov.miamidade.cirm.maintenance;

import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.rest.OperationService.getPersister;
import static org.sharegov.cirm.utils.GenUtils.ko;
import static org.sharegov.cirm.utils.GenUtils.ok;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.legacy.Permissions;
import org.sharegov.cirm.rdb.Concepts;
import org.sharegov.cirm.rdb.Query;
import org.sharegov.cirm.rdb.QueryTranslator;
import org.sharegov.cirm.rdb.RelationalOWLPersister;
import org.sharegov.cirm.rdb.RelationalStore;
import org.sharegov.cirm.rdb.RelationalStoreExt;
import org.sharegov.cirm.rdb.Sql;
import org.sharegov.cirm.rdb.Statement;
import org.sharegov.cirm.rest.LegacyEmulator;
import org.sharegov.cirm.rest.OperationService;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

/**
 * Given a tab separated spreadsheet containing a 
 * current status of closed and an SR number
 * lookup up the case in CiRM and verify if the status
 * is matches and the dates match, if not closed then
 * set the sr status to closed.
 * 
 * @author SABBAS
 *
 */
public class ScriptVerifyAndClose
{
	public static final Object lock = new Object();
	public static boolean WRITE_BEFORE_AND_AFTER_TO_FILE = false;
	public static boolean SAVE_TO_DB = true;
	public static boolean READ_AFTER_SAVE = false;
	public static long SLEEP_AFTER_FIX = 5000;
	
	private String caseDateFormat = "MM/dd/yyyy";
	private String spreadsheetFile = "C:/Work/cirmservices_fixes/cms_closed_date/CMS_CIRM_Cases.txt";
	private String outputFile = "C:/Work/cirmservices_fixes/cms_closed_date/cms_processed.txt";
	private String logFile = "C:/Work/cirmservices_fixes/cms_closed_date/cms_log.txt";
	private int caseNumberIdx = 2;
	private int closedDateIdx = 1;
	private int statusIdx = 0;
	private int deptIdIdx = 3;
	private int rowsToSkip = 1;
	private int threadCount = 5;
	private int recordsPerThread = 15;
	private File log;
	private File output;
	
	
	
	private Map<String,String> statusMap = new HashMap<String, String>();
	

	public static final ScriptVerifyAndClose CMS_SCRIPT = new ScriptVerifyAndClose()
	{
			{
				this.setSpreadsheetFile("C:/Work/cirmservices_fixes/cms_closed_date/CMS_CIRM_Cases.txt");
				this.setOutputFile("C:/Work/cirmservices_fixes/cms_closed_date/cms_processed.txt");
				this.setLogFile("C:/Work/cirmservices_fixes/cms_closed_date/cms_log.txt");
				this.setCaseNumberIdx(2);
				this.setClosedDateIdx(1);
				this.setRowsToSkip(1);
				this.setStatusIdx(0);
				this.setDeptIdIdx(3);
				this.getStatusMap().put("CLOSED", "C-CLOSED");
				this.getStatusMap().put("OPEN", "O-OPEN");
				this.getStatusMap().put("CLOSED-LIEN", "C-LIEN");
			}
		
	};
	
	public static final ScriptVerifyAndClose PWS_SCRIPT = new ScriptVerifyAndClose()
	{
			{
				this.setSpreadsheetFile("C:/Work/cirmservices_fixes/pws_closed_date/PWS 311 ALL MAY 2014 PRESENT.txt");
				this.setOutputFile("C:/Work/cirmservices_fixes/pws_closed_date/pws_processed.txt");
				this.setLogFile("C:/Work/cirmservices_fixes/pws_closed_date/pws_log.txt");
				this.setCaseNumberIdx(1);
				this.setClosedDateIdx(6);
				this.setRowsToSkip(4);
				//this.setCaseDateFormat("dd-MMM-yy hh'.'mm");
				//03-25-2015
				this.setCaseDateFormat("dd-MMM-yy hh'.'mm");
				this.setStatusIdx(2);
				this.setDeptIdIdx(0);
				this.getStatusMap().put("C", "C-CLOSED");
				this.getStatusMap().put("O", "O-OPEN");
				this.getStatusMap().put("V", "C-VOID");
			}
		
	};
	
	
	public static final ScriptVerifyAndClose WCS_SCRIPT = new ScriptVerifyAndClose()
	{
			{
				this.setSpreadsheetFile("C:/Work/cirmservices_fixes/wcs_closed_date_fix/wcs_file.txt");
				this.setOutputFile("C:/Work/cirmservices_fixes/wcs_closed_date_fix/wcs_processed.txt");
				this.setLogFile("C:/Work/cirmservices_fixes/wcs_closed_date_fix/wcs_log.txt");
				this.setCaseNumberIdx(2);
				this.setClosedDateIdx(4);
				//this.setRowsToSkip(1);
				//this.setRowsToSkip(29935);
				//this.setRowsToSkip(55216);
				this.setRowsToSkip(73199);
				//this.setCaseDateFormat("dd-MMM-yy hh'.'mm");
				//03-25-2015
				this.setCaseDateFormat("M/d/yyyy");
				this.setStatusIdx(5);
				this.setDeptIdIdx(0);
				this.getStatusMap().put("FR", "C-CLOSED");
				this.getStatusMap().put("CK", "C-CLOSED");
				this.getStatusMap().put("CN", "C-CLOSED");
				this.getStatusMap().put("PP", "O-OPEN");
				this.getStatusMap().put("SC", "O-OPEN");
				this.getStatusMap().put("PD", "O-OPEN");
			}
		
	};
	
	public static Date zeroedTimeValue(Date date) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.set(Calendar.HOUR_OF_DAY,0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		return calendar.getTime();
	}
	
	public static void main(String[] args)
	{
		StartUp.config.set("ontologyConfigSet", "http://www.miamidade.gov/ontology#ProdConfigSet");
		System.out.println(
				StartUp.config.at("ontologyConfigSet").toString()
				);
		
		
		//CMS_SCRIPT.run();
		//PWS_SCRIPT.run();
		WCS_SCRIPT.run();
	}
	
	
	
	public void run()
	{
		readSpreadsheetVerifyAndClose();
		cleanUp();
	}
	
	public void cleanUp()
	{
	
	}
	
	public void readSpreadsheetVerifyAndClose()
	{
		try
		{
			
			FileReader in = new FileReader(spreadsheetFile);
			BufferedReader bin = new BufferedReader(in);
			String[] columnNames = {"CASE_NUMBER","BO_ID",
					 "CIRM_STATUS",
					 "DEPT_STATUS",
					 "CIRM_CLOSED_DATE",
					 "DEPT_CLOSED_DATE",
					 "CIRM_LAST_UPDATE_DATE",
					 "DEPT_ID"};
			writeOutput(columnNames);
			String[] logColumnNames = {"CASE_NUMBER","BO_ID",
					 "ACTIVITY_ID",
					 "RULE_APPLIED"};
			writeLog(logColumnNames);
			int row = 0;		
			Map<String,String[]> caseNumbers = null;
			ExecutorService service = Executors.newFixedThreadPool(threadCount);
			for (String line = bin.readLine(); line != null; line = bin.readLine(),row++)
			{
						
						if(row < rowsToSkip)
						{
							continue;
						}
						String [] fields = line.split("\t");
						String caseNumber = fields[caseNumberIdx];
						if(caseNumber == null || caseNumber.isEmpty())
						{
							continue;
						}
						if(caseNumbers == null)
							caseNumbers = new HashMap<String,String[]>(recordsPerThread);
						caseNumbers.put(caseNumber, fields);
						if(caseNumbers.size() == recordsPerThread)
						{
							final Map<String,String[]> toVerifyAndClose = new HashMap<String,String[]>(caseNumbers);
							Callable<Object> readVerifyAndClose = new Callable<Object>()
							{
							
								@Override
								public Object call() throws Exception
								{
									
									for(Map.Entry<String,String[]> entry: toVerifyAndClose.entrySet())
									{
										String caseNum = entry.getKey();
										String[] fieldsForCase = entry.getValue(); 
										LegacyEmulator e = new LegacyEmulator();
										//04-01-2015 - LegacyEmulator has horrible api for retrieving SR ontos.
										//bringing the code in here for now.
										QueryTranslator qt = new QueryTranslator();
										RelationalOWLPersister persister = getPersister();
										RelationalStore store = persister.getStore();
										Query q = qt.translate(Json.object("legacy:hasCaseNumber", 
												caseNum, "type", "legacy:ServiceCase"), store);
										Set<Long> results = store.query(q, Refs.tempOntoManager.resolve().getOWLDataFactory());
										Json sr = null;
										BOntology bo = null;
										if (results.size() == 0)
											sr = Json.nil();
										else{
											Long caseId = results.iterator().next();
											try
											{
												bo = e.findServiceCaseOntology(caseId);
												if (bo == null)
													sr = ko("Case not found.");				
												sr = bo.toJSON();
												GenUtils.ensureArray(sr.at("properties"), "hasServiceActivity");
												GenUtils.ensureArray(sr.at("properties"), "hasServiceAnswer");
												GenUtils.ensureArray(sr.at("properties"), "hasServiceCaseActor");		
												//e.addAddressData(result);
												sr = ok().set("bo", sr);
											}
											catch (Throwable ex)
											{
												ex.printStackTrace();
												return ko(ex);
											}
										}
										if (!sr.isNull() && sr.is("ok", true))
								        {
								            sr = sr.at("bo");
								        }
								        else 
								        {
								            System.out.println("for case " + caseNum + " -- " + sr.at("error") );
								        
								        }
										verifyAndClose(sr, fieldsForCase);
										clearOntologyManager(bo.getOntology());
										
									}
									return toVerifyAndClose;
								}
							};
							service.submit(readVerifyAndClose);
							caseNumbers.clear();
							
						} else {
							continue;
						}
						
						
			}
			in.close();
			bin.close();
			if(service != null)
			{
				service.shutdown();
			}
		} catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
	
	public void verifyAndClose(Json sr, String[] givenFields)
	{
		String givenCaseNumber = givenFields[caseNumberIdx];
		String givenStatus = statusMap.get(givenFields[statusIdx]);
		Date givenClosedDate = parseDate(givenFields[closedDateIdx]);
		OWLNamedIndividual currentStatus = OWL.individual(sr.at("properties"),"hasStatus");
		Map<String,String> earliestClosingActivity = getEarliestActivityClosedDate(sr);
		String closedDate = (earliestClosingActivity!=null)?earliestClosingActivity.keySet().iterator().next():null;
		if (givenStatus==null)
			givenStatus = "";
		
		String[] row = {givenCaseNumber, 
				sr.at("boid").asLong() + "",
				currentStatus.getIRI().getFragment(),
				givenStatus,
				(closedDate==null)?"":closedDate,
				OWL.dateLiteral(givenClosedDate).getLiteral(),
				sr.at("properties").at("hasDateLastModified").asString(),
				givenFields[deptIdIdx]};
		//--Decision table
		VerifyAndCloseDecisionTable table = new VerifyAndCloseDecisionTable(sr,givenFields, givenStatus,currentStatus,closedDate,givenClosedDate,earliestClosingActivity);
		for(Rule rule : table.getRules())
		{
			try
			{
				rule.ifthen();
			}catch(Exception e)
			{
				System.out.println(rule.getName());
			}
		}
		writeOutput(row);
	}

	public Statement createChangeSRStatusStatement(Long boid,
			String givenStatus)
	{
		Statement stmt = new Statement();
		Sql insert = Sql.UPDATE("CIRM_SR_REQUESTS")
				.SET("SR_STATUS", "?")
				.WHERE("SR_REQUEST_ID").EQUALS("?");
		List<OWLNamedIndividual> types = new ArrayList<OWLNamedIndividual>();
		types.add(individual(Concepts.VARCHAR));
		types.add(individual(Concepts.INTEGER));
		List<Object> parameters = new ArrayList<Object>();
		parameters.add(givenStatus);
		parameters.add(boid);
		stmt.setSql(insert);
		stmt.setTypes(types);
		stmt.setParameters(parameters);
		return stmt;	
	}

	public List<Statement> createStatusChangeActivityStatements(Json sr,
			String[] givenFields, String givenStatus, Date givenClosedDate,
			OWLNamedIndividual currentStatus, Long boid, Date now,
			Long newActivityId)
	{
		List<Statement> activityStatements = new ArrayList<Statement>();
		Statement iriInsert  = createServiceActivityIriStatement(newActivityId);
		Statement classification = createServiceActivityClassificationStatement(newActivityId, new Timestamp(now.getTime()));
		Statement statusChangeActivity = createStatusChangeActivityStatement(newActivityId, 
				boid,
				givenStatus,
				new Timestamp(givenClosedDate.getTime()),
				new Timestamp(givenClosedDate.getTime()),
				sr, 
				givenFields);
		activityStatements.add(iriInsert);
		activityStatements.add(classification);
		activityStatements.add(statusChangeActivity);
		return activityStatements;
	}

	private Map<String,String> getEarliestActivityClosedDate(Json sr)
	{
		Map<String,String> earliestActivityClose = null;
		if( OWL.individual(sr.at("properties"),"hasStatus").getIRI().getFragment().contains("C-"))
		{
			List<String> orderedStatusChangeDates = new ArrayList<String>();
			Map<String,String> activityIris = new HashMap<String, String>(); 
			if(sr.at("properties").has("hasServiceActivity"))
			{
				for(Json serviceActivity: sr.at("properties").at("hasServiceActivity").asJsonList())
				{
					OWLNamedIndividual activity = OWL.individual(serviceActivity,"hasActivity");
					OWLNamedIndividual outcome = OWL.individual(serviceActivity, "hasOutcome");
					if(activity.getIRI().getFragment().contains("StatusChangeActivity")
							&& outcome.getIRI().getFragment().contains("C-"))
					{
						String date = serviceActivity.at("hasCompletedTimestamp").asString();
						orderedStatusChangeDates.add(date);
						activityIris.put(date, serviceActivity.at("iri").toString());
					}
				}
				if(!orderedStatusChangeDates.isEmpty())
				{
					Collections.sort(orderedStatusChangeDates);
					earliestActivityClose = Collections.singletonMap(orderedStatusChangeDates.get(0), activityIris.get(orderedStatusChangeDates.get(0)));
				}
			}
		
		}
		return earliestActivityClose;
	}

	private Date parseDate(String date)
	{
		Date result = null;
		try
		{
			if (date.equals(""))
	        {
	        	  System.out.println("empty date given" );
	              return result;
	        }
	        else
	        {
	        	SimpleDateFormat sdf = new SimpleDateFormat(caseDateFormat);
	        	result =sdf.parse(date);	
	        }
		}catch(Exception e)
		{
			e.printStackTrace();
		}
		return result;
	}

	private void saveChanges(BOntology bo)
	{
		if(SAVE_TO_DB)
		{
			getPersister().saveBusinessObjectOntology(bo.getOntology());
			if(READ_AFTER_SAVE)
				bo = new BOntology(getPersister().getBusinessObjectOntology(Long.parseLong(bo.getObjectId())));
		}
	}

	private void writeToFile(String file, BOntology bo)
	{
		FileOutputStream fos = null;
		try
		{
			fos = new FileOutputStream(file);
			fos.write(bo.toJSON().toString().getBytes());
			fos.flush();
			fos.close();
		} catch(IOException e)
		{
			throw new RuntimeException(e);
		}finally
		{
			try {
				if (fos != null) {
					fos.close();
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	private void writeToFile(String file, BOntology bo, Throwable t)
	{
		FileOutputStream fos = null;
		try
		{
			fos = new FileOutputStream(file);
			fos.write(bo.toJSON().toString().getBytes());
			fos.write("\n".getBytes());
			t.printStackTrace(new PrintStream(fos));
			fos.flush();
			fos.close();
		} catch(IOException e)
		{
			throw new RuntimeException(e);
		}finally
		{
			try {
				if (fos != null) {
					fos.close();
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	public Statement createStatusChangeActivityStatement(Long activityId, Long srId, String status, Timestamp createdDate, Timestamp completeDate,  Json sr, String[] givenFields)
	{
		Statement stmt = new Statement();
		Sql insert = Sql.INSERT_INTO("CIRM_SR_ACTIVITY")
				.VALUES("ACTIVITY_ID", "?")
				.VALUES("SR_REQUEST_ID", "?")
				.VALUES("ACTIVITY_CODE", "'StatusChangeActivity'")
				.VALUES("OUTCOME_CODE", "?")
				.VALUES("CREATED_BY", "'script'")
				.VALUES("CREATED_DATE", "?")
				.VALUES("COMPLETE_DATE", "?"  );
		List<OWLNamedIndividual> types = new ArrayList<OWLNamedIndividual>();
		types.add(individual(Concepts.INTEGER));
		types.add(individual(Concepts.INTEGER));
		types.add(individual(Concepts.VARCHAR));
		types.add(individual(Concepts.TIMESTAMP));
		types.add(individual(Concepts.TIMESTAMP));
		List<Object> parameters = new ArrayList<Object>();
		parameters.add(activityId);
		parameters.add(srId);
		parameters.add(status);
		parameters.add(createdDate);
		parameters.add(completeDate);
		stmt.setSql(insert);
		stmt.setTypes(types);
		stmt.setParameters(parameters);
		return stmt;	
	}
	
	public Statement createServiceActivityIriStatement(Long activityId){
		
		Statement stmt = new Statement();
		Sql insert = Sql.INSERT_INTO("CIRM_IRI")
				.VALUES("ID", "?")
				.VALUES("IRI", "?")
				.VALUES("IRI_TYPE_ID", "?");
		List<OWLNamedIndividual> types = new ArrayList<OWLNamedIndividual>();
		types.add(individual(Concepts.INTEGER));
		types.add(individual(Concepts.VARCHAR));
		types.add(individual(Concepts.INTEGER));
		List<Object> parameters = new ArrayList<Object>();
		parameters.add(activityId);
		parameters.add("http://www.miamidade.gov/ontology#ServiceActivity" + activityId); //90 is the Iri id of the ServiceActivity OWLClass
		parameters.add(new Long(4));
		stmt.setSql(insert);
		stmt.setTypes(types);
		stmt.setParameters(parameters);
		return stmt;	
	}
	
	public Statement createServiceActivityClassificationStatement(Long activityId, Timestamp fromDate){
		
		Statement stmt = new Statement();
		Sql insert = Sql.INSERT_INTO("CIRM_CLASSIFICATION")
				.VALUES("SUBJECT", "?")
				.VALUES("OWLCLASS", "?")
				.VALUES("FROM_DATE", "?");
		List<OWLNamedIndividual> types = new ArrayList<OWLNamedIndividual>();
		types.add(individual(Concepts.INTEGER));
		types.add(individual(Concepts.INTEGER));
		types.add(individual(Concepts.TIMESTAMP));
		List<Object> parameters = new ArrayList<Object>();
		parameters.add(activityId);
		parameters.add(new Long(90)); //90 is the Iri id of the ServiceActivity OWLClass
		parameters.add(fromDate);
		stmt.setSql(insert);
		stmt.setTypes(types);
		stmt.setParameters(parameters);
		return stmt;	
		
	}
	
	public Statement createStatusChangeStatement(Long srId, String newStatus){
		
		Statement stmt = new Statement();
		Sql update = Sql.UPDATE("CIRM_SR_REQUESTS")
				.SET("SR_STATUS", "?")
				.WHERE("SR_REQUEST_ID").EQUALS("?");
		List<OWLNamedIndividual> types = new ArrayList<OWLNamedIndividual>();
		types.add(individual(Concepts.VARCHAR));
		types.add(individual(Concepts.INTEGER));
		List<Object> parameters = new ArrayList<Object>();
		parameters.add(newStatus);
		parameters.add(srId);
		stmt.setSql(update);
		stmt.setTypes(types);
		stmt.setParameters(parameters);
		return stmt;	
		
	}
	
	public Statement createCommentBlockStatement(Long boid, Long activityId, String currentStatus, String givenStatus, Date now){
		
		Statement stmt = new Statement();
		Sql update = Sql.UPDATE("CIRM_SR_ACTIVITY")
				.SET("DETAILS", "?")
				.WHERE("ACTIVITY_ID").EQUALS("?");
		List<OWLNamedIndividual> types = new ArrayList<OWLNamedIndividual>();
		types.add(individual(Concepts.VARCHAR));
		types.add(individual(Concepts.INTEGER));
		List<Object> parameters = new ArrayList<Object>();
		parameters.add("Changing sr:" + boid +" from status " + currentStatus + " to " + givenStatus +  " on " + now.toString());
		parameters.add(activityId);
		stmt.setSql(update);
		stmt.setTypes(types);
		stmt.setParameters(parameters);
		return stmt;	
	}
	
	
	public Statement createEditActivityStatement(String activityIri, Date fromDate, Date toDate, Date now){
		
		Statement stmt = new Statement();
		Sql update = Sql.UPDATE("CIRM_SR_ACTIVITY")
				.SET("DETAILS", "?")
				.SET("UPDATED_BY", "'script'"  )
				.SET("COMPLETE_DATE", "?"  )
				.SET("UPDATED_DATE", "?"  )
				.WHERE("ACTIVITY_ID").EQUALS("(select ID from CIRM_IRI where IRI = ?)");
		List<OWLNamedIndividual> types = new ArrayList<OWLNamedIndividual>();
		types.add(individual(Concepts.VARCHAR));
		types.add(individual(Concepts.TIMESTAMP));
		types.add(individual(Concepts.TIMESTAMP));
		types.add(individual(Concepts.VARCHAR));
		List<Object> parameters = new ArrayList<Object>();
		parameters.add("Changing activity complete date from :" + OWL.dateLiteral(fromDate).getLiteral() +" to " +  OWL.dateLiteral(fromDate).getLiteral());
		parameters.add(new Timestamp(fromDate.getTime()));
		parameters.add(new Timestamp(now.getTime()));
		parameters.add(activityIri);
		stmt.setSql(update);
		stmt.setTypes(types);
		stmt.setParameters(parameters);
		return stmt;	
	}
	
	private void writeOutput(String[] row)
	{
		synchronized (lock)
		{
			FileOutputStream stream = null;
			try
			{
				
				if(output == null)
				{
					output = new File(outputFile);
					if(output.exists())
					{
						output.delete();
					}
					
				}
				stream = new FileOutputStream(output, true);
				for(int i = 0; i < row.length; i++)
				{
					stream.write(row[i].getBytes());
					if(i != row.length - 1)
						stream.write("\t".getBytes());
				}
				stream.write("\n".getBytes());
				stream.flush();
				stream.close();
			} catch(IOException e)
			{
				e.printStackTrace();
				throw new RuntimeException(e);
			}finally
			{
				try {
					if (stream != null) {
						stream.close();
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}
	
	private void writeLog(String[] row)
	{
		synchronized (lock)
		{
			FileOutputStream stream = null;
			try
			{
				
				if(log == null)
				{
					log = new File(logFile);
					if(log.exists())
					{
						log.delete();
					}
				}
				stream = new FileOutputStream(log, true); 
				for(int i = 0; i < row.length; i++)
				{
					stream.write(row[i].getBytes());
					if(i != row.length - 1)
						stream.write("\t".getBytes());
				}
				stream.write("\n".getBytes());
				stream.flush();
//				log.close();
			} catch(IOException e)
			{
				e.printStackTrace();
				throw new RuntimeException(e);
			}finally
			{
				try {
					if (stream != null) {
						stream.close();
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}
	
	
	public void readFromFileAndSave(String file)
	{
		String formData;
		try
		{
			formData = new Scanner(new File(file)).useDelimiter("\\A").next();
			BOntology bo = BOntology.makeRuntimeBOntology(Json.read(formData));
			getPersister().saveBusinessObjectOntology(bo.getOntology());
		}
		catch (FileNotFoundException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public String getLogFile()
	{
		return logFile;
	}

	public void setLogFile(String logFile)
	{
		this.logFile = logFile;
	}

	public String getSpreadsheetFile()
	{
		return spreadsheetFile;
	}

	public void setSpreadsheetFile(String spreadsheetFile)
	{
		this.spreadsheetFile = spreadsheetFile;
	}

	public int getCaseNumberIdx()
	{
		return caseNumberIdx;
	}

	public void setCaseNumberIdx(int caseNumberIdx)
	{
		this.caseNumberIdx = caseNumberIdx;
	}

	public int getClosedDateIdx()
	{
		return closedDateIdx;
	}

	public void setClosedDateIdx(int closedDateIdx)
	{
		this.closedDateIdx = closedDateIdx;
	}

	public int getRowsToSkip()
	{
		return rowsToSkip;
	}

	public void setRowsToSkip(int rowsToSkip)
	{
		this.rowsToSkip = rowsToSkip;
	}

	
	public Map<String, String> getStatusMap()
	{
		return statusMap;
	}

	public void setStatusMap(Map<String, String> statusMap)
	{
		this.statusMap = statusMap;
	}
	
	public int getStatusIdx()
	{
		return statusIdx;
	}

	public void setStatusIdx(int statusIdx)
	{
		this.statusIdx = statusIdx;
	}
	
	public String getOutputFile()
	{
		return outputFile;
	}

	public void setOutputFile(String outputFile)
	{
		this.outputFile = outputFile;
	}
	
	public int getDeptIdIdx()
	{
		return deptIdIdx;
	}

	public void setDeptIdIdx(int deptIdIdx)
	{
		this.deptIdIdx = deptIdIdx;
	}
	
	public String getCaseDateFormat()
	{
		return caseDateFormat;
	}

	public void setCaseDateFormat(String caseDateFormat)
	{
		this.caseDateFormat = caseDateFormat;
	}
	
	class InsertStatusChangeActivity implements Action{

		private Long activityId;
		private List<Statement> fixStatements;
		
		public InsertStatusChangeActivity(Long aId, Json sr,
				String[] givenFields, String givenStatus, Date givenClosedDate,
				OWLNamedIndividual currentStatus, Long boid, Date now)
		{
			
			this.activityId = aId;
			this.fixStatements = createStatusChangeActivityStatements(sr, givenFields, givenStatus,
					givenClosedDate, currentStatus, boid, now, this.activityId);
		
		}
		
		@Override
		public void execute()
		{
			ThreadLocalStopwatch.getWatch().time("InsertStatusChangeActivity.execute()");
			for(Statement stmt : fixStatements)
			{
				try
				{
					
					OperationService.getPersister().getStoreExt().executeStatement(stmt);
				}catch(Exception e)
				{
					e.printStackTrace();
				}
			}
			
			// TODO Auto-generated method stub
			
		}
		
	}
	
	class ChangeSRStatus implements Action{
		
		private Long srId;
		private Long activityId;
		private String status;
		private String givenStatus;
		private Statement fixStatement;
		
		public ChangeSRStatus(Long srId, Long activityId, String status,String givenStatus, Date now)
		{
				this.srId = srId;
				this.status = status;
				this.activityId = activityId;
				this.fixStatement = createChangeSRStatusStatement(srId,givenStatus);
		}
		
		@Override
		public void execute()
		{
			try
			{
					
					OperationService.getPersister().getStoreExt().executeStatement(fixStatement);
			}catch(Exception e)
			{
				e.printStackTrace();
			}
			
		}
		
	}
	
	class AddCommentBlock implements Action{
		
		private Long srId;
		private Long activityId;
		private String status;
		private String givenStatus;
		private Statement fixStatement;
		
		public AddCommentBlock(Long srId, Long activityId, String status,String givenStatus, Date now)
		{
				this.srId = srId;
				this.status = status;
				this.activityId = activityId;
				this.fixStatement = createCommentBlockStatement(srId, activityId, status, givenStatus, now);
		}
		
		@Override
		public void execute()
		{
			try
			{
					
					OperationService.getPersister().getStoreExt().executeStatement(fixStatement);
			}catch(Exception e)
			{
				e.printStackTrace();
			}
			
		}
		
	}
	
	
	class EditEarliestClosedDate implements Action{
		
		private Map<String,String> earliestClosingActivity;
		private Date givenClosedDate;
		private Statement fixStatement;
		private Date now;
		
		public EditEarliestClosedDate(Map<String,String> earliestClosingActivity, Date givenClosedDate, Date now)
		{
				this.earliestClosingActivity = earliestClosingActivity;
				this.givenClosedDate = givenClosedDate;
				this.now= now;
				this.fixStatement = createEditActivityStatement(earliestClosingActivity.values().iterator().next(),GenUtils.parseDate(earliestClosingActivity.keySet().iterator().next()), givenClosedDate, now);
		}
		
		@Override
		public void execute()
		{
			try
			{
					
				System.out.println(fixStatement.getSql().SQL());
				OperationService.getPersister().getStoreExt().executeStatement(fixStatement);
			}catch(Exception e)
			{
				e.printStackTrace();
			}
			
		}
		
	}
	
	class VerifyAndCloseDecisionTable
	{
		String[] givenFields;
		String givenStatus;
		OWLNamedIndividual currentStatus;
		String closedDate;
		Date givenClosedDate;
		boolean deptStatusEqualClosed;
		boolean cirmStatusNotEqualDeptStatus;
		boolean cirmClosedDateNotEqualDeptClosedDate;
		boolean cirmClosedDateLessThanDeptClosedDate;
		RelationalOWLPersister persister = OperationService.getPersister();
		RelationalStoreExt store = persister.getStoreExt();
		Long boid;
		Date now;
		Long newActivityId;
		Json sr;
		List<Rule> rules;
		Map<String,String> earliestClosingActivity;
		public VerifyAndCloseDecisionTable(Json sr, String[] givenFields, String givenStatus, OWLNamedIndividual currentStatus, String closedDate, Date givenClosedDate, Map<String,String> earliestClosingActivity)
		{
            this.newActivityId = store.nextSequenceNumber();
            this.sr = sr;
            this.boid  =  sr.at("boid").asLong();
            this.now = store.getStoreTime();
            this.givenStatus = givenStatus;
            this.closedDate = closedDate;
            if(this.closedDate == null)
            	this.closedDate = sr.at("properties").at("hasDateLastModified").asString();
            this.givenClosedDate = givenClosedDate;
            this.currentStatus = currentStatus;
            this.givenFields = givenFields;
            this.earliestClosingActivity = earliestClosingActivity;
            this.deptStatusEqualClosed = (givenStatus==null)?false:givenStatus.startsWith("C");
			this.cirmStatusNotEqualDeptStatus = givenStatus == null || givenStatus.equals("") || !currentStatus.getIRI().getFragment().startsWith(givenStatus.substring(0,1));
			this.cirmClosedDateNotEqualDeptClosedDate = (!(this.closedDate==null) && !GenUtils.parseDate(this.closedDate).equals(givenClosedDate));
			this.cirmClosedDateLessThanDeptClosedDate = (!(this.closedDate==null) && zeroedTimeValue(GenUtils.parseDate(this.closedDate)).before(zeroedTimeValue(givenClosedDate)));
		}
		
		public List<Rule> getRules()
		{

			if(rules != null && !rules.isEmpty())
			{
				return rules;
			}else
			{
				Rule rule1 = new Rule( 
					new Condition(){
						
						public boolean eval(){
							 return deptStatusEqualClosed && cirmStatusNotEqualDeptStatus && cirmClosedDateNotEqualDeptClosedDate && cirmClosedDateLessThanDeptClosedDate;
						}
						
						public String toString()
						{
							return "deptStatusEqualClosed && cirmStatusNotEqualDeptStatus && cirmClosedDateNotEqualDeptClosedDate && cirmClosedDateLessThanDeptClosedDate";
						}
						
					}, 
					new Action(){
						public void execute(){
							String[] row = {sr.at("properties").at("hasCaseNumber").asString(), boid+"",
									newActivityId+ "",
							 "rule1"};
							ScriptVerifyAndClose.this.writeLog(row);
							new InsertStatusChangeActivity(newActivityId, sr, givenFields, givenStatus, givenClosedDate, currentStatus, boid, now).execute();
							new ChangeSRStatus(boid, newActivityId, currentStatus.getIRI().getFragment(), givenStatus, now).execute();
							new AddCommentBlock(boid, newActivityId, currentStatus.getIRI().getFragment(), givenStatus, now).execute();
							if(earliestClosingActivity != null && earliestClosingActivity.size() > 0)
							{
								new EditEarliestClosedDate(earliestClosingActivity, givenClosedDate, now).execute();
							}
						}

					});
				Rule rule2 = new Rule( 
						new Condition(){
							
							public boolean eval(){
								 return deptStatusEqualClosed && cirmStatusNotEqualDeptStatus && cirmClosedDateNotEqualDeptClosedDate && (cirmClosedDateLessThanDeptClosedDate==false);
							}
							
							public String toString()
							{
								return "deptStatusEqualClosed && cirmStatusNotEqualDeptStatus && cirmClosedDateNotEqualDeptClosedDate && (cirmClosedDateLessThanDeptClosedDate==false)";
							}
							
						}, 
						new Action(){
							public void execute(){
								String[] row = {sr.at("properties").at("hasCaseNumber").asString(), boid+"",
										newActivityId+ "",
								 "rule2"};
								ScriptVerifyAndClose.this.writeLog(row);
								new InsertStatusChangeActivity(newActivityId, sr, givenFields, givenStatus, givenClosedDate, currentStatus, boid, now).execute();
								new ChangeSRStatus(boid, newActivityId, currentStatus.getIRI().getFragment(), givenStatus, now).execute();
								new AddCommentBlock(boid, newActivityId, currentStatus.getIRI().getFragment(), givenStatus, now).execute();
							}
						});
				Rule rule3 = new Rule( 
						new Condition(){
							
							public boolean eval(){
								 return deptStatusEqualClosed && cirmStatusNotEqualDeptStatus && (cirmClosedDateNotEqualDeptClosedDate==false) && (cirmClosedDateLessThanDeptClosedDate==false);
							}
							
							public String toString()
							{
								return "deptStatusEqualClosed && cirmStatusNotEqualDeptStatus && cirmClosedDateNotEqualDeptClosedDate && (cirmClosedDateLessThanDeptClosedDate==false)";
							}
							
						}, 
						new Action(){
							public void execute(){
								String[] row = {sr.at("properties").at("hasCaseNumber").asString(), boid+"",
										newActivityId+ "",
								 "rule3"};
								ScriptVerifyAndClose.this.writeLog(row);
								new InsertStatusChangeActivity(newActivityId, sr, givenFields, givenStatus, givenClosedDate, currentStatus, boid, now).execute();
								new ChangeSRStatus(boid, newActivityId, currentStatus.getIRI().getFragment(), givenStatus, now).execute();
							}
						});
				Rule rule6 = new Rule( 
						new Condition(){
							
							public boolean eval(){
								 return (deptStatusEqualClosed==false) && cirmStatusNotEqualDeptStatus && (cirmClosedDateNotEqualDeptClosedDate==false) && (cirmClosedDateLessThanDeptClosedDate==false);
							}
							
							public String toString()
							{
								return "(deptStatusEqualClosed==false) && cirmStatusNotEqualDeptStatus && (cirmClosedDateNotEqualDeptClosedDate==false) && (cirmClosedDateLessThanDeptClosedDate==false)";
							}
							
							
						}, 
						new Action(){
							public void execute(){
								String[] row = { sr.at("properties").at("hasCaseNumber").asString(), boid+"",
										newActivityId+ "",
								 "rule6"};
								ScriptVerifyAndClose.this.writeLog(row);
								new InsertStatusChangeActivity(newActivityId, sr, givenFields, givenStatus, givenClosedDate, currentStatus, boid, now).execute();
								new ChangeSRStatus(boid, newActivityId, currentStatus.getIRI().getFragment(), givenStatus, now).execute();
								new AddCommentBlock(boid, newActivityId, currentStatus.getIRI().getFragment(), givenStatus, now).execute();
							}
						});
				Rule rule8 = new Rule( 
						new Condition(){
							
							public boolean eval(){
								 return (deptStatusEqualClosed) && cirmStatusNotEqualDeptStatus==false && (cirmClosedDateNotEqualDeptClosedDate) && (cirmClosedDateLessThanDeptClosedDate);
							}
							
							public String toString()
							{
								return "(deptStatusEqualClosed) && cirmStatusNotEqualDeptStatus==false && (cirmClosedDateNotEqualDeptClosedDate) && (cirmClosedDateLessThanDeptClosedDate)";
							}
							
							
						}, 
						new Action(){
							public void execute(){
								String[] row = {sr.at("properties").at("hasCaseNumber").asString(), boid+"",
										newActivityId+ "",
								 "rule8" + ((earliestClosingActivity.size() > 0)?"(edit)":"(insert)")};
								ScriptVerifyAndClose.this.writeLog(row);
								if(earliestClosingActivity != null && earliestClosingActivity.size() > 0)
								{
									new EditEarliestClosedDate(earliestClosingActivity, givenClosedDate, now).execute();
								}else
								{
									new InsertStatusChangeActivity(newActivityId, sr, givenFields, givenStatus, givenClosedDate, currentStatus, boid, now).execute();
								}
							}
						});
				
				Rule rule9 = new Rule( 
						new Condition(){
							
							public boolean eval(){
								 return (deptStatusEqualClosed) && cirmStatusNotEqualDeptStatus==false && (cirmClosedDateNotEqualDeptClosedDate) && (cirmClosedDateLessThanDeptClosedDate==false);
							}
							
							public String toString()
							{
								return "(deptStatusEqualClosed) && cirmStatusNotEqualDeptStatus==false && (cirmClosedDateNotEqualDeptClosedDate) && (cirmClosedDateLessThanDeptClosedDate)";
							}
							
							
						}, 
						new Action(){
							public void execute(){
								String[] row = {sr.at("properties").at("hasCaseNumber").asString(), boid+"",
										newActivityId+ "",
								 "rule9"};
								ScriptVerifyAndClose.this.writeLog(row);
								new InsertStatusChangeActivity(newActivityId, sr, givenFields, givenStatus, givenClosedDate, currentStatus, boid, now).execute();
							}
						});
			rule1.setName("rule1");
			rule2.setName("rule2");
			rule3.setName("rule3");
			rule6.setName("rule6");
			rule8.setName("rule8");
			rule9.setName("rule9");
			rules = new ArrayList<Rule>();
			rules.add(rule1);
			rules.add(rule2);
			rules.add(rule3);
			rules.add(rule6);
			rules.add(rule8);
			rules.add(rule9);
			return rules;
			}
		}
		
		
	}
	
 /**
     * Removes all ontologies from temporory ontology Manager.
     * (e.g Clears loaded SR ontologies)
     */
     public synchronized void clearOntologyManager() {
            OWLOntologyManager m = Refs.tempOntoManager.resolve();
            Set<OWLOntology> tempOntos = m.getOntologies();
            for(OWLOntology o : tempOntos) {
                   m.removeOntology(o);
            }
     }

    /**
     * Removes one ontology from temporory ontology Manager.
     * (e.g Clears loaded SR ontologies)
     */
     public synchronized void clearOntologyManager(OWLOntology o) {
            OWLOntologyManager m = Refs.tempOntoManager.resolve();
            //m is a SynchonizedOntoManager, so its thread save to remove an ontology 
            //that was just processed
            m.removeOntology(o);
     }

}

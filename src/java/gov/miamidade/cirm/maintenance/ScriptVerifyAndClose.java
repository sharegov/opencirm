package gov.miamidade.cirm.maintenance;

import static org.sharegov.cirm.rest.OperationService.getPersister;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.rest.LegacyEmulator;

import com.sleepycat.je.rep.elections.Protocol.Result;

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
	private int caseNumberIdx = 2;
	private int closedDateIdx = 1;
	private int statusIdx = 0;
	private int deptIdIdx = 3;
	private int rowsToSkip = 1;
	private int threadCount = 10;
	private int recordsPerThread = 5;
	
	private Map<String,String> statusMap = new HashMap<String, String>();
	

	public static final ScriptVerifyAndClose CMS_SCRIPT = new ScriptVerifyAndClose()
	{
			{
				this.setSpreadsheetFile("C:/Work/cirmservices_fixes/cms_closed_date/CMS_CIRM_Cases.txt");
				this.setOutputFile("C:/Work/cirmservices_fixes/cms_closed_date/cms_processed.txt");
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
				this.setSpreadsheetFile("C:/Work/cirmservices_fixes/pws_closed_date/ALL PW 311 SRS 2013 - PRESENT.txt");
				this.setOutputFile("C:/Work/cirmservices_fixes/pws_closed_date/pws_processed.txt");
				this.setCaseNumberIdx(1);
				this.setClosedDateIdx(6);
				this.setRowsToSkip(4);
				this.setCaseDateFormat("dd-MMM-yy hh'.'mm");
				this.setStatusIdx(2);
				this.setDeptIdIdx(0);
				this.getStatusMap().put("C", "C-CLOSED");
				this.getStatusMap().put("O", "O-OPEN");
				this.getStatusMap().put("V", "C-VOID");
			}
		
	};
	
	public static void main(String[] args)
	{
		//StartUp.config.set("ontologyConfigSet", "http://www.miamidade.gov/ontology#ProdConfigSet");
		//CMS_SCRIPT.run();
		PWS_SCRIPT.run();
	}
	
	public void run()
	{
		readSpreadsheetVerifyAndClose();
	}
	
	public void readSpreadsheetVerifyAndClose()
	{
		try
		{
			
			FileReader in = new FileReader(spreadsheetFile);
			BufferedReader bin = new BufferedReader(in);
			String[] columnNames = {"CASE_NUMBER",
					 "CIRM_STATUS",
					 "DEPT_STATUS",
					 "CIRM_CLOSED_DATE",
					 "DEPT_CLOSED_DATE",
					 "CIRM_LAST_UPDATE_DATE",
					 "DEPT_ID"};
			writeOutput(columnNames, false);
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
										Json sr = e.lookupByCaseNumber(caseNum);
										if (!sr.isNull() && sr.is("ok", true))
								        {
								            sr = sr.at("bo");
								        }
								        else 
								        {
								            System.out.println("for case " + caseNum + " -- " + sr.at("error") );
								        
								        }
										verifyAndClose(sr, fieldsForCase);
										
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
		String closedDate = getEarliestActivityClosedDate(sr);
		String[] row = {givenCaseNumber, 
				currentStatus.getIRI().getFragment(),
				(givenStatus==null)?"":givenStatus,
				(closedDate==null)?"":closedDate,
				OWL.dateLiteral(givenClosedDate).getLiteral(),
				sr.at("properties").at("hasDateLastModified").asString(),
				givenFields[deptIdIdx]};
		writeOutput(row, true);
	}

	private String getEarliestActivityClosedDate(Json sr)
	{
		String closedDate = null;
		if( OWL.individual(sr.at("properties"),"hasStatus").getIRI().getFragment().contains("C-"))
		{
			List<String> orderedStatusChangeDates = new ArrayList<String>();
			if(sr.at("properties").has("hasServiceActivity"))
			{
				for(Json serviceActivity: sr.at("properties").at("hasServiceActivity").asJsonList())
				{
					OWLNamedIndividual activity = OWL.individual(serviceActivity,"hasActivity");
					OWLNamedIndividual outcome = OWL.individual(serviceActivity, "hasOutcome");
					if(activity.getIRI().getFragment().contains("StatusChangeActivity")
							&& outcome.getIRI().getFragment().contains("C-"))
					{
						orderedStatusChangeDates.add(serviceActivity.at("hasCompletedTimestamp").asString());
					}
				}
				if(!orderedStatusChangeDates.isEmpty())
				{
					Collections.sort(orderedStatusChangeDates);
					closedDate = orderedStatusChangeDates.get(0);
				}
			}
		
		}
		return closedDate;
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
	
	private void writeOutput(String[] row, boolean append)
	{
		synchronized (lock)
		{
			FileOutputStream fos = null;
			try
			{
				fos = new FileOutputStream(outputFile, append);
				for(int i = 0; i < row.length; i++)
				{
					fos.write(row[i].getBytes());
					if(i != row.length - 1)
						fos.write("\t".getBytes());
				}
				fos.write("\n".getBytes());
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
}

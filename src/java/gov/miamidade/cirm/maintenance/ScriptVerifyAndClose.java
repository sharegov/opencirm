package gov.miamidade.cirm.maintenance;

import static org.sharegov.cirm.rest.OperationService.getPersister;
import gov.miamidade.cirm.legacy.LegacyCaseImport;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.rdb.RelationalOWLPersister;
import org.sharegov.cirm.rdb.RelationalStoreExt;

/**
 * Give a tab separated spreadsheet containing a 
 * current status of closed and an SR number
 * lookup up the case in CiRM and verify if the status
 * is closed (matches), if not closed then set the sr 
 * status to closed.
 * 
 * @author SABBAS
 *
 */
public class ScriptVerifyAndClose
{
	public static boolean WRITE_BEFORE_AND_AFTER_TO_FILE = false;
	public static boolean SAVE_TO_DB = true;
	public static boolean READ_AFTER_SAVE = false;
	public static long SLEEP_AFTER_FIX = 5000;
		
	public static void main(String[] args)
	{
		//StartUp.config.set("ontologyConfigSet", "http://www.miamidade.gov/ontology#ProdConfigSet");
		ScriptVerifyAndClose script = new ScriptVerifyAndClose();
		//script.readFromFileAndSave(StartUp.config.at("workingDir").asString() + "/" + 24557709 + "-before.json");
		//script.run();
		//script.runForOne(1311l);
		//script.runForOne(24557709l);
		script.runInParallel(10, 30);
		
		//BOntology bo = new BOntology(getPersister().getBusinessObjectOntology(19479l));
		//System.out.println(bo.toJSON());
	}
	
	public void run()
	{
		readSpreadsheetVerifyAndClose(null, 10);
	}
	
	public void runForOne(Long srId)
	{
		verifyAndClose(srId);
	}
	
	public void runInParallel(int threadCount, int recordsPerThread)
	{
		ExecutorService service = Executors.newFixedThreadPool(threadCount);
		readSpreadsheetVerifyAndClose(service, recordsPerThread);
		//service.submit(task);
	}

	public void readSpreadsheetVerifyAndClose(final ExecutorService service, final int recordPerThread)
	{
		getPersister().getStoreExt().txn( new CirmTransaction<List<Long>>()
				{
					@Override
					public List<Long> call() throws Exception
					{
						RelationalStoreExt store = getPersister().getStoreExt();
						Connection conn = store.getConnection();
						ResultSet rs = null;
						Statement stmt = null;
						try
						{
							stmt = conn.createStatement();
							//rs = stmt.executeQuery("select SUBJECT from CIRM_OWL_DATA_PROPERTY where PREDICATE = 107 and TO_DATE IS NULL and rownum <= 10 order by SUBJECT desc");
							rs = stmt.executeQuery("select SUBJECT_ID as SUBJECT from CIRM_DATA_PROPERTY_VIEW  where PREDICATE_ID = 107 and TO_DATE IS NULL and " + 
									"(dbms_lob.instr(VALUE_CLOB, 'hasServiceAnswer') > 0 " + 
									"OR " + 
									"INSTR(COALESCE(VALUE_VARCHAR, " + 
									"	VALUE_VARCHAR_LONG " + 
									"),'hasServiceAnswer') > 0) order by SUBJECT_ID asc");
							List<Long> srIds = null;
							while(rs.next())
							{
								if(srIds == null)
									srIds = new ArrayList<Long>(recordPerThread);
								srIds.add(rs.getLong("SUBJECT"));
								if(srIds.size() == recordPerThread)
								{
									final List<Long> forFixing = new ArrayList<Long>(srIds);
									if(service != null)
									{
										service.submit(new Callable<List<Long>>()
										{ 
											@Override
											public List<Long> call() throws Exception
											{
												verifyAndClose(forFixing);
												Thread.sleep(SLEEP_AFTER_FIX);
												return forFixing;
											}
										});
									}
									else
									{
											//single thread
											verifyAndClose(forFixing);
											Thread.sleep(SLEEP_AFTER_FIX);
									}
									srIds.clear();
								}else
								{
									continue;
								}
							}
							if(service != null)
							{
								service.shutdown();
								//service.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
								//System.exit(0);
							}
							
						}
						catch(SQLException e)
						{
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
						return null;
					}
				});
	}
	
	public void verifyAndClose(List<Long> srIds)
	{
		for(Long srId: srIds)
		{
			verifyAndClose(srId);
		}
	}
	
	public void verifyAndClose(Long srId)
	{
//		RelationalOWLPersister persister = getPersister();
//		BOntology bo = new BOntology(persister.getBusinessObjectOntology(srId));
//		OWLOntologyManager m = bo.getOntology().getOWLOntologyManager();
//		try
//		{
//			
//			if(bo.getDataProperty("legacy:hasOldData") != null)
//			{
//				if(WRITE_BEFORE_AND_AFTER_TO_FILE)
//				{
//					writeToFile(StartUp.config.at("workingDir").asString() + "/" + srId + "-before.json", bo);
//				}
//				Json olddata = Json.read(bo.getDataProperty("legacy:hasOldData")
//						.getLiteral());
//				if(olddata.has("hasServiceAnswer"))
//				{
//					List<Json> serviceAnswersOld = olddata.at("hasServiceAnswer").asJsonList();
//					List<Json> serviceAnswersNew = new ArrayList<Json>(); 
//					boolean isSomethingFixed = false;
//					for (Json serviceAnswer : serviceAnswersOld)
//					{
//						String dataType = serviceAnswer.at("hasServiceField")
//								.at("hasDataType").at("literal").asString();
//						if (isObjectDatatype(dataType))
//						{
//							OWLNamedIndividual serviceField = OWL.individual(serviceAnswer
//									.at("hasServiceField").at("iri").asString());
//							if(dataType.equals("CHARMULT"))
//							{
//								isSomethingFixed = fixCharMultAnswers(bo, serviceAnswersNew,
//										serviceAnswer, serviceField);
//							}else
//							{
//								String answer = serviceAnswer.at("hasAnswerValue").at("literal").asString();
//								OWLNamedIndividual answerObject = LegacyCaseImport.getAnswerObjectIndividual(serviceField, answer);
//								if(answerObject != null)
//								{
//									if(!isSomethingFixed)
//										isSomethingFixed = true;
//										
//									addAnswerToOnto(bo,serviceField,answerObject);
//								}
//								else
//								{
//									serviceAnswersNew.add(serviceAnswer);
//								}
//							}
//						}
//					}
//					if(isSomethingFixed)
//					{
//						replaceFixedAnswersFromOldData(olddata,serviceAnswersNew);
//						removeOrReplaceOldDataProperty(bo,olddata);
//						saveChanges(bo);
//						serviceAnswersOld.clear();
//						serviceAnswersNew.clear();
//					}
//					if(WRITE_BEFORE_AND_AFTER_TO_FILE)
//					{
//						writeToFile(StartUp.config.at("workingDir").asString() + "/" + srId + "-after.json", bo);
//					}
//					if(isSomethingFixed)
//					{
//						m.removeOntology(bo.getOntology());
//						bo.getOntology().getAxioms().clear();
//						bo = null;
//					}
//				}
//				if(olddata.isArray())
//					olddata.asJsonList().clear();
//				else if(olddata.isObject())
//					olddata.asJsonMap().clear();
//				
//			}
//		}catch(Throwable t)
//		{
//			writeToFile(StartUp.config.at("workingDir").asString() + "/" + srId + "-error.json", bo, t);
//		}finally
//		{
//			
//		}
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

}

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
 * pseudo code
 * for each sr with a hasOldData prop
 * 		for each serviceanswer in olddata
 * 			recheck if the answer exists by comparing the label
 * 			if match 
 * 				add serviceanswer as prop to bo
 * 				remove serviceanswer from old data
 * remove hasOldData if no longer needed 
 * @author SABBAS
 *
 */
public class ScriptHasOldDataFix
{
	public static boolean WRITE_BEFORE_AND_AFTER_TO_FILE = false;
	public static boolean SAVE_TO_DB = true;
	public static boolean READ_AFTER_SAVE = false;
	public static long SLEEP_AFTER_FIX = 0;
		
	public static void main(String[] args)
	{
		StartUp.config.set("ontologyConfigSet", "http://www.miamidade.gov/ontology#ProdConfigSet");
		ScriptHasOldDataFix script = new ScriptHasOldDataFix();
		//script.readFromFileAndSave(StartUp.config.at("workingDir").asString() + "/" + 24557709 + "-before.json");
		//script.run();
		//script.runForOne(1311l);
		//script.runForOne(24557709l);
		//script.runForOne(22902070l);
		script.runInParallel(5, 100);
		
		//BOntology bo = new BOntology(getPersister().getBusinessObjectOntology(19479l));
		//System.out.println(bo.toJSON());
	}
	
	public void run()
	{
		findAndFixSRsWithHasOldDataProperty(null, 10);
	}
	
	public void runForOne(Long srId)
	{
		fixHasOldDataProperty(srId);
	}
	
	public void runInParallel(int threadCount, int recordsPerThread)
	{
		ExecutorService service = Executors.newFixedThreadPool(threadCount);
		findAndFixSRsWithHasOldDataProperty(service, recordsPerThread);
	}

	public void findAndFixSRsWithHasOldDataProperty(final ExecutorService service, final int recordPerThread)
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
												fixHasOldDataProperty(forFixing);
												Thread.sleep(SLEEP_AFTER_FIX);
												return forFixing;
											}
										});
									}
									else
									{
											//single thread
											fixHasOldDataProperty(forFixing);
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
	
	public void fixHasOldDataProperty(List<Long> srIds)
	{
		for(Long srId: srIds)
		{
			fixHasOldDataProperty(srId);
		}
	}
	
	public void fixHasOldDataProperty(Long srId)
	{
		RelationalOWLPersister persister = getPersister();
		BOntology bo = new BOntology(persister.getBusinessObjectOntology(srId));
		OWLOntologyManager m = bo.getOntology().getOWLOntologyManager();
		try
		{
			
			if(bo.getDataProperty("legacy:hasOldData") != null)
			{
				if(WRITE_BEFORE_AND_AFTER_TO_FILE)
				{
					writeToFile(StartUp.config.at("workingDir").asString() + "/" + srId + "-before.json", bo);
				}
				Json olddata = Json.read(bo.getDataProperty("legacy:hasOldData")
						.getLiteral());
				if(olddata.has("hasServiceAnswer"))
				{
					List<Json> serviceAnswersOld = olddata.at("hasServiceAnswer").asJsonList();
					List<Json> serviceAnswersNew = new ArrayList<Json>(); 
					boolean isSomethingFixed = false;
					for (Json serviceAnswer : serviceAnswersOld)
					{
						String dataType = serviceAnswer.at("hasServiceField")
								.at("hasDataType").at("literal").asString();
						if (isObjectDatatype(dataType))
						{
							OWLNamedIndividual serviceField = OWL.individual(serviceAnswer
									.at("hasServiceField").at("iri").asString());
							if(dataType.equals("CHARMULT"))
							{
								isSomethingFixed = fixCharMultAnswers(bo, serviceAnswersNew,
										serviceAnswer, serviceField);
							}else
							{
								String answer = serviceAnswer.at("hasAnswerValue").at("literal").asString();
								OWLNamedIndividual answerObject = LegacyCaseImport.getAnswerObjectIndividual(serviceField, answer);
								if(answerObject != null)
								{
									if(!isSomethingFixed)
										isSomethingFixed = true;
										
									addAnswerToOnto(bo,serviceField,answerObject);
								}
								else
								{
									serviceAnswersNew.add(serviceAnswer);
								}
							}
						}
					}
					if(isSomethingFixed)
					{
						replaceFixedAnswersFromOldData(olddata,serviceAnswersNew);
						removeOrReplaceOldDataProperty(bo,olddata);
						saveChanges(bo);
						serviceAnswersOld.clear();
						serviceAnswersNew.clear();
					}
					if(WRITE_BEFORE_AND_AFTER_TO_FILE)
					{
						writeToFile(StartUp.config.at("workingDir").asString() + "/" + srId + "-after.json", bo);
					}
					if(isSomethingFixed)
					{
						m.removeOntology(bo.getOntology());
						bo.getOntology().getAxioms().clear();
						bo = null;
					}
				}
				if(olddata.isArray())
					olddata.asJsonList().clear();
				else if(olddata.isObject())
					olddata.asJsonMap().clear();
				
			}
		}catch(Throwable t)
		{
			writeToFile(StartUp.config.at("workingDir").asString() + "/" + srId + "-error.json", bo, t);
		}finally
		{
			
		}
	}


	private boolean fixCharMultAnswers(BOntology bo, List<Json> serviceAnswersNew,
			Json serviceAnswer, OWLNamedIndividual serviceField)
	{
		boolean isSomethingFixed = false;
		if(serviceAnswer.at("hasAnswerValue").isArray())
		{
			Json answerJson = Json.array();
			for(Json answerValue: serviceAnswer.at("hasAnswerValue").asJsonList())
			{
				String answer = answerValue.at("literal").asString();
				OWLNamedIndividual answerObject = LegacyCaseImport.getAnswerObjectIndividual(serviceField, answer);
				if(answerObject != null)
				{
					if(!isSomethingFixed)
						isSomethingFixed = true;
					addAnswerToOnto(bo,serviceField,answerObject);
				}else
				{
					answerJson.add(answerValue);
				}
			}
			if(answerJson.asJsonList().size() > 0)
			{
				serviceAnswer.delAt("hasAnswerValue");
				serviceAnswer.set("hasAnswerValue", answerJson);
				serviceAnswersNew.add(serviceAnswer);
			}
		}else
		{
			Json answerValue =  serviceAnswer.at("hasAnswerValue");
			String[] values = answerValue.at("literal").asString().split(",");
			StringBuilder newValues = new StringBuilder();
			for(String v : values)
			{
				OWLNamedIndividual answerObject = LegacyCaseImport.getAnswerObjectIndividual(serviceField, v);
				if(answerObject != null)
				{
					addAnswerToOnto(bo,serviceField,answerObject);
				}else
				{
					newValues.append(v).append(",");
				}
			}
			if(newValues.length() > 0)
			{
				newValues.deleteCharAt(newValues.length() - 1);
				answerValue.delAt("literal");
				answerValue.set("literal", newValues.toString());
				serviceAnswer.delAt("hasAnswerValue");
				serviceAnswer.set("hasAnswerValue", answerValue);
				serviceAnswersNew.add(serviceAnswer);
			}
		}
		return isSomethingFixed;
	}

	private void removeOrReplaceOldDataProperty(BOntology bo, Json olddata)
	{
		bo.deleteDataProperty(bo.getBusinessObject(), "legacy:hasOldData");
		if(!olddata.asJsonMap().isEmpty())
		{
			bo.addDataProperty(bo.getBusinessObject(), OWL.dataProperty("legacy:hasOldData"), 
					Json.object()
					.set("literal", olddata.toString())
					.set("type",
							"http://www.w3.org/2001/XMLSchema#string"));
		}
	}

	private void replaceFixedAnswersFromOldData(Json olddata,
			List<Json> answersToFix)
	{
		olddata.delAt("hasServiceAnswer");
		if(!answersToFix.isEmpty())
		{
			olddata.set("hasServiceAnswer", answersToFix);
		}
	}

	private boolean isObjectDatatype(String dataType)
	{
		return dataType.equals("CHARLIST") || dataType.equals("CHARMULT") || dataType
				.equals("CHAROPT");
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

	private void addAnswerToOnto(BOntology bo, OWLNamedIndividual serviceField,
			OWLNamedIndividual answerObject)
	{
		OWLIndividual serviceAnswer = bo.addObjectProperty(bo.getBusinessObject(), "legacy:hasServiceAnswer", 
				Json.object().set("type", "ServiceAnswer"));
		bo.addObjectProperty(serviceAnswer, "legacy:hasServiceField", Json.object()
																		.set("iri",serviceField.getIRI().toString()));
		bo.addObjectProperty(serviceAnswer, "legacy:hasAnswerObject", Json.object()
				.set("iri",answerObject.getIRI().toString()));
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

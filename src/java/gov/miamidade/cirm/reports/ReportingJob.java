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
package gov.miamidade.cirm.reports;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import org.apache.commons.io.FileUtils;
import org.sharegov.cirm.StartUp;

/**
 * Runs the new version of the reporting job with error handling. 
 * @author SABBAS
 *
 */
public class ReportingJob
{
	public static boolean DISABLE_EXPORT_META_DATA = false;
	public static boolean DISABLE_RUN_BATCH_JOB = false;

	public static boolean SYSOUT_TO_FILE = false;
	public static boolean CLEAR_ONTO_DIR = false;
	public static boolean USE_FILE_ONTOS = false;
	
	public final static String BATCH_JOB_DIR = "batchjob";
	public final static String DATA_DIR = "data"; 
	public final static String ONTO_DB_DIR = "ontodb";
	public final static String LOG_FILE= "batchjob.log";
	
	private String baseWorkingDirectory;
	private String batchJobCommand;
	private String deploymentEndpoint;
	
	public static final ReportingJob TEST = new ReportingJob()
	{
		{
			
			StartUp.config.set("ontologyConfigSet", "http://www.miamidade.gov/ontology#TestConfigSet");
			setBaseWorkingDirectory("C:/Work/cirmservices_etl_test");
			setBatchJobCommand("ReportingJobControl_Test2Test_0.2/ReportingJobControl_Test2Test/ReportingJobControl_Test2Test_run.bat");
			setDeploymentEndpoint("http://cirm.miamidade.gov");
		}
	};
	

	public static final ReportingJob PROD = new ReportingJob()
	{
		{
			StartUp.config.set("ontologyConfigSet", "http://www.miamidade.gov/ontology#ProdConfigSet");
			setBaseWorkingDirectory("C:/Work/cirmservices_etl_prod");
			setBatchJobCommand("ReportingJobControl_Prod2Prod_0.2/ReportingJobControl_Prod2Prod/ReportingJobControl_Prod2Prod_run.bat");
			setDeploymentEndpoint("https://311hub.miamidade.gov");
		}
	};
	
	
	public static void main(String args[])
	{
		
		
		if(args.length < 1)
			throw new IllegalArgumentException("Please specify command line argument ['test' or 'prod']");
		try
		{
			if("test".equals(args[0]))
				TEST.run();
			else if("prod".equals(args[0]))
				PROD.run();
			else
				throw new IllegalArgumentException("Please specify command line argument ['test' or 'prod']");
		}catch(RuntimeException e)
		{
			e.printStackTrace(System.out);
			System.exit(1);
		}
		
		System.exit(0);
		
		
	}
	
	public void run()
	{
		try
		{
			if(SYSOUT_TO_FILE)
			{
				PrintStream ps = new PrintStream(getLogFile());
				System.setOut(ps);
				System.setErr(ps);
			}
			if (!DISABLE_EXPORT_META_DATA) 
			{
				refreshOntology();
				exportMetaData();
			}
			if (!DISABLE_RUN_BATCH_JOB) runBatchJob();
		}
		catch (Throwable e)
		{
			throw new RuntimeException(e);
		}
		
	}
	
	private void exportMetaData()
	{
		try
		{
			ReportingMetadataExport export = new ReportingMetadataExport();
			export.setExportDirectory(getDataDirectory() + File.separator);
			export.execute();
		} catch (Throwable t)
		{
			throw new RuntimeException(t);
		}
	}

	/**
	 * Takes the latest ontology from a deployed operational environment
	 * and copies it to the local ontology directory.
	 * @throws IOException 
	 */
	private void refreshOntology() throws IOException
	{
		
//		OntoAdmin localAdmin = new OntoAdmin();
//		Json localOntos = localAdmin.listOntologies(); 
//		
//		Json deployedVersion = GenUtils.
		if(USE_FILE_ONTOS)
		{
			StartUp.config.set("_metaDatabaseLocation", getOntoDBDirectory());
		}
		else 
		{
			if(CLEAR_ONTO_DIR)
			{
				FileUtils.cleanDirectory(new File(getOntoDBDirectory()));
			}
			StartUp.config.set("metaDatabaseLocation", getOntoDBDirectory());
		}
		
	}

	/**
	 * This method executes a java class created with Talend Data Integration Suite 5.1
	 * java reflect api is used to execute it as to not create a compile time dependency between this(CiRMServices) project
	 * and the libs needed by the Talend Job to run. The job itself wipes out the reporting DB
	 * and refreshes it with the latest ontology data + operations DB data. Dependent libs are 
	 * exported by the ETL development tool and are listed below.
	 * 
	 *  The list of dependent jars are:
	 *  reportingbatchjob_0_1.jar (ETL jar'd lib)
	 *  advancedPersistentLookupLib-1.0.jar
	 *  commons-collections-3.2.jar
	 *  javacsv.jar
	 *  jboss-serialization.jar
	 *  log4j-1.2.15.jar
	 *  ojdbc5-11g.jar
	 *  systemRoutines.jar
	 *  talend_file_enhanced_20070724.jar
	 *  tns.jar
	 *  trove.jar
	 *  userRoutines.jar
	 *  xdb.jar
	 *  xmlparserv2.jar
	 *  
	 *  These need to be loaded by the runtime in order for this method to work. There is an additional
	 *  dependency on the oracle client because the job internally uses sqlldr.exe to load data.
	 * 
	 */
	private void runBatchJobOld()
	{
		try
		{
//			Object batchJob = Class.forName(getBatchJobClass()).newInstance();
//			Method runJobMethod = batchJob.getClass().getMethod("runJob", String[].class);
//			String [] context= new String[] {
//					"--context_param workingDir="+ getWorkingDirectory()
//			/**		,"--context_param reportingDBUrl="+config.at("reportingDBUrl").asString()
//					,"--context_param reportingDBUser=" + config.at("reportingDBUser").asString()
//					,"--context_param reportingDBPwd=" + config.at("reportingDBPwd").asString()
//					,"--context_param dbUrl=" + config.at("dbUrl").asString()
//					,"--context_param dbUser=" + config.at("dbUser").asString()
//					,"--context_param dbPwd=" + config.at("dbPwd").asString()
//					,"--context_param ldapBaseDN=" + config.at("ldapBaseDN").asString()
//					,"--context_param ldapHost=" + config.at("ldapHost").asString()
//					,"--context_param ldapPort=" + config.at("ldapPort").asString()
//					,"--context_param ldapUser=" + config.at("ldapUser").asString()
//					,"--context_param ldapPwd=" + config.at("ldapPwd").asString()**/
//					};
//			
//			runJobMethod.invoke(batchJob, (Object)context);
			
		}
		catch (Throwable e)
		{
			throw new RuntimeException(e);
		}
	}

	private void runBatchJob()
	{
		Runtime rt = Runtime.getRuntime();
		try
		{
			//No dir, no params needed.
			String cmdFile = getBatchJobDirectory() + File.separator + getBatchJobCommand(); 
			final Process jobProcess = rt.exec(cmdFile);
			Thread normal_tRunJob_1 = new Thread() {
				public void run() {
					try {
						java.io.BufferedReader reader = new java.io.BufferedReader(
								new java.io.InputStreamReader(
										jobProcess.getInputStream()));
						String line = "";
						try {
							while ((line = reader.readLine()) != null) {
								System.out.println(line);
							}
						} finally {
							reader.close();
						}
					} catch (java.io.IOException ioe) {
						ioe.printStackTrace();
					}
				}
			};
			normal_tRunJob_1.start();

			final StringBuffer errorMsg_tRunJob_1 = new StringBuffer();
			Thread error_tRunJob_1 = new Thread() {
				public void run() {
					try {
						java.io.BufferedReader reader = new java.io.BufferedReader(
								new java.io.InputStreamReader(
										jobProcess.getErrorStream()));
						String line = "";
						try {
							while ((line = reader.readLine()) != null) {
								errorMsg_tRunJob_1.append(line).append(
										"\n");
							}
						} finally {
							reader.close();
						}
					} catch (java.io.IOException ioe) {
						ioe.printStackTrace();
					}
				}
			};
			error_tRunJob_1.start();

			// 0 indicates normal termination
			int jobProcessReturnCode = jobProcess.waitFor();
			normal_tRunJob_1.join(10000);
			error_tRunJob_1.join(10000);
			System.out.println("jobProcess completed with error code: " + jobProcessReturnCode);
			
		} catch (IOException e)
		{
			e.printStackTrace();
		} catch (InterruptedException e) 
		{
			e.printStackTrace();
		}
	}

	public String getDataDirectory() 
	{
		return getBaseWorkingDirectory() + File.separator + DATA_DIR;
	}
	
	public String getOntoDBDirectory() 
	{
		return getBaseWorkingDirectory() + File.separator + ONTO_DB_DIR;
	}

	public String getBatchJobDirectory() 
	{
		return getBaseWorkingDirectory() + File.separator + BATCH_JOB_DIR;
	}

	public File getLogFile() 
	{
		return new File(getDataDirectory() + File.separator + LOG_FILE);
	}

	public String getBaseWorkingDirectory()
	{
		return baseWorkingDirectory;
	}

	public void setBaseWorkingDirectory(String baseWorkingDirectory)
	{
		this.baseWorkingDirectory = baseWorkingDirectory;
	}

	public String getBatchJobCommand()
	{
		return batchJobCommand;
	}

	public void setBatchJobCommand(String batchJobCommand)
	{
		this.batchJobCommand = batchJobCommand;
	}

	public String getDeploymentEndpoint()
	{
		return deploymentEndpoint;
	}

	public void setDeploymentEndpoint(String deploymentEndpoint)
	{
		this.deploymentEndpoint = deploymentEndpoint;
	}

}

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
package org.sharegov.cirm.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import mjson.Json;
/**
 * 
 * Class that uses jmx remote monitoring and polls a host:port
 * to gather cpu and memory utilization.
 *  
 * @author SABBAS
 *
 */
public class ServerMonitorClient extends Thread{
	
	private String hostname;
	private String port;
	private MBeanServerConnection connection;
	private JMXConnector connector;
	private Json stats;
	private Long pollPeriod = 3000l;
	private Long samples = 10l;
	
	private File statsFile;
	private File csvFile;
	
	public ServerMonitorClient() {
		this.hostname = "s0020284";
		this.port = "9010";
		this.statsFile = new File("C:/Work/cirmservices/server-stats.json");
		try
		{
		this.init();
		}catch (Exception e) {
			e.printStackTrace(System.err);
		}
	}
	
	public ServerMonitorClient(String hostname, String port, String file) throws Exception {
		this.hostname = hostname;
		this.port = port;
		this.statsFile = new File(file);
		this.init();
	}
	
	public ServerMonitorClient(String hostname, String port, String file, String csvFile) throws Exception {
		this(hostname, port, file);
		this.csvFile = new File(csvFile);
	}

	public ServerMonitorClient(String hostname, String port, String file,
			String csvFile, long pollPeriod, long samples) throws Exception{
		this(hostname, port, file,csvFile);
		this.pollPeriod = pollPeriod;
		this.samples = samples;
		
	}

	public void init()
			throws IOException {
		Integer portInteger = Integer.valueOf(port);
		JMXServiceURL address = new JMXServiceURL(
				"service:jmx:rmi:///jndi/rmi://" + hostname + ":" + port
						+ "/jmxrmi");
		connector = JMXConnectorFactory.connect(address, null);
		connection = connector.getMBeanServerConnection();
	}

	public void remotegc() throws Exception {
		ObjectName memoryMXBean = new ObjectName("java.lang:type=Memory");
		connection.invoke(memoryMXBean, "gc", null, null);
	}

	private Json getOperatingSystemDetails() throws Exception {
		Json result = Json.object();
		result.set("host", hostname);
		ObjectName operatingSystemMXBean = new ObjectName(
				"java.lang:type=OperatingSystem");
		AttributeList list =  connection.getAttributes(operatingSystemMXBean, new String[]{
				 "SystemLoadAverage",
				 "FreePhysicalMemorySize",
				 "ProcessCpuTime",
				 "CommittedVirtualMemorySize",
				 "FreeSwapSpaceSize",
				 "TotalPhysicalMemorySize",
				 "TotalSwapSpaceSize",
				 "Name",
				 "Version",
				 "Arch",
				 "AvailableProcessors"
				});
		
		for(Attribute a : list.asList())
		{
			result.set(a.getName(), a.getValue());
		}
		return result;
	}

	private Json getHeapMemoryUsage() throws Exception {
		Json result = Json.object();
		ObjectName memoryMXBean = new ObjectName("java.lang:type=Memory");
		CompositeDataSupport dataSenders = (CompositeDataSupport) connection
				.getAttribute(memoryMXBean, "HeapMemoryUsage");
		if (dataSenders != null) {
			Long committed = (Long) dataSenders.get("committed");
			Long init = (Long) dataSenders.get("init");
			Long max = (Long) dataSenders.get("max");
			Long used = (Long) dataSenders.get("used");
			Long percentage = ((used * 100) / max);
			result.set("committed", committed)
				.set("init", init)
				.set("max", max)
				.set("used", used)
				.set("percentage", percentage);
		}
		return result;
	}

	private Json getNonHeapMemoryUsage() throws Exception {
		Json result = Json.object();
		ObjectName memoryMXBean = new ObjectName("java.lang:type=Memory");
		CompositeDataSupport dataSenders = (CompositeDataSupport) connection
				.getAttribute(memoryMXBean, "NonHeapMemoryUsage");
		if (dataSenders != null) {
			Long committed = (Long) dataSenders.get("committed");
			Long init = (Long) dataSenders.get("init");
			Long max = (Long) dataSenders.get("max");
			Long used = (Long) dataSenders.get("used");
			Long percentage = ((used * 100) / max);
			result.set("committed", committed)
			.set("init", init)
			.set("max", max)
			.set("used", used)
			.set("percentage", percentage);
		}
		return result;
	}
	
	private Json getStats() throws Exception
	{
		
		return Json.object()
				.set("OperatingSystem", getOperatingSystemDetails())
				.set("HeapMemoryUsage", getHeapMemoryUsage())
				.set("NonHeapMemoryUsage", getNonHeapMemoryUsage());
	}
	
	public void run()
	{
		System.out.println("ServerMonitorClient is running.");
		int sampleCount = 0;
		boolean interrupted = isInterrupted();
		while(!interrupted)
		{
			try
			{
				if(sampleCount >= samples)
				{	
					interrupt();
				}
				Long nanoBefore = System.nanoTime();
				Json statsBefore;
				if(stats == null)
				{
					stats = Json.array();
					statsBefore = getStats();
					stats.add(statsBefore.set("time", System.currentTimeMillis()).set("CpuLoad", 0.0));
				}else
				{
					statsBefore = stats.at(stats.asList().size() - 1);
				}
				sleep(pollPeriod);
				Json statsAfter = getStats();
				Long nanoAfter = System.nanoTime();
				statsAfter.set("time", System.currentTimeMillis());
				statsAfter.set("CpuLoad", 
						calculateCpuLoad(nanoBefore, nanoAfter
								, statsBefore.at("OperatingSystem").at("ProcessCpuTime").asLong()
								, statsAfter.at("OperatingSystem").at("ProcessCpuTime").asLong()));
				writeCSVRow(statsAfter, sampleCount == 0);
				stats.add(statsAfter);
				sampleCount++;
			}catch(InterruptedException e)
			{
				interrupted = true;
			}catch(Exception e)
			{
				e.printStackTrace(System.err);
			}
		}
	}
	
	
	private Double calculateCpuLoad(Long nanoBefore, Long nanoAfter, Long cpuBefore, Long cpuAfter)
	{
//		System.out.println("------------------------------");
//		System.out.println("nanoBefore" + nanoBefore);
//		System.out.println("nanoAfter" + nanoAfter);
//		System.out.println("cpuBefore" + cpuBefore);
//		System.out.println("cpuAfter" + cpuAfter);
//		System.out.println("(cpuAfter - cpuBefore)" + (cpuAfter - cpuBefore));
//		System.out.println("(nanoAfter - nanoBefore)" + (nanoAfter - nanoBefore));
		double x = (cpuAfter - cpuBefore);
		double y = (nanoAfter - nanoBefore);
		double r = .0;
		if(y > 0)
				r =	x/y;
//		System.out.println("ratio" + r);
		return (r>1)?1.0d:r;
	}
	
	private void write() {
		if(statsFile != null && stats != null)
		{
			FileWriter writer;
			try {
				writer = new FileWriter(statsFile);
				writer.write(stats.toString());
				writer.flush();
				writer.close();
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}
	
	private void writeCSV(){
		if(csvFile != null && stats != null)
		{
			try {
				
				boolean headersWritten = false;
				FileWriter writer = new FileWriter(csvFile);
				for(Json stat: stats.asJsonList())
				{
					StringBuilder csvRow = new StringBuilder();
					StringBuilder header = new StringBuilder();
					for(Map.Entry<String, Json> properties :  stat.asJsonMap().entrySet())
					{
						String key = properties.getKey();
						Json value = properties.getValue();
						if(value.isObject())
						{
							for(Map.Entry<String, Json> prop :  value.asJsonMap().entrySet())
							{
								String k = prop.getKey();
								String v = prop.getValue().toString();
								csvRow.append("\"").append(v.toString().replaceAll(",", "\\,").replaceAll("\"", "")).append("\"").append(",");
								if(!headersWritten)
									header.append("\"").append(key).append("-").append(k.toString().replaceAll(",", "\\,")).append("\"").append(",");
							}
						}else{
							if(!headersWritten)
								header.append("\"").append(key.toString().replaceAll(",", "\\,")).append("\"").append(",");
							csvRow.append("\"").append(value.toString().replaceAll(",", "\\,")).append("\"").append(",");
						}
					}
					csvRow.deleteCharAt(csvRow.length() - 1);
					csvRow.append("\n");
					if(!headersWritten)
					{
						header.deleteCharAt(header.length() - 1);
						header.append("\n");
						writer.write(header.toString());
						headersWritten = true;
					}
					writer.write(csvRow.toString());
				}
				writer.flush();
				writer.close();
			} catch (Exception e) {
				e.printStackTrace(System.err);
			}

		}
		
		
	}
	
	private void writeCSVRow(Json row, boolean writeHeader){
		if(row != null)
		{
			try {
				
				FileWriter writer = new FileWriter(csvFile, !writeHeader);
				StringBuilder csvRow = new StringBuilder();
				StringBuilder header = new StringBuilder();
				for(Map.Entry<String, Json> properties :  row.asJsonMap().entrySet())
				{
					String key = properties.getKey();
					Json value = properties.getValue();
					if(value.isObject())
					{
						for(Map.Entry<String, Json> prop :  value.asJsonMap().entrySet())
						{
							String k = prop.getKey();
							String v = prop.getValue().toString();
							csvRow.append("\"").append(v.toString().replaceAll(",", "\\,").replaceAll("\"", "")).append("\"").append(",");
							if(writeHeader)
								header.append("\"").append(key).append("-").append(k.toString().replaceAll(",", "\\,")).append("\"").append(",");
						}
					}else{
						if(writeHeader)
							header.append("\"").append(key.toString().replaceAll(",", "\\,")).append("\"").append(",");
						csvRow.append("\"").append(value.toString().replaceAll(",", "\\,")).append("\"").append(",");
					}
				}
				csvRow.deleteCharAt(csvRow.length() - 1);
				csvRow.append("\n");
				if(writeHeader)
				{
					header.deleteCharAt(header.length() - 1);
					header.append("\n");
					writer.write(header.toString());
				}
				writer.write(csvRow.toString());
				writer.flush();
				writer.close();
			} catch (Exception e) {
				e.printStackTrace(System.err);
			}

		}
		
		
	}

	protected void stopClient() {
		interrupt();
		//write();
		//writeCSV();
 	}
	
	
	/**
	 * 
	 * java -classpath .\classes;.\lib\mjson-1.2.jar org.sharegov.cirm.utils.ServerMonitorClient s0020269 9010 "c:\work\cirmservices\server-stats.json" "c:\work\cirmservices\server-stats.csv"
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		if(args.length < 6)
		{
			System.out.println("Usage:");
			System.out.println("java -classpath .\\classes;.\\lib\\mjson-1.2.jar org.sharegov.cirm.utils.ServerMonitorClient arg[0] arg[1] arg[2] arg[3] arg[4] arg[5]");
			System.out.println("\t\t arg[0] - the server hostname to be monitored - i.e. s0020269");
			System.out.println("\t\t arg[1] - the server port which jmx is running");
			System.out.println("\t\t arg[2] - the fullpath where to store the monitor json file");
			System.out.println("\t\t arg[3] - the fullpath where to store the monitor csv file");
			System.out.println("\t\t arg[4] - the poll period, how often to poll the server in seconds");
			System.out.println("\t\t arg[5] - how many total samples to take.");
			System.exit(0);
		}
		String hostname = args[0];
		String port = args[1];
		String file = args[2];
		String cFile = args[3];
		String pollPeriod = args[4];
		String samples = args[5];
		final ServerMonitorClient client = new ServerMonitorClient(hostname, port, file, cFile, Long.parseLong(pollPeriod) * 1000, Long.parseLong(samples));
		//client.setDaemon(true);
		client.start();
		Runtime.getRuntime().addShutdownHook(new Thread(){
			public void run(){
				client.stopClient();	
			}});
	}
}

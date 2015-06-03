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
package gov.miamidade.cirm.other;

/**
 * Holds all configuration properties needed for CSR-ServiceDirect bidirectional
 * communication via JMS. 
 * 
 * @author Borislav Iordanov, Thomas Hilpold
 */
public class JMSConfig
{
	
	private String factoryClassName;
	private String channel;	
	private String queueManager;
	private String hostName;
	private int port;
	private String inQueueName;
	private String outQueueName;
	private String outReportingQueueName;
	private boolean authenticate = false;
	private String user ="";
	private String pwd ="";
	
	public boolean isAuthenticate() {
		return authenticate;
	}

	public void setAuthenticate(boolean authenticate) {
		this.authenticate = authenticate;
	}

	public String getPwd() {
		return pwd;
	}

	public void setPwd(String pwd) {
		this.pwd = pwd;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	
	public JMSConfig()
	{		
			
	}
		
	public String getFactoryClassName()
	{
		return factoryClassName;
	}
	public void setFactoryClassName(String factoryClassName)
	{
		this.factoryClassName = factoryClassName;
	}
	public String getHostName()
	{
		return hostName;
	}
	public void setHostName(String hostName)
	{
		this.hostName = hostName;
	}
	public String getInQueueName()
	{
		return inQueueName;
	}
	public void setInQueueName(String inQueueName)
	{
		this.inQueueName = inQueueName;
	}
	public String getOutQueueName()
	{
		return outQueueName;
	}
	public void setOutQueueName(String outQueueName)
	{
		this.outQueueName = outQueueName;
	}

	public String getOutReportingQueueName()
	{
		return outReportingQueueName;
	}
	
	/**
	 * Set the queue name for live reporting.
	 * @param outReportingQueueName
	 */
	public void setOutReportingQueueName(String outReportingQueueName)
	{		
		this.outReportingQueueName = outReportingQueueName;
	}

	public int getPort()
	{
		return port;
	}
	public void setPort(int port)
	{
		this.port = port;
	}
	public String getQueueManager()
	{
		return queueManager;
	}
	public void setQueueManager(String queueManager)
	{
		this.queueManager = queueManager;
	}	
	
	public String getChannel() 
	{
		return channel;
	}

	public void setChannel(String channel) 
	{
		this.channel = channel;
	}

	public String toString()
	{
		StringBuffer result = new StringBuffer();
		result.append("JMSConfig[");
		result.append("factory=");
		result.append(factoryClassName);
		result.append("; hostName=");
		result.append(hostName);
		result.append("; port=");
		result.append(port);
		result.append("; queueManager=");
		result.append(queueManager);
		result.append("; authenticate=");
		result.append(authenticate);
		if (authenticate) {
			result.append("; user=");
			result.append(user);
		}
		result.append(";\r\n inQueueName=");
		result.append(inQueueName);
		result.append("; outQueueName=");
		result.append(outQueueName);
		result.append("; outReportingQueueName=");
		result.append(outReportingQueueName);
		result.append("]");
		return result.toString();
	}

}

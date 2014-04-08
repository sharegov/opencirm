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

import java.util.Properties;

/**
 * <p>
 * Holds all configuration properties needed for CSR-ServiceDirect bidirectional
 * communication via JMS. 
 * </p>
 * 
 * @author Borislav Iordanov
 */
public class JMSConfig
{
	public static final String CONNECTION_FACTORY_PROP = "jms-factory";
	public static final String HOSTNAME = "hostname";
	public static final String PORT = "port";
	public static final String QUEUE_MANAGER_NAME = "queue-manager";
	public static final String CHANNEL = "channel";
	public static final String TOCSR_QUEUE_NAME = "tocsr-queue";
	public static final String FROMCSR_QUEUE_NAME = "fromcsr-queue";
	
	private String factoryClassName;
	private String channel;	
	private String queueManager;
	private String hostName;
	private String port;
	private String inQueueName;
	private String outQueueName;
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

	/**
	 * Make sure all needed connection properties are present.
	 * @return <code>null</code> if everything is alright or an error message if not.
	 */
	public static String validate(Properties props)
	{
		if (props.getProperty(CONNECTION_FACTORY_PROP) == null)
			return "No connection factory specified, please use 'jms-factory' property";
		else if (props.getProperty(HOSTNAME) == null)
			return "No hostname specified, please use 'hostname' property";
		else if (props.getProperty(PORT) == null)
			return "No port number specified, please use 'port' property";		
		else if (props.getProperty(QUEUE_MANAGER_NAME) == null)
			return "No queue manager specified, please use 'queue-manager' property";			
		else if (props.getProperty(TOCSR_QUEUE_NAME) == null)
			return "No 'tocsr' queue name specified, please use 'tocsr-queue' property";
		else if (props.getProperty(FROMCSR_QUEUE_NAME) == null)
			return "No 'fromcsr' queue specified, please use 'fromcsr-queue' property";		
		else
			return null;
	}
	
	public JMSConfig()
	{		
			
	}
	
	public JMSConfig(Properties props)
	{		
		this.setFactoryClassName(props.getProperty(CONNECTION_FACTORY_PROP));
		this.setChannel(props.getProperty(CHANNEL));
		this.setHostName(props.getProperty(HOSTNAME));
		this.setPort(props.getProperty(PORT));
		this.setQueueManager(props.getProperty(QUEUE_MANAGER_NAME));
		this.setInQueueName(props.getProperty(TOCSR_QUEUE_NAME));
		this.setOutQueueName(props.getProperty(FROMCSR_QUEUE_NAME));
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
	public String getPort()
	{
		return port;
	}
	public void setPort(String port)
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
		result.append(this.factoryClassName);
		result.append(";hostName=");
		result.append(hostName);
		result.append(";port=");
		result.append(port);
		result.append(";queueManager=");
		result.append(queueManager);
		result.append("; tocsrQueue=");
		result.append(inQueueName);
		result.append("; fromcsrQueue=");
		result.append(outQueueName);
		result.append("]");
		return result.toString();
	}
}

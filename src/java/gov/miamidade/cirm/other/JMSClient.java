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

import static mjson.Json.object;
import static org.sharegov.cirm.utils.GenUtils.ok;

import gov.miamidade.cirm.other.LegacyMessageType;

import java.io.PrintStream;
import java.util.Calendar;
import java.util.UUID;
import java.util.logging.Level;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.legacy.MessageManager;
import org.sharegov.cirm.utils.GenUtils;
import com.ibm.mq.jms.JMSC;
import com.ibm.mq.jms.MQDestination;
import com.ibm.mq.jms.MQQueueConnectionFactory;

public class JMSClient
{
	private PrintStream err = System.err, out = System.out; 
	private JMSConfig jmsConfig = getConfig();
	private String clientId = "311HUB_JMS_CLIENT";
	private QueueConnectionFactory factory = null;
	private QueueConnection connection = null;
	private QueueSession session = null;
	private Queue inQueue, outQueue;
	private boolean isopen = false;
	
	/**
	 * Ask the time server to call us back in "delayInMInutes",
	 * at /departments/sendMessage with the given message to send.
	 */
	private static Json queueAtTimeServer(Json message, int delayInMinutes)
	{
		Json thisService = OWL.toJSON((OWLIndividual)Refs.configSet.resolve().get("OperationsRestService"));
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.MINUTE, delayInMinutes);
		Json task = Json.object().
		  set("name",UUID.randomUUID().toString()).
		  set( "group","cirmservicesRetry").
		  set( "scheduleType","SIMPLE").
		  set("startTime", object()
			.set("day_of_month", cal.get(Calendar.DATE))
			.set("month", cal.get(Calendar.MONTH) + 1)
			.set("year", cal.get(Calendar.YEAR))
			.set("hour", cal.get(Calendar.HOUR_OF_DAY))
			.set("minute", cal.get(Calendar.MINUTE))
			.set("second", cal.get(Calendar.SECOND))
		 ).
		 set( "endTime","").
		 set("state", "NORMAL").
		 set("description","this is the description").
		 set("restCall", Json.object().
		    set("url", thisService.at("hasUrl").asString() + "/legacy/departments/sendMessage").
		    set("method", "POST").
		    set("content", Json.object().set("a", 5).set("s", "hello").set("A", Json.array())).
		    set("retry", Json.object().set("retryInterval", 10))
		);
		Json timeMachine = OWL.toJSON((OWLIndividual)Refs.configSet.resolve().get("TimeMachineConfig"));		
		return ok().set("task", GenUtils.httpPostJson(timeMachine.at("hasUrl").asString() + "/task", task));				
	}

	private static Json makeMessage(LegacyMessageType type, long transactionId, Json data)
	{
		return Json.object("messageType", type.toString(), 
				"transactionId", transactionId,
				"data", data);		
	}
	
	private static Json makeResponse(Json originalMessage, Json response)
	{
		return Json.object(
				"messageType", LegacyMessageType.Response.toString(), 
				"transactionId", originalMessage.at("transactionId"),
				"originalMessage", originalMessage,
				"response", response);
	}
	
	public static void connectAndSend(Json msg) throws JMSException
	{
		Refs.logger.resolve().info("Sending message to interface.");
		try
		{
			JMSClient jmsClient = new JMSClient();
	    	try
	    	{	    	
	    		jmsClient.open();
	    		TextMessage textmsg = jmsClient.session.createTextMessage();
	    		textmsg.setText(msg.toString());
	    		System.out.println("Send to JMS: " + msg.toString());
	    		jmsClient.send(textmsg, jmsClient.outQueue);	
	    	}
	    	finally
	    	{
	    		jmsClient.close();
	    	}
		}
		catch (JMSException jmsex)
		{
			Json queued = JMSClient.queueAtTimeServer(msg, 60);
			if (!queued.is("ok", true))
				throw new RuntimeException("While queueing JMS message at Time Server: " + queued.at("error"));
			throw jmsex;
		}
		catch (Throwable t)
		{
			MessageManager.get().sendEmail("cirm-no-reply@miamidade.gov", "assia@miamidade.gov", 
					"JMS Failed to send, CiRM", msg.toString());
			Refs.logger.resolve().log(Level.SEVERE, "Error in send method", t);
			Refs.logger.resolve().log(Level.SEVERE, "While sending data to JMS queue", msg.toString());
		}
	}
	
	public static void connectAndSend(LegacyMessageType type, long transactionId, Json data) throws JMSException
	{
		connectAndSend(makeMessage(type, transactionId, data));
	}	
	
	public static void connectAndRespond(Json originalMessage, Json response) throws JMSException
	{
		connectAndSend(makeResponse(originalMessage, response));
	}
	
	private void open()
	{
		try
		{
			if (factory == null)
				factory = (QueueConnectionFactory)Class.forName(jmsConfig.getFactoryClassName()).newInstance();
			// Dependency on MQ Series API here... this avoids us the need to configure a JNDI resource.
			// Also, the County's messaging system has been standartized to MQ Series so there is very
			// little chance of a JMS implementation change in the future.
			MQQueueConnectionFactory mqFactory  = (com.ibm.mq.jms.MQQueueConnectionFactory)factory;			
			mqFactory.setTransportType(com.ibm.mq.jms.JMSC.MQJMS_TP_CLIENT_MQ_TCPIP);
			mqFactory.setHostName(jmsConfig.getHostName());
			mqFactory.setPort(Integer.parseInt(jmsConfig.getPort()));
			mqFactory.setQueueManager(jmsConfig.getQueueManager());
			mqFactory.setChannel(jmsConfig.getChannel());
			if(jmsConfig.isAuthenticate())
				connection = factory.createQueueConnection(jmsConfig.getUser(),jmsConfig.getPwd());
			else
				connection = factory.createQueueConnection();
			connection.setClientID(clientId);
			session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
			inQueue = session.createQueue(jmsConfig.getInQueueName());
			if(inQueue instanceof MQDestination)
			{
				((MQDestination)inQueue).setTargetClient(JMSC.MQJMS_CLIENT_NONJMS_MQ);
			}
			outQueue = session.createQueue(jmsConfig.getOutQueueName());
			isopen = true;
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}		
	}
	
	private JMSConfig getConfig()
	{
		
		OWLNamedObject x = Refs.configSet.resolve().get("OperationsQueue");
		OWLNamedIndividual info = OWL.individual(x.getIRI()); // OWL.individual("CIRMTestQueueConnection"); 
		OWLNamedIndividual queueType = OWL.objectProperty(info, "hasQueueType");
		String factoryClassName = OWL.dataProperty(queueType, "hasFactoryClassName").getLiteral();
		String url = OWL.dataProperty(info, "hasUrl").getLiteral();
		String channel = OWL.dataProperty(info, "hasChannel").getLiteral();
		String queueManager = OWL.dataProperty(info, "hasQueueManager").getLiteral();
		String outQueueName = OWL.dataProperty(info, "hasOutQueueName").getLiteral();
		String inQueueName = OWL.dataProperty(info, "hasInQueueName").getLiteral();
		String port = OWL.dataProperty(info, "hasPort").getLiteral();
		JMSConfig config = new JMSConfig();
		config.setFactoryClassName(factoryClassName);
		config.setChannel(channel);
		config.setQueueManager(queueManager);
		config.setInQueueName(inQueueName);
		config.setOutQueueName(outQueueName);
		config.setPort(port);
		config.setHostName(url);
		return config;
	}

	/**
	 * <p>
	 * This method will close all open resource without throwing an exception. All
	 * exceptions that occur during the process will be printed out to the <code>err</code>
	 * stream.
	 * </p>
	 */
	public void close()
	{
		try { if (session != null) session.close(); }
		catch (Throwable t) { t.printStackTrace(err); }
		finally { session = null; }
		
		try { if (connection != null) connection.close(); }
		catch (Throwable t) { t.printStackTrace(err); }
		finally { connection = null; }
		isopen = false;
	}
	
	public void send(Message msg, Queue queue) throws JMSException
	{
		QueueSender sender = null;
		try
		{
			sender = session.createSender(queue);
			sender.send(msg);
		}		
		finally
		{
			if (sender != null) 
				sender.close(); 
		}		
	}

	
	public void send(LegacyMessageType type, long transactionId, Json data) throws JMSException
	{
		Json jmsg = makeMessage(type, transactionId, data);
		TextMessage msg = session.createTextMessage();
		msg.setText(jmsg.toString());
		System.out.println("Send to JMS: " + jmsg.toString());
		send(msg, outQueue);	
	}
	
	public void respond(Json originalMessage, Json response) throws JMSException
	{
		Json jmsg = makeResponse(originalMessage, response);
		if (response.has("data"))
			jmsg.set("data", response.atDel("data"));
		TextMessage msg = session.createTextMessage();
		msg.setText(jmsg.toString());
		send(msg,outQueue);			
	}
	
	public void postInCirmToLegacy(String text) throws JMSException
	{
		TextMessage msg = session.createTextMessage();
		msg.setText(text);
		send(msg, outQueue);
	}

	public void postInCirmToLegacy(Message msg) throws JMSException
	{
		send(msg, outQueue);
	}
	
	public void postInLegacyToCirm(String text) throws JMSException
	{
		TextMessage msg = session.createTextMessage();
		msg.setText(text);
		send(msg, inQueue);
	}

	public void postInLegacyToCirm(Message msg) throws JMSException
	{
		send(msg, inQueue);
	}

	public Message receive(long timeout, Queue queue) throws JMSException
	{
		QueueReceiver receiver = null;
		try
		{
			receiver = session.createReceiver(queue);
			return receiver.receive(timeout);
		}
		finally
		{
			if (receiver != null)
				receiver.close();
		}		
	}
	
	public Message fromIn(long timeout) throws JMSException
	{
		return receive(timeout, inQueue);
	}

	public Message fromOut(long timeout) throws JMSException
	{
		return receive(timeout, outQueue);
	}
	
	public JMSConfig getJmsConfig()
	{
		return jmsConfig;
	}

	public void setJmsConfig(JMSConfig jmsConfig)
	{
		this.jmsConfig = jmsConfig;
	}	
	
	public void setErr(PrintStream err)
	{
		if (err != null)
			this.err = err;
		else
			this.err = System.err;
	}
	
	public PrintStream getErr()
	{
		return err;
	}
	
	public void setOut(PrintStream out)
	{
		if (out != null)
			this.out = out;
		else
			this.out = System.out;
	}
	
	public PrintStream getOut()
	{
		return out;
	}
	
	public void setClientId(String clientId)
	{
		this.clientId = clientId;
	}
	
	public String getClientId()
	{
		return clientId;
	}

	
	public boolean isOpen()
	{
		return isopen;
	}
}

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
package org.sharegov.cirm.legacy;

import static org.sharegov.cirm.OWL.dataProperty;
import static org.sharegov.cirm.OWL.objectProperty;
import static org.sharegov.cirm.OWL.reasoner;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.jms.JMSException;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import mjson.Json;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.rest.LegacyEmulator;
import org.sharegov.cirm.utils.MessageTemplateUtil;
import org.sharegov.cirm.utils.NotificationPreference;
import org.sharegov.cirm.utils.SrJsonSorter;
import org.sharegov.cirm.utils.SrJsonUtil;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;
import org.sharegov.cirm.utils.xpath.Context;
import org.sharegov.cirm.utils.xpath.Resolver;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import de.odysseus.staxon.json.JsonXMLInputFactory;
import gov.miamidade.cirm.other.JMSClient;

/**
 * MessageManager is responsible to creating email and sms CirmMessages through it's template engine.<br>
 * <br>
 * It is also capable of dispatching CirmMessages in a generalized manner, by utilizing smtp or our SMSService class.<br>
 * <br>
 * Message creation through applying the service request against SR type configured templates normally happens inside 
 * of a transaction. A Cirm transaction may need to retry in 311Hub to complete.<br>
 * <br>
 * Message sending is normally only triggered after the CirmTransaction was successful, to ensure messages are only sent once, 
 * even if multiple retries were needed in high db load situations for the transaction to complete.<br>
 * <br>
 * For special variables CITIZENS_EMAIL, and CITIZENS_CELL_PHONE for texting, the citizen's messaging preference is also evaluated in this class.<br>
 * <br>
 * Template based emails normally contain an explanation as white text inside the email to allow analysis (which trigger, template, etc).<br>
 * <br>
 * For our test systems, a test mode sends emails and sms to a configured test user group.<br>
 * Citizen notification testing or demoing by sending to the actual user entered email or cell phone is possible, 
 * if a template ONLY defines the citizen as recipient and the citizen's messaging preferences on intake allow.<br>
 * <br>
 * <br>
 * Details:<br>
 * It uses a json to DOM bridge to leverage xPath and XSL when evaluating
 * variables. The only non-java* dependency is on staxon api to create a Dom
 * from json.
 * <br>
 * Call reinit() after an ontology change that affects MessageManagerConfiguration.
 * <br>
 * This class is Thread Safe. 
 * <br>
 * @author SABBAS, hilpold
 * 
 */
public class MessageManager
{
	public static boolean DBG = true;
	public static boolean DBGVARS = false;
	public static boolean DBGXML = false;
	public static boolean DISABLE_SEND = false;
	private static boolean INCLUDE_EXPLANATION_IN_EMAIL = true;
	public static boolean USE_IBM_MQ = true;
	/**
	 * Send test emails to the DEFAULT_TO_ADDRESS instead of 
	 * 
	 * Effective only in test mode.
	 */
	private static boolean TEST_MODE_USE_DEFAULT_TO = true; 

	/**
	 * For templates that have CITIZEN_EMAIL or for SMS only CITIZEN_CELL_PHONE 
	 * as ONLY recipient in hasTo AND hasCc templates, send to the citizen also (Add to the test user hasTo default).
	 * This allows any user to test our citizen email/sms capability using his own email/cell in our test systems.
	 * (Set to false eg. for interface closing tests after a db restore from prod to test.)
	 */
	private static boolean TEST_MODE_SENDS_TO_CITIZEN = true; 

	
	private static boolean FORCE_TEST_RECOVERY = false;

	/**
	 * Uses hasLegacyBody for all templates
	 * TODO should be disabled in production 
	 */
	private static boolean ALWAYS_USE_LEGACY_BODY = false;

	/**
	 * In production mode, emails get sent to real users. 
	 * resolved to and cc fields of the templates.
	 */
	public static final String MODE_PRODUCTION = "MessageManagerProductionMode";

	/**
	 * In test mode, emails get sent to BCC users as to. 
	 * Resolved to and cc fields of the templates will be included in the body of the message.
	 */
	public static final String MODE_TEST = "MessageManagerTestMode";

	/**
	 * Used when to-field has no addresses to avoid exception.
	 */
	public static final String DEFAULT_TO_ADDRESS = "hilpold@miamidade.gov;sanchoo@miamidade.gov";
	public static final String DEFAULT_TO_CELLPHONE = "9543001632";
	public static final String SR_DOM_ROOT_NODE = "sr";
	public static final Pattern VAR_NAME_PATTERN = Pattern.compile("\\$\\$(.*?)\\$\\$");
	public static Logger logger = Refs.logger.resolve();
	
	private volatile ConcurrentMap<String, Object> messageVariables;
	private volatile Properties configurationProperties;
	
	private volatile Session session = null;// <prop key="mail.smtp.host">smtp.miamidade.gov</prop>
	private volatile boolean initialized = false;
	
	/**
	 * This is the sender address the Messagemanager uses to send emails.
	 */
	private volatile InternetAddress messageSenderAddress;
	
	private volatile SmsService smsService;
	
	private final MessageTemplateUtil mtUtil = new MessageTemplateUtil();
	
	private final SrJsonUtil srUtil = new SrJsonUtil();
	
	private static class MessageManagerHolder {
		private static MessageManager instance = new MessageManager();
	}
	
	public static MessageManager get()
	{
		return MessageManagerHolder.instance;
	}
	
	/**
	 * If this is False, all emails go to real users!
	 * @param messageTemplate
	 * @return
	 */
	public boolean isTestMode()
	{
		ensureInit();
		String mode = configurationProperties.getProperty("mode");
		if (MODE_TEST.equals(mode)) 
			return true;
		else if (MODE_PRODUCTION.equals(mode))
			return false;
		else 
		{
			System.err.println("MessageManager: No mode configured, was " + mode + " should be " + MODE_PRODUCTION + " or " + MODE_TEST);
			System.err.println("MessageManager: Will use " + MODE_TEST + ", but please update ontology!");
			return true;
		}
	}

	/**
	 * 
	 * @param sr : The sr to be transformed and emailed
	 * @param messageTemplate : The email message template defined in ontology.
	 * @param subject : The subject of the email
	 * @param to : The TO recipients of the email
	 * @param cc : The CC recipients of the email
	 * @param bcc : The BCC recipients of the email
	 * @return
	 */
	private CirmMimeMessage createMimeMessage(Json sr, OWLNamedIndividual messageTemplate, 
			String subject, String to, String cc, String bcc, String comments)
	{
		if (DBG) System.out.println("MessageManager: createMessage for: \r\n "+ (sr.has("iri")? sr.at("iri"): sr));
		ensureInit();
		CirmMimeMessage msg = null;
		try {
			OWLLiteral bodyTemplate = dataProperty(messageTemplate, "legacy:hasBody");
			//OWLLiteral bodyTemplate = dataProperty(messageTemplate, "legacy:hasLegacyBody"); //TODO FOR COM TEST ONLY
			Map<String, String> parameters = fillParameters(sr, null, null, null, null, 
					bodyTemplate != null ? bodyTemplate.getLiteral() : null);
			if(comments != null )
				parameters.put("$$COMMENTS$$", "<Strong>Comments: </Strong>"+comments);
			String body = null;
			if (bodyTemplate != null)
				body = applyTemplate(bodyTemplate.getLiteral(), parameters);
			msg = new CirmMimeMessage(session);
			if (to != null) 
				msg.setRecipients(Message.RecipientType.TO, toRecipients(to));
			if (cc != null)
				msg.setRecipients(Message.RecipientType.CC, toRecipients(cc));
			if (bcc != null)
				msg.setRecipients(Message.RecipientType.BCC, toRecipients(bcc));
			if(subject != null)
				msg.setSubject(subject, "UTF-8");
			if(body != null)
				msg.setContent(body, "text/html; charset=UTF-8");
			msg.setFrom(messageSenderAddress);
			msg.setSentDate(new java.util.Date());
			//creation explanation
			msg.addExplanation("email createMessage" + messageTemplate.getIRI().getFragment());
		}
		catch (Exception e)
		{
			String boid = "";
			if (sr.at("boid") != null)
			{
				boid = sr.at("boid").asString();
			}
			throw new IllegalStateException("Could not create emailMessage for sr" + boid + " with template "
					+ messageTemplate, e);
		}
		return msg;
	}

	/**
	 * 
	 * @param sr
	 *            - The sr to be transformed.
	 * @param legacyCode
	 *            - the legacy code for this message variable, essentially the
	 *            owner code.
	 * @param messageTemplate
	 *            - The email message template defined in the ontology.
	 * @return
	 */
	private CirmMimeMessage createMimeMessage(Json sr, String legacyCode, OWLNamedIndividual messageTemplate)
	{
		if (DBG) System.out.println("MessageManager: createMessage for: \r\n "+ (sr.has("iri")? sr.at("iri"): sr));
		ensureInit();
		boolean useLegacyBody = useLegacyBody(sr);
		CirmMimeMessage msg = null;
		try
		{
			OWLNamedIndividual messageTemplateClass = OWL.individual("legacy:MessageTemplate");			
			OWLLiteral bccTemplate = dataProperty(messageTemplateClass, "legacy:hasBCC");
			OWLLiteral bccTestTemplate = dataProperty(messageTemplateClass, "legacy:hasBCCTest");
			OWLLiteral highPriorityText = dataProperty(messageTemplateClass, "legacy:hasHighPriorityText");
			
			OWLLiteral toTemplate = dataProperty(messageTemplate, "legacy:hasTo");
			OWLLiteral ccTemplate = dataProperty(messageTemplate, "legacy:hasCc");
			OWLLiteral subjectTemplate = dataProperty(messageTemplate, "legacy:hasSubject");
			OWLLiteral bodyTemplate; 
			if (useLegacyBody)
				bodyTemplate = dataProperty(messageTemplate, "legacy:hasLegacyBody");
			else
				bodyTemplate = dataProperty(messageTemplate, "legacy:hasBody");
			OWLLiteral hasHighPriority = dataProperty(messageTemplate, "legacy:hasHighPriority");	
			boolean highPriority = (hasHighPriority !=null && hasHighPriority.isBoolean() && hasHighPriority.parseBoolean());
			String toTemplateLiteral = toTemplate != null ? toTemplate.getLiteral() : null;
			String ccTemplateLiteral = ccTemplate != null ? ccTemplate.getLiteral() : null;
			// citizen messaging preference processing
			boolean citizenIsOnlyRecipient = false;
			if(toTemplateLiteral != null && mtUtil.hasCitizenEmailRecipient(toTemplateLiteral)) {
				NotificationPreference msgPref = srUtil.getCitizenNotificationPreference(sr);
				if(msgPref != null && !msgPref.isEmailOk()) {
					//System.out.println(msgPref.getLabel());
					toTemplateLiteral = mtUtil.removeCitizenEmailRecipient(toTemplateLiteral);
				}
				citizenIsOnlyRecipient = mtUtil.hasOnlyCitizenEmailRecipient(toTemplateLiteral);
			}
			if(ccTemplateLiteral != null && mtUtil.hasCitizenEmailRecipient(ccTemplateLiteral)) {
				NotificationPreference msgPref = srUtil.getCitizenNotificationPreference(sr);
				if(msgPref != null && !msgPref.isEmailOk()) {
					ccTemplateLiteral = mtUtil.removeCitizenEmailRecipient(ccTemplateLiteral);
				}
				citizenIsOnlyRecipient = mtUtil.hasOnlyCitizenEmailRecipient(ccTemplateLiteral);
			}
			//
			Map<String, String> parameters = fillParameters(sr, legacyCode, toTemplateLiteral, ccTemplateLiteral,
					subjectTemplate != null ? subjectTemplate.getLiteral() : null, bodyTemplate != null ? bodyTemplate
							.getLiteral() : null);
			String to = null;
			String cc = null;
			String subject = null;
			String body = null;
			String bcc = null;
			String bccTest = null;
			if (toTemplate != null)
				to = applyTemplate(toTemplateLiteral, parameters);
			if (ccTemplate != null)
				cc = applyTemplate(ccTemplateLiteral, parameters);
			if (subjectTemplate != null)
				subject = applyTemplate(subjectTemplate.getLiteral(), parameters);
			if (bodyTemplate != null)
			{
				//TODO String bodyTemplateStr = useLegacyBody? legacyBodyToHTML(bodyTemplate) : bodyTemplate.getLiteral();  
				String bodyTemplateStr = bodyTemplate.getLiteral();
				boolean htmlTemplate = bodyTemplateStr.contains("$$GLOBAL_SR_TEMPLATE") || bodyTemplateStr.startsWith("<html>"); 
				if (htmlTemplate)
				{
					formatMultiLineValueStringsAsHtml(parameters);
				} 
				else 
				{ 
					bodyTemplateStr = legacyBodyToHTML(bodyTemplateStr);
				}					
				body = applyTemplate(bodyTemplateStr, parameters);
			}
			if (bccTemplate != null)
				bcc = applyTemplate(bccTemplate.getLiteral(), parameters);
			if (bccTestTemplate != null)
				bccTest = applyTemplate(bccTestTemplate.getLiteral(), parameters);
			//if (true || to != null || cc != null)
			if (to != null || cc != null || bcc != null)
			{
				msg = new CirmMimeMessage(session);
				//InternetAddress sender = new InternetAddress("cirmtest@miamidade.gov");
				// TODO: Test mode: For now send to some default recipients.
				if (highPriority) 
					body = highPriorityText.getLiteral() + body; 
				//
				// We only send to real recipients in production mode, 
				// but include resolved to and cc field in test emails' bodies.
				//
				if (isTestMode())
				{
					//testmode do not send to real recipients, but bcc as to only
					body = getTestModeHeader(to, cc, messageTemplate) + body;
					if (TEST_MODE_USE_DEFAULT_TO) {
						if (TEST_MODE_SENDS_TO_CITIZEN && citizenIsOnlyRecipient) {
							to = to + ";" + DEFAULT_TO_ADDRESS;
						} else {
							to = DEFAULT_TO_ADDRESS;
						}
					} else {
						to = bccTest;
					}
					cc = null; 
					bcc = null;
				}
				if (to != null) 
					msg.setRecipients(Message.RecipientType.TO, toRecipients(to));
				else 
					msg.setRecipients(Message.RecipientType.TO, toRecipients(DEFAULT_TO_ADDRESS));
					
				if (cc != null)
					msg.setRecipients(Message.RecipientType.CC, toRecipients(cc));
				if (bcc != null)
					msg.setRecipients(Message.RecipientType.BCC, toRecipients(bcc));
				if (subject == null)
					subject = "No subject defined (in template)!";
				if (body == null)
					body = "No body defined (in template)!";
				msg.setFrom(messageSenderAddress);
				msg.setSentDate(new java.util.Date());
				msg.setSubject(subject, "UTF-8");
				msg.setContent(body, "text/html; charset=UTF-8");				
				msg.saveChanges();
				if (highPriority) 
					msg.setHeader("X-Priority", "1");
				//creation explanation
				msg.addExplanation("createMessage" + messageTemplate.getIRI().getFragment());
			}
		}
		catch (Exception e)
		{
			String boid = "";
			if (sr.at("boid") != null)
			{
				boid = sr.at("boid").asString();
			}
			throw new IllegalStateException("Could not create message for sr" + boid + " with template "
					+ messageTemplate, e);
		}
		return msg;
	}
	
	/**
	 * Internal Method to create an Sms message. Respects Citizen messaging preference.
	 * @param sr
	 *            - The sr to be transformed.
	 * @param legacyCode
	 *            - the legacy code for this message variable, essentially the
	 *            owner code.
	 * @param messageTemplate
	 *            - The email message template defined in the ontology.
	 * @return
	 */
	private CirmSmsMessage createSmsMessage(Json sr, String legacyCode, OWLNamedIndividual messageTemplate)
	{
		if (DBG) System.out.println("MessageManager: createSmsMessage for: \r\n "+ (sr.has("iri")? sr.at("iri"): sr));
		ensureInit();
		boolean useLegacyBody = useLegacyBody(sr);
		CirmSmsMessage msg = null;
		try
		{
			//OWLNamedIndividual messageTemplateClass = OWL.individual("legacy:MessageTemplate");			
			OWLLiteral toTemplate = dataProperty(messageTemplate, "legacy:hasTo");
			OWLLiteral bodyTemplate; 
			String toTemplateLiteral = toTemplate != null ? toTemplate.getLiteral() : null;
			boolean citizenIsOnlyRecipient = false;
			if (mtUtil.hasCitizenCellPhoneRecipient(toTemplateLiteral)) {
				NotificationPreference msgPref = srUtil.getCitizenNotificationPreference(sr);
				if(msgPref == null || !msgPref.isSmsOk()) {
					toTemplateLiteral = mtUtil.removeCitizenCellPhoneRecipient(toTemplateLiteral);
				}
				citizenIsOnlyRecipient = mtUtil.hasOnlyCitizenCellPhoneRecipient(toTemplateLiteral);				
			}
			if (useLegacyBody)
				bodyTemplate = dataProperty(messageTemplate, "legacy:hasLegacyBody");
			else
				bodyTemplate = dataProperty(messageTemplate, "legacy:hasBody");
			String bodyTemplateLiteral = bodyTemplate != null ? bodyTemplate.getLiteral() : null;
			Map<String, String> parameters = fillParameters(sr, legacyCode, toTemplateLiteral, bodyTemplateLiteral);
			String to = null;
			String body = null;
			if (toTemplate != null)
				to = applyTemplate(toTemplateLiteral, parameters);
			if (bodyTemplate != null)
			{
				String bodyTemplateStr = bodyTemplate.getLiteral();
				body = applyTemplate(bodyTemplateStr, parameters);
			}
			if (to != null)
			{
				msg = new CirmSmsMessage();
				//
				// We only send to real recipients in production mode, 
				// but include resolved to 
				//
				if (isTestMode())
				{
					if (TEST_MODE_USE_DEFAULT_TO) {
						if (TEST_MODE_SENDS_TO_CITIZEN && citizenIsOnlyRecipient) {
							to = to + ";" + DEFAULT_TO_CELLPHONE;
						} else {
							to = DEFAULT_TO_CELLPHONE;
						}
					} else {
						to = "7869732464";
					}
				}
				if (to != null) { 
					msg.setPhone(to);
				} else {
					msg.setPhone(DEFAULT_TO_CELLPHONE);
				}
					
				if (body == null)
					body = "No body defined (in template)!";
				msg.setTxt(body);				
			}
		}
		catch (Exception e)
		{
			String boid = "";
			if (sr.at("boid") != null)
			{
				boid = sr.at("boid").asString();
			}
			throw new IllegalStateException("Could not create sms message for sr" + boid + " with template "
					+ messageTemplate, e);
		}
		return msg;
	}

	/**
	 * Formats all non-html multi-line string values as html by adding a <br> before each \r.
	 * @param parameters
	 */
	private void formatMultiLineValueStringsAsHtml(Map<String, String> parameters) {
		Set<String> keys = new HashSet<String>(parameters.keySet());
		for (String key : keys) {
			String value = parameters.get(key);
			//If string is multiline and does not contain html or xml tags
			if (value != null  && !containsHtmlOrXmlTags(value)) {
				if (value.contains("\r\n")) {
					String newValue = value.replaceAll("\r\n", "<br>\r\n");
					parameters.put(key, newValue);
				} else if (value.contains("\n")) {
					String newValue = value.replaceAll("\n", "<br>\n");
					parameters.put(key, newValue);
				} else {
					//single line string, no CRLF or LF, ignore
				}
			}
		}
	}
	
	/**
	 * Determines if a string contains any html or xml tags.
	 * @param s
	 * @return true if any html or xml end tag was detected
	 */
	private boolean containsHtmlOrXmlTags(String s) {
		return s.contains("</") || s.contains("/>");
	}

	public String getTestModeHeader(String to, String cc, OWLNamedIndividual messageTemplate) 
	{
		if (to != null && to.contains("@")) 
		{
			if (DBG) System.err.println("MessageManager: test mode: not sending to: " + to);
			return  "<b>This is test mode, the following email would be sent to : </b> <br/> \r\n" 
					+ to + " <br/> \r\n " 
					+ "as CC: " + cc + " <br/> \r\n "
					+ "and was created by template: " + messageTemplate.getIRI().toString() + "</br>\r\n<hr>\r\n";
					//+ body; 
		} else
		{
			if (DBG) System.err.println("MessageManager: test mode: to was null: " + to);
			return "<b>This is test mode, but a problem occured: No addresses were resolved for this email : </b> <br/> \r\n" 
					+ to + " <br/> \r\n " 
					+ "as CC: " + cc + " <br/> \r\n " 
					+ "and was created by template: " + messageTemplate.getIRI().toString() + "</br>\r\n<hr>\r\n";
					 
		}
	}
	
	private InternetAddress[] toRecipients(String recipientStr)
	{
		if (recipientStr == null) return new InternetAddress[0];
		String[] tos = recipientStr.trim().split(";");
		List<InternetAddress> recipients = new ArrayList<InternetAddress>();
		for (int i = 0; i < tos.length; i++)
			try {
				if (tos[i].contains("@")) {
					recipients.add(new InternetAddress(tos[i]));
				} else {
					ThreadLocalStopwatch.now("toRecipients: Not adding invalid recipient " + tos[i] + " to message");
				}				
			} catch (AddressException e)
			{
				System.err.println(ThreadLocalStopwatch.getThreadName() 
						+ " MessageManager: AddressException when trying to create InternetAddress to: " + tos[i] 
								+ " (which is one of: " + recipientStr + ")");
			}
		return recipients.toArray(new InternetAddress[0]);
	}

	/**
	 * Gets BO individual from verbose ontology. Throws exception, if it is not BO.
	 * Format expected:
	 * http://www.miamidade.gov/bo/MAYECC/2228124/verbose#bo
	 * 
	 * @param verboseBO
	 * @return the original bo individual
	 */
	public OWLNamedIndividual getNonVerboseBO(BOntology verboseBO) 
	{
		//http://www.miamidade.gov/bo/MAYECC/2228124/verbose#bo -->http://www.miamidade.gov/bo/MAYECC/2228124#bo
		OWLOntology verboseO = verboseBO.getOntology();
		OWLDataFactory df = verboseBO.getOntology().getOWLOntologyManager().getOWLDataFactory();
		OWLNamedIndividual verboseBOObj = verboseBO.getBusinessObject();
		String verboseBOIRIStr = verboseBOObj.getIRI().toString();
		int indexVerbose = verboseBOIRIStr.indexOf("/verbose");
		int indexHashbo = verboseBOIRIStr.indexOf("#bo");
		if (indexVerbose < 0) throw new RuntimeException("Expected to find /verbose in " + verboseBOObj.getIRI().toString());
		if (indexHashbo < 0) throw new RuntimeException("Expected to find #bo in " + verboseBOObj.getIRI().toString());
		String bOIRIStr = verboseBOIRIStr.substring(0, indexVerbose) + "#bo";
		OWLNamedIndividual result = df.getOWLNamedIndividual(IRI.create(bOIRIStr));
		if (!verboseO.containsEntityInSignature(result)) throw new IllegalStateException("Bo does not contain: " + result);
		return result;
	}
	
	public boolean isVerboseBO(BOntology bo)
	{
		return bo.getBusinessObject().getIRI().toString().contains("/verbose");
	}
	
	public CirmMimeMessage createMimeMessageFromTemplate(BOntology o, OWLLiteral legacyCode, OWLNamedIndividual emailTemplate)
	{
			CirmMimeMessage msg = null;
			try
			{
				logger.log(Level.INFO, "Creating email for sr:" + o.getObjectId() + " legacyCode: " + legacyCode + " tmpl: " + emailTemplate.getIRI().getFragment() );
				OWLNamedIndividual boInd;
				String boIndID;
				if (isVerboseBO(o))
				{
					boInd = getNonVerboseBO(o);
					boIndID = getNonVerboseBOObjectId(boInd);
				}
				else
				{
					boInd = o.getBusinessObject();
					boIndID = o.getObjectId();
				}
					
				Json sr = OWL.toJSON(o.getOntology(), boInd);
				sr.set("boid", boIndID);
				msg = createMimeMessage(sr, legacyCode.getLiteral(), emailTemplate);
				if (FORCE_TEST_RECOVERY)
					msg.addRecipient(RecipientType.CC, new InternetAddress("$$variable$$"));
				
			} catch(Exception e)
			{
				//String errmsg = "Could not send email for sr:" + o.getObjectId() + " legacyCode: " + legacyCode + " template:" + emailTemplate.getIRI().getFragment();
				String errmsg = "Could not create email for sr:" + o.getObjectId() + " legacyCode: " + legacyCode + " template:" + emailTemplate.getIRI().getFragment();
				logger.log(Level.WARNING, errmsg, e);
				//tryRecoverSendException(msg, e, emailTemplate);
			}		
			return msg;
	}

	/**
	 * Creates a CirmSmsMessage using an ontology configured template.
	 * @param o
	 * @param legacyCode
	 * @param smsTemplate
	 * @return
	 */
	public CirmSmsMessage createSmsMessageFromTemplate(BOntology o, OWLLiteral legacyCode, OWLNamedIndividual smsTemplate)
	{
			CirmSmsMessage msg = null;
			try
			{
				logger.log(Level.INFO, "Creating sms for sr:" + o.getObjectId() + " legacyCode: " + legacyCode + " tmpl: " + smsTemplate.getIRI().getFragment() );
				OWLNamedIndividual boInd;
				String boIndID;
				if (isVerboseBO(o))
				{
					boInd = getNonVerboseBO(o);
					boIndID = getNonVerboseBOObjectId(boInd);
				}
				else
				{
					boInd = o.getBusinessObject();
					boIndID = o.getObjectId();
				}
					
				Json sr = OWL.toJSON(o.getOntology(), boInd);
				sr.set("boid", boIndID);
				msg = createSmsMessage(sr, legacyCode.getLiteral(), smsTemplate);
			} catch(Exception e)
			{
				String errmsg = "Could not create email for sr:" + o.getObjectId() + " legacyCode: " + legacyCode + " template:" + smsTemplate.getIRI().getFragment();
				logger.log(Level.WARNING, errmsg, e);
			}		
			return msg;
	}

	public CirmMimeMessage createMimeMessageFromTemplate(BOntology o, 
			OWLNamedIndividual emailTemplate, 
			String subject, String to, String cc, String bcc,String comments)
	{
		CirmMimeMessage msg = null;
		try
		{
			logger.log(Level.INFO, 
					"User sending email of sr:" + o.getObjectId() + 
					" tmpl: " + emailTemplate.getIRI().getFragment() );
			OWLNamedIndividual boInd = o.getBusinessObject();
			String boIndID = o.getObjectId();
			Json sr = OWL.toJSON(o.getOntology(), boInd);
			SrJsonSorter sorter = new SrJsonSorter();
			sorter.sortSr(sr);
			sr.set("boid", boIndID);
			msg = createMimeMessage(sr, emailTemplate, subject, to, cc, bcc, comments);
		}
		catch(Exception e)
		{
			String errmsg = "Could not send email of sr:" + o.getObjectId() +
					" template:" + emailTemplate.getIRI().getFragment();
			logger.log(Level.WARNING, errmsg, e);
		}
		return msg;
	}

	/**
	 * Sends all messages in List, tries a recovery message on exception with template information.
	 * A recovery email will contain a detailed creation explanation of the message.
	 * @param messages
	 */
	public void sendMessages(List<CirmMessage> messages) 
	{
		for (CirmMessage message : messages)
		{
			if (message instanceof CirmSmsMessage) {
				message.addExplanation("<br>Msg generated on server: " + getServerShortName());
				sendSMS((CirmSmsMessage) message);
			} else if (message instanceof CirmMimeMessage) {
				CirmMimeMessage mimeMessage = (CirmMimeMessage) message;
    			try 
    			{
    				if (INCLUDE_EXPLANATION_IN_EMAIL) 
    				{
    					mimeMessage.addExplanation("<br>Msg generated on server: " + getServerShortName());
    					mimeMessage.includeExplanationInBody();
    				}
    				sendEmail(mimeMessage);
    			} catch (Exception e)
    			{
    				tryRecoverSendException(mimeMessage, e);
    			}
			} else {
				ThreadLocalStopwatch.error("error message type not implemented " + message);
			}
		}
	}
	
	
	/**
	 * Tries to recover from a send message failure if caused by a SendFailedException.
	 * A recovery email will carry information on failed email addresses and it's subject is suffixed by "[R]".
	 * 
	 * @param msg the failing msg
	 * @param e the excaption causing a failed send
	 * @param emailTemplate
	 * @param errmsg
	 */
	private void tryRecoverSendException(CirmMimeMessage msg, Exception e)
	{
		SendFailedException sfx = findSendFailedException(e);
		if (sfx != null)
		{
			Address[] invalid = sfx.getInvalidAddresses();
			Address[] validSent = sfx.getValidSentAddresses();
			Address[] validUnSent = sfx.getValidUnsentAddresses();
			String subject = "unknownSubject";
			try {
				subject = msg.getSubject();
			} catch (Exception se) {};
			logger.log(Level.WARNING, "Failed email " + subject + "\r\nSTART INVALID\r\n" + toStr(invalid) + "END INVALID. \r\n" + " RESEND TO INVALID MANUALLY.");
			logger.log(Level.WARNING, "Failed email explanation:" + msg.getExplanation() + " \r\n Email was successfully sent to \r\n" + toStr(validSent));
			if (validUnSent != null && validUnSent.length > 0)
			{
				logger.log(Level.WARNING, "Attempting to send recovery email to valid unsent: \r\n" + toStr(validUnSent) + "...");
				CirmMimeMessage recoveryMsg = new CirmMimeMessage(session);
				try {
					String body = (String) msg.getContent();
					body = body + "<br><br><br><br>For Sysadmin: <br> Recovered message could not be send to: " + toStr(invalid);
					body = body + "<br>Initial send success to : " + toStr(validSent);
					body = body + "<br>Initial valid but not sent to : " + toStr(validUnSent);
					recoveryMsg.setContent(body, msg.getContentType());
					recoveryMsg.setSubject(msg.getSubject() + " [R]", "UTF-8");
					for (Address valid : validUnSent) 
						recoveryMsg.addRecipient(RecipientType.TO, valid);
					recoveryMsg.setFrom(messageSenderAddress);
					sendEmail(recoveryMsg);
					logger.log(Level.WARNING, "... recovery email sent.");
				} catch (Exception ex) 
				{
					logger.log(Level.WARNING, "... RECOVERY FAILED. (with " + ex.getClass() + ")");
				}
			} else 
			{
				logger.log(Level.WARNING, "Failed email NO RECOVERY POSSIBLE.");
			}
		}
	}

	private String toStr(Address[] arr) 
	{
		if (arr == null || arr.length == 0) return "None";
		String result ="";
		for (Address a :arr) 
			result += a.toString() + "(Type:" + a.getType() + ");\r\n";
		return result;
	}
	
	/**
	 * Finds a SendFailedException in e's cause chain.
	 * @param e
	 * @return
	 */
	private SendFailedException findSendFailedException(Exception e) 
	{
		if (e == null) return null;
		Throwable t = e;
		while (!(t instanceof SendFailedException) && t.getCause() != null)
		{
			t = t.getCause();
		}
		if (!(t instanceof SendFailedException)) {
		    return null;
		} else {
		    return (SendFailedException) t;
		}
	}

	private String getNonVerboseBOObjectId(OWLNamedIndividual boInd)
	{
		String A[] = boInd.getIRI().toString().split("/");
		String boIndIDWithHashBO = A[A.length - 1];
		int indexOfHashBO = boIndIDWithHashBO.indexOf("#bo");
		return boIndIDWithHashBO.substring(0, indexOfHashBO);
	}

	/**
	 * 
	 * 
	 */
	private Map<String, String> fillParameters(Json sr, String legacyCode, String... anything) throws Exception
	{
		long x = System.currentTimeMillis();
		HashMap<String, String> result = new HashMap<String, String>();
		Document dom = null;
		for (String thing : anything)
		{
			if (thing == null)
				continue;
			Matcher m = VAR_NAME_PATTERN.matcher(thing);
			while (m.find())
			{
				String var = m.group();
				if (result.containsKey(var))
					continue;
				Object evaluator = messageVariables.get(var);
				if (!messageVariables.containsKey(var))
				{
					OWLNamedIndividual ind = findIndividualFromVariable(var);
					if (ind != null)
					{
						Set<OWLNamedIndividual> javaResolver = reasoner().getObjectPropertyValues(ind, objectProperty("legacy:hasVariableResolver")).getFlattened();
						if(javaResolver != null && !javaResolver.isEmpty())
						{
							if(javaResolver.size() != 1)
								throw new IllegalStateException("Only one resolver can be defined per variable " + var);
							evaluator = Class.forName(javaResolver.iterator().next().getIRI().getFragment()).newInstance();
						}else
						{
							Set<OWLLiteral> xpath = reasoner().getDataPropertyValues(ind, dataProperty("hasXPathExpression"));
							Set<OWLLiteral> xsl = reasoner().getDataPropertyValues(ind, dataProperty("hasStylesheet"));
							OWLLiteral expression = null;
							if (xpath != null && !xpath.isEmpty())
							{
								expression = xpath.iterator().next();
								evaluator = compileXPath(expression.getLiteral());
							}
							else if (xsl != null && !xsl.isEmpty())
							{
								expression = xsl.iterator().next();
								evaluator = compileXSL(expression.getLiteral());
							}
						}
						if (evaluator != null) 
							messageVariables.put(var, evaluator);
						else 
							logger.warning(ThreadLocalStopwatch.getThreadName() + " MessageManager: No Evaluator for variable " + var + " (a legacy:MessageVariable)");
					}
				}else
				{
					evaluator = messageVariables.get(var);
				}
				if (evaluator != null)
				{
					Properties properties = new Properties();
					if(legacyCode != null)
						properties.setProperty("LEGACY_CODE", legacyCode);
					if (evaluator instanceof XPathExpression)
					{
						if(dom == null)
							dom = toDOM(sr);
						result.put(var, evaluate((XPathExpression) evaluator, dom));
					}
					else if (evaluator instanceof Templates)
					{
						if(dom == null)
							dom = toDOM(sr);
						result.put(var, evaluate((Templates) evaluator, dom, properties));
					}
					else if (evaluator instanceof VariableResolver)
					{
						result.put(var, evaluate(var, (VariableResolver) evaluator, sr, properties));
					}
				}
			}
		}
		logger.info("fillParameters duration " + (System.currentTimeMillis() - x) + " milliseconds.");
		return result;
	}

	private String applyTemplate(String template, Map<String, String> parameters)
	{
		if (template == null)
			return null;
		int line = 1, col = 0;
		StringBuffer result = new StringBuffer();
		for (int i = 0; i < template.length();)
		{
			if (template.charAt(i) == '\n')
			{
				line++;
				col = 0;
			}
			else
				col++;

			if (template.startsWith("$$", i))
			{
				int varEndIdx = template.indexOf("$$", i + 2);
				if (varEndIdx == -1)
					throw new IllegalStateException("Variable not properly enclosed -- missing closing $$. line="
							+ line + " col= " + col);
				String varName = template.substring(i + 2, varEndIdx);
				i += 2 + varName.length() + 2;
				String value = (String) parameters.get("$$" + varName + "$$");
				if (value == null)
					value = "$$" + varName + "$$";
				result.append(value);
			}
			else
				result.append(template.charAt(i++));
		}
		return result.toString();
	}

	public String evaluate(XPathExpression expression, Document doc)
	{
		String result = "";
		try
		{
			result = (String) expression.evaluate(doc, XPathConstants.STRING);
			return result;
		}
		catch (Exception e)
		{
			logger.log(Level.WARNING, "Could not evaluate xPath expression" + expression.toString(), e);
			throw new IllegalStateException(e);
		}
	}

	public String evaluate(Templates stylesheetTemplate, Document doc, Properties props)
	{
		String result = "";
		StringWriter writer = new StringWriter();
		try
		{
			Transformer transformer = stylesheetTemplate.newTransformer();
			transformer.transform(new DOMSource(doc), new StreamResult(writer));
			for ( Entry<Object, Object> propertyToValue : props.entrySet())
			{
				transformer.setParameter((String) propertyToValue.getKey(), propertyToValue.getValue());
			}
			result = writer.toString();
			return result;
		}
		catch (TransformerException e)
		{
			logger.log(Level.WARNING, "Could not evaluate stylesheet template", e);
			throw new IllegalStateException(e);
		}
	}
	
	public String evaluate(String variableName, VariableResolver resolver, Json sr, Properties properties)
	{
		return resolver.resolve(variableName, sr, properties);
			
	}

	public void sendEmail(String from, String to, String subject, String body)
	{
		ensureInit();
		try
		{
			if (from != null && to != null)
			{
			    CirmMimeMessage msg = new CirmMimeMessage(session);
				InternetAddress sender = new InternetAddress(from);
				String[] tos = to.trim().split(";");
				InternetAddress[] recipients = new InternetAddress[tos.length];
				for (int i = 0; i < tos.length; i++)
					recipients[i] = new InternetAddress(tos[i]);
				msg.setFrom(sender);
				msg.setRecipients(Message.RecipientType.TO, recipients);
				msg.setSentDate(new java.util.Date());
				msg.setSubject(subject, "UTF-8");
				if (containsHtmlOrXmlTags(body)) {
					msg.setContent(body, "text/html; charset=UTF-8");
				} else {
					msg.setContent(body, "text/plain; charset=UTF-8");
				}
				msg.saveChanges();
				sendEmail(msg);
			}
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
	}
	
	/**
	 * Same as {@link #sendEmail(String, String, String, String)} except the body is attached
	 * with the media type specified.
	 * 
	 * @param from
	 * @param to
	 * @param subject
	 * @param body
	 * @param mediaType
	 */
	public void emailAsAttachment(String from, String to, String subject, String body, String mediaType, String filename)
	{
		try
		{
			Multipart bodyPart = new MimeMultipart(); 
			MimeBodyPart part = new MimeBodyPart();  
	        part.setContent(body, mediaType);  
	        bodyPart.addBodyPart(part);
	        if (filename != null)
	        	part.setFileName(filename);
			sendEmail(from, to, subject, bodyPart);
		}
		catch (MessagingException ex)
		{
			throw new RuntimeException(ex);
		}
	}

	/**
	 * Sends an email.
	 * @param from mandatory
	 * @param to mandatory semicolon separated strings
	 * @param cc optional
	 * @param bcc optional
	 * @param subject
	 * @param body
	 */
	public void sendEmail(String from, String to, String cc, String bcc, String subject, Multipart body)
	{
		if (to == null) throw new IllegalArgumentException("to null");
		ensureInit();
		try
		{
		    CirmMimeMessage msg = new CirmMimeMessage(session);
			InternetAddress sender = new InternetAddress(from);
			InternetAddress[] toA = toRecipients(to);
			InternetAddress[] ccA = toRecipients(cc);
			InternetAddress[] bccA = toRecipients(bcc);
			msg.setFrom(sender);
			msg.setRecipients(Message.RecipientType.TO, toA);
			msg.setRecipients(Message.RecipientType.CC, ccA);
			msg.setRecipients(Message.RecipientType.BCC, bccA);
			msg.setSentDate(new java.util.Date());
			msg.setSubject(subject, "UTF-8");
			msg.setContent(body);
			sendEmail(msg);
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
	}

	private void sendEmail(String from, String to, String subject, Multipart body)
	{
		ensureInit();
		try
		{
			if (from != null && to != null)
			{
				CirmMimeMessage msg = new CirmMimeMessage(session);
				InternetAddress sender = new InternetAddress(from);
				String[] tos = to.trim().split(";");
				InternetAddress[] recipients = new InternetAddress[tos.length];
				for (int i = 0; i < tos.length; i++)
					recipients[i] = new InternetAddress(tos[i]);
				msg.setFrom(sender);
				msg.setRecipients(Message.RecipientType.TO, recipients);
				msg.setSentDate(new java.util.Date());
				msg.setSubject(subject, "UTF-8");
				msg.setContent(body);
				sendEmail(msg);
			}
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
	}

	private void sendEmail(CirmMimeMessage msg)
	{
		try
		{
			Address[] recipients = msg.getAllRecipients();
			if (recipients == null || recipients.length < 1) {
				ThreadLocalStopwatch.now("sendEmail: Not sending message that had zero recipients");
			} else {
				if(!DISABLE_SEND) {
				    if (USE_IBM_MQ) {
				        sendEmailViaMessageQueue(msg);
				    } else {
				        sendEmailViaTransport(msg);
				    }
					ThreadLocalStopwatch.now("sendEmail: One Email sent to " + recipients.length + " recipients");
				}
			}
		}
		catch (Exception ex)
		{
			ThreadLocalStopwatch.error("Transport error: " + ex);
			throw new RuntimeException(ex);
		}
	}
	
	private void sendEmailViaMessageQueue(CirmMimeMessage msg) throws JMSException {
	    JMSClient.connectAndSendDirect(msg);
	}

	private void sendEmailViaTransport(CirmMimeMessage msg) throws MessagingException {
        Transport.send(msg);
	}

	public boolean isInitialized()
	{
		return initialized;
	}

	public Properties getConfiguration()
	{
		return configurationProperties;
	}

	public Document toDOM(Json sr) throws Exception
	{
		try
		{
			// This is a bit inefficient but transforming directly to a
			// DOMResult throws a NPE
			// transforming to a stream first works fine.
			DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
			domFactory.setNamespaceAware(false);
			DocumentBuilder builder = domFactory.newDocumentBuilder();
			Document doc = builder.parse(new InputSource(new StringReader(toDOMString(sr))));
			return doc;
		}
		catch (Exception e)
		{
			throw e;
		}
	}

	private void addProperties(Json sr)
	{
		sr.set("typeDescription", OWL.getEntityLabel(OWL.individual("legacy:" + sr.at("type").asString())));
		sr.delAt("transient$protected");
		LegacyEmulator.replaceAnswerValuesWithLabels(sr);
	}

	
	public String toDOMString(Json sr) throws Exception
	{
		addProperties(sr);
		StringReader json = new StringReader(sr.toString());
		XMLInputFactory factory = new JsonXMLInputFactory();
		factory.setProperty(JsonXMLInputFactory.PROP_VIRTUAL_ROOT, SR_DOM_ROOT_NODE);
		try
		{
			// This is a bit inefficient but transforming directly to a
			// DOMResult throws a NPE
			// transforming to a stream first works fine.
			XMLStreamReader reader = factory.createXMLStreamReader(json);
			StringWriter writer = new StringWriter();
			TransformerFactory xformFactory = TransformerFactory.newInstance();
			Transformer idTransform = xformFactory.newTransformer();
			idTransform.setOutputProperty(OutputKeys.INDENT, "yes");
			idTransform.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			Source input = new StAXSource(reader);
			idTransform.transform(input, new StreamResult(writer));
			if (DBGXML)
			{
				System.out.println("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
				System.out.println("MessageManager.toDOMString: \r\n" + writer.getBuffer().toString());
				System.out.println("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
			}
			return writer.getBuffer().toString();
		}
		catch (Exception e)
		{
			throw e;
		}
	}

	private void ensureInit()
	{
		if (!initialized)
		{
			synchronized (this)
			{
				if (!initialized) 
				{
					try
					{
						smsService = SmsService.get();
						OWLLiteral host = OWL.dataProperty( (OWLNamedIndividual)Refs.configSet.resolve().get("SMTPConfig"), "hasValue");
						Properties newProps = new Properties();
						newProps.setProperty("mail.smtp.host", host.getLiteral());
						OWLNamedIndividual mmMode = (OWLNamedIndividual)Refs.configSet.resolve().get("MessageManagerConfig");
						if (mmMode == null) throw new IllegalArgumentException("Exactly one value needs to be configured for MessageManagerConfig");
						OWLNamedIndividual messageSender = OWL.objectProperty(mmMode, "hasEmailAddress");
						InternetAddress messageSenderAdr = new InternetAddress(messageSender.getIRI().getFragment());
						newProps = new Properties();
						newProps.setProperty("mail.smtp.host", host.getLiteral());
						newProps.setProperty("mode", mmMode.getIRI().getFragment());
						//publish to volatile
						configurationProperties = newProps;
						messageSenderAddress = messageSenderAdr;
					}
					catch (Exception e)
					{
						throw new IllegalStateException("Messagemenager initialization failure. Check SMTPConfig, MessageManagerConfig in Ontos.", e);
					}
					messageVariables = new ConcurrentHashMap<String, Object>();
					session = Session.getDefaultInstance(configurationProperties, null);
					initialized = true;
					if (!isTestMode()) 
						System.out.println("MessageManager in PRODUCTION MODE. Emails will be sent to specified to,cc and legacy:MessageTemplate.hasBcc recipients.");
					else 
						System.out.println("MessageManager in TEST MODE. Emails will be sent to legacy:MessageTemplate.hasBccTest recipients only.");
					System.out.println("MessageManager will send email using sender address: " + messageSenderAddress);
				}
			}
		}
	}
	
	
	public synchronized void reInitialize() 
	{
		initialized = false;
	}


	public Object compileXPath(String expression)
	{
		if (expression == null)
			throw new IllegalArgumentException("Cannot compile a null expression.");
		Object evaluator = null;
		XPathFactory xPathFactory = XPathFactory.newInstance();
		xPathFactory.setXPathFunctionResolver(new Resolver());
		XPath xPath = xPathFactory.newXPath();
		xPath.setNamespaceContext(new Context());
		try
		{
			evaluator = xPath.compile(expression);
			return evaluator;
		}
		catch (Exception e)
		{
			logger.log(Level.WARNING, "Could not compile  expression as xPath: " + expression, e);
		}
		throw new IllegalArgumentException("Expression could not be compiled:" + expression);
	}
	
	public Object compileXSL(String xsl)
	{
		if (xsl == null)
			throw new IllegalArgumentException("Cannot compile a null xsl.");
		Object evaluator = null;
		TransformerFactory factory = TransformerFactory.newInstance();
		try
		{

			evaluator = factory.newTemplates(new StreamSource(new StringReader(xsl)));
			return evaluator;
		}
		catch (Exception e)
		{
			logger.log(Level.WARNING, "Could not create xsl template with the following: " + xsl, e);
		}
		throw new IllegalArgumentException("Expression could not be compiled:" + xsl);
	}
	
	/**
	 * Finds a MessageVariable individual (Encoded Fragment!) for a given (non encoded) variable name (e.g.$$VAR,1$$)
	 * in the ontology.
	 *  
	 * @param var a variable name $$varname$$, not url encoded
	 * @return a matching individual 
	 * @throws UnsupportedEncodingException
	 */
	public static OWLNamedIndividual findIndividualFromVariable(String var)
	{
		try {
			OWLNamedIndividual varInd = OWL.individual("legacy:"+ URLEncoder.encode(var.replaceAll("\\$\\$", ""),"UTF-8"));
			Set<OWLNamedIndividual>  individuals = reasoner().getInstances(
						OWL.owlClass("legacy:MessageVariable"), false).getFlattened();
			if(individuals.contains(varInd))
				return varInd;
			else
				return null;
		} catch (UnsupportedEncodingException e) 
		{
			throw new RuntimeException("UTF-8 not supported?", e);
		}
	}
	
	/**
	 * Translates a non HTML legacyBody of an email template to HTML
	 * hasLegacyBody property is expected to be non HTML, but use newline and indents to format the email message.
	 * 
	 * @param legacyBody
	 * @return
	 */
	public static String legacyBodyToHTML(String legacyBody)
	{
		return "<pre>" + legacyBody + "</pre>";
	}
	
	public static boolean useLegacyBody(Json sr)
	{
		//TODO introduce data property for SR type useLegacyEmail == true
		return ALWAYS_USE_LEGACY_BODY;
	}

	private String getServerShortName() 
	{
		String result;
		try {
			result = java.net.InetAddress.getLocalHost().getHostName();
		 if (result.length() >= 4)
			 result = result.substring(result.length() - 4);
		} catch (Exception e) 
		{
			System.err.println("Error MessageManager:getServerShortName failed with " + e.toString());
			result = "Unknown";
		}
		return result;
	}
	
	/**
	 * Sends a CirmSmsMessage using a short messaging service, possibly splitting the message into multiple texts.
	 * @param smsMessage
	 */
	private void sendSMS(CirmSmsMessage smsMessage)
	{
		try{
			if (smsService == null) {
				smsService = SmsService.get();
			}
			smsService.sendSMS(smsMessage);
		} catch (Exception e)
		{
			ThreadLocalStopwatch.error("ERROR MessageManager: sendSMS failed with " + e);
			e.printStackTrace();
		}
	}
}

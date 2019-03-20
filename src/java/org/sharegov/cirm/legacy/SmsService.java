package org.sharegov.cirm.legacy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.PhoneNumberUtil;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import mjson.Json;

/**
 * SmsService singleton to send CirmSmsMessages (text messages) via twillio using https.<br>
 * <br>
 * Because the twillio service only supports https requests pertaining to a single phone number and with a max length of 100 characters, 
 * this SmsService may split long messages into multiple parts and conducts one https call per phone number in message recipient list.<br>
 * <br>
 * Configuration is read from ontology configset param "SmsConfig", which needs "hasUrl", "hasUsername" (client_id), and "hasToken" values.<br>
 * <br>
 * Splitting long messages will preserve whole words in message parts.<br>
 * <br>
 * SMS Template Configuration user may decide one or more split points for longer templates that require multiple texts using [START_NEXT] 
 * tag in body template.<br>
 * If any split text is still longer than MAX_TXT_LENGTH (160 ASCII) it will be broken into further messages.
 * <br> 
 * This class is thread safe.<br>
 * 
 * @author Camilo, Thomas Hilpold, Syed
 */
public class SmsService {
	
	public static boolean DBG = true;
	
	/**
	 * Allows multiple messages defined in one message body.
	 */
	public final static String START_NEXT = "[START_NEXT]";
	private final static String START_NEXT_PAT = Pattern.quote(START_NEXT);
	
	/**
	 * The ontology config set parameter name where the configuration for this service is expected.
	 */
	private static final String CONFIG_PARAM_NAME = "SmsConfig";
	
	private static SmsService instance;
	
	/**
	 * If true splits messages longer than MAX_TXT_LENGHT into multiple parts.
	 */
	public final static boolean USE_MAX_TXT_LENGHT = true;
	public final static int MAX_TXT_LENGHT = 160;

	private String url;
	private String clientId;
	private String token;

	/**
	 * Gets the singleton SMS service instance.
	 * @return
	 */
	public static SmsService get() {
		if (instance == null) {
			synchronized(SmsService.class) {
				if (instance == null) {
					instance = createInstanceFromConfig();
				}
			}
		}
		return instance;
	}
	
	/**
	 * Creates SMS service singleton from properties of configset param SmsConfig.
	 * @return
	 */
	private static SmsService createInstanceFromConfig() {
		OWLNamedIndividual smsServiceParam = (OWLNamedIndividual)Refs.configSet.resolve().get(CONFIG_PARAM_NAME);
		OWLLiteral urlLiteral = OWL.dataProperty(smsServiceParam, "hasUrl");
		OWLLiteral userNameLiteral = OWL.dataProperty(smsServiceParam, "hasUsername");
		OWLLiteral tokenLiteral = OWL.dataProperty(smsServiceParam, "hasToken");
		if (urlLiteral == null || userNameLiteral == null || tokenLiteral == null) {
			throw new IllegalStateException("SmsService is not configured properly. Please check ontology configuration");
		}
		return new SmsService(urlLiteral.getLiteral(), userNameLiteral.getLiteral(), tokenLiteral.getLiteral());
	}
	
	private SmsService(String url, String clientId, String token) {
		if(url == null || clientId == null || token == null) {
			throw new IllegalArgumentException("An Sms service constructor parameter was null. Were " 
					+ url + " " + clientId + " " + token);
		}
		this.url = url;
		this.clientId = clientId;
		this.token = token;
	}

	/**
	 * Gets the read-only https service url. 
	 * @return
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * Sends a text message, possibly to multiple phone numbers and, if length exceeds split into multiple sms messages.
	 * Errors handled through exception logging.
	 * 
	 * @param message
	 */
	public void sendSMS(CirmSmsMessage message) {
		String phoneMult = message.getPhone();
		String text = message.getTxt();
		if (phoneMult == null || phoneMult.isEmpty()) {
			ThreadLocalStopwatch.error("ERROR: SmsService: phone empty. Not sending message");
			ThreadLocalStopwatch.error(message.getExplanation());
			return;
		}
		if (text == null || text.isEmpty()) {
			ThreadLocalStopwatch.error("ERROR: SmsService: text empty. Not sending message");
			ThreadLocalStopwatch.error(message.getExplanation());
			return;
		}
		//check for and process [START_NEXT], also trim.
		List<String> allTexts = checkSplitTextforStartNextToken(text);
		if (allTexts.isEmpty()) {
			ThreadLocalStopwatch.error("ERROR: SmsService: checkSplitTextforStartNextToken returned 0 messages. Not sending any message");
			ThreadLocalStopwatch.error(message.getExplanation());
		}
		for (String oneText : allTexts) {
			sendSMSInternal(phoneMult, oneText);
		}
	}
	
	/**
	 * Check text for multiple defined messages using [START_NEXT] token in template body.
	 * [START_NEXT] can be used inline or in a separate line, with or without surrounding whitespace, because.
	 * each message will be trimmed and empty message will not be returned.
	 * @param text
	 * @return
	 */
	private List<String> checkSplitTextforStartNextToken(String text) {
		List<String> result = new ArrayList<>();
		String[] messages = text.split(START_NEXT_PAT);
		for (String m : messages) {
			//Filter white space only messages to also remove newline after START_NEXT while still allowing the token inline.
			m = m.trim();
			if(!m.isEmpty()) result.add(m);
		}
		return result;
	}
	
	/**
	 * Sends a text message, possibly to multiple phone numbers and, if length exceeds split into multiple sms messages.
	 * Errors handled through exception logging.
	 * 
	 * @param message
	 */
	void sendSMSInternal(String phoneMult, String text) {
		if (phoneMult == null || phoneMult.isEmpty()) {
			ThreadLocalStopwatch.error("ERROR: SmsService: phone empty. Not sending message");
			return;
		}
		if (text == null || text.isEmpty()) {
			ThreadLocalStopwatch.error("ERROR: SmsService: text empty. Not sending message");
			return;
		}
		List<String> textParts;
		if (USE_MAX_TXT_LENGHT && text.length() > MAX_TXT_LENGHT) {
			textParts = splitIntoParts(text, MAX_TXT_LENGHT);
			ThreadLocalStopwatch.now("WARNING: SmsService: text exceeded " + MAX_TXT_LENGHT
					+ " chars and was split into " + textParts.size() + " parts, original: " + text);
		} else {
			textParts = new ArrayList<>();
			textParts.add(text);
		}
		ThreadLocalStopwatch.now("SmsService: Sending SMS to " + phoneMult + " txt: " + text);

		String[] phones = phoneMult.split(";");
		int successTarget = phones.length * textParts.size();
		//ThreadLocalStopwatch.now("SmsService: msg explanation info " + message.getExplanation());
		int successActual = 0;
		for (String onePhoneNumber : phones) {
			try {
				validatePhoneNumber(onePhoneNumber);
			} catch (Exception e) {
				ThreadLocalStopwatch.error("ERROR: SmsService: invalid phone number, skipping: " + onePhoneNumber);
				continue;
			}
			for (String txtPart : textParts) {
				try {
					sendSmsToOnePhone(onePhoneNumber, txtPart);
					successActual++;
				} catch (Exception e) {
					ThreadLocalStopwatch.error("ERROR: SmsService: Error during sending, message to " + onePhoneNumber
							+ " was not sent due to " + e);
					e.printStackTrace();
				}
			}
		}
		if (successActual == 0) {
			ThreadLocalStopwatch.error("ERROR: SmsService: NO TEXT MESSAGE COULD BE SENT.");
		} else if (successActual != successTarget){
			ThreadLocalStopwatch.error("ERROR: SmsService: SOME TEXT MESSAGES COULD NOT BE SENT. Failed: " 
					+ (successTarget - successActual) +  " of " + successTarget);
		} else {
			ThreadLocalStopwatch.now("DONE: SmsService: All " + successActual + " text messages were successfully sent.");
		}
	}

	/**
	 * Validates a phone number to have exactly 10 digits.
	 * @param onePhoneNumber null allowed
	 * @throws IllegalArgumentException if null or not exactly 10 digits
	 */
	void validatePhoneNumber(String onePhoneNumber) {
		String oneOnlyDigits = PhoneNumberUtil.onlyDigits(onePhoneNumber);
		if (oneOnlyDigits.length() != 10)
			throw new IllegalArgumentException("Phone number does not contain 10 digits: " + onePhoneNumber);

	}

	/**
	 * Sends 
	 * @param onePhone
	 * @param text
	 */
	private void sendSmsToOnePhone(String onePhone, String text) {
		if (USE_MAX_TXT_LENGHT && text.length() > MAX_TXT_LENGHT) {
			throw new IllegalArgumentException("SmsService: Text exceeds max length " + MAX_TXT_LENGHT + " was " + text);
		}
		// Service requires +1 prefix, whereas 311Hub manages all phone numbers as 10 digits.
		onePhone = "+1" + onePhone;
		ThreadLocalStopwatch.now("SmsService: START https Sending one sms to phone " + onePhone + " via " + url);
		Json autorizationJObj = Json.object().set("client_id", clientId).set("token", token); 
		Json smsPayload = Json.object();
		smsPayload.set("authorization", autorizationJObj);
		smsPayload.set("phone", onePhone);
		smsPayload.set("message", text);
		Json result = GenUtils.httpPostJson(url, smsPayload);
		ThreadLocalStopwatch.now("SmsService: END https Sending one sms");
		if (result != null && !(result.isString() && result.asString().isEmpty())) {
			ThreadLocalStopwatch.now("SmsService: http result: " + result);
		}
	}
	
	/**
	 * Splits a text into multiple parts until all are smaller then maxPartLen. 
	 * Split points are attempted to be whitespace always, so each part contains whole words after splitting. 
	 * @param text 
	 * @param maxPartLen
	 * @return
	 */
	private List<String> splitIntoParts(String text, int maxPartLen) {
		List<String> textParts = new ArrayList<>();
		for(int idx = 0; idx < text.length(); idx = idx + maxPartLen) {
			String curPart = text.substring(idx, Math.min(text.length(), idx + maxPartLen));
			if (idx + maxPartLen < text.length()) {
    			int wspace = curPart.length() - 1;
    			while(!Character.isWhitespace(curPart.charAt(wspace)) && wspace > curPart.length()/2) {
    				wspace --;
    			}
    			if (Character.isWhitespace(curPart.charAt(wspace))) {
    				idx -= (curPart.length() - 1 - wspace);
    				curPart = curPart.substring(0, wspace);
    			}
			}
			if (DBG) {
				ThreadLocalStopwatch.now("SmsService: dbg split part " + textParts.size() + " : " + curPart + " len: " + curPart.length());
			}			
			textParts.add(curPart);
		}
		return textParts;
	}
}

package org.sharegov.cirm.legacy;

import org.sharegov.cirm.utils.PhoneNumberUtil;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

public class SmsService {
	
	private String token;
	private String url;
	
	
	public String getToken() {
		return token;
	}
	public void setToken(String token) {
		this.token = token;
	}
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}

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
		ThreadLocalStopwatch.now("SmsService: Sending SMS to " + phoneMult + " txt: " + text);
		ThreadLocalStopwatch.now("debug info " + message.getExplanation());
		
		String[] phones = phoneMult.split(";");
		int success = 0;
		for (String onePhoneNumber : phones) {
			try {
				validatePhoneNumber(onePhoneNumber);
			} catch(Exception e) {
				ThreadLocalStopwatch.error("ERROR: SmsService: invalid phone number, skipping: " + onePhoneNumber);
				continue;
			}
			try {
				sendSmsToOnePhone(onePhoneNumber, text);
				success ++;
			} catch(Exception e) {
				ThreadLocalStopwatch.error("ERROR: SmsService: Error during sending, message to " + onePhoneNumber + " was not sent due to " + e);
				e.printStackTrace();				
			}
		}
		if (success == 0) {
			ThreadLocalStopwatch.error("ERROR: SmsService: NO TEXT MESSAGE COULD BE SENT.");				
		} else {
			ThreadLocalStopwatch.error("DONE: SmsService: " + success + " text messages were successfully sent.");
		}
	}
	
	void validatePhoneNumber(String onePhoneNumber) {
		String oneOnlyDigits = PhoneNumberUtil.onlyDigits(onePhoneNumber);
		if (oneOnlyDigits.length() != 10) throw new IllegalArgumentException("Phone number does not contain 10 digits.");
				
	}
	
	private void sendSmsToOnePhone(String onePhone, String text) {
		onePhone = "+1" + onePhone;
		ThreadLocalStopwatch.now("Would send sms to " + onePhone);
		throw new java.lang.IllegalStateException("Not Yet Implemented");
	}
}

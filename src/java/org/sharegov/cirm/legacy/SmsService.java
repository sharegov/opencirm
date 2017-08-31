package org.sharegov.cirm.legacy;

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
		//TODO: to know what the http call looks like.
	}
}

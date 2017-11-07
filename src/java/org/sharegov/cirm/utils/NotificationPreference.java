/*******************************************************************************
 * Copyright 2015 Miami-Dade County
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

/**
 * NotificationPreference represents SR actor/citizen preferences for email and SMS messaging as enumerated values for the hasNotificationPreference owl data property. 
 * If undefined, the actor will by default receive an email, but not SMS/text messages.
 * SMS/text messages may incur cost and are therefore only sent if actor/citizen consents to receiving these.
 * 
 * @author Thomas Hilpold
 *
 */
public enum NotificationPreference {
	UNDEFINED ("UNDEFINED"),
	A ("ALL"),
	E ("EMAIL_ONLY"),
	S ("SMS_ONLY"),
	N ("NO_MESSAGE");

	private String label;
	
	NotificationPreference(String label) {
		this.label = label;
	}
	
	public String getLabel() {
		return label;
	}
	
	/**
	 * The value indicates that sending an email is ok (Undefined, A, or E).
	 * @return
	 */
	public boolean isEmailOk() {
		return this.equals(UNDEFINED) || this.equals(A)|| this.equals(E);
	}
	
	/**
	 * The value indicates that sending an SMS/Text Message is ok (A or S).
	 * @return
	 */
	public boolean isSmsOk() {
		return this.equals(A) || this.equals(S);		
	}
}

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
 * MessagingPreference represents SR actor/citizen preferences for email and SMS messaging.
 * If undefined, the actor will by default receive an email, but not SMS/text messages.
 * SMS/text messages may incur cost and are therefore only sent if actor/citizen consents to receiving these.
 * 
 * @author Thomas Hilpold
 *
 */
public enum MessagingPreference {
	UNDEFINED ("UNDEFINED"),
	A ("ALL"),
	E ("EMAIL_ONLY"),
	S ("SMS_ONLY"),
	N ("NO_MESSAGE");

	private String label;
	
	MessagingPreference(String label) {
		this.label = label;
	}
	
	public String getLabel() {
		return label;
	}
	
	public boolean prefersEmail() {
		return this.equals(UNDEFINED) || this.equals(A)|| this.equals(E);
	}
	
	public boolean prefersSMS() {
		return this.equals(A) || this.equals(S);		
	}
}

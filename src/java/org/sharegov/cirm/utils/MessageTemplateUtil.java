/*******************************************************************************
 * Copyright 2017 Miami-Dade County
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
 * Utilities for Message Templates, primarily related to sms/email messaging to citizen.
 * 
 * @author Thomas Hilpold
 */
public class MessageTemplateUtil {
	
	public final String CITIZEN_EMAIL_VAR = "$$CITIZENS_EMAIL$$";
	public final String CITIZEN_CELL_PHONE_VAR = "$$CITIZENS_CELL_PHONE$$";
			
	public boolean hasCitizenEmailRecipient(String recipientVariables) {
		for(String r : recipientVariables.split(";")) {
			if (CITIZEN_EMAIL_VAR.equals(r)) return true;
		}
		return false;
	}

	public boolean hasCitizenCellPhoneRecipient(String recipientVariables) {
		for(String r : recipientVariables.split(";")) {
			if (CITIZEN_CELL_PHONE_VAR.equals(r)) return true;
		}
		return false;
	}
	
	public String removeCitizenEmailRecipient(String recipientVariables) {
		return removeVarFrom(CITIZEN_EMAIL_VAR, recipientVariables);
	}

	public String removeCitizenCellPhoneRecipient(String recipientVariables) {
		return removeVarFrom(CITIZEN_CELL_PHONE_VAR, recipientVariables);
	}

	private String removeVarFrom(String variableName, String recipientVariables) {
		String result = "";
		for(String r : recipientVariables.split(";")) {
			if (r != null && !r.isEmpty() && !variableName.equals(r)) {
				result += r + ";";
			};
		}
		return result;
	}
}

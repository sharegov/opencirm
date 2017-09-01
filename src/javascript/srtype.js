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
/**
 * SR Type (aka ServiceCase Types) utility functions.
 * 
 * @author hilpold
 */
define(["jquery", "U" ], function($, U) {

	//Constants for finding templates that notify citizen in hasTo/hasCC template properties.
	var VAR_CITIZEN_CELL_PHONE = 'CITIZENS_CELL_PHONE';
	var VAR_CITIZEN_EMAIL = 'CITIZENS_EMAIL';

	/**
	 * Determines if a template exists that notifies citizen by email.
	 * serviceCaseUI...serviceCase after prepare for UI called.
	 * 
	 * @return true if 1+ such templates exist, false otherwise.
	 */
	function hasCitizenEmailTemplate(serviceCaseUI) {
		var result = false;
		var hasActivityArr = serviceCaseUI.hasActivity;
		$.each(hasActivityArr, function(index, value) {
			if (value.hasEmailTemplate) {
				var hasTo = value.hasEmailTemplate.hasTo;
				if (hasTo !== undefined && U.isString(hasTo)) {
					if (hasTo.indexOf(VAR_CITIZEN_EMAIL) >= 0) {
						result = true;
						//break out of foreach
						return false;
					}
				}
			}
			return true;
		});
		return result;
	}

	/**
	 * Determines if a template exists that notifies citizen by SMS.
	 * serviceCaseUI...serviceCase after prepare for UI called.
	 * 
	 * @return true if 1+ such templates exist, false otherwise.
	 */
	function hasCitizenSmsTemplate(serviceCaseUI) {
		var result = false;
		var hasActivityArr = serviceCaseUI.hasActivity;
		$.each(hasActivityArr, function(index, value) {
			if (value.hasSmsTemplate) {
				var hasTo = value.hasSmsTemplate.hasTo;
				if (hasTo !== undefined && U.isString(hasTo)) {
					if (hasTo.indexOf(VAR_CITIZEN_CELL_PHONE) >= 0) {
						result = true;
						//break out of foreach
						return false;
					}
				}
			}
			return true;
		});
		return result;
	}

	//Public Interface
	return {
		hasCitizenEmailTemplate : hasCitizenEmailTemplate,
		hasCitizenSmsTemplate : hasCitizenSmsTemplate
	}
})
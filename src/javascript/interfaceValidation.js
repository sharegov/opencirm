/*******************************************************************************
 * Copyright 2016 Miami-Dade County
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
 * Contains interface specific service request validation for creating and updating SRs, 
 * which will be used in addition to augment validation for all SRs.
 * 
 * Current implementation only validates for COM-CITY.
 * 
 * @author Thomas Hilpold
 */
define(["jquery", "U", "cirm" ], 
function($, U, cirm)   {
	
	/**
	 * Known interfaces for this validator. Only COM-CITY validation is currently implemented.
	 */
	var KNOWN_INTERFACE_ARR = ["COM-CITY", "MD-PWS", "MD-CMS", "MD-WCSL"]; 
	var LEGACY_IRI_PREFIX = "http://www.miamidade.gov/cirm/legacy#"; 
	
	var MIN_FOLIO_LENGTH = 10; 	
		
	function InterfaceValidator() {
		var self = this;
		var validationMessage = null;
    	/**
    	 * Deterines if given serviceRequest (plain json) is valid for the interface defined 
    	 * by it's type. Only COM-CITY may return false;
    	 * 
    	 * After an invalid result, getValidationMessage() will provide details for the user on 
    	 * how to correct the service request.
    	 * @param  serviceRequest plain json object of a service request before prefixmaker.
    	 */
    	self.isValidForInterface = function (serviceRequest) {
    		validationMessage = "";
    		var typePrefixedIRI = serviceRequest.type;
    		var typeFragment = typePrefixedIRI; // typePrefixedIRI.split(":")[1];
    		var typeInterface = self.getInterfaceForType(typeFragment)
    		
    		var interfaceValidationFunction;
    		if (KNOWN_INTERFACE_ARR[0] == typeInterface) {    			
    			interfaceValidationFunction = self.validateForCOM_City
    		} else if (KNOWN_INTERFACE_ARR[1] == typeInterface) {
    			//Not yet validating MD-PWS
    		} else if (KNOWN_INTERFACE_ARR[2] == typeInterface) {
    			//Not yet validating MD-CMS
    		} else {
    			//Not a known interface type;
    		}
    		if (interfaceValidationFunction) {
    			return interfaceValidationFunction(serviceRequest);
    		} else {
    			return true; //either non interface SR or not yet implemented
    		}
    	}
    	
    	/**
    	 * gets the interface, if known by interface validator, for a given service request type as fragment.
    	 */
    	self.getInterfaceForType = function (typeFragment) {
    		var typeFullIri = /*LEGACY_IRI_PREFIX + */ typeFragment;
    		var type = cirm.refs.serviceCases[typeFullIri];
    		var legacyInterfaceArr = U.ensureArray(type.hasLegacyInterface);
    		var result = null;
    		$.each(legacyInterfaceArr, function(idx, legacyInterface) {
    			var interFaceFullIri;
    			if (legacyInterface.iri) {
    				interFaceFullIri = legacyInterface.iri
    			} else {
    				interFaceFullIri = hasLegacyInterface;
    			}
    			$.each(KNOWN_INTERFACE_ARR, function(idx2, KNOWN_INTERFACE) {
    				var knownInterfaceFullIri = LEGACY_IRI_PREFIX + KNOWN_INTERFACE;
    				if (knownInterfaceFullIri == interFaceFullIri) {
    					result = KNOWN_INTERFACE;
    					return false;
    				}
    			});
    			if (result !== null) return false;
    		});
    		return result;
    	}
    	
    	/**
    	 * Validates this service request for the COM-CITY interface (CITYVIEW)
    	 * In particular, existence of a folio number for the address of the request is checked.
    	 */
    	self.validateForCOM_City = function(serviceRequest) {
    		var atAddress = serviceRequest.properties.atAddress;
    		var valid = false;
    		if (atAddress && atAddress.folio) {
    			if (!U.isEmptyString(atAddress.folio) && atAddress.folio.length >= MIN_FOLIO_LENGTH) {
    				valid = true;
    			}
    		}
    		if (!valid) {
    			validationMessage = "This City of Miami SR requires a folio number. \n"
    				+ "Use the map to change the address to a nearby address with a folio number. \n"
    				+ "Provide details on the actual location in the SR Description field to assist " 
    				+ "the field staff that will respond. \n" 
    				+ "The SR cannot be opened with an intersection or approx address that has no folio number.";
    		}
    		return valid;
    	}
    	
    	/**
    	 * Gets the last set validation message after a validation failure.
    	 */
    	self.getValidationMessage = function() {
    		return validationMessage;
    	}
    	return self;
    }

    return new InterfaceValidator();
})

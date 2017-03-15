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
define(["jquery", "U", "rest", "uiEngine", "store!", "cirm", "legacy", "interfaceValidation", "text!../html/srmarkup.ht"], 
   function($, U, rest, ui, store, cirm, legacy, interfaceValidation, srmarkupText)   {
    
    function getMetadataUrl(){
    	return 'https://api.miamidade.gov/s3wsnc/metadata/update';
    }
	
    function AddressBluePrint() {
    	var self = this;
		self.Street_Number = "";
		self.Street_Name = "";
		self.fullAddress = ""; 
		self.Street_Unit_Number = ""; 
		self.Zip_Code = "";
		self.folio = "";
		self.hasLocationName = "";
		self.type = "Street_Address";
		self.Street_Address_City = {"iri":"", "label":"" };
		self.Street_Address_State = {"iri":"http://www.miamidade.gov/ontology#Florida", "label":"Florida" };
		self.Street_Direction = {"label":"", "iri":""};
		self.hasStreetType = {"label":"", "iri":""};
		self.addressType = "";
    }
    
    function ServiceActorAddressBluePrint()
    {
    	var self = this;
		self.fullAddress = ""; 
		self.Street_Unit_Number = ""; 
		self.Zip_Code = "";
		self.type = "Street_Address";
		self.Street_Address_City = {"iri":"", "label":"" };
		self.Street_Address_State = {"iri":"http://www.miamidade.gov/ontology#Florida", "label":"Florida" };
    }
    
    function ServiceActor(iri, label, username, serverDate, isNew) {
    	var self = this;
    	self.hasServiceActor = {"iri":iri, "label":label};
    	self.Name = "";
    	self.LastName = "";
    	self.BusinessPhoneNumber = [{"number":"", "extn":""}];
    	self.HomePhoneNumber = [{"number":"", "extn":""}];
    	self.CellPhoneNumber = [{"number":"", "extn":""}];
    	self.OtherPhoneNumber = [{"number":"", "extn":""}];
    	self.FaxNumber = [{"number":"", "extn":""}];
    	self.hasEmailAddress = {"iri":"", "label":"", "type":"EmailAddress"};
    	self.atAddress = new ServiceActorAddressBluePrint();
    	self.type = "legacy:ServiceCaseActor";
		if(!isNew) {
			self.hasUpdatedDate = serverDate;
			self.isModifiedBy = username;
    		self.isCreatedBy = "";
		}
    	else if(isNew) {
    		self.hasDateCreated = serverDate;
    		self.isCreatedBy = username;
    	}
    	self.emailCustomer = false;
    	if(isCitizenActor(iri))
	    		self.isAnonymous = false;
    }
    
	function isCitizenActor(iri) {
		return (iri === 'http://www.miamidade.gov/cirm/legacy#CITIZEN');
	}

    function ServiceActivity(iri, label, username, serverDate, isNew) {
    	var self = this;
    	self.isOldData = false;
    	self.isAccepted = false;
		self.hasCompletedTimestamp = "";
		self.hasDueDate = "";
		self.isAssignedTo = "";
		self.isAutoAssign = undefined;
		self.hasLegacyCode = undefined;
		self.hasDetails = "";
		self.hasActivity = {"iri":iri, "type":"legacy:Activity", "label":label};
		self.hasOutcome = {"iri":"", "type":"legacy:Outcome"};
		self.type = "legacy:ServiceActivity";
		if(!isNew) {
			self.hasUpdatedDate = serverDate;
			self.isModifiedBy = username;
   			self.isCreatedBy = "";
		}
		else if(isNew) {
			self.hasDateCreated = serverDate;
    		self.isCreatedBy = username;
		}
    }
    
    function RequestModel(addressModel) {
        var self = this;
        var emptyModel = {
          data : {
        	properties: { 
        		atAddress: new AddressBluePrint(),
        		hasServiceActivity:[],
        		hasServiceCaseActor:[],
        		hasAttachment: [],
        		hasRemovedAttachment:[],
        		hasStatus:{"iri":"", "label":""},
        		hasPriority:{"iri":"", "label":""},
        		hasIntakeMethod:{"iri":"", "label":""},
       			hasDetails:"",
       			hasXCoordinate:"",
       			hasYCoordinate:"",
       			hasDueDate:"",
       			hasCaseNumber:""
        	},
       		boid:"",
       		type:""
          },
          srType:{hasActivity:[], hasServiceActor:[],hasServiceField:[]},
          allStatus:[],
          allPriority:[],
          allIntake:[],
          allStates:[],
          isLockedStatus:false,
          isAddressValidationEnabled:false,
          hasTypeMapping:{},
          originalData:{},
          saveButtonLabel:"Save",
          gisInfoLabel:"Gis Info",
          activeProfile: {},
          duplicateCount: -1,
          duplicateDetails: {},
          defaultSCAnswerUpdateTimeoutMins:"",
          emailData:{"subject":"", "to":"", "cc":"", "bcc":"","comments":""},
          currentServerTime: {}
        }; //emptyModel end
		
        //START: Basic RequestModel initialization
		$.extend(self, emptyModel);		
		U.visit(self, U.makeObservableTrimmed);
		
		self.allStatus.call('push', cirm.refs.caseStatuses);
		self.allPriority.call('push', cirm.refs.casePriorities);
		self.allIntake.call('push', cirm.refs.caseIntakeMethods);
		self.allStates.call('push', cirm.refs.statesInUS);
		self.hasTypeMapping(cirm.refs.typeToXSDMappings);
		if (cirm.refs.serviceCaseClass && cirm.refs.serviceCaseClass.hasAnswerUpdateTimeout) {
			self.defaultSCAnswerUpdateTimeoutMins(parseInt(cirm.refs.serviceCaseClass.hasAnswerUpdateTimeout.hasValue));
		} else {
			self.defaultSCAnswerUpdateTimeoutMins(0);
		}
        //END: Basic RequestModel initialization
		
		//START: define knockoutjs extenders for emptyModel -----------------------------------------
		ko.extenders.required = function(target, overrideMessage) {
			self.commonExtenderForAll(target, overrideMessage);
		};
		ko.extenders.addr_required = function(target, overrideMessage) {
			self.commonExtenderForAll(target, overrideMessage, "ADDRESS_REQ");
		};
		ko.extenders.numeric = function(target, overrideMessage) {
			self.commonExtenderForAll(target, overrideMessage, "NUMBER");
		};
		ko.extenders.numeric_required = function(target, overrideMessage) {
			self.commonExtenderForAll(target, overrideMessage, "NUMBER_REQ");
		};
		ko.extenders.valiDATE = function(target, overrideMessage) {
			self.commonExtenderForAll(target, overrideMessage, "DATE");
		};
		ko.extenders.valiDATE_required = function(target, overrideMessage) {
			self.commonExtenderForAll(target, overrideMessage, "DATE_REQ");
		};
		ko.extenders.email = function(target, overrideMessage) {
			self.commonExtenderForAll(target, overrideMessage, "EMAIL");
		};
		ko.extenders.email_required = function(target, overrideMessage) {
			self.commonExtenderForAll(target, overrideMessage, "EMAIL_REQ");
		};
		ko.extenders.phone = function(target, overrideMessage) {
			self.commonExtenderForAll(target, overrideMessage, "PHONE");
		};
		ko.extenders.phone_required = function(target, overrideMessage) {
			self.commonExtenderForAll(target, overrideMessage, "PHONE_REQ");
		};
		ko.extenders.extension = function(target, overrideMessage) {
			self.commonExtenderForAll(target, overrideMessage, "EXTENSION");
		};
		ko.extenders.time = function(target, overrideMessage) {
			self.commonExtenderForAll(target, overrideMessage, "TIME");
		};
		ko.extenders.time_required = function(target, overrideMessage) {
			self.commonExtenderForAll(target, overrideMessage, "TIME_REQ");
		};
		ko.extenders.multiUnit = function(target, overrideMessage) {
			self.commonExtenderForAll(target, overrideMessage, "MULTI");
		};
		ko.extenders.none = function(target, overrideMessage) {
			self.commonExtenderForAll(target, overrideMessage, "NONE");
		};
		ko.extenders.standardizeStreet = function(target, overrideMessage) {
			self.commonExtenderForAll(target, overrideMessage, "STANDARDIZE_STREET");
		};				
		// Apply where a serviceAnswerConstraint is configured and the question is not REQINTAK.
		var serviceAnswerConstraint = function(target, stringConstraint) {
			self.charDataConstraintExtenderFunction(target, stringConstraint, false);
		};		

		ko.extenders.TextLengthConstraint = serviceAnswerConstraint;
		ko.extenders.serviceAnswerConstraint = serviceAnswerConstraint;		
		
		// Apply where a serviceAnswerConstraint is configured and the question is REQINTAK configured.
		ko.extenders.serviceAnswerConstraintAndRequired = function(target, stringConstraint) {
			self.charDataConstraintExtenderFunction(target, stringConstraint, true);
		};
		
		/**
		 * Use toolTipTitle as title attribute binding on char answer input fields
		 * to suppress title tooltip to obstruct view while call takers are typing.
		 * Bind hasfocus of input field to hasInputFocus.
		 */
		ko.extenders.charTemplateLiteralExtender = function(target, option) {
		    target.hasInputFocus = ko.observable();
		    target.toolTipTitle = ko.computed(function() {		    
		    	if(target.hasInputFocus()) {
		    		return "";
		    	} else {
		    		return target();
		    	}
		    });
			return target;
		};
		
		self.commonExtenderForAll = function(target, overrideMessage, type) {
			target.hasError = ko.observable();
			target.validationMessage = ko.observable();
			if(type == "NONE") {
				target.conditionallyRequired = ko.observable();
				target.conditionallyRequired(false);
			}
			if(type =="STANDARDIZE_STREET") {
				target.conditionallyRequired = ko.observable();
				target.isStandardizedStreet = ko.observable();
				target.conditionallyRequired(false);
				target.isStandardizedStreet(false);
			}
			
			function validate(newValue) { //all required fields empty check
				if(U.isEmptyString(newValue) && !target.conditionallyRequired) {
					if(type != undefined && type.indexOf("REQ") == -1) {
						target.hasError(false);
		   				target.validationMessage("");
					}
					else {
						target.hasError(true);
		   				target.validationMessage("Required");
					}
	   				return true;
				}
				if(type == "NONE" || type == "STANDARDIZE_STREET") {
					if(target.conditionallyRequired() == false) {
						target.hasError(false);
						target.validationMessage("");
					}
					else {
						if(U.isEmptyString(newValue)) {
							target.hasError(true);
							target.validationMessage("Required");
							if(type == 'STANDARDIZE_STREET')
								target.isStandardizedStreet(false);
						}
						else {
							target.hasError(false);
							target.validationMessage("");
						}
					}
					return true;
				}
				if(type =="MULTI"){
					if(newValue=="MULTI"){
						target.hasError(true);
						target.validationMessage("");
					}else{
						target.hasError(false);
						target.validationMessage("");
					}
					return true;
				}
				if(type == "NUMBER" || type == "NUMBER_REQ") { //required NUMBER field
					if(!isNaN(newValue) && isFinite(newValue)) {
						target.hasError(false);
						target.validationMessage("");
					}
					else {
						target.hasError(true);
		   				target.validationMessage("Invalid Number");
		   			}
	   				return true;
				}
				else if(type == "TIME" || type == "TIME_REQ") {
					var timeFilter = /(^[0-9]|0[1-9]|1[0-2]):([0-5][0-9]) (AM|am|PM|pm)/;
					if(timeFilter.test(newValue)) {
						target.hasError(false);
						target.validationMessage("");
					}
					else {
						target.hasError(true);
						target.validationMessage("HH:MM AM");
					}
					return true;
				}
				else if(type == "EMAIL" || type == "EMAIL_REQ") { //validate email field
					//var emailFilter = /[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?.)+(?:[A-Z]{2}|com|org|net|gov|mil|biz|info|mobi|name|aero|jobs|museum)/;
					var emailFilter = /^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,4}$/;
					if(emailFilter.test(newValue)) {
						target.hasError(false);
						target.validationMessage("");
					}
					else {
						target.hasError(true);
						target.validationMessage("Invalid format");
					}
	   				return true;
				}
				else if(type == "PHONE" || type == "PHONE_REQ") {
					var phoneFilter = /^(?:\d){10}$|^(?:\d{3}-\d{3}-\d{4})$/ //pre v2.0.9 format /^(?:\W*\d){10,16}$/;
					if(phoneFilter.test(newValue)) {
						target.hasError(false);
						target.validationMessage("");
					}
					else {
						target.hasError(true);
						target.validationMessage("Invalid phone format, use 305-123-1234 OR 3051231234");
					}
					return true;
				}
				else if(type == "EXTENSION") {
					var extnFilter = /^[0-9]{1,6}$/;
					if(extnFilter.test(newValue)) {
						target.hasError(false);
						target.validationMessage("");
					}
					else {
						target.hasError(true);
						target.validationMessage("Invalid extension format, use 1 to 6 digits only");
					}
					return true;
				}
				else if(type == "DATE" || type == "DATE_REQ") { //required DATE field
					var datePat = /^(\d{2,2})(\/)(\d{2,2})\2(\d{4}|\d{4})$/;
					var matchArray = newValue.match(datePat); // is the format ok?
					if (matchArray == null) {
						target.hasError(true);
	       				target.validationMessage("Invalid Date format");
	       				return true;
					}
					else {
						month = matchArray[1];
						day = matchArray[3];
						year = matchArray[4];
						if (month < 1 || month > 12) {
							target.hasError(true);
		       				target.validationMessage("Invalid Month");
		       				return true;
						}
						if (day < 1 || day > 31) {
							target.hasError(true);
		       				target.validationMessage("Invalid Date");
		       				return true;
						}
						if ((month==4 || month==6 || month==9 || month==11) && day==31) {
							target.hasError(true);
		       				target.validationMessage("Invalid Date");
		       				return true;
						}
						if (month == 2) { 
							var isleap = (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0));
							if (day>29 || (day==29 && !isleap)) {
								target.hasError(true);
			       				target.validationMessage("Invalid Date");
			       				return true;
							}
						}
						target.hasError(false);
	       				target.validationMessage("");
       					return true;
					}
				}
				else { //Rest all required fields
					if(U.isArray(newValue)) {
						target.hasError(newValue[0] ? false : true);
	       				target.validationMessage(newValue[0] ? "" : overrideMessage || "Required");
					}
					else {
						target.hasError(newValue ? false : true);
	       				target.validationMessage(newValue ? "" : overrideMessage || "Required");
					}
					return true;
				}
			}
			validate(target());
			target.subscribe(validate);
			return target;
		}
		
		/**
		 * This extender function for string constraints can be applied to ServiceAnswer literals
		 * or other SR properties (e.g. hasDetails) that allow single or multi-line char user input.
		 * Param stringConstraint may have a combination of hasMax int, hasMin int or hasRegexPattern string data properties.
		 * Param required true/false determines if the literal is required, which is configured 
		 * independent of string constraints (REQINTAK) at the ServiceQuestion level.
		 * 
		 * An empty value will cause a validation error, if REQINTAK, hasMin > 0, or both are configured. 
		 * 
		 * hilpold: in the future hasRegexPattern should be accompanied by hasRegexPatternDescription 
		 * 			to show a more meaningful error message to the user.
		 */
		self.charDataConstraintExtenderFunction = function(target, stringConstraint, required) {
			if (ko.isObservable(stringConstraint)) {
				stringConstraint = ko.toJS(stringConstraint);
			}
		    target.hasError = ko.observable();
		    target.validationMessage = ko.observable();
		    
			function validate(value) {
				target.hasError(false);
			    target.validationMessage("");
			    if (!required) {
			    	//even if !required by the parameter, hasMin could imply that a non empty string value is required
			    	required = stringConstraint.hasMin && stringConstraint.hasMin > 0;
			    }			    
			    if (U.isEmptyString(value)) {
			    	if (required) {
			    		target.hasError(true);
					    target.validationMessage("Required");
			    	} //else ok
			    	return true;
			    }
			    // A) hasMax int optional
			    if (stringConstraint.hasMax && value.length > stringConstraint.hasMax) {
			        target.hasError(true);
			        var nrOfExcessChars = value.length - stringConstraint.hasMax;
			        target.validationMessage("Maximum length " + stringConstraint.hasMax + " exceeded by " + nrOfExcessChars + ".");
			    }
			    // B) hasMin int optional, effective after first char was entered 
			    //	  (in contrast to required (REQINTAK) evaluation)
			    if (stringConstraint.hasMin && value.length < stringConstraint.hasMin) {
			        target.hasError(true);
			        target.validationMessage("Mininum length is " + stringConstraint.hasMin + ".");
			    }
			    // C) hasRegexPattern string optional
			    if (stringConstraint.hasRegexPattern) {
			    	var pattern = stringConstraint.hasRegexPattern;
			    	var isValid = true;
			    	try {
			    		var matcher = new RegExp(pattern, "im" ); //im = ignore case & multiline
			    		isValid = matcher.test(value);
			    	} catch(err) {
			    		isValid = true;
			    		console.log("Ignoring Exception in charDataConstraintExtenderFunction while evaluating pattern " + stringConstraint.hasRegexPattern);
			    	}
			    	if(!isValid) {
				        target.hasError(true);
				        target.validationMessage("Please correct entry to match " + pattern + " pattern");
			    	}
			    }
			    return true;
			} // end function
			validate(target());
			target.subscribe(validate);
			return target;		    
		};
		//END: define knockoutjs extenders -----------------------------------------

        //
        // Client-side event listeners associated with this model. They are updated during the 'bindData'
        // operation when the SR changes. Important not to have this variable be a KO observable
        // because the $.each loop below ends up unbinding all jquery events!
		self.listeners = []; 
	    self.isNew = true;
	    self.isPendingApproval = false;
	    self.bindData = function(type, request, original) {
	    	self.data(emptyModel.data);
	    	self.srType(ko.toJS(emptyModel.srType));
	    	self.originalData(emptyModel.originalData);			
			self.currentServerTime(self.getServerDateTime(true));
			if (type) {
				self.srType(type);
			}
			if (request) {
			    self.isNew = U.isEmptyString(request.boid());
			    //START: apply knockoutjs extenders ----------------------------------------------------------------------
				// A) Extend SR type level properties with data contraints (e.g. hasDetails for PW types)
			    $.each(self.srType().hasDataConstraint, function(i, constraint) {
					if (constraint.appliesTo !== undefined) {
						var propname = constraint.appliesTo.iri.split('#')[1];				      
						var extensions = {};
						extensions[constraint.type] = constraint;
						request.properties()[propname].extend(extensions);
					}
				});
				// B) Extend all required questions
				$.each(request.properties().hasServiceAnswer(), function(i,v) {
					self.addServiceQuestionExtenders(v);
            	});
           		// C) Extend email and Phone numbers of each Actor to check for validity
        		var firstCitizen = self.getFirstCitizenActor(request.properties().hasServiceCaseActor());
            	$.each(request.properties().hasServiceCaseActor(), function(i,v) {
            		//Check if first Citizen actor, make first cellphone required
        			if(firstCitizen != null && isCitizenActor(v.hasServiceActor().iri())) {
        				if(!self.isNew && !self.isCustomerLocked() && firstCitizen.iri() == v.iri())
        					self.addExtendersToActor(v, true);
        				else if(self.isNew && !self.isCustomerLocked())
        					self.addExtendersToActor(v, true);
        				else
        					self.addExtendersToActor(v);
        			}
        			else
        				self.addExtendersToActor(v);
            	});
            	// D) Status, Priority, Method Received are required fields
            	request.properties().hasStatus().iri.extend({ required: "Required" });
            	request.properties().hasPriority().iri.extend({ required: "Required" });
            	request.properties().hasIntakeMethod().iri.extend({ required: "Required" });
            	//Extend fullAddress, City and Zip fields
				self.addAddressExtenders(request);				
				
			    //End: apply knockoutjs extenders ----------------------------------------------------------------------
				self.data(request);
				
				if(!self.isNew && !U.isEmptyString(request.properties().atAddress().fullAddress()))
				{
			    	if(request.properties().gisAddressData)
			    	{
						$(document).trigger(legacy.InteractionEvents.SetAddress, 
											[ko.toJS(request.properties().gisAddressData), true]);
						delete request.properties().gisAddressData;
			    	}
			    	else
			    	{
				    	var tempAddress = ko.toJS(request.properties().atAddress);
				    	var tempCity = $.map(cirm.refs.cities, function(v,i) { 
				    		if(v.iri == tempAddress.Street_Address_City.iri) 
				    			return v; 
				    	});
				    	if(tempCity.length > 0)
				    		tempAddress.municipality = tempCity[0].Alias;
						U.visit(tempAddress, U.makeObservableTrimmed);
						$(document).trigger(legacy.InteractionEvents.SearchAddress, [tempAddress, true]);
					}
				}
			}
			if (original) {
			    //console.log('set original', original);
				self.originalData(original);
			}
			
			//
			// Cleanup existing event listeners and register new ones if necessary:
			//

			$.each(self.listeners, function(i, l) {
			        //console.log('unbind', l);
			        $(document).unbind(l.event, l.handler);
			});
			self.listeners = [];
			// Bind fields with client-side event data providers.
			if(self.srType().hasServiceField) {
				$.each(self.srType().hasServiceField, function(i, field) {
				        if (!field.hasDataSource || field.hasDataSource.type != "EventBasedDataSource")
				            return;
				        var l = {event:field.hasDataSource.providedBy.iri, handler: function(event, data) {
	                        if(!self.data().properties().hasServiceAnswer) {
	                            alertDialog("Please create/open a (relevant) Service Request to populate the SR");
	                            return; 
	                        }
	                        var answers = self.data().properties().hasServiceAnswer();
	                        $.each(answers, function(i,a) {			
	                            //console.log(acct, a.hasServiceField().iri(), a.hasAnswerValue());
	                            if (a.hasServiceField().iri() ==field.iri)
	                                a.hasAnswerValue().literal(data[field.hasDataSource.hasPropertyName]);
	                        });
	                    }};
	                    $(document).bind(l.event, l.handler);
	                    self.listeners.push(l);
				});
			}
			self.dupChecker();
			patchPlaceholdersforIE();
	    };
	    
	    /**
	     * Adds knock out extender(s) to service answer literal.
	     * Different extenders are used depending on required answer and question datatype. 
	     */
	    self.addServiceQuestionExtenders = function(v){
			if(v.isOldData && v.isOldData()) {
				return true;
			}
			if(v.hasBusinessCodes && !v.isDisabled) {
				if(self.isNew && v.hasBusinessCodes().indexOf("REQINTAK") != -1) {
					self.addServiceAnswerRequiredExtender(v);
				} else {
					self.addServiceAnswerNotRequiredExtender(v);
				}
			} else {
				self.addServiceAnswerNotRequiredExtender(v);
			}
	    };
	    
	    /**
	     * Adds knock out extender(s) for required (REQINTAK) service answer literal.
	     */
	    self.addServiceAnswerRequiredExtender = function(v) {
			if(v.hasDataType() == 'DATE') {
				v.hasAnswerValue().literal.extend({ valiDATE_required: "Required"});
			} else if(v.hasDataType() == 'NUMBER') {
				v.hasAnswerValue().literal.extend({ numeric_required: "Required"});
				v.hasAnswerValue().literal.extend({charTemplateLiteralExtender: ""});
	    	} else if(v.hasDataType() == 'TIME') {
				v.hasAnswerValue().literal.extend({ time_required: "Required"});
    		} else if(v.hasDataType() == 'PHONE' || v.hasDataType() == 'PHONENUM') {
				v.hasAnswerValue().literal.extend({charTemplateLiteralExtender: ""});
				v.hasAnswerValue().literal.extend({ phone_required : "Required"});
    		} else if(v.hasDataType() == 'CHARLIST' || v.hasDataType() == 'CHARMULT' || v.hasDataType() == 'CHAROPT' || v.hasDataType == 'CHARLISTOPT' ) {
				v.hasAnswerObject().iri.extend({ required: "Required" });
    		} else if(v.hasDataType() == 'CHAR') {
    			if (v.hasBusinessCodes && v.hasBusinessCodes().indexOf('EMAIL') != -1) {
    				v.hasAnswerValue().literal.extend({email_required: "Invalid"});
    			} else {
    				if (v.hasAnswerConstraint) {
    					v.hasAnswerValue().literal.extend({serviceAnswerConstraintAndRequired: v.hasAnswerConstraint});
    				} else {
    					v.hasAnswerValue().literal.extend({ required: "Required" });
    				}
    			}
				v.hasAnswerValue().literal.extend({charTemplateLiteralExtender: ""});				
			} else { 
				v.hasAnswerValue().literal.extend({ required: "Required" });
				v.hasAnswerValue().literal.extend({charTemplateLiteralExtender: ""});
			}
	    };
	    
	    /**
	     * Add knock out extender(s) for not required service answer literal.
	     */
	    self.addServiceAnswerNotRequiredExtender = function(v) {
			if(v.hasDataType() == 'DATE') {
				v.hasAnswerValue().literal.extend({ valiDATE: "Invalid"});
			} else if(v.hasDataType() == 'NUMBER') {
				v.hasAnswerValue().literal.extend({ numeric: "Invalid"});
				v.hasAnswerValue().literal.extend({charTemplateLiteralExtender: ""});			
			} else if(v.hasDataType() == 'PHONE' || v.hasDataType() == 'PHONENUM') {
				v.hasAnswerValue().literal.extend({ phone: "Invalid"});
				v.hasAnswerValue().literal.extend({charTemplateLiteralExtender: ""});			
			} else if(v.hasDataType() == 'TIME') {
				v.hasAnswerValue().literal.extend({ time: "Invalid format"});
			} else if(v.hasDataType() == 'CHAR') {
				if(v.hasStandardizeStreetFormat) {
					v.hasAnswerValue().literal.extend({standardizeStreet: ""});
				} else if(v.hasBusinessCodes && v.hasBusinessCodes().indexOf('EMAIL') != -1) {
					v.hasAnswerValue().literal.extend({email: "Invalid"});
				} else {
					if (v.hasAnswerConstraint) {
						v.hasAnswerValue().literal.extend({serviceAnswerConstraint: v.hasAnswerConstraint});
					} else {
						v.hasAnswerValue().literal.extend({none: ""});
					}
				}
				v.hasAnswerValue().literal.extend({charTemplateLiteralExtender: ""});
			}
		}
	    
	    self.addAddressExtenders = function(data)
	    {
        	data.properties().atAddress().fullAddress.extend({ required: "Required" });
        	data.properties().atAddress().Zip_Code.extend({ required: "Required" });
        	data.properties().atAddress().Street_Address_City().label.extend({ required: "Required" });
        	data.properties().atAddress().Street_Unit_Number.extend({multiUnit : " "});
	    }
	    
	    self.removeAddressExtenders = function(data)
	    {
			if(U.isEmptyString(self.data().type()))
			{
				self.isAddressValidationEnabled(false);
				if(data.properties().atAddress().fullAddress.hasError)
	        		data.properties().atAddress().fullAddress.hasError(false);
				if(data.properties().atAddress().fullAddress.validationMessage)
		        	data.properties().atAddress().fullAddress.validationMessage("");
				if(data.properties().atAddress().Zip_Code.hasError)
		        	data.properties().atAddress().Zip_Code.hasError(false);
				if(data.properties().atAddress().Zip_Code.validationMessage)
		        	data.properties().atAddress().Zip_Code.validationMessage("");	
				if(data.properties().atAddress().Street_Address_City().label.hasError)
		        	data.properties().atAddress().Street_Address_City().label.hasError(false);
				if(data.properties().atAddress().Street_Address_City().label.validationMessage)
		        	data.properties().atAddress().Street_Address_City().label.validationMessage("");
				if(data.properties().atAddress().Street_Unit_Number.hasError)
		        	data.properties().atAddress().Street_Unit_Number.hasError(false);
				if(data.properties().atAddress().Street_Unit_Number.validationMessage)
		        	data.properties().atAddress().Street_Unit_Number.validationMessage("");	
	    	}
	    }
	    
	    self.checkAddressError = function(el) {
	    	if(self.isAddressValidationEnabled())
	    		return el.hasError;
	    	else
	    		return false;
	    }
	    
		self.getServerDateTime = function(time) {
			var serverDateEstimate = U.getCurrentDate();
			if(time)
				return serverDateEstimate.getTime();
			else
				return serverDateEstimate;
		}
	    
		self.isStatusUpdateDisabled = function()
		{
			var updateTypeAllowed = true;
			if (self.data() && self.data().type()) 
			{
				updateTypeAllowed = cirm.user.isUpdateAllowed(self.data().type().split(":")[1]);
			}			
			return !(cirm.user.isUpdateAllowed("C-CLOSED") && updateTypeAllowed);
		}
		
		
	    function alertDialog(msg)
	    {
	    	$("#sh_dialog_alert")[0].innerText = msg;
			$("#sh_dialog_alert").dialog({ height: 300, width: 500, modal: true, buttons: {
				"Close" : function() { $("#sh_dialog_alert").dialog('close'); }
			 } 
			});
	    }
	    
	    self.makeCaseBlueprint = function(type) {
	        var blue = {boid:'', type: 'legacy:' + type.hasLegacyCode, properties:{}};
	        var P = blue.properties; 
			P.atAddress = {
	            Street_Address_City: {iri : '', label: ''},
	            Street_Address_State:{iri:"http://www.miamidade.gov/ontology#Florida","label": "FL"},
			    Street_Direction: {iri: "", label: ""},
			    hasStreetType: {iri: "", label: ""},
			    Street_Name:'',
				Street_Number: '',
				Street_Unit_Number: '',
				Zip_Code: '',
				type: "Street_Address",
				fullAddress: "",
			    folio:""};
			P.hasStatus = type.hasDefaultStatus ? U.clone(type.hasDefaultStatus) : { };
			P.hasPriority = type.hasDefaultPriority ? U.clone(type.hasDefaultPriority) : { };
			P.hasIntakeMethod =  type.hasDefaultIntakeMethod ? U.clone(type.hasDefaultIntakeMethod) : { };
			P.hasServiceAnswer = [];
			type.hasServiceField = U.ensureArray(type.hasServiceField);
			$.each(type.hasServiceField, function(i,f) {
			  //var a = {hasAnswerValue:{literal:'', type:''}, hasServiceField:{iri:f.iri, label:f.label}};
			  var a = {hasServiceField:{iri:f.iri, label:f.label}};
			  if(f.hasDataType == 'CHARLIST' || f.hasDataType == 'CHARMULT' || f.hasDataType == 'CHAROPT' || f.hasDatatype == 'CHARLISTOPT') {
			    a.hasAnswerObject = {"iri":undefined};
			  } else {
			  	a.hasAnswerValue = {literal:'', type:''};
			  }
			  //Directly refer to some serviceField properties in ServiceAnswer
			  if (f.hasOrderBy) a.hasOrderBy = f.hasOrderBy;
			  if (f.hasDataType) a.hasDataType = f.hasDataType;
			  if (f.hasBusinessCodes) a.hasBusinessCodes = f.hasBusinessCodes;
			  if (f.hasAllowableModules) a.hasAllowableModules = f.hasAllowableModules;
			  if (f.hasAnswerConstraint) a.hasAnswerConstraint = f.hasAnswerConstraint;
			  P.hasServiceAnswer.push(a); 
			});
			P.hasServiceAnswer.sort(function(x,y) {
                var l = x.hasOrderBy ? parseFloat(x.hasOrderBy) : 0;
                var r = y.hasOrderBy ? parseFloat(y.hasOrderBy) : 0;
                return l-r;
			});
			P.hasServiceActivity = [];
			P.hasServiceCaseActor = [];
			type.hasAutoServiceActor = U.ensureArray(type.hasAutoServiceActor);
			$.each(type.hasAutoServiceActor, function(i,a) {
			  a = $.grep(type.hasServiceActor, function(x){return x.iri == a.iri; })[0];
		      P.hasServiceCaseActor.push({hasServiceActor:{iri:a.iri, label:a.label}});
			});
			P.hasAttachment = [];
			P.hasRemovedAttachment = [];
			
			
    		
			//P.Comments = '';
			P.hasDetails = '';
			P.hasXCoordinate = "";
			P.hasYCoordinate = "";
			if (type.hasDurationDays) {
			    var currd = new Date();
			    currd.setTime(currd.getTime() + Math.round(parseFloat(type.hasDurationDays)*1000*60*60*24));
			    P.hasDueDate = currd.format();
			}
			//console.log('created blueprint', blue);
    	    return blue;
	    };
	    /**
	     * Sets the validation flag = true if sr type is BULKYTRA.
	     */
	    
	    self.enableValidationForSrType = function (srType)
	    {
	    	if (legacy.wcsModel != null) 
	    	{
	    		legacy.wcsModel.setValidationForBULKYTRA(srType == "BULKYTRA");
	    	}
	    };
	    
	    self.startNewServiceRequest = function(type) {
			if (cirm.user.isConfigAllowed()) {
				//hilpold Check server for new version of SR type 
				cirm.refs.reloadServiceCaseTypeIfNeeded(type);
			}
	    	self.enableValidationForSrType(type);
			var callback = function cb()
			{
			    var blueprint = self.makeCaseBlueprint(cirm.refs.serviceCases['http://www.miamidade.gov/cirm/legacy#' + type]);
                var currentAddress = ko.toJS(self.data().properties().atAddress);
                blueprint.properties.atAddress = currentAddress;
                if(self.data().properties().hasXCoordinate() != "")
                    blueprint.properties.hasXCoordinate = self.data().properties().hasXCoordinate();
                if(self.data().properties().hasYCoordinate() != "")
                    blueprint.properties.hasYCoordinate = self.data().properties().hasYCoordinate();
                fetchSR(blueprint, self, true);
			};
			if(cirm.refs.serviceCases['http://www.miamidade.gov/cirm/legacy#' + type].isDisabledCreate == 'true')
			{
				var srTypeName = $('#srTypeID').val().trim();
				alertDialog("The Creation of a new '" + srTypeName +"' Service Request is currently disabled for all users");
			}
			else if (!cirm.user.isNewAllowed(type)) 
			{
				var srTypeName = $('#srTypeID').val().trim();
				alertDialog("The Creation of a new '" + srTypeName +"' is currently not permitted for your group.");
			}
			else if(self.data().properties().hasXCoordinate() != "" && self.data().properties().hasYCoordinate() != "" ) {
				self.validateTypeOnXY(type, self.data().properties().hasXCoordinate()
					, self.data().properties().hasYCoordinate(), callback, "Service Request Type is not valid for this location. Please try a different location.");
			}
			else {
				callback();
			}
	    };
	    
		self.srTypeLookup = function() {
			$("#sh_dialog_address_search").dialog({ height: 140, modal: true });
			$("#sh_dialog_address_search").dialog('close');
			var srTypeID = $('#srTypeID').val().trim();
			var type = cirm.refs.serviceCaseList[srTypeID];
			//TODO Trigger type change event
			$(document).trigger(legacy.InteractionEvents.SrTypeSelection, [type]);
			
			//TODO hilpold - Refactor and simplify below code  
			if(!U.isEmptyString(self.data().type()))
			{
				if(!U.isEmptyString(type)){
					if (cirm.user.isConfigAllowed()) {
						//hilpold Check server for new version of SR type 
						cirm.refs.reloadServiceCaseTypeIfNeeded(type);
					}
					if(cirm.refs.serviceCases['http://www.miamidade.gov/cirm/legacy#' + type].isDisabled == 'true')
						alertDialog("Cannot create a disabled Service Request Type");
					else
						self.clearSR(type);
				}
				else
					alertDialog("Please select a valid Service Request Type");
			}
			else
			{
				if(!U.isEmptyString(type)){
					if (cirm.user.isConfigAllowed()) {
						//hilpold Check server for new version of SR type 
						cirm.refs.reloadServiceCaseTypeIfNeeded(type);
					}
					if(cirm.refs.serviceCases['http://www.miamidade.gov/cirm/legacy#' + type].isDisabled == 'true')
						alertDialog("Cannot create a disabled Service Request Type");
					else
						self.startNewServiceRequest(type);
				}
				else if(!U.isEmptyString(cirm.refs.serviceCases['http://www.miamidade.gov/cirm/legacy#' + srTypeID])){
					if (cirm.user.isConfigAllowed()) {
						//hilpold Check server for new version of SR type 
						cirm.refs.reloadServiceCaseTypeIfNeeded(type);
					}
					if(cirm.refs.serviceCases['http://www.miamidade.gov/cirm/legacy#' + srTypeID].isDisabled == 'true')
						alertDialog("Cannot create a disabled Service Request Type");
					else
						self.startNewServiceRequest(srTypeID);
				}
				else
					alertDialog("Please select a valid Service Request Type");
			}
			//TODO hilpold - Refactor and simplify above code  
		};
		
    	self.srLookupOnEnter = function(data, event) {
    		if(event.keyCode == 13) {
    			self.srLookup();
    		}
    		return true;
    	};
    	
    	//jquery related functions
        
        self.showAnswerFieldDialog = function(dialogTitle, fieldText ){ 
            $('#sh_text_dialog').text (fieldText);
            $('#sh_text_dialog').dialog({'modal':false, 'width':600, 'height':400, 'title':dialogTitle , 'draggable':true});
        };
		
		$(document).bind(legacy.InteractionEvents.populateSH, function (event, SR_ID) {
			
			if(self.data().boid() == SR_ID)
				return;
			else
			{
				$('[name="SR Lookup"]').val(SR_ID);
				//self.srLookup();
				self.removeDuplicates();
				self.loadFromServerByboid(SR_ID);
				
			}
		});

		function addActorInfo(actorDetails, actor)
		{
			var splitName = actorDetails.ownerName.split(" ");
			if(splitName.length > 1) {
				actor.Name(splitName[0]);
				actor.LastName(splitName[splitName.length-1]);
			}
			if(splitName.length == 1)
				actor.Name(splitName[0]);
			if(!U.isEmptyString(actorDetails.phone))
			{
				//actor.HomePhoneNumber(actorDetails.phone);
				if(actor.HomePhoneNumber().length > 0)
					actor.HomePhoneNumber()[0].number(actorDetails.phone);
				else
				{
					var contactNo = {"number":actorDetails.phone, "extn":""};
					U.visit(contactNo, U.makeObservableTrimmed);
					contactNo.number.extend({ phone : ""});
					contactNo.extn.extend({ extension : ""});
					actor.HomePhoneNumber.push(contactNo);
				}
					
			}
			if(actorDetails.hasAddress)
			{
				var fullAddr = 	actorDetails.atAddress.Street_Number + ' ' + 
								actorDetails.atAddress.Street_Direction + ' ' + 
								actorDetails.atAddress.Street_Name + ' ' +
								actorDetails.atAddress.hasStreetType;
				actor.atAddress().fullAddress(fullAddr);
				actor.atAddress().Zip_Code(actorDetails.atAddress.Zip_Code);
				var city = $.map(cirm.refs.cities, function(v) {
					if(typeof v.Alias == 'string' && v.Alias == actorDetails.atAddress.Street_Address_City)
						return v;
					else if(typeof v.Alias == 'object')
					{
						var aliasMatch = $.map(v.Alias, function(val) {
							if(val == actorDetails.atAddress.Street_Address_City)
								return val;
						});
						if(aliasMatch.length > 0)
							return v;
					}
				});
				if(city.length > 0)
				{
					actor.atAddress().Street_Address_City().iri(city[0].iri);
					actor.atAddress().Street_Address_City().label(city[0].label);
				}
				var state = $.map(cirm.refs.statesInUS, function(v) { 
					if(v.USPS_Abbreviation == actorDetails.atAddress.Street_Address_State) 
						return v; 
				});
				if(state.length > 0)
				{
					actor.atAddress().Street_Address_State().label(state[0].label);
					actor.atAddress().Street_Address_State().iri(state[0].iri);
				}
			}
			else
				self.populateActorAddress(actor);
		}

		$(document).bind(legacy.InteractionEvents.populateActor, function(event, actorDetails) {
			if(!U.isEmptyString(self.data().type()))
			{
				var individual = $.map(self.srType().hasServiceActor, function(v,i) { 
					if(v.hasLegacyCode == actorDetails.hasLegacyCode)
						return v;
				});
				var actor = $.map(self.data().properties().hasServiceCaseActor(), function(v,i) {
					if(v.hasServiceActor().iri() == individual[0].iri)
						return v;
				});
				if(actor.length > 0)
					addActorInfo(actorDetails, actor[0]);
				else if(actor.length == 0)
				{
					var iri = individual[0].iri;
					var label = individual[0].label;
					var newSA = new ServiceActor(iri, label, cirm.user.username, self.getServerDateTime().asISODateString(), true);
					U.visit(newSA, U.makeObservableTrimmed);
	            	var isFirstCitizen = (!self.isCustomerLocked() && isCitizenActor(iri) && self.getFirstCitizenActor() == null) ? true : false;
					self.addExtendersToActor(newSA, isFirstCitizen);
					addActorInfo(actorDetails, newSA);
		    		self.data().properties().hasServiceCaseActor.push(newSA);
				}
			}
		});
		
		$(document).bind(legacy.InteractionEvents.EndCall, function(event){
			if(!U.isEmptyString(self.data().type()) && U.isEmptyString(self.data().boid()))  {
				$("#sh_dialog_alert")[0].innerText = "Do you want to save the current Service Request?";
				$("#sh_dialog_alert").dialog({ height: 150, width: 350, modal: true, buttons: {
					"Save" : function() {
						$("#sh_dialog_alert").dialog('close');
						self.doSubmit(self);
					},
					"Clear": function() {
					  	$("#sh_dialog_alert").dialog('close');
						self.clearSR();
					},
					"Cancel": function() {
					  	$("#sh_dialog_alert").dialog('close');
					}
				  } 
				});
			}
			else {
				self.clearSR();
			}
		});

		self.loadFromServerByCaseNumber = function(lookup_boid) {
			var query = {"type":"legacy:ServiceCase", "legacy:hasCaseNumber":lookup_boid};
			cirm.top.async().postObject("/legacy/caseNumberSearch", query, function(result) {
				$("#sh_dialog_sr_lookup").dialog('close');
				if(result.ok)
					fetchSR(result.bo, self, false);
				else if(!result.ok)
				{
					switch(result.error)
					{
						case "Case not found.":
						{
							//Search by boid
							self.loadFromServerByboid(parseInt(lookup_boid.split("-")[1].substr(1)));
							break;
						}
						case "Permission denied.": 
						{
							showErrorDialog("An error occurred while searching for the Service Request : <br>"+result.error);
							break;
						}
						default :
						{
							showErrorDialog("An error occurred while searching for the Service Request : <br>"+result.error);
							break;
						}
					}
				}
			});
		};
		
		self.loadFromServerByboid = function(boid) {
			cirm.top.async().get("/legacy/search?id="+boid, {}, function(result) {
				$("#sh_dialog_sr_lookup").dialog('close');
				if(result.ok == true)
					fetchSR(result.bo, self, false);
				else if(result.ok == false)
					showErrorDialog("An error occurred while searching for the Service Request : <br>"+result.error);
			});
		};
		
		self.loadFromServer = function(lookup_boid) {
			if(lookup_boid.indexOf('-') == -1)
			{
				var yr = U.getFullYearAsString();
				lookup_boid = yr.substr(yr.length - 2) + '-1' + U.addLeadingZeroes(lookup_boid, 7);
			}
			//TODO : temp fix. We don't have to do below check once all SRs have hasCaseNumber
			if(self.data().boid() == parseInt(lookup_boid.split("-")[1]))
			{
				self.loadFromServerByBoid(lookup_boid.split("-")[1]);
				return true;
			}
			//END : temp fix.
			var caseNumberPattern = /[0-9]{2}.{1}[\d]{8}/;
			if(lookup_boid.match(caseNumberPattern))
			{
				self.loadFromServerByCaseNumber(lookup_boid);
				return true;
			}
			else
			{
				$("#sh_dialog_sr_lookup").dialog('close');
				var formatExample = "\n .SR_ID : 13-00000434 or 434";
				alertDialog("Unrecognizable SR ID Format. Please use one of these formats :" + formatExample);
				return true;
			}
		};
		
		self.isSaveDisabled = function() {
			var updateTypeAllowed = true;
			if (self.data() && self.data().type()) 
			{
				updateTypeAllowed = cirm.user.isUpdateAllowed(self.data().type().split(":")[1]);
			}		
			return !updateTypeAllowed;
		};
		

/*		
		self.loadFromServer = function(lookup_boid) {
			//var userFriendlyPattern = /[A-Z]{2}[0-9]{3,6}.{1}[\d]{8}/;
			//var alphabetPattern = /[A-Za-z]{2}/;
			//TODO : temp fix. We don't have to do below check once all SRs have hasUserFriendlyID
			if(self.data().boid() == parseInt(lookup_boid.split("-")[1]))
				lookup_boid = lookup_boid.split("-")[1];
			//END : temp fix.
			if(lookup_boid.match(userFriendlyPattern))
			{
				self.loadUserFriendlyFromServer(lookup_boid);
			}
			else if(lookup_boid.match(alphabetPattern)) {
				var pre = lookup_boid.substr(0,2);
				var srID = lookup_boid.substr(2,lookup_boid.length);
				var friendlyFormat = pre + U.getFullYear() + "-" + U.addLeadingZeroes(srID, 8);
				if(friendlyFormat.match(userFriendlyPattern) && friendlyFormat.indexOf("AC") != -1)
				{
					self.loadUserFriendlyFromServer(friendlyFormat);
				}
				else {
					$("#sh_dialog_sr_lookup").dialog('close');
					var formatExample = "\n .SR_ID : 1234 \n .SR_ID : AC1234 or AC2012-00001234 \n .Legacy_ID : 13-00000434";
					alertDialog("Unrecognizable SR ID Format. Please use one of these formats :" + formatExample);
					return true;
				}
			}
			else if(lookup_boid.indexOf("-") != -1) {
				var query = {"type":"legacy:ServiceCase", "legacy:hasLegacyId":lookup_boid,
							 "currentPage":1, "itemsPerPage":1};
				cirm.top.async().postObject("/legacy/advSearch", query,  function(result) {
					if(result.ok == true) {
						if(result.resultsArray.length == 1)
						{
							//fetchSR(result.resultsArray[0], self, false);
							var fetchedBOID = result.resultsArray[0].boid;
							cirm.top.async().get("/legacy/search?id="+fetchedBOID, {}, function(result) {
								$("#sh_dialog_sr_lookup").dialog('close');
								if(result.ok == true)
									fetchSR(result.bo, self, false);
								else if(result.ok == false)
									showErrorDialog("An error occurred while searching for the Service Request : <br>"+result.error);
							});

						}
						else
						{
							$("#sh_dialog_sr_lookup").dialog('close');
							alertDialog("No Search Results");
						}
					}
					else {
						$("#sh_dialog_sr_lookup").dialog('close');
						showErrorDialog("An error occurred while searching for the Service Request : <br>"+result.error);
					}
				});
			}
			else {
				cirm.top.async().get("/legacy/search?id="+lookup_boid, {}, function(result) {
					$("#sh_dialog_sr_lookup").dialog('close');
					if(result.ok == true)
						fetchSR(result.bo, self, false);
					else if(result.ok == false)
						showErrorDialog("An error occurred while searching for the Service Request : <br>"+result.error);
				});
			}
		};
*/
		self.srLookup = function() {
			var lookup_boid = $('[name="SR Lookup"]').val().trim();
			if(U.isEmptyString(lookup_boid))
			{
				alertDialog("Please enter a Case Number");
				return true;
			}
			self.removeDuplicates();
			$("#sh_dialog_sr_lookup").dialog({ height: 140, modal: true });
			//TODO : instead of looking for boid have to look for properties.hasCaseNumber
			store.cases.get(lookup_boid).then(function (offlineValue) {
                if (offlineValue != null) {
                    console.log('using offline object', offlineValue);
                    fetchSR(offlineValue, self, false);
					$("#sh_dialog_sr_lookup").dialog('close');
                }
                else {
                    console.log('not found locally', lookup_boid);
                    self.loadFromServer(lookup_boid);
                }
            }, function (error) {
                console.log('Error during offline load ', error);
                self.loadFromServer(lookup_boid);
            });
		};
		
		self.isDisabledDescription = function() {
			return !isServiceFieldUpdateWithinTimeout();
		};

		function isServiceFieldUpdateWithinTimeout(el) {
			/*
			1) Determine effective hasUpdateTimeoutMins value 
				1. check if serviceField has the updateTime obj prop
				2. if not, check if SR Type has it
				3. if not, use the ServiceCase default
				4. if not return undefined
			2) return (currentTimeLong - srCreatedTime) / 60000 <= effectiveUpdateTimeoutMins  
			*/		    	
			var isAnswerUpdateAllowed = undefined;
			if(el && el.hasBusinessCodes && el.hasBusinessCodes().indexOf("NOUPDATE") != -1) {
				//do nothing == isAnswerUpdateAllowed = undefined;
			} 
			else if(self.isPendingApproval) {
				isAnswerUpdateAllowed = true;
			} 
			else if(!U.isEmptyString(self.currentServerTime)) {
				var comparableTime = "";
				if(self.data().properties().hasDateCreated === undefined)
					return true;
				var timeSinceCreatedMins = 
					( self.currentServerTime() - U.getTimeInMillis(self.data().properties().hasDateCreated()) )/60000.0;
				if(el && el.hasAnswerUpdateTimeout)
					comparableTime = el.hasAnswerUpdateTimeout().hasValue();
				else if(self.srType().hasAnswerUpdateTimeout)
					comparableTime = self.srType().hasAnswerUpdateTimeout.hasValue;
				else
					comparableTime = self.defaultSCAnswerUpdateTimeoutMins();
				if(!U.isEmptyString(comparableTime))
				{
					if(comparableTime == -1)
						isAnswerUpdateAllowed = true;
					else
						isAnswerUpdateAllowed = (comparableTime >= timeSinceCreatedMins);
				}
			}
			return isAnswerUpdateAllowed;
		}
		
		/**
		 * True if activity's hasDateCreated is within certain hasAnswerUpdateTimeout limits
		 */
		function isActivityUpdateWithinTimeout(act) {
		    //return true;
			/*
			1) Determine effective hasUpdateTimeoutMins value 
				2. check if SR Type hasUpdateTimeoutMins
				3. if not, use the ServiceCase default
				4. if not return undefined
			2) return (currentTimeLong - srCreatedTime) / 60000 <= effectiveUpdateTimeoutMins  
			*/
		    if (act && act.hasDateCreated)
			var isUpdateAllowed = undefined;
			if(!U.isEmptyString(self.currentServerTime) && act.hasDateCreated())
			{
				var comparableTime = "";
				if(self.data().properties().hasDateCreated === undefined)
					return true;
				var timeSinceCreatedMins = 
					( self.currentServerTime() - U.getTimeInMillis(act.hasDateCreated()) )/60000.0;
				if(self.srType().hasAnswerUpdateTimeout)
					comparableTime = self.srType().hasAnswerUpdateTimeout.hasValue;
				else
					comparableTime = self.defaultSCAnswerUpdateTimeoutMins();
				if(!U.isEmptyString(comparableTime))
					isUpdateAllowed = (comparableTime >= timeSinceCreatedMins);
			}
			return isUpdateAllowed;
		}

		function showErrorDialog(errorMsg)
		{
			$("#sh_dialog_sr_err")[0].innerHTML= errorMsg;
			$("#sh_dialog_sr_err").dialog({height: 200, width: 500, modal: true});
		};

		self.getProfile = function() {
			if(!U.isEmptyString(self.data().properties().isCreatedBy))
				self.showProfile(self.data().properties().isCreatedBy);
		};
		
		self.showProfile =  function(eckey, el, event) {
			var s = eckey();
			if(s.indexOf('e') == 0 || s.indexOf('c') == 0)
			{
				cirm.users.async().get("/"+s,{},
					function(data) {
						if(data.ok)
						{
						    //console.log(data.profile);
							self.activeProfile(data.profile);
							$('#sh_profile_img').attr("src", "https://secure.miamidade.gov/enet/wps/PA_eNet_Profiles/getimage?p1="+s+"&p2=gov.miamidade.enet.profile.Profile&p3=ProfileImage");
							$('#sh_profile_img').error(function(){
														$('#sh_profile_img').unbind("error").attr("src","https://secure.miamidade.gov/enet/wps/PA_eNet_Profiles/images/default-profile-picture.jpeg");
														});
							$('#profile_info').tabs("destroy");
							$('#profile_info').tabs();
							$('#sh_dialog_profile').dialog({ 'modal':false, 'width':600, 'height':400, 'draggable':true });
							
						}
					});				
			}
		};
		
		function duplicateCheck()
		{
		    //console.log('duplicate check');
		    if (U.offline())
		        return;
			var postData = { "type": self.data().type(), "address" : ko.toJS(self.data().properties().atAddress)};
			if(!U.isEmptyString(self.data().boid()))
			{
				postData.createdDate = self.data().properties().hasDateCreated();
				postData.boid = self.data().boid();
				postData.hasCaseNumber = self.data().properties().hasCaseNumber();
			}
			//TODO : temp fix below. State is sometimes undefined, some times set to Florida. Debug Random behavior.
			if(postData.address.Street_Address_State.iri === undefined)
				postData.address.Street_Address_State.iri = "http://www.miamidade.gov/ontology#Florida";
			$('#dup_checking').show();
			$('#save').hide();
			cirm.top.async().post("/legacy/duplicateCheck", {data:JSON.stringify(postData)},  function(result) {
				$('#dup_checking').hide();
				$('#save').show();
				if(result.ok == true && result.count > 0)
				{
					result.details.sort(function (a,b) {
						return a.hasDateCreated - b.hasDateCreated;
					});
					var srDisplayedIsNew = U.isEmptyString(self.data().boid());
					var filteredDetails = $.map(result.details, function(v,i) { 
						if (!srDisplayedIsNew && v.boid == self.data().boid()) {
							return null;
						} else {
							return v;
						}
					});
					if(srDisplayedIsNew)					
					{
						self.data().properties().hasStatus().iri("http://www.miamidade.gov/cirm/legacy#O-DUP");
						self.data().properties().hasStatus().label("O-DUP");
					}
					self.duplicateCount(filteredDetails.length);
					self.duplicateDetails(filteredDetails);
				}
				else if(result.ok == false)
					showErrorDialog("An error occurred while searching for duplicates : <br>"+result.error);
			});
		};
		
		self.getFirstCitizenActor = function(serviceCaseActors) {
			var tempActors = (serviceCaseActors == undefined) ? self.data().properties().hasServiceCaseActor() : serviceCaseActors;
			var citizen = $.map(tempActors, function(v){
				if(isCitizenActor(v.hasServiceActor().iri()))
					return v;
			});
			if(citizen.length > 0)
				return citizen.pop(); //We are concerned only with the first created citizen 
			else
				return null;
		};

		self.showAnonymousAlert = function(data, event) {
			var citizen = self.getFirstCitizenActor();
			var user = cirm.user;
			if (citizen != null && citizen.isAnonymous()) {
				if (user && user.mdcDepartment !== "COM") {
					$("#sh_dialog_alert")[0].innerText = "Advise caller that even though report can be submitted anonymously, "
											+ " the audio recording can be provided if a public records request is submitted.";
					$("#sh_dialog_alert").dialog({ height: 170, width: 350, modal: true, buttons: {
						"I advised caller" : function() {
							$("#sh_dialog_alert").dialog('close');
						}
					}});
				}
			}
			return true;
		};
		
		self.isCitizenAnonymous = function() {
			var citizen = self.getFirstCitizenActor();
			if(citizen == null)
				return false;
			if(citizen.isAnonymous() === true) {
				citizen.CellPhoneNumber()[0].number('000-000-0000');
				citizen.Name("Anonymous");
			}
			else if(citizen.Name() === "Anonymous") {
				citizen.CellPhoneNumber()[0].number('');
				citizen.Name("");
			}
		}; 

		self.getCitizenActorField = function(el) {
			var citizen = self.getFirstCitizenActor();
			if(citizen != null)
				return citizen[el];
			else
				return citizen;
		};

		self.getCitizenActorEmail = function() {
			var citizen = self.getFirstCitizenActor();
			if(citizen != null)
				return citizen.hasEmailAddress().label;
			else
				return citizen;
		};
		
		self.getCitizenActorEmailError = function() {
			var citizen = self.getFirstCitizenActor();
			if(citizen != null)
				return citizen.hasEmailAddress().label.hasError;
			else
				return citizen;
		};

		self.getCitizenActorCellPhone = function() {
			var citizen = self.getFirstCitizenActor();
			if(citizen != null)
				return citizen.CellPhoneNumber()[0].number;
			else
				return citizen;
		};

		self.getCitizenActorCellPhoneError = function() {
			var citizen = self.getFirstCitizenActor();
			if(citizen != null)
				return citizen.CellPhoneNumber()[0].number.hasError;
			else
				return citizen;
		};
		
		self.getCitizenActorEmailCustomer = function() {
			var citizen = self.getFirstCitizenActor();
			if(citizen != null)
				return citizen.emailCustomer;
			else
				return citizen;
		};
		
		self.setScroller = function(val) {
			if(!U.isEmptyString(val))
				return true;
			else
				return false;
		};
		
		self.setTextAreaColor = function(val) {
			if(U.isEmptyString(val))
				return true;
			else
				return false;
		};
		
		self.setStatusColor = function() {
			var label = self.data().properties().hasStatus().label;
			if(label != 'undefined' && typeof label === 'function')
				label = label();
			if(self.duplicateCount() > 0 && label === 'O-DUP')
				return true;
			else
				return false;
		};
		
		self.dupChecker = function() {
	    	if(!U.isEmptyString(self.data().properties().hasXCoordinate()) && 
	    	   !U.isEmptyString(self.data().properties().atAddress().Street_Number()) && 
	    	   !U.isEmptyString(self.data().properties().atAddress().Zip_Code()) && 
	    	   !U.isEmptyString(self.data().type()))
	    	{
	    		duplicateCheck();
	    	}
	    };
		
		self.showDuplicates = function()
		{
			$("#sh_dialog_sr_duplicates_detail").dialog({ 'modal':true, 'width':1000, 'height':400, 'draggable':true });
		}
		
		self.removeDuplicates = function() {
			self.duplicateCount(-1);
			self.duplicateDetails({});
			if(!U.isEmptyString(self.data().type()))
			{
				if(U.isEmptyString(self.originalData().properties.hasStatus.iri))
					self.data().properties().hasStatus().iri(self.originalData().properties.hasStatus);
				else
				{
					self.data().properties().hasStatus().iri(self.originalData().properties.hasStatus.iri);
					self.data().properties().hasStatus().label(self.originalData().properties.hasStatus.label);
				}
			}
		};
		
		self.populateSR = function(el)
		{
		  	$("#sh_dialog_sr_duplicates_detail").dialog('close');
			$("#sh_dialog_alert")[0].innerText = "Do you want to open this SR ?";
			$("#sh_dialog_alert").dialog({ height: 150, width: 350, modal: true, buttons: {
				"Open" : function() {
					self.removeDuplicates();
					$(document).trigger(legacy.InteractionEvents.populateSH, [el.boid]);
					$("#sh_dialog_alert").dialog('close');
				},
				"Cancel": function() {
				  	$("#sh_dialog_alert").dialog('close');
					$("#sh_dialog_sr_duplicates_detail").dialog({ 'modal':true, 'width':600, 'height':400, 'draggable':true });
				}
			  } 
			});
		};
		
		self.getSRType = function(el)
		{
			if(U.isEmptyString(el.type))
				return self.srType().label + ' [' +self.srType().hasJurisdictionCode +']';
			var srTypeLabel = $.map(cirm.refs.serviceCases, function(v) {
				if(v.iri == el.type)
					return v.label + ' [' +v.hasJurisdictionCode + ']';
			});
			if(srTypeLabel.length > 0)
				return srTypeLabel[0];
			else
				return el.type;
		};

		self.computeFullAddress = function(el)
		{
			if(U.isEmptyString(el.Street_Unit_Number))
				return el.fullAddress;
			else
				return (el.fullAddress + " # " + el.Street_Unit_Number);
		};
		
		self.isParent = function(el)
		{
			var currentMillis = undefined;
			if(U.isEmptyString(self.data().boid()))
				currentMillis = self.getServerDateTime(true); 
			else
				currentMillis = U.getTimeInMillis(self.data().properties().hasDateCreated());
			if(self.duplicateCount() == 1)
			{
				return (currentMillis - el.hasDateCreated > 0 ? "PARENT" : "");
			}
			else if(self.duplicateCount() > 1)
			{
				if(el.boid === self.duplicateDetails()[0].boid)
				{
					return (currentMillis - self.duplicateDetails()[0].hasDateCreated > 0 ? "PARENT" : "");
				}
				else
					return "";
			}
			else
				return "";
		};
/*			
				var potentialParents = $.map(self.duplicateDetails(), function(v,i) {
					if(v.hasStatus =="O-OPEN")
						return v;
				});
				if(potentialParents.length == 1)
				{
					if(el.boid === potentialParents[0].boid)
						return (currentMillis - potentialParents[0].boid > 0 ? "PARENT" : "");
				}
				else if(potentialParents.length > 1)
				{
					potentialParents.sort( function(a,b) {
						return a.hasDateCreated - b.hasDateCreated;
					});
					if(el.boid === potentialParents[0].boid)
					{
						return (currentMillis - potentialParents[0].boid > 0 ? "PARENT" : "");
					}
				}
*/
		
		//Delete empty objects, modify email, 
		function modifyBO(jsondata)
		{
		    $.each(jsondata.properties.hasServiceCaseActor, function(i,v) {
		    	if(v.isAnonymous != undefined)
		    		delete v.isAnonymous;
		    	if(v.hasEmailAddress.label.indexOf("@") != -1) {
	    			v.hasEmailAddress.iri = 'mailto:'+v.hasEmailAddress.label;
	    		}
	    		//Delete the email address field if the email is empty
	    		if(U.isEmptyString(v.hasEmailAddress.label))
	    			delete v.hasEmailAddress;
	    		//Delete the whole address object if the fullAddress field is empty
//		    	if(U.isEmptyString(v.atAddress.fullAddress))
//		    		delete v.atAddress;
		    	if(v.atAddress)
		    	{
		    		$.each(v.atAddress, function(prop, value) {
		    			//Start: Outside City Fix
		    			if(prop == 'Street_Address_City')
		    			{
		    				value.iri = value.Name = value.label.trim().replace(/ /g,"_");
		    			}
		    			//End: Outside City Fix
		    			if(typeof value != 'object' && U.isEmptyString(value))
		    				delete v.atAddress[prop];
		    			if(typeof value == 'object' && U.isEmptyString(value.iri))
		    				delete v.atAddress[prop];
		    		});
		    	}
				$.each(v, function(prop,value) {
		    		//get all 5 contact numbers and convert them to strings from arrays
		    		if (prop.indexOf('Number') != -1)
		    		{
		    			var tempNumber = "";
		    			$.each(value, function(j, contactNo) {
		    				if(j != 0)
		    					tempNumber = tempNumber + ',';
		    				tempNumber = tempNumber + contactNo.number;
		    				if(!U.isEmptyString(contactNo.extn))
		    					tempNumber = tempNumber + "#" + contactNo.extn;
		    			});
		    			v[prop] = tempNumber;
		    			value = tempNumber;
		    		}
		    		//Delete all the data properties which are empty
					if(typeof prop != 'object' && U.isEmptyString(value))
						delete v[prop];
				});
		    });
		    
		    if(U.isEmptyString(jsondata.properties.hasXCoordinate))
			{
				delete jsondata.properties.hasXCoordinate;
				delete jsondata.properties.hasYCoordinate;
			}
		    if(U.isEmptyString(jsondata.properties.atAddress.fullAddress)) {
		    	delete jsondata.properties.atAddress;
		    	delete jsondata.properties.hasXCoordinate;
		    	delete jsondata.properties.hasYCoordinate;
		    	delete jsondata.properties.hasGisDataId;
		    }
		    else {
			    //Remove the fields from atAddress if the address is an Approximation Address
			    if(jsondata.properties.atAddress.fullAddress.indexOf("APPROX") != -1) {
			    	delete jsondata.properties.atAddress.Street_Direction;
			    	delete jsondata.properties.atAddress.hasStreetType;
			    	delete jsondata.properties.atAddress.Street_Number;
			    	delete jsondata.properties.atAddress.Street_Name;
			    }
			    //Remove Street_Direction and hasStreetType if addressType is not StreetAddress or Address
			    if(jsondata.properties.atAddress.addressType != "StreetAddress" 
			    	&& jsondata.properties.atAddress.addressType != "Address" 
			    	&& jsondata.properties.atAddress.addressType != "PointAddress")
			    {
			    	delete jsondata.properties.atAddress.Street_Direction;
			    	delete jsondata.properties.atAddress.hasStreetType;
			    }
	    		$.each(jsondata.properties.atAddress, function(prop, value) {
	    			var typeIs = typeof self.data().properties().atAddress()[prop]();
	    			if(typeIs != 'object' && U.isEmptyString(value))
	    				delete jsondata.properties.atAddress[prop];
	    			if(typeIs == 'object' && (value == null || U.isEmptyString(value.iri)))
	    				delete jsondata.properties.atAddress[prop];
	    		});
		    }
		    
		    var counter = 0;
		    //delete those ServiceAnswer's which are either not filled or not selected, basically empty/undefined.
		    var tempServiceAnswers = $.grep(jsondata.properties.hasServiceAnswer, function(v) {
		    		if(v.isOldData)
		    			return false;
		    		//FIX : Remove temp iri, if present, so newly created answers during update are persisted. 
			    	if(v.iri && v.iri.indexOf('#temp') != -1)
			    		delete v.iri;
		    		if(v.hasDataType == 'CHAROPT' || v.hasDataType == 'CHARLISTOPT') {
		    			var charOptArray = $('[name="charOptSelect"]');
		    			var charOptValue = charOptArray[counter++];
	    				var currentValue = $(charOptValue).attr('value');
	    				var isChoiceValue = $.map(v.hasChoiceValueList.hasChoiceValue, function(val) { 
	    					if(val.iri == currentValue)
	    						return val;
	    				});
	    				//If value is user input then replace hasAnswerObject with hasAnswerValue
	    				if (isChoiceValue.length == 0) {
	    					v.hasAnswerValue = {"literal":currentValue, "type":""};
	    					if(v.hasDataType == 'CHAROPT')
	    						v.hasAnswerValue.type = self.hasTypeMapping()[v.hasDataType];
	    					else
	    						v.hasAnswerValue.type = self.hasTypeMapping()['CHARLIST'];
	    					delete v.hasAnswerObject;
	    				}
		    		}
		    		if(v.hasDataType == 'CHARMULT') {
		    			if(U.isArray(v.hasAnswerObject.iri))
		    			{
		    				var tempHasAnswerObject = [];
		    				$.each(v.hasAnswerObject.iri, function(i,value) {
		    					if(!U.isEmptyString(value))
		    						tempHasAnswerObject.push({'iri':value});
		    				});
		    				if(!U.isEmptyString(tempHasAnswerObject))
		    				{
		    					v.hasAnswerObject = tempHasAnswerObject;
		    					return true; //return v;
		    				}
		    			}
		    		}
					//START : temp fix for NUMBER and DOUBLE datatypes
		    		if(v.hasAnswerValue && !U.isEmptyString(v.hasAnswerValue.literal) && v.hasDataType == 'NUMBER') {
                   		var remainder = v.hasAnswerValue.literal - Math.floor(v.hasAnswerValue.literal);
                   		if(remainder == 0)
                   			v.hasAnswerValue.literal = Math.floor(v.hasAnswerValue.literal);
                   		else
                   			v.hasAnswerValue.type = self.hasTypeMapping()['DOUBLE'];
		    		}
		    		//END : temp fix
		    		if(v.hasAnswerValue && v.hasAnswerValue.type == self.hasTypeMapping()['DATE'])
		    		{
		    			if(!U.isEmptyString(v.hasAnswerValue.literal))
		    			{
		    				v.hasAnswerValue.literal = new Date(v.hasAnswerValue.literal).asISODateString();
		    			}
		    		}
		    		if(v.hasAnswerValue && !U.isEmptyString(v.hasAnswerValue.literal)) 
		    			return true; //return v;
		    		if(v.hasAnswerObject && !U.isEmptyString(v.hasAnswerObject.iri)) 
		    			return true; //return v;
		    	});
		    jsondata.properties.hasServiceAnswer = U.ensureArray(tempServiceAnswers);
		    
		    var currentServerDate = self.getServerDateTime();
			$.each(jsondata.properties.hasServiceActivity, function(i,v) {
				//if the Outcome is not set, delete it.
				//either delete or populate hasCompletedTimestamp based on hasOutcome.
				if(!v.hasOutcome.iri || U.isEmptyString(v.hasOutcome.iri)) {
					delete v.hasCompletedTimestamp;
					delete v.hasOutcome;
				}
				else if(U.isEmptyString(v.hasCompletedTimestamp))
					v.hasCompletedTimestamp = currentServerDate.asISODateString();
				//delete empty data properties from the Activity
				$.each(v, function(prop, value) {
					if(typeof prop != 'object' && U.isEmptyString(value))
						delete v[prop];
				});
				//delete hasDueDate if it is empty
				//if(U.isEmptyString(v.hasDueDate) == true)
					//delete v.hasDueDate;
			});
			//delete old Activities
			jsondata.properties.hasServiceActivity = $.grep(jsondata.properties.hasServiceActivity, function(v) {
					if(v.isOldData)
						return false;
					else
						return true;
			});
			
			//Delete top level empty data properties
			if(U.isEmptyString(jsondata.properties.hasDetails))
				delete jsondata.properties.hasDetails;
			if(U.isEmptyString(jsondata.properties.hasLegacyId))
				delete jsondata.properties.hasLegacyId;
		};
		
		function prefixMaker(jsondata)
		{
		    var toprefix = U.set("hasServiceAnswer", "hasAnswerValue", "hasServiceField", "hasServiceCaseActor",
		        "ServiceAnswer", "hasServiceActor", "ServiceCaseActor", "hasServiceActivity",
		        "hasActivity", "hasCompletedTimestamp", "hasUpdatedDate","hasDetails",
		        "hasDueDate","isAssignedTo", "hasOutcome", "Outcome", "ServiceActivity", "hasOldData", "isAccepted",  
		        "hasStatus", "hasPriority", "hasIntakeMethod", "hasAnswerObject", "hasCaseNumber", 
                        "hasParentCaseNumber", "hasGisDataId", "hasLocationDetails", "hasDepartmentError");
		    var toignore = U.set("hasLegacyCode", "hasLegacyId", "addressType", "label", "hasChoiceValueList", 
		    	"hasDataType", "hasOrderBy", "hasAnswerUpdateTimeout", "transient$protected", "description", 
		        "description2", "description3", "description4", "description5", "description6", "comment", "isOldData", 
		        "participantEntityTable", "hasBusinessCodes", "hasAllowableModules", "folio" , "isDisabled", "isAlwaysPublic",
		        "extendedTypes", "hasAllowableStatuses", "isHighlighted", "isAutoAssign", "fromDiffSRType", "hasStandardizeStreetFormat", "hasAnswerConstraint");
		    
		    return U.visit(jsondata, function(n,v,parent) {
		            if (toignore[n])
	                	delete parent[n];
		            else if (toprefix[n]) {
		                parent["legacy:" + n] = parent[n];
		                delete parent[n];
		            }
		            //Add prefix to type of ServiceCaseActor and ServiceActivity
		            if(n == "type")
		            	if(v == "ServiceActivity" || v == "ServiceCaseActor")
		            		parent[n] = "legacy:"+v;
		    });
		}
		
		function isServiceCaseValid(data) {
			var isValid = true;

			if(data.properties().hasDetails.hasError && data.properties().hasDetails.hasError())
        		return false;			
        	if(data.properties().hasStatus().iri.hasError && data.properties().hasStatus().iri.hasError())
        		return false;
        	if(data.properties().hasPriority().iri.hasError && data.properties().hasPriority().iri.hasError())
        		return false;
        	if(data.properties().hasIntakeMethod().iri.hasError && data.properties().hasIntakeMethod().iri.hasError())
        		return false;
			
			$.each(data.properties().hasServiceAnswer(), function(i,v) {
				if(v.hasAnswerValue && v.hasAnswerValue().literal.hasError && v.hasAnswerValue().literal.hasError()) {
					isValid = false;
					return false;
				}
				if(v.hasAnswerValue) {
					if(v.hasAnswerValue().literal.hasError && v.hasAnswerValue().literal.hasError()) {
						isValid = false;
						return false;
					}
					if(v.hasAnswerValue().literal.conditionallyRequired && v.hasAnswerValue().literal.conditionallyRequired()
							&& v.hasAnswerValue().literal.isStandardizedStreet && !v.hasAnswerValue().literal.isStandardizedStreet()) {
						isValid = false;
						return false;
					}
				}
				if(v.hasAnswerObject && v.hasAnswerObject().iri.hasError && v.hasAnswerObject().iri.hasError()) {
					isValid = false;
					return false;
				}
			});
			$.each(data.properties().hasServiceCaseActor(), function(i, v) {
				$.each(v, function(prop, value){
					if(prop.indexOf("Number") != -1) {
						$.each(value(), function(j,val) {
							if( (val.number.hasError && val.number.hasError()) || (val.extn.hasError && val.extn.hasError()) )
							{
								isValid = false;
								return false;
							}
						});
						if(!isValid)
							return false;
					}
				});
				if(!isValid)
					return false;
				if(v.hasEmailAddress().label.hasError && v.hasEmailAddress().label.hasError())
				{
					isValid = false;
					return false;
				}
/*
				if(v.HomePhoneNumber.hasError() == true || v.CellPhoneNumber.hasError() == true || 
				   v.BusinessPhoneNumber.hasError() == true || v.FaxNumber.hasError() == true || 
				   v.OtherPhoneNumber.hasError() == true || v.hasEmailAddress().label.hasError() == true) {
					isValid = false;
					return false;
				}
*/
			});
			// Address fields validation check
			if(self.isAddressValidationEnabled()) {
				if(data.properties().atAddress().fullAddress.hasError() || 
				   data.properties().atAddress().Zip_Code.hasError() || 
				   data.properties().atAddress().Street_Unit_Number.hasError() ||
				   data.properties().atAddress().Street_Address_City().label.hasError()) {
					isValid = false;
					return false;
				}
			}
			return isValid;
		};

    	self.getFilteredSCList = function() {
			return function(s, back) {
				var matcher = new RegExp("\\b" + $.ui.autocomplete.escapeRegex(s.term), "i" );
				back($.grep( cirm.refs.autoCompleteServiceCaseList, function(value) {
					return matcher.test( value.label || value.value || value );
			})) };
    	};

    	self.validateOnEnter = function(data, event) {
    		//jQuery Bug: If an UI Dialog is present with buttons to decide on the next course of action, 
    		//we have to call the event's preventDefault function on ENTER (Return Key) as the Dialog is being ignored.
    		if(event.keyCode == 13)
    			event.preventDefault();
    	    if(event.keyCode == 9 || event.keyCode == 13) {
   	            self.srTypeLookup();
    	    }
    	    return true;    	    
    	};

    	self.validateOnTab = function(data, event) {
    	    if(event.keyCode == 9 || event.keyCode == 13) {
   	            self.searchAddress();
    	    }
    	    return true;    	    
    	};

		self.removeAddressIRI = function(atAddress)
		{
			if(atAddress.iri)
				atAddress.iri("");
			if(atAddress.label)
				atAddress.label("");
		}

		self.searchAddress = function() {
			$("#sh_dialog_address_search").dialog({ height: 140, modal: true });
			$("#sh_dialog_address_search").dialog('close');
			self.removeAddressIRI(self.data().properties().atAddress());
			$(document).trigger(legacy.InteractionEvents.SearchAddress, [self.data().properties().atAddress()]);
		};
		
    	self.searchFolioOnEnter = function(data, event) {
    		if(event.keyCode == 13) {
    			self.searchFolio();
    		}
    		return true;
    	};

		self.searchFolio = function() {
			$("#sh_dialog_address_search").dialog({ height: 140, modal: true });
			$("#sh_dialog_address_search").dialog('close');
			self.removeAddressIRI(self.data().properties().atAddress());
			$(document).trigger(legacy.InteractionEvents.SearchFolio, [self.data().properties().atAddress().folio()]);
		};

		self.saveOffline = function(model) {
		    var data = ko.toJS(model.data())
		    console.log('save model offline', data);
		    store.cases.put(data);
		};
		
		self.doEmail = function(model) {
			if(!U.isEmptyString(model.data().boid()))
				$("#sh_dialog_email").dialog({height: 300, width:600, modal: true});
			else if(U.isEmptyString(model.data().type()))
				alertDialog('No Service Request available to email');
			else 
				alertDialog('Cannot email an unsaved Service Request');
		};
		
		self.sendEmail = function(model) {
			$("#sh_dialog_address_search").dialog({ height: 140, modal: true });
			$("#sh_dialog_address_search").dialog('close');
			var send = ko.toJS(model.emailData);
			send.boid = model.data().boid();
			
			if(U.isEmptyString(send.subject))
			{
				alertDialog("'eMail Subject*' is mandatory");
				return false;
			}
			if(U.isEmptyString(send.to))
			{
				alertDialog("'eMail TO:*' is mandatory");
				return false;
			}
			else
			{
				var failedEmailList = getInvalidEmailIds(send.to);
				if(failedEmailList.length > 0)
				{
					alertDialog("Invalid email ids in 'eMail TO:*' "+failedEmailList.toString());
					return false;
				}
			}
			if(U.isEmptyString(send.cc))
				delete send.cc;
			else
			{
				var failedEmailList = getInvalidEmailIds(send.cc);
				if(failedEmailList.length > 0)
				{
					alertDialog("Invalid email ids in 'eMail CC': "+failedEmailList.toString());
					return false;
				}
			}
			if(U.isEmptyString(send.bcc))
				delete send.bcc;
			else
			{
				var failedEmailList = getInvalidEmailIds(send.bcc);
				if(failedEmailList.length > 0)
				{
					alertDialog("Invalid email ids in 'eMail BCC': "+failedEmailList.toString());
					return false;
				}
			}
			$("#sh_dialog_email").dialog('close');
			$("#sh_email_progress").dialog({height: 140, modal: true});
			cirm.top.async().post("/legacy/emailSR", {data:JSON.stringify(send)}, function(result) {
				$("#sh_email_progress").dialog('close');
				if(result.ok == true)
					alertDialog("Successfully sent the eMail.");
                else if(result.ok == false)
					showErrorDialog("An error occurred while sending the eMail : <br>"+result.error);
            });
		};

		//Checks if the email ids are valid against the emailFilter
		// and returns the list of invalid email ids. If none, returns empty list.
		function getInvalidEmailIds(IDs)
		{
			var emailFilter = /^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,4}$/;
			return $.map(IDs.split(';'), function(v) {
				if(!emailFilter.test(v.trim())) {
					return v.trim();
				}
			});
		}

		self.cancelEmail = function(model) {
			self.clearEmail();
			$("#sh_dialog_email").dialog('close');
		};
		
		self.clearEmail = function() {
			self.emailData().subject("");
			self.emailData().to("");
			self.emailData().cc("");
			self.emailData().bcc("");
			self.emailData().comments("");
		};

    	self.doPrint = function(model) {
    		if(!U.isEmptyString(model.data().boid()))
				//U.download("/legacy/printView", {boid:model.data().boid()}, true);
			{
				$("#sh_dialog_alert")[0].innerText = "Do you want to print the Service Request ?";
				$("#sh_dialog_alert").dialog({ height: 150, width: 500, modal: true, buttons: {
					"Continue" : function() {
						$("#sh_dialog_alert").dialog('close');
						U.download("/legacy/printView", {boid:model.data().boid()}, true);
					},
					"Cancel" : function() { $("#sh_dialog_alert").dialog('close'); }
				} 
				});
			}
    		else if(U.isEmptyString(model.data().type()))
				alertDialog('No Service Request available to print');
    		else 
				alertDialog('Cannot print an unsaved Service Request');
    	};
		
		//Opens the HET Rebate letter in a new popup window
		self.printHETRebateLetter = function (el)
		{
    		var isEnglish = undefined;
    		if(el.hasActivity().iri() == "http://www.miamidade.gov/cirm/legacy#WASDHETR_WSWCA3") 
    			isEnglish = true;
    		else if(el.hasActivity().iri() == "http://www.miamidade.gov/cirm/legacy#WASDHETR_WSWCA3A")
    			isEnglish = false;
    		var applicant = $.map(self.data().properties().hasServiceCaseActor(), function(v){
    			if(v.hasServiceActor().iri() == "http://www.miamidade.gov/cirm/legacy#APPLICAN")
    				return ko.toJS(v);
    		});
    		if(applicant.length == 0)
    			alertDialog("Please create a Customer of type 'Applicant' and add FirstName, LastName and address information");
    		else if(applicant.length == 1)
    		{
	    		if(U.isEmptyString(applicant[0].Name) || U.isEmptyString(applicant[0].LastName))
	    			alertDialog("Please enter the FirstName/LastName of the Applicant Customer");
	    		if(U.isEmptyString(applicant[0].atAddress.fullAddress))
	    			alertDialog("Please enter the address of the Applicant Customer");
	    		else
	    		{
	    			U.download("/legacy/hetRebateLetter", 
	    						{"applicantInfo": JSON.stringify(applicant[0]),
									"hasCaseNumber": self.data().properties().hasCaseNumber(),
									"isEnglish": isEnglish}, 
	    						true);
	    		}
    		}
		};

		function emailstoActors(jsondata)
		{
			var emailCustomers = [];
			$.each(jsondata.properties.hasServiceCaseActor, function(i,v) {
				if(v.emailCustomer && v.hasEmailAddress && !U.isEmptyString(v.hasEmailAddress.iri))
				{
					var customer = {"name":"", "email":""};
					if(v.Name)
						customer.name = customer.name + v.Name;
					if(v.LastName)
						customer.name = customer.name + " " +v.LastName;
					if(U.isEmptyString(customer.name))
						customer.name = "Customer";
					customer.email = v.hasEmailAddress.iri.split(':')[1];
					emailCustomers.push(customer);
				}
				delete v.emailCustomer;
			});
			jsondata.properties.actorEmails = emailCustomers;
		};

    	self.postNewCase = function (jsondata, model) {
           
            
            
            
            
            
    		jsondata.properties.hasDateCreated = self.getServerDateTime().asISODateString();
            jsondata.properties.isCreatedBy = cirm.user.username;
            var send = prefixMaker(jsondata);
            //console.log("send", send);
			$("#sh_save_progress").dialog({height: 140, modal: true, dialogClass: 'no-close'});
            cirm.top.async().post("/legacy/kosubmit", {data:JSON.stringify(send)}, function(result) {
                //console.log("result", ko.toJS(result));
                if(result.ok == true) {
                    $(document).trigger(legacy.InteractionEvents.UserAction, 
                        ["SR Created", result.bo.type + ":" + result.bo.properties.hasCaseNumber]);
                    $("#sh_save_progress").dialog('close');
                    alertDialog("Successfully saved SR. The SR ID is "+result.bo.properties.hasCaseNumber);
                    var srid = result.bo.properties.hasCaseNumber;
                    $('[name="SR Lookup"]').val(result.bo.properties.hasCaseNumber);
                    var type = result.bo.type;
                    if(type.indexOf("legacy:") == -1) { 
                        type = "legacy:"+type;
                        result.bo.type = type;
                    }
                    self.removeDuplicates();
                    setModel(result.bo, model, model.srType(), type, true, false);
               
                    var meta = []; 
                    meta.push(new metadata("sr",srid));
                    meta.push(new metadata("type",type));
                   
                    //2149 Optional Image upload - disabled meta until next week
                    //Enabled again
                    updateMetadata(meta, self.data().properties().hasAttachment()); 
                }
                else if(result.ok == false) {
                    $("#sh_save_progress").dialog('close');
                  showErrorDialog("An error occurred while saving the Service Request : <br>"+result.error);
                }
            });    	    
    	
            
    	
    	};
    	
    	  self.updateExistingCase = function (jsondata, model) {
          jsondata.properties.hasDateLastModified = self.getServerDateTime().asISODateString();
          jsondata.properties.isModifiedBy = cirm.user.username;
          var send = prefixMaker(jsondata);
          //console.log("send", send);
		  $("#sh_save_progress").dialog({height: 140, modal: true, dialogClass: 'no-close'});

		  var upcontinuation = function(result) {
		  		var wasPendingApprovalBeforeSave = model.isPendingApproval;
                //console.log("result", ko.toJS(result));
                if(result.ok == true) {
                    $(document).trigger(legacy.InteractionEvents.UserAction, 
                        ["SR Updated", result.bo.type + ":" + result.bo.properties.hasCaseNumber]);
                    $("#sh_save_progress").dialog('close');
                    alertDialog("Successfully updated SR : "+ result.bo.properties.hasCaseNumber);
                    var type = result.bo.type;
                    if(type.indexOf("legacy:") == -1) {
                        type = "legacy:"+type; 
                        result.bo.type = type;
                    }
                    self.removeDuplicates();
                    setModel(result.bo, model, model.srType(), type, true, false);
                    //continue isPendingApproval if sr was pending approval before update
                    //and is in pending state after update
                    if (result.bo.properties().hasStatus().iri().indexOf('O-PENDNG') >=0
                    	&& wasPendingApprovalBeforeSave) {
                    	model.isPendingApproval = true;
                    }
                }
                else if(result.ok == false) {
                    $("#sh_save_progress").dialog('close');
                    var caseNumber = send.properties['legacy:hasCaseNumber'];
                    showErrorDialog("An error occurred while updating the Service Request # "+caseNumber+" : <br>"+result.error);
                }
          };
          if (send.properties['legacy:hasStatus'].iri.indexOf('X-ERROR') > -1)
          {
              cirm.top.async().postObject( '/legacy/departments/send', send, upcontinuation);
          }
          else if(model.isPendingApproval && send.properties['legacy:hasStatus'].iri.indexOf('O-OPEN') > -1)
          {
          	  //This must fail at the server if another user already approved the same case,
          	  //after this user loaded the case in needsApproval status.
        	  console.log("validating address from open");
        	  self.searchAddress();
        	  cirm.top.async().postObject('/legacy/sr/approve', send, upcontinuation);
          }
          else
          {
              cirm.top.async().post('/legacy/update', {data:JSON.stringify(send)}, upcontinuation);
          }
    	};
    	
    	
      	self.doSubmit = function(model) { 
			
      		//2561 change the isCreatedBy if it was submitted by 311 direct 
      		if(model.isPendingApproval)
      		{
      			//console.log("SR came from 311 direct changing created by");
      			model.data().properties().isCreatedBy(cirm.user.username); 
      		}
      		
      		
      		
      		var jsondata = ko.toJS(model.data);
			//console.log("jsondata", jsondata);
			if(!jsondata.type) {
				alertDialog('No Service Request created/opened to Save');
				return;
			} 
			else if (!cirm.user.isUpdateAllowed(model.data().type().split(":")[1]))
			{
				alertDialog('Your group does not have permission to update this SR.');
				return;
			}	
			if(!isServiceCaseValid(model.data()))
			{
				var msg = "Please add values to all the fields highlighted in RED. \n";
				msg = msg + "Don't forget to scroll through all the Questions or open the SR Customers tab to see which fields are mandatory. \n";
				msg = msg + " Also, please select a valid Unit Number when 'MULTI'";
				alertDialog(msg);
				return;
			}			
			if (!interfaceValidation.isValidForInterface(jsondata)) {
				//This service request was an interface service request and specific validation for it's interface failed.
				alertDialog('Interface Validation for this Service Request failed: \n\n' + interfaceValidation.getValidationMessage())
				return;
			}
			if(jsondata.properties.hasServiceCaseActor.length > 0) {
				var tempSA = $.map(jsondata.properties.hasServiceCaseActor, function(v) {
					if(!U.isEmptyString(v.atAddress.fullAddress) && 
							v.atAddress.Street_Address_State.iri === undefined) {
						return v;
					}
				});
				if(tempSA.length > 0)
				{
					alertDialog("Service Actor's State can't be empty if filling the Service Actor Address");
					return;
				}
			}
			modifyBO(jsondata);
			emailstoActors(jsondata);
			if(jsondata.boid == "")
/*			    if (U.offline || true)
			        store.ononline.put({type:'newcase', id:store.newid(),data:jsondata});
			    else */
			        self.postNewCase(jsondata, model);
			else
/*			    if (U.offline || true)
			        store.ononline.put({type:'updatecase', id:store.newid(),data:jsondata});
			    else */
			        self.updateExistingCase(jsondata, model);
    	};
    	
    	self.addExtendersToActor = function(el, isFirstCitizen)
    	{
			el.hasEmailAddress().label.extend({ email : ""});
			
			$.each(el, function(prop,value) {
				if(prop.indexOf('Number') != -1)
				{
					$.each(value(), function(i,v) {
						v.extn.extend({ extension : ""});
						if(isFirstCitizen && prop.indexOf('Cell') != -1 && i == 0)
							v.number.extend({ phone_required : "Phone Required"});
						else
							v.number.extend({ phone : ""});
					});
				}
			});
    	};
    	
    	self.addContactNumber = function(el, event) {
    		var index = self.actorIndex(el);
    		if($('#phoneTypeList_'+index).val() == 'undefined')
    			alertDialog("Please select a Contact Number Type");
    		else
    		{
    			var item = {"number":"", "extn":""};
				U.visit(item, U.makeObservableTrimmed);
    			item.number.extend({phone:""});
    			item.extn.extend({extension:""});
    			el[$('#phoneTypeList_'+index).val()].push(item);
    		}
    	};

    	self.deleteContactNumber = function(el, phoneNumberType, parent) {
			$("#sh_dialog_alert")[0].innerText = "Are you sure you want to delete this "+phoneNumberType;
			$("#sh_dialog_alert").dialog({ height: 150, width: 400, modal: true, buttons: {
				"Delete" : function() {
					parent[phoneNumberType].remove(el);
					$("#sh_dialog_alert").dialog('close');
				},
				"Cancel": function() {
				  	$("#sh_dialog_alert").dialog('close');
				}
			  } 
			});
    	};

    	self.isCustomerLocked = function() {
			if(self.isLockedStatus())
			{
    			if(self.isPWWMCase())
    				return false;
    			else
    				return true;
			}
			else
				return false;
    	};
    	
    	self.addActor = function() {
    		var iri = $('#serviceActorList').val();
    		if(U.isEmptyString(iri))
    		{
    			alertDialog("Please select a valid Customer");
    			return false;
    		}
    		var label = $('#serviceActorList option:selected').text();
			var tempSA = U.visit(new ServiceActor(iri, label, cirm.user.username, self.getServerDateTime().asISODateString(), true), U.makeObservableTrimmed);
			//START : Extenders for fields which need validation
           	var isFirstCitizen = (!self.isCustomerLocked() && isCitizenActor(iri) && self.getFirstCitizenActor() == null) ? true : false;
			self.addExtendersToActor(tempSA, isFirstCitizen);
			//END 
    		self.data().properties().hasServiceCaseActor.unshift(tempSA);
			collapseActors(0);
			patchPlaceholdersforIE();
    	};
    	
    	self.removeActor = function(actor) {
			$("#sh_dialog_alert")[0].innerText = "Are you sure you want to delete this Actor";
			$("#sh_dialog_alert").dialog({ height: 150, width: 350, modal: true, buttons: {
				"Delete" : function() {
					self.data().properties().hasServiceCaseActor.remove(actor);
					$("#sh_dialog_alert").dialog('close');
				},
				"Cancel": function() {
				  	$("#sh_dialog_alert").dialog('close');
				}
			  } 
			});
    	};
    	
    	function collapseActors(el)
    	{
			$.each(self.data().properties().hasServiceCaseActor(), function(i,v) {
				$('#actorDetailsRow_'+i).hide();
				$('[name=actorRow_'+i+']').attr('style', 'background-color: rgb(transparent);');
			});
			if(el != undefined)
			{
	    		$('#actorDetailsRow_'+el).show();
				$('[name=actorRow_'+el+']').attr('style', 'background-color: rgb(238, 238, 238);');
			}
    	};
    	
    	function collapseActivities(el)
    	{
			$.each(self.data().properties().hasServiceActivity(), function(i,v) {
				$('#activityDetailsRow_'+i).hide();
				$('[name=activityDetailsRow_'+i+']').show();
				$('[name=activityRow_'+i+']').attr('style', 'background-color: rgb(transparent);');
			});
			if(el != undefined)
			{
	    		$('#activityDetailsRow_'+el).show();
				$('[name=activityDetailsRow_'+el+']').hide();
				$('[name=activityRow_'+el+']').attr('style', 'background-color: rgb(238, 238, 238);');
			}
    	};
    	
    	self.isPWWMCase = function() {
    		if(self.srType().providedBy)
    		{
    			if(self.srType().providedBy.hasParentAgency && self.srType().providedBy.hasParentAgency.indexOf('Public_Works_Waste_Management') > 0)
    				return true;
    			else if(self.srType().providedBy.Department && self.srType().providedBy.Department.indexOf('Public_Works_Waste_Management') > 0)
    				return true;
    			else
    				return false;
    		}
    		else
    			return false;
    	};
    	
    	self.activityList = function() {
    		var tempActs = $.grep(self.srType().hasActivity, function(v) {
    			if(v.isDisabled != undefined && v.isDisabled == 'true') {
    				 return false; //ignore the element
    			}
    			if(self.data().boid().length > 0 && self.data().properties().hasStatus().label().toLowerCase().indexOf('open') == -1) {
	    			//If status locked then should be able to add only Personal Contact Activity.
	    			if(self.isLockedStatus()) {
	    				//If SR is LOCKED then always show 'Personal Contact' Activity
		    			if(v.hasLegacyCode && v.hasLegacyCode == "PERSCNTC")
	    					return true;
		    			//if PW/WM SR then show 'SWM Urgent Notification' Activity
		    			if(self.isPWWMCase() && v.hasLegacyCode && v.hasLegacyCode == "SWMURGN")
	    					return true;
		    		}
	    			else if(v.hasBusinessCodes && v.hasBusinessCodes.indexOf("INSSPEC") != -1) 
			    			return true; 
    			}
    			else
    				return true;
    		});

			return tempActs.sort(function(a,b) {
				a.label.toUpperCase().localeCompare(b.label.toUpperCase());
			});
		};

    	self.addActivity = function() {
    		var iri = $('#serviceActivityList').val();
    		if(U.isEmptyString(iri))
    		{
    			alertDialog("Please select a valid Activity");
    			return false;
    		}
    		var label = $('#serviceActivityList option:selected').text();
    		var srTypeAct = $.map(self.srType().hasActivity, function(v) {
    			if(v.iri == iri)
    				return v;
    		});
    		var newSA = new ServiceActivity(iri, label, cirm.user.username, self.getServerDateTime().asISODateString(), true);
    		if(srTypeAct.length > 0)
    		{
				newSA.isAutoAssign = srTypeAct[0].isAutoAssign;
				newSA.hasLegacyCode = srTypeAct[0].hasLegacyCode;
    		}
    		self.data().properties().hasServiceActivity.unshift(U.visit(newSA, U.makeObservableTrimmed));
    		//var el = self.data().properties().hasServiceActivity().length-1;
			collapseActivities(0);
			patchPlaceholdersforIE();
    	};

		self.removeActivity = function(activity) {
			$("#sh_dialog_alert")[0].innerText = "Are you sure you want to delete this Activity";
			$("#sh_dialog_alert").dialog({ height: 150, width: 350, modal: true, buttons: {
				"Delete" : function() {
					self.data().properties().hasServiceActivity.remove(activity);
					$("#sh_dialog_alert").dialog('close');
				},
				"Cancel": function() {
				  	$("#sh_dialog_alert").dialog('close');
				}
			  } 
			});
		};
		
		self.checkNow = ko.computed(
		{
			read: function() {},
			write: function(label, parentLabel) {
				if(label() == parentLabel) { return true; }
				else { return false; } 
			}
		}); 

		function getActivityType(activity)
		{
			return $.grep(self.srType().hasActivity, function (t) {
				if(typeof activity.hasActivity == "object") 
					return activity.hasActivity.label == t.label;
				else if(typeof activity.hasActivity == "function")
					return activity.hasActivity().label() == t.label;
			});
		}
		
		function activityHasOutcome(activity, outcome)
		{
			var isPresent = false;
			$.each(activity.hasAllowableOutcome, function(t) {
				if(t.iri == outcome.iri())
				{
					isPresent = true;
					return false;
				}
				else 
					return true;
			});
			return isPresent;
		}
		
		self.getAllowableOutcomes = function(activity) {
			var activityType = getActivityType(activity);
			var A = U.ensureArray(activityType[0].hasAllowableOutcome);
			return $.map(A, function(el) {
				if(el.isDisabled != 'true')
					return {iri:el.iri, label:el.label};
				else if(el.isDisabled == 'true' && activity.hasOutcome().iri() == el.iri)
					return {iri:el.iri, label:el.label};
			});
		};
		
		self.isSelectedOption = function(el, option) {
			if(el.hasAnswerObject().iri().indexOf(option()) != -1)
				return true;
			else
				return false;
		};
		self.getServiceType = function(type) {
    		if(typeof type == "object")
    			return type.label; 
    		else if(typeof type == "function") 
    			return type().label;
		};
		
		self.checkAddress = function(address) {
			if(typeof address == "object")
				return true;
			else if(typeof address == "function")
				return false; 
		};
		
		self.maybeAlertPopup = function(answer, e) {
			var ans = ko.toJS(answer);
			var serviceFields = self.srType().hasServiceField;
			var serviceField = $.map(serviceFields, function(v) {
				if(v.iri == ans.hasServiceField.iri)
					return v;
			});
			if(serviceField.length == 1)
			{
				if(serviceField[0].hasServiceFieldAlert) {
					if(serviceField[0].hasServiceFieldAlert === undefined)
						return true;
					serviceField[0].hasServiceFieldAlert = U.ensureArray(serviceField[0].hasServiceFieldAlert);
					var alertMsg = undefined;
					$.each(serviceField[0].hasServiceFieldAlert, function(i,v) {
						if(v.hasAnswerObject && v.hasAnswerObject.iri == ans.hasAnswerObject.iri) {
							alertMsg = v.hasLegacyEvent.label;
							return false;
						}
					});
					if(alertMsg != undefined)
						alertDialog(alertMsg);
				}
				//check for events which enable/disabe or extend (with validators) other questions
				if(U.isEmptyString(ans.hasAnswerObject.iri) && serviceField[0].hasLegacyEvent && serviceField[0].hasLegacyEvent.type == "ClearServiceField") {
					self.clearQuestions(serviceField[0]);
				}
				else if(!U.isEmptyString(ans.hasAnswerObject.iri) && ans.hasChoiceValueList && ans.hasChoiceValueList.hasChoiceValue) {
					self.requiredQuestions(serviceField[0], ans);
					self.disableQuestions(serviceField[0], ans);
				}
			}
		};
		
		self.fetchIndividual = function(indIRI) {
			return cirm.top.get('/individuals/legacy:' + indIRI.split('#')[1]);
		};
		
		self.clearQuestions = function(serviceField) {
			var legacyEventObject;
			if(typeof serviceField.hasLegacyEvent == "object")
				legacyEventObject = serviceField.hasLegacyEvent; 
			else
				legacyEventObject = self.fetchIndividual(serviceField);
			$.each(legacyEventObject.hasServiceField, function(i,v) {
				//If disabled earlier, remove it
				$('#'+v.hasLegacyCode).prop('disabled', false);
				//If made required earlier, remove it
				$.each(self.data().properties().hasServiceAnswer(), function(j, val) {
					if(v.iri == val.hasServiceField().iri()) {
						val.hasAnswerValue().literal.conditionallyRequired(false);
						val.hasAnswerValue().literal.hasError(false);
						val.hasAnswerValue().literal.validationMessage("");
					}
				});
			});
		};

		self.disableQuestions = function(serviceField, ans) {
			var disableQtns = $.map(ans.hasChoiceValueList.hasChoiceValue, function(v) {
				var legacyEventObject;
				if(U.isEmptyString(v.hasLegacyEvent))
					return;
				if(typeof v.hasLegacyEvent =="object")
					legacyEventObject = v.hasLegacyEvent;
				else
					legacyEventObject = self.fetchIndividual(v.hasLegacyEvent);
				if(legacyEventObject && legacyEventObject.type == "MarkServiceFieldDisabled" && ans.hasAnswerObject.iri == v.iri) {
					return legacyEventObject.hasServiceField;
				}
			});
			if(disableQtns.length > 0)
				self.clearQuestions(serviceField);
			$.each(disableQtns, function(i,v) {
				$('#'+v.hasLegacyCode).prop('disabled', true);
				//remove validations and empty the answers
				$.each(self.data().properties().hasServiceAnswer(), function(j, val) {
					if(v.iri == val.hasServiceField().iri()) {
						val.hasAnswerValue().literal.conditionallyRequired(false);
						val.hasAnswerValue().literal.isStandardizedStreet(false);
						val.hasAnswerValue().literal.hasError(false);
						val.hasAnswerValue().literal.validationMessage("");
						val.hasAnswerValue().literal("");
					}
				});
			});
		};

		self.requiredQuestions = function(serviceField, ans) {
			var requiredQtns = $.map(ans.hasChoiceValueList.hasChoiceValue, function(v) {
				var legacyEventObject;
				if(U.isEmptyString(v.hasLegacyEvent))
					return;
				if(typeof v.hasLegacyEvent =="object")
					legacyEventObject = v.hasLegacyEvent;
				else
					legacyEventObject = self.fetchIndividual(v.hasLegacyEvent);
				if(legacyEventObject && legacyEventObject.type == "MarkServiceFieldRequired" && ans.hasAnswerObject.iri == v.iri) {
					return $.map(legacyEventObject.hasServiceField, function(val){
						return val.iri;
					});
				}
			});
			if(requiredQtns.length > 0)
				self.clearQuestions(serviceField);
			$.each(requiredQtns, function(i,v) {
				$.each(self.data().properties().hasServiceAnswer(), function(j, val) {
					if(v == val.hasServiceField().iri()) {
						val.hasAnswerValue().literal.conditionallyRequired(true);
						if(U.isEmptyString(val.hasAnswerValue().literal())) {
							val.hasAnswerValue().literal.hasError(true);
							val.hasAnswerValue().literal.validationMessage("Required");
						}
					}
				});
			});
		};

		self.removeActivityCheckerTemplate = function(el) {
			if(el.iri)
				return "emptyTemplate";
			else
				return "RemoveActivityTemplate";
		};
		
		self.isOpenStatus = function() {
			if(self.data().properties().hasStatus().label().toLowerCase().indexOf('open') == -1)
				return false;
			else
				return true;
		}

		self.disabledAssignedTo = function(el) {
			if(el.isOldData())
				return true;
			if(el.iri && !U.isEmptyString(el.iri()))
			{
				//Start: existing completed activity after SR of default timeout
				if(!isActivityUpdateWithinTimeout(el) && !U.isEmptyString(el.hasCompletedTimestamp()))
					return true;
				if(self.isLockedStatus())
				{
					if(el.hasLegacyCode && el.hasLegacyCode() == "PERSCNTC")
					{
						if(!U.isEmptyString(el.isAutoAssign()))
							return true;
	    				else if(!U.isEmptyString(el.hasCompletedTimestamp()))
	    					return true;
						else
							return false;
					}
	    			//Temp Fix : if PW/WM SR then 'SWM Urgent Notification'
					else if(self.isPWWMCase() && el.hasLegacyCode && el.hasLegacyCode() == "SWMURGN")
					{
						if(!U.isEmptyString(el.isAutoAssign()))
							return true;
	    				else if(!U.isEmptyString(el.hasCompletedTimestamp()))
	    					return true;
						else
							return false;
					}
					else
						return true;
				}
			}
			else
			{
				if(!U.isEmptyString(el.isAutoAssign()))
					return true;
				else
					return false;
			}
		};

		self.disabledHasDetails = function(el) {
			if(el.isOldData())
				return true;
			//disable for existing completed activities after SR or default timeout	
			if(el.iri && !U.isEmptyString(el.iri()) 
				&& !isActivityUpdateWithinTimeout(el)
				&& !U.isEmptyString(el.hasCompletedTimestamp())
			  )
				return true;
			if(!self.isOpenStatus())
			{
				if(self.isLockedStatus())
				{
					//status is locked and personal contact not completed, then dont disable details
					if(el.hasLegacyCode && el.hasLegacyCode() == "PERSCNTC")
					{
						if(!U.isEmptyString(el.hasCompletedTimestamp()))
							return true;
						else
							return false;
					}
					//Temp Fix : if PW/WM SR then 'SWM Urgent Notification'
					else if(self.isPWWMCase() && el.hasLegacyCode && el.hasLegacyCode() == "SWMURGN")
					{
	    				if(!U.isEmptyString(el.hasCompletedTimestamp()))
	    					return true;
						else
							return false;
					}
					else
						return true;
				}
				else if(el.iri && !U.isEmptyString(el.iri()))
					return true;
			}
			else
				return false;
		};
		
		self.lookupAssignedStaff = function(el) {
			//$("#sh_dialog_address_search").dialog({ height: 140, modal: true }).dialog('close');
			return function(s, back) {
				var results = [];
				if(s.term.length >= 3)
				{
					$.grep(cirm.top.get('/users/search?name='+s.term+"&providers=onto,enet"), function(v) {
						var val = {"label":v.LastName + ", " + v.FirstName, "value":v.hasUsername};
						results.push(val);
					});
					back(results);
				}
			};
		};

		self.hasDefaultOutcome = function(el) {
			var defaultOutcomeList = $.map(self.srType().hasActivity, function(v,i) {
				if(el.hasActivity().iri() == v.iri && v.hasDefaultOutcome != undefined)
					if (!v.isAutoDefaultOutcome) {
						return v;
					}
			});
			if(defaultOutcomeList.length > 0)
				return true;
			else
				return false;
		};
		
		self.outcomeTemplate = function(el) {
			if(el.isOldData())
			{
				var activityType = getActivityType(el);
				if(activityType.length == 0)
					return "OutcomeInputTemplate";
				if(activityType.length > 0)
				{
					if(activityHasOutcome(activityType[0], el.outcome()))
						return "OutcomeDisabledTemplate";
					else
						return "OutcomeInputTemplate";
				}
			}
			//START : if existing activity and status != 'open' then disable the field.
			else if(el.iri && !U.isEmptyString(el.iri()) && !self.isOpenStatus())
			{
				if(self.isLockedStatus())
				{
					if(el.hasLegacyCode && el.hasLegacyCode() == "PERSCNTC" 
							&& U.isEmptyString(el.hasCompletedTimestamp()))
						return "OutcomeSelectTemplate";
	    			//Temp Fix : if PW/WM SR then 'SWM Urgent Notification'
					else if(self.isPWWMCase() && el.hasLegacyCode && el.hasLegacyCode() == "SWMURGN"
							&& U.isEmptyString(el.hasCompletedTimestamp()))
						return "OutcomeSelectTemplate";
				}
				//if(self.isLockedStatus() && el.hasActivity().label() == 'Personal Contact' 
				//		&& U.isEmptyString(el.hasCompletedTimestamp()))
				//	return "OutcomeSelectTemplate";
				if(!U.isEmptyString(el.hasCompletedTimestamp()) && !U.isEmptyString(el.hasOutcome().iri()))
					return "OutcomeDisabledTemplate";
				else
					return "OutcomeClosedTemplate";
			}
			//END
			//if(U.isEmptyString(el.hasCompletedTimestamp()) && U.isEmptyString(el.hasOutcome().iri()))
			else if(U.isEmptyString(el.hasCompletedTimestamp()))
			{
				if(self.hasDefaultOutcome(el))
					return "DefaultOutcomeTemplate";
				else
					return "OutcomeSelectTemplate";
			}
			//if(!U.isEmptyString(el.hasCompletedTimestamp()) && !U.isEmptyString(el.hasOutcome().iri()))
			else if(!U.isEmptyString(el.hasCompletedTimestamp()))
			{
				return "OutcomeDisabledTemplate";
			}
			else
				return "OutcomeSelectTemplate";
		};
		
		self.acceptedTemplate = function(el) {
			if(self.hasDefaultOutcome(el))
				return "hasAcceptedTemplate";
			else
				return "noAcceptedTemplate";
		};
		
		self.activityPdfTemplate = function(el) {
			if(self.data().type() == 'legacy:WASDHETR' && el.iri 
					&& (el.hasActivity().iri() == "http://www.miamidade.gov/cirm/legacy#WASDHETR_WSWCA3" 
						||  el.hasActivity().iri() == "http://www.miamidade.gov/cirm/legacy#WASDHETR_WSWCA3A"))
				return "hasActivityPdfTemplate";
			else
				return "noActivityPdfTemplate";
		};

		handleAfterRender = function(element) {
			var currentField;
			$.each(element, function(i, htmlElement) { 
				var tempName = $(htmlElement.children).attr('name');
				if(tempName && tempName == 'charOptLabel') {
					var text = $(htmlElement.children).text();
				 	currentField = $.map(self.data().properties().hasServiceAnswer(), function(val) {
						if(text && val.hasServiceField().label() == text) 
							return val;
					});
				}
				else if(tempName && tempName == 'charOptSelect') {
					$(htmlElement.children).jec();
					if(currentField.length == 1 && currentField[0].hasChoiceValueList)
					{
						var choice = $.map(currentField[0].hasChoiceValueList().hasChoiceValue(), function(val) {
										//if(val.iri() == currentField[0].hasAnswerValue().literal())
										if(val.iri() == currentField[0].hasAnswerObject().iri())
											return val;
									});
						if(choice.length == 0) {
							$(htmlElement.children).find('option:first').text(currentField[0].hasAnswerObject().iri());
							$(htmlElement.children).find('option:first').attr('value', currentField[0].hasAnswerObject().iri());
							$(htmlElement.children).find('option:first').attr('selected', 'selected');
						}
					}
				}
				else if(tempName && tempName == 'dateInput') {
					$(htmlElement.children).datepicker();
				}
			});
		};

		self.standardizeStreet = function(answer, e) {
			if(event.keyCode == 9 || event.keyCode == 13) {
				self.getStandardizeStreet(answer);
    	    }
    	    return true;
		};
		
		self.getStandardizeStreet = function(answer) {
			$(document).trigger(legacy.InteractionEvents.StandardizeStreetRequest, [answer.hasAnswerValue().literal, true]);
		};
		
		self.isStandardizedStreetMsgVisible = function(value) {
			return (value.conditionallyRequired() && !value.isStandardizedStreet());
		};
		
		self.returnRegularTemplate = {
			"CHAR":"charTemplate", "NUMBER":"charTemplate", "PHONENUM":"charTemplate", "DATE":"dateTemplate", 
			"TIME":"timeTemplate", "CHARLIST":"charListTemplate", "CHARMULT":"charMultTemplate", 
			"CHAROPT":"charOptTemplate", "CHARLISTOPT":"charOptTemplate", "undefined":"charTemplate"
		};
		self.returnDisabledTemplate = {
			"CHAR":"charDisabledTemplate", "NUMBER":"charDisabledTemplate", "PHONENUM":"charDisabledTemplate", 
			"DATE":"dateDisabledTemplate", "TIME":"timeDisabledTemplate", "CHARLIST":"charListDisabledTemplate", 
			"CHARMULT":"charMultDisabledTemplate", "CHAROPT":"charOptDisabledTemplate", 
			"CHARLISTOPT":"charOptDisabledTemplate", "undefined":"charDisabledTemplate"
		};

		self.selectTemplate = function(el) {
			var datatype = el.hasDataType ? el.hasDataType() : undefined;
			if((el.isOldData && el.isOldData()) || (el.fromDiffSRType && el.fromDiffSRType()))
			{
				if(el.hasStandardizeStreetFormat)
					return "charStreetDisabledTemplate";
				else
					return self.returnDisabledTemplate[datatype];
			}
			var bizCodes = el.hasBusinessCodes ? el.hasBusinessCodes() : undefined;
			var allowableModules = el.hasAllowableModules ? el.hasAllowableModules() : undefined; 
			if(!U.isEmptyString(self.data().boid()) && allowableModules != undefined 
					&& allowableModules.indexOf("SRERESAC") == -1)
				return "emptyTemplate";
			if(el.isDisabled && el.isDisabled() == 'true') {
				if(el.hasAnswerValue) {
					if(U.isEmptyString(el.hasAnswerValue().literal()))
						return "emptyTemplate";
					else
						return "disabledTemplate";
				}
				else if(el.hasAnswerObject) {
					if(U.isEmptyString(el.hasAnswerObject().iri()))
						return "emptyTemplate";
					else
						return "disabledObjectTemplate";
				}
			}
			var isAnswerUpdateAllowed = (self.data().boid() != "") ? isServiceFieldUpdateWithinTimeout(el) : undefined;
			if(self.isLockedStatus() == true || isAnswerUpdateAllowed == false 
				|| (!U.isEmptyString(self.data().boid()) && 
					self.data().properties().hasStatus().iri().indexOf("C-CLOSED") != -1)) {
				if(el.hasStandardizeStreetFormat)
					return "charStreetDisabledTemplate";
				else if(bizCodes) {
					if (el.hasAnswerValue && bizCodes.indexOf("DELETED") != -1)
						return "deletedTemplate";
					else if (el.hasAnswerObject && bizCodes.indexOf("DELETED") != -1)
						return "deletedObjectTemplate";
					else
						return self.returnDisabledTemplate[datatype];
				}
				return self.returnDisabledTemplate[datatype];
			}
			else {
				if(el.hasStandardizeStreetFormat)
					return "charStreetTemplate";
				if(bizCodes) {
					if (bizCodes.indexOf("DELETED") != -1)
						return "deletedTemplate";
					else if(el.hasAnswerValue && bizCodes.indexOf("NOUPDATE") != -1)
						return "noUpdateTemplate";
					else if(el.hasAnswerObject && bizCodes.indexOf("NOUPDATE") != -1)	
						return self.returnDisabledTemplate[datatype];
					else if(bizCodes.indexOf("NOUPDATE") == -1)
						return self.returnRegularTemplate[datatype];
				}
				return self.returnRegularTemplate[datatype];
			}
		};
		
		self.getChoiceValueList = function(el) {
			return $.map(el.hasChoiceValueList().hasChoiceValue(), function(v) {
				if(el.hasAnswerObject && el.hasAnswerObject().iri) {
					if(U.isArray(el.hasAnswerObject().iri()) && $.inArray(v.iri(), el.hasAnswerObject().iri()) >= 0)
						return v;
					else if(el.hasAnswerObject().iri() === v.iri())
						return v;
				}
				if(v.isDisabled) {
					if(!v.isDisabled())
						return v;
				}
				else
					return v;
			});
		};
		
		self.isHighlighted = function(el, event) {
			return (!U.isEmptyString(el.isHighlighted) && el.isHighlighted() == 'true');
		};
		
		self.myDate = ko.observable("").trimmed();
		
		self.address = ko.observable(addressModel);
		
		self.clearLocation = function() {
    		$(document).trigger(legacy.InteractionEvents.AddressClear, []);
		}

		self.clearAddress = function() {
			atAddress = self.data().properties().atAddress();
			self.removeAddressIRI(atAddress);
			self.removeAddressExtenders(self.data());
			atAddress.fullAddress("");
			atAddress.folio("");
			atAddress.Street_Name("");
			atAddress.Street_Number("");
			atAddress.Street_Unit_Number("");
			atAddress.Zip_Code("");
			atAddress.hasStreetType().iri("");
			atAddress.hasStreetType().label("");
			atAddress.Street_Address_City().iri("");
			atAddress.Street_Address_City().label("");
			atAddress.Street_Direction().iri("");
			atAddress.Street_Direction().label("");
			self.data().properties().hasXCoordinate("");
			self.data().properties().hasYCoordinate("");
			self.address().addressData(null);
			self.removeDuplicates();
			patchPlaceholdersforIE();
		};
		
		self.clearSR = function(type) {
			if(self.data().type() == "") {
   				$('#srTypeID').val("");
				return;
			}
			$("#sh_dialog_clear")[0].innerText = "Are you sure you want to clear the current SR ?"
    		$("#sh_dialog_clear").dialog({ height: 150, width: 350, modal: true, buttons: {
				"Yes" : function() {
	   				$(document).trigger(legacy.InteractionEvents.SrTypeSelection, []);
       				$('#srTypeID').val("");
					$('[name="SR Lookup"]').val("");
					var tempAddr = self.data().properties().atAddress();
					var tempX = self.data().properties().hasXCoordinate();
					var tempY = self.data().properties().hasYCoordinate();
					self.removeDuplicates();
					self.data(emptyModel.data);
					self.srType(emptyModel.srType);
					self.originalData(emptyModel.originalData);
					self.data().properties().atAddress(tempAddr);
					self.data().properties().hasXCoordinate(tempX);
					self.data().properties().hasYCoordinate(tempY);
					self.removeAddressExtenders(self.data());
					self.clearEmail();
					$("#sh_dialog_clear").dialog('close');
					patchPlaceholdersforIE();
					if(type != undefined)
						self.startNewServiceRequest(type);
				},
				"No": function() {
				  	$("#sh_dialog_clear").dialog('close');
				  	return;
				}
			  } 
			});
		};

		self.clearAllTabs = function() {
			$(document).trigger(legacy.InteractionEvents.AllTABSClear, []);
		};

		$(document).bind(legacy.InteractionEvents.SHClear, function(event) {
			$('#srTypeID').val("");
			$('[name="SR Lookup"]').val("");
			self.removeDuplicates();
			self.data(emptyModel.data);
			self.srType(emptyModel.srType);
			self.originalData(emptyModel.originalData);
			self.clearAddress();
			self.clearEmail();
		});

		
		
		// Detail sections
		self.sections = [
		    {title:'SR Main', div:'sr_details'}, 
		    {title:'SR Customers', div:'sr_actors'}, 
		    {title:'SR Activities', div:'sr_activities'}, 
		    //{title:'SR Info', div:'sr_info'}, 
		    {title:'Geo Info', div:'sr_geo_info'}, 
		    //{title:'Attachments', div:'sr_images'}
		    
		    ];
		
		
		self.currentSection = ko.observable(self.sections[0]);
		
	
		
		self.goToAttachments = function(){
			self.currentSection({title:'Attachments', div:'sr_images'});
		} 
		self.goToSection = function(section) {
		        self.currentSection(section);
		};
		
	
		
		self.launchMap = function()
		{
			$(document).trigger(legacy.InteractionEvents.LaunchMap, [self.address()]);
		};
		
		self.checkboxAddress = function(el, event) {
			if(event.currentTarget.checked == true)
				self.populateActorAddress(el, event);
			else if(event.currentTarget.checked == false)
				self.clearActorAddress(el, event);
			patchPlaceholdersforIE();
			return true;
		};
		
		self.getCodeEnforcementZonePhone = function(zone) {
			if(zone === null || U.isEmptyString(zone))
				return '';
			else if(zone.toUpperCase() === 'SOUTH')
				return zone + ' (305-329-4770)';
			else if(zone.toUpperCase() === 'NORTH')
				return zone + ' (305-329-4820)';
			else if(zone.toUpperCase() === 'CENTRAL')
				return zone + ' (305-329-4800)';
			else
				return '';
		};
		
		self.getUtilityInfo = function(el, event) {
			if(U.isEmptyString(el.utilityNameAlias))
				return el.utilityName;
			else
				return el.utilityNameAlias + ' (' + el.utilityName + ')';
		};
		
		self.getMaintenanceInfo = function(el, event) {
			if(U.isEmptyString(el.maintenanceCodeAlias))
				return el.maintenanceCode;
			else
				return el.maintenanceCodeAlias + ' (' + el.maintenanceCode + ')';
		};
		
		self.getGarbagePickupDayInfo = function(el, event) {
			if(U.isEmptyString(el.garbagePickupDayAlias))
				return el.garbagePickupDay;
			else
				return el.garbagePickupDayAlias + ' (' + el.garbagePickupDay + ')';
		};
		
		self.getAddrSRTypeTemplate = function(el)
		{
			return "showAddrSRTypeTemplate";
		};
		
		self.buildAddress = function(el) {
			var addr = self.data().properties().atAddress();
			var builtAddr = "";
			if(!U.isEmptyString(addr.fullAddress()))
				builtAddr = builtAddr + addr.fullAddress();
			else
				return builtAddr;
			if(!U.isEmptyString(addr.Street_Unit_Number()))
				builtAddr = builtAddr + " #" + addr.Street_Unit_Number();
			if(!U.isEmptyString(addr.Street_Address_City().label()))
				builtAddr = builtAddr + ", " + addr.Street_Address_City().label();
			if(!U.isEmptyString(addr.Street_Address_State().label()))
				 builtAddr = builtAddr + ", " + addr.Street_Address_State().label();
			if(!U.isEmptyString(addr.Zip_Code()))
				builtAddr = builtAddr + " - " + addr.Zip_Code();
			return builtAddr;
		};
		
		self.buildSRType = function(el) {
			if(!U.isEmptyString(self.srType().label))
				return self.srType().label + " - " +self.srType().hasJurisdictionCode;
			else
				return "";
		};
		
		self.populateActorAddress = function(el, event) {
			var addr = U.clone(ko.toJS(self.data().properties().atAddress()));
			el.atAddress().fullAddress(addr.fullAddress);
			el.atAddress().Zip_Code(addr.Zip_Code);
			el.atAddress().Street_Unit_Number(addr.Street_Unit_Number);
			el.atAddress().Street_Address_City().iri(addr.Street_Address_City.iri);
			el.atAddress().Street_Address_City().label(addr.Street_Address_City.label);
			el.atAddress().Street_Address_State().label(addr.Street_Address_State.label);
			el.atAddress().Street_Address_State().iri(addr.Street_Address_State.iri);
		};
		
		self.clearActorAddress = function(el, event) {
			self.removeAddressIRI(el.atAddress());
			el.atAddress().fullAddress("");
			el.atAddress().Zip_Code("");
			el.atAddress().Street_Unit_Number("");
			el.atAddress().Street_Address_City().label("");
			el.atAddress().Street_Address_City().iri("");
			el.atAddress().Street_Address_State().label("Florida");
			el.atAddress().Street_Address_State().iri("http://www.miamidade.gov/ontology#Florida");
		};
		
		self.selectActorRow = function(el, event) {
		    var target = event.currentTarget.parentNode.parentNode; 
			var no = target.id.substring(target.id.indexOf("_")+1);
			if(event.currentTarget.style.cssText.trim() == 'background-color: rgb(238, 238, 238);')
				collapseActors();
			else
				collapseActors(no);
		};
		
		self.newActorRow = function(el) {
			return "actorRow_"+self.actorIndex(el);
		};
		
		self.actorIndex = function(el)
		{
			return self.data().properties().hasServiceCaseActor().indexOf(el);	
		};

		self.newActorDetailsRow = function(el) {
			return "actorDetailsRow_"+self.actorIndex(el);
		};
		
		self.getActorPhoneTypeListRowId = function(el) {
			return "phoneTypeList_"+self.actorIndex(el);
		};

		self.selectActivityRow = function(el, event) {
		    var target = event.currentTarget.parentNode.parentNode; 
			var no = target.id.substring(target.id.indexOf("_")+1);
			if(event.currentTarget.style.cssText.trim() == 'background-color: rgb(238, 238, 238);')
				collapseActivities();
			else
				collapseActivities(no);
		};
		
		self.newActivityRow = function(el) {
			var index =	self.data().properties().hasServiceActivity().indexOf(el);
			return "activityRow_"+index;
		};

		self.newActivityDetailsRow = function(el) {
			var index =	self.data().properties().hasServiceActivity().indexOf(el);
			return "activityDetailsRow_"+index;
		};
		
		self.computeDate = function(el) {
			if(!el || el() == "")
				return "N/A";
			else
				return new Date(Date.parse(el())).format("mm/dd/yyyy");
		};

		self.computeDateTime = function(el) {
			if(!el)
				return "";
			var tempEL = ko.toJS(el);
			if(typeof tempEL == 'number')
				return U.isEmptyString(tempEL) == true ? "" : new Date(tempEL).format("mm/dd/yyyy HH:MM:ss");
			else
				return U.isEmptyString(tempEL) == true ? "" : new Date(Date.parse(tempEL)).format("mm/dd/yyyy HH:MM:ss");
		};

		self.attachmentCallBack = function(res) {					
			if(res.ok == true){
				self.data().properties().hasAttachment.push(res.url);
				console.log();
				console.log("response from s3"); 
			    console.log(res);
			}
			else if(res.ok == false)
				alertDialog("Error uploading file");
		};				

		self.uploadFiles = function(el) {					 			
			if($('#fileUploader').val())
			{
			$('#fileUploader').upload("/upload", self.attachmentCallBack, 'json');
			$('#fileUploader')[0].value = "";
			}
			//console.log(self);
		};		
		
		function metadata(key, value){
			this.key = key;
			this.value = value;
			
		}				
		
		function updateMetadata(metadata, images){			
			var url = getMetadataUrl(); 
			//metadata = [{"key":"sr", "value": "123456"}];
			//console.log("number of images " + images.length);
			var tokens; 
			var image;
			for(i=0; i < images.length; i++){
			    image = images[i];
			    tokens = image.split("/");
			    token = tokens[4];
			    url = url + "/" + token;
			    //console.log("uploading metadata for " + url);				
				
				$.ajax({
					url:url,
					type:"POST",
					data:JSON.stringify(metadata), 
					contentType:"json",
					contentType: "application/json; charset=utf-8",
					success:function(){
						console.log("submitted metadata successfully");
					}
				});								 			
			}
		};

		/*self.getImageSource = function(data) {
			return "../uploaded/"+data;
		};*/
		
		self.removeImage = function(data) {
			console.log("removing image "); 
			console.log(data);
			$("#sh_dialog_alert")[0].innerText = "Are you sure you want to delete this Image";
			$("#sh_dialog_alert").dialog({ height: 150, width: 350, modal: true, buttons: {
				"Delete" : function() {
	        		self.data().properties().hasAttachment.remove(data);
					self.data().properties().hasRemovedAttachment.push(data);
					$("#sh_dialog_alert").dialog('close');
				},
				"Cancel": function() {
				  	$("#sh_dialog_alert").dialog('close');
				}
			  } 
			});
		};
		
		self.validateTypeOnXY = function (type, x, y, callback, invalidMsg)
		{
			self.showProgress("#sh_dialog_type_validate");
			cirm.top.async().get("/legacy/validate?type="+type+"&x=" + x + "&y=" + y, null, 
				function(data){
					self.hideProgress("#sh_dialog_type_validate");
						if(data.isAvailable)
						{
							callback();
						}
						else
						{
							$( "#sh_dialog_type_invalid" ).html('<p>' + invalidMsg + '</p>');
							$( "#sh_dialog_type_invalid" ).dialog({
								height: 200,
								modal: true
							});
						}
					});
		};
		
		self.showProgress = function(id) {
			$( id ).dialog({
				height: 140,
				modal: true
			});
		};

		self.hideProgress = function(id) {
			$( id ).dialog('close');
		};
		
		/**
		 * Returns the earliest StatusChangeActivity with a status of closed,
		 * returning it's date.
		 */
		self.getEarliestClosingActivity = function() {
			var activities = self.data().properties().hasServiceActivity();
			var closedDate = null;
			$(activities).each(function(){
				if(this.hasActivity().label() == 'StatusChangeActivity' && this.hasOutcome().iri().indexOf('#C-') > -1 )
				{
					var date = this.hasCompletedTimestamp;
					if(closedDate == null || closedDate() > date())
					{ 
						closedDate = date;
					}
				}
			});
			return closedDate;
		};
		
		self.getClosedDate = ko.computed(function(){
			return self.getEarliestClosingActivity();
		});
	    
		return self;
    }
    	
    function fetchSR(bo, model, isNew) {
    	var type = bo.type;
		if(type.indexOf("legacy:") == -1) {
			type = "legacy:"+type;
			bo.type = type;
		}
	    var srType = cirm.refs.serviceCases["http://www.miamidade.gov/cirm/legacy#" + type.split(":")[1]]; 
	    console.log('type', srType);
	    
		if(srType.hasServiceCaseAlert && bo.boid == "") {
			$("#sh_dialog_alert")[0].innerText = srType.hasServiceCaseAlert.label;
			$("#sh_dialog_alert").dialog({ height: 500, width: 500, modal: true, buttons: {
				"Continue with SR" : function() {
					$('#srTypeID').val(srType.label + " - " +srType.hasJurisdictionCode);
					setModel(bo, model, srType, type, false, isNew);
					$("#sh_dialog_alert").dialog('close');
				},
				"Clear SR": function() {
				  	$("#sh_dialog_alert").dialog('close');
				}
			  } 
			});
		}
		else {
			$('#srTypeID').val(srType.label + " - " +srType.hasJurisdictionCode);
			setModel(bo, model, srType, type, false, isNew);
			if (bo.boid() != "") {
				approvalCheck(bo.properties().hasCaseNumber(), model);
			}
		}
    }
    
	/**
	 * Checks the approval service for state. Returns true when 
	 */
    function approvalCheck (caseNumber, model) {
		cirm.top.async().get("/legacy/sr/"+caseNumber+ "/approvalState", {}, 
			function(data){
					if(data.approvalState == 'APPROVAL_PENDING')
					{
						model.isPendingApproval = true;
						unlockEntryFields();
						$("#sh_dialog_alert")[0].innerText = "The following Service Request has been identified as a self-service request and is 'Pending Approval'. Please review the request and make appropriate changes. When complete, set the status to 'Open' then save.";
						$("#sh_dialog_alert").dialog({ height: 150, width: 500, modal: true, buttons: {
								"Continue" : function() {
									$("#sh_dialog_alert").dialog('close');							
									//console.log("closing dialog, validating addresss");
						        	model.searchAddress();														
								}
							} 
						});
						
					}
					else
					{
						//do nothing
						return;
					}
				});  	    
	};
	
	function unlockEntryFields() {
		$('SELECT',$('#sr_details')).prop('disabled', false);
		$('textarea',$('#sr_details')).prop('disabled', false);
		$('input',$('#sr_details')).prop('disabled', false);
	};
	
	
    //apply view logic here hilpold?
    function setModel(bo, model, srType, type, sameTypeAsCurrent, isNew) {
    	model.isPendingApproval = false;
    	var currentDateTime = model.getServerDateTime();
    	var original = U.clone(bo);
        console.log('set model', original);

		if(srType.hasValidLocationNotRequired == 'true')
	    	model.isAddressValidationEnabled(false);
	    else
	    	model.isAddressValidationEnabled(true);
//	    if(bo.properties.hasStatus && bo.properties.hasStatus.label.toLowerCase() == 'locked')
//	    	model.isLockedStatus(true);
//	    else
//	    	model.isLockedStatus(false);
		
		var hasOldData = bo.properties.hasOldData ? $.parseJSON(bo.properties.hasOldData) : "";

		//Add top level data properties if they are not present..
		if(bo.properties.hasDetails === undefined)
			bo.properties.hasDetails = "";
		if(bo.properties.hasLegacyId === undefined)
			bo.properties.hasLegacyId = "";
		
       	if(!isNew)
       	{
			//TODO : temp fix. We don't have to do below check once all SRs have hasUserFriendlyID
			/*
			if(!bo.properties.hasUserFriendlyID)
				bo.properties.hasUserFriendlyID = "AC" + 
												new Date(Date.parse(bo.properties.hasDateCreated)).getFullYear()
												+ "-" + U.addLeadingZeroes(bo.boid, 8);
       		*/
			//END : temp fix.
       		//$('[name="SR Lookup"]').val(bo.properties.hasUserFriendlyID);
			if(!bo.properties.hasCaseNumber)
			{
				var yr = new Date(Date.parse(bo.properties.hasDateCreated)).getFullYear().toString();
				bo.properties.hasCaseNumber = yr.substr(yr.length - 2) + "-1" + U.addLeadingZeroes(bo.boid, 7);
			}
       		$('[name="SR Lookup"]').val(bo.properties.hasCaseNumber);
		} else {
			$('[name="SR Lookup"]').val("");
		} 
       	if(bo.properties.atAddress === undefined)
		{
       		bo.properties.atAddress = $.extend(new AddressBluePrint(), {});
       	}
       	else {
       		bo.properties.atAddress = $.extend(new AddressBluePrint(), bo.properties.atAddress);
			/*
			if(!isNew)
	    	{
		    	if(bo.properties.gisAddressData)
		    	{
					$(document).trigger(legacy.InteractionEvents.SetAddress, [bo.properties.gisAddressData, true]);
					delete bo.properties.gisAddressData;
		    	}
		    	else
		    	{
			    	var tempAddress = U.clone(bo.properties.atAddress);
			    	var tempCity = $.map(cirm.refs.cities, function(v,i) { 
			    		if(v.iri == tempAddress.Street_Address_City.iri) 
			    			return v; 
			    	});
			    	if(tempCity.length > 0)
			    		tempAddress.municipality = tempCity[0].Alias;
					U.visit(tempAddress, U.makeObservable);
					$(document).trigger(legacy.InteractionEvents.SearchAddress, [tempAddress, true]);
				}
			}
			*/
		}
       	bo.properties.hasXCoordinate = U.isEmptyString(bo.properties.hasXCoordinate) ? "" : bo.properties.hasXCoordinate;
       	bo.properties.hasYCoordinate = U.isEmptyString(bo.properties.hasYCoordinate) ? "" : bo.properties.hasYCoordinate;

		if(bo.properties.hasDueDate != undefined)
			bo.properties.hasDueDate = new Date(Date.parse(bo.properties.hasDueDate)).asISODateString();

	    bo.properties.hasServiceCaseActor = U.ensureArray(bo.properties.hasServiceCaseActor);

		if(bo.properties.hasServiceCaseActor.length > 0) {
			bo.properties.hasServiceCaseActor = $.map(bo.properties.hasServiceCaseActor, function(v) {
				//if (typeof v.atAddress == "undefined") v.atAddress = bo.properties.atAddress;
				$.each(v, function(prop,value){
					if(prop.indexOf('Number') != -1)
					{
						var tempNumber = [];
						$.each(value.split(','), function(j, contactNo) {
							var numberExtn = contactNo.split('#');
							var contactNumberFormat = {
								"number": U.isEmptyString(numberExtn[0]) ? "": numberExtn[0], 
								"extn": U.isEmptyString(numberExtn[1]) ? "": numberExtn[1]
							};
							tempNumber.push(contactNumberFormat);
						});
						v[prop] = tempNumber;
						value = tempNumber;
					}
				});
				if(v.hasEmailAddress)
				{
					if(typeof v.hasEmailAddress == "object" && v.hasEmailAddress.iri)
						v.hasEmailAddress.label = v.hasEmailAddress.iri.replace("mailto:","");
					else if(typeof v.hasEmailAddress == "string")
						v.hasEmailAddress = {
							"label":v.hasEmailAddress.replace("mailto:",""),
							"iri":v.hasEmailAddress, "type":"EmailAddress"};
				}
				//if(v.hasEmailAddress && v.hasEmailAddress.iri)
				//{
				//	v.hasEmailAddress.label = v.hasEmailAddress.iri.replace("mailto:","");
				//}
				if(v.atAddress) {
					if(v.atAddress.hasServiceCaseActor)
						delete v.atAddress.hasServiceCaseActor;
					if(v.atAddress.Street_Address_City && v.atAddress.Street_Address_City.iri)
					{
						var tempCity = v.atAddress.Street_Address_City.iri;
						v.atAddress.Street_Address_City.label = tempCity.substr(tempCity.indexOf('#')+1);
					}
       				v.atAddress = $.extend(new ServiceActorAddressBluePrint(), v.atAddress);
				}
				var actorIRI = (v.hasServiceActor.iri != undefined) ? v.hasServiceActor.iri : v.hasServiceActor;
				var tempSA = $.extend(new ServiceActor(actorIRI, v.label, cirm.user.username, currentDateTime.asISODateString(), isNew), v);
				if(isCitizenActor(actorIRI) && !isNew && v.Name && v.Name.toUpperCase() === 'ANONYMOUS') {
					tempSA.isAnonymous = true;
				}
				if(tempSA.atAddress.Street_Unit_Number === undefined) {
					tempSA.atAddress.Street_Unit_Number = "";
				}
				if(tempSA.atAddress.Street_Address_State === undefined || tempSA.atAddress.Street_Address_State.iri === undefined) {
					var tempState = $.map(model.allStates(), function(v) {
						if (v.iri == tempSA.atAddress.Street_Address_State)
							return v;
					});
					if(tempState.length > 0)
						tempSA.atAddress.Street_Address_State = {"iri" :tempState[0].iri, "label":tempState[0].label};
					else
						tempSA.atAddress.Street_Address_State = {"iri":undefined, "label":""};
				}
				return tempSA;
			});
			
			//Sort the Actors by created Date.
			bo.properties.hasServiceCaseActor.sort(function (a,b) {
				return a.hasDateCreated.localeCompare(b.hasDateCreated);
			});
			//Last created first
			bo.properties.hasServiceCaseActor.reverse();
		}
    	U.resolveIris(bo);
    	bo.properties.hasStatus = U.clone(bo.properties.hasStatus);
		
	    if(bo.properties.hasStatus && bo.properties.hasStatus.label.toLowerCase() == 'locked'
	    	|| !cirm.user.isUpdateAllowed(type.split(":")[1]))
	    	model.isLockedStatus(true);
	    else
	    	model.isLockedStatus(false);

	    //if no images, then add the property for images
    	bo.properties.hasAttachment = U.ensureArray(bo.properties.hasAttachment);
    	bo.properties.hasRemovedAttachment = U.ensureArray(bo.properties.hasRemovedAttachment);

    	bo.properties.hasServiceAnswer = U.ensureArray(bo.properties.hasServiceAnswer);

    	if(srType.hasServiceField && bo.properties.hasServiceAnswer.length != srType.hasServiceField.length) {
	        //populate the bo with the Service Answers which are not present
        	if(bo.properties.hasServiceAnswer.length < srType.hasServiceField.length) {
		        var tempServiceAnswerIris = [];
		        $.each(bo.properties.hasServiceAnswer, function(i,v) {
		        	tempServiceAnswerIris.push(v.hasServiceField.iri);	
		        });
	        	$.each(srType.hasServiceField, function(i,v) {
	        		if($.inArray(v.iri, tempServiceAnswerIris) == -1) {
	        			//var temp = {"hasAnswerValue":{"type":"", "literal":""}, hasServiceField:{"iri":v.iri, "label":v.label}};
	        			var temp = {hasServiceField:{"iri":v.iri, "label":v.label}};
						if(v.hasDataType == 'CHARLIST' || v.hasDataType == 'CHARMULT' || v.hasDataType == 'CHAROPT' || v.hasDataType == 'CHARLISTOPT')
							temp.hasAnswerObject = {"iri":undefined};
						else
							temp.hasAnswerValue = {literal:'', type:''};
	        			bo.properties.hasServiceAnswer.push(temp);
	        		}
	        	});
			}
			else if(bo.properties.hasServiceAnswer.length > srType.hasServiceField.length) {
		        var tempServiceFieldIris = [];
		        $.each(srType.hasServiceField, function(i,v) {
		        	tempServiceFieldIris.push(v.iri);	
		        });
	        	$.each(bo.properties.hasServiceAnswer, function(i,v) {
	        		if($.inArray(v.hasServiceField.iri, tempServiceFieldIris) == -1) {
						if(v.hasDataType == 'CHARLIST' || v.hasDataType == 'CHARMULT' || v.hasDataType == 'CHAROPT' || v.hasDataType == 'CHARLISTOPT') {
							if(!v.hasAnswerObject)
								v.hasAnswerObject = {"iri":undefined};
						}
						else {
		        			if(!v.hasAnswerValue)
		        				v.hasAnswerValue = undefined; 
						}
	        			v.hasBusinessCodes = "DELETED";
	        			v.hasDataType = "CHAR";
	        			var tempLabel = v.hasServiceField.label;
	        			v.hasServiceField.label = "The question related to ," + tempLabel + ", has been deleted from the ontology. But the answer will not be deleted from this SR (if already present) : ";
	        		}
	        	});
			}
        }

	    //populate bo's answers with srType's - hasOrderBy, hasDataType, hasChoiceValueList, hasBusinessCodes etc
		$.each(bo.properties.hasServiceAnswer, function(i,v) {
			var srTypeServiceField = $.map(srType.hasServiceField, function(value) {
				if(v.hasServiceField.iri == value.iri)
					return value;
			});
        	// if empty, means the serviceAnswer belongs to a different SR Type
        	if(srTypeServiceField.length == 0)
        	{
				var otherServiceQuestion = $.map(cirm.refs.serviceCases, function(sc) { 
					return $.map(sc.hasServiceField, function(qtn) {
						if(qtn.iri == 'http://www.miamidade.gov/cirm/legacy#RAAM1_RAAM1TD')
							return qtn;
					});
				});
				if(otherServiceQuestion.length > 0)
				{
					v.fromDiffSRType = true;
					addPropertiesfromType(otherServiceQuestion[0], v);
				}
				//Question not present in any SR Type. 
				else
				{
					v.fromDiffSRType = true;
					v.hasDataType = 'CHAR';
				}
			}
			//not empty, serviceAnswer belongs to the same SR Type
        	else
        	{
        		addPropertiesfromType(srTypeServiceField[0], v);
        	}
		});
		
	    //populate bo's answers with srType's - hasOrderBy, hasDataType, hasChoiceValueList, hasBusinessCodes, hasStandardizeStreetFormat
		function addPropertiesfromType(serviceField, serviceAnswer) {
            serviceAnswer.hasDataType = serviceField.hasDataType;
            if(serviceAnswer.hasAnswerValue)
           		serviceAnswer.hasAnswerValue.type = model.hasTypeMapping()[serviceField.hasDataType];
           	//START : temp fix for NUMBER and DOUBLE datatypes
           	if(serviceAnswer.hasAnswerValue && 
           			!U.isEmptyString(serviceAnswer.hasAnswerValue.literal) && 
           			serviceAnswer.hasDataType == 'NUMBER')
           	{
           		var remainder = serviceAnswer.hasAnswerValue.literal - Math.floor(serviceAnswer.hasAnswerValue.literal);
           		if(remainder == 0)
           			serviceAnswer.hasAnswerValue.literal = Math.floor(serviceAnswer.hasAnswerValue.literal);
           		else
           			serviceAnswer.hasAnswerValue.type = model.hasTypeMapping()['DOUBLE'];
           	}
           	//END : temp fix
           	if(serviceField.hasDataType == 'DATE')
           	{
           		if(!U.isEmptyString(serviceAnswer.hasAnswerValue.literal))
           			serviceAnswer.hasAnswerValue.literal = new Date(Date.parse(serviceAnswer.hasAnswerValue.literal)).format("mm/dd/yyyy");
           	}
           	else if (serviceField.hasDataType == 'CHAROPT' || serviceField.hasDataType == 'CHARLIST')
           	{
           		if(serviceAnswer.hasAnswerValue && serviceAnswer.hasAnswerValue.literal) {
           			serviceAnswer.hasAnswerObject = {"iri" : serviceAnswer.hasAnswerValue.literal};
           			delete serviceAnswer.hasAnswerValue;
           			if(serviceField.hasDataType == 'CHARLIST')
           				serviceAnswer.hasDataType = 'CHARLISTOPT';
           		}           		
           	}
           	if(serviceField.hasAnswerUpdateTimeout)
           		serviceAnswer.hasAnswerUpdateTimeout = serviceField.hasAnswerUpdateTimeout;
            serviceAnswer.hasOrderBy = serviceField.hasOrderBy;
            if(serviceField.isHighlighted)
            	serviceAnswer.isHighlighted = serviceField.isHighlighted;
            if(serviceField.hasChoiceValueList)
                serviceAnswer.hasChoiceValueList = U.clone(serviceField.hasChoiceValueList);
            if(serviceField.hasBusinessCodes)
                serviceAnswer.hasBusinessCodes = serviceField.hasBusinessCodes;
    		if(serviceField.hasAllowableModules)
               serviceAnswer.hasAllowableModules = serviceField.hasAllowableModules;
            if(serviceField.isDisabled)
            	serviceAnswer.isDisabled = serviceField.isDisabled;
            if(serviceField.hasStandardizeStreetFormat)
            	serviceAnswer.hasStandardizeStreetFormat = serviceField.hasStandardizeStreetFormat;
            if(serviceField.hasLegacyCode)
            	serviceAnswer.hasLegacyCode = serviceField.hasLegacyCode;
            if(serviceField.hasAnswerConstraint)
            	serviceAnswer.hasAnswerConstraint = serviceField.hasAnswerConstraint;
    	}
		
        //Delete the questions which are not associated with this view from hasServiceAnswers
        //TODO : revisit. Might not be needed after all. 
        //Ticket 412 : dont delete just hide them. if not, we will lose them on subsequent updates
        var tempHasServiceAnswers = [];
        var ignoredAnswersForExistingSR = [];
        $.each(bo.properties.hasServiceAnswer, function(i,v) {
			if(v.hasAllowableModules) {
				if(isNew && v.hasAllowableModules.indexOf("SREINTAK") != -1)
					tempHasServiceAnswers.push(v);
				else if(!isNew && v.hasAllowableModules.indexOf("SRERESAC") != -1)
					tempHasServiceAnswers.push(v);
				if(!isNew && v.hasAllowableModules.indexOf("SRERESAC") == -1)
				{
					tempHasServiceAnswers.push(v);
					ignoredAnswersForExistingSR.push(v);
				}
			}
			else
				tempHasServiceAnswers.push(v);
        });
        bo.properties.hasServiceAnswer = tempHasServiceAnswers;
        
        //Sort the Questions 
        bo.properties.hasServiceAnswer.sort(function(a,b) {
            return parseFloat(a.hasOrderBy) - parseFloat(b.hasOrderBy) } );
        
        // JSON serialization on server currently doesn't serialize non-functional properties
        // as arrays, but it should.

        $.each(bo.properties.hasServiceAnswer, function(i,v) {
            if(v.hasChoiceValueList) {
            	if(U.isArray(v.hasChoiceValueList)) {
            		console.log("There can only be 1 hasChoiceValueList objectProperty in the ontology", ko.toJS(v));
            		alertDialog("There is an error with this Service Request Type, please notify the administrator");
            	}
                v.hasChoiceValueList.hasChoiceValue = U.ensureArray(v.hasChoiceValueList.hasChoiceValue);
                v.hasChoiceValueList.hasChoiceValue.sort(function (a,b) {
               		if(a.hasOrderBy && b.hasOrderBy)
               			return parseFloat(a.hasOrderBy) - parseFloat(b.hasOrderBy);
               		else
               			return a.label.localeCompare(b.label);
                });
            }
            //If a value is not selected, then, on fetch of the record hasAnswerValue is missing...
            if(v.hasDataType == 'CHARLIST' || v.hasDataType == 'CHARMULT' || v.hasDataType == 'CHAROPT' || v.hasDataType == 'CHARLISTOPT') {
	            if(!v.hasAnswerObject)
	                v.hasAnswerObject = {"iri":undefined};
            }
            else {
	            if(!v.hasAnswerValue)
	                v.hasAnswerValue = {"literal":"", "type":model.hasTypeMapping()[v.hasDataType]};
	        }
            //Incase of multiple select fields, if only one record is selected, then on fetch doesn't return an array.
            //if(v.hasDataType == "CHARMULT" && !U.isArray(v.hasAnswerObject.iri))
              //  v.hasAnswerObject.iri = [v.hasAnswerObject.iri];
            if(v.hasDataType == 'CHARMULT') {
            	var tempCharMultIris = [];
            	v.hasAnswerObject = U.ensureArray(v.hasAnswerObject);
        		$.each(v.hasAnswerObject, function(i,v) {
        			tempCharMultIris.push(v.iri);
        		});
           		v.hasAnswerObject = {"iri":tempCharMultIris};
            }
        });
        
		//START : Old Answers
		if(hasOldData.hasServiceAnswer)
		{
			hasOldData.hasServiceAnswer = U.ensureArray(hasOldData.hasServiceAnswer);
			$.each(hasOldData.hasServiceAnswer, function(i,v) {
				v.isOldData = true;
				var ignoreList = $.grep(ignoredAnswersForExistingSR, function(ans) {
					return ans.hasServiceField.iri == v.hasServiceField.iri;
				});
				if(ignoreList.length > 0)
					return true;
				var existing = $.grep(bo.properties.hasServiceAnswer, function(ans) {
					return ans.hasServiceField.iri == v.hasServiceField.iri;
				});
				if(existing.length == 0)
				{
					v.hasDataType = "CHAR";
					bo.properties.hasServiceAnswer.push(v);
					return true;
				}
				if(existing.length > 0)
				{
					if(existing[0].hasAnswerValue && existing[0].hasAnswerValue.literal != v.hasAnswerValue.literal)
					{
						var indexNo = bo.properties.hasServiceAnswer.indexOf(existing[0]);
						bo.properties.hasServiceAnswer[indexNo].isOldData = true;
						bo.properties.hasServiceAnswer[indexNo].hasDataType = "CHAR";
					}
					if(existing[0].hasAnswerObject && existing[0].hasAnswerObject.label != v.hasAnswerValue.literal)
					{
						var indexNo = bo.properties.hasServiceAnswer.indexOf(existing[0]);
						bo.properties.hasServiceAnswer[indexNo].isOldData = true;
						bo.properties.hasServiceAnswer[indexNo].hasDataType = "CHAR";
						delete bo.properties.hasServiceAnswer[indexNo].hasAnswerObject;
						bo.properties.hasServiceAnswer[indexNo].hasAnswerValue = {"literal":v.hasAnswerValue.literal};
					}
				}
			});
		}
		//END : Old Answers

	    bo.properties.hasServiceActivity = U.ensureArray(bo.properties.hasServiceActivity);
	    
		if(bo.properties.hasServiceActivity.length > 0) {
			bo.properties.hasServiceActivity = $.map(bo.properties.hasServiceActivity, function(v) {
				v.isAccepted = v.isAccepted ? v.isAccepted == 'true' ? true : false : false;
				return $.extend(new ServiceActivity(v.iri, v.label, cirm.user.username, currentDateTime.asISODateString()), v);
			});
			$.each(bo.properties.hasServiceActivity, function(i,v) {
				$.each(srType.hasActivity, function(j,val) {
					if(v.hasActivity.iri == val.iri)
					{ 
						v.isAutoAssign = val.isAutoAssign;
						v.hasLegacyCode = val.hasLegacyCode;
						if(val.isAutoCreate == 'Y')
							v.hasOrderBy = val.hasOrderBy;
					}
				});
			});
			//Sort the Activities by created Date.
			bo.properties.hasServiceActivity.sort(function (a,b) {
				if(a.hasOrderBy && b.hasOrderBy)
					return (-(parseFloat(a.hasOrderBy) - parseFloat(b.hasOrderBy)));
				else
					return a.hasDateCreated.localeCompare(b.hasDateCreated);
			});
			//Last created first
			bo.properties.hasServiceActivity.reverse();
		}
		//START : Old Activities
		if(hasOldData.hasServiceActivity)
		{
			hasOldData.hasServiceActivity = U.ensureArray(hasOldData.hasServiceActivity);
			$.each(hasOldData.hasServiceActivity, function(i,v) {
				var oldActivity = $.extend(new ServiceActivity(v.iri, "", "", ""), v);
				oldActivity.isOldData = true;
				bo.properties.hasServiceActivity.push(oldActivity);
			});
		}
		//END : Old Activities
		//model.bindData(U.visit(srType, U.makeObservable),U.visit(bo, U.makeObservable), original);
		model.bindData(srType, U.visit(bo, U.makeObservableTrimmed), original);		
    }    
    
    /**
     * sr: The service request
     */
    function edit(sr, addressModel) {
        var self = {};
        self.markup = $(srmarkupText);
        self.model = new RequestModel(addressModel);
       
       
        self.model.getFileInfo = function(url){        	
        	var obj = {}; 
            obj.isImage = false;
            var imageTypes = ['jpg', 'png', 'gif', 'tif','jpeg'];
             
        	var tokens; 
        	var name;
        	var ext; 
        	tokens = url.split("/");
        	
        	if(tokens.length < 5)
        		{
        		console.log("error tokenizing url"); 
        		obj.name = file;
        		return obj; 
        		}
        	
        	name = tokens[4]; 
        	
        	tokens = name.split("_");
        	tokens = tokens[1].split("-")
        	name = tokens[0]; 
        	//tokens = name.split(".");
        	ext = tokens[1];
        	console.log('name and ext ' + name + ext); 
        	name = name + '.' +ext;
        	for(var i = 0; i < imageTypes.length; i++){
        		if(imageTypes[i] == ext)
        			{
        			obj.isImage = true; 
        			}
        	}
        	obj.name = name;
        	return obj;
        }
               
        ko.applyBindings(self.model, self.markup[0]);
      
/*
        var obj = legacy.metadata.LegacyServiceCaseListInput;
        //cirm.top.get("/legacy?q=LegacyServiceCaseListInput");
        var renderer = ui.engine.getRenderer(obj.type);
        var srtypeinput = renderer.apply(ui, [obj]);
		$(srtypeinput).addClass('ic_field');
		$(srtypeinput).attr("placeholder", "Service Request Type").attr('title', 'Service Request Type');
		$(srtypeinput).attr('style', 'width:312px');
		//$(srtypeinput).attr('data-bind', 'event: {keydown: validateOnEnter}, css: {color_green: $root.setSRTypeColor()}');
        $('#srTypeList .input_clear', self.markup).prepend(srtypeinput);
*/
        if (sr) {
            fetchSR(sr,self.model, false);
        }
        
        self.embed = function(parent) {
            $(parent).append(self.markup);
            
            var ihist = legacy.interactionHistory(); 
		    ihist.embed($('#callInteractionContainer',self.markup));	
        }        

        // Menu switch on SR details, not sure if this 
        return self;
    }
    
    return {
        edit: edit
    };
});
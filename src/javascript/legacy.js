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
//<needs(U.js)
//<needs(rest.js)
//<needs(cirm.js)
//<needs(require.js)
//<needs(date.js)

define(["jquery", "U", "rest", "uiEngine", "cirm", "text!../html/legacyTemplates.ht"], 
   function($, U, rest, ui, cirm, legacyHtml)   {
    
    var legacyTemplates = $(legacyHtml);

    // Temporary before SR tagging are fixed in PKBI to use the new IDs from the legacy ontology
//    var serviceRequests = {}; // map SR title to SR individuals    
/*    var serviceCaseList = {};
    var autoCompleteServiceCaseList = [];
    var metadata = {};
    function loadMetadata()
    { 
        metadata.LegacyServiceCaseListInput = cirm.top.get("/individuals/LegacyServiceCaseListInput");
        cirm.delayDataFetch(metadata.LegacyServiceCaseListInput.hasDataSource, 
            function(A) { $.each(A, function (i,v) { 
//	            serviceRequests[v.iri] = v;
	            var tempLabel = v.label+' - '+v.hasJurisdictionCode;
	    		serviceCaseList[tempLabel] = v.hasLegacyCode;
	    		autoCompleteServiceCaseList.push({"label":tempLabel, "value":tempLabel});
            }) }).call();
//        metadata.agencies = cirm.top.get('/legacy/searchAgencyMap');
//        metadata.departments = cirm.top.get('/individuals/instances/Department_County');
//        metadata.municipalities = cirm.top.get('/individuals/instances/City_Organization?direct=true');
    }
    loadMetadata();
   */ 
    var InteractionEvents = {
            StartCall : "StartCall",
            EndCall: "EndCall",
            UserAction: "UserAction",
            ServiceRequestTypeClick:"ServiceRequestTypeClick",
            AddressValidated: "AddressValidated",
            AddressClear: "AddressClear",
            LaunchMap:"LaunchMap",
            SolidWasteAccountNumberAvailable:"SolidWasteAccountNumberAvailable",
            SearchAddress:"SearchAddress",
            SetAddress:"SetAddress",
            SearchFolio:"SearchFolio",
            WCSClear:"WCSClear",
            AdvSrchAddrClear:"AdvSrchAddrClear",
            AdvSrchClear:"AdvSrchClear",
			ElectionsClear:"ElectionsClear",
			ASDClear:"ASDClear",
			AllTABSClear:"AllTABSClear",
			SHClear:"SHClear",
			SrTypeSelection: "SrTypeSelection", // param "BULKYTRA"
            populateSH:"populateSH",
            //populateWCSAccountOwnerActor:"populateWCSAccountOwnerActor",
            populateActor:"populateActor",
            showServiceHub:"showServiceHub",
            showServiceActivities:"showServiceActivities",
            resetServiceHubToSRMain:"resetServiceHubToSRMain",
            StandardizeStreetRequest:"StandardizeStreetRequest"
    };

    function ServiceCallModel() {
        var self = this; 
        self.callOngoing = ko.observable(false);
        self.actions = ko.observableArray();
        self.resolution = ko.observable("");
        self.lastAction = ko.computed(function() {
                if (self.actions().length == 0)
                    return "";
                var a = self.actions()[self.actions().length-1];
                //console.log('action ', self.actions(), a);
                return a.hasName + ":" + a.hasValue;                    
         }, self);
        
        self.addAction = function (name, data) {
            if (!self.callOngoing())
                return;            
            if (self.actions == null)
                throw new Error("No call started yet.");
            var a = {
                    type:'ServiceCallAction',
                    occurAt:new Date().asISODateString(),
                    hasName:name,
                    hasValue:data
            };
            self.actions.push(a);          
            return a;
        };
        
        $(document).bind(InteractionEvents.UserAction, function(event, name, data) {
                self.addAction(name, data);
        });
        $(document).bind(InteractionEvents.ServiceRequestTypeClick, function(event, type) {
                self.addAction("Create SR", U.IRI.name(type));                
        });
        
        self.startCall = function() {
            if (self.callOngoing())
                return;            
            self.actions.removeAll();
            self.startAt = new Date();
            self.callOngoing(true);            
            self.addAction("START CALL", "");
        };
        
        self.endCall = function() {
            if (!self.callOngoing())
                return;            
            self.endAt = new Date();
//            var resolution = $('#callResolution', self.dom).val();
            if (!self.resolution()) {
                alert('Please select call resolution first!');
                return;
            }
            self.addAction("END CALL", self.resolution());
            // Save to database.
            var x = {
                startAt:self.startAt.asISODateString(),
                endAt:self.endAt.asISODateString(),
                hasMember:ko.toJS(self.actions),
                hasUsername:cirm.user.username
            };
            // TODO: maybe we should block here until the full call interaction history is saved?
            cirm.op.postObject('/create/ServiceCall', x, function(r) {
                    var msg = "Call history saved:";
                    $.each(x.hasMember, function(i,a) {
                            msg += a.hasName + " " + a.hasValue;
                            if (a.hasName != "END CALL")
                                msg += ",";
                    });
                    $.each($('.interaction_success'), function (i,el) {
                            if ($(el).parent().is(':visible'))
                                $(el).html(msg)
                                   .fadeIn(1000)
                                   .delay(5000)
                                   .animate({height:0, opacity:0}, 3000);
                    });
            });
            self.actions.removeAll();            
            self.callOngoing(false);
            self.resolution("");
            $(document).trigger(InteractionEvents.EndCall, []);            
            self.startCall();           
        };
        
        self.actionDescription = function(a) {
            var result = a.hasName;
            if (a.hasValue) result += " - " + a.hasValue;
            var d = new Date(a.occurAt);
            return result + " - " + d.format("mmmm dS, h:MM:ss TT");
        };            
        
        // changing resolution dropdown automatically ends the call with that resolution
        self.resolution.subscribe(function (newValue) {
               self.endCall();
        }, self);
        return this;
    }
    
    var serviceCallModel = null;
    function getServiceCallModel() { 
        if (serviceCallModel == null)
            serviceCallModel = new ServiceCallModel();
        return serviceCallModel;
    }
    
    function interactionHistory() {
        var self = {};
        self.dom = $.tmpl($('#callActionsTemplate', legacyTemplates))[0];        
        self.model = getServiceCallModel();        
        self.embed = function(parent) {
            ko.applyBindings(self.model, self.dom);
            var txtNote = $('#txtCallActionNotetype', self.dom);
            txtNote.autoResize({
                    onResize : function() { $(this).css({opacity:0.8}); },
                    animateCallback : function() { $(this).css({opacity:1}); },
                    animateDuration : 300,
                    extraSpace : 30 })
                .keyup(function(event) {
                        if(event.keyCode == 13){
                            self.model.addAction("Note", txtNote.val());
                            txtNote.val('').change();
                        }
                });
              $(parent).append(self.dom);
              self.model.startCall();              
              return self;
        }        
        return self;
    }

    function getMarqueeMessages() {
        var messages = ko.observableArray();
        //var config = cirm.op.get('/individual/AnswerHubMarqueeList');        
        //cirm.op.get('/list', {q:{type:'AnswerHubMarqueeList'}});
        cirm.op.async().get('/individual/AnswerHubMarqueeList', {}, function (config) {
            if (!config.ok) {
                console.log('Unable to read AnswerHubMarqueeList', config.error);
                return messages;
            }
            config = config.data;
            if (config.hasMember === undefined)
                return messages;
            var A = U.isArray(config.hasMember) ?  config.hasMember : [config.hasMember];
            $.each(A, function(i,v) { 
                messages.push({
                        hasMarqueeImportance:ko.observable(v.hasMarqueeImportance), 
                        hasText:ko.observable(v.hasText), 
                        type:'AnswerHubConfiguration'});
            });
            cirm.events.trigger({entity:config});
        });
        return messages;
    }
    
    function MarqueeAdminModel(dom) {
        var self = this;
        self.dom = dom;
        self.messages = getMarqueeMessages(); 
        self.addMessage = function() {
            self.messages.push({
                    hasMarqueeImportance:ko.observable('low'), 
                    hasText:ko.observable('type message here'), 
                    type:'AnswerHubConfiguration'});
            $('textarea', self.dom)
              .filter(function (i, el) { return !$(el).data('AutoResizer'); })
              .autoResize({
                    onResize : function() { $(this).css({opacity:0.8}); },
                    animateCallback : function() { $(this).css({opacity:1}); },
                    animateDuration : 300,
                    extraSpace : 0 });            
        };
        self.removeMessage = function() {
            self.messages.remove(this);
        };
        self.saveMarquee = function () {
            var x = {
                iri:"http://www.miamidade.gov/ontology#AnswerHubMarqueeList",
                type:"AnswerHubConfiguration",
                hasMember:ko.toJS(self.messages)
            };
            console.log('saving marquee ', ko.toJS(self.messages));
            cirm.op.postObject('/individual/AnswerHubMarqueeList', 
                x, function(r) {
                    console.log(r);
                });
        };
    }
    
    function marqueeAdmin() {
        var self = this;
        self.dom = $.tmpl($('#marqueeAdminTemplate', legacyTemplates))[0];        
        self.model = new MarqueeAdminModel(self.dom);
        self.embed = function(parent) {
            ko.applyBindings(self.model, self.dom);
            $('textarea', self.dom)
              .autoResize({
                    onResize : function() { $(this).css({opacity:0.8}); },
                    animateCallback : function() { $(this).css({opacity:1}); },
                    animateDuration : 300,
                    extraSpace : 0 });
              $(parent).append(self.dom);
              return self;
        }
        return self;
    }
    
    function MarqueeModel(dom) {
        var self = {};
        self.dom = dom;
        self.marquee = getMarqueeMessages();
        cirm.events.bind("http://www.miamidade.gov/ontology#AnswerHubMarqueeList", function() {
                //console.log('received marquee event', this);                
                self.marquee.removeAll();
                $.each(this.entity.hasMember, function(i,v) {
                        self.marquee.push({
                            hasMarqueeImportance:ko.observable(v.hasMarqueeImportance), 
                            hasText:ko.observable(v.hasText), 
                            type:'AnswerHubConfiguration'});
                });
                $("#marquee").marquee();
        });        
        return self;
    }
    
    function marquee() {
        var self = {};
        self.dom = $.tmpl($('#marqueeTemplate', legacyTemplates))[0];        
        self.model = new MarqueeModel(self.dom);
        self.embed = function(parent) {
              ko.applyBindings(self.model, self.dom);            
              $(parent).append(self.dom); 
              $("#marquee", $(parent)).marquee();            
              return self;
        };
        return self;
    }
    
    function getWCSModel(dom, addressModel) {
		self.dom = dom;
		self.address = addressModel;
		self.accountNumber = ko.observable("");
		self.result = ko.observable({"record":null});

		self.validateResult = ko.observable({ "AccountInfo":{Accounts:[], "ReturnMsg": ""} });
		U.visit(self.validateResult(), U.makeObservableTrimmed);
		self.enableValidationForBULKYTRA = false;

		function alertDialogWCS(msg) {
	    	$("#wcs_dialog_alert")[0].innerText = msg;
			$("#wcs_dialog_alert").dialog({ height: 230, width: 500, modal: true, buttons: {
				"Close" : function() { $("#wcs_dialog_alert").dialog('close'); }
			 } 
			});
		};
		
		self.validateInWCS = function(el, event) {
			//var query = "/legacy/ws/";
			if(!U.isEmptyString(self.accountNumber()))
			{
				self.accountLookupByAccount();
				return;
			}
			if(self.address != undefined && self.address.unit() == "MULTI") {
				alertDialogWCS("This address contains MULTIPLE Units. Please select an address with specific Unit No.");
				return;
			}
			if(self.address === undefined || U.isEmptyString(self.address.fullAddress())) {
				alertDialogWCS("This address does not have a folio");
				return;
			}
			else {
				if(!U.isEmptyString(self.address.folio()))
					self.accountLookupbyFolio();
				else
					self.accountLookupByAddress();
/*
					query = query+"WCSAccountQueryByFolio?arg="+self.address.folio();
				else
					query = query+"WCSAccountQueryByAddress?arg="+self.address.fullAddress();
				$("#legacy_dialog_wcs_validate").dialog({height: 140, modal: true});
				cirm.top.async().get(query, {}, function(results) {
					var account = {"AccountInfo":{Accounts:[], "ReturnMsg": ""}};
					if(results.result) {
						var temp = results.result.WCS;
						account.AccountInfo.ReturnMsg = temp.RETURNMSG;
						if(temp.RETURNMSG != 'EMPTY')
							account.AccountInfo.Accounts = U.isArray(temp.Accounts) ? temp.Accounts : [temp.Accounts];
					}
					self.validateResult(account);
					$("#legacy_dialog_wcs_validate").dialog('close');
					if(self.validateResult().AccountInfo.Accounts.length == 0 || self.validateResult().AccountInfo.ReturnMsg == "EMPTY")
						alertDialogWCS("No results found for this address");
					else if(self.validateResult().AccountInfo.Accounts.length == 1)
						getWCSResults(self.validateResult().AccountInfo.Accounts[0].Account.AccountNumber);
					else if(self.validateResult().AccountInfo.Accounts.length > 1)
						$("#legacy_dialog_wcs_resolve").dialog({ height: 400, width: 500, modal: true });
				});
*/						
			}
		};
		
		self.accountLookupbyFolio = function() {
			$("#legacy_dialog_wcs_validate").dialog({height: 140, modal: true});
			var query = "/legacy/ws/WCSAccountQueryByFolio?arg="+self.address.folio();
			cirm.top.async().get(query, {}, function(results) {
				$("#legacy_dialog_wcs_validate").dialog('close');
				if(results.ok == false && results.error)
					self.serviceLookupByAddress();
				if(results.ok)
				{
					if(results.result.WCS.RETURNMSG == 'INVALID')
						self.accountLookupByAddress();
					else
						self.populateDetails(results);
				}
			});
		};
		
		self.accountLookupByAddress = function() {
			$("#legacy_dialog_wcs_validate").dialog({height: 140, modal: true});
			var query = "/legacy/ws/WCSAccountQueryByAddress?arg="+self.address.fullAddress();
			cirm.top.async().get(query, {}, function(results) {
				self.populateDetails(results);
			});
		};
		
		self.accountLookupByAccount = function() {
			$("#legacy_dialog_wcs_validate").dialog({height: 140, modal: true});
			var query = "/legacy/ws/WCSAccountQueryByAccount?arg="+self.accountNumber();
			cirm.top.async().get(query, {}, function(results) {
				self.populateDetails(results);
			});
		};
		
		
		self.populateDetails = function(results) {
			var account = {"AccountInfo":{Accounts:[], "ReturnMsg": ""}};
			if(results.result) {
				var temp = results.result.WCS;
				account.AccountInfo.ReturnMsg = temp.RETURNMSG;
				if(temp.RETURNMSG != 'EMPTY')
					account.AccountInfo.Accounts = U.isArray(temp.Accounts) ? temp.Accounts : [temp.Accounts];
			}
			self.validateResult(account);
			$("#legacy_dialog_wcs_validate").dialog('close');
			if(self.validateResult().AccountInfo.Accounts.length == 0 || self.validateResult().AccountInfo.ReturnMsg == "EMPTY")
				alertDialogWCS("No results found for this address");
			else if(self.validateResult().AccountInfo.Accounts.length == 1)
				getWCSResults(self.validateResult().AccountInfo.Accounts[0].Account.AccountNumber);
			else if(self.validateResult().AccountInfo.Accounts.length > 1)
				$("#legacy_dialog_wcs_resolve").dialog({ height: 400, width: 500, modal: true });
		};
		
		self.getIndividualWCSInfo = function(el, event) {
			$("#legacy_dialog_wcs_resolve").dialog('close');
			getWCSResults(el.Account.AccountNumber);
		};
		
		function getWCSResults(accountNo) {
			$("#legacy_dialog_wcs_validate").dialog({height: 140, modal: true});
			var result = {"record": {
							"AccountInfo":{Accounts:[], "ReturnMsg": ""}, 
							"WorkOrderInfo":{WorkOrders:[], "ReturnMsg": ""}, 
							"PublicComplaintInfo":{PublicComplaints:[], "ReturnMsg": ""}, 
							"EnforcementComplaintInfo":{EnforcementComplaints:[], "ReturnMsg": ""}
						} };
			var accountWS = "WCSAccountQueryByAccount";
			var bulkyWS = "WCSBulkyQueryByAccount";
			var publicWS = "WCSPublicComplaintQueryByAccount";
			var enforcementWS = "WCSEnforcementComplaintQueryByAccount";

			cirm.top.async().get('/legacy/ws/'+accountWS+'?arg='+accountNo, {}, function(accountResults) {
				if(accountResults.result) {
					var temp = accountResults.result.WCS;
					result.record.AccountInfo.ReturnMsg = temp.RETURNMSG;
					if(temp.RETURNMSG != 'EMPTY')
						result.record.AccountInfo.Accounts = U.isArray(temp.Accounts) ? temp.Accounts : [temp.Accounts];
				}
				cirm.top.async().get('/legacy/ws/'+bulkyWS+'?arg='+accountNo, {}, function(bulkyResults) {
					if(bulkyResults.result) {
						var temp = bulkyResults.result.WCS;
						result.record.WorkOrderInfo.ReturnMsg = temp.RETURNMSG;
						if(temp.RETURNMSG != 'EMPTY')
							result.record.WorkOrderInfo.WorkOrders = U.isArray(temp.WorkOrders) ? temp.WorkOrders : [temp.WorkOrders];
					}
					cirm.top.async().get('/legacy/ws/'+publicWS+'?arg='+accountNo, {}, function(publicResults) {
						if(publicResults.result) {
							var temp = publicResults.result.WCS;
							result.record.PublicComplaintInfo.ReturnMsg = temp.RETURNMSG;
							if(temp.RETURNMSG != 'EMPTY')
								result.record.PublicComplaintInfo.PublicComplaints = U.isArray(temp.PublicComplaints) ? temp.PublicComplaints : [temp.PublicComplaints];
						}
						cirm.top.async().get('/legacy/ws/'+enforcementWS+'?arg='+accountNo, {}, function(enforcementResults){
							if(enforcementResults.result) {
								var temp = enforcementResults.result.WCS;
								result.record.EnforcementComplaintInfo.ReturnMsg = temp.RETURNMSG;
								if(temp.RETURNMSG != 'EMPTY')
									result.record.EnforcementComplaintInfo.EnforcementComplaints = U.isArray(temp.EnforcementComplaints) ? temp.EnforcementComplaints : [temp.EnforcementComplaints];
							}
							$("#legacy_dialog_wcs_validate").dialog('close');
							self.result(result);
						});
					});
				});
			});
		}

		self.publishAccountInfo = function () {
			$(document).trigger("http://www.miamidade.gov/ontology#WCSAccountSelectedEvent",
			    [{accountNumber:self.result().record.AccountInfo.Accounts[0].Account.AccountNumber}]);
			//var ownerName = self.result().record.AccountInfo.Accounts[0].Account.OwnerName;
			//$(document).trigger(InteractionEvents.populateWCSAccountOwnerActor, [ownerName]);
			var actorDetails = {"hasLegacyCode":"ACCOUNTO", 
						"ownerName":self.result().record.AccountInfo.Accounts[0].Account.OwnerName,
						"hasAddress":false};
			$(document).trigger(InteractionEvents.populateActor, [actorDetails]);
			$(document).trigger(InteractionEvents.showServiceHub, []);			
		};
		/**
		 * Handles the SR type change when the sr type is cleared and a new sr type is added.
		 */
		
		self.handleSrTypeChange = function(srTypeFragment) 
		{
			self.setValidationForBULKYTRA(srTypeFragment == "BULKYTRA");
		}
		/**
		 * Sets validation as true if sr type is BULKYTRA
		 */
		self.setValidationForBULKYTRA = function (enabled) 
		{
			self.enableValidationForBULKYTRA = enabled;
		};
		/**
		 * Disables the Send to SR button if sr type is BULKYTRA and if validateForBULKYTRA returns false. 
		 */
		self.isButtonDisable = function () {
			//Only validate if srEditor enabled bulkytra validation
			if (!self.enableValidationForBULKYTRA) 
			{ 
				return false;
			} else 
			{
				//Don't disable button if validation is ok.
				return !validateForBULKYTRA();
			}
		};
		
		/**
		 * Validates the wcs service results for a BULKYTRA sr.
		 * returns true, if WCS results allow for a new BULKYTRA SR for the given account.
		 */
		self.validateForBULKYTRA = function() {
			var validationResult;
			var accNO = self.result().record.AccountInfo.Accounts[0].Account.AccountNumber;
			var noOfTripsLeft = cirm.top.get('/legacy/ws/WCSAccountQueryByAccount?arg='+accNO).result.WCS.Accounts.Account.Trips;
			var accStatus = cirm.top.get('/legacy/ws/WCSAccountQueryByAccount?arg='+accNO).result.WCS.Accounts.Account.AccountStatus;
			var workOrders = cirm.top.get('/legacy/ws/WCSBulkyQueryByAccount?arg='+accNO).result.WCS.WorkOrders;
			var isPendingWorkOrder = false;
			var pendingWorkOrderStatus = null;
			workOrders = U.ensureArray(workOrders);
			for (var i = 0; i < workOrders.length; i++) 
			{ 
				if (workOrders[i].WorkOrder.OrderStatus == "PP"  || workOrders[i].WorkOrder.OrderStatus == "SC" || workOrders[i].WorkOrder.OrderStatus == "CK")
				{
					isPendingWorkOrder = true;
					pendingWorkOrderStatus = workOrders[i].WorkOrder.OrderStatus;
				}
			}
			validationResult = (noOfTripsLeft > 0 && accStatus == null && !isPendingWorkOrder);
			if (!validationResult) 
			{
				alertDialogWCS(self.getWCSErrorMessage(noOfTripsLeft, accStatus, isPendingWorkOrder, pendingWorkOrderStatus));
			}
			return validationResult;
		}
		/**
		 * Builds the error message to be displayed on the screen when the validation fails.
		 */
		
		self.getWCSErrorMessage = function (noOfTripsLeft, accStatus,isPendingWorkOrder, pendingWorkOrderStatus) {
			var WCSErrorMessage = "The SR cannot be opened for this address because: \n";
			if(noOfTripsLeft < 1)
			{
					WCSErrorMessage += "There are no Bulky Free trips available for this address. \n Number of Trips:" + noOfTripsLeft;	
			}
			if(accStatus != null)
			{
				WCSErrorMessage += "\nThe account status is not Active for this address. \n Account Status:" + accStatus;	
			}
			if(isPendingWorkOrder)
			{
				WCSErrorMessage += "\nThere is a pending work order for this address. \n Pending Work Order Status:" + pendingWorkOrderStatus;	
			}			
			return WCSErrorMessage;
		};
		
		
		self.clear = function() {
			self.result({"record":null});
			self.accountNumber("");
		};
		
		$(document).bind(InteractionEvents.WCSClear, function(event) {
			self.clear();
		});

		$(document).bind(InteractionEvents.SrTypeSelection, function(event, srTypeFragment) {
			self.handleSrTypeChange(srTypeFragment);
		});

		$(document).bind(InteractionEvents.ServiceRequestTypeClick, function(event, type) {
			self.handleSrTypeChange(U.IRI.name(type));
		});

	 	return self;
	} // End getWCSModel ----------------------------------------------------------------------------------------------
    
    function wcs(addressModel) {
    	var self = {};
        self.dom = $.tmpl($('#wcsTemplate', legacyTemplates))[0];
        self.model = getWCSModel(self.dom, addressModel);
        self.embed = function(parent) {
            ko.applyBindings(self.model, self.dom);
        	$(parent).append(self.dom);
        	return self;
        };
    	return self;
    }

	function getElectionsModel(dom) {
		var self = {};
		self.searchCriteria = ko.observable({"FNAME":"", "LNAME":"", "BIRTHDATE":"", "SSNUM":"", "FVRSIDNUM":"", "CERTNUM":"", "address":""});
		U.visit(self.searchCriteria(), U.makeObservableTrimmed);
		self.dom = dom;
		self.result = ko.observable({"record":null});

		function alertDialogElections(msg) {
	    	$("#elections_dialog_alert")[0].innerText = msg;
			$("#elections_dialog_alert").dialog({ height: 150, width: 500, modal: true, buttons: {
				"Close" : function() { $("#elections_dialog_alert").dialog('close'); }
			 } 
			});
		};

		self.getElectionsInfo = function(el, event) {
			var search = ko.toJS(self.searchCriteria);
			if(search.FNAME == "" && search.LNAME == "" && search.BIRTHDATE == "" && search.SSNUM == ""
				&& search.FVRSIDNUM == "" && search.CERTNUM == "") {
				alertDialogElections("Please select a search criteria");
				return;
			}
			if(search.BIRTHDATE != "")
				search.BIRTHDATE = new Date(search.BIRTHDATE).asISODateString(true);
			$("#legacy_dialog_elections_progress").dialog({height: 140, modal: true});
			cirm.top.async().get("/elections/query?arg="+JSON.stringify(search)+"&flag=false", {}, function(results) {
				$("#legacy_dialog_elections_progress").dialog('close');
				if(results.ok == true) {
					if(results.result && results.result.totalCount)
						alertDialogElections("Too many search results. Please refine your search criteria");
					if(results.result)
						self.result(results.result);
					if(self.result().records) {
						if(self.result().records.length > 1)
							$("#legacy_dialog_elections_resolve").dialog({ height: 400, width: 400, modal: true });
						if(self.result().records.length == 0)
							alertDialogElections("No results found");
					}
				}
				else if(results.ok == false)
					alertDialogElections("No results found");
			});
		};
		
		self.getIndividualElectionInfo = function (el, event) {
			$("#legacy_dialog_elections_resolve").dialog('close');
			var search = ko.toJS(el);
			search.BIRTHDATE = new Date(search.BIRTHDATE).asISODateString(true);
			$("#legacy_dialog_elections_progress").dialog({height: 140, modal: true});
			cirm.top.async().get("/elections/query?arg="+JSON.stringify(search)+"&flag=true", {}, function(results) {
				$("#legacy_dialog_elections_progress").dialog('close');
				if(results.result && results.result.record)
					self.result(results.result);
				else
					alertDialogElections("No search results found");
			});
		};
		
		$(document).bind(InteractionEvents.ElectionsClear, function(event) {
			self.clear();
		});

		self.clear = function() {
			self.searchCriteria().FNAME("");
			self.searchCriteria().LNAME("");
			self.searchCriteria().BIRTHDATE("");
			self.searchCriteria().SSNUM("");
			self.searchCriteria().FVRSIDNUM("");
			self.searchCriteria().CERTNUM("");
			self.searchCriteria().address("");
			self.result({"record":null});
			patchPlaceholdersforIE();
		};

		return self;
	}

	function elections() {
		var self={};
		self.dom = $.tmpl($('#electionsTemplate', legacyTemplates))[0];
		self.model = getElectionsModel(self.dom);
        self.embed = function(parent) {
            ko.applyBindings(self.model, self.dom);
        	$(parent).append(self.dom);
        	return self;
        };
		return self;
	}
	
	function getASDModel(dom) {
		var self = {};
		self.searchCriteria = ko.observable({"first_name":"", "last_name":"", "phone":"", "tag_no":"", 
			"street_no":"", "street_name":"", "street_dir":"", "street_type":"", "apt":"", "city":"", "zip_code":""});
		U.visit(self.searchCriteria(), U.makeObservableTrimmed);
		self.dom = dom;
		self.result = ko.observable({"record":null});

        // Detail sections
		self.sections = [
		    {title:'Chameleon Lookup', div:'chameleon_lookup'}, 
		    {title:'Dispatch Lookup', div:'dispatch_lookup'}];
		self.currentSection = ko.observable(self.sections[0]);
		self.goToSection = function(section) {
		        self.currentSection(section);
		};

		function alertDialogASD(msg) {
	    	$("#asd_dialog_alert")[0].innerText = msg;
			$("#asd_dialog_alert").dialog({ height: 150, width: 500, modal: true, buttons: {
				"Close" : function() { $("#asd_dialog_alert").dialog('close'); }
			 } 
			});
		};
		
		self.buildASDAddress = function(el) {
			return 	el.Address.Street_Number + ' ' + 
					el.Address.Street_Direction + ' ' +
					el.Address.Street_Name + ' ' +
					el.Address.hasStreetType + ' ' +
					el.Address.Street_Address_City + ', ' +
					el.Address.Street_Address_State + ' - ' +
					el.Address.Zip_Code + ' ';
		};

		self.getASDInfo = function(el, event) {
			var search = ko.toJS(self.searchCriteria);
			if(search.first_name == "" && search.last_name == "" && search.phone == "" && search.tag_no == "" 
				&& search.street_no == "" && search.street_name == "" && search.street_dir == "" && search.street_type == ""
				&& search.apt == "" && search.city == "" && search.zip_code == "") {
				alertDialogASD("Please select a search criteria");
				return;
			}
			if(!U.isEmptyString(search.phone)) {
				if(search.phone.length == 10) {
					search.phone_area_code = search.phone.substr(0,3);
					search.phone_number = search.phone.substr(3);
					delete search.phone;
				}
				//If phone length not 10 then show a dialog
				else {
					alertDialogASD("Please enter the Phone No. with exactly 10 digits");
					return;
				}
			}
			$("#legacy_dialog_asd_progress").dialog({height: 140, modal: true});
			cirm.top.async().get("/asd/query?arg="+JSON.stringify(search), {}, function(results) {
				if(results.ok == true) {
					if(results.result) {
						if(results.result.records) {
							$("#legacy_dialog_asd_progress").dialog('close');
							if(results.result.records.length == 0) {
								alertDialogASD("No results found");
							}
							else if(results.result.records.length > 1) {
								if(results.result.records.length > 200)
									alertDialogASD("Too many search results. Please refine your search criteria");
								else
								{
									self.result(results.result);
									$("#legacy_dialog_asd_resolve").dialog({ height: 400, width: 700, modal: true });
								}
							}
							else if(results.result.records.length == 1) {
								var record = {"record":results.result.records[0]};
								self.result(record);
							}
						}
					}
				}
				else if(results.ok == false)
				{
					$("#legacy_dialog_asd_progress").dialog('close');
					alertDialogASD("No results found");
				}
			});
		};
		
		self.getIndividualASDInfo = function (el, event) {
			$("#legacy_dialog_asd_resolve").dialog('close');
			var record = {"record":el};
			self.result(record);
		};
		
		self.publishASDInfo = function () {
			$(document).trigger("http://www.miamidade.gov/ontology#ASDAnimalNameSelectedEvent",
			    [{animalName:self.result().record.AnimalName}]);
			$(document).trigger("http://www.miamidade.gov/ontology#ASDAnimalTypeSelectedEvent",
			    [{animalType:self.result().record.AnimalType}]);
			$(document).trigger("http://www.miamidade.gov/ontology#ASDTagNumberSelectedEvent",
			    [{tagNumber:self.result().record.Tag}]);
			$(document).trigger("http://www.miamidade.gov/ontology#ASDTagExpiresSelectedEvent",
			    [{tagExpires:self.result().record.TagExpires}]);
			var actorDetails = {"hasLegacyCode":"CHPETOWN", 
						"ownerName":self.result().record.Name,
						"hasAddress":true,
						"atAddress":self.result().record.Address,
						"phone":self.result().record.Phone
			};
			$(document).trigger(InteractionEvents.populateActor, [actorDetails]);
			$(document).trigger(InteractionEvents.showServiceHub, []);
		};

		$(document).bind(InteractionEvents.ASDClear, function(event) {
			self.clear();
			//self.clearDispatch();
		});

		self.clear = function() {
			self.searchCriteria().first_name("");
			self.searchCriteria().last_name("");
			self.searchCriteria().phone("");
			self.searchCriteria().tag_no("");
			//self.searchCriteria().vaccine("");
			self.searchCriteria().street_no("");
			self.searchCriteria().street_dir("");
			self.searchCriteria().street_name("");
			self.searchCriteria().street_type("");
			self.searchCriteria().apt("");
			self.searchCriteria().city("");
			self.searchCriteria().zip_code("");
			self.result({"record":null});
			patchPlaceholdersforIE();
		};
		
		//Dispatch Lookup
		
		self.dispatchResult = {"records":[]};
		U.visit(self.dispatchResult, U.makeObservableTrimmed);

		self.misc = {
			"currentPage":1, "itemsPerPage":15, "counter":0, "query":{}, 
			"sortDetails":{"sortBy":"boid", "sortDirection":"desc"},
			"tempRecord":{
				"SR_ID":"", "SR_TYPE":"", "PRIORITY":"", "STATUS":"", "ACTIVITY":"", "ACTIVITY_IRI":"", 
				"ADDRESS":"", "ZIP":"", "ASSIGNED_TO":"", "DETAILS":"", "OUTCOME":"", "RNUM":"" , "CASE_NUMBER":"",
				"ACTIVITY_LABEL":"", "PRIORITY_LABEL":"", OUTCOME_LABEL:"", "isAccepted":"", "ACT_CREATED_DATE":"",
				"ACT_COMPLETED_DATE":""
			}
		};
		U.visit(self.misc, U.makeObservableTrimmed);

		self.fetchFirst = function(el) {
			if(self.misc.currentPage() == 1)
				return true;
			else
			{
				self.misc.currentPage(1);
				self.getDispatchInfo();
			}
		};
    	self.sortData = function(filterField) {
    		var sortDetails = self.misc.sortDetails();
			if(sortDetails.sortBy() == filterField)
				sortDetails.sortDirection() == "asc" ? sortDetails.sortDirection("desc") : sortDetails.sortDirection("asc");
			else
			{
				sortDetails.sortBy(filterField);
				sortDetails.sortDirection("desc");
			}
    		self.misc.currentPage(1);
			self.getDispatchInfo();
    	};

		self.allStatus = cirm.refs.caseStatuses;
		self.dispatchSC = {
			"type":"", "assignedTo":"", "fromDate":"", "toDate":"", "dateRange":"", "refreshSeconds":60, 
			"zip":"", 
			"hasStatus":{"iri":undefined, "label":""}, 
			"hasActivity":{"iri":undefined, "label":""}, 
			"hasAllowableOutcome":{"iri":undefined, "label":""}
		};

		self.srActivities = ko.observable(new Array());
		self.activityOutcomes = ko.observable(new Array());
		self.allDateRanges = ko.observableArray([
			{"iri":"hasDateCreated", "label":"SR-Date Created"},
			{"iri":"hasDueDate", "label":"Activity-Due Date"},
			{"iri":"hasCompletedTimestamp", "label":"Activity-Date Completed"},
			{"iri":"hasActivityDateCreated", "label":"Activity-Date Created"},
			{"iri":"hasUpdatedDate", "label":"Activity-Date Updated"},
		]);

		U.visit(self.dispatchSC, U.makeObservableTrimmed);

		self.applyDefaultDispatchValues = function() {
			self.dispatchSC.fromDate(Date.parse("-90").toString('MM/dd/yyyy'));
			self.dispatchSC.dateRange("hasDateCreated");
			var openStatusIRI = $.map(cirm.refs.caseStatuses, function(v,i) { if(v.label == 'Open') return v.iri;})[0];			
			self.dispatchSC.hasStatus().iri(openStatusIRI);
		};

		self.applyDefaultDispatchValues();

		self.isPopulatedActivityOutcomes = ko.computed(function() {
			self.dispatchSC.hasAllowableOutcome().iri(undefined);
			if(!U.isEmptyString(self.srActivities()))
			{
				var activity = $.grep(self.srActivities(), function (t) {
					return self.dispatchSC.hasActivity().iri() == t.iri;
				});
				if(activity.length > 0)
				{
					var A = U.ensureArray(activity[0].hasAllowableOutcome);
					self.activityOutcomes(A);
					return true;
				}
			}
			return false;
		});

		self.isPopulatedSRActivities = ko.computed(function() {
			self.dispatchSC.hasActivity().iri(undefined);
			var tempSRType = self.dispatchSC.type();
			if(!U.isEmptyString(tempSRType) && tempSRType != 'Service Request Type') {
				var srTypeShort = cirm.refs.serviceCaseList[tempSRType];
				var srType = cirm.refs.serviceCases['http://www.miamidade.gov/cirm/legacy#'+srTypeShort];
				if(srType)
				{
			        srType.hasActivity.sort(function(a,b) {
			            return parseFloat(a.hasOrderBy) - parseFloat(b.hasOrderBy) } );
					self.srActivities(srType.hasActivity);
					return true;
				}
			}
			//self.srActivities(["Select Activity"]);
			return false;
		});

		self.isDisabledServiceActivity = ko.computed(function() {
			var tempSRType = self.dispatchSC.type();
			if(!U.isEmptyString(tempSRType) && tempSRType != 'Service Request Type') {
				var srtypeShort = cirm.refs.serviceCaseList[tempSRType];
				var srtype = cirm.refs.serviceCases['http://www.miamidade.gov/cirm/legacy#'+srtypeShort];
				return (srtype === undefined);
			}
			return true;
		});

		self.isDisabledActivityOutcomes = ko.computed(function() {
			var currentActivity = self.dispatchSC.hasActivity().iri();
			if (!U.isEmptyString(currentActivity) && currentActivity != 'Select Activity')
				return false;
			else
				return true;
		});

    	self.getFilteredSCList = function() {
			return function(s, back) {
				var matcher = new RegExp("\\b" + $.ui.autocomplete.escapeRegex(s.term), "i" );
				back($.grep(cirm.refs.autoCompleteServiceCaseList, function(value) {
					return matcher.test( value.label || value.value || value );
			})) };
    	};

    	self.lookupAssignedStaff = function() {
			$("#sh_dialog_address_search").dialog({ height: 140, modal: true }).dialog('close');
			return function(s, back) {
				var results = [];
				if(s.term.length >= 3)
				{
					$.grep(cirm.top.get('/users/search?name='+s.term), function(v) {
						var val = {"label":v.hasFullName, "value":v.hasEmployeeKey};
						results.push(val);
					});
					back(results);
				}
			};
		};
		
		self.checkDateOnEnter = function(data, event) {
    		if(event.keyCode == 9 || event.keyCode == 13) {
    			self.checkDate(data, event);
    		}
    		return true;
    	};
    	
    	self.checkDate = function(data, event) {
			$("#sh_dialog_address_search").dialog({ height: 140, modal: true });
			$("#sh_dialog_address_search").dialog('close');
    		var inputValue = $(event.currentTarget).val();
    		if(Date.parse(inputValue) != null) {
    			self.dispatchSC[event.currentTarget.id](Date.parse(inputValue).toString('MM/dd/yyyy'));
    		}
    		event.currentTarget.focus();
			patchPlaceholdersforIE();
    	};
    	
		self.clearTempRecord = function() {
			self.misc.tempRecord().RNUM("");
			self.misc.tempRecord().SR_ID("");
			self.misc.tempRecord().CASE_NUMBER("");
			self.misc.tempRecord().PRIORITY("");
			self.misc.tempRecord().PRIORITY_LABEL("");
			self.misc.tempRecord().STATUS("");
			self.misc.tempRecord().ACTIVITY("");
			self.misc.tempRecord().ACTIVITY_IRI("");
			self.misc.tempRecord().ACTIVITY_LABEL("");
			self.misc.tempRecord().ACT_CREATED_DATE("");
			self.misc.tempRecord().ACT_COMPLETED_DATE("");
			self.misc.tempRecord().ADDRESS("");
			self.misc.tempRecord().ZIP("");
			self.misc.tempRecord().ASSIGNED_TO("");
			self.misc.tempRecord().DETAILS("");
			self.misc.tempRecord().SR_TYPE("");
			self.misc.tempRecord().OUTCOME(undefined);
			self.misc.tempRecord().OUTCOME_LABEL("");
			self.misc.tempRecord().isAccepted("");
		};
		
		self.populateTempRecord = function(el) {
			self.misc.tempRecord().RNUM(el.RNUM());
			self.misc.tempRecord().SR_ID(el.SR_ID());
			self.misc.tempRecord().CASE_NUMBER(self.buildCaseNumber(el));
			self.misc.tempRecord().SR_TYPE(el.SR_TYPE());
			self.misc.tempRecord().PRIORITY(el.PRIORITY());
			self.misc.tempRecord().PRIORITY_LABEL(el.PRIORITY_LABEL());
			self.misc.tempRecord().STATUS(el.STATUS());
			self.misc.tempRecord().ACTIVITY(el.ACTIVITY());
			self.misc.tempRecord().ACTIVITY_IRI(el.ACTIVITY_IRI());
			self.misc.tempRecord().ACTIVITY_LABEL(el.ACTIVITY_LABEL());
			self.misc.tempRecord().ACT_CREATED_DATE(el.ACT_CREATED_DATE());
			self.misc.tempRecord().ACT_COMPLETED_DATE(el.ACT_COMPLETED_DATE());
			self.misc.tempRecord().ADDRESS(el.ADDRESS());
			self.misc.tempRecord().ZIP(el.ZIP());
			self.misc.tempRecord().ASSIGNED_TO(el.ASSIGNED_TO());
			self.misc.tempRecord().DETAILS(el.DETAILS());
			self.misc.tempRecord().OUTCOME(el.OUTCOME());
			self.misc.tempRecord().OUTCOME_LABEL(el.OUTCOME_LABEL());
			self.misc.tempRecord().isAccepted("");
		};

		self.isDisabledDate = function() { 
			if(self.dispatchSC.dateRange() === undefined)
				return true;
			else
				return false;
		};

/*		
		self.colorResultsRow = function(el) {
			return $.inArray(el, self.dispatchResult.records())%2;
		};
*/
/*
		self.resolveAddress = function(el) {
			if(el)
			{
				if(U.isEmptyString(el.ADDRESS()))
					return "";
				return el.ADDRESS() + ', ' + el.ZIP();
			}
			else {
				if(U.isEmptyString(self.misc.tempRecord().ADDRESS()))
					return "";
				return self.misc.tempRecord().ADDRESS() + ', ' + self.misc.tempRecord().ZIP();
			}
		};
*/
/*		
		self.resolveStatus = function(el) {
			if(el)
			{
				if(U.isEmptyString(el.STATUS))
					return "";
				var label = $.map(self.allStatus, function(v) {
					if(v.iri == el.STATUS)
						return v.label;
				});
				return label[0];
			}
			else
			{
				if(U.isEmptyString(self.misc.tempRecord().STATUS()))
					return "";
				var label = $.map(self.allStatus, function(v) {
					if(v.iri == self.misc.tempRecord().STATUS())
						return v.label;
				});
				return label[0];
			}
		};
*/

		self.resolvePriority = function(el) {
			if(el)
			{
				if(U.isEmptyString(el.PRIORITY()))
					return "";
				var label = $.map(cirm.refs.casePriorities, function(v) {
					if(v.iri == el.PRIORITY())
						return v.label;
				});
				el.PRIORITY_LABEL(label[0]);
				return label[0];
			}
/*
			else
			{
				if(U.isEmptyString(self.misc.tempRecord().PRIORITY()))
					return "";
				var label = $.map(cirm.refs.casePriorities, function(v) {
					if(v.iri == self.misc.tempRecord().PRIORITY())
						return v.label;
				});
				self.misc.tempRecord().PRIORITY_LABEL(label[0]);
				return label[0];
			}
*/			
		};
		
		self.resolveSRType = function(el) {
			if(el && !U.isEmptyString(el.SR_TYPE()))
				return cirm.refs.serviceCases[el.SR_TYPE()].label;
			else
				return "";
		};
		
		self.resolveActivity = function(el) {
			if(el)
			{
				if(U.isEmptyString(el.ACTIVITY()) || U.isEmptyString(cirm.refs.serviceCases[el.SR_TYPE()]) )
					return "";
				var label = $.map(cirm.refs.serviceCases[el.SR_TYPE()].hasActivity, function(v) {
					if(v.iri == el.ACTIVITY())
						return v.label;
				});
				el.ACTIVITY_LABEL(label[0]);
				return label[0];
			}
			else
				return "";
/*
			else
			{
				if(U.isEmptyString(self.misc.tempRecord().ACTIVITY()))
					return "";
				var label = $.map(cirm.refs.serviceCases[self.misc.tempRecord().SR_TYPE()].hasActivity, function(v) {
					if(v.iri == self.misc.tempRecord().ACTIVITY())
						return v.label;
				});
				self.misc.tempRecord().ACTIVITY_LABEL(label[0]);
				return label[0];
			}
*/
		};
		
		self.resolveOutcomeText = function(el) {
			if(U.isEmptyString(el.OUTCOME()) || U.isEmptyString(cirm.refs.serviceCases[el.SR_TYPE()]) )
				return "";
			var outcomeList = $.map(cirm.refs.serviceCases[el.SR_TYPE()].hasActivity, function(v) {
				if(v.iri == el.ACTIVITY())
					return v.hasAllowableOutcome;
			});
			var label = $.map(outcomeList, function(v) {
				if(v.iri == el.OUTCOME())
					return v.label;
			});
			el.OUTCOME_LABEL(label[0]);
			return label[0];
		};
		
		self.resolveOutcome = function() {
			if(U.isEmptyString(self.misc.tempRecord().ACTIVITY()) || 
					U.isEmptyString(cirm.refs.serviceCases[self.misc.tempRecord().SR_TYPE()]) )
				return undefined;
			var outcomeList = $.map(cirm.refs.serviceCases[self.misc.tempRecord().SR_TYPE()].hasActivity, function(v) {
				if(v.iri == self.misc.tempRecord().ACTIVITY())
					return v.hasAllowableOutcome;
			});
			return outcomeList;
		};
		
		self.isServiceRequestClosed = function() {
			if(self.misc.tempRecord().STATUS() == 'C-CLOSED')
				return true;
			else
				return false;
		};
		
		self.isServiceRequestLocked = function() {
			if(self.misc.tempRecord().STATUS() == 'O-LOCKED')
				return true;
			else
				return false;
		};

		self.showIsAccepted = function() {
			if(!U.isEmptyString(self.misc.tempRecord().SR_TYPE()))
			{
				var hasDefaultOutcome = self.fetchDefaultOutcome(
											self.misc.tempRecord().SR_TYPE(), 
											self.misc.tempRecord().ACTIVITY());
				if(hasDefaultOutcome.length > 0 && U.isEmptyString(self.misc.tempRecord().OUTCOME()))
					return true;
				else
					return false;
			}
			else
				return false;
		};

		self.disabledAssignedTo = function() {
			if(self.isServiceRequestClosed())
				return true;
			else if(isActivityAutoAssign())
				return true;
			else if(!isActivityUpdateWithinTimeout() && !U.isEmptyString(self.misc.tempRecord().ACT_COMPLETED_DATE()))
				return true;
			else
				return false;
		};
		
		self.disabledHasDetails = function() {
			if(self.isServiceRequestClosed())
				return true;
			else if(!isActivityUpdateWithinTimeout() && !U.isEmptyString(self.misc.tempRecord().ACT_COMPLETED_DATE()))
				return true;
			else
				return false;
		};

		function isActivityUpdateWithinTimeout() {
			if(U.isEmptyString(self.misc.tempRecord().ACT_CREATED_DATE()))
				return true;
			var comparableTime = "";
			var isUpdateAllowed = undefined;
			var timeSinceCreatedMins = 
				( U.getCurrentDate().getTime() - U.getTimeInMillis(self.misc.tempRecord().ACT_CREATED_DATE()) )/60000.0;
			if(cirm.refs.serviceCases[self.misc.tempRecord().SR_TYPE()].hasAnswerUpdateTimeout)
				comparableTime = cirm.refs.serviceCases[self.misc.tempRecord().SR_TYPE()].hasAnswerUpdateTimeout.hasValue;
			else
				comparableTime = parseInt(cirm.refs.serviceCaseClass.hasAnswerUpdateTimeout.hasValue);
			if(!U.isEmptyString(comparableTime))
				isUpdateAllowed = (comparableTime >= timeSinceCreatedMins);
			return isUpdateAllowed;
		}

		function isActivityAutoAssign() {
			var srType = self.misc.tempRecord().SR_TYPE();
			if(!U.isEmptyString(srType) && !U.isEmptyString(self.misc.tempRecord().ACTIVITY()))
			{
				var autoAssign = $.map(cirm.refs.serviceCases[srType].hasActivity, function(v) {
					if(v.iri == self.misc.tempRecord().ACTIVITY() && v.isAutoAssign)
						return v;
				});
				if (autoAssign.length > 0)
					return true;
				else
					return false;
			}
			else
				return false;
		}
		
		self.fetchDefaultOutcome = function(srType, activityIRI) {
			var hasDefaultOutcome = $.grep(cirm.refs.serviceCases[srType].hasActivity, function(v) {
				if(v.iri == activityIRI && v.hasDefaultOutcome) 
					return true;
			});
			return hasDefaultOutcome;
		};
		
		self.disabledOutcome = function() {
			if(self.misc.tempRecord().STATUS() == 'C-CLOSED')
				return true;
			var currentElement = $.map(self.dispatchResult.records(), function(v) {
				if(v.ACTIVITY_IRI() == self.misc.tempRecord().ACTIVITY_IRI())
					return ko.toJS(v);
			});
			if(currentElement.length > 0)
			{
				var hasDefaultOutcome = self.fetchDefaultOutcome(
											self.misc.tempRecord().SR_TYPE(), 
											self.misc.tempRecord().ACTIVITY()
										);
				if(hasDefaultOutcome.length > 0 )
					return true;
				if(hasDefaultOutcome.length == 0 && !U.isEmptyString(currentElement[0].OUTCOME))
					return true;
				else
					return false;
			} 
			else
				return false;
		};

		function resetMisc() {
			self.misc.currentPage(1);
			self.misc.sortDetails().sortBy("boid");
			self.misc.sortDetails().sortDirection("desc");
			self.misc.counter(0);
			self.misc.query({});
		};
		
		self.dispatchRowName = function(el) {
			return "dispatchRow_"+el.RNUM();
			//return el.ACTIVITY_IRI().split('#')[1];
		};

		self.dispatchRow = function(el) {
			var index =	self.dispatchResult.records().indexOf(el)+1;
			return "dispatchRow_"+index;
		};
		
		self.buildCaseNumber = function(el) {
			if(U.isEmptyString(el.CASE_NUMBER()))
			{
				var year = Date.parse(el.CREATED_DATE()).getFullYear().toString();
				return year.substr(year.length - 2) + "-1" + U.addLeadingZeroes(el.SR_ID(), 7);
			}
			else
			{
				//TODO : temp fix, remove this after wiping out the DB 
				if(el.CASE_NUMBER().indexOf("AC") != -1)
				{
					return el.CASE_NUMBER().split('-')[0].split('AC20')[1] + "-" + el.CASE_NUMBER().split('-')[1];
				}
				// END : temp fix
				else
					return el.CASE_NUMBER();
			}
		};

		self.populateSH = function(el) {
			$("#populate_legacy_dialog_alert").dialog({ height: 150, width: 300, modal: true, buttons: {
				"View Activities" : function() {
					$(document).trigger(InteractionEvents.populateSH, [el.SR_ID()]);
					$("#populate_legacy_dialog_alert").dialog('close');
					$(document).trigger(InteractionEvents.showServiceActivities, []);
				},
				"View Report" : function() {
					$("#populate_legacy_dialog_alert").dialog('close');
					U.download("/legacy/printView", {boid:el.SR_ID()}, true);
				},
				"Close": function() {
				  	$("#populate_legacy_dialog_alert").dialog('close');
				}
			  } 
			});
		};
		
		self.highlightID = function(el, event) {
			if(el.SR_TYPE() == "http://www.miamidade.gov/cirm/legacy#ASINJURE" || 
					el.SR_TYPE() == "http://www.miamidade.gov/cirm/legacy#ASPOLAST")
				return true;
			else
				return false; 
		};

		self.viewPopup = function(el, event) {
			self.clearTempRecord();
			self.populateTempRecord(el);
			$("#legacy_dialog_asd_dispatch").dialog({height: 400, width:600, modal: true});
		};
		
/*
		self.highliteRow = function(el, event) {
			var isBlue = event.currentTarget.parentNode.parentNode.style.backgroundColor == 'rgb(173, 216, 230)' ? true : false;
			$.each(self.dispatchResult.records, function(i,v) {
				if($('#dispatchRow_'+i).attr('style').trim() == 'background-color: rgb(173, 216, 230);')
				{
					v.DETAILS = self.tempDetails();
					self.tempDetails("");
					if(self.colorResultsRow(v) > 0)
						$('#dispatchRow_'+i).attr('style', 'background-color: rgb(238, 238, 238);');
					else
						$('#dispatchRow_'+i).attr('style', 'background-color: white;');
				}
			});
			
			if(!isBlue)
			{
				event.currentTarget.parentNode.parentNode.style.backgroundColor = 'rgb(173, 216, 230)';
				self.tempDetails(el.DETAILS);
			}
		};
*/
		self.counter = ko.observable("");
		self.isTimerOn = ko.observable(false);
		
		self.startCountDown = function() {
			self.isTimerOn(true);
			self.counter("");
			self.counter(setInterval(timer, 1000));
		};
		
		function clearTimer(resetTime)
		{
			self.isTimerOn(false);
			clearInterval(self.counter());
			self.counter("");
			if(resetTime)
				self.dispatchSC.refreshSeconds(60);
			$('#pauseCounter').val('Pause');
		}
		
		function timer()
		{
			self.dispatchSC.refreshSeconds(self.dispatchSC.refreshSeconds()-1);
			if(self.dispatchSC.refreshSeconds() == 0 )
			{
				clearTimer(true);
				self.getDispatchInfo();
				return;
			}	
		}
		self.pauseCounter = function(el, event) {
			if(el === undefined)
			{
				$('#pauseCounter').val('Continue');
				clearTimer();
				return true;
			}
			if(event.currentTarget.value == 'Pause')
			{
				clearTimer();
				event.currentTarget.value = 'Continue';
			}
			else
			{
				if(self.isTimerOn())
					clearTimer();
				self.isTimerOn(true);
				self.counter(setInterval(timer, 1000));
				event.currentTarget.value = 'Pause';
			}
		};

		self.updateActivity = function(el) {
			var isSuccess = true;
			var currentRecord = $.map(self.dispatchResult.records(), function(v) {
				if(v.ACTIVITY_IRI() == self.misc.tempRecord().ACTIVITY_IRI())
				{
					v.hasUpdatedDate = U.getCurrentDate().asISODateString();
					v.DETAILS(self.misc.tempRecord().DETAILS());
					v.isModifiedBy = cirm.user.username;
					if(v.STATUS() != 'C-CLOSED')
					{
						v.ASSIGNED_TO(self.misc.tempRecord().ASSIGNED_TO());
						if(U.isEmptyString(v.OUTCOME()) && !U.isEmptyString(self.misc.tempRecord().OUTCOME()))
						{
							v.hasCompletedTimestamp = v.hasUpdatedDate;
							v.OUTCOME(self.misc.tempRecord().OUTCOME());
						}
					}
					v.isAccepted(self.misc.tempRecord().isAccepted());
					return v;
				}
			});
			$("#legacy_dialog_asd_dispatch").dialog('close');
			//var rowName = "dispatchRow_"+(self.dispatchResult.records().indexOf(currentRecord[0])+1);
			//var rowName = currentRecord[0].ACTIVITY_IRI().split('#')[1];
			var rowName = "dispatchRow_"+currentRecord[0].RNUM();
			$('[name="'+rowName+'"]').html('<img src="images/ajax-loader-circular.gif">');
			cirm.top.async().postObject("/asd/dispatchUpdate", ko.toJS(currentRecord[0]),  function(result) {
				if(result.ok == true)
					$('[name="'+rowName+'"]').html('<img src="images/tick.png">');
				else if(result.ok == false)
				{
					isSuccess = false;
					$('[name="'+rowName+'"]').html('<img src="images/cross.png">');
					var msg = "An error occured while updating the Activity. Please contact Tech Support with the following Information : "+result.error;
					alertDialogASD(msg);
					self.pauseCounter();
				}
			});
			return isSuccess;
		};
		
		self.updateActivityFetchNext = function(el) {
			var isSuccess = self.updateActivity();
			if(self.misc.tempRecord().RNUM() == self.dispatchResult.records().length-1) 
			// || self.misc.tempRecord().RNUM() % self.misc.itemsPerPage() == 0)
				return true;
			if(isSuccess)
			{
				var nextEL = $.map(self.dispatchResult.records(), function(v) {
					if(v.RNUM()-1 == self.misc.tempRecord().RNUM())
					return v;
				});
				self.viewPopup(nextEL[0]);
			}
		};
		
		
		self.cancelActivity = function(el) {
			self.clearTempRecord();
			$("#legacy_dialog_asd_dispatch").dialog('close');
		};

		function populateSearchCriteria() {
		
			var startTime = "T00:00:00.000";
			var endTime = "T23:59:59.999";
			
			var tempSRType = $('[name="ASD_ServiceRequestType"]').val();
			if(!U.isEmptyString(tempSRType) && tempSRType != 'Service Request Type') {
				var srt = cirm.refs.serviceCaseList[tempSRType];
				if(!U.isEmptyString(srt))
				{
					self.misc.query().type = "legacy:"+srt;
					self.misc.counter(self.misc.counter() + 1);
				}
				else
				{
					alertDialogASD("Please select a valid Service Request Type");
					return false;
				}
			}
			else
				self.misc.query().type = "legacy:ServiceCase";
			
			if(!U.isEmptyString(self.dispatchSC.hasStatus().iri())) {
				self.misc.query()["legacy:hasStatus"] = {"iri":self.dispatchSC.hasStatus().iri(), "type":"legacy:Status"};
				self.misc.counter(self.misc.counter() + 1);
			}
			
			if(!U.isEmptyString(self.dispatchSC.zip())) {
				if(self.misc.query().atAddress === undefined)
					self.misc.query().atAddress = {"type":"Street_Address"};
				self.misc.query().atAddress.Zip_Code = self.dispatchSC.zip();
				self.misc.counter(self.misc.counter() + 1);
			}
			
			if(!U.isEmptyString(self.dispatchSC.hasActivity().iri())) {
				if(self.misc.query()["legacy:hasServiceActivity"] === undefined)
					self.misc.query()["legacy:hasServiceActivity"] = {"type":"legacy:ServiceActivity"};
				self.misc.query()["legacy:hasServiceActivity"]["legacy:hasActivity"] = 
					{"iri":self.dispatchSC.hasActivity().iri()};
				//self.misc.query()["legacy:hasActivity"] = {"iri":self.dispatchSC.hasActivity().iri()};
				self.misc.counter(self.misc.counter() + 1);
			}

			if(!U.isEmptyString(self.dispatchSC.hasAllowableOutcome().iri())) {
				if(self.misc.query()["legacy:hasServiceActivity"] === undefined)
					self.misc.query()["legacy:hasServiceActivity"] = {"type":"legacy:ServiceActivity"};
				 self.misc.query()["legacy:hasServiceActivity"]["legacy:hasOutcome"] =
					{"iri":self.dispatchSC.hasAllowableOutcome().iri()};
				 //self.misc.query()["legacy:hasOutcome"] = {"iri":self.dispatchSC.hasAllowableOutcome().iri()};
				self.misc.counter(self.misc.counter() + 1);
			}
			
			if(!U.isEmptyString(self.dispatchSC.assignedTo())) {
				if(self.misc.query()["legacy:hasServiceActivity"] === undefined)
					self.misc.query()["legacy:hasServiceActivity"] = {"type":"legacy:ServiceActivity"};
				 self.misc.query()["legacy:hasServiceActivity"]["legacy:isAssignedTo"] = self.dispatchSC.assignedTo();
				 //self.misc.query()["legacy:isAssignedTo"] = self.dispatchSC.assignedTo();
				self.misc.counter(self.misc.counter() + 1);
			}

			if(!U.isEmptyString(self.dispatchSC.dateRange())) {
				var dateRangeItem = $.map(self.allDateRanges(), function(v) {
					if(v.iri == self.dispatchSC.dateRange())
						return v.iri;
				});

				if(!U.isEmptyString(self.dispatchSC.fromDate())) 
				{
					if(Date.parse(self.dispatchSC.fromDate()) == null)
					{
						alertDialogASD("Invalid From Date");
						return false;
					}
					else
					{
						self.dispatchSC.fromDate(
							Date.parse(self.dispatchSC.fromDate()).toString('MM/dd/yyyy')
						);	
					}
				}
					
				if(!U.isEmptyString(self.dispatchSC.toDate())) 
				{
					if(Date.parse(self.dispatchSC.toDate()) == null)
					{
						alertDialogASD("Invalid To Date");
						return false;
					}
					else
					{
						self.dispatchSC.toDate(
							Date.parse(self.dispatchSC.toDate()).toString('MM/dd/yyyy')
						);	
					}
				}

				if(!U.isEmptyString(self.dispatchSC.fromDate()) && !U.isEmptyString(self.dispatchSC.toDate())) 
				{
					var q = "between("+
							new Date(self.dispatchSC.fromDate()).format('yyyy-mm-dd')+startTime
							+","+ new Date(self.dispatchSC.toDate()).format('yyyy-mm-dd')+endTime
							+")";
					if(dateRangeItem == 'hasDateCreated') {
						self.misc.query()[dateRangeItem] = q; 
					}
					else {
						if(self.misc.query()["legacy:hasServiceActivity"] === undefined)
							self.misc.query()["legacy:hasServiceActivity"] = {"type":"legacy:ServiceActivity"};
						if(dateRangeItem == 'hasActivityDateCreated')
							self.misc.query()["legacy:hasServiceActivity"].hasDateCreated = q;
							//self.misc.query()[dateRangeItem] = q;
						else
							self.misc.query()["legacy:hasServiceActivity"]["legacy:"+dateRangeItem] = q;
							//self.misc.query()["legacy:"+dateRangeItem] = q;
					}
					self.misc.counter(self.misc.counter() + 1);
				}

				if(!U.isEmptyString(self.dispatchSC.fromDate()) && U.isEmptyString(self.dispatchSC.toDate())) 
				{
					var q = ">="+ new Date(self.dispatchSC.fromDate()).format('yyyy-mm-dd')+startTime;
					if(dateRangeItem == 'hasDateCreated') {
						self.misc.query().hasDateCreated = q; 
					}
					else {
						if(self.misc.query()["legacy:hasServiceActivity"] === undefined)
							self.misc.query()["legacy:hasServiceActivity"] = {"type":"legacy:ServiceActivity"};
						if(dateRangeItem == 'hasActivityDateCreated')
							self.misc.query()["legacy:hasServiceActivity"].hasDateCreated = q;
							//self.misc.query()[dateRangeItem] = q;
						else
							self.misc.query()["legacy:hasServiceActivity"]["legacy:"+dateRangeItem] = q;
							//self.misc.query()["legacy:"+dateRangeItem] = q;
					}
					self.misc.counter(self.misc.counter() + 1);
				}

			}
		}

		self.getDispatchInfo = function(el, event) {
			//self.dispatchResult({"records":[]});
			self.dispatchResult.records([]);
			var success = true;
			clearTimer(true);
			if(el) {
				resetMisc();
				success = populateSearchCriteria();
			}
			if(success == false)
				return;

			if(self.misc.counter() == 0) {
				alertDialogASD("Please select atleast one search criteria");
				return;
			}	
			else {
				self.misc.query().caseSensitive = false;
				//self.misc.query().currentPage = self.misc.currentPage();
				//self.misc.query().itemsPerPage = self.misc.itemsPerPage();
				var sortByField = self.misc.sortDetails().sortBy();
				var sortDirection = self.misc.sortDetails().sortDirection();
				if(sortByField == 'fullAddress')
				{
					if(self.misc.query().atAddress === undefined)
						self.misc.query().atAddress = {"type":"Street_Address"};
					self.misc.query().atAddress.sortBy = sortByField;
					self.misc.query().atAddress.sortDirection = sortDirection;
					delete self.misc.query().sortBy;
					delete self.misc.query().sortDirection;
					if(self.misc.query()["legacy:hasServiceActivity"]) {
						delete self.misc.query()["legacy:hasServiceActivity"].sortBy;
						delete self.misc.query()["legacy:hasServiceActivity"].sortDirection; 
					}
				}
				else if(sortByField == 'hasActivity' || sortByField == 'isAssignedTo' || sortByField == 'hasOutcome')
				{
					if(self.misc.query()["legacy:hasServiceActivity"] === undefined)
						self.misc.query()["legacy:hasServiceActivity"] = {"type":"legacy:ServiceActivity"};
					self.misc.query()["legacy:hasServiceActivity"].sortBy = "legacy:"+sortByField;
					self.misc.query()["legacy:hasServiceActivity"].sortDirection = sortDirection;
					delete self.misc.query().sortBy;
					delete self.misc.query().sortDirection;
					if(self.misc.query().atAddress) {
						delete self.misc.query().atAddress.sortBy;
						delete self.misc.query().atAddress.sortDirection; 
					}
				}
				else if(sortByField == 'hasPriority')
				{
					self.misc.query().sortBy = 'legacy:'+sortByField;
					self.misc.query().sortDirection = sortDirection;
					if(self.misc.query().atAddress) {
						delete self.misc.query().atAddress.sortBy;
						delete self.misc.query().atAddress.sortDirection; 
					}
					if(self.misc.query()["legacy:hasServiceActivity"]) {
						delete self.misc.query()["legacy:hasServiceActivity"].sortBy;
						delete self.misc.query()["legacy:hasServiceActivity"].sortDirection; 
					}
				}
				else
				{
					self.misc.query().sortBy = sortByField;
					self.misc.query().sortDirection = sortDirection;
					if(self.misc.query().atAddress) {
						delete self.misc.query().atAddress.sortBy;
						delete self.misc.query().atAddress.sortDirection; 
					}
					if(self.misc.query()["legacy:hasServiceActivity"]) {
						delete self.misc.query()["legacy:hasServiceActivity"].sortBy;
						delete self.misc.query()["legacy:hasServiceActivity"].sortDirection; 
					}
				}
				
				console.log("query", self.misc.query());

				$("#legacy_dialog_asd_progress").dialog({height: 140, modal: true});
				cirm.top.async().postObject("/asd/dispatch", self.misc.query(),  function(result) {
					console.log(ko.toJS(result));
					if(result.ok == true) {
						//To be hidden array
						var hideSRList = [];
						if(result.records) {
							$.each(result.records, function(i, v) {
								if(!U.isEmptyString(v.OUTCOME)) {
									$.each(cirm.refs.serviceCases[v.SR_TYPE].hasActivity, function(j, act) {
										if(act.iri == v.ACTIVITY) {
											$.each(act.hasAllowableOutcome, function(k, o) {
												if(o.iri == v.OUTCOME) {
													if(o.isHidden && o.isHidden == 'true')
														hideSRList.push(v.SR_ID);
													return false;
												}
											});
										}
									});
								}
							});
							var filteredRecords = $.grep(result.records, function(v,i) {
								if($.inArray(v.SR_ID, hideSRList) == -1)
									return true;
								else
									return false;
							});
							$("#legacy_dialog_asd_progress").dialog('close');
							if(filteredRecords.length == 0) {
								alertDialogASD("No results found");
							}
							else if(filteredRecords.length > 0) {
								$.each(result.records, function(i, record) {
									U.visit(record, U.makeObservableTrimmed);
								});
								self.dispatchResult.records(filteredRecords);
								if(self.isTimerOn())
									clearTimer(true);
								self.startCountDown();
							}
						}
					}
					else if(result.ok == false)
					{
						$("#legacy_dialog_asd_progress").dialog('close');
						alertDialogASD(result.error);
					}
				});
			}
		};

		self.exportData = function(type) {
			$("#pdf_report_dialog_alert").dialog({ height: 150, width: 500, modal: true, buttons: {
				"Continue" : function() {
					$("#pdf_report_dialog_alert").dialog('close');
						var sc = ko.toJS(self.misc.query);
						sc.itemsPerPage = 1000;
						sc.currentPage = 1;
						U.download("/asd/dispatchPDF", {formData:JSON.stringify(sc)}, true);
				},
				"Close": function() {
				  	$("#pdf_report_dialog_alert").dialog('close');
				}
			  } 
			});
		};

		self.clearDispatch = function() {
			clearTimer(true);
			self.dispatchSC.type("");
			self.dispatchSC.assignedTo("");
			self.dispatchSC.toDate("");
			self.dispatchSC.zip("");
			self.dispatchSC.hasActivity().iri(undefined);
			self.dispatchSC.hasAllowableOutcome().iri(undefined);
			self.applyDefaultDispatchValues();
			//self.dispatchSC.fromDate("");
			//self.dispatchSC.dateRange(undefined);
			//self.dispatchSC.hasStatus().iri(undefined);
			self.dispatchResult.records([]);
		};
		
		return self;
	}
	
	function asd() {
		var self ={};
		self.dom = $.tmpl($('#asdTemplate', legacyTemplates));
		self.model = getASDModel(self.dom);
        self.embed = function(parent) {
            ko.applyBindings(self.model, self.dom[0]);
        	$(parent).append(self.dom);
        	return self;
        };
		return self;
	}
	
	// Start Advanced Searchmodel ------------------------------------------------------------------------------------------

	function getAdvancedSearchModel(dom, addressModel) {
		var self = {};
		
		window.advancedSearch = self;
		
		self.dom = dom;
		self.address = ko.observable(undefined);//addressModel;
		self.addresses = ko.observableArray([]);

		self.searchCriteria = {department: "", type:"", srID:"", inputBy:"", modifiedBy:"",  
				serviceQuestion:"", 
				serviceQuestionAnswerTermString:"", //for CHAR, PHONENUM
				serviceQuestionAnswerTermChoice:"", //for CHARLIST, CHARMULT, CHAROPT
				serviceQuestionAnswerTermDateStart:"", // for DATE
				serviceQuestionAnswerTermDateEnd:"", // for DATE
				serviceQuestionAnswerTermNumberStart:"", // for NUMBER 
				serviceQuestionAnswerTermNumberEnd:"", // value dependent on hasDataType of ServiceField
				// TIME -> not currently supported
				createdStartDate:"", "createdEndDate":"", "updatedStartDate":"", "updatedEndDate":"", "overdueDate":"",
				"hasStatus":{"iri":undefined, "label":""}, "hasIntakeMethod":{"iri":undefined, "label":""},
				"hasPriority":{"iri":undefined, "label":""},"name":"", "lastName":"", 
				"atAddress":{
					"fullAddress":"", "municipality":"", 
					Street_Address_City:{"iri":"", "label":"" }, 
					"Street_Address_State":{"iri":undefined, "label":"" }, 
					Street_Unit_Number:"", Zip_Code:"", "type":"Street_Address"
				},
				"geoLayerAttr":{"iri":undefined, "label":""},
				"geoLayerAreaSearchValue":""
				};
		U.visit(self.searchCriteria, U.makeObservableTrimmed);
		
		self.hasSearchCriteria = function () {
		    return self.searchCriteria.type != "" || self.searchCriteria.srID != "" || self.searchCriteria.inputBy != "" || 
		    self.searchCriteria.modifiedBy != "" || self.searchCriteria.serviceQuestion != "" || 
		    self.searchCriteria.createStartDate != "" || self.searchCriteria.atAddress.fullAddress != "";
		};
		
		$(document).bind(InteractionEvents.AddressValidated, function(event, data) {
		     if (self.hasSearchCriteria())
		             return;
			if(true || data.addressData().parsedAddress.House != undefined && data.addressData().parsedAddress.House.length > 0)
        	{
				self.searchCriteria.atAddress().fullAddress(data.fullAddress());
				self.searchCriteria.atAddress().Street_Address_City().iri(data.getCity().iri);
				self.searchCriteria.atAddress().municipality(data.municipality());
				if(data.addressData().propertyInfo.parcelInfoCondoUnit != undefined && data.addressData().propertyInfo.parcelInfoCondoUnit.length > 0)
					self.searchCriteria.atAddress().Street_Unit_Number($.trim(data.addressData().propertyInfo.parcelInfoCondoUnit));
				self.searchCriteria.atAddress().Zip_Code(data.zip());
			}
			
		});
		
		self.result = ko.observable({"record":[], "totalRecordCount":0});
		
		// ---------------------------------------------------------------------------------------------------
		// SEARCH BY JURISDICTION / DEPARTMENT / DIVISION START
		//
        self.tempJurDeptDiv = {}; //group iri to group, group containing array of provided case iris
		self.mapJurDeptDiv = []; //group array containing cases, levels matter to determine jur dept or div, higher levels contain lower level cases.

		self.addSRToGroupMapEntry = function(srtype_iri, jurDeptDiv_iri) {
			var jurOrDeptOrDiv = self.tempJurDeptDiv[jurDeptDiv_iri];
			if ($.inArray(srtype_iri, jurOrDeptOrDiv.cases) == -1) {
				jurOrDeptOrDiv.cases.push(srtype_iri);
				jurOrDeptOrDiv.count++;
			}
		};
		
		//ensures one group entry level 0 = jur, 1 = dept, 2 = div
		self.ensureGroupMapEntry = function(iri, name, level, parentAgencyIRI) {
			var jurOrDeptOrDiv = self.tempJurDeptDiv[iri];
			if (!jurOrDeptOrDiv) {
				//hilpold We'll later sort by JurisdicitonIRI >> Department.Name >> Division.Name
				//so we keep track of the information needed here.
				jurOrDeptOrDiv = {
					iri: iri,
					name: name,
					displayName: name,
					count: 0,
					level: level, 
					parentAgencyIRI: parentAgencyIRI, //only NOT isDepartment this is the department
													  // if isDepartmetn, this is the Jurisdicition
					cases: []
				};
				self.tempJurDeptDiv[iri] = jurOrDeptOrDiv;
				//Ensure parent agencies resolved
			} 
			
		};

        self.getParentAgencyIRI = function(groupInd) {
        	var result = undefined;
        	if (groupInd.hasParentAgency) {
        		result = (groupInd.hasParentAgency.iri)? groupInd.hasParentAgency.iri : groupInd.hasParentAgency; 
        	} else if (groupInd.Department){
        		result = (groupInd.Department.iri)? groupInd.Department.iri : groupInd.Department;
        	} // else undefined 
        	return result;
        };        

        // Recursivly ensures that all parent groups are contained in self.tempJurDeptDiv
        self.ensureAllParentAgencies = function(group) {
    		if (group.parentAgencyIRI) {
        		var parentAgency = self.tempJurDeptDiv[group.parentAgencyIRI];
        		if (!parentAgency) 
        		{
        			//load from server, set name and parentAgency
        			var parentInd;
        			var attempts = 3;
        			do {
        				parentInd = cirm.top.get('/individuals/' + encodeURIComponent(group.parentAgencyIRI));        				
        				attempts --;
        			} while (attempts > 0 && !parentInd.iri)        			
        			if (parentInd.iri) {
	        			var name = parentInd.Name? parentInd.Name : parentInd.label;
	        			var parentParentIRI = self.getParentAgencyIRI(parentInd);
	        			self.ensureGroupMapEntry(parentInd.iri, name, -1, parentParentIRI);
	        			parentAgency = self.tempJurDeptDiv[parentInd.iri];
	        		}	
        		}
				//Recursivly ensure till top level
        		if (parentAgency.parentAgencyIRI) {
        			self.ensureAllParentAgencies(parentAgency);
        		}
        	}    		
        };  
        try {
	        //1. Add all SRs to a group or Other, level unknown -1
	        $.each(cirm.refs.serviceCases, function(i,v) {
	        	if(v.providedBy && v.providedBy.iri ) {
	        		var name = v.providedBy.Name? v.providedBy.Name : v.providedBy.label;
	    			self.ensureGroupMapEntry(v.providedBy.iri, name, -1, self.getParentAgencyIRI(v.providedBy));
	    			self.addSRToGroupMapEntry(v.iri, v.providedBy.iri);
	        	} else {
	        		//Add to Other group on Jurisdiction/top level
	        		self.ensureGroupMapEntry('Other', 'Other', 0, undefined);
			        self.addSRToGroupMapEntry(v.iri, 'Other', 'Other');
	        	}        	
	        });
	
	 
	        //2. Ensure all parents recursively in map, all cases added to parents and levels set
	        $.each(self.tempJurDeptDiv, function(i,v) {
	        	self.ensureAllParentAgencies(v);
	   			// Add all group cases to parent
	        	var curGroup = v;
	        	var groupPath = [];
	        	groupPath.push(curGroup);
	        	while (curGroup && curGroup.parentAgencyIRI) {
					$.each(curGroup.cases, function(i,acase) {
		    			self.addSRToGroupMapEntry(acase, curGroup.parentAgencyIRI);
					});
					curGroup = self.tempJurDeptDiv[curGroup.parentAgencyIRI];
	            	if (curGroup) groupPath.push(curGroup);
				};
		        $.each(groupPath, function(i,v) {
		        	v.level = groupPath.length - i - 1;
		        });	
	        });
	   		
	        
	        //3. Set displayname and make array
			$.each(self.tempJurDeptDiv, function(i,v) {
				//Set displayname to current count, use dash for non departments
				var prefix = '';
				for (var i = 0; i < v.level; i++) prefix += '....';
				if (v.level > 0) prefix = ' ' + prefix + ' ';
				//'v.level + " "; //hilpold debug
				v.displayName = prefix + v.name; //DBG + ' (' + v.count + ')'; 			
				self.mapJurDeptDiv.push(v);
			});
		} catch (error) 
		{
			console.warn("Error creating Search by Department structure: " + error);
		}		
		
		//Sort function for jurisdicitions/departments/divisons mapJurDeptDiv array by JurisdictionIRI, department.name, division.name 
		function sortByJurisDepartmentDivision(a,b) {
			var levelA = a.level;
			var levelB = b.level;
			var compA = a;
			var compB = b;
			var pathA = [];
			var pathB = [];
			var pathLevel = levelA > levelB? levelA : levelB;
			do {
				if (pathLevel > levelA) {
					pathA.push(undefined);
				} else {
					pathA.push(compA);
					if (compA)
						compA = self.tempJurDeptDiv[compA.parentAgencyIRI];					
				}
				if (pathLevel > levelB) {
					pathB.push(undefined);
				} else {
					pathB.push(compB);
					if (compB)
						compB = self.tempJurDeptDiv[compB.parentAgencyIRI];					
				}
				pathLevel --;
			} while (pathLevel >= 0);
			var compIdx = pathA.length - 1;
			var result;
			//Compare highest index / toplevel parent first
			do {
				if (pathA[compIdx] && pathB[compIdx]) {
					result = pathA[compIdx].name < pathB[compIdx].name? -1 : pathA[compIdx].name > pathB[compIdx].name? +1 : 0;
				} else {
					//result = pathA[compIdx] && !pathB[compIdx]? -1 : !pathA[compIdx] && pathB[compIdx]? +1 : 0;
					//higher level comes first in list
					result = levelA < levelB? -1 : levelA > levelB? +1 : 0;
				}				
				compIdx --;
			} while (result == 0 && compIdx >= 0)
			return result;
		}
		
		
		function sortAlpha(a,b) { 
			   return (a.label > b.label)? 1 : -1;
		}
		
		//4. Sort dept/div by jur, dept, div
		try {
			self.allDepartments = self.mapJurDeptDiv.sort(sortByJurisDepartmentDivision);
		} catch (error) {
			console.warn("Error sorting Search by Department structure: " + error);
		}				
		
		delete self.tempJurDeptDiv;
		//
		// SEARCH BY JURISDICTION / DEPARTMENT / DIVISION END
		// ---------------------------------------------------------------------------------------------------
		
		self.allStatus = cirm.refs.caseStatuses.sort(sortAlpha);
		self.allIntake = cirm.refs.caseIntakeMethods.sort(sortAlpha);
		self.allPriority = cirm.refs.casePriorities.sort(sortAlpha);
		self.geoLayerAttribute = cirm.refs.GeoLayerAttributes.sort(sortAlpha);

		self.srQuestions = ko.observable(new Array());
		self.srQuestionChoiceValues = ko.observable(new Array());

		self.colorResultsRow = function(el) {
			return $.inArray(el, self.result().record)%2;
		};
		
		function alertDialogAdvSrch(msg) {
	    	$("#advSrch_dialog_alert")[0].innerText = msg;
			$("#advSrch_dialog_alert").dialog({ height: 150, width: 500, modal: true, buttons: {
				"Close" : function() { $("#advSrch_dialog_alert").dialog('close'); }
			 } 
			});
		};

		self.isPopulatedSRQuestions = ko.computed(function() {
			self.searchCriteria['serviceQuestion'](undefined);
			var tempSRType = self.searchCriteria['type']();
			if(!U.isEmptyString(tempSRType) && tempSRType != 'Service Request Type') {
				var srtypeShort = cirm.refs.serviceCaseList[tempSRType];
				var srtype = cirm.refs.serviceCases['http://www.miamidade.gov/cirm/legacy#'+srtypeShort];
				if(srtype)
				{
					self.srQuestions(getServiceQuestions(srtype));
					return true;
				}
			}
			self.srQuestions(["Select Question"]);
			return false;
		});

		self.isCorrectAnswerDivShown = ko.computed(function() {
			var selectedSRQuestionIRI = self.searchCriteria['serviceQuestion']();
			clearAnswerSearch();
			if(!U.isEmptyString(selectedSRQuestionIRI)) 
			{
				var selectedSRQuestion = findServiceQuestion(selectedSRQuestionIRI);
				var selectedSRQuestionDatatype = selectedSRQuestion['hasDataType'];
				
				if (selectedSRQuestionDatatype === "CHAR"
					|| selectedSRQuestionDatatype === "PHONENUM") 
				{
					showAnswerDiv("#advancedSearchAnswerStringDiv");
				} 
				else if (selectedSRQuestionDatatype === "NUMBER") 
				{
					showAnswerDiv("#advancedSearchAnswerNumberDiv");
				} 
				else if (selectedSRQuestionDatatype === "DATE") 
				{
					showAnswerDiv("#advancedSearchAnswerDateDiv");
					//using smartdate instead of: 
					//$("#ServiceQuestionAnswerSearchTermDate1").datepicker();
					//$("#ServiceQuestionAnswerSearchTermDate2").datepicker();
				} 
				else if (selectedSRQuestionDatatype === "CHARLIST"
					|| selectedSRQuestionDatatype === "CHARMULT"
						|| selectedSRQuestionDatatype === "CHAROPT") 
				{
					updateSrQuestionChoiceValues(selectedSRQuestionIRI);
					showAnswerDiv("#advancedSearchAnswerChoiceDiv");
				} 
				else 
				{
					//dataype of question not recognized.
					showAnswerDiv("#advancedSearchAnswerStringDiv");
				}				
			} 
			else 
			{
				showAnswerDiv("#advancedSearchAnswerStringDiv");
			}
		});
		
		function clearAnswerSearch() 
		{
			self.searchCriteria.serviceQuestionAnswerTermString(""); //for CHAR, PHONENUM
			self.searchCriteria.serviceQuestionAnswerTermChoice(""); //for CHARLIST, CHARMULT, CHAROPT
			self.searchCriteria.serviceQuestionAnswerTermDateStart(""); // for DATE
			self.searchCriteria.serviceQuestionAnswerTermDateEnd(""); // for DATE
			self.searchCriteria.serviceQuestionAnswerTermNumberStart(""); // for NUMBER 
			self.searchCriteria.serviceQuestionAnswerTermNumberEnd(""); // value dependent on hasDataType of ServiceField
		}
		
		function showAnswerDiv(id) {
			var advancedSearchAnswerDivIds = ["#advancedSearchAnswerStringDiv", "#advancedSearchAnswerChoiceDiv", "#advancedSearchAnswerDateDiv", "#advancedSearchAnswerNumberDiv"];
			var i; 
			for (i = 0; i < advancedSearchAnswerDivIds.length; i++) {
				if (advancedSearchAnswerDivIds[i] === id) {
					$(advancedSearchAnswerDivIds[i]).show();
				} else {
					$(advancedSearchAnswerDivIds[i]).hide();
				}
			}
		}
		
		function updateSrQuestionChoiceValues(selectedSRQuestionIRI) {
			//var selectedSRQuestionIRI = self.searchCriteria['serviceQuestion']();
			if(!U.isEmptyString(selectedSRQuestionIRI)) 
			{
				self.srQuestionChoiceValues(getServiceQuestionChoiceValues(selectedSRQuestionIRI));
			}
			else 
			{
				self.srQuestions([]);
			};
		}; 

		function getServiceQuestions(srType) {
			//Expects a full srType
			var questions = $.map(srType.hasServiceField, function(elem, idx) {
				if(!(elem.isDisabled
	    			|| (elem.hasBusinessCodes && elem.hasBusinessCodes.indexOf("DELETED") != -1)
	    		   ))
	    		{
	    			return elem;
	    		}
	    	});
	        //Sort the Questions 
	        questions.sort(function(a,b) {
	            return parseFloat(a.hasOrderBy) - parseFloat(b.hasOrderBy) } );
	        return questions;
		}

		/**
		 * Returns hasChoiceValue array for currently selected servicequestion.
		 * @the iri of the selected SRQuestions, expected in srQuestions.
		 */
		function findServiceQuestion(selectedSRQuestionIRI) {
			var allQuestions = self.srQuestions();
			var i;
			var curQ;
			for (i = 0; i < allQuestions.length; i++) {
				curQ = allQuestions[i];
				if (curQ['iri'] === selectedSRQuestionIRI) {
					return curQ;
				};
			}
			return null;
		}

		/**
		 * Returns hasChoiceValue array for currently selected servicequestion.
		 * @the iri of the selected SRQuestions, expected in srQuestions.
		 */
		function getServiceQuestionChoiceValues(selectedSRQuestionIRI) {
			var q = findServiceQuestion(selectedSRQuestionIRI);
			if(q !== null) {
				var choiceValues =  U.ensureArray(q['hasChoiceValueList']['hasChoiceValue']);
                return choiceValues.sort(function (a,b) {
               		if(a.hasOrderBy && b.hasOrderBy)
               			return parseFloat(a.hasOrderBy) - parseFloat(b.hasOrderBy);
               		else
               			return a.label.localeCompare(b.label);
                });

			}
		}
		
		self.exportData = function(type) {
			var sc = ko.toJS(self.misc.query);
			sc.itemsPerPage = 1000;
			sc.currentPage = 1;
			//var formInfo = {"searchCriteria":sc, "sortData":ko.toJS(self.misc.sortDetails)};
			if(type == 'srView')
			{
				$("#pdf_report_dialog_alert").dialog({ height: 150, width: 500, modal: true, buttons: {
					"Continue" : function() {
						$("#pdf_report_dialog_alert").dialog('close');
						delete sc.gisColumnName;
						U.download("/legacy/srView", {formData:JSON.stringify(sc)}, true);
					},
					"Close": function() {
					  	$("#pdf_report_dialog_alert").dialog('close');
					}
				  } 
				});
			}
			else
			{
				$("#export_results_dialog_alert").dialog({ height: 150, width: 500, modal: true, buttons: {
					"Continue" : function() {
						$("#export_results_dialog_alert").dialog('close');
						if(type == 'excel')
							U.download("/legacy/exportToExcel", {formData:JSON.stringify(sc)} );
						else if(type == 'pdf')
							U.download("/legacy/exportToPDF", {formData:JSON.stringify(sc)}, true);
					},
					"Close": function() {
					  	$("#export_results_dialog_alert").dialog('close');
					}
				  } 
				});
			}
		};

		self.showMoreFields = function(el, event) {
			if($("#advsearch_moreDetails").attr("style").indexOf("none") != -1)
				$("#advsearch_moreDetails").attr("style", "display:block");
			else
				$("#advsearch_moreDetails").attr("style", "display:none");
			if(event.currentTarget.innerText == 'SHOW Advanced Search Fields')
				event.currentTarget.innerText = 'HIDE Advanced Search Fields';
			else
				event.currentTarget.innerText = 'SHOW Advanced Search Fields';
		};
		
		self.buildCaseNumber = function(el) {
			if(U.isEmptyString(el.hasCaseNumber))
			{
				var year = Date.parse(el.hasDateCreated).getFullYear().toString();
				return year.substr(year.length - 2) + '-1' + U.addLeadingZeroes(el.boid, 7);
			}
			else
			{
				//TODO : temp fix, remove this after wiping out the DB 
				if(el.hasCaseNumber.indexOf("AC") != -1)
				{
					return el.hasCaseNumber.split('-')[0].split('AC20')[1] + "-" + el.hasCaseNumber.split('-')[1];
				}
				//end : temp fix
				else
					return el.hasCaseNumber;
			}
		};

		self.populateSH = function(el) {
			$("#populate_legacy_dialog_alert").dialog({ height: 150, width: 300, modal: true, buttons: {
				"View Activities" : function() {
					$(document).trigger(InteractionEvents.populateSH, [el.boid]);
					$("#populate_legacy_dialog_alert").dialog('close');
					$(document).trigger(InteractionEvents.showServiceActivities, []);
				},
				"View Report" : function() {
					$("#populate_legacy_dialog_alert").dialog('close');
					U.download("/legacy/printView", {boid:el.boid}, true);
				},
				"Close": function() {
				  	$("#populate_legacy_dialog_alert").dialog('close');
				}
			  } 
			});
		};
		
		self.isDisabledDepartment = ko.computed(function() {
			var tempSRType = self.searchCriteria['type']();
			if(!U.isEmptyString(tempSRType) && tempSRType != 'Service Request Type')
				return true;
			else
				return false;
		});

		self.isDisabledSrType = ko.computed(function() {
			//console.log("self.searchCriteria.department()", self.searchCriteria.department());
			if(!U.isEmptyString(self.searchCriteria.department()))
				return true;
			else
				return false;
		});

		self.isDisabledServiceQuestion = ko.computed(function() {
			var tempSRType = self.searchCriteria['type']();
			if(!U.isEmptyString(tempSRType) && tempSRType != 'Service Request Type') {
				var srtypeShort = cirm.refs.serviceCaseList[tempSRType];
				var srtype = cirm.refs.serviceCases['http://www.miamidade.gov/cirm/legacy#'+srtypeShort];
				return (srtype === undefined);
			}
			return true;
		});
		
		self.isDisabledServiceAnswer = ko.computed(function() {
			var selectedSQ = self.searchCriteria['serviceQuestion']();
			var disabled = U.isEmptyString(selectedSQ) || selectedSQ === 'Select Question';
			return disabled;
		});
		
		self.isEnabledGeoLayerArea = function() {
			var layerArea = self.searchCriteria.geoLayerAttr().iri(); 
			if(layerArea === undefined)
				self.searchCriteria.geoLayerAreaSearchValue("");
			return layerArea === undefined ? false : true;
		};
		
		self.disabledEndDate = function(startDate, endDate) { 
			if(Date.parse(self.searchCriteria[startDate]()) != null)
				return false;
			else {
				self.searchCriteria[endDate]("");
				return true;
			}
		};
		
		self.disabledEndNumber = function(startNumber, endNumber) { 
			if(U.isEmptyString(self.searchCriteria[startNumber]())) 
			{
				self.searchCriteria[endNumber]("");
				return true;
			}
			else 
			{
				return false;
			}
		};
		self.misc = {
			"currentPage":1, "itemsPerPage":15, "counter":0, "query":{}, 
			"sortDetails":{"sortBy":"hasCaseNumber", "sortDirection":"desc"},
			"metaData":{"columns":8, "newColumn":""}
		};
		U.visit(self.misc, U.makeObservableTrimmed);

		self.fetchFirst = function(el) {
			if(self.misc.currentPage() == 1)
				return true;
			else
			{
				self.misc.currentPage(1);
				self.search();
			}
		};
		self.fetchPrev = function(el) {
			if(self.misc.currentPage() == 1)
				return true;
			else
			{
				self.misc.currentPage(self.misc.currentPage() - 1);
				self.search();
			}
		};
		
		self.fetchNext = function(el) {
			if(self.misc.currentPage() == self.fetchLastPage())
				return true;
			else
			{
				self.misc.currentPage(self.misc.currentPage() + 1);
				self.search();
			}
		};
		
		self.fetchLast = function(el) {
			var lastPage = self.fetchLastPage();
			if(self.misc.currentPage() == lastPage)
				return true;
			else
			{
				self.misc.currentPage(lastPage);
				self.search();
			}
		};
		
		self.fetchLastPage = function() {
			var count = self.result().totalRecordCount;
			var maxPP = self.misc.itemsPerPage();
			var lastPage = ( (count - (count % maxPP) ) / maxPP );
			return (count % maxPP == 0) ? lastPage : lastPage+1;
		}
		
    	self.sortData = function(filterField) {
    		var sortDetails = self.misc.sortDetails();
			if(sortDetails.sortBy() == filterField)
				sortDetails.sortDirection() == "asc" ? sortDetails.sortDirection("desc") : sortDetails.sortDirection("asc");
			else
			{
				sortDetails.sortBy(filterField);
				sortDetails.sortDirection("desc");
			}
    		//self.result({"record":[], "totalRecordCount":0});
    		self.misc.currentPage(1);
			self.search();
    	};

		self.makeCaseNumber = function() {
			var userFriendlyPattern = /[A-Z]{2}[0-9]{3,6}.{1}[\d]{8}/;
			if(self.searchCriteria.srID().match(userFriendlyPattern))
				return self.searchCriteria.srID();
			var pre = self.searchCriteria.srID().substr(0,2);
			var srID = self.searchCriteria.srID().substr(2,self.searchCriteria.srID().length);
			var friendlyFormat = pre + U.getFullYear() + "-1" + U.addLeadingZeroes(srID, 7);
			if(friendlyFormat.match(userFriendlyPattern))
				return friendlyFormat;
			else
				return 'false';
		};
		
		self.checkDateOnEnter = function(data, event) {
    		if(event.keyCode == 9 || event.keyCode == 13) {
    			self.checkDate(data, event);
    		}
    		return true;
    	};
    	
    	self.checkDate = function(data, event) {
			$("#sh_dialog_address_search").dialog({ height: 140, modal: true });
			$("#sh_dialog_address_search").dialog('close');
    		var inputValue = $(event.currentTarget).val();
    		if(Date.parse(inputValue) != null) {
    			self.searchCriteria[event.currentTarget.id](Date.parse(inputValue).toString('MM/dd/yyyy'));
    		}
    		event.currentTarget.focus();
			patchPlaceholdersforIE();
    	};

    	self.getFilteredSCList = function() {
			return function(s, back) {
				var matcher = new RegExp("\\b" + $.ui.autocomplete.escapeRegex(s.term), "i" );
				back($.grep(cirm.refs.autoCompleteServiceCaseList, function(value) {
					return matcher.test( value.label || value.value || value );
			})) };
    	};
    	
		function reset() {
			self.misc.currentPage(1);
			self.misc.sortDetails().sortBy("hasCaseNumber");
			self.misc.sortDetails().sortDirection("desc");
			self.misc.counter(0);
			self.misc.query({});
		};
		
		function populateSearchCriteria() {
		
			var startTime = "T00:00:00.000";
			var endTime = "T23:59:59.999";

			var tempSRType = $('[name="ServiceRequestType"]').val();
			
			if(!U.isEmptyString(self.searchCriteria.department())) {
				console.log(self.searchCriteria.department());
				self.misc.query().type = self.searchCriteria.department();
				self.misc.counter(self.misc.counter() + 1);
			}
			else if(!U.isEmptyString(tempSRType) && tempSRType != 'Service Request Type') {
				var srt = cirm.refs.serviceCaseList[tempSRType];
				if(!U.isEmptyString(srt))
				{
					self.misc.query().type = "legacy:"+srt;
					self.misc.counter(self.misc.counter() + 1);
				}
				else
				{
					alertDialogAdvSrch("Please select a valid Service Request Type");
					return false;
				}
			}
			else
				self.misc.query().type = "legacy:ServiceCase";

			if(!U.isEmptyString(self.searchCriteria.srID())) {
				if(self.searchCriteria.srID().indexOf("-") != -1)
					self.misc.query()["legacy:hasCaseNumber"] = self.searchCriteria.srID();
				else {
					var yr = U.getFullYearAsString();
					self.misc.query()["legacy:hasCaseNumber"] = yr.substr(yr.length - 2) + "-1" + U.addLeadingZeroes(self.searchCriteria.srID(), 7);
				}
				self.misc.counter(self.misc.counter() + 1);
			}
/*			
			if(!U.isEmptyString(self.searchCriteria.srID())) {
				var alphabetPattern = /[A-Za-z]{2}/;
				if(self.searchCriteria.srID().match(alphabetPattern))
				{
					var friendlyFormat = self.makeCaseNumber(self.searchCriteria.srID());
					if(friendlyFormat == 'false' || friendlyFormat.indexOf('AC') == -1)
					{
						var formatExample = "\n .SR_ID : 13-00000434 or 434";
						alertDialogAdvSrch("Unrecognizable SR ID Format. Please use one of these formats :" + formatExample);
						return false;
					}
					else
						self.misc.query()["legacy:hasCaseNumber"] = friendlyFormat;
						//self.misc.query().hasUserFriendlyID = friendlyFormat;
				}
				else if(self.searchCriteria.srID().indexOf("-") != -1)
					self.misc.query()["legacy:hasCaseNumber"] = self.searchCriteria.srID();
					//self.misc.query()["legacy:hasLegacyId"] = self.searchCriteria.srID();
				else
					self.misc.query().boid = self.searchCriteria.srID();
				self.misc.counter(self.misc.counter() + 1);
			}
*/
			if(!U.isEmptyString(self.searchCriteria.hasStatus().iri())) {
				self.misc.query()["legacy:hasStatus"] = {"iri":self.searchCriteria.hasStatus().iri(), "type":"legacy:Status"};
				self.misc.counter(self.misc.counter() + 1);
			}

			if(!U.isEmptyString(self.searchCriteria.hasIntakeMethod().iri())) {
				self.misc.query()["legacy:hasIntakeMethod"] = {"iri":self.searchCriteria.hasIntakeMethod().iri(), "type":"legacy:IntakeMethod"};
				self.misc.counter(self.misc.counter() + 1);
			}
			
			if(!U.isEmptyString(self.searchCriteria.hasPriority().iri())) {
				self.misc.query()["legacy:hasPriority"] = {"iri":self.searchCriteria.hasPriority().iri(), "type":"legacy:Priority"};
				self.misc.counter(self.misc.counter() + 1);
			}
			
			// ServiceQuestion criteria
			// Get type
			if (!(U.isEmptyString(self.searchCriteria.serviceQuestion())
					|| self.searchCriteria.serviceQuestion() == "Select Question"
				)) {
				//Find by IRI
				//required is: type, valid string > 3 chars, both bounds on date or number
				var serviceFieldIRI = self.searchCriteria.serviceQuestion();
				var selectedSRQuestion = findServiceQuestion(serviceFieldIRI);
				var selectedSRQuestionDatatype = selectedSRQuestion['hasDataType'];
				var answerValueType ;
				var answerValueLiteral;
				var answerValueObjectIRI;
				var validAnswerQuery = false;
				var isAnswerObjectQuery = false;
				var msg = "";
				if (selectedSRQuestionDatatype === "CHAR" || selectedSRQuestionDatatype === "PHONENUM") 
				{
					var searchTerm = self.searchCriteria.serviceQuestionAnswerTermString();
					validAnswerQuery = (searchTerm.length >= 1);
					if (validAnswerQuery) {
						answerValueLiteral = "like(\"%" + searchTerm + "%\")";
						answerValueType = "http://www.w3.org/2001/XMLSchema#string";
					} else {
						msg = "The search service question answer query string has to be at least 1 characters long. Was: " + searchTerm;
						alertDialogAdvSrch(msg);
						return false;
					}
				} 
				else if (selectedSRQuestionDatatype === "NUMBER") 
				{
					var from = self.searchCriteria.serviceQuestionAnswerTermNumberStart();
					var to = self.searchCriteria.serviceQuestionAnswerTermNumberEnd();
					from = parseInt(from);
					to = parseInt(to);
					validAnswerQuery = (typeof from == typeof 1 && typeof to == typeof 1 && from <= to);
					if (validAnswerQuery) 
					{
						answerValueLiteral  = "between(" +	from + "," + to +")";
						answerValueType = "http://www.w3.org/2001/XMLSchema#integer";
					} else 
					{
						msg = "The search service question answer query numbers have to be \r\n both set, be integers and start has to be smaller  \r\n\ or equal to end.";
						alertDialogAdvSrch(msg);
						return false;
					}					
				} 
				else if (selectedSRQuestionDatatype === "DATE") 
				{
					var from = self.searchCriteria.serviceQuestionAnswerTermDateStart();
					var to = self.searchCriteria.serviceQuestionAnswerTermDateEnd();
					from = new Date(from);
					to = new Date(to);
					validAnswerQuery = (from && to && from <= to && typeof from == typeof to && typeof to == typeof new Date());
					if (validAnswerQuery) 
					{
						from = from.format('yyyy-mm-dd') + startTime;
						to = to.format('yyyy-mm-dd') + endTime;
						answerValueLiteral = "between("+ from + "," + to + ")";					
						answerValueType = "http://www.w3.org/2001/XMLSchema#dateTimeStamp";
					} 
					else 
					{
						msg = "The search service question answer query dates have to be \r\n both set, " +
								"format mm/dd/yyyy and start has to be smaller  \r\n\ or equal to end.";
						alertDialogAdvSrch(msg);
						return false;
					}
				} 
				else if (selectedSRQuestionDatatype === "CHARLIST"
						|| selectedSRQuestionDatatype === "CHARMULT"
						|| selectedSRQuestionDatatype === "CHAROPT") 
				{
					var selectedIRI = self.searchCriteria.serviceQuestionAnswerTermChoice();
					validAnswerQuery = !(U.isEmptyString(selectedIRI) || selectedIRI == "Select Answer");
					if (validAnswerQuery) 
					{
						isAnswerObjectQuery = (selectedIRI.indexOf("http://", 0) >= 0); 
						if (isAnswerObjectQuery) 
						{
							answerValueObjectIRI = selectedIRI;
						} 
						else
						{
							answerValueLiteral = "=\"" + selectedIRI + "\"";
							answerValueType = "http://www.w3.org/2001/XMLSchema#string";
						}
					} 
					else 
					{
						msg = "The search service question answer was not selected from the dropdown list.";
						alertDialogAdvSrch(msg);
						return false;
					}
				} 
				else 
				{
					// validAnswerQuery remains false;
					answerValueType = null; 
					answerValueLiteral = null;
				}
				if (validAnswerQuery) 
				{
					if (isAnswerObjectQuery) 
					{
						self.misc.query()[serviceFieldIRI] = answerValueObjectIRI;
					}
					else 
					{
						self.misc.query()[serviceFieldIRI] = {literal: answerValueLiteral, datatype: answerValueType};
					}
					self.misc.counter(self.misc.counter() + 1);
				}
			}
			
			if(!U.isEmptyString(self.searchCriteria.createdStartDate()))
			{
				if(Date.parse(self.searchCriteria.createdStartDate()) == null)
				{
					alertDialogAdvSrch("Invalid Created Date (Start)");
					return false;
				}
				else
				{
					self.searchCriteria.createdStartDate(
						Date.parse(self.searchCriteria.createdStartDate()).toString('MM/dd/yyyy')
					);
				}
			}

			if(!U.isEmptyString(self.searchCriteria.createdEndDate()))
			{
				if(Date.parse(self.searchCriteria.createdEndDate()) == null)
				{
					alertDialogAdvSrch("Invalid Created Date (End)");
					return false;
				}
				else
				{
					self.searchCriteria.createdEndDate(
						Date.parse(self.searchCriteria.createdEndDate()).toString('MM/dd/yyyy')
					);
				}
			}

			if(!U.isEmptyString(self.searchCriteria.overdueDate()))
			{
				if(Date.parse(self.searchCriteria.overdueDate()) == null)
				{
					alertDialogAdvSrch("Invalid Over Due Date");
					return false;
				}
				else
				{
					self.searchCriteria.overdueDate(
						Date.parse(self.searchCriteria.overdueDate()).toString('MM/dd/yyyy')
					);
				}
			}

			if(!U.isEmptyString(self.searchCriteria.createdStartDate()) 
					&& !U.isEmptyString(self.searchCriteria.createdEndDate())) 
			{
				self.misc.query().hasDateCreated = "between("+
					new Date(self.searchCriteria.createdStartDate()).format('yyyy-mm-dd')+startTime
					+","+
					new Date(self.searchCriteria.createdEndDate()).format('yyyy-mm-dd')+endTime
					+")";
				self.misc.counter(self.misc.counter() + 1);
			}

			else if(!U.isEmptyString(self.searchCriteria.createdStartDate())
					&& U.isEmptyString(self.searchCriteria.createdEndDate())) {
				self.misc.query().hasDateCreated = ">="+new Date(self.searchCriteria.createdStartDate()).format('yyyy-mm-dd')+startTime;
				self.misc.counter(self.misc.counter() + 1);
			}

			if(!U.isEmptyString(self.searchCriteria.updatedStartDate())
					&& !U.isEmptyString(self.searchCriteria.updatedEndDate())) {
				self.misc.query().hasDateLastModified = "between("+
					new Date(self.searchCriteria.updatedStartDate()).format('yyyy-mm-dd')+startTime
					+","+
					new Date(self.searchCriteria.updatedEndDate()).format('yyyy-mm-dd')+endTime
					+")";
				self.misc.counter(self.misc.counter() + 1);
			}

			else if(!U.isEmptyString(self.searchCriteria.updatedStartDate())
					&& U.isEmptyString(self.searchCriteria.updatedEndDate())) {
				self.misc.query().hasDateLastModified = ">="+new Date(self.searchCriteria.updatedStartDate()).format('yyyy-mm-dd')+startTime;
				self.misc.counter(self.misc.counter() + 1);
			}

			if(!U.isEmptyString(self.searchCriteria.overdueDate()))
			{
				var dueDate = new Date(self.searchCriteria.overdueDate()).format('yyyy-mm-dd');
				self.misc.query()["legacy:hasDueDate"] = "<="+dueDate+endTime;
				self.misc.counter(self.misc.counter() + 1);
			}
			
			if(!U.isEmptyString(self.searchCriteria.inputBy())) {
				self.misc.query().isCreatedBy = self.searchCriteria.inputBy();
				self.misc.counter(self.misc.counter() + 1);
			}

			if(!U.isEmptyString(self.searchCriteria.modifiedBy())) {
				self.misc.query().isModifiedBy = self.searchCriteria.modifiedBy();
				self.misc.counter(self.misc.counter() + 1);
			}

			if(!U.isEmptyString(self.searchCriteria.atAddress().fullAddress()))
			{ 
				if(self.misc.query().atAddress === undefined)
					self.misc.query().atAddress = {"type":"Street_Address"};
				self.misc.query().atAddress.fullAddress = self.searchCriteria.atAddress().fullAddress();
				self.misc.counter(self.misc.counter() + 1);
			}
			if(!U.isEmptyString(self.searchCriteria.atAddress().Street_Unit_Number()))
			{ 
				if(self.misc.query().atAddress === undefined)
					self.misc.query().atAddress = {"type":"Street_Address"};
				self.misc.query().atAddress.Street_Unit_Number = self.searchCriteria.atAddress().Street_Unit_Number();
				self.misc.counter(self.misc.counter() + 1);
			}
			if(!U.isEmptyString(self.searchCriteria.atAddress().municipality()))
			{
				var city = answerhub.AddressModel().getCity(self.searchCriteria.atAddress().municipality());
				if(city != undefined && city.iri)
				{
					if(self.misc.query().atAddress === undefined)
						self.misc.query().atAddress = {"type":"Street_Address"};
					self.misc.query().atAddress.Street_Address_City = {"iri": city.iri};
					self.misc.counter(self.misc.counter() + 1);
				}
			}
			if(!U.isEmptyString(self.searchCriteria.atAddress().Zip_Code()))
			{ 
				if(self.misc.query().atAddress === undefined)
					self.misc.query().atAddress = {"type":"Street_Address"};
				self.misc.query().atAddress.Zip_Code = self.searchCriteria.atAddress().Zip_Code();
				self.misc.counter(self.misc.counter() + 1);
			}
			if(!U.isEmptyString(self.searchCriteria.name()))
			{
				if(self.misc.query()["legacy:hasServiceCaseActor"] === undefined)
					self.misc.query()["legacy:hasServiceCaseActor"] = {"type":"legacy:ServiceCaseActor"};
				self.misc.query()["legacy:hasServiceCaseActor"].Name = self.searchCriteria.name();
				self.misc.counter(self.misc.counter() + 1);
			}
			if(!U.isEmptyString(self.searchCriteria.lastName()))
			{
				if(self.misc.query()["legacy:hasServiceCaseActor"] === undefined)
					self.misc.query()["legacy:hasServiceCaseActor"] = {"type":"legacy:ServiceCaseActor"};
				self.misc.query()["legacy:hasServiceCaseActor"].LastName = self.searchCriteria.lastName();
				self.misc.counter(self.misc.counter() + 1);
			}
			if(!U.isEmptyString(self.searchCriteria.geoLayerAttr().iri()))
			{
				self.misc.query().hasGeoPropertySet = {"type":"GeoPropertySet"};
				var geoLayerArea = $.grep(self.geoLayerAttribute, function(v) { 
					if(v.iri == self.searchCriteria.geoLayerAttr().iri())
						return v.label
				});
				var geoLayerAreaLabel = geoLayerArea[0].label;
				var value = self.searchCriteria.geoLayerAreaSearchValue();
				value = U.isEmptyString(value) == false ? value : "isNotNull(\"\")";
				self.misc.query().hasGeoPropertySet[geoLayerAreaLabel] = value;
				//self.misc.query().hasGeoPropertySet[geoLayerAreaLabel] = self.searchCriteria.geoLayerAreaSearchValue();
				self.misc.metaData().columns(9);
				self.misc.metaData().newColumn(geoLayerAreaLabel);
				self.misc.counter(self.misc.counter() + 1);
			}
			else if(U.isEmptyString(self.searchCriteria.geoLayerAttr().iri()))
			{
				self.misc.metaData().columns(8);
				self.misc.metaData().newColumn("");
			}
		};
		
		self.search = function(el) {
			self.result({"record":[]});
			var success = true;
			if(el) {
				reset();
				success = populateSearchCriteria();
			}
			if(success == false)
				return;

			if(self.misc.counter() == 0) {
				alertDialogAdvSrch("Please select atleast one search criteria");
				return;
			}	
			else {
				self.misc.query().caseSensitive = false;
				self.misc.query().currentPage = self.misc.currentPage();
				self.misc.query().itemsPerPage = self.misc.itemsPerPage();
				if(!U.isEmptyString(self.misc.metaData().newColumn()))
					self.misc.query().gisColumnName = self.misc.metaData().newColumn();
	
				var sortByField = self.misc.sortDetails().sortBy();
				var sortDirection = self.misc.sortDetails().sortDirection();
				if(sortByField == "fullAddress" || sortByField == "Street_Address_City" || sortByField == "Zip_Code") {
					if(self.misc.query().atAddress === undefined)
						self.misc.query().atAddress = {"type":"Street_Address"};
					self.misc.query().atAddress.sortBy = sortByField;
					self.misc.query().atAddress.sortDirection = sortDirection; 
	
					delete self.misc.query().sortBy;
					delete self.misc.query().sortDirection;
					if(self.misc.query().hasGeoPropertySet) {
						delete self.misc.query().hasGeoPropertySet.sortBy;
						delete self.misc.query().hasGeoPropertySet.sortDirection; 
					}
				}
				else if(sortByField == "gisColumn") {
					self.misc.query().hasGeoPropertySet.sortBy = self.misc.metaData().newColumn();
					self.misc.query().hasGeoPropertySet.sortDirection = sortDirection; 
					delete self.misc.query().sortBy;
					delete self.misc.query().sortDirection;
					if(self.misc.query().atAddress) {
						delete self.misc.query().atAddress.sortBy;
						delete self.misc.query().atAddress.sortDirection; 
					}
				}
				else if(sortByField == "hasStatus" || sortByField == "hasCaseNumber") {
					self.misc.query().sortBy = "legacy:"+sortByField;
					self.misc.query().sortDirection = sortDirection;
				}
				else {
					self.misc.query().sortBy = sortByField;
					self.misc.query().sortDirection = sortDirection;
				}
				console.log("query", self.misc.query());
				console.log("criteria", ko.toJS(self.searchCriteria));
			
				$("#advSearch_dialog_progress").dialog({height: 140, modal: true});
				cirm.top.async().postObject("/legacy/advSearch", self.misc.query(),  function(result) {
					console.log("result", result);
					if(result.ok == true) {
						if(result.resultsArray.length == 0)
						{
							$("#advSearch_dialog_progress").dialog('close');
							if(self.result().record.length > 0)
							{
								self.misc.currentPage(self.misc.currentPage() - 1);
								alertDialogAdvSrch("No more records available");
							}
							else
							{
								self.result({"record":[], "totalRecordCount":0});
								alertDialogAdvSrch("No search results found");
							}
						}
						else if(result.resultsArray.length > 0) {
							var records = {"record":[], "totalRecordCount":0};
							self.result(records);
							records.record = result.resultsArray;
							records.totalRecordCount = result.totalRecords;

							/*
							if(records.totalRecordCount >= 100)
							{
						    	var msg = "Please note that trying to print " + records.totalRecordCount + " reports may take a lot of time";
						    	alertDialogAdvSrch(msg);
							}
							*/

							//Sort results if not sorted properly
							records.record.sort(function(a,b) {
								var sortBy = self.misc.sortDetails().sortBy();
								if(sortBy == 'Zip_Code')
									return a[sortBy] - b[sortBy];
								else
									return a[sortBy].localeCompare(b[sortBy]);
							});
							if(self.misc.sortDetails().sortDirection() == "desc")
								records.record.reverse();
							self.result(records);
							$("#advSearch_dialog_progress").dialog('close');
						}
					}
					else if(result.ok == false) {
						self.result({"record":[], "totalRecordCount":0});
						$("#advSearch_dialog_progress").dialog('close');
						var msg = "An error occured while searching. Please contact Tech Support with the following Information : "+result.error;
						alertDialogAdvSrch(msg);
					}
				});
			}
		};

    	self.validateOnTab = function(data, event) {
    	    if(event.keyCode == 9 || event.keyCode == 13) {
   	            self.searchAddress();
    	    }
    	    return true;
    	};

		self.gis = null;
		
		function initGis() {
			if(!MDCJSLIB.modules.gis.getPath())
			{
				var config = cirm.top.get("/individuals/predefined/configset");
				var fullUrl = config.GisConfig.hasUrl;
				var url = fullUrl.substring(0,fullUrl.lastIndexOf("/"));
				var path = fullUrl.substring(fullUrl.lastIndexOf("/"));
				MDCJSLIB.modules.gis.initConnection(url, path);
				self.gis = MDCJSLIB.modules.gis;
			}
			else
				self.gis = MDCJSLIB.modules.gis;
		}
		
		self.searchAddress = function() {
			$("#sh_dialog_address_search").dialog({ height: 140, modal: true });
			//$("#sh_dialog_address_search").dialog('close');
			//$(document).trigger(InteractionEvents.SearchAddress, [self.searchCriteria.atAddress()]);
			if(self.gis == null)
				initGis();
			self.gis.getAddressCandidates(
				self.searchCriteria.atAddress().fullAddress(),
				self.searchCriteria.atAddress().Zip_Code(),
				self.searchCriteria.atAddress().municipality(),
				function(candidates) {
					if(candidates == undefined || candidates.length == 0)
					{
						$("#sh_dialog_address_search").dialog('close');
						alertDialogAdvSrch("No results found. Please enter a valid address");
					}
					if(candidates.length == 1)
					{
						setAddressFields(candidates[0]);
						$("#sh_dialog_address_search").dialog('close');
					}
					if(candidates.length > 1)
					{
						$("#sh_dialog_address_search").dialog('close');
						self.addresses(candidates);
						$( "#bs_dialog_addresses_resolve" ).dialog({ height: 200, modal: true });
					}
				},
				function(msg, req, status) {
					console.log("error", msg, req, status);
					$("#sh_dialog_address_search").dialog('close');
				}
			);
		};
		
		self.resolveAddress = function(candidate) {
			setAddressFields(candidate);
			$( "#bs_dialog_addresses_resolve" ).dialog('close');
			self.addresses([]);
		};
		
		function setAddressFields(candidate) {
			self.address(candidate);
			self.searchCriteria.atAddress().fullAddress(candidate.address.split(',')[0]);
			self.searchCriteria.atAddress().municipality(candidate.municipality);
			self.searchCriteria.atAddress().Zip_Code(candidate.address.split(',')[1]);
		}

		self.clearLocation = function() {
    		//$(document).trigger(InteractionEvents.AddressClear, []);
    		$(document).trigger(InteractionEvents.AdvSrchAddrClear, []);
		}

		self.launchMap = function() {
			$(document).trigger(InteractionEvents.LaunchMap, [self.address]);
		};

		$(document).bind(InteractionEvents.AdvSrchAddrClear, function(event) {
			self.clearAddress();
		});

		$(document).bind(InteractionEvents.AdvSrchClear, function(event) {
			self.clear();
		});
		
		self.clearAddress = function() {
			self.address(undefined);
			self.addresses([]);
			self.searchCriteria.atAddress().fullAddress("");
			self.searchCriteria.atAddress().municipality("");
			self.searchCriteria.atAddress().Street_Address_City().iri("");
			self.searchCriteria.atAddress().Street_Address_City().label("");
			self.searchCriteria.atAddress().Street_Address_State().iri(undefined);
			self.searchCriteria.atAddress().Street_Address_State().label("");
			self.searchCriteria.atAddress().Street_Unit_Number("");
			self.searchCriteria.atAddress().Zip_Code("");
			patchPlaceholdersforIE();
		};
		
		self.clear = function() {
			$('[name="ServiceRequestType"]').val(""); //TODO th not sure if above is needed when next line is present. ask phani.
			self.searchCriteria.type("");   
			self.searchCriteria.department(undefined);
			self.searchCriteria.serviceQuestion(""); 
			self.searchCriteria.serviceQuestionAnswerTermString(""); //for CHAR, PHONENUM
			self.searchCriteria.serviceQuestionAnswerTermChoice(""); //for CHARLIST, CHARMULT, CHAROPT
			self.searchCriteria.serviceQuestionAnswerTermDateStart(""); // for DATE
			self.searchCriteria.serviceQuestionAnswerTermDateEnd(""); // for DATE
			self.searchCriteria.serviceQuestionAnswerTermNumberStart(""); // for NUMBER 
			self.searchCriteria.serviceQuestionAnswerTermNumberEnd(""); // value dependent on hasDataType of ServiceField
			self.searchCriteria.srID("");
			self.searchCriteria.hasStatus().iri(undefined);
			self.searchCriteria.hasStatus().label("");
			self.searchCriteria.hasIntakeMethod().iri(undefined);
			self.searchCriteria.hasIntakeMethod().label("");
			self.searchCriteria.createdStartDate("");
			self.searchCriteria.createdEndDate("");
			self.searchCriteria.updatedStartDate("");
			self.searchCriteria.updatedEndDate("");
			self.searchCriteria.overdueDate("");
			self.searchCriteria.inputBy("");
			self.searchCriteria.modifiedBy("");
			self.searchCriteria.name("");
			self.searchCriteria.lastName("");
			self.searchCriteria.geoLayerAttr().iri(undefined);
			self.searchCriteria.geoLayerAreaSearchValue("");
			self.misc.metaData().columns(8);
			self.misc.metaData().newColumn("");
			self.result({"record":[]});
			//self.clearAddress();
			patchPlaceholdersforIE();
			reset();
		};
		
		return self;
	}
	// END Advanced Searchmodel
	
	function advancedSearch(addressModel) {
		var self = {};
		self.dom = $.tmpl($('#advancedSearchTemplate', legacyTemplates))[0];

        //var obj = metadata.LegacyServiceCaseListInput;
        //var renderer = ui.engine.getRenderer(obj.type);
        //var srtypeinput = renderer.apply(ui, [obj]);
		//$('#srTypeListAdvSearch', self.dom).append($(srtypeinput).attr("placeholder", "Service Request Type")
		//.attr("name", "ServiceRequestType"));
		
		self.model = getAdvancedSearchModel(self.dom, addressModel);
        self.embed = function(parent) {
            ko.applyBindings(self.model, self.dom);
        	$(parent).append(self.dom);
        	return self;
        };
		return self;
	}
	
    function getPopularSearches() {
        var terms = ko.observableArray();
        cirm.op.async().get('/individual/PopularSearchesList', {}, function (config) {
            if (!config.ok) {
                console.log('Unable to read PopularSearchList', config.error);
                return terms;
            }
            config = config.data;
            if (config.hasMember === undefined)
                return terms;
            var A = U.isArray(config.hasMember) ?  config.hasMember : [config.hasMember];
            A.sort(function(a,b) { return a.hasOrdinal - b.hasOrdinal; });
            $.each(A, function(i,v) {
                terms.push({
                        hasText:ko.observable(v.hasText),
                        hasName:ko.observable(v.hasName),
                        type:'AnswerHubConfiguration'});
            });                
        });
//        var config = cirm.op.get('/individual/PopularSearchesList');
        return terms;
    }

    function PopularSearchesAdminModel(dom) {
        var self = {};
        self.dom = dom;
        self.terms = getPopularSearches();
        self.addTerm = function() {
            self.terms.push({
                    hasText:ko.observable('type search string'),
                    hasName:ko.observable('type label'),
                    type:'AnswerHubConfiguration'});
        };
        self.removeTerm = function() {
            self.terms.remove(this);
        };
        self.savePopularSearches = function () {
            var A = [];
            for (i=0; i < self.terms().length; i++) {
                var x = self.terms()[i];
                A.push({hasText:x.hasText(), hasName:x.hasName(), hasOrdinal:i, type:x.type});
            }
            var x = {
                iri:"http://www.miamidade.gov/ontology#PopularSearchesList",
                type:"AnswerHubConfiguration",
                hasMember:A
            };
            cirm.op.postObject('/individual/PopularSearchesList',
                x, function(r) {
                    console.log(r);
                });
        };
        self.rotateUp = function() {
            var idx = self.terms.indexOf(this);
            if (idx == 0) {
                self.terms.push(self.terms.shift());
            }
            else {
                var array = self.terms();
                self.terms.splice(idx-1, 2, array[idx], array[idx-1]); 
            }
        };
        self.rotateDown = function() {
            var idx = self.terms.indexOf(this);
            if (idx == self.terms().length - 1) {
                self.terms.unshift(self.terms.pop());
            }
            else {
                var array = self.terms();
                self.terms.splice(idx, 2, array[idx + 1], array[idx]); 
            }
        };
        return self;
    }

    function popularSearchAdmin() {
        var self = {};
        self.dom = $.tmpl($('#popularSearchAdminTemplate', legacyTemplates))[0];
        self.model = new PopularSearchesAdminModel(self.dom);
        self.embed = function(parent) {
            ko.applyBindings(self.model, self.dom);
              $(parent).append(self.dom);
              return self;
        }
        return self;
    }
    
    function popularSearches(searchFunction) {
        var self = {};
        self.dom = $.tmpl($('#popularSearchesTemplate', legacyTemplates))[0];
        self.model = {
            terms:getPopularSearches(),
            searchTerms: function (t) {
                searchFunction(t);
            }
        };
        self.embed = function(parent) {
            ko.applyBindings(self.model, self.dom);
              $(parent).append(self.dom);
              return self;
        }
        return self;
    }
    
    var M = {
        interactionHistory: interactionHistory,
        marquee:marquee,
        marqueeAdmin:marqueeAdmin,
        wcs:wcs,
        elections:elections,
        asd:asd,
        advancedSearch:advancedSearch,
        popularSearchAdmin:popularSearchAdmin,
        popularSearches:popularSearches,
        InteractionEvents: InteractionEvents
//        serviceRequests:serviceRequests,
//        serviceCaseList:serviceCaseList,
//		autoCompleteServiceCaseList:autoCompleteServiceCaseList,
//        metadata:metadata
    };
    if (modulesInGlobalNamespace)
        window.legacy = M;
    return M;
});
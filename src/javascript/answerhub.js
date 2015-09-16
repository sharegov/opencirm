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
define(["jquery", "U", "rest", "uiEngine", "cirm", "legacy", "cirmgis", "text!../html/answerhubTemplates.ht"], 
    function($, U, rest, ui, cirm, legacy, cirmgis, answerhubHtml)   {
			
	function AddressModel() {
		var self = this;
		self.fullAddress = ko.observable("");
		self.zip = ko.observable("");
		self.folio = ko.observable("");
		self.unit = ko.observable("");
		self.municipality = ko.observable("");
		self.municipalityId = ko.observable("");
		self.coordinates = ko.observable({});
		self.commonLocation = {name:ko.observable(""), layer: ko.observable(""), id:ko.observable("")};
		self.addressData = ko.observable(null);
		self.setData = function ( data ) {
			if(data.address != null)
				self.fullAddress ( data.address.split(",")[0]);
			if(data.addressApproximation == true)
				self.fullAddress ( self.fullAddress() + " (APPROX)");
			if(data.parsedAddress.zip != undefined && data.parsedAddress.zip.length > 0)
				self.zip ( data.parsedAddress.zip );
			else {
				if(data.address != null)
					self.zip ( data.address.split(",")[1] );
				else if(data.addressApproximation != null)
					self.zip ( data.addressApproximation.split(",")[1] );
			}
			if(data.parsedAddress.unit != undefined && data.parsedAddress.unit.length > 0)
				self.unit ( data.parsedAddress.unit );
			if(data.propertyInfo == null)
				data.propertyInfo = {};
			if(data.propertyInfo.parcelFolioNumber != undefined)
			{
				self.folio(data.propertyInfo.parcelFolioNumber );
				if(data.propertyInfo.homeOwnerAssociationName == undefined)
					data.propertyInfo.homeOwnerAssociationName = "";
			} else {
				self.folio(null);
			}
			self.municipality( data.municipality );
			self.municipalityId( data.municipalityId );
			self.coordinates ( data.location );
			self.addressData ( data );
		};
		
		self.clearLocation = function() {
    		$(document).trigger(legacy.InteractionEvents.AddressClear, []);
		};
		
		self.clear = function() {
    	    self.fullAddress("");
    	    self.zip("");
    		self.folio("");
    		self.unit("");
    		self.municipality("");
    		self.municipalityId("");
    		self.coordinates({});
    		self.commonLocation.name("");
    		self.commonLocation.layer("");
    		self.commonLocation.id("");
    		self.addressData(null);
    	};

    	self.getCity = function( alias ) {
    		if(!alias)
    			alias = self.municipality();
    		for (i=0; i < cirm.refs.cities.length; i++) {
                var x = cirm.refs.cities[i];
                if( U.isArray( x.Alias ) && $.inArray(alias, x.Alias) > -1)
                	return x;
                else if( alias == x.Alias)
                	return x;
            }
    	};
    	
    	self.getDirection = function( alias ) {
    		if(!alias)
    			alias = self.addressData().parsedAddress.PreDir;
    		for (i=0; i < cirm.refs.directions.length; i++) {
                var x = cirm.refs.directions[i];
                if( U.isArray( x.USPS_Abbreviation ) && $.inArray(alias, x.USPS_Abbreviation) > -1)
                	return x;
                else if( alias == x.USPS_Abbreviation)
                	return x;
            }
    	};
    	
    	self.getStreetType = function( alias ) {
    		if(!alias)
    			alias = self.addressData().parsedAddress.SufType;
    		for (i=0; i < cirm.refs.streetTypes.length; i++) {
                var x = cirm.refs.streetTypes[i];
                if( U.isArray( x.Alias ) && $.inArray(alias, x.Alias) > -1)
                	return x;
                else if( alias == x.Alias )
                	return x;
            }
    	};
    	return self;
	}
	
	function AddressSearchModel() {
		var self = this;
		self.address = new AddressModel();
		self.addresses = ko.observableArray([]);
		self.commonLocations =  ko.observableArray([]);
		self.gis = null;
		self.map = ko.observable(null);
		self.last5Addr = ko.observableArray([]);
    	self.clear = function() {
    		self.address.clear();
    	    self.addresses.removeAll();
    	};

    	self.updateLast5Addr = function() {
    		var temp = {
    			"fullAddress": self.address.fullAddress(), "municipality":self.address.municipality(),
    			"zip":self.address.zip(), "folio":self.address.folio(), "unit": self.address.unit()};
    		if(self.last5Addr().length == 5)
    			self.last5Addr.pop();
    		self.last5Addr.unshift(temp);
    	};
    	
    	self.showLast5Addr = function() {
			$("#ah_last5_addr").dialog({height: 200, width: 600, modal: true});
    	};

    	self.fetchAddress = function(el) {
		  	$("#ah_last5_addr").dialog('close');
			$("#ah_addr_dialog_alert")[0].innerText = "Do you want to fetch this Address ?";
			$("#ah_addr_dialog_alert").dialog({ height: 150, width: 350, modal: true, buttons: {
				"Fetch" : function() {
					self.address.fullAddress(el.fullAddress);
					self.address.municipality(el.municipality);
					self.address.folio(el.folio);
					self.address.zip(el.zip);
					$("#ah_addr_dialog_alert").dialog('close');
					self.searchAddress();
				},
				"Cancel": function() {
				  	$("#ah_addr_dialog_alert").dialog('close');
				}
			  } 
			});

    	};

    	self.validateOnTab = function(data, event) {
    	    if(event.keyCode == 9 || event.keyCode == 13) {
   	            self.searchAddress();
    	    }
    	    return true;    	    
    	};
    	
    	self.searchFolioOnEnter = function(data, event) {
    		if(event.keyCode == 13) {
    			self.searchFolio();
    		}
    		return true;
    	};
    	
    	self.searchCommonLocationOnEnter = function(data, event) {
    		if(event.keyCode == 13) {
    			self.searchCommonLocation();
    		}
    		return true;
    	};
    	
		self.launchMap = function() {
			$(document).trigger(legacy.InteractionEvents.UserAction, ['Launch Map', '']);		    
			if(!self.map() || self.map().closed) {
			    var mapWindow = null;
				var callback = function () {
			        self.setAddressOnMap(self.address.fullAddress(), 
				                                    self.address.zip(),
				                                    self.address.addressData().propertyInfo,
				                                    self.address.unit(),
				                                    self.address.municipalityId()
				                                );
			        $(document).unbind("mapInitializedEvent", callback);			        
			    };
			    $(document).bind("mapInitializedEvent", callback);
			    mapWindow = window.open("/html/answerhubmap.html","","width=1024,height=768,alwaysRaised=yes");
			    //IE9 fix for callee exception.
			    //02.21.2013 wrapped event and listeners as workaround to avoid initial page load
                $(mapWindow).load(function() {
                    $(mapWindow).unload(function() {
                        self.map(null);
                    });
                });
				self.map(mapWindow);
				
				
			}
			else {
				self.map().focus();
				self.setAddressOnMap(self.address.fullAddress(), 
				                                self.address.zip(),
				                                self.address.addressData().propertyInfo,
				                                self.address.unit(),
				                                self.address.municipalityId());
			}			
		};
		
		self.setAddressOnMap = function(address, zip, propertyInfo, unit, municipalityId)
		{
			//if(propertyInfo.match) {
				self.map().address = address;
				self.map().zip = zip;
				self.map().propertyInfo = propertyInfo;
				if(unit != 'MULTI')
					self.map().unit = unit;
				else 
					self.map().unit = "";
				self.map().municipalityId = municipalityId;
				self.map().doMap();
			//}
		};
		
		$(document).bind(legacy.InteractionEvents.LaunchMap, function(event, data) {
			self.launchMap();
			//self.setAddressOnMap(data.fullAddress(), data.zip(), data.unit());
		});
		
		$(document).bind(legacy.InteractionEvents.SearchAddress, function(event, data, behindTheScenes) {
			self.address.fullAddress(data.fullAddress());
			if(behindTheScenes)
			{
				if(data.Street_Unit_Number() == 'MULTI')
					self.address.unit("");
				else
					self.address.unit(data.Street_Unit_Number());
				if(data.municipality)
					self.address.municipality(data.municipality());
			}
			self.address.zip(data.Zip_Code());
			self.searchAddress(behindTheScenes);
		});
		
		$(document).bind(legacy.InteractionEvents.SetAddress, function(event, addressData, behindTheScenes) {
			self.setAddress(addressData, false, behindTheScenes);
		});

		$(document).bind(legacy.InteractionEvents.SearchFolio, function(event, data) {
			self.address.folio(data);
			self.searchFolio();
		});
		
		$(document).bind(legacy.InteractionEvents.StandardizeStreetRequest, function(event, data, fromServiceHub) {
			self.standardizeStreet(data, fromServiceHub);
		});

		/*
		self.mapAddress = function() {
			AnswerHubMap.mapAddress(self.address.fullAddress(), self.address.zip());
		};*/
	
		self.setAddress = function(data, viaResolveAddress, behindTheScenes) {
		    console.log('set address', data);
				self.address.setData( data );
				if(data.propertyInfo.propertyType != undefined && data.propertyInfo.propertyType == 'MULTI')
				{
					self.address.unit('MULTI');
					var unitAutoComplete = {
						minLength: 0,
						source: data.propertyInfo.units,
						select: function (event, ui)
						{
							$("#ah_unit_select").dialog({
		   		             	autoOpen: true,
		   		             	modal:true,
		   		             	buttons : {
		   		                	"Yes" : function() {
										self.address.unit(ui.item.value);
										$(this).dialog("close");
										self.searchAddress();
							     	},
		   			                "No" : function() {
							     		$(this).dialog("close");
		   			                }
		   			             }
		   			         });
						}
					};
					$("#ah_unit").autocomplete(unitAutoComplete);
					$("#sh_unit").autocomplete(unitAutoComplete);
				}
				else
				{
					$("#ah_unit").autocomplete( "destroy" );
					$("#sh_unit").autocomplete( "destroy" );
				}
				
				if(self.map()) {
					self.map().address = self.address.fullAddress();
					self.map().zip = self.address.zip();
					self.map().propertyInfo = self.address.addressData().propertyInfo;
					if(self.address.unit() != 'MULTI')
						self.map().unit = self.address.unit();
					else 
						self.map().unit = "";
					self.map().municipalityId = self.address.municipalityId();
					self.map()['doMap'].call(self.map());
				}
				if(viaResolveAddress == false)
					self.updateLast5Addr();
				$(document).trigger(legacy.InteractionEvents.AddressValidated, [self.address, behindTheScenes]);
		};
		
		self.setCommonLocation = function (data) {
			self.address.commonLocation.id(data.id);
			self.address.commonLocation.name(data.name);
			self.address.commonLocation.layer(data.layer);
			self.setAddress(data.address, false);
		};
		
//		self.getPropertyInfo = function() {
//		    var zip = self.address.zip() || "";
//			AnswerHubMap.getPropertyInfo(self.address.fullAddress(),self.address.zip(), "setPropertyInfo");
//		};
		
		self.searchCommonLocation = function(){
			if(!self.gis) {
				initGis();
			}
			self.showProgress("#ah_dialog_location_search");
		    $(document).trigger(legacy.InteractionEvents.UserAction, 
		            ['Search Common Location', self.address.commonLocation.name()]);			
			self.gis.getCommonLocationCandidates(self.address.commonLocation.name(), function( candidates ) {
				if(candidates === undefined || candidates.length == 0) {
					//self.address.addressData(null);
					self.hideProgress("#ah_dialog_location_search");
					$( "#ah_dialog_location" ).dialog({
						height: 200,
						modal: true
					});
					return;
				}
				if(candidates.length == 1) {
					self.hideProgress("#ah_dialog_location_search");
					self.setCommonLocation(candidates[0]);
				}else {
					self.hideProgress("#ah_dialog_location_search");
					self.commonLocations(candidates);
					$( "#ah_dialog_locations_resolve" ).dialog({
						height: 200,
						modal: true
					});
				}
			}
			,function(msg, req, status) {
				self.hideProgress("#ah_dialog_location_search");
				$("#ah_addr_dialog_alert")[0].innerText = status + ' \n' + msg;
				$("#ah_addr_dialog_alert").dialog({ height: 150, width: 350, modal: true, buttons: {
					"Close" : function() { $("#ah_addr_dialog_alert").dialog('close'); }
				}});
			}
			);
		};

		self.standardizeStreet = function(data, fromServiceHub) {
			self.showProgress("#ah_dialog_address_search");
			if(!self.gis) {
				initGis();
			}
			var stdStr = (typeof data == 'function') ? data() : data;
			self.gis.getStandardizedStreet(stdStr 
				, function(street) {
					self.hideProgress("#ah_dialog_address_search");
					if(fromServiceHub && typeof data == 'function') {
						data(street);
						data.isStandardizedStreet(true);
					}
				} 
				, function(msg, req, status) {
					self.hideProgress("#ah_dialog_address_search");
					$("#ah_addr_dialog_alert")[0].innerText = status + ' \n' + msg;
					$("#ah_addr_dialog_alert").dialog({ height: 150, width: 350, modal: true, buttons: {
						"Close" : function() { $("#ah_addr_dialog_alert").dialog('close'); }
					}});
			});
		};
		
		self.searchAddress = function(behindTheScenes) {
		    if (U.offline()) {
		        console.log("Can't search address while in offline mode.");
		        return;
		    }
		    $(document).trigger(legacy.InteractionEvents.WCSClear, []);
			$(document).trigger(legacy.InteractionEvents.UserAction, ['Validate Address', 
			    self.address.fullAddress() + ',' + self.address.zip()]);
			if(!self.gis) {
				initGis();
			}
			if(!behindTheScenes)
			{
				$('body').css('cursor', 'url(/html/images/hourglass.cur),auto');
				self.showProgress("#ah_dialog_address_search");
			}
			if (self.address.unit() != undefined && self.address.unit().length > 0) {
				self.gis.getCondoAddress(self.address.fullAddress(),self.address.zip(), self.address.unit(), function(candidate) {
					self.hideProgress("#ah_dialog_address_search");
					$('body').css('cursor', 'default');
					if(!candidate)
						$( "#ah_dialog_address" ).dialog({ height: 200,modal: true });
					else
						self.setAddress(candidate, false);
				}
				,function(msg, req, status) {
					self.hideProgress("#ah_dialog_address_search");
					$('body').css('cursor', 'default');
					$("#ah_addr_dialog_alert")[0].innerText = status + ' \n' + msg;
					$("#ah_addr_dialog_alert").dialog({ height: 150, width: 350, modal: true, buttons: {
						"Close" : function() { $("#ah_addr_dialog_alert").dialog('close'); }
					}});
				});
			}
			else {
				self.gis.getAddressCandidates(self.address.fullAddress(),self.address.zip(), self.address.municipality(), function(candidates) {
					if(candidates === undefined || candidates.length == 0) {
						//self.address.clear();
						self.hideProgress("#ah_dialog_address_search");
						$('body').css('cursor', 'default');
						$( "#ah_dialog_address" ).dialog({
							height: 200,
							modal: true
						});
						return;
					}	
					else if(candidates.length == 1) {
						self.hideProgress("#ah_dialog_address_search");
						$('body').css('cursor', 'default');
						self.setAddress(candidates[0], false, behindTheScenes);
					}
					else {
						if(!behindTheScenes)
						{
							self.hideProgress("#ah_dialog_address_search");
							$('body').css('cursor', 'default');
							self.addresses(candidates);
							$( "#ah_dialog_addresses_resolve" ).dialog({ height: 200, modal: true });
						}
						else
						{
							$('body').css('cursor', 'default');
							var finalCandidate = $.map(candidates, function(v,i) {
								if(self.address.municipality && self.address.municipality().indexOf(v.municipality) != -1)
									return v;
							});
							self.address.municipality("");
							if(finalCandidate.length == 1)
								self.resolveAddress(finalCandidate[0], behindTheScenes);
							else
							{
								self.addresses(candidates);
								$( "#ah_dialog_addresses_resolve" ).dialog({ height: 200, modal: true });
							}
						}
					}
				}
				,function(msg, req, status) {
				self.hideProgress("#ah_dialog_address_search");
				$('body').css('cursor', 'default');	
				 if (req.status != 200) {
						$("#ah_addr_dialog_alert")[0].innerText = status + ' \n' + "Address and geographic data lookups are currently unavailable.  Please try again shortly.";
				 }else{			
						$("#ah_addr_dialog_alert")[0].innerText = status + ' \n' + msg;			 
				 }
					
					$("#ah_addr_dialog_alert").dialog({ height: 150, width: 350, modal: true, buttons: {
						"Close" : function() { $("#ah_addr_dialog_alert").dialog('close'); }
					}});
				});
			}
		};

		self.resolveAddress = function(selected, behindTheScenes) {
			$("#ah_dialog_addresses_resolve").dialog('close');
			self.setAddress(selected, true, behindTheScenes);
			//need to search a second time to resolve folio information
			self.searchAddress(behindTheScenes);
			$(document).trigger(legacy.InteractionEvents.UserAction, ['Select Address', 
			    self.address.fullAddress() + ',' + self.address.zip()]);			
		};
		
		self.resolveCommonLocation = function(selected) {
			$("#ah_dialog_locations_resolve").dialog('close');
			self.showProgress("#ah_dialog_location_search");
			self.gis.getCommonLocation(selected.id, function(location) {
				self.hideProgress("#ah_dialog_location_search");
				if(location === undefined)
					$( "#ah_dialog_location" ).dialog({ height: 200,modal: true });
				else
					self.setCommonLocation(location);
			}
			,function(msg, req, status) {
				self.hideProgress("#ah_dialog_location_search");
				$("#ah_addr_dialog_alert")[0].innerText = status + ' \n' + msg;
				$("#ah_addr_dialog_alert").dialog({ height: 150, width: 350, modal: true, buttons: {
					"Close" : function() { $("#ah_addr_dialog_alert").dialog('close'); }
				}});
			});
			
			$(document).trigger(legacy.InteractionEvents.UserAction, ['Select Address', 
			    self.address.fullAddress() + ',' + self.address.zip()]);			
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
		
		
		self.searchFolio = function() {
			self.showProgress("#ah_dialog_address_search");
			if(!self.gis) {
				initGis();
			}
		    $(document).trigger(legacy.InteractionEvents.UserAction, 
                                ['Search Folio', self.address.folio()]);
			self.gis.getAddressByFolio (self.address.folio(), function(candidate){
				self.hideProgress("#ah_dialog_address_search");
				if(candidate === undefined) {
					$( "#ah_dialog_folio" ).dialog({
						height: 200,
						modal: true
					});
					return;
				}
				else {
					self.setAddress(candidate, false);
				}
			}
			,function(msg, req, status) {
				self.hideProgress("#ah_dialog_address_search");
				$("#ah_addr_dialog_alert")[0].innerText = status + ' \n' + msg;
				$("#ah_addr_dialog_alert").dialog({ height: 150, width: 350, modal: true, buttons: {
					"Close" : function() { $("#ah_addr_dialog_alert").dialog('close'); }
				}});
			});
		};

		self.showPropertyInfo = function(data, event) {
			var id = event.target.id;
			if ( id == 'geo_info_district')
				self.showDistrictDialog();
			else if	(id == 'geo_info_property')		
				self.showPropertyDialog();
			else if (id == 'geo_info_garbage')
				self.showGarbageDialog();
			else if (id == 'geo_info_publicworks')
				self.showPublicWorksDialog();	
		};
        
		self.showDistrictDialog = function() {
			$('#ah_dialog_district').dialog({ 'modal':false, 'width':600, 'height':400, 'draggable':true });
		};
		
		self.showPropertyDialog = function() {
			$('#property_info').tabs();
			$('#ah_dialog_property').dialog({ 'modal':false, 'width':600, 'height':400, 'draggable':true });
		};

		self.showGarbageDialog = function() {
			$('#ah_dialog_garbage').dialog({ 'modal':false, 'width':600, 'height':400, 'draggable':true });
		};

		self.showPublicWorksDialog = function() {
			$('#ah_dialog_publicworks').dialog({ 'modal':false, 'width':600, 'height':400, 'draggable':true });
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
		
		function initGis() {
			var config = cirm.top.get("/individuals/predefined/configset");
			var fullUrl = config.GisConfig.hasUrl;
			var url = fullUrl.substring(0,fullUrl.lastIndexOf("/"));
			var path = fullUrl.substring(fullUrl.lastIndexOf("/"));
			cirmgis.initConnection(url, path);
			self.gis = cirmgis;
		}
		
		return self;
	}
    
	function TopicSearchModel(addressModel) {
		var self = this;
		self.topics = ko.observable(null);
		self.totalResults = ko.observable("");
		self.serviceRequests = ko.observableArray();
		self.keywords = ko.observable("");
		self.addressModel = addressModel;
		self.maxResults = ko.observable("15");
		
		self.agencies = [];
		$.each(cirm.refs.agencies, function(iri,v) {
		        self.agencies.push({name:v.label,value:iri});
		});
		self.agencies.sort(function(x,y) { return (x.name > y.name) ? 1 : (x.name == y.name ? 0 : -1); });
        var defAgency = {name:'Select Municipality', value:''};
		self.agencies.unshift(defAgency);
		self.selectedAgency = ko.observable(defAgency);
        $(document).bind(legacy.InteractionEvents.AddressValidated, function(event, data, behindTheScenes) {
          //console.log('set municipality', data.propertyInfo());
          var name  = data.municipality();
          var municipality = cirm.refs.citiesByName[name];
          self.selectedAgency(defAgency);
          if (municipality) 
            $.each(self.agencies, function(i, x) {
              if (x.value == municipality.iri)
                self.selectedAgency(x);
            }); 
        });
		
		var defDepartment = {name:'Department/Agency', value:''};
		self.departments = ko.observableArray([defDepartment]);
		self.selectedDepartment = ko.observable(defDepartment);
		
        self.selectedAgency.subscribe(function (v) {
               self.departments.removeAll();                
               self.departments.unshift(defDepartment);
               self.selectedDepartment(defDepartment);
               if (v.value == '')
                   return;
               var iri = v.value;
               //console.log('selected ', iri, cirm.refs.[iri]);
               $.each(cirm.refs.agencies[iri].agencies, function(i,v) {
                    self.departments.push({name:v.label,value:v.iri});
                });
               self.departments.sort(function(x,y) 
                   { return (x.name > y.name) ? 1 : (x.name == y.name ? 0 : -1); });
        }, self);

        $(document).bind(legacy.InteractionEvents.AddressClear, function(event) {
                self.selectedAgency(defAgency);
                self.departments.removeAll();
                self.departments.unshift(defDepartment);
                self.selectedDepartment(defDepartment);
        });
        
		$("#kb_results").hide();

    	self.clear = function() {
    	    self.topics(null);
    	    self.serviceRequests.removeAll();
    	    self.keywords("");
    	    self.selectedAgency(defAgency);  	    
    	    $("#kb_results").hide();
    	};

    	self.searchOnEnter  = function(data, event) {
    	    if(event.keyCode == 13) {
   	            self.searchKnowledgeBase();
    	    }
    	    return true;    	    
    	};

		self.searchKnowledgeBase = function(terms) {
			var query = "";
			if (!terms || ! (typeof terms == "string"))
			{
			    $(document).trigger(legacy.InteractionEvents.UserAction, ['Search', self.keywords()]);
			    query = self.keywords();
			}
			else
			    query = terms;
			if(!query)
			{
				$( "#ah_dialog_keywords" ).dialog({
						height: 140,
						modal: true
					});
					return;
			}
			self.showProgress("#ah_dialog_keywords_search");
    		console.log('search', query, ko.toJS(self.addressModel));
    		var params = { query: query, 
    		              meta: {rows:self.maxResults(), 
    		                         address:ko.toJS(self.addressModel) }};
    		if (self.selectedAgency().value.length > 0 || self.selectedDepartment().value.length > 0)
    		{
    		    if (self.selectedAgency().value.length > 0)
    		        params.agency = self.selectedAgency().value;
    		    if (self.selectedDepartment().value.length > 0) {
    		        params.ontology = [];
    		        params.ontology.push(self.selectedDepartment().value);
    		    }
    		}
    		else if (self.addressModel.municipality())
    		    params.agency = cirm.refs.citiesByName[self.addressModel.municipality()].iri;
    		cirm.search.postObject("/kb", params, 
						function(r) {
							self.hideProgress("#ah_dialog_keywords_search");
						if (r.ok) {
						    $.each(r.docs, function(i,doc) {
						            if (typeof doc.ontology == "undefined") {
						                doc.ontology = [];
						            }
						    });
		                    if(window.location.href.indexOf('.311.') > -1 ) {
				                doc.url = doc.url.replace('kb.miamidade', 'kb.311.miamidade'); 						                   	 
				            }
						    console.log('results', r);
						    self.totalResults(r.total);
						    self.topics(r.docs);
						    self.serviceRequests.removeAll();
						    $.each(r.docs, function(i,doc) {
						            $.each(doc.ontology, function(i,o) {
						                    if (cirm.refs.serviceCases[o] != undefined &&
						                        self.serviceRequests.indexOf(o) < 0) {
    						                    self.serviceRequests.push(o);
    						                   }
						            });
						    });
						    $("#kb_results").show();
						}
						else {
						    alert(r.error);
						    console.log(r);
						}
						$('#kb_results').scrollTop(0);
						});
		}
		
		self.showProgress = function(id) {
			$( id ).dialog({
				height: 140,
				modal: true
			});
		}

		self.hideProgress = function(id) {
			$( id ).dialog('close');
		}
		return self;
	}
    
	function AnswerHubModel(dom) {
		var self = this;
		self.dom = dom; 
		self.addressSearch = new AddressSearchModel();
		self.topicSearch = new TopicSearchModel(self.addressSearch.address);	
		
		self.setScroller = function(val) {
			if(U.isEmptyString(val) == false)
				return true;
			else
				return false;
		}

		self.isServiceRequest = function(data) {
		    return cirm.refs.serviceCases[data] != undefined;
		}
		
		self.ontologyClick = function(data) {
		    if(cirm.refs.serviceCases[data].isDisabled != 'true')
		    	$(document).trigger(legacy.InteractionEvents.ServiceRequestTypeClick, [data]);
		    else {
			    $("#sh_dialog_alert")[0].innerText = "Cannot create a disabled Service Request Type";
				$("#sh_dialog_alert").dialog({ height: 300, width: 500, modal: true, buttons: {
					"Close" : function() { $("#sh_dialog_alert").dialog('close'); }
				 } 
				});
		    }
		}
		
		self.trackTopicClick = function(data) {
		    $(document).trigger(legacy.InteractionEvents.UserAction, ["TopicClick", data.url]);
		    return true;
		}
		
		self.ontologyLabel = function(data) {
		    var sr = cirm.refs.serviceCases[data];
		    if (sr)
		        return sr.label;
		    else
		        return U.IRI.name(data).replace(/_/g, ' ');
		}
		
		self.clearAllTabs = function(data) {
		    $(document).trigger(legacy.InteractionEvents.AllTABSClear, []);
		};
		
		self.clear = function() {
			self.addressSearch.clear();
			self.topicSearch.clear();
		}
			
		self.openTransitFolder = function () {
		    console.log('open transit');
		    window.open("file://\\Etsddept\311 operations services\TRANSIT");
        }
		return self;
	}
    
	function makeAnswerHub() {
		var self = {};
		self.markup = $(answerhubHtml);
		// console.log('map div', $('#map', self.markup)[0]);
		//no more dependency on the map control embedded in AH.
		//loadMapControl($('#map', self.markup)[0]);
		self.model = new AnswerHubModel(self.markup[0]);
		ko.applyBindings(self.model, self.markup[0]);
								
		self.embed = function(parent) {
		    $(parent).append(self.markup);
		// NOTE: important to embed any sub-components after the top-level ko
			// bindings are performed,
		// otherwise KO complains when it can't find the bindings defined in the
			// sub-component (while
		// binding the containing component..
		legacy.popularSearches(function(t) {
			self.model.topicSearch.keywords("");
			$(document).trigger(legacy.InteractionEvents.UserAction, ['PredefinedSearch', t.hasName()]);			
			self.model.topicSearch.searchKnowledgeBase(t.hasText());
		}).embed($('#popularSearchContainer',self.markup));
		    
		    var ihist = legacy.interactionHistory(); 
		    ihist.embed($('#callInteractionContainer',self.markup));	
		    
		}
		
		// Menu switch on SR details, not sure if this
		return self;
	}
    
	var M = {
	    AddressModel:AddressModel,
		makeAnswerHub: makeAnswerHub
	};
    if (modulesInGlobalNamespace)
        window.answerhub = M;
    return M;	
});
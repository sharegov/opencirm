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
define(["jquery", "U", "rest", "uiEngine", "cirm", "businessobject"], function($, U, rest, ui, cirm, bo)   {
	var urlBase = "http://olsportaldev:8182";

	// The parent DOM element where this app resides.
	var parentElement = null;

	// Title of the application
	var title = 'Legacy Emulator';
	
	// The application OWL individual
	var app = null;

	// The UI components display on the main, top-level page of this application
	var components = null;

	// The current BusinessObject (an Iquiry or ServiceRequest that's in view)
	var currentRequest = null;

	// The list of all available inquiries (service requests) to initiate.
	var inquiries = null;

	//list of participants with their personal info.
	var actorsCounter = 0;
	var serviceActorsInfo = {};
	var blankServiceActorData = {
		"Name":"","LastName":"",
		"HomePhoneNumber":"","CellPhoneNumber":"","BusinessPhoneNumber":"","OtherPhoneNumber":"",
		"FaxNumber":"","hasEmailAddress":"mailto:","type":"legacy:ServiceCaseActor",
		"atAddress": {
			"Street_Number":"","Street_Direction":"","Street_Name":"",
			"hasStreetType":"","Street_Unit_Number":"","Street_Address_City":"",
			"Zip_Code":"", "type":"Street_Address"},
		"legacy:hasServiceActor": { "iri":"","type":"legacy:ServiceActor"}
	};

	//list of activities with each activity info.
	var counter = 0;
	var allActivities = null;
	var listActivitiesInfo = {};
	var blanklistActivitiesInfo = {
		"legacy:hasCreatedDate":"", "legacy:hasCompletedTimestamp":"", "legacy:hasUpdatedDate":"", "legacy:hasDueDate":"", 
		"legacy:isAssignedTo":"", "legacy:hasDetails":"",
		"legacy:hasActivity":{"iri":"", "type":"legacy:Activity"}, 
		"type":"legacy:ServiceActivity"
	};
	var blanklistActivitiesInfoWithOutcome = {
		"legacy:hasCreatedDate":"", "legacy:hasCompletedTimestamp":"", "legacy:hasUpdatedDate":"", "legacy:hasDueDate":"", 
		"legacy:isAssignedTo":"", "legacy:hasDetails":"",
		"legacy:hasActivity":{"iri":"", "type":"legacy:Activity"}, 
		"legacy:hasOutcome":{"iri":"", "type":"legacy:Outcome"},
		"type":"legacy:ServiceActivity"
	};

	function startInquiry(iri, properties, containingElement, continuation)
    {
        var classname = iri.substring(iri.lastIndexOf('#')+1, iri.length);
        $.getJSON(urlBase + '/op/new/' + classname, function (data) {
                if (properties) {
                    $.extend(data.properties, properties);
                }
                currentRequest = new bo.BusinessObject(data);
                //currentRequest.runWorkflowWizard(containingElement, continuation);
        });
    }

    function setui(dom)
    {
        dom.setAttribute('has-continuation', 'yes');        
        dom.continuationScope = this;
        dom.continuation = function (postTo) {
            var formDataAsJson = JSON.stringify($(dom).cirmSerializeObject());          
            $.post(postTo, {data:formDataAsJson}, function (x) {
                if (x.error)
                {
                    alert('Snap :(, server error: ' + x.error);
                    return;
                }
                if (x.done != null)
                {
                    var  text  = "Inquiry completed " + (x.done  ? "successfully" : "unsuccessfully") + ". ";
                    text += x.info;
                    var comp = document.createElement("p");
                    comp.innerHTML = "<b>" + text + "</b>";
                    setui(comp);
                    return;
                }
                currentRequest = x;
                var comp = ui.makeDomTree(x);
                setui(comp);                    
            });
        };
        $(parentElement).empty();        
        $(parentElement).append(dom);
    }

	function buildServiceActivitiesArray()
	{
		var serviceActivitiesArray = [];
		$.each(listActivitiesInfo, function (key, value) {
			serviceActivitiesArray.push(value);
		});
		return serviceActivitiesArray;
	}

    function buildServiceCaseActorsArray()
	{
		var serviceActorsArray = [];
		$.each(serviceActorsInfo, function (key, value) {
			serviceActorsArray.push(value);
		});
		return serviceActorsArray;
	}
    
	function buildServiceAnswersArray(formData)
	{
		var legacyForm = {};
		var serviceAnswers = [];
		$.each(formData, function (key, value) {
			if(key == "type" || key == "boid")
			{
				//legacyForm[key] = value;
			}
			else if(key == "atAddress") {
				value.type = "Street_Address";
				legacyForm[key] = value;
			}
			else if(key == "hasStatus")
				legacyForm[key] = value;
			else
			{
				if(key != "legacy:hasServiceActor") {
					var serviceAnswer = {"type":"legacy:ServiceAnswer", "legacy:hasAnswerValue":value,"legacy:hasServiceField":{"iri":key} };
					serviceAnswers.push(serviceAnswer);
				}
			}
		});
		legacyForm["legacy:hasServiceAnswer"] = serviceAnswers;
		return legacyForm;
	}

	function populateForm(form, caseData)
   {
	    //populate the address fields
		if(caseData.properties.atAddress)
		{
		    $.each(caseData.properties.atAddress, function(i,v) { 
		    	if(v.iri)
		    		$("[name='atAddress." + i + "']", $(form)).val(v.iri);
		    	else
					$("[name='atAddress." + i + "']", $(form)).val(v);
	    	});
		}

	    //populate all the Service Answers
	    if(caseData.properties.hasServiceAnswer)
	    {
	    	$.each(caseData.properties.hasServiceAnswer, function(i,v) { 
		    	if (v.hasServiceField) {
		    		var text = v.hasServiceField.iri.replace("http://www.miamidade.gov/cirm/legacy#","legacy:");
			    	$("[name='" + text + "']", $(form)).val(v.hasAnswerValue);

			    	if($("[name='" + text + "'] option:first-child").hasClass('jecEditableOption'))
			    		if($("[name='" + text + "']").val() == "" || $("[name='" + text + "']").val() == " ")
			    			$("[name='" + text + "'] option:first-child").text(v.hasAnswerValue).val(v.hasAnswerValue);

			    }
	    	});
	    }

		//populate the Status
	    if(caseData.properties.hasStatus)
	    {
			$("[name='hasStatus']", $(form)).val(caseData.properties.hasStatus.iri);
	    };

		//populate the ServiceActorsInfo map
		if(caseData.properties.hasServiceCaseActor)
		{
			if($.isArray(caseData.properties.hasServiceCaseActor))
			{
			    $.each(caseData.properties.hasServiceCaseActor, function(i,v) { 
			    	var currentText = "";
			    	var currentValue = "";
			    	var currentName = "";
			    	var currentLastName = "";
				    var serviceActorData = U.clone(blankServiceActorData);
					
					$.each(v, function(name, value) {
					    if(name == "atAddress")
						{
							$.each(value, function(addrFieldName, addrFieldValue) {
								if(addrFieldValue.iri)
									serviceActorData.atAddress[addrFieldName] = addrFieldValue.iri;
								else
									serviceActorData.atAddress[addrFieldName] = addrFieldValue;
							});
						}
						else if(name == "hasServiceActor")
						{
							currentValue = value.iri;
							currentText = value.label;
							serviceActorData["legacy:hasServiceActor"].iri = value.iri;
						}
						else if(serviceActorData.hasOwnProperty(name))
						    {
							    if(name == "hasEmailAddress")
								    serviceActorData[name] = value.iri;
							    else if(name == "type")
								    serviceActorData[name] = "legacy:"+value;
						    	else if(name == "Name")
						    	{
						    		currentName = value;
						    		serviceActorData[name] = value;
						    	}
						    	else if(name == "LastName")
						    	{
						    		currentLastName = value;
						    		serviceActorData[name] = value;
						    	}
					    		else 
					    			serviceActorData[name] = value;
						    }
					});
	
			    	//add row for the current Service Actor
			    	addActorRow(currentText, currentValue, currentName, currentLastName);
	
			    	//Add record to ServiceActorsInfo
					serviceActorsInfo[actorsCounter] = serviceActorData;
				});
			}
			else
			{
		    	var currentText = "";
		    	var currentValue = "";
		    	var currentName = "";
		    	var currentLastName = "";
			    var serviceActorData = U.clone(blankServiceActorData);

			    $.each(caseData.properties.hasServiceCaseActor, function(name,value) { 
				    if(name == "atAddress")
					{
						$.each(value, function(addrFieldName, addrFieldValue) {
							if(addrFieldValue.iri)
								serviceActorData.atAddress[addrFieldName] = addrFieldValue.iri;
							else
								serviceActorData.atAddress[addrFieldName] = addrFieldValue;
						});
					}
					else if(name == "hasServiceActor")
					{
						currentValue = value.iri;
						currentText = value.label;
						serviceActorData["legacy:hasServiceActor"].iri = value.iri;
					}
					else if(serviceActorData.hasOwnProperty(name))
				    {
					    if(name == "hasEmailAddress")
						    serviceActorData[name] = value.iri;
					    else if(name == "type")
						    serviceActorData[name] = "legacy:"+value;
				    	else if(name == "Name")
				    	{
				    		currentName = value;
				    		serviceActorData[name] = value;
				    	}
				    	else if(name == "LastName")
				    	{
				    		currentLastName = value;
				    		serviceActorData[name] = value;
				    	}
			    		else 
			    			serviceActorData[name] = value;
				    }
				});
				//add row for the current Service Actor
			    addActorRow(currentText, currentValue, currentName, currentLastName);
				
				//Add record to ServiceActorsInfo
				serviceActorsInfo[actorsCounter] = serviceActorData;
			}

		}

	}
 
    function populateServiceActorData(currentRecordNo)
    {
		$.each(serviceActorsInfo[currentRecordNo], function(i,v) {
			if(i == "type" || i == "hasServiceActor") {}
			else if(i == "atAddress")
			{
				$.each(v, function(addrName, addrValue) {
					$('[name="'+addrName+'"]').val(addrValue);
				});
			}
			else if(i == "hasEmailAddress")
				$('[name="'+i+'"]').val(v.replace("mailto:", ""));
			else
				$('[name="'+i+'"]').val(v);
		});

    }

    function saveServiceActivityData()
    {
	    var serializedActivity = $('#ServiceActivity').cirmSerializeObject('name');
	    
	    var currentRecordNo = 0;
	    $("#ServiceActivity :checkbox:checked").each(function() {
    		var name = this.name;
    		currentRecordNo = name.substring(name.lastIndexOf('-')+1); 
	    });
	    
	    
	    var serviceActivityData = null;
	    if(serializedActivity.hasOutcome == " " || serializedActivity.hasOutcome == "")
	    	serviceActivityData = U.clone(blanklistActivitiesInfo);
	    else
	    	serviceActivityData = U.clone(blanklistActivitiesInfoWithOutcome);
   
	    $.each(serializedActivity, function(name, value) {
    		if(name == "hasOutcome")
    		{
   				serviceActivityData["legacy:hasUpdatedDate"] = getCurrentTime();
    			if(value != " ") {
					serviceActivityData["legacy:hasOutcome"].iri = value;
    				serviceActivityData["legacy:hasCompletedTimestamp"] = getCurrentTime();
    			}
    		}
    		else if(serviceActivityData.hasOwnProperty("legacy:"+name))
    		{
		    	if(name != "hasActivity" && name != "hasCompletedTimestamp")
			    	serviceActivityData["legacy:"+name] = value;
			}
    		else if(name.indexOf('-') != -1)
	    	{
	    		var tempName = "";
	    		if(currentRecordNo != 0)
	    		{
		    		if(name.indexOf('-'+currentRecordNo) != -1)
		    		{
				    	tempName = name.substr(0, name.indexOf("-"));
					    if(tempName == "activityIRI")
						    serviceActivityData["legacy:hasActivity"].iri = value;
					    else
					    {
			    			if(serviceActivityData.hasOwnProperty("legacy:"+tempName))
			    				serviceActivityData["legacy:"+tempName] = value;
			    		}
		    		}
	    		}
	    		else
	    		{
		    		if(name.indexOf('-'+counter) != -1)
		    		{
				    	tempName = name.substr(0, name.indexOf("-"));
					    if(tempName == "activityIRI")
						    serviceActivityData["legacy:hasActivity"].iri = value;
					    else
					    {
			    			if(serviceActivityData.hasOwnProperty("legacy:"+tempName))
			    				serviceActivityData["legacy:"+tempName] = value;
			    		}
		    		}
	    		}
	    	}
		});
	    
	    if(currentRecordNo != 0)
	    	listActivitiesInfo[currentRecordNo] = serviceActivityData;
	    else
	    	listActivitiesInfo[counter] = serviceActivityData;
	
		emptyServiceActivityFields("save", counter);
	    console.log(listActivitiesInfo);
    }
    
    function saveServiceActorData()
    {
	    var serializedSA = $('#ServiceActor').cirmSerializeObject('name');
		var serviceActorData = U.clone(blankServiceActorData);

	    var currentRecordNo = 0;
	    $("#ServiceActor :checkbox:checked").each(function() {
    		var name = this.name;
    		currentRecordNo = name.substring(name.lastIndexOf('-')+1); 
	    });

	    $.each(serializedSA, function(name, value) {
		    if(serviceActorData.atAddress.hasOwnProperty(name)) 
			    serviceActorData.atAddress[name] = value;
		    else if(serviceActorData.hasOwnProperty(name))
		    {
			    if(name != "legacy:hasServiceActor")
			    {
				    if(name == "hasEmailAddress")
						serviceActorData[name] = serviceActorData.hasEmailAddress+value;
				    else
					    serviceActorData[name] = value;
			    }
		    }
    		else if(name.indexOf('-') != -1)
	    	{
	    		var tempName = "";
	    		if(currentRecordNo != 0)
	    		{
		    		if(name.indexOf('-'+currentRecordNo) != -1)
		    		{
				    	tempName = name.substr(0, name.indexOf("-"));
					    if(tempName == "actorIRI")
						    serviceActorData["legacy:hasServiceActor"].iri = value;
					    else
					    {
		    				if(serviceActorData.hasOwnProperty(tempName))
		    					serviceActorData[tempName] = value;
		    			}
		    		}
	    		}
	    		else
	    		{
		    		if(name.indexOf('-'+actorsCounter) != -1)
		    		{
				    	tempName = name.substr(0, name.indexOf("-"));
					    if(tempName == "actorIRI")
						    serviceActorData["legacy:hasServiceActor"].iri = value;
					    else
					    {
		    				if(serviceActorData.hasOwnProperty(tempName))
		    					serviceActorData[tempName] = value;
		    			}
		    		}
	    		}
	    	}

		    
	    }); 
	    
	    if(currentRecordNo != 0)
	    	serviceActorsInfo[currentRecordNo] = serviceActorData;
	    else
	    	serviceActorsInfo[actorsCounter] = serviceActorData;
	    console.log(serviceActorsInfo);

    }

    function addTabs(boid)
    {
	    var ul = $('<ul id = "tabUL">');
	    var li_1 = $('<li><a href="#LegacyEmulatorDiv">Service Request Form</li>');
	    var li_2 = $('<li><a href="#ServiceActor">Service Actors</li>');
	    ul.append(li_1).append(li_2);
	    if(boid)
	    {
		    var li_3 = $('<li><a href="#ServiceActivity">Service Activities</li>');
		    ul.append(li_3);
	    }
		$('#mainApp').prepend(ul).tabs();
    }

    function getServiceActorForm(actorsObj)
    {
	    var participantDiv = $('<div id = "ServiceActor">');
	
	    //var actorsObj = cirm.top.get('/legacy/serviceActors/' + code);
		var actors_renderer = ui.engine.getRenderer(actorsObj.head);
		var dropdown = actors_renderer.apply(ui, [actorsObj]);
		var dropdownlabel = $('<label>'+actorsObj.label+'</label> <br>');
		$(participantDiv).append(dropdownlabel).append(dropdown);
		
		//on change of Service Actor Type - Add a record.
		$(dropdown).change(function() {
		
			var currentValue = this.value;
			var currentText = $('[name="legacy:hasServiceActor"] option:selected').text();
			if(currentText != "Select One")
			{
				var no = addActorRow(currentText, currentValue);
				$(dropdown).attr("disabled", "true");
				$('[name*="actorCheck"]').attr("disabled", "true").removeAttr('checked');
				$('[name="submitActor"]').show();
				$('[name="cancelActor"]').show();
			}
			else
			{
				$('[name=submitActor]').hide();
				$('[name=cancelActor]').hide();
			}
		});
		
		
		var table = $('<br> <table name ="actorTable" border=0> <tbody> </tbody> </table>').hide();

	    var homePhoneNo = $('<br> <label>Home Phone Number</label> <input type="text" name="HomePhoneNumber"/> <br>');
	    var cellPhoneNo = $('<label>Cell Phone Number</label> <input type="text" name="CellPhoneNumber"/> <br>');
	    var businessPhoneNo = $('<label>Business Phone Number</label> <input type="text" name="BusinessPhoneNumber"/> <br>');
	    var otherPhoneNo = $('<label>Other Phone Number</label> <input type="text" name="OtherPhoneNumber"/> <br>');
	    var faxNo = $('<label>Fax Number</label> <input type="text" name="FaxNumber"/> <br>');
	    var email = $('<label>email</label> <input type="text" name="hasEmailAddress"/> <br>');
		
	    var addressLabel = $('<br> <label>Service Actor Address</label> <br>');
	   
	    var participantAddress = cirm.top.get("/ui/new/Street_Address");
	    participantAddress.type = "Street_Address";
	    participantAddress.attributes = {"name":"atAddress"};
	   
	    var addrRenderer = ui.engine.getRenderer(participantAddress.head);
	    var addrForm = addrRenderer.apply(ui, [participantAddress]);
	   
	    var submitParticipant = $('<button type="button" name="submitActor"> Add Service Actor </button> ').hide();
	    var cancelParticipant = $('<button type="button" name="cancelActor"> Cancel Service Actor </button> <br> <br>').hide();

	    submitParticipant.click( function() {
	    	saveServiceActorData();
	    	clearServiceActorForm();
	    });
	    
	    cancelParticipant.click( function() {
	    	--actorsCounter;
    		$('table[name="actorTable"] tr:last').remove();
    		if(actorsCounter == 0)
    			$('table[name="actorTable"]').hide();
	    	clearServiceActorForm();
	    });
	
	    $(participantDiv).append(table)
	    .append(homePhoneNo).append(cellPhoneNo).append(businessPhoneNo).append(otherPhoneNo)
	    .append(faxNo).append(email)
	    .append(addressLabel).append(addrForm).append(submitParticipant).append(cancelParticipant);

	    return participantDiv;
    }
    
	function clearServiceActorForm()
	{
		$('#ServiceActor').each(function() { 
			$(':input', this).each(function() { 
				if(this.type == "select-one")
					$(this)[0].selectedIndex = 0;
				else if(this.name.indexOf('-') == -1) 
					this.value = "";
			});
		});
		$('[name*="actorCheck"]').removeAttr("disabled").removeAttr("checked");
		$('[name^="Name"]').attr("readonly", "true");
		$('[name*="LastName"]').attr("readonly", "true");
		$('[name="legacy:hasServiceActor"]').removeAttr('disabled');
    	$('[name="submitActor"]').hide();
    	$('[name="cancelActor"]').hide();
	}

	function addActorTableHeader()
	{
		var newTR = $('<tr>');
		var checkbox = $('<th> </th>');
	    var actorType = $('<th>Service Actor Type</th>');
		var actorName = $('<th>Company/First Name</th>');
		var actorLastName = $('<th>Last Name</th>');
		newTR.append(checkbox).append(actorType).append(actorName).append(actorLastName);
		$('table[name="actorTable"]').find('tbody').append(newTR);
	}
	
    function addActivityTableHeader()
    {
		var newTR = $('<tr>');
		var checkbox = $('<th> </th>');
	    var activityHead = $('<th>Activity</th>');
		var assignedTo = $('<th>Assigned To</th>');
		var dueDate = $('<th>Due Date</th>');
		newTR.append(checkbox).append(activityHead).append(assignedTo).append(dueDate);
		$('table[name="activityTable"]').find('tbody').append(newTR);
    }
    
    function getCurrentTime()
    {
	    var now = new Date();
	    var month = (now.getMonth()+1);
	    if(month.length == 1)
	    month = "0"+month;
		return now.getFullYear()+"-"+month+"-"+now.getDate()+"T"+now.getHours()+":"+now.getMinutes()+":"+now.getSeconds()+":"+now.getMilliseconds()+"-4:00";
    }
    
    function addActorRow(currentText, currentValue, currentName, currentLastName)
    {
		var no = ++actorsCounter;
		if(actorsCounter > 0)
			$('table[name="actorTable"]').show();
		
		var newTR = $('<tr>');
		var actorCheck = $('<td> <input type="checkbox" name="actorCheck-'+no+'" /> </td>');
		var actorType = $('<td> <input type="hidden" name="actorIRI-'+no+'" value="'+currentValue+'"/> <input type="text" name="actorName-'+no+'" readonly="true" value="'+currentText+'"/> </td>');
		var actorName = "";
		var actorLastName = "";
		if(currentName) 
			actorName = $('<td> <input type="text" name="Name-'+no+'" value="'+currentName+'"/> </td>');
		else
			actorName = $('<td> <input type="text" name="Name-'+no+'" /> </td>');
		if(currentLastName)	
			actorLastName = $('<td> <input type="text" name="LastName-'+no+'" value="'+currentLastName+'" /> </td>');
		else
			actorLastName = $('<td> <input type="text" name="LastName-'+no+'" /> </td>');

		newTR.append(actorCheck).append(actorType).append(actorName).append(actorLastName);
		    $('table[name="actorTable"]').find('tbody').append(newTR);

		$('[name*="actorCheck"]').change(function() {
			 var currentRecordNo = this.name.substring(this.name.lastIndexOf("-")+1);
			 if(this.checked)
			 {
			 	doActorChecked(currentRecordNo);
			 	populateServiceActorData(currentRecordNo);
			 }
			 else
			 {
			 	clearServiceActorForm();
			 }
		});
		
		return no;
    }
    
    function doActorChecked(no)
    {
	 	$('[name="Name-'+no+'"]').removeAttr("readonly");
	 	$('[name="LastName-'+no+'"]').removeAttr("readonly");
		$('[name*="actorCheck"]').not('[name="actorCheck-'+no+'"]').attr("disabled", "true");
		$('[name="legacy:hasServiceActor"]').attr("disabled", "true");
		$('[name="submitActor"]').show();
    }
    
    function doActorNotChecked(no)
    {
		$('[name="Name-'+no+'"]').attr("readonly", "true");
		$('[name="LastName-'+no+'"]').attr("readonly", "true");
		$('[name*="actorCheck"]').removeAttr("disabled");
		$('[name="legacy:hasServiceActor"]').removeAttr("disabled");
		$('[name="submitActor"]').hide();
		$('[name="cancelActor"]').hide();
    }
    
    function addActivityRow(currentText, currentValue, defaultActivity)
    {
		var no = ++counter;
		var newTR = $('<tr>');
		var activityCheck; 
		var isAssignedTo;
		var hasDueDate = $('<td> <input type="text" name="hasDueDate-'+no+'" length="10" size="10" readonly="true"/> </td>');
		var activityName = $('<td> <input type="hidden" name="activityIRI-'+no+'" value="'+currentValue+'"/> <input type="text" name="activityName-'+no+'" readonly="true" value="'+currentText+'"/> </td>');

		if(defaultActivity == "true") {
			activityCheck = $('<td> <input type="checkbox" name="activityCheck-'+no+'" /> </td>');
			isAssignedTo = $('<td> <input type="text" name="isAssignedTo-'+no+'" readonly="true"/> </td>');
			hasDueDate = $('<td> <input type="text" name="hasDueDate-'+no+'" length="10" size="10" readonly="true" /> </td>');
		}
		else {
			activityCheck = $('<td> <input type="checkbox" name="activityCheck-'+no+'" disabled="true"/> </td>');
			isAssignedTo = $('<td> <input type="text" name="isAssignedTo-'+no+'" /> </td>');
		}

		newTR.append(activityCheck).append(activityName).append(isAssignedTo).append(hasDueDate);
		    $('table[name="activityTable"]').find('tbody').append(newTR);

		$('[name*="activityCheck"]').change(function() { 
			var currentRecordNo = this.name.substring(this.name.lastIndexOf("-")+1);
			if(this.checked) {
				var temp = "";
				$.each(listActivitiesInfo[currentRecordNo], function(i,v) {
					if(i == "legacy:hasCreatedDate" || i == "legacy:hasCompletedTimestamp" || i == "legacy:hasDetails")
					{
						var j = i.replace("legacy:","");
						$('[name="'+j+'"]').val(v);
					}
					if(i == "legacy:hasActivity")
					{
						$('[name="hasOutcome"]').empty().append('<option value=" "></option>');
						if($.isArray(allActivities)) {
							$.each(allActivities, function(index, value) {
								if(v.iri == value.iri) {
									if($.isArray(value.hasAllowableOutcome)) {
										$.each(value.hasAllowableOutcome, function(n,val) {
											$('[name="hasOutcome"]').append('<option value="'+val.iri+'">'+val.label+'</option>');
										});
									}
									else {
										$('[name="hasOutcome"]').append('<option value="'+value.hasAllowableOutcome.iri+'">'+value.hasAllowableOutcome.label+'</option>');
									}
								}
							});
						}
						else {
							if(v.iri == allActivities.iri) {
								if($.isArray(allActivities.hasAllowableOutcome)) {
									$.each(allActivities.hasAllowableOutcome, function(n,val) {
										$('[name="hasOutcome"]').append('<option value="'+val.iri+'">'+val.label+'</option>');
									});
								}
								else {
									$('[name="hasOutcome"]').append('<option value="'+allActivities.iri+'">'+allActivities.label+'</option>');
								}
							}
						}
					}
					if(i == "legacy:hasOutcome")
						temp = v.iri;
				});
				enableFields(currentRecordNo, temp);
			}
			else 
				disableFields(currentRecordNo);
		});
	return no;
    }
    
    function enableFields(currentRecordNo, temp)
    {
		if(temp!=" " && temp!="") {
			$('[name="hasOutcome"]').val(temp).attr("disabled", "true");
			$('[name="hasDetails"]').attr("readonly", "true");
			$('[name="isAssignedTo-'+currentRecordNo+'"]').attr("readonly", "true");
		    $('[name="hasDueDate-'+currentRecordNo+'"]').datepicker('destroy');
			$('[name="submitActivity"]').hide();
		}
		else {
			$('[name="hasOutcome"]').val(temp).removeAttr("disabled");
		    $('[name="hasDetails"]').removeAttr("readonly");
		    $('[name="isAssignedTo-'+currentRecordNo+'"]').removeAttr("readonly");
		    $('[name="hasDueDate-'+currentRecordNo+'"]').datepicker();
			$('[name="submitActivity"]').show();
		}
	    $('[name*="activityCheck"]').not('[name="activityCheck-'+currentRecordNo+'"]').attr("disabled", "true");
	    $('[name="hasActivity"]').attr("disabled", "true");
    }
	    
    function disableFields(currentRecordNo)
    {
	    $('[name="hasOutcome"]').removeAttr("disabled").empty().append('<option value=" "></option>');
	    $('[name*="activityCheck"]').removeAttr("disabled");
	    $('[name="isAssignedTo-'+currentRecordNo+'"]').attr("readonly", "true");
	    $('[name="hasDueDate-'+currentRecordNo+'"]').datepicker('destroy');
	    $('[name="hasDetails"]').val("").attr("readonly", "true");
		$('[name="hasCompletedTimestamp"]').val("");
		$('[name="hasCreatedDate"]').val("");
		$('[name="submitActivity"]').hide();
	    $('[name="hasActivity"]').removeAttr("disabled");
    }
	    
    function emptyServiceActivityFields(status, no)
    {
		if(status == "save")
		{
			$('[name*="activityCheck"]').removeAttr('disabled').removeAttr('checked');
			$('[name="hasActivity"]').val(" ").removeAttr('disabled');
			$('[name="isAssignedTo-'+no+'"]').attr("readonly", "true");
			$('[name="hasDueDate-'+no+'"]').datepicker('destroy');
			$('[name="hasCreatedDate"]').val("");
		}
		else if(status == "add")
		{
			$('[name*="activityCheck"]').attr("disabled", "true").removeAttr('checked');
			$('[name="hasDueDate-'+no+'"]').datepicker();
			$('[name="hasCreatedDate"]').val(getCurrentTime());
			$('[name="submitActivity"]').show();
		}
		$('[name="hasOutcome"]').removeAttr("disabled").empty();
		$('[name="hasCompletedTimestamp"]').val("");
		$('[name="hasDetails"]').val("").removeAttr("readonly");
    }
    
	function populateServiceActivityData(v)
	{
		var serviceActivityData = null;
		if(v.hasOutcome)
	    	serviceActivityData = U.clone(blanklistActivitiesInfoWithOutcome);
	    else
	    	serviceActivityData = U.clone(blanklistActivitiesInfo);
		
		$.each(v, function (index, value) {
			if(serviceActivityData.hasOwnProperty("legacy:"+index))
			{
				if(index == "type") {}
				else if(index == "legacy:hasActivity")
					serviceActivityData["legacy:hasActivity"].iri = value.iri;
				else if (index == "hasOutcome")
					serviceActivityData["legacy:hasOutcome"].iri = value.iri;
				else
					serviceActivityData["legacy:"+index] = value;
			}
		});
		
		//Add the row first to the table, this will also increment the counter.
		addActivityRow(v.hasActivity.label, v.hasActivity.iri, "true");
		
		if(v.isAssignedTo) {
			$('[name="isAssignedTo-'+counter+'"]').val(v.isAssignedTo);
		}
		if(v.hasDueDate) {
			$('[name="hasDueDate-'+counter+'"]').val(v.hasDueDate);
		}
		
		//Add the entry to the main list of activities.
		listActivitiesInfo[counter] = serviceActivityData;
		
	}

    function getAllServiceActivities(caseData)
    {
		console.log(allActivities);
		if(caseData.properties.hasServiceActivity) {
			var sa = caseData.properties.hasServiceActivity;
			if($.isArray(sa)) {
				$.each(sa, function(i,v) {
					populateServiceActivityData(v);
				});
			}
			else {
				populateServiceActivityData(sa);
			}
		}
		   
	    var outcome = {};
	    $.each(allActivities, function(i,v) { 
		    $('<option value="'+v.iri+'">'+v.label+'</option>').appendTo('[name="hasActivity"]');
		    outcome[v.iri] = v.hasAllowableOutcome;
	    });
		
	    $('[name="hasActivity"]').change(function() {
	    	$(this).attr("disabled", "true");
			var currentValue = $('[name="hasActivity"]').val();
		    var currentText = $('[name="hasActivity"] option:selected').text();
		    if(currentText != "Select One")
		    {
				var no = addActivityRow(currentText, currentValue, "false");
				emptyServiceActivityFields("add", no);
				if(outcome[currentValue])
				{
					var check = outcome[currentValue];
					$('[name="hasOutcome"]').empty().append('<option value=" "></option>');
					if($.isArray(check))
					{
						$.each(check, function(index,val) {
							$('[name="hasOutcome"]').append('<option value="'+val.iri+'">'+val.label+'</option>');
						});
					}
					else
						$('[name="hasOutcome"]').append('<option value="'+check.iri+'">'+check.label+'</option>');
				}
		    }
		    else {
			    $('[name="submitActivity"]').hide();
		    }
	    });
		
    }
    
    function getServiceActivityForm()
    {
	    var serviceActivityDiv = $('<div id = "ServiceActivity">');

		var hasActivity = $('<br> <label>Activity List</label> <select name="hasActivity"> <option value=" ">Select One</option> </select> <br>');
	    var table = $('<br> <table name ="activityTable" border=0> <tbody> </tbody> </table>');
	    var hasOutcome = $('<br> <label>Outcome</label> <select name="hasOutcome"> <option value =" "></option> </select> <br>');
	    var hasCreatedDate = $('<br> <label>Created</label> <input type="text" name="hasCreatedDate" readonly="true" size="28"/>');
	    var hasCompletedTimestamp = $('<label>Completed</label> <input type="text" name="hasCompletedTimestamp" readonly="true" size="28"/> <br>');
	    var hasDetails = $('<label>Details</label> <textarea name="hasDetails" rows="3"/> <br> ');
   
		var submitActivity = $('<button type="button" name="submitActivity"> Add Service Activity </button> <br> <br>').hide();
 
	    submitActivity.click( function() {
			saveServiceActivityData();
			$(this).hide();
	    });
	   
	    $(serviceActivityDiv).append(hasActivity).append(table)
		    .append(hasOutcome).append(hasCreatedDate).append(hasCompletedTimestamp).append(hasDetails).append(submitActivity);
	   
	    return serviceActivityDiv;
    }
    
    function submitButton(boid)
    {
	    var buttonName;
	    var button;
	    if(boid)
	    {
		    buttonName = "Save Service Request";
			button = $('<button type="button" name="submit">'+buttonName+'</button>');
			button.click( function() {
				return document.getElementById('LegacyEmulatorDiv').actions.updateForm(boid); 
			});
	    }
	    else
	    {
			buttonName = "Submit Service Request";
			button = $('<button type="button" name="submit">'+buttonName+'</button>');
			button.click( function() {
				return document.getElementById('LegacyEmulatorDiv').actions.submitForm();
			});
	    }
        return button;
    }

    function getCaseform(obj, code, boid)
    {
	    var renderer = ui.engine.getRenderer(obj.form.head);
	    var form = renderer.apply(ui, [obj.form]);
        $(form).append('<input type="hidden" name="type" value="legacy:' + code + '">');
        $(form).append('<input type="hidden" name="boid" value="'+obj.form.boid+'">');
        if(obj.form.defaultStatus)
	        $(form [name='hasStatus']).val(obj.form.defaultStatus);
        return form;
    }
    
    function getAlerts(code)
    {
	    var alerts = cirm.top.get("/legacy/alerts/" + code);
	    $.each(alerts.hasAlerts, function(i,v) {
		    $('[name*="'+i+'"]').change(function() {
			    var currentValue = $('[name*="'+i+'"]').val();
			    if(v.hasOwnProperty(currentValue))
				    alert(v[currentValue]);
		    });
		});
    }
    
    function lookupServiceCase(div)
    {
		var searchButton = $('<button type="button" name="search">Search</button>');
		var inputBox = $('<input name="LegacyServiceCaseSearchInput">');
        $(div).append('<br> Search for an SR');
        $(div).append(inputBox).append(searchButton);
        searchButton.click( function() {
			var srID = $('input[name="LegacyServiceCaseSearchInput"]').val();
			var code = "ServiceCase";
			cirm.top.async().get("/legacy/search?id="+srID+"&case=legacy:"+code, {}, function (caseData) {
				console.log(caseData);
				var obj = cirm.top.get("/legacy/ui/" + caseData.type + "?boid=" +caseData.boid);
				if(obj.form.allActivities)
					allActivities = obj.form.allActivities;
				var form = getCaseform(obj, caseData.type, caseData.boid);
				var serviceActorDiv = getServiceActorForm(obj.form.hasServiceActor);
				var serviceActivityDiv = getServiceActivityForm();
				var submitbutton  = submitButton(caseData.boid);
				$('#LegacyEmulatorDiv').empty().append(form).append(submitbutton);
				$('#mainApp').append(serviceActorDiv).append(serviceActivityDiv);
				addActivityTableHeader();
				getAllServiceActivities(caseData);
				addTabs(caseData.boid);
				populateForm(form, caseData);
				getAlerts(caseData.type);
				console.log(serviceActorsInfo);
			});
        });
    }

    function fetchLegacyForm(boid)
    {
		var formData = $('form[name="UIFormLegacySR"]').cirmSerializeObject('name');
		var legacyForm = buildServiceAnswersArray(formData);
		var legacyServiceCaseActors = buildServiceCaseActorsArray();
		legacyForm["legacy:hasServiceCaseActor"] = legacyServiceCaseActors;
  
		if(boid)
		{
			var legacyServiceActivities = buildServiceActivitiesArray();
			console.log(legacyServiceActivities);
			legacyForm["legacy:hasServiceActivity"] = legacyServiceActivities;
		}

		var finalform = {};
		finalform.properties = legacyForm;
		finalform.type = formData.type;
		finalform.boid = formData.boid;
  
		return finalform;
    }
    
    function mainUI()
    {
		if (app.hasTemplate) {
			var div = document.createElement("div");
			div.id = 'LegacyEmulatorDiv';
			components.actions = {
				startNew : function () {
					return "document.getElementById('LegacyEmulatorDiv').actions.doStartNew()";
					//return "document.getElementById('LegacyEmulatorDiv').actions.knockOutStartNew()";
				},
                
                knockOutStartNew : function() {
                	var code = $('input[name="LegacyServiceCaseListInput"]').val();
					var emptyobj = cirm.top.get("/legacy/emptyform/ui/" + code);
					console.log(emptyobj);
					//$('#LegacyEmulatorDiv').empty();
                },
                
				doStartNew : function () {
					var code = $('input[name="LegacyServiceCaseListInput"]').val();
					var obj = cirm.top.get("/legacy/ui/" + code);
					console.log(obj);
					var form = getCaseform(obj, code);
					var serviceActorDiv = getServiceActorForm(obj.form.hasServiceActor);
					var submitbutton  = submitButton();
					$('#LegacyEmulatorDiv').empty().append(form).append(submitbutton);
					$('#mainApp').append(serviceActorDiv); 
					addActorTableHeader();
					addTabs();
					getAlerts(code);
				},

				submitForm : function () { 
					var legacyForm = fetchLegacyForm();
					console.log(legacyForm);
					var result = cirm.top.async().post("/legacy/submit", {data:JSON.stringify(legacyForm)});
					console.log(result.ok);
				},
                   
				updateForm : function (boid) { 
					var legacyForm = fetchLegacyForm(boid);
					console.log(legacyForm);
					var result = cirm.top.async().post("/legacy/update", {data:JSON.stringify(legacyForm)});
					console.log(result.ok);
				}
			};

			div.actions = components.actions;
			$.tmpl(app.hasTemplate.hasContents, components).appendTo(div);
                
			setui(div);
			$(components.LegacyServiceCaseListComponent).appendTo($("#LegacyTypeListInputDiv"));
                
			lookupServiceCase(div);
           
		}
		else
			throw new Error('Legacy Emulator, Missing application top level template');                
    }
    
    function load(appParam, parent) {       
        parentElement = parent;
        app = appParam;
        components = {
            LegacyServiceCaseListComponent: undefined,
            LegacyServiceCaseListInput: cirm.top.delay("/legacy?q=LegacyServiceCaseListInput", function (obj) {
            var renderer = ui.engine.getRenderer(obj[0].type);
            this.LegacyServiceCaseListComponent = renderer.apply(ui, [obj[0]]);
                    return "<div id='LegacyTypeListInputDiv'/>";
            })
        };        
        rest.onObjectReady(components, function() { mainUI(); });
    }
    
    return {
        load: load,
        mainUI:mainUI//,
       // startInquiry:startInquiry
    };
}); 
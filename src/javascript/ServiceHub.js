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
    var urlBase = "http://localhost:8182";
    
    // The parent DOM element where this app resides.
    var parentElement = null;

    // Title of the application
    var title = 'Service Hub';
    
    // The application OWL individual
    var app = null;
    
    // The UI components display on the main, top-level page of this application
    var components = null;
    
    // The current BusinessObject (an Iquiry or ServiceRequest that's in view)
    var currentRequest = null;
    
    // The list of all available inquiries (service requests) to initiate.
    var inquiries = null;
   
    function startInquiry(iri, properties, containingElement, continuation)
    {
        var classname = iri.substring(iri.lastIndexOf('#')+1, iri.length);
        $.getJSON(urlBase + '/op/new/' + classname, function (data) {
                if (properties) {
                    $.extend(data.properties, properties);
                }
                currentRequest = new bo.BusinessObject(data);
                currentRequest.runWorkflowWizard(containingElement, continuation);
        });
    }
    
    function makeInquiriesDropDown() {
        var frm = document.createElement('form');
        var dropdown = document.createElement('select');
        $(frm).append(dropdown);
        $(dropdown).append('<option>-- Make Selection --</option>');
        $.each(inquiries, function(i) {
            var x = inquiries[i];
            var o = document.createElement("option");
            o.innerHTML = x.label;
            o.value = x.iri;
            dropdown.add(o);
        });
        $(dropdown).change(function (ev) { 
                startInquiry(this.value, {}, parentElement, function() {
                        mainUI();
                });
        });
        return frm;
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

    function mainUI() {
            if (app.hasTemplate) {
                var div = document.createElement("div");
                $.tmpl(app.hasTemplate.hasContents, components).appendTo(div);
                setui(div);
                $("#InquiryTypesTopDiv").append(components.InquiryTypesComponent);
                $("#InquiryTableTopDiv").append(components.InquiryTableComponent);
            }
            else
                throw new Error('Missing application top level template');                
    }
    
    function load(appParam, parent) {       
        parentElement = parent;
        app = appParam;
        components = {
            InquiryTypesComponent: undefined,
            InquiryTableComponent: undefined,
            InquiryBrowsePanel: cirm.top.delay("/individuals/InquiryBrowsePanel", function (obj) {
                    var renderer = ui.engine.getRenderer(obj.type);
                    this.InquiryTableComponent = ui.engine.getRenderer(obj.type).apply(ui, [obj]);                    
                    return "<div id='InquiryTableTopDiv'/>";
            }),
            InquiryTypes: cirm.top.delay("/classes/sub?parentClass=Inquiry", function (obj) {
                    inquiries = obj.classes;                    
                    this.InquiryTypesComponent = makeInquiriesDropDown();                    
                    return "<div id='InquiryTypesTopDiv'/>";
            })
        };
        rest.onObjectReady(components, function() { mainUI(); });
    }
    
    return {
        load: load,
        mainUI:mainUI,
        startInquiry:startInquiry
    };
}); 
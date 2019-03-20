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
define(["jquery", "rest", "cirm", "workflow"], function($, rest, cirm, workflow)   {
    var urlBase = "http://localhost:8182";
    var parentElement = null;
    var title = 'CiRM Business Rules Manager';    
    var inquiries = null;
    var visualizationId = "BusinessWorkflowViz";

    function addVisualizationDiv  () {
        var div = document.createElement("div");
        div.setAttribute("id", visualizationId);
        $(div).css({ width:'1400px',  height:'1000px'}); 
        $(parentElement).append(div);
    }
    
    function inquirySelected (iri) {
        var classname = iri.substring(iri.lastIndexOf('#')+1, iri.length)
        var workflowData = cirm.meta.get("/workflow/" + classname);
        var W = new workflow.Workflow(workflowData);
        $("#" + visualizationId).empty();
        W.show(visualizationId);
    }

    function makeInquiriesDropDown  () {
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
            if (this.value.charAt(0) != "-")
                inquirySelected(this.value);
        });
        return frm;
    }

    function load (app, parent) {
        parentElement = parent;
        var components = {
            InquiryTypes: cirm.top.delay("/classes/sub?parentClass=Inquiry", function (obj) {
                    inquiries = obj.classes;                    
                    this.InquiryTypesComponent = makeInquiriesDropDown();                    
                    return "<div id='InquiryTypesTopDiv'/>";
            })
        };
        rest.onObjectReady(components, function() {
            var div = document.createElement("div");
            $(div).append(components.InquiryTypesComponent);
            $(parentElement).empty();
            $(parentElement).append(div);
            addVisualizationDiv();            
        });
    }

    return {
        load:load
    }
});
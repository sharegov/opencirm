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
// Initialize/bootstrap environment, calling require 
var initialized = false;
modulesInGlobalNamespace  = true;
require({
  baseUrl: "/javascript",
  paths: {
    "jquery": "jquery-1.7.1.min",
    "jquery-ui":"jquery-ui-1.8.17.custom.min",
    "jquery-jec":"jquery.jec-1.3.3",
    "jquery-upload":"jquery.upload-1.0.2",
    "jquery-tmpl":"jquery.tmpl.min",
    "jquery-autoresize": "jquery.autoresize",
    "jquery-marquee":"jquery.marquee.min",
    "jquery-cookie":"jquery.cookie",
    "jquery-knockout":"knockout-jquery-ui-widget",
    "jquery-datatables":"jquery.dataTables.1.9.0.min",
    "jquery-dt-ko-binding":"knockout.bindings.dataTables",
    "chai":"http://chaijs.com/chai",    
    "rest":"http://sharegov.org/jslib/rest",
    "U":"http://sharegov.org/jslib/U",
    "T":"../test/testfix"
  }, 
  shim : {
    'jquery':{ deps:[], exports:'$'},
    'jquery-ui':['jquery']
  }
  , urlArgs: "cache_bust=" + (new Date()).getTime()
}, [ "jquery", "U", "rest", "T",  "cirm", "legacy", "sreditor", "answerhub", 
     "jquery-ui", "jquery-tmpl", "jquery-autoresize", 
           "jquery-marquee", "jquery-cookie", "jquery-jec",
           "jquery-upload", "jquery-knockout", "jquery-datatables", "jquery-dt-ko-binding", "chai"],
function ($, U, rest, T, cirm) {
  var should = require('chai').should() ;
  // Define all needed test fixtures
  T.fixt("UserLogin").do(function () {
//    console.log(cirm.user, cirm.user.ensureLoggedin());
  }).undo(function () {
  });
  T.fixt("SpaythonResource").do(function () {
    var spayslots = cirm.op.get('/individual/SpaythonSlots');
    if (spayslots.ok) return;
    var A = [
        {
            iri:"legacy:ASSPTHN_DATEOFEV_ASDEVT_ASDEVT4B",
            hasQuantity:{type:"xsd:long", literal:10}
        }
    ];
    var x = {
      iri:"http://www.miamidade.gov/cirm/legacy#SpaythonSlots",
      type:"TimeSlotResource",
      hasMember:A
    };
    console.log('posting object to DB', x);
    cirm.op.postObject('/individual/SpaythonSlots',  x); 
  }).undo(function () {
     cirm.op.del('/individual/SpaythonSlots');
  });  
  initialized = true;
});

wait(function () { return initialized; }, 30*1000);

// == SECTION Persist Arbitrary Ontology Individuals

// @UserLogin, SpaythonResource

var spayslots = cirm.op.get('/individual/SpaythonSlots');
spayslots.ok.should.be.true;
spayslots = spayslots.data;

var spaytype = cirm.refs.serviceCases['http://www.miamidade.gov/cirm/legacy#ASSPTHN'];
// Create new SR with this type and check resources have been perused.

//var sr = spaytype.make();

//cirm.top.postObject('/legacy/new', sr);

// => true



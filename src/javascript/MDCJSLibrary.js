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
//(function() {
//
//	function getScript(src) {
//		document.write('<' + 'script src="' + src + '"'
//				+ ' type="text/javascript"><' + '/script>');
//
//	}
//
//	getScript("jquery-1.6.2.min.js");
//
//})();


/**
 * @namespace Top level namespace.
 */
var MDCJSLIB = MDCJSLIB || {};

MDCJSLIB.namespace = function(ns_string) {
	var parts = ns_string.split('.'), parent = MDCJSLIB, i;

	// strip redundant leading global
	if (parts[0] === "MDCJSLIB") {
		parts = parts.slice(1);
	}

	for (i = 0; i < parts.length; i += 1) {
		// create a property if it does not exist.
		if (typeof parent[parts[i]] === "undefined") {
			parent[parts[i]] = {};
		}

		parent = parent[parts[i]];
	}

	return parent;
};

//declare the ajax module module
MDCJSLIB.namespace('MDCJSLIB.modules.ajax');

//declare the contentmanager module
MDCJSLIB.namespace('MDCJSLIB.modules.contentmanager');

//declare the gis module
MDCJSLIB.namespace('MDCJSLIB.modules.gis');

// declare the usermanager module
MDCJSLIB.namespace('MDCJSLIB.modules.usermanager');

//declare the domain module
MDCJSLIB.namespace('MDCJSLIB.modules.domain');

// define usermanager public interface
MDCJSLIB.modules.usermanager = (function() {

	// dependenciesø,
	// var ajaxcalls = MDCJSLIB.modules.utilities.ajaxobject;

	// private methods
	var getPerson = function(iri, callback) {
		$.ajax({
			type : "GET",
			dataType : "jsonp",
			url : "http://ols00053:8182/users/all?name=Abbas",
			success : function(data) {
				callback(data);
			}
		});
	};

	// public API
	return {
		getPerson : getPerson
	};

}());



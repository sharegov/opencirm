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
define(["jquery"], function($) {

if (!window.console) window.console = {};
if (!window.console.log) window.console.log = function () { };
// Let's put a global trace function that can be used in promises to 
// simply trace results/error of AJAX calls.
function trace(x) { console.log(x); }

function beget(o, extension) {
    function F() {}
    F.prototype = o;
    var x = new F();
    if (extension) 
        $.extend(x, extension);
    return x;
}

function defClass(obj) {
    f = new Function();
    for (x in obj) {
        f.prototype[x] = obj[x];
    }
    return f;
}

function clone(obj) {
    return $.extend(true, {}, obj);
}

// Create a set (as a JavaScript object) from the arguments of this function. The arguments become keys
// of a JavaScript object where all the values are the boolean 'true'.
function set () {
    var result = {};
    var i;
    for (var i = 0; i < arguments.length; i++)
      result[arguments[i]] = true;
    return result;
}


// The following is taken from http://www.amirharel.com/. jQuery has something that copies
// over properties. The below is more class-oriented and deals only with methods at the 
// prototype level. So the two are kind of complimentary and can be used jointly.
function extend(sub){
	var sups = [];
	var single = function(sub,sup){
		var thinF = function(){};
		thinF.prototype = sup.prototype;
		sub.prototype = new thinF();
		sub.prototype.constructor = sub;
		sub.sup = sup.prototype;
		if( sup.prototype && sup.prototype.constructor == Object.prototype.constructor ){
			sup.prototype.constructor = sup;
		}
	}

	var multi = function(sub,sup){
		var proto = sup.prototype;
		if (!proto) return;
		for( var f in proto ){
			if( f != "constructor" && typeof proto[f] == "function" ){
				if( sub.prototype[f] == undefined ){
					sub.prototype[f] = (function(method) { return function(){
						return method.apply(this,arguments);
					}; })(proto[f]);
				}
			}
			else if (f != "constructor") {
			    sub.prototype[f] = proto[f];
			}
		}
	}

	if( arguments.length < 2 ) return;
	single(sub,arguments[1]);
	sups.push(arguments[1]);
	for( var i=2; i<arguments.length;i++){
		multi(sub,arguments[i]);
		sups.push(arguments[i]);
	}
	sub.prototype.callSuper = function(fnc){
		var len = sups.length;
		for( var i=0;i<len;i++){
			var sup = sups[i];
			if( sup.prototype && (fnc in  sup.prototype) && (typeof sup.prototype[fnc] == "function") ){
				return sup.prototype[fnc].apply(this,[].splice.call(arguments,1));
			}
		}
		return null;
	}
}


        
//Returns true if it is a DOM node
function isDOMNode(o){
  return (
    typeof Node === "object" ? o instanceof Node : 
    typeof o === "object" && typeof o.nodeType === "number" && typeof o.nodeName==="string"
  );
}

//Returns true if it is a DOM element    
function isDOMElement(o){
  return (
    typeof HTMLElement === "object" ? o instanceof HTMLElement : //DOM2
    typeof o === "object" && o.nodeType === 1 && typeof o.nodeName==="string"
);
}

function isArray(o) {
  return Object.prototype.toString.call(o) === '[object Array]'; 
}

function ensureArray(o) {
  if (typeof o == "undefined")
      return [];
  else if (isArray(o))
      return o;
  else
      return [o]; 
}

function isEmptyString(o) {
	return (o === undefined || o === null || $.trim(o) === "");
}

function isString(o) {
	return typeof o == "string";
}


function getTimeInMillis(o) {
	return Date.parse(o);
	//return new Date(o).getTime();
}

function getCurrentDate() {
	var nowClient = new Date().getTime();
	//If clientTime is 120 minutes old, then recalculate it.
	if(Math.abs((nowClient - cirm.refs.time.clientTime)/(1000 * 60 )) > 120) {
		getServerDate();
	}
	return new Date(new Date().getTime() - cirm.refs.time.delta);
}

function getFullYear() {
	return getCurrentDate().getFullYear();
}

function getFullYearAsString() {
	return getFullYear().toString();
}

// Calls the server endpoint and refreshes cirm.refs.time properties 
function getServerDate() {
	var serverTime = cirm.top.get('/op/time');
	if(serverTime.ok) {
	    var clientTime = new Date().getTime();
	    cirm.refs.time.serverTime = serverTime.time;
	    cirm.refs.time.clientTime = clientTime;
	    cirm.refs.time.delta = clientTime - serverTime.time;
	}
	else {
    	console.log("Fetching Server Time failed");
	}
}

/**
 * Takes a JSON object where property names contain the dot character "." and
 * transforms those properties into nested structures. For example an object that
 * looks like { 'a' : 1, 'x.a' : 2, 'x.b' : 3, 'x.c.t' : 'foo', 'x.c.s' : 'bar'} is transformed into
 * {'a' : 1 'x': {'a': 2, 'b':3, 'c' : {'t':'foo', 's':'bar'}}}. This is helpful in mapping a form
 * of a complex object, with nested structures, into a complex JSON object. The id's of
 * the form elements are taken initially as property names of a flat structure, which is 
 * then "nestified". 
 */
function nestify(obj) {
    if (isArray(obj))
        //return $(obj).map(function () { return nestify(this); });
    	return $.map(obj, function (v,i) { return nestify(v); });
    else if (obj == null || typeof(obj) != "object")
        return obj;
    var clone = new Object();
    for (name in obj) { 
        var A = name.split("."); 
        if (A.length == 1) 
            clone[name] = nestify(obj[name]);
        else {
            var curr = clone;
            while (A.length > 1) {
                var next = curr[A[0]];
                if (!next) {
                    next = new Object();
                    curr[A[0]] = next;
                }
                else if (typeof next != "object")
                    throw new Error("Conflict between scalar value and a structured object for " + name);
                curr = next;
                A.shift();
            }
            curr[A[0]] = nestify(obj[name]);
        }
    }
    return clone;
}

// Implement a recursive visit of all object properties 
function objectRecurse(obj, f) {	
    if (typeof obj != "object" || !obj) return obj;
    function impl(obj, f) {
        if (obj.donerecurse) {
            return;
        }
        obj['donerecurse'] = true;
        $.each(obj, function(n,v) {
            f(n,v,obj);
            if (v != null && typeof v == "object") { impl(v, f); }
        });        
    };
    impl(obj,f);
    function cleanup(obj) {
    	if (obj && obj.donerecurse !== undefined)
    	    delete obj.donerecurse;
    	else
    	    return;
        $.each(obj, function(n,v) { 
        	if (v != null)
        		if(typeof v == "object")
        			cleanup(v); 
        		else if(typeof v == "function") 
        			cleanup(v());
        });
    };    
    cleanup(obj);    
    return obj;
}

// Utility functions for manipulating IRIs
var IRI = {
    name : function(iri) {
        return iri.split('#')[1];
    },
    type : function(iri) {
        var A = iri.split('#')[0].split('/');
        return A[A.length-2];            
    },
    id : function(iri) {
        var A = iri.split('#')[0].split('/');
        return A[A.length-1];                    
    }
};

/*
 * Date Format 1.2.3
 * (c) 2007-2009 Steven Levithan <stevenlevithan.com>
 * MIT license
 *
 * Includes enhancements by Scott Trenda <scott.trenda.net>
 * and Kris Kowal <cixar.com/~kris.kowal/>
 *
 * Accepts a date, a mask, or a date and a mask.
 * Returns a formatted version of the given date.
 * The date defaults to the current date/time.
 * The mask defaults to dateFormat.masks.default.
 */

var dateFormat = function () {
	var	token = /d{1,4}|m{1,4}|yy(?:yy)?|([HhMsTt])\1?|[LloSZ]|"[^"]*"|'[^']*'/g,
		timezone = /\b(?:[PMCEA][SDP]T|(?:Pacific|Mountain|Central|Eastern|Atlantic) (?:Standard|Daylight|Prevailing) Time|(?:GMT|UTC)(?:[-+]\d{4})?)\b/g,
		timezoneClip = /[^-+\dA-Z]/g,
		pad = function (val, len) {
			val = String(val);
			len = len || 2;
			while (val.length < len) val = "0" + val;
			return val;
		};

	// Regexes and supporting functions are cached through closure
	return function (date, mask, utc) {
		var dF = dateFormat;

		// You can't provide utc if you skip other args (use the "UTC:" mask prefix)
		if (arguments.length == 1 && Object.prototype.toString.call(date) == "[object String]" && !/\d/.test(date)) {
			mask = date;
			date = undefined;
		}

		// Passing date through Date applies Date.parse, if necessary
		date = date ? new Date(date) : new Date;
		if (isNaN(date)) throw SyntaxError("invalid date");

		mask = String(dF.masks[mask] || mask || dF.masks["default"]);

		// Allow setting the utc argument via the mask
		if (mask.slice(0, 4) == "UTC:") {
			mask = mask.slice(4);
			utc = true;
		}

		var	_ = utc ? "getUTC" : "get",
			d = date[_ + "Date"](),
			D = date[_ + "Day"](),
			m = date[_ + "Month"](),
			y = date[_ + "FullYear"](),
			H = date[_ + "Hours"](),
			M = date[_ + "Minutes"](),
			s = date[_ + "Seconds"](),
			L = date[_ + "Milliseconds"](),
			o = utc ? 0 : date.getTimezoneOffset(),
			flags = {
				d:    d,
				dd:   pad(d),
				ddd:  dF.i18n.dayNames[D],
				dddd: dF.i18n.dayNames[D + 7],
				m:    m + 1,
				mm:   pad(m + 1),
				mmm:  dF.i18n.monthNames[m],
				mmmm: dF.i18n.monthNames[m + 12],
				yy:   String(y).slice(2),
				yyyy: y,
				h:    H % 12 || 12,
				hh:   pad(H % 12 || 12),
				H:    H,
				HH:   pad(H),
				M:    M,
				MM:   pad(M),
				s:    s,
				ss:   pad(s),
				l:    pad(L, 3),
				L:    pad(L > 99 ? Math.round(L / 10) : L),
				t:    H < 12 ? "a"  : "p",
				tt:   H < 12 ? "am" : "pm",
				T:    H < 12 ? "A"  : "P",
				TT:   H < 12 ? "AM" : "PM",
				Z:    utc ? "UTC" : (String(date).match(timezone) || [""]).pop().replace(timezoneClip, ""),
				o:    (o > 0 ? "-" : "+") + pad(Math.floor(Math.abs(o) / 60) * 100 + Math.abs(o) % 60, 4),
				S:    ["th", "st", "nd", "rd"][d % 10 > 3 ? 0 : (d % 100 - d % 10 != 10) * d % 10]
			};

		return mask.replace(token, function ($0) {
			return $0 in flags ? flags[$0] : $0.slice(1, $0.length - 1);
		});
	};
}();

// Some common format strings
dateFormat.masks = {
	"default":      "ddd mmm dd yyyy HH:MM:ss",
	shortDate:      "m/d/yy",
	mediumDate:     "mmm d, yyyy",
	longDate:       "mmmm d, yyyy",
	fullDate:       "dddd, mmmm d, yyyy",
	shortTime:      "h:MM TT",
	mediumTime:     "h:MM:ss TT",
	longTime:       "h:MM:ss TT Z",
	isoDate:        "yyyy-mm-dd",
	isoTime:        "HH:MM:ss",
	isoDateTime:    "yyyy-mm-dd'T'HH:MM:ss",
	isoUtcDateTime: "UTC:yyyy-mm-dd'T'HH:MM:ss'Z'"
};

// Internationalization strings
dateFormat.i18n = {
	dayNames: [
		"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat",
		"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
	],
	monthNames: [
		"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
		"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"
	]
};

// For convenience...
Date.prototype.format = function (mask, utc) {
	return dateFormat(this, mask, utc);
};

Date.prototype.setISO8601 = function (string) {
    var regexp = "([0-9]{4})(-([0-9]{2})(-([0-9]{2})" +
        "(T([0-9]{2}):([0-9]{2})(:([0-9]{2})(\.([0-9]+))?)?" +
        "(Z|(([-+])([0-9]{2}):([0-9]{2})))?)?)?)?";
    var d = string.match(new RegExp(regexp));

    var offset = 0;
    var date = new Date(d[1], 0, 1);

    if (d[3]) { date.setMonth(d[3] - 1); }
    if (d[5]) { date.setDate(d[5]); }
    if (d[7]) { date.setHours(d[7]); }
    if (d[8]) { date.setMinutes(d[8]); }
    if (d[10]) { date.setSeconds(d[10]); }
    if (d[12]) { date.setMilliseconds(Number("0." + d[12]) * 1000); }
    if (d[14]) {
        offset = (Number(d[16]) * 60) + Number(d[17]);
        offset *= ((d[15] == '-') ? 1 : -1);
    }

    offset -= date.getTimezoneOffset();
    time = (Number(date) + (offset * 60 * 1000));
    this.setTime(Number(time));
}

Date.prototype.asISODateString = function(z) {
    var d = this;
    function pad(n){
        return n < 10 ? '0'+n : n
    }
    var result = d.getUTCFullYear()+'-'
    + pad(d.getUTCMonth()+1)+'-'
    + pad(d.getUTCDate())+'T'
    + pad(d.getUTCHours())+':'
    + pad(d.getUTCMinutes())+':'
    + pad(d.getUTCSeconds());
    
    if(z)
    	result = result + 'Z';
    else
    	result = result +'.000-0000';
    return result; 
}

function makeObservable(n,v,parent) {
    if (isArray(parent) || ko.isObservable(v))
        return; 
    else if(n == "hasServiceCaseActor" || n == "hasServiceActivity")
        parent[n] = ko.observableArray(v);
    else if (isArray(parent[n]))
        parent[n] = ko.observableArray(v);
    else
        parent[n] = ko.observable(v);		
}

	function makeObservableTrimmed(n,v,parent) {
    if (isArray(parent) || ko.isObservable(v))
        return; 
    else if(n == "hasServiceCaseActor" || n == "hasServiceActivity")
        parent[n] = ko.observableArray(v);
    else if (isArray(parent[n]))
        parent[n] = ko.observableArray(v);
    else
        parent[n] = ko.observable(v).trimmed();		
	}
	
// TMP solution to IRI instead of object problem
function resolveIris(x) {
    var map = {};
    objectRecurse(x, function(n,v,p) {
        if (n == "iri")
            map[v] = p;
    });
    objectRecurse(x, function(n,v,p) {
        if (typeof v == "string" && 
            n != "iri" &&
            v.indexOf("http://www.miamidade.gov/") == 0 &&
            map[v] !== undefined)
            p[n] = map[v];			
    });
    //console.log('Full graph ready', x);    
}

// Boris Aug 23 2014, new client-side IRI resolution when
// new JSON<->OWL takes effect. There should probably be a separate
// module to deal with OWL...
function newResolveIris(x) {
    var map = {};
    objectRecurse(x, function(n,v,p) {
        if (n == "iri" && p.type !== undefined)
            map[v] = p;
    });
    objectRecurse(x, function(n,v,p) {
        if (typeof v == "object" &&
            v != null &&
            v.iri !== undefined &&
            v.type === undefined &&
            map[v.iri] !== undefined)
            p[n] = map[v.iri];			
    });
    //console.log('Full graph ready', x);    
}

function addLeadingZeroes(n, length) {
	var result = ''+n;
	while(result.length < length) {
		result = '0'+result;
	}
	return result;
}

/**
 * This function performs a deep (recursive) comparison between two JSON structures  
 * and returns a map with all of their differences. 
 *
 * The elements of those structures are expected to be JSON: boolean, number, object, array, null or
 * string. For the sake of making it easier to compare full JavaScript objects, we allow functions to be
 * compared and use the 'toString()' representation to do the comparison.
 *
 * The returned object is map of paths in dotted notation (a.b.x[i].c. etc...) to
 * differences where a "difference" is a string constant indicating what exactly is
 * different as follows:
 *
 * type_mismatch : the Json elements are of different type.
 * value_mismatch: the primitive values on the left and right are not the same (see also arrays comparison)
 * property_missing: the left hand side has a property that the right-hand side doesn't have
 * property_extra: the left hand side does NOT have a property that's in the right-hand side 
 * element_missing: the left-hand side array has an element that the right-hand side doesn't
 * element_extra: the right-hand side array has an element missing from the left-hand side
 *
 * Note that there is an asymmetry in the comparison. We are doing from the perspective of 
 * the left-hand side. So a "property missing" means that it's missing in the left-hand side, but
 * present in the right-hand side.
 *
 * Comparing arrays is a bit tricky. Arrays may represent sets or tuples (ordered collections). 
 * When they represent sets, we allow the user to specify how objects within an array
 * are identified. That's the purpose of the keymap argument: it is a map of JSON paths 
 * to JSON maps (both in dotted notation) that says for which arrays which keys to use as identifying
 * properties. For example if you {"foo.A": "x.name"} in the keymap, that means that we expect the
 * top-level objects being compared to have an array at "foo.A" and that array to contain JSON objects
 * that have a property x that has a property name that identifies the object. For example, a top level
 * structure could look like this: {foo:{A:[..., { x: {name:'blabla'}}, ...]}}. A lot of nesting here, basically
 * we are just navigating using arbitrarily deep path that's all. If the array-as-sets contains primitive
 * values then use "" as the key. For example, the keymap could contain an entry {"foo.A": ""} with
 * the structure looking like this: {foo: { A : [23,23,'asdsd'] }}. 
 *
 * The function handles recursive structures correctly. 
 */
function jsonDiff(left, right, keymap) {
    var diffs = {};
    var done = {};
    function implObjects(left, right, path) {
        $.each(left, function (n,v) {
           var newpath = path + (path == "" ? "" : ".") + n;
           if (!right.hasOwnProperty(n))
               diffs[newpath] = 'property_missing';
           else
             impl(left[n], right[n], newpath);
        });
        $.each(right, function (n,v) {
           var newpath = path + (path == "" ? "" : ".") + n;                
           if (!left.hasOwnProperty(n))
               diffs[newpath] = 'property_extra';
        });
    }
    function resolvekey(key,x) {
        var path = key.split(".")
        while (path.length > 0) {
            key = path.shift();
            var m = key.match(/(\w+)\[(\d+)\]/);
            x = m ? x[m[1]][m[2]] : x[key]; 
        }
        return x;
    }
    function implArrays(left, right, path) {
        var key = keymap[path];
        if (!key) {
            // we're more brutal without a key:
            if (left.length != right.length)
                diffs[path] = 'value_mismatch';
            else $.each(left, function (idx, value)  {
                impl(left[idx], right[idx], path + "[" + idx + "]");
            });
        }
        else {
            $.each(left, function (i,v) {
               var keyvalue = resolvekey(key, v);
               var newpath = path + "[" + i + "]";
               var A = $.grep(right, function(x) { return resolvekey(key, x) == keyvalue; });
               if (A.length > 0)
                 impl(v, A[0], newpath);
               else
                   diffs[newpath] = 'element_missing';
            });
            $.each(right, function (i,v) {
               var keyvalue = resolvekey(key, v);
               var newpath = path + "[" + i + "]";
               var A = $.grep(left, function(x) { return resolvekey(key, x) == keyvalue; });
               if (A.length == 0)
                   diffs[newpath] = 'element_extra';
            });            
        }
    }
    function impl(left, right, path) {
        if (done[path])
            return;
        done[path] = true;
        if (typeof left != typeof right) {
            diffs[path ] = 'type_mismatch';
            return;
        }
        switch (typeof left) {
          case "boolean": case "string": case "number":
            if (left != right) diffs[path] = 'value_mismatch';
            break;
          case "function":
            if (left.toString() != right.toString()) diffs[path] = 'value_mismatch';
            break;
          case "object":
            if (left instanceof Array)
                if (right instanceof Array)
                    implArrays(left, right, path);
                else
                    diffs[path] = 'type_mismatch';
            else if (right instanceof Array)
                diffs[path] = 'type_mismatch';
            else if (left == null) {
                if (right != null)
                  diffs[path] = 'value_mismatch';
            }
            else if (right == null)
                diffs[path] = 'value_mismatch';
            else
                implObjects(left, right, path);
            break;
        }
    }
    impl(left, right, "");
    return diffs;
}

/*
 * --------------------------------------------------------------------
 * jQuery-Plugin - $.download - allows for simple get/post requests for files
 * by Scott Jehl, scott@filamentgroup.com
 * http://www.filamentgroup.com
 * reference article: http://www.filamentgroup.com/lab/jquery_plugin_for_requesting_ajax_like_file_downloads/
 * Copyright (c) 2008 Filament Group, Inc
 * Dual licensed under the MIT (filamentgroup.com/examples/mit-license.txt) and GPL (filamentgroup.com/examples/gpl-license.txt) licenses.
 * --------------------------------------------------------------------
 */
function download(url, data, newWindow, method) {
	//url and data options required
	if( url && data ){ 
		//data can be string of parameters or array/object
		data = typeof data == 'string' ? data : $.param(data);
		//split params into form inputs
		var inputs = '';
		$.each(data.split('&'), function() { 
			var pair = this.split('=');
			inputs+='<input type="hidden" name="'+ pair[0] +'" value="'+ pair[1] +'" />'; 
		});
		var formpopup = 'formpopup_' + (new Date()).getTime();
		function onSubmit()
		{
			window.open('about:blank', formpopup, 'width=1000, height=700, resizable=yes, scrollbars=yes');
		}
		//send request
		var tempForm = $('<form action="'+ url +'" method="'+ (method||'post') +'">'+inputs+'</form>');
		if(newWindow)
		{
			tempForm.attr("target", formpopup);
			tempForm.attr("submit", onSubmit());
		}
		tempForm.appendTo('body').submit().remove();
	};
};


// Cross-browser support for property getters and property setters
try {
   if (!Object.prototype.__defineGetter__ &&
        Object.defineProperty({},"x",{get: function(){return true}}).x) {		
		// Setter	
		Object.defineProperty(
			Object.prototype,
			"__defineSetter__",
			{
				enumerable: false,
				configurable: true,
				value: function(name,func){
					Object.defineProperty(this,name,{set:func,enumerable: true,configurable: true});					
					// Adding the property to the list (for __lookupSetter__)
					if(!this.setters) this.setters = {};
					this.setters[name] = func;
				}
			}
		);
		
		// Lookupsetter
		Object.defineProperty(
			Object.prototype,
			"__lookupSetter__",
			{
				enumerable: false,
				configurable: true,
				value: function(name){
					if(!this.setters) return false;
					return this.setters[name];
				}
			}
		);
		
		// Getter	
		Object.defineProperty(
			Object.prototype,
			"__defineGetter__",
			{
				enumerable: false,
				configurable: true,
				value: function(name,func){
					Object.defineProperty(this,name,{get:func,enumerable: true,configurable: true});					
					// Adding the property to the list (for __lookupSetter__)
					if(!this.getters) this.getters = {};
					this.getters[name] = func;
				}
			}
		);
		
		// Lookupgetter
		Object.defineProperty(
			Object.prototype,
			"__lookupGetter__",
			{
				enumerable: false,
				configurable: true,
				value: function(name){
					if(!this.getters) return false;
					return this.getters[name];
				}
			}
		);
   }
} catch(defPropException) {/*Do nothing if an exception occurs*/};


var connectivity = {
    offline : false,
    goOnline: function(e) {
        connectivity.offline = false;
        connectivity.app_status(true);
    },
    goOffline: function(e) {
        connectivity.offline=true;
        connectivity.app_status(false);
    },    
    app_status : function (online) {
        // Toggle online/offline visiually in the app hearder
        if (online) {
            $('#status_container').removeClass('app_offline')
            $('#app_status').html('online')
        } else {
            $('#status_container').addClass('app_offline')
            $('#app_status').html('offline')
        }
    }
};

window.addEventListener("offline", connectivity.goOnline, false);    
window.addEventListener("online", connectivity.goOffline, false);

//
// Knockout observable.trimmed() on write extension

try 
{
	ko.subscribable.fn.trimmed = function() {
	    return ko.computed({
	        read: function() {
	            return this();
	        },
	        write: function(value) {
	        	if (U.isString(value))
    	            this($.trim(value));
	        	else
	        		this(value);	        	
	        },
	        owner: this
	    });
	};
} catch (error) 
{
	console.log(error)
}





var M = {
    IRI:IRI,
    beget:beget,
    set:set,
    extend:extend,
    clone:clone,
    defClass:defClass,
    isDOMNode:isDOMNode,
    isDOMElement:isDOMElement,
    isArray:isArray,
    ensureArray:ensureArray,
    isEmptyString:isEmptyString,
    isString:isString,
    getTimeInMillis:getTimeInMillis,
    getCurrentDate:getCurrentDate,
    getFullYear:getFullYear,
    getFullYearAsString:getFullYearAsString,
    nestify:nestify,
    visit:objectRecurse,
    resolveIris:resolveIris,
    addLeadingZeroes:addLeadingZeroes,
    makeObservable:makeObservable,
    makeObservableTrimmed:makeObservableTrimmed,
    download:download,
    baseurl : document.location.protocol + '//' + document.location.host,
    connectivity:connectivity,
    offline: function () { return connectivity.offline; }
};
if (modulesInGlobalNamespace)
    window.U = M;
return M;
});

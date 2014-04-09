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
define(["jquery", "U"], function($, U) {

function AsyncCall(args, callback) {
    this.args = args;
    this.callback = callback;
    this.call = function() {
        if (U.offline()) 
            throw "Attempt to make AJAX call while offline"; // catch it at least, until we handle it properly        
        var args = this.args;
        var cached = thecache[args.url];
        if (cached != null) {
            if (cached.callbacks.indexOf(callback) < 0)
                cached.callbacks.push(callback);
            if (typeof cached.data != "undefined")
                return cached.data;
            else if (!cached.pending)
            {
                cached.pending = true;
                $.ajax($.extend(this.args, { async : true, success:function(x) {
                        cached.data = x;
                        cached.pending = false;
                        $.each(cached.callbacks, function(i,f) {
                                try { f(cached.data); } catch (t) { console.log(t); }
                        });
                }}));
            }
        }
        else
        {
            $.ajax($.extend(this.args, { async : true, success:callback}));
        }
    };
    return this;
}

function asyncCall(args, callback) { return new AsyncCall(args, callback); }

// Make multiple asynchronous calls and call the 'callback' only when all are done
// obj contains properties that are AsyncCall instances...
// given that now jQuery has this 'when' function that does the same thing, we should 
// get rid of this
function onObjectReady(obj, callback) { 
    var synchronizer = {
        remaining: undefined,
        done: function() {
            if (! (--this.remaining) ) callback.apply(obj);
        }
    };
    var calls = [];
    for (var prop in obj) {
      if (!obj.hasOwnProperty(prop)) continue;
      var val = obj[prop];
      if (! (val instanceof AsyncCall)) continue;
      (function(prop, val) {
        var onsuccess = function(data) {
            if (val.callback) {                
                obj[prop] = val.callback.apply(obj, [data]);
            }
            else
                obj[prop] = data;
            synchronizer.done();
        }
        var onerror = function(e) { 
            obj[prop] = e;
            synchronizer.done();
        }
        var thecall = function() {
            var args = $.extend(val.args, { async : true, 
                                            success:onsuccess,
                                            error:onerror });
            $.ajax(val.args);
        }
        calls.push(thecall);
      })(prop, val);
    }
    synchronizer.remaining = calls.length;
    for (var i = 0; i < calls.length; i++) {
        calls[i]();
    }
}
        
function RESTClient(urlbase, options)
{
    this.urlbase = urlbase != null && urlbase !== undefined ? urlbase : "http://localhost:8182";    
    this.options = { async: false, parseJSON: true};
    this.offlinehook = function () {
        console.log('attempt to make AJAX call offline', arguments);
        return null;
    };    
    if (options) { 
        $.extend(this.options, options);
        if (options.offlinehook) this.offlinehook = options.offlinehook;
    }
     
    RESTClient.prototype.onoffline = function(f) { this.offlinehook = f; return this; }
    
    RESTClient.prototype.toQueryString = function (params)
    {
        var s = "";
        for (e in params)  {
            var val = params[e];
            if (typeof val == "object")
                val = JSON.stringify(val);
            s += e + "=" + encodeURIComponent(val) + "&";
        }
        s = s.substring(0, s.length - 1);
        return s.length > 0 ? "?" + s : "";
    };
    
    RESTClient.prototype.async =function() {  return U.beget(this, {options:{async:true}});  };
    
    RESTClient.prototype.ajax =function(type, path, params, onsuccess, onerror) {
        params = params || {};
        var args = this.toArgs(path, params, onsuccess, onerror);
        args.type = type;
        if (this.options.jsonp) {        
            args.dataType = "jsonp";
            args.data = {callback:"?"};
            if (type == "POST")
                throw new Error("Can't use POST method with JSONP.");
            if (!this.options.async)
                throw new Error("Can't make synchronous call with JSONP.");
        }
        if (type == 'POST')
        {
            args.url = this.urlbase + path;            
            args.data = params;
        }
        if (U.offline()) return this.offlinehook(args) ; 
        var res = $.ajax(args);        
        if (!this.options.async) {
            if (res.status == 200)
            {
                return this.options.parseJSON ? JSON.parse(res.responseText) : res.responseText;
            }
            else 
            {
//                console.log('call failed ' + res.status + ' ' + res.statusText);
    //            throw new Error("Remote call to '" + 
    //                 args.url + "' failed, status=" + res.status + ", statusText=" + res.statusText);
            }
        }
        return res;
    }; 
    RESTClient.prototype.get = function(path, params, onsuccess, onerror) {
    	return this.ajax("GET", path, params, onsuccess, onerror);
    };
    RESTClient.prototype.post = function(path, params, onsuccess, onerror) {
        return this.ajax("POST", path, params, onsuccess, onerror);
    };
    RESTClient.prototype.postObject = function(path, object, onsuccess, onerror) {
       var args = {  type: 'POST',  
                      url: this.urlbase + path,  
                      data: JSON.stringify(object),
                      success:onsuccess, 
                      error:onerror,
                      contentType:"application/json; charset=utf-8"};
         args.async = this.options.async;
         if (U.offline()) return this.offlinehook(args);
         var res = $.ajax(args);
         if (!args.async) {
            if (res.status == 200)
                return this.options.parseJSON ? JSON.parse(res.responseText) : res.responseText;
            else 
            {
                console.log('AJAX call failed', res);
                return {ok:false, error:res.statusText};
            }
        }
    };
    RESTClient.prototype.putObject = function(path, object, onsuccess, onerror) {
       var args = {  type: 'PUT',  
                      url: this.urlbase + path,  
                      data: JSON.stringify(object),
                      success:onsuccess, 
                      error:onerror,
                      contentType:"application/json; charset=utf-8"};
         args.async = this.options.async;
         if (U.offline()) return this.offlinehook(args);
         var res = $.ajax(args);
         if (!args.async) {
            if (res.status == 200)
                return this.options.parseJSON ? JSON.parse(res.responseText) : res.responseText;
            else 
            {
                console.log('AJAX call failed', res);
                return {ok:false, error:res.statusText};
            }
        }         
    };
    RESTClient.prototype.del = function(path, params, onsuccess, onerror) {
        return this.ajax("DELETE", path, params, onsuccess, onerror);
    };    
    RESTClient.prototype.toArgs = function(path, params, onsuccess, onerror) {
        var args = { url: this.urlbase + path + this.toQueryString(params)};
        args.async = this.options.async;
        if (onsuccess) { args.success = onsuccess; }
        if (onerror) { args.error = onerror; }
        return args;
    }; 
    RESTClient.prototype.delay = function(path) {        
        var params, callback;
        if (arguments.length == 2) {
            if (typeof arguments[1] == "function")
                callback = arguments[1];
            else
                params = arguments[1];
        }
        else if (arguments.length == 3)  {
            params = arguments[1];
            callback = arguments[2];
        }
        else if (arguments.length > 3)
            throw new Error("Unexpected number of arguments in call to RESTClient.delay");
        return new AsyncCall(this.toArgs(path, params), callback);
    };

};

var M = {
    Client: RESTClient,
    AsyncCall: AsyncCall,
    asyncCall:asyncCall,
    onObjectReady:onObjectReady
};
if (modulesInGlobalNamespace)
    window.rest = M;
return M;
});

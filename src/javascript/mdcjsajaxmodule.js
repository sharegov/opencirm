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
// module declared in MDCJSLibrary.js


MDCJSLIB.modules.ajax.RESTClient = function(urlbase, options)
{  
    this.urlbase = urlbase ? urlbase : "http://localhost:8182";    
    this.options = { async: false, parseJSON: true, timeout: 10000};
    if (options) { $.extend(this.options, options); }
};

(function() {
    MDCJSLIB.modules.ajax.RESTClient.prototype.toQueryString = function (params)
    {
        var s = "";
        for (e in params)  {
            s += e + "=" + encodeURIComponent(params[e]) + "&";
        }
        s = s.substring(0, s.length - 1);
        return s.length > 0 ? "?" + s : "";
    };
    
    MDCJSLIB.modules.ajax.RESTClient.prototype.ajax =function(type, path, params, onsuccess, onerror) {
        params = params || {};
        var args = this.toArgs(path, params, onsuccess, onerror);
        args.type = type;
        if (this.options.jsonp) {        
            args.dataType = "jsonp";
            //args.data = {callback:"?"};
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
/*        console.log("ajax call ");
        console.log(args);
        console.log(onsuccess);
        console.log(onerror); */
        var res = $.ajax(args);        
        if (!this.options.async) {
            if (res.status == 200)
            {
                return this.options.parseJSON ? JSON.parse(res.responseText) : res.responseText;
            }
            else 
            {
                console.log('call failed ' + res.status + ' ' + res.statusText);
    //            throw new Error("Remote call to '" + 
    //                 args.url + "' failed, status=" + res.status + ", statusText=" + res.statusText);
            }
        }
        return res;
    };
    MDCJSLIB.modules.ajax.RESTClient.prototype.get = function(path, params, onsuccess, onerror) {
        return this.ajax("GET", path, params, onsuccess, onerror);
    };
    MDCJSLIB.modules.ajax.RESTClient.prototype.post = function(path, params, onsuccess, onerror) {
        return this.ajax("POST", path, params, onsuccess, onerror);
    };
    MDCJSLIB.modules.ajax.RESTClient.prototype.del = function(path, params, onsuccess, onerror) {
        return this.ajax("DELETE", path, params, onsuccess, onerror);
    };    
    MDCJSLIB.modules.ajax.RESTClient.prototype.toArgs = function(path, params, onsuccess, onerror) {
        var args = { url: this.urlbase + path + this.toQueryString(params)};
        args.async = this.options.async;
        args.timeout = this.options.timeout;
        if (onsuccess) { args.success = onsuccess; }
        if (onerror) { args.error = onerror; }
        
        return args;
    };
    MDCJSLIB.modules.ajax.RESTClient.prototype.delay = function(path) {        
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
    
//    MDCJSLIB.modules.ajax.bot = {};
//    MDCJSLIB.modules.ajax.bot.matcher = {
//        client : new MDCJSLIB.modules.ajax.RESTClient("http://localhost:8400/servicebot/rest/patterns", {jsonp:true, async:true}),
//        matchtext : function(text, callback, onerror) {
//            return this.client.get("/matchget", {text:text}, callback, onerror);
//        },
//        matchurl : function(url, callback, onerror) {
//            return this.client.get("/matchurl", {url:url}, callback, onerror);
//        },
//    };

}());

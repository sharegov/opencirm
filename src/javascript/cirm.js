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
define(['jquery', 'rest', 'U','user', 'store!', 'refs!all'], function($, rest, U, user, store, refs) {

    // A variation on JQuery's serializeArray that doesn't ignore unchecked checkboxes, radio buttons or
    // disabled elements (that could simply be read-only fields that we might want to submit back.
    $.fn.cirmSerializeArray = function(idattr) {
        var rselectTextarea = /^(?:select|textarea)/i,
        rinput = /^(?:color|date|checkbox|radio|datetime|email|hidden|month|number|password|range|search|tel|text|time|url|week)$/i,
        ronoff = /^(checkbox|radio)$/i,        
        rCRLF = /\r?\n/g;
        
		/*return this.map(function(){
			return this.elements ? jQuery.makeArray( this.elements ) : this;
		})*/
		return $(this).find('*')
		.filter(function(){
			return (this.id || this.name) && 
				( rselectTextarea.test( this.nodeName ) ||
					rinput.test( this.type ) );
		})
		.map(function( i, elem ){
			var val = jQuery( this ).val();
			
			if (ronoff.test( this.type ) && !this.checked) {
			    val = "off";
			}           
			
			//var name = elem.id || elem.name;
			var name;
			if(idattr === 'name')
				name = elem.name;
			else if(idaddr === 'id')
				name = elem.id;
			else if(typeof idattr === 'undefined')
				name = elem.id || elem.name;
			
			return val == null ?
				null :
				jQuery.isArray( val ) ?
					jQuery.map( val, function( val, i ){
					        return { type:elem.type, name: name, value: val.replace( rCRLF, "\r\n" ) };
					}) :
					{ type:elem.type, name: name, value: val.replace( rCRLF, "\r\n" ) };
		}).get();
	}
	
    // serialize a DOM object as a JSON object
    $.fn.cirmSerializeObject = function(idattr)
    {
       var o = {};
       var a = this.cirmSerializeArray(idattr);
       $.each(a, function() {   
           var name = this.id || this.name;
           if (o[name]) {
               if (!o[name].push) {
                   o[name] = [o[name]];
               }
               o[name].push(this.value || '');
           } else {
               o[name] = this.value || '';
           }
       });
       return U.nestify(o);
    };
        
    function viewBusinessObject(bo) 
    {
            var viewer = toDOM(bo);
            var dialog = document.createElement("div");
            dialog.setAttribute("title", bo.type + ' ' + bo.iri.split('/').pop().split('#')[0]);
            $(dialog).append(viewer).dialog();
            // this.setui.call(this, viewer);
    }

    var baseurl = document.location.protocol + '//' + document.location.host;
    var op = new rest.Client(baseurl + "/op");
    var ui = new rest.Client(baseurl + "/ui");
    var meta = new rest.Client(baseurl + "/meta");
    var search = new rest.Client(baseurl + "/search");
    var users = new rest.Client(baseurl + "/users");
    var top = new rest.Client(baseurl);
    
    var isConfigMode = false;
    
    var dataCache = {};
    
    function toQueryString(params)
    {
        var s = "";
        for (e in params)
        {
            s += e + "=" + encodeURIComponent(params[e]) + "&";
        }
        s = s.substring(0, s.length - 1);
        return s.length > 0 ? "?" + s : "";
    };
    
    function dataFetchParams(datasource)  {
        switch (datasource.type) {
        case "RestQueryDataSource": 
            var queryString = $.tmpl(datasource.hasTemplate.hasContents, this)[0].nodeValue;
            return [datasource.hasUrl, {q:queryString}];
        case "IndividualsListDataSource":
            return ["/individuals", {q : datasource.hasQueryExpression}];
        default:
            throw new Error("Can't interpret data source of type " + datasource.type);
        }                
    }
    
    function makeCacheKey(datasource) {
        switch (datasource.type) {
        case "IndividualsListDataSource":
            return "/individuals/q?=" + datasource.hasQueryExpression;
        default:
            return null;
        }
    }
    
    function fetchNow(datasource, callback) {
        var cacheKey = makeCacheKey(datasource);
        if (cacheKey != null) {
            var val = dataCache[cacheKey];
            if (typeof val != "undefined")
                return val;
            val = top.get.apply(top, dataFetchParams(datasource));
            dataCache[cacheKey] = val;
            return val;
        }
        else
            return top.get.apply(top, dataFetchParams(datasource));
    };
    
    function delayDataFetch(datasource, callback) 
    {
        var args = dataFetchParams(datasource);
        args.push(callback);
        return top.delay.apply(top, args);     
    };
    
    // Server-side event listener. Currently implemented with normal polling because
    // long polling is not available in restlet
    function EventManager() {
        var self = this;
        self.listeners = {},
        self.pollInterval = 30000;
        self.lastEventTimestamp = (new Date()).getTime();
        self.url = op.urlbase + "/event";
        self.timer = null;        
        self.bind = function (entityIri, listener) {
            var L = self.listeners[entityIri];
            if (!L)
                self.listeners[entityIri] = [listener];
            else
                L.push(listener);
        };
        
        self.unbind = function (eventType, listener) {
            var L = listeners[eventType];
            if (!L) return;
            var location = 0;
            while (L[location] != listener && location < L.length) location++;
            if (location < L.length) L.splice(location, 1);
        };
        
        self.trigger = function (ev) {
            var L = self.listeners[ev.entity.iri];    
            if (L)
                $.each(L, function(i,l) { l.apply(ev); });            
        };
        
        self.startPolling = function () {
            if (self.timer != null) throw "Polling already started for " + self.url;
            self.timer = setTimeout(self.doPoll, self.pollInterval);
        };
        
        self.stopPolling = function () {
            if (self.timer != null) throw "No active polling for " + self.url;
            clearTimeout(self.timer);
            self.timer = null;
        };
        
        self.doPoll = function () {
            if (U.offline()) return;
            $.ajax(self.url  + "/" + self.lastEventTimestamp, {
                    async:true,
                    error:function (xhr, status, er) {
                        // don't know of a reliable way to detect if the server went?
                        // but this would be a good place to indicate that we went offline as well
                        console.log("Event poll failed", xhr, status, er);
                    },
                    success:function(data, status,xhr) {
                        //console.log("Event poll success", data, status, xhr);
                        if (!data.ok) {
                            console.log('error polling events', data);
                            return;
                        }
                        if (data.events.length > 0) {
                            self.lastEventTimestamp = data.events[0].timestamp;
                            $.each(data.events, function(i,x) {
                                    if (x.timestamp > self.lastEventTimestamp)
                                        self.lastEventTimestamp = x.timestamp;
//                                    console.log('dispatching event', x.event);
                                    var L = self.listeners[x.event.entity.iri];
                                    if (L)
                                        $.each(L, function(i,l) { l.apply(x.event); });
                            });
                        }
                    },
                    complete:function (xhr, status) {
                        self.timer = null;
                        self.startPolling();
                    }
            });
        };
        
        return this;
    };

    var eventManager = new EventManager();
    
    // Catch ontology changes events pushed from the server and clears the cach when such an
    // event occurs. The danger here is with the offline mode - if we clear the cache and immediately
    // after that go offline, we won't be able to download the latest version. In fact, we could go
    // offline right in the middle of a fetch operation so we should detect that and store the fetch
    // operation as an online event. Which means that we'd want to store full JavaScript continuations
    // if we want to easily and transparently handle all cases. 
    eventManager.bind("http://www.miamidade.gov/ontology#MiamiDadeCoreOntology", function (e) {
        refs.clearCached();
    });

    var onlineEventHandlerMap = {
    newcase:function(e) {
            
    },
    updatecase:function(e) {
    }
    };
    
    function onlineCatchUp() {
        store.ononline.all().then(function (events) {
          if (processEvent(e)) {
              var handler = onlineEventHandlerMap[e.type];
              if (handler == null)
                  console.log('No handler for online event', e);
              else if (handler.apply([e])) 
                  store.ononline.remove(e.id);
          }
        });
    }
    top.get('/manage/sysinfo', '', function (info) {
            console.log('Sysinfo', info);
            if (info.config && info.config.allClientsExempt !== undefined) {
            	 user.allClientsExempt = info.config.allClientsExempt;            	 
            }
            if (info.config && info.config.isConfigMode !== undefined) {
            	isConfigMode = info.config.isConfigMode;
            }
            $('#span_hostname').html(info.host);
    });
    
    var cirm =  {    
        baseurl: baseurl,
        user : user,
        op : op,
        ui : ui,
        meta : meta,
        top : top,
        search: search,
        users : users,
        delayDataFetch : delayDataFetch,
        fetchNow : fetchNow,
        toQueryString : toQueryString,
        viewBusinessObject : viewBusinessObject,
        events : eventManager,
        refs : refs,
        isConfigMode : isConfigMode
     };
    
    if (modulesInGlobalNamespace)
        window.cirm = cirm;
    
    return cirm;
    
});

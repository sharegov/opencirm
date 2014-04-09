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
/**
 * This is a simple wrapper around IndexedDB (HTML5 local storage standard). It uses a
 * database called 'cirmdb' to store data for offline use. Multiple users could be supported
 * on a single computer by creating a different database for each currently loggedin user,
 * but for now we don't do this...easy to change though.
 */

define(['U'], function(U) {
    var trace = false;
    var onerr = function (e) {console.log('Store Error', e);};
    var indexedDB = false;//window.indexedDB || window.mozIndexedDB || window.webkitIndexedDB;
    var cirmdbVersion = "5";
    var cirmdb = null;
        
    function ok(def) { return function(x) { def.resolve(x); }; }
    function ko(def) { return function(x) { def.reject(x); }; }

    function makeNOPStore(storename, idname) {
        var self = {};
        self.name = storename;
        self.idname = idname;
        self.get = function () { return $.Deferred().resolve(null); };
        self.put = function() { $.Deferred();};
        self.remove = function() { $.Deferred().resolve(null);};
        self.all = function() { $.Deferred().resolve([]); };
        self.removeAll = function() { };
        return self;
    }
    
    /**
     * Read-write methods to a single store available as a object. It simplifies a bit
     * dealing with transactions and error handling.
     */
    function makeStore(storename, idname) {
        var self = {};
        self.name = storename;
        self.idname = idname || "id";               
        self.get = function (id) {
            if (trace) console.log('read from ' + self.name, id);
            var D = $.Deferred();
            var tx =cirmdb.transaction([self.name],"readonly");
            var req = tx.objectStore(self.name).get(id);
            req.onsuccess = function(ev) {
                if (ev.target.result)
                    D.resolve(ev.target.result.data);
                else
                    D.resolve(null);
            };
            req.onerror = ko(D);
            return D.promise();            
        };
        self.put = function (theid, theobj) {
            var id = theobj ? theid : theid[self.idname];
            var data = theobj ? theobj : theid;
            if (trace) console.log('write to ' + self.name, id, data);
            var D = $.Deferred();
            var tx =cirmdb.transaction([self.name],"readwrite");
            var req = tx.objectStore(self.name).put({id:id, data:data});
            req.onsuccess = ok(D);
            req.onerror = ko(D);
            return D.promise();            
        };
        self.remove = function (id) {
            if (trace) console.log('remove from ' + self.name, id);
            var D = $.Deferred();
            var tx =cirmdb.transaction([self.name],"readwrite");        
            var req = tx.objectStore(self.name).delete(id);
            req.onsuccess = ok(D);
            req.onerror = ko(D);        
            return D.promise();
        };        
        self.all = function () {
            if (trace) console.log('all from ' + self.name);
            var D = $.Deferred();
            var tx =cirmdb.transaction([self.name],"readonly");        
            var request = tx.objectStore(self.name).openCursor();
            var A = [];        
            request.onsuccess = function(evt) {
                var cursor = evt.target.result;  
                if (cursor) {  
                    A.push(cursor.value);
                    cursor.continue();
                }  
                else {  
                    D.resolve(A);
                }                        
            };
            request.onerror = ko(D);        
            return D.promise();            
        };
        self.removeAll = function() {
            self.all().done(function(x) { self.remove(x); });
        };        
        if (!cirmdb.objectStoreNames.contains(self.name))
            cirmdb.createObjectStore(self.name, {keyPath:"id"});        
        return self;
    }
        
    var store =  {
        trace:function (t) { trace = t},
        cirmdb:function() { return cirmdb; },
        wipeOut : function() { 
            if (cirmdb) cirmdb.close();
            var req = indexedDB.deleteDatabase('cirmdb');
            req.onsuccess = function (e) { console.log('Database deleted', e); };
            req.onerror = onerr;
            if (trace) console.log('Wipe out request', req);
        },
        idseq:0,
        newid: function () {
            store.kv.put("idseq", ++store.idseq);
            return store.idseq;
        }
    };
    
    var nop = function() {return $.Deferred().resolve(null); };
    var nostore = {
        trace:function (t) { trace = t},
        cirmdb:function() { return null; },
        cache:makeNOPStore('cachestore', 'url'),
        cases: makeNOPStore("serviceCaseStore", "boid"),
        ononline:makeNOPStore("offlineEventsStore", "id"),
        kv: makeNOPStore("kv", "key"),
        wipeOut : function() { },
        idseq:0,
        newid: function() {
            return nostore.idseq++; 
        }
    };

    function load(name, req, done, config) {
        if (!indexedDB) { done(nostore); return; }
        
        // TODO: This needs to be reworked: the IndexedDB spec is a moving target and
        // browsers are catching up. There's poor documentation on what the 
        // sequence of events is, so it we need experimentation and a rewrite again.
        // The following is just a draft with the new 'onupgradeneeded' event.
        var onCreated = function (e) {
                if (trace) console.log('store open set version', e);
                cirmdb = e.target.result;
                // URL cache store
                store.cache = makeStore("cachestore", "url");
                
                // Service Cases key-id by their "business object id"
                store.cases = makeStore("serviceCaseStore", "boid");
                
                // Store only events to be processed when we get back online
                // events should be deleted from the store once they've
                // processed
                store.ononline = makeStore("offlineEventsStore", "id");
                
                // Generic key-value store, anything goes, make sure your
                // keys are unique
                store.kv = makeStore("kv", "key");
                
                store.kv.get('idseq').then(function() { onReady(e); });
        };
        var onReady = function(e) {
          if (trace) console.log('db read');
          if (modulesInGlobalNamespace)
            window.store = store;
          done(store);            
        };
        var req = indexedDB.open('cirmdb', cirmdbVersion);
        req.onupgradeneeded = onCreated;
        req.onsuccess = onCreated; //onReady;            
/*            req.onsuccess = function(e) { 
                cirmdb = req.result; 
                if (trace) console.log('opened', cirmdb);
                var r2 = cirmdb.setVersion(cirmdbVersion);
                r2.onsuccess=onCreated;
                r2.onerror = onerr;
            }; */
        req.onerror = onerr;
    }
        
    return { load: load }
});

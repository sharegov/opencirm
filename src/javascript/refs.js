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
define(['rest', 'U', 'store!'], function(rest, U, store) {
        
    var top = new rest.Client(U.baseurl);
    
    // Holds metadata references (ontology query results stored in JavaScript variables) 
    // that need to be updated when the metadata changes on the server side.
    //
    // The first idea was to declare those reference in the ontology:
    // top.get('/individuals/instances/SoftwareReference');
    // and then track changes to each individually. But that's more complicated and a non-trivial
    // problem to go from an entity to the relevant queries that may be impacted by a change in 
    // that entity.
    //
    // So what we do, is simply list the needed references here with the queries and then we invalidate
    // all of them when any ontology changes...
    function MetaDataRefs() {
        var self = this;
        self.cached = {};
        self.all = [
            {"hasName": "time", "hasUrl":"/op/time", 
            	"map":function(A) {
            		if(A.ok)
            		{
	            		var clientTime = new Date().getTime();
	            		var delta = clientTime - A.time;
	            		var result = {"ok":A.ok, "serverTime":A.time, "clientTime":clientTime, "delta":delta};
	            		return result;
            		}
            		else
            			return A;
            }},
            {"hasName": "caseStatuses", "hasQueryExpression": "legacy:Status"},
            {"hasName": "casePriorities", "hasQueryExpression": "legacy:Priority"},
            {"hasName": "caseIntakeMethods", "hasQueryExpression": "legacy:IntakeMethod"},
            {"hasName": "statesInUS", "hasQueryExpression": "State__U.S._"},
            {"hasName": "typeToXSDMappings", "hasUrl": "/legacy/getHasTypeMappingToXSD"},
            {"hasName": "serviceCaseClass", "hasUrl": "/individuals/legacy:ServiceCase"},
            {"hasName": "cities", "hasQueryExpression": "Miami_Dade_City or County"},
            {"hasName": "directions", "hasQueryExpression" : "Direction"},
            {"hasName": "streetTypes", "hasQueryExpression" : "Street_Type"},
            {"hasName": "GeoLayerAttributes", "hasQueryExpression" : "GeoLayerAttribute"},
            {"hasName": "serviceCases", "hasQueryExpression" : "legacy:ServiceCase",            
                "map":function(A) {
                    //A = [A];  
                    var m = {};
                    $.each(A, function(i,v) {
                        v.hasServiceField = U.ensureArray(v.hasServiceField);
                        $.each(v.hasServiceField, function(i,x) {
                                x.hasServiceFieldAlert = U.ensureArray(x.hasServiceFieldAlert);
                        });                        
                        v.hasDataConstraint = U.ensureArray(v.hasDataConstraint);
                        v.hasServiceActor = U.ensureArray(v.hasServiceActor);
                        v.hasActivity = U.ensureArray(v.hasActivity);
                        v.hasAutoServiceActor = U.ensureArray(v.hasAutoServiceActor);
                        v.hasServiceActor.sort(function (a,b) {
                      		if(a.label && b.label)
                                return a.label.toUpperCase().localeCompare(b.label.toUpperCase());
                            else
                            	return parseFloat(a.hasOrderBy) - parseFloat(b.hasOrderBy);
                        });
                        v.hasActivity.sort(function (a,b) {
                      		if(a.label && b.label)
                                return a.label.toUpperCase().localeCompare(b.label.toUpperCase());
                            else
                            	return parseFloat(a.hasOrderBy) - parseFloat(b.hasOrderBy);
                        });             
                        U.resolveIris(v);                        
                        m[v.iri] = v;
                    });
                    // May we could a 'get' method here the accept IRI fragments and prepends
                    // the rest, cause now the full IRI is scattered through the code hard-coded
                    return m;
            }},
            {"hasName": "agencies", "hasUrl":"/legacy/searchAgencyMap"},
            {"hasName": "serviceCaseList", "dependsOn":["serviceCases"], 
             "map": function(A) { 
                 var x = {};
                 $.each(A, function (i,v) {x[v.label+' - '+v.hasJurisdictionCode]=v.hasLegacyCode});
                 return x;
            }},
            {"hasName": "autoCompleteServiceCaseList", "dependsOn":["serviceCases"],
                "map": function(A) {
                    var x = [];
                    $.each(A, function (i,v) {
                    	if(v.isDisabled != 'true') {
                          var tempLabel = v.label+' - '+v.hasJurisdictionCode;
                          x.push({"label":tempLabel, "value":tempLabel});
                        }
                   });                    
                    x.sort( function(a,b) {  
                         if(a.label && b.label) return a.label.localeCompare(b.label); else return -1; });                    
                    return x;
            }}
        ];
        self.preload = function(R) {
            var d = $.Deferred();
            if (R.dependsOn) {
                var deps = [];
                $.each(R.dependsOn, function(i,v){deps.push(self[v]);});
                d.resolve(self.cached[R.hasName] = R.map.apply(null, deps));                
            }
            else {
              var url = R.hasUrl ? R.hasUrl : '/individuals?q=' + R.hasQueryExpression;
              if (U.offline()) {
                store.cache.get(url).then(function(x) {
                  d.resolve(self.cached[R.hasName] = 
                      R.map ? R.map.apply(null, [x]) : x);
                });
              }
              else {
                top.async().get(url).then(function(x) {
                  store.cache.put(url, x);
                  d.resolve(self.cached[R.hasName] = 
                      R.map ? R.map.apply(null, [x]) : x);
                });
              }
            }
            return d;
        };
        $.each(self.all, function(i,x) {             
            self.__defineGetter__(x.hasName, function () {
                if (self.cached[x.hasName] == undefined) {
                    if (x.hasQueryExpression) {
                        var result = top.get('/individuals?q='+x.hasQueryExpression);
                        self.cached[x.hasName] = x.map ? x.map.apply(null, [result]) : result;
                    }
                    else if (x.hasUrl) {                        
                        var result = top.get(x.hasUrl);
                        self.cached[x.hasName] = x.map ? x.map.apply(null, [result]) : result;
                    }
                    else if (x.dependsOn) {
                        var deps = [];
                        $.each(x.dependsOn, function(i,v){deps.push(self[v]);});
                        self.cached[x.hasName] = x.map.apply(null, deps);
                    }
                }
                return self.cached[x.hasName];
            });
        });
        self.clearCached = function () { self.cached= {} };
    }

    var meta = new MetaDataRefs();
    
    
    /**
     * Reload all references and call 'done' when done.
     */
    function reloadAll(done) {
        var set1 = meta.all.filter(function(x) { return !x.hasOwnProperty("dependsOn"); }).map(function(x) {
                return meta.preload(x);
        });
        // first resolve all refs that don't depend on anything else, then the ones that do
        $.when.apply(null, set1).then(function(dontcare) {
                var set2 = meta.all.filter(function(x) { return x.hasOwnProperty("dependsOn"); }).map(function(x) {
                        return meta.preload(x);
                });
                $.when.apply(null, set2).then(function(dontcare) {
                    done(meta); 
                });
        });
    }
    
    function load(name, req, done, config) {    
        //console.log('load', name, req, done, config);
        // We don't care about name for now...this is just to put the pre-caching of metadata
        // in the module loading sequence so the application doesn't start until
        // all meta data (in the form of references) is loaded. 
        //
        // While it seems better to load all those things lazily, since AJAX has
        // an asynchronous mode, we can't do it until the IndexedDB implementation of
        // Chrome starts supporting the synchronous API as well. So basically, 
        // the main reason this is being preloaded is because we want the meta data to 
        // be available synchronously and not via a callback
        
        // First check that server is up, and if not, mark the app as being 'offline'.        
        top.async().get("/op/time").then(function(x) {
            reloadAll(done)
        }, function (x) {
            console.log('Server down, set offline mode');
            U.connectivity.goOffline();
            reloadAll(done);
        });
    }
    
    var M = {
        load: load
    };
    if (modulesInGlobalNamespace)
        window.refs = M;
    return M;    
});

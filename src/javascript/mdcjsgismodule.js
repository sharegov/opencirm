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
// define gis public interface
MDCJSLIB.modules.gis = (function() {

	// dependencies
	var ajaxcalls = MDCJSLIB.modules.ajax;

	// private properties
	var _url, _path, restClient;

	// private methods
	var addressCandidates = function(address, zip, municipality, callback, error) {
		var path = _path  + "/candidates";
		restClient.get(path,{street:address,zip:zip,municipality:municipality},function(data) {
			  if(data.ok == true){
                            var candidates = [];
			    $.each(data.candidates, function(index,candidate){
			    	candidates.push(candidate);
    	                    });
			    callback(candidates);
                          }else
                            error(data.message, null, "")
			},function(XMLHttpRequest, textStatus, errorThrown) {
				var message = ajaxErrorMessage(XMLHttpRequest, textStatus);
				if(error)
					error(message, XMLHttpRequest, textStatus);
			}
		);

	};
	
        var commonLocationCandidates = function(name, callback, error) {
		var path = _path  + "/commonlocationcandidates";
	    restClient.get(path,{name:name},function(data) {
			var candidates = [];
                        if(data.ok == true){
			  $.each(data.candidates, function(index,candidate){
			  	candidates.push(candidate);
    		          });
			  callback(candidates);
                        }else
                          error(data.message, null, "")

			},function(XMLHttpRequest, textStatus, errorThrown) {
				var message = ajaxErrorMessage(XMLHttpRequest, textStatus);
				if(error)
					error(message, XMLHttpRequest, textStatus);
			}
		);

	};


        var commonLocation = function(id, callback, error) {
		var path = _path  + "/commonlocations/" + id;
	    restClient.get(path,null,function(data) {
                        if(data.ok == true){
			  var commonLocation = data.commonlocation;
       			  callback(commonLocation);
                        }else
                          error(data.message, null, "")
			},function(XMLHttpRequest, textStatus, errorThrown) {
				var message = ajaxErrorMessage(XMLHttpRequest, textStatus);
				if(error)
					error(message, XMLHttpRequest, textStatus);
			}
		);

	};

        var addressByFolio = function(folio, callback, error) {
		var path = _path  + "/address/";
	    restClient.get(path,{folio:folio},function(data) {
                        if(data.ok == true){
  			  callback(data.address);
                        }else
                          error(data.message, null, "")
			},function(XMLHttpRequest, textStatus, errorThrown) {
				var message = ajaxErrorMessage(XMLHttpRequest, textStatus);
				if(error)
					error(message, XMLHttpRequest, textStatus);
			}
		);

	};
	

    var condoAddress = function(street, zip, unit, callback, error) {
		var path = _path  + "/condoaddress/";
	        restClient.get(path,{street:street,zip:zip,unit:unit},function(data) {
                        if(data.ok == true){
  			  callback(data.address);
                        }else
                          error(data.message, null, "")

			},function(XMLHttpRequest, textStatus, errorThrown) {
				var message = ajaxErrorMessage(XMLHttpRequest, textStatus);
				if(error)
					error(message, XMLHttpRequest, textStatus);
			}
		);

	};
	
	var standardizeStreet = function(street, callback, error) {
		var path = _path + "/standardizestreet/";
		restClient.get(path,{street:street}, function(data) {
        	if(data.ok == true){
  				callback(data.street);
            } else
				error(data.message, null, "");
			}, function(XMLHttpRequest, textStatus, errorThrown) {
				var message = ajaxErrorMessage(XMLHttpRequest, textStatus);
				if(error)
					error(message, XMLHttpRequest, textStatus);
			}
		);
	};

	var ajaxErrorMessage = function(jqXHR, exception) {
		
		var errorMessage = "";
		
        if (jqXHR.status === 0) {
        	errorMessage = 'Not connect.\n Verify Network.';
        } else if (jqXHR.status == 404) {
        	errorMessage = 'Requested page not found. [404]';
        } else if (jqXHR.status == 500) {
        	errorMessage = 'Internal Server Error [500].';
        } else if (exception === 'parsererror') {
        	errorMessage = 'Requested JSON parse failed.';
        } else if (exception === 'timeout') {
        	errorMessage = 'Time out error.';
        } else if (exception === 'abort') {
        	errorMessage = 'Ajax request aborted.';
        } else {
        	errorMessage = 'Uncaught Error.\n' + x.responseText;
        }
        
        return errorMessage;
    };
	
	var initConnection = function(url, path) {
		_url = url;
		_path = path;
		restClient = new ajaxcalls.RESTClient(url, {jsonp:true, async:true});
	};
	var getUrl = function() {
		return _url;
	};
	var getPath = function() {
		return _path;
	};

	// public API
	return {
	    getAddressCandidates : addressCandidates,
	    getCommonLocationCandidates: commonLocationCandidates,
	    getCommonLocation : commonLocation,
            getAddressByFolio: addressByFolio,
	    getCondoAddress: condoAddress,
	    getStandardizedStreet: standardizeStreet,
	    initConnection : initConnection,
	    getUrl : getUrl,
	    getPath : getPath
	};

}());



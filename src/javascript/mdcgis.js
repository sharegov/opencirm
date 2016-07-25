define('cirmgis', ['rest'], function(rest) {
  //  var mdcgis = MDCJSLIB.modules.gis;
  var url = null;
  var path = null;
  var restClient = null;
  
  function initConnection(_url, _path) {
    //    mdcgis.initConnection(_url, _path);
    url = _url;
    path = _path;
    restClient = new rest.Client(url, {
      jsonp: true,
      async: true
    });
  }
  
  function ensureInit() {
    if (url == null)
      throw "Please initialize the googlegis module with the initConnection method.";
  }
  
  function getUrl() {
    return url;
  }
  
  function getPath() {
    return path;
  }
  
  function addressCandidates(address, zip, municipality, callback, error) {
    ensureInit();
    var path = _path + "/candidates";
    restClient.get(path, {
      street: address,
      zip: zip,
      municipality: municipality
    }, function(data) {
      if (data.ok == true) {
        var candidates = [];
        $.each(data.candidates, function(index, candidate) {
          candidates.push(candidate);
        });
        callback(candidates);
      } else
        error(data.message, null, "")
    }, function(XMLHttpRequest, textStatus, errorThrown) {
      var message = ajaxErrorMessage(XMLHttpRequest, textStatus);
      if (error)
        error(message, XMLHttpRequest, textStatus);
    });
  }
  
  function commonLocationCandidates(name, callback, error) {
    ensureInit();
    var path = _path + "/commonlocationcandidates";
    restClient.get(path, {
      name: name
    }, function(data) {
      var candidates = [];
      if (data.ok == true) {
        $.each(data.candidates, function(index, candidate) {
          candidates.push(candidate);
        });
        callback(candidates);
      } else
        error(data.message, null, "")
    }, function(XMLHttpRequest, textStatus, errorThrown) {
      var message = ajaxErrorMessage(XMLHttpRequest, textStatus);
      if (error)
        error(message, XMLHttpRequest, textStatus);
    });
  }
  
  function commonLocation(id, callback, error) {
    ensureInit();
    var path = _path + "/commonlocations/" + id;
    restClient.get(path, null, function(data) {
      if (data.ok == true) {
        var commonLocation = data.commonlocation;
        callback(commonLocation);
      } else
        error(data.message, null, "")
    }, function(XMLHttpRequest, textStatus, errorThrown) {
      var message = ajaxErrorMessage(XMLHttpRequest, textStatus);
      if (error)
        error(message, XMLHttpRequest, textStatus);
    });
  }
  
  function addressByFolio(folio, callback, error) {
    ensureInit();
    var path = _path + "/address/";
    restClient.get(path, {
      folio: folio
    }, function(data) {
      if (data.ok == true) {
        callback(data.address);
      } else
        error(data.message, null, "")
    }, function(XMLHttpRequest, textStatus, errorThrown) {
      var message = ajaxErrorMessage(XMLHttpRequest, textStatus);
      if (error)
        error(message, XMLHttpRequest, textStatus);
    });
  }

  function condoAddress(street, zip, unit, callback, error) {
    ensureInit();
    var path = _path + "/condoaddress/";
    restClient.get(path, {
        street: street,
        zip: zip,
        unit: unit
      }, function(data) {
        if (data.ok == true) {
          callback(data.address);
        } else
          error(data.message, null, "")
      }, function(XMLHttpRequest, textStatus, errorThrown) {
        var message = ajaxErrorMessage(XMLHttpRequest, textStatus);
        if (error)
          error(message, XMLHttpRequest, textStatus);
      }
  }

  function standardizeStreet(street, callback, error) {
    ensureInit();
    var path = _path + "/standardizestreet/";
    restClient.get(path, {
      street: street
    }, function(data) {
      if (data.ok == true) {
        callback(data.street);
      } else
        error(data.message, null, "");
    }, function(XMLHttpRequest, textStatus, errorThrown) {
      var message = ajaxErrorMessage(XMLHttpRequest, textStatus);
      if (error)
        error(message, XMLHttpRequest, textStatus);
    });
  }
  
  return {
    getAddressCandidates: addressCandidates,
    getCommonLocationCandidates: commonLocationCandidates,
    getCommonLocation: commonLocation,
    getAddressByFolio: addressByFolio,
    getCondoAddress: condoAddress,
    getStandardizedStreet: standardizeStreet,
    initConnection: initConnection,
    getUrl: getUrl,
    getPath: getPath
  };
});
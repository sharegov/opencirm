define('cirmgis',['rest'], function(rest) {

  var mdcgis = MDCJSLIB.modules.gis;
  var url = null;
  var path = null;
  var restClient = null;
  
  function initConnection(_url, _path) {
    mdcgis.initConnection(_url, _path);
    url = _url;
    path = _path;
  }
	
  function ensureInit() {
    if (url == null)
      throw "Please initialize the googlegis module with the initConnection method.";
  }
	
  function getUrl() { return url;	}
  function getPath() { return path;	}
	
  function addressCandidates(address, zip, municipality, callback, error) {
    ensureInit();
    mdcgis.getAddressCandidates(address, zip, municipality, callback, error);
  }
  
  function commonLocationCandidates(name, callback, error) {
    ensureInit();    
    mdcgis.getCommonLocationCandidates(name, callback, error);
  }

  function commonLocation(id, callback, error) {
    ensureInit();    
    mdcgis.getCommonLocation(id, callback, error);
  }
	
  function addressByFolio(folio, callback, error) {
    ensureInit();    
    mdcgis.getAddressByFolio(folio, callback, error);
  }

  function condoAddress(street, zip, unit, callback, error) {
    ensureInit();    
    mdcgis.getCondoAddress(street, zip, unit, callback, error);
  }

  function standardizeStreet(street, callback, error) {
    ensureInit();    
    mdcgis.getStandardizedStreet(street, callback, error);
  }
	
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
});
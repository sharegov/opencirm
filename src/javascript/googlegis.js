define('cirmgis', 
       ['jquery', 
        'async', 
        'async!http://maps.google.com/maps/api/js?key=AIzaSyCaOiVSUeMAx_RCyUvpuorvZKTT3-t1JiU&v=3&sensor=false'], 
function ($) {
  var url = null;
  var path = null;
  var restClient = null;
  var geocoder = null;
  var defaultComponentRestrictions = {'administrativeArea' :'Miami-Dade County', 'country':'US'};

  function initConnection(_url, _path) {
      url = _url;
      path = _path;
      restClient = new rest.Client("/", {jsonp: true, async: true});
      geocoder = new google.maps.Geocoder();
      window.gmaps = google;
      window.geo = geocoder;
  }

  function ensureInit() {
    if (restClient == null)
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
    var componentRestrictions = $.extend({}, defaultComponentRestrictions);
    if (zip != undefined && zip.length > 0)
    {
        componentRestrictions.postalCode = zip;
    }
    geocoder.geocode( { 'address': address,
                        'componentRestrictions': componentRestrictions
                      },
        function(results, status) {
            if (status == google.maps.GeocoderStatus.OK) {
		var candidates = resultsToAddresCandidates(results);
		if (candidates.length > 0)
                    callback(candidates);
		else
                    error("Failed to map address:only partial matches.", {"status":200},"");
            } else {
                error("Failed to map address:no matches.", {"status":500}, "");
            }
    });    
  }

  function commonLocationCandidates(name, callback, error) {
    ensureInit();
    error('This is a default implementation based on Google Maps.',
        {status: 404}, 'Common location candidates is not available.');
  }

  function commonLocation(id, callback, error) {
    ensureInit();
    error('This is a default implementation based on Google Maps.',
        {status: 404}, 'getCommonLocation is not available.');
  }

  function addressByFolio(folio, callback, error) {
    ensureInit();
    error('This is a default implementation based on Google Maps.',
        {status: 404}, 'Address lookup by folio number is not available.');
  }

  function condoAddress(street, zip, unit, callback, error) {
    ensureInit();
    error('This is a default implementation based on Google Maps.',
        {status: 404}, 'Condo addressing is not available.');
  }

  function standardizeStreet(street, callback, error) {
    ensureInit();
    error('This is a default implementation based on Google Maps.',
        {status: 404}, 'Standardized street address is not available.');
  }

  function resultsToAddresCandidates(results) {
    var candidates = [];
    for (var i=0, l = results.length; i<l; i++) {
	if (!results[i].partial_match)
	    candidates.push(resultToAddressCandidate(results[i]));
    }
    return candidates;
  }
  
  function resultToAddressCandidate(result) {
//    console.log('google result to candidate', result);
    var candidate = {address:'', municipality:'', zip: '', fullAddress: '', parsedAddress: {zip:'', House:''}};
    candidate.address =  result.formatted_address.split(",")[0];
    candidate.fullAddress = result.formatted_address;
    for (var i=0, l = result.address_components.length; i<l; i++) {
        if($.inArray('street_number',result.address_components[i].types) > -1)
        {
            candidate.parsedAddress.House = result.address_components[i].short_name;
        }
        if($.inArray('locality',result.address_components[i].types) > -1)
        {
            candidate.municipality = result.address_components[i].short_name.toUpperCase();
        }
        if($.inArray('postal_code', result.address_components[i].types) > -1)
        {
            candidate.zip = result.address_components[i].short_name;
            candidate.parsedAddress.zip =  candidate.zip;
        }
        candidate.originalResult = result;
    }
    return candidate;
  }

  function reverseGeoCode(latlng, continuation) {
    geocoder.geocode({'location': latlng}, function(results, status) {
      if (status === 'OK') {
//        console.log('geocode ok', results);            
        for (var i = 0; i < results.length; i++)
          if (results[i].types.find(function(t) { return t=="street_address"})) {          
            var info = resultToAddressCandidate(results[i]);
//            console.log('info', info);            
            // get rid of the ranges that google returns by picking the starting number
            var streetNumber = info.address.split(" ")[0];
            if (streetNumber.indexOf("-") > 0) {
              var firstNumber = streetNumber.split("-")[0];
              info.address = info.address.replace(streetNumber, firstNumber);
              if (info.fullAddress)
                info.fullAddress = info.fullAddress.replace(streetNumber, firstNumber);
            }            
            continuation.call(this, info);
          }
      } else {
        console.log('geocode failed', status);
      }
    });
  }
  
  return {
      reverseGeoCode : reverseGeoCode,
      getAddressCandidates: addressCandidates,
      getCommonLocationCandidates: commonLocationCandidates,
      getCommonLocation: commonLocation,
      getAddressByFolio: addressByFolio,
      getCondoAddress: condoAddress,
      getStandardizedStreet: standardizeStreet,
      initConnection: initConnection,
      getUrl: getUrl,
      getPath: getPath
  }
});

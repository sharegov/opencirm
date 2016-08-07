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
            console.log(results);
            if (status == google.maps.GeocoderStatus.OK) {
                callback(resultsToAddresCandidates(results));
            } else {
                error(status);
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
       candidates.push(resultToAddressCandidate(results[i]));
    }
    return candidates;
  }
  
  function resultToAddressCandidate(result) {
    console.log('google result to candidate', result);
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
  }
});
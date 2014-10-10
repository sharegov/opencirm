/**
 * Google GIS geocoder implementation of cirmgis
 * The google maps module is loaded async using advice here:
 * http://blog.millermedeiros.com/requirejs-2-0-delayed-module-evaluation-and-google-maps
 *
 * Geocoding Guide:
 * https://developers.google.com/maps/documentation/javascript/geocoding
 * 'The Google Maps API provides a geocoder class for geocoding and reverse geocoding dynamically from user input.'
 *
 * Google Gecoder usage limits:
 * https://developers.google.com/maps/documentation/geocoding/#Limits
 * The Google Geocoding API has the following limits in place:
 * Users of the free API:
 * 2,500 requests per 24 hour period.
 * 5 requests per second.
 * Google Maps API for Work customers:
 * 100,000 requests per 24 hour period.
 * 10 requests per second.
 *
 */
define('cirmgis', ['jquery','async', 'async!http://maps.google.com/maps/api/js?v=3&sensor=false'], function ($) {

    var url = null;
    var path = null;
    var geocoder = null;
    var defaultComponentRestrictions = {'administrativeArea' :'Miami-Dade County', 'country':'US'};


    function initConnection() {
        geocoder = new google.maps.Geocoder();
    }

    function ensureInit() {
        if (geocoder == null)
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
        var componentRestrictions = defaultComponentRestrictions;
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
    	var candidate = {address:'', municipality:'', zip: ''};
    	candidate.address =  result.formatted_address.split(",")[0];
    	for (var i=0, l = result.address_components.length; i<l; i++) {
    		if($.inArray('locality',result.address_components[i].types) > -1)
    		{
    			candidate.municipality = result.address_components[i].short_name;
    		}
    		if($.inArray('postal_code', result.address_components[i].types) > -1)
    		{
    			candidate.zip = result.address_components[i].short_name;
    		}
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
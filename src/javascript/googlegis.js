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
 *
 *
 */
define('cirmgis', ['async', 'async!http://maps.google.com/maps/api/js?v=3&sensor=false'], function () {

    var geocoder = null;

    function initConnection() {
        geocoder = new google.maps.Geocoder();
        console.log('googlegis geocoder', geocoder);
        var latlng = new google.maps.LatLng(-34.397, 150.644);
        var mapOptions = {
            zoom: 8,
            center: latlng
        }
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
        error('This is a default implementation based on Google Maps.',
            {status: 404}, 'Address candidates is not available.');
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
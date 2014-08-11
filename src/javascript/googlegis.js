define('cirmgis', ['rest'], function (rest) {

    var url = null;
    var path = null;
    var restClient = null;

    function initConnection(_url, _path) {
        url = _url;
        path = _path;
        console.log('gis rest', rest);
        restClient = new rest.Client("/", {jsonp: true, async: true});
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
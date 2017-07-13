package gov.miamidade.cirm.rest;

import gov.miamidade.cirm.other.ServiceCaseJsonHelper;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import mjson.Json;

import org.sharegov.cirm.rest.RestService;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

/**
 * A GisService.
 *   
 * @author Thomas Hilpold
 *
 */
@Path("gis")
@Produces("application/json")
public class GisService extends RestService
{
	
	/**
	 * Tries to find an address to MD GIS x, y coordinates and returns the address in CiRM form.
	 * For CiRM form see an SR with atAddress parameter.
	 * 
	 * @see ServiceCaseJsonHelper.reverseGeoCode(x, y)
	 * @param x a MD GIS x coordinate inside MiamiDade 
	 * @param y a MD GIS y coordidate inside MiamiDade
	 * @return ok with atAddress property or ko with error message. 
	 */
	@GET
	@Path("/reverseGeocode")
	@Produces("application/json")
	public Json getReverseGeocode(@QueryParam("x") double x, @QueryParam("y") double y)
	{
		ThreadLocalStopwatch.startTop("START getReverseGeocode " + x + ", " + y);
		Json result;
		try {
			//This can block up to 2 minutes, retries are configured:
			result = ServiceCaseJsonHelper.reverseGeoCode(x, y);
			if (result.isNull()) {
				result = GenUtils.ko("MD GIS reverse geo code was unable to " 
						+ "find an address for coordinates x "  + x	+ " y " + y
						+ ". Ensure it's inside Miami Dade."); 
			} else {
				// resolve usps prefixes to iris
				ServiceCaseJsonHelper.assignIris(result);
				// clean up and set type
				if (result.has("Street_Direction") && result.at("Street_Direction").has("USPS_Abbreviation")) {
					result.at("Street_Direction").delAt("USPS_Abbreviation");
				}
				if (result.has("hasStreetType") && result.at("hasStreetType").has("USPS_Suffix")) {
					result.at("hasStreetType").delAt("USPS_Suffix");
				}
				result.set("type" , "Street_Address");
				result = GenUtils.ok().set("atAddress", result);
			}
			ThreadLocalStopwatch.now("END getReverseGeocode ");
		} catch (Exception e) {
			String msg = e.toString() + " during getReverseGeocode with params: x " + x + " y " + y + " : ";
			ThreadLocalStopwatch.fail("FAIL getReverseGeocode with " + e);
			e.printStackTrace();
			result = GenUtils.ko(msg);
		}
		return result;
	}
			
	/**
	 * Tries to find an address to a folio number and returns the address in CiRM form.<br>
	 * For CiRM form, see an SR with atAddress parameter.<br>
	 * Example: <br>
	 * <code>
     *   	  {
     *   	"server": "77",
     *   	"atAddress": {
  	 *			"Street_Name": "149TH CIRCLE",
     *          "Street_Address_State": {
     *             "iri": "http://www.miamidade.gov/ontology#Florida"
     *          },
     *          "Street_Number": "13807",
     *          "Street_Unit_Number": "4-67",
     *          "Street_Direction": {
     *             "iri": "http://www.miamidade.gov/ontology#South_West"
     *          },
     *          "fullAddress": "13807 SW 149TH CIRCLE LN",
     *          "Zip_Code": "33186",
     *          "type": "Street_Address",
     *          "Street_Address_City": {
     *             "iri": "http://www.miamidade.gov/ontology#Miami_Dade_County"
     *          },
     *          "hasStreetType": {
     *             "iri": "http://www.miamidade.gov/ontology#Street_Type_Lane"
     *          }
     *   	},
     *   	"ok": true
     *   }
	 * </code>
	 * 
	 * @param folio a MD GIS folio inside MiamiDade 
	 * @return ok with atAddress property or ko with error message. 
	 */
	@GET
	@Path("/addressByFolio")
	@Produces("application/json")
	public Json getCirmAddressByFolio(@QueryParam("folio") long folio)
	{
		ThreadLocalStopwatch.startTop("START getCirmAddressByFolio " + folio);
		Json result;
		try {
			//This can block up to 2 minutes, retries are configured:
			result = ServiceCaseJsonHelper.getCirmAddressByFolio(folio);
			if (result.isNull()) {
				result = GenUtils.ko("MD GIS getAddressFromFolio was unable to " 
						+ "find an address for folio "  + folio
						+ ". Ensure it's inside Miami Dade."); 
			} else {
				// resolve usps prefixes to iris
				ServiceCaseJsonHelper.assignIris(result);
				// clean up and set type
				if (result.has("Street_Direction") && result.at("Street_Direction").has("USPS_Abbreviation")) {
					result.at("Street_Direction").delAt("USPS_Abbreviation");
				}
				if (result.has("hasStreetType") && result.at("hasStreetType").has("USPS_Suffix")) {
					result.at("hasStreetType").delAt("USPS_Suffix");
				}
				result.set("type" , "Street_Address");
				result = GenUtils.ok().set("atAddress", result);
			}
			ThreadLocalStopwatch.now("END getCirmAddressByFolio");
		} catch (Exception e) {
			String msg = e.toString() + " during getCirmAddressByFolio with param: folio " + folio + " : ";
			ThreadLocalStopwatch.fail("FAIL getCirmAddressByFolio " + msg);
			e.printStackTrace();
			result = GenUtils.ko(msg);
		}
		return result;
	}
}

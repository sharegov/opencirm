package gov.miamidade.cirm.rest;

import gov.miamidade.cirm.other.ServiceCaseJsonHelper;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import mjson.Json;

import org.sharegov.cirm.rest.RestService;
import org.sharegov.cirm.utils.GenUtils;

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
		} catch (Exception e) {
			String msg = e.toString() + " during getReverseGeocode with params: x " + x + " y " + y + " : ";
			System.err.println(msg);
			e.printStackTrace();
			result = GenUtils.ko(msg);
		}
		return result;
	}
		
}

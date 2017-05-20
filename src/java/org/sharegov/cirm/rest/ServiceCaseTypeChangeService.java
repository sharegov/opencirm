package org.sharegov.cirm.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import org.sharegov.cirm.process.ServiceCaseTypeChanger;
import org.sharegov.cirm.utils.GenUtils;

import mjson.Json;

/**
 * Allows SR type changes during approval process with same SR number/id and performs extensive validation. <br>
 * The SR will be loaded and converted into a 311Hub UI compatible format, but will NOT be persisted by this class. <br>
 * 
 * @author Thomas Hilpold
 */
@Path("typeChange")
@Produces("application/json")
public class ServiceCaseTypeChangeService extends RestService {

	/**
	 * Loads a service case converted to a target type for initial approval.
	 * 
	 * @param caseNumber
	 * @param targetTypeFragment
	 * @return
	 */
	@GET
	@Path("/loadAsTargetTypeForApproval/{caseNumber}")
	@Produces("application/json")
	public Json loadAsTargetTypeForApproval(@PathParam("caseNumber") String caseNumber, @QueryParam("targetTypeFragment") String targetTypeFragment) {
		Json q = Json.object("type", "legacy:ServiceCase", "legacy:hasCaseNumber", caseNumber);
		LegacyEmulator emu = new LegacyEmulator();
		ServiceCaseTypeChanger tc = new ServiceCaseTypeChanger();
		//1 try find case
		Json result = emu.lookupServiceCase(q);
		if (result.is("ok", true)) {
			try {
				//2 change type with full validation and exceptions on any error.
				Json targetBo = tc.typeChange(result.at("bo"), targetTypeFragment);
				result = GenUtils.ok().set("bo", targetBo);
			} catch (Exception e) {
				result = GenUtils.ko(e.getMessage());
			}
		}
		return result;
	}
}

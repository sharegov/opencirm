package org.sharegov.cirm.rest;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLClassExpression;

@Path("sradmin")
@Produces("application/json")
public class ServiceCaseAdmin extends RestService {
	
	private static final String PREFIX = "legacy:";
	/**
	 * 
	 *
	 */
	
	@GET
	@Path("/types/enabled")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getEnabledServiceCases() {
		try
		{
			ServiceCaseManager scm = new ServiceCaseManager();
			
			return Response.ok(scm.getEnabled(), MediaType.APPLICATION_JSON).build();
		} catch (Exception e) {
			return Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.type(MediaType.APPLICATION_JSON)
					.entity(Json.object().set("error", e.getClass().getName())
							.set("message", e.getMessage())).build();
		}
	}
	
	@GET
	@Path("/types/disabled")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getDisnabledServiceCases() {
		try
		{
			ServiceCaseManager scm = new ServiceCaseManager();
			
			return Response.ok(scm.getDisabled(), MediaType.APPLICATION_JSON).build();
		} catch (Exception e) {
			return Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.type(MediaType.APPLICATION_JSON)
					.entity(Json.object().set("error", e.getClass().getName())
							.set("message", e.getMessage())).build();
		}
	}
	
	@PUT
	@Path("{srType}/disable")
	public Response disable(@PathParam("srType") String srType, Json aData)
	{
		try
		{ 

			String userName = aData.at("userName").asString();
			String comment = "Disable Service Request "+PREFIX+srType;
			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");
			
			ServiceCaseManager scm = new ServiceCaseManager();
			
			return Response.ok(scm.disable(srType, userName, comment), MediaType.APPLICATION_JSON).build();
		}
		catch(Exception e){
			return Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.type(MediaType.APPLICATION_JSON)
					.entity(Json.object().set("error", e.getClass().getName())
							.set("message", e.getMessage())).build();
		}
		
	}
	
	private void permissionCheck(OWLClassExpression expr){
		//TODO enable security
//		if (!isClientExempt() && reasoner().getSuperClasses(expr, false).containsEntity(owlClass("Protected")))
//			expr = OWL.and(expr, Permissions.constrain(OWL.individual("BO_View"), getUserActors()));
//		else if (!isClientExempt() && !reasoner().getSubClasses(OWL.and(expr, owlClass("Protected")), false).isBottomSingleton())
//		{
//			return ko("Access denied - protected resources could be returned, please split the query.");
//		}
		
	}

}

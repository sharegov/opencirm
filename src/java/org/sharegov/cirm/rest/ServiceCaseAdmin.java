package org.sharegov.cirm.rest;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLClassExpression;
import org.sharegov.cirm.legacy.ServiceCaseManager;

@Path("sradmin")
@Produces("application/json")
public class ServiceCaseAdmin extends RestService {
	
	private static final String PREFIX = "legacy:";
	private static final String KEY = "7ef54dc3a604a1514368e8707d8415";
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
	public Response getDisabledServiceCases() {
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
	
	@GET
	@Path("/types/all")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllServiceCases() {
		try
		{
			ServiceCaseManager scm = new ServiceCaseManager();
			
			return Response.ok(scm.getAll(), MediaType.APPLICATION_JSON).build();
		} catch (Exception e) {
			return Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.type(MediaType.APPLICATION_JSON)
					.entity(Json.object().set("error", e.getClass().getName())
							.set("message", e.getMessage())).build();
		}
	}
	
	@GET
	@Path("/ontology/{ontologyname}/diff")
	@Produces(MediaType.APPLICATION_JSON)
	public Response compareOntos(@PathParam("ontologyname") String ontologyName) {
		try
		{			
			ServiceCaseManager scm = new ServiceCaseManager();
			
			return Response.ok(scm.compare(ontologyName), MediaType.APPLICATION_JSON).build();
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
	
	@PUT
	@Path("{srType}/enable")
	public Response enable(@PathParam("srType") String srType, Json aData)
	{
		try
		{ 
			String userName = aData.at("userName").asString();
			String comment = "Enable Service Request "+PREFIX+srType;
			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");
			
			ServiceCaseManager scm = new ServiceCaseManager();
			
			return Response.ok(scm.enable(srType, userName, comment), MediaType.APPLICATION_JSON).build();
		}
		catch(Exception e){
			return Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.type(MediaType.APPLICATION_JSON)
					.entity(Json.object().set("error", e.getClass().getName())
							.set("message", e.getMessage())).build();
		}
	}
	
	@POST	
	@Path("/push")
	public Response refresh(){
		try
		{			
			ServiceCaseManager scm = new ServiceCaseManager();
			
			return Response.ok(scm.push(), MediaType.APPLICATION_JSON).build();
		}
		catch(Exception e){
			return Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.type(MediaType.APPLICATION_JSON)
					.entity(Json.object().set("error", e.getClass().getName())
							.set("message", e.getMessage())).build();
		}
	}
	
	@POST	
	@Path("/refresh/now")
	public Response refresh(Json aKey)
	{
		try
		{
			String key = aKey.at("key").asString();
			if (key == null || key.isEmpty()) throw new IllegalArgumentException("key needed for this operation");
			if (key.compareTo(KEY) != 0) throw new IllegalArgumentException("key is invalid");
			
			ServiceCaseManager scm = new ServiceCaseManager();
			
			return Response.ok(scm.refreshOnto(), MediaType.APPLICATION_JSON).build();
		}
		catch(Exception e){
			return Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.type(MediaType.APPLICATION_JSON)
					.entity(Json.object().set("error", e.getClass().getName())
							.set("message", e.getMessage())).build();
		}
		
	}
	
	@PUT
	@Path("{srType}/question")
	public Response updateQuestion(@PathParam("srType") String srType, Json aData)
	{
		try
		{ 
			String userName = aData.at("userName").asString();
			String questionUri = aData.at("questionUri").asString();
			String newLabel = aData.at("newLabel").asString();
			String comment = "Update Service Request Question "+PREFIX+srType;
			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");
			if (questionUri == null || questionUri.isEmpty()) throw new IllegalArgumentException("question uri null or empty");
			if (newLabel == null || newLabel.isEmpty()) throw new IllegalArgumentException("new label null or empty");
			
			ServiceCaseManager scm = new ServiceCaseManager();
			
			return Response.ok(scm.replaceObjectAnnotation(questionUri, newLabel, userName, comment), MediaType.APPLICATION_JSON).build();
		}
		catch(Exception e){
			return Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.type(MediaType.APPLICATION_JSON)
					.entity(Json.object().set("error", e.getClass().getName())
							.set("message", e.getMessage())).build();
		}
	}
	
	@GET
	@Path("{srType}/alert")
	public Response getAlert(@PathParam("srType") String srType)
	{
		
		try
		{ 
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");		     
			
			ServiceCaseManager scm = new ServiceCaseManager();
			
			Json result = scm.getServiceCaseAlert(srType);
			
			if (result == null) {
				return Response
						.status(Status.NOT_FOUND)
						.type(MediaType.APPLICATION_JSON).build();
			} else {			
				return Response.ok(result, MediaType.APPLICATION_JSON).build();
			}
		}
		catch(Exception e){
			return Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.type(MediaType.APPLICATION_JSON)
					.entity(Json.object().set("error", e.getClass().getName())
							.set("message", e.getMessage())).build();
		}
	}
	
	@PUT
	@Path("{srType}/alert")
	public Response updateAlert(@PathParam("srType") String srType, Json aData)
	{
		
		try
		{ 
			String userName = aData.at("userName").asString();
			String alertUri = aData.at("alertUri").asString();
			String newLabel = aData.at("newLabel").asString();
			boolean isEnable = aData.at("isEnabled").asBoolean();
			String comment = "Update Service Request Alert "+PREFIX+srType;
			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");
			if (alertUri == null || alertUri.isEmpty()) throw new IllegalArgumentException("alert uri null or empty");
			if (newLabel == null || newLabel.isEmpty()) throw new IllegalArgumentException("new label null or empty");
		     
			
			ServiceCaseManager scm = new ServiceCaseManager();
			
			return Response.ok(scm.replaceObjectAnnotation(alertUri, newLabel, userName, comment), MediaType.APPLICATION_JSON).build();
		}
		catch(Exception e){
			return Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.type(MediaType.APPLICATION_JSON)
					.entity(Json.object().set("error", e.getClass().getName())
							.set("message", e.getMessage())).build();
		}
	}
	
	@POST
	@Path("{srType}/alert")
	public Response createAlert(@PathParam("srType") String srType, Json aData)
	{
		
		try
		{ 
			String userName = aData.at("userName").asString();
			String alertUri = aData.at("alertUri").asString();
			String newLabel = aData.at("newLabel").asString();
			boolean isEnable = aData.at("isEnabled").asBoolean();
			String comment = "Update Service Request Alert "+PREFIX+srType;
			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");
			if (alertUri == null || alertUri.isEmpty()) throw new IllegalArgumentException("alert uri null or empty");
			if (newLabel == null || newLabel.isEmpty()) throw new IllegalArgumentException("new label null or empty");
		     
			
			ServiceCaseManager scm = new ServiceCaseManager();
			
			return Response.ok(scm.replaceObjectAnnotation(alertUri, newLabel, userName, comment), MediaType.APPLICATION_JSON).build();
		}
		catch(Exception e){
			return Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.type(MediaType.APPLICATION_JSON)
					.entity(Json.object().set("error", e.getClass().getName())
							.set("message", e.getMessage())).build();
		}
	}
	
	@PUT
	@Path("update/object")
	public Response updateObjectProperty(Json aData)
	{
		
		try
		{ 
			String userName = aData.at("userName").asString();
			String objectUri = aData.at("objectUri").asString();
			String propertyUri = aData.at("propertyUri").asString();
			String comment = "Update Individial Object Property "+PREFIX+objectUri;
			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (objectUri == null || objectUri.isEmpty()) throw new IllegalArgumentException("object uri null or empty");
			if (propertyUri == null || propertyUri.isEmpty()) throw new IllegalArgumentException("property uri null or empty");
		    			
			ServiceCaseManager scm = new ServiceCaseManager();
			
			return Response.ok(scm.addIndividualObjectPropertyToIndividual(objectUri, propertyUri, aData.at("payload"), userName, comment), MediaType.APPLICATION_JSON).build();
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

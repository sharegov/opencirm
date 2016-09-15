package org.sharegov.cirm.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.semanticweb.owlapi.model.OWLClassExpression;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.legacy.ServiceCaseManager;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import mjson.Json;

@Path("sradmin")
@Produces("application/json")
public class ServiceCaseAdmin extends RestService {
	
	private static final String PREFIX = "legacy:";
	private static final String KEY = "7ef54dc3a604a1514368e8707f8415";
	private static Map<String, Json> cache = new ConcurrentHashMap<String, Json>();
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
			return Response.ok(ServiceCaseManager.getInstance().getEnabled(), MediaType.APPLICATION_JSON).build();
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
			return Response.ok(ServiceCaseManager.getInstance().getDisabled(), MediaType.APPLICATION_JSON).build();
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
			return Response.ok(ServiceCaseManager.getInstance().getAll(), MediaType.APPLICATION_JSON).build();
		} catch (Exception e) {
			return Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.type(MediaType.APPLICATION_JSON)
					.entity(Json.object().set("error", e.getClass().getName())
							.set("message", e.getMessage())).build();
		}
	}
	
	@GET
	@Path("/activities/{department}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getActivites(@PathParam("department") String aDepartment) {		
		try
		{		
			if (aDepartment == null || aDepartment.isEmpty()) throw new IllegalArgumentException("department null or empty");
			
			return Response.ok(ServiceCaseManager.getInstance().getActivities(aDepartment), MediaType.APPLICATION_JSON).build();
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
			return Response.ok(ServiceCaseManager.getInstance().compare(ontologyName), MediaType.APPLICATION_JSON).build();
		} catch (Exception e) {
		    e.printStackTrace();
			return Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.type(MediaType.APPLICATION_JSON)
					.entity(Json.object().set("error", e.getClass().getName())
							.set("message", e.getMessage())).build();
		}
	}
	
	@POST
	@Path("/ontology/rollback")
	@Produces(MediaType.APPLICATION_JSON)
	public Response compareOntos(Json aData) {
		try
		{						
			if (!(aData.has("userName"))) throw new IllegalArgumentException("User Name not found"); 
			if (!(aData.has("revisions"))) throw new IllegalArgumentException("Revisions Name not found"); 
			
			String userName = aData.at("userName").asString();
			Json revisions = aData.at("revisions");
			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (revisions == null || !revisions.isArray()) throw new IllegalArgumentException("Revisions List null or empty");
			
			List<Integer> intRevisions = new ArrayList<>();
			for(Object o : revisions.asList()) intRevisions.add(Integer.valueOf(o.toString()));
						
			return Response.ok(ServiceCaseManager.getInstance().doRollBack(intRevisions), MediaType.APPLICATION_JSON).build();
		} catch (Exception e) {
		    e.printStackTrace();
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
			if (!(aData.has("userName"))) throw new IllegalArgumentException("User Name not found"); 
			
			String userName = aData.at("userName").asString();
			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");
						
			return Response.ok(ServiceCaseManager.getInstance().disable(srType, userName), MediaType.APPLICATION_JSON).build();
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
			if (!(aData.has("userName"))) throw new IllegalArgumentException("User Name not found"); 
			
			String userName = aData.at("userName").asString();
			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");
						
			return Response.ok(ServiceCaseManager.getInstance().enable(srType, userName), MediaType.APPLICATION_JSON).build();
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
	public Response pushToRepo(Json aData){
		try
		{		
			if (!aData.has("userName")) throw new IllegalArgumentException("User Name or Alert data null or empty"); 
			
			String userName = aData.at("userName").asString();
			
			return Response.ok(ServiceCaseManager.getInstance().pushToRepo(userName), MediaType.APPLICATION_JSON).build();
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
	@Path("/deploy/{target}")
	public Response deploy(@PathParam("target") String target, String aJsonString)
	{
		synchronized (cache){		
			Json result = cache.get(target + ":" + aJsonString);
			
			if (result != null && !result.isNull()){
				ThreadLocalStopwatch.now("Identical Request, cached results used as response. End sending deploy signals.");
				
				return Response.ok(result, MediaType.APPLICATION_JSON).build();
			}
			
			Json aData = Json.read(aJsonString);
			
			try
			{
				String key = aData.at("key").asString();
				String date = aData.has("date") ? aData.at("date").asString(): "0";
				if (key == null || key.isEmpty()) throw new IllegalArgumentException("key needed for this operation");
				if (key.compareTo(KEY) != 0) throw new IllegalArgumentException("key is invalid");
				
				result = ServiceCaseManager.getInstance().refreshOnto(target, date, key);
				
				cache.put(target + ":" + aJsonString, result);
				
				return Response.ok(result, MediaType.APPLICATION_JSON).build();
			}
			catch(Exception e){
				return Response
						.status(Status.INTERNAL_SERVER_ERROR)
						.type(MediaType.APPLICATION_JSON)
						.entity(Json.object().set("error", e.getClass().getName())
								.set("message", e.getMessage())).build();
			}
		}
		
	}
	
	@GET
	@Path("{srType}/alert")
	public Response getAlert(@PathParam("srType") String srType)
	{
		
		try
		{ 
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");		     
			
			Json result = ServiceCaseManager.getInstance().getServiceCaseAlert(srType);
			
			if (result == Json.nil()) {
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
			if (!(aData.has("userName") && aData.has("payload") && aData.at("payload").has("iri") && aData.at("payload").has("label"))) throw new IllegalArgumentException("User Name or Alert data null or empty"); 
			
			String userName = aData.at("userName").asString();
			String alertUri = aData.at("payload").at("iri").asString();
			String newLabel = aData.at("payload").at("label").asString();						

			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");
			if (alertUri == null || alertUri.isEmpty()) throw new IllegalArgumentException("alert uri null or empty");
			if (newLabel == null || newLabel.isEmpty()) throw new IllegalArgumentException("new label null or empty");
		     
//			return Response.ok(ServiceCaseManager.getInstance().replaceAlertLabel(srType, alertUri, newLabel, userName), MediaType.APPLICATION_JSON).build();
			return Response.ok(ServiceCaseManager.getInstance().replaceAlertServiceCase(srType, alertUri, newLabel, userName), MediaType.APPLICATION_JSON).build();
		}
		catch(Exception e){
			
			e.printStackTrace();
			
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
			if (!(aData.has("userName") && aData.has("payload") && aData.at("payload").has("iri") && aData.at("payload").has("label")&& aData.at("payload").has("type"))) throw new IllegalArgumentException("User Name or Alert data null/empty/Incomplete"); 
			
			String userName = aData.at("userName").asString();			

			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");	
			
			return Response.ok(ServiceCaseManager.getInstance().addNewAlertServiceCase(srType, aData.at("payload"), userName), MediaType.APPLICATION_JSON).build();
		}
		catch(Exception e){
			e.printStackTrace();
			return Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.type(MediaType.APPLICATION_JSON)
					.entity(Json.object().set("error", e.getClass().getName())
							.set("message", e.getMessage())).build();
		}
		
	}
	
	@DELETE
	@Path("{srType}/alert")
	public Response deleteAlert(@PathParam("srType") String srType, @QueryParam ("userName") String userName)
	{
		
		try
		{					

			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");
			
			return Response.ok(ServiceCaseManager.getInstance().deleteAlertServiceCase(srType, userName), MediaType.APPLICATION_JSON).build();
		}
		catch(Exception e){
			e.printStackTrace();
			return Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.type(MediaType.APPLICATION_JSON)
					.entity(Json.object().set("error", e.getClass().getName())
							.set("message", e.getMessage())).build();
		}
		
	}
	
	@GET
	@Path("schemas/questionSchema")
	public Response getFullQuestionSchema()
	{
		try {			
			String host = java.net.InetAddress.getLocalHost().getHostName();
			String protocol = StartUp.config.at("ssl").asBoolean() ? "https://": "http://";
			String port =  StartUp.config.at("ssl").asBoolean() ? StartUp.config.at("ssl-port").asInteger() != 443 ? ":" + StartUp.config.at("ssl-port").asString(): "": 
																  StartUp.config.at("port").asInteger() != 80 ? ":" + StartUp.config.at("port").asString(): "";
																  
//			return Response.ok (Json.object().set("result", ServiceCaseManager.getInstance().getFullSchema(protocol + host + port + "/javascript/schemas/service_field_compact.json")), MediaType.APPLICATION_JSON).build();																  
		    return Response.ok (Json.object().set("result", ServiceCaseManager.getInstance().getSchema(protocol + host + port + "/javascript/schemas/service_field_compact.json")), MediaType.APPLICATION_JSON).build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.type(MediaType.APPLICATION_JSON)
					.entity(Json.object().set("error", e.getClass().getName())
							.set("message", e.getMessage())).build();
		}
		
	}
	
	@GET
	@Path("{srType}/questions")
	public Response getQuestions(@PathParam("srType") String srType)
	{
		
		try
		{ 
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");		     
			
			Json result = ServiceCaseManager.getInstance().getServiceCaseQuestions(srType);
			
			if (result == Json.nil()) {
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
	
	@POST
	@Path("{srType}/questions")
	public Response createQuestions(@PathParam("srType") String srType, String aJsonStr)
	{		
		synchronized (cache){
			Json result = cache.get(aJsonStr);
			
			if (result != null && !result.isNull()){
				ThreadLocalStopwatch.now("Identical Request, cache results used as response. End Saving Questions.");
				
				return Response.ok(result, MediaType.APPLICATION_JSON).build();
			}
					
			ThreadLocalStopwatch.startTop("Started Saving Questions.");
			
			Json aData = Json.read(aJsonStr);
			
			try
			{ 
				if (!(aData.has("userName") && aData.has("payload") && aData.at("payload").isArray())) throw new IllegalArgumentException("User Name or Question data null/empty/Incomplete"); 
				
				String userName = aData.at("userName").asString();			
	
				if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
				if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");	
				
				result = ServiceCaseManager.getInstance().addQuestionsServiceCase(srType, aData.at("payload"), userName);			
				
				cache.put(aJsonStr, result);
				
				ThreadLocalStopwatch.now("End Saving Questions.");
				
				return Response.ok(result, MediaType.APPLICATION_JSON).build();
			}
			catch(Exception e){
				ThreadLocalStopwatch.now("Error found Saving Questions.");
				
				e.printStackTrace();
				return Response
						.status(Status.INTERNAL_SERVER_ERROR)
						.type(MediaType.APPLICATION_JSON)
						.entity(Json.object().set("error", e.getClass().getName())
								.set("message", e.getMessage())).build();
			}
		}
		
	}
	
	@GET
	@Path("{srType}/activities")
	public Response getActivities(@PathParam("srType") String srType)
	{
		
		try
		{ 
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");		     
			
			Json result = ServiceCaseManager.getInstance().getServiceCaseActivities(srType);
			
			if (result == Json.nil()) {
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
	
	@POST
	@Path("{srType}/activities")
	public Response createActivities(@PathParam("srType") String srType, String aJsonStr)
	{		
		synchronized (cache){
			Json result = cache.get(aJsonStr);
			
			if (result != null && !result.isNull()){
				ThreadLocalStopwatch.now("Identical Request, cache results used as response. End Saving Activities.");
				
				return Response.ok(result, MediaType.APPLICATION_JSON).build();
			}
					
			ThreadLocalStopwatch.startTop("Started Saving Activities.");
			
			Json aData = Json.read(aJsonStr);
			
			try
			{ 
				if (!(aData.has("userName") && aData.has("payload") && aData.at("payload").isArray())) throw new IllegalArgumentException("User Name or Activity data null/empty/Incomplete"); 
				
				String userName = aData.at("userName").asString();			
	
				if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
				if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");	
				
				result = ServiceCaseManager.getInstance().addActivitesServiceCase(srType, aData.at("payload"), userName);			
				
				cache.put(aJsonStr, result);
				
				ThreadLocalStopwatch.now("End Saving Activities.");
				
				return Response.ok(result, MediaType.APPLICATION_JSON).build();
			}
			catch(Exception e){
				ThreadLocalStopwatch.now("Error found Saving Activities.");
				
				e.printStackTrace();
				return Response
						.status(Status.INTERNAL_SERVER_ERROR)
						.type(MediaType.APPLICATION_JSON)
						.entity(Json.object().set("error", e.getClass().getName())
								.set("message", e.getMessage())).build();
			}
		}
		
	}
	
	/**
	 * Adds an existing activity to an existing SR.
	 * 
	 */
	@PUT
	@Path("{srType}/activity")
	public Response addActivity(@PathParam("srType") String srType, String aJsonStr)
	{		
		synchronized (cache){
			Json result = cache.get(aJsonStr);
			
			if (result != null && !result.isNull()){
				ThreadLocalStopwatch.now("Identical Request, cache results used as response. End Saving Activities.");
				
				return Response.ok(result, MediaType.APPLICATION_JSON).build();
			}
					
			ThreadLocalStopwatch.startTop("End Saving Activity.");
			
			Json aData = Json.read(aJsonStr);
			
			try
			{ 
				if (!(aData.has("userName") && aData.has("payload") && aData.at("payload").has("hasActivity") && aData.at("payload").at("hasActivity").has("iri"))) throw new IllegalArgumentException("User Name or Activity data null/empty/Incomplete"); 
				
				String userName = aData.at("userName").asString();			
	
				if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
				if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");	
				
				result = ServiceCaseManager.getInstance().addActivityToServiceCase(srType, aData.at("payload").at("hasActivity"), userName);			
				
				cache.put(aJsonStr, result);
				
				ThreadLocalStopwatch.now("End Saving Activity.");
				
				return Response.ok(Json.object().set("message","ok"), MediaType.APPLICATION_JSON).build();
			}
			catch(Exception e){
				ThreadLocalStopwatch.now("Error found Saving Activity.");
				
				e.printStackTrace();
				return Response
						.status(Status.INTERNAL_SERVER_ERROR)
						.type(MediaType.APPLICATION_JSON)
						.entity(Json.object().set("error", e.getClass().getName())
								.set("message", e.getMessage())).build();
			}
		}
		
	}
	
	/**
	 * Removes an existing activity to an existing SR.
	 * 
	 */
	@DELETE
	@Path("{srType}/activity")
	public Response removeActivity(@PathParam("srType") String srType, String aJsonStr)
	{		
		synchronized (cache){
			Json result = cache.get(aJsonStr);
			
			if (result != null && !result.isNull()){
				ThreadLocalStopwatch.now("Identical Request, cache results used as response. End Saving Activities.");
				
				return Response.ok(result, MediaType.APPLICATION_JSON).build();
			}
					
			ThreadLocalStopwatch.startTop("End Removing Activity.");
			
			Json aData = Json.read(aJsonStr);
			
			try
			{ 
				if (!(aData.has("userName") && aData.has("payload") && aData.at("payload").has("hasActivity") && aData.at("payload").at("hasActivity").has("iri"))) throw new IllegalArgumentException("User Name or Activity data null/empty/Incomplete"); 
				
				String userName = aData.at("userName").asString();			
	
				if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
				if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");	
				
				result = ServiceCaseManager.getInstance().removeActivityFromServiceCase(srType, aData.at("payload").at("hasActivity"), userName);			
				
				cache.put(aJsonStr, result);
				
				ThreadLocalStopwatch.now("End Removing Activity.");
				
				return Response.ok(Json.object().set("message","ok"), MediaType.APPLICATION_JSON).build();
			}
			catch(Exception e){
				ThreadLocalStopwatch.now("Error found Removing Activity.");
				
				e.printStackTrace();
				return Response
						.status(Status.INTERNAL_SERVER_ERROR)
						.type(MediaType.APPLICATION_JSON)
						.entity(Json.object().set("error", e.getClass().getName())
								.set("message", e.getMessage())).build();
			}
		}
		
	}
	
	
	@POST
	@Path("/authorize")
	@Produces(MediaType.APPLICATION_JSON)
	public Response authorize(Json aData) {
		try
		{						
			if (!(aData.has("username"))) throw new IllegalArgumentException("User Name not found"); 
			if (!(aData.has("password"))) throw new IllegalArgumentException("Revisions not found"); 
			if (!(aData.has("provider"))) throw new IllegalArgumentException("Provider not found"); 
			if (!(aData.has("groups"))) throw new IllegalArgumentException("Groups not found"); 
			
			String username = aData.at("username").asString();
			String password = aData.at("password").asString();
			String provider = aData.at("provider").asString();
			Boolean groups = aData.at("groups").asBoolean();
			if (username == null || username.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (password == null || password.isEmpty()) throw new IllegalArgumentException("password List null or empty");
			if (provider == null || !provider.isEmpty()) throw new IllegalArgumentException("invalid value sent for provider");
			if (groups == null || !groups) throw new IllegalArgumentException("invalid value sent for groups");
			
			Json r = (new UserService()).authenticate(aData);
			
			if (r.at("ok").asBoolean()){
				Json user = Json.object()
								.set("cn", r.at("user").at("cn"))
								.set("groups", r.at("user").at("groups"));
				Json result = Json.object()
								  .set("ok", true)
								  .set("user", user);
				
				return Response.ok(result, MediaType.APPLICATION_JSON).build();
			} else {
				return Response
						.status(Status.UNAUTHORIZED)
						.type(MediaType.APPLICATION_JSON)
						.entity(r).build();
			}	
						
			
		} catch (Exception e) {
		    e.printStackTrace();
			return Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.type(MediaType.APPLICATION_JSON)
					.entity(Json.object().set("error", e.getClass().getName())
							.set("message", e.getMessage())).build();
		}
	}
	
	// Experimental. Not in use for the UI
	
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
		    			
			return Response.ok(ServiceCaseManager.getInstance().addIndividualObjectPropertyToIndividual(objectUri, propertyUri, aData.at("payload"), userName, comment), MediaType.APPLICATION_JSON).build();
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
	@Path("test")
	public Response testEndPoint()
	{
		return Response.ok (Json.object().set("result", ServiceCaseManager.getInstance().getFullSchema("http://localhost:8182/javascript/schemas/service_field_compact.json")), MediaType.APPLICATION_JSON).build();
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



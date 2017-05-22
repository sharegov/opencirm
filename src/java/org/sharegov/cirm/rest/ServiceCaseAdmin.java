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
	@Path("/types/activity/{activityIRI}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAtivity(@PathParam("activityIRI") String activityIRI) {
		try
		{			
			return Response.ok(ServiceCaseManager.getInstance().getServiceCasesByActivityFromCache(activityIRI), MediaType.APPLICATION_JSON).build();
		} catch (Exception e) {
			return Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.type(MediaType.APPLICATION_JSON)
					.entity(Json.object().set("error", e.getClass().getName())
							.set("message", e.getMessage())).build();
		}
	}
	
	@GET
	@Path("/activity/{activityIRI}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getServiceCaseByActivity(@PathParam("activityIRI") String activityIRI) {
		try
		{			
			return Response.ok(ServiceCaseManager.getInstance().getAdminSerializedIndividual(activityIRI), MediaType.APPLICATION_JSON).build();
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
	@Path("/outcomes/{department}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getOutcomes(@PathParam("department") String aDepartment) {		
		try
		{		
			if (aDepartment == null || aDepartment.isEmpty()) throw new IllegalArgumentException("department null or empty");
			
			return Response.ok(ServiceCaseManager.getInstance().getOutcomes(aDepartment), MediaType.APPLICATION_JSON).build();
		} catch (Exception e) {
			return Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.type(MediaType.APPLICATION_JSON)
					.entity(Json.object().set("error", e.getClass().getName())
							.set("message", e.getMessage())).build();
		}
	}
	
	@GET
	@Path("/outcomes")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getOutcomes(@QueryParam("depts") String... departments) {		
		try
		{		
			if (departments == null || departments.length == 0) throw new IllegalArgumentException("departments null or empty");
			Json result = ServiceCaseManager.getInstance().getOutcomes(departments);
			return Response.ok(result, MediaType.APPLICATION_JSON).build();
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
			String comment = aData.has("comment")?aData.at("comment").asString():null;
			
			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");
						
			return Response.ok(ServiceCaseManager.getInstance().disable(srType, userName, comment), MediaType.APPLICATION_JSON).build();
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
			String comment = aData.has("comment")?aData.at("comment").asString():null;
			
			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");
						
			return Response.ok(ServiceCaseManager.getInstance().enable(srType, userName, comment), MediaType.APPLICATION_JSON).build();
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
			if (!(aData.has("userName") && aData.has("payload") && aData.at("payload").has("iri") && aData.at("payload").has("rdfs:label"))) throw new IllegalArgumentException("User Name or Alert data null or empty"); 
			
			String userName = aData.at("userName").asString();
			String comment = aData.has("comment")?aData.at("comment").asString():null;
			String alertUri = aData.at("payload").at("iri").asString();
			String newLabel = aData.at("payload").at("rdfs:label").asString();						

			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");
			if (alertUri == null || alertUri.isEmpty()) throw new IllegalArgumentException("alert uri null or empty");
			if (newLabel == null || newLabel.isEmpty()) throw new IllegalArgumentException("new label null or empty");
		     
//			return Response.ok(ServiceCaseManager.getInstance().replaceAlertLabel(srType, alertUri, newLabel, userName), MediaType.APPLICATION_JSON).build();
			return Response.ok(ServiceCaseManager.getInstance().replaceAlertServiceCase(srType, alertUri, newLabel, userName, comment), MediaType.APPLICATION_JSON).build();
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
			if (!(aData.has("userName") && aData.has("payload") && aData.at("payload").has("iri") && aData.at("payload").has("rdfs:label")&& aData.at("payload").has("type"))) throw new IllegalArgumentException("User Name or Alert data null/empty/Incomplete"); 
			
			String userName = aData.at("userName").asString();	
			String comment = aData.has("comment")?aData.at("comment").asString():null;

			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");	
			
			return Response.ok(ServiceCaseManager.getInstance().addNewAlertServiceCase(srType, aData.at("payload"), userName, comment), MediaType.APPLICATION_JSON).build();
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
	@Path("repo/schemas/{schema}")
	public Response getFullQuestionSchema(@PathParam("schema") String schema)
	{
		try {			
			String host = java.net.InetAddress.getLocalHost().getHostName();
			String protocol = StartUp.getConfig().at("ssl").asBoolean() ? "https://": "http://";
			String port =  StartUp.getConfig().at("ssl").asBoolean() ? StartUp.getConfig().at("ssl-port").asInteger() != 443 ? ":" + StartUp.getConfig().at("ssl-port").asString(): "": 
																  StartUp.getConfig().at("port").asInteger() != 80 ? ":" + StartUp.getConfig().at("port").asString(): "";
			String path = "";
																  
			switch (schema){
				case "questions": path = "/javascript/schemas/service_field_list_compact.json";
					break;
				case "activities": path = "/javascript/schemas/activity_list_compact.json";
					break;
				case "activity": path = "/javascript/schemas/activity_compact.json";
					break;
			}
		
			if (path == "") throw new IllegalArgumentException("Invalid schema identifier: " + schema);
																		  
		    return Response.ok (Json.object().set("result", ServiceCaseManager.getInstance().getSchema(protocol + host + port + path)), MediaType.APPLICATION_JSON).build();
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
				String comment = aData.has("comment")?aData.at("comment").asString():null;
				
				if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
				if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");	
				
				result = ServiceCaseManager.getInstance().addQuestionsServiceCase(srType, aData.at("payload"), userName, comment);			
				
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
				String comment = aData.has("comment")?aData.at("comment").asString():null;
				
				if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
				if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");	
				
				result = ServiceCaseManager.getInstance().addActivitesServiceCase(srType, aData.at("payload"), userName, comment);			
				
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
	@Path("{srType}/activity/{activityFragment}")
	public Response addActivity(@PathParam("srType") String srType, @PathParam ("activityFragment") String activityFragment, String aJsonStr)
	{		
				
		Json aData = Json.read(aJsonStr);
		
		try
		{ 
			if (!aData.has("userName")) throw new IllegalArgumentException("User Name null/empty/Incomplete"); 
			
			String userName = aData.at("userName").asString();
			String comment = aData.has("comment")?aData.at("comment").asString():null;

			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");
			if (activityFragment == null || activityFragment.isEmpty()) throw new IllegalArgumentException("Activity Fragment null or empty");		
			
			return Response.ok(ServiceCaseManager.getInstance().addActivityToServiceCase(srType, activityFragment, userName, comment), MediaType.APPLICATION_JSON).build();
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
	
	/**
	 * Removes an existing activity to an existing SR.
	 * 
	 */
	@DELETE
	@Path("{srType}/activity/{activityFragment}")
	public Response removeActivity(@PathParam("srType") String srType, @PathParam ("activityFragment") String activityFragment,  @QueryParam ("userName") String userName, @QueryParam ("comment") String comment)
	{			
		try
		{ 
			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");
			if (activityFragment == null || activityFragment.isEmpty()) throw new IllegalArgumentException("Activity Fragment null or empty");		
			
			return Response.ok(ServiceCaseManager.getInstance().removeActivityFromServiceCase(srType, activityFragment, userName, comment), MediaType.APPLICATION_JSON).build();
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
	
	// create a new individual on the ontology based on the json serialization

	
	@POST
	@Path("ontology/add")
	public Response createIndividual(String aJsonStr)
	{		
		synchronized (cache){
			Json result = cache.get(aJsonStr);
			
			if (result != null && !result.isNull()){
				ThreadLocalStopwatch.now("Identical Request, cache results used as response. End Saving Activities.");
				
				return Response.ok(result, MediaType.APPLICATION_JSON).build();
			}
					
			ThreadLocalStopwatch.startTop("Started Saving Objects to Ontology.");
			
			Json aData = Json.read(aJsonStr);
			
			try
			{ 
				if (!(aData.has("userName") && aData.has("payload") && aData.at("payload").has("iri") && aData.at("payload").has("type"))) throw new IllegalArgumentException("User Name or Object data null/empty/Incomplete"); 
				
				String userName = aData.at("userName").asString();			
				String comment = aData.has("comment")?aData.at("comment").asString():null;
				
				if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
				
				result = ServiceCaseManager.getInstance().addObjectOnto(aData.at("payload").at("iri").asString(), aData.at("payload"), userName, comment);			
				
				cache.put(aJsonStr, result);
				
				ThreadLocalStopwatch.now("End Saving Objects to Ontology.");
				
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
	
	// create a copy of the individual {individualID} on the ontology identified under newID

	
	@POST
	@Path("{individualID}/clone/{newID}")
	public Response cloneIndividual(@PathParam("individualID") String individualID, @PathParam("newID") String newID, String aJsonStr)
	{		
		synchronized (cache){
			Json result = cache.get(aJsonStr);
			
			if (result != null && !result.isNull()){
				ThreadLocalStopwatch.now("Identical Request, cache results used as response. End Saving Activities.");
				
				return Response.ok(result, MediaType.APPLICATION_JSON).build();
			}
					
			ThreadLocalStopwatch.startTop("Started Saving Objects to Ontology.");
			
			Json aData = Json.read(aJsonStr);
			
			try
			{ 
				if (!(aData.has("userName"))) throw new IllegalArgumentException("User Name or Object data null/empty/Incomplete"); 
				
				String userName = aData.at("userName").asString();			
				String comment = aData.has("comment")?aData.at("comment").asString():null;
				
				if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
				
				result = ServiceCaseManager.getInstance().cloneObjectOnto(individualID, newID, userName, comment);			
				
				cache.put(aJsonStr, result);
				
				ThreadLocalStopwatch.now("End Saving Objects to Ontology.");
				
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
			String comment = aData.has("comment")?aData.at("comment").asString():"Update Individial Object Property "+PREFIX+objectUri;
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
	
	/**
	 * Adds an existing activity to an existing SR.
	 * 
	 */
	@PUT
	@Path("{srType}/rename")
	public Response renameServiceCase(@PathParam("srType") String srType, String aJsonStr)
	{		
				
		Json aData = Json.read(aJsonStr);
		
		try
		{ 
			if (!aData.has("userName")) throw new IllegalArgumentException("User Name null/empty/Incomplete"); 
			
			String userName = aData.at("userName").asString();
			String comment = aData.has("comment")?aData.at("comment").asString():null;
			String newName = aData.has("rdfs:label")?aData.at("rdfs:label").asString():null;
			
			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (srType == null || srType.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");
			if (newName == null || newName.isEmpty()) throw new IllegalArgumentException("SR label null or empty");
			
			return Response.ok(ServiceCaseManager.getInstance().rename(srType, newName, userName, comment), MediaType.APPLICATION_JSON).build();
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
	
	@GET
	@Path("test")
	public Response testEndPoint()
	{
		return Response.ok (Json.object().set("result", ServiceCaseManager.getInstance().getFullSchema("http://localhost:8182/javascript/schemas/service_field_compact.json")), MediaType.APPLICATION_JSON).build();
	}
	
	@PUT
	@Path("/activity/{activity}/disable")
	public Response disableActivity(@PathParam("activity") String activity, Json aData)
	{		
		try
		{ 
			if (!(aData.has("userName"))) throw new IllegalArgumentException("User Name not found"); 
			
			String userName = aData.at("userName").asString();
			String comment = aData.has("comment")?aData.at("comment").asString():null;
			
			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (activity == null || activity.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");
						
			return Response.ok(ServiceCaseManager.getInstance().disableActivity(activity, userName, comment), MediaType.APPLICATION_JSON).build();
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
	@Path("/activity/{activity}/enable")
	public Response enableActivity(@PathParam("activity") String activity, Json aData)
	{
		try
		{ 
			if (!(aData.has("userName"))) throw new IllegalArgumentException("User Name not found"); 
			
			String userName = aData.at("userName").asString();
			String comment = aData.has("comment")?aData.at("comment").asString():null;
			
			if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("username null or empty");
			if (activity == null || activity.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");
						
			return Response.ok(ServiceCaseManager.getInstance().enableActivity(activity, userName, comment), MediaType.APPLICATION_JSON).build();
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
	@Path("activity/{activity}/used")
	public Response getActivityDBStatus(@PathParam("activity") String activity)
	{
		
		try
		{ 
			if (activity == null || activity.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");		     
			
			Json result = ServiceCaseManager.getInstance().getActivityDBStatus(activity);
			
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
	
	@GET
	@Path("outcome/{outcome}/used")
	public Response getOutcomeDBStatus(@PathParam("outcome") String outcome)
	{
		
		try
		{ 
			if (outcome == null || outcome.isEmpty()) throw new IllegalArgumentException("SR Type null or empty");		     
			
			Json result = ServiceCaseManager.getInstance().getOutcomeDBStatus(outcome);
			
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
	
}



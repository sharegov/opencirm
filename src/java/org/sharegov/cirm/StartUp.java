/*******************************************************************************
 * Copyright 2014 Miami-Dade County
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.sharegov.cirm;


import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Vector;
import java.util.logging.Level;

import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Variant.VariantListBuilder;
import javax.ws.rs.ext.RuntimeDelegate;

import mjson.Json;

import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.Server;
import org.restlet.data.MediaType;
import org.restlet.data.Protocol;
import org.restlet.data.Status;
import org.restlet.engine.application.Encoder;
import org.restlet.engine.local.DirectoryServerResource;
import org.restlet.ext.jaxrs.JaxRsApplication;
import org.restlet.representation.Representation;
import org.restlet.resource.Directory;
import org.restlet.resource.ServerResource;
import org.restlet.routing.Filter;
import org.restlet.routing.Redirector;
import org.restlet.routing.Router;
import org.restlet.routing.Template;
import org.restlet.service.EncoderService;
import org.sharegov.cirm.legacy.ActivityManager;
import org.sharegov.cirm.legacy.MessageManager;
import org.sharegov.cirm.owl.CachedReasoner;
import org.sharegov.cirm.rdb.RelationalOWLMapper;
import org.sharegov.cirm.rest.MainRestApplication;
import org.sharegov.cirm.rest.OntoAdmin;
import org.sharegov.cirm.utils.AdaptiveClassLoader;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.PhotoUploadResource;

/**
 * Starts up OpenCirm.
 * 
 * @author unknown, Thomas Hilpold
 *
 */
public class StartUp extends ServerResource
{ 

	/**
	 * Switch for stress testing. If true, disables most external calls and dbg output.
	 */
	public static boolean STRESS_TEST_CONFIG = false;

	public static Level LOGGING_LEVEL = Level.INFO;

	public volatile static Json config = Json.object()
			.set("workingDir", "C:/work/opencirm")
			.set("mainApplication", "http://www.miamidade.gov/ontology#CIRMApplication") 
			.set("port", 8182)
			.set("ignorePasswords", true)
			.set("ssl-port", 8183)
			.set("ssl", true)
			.set("keystore", "cirm84.jks")
			.set("storePass", "password")
			.set("keyPass", "password")
			.set("defaultOntologyIRI", "http://www.miamidade.gov/cirm/legacy")
			//.set("ontologyConfigSet", "http://www.miamidade.gov/ontology#ProdConfigSet")
			.set("ontologyConfigSet", "http://www.miamidade.gov/ontology#TestConfigSet")
			//.set("ontologyConfigSet", "http://www.miamidade.gov/ontology#LocalConfigSetXE")
			//.set("ontologyConfigSet", "http://www.miamidade.gov/ontology#LocalConfigSet")
			.set("nameBase", "http://www.miamidade.gov/ontology" )
			.set("customIRIMappingFile", "C:/work/mdcirm/customIRIMap.properties")
			.set("stopExpansionConditionIRI", Json.array(
					"http://www.miamidade.gov/cirm/legacy#providedBy",
					"http://www.miamidade.gov/cirm/legacy#hasChoiceValue"
					))
			.set("metaDatabaseLocation", "c:/temp/testontodb")
			.set("allClientsExempt", true)
			.set("network", Json.object(				
					"user", "cirm-test",
					"password","12345",
					"serverUrl","s0141667",
					"bff","ontology_server_test"))
			.set("ontologyPrefixes", Json.object(
					"legacy:", "http://www.miamidade.gov/cirm/legacy#",
					"mdc:", "http://www.miamidade.gov/ontology#",
					":", "http://www.miamidade.gov/ontology#"
					))
			.set("cachedReasonerPopulate", false);
			
	
	public static Component server = null; 
	public static Component redirectServer = null;
	public static PaddedJSONFilter jsonpFilter = null;
	public static Encoder encoder = null;	
	
	/**
	 * Just for registration
	 */
	private static volatile RequestScopeFilter requestScopeFilter = null;

	public static class CirmServerResource extends DirectoryServerResource 
	{
	    public Representation handle() {
	    	try
	    	{
	    		URI uri = new URI(this.getTargetUri());
	    		String localFilename = URLDecoder.decode(uri.getRawPath(), "UTF-8");
	    		File expectedParent = new File(config.at("workingDir").asString() + "/src");
	    		File thefile = new File(localFilename).getCanonicalFile();
	    		boolean grantaccess = false;
	    		while (thefile.getParentFile() != null && !grantaccess)
	    		{
	    			if (thefile.getParentFile().equals(expectedParent))
	    				grantaccess = true;
	    			thefile = thefile.getParentFile();
	    		}
	    		if (!grantaccess)
	    		{
	    			this.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
	    			return null;
	    		}
	    	}
	    	catch (Exception ex)
	    	{
	    		ex.printStackTrace();
	    		return null;
	    	}
	        Representation result = super.handle();
	        if (result != null)
	        {
//		        System.out.println("Handle: " + result.getMediaType() + " " +	        
//		        result.getLocationRef());
		        if (result.getLocationRef().toString().contains("manifest.appcache"))
		        	result.setMediaType(new MediaType("text/cache-manifest"));
	        }
	        return result;
	    }		
	}

	public static Directory configureDirResource(Directory dir)
	{
        dir.setListingAllowed(false);
        dir.setModifiable(false);
        dir.setTargetClass(CirmServerResource.class);
        return dir;
	}
	
	public static Restlet createRestServicesApp()
	{
	    final JaxRsApplication app = new JaxRsApplication(server.getContext().createChildContext());	    
	    EncoderService encoderService = new EncoderService();
	    encoderService.setEnabled(true);
	    //app.setEncoderService(encoderService);
	    app.getLogger().setLevel(LOGGING_LEVEL);
	    
	    try
	    {
	    	Vector<File> v = new Vector<File>();
	    	v.add(new File(new File(config.at("workingDir").asString()), "hotdeploy"));
		    AdaptiveClassLoader loader = new AdaptiveClassLoader(v, true);
		    MainRestApplication main = new MainRestApplication(loader);
		    main.configure(OWL.individual(config.at("mainApplication").asString()));
		    app.add(main);
	    }
	    catch (Exception ex)
	    {
	    	throw new RuntimeException(ex);
	    }
	    
	    // Set filters.
	    HttpMethodOverrideFilter methodFilter = new HttpMethodOverrideFilter(app.getContext());
	    methodFilter.setNext(app);
	    	
	    jsonpFilter = new PaddedJSONFilter(app.getContext());
	    jsonpFilter.setNext(methodFilter);
//	    TrafficMonitor trafficMonitor = new TrafficMonitor(app.getContext());
//	    trafficMonitor.setNext(jsonpFilter);
	    requestScopeFilter = new RequestScopeFilter(); 
	    requestScopeFilter.setNext(jsonpFilter);
	    
	    encoder = new Encoder(app.getContext(), 
	    		true,
	    		true,
	    		encoderService);
	    encoder.setNext(requestScopeFilter);
	    //set the form param fix as the last filter in the chain.
	    Filter formParamFix = new FormParamAdditionalDecodeFilter(app.getContext());
	    formParamFix.setNext(encoder);
	    return formParamFix;
	}
	
	static URL selfUrl()
	{
		try
		{
			String hostname = java.net.InetAddress.getLocalHost().getHostName();
			boolean ssl = config.is("ssl", true); 
			int port = ssl ? config.at("ssl-port").asInteger() 
						   : config.at("port").asInteger();
			return new URL( ( ssl ? "https://" : "http://") + hostname + ":" + port);
			
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
	}
	
	/**
	 * Disable most external calls and dbg output during stresstesting.
	 */
	public static void stressTestConfig() 
	{
		ActivityManager.USE_TIME_MACHINE = false;
		MessageManager.DISABLE_SEND = true;
		// LegacyEmulator.SEND_BO_TO_INTERFACE = false; // this is triggered by events now, so the event should be removed from ontology
		CachedReasoner.DBG_CACHE_MISS = false;
		// GisClient.USE_GIS_SERVICE = false;
		// GisClient.DBG = false;
	}

	public static void main(String[] args) throws Exception
	{
		if (STRESS_TEST_CONFIG)
			stressTestConfig();
		if( (args.length > 0) )
			config = Json.read(GenUtils.readTextFile(new File(args[0])));
		System.out.println("Using config " + config.toString());
		try
		{
			ConfigSet.getInstance();
		}catch(Throwable t)
		{
			throw new RuntimeException(t);
		}
		
		configureServer();
	    
	    final Restlet restApplication = createRestServicesApp();
	    
	    final Context childCtx = server.getContext().createChildContext();
	    
	    Application javaScriptApplication = new Application(childCtx) {  
	        @Override  
	        public Restlet createInboundRoot() {  
	            return configureDirResource(new Directory(childCtx.createChildContext(), 
	            		"file:///" + config.at("workingDir").asString() + "/src/javascript/"));  
	        }  
	    }; 
	    //server.getDefaultHost().attach("/javascript", application);
	    
	    Application resourcesApplication = new Application(childCtx) {  
	        @Override  
	        public Restlet createInboundRoot() {  
	            return configureDirResource(new Directory(childCtx.createChildContext(), 
	            		"file:///" + config.at("workingDir").asString() + "/src/resources/"));  
	        }  
	    }; 
	    //server.getDefaultHost().attach("/resources", application);

	    // Restlet to upload file from client to server
        Application uploadApplication = new Application(server.getContext().createChildContext()) {
            public Restlet createInboundRoot() {
                Router router = new Router();
                // Attach the resource.
                router.attachDefault(PhotoUploadResource.class);
                return router;
            }
        };
	    //server.getDefaultHost().attach("/upload", application);

	    // Restlet to serve uploaded files from server to client
	    Application uploadedApplication = new Application(childCtx) {  
	        @Override  
	        public Restlet createInboundRoot() {  
	            return configureDirResource(new Directory(childCtx.createChildContext(), 
	            		"file:///" + config.at("workingDir").asString() + "/src/uploaded/"));
	        }  
	    }; 
	    //server.getDefaultHost().attach("/uploaded", application);
	    	    
	    Application healthcheckApplication = new Application(childCtx) {  
	        @Override  
	        public Restlet createInboundRoot() {  
	            return configureDirResource(new Directory(childCtx.createChildContext(), 
	            		"file:///" + config.at("workingDir").asString() + "/src/html/healthcheck.htm"));
	        }  
	    }; 
	    //server.getDefaultHost().attach("/healthcheck.htm", application);
	    
	    Application htmlApplication = new Application(server.getContext().createChildContext()) {  
	        @Override  
	        public Restlet createInboundRoot() {  
	            Directory dir = new Directory(getContext().createChildContext(), 
	            		"file:///" + config.at("workingDir").asString() + "/src/html/");
	            dir.setIndexName("startup.html");
	            return configureDirResource(dir);
	        }
	    }; 	    
	    
	    final Router router = new Router(server.getContext().createChildContext());
        router.setDefaultMatchingMode(Template.MODE_STARTS_WITH);
        router.attach("/healthcheck.htm", healthcheckApplication).setMatchingMode(Template.MODE_EQUALS);
        router.attach("/", htmlApplication).setMatchingMode(Template.MODE_STARTS_WITH);
        router.attach("/go", htmlApplication).setMatchingMode(Template.MODE_STARTS_WITH);	            
        router.attach("/html", htmlApplication).setMatchingMode(Template.MODE_STARTS_WITH);
        router.attach("/javascript", javaScriptApplication).setMatchingMode(Template.MODE_STARTS_WITH);
        router.attach("/resources", resourcesApplication).setMatchingMode(Template.MODE_STARTS_WITH);
        router.attach("/upload", uploadApplication).setMatchingMode(Template.MODE_STARTS_WITH);
        router.attach("/uploaded", uploadedApplication).setMatchingMode(Template.MODE_STARTS_WITH);
        
        if (config.is("startTest", true))
        {
    	    Application testApplication = new Application(server.getContext().createChildContext()) {  
    	        @Override  
    	        public Restlet createInboundRoot() {  
    	            Directory dir = new Directory(getContext().createChildContext(), 
    	            		"file:///" + config.at("workingDir").asString() + "/src/test/");
    	            dir.setIndexName("testsuite.html");
    	            return configureDirResource(dir);
    	        }
    	    }; 	            	
            router.attach("/test", testApplication).setMatchingMode(Template.MODE_STARTS_WITH);    	    
        }
        
        router.setRoutingMode(Router.MODE_BEST_MATCH);
  	    
        final Restlet topRestlet = new Restlet() {                
            @Override  
            public void handle(Request request, Response response) 
            {  
                if (request.getResourceRef().getPath().equals("/") || 
                	request.getResourceRef().getPath().startsWith("/images") ||
                	request.getResourceRef().getPath().equals("/healthcheck.htm") ||
                	request.getResourceRef().getPath().startsWith("/go") ||
                	request.getResourceRef().getPath().startsWith("/favicon.ico") ||
                	request.getResourceRef().getPath().startsWith("/html") ||
                	request.getResourceRef().getPath().startsWith("/test") ||
                	request.getResourceRef().getPath().startsWith("/javascript") ||
                	request.getResourceRef().getPath().startsWith("/resources") ||
                	request.getResourceRef().getPath().startsWith("/upload"))
                {
                	//System.out.println("FILE REQUEST : " + request);// + request.getResourceRef().getPath());
                    router.handle(request, response);
                }
                else 
                {
                	//System.out.println("REST SERVICE : " + request.getResourceRef().getPath());
                	RequestScopeFilter.set("clientInfo", request.getClientInfo());
                    restApplication.handle(request, response);
                }
            }
        };
        
        StartupUtils.disableCertificateValidation();
        attachToServer(topRestlet);
	    
   		RelationalOWLMapper.getInstance();
   		if (config.has("cachedReasonerPopulate") && config.is("cachedReasonerPopulate", true))
   		{
   			OntoAdmin oa = new OntoAdmin();
   			oa.cachedReasonerQ1Populate();
   		}
	    try {
	    	server.start();
	    	if (redirectServer != null) redirectServer.start();
	    } catch (Exception e) {
	    	System.err.println("ERROR ON STARTUP - EXITING");
	    	throw new RuntimeException(e);
	    }
	}
	
	static void commandLineStart(Component server)
	{	    
	    try
	    {
		    BufferedReader stdReader = new BufferedReader(new InputStreamReader(System.in));
		    while (true) 
		    {
		    	System.out.print("\n>");
		    	String line = stdReader.readLine();
		    	System.out.println("Command: " + line);
		    	if ("stop".equals(line.trim()))
		    	{
		    		server.stop();
		    	}
		    	else if ("start".equals(line.trim()))
		    		server.start();
		    	else if ("exit".equals(line.trim()))
		    	{
		    		server.stop();
		    	}
		    }
	    }
	    catch (Exception ex)
	    {
	    	ex.printStackTrace(System.err);
	    	System.exit(-1);
	    }	    		
	}
	
	static void set311RuntimeDelegate()
	{
	    final RuntimeDelegate currentDelegate = RuntimeDelegate.getInstance(); 
	    RuntimeDelegate.setInstance(new RuntimeDelegate() {

			@Override
			public <T> T createEndpoint(
					javax.ws.rs.core.Application application,
					Class<T> endpointType) throws IllegalArgumentException,
					UnsupportedOperationException
			{
				System.out.println("Endpoint: " + endpointType.getName());
				return currentDelegate.createEndpoint(application, endpointType);
			}

			@Override
			public <T> HeaderDelegate<T> createHeaderDelegate(Class<T> type)
			{
				System.out.println("Header: " + type.getName());
				return currentDelegate.createHeaderDelegate(type);
			}

			@Override
			public ResponseBuilder createResponseBuilder()
			{
				System.out.println("create response builder");
				return currentDelegate.createResponseBuilder();
			}

			@Override
			public UriBuilder createUriBuilder()
			{
				System.out.println("create URI builder");
				return currentDelegate.createUriBuilder();
			}

			@Override
			public VariantListBuilder createVariantListBuilder()
			{
				System.out.println("create variant list builder");
				return currentDelegate.createVariantListBuilder();
			}	    	
	    });		
	}	

	
	private static void configureServer() 
	{
		server = new Component();
        if (config.is("ssl", true)) {
        	configureSSLServer(server);
        	redirectServer = new Component();
           	Server http = redirectServer.getServers().add(Protocol.HTTP, config.at("port").asInteger());
           	http.getContext().getParameters().add("ioMaxIdleTimeMs", "" + 24*60*60*1000);
        } else {
           	Server http = server.getServers().add(Protocol.HTTP, config.at("port").asInteger());
           	// this is Jetty configuration parameter to allow for very long
           	// 	server processing (i.e. in debugging mode) with no timeout, otherwise
           	// 	the requests get retried 
           	http.getContext().getParameters().add("ioMaxIdleTimeMs", "" + 24*60*60*1000);
        }
       	// Configure client protocols
       	server.getClients().add(Protocol.HTTP);
       	server.getClients().add(Protocol.FILE);
       	server.getLogger().setLevel(Level.WARNING); //WARNING
	}

	private static void attachToServer(Restlet topRestlet) 
	{	
        if (config.is("ssl", true)) {
        	configureRedirectToSSL(redirectServer, topRestlet);
        }
       	server.getDefaultHost().attach(topRestlet);
	}

	/** 
	 * Configures the SSL server.
	 * 
	 */
	private static void configureSSLServer(Component sslServer) 
	{
		final int sslport = config.at("ssl-port").asInteger();            
        final Server httpsServer = sslServer.getServers().add(Protocol.HTTPS, sslport);
        httpsServer.getContext().getParameters().add("hostname", selfUrl().getHost());
        httpsServer.getContext().getParameters().add("keystorePath", 
        				config.at("workingDir").asString() + "/conf/" + config.at("keystore").asString());            
        httpsServer.getContext().getParameters().add("keystorePassword", config.at("storePass").asString());            
        httpsServer.getContext().getParameters().add("keyPassword", config.at("keyPass").asString()); 
        httpsServer.getContext().getParameters().add("ioMaxIdleTimeMs", "" + 24*60*60*1000);
        configureSSLCipherSuites(httpsServer);        
        //hilpold server.getServers().add(Protocol.HTTP, config.at("port").asInteger());
	}
	
	/**
	 * Configures HTTP > HTTPS redirector.
	 * 
	 * @param topRestlet
	 */
	private static void configureRedirectToSSL(Component httpServer, final Restlet topRestlet) 
	{
    	URL url = selfUrl();
        final Redirector redirector = new Redirector(httpServer.getContext().createChildContext(), 
                url.toString(), 
                Redirector.MODE_CLIENT_FOUND);            

        httpServer.getDefaultHost().attach(new Restlet()
		{
			@Override
			public void handle(Request request, Response response)
			{
				if (request.getProtocol().equals(Protocol.HTTP))
				{
					redirector.handle(request, response);
				} else
					topRestlet.handle(request, response);
			}
		});		
	}

	/**
	 * Enables strong and disables weak cipher suites for a restlet server.
	 * 
	 * @param s a restlet server.
	 */
	private static void configureSSLCipherSuites(Server s) 
	{
		//printSSLCipherSuites();
		Context ctx = s.getContext();
		ctx.getParameters().add("disabledCipherSuites", 
				StartupUtils.getWeakSSLCipherSuitesParamString());
		ctx.getParameters().add("enabledCipherSuites", 
				StartupUtils.getStrongSSLCipherSuitesParamString());
	}
}

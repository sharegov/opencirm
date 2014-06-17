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
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Vector;
import java.util.logging.Level;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Variant.VariantListBuilder;
import javax.ws.rs.ext.RuntimeDelegate;


import mjson.Json;

import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
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
import org.restlet.engine.local.DirectoryServerResource;
import org.restlet.ext.jaxrs.JaxRsApplication;
import org.restlet.representation.Representation;
import org.restlet.resource.Directory;
import org.restlet.resource.ServerResource;
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
import org.sharegov.cirm.utils.SslContextedSecureProtocolSocketFactory;

public class StartUp extends ServerResource
{ 
	public static boolean STRESS_TEST_CONFIG = false;

	public static Json config = Json.object()
			.set("workingDir", "C:/work/opencirm")
			.set("mainApplication", "http://www.miamidade.gov/ontology#CIRMApplication") 
			.set("port", 8182)
			.set("ignorePasswords", true)
			.set("ssl-port", 8183)
			.set("keystore", "cirm.jks")
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
			.set("____metaDatabaseLocation", "c:/temp/testontodb")
			.set("allClientsExempt", false)
			.set("network", Json.object(				
					"user", "bolerio-dev",
					"password","password",
					"serverUrl","s0141667",
					"bff","cirmdevelopmentontology"))
			.set("ontologyPrefixes", Json.object(
					"legacy:", "http://www.miamidade.gov/cirm/legacy#",
					"mdc:", "http://www.miamidade.gov/ontology#",
					":", "http://www.miamidade.gov/cirm/ontology#"
					))
			.set("cachedReasonerPopulate", false);
	
	public static Component server = null;
	public static PaddedJSONFilter jsonpFilter = null;
	/**
	 * Just for registration
	 */
	private static volatile RequestScopeFilter requestScopeFilter = null;

	public static class CirmServerResource extends DirectoryServerResource 
	{
	    @SuppressWarnings("deprecation")
		public Representation handle() {
	    	try
	    	{
	    		URI uri = new URI(this.getTargetUri());
	    		String localFilename = URLDecoder.decode(uri.getRawPath());
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
	    app.setEncoderService(encoderService);
	    app.getLogger().setLevel(Level.WARNING);
	    
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
//	    app.getContext().setDefaultVerifier(verifier);

//	    ChallengeAuthenticator guard = new ChallengeAuthenticator(app.getContext(), ChallengeScheme.HTTP_BASIC, "Tutorial");
//	    guard.setVerifier(verifier);	
//	    guard.setNext(app);
	    
//	    server.getDefaultHost().attach(guard);
	    
	    // Set filters.
	    HttpMethodOverrideFilter methodFilter = new HttpMethodOverrideFilter(app.getContext());
	    methodFilter.setNext(app);
	    	
	    jsonpFilter = new PaddedJSONFilter(app.getContext());
	    jsonpFilter.setNext(methodFilter);
//	    TrafficMonitor trafficMonitor = new TrafficMonitor(app.getContext());
//	    trafficMonitor.setNext(jsonpFilter);
	    requestScopeFilter = new RequestScopeFilter(); 
	    requestScopeFilter.setNext(jsonpFilter);

	    return requestScopeFilter;
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
//		GisClient.USE_GIS_SERVICE = false;
//		GisClient.DBG = false;
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
			t.printStackTrace();
		}
		server = new Component();		
	    server.getServers().add(Protocol.HTTP, config.at("port").asInteger())
	    	// this is Jetty configuration parameter to allow for very long
	        // server processing (i.e. in debugging mode) with no timeout, otherwise
	    	// the requests get retried 
	    	.getContext().getParameters().add("ioMaxIdleTimeMs", "" + 24*60*60*1000);
	    
	    server.getClients().add(Protocol.HTTP);
	    	// Apparently this is not needed for the HTTP client...
	    	//.getContext().getParameters().add("ioMaxIdleTimeMs", "" + 24*60*60*1000);
	    
	    server.getClients().add(Protocol.FILE);
	    server.getLogger().setLevel(Level.WARNING);
	    
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
                	request.getResourceRef().getPath().startsWith("/html") ||
                	request.getResourceRef().getPath().startsWith("/test") ||
                	request.getResourceRef().getPath().startsWith("/javascript") ||
                	request.getResourceRef().getPath().startsWith("/resources") ||
                	request.getResourceRef().getPath().startsWith("/upload"))
                {
//                	System.out.println("FILE REQUEST : " + request.getResourceRef().getPath());
                    router.handle(request, response);
                }
                else 
                {
//                	System.out.println("REST SERVICE : " + request.getResourceRef().getPath());
                	RequestScopeFilter.set("clientInfo", request.getClientInfo());
                    restApplication.handle(request, response);
                }
            }
        };
        
    	disableCertificateValidation();
    	
        if (config.is("ssl", true))
        {
        	URL url = selfUrl();
            final int sslport = config.at("ssl-port").asInteger();            
            final Server httpsServer = server.getServers().add(Protocol.HTTPS, sslport);
            httpsServer.getContext().getParameters().add("hostname", selfUrl().getHost());
            httpsServer.getContext().getParameters().add("keystorePath", 
            				config.at("workingDir").asString() + "/conf/" + config.at("keystore").asString());            
            httpsServer.getContext().getParameters().add("keystorePassword", config.at("storePass").asString());            
            httpsServer.getContext().getParameters().add("keyPassword", config.at("keyPass").asString());            
            
            // setup HTTP redirect to HTTPS
            server.getServers().add(Protocol.HTTP, config.at("port").asInteger());
            final Redirector redirector = new Redirector(server.getContext().createChildContext(), 
                                                   url.toString(), 
                                                   Redirector.MODE_CLIENT_FOUND);            
            server.getDefaultHost().attach(new Restlet() {                
                @Override  
                public void handle(Request request, Response response) 
                {  
//                    System.out.println("Redirect request protocol: " + request.getProtocol() 
//                                       + ", " + Protocol.HTTP);
                    if (request.getProtocol().equals(Protocol.HTTP))
                    {
//                        System.out.println("Redirect from HTTP");
                        redirector.handle(request, response);
//                        System.out.println("Redirect from HTTP done");
                    }
                    else
                        topRestlet.handle(request, response);
                }
            });
            disableCertificateValidation();
        }
        else
        {
    	    server.getDefaultHost().attach(topRestlet);
    	}
	    
   		RelationalOWLMapper.getInstance();
   		if (config.has("cachedReasonerPopulate") && config.is("cachedReasonerPopulate", true))
   		{
   			OntoAdmin oa = new OntoAdmin();
   			oa.cachedReasonerQ1Populate();
   		}
//   		OperationService.getPersister().addRDBListener(new BOChangeListener());
	    server.start();	    
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
	
	public static void disableCertificateValidation()
	{
		// Create a trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager()
		{
			public X509Certificate[] getAcceptedIssuers()
			{
				return new X509Certificate[0];
			}

			public void checkClientTrusted(X509Certificate[] certs,
					String authType)
			{
				// System.out.println("Check client trusted");
			}

			public void checkServerTrusted(X509Certificate[] certs,
					String authType)
			{
				// System.out.println("Check server trusted");

			}
		} };

		// Ignore differences between given hostname and certificate hostname
		HostnameVerifier hv = new HostnameVerifier()
		{
			@Override
			public boolean verify(String hostname, SSLSession session)
			{
				// TODO Auto-generated method stub
				return true;
			}
		};

		// Install the all-trusting trust manager
		try
		{
			// SSLContext sc = SSLContext.getInstance("SSL");
			// sc.init(null, trustAllCerts, new SecureRandom());
			SSLContext ctx = SSLContext.getInstance("TLS");
			ctx.init(new KeyManager[0], trustAllCerts, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(ctx
					.getSocketFactory());
			HttpsURLConnection.setDefaultHostnameVerifier(hv);
			// see:http://stackoverflow.com/questions/1828775/how-to-handle-invalid-ssl-certificates-with-apache-httpclient
			// see:https://code.google.com/p/jsslutils/wiki/ApacheHttpClientUsage
			org.apache.commons.httpclient.protocol.Protocol
					.registerProtocol(
							"https",
							new org.apache.commons.httpclient.protocol.Protocol(
									"https",
									(ProtocolSocketFactory) new SslContextedSecureProtocolSocketFactory(
											ctx, false), 443));
			SSLContext.setDefault(ctx);
		}
		catch (Exception e)
		{
		}
	}
}

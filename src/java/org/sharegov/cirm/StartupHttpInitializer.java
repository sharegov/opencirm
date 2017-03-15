package org.sharegov.cirm;

import java.net.InetAddress;
import java.net.URL;

import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import mjson.Json;

/**
 * Startup initializer that uses this service's Rest API and should therefore run after the http/https servers have started. <br>
 * Allows for cluster global prod/test configuration files and matches fully qualified host names with this host's name to determine
 * which initialization should occur.
 * 
 * @author Thomas Hilpold
 *
 */
public class StartupHttpInitializer {

	/**
	 * Lower case fully qualified hostname of this machine.
	 */
	private String thisHost;
	
	/**
	 * Performs initialization of this host using http/s.
	 * 
	 * @throws Exception if any problem occurs. Service should stop and exit in such case.
	 */
	public void initialize() throws Exception {
		thisHost = InetAddress.getLocalHost().getCanonicalHostName().toLowerCase();
		checkStartDepartmentIntegration();
	}

	/**
	 * Starts department integration on this host, if the configuration property startDepartmentIntegration contains 
	 * this host's fully qualified canonical host name. <br>
	 * Returns if startDepartmentIntegration is not configured or contains a non matching hostname value.<br>
	 * <br>
	 * 
	 * @throws IllegalStateException if starting department integration failed or it was already started.
	 */
	private void checkStartDepartmentIntegration() {
		if  (!StartUp.getConfig().has("startDepartmentIntegration")) return; 
		String depIntegrationHost = StartUp.getConfig().at("startDepartmentIntegration").asString().toLowerCase();
		if (depIntegrationHost.equals(thisHost)) {
			ThreadLocalStopwatch.now("START via HTTP Start DepartmentIntegration on this host: " + thisHost);
			URL startUrl = GenUtils.makeLocalURL("/legacy/departments/start");
			String response = GenUtils.httpPost(startUrl.toString(), "", (String[])null);
			Json resp = Json.read(response);
			if (!resp.at("ok").asBoolean()) {
				ThreadLocalStopwatch.now("FAILED via HTTP Start DepartmentIntegration ");			
				throw new IllegalStateException("Could not start department integration.");
			} else {
				ThreadLocalStopwatch.now("SUCCESS via HTTP Start DepartmentIntegration ");
			}
		}
	}
}

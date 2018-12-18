package gov.miamidade.cirm.rest;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import gov.miamidade.cirm.GisClient;
import mjson.Json;

/**
 * GisProxyService to access MDCGIS from same origin until MDCGIS supports CORS.
 * 
 * @author Thomas Hilpold
 *
 */
@Path("gisProxy")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GisProxyService {
	
	
	/**
	 * candidates proxy
	 * @param queryparams
	 * @return
	 */
	@GET
	@Path("/candidates")
	@Consumes("application/json")
	public Json candidates(@Context UriInfo info) {
		MultivaluedMap<String, String>queryParams = info.getQueryParameters();
		String pathAndparams = "/candidates?";
		for (String k : queryParams.keySet()) {
			String v = queryParams.getFirst(k);
			if (v==null) v="";
			pathAndparams+= k+ "=" + urlencodeUTF8(v) + "&";
		}
		//Cache bust
		pathAndparams+= "_=" + System.currentTimeMillis();
		String url = getBaseUrl() + pathAndparams;
		ThreadLocalStopwatch.startTop("GisProxy: " + url);
		return GenUtils.httpGetJson(url);
	}
	
	
	@GET
	@Path("/addressFromCoords")
	@Consumes("application/json")
	public Json addressFromCoords(@Context UriInfo info) {
		MultivaluedMap<String, String> queryParams = info.getQueryParameters();
		String pathAndparams = "/addressFromCoords?";
		for (String k : queryParams.keySet()) {
			String v = queryParams.getFirst(k);
			if (v==null) v="";
			pathAndparams+= k+ "=" + urlencodeUTF8(v) + "&";
		}
		//Cache bust
		pathAndparams+= "_=" + System.currentTimeMillis();
		String url = getBaseUrl() + pathAndparams;
		ThreadLocalStopwatch.startTop("GisProxy: " + url);
		return GenUtils.httpGetJson(url);
	}
	
	@GET
	@Path("/condoaddress")
	@Consumes("application/json")
	public Json condoaddress(@Context UriInfo info) {
		MultivaluedMap<String, String> queryParams = info.getQueryParameters();
		String pathAndparams = "/condoaddress?";
		//street, zip, unit only
		for (String k : queryParams.keySet()) {
			String v = queryParams.getFirst(k);
			if (v==null) v="";
			pathAndparams+= k+ "=" + urlencodeUTF8(v) + "&";
		}
		//Cache bust
		pathAndparams+= "_=" + System.currentTimeMillis();
		String url = getBaseUrl() + pathAndparams;
		ThreadLocalStopwatch.startTop("GisProxy: " + url);
		return GenUtils.httpGetJson(url);
	}
	
	
	//
	// HELPER METHODS
	//
	
	String getBaseUrl() {
		return GisClient.getGisServerUrl();
	}
	
	String urlencodeUTF8(String str) {
		try {
			return URLEncoder.encode(str, "UTF-8");
		}catch(UnsupportedEncodingException e) {
			throw new RuntimeException("GisProxy encode failed ", e);
		}
	}
}

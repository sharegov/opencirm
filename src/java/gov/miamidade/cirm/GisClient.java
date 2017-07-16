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
package gov.miamidade.cirm;

import static org.sharegov.cirm.OWL.dataProperty;

import gov.miamidade.cirm.cache.LocationInfoCache;
import gov.miamidade.cirm.search.GisResultFilter;
import gov.miamidade.cirm.search.GisServiceMapping;
import gov.miamidade.cirm.search.GisServiceRule;

import java.net.URLEncoder;

import mjson.Json;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.gis.GisException;
import org.sharegov.cirm.gis.GisInterface;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.Mapping;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;


/**
 * <p>
 * This is a utility class that deals with managing GIS information related to a service
 * case. All methods that call the GIS services will throw a GisException in case the call
 * fails for whatever reason (the HTTP connection itself failed, or the service return an 
 * error which it's never supposed to do). 
 * </p>
 * 
 * @author boris, Thomas Hilpold
 *
 */
public class GisClient implements GisInterface
{
	/**
	 * Duration in millisecond before an entry is considered invalid and will be purged.
	 */
	public static final long LOCATION_INFO_CACHE_EXPIRATION_MS = 4 * 60 * 60 * 1000L; //4 hours
	
	/**
	 * Maximum number of cached entries. Cache will purge all expired and at least 30% of entries if size exceeded.
	 * null is never cached.
	 */
	public static final int LOCATION_INFO_CACHE_MAX_SIZE = 10000; //entry
	
	public static boolean DBG = true;
	public static boolean DBGX = false;
	public static boolean DBGSQL = false;
	
	public static boolean USE_GIS_SERVICE = true;
	
	private final LocationInfoCache locationInfoCache = new LocationInfoCache(LOCATION_INFO_CACHE_EXPIRATION_MS, LOCATION_INFO_CACHE_MAX_SIZE);

	
	public static String getGisServerUrl()
	{
		OWLNamedIndividual gisConfig = Refs.configSet.resolve().get("GisConfig");
		if (gisConfig == null)
			return null;
		OWLLiteral gisUrl = dataProperty(gisConfig, "hasUrl");
		return  (gisUrl == null) ? null : gisUrl.getLiteral();
	}
	
	@SuppressWarnings("deprecation")
	public static Json findCandidates(String street, String zip, String municipality)
	{
		if (!USE_GIS_SERVICE) return GenUtils.ok().set("USE_GIS_SERVICE", "DISABLED");
        HttpClient client = new HttpClient();
        StringBuffer url = new StringBuffer(getGisServerUrl() + "/candidates?");
		if (street != null)
			url.append("street=" + URLEncoder.encode(street) + "&");
		if (zip != null)
			url.append("zip=" + zip + "&");
		if (municipality != null)
			url.append("municipality=" + municipality + "&");
        GetMethod method = new GetMethod(url.toString());
        try
        {
	        int statusCode = client.executeMethod(method);
	        if (statusCode != HttpStatus.SC_OK)
	            throw new GisException("HTTP Error " + statusCode + " while calling " + url.toString());
	        Json result = Json.read(method.getResponseBodyAsString());
	        if (result.is("ok", true))
	        	return result.at("candidates");
	        else 
	        	throw new GisException("Can't fetch candidates for " + street + "," + zip + ","
	        			+ municipality + ", error : " + result.at("message"));
        }
        catch (GisException ex)
        {
        	throw ex;
        }
        catch (Exception ex)
        {
        	throw new GisException(ex);
        }
        finally
        {
            method.releaseConnection();
        }			
	}
	
	public static Json getAddressFromCoordinates(double x, double y, int maxRetries, long sleepTime)
	{
		RuntimeException lastEx = new RuntimeException("Not tried at all.");
		while (maxRetries-- > 0)
		{
			try
			{
				return getAddressFromCoordinates(x,y);
			}
			catch (RuntimeException ex)
			{
				lastEx = ex;
				// TODO - what's the right way to determine that it's worth retrying the request?
				if (ex.toString().indexOf("Unable to complete  operation") > -1)
				{
					// then we can retry...
				}
				else if (ex instanceof GisException)
					throw ex;
				else
					throw new GisException(ex);
			}
			try { Thread.sleep(sleepTime); } catch (InterruptedException t) { break; }
		}
		throw lastEx;
	}
	
	public static Json getAddressFromCoordinates(double x, double y)
	{
		if (!USE_GIS_SERVICE) return GenUtils.ok().set("USE_GIS_SERVICE", "DISABLED");
		///addressFromCoords?y=488984.2498927936&x=857865.8465290442
        HttpClient client = new HttpClient();
        StringBuffer url = new StringBuffer(getGisServerUrl() + "/addressFromCoords?x=" + x + "&y=" + y);
        GetMethod method = new GetMethod(url.toString());
        try
        {
            int statusCode = client.executeMethod(method);
            if (statusCode != HttpStatus.SC_OK)
                throw new GisException("HTTP Error " + statusCode + " while calling " + url.toString());
            Json result = Json.read(method.getResponseBodyAsString());
            if (result.is("ok", true))
            	return result.at("address");
            else 
            	throw new GisException("Can't fetch candidates for " + x + "," + y + ", error : " + result.at("message"));
        }
        catch (GisException ex)
        {
        	throw ex;
        }
        catch (Exception ex)
        {
        	throw new GisException(ex);
        }
        finally
        {
            method.releaseConnection();
        }			
	}
	
	/**
	 * Gets an address to a folio number (with parsedAddress, municipality, propertyInfo, x,y, and various other information).
	 * 
	 * @param folio
	 * @param maxRetries
	 * @param sleepTimeMs
	 * @return
	 */
	public static Json getAddressFromFolio(long folio, int maxRetries, long sleepTimeMs) {
		if (maxRetries < 0) throw new IllegalArgumentException("maxRetries < 0");
		Json result;
		int attempts = 0;
		do {
			attempts ++;
			try {				
				result = getAddressFromFolio(folio);
			} catch (Exception e) {
				result = null;
				try {
					Thread.sleep(sleepTimeMs);
				} catch(InterruptedException ie) {};
			}
		} while (result == null && attempts <= maxRetries);
		return result;
	}

	/**
	 * Gets an address to a folio number (with parsedAddress, municipality, propertyInfo, x,y, and various other information).
	 * @param folio
	 * @return
	 */
	public static Json getAddressFromFolio(long folio)
	{
		if (!USE_GIS_SERVICE) return GenUtils.ok().set("USE_GIS_SERVICE", "DISABLED");
		// /address/?folio=3050070430001
        HttpClient client = new HttpClient();
        StringBuffer url = new StringBuffer(getGisServerUrl() + "/address/?folio=" + folio);
        GetMethod method = new GetMethod(url.toString());
        try
        {
            int statusCode = client.executeMethod(method);
            if (statusCode != HttpStatus.SC_OK)
                throw new GisException("HTTP Error " + statusCode + " while calling " + url.toString());
            Json result = Json.read(method.getResponseBodyAsString());
            if (result.is("ok", true))
            	return result.at("address");
            else 
            	throw new GisException("Can't fetch address to folio " + folio + " error : " + result.at("message"));
        }
        catch (GisException ex)
        {
        	throw ex;
        }
        catch (Exception ex)
        {
        	throw new GisException(ex);
        }
        finally
        {
            method.releaseConnection();
        }			
	}
	
	public Json getLocationInfo(double x, double y, String [] layers, int maxAttempts, long sleepTime) {
		
		RuntimeException lastEx = new RuntimeException("Not tried at all.");
		while (maxAttempts > 0)
		{
			maxAttempts = maxAttempts - 1;
			try
			{
				return getLocationInfo(x, y, layers);
			}
			catch (RuntimeException ex)
			{
				lastEx = ex;
				if (ex.toString().contains("java.net.SocketTimeoutException") 
						|| ex.toString().contains("java.net.SocketException")
						|| ex.toString().contains("code:400, message:Failed to execute query"))
				{
					// Try again
				}
				else if (ex instanceof GisException)
					throw ex;
				else
					throw new GisException(ex);
			}
			try { Thread.sleep(sleepTime); } catch (InterruptedException t) { break; }
		}
		throw lastEx;
	}

	public Json getLocationInfo(double x, double y, String [] layers) {
		Json result;
		result = locationInfoCache.get(x, y, layers);
		if (result == null) {
			result = getLocationInfoInt(x, y, layers);
			if (result != null) {
				locationInfoCache.put(x, y, layers, result);
			}
		}
		return result;
	}

	private Json getLocationInfoInt(double x, double y, String [] layers)
	{
		if (!USE_GIS_SERVICE) 
		    return GenUtils.ok().set("USE_GIS_SERVICE", "DISABLED");
		HttpClient client = new HttpClient();
        StringBuffer url = new StringBuffer(getGisServerUrl() + "/servicelayers?x=" + 
        		x + "&y=" + y);
        if(layers != null && layers.length > 0)
        {
        	for(String layer : layers)
        	{
        		url.append( "&layer=").append(layer);
        	}
        }
//        System.out.println(url.toString());
        GetMethod method = new GetMethod(url.toString());
        try
        {
        	ThreadLocalStopwatch.now("Start GisService getLocationInfo Call");
            int statusCode = client.executeMethod(method);
        	ThreadLocalStopwatch.now("End GisService getLocationInfo Call");
            if (statusCode != HttpStatus.SC_OK)
                throw new GisException("HTTP Error " + statusCode + " while calling " + url.toString());
            Json result = Json.read(method.getResponseBodyAsString());            
            if (!result.is("ok", true)) 
            	throw new GisException("GisService returned error:" + result.at("message") + " as response to " + url.toString());
            else
            	return result.at("data");
        }
        catch (GisException ex)
        {
        	throw ex;
        }
        catch (Exception ex)
        {
        	throw new GisException(ex);
        }
        finally
        {
            method.releaseConnection();
        }	
	}
	
	
	@SuppressWarnings("deprecation")
	public Json getExtendedGisInfo(String locationType, 
    								  String street1,
    								  String street2, 
    								  String street3, 
    								  String street4)
	{
		if (!USE_GIS_SERVICE) return GenUtils.ok().set("USE_GIS_SERVICE", "DISABLED");
		Json result = Json.nil();
		StringBuffer params = new StringBuffer();
		String servicename = "";
		if(locationType != null)
		{
			if(locationType.equalsIgnoreCase("Area") && 
			   street1 != null && street2 != null && street3 != null && street4 != null)
			{
				servicename = "publicworksareadata";
				params.append("street1=").append(URLEncoder.encode(street1))
  	    			  .append("&street2=").append(URLEncoder.encode(street2))
		    		  .append("&street3=").append(URLEncoder.encode(street3))
		    		  .append("&street4=").append(URLEncoder.encode(street4));
			}
			else if(locationType.equalsIgnoreCase("Corridor") && 
					street1 != null && street2 != null && street3 != null)
			{
				servicename = "publicworkscorridordata";				
				params.append("street1=").append(URLEncoder.encode(street1))
    			      .append("&street2=").append(URLEncoder.encode(street2))
	    		      .append("&street3=").append(URLEncoder.encode(street3));
			}
			else if(locationType.equalsIgnoreCase("Intersection") && street1 != null && street2 != null)
			{
				servicename = "publicworksintersectiondata";
				params.append("street1=").append(URLEncoder.encode(street1))
				      .append("&street2=").append(URLEncoder.encode(street2));				
			} else {
				servicename = null;
			}
		}
		
		HttpClient client = new HttpClient();
        String url = getGisServerUrl() + "/" + servicename + "?" + params.toString();
        GetMethod method = new GetMethod(url);
        try
        {
        	ThreadLocalStopwatch.now("Start GisAreaData Call");
            int statusCode = client.executeMethod(method);
            if (statusCode != HttpStatus.SC_OK) {
            	ThreadLocalStopwatch.error("ERROR GisAreaData Call: Http status " + statusCode + " for " + url);
                throw new GisException("HTTP Error " + statusCode + " while calling " + url.toString());
            }
            result = Json.read(method.getResponseBodyAsString());            
        	ThreadLocalStopwatch.now("End GisAreaData Call");
            if (!result.is("ok", true)) { 
            	ThreadLocalStopwatch.error("ERROR GisAreaData Call: " + result.at("message") + " for " + url);
            	throw new GisException("GisService returned error:" + result.at("message") + " as response to " + url.toString());
            } else {
            	result = result.at("data");
            }
        }
        catch (GisException ex)
        {
        	throw ex;
        }
        catch (Exception ex)
        {
        	ThreadLocalStopwatch.error("ERROR GisAreaData Call: " + ex + " for " + url);
        	throw new GisException(ex);
        }
        finally
        {
            method.releaseConnection();
        }	
		
		return result;
	}
	
	public Json getInformationForCase(Json scase)
	{
        // If we have a public works case (interface==MD-PWS) and
        // a location type that's not a property, we have to get extended
        // attribute that can potentially span a larger area and have
        // multiple values for what would otherwise be scalars (e.g. district number)
        Json props = scase.at("properties");
        if (!props.has("hasLegacyInterface"))
            return Json.nil();
        if (!props.is("hasLegacyInterface", "MD-PWS") &&
            !props.at("hasLegacyInterface").is("hasLegacyCode", "MD-PWS"))
            return Json.nil();
        String locationType = null, s1 = null, s2 = null, s3 = null, s4 = null;
        for (Json ans : props.at("hasServiceAnswer").asJsonList())
        {
            String f = ans.at("hasServiceField").at("iri").asString(); 
            if (f.contains("GIS1"))
                locationType = ans.at("hasAnswerObject").at("label").asString(); 
            else if (f.contains("GIS2"))
                s1 = ans.at("hasAnswerValue").at("literal").asString(); 
            else if (f.contains("GIS3"))
                s2 = ans.at("hasAnswerValue").at("literal").asString(); 
            else if (f.contains("GIS4"))
                s3 = ans.at("hasAnswerValue").at("literal").asString(); 
            else if (f.contains("GIS5"))
                s4 = ans.at("hasAnswerValue").at("literal").asString(); 
        }
        return locationType==null || locationType.equals("Location/Property") ? 
                    Json.nil() : 
                    getExtendedGisInfo(locationType, s1, s2, s3, s4);
	    
	}
	
    public Mapping<Json, Boolean> makeGisFilter(Json locationInfo, boolean removeUnavailable, String[] layers)
    {
    	return new GisResultFilter(locationInfo, removeUnavailable, layers);
    }
    
    public boolean testLayerValue(Json locationInfo, 
    							  String layerName, 
    							  String attributeName, 
    							  String valueExpression)
    {		
		GisServiceRule gisRule = GisServiceRule.make(layerName, 
													 attributeName, 
													 valueExpression);
		Json svc = locationInfo.at(layerName, Json.object());				
		if(!svc.isNull())
		{
			if (!svc.isArray())
			{
				if (gisRule.eval(svc.at(attributeName, Json.nil()).getValue()))
					return true;
			}
			else for (Json data : svc.asJsonList())
			{
				if (gisRule.eval(data.at(attributeName, Json.nil()).getValue()))
					return true;
			}		
		}
    	return false;
    }
    
    public boolean isAvailable(Json propertyInfo, IRI caseType)
    {
		Json gisInfo = new GisResultFilter(propertyInfo, true).getGisInfo();
		// System.out.println(gisInfo.toString());
		return GisServiceMapping.get().isAvailable(caseType.getFragment(), gisInfo);    	
    }
}

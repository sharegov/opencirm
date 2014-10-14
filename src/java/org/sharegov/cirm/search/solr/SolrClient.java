/*******************************************************************************
 * Copyright (c) 2011 Miami-Dade County.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Miami-Dade County - initial API and implementation
 ******************************************************************************/
package org.sharegov.cirm.search.solr;

import static org.sharegov.cirm.search.solr.SearchQuery.field;

import java.util.Map;
import mjson.Json;
import static mjson.Json.*;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.ConfigSet;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.utils.RESTClient;

public class SolrClient
{
	/**
	 * 
	 * <p>
	 * Add a single document to the search engine and commit right away.
	 * </p>
	 * 
	 * @param fields
	 */
	public void submitDocument(String url, 
							   Map<String, Object> fields,
							   Float boost, 
							   Map<String, Float> fieldBoost)
	{
		Json doc = object().set("url", url);
		for (Map.Entry<String, Object> e : fields.entrySet())
		{
			if (e.getValue() == null) // don't remember how this can be, but...
				continue;			
			Float b = (fieldBoost == null ? null : fieldBoost.get(e.getKey()));
			if (b != null)
				doc.set(e.getKey(), object("boost", b, "value", e.getValue()));
			else
				doc.set(e.getKey(), e.getValue());
		}		
		Json j = object("doc", doc);
		if (boost != null)
			j.set("boost", boost);
		Json result = RESTClient.post(getServerUrl() + "/update/?commit=true", object("add", j));
		if (!result.at("responseHeader").is("status", 0))
			throw new RuntimeException("Failed to submit document '"
			+ j + "' to Solr at url '" + getServerUrl() + "' -- " + result);			
	}

	public void removeDocument(String id)
	{
		Json result = RESTClient.post(getServerUrl() + "/update?commit=true", 
				object().set("delete", object("id", id)));
		if (!result.at("responseHeader").is("status", 0))
			throw new RuntimeException("Failed to delete document '"
			+ id + "' to Solr at url '" + getServerUrl() + "' -- " + result);			
	}

	public void removeDocument(long id)
	{
		Json result = RESTClient.post(getServerUrl() + "/update?commit=true", 
				object().set("delete", object("id", id)));
		if (!result.at("responseHeader").is("status", 0))
			throw new RuntimeException("Failed to delete document '"
			+ id + "' to Solr at url '" + getServerUrl() + "' -- " + result);			
	}

	public void optimize()
	{
		Json result = RESTClient.post(getServerUrl(), object().set("optimize", object()));
		if (!result.at("responseHeader").is("status", 0))
			throw new RuntimeException("Failed to optimize Solr at url '" + getServerUrl() + "'");			
	}

	public String getServerUrl()
	{
		OWLNamedIndividual ind = ConfigSet.getInstance().get("SearchService");
		return ind == null ? null : OWL.dataProperty(ind, "hasUrl").getLiteral(); 
	}

	public Json search(SearchQuery query, SearchResultTransform transform) //, DefaultSearchResultList out)
	{
		// TMP - use of apache.commons HttpClient because of a bug in Restlet framework
		// when fetching a ClientResource within a restlet server thread - No CallContext exception.
		// (see http://markmail.org/thread/2rnkiwfsznbjgb4s)
        HttpClient client = new HttpClient();
        String url = getServerUrl() + "/select" + query + "&wt=json";
        //System.out.println(url);
        GetMethod method = new GetMethod(url);
        try
        {
            int statusCode = client.executeMethod(method);
            if (statusCode != HttpStatus.SC_OK)
                throw new RuntimeException("HTTP Error " + statusCode + " while calling " + url);
            Json result = transform.eval(Json.read(method.getResponseBodyAsString()));
            return result;
        }
        catch (Exception ex)
        {
        	throw new RuntimeException(ex);
        }
        finally
        {
            method.releaseConnection();
        }

        
//		try
//		{
//			String result = RESTClient.get(getServerUrl() + "/select" + query + "&wt=json");
//			System.out.println(result);
//			return read(result);
//		}
//		catch (Exception ex)
//		{
//			throw new RuntimeException(ex);
//		}
	}
	
	public static void main(String []argv)
	{
		SolrClient cl = new SolrClient();
		SearchResultTransform transform = new SearchResultTransform();
		Json result = cl.search(new SearchQuery(field("text", "tax")), transform);
		System.out.println(result);
	}
}
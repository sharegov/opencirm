package org.sharegov.cirm.search;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import mjson.Json;

import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.Mapping;

public class GoogleSearchEngine implements SearchEngine
{
	private static final String APIURL = "http://ajax.googleapis.com/ajax/services/search/web?v=1.0&q=";
	
	@Override
	public Json find(String question, Json params, Mapping<Json, Boolean> filter)
	{
		Json gresult;
		try
		{
			gresult = GenUtils.httpGetJson(APIURL + URLEncoder.encode(question, "UTF-8"));
		}
		catch (UnsupportedEncodingException e)
		{
			throw new RuntimeException(e);
		}
		if (gresult.at("responseStatus").asInteger() !=  200)
			return GenUtils.ko(gresult.at("responseDetails").toString());
		else
			gresult = gresult.at("responseData");
		Json result = Json.object("docs", Json.array(), 
							"total", gresult.at("cursor").at("resultCount"));
		for (Json doc : gresult.at("results").asJsonList())
			result.at("docs").add(Json.object(
				"title", doc.at("title"),
				"url", doc.at("url"),
				"summary", doc.at("content"),
				"ontology", Json.array()
			));
		return GenUtils.ok().with(result);
	}

	public static void main(String []argv)
	{
		System.out.println(new GoogleSearchEngine().find("tax", Json.object(), null));
	}
}
package org.sharegov.cirm.search.solr;

import java.util.List;

import mjson.Json;

import org.sharegov.cirm.utils.Mapping;

/**
 * Search result transform class that currently just inspects the json
 * and removes negated(those that begin with '~') ontology iris from
 * the result. 
 * 
 * @author Syed
 *
 */
public class SearchResultTransform implements Mapping<Json, Json>
{

	@Override
	public Json eval(Json r)
	{
		for(Json doc : r.at("response").at("docs").asJsonList())
		{
			List<Json> o = doc.at("ontology").asJsonList();
			for (int i = 0 ; i < o.size(); i++)
			{
				if(o.get(i).asString().startsWith("~"))
				{
					o.remove(i);
				}
			}
		}
		return r;
	}

}

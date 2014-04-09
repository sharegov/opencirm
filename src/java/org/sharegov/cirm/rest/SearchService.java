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
package org.sharegov.cirm.rest;

import static mjson.Json.object;
import static org.sharegov.cirm.utils.GenUtils.ko;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import mjson.Json;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.search.solr.SolrSearchEngine;
import org.sharegov.cirm.utils.Mapping;

@Path("search")
@Produces("application/json")
@Consumes("application/json")
public class SearchService extends RestService
{
	/**
	 * <p>
	 * Search specifically for KB topics. The following parameters are recognized (a name in dot
	 * notation implies a nested object property):
	 * <table>
	 * <tr>
	 * <td>query</td>
	 * <td>The query as typed by the user.</td>
	 * </tr>
	 * <tr>
	 * <td>meta.start</td>
	 * <td>The 0-based index of the first item to return when paging through results.</td>
	 * </tr>
	 * <tr>
	 * <td>meta.rows</td>
	 * <td>The maximum number of items to return.</td>
	 * </tr>
	 * <tr>
	 * <td>meta.sortBy</td>
	 * <td>The name of the "sort by" field, default is relevance score.</td>
	 * </tr>
	 * <tr>
	 * <td>meta.sortDir</td>
	 * <td>Direction of sorting - asc or desc.</td>
	 * </tr>
	 * <tr>
	 * <td>ontology</td>
	 * <td>An array of ontology IRIs that returned documents must be tagged with.</td>
	 * </tr>
	 * <tr>
	 * <td>geo</td>
	 * <td>An array of geographical entities (municipalities) that documents must be tagged with</td>
	 * </tr>
	 * <tr>
	 * <td>propertyInfo</td>
	 * <td>The property info structure returned by the mapping GIS service</td>
	 * </tr>
	 * <tr>
	 * <td></td>
	 * <td></td>
	 * </tr>  
	 * </table>
	 * </p>
	 * 
	 * @param params As described above.
	 * @return The response signal success with the "ok" property (true or false). If true, the 'docs' property
	 * contains the list of search results. If false, the error property contains the error.
	 */
	@POST
	@Path("/kb")
	@Produces("application/json")
	@Consumes("application/json")
	public Json searchKBTopics(Json params)
	{
		try
		{
			//System.out.println(params);
			Json ainfo = params.at("meta", object()).at("address");
			Mapping<Json, Boolean> gisFilter = ainfo != null && 
							!ainfo.at("coordinates").asJsonMap().isEmpty() ? 
					Refs.gisClient.resolve().makeGisFilter(ainfo, true, null) :
						null;
			return new SolrSearchEngine().find(params.atDel("query").asString(), 
													  params,
													  gisFilter);
		}
		catch (Throwable t)
		{
			t.printStackTrace(System.err);
			return ko(t);
		}
	}
	
	public static void main(String []argv)
	{
		SearchService S = new SearchService();
		Json result = S.searchKBTopics(object("query", "tax")
						.set("start", 0)
						.set("rows", 10));
		System.out.println(object("query", "tax")
				.set("start", 0)
				.set("rows", 10));
		System.out.println(result);
	}	
}

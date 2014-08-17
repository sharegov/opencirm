package org.sharegov.cirm.search;

import mjson.Json;

import org.sharegov.cirm.utils.Mapping;

/**
 * <p>
 * A search engine offers a simple interface to do a text search for relevant
 * documents to help resolve a case. Normally, an implementation would query
 * the in-house search for a whatever document repository or content management
 * is used for citizen help topics and public or private web pages. 
 * </p>
 * 
 * @author Borislav Iordanov
 *
 */
public interface SearchEngine
{
	/**
	 * <p>
	 * Query a back-end, possibly remote, search service and return 
	 * the results in a normalized format.
	 * </p>
	 * 
	 * @param question The user search input.
	 * @param params Extra parameters to control what results to return:
	 * <code>rows</code> for the number of results, <code>start</code> 
	 * for the index of the first result, <code>sortBy</code> and <code>sortDir</code>
	 * for sorting. 
	 * @param filter An extra, arbitrary filtering function to ignore some of the search results.
	 * @return A <code>Json.array</code> of objects each representing a search result.
	 */
	Json find(String question, Json params, Mapping<Json, Boolean> filter);
}
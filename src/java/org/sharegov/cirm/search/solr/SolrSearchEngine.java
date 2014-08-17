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


import static mjson.Json.array;
import static mjson.Json.object;
import static org.sharegov.cirm.search.solr.SearchQuery.not;
import static org.sharegov.cirm.search.solr.SearchQuery.and;
import static org.sharegov.cirm.search.solr.SearchQuery.boost;
import static org.sharegov.cirm.search.solr.SearchQuery.field;
import static org.sharegov.cirm.search.solr.SearchQuery.oneOf;
import static org.sharegov.cirm.search.solr.SearchQuery.or;
import static org.sharegov.cirm.search.solr.SearchQuery.quote;
import static org.sharegov.cirm.search.solr.SearchQuery.requiredField;
import static org.sharegov.cirm.utils.GenUtils.ko;
import static org.sharegov.cirm.utils.GenUtils.ok;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.owl.Model;
import org.sharegov.cirm.search.SearchEngine;
import org.sharegov.cirm.utils.Mapping;

public class SolrSearchEngine implements SearchEngine
{
	static final String noJurisdiction = Model.upper("NO_JURISDICTION").toString();
	static final String fedGov = Model.upper("Federal_Government").toString();
	static final String stateGov = Model.upper("State_of_Florida").toString();
	static final String countyGov = Model.upper("Miami-Dade_County").toString();
	
    public static final String ESTIMATED_RESULT_COUNT = "estimatedResultCount";
    public static final String START_PAGE = "start";
    public static final String ROWS = "rows";
    public static final String SORT_BY = "sortBy";
    public static final String SORT_DIR = "sortDir";

    OWLClass countyCl = OWL.owlClass("County_Organization"),
   		 	 cityCl = OWL.owlClass("City_Organization");

	private void setAgencyLabel(Json doc, OWLReasoner reasoner)
	{
		String agencyLabel = null;
		for (Json o : doc.at("ontology").asJsonList())
		{
			// Syed Ticket #1002 - URIs are negated with a tilde in KB
			// for now filter them because they cause and invalid URI syntax
			// exception. This is short term fix.
			if(o.asString().startsWith("~"))
				continue;
			if (o.asString().endsWith("City_of_Miami") || o.asString().contains("COM_"))
			{
				agencyLabel = "COM";
				break;
			}
    		OWLNamedIndividual ind = OWL.individual(o.asString());
    		if (reasoner.getTypes(ind, false).containsEntity(countyCl))
    		{
    			agencyLabel = "MDC";
    			break;
    		}													  
		}
		if (agencyLabel != null)
			doc.set("agencyLabel", agencyLabel);		
	}
	
    public Json find(String question, Json params, Mapping<Json, Boolean> filter)
    {
        SolrClient cl = new SolrClient();
        SearchQuery query = new SearchQuery();
        QueryExpression expression = null;
    	if (question == null || question.trim().length() == 0)
    	{
    		expression = requiredField("url", "h*");
    	}
    	else if (question.startsWith("http://") || question.startsWith("https://"))
    		expression = requiredField("url", '"' + question + '"');
    	else
    		expression =
    				or(boost(field("title", '"' + question + '"'), 5000.0f),
    				   boost(field("title", question), 20.0f),
    				   boost(field("keywords", '"' + question + '"'), 300.0f),
    				   boost(field("keywords", question), 100.0f),
    				   boost(field("text", '"' + question + '"'), 50.0f),
	 		    	   field("text", question));
    	Json meta = params.at("meta", object());
    	int rows = meta.at("rows", 25).asInteger();
    	int start = meta.at("start", 0).asInteger();
    	String sortBy = meta.at("sortBy", "score").asString();
    	String sortDir = meta.at("sortDir", "desc").asString();
    	query.setRows(rows);
    	query.setStart(start);
    	query.setSortBy(sortBy);
    	query.setSortDir(sortDir);
        for (Map.Entry<String, Json> e : params.asJsonMap().entrySet())
        {
        	String name = e.getKey();
        	if ("meta".equals(name))
        		continue;
        	else if ("agency".equals(name))
        	{        		
        		Set<String> ocrit = new HashSet<String>();
        		ocrit.add(noJurisdiction);        		
        		ocrit.add(e.getValue().asString());        		
        		if (e.getValue().asString().equals(countyGov))
        		{
        			ocrit.add(stateGov);
        			ocrit.add(fedGov);
        		}
        		else if (e.getValue().asString().equals(stateGov))
        		{
        			ocrit.add(fedGov);
        		}
        		else if (!e.getValue().asString().equals(fedGov))
        		{
        			ocrit.add(countyGov);
        			ocrit.add(stateGov);
        			ocrit.add(fedGov);
        		}
    			expression = and(expression, oneOf("ontology", ocrit));  
    			expression = and(expression, not(field("notRelevantFor", e.getValue().toString())));
        	}
        	else
        	{
        		if (e.getValue().isArray())
        			expression = and(expression, oneOf(e.getKey(),
        											   e.getValue().asList()));
        		else
        		{
        			Object v = e.getValue().getValue();
        			if (v instanceof String)
        				v = quote(v);
        			expression = and(expression, field(e.getKey(), v));
        		}
        	}
        }
        query.setExpression(expression);

        Json result = ok().set("docs", array());
        Json docs = result.at("docs");
                
        if (filter == null)
        {
        	Json A = cl.search(query);
        	if (A.at("responseHeader").is("status", 0l))
        		result.set("docs", A.at("response").at("docs"))
        			  .set("total", A.at("response").at("numFound"));
        	else
        		return ko("Search engine failed.").set("responseHeader", A.at("responseHeader"));        	        	
        }        
        else while (docs.asJsonList().size() < rows)
        {
        	query.setStart(start);
        	Json A = cl.search(query);
        	if (!result.has("total"))
        		result.set("total", A.at("response").at("numFound"));
        	if (A.at("responseHeader").is("status", 0l))
        		A = A.at("response").at("docs");
        	else
        		return ko("Search engine failed.").set("responseHeader", A.at("responseHeader")); 
        	if (A.asJsonList().isEmpty())
        		break;
        	for (Json x : A.asJsonList())
        	{
        		//System.out.println(x);
        		if (filter.eval(x))
        			docs.add(x);
        		if (docs.asJsonList().size() >= rows)
        			break;
        	}
        	start += rows;
        }
        
        OWLReasoner reasoner = OWL.reasoner();        
        for (Json x : result.at("docs").asJsonList())
        	setAgencyLabel(x, reasoner);
        return result;        
    }
}
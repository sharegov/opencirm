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

import java.net.URLEncoder;
import java.util.Collection;

public class SearchQuery
{
	public static final String SPACE = "%20";
	public static final String COLON = "%3A";
	public static final String PLUS = "%2B";
	public static final String QUOTE = "%22";
	public static final String LEFT_BRACKET = "%5B";
	public static final String RIGHT_BRACKET = "%5D";
	public static final String CARET = "%5E";
	
	// Searches in all fields
//	public String question = null;

	// if set search in specified field for given values
//	public Map<String, Object> fieldsValuesMap = new HashMap<String, Object>();

	// rows returned
	private int rows = 25;
	// start page
	private int start = 0;

	// sorting
	private String sortBy = "score";
	private String sortDir = "desc";

	private boolean includeScore = true;
	
	// facet
//	public String facetField = null;
//	public int facetMinCount = 3;

	// OR or AND operator used in query
//	public boolean OR_AND = true;
	
	private QueryExpression expression;	
	
	public SearchQuery()
	{		
	}
	
	public SearchQuery(QueryExpression expression)
	{
		this.expression = expression;
	}
	
	@SuppressWarnings({ })
	public String toString()
	{
		//System.out.println("SOLR Query:" + expression.toString());		
		StringBuilder sb = new StringBuilder(256);
		// try {
		sb.append("?q="); 
		sb.append(expression.toString());
		if (start > 0)
			sb.append("&start=" + start);
		sb.append("&rows=" + rows);
		if (includeScore)
			sb.append("&fl=score");
		if (sortBy != null)
		{
			sb.append("&sort=" + sortBy + SPACE + sortDir);
		}		
		String val = sb.toString();
		return val;
	}
	
	public int getRows()
	{
		return rows;
	}

	public void setRows(int rows)
	{
		this.rows = rows;
	}

	public int getStart()
	{
		return start;
	}

	public void setStart(int start)
	{
		this.start = start;
	}

	public String getSortBy()
	{
		return sortBy;
	}

	public void setSortBy(String sortBy)
	{
		this.sortBy = sortBy;
	}

	public String getSortDir()
	{
		return sortDir;
	}

	public void setSortDir(String sortDir)
	{
		this.sortDir = sortDir;
	}

	public boolean isIncludeScore()
	{
		return includeScore;
	}

	public void setIncludeScore(boolean includeScore)
	{
		this.includeScore = includeScore;
	}

	public QueryExpression getExpression()
	{
		return expression;
	}

	public void setExpression(QueryExpression expression)
	{
		this.expression = expression;
	}

	@SuppressWarnings("deprecation")
	public static String encode(String s)
	{
		if (s.startsWith(QUOTE) && s.endsWith(QUOTE))
			return quote(URLEncoder.encode(s.substring(QUOTE.length(), s.length()-QUOTE.length())));
		else
			return URLEncoder.encode(s);
	}
	
	public static String quote(Object value)
	{
		return QUOTE + value.toString() + QUOTE;
	}
	
	public static QueryOr oneOf(String name, Object [] values)
	{
		QueryOr or = QueryOr.make(new QueryExpression[0]);
		if (values != null)
			for (Object x : values)
			{
				if (x instanceof String)
					x = quote(x);
				or.add(field(name, x));
			}
		return or;
	}
	
	public static QueryOr oneOf(String name, Collection<?> values)
	{
		QueryOr or = QueryOr.make(new QueryExpression[0]);
		if (values != null)
			for (Object x : values)
			{
				if (x instanceof String)
					x = quote(x);
				or.add(field(name, x));
			}
		return or;
	}
	
	public static QueryFieldValue field(String name, Object value)
	{
		return new QueryFieldValue(name, value, null);
	}

	public static QueryFieldValue requiredField(String name, Object value)
	{
		return new QueryFieldValue(name, value, PLUS);
	}
	
	public static QueryFieldValue absentField(String name, Object value)
	{
		return new QueryFieldValue(name, value, "-");
	}
	
	public static QueryFieldRange range(String name, Object start, Object end)
	{
		return new QueryFieldRange(name, start, end, null);
	}
	
	public static QueryFieldRange requiredRange(String name, Object start, Object end)
	{
		return new QueryFieldRange(name, start, end, PLUS);
	}
	
	public static QueryFieldRange absentRange(String name, Object start, Object end)
	{
		return new QueryFieldRange(name, start, end, "-");
	}	
	
	public static QueryOr or(QueryExpression...expressions)
	{
		return QueryOr.make(expressions);
	}
	
	public static QueryAnd and(QueryExpression...expressions)
	{
		return QueryAnd.make(expressions);
	}	
	
	public static QueryNot not(QueryExpression negated)
	{
		return QueryNot.make(negated);
	}
	
	public static QueryBoost boost(QueryExpression expression, float boost)
	{
		return new QueryBoost(expression, boost);
	}
}

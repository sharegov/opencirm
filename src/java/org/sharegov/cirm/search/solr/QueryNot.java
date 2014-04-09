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

public class QueryNot implements QueryExpression
{
	private QueryExpression negated;
	
	public static QueryNot make(QueryExpression negated)
	{
		return new QueryNot(negated);
	}
	
	public QueryNot()
	{		
	}
	
	public QueryNot(QueryExpression negated)
	{
		this.negated = negated;
	}

	public QueryExpression getNegated()
	{
		return negated;
	}

	public void setNegated(QueryExpression negated)
	{
		this.negated = negated;
	}
	
	public String toString()
	{
		if (negated == null)
			return "";
		StringBuilder sb = new StringBuilder();
		sb.append(SearchQuery.SPACE);
		sb.append("NOT");
		sb.append(SearchQuery.SPACE);
		sb.append(negated);
		return sb.toString();
	}
}

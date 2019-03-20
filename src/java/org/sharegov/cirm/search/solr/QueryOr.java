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


import java.util.ArrayList;

public class QueryOr extends ArrayList<QueryExpression> implements QueryExpression
{
	private static final long serialVersionUID = -1960387205448538799L;

	public static QueryOr make(QueryExpression...expressions)
	{
		QueryOr qo = new QueryOr();
		for (QueryExpression exp : expressions)
			if (exp != null)
				qo.add(exp);
		return qo;
	}
	
	public String toString()
	{
		if (isEmpty())
			return "";		
		else if (size() == 1)
			return get(0).toString();
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		for (int i = 0; i < size(); i++)
		{
			sb.append(get(i).toString());
			if (i < size() - 1)
			{
				sb.append(SearchQuery.SPACE);
				sb.append("OR");
				sb.append(SearchQuery.SPACE);
			}
		}
		sb.append(")");
		return sb.toString();
	}
}

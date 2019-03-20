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

public class QueryFieldRange extends QueryField
{
	private Object start, end;

	public QueryFieldRange(String name, Object start, Object end, String operator)
	{
		super(name, operator);
		this.start = start;
		this.end = end;
	}
	
	public Object getStart()
	{
		return start;
	}

	public void setStart(Object start)
	{
		this.start = start;
	}

	public Object getEnd()
	{
		return end;
	}

	public void setEnd(Object end)
	{
		this.end = end;
	}	
	
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		if (getOperator() != null)
			sb.append(getOperator());
		if (getFieldName() != null && getFieldName().length() > 0)
		{
			sb.append(getFieldName());
			sb.append(SearchQuery.COLON);
		}
		sb.append("[");
		sb.append(SearchQuery.encode(start.toString()));
		sb.append(SearchQuery.SPACE);
		sb.append("TO");
		sb.append(SearchQuery.SPACE);
		sb.append(SearchQuery.encode(end.toString()));
		sb.append("]");
		return sb.toString();		
	}	
}

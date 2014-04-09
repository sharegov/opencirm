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

public class QueryFieldValue extends QueryField
{
	private Object value;

	public QueryFieldValue() { }
	public QueryFieldValue(String name, Object value, String operator)
	{
		super(name, operator);
		this.value = value;
	}
	
	public Object getValue()
	{
		return value;
	}

	public void setValue(Object value)
	{
		this.value = value;
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
		sb.append(SearchQuery.encode(value.toString()));
		return sb.toString();
	}
}

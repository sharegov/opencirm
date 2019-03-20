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

public abstract class QueryField implements QueryExpression
{
	private String fieldName;
	private String operator;
	
	public QueryField()
	{
		
	}
	
	public QueryField(String fieldName)
	{
		this.fieldName = fieldName;
	}
	
	public QueryField(String fieldName, String operator)
	{
		this.fieldName = fieldName;
		this.operator = operator;
	}
	
	public String getFieldName()
	{
		return fieldName;
	}

	public void setFieldName(String fieldName)
	{
		this.fieldName = fieldName;
	}

	public String getOperator()
	{
		return operator;
	}

	public void setOperator(String operator)
	{
		this.operator = operator;
	}
	
	public abstract String toString();
}

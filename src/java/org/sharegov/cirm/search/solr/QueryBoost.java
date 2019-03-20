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

public class QueryBoost implements QueryExpression
{
	private float boost;
	private QueryExpression expression;
	
	public QueryBoost()
	{		
	}
	
	public QueryBoost(QueryExpression expression, float boost)
	{
		this.expression = expression;
		this.boost = boost;
	}

	public float getBoost()
	{
		return boost;
	}

	public void setBoost(float boost)
	{
		this.boost = boost;
	}

	public QueryExpression getExpression()
	{
		return expression;
	}

	public void setExpression(QueryExpression expression)
	{
		this.expression = expression;
	}
	
	public String toString()
	{
		return expression.toString() + SearchQuery.encode("^" + boost);
	}
}

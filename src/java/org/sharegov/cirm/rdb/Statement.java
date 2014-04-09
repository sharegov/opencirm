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
package org.sharegov.cirm.rdb;

import java.util.ArrayList;
import java.util.List;

import org.semanticweb.owlapi.model.OWLNamedIndividual;

public class Statement
{
	private Sql sql;
	private List<Object> parameters = new ArrayList<Object>();
	private List<OWLNamedIndividual> types = new ArrayList<OWLNamedIndividual>();
	
	public Sql getSql()
	{
		return sql;
	}
	public void setSql(Sql sql)
	{
		this.sql = sql;
	}
	public List<Object> getParameters()
	{
		return parameters;
	}
	public void setParameters(List<Object> parameters)
	{
		this.parameters = parameters;
	}
	public List<OWLNamedIndividual> getTypes()
	{
		return types;
	}
	public void setTypes(List<OWLNamedIndividual> types)
	{
		this.types = types;
	} 
}

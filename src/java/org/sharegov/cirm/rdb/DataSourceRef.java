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

import mjson.Json;

import org.semanticweb.owlapi.model.IRI;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.utils.Ref;
import javax.sql.DataSource;

/**
 * <p>
 * Create a {@link javax.sql.DataSource} based on an OWL description. The reference
 * is smart enough to recognize when the description value has changed and it recomputes the value. 
 * </p>
 * 
 * @author boris
 *
 */
public class DataSourceRef implements Ref<DataSource>
{
	final Ref<Json> description;
	volatile Json desc = null;
	volatile DataSource value = null;
	volatile DatabaseHook hook = null;
		
	public DataSourceRef(Ref<Json> description)
	{
		this.description = description;
	}
	
	public DataSourceRef(IRI descriptionIri)
	{
		this.description = Refs.owlJsonCache.resolve().individual(descriptionIri);
	}

	public DatabaseHook getHook()
	{
	    if (hook == null)
	        synchronized (this)
	        {
	            if (hook == null) try
	            {
	                hook = (DatabaseHook)Class.forName(
	                        description.resolve().at("hasDatabaseType").at("hasDataSourceFactory").asString()).newInstance();
	            }
	            catch (Exception ex)
	            {
	                throw new RuntimeException(ex);
	            }	            
	        }
	    return hook;
	}
	
	public DataSource resolve()
	{
		if (value == null || description.resolve() != desc)
			synchronized (this) 
			{
				if (value == null || description.resolve() != desc)
				{
					desc = description.resolve();
		            if (desc.is("usesPool", true) || desc.is("usesPool", "true"))
		                value = getHook().createPooledDataSource(desc);
		            else
		                value = getHook().createDataSource(desc);
			} 
			}
		return value;
	}
}
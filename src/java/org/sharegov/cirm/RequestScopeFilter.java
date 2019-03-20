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
package org.sharegov.cirm;

import java.util.IdentityHashMap;
import java.util.Map;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.routing.Filter;

public class RequestScopeFilter extends Filter
{
	static final ThreadLocal<Map<Object, Object>> requestScope = new ThreadLocal<Map<Object, Object>>()
	{
		protected Map<Object, Object> initialValue()
		{
			return new IdentityHashMap<Object, Object>();
		}
	};

	protected int beforeHandle(Request request, Response response)
	{
		return CONTINUE;
	}

	protected void afterHandle(Request request, Response response)
	{
		requestScope.get().clear();
	}
	
	public static Object get(Object key)
	{
		return requestScope.get().get(key);
	}
	
	public static void set(Object key, Object value)
	{
		requestScope.get().put(key, value);
	}
	
	public static void clear() 
	{
		requestScope.get().clear();
	}
}

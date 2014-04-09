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

import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Form;
import org.restlet.data.Method;
import org.restlet.routing.Filter;
import org.restlet.util.Series;

/**
 * Overrides the HTTP request method if the header {@code
 * X-HTTP-Method-Override} is detected.
 * 
 * @author	Alfonso Boza	<aboza@miamidade.gov>
 * @version	1.0
 */
public class HttpMethodOverrideFilter extends Filter
{
	/** The attribute name for retrieving HTTP headers */
	final private static String HTTP_HEADERS_ATTRIBUTE = "org.restlet.http.headers";
	
	/** The non-standard HTTP header for overriding method */
	final private static String X_HTTP_METHOD_OVERRIDE = "X-HTTP-Method-Override";
	
	/**
	 * Constructs a new Restlet filter that overrides the HTTP request method.
	 */
	public HttpMethodOverrideFilter()
	{
		this(null);
	}
	
	/**
	 * Constructs a new Restlet filter with a specified context.
	 * 
	 * @param context
	 */
	public HttpMethodOverrideFilter(Context context)
	{
		super(context);
	}
	
	/**
	 * @see Filter#beforeHandle(org.restlet.Request, org.restlet.Response)
	 */
	@Override
	protected int beforeHandle(Request request, Response response)
	{
		this.overrideHTTPMethod(request);
		
		return CONTINUE;
	}
	
	/**
	 * Overrides the HTTP request method if necessary.
	 * 
	 * @param	request	Request to Restlet.
	 */
	private void overrideHTTPMethod(Request request)
	{
		final Series<?> attrs = (Series<?>) request.getAttributes().get(HTTP_HEADERS_ATTRIBUTE);
		final String method = attrs.getFirstValue(X_HTTP_METHOD_OVERRIDE, true);
		
		if (method != null && !method.trim().isEmpty()
			&& request.getMethod().equals(Method.GET))
		{
			for (Method m : new Method[] { Method.DELETE, Method.POST, Method.PUT })
			{
				if (method.equalsIgnoreCase(m.getName()))
				{
					request.setMethod(m);
					break;
				}
			}
		}
	}
}

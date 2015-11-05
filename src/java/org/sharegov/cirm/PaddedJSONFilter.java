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

import java.io.IOException;

import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.MediaType;
import org.restlet.representation.Representation;
import org.restlet.routing.Filter;

/**
 * Filters outgoing responses, padding the JSON if necessary.
 * 
 * @author    Alfonso Boza    <aboza@miamidade.gov>
 * @version    1.0
 */
public class PaddedJSONFilter extends Filter
{
    /**
     * Constructs a new filter to pad JSON if necessary.
     */
	public PaddedJSONFilter()
	{
		this(null);
	}
	
    /**
     * Constructs a new JSONP filter with Restlet context.
     *
     * @param   context     Restlet context.
     */
	public PaddedJSONFilter(Context context)
	{
		super(context);
	}
	
    /**
     * Manages the outgoing response respective to its request.
     * 
     * @param	request		Request to Restlet.
     * @param	response	Response from Restlet.
     */
    @Override
    protected void afterHandle(Request request, Response response)
    {
    	this.padJSON(request, response);
    }
	/**
     * Pads the response in JSON if callback found in request query parameters.
     * 
     * @param	request		Request to Restlet.
     * @param	response	Response from Restlet.
     */
    private void padJSON(Request request, Response response)
    {
        Representation entity = response.getEntity();
        String callback = request.getResourceRef().getQueryAsForm().getFirstValue("callback");
        
        try
        {
            if (entity != null && entity.getMediaType().includes(MediaType.APPLICATION_JSON)
                && callback != null && !callback.isEmpty())
            {
                response.setEntity(String.format("%s(%s);", callback, entity.getText()),
                                   MediaType.APPLICATION_JAVASCRIPT);
            }
        }
        catch (IOException e)
        {
            // Ignore attempt to set entity.
        }
    }
}

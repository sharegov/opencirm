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
package org.sharegov.cirm.workflows;

import org.hypergraphdb.HGHandle;

/**
 * 
 * <p>
 * Represents an ad hoc, extra step that needs to be completed externally
 * before the normal execution of the workflow can continue. Input from
 * a user is an example of such a step. This is essentially a mechanism
 * be which the workflow releases control to its execution environment
 * for something else to be done. When that something else has finished,
 * any result should be stored in the <em>response</em> property of this
 * object and then its <em>perform</code> method can be called.
 * </p>
 *
 * @author Borislav Iordanov
 *
 */
public abstract class WorkflowRequestStep implements WorkflowStep
{
	private Object request, response;
	private HGHandle handle;
		
	public HGHandle getAtomHandle()
	{
		return handle;
	}

	public void setAtomHandle(HGHandle handle)
	{
		this.handle = handle;
	}

	public Object getRequest()
	{
		return request;
	}

	public void setRequest(Object request)
	{
		this.request = request;
	}

	public Object getResponse()
	{
		return response;
	}

	public void setResponse(Object response)
	{
		this.response = response;
	}	
}

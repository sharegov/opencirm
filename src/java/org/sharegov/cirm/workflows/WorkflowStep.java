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

import org.hypergraphdb.HGHandleHolder;

/**
 * 
 * <p>
 * Represents a single step in the execution of a process. The step by an activity, or
 * a decision point. In all cases, the execution must lead to the next step to execute.
 * <code>WorkflowStep</code>s make up a given {@link Workflow} and just like it are not
 * specific to an execution context. 
 * </p>
 *
 * @author Borislav Iordanov
 *
 */
public interface WorkflowStep extends HGHandleHolder
{
	/**
	 * <p>
	 * Execute this <code>WorkflowStep</code>. 
	 * </p>
	 * 
	 * @param context The execution context. Any information needed perform
	 * this step should be available from the object.
	 * @return The step to execute or <code>null</code> if this is a final
	 * step (and end state).
	 */
	WorkflowStep perform(WorkflowExecutionContext context);
	
	int hashCode();
	boolean equals(Object x);	
}

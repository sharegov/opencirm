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


/**
 * A RetryDetectedException shall be thrown by sub-level transactions whenever 
 * all current transaction execution shall cease and the top-level transaction 
 * should be retried as a whole immediately.
 * Throwing and catching this exception happens automatically by the transaction execution runtime.
 * 
 * @author Thomas Hilpold
 */
public class RetryDetectedException extends CirmTransactionException
{

	private static final long serialVersionUID = -1132792692373707608L;

	public RetryDetectedException()
	{
	}

	public RetryDetectedException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public RetryDetectedException(String message)
	{
		super(message);
	}

	public RetryDetectedException(Throwable cause)
	{
		super(cause);
	}

	/**
	 * Determines if a RetryDetectedException is a cause for the Throwable.
	 * 
	 * @param t
	 * @return true if a RetryException is in the cause stack.
	 */
	public static boolean isRetryDetectedOrCausedBy(Throwable t) 
	{
    	do 
    	{
    		if (t instanceof RetryDetectedException)
				return true;
    		t = t.getCause();
    	} while (t != null);
		return false;
	}
}

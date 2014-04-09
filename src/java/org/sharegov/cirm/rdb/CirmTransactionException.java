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
 * CirmTransactionException
 * 
 * @author Thomas Hilpold
 */
public class CirmTransactionException extends Exception
{

	private static final long serialVersionUID = 4561141493955154888L;

	public CirmTransactionException()
	{
		super();
	}

	public CirmTransactionException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public CirmTransactionException(String message)
	{
		super(message);
	}

	public CirmTransactionException(Throwable cause)
	{
		super(cause);
	}
}

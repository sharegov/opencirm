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
package org.sharegov.cirm.utils;

public class SingletonRef<T> implements Ref<T>
{
	private volatile T object;
	private Ref<T> factory;
	
	public SingletonRef(T object)
	{
		this.object = object;
	}
	
	public SingletonRef(Ref<T> factory)
	{
	    this.factory = factory;
	}
	
	public T resolve()
	{
		if (object == null)
		{
			synchronized(this)
			{
				if (object == null)
				{
				    object = factory.resolve();
				}
			}
		}
		return object;
	}
}

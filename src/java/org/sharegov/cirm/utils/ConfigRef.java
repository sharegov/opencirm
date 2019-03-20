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

import org.sharegov.cirm.Refs;

public class ConfigRef<T> implements Ref<T>
{
	private String name;
	
	public ConfigRef(String name)
	{
		this.name = name;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public T resolve()
	{
		return (T)Refs.configSet.resolve().get(name);
	}	
}

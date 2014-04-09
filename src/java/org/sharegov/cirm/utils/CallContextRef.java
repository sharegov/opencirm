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

import java.util.LinkedList;

public abstract class CallContextRef<T> implements Ref<T>
{
	ThreadLocal<LinkedList<T>> stack = new ThreadLocal<LinkedList<T>>() {
      @Override
      protected LinkedList<T> initialValue()
      {
          return new LinkedList<T>();
      }		
	};
	
	public abstract T compute();
	
	public void push() { stack.get().push(compute()); }
	public void pop() { stack.get().pop(); }
	
	@Override
	public T resolve()
	{
		return stack.get().peek();
	}
}

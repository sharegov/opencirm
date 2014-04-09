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
package org.sharegov.cirm.owl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLClass;
import org.sharegov.cirm.OWL;

/**
 * Thread safe and non blocking implementation of a cache of Protected Class and
 * it's decendents.
 * 
 * @author Thomas Hilpold
 * 
 */
public class OWLProtectedClassCache
{
	public static final String PROTECTED_CLASS = "Protected";

	private static volatile OWLProtectedClassCache instance;

	private volatile Set<OWLClass> cache;

	private volatile boolean rebuildInProgress = false;

	public static OWLProtectedClassCache getInstance()
	{
		if (instance == null)
		{
			synchronized (OWLProtectedClassCache.class)
			{
				if (instance == null)
				{
					instance = new OWLProtectedClassCache();
					instance.rebuild();
				}
			}
		}
		return instance;
	}

	protected OWLProtectedClassCache()
	{
	};

	/**
	 * Determines if the given class is Protected class, a subclass or a
	 * descendent class.
	 * 
	 * @param candidate
	 * @return
	 */
	public boolean isProtectedClass(OWLClass candidateClass)
	{
		if (rebuildInProgress)
			synchronized (this)
			{
				// Assert (!rebuildInProgress)
				return isProtectedClassUnsafe(candidateClass);
			}
		else
			return isProtectedClassUnsafe(candidateClass);
	}

	private boolean isProtectedClassUnsafe(OWLClass candidate)
	{
		return cache.contains(candidate);
	}

	public synchronized void rebuild()
	{
		rebuildInProgress = true;
		Set<OWLClass> subclasses = OWL.reasoner()
				.getSubClasses(OWL.owlClass(PROTECTED_CLASS), false)
				.getFlattened();
		cache = new HashSet<OWLClass>(subclasses.size() + 3);
		cache.addAll(subclasses);
		cache.add(OWL.owlClass("Protected"));
		cache = Collections.unmodifiableSet(cache);
		System.out.println("OWLProtectedClassCache built contains "
				+ cache.size() + " protected classes");
		rebuildInProgress = false;
	}
}

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

import java.util.HashMap;
import java.util.Map;

import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.PrefixManager;

/**
 * Creates short names such as mdc$FRAGMENT or legacy$FRAGMENT
 * 
 * @author Thomas Hilpold
 */
public class PrefixedFragmentShortFormProvider extends
		FragmentShortFormProvider
{

	public static final char FRAGMENT_SEPARATOR = '$';

	private static Map<String, String> prefix2prefixNameMap;

	public PrefixedFragmentShortFormProvider(PrefixManager prefixManager)
	{
		prefix2prefixNameMap = new HashMap<String, String>();
		for (Map.Entry<String, String> prefixName2PrefixEntry : prefixManager
				.getPrefixName2PrefixMap().entrySet())
		{
			String prefixName = prefixName2PrefixEntry.getKey();
			if (prefixName.length() > 1)
			{
				// Inverse map: full iri prefix with trailing # to prefixName
				// without ':'
				prefix2prefixNameMap.put(prefixName2PrefixEntry.getValue(),
						prefixName.substring(0, prefixName.length() - 1));
			}
		}
	}

	/**
	 * Returns prefixNameNoColon + FRAGMENT_SEPARATOR + fragmentStr
	 */
	@Override
	public String getShortForm(OWLEntity entity)
	{
		String iriPrefix = entity.getIRI().getStart(); // includes as last char
		String fragmentStr = entity.getIRI().getFragment();
		String prefixNameNoColon = prefix2prefixNameMap.get(iriPrefix);
		if (prefixNameNoColon == null)
		{
			throw new IllegalStateException("No Prefix found for "
					+ entity.getIRI());
		}
		return prefixNameNoColon + FRAGMENT_SEPARATOR + fragmentStr;
	}

	@Override
	public void dispose()
	{
		super.dispose();
	}

}

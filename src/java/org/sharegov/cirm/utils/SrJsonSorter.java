/*******************************************************************************
 * Copyright 2018 Miami-Dade County
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

import static org.sharegov.cirm.OWL.dataProperty;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.OWL;

import mjson.Json;

/**
 * Sorts various Service Request collections found in an sr json in loaded (without legacy: prefixes).
 * Currently only answer sorting is implemented.
 * 
 * Extensible for activities, customers, et.c.
 * 
 * @author Thomas Hilpold
 *
 */
public class SrJsonSorter {

	/**
	 * Sorts various sr collection properties. Currently only answer sorting is implemented.
	 * @param sr
	 */
	public void sortSr(Json sr) {
		if (sr == null || !sr.isObject()) return;
		synchronized(sr) {
			sortSrAnswers(sr);
		}
	}
	
	/**
	 * Sorts SR answers by accessing the onto configuration and attempting to determine hasOrderBy.
	 * If not configured, Service field labels will be compared.  
	 * @param sr
	 */
	private void sortSrAnswers(Json sr) {
		if (!sr.has("hasServiceAnswer") || !sr.at("hasServiceAnswer").isArray()) return;
		Map<String, Float> iriToOrderMap = new HashMap<>();
		List<Json> hasServiceAnswer = sr.at("hasServiceAnswer").asJsonList();
		for (Json a : hasServiceAnswer) {
			try {
    			String iri = a.at("hasServiceField").at("iri").asString();
    			OWLNamedIndividual serviceField = OWL.individual(iri);
    			OWLLiteral orderByLit = dataProperty(serviceField, "legacy:hasOrderBy");
    			if (orderByLit != null) {
    				iriToOrderMap.put(iri, orderByLit.parseFloat());
    			}
			} catch(Exception e) {
				System.err.println("sortSrAnswers error at " + a + " " + e);
			}
		}
		sortSrAnswersByServiceField(hasServiceAnswer, iriToOrderMap);
	}
	
	/**
	 * Sorts service fields and modifies Json by the given service field iri to hasOrderBy value map.
	 * @param hasServiceAnswer the list of answers to modify/sort.
	 * @param iriToOrderMap
	 */
	private static void sortSrAnswersByServiceField(List<Json> hasServiceAnswer, final Map<String, Float> iriToOrderMap) 
	{
		Collections.sort(hasServiceAnswer, new Comparator<Json>()
		{
			public int compare(Json left, Json right)
			{
				Float leftOrder = 0f;
				Float rightOrder = 0f;
				try {
					String leftFieldIri = left.at("hasServiceField").at("iri").asString();
					String rightFieldIri = right.at("hasServiceField").at("iri").asString();
					if (iriToOrderMap.containsKey(leftFieldIri)) {
						leftOrder = iriToOrderMap.get(leftFieldIri);
					}
					if (iriToOrderMap.containsKey(rightFieldIri)) {
						rightOrder = iriToOrderMap.get(rightFieldIri);
					}
				} catch(Exception e) {
					System.err.println("SrJsonSorter.sortServiceFields: Could not compare orderBy" + left + " to " + right);
				}
				Float compareByOrder = leftOrder - rightOrder;
				if (compareByOrder > 0) {
					return 1;
				} else if (compareByOrder < 0) {
					return -1;
				} else {
					int compareByLabel = 0;
					try {
						String leftLabel = left.at("hasServiceField").at("label").asString();
						String rightLabel = right.at("hasServiceField").at("label").asString();						
						compareByLabel = leftLabel.compareTo(rightLabel);
					} catch(Exception e) {
						System.err.println("SrJsonSorter.sortServiceFields: Could not compare labels " + left + " to " + right);
					}
					return compareByLabel;
				}
			}
		});
	}
}

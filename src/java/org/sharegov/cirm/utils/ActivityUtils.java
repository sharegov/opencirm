/*******************************************************************************
 * Copyright 2016 Miami-Dade County
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
import static org.sharegov.cirm.OWL.objectProperty;
import static org.sharegov.cirm.OWL.reasoner;

import java.util.Set;

import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;

/**
 * Utility functions for Activity Types.
 * Can be expanded to reflect the implicit schema of an Activity (type) in the code.
 * 
 * @author Thomas Hilpold
 */
public class ActivityUtils {
	
	/**
	 * Determines if the given activity type is disabled. <br>
	 * i.e. legacy:isDisabled "true"^^xsd:Boolean was configured for it.
	 * 
	 * @param activityType
	 * @return true if disabled
	 */
	public boolean isDisabled(OWLNamedIndividual activityType) {
		boolean result = false;
		Set<OWLLiteral> values = reasoner().getDataPropertyValues(
				activityType,
				dataProperty("legacy:isDisabled"));
		if (values.size() > 0) {
			OWLLiteral isDisabledValue = values.iterator().next();
			result = isDisabledValue.isBoolean() && isDisabledValue.parseBoolean();
		}
		return result;
	}
	
	/**
	 * Determines if for the given activity type autocreate is enabled. <br>
	 * i.e. legacy:isAutoCreate "Y"^^xsd:string was confiured for it.
	 * 
	 * @param activityType
	 * @return
	 */
	public boolean isAutoCreate(OWLNamedIndividual activityType) {
		boolean result = false;
		Set<OWLLiteral> values = reasoner().getDataPropertyValues(
				activityType,
				dataProperty("legacy:isAutoCreate"));
		if (values.size() > 0) {
			OWLLiteral isAutoCreateValue = values.iterator().next();
			result = isAutoCreateValue.getLiteral().equalsIgnoreCase("Y");
		}
		return result;		
	}
	
	/**
	 * Determines if for the given activity type auto assignment is enabled. <br>
	 * i.e. legacy:isAutoAssign "true"^^xsd:boolean was configured for it.
	 * 
	 * @param activityType
	 * @return
	 */
	public boolean isAutoAssign(OWLNamedIndividual activityType) {
		boolean result = false;
		Set<OWLLiteral> values = reasoner().getDataPropertyValues(
				activityType,
				dataProperty("legacy:isAutoAssign"));
		if (values.size() > 0) {
			OWLLiteral isAutoAssignValue = values.iterator().next();
			result = isAutoAssignValue.isBoolean() && isAutoAssignValue.parseBoolean();
		}
		return result;		
	}
	
	/**
	 * Get's the default outcome for the given activity type, if set.
	 * 
	 * @param activityType
	 * @return an individual or null, if no defaultoutcome exists.
	 */
	public OWLNamedIndividual getDefaultOutcome(OWLNamedIndividual activityType) {
		OWLNamedIndividual result = null;
		Set<OWLNamedIndividual> outcomes = reasoner().getObjectPropertyValues(
	    		activityType,
	    		objectProperty("legacy:hasDefaultOutcome"))
	    		.getFlattened();
	    if(outcomes.size() > 0) {
	    	result = outcomes.iterator().next();
	    }
	    return result;
	}
}
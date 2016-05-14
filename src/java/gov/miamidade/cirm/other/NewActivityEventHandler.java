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
package gov.miamidade.cirm.other;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.event.EventTrigger;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;
/**
 * Sends activities to departmentintegration interfaces, unless they originate at the interface or are of any feedback activity type.
 * 
 * @author Thomas Hilpold
 *
 */
public class NewActivityEventHandler implements EventTrigger
{
	public final String DEPT = "department";
	public final String ORIG = "originator";
	public final String[] FEEDBACK_ACTIVITY_TYPE_FRAGMENTS = { 
			"MDSHARED_FEEDBACK", 
			"MDSHARED_FEEDBACKWEB", 
			"MDSHARED_FEEDBACKFROMC",
			"COMSHARED_COMFEEDBACK"
			};
	
    @Override
    public void apply(OWLNamedIndividual entity, OWLNamedIndividual changeType, Json data)
    {    	
    	if (data.has(ORIG) && data.at(ORIG).isString() && DEPT.equalsIgnoreCase(data.at(ORIG).asString())) 
    	{
    		ThreadLocalStopwatch.now("NewActivityEventHandler activity originated from department, not sending act " + entity);
    	} 
    	else 
        {    	
    		if (!isFeedBackActivity(data.at("activity"))) {
    			DepartmentIntegration di =  new DepartmentIntegration();
    			Json result = di.tryActivitySend(data.at("serviceCase"), data.at("activity"), -1);
    			if (result == null || result.is("ok", false)) {
    				ThreadLocalStopwatch.error("NewActivityEventHandler failed to tryActivitySend with " + result);
    			}
    		} else {
    			ThreadLocalStopwatch.now("NewActivityEventHandler is not sending any feedback from or to consitutent activity.");
    		}
        }
    }
    
    /**
     * Determines if activity type's fragment is any of FEEDBACK_ACTIVITY_TYPE_FRAGMENTS.
     * 
     * This method does return false if type cannot be determined or activity parameter is null.
     * 
     * @param activity
     * @return true if any of FEEDBACK_ACTIVITY_TYPE_FRAGMENTS, false if not OR type could not be determined.
     */
    public boolean isFeedBackActivity(Json activity) {
    	if (activity == null || Json.nil().equals(activity)) {
    		System.err.println("NewActivityEventHandler.isFeedBackActivity activity was null or nil " + activity);
    	}
    	String actTypeIRI = null;    	
    	if (activity.has("legacy:hasActivity")) {
    		Json hasActivity = activity.at("legacy:hasActivity");
    		if (hasActivity.isObject() && hasActivity.has("iri") && hasActivity.at("iri").isString()) {
    			actTypeIRI = hasActivity.at("iri").asString();
    		} else if (hasActivity.isString()) {
    			actTypeIRI = hasActivity.asString();
    		} // else cannot find activity type iri
    	}    	
    	return (actTypeIRI == null)? false : isFeedbackActivityIri(actTypeIRI);
    }
    
    /**
     * Determines if an activity type iri ends with one of FEEDBACK_ACTIVITY_FRAGMENTS.
     * 
     * @param actTypeIRI any kind of iri string, null throws null pointer exc.
     * @return true if iri fragment indicates any feedback activity type.
     */
    private boolean isFeedbackActivityIri(String actTypeIRI) {
       	for (String curFragment : FEEDBACK_ACTIVITY_TYPE_FRAGMENTS) {
       		if (actTypeIRI.endsWith(curFragment)) {
       			ThreadLocalStopwatch.now("NewActivityEventHandler detected " + curFragment);
       			return true;
       		}
       	}
       	return false;
    }
    
}

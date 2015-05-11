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

import java.util.logging.Level;
import java.util.logging.Logger;

import gov.miamidade.cirm.MDRefs;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.event.EventTrigger;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

/**
 * Handles new service requests that did not originate from the CiRM User Interface for live reporting.
 * This includes new service request that originated at an external interface such as PW or CMS/RER.
 * 
 * @author Thomas Hilpold
 */
public class NewCaseNonCirmEventHandler implements EventTrigger
{
    private static Logger logger = Logger.getLogger("gov.miamidade.cirm.other");

    @Override
    public void apply(OWLNamedIndividual entity, OWLNamedIndividual changeType, Json data)
    {
    	//assert changeType == BO_NEW
        if (MDRefs.liveReportingStatus.resolve().isEnabled()) {
        	ThreadLocalStopwatch.getWatch().time("START NewCaseNonCirmEventHandler entity: " + entity + " changetype: " + changeType);
        	try {
        		LiveReportingSender sender = new LiveReportingSender();
        		sender.sendNewServiceRequestToReporting(data);
        	} catch (Exception e){
        		//We only post a warning here.
        		logger.log(Level.WARNING, "sendNewCaseToReporting to reporting failed with " + e);
        		e.printStackTrace();
        	}
        	ThreadLocalStopwatch.getWatch().time("END NewCaseNonCirmEventHandler");
        }
    }
}

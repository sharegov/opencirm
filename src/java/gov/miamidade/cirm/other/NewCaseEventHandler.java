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

import java.util.logging.Logger;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.event.EventTrigger;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

public class NewCaseEventHandler implements EventTrigger
{
    private static Logger logger = Logger.getLogger("gov.miamidade.cirm.other");
    
    @SuppressWarnings("unused")
    @Override
    public void apply(OWLNamedIndividual entity, OWLNamedIndividual changeType, Json data)
    {
        ThreadLocalStopwatch.getWatch().time("START NewCaseEventHandler entity: " + entity + " changetype: " + changeType);
    	DepartmentIntegration D = new DepartmentIntegration();
        Json deptInterface = D.getDepartmentInterfaceCode(data.at("case").at("type").asString());
        if (!deptInterface.isNull())
           data.at("case").set("hasLegacyInterface", deptInterface);

        if (!deptInterface.isNull() 
                /*
                && 
                (!locationInfo.has("extendedInfo")||!locationInfo.at("extendedInfo").has("error")) */)
        {
            if (false) // send immediately or delay...for debugging purposes (normally we delay)
            {
                D.sendToDepartment(data.at("case"), data.at("locationInfo"));
            }
            else
            {
                Json delay = D.delaySendToDepartment(data.at("case"), data.at("locationInfo"), -1); 
                if (!delay.is("ok", true))
                {
                    ThreadLocalStopwatch.getWatch().time("IMMEDIATE DEPT SEND NewCaseEventHandler");
                    logger.warning("Unable to schedule send case to department: " + 
                                    delay + ", sending immediately...");
                    D.sendToDepartment(data.at("case"), data.at("locationInfo"));
                }
            }
        }
        ThreadLocalStopwatch.getWatch().time("END NewCaseEventHandler");
    }
}

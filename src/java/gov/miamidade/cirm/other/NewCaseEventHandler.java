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

import gov.miamidade.cirm.MDRefs;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.event.EventTrigger;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

/**
 * Handler for new Service Requests created in CiRM (311HUb UI) that may need sending to departmental interfaces and
 * live reporting (if enabled).
 * <br> 
 * Sending to live reporting will be immediate. 
 * <br> 
 * Sending to department will be delayed for a fix grace period using the time machine. if scheduling fails, a case 
 * would be sent immediately.
 * <br>
 *  
 * @author Thomas Hilpold
 *
 */
public class NewCaseEventHandler implements EventTrigger
{
	public static final boolean USE_DELAY_SEND_SR_TO_DEPARTMENT = true;
	
	/**
	 * Send all digital intake cases immediately. 
	 */
	public final static String[] DELAY_EXEMPT_INTAKE_METHODS = new String[] {"WEB", "IPHONE", "ANDROID", "APPEOCA", "BLKBERY", "ZAPATA"};
	
    private static Logger logger = Logger.getLogger("gov.miamidade.cirm.other");
    
    @Override
    public void apply(OWLNamedIndividual entity, OWLNamedIndividual changeType, Json data)
    {
    	//assert changeType == BO_NEW
        ThreadLocalStopwatch.start("START NewCaseEventHandler entity: " + entity + " changetype: " + changeType);
        if (MDRefs.liveReportingStatus.resolve().isEnabled()) {
        	sendNewCaseToReporting(data);
        	ThreadLocalStopwatch.now("NewCaseEventHandler sendNewCaseToReporting completed");
        }
        sendNewCaseToDepartmentInterfaceIfNeeded(data);
        ThreadLocalStopwatch.now("NewCaseEventHandler sendNewCaseToDepartmentInterfaceIfNeeded completed");
        ThreadLocalStopwatch.stop("END NewCaseEventHandler");        
    }
    
    /**
     * Immediately sends a new SR to live reporting.
     * 
     * @param newSR
     * @throws never any exception, logs on failure.
     */
    private void sendNewCaseToReporting(Json newSR) {
    	try {
    		LiveReportingSender sender = new LiveReportingSender();
    		sender.sendNewServiceRequestToReporting(newSR);
    	} catch (Exception e){
    		//We only post a warning here.
    		logger.log(Level.WARNING, "sendNewCaseToReporting to reporting failed with " + e);
    		e.printStackTrace();
    	}
    }
    
    /**
     * 
     * Modifies the newSR (adds property case.hasLegacyInterface)
     * @param newSR
     * @return true if case needed to be sent, false otherwise
     */
    private boolean sendNewCaseToDepartmentInterfaceIfNeeded(Json newSR) {
    	DepartmentIntegration D = new DepartmentIntegration();
        Json deptInterface = D.getDepartmentInterfaceCode(newSR.at("case").at("type").asString());
        boolean needsToBeSent = !(deptInterface == null || deptInterface.isNull()); 
        if (needsToBeSent) 
        {
        	newSR.at("case").set("hasLegacyInterface", deptInterface);
            if (USE_DELAY_SEND_SR_TO_DEPARTMENT && !isDelayExempt(newSR)) // send immediately or delay...for debugging purposes (normally we delay)
            {
                Json delay = D.delaySendToDepartment(newSR.at("case"), newSR.at("locationInfo"), -1); 
                if (!delay.is("ok", true))
                {
                    ThreadLocalStopwatch.now("Delay failed, IMMEDIATE DEPT SEND NewCaseEventHandler");
                    logger.warning("Unable to schedule send case to department: " + 
                                    delay + ", sending immediately...");
                    D.sendToDepartment(newSR.at("case"), newSR.at("locationInfo"));
                }
            }
            else 
            {
            	//Don't delay use immediate send not allowing for calltakers to fix an SR after save
            	//Use for testing only.
            	D.sendToDepartment(newSR.at("case"), newSR.at("locationInfo"));
            }
        }
        return needsToBeSent;
    }
    
    private boolean isDelayExempt(Json newSR) {
    	boolean result = false;
    	try {
    		Json properties = newSR.at("case");
    		Json intakeMethod = null;
    		if (properties.has("legacy:hasIntakeMethod")) {
    			intakeMethod = properties.at("legacy:hasIntakeMethod");
    		} else if (properties.has("hasIntakeMethod")) {
    			intakeMethod = properties.at("hasIntakeMethod");
    		} else {
    			//not found, null, return false
    		}
    		if (intakeMethod != null) {
    			if (intakeMethod.isObject()) {
    				intakeMethod = intakeMethod.at("iri");
    			}
    			String intakeMethodStr = intakeMethod.asString();
    			int hashIdx = intakeMethodStr.indexOf("#");
    			if (hashIdx > 0) {
    				intakeMethodStr = intakeMethodStr.substring(hashIdx + 1, intakeMethodStr.length());
    				intakeMethodStr = intakeMethodStr.toUpperCase();
    			}
    			result = Arrays.asList(DELAY_EXEMPT_INTAKE_METHODS).contains(intakeMethodStr);
    			if (result) {
    				ThreadLocalStopwatch.now("NewCaseEventHandler: " + intakeMethodStr + " digital intake SR detected, sending immediately");
    			}
    		} else {
    			throw new IllegalStateException();
    		}
    	} catch (Exception e) {
    		ThreadLocalStopwatch.error("ERROR: NewCaseEventHandler: Could not determine intake method of SR: " + newSR);
    	}
    	return result;
    }
}

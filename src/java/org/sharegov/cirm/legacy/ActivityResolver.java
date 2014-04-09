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
package org.sharegov.cirm.legacy;

import java.util.Arrays;
import java.util.Properties;
import java.util.TreeSet;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLLiteral;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.utils.SRJsonActivityUtil;

/**
 * Resolves many Activity related variables to their values in an SR Json. 
 * This class was extended by assuming variable naming patterns during City of Miami adaption. 
 * 
 * Activities are found by legacy code in the SRs activities.
 * By default, the legacy code set in properties (LEGACY_CODE) determines which activity is used to resolve the variable. 
 * But, if a MessageVariable has a hasLegacyCode dataproperty, it's value will be used instead.
 *  
 *  
 * 
 * @author Syed Abbas, Thomas Hilpold
 */
public class ActivityResolver implements VariableResolver
{
	public static boolean DBG = true;
	public static boolean VALIDATE_VARS_POST_RESOLUTION = true;
	public static String VAR_SR_ACTIVITY_TYPE = "$$SR_ACTIVITY_TYPE$$";
	
	public static final String DATE_PATTERN = "MMMM d, yyyy h:mm aa"; //from SQL: "Month DD, YYYY HH:MM AM";
	
	@Override
	public String resolve(String variableName, Json sr, Properties properties)
	{
		String activityLegacyCode;
		
		OWLLiteral variableLegacyCode = OWL.dataProperty(MessageManager.findIndividualFromVariable(variableName), "legacy:hasLegacyCode");
		if (variableLegacyCode != null && variableLegacyCode.getLiteral().length() > 0)
			activityLegacyCode = variableLegacyCode.getLiteral(); //look for a specific activity as defined with the variable
		else
			activityLegacyCode = properties.getProperty("LEGACY_CODE");
		Json activity = SRJsonActivityUtil.getMostRecentActivityByLegacyCode(sr, activityLegacyCode);
		if (activity == null || activity.isNull()) 
		{
			System.out.println("Messaging - ActivityResolver: unable to find activity " + properties.getProperty("LEGACY_CODE") + " in SR " + sr);				
			return null;
		}
		String result = null;
		if(VAR_SR_ACTIVITY_TYPE.equals(variableName))
			result = SRJsonActivityUtil.getActivityTypeLabel(activity);
		else 
		{
			if (variableName.contains("_OUTCOME")) 
			{
				result = SRJsonActivityUtil.getHasOutcomeLabel(activity);
			} 
			else if (variableName.contains("_DETAILS") || variableName.contains("_DTLS$$"))
			{
				result = SRJsonActivityUtil.getHasDetails(activity);
			} 
			else if (variableName.contains("_DUE_DTE"))
			{
				result = SRJsonActivityUtil.getHasDueDate(activity, DATE_PATTERN);
			}
			else if (variableName.equals("$$SR_ACTIVITY_DATE_TIME$$"))
			{
				result = SRJsonActivityUtil.getHasDateCreated(activity, DATE_PATTERN);
			}
			else if (variableName.equals("$$SR_ACTIVITY_DUEDATE_SWR$$"))
			{
				result = SRJsonActivityUtil.getDueDate90Days(activity, DATE_PATTERN);
			}
			else if (variableName.contains("SR_ACTIVITY_CALLCREATED_D"))
			{
				result = SRJsonActivityUtil.getIsCreatedByName(activity);
			}
			else if (variableName.equals("$$SR_ASSIGNED_STAFF$$"))
			{
				result = SRJsonActivityUtil.getAssignedStaffName(activity);
			}
			else
			{
				System.out.println("Messaging - ActivityResolver: unable to resolve variable" + variableName);
			}
			//Just a check if we already know the variable.
			if (VALIDATE_VARS_POST_RESOLUTION && !ActivityVariableValidator.isKnown(variableName))
				System.err.println("ActivityResolver resolved an unknown variable: " + variableName + " to value " + result);
		}
		if (DBG) {
			System.out.println("ActivityResolver: Var " + variableName + " Result: " + result + " Act: " + activity + " Code: " + activityLegacyCode);
		}
		return result;
	}

	
	/**
	 * ActivityVariableValidator holds a lost of known City of Miami activity based variables.
	 * 
	 * The ActivityResolver was build based on custom SQL provided by City of Miami in a as generic as possible way.
	 * The validator serves the purpose to detect during runtime, if other non COM variables are passed to it and may be removed later,
	 * if the assumed variable name patterns in the "resolve" method hold.
	 * 
	 * @author Thomas Hilpold
	 *
	 */
	static class ActivityVariableValidator 
	{
		public final static String[] VARS_COM_SR_ACTIVITY = new String[] {
			//_OUTCOME
			"$$SR_ACTIVITY_OUTCOME$$",
			"$$SR_COMPRIUM_OUTCOME$$",
			"$$SR_INSPECT1_OUTCOME$$",
			"$$SR_STATUS_OUTCOME$$", //COMPRIUM
			//_DETAIL OR _DTLS
			"$$SR_ACTIVITY_DETAILS_1E$$",
			"$$SR_ACTIVITY_DETAILS_2E$$",
			"$$SR_ACTIVITY_DETAILS_D4$$",
			"$$SR_DEPTRES1_DETAILS$$",    //removed space, modify onto
			"$$SR_NOTUFBS_DETAILS$$",
			"$$SR_DENIALDE_DETAILS$$",
			"$$SR_ENTER2_DETAILS$$",
			"$$SR_ENTERFUT_DETAILS$$",
			"$$SR_ENTERIN1_DETAILS$$",
			"$$SR_ENTERIN2_DETAILS$$",
			"$$SR_ESCALAT2_DETAILS$$",
			"$$SR_NOTFYCHF_DETAILS$$",
			"$$SR_NOTIFYDD_DETAILS$$",
			"$$SR_REJDPARK_DETAILS$$",
			"$$SR_REJRISK_DETAILS$$",
			"$$SR_REPNET_DETAILS$$",
			"$$SR_REPOLIC_DETAILS$$",
			"$$SR_REPUBLIC_DETAILS$$",
			"$$SR_REZONI_DETAILS$$",
			"$$SR_UPDATE_DETAILS$$",
			"$$SR_COMENTER_ACTIVITY_DTLS$$",
			"$$SR_COMINSP_ACTIVITY_DTLS$$",
			//_DUE
			"$$SR_ACTIVITY_DUEDATE_SWR$$", //RESODET
			"$$ACTVTY_DEPBUDR1_DUE_DTE$$",		
			"$$ACTVTY_INSPECT1_DUE_DTE$$",
			"$$ACTVTY_STATUS_DUE_DTE$$",
			"$$ACTVTY_DEPMMEH1_DUE_DTE$$",
			//Custom
			"$$SR_ACTIVITY_CALLCREATED_D4$$", //Creator Name
			//
			"$$SR_ACTIVITY_DATE_TIME$$",
			//later added
			"$$SR_ACTIVITY_CALLCREATED_D5$$",
			"$$SR_ACTIVITY_DETAILS_D5$$",
			"$$SR_ASSIGNED_STAFF$$"
		};
		static final TreeSet<String>  VARS_COM_SR_ACTIVITY_SET = new TreeSet<String>(Arrays.asList(VARS_COM_SR_ACTIVITY));
		
		/**
		 * Checks, if variable is a know City of Miami activity variable. 
		 * @return true, if variable is a known COM activity variable.
		 */
		static boolean isKnown(String variableName) {
			return VARS_COM_SR_ACTIVITY_SET.contains(variableName);
		}
	}

}

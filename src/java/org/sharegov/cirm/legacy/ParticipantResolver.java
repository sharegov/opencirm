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

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import mjson.Json;

public class ParticipantResolver implements VariableResolver
{
	public static boolean DBG = true;
	//COM used variables:
	public static final String VAR1_APLICANT_PHONE = "$$APLICANT_PHONE$$"; // Phone number of APLICAN //missing P intended
	public static final String VAR2_APPLICANT_ADDRESS1 = "$$APPLICANT_ADDRESS1$$"; //FullAddress of APPLICAN
	public static final String VAR3_APPLICANT_ADDRESS2 = "$$APPLICANT_ADDRESS2$$"; // City StateCode Zipcode of APPLICAN
	public static final String VAR4_APPLICANT_NAME = "$$APPLICANT_NAME$$"; //First Middle LastName of APPLICAN
	public static final String VAR5_CITIZENS_ADDRESS1 = "$$CITIZENS_ADDRESS1$$"; //FullAddress of CITIZEN
	public static final String VAR6_CITIZENS_ADDRESS2 = "$$CITIZENS_ADDRESS2$$"; //City StateCode Zipcode of CITIZEN
	public static final String VAR7_CITIZENS_EMAIL = "$$CITIZENS_EMAIL$$"; // Email of Citizen
	public static final String VAR8_CITIZENS_NAME = "$$CITIZENS_NAME$$"; //First Middle LastName of CITIZEN
	public static final String VAR9_CITIZENS_PHONE = "$$CITIZENS_PHONE$$"; // Phone number of CITIZEN

	//Resolved Later:
	//PHONE NUMBER - will resolve to a txt formatted table of all supplied phone numbers, if more than one.
	public static final String VAR10_CITIZENS_PHONE_NTYPE = "$$CITIZENS_PHONE_NTYPE$$"; // Phone number of CITIZEN
	public static final String VAR11_CITIZENS_PHONE_NTYPED1 = "$$CITIZENS_PHONE_NTYPED1$$"; // Phone number of CITIZEN
	public static final String VAR12_CITIZENS_PHONE_NTYPED4 = "$$CITIZENS_PHONE_NTYPED4$$"; // Phone number of CITIZEN
	public static final String VAR13_CITIZENS_PHONE_NTYPED5 = "$$CITIZENS_PHONE_NTYPED5$$"; // Phone number of CITIZEN
	// For BACKWARD COMPATIBIULITY
	//DETAILS (used to be extension, or some string or cell number in legacy)
	public static final String VAR14_CITIZENS_PHONE_NTYPE_DET = "$$CITIZENS_PHONE_NTYPE_DET"; // Phone number of CITIZEN
	public static final String VAR15_CITIZENS_PHONE_NTYPE_DETD1 = "$$CITIZENS_PHONE_NTYPE_DETD1$$"; // Phone number of CITIZEN
	public static final String VAR16_CITIZENS_PHONE_NTYPE_DETD4 = "$$CITIZENS_PHONE_NTYPE_DETD4$$"; // Phone number of CITIZEN
	public static final String VAR17_CITIZENS_PHONE_NTYPE_DETD5 = "$$CITIZENS_PHONE_NTYPE_DETD5$$"; // Phone number of CITIZEN
	//PHONE TYPE CODE
	public static final String VAR18_CITIZENS_PHONE_NTYPE_SEL = "$$CITIZENS_PHONE_NTYPE_SEL$$"; // Phone number of CITIZEN
	public static final String VAR19_CITIZENS_PHONE_NTYPE_SELD1 = "$$CITIZENS_PHONE_NTYPE_SELD1$$"; // Phone number of CITIZEN
	public static final String VAR20_CITIZENS_PHONE_NTYPE_SELD4 = "$$CITIZENS_PHONE_NTYPE_SELD4$$"; // Phone number of CITIZEN
	public static final String VAR21_CITIZENS_PHONE_NTYPE_SELD5 = "$$CITIZENS_PHONE_NTYPE_SELD5$$"; // Phone number of CITIZEN
	

	private enum ActorEnum {
		CITIZEN, APPLICAN
	}

	
	@Override
	public String resolve(String variableName, Json sr, Properties properties)
	{
		String result;
		if (VAR1_APLICANT_PHONE.equals(variableName))
			result = getActorPhonesMultiLine(getActorJson(sr, ActorEnum.APPLICAN));
		else if (VAR2_APPLICANT_ADDRESS1.equals(variableName))
			result = getActorAddress1(getActorJson(sr, ActorEnum.APPLICAN));
		else if (VAR3_APPLICANT_ADDRESS2.equals(variableName))
			result = getActorAddress2(getActorJson(sr, ActorEnum.APPLICAN));
		else if (VAR4_APPLICANT_NAME.equals(variableName))
			result = getActorName(getActorJson(sr, ActorEnum.APPLICAN));
		else if (VAR5_CITIZENS_ADDRESS1.equals(variableName))
			result = getActorAddress1(getActorJson(sr, ActorEnum.CITIZEN));
		else if (VAR6_CITIZENS_ADDRESS2.equals(variableName))
			result = getActorAddress2(getActorJson(sr, ActorEnum.CITIZEN));
		else if (VAR7_CITIZENS_EMAIL.equals(variableName))
			result = getActorEmail(getActorJson(sr, ActorEnum.CITIZEN));
		else if (VAR8_CITIZENS_NAME.equals(variableName))
			result = getActorName(getActorJson(sr, ActorEnum.CITIZEN));
		else if (VAR9_CITIZENS_PHONE.equals(variableName)
				|| VAR10_CITIZENS_PHONE_NTYPE.equals(variableName)
				|| VAR11_CITIZENS_PHONE_NTYPED1.equals(variableName)
				|| VAR12_CITIZENS_PHONE_NTYPED4.equals(variableName)
				|| VAR13_CITIZENS_PHONE_NTYPED5.equals(variableName)
				)
			{
				result = getActorPhonesMultiLine(getActorJson(sr, ActorEnum.CITIZEN));
			}
		else if (VAR14_CITIZENS_PHONE_NTYPE_DET.equals(variableName)
				|| VAR15_CITIZENS_PHONE_NTYPE_DETD1.equals(variableName)
				|| VAR16_CITIZENS_PHONE_NTYPE_DETD4.equals(variableName)
				|| VAR17_CITIZENS_PHONE_NTYPE_DETD5.equals(variableName)
				|| VAR18_CITIZENS_PHONE_NTYPE_SEL.equals(variableName)
				|| VAR19_CITIZENS_PHONE_NTYPE_SELD1.equals(variableName)
				|| VAR20_CITIZENS_PHONE_NTYPE_SELD4.equals(variableName)
				|| VAR21_CITIZENS_PHONE_NTYPE_SELD5.equals(variableName)
				)
			{
				// Not needed anymode, returning an empty string for backward compatibility.
				result = "";
			}
		else
			result = null;
		if (DBG)
		{
			System.out.println("ParticipantResolver: Var: " + variableName + "result: " + result);
		}
		return result;
	}

	/**
	 * Returns a multi-line string with each line containing phone type + phone number for each entered actor's phone.
	 * No newline will be added, if only one line is returned.
	 * (Multiple phone numbers of same type will be comma separated strings in the actor json: e.g. "1112223333#123,1112223333#222")
	 * @param actorJsonObj
	 * @return
	 */
	public static String getActorPhonesMultiLine(Json actorJsonObj)
	{
		int found = 0;
		String result = "";
		if (actorJsonObj.has("HomePhoneNumber"))
		{
			found++;
			result += "Home: " + actorJsonObj.at("HomePhoneNumber").asString();
		}
		if (actorJsonObj.has("CellPhoneNumber"))
		{
			if (found > 0) result = result + "\r\n";
			found++;
			result += "Cell: " + actorJsonObj.at("CellPhoneNumber").asString();
		}
		if (actorJsonObj.has("BusinessPhoneNumber"))
		{
			if (found > 0) result = result + "\r\n";
			found++;
			result += "Biz:  " + actorJsonObj.at("BusinessPhoneNumber").asString();
		}
		if (actorJsonObj.has("OtherPhoneNumber")) 
		{
			if (found > 0) result = result + "\r\n";
			found++;
			result += "Other:" + actorJsonObj.at("OtherPhoneNumber").asString();
		}
		if (actorJsonObj.has("FaxNumber")) 
		{
			if (found > 0) result = result + "\r\n";
			found++;
			result += "Fax:  " + actorJsonObj.at("FaxNumber").asString();
		}
		if (found > 1) 
			return result + "\r\n";
		else if (found > 0)
			return result;
		else
			return "N/A";
	}

	public String getActorAddress1(Json actorJsonObj)
	{
		if (actorJsonObj.has("atAddress"))
			return actorJsonObj.at("atAddress").at("fullAddress", "").asString();
		else
			return "N/A";
	}

	public static String getActorAddress2(Json actorJsonObj)
	{
		try {
			if (actorJsonObj.has("atAddress"))
			{
				//City StateCode Zipcode 
				Json cityJson = actorJsonObj.at("atAddress").at("Street_Address_City", Json.object());
				Json stateJson = actorJsonObj.at("atAddress").at("Street_Address_State", Json.object());
				if (cityJson.isObject()) cityJson = cityJson.at("iri","owl:Nothing");
				if (stateJson.isObject()) stateJson = stateJson.at("iri", "owl:Nothing");
				IRI cityIRI = IRI.create(cityJson.isString()? cityJson.asString() : "owl:Nothing");
				IRI stateIRI = IRI.create(stateJson.isString()? stateJson.asString() : "owl:Nothing");
				String zipCode = actorJsonObj.at("atAddress").at("Zip_Code", "N/A").asString();
				String cityLabel = OWL.getEntityLabel(OWL.individual(cityIRI));
				if (cityLabel == null) cityLabel = "N/A";
				String stateCode = "";
				Iterator<OWLLiteral> stateCodeIt = OWL.dataProperties(OWL.individual(stateIRI), "mdc:USPS_Abbreviation").iterator();
				if (stateCodeIt.hasNext()) stateCode = stateCodeIt.next().getLiteral();
				if (stateCode == null) stateCode = "N/A";
				return cityLabel + " " + stateCode + " " + zipCode;
			}
			else
				return "N/A";
		} catch(Exception e) 
		{
			ThreadLocalStopwatch.getWatch().time("ParticipantEmailResolver: getActorAddress2: Error on resolve: " + e.toString() + " resuming.");
			e.printStackTrace();
			return "N-A";
		}
	}

	/**
	 * 
	 * @param actorJson
	 * @return String First [Middle] Last name
	 */
	public static String getActorName(Json actorJsonObj)
	{
		String first = actorJsonObj.at("Name", "").asString();
		String last = actorJsonObj.at("LastName", "").asString();
		if (!first.isEmpty() || !last.isEmpty())
			return first + " " + last;
		else 
			return "N/A";
	}

	private String getActorEmail(Json actorJsonObj)
	{
		String emailIRI = actorJsonObj.at("hasEmailAddress", Json.object()).at("iri", "").asString();
		if (emailIRI.length() > 1) 
			return emailIRI.substring("mailto:".length());
		else 
			return "N/A";
	}

	/**
	 * Finds the first actor in the sr json by ActorEnum and returns it's Json object. 
	 * @param sr 
	 * @param citizen
	 * @return the actor json or an empty json object
	 */
	private static Json getActorJson(Json sr, ActorEnum citizen)
	{
		//sr = sr.at("properties", Json.object());
		if (!sr.has("hasServiceCaseActor")) return Json.object();
		List<Json> serviceCaseActors = (sr.at("hasServiceCaseActor").isArray()) ? sr.at("hasServiceCaseActor").asJsonList() : Collections.singletonList(sr.at("hasServiceCaseActor"));
		for (Json actor : serviceCaseActors)
		{
			if (!actor.isObject()) continue;
			String actorTypeIRI = actor.at("hasServiceActor", Json.object()).at("iri", "").asString();
			//e.g. http://www.miamidade.gov/cirm/legacy#CITIZEN
			if (actorTypeIRI.endsWith(citizen.name()))
				return actor;
		}
		return Json.object();
	}
}

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
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import mjson.Json;

/**
 * Finds all emails of all actors in an Sr and returns them as a list of strings semicolon delimited.
 * All emails returned are ensured to be unique (no 2 equal emails will be returned; case sensitive comparison).
 * 
 * @author ?, Thomas Hilpold
 *
 */
public class ParticipantEmailResolver implements VariableResolver
{
	public static boolean DBG = true;
	//public static final String VAR_PARTICIPANT_EMAIL = "$$PARTICIPANT_EMAIL$$";
	//2015.01.09 Multiple MessageVariables refer to ParticipantEmailResolver issue mdCirm 2027
	
	@Override
	public String resolve(String variableName, Json sr, Properties properties)
	{
		String result;
		//if(VAR_PARTICIPANT_EMAIL.equals(variableName))
			result = resolveParticipantEmail(sr, properties);
		//else 
		//	result = null;
		if(DBG)
		{
			System.out.println("ParticipantEmailResolver: Var: " + variableName + " result: " + result);
		}
		return result;
	}
	
	private String resolveParticipantEmail(Json sr, Properties properties)
	{
		String result = null;
		Set<String> emails = new HashSet<String>();
		if(sr.has("hasServiceCaseActor"))
		{
			List<Json> serviceCaseActors = (sr.at("hasServiceCaseActor").isArray()) ? sr.at("hasServiceCaseActor").asJsonList() : Collections.singletonList(sr.at("hasServiceCaseActor"));
			for(Json actor : serviceCaseActors)
			{
				if (actor.has("hasEmailAddress")) 
				{
					if (actor.at("hasEmailAddress").has("iri"))
					{						
						if (actor.at("hasEmailAddress").at("iri").isString())
						{
							String emailAddress = actor.at("hasEmailAddress").at("iri").asString();
							if (emailAddress.startsWith("mailto:") && emailAddress.contains("@")) 
							{
								emailAddress = emailAddress.substring("mailto:".length());
								//add valid email
								emails.add(emailAddress); //+ ";";
							} 
							else
								System.out.println("ParticipantEmailResolver: user enter bad email in hasServiceCaseActor: " + emailAddress + sr.at("boid"));
						}
					}
				}
			}
		}
		for (String email : emails) {
			if (result == null) result = "";
			result += email + ";";
		}
		return result;
	}
}

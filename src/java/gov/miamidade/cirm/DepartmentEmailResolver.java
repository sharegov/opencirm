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
package gov.miamidade.cirm;

import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.legacy.VariableResolver;
import org.sharegov.cirm.rest.UserService;

public class DepartmentEmailResolver implements VariableResolver
{
	public static boolean DBG = false;
	public static final String VAR_DEPARTMENT_DEPUTY_MAYOR_EMAIL = "$$SELECTED_DEPT_DEP_MAYOR_EMAIL$$";
	public static final String VAR_DEPARTMENT_DEPUTY_MAYOR_SALUTATION = "$$SELECTED_DEPT_DEP_MAYOR_SALUTATION$$";
	public static final String VAR_DEPARTMENTS = "$$SELECTED_DEPARTMENTS$$";
	public static final String SERVICE_FIELD_IRI = "http://www.miamidade.gov/cirm/legacy#MAYECC_Q2";
	public static final String HAS_EMAIL_ADDRESS_IRI = "http://www.miamidade.gov/ontology#hasEmailAddress";
	
	/**
	 * DLQ: EmailAddress and inverse hasEmailAddress some (Deputy_Mayor and inverse hasDeputyMayor value Community_Information_and_Outreach)
	 * 		@Override
	 * @param sr: sr.at("hasServiceAnswer") expected 
 	 */
	public String resolve(String variableName, Json sr, Properties properties)
	{
		String result = null;
		if(VAR_DEPARTMENT_DEPUTY_MAYOR_EMAIL.equals(variableName))
		{
			result = resolveVAR_DEPARTMENT_DEPUTY_MAYOR_EMAIL(sr, properties);
		} 
		else if (VAR_DEPARTMENT_DEPUTY_MAYOR_SALUTATION.equals(variableName))
		{
			result = resolveVAR_DEPARTMENT_DEPUTY_MAYOR_SALUTATION(sr, properties);
		} 
		else if (VAR_DEPARTMENTS.equals(variableName))
		{
			result = resolveVAR_DEPARTMENTS(sr, properties);
		}
		return result;
	}

	
	private Set<String> getDeputyMayorEmailAddresses(Json sr) 
	{
		if (sr.at("hasServiceAnswer").isArray())
		{
			Json hasServiceAnswerArr = sr.at("hasServiceAnswer");
			for(Json serviceAnswer : hasServiceAnswerArr.asJsonList())
			{
				String fieldIRI = serviceAnswer.at("hasServiceField").at("iri").asString();
				if (SERVICE_FIELD_IRI.equals(fieldIRI)) 
				{
					Set<String> deputyMayorIRIs = new HashSet<String>();
					if (serviceAnswer.at("hasAnswerObject").isObject())
						fillDeputyMajorIRI(serviceAnswer.at("hasAnswerObject"), deputyMayorIRIs);
					else
						for(Json answerObject : serviceAnswer.at("hasAnswerObject").asJsonList())
							fillDeputyMajorIRI(answerObject, deputyMayorIRIs);
					return getDeputyMayorEmailAddressesByIRI(deputyMayorIRIs);
				} //if
			}
		}
		//If went wrong.
		return new HashSet<String>();
	}
	
	private void fillDeputyMajorIRI(Json answerObject, Set<String> deputyMayorIRIs)
	{
		if (answerObject.isObject() && answerObject.has("hasDeputyMayor"))
		{   
			if (answerObject.at("hasDeputyMayor").isObject()) 
				deputyMayorIRIs.add(answerObject.at("hasDeputyMayor").at("iri").asString());
			else
				deputyMayorIRIs.add(answerObject.at("hasDeputyMayor").asString());
		}
		else 
		{
			if (answerObject.isObject())
				System.err.println("No deputy mayor for " + answerObject.at("iri"));
			else 
				System.err.println("Answerobject not object type" + answerObject.toString());
		}
	}

	/**
	 * Queries meta for hasEmailAddress for each given deputy mayor iri.
	 * @param depMayIRis
	 */
	private Set<String> getDeputyMayorEmailAddressesByIRI(Set<String> depMayIRis) 
	{
		Set<String> result = new HashSet<String>();
		for(String iri : depMayIRis)
		{
			OWLNamedIndividual emailAddressAsIRI = OWL.objectProperty(OWL.individual(iri), HAS_EMAIL_ADDRESS_IRI);
			result.add(emailAddressAsIRI.getIRI().getFragment());
		}
		return result;
	}

	/**
	 * Queries meta for hasEmailAddress for each given deputy mayor iri.
	 * Returns empty string if none found or empty set given.
	 * 
	 * @param depMayIRis
 	 * @return a semicolon separated list of email addresses with a trailing semicolon or an empty string.
	 */
	private String getSemicolonSeparated(Set<String> strings) 
	{
		String result = "";
		for(String str : strings)
		{
			result += str + ";";
		}
		return result;
	}

	/**
	 * DLQ: EmailAddress and inverse hasEmailAddress some (Deputy_Mayor and inverse hasDeputyMayor value Community_Information_and_Outreach)
	 * 		@Override
	 * @param sr: sr.at("hasServiceAnswer") expected 
 	 */
	private String resolveVAR_DEPARTMENT_DEPUTY_MAYOR_EMAIL(Json sr, Properties properties) 
	{
		return getSemicolonSeparated(getDeputyMayorEmailAddresses(sr));
	}

	private String resolveVAR_DEPARTMENT_DEPUTY_MAYOR_SALUTATION(Json sr, Properties properties) 
	{
		StringBuffer result = new StringBuffer(1000);
		Set<String> emailAddresses = getDeputyMayorEmailAddresses(sr);
		Set<String> salutations = new TreeSet<String>();
		UserService us = new UserService();
		for (String email : emailAddresses) 
		{
			StringBuffer curSalutation = new StringBuffer(100);
			curSalutation.append("Dear ");  
			Json L = us.searchProvider("intranet", Json.object("mail", email), 1);
			if (L.asJsonList().isEmpty())
				curSalutation.append(email);
			else
			{
			    Json user = L.at(0);
				//dbgPrintUser(user);
				//curSalutation.append(user.get("title") + " ");  
				curSalutation.append("Deputy Mayor ");
				curSalutation.append(user.at("givenName").asString() + " ");
				if (user.has("Initials") && user.at("Initials").asString().length() > 0)
					curSalutation.append(user.at("Initials").asString() + " ");
				curSalutation.append(user.at("sn").asString() + "!");
			}
			salutations.add(curSalutation.toString());
		}
		for (String salutation : salutations) 
		{
			result.append(salutation + "<br/>\r\n");
		}
		return result.toString(); 
	}
	
	private void dbgPrintUser(Map<String, Object> user) 
	{
		if (user == null) return;
		for (Map.Entry<String, Object> entry : user.entrySet()) 
			System.out.println(entry.getKey() + " = " + entry.getValue());
	}

	private String resolveVAR_DEPARTMENTS(Json sr, Properties properties) 
	{
		String result = "";
		Set<String> departmentLabels = new TreeSet<String>();
		if (sr.at("hasServiceAnswer").isArray()) 
		{
			Json hasServiceAnswerArr = sr.at("hasServiceAnswer");
			for(Json serviceAnswer : hasServiceAnswerArr.asJsonList())
			{
				String fieldIRI = serviceAnswer.at("hasServiceField").at("iri").asString();
				if (SERVICE_FIELD_IRI.equals(fieldIRI)) 
				{
					Json hasAnswerObject = serviceAnswer.at("hasAnswerObject");
					if (hasAnswerObject.isObject())
						fillDepartmentLabel(hasAnswerObject, departmentLabels);
					else
						for(Json answerObject : serviceAnswer.at("hasAnswerObject").asJsonList())
							fillDepartmentLabel(answerObject, departmentLabels);
				}
			}
		}
		int i = 0;
		for (String dlabel : departmentLabels) 
		{
			result += dlabel;
			if (departmentLabels.size() > 1 && i == departmentLabels.size() - 2) result += ",and ";
			else
				if (i < departmentLabels.size() - 1) result += ", ";
			i++;
		}
		return result;
	}	
	/**
	 * 
	 * @param answerObject aJson object
	 * @param departmentLabels
	 */
	private void fillDepartmentLabel(Json answerObject, Set<String> departmentLabels) 
	{
		if (answerObject.isObject() && answerObject.has("label"))
		{   
			departmentLabels.add(answerObject.at("label").asString());
		}
		else 
		{
			if (answerObject.isObject())
				System.err.println("resolveVAR_DEPARTMENTS:No label for " + answerObject.at("iri"));
			else 
				System.err.println("resolveVAR_DEPARTMENTS:Answerobject not object type" + answerObject.toString());
		}
	}
}

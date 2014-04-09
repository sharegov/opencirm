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
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.OWL;

import mjson.Json;

/**
 * Resolves all message variables that refer to an AnswerValue/Object(s) of a ServiceQuestion.
 * Each variable will be resolved dependent on its hasLegacyCode property.
 * If hasLegacyCode is no REGEXP, one answer for a question, whose IRI fragments ends with the legacy code will be returned. 
 * Else if hasLegacyCode starts with REGEX_DETECT_STR "(", the result will contain answers to all questions matching the regex pattern by iri fragment in the given sr.
 * (Separarted by ";", no ")
 *  
 * @author Thomas Hilpold
 */
public class AnswerResolver implements VariableResolver
{
	public static boolean DBG = true;
	public static final String HAS_LEGACY_CODE_DP = "legacy:hasLegacyCode";
	public static final char ANSWER_SEPARATOR_CHAR = ';';
	public static final String REGEX_DETECT_STR = "(";
	
	@Override
	public String resolve(String variableName, Json sr, Properties properties)
	{
		String result = null;
		//1. Get Var individual and hasLegacyCode
		OWLNamedIndividual varInd = MessageManager.findIndividualFromVariable(variableName);
		if (varInd == null) 
			{	
				System.err.println("AnswerResolver Error: varInd not found for: " + variableName );
				return null;
			}
		OWLLiteral questionEndsWithLit = OWL.dataProperty(varInd, HAS_LEGACY_CODE_DP);
		if (questionEndsWithLit == null || questionEndsWithLit.getLiteral() == null 
				|| questionEndsWithLit.getLiteral().isEmpty()) 
			{
				System.err.println("AnswerResolver Error: questionEndsWithLit not found for: " + variableName );			
				return null;
			}
		String serviceQuestionEndsWith = questionEndsWithLit.getLiteral();
		try {
			result =  resolveQuestionAnswer(sr, serviceQuestionEndsWith);
		} catch (Exception e) {
			System.err.println("AnswerResolver Error: Exception resolving var " + variableName + " for sr: " + sr.toString());
			e.printStackTrace();
		}
		if (DBG)
		{
			System.out.println("AnswerResolver: Var: " + variableName + " result: " + result + " code: " + serviceQuestionEndsWith);
		}
		return result;
	}
	
	/**
	 * Gets all answers to a question matching the serviceQuestionSuffixOrRegex.
	 * A regexp is detected by startswith REGEX_DETECT_STR.
	 * If the rexep matches multiple service questions in the same SR, the answers 
	 * will be separated by ANSWER_SEPARATOR_CHAR ";" (e.g. A1;A2;).
	 * 
	 * @param sr
	 * @param serviceQuestionSuffix
	 * @return
	 */
	private String resolveQuestionAnswer(Json sr, String serviceQuestionSuffixOrRegex)
	{
		//TODO: use a cache for allAnswers by key (BOID  UPDATEDTIME) 
		List<Json> allAnswers = sr.at("hasServiceAnswer").isArray()? sr.at("hasServiceAnswer").asJsonList() : Collections.singletonList(sr.at("hasServiceAnswer"));
				//ServiceRequestReportUtil.getAllServiceAnswers(sr);
		if (serviceQuestionSuffixOrRegex.startsWith(REGEX_DETECT_STR)) 
		{
			Pattern serviceFieldFragmentRegex;
			try {
				serviceFieldFragmentRegex = Pattern.compile(serviceQuestionSuffixOrRegex);
			} catch (Exception e) 
			{
				System.err.println("AnswerResolver. Pattern.compile failed for pattern: " + serviceQuestionSuffixOrRegex + " aborting answer resolving");
				return null;
			}
			return getAllServiceAnswers(serviceFieldFragmentRegex, allAnswers);
		} 
		else 
		{
			return getServiceAnswer(serviceQuestionSuffixOrRegex, allAnswers);
		}
	}
	
	protected String getServiceAnswer(String serviceFieldFragmentSuffix, List<Json> allAnswers)
	{
		String result = "";
		for (Json tempObj : allAnswers)
		{
			String candidate = tempObj.at("hasServiceField", Json.object()).at("iri", "").asString();
			if (candidate.endsWith(serviceFieldFragmentSuffix)) {
				//hasAnswerObject could be array of {iri, label};
				if (tempObj.has("hasAnswerObject")) {
					Json hasAnswerObjectArrOrObj = tempObj.at("hasAnswerObject");
					List<Json> hasAnswerObjectlist = hasAnswerObjectArrOrObj.isArray()? hasAnswerObjectArrOrObj.asJsonList() :  Collections.singletonList(hasAnswerObjectArrOrObj);
					int i = 0;
					for (Json answerObject : hasAnswerObjectlist) 
					{
						result += answerObject.at("label", "").asString();
						if (i == hasAnswerObjectlist.size() - 2) result += " and ";
						else if (i < hasAnswerObjectlist.size() - 1) result += ", ";
						i++;
					}
				}
				else if (tempObj.has("hasAnswerValue")) 
				{
					result = tempObj.at("hasAnswerValue", "").asString();
				}
				// else not valid 
			}
		} 
		return result;
	}
	
	/**
	 * Gets all service answers concatenated by ANSWER_SEPARATOR_CHAR, whose IRI fragment matches the given regex pattern.
	 * @param serviceFieldFragmentRegex
	 * @param allAnswers
	 * @return
	 */
	protected String getAllServiceAnswers(Pattern serviceFieldFragmentRegex, List<Json> allAnswers)
	{
		String result = "";
		for (Json tempObj : allAnswers)
		{
			boolean found = false;
			Matcher candidateFragmentMatcher;
			String candidateFull = tempObj.at("hasServiceField", Json.object()).at("iri", "").asString();
			candidateFragmentMatcher = serviceFieldFragmentRegex.matcher((IRI.create(candidateFull).getFragment()));
			if (candidateFragmentMatcher.matches()) {
				//hasAnswerObject could be array of {iri, label};
				if (tempObj.has("hasAnswerObject")) {
					found = true;
					Json hasAnswerObjectArrOrObj = tempObj.at("hasAnswerObject");
					List<Json> hasAnswerObjectlist = hasAnswerObjectArrOrObj.isArray()? hasAnswerObjectArrOrObj.asJsonList() :  Collections.singletonList(hasAnswerObjectArrOrObj);
					int i = 0;
					for (Json answerObject : hasAnswerObjectlist) 
					{
						result += answerObject.at("label", "").asString();
						if (i == hasAnswerObjectlist.size() - 2) result += " and ";
						else if (i < hasAnswerObjectlist.size() - 1) result += ", ";
						i++;
					}
				}
				else if (tempObj.has("hasAnswerValue")) 
				{
					found = true;
					result += tempObj.at("hasAnswerValue", "").asString();
				}
				// else not valid 
				// ans1;ans2;ans3;
				if (found) result += ANSWER_SEPARATOR_CHAR;
			}
		} 
		return result;
	}

}

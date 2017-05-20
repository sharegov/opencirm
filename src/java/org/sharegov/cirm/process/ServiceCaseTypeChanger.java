package org.sharegov.cirm.process;

import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.ontology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.OWL;

import mjson.Json;

/**
 * Performs extensive validation and changes the type of an SR, if compatible with existing SR (use only during approval!).
 * The modified SR will not be persisted by this class.
 * 
 * @author Thomas Hilpold
 *
 */
public class ServiceCaseTypeChanger {
	
	
	/**
	 * Changes the type of an SR, if all validation passes. 
	 * Answers will be removed but a textual version with a type change hint inserted into hasDetails (the description field).
	 * All other data is available after the type change, including all activities, all actors, attachments, status, etc.  <br>
	 * <br>
	 * Any validation failure will lead to an exception with an appropriate message.
	 * <br>
	 * Param bo is expected to be in non expanded and ui form.
	 * <code>
	 * bo.iri
	 * bo.boid 
	 * bo.type
	 * bo.properties.hasDateCreated
	 * bo.properties.hasXCoordinate double
	 * bo.properties.hasPriority {} or iri
	 * bo.properties.atAddress {} optional
	 * bo.properties.hasDateLastModified optional
	 * bo.properties.hasServiceActivity[] optional //check if all are avail in target type if any
	 * bo.properties.hasYCoordinate double
	 * bo.properties.isModifiedBy optional
	 * bo.properties.hasIntakeMethod {} or iri
	 * bo.properties.hasAttachment [string] or string
	 * bo.properties.hasStatus {} or iri
	 * bo.properties.isCreatedBy string
	 * bo.properties.hasCaseNumber str
	 * bo.properties.hasGisDataId opt
	 * bo.properties.hasServiceCaseActor[{ hasServiceActor {iri} or iri}] //check if all are avail in target type if any
	 * </code>
	 * @param bo sr in ui compatible format, may be unexpanded.
	 * 
	 * @param targetTypeFragment a fragment of the target type candidate.
	 * @return
	 */
	public Json typeChange(Json bo, String targetTypeFragment) {
		if (targetTypeFragment == null || targetTypeFragment.isEmpty()) throw new IllegalArgumentException("targetTypeFragment null or empty");
		Json targetType = tryGetTargetType(targetTypeFragment);
		validateTargetTypeEnabled(targetType);		
		Json target = bo.dup();
		String oldType = bo.at("type").asString();
		validateDifferentType(oldType, targetTypeFragment);
		Json properties = bo.at("properties");
		target.set("iri", modifyBoIRI(target.at("iri").asString(), targetTypeFragment));
		target.set("type", targetTypeFragment.trim());
		validateExistingServiceCaseActors(properties, targetType);
		validateExistingActivities(properties, targetType);
		String oldAnswers = answersAsString(properties);
		String newHasDetails = "";
		if (bo.at("properties").has("hasDetails") && bo.at("properties").at("hasDetails").isString()) {
			newHasDetails = bo.at("properties").at("hasDetails").asString() + "\r\n\r\n";
		}
		newHasDetails += "Type change from " + oldType + " to " + targetTypeFragment + " Old Answers below: \r\n";
		newHasDetails += oldAnswers;
		//Clear OLD ANSWERS!!
		target.at("properties").set("hasServiceAnswer", Json.array());
		target.at("properties").set("hasDetails", newHasDetails);
		return target;
	}		
	
	private void validateDifferentType(String oldType, String targetTypeFragment) {
		if (oldType.equalsIgnoreCase(targetTypeFragment)) {
			throw new IllegalArgumentException("A type change from " + targetTypeFragment + " to the exact same type is not allowed.");
		}		
	}

	/**
	 * Tries to find the target type.
	 * @param targetTypeFragment
	 * @return
	 * @throws IllegalArgumentException if found individual does not have type ServiceCase.
	 */
	private Json tryGetTargetType(String targetTypeFragment) {
		OWLNamedIndividual ind = individual("legacy:" + targetTypeFragment);
		Json type = OWL.toJSON(ontology(), ind);
		if (type != null && type.has("type") && type.at("type").asString().equalsIgnoreCase("ServiceCase")) {
			return type;
		} else {
			throw new IllegalArgumentException("Target type is not configured (" + targetTypeFragment + ").");
		}
	}
	
	/**
	 * Converts all existing answers into a multi-line string for hasDetails.
	 * @param properties
	 * @return
	 */
	private String answersAsString(Json properties) {
		String result = "";
		Json hasServiceAnswer = properties.at("hasServiceAnswer");
		if (hasServiceAnswer == null || hasServiceAnswer == Json.nil()) return result;
		if (hasServiceAnswer.isObject()) {
			hasServiceAnswer = Json.array(hasServiceAnswer);
		}
		for (Json cur : hasServiceAnswer.asJsonList()) {
			String q = questionLabel(cur);
			String a = answerString(cur);
			result += q;
			result += a;			
		}		
		return result;
	}

	private String questionLabel(Json a) {
		if (a.has("hasServiceField") && a.at("hasServiceField").has("label")) {
			return a.at("hasServiceField").at("label").asString() + "\r\n";
		} else {
			return "Unknown Question\r\n";
		}		
	}
	
	/**
	 * Converts an existing answer into a multi-line string representation.
	 * 
	 * @param a
	 * @return
	 */
	private String answerString(Json a) {
		if (a.has("hasAnswerValue") && a.at("hasAnswerValue").has("literal")) {
			return "  " + a.at("hasAnswerValue").at("literal") + "\r\n";
		} else if (a.has("hasAnswerObject")) {
			String result = "";
			Json objAns = a.at("hasAnswerObject");
			if (!objAns.isArray()) {
				objAns = Json.array(objAns);
			}
			for (Json ao : objAns.asJsonList()) {
				if (ao.has("label")) {
					result += "  " + ao.at("label") + "\r\n";
				} else {
					result += "  " + "N/A" + "\r\n";
				}
			}
			return result;
		} else {
			return "Answer not found\r\n"; 
		}
	}

	private void validateExistingActivities(Json properties, Json targetType) {
		List<String> existing = getExistingActivityFragments(properties);
		if (!existing.isEmpty()) {
			List<String> allowed = getAllowedActivityFragments(targetType);
			existing.removeAll(allowed);
		}
		if (!existing.isEmpty()) {
			String msg = "Target Type is not allowed, because the SR contains the following activities that are not configured: \r\n";
			msg += Arrays.toString(existing.toArray());
			throw new IllegalArgumentException(msg);
		}
	}

	private void validateExistingServiceCaseActors(Json properties, Json targetType) {
		List<String> existing = getExistingActorFragments(properties);
		if (!existing.isEmpty()) {
			List<String> allowed = getAllowedActorFragments(targetType);
			existing.removeAll(allowed);
		}
		if (!existing.isEmpty()) {
			String msg = "Target Type is not allowed, because the SR contains the following actors that are not configured: \r\n";
			msg += Arrays.toString(existing.toArray()) + "\r\n";
			throw new IllegalArgumentException(msg);
		}		
	}

	/**
	 * Exception if isDisabled or isDisabledCreate true
	 * @param targetType
	 */
	private void validateTargetTypeEnabled(Json targetType) {
		String type = targetType.at("label").asString();
		if (targetType.has("isDisabled") && targetType.at("isDisabled").asBoolean() == true) {
			throw new IllegalStateException("Target type " + type + " is disabled. Please select a different type.");
		}
		if (targetType.has("isDisabledCreate") && targetType.at("isDisabledCreate").asBoolean() == true) {
			throw new IllegalStateException("Target type " + type + " is disabled to be created. Please select a different type.");
		}
	}
	
	private List<String> getAllowedActorFragments(Json targetType) {		
		Json hasServiceActor = targetType.at("hasServiceActor");
		return jsonIndArrayToFragments(hasServiceActor);
	}
	
	private List<String> getAllowedActivityFragments(Json targetType) {		
		Json hasActivity = targetType.at("hasActivity");
		return jsonIndArrayToFragments(hasActivity);
	}
	/**
	 * Get Existing activity types, except Status change activities, if any.
	 * @param bo
	 * @return
	 */
	private List<String> getExistingActivityFragments(Json bo) {
		List<String> fragments = new ArrayList<>();
		Json hasServiceActivity = bo.at("hasServiceActivity");
		if (hasServiceActivity == null || hasServiceActivity == Json.nil()) return fragments;
		Json arr;
		if (!hasServiceActivity.isArray()) {
			arr = Json.array(hasServiceActivity);
		} else {
			arr = hasServiceActivity;
		}
		for (Json ind : arr.asJsonList()) {
			String f = fragmentStr(ind.at("hasActivity"));
			if (f != null && !"StatusChangeActivity".equalsIgnoreCase(f)) fragments.add(f);
		}
		return fragments;
	}
	
	private List<String> getExistingActorFragments(Json bo) {
		List<String> fragments = new ArrayList<>();
		Json hasServiceCaseActor = bo.at("hasServiceCaseActor");
		if (hasServiceCaseActor == null || hasServiceCaseActor == Json.nil()) return fragments;
		Json arr;
		if (!hasServiceCaseActor.isArray()) {
			arr = Json.array(hasServiceCaseActor);
		} else {
			arr = hasServiceCaseActor;
		}
		for (Json ind : arr.asJsonList()) {
			String f = fragmentStr(ind.at("hasServiceActor"));
			if (f != null) fragments.add(f);
		}
		return fragments;
	}

	
	
	// ----------------------------------------------------------------------------------------
	// HELPER METHODS
	//
	/**
	 * Replaces type portion of an iri such as http://www.miamidade.gov/bo/311OTHER/74205696#bo
	 * @param source
	 * @param targetType
	 * @return
	 */
	private String modifyBoIRI(String source, String targetTypeFragment) {
		if (targetTypeFragment == null || targetTypeFragment.isEmpty()) throw new IllegalArgumentException("targetTypeFragment null or empty");
		int typeStart = source.indexOf("/bo/");
		if (typeStart > 0) typeStart += "/bo/".length();
		int typeEnd = source.lastIndexOf("/");
		if (typeStart < 0 || typeEnd < 0) throw new IllegalArgumentException("Error: The identifying iri of this SR could not be parsed: " + source);
		String result = source.substring(0, typeStart) + targetTypeFragment.trim() + source.substring(typeEnd);
		return result;
	}
	
	private List<String> jsonIndArrayToFragments(Json indArrayOrObj) {
		List<String> fragments = new ArrayList<>();
		if (indArrayOrObj == null || indArrayOrObj == Json.nil()) return fragments;
		Json arr;
		if (!indArrayOrObj.isArray()) {
			arr = Json.array(indArrayOrObj);
		} else {
			arr = indArrayOrObj;
		}
		for (Json ind : arr.asJsonList()) {
			String f = fragmentStr(ind);
			if (ind != null) fragments.add(f);
		}
		return fragments;
	}
	
	private String fragmentStr(Json ind) {
		if (ind == null || ind == Json.nil()) return null;
		String iri;
		if (ind.isString()) {
			iri = ind.asString();
		} else if (ind.isObject()){
			iri = ind.at("iri").asString();
		} else {
			iri = null;
		}
		if (iri != null) iri = iri.substring(iri.lastIndexOf("#") + 1);
		return iri;
	}
}

/*******************************************************************************
 * Copyright 2017 Miami-Dade County
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
package org.sharegov.cirm.utils;

import java.util.Date;

import org.semanticweb.owlapi.model.IRI;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.owl.OWLSerialEntityCache;

import mjson.Json;

/**
 * SR Due date utilities for new and existing SRs base on type.<br>
 * Due date recalculation should occur for existing SRs if a type change occurs (e.g. approval type change or interface type change request)<br>
 * <br>
 * Existing SR json legacy properties are expected non legacy prefixed, such as when loaded for Ui.<br>
 * New SR json properties are expected legacy: prefixes, ready for storing.<br>
 * County properties are always expected fragment only.<br>
 * 
 * @author Thomas Hilpold
 *
 */
public class DueDateUtil {

	/**
	 * Sets the correct due date in the given newSr json based on sr hasDateCreated, srtype duration and srType workweek configuration.<br>
	 * If a due date already exists in newSr it will be overwritten.<br>
	 * <br>
	 * Examples:<br>
	 * type : "legacy:PW6102"<br>
	 * properties/legacy:hasDueDate: "2017-09-14T19:45:48.000-0000"<br>
	 * properties/hasDateCreated : "2017-06-16T19:48:43.000-0000"<br>
	 * @param newSr
	 */
	public void setDueDateNewSr(Json newSr) {
		setDueDateSrImpl(newSr, true);
	}	
	
	/**
	 * Sets the correct due date in the given existing Sr based on sr hasDateCreated, srtype duration and srType workweek configuration.<br>
	 * Existing SRs are loaded mostly without namespace qualified iri fragments.<br>
	 * <br>
	 * If a due date already exists in newSr it will be overwritten.<br>
	 * <br>
	 * Examples:<br>
	 * type: "PW6102" //no legacy prefix for existing.<br>
	 * properties/hasDueDate: "2017-09-14T19:45:48.000-0000" //no legacy: prefix<br>
	 * properties/hasDateCreated : "2017-06-16T19:48:43.000-0000"<br>
	 * <br>
	 * @param existingSr sr json without legacy: namespace prefixes.
	 */
	public void setDueDateExistingSr(Json existingSr) {
		setDueDateSrImpl(existingSr, false);
	}
	
	/**
	 * Sets the correct due date in the given existing Sr based on a custom start date (e.g. approvalDate), srtype duration and srType workweek configuration.<br>
	 * (Created date of SR is not used)
	 * Existing SRs are loaded mostly without namespace qualified iri fragments.<br>
	 * <br>
	 * If a due date already exists in newSr it will be overwritten.<br>
	 * <br>
	 * Examples:<br>
	 * type: "PW6102" //no legacy prefix for existing.<br>
	 * properties/hasDueDate: "2017-09-14T19:45:48.000-0000" //no legacy: prefix<br>
	 * <br>
	 * @param existingSr sr json without legacy: namespace prefixes.
	 * @param startDate the start date for adding type specific duration for due date calculation
	 */
	public void setDueDateExistingSr(Json existingSr, Date startDate) {
		setDueDateSrImpl(existingSr, false, startDate);
	}
	
	/**
	 * Internal setDueDate using created date set in sr.
	 * @param srJson
	 * @param newSrJson true if new with legacy: prefix, false if existing without prefixes.
	 */
	private void setDueDateSrImpl(Json srJson, boolean newSrJson) {
		if (newSrJson) {
			validateNewSr(srJson);
		} else {
			 validateExistingSr(srJson);
		}
		String dateCreatedStr = srJson.at("properties").at("hasDateCreated").asString();
		Date created = GenUtils.parseDate(dateCreatedStr);
		setDueDateSrImpl(srJson, newSrJson, created);
	}	
	
	private void setDueDateSrImpl(Json srJson, boolean newSrJson, Date startDate) {
		if (newSrJson) {
			validateNewSr(srJson);
		} else {
			 validateExistingSr(srJson);
		}
		String typeStr = srJson.at("type").asString();
		if (!(typeStr.startsWith("legacy:") || typeStr.contains("#"))) {
			typeStr =  "legacy:" + typeStr;
		}
		IRI typeIri = OWL.fullIri(typeStr);
		Json type = getSRType(typeIri);
		double durationDays = SrTypeJsonUtil.getDurationDays(type);
		if (durationDays <= 0) {			
			if (!newSrJson && hasDueDateExistingSr(srJson)) {
				//For exising SRs (type change), delete Due date if SRtype has 0 or no duration N/A.
				ThreadLocalStopwatch.now("SR Due date deleted of existing SR type: " + typeStr);
				srJson.at("properties").delAt("hasDueDate");
			}
			return;
		} else {
			boolean useWorkWeek = SrTypeJsonUtil.isDuration5DayBased(type);
			Date due = OWL.addDaysToDate(startDate, (float)durationDays, useWorkWeek);
			String dueStr = GenUtils.formatDate(due);
			if (newSrJson) {
				srJson.at("properties").set("legacy:hasDueDate", dueStr);
			} else {
				srJson.at("properties").set("hasDueDate", dueStr);
			}
			ThreadLocalStopwatch.now("SR Due date set: " + due + " (=" + startDate + " + " + durationDays + " ww5:" + useWorkWeek + " type: " + typeStr + ")");
		}
	}	

	
	/**
	 * Determines if the newSr json has a legacy prefixed Due date property.
	 * @param newSr
	 * @return
	 */
	public boolean hasDueDateNewSr(Json newSr) {
		validateNewSr(newSr);
		return newSr.at("properties").has("legacy:hasDueDate");		
	}
	
	/**
	 * Determines if the existingSr json has a non prefixed Due date property.
	 * @param newSr
	 * @return
	 */
	public boolean hasDueDateExistingSr(Json exisingSr) {
		validateExistingSr(exisingSr);
		return exisingSr.at("properties").has("hasDueDate");		
	}
	
	/**
	 * Cached retrieval of SR type Json.
	 * 
	 * @param srTypeIri
	 * @return
	 */
	private Json getSRType(IRI srTypeIri) {		
		OWLSerialEntityCache jsonEntities = Refs.owlJsonCache.resolve();
		Json type = jsonEntities.individual(srTypeIri).resolve();
		if (type != null && type.has("type") && type.at("type").asString().equalsIgnoreCase("ServiceCase")) {
			return type;
		} else {
			throw new IllegalArgumentException("SRType is not configured (" + srTypeIri + ").");
		}
	}
	
	/**
	 * Validates core properties including hasDateCreated with valid date of SR.
	 * 
	 * @param newSr
	 * @throws IllegalArgumentException
	 */
	private void validateNewSr(Json newSr) {
		if (newSr == null || newSr.isNull() || !newSr.isObject()) {
			throw new IllegalArgumentException("newSr is null or not an object.");
		}
		if (!newSr.has("type")) {
			throw new IllegalArgumentException("newSr does not have /type.");
		}
		if (!newSr.has("properties")) {
			throw new IllegalArgumentException("newSr does not have a /properties.");
		}
		if (!newSr.at("properties").isObject()) {
			throw new IllegalArgumentException("newSr properties are not an object.");
		}
		if (!newSr.at("properties").has("hasDateCreated") || !newSr.at("properties").at("hasDateCreated").isString()) {
			throw new IllegalArgumentException("hasDateCreated not available or not a String.");
		}
	}

	/**
	 * Validates core properties including hasDateCreated with valid date of SR.
	 * 
	 * @param existingSr an SR without namespace qualified properties.
	 * @throws IllegalArgumentException
	 */	
	private void validateExistingSr(Json existingSr) {
		//Currently we can use newSR validation variant because all properties checked are county properties and
		//therefore no legacy: prefix is required.
		validateNewSr(existingSr);		
	}
}

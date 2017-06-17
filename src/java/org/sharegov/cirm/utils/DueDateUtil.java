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
 * SR Due date utilities for new SRs base on type. 
 * 
 * @author Thomas Hilpold
 *
 */
public class DueDateUtil {

	/**
	 * Sets the correct due date in the given newSr json based on sr hasDateCreated, srtype duration and srType workweek configuration.
	 * If a due date already exists in newSr it will be overwritten.
	 * 
	 * Examples:
	 * properties/legacy:hasDueDate: "2017-09-14T19:45:48.000-0000"
	 * properties/hasDateCreated : "2017-06-16T19:48:43.000-0000"
	 * @param newSr
	 */
	public void setDueDateNewSr(Json newSr) {
		validateNewSr(newSr);
		String dateCreatedStr = newSr.at("properties").at("hasDateCreated").asString();
		String typeStr = newSr.at("type").asString();
		IRI typeIri = OWL.fullIri(typeStr);
		Json type = getSRType(typeIri);
		double durationDays = SrTypeJsonUtil.getDurationDays(type);
		if (durationDays <= 0) {
			//Does not need due date (but may have it TODO maybe remove)
			return;
		} else {
			boolean useWorkWeek = SrTypeJsonUtil.isDuration5DayBased(type);
			Date created = GenUtils.parseDate(dateCreatedStr);
			Date due = OWL.addDaysToDate(created, (float)durationDays, useWorkWeek);
			String dueStr = GenUtils.formatDate(due);
			newSr.at("properties").set("legacy:hasDueDate", dueStr);
			ThreadLocalStopwatch.now("SR Due date set: " + due + " (=" + created + " + " + durationDays + " ww5:" + useWorkWeek + ")");
		}
	}	
	
	/**
	 * Determines if the newSr json has a Due date property.
	 * @param newSr
	 * @return
	 */
	public boolean hasDueDate(Json newSr) {
		validateNewSr(newSr);
		return newSr.at("properties").has("legacy:hasDueDate");		
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
}

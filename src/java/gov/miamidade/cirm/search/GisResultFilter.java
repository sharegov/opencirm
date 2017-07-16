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
package gov.miamidade.cirm.search;

import java.util.HashSet;
import mjson.*;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.utils.Mapping;

/**
 * Even though this is "just" a filter, it can be configured to remove associated
 * SR types from 'ontology' attributes of a result if they are not available for
 * the address. Otherwise, it will return true if at least on SR type is available.
 * 
 * @author unknown, Thomas Hilpold
 */
public class GisResultFilter implements Mapping<Json, Boolean>
{
	private boolean removeUnavailable;
	private Json propertyInfo;
	private Json gisInfo;
	private String[] layers;
	
	public Json getGisInfo()
	{
		if (gisInfo != null)
			return gisInfo;
		
		final Json xxJ =  propertyInfo.at("coordinates").at("x");
		final Json yyJ = propertyInfo.at("coordinates").at("y");
		final double xCoordinate;
		final double yCoordinate;
		if (xxJ.isString()) {
			xCoordinate = Double.parseDouble(xxJ.asString());
		} else {
			xCoordinate = xxJ.asDouble();
		}
		if (yyJ.isString()) {
			yCoordinate = Double.parseDouble(yyJ.asString());
		} else {
			yCoordinate = yyJ.asDouble();
		}
		Json result = Refs.gisClient.resolve().getLocationInfo(xCoordinate, yCoordinate, layers, 3, 500);
		//Save in instance variable
		gisInfo = result;
		return result;
	}
	
	public GisResultFilter(Json propertyInfo, boolean removeUnavailable)
	{
		this.propertyInfo = propertyInfo;
		this.removeUnavailable = removeUnavailable;
	}
	
	public GisResultFilter(Json propertyInfo, boolean removeUnavailable, String[] layers)
	{
		this.propertyInfo = propertyInfo;
		this.removeUnavailable = removeUnavailable;
		this.layers = layers;
	}
	
	public Boolean eval(Json result)
	{
		// The following logic assumes there's only one SR associated with a topic (or that
		// if one is not available then any other associations aren't available either). This is
		// from stated requirements that a topic describes at most 1 SR. This
		// way there's no need to distinguish b/w		
		boolean b = true;
		boolean hasAvailable = false;
		if (!result.has("ontology"))
			return b;
		OWLOntology O = OWL.ontology();
		OWLClass srClass = OWL.owlClass("http://www.miamidade.gov/cirm/legacy#ServiceCase");
		HashSet<Json> toRemove = new HashSet<Json>();
		for (Json j : result.at("ontology").asJsonList())
		{
			OWLNamedIndividual ind = OWL.individual(j.asString());
			if (!ind.getTypes(O.getImportsClosure()).contains(srClass))
				continue;
			String typeCode = ind.getIRI().getFragment(); //((OWLLiteral)anns.iterator().next().getValue()).getLiteral();
			if (!GisServiceMapping.get().isAvailable(typeCode, getGisInfo()))
			{
				b = false;
				if (removeUnavailable)
					toRemove.add(j);
			}
			else
				hasAvailable = true;
		}
		for (Json j : toRemove) result.at("ontology").remove(j);
		return b || hasAvailable;
	}
}

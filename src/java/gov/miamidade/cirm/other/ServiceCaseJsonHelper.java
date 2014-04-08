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

import gov.miamidade.cirm.GisClient;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import mjson.Json;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.utils.JsonUtil;
import org.sharegov.cirm.utils.Mapping;

/**
 * <p>
 * This holds some utility methods (i.e. hacks basically) that deal with JSON coming from
 * departmental integration code. An update from an external system may not contain the full,
 * latest version of the case, so some work has to be put into properly merging the information
 * from the update (could be a new activity or a status update, or an address change).
 * </p>
 * 
 * <p>
 * Even when a new case is submitted, the JSON may be missing some properties that are otherwise
 * expected and that the UI would normally set.
 * </p>
 * 
 * <p>
 * Another situation where we need to align identifiers is mapping the CSR legacy codes to
 * our ontology IRIs. In the ontology, a CSR code can't be directly translated into an IRI 
 * because it may not be unique. Therefore we manage it as a property ('hasLegacyCode'). So
 * we sometimes need to find the OWL individual corresponding to a CSR code that we get
 * from the legacy interface. 
 * </p>
 * 
 * @author boris
 *
 */
public class ServiceCaseJsonHelper
{	    
    private static Set<String> ignorePrefixFor = OWL.set("PW_PWSTATUS");
 
    public static Json removeAnswerFieldByIri(Json answers, String fieldIri)
    {
    	int i = findAnswerByField(answers, fieldIri);
    	if (i > -1)
    		answers.delAt(i);
    	return answers;
    }
    
    public static int findAnswerByField(Json answers, String fieldIri)
    {
    	for (int i = 0; i < answers.asJsonList().size(); i++)
    	{
    		Json field = answers.at(i).at("legacy:hasServiceField");
    		if (field == null)
    			field = answers.at(i).at("hasServiceField");    		
    		if (field == null)
    			continue;
    		else if (field.is("iri", fieldIri))
    			return i;
    	}
    	return -1;
    }
    
    /**
     * Entities coming from departmental interfaces (MDCEAI) are not annotated
     * with their corresponding ontology IRIs. They only rely on CSR "codes" 
     * which we have ported as a hasLegacyCode property. The IRI in the ontology
     * must be unique is therefore constructed by using the SR type as a prefix.
     * So here, we try to find all such entities within the JSON structure and
     * replace with the correct IRIs. The prefix will be used depending on whether
     * an OWL individual exists identified solely by hasLegacyCode, or the 
     * code itself has been listed in 'ignorePrefixFor'. 
     */
    public static Json insertIriFromCode(Json x, String prefix)
    {
    	if (x == null)
    		return null;
    	if (x.has("hasLegacyCode"))
		{
        	String lcode = x.at("hasLegacyCode").asString();
        	OWLOntology O = Refs.legacyOntology.resolve();
        	if (O.isDeclared(OWL.individual("legacy:" + lcode)))
				return x.set("iri", "legacy:" + lcode).delAt("hasLegacyCode");
        	else if (!ignorePrefixFor.contains(lcode))
        		return x.set("iri", "legacy:" + prefix + lcode).delAt("hasLegacyCode");
        	else
        		return x.set("iri", "legacy:" + lcode).delAt("hasLegacyCode");
		}
		else if (x.has("iri") && !x.at("iri").asString().startsWith("legacy:") &&
				!x.at("iri").asString().startsWith("http"))
			return x.set("iri", "legacy:" + x.at("iri").asString());    	
		else
			return x;
    }
    
    /**
     * Apply the {@link ServiceCaseJsonHelper#insertIriFromCode(Json, String)} method
     * to the <code>prop</code> property of all elements of the JSON array <code>L</code>. 
     */
    public static void insertIriFromCode(Json L, String prop, String prefix)
    {
    	if (L == null || !L.isArray())
    		return;
    	for (Json x : L.asJsonList())
    	{
    		if (x.has(prop))
    			insertIriFromCode(x.at(prop), prefix);
    	}
    }

    /**
     * Check if a property (data of object) with the given IRI is
     * in the CiRM ontology.
     * @param iri
     * @return
     */
    private static boolean isCiRMProperty(IRI iri)
    {        
    	OWLOntology O = OWL.ontology();    	
    	return O.isDeclared(OWL.dataProperty(iri), true) || 
    		   O.isDeclared(OWL.objectProperty(iri), true);    					
    }
    
    private static boolean isCiRMClass(IRI iri)
    {		
    	OWLOntology O = OWL.ontology();    	    	
		return !O.getAxioms(OWL.owlClass(iri)).isEmpty()
			  || O.isDeclared(OWL.owlClass(iri));    	
    }

    // This is probably the same as BOntology, so not sure this step is needed at all anymore
	final static Set<String> toignore = OWL.set("hasLegacyCode", "hasLegacyId", "hasUpdatedDate",
	        "label",  "hasChoiceValueList", "hasDataType", "hasOrderBy",
	        "description2", "description3", "description4", "description5", "description6", 
	        "comment", "participantEntityTable", "hasBusinessCodes");
    
    /**
     * Remove a set of properties that are more part of the SR type than an SR, but
     * find their way into the SR's JSON when coming from MDCEAI or whatever....
     * 
     * @param data
     * @return
     */
    public static Json cleanUpProperties(Json data)
    {
    	return JsonUtil.apply(data, new Mapping<Json, Json> ()
    	{
    		public Json eval(Json j)
    		{
    			if (j.isObject())
    				for (String s : toignore)
    					j.delAt(s);
    			return j;
    		}
    	});
    }
    
    /**
     * This method prepares a JSON structure representing a service case for converting
     * into a business ontology so that it can be saved in the database. Depending on
     * where the JSON comes from, read from the DB or obtained from a departmental integration
     * module, it will contain properties that correspond to OWL properties but without
     * the IRIs. This method
     * will take care of assigning correct IRIs to the properties (essentially figuring out
     * where the cirm "legacy:" prefix should be used. There are also some special cases where the 
     * entity has be to obtained via
     * some other identifying OWL property (e.g. the USPS_Suffix of a street type). 
     * @param x
     */
    public static void assignIris(Json x)
    {
    	if (x.isObject())
    	{
    		Set<String> S = new HashSet<String>();
    		for (Map.Entry<String, Json> e : x.asJsonMap().entrySet())
    		{

    			if ("type".equals(e.getKey()) && 
    				!e.getValue().asString().startsWith("http://") &&
    				isCiRMClass(OWL.fullIri("legacy:" + e.getValue().asString())))
    			{
    				x.set("type", "legacy:" + x.at("type").asString());
    				continue;
    			}
    			IRI iri = OWL.fullIri("legacy:" + e.getKey());
    			if (isCiRMProperty(iri))
   					S.add(e.getKey());
    			assignIris(e.getValue());
    		}
    		for (String s : S)
    			x.set("legacy:" + s, x.atDel(s));
    		if (x.has("USPS_Suffix"))
    		{
    			Set<OWLNamedIndividual> stype = OWL.queryIndividuals(
    					"Street_Type and USPS_Suffix value \"" + x.at("USPS_Suffix").asString() + "\"",
    					OWL.ontology());
    			if (!stype.isEmpty())
    				x.set("iri", stype.iterator().next().getIRI().toString());
    			else if (x.up().at("hasStreetType") == x)
    				x.delAt("USPS_Suffix");
    		}
    		else if (x.has("USPS_Abbreviation"))
    		{
    			Set<OWLNamedIndividual> sdir = OWL.queryIndividuals(
    					"Direction and USPS_Abbreviation value \"" + x.at("USPS_Abbreviation").asString() + "\"",
    					OWL.ontology());
    			if (!sdir.isEmpty())
    				x.set("iri", sdir.iterator().next().getIRI().toString());    			
    			else if (x.up().at("Street_Direction") == x)
    				x.up().delAt("Street_Direction");
    		}
    	}
    	else if (x.isArray())
    	{
    		for (Json el : x.asJsonList())
    			assignIris(el);
    	}
    }

    /**
     * A department will send the label of a choice list selection instead of an OWL IRI as a value.
     * So we need to find the IRI from the label. 
     * @param fields
     */
	public static void replaceAnswerLabelsWithValues(Json fields)
	{
		if (fields == null)
			return;		
		HashSet<Integer> toremove = new HashSet<Integer>();
		for (int i = 0; i < fields.asJsonList().size(); i++)
		{
			Json ans = fields.at(i);
			if (!ans.has("legacy:hasServiceField"))
				continue;
			Json field = OWL.toJSON(OWL.individual(
					ans.at("legacy:hasServiceField").at("iri").asString()));
			if (!field.has("hasChoiceValueList") || !ans.has("legacy:hasAnswerValue"))
				continue;
			OWL.resolveIris(field, null);			
			String ansValue = "";
			if (ans.at("legacy:hasAnswerValue").isString())
				ansValue = ans.at("legacy:hasAnswerValue").asString();
			else if (ans.at("legacy:hasAnswerValue").isObject())
				ansValue = ans.at("legacy:hasAnswerValue").at("literal").asString();
			else
				continue;
			for (Json choice : field.at("hasChoiceValueList").at("hasChoiceValue").asJsonList())
			{
				OWLNamedIndividual ansIndividual = OWL.individual(choice.at("iri").asString());
				String label = OWL.getEntityLabel(ansIndividual);
				if  (label != null && label.equalsIgnoreCase(ansValue))
				{
					ans.set("legacy:hasAnswerObject", choice.dup());
					ans.delAt("legacy:hasAnswerValue");
					break;
				}
			}
//			if (!ans.has("legacy:hasAnswerObject"))
//				if (ansValue.length() == 0)
//					toremove.add(i);
//				else
//				{
//					new RuntimeException("Could not find choice object for field " + 
//							field.at("iri") + " from label " + ansValue).printStackTrace();
//					toremove.add(i);
//				}
		}
//		int removed = 0;
//		for (Integer i : toremove)
//			fields.delAt(i - (removed++));
	}

	// Follows the merge function that consolidates an update coming from an external system
	// and the latest version of a case in the CiRM database. 
	
	// The 'dest' parameter is the result of the merge. The 'src' parameter is the new
	// data coming from elsewhere. The method assumes both src and dest are objects.
	// All properties of src are copied into dest, possibly overwriting existing properties.
	// When a property values is an object, it is merged recursively. When a property value
	// is an array, then it's a bit more complicated: every element that's in 'src' but not 
	// in 'dest' is put into 'dest' and when an element is found in both places, its 'src' version 
	// is used. To determine whether we are dealing with the same element, comparators are used.
	// And the comparators are different depending on the property whose value is the array.
	// The comparators are below...
 
	
    private static final Comparator<Json> defaultJsonCompare = new Comparator<Json>() 
    {
    	public int compare(Json left, Json right)
    	{
    		if (left.equals(right)) return 0;
    		else return left.toString().compareTo(right.toString());
    	}
    };
    private static final Comparator<Json> serviceAnswerCompare = new Comparator<Json>() 
    {
    	public int compare(Json left, Json right)
    	{
    		IRI lid = OWL.fullIri(left.at("legacy:hasServiceField").at("iri").asString());
    		IRI rid = OWL.fullIri(right.at("legacy:hasServiceField").at("iri").asString());
    		return lid.toString().compareTo(rid.toString());
    	}
    };
    
    private static final HashMap<String, Comparator<Json>> arrayElementComparators = new HashMap<String, Comparator<Json>>();
    static 
    {
    	arrayElementComparators.put("hasServiceAnswer", serviceAnswerCompare);
    	arrayElementComparators.put("legacy:hasServiceAnswer", serviceAnswerCompare);
    }
    
    public static void mergeInto(Json src, Json dest)
    {
    	for (Map.Entry<String, Json> e : src.asJsonMap().entrySet())
    	{
    		if (e.getValue().isNull())
    			dest.delAt(e.getKey());
    		else if (!dest.has(e.getKey()))
    			dest.set(e.getKey(), e.getValue());
    		else if (e.getValue().isObject())
    			mergeInto(e.getValue(), dest.at(e.getKey()));
    		else if (e.getValue().isArray())
    		{
    			Comparator<Json> comp = arrayElementComparators.get(e.getKey());
    			if (comp == null)
    				comp = defaultJsonCompare;
    			Json A = dest.at(e.getKey());
    			if (A == null)
    				dest.set(e.getKey(), e.getValue());
    			else if (!A.isArray())
    				throw new RuntimeException("Attempt to merge array element into a scalar or an object.");
    			else
    			{
    				for (Json srcel : e.getValue().asJsonList())
    				{
    					int idx = -1;
    					for (int i = 0; i < A.asJsonList().size() && idx < 0; i++)
    						if (comp.compare(A.at(i), srcel) == 0)
    							idx = i;
    					if (idx < 0)
    						A.add(srcel);
    					else
    						A.asJsonList().set(idx, srcel);
    				}
    			}
    		}
    		else
    			dest.set(e.getKey(), e.getValue());
    	}
    }
	
    public static Json findUSStateObject(String abbreviation)
    {
    	for (OWLNamedIndividual ind : OWL.queryIndividuals("State__U.S._"))
    		if (OWL.dataProperties(ind, "USPS_Abbrevation").contains(OWL.literal(abbreviation)))
    			return Json.object().set("iri", ind.getIRI().toString());
    	return Json.nil();
    }
    
    public static Json findCityObject(String name)
    {
    	for (OWLNamedIndividual ind : OWL.queryIndividuals("City"))
    		if (OWL.dataProperties(ind, "Name").contains(OWL.literal(name)) || 
    			OWL.dataProperties(ind, "Alias").contains(OWL.literal(name)))
    			return Json.object().set("iri", ind.getIRI().toString());
    	return Json.nil();
    }
    
    public static Json reverseGeoCode(double xcoord, double ycoord)
    {
		Json gis = GisClient.getAddressFromCoordinates(xcoord, ycoord, 3, 1000*30); 
		if (gis.isArray())
			gis = gis.at(0);
		else if (gis.isNull() || gis.asJsonMap().isEmpty() || !gis.has("parsedAddress"))
			return Json.nil();
		Json addr =  Json.object();
		Json parsed = gis.at("parsedAddress");
		Set<OWLNamedIndividual> S = OWL.queryIndividuals("Place and (Name value \"" + 
				gis.at("municipality").asString() +
				"\" or Alias value \"" + gis.at("municipality").asString() + "\")");
		if (S.isEmpty())
			throw new IllegalArgumentException("Cannot find municipality in ontology " + gis.at("municipality"));
		String streetAddress = gis.at("address").asString().split(",")[0];
		addr.set("Street_Number", parsed.at("House"))
			.set("Zip_Code", parsed.at("zip"))
			// We shouldn't be hard-coding the state here as there may be out of state cases, though unlikely when coming from departments...
			.set("Street_Address_State", 
				  Json.object("iri", "http://www.miamidade.gov/ontology#Florida"))
			.set("Street_Address_City", Json.object("iri", S.iterator().next().getIRI().toString()))
			.set("Street_Name", parsed.at("StreetName"))
			.set("fullAddress", streetAddress);
		if (parsed.has("PreDir") && !parsed.is("PreDir", ""))
			addr.set("Street_Direction", Json.object("USPS_Abbreviation", parsed.at("PreDir").asString()));
		if (parsed.has("SufType") && !parsed.is("SufType", ""))
			addr.set("hasStreetType", Json.object("USPS_Suffix", parsed.at("SufType").asString()));
		return addr;		
    }
}

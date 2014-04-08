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

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import mjson.Json;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.utils.GenUtils;

import static org.sharegov.cirm.OWL.*;

public class AddressSearchService
{
	String url = null;
	
	public static Map<String, IRI> 	citiesToAgency = new HashMap<String, IRI>();
	static
	{
    	citiesToAgency.put("AVENTURA",IRI.create("http://www.miamidade.gov/ontology#City_of_Aventura"));
    	citiesToAgency.put("BAL HARBOUR",IRI.create("http://www.miamidade.gov/ontology#Bal_Harbour_Village"));
    	citiesToAgency.put("BAY HARBOR ISLANDS",IRI.create("http://www.miamidade.gov/ontology#Town_of_Bay_Harbor_Islands"));
    	citiesToAgency.put("BISCAYNE PARK",IRI.create("http://www.miamidade.gov/ontology#Village_of_Biscayne_Park"));
    	citiesToAgency.put("CORAL GABLES",IRI.create("http://www.miamidade.gov/ontology#City_of_Coral_Gables"));
    	citiesToAgency.put("CUTLER BAY",IRI.create("http://www.miamidade.gov/ontology#Town_of_Cutler_Bay"));
    	citiesToAgency.put("DORAL",IRI.create("http://www.miamidade.gov/ontology#City_of_Doral"));
    	citiesToAgency.put("EL PORTAL",IRI.create("http://www.miamidade.gov/ontology#Village_of_El_Portal"));
    	citiesToAgency.put("FLORIDA CITY",IRI.create("http://www.miamidade.gov/ontology#City_of_Florida_City"));
    	citiesToAgency.put("GOLDEN BEACH",IRI.create("http://www.miamidade.gov/ontology#Town_of_Golden_Beach"));
    	citiesToAgency.put("HIALEAH",IRI.create("http://www.miamidade.gov/ontology#City_of_Hialeah"));
    	citiesToAgency.put("HIALEAH GARDENS",IRI.create("http://www.miamidade.gov/ontology#City_of_Hialeah_Gardens"));
    	citiesToAgency.put("HOMESTEAD",IRI.create("http://www.miamidade.gov/ontology#City_of_Homestead"));
    	citiesToAgency.put("INDIAN CREEK VILLAGE",IRI.create("http://www.miamidade.gov/ontology#Indian_Creek_Village"));
    	citiesToAgency.put("ISLANDIA",IRI.create("http://www.miamidade.gov/ontology#City_of_Islandia"));
    	citiesToAgency.put("KEY BISCAYNE",IRI.create("http://www.miamidade.gov/ontology#Village_of_Key_Biscayne"));
    	citiesToAgency.put("MEDLEY",IRI.create("http://www.miamidade.gov/ontology#Town_of_Medley"));
    	citiesToAgency.put("MIAMI",IRI.create("http://www.miamidade.gov/ontology#City_of_Miami"));
    	citiesToAgency.put("MIAMI BEACH",IRI.create("http://www.miamidade.gov/ontology#City_of_Miami_Beach"));
    	citiesToAgency.put("MIAMI GARDENS",IRI.create("http://www.miamidade.gov/ontology#City_of_Miami_Gardens"));
    	citiesToAgency.put("MIAMI LAKES",IRI.create("http://www.miamidade.gov/ontology#City_of_Miami_Lakes"));
    	citiesToAgency.put("MIAMI SHORES",IRI.create("http://www.miamidade.gov/ontology#City_of_Miami_Shores"));
    	citiesToAgency.put("MIAMI SPRINGS",IRI.create("http://www.miamidade.gov/ontology#City_of_Miami_Springs"));
    	citiesToAgency.put("NORTH BAY VILLAGE",IRI.create("http://www.miamidade.gov/ontology#North_Bay_Village"));
    	citiesToAgency.put("NORTH MIAMI",IRI.create("http://www.miamidade.gov/ontology#City_of_North_Miami"));
    	citiesToAgency.put("NORTH MIAMI BEACH",IRI.create("http://www.miamidade.gov/ontology#City_of_North_Miami_Beach"));
    	citiesToAgency.put("OPA-LOCKA",IRI.create("http://www.miamidade.gov/ontology#City_of_Opa-Locka"));
    	citiesToAgency.put("PALMETTO BAY",IRI.create("http://www.miamidade.gov/ontology#City_of_Palmetto_Bay"));
    	citiesToAgency.put("PINECREST",IRI.create("http://www.miamidade.gov/ontology#City_of_Pinecrest"));
    	citiesToAgency.put("SOUTH MIAMI",IRI.create("http://www.miamidade.gov/ontology#City_of_South_Miami"));
    	citiesToAgency.put("SUNNY ISLES BEACH",IRI.create("http://www.miamidade.gov/ontology#City_of_Sunny_Isles_Beach"));
    	citiesToAgency.put("SURFSIDE",IRI.create("http://www.miamidade.gov/ontology#City_of_Surfside"));
    	citiesToAgency.put("SWEETWATER",IRI.create("http://www.miamidade.gov/ontology#City_of_Sweetwater"));
    	citiesToAgency.put("UNINCORPORATED MIAMI-DADE",IRI.create("http://www.miamidade.gov/ontology#Miami-Dade_County"));
    	citiesToAgency.put("VIRGINIA GARDENS",IRI.create("http://www.miamidade.gov/ontology#Village_of_Virginia_Gardens"));
    	citiesToAgency.put("WEST MIAMI",IRI.create("http://www.miamidade.gov/ontology#City_of_West_Miami"));
    }
	
	private void init()
	{
		if (url != null)
			return;
		OWLNamedIndividual addressService = Refs.configSet.resolve().get("GisConfig");
		OWLLiteral lurl = OWL.dataProperty(addressService, "hasUrl");
		if (lurl != null)
			url = lurl.getLiteral();
		else
			throw new RuntimeException("Couldn't find AddressSearchService.hasUrl in the ontology");
	}
	
	public Json findPropertyInfo(String street, String zip)
	{
		init();
		Json result = GenUtils.httpGetJson(url + "/candidates?street=" + 
				URLEncoder.encode(street) + 
				"&zip=" + zip);
		if (!result.is("ok", true))
			throw new RuntimeException("While looking property info for " + street + 
					"," + zip + ": " + result.at("error").asString());
		result = result.at("candidates");
		if (result.isArray())
			if (result.asJsonList().isEmpty())
				return null;
			else
				return result.asJsonList().isEmpty() ? Json.nil() : result.at(0);
		else
			return result;
	}
	
	public static void main(String [] argv)
	{
		System.out.println(new AddressSearchService().findPropertyInfo("8720 sw 41 st","33165"));
	}	
}

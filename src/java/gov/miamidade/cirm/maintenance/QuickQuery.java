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
package gov.miamidade.cirm.maintenance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;

import javax.sql.rowset.CachedRowSet;

import mjson.Json;


import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.legacy.MessageManager;
import org.sharegov.cirm.owl.CachedReasoner;

import com.clarkparsia.pellet.el.CachedSubsumptionComparator;


/**
 * Queries the reasoner and prints properties of resulting objects in a CSV format that's easy to process.
 * Just set DLQUERY, PROPS (dotted notation ok!) and VAR(if you use $$VAR$$ in DLQuery).
 * 	 
 * @author Thomas Hilpold
 *
 */
public class QuickQuery {
	//public static String DLQUERY = "legacy:hasLegacyInterface value legacy:MD-CMS";
	
	//1 
	//public static String DLQUERY = "legacy:MessageTemplate and inverse legacy:hasEmailTemplate some (legacy:Activity and inverse legacy:hasActivity value legacy:$$VAR$$)";
	//public static String[] PROPS = new String[] { "iri" };//, "hasSubject", "hasTo" };
	//2 
	//public static String DLQUERY = "legacy:MessageTemplate and inverse legacy:hasEmailTemplate value legacy:$$VAR$$";
	//public static String[] PROPS = new String[] { "iri","label", "hasEmailTemplate.iri", "hasEmailTemplate.hasHighPriority" };
	//3 
	//public static String DLQUERY = "(legacy:Activity and inverse legacy:hasActivity value legacy:$$VAR$$ and legacy:hasEmailTemplate some owl:Thing)";
	//public static String[] PROPS = new String[] { "iri","label", "hasEmailTemplate.iri", "hasEmailTemplate.hasHighPriority" };
	//Previous queries:
	//public static String DLQUERY = "hasLegacyInterface value MD-CMS";
	//4 Message Templates for Activities for Standard text on SR creation
	//	public static String DLQUERY = "legacy:ServiceCase and { $$VAR$$ }";
	//	public static String[] PROPS = new String[] { "iri","label", "providedBy" };

	//5 Message Templates for Activities for Standard text on SR creation
	//public static String DLQUERY = "legacy:Activity and inverse legacy:hasActivity value legacy:$$VAR$$ and legacy:hasEmailTemplate some owl:Thing";
	//public static String[] PROPS = new String[] { "iri","label", "isAutoCreate", "hasEmailTemplate.iri" };

	//6 For Syed
	//public static String DLQUERY = "mdc:Division_County and inverse mdc:Divisions value mdc:Public_Works_Waste_Management ";
	//public static String[] PROPS = new String[] { "iri","label", "providedBy" };
	//7 Find AP to template
//	public static String DLQUERY = "mdc:AccessPolicy and mdc:hasObject some (legacy:ServiceCase and  legacy:hasActivity some (legacy:Activity and legacy:hasEmailTemplate value legacy:PERSONEL_SWMEMAI4_GENASSIG ))";
//	public static String[] PROPS = new String[] { "iri","label" };
	
	//8 Solidwaste SRs
//	public static String DLQUERY = "{legacy:$$VAR$$}";
//	public static String[] PROPS = new String[] { "label" , "isDisabled" /*, "providedBy" */ };
	
	//9 RER SRs
//	public static String DLQUERY = "legacy:ServiceCase and inverse mdc:hasObject value $$VAR$$ ";
//	public static String[] PROPS = new String[] { "iri", "label" , "isDisabled" /*, "providedBy" */ };

	
	//10 COM SRs
	//public static String DLQUERY = "legacy:hasJurisdictionCode value \"COM\"";
	//public static String[] PROPS = new String[] { "iri", "label" , "isDisabledCreate", "providedBy.type", "providedBy.iri" };

//	public static String[] VAR9 = new String[]
//	{
//		"legacy:RER_SR_ACCESS",
//		"legacy:RER_SR_PRIV_ACCESS"
//	};

	//11 Departments & Divisions
	//public static String DLQUERY = "mdc:Department_County";
	//public static String[] PROPS = new String[] { "iri", "hasDivision.iri", "hasDivision.Dept_Code"};

	//12 311DUMP email templates Departments
	//public static String DLQUERY = "(legacy:Activity and inverse legacy:hasActivity value legacy:311DUMP and legacy:hasEmailTemplate some owl:Thing)";
	//public static String[] PROPS = new String[] { "iri","label", "hasEmailTemplate.iri", "hasEmailTemplate.hasHighPriority" };

	//13 Servicecase Access policies
		//public static String DLQUERY = "owl:Thing and legacy:hasAccessPolicy some (owl:Thing)";
		//public static String[] PROPS = new String[] { "iri", "hasAccessPolicy.iri", "hasAccessPolicy.hasObject.iri", "hasAccessPolicy.hasObject.label", "hasAccessPolicy.hasAction.iri" };

	//14 Servicecase MessageTemplates for COM activites
//	public static String DLQUERY = "legacy:ServiceCase and (legacy:hasJurisdictionCode value \"MD\") and ((legacy:hasActivity some (owl:Thing and legacy:hasEmailTemplate some owl:Thing)) "
//			+ " or legacy:hasEmailTemplate some owl:Thing)";
//	public static String[] PROPS = new String[] { "iri", 
//		"label", 
//		"hasEmailTemplate.iri", 
//		"hasEmailTemplate.hasTo", 
//		"hasEmailTemplate.hasCC", 
//		"hasEmailTemplate.hasSubject",
//		//"hasEmailTemplate.hasLegacyBody",
//		"hasActivity.iri", 
//		"hasActivity.label", 
//		"hasActivity.hasEmailTemplate.iri", 
//		"hasActivity.hasEmailTemplate.hasTo", 
//		"hasActivity.hasEmailTemplate.hasCC", 
//		"hasActivity.hasEmailTemplate.hasSubject",
//		//"hasActivity.hasEmailTemplate.hasBody" 
//		};
	//15 WS interface types and all activity triggers (close case)
//	public static String DLQUERY = "{legacy:$$VAR$$}";
//	public static String[] PROPS = new String[] { "iri", 
//		"label", 
//		"hasActivity.iri", 
//		"hasActivity.label", 
//		"hasActivity.hasAllowableOutcome.iri",
//		"hasActivity.hasAllowableOutcome.label" 
//		};
	
	

//	public static String DLQUERY = "legacy:ServiceCase and legacy:hasJurisdictionCode value \"MD\" ";
//	public static String[] PROPS = new String[] { "iri", "providedBy"}; 
	
//	public static String DLQUERY = "legacy:ServiceCase and { legacy:$$VAR$$ }";
//	
//	public static String[] PROPS = new String[] { "iri", "label", "hasActivity.hasEmailTemplate.iri", "hasActivity.hasEmailTemplate.hasTo",  "hasActivity.hasEmailTemplate.hasSubject"};
	
	public static boolean PRINT_JSON = false;
	
	public static boolean LIST_MESSAGEVARIABLES = true; //for templates ending with  Body, Subject
	public static boolean COLLECT_MESSAGEVARIABLES = true; //for templates ending with  Body, Subject
	
	
	//16 Servicecase MessageTemplates for COM activites
	//public static String DLQUERY = "legacy:ServiceCase and legacy:hasJurisdictionCode value \"COM\" and legacy:hasActivity some (owl:Thing and legacy:hasEmailTemplate some owl:Thing)";
	//public static String[] PROPS = new String[] { "iri", "label", "hasEmailTemplate.iri", "hasEmailTemplate.hasTo", "hasEmailTemplate.hasSubject" };
	
	//17 Activities with autoAssing true, but no assignmentRule
	public static String DLQUERY = "legacy:MessageVariable ";
	public static String[] PROPS = new String[] { "iri", "label" };
	
	public static String[] VAR3 = new String[]
	{
		"BULKYTRA",
		"MISSEDBU",		
		"EZGO",
		"GARBAGEM"
	};

	public static String[] VAR2 = new String[]
	{
		"WASDHET",
		"WASDHETD",		
		"WASDHETR",
		"WASDHEW",
		"WASDHEWR"
	};

	
	public static String[] VAR1 = new String[]
	{
		"311DUMP",
		"BULKYTRA",
		"EZGO",
		"GARBAGEM",
		"MISSEDBU",
		"PCNOACC",
		"PERSONEL",
		"SOLIDWAS",
		"SWBUNOAC",
		"SWECLO",
		"SWESHCR",
		"SWMADRCP",
		"SWMCOMM",
		"SWMEIDS",
		"SWMERECC",
		"SWMERECM",
		"SWMFACI",
		"SWMOVCL", //below 4 were added May 15th.
		"SWMRECIS",
		"SWMRESVC",
		"SWMTOUR",
		"SWMOVCTI"
	};
	
	public static String[] VAR = VAR3;
 
	private static Json curJson;
	
	private static Set<String> messageVariables; 
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		messageVariables = new TreeSet<String>();
		String curQuery; 
		boolean done = true;
		int index = 0;
		OWL.reasoner();
		//Print header
		System.out.println(DLQUERY);
		//System.out.println(VAR.toString());
		//System.out.print("VAR;index;");
		for (String prop : PROPS) 
		{
			System.out.print(prop + ";");
		}
		System.out.println();
		//Let's go!
		CachedReasoner.DBG_CACHE_MISS = false;
		do {
			if (DLQUERY.contains("$$VAR$$"))
			{
				curQuery = DLQUERY.replace("$$VAR$$", VAR[index]);
				done = (index >= VAR.length - 1); 
			} else
				curQuery = DLQUERY;
			Set<OWLNamedIndividual> rs = OWL.queryIndividuals(curQuery);
			int i = 1;
			for(OWLNamedIndividual ind : rs) {
				Json indJ = OWL.toJSON(ind);
				if (PRINT_JSON) System.out.println(indJ.toString());
				//System.out.print(VAR[index] + ";" + i + ";");
				
				printIndividual("" + i + ";", indJ, PROPS);
				i++;
			}
			index ++;
		}
		while (!done);
		System.out.println("Finished!");
		if (COLLECT_MESSAGEVARIABLES) {
			System.out.println("FOUND MESSAGEVARIABLES DURING QUERY: " + messageVariables.size());
			for (String var : messageVariables)
				System.out.println(var);
		}
	}
	
	/**
	 * Prints an individual
	 * @param i
	 * @param props
	 */
	public static void printIndividual(Json i, String[] props)
	{
		curJson = i;
		List<String> propPathToParent = new ArrayList<String>();
		printIndividualRecursive("", i, 0, props, propPathToParent);
	}
	
	public static void printIndividual(String linePrefix, Json i, String[] props)
	{
		if (linePrefix == null) linePrefix = "";
		curJson = i;
		List<String> propPathToParent = new ArrayList<String>();
		printIndividualRecursive(linePrefix, i, 0, props, propPathToParent);
	}

	private static void printIndividualRecursive(String curLine, Json i, int pIdx, String[] props, List<String> propPathToParent)
	{
		for (int propIdx = pIdx; propIdx < props.length; propIdx++)
		{
			String prop = props[propIdx];
			Json value = getPropertyValue(i, prop, propPathToParent);
			if (value == null) 
				{ 
					curLine += "N-A;";
					continue;
				}
			if (!value.isPrimitive()) 
			{
				String pathElem = getNextPropPathElem(prop, propPathToParent); 
				if (pathElem != null) {
					propPathToParent.add(pathElem);
					if (value.isArray())
					{
						List<Json> l = value.asJsonList();
						String curLineX = curLine;
						int idx = 0;
						for (Json cur : l) 
						{							
							//TODO maybe: curLineX = curLine + "[" + idx + "];";
							printIndividualRecursive(curLineX, cur,propIdx, props, propPathToParent);
							idx++;
						}
					} 
					else 
					{
						printIndividualRecursive(curLine, value, propIdx, props, propPathToParent);
					}
					propPathToParent.remove(propPathToParent.size() - 1);
					return;
				}
			}
			else
			{
				String valueStr = value.toString();
				if (prop.endsWith("iri") && valueStr.lastIndexOf('#') > 0)
				{
					valueStr = valueStr.split("#")[1];
					valueStr = valueStr.trim();
					if (valueStr.endsWith("\"")) 
						valueStr = valueStr.substring(0, valueStr.length() - 1);
				}
				if (LIST_MESSAGEVARIABLES && (prop.endsWith("Body") || prop.endsWith("Subject") 
						|| prop.endsWith("hasTo") || prop.endsWith("hasCC"))) 
				{
					Set<String> msgVars = listMessageVariablesIn(valueStr);
					valueStr = "";
					for (String msgVar : msgVars)
					{
						valueStr += msgVar + ".";
						if (COLLECT_MESSAGEVARIABLES)
							messageVariables.add(msgVar);
					}
				}
				curLine += (valueStr + ";");
			}
		}
		System.out.println(curLine);
	}
//Error: COMSWCB;"SOLID WASTE MISCELLANEOUS REQUESTS";[0];COMSWCB_ENTERBLU\";"Error:\"http://www.miamidade.gov/cirm/legacy#COMSWCB_ENTERBLU\"";COMSWCB_ENTERBLU\";"Error:\"http://www.miamidade.gov/cirm/legacy#COMSWCB_ENTERBLU\"";


	private static Set<String> listMessageVariablesIn(String valueStr)
	{
		Set<String> s = new HashSet<String>();
		Matcher m = MessageManager.VAR_NAME_PATTERN.matcher(valueStr);
		while (m.find())
			s.add(m.group());
		return s;
	}
	/**
	 * 
	 * @param prop
	 * @param propPathToParent
	 * @return a property string that is the next level in prop for , no dots included.
	 */
	private static String getNextPropPathElem(String prop,
			List<String> propPathToParent)
	{
		return getNextPropPathElem(prop, propPathToParent, false);
	}
	
	/**
	 * 
	 * @param prop
	 * @param propPathToParent
	 * @param includeParents
	 * @return the next dotted string after the current prop path if all parents match
	 */
	private static String getNextPropPathElem(String prop,
			List<String> propPathToParent, boolean includeParents)
	{
		StringBuffer result = new StringBuffer(prop.length());
		String[] path = prop.split("\\."); //dept division
		int level = 0;
		//Find level = how many dots in prop to omit
		for (String pathElem : path) 
		{
				if (level < propPathToParent.size())
				{
					if (!propPathToParent.get(level).equals(pathElem)) 
					//on not mathing that needs to match
						return null;
					else if (includeParents)
						result.append(path[level] + ".");
				level ++;
			}
			else
				break;
		}
		if (level < path.length)
			return result.append(path[level]).toString();
		else
			return null;
	}
	/**
	 * 
	 * @param i
	 * @param prop
	 * @param level
	 * @return null, if the prop does not apply to the level.
	 */
	public static Json getPropertyValue(Json i, String prop, List<String> propPathToParent) 
	{
		//level counts how many dots to omit for the prop to be applicable to the current Json i
		String propNextLevel = getNextPropPathElem(prop, propPathToParent, false);
		if (propNextLevel == null) return null;
		List<String> path = Arrays.asList(propNextLevel.split("\\.")); //dept division
		if (path == null) System.out.print("PathNull"); 
		for (String elem : path)
		{
			if (i.isPrimitive()) {
				//
				if (i.toString().contains("#")) {
					//we try to move our reference i the JSON structure to an earlier location
					//where toJSon has first visited the OWLObject and fully serialized it
					Json j = locateByIRI(i.toString());
					if (j == null) 
						Json.make("Error:" + i.toString());
					else
						i = j;
				}
			}
			if (i.isNull() || !i.has(elem)) {
				return Json.make("N/A");
			} 
			i = i.at(elem);			
		}
		return i;		
	}
	private static Json locateByIRI(String iri)
	{
		return locateByIRIRecursive(iri, curJson);
	}
	
	public static Json locateByIRIRecursive(String iri, Json cur) 
	{
		Json found;
		if (cur.isArray()) {
			List<Json> l = cur.asJsonList();
			for (Json arrElem : l)
			{
				found = locateByIRIRecursive(iri, arrElem);
				if (found != null) return found;
			}
		}
		else if (cur.isObject()) 
		{
			if (cur.has("iri") && cur.at("iri").toString().equals(iri))
				return cur;
			else 
			{
				Map<String, Json> m = cur.asJsonMap();
				for (Map.Entry<String, Json> mapElem : m.entrySet())
				{
					found = locateByIRIRecursive(iri, mapElem.getValue());
					if (found != null) return found;
				}
					
			}
		}
		return null;
	}
}

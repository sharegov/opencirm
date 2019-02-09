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

import static org.sharegov.cirm.OWL.and;
import static org.sharegov.cirm.OWL.dataFactory;
import static org.sharegov.cirm.OWL.dataProperty;
import static org.sharegov.cirm.OWL.has;
import static org.sharegov.cirm.OWL.owlClass;
import static org.sharegov.cirm.OWL.reasoner;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import mjson.Json;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.AutoConfigurable;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.user.FailoverUserProvider;
import org.sharegov.cirm.user.LDAPUserProvider;
import org.sharegov.cirm.user.UserProvider;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.JsonUtil;

public class EnetUserProvider implements UserProvider, AutoConfigurable
{
    public static final String ENET_GROUP_IRI_PREFIX = "mdc:";
    
    private Json description = Json.object();
    private UserProvider delegate = null;
    
    private List<String> getEnetLDAPGroups(String uid)
    {
        List<String> result = new ArrayList<String>();
        Json groupsFound = delegate.findGroups(uid); //find("uniquemember", "uid="+ uid + "*");
        for (Json group : groupsFound.asJsonList()) 
        {
            Json cn = group.at("cn");
            if (cn != null && cn.isString()) 
                result.add(cn.asString());
            else 
                System.err.println("ENETLDAP: Non String, Empty or null group cn for user: " + uid + " was: " + cn);
        }
        return result;
    }
    
    private Json getGroups(Json userdata)
    {       
        Json groups = Json.array();
        if (userdata.has("uid")) 
        {
            for (String enetGroup : getEnetLDAPGroups(userdata.at("uid").asString()))
            {
                if (enetGroup.isEmpty() || enetGroup.length() < 5) System.err.println("Empty or short enetGroup name :" 
                            + enetGroup + " for user with uid=" +  userdata.at("uid").asString());
                enetGroup = GenUtils.capitalize(enetGroup);
                if (enetGroup.startsWith("Cirm")) 
                {
                    // Only those guaranteed to be in Ontology
                    IRI enetGroupIRI = OWL.fullIri(ENET_GROUP_IRI_PREFIX + enetGroup);
                    groups.add(enetGroupIRI.toString());
                }
            }
        }
        // MDC employees and 
        if (userdata.has("mdcDepartment") && !userdata.at("mdcDepartment").asString().isEmpty()) 
        {
            if (Character.isDigit(userdata.at("mdcDepartment").asString().charAt(0)))
            {
                int deptCode = userdata.at("mdcDepartment").asInteger();
                OWLLiteral deptCodeLit = dataFactory().getOWLLiteral(deptCode);
                Set<OWLNamedIndividual> departments = reasoner().getInstances(
                        and(owlClass("Department_County"), has(dataProperty("Dept_Code"), deptCodeLit)), false).getFlattened();  
                if (departments.size() > 1) 
                {
                    System.err.println("Ontology Error: More than one department found for Dept_Code:" + deptCode + " Check ontology!! Resuming by using all.");
                }
                for (OWLNamedIndividual departmentInd : departments)
                {
                    groups.add(departmentInd.getIRI().toString());
                    if (userdata.has("mdcDivision")) {
                        //mdc:Division_County and mdc:Division_Code value 51 and legacy:hasAccessPolicy some owl:Thing and inverse mdc:hasSubAgency value mdc:Water_and_Sewer_Department
                        int divisonCode = userdata.at("mdcDivision").asInteger();
                        String query = "mdc:Division_County and mdc:Division_Code value " + divisonCode 
                                + " and legacy:hasAccessPolicy some owl:Thing and inverse mdc:hasSubAgency value mdc:" + departmentInd.getIRI().getFragment() + " ";
                        for (OWLNamedIndividual ind : OWL.queryIndividuals(query))
                        {
                            groups.add(ind.getIRI().toString());
                        }
                    }
                }
            } 
            // CITY users e.g. CITYOFMIAMI
            //else if (userdata.has("mdcDepartment") && userdata.at("mdcDepartment").isString())
            else
            {
                String deptCodeStr = userdata.at("mdcDepartment").asString().trim();
                if ("COM".equals(deptCodeStr)) {
                    //FIX for COM in LDAP - Fix LDAP set CITYOFMIAMI
                    if ("COM".equals(deptCodeStr)) deptCodeStr = "CITYOFMIAMI"; 
                    OWLLiteral deptCodeLit = dataFactory().getOWLLiteral(deptCodeStr);
                    Set<OWLNamedIndividual> cities = reasoner().getInstances(and(owlClass("City_Organization"), has(dataProperty("Dept_Code"), deptCodeLit)), false).getFlattened();
                    if (cities.isEmpty()) 
                    {
                        System.err.println("Ontology Error: No city found for Dept_Code:" + deptCodeStr + " Check ontology!! No city based permissions given.");
                    } 
                    else if (cities.size() > 1) 
                    {
                        System.err.println("Ontology Error: More than one city found for Dept_Code:" + deptCodeStr + " Check ontology!! Resuming by using all.");
                    }
                    // else ok
                    for (OWLNamedIndividual cityInd : cities) 
                    {
                        groups.add(cityInd.getIRI().toString());
                        if (userdata.has("mdcDivision")) {
                            //mdc:City_Organization and mdc:Division_Code value 010 and legacy:hasAccessPolicy some owl:Thing and mdc:Parent_City value mdc:City_of_Miami
                            int divisonCode = userdata.at("mdcDivision").asInteger();
                            String query = "mdc:City_Organization and mdc:Division_Code value " + divisonCode 
                                    + " and legacy:hasAccessPolicy some owl:Thing and mdc:Parent_City value mdc:" + cityInd.getIRI().getFragment() + " ";
                            for (OWLNamedIndividual ind : OWL.queryIndividuals(query))
                            {
                                groups.add(ind.getIRI().toString());
                            }
                        }
                    }
                } else {
                	//Other Strings, e.g FDOH
                    OWLLiteral deptCodeLit = dataFactory().getOWLLiteral(deptCodeStr);
                    Set<OWLNamedIndividual> govEnterprises = reasoner().getInstances(and(owlClass("Government_Enterprise"), has(dataProperty("Dept_Code"), deptCodeLit)), false).getFlattened();
                    if (govEnterprises.isEmpty()) 
                    {
                        System.err.println("Ontology Error: No Government_Enterprise found for Dept_Code:" + deptCodeStr + " Check ontology!! No Government_Enterprise based permissions given.");
                    } 
                    else if (govEnterprises.size() > 1) 
                    {
                        System.err.println("Ontology Error: More than one Government_Enterprise found for Dept_Code:" + deptCodeStr + " Check ontology!! Resuming by using all.");
                    }
                    // else ok
                    for (OWLNamedIndividual govEnterpriseInd : govEnterprises) 
                    {
                        groups.add(govEnterpriseInd.getIRI().toString());
                        if (userdata.has("mdcDivision")) {
                            //mdc:City_Organization and mdc:Division_Code value 010 and legacy:hasAccessPolicy some owl:Thing and mdc:Parent_City value mdc:City_of_Miami
                            int divisonCode = userdata.at("mdcDivision").asInteger();
                            String query = "mdc:Government_Enterprise and mdc:Division_Code value " + divisonCode 
                                    + " and mdc:hasParentAgency value mdc:" + govEnterpriseInd.getIRI().getFragment() + " ";
                            for (OWLNamedIndividual ind : OWL.queryIndividuals(query))
                            {
                                groups.add(ind.getIRI().toString());
                            }
                        }
                    }
                }
            }
        }
        return groups;
    }    
    
    public EnetUserProvider()
    {
    }
    
    public void autoConfigure(Json config)
    {            
        this.description = config.dup();
        OWLNamedIndividual primaryProvider = Refs.configSet.resolve().get("LdapUserProvider");
        OWLNamedIndividual failoverProvider = Refs.configSet.resolve().get("LdapUserProviderReplicate"); 
        if (primaryProvider == null)
        {
            delegate = new LDAPUserProvider(this.description);
        }
        else if (failoverProvider == null)
        {
            delegate = new LDAPUserProvider(OWL.toJSON(primaryProvider));
        }
        else
        {
            delegate = new FailoverUserProvider(new LDAPUserProvider(OWL.toJSON(primaryProvider)), 
                                                new LDAPUserProvider(OWL.toJSON(failoverProvider)));
        }
    }

    public Json find(String attribute, String value)
    {
        return delegate.find(attribute, value);
    }

    public Json find(Json prototype)
    {
        return delegate.find(prototype);
    }

    public Json find(Json prototype, int resultLimit)
    {
        return delegate.find(prototype, resultLimit);
    }

    public Json findGroups(String id)
    {
        return getGroups(get(id));
    }

    public Json get(String id)
    {
    	if (!UserUtil.isECkey(id)) return Json.nil();
        return delegate.get(id);
    }

    public String getIdAttribute()
    {
        return delegate.getIdAttribute();
    }

    public boolean authenticate(String username, String password)
    {
        return delegate.authenticate(username, password);
    }
    
    public Json populate(Json user)
    {
    	if (user.has("userid"))
    	{
    		Json found = get(user.at("userid").asString());
    		if (!found.isNull())
    		{    			
    			user.set(description.at("hasName").asString(), found);
    			JsonUtil.setIfMissing(user, "email", found.at("mail"));    			
    			JsonUtil.setIfMissing(user, "FirstName", found.at("givenName"));    			
    			JsonUtil.setIfMissing(user, "LastName", found.at("sn"));
    			JsonUtil.setIfMissing(user, "hasUsername", found.at("uid"));    			
    		}
    	}
    	return user;
    }
}

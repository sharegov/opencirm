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
package org.sharegov.cirm;

import static org.sharegov.cirm.OWL.*;

import java.util.HashMap;
import java.util.Map;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

public class ConfigSet
{
	private static volatile ConfigSet activeInstance = null;
	
	private Map<String, OWLObject> params = new HashMap<String, OWLObject>();
	private OWLNamedIndividual theset = null;
	
	
	private static void loadInstance(String instanceName, ConfigSet instance)
	{		 
//		meta.getManager().applyChanges(meta.getManager().addAxiom(ontology(), 
//				meta.getDataFactory().getOWLSameIndividualAxiom(individual(instanceName),individual("ActiveConfigSet"))));
		OWLReasoner reasoner = reasoner(ontology());
		OWLNamedIndividual activeInd = dataFactory().getOWLNamedIndividual(fullIri(instanceName));		
//		Set<OWLNamedIndividual> S = reasoner.getSameIndividuals(activeInd).getEntitiesMinus(activeInd);
//		if (!S.isEmpty())
////			throw new RuntimeException("No active configuration available: " +
////					"ActiveConfigSet individual must have a 'same' declaration with the actual configuration.");
//			activeInd = S.iterator().next();
		instance.theset = activeInd;
		for (OWLNamedIndividual prop : reasoner.getObjectPropertyValues(activeInd, objectProperty("hasMember")).getFlattened())
		{
			String name = dataProperty(prop, "Name").getLiteral();//reasoner.getDataPropertyValues(prop, dataProperty("Name")).iterator().next().getLiteral();
            OWLObject value = objectProperty(prop, "ValueObj"); //reasoner.getObjectPropertyValues(prop, objectProperty("Value")).getFlattened();
            //TODO remove next two lines (tolerance for old triple punning of Value 
            if (value == null) {
                value = objectProperty(prop, "Value");
            }
            if (value == null) {
                value = dataProperty(prop, "Value");
            }
            if (value == null) throw new java.lang.IllegalStateException("NULL VALUE FOR property " + name 
                    + " of configset " + activeInd.getIRI() 
                    +  " found. Configuration not valid. Exiting.");
            instance.params.put(name, value);
        }
	}
	
	public static synchronized ConfigSet getInstance()
	{
		if (activeInstance == null)
		{
			activeInstance = new ConfigSet();
			String instanceName = StartUp.config.at("ontologyConfigSet").asString();
			
			loadInstance(instanceName, activeInstance);
		}
		return activeInstance;
	}
	
	public OWLNamedIndividual getConfigSetIndividual()
	{
		return theset;
	}
	
	public Map<String, OWLObject> getParams()
	{
		return params;
	}
	 
	@SuppressWarnings("unchecked")
	public <T> T get(String name)	
	{
		return (T)params.get(name);
	}
}

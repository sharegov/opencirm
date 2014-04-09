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
package org.sharegov.cirm.reasoner;

import static org.sharegov.cirm.reasoner.OWLHelp.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLProperty;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

public class ReasonerBench
{
	private Map<OWLClass, OWLNamedIndividual> getTableMappings()
	{
		Map<OWLClass, OWLNamedIndividual> mapping = new LinkedHashMap<OWLClass, OWLNamedIndividual>();
		OWLClassExpression q = and(owlClass(fulliri("OWLClass")), 
							some(objectProperty(fulliri("hasTableMapping")),
									owlClass(fulliri("DBTable"))));
		Set<OWLNamedIndividual> S = reasoner.getInstances(q, false).getFlattened();
		int x = S.size();
		for (OWLNamedIndividual i : S)
		{
			OWLClass mappedClass = owlClass(i.getIRI());
			OWLNamedIndividual table = objectProperty(i, fulliri("hasTableMapping"));
			mapping.put(mappedClass, table);
			for(OWLClass sub : reasoner.getSubClasses(mappedClass, false).getFlattened())
			{
				if(!sub.isOWLNothing() && objectProperty(individual(sub.getIRI()),fulliri("hasTableMapping")) == null)
				{
					mapping.put(sub, table);
				}
			}
		}
		return mapping;
	}
	
	private void getColumMappings()
	{
		Map<OWLClass, OWLNamedIndividual> tableMapping = getTableMappings();
		Map<OWLNamedIndividual, Map<OWLProperty<?, ?>, OWLNamedIndividual>> mapping = new LinkedHashMap<OWLNamedIndividual, Map<OWLProperty<?, ?>, OWLNamedIndividual>>();
		OWLClassExpression q = and(owlClass(fulliri("OWLProperty")), 
				some(objectProperty(fulliri("hasColumnMapping")), 
					 and(
				        or(owlClass(fulliri("DBPrimaryKey")), owlClass(fulliri("DBNoKey"))), 
				        some(objectProperty(fulliri("hasTable")), 
				        		oneOf(tableMapping.values().toArray(new OWLIndividual[tableMapping.values().size()]))))));
		long start = System.currentTimeMillis();
		NodeSet<OWLNamedIndividual> S = reasoner.getInstances(q, false);
		System.out.println("Time " + (System.currentTimeMillis() - start) + " on " + q);
		System.out.println(S.getFlattened());
	}
	
	public static void main(String [] argv)
	{
		try
		{
			OWLHelp.init();
			ReasonerBench b = new ReasonerBench();
			System.out.println(b.getTableMappings());			
			b.getColumMappings();
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
	}
}

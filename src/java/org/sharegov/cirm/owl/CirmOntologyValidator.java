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
package org.sharegov.cirm.owl;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.hypergraphdb.util.Pair;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.sharegov.cirm.OWL;

public class CirmOntologyValidator
{

	public static boolean DBG = true;

	/**
	 * Finds all individuals where more than one type is asserted, which is
	 * disallowed by Cirm team convention. Except: Protected is allowed as
	 * second class, but will still be returned from this method.
	 * 
	 * @return
	 */
	public List<Pair<OWLNamedIndividual, Set<OWLClassExpression>>> getIndividualsWithMoreThanOneTypeAsserted()
	{
		List<Pair<OWLNamedIndividual, Set<OWLClassExpression>>> errors = new LinkedList<Pair<OWLNamedIndividual, Set<OWLClassExpression>>>();
		if (DBG)
			System.out
					.println("Start CirmOntologyValidator::getIndividualsWithMoreThanOneTypeAsserted");
		Set<OWLOntology> owlOntologies = OWL.ontologies();
		for (OWLOntology owlOntology : owlOntologies)
		{
			if (DBG)
				System.out
						.println("Getting individuals in signature of Ontology "
								+ owlOntology);
			Set<OWLNamedIndividual> s = owlOntology
					.getIndividualsInSignature(false);
			if (DBG)
				System.out.println("...done.");
			for (OWLNamedIndividual owlNamedIndividual : s)
			{
				Set<OWLClassExpression> types = owlNamedIndividual
						.getTypes(owlOntologies);
				if (types.size() > 1)
				{
					Pair<OWLNamedIndividual, Set<OWLClassExpression>> error = new Pair<OWLNamedIndividual, Set<OWLClassExpression>>(
							owlNamedIndividual, types);
					errors.add(error);
					if (DBG)
					{
						System.out.println("Found " + types.size()
								+ " types for " + owlNamedIndividual);
						for (OWLClassExpression owlClassExpression : types)
						{
							System.out.println(owlClassExpression);
						}
					}
				}
			} // for (OWLNamedIndividual
		} // for (OWLOntology
		if (DBG)
			System.out.println("Total Individuals not passing validation: "
					+ errors.size());
		return errors;
	}

	public static void main(String[] argv)
	{
		CirmOntologyValidator v = new CirmOntologyValidator();
		v.getIndividualsWithMoreThanOneTypeAsserted();
	}
}

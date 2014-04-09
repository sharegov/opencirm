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
package org.sharegov.cirm.workflows;

import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.objectProperty;

import org.hypergraphdb.app.owl.model.swrl.SWRLBuiltInAtomHGDB;
import org.hypergraphdb.util.RefResolver;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLProperty;
import org.semanticweb.owlapi.model.SWRLArgument;
import org.semanticweb.owlapi.model.SWRLAtom;
import org.semanticweb.owlapi.model.SWRLBuiltInAtom;
import org.semanticweb.owlapi.model.SWRLIArgument;
import org.semanticweb.owlapi.model.SWRLIndividualArgument;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.OWL;

/**
 * 
 * <p>
 * Given an unknown SWRL atom, where there is not enough info to evaluate it,
 * an <code>AtomResolverFactory</code> will produce a SWRL built-in that acquires
 * the necessary information, possibly with a side-effect. The main intended use is 
 * for object and data property atoms. The factory will return <code>null</code> if 
 * it can't produce such a built-in.
 * </p>
 *
 * @author Borislav Iordanov
 *
 */
public class AtomResolverFactory implements RefResolver<SWRLAtom, SWRLBuiltInAtom>
{
	@SuppressWarnings("unchecked")
	public SWRLBuiltInAtom resolve(SWRLAtom atom)
	{
		if (! (atom.getPredicate() instanceof OWLProperty) || atom.getAllArguments().isEmpty())			
			return null; 
		SWRLArgument arg1 = atom.getAllArguments().iterator().next();
		if (! (arg1 instanceof SWRLIArgument))
			return null;
		IRI subject = null;
		if (arg1 instanceof SWRLIndividualArgument)
		{
			OWLIndividual ind = ((SWRLIndividualArgument)arg1).getIndividual();
			if (! (ind instanceof OWLNamedIndividual))
				return null;
			subject = ((OWLNamedIndividual)ind).getIRI();			
		}
		else
			subject = ((SWRLVariable)arg1).getIRI();
		OWLProperty prop = (OWLProperty)atom.getPredicate();
		OWLNamedIndividual punnedProp = individual(prop.getIRI());
		OWLNamedIndividual propertyResolver = (OWLNamedIndividual)objectProperty(punnedProp, 
																		Refs.hasPropertyResolver);
//		System.out.println("Property resolver for property " + punnedProp + " is " + propertyResolver);
		if (propertyResolver != null)
		{
			SWRLBuiltInAtomHGDB builtin = new SWRLBuiltInAtomHGDB(propertyResolver.getIRI());
			OWLDataFactory df = OWL.dataFactory();
			builtin.getArguments().add(df.getSWRLLiteralArgument(df.getOWLLiteral(prop.getIRI().toString())));
			builtin.getArguments().add(df.getSWRLLiteralArgument(df.getOWLLiteral(subject.toString())));
			return builtin;
		}
		else
			return null; 
	}	
}

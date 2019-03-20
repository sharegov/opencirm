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

import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.SWRLAtom;
import org.semanticweb.owlapi.model.SWRLBuiltInAtom;
import org.semanticweb.owlapi.model.SWRLClassAtom;
import org.semanticweb.owlapi.model.SWRLDataPropertyAtom;
import org.semanticweb.owlapi.model.SWRLIndividualArgument;
import org.semanticweb.owlapi.model.SWRLLiteralArgument;
import org.semanticweb.owlapi.model.SWRLObjectPropertyAtom;
import org.semanticweb.owlapi.model.SWRLRule;
import org.semanticweb.owlapi.model.SWRLSameIndividualAtom;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.sharegov.cirm.utils.EvalUtils;

import mjson.Json;
import static mjson.Json.*;

public class JsonSWRLSerializer
{
	public Json apply(SWRLLiteralArgument arg)
	{
		return object().set("type", "literal").set("value", arg.getLiteral().getLiteral());
	}
 
	public Json apply(SWRLVariable arg)
	{
		return object().set("type", "variable").set("iri", arg.getIRI().toString());
	}

	public Json apply(SWRLIndividualArgument arg)
	{
		return object().set("type", "individual").set("iri", arg.getIndividual().toStringID());
	}
	
	public Json apply(SWRLSameIndividualAtom atom)
	{
		return object().set("type", "same")
					   .set("first", (Json)EvalUtils.dispatch(this, atom.getFirstArgument()))
					   .set("second", (Json)EvalUtils.dispatch(this, atom.getFirstArgument()));	
	}
	
	public Json apply(SWRLClassAtom atom)
	{
		return object().set("type", "class")
					   .set("class", atom.getPredicate().asOWLClass().getIRI().toString())
					   .set("argument", (Json)EvalUtils.dispatch(this, atom.getArgument()));
	}

	public Json apply(OWLObjectProperty prop)
	{
		return object().set("type", "object-property")
					   .set("iri", prop.getIRI().toString());
	}
	
	public Json apply(SWRLObjectPropertyAtom atom)
	{
		return object().set("type", "object-atom")
					   .set("predicate", (Json)EvalUtils.dispatch(this, atom.getPredicate()))
					   .set("subject", (Json)EvalUtils.dispatch(this, atom.getFirstArgument()))
					   .set("object", (Json)EvalUtils.dispatch(this, atom.getSecondArgument()));		
	}

	public Json apply(OWLDataProperty prop)
	{
		return object().set("type", "data-property")
					   .set("iri", prop.getIRI().toString());
	}
	
	public Json apply(SWRLDataPropertyAtom atom)
	{
		return object().set("type", "data-atom")
					   .set("predicate", (Json)EvalUtils.dispatch(this, atom.getPredicate()))
					   .set("subject", (Json)EvalUtils.dispatch(this, atom.getFirstArgument()))
					   .set("object", (Json)EvalUtils.dispatch(this, atom.getSecondArgument()));		
	}
	
	public Json apply(SWRLBuiltInAtom atom)
	{
		Json j = object().set("type", "builtin")
						 .set("predicate", atom.getPredicate().toString())
						 .set("arguments", array());
		for (int i = 0;i < atom.getArguments().size(); i++)
			j.at("arguments").add((Json)EvalUtils.dispatch(this, atom.getArguments().get(i)));
		return j;
	}
	
	public Json apply(SWRLRule rule)
	{
		Json result = object();
		Json b = array();
		Json h = array();
		for (SWRLAtom atom :rule.getBody())
			b.add(EvalUtils.dispatch(this, atom));
		result.set("body", b);
		for (SWRLAtom atom: rule.getHead())
			h.add(EvalUtils.dispatch(this, atom));
		result.set("head", h);
		return result;
	}
}

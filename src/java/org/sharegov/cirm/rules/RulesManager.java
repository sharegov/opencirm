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
package org.sharegov.cirm.rules;

import static org.sharegov.cirm.OWL.dataProperty;

import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.objectProperty;
import static org.sharegov.cirm.OWL.owlClass;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mjson.Json;
import org.semanticweb.owlapi.io.WriterDocumentTarget;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.SWRLArgument;
import org.semanticweb.owlapi.model.SWRLAtom;
import org.semanticweb.owlapi.model.SWRLDArgument;
import org.semanticweb.owlapi.model.SWRLIArgument;
import org.semanticweb.owlapi.model.SWRLRule;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.utils.DirectRef;
import org.sharegov.cirm.utils.ObjectRef;
import org.sharegov.cirm.utils.SingletonRef;

public class RulesManager
{
	public static SingletonRef<RulesManager> ref = 
		new SingletonRef<RulesManager>(new ObjectRef<RulesManager>(DirectRef.make(RulesManager.class)));
	
	public static RulesManager getInstance() { return ref.resolve(); }
	
	public OWLOntology getRuleOntology(String type)
	{
		return OWL.loader().get(Refs.nameBase.resolve() + "/swrl/" + type);
	}
	
	public void setRules(String type, Set<SWRLRule> rules) throws OWLOntologyCreationException,OWLOntologyStorageException,IOException
	{
		
		OWLOntologyManager manager = Refs.tempOntoManager.resolve();
		OWLOntology ontology = manager.createOntology
								(IRI.create(Refs.nameBase.resolve() + "/swrl/" + type));
		List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
		for(SWRLRule rule: rules)
		{
			if(rule != null)
			{
				AddAxiom axiom = new AddAxiom(ontology,rule);
				changes.add(axiom);
			}
		}
		if(!changes.isEmpty())
		{
			deleteRules(type);
			File f = getFile(type);
			manager.applyChanges(changes);
			manager.saveOntology(ontology,new WriterDocumentTarget(new FileWriter(f)));
			
		}
	}
	
	public Set<SWRLRule> getRules(String type)
	{
		OWLOntology o = getRuleOntology(type);
		if(o == null)
			return new HashSet<SWRLRule>();
		else  
			return o.getAxioms(AxiomType.SWRL_RULE);
	}
	
	public Set<IRI> getAllRules()
	{
		return OWL.loader().allSWRL();
	}
	
	public void deleteRules(String type)
	{
		File f = getFile(type);
		if(f.exists())
		{
			f.delete();
		}
	}
	
	public Set<SWRLRule> fromJSON(Json json)
	{
		Set<SWRLRule> result = new HashSet<SWRLRule>();
		if(json.isArray())
		{
			
			for(Json rule: json.asJsonList())
			{
				Set<SWRLAtom> body = toAtoms(rule.at("body"));
				Set<SWRLAtom> head = toAtoms(rule.at("head"));
				result.add(OWL.dataFactory().getSWRLRule(body, head));
			}
		}
		return result;
	}
	
	public Set<SWRLAtom> toAtoms(Json j)
	{
		Set<SWRLAtom> result = new HashSet<SWRLAtom>();
		if(j.isArray())
		{
			for(Json o: j.asJsonList())
			{
				SWRLAtom a = toAtom(o);
				result.add(a);
			}
		}
		return result;
	}
	
	public SWRLAtom toAtom(Json j)
	{
		String type = j.at("type").asString();
		OWLDataFactory factory = OWL.dataFactory();
		if("data-atom".equals(type))
		{
			return factory.
					getSWRLDataPropertyAtom(
							dataProperty(j.at("predicate").at("iri")
							.asString())
							, (SWRLIArgument)toArg(j.at("subject"))
							, (SWRLDArgument)toArg(j.at("object")));
		}
		else if("object-atom".equals(type))
		{
			return factory.
					getSWRLObjectPropertyAtom(
							objectProperty(j.at("predicate").at("iri")
							.asString())
							, (SWRLIArgument)toArg(j.at("subject"))
							, (SWRLIArgument)toArg(j.at("object")));
		}
		else if("builtin".equals(type))
		{
			return factory.
					getSWRLBuiltInAtom(
							IRI.create(j.at("predicate").asString())
							, toArgs(j.at("arguments")));
		}
		else if("same".equals(type))
		{
			return factory.
					getSWRLSameIndividualAtom(
							(SWRLIArgument)toArg(j.at("first"))
							, (SWRLIArgument)toArg(j.at("second")));
		}
		else if("class".equals(type))
		{
			return factory.
					getSWRLClassAtom(
							owlClass(j.at("class").asString()), 
							(SWRLIArgument)toArg(j.at("argument")));
		}
		else
			return null;
	}
	
	public List<SWRLDArgument> toArgs(Json j)
	{
		List<SWRLDArgument> result = new ArrayList<SWRLDArgument>();
		if(j.isArray())
		{
			for(Json o: j.asJsonList())
			{ 
				result.add((SWRLDArgument)toArg(o));
			}
		}
		return result;
	}
	
	public SWRLArgument toArg(Json j)
	{
		String type = j.at("type").asString();
		OWLDataFactory factory = OWL.dataFactory();
		if("variable".equals(type))
		{
			return factory
				.getSWRLVariable(IRI.create(j.at("iri").asString()));
		}
		else if("literal".equals(type))
		{
			return factory
				.getSWRLLiteralArgument(
						factory.getOWLLiteral(j.at("value").asString()));
		}
		else if("individual".equals(type))
		{
			return factory
				.getSWRLIndividualArgument(
						individual(j.at("iri").asString()));
		}
		else
			return null;
	}
	
	private File getFile(String type)
	{
		File f =  new File(new File(StartUp.getConfig().at("workingDir").asString() + "/src/ontology"), 
				type + ".swrl");
		return f;
	}
}

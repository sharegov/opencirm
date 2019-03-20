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

import java.io.File;
import java.util.Set;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataAllValuesFrom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataHasValue;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLDataRange;
import org.semanticweb.owlapi.model.OWLDataSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectAllValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectComplementOf;
import org.semanticweb.owlapi.model.OWLObjectHasValue;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectOneOf;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectUnionOf;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.sharegov.cirm.owl.SynchronizedOWLManager;

import com.clarkparsia.pellet.owlapiv3.PelletReasonerFactory;

public class OWLHelp
{
	static File dir = new File("c:/work/cirmservices/src/ontology");
	static OWLOntologyManager manager;
	static OWLDataFactory factory;
	static OWLReasonerFactory reasonerFactory = PelletReasonerFactory.getInstance();
	static OWLReasoner reasoner;
	
	static void init() throws Exception
	{
		manager = SynchronizedOWLManager.createOWLOntologyManager();		
		factory = manager.getOWLDataFactory();
		
		manager.loadOntologyFromOntologyDocument(new File(dir, "County_Confidential.owl"));
		manager.loadOntologyFromOntologyDocument(new File(dir, "csr.owl"));
		OWLOntology o = manager.loadOntologyFromOntologyDocument(new File(dir, "legacy.owl"));
		reasoner = reasonerFactory.createReasoner(o);
	}
	
	public static IRI fulliri(String s)
	{
		if (s != null && s.startsWith("legacy:"))
			return IRI.create("http://www.miamidade.gov/cirm/legacy#" + s.substring("legacy:".length()));
		IRI ontologyIri = IRI.create("http://www.miamidade.gov/ontology");
		if (s == null || s.length() == 0)
			return ontologyIri;
		else if (s.charAt(0) == '/' || s.charAt(0) == '#')
			return ontologyIri.resolve(s);
		else if (!s.startsWith("http://"))
			return ontologyIri.resolve("#" + s);
		else
			return IRI.create(s);
	}
	
	public static OWLNamedIndividual individual(IRI id) 
	{
		return factory.getOWLNamedIndividual(id);
	}
	
	public static OWLClass owlClass(IRI id)
	{
		return factory.getOWLClass(id);
	}
	
	public static OWLNamedIndividual objectProperty(OWLNamedIndividual ind, IRI id)
	{
		OWLObjectProperty prop = objectProperty(id);
		if (prop == null)
			throw new NullPointerException("No object property with ID '" + id + "'");
		Set<OWLNamedIndividual> S = reasoner.getObjectPropertyValues(ind, prop).getFlattened();
		if (S.isEmpty())
			return null;
		else
			return S.iterator().next();
	}
	
	public static OWLObjectProperty objectProperty(IRI id)
	{
		return factory.getOWLObjectProperty(id);
	}
	
	public static OWLDataProperty dataProperty(IRI id)
	{
		return factory.getOWLDataProperty(id);
	}	
	
	// forming class expressions
	public static OWLObjectSomeValuesFrom some(OWLObjectPropertyExpression prop, OWLClassExpression clexpr)
	{
		return factory.getOWLObjectSomeValuesFrom(prop, clexpr);
	}

	public static OWLDataSomeValuesFrom some(OWLDataPropertyExpression prop, OWLDataRange range)
	{
		return factory.getOWLDataSomeValuesFrom(prop, range);
	}

	public static OWLDataHasValue has(OWLDataPropertyExpression prop, OWLLiteral literal)
	{
		return factory.getOWLDataHasValue(prop, literal);
	}
	
	public static OWLObjectHasValue has(OWLObjectPropertyExpression prop, OWLIndividual individual)
	{
		return factory.getOWLObjectHasValue(prop, individual);
	}
	
	public static OWLObjectAllValuesFrom only(OWLObjectPropertyExpression prop, OWLClassExpression cl)
	{
		return factory.getOWLObjectAllValuesFrom(prop, cl);
	}

	public static OWLDataAllValuesFrom only(OWLDataPropertyExpression prop, OWLDataRange range)
	{
		return factory.getOWLDataAllValuesFrom(prop, range);
	}
	
	public static OWLObjectComplementOf not(OWLClassExpression cl)
	{
		return factory.getOWLObjectComplementOf(cl);
	}
	
	public static OWLObjectIntersectionOf and(OWLClassExpression...classExpressions)
	{
		return factory.getOWLObjectIntersectionOf(classExpressions);
	}
	
	public static OWLObjectUnionOf or(OWLClassExpression...classExpressions)
	{
		return factory.getOWLObjectUnionOf(classExpressions);
	}
	
	public static OWLObjectOneOf oneOf(OWLIndividual...individuals)
	{
		return factory.getOWLObjectOneOf(individuals);
	} 
	
	public static Set<OWLNamedIndividual> objectProperties(OWLNamedIndividual ind, String id)
	{
		 return reasoner.getObjectPropertyValues(ind, objectProperty(fulliri(id))).getFlattened();
	}
	
	public static final String hasContents = "http://www.miamidade.gov/ontology#hasContents";
	public static final String hasNext = "http://www.miamidade.gov/ontology#hasNext";
	public static final String EmptyList = "http://www.miamidade.gov/ontology#EmptyList";
	public static final String isJsonMapper = "http://www.miamidade.gov/ontology#isJsonMapper";
	public static final String hasJsonMapper = "http://www.miamidade.gov/ontology#hasJsonMapper";
	public static final String hasParentClass = "http://www.miamidade.gov/ontology#hasParentClass";
	public static final String hasPropertyResolver = "http://www.miamidade.gov/ontology#hasPropertyResolver";
	public static final String OWLClass = "http://www.miamidade.gov/ontology#OWLClass";
	public static final String OWLProperty = "http://www.miamidade.gov/ontology#OWLProperty";
	public static final String OWLDataProperty = "http://www.miamidade.gov/ontology#OWLDataProperty";
	public static final String OWLObjectProperty = "http://www.miamidade.gov/ontology#OWLObjectProperty";
}

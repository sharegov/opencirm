package org.sharegov.cirm.owl;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.sharegov.cirm.OWL;

/**
 * <p>
 * This class is intended to be a bridge between Java code and the OWL models. 
 * It often happens that Java or JavaScript code has to be written that directly
 * refers to various OWL entities. Since the ontology itself is subject to 
 * evolution (changes), this direct dependency between code and metadata can
 * easily become problematic. This class should accumulate methods and knowledge
 * how to handle the dependency, for example resolving namespace (a.k.a. OWL prefix)
 * issues etc.
 * </p>
 * 
 * @author Borislav Iordanov
 *
 */
public class Model
{
	public static IRI upper(String name)
	{
		return OWL.fullIri(name);
	}

	public static IRI legacy(String name)
	{
		return OWL.fullIri("legacy:" + name);
	}
	
	public static String prefixFor(String fragment, Class<?> type) throws Exception
	{
		for (String prefix : OWL.prefixManager().getPrefixNames()) {
			IRI iri = OWL.fullIri(prefix + fragment);
			for (OWLOntology o : OWL.ontologies()) {
				if (type == OWLClass.class && o.containsClassInSignature(iri)) {
					return prefix;
				} else if (type == OWLDataProperty.class
						&& o.containsDataPropertyInSignature(iri)) {
					return prefix;
				} else if (type == OWLObjectProperty.class
						&& o.containsObjectPropertyInSignature(iri)) {
					return prefix;
				} else if ((type == OWLIndividual.class ||type == OWLNamedIndividual.class)
							&& o.containsIndividualInSignature(iri)) {
						return prefix;
				}
			}
		}
		throw new Exception("Undeclared OWL entity fragment: " + fragment);
	}
	
	
	public static String prefixFor(String fragment, Class<?> type, String defaultPrefix)
	{
		try{
			return  prefixFor(fragment, type);
		}catch (Exception e)
		{
			return defaultPrefix;
		}
	}
}
package org.sharegov.cirm.owl;

import org.semanticweb.owlapi.model.IRI;
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
}
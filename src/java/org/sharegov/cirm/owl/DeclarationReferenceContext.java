package org.sharegov.cirm.owl;

import org.semanticweb.owlapi.model.EntityType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.PrefixManager;
import org.sharegov.cirm.OWL;

/**
 * <p>
 * An {@link OWLReferenceContext} implementation based on the declaration
 * of entities in an ontology module dependency graph.
 * </p>
 * 
 * <p>
 * The basic idea here is that at any point, we have a <em>current</em> 
 * ontology which holds the knowledge relevant at that particular 
 * execution point. While this is not mandatory, it is quite common 
 * for each ontology module to have its own base IRI for entities
 * declared in it. It is also not mandatory to declare entities 
 * before asserting anything about them, but we will make that assumption
 * as a prerequisite and proper knowledge authoring for our system.   
 * </p>
 * 
 * @author Borislav Iordanov
 *
 */
public class DeclarationReferenceContext implements OWLReferenceContext
{
	private OWLOntology ontology;
	private PrefixManager prefixManager;
	private DeclarationReferenceContext [] parents;	

	
	public DeclarationReferenceContext(OWLOntology ontology, 
									   PrefixManager prefixManager, 
									   DeclarationReferenceContext [] parents) 
	{
		this.ontology = ontology;
		this.prefixManager = prefixManager;
		this.parents = parents;
	}
	
	@Override
	public String fullName(String name)
	{
		if (name == null) return null;
		else if (name.startsWith("http:") || name.startsWith("https:")) return name;
		String prefixName;		
		int colonidx = name.indexOf(':');
		if (colonidx > 0)
		{
			prefixName = name.substring(0, colonidx + 1);
			name = name.substring(colonidx + 1);
		}
		else
			prefixName = ":";
		String prefix = prefixManager.getPrefix(prefixName);
		return prefix == null ? null : prefix + name;
	}

	@Override
	public IRI fullIri(String name)
	{
		String fullName = fullName(name);
		return fullName == null ? null : IRI.create(fullName);
	}

	public <T extends OWLEntity> T entityFromParents(String name, EntityType<T> type)
	{
		T e = null;
		for (DeclarationReferenceContext p : parents)
		{
			T e2 = p.entity(name, type);
			if (e2 != null)
				if (e == null)
					e = e2;
				else if (!e2.equals(e))
					throw new RuntimeException("Ambiguous name <" + name + "> resolves to two entities:" +
							e + " and " + e2);
		}
		return e;
	}
	
	@Override
	public <T extends OWLEntity> T entity(String name, EntityType<T> type)
	{		
		String full = fullName(name);
		if (full == null)
			return entityFromParents(name, type);		
		T e = OWL.dataFactory().getOWLEntity(type, IRI.create(full));
		if (!ontology.getDeclarationAxioms(e).isEmpty())
			return e;
		else
			return parents != null ? entityFromParents(name, type) : null;
	}
	
	public static DeclarationReferenceContext make(OWLOntology ontology)
	{
		DeclarationReferenceContext [] parents = new DeclarationReferenceContext[ontology.getImportsDeclarations().size()];
		int pindex = 0;
		for (OWLImportsDeclaration decl : ontology.getImportsDeclarations())
		{
			OWLOntology pontology = OWL.ontology(decl.getIRI().toString());
			parents[pindex++] = make(pontology);
		}
		return new DeclarationReferenceContext(ontology, OWL.prefixManager(), parents);
	}
}
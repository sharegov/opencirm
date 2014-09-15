package org.sharegov.cirm.owl;

import org.semanticweb.owlapi.model.EntityType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLEntity;

/**
 * 
 * <p>
 * Provides a mapping from a short name of an OWL entity to its fully qualified
 * IRI or {@link OWLEntity} instance for that matter. This is a bit like the 
 * {@link BiDirectionalShortFormProvider} except you can also obtain simply the
 * longer version of a name rather than the entity. Thus, entities of different
 * types but with the same name can be handled together in the same context.  
 * </p>
 * 
 * <p>
 * An implementation should be versatile and deal with situations when the name 
 * is already fully qualified, or it looks like a relative URL so it must be resolved
 * in the context of a naming top-level IRI etc.
 * </p>
 * 
 * <p>
 * The intention for <code>OWLReferenceContext</code>s is to be long-lived. Therefore,
 * it is okay and recommended to cache any information to speedup translation from short
 * to full name.
 * </p>
 * 
 * @author Borislav Iordanov
 *
 */
public interface OWLReferenceContext
{
	/**
	 * Not sure if we need name->full name/full iri, since we are probably
	 * dealing with entities wherever we work with this.
	 * 
	 * This would make sense for an OWLReferenceContext that doesn't do
	 * entity lookup like the DeclarationReferenceContext, but has some
	 * other rules to determine the full name.
	 * @param name
	 * @return
	 */
	String fullName(String name);
	
	/**
	 * IRI.create(fullName(name))
	 * 
	 * @param name
	 * @return
	 */
	IRI fullIri(String name); 
	<T extends OWLEntity> T entity(String name, EntityType<T> type);
	
	// it could make sense here to provide other methods for example
	// to obtain a list of "related" entity to a given entity for
	// purpose of "intellisense" in a text editor for example
	//
	// e.g. OWLProperty relatedProperties(individual)
}
package org.sharegov.cirm;

import org.semanticweb.owlapi.model.IRI;

/*
 * @author Chirino
 * 
 * Simple wrapper for Ontology Properties attributes 
 * 
 */

public class PropertyDescriptor {
	private IRI iri;
	private PropertyType type;
	
	public PropertyType getType() {
		return type;
	}
	public void setType(PropertyType type) {
		this.type = type;
	}
	public IRI getIri() {
		return iri;
	}
	public void setIri(IRI iri) {
		this.iri = iri;
	}
}

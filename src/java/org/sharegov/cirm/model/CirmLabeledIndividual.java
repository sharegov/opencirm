package org.sharegov.cirm.model;

import mjson.Json;

/**
 * Immutable Cirm optionally labeled individual.
 * 
 * @author Thomas Hilpold
 */
public class CirmLabeledIndividual {

	private String iri;
	private String label = null;
	
	public static CirmLabeledIndividual createFrom(Json iriAndLabel) {
		if (!iriAndLabel.has("iri")) return null;
		if (!iriAndLabel.at("iri").isString()) return null;
		if (iriAndLabel.at("iri").asString().isEmpty()) return null;
		String iri = iriAndLabel.at("iri").asString();
		if (iriAndLabel.has("label") && iriAndLabel.at("label").isString()) {
			return new CirmLabeledIndividual(iri, iriAndLabel.at("label").asString());
		} else {
			return new CirmLabeledIndividual(iri);
		}
	}
	
	public CirmLabeledIndividual(String iri) {
		this.iri = iri; 
	}

	public CirmLabeledIndividual(String iri, String label) {
		this.iri = iri; 
		this.label = label;
	}
	
	public String getIri() {
		return iri;
	}

	public String getLabel() {
		return label;
	}
	
	public String toString() {
		return getIri() + " (" + getLabel() + ")";
	}

}

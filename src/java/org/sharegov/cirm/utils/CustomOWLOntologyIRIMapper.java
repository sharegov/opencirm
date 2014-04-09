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
package org.sharegov.cirm.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;

/**
 * A CustomOWLOntologyIRIMapper that can read ontology to document mappings from java Properties.
 * Use this by adding it to the OWLOntologyManager's iri mappers to provide 
 * document locations for specific ontologies during load and import resolution.
 * 
 * Example property (file) contents:
 * #Custom Mappings below: 
 * http://miamidade.gov/ontology = file://C:/myonto.owl 
 * http://miamidade.gov/ontology = hgdb://miamionto
 * 
 * The right hand value side has to be convertible into a URL.
 * 
 * @author Thomas Hilpold
 *
 */
public class CustomOWLOntologyIRIMapper implements OWLOntologyIRIMapper
{
	public static boolean DBG = true;
	
	private Map<IRI, IRI> ontologyIRIToDocumentIRIMap = new HashMap<IRI, IRI>();
	
	/**
	 * Convenience method to create a CustomOWLOntologyIRIMapper from mappingFile.
	 * @param mappingFile
	 * @return
	 */
	public static CustomOWLOntologyIRIMapper createFrom(File propertiesFile) {
		Properties properties = new Properties();
		try {
		    properties.load(new FileInputStream(propertiesFile));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return new CustomOWLOntologyIRIMapper(properties);
	}
	
	public CustomOWLOntologyIRIMapper() {
		//nothing to do.
	}

	public CustomOWLOntologyIRIMapper(Properties customMappings) {
		setCustomMappings(customMappings);
	}

	/**
	 * Sets the custom ontologyIRI to documentIRI mappings.
	 *  
	 * @param customMappings
	 * @throws IllegalArgumentException, if either key or value could not be converted into IRI, or documentIRI cannot be converted to URI.
	 */
	public void setCustomMappings(Properties customMappings) {
		ontologyIRIToDocumentIRIMap.clear();
		for (Entry<Object, Object> customMapping : customMappings.entrySet()) {
			try {
				IRI ontologyIRI = IRI.create(((String)customMapping.getKey()).trim());
				IRI documentIRI = IRI.create(((String)customMapping.getValue()).trim());
				ontologyIRIToDocumentIRIMap.put(ontologyIRI, documentIRI);
				//Test URL creation of document IRI to validate. Has to be absolute.
				@SuppressWarnings("unused")
				URL documentURL = documentIRI.toURI().toURL();
				if (DBG) System.out.println("Custom Mapping: " + ontologyIRI + " --> " + documentIRI + " registered.");
			} catch (Exception e) {
				throw new IllegalArgumentException("Error during reading property: " +
						customMapping.getKey() + " -> " + customMapping.getValue(), e);
			}
		}
	}

	/**
	 * Returns the current mappings from ontologyIRI to documentIRI.
	 * @return
	 */
	public Map<IRI, IRI> getCustomMappings() {
		return ontologyIRIToDocumentIRIMap;
	}

	
	@Override
	public IRI getDocumentIRI(IRI ontologyIRI)
	{
		IRI documentIRI = ontologyIRIToDocumentIRIMap.get(ontologyIRI);
		if (DBG && documentIRI != null) {
			System.out.println("Custom Mapper: " + ontologyIRI + " --> " + documentIRI + " used.");
		}
		return documentIRI;
	}

}

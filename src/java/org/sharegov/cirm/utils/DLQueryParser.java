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


import org.coode.owlapi.manchesterowlsyntax.ManchesterOWLSyntaxEditorParser;
import org.semanticweb.owlapi.expression.OWLEntityChecker;
import org.semanticweb.owlapi.expression.ParserException;
import org.semanticweb.owlapi.expression.ShortFormEntityChecker;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.PrefixManager;
import org.semanticweb.owlapi.model.UnloadableImportException;
import org.semanticweb.owlapi.util.BidirectionalShortFormProvider;
import org.semanticweb.owlapi.util.BidirectionalShortFormProviderAdapter;
import org.semanticweb.owlapi.util.ShortFormProvider;
import org.semanticweb.owlapi.util.SimpleShortFormProvider;
import org.sharegov.cirm.OWL;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>
 * Parse a DL query with Manchester syntax. Adapted from the first author below.
 * Be sure to call the {@link #dispose()} method after you're done with the parser
 * to avoid a memory leak due to a change listener being registered with the
 * OWLOntologyManager. 
 * </p>
 * 
 * This class is thread safe. If changes to the ontology occur, the ontology should be managed by a synchronized manager, for thread safe change propagation.
 * The data factory used by this class and acquired through the manager should be synchronized also.
 * 
 * Author: Matthew Horridge<br>
 * The University of Manchester<br>
 * Bio-Health Informatics Group<br>,
 * Borislav Iordanov, Thomas Hilpold Miami-Dade County
 * Date: 13-May-2010
 */
public class DLQueryParser
{
	
	/**
	 * Creates and caches a or returns a cached DLQueryParser.
	 * This method is thread safe. 
	 * @param rootOntology an ontology (with a synchronized manager, if changes to the ontology occur.)
	 * @return
	 */
	public static synchronized DLQueryParser getParser(OWLOntology rootOntology) {
		return getParser(rootOntology, DEFAULT_SHORTFORM_PROVIDER);
	}

	/**
	 * Creates and caches a or returns a cached DLQueryParser.
	 * This method is thread safe. 
	 * @param rootOntology an ontology (with a synchronized manager, if changes to the ontology occur).
	 * @param shortFormProvider callers should make sure to pass in the same provider for the same ontologies or providers that overwrite equals.
	 * @return
	 */
	public static synchronized DLQueryParser getParser(OWLOntology rootOntology, ShortFormProvider shortFormProvider) {
		IRI ontoIRI = rootOntology.getOntologyID().getOntologyIRI();
		DLQueryParser result = cache.get(ontoIRI);
		if (result == null || !result.getShortFormProviderOnCreation().equals(shortFormProvider)) {
			result = new DLQueryParser(rootOntology, shortFormProvider);
			cache.put(ontoIRI, result);
		}
		return result;
	}

	/**
	 * Clears the cache and ensures change listeners are unregisterd from the ontologie's manager to allow garbage collection of the cached parsers.
	 */
	public static synchronized void disposeCachedParsers() {
		for (DLQueryParser cur : cache.values()) {
			//unregister listener at manager to allow GCign 
			cur.dispose();
		}
		cache.clear();
	}

	public static synchronized void disposeCachedParser(OWLOntology rootOntology) {
		DLQueryParser cur = getParser(rootOntology);
		//unregister listener at manager to allow GCign 
		cur.dispose();
		cache.remove(rootOntology);
	}

	//SimpleShortFormProvider determined stateless and therefore thread safe in OWLAPI 3.2.4
	private static final SimpleShortFormProvider DEFAULT_SHORTFORM_PROVIDER = new SimpleShortFormProvider();
	
	private static volatile ConcurrentHashMap<IRI, DLQueryParser> cache = new ConcurrentHashMap<IRI, DLQueryParser>(); 

	private OWLOntology rootOntology;

	private BidirectionalShortFormProvider bidiShortFormProvider;

	private ShortFormProvider shortFormProviderOnCreation;

	private String[] swapDefaultPrefixNameSpecification;

	private DLQueryParser(OWLOntology rootOntology)
	{
		this(rootOntology, new SimpleShortFormProvider());
	}
	
	/**
	 * Constructs a DLQueryParser using the specified ontology and short form
	 * provider to map entity IRIs to short names.
	 * 
	 * @param rootOntology
	 *            The root ontology. This essentially provides the domain
	 *            vocabulary for the query.
	 * @param shortFormProvider
	 *            A short form provider to be used for mapping back and forth
	 *            between entities and their short names (renderings).
	 */
	private DLQueryParser(OWLOntology rootOntology,
			ShortFormProvider shortFormProvider)
	{
		shortFormProviderOnCreation = shortFormProvider;
		this.rootOntology = rootOntology;
		OWLOntologyManager manager = rootOntology.getOWLOntologyManager();
		Set<OWLOntology> importsClosure = rootOntology.getImportsClosure();
		// Create a bidirectional short form provider to do the actual mapping.
		// It will generate names using the input
		// short form provider.
		synchronized(manager) {
			BidirectionalShortFormProviderAdapter bsfa = new BidirectionalShortFormProviderAdapter(
					manager, importsClosure, shortFormProvider);
			bsfa.add(OWL.dataFactory().getOWLNothing());
			bsfa.add(OWL.dataFactory().getOWLThing());
			bidiShortFormProvider = bsfa;
			swapDefaultPrefixNameSpecification = getPrefixNameToSwapForDoubleDefault(shortFormProviderOnCreation);
		}
	}

	private ShortFormProvider getShortFormProviderOnCreation() {
		return shortFormProviderOnCreation;
	}

	/**
	 * Parses a class expression string to obtain a class expression.
	 * This method is thread safe. 
	 * @param classExpressionString
	 *            The class expression string
	 * @return The corresponding class expression
	 * @throws ParserException
	 *             if the class expression string is malformed or contains
	 *             unknown entity names.
	 */
	public OWLClassExpression parseClassExpression(String classExpressionString)
			throws ParserException
	{
		OWLOntologyManager manager = rootOntology.getOWLOntologyManager();
		OWLDataFactory dataFactory = manager.getOWLDataFactory();
		// Set up the real parser
		ManchesterOWLSyntaxEditorParser parser = new ManchesterOWLSyntaxEditorParser(
				dataFactory, classExpressionString);
		parser.setDefaultOntology(rootOntology);
		
		// Specify an entity checker that wil be used to check a class
		// expression contains the correct names.
		final OWLEntityChecker ec = new ShortFormEntityChecker(bidiShortFormProvider);
		OWLEntityChecker entityChecker =  new OWL.EntityChecker(); //new DoubleDefaultPrefixEntityCheckerAdapter(ec);
		parser.setOWLEntityChecker(entityChecker);
//		parser.setOWLEntityChecker(ec);
		// Do the actual parsing
		synchronized(manager) {
			// Sync necessary, because manager might fire changes to the CacheBidiShortFormProviderAdapter
			// used by the parser in another thread.
			return parser.parseClassExpression();
		}
	}
	
	/**
	 * In case one iris can be mapped to a default as well as a custom prefix, 
	 * determines which prefix the bidiShortFormProvider (accidentally) uses and returns
	 * which prefix therefore needs to be replaced for which other.
	 * e.g. bidi uses : for (default and mdc:) -> [0]=mdc:, [1]=:
	 *  bidi uses mdc: for (default and mdc:) -> [0]=:, [1]=mdc:
	 * @return a String[2] [0]=fromPrefix [1]=toPrefix
	 */
	private String[] getPrefixNameToSwapForDoubleDefault(ShortFormProvider shortFormProvider) 
	{
		String[] result = new String[] { ":", ":" };
		if (shortFormProvider instanceof PrefixManager)
		{
			PrefixManager pfm = (PrefixManager)shortFormProviderOnCreation;
			if (pfm.containsPrefixMapping(":")) 
			{
				String defaultPrefix = pfm.getDefaultPrefix(); //e.g. http://www.miamidade.gov/ontology#
				String alternativeDefaultPrefixName = null;
				for (Map.Entry<String, String> nameToPrefix : pfm.getPrefixName2PrefixMap().entrySet())
				{
					if (!nameToPrefix.getKey().equals(":")
							&& nameToPrefix.getValue().equals(defaultPrefix))
					{
							//another prefix name (e.g. mdc:) that maps to the same prefix as default was found.
							alternativeDefaultPrefixName = nameToPrefix.getKey();
					}
				}
				if (alternativeDefaultPrefixName != null) 
				{
					OWLNamedIndividual testShortFormIndividual = OWL.individual(defaultPrefix + "TEST");
					String testShortForm = shortFormProviderOnCreation.getShortForm(testShortFormIndividual); 
					if (testShortForm.indexOf(':') > 0)  { //after first char
						//using alterNativeDefaultPrefixName for Bidicache
						result = new String[] { ":", alternativeDefaultPrefixName };
					} else {
						//using default prefix for Bidicache
						result = new String[] { alternativeDefaultPrefixName, ":" };
					}
				}
			}
		}
		return result;
	}

	/**
	 * This method is thread safe. 
	 * @param individual
	 * @return
	 * @throws ParserException
	 */
	public OWLIndividual parseIndExpression(String individual)
			throws ParserException
	{
		OWLOntologyManager manager = rootOntology.getOWLOntologyManager();
		OWLDataFactory dataFactory = manager.getOWLDataFactory();
		// Set up the real parser
		ManchesterOWLSyntaxEditorParser parser = new ManchesterOWLSyntaxEditorParser(
				dataFactory, individual);
		parser.setDefaultOntology(rootOntology);
		// Specify an entity checker that wil be used to check a class
		// expression contains the correct names.
		OWLEntityChecker delegateEc = new ShortFormEntityChecker(bidiShortFormProvider);
		OWLEntityChecker entityChecker = new OWL.EntityChecker(); // new DoubleDefaultPrefixEntityCheckerAdapter(delegateEc);
		parser.setOWLEntityChecker(entityChecker);
		// Do the actual parsing
		synchronized(manager) {
			// Sync necessary, because manager might fire changes to the CacheBidiShortFormProviderAdapter
			// used by the parser in another thread.
			return parser.parseIndividual();
		}
	}

	/*
	 * This method is thread safe.
	 */
	public void dispose()
	{		
		OWLOntologyManager manager = rootOntology.getOWLOntologyManager();
		synchronized(manager) {
			// Sync necessary, because manager might fire changes to the CacheBidiShortFormProviderAdapter
			// during disposal in another thread.
			bidiShortFormProvider.dispose();
		}
	}
	
	private class DoubleDefaultPrefixEntityCheckerAdapter implements OWLEntityChecker {

		OWLEntityChecker ec;
		
		public DoubleDefaultPrefixEntityCheckerAdapter(OWLEntityChecker ec)
		{
			this.ec = ec;
		}
		
		public OWLAnnotationProperty getOWLAnnotationProperty(String name)
			{
				return ec.getOWLAnnotationProperty(name);
			}

			public OWLClass getOWLClass(String name)
			{				
				if (name.equals("Nothing")) 
					return OWL.dataFactory().getOWLNothing();
				return ec.getOWLClass(fixDefaultPrefixNameFor(name));
			}

			@Override
			public OWLDataProperty getOWLDataProperty(String name)
			{
				return ec.getOWLDataProperty(fixDefaultPrefixNameFor(name));
			}

			@Override
			public OWLDatatype getOWLDatatype(String name)
			{
				return ec.getOWLDatatype(name);
			}

			@Override
			public OWLNamedIndividual getOWLIndividual(String name)
			{
				return ec.getOWLIndividual(fixDefaultPrefixNameFor(name));
			}

			@Override
			public OWLObjectProperty getOWLObjectProperty(String name)
			{
				return ec.getOWLObjectProperty(fixDefaultPrefixNameFor(name));
			}			
			
			/**
			 * If there are two equal prefixes and one is mapped to the defaultPrefixName ":",
			 * all names have to be checked to use the same prefixName as bidicache does (either ":" or "mdc:").
			 * @param name
			 * @return
			 */
			public String fixDefaultPrefixNameFor(final String name)
			{
				String result = name;
				int colonIndex = name.indexOf(":");
				
				if (colonIndex <= 0)
				{	//() and or State
					//not found or first char
					boolean isColonFirstChar = colonIndex == 0;
					if (swapDefaultPrefixNameSpecification[0].equals(":")) 
					{
						result = swapDefaultPrefixNameSpecification[1] + (isColonFirstChar? name.substring(1) : name);	
					} else 
					{
						if (!isColonFirstChar)
						{
							result = ":" + name;
						}
					}
				} else
				{
					String prefixName = name.substring(0, colonIndex + 1);
					if (swapDefaultPrefixNameSpecification[0].equals(prefixName)) 
					{
						result = swapDefaultPrefixNameSpecification[1] + name.substring(colonIndex + 1);	
					} 
				}
				return result;
			}
	}
}

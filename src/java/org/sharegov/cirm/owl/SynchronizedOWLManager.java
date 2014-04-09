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
package org.sharegov.cirm.owl;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import uk.ac.manchester.cs.owl.owlapi.OWLDataFactoryImpl;

/**
 * A thread safe OWL Manager.
 * 
 * The static OWLManager initialization is performed in a not thread safe way.
 * 
 * @author Thomas Hilpold
 * 
 */
public class SynchronizedOWLManager
{

	private static volatile SynchronizedOWLDataFactory dataFactoryInstance;

	/**
	 * Creates an OWL ontology manager that is configured with standard parsers,
	 * storeres etc.
	 * 
	 * @return The new manager.
	 */
	public static SynchronizedOWLOntologyManager createOWLOntologyManager()
	{
		return createOWLOntologyManager(getOWLDataFactory());
	}

	/**
	 * Creates a thread safe OWL ontology manager that is configured with
	 * standard parsers, storeres etc.
	 * 
	 * The static initialization of OWLManager, which is likely caused by the
	 * first call to this method is not thread safe.
	 * 
	 * @param dataFactory
	 *            A thread safe data factory that the manager should have a
	 *            reference to.
	 * @return The manager.
	 */
	private static SynchronizedOWLOntologyManager createOWLOntologyManager(
			SynchronizedOWLDataFactory dataFactory)
	{
		// Create the ontology manager and add ontology factories, mappers and
		// storers
		// causes non thread safe static initialization of OWLManager.
		OWLOntologyManager ontologyManager = OWLManager
				.createOWLOntologyManager(dataFactory);
		return SynchronizedOWLOntologyManager
				.synchronizedManager(ontologyManager);
	}

	/**
	 * Gets a global data factory that can be used to create OWL API objects.
	 * THREAD SAFE.
	 * 
	 * @return The thread safe OWLDataFactory singleton instance.
	 */
	public static synchronized SynchronizedOWLDataFactory getOWLDataFactory()
	{
		if (dataFactoryInstance == null)
		{
			dataFactoryInstance = SynchronizedOWLDataFactory
					.synchronizedFactory(OWLDataFactoryImpl.getInstance());
		}
		return dataFactoryInstance;
	}
}

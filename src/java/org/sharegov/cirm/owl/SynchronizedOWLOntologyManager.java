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

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.semanticweb.owlapi.io.OWLOntologyDocumentSource;
import org.semanticweb.owlapi.io.OWLOntologyDocumentTarget;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.ImpendingOWLOntologyChangeListener;
import org.semanticweb.owlapi.model.MissingImportListener;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyChangeBroadcastStrategy;
import org.semanticweb.owlapi.model.OWLOntologyChangeListener;
import org.semanticweb.owlapi.model.OWLOntologyChangeProgressListener;
import org.semanticweb.owlapi.model.OWLOntologyChangesVetoedListener;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyFactory;
import org.semanticweb.owlapi.model.OWLOntologyFormat;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyLoaderListener;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyRenameException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLOntologyStorer;
import org.semanticweb.owlapi.model.UnknownOWLOntologyException;
import org.semanticweb.owlapi.model.UnloadableImportException;

/**
 * SynchronizedOWLOntologyManager wraps any OWLOntologyManager for synchronized thread safe access.
 * 
 * @author Thomas Hilpold
 *
 */
@SuppressWarnings("deprecation")
public class SynchronizedOWLOntologyManager implements OWLOntologyManager
{

	OWLOntologyManager manager;

	public static SynchronizedOWLOntologyManager synchronizedManager(
			OWLOntologyManager manager)
	{
		if (manager instanceof SynchronizedOWLOntologyManager)
		{
			throw new IllegalStateException(
					"sychronized manager called with already synchronized manager: "
							+ manager);
		}
		return new SynchronizedOWLOntologyManager(manager);
	}

	SynchronizedOWLOntologyManager(OWLOntologyManager manager)
	{
		this.manager = manager;
	}

	public synchronized OWLOntologyManager getWrappedOWLOntologyManager()
	{
		return manager;
	}

	public synchronized OWLDataFactory getOWLDataFactory()
	{
		return manager.getOWLDataFactory();
	}

	public synchronized Set<OWLOntology> getOntologies()
	{
		return manager.getOntologies();
	}

	public synchronized Set<OWLOntology> getOntologies(OWLAxiom axiom)
	{
		return manager.getOntologies(axiom);
	}

	public synchronized Set<OWLOntology> getVersions(IRI ontology)
	{
		return manager.getVersions(ontology);
	}

	public synchronized boolean contains(IRI ontologyIRI)
	{
		return manager.contains(ontologyIRI);
	}

	public synchronized boolean contains(OWLOntologyID id)
	{
		return manager.contains(id);
	}

	public synchronized OWLOntology getOntology(IRI ontologyIRI)
	{
		return manager.getOntology(ontologyIRI);
	}

	public synchronized OWLOntology getOntology(OWLOntologyID ontologyID)
	{
		return manager.getOntology(ontologyID);
	}

	public synchronized OWLOntology getImportedOntology(
			OWLImportsDeclaration declaration)
	{
		return manager.getImportedOntology(declaration);
	}

	public synchronized Set<OWLOntology> getDirectImports(OWLOntology ontology)
	{
		return manager.getDirectImports(ontology);
	}

	public synchronized Set<OWLOntology> getImports(OWLOntology ontology)
	{
		return manager.getImports(ontology);
	}

	public synchronized Set<OWLOntology> getImportsClosure(OWLOntology ontology)
	{
		return manager.getImportsClosure(ontology);
	}

	public synchronized List<OWLOntology> getSortedImportsClosure(
			OWLOntology ontology)
	{
		return manager.getSortedImportsClosure(ontology);
	}

	public synchronized List<OWLOntologyChange> applyChanges(
			List<? extends OWLOntologyChange> changes)
			throws OWLOntologyRenameException
	{
		return manager.applyChanges(changes);
	}

	public synchronized List<OWLOntologyChange> addAxioms(OWLOntology ont,
			Set<? extends OWLAxiom> axioms)
	{
		return manager.addAxioms(ont, axioms);
	}

	public synchronized List<OWLOntologyChange> addAxiom(OWLOntology ont,
			OWLAxiom axiom)
	{
		return manager.addAxiom(ont, axiom);
	}

	public synchronized List<OWLOntologyChange> removeAxiom(OWLOntology ont,
			OWLAxiom axiom)
	{
		return manager.removeAxiom(ont, axiom);
	}

	public synchronized List<OWLOntologyChange> removeAxioms(OWLOntology ont,
			Set<? extends OWLAxiom> axioms)
	{
		return manager.removeAxioms(ont, axioms);
	}

	public synchronized List<OWLOntologyChange> applyChange(
			OWLOntologyChange change) throws OWLOntologyRenameException
	{
		return manager.applyChange(change);
	}

	public synchronized OWLOntology createOntology()
			throws OWLOntologyCreationException
	{
		return manager.createOntology();
	}

	public synchronized OWLOntology createOntology(Set<OWLAxiom> axioms)
			throws OWLOntologyCreationException
	{
		return manager.createOntology(axioms);
	}

	public synchronized OWLOntology createOntology(Set<OWLAxiom> axioms,
			IRI ontologyIRI) throws OWLOntologyCreationException
	{
		return manager.createOntology(axioms, ontologyIRI);
	}

	public synchronized OWLOntology createOntology(IRI ontologyIRI)
			throws OWLOntologyCreationException
	{
		return manager.createOntology(ontologyIRI);
	}

	public synchronized OWLOntology createOntology(OWLOntologyID ontologyID)
			throws OWLOntologyCreationException
	{
		return manager.createOntology(ontologyID);
	}

	public synchronized OWLOntology createOntology(IRI ontologyIRI,
			Set<OWLOntology> ontologies, boolean copyLogicalAxiomsOnly)
			throws OWLOntologyCreationException
	{
		return manager.createOntology(ontologyIRI, ontologies,
				copyLogicalAxiomsOnly);
	}

	public synchronized OWLOntology createOntology(IRI ontologyIRI,
			Set<OWLOntology> ontologies) throws OWLOntologyCreationException
	{
		return manager.createOntology(ontologyIRI, ontologies);
	}

	public synchronized OWLOntology loadOntology(IRI ontologyIRI)
			throws OWLOntologyCreationException
	{
		return manager.loadOntology(ontologyIRI);
	}

	public synchronized OWLOntology loadOntologyFromOntologyDocument(
			IRI documentIRI) throws OWLOntologyCreationException
	{
		return manager.loadOntologyFromOntologyDocument(documentIRI);
	}

	public synchronized OWLOntology loadOntologyFromOntologyDocument(File file)
			throws OWLOntologyCreationException
	{
		return manager.loadOntologyFromOntologyDocument(file);
	}

	public synchronized OWLOntology loadOntologyFromOntologyDocument(
			InputStream inputStream) throws OWLOntologyCreationException
	{
		return manager.loadOntologyFromOntologyDocument(inputStream);
	}

	public synchronized OWLOntology loadOntologyFromOntologyDocument(
			OWLOntologyDocumentSource documentSource)
			throws OWLOntologyCreationException
	{
		return manager.loadOntologyFromOntologyDocument(documentSource);
	}

	public synchronized OWLOntology loadOntologyFromOntologyDocument(
			OWLOntologyDocumentSource documentSource,
			OWLOntologyLoaderConfiguration config)
			throws OWLOntologyCreationException
	{
		return manager.loadOntologyFromOntologyDocument(documentSource, config);
	}

	public synchronized void removeOntology(OWLOntology ontology)
	{
		manager.removeOntology(ontology);
	}

	public synchronized IRI getOntologyDocumentIRI(OWLOntology ontology)
	{
		return manager.getOntologyDocumentIRI(ontology);
	}

	public synchronized void setOntologyDocumentIRI(OWLOntology ontology,
			IRI documentIRI) throws UnknownOWLOntologyException
	{
		manager.setOntologyDocumentIRI(ontology, documentIRI);
	}

	public synchronized OWLOntologyFormat getOntologyFormat(OWLOntology ontology)
			throws UnknownOWLOntologyException
	{
		return manager.getOntologyFormat(ontology);
	}

	public synchronized void setOntologyFormat(OWLOntology ontology,
			OWLOntologyFormat ontologyFormat)
	{
		manager.setOntologyFormat(ontology, ontologyFormat);
	}

	public synchronized void saveOntology(OWLOntology ontology)
			throws OWLOntologyStorageException
	{
		manager.saveOntology(ontology);
	}

	public synchronized void saveOntology(OWLOntology ontology, IRI documentIRI)
			throws OWLOntologyStorageException
	{
		manager.saveOntology(ontology, documentIRI);
	}

	public synchronized void saveOntology(OWLOntology ontology,
			OutputStream outputStream) throws OWLOntologyStorageException
	{
		manager.saveOntology(ontology, outputStream);
	}

	public synchronized void saveOntology(OWLOntology ontology,
			OWLOntologyFormat ontologyFormat)
			throws OWLOntologyStorageException
	{
		manager.saveOntology(ontology, ontologyFormat);
	}

	public synchronized void saveOntology(OWLOntology ontology,
			OWLOntologyFormat ontologyFormat, IRI documentIRI)
			throws OWLOntologyStorageException
	{
		manager.saveOntology(ontology, ontologyFormat, documentIRI);
	}

	public synchronized void saveOntology(OWLOntology ontology,
			OWLOntologyFormat ontologyFormat, OutputStream outputStream)
			throws OWLOntologyStorageException
	{
		manager.saveOntology(ontology, ontologyFormat, outputStream);
	}

	public synchronized void saveOntology(OWLOntology ontology,
			OWLOntologyDocumentTarget documentTarget)
			throws OWLOntologyStorageException
	{
		manager.saveOntology(ontology, documentTarget);
	}

	public synchronized void saveOntology(OWLOntology ontology,
			OWLOntologyFormat ontologyFormat,
			OWLOntologyDocumentTarget documentTarget)
			throws OWLOntologyStorageException
	{
		manager.saveOntology(ontology, ontologyFormat, documentTarget);
	}

	public synchronized void addIRIMapper(OWLOntologyIRIMapper mapper)
	{
		manager.addIRIMapper(mapper);
	}

	public synchronized void removeIRIMapper(OWLOntologyIRIMapper mapper)
	{
		manager.removeIRIMapper(mapper);
	}

	public synchronized void clearIRIMappers()
	{
		manager.clearIRIMappers();
	}

	public synchronized void addOntologyFactory(OWLOntologyFactory factory)
	{
		manager.addOntologyFactory(factory);
	}

	public synchronized void removeOntologyFactory(OWLOntologyFactory factory)
	{
		manager.removeOntologyFactory(factory);
	}

	public synchronized Collection<OWLOntologyFactory> getOntologyFactories()
	{
		return manager.getOntologyFactories();
	}

	public synchronized void addOntologyStorer(OWLOntologyStorer storer)
	{
		manager.addOntologyStorer(storer);
	}

	public synchronized void removeOntologyStorer(OWLOntologyStorer storer)
	{
		manager.removeOntologyStorer(storer);
	}

	public synchronized void addOntologyChangeListener(
			OWLOntologyChangeListener listener)
	{
		manager.addOntologyChangeListener(listener);
	}

	public synchronized void addOntologyChangeListener(
			OWLOntologyChangeListener listener,
			OWLOntologyChangeBroadcastStrategy strategy)
	{
		manager.addOntologyChangeListener(listener, strategy);
	}

	public synchronized void addImpendingOntologyChangeListener(
			ImpendingOWLOntologyChangeListener listener)
	{
		manager.addImpendingOntologyChangeListener(listener);
	}

	public synchronized void removeImpendingOntologyChangeListener(
			ImpendingOWLOntologyChangeListener listener)
	{
		manager.removeImpendingOntologyChangeListener(listener);
	}

	public synchronized void addOntologyChangesVetoedListener(
			OWLOntologyChangesVetoedListener listener)
	{
		manager.addOntologyChangesVetoedListener(listener);
	}

	public synchronized void removeOntologyChangesVetoedListener(
			OWLOntologyChangesVetoedListener listener)
	{
		manager.removeOntologyChangesVetoedListener(listener);
	}

	public synchronized void setDefaultChangeBroadcastStrategy(
			OWLOntologyChangeBroadcastStrategy strategy)
	{
		manager.setDefaultChangeBroadcastStrategy(strategy);
	}

	public synchronized void removeOntologyChangeListener(
			OWLOntologyChangeListener listener)
	{
		manager.removeOntologyChangeListener(listener);
	}

	public synchronized void makeLoadImportRequest(
			OWLImportsDeclaration declaration) throws UnloadableImportException
	{
		manager.makeLoadImportRequest(declaration);
	}

	public synchronized void makeLoadImportRequest(
			OWLImportsDeclaration declaration,
			OWLOntologyLoaderConfiguration configuration)
			throws UnloadableImportException
	{
		manager.makeLoadImportRequest(declaration, configuration);
	}

	public synchronized void setSilentMissingImportsHandling(boolean b)
	{
		manager.setSilentMissingImportsHandling(b);
	}

	public synchronized boolean isSilentMissingImportsHandling()
	{
		return manager.isSilentMissingImportsHandling();
	}

	public synchronized void addMissingImportListener(
			MissingImportListener listener)
	{
		manager.addMissingImportListener(listener);
	}

	public synchronized void removeMissingImportListener(
			MissingImportListener listener)
	{
		manager.removeMissingImportListener(listener);
	}

	public synchronized void addOntologyLoaderListener(
			OWLOntologyLoaderListener listener)
	{
		manager.addOntologyLoaderListener(listener);
	}

	public synchronized void removeOntologyLoaderListener(
			OWLOntologyLoaderListener listener)
	{
		manager.removeOntologyLoaderListener(listener);
	}

	public synchronized void addOntologyChangeProgessListener(
			OWLOntologyChangeProgressListener listener)
	{
		manager.addOntologyChangeProgessListener(listener);
	}

	public synchronized void removeOntologyChangeProgessListener(
			OWLOntologyChangeProgressListener listener)
	{
		manager.removeOntologyChangeProgessListener(listener);
	}

}

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
import java.util.HashSet;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

public class OwlRepoTest
{
	public final static String REPO_DIR = "TempOwlRepoTestDir";
	public static IRI IRI_1 = IRI.create("http://www.miamidade.gov/ontology");
	public static IRI IRI_2 = IRI.create("http://www.miamidade.gov/cirm/legacy");

	File tempDir;
	OwlRepo owlRepo;
	Set<IRI> ontologyIRIs;
	
	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		OwlRepoTest repoTest = new OwlRepoTest();
		repoTest.ontologyIRIs = new HashSet<IRI>();
		repoTest.ontologyIRIs.add(IRI_1);
		repoTest.ontologyIRIs.add(IRI_2);
		repoTest.init();
		repoTest.testCreateRepositoryFromDefaultPeer();
	}
	
	@Before
	public void init()
	{
		tempDir = new File(System.getProperty("java.io.tmpdir") + REPO_DIR);
		System.out.println("Creating repository in dir: " + tempDir);
		owlRepo = OwlRepo.getInstance();
		if (!tempDir.exists()) tempDir.mkdir();
	}

	@After
	public void cleanup() throws OWLOntologyCreationException
	{
		//Delete all files
	}

	public void testCreateRepositoryFromDefaultPeer() {
		owlRepo.createRepositoryFromDefaultPeer(tempDir.getAbsolutePath(), ontologyIRIs);
	}
}

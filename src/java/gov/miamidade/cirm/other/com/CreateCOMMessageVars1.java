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
package gov.miamidade.cirm.other.com;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import org.hypergraphdb.app.owl.versioning.VHGDBOntologyRepository;
import org.hypergraphdb.app.owl.versioning.VersionedOntology;
import org.mindswap.pellet.PelletOptions;
import org.mindswap.pellet.owlapi.PelletReasonerFactory;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerConfiguration;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.legacy.MessageManager;
import org.sharegov.cirm.owl.CachedReasoner;
import org.sharegov.cirm.owl.SynchronizedReasoner;
import org.sharegov.cirm.owl.Wrapper;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import com.clarkparsia.pellet.owlapiv3.PelletReasoner;

/**
 * Asserts hasVariableResolver A and hasLegacyCode B for each MessageVariable C contained in a textfile in legacy.
 * A...ANSWER_RESOLVER_IND
 * B...taken from a line in the text file
 * C...taken from a line in the text file and validated to exist in ontology
 * 
 * Reads a text file with a mapping from Variable name ($$VARNAME$$) to ServiceQuestion legacy code.
 * (Each line in the textfile starts with $$, and contains only two tokens separated by \t)
 * 
 *  All AddAxiomChanges will be applied to legacy and a local commit will be performed with commit comment 
 * 
 * 
 *  
 * @author Thomas Hilpold
 */
public class CreateCOMMessageVars1
{
	static final boolean MODIFY_ONTOLOGY = false;
	static final boolean SLOW_REASONER = false; //Set to true for some really slow perf caused by existsServiceFieldsForLegacyCode
	static final boolean REASONER_SET_PARAMS = false;
	static final OWLNamedIndividual ANSWER_RESOLVER_IND = OWL.individual("legacy:org.sharegov.cirm.legacy.AnswerResolver");  
	static final OWLObjectProperty HAS_VARIABLE_RESOLVER = OWL.objectProperty("legacy:hasVariableResolver");
	static final OWLDataProperty HAS_LEGACY_CODE = OWL.dataProperty("legacy:hasLegacyCode");  

	//static final String COMMIT_COMMENT = "675 COM Answer variable assertions for AnswerResolver";  

	static final String TEXT_FILE = "1_COMAnswerVariables.utf8.txt";  

	
	public static void main(String[] argv) 
	{
		CachedReasoner.DBG_CACHE_MISS = true;
		CreateCOMMessageVars1 app = new CreateCOMMessageVars1();
		app.execute();
		ThreadLocalStopwatch.getWatch().time("COMPLETED.");
	}
	
	void execute() 
	{
		if (REASONER_SET_PARAMS)
			reasonerInit();
		System.out.println("Reading text file.");
		Map<String, String> varNameToLegacyCode = readTextFile(this.getClass().getResource(TEXT_FILE));
		System.out.println("Validating " + varNameToLegacyCode.size() + " variabled to be in ontology");
		Map<String, OWLNamedIndividual> varToVarInd = validateMapInOnto(varNameToLegacyCode);
		if (MODIFY_ONTOLOGY)
		{
			System.out.println("Creating changeset");
			createComAnswerVarAssertionsChangeSet(varNameToLegacyCode, varToVarInd);
		} else
			System.out.println("Did not change ontology.");

	}
	
	public void reasonerInit() 
	{
//		OWLReasoner r = OWL.reasoner();
//		if (r instanceof Wrapper<?>)
//		{
//			r = ((Wrapper<OWLReasoner>)r).unwrapAll();
//		}
//		PelletReasoner pr = (PelletReasoner)r;
		//pr.getKB().setDoExplanation(false);
//		for (InferenceType it : pr.getPrecomputableInferenceTypes())
//		{
//			System.out.println("Precomputing: " + it.name());
//			pr.precomputeInferences(it);
//		}
		PelletOptions.KEEP_ABOX_ASSERTIONS = true;
		PelletOptions.MAX_ANONYMOUS_CACHE = 300000;
		PelletOptions.SAMPLING_RATIO = 0.1;	
//		PelletOptions.ALWAYS_REBUILD_RET
		PelletOptions.CACHE_RETRIEVAL = true;
//		PelletOptions.COPY_ON_WRITE
//		PelletOptions.USE_CACHING
		PelletOptions.STATIC_REORDERING_LIMIT = 1;
//		//PelletOptions.
		//OWLReasonerConfiguration
		System.out.println("PelletOptions.KEEP_ABOX_ASSERTIONS" + PelletOptions.KEEP_ABOX_ASSERTIONS);
		System.out.println("MAX_ANONYMOUS_CACHE " + PelletOptions.MAX_ANONYMOUS_CACHE);
		System.out.println("SAMPLING_RATIO " + PelletOptions.SAMPLING_RATIO);
		System.out.println("ALWAYS_REBUILD_RETE " + PelletOptions.ALWAYS_REBUILD_RETE);
		System.out.println("CACHE_RETRIEVAL " + PelletOptions.CACHE_RETRIEVAL);
		System.out.println("COPY_ON_WRITE " + PelletOptions.COPY_ON_WRITE);
		System.out.println("USE_CACHING " + PelletOptions.USE_CACHING);
		System.out.println("STATIC_REORDERING_LIMIT " + PelletOptions.STATIC_REORDERING_LIMIT);
	}

	/**
	 * 
	 * @param varNameToLegacyCode
	 * @param varToVarInd
	 * @return nr of commitable changes
	 */
	private int createComAnswerVarAssertionsChangeSet(
			Map<String, String> varNameToLegacyCode,
			Map<String, OWLNamedIndividual> varToVarInd)
	{
		OWLOntology o = OWL.ontology();
		OWLOntologyManager m = OWL.manager();
		OWLDataFactory f = OWL.dataFactory();
		VHGDBOntologyRepository repo = VHGDBOntologyRepository.getInstance();
		VersionedOntology vo = repo.getVersionControlledOntology(o);
		if(vo == null) throw new IllegalStateException("ontology " + o + " must be a version controlled ontology for changeset application");
		int changeCount = 0;
		if(vo.getNrOfCommittableChanges() > 0) throw new IllegalStateException("ontology " + o + " must not have any pending changes, has " + vo.getNrOfCommittableChanges());
		int expectedChangeCount = varToVarInd.size() * 2;
		for (String varName : varToVarInd.keySet()) 
		{
			OWLNamedIndividual varInd = varToVarInd.get(varName);
			String legacyCode = varNameToLegacyCode.get(varName);
			if (varInd == null) throw new IllegalStateException("varInd null for " + varName);
			if (legacyCode == null || legacyCode.isEmpty()) throw new IllegalStateException("legacycode null or empty for " + varName);
			//1. Axiom varInd hasVariableResolver ...AnswerResolver
			OWLAxiom ax1 = f.getOWLObjectPropertyAssertionAxiom(HAS_VARIABLE_RESOLVER, varInd, ANSWER_RESOLVER_IND);
			//2. Axiom varInd hasLegacyCode StringLiteral
			OWLAxiom ax2 = f.getOWLDataPropertyAssertionAxiom(HAS_LEGACY_CODE, varInd, legacyCode);
			m.addAxiom(o, ax1);
			changeCount ++;
			m.addAxiom(o, ax2);
			changeCount ++;
			System.out.println(changeCount + "/" + expectedChangeCount + " " + ax1);
			System.out.println(changeCount + "/" + expectedChangeCount + " " + ax2);
		}
		if (vo.getNrOfCommittableChanges() != expectedChangeCount) 
			throw new IllegalStateException("Won't commit. Nr of expected changes ("+ expectedChangeCount 
					+")does not match Commitable ("+ vo.getNrOfCommittableChanges() + ").");
		return vo.getNrOfCommittableChanges();
	}

	/**
	 * Validates and creates a Map of $$VAR$$ to VariableIndividual.
	 * Validation passes if a question is not found, as it might be added later, but fails, if a messagevariable is missing.
	 * 
	 * @param varNameToLegacyCode
	 * @return
	 */
	private Map<String, OWLNamedIndividual> validateMapInOnto(Map<String, String> varNameToLegacyCode)
	{
		int validationNoVarErrorCount = 0;
		int validationNoQuestionErrorCount = 0;
		int count = 0;
		Map<String, OWLNamedIndividual> varToVarInd = new TreeMap<String, OWLNamedIndividual>();
		for(String key : varNameToLegacyCode.keySet())
		{
			String value = varNameToLegacyCode.get(key);
			OWLNamedIndividual messageVar = findMessageVariableIndividual(key);
			boolean existsServiceFields;
			if (SLOW_REASONER)
				existsServiceFields = existsServiceFieldsForLegacyCode(value);
			else
				existsServiceFields = existsServiceFieldsForLegacyCode2(value);
				
			System.out.print(key + " -> " + value + " ");
			varToVarInd.put(key, messageVar);
			if (!(existsServiceFields &&  messageVar != null))
			{
				String msg = "";
				if (messageVar == null) {
					msg+= "NO Message Variable individual for " + key + " ";
					validationNoVarErrorCount ++;
				}
				if (!existsServiceFields) 
				{
					msg += "NO question individual for " + value;
					validationNoQuestionErrorCount ++;
				}
				System.err.println(msg);
			}
			count ++;
			System.out.println("" + count + "/" + varNameToLegacyCode.size());
		}
		String msg = "Tried: " + varNameToLegacyCode.size() 
				+ " Errors: " + (validationNoVarErrorCount + validationNoQuestionErrorCount) 
				+ " (NoQuestion: " + validationNoQuestionErrorCount + " , NoMsgVar: " + validationNoVarErrorCount + ")";
		System.out.println("validateMapInOnto " + msg);
		if (validationNoVarErrorCount> 0) 
			throw new IllegalStateException("All MessageVariables must be found" + msg);
		return varToVarInd;
	}

	Map<String, String> readTextFile(URL txtFileUtf8)
	{
		Map<String, String> varNameToLegacyCode = new TreeMap<String, String>();
		String cur;
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(txtFileUtf8.openStream(), "UTF-8"));
			do {
				cur = br.readLine();
				if (cur != null && !cur.isEmpty()) {
					addLineToMap(cur, varNameToLegacyCode);
				}
			} while(cur != null);			
			} catch (Exception e) {
				throw new RuntimeException("Error reading textfile", e);
			}
		return varNameToLegacyCode;
	}

	private void addLineToMap(String cur,
			Map<String, String> varNameToLegacyCode)
	{
		StringTokenizer st = new StringTokenizer(cur, "\t");
		//allow it to break here, if line not ok
		varNameToLegacyCode.put(st.nextToken().trim(), st.nextToken().trim());
	}
	
	/**
	 * Finds the Message Variable individual in the ontology given by varname (param must include pre and post "$$").
	 * @param varName
	 * @return
	 */
	private OWLNamedIndividual findMessageVariableIndividual(String varName)
	{
		if (!(varName.startsWith("$$") && varName.endsWith("$$"))) throw new IllegalArgumentException("$$ missing in " + varName);
		return MessageManager.findIndividualFromVariable(varName);
	}

	/**
	 * SLOW!
	 * True if one or more ServiceField(s) could be found with the given legacyCode
	 * @param legacyCode
	 * @return
	 */
	private boolean existsServiceFieldsForLegacyCode(String legacyCode)
	{
		return !OWL.queryIndividuals("legacy:ServiceField and legacy:hasLegacyCode value " + "\"" + legacyCode + "\"").isEmpty();
		//return !OWL.queryIndividuals("legacy:ServiceQuestion and legacy:hasLegacyCode value " + "\"" + legacyCode + "\"").isEmpty();
		//just as slow and wrong: return !OWL.queryIndividuals("legacy:hasLegacyCode value " + "\"" + legacyCode + "\"").isEmpty();
	}

	private boolean existsServiceFieldsForLegacyCode2(String legacyCode)
	{
		Set<OWLNamedIndividual> allServiceFields = OWL.queryIndividuals("legacy:ServiceField");
		for (OWLNamedIndividual i : allServiceFields) 
		{
			Set<OWLLiteral> values = OWL.dataProperties(i, "legacy:hasLegacyCode");
			for (OWLLiteral lit : values) 
			{
				if (lit.getLiteral().equals(legacyCode)) return true;
			}
		}
		return false;
	}

}

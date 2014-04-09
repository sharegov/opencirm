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
package org.sharegov.cirm.workflows;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hypergraphdb.util.RefResolver;
import org.junit.Test;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.SWRLBuiltInAtom;
import org.semanticweb.owlapi.model.SWRLDArgument;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.sharegov.cirm.OWL;

public class WebServiceCallTaskTest
{

	
	@Test
	public void testEval()
	{
		OWLDataFactory factory = OWL.dataFactory();
		//OWLOntology O = MetaService.get().getOntology(MetaService.iribase.toString());

		List<SWRLDArgument> args = new ArrayList<SWRLDArgument>();
		//args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral("SolidWasteAccountQueryByAddress")));
		//args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral("6544 SW 73 CT")));
		//5055 SW 113 AVE // 6544 SW 73 CT
		//args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral("WCSAccountQueryByAccount")));
		//args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral("40938937")));
		args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral("WCSBulkyQueryByAccount")));
		args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral("12409456")));
		//args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral("WCSBulkyQueryByWO")));
		//args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral("33428200")));
		//args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral("WCSPublicComplaintQueryByAccount")));
		//args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral("11563148")));
		//args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral("WCSPublicComplaintQueryByComplaint")));
		//args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral("20107186")));
		//args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral("WCSEnforcementComplaintQueryByComplaint")));
		//args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral("10560161")));
		args.add(factory.getSWRLVariable(OWL.fullIri("document")));
		SWRLBuiltInAtom atom = factory.getSWRLBuiltInAtom(OWL.fullIri("webServiceCall"), args);
		WebServiceCallTask accountQuery = new WebServiceCallTask();
		Map<SWRLVariable, OWLObject> eval = accountQuery.eval(atom, OWL.ontology(), new RefResolver<SWRLVariable, OWLObject>()
														{
															public OWLObject resolve(SWRLVariable v) { return null; }
														}
		);
		
		 System.out.println(eval);

		 //factory.getOWLDataOneOf(values);
		 
	}
	
	@Test
	public void testDatatype()
	{		
		OWLDataFactory factory = OWL.dataFactory();
		OWLDatatype type = factory.getOWLDatatype("xsd:string", new DefaultPrefixManager());
		System.out.println(type.getBuiltInDatatype());
	}
	
	
	public static void main(String[] args) throws IOException
	{
		
		WebServiceCallTaskTest test = new WebServiceCallTaskTest();
		test.testEval();
		//OWLLiteral s = OWLUtils.dataProperty(OWLUtils.individual("http://www.miamidade.gov/ontology#SolidWasteAccountQueryByAddress"), "hasRequestStylesheet");
		//System.out.println(s);
		//System.out.println(OWLUtils.unescape(s));
		
		
	}
	
}

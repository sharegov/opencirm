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
package org.sharegov.cirm.rules;

import java.util.ArrayList;

import java.util.List;
import java.util.Map;

import org.hypergraphdb.util.RefResolver;
import org.junit.Test;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.SWRLBuiltInAtom;
import org.semanticweb.owlapi.model.SWRLDArgument;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.OWL;


public class PartBuiltInTest
{

	
	@Test
	public void testEval()
	{
		OWLDataFactory factory = OWL.dataFactory();
		List<SWRLDArgument> args = new ArrayList<SWRLDArgument>();
		args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral(sampleData , OWL2Datatype.RDF_XML_LITERAL)));
		args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral("/:WCSW010Response/:WCSW010Out/:RETURNMSG")));
		args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral("xsd:string")));
		args.add(factory.getSWRLVariable(OWL.fullIri("value")));
		args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral(false)));
		args.add(factory.getSWRLLiteralArgument(factory.getOWLLiteral("http://ibmtst.metro-dade.com:4004/CICS/TWBA/WCS0010W")));
		SWRLBuiltInAtom atom = factory.getSWRLBuiltInAtom(OWL.fullIri("part"), args);
		PartBuiltIn part = new PartBuiltIn();
		Map<SWRLVariable, OWLObject> eval = part.eval(atom, OWL.ontology(), new RefResolver<SWRLVariable, OWLObject>()
														{
															public OWLObject resolve(SWRLVariable v) { return null; }
														}
		);
		
		System.out.println(sampleData);
		System.out.println(eval.get(factory.getSWRLVariable(OWL.fullIri("value"))));
		//factory.getOWLDataOneOf(values);
		
	}
	
	private String sampleData = "<WCSW010Response xmlns=\"http://ibmtst.metro-dade.com:4004/CICS/TWBA/WCS0010W\">" +
	"    <WCSW010Out>" +
	"    <MAPNAME>WCSM0005</MAPNAME>" +
	"    <SCRNAME>ACCOUNT SELECTION</SCRNAME>" +
	"    <ArrayOfADRADDR>" +
	"       <ADRADDR>6544 SW 73 CT</ADRADDR>" +
	"       <ADRADDR>6544 SW 73 CT</ADRADDR>" +
	"       <ADRADDR> 6544 SW 73 CT</ADRADDR>" +
	"    </ArrayOfADRADDR>" +
	"    <ArrayOfADRAPT></ArrayOfADRAPT>" +
	"    <ArrayOfADRFMUN>" +
	"       <ADRFMUN>30</ADRFMUN>" +
	"       <ADRFMUN>30</ADRFMUN>" +
	"       <ADRFMUN>30</ADRFMUN>" +
	"    </ArrayOfADRFMUN>" +
	"    <ArrayOfADRFNUM>" +
	"       <ADRFNUM>40260090440</ADRFNUM>" +
	"       <ADRFNUM>40260090440</ADRFNUM>" +
	"       <ADRFNUM>40260090440</ADRFNUM>" +
	"    </ArrayOfADRFNUM>" +
	"    <ArrayOfADRACC1>" +
	"       <ADRACC1>4</ADRACC1>" +
	"       <ADRACC1>4</ADRACC1>" +
	"       <ADRACC1>1</ADRACC1>" +
	"    </ArrayOfADRACC1>" +
	"    <ArrayOfADRACC2>" +
	"       <ADRACC2>093893</ADRACC2>" +
	"       <ADRACC2>092739</ADRACC2>" +
	"       <ADRACC2>151141</ADRACC2>" +
	"    </ArrayOfADRACC2>" +
	"    <ArrayOfADRACC3>" +
	"       <ADRACC3>7</ADRACC3>" +
	"       <ADRACC3>3</ADRACC3>" +
	"       <ADRACC3>0</ADRACC3>" +
	"    </ArrayOfADRACC3>" +
	"    <ArrayOfADRCODE>" +
	"       <ADRCODE>AC</ADRCODE>" +
	"       <ADRCODE>AR</ADRCODE>" +
	"       <ADRCODE>UH</ADRCODE>" +
	"" +
	"    </ArrayOfADRCODE>" +
	"   <ArrayOfADRST></ArrayOfADRST>" +
	"    <ArrayOfADRNAME>" +
	"       <ADRNAME>MARIA E LOPEZ</ADRNAME>" +
	"       <ADRNAME>JOSE LOPEZ &amp;W MARIA E</ADRNAME>" +
	"       <ADRNAME>JOSE LOPEZ &amp;W MARIA E</ADRNAME>" +
	"    </ArrayOfADRNAME>" +
	"    <SCRMSG>* NO MORE RECORDS, ENTER AN &quot;X&quot; BY THE ADDRESS/ACC</SCRMSG>" +
	"    <ACC1/>" +
	"    <ACC2/>" +
	"    <ACC3/>" +
	"    <TAXUNIT/>" +
	"    <WSTUNIT/>" +
	"    <FEECODE/>" +
	"    <FCDESC/>" +
	"    <CRDATE/>" +
	"    <FOLMUN/>" +
	"    <FOLNUM/>" +
	"    <ACCTYPE/>" +
	"    <OUTSRV/>" +
	"    <SRVSTART/>" +
	"    <SRVDATE/>" +
	"    <ACCST/>" +
	"    <HANDICAP/>" +
	"    <BILLST/>" +
	"    <PENCODE/>" +
	"    <ACCNAME/>" +
	"    <BILLDATE/>" +
	"    <ACCADDR/>" +
	"    <PRAMT/>" +
	"    <ACCAPT/>" +
	"    <ACCZIP/>" +
	"    <PHAREA/>" +
	"    <PHNUM/>" +
	"    <PRTAX/>" +
	"    <LEGAL/>" +
	"    <PRPENDATE/>" +
	"    <OWNAME/>" +
	"    <MTHDELQ/>" +
	"    <PRPENAMT/>" +
	"    <MAILCO/>" +
	"    <PENDATE/>" +
	"    <PENAMT/>" +
	"    <MISCMAIL/>" +
	"    <SRVTAX/>" +
	"    <SRVTAXAMT/>" +
	"    <MAILADDR/>" +
	"    <MAILAPT/>" +
	"    <CURFEEAMT/>" +
	"    <MAILCITY/>" +
	"    <MAILST/>" +
	"    <MAILZIP/>" +
	"    <BADCHKFEE/>" +
	"    <OWNPHAREA/>" +
	"    <OWNPHNUM/>" +
	"    <CLUC/>" +
	"    <PDAMT/>" +
	"    <CANROLL/>" +
	"    <ArrayOfUSIZE></ArrayOfUSIZE>" +
	"    <ArrayOfUNITS></ArrayOfUNITS>" +
	"    <ArrayOfIDNO></ArrayOfIDNO>" +
	"    <OCLNO/>" +
	"    <FEECR/>" +
	"    <BOOK/>" +
	"    <ROUTE/>" +
	"    <DIST/>" +
	"    <REG/>" +
	"    <DELINQ/>" +
	"    <TOTDUE/>" +
	"    <GRTOTDUE/>" +
	"    <JUDGAMT/>" +
	"    <TRIPS/>" +
	"    <EXEMPT/>" +
	"    <BLKAMT/>" +
	"    <XMLCONVRC>2</XMLCONVRC>" +
	"    <RETURNCODE>0</RETURNCODE>" +
	"    <RETURNMSG>MULTIPLE ADDRESSES</RETURNMSG>" +
	"    <PICKUP/>" +
	" </WCSW010Out>" +
	"</WCSW010Response>";

	

}

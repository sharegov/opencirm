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
package org.sharegov.cirm.legacy;

import static org.junit.Assert.fail;
import static org.sharegov.cirm.OWL.and;
import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.objectProperty;
import static org.sharegov.cirm.OWL.owlClass;
import static org.sharegov.cirm.OWL.reasoner;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.utils.RESTClient;

public class ActivityManagerTest
{

	/**
	 * 
	 */
	@Test
	public void testCreateReferralCase()
	{
		ActivityManager m = new ActivityManager();
		OWLClass c = OWL.owlClass(OWL.fullIri("legacy:BULKYTRA"));
		System.out.println(c.getIRI());
		for(OWLNamedIndividual a : m.getActivities(c))
		{
			System.out.println(OWL.toJSON(a));
		}
		
	}
	
	/**
	 * 
	 */
	@Test
	public void testGetActivities()
	{
		ActivityManager m = new ActivityManager();
		OWLClass c = OWL.owlClass(OWL.fullIri("legacy:BULKYTRA"));
		System.out.println(c.getIRI());
		for(OWLNamedIndividual a : m.getActivities(c))
		{
			System.out.println(OWL.toJSON(a));
		}
		
	}

	@Test
	public void testCreateDefaultActivities()
	{
		ActivityManager m = new ActivityManager();
		//OWLClass c = OWLUtils.owlClass(OWLUtils.fullIri("legacy:311APISU"));
		OWLClass c = OWL.owlClass(OWL.fullIri("legacy:RAAM7"));
		BOntology bo = null;
		try
		{
			bo = BOntology.makeNewBusinessObject(c);
			List<CirmMessage> msgs = new ArrayList<CirmMessage>();
			m.createDefaultActivities(c, bo, null,msgs);
			MessageManager.get().sendMessages(msgs);
		}
		catch (OWLOntologyCreationException e)
		{
			e.printStackTrace();
		}
		
		if(bo != null)
		{
			for(OWLAxiom a: bo.getOntology().getAxioms())
			{
				System.out.println(a);
			}
		}
	}
	
	@Test
	public void testCreateActivityWithEmail()
	{
		ActivityManager m = new ActivityManager();
		//OWLClass c = OWLUtils.owlClass(OWLUtils.fullIri("legacy:311APISU"));
		OWLClass c = OWL.owlClass(OWL.fullIri("legacy:RAAM7"));
		BOntology bo = null;
		try
		{
			bo = BOntology.makeNewBusinessObject(c);			
			List<CirmMessage> msgs = new ArrayList<CirmMessage>();
			m.createActivity(OWL.individual("legacy:RAAM7_RAAMOD"), null, "Activity overdue", null, bo, null, null,null, msgs);
			MessageManager.get().sendMessages(msgs);
		}
		catch (OWLOntologyCreationException e)
		{
			e.printStackTrace();
		}
		
		if(bo != null)
		{
			for(OWLAxiom a: bo.getOntology().getAxioms())
			{
				System.out.println(a);
			}
		}
	}
	
	@Test
	public void testCreateTimeBasedActivity()
	{
		ActivityManager m = new ActivityManager();
		//OWLClass c = OWLUtils.owlClass(OWLUtils.fullIri("legacy:311APISU"));
		//OWLClass c = OWLUtils.owlClass(OWLUtils.fullIri("legacy:ASDEATH"));
		//OWLClass c = OWLUtils.owlClass(OWLUtils.fullIri("legacy:ASSTRAY"));
		OWLClass c = OWL.owlClass(OWL.fullIri("legacy:RAAM7"));
		BOntology bo = null;
		try
		{
			bo = BOntology.makeNewBusinessObject(c);
			List<CirmMessage> msgs = new ArrayList<CirmMessage>();
			m.createDefaultActivities(c, bo, null,msgs);
			MessageManager.get().sendMessages(msgs);
		}
		catch (OWLOntologyCreationException e)
		{
			e.printStackTrace();
		}
		
		if(bo != null)
		{
			for(OWLAxiom a: bo.getOntology().getAxioms())
			{
				System.out.println(a);
			}
		}
	}

	@Test
	public void testCreateActivityOWLNamedIndividualBOntology()
	{
		fail("Not yet implemented");
	}

	@Test
	public void testCreateActivityOWLNamedIndividualOWLNamedIndividualBOntology()
	{
		fail("Not yet implemented");
	}

	@Test
	public void testCreateActivityOWLNamedIndividualOWLNamedIndividualStringBOntology()
	{
		fail("Not yet implemented");
	}

	@Test
	public void testUpdateActivityOWLNamedIndividualBOntology()
	{
		fail("Not yet implemented");
	}

	@Test
	public void testUpdateActivityOWLNamedIndividualOWLNamedIndividualBOntology()
	{
		fail("Not yet implemented");
	}

	@Test
	public void testUpdateActivityOWLNamedIndividualOWLNamedIndividualStringBOntology()
	{
		fail("Not yet implemented");
	}

	@Test
	public void testDeleteActivity()
	{
		fail("Not yet implemented");
	}

	@Test
	public void testUpdateTimeBasedActivity()
	{
		fail("Not yet implemented");
	}

	@Test
	public void testDeleteTimeBasedActivity()
	{
		try
		{
			ActivityManager manager = new ActivityManager();
			//manager.removeTimer("");
			
		//	RESTClient.del("http://s0141668:9192/timemachine-0.1/task/cirm_service_hub/"+URLEncoder.encode("iSGtQ7L+GPZr5dSpE6SH6edFTxg=", "UTF-8"));
		//	RESTClient.del("http://s0141668:9192/timemachine-0.1/task/cirm_service_hub/"+URLEncoder.encode("+5jxFSC/pLDNyXIpPJGEup/b1Ow=", "UTF-8"));
//			RESTClient.del("http://s0141668:9192/timemachine-0.1/task/cirm_group/"+URLEncoder.encode("leKjzP7PeUfqNNdvV2kCSTrBbnY=", "UTF-8"));
//			RESTClient.del("http://s0141668:9192/timemachine-0.1/task/cirm_group/"+URLEncoder.encode("zja5g9DYcEMegy2wD6TCEaXq06c=", "UTF-8"));
//			RESTClient.del("http://s0141668:9192/timemachine-0.1/task/cirm_group/"+URLEncoder.encode("7YRvR3do1cgb2xjnzTd7PuisWyg=", "UTF-8"));
//			RESTClient.del("http://s0141668:9192/timemachine-0.1/task/cirm_group/"+URLEncoder.encode("CKcaCgGzhUx6NZo210HQOFNe5Mg=", "UTF-8"));
//			RESTClient.del("http://s0141668:9192/timemachine-0.1/task/cirm_group/"+URLEncoder.encode("gOOI1kTQ6zwV+2Rtn7LHwyAupHI=", "UTF-8"));
//			RESTClient.del("http://s0141668:9192/timemachine-0.1/task/cirm_group/"+URLEncoder.encode("FSrKcHPX02t9JjCmCcHKGJgWHfc=", "UTF-8"));
//			RESTClient.del("http://s0141668:9192/timemachine-0.1/task/cirm_group/"+URLEncoder.encode("GG/8MGmfWjnva65JdjicFXJUPhA=", "UTF-8"));
//			RESTClient.del("http://s0141668:9192/timemachine-0.1/task/cirm_group/"+URLEncoder.encode("UKhHAieGqJsahEsXhk5BEq6HQPA=", "UTF-8"));
//			RESTClient.del("http://s0141668:9192/timemachine-0.1/task/cirm_group/"+URLEncoder.encode("bJ2uU5CibBXYHOT9syqhr+OY9Mw=", "UTF-8"));
//			RESTClient.del("http://s0141668:9192/timemachine-0.1/task/cirm_group/"+URLEncoder.encode("1PrL1dp5qoEItHWMgNCdXyHljV8=", "UTF-8"));

			
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	@Test
	public void testAcceptTimeBasedActivityCallback()
	{
		try
		{
			RESTClient.get("http://OLS00053.miamidade.gov:8182/legacy/bo/110495/activity/ServiceActivity110496/overdue/create/RAAM7_RAAMOD");
		}
		catch (Throwable e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args)
	{
		
		OWLClassExpression q2 = and(owlClass("legacy:ServiceCaseOutcomeTrigger"),
				OWL.has(objectProperty("legacy:hasServiceCase"), individual("legacy:ASBITE")),
				OWL.has(objectProperty("legacy:hasOutcome"), individual("legacy:OUTCOME_ASCRUFUP")),
				OWL.some(objectProperty("legacy:hasLegacyEvent"), owlClass("legacy:CreateServiceCase")));
		Set<OWLNamedIndividual> createCaseTriggers = reasoner().getInstances(q2, false).getFlattened();
		for(OWLNamedIndividual trigger: createCaseTriggers)
		{
			System.out.println(objectProperty(trigger, "legacy:hasOutcome"));
		}
		System.out.println(createCaseTriggers);
//		ActivityManagerTest test = new ActivityManagerTest();
////		//test.testGetActivities();
//		//test.testCreateDefaultActivities();
////		//test.testCreateTimeBasedActivity();
////		//test.testDeleteTimeBasedActivity();
//		test.testAcceptTimeBasedActivityCallback();
//		
		//System.out.println(LegacyEmulator.class.getAnnotation(Path.class).value());
		
		
//		System.out.println(OWLUtils.add(OWLUtils.parseDate("2012-11-21T00:00:00"), 1, true));
		

	}

}

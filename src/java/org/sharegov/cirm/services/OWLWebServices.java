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
package org.sharegov.cirm.services;

import static org.sharegov.cirm.OWL.individual;

import static org.sharegov.cirm.OWL.objectProperties;
import static org.sharegov.cirm.OWL.objectProperty;
import static org.sharegov.cirm.OWL.ontology;
import static org.sharegov.cirm.OWL.owlClass;

import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.sharegov.cirm.OWL;

@Path("services")
public class OWLWebServices {

	@GET
	@Path("/all")
	@Produces("application/json")
	public String getOWLWebServices()
	{
		OWLClass cl = owlClass("WebService");
		Set<OWLIndividual> S = cl.getIndividuals(ontology());
		Json A = Json.array();
		for (OWLIndividual ind : S)
			A.add(ind.toStringID());
		return A.toString();
	}

	@GET
	@Path("/{service}")
	@Produces("application/json")
	public String getOWLWebService(@PathParam("service") String service) {
		OWLClass cl = owlClass("WebService");
		OWLIndividual svc = individual(service);
		OWLDataFactory df = OWL.dataFactory();
		OWLClassAssertionAxiom a = df.getOWLClassAssertionAxiom(cl, svc);
		OWLReasoner r = OWL.reasoner(ontology());
		if(r.isEntailed(a))
		{
			return OWL.toJSON(svc).toString();
		}
		else
			return "{}";
	}

	@GET
	@Path("/{service}/description")
	@Produces("application/json")
	public String getOWLWebServiceDescription(@PathParam("service") String service) {
		OWLNamedIndividual svc = individual(service);
		Set<OWLNamedIndividual> arguments = getArgs(svc);
		Json A = Json.array();
		for (OWLIndividual ind : arguments)
			A.add(OWL.toJSON(ind));
		return A.toString();
	}
	
	@GET
	@Path("/{service}/call")
	@Produces("application/json")
	public String callOWLWebService(@PathParam("service") String service, @QueryParam("argument")
			String[] args) {
		OWLDataFactory df = OWL.dataFactory();
		OWLNamedIndividual svc = individual(service);
		OWLWebServiceCall call = new  OWLWebServiceCall(svc);
		OWLLiteral[] v = new OWLLiteral[args.length];
		for (int i = 0; i < v.length; i++)
		{
			v[i] = df.getOWLLiteral(args[i]);
		}
		try
		{
		v = call.execute(v);
		}catch(Exception e)
		{
			e.printStackTrace();
		}
		Json A = Json.array();
		for (int i = 0; i < v.length; i++)
			A.add(OWL.toJSON(v[i]));
		return A.toString();
	}
	
	
	private Set<OWLNamedIndividual> getArgs(OWLIndividual service)
	{
		OWLDataFactory df = OWL.dataFactory();
		OWLClassExpression paramsQuery = df.getOWLObjectIntersectionOf(
				owlClass("WebArgumentMapping"),
				df.getOWLObjectHasValue(objectProperty("forWebService"), 
							            service)); 
		NodeSet<OWLNamedIndividual> S = OWL.reasoner(ontology()).getInstances(paramsQuery, false);
		OWLNamedIndividual argumentMapping = S.iterator().next().getEntities().iterator().next();
		Set<OWLNamedIndividual> arguments = objectProperties(argumentMapping, "hasArgument");
		return arguments;
	}	
}

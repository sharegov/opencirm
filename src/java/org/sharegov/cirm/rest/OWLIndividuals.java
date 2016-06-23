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
package org.sharegov.cirm.rest;


import static org.sharegov.cirm.OWL.and;
import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.ontology;
import static org.sharegov.cirm.OWL.owlClass;
import static org.sharegov.cirm.OWL.reasoner;
import static org.sharegov.cirm.utils.GenUtils.ko;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLException;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.legacy.Permissions;
import org.sharegov.cirm.legacy.ServiceCaseManager;
import org.sharegov.cirm.owl.OWLSerialEntityCache;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.TraceUtils;

@Path("individuals")
@Produces("application/json")
public class OWLIndividuals extends RestService
{
	/**
	 * <p>
	 * Obtain all instances of a given class.
	 * </p>
	 * @param classname The name of the OWL class. This can be a full IRI or a short name using
	 * one of the ontology prefixes registered with the system.  
	 * @param direct Whether to return instances only from the top-level global ontology (true), or
	 * also from all imported ontologies (false).
	 * @return All instance of the class described by the class expression parameter, serialized into
	 * JSON an array of objects where each object represents a single individual. 
	 */
	@GET
	@Path("/instances/{classname}")
	public Json getOWLClassInstances(@PathParam("classname") String classname, @QueryParam("direct") boolean direct)
	{
		try
		{
			OWLReasoner reasoner = reasoner();
			OWLClassExpression expr = owlClass(classname);
			if (!isClientExempt() && reasoner.getSuperClasses(expr, false).containsEntity(owlClass("Protected")))
				expr = and(expr, Permissions.constrain(individual("BO_View"), getUserActors()));
			else if (!isClientExempt() && !reasoner.getSubClasses(and(expr, owlClass("Protected")), false).isBottomSingleton())
			{
				return ko("Access denied - protected resources could be returned, please split the query.");
			}
			Set<OWLNamedIndividual> S = reasoner().getInstances(expr, direct).getFlattened();
			Json A = Json.array();
			for (OWLIndividual ind : S)
				A.add(OWL.toJSON(ind));
			return A;
		}
		catch (Throwable t)
		{
			TraceUtils.error(t);
			return Json.array();
		}
	}
	
	/**
	 * <p>
	 * Retrieve the active configuration set as identified by the 
	 * <code>ontologyConfigSet</code> startup configuration parameter.  
	 * </p>
	 * 
	 * @return A JSON object where each member of the <code>ConfigSet</code>
	 * is a property with name the "Name" of the member and value the "Value"
	 * of the member. Essentially a modified JSON representation of the config
	 * set which by default would serialized into JSON as an array of 'hasMember' 
	 * properties.
	 * 
	 */
	@GET
	@Path("/predefined/configset")
	public Json getSameAsIndividual() throws OWLException
	{
		OWLNamedIndividual ind = individual(StartUp.config.at("ontologyConfigSet").asString());
		try
		{
			Json el = OWL.toJSON(ontology(), ind);
			Json result = Json.object();
			for (Json x : el.at("hasMember").asJsonList())
			{
				if(x.has("Value")) {
				//System.out.println(x);
						result.set(x.at("Name").asString(), x.at("Value"));
				} else {
					result.set(x.at("Name").asString(), x.at("ValueObj"));
				}
				
			}
			if (!isClientExempt() && 
				reasoner().getTypes(ind, false).containsEntity(OWL.owlClass("Protected")) &&					
				!!Permissions.check(individual("BO_View"), 
									ind, 
									getUserActors()))
				return ko("Permission denied.");
			return result;
		}
		catch (Throwable t)
		{
			t.printStackTrace(System.err);
			return Json.object();
		}		
	}
	
	/**
	 * <p>
	 * Retrieve an individual by its name. The name can be a full IRI or a prefixed short form where
	 * the prefix is one of the prefixes registered with the system.
	 * </p>
	 * @param individualName The name of the individual (e.g. 
	 * <code>http://www.miamidade.gov.ontology#City_of_Miami</code> or <code>legacy:TM15</code>) 
	 * @return The standard JSON representation of the individual.
	 * @throws OWLException
	 */
	@GET
	@Path("/{individual}")
	@Produces("application/json")
	public Json getOWLIndividual(@PathParam("individual") String individualName) throws OWLException
	{
		return getOWLIndividualByName(individualName);		
	}	
	
	public Json getOWLIndividualByName(String individualName) throws OWLException
	{
		try
		{
			OWLNamedIndividual ind = individual(individualName);
			Json el = OWL.toJSON(ontology(), ind);
			if (!isClientExempt() &&
				reasoner().getTypes(ind, false).containsEntity(OWL.owlClass("Protected")) &&
				!Permissions.check(individual("BO_View"), 
									individual(individualName), 
									getUserActors()))
				return ko("Permission denied.");
			return el;
		}
		catch (Throwable t)
		{
			t.printStackTrace(System.err);
			return Json.object();
		}		
	}
	
	/**
	 * Determines if an OWL individual was modified after a specified time.
	 * 
	 * This currently works for service case types, but not yet for other individuals.
	 * Also, currently only local changes that were applied to an sr type after the server was started 
	 * are considered in determining modifiedAfter.
	 * TODO consider changes in persisted managed ontology history
	 *  
	 * @param indPrefixedIri the prefixed IRI of the individual (format "legacy:xxx")
	 * @param timeMs specified time in milliseconds
	 * @return ok with a boolean modifiedAfter property; false means not modified or individual changes not found.
	 * @throws OWLException
	 */
	@GET
	@Path("/{individual}/modifiedAfter/{timeMs}")
	@Produces("application/json")
	public Json isOWLIndividualModifiedAfter(@PathParam("individual") String indPrefixedIri, @PathParam("timeMs") Long timeMs) throws OWLException {
		if (timeMs == null) return GenUtils.ko("timeMs parameter was null");
		if (indPrefixedIri == null || indPrefixedIri.isEmpty()) return  GenUtils.ko("indPrefixedIri was null or empty");
		return GenUtils.ok().set("modifiedAfter", ServiceCaseManager.getInstance().isInvididualModifiedAfter(indPrefixedIri, timeMs));
	}
	
	/**
	 * <p>
	 * Perform a query for individuals in the ontology using a DL class expression. 
	 * </p>
	 * 
	 * @param queryAsString A DL (description logic) class expression using the 
	 * <a href="http://protegewiki.stanford.edu/wiki/DLQueryTab">Manchnester syntax</a>
	 * @return All instance of the class described by the class expression parameter, serialized into
	 * JSON an array of objects where each object represents a single individual. 
	 */
	@GET
	@Path("/")
	public synchronized Json doQuery(@QueryParam("q") String queryAsString)
	{
		try
		{
			OWLOntology ontology = ontology();
			OWLReasoner reasoner = reasoner(ontology);
			//Set<OWLNamedIndividual> userActors = getUserActors();
//			Json result = Json.array();		
			OWLClassExpression expr = OWL.parseDL(queryAsString, ontology);
			Set<OWLNamedIndividual> allAllowed = null;
			if (!isClientExempt() && reasoner.getSuperClasses(expr, false).containsEntity(owlClass("Protected")))
			{
//				queryAsString = Permissions.constrainClause(individual("BO_View"), getUserActors())
//						+ " and " + queryAsString;
				allAllowed = new HashSet<OWLNamedIndividual>();
				Set<OWLNamedIndividual> policies = Permissions.policiesForActors(getUserActors());
				for (OWLNamedIndividual x : policies)
				{
//					System.out.println("Policy: " + x);
					Set<OWLNamedIndividual> policyObjects = OWL.objectProperties(x, "hasObject");					
//					System.out.println("Object: " + policyObjects);
					allAllowed.addAll(policyObjects);
				}
			}
			// The following is not a 100% test for protection. To be 100%, we'd have
			// to check that getInstances returns an empty set rather than getSubClasses, but
			// this will work in practice and it's a hopefully faster test
			else if (!isClientExempt() && !reasoner.getSubClasses(and(expr, owlClass("Protected")), false).isBottomSingleton())
			{
				return ko("Access denied - protected resources could be returned, please split the query.");
			}
			
			//long ss = System.currentTimeMillis();
//			Json j = Refs.owlJsonCache.resolve().set(queryAsString).resolve();
//			
			OWLSerialEntityCache jsonEntities = Refs.owlJsonCache.resolve();
			Json j = Json.array();
			for (OWLNamedIndividual ind : OWL.queryIndividuals(queryAsString))
				if (allAllowed == null || allAllowed.contains(ind))
					j.add(jsonEntities.individual(ind.getIRI()).resolve());
			
			//System.out.println("QUERY TIME for '" + queryAsString + "' -- " + (System.currentTimeMillis() - ss));
			return j;
//			for (OWLNamedIndividual ind : reasoner.getInstances(expr, false).getFlattened())
//				//reasoner.getInstances(expr, false).getFlattened())
//				result.add(OWL.toJSON(ontology, ind));
//			return result;
		}
		catch (Exception ex)
		{
			System.out.println("While get instances for " + queryAsString);
			ex.printStackTrace();
			return ko(ex);
		}
	}
	
	public synchronized Json doInternalQuery (String queryAsString)
	{
		try
		{
			OWLOntology ontology = ontology();
			OWLReasoner reasoner = reasoner(ontology);
			//Set<OWLNamedIndividual> userActors = getUserActors();
//			Json result = Json.array();		
			OWLClassExpression expr = OWL.parseDL(queryAsString, ontology);
			Set<OWLNamedIndividual> allAllowed = null;
			if (!isClientExempt() && reasoner.getSuperClasses(expr, false).containsEntity(owlClass("Protected")))
			{
//				queryAsString = Permissions.constrainClause(individual("BO_View"), getUserActors())
//						+ " and " + queryAsString;
				allAllowed = new HashSet<OWLNamedIndividual>();
				Set<OWLNamedIndividual> policies = Permissions.policiesForActors(getUserActors());
				for (OWLNamedIndividual x : policies)
				{
//					System.out.println("Policy: " + x);
					Set<OWLNamedIndividual> policyObjects = OWL.objectProperties(x, "hasObject");					
//					System.out.println("Object: " + policyObjects);
					allAllowed.addAll(policyObjects);
				}
			}
			// The following is not a 100% test for protection. To be 100%, we'd have
			// to check that getInstances returns an empty set rather than getSubClasses, but
			// this will work in practice and it's a hopefully faster test
			else if (!isClientExempt() && !reasoner.getSubClasses(and(expr, owlClass("Protected")), false).isBottomSingleton())
			{
				throw new IllegalAccessError ("Access denied - protected resources could be returned, please split the query.");
			}
			
			//long ss = System.currentTimeMillis();
//			Json j = Refs.owlJsonCache.resolve().set(queryAsString).resolve();
//			
			OWLSerialEntityCache jsonEntities = Refs.owlJsonCache.resolve();
			Json j = Json.array();
			for (OWLNamedIndividual ind : OWL.queryIndividuals(queryAsString))
				if (allAllowed == null || allAllowed.contains(ind))
					j.add(jsonEntities.individual(ind.getIRI()).resolve());
			
			//System.out.println("QUERY TIME for '" + queryAsString + "' -- " + (System.currentTimeMillis() - ss));
			return j;
//			for (OWLNamedIndividual ind : reasoner.getInstances(expr, false).getFlattened())
//				//reasoner.getInstances(expr, false).getFlattened())
//				result.add(OWL.toJSON(ontology, ind));
//			return result;
		}
		catch (Exception ex)
		{
			System.out.println("While get instances for " + queryAsString);
			ex.printStackTrace();
			return Json.object();
		}
	}

}

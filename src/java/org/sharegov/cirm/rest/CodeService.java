package org.sharegov.cirm.rest;

import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.OWL;

/**
 * The <em>code</em> service provides dynamic JavaScript code, modules or
 * application components, to the client. Those modules are defined in the
 * ontology and their instantiating may be parameterized somehow based on the
 * client's needs.
 * 
 * @author borislav
 *
 */
@Path("code")
public class CodeService extends RestService
{
	@GET
	@Path("/{module}")
	public Response getModule(@PathParam("module") String moduleName)
	{
		Set<OWLNamedIndividual> S = OWL.queryIndividuals(OWL.and(OWL.owlClass("JavaScriptLibrary"), OWL.hasData("hasName", OWL.literal(moduleName))));
		if (!S.isEmpty())
			return Response.ok(OWL.dataProperty(S.iterator().next(), "hasContents").getLiteral(), new MediaType("application", "javascript")).build();
		else
			return Response.status(Response.Status.NOT_FOUND).build();
	}
}
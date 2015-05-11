package org.sharegov.cirm;

import static org.sharegov.cirm.OWL.objectProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLIndividualAxiom;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.owl.Model;

public class BOUtils
{
	public static BOntology addMetaDataAxioms(BOntology bo)
			throws OWLOntologyCreationException
	{
		OWLOntology ontology = bo.getOntology();
		IRI verboseiri = IRI.create(ontology.getOntologyID().getOntologyIRI()
				.toString()
				+ "/verbose");
		OWLOntologyManager manager = Refs.tempOntoManager.resolve();
		OWLOntology result = manager.getOntology(verboseiri);
		if (result != null)
			manager.removeOntology(result);
		result = manager.createOntology(
				IRI.create(ontology.getOntologyID().getOntologyIRI().toString()
						+ "/verbose"), Collections.singleton(ontology));
		OWLDataFactory factory = manager.getOWLDataFactory();
		Set<OWLNamedIndividual> individuals = result
				.getIndividualsInSignature();
		List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
		for (OWLNamedIndividual ind : individuals)
		{
			// Idk if this is the best way to check if an individual is declared
			// in meta.
			// OWLReasoner doesn't provide facilities to retrieve axioms for an
			// individual.
			// and for now we are only interested in adding legacy axioms.

			// If the individual lives in the CiRM namespace, we add all
			// information about it.
			if (OWL.ontology().containsEntityInSignature(ind, true))
			{
				ind = OWL.individual(ind.getIRI());
				for (OWLOntology O : OWL.ontologies())
				{
					for (OWLIndividualAxiom axiom : O
							.getDataPropertyAssertionAxioms(ind))
						changes.add(new AddAxiom(result, axiom));
					for (OWLObjectPropertyAssertionAxiom axiom : O
							.getObjectPropertyAssertionAxioms(ind))
						// I'm not sure why we are skipping those two
						// properties. Perhaps they are not needed
						// but are they harmful? That logic takes away the
						// generality of the method.
						if (!axiom.getProperty().equals(
								objectProperty("legacy:hasLegacyInterface"))
								&& !axiom
										.getProperty()
										.equals(objectProperty("legacy:hasAllowableEvent")))
							changes.add(new AddAxiom(result, axiom));
				}
			}
			else
			{
				// add boid to businessObject in the BOntology
				OWLDataProperty boid = factory.getOWLDataProperty(Model
						.legacy("boid"));
				if (ind.getDataPropertyValues(boid, result).isEmpty())
				{
					Long id = Long.valueOf(bo.getObjectId()); // identifiers.get(ind);
					// 1-15-2013 save the round trip to the DB and grab the id
					// from the onto.
					if (id != null)
						changes.add(new AddAxiom(
								result,
								factory.getOWLDataPropertyAssertionAxiom(
										boid,
										ind,
										factory.getOWLLiteral(
												id.toString(),
												factory.getOWLDatatype(OWL2Datatype.XSD_INT
														.getIRI())))));
				}
			}
		}
		if (changes.size() > 0)
			manager.applyChanges(changes);
		BOntology newBO = new BOntology(result);
		return newBO;
	}
}

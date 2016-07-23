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

import java.util.List;
import java.util.Set;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.semanticweb.owlapi.vocab.OWLFacet;

/**
 * Simple synchronized OwlDataFactory wrapper.
 * 
 * @author Thomas Hilpold
 */
@SuppressWarnings("deprecation")
public class SynchronizedOWLDataFactory implements OWLDataFactory
{
	OWLDataFactory factory;

	public static SynchronizedOWLDataFactory synchronizedFactory(
			OWLDataFactory factory)
	{
		if (factory instanceof SynchronizedOWLDataFactory)
		{
			throw new IllegalStateException(
					"sychronized factory called with already synchronized factory: "
							+ factory);
		}
		return new SynchronizedOWLDataFactory(factory);
	}

	SynchronizedOWLDataFactory(OWLDataFactory factory)
	{
		this.factory = factory;
	}

	public synchronized SWRLRule getSWRLRule(IRI iri,
			Set<? extends SWRLAtom> body, Set<? extends SWRLAtom> head)
	{
		return factory.getSWRLRule(iri, body, head);
	}

	public synchronized OWLClass getOWLThing()
	{
		return factory.getOWLThing();
	}

	public synchronized OWLClass getOWLNothing()
	{
		return factory.getOWLNothing();
	}

	public synchronized SWRLRule getSWRLRule(NodeID nodeID,
			Set<? extends SWRLAtom> body, Set<? extends SWRLAtom> head)
	{
		return factory.getSWRLRule(nodeID, body, head);
	}

	public synchronized OWLObjectProperty getOWLTopObjectProperty()
	{
		return factory.getOWLTopObjectProperty();
	}

	public synchronized OWLDataProperty getOWLTopDataProperty()
	{
		return factory.getOWLTopDataProperty();
	}

	public synchronized OWLObjectProperty getOWLBottomObjectProperty()
	{
		return factory.getOWLBottomObjectProperty();
	}

	public synchronized OWLDataProperty getOWLBottomDataProperty()
	{
		return factory.getOWLBottomDataProperty();
	}

	public synchronized OWLDatatype getTopDatatype()
	{
		return factory.getTopDatatype();
	}

	public synchronized SWRLRule getSWRLRule(Set<? extends SWRLAtom> body,
			Set<? extends SWRLAtom> head)
	{
		return factory.getSWRLRule(body, head);
	}

	public synchronized <E extends OWLEntity> E getOWLEntity(
			EntityType<E> entityType, IRI iri)
	{
		return factory.getOWLEntity(entityType, iri);
	}

	public synchronized SWRLRule getSWRLRule(Set<? extends SWRLAtom> body,
			Set<? extends SWRLAtom> head, Set<OWLAnnotation> annotations)
	{
		return factory.getSWRLRule(body, head, annotations);
	}

	public synchronized OWLClass getOWLClass(IRI iri)
	{
		return factory.getOWLClass(iri);
	}

	public synchronized SWRLClassAtom getSWRLClassAtom(
			OWLClassExpression predicate, SWRLIArgument arg)
	{
		return factory.getSWRLClassAtom(predicate, arg);
	}

	public synchronized OWLClass getOWLClass(String abbreviatedIRI,
			PrefixManager prefixManager)
	{
		return factory.getOWLClass(abbreviatedIRI, prefixManager);
	}

	public synchronized SWRLDataRangeAtom getSWRLDataRangeAtom(
			OWLDataRange predicate, SWRLDArgument arg)
	{
		return factory.getSWRLDataRangeAtom(predicate, arg);
	}

	public synchronized SWRLObjectPropertyAtom getSWRLObjectPropertyAtom(
			OWLObjectPropertyExpression property, SWRLIArgument arg0,
			SWRLIArgument arg1)
	{
		return factory.getSWRLObjectPropertyAtom(property, arg0, arg1);
	}

	public synchronized OWLObjectProperty getOWLObjectProperty(IRI iri)
	{
		return factory.getOWLObjectProperty(iri);
	}

	public synchronized SWRLDataPropertyAtom getSWRLDataPropertyAtom(
			OWLDataPropertyExpression property, SWRLIArgument arg0,
			SWRLDArgument arg1)
	{
		return factory.getSWRLDataPropertyAtom(property, arg0, arg1);
	}

	public synchronized OWLObjectProperty getOWLObjectProperty(
			String abbreviatedIRI, PrefixManager prefixManager)
	{
		return factory.getOWLObjectProperty(abbreviatedIRI, prefixManager);
	}

	public synchronized SWRLBuiltInAtom getSWRLBuiltInAtom(IRI builtInIRI,
			List<SWRLDArgument> args)
	{
		return factory.getSWRLBuiltInAtom(builtInIRI, args);
	}

	public synchronized SWRLVariable getSWRLVariable(IRI var)
	{
		return factory.getSWRLVariable(var);
	}

	public synchronized OWLObjectInverseOf getOWLObjectInverseOf(
			OWLObjectPropertyExpression property)
	{
		return factory.getOWLObjectInverseOf(property);
	}

	public synchronized SWRLIndividualArgument getSWRLIndividualArgument(
			OWLIndividual individual)
	{
		return factory.getSWRLIndividualArgument(individual);
	}

	public synchronized OWLDataProperty getOWLDataProperty(IRI iri)
	{
		return factory.getOWLDataProperty(iri);
	}

	public synchronized SWRLLiteralArgument getSWRLLiteralArgument(
			OWLLiteral literal)
	{
		return factory.getSWRLLiteralArgument(literal);
	}

	public synchronized OWLDataProperty getOWLDataProperty(
			String abbreviatedIRI, PrefixManager prefixManager)
	{
		return factory.getOWLDataProperty(abbreviatedIRI, prefixManager);
	}

	public synchronized SWRLSameIndividualAtom getSWRLSameIndividualAtom(
			SWRLIArgument arg0, SWRLIArgument arg1)
	{
		return factory.getSWRLSameIndividualAtom(arg0, arg1);
	}

	public synchronized SWRLDifferentIndividualsAtom getSWRLDifferentIndividualsAtom(
			SWRLIArgument arg0, SWRLIArgument arg1)
	{
		return factory.getSWRLDifferentIndividualsAtom(arg0, arg1);
	}

	public synchronized OWLNamedIndividual getOWLNamedIndividual(IRI iri)
	{
		return factory.getOWLNamedIndividual(iri);
	}

	public synchronized OWLNamedIndividual getOWLNamedIndividual(
			String abbreviatedIRI, PrefixManager prefixManager)
	{
		return factory.getOWLNamedIndividual(abbreviatedIRI, prefixManager);
	}

	public synchronized OWLAnonymousIndividual getOWLAnonymousIndividual(
			String id)
	{
		return factory.getOWLAnonymousIndividual(id);
	}

	public synchronized OWLAnonymousIndividual getOWLAnonymousIndividual()
	{
		return factory.getOWLAnonymousIndividual();
	}

	public synchronized OWLAnnotationProperty getOWLAnnotationProperty(IRI iri)
	{
		return factory.getOWLAnnotationProperty(iri);
	}

	public synchronized OWLAnnotationProperty getOWLAnnotationProperty(
			String abbreviatedIRI, PrefixManager prefixManager)
	{
		return factory.getOWLAnnotationProperty(abbreviatedIRI, prefixManager);
	}

	public synchronized OWLAnnotationProperty getRDFSLabel()
	{
		return factory.getRDFSLabel();
	}

	public synchronized OWLAnnotationProperty getRDFSComment()
	{
		return factory.getRDFSComment();
	}

	public synchronized OWLAnnotationProperty getRDFSSeeAlso()
	{
		return factory.getRDFSSeeAlso();
	}

	public synchronized OWLAnnotationProperty getRDFSIsDefinedBy()
	{
		return factory.getRDFSIsDefinedBy();
	}

	public synchronized OWLAnnotationProperty getOWLVersionInfo()
	{
		return factory.getOWLVersionInfo();
	}

	public synchronized OWLAnnotationProperty getOWLBackwardCompatibleWith()
	{
		return factory.getOWLBackwardCompatibleWith();
	}

	public synchronized OWLAnnotationProperty getOWLIncompatibleWith()
	{
		return factory.getOWLIncompatibleWith();
	}

	public synchronized OWLAnnotationProperty getOWLDeprecated()
	{
		return factory.getOWLDeprecated();
	}

	public synchronized OWLDatatype getRDFPlainLiteral()
	{
		return factory.getRDFPlainLiteral();
	}

	public synchronized OWLDatatype getOWLDatatype(IRI iri)
	{
		return factory.getOWLDatatype(iri);
	}

	public synchronized OWLDatatype getOWLDatatype(String abbreviatedIRI,
			PrefixManager prefixManager)
	{
		return factory.getOWLDatatype(abbreviatedIRI, prefixManager);
	}

	public synchronized OWLDatatype getIntegerOWLDatatype()
	{
		return factory.getIntegerOWLDatatype();
	}

	public synchronized OWLDatatype getFloatOWLDatatype()
	{
		return factory.getFloatOWLDatatype();
	}

	public synchronized OWLDatatype getDoubleOWLDatatype()
	{
		return factory.getDoubleOWLDatatype();
	}

	public synchronized OWLDatatype getBooleanOWLDatatype()
	{
		return factory.getBooleanOWLDatatype();
	}

	public synchronized OWLLiteral getOWLLiteral(String lexicalValue,
			OWLDatatype datatype)
	{
		return factory.getOWLLiteral(lexicalValue, datatype);
	}

	public synchronized OWLLiteral getOWLLiteral(String lexicalValue,
			OWL2Datatype datatype)
	{
		return factory.getOWLLiteral(lexicalValue, datatype);
	}

	public synchronized OWLLiteral getOWLLiteral(int value)
	{
		return factory.getOWLLiteral(value);
	}

	public synchronized OWLLiteral getOWLLiteral(double value)
	{
		return factory.getOWLLiteral(value);
	}

	public synchronized OWLLiteral getOWLLiteral(boolean value)
	{
		return factory.getOWLLiteral(value);
	}

	public synchronized OWLLiteral getOWLLiteral(float value)
	{
		return factory.getOWLLiteral(value);
	}

	public synchronized OWLLiteral getOWLLiteral(String value)
	{
		return factory.getOWLLiteral(value);
	}

	public synchronized OWLLiteral getOWLLiteral(String literal, String lang)
	{
		return factory.getOWLLiteral(literal, lang);
	}

	public synchronized OWLLiteral getOWLTypedLiteral(String literal,
			OWLDatatype datatype)
	{
		return factory.getOWLTypedLiteral(literal, datatype);
	}

	public synchronized OWLLiteral getOWLTypedLiteral(String literal,
			OWL2Datatype datatype)
	{
		return factory.getOWLTypedLiteral(literal, datatype);
	}

	public synchronized OWLLiteral getOWLTypedLiteral(int value)
	{
		return factory.getOWLTypedLiteral(value);
	}

	public synchronized OWLLiteral getOWLTypedLiteral(double value)
	{
		return factory.getOWLTypedLiteral(value);
	}

	public synchronized OWLLiteral getOWLTypedLiteral(boolean value)
	{
		return factory.getOWLTypedLiteral(value);
	}

	public synchronized OWLLiteral getOWLTypedLiteral(float value)
	{
		return factory.getOWLTypedLiteral(value);
	}

	public synchronized OWLLiteral getOWLTypedLiteral(String value)
	{
		return factory.getOWLTypedLiteral(value);
	}

	public synchronized OWLLiteral getOWLStringLiteral(String literal,
			String lang)
	{
		return factory.getOWLStringLiteral(literal, lang);
	}

	public synchronized OWLLiteral getOWLStringLiteral(String literal)
	{
		return factory.getOWLStringLiteral(literal);
	}

	public synchronized OWLDataOneOf getOWLDataOneOf(
			Set<? extends OWLLiteral> values)
	{
		return factory.getOWLDataOneOf(values);
	}

	public synchronized OWLDataOneOf getOWLDataOneOf(OWLLiteral... values)
	{
		return factory.getOWLDataOneOf(values);
	}

	public synchronized OWLDataComplementOf getOWLDataComplementOf(
			OWLDataRange dataRange)
	{
		return factory.getOWLDataComplementOf(dataRange);
	}

	public synchronized OWLDatatypeRestriction getOWLDatatypeRestriction(
			OWLDatatype dataRange, Set<OWLFacetRestriction> facetRestrictions)
	{
		return factory.getOWLDatatypeRestriction(dataRange, facetRestrictions);
	}

	public synchronized OWLDatatypeRestriction getOWLDatatypeRestriction(
			OWLDatatype dataRange, OWLFacet facet, OWLLiteral typedLiteral)
	{
		return factory
				.getOWLDatatypeRestriction(dataRange, facet, typedLiteral);
	}

	public synchronized OWLDatatypeRestriction getOWLDatatypeRestriction(
			OWLDatatype dataRange, OWLFacetRestriction... facetRestrictions)
	{
		return factory.getOWLDatatypeRestriction(dataRange, facetRestrictions);
	}

	public synchronized OWLDatatypeRestriction getOWLDatatypeMinInclusiveRestriction(
			int minInclusive)
	{
		return factory.getOWLDatatypeMinInclusiveRestriction(minInclusive);
	}

	public synchronized OWLDatatypeRestriction getOWLDatatypeMaxInclusiveRestriction(
			int maxInclusive)
	{
		return factory.getOWLDatatypeMaxInclusiveRestriction(maxInclusive);
	}

	public synchronized OWLDatatypeRestriction getOWLDatatypeMinMaxInclusiveRestriction(
			int minInclusive, int maxInclusive)
	{
		return factory.getOWLDatatypeMinMaxInclusiveRestriction(minInclusive,
				maxInclusive);
	}

	public synchronized OWLDatatypeRestriction getOWLDatatypeMinExclusiveRestriction(
			int minExclusive)
	{
		return factory.getOWLDatatypeMinExclusiveRestriction(minExclusive);
	}

	public synchronized OWLDatatypeRestriction getOWLDatatypeMaxExclusiveRestriction(
			int maxExclusive)
	{
		return factory.getOWLDatatypeMaxExclusiveRestriction(maxExclusive);
	}

	public synchronized OWLDatatypeRestriction getOWLDatatypeMinMaxExclusiveRestriction(
			int minExclusive, int maxExclusive)
	{
		return factory.getOWLDatatypeMinMaxExclusiveRestriction(minExclusive,
				maxExclusive);
	}

	public synchronized OWLDatatypeRestriction getOWLDatatypeMinInclusiveRestriction(
			double minInclusive)
	{
		return factory.getOWLDatatypeMinInclusiveRestriction(minInclusive);
	}

	public synchronized OWLDatatypeRestriction getOWLDatatypeMaxInclusiveRestriction(
			double maxInclusive)
	{
		return factory.getOWLDatatypeMaxInclusiveRestriction(maxInclusive);
	}

	public synchronized OWLDatatypeRestriction getOWLDatatypeMinMaxInclusiveRestriction(
			double minInclusive, double maxInclusive)
	{
		return factory.getOWLDatatypeMinMaxInclusiveRestriction(minInclusive,
				maxInclusive);
	}

	public synchronized OWLDatatypeRestriction getOWLDatatypeMinExclusiveRestriction(
			double minExclusive)
	{
		return factory.getOWLDatatypeMinExclusiveRestriction(minExclusive);
	}

	public synchronized OWLDatatypeRestriction getOWLDatatypeMaxExclusiveRestriction(
			double maxExclusive)
	{
		return factory.getOWLDatatypeMaxExclusiveRestriction(maxExclusive);
	}

	public synchronized OWLDatatypeRestriction getOWLDatatypeMinMaxExclusiveRestriction(
			double minExclusive, double maxExclusive)
	{
		return factory.getOWLDatatypeMinMaxExclusiveRestriction(minExclusive,
				maxExclusive);
	}

	public synchronized OWLFacetRestriction getOWLFacetRestriction(
			OWLFacet facet, OWLLiteral facetValue)
	{
		return factory.getOWLFacetRestriction(facet, facetValue);
	}

	public synchronized OWLFacetRestriction getOWLFacetRestriction(
			OWLFacet facet, int facetValue)
	{
		return factory.getOWLFacetRestriction(facet, facetValue);
	}

	public synchronized OWLFacetRestriction getOWLFacetRestriction(
			OWLFacet facet, double facetValue)
	{
		return factory.getOWLFacetRestriction(facet, facetValue);
	}

	public synchronized OWLFacetRestriction getOWLFacetRestriction(
			OWLFacet facet, float facetValue)
	{
		return factory.getOWLFacetRestriction(facet, facetValue);
	}

	public synchronized OWLDataUnionOf getOWLDataUnionOf(
			Set<? extends OWLDataRange> dataRanges)
	{
		return factory.getOWLDataUnionOf(dataRanges);
	}

	public synchronized OWLDataUnionOf getOWLDataUnionOf(
			OWLDataRange... dataRanges)
	{
		return factory.getOWLDataUnionOf(dataRanges);
	}

	public synchronized OWLDataIntersectionOf getOWLDataIntersectionOf(
			Set<? extends OWLDataRange> dataRanges)
	{
		return factory.getOWLDataIntersectionOf(dataRanges);
	}

	public synchronized OWLDataIntersectionOf getOWLDataIntersectionOf(
			OWLDataRange... dataRanges)
	{
		return factory.getOWLDataIntersectionOf(dataRanges);
	}

	public synchronized OWLObjectIntersectionOf getOWLObjectIntersectionOf(
			Set<? extends OWLClassExpression> operands)
	{
		return factory.getOWLObjectIntersectionOf(operands);
	}

	public synchronized OWLObjectIntersectionOf getOWLObjectIntersectionOf(
			OWLClassExpression... operands)
	{
		return factory.getOWLObjectIntersectionOf(operands);
	}

	public synchronized OWLDataSomeValuesFrom getOWLDataSomeValuesFrom(
			OWLDataPropertyExpression property, OWLDataRange dataRange)
	{
		return factory.getOWLDataSomeValuesFrom(property, dataRange);
	}

	public synchronized OWLDataAllValuesFrom getOWLDataAllValuesFrom(
			OWLDataPropertyExpression property, OWLDataRange dataRange)
	{
		return factory.getOWLDataAllValuesFrom(property, dataRange);
	}

	public synchronized OWLDataExactCardinality getOWLDataExactCardinality(
			int cardinality, OWLDataPropertyExpression property)
	{
		return factory.getOWLDataExactCardinality(cardinality, property);
	}

	public synchronized OWLDataExactCardinality getOWLDataExactCardinality(
			int cardinality, OWLDataPropertyExpression property,
			OWLDataRange dataRange)
	{
		return factory.getOWLDataExactCardinality(cardinality, property,
				dataRange);
	}

	public synchronized OWLDataMaxCardinality getOWLDataMaxCardinality(
			int cardinality, OWLDataPropertyExpression property)
	{
		return factory.getOWLDataMaxCardinality(cardinality, property);
	}

	public synchronized OWLDataMaxCardinality getOWLDataMaxCardinality(
			int cardinality, OWLDataPropertyExpression property,
			OWLDataRange dataRange)
	{
		return factory.getOWLDataMaxCardinality(cardinality, property,
				dataRange);
	}

	public synchronized OWLDataMinCardinality getOWLDataMinCardinality(
			int cardinality, OWLDataPropertyExpression property)
	{
		return factory.getOWLDataMinCardinality(cardinality, property);
	}

	public synchronized OWLDataMinCardinality getOWLDataMinCardinality(
			int cardinality, OWLDataPropertyExpression property,
			OWLDataRange dataRange)
	{
		return factory.getOWLDataMinCardinality(cardinality, property,
				dataRange);
	}

	public synchronized OWLDataHasValue getOWLDataHasValue(
			OWLDataPropertyExpression property, OWLLiteral value)
	{
		return factory.getOWLDataHasValue(property, value);
	}

	public synchronized OWLObjectComplementOf getOWLObjectComplementOf(
			OWLClassExpression operand)
	{
		return factory.getOWLObjectComplementOf(operand);
	}

	public synchronized OWLObjectOneOf getOWLObjectOneOf(
			Set<? extends OWLIndividual> values)
	{
		return factory.getOWLObjectOneOf(values);
	}

	public synchronized OWLObjectOneOf getOWLObjectOneOf(
			OWLIndividual... individuals)
	{
		return factory.getOWLObjectOneOf(individuals);
	}

	public synchronized OWLObjectAllValuesFrom getOWLObjectAllValuesFrom(
			OWLObjectPropertyExpression property,
			OWLClassExpression classExpression)
	{
		return factory.getOWLObjectAllValuesFrom(property, classExpression);
	}

	public synchronized OWLObjectSomeValuesFrom getOWLObjectSomeValuesFrom(
			OWLObjectPropertyExpression property,
			OWLClassExpression classExpression)
	{
		return factory.getOWLObjectSomeValuesFrom(property, classExpression);
	}

	public synchronized OWLObjectExactCardinality getOWLObjectExactCardinality(
			int cardinality, OWLObjectPropertyExpression property)
	{
		return factory.getOWLObjectExactCardinality(cardinality, property);
	}

	public synchronized OWLObjectExactCardinality getOWLObjectExactCardinality(
			int cardinality, OWLObjectPropertyExpression property,
			OWLClassExpression classExpression)
	{
		return factory.getOWLObjectExactCardinality(cardinality, property,
				classExpression);
	}

	public synchronized OWLObjectMinCardinality getOWLObjectMinCardinality(
			int cardinality, OWLObjectPropertyExpression property)
	{
		return factory.getOWLObjectMinCardinality(cardinality, property);
	}

	public synchronized OWLObjectMinCardinality getOWLObjectMinCardinality(
			int cardinality, OWLObjectPropertyExpression property,
			OWLClassExpression classExpression)
	{
		return factory.getOWLObjectMinCardinality(cardinality, property,
				classExpression);
	}

	public synchronized OWLObjectMaxCardinality getOWLObjectMaxCardinality(
			int cardinality, OWLObjectPropertyExpression property)
	{
		return factory.getOWLObjectMaxCardinality(cardinality, property);
	}

	public synchronized OWLObjectMaxCardinality getOWLObjectMaxCardinality(
			int cardinality, OWLObjectPropertyExpression property,
			OWLClassExpression classExpression)
	{
		return factory.getOWLObjectMaxCardinality(cardinality, property,
				classExpression);
	}

	public synchronized OWLObjectHasSelf getOWLObjectHasSelf(
			OWLObjectPropertyExpression property)
	{
		return factory.getOWLObjectHasSelf(property);
	}

	public synchronized OWLObjectHasValue getOWLObjectHasValue(
			OWLObjectPropertyExpression property, OWLIndividual individual)
	{
		return factory.getOWLObjectHasValue(property, individual);
	}

	public synchronized OWLObjectUnionOf getOWLObjectUnionOf(
			Set<? extends OWLClassExpression> operands)
	{
		return factory.getOWLObjectUnionOf(operands);
	}

	public synchronized OWLObjectUnionOf getOWLObjectUnionOf(
			OWLClassExpression... operands)
	{
		return factory.getOWLObjectUnionOf(operands);
	}

	public synchronized OWLDeclarationAxiom getOWLDeclarationAxiom(
			OWLEntity owlEntity)
	{
		return factory.getOWLDeclarationAxiom(owlEntity);
	}

	public synchronized OWLDeclarationAxiom getOWLDeclarationAxiom(
			OWLEntity owlEntity, Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLDeclarationAxiom(owlEntity, annotations);
	}

	public synchronized OWLSubClassOfAxiom getOWLSubClassOfAxiom(
			OWLClassExpression subClass, OWLClassExpression superClass)
	{
		return factory.getOWLSubClassOfAxiom(subClass, superClass);
	}

	public synchronized OWLSubClassOfAxiom getOWLSubClassOfAxiom(
			OWLClassExpression subClass, OWLClassExpression superClass,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLSubClassOfAxiom(subClass, superClass, annotations);
	}

	public synchronized OWLEquivalentClassesAxiom getOWLEquivalentClassesAxiom(
			Set<? extends OWLClassExpression> classExpressions)
	{
		return factory.getOWLEquivalentClassesAxiom(classExpressions);
	}

	public synchronized OWLEquivalentClassesAxiom getOWLEquivalentClassesAxiom(
			Set<? extends OWLClassExpression> classExpressions,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLEquivalentClassesAxiom(classExpressions,
				annotations);
	}

	public synchronized OWLEquivalentClassesAxiom getOWLEquivalentClassesAxiom(
			OWLClassExpression... classExpressions)
	{
		return factory.getOWLEquivalentClassesAxiom(classExpressions);
	}

	public synchronized OWLEquivalentClassesAxiom getOWLEquivalentClassesAxiom(
			OWLClassExpression clsA, OWLClassExpression clsB)
	{
		return factory.getOWLEquivalentClassesAxiom(clsA, clsB);
	}

	public synchronized OWLEquivalentClassesAxiom getOWLEquivalentClassesAxiom(
			OWLClassExpression clsA, OWLClassExpression clsB,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLEquivalentClassesAxiom(clsA, clsB, annotations);
	}

	public synchronized OWLDisjointClassesAxiom getOWLDisjointClassesAxiom(
			Set<? extends OWLClassExpression> classExpressions)
	{
		return factory.getOWLDisjointClassesAxiom(classExpressions);
	}

	public synchronized OWLDisjointClassesAxiom getOWLDisjointClassesAxiom(
			OWLClassExpression... classExpressions)
	{
		return factory.getOWLDisjointClassesAxiom(classExpressions);
	}

	public synchronized OWLDisjointClassesAxiom getOWLDisjointClassesAxiom(
			Set<? extends OWLClassExpression> classExpressions,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory
				.getOWLDisjointClassesAxiom(classExpressions, annotations);
	}

	public synchronized OWLDisjointUnionAxiom getOWLDisjointUnionAxiom(
			OWLClass owlClass,
			Set<? extends OWLClassExpression> classExpressions)
	{
		return factory.getOWLDisjointUnionAxiom(owlClass, classExpressions);
	}

	public synchronized OWLDisjointUnionAxiom getOWLDisjointUnionAxiom(
			OWLClass owlClass,
			Set<? extends OWLClassExpression> classExpressions,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLDisjointUnionAxiom(owlClass, classExpressions,
				annotations);
	}

	public synchronized OWLSubObjectPropertyOfAxiom getOWLSubObjectPropertyOfAxiom(
			OWLObjectPropertyExpression subProperty,
			OWLObjectPropertyExpression superProperty)
	{
		return factory.getOWLSubObjectPropertyOfAxiom(subProperty,
				superProperty);
	}

	public synchronized OWLSubObjectPropertyOfAxiom getOWLSubObjectPropertyOfAxiom(
			OWLObjectPropertyExpression subProperty,
			OWLObjectPropertyExpression superProperty,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLSubObjectPropertyOfAxiom(subProperty,
				superProperty, annotations);
	}

	public synchronized OWLSubPropertyChainOfAxiom getOWLSubPropertyChainOfAxiom(
			List<? extends OWLObjectPropertyExpression> chain,
			OWLObjectPropertyExpression superProperty)
	{
		return factory.getOWLSubPropertyChainOfAxiom(chain, superProperty);
	}

	public synchronized OWLSubPropertyChainOfAxiom getOWLSubPropertyChainOfAxiom(
			List<? extends OWLObjectPropertyExpression> chain,
			OWLObjectPropertyExpression superProperty,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLSubPropertyChainOfAxiom(chain, superProperty,
				annotations);
	}

	public synchronized OWLEquivalentObjectPropertiesAxiom getOWLEquivalentObjectPropertiesAxiom(
			Set<? extends OWLObjectPropertyExpression> properties)
	{
		return factory.getOWLEquivalentObjectPropertiesAxiom(properties);
	}

	public synchronized OWLEquivalentObjectPropertiesAxiom getOWLEquivalentObjectPropertiesAxiom(
			Set<? extends OWLObjectPropertyExpression> properties,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLEquivalentObjectPropertiesAxiom(properties,
				annotations);
	}

	public synchronized OWLEquivalentObjectPropertiesAxiom getOWLEquivalentObjectPropertiesAxiom(
			OWLObjectPropertyExpression... properties)
	{
		return factory.getOWLEquivalentObjectPropertiesAxiom(properties);
	}

	public synchronized OWLEquivalentObjectPropertiesAxiom getOWLEquivalentObjectPropertiesAxiom(
			OWLObjectPropertyExpression propertyA,
			OWLObjectPropertyExpression propertyB)
	{
		return factory.getOWLEquivalentObjectPropertiesAxiom(propertyA,
				propertyB);
	}

	public synchronized OWLEquivalentObjectPropertiesAxiom getOWLEquivalentObjectPropertiesAxiom(
			OWLObjectPropertyExpression propertyA,
			OWLObjectPropertyExpression propertyB,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLEquivalentObjectPropertiesAxiom(propertyA,
				propertyB, annotations);
	}

	public synchronized OWLDisjointObjectPropertiesAxiom getOWLDisjointObjectPropertiesAxiom(
			Set<? extends OWLObjectPropertyExpression> properties)
	{
		return factory.getOWLDisjointObjectPropertiesAxiom(properties);
	}

	public synchronized OWLDisjointObjectPropertiesAxiom getOWLDisjointObjectPropertiesAxiom(
			OWLObjectPropertyExpression... properties)
	{
		return factory.getOWLDisjointObjectPropertiesAxiom(properties);
	}

	public synchronized OWLDisjointObjectPropertiesAxiom getOWLDisjointObjectPropertiesAxiom(
			Set<? extends OWLObjectPropertyExpression> properties,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLDisjointObjectPropertiesAxiom(properties,
				annotations);
	}

	public synchronized OWLInverseObjectPropertiesAxiom getOWLInverseObjectPropertiesAxiom(
			OWLObjectPropertyExpression forwardProperty,
			OWLObjectPropertyExpression inverseProperty)
	{
		return factory.getOWLInverseObjectPropertiesAxiom(forwardProperty,
				inverseProperty);
	}

	public synchronized OWLInverseObjectPropertiesAxiom getOWLInverseObjectPropertiesAxiom(
			OWLObjectPropertyExpression forwardProperty,
			OWLObjectPropertyExpression inverseProperty,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLInverseObjectPropertiesAxiom(forwardProperty,
				inverseProperty, annotations);
	}

	public synchronized OWLObjectPropertyDomainAxiom getOWLObjectPropertyDomainAxiom(
			OWLObjectPropertyExpression property,
			OWLClassExpression classExpression)
	{
		return factory.getOWLObjectPropertyDomainAxiom(property,
				classExpression);
	}

	public synchronized OWLObjectPropertyDomainAxiom getOWLObjectPropertyDomainAxiom(
			OWLObjectPropertyExpression property,
			OWLClassExpression classExpression,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLObjectPropertyDomainAxiom(property,
				classExpression, annotations);
	}

	public synchronized OWLObjectPropertyRangeAxiom getOWLObjectPropertyRangeAxiom(
			OWLObjectPropertyExpression property, OWLClassExpression range)
	{
		return factory.getOWLObjectPropertyRangeAxiom(property, range);
	}

	public synchronized OWLObjectPropertyRangeAxiom getOWLObjectPropertyRangeAxiom(
			OWLObjectPropertyExpression property, OWLClassExpression range,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLObjectPropertyRangeAxiom(property, range,
				annotations);
	}

	public synchronized OWLFunctionalObjectPropertyAxiom getOWLFunctionalObjectPropertyAxiom(
			OWLObjectPropertyExpression property)
	{
		return factory.getOWLFunctionalObjectPropertyAxiom(property);
	}

	public synchronized OWLFunctionalObjectPropertyAxiom getOWLFunctionalObjectPropertyAxiom(
			OWLObjectPropertyExpression property,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLFunctionalObjectPropertyAxiom(property,
				annotations);
	}

	public synchronized OWLInverseFunctionalObjectPropertyAxiom getOWLInverseFunctionalObjectPropertyAxiom(
			OWLObjectPropertyExpression property)
	{
		return factory.getOWLInverseFunctionalObjectPropertyAxiom(property);
	}

	public synchronized OWLInverseFunctionalObjectPropertyAxiom getOWLInverseFunctionalObjectPropertyAxiom(
			OWLObjectPropertyExpression property,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLInverseFunctionalObjectPropertyAxiom(property,
				annotations);
	}

	public synchronized OWLReflexiveObjectPropertyAxiom getOWLReflexiveObjectPropertyAxiom(
			OWLObjectPropertyExpression property)
	{
		return factory.getOWLReflexiveObjectPropertyAxiom(property);
	}

	public synchronized OWLReflexiveObjectPropertyAxiom getOWLReflexiveObjectPropertyAxiom(
			OWLObjectPropertyExpression property,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory
				.getOWLReflexiveObjectPropertyAxiom(property, annotations);
	}

	public synchronized OWLIrreflexiveObjectPropertyAxiom getOWLIrreflexiveObjectPropertyAxiom(
			OWLObjectPropertyExpression property)
	{
		return factory.getOWLIrreflexiveObjectPropertyAxiom(property);
	}

	public synchronized OWLIrreflexiveObjectPropertyAxiom getOWLIrreflexiveObjectPropertyAxiom(
			OWLObjectPropertyExpression property,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLIrreflexiveObjectPropertyAxiom(property,
				annotations);
	}

	public synchronized OWLSymmetricObjectPropertyAxiom getOWLSymmetricObjectPropertyAxiom(
			OWLObjectPropertyExpression property)
	{
		return factory.getOWLSymmetricObjectPropertyAxiom(property);
	}

	public synchronized OWLSymmetricObjectPropertyAxiom getOWLSymmetricObjectPropertyAxiom(
			OWLObjectPropertyExpression property,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory
				.getOWLSymmetricObjectPropertyAxiom(property, annotations);
	}

	public synchronized OWLAsymmetricObjectPropertyAxiom getOWLAsymmetricObjectPropertyAxiom(
			OWLObjectPropertyExpression propertyExpression)
	{
		return factory.getOWLAsymmetricObjectPropertyAxiom(propertyExpression);
	}

	public synchronized OWLAsymmetricObjectPropertyAxiom getOWLAsymmetricObjectPropertyAxiom(
			OWLObjectPropertyExpression propertyExpression,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLAsymmetricObjectPropertyAxiom(propertyExpression,
				annotations);
	}

	public synchronized OWLTransitiveObjectPropertyAxiom getOWLTransitiveObjectPropertyAxiom(
			OWLObjectPropertyExpression property)
	{
		return factory.getOWLTransitiveObjectPropertyAxiom(property);
	}

	public synchronized OWLTransitiveObjectPropertyAxiom getOWLTransitiveObjectPropertyAxiom(
			OWLObjectPropertyExpression property,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLTransitiveObjectPropertyAxiom(property,
				annotations);
	}

	public synchronized OWLSubDataPropertyOfAxiom getOWLSubDataPropertyOfAxiom(
			OWLDataPropertyExpression subProperty,
			OWLDataPropertyExpression superProperty)
	{
		return factory.getOWLSubDataPropertyOfAxiom(subProperty, superProperty);
	}

	public synchronized OWLSubDataPropertyOfAxiom getOWLSubDataPropertyOfAxiom(
			OWLDataPropertyExpression subProperty,
			OWLDataPropertyExpression superProperty,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLSubDataPropertyOfAxiom(subProperty, superProperty,
				annotations);
	}

	public synchronized OWLEquivalentDataPropertiesAxiom getOWLEquivalentDataPropertiesAxiom(
			Set<? extends OWLDataPropertyExpression> properties)
	{
		return factory.getOWLEquivalentDataPropertiesAxiom(properties);
	}

	public synchronized OWLEquivalentDataPropertiesAxiom getOWLEquivalentDataPropertiesAxiom(
			Set<? extends OWLDataPropertyExpression> properties,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLEquivalentDataPropertiesAxiom(properties,
				annotations);
	}

	public synchronized OWLEquivalentDataPropertiesAxiom getOWLEquivalentDataPropertiesAxiom(
			OWLDataPropertyExpression... properties)
	{
		return factory.getOWLEquivalentDataPropertiesAxiom(properties);
	}

	public synchronized OWLEquivalentDataPropertiesAxiom getOWLEquivalentDataPropertiesAxiom(
			OWLDataPropertyExpression propertyA,
			OWLDataPropertyExpression propertyB)
	{
		return factory
				.getOWLEquivalentDataPropertiesAxiom(propertyA, propertyB);
	}

	public synchronized OWLEquivalentDataPropertiesAxiom getOWLEquivalentDataPropertiesAxiom(
			OWLDataPropertyExpression propertyA,
			OWLDataPropertyExpression propertyB,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLEquivalentDataPropertiesAxiom(propertyA,
				propertyB, annotations);
	}

	public synchronized OWLDisjointDataPropertiesAxiom getOWLDisjointDataPropertiesAxiom(
			OWLDataPropertyExpression... dataProperties)
	{
		return factory.getOWLDisjointDataPropertiesAxiom(dataProperties);
	}

	public synchronized OWLDisjointDataPropertiesAxiom getOWLDisjointDataPropertiesAxiom(
			Set<? extends OWLDataPropertyExpression> properties)
	{
		return factory.getOWLDisjointDataPropertiesAxiom(properties);
	}

	public synchronized OWLDisjointDataPropertiesAxiom getOWLDisjointDataPropertiesAxiom(
			Set<? extends OWLDataPropertyExpression> properties,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLDisjointDataPropertiesAxiom(properties,
				annotations);
	}

	public synchronized OWLDataPropertyDomainAxiom getOWLDataPropertyDomainAxiom(
			OWLDataPropertyExpression property, OWLClassExpression domain)
	{
		return factory.getOWLDataPropertyDomainAxiom(property, domain);
	}

	public synchronized OWLDataPropertyDomainAxiom getOWLDataPropertyDomainAxiom(
			OWLDataPropertyExpression property, OWLClassExpression domain,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLDataPropertyDomainAxiom(property, domain,
				annotations);
	}

	public synchronized OWLDataPropertyRangeAxiom getOWLDataPropertyRangeAxiom(
			OWLDataPropertyExpression property, OWLDataRange owlDataRange)
	{
		return factory.getOWLDataPropertyRangeAxiom(property, owlDataRange);
	}

	public synchronized OWLDataPropertyRangeAxiom getOWLDataPropertyRangeAxiom(
			OWLDataPropertyExpression property, OWLDataRange owlDataRange,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLDataPropertyRangeAxiom(property, owlDataRange,
				annotations);
	}

	public synchronized OWLFunctionalDataPropertyAxiom getOWLFunctionalDataPropertyAxiom(
			OWLDataPropertyExpression property)
	{
		return factory.getOWLFunctionalDataPropertyAxiom(property);
	}

	public synchronized OWLFunctionalDataPropertyAxiom getOWLFunctionalDataPropertyAxiom(
			OWLDataPropertyExpression property,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLFunctionalDataPropertyAxiom(property, annotations);
	}

	public synchronized OWLHasKeyAxiom getOWLHasKeyAxiom(OWLClassExpression ce,
			Set<? extends OWLPropertyExpression<?, ?>> properties)
	{
		return factory.getOWLHasKeyAxiom(ce, properties);
	}

	public synchronized OWLHasKeyAxiom getOWLHasKeyAxiom(OWLClassExpression ce,
			OWLPropertyExpression<?, ?>... properties)
	{
		return factory.getOWLHasKeyAxiom(ce, properties);
	}

	public synchronized OWLHasKeyAxiom getOWLHasKeyAxiom(OWLClassExpression ce,
			Set<? extends OWLPropertyExpression<?, ?>> objectProperties,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLHasKeyAxiom(ce, objectProperties, annotations);
	}

	public synchronized OWLDatatypeDefinitionAxiom getOWLDatatypeDefinitionAxiom(
			OWLDatatype datatype, OWLDataRange dataRange)
	{
		return factory.getOWLDatatypeDefinitionAxiom(datatype, dataRange);
	}

	public synchronized OWLDatatypeDefinitionAxiom getOWLDatatypeDefinitionAxiom(
			OWLDatatype datatype, OWLDataRange dataRange,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLDatatypeDefinitionAxiom(datatype, dataRange,
				annotations);
	}

	public synchronized OWLSameIndividualAxiom getOWLSameIndividualAxiom(
			Set<? extends OWLIndividual> individuals)
	{
		return factory.getOWLSameIndividualAxiom(individuals);
	}

	public synchronized OWLSameIndividualAxiom getOWLSameIndividualAxiom(
			OWLIndividual... individual)
	{
		return factory.getOWLSameIndividualAxiom(individual);
	}

	public synchronized OWLSameIndividualAxiom getOWLSameIndividualAxiom(
			Set<? extends OWLIndividual> individuals,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLSameIndividualAxiom(individuals, annotations);
	}

	public synchronized OWLDifferentIndividualsAxiom getOWLDifferentIndividualsAxiom(
			Set<? extends OWLIndividual> individuals)
	{
		return factory.getOWLDifferentIndividualsAxiom(individuals);
	}

	public synchronized OWLDifferentIndividualsAxiom getOWLDifferentIndividualsAxiom(
			OWLIndividual... individuals)
	{
		return factory.getOWLDifferentIndividualsAxiom(individuals);
	}

	public synchronized OWLDifferentIndividualsAxiom getOWLDifferentIndividualsAxiom(
			Set<? extends OWLIndividual> individuals,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory
				.getOWLDifferentIndividualsAxiom(individuals, annotations);
	}

	public synchronized OWLClassAssertionAxiom getOWLClassAssertionAxiom(
			OWLClassExpression classExpression, OWLIndividual individual)
	{
		return factory.getOWLClassAssertionAxiom(classExpression, individual);
	}

	public synchronized OWLClassAssertionAxiom getOWLClassAssertionAxiom(
			OWLClassExpression classExpression, OWLIndividual individual,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLClassAssertionAxiom(classExpression, individual,
				annotations);
	}

	public synchronized OWLObjectPropertyAssertionAxiom getOWLObjectPropertyAssertionAxiom(
			OWLObjectPropertyExpression property, OWLIndividual individual,
			OWLIndividual object)
	{
		return factory.getOWLObjectPropertyAssertionAxiom(property, individual,
				object);
	}

	public synchronized OWLObjectPropertyAssertionAxiom getOWLObjectPropertyAssertionAxiom(
			OWLObjectPropertyExpression property, OWLIndividual individual,
			OWLIndividual object, Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLObjectPropertyAssertionAxiom(property, individual,
				object, annotations);
	}

	public synchronized OWLNegativeObjectPropertyAssertionAxiom getOWLNegativeObjectPropertyAssertionAxiom(
			OWLObjectPropertyExpression property, OWLIndividual subject,
			OWLIndividual object)
	{
		return factory.getOWLNegativeObjectPropertyAssertionAxiom(property,
				subject, object);
	}

	public synchronized OWLNegativeObjectPropertyAssertionAxiom getOWLNegativeObjectPropertyAssertionAxiom(
			OWLObjectPropertyExpression property, OWLIndividual subject,
			OWLIndividual object, Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLNegativeObjectPropertyAssertionAxiom(property,
				subject, object, annotations);
	}

	public synchronized OWLDataPropertyAssertionAxiom getOWLDataPropertyAssertionAxiom(
			OWLDataPropertyExpression property, OWLIndividual subject,
			OWLLiteral object)
	{
		return factory.getOWLDataPropertyAssertionAxiom(property, subject,
				object);
	}

	public synchronized OWLDataPropertyAssertionAxiom getOWLDataPropertyAssertionAxiom(
			OWLDataPropertyExpression property, OWLIndividual subject,
			OWLLiteral object, Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLDataPropertyAssertionAxiom(property, subject,
				object, annotations);
	}

	public synchronized OWLDataPropertyAssertionAxiom getOWLDataPropertyAssertionAxiom(
			OWLDataPropertyExpression property, OWLIndividual subject, int value)
	{
		return factory.getOWLDataPropertyAssertionAxiom(property, subject,
				value);
	}

	public synchronized OWLDataPropertyAssertionAxiom getOWLDataPropertyAssertionAxiom(
			OWLDataPropertyExpression property, OWLIndividual subject,
			double value)
	{
		return factory.getOWLDataPropertyAssertionAxiom(property, subject,
				value);
	}

	public synchronized OWLDataPropertyAssertionAxiom getOWLDataPropertyAssertionAxiom(
			OWLDataPropertyExpression property, OWLIndividual subject,
			float value)
	{
		return factory.getOWLDataPropertyAssertionAxiom(property, subject,
				value);
	}

	public synchronized OWLDataPropertyAssertionAxiom getOWLDataPropertyAssertionAxiom(
			OWLDataPropertyExpression property, OWLIndividual subject,
			boolean value)
	{
		return factory.getOWLDataPropertyAssertionAxiom(property, subject,
				value);
	}

	public synchronized OWLDataPropertyAssertionAxiom getOWLDataPropertyAssertionAxiom(
			OWLDataPropertyExpression property, OWLIndividual subject,
			String value)
	{
		return factory.getOWLDataPropertyAssertionAxiom(property, subject,
				value);
	}

	public synchronized OWLNegativeDataPropertyAssertionAxiom getOWLNegativeDataPropertyAssertionAxiom(
			OWLDataPropertyExpression property, OWLIndividual subject,
			OWLLiteral object)
	{
		return factory.getOWLNegativeDataPropertyAssertionAxiom(property,
				subject, object);
	}

	public synchronized OWLNegativeDataPropertyAssertionAxiom getOWLNegativeDataPropertyAssertionAxiom(
			OWLDataPropertyExpression property, OWLIndividual subject,
			OWLLiteral object, Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLNegativeDataPropertyAssertionAxiom(property,
				subject, object, annotations);
	}

	public synchronized OWLAnnotation getOWLAnnotation(
			OWLAnnotationProperty property, OWLAnnotationValue value)
	{
		return factory.getOWLAnnotation(property, value);
	}

	public synchronized OWLAnnotation getOWLAnnotation(
			OWLAnnotationProperty property, OWLAnnotationValue value,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLAnnotation(property, value, annotations);
	}

	public synchronized OWLAnnotationAssertionAxiom getOWLAnnotationAssertionAxiom(
			OWLAnnotationProperty property, OWLAnnotationSubject subject,
			OWLAnnotationValue value)
	{
		return factory.getOWLAnnotationAssertionAxiom(property, subject, value);
	}

	public synchronized OWLAnnotationAssertionAxiom getOWLAnnotationAssertionAxiom(
			OWLAnnotationSubject subject, OWLAnnotation annotation)
	{
		return factory.getOWLAnnotationAssertionAxiom(subject, annotation);
	}

	public synchronized OWLAnnotationAssertionAxiom getOWLAnnotationAssertionAxiom(
			OWLAnnotationProperty property, OWLAnnotationSubject subject,
			OWLAnnotationValue value, Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLAnnotationAssertionAxiom(property, subject, value,
				annotations);
	}

	public synchronized OWLAnnotationAssertionAxiom getOWLAnnotationAssertionAxiom(
			OWLAnnotationSubject subject, OWLAnnotation annotation,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLAnnotationAssertionAxiom(subject, annotation,
				annotations);
	}

	public synchronized OWLAnnotationAssertionAxiom getDeprecatedOWLAnnotationAssertionAxiom(
			IRI subject)
	{
		return factory.getDeprecatedOWLAnnotationAssertionAxiom(subject);
	}

	public synchronized OWLImportsDeclaration getOWLImportsDeclaration(
			IRI importedOntologyIRI)
	{
		return factory.getOWLImportsDeclaration(importedOntologyIRI);
	}

	public synchronized OWLAnnotationPropertyDomainAxiom getOWLAnnotationPropertyDomainAxiom(
			OWLAnnotationProperty prop, IRI domain)
	{
		return factory.getOWLAnnotationPropertyDomainAxiom(prop, domain);
	}

	public synchronized OWLAnnotationPropertyDomainAxiom getOWLAnnotationPropertyDomainAxiom(
			OWLAnnotationProperty prop, IRI domain,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLAnnotationPropertyDomainAxiom(prop, domain,
				annotations);
	}

	public synchronized OWLAnnotationPropertyRangeAxiom getOWLAnnotationPropertyRangeAxiom(
			OWLAnnotationProperty prop, IRI range)
	{
		return factory.getOWLAnnotationPropertyRangeAxiom(prop, range);
	}

	public synchronized OWLAnnotationPropertyRangeAxiom getOWLAnnotationPropertyRangeAxiom(
			OWLAnnotationProperty prop, IRI range,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory.getOWLAnnotationPropertyRangeAxiom(prop, range,
				annotations);
	}

	public synchronized OWLSubAnnotationPropertyOfAxiom getOWLSubAnnotationPropertyOfAxiom(
			OWLAnnotationProperty sub, OWLAnnotationProperty sup)
	{
		return factory.getOWLSubAnnotationPropertyOfAxiom(sub, sup);
	}

	public synchronized OWLSubAnnotationPropertyOfAxiom getOWLSubAnnotationPropertyOfAxiom(
			OWLAnnotationProperty sub, OWLAnnotationProperty sup,
			Set<? extends OWLAnnotation> annotations)
	{
		return factory
				.getOWLSubAnnotationPropertyOfAxiom(sub, sup, annotations);
	}

	public synchronized void purge()
	{
		factory.purge();
	}
}

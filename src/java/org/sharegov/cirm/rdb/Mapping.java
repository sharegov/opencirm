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
package org.sharegov.cirm.rdb;

import static org.sharegov.cirm.OWL.and;
import static org.sharegov.cirm.OWL.dataProperty;
import static org.sharegov.cirm.OWL.has;
import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.objectProperty;
import static org.sharegov.cirm.OWL.oneOf;
import static org.sharegov.cirm.OWL.or;
import static org.sharegov.cirm.OWL.owlClass;
import static org.sharegov.cirm.OWL.reasoner;
import static org.sharegov.cirm.OWL.some;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLProperty;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.utils.DirectRef;
import org.sharegov.cirm.utils.ObjectRef;
import org.sharegov.cirm.utils.SingletonRef;
/**
 * 
 * This class is deprecated use the RelationalOWLMapper instead.
 * @see RelationalOWLMapper
 * @deprecated
 * 
 */

public class Mapping
{

	private Map<OWLClass, OWLNamedIndividual> tableMapping;
	private Map<OWLNamedIndividual, Map<OWLProperty<?, ?>, OWLNamedIndividual>> columnMapping;
	private Map<Map<OWLObjectProperty,OWLNamedIndividual>,OWLNamedIndividual> hasOne;
	private Map<OWLObjectProperty, Map<OWLClass,OWLNamedIndividual>> hasMany;
	private List<OWLClass> unmapped;
	
	public Mapping()
	{
		tableMapping = tableMappings();
		columnMapping = columnMapping(tableMapping);
		hasOne = hasOne();
		hasMany = hasMany(tableMapping);
		unmapped = new Vector<OWLClass>();
	}
	
	public Map<OWLObjectProperty, Map<OWLClass, OWLNamedIndividual>> getHasMany()
	{
		return hasMany;
	}


	public Map<OWLClass, OWLNamedIndividual> getTableMapping()
	{
		return tableMapping;
	}

	
	public Map<OWLClass, OWLNamedIndividual> getTableMapping(Set<? extends OWLClassExpression> types)
	{
		Map<OWLClass, OWLNamedIndividual> mapping =  new LinkedHashMap<OWLClass, OWLNamedIndividual>();  
		outer:
		for(OWLClassExpression c : types)
		{
			if (! (c instanceof OWLClass))
				continue;
			OWLClass cl = (OWLClass)c;
			if(unmapped.contains(cl))
				continue;
			if(tableMapping.containsKey((OWLClass)c))
			{
				mapping.put(cl, tableMapping.get(cl));
				continue;
			}
			for(OWLClass mapped: tableMapping.keySet())
			{
				if(reasoner().isEntailed(OWL.dataFactory().getOWLSubClassOfAxiom(cl,mapped)))
				{
				
					mapping.put(cl, tableMapping.get(mapped));
					tableMapping.put(cl, tableMapping.get(mapped));
					continue outer;
				}
			}
			unmapped.add(cl);
		}
		return mapping;
	}

	public Map<OWLNamedIndividual, Map<OWLProperty<?, ?>, OWLNamedIndividual>> getColumnMapping()
	{
		return columnMapping;
	}


	public Map<Map<OWLObjectProperty, OWLNamedIndividual>, OWLNamedIndividual> getHasOne()
	{
		return hasOne;
	}

	public static SingletonRef<Mapping> ref = 
		new SingletonRef<Mapping>(new ObjectRef<Mapping>(DirectRef.make(Mapping.class)));
	
	public static Mapping getInstance() { return ref.resolve(); }
	
	
	public static OWLNamedIndividual pun(OWLClass c)
	{
		return individual(c.getIRI());
	}

	public static OWLNamedIndividual pun(OWLClassExpression c)
	{
		return pun(c.asOWLClass());
	}

	public static OWLClass pun(OWLNamedIndividual i)
	{
		return owlClass(i.getIRI());
	}

	public static Map<OWLClass, OWLNamedIndividual> tableMapping(Set<? extends OWLClassExpression> types)
	{

		Map<OWLClass, OWLNamedIndividual> mapping = new LinkedHashMap<OWLClass, OWLNamedIndividual>();
		OWLClassExpression q = and(owlClass(Refs.OWLClass), some(objectProperty(Concepts.hasTableMapping),owlClass(Concepts.DBTable)));
		NodeSet<OWLNamedIndividual> S = OWL.reasoner().getInstances(q, false);
		for (OWLNamedIndividual i : S.getFlattened())
		{
			OWLClass mapped = owlClass(i.getIRI());
			for(OWLClassExpression c : types)
			{
				if (! (c instanceof OWLClass))
					continue;
				if(c.equals(mapped) || 
						reasoner().isEntailed(OWL.dataFactory().getOWLSubClassOfAxiom(c,mapped)))
				{
					mapping.put(c.asOWLClass(), objectProperty(i, Concepts.hasTableMapping));
				}
			}
		}
		return mapping;
	}

	public static Map<OWLNamedIndividual, Map<OWLProperty<?, ?>, OWLNamedIndividual>> columnMapping(
			Map<OWLClass, OWLNamedIndividual> tableMapping)
	{
		Map<OWLNamedIndividual, Map<OWLProperty<?, ?>, OWLNamedIndividual>> mapping = new LinkedHashMap<OWLNamedIndividual, Map<OWLProperty<?, ?>, OWLNamedIndividual>>();
		
			OWLClassExpression q = and(owlClass(Refs.OWLProperty), some(objectProperty(Concepts.hasColumnMapping), and(
					or(owlClass(Concepts.DBPrimaryKey), owlClass(Concepts.DBNoKey)), 
					some(objectProperty(Concepts.hasTable), oneOf(tableMapping.values().toArray(new OWLIndividual[tableMapping.values().size()]))))));
					//has(objectProperty(Concepts.hasTable), table))));
			
			NodeSet<OWLNamedIndividual> S = reasoner().getInstances(q, false);
			for (OWLNamedIndividual i : S.getFlattened())
			{
				for(OWLNamedIndividual c : reasoner().getObjectPropertyValues(i, objectProperty(Concepts.hasColumnMapping)).getFlattened())
				{
					OWLNamedIndividual table = reasoner().getObjectPropertyValues(c,objectProperty(Concepts.hasTable)).getFlattened().iterator().next();
					Map<OWLProperty<?, ?>, OWLNamedIndividual> columns =  new LinkedHashMap<OWLProperty<?, ?>, OWLNamedIndividual>();
					if(mapping.containsKey(table))
					{
						columns = mapping.get(table);
					}
					if(reasoner().getTypes(i, false).getFlattened().contains(owlClass(Refs.OWLDataProperty)))
					{
						columns
						.put(dataProperty(i.getIRI()), 
								reasoner().getObjectPropertyValues(i, objectProperty(Concepts.hasColumnMapping))
								.getFlattened().iterator().next());
					}else
					{
						columns
						.put(objectProperty(i.getIRI()), 
								reasoner().getObjectPropertyValues(i, objectProperty(Concepts.hasColumnMapping))
								.getFlattened().iterator().next());
					}
					mapping.put(table, columns);
				}
			}
		return mapping;
	}
	
	public static Set<OWLNamedIndividual> columnIRI(OWLNamedIndividual table)
	{
		Set<OWLNamedIndividual> columns = new HashSet<OWLNamedIndividual>();
		OWLClassExpression q = 
			and(
				or(owlClass(Concepts.DBPrimaryKey), owlClass(Concepts.DBNoKey)),
				and(owlClass(Concepts.IRIKey)),
				has(objectProperty(Concepts.hasTable), table));
		NodeSet<OWLNamedIndividual> S = reasoner().getInstances(q, false);
		for (OWLNamedIndividual i : S.getFlattened())
			columns.add(i);
		return columns;
	}

	public static Map<Map<OWLObjectProperty,OWLNamedIndividual>, Map<OWLClass, OWLNamedIndividual>> hasOne(
			Map<OWLObjectPropertyExpression, Set<OWLIndividual>> objectPropertyValues, OWLOntology o, OWLIndividual fktable, Map<OWLClass,OWLNamedIndividual> tableMapping)
	{
		Map<Map<OWLObjectProperty,OWLNamedIndividual>, Map<OWLClass, OWLNamedIndividual>> result = new LinkedHashMap<Map<OWLObjectProperty,OWLNamedIndividual>, Map<OWLClass, OWLNamedIndividual>>();
		OWLClassExpression q =
		and(
			owlClass(Refs.OWLProperty),
			some(objectProperty(Concepts.hasOne),owlClass(Refs.OWLClass))
		);
				
		NodeSet<OWLNamedIndividual> S = reasoner().getInstances(q, false);
		for(OWLNamedIndividual i : S.getFlattened())
		{
			OWLObjectProperty property = objectProperty(i.getIRI());
			if(objectPropertyValues.containsKey(property))
			{
				OWLNamedIndividual l = objectPropertyValues.get(property).iterator().next().asOWLNamedIndividual();
				Set<OWLClassExpression> types = l.getTypes(o);
				if (!types.isEmpty() && types.size() == 1)
				{
					OWLClassExpression cle = types.iterator().next();
					if(cle instanceof OWLClass)
					{
						Map<OWLClass,OWLNamedIndividual> tbm = new HashMap<OWLClass, OWLNamedIndividual>();
						tbm.put(cle.asOWLClass(), tableMapping.get(cle.asOWLClass()));
						Set<OWLNamedIndividual> columns = reasoner().getObjectPropertyValues(i, 
								objectProperty(Concepts.hasColumnMapping)).getFlattened();
						OWLClassExpression q2 = and(
								owlClass("DBForeignKey"), 
								has( objectProperty("hasTable"), fktable), 
								oneOf(columns.toArray(new OWLIndividual[columns.size()]) )
							);
						OWLNamedIndividual column = reasoner().getInstances(q2, false).getFlattened().iterator().next();
						Map<OWLObjectProperty, OWLNamedIndividual> columnMapping = new HashMap<OWLObjectProperty, OWLNamedIndividual>();
						columnMapping.put(property, column);
						result.put(columnMapping, tbm);
					}
				}
				
			}
		 }
		return result;
	}
	
	public static Map<Map<OWLObjectProperty,OWLNamedIndividual>,OWLNamedIndividual> hasOne()
	{
		Map<Map<OWLObjectProperty,OWLNamedIndividual>,OWLNamedIndividual> result = new LinkedHashMap<Map<OWLObjectProperty,OWLNamedIndividual>,OWLNamedIndividual> ();
		OWLClassExpression q =
		and(
			owlClass(Refs.OWLProperty),
			some(objectProperty(Concepts.hasOne),owlClass(Refs.OWLClass)),
			some(objectProperty(Concepts.hasColumnMapping), owlClass("DBForeignKey")
			)
		);
		NodeSet<OWLNamedIndividual> S = reasoner().getInstances(q, false);
		for(OWLNamedIndividual i : S.getFlattened())
		{
			OWLObjectProperty property = objectProperty(i.getIRI());
			Set<OWLNamedIndividual> foreignKeyColumns = reasoner().getObjectPropertyValues(i,objectProperty(Concepts.hasColumnMapping)).getFlattened();
			for(OWLNamedIndividual foreignKeyColumn : foreignKeyColumns)
			{
				Set<OWLNamedIndividual> foreignKeyTables = reasoner().getObjectPropertyValues(foreignKeyColumn,objectProperty(Concepts.hasTable)).getFlattened();
				for(OWLNamedIndividual foreignKeyTable : foreignKeyTables)
				{
					Map<OWLObjectProperty,OWLNamedIndividual> propertyToForeignTable = new LinkedHashMap<OWLObjectProperty, OWLNamedIndividual>();
					propertyToForeignTable.put(property, foreignKeyTable);
					result.put(propertyToForeignTable, foreignKeyColumn);
				}
			}
			break;
		}
		return result;
	}
	
	public static Map<OWLObjectProperty,OWLNamedIndividual> hasOne(OWLIndividual fkTable, OWLIndividual key) {

		Map<OWLObjectProperty,OWLNamedIndividual> result = new LinkedHashMap<OWLObjectProperty, OWLNamedIndividual>();
		OWLClassExpression q = and(
									owlClass(Refs.OWLObjectProperty),
									some(objectProperty(Concepts.hasOne),owlClass(Refs.OWLClass)),
									oneOf(key)
								);
		NodeSet<OWLNamedIndividual> S = reasoner().getInstances(q, false);
		for(OWLNamedIndividual i : S.getFlattened()) {
			OWLObjectProperty property = objectProperty(i.getIRI());
			Set<OWLNamedIndividual> aaa = reasoner().getObjectPropertyValues(i, objectProperty(Concepts.hasColumnMapping)).getFlattened();
			OWLClassExpression q2 = and(
										owlClass("DBForeignKey"), 
										has( objectProperty("hasTable"), fkTable ), 
										oneOf( aaa.toArray(new OWLIndividual[aaa.size()]) )
									);
			NodeSet<OWLNamedIndividual> S2  = reasoner().getInstances(q2, false);
			for(OWLNamedIndividual j : S2.getFlattened()) {
				result.put(property, j);
			}
		}
		return result;
	}
	
	public static Set<OWLObjectProperty> hasMany(OWLIndividual key) {
		Set<OWLObjectProperty> result = new HashSet<OWLObjectProperty>();
		OWLClassExpression q = and(
									owlClass(Refs.OWLProperty), 
									some(objectProperty(Concepts.hasMany),owlClass(Refs.OWLClass)),
									oneOf(key)
								);
		NodeSet<OWLNamedIndividual> S = reasoner().getInstances(q, false);
		for(OWLNamedIndividual i : S.getFlattened())
		{
			OWLObjectProperty property = objectProperty(i.getIRI());
			result.add(property);
		}
		return result;
	}
	
	public static Map<OWLObjectProperty, Map<OWLClass,OWLNamedIndividual>> hasMany(Map<OWLClass, OWLNamedIndividual> tableMapping)
	{
		Map<OWLObjectProperty, Map<OWLClass,OWLNamedIndividual>> result = new LinkedHashMap<OWLObjectProperty,Map<OWLClass,OWLNamedIndividual>>();
		OWLClassExpression q =
			and(
			owlClass(Refs.OWLProperty)
			,
			some(objectProperty(Concepts.hasMany),owlClass(Refs.OWLClass))
			);
		NodeSet<OWLNamedIndividual> S = reasoner().getInstances(q, false);
		for(OWLNamedIndividual i : S.getFlattened())
		{
			OWLObjectProperty property = objectProperty(i.getIRI());
			Set<OWLNamedIndividual> classes = reasoner().getObjectPropertyValues(i,objectProperty(Concepts.hasMany)).getFlattened();
			for (OWLNamedIndividual iclass : classes)
			{
				OWLClass cle = owlClass(iclass.getIRI());
				Map<OWLClass,OWLNamedIndividual> tbm = new HashMap<OWLClass, OWLNamedIndividual>();
				tbm.put(cle.asOWLClass(), tableMapping.get(cle.asOWLClass()));
				result.put(property, tbm);
			}
		 }	
		return result;
	}
					
	public static Map<OWLObjectProperty, Map<OWLClass,OWLNamedIndividual>> hasMany(Map<OWLObjectPropertyExpression, Set<OWLIndividual>> objectPropertyValues, OWLOntology o, OWLIndividual table, Map<OWLClass, OWLNamedIndividual> tableMapping)
	{
		Map<OWLObjectProperty, Map<OWLClass,OWLNamedIndividual>> result = new LinkedHashMap<OWLObjectProperty,Map<OWLClass,OWLNamedIndividual>>();
		OWLClassExpression q =
			and(
			owlClass(Refs.OWLProperty)
			,
			some(objectProperty(Concepts.hasMany),owlClass(Refs.OWLClass))
			);
		NodeSet<OWLNamedIndividual> S = reasoner().getInstances(q, false);
		for(OWLNamedIndividual i : S.getFlattened())
		{
			OWLObjectProperty property = objectProperty(i.getIRI());
			if(objectPropertyValues.containsKey(property))
			{
				OWLNamedIndividual l = objectPropertyValues.get(property).iterator().next().asOWLNamedIndividual();
				Set<OWLClassExpression> types = l.getTypes(o);
				if (!types.isEmpty() && types.size() == 1)
				{
					OWLClassExpression cle = types.iterator().next();
					if(cle instanceof OWLClass)
					{
						Map<OWLClass,OWLNamedIndividual> tbm = new HashMap<OWLClass, OWLNamedIndividual>();
						tbm.put(cle.asOWLClass(), tableMapping.get(cle.asOWLClass()));
						result.put(property, tbm);
					}
					
				}
			}
		 }	
		return result;
	}
	
	public static OWLNamedIndividual join(OWLNamedIndividual tableA, OWLNamedIndividual tableB)
	{
		//Check if many-to-many
		OWLNamedIndividual joinTable  = null;
		OWLClassExpression q =
			and(
			has(objectProperty(Concepts.isJoinedWithTable), tableA)
			,
			has(objectProperty(Concepts.isJoinedWithTable), tableB)
			);
		NodeSet<OWLNamedIndividual> S = reasoner().getInstances(q, false);
		for (OWLNamedIndividual i : S.getFlattened())
		{
			joinTable = i;
			break;
		}
		if(joinTable == null)
		{
			q =
				has(objectProperty(Concepts.isJoinedWithTable), tableA);
			//Check if many-to-one
			S = reasoner().getInstances(q, false);
			if(!S.isEmpty() && S.getFlattened().contains(tableB))
			{
				joinTable = tableB;
			}
		}
		return joinTable;
	}

	public static OWLNamedIndividual joinColumnIRI(OWLNamedIndividual joinColumn, OWLNamedIndividual joinTable)
	{
		OWLNamedIndividual columnIRI  = null;
		OWLClassExpression q =
			and(
			owlClass(Concepts.DBForeignKey)
			,
			has(objectProperty(Concepts.hasTable), joinTable)
			,
			has(objectProperty(Concepts.hasJoinColumn), joinColumn)
			);
		NodeSet<OWLNamedIndividual> S = reasoner().getInstances(q, false);
		for (OWLNamedIndividual i : S.getFlattened())
		{
			columnIRI = i;
			break;
		}
		return columnIRI;
	}
	
	
	public static Map<OWLClass, OWLNamedIndividual> tableMappings()
	{

		Map<OWLClass, OWLNamedIndividual> mapping = new LinkedHashMap<OWLClass, OWLNamedIndividual>();
		OWLClassExpression q = and(owlClass(Refs.OWLClass), some(objectProperty(Concepts.hasTableMapping),owlClass(Concepts.DBTable)));
		NodeSet<OWLNamedIndividual> S = OWL.reasoner().getInstances(q, false);
		for (OWLNamedIndividual i : S.getFlattened())
		{
			OWLClass mapped = owlClass(i.getIRI());
				mapping.put(mapped, objectProperty(i, Concepts.hasTableMapping));
			
		}
		return mapping;
	}
	
	public static OWLNamedIndividual table(OWLClassExpression c, Map<OWLClass, OWLNamedIndividual> tableMapping)
	{

		if(c != null 
				&& c instanceof OWLClass
					&& tableMapping.containsKey(c.asOWLClass()))
			return tableMapping.get(c.asOWLClass());
		else 
			return null;
	}
	
	public static OWLNamedIndividual table(Set<? extends OWLClassExpression> types, Map<OWLClass, OWLNamedIndividual> tableMapping)
	{
		if(types.size() == 1)
			return table(types.iterator().next(), tableMapping);
		else
			for(OWLClassExpression type: types)
			{
				OWLNamedIndividual table = table(type, tableMapping);
				if(table != null)
					return table;
			}
		return null;
	}
	
}

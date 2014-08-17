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
package gov.miamidade.cirm.reports;

import static org.sharegov.cirm.OWL.reasoner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.rdb.DbId;

/**
 * This class exports ontology data to a set of CSV in an exportDirectory
 * 
 * @author Syed Abbas
 *
 */
public class ReportingMetadataExport
{

	private String exportDirectory = "C:/Work/cirmservices_etl/";
	
	public void execute() throws Throwable
	{
		
		try
		{
			exportOntologyMetadataToCSV();
			
		}catch (Throwable e)
		{
			throw e;
		}
		
	}
	
	
	public void exportOntologyMetadataToCSV()
	{
		dumpSRTypes();
		dumpStatus();
		dumpPriority();
		dumpIntakeMethods();
		dumpActivities();
		dumpAnnotationLabels();
		dumpQuestions();
		dumpChoiceValues();
		dumpOutcomes();
		dumpUserOrgUnit();
		dumpObservedHolidays();
		dumpMetaIndividuals();
		//dumpOfficialOrgUnit();
		//dumpStreetAliases();
	}
	
	private void dumpOfficialOrgUnit()
	{
		List<List<String>> officialTable = new ArrayList<List<String>>();
		OWLClassExpression cle = OWL.parseDL("Government_Official", reasoner().getRootOntology());
		
		for(OWLNamedIndividual  orgUnit : reasoner().getInstances(cle, false).getFlattened())
		{
			List<String> orgUnits = new ArrayList<String>();
			orgUnits.add(orgUnit.getIRI().getFragment());
			orgUnits.add(OWL.getEntityLabel(orgUnit));
			orgUnits.add(OWL.dataProperty(orgUnit, "Dept_Code").getLiteral());
			officialTable.add(orgUnits);
		}
		//toCSV(orgUnitByDeptTable, new File(config.at("workingDir").asString() + "org_unit_by_official_metadata.csv"));
		
		//hasGovernmentOfficial some Government_Official
	}
	
	
	private void dumpMetaIndividuals()
	{
		List<List<String>> metaTable =  new ArrayList<List<String>>();
		Set<OWLEntity> set = new HashSet<OWLEntity>();
		set.addAll(Refs.defaultOntology.resolve().getIndividualsInSignature(true));
		set.addAll(Refs.defaultOntology.resolve().getClassesInSignature(true));
		Map<OWLEntity, DbId> identifiers = Refs.defaultRelationalStoreExt.resolve().selectIDsAndEntitiesByIRIs(set);
		for(OWLEntity entity : set)
		{
			List<String> l = new ArrayList<String>();
			Long id = null;
			if(identifiers.containsKey(entity))
			{
				id = identifiers.get(entity).getFirst();
			}
			l.add(id==null?"":id+"");
			l.add(entity.getIRI().getFragment());
			l.add(OWL.getEntityLabel(entity).replace("\"", "\"\""));
			if(entity.isOWLNamedIndividual())
			{
				OWLNamedIndividual ind = entity.asOWLNamedIndividual();
				Set<OWLClassExpression> types =  ind.getTypes(Refs.defaultOntology.resolve());
				OWLClass type = OWL.owlClass("Thing");
				if(!types.isEmpty())
				{
					OWLClassExpression exp = types.iterator().next();
					if(!exp.isAnonymous())
						type = exp.asOWLClass();
				}
				l.add(type.getIRI().getFragment());
				OWLLiteral nameOrAlias = OWL.dataProperty(ind,"Name");
				if(nameOrAlias == null)
					nameOrAlias = OWL.dataProperty(ind,"Alias");
				l.add(ind.getIRI().getFragment());
				l.add(nameOrAlias != null? nameOrAlias.getLiteral():"");
			}
			else
			{
				l.add("");
				l.add("");
				l.add("");
			}
			metaTable.add(l);
		}
		toCSV(metaTable, new File(getExportDirectory() + "meta_individuals_metadata.csv"));
	}
	
	private void dumpUserOrgUnit()
	{
		List<List<String>> orgUnitByDeptTable = new ArrayList<List<String>>();
		OWLClassExpression cle = OWL.parseDL("Dept_Code min 1", reasoner().getRootOntology());
		
		for(OWLNamedIndividual  orgUnit : reasoner().getInstances(cle, false).getFlattened())
		{
			List<String> orgUnits = new ArrayList<String>();
			orgUnits.add(orgUnit.getIRI().getFragment());
			orgUnits.add(OWL.getEntityLabel(orgUnit));
			orgUnits.add(OWL.dataProperty(orgUnit, "Dept_Code").getLiteral());
			orgUnitByDeptTable.add(orgUnits);
		}
		toCSV(orgUnitByDeptTable, new File(getExportDirectory() + "org_unit_by_dept_metadata.csv"));
	}

	private void dumpQuestions()
	{
		List<List<String>> questionsTable = getQuestionsAsSortedList();
		toCSV(questionsTable, new File(getExportDirectory() + "sr_questions_metadata.csv"));
	}
	
	private void dumpChoiceValues()
	{
		List<List<String>> answersTable = getChoiceValuesAsSortedList();
		toCSV(answersTable, new File(getExportDirectory() + "sr_choice_values_metadata.csv"));
	}

	private List<List<String>> getQuestionsAsSortedList()
	{
		List<List<String>> questionsTable = new ArrayList<List<String>>();
		List<OWLNamedIndividual> questionsIndividuals = new ArrayList<OWLNamedIndividual>();
		questionsIndividuals.addAll(reasoner().getInstances(OWL.owlClass("legacy:ServiceField"), false).getFlattened());
		Collections.sort(questionsIndividuals, new Comparator<OWLNamedIndividual>()
			{@Override
			public int compare(OWLNamedIndividual o1, OWLNamedIndividual o2)
			{
				
				return o1.getIRI().getFragment().compareTo(o2.getIRI().getFragment());
			}
			
		});
		String currentSRType = null;
		List<List<String>> srTypeQuestions = new ArrayList<List<String>>();
		for(OWLNamedIndividual  question : questionsIndividuals)
		{
			String questionFragment = question.getIRI().getFragment();
			String srType = questionFragment.substring(0, questionFragment.indexOf('_'));
			if(currentSRType == null)
			{
				currentSRType = srType;
			} else if(!currentSRType.equals(srType))
			{
				Collections.sort(srTypeQuestions, new Comparator<List<String>>()
				{
					public int compare(List<String> o1, List<String> o2) {
						Double d1 = 0.0;
						Double d2 = 0.0;
						String s1 = o1.get(4);
						String s2 = o2.get(4);
						try
						{
							
							d1 = Double.parseDouble(s1);
							d2 = Double.parseDouble(s2);
							
						}catch(NumberFormatException nfe )
						{
							System.out.println("Number format exception when comparing hasOrderBy " + s1 + " = " + s2);
							return s1.compareTo(s2);
						}
						return d1.compareTo(d2);
					};
				});
				//sort questions by the hasOrderBY entry.
				//put in table.
				questionsTable.addAll(srTypeQuestions);
				//System.out.println("Question Table size: " + questionsTable.size());
				//clear srTypeQuestions
				int questionIndex = 0;
				for(List<String> row : srTypeQuestions)
				{
					row.add(questionIndex+"");
					questionIndex++;
				}
				srTypeQuestions.clear();
	
			}
			List<String> questionRow = new ArrayList<String>();
			questionRow.add(questionFragment);
			questionRow.add(OWL.getEntityLabel(question).replaceAll("\"", "\"\""));
			questionRow.add(srType);
			questionRow.add(OWL.dataProperty(question, "legacy:hasDataType").getLiteral());
			questionRow.add(OWL.dataProperty(question, "legacy:hasOrderBy").getLiteral());
			srTypeQuestions.add(questionRow);
			currentSRType = srType;
		}
		return questionsTable;
	}
	
	private List<List<String>> getChoiceValuesAsSortedList()
	{
		List<List<String>> questionsTable = new ArrayList<List<String>>();
		List<OWLNamedIndividual> questionsIndividuals = new ArrayList<OWLNamedIndividual>();
		
		for(OWLNamedIndividual serviceField : reasoner().getInstances(OWL.owlClass("legacy:ServiceField"), false).getFlattened())
		{
			if(OWL.objectProperty(serviceField, "legacy:hasChoiceValueList") != null)
			{
				questionsIndividuals.add(serviceField);
			}
		}
		Collections.sort(questionsIndividuals, new Comparator<OWLNamedIndividual>()
			{@Override
			public int compare(OWLNamedIndividual o1, OWLNamedIndividual o2)
			{
				
				return o1.getIRI().getFragment().compareTo(o2.getIRI().getFragment());
			}
			
		});
		String currentSRType = null;
		List<List<String>> srTypeQuestions = new ArrayList<List<String>>();
		for(OWLNamedIndividual  question : questionsIndividuals)
		{
			String questionFragment = question.getIRI().getFragment();
			String srType = questionFragment.substring(0, questionFragment.indexOf('_'));
			if(currentSRType == null)
			{
				currentSRType = srType;
			} else if(!currentSRType.equals(srType))
			{
				Collections.sort(srTypeQuestions, new Comparator<List<String>>()
				{
					public int compare(List<String> o1, List<String> o2) {
						String s1 = o1.get(0);
						String s2 = o2.get(0);
						return s1.compareTo(s2);
						
					};
				});
				//sort questions by the hasOrderBY entry.
				//put in table.
				questionsTable.addAll(srTypeQuestions);
				//System.out.println("Question Table size: " + questionsTable.size());
				//clear srTypeQuestions
				int questionIndex = 0;
				for(List<String> row : srTypeQuestions)
				{
					row.add(questionIndex+"");
					questionIndex++;
				}
				srTypeQuestions.clear();
	
			}
			OWLNamedIndividual choiceValueList =  OWL.objectProperty(question, "legacy:hasChoiceValueList");
			Set<OWLNamedIndividual>  choiceValues = OWL.objectProperties(choiceValueList, "legacy:hasChoiceValue");
			Map<OWLEntity, DbId> choiceValueIDs = Refs.defaultRelationalStoreExt.resolve().selectIDsAndEntitiesByIRIs(choiceValues);
			for(OWLNamedIndividual choiceValue : choiceValues)
			{
				List<String> choiceValueRow = new ArrayList<String>();
				choiceValueRow.add(questionFragment);
				choiceValueRow.add(choiceValue.getIRI().toString());
				Long id = null;
				if(choiceValueIDs.containsKey(choiceValue))
				{
					id = choiceValueIDs.get(choiceValue).getFirst();
				}
				choiceValueRow.add(id==null?"":id+"");
				choiceValueRow.add(OWL.getEntityLabel(choiceValue));
				srTypeQuestions.add(choiceValueRow);
			}
			currentSRType = srType;
		}
		return questionsTable;
	}

	private void dumpAnnotationLabels()
	{
		List<List<String>> iriLabels = new ArrayList<List<String>>();
		for(OWLOntology o : OWL.ontologies())
		{
			for(OWLAnnotationAssertionAxiom axiom : o.getAxioms(AxiomType.ANNOTATION_ASSERTION))
			{
				if(axiom.getProperty().equals(OWL.annotationProperty("http://www.w3.org/2000/01/rdf-schema#label")))
				{
					List<String> iriRow = new ArrayList<String>();
					iriRow.add(axiom.getSubject().toString());
					String value = axiom.getValue().toString();
					iriRow.add(value.substring(0,value.indexOf("\"",1)).replaceAll("\"", ""));
					iriLabels.add(iriRow);
				}
			}
		}
		toCSV(iriLabels, new File(getExportDirectory() + "irilabels_metadata.csv"));
	}

	private void dumpSRTypes()
	{
		List<List<String>> typeTable = new ArrayList<List<String>>();
		Map<String,String> owners = new HashMap<String,String>();
		for(OWLNamedIndividual  type : reasoner().getInstances(OWL.owlClass("legacy:ServiceCase"), false).getFlattened())
		{
			List<String> typeRow = new ArrayList<String>();
			typeRow.add(type.getIRI().getFragment());
			typeRow.add(OWL.getEntityLabel(type));
			OWLNamedIndividual providedBy = OWL.objectProperty(type, "legacy:providedBy");
			String srOwner = "";
			if(providedBy != null)
			{
				srOwner = providedBy.getIRI().getFragment();
				if(!owners.containsKey(srOwner))
				{
					String ownerLabel = OWL.getEntityLabel(providedBy);
					owners.put(srOwner, ownerLabel);
				}
			}
			typeRow.add(srOwner);
			double days;
			OWLLiteral literal = OWL.dataProperty(type, "legacy:hasDurationDays");
			if(literal != null)
				days = literal.parseFloat();
			else
				days = 0.0;
			typeRow.add(days+"");
			typeTable.add(typeRow);
		}
		toCSV(typeTable, new File(getExportDirectory() + "sr_types_metadata.csv"));
		List<List<String>> ownersTable = new ArrayList<List<String>>();
		for(Map.Entry<String, String> entry : owners.entrySet())
		{
			List<String> ownerRow = new ArrayList<String>();
			ownerRow.add(entry.getKey());
			ownerRow.add(entry.getValue());
			ownersTable.add(ownerRow);
		}
		toCSV(ownersTable,  new File(getExportDirectory() + "org_unit_by_sr_metadata.csv"));
	}
	
	private void dumpActivities()
	{
		List<List<String>> activityTable = new ArrayList<List<String>>();
		for(OWLNamedIndividual  activity : reasoner().getInstances(OWL.owlClass("legacy:Activity"), false).getFlattened())
		{
			List<String> activityRow = new ArrayList<String>();
			activityRow.add(activity.getIRI().getFragment());
			activityRow.add(OWL.getEntityLabel(activity));
			activityTable.add(activityRow);
		}
		toCSV(activityTable, new File(getExportDirectory() + "sr_activity_metadata.csv"));
	}
	
	private void dumpOutcomes()
	{
		List<List<String>> outcomeTable = new ArrayList<List<String>>();
		for(OWLNamedIndividual  outcome : reasoner().getInstances(OWL.owlClass("legacy:Outcome"), false).getFlattened())
		{
			List<String> outcomeRow = new ArrayList<String>();
			outcomeRow.add(outcome.getIRI().getFragment());
			outcomeRow.add(OWL.getEntityLabel(outcome));
			outcomeTable.add(outcomeRow);
		}
		toCSV(outcomeTable, new File(getExportDirectory() + "sr_outcome_metadata.csv"));
	}
	
	private void dumpIntakeMethods()
	{
		List<List<String>> intakeMethodTable = new ArrayList<List<String>>();
		for(OWLNamedIndividual  intakeMethod : reasoner().getInstances(OWL.owlClass("legacy:IntakeMethod"), false).getFlattened())
		{
			List<String> intakeMethodRow = new ArrayList<String>();
			intakeMethodRow.add(intakeMethod.getIRI().getFragment());
			intakeMethodRow.add(OWL.getEntityLabel(intakeMethod));
			intakeMethodTable.add(intakeMethodRow);
		}
		toCSV(intakeMethodTable, new File(getExportDirectory() + "intake_method_metadata.csv"));
	}
	
	private void dumpPriority()
	{
		List<List<String>> priorityTable = new ArrayList<List<String>>();
		for(OWLNamedIndividual  priority : reasoner().getInstances(OWL.owlClass("legacy:Priority"), false).getFlattened())
		{
			List<String> priorityRow = new ArrayList<String>();
			priorityRow.add(priority.getIRI().getFragment());
			priorityRow.add(OWL.getEntityLabel(priority));
			priorityTable.add(priorityRow);
		}
		toCSV(priorityTable, new File(getExportDirectory() + "priority_metadata.csv"));
	}
	
	private void dumpStatus()
	{
		List<List<String>> statusTable = new ArrayList<List<String>>();
		for(OWLNamedIndividual  status : reasoner().getInstances(OWL.owlClass("legacy:Status"), false).getFlattened())
		{
			List<String> statusRow = new ArrayList<String>();
			statusRow.add(status.getIRI().getFragment());
			statusRow.add(OWL.getEntityLabel(status));
			statusTable.add(statusRow);
		}
		toCSV(statusTable, new File(getExportDirectory() + "status_metadata.csv"));
	}
	
	private void dumpServiceFieldsWithAnswerObjects()
	{
		List<List<String>> fieldTable = new ArrayList<List<String>>();
		for(OWLNamedIndividual  field : reasoner().getInstances(OWL.owlClass("legacy:ServiceQuestion"), false).getFlattened())
		{
			OWLLiteral dataType =  OWL.dataProperty(field, "legacy:hasDataType");
			if(dataType != null)
			{
				String dataTypeCode = dataType.getLiteral();
				if((dataTypeCode.equals("CHARLIST") || dataTypeCode.equals("CHARMULT") || dataTypeCode.equals("CHAROPT")))
				{
					List<String> fieldRow = new ArrayList<String>();
					fieldRow.add(field.getIRI().toString());
					fieldRow.add(dataTypeCode);
					fieldTable.add(fieldRow);
				}
			}
		}
		toCSV(fieldTable, new File(getExportDirectory() + "questions_with_answer_objects.csv"));
	}

	private void dumpAnswerObjects()
	{
		List<List<String>> answerTable = new ArrayList<List<String>>();
		for(OWLNamedIndividual  answer : reasoner().getInstances(OWL.owlClass("legacy:ChoiceValue"), false).getFlattened())
		{
			String answerLabel =  OWL.getEntityLabel(answer);
			List<String> answerRow = new ArrayList<String>();
			answerRow.add(answer.getIRI().toString());
			answerRow.add(OWL.dataProperty(answer, "legacy:hasLegacyCode").getLiteral());
			answerRow.add(answerLabel);
			answerTable.add(answerRow);
		}
		toCSV(answerTable, new File(getExportDirectory() + "answer_objects.csv"));
	}
	
	private void toCSV(List<List<String>> tableOfValues, File file)
	{
		try
		{
			FileWriter writer = new FileWriter(file);
			for(List<String> rows : tableOfValues)
			{
				StringBuilder csvRow = new StringBuilder();
				for(String column: rows)
				{
					csvRow.append("\"").append(column.replaceAll(",", "\\,")).append("\"").append(",");
				}
				csvRow.deleteCharAt(csvRow.length() - 1);
				csvRow.append("\n");
				writer.write(csvRow.toString());
			}
			writer.close();
		} catch (IOException ioe)
		{
			ioe.printStackTrace();
		}
	}
	
	public List<String> createAnswerTableDDLScript() throws Exception
	{
		final int ORACLE_TABLE_COLUMN_LIMIT = 1000;
		List<List<String>> srTypeQuestions = getQuestionsAsSortedList();
		String tableNameN = "CIRMDW_SR_ANSWER";
		StringBuilder sql = new StringBuilder();
		Map<String,String> uniqueAnswerCodeColumnNames = new TreeMap<String,String>(new HashMap<String,String>(srTypeQuestions.size()));
		for(List<String> row: srTypeQuestions)
		{
			String columnFragment = URLDecoder.decode(row.get(0),"UTF-8");
			String column = columnFragment;//.substring(columnFragment.indexOf('_') + 1);
			String dataType = row.get(3);
			String columnQualifiedName = column + "_" + dataType;
			if(uniqueAnswerCodeColumnNames.containsKey(column))
			{
				if(!dataType.equals(uniqueAnswerCodeColumnNames.get(column)))
				{
					
					uniqueAnswerCodeColumnNames.put(columnQualifiedName, dataType);//uniqueAnswerCodeColumnNames.put(columnFragment, dataType);
					
				}
				else
				{
					//uniqueAnswerCodeColumnNames.put(columnQualifiedName, dataType);
					System.out.println("Column with type already present." +column + " " + dataType);
				}
			}else
			{
				uniqueAnswerCodeColumnNames.put(column, dataType);
			}
			
		}
		
		int tableCountN = 0;
		int columnSize = 0;
		int totalTables = (((int)(uniqueAnswerCodeColumnNames.size()/ORACLE_TABLE_COLUMN_LIMIT)));
		if(uniqueAnswerCodeColumnNames.size() % ORACLE_TABLE_COLUMN_LIMIT > 0)
			totalTables = totalTables + 1;
		System.out.println("Total Columns: " + uniqueAnswerCodeColumnNames.size() + "\nTotal Table: " +  totalTables);
		for(Map.Entry<String, String> columnAndType : uniqueAnswerCodeColumnNames.entrySet())
		{
			if(columnSize == ORACLE_TABLE_COLUMN_LIMIT || tableCountN == totalTables - 1)
			{
				columnSize = 0;
				sql.append("\n primary key (SR_REQUEST_ID))");
				System.out.println(sql);
			}
			if(columnSize == 0)
			{
				sql.delete(0, sql.length());
				sql.append("create table " + tableNameN);
				if(tableCountN > 0)
					sql.append(tableCountN);
				sql.append("\n( SR_REQUEST_ID number(19,0) not null,");
				tableCountN++;
			}
			sql.append("\n\t");
			sql.append(columnSize +") " + columnAndType.getKey()).append(" ").append(columnAndType.getValue()).append(",");
			columnSize++;
		}
		return Collections.singletonList(sql.toString());
	}
	
	private void dumpObservedHolidays()
	{
		List<List<String>> holidayTable = new ArrayList<List<String>>();
		for(OWLNamedIndividual  holiday : reasoner().getInstances(OWL.owlClass("mdc:Observed_County_Holiday"), false).getFlattened())
		{
			for(OWLLiteral date : reasoner().getDataPropertyValues(holiday, OWL.dataProperty("mdc:hasDate")))
			{
				List<String> holidayRow = new ArrayList<String>();
				holidayRow.add(holiday.getIRI().getFragment());
				holidayRow.add(OWL.getEntityLabel(holiday));
				holidayRow.add(date.getLiteral());
				holidayTable.add(holidayRow);
			}
		}
		toCSV(holidayTable, new File(getExportDirectory() + "holidays_metadata.csv"));
	}
	
	public String getExportDirectory()
	{
		return exportDirectory;
	}

	public void setExportDirectory(String exportDirectory)
	{
		this.exportDirectory = exportDirectory;
	}

}

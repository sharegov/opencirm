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

import static org.sharegov.cirm.OWL.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import javax.xml.datatype.DatatypeFactory;
import mjson.Json;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.owl.Model;
import org.sharegov.cirm.rest.LegacyEmulator;
import org.sharegov.cirm.utils.ActivityUtils;
import org.sharegov.cirm.utils.GenUtils;

/**
 * The ActivityManager is responsible for creating and updating serviceActivities as well as executing configured triggers,
 * such as close case on outcome, execute email templates and trigger the creation of new activities or dependent service cases.
 * Furthermore, it's submitting tasks to the time machine for overdue callback checks.
 * 
 * @author Syed
 * @author Tom
 */
public class ActivityManager
{

	public static boolean DBG = true;
	public static boolean THROW_ALL_EXC = false;
	public static boolean USE_MESSAGE_MANAGER = true;
	public static boolean USE_TIME_MACHINE = true;
	
	public static final String ACTIVITY_AUTO = "auto";
	public static final String ACTIVITY_ERROR = "error";

	private final ActivityUtils utils = new ActivityUtils();
	
	/**
	 * Counts time machine calls since activitymanager creation for each task.
	 * As activitymanagers should be created once per transaction execution, it can ensure proper time machine task overwrites on retry.
	 * Task keys should remain constant during all retries of a transaction.
	 * Assuming single threaded execution.
	 */
	private final HashMap<String, Integer> timeMachineTaskToIndex = new HashMap<String, Integer>();

	/**
	 * Gets a unique taskId within this transaction execution that remains equal on transaction retries.
	 * Use with FRAGMENTA-OVERDUE-FRAGMENTB or DELAY-FRAGMENTA.
	 * Not thread safe, assuming single worker thread.
	 * hilpold
	 * @param task a task string that should be equal on retries and therefore not contain regenerated timestamps or any sequence ids.
	 * @return task + indexForThisTask
	 */
	private String getNextTimeMachineTaskIDFor(String task) 
	{
		Integer curIdx = timeMachineTaskToIndex.get(task);
		if (curIdx == null) 
			curIdx = 0;
		else 
			curIdx ++;
		timeMachineTaskToIndex.put(task, curIdx);
		return task + " idx " + curIdx.toString();
	}
	
	private String findAutoAssignment(OWLNamedIndividual rule, BOntology bo, OWLNamedIndividual serviceActivity, OWLNamedIndividual outcome)
	{
		if (rule.getIRI().equals(Model.legacy("AssignActivityToCaseCreator")))
		{
			OWLLiteral createdBy = bo.getDataProperty("isCreatedBy");
			if (createdBy != null)
				return createdBy.getLiteral();
		}
//		else if (rule.getIRI().toString().equals(OWLRefs.LEGACY_PREFIX + "#AssignActivityByGeoArea"))
//		{
//			OWLNamedIndividual srType = individual(bo.getTypeIRI());
//			OWLLiteral geoArea = dataProperty(srType, "legacy:hasGeoAreaCode");
//			
//		}		
		// All other rules:
		Set<OWLClass> S = reasoner().getTypes(rule, true).getFlattened();
		if (S.isEmpty())
			return null;
		OWLClass type = S.iterator().next();
		if (type.getIRI().equals(Model.legacy("AssignActivityToUserRule")))
		{
			OWLLiteral assignment = dataProperty(rule, "hasUsername");
			if (assignment != null)
				return assignment.getLiteral();
		}
		else if (type.getIRI().equals(Model.legacy("AssignActivityFromGeoAttribute")))
		{			
			// Get property info first
			Json locationInfo = Refs.gisClient.resolve().getLocationInfo(
					Double.parseDouble(bo.getDataProperty("hasXCoordinate").getLiteral()), 
					Double.parseDouble(bo.getDataProperty("hasYCoordinate").getLiteral()), 
					null);
			for (OWLNamedIndividual assignment : OWL.objectProperties(rule, "legacy:hasAssignmentRule"))
			{
				OWLLiteral attributeName = dataProperty(assignment, "hasName");
				OWLNamedIndividual layer = objectProperty(assignment, "hasGisLayer");
				OWLLiteral layerName = dataProperty(layer, "hasName");
				OWLLiteral valueExpression = dataProperty(assignment, "hasValue");
				OWLLiteral username = dataProperty(assignment, "hasUsername");
				if (Refs.gisClient.resolve().testLayerValue(locationInfo, layerName.getLiteral(), 
						  attributeName.getLiteral(), 
						  valueExpression.getLiteral()))
					return username.getLiteral();
			}
		} 
		else if (type.getIRI().equals(Model.legacy("AssignActivityToOutcomeEmail"))) 
		{
			return getAssignActivityToOutcomeEmail(outcome, bo);
		}
		return null;
	}
	
	/**
	 * Gets all activities defined for a ServiceCase type
	 * in the legacy ontology.
	 * 
	 * @param serviceCaseType
	 * @return A set containing the iri of each activity. 
	 * 
	 */
	public Set<OWLNamedIndividual> getActivities(OWLClass serviceCaseType)
	{
		Set<OWLNamedIndividual> activities = reasoner().getObjectPropertyValues(
				individual(serviceCaseType.getIRI()),
				objectProperty(fullIri("legacy:hasActivity"))
				).getFlattened();
		return activities;
	}
	
	/**
	 * Creates the serviceActivity axioms for the business object
	 * as defined by the ontology property isAutoCreate 'Y'
	 * for the activityType configuration of the
	 * supplied serviceCaseType.
	 * If the activity was disabled, it will not be created.  
	 * 
	 * @param serviceCaseType
	 * @param bo
	 */
	public void createDefaultActivities(OWLClass serviceCaseType, BOntology bo, Date createdDate, List<CirmMessage> messages)
	{
		for(OWLNamedIndividual activityType : getActivities(serviceCaseType))
		{
			if (utils.isAutoCreate(activityType) && !utils.isDisabled(activityType)) 
			{
				Date completedDate = null;
				OWLNamedIndividual outcome = null;
                //TODO Discuss if autoassign activites should have their default outcomes set.
				// Enable below if decision positive:
				// Maybe introduce property.
                //				if (utils.isAutoAssign(activityType)) {
                //				    outcome = utils.getDefaultOutcome(activityType);
                //				    //completedDate = createdDate;
                //				}
    			List<CirmMessage> localMessages = new ArrayList<CirmMessage>();
    			createActivity(activityType, outcome, null, null, bo, createdDate, completedDate, null, localMessages);
    			for (CirmMessage lm : localMessages) 
    			{
    				lm.addExplanation("createDefaultActivities T: " + serviceCaseType.getIRI().getFragment());
    				messages.add(lm);
    			}
			}
		} //for
		createActivitiesFromQuestions(bo, messages);
	}
	


	/** 
	 * Creates an activity now and ignores a potentially configured occurday setting. 
	 * This method is used to create already scheduled activities on TM callback.
	 * 
	 * @param activityType
	 * @param bo
	 * @param messages
	 */
	public void createActivityOccurNow(OWLNamedIndividual activityType, BOntology bo, List<CirmMessage> messages) {
		createActivityImpl(activityType, null, null, null, bo, null, null, null, messages, true);
	}

	/**
	 * Creates an activity or schdules it for creation (occurdays > 0).
	 * @param activityType
	 * @param details
	 * @param isAssignedTo
	 * @param bo
	 * @param createdDate
	 * @param createdBy
	 * @param messages
	 */
	public void createActivity(OWLNamedIndividual activityType, String details, String isAssignedTo, BOntology bo, Date createdDate, String createdBy, List<CirmMessage> messages)
	{
		//Don't set the defaultOutcome, first the activityType needs to be accepted by the Assignee!!
//		Set<OWLNamedIndividual> outcomes = reasoner().getObjectPropertyValues(
//				individual(activity.getIRI()),
//				objectProperty("legacy:hasDefaultOutcome"))
//				.getFlattened();
//		if(outcomes.size() > 0)
//			createActivity(activity, outcomes.iterator().next(), details, isAssignedTo, bo);
//		else
			createActivity(activityType, null, details, isAssignedTo, bo, createdDate, null, createdBy, messages);
	}
	
	
	/**
	 * Creates an activity or schedules it for creation (occurdays >0).
	 * 
	 * @param activityType
	 * @param outcome
	 * @param details
	 * @param isAssignedTo
	 * @param bo
	 * @param createdDate
	 * @param completedDate
	 * @param createdBy
	 * @param messages a list of messages to add messages to.
	 * @return a Pair of message and template
	 */
	public void createActivity(OWLNamedIndividual activityType, 
							   OWLNamedIndividual outcome, 
							   String details, 
							   String isAssignedTo, 
							   BOntology bo,
							   Date createdDate,
							   Date completedDate,
							   String createdBy,
							   List<CirmMessage> messages)
	{
		createActivityImpl(activityType, outcome, details, isAssignedTo, bo, createdDate, completedDate, createdBy, messages, false);
	}
	
	/**
	 * Creates an activity with all side effect. Delays creation through Time Machine, if activity type's occurdays 
	 * if configured as > 0.0f and ignoreOccurdays is false. 
	 * @param activityType
	 * @param outcome
	 * @param details
	 * @param isAssignedTo
	 * @param bo
	 * @param createdDate
	 * @param completedDate
	 * @param createdBy
	 * @param messages a list of messages to add messages to.
	 * @param ignoreOccurDays ignore activity type occur day setting and create activity now.
	 * @return a Pair of message and template
	 */
	private void createActivityImpl(OWLNamedIndividual activityType, 
							   OWLNamedIndividual outcome, 
							   String details, 
							   String isAssignedTo, 
							   BOntology bo,
							   Date createdDate,
							   Date completedDate,
							   String createdBy,
							   List<CirmMessage> messages,
							   boolean ignoreOccurDays)
	{
		try
		{
			OWLOntology o = bo.getOntology();
			OWLOntologyManager manager = o.getOWLOntologyManager();
			OWLDataFactory factory = manager.getOWLDataFactory();
			OWLClass activityTypeClass = owlClass("legacy:ServiceActivity");
			Calendar now = Calendar.getInstance();
			Calendar calcreated = Calendar.getInstance();
			calcreated.setTime(createdDate != null ? createdDate : now.getTime());
			boolean useWorkWeek = false;
			Set<OWLLiteral> businessCodes = reasoner().getDataPropertyValues(
					activityType,
					dataProperty("legacy:hasBusinessCodes"));
			
			if(businessCodes.size() > 0)
			{
				useWorkWeek = businessCodes.iterator().next().getLiteral().contains("5DAYWORK");
			}
			/**
			 * If activityType hasOccurDays > 0, the set a timer for delayed 
			 * activity creation
			 */
			Set<OWLLiteral> occurDays = reasoner().getDataPropertyValues(
					activityType,
					dataProperty("legacy:hasOccurDays"));
			if(occurDays.size() > 0 && !ignoreOccurDays)
			{
				try
				{
					float occurDay = occurDays.iterator().next().parseFloat();
					if (occurDay > 0)
					{
					Date delayedCreationDate = OWL.addDaysToDate(now.getTime(), occurDay, useWorkWeek);
						try
						{
							String serverUrl = getServerUrl();
							if(serverUrl != null)
							{	
									String path =  "/legacy/bo/"+ bo.getObjectId() + "/activities/create/"+ activityType.getIRI().getFragment();
									String fullUrl = serverUrl + path; 
									if (USE_TIME_MACHINE) 
									{
										Json post = Json.object();
										if(details != null)
											post.set("legacy:hasDetails", details);
										if(isAssignedTo != null)
											post.set("legacy:isAssignedTo",isAssignedTo);										
										String taskId = getNextTimeMachineTaskIDFor(path);
										if (DBG) System.out.println("ActManager: TM task " + taskId);
										Calendar delayedDateCal = Calendar.getInstance();
										delayedDateCal.setTime(delayedCreationDate);
										Json j = GenUtils.timeTask(taskId, delayedDateCal, fullUrl, post);
										if (j.is("ok", false))
											throw new RuntimeException("Time machine post returned false");
									}
							} 
							else
							{
								System.err.println("ActivityManager: " + activityType + " Server URL was NULL - Delayed activity creation failed! bo: " + bo.getObjectId());
							}
						}catch(Exception e)
						{
							System.out.println("Could not addTimer for activityType " + activityType.getIRI());
							if(DBG)
								e.printStackTrace(System.err);
							if (THROW_ALL_EXC) 
								throw new RuntimeException(e);
						}						
						return;//activity creation will be delayed.
					}
				}catch(NumberFormatException nfe)
				{
					System.err.println("ActivityManager: " + activityType + " parseFloat problem - Delayed activity creation failed! bo: " + bo.getObjectId());
					if (THROW_ALL_EXC) 
						throw new RuntimeException(nfe);
				}
			}
			OWLNamedIndividual serviceActivity = factory.getOWLNamedIndividual(
					fullIri(activityTypeClass.getIRI().getFragment() + Refs.idFactory.resolve().newId(null)));
			manager.addAxiom(o, factory.getOWLClassAssertionAxiom(activityTypeClass, serviceActivity));
			manager.addAxiom(o,factory.getOWLObjectPropertyAssertionAxiom(
								objectProperty("legacy:hasActivity")
								, serviceActivity, activityType));
			OWLLiteral createdDateLiteral = factory.getOWLLiteral(DatatypeFactory.newInstance()
										.newXMLGregorianCalendar((GregorianCalendar)calcreated)
										.toXMLFormat()
										,OWL2Datatype.XSD_DATE_TIME_STAMP);
			manager.addAxiom(o,
					factory.getOWLDataPropertyAssertionAxiom(
						dataProperty("hasDateCreated"), serviceActivity, 
						createdDateLiteral
					));
			manager.addAxiom(o,
					factory.getOWLDataPropertyAssertionAxiom(
						dataProperty("legacy:hasUpdatedDate"),serviceActivity, 
						createdDateLiteral
					));
			if(details != null)
			{
				manager.addAxiom(o,factory.getOWLDataPropertyAssertionAxiom(
						dataProperty("legacy:hasDetails")
						, serviceActivity, details));
			}
			
			if (isAssignedTo == null)
			{
				Set<OWLNamedIndividual> assignRules = OWL.objectProperties(activityType, 
															"legacy:hasAssignmentRule"); 
				for (OWLNamedIndividual rule : assignRules)
				{
					isAssignedTo = findAutoAssignment(rule, bo, serviceActivity, outcome);
					if (isAssignedTo != null)
						break;
				}
			}
			
			if(isAssignedTo != null)
			{
				manager.addAxiom(o, factory.getOWLDataPropertyAssertionAxiom(
						dataProperty("legacy:isAssignedTo"), serviceActivity, isAssignedTo));
			}
			
			if(createdBy != null)
			{
				manager.addAxiom(o,factory.getOWLDataPropertyAssertionAxiom(
						dataProperty("isCreatedBy")
						, serviceActivity, createdBy));
			}

			if(outcome != null || completedDate != null)
			{
				if (outcome == null)
					outcome = OWL.individual("legacy:OUTCOME_COMPLETE");
				manager.addAxiom(o,factory.getOWLObjectPropertyAssertionAxiom(
							 objectProperty("legacy:hasOutcome")
							, serviceActivity,outcome ));
				Calendar calcompleted = Calendar.getInstance();
				calcompleted.setTime(completedDate != null ? completedDate : calcreated.getTime()); 
				OWLLiteral completedDateLiteral = factory.getOWLLiteral(DatatypeFactory.newInstance()
						.newXMLGregorianCalendar((GregorianCalendar)calcompleted)
						.toXMLFormat()
						,OWL2Datatype.XSD_DATE_TIME_STAMP);				
				manager.addAxiom(o,
						factory.getOWLDataPropertyAssertionAxiom(
							dataProperty("legacy:hasCompletedTimestamp"),serviceActivity, 
							completedDateLiteral));
				List<CirmMessage> localMessages = new ArrayList<CirmMessage>();
				checkOutcomeTrigger(serviceActivity, activityType, outcome, isAssignedTo, bo, localMessages);
				for (CirmMessage lm : localMessages) 
				{
					lm.addExplanation("createactivity.checkoutcomeTrigger " + outcome.getIRI().getFragment() 
							+ " Act: " + activityType.getIRI().getFragment());
					messages.add(lm);
				}
			}

			Set<OWLLiteral> suspenseDays = reasoner().getDataPropertyValues(
					activityType,
					dataProperty("legacy:hasSuspenseDays"));
			if(suspenseDays.size() > 0)
			{
				try
				{
					float i = suspenseDays.iterator().next().parseFloat();
					if (i > 0)
					{
					Calendar due = Calendar.getInstance();
					due.setTime(OWL.addDaysToDate(now.getTime(), i, useWorkWeek));
					OWLLiteral dueDate = factory.getOWLLiteral(DatatypeFactory.newInstance()
							.newXMLGregorianCalendar((GregorianCalendar)due)
							.toXMLFormat()
							,OWL2Datatype.XSD_DATE_TIME_STAMP);

					manager.addAxiom(o,
							factory.getOWLDataPropertyAssertionAxiom(
									dataProperty("legacy:hasDueDate"),serviceActivity, 
								dueDate
							));
					Set<OWLNamedIndividual> overdueActivity = reasoner().getObjectPropertyValues(
							activityType,
							objectProperty("legacy:hasOverdueActivity"))
							.getFlattened();
						if(overdueActivity.size() > 0)
						{
							OWLNamedIndividual oa = overdueActivity.iterator().next();
							
							try
							{
								String serverUrl = getServerUrl();
								if(serverUrl != null)
								{	
									String path = "/legacy/bo/"+ 
											bo.getObjectId()
											+"/activity/"+serviceActivity.getIRI().getFragment()+"/overdue/create/"+ oa.getIRI().getFragment();
									String fullUrl = serverUrl + path;
									if (USE_TIME_MACHINE) 
									{
										//cannot use task, as serviceActivity will get new id on each retry. oa is type and therefore constant across retries.
										String almostTaskId = bo.getObjectId() + "act: " + activityType.getIRI().getFragment() + "/overdue/create/" + oa.getIRI().getFragment();  
										String taskId = getNextTimeMachineTaskIDFor(almostTaskId);
										if (DBG) System.out.println("ActManager: TM task " + taskId);
										Json j = GenUtils.timeTask(taskId, due, fullUrl, null);
										if (j.is("ok", false))
											throw new RuntimeException("Time machine post returned false");
									}
								}
							}catch(Exception e)
							{
								System.out.println("Could not addTimer for serviceActivity" + serviceActivity.getIRI().toString());
								if(DBG)
									e.printStackTrace(System.out);
								if (THROW_ALL_EXC) 
									throw new RuntimeException(e);
							}
						}
					}
				}catch(NumberFormatException nfe)
				{
					if(DBG)
						nfe.printStackTrace(System.err);
					if (THROW_ALL_EXC) 
						throw new RuntimeException(nfe);
				}
		}
		manager.addAxiom(o, factory.getOWLObjectPropertyAssertionAxiom(
						objectProperty("legacy:hasServiceActivity")
						, bo.getBusinessObject(), serviceActivity));
/*
			float orderBy = o.getAxioms(objectProperty("legacy:hasServiceActivity")).size();
			manager.addAxiom(o, factory.getOWLDataPropertyAssertionAxiom(
						dataProperty("legacy:hasOrderBy")
						, serviceActivity, factory.getOWLLiteral(orderBy)));
*/		
		OWLNamedIndividual emailTemplate = objectProperty(activityType, "legacy:hasEmailTemplate");
		if(emailTemplate != null && USE_MESSAGE_MANAGER)
		{
			if (hasAssignActivityToOutcomeEmail(activityType) && isAssignedTo == null)
			{
				//prevent email creation for serviceActivity as it should be created on a later update, where an outcome email is found.
				System.out.println("createActivity: email creation prevented, because serviceActivity " + serviceActivity + " Type: " + activityType + " hasAssignActivityToOutcomeEmail, was executed and still noone assigned.");
			} else 
			{
				CirmMessage m = MessageManager.get().createMessageFromTemplate(bo, dataProperty(activityType, "legacy:hasLegacyCode"), emailTemplate);
				if (m!= null) {
					m.addExplanation("createActivity " + serviceActivity.getIRI().getFragment() 
							+ " Tpl: " + emailTemplate.getIRI().getFragment());
					messages.add(m);
				}
				else
					System.err.println("ActivityManager: created Message was Null for " + (bo != null? bo.getObjectId() : bo) + "act: " + serviceActivity + " actT:" + activityType + " tmpl: " + emailTemplate);
			}
		} 
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
	private String getServerUrl()
	{
		try
		{
			OWLNamedIndividual operationsService = Refs.configSet.resolve().get("OperationsRestService");
			OWLLiteral osUrl = dataProperty(operationsService, "hasUrl");
			return osUrl.getLiteral();
		}catch(Exception e)
		{
			e.printStackTrace(System.out);
			if (THROW_ALL_EXC) 
				throw new RuntimeException(e);
			else
				return null;
		}
		
	}

	/**
	 * Updates the passed in Business Ontology's ServiceActivity Axioms with the passed in parameter values.
	 * Any parameter may be null.
	 * 
	 * @param activityType : String representation of the ActivityType
	 * @param serviceActivity : String representation of the ServiceActivity to be updated
	 * @param outcome : String representation of Outcome of the ServiceActivity
	 * @param details : Specified Details for the ServiceActivity by the user
	 * @param assignedTo : The person's email/name/eNet No. to whom this ServiceActivity is assigned 
	 * @param modifiedBy : The eNet No. of the User who sent in the request
	 * @param isAccepted : true if the Assignee accepts the ServiceActivity
	 * @param bo : The Business Ontology of the Service Request
	 */
	public void updateActivity(String activityType, String serviceActivity, String outcome, 
			String details, String assignedTo, String modifiedBy, boolean isAccepted, BOntology bo, List<CirmMessage> messages)
	{
		if(serviceActivity != null)
		{
			OWLOntology o = bo.getOntology();
			OWLOntologyManager manager = o.getOWLOntologyManager();
			OWLDataFactory factory = manager.getOWLDataFactory();
			OWLNamedIndividual serviceActivityIndividual = factory.getOWLNamedIndividual(fullIri(serviceActivity));
			OWLNamedIndividual outcomeIndividual = null;
			if(outcome != null)
				outcomeIndividual = factory.getOWLNamedIndividual(fullIri(outcome));
			//Apply default default outcome only if the ServiceActivity isAccepted by the Assignee
			if(outcome == null && isAccepted == true)
			{
				OWLNamedIndividual activityTypeInd = individual(activityType);
				Set<OWLNamedIndividual> outcomes = reasoner().getObjectPropertyValues(
						activityTypeInd, objectProperty("legacy:hasDefaultOutcome")).getFlattened();
				if(outcomes.size() > 0)
					outcomeIndividual = outcomes.iterator().next();
			}
			updateActivity(serviceActivityIndividual, outcomeIndividual, details, assignedTo, modifiedBy, bo, messages);
		}
	}

	/**
	 * Updates the passed in Business Ontology's ServiceActivity Axioms with the passed in parameter values.
	 * If any of the parameter values are null then that property is ignored.
	 * 
	 * @param serviceActivity : The ServiceActivity Individual to be updated 
	 * @param outcome : The Outcome Individual which is to be set as the Outcome of the ServiceActivity (can be null)
	 * @param details : Specified Details for the ServiceActivity by the user (can be null)
	 * @param assignedTo : The person's email/name/eNet No. to whom this ServiceActivity is assigned (can be null) 
	 * @param modifiedBy : The eNet No. of the User who sent in the request (can be null)
	 * @param bo : The Business Ontology of the Service Request
	 */
	public void updateActivity(OWLNamedIndividual serviceActivity, OWLNamedIndividual outcome, 
			String details, String assignedTo, String modifiedBy, BOntology bo, List<CirmMessage> messages)
	{
		boolean createMessageFromTemplate = false;
		try
		{
			OWLOntology o = bo.getOntology();
			OWLOntologyManager manager = o.getOWLOntologyManager();
			OWLDataFactory factory = manager.getOWLDataFactory();
			//06-20-2013 syed - Use the SR hasDateLastModified as the ServiceActivity hasUpdatedDate.
			OWLLiteral updatedDate = bo.getDataProperty("hasDateLastModified");
			
					//factory.getOWLLiteral(DatatypeFactory.newInstance()
						//				.newXMLGregorianCalendar((GregorianCalendar)Calendar.getInstance())
							//			.toXMLFormat()
								//		,OWL2Datatype.XSD_DATE_TIME_STAMP);
			bo.deleteDataProperty(serviceActivity, dataProperty("legacy:hasUpdatedDate"));
			manager.addAxiom(o,
					factory.getOWLDataPropertyAssertionAxiom(
						dataProperty("legacy:hasUpdatedDate"),serviceActivity, 
						updatedDate
					));
			if(details != null)
			{
				bo.deleteDataProperty(serviceActivity, dataProperty("legacy:hasDetails"));
				manager.addAxiom(o,factory.getOWLDataPropertyAssertionAxiom(
						dataProperty("legacy:hasDetails")
						, serviceActivity, details));
			}
			if(modifiedBy != null)
			{
				bo.deleteDataProperty(serviceActivity, dataProperty("isModifiedBy"));
				manager.addAxiom(o,factory.getOWLDataPropertyAssertionAxiom(
						dataProperty("isModifiedBy")
						, serviceActivity, modifiedBy));
			}
			if(outcome != null)
			{
				OWLNamedIndividual activityType = objectProperty(serviceActivity, 
															 "legacy:hasActivity",
															 bo.getOntology());
				if (activityType != null)
				{
					//HGDB fix. Might be impl; need HGDB for allProperties.
					activityType = OWL.individual(activityType.getIRI());
					bo.deleteObjectProperty(serviceActivity, objectProperty("legacy:hasOutcome"));
					manager.addAxiom(o,factory.getOWLObjectPropertyAssertionAxiom(
								 objectProperty("legacy:hasOutcome")
								, serviceActivity, outcome ));
					bo.deleteDataProperty(serviceActivity, dataProperty("legacy:hasCompletedTimestamp"));
					manager.addAxiom(o,
							factory.getOWLDataPropertyAssertionAxiom(
								dataProperty("legacy:hasCompletedTimestamp"),serviceActivity, 
								updatedDate
							));
					List<CirmMessage> localMessages = new ArrayList<CirmMessage>();
					checkOutcomeTrigger(serviceActivity, activityType, outcome, assignedTo, bo, localMessages);
					if (assignedTo == null) {
						if (hasAssignActivityToOutcomeEmail(activityType)) 
						{
							assignedTo = getAssignActivityToOutcomeEmail(outcome, bo);
							if (assignedTo != null)
							{ //hilpold assign here??? axiom
								createMessageFromTemplate = true;
							}							
						}
					}
					for (CirmMessage lm : localMessages) 
					{
						lm.addExplanation("updateActivity.checkoutcomeTrigger " + outcome.getIRI().getFragment()
								+ "Act: " + activityType.getIRI().getFragment());
						messages.add(lm);
					}
				}
			}
			//hilpold this has to happen after (!) hasAssignActivityToOutcomeEmail. 
			if(assignedTo != null)
			{
				bo.deleteDataProperty(serviceActivity, dataProperty("legacy:isAssignedTo"));
				manager.addAxiom(o,factory.getOWLDataPropertyAssertionAxiom(
						dataProperty("legacy:isAssignedTo")
						, serviceActivity, assignedTo));
			}
			//TODO: if time based activity then update time record.
			if (createMessageFromTemplate) 
			{
				OWLNamedIndividual activityType = objectProperty(serviceActivity, 
						 "legacy:hasActivity",
						 bo.getOntology());
				//HGDB error getting data properties for non HGDB individual activitytype; convert to hgdb:
				activityType = OWL.individual(activityType.getIRI());
				OWLNamedIndividual emailTemplate = objectProperty(activityType, "legacy:hasEmailTemplate");
				if(emailTemplate != null && USE_MESSAGE_MANAGER)
				{
					System.out.println("updateactivity & email: aType: " + activityType);
					CirmMessage m = MessageManager.get().createMessageFromTemplate(bo, dataProperty(activityType, "legacy:hasLegacyCode"), emailTemplate);
					if (m!= null) {
						m.addExplanation("updateActivity outcomeEmailAssign " + serviceActivity.getIRI().getFragment() 
								+ " AType: " + activityType 
								+ " Outcome: " + outcome
								+ " Tpl: " + emailTemplate.getIRI().getFragment());
						messages.add(m);
					}
					else
						System.err.println("ActivityManager: created Message was Null for " + (bo != null? bo.getObjectId() : bo) + " act:" + activityType + " tmpl: " + emailTemplate);
				} 
			}
		}catch (Exception e) {
			e.printStackTrace();
			if (THROW_ALL_EXC) 
				throw new RuntimeException(e);
		}
		
	}
	
	
	public void deleteActivity(OWLNamedIndividual serviceActivity, BOntology bo)
	{
		OWLOntology o = bo.getOntology();
		OWLOntologyManager manager = o.getOWLOntologyManager();
		OWLDataFactory factory = manager.getOWLDataFactory();
		manager.removeAxiom(o, factory.getOWLObjectPropertyAssertionAxiom(
						objectProperty("legacy:hasServiceActivity")
						, bo.getBusinessObject(), serviceActivity));
		manager.removeAxioms(o,o.getAxioms(serviceActivity));
	}
	
	private void checkOutcomeTrigger(OWLNamedIndividual serviceActivity, OWLNamedIndividual activityType, OWLNamedIndividual outcome, String assignedTo, BOntology bo , List<CirmMessage> messages)
	{
		//Create activityType triggers.
		triggerActivityAssignments(serviceActivity, activityType, outcome, assignedTo, bo, messages);
		//Create referral SRs.
		triggerReferralCaseOnOutcome(outcome, bo);
		//close service case on outcome
		triggerCloseCaseOnOutcome(serviceActivity, outcome, bo, messages);
		//Send email when status changes to X-Error
		triggerSendEmailOnOutCome(outcome, bo, messages);
	}
	
	/**
	 * Sends an email to WCS/Support group when an interface error is rejected by the department and the SR status is changed to X-Error
	 * @param outcome : The Outcome Individual which is to be set as the Outcome of the ServiceActivity (can be null)
	 * @param bo : The Business Ontology of the Service Request
	 * @param messages : 
	 */
	
	private void triggerSendEmailOnOutCome(OWLNamedIndividual outcome,
			BOntology bo, List<CirmMessage> messages) {
		if (outcome  == null) return;
		// ensure HGDB individual if not yet in repo:
		outcome = OWL.individual(outcome.getIRI());
		System.out.println("Outcome:"+outcome);
		if (!reasoner().getInstances(
					OWL.and(OWL.oneOf(outcome),	OWL.some(OWL.objectProperty("legacy:hasLegacyEvent"), OWL.owlClass("legacy:SendEmail"))),true).isEmpty())		
		{
			OWLNamedIndividual outcomeEvent = OWL.objectProperty(outcome, "legacy:hasLegacyEvent");
			System.out.println("outcomeEvent:"+outcomeEvent);
			OWLNamedIndividual emailTemplate = objectProperty(outcomeEvent,"legacy:hasEmailTemplate");
			System.out.println("emailTemplate:"+emailTemplate);
			Set<OWLNamedIndividual> srType =   objectProperties(outcomeEvent,"legacy:hasServiceCase");
			System.out.println("srType:"+srType);
			OWLIndividual boSRType = individual(bo.getTypeIRI("legacy"));
			System.out.println("boSRType:"+boSRType);
			if (emailTemplate != null && USE_MESSAGE_MANAGER && srType.contains(boSRType)) {
				System.out.println("Sending Email:");
				CirmMessage m = MessageManager.get().createMessageFromTemplate(bo, dataProperty(outcomeEvent, "legacy:hasLegacyCode"),emailTemplate);
				System.out.println("CirmMessage is : "+m);
				if (m != null) {
					m.addExplanation("triggerSendEmailOnOutCome " + outcome);
					messages.add(m);
				} else
					System.err.println("ActivityManager: Message is NULL");
			}
			else{
				System.out.println(boSRType+ " IS NOT AN INTERFACE SR TYPE: EMAIL WILL NOT BE SENT");
			}
		}
	}

	/**
	 * Closes a case using the activity completed date as status change date.
	 * @param serviceActivity
	 * @param outcome
	 * @param bo
	 */
	private void triggerCloseCaseOnOutcome(OWLNamedIndividual serviceActivity, OWLNamedIndividual outcome,
			BOntology bo, List<CirmMessage> messages)
	{
		if (serviceActivity == null) throw new IllegalArgumentException();
		if (outcome == null) throw new IllegalArgumentException();
		if (bo == null) throw new IllegalArgumentException();
		OWLNamedIndividual outcomeEvent = OWL.objectProperty(outcome, "legacy:hasLegacyEvent");
		if(outcomeEvent != null && outcomeEvent.getIRI().getFragment().equals("CloseServiceCase"))
		{
			OWLLiteral statusChangeModifiedOrCreatedBy;
			OWLNamedIndividual statusChangeStatus;
			OWLLiteral statusChangeDate;
			OWLDataFactory df = bo.getOntology().getOWLOntologyManager().getOWLDataFactory();
			
			// User / maybe department
			statusChangeModifiedOrCreatedBy = bo.getDataProperty(serviceActivity, "mdc:isModifiedBy");
			if(statusChangeModifiedOrCreatedBy == null) //the sr is new and not yet modified!
				statusChangeModifiedOrCreatedBy = bo.getDataProperty(serviceActivity, "mdc:isCreatedBy");
			// The new status
			statusChangeStatus = objectProperty(outcomeEvent, "legacy:hasStatus");
			// Use activtiy completion date as status change created, updated and completed date.
			statusChangeDate = bo.getDataProperty(serviceActivity, "legacy:hasCompletedTimestamp");
			
			// Validation
			if (statusChangeModifiedOrCreatedBy == null) {
				System.err.println("Error: triggerCloseCaseOnOutcome statusChangeModifiedOrCreatedBy could not be determined for act " + serviceActivity);
				System.err.println("Error: SR Number was: " + bo.getObjectId());
				statusChangeModifiedOrCreatedBy = df.getOWLLiteral(ACTIVITY_ERROR);
			}
			if (statusChangeStatus == null) {
				System.err.println("Error: triggerCloseCaseOnOutcome statusChangeStatus could not be determined for act " + serviceActivity.getIRI() + " and event " + outcomeEvent.getIRI());
				System.err.println("Error: SR Number was: " + bo.getObjectId());
				statusChangeStatus = df.getOWLNamedIndividual(fullIri("legacy:C-CLOSED")); 
			}
			if (statusChangeDate == null)
			{
				System.err.println("Error: triggerCloseCaseOnOutcome statusChangeDate could not be determined for act " + serviceActivity.getIRI() + " and event " + outcomeEvent.getIRI());
				System.err.println("Error: SR Number was: " + bo.getObjectId());
				System.err.println("Error: Using either SRs mdc:hasDateLastModified or mdc:hasDateCreated");
				statusChangeDate = bo.getDataProperty("mdc:hasDateLastModified");
				if (statusChangeDate == null) 
					statusChangeDate = bo.getDataProperty("mdc:hasDateCreated");
			}
			
			bo.deleteObjectProperty(bo.getBusinessObject(), "legacy:hasStatus");
			bo.addObjectProperty(bo.getBusinessObject(), "legacy:hasStatus", Json.object().set("iri", statusChangeStatus.getIRI().toString()));
			changeStatus(statusChangeStatus, GenUtils.parseDate(statusChangeDate), statusChangeModifiedOrCreatedBy.getLiteral(), bo, messages);
		}
	}

	private void triggerReferralCaseOnOutcome(OWLNamedIndividual outcome,
			BOntology bo)
	{
		OWLClassExpression q = and(owlClass("legacy:ServiceCaseOutcomeTrigger"),
				OWL.has(objectProperty("legacy:hasServiceCase"), individual(bo.getTypeIRI("legacy"))),
				OWL.has(objectProperty("legacy:hasOutcome"), individual(outcome.getIRI())),
				OWL.some(objectProperty("legacy:hasLegacyEvent"), owlClass("legacy:CreateServiceCase")));
		Set<OWLNamedIndividual> createCaseTriggers = reasoner().getInstances(q, false).getFlattened();
		for(OWLNamedIndividual trigger : createCaseTriggers)
		{
			Set<OWLNamedIndividual> events = reasoner().getObjectPropertyValues(trigger, objectProperty("legacy:hasLegacyEvent")).getFlattened();
			for(OWLNamedIndividual event: events)
			{
			
				OWLNamedIndividual srTypeToCreate = objectProperty(event ,"legacy:hasServiceCase");
				OWLNamedIndividual statusToSet = objectProperty(event ,"legacy:hasStatus");
				if(srTypeToCreate != null && statusToSet != null)
				{
					Json referrer = bo.toJSON();
					createReferralCase(Long.parseLong(bo.getObjectId()),referrer, OWL.owlClass(srTypeToCreate.getIRI()), statusToSet);
				}
			}
		}
	}

	private void triggerActivityAssignments(OWLNamedIndividual serviceActivity, OWLNamedIndividual activityType,
			OWLNamedIndividual outcome, String assignedTo, BOntology bo, List<CirmMessage> messages)
	{
		OWLClassExpression q = and(owlClass("legacy:ActivityTrigger"),
								OWL.has(objectProperty("legacy:hasActivity"), individual(activityType.getIRI())),
								OWL.has(objectProperty("legacy:hasOutcome"), individual(outcome.getIRI())),
								OWL.some(objectProperty("legacy:hasLegacyEvent"), owlClass("legacy:ActivityAssignment")));
		Set<OWLNamedIndividual> triggers = reasoner().getInstances(q, false).getFlattened();
		Date actCompletedDate;
		Date newActCreatedDate;
		try {
			actCompletedDate = GenUtils.parseDate(bo.getDataProperty(serviceActivity, "legacy:hasCompletedTimestamp"));
			if (actCompletedDate == null) throw new IllegalStateException("ServiceActivity must be completed and have a completed date.");
			//12-20-2013 tom/syed/boris Use the triggering activity's completed date as the new activities created date.
			newActCreatedDate = actCompletedDate;
		} catch (Exception e)
		{
			String msg = "triggerActivityAssignments could not determine dates for serviceActivity: " + serviceActivity.getIRI() 
					+ " Type: " + activityType.getIRI() 
					+ " Exc was: " + e.toString();
			throw new RuntimeException(msg, e);
		}
		for(OWLNamedIndividual trigger : triggers)
		{
			Set<OWLNamedIndividual> events = reasoner().getObjectPropertyValues(trigger, objectProperty("legacy:hasLegacyEvent")).getFlattened();
			for(OWLNamedIndividual event: events)
			{
				OWLNamedIndividual a = reasoner().getObjectPropertyValues(
							event,
							objectProperty("legacy:hasActivity"))
							.getFlattened().iterator().next();
				Set<OWLNamedIndividual> outcomes = reasoner().getObjectPropertyValues(
						event,
						objectProperty("legacy:hasOutcome"))
						.getFlattened();
				//01-07-2014 - removed per Liz's request.
//				if(outcomes.isEmpty())
//				{
//					//01-03-2014 Syed - If no outcome is configured for the event, then check the default outcome
//					//of the activity
//					outcomes = reasoner().getObjectPropertyValues(a, objectProperty("legacy:hasDefaultOutcome")).getFlattened();
//				}
				Set<OWLLiteral> businessCodes = reasoner().getDataPropertyValues(
						a, dataProperty("legacy:hasBusinessCodes"));
				boolean dupStaff = false;
				if(businessCodes.size() > 0)
				{
					dupStaff = businessCodes.iterator().next().getLiteral().contains("DUPSTAFF");
				}
				List<CirmMessage> localMessages = new ArrayList<CirmMessage>();
				if(outcomes.size() > 0)
					createActivity(a, outcomes.iterator().next(), null, (dupStaff) ? assignedTo : null, bo, newActCreatedDate, null, ACTIVITY_AUTO, localMessages);
				else
					createActivity(a, null, null, (dupStaff) ? assignedTo : null, bo, newActCreatedDate, null, ACTIVITY_AUTO, localMessages);
				for (CirmMessage lm : localMessages) 
				{
					lm.addExplanation("triggerActivityAssignments " + assignedTo 
							+ " Act: " + serviceActivity.getIRI().getFragment() 
							+ " ActType: " + activityType.getIRI().getFragment() 
							+ " Tri: " + trigger.getIRI().getFragment()
							+ " Eve: " + event.getIRI().getFragment());
					messages.add(lm);
				}
			}
		}
	}
	
	/**
	 * Creates a Referral Case 
	 * 
	 * @param referringCase
	 * @param owlClass - SR Type to 
	 * @param statusToSet
	 */
	
	private void createReferralCase(Long referringCaseId, Json referringCase, OWLClass srType,
			OWLNamedIndividual statusToSet)
	{
		LegacyEmulator emulator = new LegacyEmulator();
		Json newCase = Json.object("properties", Json.object());
		Json props = newCase.at("properties");
		newCase.set("type", "legacy:"+srType.getIRI().getFragment());
		GenUtils.timeStamp(props);
		props.set("legacy:hasStatus", 
				Json.object("iri", statusToSet.getIRI().toString()));
		props.set("legacy:hasParentCaseNumber", referringCaseId);
		Json rprops = referringCase.at("properties");
		if(rprops.has("atAddress"))
			props.set("atAddress",rprops.at("atAddress"));
		if(rprops.has("hasXCoordinate"))
			props.set("hasXCoordinate",rprops.at("hasXCoordinate"));
		if(rprops.has("hasYCoordinate"))
			props.set("hasYCoordinate",rprops.at("hasYCoordinate"));
		//TODO: not sure if this is needed.
		//expandIris(data);
		// remove properties that should be ignore or have been taken care of above already
		//validateAddresses(data); // we still need to go through actors' address etc.
		
		// set properties to the parent.
		if (rprops.has("hasPriority"))
			props.set("legacy:hasPriority", rprops.at("hasPriority"));
		if (rprops.has("hasIntakeMethod"))
			props.set("legacy:hasIntakeMethod", rprops.at("hasIntakeMethod"));
		Json result = emulator.saveNewServiceRequest(newCase.toString());
		try
		{
			Set<OWLNamedIndividual> interfaces = 
				    reasoner().getInstances(
						and(owlClass("legacy:LegacyInterface"), 
						    has(objectProperty("legacy:hasAllowableEvent"), individual("legacy:NEWSR")),
						    has(objectProperty("legacy:isLegacyInterface"), individual(newCase.at("type").asString()))), false).getFlattened();
			
			if (!interfaces.isEmpty())
			{
				OWLNamedIndividual LI = interfaces.iterator().next();							
				result.at("data").set("hasLegacyInterface", Json.object("hasLegacyCode", OWL.dataProperty(LI, "legacy:hasLegacyCode").getLiteral()));
			}
//			JMSClient.connectAndSend(LegacyMessageType.NewCase, 
//					((DBIDFactory) Refs.idFactory.resolve()).generateSequenceNumber(), result);
		}catch(Exception e)
		{
			System.err.println("Referral Case send to queue interface exception");
			e.printStackTrace(System.err);
			if (THROW_ALL_EXC) 
				throw new RuntimeException(e);
		}
	}

	/**
	 * Creates the serviceActivity axioms for the business object
	 * based on Fields and Answers that have been configured 
	 * to generate activities.
	 * @param bo
	 */
	private void createActivitiesFromQuestions(BOntology bo, List<CirmMessage> messages)
	{
		OWLOntology ontology = bo.getOntology();
		OWLNamedIndividual businessObject  =  bo.getBusinessObject();
		OWLDataFactory factory = ontology.getOWLOntologyManager().getOWLDataFactory();

		Set<OWLIndividual> answers = businessObject.getObjectPropertyValues(factory.getOWLObjectProperty(fullIri("legacy:hasServiceAnswer")), ontology);
		for(OWLIndividual answer : answers)
		{
			Set<OWLIndividual> fields = answer.getObjectPropertyValues(factory.getOWLObjectProperty(fullIri("legacy:hasServiceField")), ontology);
			if(fields.isEmpty())
				continue;
			OWLNamedIndividual field = individual(fields.iterator().next().asOWLNamedIndividual().getIRI());
			List<CirmMessage> localMessages = new ArrayList<CirmMessage>();
			triggerActivityAssignmentsOnAnswer(field, answer, bo, localMessages);
			for (CirmMessage lm : localMessages) 
			{
				lm.addExplanation("createActivitiesFromQuestions F: " + field.getIRI().getFragment()
						+ "Ans: " + (answer.isNamed()? answer.asOWLNamedIndividual().getIRI().getFragment() : "anonymous"));
				messages.add(lm);
			}
		}
	}

	private void triggerActivityAssignmentsOnAnswer(
			OWLNamedIndividual field, OWLIndividual answer,
			BOntology bo, List<CirmMessage> messages)
	{
		OWLOntology ontology = bo.getOntology();
		OWLDataFactory factory = ontology.getOWLOntologyManager().getOWLDataFactory();
		Set<OWLNamedIndividual> triggers = reasoner().getObjectPropertyValues(field, objectProperty("legacy:hasActivityAssignment")).getFlattened();
		if(triggers.isEmpty())
			return;
		Set<OWLIndividual> answerObjects = answer.getObjectPropertyValues(factory.getOWLObjectProperty(fullIri("legacy:hasAnswerObject")), ontology);
		Set<OWLLiteral> answerValues = answer.getDataPropertyValues(factory.getOWLDataProperty(fullIri("legacy:hasAnswerValue")), ontology);
		
		for(OWLNamedIndividual trigger :triggers)
		{
			
			Set<OWLNamedIndividual> triggerAnswerObjects = reasoner().getObjectPropertyValues(trigger, objectProperty("legacy:hasAnswerObject")).getFlattened();
			Set<OWLLiteral> triggerAnswerValues = reasoner().getDataPropertyValues(trigger, dataProperty("legacy:hasAnswerValue"));
			
			for(OWLLiteral triggerAnswer : triggerAnswerValues)
			{
				if(answerValues.size() > 0)
				{
					String answerValue = answerValues.iterator().next().getLiteral();
					//When the hasAnswervalue in the ontology doesn't matter, it was given a value of "any"
					if(triggerAnswer.getLiteral().equalsIgnoreCase("any"))
					{
						createActivityFromAnswer(bo, messages, trigger, field, answerValue, null);
					}
					//TODO : The below commented "else if" can be modified and used for other requirements 
					//Saw another QuestionTrigger individual "1370732305" with hasAnswerValue
					// No idea what the requirement was, ie, Do we need to persist the hasAnswerValue
					// into any Activity field or not persist at all.
					//else if(triggerAnswer.getLiteral().equals(answerValue)) {
						//createActivityFromAnswer(bo, messages, trigger, field, answerValue, null);
					//}
				}

			}

			for(OWLNamedIndividual triggerAnswer: triggerAnswerObjects )
			if(answerObjects.contains(triggerAnswer))
			{
				createActivityFromAnswer(bo, messages, trigger, field, null, triggerAnswer);
			}
		}
	}
	
	private void createActivityFromAnswer(BOntology bo, List<CirmMessage> messages, 
			OWLNamedIndividual trigger, OWLNamedIndividual field, 
			String details, OWLNamedIndividual triggerAnswer) {
	
		Set<OWLNamedIndividual> events = reasoner().getObjectPropertyValues(trigger, objectProperty("legacy:hasLegacyEvent")).getFlattened();
		for(OWLNamedIndividual event: events)
		{
			Set<OWLNamedIndividual> activities = reasoner().getObjectPropertyValues(
					event,
					objectProperty("legacy:hasActivity"))
					.getFlattened();
			if(!activities.isEmpty())
			{
				OWLNamedIndividual a =	activities.iterator().next();
				Set<OWLNamedIndividual> outcomes = reasoner().getObjectPropertyValues(
						event,
						objectProperty("legacy:hasOutcome"))
						.getFlattened();
				List<CirmMessage>localMessages = new ArrayList<CirmMessage>();
				if(outcomes.size() > 0)
					createActivity(a, outcomes.iterator().next(), details, null, bo, null, null, null, localMessages);
				else
					createActivity(a, null, details, null, bo, null, null, null, localMessages);
				for (CirmMessage lm : localMessages) 
				{
					lm.addExplanation("triggerActivityAssignmentsOnAnswer F:" + field.getIRI().getFragment() + " A:" + triggerAnswer.getIRI().getFragment()
							+ " TriggerAns: " + triggerAnswer.getIRI().getFragment() 
							+ " Event: " + event.getIRI().getFragment() 
							+ " Activity: " + a.getIRI().getFragment());
					messages.add(lm);
				}
			}
		}

	}
	
		
	public void changeStatus(OWLNamedIndividual newStatus, Date statusChangeDate, String statusChangedBy, BOntology bo, List<CirmMessage> messages)
	{
		OWLNamedIndividual statusChange = individual("legacy:StatusChangeActivity");
		createActivity(statusChange, newStatus, null, null, bo, statusChangeDate, statusChangeDate, statusChangedBy, messages);
	}
	
	/**
	 * Set's the assign field of an activity based on an email found in the outcome label,
	 * if the serviceActivity is an autoAssign activity and the activityType has an OutcomeEmailAssignmentRule 
	 * and an outcome was selected that contains an email address
	 * 
	 * @param bo
	 * @param activity
	 */
	private String getAssignActivityToOutcomeEmail(OWLNamedIndividual outcome, BOntology bo)
	{
		if (outcome == null) return null; //throw exc later
		if (bo == null) return null; //throw exc later
		// Check if activityType has a rule that assigns an outcome email to the activity's assigned to field
		String result = null;
		//1. Get Outcome and outcome label
		//2. check if outcome label contains email
		//3. return email address only
		String outcomeLabel = "NULL";
		if (outcome != null)
		{
			outcomeLabel = OWL.getEntityLabel(outcome);
			if (outcomeLabel != null && outcomeLabel.contains("@")) 
			{
				result = GenUtils.findEmailIn(outcomeLabel);
			}
		}
		System.out.println("AssignActivityToOutcomeEmail for outcome: " + outcome + " olabel: " + outcomeLabel);
		return result;
	}
	
	/**
	 * For activity types with templates and an AssignActivityToOutcomeEmail rule,
	 * emails should be sent only after update, not on creation of the activity unless there is a default outcome. 
	 * @param activityType
	 * @param bo
	 * @return
	 */
	private boolean hasAssignActivityToOutcomeEmail(OWLNamedIndividual activityType)
	{
		Set<OWLNamedIndividual> assignRules = OWL.objectProperties(activityType, 
				"legacy:hasAssignmentRule"); 
		for (OWLNamedIndividual rule : assignRules)
		{
			if (Model.legacy("AssignActivityToOutcomeEmail").equals(rule.getIRI()))
				return true;
		}
		return false;
	}

	
	public static void main(String [] argv)
	{
		
	}
}

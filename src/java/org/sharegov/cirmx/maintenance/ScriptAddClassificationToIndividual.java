package org.sharegov.cirmx.maintenance;

import java.util.HashSet;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.legacy.ServiceCaseManager;

public abstract class ScriptAddClassificationToIndividual {

	
	private String ontoDirectory;
	private ServiceCaseManager serviceCaseManager;
	
	
	
	public static final ScriptAddClassificationToIndividual TEST = new ScriptAddClassificationToIndividual()
	{
		@Override
		public void init() 
		{
			/**
			 * To use this class, just modify the startup
			 * config directory. Static init has unexpected
			 * behavior as core classes(Startup) have changed.
			 */
//			StartUp.getConfig().set("ontologyConfigSet", "http://www.miamidade.gov/ontology#TestConfigSet");
//			setOntoDirectory("C:/Work/scripts/testdb");
//			StartUp.getConfig().set("metaDatabaseLocation", getOntoDirectory());
		}
	};
	

	public static final ScriptAddClassificationToIndividual PROD = new ScriptAddClassificationToIndividual()
	{
		@Override
		public void init()
		{
//			StartUp.getConfig().set("ontologyConfigSet", "http://www.miamidade.gov/ontology#ProdConfigSet");
//			setOntoDirectory("C:/Work/scripts/proddb");
//			StartUp.getConfig().set("metaDatabaseLocation", getOntoDirectory());
		}
	};
	
	
	public abstract void init();
	
	public void run() {
		serviceCaseManager = ServiceCaseManager.getInstance();
		Set<OWLNamedIndividual> unclassifiedActivities = findAllUnclassifiedActivities();
		serviceCaseManager.classifyAsActivity(unclassifiedActivities, "SABBAS", null);
		Set<OWLNamedIndividual> unclassifiedLegacyTriggers = findAllUnclassifiedQuestionTriggers();
		serviceCaseManager.classifyAsQuestionTrigger(unclassifiedLegacyTriggers, "SABBAS", null);
	}
		
	public static void main(String[] args) {
		
		TEST.run();
		//PROD.run();
	}
	
	public Set<OWLNamedIndividual> findAllUnclassifiedActivities() {
		Set<OWLNamedIndividual> activityIndividualsMissingClassDeclarations = new HashSet<OWLNamedIndividual>();
		for(OWLNamedIndividual activity: serviceCaseManager.getAllActivityIndividuals())
		{
			Set<OWLClassExpression> classes = activity.getTypes(OWL.ontology().getImportsClosure());

				if(!classes.contains(OWL.owlClass("legacy:Activity")))
				{
					activityIndividualsMissingClassDeclarations.add(activity);
				}else
				{
					if(activityIndividualsMissingClassDeclarations.contains(activity))
						activityIndividualsMissingClassDeclarations.remove(activity);
			}
		}

		return activityIndividualsMissingClassDeclarations;
	}
	

	public Set<OWLNamedIndividual> findAllUnclassifiedQuestionTriggers() {
		
		
		Set<OWLNamedIndividual> questionTriggerIndividualsMissingClassDeclarations = new HashSet<OWLNamedIndividual>();
		for(OWLNamedIndividual serviceQuestion : OWL.reasoner().getInstances(OWL.parseDL("legacy:ServiceQuestion and legacy:hasActivityAssignment min 1", OWL.ontology()),true).getFlattened())
		{
			for(OWLNamedIndividual legacyTrigger: OWL.objectProperties(serviceQuestion, "legacy:hasActivityAssignment"))
			{
				Set<OWLClassExpression> classes = legacyTrigger.getTypes(OWL.ontology().getImportsClosure());

				if(classes.size() == 0)
				{
					questionTriggerIndividualsMissingClassDeclarations.add(legacyTrigger);
				}
				else
				{
					//empty class axiom declaration but proper type not known
					System.out.println(legacyTrigger.getIRI().toString());
				}
			}
		}

		return questionTriggerIndividualsMissingClassDeclarations;
	}
	
	
	public String getOntoDirectory() {
		return ontoDirectory;
	}

	public void setOntoDirectory(String ontoDirectory) {
		this.ontoDirectory = ontoDirectory;
	}

	public ServiceCaseManager getServiceCaseManager() {
		return serviceCaseManager;
	}

	public void setServiceCaseManager(ServiceCaseManager serviceCaseManager) {
		this.serviceCaseManager = serviceCaseManager;
	}
}

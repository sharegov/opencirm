package gov.miamidade.cirm.other;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.event.EventTrigger;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import gov.miamidade.cirm.MDRefs;
import mjson.Json;

/**
 * Handles ServiceCaseUpdateEvents by sending an updated SR to live reporting.
 * @author Thomas Hilpold
 *
 */
public class CaseUpdateEventHandler implements EventTrigger {

	 private static Logger logger = Logger.getLogger("gov.miamidade.cirm.other");

	    @Override
	    public void apply(OWLNamedIndividual entity, OWLNamedIndividual changeType, Json data)
	    {
	    	//assert changeType == BO_UPDATE
	        if (MDRefs.liveReportingStatus.resolve().isEnabled()) {
	        	ThreadLocalStopwatch.now("START CaseUpdateEventHandler entity: " + entity + " changetype: " + changeType);
	        	try {
	        		LiveReportingSender sender = new LiveReportingSender();
	        		sender.sendUpdatedServiceRequestToReporting(data);
	        	} catch (Exception e){
	        		//We only post a warning here.
	        		logger.log(Level.WARNING, "sendUpdatedServiceRequestToReporting failed with " + e);
	        		e.printStackTrace();
	        	}
	        	ThreadLocalStopwatch.now("END CaseUpdateEventHandler");
	        }
	    }
}

package gov.miamidade.cirm.other;

import gov.miamidade.cirm.MDRefs;

import java.util.logging.Level;

import javax.jms.JMSException;

import org.sharegov.cirm.Refs;
import org.sharegov.cirm.rdb.DBIDFactory;
import org.sharegov.cirm.stats.CirmStatisticsFactory;
import org.sharegov.cirm.stats.SRCirmStatsDataReporter;

import mjson.Json;

/**
 * Simple Class responsible for sending a new SR for purposes of live reporting.
 *
 * Uses MDStats LiveReportingSender/sendNewServiceRequestToReporting and /sendUpdatedServiceRequestToReporting
 * 
 * @author Thomas Hilpold
 *
 */
public class LiveReportingSender
{
	
	private SRCirmStatsDataReporter srStatsReporter = 
			CirmStatisticsFactory.createServiceRequestStatsReporter(MDRefs.mdStats.resolve(), "LiveReportingSender"); 

	
	public void sendNewServiceRequestToReporting(Json newSR) {
        //Json data = newSR; //formatNewCaseForDepartments(caseData, locationInfo);
        try
        {
            JMSClient.connectAndSendToReporting(LegacyMessageType.NewCase, 
                ((DBIDFactory) Refs.idFactory.resolve()).generateSequenceNumber(), newSR);
            srStatsReporter.succeeded("sendNewServiceRequestToReporting", newSR);
        }
        catch (JMSException ex)
        {
            srStatsReporter.failed("sendNewServiceRequestToReporting", newSR, "" + ex, "JMSCLIENT failed" + ex.getMessage());
        	// We do need to report the error, but we also have to retry at a later time.
            Refs.logger.resolve().log(Level.SEVERE, "While sending case " + newSR.at("hasServiceCase") + " to departments.", ex);
            throw new RuntimeException("sendNewServiceRequestToReporting", ex);
        }        
	}

	public void sendUpdatedServiceRequestToReporting(Json updatedSR) {
        try
        {
            JMSClient.connectAndSendToReporting(LegacyMessageType.CaseUpdate, 
                ((DBIDFactory) Refs.idFactory.resolve()).generateSequenceNumber(), updatedSR);
            srStatsReporter.succeeded("sendUpdatedServiceRequestToReporting", updatedSR);
        }
        catch (JMSException ex)
        {
            srStatsReporter.failed("sendUpdatedServiceRequestToReporting", updatedSR, "" + ex, "JMSCLIENT failed" + ex.getMessage());
        	// We do need to report the error, but we also have to retry at a later time.
            Refs.logger.resolve().log(Level.SEVERE, "While sending case " + updatedSR.at("hasServiceCase") + " to departments.", ex);
            throw new RuntimeException("sendUpdatedServiceRequestToReporting", ex);
        }        
	}
}

package org.sharegov.cirm.process;

import mjson.Json;

import org.sharegov.cirm.BOUtils;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.CirmTransactionEvent;
import org.sharegov.cirm.CirmTransactionListener;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.event.EventDispatcher;
import org.sharegov.cirm.rest.LegacyEmulator;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

public class AddTxnListenerForNewSR implements ApprovalSideEffect
{

	@Override
	public void execute(final ApprovalProcess approvalProcess)
	{
		  LegacyEmulator emulator = new LegacyEmulator();
		  final Json result = approvalProcess.getBOntology().toJSON();
          emulator.addAddressData(result);
			CirmTransaction.get().addTopLevelEventListener(new CirmTransactionListener() {
			    public void transactionStateChanged(final CirmTransactionEvent e)
			    {
			    	if (e.isSucceeded())
			    	{
			    		BOntology bontology = approvalProcess.getBOntology();
			    		try
			    		{
			    			BOntology withMeta = approvalProcess.getWithMetadata().isEmpty() ?
					    			   BOUtils.addMetaDataAxioms(approvalProcess.getBOntology()):approvalProcess.getWithMetadata().get(0);
						        EventDispatcher.get().dispatch(
						                OWL.individual("legacy:NewServiceCaseEvent"), 
						                bontology.getBusinessObject(), 
						                OWL.individual("BO_New"),
						                Json.object("case", OWL.toJSON(withMeta.getOntology(), bontology.getBusinessObject()),
						                            "locationInfo", approvalProcess.getLocationInfo()));
			    		}
			    		catch (Exception ex)
			    		{
			    			GenUtils.reportFatal("Failed to send case " + bontology.getObjectId() + " to department", "", ex);
							ThreadLocalStopwatch.error("Error createNewKOSR - Failed to send to dept " + bontology.getObjectId());
			    		}
			    	}
			    }
			});
		
	}
	
}

package org.sharegov.cirm.process;

import static org.sharegov.cirm.OWL.owlClass;

import org.restlet.Response;
import org.sharegov.cirm.legacy.ActivityManager;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

public class CreateDefaultActivities implements ApprovalSideEffect
{

	
	
	
	
	@Override
	public void execute(ApprovalProcess approvalProcess)
	{
		Response current = Response.getCurrent();
		ThreadLocalStopwatch.now("START createDefaultActivities (approval process)");
		ActivityManager am = new ActivityManager();
		am.createDefaultActivities(owlClass(approvalProcess.getSr().at("type").asString())
				, approvalProcess.getBOntology(), 
				GenUtils.parseDate(approvalProcess.getSr().at("properties").at("hasDateCreated").asString()),
				approvalProcess.getEmailsToSend());
		Response.setCurrent(current);
		ThreadLocalStopwatch.now("END createDefaultActivities  (approval process)");
	}

}

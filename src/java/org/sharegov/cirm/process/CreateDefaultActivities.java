package org.sharegov.cirm.process;

import static org.sharegov.cirm.OWL.owlClass;

import java.util.Date;

import org.restlet.Response;
import org.sharegov.cirm.legacy.ActivityManager;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

/**
 * Creates default activities based on a given approval date.
 * This ensures that all overdue activity calculation and email activities are based on the correct date.
 * 
 * All auto created activities' createdDate will match the given approval date.
 * 
 * (SR Created date is ignored) 
 * 
 * @author Syed Abbas, Thomas Hilpold
 */
public class CreateDefaultActivities implements ApprovalSideEffect {

	private final Date approvalDate;
	
	public CreateDefaultActivities(Date approvalDate) {
		if (approvalDate == null) throw new IllegalArgumentException("Approval date null");
		this.approvalDate = approvalDate;
	}
	@Override
	public void execute(ApprovalProcess approvalProcess) {
		Response current = Response.getCurrent();
		ThreadLocalStopwatch.now("START createDefaultActivities (approval process)");
		ActivityManager am = new ActivityManager();
		am.createDefaultActivities(owlClass(approvalProcess.getSr().at("type").asString()),
				approvalProcess.getBOntology(),
				approvalDate,
				approvalProcess.getMsgsToSend());
		Response.setCurrent(current);
		ThreadLocalStopwatch.now("END createDefaultActivities  (approval process)");
	}

}

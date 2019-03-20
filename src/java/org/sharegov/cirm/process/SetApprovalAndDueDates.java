package org.sharegov.cirm.process;

import java.util.Date;

import org.sharegov.cirm.utils.DueDateUtil;
import org.sharegov.cirm.utils.GenUtils;

import mjson.Json;

/**
 * Sets the approval date of an SR during approval and calculates/sets SR due date based on approval date.
 *  
 * @author Thomas Hilpold
 */
public class SetApprovalAndDueDates implements ApprovalSideEffect {
	
	private final Date approvalDate;
	private static final DueDateUtil dueDateUtil = new DueDateUtil();
	
	public SetApprovalAndDueDates(Date approvalDate) {
		if (approvalDate == null) throw new IllegalArgumentException("Approval date null");
		this.approvalDate = approvalDate;
	}

	@Override
	public void execute(ApprovalProcess approvalProcess) {
		Json sr = approvalProcess.getSr();
		String approvalDateStr = GenUtils.formatDate(approvalDate);
		sr.at("properties").set("hasDateApproved", approvalDateStr);
		//Set due date
		dueDateUtil.setDueDateExistingSr(sr, approvalDate);
	}

}

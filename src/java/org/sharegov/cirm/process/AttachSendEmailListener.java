package org.sharegov.cirm.process;

import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.utils.SendEmailOnTxSuccessListener;

public class AttachSendEmailListener implements ApprovalSideEffect
{

	@Override
	public void execute(ApprovalProcess approvalProcess)
	{
		approvalProcess.getEmailsToSend().clear();
		CirmTransaction.get().addTopLevelEventListener(new SendEmailOnTxSuccessListener(approvalProcess.getEmailsToSend()));
	}

}

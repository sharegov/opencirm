package org.sharegov.cirm.process;

import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.utils.SendMessagesOnTxSuccessListener;

public class AttachSendEmailListener implements ApprovalSideEffect
{

	@Override
	public void execute(ApprovalProcess approvalProcess)
	{
		approvalProcess.getMsgsToSend().clear();
		CirmTransaction.get().addTopLevelEventListener(new SendMessagesOnTxSuccessListener(approvalProcess.getMsgsToSend()));
	}

}

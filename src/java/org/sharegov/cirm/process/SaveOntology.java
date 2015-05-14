package org.sharegov.cirm.process;

import static org.sharegov.cirm.rest.OperationService.getPersister;

public class SaveOntology implements ApprovalSideEffect
{

	@Override
	public void execute(ApprovalProcess approvalProcess)
	{
		getPersister().saveBusinessObjectOntology(approvalProcess.getBOntology().getOntology());	
		
	}

}

package org.sharegov.cirm.process;

import org.sharegov.cirm.rest.LegacyEmulator;

import mjson.Json;

public class PopulateGisData implements ApprovalSideEffect
{

	@Override
	public void execute(ApprovalProcess approvalProcess)
	{
		LegacyEmulator emulator = new LegacyEmulator();
		if(approvalProcess.getLocationInfo() == null)
			approvalProcess.setLocationInfo(Json.object());
		Json locationInfoTmp = emulator.populateGisData(approvalProcess.getSr(), approvalProcess.getBOntology());
		if (!locationInfoTmp.isNull())
			approvalProcess.getLocationInfo().with(locationInfoTmp);

	}

}

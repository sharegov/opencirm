package org.sharegov.cirm.process;

import java.util.ArrayList;
import java.util.List;

import mjson.Json;

/**
 * Simple State Machine that represents the ApprovalProcess of self service SRs.
 * 
 * Approval state transitions:
 * 
 * APPROVAL_PENDING > APPROVED
 * APPROVAL_NOT_NEEDED
 * 
 * @author SABBAS
 * 
 **/
public class ApprovalProcess
{
	public enum ApprovalState
	{
		APPROVAL_PENDING, APPROVED, APPROVAL_NOT_NEEDED
	};

	private Json sr;
	private ApprovalState approvalState;
	private List<ApprovalSideEffect> sideEffects = new ArrayList<ApprovalSideEffect>();

	public Json getSr()
	{
		return sr;
	}

	public void setSr(Json sr)
	{
		this.sr = sr;
		approvalState = determineCurrentState();
	}

	public ApprovalState getApprovalState()
	{
		return approvalState;
	}

	public void setApprovalState(ApprovalState approvalState)
	{
		this.approvalState = approvalState;
	}

	public List<ApprovalSideEffect> getSideEffects()
	{
		return sideEffects;
	}

	public void setSideEffects(List<ApprovalSideEffect> sideEffects)
	{
		this.sideEffects = sideEffects;
	}

	public void approve() throws ApprovalException
	{
		if (approvalState.equals(ApprovalState.APPROVAL_PENDING))
		{
			for (ApprovalSideEffect sideEffect : sideEffects)
			{
				sideEffect.execute();
			}

		}
		else
		{
			if (approvalState.equals(ApprovalState.APPROVED))
			{
				throw new ApprovalException("The SR has already been approved.");
			}
			else
			{
				throw new ApprovalException(
						"Cannot approve SR, invalid state transition.");
			}

		}
	}

	private ApprovalState determineCurrentState()
	{

		// TODO finish impl.
		// if SR intake method is IPHONE, ANDROID, OR WEB INTAKE
		// and SR status is O-PENDNG
		// and there is no prior status history
		// then current state is APPROVAL_PENDING.

		//

		// else
		return ApprovalState.APPROVAL_NOT_NEEDED;

	}

}

package org.sharegov.cirm.process;

import static org.sharegov.cirm.rest.OperationService.getPersister;

import java.util.ArrayList;
import java.util.List;

import mjson.Json;

import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.legacy.CirmMessage;
import org.sharegov.cirm.utils.GenUtils;

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
    private BOntology bo;
    private List<CirmMessage> msgsToSend = new ArrayList<CirmMessage>();
    private ArrayList<BOntology> withMetadata = new ArrayList<BOntology>();
    private Json locationInfo = Json.object();
    
    public Json getSr()
	{
		return sr;
	}

	public void setSr(Json sr)
	{
		this.sr = sr;
		cleanSR();
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
//		if (approvalState.equals(ApprovalState.APPROVAL_PENDING))
//		{
			getPersister().getStore().txn(new CirmTransaction<Json> () {
				@Override
				public Json call() throws Exception
				{
					for (ApprovalSideEffect sideEffect : sideEffects)
					{
						sideEffect.execute(ApprovalProcess.this);
					}
					return GenUtils.ok().set("bo", ApprovalProcess.this.getBOntology().toJSON());
				}
				
			});
//		}
//		else
//		{
//			if (approvalState.equals(ApprovalState.APPROVED))
//			{
//				throw new ApprovalException("The SR has already been approved.");
//			}
//			else
//			{
//				throw new ApprovalException(
//						"Cannot approve SR, invalid state transition.");
//			}
//
//		}
	}

	private ApprovalState determineCurrentState()
	{

		// TODO finish impl.
		// if SR intake method is IPHONE, ANDROID, WEB INTAKE, ZAPATA or APPEOCA.
		// and SR status is O-PENDNG
		// and there is no prior status history
		// then current state is APPROVAL_PENDING.
		Json bo = sr.has("bo")? sr.at("bo") : sr;
		String intakeIri = bo.at("properties").at("legacy:hasIntakeMethod").at("iri").asString();
		if(intakeIri.endsWith("IPHONE")
				|| intakeIri.endsWith("ANDROID")
				|| intakeIri.endsWith("WEB")
				|| intakeIri.endsWith("ZAPATA")
				|| intakeIri.endsWith("APPEOCA")
			)
		{
			if(bo.at("properties").at("legacy:hasStatus").at("iri").asString().endsWith("O-PENDNG"))
			{
				return ApprovalState.APPROVAL_PENDING;
			}
			else if(bo.at("properties").at("legacy:hasStatus").at("iri").asString().endsWith("O-OPEN"))
			{
				return ApprovalState.APPROVED;
			}
			else
			{
				return ApprovalState.APPROVAL_NOT_NEEDED;
			}
		}
		else
		{
			return ApprovalState.APPROVAL_NOT_NEEDED;
		}
	}
	
    public BOntology getBOntology() {
        if(bo == null)
        {
            if(sr != null)
            {
                bo = BOntology.makeRuntimeBOntology(sr);
            }
        }
        return bo;
    }
    
	public List<CirmMessage> getMsgsToSend()
	{
		return msgsToSend;
	}

	public void setMsgsToSend(List<CirmMessage> msgsToSend)
	{
		this.msgsToSend = msgsToSend;
	}

	public ArrayList<BOntology> getWithMetadata()
	{
		return withMetadata;
	}

	public void setWithMetadata(ArrayList<BOntology> withMetadata)
	{
		this.withMetadata = withMetadata;
	}

	public Json getLocationInfo()
	{
		return locationInfo;
	}

	public void setLocationInfo(Json locationInfo)
	{
		this.locationInfo = locationInfo;
	}
	
	private void cleanSR()
	{
		Json bo = sr.has("bo")? sr.at("bo"): sr;
		bo.at("properties").atDel("actorEmails");
		bo.at("properties").atDel("hasRemovedAttachment");
	}


}

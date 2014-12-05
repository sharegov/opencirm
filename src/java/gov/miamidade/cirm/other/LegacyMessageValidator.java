package gov.miamidade.cirm.other;

import java.util.Set;

import org.semanticweb.owlapi.model.OWLClass;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.owl.Model;
import org.sharegov.cirm.rest.LegacyEmulator;

import static gov.miamidade.cirm.other.LegacyMessageType.NewCase;
import static gov.miamidade.cirm.other.LegacyMessageType.CaseUpdate;
import static gov.miamidade.cirm.other.LegacyMessageType.NewActivity;

import mjson.Json;

/**
 * A LegacyMessageValidator validates a message coming from an interface against the current configuration and business rules. 
 * e.g. 
 * A) messages for cases created prior to 2009 are invalid, but should not be responded as error to avoid retry loops.
 * B) messages for cases created prior to 2009 with types that are not configured
 * 
 * 
 * Before a message coming from an interface is processed by the Cirm system it should be validated using this class.
 *  
 * @author Thomas Hilpold
 *
 */
public class LegacyMessageValidator
{

	/**
	 * Year, for which case not found situations are reported as errors.
	 * Case not found situations prior to this year, are reported as ok with a tag. 
	 */
	public static final int CASE_NOT_FOUND_CUTOFF_YEAR = 2009;
	public static final String CASE_NOT_FOUND_TAG = "Historic Data:";
	
	public static final String TYPE_NOT_CONFIGURED_PRE_CUTOFF_OK_MESSAGE = "Error: Messages with SR Type ";
	public static final String TYPE_NOT_CONFIGURED_PRE_CUTOFF_OK_MESSAGE_END = " not allowed, because it the type is intentionally not configured in Cirm";
	public static final String TYPE_NOT_CONFIGURED_POST_CUTOFF__ERROR_MESSAGE = "Error: Type  ";
	public static final String TYPE_NOT_CONFIGURED_POST_CUTOFF__ERROR_MESSAGE_END = " not configured. Contact CiRM admin to add this missing type to the Cirm configuration.";

	/**
	 * Validates a new case message received from MDCEAI.
	 * Call within a transaction suggested.
	 * 
	 * @param jmsg
	 * @return
	 */
	public MessageValidationResult validateNewCase(Json jmsg) 
	{
		if (!NewCase.equals(LegacyMessageType.valueOf(jmsg.at("messageType").asString()))) 
		{
			throw new IllegalArgumentException("Not a NewCase message " + jmsg);
		}
		MessageValidationResult result;
		OWLClass srTypeClass = getMessageSRType(jmsg);
		boolean typeValid = checkSRTypeConfigured(srTypeClass);
		//boolean preCutoffYear = isPreCutoffYear(jmsg);
		if (typeValid) 
		{
			result = new MessageValidationResult(true, true, "Valid new case");
		} else 
		{
			//Type not valid
			String validationMsg;
			//Cirm Team should add type to configuration, let interface retry
			validationMsg = TYPE_NOT_CONFIGURED_POST_CUTOFF__ERROR_MESSAGE + srTypeClass.getIRI().getFragment() +
						TYPE_NOT_CONFIGURED_POST_CUTOFF__ERROR_MESSAGE_END;
			result = new MessageValidationResult(typeValid, true, validationMsg);
		}
		return result;
	}	
	
	/**
	 * Validates a new activity message received from MDCEAI.
	 * Call within a transaction necessary.
	 * 
	 * @param jmsg
	 * @return
	 */
	public MessageValidationResult validateNewActivity(Json jmsg) {
		if (!NewActivity.equals(LegacyMessageType.valueOf(jmsg.at("messageType").asString()))) 
		{
			throw new IllegalArgumentException("Not a NewActivity message " + jmsg);
		}
		return validateCaseUpdateOrActivityInternal(jmsg);
	}

	/**
	 * Validates a case update case message received from MDCEAI.
	 * Call within a transaction necessary.
	 * 
	 * @param jmsg
	 * @return
	 */
	public MessageValidationResult validateCaseUpdate(Json jmsg) 
	{
		if (!CaseUpdate.equals(LegacyMessageType.valueOf(jmsg.at("messageType").asString()))) 
		{
			throw new IllegalArgumentException("Not a CaseUpdate message " + jmsg);
		}
		return validateCaseUpdateOrActivityInternal(jmsg);
	}
	
	
	/**
	 * Validates a case update case message received from MDCEAI.
	 * 
	 * @param jmsg
	 * @return
	 */
	protected MessageValidationResult validateCaseUpdateOrActivityInternal(Json jmsg) 
	{
		MessageValidationResult result;
		OWLClass srTypeClass = getMessageSRType(jmsg);
		boolean typeValid = checkSRTypeConfigured(srTypeClass);
		boolean preCutoffYear = isPreCutoffYear(jmsg);
		boolean existingSrFound = false;
		Json existingSR; 
		if (typeValid) 
		{
			Json boidOrCaseNum = jmsg.at("data").at("boid");
			final LegacyEmulator emulator = new LegacyEmulator();
			existingSR = emulator.lookupServiceCase(boidOrCaseNum);
			existingSrFound = existingSR.is("ok", true);
			if (existingSrFound) 
			{
				result = new MessageValidationResult(true, true, "Valid Case Update", existingSR);
			} else 
			{
				if (preCutoffYear) 
				{
					result = new MessageValidationResult(false, false, "Case not found pre cutoff", existingSR);
				} else 
				{
					result = new MessageValidationResult(false, true, "Case not found post cutoff", existingSR);
				}
			}
		} else 
		{
			//Type not valid
			String validationMsg;
			if (preCutoffYear) 
			{
				//Cirm Team will never add type to configuration, no interface retry desired
				validationMsg = TYPE_NOT_CONFIGURED_PRE_CUTOFF_OK_MESSAGE + srTypeClass.getIRI().getFragment() + 
						TYPE_NOT_CONFIGURED_PRE_CUTOFF_OK_MESSAGE_END;
				result = new MessageValidationResult(typeValid, false, validationMsg);
			} else 
			{
				//Cirm Team should add type to configuration, let interface retry
				validationMsg = TYPE_NOT_CONFIGURED_POST_CUTOFF__ERROR_MESSAGE + srTypeClass.getIRI().getFragment() +
						TYPE_NOT_CONFIGURED_POST_CUTOFF__ERROR_MESSAGE_END;
				result = new MessageValidationResult(typeValid, true, validationMsg);
			}
			
		}
		return result;
	}

	/**
	 * Checks if a message is for a case before the cutoff year.
	 * @param jmsg
	 * @return true if jmsg contains a case number string and it's before the cutoff year, false otherwise.
	 */
	protected boolean isPreCutoffYear(Json jmsg) 
	{
		Json boidOrCaseNum = jmsg.at("data").at("boid");
		return ServiceCaseJsonHelper.isCaseNumberString(boidOrCaseNum) 
				&& ServiceCaseJsonHelper.getCaseNumberYear(boidOrCaseNum.asString()) < CASE_NOT_FOUND_CUTOFF_YEAR;
	}
	protected OWLClass getMessageSRType(Json jmsg) 
	{
		String srType = jmsg.at("data").at("type").asString();
		if (!srType.contains("#") || !srType.contains("legacy:")) {
			srType = "legacy:" + srType;
		}
		return OWL.dataFactory().getOWLClass(OWL.fullIri(srType));
	}
	/**
	 * Checks if a given service case type class is configured in the ontololy.
	 * @param srTypeClass
	 * @return true if it's configured
	 */
	protected boolean checkSRTypeConfigured(OWLClass srTypeClass) 
	{
		if (srTypeClass == null) throw new IllegalArgumentException("srTypeClass null parameter not allowed.");
		OWLClass serviceCaseSuperClass = OWL.dataFactory().getOWLClass(Model.legacy("ServiceCase"));
		Set<OWLClass> serviceCaseClasses = OWL.reasoner().getSubClasses(serviceCaseSuperClass, false).getFlattened();
		return serviceCaseClasses.contains(srTypeClass);
	}
	
	/*
	Type in a newCase:
		Json data = Json.object("properties", jmsg.at("data").dup());
		Json props = data.at("properties");
		data.set("type", props.atDel("type")); // properties.type
		Type in Update
		updateTxn(emulator, sr, jmsg.at("data").dup().delAt("boid"));
		::updateTxn(LegacyEmulator emulator, Json existing, Json newdata)
		newdata.atDel("type") //data.type
		
*/
	/**
	 * Immutable validation result.
	 *  
	 * @author Thomas Hilpold
	 *
	 */
	public static class MessageValidationResult {

		private final boolean valid;
		private final boolean allowRetry;
		private final String responseMessage;
		private final Json existingSR; //Not null for valid update/new activity messages
		
		public MessageValidationResult(boolean valid, boolean allowRetry, String responseMessage) {
			this.valid = valid;
			this.allowRetry = allowRetry;
			this.responseMessage = responseMessage;
			this.existingSR = null;
		 }

		public MessageValidationResult(boolean valid, boolean allowRetry, String responseMessage, Json existingSR ) {
			this.valid = valid;
			this.allowRetry = allowRetry;
			this.responseMessage = responseMessage;
			this.existingSR = existingSR;
		 }
		
		/**
		 * Is the message valid in terms of CiRM rules/configuration 
		 * (cutoff date, configured type)
		 * @return
		 */
		public boolean isValid()
		{
			return valid;
		}

		public boolean isAllowRetry()
		{
			return allowRetry;
		}

		/**
		 * Retrieves the exisiting SR in case of update or new activity messages.
		 * @return
		 */
		public Json getExistingSR()
		{
			return existingSR;
		}

		public String getResponseMessage()
		{
			return responseMessage;
		}
	}
}

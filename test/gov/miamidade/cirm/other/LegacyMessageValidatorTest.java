package gov.miamidade.cirm.other;

import static org.junit.Assert.*;
import gov.miamidade.cirm.other.LegacyMessageValidator.MessageValidationResult;

import mjson.Json;

import org.junit.BeforeClass;
import org.junit.Test;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.rest.RestService;
import org.sharegov.cirm.utils.GenUtils;

/**
 * 
 * Tests the message validator. RUN with Prod config set, so existing SRs are found.
 * @author Thomas Hilpold
 *
 */
public class LegacyMessageValidatorTest
{

	static Json WCSUpdateValid; //WCS have no data.type
	static Json WCSNewACValid;
	static Json PWNewCaseValid;
	static Json PWCaseUpdateValid;
	static Json PWNewActivitiyValid; //we must allow retry as age cannot (yet) be determined, because no case number exists.
	static Json PWNewCaseInValidType;
	static Json PWCaseUpdateInvalidType;
	static Json PWNewActivityInValidCaseNotExistsOld;
	static LegacyMessageValidator validator = new LegacyMessageValidator();
	
	@BeforeClass
	public static void setupBeforeClass()
	{
		WCSUpdateValid = Json.read(GenUtils.readAsStringUTF8(LegacyMessageValidatorTest.class.getResource("WCSUpdateValid.json")));
		WCSNewACValid = Json.read(GenUtils.readAsStringUTF8(LegacyMessageValidatorTest.class.getResource("WCSNewACValid.json")));
		PWNewCaseValid = Json.read(GenUtils.readAsStringUTF8(LegacyMessageValidatorTest.class.getResource("PWNewCaseValid.json")));
		PWCaseUpdateValid = Json.read(GenUtils.readAsStringUTF8(LegacyMessageValidatorTest.class.getResource("PWCaseUpdateValid.json")));
		PWNewActivitiyValid = Json.read(GenUtils.readAsStringUTF8(LegacyMessageValidatorTest.class.getResource("PWNewActivityValid.json")));
		PWNewCaseInValidType = Json.read(GenUtils.readAsStringUTF8(LegacyMessageValidatorTest.class.getResource("PWNewCaseInValidType.json")));
		PWCaseUpdateInvalidType = Json.read(GenUtils.readAsStringUTF8(LegacyMessageValidatorTest.class.getResource("PWCaseUpdateInvalidType.json")));
		PWNewActivityInValidCaseNotExistsOld = Json.read(GenUtils.readAsStringUTF8(LegacyMessageValidatorTest.class.getResource("PWNewActivityInValidCaseNotExistsOld.json")));
	}
	

	@Test
	public void testValidateNewCase()
	{		
		MessageValidationResult validResult = validator.validateNewCase(PWNewCaseValid);
		assertTrue(validResult.getExistingSR() == null);
		assertTrue(validResult.isValid());
		assertTrue(validResult.isAllowRetry());
		MessageValidationResult invalidResult = validator.validateNewCase(PWNewCaseInValidType);
		assertTrue(invalidResult.getExistingSR() == null);
		assertFalse(invalidResult.isValid());
		assertTrue(invalidResult.isAllowRetry());
	}

	@Test
	public void testValidateNewActivity()
	{
		RestService.forceClientExempt.set(true);
		//WCS no type
		MessageValidationResult validResult = validator.validateNewActivity(WCSNewACValid);
		assertTrue(validResult.getExistingSR().at("ok").asBoolean());
		assertTrue(validResult.isValid());
		assertTrue(validResult.isAllowRetry());
		//PW
		validResult = validator.validateNewActivity(PWNewActivitiyValid);
		assertTrue(validResult.getExistingSR().at("ok").asBoolean());
		assertTrue(validResult.isValid());
		assertTrue(validResult.isAllowRetry());
		MessageValidationResult invalidResult = validator.validateNewActivity(PWNewActivityInValidCaseNotExistsOld);
		assertFalse(invalidResult.getExistingSR().at("ok").asBoolean());
		assertFalse(invalidResult.isValid());
		assertFalse(invalidResult.isAllowRetry());
	}

	@Test
	public void testValidateCaseUpdate()
	{
		RestService.forceClientExempt.set(true);
		//WCS no type
		MessageValidationResult validResult = validator.validateCaseUpdate(WCSUpdateValid);
		assertTrue(validResult.getExistingSR().at("ok").asBoolean());
		assertTrue(validResult.isValid());
		assertTrue(validResult.isAllowRetry());
		//PW
		validResult = validator.validateCaseUpdate(PWCaseUpdateValid);
		assertTrue(validResult.getExistingSR().at("ok").asBoolean());
		assertTrue(validResult.isValid());
		assertTrue(validResult.isAllowRetry());
		MessageValidationResult invalidResult = validator.validateCaseUpdate(PWCaseUpdateInvalidType);
		assertTrue(invalidResult.getExistingSR() == null);
		assertFalse(invalidResult.isValid());
		assertTrue(invalidResult.isAllowRetry()); // if >= 2009
	}
	

	@Test
	public void testIsPreCutoffYear()
	{
		assertTrue(validator.isPreCutoffYear(PWNewActivityInValidCaseNotExistsOld));
		assertFalse(validator.isPreCutoffYear(PWNewActivitiyValid));
		assertFalse(validator.isPreCutoffYear(PWCaseUpdateValid));
		assertFalse(validator.isPreCutoffYear(PWCaseUpdateInvalidType));		
	}

	@Test
	public void testGetMessageSRType()
	{
		assertTrue(validator.getMessageSRType(PWCaseUpdateInvalidType).getIRI().toString().endsWith("/legacy#PW159"));
		assertTrue(validator.getMessageSRType(PWNewActivitiyValid).getIRI().toString().endsWith("/legacy#PW6102"));
		assertTrue(validator.getMessageSRType(PWNewActivityInValidCaseNotExistsOld).getIRI().toString().endsWith("/legacy#PW6102"));
	}

	@Test
	public void testCheckSRTypeConfigured()
	{
		assertFalse(validator.checkSRTypeConfigured(OWL.dataFactory().getOWLClass(OWL.fullIri("legacy:PW159"))));
		assertTrue(validator.checkSRTypeConfigured(OWL.dataFactory().getOWLClass(OWL.fullIri("legacy:PW131"))));
		assertFalse(validator.checkSRTypeConfigured(OWL.dataFactory().getOWLClass(OWL.fullIri("PW131"))));		
	}

}

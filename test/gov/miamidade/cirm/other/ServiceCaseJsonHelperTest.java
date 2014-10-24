package gov.miamidade.cirm.other;

import static org.junit.Assert.*;

import mjson.Json;

import org.junit.Test;

public class ServiceCaseJsonHelperTest
{

	@Test
	public void testIsCaseNumberString()
	{
		assertTrue(ServiceCaseJsonHelper.isCaseNumberString(Json.make("00-0")));
		assertTrue(ServiceCaseJsonHelper.isCaseNumberString(Json.make("14-0")));
		assertTrue(ServiceCaseJsonHelper.isCaseNumberString(Json.make("14-01234567898454324234")));
		assertTrue(ServiceCaseJsonHelper.isCaseNumberString(Json.make("00-01234567898454324234")));
		assertFalse(ServiceCaseJsonHelper.isCaseNumberString(Json.make(123456)));
		assertFalse(ServiceCaseJsonHelper.isCaseNumberString(Json.make("00-0-0")));
		assertFalse(ServiceCaseJsonHelper.isCaseNumberString(Json.make("00-123456A789")));
		assertFalse(ServiceCaseJsonHelper.isCaseNumberString(Json.make("00-123456A789")));
		assertFalse(ServiceCaseJsonHelper.isCaseNumberString(Json.nil()));
		assertFalse(ServiceCaseJsonHelper.isCaseNumberString(Json.object()));
	}

	@Test
	public void testGetCaseNumberYear()
	{
		assertTrue(2000 == ServiceCaseJsonHelper.getCaseNumberYear("00-0"));
		assertTrue(2099 == ServiceCaseJsonHelper.getCaseNumberYear("99-123142354534"));
		assertTrue(2014 == ServiceCaseJsonHelper.getCaseNumberYear("14-123142354534"));
		assertTrue(2008 == ServiceCaseJsonHelper.getCaseNumberYear("08-4"));
		assertFalse(2008 == ServiceCaseJsonHelper.getCaseNumberYear("09-1"));
		//only cares before first of any string, fails if more than 2 digits found.
		assertTrue(2099 == ServiceCaseJsonHelper.getCaseNumberYear("99-123142354534A"));
		assertTrue(2000 == ServiceCaseJsonHelper.getCaseNumberYear("00-123142354534A"));		
		assertTrue(2000 == ServiceCaseJsonHelper.getCaseNumberYear("00-1231423-54534"));
		assertTrue(-1 == ServiceCaseJsonHelper.getCaseNumberYear("1000-1231423-54534"));
		assertTrue(-1 == ServiceCaseJsonHelper.getCaseNumberYear("000-1231423-54534"));
		assertTrue(-1 == ServiceCaseJsonHelper.getCaseNumberYear("001-1231423-54534"));
	}

}

package org.sharegov.cirm.rest;

import java.util.Date;

import mjson.Json;

import org.junit.Assert;
import org.junit.Test;
import org.sharegov.cirm.OWL;

public class CalendarServiceTest
{

	@Test
	public void testGetFiscalYear()
	{
		Date now = new Date();
		CalendarService calendarService = new CalendarService();
		Json result = calendarService.getFiscalYear(now.getTime());
		System.out.println(result);
		Assert.assertTrue(result.has("fiscalStart"));
		Assert.assertTrue(result.has("fiscalEnd"));
		Assert.assertNotNull(OWL.parseDate(result.at("fiscalStart").asString()));
		Assert.assertNotNull(OWL.parseDate(result.at("fiscalEnd").asString()));
	}

}

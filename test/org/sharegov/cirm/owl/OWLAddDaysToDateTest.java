package org.sharegov.cirm.owl;

import static org.junit.Assert.*;

import java.util.Calendar;
import java.util.Date;

import org.junit.BeforeClass;
import org.junit.Test;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.utils.GenUtils;

public class OWLAddDaysToDateTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		OWL.reasoner();
	}

	@Test
	public void test() {		
		//Error Example from overdue email: 06-02-2016 10:34 AM + 8 workweek = June 03, 2016 3:57
		Calendar start = Calendar.getInstance();
		start.set(2016, 5, 2, 10, 34);
		System.out.print(GenUtils.formatDate(start.getTime()));
		Date result = OWL.addDaysToDate(start.getTime(), 8, true);
		System.out.println(" + 8 = " + GenUtils.formatDate(result));
		Calendar rCal = Calendar.getInstance();
		rCal.setTime(result);
		assertTrue(rCal.get(Calendar.DAY_OF_MONTH) == 14);
		assertTrue(rCal.get(Calendar.MONTH) == 5);
		assertTrue(rCal.get(Calendar.YEAR) == 2016);
		assertTrue(rCal.get(Calendar.HOUR_OF_DAY) == 10);
		assertTrue(rCal.get(Calendar.MINUTE) == 34);
	}

	@Test
	public void test2() {		
		//Error Example Thu 2021/12/30 + 1 = 2022/1/3 (observed holiday New Year 12/31/2021 for 2022)
		Calendar start = Calendar.getInstance();
		start.set(2021, 11, 30, 0, 5); 
		System.out.print(GenUtils.formatDate(start.getTime()));
		Date result = OWL.addDaysToDate(start.getTime(), 1, true);
		System.out.println(" + 1 = " + GenUtils.formatDate(result));
		Calendar rCal = Calendar.getInstance();
		rCal.setTime(result);
		assertTrue(rCal.get(Calendar.DAY_OF_MONTH) == 3);
		assertTrue(rCal.get(Calendar.MONTH) == 0); //Jan
		assertTrue(rCal.get(Calendar.YEAR) == 2022);
		assertTrue(rCal.get(Calendar.HOUR_OF_DAY) == 0);
		assertTrue(rCal.get(Calendar.MINUTE) == 5);
	}
	/*
	 * Mon 01/18/2021 observed? : true
Mon 02/15/2021 observed? : true
Mon 05/31/2021 observed? : true
/*
	 */
}

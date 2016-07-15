package org.sharegov.cirm.utils;

import static org.junit.Assert.*;

import org.junit.Test;

import mjson.Json;

public class SRJsonActivityUtilTest {

	@Test
	public void testGetHasOutcomeLabel() {
		String t1 = SRJsonActivityUtil.getHasOutcomeLabel(getTestActivity1());
		assertTrue(t1.equals("TESTLABEL"));
		String t2 = SRJsonActivityUtil.getHasOutcomeLabel(getTestActivity2());
		assertTrue(t2.equals("TESTFRAG"));
		String t3 = SRJsonActivityUtil.getHasOutcomeLabel(getTestActivity3());
		assertTrue(t3.equals("TESTOUT"));
		try {
			SRJsonActivityUtil.getHasOutcomeLabel(getTestActivity4());
			fail("Exception was expected.");
		} catch (Exception e) {
			//Test passed.
		}
	}
	
	public Json getTestActivity1() {
		return Json.object("hasOutcome", Json.object("label", "TESTLABEL"));
	}
	
	public Json getTestActivity2() {
		return Json.object("hasOutcome", "http://xyz#TESTFRAG");
	}

	public Json getTestActivity3() {
		return Json.object("hasOutcome", "TESTOUT");
	}
	
	public Json getTestActivity4() {
		return Json.object("hasOutcome", Json.array());
	}

}

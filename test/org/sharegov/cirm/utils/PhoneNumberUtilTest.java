/*******************************************************************************
 * Copyright 2015 Miami-Dade County
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.sharegov.cirm.utils;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests PhoneNumberUtil for correct output and stability.
 * 
 * @author Thomas Hilpold
 *
 */
public class PhoneNumberUtilTest {

	/**
	 * Test method for {@link org.sharegov.cirm.utils.PhoneNumberUtil#formatPhoneDataForDisplay(java.lang.String)}.
	 */
	@Test
	public void testFormatPhoneDataForDisplay() {
		String r;
		r = PhoneNumberUtil.formatPhoneDataForDisplay(null);
		assertTrue(r == null);
		r = PhoneNumberUtil.formatPhoneDataForDisplay("");
		assertTrue(r.equals(""));
		r = PhoneNumberUtil.formatPhoneDataForDisplay(" ");
		assertTrue(r.equals(""));
		r = PhoneNumberUtil.formatPhoneDataForDisplay("1");
		assertTrue(r.equals("1"));
		r = PhoneNumberUtil.formatPhoneDataForDisplay(" 3054 #");
		assertTrue(r.equals("3054"));
		r = PhoneNumberUtil.formatPhoneDataForDisplay("(305)333-444");
		assertTrue(r.equals("305333444"));
		r = PhoneNumberUtil.formatPhoneDataForDisplay("(3 05) 33 3- 444 #12");
		assertTrue(r.equals("305333444#12"));
		r = PhoneNumberUtil.formatPhoneDataForDisplay("(3 05) 33 3- 444 #12 ");
		assertTrue(r.equals("305333444#12"));
		r = PhoneNumberUtil.formatPhoneDataForDisplay("(3 05) 33 3- 444 #12,(305) 33 4- 445 #12 ");
		assertTrue(r.equals("305333444#12, 305334445#12"));
		r = PhoneNumberUtil.formatPhoneDataForDisplay("(3 05) 33 3- 444 #12,,(305)");
		assertTrue(r.equals("305333444#12, , 305"));
		//Good conversions
		r = PhoneNumberUtil.formatPhoneDataForDisplay("3051234321");
		assertTrue(r.equals("305-123-4321"));
		r = PhoneNumberUtil.formatPhoneDataForDisplay("3051234321#1, 3051234321#2");
		assertTrue(r.equals("305-123-4321#1, 305-123-4321#2"));
		r = PhoneNumberUtil.formatPhoneDataForDisplay("3051234321,3051234322");
		assertTrue(r.equals("305-123-4321, 305-123-4322"));
		r = PhoneNumberUtil.formatPhoneDataForDisplay("305123432,305123431");
		assertTrue(r.equals("305123432, 305123431"));
		r = PhoneNumberUtil.formatPhoneDataForDisplay("305-123-4321#1, 305-123-4321#2, 3051234321, 3051234321#4");
		assertTrue(r.equals("305-123-4321#1, 305-123-4321#2, 305-123-4321, 305-123-4321#4"));
	}

	/**
	 * Test method for {@link org.sharegov.cirm.utils.PhoneNumberUtil#normalizeOnePhoneNumber(java.lang.String)}.
	 */
	@Test
	public void testNormalizeOnePhoneNumber() {
		String r;
		r = PhoneNumberUtil.normalizeOnePhoneNumber(null);
		assertTrue(r == null);
		r = PhoneNumberUtil.normalizeOnePhoneNumber("");
		assertTrue(r.equals(""));
		r = PhoneNumberUtil.normalizeOnePhoneNumber(" ");
		assertTrue(r.equals(""));
		r = PhoneNumberUtil.normalizeOnePhoneNumber("(-123-)   123---1234 #1 ");
		assertTrue(r.equals("1231231234#1"));
		r = PhoneNumberUtil.normalizeOnePhoneNumber("123-123-1234 # # 1#");
		assertTrue(r.equals("1231231234"));
		r = PhoneNumberUtil.normalizeOnePhoneNumber("+123.123.1234.1#2/1");
		assertTrue(r.equals("2312312341#21"));
		r = PhoneNumberUtil.normalizeOnePhoneNumber(" +123, 123 ,1234 ,#2,H,4");
		assertTrue(r.equals("1231231234#24"));
		r = PhoneNumberUtil.normalizeOnePhoneNumber("1-2-3/123/1 2 3 4");
		assertTrue(r.equals("1231231234"));
		r = PhoneNumberUtil.normalizeOnePhoneNumber(" 123 123 1234 #1#23");
		assertTrue(r.equals("1231231234#1"));
		r = PhoneNumberUtil.normalizeOnePhoneNumber("123.123.1234 ");
		assertTrue(r.equals("1231231234"));
		r = PhoneNumberUtil.normalizeOnePhoneNumber("1 ");
		assertTrue(r.equals("1"));
		r = PhoneNumberUtil.normalizeOnePhoneNumber(" 1/2#23");
		assertTrue(r.equals("12#23"));
		r = PhoneNumberUtil.normalizeOnePhoneNumber(" 123.123.123.123");
		assertTrue(r.equals("23123123123"));
		r = PhoneNumberUtil.normalizeOnePhoneNumber(" 1 2 3 4 5 6 7 8 9 0 - 1 2 ");
		assertTrue(r.equals("23456789012"));
		r = PhoneNumberUtil.normalizeOnePhoneNumber(" #");
		assertTrue(r.equals(""));
		r = PhoneNumberUtil.normalizeOnePhoneNumber(" #1-#2");
		assertTrue(r.equals("#1"));
		r = PhoneNumberUtil.normalizeOnePhoneNumber(" #921)#2");
		assertTrue(r.equals("#921"));
		
	}
}

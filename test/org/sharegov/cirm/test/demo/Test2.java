package org.sharegov.cirm.test.demo;

import org.junit.AfterClass;
import org.junit.Test;
import org.sharegov.cirm.test.OpenCirmTestBase;

/**
 * Starting this example test case directly will start openCirm.
 * <p>
 * If this test case is part of a higher level suite that inherits from OpenCirmTestBase also, 
 * it will use the already running OpenCirm instance.
 * 
 * @author Thomas Hilpold
 *
 */
public class Test2 extends OpenCirmTestBase
{

	@AfterClass
	public static void tearDownAfterClass() throws Exception
	{
		System.out.println("Test 2 class completed.");
	}

	@Test
	public void test()
	{
		System.out.println("Test2 class - testing nothing...completed.");
	}

}

package org.sharegov.cirm.test.demo;

import org.junit.AfterClass;
import org.junit.Assert;
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
public class Test1 extends OpenCirmTestBase
{

	/**
	 * Use a method like this to clean up openCirm to a state before any test of this class was executed,
	 * so subsequent tests can run on a cleaned up opencirm instance.<br>
	 * If no cleanup is needed, this method is optional.
	 * 
	 * @throws Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception
	{
		System.out.println("Test 1 class completed.");
	}

	/**
	 * A trivial example test.
	 */
	@Test
	public void test()
	{
		Assert.assertTrue(true);
		Assert.assertEquals(1, 1);
		System.out.println("Test1 class - testing nothing...completed.");
	}

}

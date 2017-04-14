package org.sharegov.cirm.test.demo;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.sharegov.cirm.test.OpenCirmTestBase;

/**
 * Example Master junit 4 test suite for regression tests within a single run of opencirm. 
 * <p>
 * All component test suites should be directly added to a similar class.
 *  
 * This will start OpenCirm once and execute all dependent suites and tests, so each sub test or test suite does not need to start it's own instance.
 *  
 * @author Thomas Hilpold
 */
@RunWith(Suite.class)
@SuiteClasses({ TestSuite1.class })
public class TestMasterSuite extends OpenCirmTestBase
{

	@BeforeClass
	public static void setUpClass() throws Exception { 
		System.out.println("Master test suite starting....");
	}
	
	@AfterClass
	public static void tearDownMaster() {
		System.out.println("Master test suite completed.");
	}

}

package org.sharegov.cirm.test.demo;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.sharegov.cirm.test.OpenCirmTestBase;

/**
 * Starting this example test suite will start openCirm once and execute all tests.
 * <p>
 * If this example test suite is used in a higher level master suite, it will use the already running openCirm instance.
 * 
 * @author Thomas Hilpold
 *
 */
@RunWith(Suite.class)
@SuiteClasses({ Test1.class, Test2.class })
public class TestSuite1 extends OpenCirmTestBase
{

}

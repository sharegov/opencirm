package org.sharegov.cirmadmin;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.sharegov.cirm.test.OpenCirmTestBase;

/**
 * CirmAdmin test suite to include all CirmAdmin test cases.
 * 
 * <p>
 * All CIRM Admin test classes should be included in this test suit
 * </p>
 * @author Thomas Hilpold, David Wong, Camilo Chirino
 *
 */
@RunWith(Suite.class)
@SuiteClasses({ 
	org.sharegov.cirm.legacy.ServiceCaseManagerTest.class
})
public class CirmAdminTestSuite extends OpenCirmTestBase
{

}

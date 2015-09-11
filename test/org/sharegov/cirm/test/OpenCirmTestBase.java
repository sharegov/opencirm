package org.sharegov.cirm.test;


import org.junit.BeforeClass;
import org.sharegov.cirm.StartUp;

/**
 * Abstract OpenCirm test base class, from which all opencirm test suites and all opencirm tests should inherit.
 * This ensures, that independently of which class starts a junit test or suite, opencirm is only started once and the server
 * remains running throughout the test, testsuite or master suite of suites.
 * 
 * <br>
 * Usage:
 * @see package org.sharegov.cirm.test.demo 
 * 
 * @author Thomas Hilpold
 *
 */
public abstract class OpenCirmTestBase
{
	@BeforeClass
	public static void setUpClass() throws Exception {
		if (!StartUp.isServerStarted()) {
			System.out.println("Starting openCirm...");
			StartUp.main(new String[]{});
			if (!StartUp.isServerStarted()) {
				throw new IllegalStateException("Server did not start up");
			} else {
				System.out.println("Starting openCirm...COMPLETED.");
			}
		}
	}		
}

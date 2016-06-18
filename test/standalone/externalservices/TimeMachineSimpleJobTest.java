package standalone.externalservices;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.sharegov.cirm.OWL;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

/**
 * Time Machine Test that inserts a high count of simple tasks concurrently and while the test time machine is executing.
 * Each concurrent task will use an exclusive partition of the test data and run for a defined total duration.<br>
 * <br>
 * WARNING: The time machine url will be loaded from the current configuration (Normally Startup.java). <br>
 * Always use TEST or DEV config.<br>
 * <br>
 * Details:<br>
 * This test executes 2000 "sendCase" simple job time machine insert operations over a duration of 30 minutes simulating 5 servers 
 * using the time machine. Each "sendCase" job is configured to be triggered in 10 minutes.<br>
 * <ol>
 *   <li>In minute 0-10, only inserts and no simple job executions take place.
 *   <li>For the next 20 minutes inserts and simple job executions happen concurrently.
 *   <li>In minute 30 the inserts should be completed by all 5 threads 
 *   <lt>and for the next 10 minutes, simple jobs should still be executed by the TM.
 * </ol>
 * <br>
 * <br>
 * Usage:
 * <ul>
 * 	<li> A) Ensure that Open/Mdcirm is configured for test (Startup.java or config files)
 * 	<li> B) Check or replace TimeMachineTestDataBulkytra.txt, so that all boids are BULKYTRA SRs from tcirm.
 * 	<li> C) Clear all test logs on all involved machines
 * 	<li> D) Run this class as java program
 * 	<li> E) Monitor output. 5 threads each operating on a partitioned 1/5th of the test data will execute 400 inserts each.
 * </ul>
 * 	(From the test's perspective, you may use other interface SRs, but you'd need to review all code executing after the time machine calls mdcirm back)
 * 
 * @author Thomas Hilpold
 *
 */
public class TimeMachineSimpleJobTest {
	
	/**
	 * Contains Bulkytra BOIDs from tcirm.
	 */
	public final static String TEST_DATA_FILE_NAME = "TimeMachineTestDataBulkytra.txt";
	
	/**
	 * Main method
	 * @param args ignored
	 */
	public static void main(String[] args) {
		TimeMachineSimpleJobTest t = new TimeMachineSimpleJobTest();
		t.executeTest();
	}
	
	/**
	 * Creates 5 tasks and uses 5 threads to execute them concurrently over a period of 30 mins. 
	 */
	void executeTest() {	
		if (!StartUp.config.at("ontologyConfigSet").asString().contains("#TestConfigSet")) {
			throw new RuntimeException("THIS TEST MUST ONLY BE RUN AGAINST THE TEST TIME MACHINE. CHECK YOUR CONFIGURATION.");
		}
		OWL.reasoner();
		ThreadLocalStopwatch.startTop("START Time Machine Test Execution 5 threads, 400 cases, 30 minutes target duration");
		TestData testData = getTestData();
		ExecutorService s = Executors.newFixedThreadPool(5);
		TimeMachineSimpleJobTestTask[] tasks = new TimeMachineSimpleJobTestTask[] {
				new TimeMachineSimpleJobTestTask(400, 1800, testData),
				new TimeMachineSimpleJobTestTask(400, 1800, testData),
				new TimeMachineSimpleJobTestTask(400, 1800, testData),
				new TimeMachineSimpleJobTestTask(400, 1800, testData),
				new TimeMachineSimpleJobTestTask(400, 1800, testData)
		};
		for (int i = 0; i < tasks.length; i++) {
			s.submit(tasks[i]);
		}
		boolean allExecuted = false;
		try {
			s.shutdown();
			allExecuted = s.awaitTermination(1, TimeUnit.HOURS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}		
		if (allExecuted) {
			ThreadLocalStopwatch.stop("STOP Time Machine Test Execution");
		} else {
			ThreadLocalStopwatch.fail("FAILTime Machine Test Execution");
		}
	}
	
	/**
	 * Gets the test data.
	 * @return a TestData object
	 */
	TestData getTestData() {
		TestData td = new TestData();
		try {
			td.setBulkyBoids(readBulkytraBoidFile());
		} catch (Exception e) {
			throw new RuntimeException("Test Data preparation failed", e);
		}
		ThreadLocalStopwatch.now("READ Test data completed. Bulkytra boid count:  " + td.getBulkyBoids().size());
		return td;
	}
	
	/**
	 * Reads a file that only contains one boid per line into a List of long values.
	 * 
	 * @return
	 * @throws Exception
	 */
	List<Long> readBulkytraBoidFile() throws Exception {
		InputStream in = this.getClass().getResourceAsStream(TEST_DATA_FILE_NAME);
		BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
		List<Long> result = new ArrayList<>(1000);
		while (r.ready()) {
			result.add(Long.parseLong(r.readLine()));
		}
		return result;
	}

	/**
	 * Simple extensible test data class.
	 * 
	 * @author Thomas Hilpold
	 *
	 */
	static class TestData {
		private List<Long> bulkyBoids;

		public List<Long> getBulkyBoids() {
			return bulkyBoids;
		}

		public void setBulkyBoids(List<Long> bulkyBoids) {
			this.bulkyBoids = bulkyBoids;
		}
		
	}
 }

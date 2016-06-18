package standalone.externalservices;

import java.util.concurrent.atomic.AtomicInteger;

import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import gov.miamidade.cirm.other.DepartmentIntegration;
import mjson.Json;
import standalone.externalservices.TimeMachineSimpleJobTest.TestData;

/**
 * Worker task that uses mdcirm/opencirm code to insert sendcase simple jobs based on test data into the time machine.<br>
 * Each task will insert nrOfExecutions jobs over a period of targetDurationSecs.<br>
 * Tasks are aware of the total number of task objects, if all tasks are created before they are started. <br>
 * and each one will only operate on an exclusive portion of the test data. see (testData.size()/createdTasks)<br> 
 * 
 * @author Thomas Hilpold
 */
public class TimeMachineSimpleJobTestTask implements Runnable {

	private static volatile AtomicInteger createdTasks = new AtomicInteger(0);
	private int taskIndex = 0;
	private int targetDurationSecs;
	private int nrOfExecutions;
	private TestData testData;
	private int taskTestDataSize;
	private int taskTestDataStartIndex;
	
	/**
	 * Creates a test task that executes a defined nr of tests over a defined duration using all or a portion of the testData.
	 *  
	 * @param nrOfExecutions the number of tests requested
	 * @param targetDurationSecs the duration, executing all tests should roughly take
	 * @param testData testData for the test.
	 */
	public TimeMachineSimpleJobTestTask(int nrOfExecutions, int targetDurationSecs, TestData testData) {
		taskIndex = createdTasks.getAndIncrement();
		ThreadLocalStopwatch.startTop("TimeMachineSimpleJobTestTask " + taskIndex + " created.");
		this.nrOfExecutions = nrOfExecutions;
		this.targetDurationSecs = targetDurationSecs;
		this.testData = testData;
	}
	
	@Override
	public void run() {
		init();
		ThreadLocalStopwatch.startTop("START TimeMachineSimpleJobTestTask " + taskIndex + " TestDataStart: " + taskTestDataStartIndex);		
		executeAllTests();
		ThreadLocalStopwatch.startTop("STOP TimeMachineSimpleJobTestTask " + taskIndex);		
	}
	
	/**
	 * Initializes taskTestDataSize and taskTestDataStartIndex after all tasks were created, but before execution.
	 */
	void init() {
		taskTestDataSize = testData.getBulkyBoids().size() / (createdTasks.get());
		taskTestDataStartIndex = taskIndex * taskTestDataSize;
	}
	
	/**
	 * Executes all tests, pauses randomly after each test to take targetDurationSecs in total.
	 */
	void executeAllTests() {
		long targetExecutionMs = targetDurationSecs * 1000L / (long)nrOfExecutions;
		for (int i = 0; i < nrOfExecutions; i++) {
			long startTime = System.currentTimeMillis();
			executeTest(i);
			long actualDurationMs = System.currentTimeMillis() - startTime;
			if (actualDurationMs < targetExecutionMs) {
				try {
					long sleepMs = targetExecutionMs - actualDurationMs;
					long randomSleep = (long)(Math.random() * sleepMs * 2); //expecting 0.5 as avg random -> *2
					Thread.sleep(randomSleep);
				} catch (InterruptedException e) {
				}
			}
		}
	}
	
	/**
	 * Executes a test.
	 * @param executionNr current execution nr
	 */
	void executeTest(int executionNr) {
		int testDataIdx = getTestDataIndex(executionNr);
		ThreadLocalStopwatch.now("Worker: " + taskIndex + " Executing " + executionNr + " TestDataidx: " + testDataIdx);	
		executeTest_sendCase(testDataIdx);
	}
	
	/**
	 * Gets the index into the test data for the given executionNr.
	 * (init() must have been called before using this method)
	 * @param executionNr >=0
	 * @return index into testData list.
	 */
	int getTestDataIndex(int executionNr) {		
		return taskTestDataStartIndex + (executionNr % taskTestDataSize);		
	}
	
	/**
	 * Uses DepartmentIntegration to insert a sendcase simple job into the time machine.
	 *  
	 * @param testDataIndex
	 */
	void executeTest_sendCase(int testDataIndex) {
		Json serviceCase = Json.object();
		serviceCase.set("hasLegacyInterface", Json.object("hasLegacyCode", "NOTCOM"));
		serviceCase.set("type", "BULKYTRA");
		long boid = testData.getBulkyBoids().get(testDataIndex);
		serviceCase.set("boid", boid); //From Basic Search cirm
		Json locationInfo = null;
		DepartmentIntegration d = new DepartmentIntegration();
		Json result = d.delaySendToDepartment(serviceCase, locationInfo, 10);
		if (result.is("ok", false)) {
			ThreadLocalStopwatch.fail("Failed submitting case to time machine");
		}
	}
}

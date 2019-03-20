package org.sharegov.cirm.utils;

import java.util.Calendar;
import java.util.UUID;

import org.sharegov.cirm.CirmTransactionEvent;
import org.sharegov.cirm.CirmTransactionListener;

import mjson.Json;

/**
 * Inserts a new task into the time machine after a cirm transaction succeeded.<br>
 * Using this class guarantees that despite possible transaction retries only a single time machine task is inserted.<br>
 * <br>
 * Sending a task to the time machine with the same name and group as an existing task fails at the time machine with an ObjectAlreadyExistsException.<br>
 * Therefore it is essential to ensure only one task is inserted for all retries exactly after the transaction succeeds.<br>
 * The critical usecase is overdue activites, where new ids for ServiceActivities are create on each retry and only after tx success the final 
 * ids for storage are guaranteed to be persisted and can be used for tm task insertion.<br>
 * <br>
 * An additional benefit of this class is that time machine calls are avoided during retries, avoiding additional time machine load.<br> 
 * <br>
 * [Cirm Fatal] error emails are being sent on any exception, as this might indicate a time machine down situation.<br>
 * 
 * @author Thomas Hilpold
 *
 */
public class NewTimeTaskOnTxSuccessListener implements CirmTransactionListener {

	boolean calendarMode;
	private UUID cirmTransactionUUID;
	private String taskId;
	private Calendar cal; //calendar mode true
	private int minutesFromNow; //non calendar false
	private String url;
	private Json post;	

	public NewTimeTaskOnTxSuccessListener(UUID cirmTransactionUUID, String taskIdMod, Calendar cal, String url,
			Json post) {
		calendarMode = true;
		this.cirmTransactionUUID = cirmTransactionUUID;
		this.taskId = taskIdMod;
		this.cal = cal;
		this.minutesFromNow = -1; // cal mode true
		this.url = url;
		this.post = post;
	}

	public NewTimeTaskOnTxSuccessListener(UUID cirmTransactionUUID, String taskId, int minutesFromNow, String url,
			Json post) {
		calendarMode = false;
		this.cirmTransactionUUID = cirmTransactionUUID;
		this.taskId = taskId;
		this.cal = null; //cal mode false
		this.minutesFromNow = minutesFromNow;
		this.url = url;
		this.post = post;		
	}

	@Override
	public void transactionStateChanged(CirmTransactionEvent e) {
		if(e.isSucceeded()) {
			Json result = null;
			try {
    			if(calendarMode) {
    				result = GenUtils.timeTaskCalDirect(cirmTransactionUUID, taskId, cal, url, post);
    			} else {
    				result = GenUtils.timeTaskDirect(cirmTransactionUUID, taskId, minutesFromNow, url, post);
    			}
    			if (result.is("ok", false)) {
    				reportTimeMachineBadResult(result);
    			}
			} catch(Exception ex) {
				reportTimeMachineException(ex);
			}
		} else if (e.isFailed()) {
			//do nothing			
		}		
	}

	private void reportTimeMachineBadResult(Json result) {
		ThreadLocalStopwatch.error("NewTimeTaskOnTxSuccessListener FAILED to insert task " + taskId + " (Bad Result)");
		ThreadLocalStopwatch.error("Details: calendarMode: " + calendarMode);
		ThreadLocalStopwatch.error("Details: Url: " + url);
		ThreadLocalStopwatch.error("Details: Post: " + post);								
		ThreadLocalStopwatch.error("Details: Result: " + result);										
		String msg = "Details: calendarMode: " + calendarMode + "<br>"
				+ "Details: Url: " + url  + "<br>"
				+ "Post: " + post + "<br>"
				+ "Result: " + result  + "<br>";
 		GenUtils.reportFatal("NewTimeTaskOnTxSuccessListener bad result inserting " + taskId, msg, null);
	}

	private void reportTimeMachineException(Exception e) {
		ThreadLocalStopwatch.error("NewTimeTaskOnTxSuccessListener FAILED to insert task " + taskId + " (Exception)");
		ThreadLocalStopwatch.error("Details: calendarMode: " + calendarMode);
		ThreadLocalStopwatch.error("Details: Url: " + url);
		ThreadLocalStopwatch.error("Details: Post: " + post);								
		ThreadLocalStopwatch.error("Details: Result: Exception before result: " + e);
		e.printStackTrace();

		String msg = "Details: calendarMode: " + calendarMode + "<br>"
				+ "Details: Url: " + url  + "<br>"
				+ "Post: " + post + "<br>"
				+ "Result: Exception before result: " + e + "<br>";
 		GenUtils.reportFatal("NewTimeTaskOnTxSuccessListener error inserting " + taskId, msg, e);		
	}

}

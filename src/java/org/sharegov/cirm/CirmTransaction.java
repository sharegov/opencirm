/*******************************************************************************
 * Copyright 2014 Miami-Dade County
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
package org.sharegov.cirm;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.sharegov.cirm.rdb.CirmTransactionException;
import org.sharegov.cirm.rdb.RetryDetectedException;

/**
 * Thread safe thread local Transaction class.<br>
 * <br>
 * Initial state: NOTEXECUTED<br>
 * State transitions:<br>
 * NOTEXECUTED --> EXECUTING<br>
 * EXECUTING --> EXECUTING_REQUESTING_RETRY | SUCCEEDED | FAILED<br>
 * EXECUTING_REQUESTING_RETRY --> EXECUTING | SUCCEEDED | FAILED<br>
 * <br> 
 * @author Thomas Hilpold
 * @param <V>
 *
 */
public abstract class CirmTransaction<V> implements Callable<V>
{
	public enum STATE {
		NOTEXECUTED, EXECUTING, EXECUTING_REQUESTING_RETRY, SUCCEEDED, FAILED
	}
	
	/**
	 * Threadlocal association of transaction with executing thread during begin() and end().
	 * Only the toplevel transaction will be associated with each thread.
	 */
	private static final ThreadLocal<CirmTransaction<?>> transactions = new ThreadLocal<CirmTransaction<?>>();
	
	private static final AtomicLong nrOfTotalTransactions = new AtomicLong(0);
	private static final AtomicLong nrOfFailedTransactions = new AtomicLong(0);

	/**
	 * Map from ThreadID to CirmTransaction of currently executing (begin to end) top level transactions
	 */
	private static final ConcurrentHashMap<Long, CirmTransaction<?>> executingTransactions = new ConcurrentHashMap<Long, CirmTransaction<?>>();
	
	private volatile STATE transactionState = STATE.NOTEXECUTED;

	private final AtomicInteger executionCount = new AtomicInteger(0);
	private final AtomicLong beginTimeMs = new AtomicLong(0); 
	private final AtomicLong endTimeMs = new AtomicLong(0); 
	private final AtomicLong executeStartTimeMs = new AtomicLong(0); // set at each retry
	private final AtomicLong executeEndTimeMs = new AtomicLong(0); // set at each retry or first execution finish
	
	private final CirmTransactionEventSupport transactionEventSupport = new CirmTransactionEventSupport();
	/**
	 * only during execution, single thread access, lazy initialized
	 */
	private UUID transactionUUID = null; 

	private volatile boolean isAllowDBLock = false;
		
	//
	// Transaction Stats and Management
	//
	public static long getNrOfTotalTransactions()
	{
		return nrOfTotalTransactions.get();
	}

	public static long getNrOfFailedTransactions()
	{
		return nrOfFailedTransactions.get();
	}

	public static int getNrOfExecutingTransactions()
	{
		return executingTransactions.size();
	}

	/**
	 * Gets all currently executing transactions by their threads' ids.
	 * Do not assume these threads to exist or that transactions are still executing,
	 * when you iterate over the map, as some might be finishing while you are iterating.
	 * 
	 * @return a copied map of threadIds to currently executing transactions
	 */
	public static synchronized Map<Long, CirmTransaction<?>> getExecutingTransactions() 
	{
		Map<Long, CirmTransaction<?>> m = new HashMap<Long, CirmTransaction<?>>(executingTransactions);
		return m;
	}

	//
	// Transaction retrieval for executing thread
	//
	
	/**
	 * To be called by the executing thread (between begin and end) to retrieve the transaction bound to it.
	 * 
	 * @return the transaction bound to the current thread
	 */
	public static CirmTransaction<?> get() 
	{
		return transactions.get();
	}

	/**
	 * 
	 * @return
	 */
	public static boolean isExecutingOnThisThread() 
	{
		return transactions.get() != null;
	}

	/**
	 * A random UUID associated with the toplevel transaction valid during execution to be accessed by the executing thread only.
	 * UUID will remain the same across retries.
	 * Not thread safe!
	 * @return
	 */
	public static UUID getTopLevelTransactionUUID() 
	{
		CirmTransaction<?> topLevel = get();
		if (topLevel == null) throw new IllegalStateException("Toplevel transaction is not executing. You must call this method during execution.");
		return topLevel.getTransactionUUID();
	}
	
	private UUID getTransactionUUID() 
	{
		if (transactionUUID == null)
			transactionUUID = UUID.randomUUID();
		return transactionUUID;
	}

	private void clearTransactionUUID() 
	{
		transactionUUID = null;
	}

	//
	// Transaction control
	//

	/**
	 * Begin must be called by the transaction controller in the executing thread before the (first) execute call.
	 * It must not be called before retrying execute.
	 * Binds the transaction to the thread during the execution
	 */
	public final void begin()
	{
		if (isExecutingOnThisThread()) throw new IllegalStateException("A Transaction is already executed by this Thread " + Thread.currentThread().getName()); 
		transactions.set(this);
		nrOfTotalTransactions.incrementAndGet();
		executingTransactions.putIfAbsent(Thread.currentThread().getId(), this);
		beginTimeMs.set(System.currentTimeMillis());
		transactionState = STATE.EXECUTING;
	}

	/**
	 * Executes the transaction code in overwritten call method.
	 * Statistical information is also updated.
	 * This method will be called repeatedly after one begin and before one end call, if repeatable exceptions occur during it's run.
	 * Usage: Call this method instead of overwritten call().
	 * Thread safe.
	 * 
	 * @return the result of call()
	 * @throws Exception any exception call() throws
	 */
	public final V execute() throws Exception 
	{
		if (transactions.get() != this) throw new IllegalStateException("Cannot execute transaction not bound to current thread: " + Thread.currentThread().getName());
		clearTopLevelEventListeners();
		//Reset Retry!
		transactionState = STATE.EXECUTING;
		executeStartTimeMs.set(System.currentTimeMillis());
		executionCount.incrementAndGet();
		V result;
		try 
		{
			result = call();
			checkRetryRequested();
		}
		finally 
		{
			executeEndTimeMs.set(System.currentTimeMillis());
		}
		return result;
	}

	/**
	 * Set the retryFlag during execution to indicate that a toplevel transaction retry should be attempted immediately and 
	 * all subtransaction execution ceased.
	 * This flag will be cleared automatically before each retry execution.
	 *  
	 * @param retryFlag
	 * @throws CirmTransactionException 
	 */
	public void requestRetry()
	{
		if (transactionState == STATE.EXECUTING_REQUESTING_RETRY) return;
		if (transactionState == STATE.NOTEXECUTED) throw new IllegalStateException("Retry requested before Transaction execution began."); 
		if (transactionState == STATE.FAILED) throw new IllegalStateException("Retry requested after Transaction ended with failure.");
		if (transactionState == STATE.SUCCEEDED) throw new IllegalStateException("Retry requested after Transaction ended successfully.");
		transactionState = STATE.EXECUTING_REQUESTING_RETRY;
	}

	/**
	 * Checks if retry flag is set and interrupts execution by throwing a RetryDetectedException.
	 * 
	 * @throws RetryDetectedException if retry flag is set.
	 */
	public void checkRetryRequested() throws RetryDetectedException
	{
		if (transactionState == STATE.EXECUTING_REQUESTING_RETRY) throw new RetryDetectedException("Retry flag is set. Cease execution and retry toplevel transaction instantly.");
	}

	/**
	 * True between calling trans begin and end.
	 * @return 
	 */
	public boolean isExecuting()
	{
		return transactionState ==  STATE.EXECUTING || transactionState ==  STATE.EXECUTING_REQUESTING_RETRY ;
	}
	
	/**
	 * True if the transaction is executing and requesting a retry due to a retryable exception.
	 * True indicates that all sublevel transaction execution shall cease 
	 * and a toplevel retry should be started immediately.
	 * 
	 * @return
	 */
	public boolean isRequestingRetry()
	{
		return transactionState == STATE.EXECUTING_REQUESTING_RETRY;
	}

	/**
	 * End transaction sucessfully.
	 */
	public final void end()
	{
		end(true);
	}

	/**
	 * End must be called by the transaction controller in the executing thread after the transaction failed or succeeded after execution (including retries if any).
	 * It disassociates the transaction from the current thread.
	 * @param succeeded false if transaction failed (timeout, non retryable transaction, retry maximum reached)
	 * @throws CirmTransactionException
	 */
	public final void end(boolean succeeded)
	{
		if (transactionState == STATE.SUCCEEDED || transactionState == STATE.FAILED) throw new IllegalStateException("Transaction has completed. Duplicate end call."); 
		if (transactionState == STATE.NOTEXECUTED) throw new IllegalStateException("Transaction execution has not yet begun. Cannot end. Call begin first."); 
		if (transactionState == STATE.EXECUTING_REQUESTING_RETRY) System.err.println("Transaction requesting to be retried, but was ended."); 
		if (!succeeded) nrOfFailedTransactions.incrementAndGet();
		transactionState = succeeded ? STATE.SUCCEEDED: STATE.FAILED;
		executingTransactions.remove(Thread.currentThread().getId());
		endTimeMs.set(System.currentTimeMillis());
		clearTransactionUUID();
		transactions.remove();		
	}


	public STATE getState()
	{
		return transactionState;
	}

	public int getExecutionCount() 
	{
		return executionCount.get();
	}


	/**
	 * The transaction has ended execution including all retries.
	 * 
	 * @return
	 */
	public boolean isEnded()
	{
		return transactionState == STATE.SUCCEEDED || transactionState == STATE.FAILED;
	}

	/**
	 * The transaction has failed including all retries.
	 * 
	 * @return
	 */
	public boolean isFailed()
	{
		return transactionState == STATE.FAILED;
	}
	
	/**
	 * The transaction has failed including all retries.
	 * 
	 * @return
	 */
	public boolean isSucceeded()
	{
		return transactionState == STATE.SUCCEEDED;
	}

	//
	// DB locking
	//

	/**
	 * If the transaction controller sets AllowDBLock earlier, a transaction may acquire DB locks to prevent it
	 * from being retried again. Only the store should use this method to acquire locks.
	 * @return
	 */
	public boolean isAllowDBLock()
	{
		return isAllowDBLock;
	}

	/**
	 * This method shall only be called by the Transaction Controller.
	 * Thread safe.
	 * @param isAllowDBLock
	 */
	public void setAllowDBLock(boolean isAllowDBLock)
	{
		this.isAllowDBLock = isAllowDBLock;
	}

	//
	// Transaction timing
	//

	/**
	 * Begin time of the transaction. Equal across all retries.
	 * 
	 * Valid only in the toplevel transaction after txn controller has begun transaction, during execution and after finish.
	 * Zero will be returned before beginning of transaction.
	 * Thread safe.
	 * @return a long value as defined by System.currentTimeMillis() at the time the transaction began.
	 */
	public long getBeginTimeMs() 
	{
		return beginTimeMs.get();
	}
	
	/**
	 * Returns the duration between begin call and end call for completed (succeeded or failed) transactions 
	 * or (begin call and currrent time, if transaction is still executing) 
	 * or -1 if begin has not been called.
	 * 
	 * @return
	 */
	public double getTotalExecutionTimeSecs()
	{
		if (beginTimeMs.get() == 0) return -1;
		long beginTime = beginTimeMs.get();
		long curOrCompletedTime; 
		if (transactionState == STATE.SUCCEEDED 
				|| transactionState == STATE.FAILED) 
			curOrCompletedTime = endTimeMs.get();
		else 
			curOrCompletedTime = System.currentTimeMillis();
		long durationMs = curOrCompletedTime - beginTime;
		return (durationMs / 1000.0d);
	}

	/**
	 * Gets the duration of the last execute call. 
	 * Only provides meaningful time after execute and before end or a retry of execute.
	 * To be used by transaction controller only.
	 * @return duration if execution completed or -1 if executing or never executed.
	 */
	public double getExecutionTimeSecs()
	{
		double durationSecs = (executeEndTimeMs.get() - executeStartTimeMs.get()) / 1000.0d;
		return durationSecs > 0? durationSecs : - 1;
	}

	/**
	 * Get's the duration from the most recent execute call until now.
	 * To determine stale transactions.
	 * @return -1 if not currently executing
	 */
	public double getCurrentExecutionTimeSecs()
	{
		if (transactionState == STATE.SUCCEEDED
				|| transactionState == STATE.FAILED
				|| transactionState == STATE.NOTEXECUTED) return -1;
		long durationMs = System.currentTimeMillis() - executeStartTimeMs.get();
		return (durationMs / 1000.0d);
	}
	
	/**
	 * Adds a transaction event listener to the toplevel transaction.
	 * Listeners can only be registered during begin and end.
	 * @param l
	 */
	public void addTopLevelEventListener(CirmTransactionListener l) 
	{
		CirmTransaction<?> toplevel = get();
		if (toplevel == null) throw new IllegalStateException("A listener may only be registered with the toplevel transaction during begin and end of a transaction.");
		toplevel.transactionEventSupport.addListener(l);
	}

	/**
	 * Removes a transaction event listener from the toplevel transaction.
	 * Listeners can only be registered during begin and end.
	 * @param l
	 */
	public void removeTopLevelEventListener(CirmTransactionListener l) 
	{
		CirmTransaction<?> toplevel = get();
		if (toplevel == null) throw new IllegalStateException("A listener may only be registered with the toplevel transaction during begin and end of a transaction.");
		toplevel.transactionEventSupport.addListener(l);
	}

	/**
	 * Clears all listeners from the toplevel transaction.
	 * This happens automatically for each retry.
	 */
	public void clearTopLevelEventListeners() 
	{
		CirmTransaction<?> toplevel = get();
		if (toplevel == null) throw new IllegalStateException("A listener may only be registered with the toplevel transaction during begin and end of a transaction.");
		toplevel.transactionEventSupport.clearListeners();
	}
	
	public CirmTransactionEventSupport getTransactionEventSupport()
	{
		return this.transactionEventSupport;
	}
}

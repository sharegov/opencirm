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
package org.sharegov.cirm.rdb;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.sharegov.cirm.CirmTransaction;
/**
 * Locking strategy for CirmTransactions that locks, iff both maxExecutionCount is exceeded and noLockTime is exceeded.
 * 
 * E.g. Set noLockTime to x secs to never lock if a Transaction has not been executed that long, independent of retries.
 * E.g. if noLockTime is exceeded and there have been more than maxExecutionCount attempts (retries + first), suggest to lock.
 * 
 * @author Thomas Hilpold
 *
 */
public class SimpleDBLockStrategy implements DBLockStrategy
{
	private volatile int maxExecutionCount = 1;
	private volatile double noLockTimeSec = 5;
	private final ReentrantReadWriteLock rwlock = new ReentrantReadWriteLock(true);
	private final ReadLock r = rwlock.readLock();
	private final WriteLock w = rwlock.writeLock();
	
	/**
	 * Returns true, iff both maxExecutionCount is exceeded and noLockTime is exceeded.
	 * @param t
	 * @return
	 */
	public boolean isLockRequired(CirmTransaction<?> t)
	{
		r.lock();
		try {
			int executions = t.getExecutionCount();
			double secsSinceFirstExecution = t.getTotalExecutionTimeSecs();
			return (executions > maxExecutionCount && secsSinceFirstExecution > noLockTimeSec);
		} finally
		{
			r.unlock();
		}
	}

	public int getMaxExecutionCount()
	{
		return maxExecutionCount;
	}

	public void setMaxExecutionCount(int maxExecutionCount)
	{
		if (maxExecutionCount < 0) throw new IllegalArgumentException("maxExecutionCount must be >=0, was " + maxExecutionCount);
		w.lock();
		this.maxExecutionCount = maxExecutionCount;
		w.unlock();
	}

	public double getNoLockTimeSec()
	{
		r.lock();
		try {
			return noLockTimeSec;
		} finally
		{
			r.unlock();
		}
	}

	public void setNoLockTimeSec(double noLockTimeSec)
	{
		if (noLockTimeSec < 0) throw new IllegalArgumentException("noLockTimeSec must be >=0, was " + noLockTimeSec);
		w.lock();
		this.noLockTimeSec = noLockTimeSec;
		w.unlock();
	}
}

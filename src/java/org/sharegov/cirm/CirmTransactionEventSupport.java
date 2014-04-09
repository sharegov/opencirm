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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.sharegov.cirm.CirmTransaction.STATE;

/**
 * Support class for toplevel transaction events.
 * 
 * NOT THREAD SAFE, however, it's expected to be used with a single threads context. 
 * @author Thomas Hilpold
 *
 */
public class CirmTransactionEventSupport
{
	List<CirmTransactionListener> listeners = new ArrayList<CirmTransactionListener>();
	
	/**
	 * Adds a listener.
	 * Does not check, if listener is already added.
	 * @param l
	 */
	public void addListener(CirmTransactionListener l) 
	{
		listeners.add(l);
	}
	
	/**
	 * Removes a listener
	 * @param l
	 * @return true, if the listener was in the list and removed from the list.
	 */
	public boolean removeListener(CirmTransactionListener l) 
	{
		return listeners.remove(l);
	}
	
	/**
	 * Removes all listeners currently registered.
	 */
	public void clearListeners() 
	{
		listeners.clear();
	}

	/**
	 * Gets the list of registered listeners.
	 * Life list returned. Do not modify. 
	 */
	public List<CirmTransactionListener> getListeners() 
	{
		return listeners;
	}

	/**
	 * This is called from CirmTransaction after(!) a state change.
	 * The same event object will be sent to all listeners.
	 * 
	 * @param t a CirmTransaction after a state change.
	 */
	public void fireEvent(CirmTransaction<?> t) 
	{
		STATE transactionState = t.getState();
		if (Arrays.asList(CirmTransactionListener.OBSERVED_STATES).contains(transactionState)) 
		{
			CirmTransactionEvent e = new CirmTransactionEvent(transactionState);
			for (CirmTransactionListener l : listeners)
			{
				l.transactionStateChanged(e);
			}
		}
	}	
}

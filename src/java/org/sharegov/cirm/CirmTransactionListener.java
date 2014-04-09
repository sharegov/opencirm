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

import org.sharegov.cirm.CirmTransaction.STATE;

/**
 * Listener interface for CirmTransaction.
 * Add listeners to CirmTransaction
 * @author Thomas Hilpold
 *
 */
public interface CirmTransactionListener
{
	public static final STATE[] OBSERVED_STATES = new STATE[] { STATE.SUCCEEDED, STATE.FAILED };

	/**
	 * Notifies the listener that an event occured.
	 * 
	 * @param e
	 */
	public void transactionStateChanged(final CirmTransactionEvent e); 
	
}

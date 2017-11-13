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
package org.sharegov.cirm.utils;

import java.util.Collections;
import java.util.List;

import org.sharegov.cirm.CirmTransactionEvent;
import org.sharegov.cirm.CirmTransactionListener;
import org.sharegov.cirm.legacy.CirmMessage;
import org.sharegov.cirm.legacy.MessageManager;

/**
 * Transaction listener adapter that uses the MessageManager to send one or more emails on success of the toplevel transaction.
 * 
 * If a list is provided during construction, a reference to the life messages list is held, so it can be modified externally before execution.
 * Suggested usage:
 * Create and add one object of this class for every message created in the code where it is created.
 * (To avoid Lists of CirmMessages in many signatures)
 * 
 * @author Thomas Hilpold
 *
 */
public class SendMessagesOnTxSuccessListener implements CirmTransactionListener
{

	final List<CirmMessage> cirmMessages;
	
	public SendMessagesOnTxSuccessListener(final CirmMessage message) 
	{
		if (message == null) throw new IllegalArgumentException("message was null");
		cirmMessages = Collections.singletonList(message);
	}

	public SendMessagesOnTxSuccessListener(final List<CirmMessage> messages) 
	{
		if (messages == null) throw new IllegalArgumentException("messages was null");
		cirmMessages = messages;
	}
	
	@Override
	public void transactionStateChanged(CirmTransactionEvent e)
	{
		if (e.isSucceeded()) 
		{
			if (!cirmMessages.isEmpty()) {
				ThreadLocalStopwatch.getWatch().time("SendMessagesOnTxSuccessListener sending " + cirmMessages.size());
				MessageManager.get().sendMessages(cirmMessages);
			} 
		}
	}
}

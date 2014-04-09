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
package org.sharegov.cirm.legacy;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeMessage;

/**
 * CirmMessage, a MimeMessage with a detailed explanation of when and why it was created that can be included in the message body.
 *  
 * @author Thomas Hilpold
 *
 */
public class CirmMessage extends MimeMessage
{

	private Date creationTime = new Date();
	private List<String> explanations = new LinkedList<String>();
	
	public CirmMessage(Session arg0)
	{
		super(arg0);
	}

	public CirmMessage(MimeMessage arg0) throws MessagingException
	{
		super(arg0);
	}

	public CirmMessage(Session arg0, InputStream arg1)
			throws MessagingException
	{
		super(arg0, arg1);
	}

	public CirmMessage(Folder arg0, int arg1)
	{
		super(arg0, arg1);
	}

	public CirmMessage(Folder arg0, InputStream arg1, int arg2)
			throws MessagingException
	{
		super(arg0, arg1, arg2);
	}

	public CirmMessage(Folder arg0, InternetHeaders arg1, byte[] arg2, int arg3)
			throws MessagingException
	{
		super(arg0, arg1, arg2, arg3);
	}
	
	public List<String> getExplanations()
	{
		return explanations;
	}

	public void setExplanations(List<String> explanations)
	{
		if (explanations != null)
			this.explanations = explanations;
		else
			explanations = new LinkedList<String>();
	}

	public Date getCreationTime()
	{
		return creationTime;
	}

	public void setCreationTime(Date creationTime)
	{
		this.creationTime = creationTime;
	}


	/**
	 * Adds an explanation as head to the list of explanations
	 * @param activity
	 * @param trigger
	 * @param msgTemplate
	 */
	public void addExplanation(String explanation) 
	{
		explanations.add(0, explanation);
	}

	public String getExplanation() 
	{
		StringBuffer result = new StringBuffer(300);
		for (String s : explanations)
		{
			result.append(s + " \r\n");
		}
		result.append("Created " + SimpleDateFormat.getDateTimeInstance().format(getCreationTime()) + "\r\n");
		return result.toString();
	}

	public String getExplanationHTML() 
	{
		StringBuffer result = new StringBuffer(300);
		for (String s : explanations)
		{
			result.append(s + " \r\n<br>");
		}
		result.append("Created " + SimpleDateFormat.getDateTimeInstance().format(getCreationTime()) + "<br>\r\n");
		return result.toString();
	}
	
	/**
	 * Includes the explanation as white html to the end of the message content/body, if content is String and not null. 
	 * Call this once after all explanations were provided and before the message is first sent.
	 */
	public void includeExplanationInBody() 
	{
		if (explanations.isEmpty()) return;
		try {
			Object content = getContent();
			if (content !=null && content instanceof String) 
			{
				String cStr = (String) content;
				String explanation = "<br><br><br><p style=\"color:white\" \r\n";
				explanation = explanation + getExplanationHTML();
				explanation = explanation + "</p>";
				cStr = cStr + explanation;
				//setContent(cStr, getContentType());
				getContentType();
				setContent(cStr, "text/html");
			}
		} catch(Exception e) 
		{
			System.err.println("CirmMessage.includeExplanationInBody failed with " + e.getClass() + e.getMessage());
		}
	}

}

package org.sharegov.cirm.legacy;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * Cirm SMS Message
 * @author Syed, Camilo, Thomas Hilpold
 *
 */
public class CirmSmsMessage implements CirmMessage {

	private String phone;
	private String txt;

	private Date creationTime = new Date();
	private List<String> explanations = new LinkedList<String>();

	public String getPhone() {
		return phone;
	}
	public void setPhone(String phone) {
		this.phone = phone;
	}
	public String getTxt() {
		return txt;
	}
	public void setTxt(String txt) {
		this.txt = txt;
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
}

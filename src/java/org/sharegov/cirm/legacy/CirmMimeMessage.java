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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import mjson.Json;

/**
 * CirmMimeMessage, a MimeMessage with a detailed explanation of when and why it was created that can be included in the message body.
 *  
 * @author Thomas Hilpold
 *
 */
public class CirmMimeMessage extends MimeMessage implements CirmMessage
{

	private Date creationTime = new Date();
	private List<String> explanations = new LinkedList<String>();
	
	public CirmMimeMessage(Session arg0)
	{
		super(arg0);
	}

	public CirmMimeMessage(MimeMessage arg0) throws MessagingException
	{
		super(arg0);
	}

	public CirmMimeMessage(Session arg0, InputStream arg1)
			throws MessagingException
	{
		super(arg0, arg1);
	}

	public CirmMimeMessage(Folder arg0, int arg1)
	{
		super(arg0, arg1);
	}

	public CirmMimeMessage(Folder arg0, InputStream arg1, int arg2)
			throws MessagingException
	{
		super(arg0, arg1, arg2);
	}

	public CirmMimeMessage(Folder arg0, InternetHeaders arg1, byte[] arg2, int arg3)
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
				setContent(cStr, "text/html; charset=UTF-8");
				this.saveChanges();
			}
		} catch(Exception e) 
		{
			System.err.println("CirmMimeMessage.includeExplanationInBody failed with " + e.getClass() + e.getMessage());
		}
	}

    @Override
    public Json toJson() {
        Json msg = Json.object();
        msg.set("type", "EMAIL");
        msg.set("from", getFromStr());
        msg.set("to", getRecipientsAsStrList(Message.RecipientType.TO));
        msg.set("cc", getRecipientsAsStrList(Message.RecipientType.CC));
        msg.set("bcc", getRecipientsAsStrList(Message.RecipientType.BCC));
        msg.set("subject", getSubjectStr());
        msg.set("body", getHtmlBodyAndType()[0]);
        msg.set("bodyType", getHtmlBodyAndType()[1]);
        msg.set("multipart", getNonBodyMultiparts());
        msg.set("explanation", getExplanationHTML());
        msg.set("hasHighPriority", isHighPriority());
        String createdOnStr = OffsetDateTime.ofInstant(getCreationTime().toInstant(), ZoneId.systemDefault()).toString();
        msg.set("createdOn", createdOnStr);
        return msg;
    }

    Json getNonBodyMultiparts() {
        Json result = Json.array();
        try {
            this.saveChanges();
            result = getNonBodyMultipartsImpl();
        } catch (MessagingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    //Always array
    Json getNonBodyMultipartsImpl() throws MessagingException, IOException {
        Json result = Json.array();
        Object content = getContent();
        if (content instanceof MimeMultipart) {
            MimeMultipart mp = (MimeMultipart) content;
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                if (!(bp.getContent() instanceof String)) {
                    Json bodyPartJson = convertBodyPartToJson(bp);
                    if (bodyPartJson != null) {
                        result.add(bodyPartJson);
                    }
                }
            }
        }
        return result;
    }

    Json convertBodyPartToJson(BodyPart bp) {
        Json result = null;
        try {
            if (bp instanceof MimeBodyPart) {
                result = convertMimeBodyPartToJson((MimeBodyPart)bp);
            } else {
                System.err.println("Ignoring non Mime Bodypart: " + bp);
            }
        } catch (MessagingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    Json convertMimeBodyPartToJson(MimeBodyPart bp) throws MessagingException, IOException {
        if (bp.getFileName() == null) {
            System.out.println("Not a file attachement, not implemted");
            return null;
        }
        String fileName = bp.getFileName();
        String contentType = bp.getContentType();
        String contentId = bp.getContentID();
        String data = null;
        Object contentData = bp.getContent();
        if (contentData instanceof byte[]) {
            data = Base64.getEncoder().encodeToString((byte[])contentData);
        } else if (contentData instanceof InputStream) {
            data = readInputStreamAsBase64((InputStream)contentData);
        }
        Json result = Json.object();
        result.set("contentType", contentType);
        result.set("contentId", contentId);
        result.set("fileName", fileName);
        result.set("data", data);
        return result;
    }
    
    private String readInputStreamAsBase64(InputStream in) throws IOException {
        byte[] inBytes = readInputStream(in);
        String result =  Base64.getEncoder().encodeToString(inBytes);
        System.out.println("Encoded instream complete:");
        System.out.println(result);
        return result;
    }
    
    private byte[] readInputStream(InputStream in) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = in.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
        return os.toByteArray();
    }
    
    /**
     * Returns a list of recipients of the given type.
     * @param type
     * @return list of RFC822 compliant address strings
     */
    private List<String> getRecipientsAsStrList(Message.RecipientType type) {
        List<String> result = new ArrayList<String>();
        try {
            Address[] recipients = getRecipients(type);
            if (recipients != null) {
                for (Address a : recipients) {
                    //ToString on InternetAddress gives RFC822 compliant representations
                    result.add(a.toString());
                }
            }
        } catch (MessagingException e) {
            e.printStackTrace();
        }
        return result;
    }
    
    private String getFromStr() {
        String result = null;
        Address[] from;
        try {
            from = getFrom();
            if (from.length > 0) {
                result = from[0].toString();
            }
        } catch (MessagingException e) {
            e.printStackTrace();
        }
        return result;
    }
    
    private String[] getHtmlBodyAndType() {
        String[] result = new String[2]; 
        Object content;
        try {
            content = getContent();
            if (content instanceof String) {
                result[0] = (String)content;
                result[1] = getContentType();
            } else if (content instanceof MimeMultipart) {
                MimeMultipart mp = (MimeMultipart) content;
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart p = mp.getBodyPart(i);
                    if (p.getContent() instanceof String) {
                        result[0] = (String) p.getContent();
                        result[1] = (String) p.getContentType();
                        break;
                    }
                }
            }
        } catch (IOException | MessagingException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return result;
    }
    
    private boolean isHighPriority() {
        boolean result = false;
        String[] priVals;
        try {
            priVals = getHeader("X-Priority");
            if (priVals != null && priVals.length > 0) {
                result = "1".equals(priVals[0]);
            }
        } catch (MessagingException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return result;
    }
    
    private String getSubjectStr() {
        String result = "";
        try {
            result = getSubject();
        } catch (MessagingException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to get subject", e);
        }
        return result;
    }
}

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

import static mjson.Json.object;

import java.beans.PropertyDescriptor;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.DatatypeConverter;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

import mjson.Json;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.io.IOUtils;
import org.hypergraphdb.type.BonesOfBeans;
//import com.google.gson.*;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.StartUp;
import org.sharegov.cirm.SysRefs;
import org.sharegov.cirm.legacy.MessageManager;
import org.sharegov.cirm.rest.RestServiceAdmin;
import org.w3c.dom.Document;

public class GenUtils
{
	public static final String TIMETASK_NOTRANS_MARKER = "NOTRANS";
	private static final ThreadLocal<SimpleDateFormat> ISO_DATE_FORMATS = new ThreadLocal<SimpleDateFormat>();
	public static final String isoDatePattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
	public static final String SERVER_NAME_2 = SysRefs.serverName2.resolve();
	

	public static URL makeLocalURL(String relativePath)
	{
		try
		{
			if(StartUp.getConfig().is("ssl",true))
			{
				return new URL("https://"
						+ InetAddress.getLocalHost().getHostName().toLowerCase()
						+ ":" + StartUp.getConfig().at("ssl-port") + relativePath);
			}else
			{
				return new URL("http://"
						+ InetAddress.getLocalHost().getHostName().toLowerCase()
						+ ":" + StartUp.getConfig().at("port") + relativePath);
			}
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	public static String readString(Reader reader) throws IOException
	{
		StringBuilder sb = new StringBuilder();
		BufferedReader b = new BufferedReader(reader);
		for (String l = b.readLine(); l != null; l = b.readLine())
			sb.append(l);
		return sb.toString();
	}

	public static String serializeAsString(Object x)
	{
		try
		{
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutputStream out = new ObjectOutputStream(bos);
			out.writeObject(x);
			out.close();
			return DatatypeConverter.printBase64Binary(bos.toByteArray());
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
	}

	public static Object deserializeFromString(String x)
	{
		try
		{
			byte[] A = DatatypeConverter.parseBase64Binary(x);
			ByteArrayInputStream bin = new ByteArrayInputStream(A);
			ObjectInputStream in = new ObjectInputStream(bin);
			return in.readObject();
		}
		catch (Exception t)
		{
			throw new RuntimeException(t);
		}
	}

	public static String readTextFile(File f)
	{
		try
		{
			FileReader reader = new FileReader(f);
			StringBuilder sb = new StringBuilder();
			try
			{
				char[] buf = new char[4096];
				for (int cnt = reader.read(buf); cnt > -1; cnt = reader.read(buf))
					sb.append(buf, 0, cnt);
			}
			finally
			{
				reader.close();
			}
			return sb.toString();
		}
		catch (IOException ex)
		{
			throw new RuntimeException(ex);
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T cloneBean(T bean)
	{
		try
		{
			T clone = (T) bean.getClass().newInstance();
			for (PropertyDescriptor desc : BonesOfBeans
					.getAllPropertyDescriptors(bean).values())
			{
				if (desc.getReadMethod() == null
						|| desc.getWriteMethod() == null)
					continue;
				BonesOfBeans.setProperty(clone, desc,
						BonesOfBeans.getProperty(bean, desc));
			}
			return clone;
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
	}

	private static volatile boolean dbgLevelTracing = false;
	
	public static boolean dbg()
	{
	    return dbgLevelTracing;
	}
	
	public static void dbg(boolean newdbgLevelTracing)
	{
	    dbgLevelTracing = newdbgLevelTracing; 
	}
	
	public static Json ok()
	{
		return Json.object("ok", true, "server", SERVER_NAME_2);
	}

	public static void pagination(Json paginationJson, Json paginationCriteria)
	{
		int currentPage = paginationCriteria.at("currentPage").asInteger();
		int itemsPerPage = paginationCriteria.at("itemsPerPage").asInteger();
		int minValue = ((currentPage - 1) * itemsPerPage) + 1;
		int maxValue = (minValue - 1) + itemsPerPage;
		paginationJson.set("minValue", minValue);
		paginationJson.set("maxValue", maxValue);
	}

	public static String trim(String str)
	{
		if (str == null || str.equals("null") || str.equals(null))
			return "N/A";
		else
			return str.trim();
	}

	public static Json ko(String error)
	{
		return Json.object("ok", false, "error", error, "server", SERVER_NAME_2);
	}

	public static Json ko(Throwable t)
	{
		return Json.object("ok", false, 
						   "error", t.toString(), 
						   "stackTrace", stackTrace(t),
						   "server", SERVER_NAME_2);
	}

	public static byte[] getBytesFromFile(File file) throws IOException
	{
		return getBytesFromStream(new FileInputStream(file), true);
	}

	// Returns the contents of the file in a byte array.
	public static byte[] getBytesFromStream(InputStream is, boolean close)
			throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try
		{
			byte[] A = new byte[4096];
			// Read in the bytes
			for (int cnt = is.read(A); cnt > -1; cnt = is.read(A))
				out.write(A, 0, cnt);
			return out.toByteArray();
			// Close the input stream and return bytes
		}
		finally
		{
			if (close)
				is.close();
		}
	}

	public static Json httpGetJson(String url)
	{
		HttpClient client = new HttpClient();
		GetMethod method = new GetMethod(url);
		try
		{
			// disable retries from within the HTTP client
			client.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, 
					new DefaultHttpMethodRetryHandler(0, false));
			int statusCode = client.executeMethod(method);
			if (statusCode != HttpStatus.SC_OK)
				throw new RuntimeException("HTTP Error " + statusCode
						+ " while calling " + url.toString());
			return Json.read(method.getResponseBodyAsString());
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
		finally
		{
			method.releaseConnection();
		}
	}

	public static String httpDelete(String url, String...headers)
	{
		HttpClient client = new HttpClient();
		DeleteMethod method = new DeleteMethod(url);
		if (headers != null)
		{
			if (headers.length % 2 != 0)
				throw new IllegalArgumentException("Odd number of headers argument, specify HTTP headers in pairs: name then value, etc.");
			for (int i = 0; i < headers.length; i++)
				method.addRequestHeader(headers[i], headers[++i]);
		}
		try
		{
			// disable retries from within the HTTP client			 
			client.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, 
					new DefaultHttpMethodRetryHandler(0, false));			
			int statusCode = client.executeMethod(method);
			if (statusCode != HttpStatus.SC_OK)
				throw new RuntimeException("HTTP Error " + statusCode
						+ " while deleting " + url.toString());
			return method.getResponseBodyAsString();
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
		finally
		{
			method.releaseConnection();
		}		
	}
	
	@SuppressWarnings("deprecation")
	public static String httpPost(String url, String data, String...headers)
	{
		HttpClient client = new HttpClient();
		PostMethod method = new PostMethod(url);
		if (headers != null)
		{
			if (headers.length % 2 != 0)
				throw new IllegalArgumentException("Odd number of headers argument, specify HTTP headers in pairs: name then value, etc.");
			for (int i = 0; i < headers.length; i++)
				method.addRequestHeader(headers[i], headers[++i]);
		}
		method.setRequestBody(data);
		try
		{
			// disable retries from within the HTTP client			 
			client.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, 
					new DefaultHttpMethodRetryHandler(0, false));
			client.getParams().setParameter(HttpMethodParams.SO_TIMEOUT, 0);
			int statusCode = client.executeMethod(method);
			if (statusCode != HttpStatus.SC_OK)
				throw new RuntimeException("HTTP Error " + statusCode
						+ " while post to " + url.toString() + ", body " + data);
			return method.getResponseBodyAsString();
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
		finally
		{
			method.releaseConnection();
		}
	}
	
	public static Json httpPostWithBasicAuth(String url, String username, String password, String postData, String...headers)
	{
		try {
			URL uri = new URL (url);
			String credentials = username + ":" + password;
			String encoding = Base64.encode(credentials.getBytes(), false);
			
			HttpURLConnection connection = (HttpURLConnection) uri.openConnection();
	        connection.setRequestMethod("POST");
	        connection.setDoOutput(true);
	        connection.setRequestProperty  ("Authorization", "Basic " + encoding);
	        
	        if (headers != null)
			{
				if (headers.length % 2 != 0)
					throw new IllegalArgumentException("Odd number of headers argument, specify HTTP headers in pairs: name then value, etc.");
				for (int i = 0; i < headers.length; i++)
					connection.setRequestProperty (headers[i], headers[++i]);
			}
	        
	        if (postData != null && !postData.isEmpty()){		        
		        OutputStreamWriter wr = new OutputStreamWriter(connection.getOutputStream());
		        wr.write(postData);
		        wr.flush();
	        }

	        int HttpResult = connection.getResponseCode(); 
	        connection.disconnect();
	        Json result = Json.object().set("response_code", HttpResult);
	        if(HttpResult == HttpURLConnection.HTTP_OK){	        
	        	return result.set("response", IOUtils.toString((InputStream)connection.getInputStream()));
	        } else return result;
	        
		} catch(Exception e) {
			return Json.object().set("error", e.getMessage());
        }		
		
	}

	public static Json httpPostJson(String url, Json json)
	{
		String response = httpPost(url, json.toString(), "Content-Type", "application/json");
		if (response == null) {
			return null;
		} else if (response.isEmpty()) {
			return Json.make("");
		} else {
			return Json.read(response);
		}
	}
	
	public static Document httpPostXml(String url, Document xml)
	{
		return XMLU.parse(httpPost(url, XMLU.stringify(xml), "Content-Type", "text/xml"));
	}
	
	/**
	 * Return the string representation of a Json structure that is normalized
	 * so that two Json's will yield the same string representation iff they are
	 * equal. This is done simply by stringify-ing Json objects with the
	 * properties ordered by name. The other Json types already have an
	 * unambiguous unique format.
	 */
	public static String normalizeAsString(Json data)
	{
		if (!data.isObject())
			return data.toString();
		StringBuilder sb = new StringBuilder("{");
		ArrayList<String> props = new ArrayList<String>(data.asJsonMap()
				.keySet());
		Collections.sort(props);
		for (int i = 0; i < props.size(); i++)
		{
			String p = props.get(i);
			sb.append("\"" + p + "\":" + normalizeAsString(data.at(p)));
			if (i < props.size() - 1)
				sb.append(",");
		}
		sb.append("}");
		return sb.toString();
	}

	public static String readTextResource(String resource)
	{
		InputStream in = GenUtils.class.getResourceAsStream(resource);
		if (in == null)
			return null;
		else
			try
			{
				return new String(getBytesFromStream(in, true));
			}
			catch (IOException e)
			{
				throw new RuntimeException(e);
			}
	}

	private static Pattern whiteSpacePattern = Pattern.compile(".*\\s+.*");

	public static boolean containsWhiteSpace(String value)
	{
		return whiteSpacePattern.matcher(value).matches();
	}

	public static <T> Set<T> set(T... elements)
	{
		HashSet<T> S = new HashSet<T>();
		for (T x : elements)
			S.add(x);
		return S;
	}
	
	private static DatatypeFactory xmlDatatypeFactory;
	
   
	public static java.util.Date parseDate(OWLLiteral literal)
    {
           Date result = new Date();
           String value = literal.getLiteral();
           if(xmlDatatypeFactory == null)
           {
        	   	try
	       		{
	       			xmlDatatypeFactory = DatatypeFactory.newInstance();
	       		} catch (DatatypeConfigurationException e)
	       		{
	       			throw new RuntimeException("Failed to create xmlDataFactory", e);
	       		}
           }
           try
           {
               	  // parse ISO 8601 date
                  synchronized (xmlDatatypeFactory)
                  {
                        try
                        {
                               result = xmlDatatypeFactory.newXMLGregorianCalendar(value)
                                             .toGregorianCalendar().getTime();
                        } catch (IllegalArgumentException t)
                        {
                               result = parseDate(value);
                        }
                  }
           } catch (Exception e)
           {
                  ThreadLocalStopwatch.getWatch().time("Error: Could not parse date " + value + " as ISO 8601");
                  throw new RuntimeException(e);
           }
           return result;
    }

	public static java.util.Date parseDate(String s, String format)
	{
		try
		{
			SimpleDateFormat fmt = new SimpleDateFormat(format);
			return fmt.parse(s);
		}
		catch (ParseException ex)
		{
			throw new RuntimeException(ex);
		}
	}
	
	/**
	 * Parses a date string in iso format. 
	 * Fully thread safe and non blocking, by keeping one date format per thread.
	 * 
	 * @param s
	 * @return
	 */
	public static java.util.Date parseDate(String isoDateString)
	{
		try
		{
			SimpleDateFormat myDateFormat = ISO_DATE_FORMATS.get();
			if (myDateFormat == null)
			{
				myDateFormat = new SimpleDateFormat(isoDatePattern); 
				ISO_DATE_FORMATS.set(myDateFormat);
			}
			return myDateFormat.parse(isoDateString);
		}
		catch (ParseException ex)
		{
			throw new RuntimeException(ex);
		}
	}
	
	/**
	 * Formats a date in iso format.
	 * Fully thread safe and non blocking, by keeping one date format per thread.
	 * 
	 * @param d
	 * @return a date as iso formatted string.
	 */
	public static String formatDate(java.util.Date d)
	{
		SimpleDateFormat myDateFormat = ISO_DATE_FORMATS.get();
		if (myDateFormat == null)
		{
			myDateFormat = new SimpleDateFormat(isoDatePattern); 
			ISO_DATE_FORMATS.set(myDateFormat);
		}
		return myDateFormat.format(d);
	}
	
	/**
	 * This method returns the timestamp in the given format.
	 * In case of null timestamp and exceptions return "N/A"
	 */
	public static String formatDate(Timestamp ts, String format)
	{
		SimpleDateFormat sdf = new SimpleDateFormat(format);
		try
		{
			if (ts == null)
				return "N/A";
			else
				return sdf.format(ts);
		}
		catch (Exception e)
		{
			return "N/A";
		}
	}

	/**
	 * This method creates the Service Case Number in 'YY-1XXXXXXX' format
	 * YY: 2 digit year
	 * Starts with '1': identifies this Service Case was created via CiRM 
	 *  
	 * @param id : sequence No. generated by the database
	 * @return
	 */
	public static String makeCaseNumber(long id) {
		int length = 7;
		String startsWith = "1";
		String reqFormat = String.format("%%0%dd", length);
		String result = String.format(reqFormat, id);
		StringBuilder sb = new StringBuilder();
		//sb.append("AC");
		String year = Integer.toString(Calendar.getInstance().get(Calendar.YEAR));
		//sb.append(year);
		sb.append(year.substring(year.length()-2));
		sb.append("-");
		sb.append(startsWith);
		sb.append(result);
		return sb.toString();
	}
	
	public static void ensureArray(Json j, String field)
	{
		Json f = j.at(field);
		if (f == null)
			j.set(field, Json.array());
		else if (!f.isArray())
			j.set(field, Json.array().add(f));
	}
	
	public static void timeStamp(Json A)
    {
    	//java.text.DateFormat format = new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss a");
    	if (A == null || A.isPrimitive())
    		return;
    	else if (A.isObject() && !A.has("hasDateCreated"))
    		A.set("hasDateCreated",  GenUtils.formatDate(new java.util.Date()));
    	else if (A.isArray())
    		for (Json x : A.asJsonList())
    			timeStamp(x);
    		//A.set("hasDateCreate", new Date().)
    }
	
	public static String stackTrace(Throwable t)
	{
    	java.io.StringWriter strWriter = new java.io.StringWriter();
    	java.io.PrintWriter prWriter = new PrintWriter(strWriter);
    	t.printStackTrace(prWriter);
    	prWriter.flush();
    	return strWriter.toString();
	}
	
    public static Throwable getRootCause(Throwable t)
    {
    	if (t != null)
    		while (t.getCause() != null)
    			t = t.getCause();
        return t;
    }
    
    public static void rethrowRuntime(Throwable t)
    {
		if (t instanceof RuntimeException)
			throw (RuntimeException)t;
		else 
			throw new RuntimeException(t);
    }
    
    /**
     * Ensures that a string starts with a capitalized letter.
     * @param s accepts a string, null, empty
     * @return
     */
    public static String capitalize(String s) 
    {
    	if (s == null || s.isEmpty()) return s;    	
    	char first = s.charAt(0);
    	if (Character.isLetter(first) && Character.isLowerCase(first)) 
    		return Character.toUpperCase(first) + s.substring(1);
    	else 
    		return s;
    }

    public static void reportPWGisProblem(String caseNumber, Json error)
    {
    	ThreadLocalStopwatch.getWatch().time("reportPWGisProblem email sent: " + caseNumber);
    	String body = "<p>Case " + caseNumber + " has invalid extra GIS info.</p>";
    	body += "<p>"  + error + "</p>";
    	MessageManager.get().sendEmail("cirm@miamidade.gov", 
    						"angel.martin@miamidade.gov;silval@miamidade.gov", 
    						"[PW GIS ISSUE] " + caseNumber, body);
    }
    
    public static void reportFatal(String subject, String msg, Throwable t)
    {
    	ThreadLocalStopwatch.getWatch().time("ReportFatal email sent: " + msg + " " + t);
    	logStackTrace(t.getStackTrace(), 10);
    	
    	OWLLiteral recipient = Refs.configSet.resolve().get("FatalErrorEmail");
    	if (recipient == null)
    		return;
    	String body = "<p>Exception message:</p><p><b>" + msg + "</b></p>";
    	body += "<p>" + new RestServiceAdmin().sysInfo().toString() + "</p>";
    	if (t != null)
    		body += "<hr><p>Stack Trace:</p>" +
    				"<pre>" + stackTrace(t) + "</pre>";
    	if (subject == null)
    		subject = "";
    	MessageManager.get().sendEmail("cirm@miamidade.gov", 
    						recipient.getLiteral(), 
    						"[CIRM FATAL] " + subject, body);
    }

    /**
     * <p>
     * Ask the TimeServer to call back the <code>url</code> in <code>minutesFromNow</code> minutes.
     * If the <code>url</code> parameter starts with 'http' it is used as is, otherwise, the 
     * <code>OperationsRestService</code> from the ontology is used. The callback will be an HTTP POST
     * if the <code>post</code> parameter is not null.
     * taskId will be url + minutesFromNow + transUUID. url + minutesFromNow should be the same for each transaction retry but unique during one.
     * </p>
     * 
     * @param minutesFromNow
     * @param url
     * @param post
     * @return
     */
    public static Json timeTask(int minutesFromNow, String url, Json post)
    {
    	String taskId;
    	UUID cirmTransactionUUID;
    	long transactionBeginTime;
    	if (CirmTransaction.isExecutingOnThisThread()) 
    	{
    		cirmTransactionUUID = CirmTransaction.getTopLevelTransactionUUID();
    		transactionBeginTime = CirmTransaction.get().getBeginTimeMs();
    		taskId = transactionBeginTime + "_" + url + minutesFromNow;
    		NewTimeTaskOnTxSuccessListener s = new NewTimeTaskOnTxSuccessListener(cirmTransactionUUID, taskId, minutesFromNow, url, post);
    		CirmTransaction.get().addTopLevelEventListener(s);
        	return ok();
    	} else {
    		ThreadLocalStopwatch.getWatch().time("Genutils timetask with url/minsFromNow called outside of a transaction. Using now, a new RandomUUID for task a NOTRANS marker in taskid.");
    		cirmTransactionUUID = UUID.randomUUID();
    		transactionBeginTime = new Date().getTime();
    		taskId = transactionBeginTime + "_" + TIMETASK_NOTRANS_MARKER + "_" + url + minutesFromNow;
        	return timeTaskDirect(cirmTransactionUUID, taskId, minutesFromNow, url, post);
    	}
    }    

    /**
     * Schedules a time machine callback task at a given time (calendar) by posting a new task to the time machine.
     * Repeated calls due to retries inside a transaction will only be inserted into the time machine once after
     * the transaction succeeds (due to NewTimeTaskOnTxSuccessListener).
     * Creation time is established by prefixing the task name with the transaction begin time.
     * (This establishes some order in the time machine)
     * Used by activity manager (on each transaction retry).
     * @param taskId a taskId that should be unique for the task.
     * @param cal
     * @param url
     * @param post
     * @return
     */
    public static Json timeTask(String taskId, Calendar cal, String url, Json post)
	{
    	String taskIdMod;
    	UUID cirmTransactionUUID;
    	long transactionBeginTime;
    	if (CirmTransaction.isExecutingOnThisThread()) 
    	{
    		cirmTransactionUUID = CirmTransaction.getTopLevelTransactionUUID();
    		transactionBeginTime = CirmTransaction.get().getBeginTimeMs();
    		taskIdMod = transactionBeginTime + "_" + taskId;
    		//Register Tx listener to be executed once only on success.
    		NewTimeTaskOnTxSuccessListener s = new NewTimeTaskOnTxSuccessListener(cirmTransactionUUID, taskIdMod, cal, url, post);
    		CirmTransaction.get().addTopLevelEventListener(s);
    		return ok();
    	} else {
    		System.err.println("Genutils timetask with taskId called outside of a transaction. Using now, a new RandomUUID for task and a NOTRANS marker in taskid.");
    		cirmTransactionUUID = UUID.randomUUID();
    		transactionBeginTime = new Date().getTime();
    		taskIdMod = transactionBeginTime + "_" + TIMETASK_NOTRANS_MARKER + "_" + taskId;
    		return timeTaskCalDirect(cirmTransactionUUID, taskIdMod, cal, url, post);
    	}
    	
   	}

    /**
     * <p>
     * Ask the TimeServer to call back the <code>url</code> in <code>minutesFromNow</code> minutes.
     * If the <code>url</code> parameter starts with 'http' it is used as is, otherwise, the 
     * <code>OperationsRestService</code> from the ontology is used. The callback will be an HTTP POST
     * if the <code>post</code> parameter is not null.
     * </p>
     * Task name will be cirmTransactionUUID + TaskId, to ensure a proper key value for overwrites during in transaction retries.
     * 
     * @param minutesFromNow
     * @param url
     * @param post
     * @param CirmTransactionUUID a unique transaction identifier that remains the same in retries.
     * @param taskId task identifier for the parameter combination - must be the same during all retries to avoid duplicates.
     * @return
     */
    protected static Json timeTaskDirect(UUID cirmTransactionUUID, String taskId, int minutesFromNow, String url, Json post)
    {
    	if (!url.startsWith("http"))
    	{
    		Json thisService = OWL.toJSON((OWLIndividual)Refs.configSet.resolve().get("OperationsRestService"));
    		url = thisService.at("hasUrl").asString() + url;
    	}
    	//Group & name constitutes the key in the time machine
    	//To ensure A) safe retries that overwrite equal tasks 
    	//and B) allow multiple new tasks per transaction
    	//we use a combination of cirmTransactionUUID + taskId as name part of the key.
    	// This ensures that a retry will overwrite an existing and not add a new task.
    	Calendar cal = Calendar.getInstance();
    	cal.add(Calendar.MINUTE, minutesFromNow);
		return timeTaskCalDirect(cirmTransactionUUID, taskId, cal, url, post);
    }

	protected static Json timeTaskCalDirect(UUID cirmTransactionUUID, String taskId, Calendar cal, String url, Json post)
	{
		//DBG
		String taskName = taskId + "-" + cirmTransactionUUID.toString();
		System.out.println("GENUTIL TIMETASK ID: " + taskName);
		final Json taskSpec = object(); 
		final Json restCall = object("url", url, "method", post == null ? "GET" : "POST");
		if (post != null)
			restCall.set("content", post);
		taskSpec.set("restCall", restCall)
				.set("group", "cirm_services")
				.set("name", taskName)
				.set("state", "NORMAL")
				.set("scheduleType", "SIMPLE")
				.set("startTime", object()
					.set("day_of_month", cal.get(Calendar.DATE))
					.set("month", cal.get(Calendar.MONTH) + 1)
					.set("year", cal.get(Calendar.YEAR))
					.set("hour", cal.get(Calendar.HOUR_OF_DAY))
					.set("minute", cal.get(Calendar.MINUTE))
					.set("second", cal.get(Calendar.SECOND)));
		final Json timeMachine = OWL.toJSON((OWLIndividual)Refs.configSet.resolve().get("TimeMachineConfig"));				
		System.out.println("Time Machine url:" + timeMachine.at("hasUrl"));
		return GenUtils.httpPostJson(timeMachine.at("hasUrl").asString() + "/task", taskSpec);
	}
    
    /**
     * Gets the first email address found in the string or null.
     * @param anyString
     */
    public static String findEmailIn(String anyString)
    {
		Matcher m = Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+").matcher(anyString);
		if (m.find()) {
			return m.group();
		}
		//TODO maybe return all semicolon separated.
		return null;    	
    }
    
    
    /**
     * Thread.sleep, but with an unchecked exception (wraps and rethrows as RuntimeException);
     * @param millis
     */
    public static void sleep(int millis)
    {
    	try { Thread.sleep(millis); } catch (Exception ex) { throw new RuntimeException(ex); }
    }
	    
    
    /**
     * Reads UTF 8 from a URL into String (usable to load form file also).
     *  
     * @param url
     * @return
     * @throws RuntimeException on any exception.
     */
	public static String readAsStringUTF8(URL url) {
		StringBuffer str = new StringBuffer(10000);
		String cur;
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"));
			do {
				cur = br.readLine();
				if (cur != null) {
					str.append(cur);
				}
			} while(cur != null);			
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally
		{
			try
			{
				if (br != null) br.close();
			} catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		return str.toString();
	}

	/**
	 * Prints maxElems of a stack trace using ThreadLocalStopWatch to see the thread.
	 * 
	 * use Thread.currentThread().getStackTrace() to get one.
	 * 
	 * Thread safe. 
	 * 
	 * @param trace null tolerated, will print an error trace
	 * @param maxElems all values tolerated, if !>0 an error message will be logged.
	 */
	public static void logStackTrace(StackTraceElement[] trace, int maxLines)
	{
		if (trace != null && maxLines > 0) 
		{
			int i = 0;
			while (i < trace.length && i < maxLines) 
			{
				ThreadLocalStopwatch.getWatch().time("" + trace[i].toString());
				i++;
			}
		} else
		{
			ThreadLocalStopwatch.getWatch().time("Error: irgnored: GenUtils.logStackTrace() trace was " + trace + " maxLines was " + maxLines);
		}
	}
}

/*******************************************************************************
 * Copyright 2015 Miami-Dade County
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

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.sharegov.cirm.utils.SslContextedSecureProtocolSocketFactory;

/**
 * Utilities for starting embedded web servers. 
 * 
 * @author Thomas Hilpold
 *
 */
public class StartupUtils
{	

	/**
	 * Gets all supported cipher suites that are considered strong as String.
	 * 
	 * @return  space separated String
	 */
	static String getStrongSSLCipherSuitesParamString() {
		return getListAsParamString(getStrongSSLCipherSuites());
	}

	/**
	 * Gets all supported cipher suites that are considered weak as String.
	 * 
	 * @return space separated String
	 */
	static String getWeakSSLCipherSuitesParamString() {
		return getListAsParamString(getWeakSSLCipherSuites());
	}

	/**
	 * Converts a list of Strings into a single space separated string.
	 * 
	 * @param stringList a list of strings
	 * @return a single line string containing list items separared by spaces.
	 */
	static String getListAsParamString(List<String> stringList) {
		StringBuffer result = new StringBuffer(1000);
		Iterator<String> cipherIt = stringList.iterator();
		while(cipherIt.hasNext()) {
			result.append(cipherIt.next());
			if (cipherIt.hasNext()) result.append(" ");
		}
		return result.toString();
	}

	/**
	 * Gets a list of all supported cipher suites that are considered strong.
	 */
	static List<String> getStrongSSLCipherSuites() {
		List<String> strongCiphers = new LinkedList<>();
		for (String cipher : getSupportedCipherSuites()) {
			if (cipher.startsWith("TLS_DHE_RSA") || cipher.startsWith("TLS_ECDHE")) {
				strongCiphers.add(cipher);
				System.out.println(cipher);
			}
		}
		return strongCiphers;
	}

	/**
	 * Gets a list of all supported cipher suites that are considered weak.
	 */
	static List<String> getWeakSSLCipherSuites() {
		List<String> weakCiphers = new LinkedList<>();
		for (String cipher : getSupportedCipherSuites()) {
			if (cipher.contains("NULL") 
					|| cipher.contains("RC4")
					|| cipher.contains("MD5")
					|| cipher.contains("DES")
					|| cipher.contains("DSS")					
					) {
				weakCiphers.add(cipher);
			}
		}
		return weakCiphers;
	}


	/**
	 * Gets all supported cipher suites in the java runtime.
	 * Note that this depends on secuity policy and java version.
	 * 
	 * @return supported ciphers as sorted set of strings.
	 */
	static SortedSet<String> getSupportedCipherSuites() {
		SSLContext ctx;
		try
		{
			ctx = SSLContext.getDefault();
		} catch (NoSuchAlgorithmException e)
		{
			throw new RuntimeException(e);
		}
		SSLSocketFactory sf = ctx.getSocketFactory();
		return new TreeSet<>(Arrays.asList(sf.getSupportedCipherSuites()));
	}
	
	/**
	 * Prints all 
	 */
	static void printSSLCipherSuites() {
		SSLContext ctx;
		try
		{
			ctx = SSLContext.getDefault();
		} catch (NoSuchAlgorithmException e)
		{
			throw new RuntimeException(e);
		}
		SSLSocketFactory sf = ctx.getSocketFactory();
		SortedSet<String> defaultCiphers = new TreeSet<>(Arrays.asList(sf.getDefaultCipherSuites()));
		SortedSet<String> supportedCiphers = new TreeSet<>(Arrays.asList(sf.getSupportedCipherSuites()));
		System.out.println("DEFAULT CIPHERS");
		for (String c : defaultCiphers) {
			System.out.println(c);
		}
		System.out.println("SUPPORTED CIPHERS");
		for (String c : supportedCiphers) {
			System.out.println(c);
		}
	}

	/**
	 * Installs a Trustmanager for TLS HttpsURLConnection and apache commons https clients that accepts all certificates 
	 * and does not validate server names.
	 * 
	 * DO NOT USE IN PRODUCTION.
	 */
	public static void disableCertificateValidation()
	{
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager()
		{
			public X509Certificate[] getAcceptedIssuers()
			{
				return new X509Certificate[0];
			}

			public void checkClientTrusted(X509Certificate[] certs,
					String authType)
			{}

			public void checkServerTrusted(X509Certificate[] certs,
					String authType)
			{}
		} };

		// Ignore differences between given hostname and certificate hostname
		HostnameVerifier hv = new HostnameVerifier()
		{
			@Override
			public boolean verify(String hostname, SSLSession session)
			{
				return true;
			}
		};

		// Install the all-trusting trust manager
		try
		{
			SSLContext ctx = SSLContext.getInstance("TLSv1.2");
			ctx.init(new KeyManager[0], trustAllCerts, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(ctx
					.getSocketFactory());
			HttpsURLConnection.setDefaultHostnameVerifier(hv);
			// see:http://stackoverflow.com/questions/1828775/how-to-handle-invalid-ssl-certificates-with-apache-httpclient
			// see:https://code.google.com/p/jsslutils/wiki/ApacheHttpClientUsage
			org.apache.commons.httpclient.protocol.Protocol
					.registerProtocol(
							"https",
							new org.apache.commons.httpclient.protocol.Protocol(
									"https",
									(ProtocolSocketFactory) new SslContextedSecureProtocolSocketFactory(
											ctx, false), 443));
			SSLContext.setDefault(ctx);
		}
		catch (Exception e)
		{
		}
	}
}

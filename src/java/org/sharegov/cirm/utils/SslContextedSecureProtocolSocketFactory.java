/*
 * ====================================================================
 * 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * ====================================================================
 * 
 * This software consists of voluntary contributions made by many individuals on
 * behalf of the Apache Software Foundation. For more information on the Apache
 * Software Foundation, please see <http://www.apache.org/>.
 * 
 * [Additional notices, if required by prior licensing conditions]
 * 
 * Alternatively, the contents of this file may be used under the terms of the
 * GNU Lesser General Public License Version 2 or later (the "LGPL"), in which
 * case the provisions of the LGPL are applicable instead of those above. See
 * terms of LGPL at <http://www.gnu.org/copyleft/lesser.txt>. If you wish to
 * allow use of your version of this file only under the terms of the LGPL and
 * not to allow others to use your version of this file under the Apache
 * Software License, indicate your decision by deleting the provisions above and
 * replace them with the notice and other provisions required by the LGPL. If
 * you do not delete the provisions above, a recipient may use your version of
 * this file under either the Apache Software License or the LGPL.
 */

package org.sharegov.cirm.utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.x500.X500Principal;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.SecureProtocolSocketFactory;

/**
 * This is a SecureProtocolSocketFactory for with the SSLContext can be
 * configured. It is based on Sebastian Hauer's StrictSSLProtocolSocketFactory,
 * available in the contribution directory of the Apache HTTP client library
 * 3.1. The main difference is that the SSLContext can be set, which means that
 * the use of client certificates or CRLs may be configured this way. The intent
 * was to use it in conjunction with <a
 * href="http://code.google.com/p/jsslutils/">jSSLutils</a>, but it is not a
 * dependency.
 * 
 * If no SSLContext is set up, the default SSLSocketFactory is used.
 * 
 * @author <a href="mailto:Bruno.Harbulot@manchester.ac.uk">Bruno Harbulot</a>
 * @author <a href="mailto:hauer@psicode.com">Sebastian Hauer</a>
 *         <p>
 *         DISCLAIMER: HttpClient developers DO NOT actively support this
 *         component. The component is provided as a reference material, which
 *         may be inappropriate for use without additional customization.
 *         </p>
 */

public class SslContextedSecureProtocolSocketFactory implements
                SecureProtocolSocketFactory {
        private SSLContext sslContext;

        /** Host name verify flag. */
        private boolean verifyHostname = true;

        /**
         * Constructor for SslContextedSecureProtocolSocketFactory.
         * 
         * @param sslContext
         *            The SSLContext to use for building the SSLSocketFactory. If
         *            this is null, then the default SSLSocketFactory is used.
         * @param verifyHostname
         *            The host name verification flag. If set to <code>true</code>
         *            the SSL sessions server host name will be compared to the host
         *            name returned in the server certificates "Common Name" field
         *            of the "SubjectDN" entry. If these names do not match a
         *            Exception is thrown to indicate this. Enabling host name
         *            verification will help to prevent from man-in-the-middle
         *            attacks. If set to <code>false</code> host name verification
         *            is turned off.
         * 
         * Code sample:
         * 
         * <blockquote> Protocol stricthttps = new Protocol( "https", new
         * SslContextedSecureProtocolSocketFactory(sslContext,true), 443);
         * 
         * HttpClient client = new HttpClient();
         * client.getHostConfiguration().setHost("localhost", 443, stricthttps);
         * </blockquote>
         * 
         */
        public SslContextedSecureProtocolSocketFactory(SSLContext sslContext,
                        boolean verifyHostname) {
                this.sslContext = sslContext;
                this.verifyHostname = verifyHostname;
        }

        /**
         * Constructor for SslContextedSecureProtocolSocketFactory. Host name
         * verification will be enabled by default.
         * 
         * @param sslContext
         *            The SSLContext to use for building the SSLSocketFactory. If
         *            this is null, then the default SSLSocketFactory is used.
         */
        public SslContextedSecureProtocolSocketFactory(SSLContext sslContext) {
                this(sslContext, true);
        }

        /**
         * Constructor for SslContextedSecureProtocolSocketFactory. The default
         * SSLSocketFactory will be used by default.
         * 
         * @param verifyHostname
         *            The host name verification flag. If set to <code>true</code>
         *            the SSL sessions server host name will be compared to the host
         *            name returned in the server certificates "Common Name" field
         *            of the "SubjectDN" entry. If these names do not match a
         *            Exception is thrown to indicate this. Enabling host name
         *            verification will help to prevent from man-in-the-middle
         *            attacks. If set to <code>false</code> host name verification
         *            is turned off.
         */
        public SslContextedSecureProtocolSocketFactory(boolean verifyHostname) {
                this(null, verifyHostname);
        }

        /**
         * Constructor for SslContextedSecureProtocolSocketFactory. By default, the
         * default SSLSocketFactory will be used and host name verification will be
         * enabled.
         */
        public SslContextedSecureProtocolSocketFactory() {
                this(null, true);
        }

        /**
         * Set the host name verification flag.
         * 
         * @param verifyHostname
         *            The host name verification flag. If set to <code>true</code>
         *            the SSL sessions server host name will be compared to the host
         *            name returned in the server certificates "Common Name" field
         *            of the "SubjectDN" entry. If these names do not match a
         *            Exception is thrown to indicate this. Enabling host name
         *            verification will help to prevent from man-in-the-middle
         *            attacks. If set to <code>false</code> host name verification
         *            is turned off.
         */
        public synchronized void setHostnameVerification(boolean verifyHostname) {
                this.verifyHostname = verifyHostname;
        }

        /**
         * Gets the status of the host name verification flag.
         * 
         * @return Host name verification flag. Either <code>true</code> if host
         *         name verification is turned on, or <code>false</code> if host
         *         name verification is turned off.
         */
        public synchronized boolean getHostnameVerification() {
                return verifyHostname;
        }

        /**
         * @see SecureProtocolSocketFactory#createSocket(java.lang.String,int,java.net.InetAddress,int)
         */
        public Socket createSocket(String host, int port, InetAddress clientHost,
                        int clientPort) throws IOException, UnknownHostException {
                SSLSocketFactory sf = (SSLSocketFactory) getSslSocketFactory();
                SSLSocket sslSocket = (SSLSocket) sf.createSocket(host, port,
                                clientHost, clientPort);
                verifyHostname(sslSocket);

                return sslSocket;
        }

        /**
         * Attempts to get a new socket connection to the given host within the
         * given time limit.
         * <p>
         * This method employs several techniques to circumvent the limitations of
         * older JREs that do not support connect timeout. When running in JRE 1.4
         * or above reflection is used to call Socket#connect(SocketAddress
         * endpoint, int timeout) method. When executing in older JREs a controller
         * thread is executed. The controller thread attempts to create a new socket
         * within the given limit of time. If socket constructor does not return
         * until the timeout expires, the controller terminates and throws an
         * {@link ConnectTimeoutException}
         * </p>
         * 
         * @param host
         *            the host name/IP
         * @param port
         *            the port on the host
         * @param clientHost
         *            the local host name/IP to bind the socket to
         * @param clientPort
         *            the port on the local machine
         * @param params
         *            {@link HttpConnectionParams Http connection parameters}
         * 
         * @return Socket a new socket
         * 
         * @throws IOException
         *             if an I/O error occurs while creating the socket
         * @throws UnknownHostException
         *             if the IP address of the host cannot be determined
         */
        public Socket createSocket(final String host, final int port,
                        final InetAddress localAddress, final int localPort,
                        final HttpConnectionParams params) throws IOException,
                        UnknownHostException, ConnectTimeoutException {
                if (params == null) {
                        throw new IllegalArgumentException("Parameters may not be null");
                }
                int timeout = params.getConnectionTimeout();
                Socket socket = null;

                SocketFactory socketfactory = getSslSocketFactory();
                if (timeout == 0) {
                        socket = socketfactory.createSocket(host, port, localAddress,
                                        localPort);
                } else {
                        socket = socketfactory.createSocket();
                        SocketAddress localaddr = new InetSocketAddress(localAddress,
                                        localPort);
                        SocketAddress remoteaddr = new InetSocketAddress(host, port);
                        socket.bind(localaddr);
                        socket.connect(remoteaddr, timeout);
                }
                verifyHostname((SSLSocket) socket);
                return socket;
        }

        /**
         * @see SecureProtocolSocketFactory#createSocket(java.lang.String,int)
         */
        public Socket createSocket(String host, int port) throws IOException,
                        UnknownHostException {
                SSLSocketFactory sf = (SSLSocketFactory) getSslSocketFactory();
                SSLSocket sslSocket = (SSLSocket) sf.createSocket(host, port);
                verifyHostname(sslSocket);

                return sslSocket;
        }

        /**
         * @see SecureProtocolSocketFactory#createSocket(java.net.Socket,java.lang.String,int,boolean)
         */
        public Socket createSocket(Socket socket, String host, int port,
                        boolean autoClose) throws IOException, UnknownHostException {
                SSLSocketFactory sf = (SSLSocketFactory) getSslSocketFactory();
                SSLSocket sslSocket = (SSLSocket) sf.createSocket(socket, host, port,
                                autoClose);
                verifyHostname(sslSocket);

                return sslSocket;
        }

        /**
         * Describe <code>verifyHostname</code> method here.
         * 
         * @param socket
         *            a <code>SSLSocket</code> value
         * @exception SSLPeerUnverifiedException
         *                If there are problems obtaining the server certificates
         *                from the SSL session, or the server host name does not
         *                match with the "Common Name" in the server certificates
         *                SubjectDN.
         * @exception UnknownHostException
         *                If we are not able to resolve the SSL sessions returned
         *                server host name.
         */
        private void verifyHostname(SSLSocket socket)
                        throws SSLPeerUnverifiedException, UnknownHostException {
                synchronized (this) {
                        if (!verifyHostname)
                                return;
                }

                SSLSession session = socket.getSession();
                String hostname = session.getPeerHost();
                try {
                        InetAddress.getByName(hostname);
                } catch (UnknownHostException uhe) {
                        throw new UnknownHostException("Could not resolve SSL sessions "
                                        + "server hostname: " + hostname);
                }

                X509Certificate[] certs = (X509Certificate[]) session
                                .getPeerCertificates();
                if (certs == null || certs.length == 0)
                        throw new SSLPeerUnverifiedException(
                                        "No server certificates found!");

                X500Principal subjectDN = certs[0].getSubjectX500Principal();

                // get the common names from the first cert
                List<String> cns = getCNs(subjectDN);
                boolean foundHostName = false;
                for (String cn : cns) {
                        if (hostname.equalsIgnoreCase(cn)) {
                                foundHostName = true;
                                break;
                        }
                }
                if (!foundHostName) {
                        throw new SSLPeerUnverifiedException(
                                        "HTTPS hostname invalid: expected '" + hostname
                                                        + "', received '" + cns + "'");
                }
        }

        /**
         * Parses a X.500 distinguished name for the values of the "Common Name"
         * fields. This is done a bit sloppy right now and should probably be done a
         * bit more according to <code>RFC 2253</code>.
         * 
         * @param subjectDN
         *            an X.500 Principal from an X.509 certificate.
         * @return the values of the "Common Name" fields.
         */
        private List<String> getCNs(X500Principal subjectDN) {
                List<String> cns = new ArrayList<String>();

                StringTokenizer st = new StringTokenizer(subjectDN.getName(), ",");
                while (st.hasMoreTokens()) {
                        String cnField = st.nextToken();
                        if (cnField.startsWith("CN=")) {
                                cns.add(cnField.substring(3));
                        }
                }
                return cns;
        }

        /**
         * Returns the SSLSocketFactory to use to create the sockets. If the
         * sslContext is non-null, this is built from the sslContext; otherwise,
         * this is the default SSLSocketFactory.
         * 
         * @return the SSLSocketFactory to use to create the sockets.
         */
        protected SSLSocketFactory getSslSocketFactory() {
                SSLSocketFactory sslSocketFactory = null;
                synchronized (this) {
                        if (this.sslContext != null) {
                                sslSocketFactory = this.sslContext.getSocketFactory();
                        }
                }
                if (sslSocketFactory == null) {
                        sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                }
                return sslSocketFactory;
        }

        /**
         * Sets the SSLContext to use.
         * 
         * @param sslContext
         *            SSLContext to use.
         */
        public synchronized void setSSLContext(SSLContext sslContext) {
                this.sslContext = sslContext;
        }
}
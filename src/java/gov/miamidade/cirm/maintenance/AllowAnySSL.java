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
package gov.miamidade.cirm.maintenance;

import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Installs a DefaultsSSL socketfactory that allows HTTPS SSL connections to untrusted servers.
 * DO NOT USE THIS IN PRODUCTION!
 * 
 * @author Thomas Hilpold
 *
 */
public class AllowAnySSL
{
	public void installPermissiveTrustmanager() {
		// Imports: javax.net.ssl.TrustManager, javax.net.ssl.X509TrustManager
		try {
		    // Create a trust manager that does not validate certificate chains
		    final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager()
			{
				
		        @Override
		        public void checkClientTrusted( final X509Certificate[] chain, final String authType ) {
		        }
		        @Override
		        public void checkServerTrusted( final X509Certificate[] chain, final String authType ) {
		        }
		        @Override
		        public X509Certificate[] getAcceptedIssuers() {
		            return null;
		        }
		    } };
		    
		    // Install the all-trusting trust manager
		    final SSLContext sslContext = SSLContext.getInstance( "SSL" );
		    sslContext.init( null, trustAllCerts, new java.security.SecureRandom() );
		    // Create an ssl socket factory with our all-trusting manager
		    final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();		    		    
			HttpsURLConnection.setDefaultSSLSocketFactory( sslSocketFactory );
		} catch ( final Exception e ) {
		    e.printStackTrace();
		}
	}
}

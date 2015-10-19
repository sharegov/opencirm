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

import java.io.IOException;
import java.net.URLDecoder;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.engine.application.EncodeRepresentation;
import org.restlet.engine.header.ContentType;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.representation.Representation;
import org.restlet.routing.Filter;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;


/**
 * Performs an additional decoding step for form encoded requests with double urlencoded form parameters.<br>
 *  <br>
 * Temporary solution for the problem that 311Hub client javascript software sometimes double encodes values for form parameters to overcome a
 * bug in a previous Restlet version.<br>
 * <br>
 * This class assumes that all client request form parameters are UTF-8 encoded.
 * <br>
 * This filter must be used AFTER gzip decoding completed on a request.
 * <br>
 * A counter totalDecodings of actual decodings is available to track progress while web client code adopts.<br>
 * Once all double encoded form params are modified to normal encoding, this filter should be removed.
 * <br>
 * @author Thomas Hilpold
 *
 */
public class FormParamAdditionalDecodeFilter extends Filter {

	public static boolean DBG = true; 
	
	public static final String DECODER_CHAR_SET = "UTF-8";
	
	private static final AtomicInteger totalDecodings = new AtomicInteger(0);
	
	public FormParamAdditionalDecodeFilter(Context context) {
		super(context);
	}

	/**
	 * Modifies the request entity iff it contains double encoded form parameter values. <br> 
	 * Requests without form parameters will be passed through efficiently. <br>
	 * No response modification occurs.<br>
	 */
	@Override
	protected int beforeHandle(Request request, Response response) {
		try {
			decodeFormParamsIfDoubleEncoded(request);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return CONTINUE;
	}
	
	/**
	 * Decodes form parameters that are sent double encoded by performing one decode step on their values, if
	 * their restlet framework decoded value starts with an "%".
	 * 
	 * @param request a restlet request
	 * @throws IOException did not occur during tests but may.
	 * @throws IllegalArgumentException if an Encode representation is received.
	 */
	void decodeFormParamsIfDoubleEncoded(Request request) throws IOException {
		Representation r = request.getEntity();
		if (r instanceof EncodeRepresentation) throw new IllegalArgumentException("Received an Encode representation."
				+ " This filter must be after the Encoder filter. please check your filter chain order.");
		if (!(r instanceof EmptyRepresentation)) {
    		ContentType c = new ContentType(r);
    		if (MediaType.APPLICATION_WWW_FORM.equals(c.getMediaType(), true)) {
    			Form form = new Form(r);
    			Form newform = new Form(r);
    			Map<String, String> valuesMap = form.getValuesMap();
    			for (Map.Entry<String, String> e : valuesMap.entrySet()) {
    				if (DBG) ThreadLocalStopwatch.now("" + e.getKey() + " - " + e.getValue());
    				String shouldBeDecodedValue = e.getValue();
    				if (shouldBeDecodedValue.startsWith("%")) {
    					shouldBeDecodedValue = URLDecoder.decode(e.getValue(), DECODER_CHAR_SET);
    					totalDecodings.incrementAndGet();
    					if (DBG) {
    						ThreadLocalStopwatch.now("DECODED " + totalDecodings.get() 
    							+ " : " + e.getKey() + " - " + shouldBeDecodedValue);
    					}
    				}
    				newform.add(e.getKey(), shouldBeDecodedValue);
    			}
    			//we must always set the entity, because above getEntitiy call causes 
    			//NPEs later if repeated by the framework.
    			request.setEntity(newform.encode(), c.getMediaType());
    		}
		}		
	}
	
	/**
	 * Returns the total count of actually needed decodings for double encoded form parameter values.
	 * After fixing the client software this counter will remain at zero and this filter class can be removed. 
	 * Static for convenience.
	 * 
	 * @return the total number of actual form value decodings, lower is better.
	 */
	public static int getTotaDecodings() {
		return totalDecodings.get();
	}
}
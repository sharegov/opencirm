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

import javax.ws.rs.Path;

import mjson.Json;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.restlet.ext.fileupload.RestletFileUpload;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.ConfigSet;
import org.sharegov.cirm.OWL;


@Path("upload")
public class UploadToCloudServerResource extends ServerResource
{

	/**
	 * Gets the upload service URL from the Configset.
	 * 
	 * @return a full url or null if not configured.
	 */
	public synchronized String getUploadServiceUrl()
	{
		OWLNamedIndividual ind = ConfigSet.getInstance().get("UploadConfig");
		return ind == null ? null : OWL.dataProperty(ind, "hasUrl").getLiteral(); 
	}

	/***
	 * Uploads the given file AWS S3
	 * @param entity
	 * @return
	 */
	@Post 
	public Representation upload(Representation entity){
		ThreadLocalStopwatch.startTop("START UPLOAD");
		String s3URl = getUploadServiceUrl() + "upload64encoded?prefix=cirm_";
		Representation resultRep = null;
		 
		if(entity == null) //?? && org.restlet.data.MediaType.MULTIPART_FORM_DATA.equals(entity.getMediaType(), true) == false)
		{
			resultRep = new StringRepresentation(GenUtils.ko("Unable to upload, bad request. Entity was null").toString(),
		        		org.restlet.data.MediaType.TEXT_HTML);
		 	ThreadLocalStopwatch.fail("FAIL UPLOAD Entity was null");
			return resultRep;
		}
		
		try
		{
			 RestletFileUpload upload = new RestletFileUpload();
             FileItemIterator fit = upload.getItemIterator(entity);
             while(fit.hasNext()) 
             {
         		//Will only read first uploadAttachment and then break
             	FileItemStream stream = fit.next();
             	if(stream.getFieldName().equals("uploadAttachment"))
             	{
             		HttpClient client;
            		PostMethod post = new PostMethod(s3URl);
             		Json resultJson = Json.object();
               		String contentType, name;
               		
             		contentType = stream.getContentType();
             		String streamName = stream.getName();
             		name = formatStreamName(stream.getName());
             		ThreadLocalStopwatch.now("StreamName " + streamName + " > " + name + " Type: " + contentType);
             		
             		//String extn = contentType.substring(contentType.indexOf("/")+1);
             		byte[] file = GenUtils.getBytesFromStream(stream.openStream(), true);
             		String encoded = Base64.encode(file, false);
            		Json request = Json.object();
             		request.set("data", encoded);
             		request.set("contentType", contentType);
             		request.set("name", name);
             		StringRequestEntity requestEntity = new StringRequestEntity(
    					    request.toString(),
    					    "application/json",
    					    "UTF-8");
             		client = new HttpClient();
             		post.setRequestEntity(requestEntity);
             		int statusCode = client.executeMethod(post);
             		
             		if (statusCode != HttpStatus.SC_OK)
             		{
             			resultJson.set("ok", false);
             		}
             		else 
             		{
             			String responseString = post.getResponseBodyAsString();
                		Json result; 
             			result = Json.read(responseString);
             			resultJson.set("ok", true);
             			resultJson.set("key", result.at("value").at("key").asString());
             			resultJson.set("url", result.at("value").at("url").asString());
             			
             		}
             		resultRep = new StringRepresentation(resultJson.toString(), (org.restlet.data.MediaType)org.restlet.data.MediaType.TEXT_HTML);
             		ThreadLocalStopwatch.stop("END UPLOAD " + resultJson);
             		//exit loop after first uploadAttachment
             		break;             		
             	}
             } //while
             if (resultRep == null) {
     			resultRep = new StringRepresentation(GenUtils.ko("Unable to upload, bad request. No uploadAttachment").toString(),
		        		org.restlet.data.MediaType.TEXT_HTML);
     			ThreadLocalStopwatch.fail("FAIL UPLOAD No uploadAttachment");
             }
		}
		 catch (Exception e) {
			 	ThreadLocalStopwatch.fail("FAIL UPLOAD with " + e);
			 	e.printStackTrace();
			 	resultRep =  new StringRepresentation(GenUtils.ko("Unable to upload, bad request. " + e.toString()).toString(),
		        		org.restlet.data.MediaType.TEXT_HTML);
		}		
		return resultRep;
	}
	
	/**
	 * Converts stream name from client into desirable file name, removing path, and replacing certain chars.
	 * @param streamName
	 * @return
	 */
	private String formatStreamName(String streamName) {
		//Find filename for the cases where path is included in stream name:
 		int startFilenameIdx  = Math.max(streamName.lastIndexOf('/'), streamName.lastIndexOf('\\'));
 		if (startFilenameIdx >= 0) {  
 			streamName = streamName.substring(startFilenameIdx + 1);             			
 		}
 		//name is now filename only without possible path.
 		streamName = streamName.replaceAll("\\s+","");
 		streamName = streamName.replaceAll("_","");
 		streamName = streamName.replaceAll("-","");
 		streamName = streamName.replaceAll("\\.","-");
 		streamName = streamName + "-";
 		return streamName;
	}
	
}

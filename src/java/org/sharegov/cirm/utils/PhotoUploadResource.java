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
public class PhotoUploadResource extends ServerResource
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
		
		//String s3URl = getUploadServiceUrl() + "upload64encoded?prefix=cirm_";
		String s3URl = "http://10.9.25.144:6060/s3/upload64encoded?prefix=cirm_";
		Json json = Json.object();
		Json request = Json.object();
		Json result; 
		RestletFileUpload upload = new RestletFileUpload();
		
		Representation rep =  new StringRepresentation(GenUtils.ko("Unable to upload, bad request.").toString(),
	        		org.restlet.data.MediaType.TEXT_HTML);
		 
		
		
		if(entity == null && org.restlet.data.MediaType.MULTIPART_FORM_DATA.equals(entity.getMediaType(),
                true) == false)
		{
			return rep;
		}
		
	
		HttpClient client;
		PostMethod post = new PostMethod(s3URl);
		
		String contentType, type, name;
		String [] tokens; 
		
		try
			{
             FileItemIterator fit = upload.getItemIterator(entity);
             while(fit.hasNext()) 
             {
             	FileItemStream stream = fit.next();
             	if(stream.getFieldName().equals("uploadAttachment"))
             	{
             		type = "";
             		contentType = stream.getContentType();
             		name = stream.getName();
             		name = name.replaceAll("\\s+","");
             		tokens = name.split("\\.");
             		name = "";
             		
             		for(String token : tokens)
             		{
             			name = name +token + "-"; 
             		}
             		
             		
             		String extn = contentType.substring(contentType.indexOf("/")+1);
             		byte[] file = GenUtils.getBytesFromStream(stream.openStream(), true);
             		Base64 base64 = new Base64();
             		String encoded = base64.encode(file,false);
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
             			json.set("ok", false);
             		}
             		else 
             		{
             			String responseString = post.getResponseBodyAsString();
             			result = Json.read(responseString);
             			json.set("ok", true);
             			json.set("key", result.at("value").at("key").asString());
             			json.set("url", result.at("value").at("url").asString());
             			
             		}
             		rep = new StringRepresentation(json.toString(), (org.restlet.data.MediaType)org.restlet.data.MediaType.TEXT_HTML);
             		break;
             		
             	}
             }
			}
		 catch (Exception e) {
         	e.printStackTrace();
			}
		
		
		
		 
		 return rep;
	}
	
	}

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

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.ws.rs.Path;

import mjson.Json;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.restlet.data.Status;
import org.restlet.ext.fileupload.RestletFileUpload;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.StartUp;
//import org.restlet.data.MediaType;

@Path("upload")

public class PhotoUploadResource extends ServerResource
{
    /**
     * Accepts and processes a representation posted to the resource. As
     * response, the content of the uploaded file is sent back the client.
     */
    final String s3Url = "http://localhost:6060/s3/upload64encoded";
	
	@Post 
	//@Path("/s3")
	public Representation upload(Representation entity){
		Json json = Json.object();
		Json request = Json.object();
		Json result; 
		RestletFileUpload upload = new RestletFileUpload();
		System.out.println("got here yoyoyoyoyoy");
		Representation rep =  new StringRepresentation(GenUtils.ko("Unable to upload, bad request.").toString(),
	        		org.restlet.data.MediaType.TEXT_HTML);
		 
		
		
		if(entity == null && org.restlet.data.MediaType.MULTIPART_FORM_DATA.equals(entity.getMediaType(),
                true) == false)
		{
			return rep;
		}
		
	
		HttpClient client;
		PostMethod post = new PostMethod(s3Url);
		 try
			{
             FileItemIterator fit = upload.getItemIterator(entity);
             while(fit.hasNext()) 
             {
             	FileItemStream stream = fit.next();
             	if(stream.getFieldName().equals("uploadImage"))
             	{
             		String contentType = stream.getContentType();
             		String extn = contentType.substring(contentType.indexOf("/")+1);
             		byte[] file = GenUtils.getBytesFromStream(stream.openStream(), true);
             		Base64 base64 = new Base64();
             		String encoded = base64.encode(file,false);
             		request.set("data", encoded);
             		request.set("contentType", contentType);
             		
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
             			
             		}
             		
             		break;
             	}
             }
			}
		 catch (Exception e) {
         	e.printStackTrace();
			}
		
		
		
		 rep = new StringRepresentation(json.toString(), (org.restlet.data.MediaType)org.restlet.data.MediaType.TEXT_HTML);
		 return rep;
	}
	
	//@Post
    public Representation accept(Representation entity)
    {
    	// NOTE: the return media type here is TEXT_HTML because IE opens up a file download box
    	// if it's APPLICATION_JSON as we'd want it.
    	
        Representation rep =  new StringRepresentation(GenUtils.ko("Unable to upload, bad request.").toString(),
        		org.restlet.data.MediaType.TEXT_HTML);
        if (entity != null)
        {
            if (org.restlet.data.MediaType.MULTIPART_FORM_DATA.equals(entity.getMediaType(),
                                                     true))
            {

                // 1/ Create a factory for disk-based file items
//                DiskFileItemFactory factory = new DiskFileItemFactory();
//                factory.setSizeThreshold(1000240);

                // 2/ Create a new file upload handler based on the Restlet
                // FileUpload extension that will parse Restlet requests and
                // generates FileItems.
                RestletFileUpload upload = new RestletFileUpload();
//                List<FileItem> items;
//
//                // 3/ Request is parsed by the handler which generates a
//                // list of FileItems

                InputStream is = null; 
                Graphics2D g = null;
                FileOutputStream fos = null;
                ByteArrayOutputStream baos = null;

                try
				{
	                FileItemIterator fit = upload.getItemIterator(entity);
	                while(fit.hasNext()) 
	                {
	                	FileItemStream stream = fit.next();
	                	if(stream.getFieldName().equals("uploadImage"))
	                	{
	                		String contentType = stream.getContentType();
	                		
	                		if(contentType.startsWith("image"))
	                		{
		                		String extn = contentType.substring(contentType.indexOf("/")+1);
		                		byte [] data = GenUtils.getBytesFromStream(stream.openStream(), true);
						        String filename = "image_" + Refs.idFactory.resolve().newId(null) + "."+extn;
						        //String filename = "srphoto_123" + "."+extn; // + OWLRefs.idFactory.resolve().newId("Photo"); // may add Photo class to ontology
						        File f = new File(StartUp.config.at("workingDir").asString() + "/src/uploaded", filename);

						        StringBuilder sb = new StringBuilder("");
						        
								String hostname = java.net.InetAddress.getLocalHost().getHostName();
								boolean ssl = StartUp.config.is("ssl", true); 
								int port = ssl ? StartUp.config.at("ssl-port").asInteger() 
											   : StartUp.config.at("port").asInteger();
								if(ssl)
									sb.append("https://");
								else
									sb.append("http://");
								sb.append(hostname);
								if((ssl && port != 443) || (!ssl && port != 80))
									sb.append(":").append(port);
						        sb.append("/uploaded/");
						        sb.append(filename);

						        //Start : resize Image
						        int rw = 400;
						        int rh = 300;
						        is = new ByteArrayInputStream(data);
						        BufferedImage image = ImageIO.read(is);
						        int w = image.getWidth();
						        int h = image.getHeight();

						        if(w > rw)
						        {
							        BufferedImage bi = new BufferedImage(rw, rh, image.getType());
							        g = bi.createGraphics();
							        g.drawImage(image, 0, 0, rw, rh, null);
							        baos = new ByteArrayOutputStream();
							        ImageIO.write(bi, extn, baos);
							        data = baos.toByteArray();
						        }
						        //End: resize Image
						        
						        fos = new FileOutputStream(f);
						        fos.write(data);

						        Json j = GenUtils.ok().set("image", sb.toString());
						        //Json j = GenUtils.ok().set("image", filename);
						        rep = new StringRepresentation(j.toString(), (org.restlet.data.MediaType)org.restlet.data.MediaType.TEXT_HTML);
	                		}
	                		else {
	                			rep = new StringRepresentation(GenUtils.ko("Please upload only Images.").toString(),
	                					org.restlet.data.MediaType.TEXT_HTML);
	                		}
	                	}
	                }
				}
                catch (Exception e) {
                	e.printStackTrace();
				}
                
		        finally {
		        	if(fos != null) {
		        		try {
							fos.flush();
			        		fos.close();
						}
						catch (IOException e) { }
		        	}
		        	if(is != null) {
		        		try {
		        			is.close();
						}
						catch (IOException e) { }
		        	}
		        	if(baos != null) {
				        try
						{
					        baos.flush();
							baos.close();
						}
						catch (IOException e) {}
		        	}
		        	if(g != null) {
				        g.dispose();
		        	}
		        }
            }
        }
        else
        {
            // POST request with no entity.
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
        }
        return rep;
    }
}

package org.sharegov.cirm.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import javax.ws.rs.Path;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.restlet.data.MediaType;
import org.restlet.ext.fileupload.RestletFileUpload;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.sharegov.cirm.StartUp;

import mjson.Json;

/**
 * Upload attachment in dev environment, bypassing S3 storage. Files will
 * be uploaded under the working directory, under 'uploaded' top-level folder, so
 * that they are accessible via a "/uploaded/filename" URL.
 * 
 * @author borislav
 *
 */
@Path("upload")
public class UploadToWorkingDirectory extends ServerResource
{
	private File ensureFreshName(File parent, String nameCandidate, String ext) 
	{
		String name =  nameCandidate;
		File outfile = new File(parent, name + (ext == null ? "" : "." + ext));
		for (int i = 1; outfile.exists(); i++) {
			name = name + "_" + i;
			outfile = new File(parent, name + (ext == null ? "" : "." + ext));
		}
		return outfile;
	}
	
	private void saveTo(FileItemStream stream, File outfile) throws Exception
	{		
		try (FileOutputStream out = new FileOutputStream(outfile); 
			 InputStream in = stream.openStream()) 
		{
			byte [] buf = new byte[4096];
			for (int cnt = in.read(buf); cnt > 0; cnt = in.read(buf))
				out.write(buf, 0, cnt);
		}		
	}
	
	/***
	 * Uploads the local 'upload' directory: use this implementation for 
	 * development or demos. A production implementation, in a clustered
	 * environment should use a real distributed file service.
	 */
	@Post
	public Representation upload(Representation entity)
	{
		if (entity == null && 
				org.restlet.data.MediaType.MULTIPART_FORM_DATA.equals(entity.getMediaType(), true) == false)
			return new StringRepresentation(
						GenUtils.ko("Unable to upload, bad request.").toString(),
						MediaType.TEXT_HTML);

		File location = new File(StartUp.config.at("workingDir").asString() + "/src/uploaded"); 
		RestletFileUpload upload = new RestletFileUpload();

		try
		{
			FileItemIterator fit = upload.getItemIterator(entity);
			while (fit.hasNext())
			{
				FileItemStream stream = fit.next();
				if (stream.getFieldName().equals("uploadAttachment"))
				{
					String contentType = stream.getContentType();
					String name = stream.getName();
					String extn = null; 					
					int dotpos = name.lastIndexOf(".");
					if (dotpos > 0) 
					{
						extn = name.substring(dotpos + 1);						
						name = name.substring(0,  dotpos);
					}
					else
						contentType.substring(contentType.indexOf("/")+1);					
					name = name.replaceAll("\\s+", "");
					File outfile = null;
					synchronized (this.getClass())
					{
						outfile = ensureFreshName(location, name, extn);
						saveTo(stream, outfile);
					}
					Json result = Json.object("ok", true, 
											  "key", "", 
											  "url", "/uploaded/" + outfile.getName());
					return new StringRepresentation(result.toString(), MediaType.TEXT_HTML);
				}
				else 
					continue;
			}
			return new StringRepresentation(GenUtils.ko("No attachments.").toString(), 
											MediaType.TEXT_HTML);
		}
		catch (Exception e)
		{			
			e.printStackTrace();
			return new StringRepresentation(
					GenUtils.ko("Upload failed: " + e.toString()).toString(), 
					MediaType.TEXT_HTML);
		}
	}

}

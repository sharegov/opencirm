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

import java.util.List;

import mjson.Json;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.CirmTransactionEvent;
import org.sharegov.cirm.CirmTransactionListener;
import org.sharegov.cirm.ConfigSet;
import org.sharegov.cirm.OWL;

/**
 * Transaction listener that removes attachments (files or images) after a transaction success from AWS S3 (Also accepts but ignores legacy paths).<br>
 * Legacy file based attachments are detected, will appear to the user to be successfully deleted, however, we don't actually delete the file from the server,
 * but log a warning message instead. <br>
 * <br>
 * Suggested usage:<br>
 * Create and add one object of this class if and only if hasRemovedAttachment json property coming in a service request json is a non empty array.<br>
 * <br>
 * @author David Wong, Thomas Hilpold
 *
 */
public class RemoveAttachmentsOnTxSuccessListener implements CirmTransactionListener
{

	final List<Json> hasRemovedAttachmentList;
	
	public RemoveAttachmentsOnTxSuccessListener(final List<Json> hasRemovedAttachmentList) {
		if (hasRemovedAttachmentList == null) throw new IllegalArgumentException("hasRemovedAttachmentList was null");
		this.hasRemovedAttachmentList = hasRemovedAttachmentList;
	}

	
	@Override
	public void transactionStateChanged(CirmTransactionEvent e)
	{
		if (e.isSucceeded()) {
			if (!hasRemovedAttachmentList.isEmpty())	{
				ThreadLocalStopwatch.now("START RemoveAttachmentsOnTxSuccessListener removing " + hasRemovedAttachmentList.size() + " attachments");
				ThreadLocalStopwatch.getWatch().time("RemoveAttachmentsOnTxSuccessListener removing " + hasRemovedAttachmentList.size());
				try {
					deleteAttachments();
				} catch (Exception ex) {
					ThreadLocalStopwatch.error("Error: RemoveAttachmentsOnTxSuccessListener failed while deleting attachments. Stack trace to follow.");
					ex.printStackTrace();
				}
				ThreadLocalStopwatch.now("END RemoveAttachmentsOnTxSuccessListener removing " + hasRemovedAttachmentList.size() + " attachments");
			} 
		}
	}
	
	/**
	 * Deletes all images given by hasRemovedAttachmentList property from ASW, but also accepts legacy File paths.
	 */
	void deleteAttachments() {
		String file; 
		
		for (Json attachment : hasRemovedAttachmentList) {
			file = attachment.toString();
			if(file.contains("aws") && file.contains("s3")) {
				deleteFromAWS(file); 
			} else {
				deleteFromFile(file);
			}
		}
	}
	
	/**
	 * Should delete a file from a location on disc, however this is not implemented.
	 * We print a warning message instead.
	 * 
	 * @param filepath a local or remote windows file path
	 */
	void deleteFromFile(String filepath) {
		ThreadLocalStopwatch.error("WARNING: RemoveAttachmentsOnTxSuccessListener: Deleting legacy image not implemented, but attempted with: " + filepath + " ");
//		File newF = new File(filepath);
//		boolean deleteStatus = newF.delete();
//		if (deleteStatus == false) {
//			throw new RuntimeException("Unable to delete file '"
//					+ filepath.toString() + "' from Images folder.");
//		}
	}
	
	/**
	 * Deletes a file using AWS S3 middleware services as specified in config property UploadConfig.
	 * 
	 * @param fileLocationUrl an AWS S3 location
	 */
	void deleteFromAWS(String fileLocationUrl) {
		
		OWLNamedIndividual ind = ConfigSet.getInstance().get("UploadConfig");
		String deleteServiceUrl = OWL.dataProperty(ind, "hasUrl").getLiteral();   
		deleteServiceUrl = deleteServiceUrl + "delete" + "/";
		String deleteFileUrl;
		try {
    		String[] tokens = fileLocationUrl.split("/");
    		String id = tokens[4]; 
    		id = id.replace("\"", "");
    		deleteFileUrl = deleteServiceUrl  + id;
    		HttpClient client = new HttpClient();
    		DeleteMethod delete = new DeleteMethod(deleteFileUrl);
			int statusCode = client.executeMethod(delete);
			if (statusCode != HttpStatus.SC_OK)	{
				throw new RuntimeException("Http response was not SC_OK, but: " + statusCode);
     		}
		} catch(Exception ex){
			throw new RuntimeException("Unable to delete file '"
					+ fileLocationUrl.toString() + "' from aws S3.", ex);
		}			
	}
}

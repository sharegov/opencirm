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

import java.io.File;
import java.net.URL;
import java.util.Collections;
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
import org.sharegov.cirm.legacy.CirmMessage;
import org.sharegov.cirm.legacy.MessageManager;

/**
 * Transaction listener adapter that uses the MessageManager to send one or more emails on success of the toplevel transaction.
 * 
 * If a list is provided during construction, a reference to the life messages list is held, so it can be modified externally before execution.
 * Suggested usage:
 * Create and add one object of this class for every message created in the code where it is created.
 * (To avoid Lists of CirmMessages in many signatures)
 * 
 * @author Thomas Hilpold
 *
 */
public class RemoveImagesOnTxSuccessListener implements CirmTransactionListener
{

	final List<Json> hasRemovedImageList;
	
	public RemoveImagesOnTxSuccessListener(final List<Json> hasRemovedImageList) 
	{
		if (hasRemovedImageList == null) throw new IllegalArgumentException("hasRemovedImages was null");
		this.hasRemovedImageList = hasRemovedImageList;
	}

	
	@Override
	public void transactionStateChanged(CirmTransactionEvent e)
	{
		if (e.isSucceeded()) 
		{
			if (!hasRemovedImageList.isEmpty()) {
				ThreadLocalStopwatch.getWatch().time("RemoveImagesOnTxSuccessListener removing " + hasRemovedImageList.size());
				deleteImages();
			} 
		}
	}
	
	void deleteImages() {
		String file; 
		
		for (Json image : hasRemovedImageList)
		{
			
			file = image.toString();
			
			if(file.contains("aws") && file.contains("s3"))
				deleteFromAWS(file); 
			else
				deleteFromFile(file);
				
				
		}
	}
	
	void deleteFromFile(String filepath) {
		
		File newF = new File(filepath);
		boolean deleteStatus = newF.delete();
		if (deleteStatus == false)
			throw new RuntimeException("Unable to delete file '"
					+ filepath.toString() + "' from Images folder.");
		
	}
	
	void deleteFromAWS(String fileLocationUrl) {
		
		OWLNamedIndividual ind = ConfigSet.getInstance().get("UploadConfig");
		String url = OWL.dataProperty(ind, "hasUrl").getLiteral();   
		
		
		String[] tokens = fileLocationUrl.split("/");
		   
		String id = tokens[4]; 
		id = id.replace("\"", "");
		url = url + "delete" + "/" + id;
		HttpClient client = new HttpClient();
		DeleteMethod delete = new DeleteMethod(url);
		
		try {
			int statusCode = client.executeMethod(delete);
			
			if (statusCode != HttpStatus.SC_OK)
     		{
				throw new RuntimeException("Unable to delete file '"
						+ fileLocationUrl.toString() + "' aws S3");
     		}
     		
			
		}catch(Exception ex){
			throw new RuntimeException("Unable to delete file '"
					+ fileLocationUrl.toString() + "' aws S3");
		}			
		
	}
	
}

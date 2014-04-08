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

import static org.sharegov.cirm.OWL.fullIri;
import static org.sharegov.cirm.rest.OperationService.getPersister;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.rdb.RelationalStore;
import org.sharegov.cirm.rdb.RelationalStoreExt;
import org.sharegov.cirm.rdb.RelationalStoreImpl;
import org.sharegov.cirm.rdb.ThreadLocalConnection;

public class PhoneNumberSave
{

	
	public static final String DEFAULT_PHONE_DBCOLUMN = "SR_ACTOR_PHONE_NUMBER";
	public static Map<String,String> phoneTypeToDBColumn = new HashMap<String, String>()
			{
					{
						this.put("311CALLB", "SR_ACTOR_PHONE_NUMBER");
						this.put("ALT", "SR_ACTOR_PHONE_NUMBER");
						this.put("BEEP", "SR_ACTOR_PHONE_NUMBER");
						this.put("BUSINES2", "SR_ACTOR_WORK_PHONE_NO");
						this.put("BUSINESS", "SR_ACTOR_WORK_PHONE_NO");
						this.put("CELL", "SR_ACTOR_CELL_PHONE_NO");
						this.put("EMAIL", "SR_ACTOR_CELL_PHONE_NO");
						this.put("FAX", "SR_ACTOR_FAX_PHONE_NO");
						this.put("FAX2", "SR_ACTOR_FAX_PHONE_NO");
						this.put("HOME", "SR_ACTOR_PHONE_NUMBER");
						this.put("MOB", "SR_ACTOR_CELL_PHONE_NO");
						this.put("NONE", "SR_ACTOR_PHONE_NUMBER");
						this.put("PAGERPHO", "SR_ACTOR_CELL_PHONE_NO");
						this.put("WORK", "SR_ACTOR_WORK_PHONE_NO");
						this.put("WORKD", "SR_ACTOR_WORK_PHONE_NO");
						this.put("WORKTOLL", "SR_ACTOR_WORK_PHONE_NO");
						
					}
			};
	
	public void saveCSRPhoneNumberInCirm(String caseNumber, String actorType, String name, String fname, String phoneType, String phone, String ext)
	{
		
		if(phone.length() < 10)
			return;
		RelationalStoreImpl.TRANSACTION_ISOLATION_LEVEL = Connection.TRANSACTION_READ_COMMITTED;
		RelationalStoreExt store =	getPersister().getStoreExt();
		StringBuilder selectActorId = new StringBuilder();
		String dbPhoneColumn = phoneTypeToDBColumn.get(phoneType);
		selectActorId.append("select a.SR_ACTOR_ID, a."+dbPhoneColumn+" from CIRM_SR_ACTOR a, CIRM_SRREQ_SRACTOR b, CIRM_SR_REQUESTS c  WHERE  a.SR_ACTOR_ID = b.SR_ACTOR_ID and b.SR_REQUEST_ID = c.SR_REQUEST_ID and c.CASE_NUMBER = ? and a.SR_ACTOR_TYPE = ?");
		String nameParam = "";
		if ( fname.length() == 0)
        {
			if(name.length() > 0)
			{
				selectActorId.append(" AND a.SR_ACTOR_NAME = ?  ");
				nameParam = name;
			}
			else
				selectActorId.append(" AND a.SR_ACTOR_NAME IS NULL ");
			
        } else
        {
			if(name.length() > 0)
			{	
				selectActorId.append(" AND a.SR_ACTOR_LNAME = ?  ");
				nameParam = name; 
			}
			else
				selectActorId.append(" AND a.SR_ACTOR_LNAME IS NULL ");
        }
		
	
		if(dbPhoneColumn == null)
			dbPhoneColumn = DEFAULT_PHONE_DBCOLUMN;
		String phoneValue = phone;
		if(ext != null && ext.length() > 1)
			phoneValue = phoneValue + "#" +  ext.replaceAll("[^\\d.]", "");
		ThreadLocalConnection conn = store.getConnection();
		PreparedStatement selectActorStatement = null;
		PreparedStatement updateActorStatement = null;
		try
		{
			selectActorStatement = conn.prepareStatement(selectActorId.toString());
			selectActorStatement.setString(1, caseNumber);
			selectActorStatement.setString(2, actorType);
			if(nameParam.length() > 0)
			{
				selectActorStatement.setString(3,nameParam);
			}
			
			
			ResultSet rs = selectActorStatement.executeQuery();
			while(rs.next())
			{
				//System.out.println("Updating actor (" + actorType + ") name = " + nameParam + " of actorid=" + rs.getString("SR_ACTOR_ID") +" in case " + caseNumber +" with " + dbPhoneColumn+ "=" + phoneValue +"...");
				String actorId = rs.getString("SR_ACTOR_ID");
				String existingPhone = rs.getString(dbPhoneColumn);
				String phoneString = "";
				if(existingPhone != null)
				{
					if(existingPhone.contains(phoneValue))
						continue;
					phoneString = existingPhone + "," + phoneValue;
				}else
				{
					phoneString = phoneValue;
				}
				StringBuilder updateActorPhone = new StringBuilder(); 
				updateActorPhone.append("update CIRM_SR_ACTOR set " + dbPhoneColumn + " = '"+ phoneString+"' where SR_ACTOR_ID = " +actorId);
				System.out.println(updateActorPhone.toString());
				updateActorStatement = conn.prepareStatement(updateActorPhone.toString());
				int updateCount = updateActorStatement.executeUpdate();
				conn.commit();
				System.out.println(updateCount + " Loaded Phone for case: " + caseNumber);
				
//				classificationStatement.setLong(1, id);
//				classificationStatement.setLong(2, entitiesAndIds.get(streetAddress));
//				classificationStatement.executeUpdate();
//				conn.commit();
			}
			//conn.commit();
		} catch(SQLException e)
		{
			throw new RuntimeException(e);
		}
		finally
		{
		
			if (selectActorStatement != null)
				try { selectActorStatement.close(); } catch (Throwable t) { } 	
			if (updateActorStatement != null)
				try { updateActorStatement.close(); } catch (Throwable t) { } 
			if (conn != null)
				try { conn.close(); } catch (Throwable t) { } 
		}
		
	}
	
	
	public void loadAndSavePhoneNumbers()
	{
		/**
         * Source file to read data from.
         */
        String phoneNumbersFile = "C:/Work/cirmservices_log/actor_phones_appended_unfinished.csv";
        
        try
        {
        	BufferedReader bReader = new BufferedReader(
                new FileReader(phoneNumbersFile));
 
        	String line;
        	String currentCaseNumber = "";
        	while ((line = bReader.readLine()) != null)
        	{
 
	            String values[] = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
	            String caseNumber = removeQuote(values[0]);
	            String actorType = removeQuote(values[1]);
	            String name1 = removeQuote(values[2]);
	            String name2 = removeQuote(values[3]);
	            String name3 = removeQuote(values[4]);
	            String phoneNum = removeQuote(values[5]);
	            String phoneExt = removeQuote(values[6]);
	            String fax = removeQuote(values[7]);
	            String phoneType = removeQuote(values[8]);
	            String typedPhone = removeQuote(values[9]);
	            String typedPhoneExt = removeQuote(values[10]);
	            if(currentCaseNumber != caseNumber)
	            {
	            	//load only once
	            	if(phoneNum.trim().length() > 1)
	            		saveCSRPhoneNumberInCirm(caseNumber, actorType, name1 , name2,  "HOME",removeDash(phoneNum), phoneExt);
	            	if(fax.trim().length() > 1)
	            		saveCSRPhoneNumberInCirm(caseNumber, actorType, name1, name2 , "FAX", removeDash(fax), "");
	            }
	            if(!phoneNum.equals(typedPhone))
	            {
	            	saveCSRPhoneNumberInCirm(caseNumber, actorType, name1 , name2, phoneType, removeDash(typedPhone), typedPhoneExt);
	            }
	            currentCaseNumber = caseNumber;
        	}
        bReader.close();
        }catch(IOException e)
        {
        	throw new RuntimeException(e);
        }
	}
	
	public String removeQuote(String s)
	{
		return s.replaceAll("\"", "");
	}
	
	public String removeDash(String s)
	{
		return s.replaceAll("-", "");
	}
	
	public static void main(String[] args)
	{
		PhoneNumberSave phoneSave = new PhoneNumberSave();
		phoneSave.loadAndSavePhoneNumbers();
	}
}

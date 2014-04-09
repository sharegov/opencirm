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
package org.sharegov.cirm.rdb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

public class BOChangeListener implements RDBListener
{

	public static final String CHANGE_TABLE = "CIRM_BO_CHANGE";
	
	public static boolean DBG = true;
	
	@Override
	public void saveExecuting(RDBEvent e)
	{
		if(DBG)
			ThreadLocalStopwatch.getWatch().time("BOChangeListener.saveExecuting() START");
		
		OWLOntologyID ontologyId = e.getOntologyID();
		if(ontologyId == null || ontologyId.isAnonymous())
		{
			ThreadLocalStopwatch.getWatch().time("BOChangeListener.saveExecuting() ontologyId is null or anonymous.");
			return;
		}
		IRI boIRI = ontologyId.getOntologyIRI().resolve("#bo");
		long id = OWL.parseIDFromBusinessOntologyIRI(boIRI);
		insertChangeTxn(id);
		if(DBG)
			ThreadLocalStopwatch.getWatch().time("BOChangeListener.saveExecuting() END");
	}

	@Override
	public void loadExecuting(RDBEvent e)
	{
		// TODO Auto-generated method stub

	}
	
	private void insertChange(Long boid)
	{
		Connection conn = null;
		PreparedStatement pstmt = null;
		StringBuilder sql = new StringBuilder("INSERT INTO ")
				.append(CHANGE_TABLE)
				.append(" (BO_ID, CHANGE_DATE) ").append(" VALUES (?,?)");
		RelationalStoreExt store = Refs.defaultRelationalStoreExt.resolve();
		try
		{
			conn = store.getConnection();
			pstmt = conn.prepareStatement(sql.toString());
			pstmt.setLong(1,boid);
			pstmt.setTimestamp(2,(Timestamp)store.getStoreTime());
			pstmt.execute();
		}catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
		finally
		{
			store.close(pstmt, conn);
		}	
	}
	
	public Long insertChangeTxn(final Long boid)
	{
		return Refs.defaultRelationalStore.resolve().txn(new CirmTransaction<Long>() {
			public Long call()
			{
				insertChange(boid);
				return boid;
			}
		});
	}

}

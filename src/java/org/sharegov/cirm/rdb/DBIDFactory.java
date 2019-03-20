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

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.utils.CIRMIDFactory;

public class DBIDFactory implements CIRMIDFactory
{
	private IRI connectionInfo = null;
	
	public IRI getConnectionInfo()
	{
		if (connectionInfo == null)
		{
			OWLNamedObject x = Refs.configSet.resolve().get("OperationsDatabaseConfig");
			connectionInfo = x.getIRI();
		}
		return connectionInfo;
	}

	public String newId() 
	{
		return newId(null);
	}
	
	public long generateSequenceNumber()
	{
		RelationalStoreExt store =  (RelationalStoreExt) Refs.defaultRelationalStoreExt.resolve();
		return store.nextSequenceNumber();		
	}

	/**
	 * Creates a new long ID from a db sequence and optionally wraps it into a bo iri.
	 * @param boType if not null, a bo IRI is returned, but not yet inserted in the DB.
	 * @return a new ID from the store. 
	 */
	public String newId(String boType)
	{
		String resultIri; 
		long n = generateSequenceNumber();
		if (boType != null)	{
			resultIri = Refs.boIriPrefix.resolve() + "/" + boType + "/" + n + "#bo";
		} else {
			resultIri  = Long.toString(n);
		}
		return resultIri;
	}
	
	/**
	 * Fetch the next sequence number from the USER_FRIENDLY_SEQUENCE
	 */
	public long generateUserFriendlySequence()
	{
		RelationalStoreExt store =  (RelationalStoreExt) Refs.defaultRelationalStoreExt.resolve();
		return store.nextUserFriendlySequenceNumber();
	}

//	/**
//	 * 
//	 * @param boType if not null, a #bo row is inserted in the IRI table with the newly created id.
//	 * @return a new ID from the store. 
//	 */
//	public String newIdOld(String boType)
//	{
//		String resultIri; 
//		RelationalOWLPersister persister = RelationalOWLPersister.getInstance(getConnectionInfo());
//		//Start transaction
//		boolean shouldRepeat;
//		do {
//			Connection conn = persister.getStore().getConnection();
//			try {
//				long n = persister.getStore().nextSequenceNumber();
//				if (boType != null)	{
//					resultIri = OWLRefs.BO_PREFIX + "/" + boType + "/" + n + "#bo";
//					persister.getStore().insertIri(n, resultIri, "NamedIndividual", conn);
//					conn.commit();
//				} else {
//					resultIri  = Long.toString(n);
//				}
//				shouldRepeat = false;
//			} catch (SQLException e) {
//				try {
//					conn.rollback();
//				} catch (SQLException e2) {
//					e2.printStackTrace(System.err);
//				}
//				if (RelationalStore.isCannotSerializeException(e)) {
//					System.out.println("Repeating newID: " + boType);
//					shouldRepeat = true;
//					resultIri = null;
//				} else {
//					throw new RuntimeException(e);
//				}
//			} finally {
//				try
//				{
//					conn.close();
//				}
//				catch (SQLException e)
//				{
//					e.printStackTrace();
//				}
//			}
//		} while (shouldRepeat);
//		return resultIri;
//	}
}

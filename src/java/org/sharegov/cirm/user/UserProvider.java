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
package org.sharegov.cirm.user;


import mjson.Json;

/**
 * <p>
 * Interface for a backing store of users. This used for authentication, profile
 * and group management.
 * </p>
 * 
 * <p>
 * Searches can be performed based on user id (a.k.a. username) or in query-by-example
 * fashion. In the latter case, since different backing stores will have different schemas,
 * the profile attributes are standardized by this interface following the OWL data properties
 * for talking about people:
 * </p>
 * <ul>
 * <li>FirstName</li>
 * <li>LastName</li>
 * <li>email</li>
 * <li>hasUsername</li>
 * <li>hasPassword</li>
 * <li>groups - a list of groups a user belongs to</li>
 * </ul>
 * 
 * <p>
 * Implementation have to abide by that blueprint both when returning data and when 
 * accepting queries by prototypical examples. Every implementation may support additional
 * properties not listed above.
 * </p>
 * 
 * @author Syed Abbas 
 * @author Borislav Iordanov
 */
public interface UserProvider 
{
	public Json find(String attribute, String value);
	public Json find(Json prototype);
	public Json find(Json prototype, int resultLimit);	
	public Json findGroups(String id);
	/**
	 * <p>
	 * Return user profile information by the username or <code>Json.nil</code> is not found.
	 * </p>
	 */
	public Json get(String id);
	public String getIdAttribute();
	public boolean authenticate(String username, String password);
	/**
	 * <p>
	 * Collect and extend user profile information with whatever this provider 
	 * can offer. 
	 * </p>
	 * 
	 * @param user User profile with some of the fields already filled in. Those
	 * fields can be used as search parameters in whatever backing stored the 
	 * implementation is providing access to.
	 * 
	 * @return The <code>user</code> parameter itself should be returned. (Note: when porting
	 * Java 8 this could be default implemented.)
	 */
	public Json populate(Json user);
}

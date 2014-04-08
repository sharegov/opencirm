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
package gov.miamidade.cirm;

public class UserUtil
{
	/**
	 * Determines if a String is an eKey or a cKey.
	 * @param uid
	 * @return
	 */
	public static boolean isECkey(String uid) 
	{
		return uid != null 
				&& uid.length() > 1 
				&& Character.isDigit(uid.charAt(uid.length() - 1)) 
				&& (uid.startsWith("e") || uid.startsWith("c"));
	}

	/**
	 * Determines is a String is a BlueBookKey (Number, length >= 2)
	 * @param uid
	 * @return
	 */
	public static boolean isBlueBookKey(String uid)
	{
		if (uid == null || uid.length() < 3) return false;
		for (int i = 0; i < uid.length(); i++)
		{
			if (!Character.isDigit(uid.charAt(i))) 
			{
				return false;
			}
		}
		return true;
	}
}

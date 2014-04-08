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

import mjson.Json;

import org.sharegov.cirm.user.DBUserProvider;

public class BluebookUserProvider extends DBUserProvider
{
	@Override
	public Json get(String id)
	{
		if (UserUtil.isECkey(id))
		{
			id = id.substring(1);
		}
		if (!UserUtil.isBlueBookKey(id)) 
			return Json.nil();
		else 
			return super.get(id);
	}
}

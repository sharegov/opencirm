/*******************************************************************************
 * Copyright 2016 Miami-Dade County
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
package org.sharegov.cirm.legacy;

import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

import mjson.Json;

/**
 * Use AnswerResolverDatePlus1Day to add 1 day to each detected date or datetime.
 * 
 * @author Thomas Hilpold
 *
 */
public class AnswerResolverDatePlus1Day implements VariableResolver {

	private AnswerResolver answerResolver;

	public AnswerResolverDatePlus1Day() {
		DateModifier datePlus1 = new DateModifier() {
			
			@Override
			public synchronized Date modifyDate(Date date) {
				Calendar c = Calendar.getInstance();
				c.setTime(date);
				c.add(Calendar.DAY_OF_MONTH, 1);
				return c.getTime();
			}
		};
		answerResolver = new AnswerResolver(datePlus1);
	}

	/* (non-Javadoc)
	 * @see org.sharegov.cirm.legacy.VariableResolver#resolve(java.lang.String, mjson.Json, java.util.Properties)
	 */
	@Override
	public String resolve(String variableName, Json sr, Properties properties) {
		return answerResolver.resolve(variableName, sr, properties);
	}
}

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
package gov.miamidade.cirm.search;

import org.sharegov.cirm.utils.JsonEvaluator;
import org.sharegov.cirm.utils.Mapping;

public class GisServiceRule
{
	private String asString;
	private String serviceName;
	private String fieldName;
	private Mapping<Object, Boolean> evaluator;
	
	GisServiceRule(String service, String field, String rule, Mapping<Object, Boolean> evaluator)
	{
		this.serviceName = service;
		this.fieldName = field;
		this.asString = rule;
		this.evaluator = evaluator;
	}
	
	public Boolean eval(Object x)
	{
		return evaluator.eval(x);
	}
	
	public String toString()
	{
		return asString;
	}
		
	public String getServiceName()
	{
		return serviceName;
	}

	public void setServiceName(String serviceName)
	{
		this.serviceName = serviceName;
	}

	public String getFieldName()
	{
		return fieldName;
	}

	public void setFieldName(String fieldName)
	{
		this.fieldName = fieldName;
	}

	public static GisServiceRule make(String serviceName, String fieldName, final String valueExpression)
	{
		Mapping<Object, Boolean> evaluator = null;
		if (valueExpression == null || valueExpression.trim().length() == 0)
			evaluator = new Mapping<Object, Boolean>() {
				public Boolean eval(Object x)
				{
					return Boolean.TRUE;
				}
			};
		else if ("MAINTCODE = 'CO' OR 'CM' OR 'CC' OR (MAINTCODE = CC and MDC.Municipality_poly.MUNICID = 30)".equals(valueExpression))
			evaluator = new Mapping<Object, Boolean>() {
			public Boolean eval(Object x)
			{
				return "CO".equals(x) || "CM".equals(x) || "CC".equals(x); //09-09-2014 - Syed added OR 'CC' test, TODO: the last or clause needs to be addressed
			}
		};
		else if ("NOT NULL AND > 0".equals(valueExpression))
			evaluator = new Mapping<Object, Boolean>() {
			public Boolean eval(Object x)
			{
				if (x instanceof String)
					x = Integer.parseInt(x.toString());				
				return x != null && ((Number)x).longValue() > 0;
			}
		};
		else if ("NOT NULL".equals(valueExpression))
			evaluator = new Mapping<Object, Boolean>() {
			public Boolean eval(Object x)
			{
				return x != null;
			}
		};
		else if ("MAINTCODE = 'CI' and address falls in City of Miami".equals(valueExpression))
			evaluator = new Mapping<Object, Boolean>() {
			public Boolean eval(Object x)
			{
				// TODO: how to deal with address here?
				// this is perhaps a case where a municipality constraint on the address should
				// play a role
				return "CI".equals(x); 
			}
		};
		else if ("UTILITYNAME = 'MDWS'".equals(valueExpression))
			evaluator = new Mapping<Object, Boolean>() {
			public Boolean eval(Object x)
			{
				return "MDWS".equals(x);
			}
		};
		else if ("ST_LIGHT = 'CML'".equals(valueExpression))
			evaluator = new Mapping<Object, Boolean>() {
			public Boolean eval(Object x)
			{
				return "CML".equals(x);
			}
		};
		else if ("ENFZONEID<>0 AND NOT NULL".equals(valueExpression))
			evaluator = new Mapping<Object, Boolean>() {
			public Boolean eval(Object x)
			{
				return x != null && !x.equals("0");
			}
		};
		else if ("INAREA = 'Y'".equals(valueExpression))
			evaluator = new Mapping<Object, Boolean>() {
			public Boolean eval(Object x)
			{
				return "Y".equals(x);
			}
		};
		else if ("ROUTE <> 'NONE' OR NOT NULL".equals(valueExpression))
			evaluator = new Mapping<Object, Boolean>() {
			public Boolean eval(Object x)
			{
				return x != null || "ROUTE".equals(x); // TODO: this doesn't make sense logically?!?
			}
		};
		else if ("ID > 0 and NOT NULL".equals(valueExpression))
			evaluator = new Mapping<Object, Boolean>() {
			public Boolean eval(Object x)
			{
				if (x instanceof String)
					x = Integer.parseInt(x.toString());
				return x != null && ((Number)x).longValue() > 0;
			}
		};
			else if ("ENFORCEMEN <> 61 AND NOT NULL".equals(valueExpression))
				evaluator = new Mapping<Object, Boolean>() {
				public Boolean eval(Object x)
				{
					if (x instanceof String)
						x = Integer.parseInt(x.toString());
					return x != null && ((Number)x).longValue() != 61;
				}
		};
		else if (valueExpression.trim().charAt(0) == '{' || valueExpression.trim().charAt(0) == '[')
			evaluator = new JsonEvaluator(valueExpression);
		else
			throw new RuntimeException("Unknown rule expression:" + valueExpression);
		return new GisServiceRule(serviceName, fieldName, valueExpression, evaluator);
	}
}

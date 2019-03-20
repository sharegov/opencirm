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
package org.sharegov.cirm.legacy;

import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFunction;
import javax.xml.xpath.XPathFunctionResolver;

import org.junit.Test;
import org.sharegov.cirm.utils.xpath.TimeFunction;

public class MessageManagerTest
{

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception
	{
//		MessageManager msgManager = new MessageManager();
////		msgManager.getMessageVariables();
////		System.out.println(msgManager.messageVariables);
//		OWLLiteral body = OWLUtils.dataProperty(OWLUtils.individual("legacy:ZONCALLB_EMAIL_GENASSIG"), "legacy:hasBody");
//		System.out.println(body);
		//msgManager.fillParameters(null, null, body.getLiteral());
		XPathFactory factory = XPathFactory.newInstance();
		factory.setXPathFunctionResolver( new XPathFunctionResolver()
		{
			
			Map<QName, XPathFunction> functions = new HashMap<QName, XPathFunction>()
			{
				{
					this.put(new QName("http://www.miamidade.gov", "time", "mdc"), TimeFunction.class.newInstance());
				}
			};
			
			@Override
			public XPathFunction resolveFunction(QName functionName, int arity)
			{
				return functions.get(functionName);
			}
		});
		
		System.out.println(factory.newXPath().getXPathFunctionResolver().resolveFunction(new QName("http://www.miamidade.gov", "time", "mdc"),1));
		

	}
	
	@Test
	public void testSaveMessageVariable()
	{
		MessageManager manager = new MessageManager();
		try
		{
//			manager.saveMessageVariable("PUBLIC_$$SR_EID$$", "/sr/boid");
//			manager.saveMessageVariable("PUBLIC_$$SR_STATUS$$", "/sr/hasStatus/label");
//			manager.saveMessageVariable("PUBLIC_$$SR_NUMBER$$", "/sr/boid");
//			manager.saveMessageVariable("PUBLIC_$$SR_TYPE$$", "/sr/type");
//			manager.saveMessageVariable("PUBLIC_$$SR_RECEIVED_METHOD$$", "/sr/hasIntakeMethod/label");
//			manager.saveMessageVariable("PUBLIC_$$SR_TYPE_DESCRIPTION$$", "/sr/typeDescription");
//			manager.saveMessageVariable("PUBLIC_$$SR_PRIORITY$$", "/sr/hasPriority/label");
//			manager.saveMessageVariable("PUBLIC_$$SR_LOCATION_STREET_ADDRESS$$", "/sr/atAddress/fullAddress");
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		
		}
	}
	

}

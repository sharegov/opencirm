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
package org.sharegov.cirm.enunciate;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.codehaus.enunciate.EnunciateException;
import org.codehaus.enunciate.config.EnunciateConfiguration;
import org.codehaus.enunciate.main.Enunciate;
import org.sharegov.cirm.rest.MainRestApplication;
import org.sharegov.cirm.rest.MetaService;
import org.xml.sax.SAXException;

/**
 * Generates JAX-RS documentation via Enunciate.
 * 
 * @author	Alfonso Boza	<ABOZA@miamidade.gov>
 * @version	1.0
 */
public class Documentation
{
	final private static String CONFIGURATION_FILE_NAME = "enunciate.xml";

	/**
	 * Sets up and then executes Enunciate to generate documentation for RESTful web service.
	 * 
	 * @param args	An array of program options.
	 */
	public static void main(String [] args)
	{
		try
		{
			// Get location of Enunciate configuration file.
			String location = Documentation.class.getProtectionDomain().getCodeSource().getLocation().getPath();
			if (location.endsWith("/"))
			{
				location = location.substring(0, location.lastIndexOf("/")).replace("%20", " ");
			}
			File xml = new File(String.format("%s/../%s", location, CONFIGURATION_FILE_NAME));
			// Load configuration.
			EnunciateConfiguration configuration = new EnunciateConfiguration();
			configuration.load(xml);
			// Get REST classes from application.
			MetaService meta = new MetaService();
			Set<Class<?>> classes = new MainRestApplication().getClasses();
			List<String> services = new ArrayList<String>();
			Iterator<Class<?>> iterator = classes.iterator();
			while (iterator.hasNext())
			{
				Class<?> clazz = iterator.next();
				String path = clazz.getPackage().getName().replaceAll("\\.", "/");
				services.add(String.format("%s/../src/java/%s/%s.java", location, path, clazz.getSimpleName()));
			}
			// Construct and generate documentation using Enunciate.
			Enunciate enunciate = new Enunciate(services.toArray(new String[] {}), configuration);
			enunciate.execute();
		}
		catch (IOException e)
		{
			System.err.println("Unable to open configuration file.");
			e.printStackTrace();
			System.exit(1);
		}
		catch (SAXException e)
		{
			System.err.println("Configuration file is improperly formatted.");
			e.printStackTrace();
			System.exit(1);
		}
		catch (EnunciateException e)
		{
			System.err.println("Enunciate was unable to generate documentation.");
			e.printStackTrace();
			System.exit(1);
		}
	}
}

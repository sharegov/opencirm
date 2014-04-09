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
package org.sharegov.cirm;

import mjson.Json;

/**
 * <p>
 * An object is <code>auto configurable</code> if it is capable of picking up
 * its description from the backing ontology. One simple way to use this
 * mechanism is the declare an OWL individual of type <code>JavaClass</code>
 * and an IRI the fully qualified Java class name. Then any configuration properties
 * will be associated with that individual, but the corresponding runtime Java object
 * will have to read them and initialize itself.
 * </p>
 * 
 * @author Borislav Iordanov
 *
 */
public interface AutoConfigurable
{
    void autoConfigure(Json config);
}

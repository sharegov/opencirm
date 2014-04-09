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

import java.util.Set;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLProperty;

public class KeyValuePropertyMapper implements OWLPropertyMapper<Json>
{
    private String keyName;
    private boolean shortForm = false;
    
    public KeyValuePropertyMapper()
    {
    }
    
    @Override
    public Json map(OWLOntology ontology, 
                    OWLIndividual object,
                    OWLProperty<?, ?> property, 
                    Set<OWLObject> values)
    {
        Json result = Json.object();
        for (OWLObject v : values)
        {
            Json j = OWL.toJSON(v);
            if (!j.isObject() || !j.has(keyName) || !j.at(keyName).isString())
                continue; // TODO: maybe one should be able to configure this mapper to throw an error here...
            if (keyName != null)
                result.at(j.at(keyName).asString(), j);
            else if (v instanceof OWLNamedObject)
            {
                OWLNamedObject named = (OWLNamedObject)v;
                result.at(shortForm ? named.getIRI().getFragment() : named.getIRI().toString(), v);
            }
        }
        return result;
    }

    public void configure(Json desc)
    {
        if (desc.has("hasName"))
            keyName = desc.at("hasName").asString();
        this.shortForm = desc.is("useShortForm", true);
    }    
}

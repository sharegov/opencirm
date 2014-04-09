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
package org.sharegov.cirm.utils;

import java.util.Set;

import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.AutoConfigurable;
import org.sharegov.cirm.OWL;

/**
 * <p>
 * This reference constructs a live Java object based on an OWL individual
 * description. Note that the reference is in effect a factory as every
 * call to {@link #resolve()} will create a new object. To make it a singleton,
 * wrap it in a {@link SingletonRef}
 * 
 * </p>
 * 
 * @author Borislav Iordanov
 *
 * @param <T>
 */
public class DescribedRef<T> implements Ref<T>
{
    private String name;
    private OWLNamedIndividual desc;
    
    // create the object from the description
    private T make(OWLNamedIndividual desc)
    {
        Set<OWLClassExpression> types = desc.getTypes(OWL.ontologies());

        OWLNamedIndividual impl;
        if (types.contains(OWL.owlClass("JavaClass")))
        {
            String classname = desc.getIRI().getFragment();
            try 
            {
                @SuppressWarnings("unchecked")
                T result = (T)Class.forName(classname).newInstance();
                if (result instanceof AutoConfigurable)
                    ((AutoConfigurable)result).autoConfigure(OWL.toJSON(desc));
                return result;
            }
            catch (Exception ex)
            {
                throw new RuntimeException(ex);
            }
        }
        else if ( (impl = OWL.objectProperty(desc, "hasImplementation")) != null)
        {
            return make(impl);
        }
        else
            throw new UnsupportedOperationException("Don't know how to construct Java object for " + desc.getIRI());
    }
    
    public DescribedRef(String name)
    {
        this.name = name;
    }
    
    public DescribedRef(OWLNamedIndividual desc)
    {
        
    }
    
    public T resolve()
    {
        if (desc == null)
            desc = OWL.individual(name);
        return make(this.desc);
    }
}

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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.CharBuffer;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;

import mjson.Json;

@javax.ws.rs.ext.Provider
@javax.ws.rs.Consumes("application/json")
@javax.ws.rs.Produces("application/json")
//@javax.ws.rs.Produces("application/x-gzip-compressed")
public class JsonEntityProvider implements MessageBodyReader<Json>,
        MessageBodyWriter<Json>
{
    public boolean isWriteable(Class<?> type, Type genericType,
                               Annotation[] annotations, MediaType mediaType)
    {
        return Json.class.isAssignableFrom(type);
    }

    public long getSize(Json t, Class<?> type, Type genericType,
                        Annotation[] annotations, MediaType mediaType)
    {
        return -1;
    }

    public void writeTo(Json t, Class<?> type, Type genericType,
                        Annotation[] annotations, MediaType mediaType,
                        MultivaluedMap<String, Object> httpHeaders,
                        OutputStream entityStream) throws IOException,
            WebApplicationException
    {
//    	httpHeaders.put("Content-Type", Collections.singletonList((Object)"application/json"));
//    	httpHeaders.put("Content-Encoding", Collections.singletonList((Object)"gzip"));
    	//java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(entityStream);
        entityStream.write(t.toString().getBytes());
    }

    public boolean isReadable(Class<?> type, Type genericType,
                              Annotation[] annotations, MediaType mediaType)
    {
        return Json.class == type;
//        return MediaType.APPLICATION_JSON.equals(mediaType) || 
//               MediaType.APPLICATION_JSON_TYPE.equals(mediaType);
    }

    public Json readFrom(Class<Json> type, Type genericType,
                         Annotation[] annotations, MediaType mediaType,
                         MultivaluedMap<String, String> httpHeaders,
                         InputStream entityStream) throws IOException, WebApplicationException
    {
        Reader reader = new InputStreamReader(entityStream);
        StringBuilder builder = new StringBuilder();
        CharBuffer buf = CharBuffer.allocate(1024);
        while (true)
        {
            buf.clear();
            int cnt = reader.read(buf);
            if (cnt == -1)
                break;
            buf.flip();
            builder.append(buf);
        }        
        return Json.read(builder.toString());
    }
}

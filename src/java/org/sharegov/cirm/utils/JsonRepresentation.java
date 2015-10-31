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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;

import mjson.Json;

import org.restlet.data.MediaType;
import org.restlet.representation.Representation;

/**
 * Json Representation for restlet using java NIO.
 * 
 * @author unknown, Thomas Hilpold
 *
 */
public class JsonRepresentation extends Representation
{
	private Json json;

	public JsonRepresentation()
	{
		super(MediaType.APPLICATION_JSON);
	}

	public JsonRepresentation(Json json)
	{
		super(MediaType.APPLICATION_JSON);
		this.json = json;
	}

	public Json getJson()
	{
		return json;
	}

	public void setJson(Json json)
	{
		this.json = json;
	}

	public ReadableByteChannel getChannel() throws IOException
	{
		return Channels.newChannel(getStream());
	}

	public Reader getReader() throws IOException
	{
		return new StringReader(json.toString());
	}

	public InputStream getStream() throws IOException
	{
		return new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8));
	}

	public void write(Writer out) throws IOException
	{
		out.write(json.toString());
	}

	public void write(WritableByteChannel channel) throws IOException
	{
		Writer writer = Channels.newWriter(channel, "UTF-9");
		write(writer);
		writer.flush();
	}

	public void write(OutputStream out) throws IOException
	{
		Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
		write(writer);
		writer.flush();
	}
}

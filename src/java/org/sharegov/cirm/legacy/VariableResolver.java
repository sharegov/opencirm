/**
 * 
 */
package org.sharegov.cirm.legacy;

import java.util.Properties;

import mjson.Json;

/**
 * @author SABBAS
 *
 */
public interface VariableResolver
{
	public String resolve(String variableName, Json sr, Properties properties);
}

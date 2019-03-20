package org.sharegov.cirm;

import org.sharegov.cirm.utils.Ref;
import org.sharegov.cirm.utils.SingletonRef;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;
/**
 * System refs, independent of and not reliant on the current Startup.config.
 * 
 * @author Thomas Hilpold
 *
 */
public class SysRefs
{
	public static final Ref<String> serverName2 = new SingletonRef<String>(new Ref<String>() {
		@Override
		public String resolve()
		{
			String result;
			try {
				result = java.net.InetAddress.getLocalHost().getHostName();
			 if (result.length() >= 2)
				 result = result.substring(result.length() - 2);
			} catch (Exception e) 
			{
				ThreadLocalStopwatch.error("Refs.serverName2 resolve failed with " + e);
				result = "NA";
			}
			return result;
		}		
	});
}

package gov.miamidade.cirm.other;

/**
 * Class representing the status of Live Reporting.
 * 
 * This class is completely thread safe (non blocking).
 * 
 * @author Thomas Hilpold
 */
public class LiveReportingStatus
{
	private volatile boolean isEnabled = true;
	
	public boolean isEnabled() {
		return isEnabled;
	}
	
	/**
	 * Sets a new status.
	 * @param enabled
	 * @return false, if the given status was already set.
	 */
	public boolean setEnabled(boolean enabled) {
		if (isEnabled == enabled) {
			return false;
		} else {
			isEnabled = enabled;
			return true;
		}
	}
}

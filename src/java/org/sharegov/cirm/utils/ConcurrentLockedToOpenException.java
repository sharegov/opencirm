package org.sharegov.cirm.utils;

/**
 * A user tries to update an interface service request to open status after it was locked by the interface.
 *  
 * @author Thomas Hilpold
 *
 */
public class ConcurrentLockedToOpenException extends ConcurrentServiceRequestUpdateException {

	private static final long serialVersionUID = -293987179261698588L;

	public static final String DEFAULT_USER_MESSAGE = "<br><br>\r\nThe update of this LOCKED interface service request to OPEN was prevented. <br><br>\r\n " 
													+ "Please reload the SR to retrieve the LOCKED status, reapply your changes, and save again.";				
	
	/**
	 * Create with DEFAULT_USER_MESSAGE.
	 */
	public ConcurrentLockedToOpenException() {
		super(DEFAULT_USER_MESSAGE);
	}
	
	public ConcurrentLockedToOpenException(String message) {
		super(message);
	}

	public ConcurrentLockedToOpenException(String message, Throwable cause) {
		super(message, cause);
	}

	public ConcurrentLockedToOpenException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}

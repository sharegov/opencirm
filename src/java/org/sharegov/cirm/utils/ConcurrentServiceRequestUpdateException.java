package org.sharegov.cirm.utils;

/**
 * User tries to update a service request that was modified by interfaces or another user
 * after the user loaded the service request.
 * 
 * @author Thomas Hilpold
 *
 */
public class ConcurrentServiceRequestUpdateException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ConcurrentServiceRequestUpdateException(String message) {
		super(message);
	}

	public ConcurrentServiceRequestUpdateException(String message, Throwable cause) {
		super(message, cause);
	}

	public ConcurrentServiceRequestUpdateException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}

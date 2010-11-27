/**
 * 
 */
package com.raddle.config.tree.remote.exception;

/**
 * 等待响应超时
 * @author xurong
 *
 */
public class ResponseTimeoutException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public ResponseTimeoutException() {
	}

	/**
	 * @param message
	 */
	public ResponseTimeoutException(String message) {
		super(message);
	}

	/**
	 * @param cause
	 */
	public ResponseTimeoutException(Throwable cause) {
		super(cause);
	}

	/**
	 * @param message
	 * @param cause
	 */
	public ResponseTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}

}

/**
 * 
 */
package com.raddle.config.tree.remote.exception;

/**
 * 远程已经收到命令，但在执行过程中出现异常
 * @author xurong
 *
 */
public class RemoteExecuteException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public RemoteExecuteException() {
	}

	/**
	 * @param message
	 */
	public RemoteExecuteException(String message) {
		super(message);
	}

	/**
	 * @param cause
	 */
	public RemoteExecuteException(Throwable cause) {
		super(cause);
	}

	/**
	 * @param message
	 * @param cause
	 */
	public RemoteExecuteException(String message, Throwable cause) {
		super(message, cause);
	}

}

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
	
	private String type;

	/**
	 * @param message
	 */
	public RemoteExecuteException(String type, String message) {
		super(message);
		this.type = type;
	}

	/**
	 * @param message
	 * @param cause
	 */
	public RemoteExecuteException(String type, String message, Throwable cause) {
		super(message, cause);
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

}

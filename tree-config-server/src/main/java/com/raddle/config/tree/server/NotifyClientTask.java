/**
 * 
 */
package com.raddle.config.tree.server;

/**
 * @author xurong
 * 
 */
public class NotifyClientTask {
	private String clientId;
	private String method;
	private Object[] args;

	public NotifyClientTask(String clientId, String method, Object[] args) {
		this.clientId = clientId;
		this.method = method;
		this.args = args;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public Object[] getArgs() {
		return args;
	}

	public void setArgs(Object[] args) {
		this.args = args;
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}
}

/**
 * 
 */
package com.raddle.config.tree.client.impl;

import org.apache.mina.core.session.IoSession;

import com.raddle.nio.mina.cmd.invoke.InvokeMethod;

/**
 * @author xurong
 * 
 */
public interface TreeConfigClientListener {
	/**
	 * 连接建立,断开后重新连接也会调用<br>
	 * 这个方法在执行初始化操作之前调用，可以在这个方法里，执行一些初始化的操作
	 * @param session
	 */
	public void sessionConnected(IoSession session);
	
	/**
	 * 收到执行命令
	 * @param methodInvoke
	 */
	public void commandReceived(InvokeMethod methodInvoke);
}

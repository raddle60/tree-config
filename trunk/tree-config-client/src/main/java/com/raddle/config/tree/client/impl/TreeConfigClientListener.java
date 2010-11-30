/**
 * 
 */
package com.raddle.config.tree.client.impl;

import org.apache.mina.core.session.IoSession;

import com.raddle.nio.mina.cmd.invoke.MethodInvoke;

/**
 * @author xurong
 * 
 */
public interface TreeConfigClientListener {
	/**
	 * 连接建立
	 * @param session
	 */
	public void sessionConnected(IoSession session);
	
	/**
	 * 收到执行命令
	 * @param methodInvoke
	 */
	public void commandReceived(MethodInvoke methodInvoke);
}

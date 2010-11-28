package com.raddle.config.tree.client;

import com.raddle.config.tree.api.TreeConfigManager;
import com.raddle.config.tree.api.TreeConfigNode;

/**
 * @author xurong
 * 
 */
public interface TreeConfigClient extends TreeConfigManager {
	/**
	 * 绑定断开连接时的值，连接断开后，服务端自动设置这些值
	 * @param disconnectedValue 端口连接后的值
	 * @param includeNodeValue 是否更新节点的值
	 */
	public void bindDisconnectedValue(TreeConfigNode disconnectedValue ,boolean includeNodeValue);
	
	/**
	 * 断开和服务器的连接
	 */
	public void close();
}

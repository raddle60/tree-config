package com.raddle.config.tree.client;

import com.raddle.config.tree.api.TreeConfigManager;
import com.raddle.config.tree.api.TreeConfigNode;
import com.raddle.config.tree.api.TreeConfigPath;

/**
 * @author xurong
 * 
 */
public interface TreeConfigClient extends TreeConfigManager {
	/**
	 * 绑定断开连接时的值，连接断开后，服务端自动设置这些值,只更新值，不会删除属性
	 * @param node 端口连接后的节点
	 * @param includeNodeValue 是否更新节点的值
	 */
	public void bindDisconnectedNode(TreeConfigNode node ,boolean includeNodeValue);
	
	/**
	 * 绑定断开连接时的值，连接断开后，服务端自动刪除这个节点
	 * @param path 断开连接后的节点节点路径
	 * @param recursive 是否有子节点也删除
	 */
	public void bindDisconnectedDelNode(TreeConfigPath path ,boolean recursive);
	
	/**
	 * 绑定连接时，初始化的节点。第一次连接和每次异常断开再连接时，都会重新发送这些值到服务器端，只更新值，不会删除属性
	 * @param path 初始化节点路径
	 * @param recursive 是否递归更新
	 */
	public void bindInitialPushNodes(TreeConfigPath path , boolean recursive);
	
	/**
	 * 绑定连接时，初始化获取的节点。第一次连接和每次异常断开再连接时，都会重新从服务器端获取这些值<br>
	 * 服务端更具这个path，判断是否需要发送更新通知过来
	 * @param path 初始化节点路径
	 * @param recursive 是否递归获取
	 */
	public void bindInitialGetNodes(TreeConfigPath path , boolean recursive);
	
	/**
	 * 是否已连接
	 * @return
	 */
	public boolean isConnected();
	
	/**
	 * 断开和服务器的连接
	 */
	public void close();
}

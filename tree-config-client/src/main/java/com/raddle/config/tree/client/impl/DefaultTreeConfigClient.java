/**
 * 
 */
package com.raddle.config.tree.client.impl;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

import org.apache.mina.transport.socket.nio.NioSocketConnector;

import com.raddle.config.tree.api.TreeConfigAttribute;
import com.raddle.config.tree.api.TreeConfigManager;
import com.raddle.config.tree.api.TreeConfigNode;
import com.raddle.config.tree.api.TreeConfigPath;
import com.raddle.config.tree.client.TreeConfigClient;
import com.raddle.config.tree.local.MemoryConfigManager;

/**
 * @author xurong
 * 
 */
public class DefaultTreeConfigClient implements TreeConfigClient {
	private String clientId = UUID.randomUUID().toString();
	private String serverIp;
	private int serverPort;
	private TreeConfigManager localManager = new MemoryConfigManager();
	private NioSocketConnector connector = null;

	public DefaultTreeConfigClient(String clientId, String serverIp, int serverPort) {
		this.clientId = clientId;
		this.serverIp = serverIp;
		this.serverPort = serverPort;
	}

	public DefaultTreeConfigClient(String serverIp, int serverPort) {
		this.serverIp = serverIp;
		this.serverPort = serverPort;
	}

	public synchronized void connect() {
		if (connector == null) {
			connector = new NioSocketConnector();
		}
	}

	public synchronized void close() {
		if (connector != null) {
			connector.dispose();
			connector = null;
		}
	}

	@Override
	public void bindDisconnectedValue(TreeConfigNode disconnectedValue, boolean includeNodeValue) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeAttributes(TreeConfigPath path, String... attributeNames) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean removeNode(TreeConfigPath path, boolean recursive) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean removeNodes(List<TreeConfigPath> paths, boolean recursive) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void saveAttribute(TreeConfigPath path, TreeConfigAttribute attribute) {
		// TODO Auto-generated method stub

	}

	@Override
	public void saveAttributeValue(TreeConfigPath path, String attributeName, Serializable value) {
		// TODO Auto-generated method stub

	}

	@Override
	public void saveNode(TreeConfigNode node, boolean updateNodeValue) {
		// TODO Auto-generated method stub

	}

	@Override
	public void saveNodeValue(TreeConfigPath path, Serializable value) {
		// TODO Auto-generated method stub

	}

	@Override
	public void saveNodes(List<TreeConfigNode> nodes, boolean updateNodeValue) {
		// TODO Auto-generated method stub

	}

	@Override
	public TreeConfigAttribute getAttribute(TreeConfigPath path, String attributeName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Serializable getAttributeValue(TreeConfigPath path, String attributeName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<TreeConfigNode> getChildren(TreeConfigPath path) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TreeConfigNode getNode(TreeConfigPath path) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Serializable getNodeValue(TreeConfigPath path) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isAttributesExist(TreeConfigPath path, String... attributeNames) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isNodeExist(TreeConfigPath path) {
		// TODO Auto-generated method stub
		return false;
	}

	public TreeConfigManager getLocalManager() {
		return localManager;
	}

	public void setLocalManager(TreeConfigManager localManager) {
		this.localManager = localManager;
	}

	public String getClientId() {
		return clientId;
	}

	public String getServerIp() {
		return serverIp;
	}

	public int getServerPort() {
		return serverPort;
	}

}

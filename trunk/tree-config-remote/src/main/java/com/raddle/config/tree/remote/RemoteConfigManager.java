/**
 * 
 */
package com.raddle.config.tree.remote;

import java.io.Serializable;
import java.util.List;

import org.apache.mina.core.session.IoSession;

import com.raddle.config.tree.api.TreeConfigAttribute;
import com.raddle.config.tree.api.TreeConfigManager;
import com.raddle.config.tree.api.TreeConfigNode;
import com.raddle.config.tree.api.TreeConfigPath;

/**
 * @author xurong
 * 
 */
public class RemoteConfigManager implements TreeConfigManager {
	private int timeoutSeconds = 3;
	private SyncCommandSender commandSender;

	public RemoteConfigManager(IoSession session) {
		this.commandSender = new SyncCommandSender(session);
	}

	private Object sendCommand(String method, Object[] args) {
		return commandSender.sendCommand("treeConfigManager", method, args, timeoutSeconds);
	}

	@Override
	public void removeAttributes(TreeConfigPath path, String... attributeNames) {
		sendCommand("removeAttributes", new Object[] { path, attributeNames });
	}

	@Override
	public boolean removeNode(TreeConfigPath path, boolean recursive) {
		return (Boolean) sendCommand("removeNode", new Object[] { path, recursive });
	}

	@Override
	public boolean removeNodes(List<TreeConfigPath> paths, boolean recursive) {
		return (Boolean) sendCommand("removeNodes", new Object[] { paths, recursive });
	}

	@Override
	public void saveAttribute(TreeConfigPath path, TreeConfigAttribute attribute) {
		sendCommand("saveAttribute", new Object[] { path, attribute });
	}

	@Override
	public void saveAttributeValue(TreeConfigPath path, String attributeName, Serializable value) {
		sendCommand("saveAttributeValue", new Object[] { path, attributeName, value });
	}

	@Override
	public void saveNode(TreeConfigNode node, boolean updateNodeValue) {
		sendCommand("saveNode", new Object[] { node, updateNodeValue });
	}

	@Override
	public void saveNodeValue(TreeConfigPath path, Serializable value) {
		sendCommand("saveNodeValue", new Object[] { path, value });
	}

	@Override
	public void saveNodes(List<TreeConfigNode> nodes, boolean updateNodeValue) {
		sendCommand("saveNodes", new Object[] { nodes, updateNodeValue });
	}

	@Override
	public TreeConfigAttribute getAttribute(TreeConfigPath path, String attributeName) {
		return (TreeConfigAttribute) sendCommand("getAttribute", new Object[] { path, attributeName });
	}

	@Override
	public Serializable getAttributeValue(TreeConfigPath path, String attributeName) {
		return (Serializable) sendCommand("getAttributeValue", new Object[] { path, attributeName });
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<TreeConfigNode> getChildren(TreeConfigPath path) {
		return (List<TreeConfigNode>) sendCommand("getChildren", new Object[] { path });
	}

	@Override
	public TreeConfigNode getNode(TreeConfigPath path) {
		return (TreeConfigNode) sendCommand("getNode", new Object[] { path });
	}

	@Override
	public Serializable getNodeValue(TreeConfigPath path) {
		return (Serializable) sendCommand("getNodeValue", new Object[] { path });
	}

	@Override
	public boolean isAttributesExist(TreeConfigPath path, String... attributeNames) {
		return (Boolean) sendCommand("isAttributesExist", new Object[] { path, attributeNames });
	}

	@Override
	public boolean isNodeExist(TreeConfigPath path) {
		return (Boolean) sendCommand("isNodeExist", new Object[] { path });
	}

	public int getTimeoutSeconds() {
		return timeoutSeconds;
	}

	public void setTimeoutSeconds(int timeoutSeconds) {
		this.timeoutSeconds = timeoutSeconds;
	}
}

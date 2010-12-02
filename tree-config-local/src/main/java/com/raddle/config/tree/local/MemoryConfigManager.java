/**
 * 
 */
package com.raddle.config.tree.local;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.raddle.config.tree.DefaultConfigNode;
import com.raddle.config.tree.DefaultConfigPath;
import com.raddle.config.tree.api.TreeConfigAttribute;
import com.raddle.config.tree.api.TreeConfigListenable;
import com.raddle.config.tree.api.TreeConfigListener;
import com.raddle.config.tree.api.TreeConfigManager;
import com.raddle.config.tree.api.TreeConfigNode;
import com.raddle.config.tree.api.TreeConfigPath;

/**
 * @author xurong
 *
 */
public class MemoryConfigManager implements TreeConfigManager,TreeConfigListenable {
	private DefaultConfigNode root = new DefaultConfigNode();
	private TreeConfigListener listener = new TreeConfigListenerAdapter();

	@Override
	public void removeAttributes(TreeConfigPath path, String... attributeNames) {
		TreeConfigNode n = getNodeByPath(path, false);
		if(n != null){
			for (String attributeName : attributeNames) {
				TreeConfigAttribute old = n.getAttribute(attributeName);
				n.removeAttribute(attributeName);
				listener.attributeRemoved(n, old);
			}
		}
	}

	@Override
	public boolean removeNode(TreeConfigPath path, boolean recursive) {
		DefaultConfigNode n = (DefaultConfigNode) getNodeByPath(path, false);
		if(n != null){
			if(n.getChildren().size() > 0){
				if(recursive){
					DefaultConfigNode old = n.getParent().getChild(n.getNodePath().getLast());
					n.getParent().removeChild(n.getNodePath().getLast());
					listener.nodeRemoved(old);
				} else {
					return false;
				}
			} else {
				DefaultConfigNode old = n.getParent().getChild(n.getNodePath().getLast());
				n.getParent().removeChild(n.getNodePath().getLast());
				listener.nodeRemoved(old);
			}
		}
		return false;
	}

	@Override
	public boolean removeNodes(List<TreeConfigPath> paths, boolean recursive) {
		boolean ret = true;
		for (TreeConfigPath treeConfigPath : paths) {
			boolean remove = removeNode(treeConfigPath, recursive);
			if (!remove) {
				ret = false;
			}
		}
		return ret;
	}

	@Override
	public void saveAttribute(TreeConfigPath path, TreeConfigAttribute attribute) {
		TreeConfigNode n = getNodeByPath(path, true);
		Serializable oldValue = n.getAttributeValue(attribute.getName());
		n.setAttributeValue(attribute.getName(), attribute.getValue());
		listener.attributeValueChanged(n, n.getAttribute(attribute.getName()), attribute.getValue(), oldValue);
	}

	@Override
	public void saveAttributeValue(TreeConfigPath path, String attributeName, Serializable value) {
		TreeConfigNode n = getNodeByPath(path, true);
		Serializable oldValue = n.getAttributeValue(attributeName);
		n.setAttributeValue(attributeName, value);
		listener.attributeValueChanged(n, n.getAttribute(attributeName), value, oldValue);
	}

	@Override
	public void saveNode(TreeConfigNode node, boolean updateNodeValue) {
		TreeConfigNode n = getNodeByPath(node.getNodePath(), true);
		// 如果节点已存在
		if (updateNodeValue) {
			Serializable oldValue = n.getValue();
			// 更新节点值
			n.setValue(node.getValue());
			listener.nodeValueChanged(n, n.getValue(), oldValue);
		} else {
			// 更新属性值
			for (TreeConfigAttribute attribute : node.getAttributes()) {
				Serializable oldValue = n.getAttributeValue(attribute.getName());
				n.setAttributeValue(attribute.getName(), attribute.getValue());
				listener.attributeValueChanged(node, n.getAttribute(attribute.getName()), attribute.getValue(), oldValue);
			}
		}
	}

	@Override
	public void saveNodeValue(TreeConfigPath path, Serializable value) {
		TreeConfigNode n = getNodeByPath(path, true);
		Serializable oldValue = n.getValue();
		n.setValue(value);
		listener.nodeValueChanged(n, n.getValue(), oldValue);
	}

	@Override
	public void saveNodes(List<TreeConfigNode> nodes, boolean updateNodeValue) {
		for (TreeConfigNode treeConfigNode : nodes) {
			saveNode(treeConfigNode, updateNodeValue);
		}
	}

	@Override
	public TreeConfigAttribute getAttribute(TreeConfigPath path, String attributeName) {
		TreeConfigNode node = getNodeByPath(path, false);
		if(node != null){
			return node.getAttribute(attributeName);
		}
		return null;
	}

	@Override
	public Serializable getAttributeValue(TreeConfigPath path, String attributeName) {
		TreeConfigAttribute attribute = getAttribute(path, attributeName);
		if (attribute != null) {
			return attribute.getValue();
		}
		return null;
	}

	@Override
	public List<TreeConfigNode> getChildren(TreeConfigPath path) {
		if(path == null){
			return new ArrayList<TreeConfigNode>(root.getChildren().values());
		}
		DefaultConfigNode node = (DefaultConfigNode) getNodeByPath(path, false);
		ArrayList<TreeConfigNode> list = new ArrayList<TreeConfigNode>();
		if(node != null){
			for (TreeConfigNode n : node.getChildren().values()) {
				list.add(((DefaultConfigNode)n).toSelfOnly());
			}
		}
		return list;
	}

	@Override
	public TreeConfigNode getNode(TreeConfigPath path) {
		DefaultConfigNode node = (DefaultConfigNode) getNodeByPath(path, false);
		if(node != null){
			return node.toSelfOnly();
		}
		return null;
	}

	@Override
	public Serializable getNodeValue(TreeConfigPath path) {
		TreeConfigNode node = getNodeByPath(path, false);
		if(node != null){
			return node.getValue();
		}
		return null;
	}

	@Override
	public boolean isAttributesExist(TreeConfigPath path, String... attributeNames) {
		TreeConfigNode node = getNodeByPath(path,false);
		if(node == null){
			return false;
		}
		for (String attributeName : attributeNames) {
			if(node.getAttribute(attributeName) == null){
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean isNodeExist(TreeConfigPath path) {
		return getNodeByPath(path,false) != null;
	}
	
	private TreeConfigNode getNodeByPath(TreeConfigPath nodePath, boolean create) {
		DefaultConfigNode parent = root;
		DefaultConfigNode current = null;
		for (int i = 0; i < nodePath.getPath().length; i++) {
			// 根据段查找
			current = parent.getChild(nodePath.getPath()[i]);
			if (current == null) {
				if (create) {
					current = new DefaultConfigNode();
					DefaultConfigPath curPath = new DefaultConfigPath();
					curPath.setPath(nodePath.getSubPath(0, i + 1));
					current.setNodePath(curPath);
					current.setParent(parent);
					parent.setChild(curPath.getLast(), current);
				} else {
					return null;
				}
			}
			// 查找下一段
			parent = current;
		}
		return current;
	}

	@Override
	public List<TreeConfigNode> getDescendants(TreeConfigPath path) {
		List<TreeConfigNode> descendants = new ArrayList<TreeConfigNode>();
		if (path == null) {
			Map<String, DefaultConfigNode> children = root.getChildren();
			for (DefaultConfigNode treeConfigNode : children.values()) {
				putDescendants(treeConfigNode, descendants);
			}
		} else {
			DefaultConfigNode node = (DefaultConfigNode) getNodeByPath(path, false);
			if (node != null) {
				Map<String, DefaultConfigNode> children = node.getChildren();
				for (DefaultConfigNode treeConfigNode : children.values()) {
					putDescendants(treeConfigNode, descendants);
				}
			}
		}
		return descendants;
	}
	
	private void putDescendants(DefaultConfigNode parent, List<TreeConfigNode> descendants) {
		descendants.add(parent.toSelfOnly());
		for (DefaultConfigNode treeConfigNode : parent.getChildren().values()) {
			putDescendants(treeConfigNode, descendants);
		}
	}
	
	@Override
	public void setTreeConfigListener(TreeConfigListener listener) {
		this.listener = listener;
	}
}

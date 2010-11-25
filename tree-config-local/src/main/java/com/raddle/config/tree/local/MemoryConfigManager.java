/**
 * 
 */
package com.raddle.config.tree.local;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.raddle.config.tree.DefaultConfigNode;
import com.raddle.config.tree.DefaultConfigPath;
import com.raddle.config.tree.api.TreeConfigAttribute;
import com.raddle.config.tree.api.TreeConfigManager;
import com.raddle.config.tree.api.TreeConfigNode;
import com.raddle.config.tree.api.TreeConfigPath;

/**
 * @author xurong
 *
 */
public class MemoryConfigManager implements TreeConfigManager {
	private DefaultConfigNode root = new DefaultConfigNode();

	@Override
	public void removeAttributes(TreeConfigPath path, String... attributeNames) {
		TreeConfigNode n = getNodeByPath(path, false);
		if(n != null){
			for (String attributeName : attributeNames) {
				n.removeAttribute(attributeName);
			}
		}
	}

	@Override
	public boolean removeNode(TreeConfigPath path, boolean recursive) {
		DefaultConfigNode n = (DefaultConfigNode) getNodeByPath(path, false);
		if(n != null){
			if(n.getChildren().size() > 0){
				if(recursive){
					n.getParent().removeChild(n.getNodePath().getLast());
				} else {
					return false;
				}
			} else {
				n.getParent().removeChild(n.getNodePath().getLast());
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
		n.setAttributeValue(attribute.getName(), attribute.getValue());
	}

	@Override
	public void saveAttributeValue(TreeConfigPath path, String attributeName, Serializable value) {
		TreeConfigNode n = getNodeByPath(path, true);
		n.setAttributeValue(attributeName, value);
	}

	@Override
	public void saveNode(TreeConfigNode node, boolean updateNodeValue) {
		TreeConfigNode n = getNodeByPath(node.getNodePath(), true);
		// 如果节点已存在
		if (updateNodeValue) {
			// 更新节点值
			n.setValue(node.getValue());
		} else {
			// 更新属性值
			for (TreeConfigAttribute attribute : node.getAttributes()) {
				n.setAttributeValue(attribute.getName(), attribute.getValue());
			}
		}
	}

	@Override
	public void saveNodeValue(TreeConfigPath path, Serializable value) {
		TreeConfigNode n = getNodeByPath(path, true);
		n.setValue(value);
	}

	@Override
	public void saveNodes(List<TreeConfigNode> nodes, boolean updateNodeValue) {
		for (TreeConfigNode treeConfigNode : nodes) {
			saveNode(treeConfigNode, updateNodeValue);
		}
	}

	@Override
	public TreeConfigAttribute getAttribute(TreeConfigPath path, String attributeName) {
		TreeConfigNode node = getNode(path);
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
		DefaultConfigNode node = (DefaultConfigNode) getNode(path);
		if(node != null){
			return new ArrayList<TreeConfigNode>(node.getChildren().values());
		}
		return new ArrayList<TreeConfigNode>();
	}

	@Override
	public TreeConfigNode getNode(TreeConfigPath path) {
		return getNodeByPath(path, false);
	}

	@Override
	public Serializable getNodeValue(TreeConfigPath path) {
		TreeConfigNode node = getNode(path);
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

}

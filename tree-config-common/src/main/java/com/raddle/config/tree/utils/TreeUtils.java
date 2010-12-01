/**
 * 
 */
package com.raddle.config.tree.utils;

import java.util.List;

import com.raddle.config.tree.DefaultConfigNode;
import com.raddle.config.tree.DefaultConfigPath;
import com.raddle.config.tree.api.TreeConfigAttribute;
import com.raddle.config.tree.api.TreeConfigNode;
import com.raddle.config.tree.api.TreeConfigPath;

/**
 * @author xurong
 *
 */
public class TreeUtils {
	/**
	 * 将平铺的节点变成树形结构
	 * @param nodes
	 * @return root节点
	 */
	public static DefaultConfigNode toTree(List<TreeConfigNode> nodes){
		DefaultConfigNode root = new DefaultConfigNode();
		for (TreeConfigNode treeConfigNode : nodes) {
			saveNode(root, treeConfigNode);
		}
		return root;
	}
	
	private static void saveNode(DefaultConfigNode root ,TreeConfigNode node) {
		DefaultConfigNode current = null;
		DefaultConfigNode parent = root;
		// 创建带层次的节点
		for (int i = 0; i < node.getNodePath().getPath().length; i++) {
			// 根据段查找
			current = parent.getChild(node.getNodePath().getPath()[i]);
			if (current == null) {
				current = new DefaultConfigNode();
				DefaultConfigPath curPath = new DefaultConfigPath();
				curPath.setPath(node.getNodePath().getSubPath(0, i + 1));
				current.setNodePath(curPath);
				current.setParent(parent);
				parent.setChild(curPath.getLast(), current);
			}
			parent = current;
		}
		// 更新节点值
		current.setValue(node.getValue());
		// 更新属性值
		for (TreeConfigAttribute attribute : node.getAttributes()) {
			current.setAttributeValue(attribute.getName(), attribute.getValue());
		}
	}
	
	public static boolean isPathEquals(TreeConfigPath path1, TreeConfigPath path2){
		// TODO
		return false;
	}
	
	public static boolean isEqualOrDescendant(TreeConfigPath path1, TreeConfigPath path2){
		// TODO
		return false;
	}
}

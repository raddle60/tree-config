/**
 * 
 */
package com.raddle.config.tree.utils;

import java.util.List;

import org.apache.commons.lang.ObjectUtils;
import org.omg.CORBA.BooleanHolder;

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
	
	/**
	 * 将节点存入root节点，按层次结构
	 * @param root
	 * @param node
	 */
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
	
	/**
	 * path1和path2是否相等
	 * @param path1
	 * @param path2
	 * @return
	 */
	public static boolean isPathEquals(TreeConfigPath path1, TreeConfigPath path2) {
		String[] pathArray1 = path1.getPath();
		String[] pathArray2 = path2.getPath();
		if (pathArray1.length != pathArray2.length) {
			return false;
		}
		for (int i = 0; i < pathArray1.length; i++) {
			if (!pathArray1[i].equals(pathArray2[i])) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * path2是否和path1相等或是path1的子孙节点
	 * @param path1
	 * @param path2
	 * @return
	 */
	public static boolean isEqualOrDescendant(TreeConfigPath path1, TreeConfigPath path2) {
		String[] pathArray1 = path1.getPath();
		String[] pathArray2 = path2.getPath();
		if (pathArray1.length > pathArray2.length) {
			return false;
		}
		for (int i = 0; i < pathArray1.length; i++) {
			if (!pathArray1[i].equals(pathArray2[i])) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * 比较节点值，返回节点需要更新的值
	 * @param newNode 节点的新值
	 * @param oldNode 节点的原值
	 * @param updateNodeValue 是否需要更新节点的值，传入是否更新，传出比较结果，如果节点值一样，传出false
	 * @return 返回需要更新的值，如果不需要更新返回null
	 */
	public static TreeConfigNode getToUpdateNode(TreeConfigNode newNode, TreeConfigNode oldNode, BooleanHolder updateNodeValue) {
		DefaultConfigNode toUpdateNode = new DefaultConfigNode();
		toUpdateNode.setNodePath(newNode.getNodePath());
		toUpdateNode.setValue(newNode.getValue());
		if (oldNode == null) {
			for (TreeConfigAttribute attribute : newNode.getAttributes()) {
				toUpdateNode.setAttributeValue(attribute.getName(), attribute.getValue());
			}
			return toUpdateNode;
		} else {
			boolean hasChange = false;
			if (updateNodeValue.value) {
				if (!ObjectUtils.equals(newNode.getValue(), oldNode.getValue())) {
					hasChange = true;
				} else {
					updateNodeValue.value = false;
				}
			}
			for (TreeConfigAttribute attribute : newNode.getAttributes()) {
				if (!ObjectUtils.equals(attribute.getValue(), oldNode.getAttributeValue(attribute.getName()))) {
					toUpdateNode.setAttributeValue(attribute.getName(), attribute.getValue());
					hasChange = true;
				}
			}
			if (hasChange) {
				return toUpdateNode;
			} else {
				return null;
			}
		}
	}
}

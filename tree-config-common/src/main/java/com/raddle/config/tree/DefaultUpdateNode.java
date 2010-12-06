/**
 * 
 */
package com.raddle.config.tree;

import java.io.Serializable;

import com.raddle.config.tree.api.TreeConfigNode;

/**
 * @author xurong
 * 
 */
public class DefaultUpdateNode implements Serializable {
	private static final long serialVersionUID = 1L;
	private TreeConfigNode node;
	private boolean updateNodeValue = false;
	private boolean deleteNode = false;
	private boolean recursive = false;

	/**
	 * 删除节点
	 * @param node
	 * @param deleteNode 始终为true
	 * @param recursive
	 */
	public DefaultUpdateNode(TreeConfigNode node, boolean deleteNode, boolean recursive) {
		this.node = node;
		this.deleteNode = true;
		this.recursive = recursive;
	}

	/**
	 * 更新节点
	 * @param node
	 * @param updateNodeValue
	 */
	public DefaultUpdateNode(TreeConfigNode node, boolean updateNodeValue) {
		this.node = node;
		this.updateNodeValue = updateNodeValue;
		this.deleteNode = false;
	}

	public TreeConfigNode getNode() {
		return node;
	}

	public void setNode(TreeConfigNode node) {
		this.node = node;
	}

	public boolean isUpdateNodeValue() {
		return updateNodeValue;
	}

	public void setUpdateNodeValue(boolean updateNodeValue) {
		this.updateNodeValue = updateNodeValue;
	}

	public boolean isDeleteNode() {
		return deleteNode;
	}

	public void setDeleteNode(boolean deleteNode) {
		this.deleteNode = deleteNode;
	}

	public boolean isRecursive() {
		return recursive;
	}

	public void setRecursive(boolean recursive) {
		this.recursive = recursive;
	}
}

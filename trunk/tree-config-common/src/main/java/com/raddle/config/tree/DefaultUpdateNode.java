/**
 * 
 */
package com.raddle.config.tree;

import com.raddle.config.tree.api.TreeConfigNode;

/**
 * @author xurong
 *
 */
public class DefaultUpdateNode {
	private TreeConfigNode node;
	private boolean updateNodeValue;

	public DefaultUpdateNode(TreeConfigNode node, boolean updateNodeValue) {
		this.node = node;
		this.updateNodeValue = updateNodeValue;
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
}

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

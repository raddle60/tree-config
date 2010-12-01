/**
 * 
 */
package com.raddle.config.tree.api;

import java.io.Serializable;

/**
 * @author xurong
 * 
 */
public interface TreeConfigListener {
	/**
	 * 节点新增或修改
	 * @param node
	 * @param newValue
	 * @param oldValue
	 */
	public void nodeValueChanged(TreeConfigNode node, Serializable newValue, Serializable oldValue);

	/**
	 * 节点删除
	 * @param removedNode
	 */
	public void nodeRemoved(TreeConfigNode removedNode);

	/**
	 * 属性新增或修改
	 * @param node
	 * @param attribute
	 * @param newValue
	 * @param oldValue
	 */
	public void attributeValueChanged(TreeConfigNode node, TreeConfigAttribute attribute, Serializable newValue, Serializable oldValue);

	/**
	 * 属性删除
	 * @param node
	 * @param removedAttribute
	 */
	public void attributeRemoved(TreeConfigNode node, TreeConfigAttribute removedAttribute);
}

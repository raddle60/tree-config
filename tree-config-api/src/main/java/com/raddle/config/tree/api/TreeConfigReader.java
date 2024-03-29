/**
 * 
 */
package com.raddle.config.tree.api;

import java.io.Serializable;
import java.util.List;

/**
 * @author xurong
 * 
 */
public interface TreeConfigReader {
	/**
	 * 获得配置的节点
	 * @param path 节点路径
	 * @return 不存在返回null
	 */
	public TreeConfigNode getNode(TreeConfigPath path);

	/**
	 * 获得配置节点的值
	 * @param path 节点路径
	 * @return 不存在返回null
	 */
	public Serializable getNodeValue(TreeConfigPath path);

	/**
	 * 获得节点的属性
	 * @param path 节点路径
	 * @param attributeName 属性名称
	 * @return 不存在返回null
	 */
	public TreeConfigAttribute getAttribute(TreeConfigPath path, String attributeName);

	/**
	 * 获得节点属性的值
	 * @param path 节点路径
	 * @param attributeName 属性名称
	 * @return 不存在返回null
	 */
	public Serializable getAttributeValue(TreeConfigPath path, String attributeName);

	/**
	 * 获得子节点，只包括本节点的子节点，不包括子节点的子节点
	 * @param path 节点路径,传null获得第一级的节点
	 * @return 不存在返回空List
	 */
	public List<TreeConfigNode> getChildren(TreeConfigPath path);
	
	/**
	 * 获得子孙节点，包括子节点的子节点，直到末节点
	 * @param path 节点路径,传null获得第一级的节点
	 * @return 不存在返回空List
	 */
	public List<TreeConfigNode> getDescendants(TreeConfigPath path);
	
	/**
	 * 判断节点是否存在
	 * @param path 节点路径
	 * @return true存在，false不存在
	 */
	public boolean isNodeExist(TreeConfigPath path);
	
	/**
	 * 判断属性是否存在
	 * @param path 节点路径
	 * @param attributeNames 属性名称
	 * @return true全都存在，false只要有一个不存在
	 */
	public boolean isAttributesExist(TreeConfigPath path, String... attributeNames);
}

/**
 * 
 */
package com.raddle.config.tree.api;

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
	public Object getNodeValue(TreeConfigPath path);

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
	public Object getAttributeValue(TreeConfigPath path, String attributeName);

	/**
	 * 获得子节点
	 * @param path 节点路径
	 * @return 不存在返回空List
	 */
	public List<TreeConfigNode> getChildren(TreeConfigPath path);
}

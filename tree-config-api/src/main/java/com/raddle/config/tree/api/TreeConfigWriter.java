package com.raddle.config.tree.api;

import java.io.Serializable;
import java.util.List;

/**
 * @author xurong
 *
 */
public interface TreeConfigWriter {
	/**
	 * 保存节点，不存在就创建，已经存在就更新，不删除属性
	 * @param node 节点
	 * @param updateNodeValue false不更新node的值，只更新属性的值
	 */
	public void saveNode(TreeConfigNode node , boolean updateNodeValue);
	
	/**
	 * 保存节点，不存在就创建，已经存在就更新，不删除属性
	 * @param node 节点
	 * @param updateNodeValue false不更新node的值，只更新属性的值
	 */
	public void saveNodes(List<TreeConfigNode> nodes, boolean updateNodeValue);
	
	/**
	 * 保存节点的值，不存在就创建，已经存在就更新
	 * @param path 节点路径
	 * @param value 节点的值
	 */
	public void saveNodeValue(TreeConfigPath path, Serializable value);
	
	/**
	 * 保存节点的属性，不存在就创建（包括节点），已经存在就更新
	 * @param path 节点路径
	 * @param attribute 节点的属性
	 */
	public void saveAttribute(TreeConfigPath path, TreeConfigAttribute attribute);
	
	/**
	 * 保存节点的属性，不存在就创建（包括节点），已经存在就更新
	 * @param path 节点路径
	 * @param attributeName 属性名称
	 * @param value 属性值
	 */
	public void saveAttributeValue(TreeConfigPath path, String attributeName, Serializable value);
	
	/**
	 * 删除属性
	 * @param path 节点路径
	 * @param attributeNames 属性名称
	 */
	public void removeAttributes(TreeConfigPath path, String... attributeNames);
	
	/**
	 * 删除节点,如果recursive为false，node下有字节点，则不删除
	 * @param path 节点路径
	 * @param recursive true删除字节点，false不删除子节点
	 * @return 如果recursive为false，node下有字节点，返回false
	 */
	public boolean removeNode(TreeConfigPath path, boolean recursive);
	
	/**
	 * 删除节点,如果recursive为false，node下有字节点，则不删除，其他无子节点的仍然删除
	 * @param paths 节点路径
	 * @param recursive true删除字节点，false不删除子节点
	 * @return 如果recursive为false，只要有一个node下有字节点，返回false
	 */
	public boolean removeNodes(List<TreeConfigPath> paths, boolean recursive);
}

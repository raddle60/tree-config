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
public interface TreeConfigNode extends Serializable {
	/**
	 * 获得节点路径
	 * @return
	 */
	public TreeConfigPath getNodePath();
	
	/**
	 * 设置节点路径
	 */
	public void setNodePath(TreeConfigPath nodePath);
	
	/**
	 * 获得节点的值
	 * @return
	 */
	public Serializable getValue();
	
	/**
	 * 设置节点的值
	 */
	public void getValue(Serializable value);
	
	/**
	 * 获得属性
	 * @param attributeName 属性名称
	 * @return 没有返回null
	 */
	public TreeConfigAttribute getAttribute(String attributeName);
	
	/**
	 * 设置属性
	 * @param attributeName 属性名称
	 * @param attribute 属性
	 */
	public void setAttribute(String attributeName , TreeConfigAttribute attribute);
	
	/**
	 * 获得属性值
	 * @param attributeName 属性名称
	 * @return 没有返回null
	 */
	public Serializable getAttributeValue(String attributeName);
	
	/**
	 * 设置属性值
	 * @param attributeName 属性名称
	 * @param value 属性值
	 */
	public void setAttributeValue(String attributeName , Serializable value);
	
	/**
	 * 移除属性
	 * @param attributeName
	 */
	public void removeAttribute(String attributeName);
	
	/**
	 * 获得节点的所有属性
	 * @return 没有返回空List
	 */
	public List<TreeConfigAttribute> getAttributes();
}

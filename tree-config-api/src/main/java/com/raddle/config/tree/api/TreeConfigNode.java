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
	 * 获得节点的值
	 * @return
	 */
	public Serializable getValue();
	
	/**
	 * 获得属性
	 * @param attributeName 属性名称
	 * @return 没有返回null
	 */
	public TreeConfigAttribute getAttribute(String attributeName);
	
	/**
	 * 获得属性值
	 * @param attributeName 属性名称
	 * @return 没有返回null
	 */
	public Serializable getAttributeValue(String attributeName);
	
	/**
	 * 获得节点的所有属性
	 * @return 没有返回空List
	 */
	public List<TreeConfigAttribute> getAttributes();
}

/**
 * 
 */
package com.raddle.config.tree.api;

import java.io.Serializable;

/**
 * @author xurong
 *
 */
public interface TreeConfigAttribute extends Serializable {
	/**
	 * 获得属性名称
	 * @return
	 */
	public String getName();
	
	/**
	 * 获得属性值
	 * @return
	 */
	public Serializable getValue();
	
}

package com.raddle.config.tree.api;

import java.io.Serializable;

/**
 * @author xurong
 *
 */
public interface TreeConfigPath extends Serializable {
	/**
	 * 获得配置路径
	 * @return
	 */
	public String[] getPath();
}

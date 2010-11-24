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
	
	/**
	 * 获得部分路径，从start开始，到end-1
	 * @param start 从0开始
	 * @param end 从1开始
	 * @return
	 */
	public String[] getSubPath(int start, int end);
	
}

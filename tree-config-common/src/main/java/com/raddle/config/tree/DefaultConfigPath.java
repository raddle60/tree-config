/**
 * 
 */
package com.raddle.config.tree;

import java.util.Arrays;

import com.raddle.config.tree.api.TreeConfigPath;

/**
 * @author xurong <br>
 *         用反斜杠“/”分割的路径
 */
public class DefaultConfigPath implements TreeConfigPath {
	private static final long serialVersionUID = 1L;
	private String[] path;
	private String splitPath;

	public DefaultConfigPath() {
	}

	public DefaultConfigPath(String splitPath) {
		this.splitPath = splitPath;
		this.path = splitPath.split("/");
	}

	@Override
	public String[] getPath() {
		return path;
	}

	public void setPath(String[] path) {
		this.path = path;
	}

	public String getSplitPath() {
		return splitPath;
	}

	public void setSplitPath(String splitPath) {
		this.splitPath = splitPath;
		this.path = splitPath.split("/");
	}

	@Override
	public String[] getSubPath(int start, int end) {
		return Arrays.asList(path).subList(start, end).toArray(new String[0]);
	}

	@Override
	public String get(int i) {
		return path[i];
	}

	@Override
	public String getFirst() {
		return path[0];
	}

	@Override
	public String getLast() {
		return path[path.length - 1];
	}

}

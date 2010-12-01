/**
 * 
 */
package com.raddle.config.tree;

import java.io.Serializable;

import com.raddle.config.tree.api.TreeConfigPath;

/**
 * @author xurong
 *
 */
public class DefaultNodeSelector implements Serializable {
	private static final long serialVersionUID = 1L;
	private TreeConfigPath path;
	private boolean recursive;

	public DefaultNodeSelector(TreeConfigPath path, boolean recursive) {
		this.path = path;
		this.recursive = recursive;
	}

	public TreeConfigPath getPath() {
		return path;
	}

	public void setPath(TreeConfigPath path) {
		this.path = path;
	}

	public boolean isRecursive() {
		return recursive;
	}

	public void setRecursive(boolean recursive) {
		this.recursive = recursive;
	}
}

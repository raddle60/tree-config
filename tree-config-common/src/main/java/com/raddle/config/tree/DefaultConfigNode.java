/**
 * 
 */
package com.raddle.config.tree;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.raddle.config.tree.api.TreeConfigAttribute;
import com.raddle.config.tree.api.TreeConfigNode;
import com.raddle.config.tree.api.TreeConfigPath;

/**
 * @author xurong
 * 
 */
public class DefaultConfigNode implements TreeConfigNode {

	private static final long serialVersionUID = 1L;

	private Map<String, TreeConfigAttribute> attrMap = new LinkedHashMap<String, TreeConfigAttribute>();

	private Serializable value;

	private Map<String, DefaultConfigNode> children = new LinkedHashMap<String, DefaultConfigNode>();
	
	private TreeConfigPath nodePath;
	
	private DefaultConfigNode parent;
	
	/**
	 * 只保留value和属性，去掉父节点和子节点的关联信息
	 * @return
	 */
	public TreeConfigNode toSelfOnly() {
		DefaultConfigNode node = new DefaultConfigNode();
		node.setNodePath(nodePath);
		node.setValue(this.getValue());
		for (TreeConfigAttribute attribute : attrMap.values()) {
			node.setAttributeValue(attribute.getName(), attribute.getValue());
		}
		return node;
	}

	@Override
	public TreeConfigAttribute getAttribute(String attributeName) {
		return attrMap.get(attributeName);
	}
	
	@Override
	public void setAttribute(String attributeName , TreeConfigAttribute attribute){
		attrMap.put(attributeName, attribute);
	}

	@Override
	public Serializable getAttributeValue(String attributeName) {
		TreeConfigAttribute attribute = getAttribute(attributeName);
		if (attribute != null) {
			return attribute.getValue();
		}
		return null;
	}
	
	@Override
	public void setAttributeValue(String attributeName , Serializable value){
		TreeConfigAttribute attribute = getAttribute(attributeName);
		if (attribute != null) {
			attribute.setValue(value);
		} else {
			attrMap.put(attributeName, new DefaultConfigAttribute(attributeName, value));
		}
	}

	@Override
	public List<TreeConfigAttribute> getAttributes() {
		return new ArrayList<TreeConfigAttribute>(attrMap.values());
	}

	@Override
	public TreeConfigPath getNodePath() {
		return nodePath;
	}

	@Override
	public Serializable getValue() {
		return value;
	}

	public DefaultConfigNode getChild(String nodeName) {
		return children.get(nodeName);
	}
	
	public void setChild(String nodeName, DefaultConfigNode child){
		child.setParent(this);
		children.put(nodeName, child);
	}
	
	public void removeChild(String nodeName){
		children.remove(nodeName);
	}
	
	public Map<String, DefaultConfigNode> getChildren() {
		return children;
	}

	@Override
	public void setValue(Serializable value) {
		this.value = value;
	}

	@Override
	public void removeAttribute(String attributeName) {
		attrMap.remove(attributeName);
	}

	public void setNodePath(TreeConfigPath nodePath) {
		this.nodePath = nodePath;
	}

	public DefaultConfigNode getParent() {
		return parent;
	}

	public void setParent(DefaultConfigNode parent) {
		this.parent = parent;
	}

}

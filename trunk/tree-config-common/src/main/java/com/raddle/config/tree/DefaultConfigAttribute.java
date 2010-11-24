package com.raddle.config.tree;

import java.io.Serializable;

import com.raddle.config.tree.api.TreeConfigAttribute;

/**
 * @author xurong
 * 
 */
public class DefaultConfigAttribute implements TreeConfigAttribute {

	private static final long serialVersionUID = 1L;
	private String name;
	private Serializable value;

	public DefaultConfigAttribute() {
	}

	public DefaultConfigAttribute(String name, Serializable value) {
		this.name = name;
		this.value = value;
	}

	@Override
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public Serializable getValue() {
		return value;
	}

	@Override
	public void setValue(Serializable value) {
		this.value = value;
	}

}

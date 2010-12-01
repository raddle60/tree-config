package com.raddle.config.tree.local;

import java.io.Serializable;

import com.raddle.config.tree.api.TreeConfigAttribute;
import com.raddle.config.tree.api.TreeConfigListener;
import com.raddle.config.tree.api.TreeConfigNode;

public class TreeConfigListenerAdapter implements TreeConfigListener {

	@Override
	public void attributeValueChanged(TreeConfigNode node, TreeConfigAttribute attribute, Serializable newValue, Serializable oldValue) {

	}

	@Override
	public void attributeRemoved(TreeConfigNode node, TreeConfigAttribute removedAttribute) {

	}

	@Override
	public void nodeRemoved(TreeConfigNode removedNode) {

	}

	@Override
	public void nodeValueChanged(TreeConfigNode node, Serializable newValue, Serializable oldValue) {

	}

}

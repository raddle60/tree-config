/**
 * 
 */
package com.raddle.config.tree.server;

import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.apache.mina.core.session.IoSession;

import com.raddle.config.tree.DefaultNodeSelector;
import com.raddle.config.tree.DefaultUpdateNode;

/**
 * @author xurong
 * 
 */
public class ClientContext {

	private String clientId;
	private Set<DefaultNodeSelector> selectors = new HashSet<DefaultNodeSelector>();
	private Set<DefaultUpdateNode> disconnectedValues = new HashSet<DefaultUpdateNode>();
	private Deque<NotifyClientTask> notifyTasks = new LinkedList<NotifyClientTask>();
	private IoSession session;

	public ClientContext(String clientId) {
		this.clientId = clientId;
	}

	public ClientContext(String clientId, IoSession session) {
		this.clientId = clientId;
		this.session = session;
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public Set<DefaultNodeSelector> getSelectors() {
		return selectors;
	}

	public void setSelectors(Set<DefaultNodeSelector> selectors) {
		this.selectors = selectors;
	}

	public Set<DefaultUpdateNode> getDisconnectedValues() {
		return disconnectedValues;
	}

	public void setDisconnectedValues(Set<DefaultUpdateNode> disconnectedValues) {
		this.disconnectedValues = disconnectedValues;
	}

	public Deque<NotifyClientTask> getNotifyTasks() {
		return notifyTasks;
	}

	public void setNotifyTasks(Deque<NotifyClientTask> notifyTasks) {
		this.notifyTasks = notifyTasks;
	}

	public IoSession getSession() {
		return session;
	}

	public void setSession(IoSession session) {
		this.session = session;
	}
}

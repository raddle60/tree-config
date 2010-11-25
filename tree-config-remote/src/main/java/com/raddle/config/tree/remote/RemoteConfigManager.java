/**
 * 
 */
package com.raddle.config.tree.remote;

import java.io.Serializable;
import java.util.List;

import org.apache.mina.core.session.IoSession;

import com.raddle.config.tree.api.TreeConfigAttribute;
import com.raddle.config.tree.api.TreeConfigManager;
import com.raddle.config.tree.api.TreeConfigNode;
import com.raddle.config.tree.api.TreeConfigPath;
import com.raddle.nio.mina.cmd.SessionCommandSender;
import com.raddle.nio.mina.cmd.api.CommandCallback;
import com.raddle.nio.mina.cmd.api.CommandSender;
import com.raddle.nio.mina.cmd.invoke.InvokeCommand;

/**
 * @author xurong
 * 
 */
public class RemoteConfigManager implements TreeConfigManager {
	private int timeoutSeconds = 3;
	private CommandSender commandSender;

	public RemoteConfigManager(IoSession session) {
		this.commandSender = new SessionCommandSender(session);
	}

	private Object sendCommand(String method, Object[] args) {
		final ObjectHolder ret = new ObjectHolder();
		final ObjectHolder exception = new ObjectHolder();
		InvokeCommand command = new InvokeCommand();
		command.setTargetId("treeConfigManager");
		command.setMethod(method);
		command.setArgs(args);
		commandSender.sendCommand(command, timeoutSeconds, new CommandCallback<InvokeCommand, Object>() {

			@Override
			public void commandResponse(InvokeCommand command, Object response) {
				ret.setValue(response);
				synchronized (ret) {
					ret.notify();
				}
			}

			@Override
			public void responseException(InvokeCommand command, String type, String message) {
				exception.setValue("方法调用返回异常[" + type + "]:" + message);
				synchronized (ret) {
					ret.notify();
				}
			}

			@Override
			public void responseTimeout(InvokeCommand command) {
				exception.setValue("方法调用返回超时,超时时间" + timeoutSeconds + "秒");
				synchronized (ret) {
					ret.notify();
				}
			}

		});
		// 等待结果返回
		synchronized (ret) {
			try {
				ret.wait(timeoutSeconds * 1000 + 500);
			} catch (InterruptedException e) {
				throw new RuntimeException(e.getMessage(), e);
			}
		}
		// 调用异常
		if (exception.getValue() != null) {
			throw new RuntimeException(exception.getValue() + "");
		}
		return ret.getValue();
	}

	@Override
	public void removeAttributes(TreeConfigPath path, String... attributeNames) {
		sendCommand("removeAttributes", new Object[] { path, attributeNames });
	}

	@Override
	public boolean removeNode(TreeConfigPath path, boolean recursive) {
		return (Boolean) sendCommand("removeNode", new Object[] { path, recursive });
	}

	@Override
	public boolean removeNodes(List<TreeConfigPath> paths, boolean recursive) {
		return (Boolean) sendCommand("removeNodes", new Object[] { paths, recursive });
	}

	@Override
	public void saveAttribute(TreeConfigPath path, TreeConfigAttribute attribute) {
		sendCommand("saveAttribute", new Object[] { path, attribute });
	}

	@Override
	public void saveAttributeValue(TreeConfigPath path, String attributeName, Serializable value) {
		sendCommand("saveAttributeValue", new Object[] { path, attributeName, value });
	}

	@Override
	public void saveNode(TreeConfigNode node, boolean updateNodeValue) {
		sendCommand("saveNode", new Object[] { node, updateNodeValue });
	}

	@Override
	public void saveNodeValue(TreeConfigPath path, Serializable value) {
		sendCommand("saveNodeValue", new Object[] { path, value });
	}

	@Override
	public void saveNodes(List<TreeConfigNode> nodes, boolean updateNodeValue) {
		sendCommand("saveNodes", new Object[] { nodes, updateNodeValue });
	}

	@Override
	public TreeConfigAttribute getAttribute(TreeConfigPath path, String attributeName) {
		return (TreeConfigAttribute) sendCommand("getAttribute", new Object[] { path, attributeName });
	}

	@Override
	public Serializable getAttributeValue(TreeConfigPath path, String attributeName) {
		return (Serializable) sendCommand("getAttributeValue", new Object[] { path, attributeName });
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<TreeConfigNode> getChildren(TreeConfigPath path) {
		return (List<TreeConfigNode>) sendCommand("getChildren", new Object[] { path });
	}

	@Override
	public TreeConfigNode getNode(TreeConfigPath path) {
		return (TreeConfigNode) sendCommand("getNode", new Object[] { path });
	}

	@Override
	public Serializable getNodeValue(TreeConfigPath path) {
		return (Serializable) sendCommand("getNodeValue", new Object[] { path });
	}

	@Override
	public boolean isAttributesExist(TreeConfigPath path, String... attributeNames) {
		return (Boolean) sendCommand("isAttributesExist", new Object[] { path, attributeNames });
	}

	@Override
	public boolean isNodeExist(TreeConfigPath path) {
		return (Boolean) sendCommand("isNodeExist", new Object[] { path });
	}

	public int getTimeoutSeconds() {
		return timeoutSeconds;
	}

	public void setTimeoutSeconds(int timeoutSeconds) {
		this.timeoutSeconds = timeoutSeconds;
	}

	class ObjectHolder {
		private Object value;

		public Object getValue() {
			return value;
		}

		public void setValue(Object value) {
			this.value = value;
		}
	}
}

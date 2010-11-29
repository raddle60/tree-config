/**
 * 
 */
package com.raddle.config.tree.client.impl;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.raddle.config.tree.api.TreeConfigAttribute;
import com.raddle.config.tree.api.TreeConfigManager;
import com.raddle.config.tree.api.TreeConfigNode;
import com.raddle.config.tree.api.TreeConfigPath;
import com.raddle.config.tree.client.TreeConfigClient;
import com.raddle.config.tree.local.MemoryConfigManager;
import com.raddle.config.tree.remote.RemoteConfigManager;
import com.raddle.config.tree.utils.InvokeUtils;
import com.raddle.nio.mina.cmd.invoke.AbstractInvokeCommandHandler;
import com.raddle.nio.mina.cmd.invoke.MethodInvoke;
import com.raddle.nio.mina.hessian.HessianDecoder;
import com.raddle.nio.mina.hessian.HessianEncoder;

/**
 * @author xurong
 * 
 */
public class DefaultTreeConfigClient implements TreeConfigClient {
	private static final Logger logger = LoggerFactory.getLogger(DefaultTreeConfigClient.class);
	private String clientId = UUID.randomUUID().toString();
	private String serverIp;
	private int serverPort;
	private long connectTimeoutMs = 3000;
	private TreeConfigManager localManager = new MemoryConfigManager();
	private TreeConfigManager remoteManager = null;
	private NioSocketConnector connector = null;
	private List<NodePath> initialGetNodes = new LinkedList<NodePath>();
	private List<NodePath> initialPushNodes = new LinkedList<NodePath>();
	private List<UpdateNode> disconnectedNodes = new LinkedList<UpdateNode>();
	private SocketAddress localAddress = null;

	public DefaultTreeConfigClient(String clientId, String serverIp, int serverPort) {
		this.clientId = clientId;
		this.serverIp = serverIp;
		this.serverPort = serverPort;
	}

	public DefaultTreeConfigClient(String serverIp, int serverPort) {
		this.serverIp = serverIp;
		this.serverPort = serverPort;
	}

	public synchronized void connect() {
		if (connector == null) {
			logger.info("initialize nio-connector");
			connector = new NioSocketConnector();
			logger.info("setting connect timeout {}ms", connectTimeoutMs);
			connector.setConnectTimeoutMillis(connectTimeoutMs);
			connector.getFilterChain().addFirst("binaryCodec", new ProtocolCodecFilter(new HessianEncoder(), new HessianDecoder()));
			// 处理接收的命令和响应
			connector.setHandler(new AbstractInvokeCommandHandler() {
				@Override
				protected Object invokeMethod(MethodInvoke methodInvoke) throws Exception {
					return InvokeUtils.invokeMethod(methodInvoke.getTarget(), methodInvoke.getMethod(), methodInvoke.getArgs());
				}

				@Override
				protected Object getObject(String id) {
					return localManager;
				}

				@Override
				public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
					logger.error("Session exception , remote address [" + session.getRemoteAddress() + "] .", cause);
					session.close(true);
				}

				@Override
				public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
					logger.warn("Session idle timeout , idle status [{}] , remote address [{}] .",
							new Object[] { status, session.getRemoteAddress() });
					session.close(true);
				}

				@Override
				public void sessionClosed(IoSession session) throws Exception {
					logger.debug("Session closed , remote address [{}] .", session.getRemoteAddress());
					remoteManager = null;
				}

				@Override
				public void sessionCreated(IoSession session) throws Exception {
					logger.debug("Session created , remote address [{}] .", session.getRemoteAddress());
					remoteManager = new RemoteConfigManager(session);
					localAddress = session.getLocalAddress();
				}
			});
			logger.info("connecting to {}:{}", serverIp, serverPort);
			ConnectFuture future = connector.connect(new InetSocketAddress(serverIp, serverPort));
			future.awaitUninterruptibly();
			future.getSession();
			logger.info("connected to {}:{}", serverIp, serverPort);
		}
	}

	public synchronized void close() {
		if (connector != null) {
			connector.dispose();
			connector = null;
		}
	}

	@Override
	public void bindDisconnectedNodes(List<TreeConfigNode> nodes, boolean includeNodeValue) {
		for (TreeConfigNode treeConfigNode : nodes) {
			disconnectedNodes.add(new UpdateNode(treeConfigNode, includeNodeValue));
		}
	}

	@Override
	public void bindInitialPushNodes(List<TreeConfigPath> paths, boolean recursive) {
		for (TreeConfigPath treeConfigPath : paths) {
			initialPushNodes.add(new NodePath(treeConfigPath, recursive));
		}
	}
	
	@Override
	public void bindInitialGetNodes(List<TreeConfigPath> paths, boolean recursive) {
		for (TreeConfigPath treeConfigPath : paths) {
			initialGetNodes.add(new NodePath(treeConfigPath, recursive));
		}
	}
	
	@Override
	public void removeAttributes(TreeConfigPath path, String... attributeNames) {
		localManager.removeAttributes(path, attributeNames);
		if (remoteManager != null) {
			remoteManager.removeAttributes(path, attributeNames);
		}
	}

	@Override
	public boolean removeNode(TreeConfigPath path, boolean recursive) {
		boolean ret = localManager.removeNode(path, recursive);
		if (ret && remoteManager != null) {
			remoteManager.removeNode(path, recursive);
		}
		return ret;
	}

	@Override
	public boolean removeNodes(List<TreeConfigPath> paths, boolean recursive) {
		boolean ret = localManager.removeNodes(paths, recursive);
		if (ret && remoteManager != null) {
			remoteManager.removeNodes(paths, recursive);
		}
		return ret;
	}

	@Override
	public void saveAttribute(TreeConfigPath path, TreeConfigAttribute attribute) {
		localManager.saveAttribute(path, attribute);
		if (remoteManager != null) {
			remoteManager.saveAttribute(path, attribute);
		}
	}

	@Override
	public void saveAttributeValue(TreeConfigPath path, String attributeName, Serializable value) {
		localManager.saveAttributeValue(path, attributeName, value);
		if (remoteManager != null) {
			remoteManager.saveAttributeValue(path, attributeName, value);
		}
	}

	@Override
	public void saveNode(TreeConfigNode node, boolean updateNodeValue) {
		localManager.saveNode(node, updateNodeValue);
		if (remoteManager != null) {
			remoteManager.saveNode(node, updateNodeValue);
		}
	}

	@Override
	public void saveNodeValue(TreeConfigPath path, Serializable value) {
		localManager.saveNodeValue(path, value);
		if (remoteManager != null) {
			remoteManager.saveNodeValue(path, value);
		}
	}

	@Override
	public void saveNodes(List<TreeConfigNode> nodes, boolean updateNodeValue) {
		localManager.saveNodes(nodes, updateNodeValue);
		if (remoteManager != null) {
			remoteManager.saveNodes(nodes, updateNodeValue);
		}
	}

	@Override
	public TreeConfigAttribute getAttribute(TreeConfigPath path, String attributeName) {
		TreeConfigAttribute attribute = localManager.getAttribute(path, attributeName);
		if(attribute == null && remoteManager != null){
			attribute = remoteManager.getAttribute(path, attributeName);
			if(attribute != null){
				localManager.saveAttribute(path, attribute);
			}
		}
		return attribute;
	}

	@Override
	public Serializable getAttributeValue(TreeConfigPath path, String attributeName) {
		Serializable value = localManager.getAttributeValue(path, attributeName);
		if(value == null && remoteManager != null){
			value = remoteManager.getAttributeValue(path, attributeName);
			if(value != null){
				localManager.saveAttributeValue(path, attributeName, value);
			}
		}
		return value;
	}

	@Override
	public List<TreeConfigNode> getChildren(TreeConfigPath path) {
		List<TreeConfigNode> children = localManager.getChildren(path);
		if(children.size() == 0 && remoteManager != null){
			children = remoteManager.getChildren(path);
			if(children.size() > 0 ){
				localManager.saveNodes(children, true);
			}
		}
		return children;
	}

	@Override
	public TreeConfigNode getNode(TreeConfigPath path) {
		TreeConfigNode node = localManager.getNode(path);
		if(node == null && remoteManager != null){
			node = remoteManager.getNode(path);
			if(node != null){
				localManager.saveNode(node, true);
			}
		}
		return node;
	}

	@Override
	public Serializable getNodeValue(TreeConfigPath path) {
		Serializable value = localManager.getNodeValue(path);
		if(value == null && remoteManager != null){
			value = remoteManager.getNodeValue(path);
			if(value != null){
				localManager.saveNodeValue(path, value);
			}
		}
		return value;
	}

	@Override
	public boolean isAttributesExist(TreeConfigPath path, String... attributeNames) {
		boolean exist = localManager.isAttributesExist(path, attributeNames);
		if (!exist && remoteManager != null) {
			exist = remoteManager.isAttributesExist(path, attributeNames);
			if(exist){
				for (String attributeName : attributeNames) {
					TreeConfigAttribute attribute = remoteManager.getAttribute(path, attributeName);
					localManager.saveAttribute(path, attribute);
				}
			}
		}
		return exist;
	}

	@Override
	public boolean isNodeExist(TreeConfigPath path) {
		boolean exist = localManager.isNodeExist(path);
		if (!exist && remoteManager != null) {
			exist = remoteManager.isNodeExist(path);
			if (exist) {
				TreeConfigNode node = remoteManager.getNode(path);
				localManager.saveNode(node, true);
			}
		}
		return exist;
	}

	public TreeConfigManager getLocalManager() {
		return localManager;
	}

	public void setLocalManager(TreeConfigManager localManager) {
		this.localManager = localManager;
	}

	public String getClientId() {
		return clientId;
	}

	public String getServerIp() {
		return serverIp;
	}

	public int getServerPort() {
		return serverPort;
	}

	public long getConnectTimeoutMs() {
		return connectTimeoutMs;
	}

	public void setConnectTimeoutMs(long connectTimeoutMs) {
		this.connectTimeoutMs = connectTimeoutMs;
	}

	public TreeConfigManager getRemoteManager() {
		return remoteManager;
	}

	public void setRemoteManager(TreeConfigManager remoteManager) {
		this.remoteManager = remoteManager;
	}

	class UpdateNode {
		private TreeConfigNode node;
		private boolean updateNodeValue;

		public UpdateNode(TreeConfigNode node, boolean updateNodeValue) {
			this.node = node;
			this.updateNodeValue = updateNodeValue;
		}

		public TreeConfigNode getNode() {
			return node;
		}

		public void setNode(TreeConfigNode node) {
			this.node = node;
		}

		public boolean isUpdateNodeValue() {
			return updateNodeValue;
		}

		public void setUpdateNodeValue(boolean updateNodeValue) {
			this.updateNodeValue = updateNodeValue;
		}
	}

	class NodePath {
		private TreeConfigPath path;
		private boolean recursive;

		public NodePath(TreeConfigPath path, boolean recursive) {
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

	public SocketAddress getLocalAddress() {
		return localAddress;
	}

}

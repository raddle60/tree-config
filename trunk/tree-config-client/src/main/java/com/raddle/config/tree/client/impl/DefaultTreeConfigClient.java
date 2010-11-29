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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
import com.raddle.nio.mina.cmd.SessionCommandSender;
import com.raddle.nio.mina.cmd.invoke.AbstractInvokeCommandHandler;
import com.raddle.nio.mina.cmd.invoke.InvokeCommand;
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
	private ExecutorService taskExecutor = null;
	private ScheduledExecutorService syncSchedule = null;
	private ScheduledExecutorService pingSchedule = null;
	private int syncSeconds = 60 * 10;
	private int pingSeconds = 60;

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
			logger.info("initialize executors");
			taskExecutor = new ThreadPoolExecutor(0, 5, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
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
					taskExecutor.execute(new SyncTask());
				}
			});
			logger.info("connecting to {}:{}", serverIp, serverPort);
			ConnectFuture future = connector.connect(new InetSocketAddress(serverIp, serverPort));
			future.awaitUninterruptibly();
			try {
				IoSession session = future.getSession();
				logger.info("connected to {}", session.getRemoteAddress());
			} catch (Exception e) {
				logger.warn("connecting to {}:{} failed." , serverIp, serverPort);
			}
			logger.info("synchronize nodes per {} seconds" , syncSeconds);
			syncSchedule = Executors.newScheduledThreadPool(1);
			syncSchedule.scheduleWithFixedDelay(new SyncTask(), syncSeconds, syncSeconds, TimeUnit.SECONDS);
			logger.info("ping server per {} seconds" , pingSeconds);
			pingSchedule = Executors.newScheduledThreadPool(1);
			pingSchedule.scheduleWithFixedDelay(new PingTask(), pingSeconds, pingSeconds, TimeUnit.SECONDS);
			logger.info("client initialize completed");
		}
	}

	public synchronized void close() {
		if(taskExecutor != null){
			logger.info("task executor shuting down");
			taskExecutor.shutdown();
		}
		if(syncSchedule != null){
			logger.info("sync schedule shuting down");
			syncSchedule.shutdown();
		}
		if(pingSchedule != null){
			logger.info("ping schedule shuting down");
			pingSchedule.shutdown();
		}
		if (connector != null) {
			logger.info("connector disposing");
			connector.dispose();
			connector = null;
		}
		logger.info("tree config client close complete .");
	}

	@Override
	public void bindDisconnectedNode(TreeConfigNode node, boolean includeNodeValue) {
		disconnectedNodes.add(new UpdateNode(node, includeNodeValue));
	}

	@Override
	public void bindInitialPushNodes(TreeConfigPath path, boolean recursive) {
		initialPushNodes.add(new NodePath(path, recursive));
	}
	
	@Override
	public void bindInitialGetNodes(TreeConfigPath path, boolean recursive) {
		initialGetNodes.add(new NodePath(path, recursive));
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
	
	public SocketAddress getLocalAddress() {
		return localAddress;
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

	class SyncTask implements Runnable {
		
		@Override
		public void run() {
			try {
				if (connector.getManagedSessionCount() > 0) {
					logger.debug("register client , clientId : {}", clientId);
					IoSession session = connector.getManagedSessions().values().iterator().next();
					SessionCommandSender sender = new SessionCommandSender(session);
					InvokeCommand registerClient = new InvokeCommand();
					registerClient.setTargetId("treeConfigRegister");
					registerClient.setMethod("registerClient");
					registerClient.setArgs(new Object[] { clientId });
					sender.sendCommand(registerClient);
					logger.debug("binding disconnected value");
					for (UpdateNode updateNode : disconnectedNodes) {
						InvokeCommand bindingValue = new InvokeCommand();
						bindingValue.setTargetId("treeConfigBinder");
						bindingValue.setMethod("bindingDisconnectedValue");
						bindingValue.setArgs(new Object[] { updateNode.getNode(), updateNode.isUpdateNodeValue() });
						sender.sendCommand(bindingValue);
					}
					logger.debug("setting push nodes");
					List<TreeConfigNode> pushNodes = new LinkedList<TreeConfigNode>();
					for (NodePath nodePath : initialPushNodes) {
						putPushNodes(nodePath.getPath(), nodePath.isRecursive(), pushNodes);
					}
					InvokeCommand pushNodeCmd = new InvokeCommand();
					pushNodeCmd.setTargetId("treeConfigManager");
					pushNodeCmd.setMethod("saveNodes");
					pushNodeCmd.setArgs(new Object[] { pushNodes, true });
					sender.sendCommand(pushNodeCmd);
				}
				if(remoteManager != null){
					logger.debug("getting initial nodes");
					for (NodePath nodePath : initialGetNodes) {
						freshNode(nodePath.getPath() ,nodePath.isRecursive());
					}
				}
			} catch (Throwable e) {
				logger.error(e.getMessage(), e);
			}
		}

		private void putPushNodes(TreeConfigPath path, boolean recursive, List<TreeConfigNode> pushNodes) {
			if(path == null){
				if(recursive){
					List<TreeConfigNode> children = localManager.getChildren(path);
					for (TreeConfigNode treeConfigNode : children) {
						putPushNodes(treeConfigNode.getNodePath(), recursive, pushNodes);
					}
				}
			} else {
				TreeConfigNode node = localManager.getNode(path);
				if (node != null) {
					pushNodes.add(node);
					if(recursive){
						List<TreeConfigNode> children = localManager.getChildren(node.getNodePath());
						for (TreeConfigNode treeConfigNode : children) {
							putPushNodes(treeConfigNode.getNodePath(), recursive, pushNodes);
						}
					}
				}
			}
		}

		private void freshNode(TreeConfigPath path, boolean recursive) {
			if(path == null){
				if (recursive) {
					List<TreeConfigNode> children = remoteManager.getChildren(path);
					freshNodes(children, recursive);
				}
			} else {
				TreeConfigNode parent = remoteManager.getNode(path);
				if (parent != null) {
					localManager.saveNode(parent, true);
					if (recursive) {
						List<TreeConfigNode> children = remoteManager.getChildren(path);
						freshNodes(children, recursive);
					}
				}
			}
		}
		
		private void freshNodes(List<TreeConfigNode> nodes, boolean recursive) {
			if(nodes.size() > 0){
				localManager.saveNodes(nodes, true);
				if (recursive) {
					for (TreeConfigNode treeConfigNode : nodes) {
						List<TreeConfigNode> children = remoteManager.getChildren(treeConfigNode.getNodePath());
						freshNodes(children , recursive);
					}
				}
			}
		}
	}
	
	class PingTask implements Runnable {
		
		@Override
		public void run() {
			try {
				logger.debug("ping server");
				if (connector.getManagedSessionCount() > 0) {
					IoSession session = connector.getManagedSessions().values().iterator().next();
					SessionCommandSender sender = new SessionCommandSender(session);
					sender.sendCommand("ping");
				}
			} catch (Throwable e) {
				logger.error(e.getMessage(), e);
			}
		}
	}

	public int getSyncSeconds() {
		return syncSeconds;
	}

	public void setSyncSeconds(int syncSeconds) {
		this.syncSeconds = syncSeconds;
	}

	public int getPingSeconds() {
		return pingSeconds;
	}

	public void setPingSeconds(int pingSeconds) {
		this.pingSeconds = pingSeconds;
	};

}
/**
 * 
 */
package com.raddle.config.tree.client.impl;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.raddle.config.tree.remote.SyncCommandSender;
import com.raddle.config.tree.remote.exception.RemoteExecuteException;
import com.raddle.config.tree.utils.ExceptionUtils;
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
	private AtomicInteger currentReaderCount = new AtomicInteger(0);
	private Object writeLock = new Object();
	private TreeConfigManager localManager = new MemoryConfigManager();
	private TreeConfigManager remoteManager = null;
	private NioSocketConnector connector = null;
	private List<NodePath> initialGetNodes = new LinkedList<NodePath>();
	private List<NodePath> initialPushNodes = new LinkedList<NodePath>();
	private List<UpdateNode> disconnectedNodes = new LinkedList<UpdateNode>();
	private Deque<InvokeCommand> notifyTask = new LinkedList<InvokeCommand>();
	private SocketAddress localAddress = null;
	private ExecutorService taskExecutor = null;
	private ScheduledExecutorService pingSchedule = null;
	private int pingSeconds = 60;
	private TreeConfigClientListener listener = null;

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
					if(listener != null){
						listener.commandReceived(methodInvoke);
					}
					synchronized (writeLock) {
						// 等待所有读操作结束
						while (currentReaderCount.get() > 0) {
							Thread.sleep(50);
						}
						return InvokeUtils.invokeMethod(methodInvoke.getTarget(), methodInvoke.getMethod(), methodInvoke.getArgs());
					}
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
					// 断开连接后会重新初始化，以前的通知已经无效
					notifyTask.clear();
				}

				@Override
				public void sessionCreated(IoSession session) throws Exception {
					logger.debug("Session created , remote address [{}] .", session.getRemoteAddress());
					remoteManager = new RemoteConfigManager(session);
					localAddress = session.getLocalAddress();
					if(listener != null){
						listener.sessionConnected(session);
					}
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
			logger.info("ping server per {} seconds" , pingSeconds);
			pingSchedule = Executors.newScheduledThreadPool(1);
			pingSchedule.scheduleWithFixedDelay(new PingTask(), pingSeconds, pingSeconds, TimeUnit.SECONDS);
			// 保持连接
			logger.info("starting reconnect thread");
			Thread checkConnectionThread = new Thread(){

				@Override
				public void run() {
					// 断开连接后重连
					while (connector != null && connector.getManagedSessionCount() == 0) {
						try {
							ConnectFuture future = connector.connect(new InetSocketAddress(serverIp, serverPort));
							future.awaitUninterruptibly();
							future.getSession();
						} catch (Exception e) {
							logger.error("connecting to {}:{} faild , because of {}: {}", new Object[] { serverIp, serverPort, ExceptionUtils.getRootCause(e).getClass(), ExceptionUtils.getRootCause(e).getMessage() });
						}
						try {
							Thread.sleep(2000);
						} catch (InterruptedException e) {
							logger.warn(e.getMessage(), e);
						}
					}
				}
			};
			checkConnectionThread.setDaemon(true);
			checkConnectionThread.start();
			// 发送通知
			logger.info("starting notify thread");
			Thread notifyThread = new Thread(){
				@Override
				public void run() {
					while (true) {
						InvokeCommand command = null;
						synchronized (notifyTask) {
							if(notifyTask.size() > 0){
								command = notifyTask.pollFirst();
							} else {
								try {
									notifyTask.wait();
								} catch (InterruptedException e) {
									logger.warn(e.getMessage(), e);
								}
							}
						}
						try {
							InvokeUtils.invokeMethod(remoteManager, command.getMethod(), command.getArgs());
						} catch (RemoteExecuteException e) {
							// 远端的异常，忽略
						} catch (Exception e) {
							logger.error(e.getMessage(), e);
							// 失败了放回队列头，重新发送。
							// 不能在这循环发送，因为连接断开后任务会被清空，这个失败的任务也会被清除
							// 在这循环将无法被清除
							notifyTask.addFirst(command);
							// 失败了10秒以后再试
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e1) {
								logger.warn(e1.getMessage(), e1);
							}
						}
					}
				}
			};
			notifyThread.setDaemon(true);
			notifyThread.start();
			logger.info("client initialize completed");
		}
	}

	public synchronized void close() {
		if(taskExecutor != null){
			logger.info("task executor shuting down");
			taskExecutor.shutdown();
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
		addNotifyTask("treeConfigManager" ,"removeAttributes", new Object[] { path, attributeNames });
	}

	@Override
	public boolean removeNode(TreeConfigPath path, boolean recursive) {
		boolean ret = localManager.removeNode(path, recursive);
		addNotifyTask("treeConfigManager" ,"removeNode", new Object[] { path, recursive });
		return ret;
	}

	@Override
	public boolean removeNodes(List<TreeConfigPath> paths, boolean recursive) {
		boolean ret = localManager.removeNodes(paths, recursive);
		addNotifyTask("treeConfigManager" ,"removeNodes", new Object[] { paths, recursive });
		return ret;
	}

	@Override
	public void saveAttribute(TreeConfigPath path, TreeConfigAttribute attribute) {
		localManager.saveAttribute(path, attribute);
		addNotifyTask("treeConfigManager" ,"saveAttribute", new Object[] { path, attribute });
	}

	@Override
	public void saveAttributeValue(TreeConfigPath path, String attributeName, Serializable value) {
		localManager.saveAttributeValue(path, attributeName, value);
		addNotifyTask("treeConfigManager" ,"saveAttributeValue", new Object[] { path, attributeName, value });
	}

	@Override
	public void saveNode(TreeConfigNode node, boolean updateNodeValue) {
		localManager.saveNode(node, updateNodeValue);
		addNotifyTask("treeConfigManager" ,"saveNode", new Object[] { node, updateNodeValue });
	}

	@Override
	public void saveNodeValue(TreeConfigPath path, Serializable value) {
		localManager.saveNodeValue(path, value);
		addNotifyTask("treeConfigManager" ,"saveNodeValue", new Object[] { path, value });
	}

	@Override
	public void saveNodes(List<TreeConfigNode> nodes, boolean updateNodeValue) {
		localManager.saveNodes(nodes, updateNodeValue);
		addNotifyTask("treeConfigManager" ,"saveNodes", new Object[] { nodes, updateNodeValue });
	}

	@Override
	public TreeConfigAttribute getAttribute(TreeConfigPath path, String attributeName) {
		try{
			synchronized (writeLock) {
				// 防止和server通知并发，在server通知过程中，不做get，get操作也可能有更新操作
				currentReaderCount.incrementAndGet();
			}
			TreeConfigAttribute attribute = localManager.getAttribute(path, attributeName);
			if(attribute == null && remoteManager != null){
				attribute = remoteManager.getAttribute(path, attributeName);
				if(attribute != null){
					localManager.saveAttribute(path, attribute);
				}
			}
			return attribute;
		} finally {
			currentReaderCount.decrementAndGet();
		}
	}

	@Override
	public Serializable getAttributeValue(TreeConfigPath path, String attributeName) {
		try{
			synchronized (writeLock) {
				// 防止和server通知并发，在server通知过程中，不做get，get操作也可能有更新操作
				currentReaderCount.incrementAndGet();
			}
			Serializable value = localManager.getAttributeValue(path, attributeName);
			if(value == null && remoteManager != null){
				value = remoteManager.getAttributeValue(path, attributeName);
				if(value != null){
					localManager.saveAttributeValue(path, attributeName, value);
				}
			}
			return value;
		} finally {
			currentReaderCount.decrementAndGet();
		}
	}

	@Override
	public List<TreeConfigNode> getChildren(TreeConfigPath path) {
		try{
			synchronized (writeLock) {
				// 防止和server通知并发，在server通知过程中，不做get，get操作也可能有更新操作
				currentReaderCount.incrementAndGet();
			}
			List<TreeConfigNode> children = localManager.getChildren(path);
			if(children.size() == 0 && remoteManager != null){
				children = remoteManager.getChildren(path);
				if(children.size() > 0 ){
					localManager.saveNodes(children, true);
				}
			}
			return children;
		} finally {
			currentReaderCount.decrementAndGet();
		}
	}

	@Override
	public TreeConfigNode getNode(TreeConfigPath path) {
		try{
			synchronized (writeLock) {
				// 防止和server通知并发，在server通知过程中，不做get，get操作也可能有更新操作
				currentReaderCount.incrementAndGet();
			}
			TreeConfigNode node = localManager.getNode(path);
			if(node == null && remoteManager != null){
				node = remoteManager.getNode(path);
				if(node != null){
					localManager.saveNode(node, true);
				}
			}
			return node;
		} finally {
			currentReaderCount.decrementAndGet();
		}
	}

	@Override
	public Serializable getNodeValue(TreeConfigPath path) {
		try{
			synchronized (writeLock) {
				// 防止和server通知并发，在server通知过程中，不做get，get操作也可能有更新操作
				currentReaderCount.incrementAndGet();
			}
			Serializable value = localManager.getNodeValue(path);
			if(value == null && remoteManager != null){
				value = remoteManager.getNodeValue(path);
				if(value != null){
					localManager.saveNodeValue(path, value);
				}
			}
			return value;
		} finally {
			currentReaderCount.decrementAndGet();
		}
	}

	@Override
	public boolean isAttributesExist(TreeConfigPath path, String... attributeNames) {
		try{
			synchronized (writeLock) {
				// 防止和server通知并发，在server通知过程中，不做get，get操作也可能有更新操作
				currentReaderCount.incrementAndGet();
			}
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
		} finally {
			currentReaderCount.decrementAndGet();
		}
	}

	@Override
	public boolean isNodeExist(TreeConfigPath path) {
		try{
			synchronized (writeLock) {
				// 防止和server通知并发，在server通知过程中，不做get，get操作也可能有更新操作
				currentReaderCount.incrementAndGet();
			}
			boolean exist = localManager.isNodeExist(path);
			if (!exist && remoteManager != null) {
				exist = remoteManager.isNodeExist(path);
				if (exist) {
					TreeConfigNode node = remoteManager.getNode(path);
					localManager.saveNode(node, true);
				}
			}
			return exist;
		} finally {
			currentReaderCount.decrementAndGet();
		}
	}

	private void addNotifyTask(String targetId, String method, Object[] args){
		InvokeCommand command = new InvokeCommand();
		command.setTargetId(targetId);
		command.setMethod(method);
		command.setArgs(args);
		notifyTask.addLast(command);
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
				// 注册客户端
				logger.debug("register client , clientId : {}", clientId);
				IoSession session = connector.getManagedSessions().values().iterator().next();
				SyncCommandSender syncSender = new SyncCommandSender(session);
				syncSender.sendCommand("treeConfigRegister", "registerClient", new Object[] { clientId }, 3);
				// 绑定断开连接时的值
				logger.debug("binding disconnected value");
				for (UpdateNode updateNode : disconnectedNodes) {
					syncSender.sendCommand("treeConfigBinder", "bindingDisconnectedValue", new Object[] {updateNode.getNode(), updateNode.isUpdateNodeValue()}, 3);
				}
				// 设置sever上的节点值
				logger.debug("setting push nodes");
				List<TreeConfigNode> pushNodes = new LinkedList<TreeConfigNode>();
				for (NodePath nodePath : initialPushNodes) {
					putPushNodes(nodePath.getPath(), nodePath.isRecursive(), pushNodes);
				}
				syncSender.sendCommand("treeConfigManager", "saveNodes", new Object[] { pushNodes, true }, 3);
				// 初始化client用到的节点
				logger.debug("getting initial nodes");
				try{
					synchronized (writeLock) {
						// 防止和server通知并发，在server通知过程中，不做get，get操作也可能有更新操作
						currentReaderCount.incrementAndGet();
					}
					for (NodePath nodePath : initialGetNodes) {
						freshNode(nodePath.getPath() ,nodePath.isRecursive());
					}
				} finally {
					currentReaderCount.decrementAndGet();
				}
			} catch (Throwable e) {
				logger.error(e.getMessage(), e);
				try {
					Thread.sleep(10 * 1000);
				} catch (InterruptedException e1) {
					logger.warn(e1.getMessage(), e1);
				}
				if(connector.getManagedSessionCount() > 0) {
					// 执行失败后10秒后继续执行
					taskExecutor.execute(new SyncTask());
				}
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

	public int getPingSeconds() {
		return pingSeconds;
	}

	public void setPingSeconds(int pingSeconds) {
		this.pingSeconds = pingSeconds;
	}

	public TreeConfigClientListener getListener() {
		return listener;
	}

	public void setListener(TreeConfigClientListener listener) {
		this.listener = listener;
	};

}

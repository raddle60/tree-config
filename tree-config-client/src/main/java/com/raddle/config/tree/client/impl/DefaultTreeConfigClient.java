/**
 * 
 */
package com.raddle.config.tree.client.impl;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
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

import com.raddle.config.tree.DefaultConfigNode;
import com.raddle.config.tree.DefaultNodeSelector;
import com.raddle.config.tree.DefaultUpdateNode;
import com.raddle.config.tree.api.TreeConfigAttribute;
import com.raddle.config.tree.api.TreeConfigManager;
import com.raddle.config.tree.api.TreeConfigNode;
import com.raddle.config.tree.api.TreeConfigPath;
import com.raddle.config.tree.client.TreeConfigClient;
import com.raddle.config.tree.local.MemoryConfigManager;
import com.raddle.config.tree.remote.RemoteConfigManager;
import com.raddle.config.tree.remote.SyncCommandSender;
import com.raddle.config.tree.remote.exception.RemoteExecuteException;
import com.raddle.config.tree.remote.utils.RemoteUtils;
import com.raddle.config.tree.utils.ExceptionUtils;
import com.raddle.config.tree.utils.InvokeUtils;
import com.raddle.nio.mina.cmd.invoke.AbstractInvokeCommandHandler;
import com.raddle.nio.mina.cmd.invoke.InvokeCommand;
import com.raddle.nio.mina.cmd.invoke.MethodInvoke;
import com.raddle.nio.mina.hessian.HessianDecoder;
import com.raddle.nio.mina.hessian.HessianEncoder;

/**
 * 注意：client只能连接一次，用完后一定要close，close会关闭所有连接和处理线程<br>
 * 不close，将导致处理线程没有关闭，程序无法退出<br>
 * bindDisconnectedNode<br>
 * bindDisconnectedDelNode<br>
 * bindInitialPushNodes<br>
 * bindInitialGetNodes<br>
 * 这四个方法，必须在connect之前完成，这四个方法只是加入列表，并未直接执行，在每次连接成功后执行，所以在连接之后的操作，将无法执行
 * @author xurong
 * 
 */
public class DefaultTreeConfigClient implements TreeConfigClient {
	private static final Logger logger = LoggerFactory.getLogger(DefaultTreeConfigClient.class);
	private String clientId = UUID.randomUUID().toString();
	private String serverIp;
	private int serverPort;
	private long connectTimeoutMs = 3000;
	private int invokeTimeoutSeconds = 10;
	private AtomicInteger currentReaderCount = new AtomicInteger(0);
	private Object writeLock = new Object();
	private TreeConfigManager localManager = new MemoryConfigManager();
	private TreeConfigManager remoteManager = null;
	private NioSocketConnector connector = null;
	private List<DefaultNodeSelector> initialGetNodes = new LinkedList<DefaultNodeSelector>();
	private List<DefaultNodeSelector> initialPushNodes = new LinkedList<DefaultNodeSelector>();
	private List<DefaultUpdateNode> disconnectedNodes = new LinkedList<DefaultUpdateNode>();
	private Deque<InvokeCommand> notifyTask = new LinkedList<InvokeCommand>();
	private SocketAddress localAddress = null;
	private ExecutorService taskExecutor = null;
	private ScheduledExecutorService scheduleExecutor = null;
	private int pingSeconds = 60;
	private TreeConfigClientListener listener = null;
	private static final Set<String> updateMethodSet = new HashSet<String>();
	private boolean closing = false;
	private AtomicInteger shutdownCount = new AtomicInteger(0);
	private AbstractInvokeCommandHandler commandHandler = null;
	static {
		updateMethodSet.add("saveNode");
		updateMethodSet.add("saveNodes");
		updateMethodSet.add("saveNodeValue");
		updateMethodSet.add("saveAttribute");
		updateMethodSet.add("saveAttributeValue");
		updateMethodSet.add("removeAttributes");
		updateMethodSet.add("removeNode");
		updateMethodSet.add("removeNodes");
	}

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
		closing = false;
		if (connector == null) {
			logger.info("initialize executors");
			taskExecutor = new ThreadPoolExecutor(0, 5, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
			scheduleExecutor = Executors.newScheduledThreadPool(1);
			logger.info("initialize nio-connector");
			connector = new NioSocketConnector();
			logger.info("setting connect timeout {}ms", connectTimeoutMs);
			connector.setConnectTimeoutMillis(connectTimeoutMs);
			connector.getFilterChain().addFirst("binaryCodec", new ProtocolCodecFilter(new HessianEncoder(), new HessianDecoder()));
			// 处理接收的命令和响应
			commandHandler = new AbstractInvokeCommandHandler() {
				@Override
				protected Object invokeMethod(MethodInvoke methodInvoke) throws Exception {
					logger.debug("invoke received , target:{} , method {}" , methodInvoke.getTarget().getClass(), methodInvoke.getMethod());
					Object result = null;
					if(listener != null){
						listener.commandReceived(methodInvoke);
					}
					synchronized (writeLock) {
						// 等待所有读操作结束
						while (currentReaderCount.get() > 0) {
							logger.debug("reader count:{} , method:{}",currentReaderCount.get(), methodInvoke.getMethod());
							Thread.sleep(100);
						}
						result = InvokeUtils.invokeMethod(methodInvoke.getTarget(), methodInvoke.getMethod(), methodInvoke.getArgs());
					}
					logger.debug("invoke returned , target:{} , method {} , return {}" , new Object[]{methodInvoke.getTarget().getClass(), methodInvoke.getMethod(),result == null?"null":"not null"});
					return result;
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
					RemoteConfigManager remoteConfigManager = new RemoteConfigManager(session);
					remoteConfigManager.setTimeoutSeconds(invokeTimeoutSeconds);
					remoteManager = remoteConfigManager;
					localAddress = session.getLocalAddress();
					if(listener != null){
						listener.sessionConnected(session);
					}
					taskExecutor.execute(new SyncTask());
				}

				@Override
				protected String getCommandQueue(MethodInvoke methodInvoke) {
					if(updateMethodSet.contains(methodInvoke.getMethod())){
						// 所有更新操作在同一个队列中执行
						return "update";
					}
					// 其他操作并发执行
					return null;
				}
			};
			connector.setHandler(commandHandler);
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
			scheduleExecutor.scheduleWithFixedDelay(new Runnable() {
				
				@Override
				public void run() {
					if(connector.getManagedSessionCount() > 0){
						IoSession session = connector.getManagedSessions().values().iterator().next();
						RemoteUtils.pingAndCloseIfFailed(session);
					}
				}
			}, pingSeconds, pingSeconds, TimeUnit.SECONDS);
			// 保持连接
			logger.info("starting reconnect thread");
			scheduleExecutor.scheduleWithFixedDelay(new Runnable(){

				@Override
				public void run() {
					// 断开连接后重连
					if (!closing && connector != null && connector.getManagedSessionCount() == 0) {
						try {
							ConnectFuture future = connector.connect(new InetSocketAddress(serverIp, serverPort));
							future.awaitUninterruptibly();
							future.getSession();
						} catch (Exception e) {
							logger.error("connecting to {}:{} faild , because of {}: {}", new Object[] { serverIp, serverPort, ExceptionUtils.getRootCause(e).getClass(), ExceptionUtils.getRootCause(e).getMessage() });
						}
					}
				}
			}, 1, 5, TimeUnit.SECONDS);
			// 发送通知
			logger.info("starting notify thread");
			Thread notifyThread = new Thread(){
				@Override
				public void run() {
					shutdownCount.incrementAndGet();
					while (!closing && true) {
						InvokeCommand command = null;
						synchronized (notifyTask) {
							while(notifyTask.size() > 0){
								command = notifyTask.pollFirst();
								try {
									InvokeUtils.invokeMethod(remoteManager, command.getMethod(), command.getArgs());
								} catch (RemoteExecuteException e) {
									// 远端的异常，忽略
								} catch (Exception e) {
									logger.error(e.getMessage(), e);
									// 失败了放回队列头，重新发送。
									notifyTask.addFirst(command);
									// 失败了10秒以后再试
									break;
								}
							}
							try {
								// 失败了10秒以后再试
								notifyTask.wait(1000 * 10);
							} catch (InterruptedException e) {
								logger.warn(e.getMessage(), e);
							}
						}
					}
					shutdownCount.decrementAndGet();
				}
			};
			notifyThread.setDaemon(true);
			notifyThread.start();
			logger.info("client initialize completed");
		}
	}

	public synchronized void close() {
		closing = true;
		if (taskExecutor != null) {
			logger.info("task executor shuting down");
			taskExecutor.shutdown();
		}
		if (scheduleExecutor != null) {
			logger.info("schedule executor shuting down");
			scheduleExecutor.shutdown();
		}
		if (connector != null) {
			logger.info("connector disposing");
			connector.dispose();
		}
		if (commandHandler != null) {
			logger.info("command handler disposing");
			commandHandler.dispose();
		}
		logger.info("notify thread shuting down");
		synchronized (notifyTask) {
			notifyTask.notify();
		}
		logger.info("tree config client close complete .");
	}

	@Override
	public void bindDisconnectedNode(TreeConfigNode node, boolean includeNodeValue) {
		disconnectedNodes.add(new DefaultUpdateNode(node, includeNodeValue));
	}
	
	@Override
	public void bindDisconnectedDelNode(TreeConfigPath path, boolean recursive) {
		DefaultConfigNode node = new DefaultConfigNode();
		node.setNodePath(path);
		disconnectedNodes.add(new DefaultUpdateNode(node, true, recursive));
	}

	@Override
	public void bindInitialPushNodes(TreeConfigPath path, boolean recursive) {
		initialPushNodes.add(new DefaultNodeSelector(path, recursive));
	}
	
	@Override
	public void bindInitialGetNodes(TreeConfigPath path, boolean recursive) {
		initialGetNodes.add(new DefaultNodeSelector(path, recursive));
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
	public List<TreeConfigNode> getDescendants(TreeConfigPath path) {
		try{
			synchronized (writeLock) {
				// 防止和server通知并发，在server通知过程中，不做get，get操作也可能有更新操作
				currentReaderCount.incrementAndGet();
			}
			List<TreeConfigNode> descendants = localManager.getDescendants(path);
			if(descendants.size() == 0 && remoteManager != null){
				descendants = remoteManager.getDescendants(path);
				if(descendants.size() > 0 ){
					localManager.saveNodes(descendants, true);
				}
			}
			return descendants;
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
	
	@Override
	public boolean isConnected() {
		return remoteManager != null;
	};
	
	private void addNotifyTask(String targetId, String method, Object[] args){
		InvokeCommand command = new InvokeCommand();
		command.setTargetId(targetId);
		command.setMethod(method);
		command.setArgs(args);
		notifyTask.addLast(command);
		synchronized (notifyTask) {
			notifyTask.notify();
		}
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

	public SocketAddress getLocalAddress() {
		return localAddress;
	}
	
	class SyncTask implements Runnable {
		
		@Override
		public void run() {
			try {
				// 注册客户端
				IoSession session = connector.getManagedSessions().values().iterator().next();
				if(Boolean.TRUE.equals(session.getAttribute("initialized"))){
					return;
				}
				logger.debug("register client , clientId : {}", clientId);
				SyncCommandSender syncSender = new SyncCommandSender(session);
				syncSender.sendCommand("treeConfigRegister", "registerClient", new Object[] { clientId }, invokeTimeoutSeconds);
				// 绑定断开连接时的值
				logger.debug("binding disconnected value");
				for (DefaultUpdateNode updateNode : disconnectedNodes) {
					syncSender.sendCommand("treeConfigBinder", "bindDisconnectedValue", new Object[] {updateNode}, 3);
				}
				// 绑定接收通知的节点
				logger.debug("binding listening nodes");
				syncSender.sendCommand("treeConfigBinder", "bindlisteningNodes", new Object[] { initialGetNodes }, invokeTimeoutSeconds);
				// 设置sever上的节点值
				logger.debug("setting push nodes");
				List<TreeConfigNode> pushNodes = new LinkedList<TreeConfigNode>();
				for (DefaultNodeSelector nodePath : initialPushNodes) {
					putPushNodes(nodePath.getPath(), nodePath.isRecursive(), pushNodes);
				}
				syncSender.sendCommand("treeConfigManager", "saveNodes", new Object[] { pushNodes, true }, invokeTimeoutSeconds);
				// 初始化client用到的节点
				logger.debug("getting initial nodes");
				try{
					synchronized (writeLock) {
						// 防止和server通知并发，在server通知过程中，不做get，get操作也可能有更新操作
						currentReaderCount.incrementAndGet();
					}
					for (DefaultNodeSelector nodePath : initialGetNodes) {
						freshNode(nodePath.getPath() ,nodePath.isRecursive());
					}
				} finally {
					currentReaderCount.decrementAndGet();
				}
				session.setAttribute("initialized" , Boolean.TRUE);
			} catch (Throwable e) {
				logger.error(e.getMessage(), e);
				if(connector != null && connector.getManagedSessionCount() > 0) {
					try {
						Thread.sleep(10 * 1000);
					} catch (InterruptedException e1) {
						logger.warn(e1.getMessage(), e1);
					}
					if(connector != null && connector.getManagedSessionCount() > 0) {
						// 执行失败后10秒后继续执行
						taskExecutor.execute(new SyncTask());
					}
				}
			}
		}

		private void putPushNodes(TreeConfigPath path, boolean recursive, List<TreeConfigNode> pushNodes) {
			TreeConfigNode node = localManager.getNode(path);
			if (node != null) {
				pushNodes.add(node);
				if (recursive) {
					List<TreeConfigNode> descendants = localManager.getDescendants(path);
					pushNodes.addAll(descendants);
				}
			}
		}

		private void freshNode(TreeConfigPath path, boolean recursive) {
			if(path == null){
				if(recursive){
					List<TreeConfigNode> descendants = remoteManager.getDescendants(path);
					localManager.saveNodes(descendants, true);
				}
			} else {
				TreeConfigNode node = remoteManager.getNode(path);
				if (node != null) {
					localManager.saveNode(node, true);
					if (recursive) {
						List<TreeConfigNode> descendants = remoteManager.getDescendants(path);
						localManager.saveNodes(descendants, true);
					}
				}
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
	}

	public int getInvokeTimeoutSeconds() {
		return invokeTimeoutSeconds;
	}

	public void setInvokeTimeoutSeconds(int invokeTimeoutSeconds) {
		this.invokeTimeoutSeconds = invokeTimeoutSeconds;
	}

}

/**
 * 
 */
package com.raddle.config.tree.server;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.raddle.config.tree.DefaultConfigNode;
import com.raddle.config.tree.DefaultConfigPath;
import com.raddle.config.tree.DefaultNodeSelector;
import com.raddle.config.tree.DefaultUpdateNode;
import com.raddle.config.tree.api.TreeConfigManager;
import com.raddle.config.tree.api.TreeConfigNode;
import com.raddle.config.tree.api.TreeConfigPath;
import com.raddle.config.tree.local.MemoryConfigManager;
import com.raddle.config.tree.remote.SyncCommandSender;
import com.raddle.config.tree.remote.exception.RemoteExecuteException;
import com.raddle.config.tree.remote.utils.IpUtils;
import com.raddle.config.tree.remote.utils.RemoteUtils;
import com.raddle.config.tree.utils.InvokeUtils;
import com.raddle.config.tree.utils.ReflectToStringBuilder;
import com.raddle.config.tree.utils.TreeUtils;
import com.raddle.nio.mina.cmd.CommandContext;
import com.raddle.nio.mina.cmd.invoke.AbstractInvokeCommandHandler;
import com.raddle.nio.mina.cmd.invoke.MethodInvoke;
import com.raddle.nio.mina.hessian.HessianDecoder;
import com.raddle.nio.mina.hessian.HessianEncoder;

/**
 * @author xurong
 * 
 */
public class DefaultTreeConfigServer {
	private static final Logger logger = LoggerFactory.getLogger(DefaultTreeConfigServer.class);
	private static final String NOTIFY_CLIENT_TARGET_ID = "treeConfigManager";
	private static final String ATTR_KEY_CLIENT_ID = "client_id";
	private static final Set<String> updateMethodSet = new HashSet<String>();
	private int invokeTimeoutSeconds = 10;
	private int readerIdleSeconds = 60 * 30;
	private IoAcceptor acceptor = new NioSocketAcceptor();
	private TreeConfigManager localManager = new MemoryConfigManager();
	private int port = 9877;
	private int maxTaskThreads = 50;
	private int failedResendSeconds = 10;
	private ThreadPoolExecutor taskExecutor = null;
	private int pingSeconds = 60;
	private ScheduledExecutorService pingSchedule = null;
	private Map<String, ClientContext> clientMap = new Hashtable<String, ClientContext>();
	private Object notifyWaiting = new Object();
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

	public void start() {
		long startAt = System.currentTimeMillis();
		logger.info("server starting ...");
		// 调用远程方法，等待响应返回
		logger.info("setting invoke timeout {} seconds ", invokeTimeoutSeconds);
		// 读空闲10分钟
		logger.info("setting reader idle time {} seconds ", readerIdleSeconds);
		acceptor.getSessionConfig().setReaderIdleTime(readerIdleSeconds);
		// hessain序列化
		acceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(new HessianEncoder(), new HessianDecoder()));
		// 方法调用
		commandHandler = new AbstractInvokeCommandHandler() {

			@Override
			@SuppressWarnings("unchecked")
			protected Object invokeMethod(final MethodInvoke methodInvoke) throws Exception {
				logger.debug("invoke received , target:{} , method {}" , methodInvoke.getTarget().getClass(), methodInvoke.getMethod());
				Object result = null;
				if ("treeConfigManager".equals(methodInvoke.getTargetId())) {
					if (updateMethodSet.contains(methodInvoke.getMethod())) {
						synchronized (CommandContext.getIoSession()) {
							// 更新操作需要同步，保证执行顺序,先到先执行
							// 对于同一个client发过来的，能保证是按调用顺序执行
							if ("saveNodes".equals(methodInvoke.getMethod())) {
								// 可能节点很多，需要检查是否有变化，客户端在断开重连时，会重发一遍，这时就不应该通知
								List<TreeConfigNode> nodes = (List<TreeConfigNode>) methodInvoke.getArgs()[0];
								Boolean updateNodeValue = (Boolean) methodInvoke.getArgs()[1];
								List<TreeConfigNode> toUpdateNodes = new ArrayList<TreeConfigNode>();
								for (Iterator<TreeConfigNode> iterator = nodes.iterator(); iterator.hasNext();) {
									TreeConfigNode newNode = (TreeConfigNode) iterator.next();
									TreeConfigNode toUpdateNode = TreeUtils.getToUpdateNode(newNode, localManager.getNode(newNode.getNodePath()), updateNodeValue);
									if (toUpdateNode != null) {
										toUpdateNodes.add(toUpdateNode);
									}
								}
								if (toUpdateNodes.size() == 0) {
									// 沒有变化不用执行后续操作
									return null;
								} else {
									methodInvoke.getArgs()[0] = toUpdateNodes;
								}
							} else if ("saveNode".equals(methodInvoke.getMethod())) {
								TreeConfigNode newNode = (TreeConfigNode) methodInvoke.getArgs()[0];
								Boolean updateNodeValue = (Boolean) methodInvoke.getArgs()[1];
								TreeConfigNode toUpdateNode = TreeUtils.getToUpdateNode(newNode, localManager.getNode(newNode.getNodePath()), updateNodeValue);
								if (toUpdateNode != null) {
									methodInvoke.getArgs()[0] = toUpdateNode;
								} else {
									return null;
								}
							}
							// 本地的执行相对较快，直接执行
							result = InvokeUtils.invokeMethod(methodInvoke.getTarget(), methodInvoke.getMethod(), methodInvoke.getArgs());
							// 增加通知任务
							addNotifyTask(CommandContext.getIoSession(), methodInvoke.getMethod(), methodInvoke.getArgs());
						}
					} else {
						// 读操作直接执行
						result = InvokeUtils.invokeMethod(methodInvoke.getTarget(), methodInvoke.getMethod(), methodInvoke.getArgs());
					}
				} else {
					result = InvokeUtils.invokeMethod(methodInvoke.getTarget(), methodInvoke.getMethod(), methodInvoke.getArgs());
				}
				logger.debug("invoke returned , target:{} , method {} , return {}" , new Object[]{methodInvoke.getTarget().getClass(), methodInvoke.getMethod(),result == null?"null":"not null"});
				return result;
			}

			@Override
			protected Object getObject(String id) {
				if ("treeConfigManager".equals(id)) {
					return localManager;
				}
				if ("treeConfigRegister".equals(id)) {
					return DefaultTreeConfigServer.this;
				}
				if ("treeConfigBinder".equals(id)) {
					return DefaultTreeConfigServer.this;
				}
				return localManager;
			}
			
			@Override
			public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
				logger.error("Session exception , remote address [" + session.getRemoteAddress() + "] , clientId ["
						+ session.getAttribute(ATTR_KEY_CLIENT_ID) + "] .", cause);
				session.close(true);
			}

			@Override
			public void sessionClosed(IoSession session) throws Exception {
				logger.debug("Session closed , remote address [{}], clientId [{}], sessions [{}].", new Object[]{session.getRemoteAddress(), session
						.getAttribute(ATTR_KEY_CLIENT_ID), acceptor.getManagedSessionCount()});
				ClientContext clientContext = clientMap.get(session.getAttribute(ATTR_KEY_CLIENT_ID));
				clientMap.remove(session.getAttribute(ATTR_KEY_CLIENT_ID));
				if(clientContext != null && clientContext.getDisconnectedValues().size() > 0){
					// 通知断开以后的值
					for (DefaultUpdateNode updateNode : clientContext.getDisconnectedValues()) {
						if (updateNode.isDeleteNode()) {
							localManager.removeNode(updateNode.getNode().getNodePath(), updateNode.isRecursive());
							addNotifyTask(session, "removeNode", new Object[] { updateNode.getNode().getNodePath(), updateNode.isRecursive() });
						} else {
							localManager.saveNode(updateNode.getNode(), updateNode.isUpdateNodeValue());
							addNotifyTask(session, "saveNode", new Object[] { updateNode.getNode(), updateNode.isUpdateNodeValue() });
						}
					}
				}
				// 更新客户端连接数
				DefaultConfigNode serverStateNode = new DefaultConfigNode();
				serverStateNode.setNodePath(new DefaultConfigPath("/树形配置服务器/状态"));
				serverStateNode.setAttributeValue("客户端连接数", acceptor.getManagedSessionCount());
				localManager.saveNode(serverStateNode, false);
				addNotifyTask(null, "saveNode", new Object[] { serverStateNode, false });
				///
				DefaultConfigNode clientCount = new DefaultConfigNode();
				clientCount.setNodePath(new DefaultConfigPath("/树形配置服务器/状态/客戶端"));
				clientCount.setValue(acceptor.getManagedSessionCount()+"个连接");
				localManager.saveNode(clientCount, true);
				addNotifyTask(null, "saveNode", new Object[] { clientCount, true });
				// 刪除客戶端
				DefaultConfigPath clientPath = new DefaultConfigPath("/树形配置服务器/状态/客戶端/" + IpUtils.getIpAndPort(session.getRemoteAddress()));
				localManager.removeNode(clientPath, true);
				addNotifyTask(null, "removeNode", new Object[] { clientPath, true });
			}

			@Override
			public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
				logger.warn("Session idle timeout , idle status [{}] , remote address [{}], clientId [{}] .", new Object[] { status,
						session.getRemoteAddress(), session.getAttribute(ATTR_KEY_CLIENT_ID) });
				session.close(true);
			}

			@Override
			public void sessionCreated(IoSession session) throws Exception {
				logger.debug("Session created , remote address [{}], sessions [{}] .", session.getRemoteAddress(), acceptor.getManagedSessionCount());
				// 更新客户端连接数
				DefaultConfigNode serverStateNode = new DefaultConfigNode();
				serverStateNode.setNodePath(new DefaultConfigPath("/树形配置服务器/状态"));
				serverStateNode.setAttributeValue("客户端连接数", acceptor.getManagedSessionCount());
				localManager.saveNode(serverStateNode, false);
				addNotifyTask(null, "saveNode", new Object[]{serverStateNode, false});
				/////
				DefaultConfigNode clientCount = new DefaultConfigNode();
				clientCount.setNodePath(new DefaultConfigPath("/树形配置服务器/状态/客戶端"));
				clientCount.setValue(acceptor.getManagedSessionCount()+"个连接");
				localManager.saveNode(clientCount, true);
				addNotifyTask(null, "saveNode", new Object[] { clientCount, true });
				// 添加客戶端
				DefaultConfigNode clientNode = new DefaultConfigNode();
				clientNode.setNodePath(new DefaultConfigPath("/树形配置服务器/状态/客戶端/" + IpUtils.getIpAndPort(session.getRemoteAddress())));
				clientNode.setAttributeValue("连接时间", DateFormatUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss"));
				localManager.saveNode(clientNode, false);
				addNotifyTask(null, "saveNode", new Object[] { clientNode, false });
			}

			@Override
			protected String getCommandQueue(MethodInvoke methodInvoke) {
				// 全部操作并发执行
				return null;
			}

		};
		commandHandler.setMaxTaskThreads(50);
		acceptor.setHandler(commandHandler);
		logger.info("initialize local configuration");
		DefaultConfigNode serverConfigNode = new DefaultConfigNode();
		serverConfigNode.setNodePath(new DefaultConfigPath("/树形配置服务器/设置"));
		serverConfigNode.setAttributeValue("IP地址", getLocalHostIps());
		serverConfigNode.setAttributeValue("监听端口号", port);
		serverConfigNode.setAttributeValue("读超时时间", readerIdleSeconds+"秒");
		serverConfigNode.setAttributeValue("远程调用超时时间", invokeTimeoutSeconds+"秒");
		serverConfigNode.setAttributeValue("本地配置类", localManager.getClass().getName());
		serverConfigNode.setAttributeValue("最大任务线程", maxTaskThreads);
		serverConfigNode.setAttributeValue("通知失败重试间隔", failedResendSeconds + "秒");
		localManager.saveNode(serverConfigNode, false);
		DefaultConfigNode serverStateNode = new DefaultConfigNode();
		serverStateNode.setNodePath(new DefaultConfigPath("/树形配置服务器/状态"));
		serverStateNode.setAttributeValue("启动时间", DateFormatUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss"));
		serverStateNode.setAttributeValue("任务执行状态（当前/总数）", "0/0");
		serverStateNode.setAttributeValue("客户端连接数", 0);
		localManager.saveNode(serverStateNode, false);
		logger.info("binding on port {}", port);
		try {
			acceptor.bind(new InetSocketAddress(port));
			logger.info("server listening on {}", port);
			logger.info("starting task executor");
			taskExecutor = new ThreadPoolExecutor(0, maxTaskThreads, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
			logger.info("starting updating notify thread");
			Thread notifyThread = new Thread(){
				@Override
				public void run() {
					while(true){
						try {
							for (final String clientId : clientMap.keySet()) {
								final ClientContext clientContext = clientMap.get(clientId);
								if(clientContext != null && clientContext.getNotifyTasks().size() > 0){
									taskExecutor.execute(new Runnable() {
										@Override
										public void run() {
											IoSession session = clientContext.getSession();
											// 需要判断，可能已经关闭
											if (session != null && session.isConnected()) {
												NotifyClientTask notifyClientTask = null;
												SyncCommandSender sender = new SyncCommandSender(session);
												// 同步，防止并发执行同一个队列的任务
												synchronized (clientContext) {
													try {
														while (clientContext.getNotifyTasks().size() > 0) {
															notifyClientTask = clientContext.getNotifyTasks().pollFirst();
															try {
																sender.sendCommand(NOTIFY_CLIENT_TARGET_ID, notifyClientTask.getMethod(), notifyClientTask.getArgs(), invokeTimeoutSeconds);
															} catch (RemoteExecuteException e) {
																// 远端的异常，忽略
															}
														}
													} catch (Exception e) {
														logger.error(e.getMessage(), e);
														// 重新放回队列，等待下次执行
														if(notifyClientTask != null){
															clientContext.getNotifyTasks().addFirst(notifyClientTask);
														}
													}
												}
											} else {
												logger.debug("session is closed , clientId :{}",clientId);
											}
										}
									});
								}
							}
							synchronized (notifyWaiting) {
								try {
									notifyWaiting.wait(failedResendSeconds * 1000);
								} catch (InterruptedException e) {
									logger.error(e.getMessage(), e);
								}
							}
							// 更新任务执行状态
							DefaultConfigNode serverStateNode = new DefaultConfigNode();
							serverStateNode.setNodePath(new DefaultConfigPath("/树形配置服务器/状态"));
							int allTaskCount = 0;
							for (String clientId : clientMap.keySet()) {
								ClientContext clientContext = clientMap.get(clientId);
								if (clientContext != null) {
									allTaskCount += clientContext.getNotifyTasks().size();
								}
							}
							String taskState = taskExecutor.getActiveCount() + "/" + allTaskCount;
							if(!ObjectUtils.equals(taskState, localManager.getAttributeValue(new DefaultConfigPath("/树形配置服务器/状态"), "任务执行状态（当前/总数）"))){
								serverStateNode.setAttributeValue("任务执行状态（当前/总数）", taskExecutor.getActiveCount() + "/" + allTaskCount);
								localManager.saveNode(serverStateNode, false);
								addNotifyTask(null, "saveNode", new Object[]{serverStateNode, false});
							}
						} catch (Exception e) {
							logger.error(e.getMessage(), e);
						}
					}
				}
			};
			notifyThread.setDaemon(true);
			notifyThread.start();
			logger.info("ping client per {} seconds" , pingSeconds);
			pingSchedule = Executors.newScheduledThreadPool(1);
			pingSchedule.scheduleWithFixedDelay(new Runnable() {

				@Override
				public void run() {
					for (String clientId : clientMap.keySet()) {
						final ClientContext clientContext = clientMap.get(clientId);
						if (clientContext != null) {
							taskExecutor.execute(new Runnable() {
								@Override
								public void run() {
									RemoteUtils.pingAndCloseIfFailed(clientContext.getSession());
								}
							});
						}
					}
				}
			}, pingSeconds, pingSeconds, TimeUnit.SECONDS);
			logger.info("server start completed in {}ms " , System.currentTimeMillis() - startAt);
		} catch (IOException e) {
			logger.error("server start failed .", e);
		}
	}

	public void registerClient(String clientId) {
		logger.debug("register client [{}] , remote address [{}] .", clientId, CommandContext.getIoSession().getRemoteAddress());
		CommandContext.getIoSession().setAttribute(ATTR_KEY_CLIENT_ID, clientId);
		ClientContext clientContext = new ClientContext(clientId, CommandContext.getIoSession());
		clientMap.put(clientId, clientContext);
	}

	public void bindDisconnectedValue(DefaultUpdateNode node) {
		logger.debug("bind disconnected value , remote address [{}]", CommandContext.getIoSession().getRemoteAddress());
		String clientId = (String) CommandContext.getIoSession().getAttribute(ATTR_KEY_CLIENT_ID);
		clientMap.get(clientId).getDisconnectedValues().add(node);
	}

	public void bindlisteningNodes(List<DefaultNodeSelector> listeningNodes) {
		logger.debug("bind listening nodes , remote address [{}]", CommandContext.getIoSession().getRemoteAddress());
		String clientId = (String) CommandContext.getIoSession().getAttribute(ATTR_KEY_CLIENT_ID);
		clientMap.get(clientId).getSelectors().addAll(listeningNodes);
	}

	@SuppressWarnings("unchecked")
	private void addNotifyTask(IoSession curSession, String method, Object[] args) {
		for (final String clientId : clientMap.keySet()) {
			// 发送者不用通知
			if (curSession == null || !clientId.equals(curSession.getAttribute(ATTR_KEY_CLIENT_ID))) {
				// 加入通知队列
				ClientContext clientContext = clientMap.get(clientId);
				if (clientContext != null) {
					boolean acceptable = false;
					// 检查是否接收通知
					if (args[0] instanceof TreeConfigPath) {
						TreeConfigPath path = (TreeConfigPath) args[0];
						acceptable = clientContext.isAcceptable(path);
					} else if (args[0] instanceof TreeConfigNode) {
						TreeConfigNode node = (TreeConfigNode) args[0];
						acceptable = clientContext.isAcceptable(node.getNodePath());
					} else if (args[0] instanceof List) {
						List<TreeConfigNode> nodes = (List<TreeConfigNode>) args[0];
						for (TreeConfigNode treeConfigNode : nodes) {
							if (clientContext.isAcceptable(treeConfigNode.getNodePath())) {
								acceptable = true;
								break;
							}
						}
					}
					if (acceptable) {
						clientContext.getNotifyTasks().addLast(new NotifyClientTask(clientId, method, args));
						if (logger.isDebugEnabled()) {
							logger.debug("added task , client:{}, method:{}, args:{}", new Object[] { clientId, method, ReflectToStringBuilder.reflectToString(args) });
						}
					}
				}
			}
		}
		// 立即执行通知
		synchronized (notifyWaiting) {
			notifyWaiting.notify();
		}
	}

	public void shutdown() {
		long startAt = System.currentTimeMillis();
		if(pingSchedule != null){
			logger.info("shuting down ping schedule");
			pingSchedule.shutdown();
		}
		if (taskExecutor != null) {
			logger.info("shuting down task executor");
			taskExecutor.shutdown();
		}
		logger.info("unbinding listening port");
		acceptor.unbind();
		acceptor.dispose();
		clientMap.clear();
		if (commandHandler != null) {
			logger.info("command handler disposing");
			commandHandler.dispose();
		}
		logger.info("shutdown server completed in {}ms ", System.currentTimeMillis() - startAt);
	}

	private static String getLocalHostIps() {
		List<String> ips = new ArrayList<String>();
		Enumeration<NetworkInterface> allNetInterfaces = null;
		try {
			allNetInterfaces = NetworkInterface.getNetworkInterfaces();
		} catch (SocketException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
		while (allNetInterfaces.hasMoreElements()) {
			NetworkInterface netInterface = (NetworkInterface) allNetInterfaces.nextElement();
			Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
			while (addresses.hasMoreElements()) {
				InetAddress inetAddress = addresses.nextElement();
				if (inetAddress != null && inetAddress instanceof Inet4Address) {
					String ip = inetAddress.getHostAddress();
					if (ip != null && !ip.equals("127.0.0.1")) {
						ips.add(ip);
					}
				}
			}
		}
		return StringUtils.join(ips, ", ");
	}
	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getInvokeTimeoutSeconds() {
		return invokeTimeoutSeconds;
	}

	public void setInvokeTimeoutSeconds(int timeoutSeconds) {
		this.invokeTimeoutSeconds = timeoutSeconds;
	}

	public int getReaderIdleSeconds() {
		return readerIdleSeconds;
	}

	public void setReaderIdleSeconds(int readerIdleSeconds) {
		this.readerIdleSeconds = readerIdleSeconds;
	}

	public TreeConfigManager getLocalManager() {
		return localManager;
	}

	public void setLocalManager(TreeConfigManager localManager) {
		this.localManager = localManager;
	}

	public int getPingSeconds() {
		return pingSeconds;
	}

	public void setPingSeconds(int pingSeconds) {
		this.pingSeconds = pingSeconds;
	}

}

/**
 * 
 */
package com.raddle.config.tree.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.raddle.config.tree.api.TreeConfigManager;
import com.raddle.config.tree.api.TreeConfigNode;
import com.raddle.config.tree.local.MemoryConfigManager;
import com.raddle.config.tree.remote.SyncCommandSender;
import com.raddle.config.tree.remote.exception.RemoteExecuteException;
import com.raddle.config.tree.remote.exception.ResponseTimeoutException;
import com.raddle.config.tree.utils.InvokeUtils;
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
	private int invokeTimeoutSeconds = 5;
	private int readerIdleSeconds = 60 * 30;
	private IoAcceptor acceptor = new NioSocketAcceptor();
	private TreeConfigManager localManager = new MemoryConfigManager();
	private int port = 9877;
	private ExecutorService taskExecutor = null;
	private ScheduledExecutorService scheduleService = null;
	private Deque<NotifyClientTask> notifyFailedTasks = new LinkedList<NotifyClientTask>();
	private Map<String, IoSession> clientMap = new HashMap<String, IoSession>();
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
		logger.info("tree configuartion server starting ...");
		// 调用远程方法，等待响应返回
		logger.info("setting invoke timeout {} seconds ", invokeTimeoutSeconds);
		// 读空闲10分钟
		logger.info("setting reader idle time {} seconds ", readerIdleSeconds);
		acceptor.getSessionConfig().setReaderIdleTime(readerIdleSeconds);
		// hessain序列化
		acceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(new HessianEncoder(), new HessianDecoder()));
		// 方法调用
		acceptor.setHandler(new AbstractInvokeCommandHandler() {

			@Override
			protected Object invokeMethod(final MethodInvoke methodInvoke) throws Exception {
				Object result = null;
				if ("treeConfigManager".equals(methodInvoke.getTargetId())) {
					if (updateMethodSet.contains(methodInvoke.getMethod())) {
						synchronized (DefaultTreeConfigServer.this) {
							// 更新操作需要同步，保证执行顺序,先到先执行
							// 对于同一个client发过来的，能保证是按调用顺序执行
							// 由于client调用都有超时限制，所以不能执行时间太长，可以用队列的方式执行
							// 本地的执行非常快，直接执行
							result = InvokeUtils.invokeMethod(methodInvoke.getTarget(), methodInvoke.getMethod(), methodInvoke.getArgs());
							// 通知通过网络，所以用队列执行
							for (final String clientId : clientMap.keySet()) {
								// 发送者不用通知
								if (!clientId.equals(CommandContext.getIoSession().getAttribute(ATTR_KEY_CLIENT_ID))) {
									taskExecutor.execute(new Runnable() {
										@Override
										public void run() {
											SyncCommandSender sender = new SyncCommandSender(clientMap.get(clientId));
											try {
												sender.sendCommand(NOTIFY_CLIENT_TARGET_ID, methodInvoke.getMethod(), methodInvoke.getArgs(),
														invokeTimeoutSeconds);
											} catch (RemoteExecuteException e) {
												// 远端的异常，忽略
											} catch (ResponseTimeoutException e) {
												logger.warn("wating timeout , clientId {}, targetId:{} ,method:{} , remote address:{}", new Object[] {
														clientId, NOTIFY_CLIENT_TARGET_ID, methodInvoke.getMethod(),
														clientMap.get(clientId).getRemoteAddress() });
												// 等待超时 , 重新发送
												NotifyClientTask notifyTask = new NotifyClientTask(clientId, methodInvoke.getMethod(), methodInvoke
														.getArgs());
												notifyFailedTasks.add(notifyTask);
											} catch (Exception e) {
												logger.error(e.getMessage(), e);
												// 失败了放到失败队列
												// 这种重发策略，将失去通知原有的顺序
												// 失败的通知可能会在新到的通知之后到达
												NotifyClientTask notifyTask = new NotifyClientTask(clientId, methodInvoke.getMethod(), methodInvoke
														.getArgs());
												notifyFailedTasks.add(notifyTask);
											}
										}
									});
								}
							}
						}
					} else {
						// 读操作直接执行
						result = InvokeUtils.invokeMethod(methodInvoke.getTarget(), methodInvoke.getMethod(), methodInvoke.getArgs());
					}
				}
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
				logger.debug("Session closed , remote address [{}], clientId [{}] .", session.getRemoteAddress(), session
						.getAttribute(ATTR_KEY_CLIENT_ID));
				// 防止在循环发通知的过程中，并发remove
				synchronized (DefaultTreeConfigServer.this) {
					clientMap.remove(session.getAttribute(ATTR_KEY_CLIENT_ID));
				}
			}

			@Override
			public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
				logger.warn("Session idle timeout , idle status [{}] , remote address [{}], clientId [{}] .", new Object[] { status,
						session.getRemoteAddress(), session.getAttribute(ATTR_KEY_CLIENT_ID) });
				session.close(true);
			}

			@Override
			public void sessionCreated(IoSession session) throws Exception {
				logger.debug("Session created , remote address [{}] .", session.getRemoteAddress());
			}

		});
		logger.info("binding on port {}", port);
		try {
			acceptor.bind(new InetSocketAddress(port));
			logger.info("tree configuartion server listening on {}", port);
			logger.info("starting task executor");
			taskExecutor = new ThreadPoolExecutor(0, 10, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
			logger.info("starting failed task process executor");
			scheduleService = Executors.newScheduledThreadPool(1);
			scheduleService.scheduleWithFixedDelay(new Runnable() {
				@Override
				public void run() {
					// 失败的任务
					while (notifyFailedTasks.size() > 0) {
						NotifyClientTask notifyClientTask = notifyFailedTasks.pollFirst();
						// 现在的实现重试将不能保证数据一致的状态
						// 简单的关闭连接，让客户端重新初始化
						IoSession session = clientMap.get(notifyClientTask.getClientId());
						// 需要判断，可能已经关闭
						if (session != null && session.isConnected()) {
							logger.warn(
									"session to be closed because of sending command failed , clientId ,targetId:{} ,method:{} , remote address:{}",
									new Object[] { notifyClientTask.getClientId(), NOTIFY_CLIENT_TARGET_ID, notifyClientTask.getMethod(),
											session.getRemoteAddress() });
							session.close(true);
						}
					}
				}
			}, 1, 1, TimeUnit.SECONDS);
			logger.info("server start completed in {}ms " , System.currentTimeMillis() - startAt);
		} catch (IOException e) {
			logger.error("tree configuartion start failed .", e);
		}
	}

	public void registerClient(String clientId) {
		logger.debug("register client [{}] , remote address [{}] .", clientId, CommandContext.getIoSession().getRemoteAddress());
		CommandContext.getIoSession().setAttribute(ATTR_KEY_CLIENT_ID, clientId);
		clientMap.put(clientId, CommandContext.getIoSession());
	}
	
	public void bindingDisconnectedValue(TreeConfigNode node ,boolean updatedNodeValue){
		logger.debug("binding disconnected value , remote address [{}]" , CommandContext.getIoSession().getRemoteAddress());
	}

	public void shutdown() {
		long startAt = System.currentTimeMillis();
		if (taskExecutor != null) {
			logger.info("shuting down task executor");
			taskExecutor.shutdown();
		}
		if (scheduleService != null) {
			logger.info("shuting down failed task process executor");
			scheduleService.shutdown();
		}
		logger.info("unbinding listening port");
		acceptor.unbind();
		acceptor.dispose();
		clientMap.clear();
		notifyFailedTasks.clear();
		logger.info("shutdown server completed in {}ms ", System.currentTimeMillis() - startAt);
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	class NotifyClientTask {
		private String clientId;
		private String method;
		private Object[] args;

		public NotifyClientTask(String clientId, String method, Object[] args) {
			this.clientId = clientId;
			this.method = method;
			this.args = args;
		}

		public String getMethod() {
			return method;
		}

		public void setMethod(String method) {
			this.method = method;
		}

		public Object[] getArgs() {
			return args;
		}

		public void setArgs(Object[] args) {
			this.args = args;
		}

		public String getClientId() {
			return clientId;
		}

		public void setClientId(String clientId) {
			this.clientId = clientId;
		}

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
}

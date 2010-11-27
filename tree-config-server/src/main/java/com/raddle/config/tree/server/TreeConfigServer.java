/**
 * 
 */
package com.raddle.config.tree.server;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.beanutils.MethodUtils;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.raddle.config.tree.api.TreeConfigManager;
import com.raddle.config.tree.local.MemoryConfigManager;
import com.raddle.config.tree.remote.SyncCommandSender;
import com.raddle.config.tree.remote.exception.RemoteExecuteException;
import com.raddle.config.tree.remote.exception.ResponseTimeoutException;
import com.raddle.nio.mina.cmd.CommandContext;
import com.raddle.nio.mina.cmd.invoke.AbstractInvokeCommandHandler;
import com.raddle.nio.mina.cmd.invoke.MethodInvoke;
import com.raddle.nio.mina.hessian.HessianDecoder;
import com.raddle.nio.mina.hessian.HessianEncoder;

/**
 * @author xurong
 * 
 */
public class TreeConfigServer {
	private static final Logger logger = LoggerFactory.getLogger(TreeConfigServer.class);
	private static final String ATTR_KEY_CLIENT_ID = "client_id";
	private static final Set<String> updateMethodSet = new HashSet<String>();
	private int invokeTimeoutSeconds = 5;
	private int readerIdleSeconds = 60 * 10;
	private IoAcceptor acceptor = new NioSocketAcceptor();
	private TreeConfigManager manager = new MemoryConfigManager();
	private int port;
	private ExecutorService executorService = null;
	private List<NotifyClientTask> notifyFailedTasks = new LinkedList<NotifyClientTask>();
	private Map<String, IoSession> clientMap = new HashMap<String, IoSession>();
	static {
		updateMethodSet.add("saveNode");
		updateMethodSet.add("saveNodeValue");
		updateMethodSet.add("saveAttribute");
		updateMethodSet.add("saveAttributeValue");
		updateMethodSet.add("removeAttributes");
		updateMethodSet.add("removeNode");
		updateMethodSet.add("saveNodes");
		updateMethodSet.add("removeNodes");
	}

	public void start() {
		logger.info("tree configuartion server starting ...");
		// 调用远程方法，等待响应返回
		logger.info("setting invoke timeout {} seconds " , invokeTimeoutSeconds);
		// 读空闲10分钟
		logger.info("setting reader idle time {} seconds " , readerIdleSeconds);
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
						synchronized (TreeConfigServer.this) {
							// 更新操作需要同步，保证执行顺序,先到先执行
							// 对于同一个client发过来的，能保证是按调用顺序执行
							// 由于client调用都有超时限制，所以不能执行时间太长，可以用队列的方式执行
							// 本地的执行非常快，直接执行
							result = TreeConfigServer.this.invokeMethod(methodInvoke.getTarget(), methodInvoke.getMethod(), methodInvoke.getArgs());
							// 通知通过网络，所以用队列执行
							for (final String clientId : clientMap.keySet()) {
								// 发送者不用通知
								if (!clientId.equals(CommandContext.getIoSession().getAttribute(ATTR_KEY_CLIENT_ID))) {
									executorService.execute(new Runnable() {
										@Override
										public void run() {
											SyncCommandSender sender = new SyncCommandSender(clientMap.get(clientId));
											try {
												sender.sendCommand("treeConfigManager", methodInvoke.getMethod(), methodInvoke.getArgs(),
														invokeTimeoutSeconds);
											} catch (RemoteExecuteException e) {
												// 远端的异常，忽略
											} catch (ResponseTimeoutException e) {
												logger.warn("wating timeout , targetId:{} ,method:{} , remote address:{}", new Object[] {
														"treeConfigManager", methodInvoke.getMethod(), clientMap.get(clientId).getRemoteAddress() });
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
						result = TreeConfigServer.this.invokeMethod(methodInvoke.getTarget(), methodInvoke.getMethod(), methodInvoke.getArgs());
					}
				}
				return result;
			}

			@Override
			protected Object getObject(String id) {
				if ("treeConfigManager".equals(id)) {
					return manager;
				}
				if ("treeConfigRegister".equals(id)) {
					return TreeConfigServer.this;
				}
				if ("treeConfigBinder".equals(id)) {
					return TreeConfigServer.this;
				}
				return manager;
			}

			@Override
			public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
				logger.error("Session exception , remote address [" + session.getRemoteAddress() + "] .", cause);
				session.close(true);
			}

			@Override
			public void sessionClosed(IoSession session) throws Exception {
				logger.debug("Session closed , remote address [{}] .", session.getRemoteAddress());
				// 防止在循环发通知的过程中，并发remove
				synchronized (TreeConfigServer.this) {
					clientMap.remove(session.getAttribute(ATTR_KEY_CLIENT_ID));
				}
			}

			@Override
			public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
				logger.warn("Session idle timeout , idle status [{}] , remote address [{}] .", status, session.getRemoteAddress());
				session.close(true);
			}

			@Override
			public void sessionCreated(IoSession session) throws Exception {
				logger.debug("Session created , remote address [{}] .", session.getRemoteAddress());
			}

		});
		logger.info("binding on port {}" , port);
		try {
			acceptor.bind(new InetSocketAddress(port));
			logger.info("tree configuartion server listening on {}" , port);
			executorService = new ThreadPoolExecutor(0, 10, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
		} catch (IOException e) {
			logger.error("tree configuartion start failed ." , e);
		}
	}

	public void registerClient(String clientId) {
		logger.debug("register client [{}] , remote address [{}] .", clientId, CommandContext.getIoSession().getRemoteAddress());
		CommandContext.getIoSession().setAttribute(ATTR_KEY_CLIENT_ID, clientId);
		clientMap.put(clientId, CommandContext.getIoSession());
	}

	private Object invokeMethod(Object target, String method, Object[] args) throws NoSuchMethodException, IllegalAccessException,
			InvocationTargetException {
		if (target == null) {
			throw new IllegalArgumentException(" target is null");
		}
		if (method == null) {
			throw new IllegalArgumentException(" method is null");
		}
		if (args == null || args.length == 0) {
			return MethodUtils.invokeMethod(target, method, args);
		} else {
			boolean hasNull = false;
			for (Object object : args) {
				if (object == null) {
					hasNull = true;
				}
			}
			if (!hasNull) {
				// 不为null可以根据参数找到精确的方法
				return MethodUtils.invokeMethod(target, method, args);
			} else {
				// 有null无法反射参数类型，只有根据名称找
				Class<?> targetClass = target.getClass();
				Method targetMethod = null;
				for (Method publicMethod : targetClass.getMethods()) {
					if (publicMethod.getName().equals(publicMethod)) {
						targetMethod = publicMethod;
						break;
					}
				}
				if (targetMethod == null) {
					throw new NoSuchMethodException("No such method: " + method + "() on object: " + targetClass.getName());
				}
				return targetMethod.invoke(target, args);
			}
		}
	}

	public void shutdown() {
		if (executorService != null) {
			executorService.shutdown();
		}
		acceptor.unbind();
		acceptor.dispose();
		clientMap.clear();
		notifyFailedTasks.clear();
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	class NotifyTaskExecute implements Runnable {
		private NotifyClientTask task;

		public NotifyTaskExecute(NotifyClientTask task) {
			this.task = task;
		}

		@Override
		public void run() {

		}
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
}

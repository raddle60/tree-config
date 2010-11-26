/**
 * 
 */
package com.raddle.config.tree.server;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
	private int timeoutSeconds = 3;
	private IoAcceptor acceptor = new NioSocketAcceptor();
	private TreeConfigManager manager = new MemoryConfigManager();
	private int port;
	private ExecutorService executorService = new ThreadPoolExecutor(0, 10, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
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
		logger.info("setting reader idle time five minute");
		// 读空闲10分钟
		acceptor.getSessionConfig().setReaderIdleTime(60 * 10);
		// hessain序列化
		acceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(new HessianEncoder(), new HessianDecoder()));
		// 方法调用
		acceptor.setHandler(new AbstractInvokeCommandHandler() {

			@Override
			protected Object invokeMethod(final MethodInvoke methodInvoke) throws Exception {
				// 放在最前面，通一个session过来的调用，先来的先通知
				final NotifyClientTask notifyTask = new NotifyClientTask(null, methodInvoke.getMethod(), methodInvoke.getArgs());
				Object result = TreeConfigServer.this.invokeMethod(methodInvoke.getTarget(), methodInvoke.getMethod(), methodInvoke.getArgs());
				if ("treeConfigManager".equals(methodInvoke.getTargetId())) {
					if (updateMethodSet.contains(methodInvoke.getMethod())) {
						for (final String clientId : clientMap.keySet()) {
							if (!clientId.equals(CommandContext.getIoSession().getAttribute(ATTR_KEY_CLIENT_ID))) {
								notifyTask.setClientId(clientId);
								executorService.execute(new Runnable() {
									@Override
									public void run() {
										SyncCommandSender sender = new SyncCommandSender(clientMap.get(clientId));
										try {
											sender.sendCommand("treeConfigManager", methodInvoke.getMethod(), methodInvoke.getArgs(), timeoutSeconds);
										} catch (Exception e) {
											logger.error(e.getMessage(), e);
											// 失败了放到失败队列
											// TODO 为每个client配置发送队列
											notifyFailedTasks.add(notifyTask);
										}
									}
								});
							}
						}
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
				clientMap.remove(session.getAttribute(ATTR_KEY_CLIENT_ID));
				logger.debug("Session closed , remote address [{}] .", session.getRemoteAddress());
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
	}

	public void registerClient(String clientId) {
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
		acceptor.unbind();
		acceptor.dispose();
		clientMap.clear();
		executorService.shutdown();
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

	public int getTimeoutSeconds() {
		return timeoutSeconds;
	}

	public void setTimeoutSeconds(int timeoutSeconds) {
		this.timeoutSeconds = timeoutSeconds;
	}
}

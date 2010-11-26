/**
 * 
 */
package com.raddle.config.tree.server;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
	private IoAcceptor acceptor = new NioSocketAcceptor();
	private TreeConfigManager manager = new MemoryConfigManager();
	private int port;

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
			protected Object invokeMethod(MethodInvoke methodInvoke) throws Exception {
				return TreeConfigServer.this.invokeMethod(methodInvoke.getTarget(), methodInvoke.getMethod(), methodInvoke.getArgs());
			}

			@Override
			protected Object getObject(String id) {
				if ("treeConfigManager".equals(id)) {
					return manager;
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

	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}
}

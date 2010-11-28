/**
 * 
 */
package com.raddle.config.tree.client.impl;

import java.io.Serializable;
import java.net.InetSocketAddress;
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
	public void bindDisconnectedValue(TreeConfigNode disconnectedValue, boolean includeNodeValue) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeAttributes(TreeConfigPath path, String... attributeNames) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean removeNode(TreeConfigPath path, boolean recursive) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean removeNodes(List<TreeConfigPath> paths, boolean recursive) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void saveAttribute(TreeConfigPath path, TreeConfigAttribute attribute) {
		// TODO Auto-generated method stub

	}

	@Override
	public void saveAttributeValue(TreeConfigPath path, String attributeName, Serializable value) {
		// TODO Auto-generated method stub

	}

	@Override
	public void saveNode(TreeConfigNode node, boolean updateNodeValue) {
		// TODO Auto-generated method stub

	}

	@Override
	public void saveNodeValue(TreeConfigPath path, Serializable value) {
		// TODO Auto-generated method stub

	}

	@Override
	public void saveNodes(List<TreeConfigNode> nodes, boolean updateNodeValue) {
		// TODO Auto-generated method stub

	}

	@Override
	public TreeConfigAttribute getAttribute(TreeConfigPath path, String attributeName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Serializable getAttributeValue(TreeConfigPath path, String attributeName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<TreeConfigNode> getChildren(TreeConfigPath path) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TreeConfigNode getNode(TreeConfigPath path) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Serializable getNodeValue(TreeConfigPath path) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isAttributesExist(TreeConfigPath path, String... attributeNames) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isNodeExist(TreeConfigPath path) {
		// TODO Auto-generated method stub
		return false;
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

}

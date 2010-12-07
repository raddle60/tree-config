/**
 * 
 */
package com.raddle.config.tree.client.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.raddle.config.tree.DefaultConfigNode;
import com.raddle.config.tree.DefaultConfigPath;
import com.raddle.config.tree.api.TreeConfigAttribute;
import com.raddle.config.tree.api.TreeConfigManager;
import com.raddle.config.tree.api.TreeConfigNode;
import com.raddle.config.tree.api.TreeConfigPath;
import com.raddle.config.tree.remote.utils.IpUtils;
import com.raddle.nio.mina.cmd.invoke.MethodInvoke;

/**
 * 方便在sping中使用，简化DefaultTreeConfigClient的初始化操作
 * 
 * @author xurong
 * 
 */
public class TreeConfigClientBean implements TreeConfigManager {
	private static final Logger logger = LoggerFactory.getLogger(TreeConfigClientBean.class);
	private static final Pattern xpath = Pattern.compile("([^@\\^=]+)(?:@([^@\\^=]+))?=([^@\\^=]+)(?:\\^(\\w+))?");
	private String clientId = UUID.randomUUID().toString();
	private String serverIp;
	private int serverPort = -1;
	private DefaultTreeConfigClient client = null;
	private TreeConfigManager localManager = null;
	private Map<String, String> substituteMap = new HashMap<String, String>();
	/**
	 * 感兴趣的节点，接收这些节点的更新通知
	 */
	private Set<String> interestNodes = new HashSet<String>();
	/**
	 * 本身提供的节点，在每次连接后，上传本地的节点值
	 */
	private Set<String> providedNodes = new HashSet<String>();
	/**
	 * 断开更新的节点，当断开服务器后，服务器更新这些节点
	 */
	private Set<String> disconnUpdateNodes = new HashSet<String>();
	/**
	 * 断开删除的节点，当断开服务器后，服务器删除这些节点
	 */
	private Set<String> disconnDeleteNodes = new HashSet<String>();

	/**
	 * 初始化配置，连接服务器
	 */
	public synchronized void init() {
		if (StringUtils.isBlank(serverIp)) {
			throw new IllegalArgumentException("server ip is empty");
		}
		if (serverPort == -1) {
			throw new IllegalArgumentException("server port is not assigned");
		}
		if (client == null) {
			client = new DefaultTreeConfigClient(clientId, serverIp, serverPort);
			if (localManager != null) {
				client.setLocalManager(localManager);
			}
			client.setListener(new TreeConfigClientListener() {
				private boolean inited = false;

				@Override
				public void sessionConnected(IoSession session) {
					if (!inited) {
						substituteMap.put("clientIp", IpUtils.getIpAddress(session.getLocalAddress()));
						// 接收通知的节点
						if (interestNodes != null) {
							for (String interestNode : interestNodes) {
								boolean recursive = false;
								DefaultConfigPath nodePath = new DefaultConfigPath();
								String[] ss = StringUtils.split(substitute(interestNode), ",");
								if (ss.length == 2) {
									nodePath.setSplitPath(ss[0]);
									recursive = "recursive".equalsIgnoreCase(ss[1]);
								} else if (ss.length == 1) {
									nodePath.setSplitPath(ss[0]);
								}
								client.bindInitialGetNodes(nodePath, recursive);
							}
						}
						// 提供的节点
						if (providedNodes != null) {
							for (String providedNode : providedNodes) {
								boolean recursive = false;
								DefaultConfigPath nodePath = new DefaultConfigPath();
								String[] ss = StringUtils.split(substitute(providedNode), ",");
								if (ss.length == 2) {
									nodePath.setSplitPath(ss[0]);
									recursive = "recursive".equalsIgnoreCase(ss[1]);
								} else if (ss.length == 1) {
									nodePath.setSplitPath(ss[0]);
								}
								client.bindInitialPushNodes(nodePath, recursive);
							}
						}
						if (disconnUpdateNodes != null) {
							if (disconnUpdateNodes != null) {
								for (String updateNode : disconnUpdateNodes) {
									String substituted = substitute(updateNode);
									Matcher matcher = xpath.matcher(substituted);
									if (matcher.matches()) {
										DefaultConfigNode node = new DefaultConfigNode();
										node.setNodePath(new DefaultConfigPath(matcher.group(1)));
										Serializable value = matcher.group(3);
										if (matcher.group(4) != null) {
											String type = matcher.group(4);
											String svalue = matcher.group(3);
											// 转类型
											if ("int".equals(type)) {
												value = new Integer(svalue);
											} else if ("int".equals(type)) {
												value = new Long(svalue);
											} else if ("float".equals(type)) {
												value = new Float(svalue);
											} else if ("double".equals(type)) {
												value = new Double(svalue);
											} else if ("decimal".equals(type)) {
												value = new BigDecimal(svalue);
											} else if ("boolean".equals(type)) {
												value = new Boolean(svalue);
											} else if ("date".equals(type)) {
												try {
													if (svalue.length() > 10) {
														value = DateUtils.parseDate(svalue, new String[] { "yyyy-M-d H:m:s", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss.SSS" });
													} else {
														value = DateUtils.parseDate(svalue, new String[] { "yyyy-M-d", "yyyy-MM-dd" });
													}
												} catch (ParseException e) {
													logger.warn(e.getMessage(), e);
												}
											}
										}
										if (matcher.group(2) != null) {
											node.setAttributeValue(matcher.group(2), value);
											client.bindDisconnectedNode(node, false);
										} else {
											node.setValue(value);
											client.bindDisconnectedNode(node, true);
										}
									} else {
										logger.warn("wrong update path [{}]", substituted);
									}
								}
							}
						}
						// 断开删除的节点
						if (disconnDeleteNodes != null) {
							for (String deleteNode : disconnDeleteNodes) {
								boolean recursive = false;
								DefaultConfigPath nodePath = new DefaultConfigPath();
								String[] ss = StringUtils.split(substitute(deleteNode), ",");
								if (ss.length == 2) {
									nodePath.setSplitPath(ss[0]);
									recursive = "recursive".equalsIgnoreCase(ss[1]);
								} else if (ss.length == 1) {
									nodePath.setSplitPath(ss[0]);
								}
								client.bindDisconnectedDelNode(nodePath, recursive);
							}
						}
						// ////////////
						inited = true;
					}
				}

				@Override
				public void commandReceived(MethodInvoke methodInvoke) {
				}

			});
			// ///
			client.connect();
		}
	}

	/**
	 * 释放连接和处理线程
	 */
	public synchronized void destroy() {
		if (client != null) {
			client.close();
			client = null;
		}
	}

	private String substitute(String string) {
		String substituted = string;
		for (String key : substituteMap.keySet()) {
			substituted = StringUtils.replace(substituted, "#" + key + "#", substituteMap.get(key));
		}
		return substituted;
	}

	/**
	 * 感兴趣的节点，接收这些节点的更新通知 格式：<br>
	 * /xx/xx,recursive
	 * 
	 * @param path
	 */
	public void addInterestNode(String path) {
		interestNodes.add(path);
	}

	/**
	 * 本身提供的节点，在每次连接后，上传本地的节点值 格式：<br>
	 * /xx/xx,recursive
	 * 
	 * @param path
	 */
	public void addProvidedNode(String path) {
		providedNodes.add(path);
	}

	/**
	 * 断开删除的节点，当断开服务器后，服务器删除这些节点 格式：<br>
	 * /xx/xx,recursive
	 * 
	 * @param path
	 */
	public void addDisconnDeleteNode(String path) {
		disconnDeleteNodes.add(path);
	}

	/**
	 * 断开更新的节点，当断开服务器后，服务器更新这些节点<br>
	 * 格式：<br>
	 * /xx/xx=value^type<br>
	 * /xx/xx@prop=value^type<br>
	 * #type不填默认是String，type可选值为:int,long,float,double,decimal,boolean,date
	 * 
	 * @param pathedValue
	 */
	public void addDisconnUpdateNode(String pathedValue) {
		disconnUpdateNodes.add(pathedValue);
	}

	@Override
	public void removeAttributes(TreeConfigPath path, String... attributeNames) {
		client.removeAttributes(path, attributeNames);
	}

	@Override
	public boolean removeNode(TreeConfigPath path, boolean recursive) {
		return client.removeNode(path, recursive);
	}

	@Override
	public boolean removeNodes(List<TreeConfigPath> paths, boolean recursive) {
		return client.removeNodes(paths, recursive);
	}

	@Override
	public void saveAttribute(TreeConfigPath path, TreeConfigAttribute attribute) {
		client.saveAttribute(path, attribute);
	}

	@Override
	public void saveAttributeValue(TreeConfigPath path, String attributeName, Serializable value) {
		client.saveAttributeValue(path, attributeName, value);
	}

	@Override
	public void saveNode(TreeConfigNode node, boolean updateNodeValue) {
		client.saveNode(node, updateNodeValue);
	}

	@Override
	public void saveNodeValue(TreeConfigPath path, Serializable value) {
		client.saveNodeValue(path, value);
	}

	@Override
	public void saveNodes(List<TreeConfigNode> nodes, boolean updateNodeValue) {
		client.saveNodes(nodes, updateNodeValue);
	}

	@Override
	public TreeConfigAttribute getAttribute(TreeConfigPath path, String attributeName) {
		return client.getAttribute(path, attributeName);
	}

	@Override
	public Serializable getAttributeValue(TreeConfigPath path, String attributeName) {
		return client.getAttributeValue(path, attributeName);
	}

	@Override
	public List<TreeConfigNode> getChildren(TreeConfigPath path) {
		return client.getChildren(path);
	}

	@Override
	public List<TreeConfigNode> getDescendants(TreeConfigPath path) {
		return client.getDescendants(path);
	}

	@Override
	public TreeConfigNode getNode(TreeConfigPath path) {
		return client.getNode(path);
	}

	@Override
	public Serializable getNodeValue(TreeConfigPath path) {
		return client.getNodeValue(path);
	}

	@Override
	public boolean isAttributesExist(TreeConfigPath path, String... attributeNames) {
		return client.isAttributesExist(path, attributeNames);
	}

	@Override
	public boolean isNodeExist(TreeConfigPath path) {
		return client.isNodeExist(path);
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getServerIp() {
		return serverIp;
	}

	public void setServerIp(String serverIp) {
		this.serverIp = serverIp;
	}

	public int getServerPort() {
		return serverPort;
	}

	public void setServerPort(int serverPort) {
		this.serverPort = serverPort;
	}

	public Set<String> getInterestNodes() {
		return interestNodes;
	}

	public void setInterestNodes(Set<String> interestNodes) {
		this.interestNodes = interestNodes;
	}

	public Set<String> getProvidedNodes() {
		return providedNodes;
	}

	public void setProvidedNodes(Set<String> providedNodes) {
		this.providedNodes = providedNodes;
	}

	public Set<String> getDisconnDeleteNodes() {
		return disconnDeleteNodes;
	}

	public void setDisconnDeleteNodes(Set<String> disconnDeleteNodes) {
		this.disconnDeleteNodes = disconnDeleteNodes;
	}

	public TreeConfigManager getLocalManager() {
		if (localManager == null) {
			return client.getLocalManager();
		}
		return localManager;
	}

	public void setLocalManager(TreeConfigManager localManager) {
		this.localManager = localManager;
		if (client != null) {
			client.setLocalManager(localManager);
		}
	}

	public Map<String, String> getSubstituteMap() {
		return substituteMap;
	}

}

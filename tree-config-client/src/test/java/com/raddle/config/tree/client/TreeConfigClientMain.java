package com.raddle.config.tree.client;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

import org.apache.commons.lang.time.DateFormatUtils;

import com.raddle.config.tree.DefaultConfigNode;
import com.raddle.config.tree.DefaultConfigPath;
import com.raddle.config.tree.client.impl.TreeConfigClientBean;

public class TreeConfigClientMain {
	public static void main(String[] args) {
		TreeConfigClientBean client = new TreeConfigClientBean();
		client.setServerIp("127.0.0.1");
		client.setServerPort(9877);
		String localIp = null;
		try {
			localIp = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		client.getSubstituteMap().put("localIp", localIp);
		client.addProvidedNode("testing/client/#localIp#");
		client.addDisconnDeleteNode("testing/client/#localIp#/todel");
		client.addDisconnUpdateNode("testing/client/#localIp#@isConnected=false^boolean");
		client.addDisconnUpdateNode("testing/client/#localIp#="+DateFormatUtils.format(new Date(), "yyyy-M-d HH:mm:ss.SSS")+"^date");
		///// 
		client.init();
		// 初始化节点
		DefaultConfigNode clientNode = new DefaultConfigNode();
		clientNode.setNodePath(new DefaultConfigPath("testing/client/" + localIp));
		clientNode.setAttributeValue("isConnected", true);
		clientNode.setValue(new Date());
		client.saveNode(clientNode, true);
		//
		DefaultConfigNode delNode = new DefaultConfigNode();
		delNode.setNodePath(new DefaultConfigPath("testing/client/" + localIp + "/todel"));
		delNode.setAttributeValue("xxx", "fff");
		client.saveNode(delNode, false);
		try {
			Thread.sleep(5 * 1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		client.destroy();
		/*
		final DefaultTreeConfigClient client = new DefaultTreeConfigClient("127.0.0.1", 9877);
		// 可以在这里初始化
		// 但是为了获得连接的ip地址，所以放到了连接已后才初始化
		// ///////////////////////
		client.setListener(new TreeConfigClientListener() {
			private boolean init = false;
			@Override
			public void sessionConnected(IoSession session) {
				DefaultConfigPath configPath = new DefaultConfigPath();
				// 获得当前链接的ip地址，每次连接可能会变，所以每次都设置
				configPath.setSplitPath("testing/client/" + IpUtils.getIpAddress(session.getLocalAddress()));
				// 只初始化一次
				if (!init) {
					// 初始化节点
					DefaultConfigNode clientNode = new DefaultConfigNode();
					clientNode.setNodePath(configPath);
					clientNode.setAttributeValue("isConnected", true);
					client.getLocalManager().saveNode(clientNode, true);
					//
					DefaultConfigNode delNode = new DefaultConfigNode();
					delNode.setNodePath(new DefaultConfigPath("testing/client/" + IpUtils.getIpAddress(session.getLocalAddress()) + "/todel"));
					delNode.setAttributeValue("xxx", "fff");
					client.getLocalManager().saveNode(delNode, true);
					// 推送的节点
					client.bindInitialPushNodes(clientNode.getNodePath(), true);
					client.bindInitialPushNodes(delNode.getNodePath(), true);
					// 绑定初始化
					// ////////////////////////
					// 断开更新
					DefaultConfigNode disconnectedNode = new DefaultConfigNode();
					disconnectedNode.setNodePath(configPath);
					disconnectedNode.setAttributeValue("isConnected", false);
					client.bindDisconnectedNode(disconnectedNode, false);
					// 断开删除
					DefaultConfigNode disconnectedDelNode = new DefaultConfigNode();
					disconnectedDelNode.setNodePath(new DefaultConfigPath("testing/client/" + IpUtils.getIpAddress(session.getLocalAddress())+"/todel"));
					client.bindDisconnectedDelNode(disconnectedDelNode, false);
					// ///////////////////////
					// 初始获得所有节点
					client.bindInitialGetNodes(null, true);
					init = true;
				}
			}

			@Override
			public void commandReceived(MethodInvoke methodInvoke) {

			}
		});
		// ///////////////////////
		client.connect();
		try {
			Thread.sleep(5 * 1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		client.close();
		*/
	}
}

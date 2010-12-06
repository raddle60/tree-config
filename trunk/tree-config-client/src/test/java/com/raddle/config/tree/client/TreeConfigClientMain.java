package com.raddle.config.tree.client;

import org.apache.mina.core.session.IoSession;
import org.omg.CORBA.BooleanHolder;

import com.raddle.config.tree.DefaultConfigNode;
import com.raddle.config.tree.DefaultConfigPath;
import com.raddle.config.tree.client.impl.DefaultTreeConfigClient;
import com.raddle.config.tree.client.impl.TreeConfigClientListener;
import com.raddle.config.tree.remote.utils.IpUtils;
import com.raddle.nio.mina.cmd.invoke.MethodInvoke;

public class TreeConfigClientMain {
	public static void main(String[] args) {
		final DefaultTreeConfigClient client = new DefaultTreeConfigClient("127.0.0.1", 9877);
		final BooleanHolder init = new BooleanHolder(false);
		// ///////////////////////
		client.setListener(new TreeConfigClientListener() {

			@Override
			public void sessionConnected(IoSession session) {
				DefaultConfigPath configPath = new DefaultConfigPath();
				// 获得当前链接的ip地址，每次连接可能会变，所以每次都设置
				configPath.setSplitPath("testing/client/" + IpUtils.getIpAddress(session.getLocalAddress()));
				// 只初始化一次
				if (!init.value) {
					// 待更新的节点
					DefaultConfigNode clientNode = new DefaultConfigNode();
					clientNode.setNodePath(configPath);
					clientNode.setAttributeValue("isConnected", true);
					client.getLocalManager().saveNode(clientNode, true);
					client.bindInitialPushNodes(clientNode.getNodePath(), true);
					// 待删除的节点
					DefaultConfigNode delNode = new DefaultConfigNode();
					delNode.setNodePath(new DefaultConfigPath("testing/client/" + IpUtils.getIpAddress(session.getLocalAddress()) + "/todel"));
					delNode.setAttributeValue("xxx", "fff");
					client.getLocalManager().saveNode(delNode, true);
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
		System.out.println();
	}
}

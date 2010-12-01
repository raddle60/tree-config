package com.raddle.config.tree.client;

import java.net.InetSocketAddress;

import org.apache.mina.core.session.IoSession;
import org.omg.CORBA.BooleanHolder;

import com.raddle.config.tree.DefaultConfigNode;
import com.raddle.config.tree.DefaultConfigPath;
import com.raddle.config.tree.client.impl.DefaultTreeConfigClient;
import com.raddle.config.tree.client.impl.TreeConfigClientListener;
import com.raddle.nio.mina.cmd.invoke.MethodInvoke;

public class TreeConfigClientMain {
	public static void main(String[] args) {
		final DefaultTreeConfigClient client = new DefaultTreeConfigClient("127.0.0.1", 9877);
		final BooleanHolder init = new BooleanHolder(false);
		final DefaultConfigPath configPath = new DefaultConfigPath();
		// ///////////////////////
		client.setListener(new TreeConfigClientListener() {

			@Override
			public void sessionConnected(IoSession session) {
				// 获得当前链接的ip地址，每次连接可能会变，所以每次都设置
				configPath.setSplitPath("testing/client/" + ((InetSocketAddress) session.getLocalAddress()).getAddress().getHostAddress());
				if (!init.value) {
					// 只初始化一次
					DefaultConfigNode clientNode = new DefaultConfigNode();
					clientNode.setNodePath(configPath);
					clientNode.setAttributeValue("isConnected", true);
					client.getLocalManager().saveNode(clientNode, true);
					client.bindInitialPushNodes(clientNode.getNodePath(), false);
					// ////////////////////////
					DefaultConfigNode disconnectedNode = new DefaultConfigNode();
					disconnectedNode.setNodePath(configPath);
					disconnectedNode.setAttributeValue("isConnected", false);
					client.bindDisconnectedNode(disconnectedNode, false);
					// ///////////////////////
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
	}
}

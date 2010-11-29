package com.raddle.config.tree.client;

import com.raddle.config.tree.DefaultConfigNode;
import com.raddle.config.tree.DefaultConfigPath;
import com.raddle.config.tree.client.impl.DefaultTreeConfigClient;

public class TreeConfigClientMain {
	public static void main(String[] args) {
		DefaultTreeConfigClient client = new DefaultTreeConfigClient("127.0.0.1", 9877);
		String clientNo = "client" + System.currentTimeMillis();
		//////////////////////////
		DefaultConfigNode clientNode = new DefaultConfigNode();
		clientNode.setNodePath(new DefaultConfigPath("tree-config/client/" + clientNo));
		clientNode.setAttributeValue("isConnected", true);
		client.getLocalManager().saveNode(clientNode, true);
		client.bindInitialPushNodes(new DefaultConfigPath("tree-config/client/" + clientNo), false);
		//////////////////////////
		DefaultConfigNode disconnectedNode = new DefaultConfigNode();
		disconnectedNode.setNodePath(new DefaultConfigPath("tree-config/client/" + clientNo));
		disconnectedNode.setAttributeValue("isConnected", false);
		client.bindDisconnectedNode(disconnectedNode, false);
		/////////////////////////
		client.bindInitialPushNodes(null, true);
		/////////////////////////
		client.connect();
		try {
			Thread.sleep(5*1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		client.close();
	}
}

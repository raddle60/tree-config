package com.raddle.config.tree.client;

import com.raddle.config.tree.client.impl.DefaultTreeConfigClient;

public class TreeConfigClientMain {
	public static void main(String[] args) {
		DefaultTreeConfigClient client = new DefaultTreeConfigClient("127.0.0.1", 9877);
		client.connect();
		client.close();
	}
}

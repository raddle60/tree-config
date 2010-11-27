package com.raddle.config.tree.server;

public class TreeConfigServerMain {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		TreeConfigServer server = new TreeConfigServer();
		server.start();
		server.shutdown();
	}

}

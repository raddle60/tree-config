package com.raddle.config.tree.server;

public class DefaultTreeConfigServerMain {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		DefaultTreeConfigServer server = new DefaultTreeConfigServer();
		server.start();
		try {
			Thread.sleep(10 * 1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		server.shutdown();
	}

}

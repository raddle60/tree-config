package com.raddle.config.tree.server;

public class DefaultTreeConfigServerMain {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		final DefaultTreeConfigServer server = new DefaultTreeConfigServer();
		server.start();
		Runtime.getRuntime().addShutdownHook(new Thread(){
			@Override
			public void run() {
				server.shutdown();
			}
		});
	}

}

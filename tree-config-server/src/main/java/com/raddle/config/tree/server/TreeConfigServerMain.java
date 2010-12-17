/**
 * 
 */
package com.raddle.config.tree.server;


/**
 * @author xurong
 * 
 */
public class TreeConfigServerMain {

	public static void main(String[] args) {
		final DefaultTreeConfigServer server = new DefaultTreeConfigServer();
		if (args != null && args.length > 0) {
			server.setPort(Integer.parseInt(args[0]));
		}
		server.start();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				server.shutdown();
			}
		});
	}

}

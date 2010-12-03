/**
 * 
 */
package com.raddle.config.tree.remote.utils;

import org.apache.mina.core.session.IoSession;
import org.omg.CORBA.IntHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.raddle.nio.mina.cmd.SessionCommandSender;
import com.raddle.nio.mina.cmd.api.CommandCallback;

/**
 * @author xurong
 * 
 */
public class RemoteUtils {
	private static final Logger logger = LoggerFactory.getLogger(RemoteUtils.class);

	/**
	 * ping 另一端，失败后自动关闭
	 * 
	 * @param session
	 */
	public static void pingAndCloseIfFailed(IoSession session) {
		try {
			final Object pingWaitor = new Object();
			final IntHolder pingFailedTimes = new IntHolder(0);
			int pingTimeoutSeconds = 5;
			for (int i = 0; i < 3; i++) {
				SessionCommandSender sender = new SessionCommandSender(session);
				sender.sendCommand("ping", pingTimeoutSeconds, new CommandCallback<String, Object>() {

					@Override
					public void commandResponse(String command, Object response) {
						synchronized (pingWaitor) {
							pingFailedTimes.value = 0;
							pingWaitor.notify();
						}
					}

					@Override
					public void responseException(String command, String type, String message) {
						synchronized (pingWaitor) {
							pingFailedTimes.value = 0;
							pingWaitor.notify();
						}
					}

					@Override
					public void responseTimeout(String command) {
						synchronized (pingWaitor) {
							pingFailedTimes.value++;
							pingWaitor.notify();
						}
					}
				});
				// 等待结果返回
				synchronized (pingWaitor) {
					try {
						pingWaitor.wait(pingTimeoutSeconds * 1000 + 500);
					} catch (InterruptedException e) {
						logger.warn("waiting for ping interrupted");
					}
				}
				if (pingFailedTimes.value >= 3) {
					// 3次超时，认为断开
					session.close(true);
				} else if (pingFailedTimes.value == 0) {
					// 通了直接跳出
					break;
				}
			}
		} catch (Throwable e) {
			logger.error("ping failed," + e.getMessage(), e);
		}
	}
}

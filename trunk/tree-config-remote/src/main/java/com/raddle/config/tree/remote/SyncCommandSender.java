/**
 * 
 */
package com.raddle.config.tree.remote;

import org.apache.mina.core.session.IoSession;
import org.omg.CORBA.BooleanHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.raddle.config.tree.remote.exception.RemoteExecuteException;
import com.raddle.config.tree.remote.exception.ResponseTimeoutException;
import com.raddle.nio.mina.cmd.SessionCommandSender;
import com.raddle.nio.mina.cmd.api.CommandCallback;
import com.raddle.nio.mina.cmd.api.CommandSender;
import com.raddle.nio.mina.cmd.invoke.InvokeCommand;

/**
 * @author xurong
 * 
 */
public class SyncCommandSender {
	private static final Logger logger = LoggerFactory.getLogger(SyncCommandSender.class);
	private CommandSender commandSender;

	public SyncCommandSender(IoSession session) {
		this.commandSender = new SessionCommandSender(session);
	}

	public Object sendCommand(String targetId, String method, Object[] args, final int timeoutSeconds) throws RemoteExecuteException, ResponseTimeoutException{
		logger.debug("send command target:{} , method:{}" , targetId, method);
		final ObjectHolder ret = new ObjectHolder();
		final ObjectHolder exception = new ObjectHolder();
		final BooleanHolder isTimeout = new BooleanHolder(false);
		InvokeCommand command = new InvokeCommand();
		command.setTargetId(targetId);
		command.setMethod(method);
		command.setArgs(args);
		commandSender.sendCommand(command, timeoutSeconds, new CommandCallback<InvokeCommand, Object>() {

			@Override
			public void commandResponse(InvokeCommand command, Object response) {
				ret.setValue(response);
				synchronized (ret) {
					ret.notify();
				}
			}

			@Override
			public void responseException(InvokeCommand command, String type, String message) {
				exception.setValue("方法调用返回异常[" + type + "]:" + message);
				synchronized (ret) {
					ret.notify();
				}
			}

			@Override
			public void responseTimeout(InvokeCommand command) {
				exception.setValue("方法调用返回超时,设定的超时时间" + timeoutSeconds + "秒");
				isTimeout.value = true;
				synchronized (ret) {
					ret.notify();
				}
			}

		});
		// 等待结果返回
		synchronized (ret) {
			try {
				ret.wait(timeoutSeconds * 1000 + 500);
			} catch (InterruptedException e) {
				logger.debug("waiting result for command target:{} , method:{} interrupted" , targetId, method);
				throw new ResponseTimeoutException(e.getMessage(), e);
			}
		}
		// 调用异常
		if (exception.getValue() != null) {
			if(isTimeout.value){
				logger.debug("receive command target:{} , method:{} timeout" , targetId, method);
				throw new ResponseTimeoutException(exception.getValue() + "");
			} else {
				logger.debug("execute command target:{} , method:{} , client has exception" , targetId, method);
				throw new RemoteExecuteException(exception.getValue() + "");
			}
		}
		logger.debug("execute command target:{} , method:{} successed , return {}" , new Object[]{targetId, method, ret.getValue() == null?"null":"not null"});
		return ret.getValue();
	}

	class ObjectHolder {
		private Object value;

		public Object getValue() {
			return value;
		}

		public void setValue(Object value) {
			this.value = value;
		}
	}
}

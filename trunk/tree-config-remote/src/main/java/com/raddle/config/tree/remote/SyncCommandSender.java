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
import com.raddle.config.tree.utils.ReflectToStringBuilder;
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

	public Object sendCommand(String targetId, String method, Object[] args, final int timeoutSeconds) throws RemoteExecuteException, ResponseTimeoutException {
		if (logger.isDebugEnabled()) {
			logger.debug("send command target:{} , method:{} , args :{}" , new Object[] { targetId, method, ReflectToStringBuilder.reflectToString(args) });
		}
		final ObjectHolder ret = new ObjectHolder();
		final ObjectHolder exception = new ObjectHolder();
		final BooleanHolder isTimeout = new BooleanHolder(false);
		final BooleanHolder isResponse = new BooleanHolder(false);
		InvokeCommand command = new InvokeCommand();
		command.setTargetId(targetId);
		command.setMethod(method);
		command.setArgs(args);
		commandSender.sendCommand(command, timeoutSeconds, new CommandCallback<InvokeCommand, Object>() {

			@Override
			public void commandResponse(InvokeCommand command, Object response) {
				isResponse.value = true;
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
				if (logger.isDebugEnabled()) {
					logger.debug("waiting result for command interrupted , target:{} , method:{} , args :{}" , new Object[] { targetId, method, ReflectToStringBuilder.reflectToString(args) });
				}
				throw new ResponseTimeoutException(e.getMessage() +", "+ ReflectToStringBuilder.reflectToString(command), e);
			}
		}
		// 调用异常
		if (exception.getValue() != null) {
			if(isTimeout.value){
				if (logger.isDebugEnabled()) {
					logger.debug("receive command timeout, target:{} , method:{} , args :{}" , new Object[] { targetId, method, ReflectToStringBuilder.reflectToString(args) });
				}
				throw new ResponseTimeoutException(exception.getValue() + " , "+ ReflectToStringBuilder.reflectToString(command));
			} else {
				if (logger.isDebugEnabled()) {
					logger.debug("execute command has client exception ,target:{} , method:{} , args :{}" , new Object[] { targetId, method, ReflectToStringBuilder.reflectToString(args) });
				}
				throw new RemoteExecuteException(exception.getValue() + " , "+ ReflectToStringBuilder.reflectToString(command));
			}
		}
		if(isResponse.value){
			if (logger.isDebugEnabled()) {
				logger.debug("execute command successed, target:{} , method:{} , args :{} , return {}" , new Object[] { targetId, method, ReflectToStringBuilder.reflectToString(args), ReflectToStringBuilder.reflectToString(ret.getValue())});
			}
			return ret.getValue();
		} else {
			if (logger.isDebugEnabled()) {
				logger.debug("execute command timeout, target:{} , method:{} , args :{} , return {}" , new Object[] { targetId, method, ReflectToStringBuilder.reflectToString(args), ReflectToStringBuilder.reflectToString(ret.getValue())});
			}
			throw new ResponseTimeoutException("方法调用返回超时,设定的超时时间" + timeoutSeconds + "秒 , "+ ReflectToStringBuilder.reflectToString(command));
		}
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

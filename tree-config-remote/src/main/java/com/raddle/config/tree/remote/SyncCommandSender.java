/**
 * 
 */
package com.raddle.config.tree.remote;

import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.raddle.config.tree.remote.exception.RemoteExecuteException;
import com.raddle.config.tree.remote.exception.ResponseTimeoutException;
import com.raddle.config.tree.utils.ReflectToStringBuilder;
import com.raddle.nio.mina.cmd.SessionCommandSender;
import com.raddle.nio.mina.cmd.api.CommandSender;
import com.raddle.nio.mina.cmd.invoke.InvokeCommand;
import com.raddle.nio.mina.exception.ReceivedClientException;
import com.raddle.nio.mina.exception.WaitingTimeoutException;

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
		InvokeCommand command = new InvokeCommand();
		command.setTargetId(targetId);
		command.setMethod(method);
		command.setArgs(args);
		try {
			Object ret = commandSender.sendSyncCommand(command, timeoutSeconds);
			if (logger.isDebugEnabled()) {
				logger.debug("execute command successed, target:{} , method:{} , args :{} , return {}" , new Object[] { targetId, method, ReflectToStringBuilder.reflectToString(args), ReflectToStringBuilder.reflectToString(ret)});
			}
			return ret;
		} catch (WaitingTimeoutException e) {
			if (logger.isDebugEnabled()) {
				logger.debug("execute command timeout, target:{} , method:{} , args :{} " , new Object[] { targetId, method, ReflectToStringBuilder.reflectToString(args)});
			}
			throw new ResponseTimeoutException("方法调用返回超时,设定的超时时间" + timeoutSeconds + "秒 , " + "targetId:" + command.getTargetId() + ", method:" + command.getMethod());
		} catch (ReceivedClientException e) {
			if (logger.isDebugEnabled()) {
				logger.debug("execute command has client exception ,target:{} , method:{} , args :{}" , new Object[] { targetId, method, ReflectToStringBuilder.reflectToString(args) });
			}
			throw new RemoteExecuteException(e.getType() ,e.getType()+", "+e.getMessage() + ", targetId:" + command.getTargetId() + ", method:" + command.getMethod());
		}
	}
}

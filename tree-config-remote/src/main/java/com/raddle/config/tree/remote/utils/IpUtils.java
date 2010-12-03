package com.raddle.config.tree.remote.utils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class IpUtils {
	/**
	 * 获得ip地址的字符串
	 * 
	 * @param address
	 * @return
	 */
	public static String getIpAddress(SocketAddress address) {
		if (address == null) {
			return null;
		}
		if (address instanceof InetSocketAddress) {
			return ((InetSocketAddress) address).getAddress().getHostAddress();
		}
		return null;
	}

	/**
	 * 获得地址的端口号，获得失败，返回-1
	 * 
	 * @param address
	 * @return
	 */
	public static int getAddressPort(SocketAddress address) {
		if (address == null) {
			return -1;
		}
		if (address instanceof InetSocketAddress) {
			return ((InetSocketAddress) address).getPort();
		}
		return -1;
	}

	/**
	 * 获得ip和端口号
	 * 
	 * @param address
	 * @return ip:port
	 */
	public static String getIpAndPort(SocketAddress address) {
		return getIpAddress(address) + ":" + getAddressPort(address);
	}
}

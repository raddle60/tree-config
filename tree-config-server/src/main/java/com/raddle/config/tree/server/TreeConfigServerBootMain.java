package com.raddle.config.tree.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TreeConfigServerBootMain {
	public static void main(String[] args) {
		if (args == null || args.length == 0) {
			System.out.println("缺少参数：start 端口号 或 stop");
			return;
		}
		if ("start".equals(args[0])) {
			if (args.length < 2) {
				System.out.println("缺少参数：端口号");
				return;
			}
			// 判断pid
			File pidFile = new File("tree-server.pid");
			if (pidFile.exists()) {
				System.out.println("配置服务器没有正确关闭，进程文件:[" + pidFile.getAbsolutePath() + "]已存在, 请关闭后再启动");
				return;
			} else {
				try {
					Set<String> libDirs = new LinkedHashSet<String>();
					libDirs.add("lib");
					Set<String> classpathes = new LinkedHashSet<String>();
					classpathes.add("classes");
					classpathes.add("config");
					StringBuffer command = new StringBuffer();
					command.append("java -cp ");
					appendCpDirs(classpathes, libDirs, command, ":");
					command.append(" " + TreeConfigServerMain.class.getName() + " " + Integer.parseInt(args[1]));
					command.append(" >> tree_config_server.log 2>&1 &");
					Process exec = Runtime.getRuntime().exec(command.toString());
					Field f = exec.getClass().getDeclaredField("pid");
					f.setAccessible(true);
					int pid = (Integer) f.get(exec);
					System.out.println("PID: " + pid);
					System.out.println("Command: " + command);
					FileWriter writer = new FileWriter(pidFile);
					writer.write((Integer) f.get(exec) + "");
					writer.close();
					System.out.println("配置服务器已启动");
				} catch (Exception e) {
					System.out.println("配置服务器启动失败，" + e.getMessage());
					return;
				}
			}
		} else if ("stop".equals(args[0])) {
			File pidFile = new File("tree-server.pid");
			if (!pidFile.exists()) {
				System.out.println("服务器没有启动，进程文件:[" + pidFile.getAbsolutePath() + "]不存在");
				return;
			} else {
				try {
					BufferedReader reader = new BufferedReader(new FileReader(pidFile));
					int pid = Integer.parseInt(reader.readLine());
					reader.close();
					Runtime.getRuntime().exec("kill " + pid).waitFor();
					System.out.println("kill " + pid);
					pidFile.delete();
					System.out.println("配置服务器已停止");
				} catch (Exception e) {
					System.out.println("停止配置服务器失败，" + e.getMessage());
					return;
				}
			}
		} else {
			System.out.println("不支持的命令：" + args[0]);
		}
	}

	private static void appendCpDirs(Set<String> classpathes, Set<String> libDirs, StringBuffer commandLine, String spliter) {
		for (String cls : classpathes) {
			if (new File(cls).isDirectory()) {
				commandLine.append(cls).append(spliter);
			}
		}
		for (String dir : libDirs) {
			File libDir = new File(dir);
			if (libDir.isDirectory()) {
				List<File> fileList = getLibFile(libDir);
				for (File file : fileList) {
					commandLine.append(file.getAbsolutePath().substring(libDir.getAbsolutePath().length() - libDir.getName().length())).append(spliter);
				}
			}
		}
		commandLine.deleteCharAt(commandLine.length() - 1);
	}

	private static List<File> getLibFile(File libDir) {
		List<File> fileList = new ArrayList<File>();
		File[] subFileList = libDir.listFiles();
		for (int i = 0; i < subFileList.length; i++) {
			File file = subFileList[i];
			if (file.isFile()) {
				if (file.getName().endsWith(".jar") || file.getName().endsWith(".zip")) {
					fileList.add(file);
				}
			} else if (file.isDirectory()) {
				fileList.addAll(getLibFile(file));
			}
		}
		return fileList;
	}
}

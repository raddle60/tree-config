/**
 * 
 */
package com.raddle.config.tree.ui;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.omg.CORBA.IntHolder;

/**
 * @author xurong
 * 
 */
public class ConsoleUtils {
	public static void redirectConsole(final JTextComponent textComponent) {
		final PipedInputStream pipledInputStream = new PipedInputStream();
		final PipedOutputStream pipedOutputStream = new PipedOutputStream();
		// 需要用buffer缓冲
		// 不用缓冲在swing线程阻塞的情况下，PipedOutputStream会断开pipe
		final ByteArrayOutputStreamEx byteArrayOutputStream = new ByteArrayOutputStreamEx();
		final IntHolder bufferSize = new IntHolder(0);
		try {
			pipedOutputStream.connect(pipledInputStream);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null, "连接失败：" + e);
		}
		PrintStream ps = new PrintStream(byteArrayOutputStream);
		System.setOut(ps);
		System.setErr(ps);
		Thread byteThread = new Thread() {
			@Override
			public void run() {
				while (true) {
					if (byteArrayOutputStream.size() > 0) { // 检查流里面的字节
						byte buffer[] = null;
						synchronized (byteArrayOutputStream) {
							buffer = byteArrayOutputStream.toByteArray();
							byteArrayOutputStream.reset(); // 清空缓冲区
						}
						try {
							pipedOutputStream.write(buffer, 0, buffer.length); // 把提取的流发送到pipedOS
						} catch (IOException e) {
						}
					} else {
						// 没有数据可用，线程进入休眠状态
						if (byteArrayOutputStream.getBufferSize() > bufferSize.value) {
							bufferSize.value = byteArrayOutputStream.getBufferSize();
							System.out.println("current byteArrayOutputStream for console buffer size " + byteArrayOutputStream.getBufferSize());
						}
						try {
							Thread.sleep(500); // 休眠1秒
						} catch (InterruptedException e) {
						}
					}
				}
			}
		};
		byteThread.setDaemon(true);
		byteThread.start();
		Thread pipeThread = new Thread() {
			@Override
			public void run() {
				BufferedReader reader = new BufferedReader(new InputStreamReader(pipledInputStream));
				String s = null;
				try {
					while ((s = reader.readLine()) != null) {
						final String line = s + "\n";
						SwingUtilities.invokeLater(new Runnable() {
							@Override
							public void run() {
								Document doc = textComponent.getDocument();
								if (doc != null) {
									try {
										doc.insertString(doc.getLength(), line, null);
									} catch (BadLocationException e) {
									}
								}
								if (doc.getLength() > 20000) {
									try {
										doc.remove(0, 15000);
									} catch (BadLocationException e) {
									}
								}
								textComponent.setCaretPosition(doc.getLength());
							}
						});
					}
				} catch (IOException e) {
					JOptionPane.showMessageDialog(null, "从BufferedReader读取错误：" + e);
				}
			}
		};
		pipeThread.setDaemon(true);
		pipeThread.start();
	}

	private static class ByteArrayOutputStreamEx extends ByteArrayOutputStream {
		/**
		 * 返回buffer的大小，不是有效数据的大小
		 * 
		 * @return
		 */
		public int getBufferSize() {
			return buf.length;
		}
	}
}

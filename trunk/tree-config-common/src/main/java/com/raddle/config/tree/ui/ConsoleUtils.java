/**
 * 
 */
package com.raddle.config.tree.ui;

import java.io.BufferedReader;
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

/**
 * @author xurong
 * 
 */
public class ConsoleUtils {
	public static void redirectConsole(final JTextComponent textComponent) {
		final PipedInputStream pipledInputStream = new PipedInputStream();
		final PipedOutputStream pipedOutputStream = new PipedOutputStream();
		try {
			pipedOutputStream.connect(pipledInputStream);
		} catch (Exception e) {
		}
		PrintStream ps = new PrintStream(pipedOutputStream);
		System.setOut(ps);
		System.setErr(ps);
		Thread outputThread = new Thread() {
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
		outputThread.setDaemon(true);
		outputThread.start();
	}
}

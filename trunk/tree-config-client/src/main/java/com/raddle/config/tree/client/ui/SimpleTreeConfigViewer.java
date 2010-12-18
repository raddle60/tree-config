/**
 * 
 */
package com.raddle.config.tree.client.ui;

import java.awt.Rectangle;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import com.raddle.config.tree.DefaultConfigPath;
import com.raddle.config.tree.api.TreeConfigAttribute;
import com.raddle.config.tree.api.TreeConfigListener;
import com.raddle.config.tree.api.TreeConfigNode;
import com.raddle.config.tree.client.impl.DefaultTreeConfigClient;
import com.raddle.config.tree.local.MemoryConfigManager;
import com.raddle.config.tree.ui.ConsoleUtils;
import com.raddle.config.tree.utils.ReflectToStringBuilder;
import com.raddle.config.tree.utils.TreeUtils;

/**
 * @author xurong
 *
 */
public class SimpleTreeConfigViewer {
	static{
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private JFrame jFrame = null;  //  @jve:decl-index=0:visual-constraint="10,10"
	private JTabbedPane jTabbedPane = null;
	private JSplitPane jSplitPane = null;
	private JDesktopPane jDesktopPane = null;
	private JSplitPane jSplitPane1 = null;
	private JScrollPane jScrollPane = null;
	private JTree jTree = null;
	private JScrollPane jScrollPane1 = null;
	private JTextPane jTextPane = null;
	/**
	 * This method initializes jFrame
	 * 
	 * @return javax.swing.JFrame
	 */
	private JFrame getJFrame() {
		if (jFrame == null) {
			jFrame = new JFrame();
			jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			jFrame.setSize(706, 538);
			jFrame.setContentPane(getJTabbedPane());
			jFrame.setTitle("Tree Config 查看器");				
			jFrame.addWindowListener(new java.awt.event.WindowAdapter() {
				public void windowClosing(java.awt.event.WindowEvent e) {
					if(client != null){
						client.close();
					}
				}
			});
	}
		return jFrame;
	}

	/**
	 * This method initializes jTabbedPane	
	 * 	
	 * @return javax.swing.JTabbedPane	
	 */
	private JTabbedPane getJTabbedPane() {
		if (jTabbedPane == null) {
			jTabbedPane = new JTabbedPane();
			jTabbedPane.addTab("TreeConfig参数", null, getJSplitPane(), null);
			jTabbedPane.addTab("控制台输出", null, getJScrollPane2(), null);
		}
		return jTabbedPane;
	}

	/**
	 * This method initializes jSplitPane	
	 * 	
	 * @return javax.swing.JSplitPane	
	 */
	private JSplitPane getJSplitPane() {
		if (jSplitPane == null) {
			jSplitPane = new JSplitPane();
			jSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
			jSplitPane.setDividerSize(10);
			jSplitPane.setDividerLocation(100);
			jSplitPane.setBottomComponent(getJSplitPane1());
			jSplitPane.setTopComponent(getJDesktopPane());
		}
		return jSplitPane;
	}

	/**
	 * This method initializes jDesktopPane	
	 * 	
	 * @return javax.swing.JDesktopPane	
	 */
	private JDesktopPane getJDesktopPane() {
		if (jDesktopPane == null) {
			jLabel1 = new JLabel();
			jLabel1.setBounds(new Rectangle(180, 12, 49, 21));
			jLabel1.setText("Port");
			jLabel = new JLabel();
			jLabel.setBounds(new Rectangle(8, 9, 23, 22));
			jLabel.setText("IP");
			jDesktopPane = new JDesktopPane();
			jDesktopPane.add(jLabel, null);
			jDesktopPane.add(getIpTxt(), null);
			jDesktopPane.add(jLabel1, null);
			jDesktopPane.add(getPortTxt(), null);
			jDesktopPane.add(getConnectBtn(), null);
			jDesktopPane.add(getDisconnectBtn(), null);
		}
		return jDesktopPane;
	}

	/**
	 * This method initializes jSplitPane1	
	 * 	
	 * @return javax.swing.JSplitPane	
	 */
	private JSplitPane getJSplitPane1() {
		if (jSplitPane1 == null) {
			jSplitPane1 = new JSplitPane();
			jSplitPane1.setDividerLocation(250);
			jSplitPane1.setLeftComponent(getJScrollPane());
			jSplitPane1.setRightComponent(getJScrollPane1());
		}
		return jSplitPane1;
	}

	/**
	 * This method initializes jScrollPane	
	 * 	
	 * @return javax.swing.JScrollPane	
	 */
	private JScrollPane getJScrollPane() {
		if (jScrollPane == null) {
			jScrollPane = new JScrollPane();
			jScrollPane.setViewportView(getJTree());
		}
		return jScrollPane;
	}

	/**
	 * This method initializes jTree	
	 * 	
	 * @return javax.swing.JTree	
	 */
	private JTree getJTree() {
		if (jTree == null) {
			jTree = new JTree();
			jTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
			jTree.addTreeSelectionListener(new javax.swing.event.TreeSelectionListener() {
				public void valueChanged(javax.swing.event.TreeSelectionEvent e) {
					nodeSelected();
				}
			});
			jTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
		}
		return jTree;
	}

	/**
	 * This method initializes jScrollPane1	
	 * 	
	 * @return javax.swing.JScrollPane	
	 */
	private JScrollPane getJScrollPane1() {
		if (jScrollPane1 == null) {
			jScrollPane1 = new JScrollPane();
			jScrollPane1.setViewportView(getJTextPane());
		}
		return jScrollPane1;
	}

	/**
	 * This method initializes jTextPane	
	 * 	
	 * @return javax.swing.JTextPane	
	 */
	private JTextPane getJTextPane() {
		if (jTextPane == null) {
			jTextPane = new JTextPane();
		}
		return jTextPane;
	}
	private DefaultTreeConfigClient client = null;  //  @jve:decl-index=0:
	private MemoryConfigManager manager = new MemoryConfigManager();  //  @jve:decl-index=0:
	private JScrollPane jScrollPane2 = null;
	private JTextPane jConsolePane = null;
	private JLabel jLabel = null;
	private JTextField ipTxt = null;
	private JLabel jLabel1 = null;
	private JTextField portTxt = null;
	private JButton connectBtn = null;
	private JButton disconnectBtn = null;
	private void init(){
		ConsoleUtils.redirectConsole(getJConsolePane());
	}
	
	private void nodeSelected() {
		TreeConfigNode configNode = getSelectedConfigNode ();
		if(configNode == null){
			getJTextPane().setText("");
		} else {
			StringBuilder sb = new StringBuilder();
			if(configNode.getValue() != null){
				sb.append(ReflectToStringBuilder.reflectToString(configNode.getValue())).append("\n");
			}
			for (TreeConfigAttribute attribute : configNode.getAttributes()) {
				sb.append(attribute.getName()).append(" : ").append(ReflectToStringBuilder.reflectToString(attribute.getValue())).append("\n");
			}
			getJTextPane().setText(sb.toString());
		}
	}
	
	public TreeConfigNode getSelectedConfigNode (){
		TreePath path = jTree.getSelectionPath();
		if(path != null){
			DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode)path.getLastPathComponent();
			List<String> nodePath = new ArrayList<String>();
			if(!selectedNode.isRoot()){
				for (int i = 1; i < selectedNode.getPath().length; i++) {
					DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedNode.getPath()[i];
					nodePath.add(node.getUserObject() + "");
				}
				DefaultConfigPath p = new DefaultConfigPath();
				p.setPath(nodePath.toArray(new String[0]));
				return manager.getNode(p);
			}
		}
		return null;
	}

	/**
	 * This method initializes jScrollPane2	
	 * 	
	 * @return javax.swing.JScrollPane	
	 */
	private JScrollPane getJScrollPane2() {
		if (jScrollPane2 == null) {
			jScrollPane2 = new JScrollPane();
			jScrollPane2.setViewportView(getJConsolePane());
		}
		return jScrollPane2;
	}

	/**
	 * This method initializes jConsolePane	
	 * 	
	 * @return javax.swing.JTextPane	
	 */
	private JTextPane getJConsolePane() {
		if (jConsolePane == null) {
			jConsolePane = new JTextPane();
		}
		return jConsolePane;
	}

	/**
	 * This method initializes ipTxt	
	 * 	
	 * @return javax.swing.JTextField	
	 */
	private JTextField getIpTxt() {
		if (ipTxt == null) {
			ipTxt = new JTextField();
			ipTxt.setBounds(new Rectangle(35, 11, 135, 22));
			ipTxt.setText("127.0.0.1");
		}
		return ipTxt;
	}

	/**
	 * This method initializes portTxt	
	 * 	
	 * @return javax.swing.JTextField	
	 */
	private JTextField getPortTxt() {
		if (portTxt == null) {
			portTxt = new JTextField();
			portTxt.setBounds(new Rectangle(238, 11, 83, 21));
			portTxt.setText("9877");
		}
		return portTxt;
	}

	/**
	 * This method initializes connectBtn	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getConnectBtn() {
		if (connectBtn == null) {
			connectBtn = new JButton();
			connectBtn.setBounds(new Rectangle(341, 13, 83, 20));
			connectBtn.setText("连接");
			connectBtn.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					connectBtn.setEnabled(false);
					ipTxt.setEnabled(false);
					portTxt.setEnabled(false);
					try {
						final DefaultMutableTreeNode root = new DefaultMutableTreeNode("ROOT");
						DefaultTreeModel treeModel = new DefaultTreeModel(root);
						getJTree().setModel(treeModel);
						client = new DefaultTreeConfigClient(ipTxt.getText(), Integer.parseInt(portTxt.getText()));
						client.setLocalManager(manager);
						manager.setTreeConfigListener(new TreeConfigListener() {
							
							@Override
							public void nodeValueChanged(final TreeConfigNode node, final Serializable newValue, final Serializable oldValue) {
								SwingUtilities.invokeLater(new Runnable() {
									@Override
									public void run() {
										prepareNode(root, node);
										TreeConfigNode configNode = getSelectedConfigNode();
										if (configNode != null && TreeUtils.isPathEquals(node.getNodePath(), configNode.getNodePath())) {
											nodeSelected();
										}
									}
								});
							}

							private void prepareNode(final DefaultMutableTreeNode root, TreeConfigNode node) {
								boolean hasChanged = false;
								DefaultMutableTreeNode parent = root;
								for (int i = 0; i < node.getNodePath().getPath().length; i++) {
									String path = node.getNodePath().getPath()[i];
									boolean exist = false;
									for (int j = 0; j < parent.getChildCount(); j++) {
										DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(j);
										if (path.equals(child.getUserObject())) {
											exist = true;
											parent = child;
											break;
										}
									}
									if (!exist) {
										DefaultMutableTreeNode child = new DefaultMutableTreeNode(path);
										parent.add(child);
										parent = child;
										hasChanged = true;
									}
								}
								if (hasChanged) {
									getJTree().updateUI();
								}
							}
							
							@Override
							public void nodeRemoved(final TreeConfigNode removedNode) {
								SwingUtilities.invokeLater(new Runnable() {
									@Override
									public void run() {
										DefaultMutableTreeNode toRemove = root;
										for (int i = 0; i < removedNode.getNodePath().getPath().length; i++) {
											String path = removedNode.getNodePath().getPath()[i];
											boolean exist = false;
											for (int j = 0; j < toRemove.getChildCount(); j++) {
												DefaultMutableTreeNode child = (DefaultMutableTreeNode) toRemove.getChildAt(j);
												if (path.equals(child.getUserObject())) {
													exist = true;
													toRemove = child;
													break;
												}
											}
											if (!exist) {
												return;
											}
										}
										toRemove.removeFromParent();
										getJTree().updateUI();
									}
								});
							}
							
							@Override
							public void attributeValueChanged(final TreeConfigNode node,final TreeConfigAttribute attribute,final Serializable newValue,final Serializable oldValue) {
								SwingUtilities.invokeLater(new Runnable() {
									@Override
									public void run() {
										prepareNode(root, node);
										TreeConfigNode configNode = getSelectedConfigNode();
										if (configNode != null && TreeUtils.isPathEquals(node.getNodePath(), configNode.getNodePath())) {
											nodeSelected();
										}
									}
								});
							}
							
							@Override
							public void attributeRemoved(final TreeConfigNode node,final TreeConfigAttribute removedAttribute) {
								SwingUtilities.invokeLater(new Runnable() {
									@Override
									public void run() {
										TreeConfigNode configNode = getSelectedConfigNode();
										if (configNode != null && TreeUtils.isPathEquals(node.getNodePath(), configNode.getNodePath())) {
											nodeSelected();
										}
									}
								});
							}
							
						});
						// 获取全部节点
						client.bindInitialGetNodes(null, true);
						client.connect();
						disconnectBtn.setEnabled(true);
					} catch (Exception e1) {
						e1.printStackTrace();
						connectBtn.setEnabled(true);
						ipTxt.setEnabled(true);
						portTxt.setEnabled(true);
					}
				}
			});
		}
		return connectBtn;
	}

	/**
	 * This method initializes disconnectBtn	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getDisconnectBtn() {
		if (disconnectBtn == null) {
			disconnectBtn = new JButton();
			disconnectBtn.setBounds(new Rectangle(447, 13, 86, 22));
			disconnectBtn.setEnabled(false);
			disconnectBtn.setText("断开");
			disconnectBtn.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					disconnectBtn.setEnabled(false);
					// close是有等待，所以另起线程，放置阻塞ui
					new Thread(){
						@Override
						public void run() {
							client.close();
							connectBtn.setEnabled(true);
							ipTxt.setEnabled(true);
							portTxt.setEnabled(true);
						}
					}.start();
				}
			});
		}
		return disconnectBtn;
	}

	/**
	 * Launches this application
	 */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				SimpleTreeConfigViewer application = new SimpleTreeConfigViewer();
				application.init();
				application.getJFrame().setLocationRelativeTo(null);
				application.getJFrame().setVisible(true);
			}
		});
	}

}

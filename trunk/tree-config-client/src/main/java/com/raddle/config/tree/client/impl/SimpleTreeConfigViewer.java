/**
 * 
 */
package com.raddle.config.tree.client.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.apache.commons.lang.ObjectUtils;

import com.raddle.config.tree.DefaultConfigPath;
import com.raddle.config.tree.api.TreeConfigAttribute;
import com.raddle.config.tree.api.TreeConfigListener;
import com.raddle.config.tree.api.TreeConfigNode;
import com.raddle.config.tree.local.MemoryConfigManager;
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
					client.close();
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
			jDesktopPane = new JDesktopPane();
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
			jTree.addTreeSelectionListener(new javax.swing.event.TreeSelectionListener() {
				public void valueChanged(javax.swing.event.TreeSelectionEvent e) {
					nodeSelected();
				}
			});
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
	private MemoryConfigManager manager = new MemoryConfigManager();
	private void init(){
		final DefaultMutableTreeNode root = new DefaultMutableTreeNode("ROOT");
		DefaultTreeModel treeModel = new DefaultTreeModel(root);
		getJTree().setModel(treeModel);
		client = new DefaultTreeConfigClient("127.0.0.1", 9877);
		client.setLocalManager(manager);
		manager.setTreeConfigListener(new TreeConfigListener() {
			
			@Override
			public void nodeValueChanged(TreeConfigNode node, Serializable newValue, Serializable oldValue) {
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
					}
				}
				if(!ObjectUtils.equals(newValue, oldValue)){
					TreeConfigNode configNode = getSelectedConfigNode();
					if(configNode != null && TreeUtils.isPathEquals(node.getNodePath(), configNode.getNodePath())){
						nodeSelected();
					}
				}
			}
			
			@Override
			public void nodeRemoved(TreeConfigNode removedNode) {
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
			}
			
			@Override
			public void attributeValueChanged(TreeConfigNode node, TreeConfigAttribute attribute, Serializable newValue, Serializable oldValue) {
				if(!ObjectUtils.equals(newValue, oldValue)){
					TreeConfigNode configNode = getSelectedConfigNode();
					if(configNode != null && TreeUtils.isPathEquals(node.getNodePath(), configNode.getNodePath())){
						nodeSelected();
					}
				}
			}
			
			@Override
			public void attributeRemoved(TreeConfigNode node, TreeConfigAttribute removedAttribute) {
				
			}
			
		});
		// 获取全部节点
		client.bindInitialGetNodes(null, true);
		client.connect();
	}
	
	private void nodeSelected() {
		TreeConfigNode configNode = getSelectedConfigNode ();
		if(configNode == null){
			getJTextPane().setText("");
		} else {
			StringBuilder sb = new StringBuilder();
			for (TreeConfigAttribute attribute : configNode.getAttributes()) {
				sb.append(attribute.getName()).append(" : ").append(attribute.getValue()).append("\n");
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
	 * Launches this application
	 */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				SimpleTreeConfigViewer application = new SimpleTreeConfigViewer();
				application.init();
				application.getJFrame().setVisible(true);
			}
		});
	}

}

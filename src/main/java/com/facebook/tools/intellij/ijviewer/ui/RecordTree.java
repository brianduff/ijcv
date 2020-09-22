package com.facebook.tools.intellij.ijviewer.ui;

import java.awt.Component;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeSelectionModel;

import com.facebook.tools.intellij.ijviewer.IjViewer;

public class RecordTree extends JTree {
  private final IconLoader iconLoader = new IconLoader();

  private Consumer<Integer> selectionChangeListener = i -> {
  };

  RecordTree(IjViewer viewer) {
    setBorder(BorderFactory.createEmptyBorder());
    setModel(new RecordTreeModel(viewer));
    setRootVisible(false);
    setShowsRootHandles(true);
    javax.swing.ToolTipManager.sharedInstance().registerComponent(this);

    getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    addTreeSelectionListener(e -> {
      if (e.getNewLeadSelectionPath() == null || e.getNewLeadSelectionPath().getLastPathComponent() == null) {
        selectionChangeListener.accept(-1);
        return;
      }
      RecordTreeNode node = (RecordTreeNode) e.getNewLeadSelectionPath().getLastPathComponent();
      selectionChangeListener.accept(node.fileId);
    });

    setCellRenderer(new DefaultTreeCellRenderer() {
      @Override
      public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
          boolean leaf, int row, boolean hasFocus) {
        Component c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        if (leaf) {
          RecordTreeNode node = (RecordTreeNode) value;
          String iconName;

          // Some leaf nodes are actually logically directories with no children.
          if (viewer.records.isDirectory(node.fileId)) {
            iconName = "folder";
          } else {
            iconName = node.hasCachedContent() ? "file_cached" : "file";
          }
          iconLoader.getIcon(iconName).ifPresent(icon -> setIcon(icon));

          if (node.hasCachedContent()) {
            setToolTipText("The content of this file is cached");
          }
        } else {
          if (expanded) {
            iconLoader.getIcon("folder_open").ifPresent(icon -> setIcon(icon));
          } else {
            iconLoader.getIcon("folder").ifPresent(icon -> setIcon(icon));
          }
        }

        return c;
      }
    });
  }

  public RecordTreeModel getModel() {
    return (RecordTreeModel) super.getModel();
  }

  void setSelectionChangeListener(Consumer<Integer> listener) {
    this.selectionChangeListener = listener;
  }
}

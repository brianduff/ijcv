package com.facebook.tools.intellij.ijviewer.ui;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import com.facebook.tools.intellij.ijviewer.IjViewer;

final class RecordTreeModel implements TreeModel {
  private final IjViewer viewer;
  final List<TreeModelListener> treeModelListeners = new ArrayList<TreeModelListener>(1);

  RecordTreeModel(IjViewer viewer) {
    this.viewer = viewer;
  }

  @Override
  public Object getRoot() {
    return RecordTreeNode.findOrCreateNode(viewer, null, 2); /// TODO: this isn't necessarily the root. Fix this
                                                             /// hack.
  }

  @Override
  public Object getChild(Object parent, int index) {
    RecordTreeNode[] children = ((RecordTreeNode) parent).getChildren();
    return children[index];
  }

  @Override
  public int getChildCount(Object parent) {
    RecordTreeNode[] children = ((RecordTreeNode) parent).getChildren();
    return children.length;
  }

  @Override
  public boolean isLeaf(Object node) {
    return getChildCount(node) == 0;
  }

  @Override
  public void valueForPathChanged(TreePath path, Object newValue) {
    // TODO Auto-generated method stub

  }

  @Override
  public int getIndexOfChild(Object parent, Object child) {
    RecordTreeNode[] children = ((RecordTreeNode) parent).getChildren();
    for (int i = 0; i < children.length; i++) {
      if (children[i].equals(child))
        return i;
    }
    return -1;
  }

  @Override
  public void addTreeModelListener(TreeModelListener l) {
    treeModelListeners.add(l);
  }

  @Override
  public void removeTreeModelListener(TreeModelListener l) {
    treeModelListeners.remove(l);
  }

}

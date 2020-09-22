package com.facebook.tools.intellij.ijviewer.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.facebook.tools.intellij.ijviewer.IjViewer;

class RecordTreeNode {
  private static Map<Integer, RecordTreeNode> nodeMap = new HashMap<>();

  final int fileId;

  private final IjViewer viewer;
  final RecordTreeNode parent;
  private RecordTreeNode[] children;
  private String name;
  private Optional<Boolean> hasCachedContent = Optional.empty();

  // The size of this node on disk.
  volatile long sizeOnDisk = -2;

  private RecordTreeNode(IjViewer viewer, RecordTreeNode parent, int fileId) {
    this.viewer = viewer;
    this.parent = parent;
    this.fileId = fileId;
  }

  public static RecordTreeNode findOrCreateNode(IjViewer viewer, RecordTreeNode parent, int id) {
    RecordTreeNode node = nodeMap.get(id);
    if (node == null) {
      node = new RecordTreeNode(viewer, parent, id);
      nodeMap.put(id, node);
    }
    return node;
  }

  private RecordTreeNode findOrCreateNode(RecordTreeNode parent, int id) {
    return findOrCreateNode(viewer, parent, id);
  }

  public String getPath() {
    if (parent == null)
      return "/";
    return parent.getPath() + "/" + getName();
  }

  boolean hasCachedContent() {
    return hasCachedContent.orElseGet(() -> {
      boolean hasContent = viewer.records.getContentId(fileId) != 0;
      hasCachedContent = Optional.of(hasContent);
      return hasContent;
    });
  }

  long getSizeOnDisk() {
    if (sizeOnDisk < 0) {
      if (!viewer.records.isDirectory(fileId)) {
        if (hasCachedContent()) {
          sizeOnDisk = viewer.content.getContentLength(viewer.records.getContentId(fileId));
        } else {
          sizeOnDisk = 0;
        }
      }
    }
    return sizeOnDisk;
  }

  Object[] getTreePath() {
    List<Object> parents = new ArrayList<>();
    parents.add(this);
    RecordTreeNode parent = this.parent;
    while (parent != null) {
      parents.add(parent);
      parent = parent.parent;
    }

    Collections.reverse(parents);

    return parents.toArray(new Object[0]);
  }

  int getIndexOf(RecordTreeNode child) {
    for (int i = 0; i < getChildren().length; i++) {
      if (getChildren()[i].equals(child))
        return i;
    }
    return -1;
  }

  void setSizeOnDisk(long sizeOnDisk) {
    this.sizeOnDisk = sizeOnDisk;
  }

  private boolean isAncestor(int fileId) {
    RecordTreeNode parent = this.parent;
    while (parent != null) {
      if (parent.fileId == fileId)
        return true;
      parent = parent.parent;
    }
    return false;
  }

  synchronized RecordTreeNode[] getChildren() {
    if (children != null)
      return children;

    if (!viewer.records.isDirectory(fileId)) {
      children = new RecordTreeNode[0];
      return children;
    }

    try {
      int[] childrenIds = viewer.attribs.getChildren(fileId);

      List<RecordTreeNode> children = new ArrayList<>();
      for (int i = 0; i < childrenIds.length; i++) {
        RecordTreeNode childNode = findOrCreateNode(this, childrenIds[i]);
        if (isAncestor(childrenIds[i])) {
          // System.out.println("Warning: loop detected: " + childNode.getPath());
        } else {
          children.add(childNode);
        }
      }

      // Sort the children by whether they're a directory, then by name.
      Comparator<RecordTreeNode> sortByIsDirectory = Comparator
          .comparing(node -> viewer.records.isDirectory(node.fileId));
      Comparator<RecordTreeNode> sortByName = Comparator.comparing(node -> {
        try {
          return viewer.getName(node.fileId);
        } catch (IOException e) {
          return "";
        }
      });
      children.sort(sortByIsDirectory.reversed().thenComparing(sortByName));

      this.children = children.toArray(new RecordTreeNode[0]);

      return this.children;

    } catch (Throwable e) {
      // System.err.println("Error getting children for " + fileId);
      // e.printStackTrace();
      children = new RecordTreeNode[0];
    }
    return children;
  }

  private String getName() {
    if (name == null) {
      try {
        name = viewer.getName(fileId);
      } catch (Throwable e) {
        System.err.println("Error getting name for " + fileId);
        // e.printStackTrace();
        name = "" + fileId;
      }
    }

    return name;
  }

  public String toString() {
    long sizeOnDisk = getSizeOnDisk();
    String suffix = "";
    if (sizeOnDisk == -2) {
      suffix = "";
    } else if (sizeOnDisk == -1) {
      suffix = " [calculating size]";
    } else {
      suffix = " [" + sizeOnDisk + "]";
    }

    return getName() + suffix;
  }
}
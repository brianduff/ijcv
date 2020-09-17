package com.facebook.tools.intellij.ijviewer;

import java.awt.Component;
import java.awt.Font;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import com.intellij.openapi.util.io.FileUtilRt;

public class UI {
  final IjViewer viewer;
  private List<TreeModelListener> treeModelListeners = new ArrayList<TreeModelListener>(1);


  UI(IjViewer viewer) {
    this.viewer = viewer;
  }

  public void show() {
    JTree tree = new JTree();
    JFrame f = new JFrame("IntelliJ Viewer");

    JTable table = new JTable();

    JSplitPane mainPanel = new JSplitPane();
    JSplitPane detail = new JSplitPane();
    detail.setOrientation(JSplitPane.VERTICAL_SPLIT);

    JTextArea textArea = new JTextArea();
    textArea.setFont(new Font("monospaced", Font.PLAIN, 12));
    textArea.setEditable(false);

    detail.setTopComponent(new JScrollPane(textArea));
    detail.setBottomComponent(new JScrollPane(table));

    mainPanel.setLeftComponent(new JScrollPane(tree));
    mainPanel.setRightComponent(detail);

    tree.setModel(new TreeModel() {
      @Override
      public Object getRoot() {
        return findOrCreateNode(null, 2); /// TODO: this isn't necessarily the root. Fix this hack.
      }

      @Override
      public Object getChild(Object parent, int index) {
        TreeNode[] children = ((TreeNode) parent).getChildren();
        return children[index];
      }

      @Override
      public int getChildCount(Object parent) {
        TreeNode[] children = ((TreeNode) parent).getChildren();
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
        TreeNode[] children = ((TreeNode) parent).getChildren();
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

    });

    TableModel tableModel = new TableModel();
    table.setModel(tableModel);

    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    javax.swing.ToolTipManager.sharedInstance().registerComponent(tree);
    tree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        if (e.getNewLeadSelectionPath() == null || e.getNewLeadSelectionPath().getLastPathComponent() == null) {
          tableModel.setCurrentSelectionId(-1);
          return;
        }
        TreeNode node = (TreeNode) e.getNewLeadSelectionPath().getLastPathComponent();
        tableModel.setCurrentSelectionId(node.fileId);

        textArea.setText("No content to display");

        try {
          DataInputStream content = viewer.readContent(node.fileId);
          if (content != null) {
            byte[] bytes = FileUtilRt.loadBytes(content);
            if (bytes != null) {
              String coerced = new String(bytes, StandardCharsets.UTF_8);
              textArea.setText(coerced);
              return;
            }
          }
          textArea.setText("Content is not cached");
        } catch (Throwable t) {
          t.printStackTrace();
          textArea.setText("Error loading content");
        }
      }
    });
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);

    IconLoader iconLoader = new IconLoader();

    tree.setCellRenderer(new DefaultTreeCellRenderer() {
      @Override
      public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
          boolean leaf, int row, boolean hasFocus) {
        Component c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        if (leaf) {
          TreeNode node = (TreeNode) value;
          String iconName = node.hasCachedContent() ? "file_cached" : "file";
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

    f.setContentPane(mainPanel);
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    f.pack();
    f.setVisible(true);

    computeDiskSizes((TreeNode) tree.getModel().getRoot());
  }

  private void computeDiskSizes(TreeNode root) {
    new ForkJoinPool(100).execute(new DiskSizeTask(root));
  }

  class DiskSizeTask extends RecursiveTask<Long> {
    private final TreeNode node;

    DiskSizeTask(TreeNode node) {
      this.node = node;
    }

    @Override
    protected Long compute() {
      // System.out.printf("[%s] Finding size of %s\n",
      // Thread.currentThread().getId(), node.getPath());
      // Add up the size of all files, and create subtasks for folders.

      List<DiskSizeTask> subfolderTasks = new ArrayList<>();
      long fileSize = 0;
      for (TreeNode child : node.getChildren()) {
        if (child.getSizeOnDisk() >= 0) {
          fileSize += child.getSizeOnDisk();
        } else {
          child.setSizeOnDisk(-1); // in progress
          subfolderTasks.add(new DiskSizeTask(child));
        }
      }

      long totalSize = fileSize + ForkJoinTask.invokeAll(subfolderTasks).stream().mapToLong(ForkJoinTask::join).sum();

      node.setSizeOnDisk(totalSize);

      // java.awt.EventQueue.invokeLater(() -> {
      //   TreeModelEvent event = new TreeModelEvent(this, node.getTreePath());
      //   treeModelListeners.forEach(listener -> listener.treeStructureChanged(event));  
      // });

      return totalSize;
    }

  }

  class TableModel extends AbstractTableModel {
    private int currentSelectionId = -1;

    public void setCurrentSelectionId(int currentSelectionId) {
      this.currentSelectionId = currentSelectionId;
      fireTableDataChanged();
    }

    public int getRowCount() {
      return Property.values().length;
    }

    public int getColumnCount() {
      return 2;
    }

    public Object getValueAt(int row, int column) {
      if (column == 0)
        return Property.values()[row].toString();
      if (currentSelectionId == -1)
        return "";
      return Property.values()[row].get(viewer, currentSelectionId);
    }

    public String getColumnName(int column) {
      return column == 0 ? "Name" : "Value";
    }
  }

  enum Property {
    fileId {
      public Object get(IjViewer viewer, int fileId) {
        return fileId;
      }
    },
    parentId {
      public Object get(IjViewer viewer, int fileId) {
        return viewer.records.getParentId(fileId);
      }
    },
    contentId {
      public Object get(IjViewer viewer, int fileId) {
        return viewer.records.getContentId(fileId);
      }
    },
    contentReferenceCount {
      public Object get(IjViewer viewer, int fileId) {
        int contentId = viewer.records.getContentId(fileId);
        if (contentId == 0)
          return 0;
        return viewer.content.getRefCount(contentId);
      }
    },
    contentSizeOnDisk {
      public Object get(IjViewer viewer, int fileId) {
        int contentId = viewer.records.getContentId(fileId);
        if (contentId == 0)
          return 0;
        return viewer.content.getContentLength(contentId);
      }
    },
    childCount {
      public Object get(IjViewer viewer, int fileId) {
        try {
          return viewer.attribs.getChildCount(fileId);
        } catch (Throwable t) {
          return 0;
        }
      }
    },
    timestamp {
      public Object get(IjViewer viewer, int fileId) {
        long ts = viewer.records.getTimestamp(fileId);
        return new Date(ts) + " (" + ts + ")";
      }
    },
    modCount {
      public Object get(IjViewer viewer, int fileId) {
        return NumberFormat.getNumberInstance(Locale.US).format(viewer.records.getModCount(fileId));
      }
    },
    length {
      public Object get(IjViewer viewer, int fileId) {
        return NumberFormat.getNumberInstance(Locale.US).format(viewer.records.getLength(fileId));
      }
    },
    childrenCached {
      public Object get(IjViewer viewer, int fileId) {
        return viewer.records.isFlagSet(fileId, IjViewer.Records.CHILDREN_CACHED_FLAG);
      }
    },
    isDirectory {
      public Object get(IjViewer viewer, int fileId) {
        return viewer.records.isFlagSet(fileId, IjViewer.Records.IS_DIRECTORY_FLAG);
      }
    },
    isReadOnly {
      public Object get(IjViewer viewer, int fileId) {
        return viewer.records.isFlagSet(fileId, IjViewer.Records.IS_READ_ONLY);
      }
    },
    mustReloadContent {
      public Object get(IjViewer viewer, int fileId) {
        return viewer.records.isFlagSet(fileId, IjViewer.Records.MUST_RELOAD_CONTENT);
      }
    },
    isSymlink {
      public Object get(IjViewer viewer, int fileId) {
        return viewer.records.isFlagSet(fileId, IjViewer.Records.IS_SYMLINK);
      }
    },
    isSpecial {
      public Object get(IjViewer viewer, int fileId) {
        return viewer.records.isFlagSet(fileId, IjViewer.Records.IS_SPECIAL);
      }
    },
    isHidden {
      public Object get(IjViewer viewer, int fileId) {
        return viewer.records.isFlagSet(fileId, IjViewer.Records.IS_HIDDEN);
      }
    },
    isFreeRecord {
      public Object get(IjViewer viewer, int fileId) {
        return viewer.records.isFlagSet(fileId, IjViewer.Records.FREE_RECORD_FLAG);
      }
    },

    ;

    public abstract Object get(IjViewer viewer, int fileId);

  }

  private final Map<Integer, TreeNode> nodeMap = new HashMap<>(10000);

  private TreeNode findOrCreateNode(TreeNode parent, int id) {
    TreeNode node = nodeMap.get(id);
    if (node == null) {
      node = new TreeNode(parent, id);
      nodeMap.put(id, node);
    }
    return node;
  }

  class TreeNode {
    TreeNode parent;
    int fileId;
    TreeNode[] children;
    String name;
    Optional<Boolean> hasCachedContent = Optional.empty();

    // The size of this node on disk.
    volatile long sizeOnDisk = -2;

    TreeNode(TreeNode parent, int fileId) {
      this.parent = parent;
      this.fileId = fileId;
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
      TreeNode parent = this.parent;
      while (parent != null) {
        parents.add(parent);
        parent = parent.parent;
      }

      Collections.reverse(parents);

      return parents.toArray(new Object[0]);
    }

    int getIndexOf(TreeNode child) {
      for (int i = 0; i < getChildren().length; i++) {
        if (getChildren()[i].equals(child)) return i;
      }
      return -1;
    }

    void setSizeOnDisk(long sizeOnDisk) {
      this.sizeOnDisk = sizeOnDisk;
    }

    private boolean isAncestor(int fileId) {
      TreeNode parent = this.parent;
      while (parent != null) {
        if (parent.fileId == fileId)
          return true;
        parent = parent.parent;
      }
      return false;
    }

    synchronized TreeNode[] getChildren() {
      if (children != null)
        return children;

      if (!viewer.records.isDirectory(fileId)) {
        children = new TreeNode[0];
        return children;
      }

      try {
        int[] childrenIds = viewer.attribs.getChildren(fileId);

        List<TreeNode> children = new ArrayList<>();
        for (int i = 0; i < childrenIds.length; i++) {
          TreeNode childNode = findOrCreateNode(this, childrenIds[i]);
          if (isAncestor(childrenIds[i])) {
            // System.out.println("Warning: loop detected: " + childNode.getPath());
          } else {
            children.add(childNode);
          }
        }

        // Sort the children by whether they're a directory, then by name.
        Comparator<TreeNode> sortByIsDirectory = Comparator.comparing(node -> viewer.records.isDirectory(node.fileId));
        Comparator<TreeNode> sortByName = Comparator.comparing(node -> {
          try {
            return viewer.getName(node.fileId);
          } catch (IOException e) {
            return "";
          }
        });
        children.sort(sortByIsDirectory.reversed().thenComparing(sortByName));

        this.children = children.toArray(new TreeNode[0]);

        return this.children;

      } catch (Throwable e) {
        // System.err.println("Error getting children for " + fileId);
        // e.printStackTrace();
        children = new TreeNode[0];
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
      System.out.println("Printing " + this.hashCode() + ":" + getPath() + ": " + getName() + suffix);

      return getName() + suffix;
    }
  }
}

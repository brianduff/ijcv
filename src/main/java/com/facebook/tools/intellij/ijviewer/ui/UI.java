package com.facebook.tools.intellij.ijviewer.ui;

import java.awt.Component;
import java.awt.Font;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.event.TreeModelEvent;

import com.facebook.tools.intellij.ijviewer.IjViewer;
import com.intellij.openapi.util.io.FileUtilRt;

public class UI {
  private final IjViewer viewer;

  public UI(IjViewer viewer) {
    this.viewer = viewer;
  }

  public void show() {
    JTextArea contentText = new JTextArea();
    contentText.setFont(new Font("monospaced", Font.PLAIN, 12));
    contentText.setEditable(false);

    JTable propertiesTable = new JTable();
    RecordProperties propertiesModel = new RecordProperties(viewer);
    propertiesTable.setModel(propertiesModel);

    RecordTree recordsTree = new RecordTree(viewer);
    recordsTree.setSelectionChangeListener(fileId -> {
      propertiesModel.setCurrentSelectionId(fileId);
      contentText.setText("No content to display");
      if (fileId == -1)
        return;

      try {
        DataInputStream content = viewer.readContent(fileId);
        if (content != null) {
          byte[] bytes = FileUtilRt.loadBytes(content);
          if (bytes != null) {
            String coerced = new String(bytes, StandardCharsets.UTF_8);
            contentText.setText(coerced);
            return;
          }
        }
        contentText.setText("Content is not cached");
      } catch (Throwable t) {
        t.printStackTrace();
        contentText.setText("Error loading content");
      }
    });

    JFrame f = new JFrame("IntelliJ Viewer");

    JTabbedPane tabbedPane = new JTabbedPane();

    JSplitPane mainPanel = new JSplitPane();
    mainPanel.setBorder(BorderFactory.createEmptyBorder());
    JSplitPane detail = new JSplitPane();
    detail.setBorder(BorderFactory.createEmptyBorder());
    detail.setOrientation(JSplitPane.VERTICAL_SPLIT);

    detail.setTopComponent(createScrollPane(contentText));
    detail.setBottomComponent(createScrollPane(propertiesTable));

    mainPanel.setLeftComponent(createScrollPane(recordsTree));
    mainPanel.setRightComponent(detail);

    tabbedPane.addTab("Cache", mainPanel);
    tabbedPane.addTab("Large Content", new JPanel());
    f.setContentPane(tabbedPane);
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    f.pack();
    f.setVisible(true);

    new DiskSizeComputer((e, node) -> {
      TreeModelEvent event = new TreeModelEvent(this, node.getTreePath());
      recordsTree.getModel().treeModelListeners.forEach(listener -> listener.treeStructureChanged(event));
    }).computeDiskSizes((RecordTreeNode) recordsTree.getModel().getRoot());
  }

  private static JScrollPane createScrollPane(Component child) {
    JScrollPane p = new JScrollPane(child);
    p.setBorder(BorderFactory.createEmptyBorder());
    return p;
  }
}

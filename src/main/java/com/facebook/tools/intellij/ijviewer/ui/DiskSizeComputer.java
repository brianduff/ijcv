package com.facebook.tools.intellij.ijviewer.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

final class DiskSizeComputer {

  private final Listener listener;

  DiskSizeComputer(Listener listener) {
    this.listener = listener;
  }

  void computeDiskSizes(RecordTreeNode root) {
    new ForkJoinPool(100).execute(new DiskSizeTask(root));
  }

  private class DiskSizeTask extends RecursiveTask<Long> {
    private final RecordTreeNode node;

    DiskSizeTask(RecordTreeNode node) {
      this.node = node;
    }

    @Override
    protected Long compute() {
      // Add up the size of all files, and create subtasks for folders.

      List<DiskSizeTask> subfolderTasks = new ArrayList<>();
      long fileSize = 0;
      for (RecordTreeNode child : node.getChildren()) {
        if (child.getSizeOnDisk() >= 0) {
          fileSize += child.getSizeOnDisk();
        } else {
          child.setSizeOnDisk(-1); // in progress
          notifyEvent(Event.STARTED_COMPUTING, node);
          subfolderTasks.add(new DiskSizeTask(child));
        }
      }

      long totalSize = fileSize + ForkJoinTask.invokeAll(subfolderTasks).stream().mapToLong(ForkJoinTask::join).sum();

      node.setSizeOnDisk(totalSize);

      notifyEvent(Event.DONE_COMPUTING, node);
      return totalSize;
    }
  }

  private void notifyEvent(Event event, RecordTreeNode node) {
    // Limit events for performance reasons to children of the root.
    if (node.parent != null && node.parent.parent == null) {
      java.awt.EventQueue.invokeLater(() -> {
        listener.onEvent(Event.DONE_COMPUTING, node);
      });
    }
  }

  enum Event {
    STARTED_COMPUTING, DONE_COMPUTING
  }

  interface Listener {
    public void onEvent(Event event, RecordTreeNode node);
  }
}

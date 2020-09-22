package com.facebook.tools.intellij.ijviewer.ui;

import java.text.NumberFormat;
import java.util.Date;
import java.util.Locale;

import javax.swing.table.AbstractTableModel;

import com.facebook.tools.intellij.ijviewer.IjViewer;

class RecordProperties extends AbstractTableModel {
  private final IjViewer viewer;
  private int currentSelectionId = -1;

  RecordProperties(IjViewer viewer) {
    this.viewer = viewer;
  }

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
  

  private enum Property {
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
}

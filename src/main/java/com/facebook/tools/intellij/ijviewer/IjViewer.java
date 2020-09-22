package com.facebook.tools.intellij.ijviewer;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import com.facebook.tools.intellij.ijviewer.ui.UI;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.PagePool;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.io.ResizeableMappedFile;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import com.intellij.util.io.storage.AbstractRecordsTable;
import com.intellij.util.io.storage.CapacityAllocationPolicy;
import com.intellij.util.io.storage.RefCountingStorage;
import com.intellij.util.io.storage.Storage;

public class IjViewer {

  public final Records records;
  public final PersistentStringEnumerator names;
  public final Attribs attribs;
  public final AttribEnum attribEnum;
  public final CustomRefCountingStorage content;

  public IjViewer(ResizeableMappedFile records, PersistentStringEnumerator names, Storage attribs,
      AttribEnum attribEnum, CustomRefCountingStorage content) {
    this.records = new Records(records);
    this.names = names;
    this.attribs = new Attribs(attribs);
    this.attribEnum = attribEnum;
    this.content = content;
  }

  public String getName(int recordId) throws IOException {
    int nameId = records.getRecordInt(recordId, Records.NAME_OFFSET);
    return names.valueOf(nameId);
  }

  public static IjViewer forCacheDir(File cacheDir) throws IOException {
    // Create a copy of the cache dir, otherwise sometimes reading the files will
    // corrupt them.
    Path tmpDir = Files.createTempDirectory("ijviewer");
    tmpDir.toFile().deleteOnExit();

    System.out.println("Copying files to backup");
    for (File child : cacheDir.listFiles()) {
      Files.copy(child.toPath(), tmpDir.resolve(child.getName()));
    }

    cacheDir = tmpDir.toFile();

    PagedFileStorage.StorageLockContext storageLockContext = new PagedFileStorage.StorageLockContext(false);

    File recordsFile = new File(cacheDir, "records.dat");
    boolean aligned = PagedFileStorage.BUFFER_SIZE % Records.RECORD_SIZE == 0;
    ResizeableMappedFile records = new ResizeableMappedFile(recordsFile.toPath(), 20 * 1024, storageLockContext,
        PagedFileStorage.BUFFER_SIZE, aligned, IOUtil.BYTE_BUFFERS_USE_NATIVE_BYTE_ORDER);

    File namesFile = new File(cacheDir, "names.dat");
    PersistentStringEnumerator names = new PersistentStringEnumerator(namesFile.toPath(), storageLockContext);

    File attribsFile = new File(cacheDir, "attrib.dat");
    Storage attribStorage = new Storage(attribsFile.getPath(), REASONABLY_SMALL) {
      @Override
      protected AbstractRecordsTable createRecordsTable(PagePool pool, File recordsFile) throws IOException {
        return new CompactRecordsTable(recordsFile, pool, false);
      }
    };

    File contentsFile = new File(cacheDir, "content.dat");
    CustomRefCountingStorage contents = new CustomRefCountingStorage(contentsFile);

    AttribEnum attribEnum = AttribEnum.read(cacheDir);

    return new IjViewer(records, names, attribStorage, attribEnum, contents);
  }

  public static class CustomRefCountingStorage extends RefCountingStorage {
    CustomRefCountingStorage(File contentsFile) throws IOException {
      super(contentsFile.getPath(), CapacityAllocationPolicy.FIVE_PERCENT_FOR_GROWTH, false);
    }

    public int getContentLength(int contentId) {
      return myRecordsTable.getSize(contentId);
    }

    @Override
    protected ExecutorService createExecutor() {
      return SequentialTaskExecutor.createSequentialApplicationPoolExecutor("FSRecords Pool");
    }
  }

  public DataInputStream readContent(int fileId) throws IOException {
    int contentId = records.getContentId(fileId);
    if (contentId == 0)
      return null;

    return content.readStream(contentId);
  }

  private static final AttrPageAwareCapacityAllocationPolicy REASONABLY_SMALL = new AttrPageAwareCapacityAllocationPolicy();

  private static class AttrPageAwareCapacityAllocationPolicy extends CapacityAllocationPolicy {
    boolean myAttrPageRequested;

    @Override
    public int calculateCapacity(int requiredLength) { // 20% for growth
      return Math.max(myAttrPageRequested ? 8 : 32,
          Math.min((int) (requiredLength * 1.2), (requiredLength / 1024 + 1) * 1024));
    }
  }

  public static void main(String[] args) throws IOException {
    // File cacheDir = new File("/Users/" + System.getProperty("user.name") + "/Library/Caches/AndroidStudio4.0/caches/");
    File cacheDir = new
    File("/Users/bduff/Library/Caches/JetBrains/IdeaIC2020.1/caches/");

    IjViewer viewer = IjViewer.forCacheDir(cacheDir);
    new UI(viewer).show();
  }

  public class Attribs {
    private static final int MAX_SMALL_ATTR_SIZE = 64;

    private final Storage storage;

    Attribs(Storage storage) {
      this.storage = storage;
    }

    public DataInputStream readAttribute(int recordId, String attributeName) throws IOException {
      int attributeRecordId = records.getAttributeRecordId(recordId);
      int attrId = attribEnum.attribIdsByName.get(attributeName);

      int page = 0;
      try (DataInputStream attrRefs = storage.readStream(attributeRecordId)) {
        while (attrRefs.available() > 0) {
          int attIdOnPage = DataInputOutputUtil.readINT(attrRefs);
          int attrAddressOrSize = DataInputOutputUtil.readINT(attrRefs);
          if (attIdOnPage != attrId) {
            attrRefs.skipBytes(attrAddressOrSize);
          } else {
            if (attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
              byte[] b = new byte[attrAddressOrSize];
              attrRefs.readFully(b);
              return new DataInputStream(new UnsyncByteArrayInputStream(b));
            }
            page = attrAddressOrSize - MAX_SMALL_ATTR_SIZE;
            break;
          }
        }
      }

      if (page == 0) {
        return null;
      }

      return storage.readStream(page);
    }

    public int[] getChildren(int recordId) throws IOException {
      ChildrenAttribute ca = new ChildrenAttribute(recordId);
      return ca.read(readAttribute(recordId, ChildrenAttribute.NAME));
    }

    public int getChildCount(int recordId) throws IOException {
      ChildrenAttribute ca = new ChildrenAttribute(recordId);
      return ca.getCount(readAttribute(recordId, ChildrenAttribute.NAME));
    }
  }

  public static class Records {
    private final ResizeableMappedFile records;

    Records(ResizeableMappedFile records) {
      this.records = records;
    }

    private static final int PARENT_OFFSET = 0;
    private static final int PARENT_SIZE = 4;
    private static final int NAME_OFFSET = PARENT_OFFSET + PARENT_SIZE;
    private static final int NAME_SIZE = 4;
    private static final int FLAGS_OFFSET = NAME_OFFSET + NAME_SIZE;
    private static final int FLAGS_SIZE = 4;
    private static final int ATTR_REF_OFFSET = FLAGS_OFFSET + FLAGS_SIZE;
    private static final int ATTR_REF_SIZE = 4;
    private static final int CONTENT_OFFSET = ATTR_REF_OFFSET + ATTR_REF_SIZE;
    private static final int CONTENT_SIZE = 4;
    private static final int TIMESTAMP_OFFSET = CONTENT_OFFSET + CONTENT_SIZE;
    private static final int TIMESTAMP_SIZE = 8;
    private static final int MOD_COUNT_OFFSET = TIMESTAMP_OFFSET + TIMESTAMP_SIZE;
    private static final int MOD_COUNT_SIZE = 4;
    private static final int LENGTH_OFFSET = MOD_COUNT_OFFSET + MOD_COUNT_SIZE;
    private static final int LENGTH_SIZE = 8;

    private static final int RECORD_SIZE = LENGTH_OFFSET + LENGTH_SIZE;

    public static final int CHILDREN_CACHED_FLAG = 0x01;
    public static final int IS_DIRECTORY_FLAG = 0x02;
    public static final int IS_READ_ONLY = 0x04;
    public static final int MUST_RELOAD_CONTENT = 0x08;
    public static final int IS_SYMLINK = 0x10;
    public static final int IS_SPECIAL = 0x20;
    public static final int IS_HIDDEN = 0x40;
    public static final int FREE_RECORD_FLAG = 0x100;

    private int getRecordInt(int id, int offset) {
      return records.getInt(getOffset(id, offset));
    }

    private int getOffset(int id, int offset) {
      return id * RECORD_SIZE + offset;
    }

    public int getNameId(int recordId) {
      return getRecordInt(recordId, Records.NAME_OFFSET);
    }

    public int getParentId(int recordId) {
      return getRecordInt(recordId, Records.PARENT_OFFSET);
    }

    public int getAttributeRecordId(int recordId) {
      return getRecordInt(recordId, Records.ATTR_REF_OFFSET);
    }

    public int getContentId(int recordId) {
      try {
        return getRecordInt(recordId, Records.CONTENT_OFFSET);
      } catch (Throwable t) {
        t.printStackTrace();
        return 0;
      }
    }

    public long getLength(int recordId) {
      return records.getLong(getOffset(recordId, Records.LENGTH_OFFSET));
    }

    public long getTimestamp(int recordId) {
      return records.getLong(getOffset(recordId, Records.TIMESTAMP_OFFSET));
    }

    public int getModCount(int recordId) {
      return getRecordInt(recordId, Records.MOD_COUNT_OFFSET);
    }

    public int getFlags(int recordId) {
      return getRecordInt(recordId, Records.FLAGS_OFFSET);
    }

    public boolean isFlagSet(int recordId, int flag) {
      return (getFlags(recordId) & flag) == flag;
    }

    public boolean isDirectory(int recordId) {
      return isFlagSet(recordId, IjViewer.Records.IS_DIRECTORY_FLAG);
    }

    public boolean areChildrenCached(int recordId) {
      return isFlagSet(recordId, IjViewer.Records.CHILDREN_CACHED_FLAG);
    }
  }

  private static class AttribEnum {
    private final List<String> attribs;
    private final Map<String, Integer> attribIdsByName;

    AttribEnum(List<String> attribs, Map<String, Integer> attribIdsByName) {
      this.attribs = attribs;
      this.attribIdsByName = attribIdsByName;
    }

    public static AttribEnum read(File cacheDir) throws IOException {
      File f = new File(cacheDir, "vfs_enum_attrib.dat");

      List<String> attribs = new ArrayList<>();
      Map<String, Integer> attribIdsByName = new HashMap<>();
      try (DataInputStream input = new DataInputStream(new FileInputStream(f))) {
        DataInputOutputUtil.readTIME(input); // timestamp
        DataInputOutputUtil.readINT(input); // version

        while (input.available() > 0) {
          String entry = IOUtil.readUTF(input);
          attribs.add(entry);
          attribIdsByName.put(entry, attribs.size());
        }
      }

      return new AttribEnum(attribs, attribIdsByName);
    }
  }

  class ChildrenAttribute {
    public static final String NAME = "FsRecords.DIRECTORY_CHILDREN";

    private final int fileId;

    ChildrenAttribute(int fileId) {
      this.fileId = fileId;
    }

    public int[] read(DataInputStream input) throws IOException {
      if (input == null)
        return new int[0];

      int count = DataInputOutputUtil.readINT(input);
      int[] result = new int[count];

      int prevId = this.fileId;
      for (int i = 0; i < count; i++) {
        prevId = result[i] = DataInputOutputUtil.readINT(input) + prevId;
      }
      return result;
    }

    public int getCount(DataInputStream input) throws IOException {
      if (input == null)
        return 0;
      return DataInputOutputUtil.readINT(input);
    }
  }
}
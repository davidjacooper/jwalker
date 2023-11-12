package au.djac.jwalker.attr;

import java.nio.file.attribute.*;
import java.util.*;
import java.util.function.*;

/**
 * A bundle of file attribute metadata, available to access via a series of constants to be passed
 * to the {@link get} and {@link getOrDefault} methods.

 * <p>The {@link forEach} method iterates over attribute-value pairs. However, a few attributes are
 * <em>calculated</em> rather than stored, and will not show up this way.
 *
 * <p>This class implements {@link BasicFileAttributes}, if that level of JDK compatibility is
 * useful.
 *
 * <p>However, the anticipated general case involves different archive formats, with a heterogeneous
 * mix of different kinds of attributes, and this may not be well represented by
 * {@code BasicFileAttributes} or even its subinterfaces {@link PosixFileAttributes} and
 * {@link DosFileAttributes} (which appear to contemplate a single, homogenous filesystem).
 * Hence, this class provides a dynamically extendable map of attributes.
 */
public class FileAttributes implements BasicFileAttributes
{
    /**
     * Represents an attribute property. Instances of this are expected to be public constants, to
     * represent _kinds_ of file attributes (notably, not specific attributes for specific files).
     */
    public interface Attr<T>
    {
        T from(Map<Attr<?>,Object> attrMap);
    }

    /**
     * Represents the predominant kind of attribute property, one actually stored in the map (rather
     * than calculated).
     */
    public static class StoredAttr<T> implements Attr<T>
    {
        private String name;

        public StoredAttr(String name)
        {
            this.name = name;
        }

        @Override
        public T from(Map<Attr<?>,Object> attrMap)
        {
            @SuppressWarnings("unchecked")
            T value = (T) attrMap.get(this);
            return value;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }

    /** File creation time. */
    public static final Attr<FileTime> CREATION_TIME = new StoredAttr<>("creation time");

    /** File access time. */
    public static final Attr<FileTime> LAST_ACCESS_TIME = new StoredAttr<>("last access time");

    /** File modification time. */
    public static final Attr<FileTime> LAST_MODIFIED_TIME = new StoredAttr<>("last modified time");

    /** File type; e.g., {@link REGULAR_FILE}, {@link DIRECTORY}, etc. */
    public static final Attr<FileType> TYPE = new StoredAttr<>("file type");

    /** File size, in bytes (uncompressed, where applicable). */
    public static final Attr<Long> SIZE = new StoredAttr<>("size");

    /** In UNIX archives/filesystems, the username of the file's owner. */
    public static final Attr<String> USER_NAME = new StoredAttr<>("user name");

    /** In UNIX archives/filesystems, the name of the file's group. */
    public static final Attr<String> GROUP_NAME = new StoredAttr<>("group name");

    /** In UNIX archives/filesystems, the ID of the file's owner. */
    public static final Attr<Long> USER_ID = new StoredAttr<>("user ID");

    /** In UNIX archives/filesystems, the ID of the file's group. */
    public static final Attr<Long> GROUP_ID = new StoredAttr<>("group ID");

    /** In UNIX archives/filesystems, the file's permission flags (read, write, execute, set ID and
    sticky). */
    public static final Attr<UnixPermissions> UNIX_PERMISSIONS = new StoredAttr<>("UNIX permissions");

    public static final Attr<DosAttributes> DOS = new StoredAttr<>("DOS attributes");

    // public static final Attr<Boolean> DOS_READONLY = new StoredAttr<>("dos-readonly");
    // public static final Attr<Boolean> DOS_HIDDEN   = new StoredAttr<>("dos-hidden");
    // public static final Attr<Boolean> DOS_ARCHIVE  = new StoredAttr<>("dos-archive");
    // public static final Attr<Boolean> DOS_SYSTEM   = new StoredAttr<>("dos-system");

    /** The type of archive in which a file is stored (where applicable). */
    public static final Attr<Archive> ARCHIVE = new StoredAttr<>("archive");

    /** In ARJ archives, the host operating system under which the archive was created (which seems
        to be available on a file-by-file basis). */
    public static final Attr<ArjHostOS> ARJ_HOST_OS = new StoredAttr<>("ARJ host OS");


    // public static final Attr<Integer> ARJ_PLATFORM_CODE = new StoredAttr<>("ARJ-host-OS-code");
    // public static final Attr<ArjPlatform> ARJ_PLATFORM = new Attr<>()
    // {
    //     @Override
    //     public ArjPlatform from(Map<Attr<?>,Object> attrMap)
    //     {
    //         return ArjPlatform.forCode(ARJ_PLATFORM_CODE.from(attrMap));
    //     }
    //
    //     @Override
    //     public String toString()
    //     {
    //         return "ARJ-host-OS";
    //     }
    // };

    /* In GZIP files, the host filesystem on which the archive was created. */
    public static final Attr<GzipHostFS> GZIP_HOST_FS = new StoredAttr<>("GZIP host FS");

    // public static final Attr<Integer> GZIP_HOST_FS_CODE = new StoredAttr<>("GZIP-host-FS-code");
    // public static final Attr<GzipHostFS> GZIP_HOST_FS = new Attr<>()
    // {
    //     @Override
    //     public GzipHostFS from(Map<Attr<?>,Object> attrMap)
    //     {
    //         return GzipHostFS.forCode(GZIP_HOST_FS_CODE.from(attrMap));
    //     }
    //
    //     @Override
    //     public String toString()
    //     {
    //         return "GZIP-host-FS";
    //     }
    // };

    /* An archive-based checksum stored for validation purposes. The algorithm used to create it
       depends on the archive format. */
    public static final Attr<Long> CHECKSUM = new StoredAttr<>("checksum");

    /* A free-form comment associated with a file, particularly in a ZIP archive. */
    public static final Attr<String> COMMENT = new StoredAttr<>("comment");


    private Map<Attr<?>,Object> attrMap = new HashMap<>();
    public FileAttributes() {}

    public FileAttributes copy()
    {
        var a = new FileAttributes();
        a.attrMap.putAll(attrMap);
        return a;
    }

    public <T> void put(Attr<T> attr, T value)
    {
        if(value == null)
        {
            attrMap.remove(attr);
        }
        else
        {
            attrMap.put(attr, value);
        }
    }

    public <T> T get(Attr<T> attr)
    {
        return attr.from(attrMap);
    }

    public <T> T getOrDefault(Attr<T> attr, Supplier<T> defaultSupplier)
    {
        var v = get(attr);
        return (v != null) ? v : defaultSupplier.get();
    }

    public boolean has(Attr<?> attr)
    {
        return attr.from(attrMap) != null;
    }

    public boolean isType(FileType... types)
    {
        var actualType = get(TYPE);
        for(var t : types)
        {
            if(t == actualType)
            {
                return true;
            }
        }
        return false;
    }

    public void forEach(BiConsumer<Attr<?>,Object> action)
    {
        attrMap.forEach(action);
    }

    @Override
    public long size()
    {
        return getOrDefault(SIZE, () -> 0L);
    }

    @Override
    public FileTime creationTime()
    {
        return getOrDefault(CREATION_TIME, () -> FileTime.fromMillis(0L));
    }

    @Override
    public FileTime lastAccessTime()
    {
        return getOrDefault(LAST_ACCESS_TIME, () -> FileTime.fromMillis(0L));
    }

    @Override
    public FileTime lastModifiedTime()
    {
        return getOrDefault(LAST_MODIFIED_TIME, () -> FileTime.fromMillis(0L));
    }

    @Override
    public Object fileKey() { return null; }

    @Override
    public boolean isDirectory()
    {
        return get(TYPE) == FileType.DIRECTORY;
    }

    @Override
    public boolean isRegularFile()
    {
        return get(TYPE) == FileType.REGULAR_FILE;
    }

    @Override
    public boolean isSymbolicLink()
    {
        return get(TYPE) == FileType.SYMBOLIC_LINK;
    }

    @Override
    public boolean isOther()
    {
        var t = get(TYPE);
        return t != FileType.DIRECTORY && t != FileType.REGULAR_FILE && t != FileType.SYMBOLIC_LINK;
    }

    @Override
    public int hashCode()
    {
        return attrMap.hashCode();
    }

    @Override
    public boolean equals(Object other)
    {
        if(!(other instanceof FileAttributes)) { return false; }
        return attrMap.equals(((FileAttributes)other).attrMap);
    }

    @Override
    public String toString()
    {
        return attrMap.toString();
    }
}

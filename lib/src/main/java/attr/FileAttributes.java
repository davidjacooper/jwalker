package au.djac.jwalker.attr;

import java.nio.file.attribute.*;
import java.util.*;
import java.util.function.*;

/**
 * A bundle of file attribute metadata, available to access via a series of constants to be passed
 * to the {@link get} and {@link getOrDefault} methods. Additionally, the {@link forEach} method
 * iterates over attribute-value pairs that exist for a given file.
 *
 * <p>This class implements {@link BasicFileAttributes}, if that level of JDK compatibility is
 * useful, though only limited information is available via that interface.
 *
 * <p>The anticipated general case involves different archive formats, with a heterogeneous
 * mix of different kinds of attributes, and this may not be well represented by
 * {@code BasicFileAttributes} or even its subinterfaces {@link PosixFileAttributes} and
 * {@link DosFileAttributes} (which appear to contemplate a single, homogenous filesystem).
 *
 * <p>Hence, this class provides a dynamically extendable map of attributes.
 */
public class FileAttributes implements BasicFileAttributes
{
    /**
     * Represents an attribute property. Instances of this are expected to be public constants, to
     * represent <em>kinds</em> of file attributes (notably, not specific attributes for specific
     * files).
     */
    public static class Attr<T>
    {
        private String name;

        public Attr(String name)
        {
            this.name = name;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }

    /** File creation time. */
    public static final Attr<FileTime> CREATION_TIME = new Attr<>("creation time");

    /** File access time. */
    public static final Attr<FileTime> LAST_ACCESS_TIME = new Attr<>("last access time");

    /** File modification time. */
    public static final Attr<FileTime> LAST_MODIFIED_TIME = new Attr<>("last modified time");

    /** File type; e.g., {@link FileType#REGULAR_FILE}, {@link FileType#DIRECTORY}, etc. */
    public static final Attr<FileType> TYPE = new Attr<>("file type");

    /** File size, in bytes (uncompressed, where applicable). */
    @SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
    public static final Attr<Long> SIZE = new Attr<>("size");

    /** In UNIX archives/filesystems, the username of the file's owner. */
    public static final Attr<String> USER_NAME = new Attr<>("user name");

    /** In UNIX archives/filesystems, the name of the file's group. */
    public static final Attr<String> GROUP_NAME = new Attr<>("group name");

    /** In UNIX archives/filesystems, the ID of the file's owner. */
    public static final Attr<Long> USER_ID = new Attr<>("user ID");

    /** In UNIX archives/filesystems, the ID of the file's group. */
    public static final Attr<Long> GROUP_ID = new Attr<>("group ID");

    /** In UNIX archives/filesystems, the file's permission flags (read, write, execute, set ID and
    sticky). */
    public static final Attr<UnixPermissions> UNIX_PERMISSIONS = new Attr<>("UNIX permissions");

    /** In Windows or DOS, the file's basic attribute flags: "read-only", "hidden", "system" and
        "archive". */
    public static final Attr<DosAttributes> DOS = new Attr<>("DOS attributes");

    /** The type of archive in which a file is stored (where applicable). The presence or absence
        of this attribute indicates whether the file is stored within an archive or not. */
    public static final Attr<Archive> IN_ARCHIVE = new Attr<>("archive");

    /** In ARJ archives, the host operating system under which the archive was created (which seems
        to be available on a file-by-file basis). */
    public static final Attr<ArjHostOS> ARJ_HOST_OS = new Attr<>("ARJ host OS");

    /* In GZIP files, the host filesystem on which the archive was created. */
    public static final Attr<GzipHostFS> GZIP_HOST_FS = new Attr<>("GZIP host FS");

    /* An archive-based checksum stored for validation purposes. The algorithm used to create it
       depends on the archive format. */
    public static final Attr<Long> CHECKSUM = new Attr<>("checksum");

    /* A free-form comment associated with a file, particularly in a ZIP archive. */
    public static final Attr<String> COMMENT = new Attr<>("comment");


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
        @SuppressWarnings("unchecked")
        T value = (T) attrMap.get(attr);

        return value;
    }

    public <T> T getOrDefault(Attr<T> attr, Supplier<T> defaultSupplier)
    {
        var v = get(attr);
        return (v != null) ? v : defaultSupplier.get();
    }

    public boolean has(Attr<?> attr)
    {
        return attrMap.containsKey(attr);
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals")  // Reference equality (==) intended
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

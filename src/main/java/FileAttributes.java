package au.djac.jwalker;

import java.nio.file.attribute.*;
import java.util.*;
import java.util.function.*;

/**
 * A bundle of file attribute metadata, available to access via a series of constants to be passed
 * to the get() and getOrDefault() methods.

 * There is also a 'forEach()' method (similar to Map.forEach()), to which you can pass a lambda
 * expression taking two parameters, one for the attribute constant, and one for the value. However,
 * some attributes are _calculated_ rather than stored, and will not (currently) show up this way.
 *
 * This class implements java.nio.file.attributes.BasicFileAttributes, the JDK's
 * platform-independent representation of file attributes. This facility is provided for some
 * measure of compatibility with java.nio.file.attribute.
 *
 * However, in the general case to which JWalker may be applied, different files will be stored in
 * different archive formats, with a heterogeneous mix of different kinds of attributes. Much of an
 * archive file's metadata is not represented by 'BasicFileAttributes'. This has subinterfaces
 * 'PosixFileAttributes' and 'DosFileAttributes', but even these do not cover all the metadata
 * available. The hierarchy could be extended further, in principle, but it ultimately seems more
 * convenient to have a dynamically extendable map of attributes rather than a series of interfaces.
 */
public class FileAttributes implements BasicFileAttributes
{
    /**
     * Represents a type of entry in a filesystem or archive. This list is mostly a superset of file
     * types available in various different archives formats, and an extensible list that permits
     * further types if needed.
     *
     * Importantly, archive files (.zip, .tar, etc.) and individually-compressed files (.bzip2,
     * .gz, etc.) have their own categories, 'ARCHIVE' and 'COMPRESSED_FILE', distinct from
     * 'REGULAR_FILE'. This reflects the operation of JWalker, in which archives and compressed
     * files are treated more like _directories_ than files (though they are not strictly
     * directories either, of course).
     */
    public static class Type
    {
        public static final Type REGULAR_FILE       = new Type("regular file");
        public static final Type COMPRESSED_FILE    = new Type("compressed file");
        public static final Type ARCHIVE            = new Type("archive");
        public static final Type DIRECTORY          = new Type("directory");
        public static final Type SYMBOLIC_LINK      = new Type("symbolic link");
        public static final Type HARD_LINK          = new Type("hard link");
        public static final Type BLOCK_DEVICE       = new Type("block device");
        public static final Type CHARACTER_DEVICE   = new Type("character device");
        public static final Type FIFO               = new Type("FIFO pipe");
        public static final Type SOCKET             = new Type("socket");
        public static final Type WHITEOUT           = new Type("whiteout/anti-item");
        public static final Type NETWORK            = new Type("network device");

        private String s;
        public Type(String s) { this.s = s; }

        @Override
        public String toString() { return s; }
    }

    /**
     * Represents a type of archive (or compressed file) format. This list is extensible.
     */
    public static class Archive
    {
        public static final Archive AR          = new Archive();
        public static final Archive ARJ         = new Archive();
        public static final Archive CPIO        = new Archive();
        public static final Archive DUMP        = new Archive();
        public static final Archive RAR         = new Archive();
        public static final Archive SEVENZ      = new Archive();
        public static final Archive TAR         = new Archive();
        public static final Archive ZIP         = new Archive();
        public static final Archive BROTLI      = new Archive();
        public static final Archive BZIP2       = new Archive();
        public static final Archive GZIP        = new Archive();
        public static final Archive LZMA        = new Archive();
        public static final Archive XZ          = new Archive();
        public static final Archive Z           = new Archive();
        public static final Archive ZSTANDARD   = new Archive();
        public static final Archive UNKNOWN     = new Archive();
    }

    /**
     * Represents the "host OS" values available an an ARJ archive file. ("UNKNOWN" will be assigned
     * if the numeric code doesn't match any recognised value.)
     */
    public enum ArjPlatform
    {
        UNKNOWN("unknown"), MSDOS("MSDOS"), PRIMOS("PRIMOS"), UNIX("UNIX"), AMIGA("AMIGA"),
        MAC_OS("MAC-OS"), OS_2("OS/2"), APPLE_GS("APPLE GS"), ATARI_ST("ATARI ST"), NEXT("NEXT"),
        VAX_VMS("VAX VMS"), WIN95("WIN95"), WIN32("WIN32");

        private String s;
        ArjPlatform(String s) { this.s = s; }

        @Override
        public String toString() { return s; }
    }

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

    /**
     * A list of the PosixFilePermission constants by order of bit position in a conventional UNIX
     * file mode integer. That is, 'OTHERS_EXECUTE' is bit 0 (the rightmost), OTHERS_READ is bit 1,
     * and so on. This is used for converting between that bit set and Set objects.
     */
    public static final PosixFilePermission[] POSIX_FILE_PERMISSIONS = {
        PosixFilePermission.OTHERS_EXECUTE, PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE,
        PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
        PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE};

    public static final Attr<FileTime> CREATION_TIME = new StoredAttr<>("creation-time");
    public static final Attr<FileTime> LAST_ACCESS_TIME = new StoredAttr<>("last-access-time");
    public static final Attr<FileTime> LAST_MODIFIED_TIME = new StoredAttr<>("last-modified-time");
    public static final Attr<Type> TYPE = new StoredAttr<>("file-type");
    public static final Attr<Long> SIZE = new StoredAttr<>("size");

    public static final Attr<String> USER_NAME = new StoredAttr<>("user-name");
    public static final Attr<String> GROUP_NAME = new StoredAttr<>("group-name");
    public static final Attr<Long> USER_ID = new StoredAttr<>("user-id");
    public static final Attr<Long> GROUP_ID = new StoredAttr<>("group-id");
    public static final Attr<Integer> MODE = new StoredAttr<>("mode");

    public static final Attr<Set<PosixFilePermission>> UNIX_PERMISSIONS = new Attr<>()
    {
        @Override
        public Set<PosixFilePermission> from(Map<Attr<?>,Object> attrMap)
        {
            var set = EnumSet.noneOf(PosixFilePermission.class);
            int m = MODE.from(attrMap);
            for(var attr : POSIX_FILE_PERMISSIONS)
            {
                if((m & 0x01) == 1)
                {
                    set.add(attr);
                }
                m >>= 1;
            }
            return set;
        }

        @Override
        public String toString()
        {
            return "permissions";
        }
    };

    public static final Attr<Boolean> DOS_READONLY = new StoredAttr<>("dos-readonly");
    public static final Attr<Boolean> DOS_HIDDEN   = new StoredAttr<>("dos-hidden");
    public static final Attr<Boolean> DOS_ARCHIVE  = new StoredAttr<>("dos-archive");
    public static final Attr<Boolean> DOS_SYSTEM   = new StoredAttr<>("dos-system");

    public static final Attr<Archive> ARCHIVE = new StoredAttr<>("archive");

    public static final Attr<Integer> ARJ_PLATFORM_CODE = new StoredAttr<>("host-OS-code");
    public static final Attr<ArjPlatform> ARJ_PLATFORM = new Attr<>()
    {
        @Override
        public ArjPlatform from(Map<Attr<?>,Object> attrMap)
        {
            int platformCode = ARJ_PLATFORM_CODE.from(attrMap) + 1;
            ArjPlatform[] arr = ArjPlatform.values();
            return (1 <= platformCode && platformCode < arr.length)
                ? arr[platformCode]
                : ArjPlatform.UNKNOWN;
        }

        @Override
        public String toString()
        {
            return "host-OS";
        }
    };

    public static final Attr<Long> CHECKSUM = new StoredAttr<>("checksum");
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
        return get(TYPE) == Type.DIRECTORY;
    }

    @Override
    public boolean isRegularFile()
    {
        return get(TYPE) == Type.REGULAR_FILE;
    }

    @Override
    public boolean isSymbolicLink()
    {
        return get(TYPE) == Type.SYMBOLIC_LINK;
    }

    @Override
    public boolean isOther()
    {
        var t = get(TYPE);
        return t != Type.DIRECTORY && t != Type.REGULAR_FILE && t != Type.SYMBOLIC_LINK;
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

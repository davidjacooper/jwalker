package au.djac.jwalker;

import java.nio.file.attribute.*;
import java.util.*;

public class FileAttributes implements BasicFileAttributes
{
    public enum Type {
        REGULAR_FILE, COMPRESSED_FILE, ARCHIVE, DIRECTORY, SYMBOLIC_LINK, HARD_LINK,
        BLOCK_DEVICE, CHARACTER_DEVICE, FIFO, SOCKET, WHITEOUT, NETWORK
    }

    public enum ArjPlatform {
        MSDOS, PRIMOS, UNIX, AMIGA, MAC_OS, OS_2, APPLE_GS, ATARI_ST, NEXT, VAX_VMS, WIN95, WIN32
    }

    public interface Attr<T>
    {
        T from(Map<Attr<?>,Object> attrMap);
    }

    public static class StoredAttr<T> implements Attr<T>
    {
        @Override
        public T from(Map<Attr<?>,Object> attrMap)
        {
            @SuppressWarnings("unchecked")
            T value = (T) attrMap.get(this);
            return value;
        }
    }

    private static final FileTime DEFAULT_TIME = FileTime.fromMillis(0L);

    public static final PosixFilePermission[] POSIX_FILE_PERMISSIONS = {
        PosixFilePermission.OTHERS_EXECUTE, PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE,
        PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
        PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE};

    public static final Attr<FileTime> CREATION_TIME = new StoredAttr<>();
    public static final Attr<FileTime> LAST_ACCESS_TIME = new StoredAttr<>();
    public static final Attr<FileTime> LAST_MODIFIED_TIME = new StoredAttr<>();
    public static final Attr<Type> TYPE = new StoredAttr<>();
    public static final Attr<Long> SIZE = new StoredAttr<>();

    public static final Attr<String> USER_NAME = new StoredAttr<>();
    public static final Attr<String> GROUP_NAME = new StoredAttr<>();
    public static final Attr<Long> USER_ID = new StoredAttr<>();
    public static final Attr<Long> GROUP_ID = new StoredAttr<>();
    public static final Attr<Integer> MODE = new StoredAttr<>();

    public static final Attr<Set<PosixFilePermission>> UNIX_PERMISSIONS =
        attrMap ->
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
        };

    public static final Attr<Integer> ARJ_PLATFORM_CODE = new StoredAttr<>();
    public static final Attr<ArjPlatform> ARJ_PLATFORM =
        attrMap ->
        {
            int platformCode = ARJ_PLATFORM_CODE.from(attrMap);
            ArjPlatform[] arr = ArjPlatform.values();
            return (0 <= platformCode && platformCode < arr.length) ? arr[platformCode] : null;
        };

    public static final Attr<Integer> DOS_ATTRIBUTES = new StoredAttr<>();
    public static final Attr<Long> CHECKSUM = new StoredAttr<>();
    public static final Attr<String> COMMENT = new StoredAttr<>();

    private Map<Attr<?>,Object> attrMap = new HashMap<>();

    public <T> void put(Attr<T> attr, T value)
    {
        attrMap.put(attr, value);
    }

    public <T> T get(Attr<T> attr)
    {
        return attr.from(attrMap);
    }

    public <T> T getOrDefault(Attr<T> attr, T defaultValue)
    {
        var v = get(attr);
        return (v != null) ? v : defaultValue;
    }

    @Override
    public long size()
    {
        return getOrDefault(SIZE, 0L);
    }

    @Override
    public FileTime creationTime()
    {
        return getOrDefault(CREATION_TIME, DEFAULT_TIME);
    }

    @Override
    public FileTime lastAccessTime()
    {
        return getOrDefault(LAST_ACCESS_TIME, DEFAULT_TIME);
    }

    @Override
    public FileTime lastModifiedTime()
    {
        return getOrDefault(LAST_MODIFIED_TIME, DEFAULT_TIME);
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
}

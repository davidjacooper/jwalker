package au.djac.jwalker.attr;

import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

/**
 * Represents a set of UNIX-style file permissions, including read/write/execute permissions for
 * the file's user (owner), group and others, as well as the "set UID", "set GUI" and "sticky" bits.
 */
public final class UnixPermissions
{
    /**
     * A list of the PosixFilePermission constants by order of bit position in a conventional
     * UNIX file mode integer. That is, {@code OTHERS_EXECUTE} is bit 0 (the rightmost),
     * {@code OTHERS_READ} is bit 1, and so on. This is used for converting between that bit set and
     * Set objects.
     */
    private static final PosixFilePermission[] POSIX_FILE_PERMISSIONS = {
        PosixFilePermission.OTHERS_EXECUTE, PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE,
        PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
        PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE};

    /**
     * Create a {@code UnixPermissions} instance from a "mode" value, which contains the permissions
     * flags in the lower 12 bits. (The other bits are ignored.)
     *
     * @param mode A UNIX mode value.
     * @return A new {@code UnixPermissions} instance, reflecting the same permission set.
     */
    public static UnixPermissions forMode(int mode)
    {
        return new UnixPermissions(mode);
    }

    /**
     * Create a {@code UnixPermissions} instance from a set of {@link PosixFilePermission}, as might
     * be returned from {@link PosixFileAttributes.permissions}, for compatibility with the JDK.
     *
     * <p>Note that {@code PosixFilePermission} <em>does not</em> have a representation of the
     * "set UID", "set GUI" and "sticky" bits.
     *
     * @param set A set of file permissions.
     * @return A new {@code UnixPermissions} instance, reflecting the same permission set.
     */
    public static UnixPermissions forSet(Set<PosixFilePermission> set)
    {
        int mode = 0;
        int bit = 0;
        for(var perm : POSIX_FILE_PERMISSIONS)
        {
            if(set.contains(perm))
            {
                mode |= (1 << bit);
            }
            bit++;
        }
        return new UnixPermissions(mode);
    }

    private int mode;
    private UnixPermissions(int mode)
    {
        this.mode = mode;
    }

    public int getMode() { return mode; }

    public boolean isUserExecutable()  { return (mode & 0100) == 0100; }
    public boolean isUserWritable()    { return (mode & 0200) == 0200; }
    public boolean isUserReadable()    { return (mode & 0400) == 0400; }

    public boolean isGroupExecutable() { return (mode & 0010) == 0010; }
    public boolean isGroupWritable()   { return (mode & 0020) == 0020; }
    public boolean isGroupReadable()   { return (mode & 0040) == 0040; }

    public boolean isOtherExecutable() { return (mode & 0001) == 0001; }
    public boolean isOtherWritable()   { return (mode & 0002) == 0002; }
    public boolean isOtherReadable()   { return (mode & 0004) == 0004; }

    public boolean isSticky()          { return (mode & 01000) == 01000; }
    public boolean isSetGID()          { return (mode & 02000) == 02000; }
    public boolean isSetUID()          { return (mode & 04000) == 04000; }

    public Set<PosixFilePermission> set()
    {
        var set = EnumSet.noneOf(PosixFilePermission.class);
        int m = mode;
        for(var perm : POSIX_FILE_PERMISSIONS)
        {
            if((m & 0x01) == 1)
            {
                set.add(perm);
            }
            m >>= 1;
        }
        return set;
    }

    @Override
    public String toString()
    {
        return new String(new char[] {
            isUserReadable() ? 'r' : '-',
            isUserWritable() ? 'w' : '-',
            isSetUID() ? (isUserExecutable() ? 's' : 'S')
                       : (isUserExecutable() ? 'x' : '-'),
            isGroupReadable() ? 'r' : '-',
            isGroupWritable() ? 'w' : '-',
            isSetGID() ? (isGroupExecutable() ? 's' : 'S')
                       : (isGroupExecutable() ? 'x' : '-'),
            isOtherReadable() ? 'r' : '-',
            isOtherWritable() ? 'w' : '-',
            isSticky() ? (isOtherExecutable() ? 't' : 'T')
                       : (isOtherExecutable() ? 'x' : '-')
        });
    }
}

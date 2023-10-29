package au.djac.jdirscanner;

public final class Attributes
{
    public static final String ACCESS_TIME   = "atime";
    public static final String CREATION_TIME = "ctime";
    public static final String MODIFIED_TIME = "mtime";
    public static final String COMMENT       = "comment"; // See ZipEntry.getComment()
    public static final String GROUP_ID      = "gid";
    public static final String USER_ID       = "uid";
    public static final String GROUP_NAME    = "group";
    public static final String USER_NAME     = "user";
    public static final String ZIP_PLATFORM  = "zip:platform";
    public static final String ARJ_PLATFORM  = "arj:platform";

    public static final String[] ARJ_PLATFORM_NAMES = {
        // https://github.com/FarGroup/FarManager/blob/master/plugins/multiarc/arc.doc/arj.txt
        // https://commons.apache.org/proper/commons-compress/javadocs/api-release/org/apache/commons/compress/archivers/arj/ArjArchiveEntry.HostOs.html
        "MSDOS", "PRIMOS", "UNIX", "AMIGA", "MAC-OS", "OS/2", "APPLE GS", "ATARI ST", "NEXT",
        "VAX VMS", "WIN95", "WIN32"
    };
}

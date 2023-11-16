package au.djac.jwalker.attr;

/**
 * Represents a type of entry in a filesystem or archive. This list is extensible.
 *
 * <p>Importantly, archive files (.zip, .tar, etc.) and individually-compressed files (.bzip2,
 * .gz, etc.) have their own categories, {@code ARCHIVE} and {@code COMPRESSED_FILE}, distinct
 * from {@code REGULAR_FILE}. This reflects the operation of JWalker, in which archives and
 * compressed files are treated more like <em>directories</em> than files (though they are not
 * strictly directories either, of course).
 */
public final class FileType
{
    public static final FileType REGULAR_FILE       = new FileType("regular file");
    public static final FileType COMPRESSED_FILE    = new FileType("compressed file");
    public static final FileType ARCHIVE            = new FileType("archive");
    public static final FileType DIRECTORY          = new FileType("directory");
    public static final FileType SYMBOLIC_LINK      = new FileType("symbolic link");
    public static final FileType HARD_LINK          = new FileType("hard link");
    public static final FileType BLOCK_DEVICE       = new FileType("block device");
    public static final FileType CHARACTER_DEVICE   = new FileType("character device");
    public static final FileType FIFO               = new FileType("FIFO pipe");
    public static final FileType SOCKET             = new FileType("socket");
    public static final FileType WHITEOUT           = new FileType("whiteout/anti-item");
    public static final FileType NETWORK            = new FileType("network (HP/UX)");
    public static final FileType DOOR               = new FileType("door (Solaris)");
    public static final FileType EVENT_PORT         = new FileType("event port (Solaris)");
    public static final FileType UNKNOWN            = new FileType("unknown");

    /**
     * Reports the {@code FileType} instance implied by a given UNIX file mode. The high 4 bits of a
     * 16-bit mode value are used to encode a file type, on UNIX systems, and in archive files
     * that make room for such values. (The rest of the mode value contains permission
     * information.)
     *
     * <p>This is not the sole way of identifying a file type, however. It is applicable only to
     * UNIX environments, and to a subset of file types. Different filesystems and archive
     * formats have their own approaches.
     *
     * @param mode A UNIX file mode (as defined by the 'mode_t' type in sys/types.h in C).
     * @return The corresponding {@code FileType} instance, or {@link UNKNOWN} if the mode doesn't
     * match a known type.
     */
    public static FileType forMode(int mode)
    {
        switch(mode & 0xf000)
        {
            // Generic UNIX file types:
            case 0x1000: return FIFO;
            case 0x2000: return CHARACTER_DEVICE;
            case 0x4000: return DIRECTORY;
            case 0x6000: return BLOCK_DEVICE;
            case 0x8000: return REGULAR_FILE;
            case 0xa000: return SYMBOLIC_LINK;
            case 0xc000: return SOCKET;

            // HP/UX-specific:
            case 0x9000: return NETWORK;

            // Solaris-specific:
            case 0xd000: return DOOR;
            //case 0xe000: return EVENT_PORT;

            // Dump specific:
            // case 0xe000: return WHITEOUT; // Conflicts with Solaris's EVENT_PORT.

            default:     return UNKNOWN;
        }
    }


    private final String label;

    public FileType(String label)
    {
        this.label = label;
    }

    public String getLabel() { return label; }

    @Override
    public String toString() { return label; }
}

package au.djac.jwalker.attr;

/**
 * Represents the "operating system" (or rather filesystem) value specified in a GZIP file to
 * indicate its origin.
 */
public final class GzipHostFS
{
    public static final GzipHostFS FAT          = new GzipHostFS(0, "FAT");
    public static final GzipHostFS AMIGA        = new GzipHostFS(1, "Amiga");
    public static final GzipHostFS VMS          = new GzipHostFS(2, "VMS/OpenVMS");
    public static final GzipHostFS UNIX         = new GzipHostFS(3, "Unix");
    public static final GzipHostFS VM_CMS       = new GzipHostFS(4, "VM/CMS");
    public static final GzipHostFS ATARI_TOS    = new GzipHostFS(5, "Atari TOS");
    public static final GzipHostFS HPFS         = new GzipHostFS(6, "NPFS");
    public static final GzipHostFS MAC          = new GzipHostFS(7, "Macintosh");
    public static final GzipHostFS Z_SYSTEM     = new GzipHostFS(8, "Z-System");
    public static final GzipHostFS CP_M         = new GzipHostFS(9, "CP/M");
    public static final GzipHostFS TOPS_20      = new GzipHostFS(10, "TOPS-20");
    public static final GzipHostFS NTFS         = new GzipHostFS(11, "NTFS");
    public static final GzipHostFS QDOS         = new GzipHostFS(12, "QDOS");
    public static final GzipHostFS ACORN_RISCOS = new GzipHostFS(13, "Acorn RISCOS");

    private static final GzipHostFS[] VALUES = {
        FAT, AMIGA, VMS, UNIX, VM_CMS, ATARI_TOS, HPFS, MAC, Z_SYSTEM, CP_M, TOPS_20, NTFS, QDOS,
        ACORN_RISCOS};

    static
    {
        for(int i = 0; i < VALUES.length; i++)
        {
            if(i != VALUES[i].code)
            {
                throw new AssertionError();
            }
        }
    }

    public static GzipHostFS forCode(int code)
    {
        if(0 <= code && code < VALUES.length)
        {
            return VALUES[code];
        }
        else
        {
            return new GzipHostFS(code, "unknown");
        }
    }

    private final int code;
    private final String label;

    private GzipHostFS(int code, String label)
    {
        this.code = code;
        this.label = label;
    }

    public int getCode()     { return code; }
    public String getLabel() { return label; }

    @Override
    public String toString() { return label; }
}

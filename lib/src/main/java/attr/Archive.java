package au.djac.jwalker.attr;

/**
 * Represents a type of archive (or compressed file) format. This list is extensible.
 */
public final class Archive
{
    public static final Archive AR            = new Archive();
    public static final Archive ARJ           = new Archive();
    public static final Archive CPIO          = new Archive();
    public static final Archive DUMP          = new Archive();
    public static final Archive RAR           = new Archive();
    public static final Archive SEVENZ        = new Archive();
    public static final Archive TAR           = new Archive();
    public static final Archive ZIP           = new Archive();
    public static final Archive BROTLI        = new Archive();
    public static final Archive BZIP2         = new Archive();
    public static final Archive GZIP          = new Archive();
    public static final Archive LZMA          = new Archive();
    public static final Archive LZ4_BLOCK     = new Archive();
    public static final Archive LZ4_FRAMED    = new Archive();
    public static final Archive SNAPPY_FRAMED = new Archive();
    public static final Archive SNAPPY_RAW    = new Archive();
    public static final Archive XZ            = new Archive();
    public static final Archive Z             = new Archive();
    public static final Archive ZSTANDARD     = new Archive();
    public static final Archive UNKNOWN       = new Archive();
}

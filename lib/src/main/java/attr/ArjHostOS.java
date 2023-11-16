package au.djac.jwalker.attr;

/**
 * Represents the "host OS" values used within an ARJ archive file to indicate its origin
 * (apparently on a file-by-file basis).
 */
public final class ArjHostOS
{
    public static final ArjHostOS MSDOS    = new ArjHostOS(0, "MSDOS");
    public static final ArjHostOS PRIMOS   = new ArjHostOS(1, "PRIMOS");
    public static final ArjHostOS UNIX     = new ArjHostOS(2, "UNIX");
    public static final ArjHostOS AMIGA    = new ArjHostOS(3, "AMIGA");
    public static final ArjHostOS MAC_OS   = new ArjHostOS(4, "MAC-OS");
    public static final ArjHostOS OS_2     = new ArjHostOS(5, "OS/2");
    public static final ArjHostOS APPLE_GS = new ArjHostOS(6, "APPLE GS");
    public static final ArjHostOS ATARI_ST = new ArjHostOS(7, "ATARI ST");
    public static final ArjHostOS NEXT     = new ArjHostOS(8, "NEXT");
    public static final ArjHostOS VAX_VMS  = new ArjHostOS(9, "VAX VMS");
    public static final ArjHostOS WIN95    = new ArjHostOS(10, "WIN95");
    public static final ArjHostOS WIN32    = new ArjHostOS(11, "WIN32");

    private static final ArjHostOS[] VALUES = {
        MSDOS, PRIMOS, UNIX, AMIGA, MAC_OS, OS_2, APPLE_GS, ATARI_ST, NEXT, VAX_VMS, WIN95, WIN32};

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

    public static ArjHostOS forCode(int code)
    {
        if(0 <= code && code < VALUES.length)
        {
            return VALUES[code];
        }
        else
        {
            return new ArjHostOS(code, "unknown");
        }
    }


    private final int code;
    private final String label;

    private ArjHostOS(int code, String label)
    {
        this.code = code;
        this.label = label;
    }

    public int getCode()     { return code; }
    public String getLabel() { return label; }

    @Override
    public String toString() { return label; }
}


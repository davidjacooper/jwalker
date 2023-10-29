package au.djac.jdirscanner;

public class JDirScannerException extends RuntimeException
{
    public JDirScannerException()                            { super(); }
    public JDirScannerException(String msg)                  { super(msg); }
    public JDirScannerException(Throwable cause)             { super(cause); }
    public JDirScannerException(String msg, Throwable cause) { super(msg, cause); }
}

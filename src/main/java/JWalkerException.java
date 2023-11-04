package au.djac.jwalker;

public class JWalkerException extends RuntimeException
{
    public JWalkerException()                            { super(); }
    public JWalkerException(String msg)                  { super(msg); }
    public JWalkerException(Throwable cause)             { super(cause); }
    public JWalkerException(String msg, Throwable cause) { super(msg, cause); }
}

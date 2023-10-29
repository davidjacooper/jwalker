package au.djac.jdirscanner;
import java.io.IOException;

@FunctionalInterface
public interface ErrorPolicy<E extends Exception>
{
    static ErrorPolicy DEFAULT = (msg, ex) ->
    {
        throw new JDirScannerException(msg, ex);
    };

    void enact(String msg, Exception e) throws RuntimeException;
}

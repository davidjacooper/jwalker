package au.djac.jdirscanner;
import java.io.*;

@FunctionalInterface
public interface InputSupplier
{
    InputStream get() throws IOException;
}

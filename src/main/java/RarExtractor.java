package au.djac.jdirscanner;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Extracts RAR archives, by invoking the external 'unrar' tool. This will fail if the user does
 * not have this tool installed.
 *
 * The RAR format is not supported by Apache Commons Compress, and not well supported in general,
 * due to being proprietory. There is a Java library that supports the RAR4 format
 * (https://github.com/junrar/junrar), but not RAR5 it seems.
 */
public class RarExtractor extends RandomAccessArchiveExtractor
{
    private static final Logger log = LoggerFactory.getLogger(RarExtractor.class);
    private static final long UNRAR_COMMAND_TIMEOUT = 30; // seconds

    @Override
    protected String getExtension()
    {
        return "rar";
    }

    @Override
    protected void extract(JDirScanner dirScanner,
                           Path fsPath,
                           Path displayPath) throws IOException, ArchiveSkipException
    {
        log.debug("Reading RAR archive '{}'", displayPath);
        var tmpPath = Files.createTempDirectory("jdirscanner");
        try
        {
            var unrarArg = fsPath.toAbsolutePath().toString();
            log.debug("Running 'unrar -x {}' from directory {}", unrarArg, tmpPath);
            var proc = new ProcessBuilder("unrar", "x", unrarArg)
                .directory(tmpPath.toFile())
                .start();

            // FIXME?: not sure how to grab all command-output *and* timeout after a period of
            // time. Might need
            String output = IOUtils.toString(proc.getInputStream(), Charset.defaultCharset());

            log.debug(output);

            try
            {
                if(!proc.waitFor(UNRAR_COMMAND_TIMEOUT, TimeUnit.SECONDS))
                {
                    dirScanner.error(
                        String.format(
                              "Couldn't read archive file '%s': unrar command timed out",
                              displayPath),
                        null);

                    // Skip only this file. But if errorPolicy is 'ABORT', then an AddAbortException
                    // will have already been thrown.
                    throw new ArchiveSkipException();
                }
            }
            catch(InterruptedException e) { throw new AssertionError(); }

            // Walk directory tree
            dirScanner.walkTree(tmpPath, displayPath);

        }
        finally
        {
            // Recursively delete tmpPath
            FileUtils.deleteQuietly(tmpPath.toFile());
        }
    }
}

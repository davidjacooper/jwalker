package au.djac.jdirscanner;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Represents an extractor that requires (or benefits from) random read access to an archive file,
 * and not just an InputStream. Specifically, this includes ZIP, 7Z and RAR formats.
 *
 * If an InputStream is all that's available -- e.g., if the archive is nested inside another
 * archive -- this class will write it out to a temporary file, and use that for random access.
 */
public abstract class RandomAccessArchiveExtractor extends ArchiveExtractor
{
    private static final Logger log = LoggerFactory.getLogger(RandomAccessArchiveExtractor.class);

    protected abstract String getExtension();

    @Override
    public Set<String> getFileExtensions()
    {
        return Set.of(getExtension());
    }

    protected abstract void extract(JDirScanner dirScanner,
                                    Path fsPath,
                                    Path displayPath) throws IOException, ArchiveSkipException;

    @Override
    public void extract(JDirScanner dirScanner,
                        String extension, // unused
                        Path fsPath,
                        Path displayPath,
                        InputSupplier input,
                        Map<String,String> archiveMetadata)// unused
        throws ArchiveSkipException
    {
        log.debug("Extracting random access archive '{}'", displayPath);
        try
        {
            if(fsPath == null)
            {
                var tmpPath = Files.createTempFile("jdirscanner", "." + getExtension());
                try
                {
                    IOUtils.copy(input.get(), Files.newOutputStream(tmpPath));
                    extract(dirScanner, tmpPath, displayPath);
                }
                finally
                {
                    Files.deleteIfExists(tmpPath);
                }
            }
            else
            {
                extract(dirScanner, fsPath, displayPath);
            }
        }
        catch(IOException e)
        {
            dirScanner.error("Could not extract archive '" + displayPath + "': " + e.getMessage(), e);
            throw new ArchiveSkipException(e);
        }
    }
}

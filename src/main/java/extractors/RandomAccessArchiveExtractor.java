package au.djac.jwalker.extractors;
import au.djac.jwalker.*;

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

    @Override
    public FileAttributes.Type getModifiedFileType()
    {
        return FileAttributes.Type.ARCHIVE;
    }

    protected abstract void extract(JWalkerOperation operation,
                                    Path fsPath,
                                    Path displayPath,
                                    FileAttributes archiveAttr)
                                    throws IOException, ArchiveSkipException;

    @Override
    public void extract(JWalkerOperation operation,
                        String extension, // unused
                        Path fsPath,
                        Path displayPath,
                        JWalker.InputSupplier input,
                        FileAttributes archiveAttr)
        throws ArchiveSkipException
    {
        log.debug("Extracting random access archive '{}'", displayPath);
        try
        {
            if(fsPath == null)
            {
                var tmpPath = Files.createTempFile("jwalker", "." + getExtension());
                try
                {
                    IOUtils.copy(input.get(), Files.newOutputStream(tmpPath));
                    extract(operation, tmpPath, displayPath, archiveAttr);
                }
                finally
                {
                    Files.deleteIfExists(tmpPath);
                }
            }
            else
            {
                extract(operation, fsPath, displayPath, archiveAttr);
            }
        }
        catch(IOException e)
        {
            operation.error(displayPath,
                            archiveAttr,
                            "Could not extract archive '" + displayPath + "': " + e.getMessage(),
                            e);
            throw new ArchiveSkipException(e);
        }
    }
}

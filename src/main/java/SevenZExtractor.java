package au.djac.jdirscanner;

import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Extracts 7Z archives, using the Apache Commons Compress SevenZFile class.
 *
 * Commons Compress does not provide an 'InputStream' mechanism for reading .7z files; it requires
 * random access.
 *
 * For reference, the 7Z specification is available here:
 * https://py7zr.readthedocs.io/en/latest/archive_format.html
 */
public class SevenZExtractor extends RandomAccessArchiveExtractor
{
    private static final Logger log = LoggerFactory.getLogger(SevenZExtractor.class);

    @Override
    protected String getExtension()
    {
        return "7z";
    }

    @Override
    protected void extract(JDirScanner dirScanner,
                           Path fsPath,
                           Path displayPath) throws IOException
    {
        log.debug("Reading 7Z archive '{}'", displayPath);
        try(var zipFile = new SevenZFile(fsPath.toFile()))
        {
            for(var entry : zipFile.getEntries())
            {
                if(!entry.isDirectory())
                {
                    var entryPath = Path.of("", entry.getName().split(ARCHIVE_DIRECTORY_SEPARATOR));
                    var metadata = new HashMap<String,String>();
                    log.debug("File in .7z: {}", entryPath);

                    if(entry.getHasAccessDate())
                    {
                        metadata.put(Attributes.ACCESS_TIME,
                                     entry.getAccessDate().toInstant().toString());
                    }

                    if(entry.getHasCreationDate())
                    {
                        metadata.put(Attributes.CREATION_TIME,
                                     entry.getCreationDate().toInstant().toString());
                    }

                    if(entry.getHasLastModifiedDate())
                    {
                        metadata.put(Attributes.MODIFIED_TIME,
                                     entry.getLastModifiedDate().toInstant().toString());
                    }

                    // As for .zips, there is other technical metadata available, but it's of marginal
                    // benefit.

                    dirScanner.filterFile(displayPath.resolve(entryPath),
                                          () -> zipFile.getInputStream(entry),
                                          metadata);
                }
            }
        }
    }
}

package au.djac.jwalker.extractors;
import au.djac.jwalker.*;

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
    protected void extract(JWalkerOperation operation,
                           Path fsPath,
                           Path displayPath,
                           FileAttributes archiveAttr) throws IOException
    {
        log.debug("Reading 7Z archive '{}'", displayPath);
        try(var zipFile = new SevenZFile(fsPath.toFile()))
        {
            for(var entry : zipFile.getEntries())
            {
                var entryPath = Path.of("", entry.getName().split(ARCHIVE_DIRECTORY_SEPARATOR));
                log.debug("Entry in .7z: {}", entryPath);

                var attr = new FileAttributes();
                attr.put(FileAttributes.ARCHIVE, FileAttributes.Archive.SEVENZ);
                attr.put(FileAttributes.SIZE, entry.getSize());

                FileAttributes.Type type;
                if(entry.isAntiItem())
                {
                    type = FileAttributes.Type.WHITEOUT;
                }
                else if(entry.isDirectory())
                {
                    type = FileAttributes.Type.DIRECTORY;
                }
                else
                {
                    type = FileAttributes.Type.REGULAR_FILE;
                }
                attr.put(FileAttributes.TYPE, type);


                if(entry.getHasCreationDate())
                {
                    attr.put(FileAttributes.CREATION_TIME, entry.getCreationTime());
                }

                if(entry.getHasAccessDate())
                {
                    attr.put(FileAttributes.LAST_ACCESS_TIME, entry.getAccessTime());
                }

                if(entry.getHasLastModifiedDate())
                {
                    attr.put(FileAttributes.LAST_MODIFIED_TIME, entry.getLastModifiedTime());
                }

                if(entry.getHasCrc())
                {
                    attr.put(FileAttributes.CHECKSUM, entry.getCrcValue());
                }

                if(entry.getHasWindowsAttributes())
                {
                    var flags = entry.getWindowsAttributes();
                    attr.put(FileAttributes.DOS_READONLY, (flags & 0x1) != 0);
                    attr.put(FileAttributes.DOS_HIDDEN,   (flags & 0x2) != 0);
                    attr.put(FileAttributes.DOS_ARCHIVE,  (flags & 0x20) != 0);
                }

                operation.filterFile(displayPath.resolve(entryPath),
                                     () -> zipFile.getInputStream(entry),
                                     attr);
            }
        }
    }
}

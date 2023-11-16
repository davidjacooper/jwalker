package au.djac.jwalker.extractors;
import au.djac.jwalker.*;
import au.djac.jwalker.attr.*;

import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Extracts ZIP archives, using the Apache Commons Compress ZipFile class.
 *
 * This is a slight improvement on the standard java.util.zip.ZipFile, and the Commons Compress
 * docs advise the use of ZipFile over ZipArchiveInputStream:
 * https://commons.apache.org/proper/commons-compress/javadocs/api-release/index.html
 *
 * For reference, the ZIP specification is available here:
 * https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT
 */
public class ZipExtractor extends RandomAccessArchiveExtractor
{
    private static final Logger log = LoggerFactory.getLogger(ZipExtractor.class);

    @Override
    protected String getExtension()
    {
        return "zip";
    }

    @Override
    protected void extract(JWalkerOperation operation,
                           Path fsPath,
                           Path displayPath,
                           FileAttributes archiveAttr) throws IOException
    {
        log.debug("Reading ZIP archive '{}'", displayPath);
        try(var zipFile = new ZipFile(fsPath.toFile()))
        {
            Enumeration<ZipArchiveEntry> en = zipFile.getEntries();
            while(en.hasMoreElements())
            {
                var entry = en.nextElement();
                var entryPath = Path.of("", entry.getName().split(ARCHIVE_DIRECTORY_SEPARATOR));
                log.debug("File in .zip: {}", entryPath);

                var attr = new FileAttributes();
                attr.put(FileAttributes.IN_ARCHIVE, Archive.ZIP);

                int mode = 0;
                if(entry.getPlatform() == ZipArchiveEntry.PLATFORM_UNIX)
                {
                    mode = entry.getUnixMode();
                    //attr.put(FileAttributes.MODE, mode);
                    attr.put(FileAttributes.UNIX_PERMISSIONS, UnixPermissions.forMode(mode));
                }

                attr.put(FileAttributes.SIZE,               entry.getSize());
                attr.put(FileAttributes.CREATION_TIME,      entry.getCreationTime());
                attr.put(FileAttributes.LAST_ACCESS_TIME,   entry.getLastAccessTime());
                attr.put(FileAttributes.LAST_MODIFIED_TIME, entry.getLastModifiedTime());
                attr.put(FileAttributes.COMMENT,            entry.getComment());

                FileType type;
                if(entry.isDirectory())
                {
                    type = FileType.DIRECTORY;
                }
                else if(entry.isUnixSymlink())
                {
                    type = FileType.SYMBOLIC_LINK;
                }
                else if(mode != 0)
                {
                    // If the UNIX mode exists, it may contain a file type itself.
                    type = FileType.forMode(mode);
                }
                else
                {
                    type = FileType.REGULAR_FILE;
                }
                attr.put(FileAttributes.TYPE, type);

                // TODO: other metadata?

                operation.filterFile(displayPath.resolve(entryPath),
                                     () -> zipFile.getInputStream(entry),
                                     attr);
            }
        }
    }
}

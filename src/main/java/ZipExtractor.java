package au.djac.jdirscanner;

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
    protected void extract(JDirScanner dirScanner,
                           Path fsPath,
                           Path displayPath) throws IOException
    {
        log.debug("Reading ZIP archive '{}'", displayPath);
        try(var zipFile = new ZipFile(fsPath.toFile()))
        {
            Enumeration<ZipArchiveEntry> en = zipFile.getEntries();
            while(en.hasMoreElements())
            {
                var entry = en.nextElement();
                if(!entry.isDirectory())
                {
                    var entryPath = Path.of("", entry.getName().split(ARCHIVE_DIRECTORY_SEPARATOR));
                    var metadata = new HashMap<String,String>();
                    log.debug("File in .zip: {}", entryPath);

                    var atime = entry.getLastAccessTime();
                    if(atime != null)
                    {
                        metadata.put(Attributes.ACCESS_TIME,
                                     atime.toInstant().toString());
                    }

                    var ctime = entry.getCreationTime();
                    if(ctime != null)
                    {
                        metadata.put(Attributes.CREATION_TIME,
                                     ctime.toInstant().toString());
                    }

                    var mtime = entry.getLastModifiedTime();
                    if(mtime != null)
                    {
                        metadata.put(Attributes.MODIFIED_TIME,
                                     mtime.toInstant().toString());
                    }

                    var comment = entry.getComment();
                    if(comment != null)
                    {
                        metadata.put(Attributes.COMMENT, comment);
                    }

                    metadata.put(Attributes.ZIP_PLATFORM,
                                 (entry.getPlatform() == ZipArchiveEntry.PLATFORM_FAT) ? "FAT" : "UNIX");

                    // There is other metadata available on the technical details of file storage
                    // within the .zip. We could consider adding that too, though the benefit is
                    // likely marginal.

                    dirScanner.filterFile(displayPath.resolve(entryPath),
                                          () -> zipFile.getInputStream(entry),
                                          metadata);
                }
            }
        }
    }
}

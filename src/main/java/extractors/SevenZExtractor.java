package au.djac.jwalker.extractors;
import au.djac.jwalker.attr.*;
import au.djac.jwalker.*;

import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Extracts 7Z archives, using the Apache Commons Compress.
 *
 * <p>Commons Compress does not provide an 'InputStream' mechanism for reading .7z files; it
 * requires random access. Also, Commons Compress does not yet seem to support UNIX attributes in
 * .7z files. (This may not have been part of the spec originally, but it is now.)
 *
 * <p>For reference, the 7Z specification is available here:
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
                attr.put(FileAttributes.ARCHIVE, Archive.SEVENZ);
                attr.put(FileAttributes.SIZE, entry.getSize());



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

                FileType type;
                if(entry.isAntiItem())
                {
                    type = FileType.WHITEOUT;
                }
                else if(entry.isDirectory())
                {
                    type = FileType.DIRECTORY;
                }
                else
                {
                    type = FileType.REGULAR_FILE;
                }


                if(entry.getHasWindowsAttributes())
                {
                    // var flags = entry.getWindowsAttributes();
                    // attr.put(FileAttributes.DOS_READONLY, (flags & 0x1) != 0);
                    // attr.put(FileAttributes.DOS_HIDDEN,   (flags & 0x2) != 0);
                    // attr.put(FileAttributes.DOS_ARCHIVE,  (flags & 0x20) != 0);

                    var attrField = entry.getWindowsAttributes();
                    attr.put(FileAttributes.DOS, DosAttributes.forAttrField(attrField));

                    var mode = attrField >> 16;
                    if(mode != 0)
                    {
                        attr.put(FileAttributes.UNIX_PERMISSIONS, UnixPermissions.forMode(mode));

                        var unixFileType = FileType.forMode(mode);
                        if(type == FileType.REGULAR_FILE && unixFileType != FileType.UNKNOWN)
                        {
                            type = unixFileType;
                        }
                    }
                }

                attr.put(FileAttributes.TYPE, type);


                // TODO (once supported): retrieve UNIX mode (permissions and file type)

                operation.filterFile(displayPath.resolve(entryPath),
                                     () -> zipFile.getInputStream(entry),
                                     attr);
            }
        }
    }
}

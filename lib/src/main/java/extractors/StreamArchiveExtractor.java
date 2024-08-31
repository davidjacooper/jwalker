package au.djac.jwalker.extractors;
import au.djac.jwalker.*;
import au.djac.jwalker.attr.*;

import org.apache.commons.compress.archivers.*;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.arj.ArjArchiveEntry;
import org.apache.commons.compress.archivers.cpio.*;
import org.apache.commons.compress.archivers.dump.DumpArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;

/**
 * Extracts various archive formats using the Apache Commons Compress ArchiveInputStream class.
 * These include "tar" most importantly, but also "ar" ("a"), "arj", "cpio" and "dump".
 *
 * The non-tar formats are probably of marginal value, as they tend not to be used anymore for
 * general-purpose archiving. Also "arj" can only be read in uncompressed form by Commons Compress.
 *
 * For tar, this class only handles the actual uncompressed tar format. For tar files that have
 * been compressed with gzip, bzip2, etc., SingleFileDecompressor will apply first to decompress
 * them.
 *
 * For reference, the TAR standard is available here:
 * https://www.gnu.org/software/tar/manual/html_node/Standard.html
 */
public class StreamArchiveExtractor extends ArchiveExtractor
{
    private static final Logger log = LoggerFactory.getLogger(StreamArchiveExtractor.class);

    private static final Map<String,String> EXTENSION_MAP = Map.of(
        "a",    ArchiveStreamFactory.AR,
        "ar",   ArchiveStreamFactory.AR,
        "arj",  ArchiveStreamFactory.ARJ,
        "cpio", ArchiveStreamFactory.CPIO,
        "dump", ArchiveStreamFactory.DUMP, // Not sure how official the '.dump' extension is.
        "tar",  ArchiveStreamFactory.TAR); // Compressed tars (e.g., tar.gz) are first handled by
                                           // SingleFileDecompressor.

    private static final Map<DumpArchiveEntry.TYPE,FileType> DUMP_TYPE_MAP
        = new EnumMap<>(DumpArchiveEntry.TYPE.class);
    static
    {
        DUMP_TYPE_MAP.put(DumpArchiveEntry.TYPE.BLKDEV,    FileType.BLOCK_DEVICE);
        DUMP_TYPE_MAP.put(DumpArchiveEntry.TYPE.CHRDEV,    FileType.CHARACTER_DEVICE);
        DUMP_TYPE_MAP.put(DumpArchiveEntry.TYPE.DIRECTORY, FileType.DIRECTORY);
        DUMP_TYPE_MAP.put(DumpArchiveEntry.TYPE.FIFO,      FileType.FIFO);
        DUMP_TYPE_MAP.put(DumpArchiveEntry.TYPE.FILE,      FileType.REGULAR_FILE);
        DUMP_TYPE_MAP.put(DumpArchiveEntry.TYPE.LINK,      FileType.SYMBOLIC_LINK);
        DUMP_TYPE_MAP.put(DumpArchiveEntry.TYPE.SOCKET,    FileType.SOCKET);
        DUMP_TYPE_MAP.put(DumpArchiveEntry.TYPE.UNKNOWN,   null);
        DUMP_TYPE_MAP.put(DumpArchiveEntry.TYPE.WHITEOUT,  FileType.WHITEOUT);
    }

    @Override
    public Set<String> getFileExtensions()
    {
        return EXTENSION_MAP.keySet();
    }

    @Override
    public FileType getModifiedFileType()
    {
        return FileType.ARCHIVE;
    }

    @Override
    public void extract(JWalkerOperation operation,
                        String extension,
                        Path fsPath, //unused
                        Path displayPath,
                        JWalker.InputSupplier input,
                        FileAttributes archiveAttr)
        throws ArchiveSkipException
    {
        log.debug("Reading streamed archive '{}'", displayPath);

        String archiver = EXTENSION_MAP.get(extension.toLowerCase());
        if(archiver == null)
        {
            throw new IllegalArgumentException(
                "StreamArchiveExtractor cannot handle the file extension '" + extension + "'");
        }

        try(ArchiveInputStream ais =
            ArchiveStreamFactory.DEFAULT.createArchiveInputStream(
                archiver,
                new BufferedInputStream(input.get())))
        {
            while(true)
            {
                ArchiveEntry entry = ais.getNextEntry();
                if(entry == null) { break; }

                var entryPath = Path.of("", entry.getName().split(ARCHIVE_DIRECTORY_SEPARATOR));
                log.debug("File in archive: {}", entryPath);

                var attr = new FileAttributes();
                var type = FileType.REGULAR_FILE;

                attr.put(FileAttributes.LAST_MODIFIED_TIME, FileTime.from(entry.getLastModifiedDate().toInstant()));
                attr.put(FileAttributes.SIZE,               entry.getSize());

                if(entry instanceof ArArchiveEntry)
                {
                    // https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/ar/ArArchiveEntry.html
                    var arEntry = (ArArchiveEntry) entry;
                    attr.put(FileAttributes.IN_ARCHIVE,  Archive.AR);
                    attr.put(FileAttributes.GROUP_ID, (long)arEntry.getGroupId());
                    attr.put(FileAttributes.USER_ID,  (long)arEntry.getUserId());

                    int mode = arEntry.getMode();
                    attr.put(FileAttributes.UNIX_PERMISSIONS, UnixPermissions.forMode(mode));


                    if(arEntry.isDirectory())
                    {
                        type = FileType.DIRECTORY;
                    }
                    else
                    {
                        type = FileType.forMode(mode);
                    }
                }
                else if(entry instanceof ArjArchiveEntry)
                {
                    // https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/arj/ArjArchiveEntry.html
                    var arjEntry = (ArjArchiveEntry) entry;
                    attr.put(FileAttributes.IN_ARCHIVE, Archive.ARJ);
                    attr.put(FileAttributes.ARJ_HOST_OS, ArjHostOS.forCode(arjEntry.getHostOs()));

                    if(arjEntry.isHostOsUnix())
                    {
                        attr.put(FileAttributes.UNIX_PERMISSIONS,
                                 UnixPermissions.forMode(arjEntry.getUnixMode()));
                    }

                    if(arjEntry.isDirectory())
                    {
                        type = FileType.DIRECTORY;
                    }

                    // Note: ARJ does not apparently use the upper 4 bits of the mode for the file
                    // type, or at least not in the conventional UNIX fashion.
                    // => _Don't_ call FileType.forMode() to find the file type.
                }
                else if(entry instanceof CpioArchiveEntry)
                {
                    // https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/cpio/CpioArchiveEntry.html
                    var cpioEntry = (CpioArchiveEntry) entry;

                    attr.put(FileAttributes.IN_ARCHIVE,  Archive.CPIO);
                    attr.put(FileAttributes.GROUP_ID, cpioEntry.getGID());
                    attr.put(FileAttributes.USER_ID,  cpioEntry.getUID());

                    // The .cpio format's 'mode' field has bits representing the file type.
                    //attr.put(FileAttributes.MODE, (int)(cpioEntry.getMode() & ~CpioConstants.S_IFMT));
                    attr.put(FileAttributes.UNIX_PERMISSIONS,
                             UnixPermissions.forMode((int)cpioEntry.getMode()));

                    if(cpioEntry.isRegularFile())
                    {
                        type = FileType.REGULAR_FILE;
                    }
                    else if(cpioEntry.isDirectory())
                    {
                        type = FileType.DIRECTORY;
                    }
                    else if(cpioEntry.isSymbolicLink())
                    {
                        type = FileType.SYMBOLIC_LINK;
                    }
                    else if(cpioEntry.isBlockDevice())
                    {
                        type = FileType.BLOCK_DEVICE;
                    }
                    else if(cpioEntry.isCharacterDevice())
                    {
                        type = FileType.CHARACTER_DEVICE;
                    }
                    else if(cpioEntry.isNetwork())
                    {
                        type = FileType.NETWORK;
                    }
                    else if(cpioEntry.isPipe())
                    {
                        type = FileType.FIFO;
                    }
                    else if(cpioEntry.isSocket())
                    {
                        type = FileType.SOCKET;
                    }
                    else
                    {
                        type = FileType.UNKNOWN;
                    }

                    // TODO: other technical metadata?
                }
                else if(entry instanceof DumpArchiveEntry)
                {
                    // https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/dump/DumpArchiveEntry.html
                    var dumpEntry = (DumpArchiveEntry) entry;

                    attr.put(FileAttributes.IN_ARCHIVE, Archive.DUMP);
                    var ctime = dumpEntry.getCreationTime();
                    if(ctime != null)
                    {
                        attr.put(FileAttributes.CREATION_TIME, FileTime.from(ctime.toInstant()));
                    }

                    var atime = dumpEntry.getAccessTime();
                    if(atime != null)
                    {
                        attr.put(FileAttributes.LAST_ACCESS_TIME, FileTime.from(atime.toInstant()));
                    }

                    attr.put(FileAttributes.GROUP_ID, (long)dumpEntry.getGroupId());
                    attr.put(FileAttributes.USER_ID,  (long)dumpEntry.getUserId());
                    attr.put(FileAttributes.UNIX_PERMISSIONS,
                             UnixPermissions.forMode(dumpEntry.getMode()));

                    type = DUMP_TYPE_MAP.get(dumpEntry.getType());

                    // Other metadata available, including UNIX permissions and file type.
                }
                else if(entry instanceof TarArchiveEntry)
                {
                    // https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/tar/TarArchiveEntry.html

                    var tarEntry = (TarArchiveEntry) entry;

                    attr.put(FileAttributes.IN_ARCHIVE,       Archive.TAR);
                    attr.put(FileAttributes.GROUP_ID,         tarEntry.getLongGroupId());
                    attr.put(FileAttributes.GROUP_NAME,       tarEntry.getGroupName());
                    attr.put(FileAttributes.USER_ID,          tarEntry.getLongUserId());
                    attr.put(FileAttributes.USER_NAME,        tarEntry.getUserName());
                    attr.put(FileAttributes.CREATION_TIME,    tarEntry.getCreationTime());
                    attr.put(FileAttributes.LAST_ACCESS_TIME, tarEntry.getLastAccessTime());

                    attr.put(FileAttributes.UNIX_PERMISSIONS,
                             UnixPermissions.forMode(tarEntry.getMode()));

                    if(tarEntry.isDirectory())
                    {
                        type = FileType.DIRECTORY;
                    }
                    else if(tarEntry.isSymbolicLink())
                    {
                        type = FileType.SYMBOLIC_LINK;
                    }
                    else if(tarEntry.isBlockDevice())
                    {
                        type = FileType.BLOCK_DEVICE;
                    }
                    else if(tarEntry.isCharacterDevice())
                    {
                        type = FileType.CHARACTER_DEVICE;
                    }
                    else if(tarEntry.isFIFO())
                    {
                        type = FileType.FIFO;
                    }
                    else if(tarEntry.isLink())
                    {
                        type = FileType.HARD_LINK;
                    }
                    else if(tarEntry.isFile())
                    {
                        // isFile() seems to be true for any non-directory entry.
                        type = FileType.REGULAR_FILE;
                    }
                    else
                    {
                        type = FileType.UNKNOWN;
                    }
                }
                else
                {
                    // Theoretically not possible, but just in case...
                    attr.put(FileAttributes.IN_ARCHIVE, Archive.UNKNOWN);
                }

                attr.put(FileAttributes.TYPE, type);

                JWalker.InputSupplier entryInput;
                if(ais.canReadEntryData(entry))
                {
                    // Provide an uncloseable FilterInputStream to the client. The same stream is
                    // reused for subsequent entries, so if actually closed by the client, problems
                    // will ensue.
                    //
                    // (When we previously just passed 'ais' directly to the client, we'd get a
                    // "stream closed" IOException when one streaming archive was nested directly
                    // within another, once the inner archive was finished.)

                    entryInput = () -> new FilterInputStream(ais)
                    {
                        @Override public void close() {} // Disable
                    };
                }
                else
                {
                    log.warn("Couldn't read '{}' from archive '{}'", entryPath, displayPath);
                    entryInput = () -> {
                        throw new IOException(String.format(
                            "Couldn't read archive entry '%s' from archive file '%s'",
                            entryPath, displayPath));
                    };
                }

                operation.filterFile(displayPath.resolve(entryPath), entryInput, attr);
            }
        }
        catch(ArchiveException | IOException e)
        {
            operation.handleError(
                displayPath,
                archiveAttr,
                "Could not extract archive '" + displayPath + "': " + e.getMessage(),
                e);
            throw new ArchiveSkipException(e);
        }
    }
}

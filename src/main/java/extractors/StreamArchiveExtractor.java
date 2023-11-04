package au.djac.jwalker.extractors;
import au.djac.jwalker.*;

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

    private static final Map<DumpArchiveEntry.TYPE,FileAttributes.Type> DUMP_TYPE_MAP
        = new EnumMap<>(DumpArchiveEntry.TYPE.class);
    static
    {
        DUMP_TYPE_MAP.put(DumpArchiveEntry.TYPE.BLKDEV,    FileAttributes.Type.BLOCK_DEVICE);
        DUMP_TYPE_MAP.put(DumpArchiveEntry.TYPE.CHRDEV,    FileAttributes.Type.CHARACTER_DEVICE);
        DUMP_TYPE_MAP.put(DumpArchiveEntry.TYPE.DIRECTORY, FileAttributes.Type.DIRECTORY);
        DUMP_TYPE_MAP.put(DumpArchiveEntry.TYPE.FIFO,      FileAttributes.Type.FIFO);
        DUMP_TYPE_MAP.put(DumpArchiveEntry.TYPE.FILE,      FileAttributes.Type.REGULAR_FILE);
        DUMP_TYPE_MAP.put(DumpArchiveEntry.TYPE.LINK,      FileAttributes.Type.SYMBOLIC_LINK);
        DUMP_TYPE_MAP.put(DumpArchiveEntry.TYPE.SOCKET,    FileAttributes.Type.SOCKET);
        DUMP_TYPE_MAP.put(DumpArchiveEntry.TYPE.UNKNOWN,   null);
        DUMP_TYPE_MAP.put(DumpArchiveEntry.TYPE.WHITEOUT,  FileAttributes.Type.WHITEOUT);
    }

    @Override
    public Set<String> getFileExtensions()
    {
        return EXTENSION_MAP.keySet();
    }

    @Override
    public FileAttributes.Type getModifiedFileType()
    {
        return FileAttributes.Type.ARCHIVE;
    }

    @Override
    public void extract(JWalkerOperation operation,
                        String extension,
                        Path fsPath, //unused
                        Path displayPath,
                        JWalker.InputSupplier input,
                        FileAttributes archiveAttr) // unused
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
            ArchiveEntry entry;
            while((entry = ais.getNextEntry()) != null)
            {
                if(!entry.isDirectory())
                {
                    if(!ais.canReadEntryData(entry))
                    {
                        log.warn("Couldn't read entry '{}' from archive '{}'", displayPath, entry);
                        operation.error(
                            String.format(
                                "Couldn't read archive entry '%s' from archive file '%s'",
                                entry.getName(), displayPath),
                            null
                        );
                    }

                    var entryPath = Path.of("", entry.getName().split(ARCHIVE_DIRECTORY_SEPARATOR));
                    // var metadata = new HashMap<String,String>();
                    log.debug("File in archive: {}", entryPath);


                    var attr = new FileAttributes();
                    var type = FileAttributes.Type.REGULAR_FILE;

                    attr.put(FileAttributes.LAST_MODIFIED_TIME, FileTime.from(entry.getLastModifiedDate().toInstant()));
                    attr.put(FileAttributes.SIZE,               entry.getSize());

                    if(entry instanceof ArArchiveEntry)
                    {
                        // https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/ar/ArArchiveEntry.html
                        var arEntry = (ArArchiveEntry) entry;
                        attr.put(FileAttributes.GROUP_ID, (long)arEntry.getGroupId());
                        attr.put(FileAttributes.USER_ID,  (long)arEntry.getUserId());
                        attr.put(FileAttributes.MODE,     arEntry.getMode());
                        if(arEntry.isDirectory())
                        {
                            type = FileAttributes.Type.DIRECTORY;
                        }
                    }
                    else if(entry instanceof ArjArchiveEntry)
                    {
                        // https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/arj/ArjArchiveEntry.html
                        var arjEntry = (ArjArchiveEntry) entry;
                        attr.put(FileAttributes.ARJ_PLATFORM_CODE, arjEntry.getHostOs());
                        if(arjEntry.isHostOsUnix())
                        {
                            attr.put(FileAttributes.MODE, arjEntry.getUnixMode());
                        }
                        if(arjEntry.isDirectory())
                        {
                            type = FileAttributes.Type.DIRECTORY;
                        }
                    }
                    else if(entry instanceof CpioArchiveEntry)
                    {
                        // https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/cpio/CpioArchiveEntry.html
                        var cpioEntry = (CpioArchiveEntry) entry;

                        attr.put(FileAttributes.GROUP_ID, cpioEntry.getGID());
                        attr.put(FileAttributes.USER_ID,  cpioEntry.getUID());

                        // The .cpio format's 'mode' field has bits representing the file type.
                        attr.put(FileAttributes.MODE, (int)(cpioEntry.getMode() & ~CpioConstants.S_IFMT));

                        if(cpioEntry.isRegularFile())
                        {
                            type = FileAttributes.Type.REGULAR_FILE;
                        }
                        else if(cpioEntry.isDirectory())
                        {
                            type = FileAttributes.Type.DIRECTORY;
                        }
                        else if(cpioEntry.isSymbolicLink())
                        {
                            type = FileAttributes.Type.SYMBOLIC_LINK;
                        }
                        else if(cpioEntry.isBlockDevice())
                        {
                            type = FileAttributes.Type.BLOCK_DEVICE;
                        }
                        else if(cpioEntry.isCharacterDevice())
                        {
                            type = FileAttributes.Type.CHARACTER_DEVICE;
                        }
                        else if(cpioEntry.isNetwork())
                        {
                            type = FileAttributes.Type.NETWORK;
                        }
                        else if(cpioEntry.isPipe())
                        {
                            type = FileAttributes.Type.FIFO;
                        }
                        else if(cpioEntry.isSocket())
                        {
                            type = FileAttributes.Type.SOCKET;
                        }
                        else
                        {
                            type = null;
                        }

                        // TODO: other technical metadata?
                    }
                    else if(entry instanceof DumpArchiveEntry)
                    {
                        // https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/dump/DumpArchiveEntry.html
                        var dumpEntry = (DumpArchiveEntry) entry;

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
                        attr.put(FileAttributes.MODE,     dumpEntry.getMode());

                        type = DUMP_TYPE_MAP.get(dumpEntry.getType());

                        // Other metadata available, including UNIX permissions and file type.
                    }
                    else if(entry instanceof TarArchiveEntry)
                    {
                        // https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/tar/TarArchiveEntry.html

                        var tarEntry = (TarArchiveEntry) entry;

                        attr.put(FileAttributes.GROUP_ID,         tarEntry.getLongGroupId());
                        attr.put(FileAttributes.GROUP_NAME,       tarEntry.getGroupName());
                        attr.put(FileAttributes.USER_ID,          tarEntry.getLongUserId());
                        attr.put(FileAttributes.USER_NAME,        tarEntry.getUserName());
                        attr.put(FileAttributes.MODE,             tarEntry.getMode());
                        attr.put(FileAttributes.CREATION_TIME,    tarEntry.getCreationTime());
                        attr.put(FileAttributes.LAST_ACCESS_TIME, tarEntry.getLastAccessTime());

                        if(tarEntry.isFile())
                        {
                            type = FileAttributes.Type.REGULAR_FILE;
                        }
                        else if(tarEntry.isDirectory())
                        {
                            type = FileAttributes.Type.DIRECTORY;
                        }
                        else if(tarEntry.isSymbolicLink()) // "isLink()"??
                        {
                            type = FileAttributes.Type.SYMBOLIC_LINK;
                        }
                        else if(tarEntry.isBlockDevice())
                        {
                            type = FileAttributes.Type.BLOCK_DEVICE;
                        }
                        else if(tarEntry.isCharacterDevice())
                        {
                            type = FileAttributes.Type.CHARACTER_DEVICE;
                        }
                        else if(tarEntry.isFIFO())
                        {
                            type = FileAttributes.Type.FIFO;
                        }
                        else if(tarEntry.isLink())
                        {
                            type = FileAttributes.Type.HARD_LINK;
                        }
                        else
                        {
                            type = null;
                        }
                    }

                    attr.put(FileAttributes.TYPE, type);

                    operation.filterFile(displayPath.resolve(entryPath),
                                         () -> ais,
                                         attr);
                }
            }
        }
        catch(ArchiveException | IOException e)
        {
            operation.error(
                "Could not extract archive '" + displayPath + "': " + e.getMessage(),
                e);
            throw new ArchiveSkipException(e);
        }
    }
}

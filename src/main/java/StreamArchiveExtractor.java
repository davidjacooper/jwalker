package au.djac.jdirscanner;

import org.apache.commons.compress.archivers.*;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.arj.ArjArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.dump.DumpArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Extracts various archive formats using the Apache Commons Compress ArchiveInputStream class.
 * These include "tar" most importantly, but also "ar" ("a"), "arj", "cpio" and "dump".
 *
 * The non-tar formats are probably of marginal value, as they tend not to be used anymore for
 * general-purpose archiving. Also:
 * - "ar" does not support directories.
 * - "arj" can only be read in uncompressed form by Commons Compress.
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

    private final Map<String,String> extensionMap = Map.of(
        "a",    ArchiveStreamFactory.AR,
        "ar",   ArchiveStreamFactory.AR,
        "arj",  ArchiveStreamFactory.ARJ,
        "cpio", ArchiveStreamFactory.CPIO,
        "dump", ArchiveStreamFactory.DUMP, // Not sure how official the '.dump' extension is.
        "tar",  ArchiveStreamFactory.TAR); // Compressed tars (e.g., tar.gz) are first handled by
                                           // SingleFileDecompressor.

    @Override
    public Set<String> getFileExtensions()
    {
        return extensionMap.keySet();
    }

    @Override
    public void extract(JDirScanner dirScanner,
                        String extension,
                        Path fsPath, //unused
                        Path displayPath,
                        InputSupplier input,
                        Map<String,String> archiveMetadata) // unused
        throws ArchiveSkipException
    {
        log.debug("Reading streamed archive '{}'", displayPath);

        String archiver = extensionMap.get(extension.toLowerCase());
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
                        dirScanner.error(
                            String.format(
                                "Couldn't read archive entry '%s' from archive file '%s'",
                                entry.getName(), displayPath),
                            null
                        );
                    }

                    var entryPath = Path.of("", entry.getName().split(ARCHIVE_DIRECTORY_SEPARATOR));
                    var metadata = new HashMap<String,String>();
                    log.debug("File in archive: {}", entryPath);

                    // Modification time is defined in ArchiveEntry. All other useful metadata is
                    // defined in subclasses.
                    var mtime = entry.getLastModifiedDate();
                    if(mtime != null)
                    {
                        metadata.put(Attributes.MODIFIED_TIME, mtime.toInstant().toString());
                    }

                    if(entry instanceof ArArchiveEntry)
                    {
                        var arEntry = (ArArchiveEntry) entry;

                        metadata.put(Attributes.GROUP_ID, "" + arEntry.getGroupId());
                        metadata.put(Attributes.USER_ID,  "" + arEntry.getUserId());
                    }
                    else if(entry instanceof ArjArchiveEntry)
                    {
                        var arjEntry = (ArjArchiveEntry) entry;

                        int arjPlatform = arjEntry.getHostOs();
                        if(arjPlatform >= 0 && arjPlatform < Attributes.ARJ_PLATFORM_NAMES.length)
                        {
                            metadata.put(Attributes.ARJ_PLATFORM,
                                         Attributes.ARJ_PLATFORM_NAMES[arjPlatform]);
                        }

                        // Other metadata includes the 'mode' and 'UNIX mode', both ints. I'm not sure
                        // how to interpret them.
                    }
                    else if(entry instanceof CpioArchiveEntry)
                    {
                        var cpioEntry = (CpioArchiveEntry) entry;

                        metadata.put(Attributes.GROUP_ID, "" + cpioEntry.getGID());
                        metadata.put(Attributes.USER_ID,  "" + cpioEntry.getUID());

                        // Other technical metadata available, including for entries that are symlinks,
                        // block devices, character devices, pipes and sockets.
                    }
                    else if(entry instanceof DumpArchiveEntry)
                    {
                        var dumpEntry = (DumpArchiveEntry) entry;

                        var atime = dumpEntry.getAccessTime();
                        if(atime != null)
                        {
                            metadata.put(Attributes.ACCESS_TIME, atime.toInstant().toString());
                        }

                        var ctime = dumpEntry.getCreationTime();
                        if(atime != null)
                        {
                            metadata.put(Attributes.CREATION_TIME, ctime.toInstant().toString());
                        }

                        metadata.put(Attributes.GROUP_ID, "" + dumpEntry.getGroupId());
                        metadata.put(Attributes.USER_ID,  "" + dumpEntry.getUserId());

                        // Other metadata available, including UNIX permissions and file type.
                    }
                    else if(entry instanceof TarArchiveEntry)
                    {
                        var tarEntry = (TarArchiveEntry) entry;

                        metadata.put(Attributes.GROUP_ID, "" + tarEntry.getLongGroupId());
                        metadata.put(Attributes.USER_ID,  "" + tarEntry.getLongUserId());

                        var group = tarEntry.getGroupName();
                        if(group != null)
                        {
                            metadata.put(Attributes.GROUP_NAME, group);
                        }

                        var user = tarEntry.getUserName();
                        if(user != null)
                        {
                            metadata.put(Attributes.USER_NAME, user);
                        }
                    }

                    dirScanner.filterFile(displayPath.resolve(entryPath),
                                          () -> ais,
                                          metadata);
                }
            }
        }
        catch(ArchiveException | IOException e)
        {
            dirScanner.error(
                "Could not extract archive '" + displayPath + "': " + e.getMessage(),
                e);
            throw new ArchiveSkipException(e);
        }
    }
}

package au.djac.jdirscanner;

import org.apache.commons.compress.compressors.*;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Extracts the uncompressed form of a compressed file, using the Apache Commons Compress
 * CompressorInputStream class. It handles compression formats such as gzip, bzip2, etc., which
 * are used to compress *individual files*. Often they are used to compress .tar files, using
 * extensions like .tar.gz, .tgz, etc.
 *
 * However, we're not handling the tar format itself here, nor any other multifile archive formats
 * like ZIP, 7Z, RAR, etc., which employ their own compression mechanisms.
 */
public class SingleFileDecompressor extends ArchiveExtractor
{
    private static final Logger log = LoggerFactory.getLogger(SingleFileDecompressor.class);

    private static final String AUTODETECT = "*";

    private final Map<String,String> extensionMap;
    private final Set<String> combinedTarExtensions;

    public SingleFileDecompressor()
    {
        var m = new HashMap<String,String>();
        m.put("tb2",  CompressorStreamFactory.BZIP2);
        m.put("tbz",  CompressorStreamFactory.BZIP2);
        m.put("tbz2", CompressorStreamFactory.BZIP2);
        m.put("tz2",  CompressorStreamFactory.BZIP2);
        m.put("taz",  CompressorStreamFactory.GZIP);
        m.put("tgz",  CompressorStreamFactory.GZIP);
        m.put("tlz",  CompressorStreamFactory.LZMA);
        m.put("txz",  CompressorStreamFactory.XZ);
        m.put("tz",   CompressorStreamFactory.Z);
        m.put("taZ",  CompressorStreamFactory.Z); // "taz" and "taZ" are different!
        m.put("tzst", CompressorStreamFactory.ZSTANDARD);

        // All the above extensions are shorthand for ".tar.*". The ones below are not.
        combinedTarExtensions = new HashSet<>(m.keySet());

        m.put("br",   CompressorStreamFactory.BROTLI);
        m.put("bz2",  CompressorStreamFactory.BZIP2);
        m.put("gz",   CompressorStreamFactory.GZIP);
        m.put("lzma", CompressorStreamFactory.LZMA);
        m.put("xz",   CompressorStreamFactory.XZ);
        m.put("z",    CompressorStreamFactory.Z);
        m.put("zst",  CompressorStreamFactory.ZSTANDARD);

        // For lz4 and snappy, there are both 'framed' and 'block' versions, so we need
        // auto-detection.
        m.put("lz4",    AUTODETECT);

        // Also, snappy can't make its mind up on the appropriate file extension. :-P
        // snzip -h (from https://github.com/kubo/snzip) lists the following.
        m.put("snappy", AUTODETECT);
        m.put("snz",    AUTODETECT);
        m.put("sz",     AUTODETECT);

        // Lzip and Lzop are compression formats that Apache Commons Compress does not apparently
        // support, but we'll try to auto-detect them anyway. This way, the user will know... or
        // it will just magically work!
        m.put("lz",   AUTODETECT);
        m.put("lzo",  AUTODETECT);

        extensionMap = m;
    }

    @Override
    public Set<String> getFileExtensions()
    {
        return extensionMap.keySet();
    }


    @Override
    public void extract(JDirScanner dirScanner,
                        String extension,
                        Path fsPath,
                        Path displayPath,
                        InputSupplier input,
                        Map<String,String> archiveMetadata) throws ArchiveSkipException
    {
        log.debug("Decompressing file '{}'", displayPath);
        try
        {
            String lExtension = extension.toLowerCase();
            String compressor = extensionMap.get(extension);
            if(compressor == null)
            {
                compressor = extensionMap.get(lExtension);
                if(compressor == null)
                {
                    throw new IllegalArgumentException(
                        "SingleFileDecompressor cannot handle the file extension '" + extension + "'");
                }
            }

            // There's no explicit filename 'within' the compressed file; instead, we deduce the
            // correct name based on the original name.
            String entryName = displayPath.getName(displayPath.getNameCount() - 1).toString();

            // Strip extension
            entryName = entryName.substring(0, entryName.lastIndexOf('.'));

            // Possibly re-add .tar extension
            if(combinedTarExtensions.contains(lExtension))
            {
                entryName += ".tar";
            }

            var factory = CompressorStreamFactory.getSingleton();
            var bufIn = new BufferedInputStream(input.get());

            InputStream inputStream;
            if(compressor.equals(AUTODETECT))
            {
                inputStream = factory.createCompressorInputStream(bufIn);
            }
            else
            {
                inputStream = factory.createCompressorInputStream(compressor, bufIn);
            }

            dirScanner.filterFile(
                // No filesystem path available.
                null,

                // Uncompressed file's matchPath reflects its uncompressed nature.
                displayPath.resolve(entryName), // matchPath

                // Uncompressed file inherits the same displayPath as the compressed file.
                displayPath,

                () -> inputStream,

                // Inherit the metadata of the compressed file. (These compression schemes don't
                // really add any useful metadata of their own.)
                archiveMetadata);
        }
        catch(CompressorException | IOException e)
        {
            dirScanner.error(
                "Could not decompress file '" + displayPath + "': " + e.getMessage(),
                e);
            throw new ArchiveSkipException(e);
        }
    }
}

package au.djac.jwalker.extractors;
import au.djac.jwalker.*;

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

    private final Set<String> combinedTarExtensions;
    private final Map<String,String> extensionMap;
    private final Map<String,FileAttributes.Archive> typeMap;

    public SingleFileDecompressor()
    {
        extensionMap = new HashMap<>();
        extensionMap.put("tb2",  CompressorStreamFactory.BZIP2);
        extensionMap.put("tbz",  CompressorStreamFactory.BZIP2);
        extensionMap.put("tbz2", CompressorStreamFactory.BZIP2);
        extensionMap.put("tz2",  CompressorStreamFactory.BZIP2);
        extensionMap.put("taz",  CompressorStreamFactory.GZIP);
        extensionMap.put("tgz",  CompressorStreamFactory.GZIP);
        extensionMap.put("tlz",  CompressorStreamFactory.LZMA);
        extensionMap.put("txz",  CompressorStreamFactory.XZ);
        extensionMap.put("tz",   CompressorStreamFactory.Z);
        extensionMap.put("taZ",  CompressorStreamFactory.Z); // "taz" and "taZ" are different!
        extensionMap.put("tzst", CompressorStreamFactory.ZSTANDARD);

        // All the above extensions are shorthand for ".tar.*". The ones below are not.
        combinedTarExtensions = new HashSet<>(extensionMap.keySet());

        extensionMap.put("br",   CompressorStreamFactory.BROTLI);
        extensionMap.put("bz2",  CompressorStreamFactory.BZIP2);
        extensionMap.put("gz",   CompressorStreamFactory.GZIP);
        extensionMap.put("lzma", CompressorStreamFactory.LZMA);
        extensionMap.put("xz",   CompressorStreamFactory.XZ);
        extensionMap.put("z",    CompressorStreamFactory.Z);
        extensionMap.put("zst",  CompressorStreamFactory.ZSTANDARD);

        // For lz4 and snappy, there are both 'framed' and 'block' versions, so we need
        // auto-detection.
        extensionMap.put("lz4",    AUTODETECT);

        // Also, snappy can't make its mind up on the appropriate file extension. :-P
        // snzip -h (from https://github.com/kubo/snzip) lists the following.
        extensionMap.put("snappy", AUTODETECT);
        extensionMap.put("snz",    AUTODETECT);
        extensionMap.put("sz",     AUTODETECT);

        // Lzip and Lzop are compression formats that Apache Commons Compress does not apparently
        // support, but we'll try to auto-detect them anyway. This way, the user will know... or
        // it will just magically work!
        extensionMap.put("lz",   AUTODETECT);
        extensionMap.put("lzo",  AUTODETECT);

        typeMap = Map.of(
            CompressorStreamFactory.BROTLI,    FileAttributes.Archive.BROTLI,
            CompressorStreamFactory.BZIP2,     FileAttributes.Archive.BZIP2,
            CompressorStreamFactory.GZIP,      FileAttributes.Archive.GZIP,
            CompressorStreamFactory.LZMA,      FileAttributes.Archive.LZMA,
            CompressorStreamFactory.XZ,        FileAttributes.Archive.XZ,
            CompressorStreamFactory.Z,         FileAttributes.Archive.Z,
            CompressorStreamFactory.ZSTANDARD, FileAttributes.Archive.ZSTANDARD
        );
    }

    @Override
    public Set<String> getFileExtensions()
    {
        return extensionMap.keySet();
    }

    @Override
    public FileAttributes.Type getModifiedFileType()
    {
        return FileAttributes.Type.COMPRESSED_FILE;
    }

    @Override
    public void extract(JWalkerOperation operation,
                        String extension,
                        Path fsPath,
                        Path displayPath,
                        JWalker.InputSupplier input,
                        FileAttributes attr) throws ArchiveSkipException
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

            // Inherit the metadata of the compressed file, except for recording the compression
            // format, and changing its type to a regular file.
            var uncompressedAttr = attr.copy();
            uncompressedAttr.put(FileAttributes.ARCHIVE, typeMap.get(compressor));
            uncompressedAttr.put(FileAttributes.TYPE,    FileAttributes.Type.REGULAR_FILE);

            operation.filterFile(
                // No filesystem path available.
                null,

                // Uncompressed file's matchPath reflects its uncompressed nature.
                displayPath.resolve(entryName), // matchPath

                // Uncompressed file inherits the same displayPath as the compressed file.
                displayPath,

                () -> inputStream,
                uncompressedAttr);
        }
        catch(CompressorException | IOException e)
        {
            operation.error(
                displayPath,
                attr,
                "Could not decompress file '" + displayPath + "': " + e.getMessage(),
                e);
            throw new ArchiveSkipException(e);
        }
    }
}

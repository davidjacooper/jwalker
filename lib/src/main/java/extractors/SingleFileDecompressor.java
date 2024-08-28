package au.djac.jwalker.extractors;
import au.djac.jwalker.attr.*;
import au.djac.jwalker.*;

import org.apache.commons.compress.compressors.*;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.lz77support.AbstractLZ77CompressorInputStream;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
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
    private final Map<String,Archive> typeMap;

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

        extensionMap.put("br",      CompressorStreamFactory.BROTLI);
        extensionMap.put("bz2",     CompressorStreamFactory.BZIP2);
        extensionMap.put("gz",      CompressorStreamFactory.GZIP);
        extensionMap.put("lzma",    CompressorStreamFactory.LZMA);
        extensionMap.put("xz",      CompressorStreamFactory.XZ);
        extensionMap.put("z",       CompressorStreamFactory.Z);
        extensionMap.put("zst",     CompressorStreamFactory.ZSTANDARD);

        // For lz4 and snappy, there are both 'framed' and 'block' versions, so we need
        // auto-detection.
        extensionMap.put("lz4",    AUTODETECT);

        // Also, snappy can't make its mind up on the appropriate file extension. :-P
        // snzip -h (from https://github.com/kubo/snzip) lists the following.
        extensionMap.put("snappy", AUTODETECT);
        extensionMap.put("snz",    AUTODETECT);
        extensionMap.put("sz",     AUTODETECT);

        // Deflate and deflate64? (not typically appearing in a standalone file)
        extensionMap.put("deflate", AUTODETECT);
        // extensionMap.put("zz",      AUTODETECT);

        // Lzip and Lzop are compression formats that Apache Commons Compress does not apparently
        // support, but we'll try to auto-detect them anyway. This way, the user will know... or
        // it will just magically work!
        extensionMap.put("lz",   AUTODETECT);
        extensionMap.put("lzo",  AUTODETECT);

        typeMap = new HashMap<>();
        typeMap.put(CompressorStreamFactory.BROTLI,        Archive.BROTLI);
        typeMap.put(CompressorStreamFactory.BZIP2,         Archive.BZIP2);
        typeMap.put(CompressorStreamFactory.GZIP,          Archive.GZIP);
        typeMap.put(CompressorStreamFactory.LZ4_BLOCK,     Archive.LZ4_BLOCK);
        typeMap.put(CompressorStreamFactory.LZ4_FRAMED,    Archive.LZ4_FRAMED);
        typeMap.put(CompressorStreamFactory.LZMA,          Archive.LZMA);
        typeMap.put(CompressorStreamFactory.SNAPPY_FRAMED, Archive.SNAPPY_FRAMED);
        typeMap.put(CompressorStreamFactory.SNAPPY_RAW,    Archive.SNAPPY_RAW);
        typeMap.put(CompressorStreamFactory.XZ,            Archive.XZ);
        typeMap.put(CompressorStreamFactory.Z,             Archive.Z);
        typeMap.put(CompressorStreamFactory.ZSTANDARD,     Archive.ZSTANDARD);

        // Apache Commons also supports DEFLATE, DEFLATE64 and PACK200, but these don't seem to be
        // used as standalone files.

        // typeMap.put(CompressorStreamFactory.DEFLATE,       Archive.DEFLATE);
        // typeMap.put(CompressorStreamFactory.DEFLATE64,     Archive.DEFLATE64);
    }

    @Override
    public Set<String> getFileExtensions()
    {
        return extensionMap.keySet();
    }

    @Override
    public FileType getModifiedFileType()
    {
        return FileType.COMPRESSED_FILE;
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

            var factory = CompressorStreamFactory.getSingleton();
            var bufIn = new BufferedInputStream(input.get());
            var inputStream = AUTODETECT.equals(compressor)
                ? factory.createCompressorInputStream(bufIn)
                : factory.createCompressorInputStream(compressor, bufIn);

            // Inherit the metadata of the compressed file, except for recording the compression
            // format, and changing its type to a regular file, and deleting the size (because
            // we mostly can't retrieve the uncompressed size without buffering the entire
            // uncompressed content).
            var uncompressedAttr = attr.copy();
            uncompressedAttr.put(FileAttributes.IN_ARCHIVE, typeMap.get(compressor));
            uncompressedAttr.put(FileAttributes.TYPE, FileType.REGULAR_FILE);
            uncompressedAttr.put(FileAttributes.SIZE, null);

            String entryName = null;
            Path uncompressedMatchPath;
            Path uncompressedDisplayPath;

            // Most compression formats apparently have no interesting metadata of their own.
            // GZIP does, though (see http://www.zlib.org/rfc-gzip.html).
            if(inputStream instanceof GzipCompressorInputStream)
            {
                // https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/compressors/gzip/GzipCompressorInputStream.html
                var gzMetadata = ((GzipCompressorInputStream)inputStream).getMetaData();

                // GZIP stores times in seconds, but Commons Compress returns it in milliseconds.
                uncompressedAttr.put(FileAttributes.LAST_MODIFIED_TIME,
                                     FileTime.fromMillis(gzMetadata.getModificationTime()));

                uncompressedAttr.put(FileAttributes.GZIP_HOST_FS,
                                     GzipHostFS.forCode(gzMetadata.getOperatingSystem()));

                // Comments are optional, and should be null if absent.
                uncompressedAttr.put(FileAttributes.COMMENT, gzMetadata.getComment());

                // GZIPs can store an optional original filename; may be null.
                entryName = gzMetadata.getFilename();
            }
            else if(inputStream instanceof AbstractLZ77CompressorInputStream)
            {
                // https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/compressors/lz77support/AbstractLZ77CompressorInputStream.html
                // Used for LZ4 (block) and Snappy. Just in these cases, apparently we _can_
                // retrieve the uncompressed size.
                uncompressedAttr.put(
                    FileAttributes.SIZE,
                    (long)((AbstractLZ77CompressorInputStream)inputStream).getSize());
            }

            if(entryName == null)
            {
                // There's no explicit filename 'within' the compressed file; instead, we deduce the
                // correct name based on the original name.
                entryName = displayPath.getName(displayPath.getNameCount() - 1).toString();

                // Strip extension
                entryName = entryName.substring(0, entryName.lastIndexOf('.'));

                // Possibly re-add .tar extension
                if(combinedTarExtensions.contains(lExtension))
                {
                    entryName += ".tar";
                }
                uncompressedMatchPath = displayPath.resolve(entryName);
                uncompressedDisplayPath = displayPath;
            }
            else
            {
                uncompressedMatchPath = displayPath.resolve(entryName);
                uncompressedDisplayPath = uncompressedMatchPath;
            }

            operation.filterFile(
                null, // No filesystem path available.
                uncompressedMatchPath,
                uncompressedDisplayPath,
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

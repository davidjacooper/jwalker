package au.djac.jdirscanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Scans a directory for files, optionally recursing into arbitrarily-nested archives and retrieving
 * metadata. Client code supplies a location on the local filesystem, and callback to handle each
 * entry. JDirScanner will recurse through the directory tree at that location, and call the
 * callback for each file encountered, subject to inclusions, exclusions and archive extraction.
 *
 * Some moderately complex recursive calls take place here. Here's an outline of how it goes:
 *
 *   forEachFile()
 *   |
 *   |-- filterFile()
 *   |   |
 *   |   ... (see below)
 *   |or
 *   +-- walkTree()
 *       |
 *       +-- filterFile()
 *           |
 *           +-- ArchiveExtractor.extract()
 *           |   [various implementations]
 *           |   |
 *           |   |-- filterFile()
 *           |   |   |
 *           |   |   ... [recurse]
 *           |   |or
 *           |   +-- walkTree()
 *           |       |
 *           |       ... [recurse]
 *           |or
 *           +-- callback.call()
 */
public class JDirScanner
{
    private static final Logger log = LoggerFactory.getLogger(JDirScanner.class);

    private final JDirScannerOptions options;
    private FileCallback callback = null;

    public JDirScanner(JDirScannerOptions options)
    {
        this.options = options;
    }

    public JDirScanner()
    {
        this(new JDirScannerOptions());
    }

    void error(String msg, Exception ex)
    {
        // Called from various ArchiveExtractor subclasses, and within this class.
        log.error(msg, ex);
        options.errorPolicy().enact(msg, ex);
    }

    public void forEachFile(File fsRootPath, FileCallback callback)
    {
        forEachFile(fsRootPath.toPath(), callback);
    }

    public synchronized void forEachFile(Path fsRootPath, FileCallback callback)
    {
        this.callback = callback;
        if(Files.isDirectory(fsRootPath))
        {
            walkTree(fsRootPath, Path.of(""));
        }
        else
        {
            // Special case for when the user supplies a single file
            // representing a whole submission.
            //
            // Normally we discard the submission directory name from the path of each file
            // in that submission (for brevity). We can't really do that in this situation.

            var metadata = new HashMap<String,String>();
            putFsMetadata(fsRootPath, metadata);

            var displayPath = fsRootPath.getName(fsRootPath.getNameCount() - 1);
            filterFile(fsRootPath,
                       displayPath, // matchPath, equal to the displayPath here.
                       displayPath,
                       () -> Files.newInputStream(fsRootPath),
                       metadata);
        }
    }

    void walkTree(Path fsPath, Path displayPath)
    {
        try
        {
            Files.walkFileTree(
                fsPath,
                new SimpleFileVisitor<>()
                {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dirFsPath, BasicFileAttributes attrs)
                    {
                        // Only apply exclusions to directories
                        for(var matcher : options.exclusions())
                        {
                            if(matcher.matches(dirFsPath))
                            {
                                // Directory matches exclusion pattern; skip the entire branch.
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                        }
                        // Otherwise, descend into the directory.
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path fileFsPath, BasicFileAttributes attrs)
                    {
                        // Create the initial metadata container.
                        var metadata = new HashMap<String,String>();
                        putFsMetadata(fileFsPath, metadata);

                        // Note 2: to get a 'display path' for the file, we:
                        // (1) relativize() it against the root path, which strips the root path
                        //     component(s) off. These are assumed to be outside the scope of useful
                        //     info we want to store in the DB.
                        //
                        // (2) resolve() it against the root display path, which adds on those
                        //     component(s), if any.
                        var fileDisplayPath = displayPath.resolve(fsPath.relativize(fileFsPath));

                        filterFile(fileFsPath,
                                   fileDisplayPath, // matchPath, equal to the displayPath here.
                                   fileDisplayPath,
                                   () -> Files.newInputStream(fileFsPath),
                                   metadata);
                        return FileVisitResult.CONTINUE;
                    }
                }
            );
        }
        catch(IOException e)
        {
            error(String.format("Cannot traverse directory tree at '%s'", fsPath), e);
        }
    }

    private void putFsMetadata(Path path, Map<String,String> metadata)
    {
        // Note: extracting metadata from the user's filesystem is risky, because it potentially
        // conflates their own system's behaviour with the data they're trying to analyse.
        //
        // Here, we are recording the modification time, as this has a reasonable chance of being
        // informative.
        //
        // (If we wanted, we *could* get an instance of PosixFileAttributes, and store the group,
        // owner and permissions, but this will tend to come from the user's own system setup.)

        try
        {
            metadata.put(
                Attributes.MODIFIED_TIME,
                Files.getLastModifiedTime(path).toInstant().toString());
        }
        catch(IOException e)
        {
            error(String.format("Cannot get file attributes from '%s'", path), e);
        }
    }

    void filterFile(Path displayPath, InputSupplier input, Map<String,String> metadata)
    {
        filterFile(null, displayPath, displayPath, input, metadata);
    }

    /**
     * Inspects a given file, to see whether to include it directly, apply an extractor, or ignore
     * it.
     *
     * @param fsPath      The file's physical location (for random-access IO purposes), or null if
     *                    it's stored in an archive.
     * @param matchPath   Represents the file's 'true nature', for determining what to do with it;
     *                    i.e., which extractor to apply (if any), and whether to include it.
     * @param displayPath The way the file will be presented to the user. This is often equal to
     *                    matchPath, but not always. (If a decompressor has been applied,
     *                    displayPath may reflect the original compressed name, while matchPath the
     *                    decompressed name.)
     * @param input       An InputStream supplier, for streaming IO operations.
     * @param metadata    A container into which filesystem/archive metadata should be stored.
     */
    void filterFile(Path fsPath,
                    Path matchPath,
                    Path displayPath,
                    InputSupplier input,
                    Map<String,String> metadata)
    {
        var inclusions = options.inclusions();
        var exclusions = options.exclusions();

        log.debug("Filtering fsPath = '{}', matchPath = '{}', displayPath = '{}', exclusions = {}, inclusions = {}",
            fsPath, matchPath, displayPath, exclusions, inclusions);

        for(var matcher : exclusions)
        {
            if(matcher.matches(matchPath))
            {
                log.debug("Applying exclusion '{}'", matcher);
                // File matches exclusion pattern, so skip file. That is, don't call
                // the callback.
                return;
            }
        }

        if(options.recurseIntoArchives())
        {
            var pathStr = matchPath.toString();
            var extIndex = pathStr.lastIndexOf('.');
            if(extIndex != -1)
            {
                var ext = pathStr.substring(extIndex + 1);
                var extractor = options.extractorMap().get(ext.toLowerCase());
                if(extractor != null)
                {
                    try
                    {
                        extractor.extract(this, ext, fsPath, displayPath, input, metadata);
                        return; // Skip everything else, on the assumption that the extractor will
                                // have called filterFile() or walkTree() recursively as needed.
                    }
                    catch(ArchiveSkipException e)
                    {
                        // Extracting from archive failed; try treating it as an ordinary file.
                        log.debug("Skipping archive extraction for '{}'", displayPath);
                    }
                }
            }
        }

        for(var matcher : inclusions)
        {
            if(matcher.matches(matchPath))
            {
                log.debug("Applying inclusion '{}'", matcher);
                // File matches inclusion pattern, so call the callback.
                callback.call(displayPath, input, metadata);
                return;
            }
        }

        if(inclusions.isEmpty())
        {
            // The default is to include, if no inclusions have been specified, or to
            // exclude otherwise.
            log.debug("Including by default");
            callback.call(displayPath, input, metadata);
        }
        else
        {
            log.debug("Excluding by default");
        }
    }
}

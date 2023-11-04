package au.djac.jwalker;
import static au.djac.jwalker.JWalker.*;
import au.djac.jwalker.extractors.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Scans a directory for files, optionally recursing into arbitrarily-nested archives and retrieving
 * metadata. Client code supplies a location on the local filesystem, and callback to handle each
 * entry. JWalker will recurse through the directory tree at that location, and call the
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
 *           |
 *           +-- fileConsumer.accept()
 */
public class JWalkerOperation
{
    private static final Logger log = LoggerFactory.getLogger(JWalker.class);

    private final JWalker options;
    private final FileConsumer fileConsumer;
    private final ErrorHandler errorHandler;
    private final LinkOption[] linkOptions;

    private final HashSet<Path> excludedSubPaths = new HashSet<>();
    private final HashSet<Path> nonExcludedSubPaths = new HashSet<>();

    public JWalkerOperation(JWalker options,
                            FileConsumer fileConsumer,
                            ErrorHandler errorHandler)
    {
        this.options = options;
        this.fileConsumer = fileConsumer;
        this.errorHandler = errorHandler;
        this.linkOptions = options.followLinks() ? new LinkOption[] {}
                                                 : new LinkOption[] {LinkOption.NOFOLLOW_LINKS};
    }

    private FileAttributes extractAttr(Path fsPath, BasicFileAttributes basicAttr)
    {
        var attr = new FileAttributes();
        attr.put(FileAttributes.CREATION_TIME,      basicAttr.creationTime());
        attr.put(FileAttributes.LAST_ACCESS_TIME,   basicAttr.lastAccessTime());
        attr.put(FileAttributes.LAST_MODIFIED_TIME, basicAttr.lastModifiedTime());
        attr.put(FileAttributes.SIZE,               basicAttr.size());

        FileAttributes.Type type = null;
        if(basicAttr.isDirectory())
        {
            type = FileAttributes.Type.DIRECTORY;
        }
        else if(basicAttr.isRegularFile())
        {
            type = FileAttributes.Type.REGULAR_FILE;
        }
        else if(basicAttr.isSymbolicLink())
        {
            type = FileAttributes.Type.SYMBOLIC_LINK;
        }
        attr.put(FileAttributes.TYPE, type);

        if(fsPath != null && options.unixAttributes())
        {
            PosixFileAttributes posixAttr = null;
            try
            {
                posixAttr = Files.readAttributes(
                    fsPath, PosixFileAttributes.class, linkOptions);
            }
            catch(UnsupportedOperationException e)
            {
                log.atDebug().setCause(e).log("File '{}' does not support POSIX file attributes", fsPath);
            }
            catch(IOException e)
            {
                error(fsPath,
                      attr,
                      String.format("Could not read POSIX file attributes from '%s'", fsPath),
                      e);
            }

            if(posixAttr != null)
            {
                attr.put(FileAttributes.USER_NAME,  posixAttr.owner().getName());
                attr.put(FileAttributes.GROUP_NAME, posixAttr.group().getName());

                var permissions = posixAttr.permissions();
                int mode = 0;
                int bit = 0;
                for(var perm : FileAttributes.POSIX_FILE_PERMISSIONS)
                {
                    if(permissions.contains(perm))
                    {
                        mode |= (1 << bit);
                    }
                    bit++;
                }
                attr.put(FileAttributes.MODE, mode);
            }
        }

        // TODO?: we could extract extended attributes via Files.getAttributeView() and UserDefinedAttributeView.

        return attr;
    }

    public void walk(Path rootPath)
    {
        if(Files.isDirectory(rootPath))
        {
            walkTree(rootPath, Path.of(""), this::extractAttr);
        }
        else
        {
            // Special case for when the user supplies a single file
            // representing a whole submission.
            //
            // Normally we discard the submission directory name from the path of each file
            // in that submission (for brevity). We can't really do that in this situation.

            var displayPath = rootPath.getName(rootPath.getNameCount() - 1);

            FileAttributes attr;
            try
            {
                attr = extractAttr(
                    rootPath,
                    Files.readAttributes(rootPath, BasicFileAttributes.class, linkOptions));
            }
            catch(IOException e)
            {
                error(rootPath,
                      new FileAttributes(),
                      String.format("Could not read file attributes from '%s'", rootPath),
                      e);
                attr = new FileAttributes();
                attr.put(FileAttributes.TYPE, FileAttributes.Type.REGULAR_FILE);
            }

            filterFile(rootPath,
                       displayPath, // matchPath, equal to the displayPath here.
                       displayPath,
                       () -> Files.newInputStream(rootPath),
                       attr);
        }
    }

    public void error(Path path, FileAttributes attr, String msg, Exception ex)
    {
        // Called from various ArchiveExtractor subclasses, and within this class.
        log.error(msg, ex);
        errorHandler.error(path, attr, msg, ex);
    }

    public void walkTree(Path fsPath,
                         Path displayPath,
                         BiFunction<Path,BasicFileAttributes,FileAttributes> attrFn)
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
                        var fileDisplayPath = displayPath.resolve(fsPath.relativize(dirFsPath));

                        // Only apply exclusions to directories
                        for(var matcher : options.exclusions())
                        {
                            if(matcher.matches(dirFsPath))
                            {
                                // Directory matches exclusion pattern; skip the entire branch.
                                excludedSubPaths.add(fileDisplayPath);
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                        }
                        nonExcludedSubPaths.add(fileDisplayPath);

                        filterFile(dirFsPath,
                                   fileDisplayPath,
                                   fileDisplayPath,
                                   null,
                                   attrFn.apply(dirFsPath, attrs));

                        // Descend into the directory.
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path fileFsPath, BasicFileAttributes attrs)
                    {
                        // To get a 'display path' for the file, we:
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
                                   attrFn.apply(fileFsPath, attrs));

                        return FileVisitResult.CONTINUE;
                    }
                }
            );
        }
        catch(IOException e)
        {
            error(displayPath,
                  new FileAttributes(),
                  String.format("Cannot traverse directory tree at '%s'", fsPath),
                  e);
        }
    }

    public void filterFile(Path displayPath, InputSupplier input, FileAttributes attr)
    {
        filterFile(null, displayPath, displayPath, input, attr);
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
     * @param attr        The file's various attributes (depending on the features of its containing
     *                    filesystem/archive).
     */
    public void filterFile(Path fsPath,
                           Path matchPath,
                           Path displayPath,
                           InputSupplier input,
                           FileAttributes attr)
    {
        var inclusions = options.inclusions();
        var exclusions = options.exclusions();

        log.debug("Filtering fsPath = '{}', matchPath = '{}', displayPath = '{}', exclusions = {}, inclusions = {}",
            fsPath, matchPath, displayPath, exclusions, inclusions);

        // Check whether every subpath prefix has been excluded.
        //
        // (Files.walkFileTree() already prunes the search for excluded filesystem directories, but
        // the mechanisms for parsing _archives_ do not, because archives are not really stored
        // hierarchically.)
        //
        // While we're at it, we check the full path as well.

        for(int prefixSize = 1; prefixSize <= matchPath.getNameCount(); prefixSize++)
        {
            var subPath = matchPath.subpath(0, prefixSize);
            if(!nonExcludedSubPaths.contains(subPath))
            {
                var excluded = excludedSubPaths.contains(subPath);
                if(!excluded)
                {
                    for(var matcher : exclusions)
                    {
                        if(matcher.matches(subPath))
                        {
                            excluded = true;
                            excludedSubPaths.add(subPath);
                            break;
                        }
                    }
                }
                if(excluded)
                {
                    log.debug("Excluding '{}' because path '{}' has been excluded", matchPath, subPath);
                    return;
                }
                else
                {
                    nonExcludedSubPaths.add(subPath);
                }
            }
        }

        // for(var matcher : exclusions)
        // {
        //     if(matcher.matches(matchPath))
        //     {
        //         log.debug("Applying exclusion '{}'", matcher);
        //         // File matches exclusion pattern, so skip file. That is, don't call
        //         // the callback. If it's a compressed/archive file, then we won't recurse into it
        //         // _even if_ 'recurseIntoArchives' is true (consistent with directories).
        //         return;
        //     }
        // }

        var type = attr.get(FileAttributes.TYPE);
        ArchiveExtractor extractor = null;
        String ext = null;

        if(type == FileAttributes.Type.REGULAR_FILE)
        {
            var pathStr = matchPath.toString();
            var extIndex = pathStr.lastIndexOf('.');
            if(extIndex != -1)
            {
                ext = pathStr.substring(extIndex + 1);
                extractor = options.extractorMap().get(ext.toLowerCase());
                if(extractor != null)
                {
                    // Archive files are _not_ considered "REGULAR_FILE"s for our purposes. We change
                    // the type to either "ARCHIVE" or "COMPRESSED_FILE".
                    type = extractor.getModifiedFileType();
                    attr.put(FileAttributes.TYPE, type);
                }
            }
        }

        if(options.showFileType(type))
        {
            for(var matcher : inclusions)
            {
                if(matcher.matches(matchPath))
                {
                    log.debug("Applying inclusion '{}'", matcher);
                    // File matches inclusion pattern, so call the callback.
                    fileConsumer.accept(displayPath, input, attr);
                    break;
                }
            }

            if(inclusions.isEmpty())
            {
                // The default is to include, if no inclusions have been specified, or to
                // exclude otherwise.
                log.debug("Including by default");
                fileConsumer.accept(displayPath, input, attr);
            }
            else
            {
                log.debug("Excluding by default");
            }
        }

        if(extractor != null && options.recurseIntoArchives())
        {
            // Archives are recursed into even if they are not 'included' (consistent with
            // directories).

            try
            {
                extractor.extract(this, ext, fsPath, displayPath, input, attr);
                // The extractor will call filterFile() or walkTree() recursively as needed.
            }
            catch(ArchiveSkipException e)
            {
                // Extracting from archive failed; try treating it as an ordinary file.
                log.debug("Skipping archive extraction for '{}'", displayPath);
            }
        }
    }
}

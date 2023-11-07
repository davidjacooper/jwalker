package au.djac.jwalker;
import au.djac.jwalker.extractors.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Entry point into the JWalker library, providing various option-setting methods, and the
 * walk() method that performs the actual task.
 */
public class JWalker
{
    @FunctionalInterface
    public interface InputSupplier
    {
        InputStream get() throws IOException;
    }

    @FunctionalInterface
    public interface FileConsumer
    {
        public void accept(Path displayPath, InputSupplier input, FileAttributes attr);
    }

    @FunctionalInterface
    public interface ErrorHandler
    {
        public void error(Path displayPath, FileAttributes attr,
                          String message, Exception exception);
    }


    private int maxDepth = Integer.MAX_VALUE;
    private boolean recurseIntoArchives = true;
    private boolean followLinks = false;      // TBD: whether the follow FS symlinks. (We're not going to follow links within archives.)
    private boolean unixAttributes = true;

    private List<PathMatcher> inclusions = new ArrayList<>();
    private List<PathMatcher> exclusions = new ArrayList<>();
    private Set<FileAttributes.Type> fileTypes = null;
    private Set<ArchiveExtractor> extractors = null;
    private Map<String,ArchiveExtractor> extractorMap = null;

    public JWalker() {}

    /**
     * Traverses the file tree, beginning at a specified path, providing each directory entry
     * (subject to inclusion/exclusion criteria) to a given callback, and diverting any errors to
     * a given error handler.
     *
     * @param rootPath The top-most path at which to start walking. If this is a directory or
     * archive file, its contents will be traversed. If it is a regular file, it will be the sole
     * result of the 'traversal'.
     * @param fileConsumer Receives each file entry (subject to inclusion/exclusion criteria).
     * @param errorHandler Receives each error as it happens. The error handler can choose to
     * throw any subclass of {@link RuntimeException}, aborting the traversal, or not, in which case
     * the traversal will continue.
     */
    public void walk(Path rootPath, FileConsumer fileConsumer, ErrorHandler errorHandler)
    {
        new JWalkerOperation(this, fileConsumer, errorHandler).walkTree(rootPath);
    }

    /**
     * Traverses the file tree, beginning at a specified path, and providing each directory entry
     * (subject to inclusion/exclusion criteria) to a given callback.
     *
     * This is equivalent to the three-parameter version, where the error handler simply throws
     * {@link JWalkerException} on any error.
     *
     * @param rootPath The top-most path at which to start walking. If this is a directory or
     * archive file, its contents will be traversed. If it is a regular file, it will be the sole
     * result of the 'traversal'.
     * @param fileConsumer Receives each file entry (subject to inclusion/exclusion criteria).
     */
    public void walk(Path rootPath, FileConsumer fileConsumer)
    {
        walk(rootPath, fileConsumer, (path, attr, msg, ex) -> { throw new JWalkerException(msg, ex); });
    }

    // TBD
    public JWalker maxDepth(int d)
    {
        maxDepth = d;
        return this;
    }

    /**
     * Specifies whether to include the contents of archive files (true by default).
     *
     * @param b If true (the default), archive files are treated like directories. If false, they
     * are treated as regular files.
     */
    public JWalker recurseIntoArchives(boolean b)
    {
        recurseIntoArchives = b;
        return this;
    }

    public JWalker followLinks(boolean b)
    {
        followLinks = b;
        return this;
    }

    public JWalker unixAttributes(boolean b)
    {
        unixAttributes = b;
        return this;
    }

    private void initFileTypes()
    {
        if(this.fileTypes == null)
        {
            this.fileTypes = new HashSet<>();
        }
    }

    public JWalker fileTypes(FileAttributes.Type... newFileTypes)
    {
        initFileTypes();
        for(var t : newFileTypes)
        {
            this.fileTypes.add(t);
        }
        return this;
    }

    public JWalker fileTypes(Iterable<FileAttributes.Type> newFileTypes)
    {
        initFileTypes();
        newFileTypes.forEach(this.fileTypes::add);
        return this;
    }

    public Set<FileAttributes.Type> defaultFileTypes()
    {
        return Set.of(FileAttributes.Type.REGULAR_FILE);
    }

    private PathMatcher globMatcher(String globPattern)
    {
        var fs = FileSystems.getDefault();
        var fullPattern = "glob:{**" + fs.getSeparator() + ",}" + globPattern;
        var wrappedMatcher = fs.getPathMatcher(fullPattern);

        return new PathMatcher()
        {
            @Override
            public boolean matches(Path p)
            {
                return wrappedMatcher.matches(p);
            }

            @Override
            public String toString()
            {
                return fullPattern;
            }
        };
    }

    public JWalker include(String globPattern)
    {
        inclusions.add(globMatcher(globPattern));
        return this;
    }

    public JWalker include(PathMatcher matcher)
    {
        inclusions.add(matcher);
        return this;
    }

    public JWalker exclude(String globPattern)
    {
        exclusions.add(globMatcher(globPattern));
        return this;
    }

    public JWalker exclude(PathMatcher matcher)
    {
        exclusions.add(matcher);
        return this;
    }

    private void initExtractors()
    {
        if(this.extractors == null)
        {
            this.extractors = new HashSet<>();
        }
        extractorMap = null;
    }

    public JWalker extractWith(ArchiveExtractor... newExtractors)
    {
        initExtractors();
        for(var e : extractors)
        {
            this.extractors.add(e);
        }
        return this;
    }

    public JWalker extractWith(Iterable<ArchiveExtractor> newExtractors)
    {
        initExtractors();
        newExtractors.forEach(this.extractors::add);
        return this;
    }

    public Set<ArchiveExtractor> defaultExtractors()
    {
        return Set.of(
            new RarExtractor(),
            new SevenZExtractor(),
            new SingleFileDecompressor(),
            new StreamArchiveExtractor(),
            new ZipExtractor()
        );
    }


    int maxDepth()                 { return maxDepth; }
    boolean recurseIntoArchives()  { return recurseIntoArchives; }
    boolean followLinks()          { return followLinks; }
    boolean unixAttributes()       { return unixAttributes; }


    boolean showFileType(FileAttributes.Type type)
    {
        return ((fileTypes == null) ? defaultFileTypes() : fileTypes).contains(type);
    }

    List<PathMatcher> inclusions() { return inclusions; }
    List<PathMatcher> exclusions() { return exclusions; }

    Map<String,ArchiveExtractor> extractorMap()
    {
        if(extractorMap == null)
        {
            extractorMap = new HashMap<>();
            for(var extractor : (extractors == null) ? defaultExtractors() : extractors)
            {
                for(var fileExtension : extractor.getFileExtensions())
                {
                    extractorMap.put(fileExtension, extractor);
                }
            }
        }
        return extractorMap;
    }
}

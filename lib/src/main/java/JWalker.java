package au.djac.jwalker;
import au.djac.jwalker.attr.*;
import au.djac.jwalker.extractors.*;
import au.djac.jwalker.tree.*;

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
    private boolean followLinks = false;
    private boolean unixAttributes = true;
    private boolean dosAttributes = false;

    private List<PathMatcher> inclusions = new ArrayList<>();
    private List<PathMatcher> exclusions = new ArrayList<>();
    private Set<FileType> fileTypes = null;
    private boolean invertedFileTypes = false;
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

    public FileTree makeTree(Path rootPath)
    {
        var tree = new FileTree(rootPath);
        walk(rootPath,
             (path, input, attr) -> tree.addPath(path, attr),
             (path, attr, msg, ex) -> tree.addError(path, msg, ex));
        return tree;
    }

    /**
     * Specifies the number of directory levels to visit, counting archive files as directories
     * (unless 'recurseIntoArchives' is false). Any files in directories/archives that are nested
     * more deeply are skipped. A value of 0 corresponds to just the path explicitly passed to
     * {@code walk()}.
     *
     * @param d The maximum recursion depth.
     * @return This object.
     */
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
     * @return This object.
     */
    public JWalker recurseIntoArchives(boolean b)
    {
        recurseIntoArchives = b;
        return this;
    }

    /**
     * Specifies whether to follow symbolic links outside of archives (false by default).
     * Symbolic links occurring <em>within</em> archive files are generally <em>not</em> followed,
     * regardless of this setting.
     *
     * @param b Whether to follow symbolic links (outside archive files).
     * @return This object.
     */
    public JWalker followLinks(boolean b)
    {
        followLinks = b;
        return this;
    }

    /**
     * Specifies whether to (try to) obtain UNIX-related attributes from files, outside of archives
     * (true by default). This includes user and group names, and read/write/execute permissions.
     *
     * <p>This does not affect files within archives, where this information is automatically
     * retrieved anyway if available.
     *
     * @param b Whether to obtain UNIX attributes.
     * @return This object.
     */
    public JWalker unixAttributes(boolean b)
    {
        unixAttributes = b;
        return this;
    }

    public JWalker dosAttributes(boolean b)
    {
        dosAttributes = b;
        return this;
    }

    private JWalker fileTypes(boolean inverted, Iterable<FileType> newFileTypes)
    {
        if(this.fileTypes != null && this.invertedFileTypes != inverted)
        {
            throw new IllegalStateException(
                "Cannot mix calls to fileTypes(), fileTypesExcept() and allFileTypes()");
        }
        if(this.fileTypes == null)
        {
            this.fileTypes = new HashSet<>();
            this.invertedFileTypes = inverted;
        }
        newFileTypes.forEach(this.fileTypes::add);
        return this;
    }

    /**
     * Causes {@code walk()} to report only the given set of file types. This does not limit the
     * extent of recursion; directories and archives will still be recursed into irrespective of
     * this setting.
     *
     * <p>The default is to report those file types returned by {@link defaultFileTypes()}. This
     * default is forgotten when calling {@code fileTypes()}, {@code fileTypesExcept()} or
     * {@code allFileTypes()}.
     *
     * @param includedFileTypes One or more {@link FileType} values, representing the
     * file type(s) to report.
     * @return This object.
     */
    public JWalker fileTypes(FileType... includedFileTypes)
    {
        return fileTypes(false, Arrays.asList(includedFileTypes));
    }

    /**
     * Causes {@code walk()} to report only the given set of file types. This does not limit the
     * extent of recursion; directories and archives will still be recursed into irrespective of
     * this setting.
     *
     * <p>The default is to report those file types returned by {@link defaultFileTypes()}. This
     * default is forgotten when calling {@code fileTypes()}, {@code fileTypesExcept()} or
     * {@code allFileTypes()}.
     *
     * @param includedFileTypes An {@code Iterable} returning {@link FileType} values,
     * representing the file type(s) to report.
     * @return This object.
     */
    public JWalker fileTypes(Iterable<FileType> includedFileTypes)
    {
        return fileTypes(false, includedFileTypes);
    }

    /**
     * Causes {@code walk()} to report all file types <em>except</em> for a given set. This does not
     * limit the extent of recursion; directories and archives will still be recursed into
     * irrespective of this setting.
     *
     * <p>This cannot be used in conjunction with {@code fileTypes()}. If you wish to specify
     * which file types to report, you must do so either inclusively ({@code fileTypes()})
     * <em>or</em> exclusively ({@code fileTypesExcept()} or {@code allFileTypes()}).
     *
     * <p>The default is to report those file types returned by {@link defaultFileTypes()}. This
     * default is forgotten when calling {@code fileTypes()}, {@code fileTypesExcept()} or
     * {@code allFileTypes()}.
     *
     * @param excludedFileTypes One or more {@link FileType} values, representing the
     * file type(s) <em>not</em> to report.
     * @return This object.
     */
    public JWalker fileTypesExcept(FileType... excludedFileTypes)
    {
        return fileTypes(true, Arrays.asList(excludedFileTypes));
    }

    /**
     * Causes {@code walk()} to report all file types <em>except</em> for a given set. This does not
     * limit the extent of recursion; directories and archives will still be recursed into
     * irrespective of this setting.
     *
     * <p>This cannot be used in conjunction with {@code fileTypes()}. If you wish to specify
     * which file types to report, you must do so either inclusively ({@code fileTypes()})
     * <em>or</em> exclusively ({@code fileTypesExcept()} or {@code allFileTypes()}).
     *
     * <p>The default is to report those file types returned by {@link defaultFileTypes()}. This
     * default is forgotten when calling {@code fileTypes()}, {@code fileTypesExcept()} or
     * {@code allFileTypes()}.
     *
     * @param excludedFileTypes An {@code Iterable} returning {@link FileType} values,
     * representing the file type(s) <em>not</em> to report.
     * @return This object.
     */
    public JWalker fileTypesExcept(Iterable<FileType> excludedFileTypes)
    {
        return fileTypes(true, excludedFileTypes);
    }

    /**
     * Causes {@code walk()} to report all file types.
     *
     * <p>The default is to report those file types returned by {@link defaultFileTypes()}. This
     * default is forgotten when calling {@code fileTypes()}, {@code fileTypesExcept()} or
     * {@code allFileTypes()}.
     *
     * @return This object.
     */
    public JWalker allFileTypes()
    {
        fileTypes = new HashSet<>();
        invertedFileTypes = true;
        return this;
    }

    /**
     * Returns the set of file types to be reported by {@code walk()} <em>in the absence</em> of any
     * call to {@code fileTypes()}, {@code fileTypesExcept()} or {@code allFileTypes()}.
     *
     * @return The set of default file types.
     */
    public Set<FileType> defaultFileTypes()
    {
        return Set.of(FileType.REGULAR_FILE);
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
    boolean dosAttributes()        { return dosAttributes; }


    boolean showFileType(FileType type)
    {
        return ((fileTypes == null) ? defaultFileTypes() : fileTypes).contains(type) != invertedFileTypes;
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

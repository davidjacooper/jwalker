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


    private int _maxDepth = Integer.MAX_VALUE;
    private boolean _recurseIntoArchives = true;
    private boolean _followLinks = false;
    private boolean _unixAttributes = true;
    private boolean _dosAttributes = false;

    private List<PathMatcher> _inclusions = new ArrayList<>();
    private List<PathMatcher> _exclusions = new ArrayList<>();
    private Set<FileType> _fileTypes = null;
    private boolean _invertedFileTypes = false;
    private Set<ArchiveExtractor> _extractors = null;
    private Map<String,ArchiveExtractor> _extractorMap = null;

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
        _maxDepth = d;
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
        _recurseIntoArchives = b;
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
        _followLinks = b;
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
        _unixAttributes = b;
        return this;
    }

    public JWalker dosAttributes(boolean b)
    {
        _dosAttributes = b;
        return this;
    }

    private JWalker fileTypes(boolean inverted, Iterable<FileType> newFileTypes)
    {
        if(this._fileTypes != null && this._invertedFileTypes != inverted)
        {
            throw new IllegalStateException(
                "Cannot mix calls to fileTypes(), fileTypesExcept() and allFileTypes()");
        }
        if(this._fileTypes == null)
        {
            this._fileTypes = new HashSet<>();
            this._invertedFileTypes = inverted;
        }
        newFileTypes.forEach(this._fileTypes::add);
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
        _fileTypes = new HashSet<>();
        _invertedFileTypes = true;
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
        _inclusions.add(globMatcher(globPattern));
        return this;
    }

    public JWalker include(PathMatcher matcher)
    {
        _inclusions.add(matcher);
        return this;
    }

    public JWalker exclude(String globPattern)
    {
        _exclusions.add(globMatcher(globPattern));
        return this;
    }

    public JWalker exclude(PathMatcher matcher)
    {
        _exclusions.add(matcher);
        return this;
    }

    private void initExtractors()
    {
        if(this._extractors == null)
        {
            this._extractors = new HashSet<>();
        }
        _extractorMap = null;
    }

    public JWalker extractWith(ArchiveExtractor... newExtractors)
    {
        initExtractors();
        for(var e : _extractors)
        {
            this._extractors.add(e);
        }
        return this;
    }

    public JWalker extractWith(Iterable<ArchiveExtractor> newExtractors)
    {
        initExtractors();
        newExtractors.forEach(this._extractors::add);
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


    int maxDepth()                 { return _maxDepth; }
    boolean recurseIntoArchives()  { return _recurseIntoArchives; }
    boolean followLinks()          { return _followLinks; }
    boolean unixAttributes()       { return _unixAttributes; }
    boolean dosAttributes()        { return _dosAttributes; }


    boolean showFileType(FileType type)
    {
        return ((_fileTypes == null) ? defaultFileTypes() : _fileTypes).contains(type) != _invertedFileTypes;
    }

    List<PathMatcher> inclusions() { return _inclusions; }
    List<PathMatcher> exclusions() { return _exclusions; }

    Map<String,ArchiveExtractor> extractorMap()
    {
        if(_extractorMap == null)
        {
            _extractorMap = new HashMap<>();
            for(var extractor : (_extractors == null) ? defaultExtractors() : _extractors)
            {
                for(var fileExtension : extractor.getFileExtensions())
                {
                    _extractorMap.put(fileExtension, extractor);
                }
            }
        }
        return _extractorMap;
    }
}

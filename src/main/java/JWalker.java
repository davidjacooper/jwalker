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


    private int maxDepth = Integer.MAX_VALUE; // TBD: equivalent to Files.walkFileTree
    private boolean recurseIntoArchives = true;
    private boolean followLinks = false;      // TBD: whether the follow FS symlinks. (We're not going to follow links within archives.)
    private boolean unixAttributes = true;
    private Set<FileAttributes.Type> fileTypes = new HashSet<>(Set.of(FileAttributes.Type.REGULAR_FILE));

    private List<PathMatcher> inclusions = new ArrayList<>();
    private List<PathMatcher> exclusions = new ArrayList<>();

    private Set<Class<? extends ArchiveExtractor>> archiveExtractors = new HashSet<>(Set.of(
        RarExtractor.class, SevenZExtractor.class, ZipExtractor.class,
        StreamArchiveExtractor.class, SingleFileDecompressor.class
    ));

    private Map<String,ArchiveExtractor> extractorMap = null;

    public JWalker() {}

    public void walk(Path rootPath, FileConsumer fileConsumer, ErrorHandler errorHandler)
    {
        new JWalkerOperation(this, fileConsumer, errorHandler).walk(rootPath);
    }

    public void walk(Path rootPath, FileConsumer fileConsumer)
    {
        walk(rootPath, fileConsumer, (path, attr, msg, ex) -> { throw new JWalkerException(msg, ex); });
    }

    public JWalker maxDepth(int d)
    {
        maxDepth = d;
        return this;
    }

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

    public JWalker fileTypes(FileAttributes.Type... fileTypes)
    {
        this.fileTypes.clear();
        for(var t : fileTypes)
        {
            this.fileTypes.add(t);
        }
        return this;
    }

    public JWalker fileTypes(Iterable<FileAttributes.Type> fileTypes)
    {
        this.fileTypes.clear();
        for(var t : fileTypes)
        {
            this.fileTypes.add(t);
        }
        return this;
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


    int maxDepth()                 { return maxDepth; }
    boolean recurseIntoArchives()  { return recurseIntoArchives; }
    boolean followLinks()          { return followLinks; }
    boolean unixAttributes()       { return unixAttributes; }

    private FileSystem fs = FileSystems.getDefault();

    private PathMatcher globMatcher(String globPattern)
    {
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

    public boolean showFileType(FileAttributes.Type type)
    {
        return fileTypes.contains(type);
    }

    public List<PathMatcher> inclusions() { return inclusions; }
    public List<PathMatcher> exclusions() { return exclusions; }

    public Set<Class<? extends ArchiveExtractor>> archiveExtractors()
    {
        return archiveExtractors;
    }

    public Map<String,ArchiveExtractor> extractorMap()
    {
        if(extractorMap == null)
        {
            extractorMap = new HashMap<>();
            for(var extractorCls : archiveExtractors)
            {
                try
                {
                    var extractor = extractorCls.getConstructor().newInstance();
                    for(var fileExtension : extractor.getFileExtensions())
                    {
                        extractorMap.put(fileExtension, extractor);
                    }
                }
                catch(ReflectiveOperationException e)
                {
                    throw new UnsupportedOperationException(
                        "Could not create extractor " + extractorCls, e);
                }
            }
        }
        return extractorMap;
    }
}

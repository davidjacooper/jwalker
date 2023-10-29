package au.djac.jdirscanner;

import java.nio.file.*;
import java.util.*;

public class JDirScannerOptions
{
    private boolean asExcludedContent = false;
    private boolean recurseIntoArchives = true;

    private ErrorPolicy errorPolicy = ErrorPolicy.DEFAULT;

    private List<PathMatcher> inclusions = new ArrayList<>();
    private List<PathMatcher> exclusions = new ArrayList<>();

    private Set<Class<? extends ArchiveExtractor>> archiveExtractors = new HashSet<>(Set.of(
        RarExtractor.class, SevenZExtractor.class, ZipExtractor.class,
        StreamArchiveExtractor.class, SingleFileDecompressor.class
    ));

    private Map<String,ArchiveExtractor> extractorMap = null;

    public JDirScannerOptions() {}

    public void asExcludedContent(boolean b)    { asExcludedContent = b; }
    public void recurseIntoArchives(boolean b)  { recurseIntoArchives = b; }
    public boolean asExcludedContent()    { return asExcludedContent; }
    public boolean recurseIntoArchives()  { return recurseIntoArchives; }

    public void errorPolicy(ErrorPolicy p) { errorPolicy = p; }
    public ErrorPolicy errorPolicy()       { return errorPolicy; }

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

    public void addInclusion(String... globPatterns)
    {
        for(var globPattern : globPatterns)
        {
            inclusions.add(globMatcher(globPattern));
        }
    }

    public void addExclusion(String... globPatterns)
    {
        for(var globPattern : globPatterns)
        {
            exclusions.add(globMatcher(globPattern));
        }
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
